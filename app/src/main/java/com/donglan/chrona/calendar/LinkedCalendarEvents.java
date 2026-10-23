package com.donglan.chrona.calendar;

import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskStore;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Candidate-scoped provider writes. Callers hold AndroidSync.LOCK and own the outer SQL transaction. */
public final class LinkedCalendarEvents implements AutoCloseable {
    private final TaskStore store;
    private final CalendarStore calendar;
    private final List<Long> created = new ArrayList<>();

    public LinkedCalendarEvents(TaskStore store, CalendarStore calendar) {
        this.store = store; this.calendar = calendar;
    }

    public long insert(EventCandidate item, CalendarStore.EventInput input) {
        long id = calendar.insertEvent(input);
        created.add(id);
        if (!store.setCalendarEventIdIfUnlinked(item.id, item.taskId, id))
            throw new IllegalStateException("日程关联已变化，请刷新后重试");
        return id;
    }

    public boolean update(EventCandidate item, CalendarStore.EventInput input) {
        if (item.calendarEventId == null) throw new IllegalArgumentException("A linked candidate is required");
        if (!store.hasOtherCalendarReferences(item.calendarEventId, List.of(item.id)))
            return calendar.updateEvent(item.calendarEventId, input);
        if (calendar.getEvent(item.calendarEventId) == null) return false;
        long id = calendar.insertEvent(input);
        created.add(id);
        if (!store.replaceCalendarEventId(item.id, item.taskId, item.calendarEventId, id))
            throw new IllegalStateException("日程关联已变化，请刷新后重试");
        return true;
    }

    public boolean description(EventCandidate item, String description) {
        if (!store.hasOtherCalendarReferences(item.calendarEventId, List.of(item.id)))
            return calendar.updateDescription(item.calendarEventId, description);
        CalendarStore.EventRecord event = calendar.getEvent(item.calendarEventId);
        return event != null && update(item, copy(event, description, event.reminderMinutes));
    }

    public boolean reminders(EventCandidate item, List<Integer> minutes) {
        if (!store.hasOtherCalendarReferences(item.calendarEventId, List.of(item.id)))
            return calendar.replaceReminders(item.calendarEventId, minutes);
        CalendarStore.EventRecord event = calendar.getEvent(item.calendarEventId);
        return event != null && update(item, copy(event, event.description, minutes));
    }

    private static CalendarStore.EventInput copy(CalendarStore.EventRecord event, String description,
            List<Integer> minutes) {
        return new CalendarStore.EventInput(event.title, event.startMillis, event.endMillis,
                event.timeZoneId, event.allDay, description, event.location, minutes).withRepeat(event);
    }

    /** Provider errors stop local deletion; references not in this selection always retain the event. */
    public void delete(Collection<EventCandidate> items) {
        Set<Long> removing = new HashSet<>(), events = new HashSet<>();
        for (EventCandidate item : items) {
            removing.add(item.id);
            if (item.calendarEventId != null) events.add(item.calendarEventId);
        }
        for (long id : events) if (!store.hasOtherCalendarReferences(id, removing)) {
            if (!calendar.deleteEvent(id) && calendar.getEvent(id) != null)
                throw new IllegalStateException("日历旧事件未能移除，请重试");
        }
    }

    public void detach(EventCandidate item) {
        if (item.calendarEventId == null) return;
        delete(List.of(item));
        if (!store.replaceCalendarEventId(item.id, item.taskId, item.calendarEventId, null))
            throw new IllegalStateException("日程关联已变化，请刷新后重试");
    }

    /** Only call after the outermost endTransaction has returned successfully. */
    public void committed() { created.clear(); }

    @Override public void close() {
        RuntimeException failure = null;
        for (long id : created) {
            try {
                if (!calendar.deleteEvent(id) && calendar.getEvent(id) != null)
                    throw new IllegalStateException("日历事件未能清理");
            }
            catch (RuntimeException error) {
                if (failure == null) failure = new IllegalStateException("未关联日历项清理失败，请在系统日历中检查", error);
                else failure.addSuppressed(error);
            }
        }
        created.clear();
        if (failure != null) throw failure;
    }
}
