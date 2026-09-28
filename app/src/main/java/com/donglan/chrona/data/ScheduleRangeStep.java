package com.donglan.chrona.data;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** Moves a half-open date interval by its own span, or by a calendar month. */
public final class ScheduleRangeStep {
    private ScheduleRangeStep() { }

    public static LocalDate[] shift(LocalDate from, LocalDate until, boolean month, int direction) {
        if (direction != -1 && direction != 1) throw new IllegalArgumentException("direction");
        if (month) {
            LocalDate start = from.withDayOfMonth(1).plusMonths(direction);
            return new LocalDate[]{start, start.plusMonths(1)};
        }
        long days = ChronoUnit.DAYS.between(from, until);
        if (days <= 0) throw new IllegalArgumentException("Empty date interval");
        return new LocalDate[]{from.plusDays(days * direction), until.plusDays(days * direction)};
    }
}
