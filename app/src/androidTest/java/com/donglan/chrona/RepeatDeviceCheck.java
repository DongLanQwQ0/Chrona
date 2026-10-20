package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import com.donglan.chrona.calendar.CalendarOccurrence;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.*;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.json.JSONObject;

/** Creates and removes only its own labelled task/provider IDs; never changes user settings. */
final class RepeatDeviceCheck {
    private static int assertions;
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context = test.getTargetContext();
        FixtureIsolation.require(context);
        android.util.Log.i("ChronaDeviceCheck", "repeat entered");
        Thread owner = Thread.currentThread();
        Thread watchdog = new Thread(() -> {
            try { Thread.sleep(30_000); } catch (InterruptedException done) { return; }
            StringBuilder trace = new StringBuilder();
            for (StackTraceElement entry : owner.getStackTrace()) trace.append(entry).append('\n');
            android.util.Log.i("ChronaDeviceCheck", trace.toString());
            Bundle status = new Bundle(); status.putString("repeat_wait_stack", trace.toString()); test.sendStatus(1, status);
        }, "repeat-check-watchdog");
        watchdog.setDaemon(true); watchdog.start();
        CalendarStore calendar = new CalendarStore(context);
        if (!calendar.hasReadPermission() || !calendar.hasWritePermission()) throw new IllegalStateException("Calendar permission required");
        long task = 0, event = 0;
        Activity screen = null;
        assertions = 0;
        String title = "重复验收 " + System.currentTimeMillis();
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long first = LocalDate.now(zone).minusDays(3).atTime(9, 0).atZone(zone).toInstant().toEpochMilli();
        long day = 86_400_000L;
        try {
            // Some devices suspend an instrumented process until an exported screen is foreground.
            try (android.os.ParcelFileDescriptor command = test.getUiAutomation().executeShellCommand(
                    "am start -W -n " + context.getPackageName() + "/com.donglan.chrona.DashboardActivity");
                    java.io.FileInputStream output = new java.io.FileInputStream(command.getFileDescriptor())) {
                while (output.read() != -1) { }
            }
            test.waitForIdleSync();
            // Recover only fixtures owned by interrupted runs of this check.
            try (TaskStore store = new TaskStore(context);
                    android.database.Cursor cursor = store.getReadableDatabase().query("tasks", new String[]{"id"},
                            "source=?", new String[]{"repeat-device-check"}, null, null, null)) {
                while (cursor.moveToNext()) {
                    long previous = cursor.getLong(0);
                    for (EventCandidate item : store.getCandidates(previous))
                        if (item.calendarEventId != null) calendar.deleteEvent(item.calendarEventId);
                    store.deleteTask(previous);
                }
            }
            long id;
            synchronized (AndroidSync.LOCK) {
                try (TaskStore store = new TaskStore(context)) {
                    store.getWritableDatabase().beginTransaction();
                    try {
                        task = store.insertTask(title, null, "repeat-device-check", System.currentTimeMillis());
                        EventCandidate draft = new EventCandidate(0, task, title, first, first + 3_600_000L,
                                zone.getId(), "", "", null, false, null, "event", false)
                                .withRepeat(new RepeatRule("daily", 1, List.of(), null, 5));
                        id = store.appendCandidate(draft);
                        store.updateStatus(task, TaskRecord.READY, null);
                        store.getWritableDatabase().setTransactionSuccessful();
                    } finally { store.getWritableDatabase().endTransaction(); }
                }
            }
            EventCandidate series = saved(context, id);
            event = calendar.insertEvent(RepeatActions.input(series));
            try (TaskStore store = new TaskStore(context)) {
                require(store.setCalendarEventIdIfUnlinked(id, task, event), "link");
            }
            series = saved(context, id);
            require(calendar.getEvent(event).rrule.contains("COUNT=5"), "provider RRULE");
            require(calendar.getEvent(event).endMillis == first + 3_600_000L, "provider duration read");
            assertParity(calendar, series, first - day, first + 7 * day, 5, "initial");
            series = RepeatActions.change(context, task, id, CandidateEditBaseline.of(series), "skip", first + day, null, null);
            assertParity(calendar, series, first - day, first + 7 * day, 4, "skip");
            series = RepeatActions.change(context, task, id, CandidateEditBaseline.of(series), "move", first + 2 * day, first + 6 * day, null);
            require(RepeatOccurrences.at(series, first + 2 * day).occurrenceAtMillis == first + 2 * day, "moved identity");
            assertParity(calendar, series, first - day, first + 7 * day, 4, "move out");
            series = RepeatActions.change(context, task, id, CandidateEditBaseline.of(series), "move", first + 4 * day, first - day, null);
            assertParity(calendar, series, first - 2 * day, first + 7 * day, 4, "future original moved past");
            if ("delete_only".equals(args.getString("scope"))) {
                require(calendar.deleteEvent(event), "delete whole provider series");
                try (android.database.Cursor cursor = context.getContentResolver().query(
                        android.provider.CalendarContract.Events.CONTENT_URI, new String[]{"_id"},
                        "_id=? OR original_id=?", new String[]{Long.toString(event), Long.toString(event)}, null)) {
                    require(cursor != null && cursor.getCount() == 0, "no master or cancelled/moved exception remains");
                }
                require(!calendar.deleteEvent(event), "deleted series is stale");
                return new JSONObject().put("assertions", assertions).put("providerDeletion", true).toString();
            }
            try (TaskStore store = new TaskStore(context)) {
                long begin = first - 2 * day, end = first + 7 * day;
                require(store.calendarCandidates(begin, end, begin, end).stream().filter(e -> e.id == id).count() == 4, "calendar query projects");
                require(store.widgetCandidates(begin, end).stream().filter(e -> e.id == id).count() == 4, "widget query projects");
            }
            Intent intent = new Intent(context, RepeatScheduleActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra("task_id", task).putExtra("candidate_id", id).putExtra("occurrence_at", first);
            screen = test.startActivitySync(intent);
            waitReady(test, screen);
            screenshot(test, context, "repeat-series");
            clickText(test, "停止重复");
            screenshot(test, context, "repeat-stop-confirm");
            clickText(test, "停止重复");
            waitReady(test, screen);
            series = saved(context, id);
            require(series.repeatStoppedAtMillis != null, "native stop confirmation persisted");
            int expected = (int) List.of(first - day, first, first + 3 * day).stream()
                    .filter(t -> t < seriesStop(context, id)).count();
            assertParity(calendar, series, first - 2 * day, first + 7 * day, expected, "stop history");
            require(RepeatOccurrences.at(series, first + 4 * day) != null, "stop retains future original moved past");
            require(RepeatOccurrences.at(series, first + 2 * day) == null, "stop removes past original moved future");
            Activity old = screen; test.runOnMainSync(old::finish);
            screen = test.startActivitySync(intent);
            waitReady(test, screen); screenshot(test, context, "repeat-stopped");
            boolean rejected = false;
            try { RepeatActions.change(context, task, id, "stale", "stop", null, null, null); }
            catch (IllegalStateException expectedFailure) { rejected = true; }
            require(rejected, "stale edit rejected");
            return new JSONObject().put("assertions", assertions).put("providerParity", true)
                    .put("screenshots", context.getExternalFilesDir(null).getAbsolutePath()).toString();
        } finally {
            watchdog.interrupt();
            if (screen != null) { Activity close = screen; test.runOnMainSync(close::finish); }
            if (event > 0) calendar.deleteEvent(event);
            if (task > 0) try (TaskStore store = new TaskStore(context)) { store.deleteTask(task); }
        }
    }
    private static long seriesStop(Context context, long id) { return saved(context, id).repeatStoppedAtMillis; }
    private static EventCandidate saved(Context context, long id) {
        try (TaskStore store = new TaskStore(context)) { return store.getCandidatesByIds(List.of(id)).get(0); }
    }
    private static void assertParity(CalendarStore calendar, EventCandidate series, long begin, long end,
            int count, String label) {
        TreeSet<Long> local = new TreeSet<>(), provider = new TreeSet<>();
        for (EventCandidate item : RepeatOccurrences.between(series, begin, end, 100)) local.add(item.anchor());
        for (CalendarOccurrence item : calendar.listAllInstances(begin, end)) if (series.title.equals(item.title)) provider.add(item.startMillis);
        require(local.size() == count, label + " local count " + local);
        require(provider.equals(local), label + " provider " + provider + " local " + local);
    }
    private static void waitReady(Instrumentation test, Activity activity) throws Exception {
        java.lang.reflect.Field busy = RepeatScheduleActivity.class.getDeclaredField("busy"); busy.setAccessible(true);
        long until = SystemClock.uptimeMillis() + 10_000;
        while (SystemClock.uptimeMillis() < until) {
            boolean[] loading = {true}; test.runOnMainSync(() -> { try { loading[0] = busy.getBoolean(activity); } catch (Exception e) { throw new RuntimeException(e); } });
            if (!loading[0]) { test.waitForIdleSync(); return; }
            SystemClock.sleep(50);
        }
        throw new IllegalStateException("Repeat screen did not load");
    }
    private static void screenshot(Instrumentation test, Context context, String name) throws Exception {
        test.waitForIdleSync(); SystemClock.sleep(300);
        Bitmap bitmap = test.getUiAutomation().takeScreenshot();
        if (bitmap == null) throw new IllegalStateException("Screenshot unavailable");
        try (FileOutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output);
        }
        bitmap.recycle();
    }
    private static void clickText(Instrumentation test, String label) {
        test.waitForIdleSync();
        android.view.accessibility.AccessibilityNodeInfo root = test.getUiAutomation().getRootInActiveWindow();
        if (root != null) for (android.view.accessibility.AccessibilityNodeInfo node : root.findAccessibilityNodeInfosByText(label)) {
            if ((label.contentEquals(node.getText() == null ? "" : node.getText())
                    || label.contentEquals(node.getContentDescription() == null ? "" : node.getContentDescription()))
                    && node.isClickable() && node.isEnabled()) {
                require(node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK), "click " + label);
                test.waitForIdleSync(); return;
            }
        }
        throw new AssertionError("Clickable control missing: " + label);
    }
    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message); assertions++;
    }
}
