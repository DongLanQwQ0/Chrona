package com.donglan.chrona.data;

/** A proposed calendar entry. Nullable times and reminder mean the AI has not resolved them. */
public final class EventCandidate {
    public final long id;
    public final long taskId;
    public final String title;
    public final Long startAtMillis;
    public final Long endAtMillis;
    public final String timeZoneId;
    public final String location;
    public final String description;
    public final Integer reminderMinutesBefore;
    public final boolean needsConfirmation;
    public final Long calendarEventId;

    public EventCandidate(long id, long taskId, String title, Long startAtMillis,
            Long endAtMillis, String timeZoneId, String location, String description,
            Integer reminderMinutesBefore, boolean needsConfirmation) {
        this(id, taskId, title, startAtMillis, endAtMillis, timeZoneId, location,
                description, reminderMinutesBefore, needsConfirmation, null);
    }

    public EventCandidate(long id, long taskId, String title, Long startAtMillis,
            Long endAtMillis, String timeZoneId, String location, String description,
            Integer reminderMinutesBefore, boolean needsConfirmation, Long calendarEventId) {
        this.id = id;
        this.taskId = taskId;
        this.title = title;
        this.startAtMillis = startAtMillis;
        this.endAtMillis = endAtMillis;
        this.timeZoneId = timeZoneId;
        this.location = location;
        this.description = description;
        this.reminderMinutesBefore = reminderMinutesBefore;
        this.needsConfirmation = needsConfirmation;
        this.calendarEventId = calendarEventId;
    }
}
