package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real native pages, isolated synthetic captures only. Never writes a calendar provider. */
final class TodoDueDefaultDeviceCheck {
    private static final long HALF_HOUR = 30 * 60_000L;
    private static final String SYNTHETIC_ACCOUNT = "00000000-0000-0000-0000-000000000036";
    private static final String[] TITLES = {
            "默认时段验收：整理本周阅读材料与讨论提纲，核对每一条参考资料并准备分享",
            "学校作业截止点验收", "明确执行时段验收"};
    private static int assertions;

    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context = test.getTargetContext();
        if (!"com.donglan.chrona.followup.validation.v33".equals(context.getPackageName()))
            throw new IllegalStateException("Isolated followup package only");
        if (!("ranchu".equals(Build.HARDWARE) || "goldfish".equals(Build.HARDWARE))
                || !(Build.MODEL.contains("Emulator") || Build.MODEL.startsWith("Android SDK built for")
                || Build.PRODUCT.startsWith("sdk_gphone")))
            throw new IllegalStateException("Official Android emulator only");
        ZjuSettingsStore school = new ZjuSettingsStore(context);
        if ((!school.account().isEmpty() || school.configured())
                && !SYNTHETIC_ACCOUNT.equals(school.account()))
            throw new IllegalStateException("Existing non-synthetic school account; fixture refused");
        // Reconciliation during native page lifecycle must not alter pre-existing linked rows.
        try (TaskStore store = new TaskStore(context)) {
            if (!store.linkedCalendarIds().isEmpty())
                throw new IllegalStateException("Existing provider links; fixture refused without altering them");
        }
        assertions = 0;
        String theme = args.getString("theme", "light");
        if (!"light".equals(theme) && !"dark".equals(theme))
            throw new IllegalArgumentException("theme must be light or dark");
        String prefix = "todo-due-default-" + theme;
        JSONArray images = new JSONArray();
        SharedPreferences guide = context.getSharedPreferences("usage_guide", 0);
        SharedPreferences homePrefs = context.getSharedPreferences("home_timeline", 0);
        Map<String, ?> oldGuide = guide.getAll(), oldHome = homePrefs.getAll();
        Map<String, ?> oldSchool = school.prefs.getAll();
        SharedPreferences reminderPrefs = AgendaReminderPreferences.prefs(context);
        Map<String, ?> oldReminders = reminderPrefs.getAll();
        String oldTheme = ThemeStore.mode(context);
        long[] tasks = new long[3];
        long[] ids = new long[3];
        Activity[] detail = new Activity[1];
        Activity home = null;
        Instrumentation.ActivityMonitor monitor = test.addMonitor(TaskDetailActivity.class.getName(), null, false);
        try {
            school.prefs.edit().putBoolean("enabled", false).commit();
            AgendaReminderPreferences.setEnabled(context, false);
            context.getSystemService(android.app.NotificationManager.class).cancelAll();
            Field guideVersion = GuideActivity.class.getDeclaredField("GUIDE_VERSION");
            guideVersion.setAccessible(true);
            guide.edit().putInt("version", guideVersion.getInt(null)).commit();
            homePrefs.edit().putBoolean("include_system_calendar", false)
                    .putBoolean("widget_include_system_calendar", false).putInt("item_limit", 50).commit();
            ThemeStore.setMode(context, theme);
            long now = System.currentTimeMillis();
            long due = (now / 60_000L + 40) * 60_000L;
            long schoolDue = due + 60 * 60_000L;
            long explicitStart = due + 120 * 60_000L, explicitEnd = explicitStart + 45 * 60_000L;
            try (TaskStore store = new TaskStore(context)) {
                for (int i = 0; i < tasks.length; i++) {
                    tasks[i] = store.insertTask("默认执行区间原生验收 " + now + " " + i,
                            null, "device_check", now);
                    ids[i] = store.appendCandidate(new EventCandidate(0, tasks[i], TITLES[i],
                            i == 2 ? explicitStart : null, i == 2 ? explicitEnd : null,
                            ZoneId.systemDefault().getId(), "", "合成验收数据", null, false, null,
                            i == 1 ? "homework" : "task", i == 0, false, EventCandidate.CERTAIN,
                            i == 0 ? due : i == 1 ? schoolDue : explicitEnd + HALF_HOUR, null, false));
                    store.updateStatus(tasks[i], TaskRecord.READY, null);
                }
            }
            verifyRaw(context, tasks, due, schoolDue, explicitStart, explicitEnd);
            AgendaRepository.Snapshot agenda = AgendaRepository.load(context, now - HALF_HOUR,
                    explicitEnd + 2 * HALF_HOUR, false, ZoneId.systemDefault(), new CancellationSignal());
            AgendaEntry ordinary = entry(agenda, ids[0]), homework = entry(agenda, ids[1]), explicit = entry(agenda, ids[2]);
            require(ordinary.start == due - HALF_HOUR && ordinary.end == due && !ordinary.allDay,
                    "AgendaRepository uses timed default interval despite raw allDay");
            require(homework.start == schoolDue && homework.end == schoolDue, "homework remains deadline point");
            require(explicit.start == explicitStart && explicit.end == explicitEnd, "explicit interval unchanged");
            WidgetAgenda widget = WidgetAgenda.load(context);
            require(!widget.failed, "real widget snapshot succeeds");
            WidgetAgenda.Item widgetItem = widget.next != null && widget.next.id == ids[0] ? widget.next : null;
            for (WidgetAgenda.Item item : widget.today) if (item.id == ids[0]) widgetItem = item;
            require(widgetItem != null && widgetItem.start == due - HALF_HOUR && widgetItem.end == due
                    && !widgetItem.allDay, "real widget model contains default interval in next/today");
            JSONObject widgetEvidence = new JSONObject().put("defaultItemIndex", index(widget.today, ids[0]))
                    .put("todayStartPosition", widget.todayStartPosition()).put("launcherTested", false);

            home = test.startActivitySync(new Intent(context, DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity dashboard = home;
            waitFor(test, () -> homeCard(dashboard.getWindow().getDecorView(), TITLES[0], "起始时间：") != null,
                    "default item in real home timeline");
            View dueCard = homeCard(home.getWindow().getDecorView(), TITLES[0], "起始时间：");
            checkTime(test, dueCard, "起始时间：");
            checkTime(test, dueCard, "终止时间：");
            expectClock(dueCard, "起始时间：", due - HALF_HOUR);
            expectClock(dueCard, "终止时间：", due);
            reveal(test, dueCard);
            capture(test, prefix + "-home-default", images);
            require(new HomeTimelineEntry(ordinaryCandidate(context, tasks[0])).timestampMillis == due - HALF_HOUR,
                    "home model positioned at execution start");
            waitFor(test, () -> homeCard(dashboard.getWindow().getDecorView(), TITLES[1], "作业截止时间：") != null,
                    "school homework in real home timeline");
            View schoolCard = homeCard(home.getWindow().getDecorView(), TITLES[1], "作业截止时间：");
            require(findDescription(schoolCard, "终止时间：") == null, "homework has no fabricated end node");
            checkTime(test, schoolCard, "作业截止时间：");
            reveal(test, schoolCard);
            capture(test, prefix + "-home-homework", images);
            View explicitCard = homeCard(home.getWindow().getDecorView(), TITLES[2], "起始时间：");
            require(explicitCard != null, "explicit item remains in home timeline");
            checkTime(test, explicitCard, "起始时间：");
            checkTime(test, explicitCard, "终止时间：");
            expectClock(explicitCard, "起始时间：", explicitStart);
            expectClock(explicitCard, "终止时间：", explicitEnd);
            reveal(test, explicitCard);
            capture(test, prefix + "-home-explicit", images);

            Method switchTo = DashboardActivity.class.getDeclaredMethod("switchTo", int.class);
            switchTo.setAccessible(true);
            test.runOnMainSync(() -> {
                try { switchTo.invoke(dashboard, DashboardActivity.SCHEDULE); }
                catch (ReflectiveOperationException e) { throw new AssertionError(e); }
            });
            waitFor(test, () -> !Boolean.TRUE.equals(field(dashboard, "pageTransitionRunning"))
                    && Integer.valueOf(DashboardActivity.SCHEDULE).equals(field(dashboard, "section"))
                    && !Boolean.TRUE.equals(field(dashboard, "scheduleResultLoading"))
                    && field(dashboard, "scheduleBuildSteps") == null
                    && field(dashboard, "results") instanceof View currentResults
                    && currentResults.isShown() && findText(currentResults, TITLES[0]) != null, "real schedule list");
            // The decor retains the old home tree during native page transitions. Search only
            // the active schedule results, never a matching time label in a cached home card.
            View scheduleResults = (View) field(dashboard, "results");
            View scheduleCard = homeCard(scheduleResults, TITLES[0], "时间：");
            require(scheduleCard != null, "default due-only task is listed as scheduled");
            TextView interval = findDescription(scheduleCard, "时间：");
            reveal(test, scheduleCard);
            capture(test, prefix + "-schedule-default", images);
            EventCandidate diagnosticCandidate = ordinaryCandidate(context, tasks[0]);
            String diagnostic = " actualText=" + interval.getText() + " contentDescription="
                    + interval.getContentDescription() + " candidateId=" + diagnosticCandidate.id
                    + " taskId=" + diagnosticCandidate.taskId + " rawStart=" + diagnosticCandidate.startAtMillis
                    + " rawEnd=" + diagnosticCandidate.endAtMillis + " rawDue=" + diagnosticCandidate.dueAtMillis
                    + " rawAllDay=" + diagnosticCandidate.allDay + " category=" + diagnosticCandidate.category
                    + " hasDefault=" + diagnosticCandidate.hasDefaultExecutionInterval();
            require(interval.getText().toString().contains(" — "),
                    "list displays interval instead of deadline-only text;" + diagnostic);
            java.text.DateFormat format = java.text.DateFormat.getDateTimeInstance(
                    java.text.DateFormat.MEDIUM, java.text.DateFormat.SHORT);
            require(interval.getText().toString().equals(format.format(new java.util.Date(due - HALF_HOUR))
                    + " — " + format.format(new java.util.Date(due))), "list displays exact default boundaries;" + diagnostic);
            checkTime(test, scheduleCard, "时间：");
            // Keep the already inspected card position. Revealing just its title can move it
            // behind a header; the non-interactive time caption bubbles to this same real card.
            tap(test, dashboard, scheduleCard, interval, prefix, images);
            detail[0] = test.waitForMonitorWithTimeout(monitor, 10000);
            require(detail[0] instanceof TaskDetailActivity, "real schedule card opens native task details");
            require(detail[0].getIntent().getLongExtra("task_id", -1) == tasks[0]
                    && detail[0].getIntent().getLongExtra("candidate_id", -1) == ids[0], "detail receives actual candidate identity");
            waitFor(test, () -> findHint(detail[0].getWindow().getDecorView(), "欸~这好像还没有时间") != null,
                    "detail retains empty original start field");
            TextView startField = findHint(detail[0].getWindow().getDecorView(), "欸~这好像还没有时间");
            TextView endField = findHint(detail[0].getWindow().getDecorView(), "遥遥路途，没有终点~");
            require(startField.length() == 0 && endField != null && endField.length() == 0,
                    "detail retains both empty original execution fields");
            reveal(test, (View) startField.getParent().getParent());
            capture(test, prefix + "-detail-empty-raw", images);
            test.getUiAutomation().performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK);
            waitFor(test, () -> detail[0].isFinishing() && dashboard.hasWindowFocus(), "native detail back returns without saving");
            verifyRaw(context, tasks, due, schoolDue, explicitStart, explicitEnd);
            capture(test, prefix + "-returned", images);
            return new JSONObject().put("passed", true).put("assertions", assertions).put("theme", theme)
                    .put("widthDp", dashboard.getResources().getConfiguration().screenWidthDp)
                    .put("fontScale", dashboard.getResources().getConfiguration().fontScale)
                    .put("screenshots", images).put("widget", widgetEvidence)
                    .put("providerWrites", false).put("schoolRequests", false).toString();
        } finally {
            test.removeMonitor(monitor);
            Activity finalHome = home;
            test.runOnMainSync(() -> {
                if (detail[0] != null && !detail[0].isFinishing()) detail[0].finish();
                if (finalHome != null) finalHome.finish();
            });
            try (TaskStore store = new TaskStore(context)) {
                for (long task : tasks) if (task > 0) store.deleteTask(task);
            } finally {
                restore(guide, oldGuide); restore(homePrefs, oldHome); restore(school.prefs, oldSchool);
                ThemeStore.setMode(context, oldTheme);
                AgendaReminderPreferences.setEnabled(context, !oldReminders.containsKey("enabled")
                        || Boolean.TRUE.equals(oldReminders.get("enabled")));
                restore(reminderPrefs, oldReminders);
            }
        }
    }

    private static EventCandidate ordinaryCandidate(Context context, long task) {
        try (TaskStore store = new TaskStore(context)) { return store.getCandidates(task).get(0); }
    }
    private static void verifyRaw(Context context, long[] tasks, long due, long schoolDue, long start, long end) {
        try (TaskStore store = new TaskStore(context)) {
            EventCandidate item = store.getCandidates(tasks[0]).get(0);
            require(item.startAtMillis == null && item.endAtMillis == null && item.dueAtMillis == due && item.allDay,
                    "ordinary raw start/end remain null; explicit due/allDay preserved");
            EventCandidate homework = store.getCandidates(tasks[1]).get(0);
            require(homework.startAtMillis == null && homework.endAtMillis == null && homework.dueAtMillis == schoolDue,
                    "homework raw deadline-only fields preserved");
            EventCandidate explicit = store.getCandidates(tasks[2]).get(0);
            require(explicit.startAtMillis == start && explicit.endAtMillis == end, "explicit raw times preserved");
        }
    }
    private static AgendaEntry entry(AgendaRepository.Snapshot snapshot, long id) {
        for (AgendaEntry item : snapshot.entries) if (item.id == id && !item.system) return item;
        throw new AssertionError("Missing real agenda entry " + id);
    }
    private static int index(List<WidgetAgenda.Item> items, long id) {
        for (int i = 0; i < items.size(); i++) if (items.get(i).id == id) return i;
        return -1;
    }
    private static View homeCard(View root, String title, String description) {
        if (!root.isShown()) return null;
        if (root instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = homeCard(group.getChildAt(i), title, description);
                if (found != null) return found;
            }
            if (root.isClickable() && findText(root, title) != null && findDescription(root, description) != null) return root;
        }
        return null;
    }
    private static TextView findText(View view, String text) {
        if (view instanceof TextView label && text.contentEquals(label.getText())) return label;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findText(group.getChildAt(i), text); if (found != null) return found;
        }
        return null;
    }
    private static TextView findHint(View view, String hint) {
        if (view instanceof TextView label && label.getHint() != null && hint.contentEquals(label.getHint())) return label;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findHint(group.getChildAt(i), hint); if (found != null) return found;
        }
        return null;
    }
    private static TextView findDescription(View view, String prefix) {
        if (view instanceof TextView label && label.getContentDescription() != null
                && label.getContentDescription().toString().startsWith(prefix)) return label;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            TextView found = findDescription(group.getChildAt(i), prefix); if (found != null) return found;
        }
        return null;
    }
    private static void checkTime(Instrumentation test, View card, String prefix) {
        test.runOnMainSync(() -> {
            TextView text = findDescription(card, prefix);
            require(text != null && text.getLayout() != null && text.getWidth() > 0, prefix + " laid out");
            for (int i = 0; i < text.getLayout().getLineCount(); i++)
                require(text.getLayout().getEllipsisCount(i) == 0, prefix + " complete line " + i);
            require(text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop()
                    - text.getCompoundPaddingBottom() + 1, prefix + " complete text height");
            Rect bounds = new Rect(0, 0, text.getWidth(), text.getHeight());
            ((ViewGroup) card).offsetDescendantRectToMyCoords(text, bounds);
            require(bounds.left >= 0 && bounds.right <= card.getWidth() && bounds.top >= 0
                    && bounds.bottom <= card.getHeight(), prefix + " inside actual card");
        });
    }
    private static void expectClock(View card, String prefix, long millis) {
        String clock = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                .format(new java.util.Date(millis));
        require(findDescription(card, prefix).getText().toString().endsWith(clock), prefix + " exact clock " + clock);
    }
    private static void reveal(Instrumentation test, View view) {
        test.runOnMainSync(() -> view.requestRectangleOnScreen(new Rect(0, 0, view.getWidth(), view.getHeight()), true));
        SystemClock.sleep(400); test.waitForIdleSync();
    }
    private static void tap(Instrumentation test, Activity activity, View card, View view,
            String prefix, JSONArray images) throws Exception {
        int[] point = new int[2];
        String[] evidence = new String[1];
        capture(test, prefix + "-before-detail-tap", images);
        test.runOnMainSync(() -> {
            View decor = activity.getWindow().getDecorView();
            require(activity.hasWindowFocus() && decor.hasWindowFocus() && view.getRootView() == decor,
                    "touch target belongs to focused Dashboard window; activityFocus=" + activity.hasWindowFocus()
                    + " decorFocus=" + decor.hasWindowFocus() + " sameRoot=" + (view.getRootView() == decor));
            require(card.isClickable() && card.isShown(), "real schedule card remains clickable and shown");
            Rect global = new Rect(), local = new Rect(), window = new Rect();
            require(view.getGlobalVisibleRect(global) && view.getLocalVisibleRect(local), "native time caption visible");
            int[] location = new int[2], decorLocation = new int[2];
            view.getLocationOnScreen(location); decor.getLocationOnScreen(decorLocation);
            // getGlobalVisibleRect is in root coordinates, not guaranteed to be screen coordinates.
            // Convert the clipped local rectangle through the view's actual screen origin instead.
            Rect screen = new Rect(local); screen.offset(location[0], location[1]);
            decor.getWindowVisibleDisplayFrame(window);
            android.graphics.Insets bars = decor.getRootWindowInsets().getInsets(
                    android.view.WindowInsets.Type.systemBars() | android.view.WindowInsets.Type.displayCutout());
            Rect safe = new Rect(decorLocation[0] + bars.left, decorLocation[1] + bars.top,
                    decorLocation[0] + decor.getWidth() - bars.right,
                    decorLocation[1] + decor.getHeight() - bars.bottom);
            require(screen.intersect(window) && screen.intersect(safe), "caption intersects actual safe app window");
            View navigation = (View) field(activity, "navigation");
            Rect dockLocal = new Rect(); int[] dockLocation = new int[2];
            if (navigation != null && navigation.isShown() && navigation.getLocalVisibleRect(dockLocal)) {
                navigation.getLocationOnScreen(dockLocation);
                dockLocal.offset(dockLocation[0], dockLocation[1]);
                if (Rect.intersects(screen, dockLocal)) screen.bottom = Math.min(screen.bottom, dockLocal.top);
            }
            require(screen.width() > 0 && screen.height() > 0, "caption has safe content outside navigation overlay");
            point[0] = screen.centerX(); point[1] = screen.centerY();
            evidence[0] = "todo_due_tap globalRoot=" + global + " localVisible=" + local
                    + " screenOrigin=" + location[0] + "," + location[1]
                    + " decorOrigin=" + decorLocation[0] + "," + decorLocation[1]
                    + " window=" + window + " safe=" + safe + " screenIntersection=" + screen
                    + " point=" + point[0] + "," + point[1] + " activityFocus=" + activity.hasWindowFocus()
                    + " decorFocus=" + decor.hasWindowFocus() + " target=" + activity.getClass().getSimpleName();
        });
        Bundle progress = new Bundle(); progress.putString("stream", evidence[0] + "\n"); test.sendStatus(0, progress);
        long down = SystemClock.uptimeMillis();
        for (int action : new int[]{MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP}) {
            MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, point[0], point[1], 0);
            event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
            try { test.sendPointerSync(event); } finally { event.recycle(); }
            if (action == MotionEvent.ACTION_DOWN) SystemClock.sleep(40);
        }
    }
    private static void waitFor(Instrumentation test, BooleanSupplier ready, String label) {
        long until = SystemClock.elapsedRealtime() + 15000;
        boolean[] result = {false};
        do {
            test.runOnMainSync(() -> result[0] = ready.getAsBoolean());
            if (result[0]) return;
            SystemClock.sleep(80);
        } while (SystemClock.elapsedRealtime() < until);
        throw new AssertionError(label);
    }
    private static Object field(Object object, String name) {
        try { Field field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
        catch (ReflectiveOperationException error) { throw new AssertionError(error); }
    }
    private static void capture(Instrumentation test, String name, JSONArray images) throws Exception {
        test.waitForIdleSync(); SystemClock.sleep(300);
        Bitmap bitmap = test.getUiAutomation().takeScreenshot();
        require(bitmap != null, "screenshot available");
        File output = new File(test.getTargetContext().getExternalFilesDir(null), name + ".png");
        try (FileOutputStream stream = new FileOutputStream(output)) {
            require(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream), "screenshot written");
        } finally { bitmap.recycle(); }
        images.put(output.getAbsolutePath());
    }
    private static void restore(SharedPreferences preferences, Map<String, ?> values) {
        SharedPreferences.Editor editor = preferences.edit().clear();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String text) editor.putString(key, text);
            else if (value instanceof Integer number) editor.putInt(key, number);
            else if (value instanceof Long number) editor.putLong(key, number);
            else if (value instanceof Float number) editor.putFloat(key, number);
            else if (value instanceof Boolean flag) editor.putBoolean(key, flag);
            else if (value instanceof java.util.Set<?> set) {
                java.util.Set<String> strings = new java.util.HashSet<>();
                for (Object item : set) strings.add((String) item);
                editor.putStringSet(key, strings);
            }
        }
        if (!editor.commit()) throw new AssertionError("Fixture preference restoration failed");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        assertions++;
    }
    private TodoDueDefaultDeviceCheck() { }
}
