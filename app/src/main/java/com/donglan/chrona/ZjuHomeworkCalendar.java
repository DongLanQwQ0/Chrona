package com.donglan.chrona;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reconciles owned provider events after the atomic local homework update has committed. */
public final class ZjuHomeworkCalendar {
    public static void retryPending(Context context) {
        synchronized (AndroidSync.LOCK) {
            try (TaskStore store = new TaskStore(context)) {
                SQLiteDatabase db = store.getWritableDatabase();
                List<Long> ids = new ArrayList<>();
                try (Cursor cursor = db.rawQuery("SELECT candidate_id FROM zju_homework_map WHERE pending_calendar=1 AND ignored=0 AND candidate_id IS NOT NULL", null)) {
                    while (cursor.moveToNext()) ids.add(cursor.getLong(0));
                }
                if (ids.isEmpty()) return;
                CalendarStore calendar = new CalendarStore(context);
                if (!calendar.hasReadPermission() || !calendar.hasWritePermission()) return;
                for (long id : ids) {
                    try {
                        List<EventCandidate> rows = store.getCandidatesByIds(Collections.singletonList(id));
                        EventCandidate item = rows.isEmpty() ? null : rows.get(0);
                        if (item == null || item.calendarEventId == null) { clear(db, id); continue; }
                        // getEvent/updateEvent both scope IDs to Chrona's own local calendar.
                        CalendarStore.EventRecord event = calendar.getEvent(item.calendarEventId);
                        if (event == null) {
                            store.clearMissingCalendarLinks(Collections.singletonList(item.calendarEventId));
                            clear(db, id); continue;
                        }
                        // A removed deadline has no valid provider interval; retain the retry until
                        // the user supplies an execution time or a later fetch restores a deadline.
                        if (item.calendarStart() == null) {
                            // Save the user's alert configuration before clearing the obsolete
                            // deadline alert; allowsReminder keeps completion retry from restoring it.
                            TaskCompletion.enqueue(context, id);
                            TaskCompletion.retryPending(context);
                            calendar.replaceReminders(item.calendarEventId, Collections.emptyList());
                            continue;
                        }
                        boolean suppressed = item.completed() || !store.allowsReminder(item.id);
                        List<Integer> reminders = suppressed ? Collections.emptyList()
                                : item.reminderMinutesBefore == null ? event.reminderMinutes : Collections.singletonList(item.reminderMinutesBefore);
                        CalendarStore.EventInput input = input(item, reminders);
                        if (!calendar.updateEvent(item.calendarEventId, input))
                            store.clearMissingCalendarLinks(Collections.singletonList(item.calendarEventId));
                        clear(db, id);
                    } catch (RuntimeException failure) {
                        // The durable pending bit remains set for permission/provider recovery.
                        android.util.Log.w("ChronaZjuCalendar", "Homework calendar update pending", failure);
                    }
                }
            }
        }
    }

    public static int pendingCount(Context context) {
        try (TaskStore store = new TaskStore(context);
             Cursor cursor = store.getReadableDatabase().rawQuery("SELECT COUNT(*) FROM zju_homework_map WHERE pending_calendar=1 AND ignored=0", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    static CalendarStore.EventInput input(EventCandidate item, List<Integer> reminders) {
        // calendarStart chooses personal execution first; deadline-only tasks remain 1-minute points.
        return new CalendarStore.EventInput(item.title, item.calendarStart(), item.calendarEnd(),
                item.allDay ? "UTC" : item.timeZoneId, item.allDay, item.description, item.location, reminders);
    }
    private static void clear(SQLiteDatabase db, long id) {
        db.execSQL("UPDATE zju_homework_map SET pending_calendar=0 WHERE candidate_id=?", new Object[]{id});
    }
    private ZjuHomeworkCalendar() { }
}
