package com.donglan.chrona.calendar;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

public final class CalendarOccurrenceCheck {
    public static void main(String[] args) {
        CalendarOccurrence first = event(1, 100, 200);
        CalendarOccurrence recurrence = event(1, 300, 400);
        CalendarOccurrence linked = event(2, 100, 200);
        CalendarOccurrence separate = event(3, 100, 200);
        List<CalendarOccurrence> result = CalendarOccurrence.unlinked(
                List.of(first, first, recurrence, linked, separate), Set.of(2L));
        if (!result.equals(List.of(first, recurrence, separate)))
            throw new AssertionError("Only duplicate instances and linked IDs must be removed");
        if (!first.equals(event(1, 100, 200)) || first.equals(event(1, 100, 250)))
            throw new AssertionError("Cache equality must detect changed event bounds");
        CalendarOccurrence day = new CalendarOccurrence(4, AllDayDates.utcStart("2026-03-08"),
                AllDayDates.utcExclusiveEnd("2026-03-08"), "全天", true, null, null);
        for (String name : List.of("Asia/Shanghai", "America/Los_Angeles")) {
            ZoneId zone = ZoneId.of(name);
            if (day.displayStart(zone) != LocalDate.parse("2026-03-08")
                    .atStartOfDay(zone).toInstant().toEpochMilli()
                    || day.displayEnd(zone) != LocalDate.parse("2026-03-09")
                    .atStartOfDay(zone).toInstant().toEpochMilli())
                throw new AssertionError("All-day bounds must follow local dates, including DST");
        }
        System.out.println("CalendarOccurrenceCheck passed");
    }

    private static CalendarOccurrence event(long id, long start, long end) {
        return new CalendarOccurrence(id, start, end, "同名日程", false, "", "日历");
    }
}
