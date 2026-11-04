package com.donglan.chrona;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.UUID;

/** Actual detail caller, native layout/animators and real pointer input; v33 emulator only. */
final class HeaderActionGroupDeviceCheck {
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context = test.getTargetContext();
        FixtureIsolation.require(context);
        String theme = args.getString("theme", "light");
        require("light".equals(theme) || "dark".equals(theme), "theme must be light or dark");
        int rotation = Integer.parseInt(args.getString("rotation", "0"));
        require(rotation == 0 || rotation == 1, "rotation must be 0 (portrait) or 1 (landscape)");
        require(UiMotion.isEnabled(), "requires existing enabled animations; does not alter system settings");
        String previousTheme = ThemeStore.mode(context);
        Activity page = null;
        long task = 0;
        File directory = new File(context.getFilesDir(), "header-actions-20261104");
        require(directory.isDirectory() || directory.mkdirs(), "create private JPEG output directory");
        String run = theme + "-r" + rotation + "-" + System.currentTimeMillis();
        StringBuilder report = new StringBuilder();
        Instrumentation.ActivityMonitor monitor = test.addMonitor(TaskDetailActivity.class.getName(), null, false);
        try {
            ThemeStore.setMode(context, theme);
            try (TaskStore store = new TaskStore(context)) {
                task = store.insertTask("操作胶囊独立草稿 " + UUID.randomUUID(), null,
                        "header-actions-device-check", System.currentTimeMillis());
                store.updateStatus(task, TaskRecord.NEEDS_REVIEW, null);
            }
            report.append("createdTask=").append(task).append(';');
            fixtureRecord(directory, run, task, "active");
            page = test.startActivitySync(new Intent(context, TaskDetailActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                    .putExtra("task_id", task));
            page = focusedActivity(test, monitor, page, task);
            require(test.getUiAutomation().setRotation(rotation == 0
                    ? android.app.UiAutomation.ROTATION_FREEZE_0 : android.app.UiAutomation.ROTATION_FREEZE_90),
                    "freeze real display rotation once after actual detail has focus and layout");
            page = stableActivity(test, monitor, page, task, rotation);
            Activity activity = page;
            View[] found = new View[1];
            for (int i = 0; i < 100 && found[0] == null; i++) {
                test.waitForIdleSync();
                onMain(test, () -> found[0] = activity.getWindow().getDecorView().findViewWithTag("detail_header_menu"));
                if (found[0] == null) SystemClock.sleep(40);
            }
            require(found[0] instanceof ImageButton, "production more ImageButton found");
            ImageButton more = (ImageButton) found[0];
            HeaderActionGroup group = (HeaderActionGroup) more.getParent();
            View completion = group.getChildAt(0);
            settle(test);
            onMain(test, () -> {
                require(group.getChildCount() == 3, "original two action caller");
                single(group, more);
                require(completion.getVisibility() == View.GONE, "completion initially hidden by real caller");
                require(more.getDrawable().getIntrinsicWidth() == dp(more, 24), "original 24dp icon");
                require(group.getBackground() != null && !group.getClipChildren(), "original surface and motion clipping contract");
            });
            capture(test, directory, run, "single-rest");
            press(test, activity, rotation, more, group, directory, run);

            onMain(test, () -> group.setActionVisible(completion, true));
            settle(test);
            onMain(test, () -> {
                require(group.getWidth() == dp(group, 48) * 2 + dp(group, 1), "two action measured width");
                require(visibleSeparators(group) == 1, "restored two actions have one separator");
            });
            capture(test, directory, run, "two-restored");

            // Pause the real production animator at a known nonterminal progress, then reverse it.
            // This verifies rendered native intermediate geometry without depending on screenshot latency.
            onMain(test, () -> {
                group.setActionVisible(completion, false);
                ValueAnimator motion = motion(group, completion);
                motion.pause(); motion.setCurrentFraction(.35f);
                require(completion.getAlpha() > 0 && completion.getAlpha() < 1, "real exit intermediate");
                separatorFollows(group.getChildAt(1), completion.getAlpha());
            });
            test.waitForIdleSync();
            capture(test, directory, run, "exit-paused-intermediate");
            onMain(test, () -> {
                float from = completion.getAlpha();
                group.setActionVisible(completion, true);
                require(Math.abs(completion.getAlpha() - from) < .001f, "reverse starts from current geometry");
                ValueAnimator motion = motion(group, completion);
                motion.pause(); motion.setCurrentFraction(.5f);
                require(completion.getAlpha() > from && completion.getAlpha() < 1, "real reversal intermediate");
                separatorFollows(group.getChildAt(1), completion.getAlpha());
            });
            test.waitForIdleSync();
            capture(test, directory, run, "reverse-paused-intermediate");
            onMain(test, () -> motion(group, completion).resume());
            settle(test);

            // Extra third action is a test-only extension of this same attached production group.
            View[] third = new View[1];
            onMain(test, () -> {
                third[0] = UiStyle.navigationIcon(activity, R.drawable.ic_refresh, "检查动作", () -> { });
                group.addAction(third[0], 48);
            });
            settle(test);
            onMain(test, () -> require(visibleSeparators(group) == 2, "three actions have two separators"));
            capture(test, directory, run, "three-rest");
            onMain(test, () -> group.setActionVisible(more, false));
            settle(test);
            onMain(test, () -> {
                require(visibleSeparators(group) == 1, "hidden middle leaves exactly one separator");
                require(group.getChildAt(1).getVisibility() == View.GONE, "separator before hidden middle gone");
                require(group.getChildAt(3).getVisibility() == View.VISIBLE, "separator before remaining third visible");
                require(group.getWidth() == dp(group, 48) * 2 + dp(group, 1), "middle-hidden measured width");
            });
            capture(test, directory, run, "middle-hidden");
            onMain(test, () -> { group.setActionVisible(completion, false); group.setActionVisible(third[0], false); });
            settle(test);
            onMain(test, () -> require(visibleSeparators(group) == 0 && group.getWidth() == 0, "all hidden has zero geometry"));
            onMain(test, () -> group.setActionVisible(more, true));
            settle(test);
            onMain(test, () -> single(group, more));
            capture(test, directory, run, "single-restored");
            report.append("PASS actual TaskDetailActivity single 48dp, two restored, middle of three hidden, all hidden, reversed native animator; real DOWN/UP and menu; paused intermediate JPEG frames; density=")
                    .append(activity.getResources().getDisplayMetrics().density)
                    .append(";orientation=").append(activity.getResources().getConfiguration().orientation)
                    .append(";rotation=").append(rotation)
                    .append(";theme=").append(theme).append(";output=").append(directory).append('/').append(run);
        } finally {
            try {
                Activity latest = monitor.getLastActivity();
                if (latest != null && latest.getIntent().getLongExtra("task_id", -1) == task) {
                    onMain(test, latest::finish);
                }
                if (page != null && page != latest) { Activity activity = page; onMain(test, activity::finish); }
                test.waitForIdleSync();
            } finally {
                try {
                    if (task > 0) try (TaskStore store = new TaskStore(context)) {
                        require(store.deleteTask(task), "delete only this check's draft");
                        require(store.getTask(task) == null, "draft deletion verified");
                        report.append(";deletedTask=").append(task);
                        fixtureRecord(directory, run, task, "deleted");
                    }
                } finally {
                    try { ThemeStore.setMode(context, previousTheme); }
                    finally {
                        try { test.removeMonitor(monitor); }
                        finally { require(test.getUiAutomation().setRotation(android.app.UiAutomation.ROTATION_UNFREEZE), "unfreeze real display rotation"); }
                    }
                }
            }
        }
        return report.toString();
    }

    private static Activity focusedActivity(Instrumentation test, Instrumentation.ActivityMonitor monitor,
            Activity initial, long task) {
        Activity candidate = initial;
        long deadline = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < deadline) {
            Activity latest = monitor.getLastActivity();
            if (latest != null && latest.getIntent().getLongExtra("task_id", -1) == task) candidate = latest;
            Activity current = candidate;
            boolean[] ready = new boolean[1];
            onMain(test, () -> {
                View decor = current.getWindow().getDecorView();
                ready[0] = !current.isDestroyed() && !current.isFinishing() && current.hasWindowFocus()
                        && decor.isAttachedToWindow() && decor.isLaidOut()
                        && decor.getWidth() > 0 && decor.getHeight() > 0;
            });
            if (ready[0]) return candidate;
            SystemClock.sleep(40);
        }
        Activity last = candidate;
        String[] state = new String[1];
        onMain(test, () -> state[0] = activityState(last, task));
        throw new AssertionError("Initial detail did not obtain focus/layout before rotation: " + state[0]);
    }
    private static Activity stableActivity(Instrumentation test, Instrumentation.ActivityMonitor monitor,
            Activity initial, long task, int rotation) {
        Activity candidate = initial;
        long deadline = SystemClock.uptimeMillis() + 15000, readySince = 0;
        while (SystemClock.uptimeMillis() < deadline) {
            Activity latest = monitor.getLastActivity();
            if (latest != null && latest != candidate && latest.getIntent().getLongExtra("task_id", -1) == task) {
                candidate = latest; readySince = 0;
            }
            Activity current = candidate;
            boolean[] ready = new boolean[1];
            onMain(test, () -> {
                View decor = current.getWindow().getDecorView();
                View menu = decor.findViewWithTag("detail_header_menu");
                ready[0] = !current.isDestroyed() && !current.isFinishing() && current.hasWindowFocus()
                        && decor.isLaidOut() && decor.getWidth() > 0 && decor.getHeight() > 0
                        && current.getResources().getConfiguration().orientation == expectedOrientation(rotation)
                        && decor.getDisplay() != null && decor.getDisplay().getRotation() == rotation
                        && menu != null && menu.isLaidOut() && menu.isShown();
            });
            if (ready[0]) {
                if (readySince == 0) readySince = SystemClock.uptimeMillis();
                if (SystemClock.uptimeMillis() - readySince >= 350) return candidate;
            } else readySince = 0;
            SystemClock.sleep(40);
        }
        Activity last = candidate;
        Activity monitored = monitor.getLastActivity();
        String[] diagnostics = new String[1];
        onMain(test, () -> diagnostics[0] = "requestedRotation=" + rotation
                + ";expectedOrientation=" + expectedOrientation(rotation)
                + ";monitorHits=" + monitor.getHits()
                + ";sameInitialCandidate=" + (initial == last)
                + ";sameMonitorCandidate=" + (monitored == last)
                + ";initial={" + activityState(initial, task) + "}"
                + ";candidate={" + activityState(last, task) + "}"
                + ";monitorLast={" + activityState(monitored, task) + "}");
        throw new AssertionError("No stable focused and laid-out detail Activity: " + diagnostics[0]);
    }
    private static String activityState(Activity activity, long expectedTask) {
        if (activity == null) return "null";
        View decor = activity.getWindow().getDecorView();
        View menu = decor.findViewWithTag("detail_header_menu");
        long task = activity.getIntent().getLongExtra("task_id", -1);
        return "instance=" + System.identityHashCode(activity)
                + ",destroyed=" + activity.isDestroyed() + ",finishing=" + activity.isFinishing()
                + ",focus=" + activity.hasWindowFocus() + ",decorFocus=" + decor.hasWindowFocus()
                + ",decorAttached=" + decor.isAttachedToWindow() + ",decorLaidOut=" + decor.isLaidOut()
                + ",decorWidth=" + decor.getWidth() + ",decorHeight=" + decor.getHeight()
                + ",configOrientation=" + activity.getResources().getConfiguration().orientation
                + ",displayRotation=" + (decor.getDisplay() == null ? "null" : decor.getDisplay().getRotation())
                + ",displayRotationIs1=" + (decor.getDisplay() != null && decor.getDisplay().getRotation() == 1)
                + ",menuExists=" + (menu != null)
                + ",menuLaidOut=" + (menu != null && menu.isLaidOut())
                + ",menuShown=" + (menu != null && menu.isShown())
                + ",taskId=" + task + ",expectedTaskId=" + expectedTask + ",taskMatches=" + (task == expectedTask);
    }
    private static int expectedOrientation(int rotation) {
        return rotation == 0 ? android.content.res.Configuration.ORIENTATION_PORTRAIT
                : android.content.res.Configuration.ORIENTATION_LANDSCAPE;
    }
    private static void press(Instrumentation test, Activity activity, int rotation, View more, HeaderActionGroup group, File directory, String run) throws Exception {
        int[] at = new int[2];
        onMain(test, () -> {
            View decor = activity.getWindow().getDecorView();
            require(!activity.isDestroyed() && activity.hasWindowFocus() && decor.isLaidOut()
                    && more.getRootView() == decor && more.isShown(), "pointer target belongs to stable focused detail window");
            require(activity.getResources().getConfiguration().orientation == expectedOrientation(rotation)
                    && decor.getDisplay().getRotation() == rotation, "actual orientation and display rotation match request before pointer input");
            more.getLocationOnScreen(at);
        });
        long down = SystemClock.uptimeMillis();
        boolean released = false;
        try {
            pointer(test, more, at, down, MotionEvent.ACTION_DOWN);
            SystemClock.sleep(300);
            onMain(test, () -> require(group.getScaleX() > 1.08f && group.getScaleY() > 1.08f, "real DOWN holds original 1.10 group response"));
            capture(test, directory, run, "single-held");
            pointer(test, more, at, down, MotionEvent.ACTION_UP); released = true;
            settle(test);
            onMain(test, () -> require(Math.abs(group.getScaleX() - 1) < .01f, "UP restores group identity"));
            require(!MenuDeviceCheck.rows().isEmpty(), "real UP invokes original more menu");
            capture(test, directory, run, "single-up-menu");
            test.sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK);
            settle(test);
        } finally {
            if (!released) pointer(test, more, at, down, MotionEvent.ACTION_CANCEL);
        }
    }
    private static void pointer(Instrumentation test, View target, int[] at, long down, int action) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                at[0] + target.getWidth()/2f, at[1] + target.getHeight()/2f, 0);
        try { test.sendPointerSync(event); } finally { event.recycle(); }
    }
    private static ValueAnimator motion(HeaderActionGroup group, View view) {
        try {
            Field actions = HeaderActionGroup.class.getDeclaredField("actions"); actions.setAccessible(true);
            Object action = ((Map<?, ?>) actions.get(group)).get(view);
            Field motion = action.getClass().getDeclaredField("motion"); motion.setAccessible(true);
            ValueAnimator animator = (ValueAnimator) motion.get(action);
            require(animator != null && animator.isStarted(), "actual production visibility animator exists");
            return animator;
        } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void separatorFollows(View separator, float alpha) {
        require(Math.abs(separator.getAlpha() - alpha) < .001f, "intermediate separator follows preceding action");
        require(separator.getLayoutParams().width == Math.round(dp(separator, 1) * alpha), "intermediate separator width");
    }
    private static int visibleSeparators(HeaderActionGroup group) {
        int count = 0;
        for (int i = 1; i < group.getChildCount(); i += 2) if (group.getChildAt(i).getVisibility() == View.VISIBLE) count++;
        return count;
    }
    private static void single(HeaderActionGroup group, View action) {
        require(action.getVisibility() == View.VISIBLE && action.getWidth() == dp(action, 48)
                && action.getHeight() == dp(action, 48), "single action actual 48dp geometry");
        require(group.getWidth() == dp(group, 48) && visibleSeparators(group) == 0, "single group has no leading/trailing separator or space");
        for (int i = 1; i < group.getChildCount(); i += 2)
            require(group.getChildAt(i).getLayoutParams().width == 0, "hidden separator slot zero");
    }
    private static void capture(Instrumentation test, File directory, String run, String name) throws Exception {
        Bitmap original = test.getUiAutomation().takeScreenshot();
        require(original != null, "actual screenshot available");
        Bitmap compact = original;
        try {
            int longest = Math.max(original.getWidth(), original.getHeight());
            if (longest > 1600) compact = Bitmap.createScaledBitmap(original,
                    Math.round(original.getWidth() * 1600f / longest), Math.round(original.getHeight() * 1600f / longest), true);
            try (FileOutputStream out = new FileOutputStream(new File(directory, run + "-" + name + ".jpg"))) {
                require(compact.compress(Bitmap.CompressFormat.JPEG, 86, out), "JPEG compression successful");
            }
        } finally { if (compact != original) compact.recycle(); original.recycle(); }
    }
    private static void fixtureRecord(File directory, String run, long task, String status) throws java.io.IOException {
        try (FileOutputStream out = new FileOutputStream(new File(directory, run + "-fixture.txt"))) {
            out.write(("createdTask=" + task + "\nstatus=" + status + "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }
    }
    private static void settle(Instrumentation test) { SystemClock.sleep(600); test.waitForIdleSync(); }
    private static void onMain(Instrumentation test, Runnable action) {
        Throwable[] failure = new Throwable[1];
        test.runOnMainSync(() -> { try { action.run(); } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw new AssertionError("Main-thread header check failed", failure[0]);
    }
    private static int dp(View view, int value) { return Math.round(value * view.getResources().getDisplayMetrics().density); }
    private static void require(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
