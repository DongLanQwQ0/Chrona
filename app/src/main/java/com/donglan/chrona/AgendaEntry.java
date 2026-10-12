package com.donglan.chrona;

/** Immutable display occurrence; system recurrence identity includes its start. */
final class AgendaEntry {
    final long id, taskId, start, end;
    final String title, location, description, source;
    final boolean system, allDay, confirmed;
    AgendaEntry(long id, long taskId, long start, long end, String title, String location,
            String description, String source, boolean system, boolean allDay, boolean confirmed) {
        this.id = id; this.taskId = taskId; this.start = start; this.end = Math.max(start, end);
        this.title = title; this.location = location == null ? "" : location;
        this.description = description == null ? "" : description;
        this.source = source; this.system = system; this.allDay = allDay; this.confirmed = confirmed;
    }
    String key() { return (system ? "system:" : "chrona:") + id + ":" + start; }
    long finish() { return end > start ? end : start + 60_000L; }
    boolean overlaps(long begin, long until) { return start < until && finish() > begin; }
}
