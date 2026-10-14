package com.donglan.chrona;

import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.EventTimeDefaults;

/** Changing category may replace generated time only; a deadline keeps its explicit due instant. */
final class CategoryTimeChange {
    final Long start, end;
    final boolean automaticEnd;

    private CategoryTimeChange(Long start, Long end, boolean automaticEnd) {
        this.start = start; this.end = end; this.automaticEnd = automaticEnd;
    }

    static CategoryTimeChange from(String previous, String next, Long start, Long end,
            boolean automaticEnd, boolean allDay) {
        if (!automaticEnd || allDay) return null;
        Long anchor = start;
        if (anchor == null) return null;
        long duration = EventTimeDefaults.durationMillis(next);
        if (EventCategory.TASK.equals(next)) return new CategoryTimeChange(anchor, null, false);
        return new CategoryTimeChange(anchor, anchor + duration, true);
    }
}
