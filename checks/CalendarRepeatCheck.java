package com.donglan.chrona.calendar;

import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.RepeatRule;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import org.json.JSONObject;
import com.donglan.chrona.data.RepeatOccurrences;

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
    private static JSONObject fields(Object... pairs) throws Exception {
        JSONObject fields = new JSONObject();
        for (int i = 0; i < pairs.length; i += 2) fields.put((String) pairs[i], pairs[i + 1]);
        return fields;
    }
    private static CalendarStore.EventInput input(EventCandidate series) {
        return new CalendarStore.EventInput(series.title, series.calendarStart(), series.calendarEnd(),
                series.timeZoneId, series.allDay, series.description, series.location,
                List.of(5, 12)).withRepeat(series);
    }
    private static ArrayList<?> operations(EventCandidate series, Long eventId) throws Exception {
        var constructor = CalendarStore.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        Object calendar = constructor.newInstance();
        var append = CalendarStore.class.getDeclaredMethod("appendExceptions", ArrayList.class,
                long.class, CalendarStore.EventInput.class, Long.class);
        append.setAccessible(true);
        ArrayList<Object> operations = new ArrayList<>();
        append.invoke(calendar, operations, 7L, input(series), eventId);
        return operations;
    }
    @SuppressWarnings("unchecked")
    private static Map<String, Object> values(Object operation) throws Exception {
        var field = operation.getClass().getDeclaredField("values");
        field.setAccessible(true);
        return (Map<String, Object>) field.get(operation);
    }
    /** Execute the exact production batch builder with map-backed Android value/operation transports. */
    private static void providerOperations() throws Exception {
        long original = time("2026-01-02T09:00:00Z"), moved = time("2026-02-02T13:00:00Z");
        EventCandidate base = new EventCandidate(1, 1, "Template", time("2026-01-01T09:00:00Z"),
                time("2026-01-01T10:00:00Z"), "UTC", "Room A", "Description", 5, false)
                .withRepeat(daily(3, null));
        EventCandidate location = RepeatOccurrences.edit(base, original, fields("location", "Room B"));
        var locationOps = operations(location, null);
        expect(locationOps.size() == 3, "location-only makes one event exception and inherited alerts");
        var row = values(locationOps.get(0));
        expect(row.get("DTSTART").equals(original) && row.get("EVENT_LOCATION").equals("Room B"),
                "same-time exception contains changed location");
        expect(row.get("ORIGINAL_INSTANCE_TIME").equals(original)
                && row.get("ORIGINAL_ALL_DAY").equals(0) && row.get("ORIGINAL_ID").equals("back:0"),
                "original identity and new master back reference survive");
        expect(row.get("RRULE") == null && row.get("DURATION") == null && row.get("_SYNC_ID") == null,
                "exception has no master recurrence state");
        expect(values(locationOps.get(1)).get("MINUTES").equals(5)
                && values(locationOps.get(2)).get("MINUTES").equals(12), "unspecified alert field inherits all provider alerts");
        var titleOps = operations(RepeatOccurrences.edit(base, original, fields("title", "Only this title")), 99L);
        expect(values(titleOps.get(0)).get("TITLE").equals("Only this title"), "title-only generates an exception");
        expect(values(titleOps.get(0)).get("ORIGINAL_ID").equals(99L), "existing master uses its provider ID");

        EventCandidate edited = RepeatOccurrences.edit(base, original, fields("title", "Workshop", "start", moved,
                "end", moved + 9_000_000L, "time_zone_id", "Asia/Shanghai", "description", JSONObject.NULL,
                "location", JSONObject.NULL, "reminder_minutes_before", 35));
        var editedOps = operations(edited, 99L);
        row = values(editedOps.get(0));
        EventCandidate local = RepeatOccurrences.at(edited, original);
        expect(row.get("DTSTART").equals(local.calendarStart()) && row.get("DTEND").equals(local.calendarEnd()),
                "provider and local projection share independently edited start/end");
        expect(row.get("TITLE").equals(local.title) && row.get("EVENT_TIMEZONE").equals(local.timeZoneId),
                "title and time zone follow the occurrence");
        expect(row.containsKey("DESCRIPTION") && row.get("DESCRIPTION") == null
                && row.containsKey("EVENT_LOCATION") && row.get("EVENT_LOCATION") == null,
                "explicit null removes description and location");
        expect(editedOps.size() == 2 && values(editedOps.get(1)).get("MINUTES").equals(35),
                "one occurrence reminder replaces inherited alerts");
        expect(operations(RepeatOccurrences.edit(edited, original, fields("reminder_minutes_before", JSONObject.NULL)), 99L)
                .size() == 1, "explicit null removes all exception alerts");

        EventCandidate allDay = RepeatOccurrences.edit(base, original, fields("start", time("2026-02-02T00:00:00Z"),
                "end", time("2026-02-04T00:00:00Z"), "all_day", true, "time_zone_id", "UTC"));
        row = values(operations(allDay, 99L).get(0));
        expect(row.get("ALL_DAY").equals(1) && row.get("EVENT_TIMEZONE").equals("UTC")
                && row.get("ORIGINAL_ALL_DAY").equals(0), "edited all-day and original timed identity are independent");

        JSONObject legacy = fields("start", moved, "completed", time("2026-01-02T10:00:00Z"));
        EventCandidate oldMove = base.withRepeatState(new JSONObject().put(Long.toString(original), legacy).toString(), null);
        EventCandidate completed = RepeatOccurrences.edit(oldMove, original, fields("location", "Completed room"));
        var completedOps = operations(completed, 99L);
        row = values(completedOps.get(0));
        expect(completedOps.size() == 1 && row.get("STATUS").equals(1) && row.get("DTSTART").equals(moved),
                "legacy move and completion remain visible without alerts");
        expect(row.get("EVENT_LOCATION").equals("Completed room"), "completed occurrence keeps its new fields");
        JSONObject changes = RepeatOccurrences.exceptions(completed);
        changes.getJSONObject(Long.toString(original)).put("skipped", true);
        var skippedOps = operations(completed.withRepeatState(changes.toString(), null), 99L);
        expect(skippedOps.size() == 1 && values(skippedOps.get(0)).get("STATUS").equals(2),
                "skip remains canceled without alerts");
        var stoppedOps = operations(edited.withRepeatState(edited.repeatExceptions, time("2026-01-03T00:00:00Z")), 99L);
        expect(stoppedOps.size() == 1 && values(stoppedOps.get(0)).get("STATUS").equals(2),
                "stop cancels the final field-edited future start");
        EventCandidate movedEarlier = RepeatOccurrences.edit(base, original, fields("start", time("2026-01-01T07:00:00Z"),
                "end", time("2026-01-01T08:00:00Z")));
        expect(values(operations(movedEarlier.withRepeatState(movedEarlier.repeatExceptions,
                time("2026-01-01T08:30:00Z")), 99L).get(0)).get("STATUS").equals(1),
                "final start before stop stays visible even when original is after stop");
        expect(base.repeatExceptions.equals("{}") && base.location.equals("Room A"), "provider projection does not mutate its source");
        System.out.println("CalendarRepeatCheck provider batch: " + checks + " checks passed");
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("operations")) { providerOperations(); return; }
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
