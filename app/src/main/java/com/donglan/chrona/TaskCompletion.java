package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskStore;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Completion is local-first; linked calendar alerts are reconciled without deleting event history. */
final class TaskCompletion {
    private static final String PENDING = "pending";
    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("task_completion_alerts", Context.MODE_PRIVATE);
    }
    static String set(Context context, long id, long taskId, boolean completed) {
        return set(context,id,taskId,completed,-1);
    }
    static String set(Context context, long id, long taskId, boolean completed, long expectedRevision) {
        synchronized (AndroidSync.LOCK) {
            try (TaskStore store = new TaskStore(context)) {
                if (expectedRevision >= 0 && store.dataRevision() != expectedRevision) throw new java.util.ConcurrentModificationException();
                if (!store.setCandidateCompleted(id, taskId, completed)) throw new IllegalStateException("待办已不存在或正在处理");
                if (!completed) {
                    for (EventCandidate candidate : store.getCandidatesByIds(List.of(id)))
                        AgendaAcknowledgement.set(context, AgendaAcknowledgement.key(candidate), 0L, false);
                }
                enqueue(context, id);
                String warning = reconcile(context, store, id);
                AgendaReminder.requestRefresh(context);
                return warning;
            }
        }
    }
    static void enqueue(Context context, long id) {
        SharedPreferences prefs = prefs(context);
        Set<String> pending = new HashSet<>(prefs.getStringSet(PENDING, Set.of()));
        pending.add(Long.toString(id));
        prefs.edit().putStringSet(PENDING, pending).apply();
    }
    static void retryPending(Context context) {
        synchronized (AndroidSync.LOCK) {
            SharedPreferences prefs = prefs(context);
            try (TaskStore store = new TaskStore(context)) {
                if (!prefs.getBoolean("existing_completed_checked",false)) {
                    Set<String> initial = new HashSet<>(prefs.getStringSet(PENDING,Set.of()));
                    try (android.database.Cursor cursor=store.getReadableDatabase().rawQuery(
                            "SELECT id FROM event_candidates WHERE category IN ('task','homework') AND completed_at_millis IS NOT NULL AND calendar_event_id IS NOT NULL",null)) {
                        while(cursor.moveToNext())initial.add(Long.toString(cursor.getLong(0)));
                    }
                    prefs.edit().putStringSet(PENDING,initial).putBoolean("existing_completed_checked",true).apply();
                }
                Set<String> pending = new HashSet<>(prefs.getStringSet(PENDING,Set.of()));
                for (String id : pending) reconcile(context, store, Long.parseLong(id));
            }
        }
    }
    private static String reconcile(Context context, TaskStore store, long id) {
        SharedPreferences prefs = prefs(context);
        String savedKey = "alerts:" + id;
        List<EventCandidate> rows = store.getCandidatesByIds(List.of(id));
        EventCandidate item = rows.isEmpty() ? null : rows.get(0);
        try {
            if (item != null && item.isTask() && item.calendarEventId != null) {
                CalendarStore calendar = new CalendarStore(context);
                if (!calendar.hasReadPermission() || !calendar.hasWritePermission())
                    return "状态已保存；请授予日历权限以同步系统提醒";
                CalendarStore.EventRecord event = calendar.getEvent(item.calendarEventId);
                if (event != null) {
                    List<Integer> alerts = new ArrayList<>();
                    if (item.completed() || !store.allowsReminder(item.id)) {
                        if (!prefs.contains(savedKey) && !prefs.edit().putString(savedKey, new JSONArray(event.reminderMinutes).toString()).commit())
                            throw new IllegalStateException("无法保存系统提醒恢复信息");
                    } else {
                        String saved = prefs.getString(savedKey, null);
                        if (saved != null) {
                            JSONArray original = new JSONArray(saved);
                            for (int i = 0; i < original.length(); i++) {
                                int minutes = original.getInt(i);
                                if (event.startMillis - minutes * 60_000L > System.currentTimeMillis()) alerts.add(minutes);
                            }
                        } else if (item.reminderMinutesBefore != null
                                && event.startMillis - item.reminderMinutesBefore * 60_000L > System.currentTimeMillis())
                            alerts.add(item.reminderMinutesBefore);
                    }
                    calendar.replaceReminders(item.calendarEventId, alerts);
                } else store.clearMissingCalendarLinks(List.of(item.calendarEventId));
            }
            Set<String> pending = new HashSet<>(prefs.getStringSet(PENDING, Set.of()));
            pending.remove(Long.toString(id));
            SharedPreferences.Editor edit = prefs.edit().putStringSet(PENDING, pending);
            if (item == null || (!item.completed() && store.allowsReminder(item.id))) edit.remove(savedKey);
            edit.apply();
            return null;
        } catch (Exception failure) {
            android.util.Log.w("ChronaCompletion", "Calendar alert update pending", failure);
            return "状态已保存；系统日历提醒同步未完成，将在下次刷新重试";
        }
    }
    private TaskCompletion() { }
}
