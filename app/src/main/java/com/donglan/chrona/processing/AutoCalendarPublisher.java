package com.donglan.chrona.processing;

import android.content.Context;
import com.donglan.chrona.AndroidSync;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.net.RequestControl;
import java.io.IOException;
import java.time.ZoneId;
import java.util.Collections;

/** Writes each certain item once; external writes are compensated if its local link fails. */
final class AutoCalendarPublisher {
    static final class Result {
        int published, pending;
        String warning;
        String summary() {
            if (published == 0) return pending == 0 ? "未识别到日程" : "日程草稿待确认";
            return "已自动确认并写入 " + published + " 项日程" + (pending == 0 ? "" : "，" + pending + " 项待处理");
        }
    }

    static boolean eligible(EventCandidate item) {
        if (item.calendarEventId != null || item.needsConfirmation || item.uncertaintyLevel != EventCandidate.CERTAIN
                || item.startAtMillis == null || item.endAtMillis == null || item.endAtMillis <= item.startAtMillis) return false;
        try { ZoneId.of(item.timeZoneId); }
        catch (RuntimeException exception) { return false; }
        return item.reminderMinutesBefore == null || item.reminderMinutesBefore >= 0;
    }

    static Result publish(Context context, long taskId, RequestControl control) throws IOException {
        Result result = new Result();
        synchronized (AndroidSync.LOCK) {
            try (TaskStore store = new TaskStore(context)) {
                CalendarStore calendar = new CalendarStore(context);
                boolean permitted = calendar.hasReadPermission() && calendar.hasWritePermission();
                for (EventCandidate item : store.getCandidates(taskId)) {
                    control.check();
                    if (!eligible(item)) continue;
                    if (!permitted) { result.warning = "明确日程已保留；请授予日历读写权限后写入"; continue; }
                    long created = 0;
                    boolean committed = false;
                    try {
                        store.getWritableDatabase().beginTransaction();
                        try {
                            // Refresh under the database write lock; deleted/edited candidates are never resurrected.
                            EventCandidate current = store.getCandidatesByIds(Collections.singleton(item.id)).stream()
                                    .filter(value -> value.taskId == taskId).findFirst().orElse(null);
                            if (current == null || !eligible(current)) continue;
                            control.check();
                            created = calendar.insertEvent(new CalendarStore.EventInput(current.title,
                                    current.startAtMillis, current.endAtMillis, current.timeZoneId, current.allDay,
                                    current.description, current.location, current.reminderMinutesBefore == null
                                            ? Collections.emptyList() : Collections.singletonList(current.reminderMinutesBefore)));
                            control.check();
                            if (!store.setCalendarEventIdIfUnlinked(current.id, taskId, created))
                                throw new IllegalStateException("日程关联已变化");
                            store.getWritableDatabase().setTransactionSuccessful();
                        } finally { store.getWritableDatabase().endTransaction(); }
                        committed = true; result.published++;
                    } catch (Exception exception) {
                        if (created > 0 && !committed) {
                            try { calendar.deleteEvent(created); }
                            catch (RuntimeException cleanup) {
                                result.warning = "写入中断，系统日历中可能留有未关联事件，请检查";
                                android.util.Log.w("Chrona", "Automatic calendar write cleanup failed", cleanup);
                            }
                        }
                        if (exception instanceof IOException) throw (IOException) exception;
                        if (result.warning == null) result.warning = "部分明确日程未能写入，草稿已保留供重试";
                        android.util.Log.w("Chrona", "Automatic calendar write failed", exception);
                    }
                }
                for (EventCandidate item : store.getCandidates(taskId)) if (item.calendarEventId == null) result.pending++;
            }
        }
        return result;
    }
    private AutoCalendarPublisher() { }
}
