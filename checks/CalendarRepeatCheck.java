package com.donglan.chrona.calendar;

import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.RepeatRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;

/** JVM checks for the exact production provider recurrence boundary and preserved input contract. */
public final class CalendarRepeatCheck {
    private static int checks;
    private static long time(String value) { return Instant.parse(value).toEpochMilli(); }
    private static void expect(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }
    private static RepeatRule daily(Integer count, LocalDate until) {
        return new RepeatRule("daily", 1, List.of(), until, count);
    }
    private static String rule(RepeatRule rule, String first, String stop) {
        return CalendarStore.stoppedRule(rule, time(first), "UTC", false, time(stop));
    }
    public static void main(String[] args) throws Exception {
        expect(rule(daily(null, null), "2026-01-01T09:00:00Z", "2026-01-03T09:00:00Z")
                .endsWith("UNTIL=20260103T085959Z"), "stop boundary is exclusive");
        String countFirst = rule(daily(2, null), "2026-01-01T09:00:00Z", "2026-03-01T09:00:00Z");
        expect(countFirst.endsWith("UNTIL=20260102T090000Z"), "COUNT cannot be extended by a later stop");
        expect(!countFirst.contains("COUNT="), "COUNT and UNTIL are mutually exclusive");
        expect(rule(daily(100, null), "2026-01-01T09:00:00Z", "2026-01-03T09:00:00Z")
                .endsWith("UNTIL=20260103T085959Z"), "stop can shorten COUNT");
        expect(rule(daily(1, null), "2026-01-01T09:00:00Z", "2026-03-01T09:00:00Z")
                .endsWith("UNTIL=20260101T090000Z"), "COUNT one retains the first occurrence");
        expect(rule(daily(null, LocalDate.parse("2026-01-02")), "2026-01-01T09:00:00Z", "2026-03-01T09:00:00Z")
                .endsWith("UNTIL=20260102T235959Z"), "existing UNTIL stays earlier");
        RepeatRule weekly = new RepeatRule("weekly", 2, List.of(1, 3), null, 4);
        expect(rule(weekly, "2026-01-05T09:00:00Z", "2027-01-01T09:00:00Z")
                .endsWith("UNTIL=20260121T090000Z"), "biweekly weekday COUNT expands actual last date");
        expect(CalendarStore.stoppedRule(daily(2, null), time("2026-01-01T00:00:00Z"), "UTC", true,
                time("2026-03-01T00:00:00Z")).endsWith("UNTIL=20260102"), "all-day date-only boundary");
        expect(CalendarStore.stoppedRule(daily(null, null), time("2026-01-01T00:00:00Z"), "UTC", true,
                time("2026-01-03T00:00:00Z")).endsWith("UNTIL=20260102"), "all-day stop excludes midnight");
        long dstFirst = LocalDate.parse("2026-03-07").atTime(9, 0).atZone(ZoneId.of("America/New_York"))
                .toInstant().toEpochMilli();
        expect(CalendarStore.stoppedRule(daily(3, null), dstFirst, "America/New_York", false,
                time("2026-04-01T00:00:00Z")).endsWith("UNTIL=20260309T130000Z"), "COUNT preserves local DST time");
        expect(CalendarStore.stoppedRule(daily(5, null), time("2026-01-01T09:00:00Z"), "UTC", false, null)
                .contains("COUNT=5"), "no stop preserves COUNT");
        var input = new CalendarStore.EventInput(" Test ", time("2026-01-01T09:00:00Z"),
                time("2026-01-01T10:00:00Z"), "UTC", false, "description", "room", List.of(5));
        expect(input.rrule == null && input.title.equals("Test"), "ordinary constructor preserved");
        EventCandidate series = new EventCandidate(1, 1, "Test", input.startMillis, input.endMillis,
                "UTC", "room", "description", 5, false).withRepeat(daily(2, null))
                .withRepeatState("{\"1767258000000\":{\"skipped\":true}}", time("2026-03-01T00:00:00Z"));
        var repeating = input.withRepeat(series);
        expect(repeating.rrule.endsWith("UNTIL=20260102T090000Z"), "withRepeat applies stopping rule");
        expect(repeating.repeatExceptions.equals(series.repeatExceptions)
                && repeating.repeatRuleJson.contains("daily"), "repeat metadata is retained for backup");
        expect(input.rrule == null && input.reminderMinutes.equals(Collections.singletonList(5)), "withRepeat is immutable");
        expect(repeating.withRepeat(series.withRepeat(null)).rrule == null, "removing repeat clears the existing rule");
        var constructor = CalendarStore.EventRecord.class.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var record = (CalendarStore.EventRecord) constructor.newInstance(1L, 2L, input.title, input.startMillis,
                input.endMillis, "UTC", false, input.description, input.location, input.reminderMinutes,
                repeating.rrule, "PT3600S", null, null, null, 1, "old-sync-id", null,
                repeating.repeatRuleJson, repeating.repeatExceptions, repeating.repeatStoppedAtMillis);
        var restored = input.withRepeat(record);
        expect(restored.rrule.equals(repeating.rrule) && restored.repeatExceptions.equals(series.repeatExceptions)
                && restored.repeatStoppedAtMillis.equals(series.repeatStoppedAtMillis), "provider record restore retains repeat state");
        var durationEnd = CalendarStore.class.getDeclaredMethod("durationEnd", long.class, String.class);
        durationEnd.setAccessible(true);
        expect((Long) durationEnd.invoke(null, 1000L, "PT3600S") == 3_601_000L, "timed duration restores end");
        expect((Long) durationEnd.invoke(null, 1000L, "P2D") == 172_801_000L, "all-day duration restores end");
        expect((Long) durationEnd.invoke(null, 1000L, "P1W") == 604_801_000L, "RFC week duration restores end");
        System.out.println("CalendarRepeatCheck: " + checks + " checks passed");
    }
}
