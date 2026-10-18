package com.donglan.chrona;

import android.content.Context;
import android.content.Intent;
import android.content.ContentUris;
import android.provider.CalendarContract;
import com.donglan.chrona.calendar.AllDayDates;
import com.donglan.chrona.calendar.CalendarOccurrence;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskStore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;

/** Read-only calendar snapshot. Call on a worker, never from a rendering callback. */
final class AgendaRepository {
    static final int LIMIT = 5000;
    static final class Snapshot {
        final List<AgendaEntry> entries = new ArrayList<>();
        boolean systemUnavailable, truncated;
        ZoneId zone = ZoneId.systemDefault();
        boolean sameContents(Snapshot other) {
            return systemUnavailable == other.systemUnavailable && truncated == other.truncated
                    && zone.equals(other.zone) && entries.equals(other.entries);
        }
    }
    static Snapshot load(Context context, long begin, long until, boolean system, ZoneId zone,
            android.os.CancellationSignal cancellation) {
        Snapshot result = new Snapshot();
        result.zone = zone;
        cancellation.throwIfCanceled();
        LocalDate first = Instant.ofEpochMilli(begin).atZone(zone).toLocalDate();
        LocalDate last = Instant.ofEpochMilli(until).atZone(zone).toLocalDate().plusDays(1);
        try (TaskStore store = new TaskStore(context)) {
            List<EventCandidate> candidates = store.calendarCandidates(begin, until,
                    first.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
                    last.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
            result.truncated = candidates.size() > LIMIT;
            for (EventCandidate candidate : candidates) {
                cancellation.throwIfCanceled();
                long start = candidate.calendarStart();
                long end = candidate.point() ? start : candidate.calendarEnd();
                if (candidate.allDay) {
                    start = LocalDate.parse(AllDayDates.displayStart(start)).atStartOfDay(zone).toInstant().toEpochMilli();
                    end = LocalDate.parse(AllDayDates.displayEnd(end)).plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
                }
                AgendaEntry entry = new AgendaEntry(candidate.id, candidate.taskId, start, end,
                        candidate.title, candidate.location, candidate.description, "拾时", false,
                        candidate.allDay, !candidate.needsConfirmation, null, candidate.category,candidate.reminderMinutesBefore,candidate.effectiveDueAtMillis());
                if (entry.overlaps(begin, until) && result.entries.size() < LIMIT) result.entries.add(entry);
            }
            List<CalendarOccurrence> systemEntries = new ArrayList<>();
            if (system) {
                CalendarStore calendar = new CalendarStore(context);
                if (!calendar.hasReadPermission()) result.systemUnavailable = true;
                else try {
                    systemEntries = calendar.listInstances(begin - 86_400_000L,
                            until + 86_400_000L, cancellation, LIMIT + 1);
                    result.truncated |= systemEntries.size() > LIMIT;
                } catch (android.os.OperationCanceledException cancelled) { throw cancelled; }
                catch (RuntimeException exception) { result.systemUnavailable = true; }
            }
            for (CalendarOccurrence item : ExternalCourseAgenda.merge(context, systemEntries,
                    begin - 86_400_000L, until + 86_400_000L, new HashSet<>(store.linkedCalendarIds()))) {
                cancellation.throwIfCanceled();
                AgendaEntry entry = new AgendaEntry(item.eventId, 0, item.displayStart(zone),
                        item.displayEnd(zone), item.title, item.location, item.description, item.calendarName,
                        true, item.allDay, true, item.calendarColor);
                if (!entry.overlaps(begin, until)) continue;
                if (result.entries.size() == LIMIT) { result.truncated = true; break; }
                result.entries.add(entry);
            }
        }
        cancellation.throwIfCanceled();
        result.entries.sort(Comparator.comparingLong((AgendaEntry e) -> e.start).thenComparing(AgendaEntry::key));
        return result;
    }
    static Intent details(Context context, AgendaEntry entry) {
        if (!entry.system) return new Intent(context, TaskDetailActivity.class)
                .putExtra("task_id", entry.taskId).putExtra("candidate_id", entry.id);
        long start = entry.start, end = entry.end;
        if (entry.allDay) {
            ZoneId zone = ZoneId.systemDefault();
            start = Instant.ofEpochMilli(start).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            end = Instant.ofEpochMilli(end).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        }
        return SystemAgendaDetailActivity.intent(context, entry.id, start, end,
                entry.allDay, entry.title, entry.location, entry.source).putExtra("description", entry.description);
    }
    private AgendaRepository() { }
}
