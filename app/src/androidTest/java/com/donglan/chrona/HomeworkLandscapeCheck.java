package com.donglan.chrona;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONObject;

final class HomeworkLandscapeCheck {
    static String run(Instrumentation test, Bundle args) throws Exception {
        var context = test.getTargetContext();
        FixtureIsolation.require(context);
        var source = new ZjuHomeworkStore(context);
        String fixture = "homework-landscape-fixture";
        var before = source.state();
        if (before.captureId != 0 && !fixture.equals(before.account))
            throw new IllegalStateException("Use an empty validation app");
        String theme = args.getString("theme", "light");
        boolean geometryOnly = args.containsKey("geometry_only");
        String oldMode = ThemeStore.mode(context);
        test.runOnMainSync(() -> ThemeStore.setMode(context, theme));
        if (!before.enabled) source.configure(fixture, true);
        var state = source.state();
        String notes = "读完题目后再写解答。\n".repeat(28) + "正文最后一行";
        long due = System.currentTimeMillis() + 86_400_000;
        source.apply(state.account, state.generation, List.of(
                new ZjuSnapshot.Homework("rotation-1", "101", "合成课程 · 另一份作业", "短正文",
                        "https://courses.zju.edu.cn/course/101/learning-activity#/122", "", due, false),
                new ZjuSnapshot.Homework("rotation-2", "101", "合成课程 · 横屏作业", notes,
                        "https://courses.zju.edu.cn/course/101/learning-activity#/123", "", due, false)));
        long selected;
        try (TaskStore store = new TaskStore(context)) { selected = store.getCandidates(state.captureId).get(1).id; }
        var ui = test.getUiAutomation();
        var app = (Application) context.getApplicationContext();
        var resumed = new AtomicReference<Activity>();
        var lifecycle = new Application.ActivityLifecycleCallbacks() {
            @Override public void onActivityResumed(Activity activity) {
                if (activity instanceof TaskDetailActivity) resumed.set(activity);
            }
            @Override public void onActivityCreated(Activity activity, Bundle state) { }
            @Override public void onActivityStarted(Activity activity) { }
            @Override public void onActivityPaused(Activity activity) { }
            @Override public void onActivityStopped(Activity activity) { }
            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
            @Override public void onActivityDestroyed(Activity activity) { }
        };
        app.registerActivityLifecycleCallbacks(lifecycle);
        Activity opened = null;
        try {
            opened = test.startActivitySync(new Intent(context, TaskDetailActivity.class)
                    .putExtra("task_id", state.captureId)
                    .putExtra(TaskDetailActivity.EXTRA_CANDIDATE_ID, selected)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            SystemClock.sleep(450);
            test.waitForIdleSync();
            Activity portrait = opened;
            int[] readingOffset = new int[2];
            boolean rotate = opened.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE;
            if (rotate) test.runOnMainSync(() -> {
                var page = find(portrait.getWindow().getDecorView(), ScrollView.class);
                page.scrollTo(0, 80);
                readingOffset[0] = page.getScrollY();
                portrait.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            });
            long end = SystemClock.uptimeMillis() + 8000;
            while (((rotate && opened == portrait) || opened.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
                    && SystemClock.uptimeMillis() < end) {
                SystemClock.sleep(100);
                var next = resumed.get();
                if (next != null) opened = next;
            }
            if ((rotate && opened == portrait) || opened.getResources().getConfiguration().orientation != Configuration.ORIENTATION_LANDSCAPE)
                throw new AssertionError("Landscape recreation missing");
            long focusDeadline = SystemClock.uptimeMillis() + 20_000;
            Activity[] focused = {null};
            int[] frame = {0, 0};
            while (focused[0] == null && SystemClock.uptimeMillis() < focusDeadline) {
                test.runOnMainSync(() -> {
                    Activity current = resumed.get();
                    if (current == null || current.isDestroyed()) return;
                    View decor = current.getWindow().getDecorView();
                    frame[0] = decor.getWidth(); frame[1] = decor.getHeight();
                    if ((geometryOnly || decor.hasWindowFocus()) && frame[0] > frame[1]) focused[0] = current;
                });
                if (focused[0] == null) SystemClock.sleep(100);
            }
            if (focused[0] == null) {
                shot(test, theme + "-focus-failed");
                throw new AssertionError("Rotated detail not focused: " + frame[0] + "x" + frame[1]);
            }
            Activity activity = focused[0];
            opened = activity;
            View root = activity.getWindow().getDecorView();
            SystemClock.sleep(1000); // Wait for the system rotation animation, not just Activity resume.
            int[] geometry = new int[4];
            View[] action = new View[1];
            Rect viewport = new Rect();
            test.runOnMainSync(() -> {
                var pager = find(root, CandidatePagerScrollView.class);
                if (pager == null) throw new AssertionError("Multi-assignment pager missing");
                var pages = (ViewGroup) pager.getChildAt(0);
                var slot = (ViewGroup) pages.getChildAt(1);
                if (slot.getChildCount() == 0) throw new AssertionError("Selected assignment lost after rotation");
                geometry[0] = pager.getWidth(); geometry[1] = slot.getWidth(); geometry[2] = pager.getScrollX();
                geometry[3] = activity.getResources().getDisplayMetrics().widthPixels;
                if (geometry[0] <= 0 || geometry[0] != geometry[1] || geometry[2] != geometry[0])
                    throw new AssertionError("Page is not aligned to usable width: " + java.util.Arrays.toString(geometry));
                View notesButton = described(slot, "查看作业内容");
                if (notesButton == null) throw new AssertionError("Missing notes action");
                action[0] = notesButton;
                var page = find(root, ScrollView.class);
                readingOffset[1] = page.getScrollY();
                if (readingOffset[0] > 0 && readingOffset[1] != readingOffset[0])
                    throw new AssertionError("Reading position changed on rotation");
                bounds(page, viewport);
            });
            if (geometryOnly) {
                int narrowWidth = Math.round(root.getWidth() * .58f);
                test.runOnMainSync(() -> activity.getWindow().setLayout(narrowWidth, -1));
                int[] narrowed = new int[3];
                long resizeDeadline = SystemClock.uptimeMillis() + 6000;
                do {
                    SystemClock.sleep(120);
                    test.runOnMainSync(() -> {
                        var pager = find(root, CandidatePagerScrollView.class);
                        var track = (ViewGroup) pager.getChildAt(0);
                        narrowed[0] = pager.getWidth(); narrowed[1] = track.getChildAt(1).getWidth();
                        narrowed[2] = pager.getScrollX();
                    });
                } while (narrowed[0] >= geometry[0] && SystemClock.uptimeMillis() < resizeDeadline);
                if (narrowed[0] <= 0 || narrowed[0] >= geometry[0] || narrowed[1] != narrowed[0] || narrowed[2] != narrowed[0])
                    throw new AssertionError("Resized window lost aligned selection: " + java.util.Arrays.toString(narrowed));
                return new JSONObject().put("viewport_px", geometry[0]).put("slot_px", geometry[1])
                        .put("selected_offset_px", geometry[2]).put("narrow_viewport_px", narrowed[0])
                        .put("narrow_selected_offset_px", narrowed[2]).put("input_tested", false).toString();
            }
            for (int i = 0; i < 3; i++) swipeUp(test, viewport);
            SystemClock.sleep(500);
            shot(test, theme + "-detail");
            int[] tap = new int[2];
            test.runOnMainSync(() -> {
                Rect visible = new Rect();
                if (!action[0].isEnabled() || !action[0].getGlobalVisibleRect(visible)
                        || visible.height() != action[0].getHeight())
                    throw new AssertionError("Notes action is not fully visible and enabled");
                action[0].getLocationOnScreen(tap);
                tap[0] += action[0].getWidth() / 2;
                tap[1] += action[0].getHeight() / 2;
            });
            long now = SystemClock.uptimeMillis();
            PtaUiInteractionCheck.touch(test, now, now, 0, tap[0], tap[1]);
            SystemClock.sleep(75);
            PtaUiInteractionCheck.touch(test, now, SystemClock.uptimeMillis(), 1, tap[0], tap[1]);
            ScrollView[] notesViewport = {null};
            long notesDeadline = SystemClock.uptimeMillis() + 5000;
            do {
                SystemClock.sleep(100);
                test.runOnMainSync(() -> {
                    for (View window : android.view.inspector.WindowInspector.getGlobalWindowViews()) {
                        if (!window.hasWindowFocus() || window == root) continue;
                        var scroll = notesScroll(window);
                        if (scroll == null || scroll.getHeight() == 0) continue;
                        notesViewport[0] = scroll;
                        bounds(scroll, viewport);
                        return;
                    }
                });
            } while (notesViewport[0] == null && SystemClock.uptimeMillis() < notesDeadline);
            if (notesViewport[0] == null) {
                shot(test, theme + "-notes-not-observed");
                throw new AssertionError("Notes dialog never became focused and measurable");
            }
            SystemClock.sleep(350); // Let the dialog finish moving before using screen coordinates.
            test.runOnMainSync(() -> bounds(notesViewport[0], viewport));
            for (int i = 0; i < 7; i++) swipeUp(test, viewport);
            test.runOnMainSync(() -> {
                var scroll = notesViewport[0];
                var text = (TextView) scroll.getChildAt(0);
                var layout = text.getLayout();
                int endY = text.getTotalPaddingTop() + layout.getLineBottom(layout.getLineCount() - 1);
                if (scroll.getScrollY() == 0 || endY > scroll.getScrollY() + scroll.getHeight())
                    throw new AssertionError("End of notes is unreachable");
            });
            test.waitForIdleSync();
            shot(test, theme + "-notes-end");
            PtaUiInteractionCheck.tap(test, "关闭");
            SystemClock.sleep(350);
            test.waitForIdleSync();
            if (ui.getRootInActiveWindow().findAccessibilityNodeInfosByText("关闭").stream()
                    .anyMatch(node -> "关闭".contentEquals(node.getText())))
                throw new AssertionError("Close action was not reachable");
            return new JSONObject().put("viewport_px", geometry[0]).put("slot_px", geometry[1])
                    .put("selected_offset_px", geometry[2]).put("display_px", geometry[3])
                    .put("portrait_scroll_px", readingOffset[0]).put("landscape_scroll_px", readingOffset[1])
                    .put("rotated_from_portrait", rotate)
                    .put("notes_end_and_close", true).toString();
        } finally {
            app.unregisterActivityLifecycleCallbacks(lifecycle);
            if (opened != null) {
                Activity activity = opened;
                test.runOnMainSync(activity::finish);
            }
            test.runOnMainSync(() -> ThemeStore.setMode(context, oldMode));
        }
    }

    private static void bounds(View view, Rect out) {
        int[] position = new int[2];
        view.getLocationOnScreen(position);
        out.set(position[0], position[1], position[0] + view.getWidth(), position[1] + view.getHeight());
    }

    private static ScrollView notesScroll(View view) {
        if (view instanceof ScrollView scroll && scroll.getChildAt(0) instanceof TextView text
                && text.getText().toString().contains("正文最后一行")) return scroll;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            ScrollView result = notesScroll(group.getChildAt(i));
            if (result != null) return result;
        }
        return null;
    }

    private static void swipeUp(Instrumentation test, Rect bounds) {
        float x = bounds.left + bounds.width() * .4f;
        long down = SystemClock.uptimeMillis();
        for (int i = 0; i <= 12; i++) {
            float y = bounds.top + bounds.height() * (.86f - .72f * i / 12f);
            PtaUiInteractionCheck.touch(test, down, SystemClock.uptimeMillis(), i == 0 ? 0 : i == 12 ? 1 : 2, x, y);
            SystemClock.sleep(18);
        }
        SystemClock.sleep(250);
    }

    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            T result = find(group.getChildAt(i), type);
            if (result != null) return result;
        }
        return null;
    }

    private static View described(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View result = described(group.getChildAt(i), description);
            if (result != null) return result;
        }
        return null;
    }

    private static void shot(Instrumentation test, String label) throws Exception {
        Bitmap original = test.getUiAutomation().takeScreenshot();
        float scale = Math.min(1f, 1200f / Math.max(original.getWidth(), original.getHeight()));
        Bitmap small = Bitmap.createScaledBitmap(original, Math.round(original.getWidth() * scale),
                Math.round(original.getHeight() * scale), true);
        try (var file = new FileOutputStream(new File(test.getTargetContext().getExternalCacheDir(),
                "homework-landscape-" + label + ".jpg"))) {
            small.compress(Bitmap.CompressFormat.JPEG, 82, file);
        } finally {
            if (small != original) small.recycle();
            original.recycle();
        }
    }
}
