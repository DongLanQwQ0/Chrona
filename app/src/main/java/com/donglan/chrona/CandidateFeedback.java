package com.donglan.chrona;

/** Presentation derived only from persisted publication state and the current editor values. */
final class CandidateFeedback {
    private CandidateFeedback() { }

    static boolean automaticEnd(Long originalEnd, boolean generatedEnd, boolean deadline) {
        return generatedEnd || (originalEnd == null && !deadline);
    }

    static String state(boolean published, boolean dirty) {
        if (published) return dirty ? "已写入日历 · 修改待保存" : "已写入日历";
        return "待确认 · 未写入日历";
    }

    static String timeHint(String start, String end, boolean defaultEnd, boolean allDay) {
        boolean missingStart = start == null || start.trim().isEmpty();
        boolean missingEnd = end == null || end.trim().isEmpty();
        if (missingStart && missingEnd) return "请补充开始与结束时间";
        if (missingStart) return "请补充开始时间";
        if (missingEnd) return "请补充结束时间";
        return defaultEnd && !allDay ? "结束时间按日程类型默认补全" : "";
    }

    static String saveFailure(boolean published) {
        return published ? "更新日历未完成：" : "尚未写入日历，仍待确认：";
    }
}
