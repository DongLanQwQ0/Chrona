package com.donglan.chrona;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;

/** Counts overlap by local dates in O(events + visible dates), with exclusive event ends. */
final class CalendarDayCounts {
    private final LocalDate first;
    private final int[] counts;
    CalendarDayCounts(List<AgendaEntry> entries, LocalDate first, LocalDate until, ZoneId zone) {
        this.first = first;
        int days = Math.toIntExact(ChronoUnit.DAYS.between(first, until));
        int[] changes = new int[days + 1];
        for (AgendaEntry entry : entries) {
            LocalDate begin = Instant.ofEpochMilli(entry.start).atZone(zone).toLocalDate();
            LocalDate end = Instant.ofEpochMilli(entry.finish() - 1).atZone(zone).toLocalDate().plusDays(1);
            int left = (int) Math.max(0, Math.min(days, ChronoUnit.DAYS.between(first, begin)));
            int right = (int) Math.max(0, Math.min(days, ChronoUnit.DAYS.between(first, end)));
            if (right > left) { changes[left]++; changes[right]--; }
        }
        counts = new int[days];
        int total = 0;
        for (int i = 0; i < days; i++) { total += changes[i]; counts[i] = total; }
    }
    int on(LocalDate day) {
        long index = ChronoUnit.DAYS.between(first, day);
        return index < 0 || index >= counts.length ? 0 : counts[(int) index];
    }
}
