package com.donglan.chrona.calendar;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** A read-only occurrence. Recurrences share an event ID but have different start times. */
public final class CalendarOccurrence {
    public final long eventId, startMillis, endMillis;
    public final String title, location, calendarName;
    public final boolean allDay;

    public CalendarOccurrence(long eventId, long startMillis, long endMillis, String title,
            boolean allDay, String location, String calendarName) {
        this.eventId = eventId;
        this.startMillis = startMillis;
        this.endMillis = endMillis;
        this.title = title == null || title.trim().isEmpty() ? "未命名日程" : title;
        this.allDay = allDay;
        this.location = location == null ? "" : location;
        this.calendarName = calendarName == null ? "" : calendarName;
    }

    public long displayStart(ZoneId zone) {
        return allDay ? LocalDate.parse(AllDayDates.displayStart(startMillis))
                .atStartOfDay(zone).toInstant().toEpochMilli() : startMillis;
    }

    public long displayEnd(ZoneId zone) {
        return allDay ? LocalDate.parse(AllDayDates.displayEnd(endMillis)).plusDays(1)
                .atStartOfDay(zone).toInstant().toEpochMilli() : endMillis;
    }

    public static List<CalendarOccurrence> unlinked(List<CalendarOccurrence> occurrences,
            Set<Long> linkedIds) {
        List<CalendarOccurrence> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CalendarOccurrence item : occurrences) {
            if (!linkedIds.contains(item.eventId) && seen.add(item.eventId + ":" + item.startMillis))
                result.add(item);
        }
        return result;
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof CalendarOccurrence)) return false;
        CalendarOccurrence item = (CalendarOccurrence) other;
        return eventId == item.eventId && startMillis == item.startMillis && endMillis == item.endMillis
                && allDay == item.allDay && title.equals(item.title) && location.equals(item.location)
                && calendarName.equals(item.calendarName);
    }

    @Override public int hashCode() {
        return Objects.hash(eventId, startMillis, endMillis, title, allDay, location, calendarName);
    }
}
