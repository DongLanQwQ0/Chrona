package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real Dashboard completion and layout, touching only three explicitly owned local fixtures. */
final class CompletionMotionDeviceCheck {
    static String run(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        long[] tasks = new long[3], candidates = new long[3];
        Activity activity = null;
        String run = Long.toString(SystemClock.elapsedRealtime());
        try {
            long now = System.currentTimeMillis();
            ZoneId zone = ZoneId.systemDefault();
            long midnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
            if (now + 8 * 60_000L >= midnight)
                throw new IllegalStateException("Same-day future fixtures require at least eight minutes before midnight");
            synchronized (AndroidSync.LOCK) {
                try (TaskStore store = new TaskStore(context)) {
                    for (int i = 0; i < 3; i++) {
                        String title = "卡片完成上移验收 " + run + " · " + (i + 1);
                        tasks[i] = store.insertTask(title + "（临时自动清理）", null,
                                "device_check", now);
                        long start = now + (2 + i * 2) * 60_000L;
                        candidates[i] = store.appendCandidate(new EventCandidate(0, tasks[i], title,
                                start, start + 60_000L, zone.getId(), "", "本机卡片动效验收，自动清理",
                                null, false, null, "task", false));
                        store.updateStatus(tasks[i], TaskRecord.READY, null);
                    }
                }
            }
            activity = test.startActivitySync(new Intent(context, DashboardActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(DashboardActivity.EXTRA_SECTION, DashboardActivity.HOME));
            Activity screen = activity;
            await(test, "Three fixture timeline cards did not appear within the home item limit", () ->
                    card(screen, candidates[0]) != null && card(screen, candidates[1]) != null
                            && card(screen, candidates[2]) != null && settled(screen));
            SystemClock.sleep(400);
            test.waitForIdleSync();

            View[] original = new View[3];
            ScrollView[] viewport = new ScrollView[1];
            int[][] before = new int[3][2];
            int[] scrollBefore = new int[1];
            onMain(test, () -> {
                try {
                    viewport[0] = (ScrollView) field(screen, "scroll");
                    for (int i = 0; i < 3; i++) original[i] = card(screen, candidates[i]);
                    int range = viewport[0].getChildAt(0).getHeight() - viewport[0].getHeight()
                            + viewport[0].getPaddingTop() + viewport[0].getPaddingBottom();
                    int margin = Math.round(16 * screen.getResources().getDisplayMetrics().density);
                    int[] firstPosition = new int[2], viewportPosition = new int[2];
                    original[0].getLocationOnScreen(firstPosition);
                    viewport[0].getLocationOnScreen(viewportPosition);
                    int bringFirstIntoView = firstPosition[1] - viewportPosition[1]
                            + viewport[0].getScrollY() - margin;
                    int requested = Math.min(bringFirstIntoView, range - original[0].getHeight() - margin);
                    if (requested <= 0) throw new AssertionError("Viewport has insufficient remaining scroll range for nonzero-scroll removal assertion");
                    viewport[0].scrollTo(0, requested);
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            });
            test.waitForIdleSync();
            onMain(test, () -> {
                scrollBefore[0] = viewport[0].getScrollY();
                if (scrollBefore[0] <= 0) throw new AssertionError("Nonzero initial scroll required");
                for (int i = 0; i < 3; i++) original[i].getLocationOnScreen(before[i]);
                View button = completionButton(original[0]);
                if (button == null || !button.isEnabled() || !button.performClick())
                    throw new AssertionError("First fixture's real completion button could not be clicked");
            });
            await(test, "Completion worker/reconciliation did not finish", () ->
                    settled(screen) && card(screen, candidates[0]) == null
                            && card(screen, candidates[1]) != null && card(screen, candidates[2]) != null);
            test.waitForIdleSync();

            JSONObject report = new JSONObject();
            onMain(test, () -> {
                try {
                    if (field(screen, "scroll") != viewport[0]) throw new AssertionError("ScrollView replaced");
                    int afterScroll = viewport[0].getScrollY();
                    if (afterScroll <= 0 || Math.abs(afterScroll - scrollBefore[0]) > 1)
                        throw new AssertionError("Scroll position jumped: before=" + scrollBefore[0] + ", after=" + afterScroll);
                    JSONArray survivors = new JSONArray();
                    for (int i = 1; i < 3; i++) {
                        View remaining = card(screen, candidates[i]);
                        if (remaining != original[i] || !original[i].isAttachedToWindow())
                            throw new AssertionError("Surviving fixture " + (i + 1) + " lost its View identity");
                        int[] after = new int[2]; remaining.getLocationOnScreen(after);
                        if (after[1] >= before[i][1]) throw new AssertionError("Surviving fixture " + (i + 1) + " did not move upward");
                        survivors.put(new JSONObject().put("candidateId", candidates[i])
                                .put("sameView", true).put("beforeY", before[i][1]).put("afterY", after[1])
                                .put("upwardPx", before[i][1] - after[1]));
                    }
                    report.put("survivors", survivors).put("sameScrollView", true)
                            .put("scrollBefore", scrollBefore[0]).put("scrollAfter", afterScroll);
                } catch (ReflectiveOperationException | org.json.JSONException failure) {
                    throw new IllegalStateException(failure);
                }
            });
            try (TaskStore store = new TaskStore(context)) {
                EventCandidate completed = store.getCandidatesByIds(java.util.List.of(candidates[0])).get(0);
                if (!completed.completed()) throw new AssertionError("Real completion callback did not persist fixture completion");
                for (long id : candidates)
                    if (store.getCandidatesByIds(java.util.List.of(id)).get(0).calendarEventId != null)
                        throw new AssertionError("Fixture unexpectedly linked to system calendar");
            }
            return report.put("firstFixtureCompleted", true).put("temporaryFixtureOnly", true)
                    .put("phase", "completion_card_motion").toString();
        } finally {
            Activity screen = activity;
            try {
                if (screen != null) onMain(test, screen::finish);
            } finally {
                synchronized (AndroidSync.LOCK) {
                    try (TaskStore store = new TaskStore(context)) {
                        for (long id : tasks) if (id > 0) store.deleteTask(id);
                    }
                }
                AgendaReminder.requestRefresh(context);
                AgendaWidgetProvider.requestRefresh(context);
            }
        }
    }

    private interface MainAction { void run() throws Exception; }
    /** An assertion belongs to the instrumentation thread so its fixture finally block still runs. */
    private static void onMain(Instrumentation test, MainAction action) throws Exception {
        Throwable[] problem = {null};
        test.runOnMainSync(() -> {
            try { action.run(); } catch (Throwable failure) { problem[0] = failure; }
        });
        if (problem[0] instanceof Error error) throw error;
        if (problem[0] instanceof Exception exception) throw exception;
        if (problem[0] != null) throw new IllegalStateException(problem[0]);
    }

    private interface Probe { boolean ready() throws Exception; }
    private static void await(Instrumentation test, String failure, Probe probe) throws Exception {
        long deadline = SystemClock.uptimeMillis() + 20_000L;
        do {
            boolean[] ready = {false};
            onMain(test, () -> ready[0] = probe.ready());
            if (ready[0]) return;
            SystemClock.sleep(80);
        } while (SystemClock.uptimeMillis() < deadline);
        throw new AssertionError(failure);
    }

    private static boolean settled(Activity activity) throws ReflectiveOperationException {
        return (boolean) field(activity, "initialDataReady")
                && !(boolean) field(activity, "completionRefreshRunning")
                && !(boolean) field(activity, "completionRefreshPending")
                && ((java.util.Set<?>) field(activity, "completingCandidates")).isEmpty()
                && !(boolean) field(activity, "calendarLoading");
    }

    /** Reads the production reconciler's stable timeline identity, not duplicate carousel titles. */
    private static View card(Activity activity, long candidate) throws ReflectiveOperationException {
        Object reconciler = field(activity, "completionRows");
        Field identities = DashboardCardReconciler.class.getDeclaredField("identities");
        identities.setAccessible(true);
        @SuppressWarnings("unchecked") Map<View, Object> rows = (Map<View, Object>) identities.get(reconciler);
        String expected = "timeline:candidate:" + candidate;
        for (Map.Entry<View, Object> entry : rows.entrySet()) {
            Method key = entry.getValue().getClass().getDeclaredMethod("key"); key.setAccessible(true);
            if (expected.equals(key.invoke(entry.getValue())) && entry.getKey().isAttachedToWindow())
                return entry.getKey();
        }
        return null;
    }

    private static Object field(Activity activity, String name) throws ReflectiveOperationException {
        Field field = DashboardActivity.class.getDeclaredField(name); field.setAccessible(true);
        return field.get(activity);
    }

    private static View completionButton(View view) {
        if (String.valueOf(view.getContentDescription()).endsWith("标记待办完成")) return view;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View button = completionButton(group.getChildAt(i)); if (button != null) return button;
        }
        return null;
    }

    private CompletionMotionDeviceCheck() { }
}
