package com.donglan.chrona;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Browser projection of the same occurrences, clipping and overlap lanes as the phone calendar. */
final class LanCalendarData {
    static List<AgendaEntry> matching(List<AgendaEntry> entries, String search) {
        if (search.length() > 256) throw new IllegalArgumentException();
        String needle = search.trim().toLowerCase(Locale.ROOT);
        List<AgendaEntry> result = new ArrayList<>();
        for (AgendaEntry entry : entries)
            if (needle.isEmpty() || (entry.title + " " + entry.location + " " + entry.description + " " + entry.source)
                    .toLowerCase(Locale.ROOT).contains(needle)) result.add(entry);
        return result;
    }

    static JSONObject project(List<AgendaEntry> input, LocalDate from, LocalDate until, ZoneId zone,
            String mode, String search, boolean truncated, boolean unavailable) throws org.json.JSONException {
        if (!List.of("year", "month", "week", "day", "agenda").contains(mode)) throw new IllegalArgumentException();
        List<AgendaEntry> entries = matching(input, search);
        JSONArray items = new JSONArray();
        IdentityHashMap<AgendaEntry, Integer> indexes = new IdentityHashMap<>();
        for (AgendaEntry item : entries) {
            indexes.put(item, items.length());
            items.put(new JSONObject().put("id", Long.toString(item.id)).put("taskId", item.taskId)
                    .put("key", item.key()).put("title", item.title).put("start", item.start).put("end", item.end).put("finish", item.finish())
                    .put("allDay", item.allDay).put("location", item.location).put("description", item.description)
                    .put("source", item.source).put("category", item.category).put("system", item.system)
                    .put("needsConfirmation", !item.confirmed)
                    .put("calendarColor", item.calendarColor == null ? JSONObject.NULL : String.format(Locale.ROOT, "#%06x", item.calendarColor & 0xffffff))
                    .put("occurrenceAt", item.occurrenceAtMillis == null ? JSONObject.NULL : item.occurrenceAtMillis)
                    .put("due", item.dueAtMillis == null ? JSONObject.NULL : item.dueAtMillis)
                    .put("overdueAt", item.overdueAtMillis == null ? JSONObject.NULL : item.overdueAtMillis));
        }
        JSONObject result = new JSONObject().put("items", items).put("from", from.toString()).put("until", until.toString())
                .put("truncated", truncated).put("systemUnavailable", unavailable).put("zone", zone.getId());
        if (mode.equals("week") || mode.equals("day")) {
            int count = mode.equals("week") ? 7 : 1;
            if (!until.equals(from.plusDays(count))) throw new IllegalArgumentException();
            boolean compress = count == 7 && !truncated && !unavailable;
            JSONArray days = new JSONArray();
            for (int index = 0; index < count; index++) {
                LocalDate day = from.plusDays(index);
                JSONArray blocks = new JSONArray(), allDay = new JSONArray();
                for (CalendarTimelineLayout.Block block : CalendarTimelineLayout.day(entries, day, zone)) {
                    if (WeekTimelineScale.occupiesQuietHours(block.startMinute, block.endMinute)) compress = false;
                    blocks.put(new JSONObject().put("index", indexes.get(block.entry)).put("startMinute", block.startMinute)
                            .put("endMinute", block.endMinute).put("lane", block.lane).put("lanes", block.lanes));
                }
                long begin = day.atStartOfDay(zone).toInstant().toEpochMilli();
                long end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
                for (AgendaEntry entry : entries)
                    if (entry.allDay && entry.overlaps(begin, end)) allDay.put(indexes.get(entry));
                days.put(new JSONObject().put("date", day.toString()).put("blocks", blocks).put("allDay", allDay));
            }
            result.put("timeline", new JSONObject().put("days", days).put("compressMorning", compress)
                    .put("hourHeight", 72).put("minimumBandHeight", 32));
        }
        return result;
    }
    private LanCalendarData() { }
}
