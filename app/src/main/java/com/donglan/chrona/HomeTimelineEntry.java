package com.donglan.chrona;

import com.donglan.chrona.calendar.AllDayDates;
import com.donglan.chrona.calendar.CalendarOccurrence;
import com.donglan.chrona.data.EventCandidate;
import java.time.LocalDate;
import java.time.ZoneId;

/** Read-only home item; each source retains its own identity and destination. */
final class HomeTimelineEntry {
    final long timestampMillis;
    final EventCandidate candidate;
    final CalendarOccurrence systemEvent;

    HomeTimelineEntry(EventCandidate candidate) {
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
}
