package com.donglan.chrona.zju;

import com.donglan.chrona.timetable.Timetable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only projection of the account cache; school term names remain unchanged. */
public final class ZjuTimetableView {
    public static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    public static Map<String, Timetable> terms(List<ZjuSnapshot.Lesson> lessons) {
        Map<String, List<Timetable.Occurrence>> grouped = new LinkedHashMap<>();
        for (ZjuSnapshot.Lesson lesson : lessons) {
            grouped.computeIfAbsent(lesson.term, ignored -> new ArrayList<>()).add(
                    new Timetable.Occurrence(lesson.title, lesson.location, lesson.notes,
                            Instant.ofEpochMilli(lesson.start).atZone(ZONE).toLocalDateTime(),
                            Instant.ofEpochMilli(lesson.end).atZone(ZONE).toLocalDateTime(), false, false, ""));
        }
        Map<String, Timetable> result = new LinkedHashMap<>();
        grouped.forEach((term, entries) -> result.put(term, new Timetable(entries, ZONE, entries.size())));
        return result;
    }
    public static String current(Map<String, Timetable> terms, LocalDate today) {
        String selected = "";
        long distance = Long.MAX_VALUE;
        for (Map.Entry<String, Timetable> term : terms.entrySet()) {
            LocalDate first = term.getValue().occurrences.stream().map(e -> e.start.toLocalDate()).min(LocalDate::compareTo).orElse(today);
            LocalDate last = term.getValue().occurrences.stream().map(e -> e.end.toLocalDate()).max(LocalDate::compareTo).orElse(today);
            long gap = today.isBefore(first) ? first.toEpochDay() - today.toEpochDay()
                    : today.isAfter(last) ? today.toEpochDay() - last.toEpochDay() : 0;
            if (gap < distance) { selected = term.getKey(); distance = gap; }
        }
        return selected;
    }
    private ZjuTimetableView() { }
}
