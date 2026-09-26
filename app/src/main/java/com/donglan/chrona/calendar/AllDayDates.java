package com.donglan.chrona.calendar;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;

/** Android CalendarContract stores all-day bounds at UTC midnight; the end is exclusive. */
public final class AllDayDates {
    private AllDayDates() { }

    public static long utcStart(String date) {
        return LocalDate.parse(date.trim()).atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli();
    }

    public static long utcExclusiveEnd(String inclusiveDate) {
        return LocalDate.parse(inclusiveDate.trim()).plusDays(1)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }

    public static String displayStart(long utcMillis) {
        return Instant.ofEpochMilli(utcMillis).atZone(ZoneOffset.UTC).toLocalDate().toString();
    }

    public static String displayEnd(long utcExclusiveMillis) {
        return Instant.ofEpochMilli(utcExclusiveMillis).atZone(ZoneOffset.UTC)
                .toLocalDate().minusDays(1).toString();
    }

    public static String localDate(long millis, ZoneId zone) {
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().toString();
    }
}
