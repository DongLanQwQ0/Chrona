package com.donglan.chrona.processing;

import android.content.Context;
import com.donglan.chrona.AndroidSync;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

/** Explicit user publication: incomplete drafts stay untouched; each valid item commits alone. */
public final class BulkCalendarPublisher {
    public static final class Result {
        public int published, skipped, failed;
        public int review, failedTasks;
        public boolean cleanupFailed;
        public String summary() {
            return "已写入 " + published + " 条，跳过 " + skipped + " 条，失败 " + failed + " 条"
                    + (cleanupFailed ? "；系统日历可能留有未关联事件，请检查" : "");
        }
    }

    static CalendarStore.EventInput input(EventCandidate item) {
        if (item == null || item.isRecord || item.completed() || item.calendarEventId != null
                || item.calendarStart() == null || item.calendarEnd() == null
                || (item.startAtMillis == null && item.endAtMillis != null)
                || (!item.isTask() && (item.startAtMillis == null || item.endAtMillis == null))
                || (item.startAtMillis != null && item.endAtMillis != null
                        && item.endAtMillis <= item.startAtMillis)) return null;
        try {
            ZoneId.of(item.timeZoneId);
            return new CalendarStore.EventInput(item.title, item.calendarStart(), item.calendarEnd(),
                    item.timeZoneId, item.allDay, item.description, item.location,
                    item.reminderMinutesBefore == null ? Collections.emptyList()
                            : Collections.singletonList(item.reminderMinutesBefore)).withRepeat(item);
        } catch (RuntimeException invalid) { return null; }
    }

    /** IDs are capture IDs in the inbox and candidate IDs on the schedule page. */
    public static Result publish(Context context, Collection<Long> selected, boolean inbox) {
        Result result = new Result();
        synchronized (AndroidSync.LOCK) {
            try (TaskStore store = new TaskStore(context)) {
                List<EventCandidate> chosen = new ArrayList<>();
                for (long id : new LinkedHashSet<>(selected)) {
                    List<EventCandidate> items = inbox ? store.getCandidates(id)
                            : store.getCandidatesByIds(Collections.singleton(id));
                    chosen.addAll(items);
                }
                CalendarStore calendar = new CalendarStore(context);
                for (EventCandidate item : chosen) {
                    long created = 0;
                    boolean committed = false;
                    try {
                        store.getWritableDatabase().beginTransaction();
                        try {
                            EventCandidate current = store.getCandidatesByIds(Collections.singleton(item.id))
                                    .stream().filter(value -> value.taskId == item.taskId).findFirst().orElse(null);
                            TaskRecord task = store.getTask(item.taskId);
                            if (task == null || TaskRecord.QUEUED.equals(task.status)
                                    || TaskRecord.PROCESSING.equals(task.status)) {
                                result.skipped++; continue;
                            }
                            CalendarStore.EventInput input = input(current);
                            if (input == null) { result.skipped++; continue; }
                            Long linked = null;
                            for (long match : calendar.findMatchingEvents(input)) {
                                CalendarStore.EventRecord existing = calendar.getEvent(match);
                                if (existing != null && java.util.Objects.equals(
                                        normalizedLocation(existing.location), normalizedLocation(input.location))) {
                                    linked = match; break;
                                }
                            }
                            if (linked == null) { created = calendar.insertEvent(input); linked = created; }
                            if (!store.setCalendarEventIdIfUnlinked(current.id, current.taskId, linked))
                                throw new IllegalStateException("日程关联已变化");
                            if (!store.updateReviewStatus(current.taskId))
                                throw new IllegalStateException("收件已变化");
                            store.getWritableDatabase().setTransactionSuccessful();
                        } finally { store.getWritableDatabase().endTransaction(); }
                        committed = true;
                        result.published++;
                    } catch (Exception failure) {
                        result.failed++;
                        if (created > 0 && !committed) {
                            try { calendar.deleteEvent(created); }
                            catch (RuntimeException cleanup) {
                                result.cleanupFailed = true;
                                android.util.Log.w("Chrona", "Bulk calendar cleanup failed", cleanup);
                            }
                        }
                        android.util.Log.w("Chrona", "Bulk calendar write failed for " + item.id, failure);
                    }
                }
                result.review = store.taskCountByStatus(TaskRecord.NEEDS_REVIEW);
                result.failedTasks = store.taskCountByStatus(TaskRecord.FAILED);
            }
        }
        return result;
    }

    private static String normalizedLocation(String location) {
        return location == null ? "" : location.trim();
    }

    private BulkCalendarPublisher() { }
}
