package com.donglan.chrona.data;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Compact, truthful summary of the conditions that remain in force outside the filter sheet. */
public final class ScheduleFilterSummary {
    private ScheduleFilterSummary() { }

    public static String describe(ScheduleFilterState state, String query, LocalDate today) {
        List<String> parts = new ArrayList<>();
        if (state.source == 1) parts.add("系统日历");
        LocalDate[] window = state.window(today);
        if (window[0] != null) {
            LocalDate end = window[1].minusDays(1);
            parts.add(window[0].equals(end) ? window[0].toString() : window[0] + " 至 " + end);
        }
        if (state.category > 0 && state.category <= EventCategory.LABELS.length)
            parts.add(EventCategory.LABELS[state.category - 1]);
        if (state.publication == 1) parts.add("待确认");
        else if (state.publication == 2) parts.add("已写入");
        if (query != null && !query.trim().isEmpty()) parts.add("搜索：" + query.trim());
        return String.join(" · ", parts);
    }
}
