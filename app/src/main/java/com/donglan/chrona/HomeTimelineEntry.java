package com.donglan.chrona;

import com.donglan.chrona.calendar.AllDayDates;
import com.donglan.chrona.calendar.CalendarOccurrence;
import com.donglan.chrona.data.EventCandidate;
import java.time.LocalDate;
import java.time.ZoneId;

/** Read-only home item; each source retains its own identity and destination. */
final class HomeTimelineEntry {
    private static final long ALL_DAY_DURATION_MILLIS = 86_400_000L;
    final long timestampMillis;
    final EventCandidate candidate;
    final CalendarOccurrence systemEvent;

    HomeTimelineEntry(EventCandidate candidate) {
        Long start = candidate.executionStartAtMillis() != null ? candidate.executionStartAtMillis()
                : deadlineOnly(candidate) ? candidate.dueAtMillis : null;
        if (start == null)
            throw new IllegalArgumentException("首页时间轴需要明确的执行开始时间");
        timestampMillis = candidate.executionAllDay() && !deadlineOnly(candidate)
                ? LocalDate.parse(AllDayDates.displayStart(start))
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : start;
        this.candidate = candidate;
        systemEvent = null;
    }

    HomeTimelineEntry(CalendarOccurrence event) {
        timestampMillis = event.displayStart(ZoneId.systemDefault());
        candidate = null;
        systemEvent = event;
    }

    String title() {
        return candidate != null ? candidate.title : systemEvent.title;
    }

    String location() {
        String value = candidate != null ? candidate.location : systemEvent.location;
        return value == null ? "" : value.trim();
    }

    static boolean deadlineOnly(EventCandidate candidate) {
        return candidate.isHomework() && candidate.startAtMillis == null && candidate.dueAtMillis != null;
    }

    static boolean hasUpcomingTime(EventCandidate candidate, long now, ZoneId zone) {
        if (deadlineOnly(candidate)) return !candidate.isRecord && !candidate.completed() && candidate.dueAtMillis >= now;
        return hasUpcomingExecution(candidate, now, zone);
    }

    static boolean hasUpcomingExecution(EventCandidate candidate, long now, ZoneId zone) {
        Long start = candidate.executionStartAtMillis();
        if (candidate.isRecord || candidate.completed() || start == null) return false;
        if (!candidate.isTask() && (candidate.endAtMillis == null
                || candidate.endAtMillis <= candidate.startAtMillis)) return false;
        long finish = executionEnd(candidate);
        if (candidate.executionAllDay()) {
            long todayUtc = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
            return finish > todayUtc;
        }
        return finish > now || (finish == start && finish == now);
    }

    static long executionEnd(EventCandidate candidate) {
        long start = candidate.executionStartAtMillis();
        Long end = candidate.executionEndAtMillis();
        if (end != null && end > start) return end;
        return candidate.executionAllDay() ? start + ALL_DAY_DURATION_MILLIS : start;
    }
}
