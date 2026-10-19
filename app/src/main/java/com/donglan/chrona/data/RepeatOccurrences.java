package com.donglan.chrona.data;

import biweekly.component.VEvent;
import biweekly.util.Frequency;
import biweekly.util.Recurrence;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
import org.json.JSONObject;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;

/** Bounded projections, never persisted as replacement series rows. */
public final class RepeatOccurrences {
    private RepeatOccurrences() { }
    public static List<EventCandidate> between(EventCandidate series, long begin, long end, int limit) {
        ArrayList<EventCandidate> result = new ArrayList<>();
        if (end <= begin || limit < 1) return result;
        if (series.repeatRule == null) {
            if (overlaps(series, begin, end)) result.add(series);
            return result;
        }
        if (series.anchor() == null || series.isRecord) throw new IllegalArgumentException("重复日程需要首次时间");
        JSONObject exceptions = exceptions(series);
        long duration = Math.max(60_000L, series.calendarEnd() - series.anchor());
        DateIterator iterator = iterator(series);
        iterator.advanceTo(new Date(Math.max(series.anchor(), begin - duration)));
        while (iterator.hasNext()) {
            long original = iterator.next().getTime();
            if (original >= end) break;
            // Changed instances are handled separately, including moves into this window.
            if (exceptions.has(Long.toString(original))) continue;
            EventCandidate occurrence = project(series, original, null);
            if (occurrence != null && overlaps(occurrence, begin, end)) {
                result.add(occurrence);
                if (result.size() >= limit) break;
            }
        }
        java.util.Iterator<String> keys = exceptions.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            EventCandidate occurrence = project(series, Long.parseLong(key), exceptions.optJSONObject(key));
            if (occurrence != null && overlaps(occurrence, begin, end)) result.add(occurrence);
        }
        result.sort(Comparator.comparingLong(e -> e.anchor()));
        return result.size() <= limit ? result : new ArrayList<>(result.subList(0, limit));
    }

    public static EventCandidate at(EventCandidate series, long original) {
        if (series.repeatRule == null) throw new IllegalArgumentException("此日程未设置重复");
        DateIterator iterator = iterator(series);
        iterator.advanceTo(new Date(original));
        if (!iterator.hasNext() || iterator.next().getTime() != original)
            throw new IllegalArgumentException("这一次日程已不存在");
        return project(series, original, exceptions(series).optJSONObject(Long.toString(original)));
    }

    private static DateIterator iterator(EventCandidate series) {
        RepeatRule rule = series.repeatRule;
        ZoneId zone = ZoneId.of(series.allDay ? "UTC" : series.timeZoneId);
        rule.validateAnchor(series.anchor(), zone.getId());
        Recurrence.Builder builder = new Recurrence.Builder(Frequency.valueOf(rule.frequency.toUpperCase(java.util.Locale.ROOT)))
                .interval(rule.interval).workweekStarts(biweekly.util.DayOfWeek.MONDAY);
        if (rule.count != null) builder.count(rule.count);
        if (rule.until != null) builder.until(Date.from(rule.until.plusDays(1).atStartOfDay(zone).toInstant().minusSeconds(1)));
        for (int day : rule.weekdays) builder.byDay(biweekly.util.DayOfWeek.valueOf(java.time.DayOfWeek.of(day).name()));
        VEvent event = new VEvent();
        event.setDateStart(new Date(series.anchor()));
        event.setRecurrenceRule(builder.build());
        return event.getDateIterator(TimeZone.getTimeZone(zone));
    }

    private static EventCandidate project(EventCandidate series, long original, JSONObject change) {
        if (change != null && change.optBoolean("skipped")) return null;
        long actual = change == null ? original : change.optLong("start", original);
        Long start = shift(series.startAtMillis, series.anchor(), original);
        Long end = shift(series.endAtMillis, series.anchor(), original);
        Long due = shift(series.dueAtMillis, series.anchor(), original);
        long moved = actual - original;
        if (start != null) start += moved;
        if (end != null) end += moved;
        if (due != null) due += moved;
        Long completed = change == null || change.isNull("completed") ? null : change.optLong("completed");
        // Stop future actual occurrences, including an old occurrence moved into the future.
        if (series.repeatStoppedAtMillis != null && actual >= series.repeatStoppedAtMillis) return null;
        return new EventCandidate(series.id, series.taskId, series.title, start, end, series.timeZoneId,
                series.location, series.description, series.reminderMinutesBefore, series.needsConfirmation,
                series.calendarEventId, series.category, series.allDay, series.endAutoGenerated,
                series.uncertaintyLevel, due, completed, false, series.repeatRule, series.repeatExceptions,
                series.repeatStoppedAtMillis, original);
    }

    private static Long shift(Long time, long anchor, long original) {
        if (time == null) return null;
        // Provider DURATION and moved exceptions preserve elapsed length across DST.
        return Math.addExact(original, Math.subtractExact(time, anchor));
    }

    public static JSONObject exceptions(EventCandidate series) {
        try { return new JSONObject(series.repeatExceptions); }
        catch (org.json.JSONException invalid) { throw new IllegalArgumentException("重复日程的单次调整记录损坏", invalid); }
    }

    private static boolean overlaps(EventCandidate event, long begin, long end) {
        return event.anchor() != null && event.anchor() < end && event.calendarEnd() > begin;
    }
}
