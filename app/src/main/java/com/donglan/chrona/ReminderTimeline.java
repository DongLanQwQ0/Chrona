package com.donglan.chrona;

import java.util.List;
import java.util.Set;

/** Pure scheduling decisions, shared by alarms and foreground refresh. */
final class ReminderTimeline {
    static final long LEAD = 30 * 60_000L;
    static final long SNOOZE = 5 * 60_000L;
    final AgendaEntry focus;
    final long wakeAt;
    final boolean visible;
    ReminderTimeline(List<AgendaEntry> entries, long now, Set<String> hidden, String snoozed, long snoozeUntil) {
        AgendaEntry future = null, active = null;
        for (AgendaEntry entry : entries) {
            if (entry.allDay || !entry.confirmed || entry.finish() <= now || hidden.contains(entry.key())) continue;
            if (entry.start > now) {
                if (future == null || entry.start < future.start
                        || (entry.start == future.start && entry.key().compareTo(future.key()) < 0)) future = entry;
            } else if (active == null || entry.start > active.start
                    || (entry.start == active.start && entry.key().compareTo(active.key()) < 0)) active = entry;
        }
        AgendaEntry first = future != null && future.start - LEAD <= now ? future : active != null ? active : future;
        focus = first;
        if (first == null) { visible = false; wakeAt = now + 24 * 60 * 60_000L; return; }
        long due = first.start - LEAD;
        long deferred = first.key().equals(snoozed) ? snoozeUntil : 0;
        visible = now >= due && now >= deferred;
        long boundary = now < due ? due : now < deferred ? Math.min(deferred, first.finish())
                : now < first.start ? first.start : first.finish();
        wakeAt = future != null && future.start - LEAD > now ? Math.min(boundary, future.start - LEAD) : boundary;
    }
}
