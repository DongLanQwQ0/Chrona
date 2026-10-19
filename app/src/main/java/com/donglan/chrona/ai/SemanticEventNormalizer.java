package com.donglan.chrona.ai;

import com.donglan.chrona.calendar.AllDayDates;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.EventTimeDefaults;
import com.donglan.chrona.data.RepeatRule;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

/** Converts extracted time facts into calendar values without guessing missing clock times. */
public final class SemanticEventNormalizer {
    private static final long MINUTE = 60_000L;
    public static final int DEFAULT_WORK_START = 10 * 60;
    public static final int DEFAULT_WORK_END = 22 * 60;
    private static final long DEFAULT_EXECUTION_MINUTES = 30;
    private static final long DAY = 24 * 60 * MINUTE;
    private static final long MIN_ADVANCE = 6 * 60 * MINUTE;
    private static final long MAX_ADVANCE = 3 * DAY;
    // The editor displays four-digit Gregorian years; reject epoch values outside that domain.
    private static final long MIN_EPOCH = -62_135_596_800_000L;
    private static final long MAX_EPOCH = 253_402_300_799_999L;
    private static final DateTimeFormatter DUE_TEXT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private SemanticEventNormalizer() { }

    private static final class Context {
        boolean uncertain;
        ZoneId zone;
        ZonedDateTime anchor;
    }

    private static final class TimeValue {
        LocalDate date;
        Long millis;
    }

    public static EventCandidate fromJson(long taskId, JSONObject event, long nowMillis,
            String fallbackZone) throws JSONException {
        return fromJson(taskId, event, nowMillis, fallbackZone, nowMillis,
                DEFAULT_WORK_START, DEFAULT_WORK_END);
    }

    public static EventCandidate fromJson(long taskId, JSONObject event, long nowMillis,
            String fallbackZone, long schedulingNowMillis, int workStartMinutes, int workEndMinutes)
            throws JSONException {
        Object rawTitle = event.opt("title");
        if (!(rawTitle instanceof String) || ((String) rawTitle).trim().isEmpty())
            throw new JSONException("Event title must be a nonempty string");
        String title = ((String) rawTitle).trim();
        Context context = new Context();
        Object rawLevel = event.opt("uncertainty_level");
        int modelLevel = rawLevel instanceof Integer && (Integer) rawLevel >= 0 && (Integer) rawLevel <= 2
                ? (Integer) rawLevel : EventCandidate.DOUBTFUL;
        Object confirmation = event.opt("needs_confirmation");
        context.uncertain = !(confirmation instanceof Boolean) || (Boolean) confirmation;
        // A reasonable inference is level 1; validation errors still escalate to level 2.
        if (event.has("uncertainty_level")) {
            context.uncertain = !(rawLevel instanceof Integer) || (Integer) rawLevel < 0 || (Integer) rawLevel > 2
                    || modelLevel == EventCandidate.DOUBTFUL || !(confirmation instanceof Boolean)
                    || (Boolean.TRUE.equals(confirmation) && modelLevel != EventCandidate.INFERRED);
        }
        String category = text(event, "category", context);
        if (category == null || !category.equals(EventCategory.normalize(category))) context.uncertain = true;
        category = EventCategory.normalize(category);
        String sourceCategory = category;
        String requestedZone = text(event, "time_zone_id", context);
        String zoneName = requestedZone == null ? fallbackZone : requestedZone;
        try {
            context.zone = ZoneId.of(zoneName);
            context.anchor = Instant.ofEpochMilli(nowMillis).atZone(context.zone);
        } catch (RuntimeException invalidZone) {
            context.uncertain = true;
        }
        boolean allDay = bool(event, "all_day", false, context);
        Long duration = integer(event, "duration_minutes", context);
        boolean invalidDuration = !event.isNull("duration_minutes") && (duration == null || duration <= 0);
        if (invalidDuration) context.uncertain = true;
        Long rawReminder = integer(event, "reminder_minutes_before", context);
        Integer reminder = null;
        if (rawReminder != null) {
            if (rawReminder < 0 || rawReminder > Integer.MAX_VALUE) context.uncertain = true;
            else reminder = rawReminder.intValue();
        }
        // The legacy protocol did not distinguish absent reminder from explicit no reminder.
        boolean specified = event.has("reminder_specified")
                ? bool(event, "reminder_specified", true, context) : rawReminder != null;
        Long start = null, end = null, due = null;
        boolean endAutoGenerated = false;
        if (context.zone != null) {
            TimeValue first = resolve(event, "start", context, null, allDay);
            TimeValue last = resolve(event, "end", context, first.date, allDay);
            TimeValue deadline = resolve(event, "due", context, null, allDay);
            if (!event.isNull("due")) {
                if (!EventCategory.HOMEWORK.equals(category)) category = EventCategory.TASK;
                due = deadline.millis;
                if (allDay && deadline.date != null) {
                    due = deadline.date.atTime(23,59).atZone(context.zone).toInstant().toEpochMilli();
                    if (first.date == null) allDay = false;
                }
            }
            if (allDay) {
                if (first.date != null) start = AllDayDates.utcStart(first.date.toString());
                LocalDate inclusiveEnd = last.date == null ? first.date : last.date;
                if (inclusiveEnd != null) end = AllDayDates.utcExclusiveEnd(inclusiveEnd.toString());
            } else {
                start = first.millis; end = last.millis;
                if (start != null && end == null && !invalidDuration
                        && (EventCategory.EVENT.equals(category) || duration != null)) {
                    end = add(start, durationMillis(duration, category, context), context);
                    endAutoGenerated = duration == null;
                }
                if (duration != null && !invalidDuration && start != null && end != null
                        && end - start != durationMillis(duration, category, context)) context.uncertain = true;
            }
        }
        start = epoch(start, context); end = epoch(end, context); due = epoch(due, context);
        if (start != null && end != null && end <= start) { end = null; context.uncertain = true; }
        if (end != null && start == null) context.uncertain = true;
        if (EventCategory.EVENT.equals(category) && (start == null || end == null)) context.uncertain = true;
        if (Boolean.TRUE.equals(event.opt("execution_time_explicit")) && start == null && end == null)
            context.uncertain = true;
        if (due != null && !specified && rawReminder == null) reminder = deadlineReminder(due, nowMillis, context.zone);
        String location = text(event, "location", context);
        String description = text(event, "description", context);
        // start/end remain source facts even if the model mislabeled their provenance.
        // A separate suggested_start can never overwrite an existing or unresolved source time.
        if (EventCategory.isTaskLike(sourceCategory) && due != null && due > schedulingNowMillis
                && Boolean.FALSE.equals(event.opt("execution_time_explicit"))
                && event.isNull("start") && event.isNull("end") && context.zone != null) {
            context.uncertain = true;
            if (!event.isNull("recurrence")) {
                description = append(description, "执行安排待确认：保留循环事项的截止锚点。");
            } else {
                TimeValue suggestion = resolve(event, "suggested_start", context, null, false);
                Long executionDuration = invalidDuration ? null : executionDurationMillis(duration);
                Long selected = executionDuration == null ? null : chooseExecutionStart(schedulingNowMillis,
                        due, suggestion.millis, executionDuration, ZoneId.of(fallbackZone), workStartMinutes, workEndMinutes);
                start = selected;
                end = selected == null ? null : selected + executionDuration;
                endAutoGenerated = selected != null && duration == null;
                allDay = false;
                description = append(description, selected == null
                        ? (executionDuration == null ? "执行安排待确认：执行时长无效。"
                                : "执行安排待确认：可用工作时间无法容纳执行时段。")
                        : "执行建议：" + DUE_TEXT.format(Instant.ofEpochMilli(start).atZone(context.zone))
                                + "–" + DUE_TEXT.format(Instant.ofEpochMilli(end).atZone(context.zone)) + "（待确认）");
            }
        }
        int level = context.uncertain ? EventCandidate.DOUBTFUL : modelLevel;
        EventCandidate candidate = new EventCandidate(0, taskId, title, start, end,
                allDay ? "UTC" : context.zone == null ? null : context.zone.getId(),
                location, description, reminder, level > EventCandidate.CERTAIN,
                null, category, allDay, endAutoGenerated && end != null, level, due, null, false);
        if (event.isNull("recurrence")) return candidate;
        Object rawRecurrence = event.opt("recurrence");
        if (!(rawRecurrence instanceof JSONObject)) throw new JSONException("Recurrence must be an object or null");
        JSONObject recurrence = (JSONObject) rawRecurrence;
        String[] keys = {"frequency", "interval", "weekdays", "until", "count"};
        if (recurrence.length() != keys.length) throw new JSONException("Recurrence must contain exactly five fields");
        for (String key : keys) if (!recurrence.has(key)) throw new JSONException("Missing recurrence field: " + key);
        Object until = recurrence.opt("until");
        if (until != JSONObject.NULL && (!(until instanceof String) || !((String) until).matches("\\d{4}-\\d{2}-\\d{2}")))
            throw new JSONException("Recurrence until must be a YYYY-MM-DD date or null");
        Long repeatAnchor = start != null ? start : due;
        if (repeatAnchor == null) throw new JSONException("Recurrence requires a first start or deadline");
        try {
            RepeatRule rule = RepeatRule.fromJson(recurrence);
            rule.validateAnchor(repeatAnchor, candidate.timeZoneId);
            return candidate.withRepeat(rule);
        } catch (IllegalArgumentException invalidRecurrence) {
            throw new JSONException("Invalid recurrence: " + invalidRecurrence.getMessage());
        }
    }

    public static EventCandidate recordFromJson(long taskId, JSONObject item, String zone) throws JSONException {
        String title = item.getString("title").trim();
        if (title.isEmpty()) throw new JSONException("Record title is empty");
        return new EventCandidate(0,taskId,title,null,null,zone,"",item.optString("description",""),
                null,false,null,EventCategory.EVENT,false,false,EventCandidate.CERTAIN,null,null,true);
    }

    private static String append(String description, String note) {
        return description == null || description.isEmpty() ? note : description + "\n" + note;
    }

    private static Long executionDurationMillis(Long minutes) {
        try {
            long value = minutes == null ? DEFAULT_EXECUTION_MINUTES : minutes;
            return value <= 0 ? null : Math.multiplyExact(value, MINUTE);
        } catch (ArithmeticException invalid) { return null; }
    }

    /** Picks the nearest complete daily work interval; never changes the deadline. */
    private static Long chooseExecutionStart(long now, long due, Long proposed, long duration,
            ZoneId zone, int workStart, int workEnd) {
        if (workStart < 0 || workStart >= 1440 || workEnd < 0 || workEnd >= 1440 || workStart == workEnd)
            throw new IllegalArgumentException("Invalid work window");
        long lower = now;
        long upper = due - duration;
        boolean distant = due - now > DAY;
        long target = now + (due - now) / 2;
        if (distant) {
            lower = Math.max(now, due - MAX_ADVANCE);
            upper = Math.min(upper, due - MIN_ADVANCE);
            target = due - (MAX_ADVANCE + MIN_ADVANCE) / 2;
        }
        if (upper < lower) return null;
        if (distant && proposed != null) target = proposed;
        target = Math.max(lower, Math.min(upper, target));
        // Whole-minute placement avoids showing a minute that has already begun as a future start.
        lower = Math.floorDiv(lower + MINUTE - 1, MINUTE) * MINUTE;
        upper = Math.floorDiv(upper, MINUTE) * MINUTE;
        target = Math.floorDiv(target, MINUTE) * MINUTE;
        LocalDate first = Instant.ofEpochMilli(lower).atZone(zone).toLocalDate().minusDays(1);
        LocalDate last = Instant.ofEpochMilli(upper).atZone(zone).toLocalDate();
        Long best = null;
        long bestDistance = Long.MAX_VALUE;
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            LocalDateTime startLocal = date.atTime(workStart / 60, workStart % 60);
            LocalDateTime endLocal = date.plusDays(workEnd < workStart ? 1 : 0)
                    .atTime(workEnd / 60, workEnd % 60);
            for (long windowStart : boundaryInstants(startLocal, zone)) {
                for (long windowEnd : boundaryInstants(endLocal, zone)) {
                    long availableStart = Math.max(lower, windowStart);
                    long availableEnd = Math.min(upper, windowEnd - duration);
                    if (availableStart > availableEnd) continue;
                    long selected = Math.max(availableStart, Math.min(availableEnd, target));
                    if (!withinWorkingHours(selected, selected + duration, zone, workStart, workEnd)) continue;
                    long distance = Math.abs(selected - target);
                    if (distance < bestDistance) { best = selected; bestDistance = distance; }
                }
            }
        }
        return best;
    }

    private static List<Long> boundaryInstants(LocalDateTime local, ZoneId zone) {
        List<Long> values = new java.util.ArrayList<>();
        for (ZoneOffset offset : zone.getRules().getValidOffsets(local))
            values.add(local.toInstant(offset).toEpochMilli());
        // A spring gap starts at the first real instant after the missing local clock.
        if (values.isEmpty()) values.add(zone.getRules().getTransition(local).getInstant().toEpochMilli());
        return values;
    }

    private static boolean withinWorkingHours(long start, long end, ZoneId zone, int from, int to) {
        if (!withinWorkClock(start, zone, from, to) || !withinWorkClock(end - 1, zone, from, to)) return false;
        java.time.zone.ZoneOffsetTransition transition = zone.getRules().nextTransition(Instant.ofEpochMilli(start));
        while (transition != null && transition.getInstant().toEpochMilli() < end) {
            long at = transition.getInstant().toEpochMilli();
            if (!withinWorkClock(at - 1, zone, from, to) || !withinWorkClock(at, zone, from, to)) return false;
            transition = zone.getRules().nextTransition(transition.getInstant());
        }
        return true;
    }

    private static boolean withinWorkClock(long millis, ZoneId zone, int from, int to) {
        LocalTime clock = Instant.ofEpochMilli(millis).atZone(zone).toLocalTime();
        int minute = clock.getHour() * 60 + clock.getMinute();
        return from < to ? minute >= from && minute < to : minute >= from || minute < to;
    }

    private static TimeValue resolve(JSONObject event, String key, Context context,
            LocalDate inheritedDate, boolean allDay) {
        TimeValue result = new TimeValue();
        if (event.isNull(key)) return result;
        Object raw = event.opt(key);
        if (!(raw instanceof JSONObject)) { context.uncertain = true; return result; }
        JSONObject value = (JSONObject) raw;
        try {
            String explicitDate = text(value, "date", context);
            String clock = text(value, "time", context);
            Long day = integer(value, "day_offset", context);
            Long minutes = integer(value, "offset_minutes", context);
            Long weekday = integer(value, "weekday", context);
            Long week = integer(value, "week_offset", context);
            LocalDate relativeDate = day == null ? null : context.anchor.toLocalDate().plusDays(day);
            LocalDate weekdayDate = null;
            if (weekday != null) {
                if (weekday < 1 || weekday > 7) throw new IllegalArgumentException("Invalid weekday");
                weekdayDate = week == null
                        ? context.anchor.toLocalDate().with(TemporalAdjusters.nextOrSame(DayOfWeek.of(weekday.intValue())))
                        : context.anchor.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                                .plusWeeks(week).plusDays(weekday - 1);
            } else if (week != null) context.uncertain = true;
            if (explicitDate != null) {
                if (!explicitDate.matches("\\d{4}-\\d{2}-\\d{2}")) throw new IllegalArgumentException("Invalid date");
                result.date = LocalDate.parse(explicitDate);
                if (relativeDate != null && !result.date.equals(relativeDate)) context.uncertain = true;
                if (weekday != null && result.date.getDayOfWeek().getValue() != weekday) context.uncertain = true;
                if (weekdayDate != null && week != null && !result.date.equals(weekdayDate)) context.uncertain = true;
            } else {
                result.date = relativeDate != null ? relativeDate : weekdayDate != null ? weekdayDate
                        : clock != null ? inheritedDate : null;
                if (relativeDate != null && weekdayDate != null && !relativeDate.equals(weekdayDate)) context.uncertain = true;
            }
            Long offsetInstant = minutes == null ? null : add(context.anchor.toInstant().toEpochMilli(),
                    Math.multiplyExact(minutes, MINUTE), context);
            if (offsetInstant != null && result.date == null && clock == null) {
                result.millis = offsetInstant;
                result.date = Instant.ofEpochMilli(offsetInstant).atZone(context.zone).toLocalDate();
                if (allDay) context.uncertain = true;
                return result;
            }
            // Keep a usable draft if the model supplies only a clock despite the prompt's date rule.
            // Do not reinterpret AM/PM here: the model has the original text and owns that inference.
            if (clock != null && result.date == null && day == null && weekday == null && explicitDate == null) {
                result.date = context.anchor.toLocalDate();
                LocalTime nextClock = clock.equals("24:00") || clock.equals("24:00:00")
                        ? LocalTime.of(23, 59) : LocalTime.parse(clock);
                if (result.date.atTime(nextClock).isBefore(context.anchor.toLocalDateTime()))
                    result.date = result.date.plusDays(1);
                context.uncertain = true;
            }
            if (allDay) {
                if (clock != null || minutes != null) context.uncertain = true;
                return result;
            }
            if (clock == null || result.date == null) { context.uncertain = true; return result; }
            if (!clock.matches("\\d{2}:\\d{2}(:\\d{2})?")) throw new IllegalArgumentException("Invalid clock");
            boolean midnightEnd = clock.equals("24:00") || clock.equals("24:00:00");
            // Product convention: end-of-day stays on the stated day, including deadlines.
            LocalTime parsedClock = midnightEnd ? LocalTime.of(23, 59) : LocalTime.parse(clock);
            LocalDateTime local = result.date.atTime(parsedClock);
            if (explicitDate == null && day == null && weekday != null && week == null
                    && local.isBefore(context.anchor.toLocalDateTime())) {
                result.date = result.date.plusWeeks(1);
                local = result.date.atTime(parsedClock);
            }
            List<ZoneOffset> offsets = context.zone.getRules().getValidOffsets(local);
            if (offsets.size() != 1) { context.uncertain = true; return result; }
            result.millis = epoch(local.toInstant(offsets.get(0)).toEpochMilli(), context);
            if (offsetInstant != null && !offsetInstant.equals(result.millis)) context.uncertain = true;
        } catch (RuntimeException invalidTime) { context.uncertain = true; result.millis = null; result.date = null; }
        return result;
    }

    private static long durationMillis(Long duration, String category, Context context) {
        if (duration == null) return EventTimeDefaults.durationMillis(category);
        try {
            if (duration <= 0) throw new IllegalArgumentException("Invalid duration");
            return Math.multiplyExact(duration, MINUTE);
        } catch (RuntimeException invalid) { context.uncertain = true; return 0; }
    }

    private static Long add(long value, long delta, Context context) {
        try { return epoch(Math.addExact(value, delta), context); }
        catch (ArithmeticException overflow) { context.uncertain = true; return null; }
    }

    private static Long epoch(Long value, Context context) {
        if (value != null && (value < MIN_EPOCH || value > MAX_EPOCH)) {
            context.uncertain = true;
            return null;
        }
        return value;
    }

    private static int deadlineReminder(long due, long now, ZoneId zone) {
        if (due <= now || due - now <= 60 * MINUTE) return 10;
        if (Instant.ofEpochMilli(due).atZone(zone).toLocalDate()
                .equals(Instant.ofEpochMilli(now).atZone(zone).toLocalDate())) return 30;
        return due - now <= 3 * 24 * 60 * MINUTE ? 120 : 360;
    }

    private static String text(JSONObject object, String key, Context context) {
        if (object.isNull(key)) return null;
        Object value = object.opt(key);
        if (!(value instanceof String)) { context.uncertain = true; return null; }
        return ((String) value).trim().isEmpty() ? null : ((String) value).trim();
    }

    private static Long integer(JSONObject object, String key, Context context) {
        if (object.isNull(key)) return null;
        Object value = object.opt(key);
        if (!(value instanceof Integer) && !(value instanceof Long)) { context.uncertain = true; return null; }
        return ((Number) value).longValue();
    }

    private static boolean bool(JSONObject object, String key, boolean fallback, Context context) {
        if (!object.has(key)) return fallback;
        Object value = object.opt(key);
        if (!(value instanceof Boolean)) { context.uncertain = true; return fallback; }
        return (Boolean) value;
    }
}
