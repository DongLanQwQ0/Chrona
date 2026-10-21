package com.donglan.chrona;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.os.*;
import android.view.*;
import android.widget.*;
import com.donglan.chrona.data.*;
import com.donglan.chrona.processing.ProcessingJobService;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Real notifications and touch/focus drawing order; disposable emulator only, no AI/calendar writes. */
final class NotificationLayersDeviceCheck {
    private static int assertions;
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context = test.getTargetContext(); FixtureIsolation.require(context); assertions = 0;
        var guide = context.getSharedPreferences("usage_guide", 0);
        int previousGuide = guide.getInt("version", 0);
        Field version = GuideActivity.class.getDeclaredField("GUIDE_VERSION"); version.setAccessible(true);
        guide.edit().putInt("version", version.getInt(null)).commit();
        String theme = args.getString("theme", "light"), oldTheme = ThemeStore.mode(context);
        ThemeStore.setMode(context, theme);
        String proof = theme + args.getString("evidenceSuffix", "");
        long taskId = 0; Activity activity = null;
        NotificationManager notifications = context.getSystemService(NotificationManager.class);
        try {
            try (TaskStore store = new TaskStore(context)) {
                List<Long> stale = new ArrayList<>();
                try (var rows = store.getReadableDatabase().rawQuery("SELECT id FROM tasks WHERE raw_text=? AND source=?", new String[]{"通知与层级合成验收", "device_check"})) {
                    while (rows.moveToNext()) stale.add(rows.getLong(0));
                }
                for (long old : stale) store.deleteTask(old);
                taskId = store.insertTask("通知与层级合成验收", null, "device_check", System.currentTimeMillis());
                long start = java.time.LocalDate.now().plusDays(1).atTime(14, 0)
                        .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
                store.appendCandidate(new EventCandidate(0, taskId, "微积分复习讨论", start, start + 3600000,
                        java.time.ZoneId.systemDefault().getId(), "东1-201", "携带第三章习题", 15, false,
                        null, "task", false));
                store.updateStatus(taskId, TaskRecord.READY, null);
            }
            test.getUiAutomation().grantRuntimePermission(context.getPackageName(), android.Manifest.permission.POST_NOTIFICATIONS);
            ProcessingJobService service = new ProcessingJobService();
            Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class); attach.setAccessible(true); attach.invoke(service, context);
            Method notify = ProcessingJobService.class.getDeclaredMethod("notifyResult", long.class, String.class); notify.setAccessible(true);
            notify.invoke(service, taskId, "已自动确认 1 项日程");
            long id = taskId;
            await(test, () -> Arrays.stream(notifications.getActiveNotifications()).anyMatch(n -> n.getId() == (int) id), "real notification delivered");
            Notification notice = Arrays.stream(notifications.getActiveNotifications()).filter(n -> n.getId() == (int) id).findFirst().orElseThrow().getNotification();
            String body = notice.extras.getCharSequence(Notification.EXTRA_TEXT).toString();
            require(body.contains("微积分复习讨论") && body.contains("14:00") && body.contains("15:00")
                    && body.contains("东1-201") && body.contains("提前 15 分钟") && body.contains("携带第三章习题"), "collapsed extras contain complete event detail");
            require(body.equals(notice.extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString()), "expanded and collapsed use same actual details");
            require(notice.contentIntent != null && notice.actions.length > 0 && notice.contentIntent.equals(notice.actions[0].actionIntent), "detail entry and action retained");
            shell(test, "cmd statusbar expand-notifications"); SystemClock.sleep(1400);
            capture(test, context, "notification-details-" + proof);
            var serviceInfo = test.getUiAutomation().getServiceInfo();
            serviceInfo.flags |= android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS;
            test.getUiAutomation().setServiceInfo(serviceInfo);
            var root = test.getUiAutomation().getRootInActiveWindow();
            var expander = node(root, "android:id/expand_button");
            require(expander != null && "收起".contentEquals(expander.getContentDescription()), "own expanded notification is accessible");
            require(expander.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK), "native notification collapses without opening app");
            SystemClock.sleep(500); capture(test, context, "notification-collapsed-" + proof);
            shell(test, "cmd statusbar collapse");
            notifications.cancel((int) taskId);

            Instrumentation.ActivityMonitor monitor = test.addMonitor(DashboardActivity.class.getName(), null, false);
            try {
                test.runOnMainSync(() -> context.startActivity(new Intent(context, DashboardActivity.class)
                        .putExtra(DashboardActivity.EXTRA_SECTION, DashboardActivity.INBOX).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)));
                activity = monitor.waitForActivityWithTimeout(10000);
            } finally { test.removeMonitor(monitor); }
            if (activity == null) throw new AssertionError("inbox not opened");
            Activity screen = activity; await(test, screen::hasWindowFocus, "inbox focused");
            await(test, () -> field(screen, "filterChips") != null && field(screen, "categoryChip") != null, "inbox controls ready");
            SystemClock.sleep(700);
            DockNavigationLayout dock = (DockNavigationLayout) field(screen, "filterChips");
            View category = (View) field(screen, "categoryChip");
            View selector = dock.isAttachedToWindow() ? dock : (View) dock.getTag();
            ViewGroup row = (ViewGroup) category.getParent();
            View host = branch(selector, row);
            float resting = host.getTranslationZ();
            int order = row.indexOfChild(host), left = category.getLeft();
            int count = dock.isAttachedToWindow() ? dock.itemCount() : 1;
            for (int index : new int[]{0, count - 1}) {
                View target = dock.isAttachedToWindow() ? dock.itemAt(index) : selector;
                Gesture gesture = down(test, target); SystemClock.sleep(700);
                require(host.getZ() > category.getZ(), "held selector is above adjacent filter");
                require(row.indexOfChild(host) == order && category.getLeft() == left, "drawing priority never reorders layout");
                requirePinnedAbove(screen, selector);
                capture(test, context, "inbox-held-" + index + "-" + proof);
                gesture.end(test, MotionEvent.ACTION_CANCEL, 0, 0);
                await(test, () -> Math.abs(host.getTranslationZ() - resting) < .001f, "release restores selector branch Z");
            }
            if (dock.isAttachedToWindow() && android.animation.ValueAnimator.areAnimatorsEnabled()) {
                int[] frames = {0, 0}; boolean[] obscured = {false};
                ViewTreeObserver.OnPreDrawListener watch = () -> {
                    if (dock.isAnimating()) {
                        frames[0]++;
                        if (host.getZ() <= category.getZ()) obscured[0] = true;
                        if (((DockSpring)field(dock, "spring")).scale < 1.001f) frames[1]++;
                    }
                    return true;
                };
                test.runOnMainSync(() -> dock.getViewTreeObserver().addOnPreDrawListener(watch));
                Gesture gesture = down(test, dock.itemAt(count - 1));
                SystemClock.sleep(700);
                gesture.move(test, -80, 0); SystemClock.sleep(20);
                gesture.move(test, 60, 0); SystemClock.sleep(20);
                gesture.end(test, MotionEvent.ACTION_UP, 180, -45);
                await(test, () -> frames[1] > 0, "late inertia frame after scale returns");
                capture(test, context, "inbox-late-inertia-" + proof);
                await(test, () -> !dock.isAnimating(), "drag-out settles with original Dock physics");
                test.runOnMainSync(() -> dock.getViewTreeObserver().removeOnPreDrawListener(watch));
                require(frames[0] > 5 && frames[1] > 0 && !obscured[0], "every rendered inertia frame stays above neighbor, including late tail");
                android.util.Log.i("ChronaLayerFrames", "rendered=" + frames[0] + " late=" + frames[1] + " obscured=" + obscured[0]);
                require(Math.abs(host.getTranslationZ() - resting) < .001f, "drag-out clears temporary layer");
            }
            boolean focusable = category.isFocusable(), touchFocusable = category.isFocusableInTouchMode();
            float categoryRest = category.getTranslationZ();
            test.runOnMainSync(() -> { category.setFocusableInTouchMode(true); category.requestFocus(); });
            await(test, category::hasFocus, "actual focus assigned");
            require(category.getZ() > host.getZ(), "focused neighbor is above idle selector");
            requirePinnedAbove(screen, category);
            capture(test, context, "inbox-focused-filter-" + proof);
            test.runOnMainSync(() -> { category.clearFocus(); category.setFocusableInTouchMode(touchFocusable); category.setFocusable(focusable); });
            require(Math.abs(category.getTranslationZ() - categoryRest) < .001f, "focus release restores prior Z");
            nativeLayers(test, screen);
            return new JSONObject().put("assertions", assertions).put("notificationBody", body)
                    .put("selector", dock.isAttachedToWindow() ? "dock" : "compact").put("theme", theme).toString();
        } finally {
            shell(test, "cmd statusbar collapse");
            if (taskId != 0) { notifications.cancel((int) taskId); try (TaskStore store = new TaskStore(context)) { store.deleteTask(taskId); } }
            if (activity != null) { Activity close = activity; test.runOnMainSync(close::finish); }
            guide.edit().putInt("version", previousGuide).commit(); ThemeStore.setMode(context, oldTheme);
        }
    }

    private static void nativeLayers(Instrumentation test, Activity activity) {
        Throwable[] failure = new Throwable[1];
        test.runOnMainSync(() -> { try {
            FrameLayout parent = new FrameLayout(activity), nested = new FrameLayout(activity);
            View active = new View(activity), neighbor = new View(activity), pinned = new View(activity);
            active.setTranslationZ(3); neighbor.setElevation(12); pinned.setTranslationZ(2);
            nested.addView(active, new FrameLayout.LayoutParams(50, 50));
            parent.addView(nested, new FrameLayout.LayoutParams(100, 100)); parent.addView(neighbor, new FrameLayout.LayoutParams(100, 100));
            parent.addView(pinned, new FrameLayout.LayoutParams(100, 100));
            ViewGroup root = activity.findViewById(android.R.id.content); root.addView(parent, new ViewGroup.LayoutParams(100, 100));
            InteractionLayers.pin(pinned);
            try {
                for (int i = 0; i < 30; i++) {
                    InteractionLayers.setPressed(active, 1); InteractionLayers.focus(null, active);
                    require(nested.getZ() > neighbor.getZ() && pinned.getZ() > nested.getZ(), "nested active branch and pinned hierarchy");
                    float raised = nested.getZ(); InteractionLayers.refresh();
                    require(nested.getZ() == raised, "repeated updates never accumulate Z");
                    InteractionLayers.setPressed(active, 0);
                    require(nested.getZ() > neighbor.getZ(), "focus independently retains layer after press");
                    InteractionLayers.focus(active, null);
                    require(active.getTranslationZ() == 3 && nested.getTranslationZ() == 0, "restore exact nonzero baseline");
                }
                InteractionLayers.setPressed(active, 1); nested.removeView(active);
                require(active.getTranslationZ() == 3 && nested.getTranslationZ() == 0, "detach restores leaf and ancestors");
            } finally { root.removeView(parent); }
            require(pinned.getTranslationZ() == 2, "detached pin restores baseline");
        } catch (Throwable error) { failure[0] = error; } });
        if (failure[0] != null) throw new AssertionError("native layers", failure[0]);
    }
    private static void requirePinnedAbove(Activity activity, View target) {
        View navigation = (View) field(activity, "navigation");
        View a = target, b = navigation;
        Set<View> parents = new HashSet<>(); for (View n = a; n != null; n = n.getParent() instanceof View v ? v : null) parents.add(n);
        View common = b; while (!parents.contains(common)) common = (View) common.getParent();
        a = branch(a, (ViewGroup) common); b = branch(b, (ViewGroup) common);
        require(b.getZ() > a.getZ(), "fixed bottom navigation remains above active content");
    }
    private static View branch(View view, ViewGroup parent) { while (view.getParent() != parent) view = (View) view.getParent(); return view; }
    private static Object field(Object target, String name) { try { Field f = target.getClass().getDeclaredField(name); f.setAccessible(true); return f.get(target); } catch (Exception e) { throw new IllegalStateException(e); } }
    private static Gesture down(Instrumentation test, View target) {
        int[] xy = new int[2]; test.runOnMainSync(() -> target.getLocationOnScreen(xy));
        Gesture gesture = new Gesture(SystemClock.uptimeMillis(), xy[0] + target.getWidth()/2f, xy[1] + target.getHeight()/2f);
        gesture.end(test, MotionEvent.ACTION_DOWN, 0, 0); return gesture;
    }
    private record Gesture(long start, float x, float y) {
        void move(Instrumentation test, float dx, float dy) { end(test, MotionEvent.ACTION_MOVE, dx, dy); }
        void end(Instrumentation test, int action, float dx, float dy) {
            MotionEvent event = MotionEvent.obtain(start, SystemClock.uptimeMillis(), action, x + dx, y + dy, 0);
            test.sendPointerSync(event); event.recycle();
        }
    }
    private static void shell(Instrumentation test, String command) throws Exception { try (var output = test.getUiAutomation().executeShellCommand(command); var input = new ParcelFileDescriptor.AutoCloseInputStream(output)) { input.readAllBytes(); } }
    private static void capture(Instrumentation test, Context context, String name) throws Exception {
        test.runOnMainSync(() -> {}); SystemClock.sleep(150);
        Bitmap image = test.getUiAutomation().takeScreenshot(); if (image == null) throw new AssertionError("No screenshot");
        try (var out = new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) { image.compress(Bitmap.CompressFormat.PNG, 100, out); } finally { image.recycle(); }
    }
    private static void await(Instrumentation test, BooleanSupplier ready, String label) { long until = SystemClock.uptimeMillis() + 10000; do { test.runOnMainSync(() -> {}); if (ready.getAsBoolean()) return; SystemClock.sleep(20); } while (SystemClock.uptimeMillis() < until); throw new AssertionError(label); }
    private static void require(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    private static android.view.accessibility.AccessibilityNodeInfo node(android.view.accessibility.AccessibilityNodeInfo root, String id) {
        if (root == null) return null;
        if (id.equals(root.getViewIdResourceName())) return root;
        for (int i = 0; i < root.getChildCount(); i++) { var found = node(root.getChild(i), id); if (found != null) return found; }
        return null;
    }
}
