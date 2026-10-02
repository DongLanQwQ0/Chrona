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
        emit("all", new ScheduleQuery(3, null, 0, "", null, null, now, zone));
        emit("upcoming", new ScheduleQuery(0, null, 0, "", null, null, now, zone));
        emit("incomplete", new ScheduleQuery(1, null, 0, "", null, null, now, zone));
        emit("past", new ScheduleQuery(2, null, 0, "", null, null, now, zone));
        emit("day", new ScheduleQuery(3, null, 0, "", day, day.plusDays(1), now, zone));
        emit("combined", new ScheduleQuery(3, "task", 1, "", day, day.plusDays(1), now, zone));
        emit("literal", new ScheduleQuery(3, null, 0, "%_", null, null, now, zone));
        emit("published", new ScheduleQuery(3, null, 2, "", null, null, now, zone));
        emit("label", new ScheduleQuery(3, null, 0, "待办", null, null, now, zone));
        ScheduleQuery all = new ScheduleQuery(3, null, 0, "", null, null, now, zone);
        emit("reverse", all, false);
        emit("dst", new ScheduleQuery(3, null, 0, "", LocalDate.of(2026, 3, 8),
                LocalDate.of(2026, 3, 9), now, ZoneId.of("America/Los_Angeles")));
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
