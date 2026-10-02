package com.donglan.chrona;

import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.EventTimeDefaults;

/** Changing category may replace generated time only; a deadline keeps its explicit due instant. */
final class CategoryTimeChange {
    final long start, end;
    final boolean automaticEnd;

    private CategoryTimeChange(long start, long end, boolean automaticEnd) {
        this.start = start; this.end = end; this.automaticEnd = automaticEnd;
    }

    static CategoryTimeChange from(String previous, String next, Long start, Long end,
            boolean automaticEnd, boolean allDay) {
        if (!automaticEnd || allDay) return null;
        Long anchor = EventCategory.DEADLINE.equals(previous) ? end : start;
        if (anchor == null) return null;
        long duration = EventTimeDefaults.durationMillis(next);
        if (EventCategory.DEADLINE.equals(next))
            return new CategoryTimeChange(anchor - duration, anchor, false);
        return new CategoryTimeChange(anchor, anchor + duration, true);
    }
}
