package com.donglan.chrona;

import java.util.*;

/** Pure scheduling decisions, shared by alarms and foreground refresh. */
final class ReminderTimeline {
    static final long LEAD = 30 * 60_000L;
    static final long SNOOZE = 5 * 60_000L;
    static final long PROGRESS_REFRESH = 60_000L;
    static long remainingMillis(AgendaEntry entry, long now) {
        return Math.max(0, (now < entry.start ? entry.start : entry.finish()) - now);
    }
    static double elapsedRatio(AgendaEntry entry, long now, long notifiedAt) {
        boolean active = now >= entry.start && "event".equals(entry.category);
        long from = active ? entry.start : Math.min(entry.start, notifiedAt);
        long to = active ? entry.finish() : entry.start;
        return to <= from ? 1 : Math.max(0, Math.min(1, (double)(now - from) / (to - from)));
    }
    static String nextTitle(List<AgendaEntry> entries, AgendaEntry current, long now) {
        AgendaEntry next = null;
        for (AgendaEntry entry : entries) {
            if (!entry.confirmed || entry.allDay || entry.start <= Math.max(now, current.start)
                    || entry.key().equals(current.key())) continue;
            if (next == null || entry.start < next.start
                    || entry.start == next.start && entry.key().compareTo(next.key()) < 0) next = entry;
        }
        return next == null ? "" : next.title;
    }
    final List<AgendaEntry> entries;
    final long wakeAt;
    ReminderTimeline(List<AgendaEntry> candidates, long now, Set<String> hidden, Map<String, Long> snoozeUntil, int limit) {
        List<AgendaEntry> eligible = new ArrayList<>();
        long boundary = now + 24 * 60 * 60_000L;
        for (long until : snoozeUntil.values()) if (until > now) boundary = Math.min(boundary, until);
        Set<String> seen = new HashSet<>();
        for (AgendaEntry entry : candidates) {
            if (entry.allDay || !entry.confirmed || entry.finish() <= now || hidden.contains(entry.key())) continue;
            long due = entry.start - LEAD;
            if (due > now) boundary = Math.min(boundary, due);
            if (entry.start > now) boundary = Math.min(boundary, entry.start);
            boundary = Math.min(boundary, entry.finish());
            if (due <= now && snoozeUntil.getOrDefault(entry.key(), 0L) <= now && seen.add(entry.key())) eligible.add(entry);
        }
        eligible.sort((a, b) -> {
            boolean af = a.start > now, bf = b.start > now;
            if (af != bf) return af ? -1 : 1;
            int order = af ? Long.compare(a.start, b.start) : Long.compare(b.start, a.start);
            return order != 0 ? order : a.key().compareTo(b.key());
        });
        entries = List.copyOf(eligible.subList(0, Math.min(Math.max(0, limit), eligible.size())));
        wakeAt = boundary;
    }
}
