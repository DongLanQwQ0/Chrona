package com.donglan.chrona;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/** Initial time-grid position; later refreshes restore the user's actual offsets. */
final class CalendarViewport {
    static int hour(LocalDate first, int days, LocalDate today, LocalTime now) {
        return today.isBefore(first) || !today.isBefore(first.plusDays(days)) ? 8 : Math.max(0, now.getHour() - 1);
    }
    static int column(LocalDate first, int days, LocalDate selected) {
        return (int) Math.max(0, Math.min(days - 1, ChronoUnit.DAYS.between(first, selected)));
    }
    private CalendarViewport() { }
}
