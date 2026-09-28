package com.donglan.chrona.data;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** A filter sheet edits its own copy; applying is the only write to the dashboard. */
public final class ScheduleFilterState {
    public static final int ALL = 0, TODAY = 1, WEEK = 2, MONTH = 3, DATE = 4, CUSTOM = 5;
    public int range, category, publication, source, tab;
    public LocalDate date, until;

    public ScheduleFilterState(int range, int category, int publication, int source,
            int tab, LocalDate date, LocalDate until) {
        this.range = range; this.category = category; this.publication = publication;
        this.source = source; this.tab = tab; this.date = date; this.until = until;
    }

    public ScheduleFilterState copy() {
        return new ScheduleFilterState(range, category, publication, source, tab, date, until);
    }

    public LocalDate[] window(LocalDate today) {
        if (range == TODAY) return new LocalDate[]{today, today.plusDays(1)};
        if (range == WEEK) {
            LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            return new LocalDate[]{start, start.plusWeeks(1)};
        }
        if (range == MONTH || (source == 1 && range == ALL)) {
            LocalDate start = date.withDayOfMonth(1);
            return new LocalDate[]{start, start.plusMonths(1)};
        }
        if (range == DATE) return new LocalDate[]{date, date.plusDays(1)};
        if (range == CUSTOM) return new LocalDate[]{date, until.plusDays(1)};
        return new LocalDate[]{null, null};
    }

    public boolean validSystemRange(LocalDate today) {
        LocalDate[] window = window(today);
        return source != 1 || window[0] == null || !window[1].isAfter(window[0].plusYears(1));
    }

    public boolean shift(int direction, LocalDate today) {
        LocalDate[] window = window(today);
        if (window[0] == null) return false;
        boolean month = range == MONTH || range == ALL;
        LocalDate[] next = ScheduleRangeStep.shift(window[0], window[1], month, direction);
        if (source == 1 && next[1].isAfter(next[0].plusYears(1))) return false;
        date = next[0]; until = next[1].minusDays(1);
        range = month ? MONTH : range == TODAY || range == DATE ? DATE : CUSTOM;
        tab = 3;
        return true;
    }

    public void reset(LocalDate today) {
        range = category = publication = source = 0;
        date = until = today;
    }
}
