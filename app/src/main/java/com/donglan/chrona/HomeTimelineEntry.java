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
        if (candidate.startAtMillis == null)
            throw new IllegalArgumentException("首页时间轴需要明确的执行开始时间");
        timestampMillis = candidate.allDay
                ? LocalDate.parse(AllDayDates.displayStart(candidate.startAtMillis))
                        .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : candidate.startAtMillis;
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

    static boolean hasUpcomingExecution(EventCandidate candidate, long now, ZoneId zone) {
        if (candidate.isRecord || candidate.completed() || candidate.startAtMillis == null) return false;
        if (!candidate.isTask() && (candidate.endAtMillis == null
                || candidate.endAtMillis <= candidate.startAtMillis)) return false;
        long finish = executionEnd(candidate);
        if (candidate.allDay) {
            long todayUtc = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                    .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
            return finish > todayUtc;
        }
        return finish > now || (finish == candidate.startAtMillis && finish == now);
    }

    static long executionEnd(EventCandidate candidate) {
        long start = candidate.startAtMillis;
        if (candidate.endAtMillis != null && candidate.endAtMillis > start) return candidate.endAtMillis;
        return candidate.allDay ? start + ALL_DAY_DURATION_MILLIS : start;
    }
}
