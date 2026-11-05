package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.io.File;
import java.io.FileOutputStream;
import java.util.List;
import java.util.concurrent.FutureTask;
import org.json.JSONObject;

/** Real native taps and data, restricted to the disposable emulator package. */
final class CompletionFeedbackDeviceCheck {
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context = test.getTargetContext();
        FixtureIsolation.require(context);
        test.getUiAutomation();
        String theme = args.getString("theme", "light");
        String previous = ThemeStore.mode(context);
        test.runOnMainSync(() -> ThemeStore.setMode(context, theme));
        if (args.containsKey("dashboard")) {
            try { return dashboard(test, context, theme, args.containsKey("future")); }
            finally { test.runOnMainSync(() -> ThemeStore.setMode(context, previous)); }
        }
        if (args.containsKey("frames_only")) {
            try { return renderFrames(test, context, theme); }
            finally { test.runOnMainSync(() -> ThemeStore.setMode(context, previous)); }
        }
        long now = System.currentTimeMillis(), task, event, todoId, eventId;
        try (TaskStore store = new TaskStore(context)) {
            task = store.insertTask("完成反馈测试", null, "manual", now);
            todoId = store.appendCandidate(new EventCandidate(0, task, "整理线代习题", null, null,
                    "Asia/Shanghai", "", "", null, false, null, "task", false, false, 0, now + 3_600_000, null, false));
            store.updateStatus(task, TaskRecord.READY, null);
            event = store.insertTask("活动反馈测试", null, "manual", now + 1);
            eventId = store.appendCandidate(new EventCandidate(0, event, "晚间跑步", now + 60_000,
                    now + 3_600_000, "Asia/Shanghai", "", "", null, false, null, "event", false));
            store.updateStatus(event, TaskRecord.READY, null);
        }
        Activity screen = null;
        try {
            var animation = CompletionButton.class.getDeclaredField("pulse");
            animation.setAccessible(true);
            boolean motion = UiMotion.isEnabled();
            screen = open(test, task);
            capture(test, theme + "-before");
            CompletionButton complete = control(test, screen, "标记完成");
            tap(test, complete);
            if (motion) {
                Activity detail = screen;
                await(test, () -> {
                    CompletionButton pulse = find(detail.getWindow().getDecorView(), "已完成");
                    return pulse != null && animation.get(pulse) != null;
                }, "task completion pulse");
                if (!completed(context, todoId)) throw new AssertionError("Pulse started before local commit");
                capture(test, theme + "-task-pulse");
            }
            CompletionButton reopen = control(test, screen, "恢复待办");
            if (!completed(context, todoId)) throw new AssertionError("Task never completed");
            tap(test, reopen);
            control(test, screen, "标记完成");
            if (completed(context, todoId) || animation.get(reopen) != null) throw new AssertionError("Reopen played a pulse");
            test.runOnMainSync(screen::finish);
            test.waitForIdleSync();
            SystemClock.sleep(500);
            screen = open(test, event);
            CompletionButton confirm = control(test, screen, "确认活动");
            tap(test, confirm);
            if (motion) {
                await(test, () -> animation.get(confirm) != null, "activity confirmation pulse");
                capture(test, theme + "-activity-pulse");
            }
            try (TaskStore store = new TaskStore(context)) {
                var item = store.getCandidatesByIds(List.of(eventId)).get(0);
                String key = AgendaAcknowledgement.key(item);
                if (item.completed() || !AgendaAcknowledgement.isAcknowledged(context, key))
                    throw new AssertionError("Activity acknowledgement changed completion meaning");
                tap(test, control(test, screen, "取消确认活动"));
                control(test, screen, "确认活动");
                if (AgendaAcknowledgement.isAcknowledged(context, key) || animation.get(confirm) != null)
                    throw new AssertionError("Undo did not clear activity state/pulse");
            }
            return new JSONObject().put("task_commit_and_restore", true)
                    .put("activity_confirm_and_undo", true).put("system_motion", motion).toString();
        } catch (AssertionError failure) {
            capture(test, theme + "-failed");
            Activity owner = screen;
            StringBuilder diagnostics = new StringBuilder();
            if (owner != null) test.runOnMainSync(() -> {
                View decor = owner.getWindow().getDecorView();
                diagnostics.append("focused=").append(decor.hasWindowFocus())
                        .append(" destroyed=").append(owner.isDestroyed());
                describeControls(decor, diagnostics);
            });
            throw new AssertionError(failure.getMessage() + "; " + diagnostics, failure);
        } finally {
            if (screen != null) { Activity open = screen; test.runOnMainSync(open::finish); }
            try (TaskStore store = new TaskStore(context)) { store.deleteTask(task); store.deleteTask(event); }
            test.runOnMainSync(() -> ThemeStore.setMode(context, previous));
        }
    }

    private static String dashboard(Instrumentation test, Context context, String theme, boolean future) throws Exception {
        var preferences = context.getSharedPreferences("home_timeline", Context.MODE_PRIVATE);
        var previous = preferences.getAll().get("todos_expanded");
        preferences.edit().putBoolean("todos_expanded", true).apply();
        long task;
        long now = System.currentTimeMillis();
        long today = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        try (TaskStore store = new TaskStore(context)) {
            task = store.insertTask("待办补位测试", null, "manual", now);
            for (int i = 0; i < 2; i++) {
                store.appendCandidate(new EventCandidate(0, task, "补位测试 " + i, null, null,
                        "Asia/Shanghai", "", "", null, false, null, "task", false, false, 0,
                        future ? now + 3_600_000 + i * 60_000 : today + i, null, false));
            }
            store.updateStatus(task, TaskRecord.READY, null);
        }
        Activity owner = null;
        try {
            owner = test.startActivitySync(new Intent(context, DashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
            View root = owner.getWindow().getDecorView();
            await(test, () -> root.findViewWithTag("home-todo-details") != null && root.hasWindowFocus(), "focused dashboard");
            ViewGroup card = root.findViewWithTag("home-todos");
            ViewGroup details = root.findViewWithTag("home-todo-details");
            View heading = card.getChildAt(0), counts = card.getChildAt(1);
            View first = details.getChildAt(0), survivor = details.getChildAt(1);
            CompletionButton button = findContains(first, "标记待办完成");
            if (button == null) throw new AssertionError("First fixture row has no completion button");
            var title = (android.widget.TextView) ((ViewGroup) heading).getChildAt(0);
            if (future && !"待办 · 2".contentEquals(title.getText()))
                throw new AssertionError("Future last-row check needs only the two synthetic tasks");
            if (future && !onMain(test, () -> countText(root, "补位测试 0") >= 3))
                throw new AssertionError("The same future task must appear in all three home sections");
            int[] base = new int[1], rest = new int[1];
            test.runOnMainSync(() -> {
                android.widget.ScrollView viewport = null;
                for (View v = card; v.getParent() instanceof View p; v = p) {
                    if (p instanceof android.widget.ScrollView sv) { viewport = sv; break; }
                }
                if (viewport == null) throw new AssertionError("Missing dashboard viewport");
                var box = new android.graphics.Rect();
                button.getDrawingRect(box); viewport.offsetDescendantRectToMyCoords(button, box);
                viewport.scrollTo(0, Math.max(0, box.centerY() - viewport.getHeight() * 2 / 3));
                base[0] = survivor.getTop();
                rest[0] = first.getTop();
            });
            SystemClock.sleep(500);
            capture(test, theme + "-dashboard-before");
            java.util.ArrayList<Integer> frames = new java.util.ArrayList<>();
            java.util.concurrent.atomic.AtomicBoolean finished = new java.util.concurrent.atomic.AtomicBoolean();
            java.util.concurrent.atomic.AtomicBoolean queuedDuringMotion = new java.util.concurrent.atomic.AtomicBoolean();
            java.util.concurrent.atomic.AtomicBoolean attemptedToggle = new java.util.concurrent.atomic.AtomicBoolean();
            var reflowing = card.getClass().getDeclaredField("reflowing");
            var pendingToggle = card.getClass().getDeclaredField("pendingToggle");
            var expanding = card.getClass().getDeclaredField("expanding");
            reflowing.setAccessible(true);
            pendingToggle.setAccessible(true);
            expanding.setAccessible(true);
            View.OnAttachStateChangeListener watch = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { }
                @Override public void onViewDetachedFromWindow(View view) { finished.set(true); }
            };
            android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
            // ChangeBounds captures the new layout in its pre-draw callback, then restores
            // the start bounds before rendering. Observe draw, not that invisible capture.
            var listener = (android.view.ViewTreeObserver.OnDrawListener) () -> {
                if (card.getChildAt(0) != heading || card.getChildAt(1) != counts || survivor.getParent() != details)
                    throw new AssertionError("Live heading, counts, and surviving row must not detach");
                if (card.getAlpha() < .99f) throw new AssertionError("Whole-card entrance replayed");
                if (details.isShown()) frames.add(survivor.getTop());
                if (future && UiMotion.isEnabled() && first.getParent() != details
                        && survivor.getTop() > rest[0] && attemptedToggle.compareAndSet(false, true)) {
                    ui.postAtFrontOfQueue(() -> {
                        try {
                            if (!reflowing.getBoolean(card)) return;
                            title.performClick();
                            queuedDuringMotion.set(pendingToggle.get(card) == title);
                        } catch (ReflectiveOperationException error) { throw new IllegalStateException(error); }
                    });
                }
            };
            test.runOnMainSync(() -> {
                first.addOnAttachStateChangeListener(watch);
                root.getViewTreeObserver().addOnDrawListener(listener);
            });
            try {
                tap(test, button);
                await(test, () -> finished.get() && survivor.getTop() == rest[0], "completion row exit and settled sibling");
                SystemClock.sleep(250);
            } finally {
                test.runOnMainSync(() -> root.getViewTreeObserver().removeOnDrawListener(listener));
            }
            int last = base[0], mid = 0;
            for (int top : frames) {
                if (top > last + 1 || top < rest[0] - 1) throw new AssertionError("Row reversed or overshot: " + frames);
                if (top < base[0] && top > rest[0]) mid++;
                last = top;
            }
            if (UiMotion.isEnabled() && mid < 2) throw new AssertionError("No continuous sibling motion: " + frames);
            capture(test, theme + "-dashboard-after");
            if (future) {
                if (UiMotion.isEnabled()) {
                    if (!queuedDuringMotion.get()) throw new AssertionError("Did not queue toggle during visible reflow: "
                            + attemptedToggle.get() + "; " + frames);
                    await(test, () -> details.getVisibility() == View.GONE, "queued collapse after reflow");
                    test.runOnMainSync(title::performClick);
                }
                CompletionButton lastButton = findContains(survivor, "标记待办完成");
                if (lastButton == null) throw new AssertionError("Last completion button missing");
                await(test, () -> !expanding.getBoolean(card) && CompletionButton.canReward(lastButton), "last row reachable after reopen");
                tap(test, lastButton);
                await(test, () -> details.getChildCount() == 0 && "待办 · 0".contentEquals(title.getText())
                        && !reflowing.getBoolean(card), "last row and summary settle");
                if (card.getChildAt(0) != heading || card.getChildAt(1) != counts
                        || title.isClickable() || details.isShown()) throw new AssertionError("Empty summary did not settle");
                capture(test, theme + "-dashboard-empty");
            }
            return new JSONObject().put("heading_counters_and_survivor_retained", true)
                    .put("future_and_last_row", future).put("queued_collapse", queuedDuringMotion.get())
                    .put("survivor_y", new org.json.JSONArray(frames)).put("intermediate_frames", mid).toString();
        } finally {
            if (owner != null) { Activity screen = owner; test.runOnMainSync(screen::finish); }
            try (TaskStore store = new TaskStore(context)) { store.deleteTask(task); }
            if (previous instanceof Boolean value) preferences.edit().putBoolean("todos_expanded", value).apply();
            else preferences.edit().remove("todos_expanded").apply();
        }
    }

    private static CompletionButton findContains(View view, String label) {
        if (view instanceof CompletionButton button && String.valueOf(view.getContentDescription()).contains(label)) return button;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            CompletionButton button = findContains(group.getChildAt(i), label);
            if (button != null) return button;
        }
        return null;
    }

    private static int countText(View view, String value) {
        int count = view instanceof android.widget.TextView text && value.contentEquals(text.getText()) ? 1 : 0;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            count += countText(group.getChildAt(i), value);
        return count;
    }

    private static String renderFrames(Instrumentation test, Context context, String theme) throws Exception {
        var progress = CompletionButton.class.getDeclaredField("progress");
        var accent = CompletionButton.class.getDeclaredField("accent");
        var task = CompletionButton.class.getDeclaredField("task");
        var labelProgress = CompletionButton.ActivityLabel.class.getDeclaredField("progress");
        for (var field : List.of(progress, accent, task, labelProgress)) field.setAccessible(true);
        Bitmap sheet = Bitmap.createBitmap(760, 485, Bitmap.Config.ARGB_8888);
        FutureTask<Boolean> render = new FutureTask<>(() -> {
            Canvas canvas = new Canvas(sheet);
            var colors = UiStyle.colors(context);
            canvas.drawColor(colors.surface);
            Paint caption = new Paint(Paint.ANTI_ALIAS_FLAG);
            caption.setColor(colors.text); caption.setTextSize(21);
            caption.setTextAlign(Paint.Align.CENTER);
            float[] stages = {-1f, .08f, .22f, .42f, .7f, 1f};
            String[] names = {"Before", "8%", "22%", "42%", "70%", "Settled"};
            for (int row = 0; row < 2; row++) {
                for (int i = 0; i < stages.length; i++) {
                    int x = 90 + 115 * i, y = 95 + row * 155;
                    CompletionButton button = new CompletionButton(context);
                    button.setPadding(20, 20, 20, 20);
                    button.setImageResource(i == 0 ? (row == 0 ? R.drawable.ic_todo_clock : R.drawable.ic_check) : R.drawable.ic_todo_check);
                    button.setImageTintList(ColorStateList.valueOf(i == 0 ? colors.muted : colors.primary));
                    UiStyle.acrylicChoice(button, i > 0, UiStyle.RADIUS_PILL, false);
                    int exact = View.MeasureSpec.makeMeasureSpec(80, View.MeasureSpec.EXACTLY);
                    button.measure(exact, exact); button.layout(0, 0, 80, 80);
                    progress.setFloat(button, i == 0 ? 1f : stages[i]);
                    accent.setInt(button, colors.primary);
                    task.setBoolean(button, row == 0);
                    int save = canvas.save();
                    canvas.translate(x - 40, y - 40);
                    button.draw(canvas);
                    canvas.restoreToCount(save);
                    canvas.drawText(names[i], x, y + 68, caption);
                }
            }
            for (int i = 0; i < 3; i++) {
                int x = 130 + i * 245, y = 395;
                var label = new CompletionButton.ActivityLabel(context);
                label.bind(i > 0);
                label.setMinimumHeight(0);
                label.setPadding(12, 8, 12, 8);
                label.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, 26);
                label.measure(View.MeasureSpec.makeMeasureSpec(200, View.MeasureSpec.EXACTLY),
                        View.MeasureSpec.makeMeasureSpec(62, View.MeasureSpec.EXACTLY));
                label.layout(0, 0, 200, 62);
                labelProgress.setFloat(label, i == 1 ? .38f : 1f);
                int save = canvas.save();
                canvas.translate(x - 100, y - 31);
                label.draw(canvas);
                canvas.restoreToCount(save);
                canvas.drawText(i == 0 ? "Before" : i == 1 ? "Peak" : "Settled", x, y + 61, caption);
            }
            return true;
        });
        test.runOnMainSync(render);
        render.get();
        try (var out = new FileOutputStream(new File(context.getExternalCacheDir(), "completion-frames-" + theme + ".jpg"))) {
            sheet.compress(Bitmap.CompressFormat.JPEG, 90, out);
        } finally { sheet.recycle(); }
        return "Rendered production button at six progress points; static capture, no input";
    }

    private static Activity open(Instrumentation test, long task) {
        return test.startActivitySync(new Intent(test.getTargetContext(), TaskDetailActivity.class)
                .putExtra("task_id", task).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
    }

    private static boolean completed(Context context, long id) {
        try (TaskStore store = new TaskStore(context)) {
            return store.getCandidatesByIds(List.of(id)).get(0).completed();
        }
    }

    private static CompletionButton control(Instrumentation test, Activity screen, String label) throws Exception {
        CompletionButton[] target = {null};
        await(test, () -> {
            target[0] = find(screen.getWindow().getDecorView(), label);
            return target[0] != null && target[0].isEnabled() && CompletionButton.canReward(target[0]);
        }, label);
        return target[0];
    }

    private static CompletionButton find(View view, String label) {
        if (view instanceof CompletionButton button && label.equals(String.valueOf(view.getContentDescription()))) return button;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            CompletionButton button = find(group.getChildAt(i), label);
            if (button != null) return button;
        }
        return null;
    }

    private static void describeControls(View view, StringBuilder report) {
        if (view instanceof CompletionButton button)
            report.append(" [").append(button.getContentDescription())
                    .append(" enabled=").append(button.isEnabled())
                    .append(" visible=").append(button.getLocalVisibleRect(new android.graphics.Rect())).append("]");
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            describeControls(group.getChildAt(i), report);
    }

    private static void await(Instrumentation test, java.util.concurrent.Callable<Boolean> ready, String label) throws Exception {
        long end = SystemClock.uptimeMillis() + 5000;
        do {
            if (onMain(test, ready)) return;
            SystemClock.sleep(12);
        } while (SystemClock.uptimeMillis() < end);
        throw new AssertionError("Not observed: " + label);
    }

    private static boolean onMain(Instrumentation test, java.util.concurrent.Callable<Boolean> ready) throws Exception {
        FutureTask<Boolean> read = new FutureTask<>(ready);
        test.runOnMainSync(read);
        return read.get();
    }

    private static void tap(Instrumentation test, View target) {
        int[] xy = new int[2];
        test.runOnMainSync(() -> {
            target.getLocationOnScreen(xy);
            xy[0] += target.getWidth() / 2; xy[1] += target.getHeight() / 2;
        });
        long now = SystemClock.uptimeMillis();
        PtaUiInteractionCheck.touch(test, now, now, 0, xy[0], xy[1]);
        SystemClock.sleep(50);
        PtaUiInteractionCheck.touch(test, now, SystemClock.uptimeMillis(), 1, xy[0], xy[1]);
    }

    private static void capture(Instrumentation test, String file) throws Exception {
        Bitmap source = test.getUiAutomation().takeScreenshot();
        float scale = Math.min(1f, 1000f / Math.max(source.getWidth(), source.getHeight()));
        Bitmap small = Bitmap.createScaledBitmap(source, Math.round(source.getWidth() * scale),
                Math.round(source.getHeight() * scale), true);
        try (var out = new FileOutputStream(new File(test.getTargetContext().getExternalCacheDir(),
                "completion-" + file + ".jpg"))) {
            small.compress(Bitmap.CompressFormat.JPEG, 85, out);
        } finally {
            if (small != source) small.recycle();
            source.recycle();
        }
    }
}
