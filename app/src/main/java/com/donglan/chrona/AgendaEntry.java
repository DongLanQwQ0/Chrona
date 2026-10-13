package com.donglan.chrona;

/** Immutable display occurrence; system recurrence identity includes its start. */
final class AgendaEntry {
    final long id, taskId, start, end;
    final String title, location, description, source, category;
    final boolean system, allDay, confirmed;
    final Integer calendarColor;
    AgendaEntry(long id, long taskId, long start, long end, String title, String location,
            String description, String source, boolean system, boolean allDay, boolean confirmed) {
        this(id, taskId, start, end, title, location, description, source, system, allDay, confirmed, null);
    }
    AgendaEntry(long id, long taskId, long start, long end, String title, String location,
            String description, String source, boolean system, boolean allDay, boolean confirmed, Integer calendarColor) {
        this(id, taskId, start, end, title, location, description, source, system, allDay, confirmed, calendarColor, "event");
    }
    AgendaEntry(long id, long taskId, long start, long end, String title, String location,
            String description, String source, boolean system, boolean allDay, boolean confirmed,
            Integer calendarColor, String category) {
        this.id = id; this.taskId = taskId; this.start = start; this.end = Math.max(start, end);
        this.title = title; this.location = location == null ? "" : location;
        this.description = description == null ? "" : description;
        this.source = source; this.system = system; this.allDay = allDay; this.confirmed = confirmed;
        this.calendarColor = calendarColor;
        this.category = category;
    }
    String key() { return (system ? "system:" : "chrona:") + id + ":" + start; }
    long finish() { return end > start ? end : start + 60_000L; }
    boolean overlaps(long begin, long until) { return start < until && finish() > begin; }
    @Override public boolean equals(Object other) {
        if (!(other instanceof AgendaEntry e)) return false;
        return id == e.id && taskId == e.taskId && start == e.start && end == e.end
                && system == e.system && allDay == e.allDay && confirmed == e.confirmed
                && java.util.Objects.equals(title, e.title) && java.util.Objects.equals(location, e.location)
                && java.util.Objects.equals(description, e.description) && java.util.Objects.equals(source, e.source)
                && java.util.Objects.equals(category, e.category)
                && java.util.Objects.equals(calendarColor, e.calendarColor);
    }
    @Override public int hashCode() {
        return java.util.Objects.hash(id, taskId, start, end, system, allDay, confirmed, title, location, description, source, calendarColor, category);
    }
}
