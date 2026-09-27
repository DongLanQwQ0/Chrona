package com.donglan.chrona.data;

/** Category-aware defaults for calendar intervals when extraction has one explicit time. */
public final class EventTimeDefaults {
    private static final long MINUTE_MILLIS = 60_000L;

    private EventTimeDefaults() { }

    public static long durationMillis(String category) {
        String normalized = EventCategory.normalize(category);
        if (EventCategory.TASK.equals(normalized)) return 30 * MINUTE_MILLIS;
        if (EventCategory.REMINDER.equals(normalized)) return 5 * MINUTE_MILLIS;
        if (EventCategory.DEADLINE.equals(normalized)) return MINUTE_MILLIS;
        if (EventCategory.NOTE.equals(normalized)) return 15 * MINUTE_MILLIS;
        return 60 * MINUTE_MILLIS;
    }

    /** Treats a lone deadline timestamp as the interval's end, preserving the exact due instant. */
    public static EventCandidate completeInterval(EventCandidate candidate) {
        if (candidate == null || candidate.allDay) return candidate;
        boolean deadline = EventCategory.DEADLINE.equals(
                EventCategory.normalize(candidate.category));
        if (deadline && candidate.startAtMillis == null && candidate.endAtMillis != null) {
            return withTimes(candidate, candidate.endAtMillis - MINUTE_MILLIS,
                    candidate.endAtMillis);
        }
        if (candidate.endAtMillis != null || candidate.startAtMillis == null) return candidate;
        long start = candidate.startAtMillis;
        if (deadline) return withTimes(candidate, start - MINUTE_MILLIS, start);
        return withTimes(candidate, start, start + durationMillis(candidate.category));
    }

    private static EventCandidate withTimes(EventCandidate candidate, long start, long end) {
        return new EventCandidate(candidate.id, candidate.taskId, candidate.title,
                start, end, candidate.timeZoneId, candidate.location, candidate.description,
                candidate.reminderMinutesBefore, candidate.needsConfirmation,
                candidate.calendarEventId, candidate.category, candidate.allDay);
    }
}
