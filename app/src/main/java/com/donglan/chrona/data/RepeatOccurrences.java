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
    private static final long MIN_TIME = -62_135_596_800_000L;
    private static final long MAX_TIME = 253_402_300_799_999L;
    private static final int MAX_REMINDER_MINUTES = 525_600;
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
        long advance = series.hasDefaultExecutionInterval() ? EventCandidate.DEFAULT_EXECUTION_DURATION_MILLIS : 0;
        DateIterator iterator = iterator(series);
        iterator.advanceTo(new Date(Math.max(series.anchor(), begin - duration)));
        while (iterator.hasNext()) {
            long original = iterator.next().getTime();
            if (original >= end + advance) break;
            // Changed instances are handled separately, including moves into this window.
            if (exceptions.has(Long.toString(original))) continue;
            EventCandidate occurrence = project(series, original, null);
            if (occurrence != null && overlaps(occurrence, begin, end)) {
                result.add(occurrence);
                if (result.size() >= limit) break;
            }
        }
        for (EventCandidate occurrence:overrides(series)) if (overlaps(occurrence,begin,end)) result.add(occurrence);
        result.sort(Comparator.comparingLong(RepeatOccurrences::executionBegin));
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
    /** Read projections use the same stored overrides as the bounded occurrence iterator. */
    public static List<EventCandidate> overrides(EventCandidate series) {
        List<EventCandidate> result=new ArrayList<>();JSONObject changes=exceptions(series);
        java.util.Iterator<String> keys=changes.keys();
        while(keys.hasNext()){
            String key=keys.next();EventCandidate occurrence=project(series,Long.parseLong(key),changes.optJSONObject(key));
            if(occurrence!=null)result.add(occurrence);
        }
        return result;
    }

    /** Merge only supplied fields into one original occurrence; neither its identity nor template changes. */
    public static EventCandidate edit(EventCandidate series, long original, JSONObject fields) {
        if (series == null || series.id <= 0 || series.taskId <= 0 || series.isRecord
                || series.occurrenceAtMillis != null)
            throw new IllegalArgumentException("请选择原始重复日程");
        if (original < MIN_TIME || original > MAX_TIME) throw new IllegalArgumentException("原定时间无效");
        if (fields == null) throw new IllegalArgumentException("请提供本次修改的字段");
        EventCandidate current = at(series, original);
        if (current == null) throw new IllegalArgumentException("这一次日程已跳过或停止");
        validateFields(fields);
        try {
            JSONObject exceptions = exceptions(series);
            String key = Long.toString(original);
            JSONObject change = exceptions.optJSONObject(key);
            if (change == null) change = new JSONObject();
            JSONObject merged = fields(change);
            java.util.Iterator<String> keys = fields.keys();
            while (keys.hasNext()) {
                String field = keys.next();
                merged.put(field, fields.get(field));
            }
            change.put("fields", merged);
            exceptions.put(key, change);
            EventCandidate updated = series.withRepeatState(exceptions.toString(), series.repeatStoppedAtMillis);
            EventCandidate occurrence = at(updated, original);
            if (occurrence == null) throw new IllegalArgumentException("已停止的系列不能改到停止时间之后");
            validateOccurrence(occurrence);
            return updated;
        } catch (org.json.JSONException invalid) {
            throw new IllegalArgumentException("本次修改字段无效", invalid);
        }
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
        JSONObject fields = fields(change);
        validateFields(fields);
        EventCandidate occurrence = new EventCandidate(series.id, series.taskId,
                string(fields, "title", series.title), number(fields, "start", start),
                number(fields, "end", end), string(fields, "time_zone_id", series.timeZoneId),
                string(fields, "location", series.location), string(fields, "description", series.description),
                fields.has("reminder_minutes_before") ? fields.isNull("reminder_minutes_before") ? null
                        : (int) fields.optLong("reminder_minutes_before") : series.reminderMinutesBefore,
                fields.has("needs_confirmation") ? fields.optBoolean("needs_confirmation") : series.needsConfirmation,
                series.calendarEventId, string(fields, "category", series.category),
                fields.has("all_day") ? fields.optBoolean("all_day") : series.allDay,
                fields.has("end_auto_generated") ? fields.optBoolean("end_auto_generated") : series.endAutoGenerated,
                fields.has("uncertainty_level") ? (int) fields.optLong("uncertainty_level") : series.uncertaintyLevel,
                number(fields, "due", due), completed, false, series.repeatRule, series.repeatExceptions,
                series.repeatStoppedAtMillis, original);
        // Stop by the final actual execution start, after both legacy moves and field patches.
        Long actualStart = executionBegin(occurrence);
        if (series.repeatStoppedAtMillis != null && actualStart != null
                && actualStart >= series.repeatStoppedAtMillis) return null;
        return occurrence;
    }

    private static String string(JSONObject fields, String key, String inherited) {
        return !fields.has(key) ? inherited : fields.isNull(key) ? null : fields.optString(key);
    }

    private static JSONObject fields(JSONObject change) {
        if (change == null || !change.has("fields")) return new JSONObject();
        JSONObject fields = change.optJSONObject("fields");
        if (fields == null) throw new IllegalArgumentException("重复日程的单次字段记录损坏");
        return fields;
    }

    private static Long number(JSONObject fields, String key, Long inherited) {
        return !fields.has(key) ? inherited : fields.isNull(key) ? null : fields.optLong(key);
    }

    private static void validateFields(JSONObject fields) {
        java.util.Iterator<String> keys = fields.keys();
        while (keys.hasNext()) {
            String key = keys.next();
            Object value = fields.opt(key);
            boolean nullable = value == JSONObject.NULL;
            switch (key) {
                case "start": case "end": case "due":
                    if (!nullable) whole(value, MIN_TIME, MAX_TIME, key);
                    break;
                case "reminder_minutes_before":
                    if (!nullable) whole(value, 0, MAX_REMINDER_MINUTES, key);
                    break;
                case "uncertainty_level":
                    whole(value, EventCandidate.CERTAIN, EventCandidate.DOUBTFUL, key);
                    break;
                case "all_day": case "end_auto_generated": case "needs_confirmation":
                    if (!(value instanceof Boolean)) throw new IllegalArgumentException(key + " 必须为布尔值");
                    break;
                case "title": case "time_zone_id": case "category":
                    if (!(value instanceof String) || ((String) value).trim().isEmpty())
                        throw new IllegalArgumentException(key + " 不能为空");
                    if ("time_zone_id".equals(key)) {
                        try { ZoneId.of((String) value); }
                        catch (java.time.DateTimeException invalid) {
                            throw new IllegalArgumentException("日程时区无效", invalid);
                        }
                    }
                    if ("category".equals(key) && !java.util.Arrays.asList(EventCategory.VALUES).contains(value))
                        throw new IllegalArgumentException("日程类型无效");
                    break;
                case "location": case "description":
                    if (!nullable && !(value instanceof String))
                        throw new IllegalArgumentException(key + " 必须为文字或空值");
                    break;
                default: throw new IllegalArgumentException("不支持的单次字段：" + key);
            }
        }
    }

    private static void whole(Object value, long min, long max, String key) {
        if (!(value instanceof Integer) && !(value instanceof Long))
            throw new IllegalArgumentException(key + " 必须为整数");
        long number = ((Number) value).longValue();
        if (number < min || number > max) throw new IllegalArgumentException(key + " 超出范围");
    }

    private static void validateOccurrence(EventCandidate occurrence) {
        if (occurrence.anchor() == null || occurrence.startAtMillis == null && occurrence.endAtMillis != null
                || occurrence.startAtMillis != null && occurrence.endAtMillis != null
                && occurrence.endAtMillis <= occurrence.startAtMillis)
            throw new IllegalArgumentException("本次日程的起止时间无效");
        if (!occurrence.isTask() && occurrence.startAtMillis == null)
            throw new IllegalArgumentException("活动需要开始时间");
        for (Long time : new Long[]{occurrence.startAtMillis, occurrence.endAtMillis, occurrence.dueAtMillis})
            if (time != null && (time < MIN_TIME || time > MAX_TIME))
                throw new IllegalArgumentException("本次日程时间超出范围");
        if (occurrence.allDay) {
            if (!"UTC".equals(occurrence.timeZoneId)) throw new IllegalArgumentException("全天日程需要 UTC 日期");
            for (Long time : new Long[]{occurrence.startAtMillis, occurrence.endAtMillis, occurrence.dueAtMillis})
                if (time != null && time % 86_400_000L != 0) throw new IllegalArgumentException("全天日程请选择日期");
            if (occurrence.calendarEnd() % 86_400_000L != 0)
                throw new IllegalArgumentException("全天日程需要结束日期");
        }
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
        Long start = executionBegin(event);
        Long finish = event.hasDefaultExecutionInterval() ? event.executionEndAtMillis() : event.calendarEnd();
        return start != null && start < end && finish > begin;
    }
    private static Long executionBegin(EventCandidate event) {
        return event.hasDefaultExecutionInterval() ? event.executionStartAtMillis() : event.calendarStart();
    }
}
