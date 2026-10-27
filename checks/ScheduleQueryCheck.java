package com.donglan.chrona.data;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;

/** Emits the actual query clauses for the SQLite behavior check. */
public final class ScheduleQueryCheck {
    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        LocalDate day = LocalDate.of(2026, 9, 28);
        long now = day.atTime(12, 0).atZone(zone).toInstant().toEpochMilli();
        checkOverdueModel(now, zone);
        emit("all", new ScheduleQuery(3, null, 0, "", null, null, now, zone));
        emit("upcoming", new ScheduleQuery(0, null, 0, "", null, null, now, zone));
        emit("incomplete", new ScheduleQuery(1, null, 0, "", null, null, now, zone));
        emit("past", new ScheduleQuery(2, null, 0, "", null, null, now, zone));
        emit("day", new ScheduleQuery(3, null, 0, "", day, day.plusDays(1), now, zone));
        emit("combined", new ScheduleQuery(3, "task", 1, "", day, day.plusDays(1), now, zone));
        emit("literal", new ScheduleQuery(3, null, 0, "%_", null, null, now, zone));
        emit("published", new ScheduleQuery(3, null, 2, "", null, null, now, zone));
        emit("label", new ScheduleQuery(3, null, 0, "待办", null, null, now, zone));
        emit("homework", new ScheduleQuery(3, ScheduleQuery.CATEGORY_HOMEWORK, 0, "", null, null, now, zone));
        emit("homeworkOpen", new ScheduleQuery(3, ScheduleQuery.CATEGORY_HOMEWORK, 0, "", null, null, now, zone, ScheduleQuery.TODO_OPEN));
        emit("homeworkToday", new ScheduleQuery(3, ScheduleQuery.CATEGORY_HOMEWORK, 0, "", null, null, now, zone, ScheduleQuery.TODO_TODAY_DUE));
        emit("overdue", new ScheduleQuery(3, null, 0, "", null, null, now, zone, ScheduleQuery.TODO_OVERDUE));
        emit("overdueReverse", new ScheduleQuery(3, null, 0, "", null, null, now, zone, ScheduleQuery.TODO_OVERDUE), false);
        emit("homeworkOverdue", new ScheduleQuery(3, "homework", 0, "", null, null, now, zone, ScheduleQuery.TODO_OVERDUE));
        ScheduleQuery all = new ScheduleQuery(3, null, 0, "", null, null, now, zone);
        emit("reverse", all, false);
        emit("dst", new ScheduleQuery(3, null, 0, "", LocalDate.of(2026, 3, 8),
                LocalDate.of(2026, 3, 9), now, ZoneId.of("America/Los_Angeles")));
    }

    private static EventCandidate task(String category, Long start, Long end, Long due,
            Long completed, boolean record) {
        return new EventCandidate(1, 1, "overdue fixture", start, end, "Asia/Shanghai", "", "",
                10, false, null, category, false, false, EventCandidate.CERTAIN,
                due, completed, record);
    }

    private static void require(boolean passed, String reason) {
        if (!passed) throw new AssertionError(reason);
    }

    private static void checkOverdueModel(long now, ZoneId zone) {
        long hour = 3_600_000L, day = 24 * hour;
        for (String category : new String[]{"task", "homework"}) {
            EventCandidate ended = task(category, now - 2 * hour, now - hour, now + day, null, false);
            require(ended.overdue(now) && "已逾期".equals(ended.taskState(now)), category + " missed execution with future deadline");
            require(ended.effectiveDueAtMillis() == now + day && ended.dueAtMillis == now + day
                    && ended.reminderAnchor() == now + day && ended.reminderMinutesBefore == 10,
                    "explicit deadline and reminder remain unchanged");
            require(!task(category, now - hour, now + hour, now + day, null, false).overdue(now), "ongoing interval");
            require(task(category, now - hour, null, now + day, null, false).overdue(now), "start-only");
            require(task(category, null, now - hour, now + day, null, false).overdue(now), "end-only");
            require(task(category, now + hour, now + 2 * hour, now - hour, null, false).overdue(now), "earlier explicit deadline");
            require(!task(category, now - hour, now, now + day, null, false).overdue(now), "equal execution boundary");
            require(!task(category, null, null, now, null, false).overdue(now), "equal deadline boundary");
            require(!task(category, null, null, null, null, false).overdue(now), "undated");
            require(!task(category, now - 2 * hour, now - hour, now - hour, now, false).overdue(now), "completed");
            require(!task(category, now - 2 * hour, now - hour, now - hour, null, true).overdue(now), "record");
        }
        require(!task("event", now - 2 * hour, now - hour, now - hour, null, false).overdue(now), "event");
        EventCandidate dueOnly = task("task", null, null, now + 10 * 60_000L, null, false);
        require(dueOnly.executionStartAtMillis() < now && !dueOnly.overdue(now), "default execution start is not overdue anchor");
        require(dueOnly.startAtMillis == null && dueOnly.endAtMillis == null
                && dueOnly.executionEndAtMillis().equals(dueOnly.dueAtMillis), "default interval remains derived");

        ScheduleQuery overdue = new ScheduleQuery(3, null, 0, "", null, null, now, zone, ScheduleQuery.TODO_OVERDUE);
        EventCandidate series = task("task", now - 2 * day, now - 2 * day + hour, now + 5 * day, null, false)
                .withRepeat(new RepeatRule("daily", 1, java.util.List.of(), null, 4));
        EventCandidate occurrence = overdue.seriesRow(series);
        require(occurrence != null && occurrence.overdue(now) && occurrence.dueAtMillis > now, "series not hidden by far deadline");
        long first = series.anchor();
        EventCandidate completedFirst = series.withRepeatState("{\"" + first + "\":{\"completed\":" + now + "}}", null);
        occurrence = overdue.seriesRow(completedFirst);
        require(occurrence != null && occurrence.occurrenceAtMillis == first + day && !occurrence.completed(), "completed occurrence does not complete series");
        EventCandidate moved = RepeatOccurrences.edit(series, first, json("start", now + day, "end", now + day + hour));
        occurrence = overdue.seriesRow(moved);
        require(occurrence != null && occurrence.occurrenceAtMillis == first + day, "future reschedule excluded per instance");
        EventCandidate future = task("task", now + 2 * day, now + 2 * day + hour, now + 9 * day, null, false)
                .withRepeat(new RepeatRule("daily", 1, java.util.List.of(), null, 2));
        moved = RepeatOccurrences.edit(future, future.anchor(), json("start", now - 2 * hour, "end", now - hour));
        occurrence = overdue.seriesRow(moved);
        require(occurrence != null && occurrence.occurrenceAtMillis.equals(future.anchor()) && occurrence.overdue(now), "future instance moved into overdue window");
    }

    private static org.json.JSONObject json(String first, long a, String second, long b) {
        try { return new org.json.JSONObject().put(first, a).put(second, b); }
        catch (org.json.JSONException invalid) { throw new AssertionError(invalid); }
    }

    private static void emit(String name, ScheduleQuery query) {
        emit(name, query, true);
    }

    private static void emit(String name, ScheduleQuery query, boolean oldestFirst) {
        StringBuilder line = new StringBuilder(name).append('\t').append(encode(query.selection))
                .append('\t').append(encode(query.orderBy(oldestFirst)));
        for (String value : query.arguments) line.append('\t').append(encode(value));
        System.out.println(line);
    }

    private static String encode(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }
}
