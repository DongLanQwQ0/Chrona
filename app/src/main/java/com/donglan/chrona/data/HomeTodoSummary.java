package com.donglan.chrona.data;

import java.util.List;

/** Counts cover every unfinished task; only the short focus list is bounded. */
public final class HomeTodoSummary {
    public final int total, todayDue, overdue, unscheduled;
    public final List<EventCandidate> focus;

    public HomeTodoSummary(int total, int todayDue, int overdue, int unscheduled,
            List<EventCandidate> focus) {
        this.total = total;
        this.todayDue = todayDue;
        this.overdue = overdue;
        this.unscheduled = unscheduled;
        this.focus = focus;
    }
}
