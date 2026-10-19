package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.calendar.CalendarOccurrence;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.calendar.AllDayDates;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.TaskStore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.LinkedHashMap;

/** A bounded, read-only snapshot shared by both desktop widgets. */
final class WidgetAgenda {
    static final int MAX_ITEMS = TaskStore.WIDGET_ITEM_LIMIT;
    static final class Item {
        final long id, taskId, start, end;
        final String title, location, category, source;
        final boolean system, allDay;
        final Long occurrenceAtMillis;
        Item(long id, long taskId, long start, long end, String title, String location,
                String category, String source, boolean system, boolean allDay) {
            this(id,taskId,start,end,title,location,category,source,system,allDay,null);
        }
        Item(long id, long taskId, long start, long end, String title, String location,
                String category, String source, boolean system, boolean allDay, Long occurrenceAtMillis) {
            this.occurrenceAtMillis = occurrenceAtMillis;
            this.id = id; this.taskId = taskId; this.start = start; this.end = end;
            this.title = title; this.location = location == null ? "" : location;
            this.category = category; this.source = source; this.system = system;
            this.allDay = allDay;
        }
        String time(long now, ZoneId zone) {
            LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
            LocalDate day = Instant.ofEpochMilli(start).atZone(zone).toLocalDate();
            String date = day.equals(today) ? "" : DateTimeFormatter.ofPattern("M月d日 ")
                    .format(day);
            if (allDay) return date + "全天";
            if(end==start) return date + "待办 · " + DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(start).atZone(zone));
            DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");
            String finish = Instant.ofEpochMilli(end).atZone(zone).toLocalDate().equals(day)
                    ? clock.format(Instant.ofEpochMilli(end).atZone(zone))
                    : DateTimeFormatter.ofPattern("M月d日 HH:mm")
                            .format(Instant.ofEpochMilli(end).atZone(zone));
            return date + clock.format(Instant.ofEpochMilli(start).atZone(zone)) + " — " + finish;
        }
    }
    final List<Item> today = new ArrayList<>();
    Item next;
    boolean calendarUnavailable;
    boolean failed;
    final long now;
    final ZoneId zone;
    final boolean previewTomorrow;
    final long nextTransition;

    WidgetAgenda(long now, ZoneId zone) {
        this(now, zone, WidgetPreferences.DEFAULT_PREVIEW_MINUTES);
    }

    WidgetAgenda(long now, ZoneId zone, int previewMinutes) {
        this.now = now;
        this.zone = zone;
        java.time.ZonedDateTime local = Instant.ofEpochMilli(now).atZone(zone);
        java.time.LocalTime preview = java.time.LocalTime.of(previewMinutes / 60, previewMinutes % 60);
        previewTomorrow = !local.toLocalTime().isBefore(preview);
        java.time.ZonedDateTime transition = previewTomorrow
                ? local.toLocalDate().plusDays(1).atStartOfDay(zone)
                : java.time.ZonedDateTime.ofLocal(local.toLocalDate().atTime(preview),
                        zone, local.getOffset());
        if (transition.toInstant().toEpochMilli() <= now)
            transition = local.toLocalDate().plusDays(1).atStartOfDay(zone);
        nextTransition = transition.toInstant().toEpochMilli();
    }

    /** Keep history in chronological order; focus the first unfinished occurrence. */
    int todayStartPosition() {
        for (int i = 0; i < today.size(); i++) {
            Item item = today.get(i);
            if (item.end > now || item.start >= now) return i;
        }
        return Math.max(0, today.size() - 1);
    }

    void select(List<Item> items, long begin, long tomorrow, long horizon) {
        long windowEnd = previewTomorrow ? Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .plusDays(2).atStartOfDay(zone).toInstant().toEpochMilli() : tomorrow;
        long windowBegin = previewTomorrow ? now : begin;
        items.sort(Comparator.comparingLong((Item item) -> item.start)
                .thenComparingLong(item -> item.id));
        for (Item item : items) {
            if (item.start >= horizon || item.end < begin) continue;
            if (item.start < windowEnd && (item.end > windowBegin || item.start >= windowBegin)
                    && today.size() < MAX_ITEMS) today.add(item);
            if (next == null && (item.end > now || item.start >= now)) next = item;
        }
    }

    static WidgetAgenda load(Context context) {
        CalendarLinkReconciler.reconcileNow(context);
        WidgetAgenda result = new WidgetAgenda(System.currentTimeMillis(), ZoneId.systemDefault(),
                WidgetPreferences.previewMinutes(context));
        LocalDate date = Instant.ofEpochMilli(result.now).atZone(result.zone).toLocalDate();
        long begin = date.atStartOfDay(result.zone).toInstant().toEpochMilli();
        long tomorrow = date.plusDays(1).atStartOfDay(result.zone).toInstant().toEpochMilli();
        long horizon = date.plusMonths(1).atStartOfDay(result.zone).toInstant().toEpochMilli();
        List<Item> items = new ArrayList<>();
        try (TaskStore store = new TaskStore(context)) {
            long utcBegin = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long utcTomorrow = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            long utcHorizon = date.plusMonths(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
            Map<String, EventCandidate> local = new LinkedHashMap<>();
            for (EventCandidate candidate : store.widgetCandidates(begin, tomorrow, utcBegin, utcTomorrow))
                local.put(candidate.occurrenceKey(), candidate);
            // Finished history may fill today's page, but must never hide the next unfinished item.
            for (EventCandidate candidate : store.widgetCandidates(result.now, horizon, utcBegin, utcHorizon))
                local.put(candidate.occurrenceKey(), candidate);
            for (EventCandidate candidate : local.values()) {
                long start = candidate.calendarStart();
                long end = candidate.point() ? start : candidate.calendarEnd();
                if (candidate.allDay) {
                    start = LocalDate.parse(AllDayDates.displayStart(start))
                            .atStartOfDay(result.zone).toInstant().toEpochMilli();
                    end = LocalDate.parse(AllDayDates.displayEnd(end)).plusDays(1)
                            .atStartOfDay(result.zone).toInstant().toEpochMilli();
                }
                items.add(new Item(candidate.id, candidate.taskId, start, end,
                        candidate.title, candidate.location, EventCategory.label(candidate.category),
                        "拾时", false, candidate.allDay, candidate.occurrenceAtMillis));
            }
            List<CalendarOccurrence> systemEntries = new ArrayList<>();
            if (WidgetPreferences.includesSystemCalendar(context)) {
                CalendarStore calendar = new CalendarStore(context);
                if (!calendar.hasReadPermission()) result.calendarUnavailable = true;
                else {
                    try {
                        systemEntries = calendar.listInstances(begin - 86400000L, horizon + 86400000L, null);
                    } catch (RuntimeException exception) { result.calendarUnavailable = true; }
                }
            }
            for (CalendarOccurrence event : ExternalCourseAgenda.merge(context, systemEntries,
                    begin - 86400000L, horizon + 86400000L, new HashSet<>(store.linkedCalendarIds()))) {
                items.add(new Item(event.eventId, 0, event.displayStart(result.zone),
                        event.displayEnd(result.zone), event.title, event.location,
                        "活动", event.calendarName, true, event.allDay));
            }
            result.select(items, begin, tomorrow, horizon);
        } catch (RuntimeException exception) {
            result.failed = true;
            result.today.clear();
            result.next = null;
        }
        return result;
    }
}
