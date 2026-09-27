package com.donglan.chrona;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** Date grouping shared by the home timeline and its lightweight boundary check. */
final class HomeTimelineDates {
    private HomeTimelineDates() { }

    static long dayOffset(long timestampMillis, long referenceMillis, ZoneId zone) {
        LocalDate target = Instant.ofEpochMilli(timestampMillis).atZone(zone).toLocalDate();
        LocalDate reference = Instant.ofEpochMilli(referenceMillis).atZone(zone).toLocalDate();
        return ChronoUnit.DAYS.between(reference, target);
    }
}
