package com.donglan.chrona.timetable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Imported course occurrences projected onto a Monday-to-Sunday overview. */
public final class Timetable {
    public static final int MAX_BLOCKS = 512;
    public final List<Block> blocks;
    public final ZoneId zone;
    public final int eventCount;
    public final int courseCount;

    public static final class Occurrence {
        public final String title, location, description, recurrence;
        public final LocalDateTime start, end;
        public final boolean allDay, openEnded;

        public Occurrence(String title, String location, String description,
                LocalDateTime start, LocalDateTime end, boolean allDay,
                boolean openEnded, String recurrence) {
            this.title = title;
            this.location = location;
            this.description = description;
            this.start = start;
            this.end = end;
            this.allDay = allDay;
            this.openEnded = openEnded;
            this.recurrence = recurrence;
        }
    }

    /** A multi-hour class is a single block; only midnight splits it into day segments. */
    public static final class Block {
        public final int day, startMinute, endMinute;
        public final String title, location, description;
        public final boolean allDay;
        private final List<Occurrence> entries = new ArrayList<>();
        private final TreeSet<String> seen = new TreeSet<>();

        Block(Occurrence entry, int day, int startMinute, int endMinute) {
            this.day = day;
            this.startMinute = startMinute;
            this.endMinute = endMinute;
            title = entry.title;
            location = entry.location;
            description = entry.description;
            allDay = entry.allDay;
        }

        void add(Occurrence entry) {
            String key = entry.start + "/" + entry.end + "/" + entry.description + "/" + entry.recurrence;
            if (seen.add(key)) entries.add(entry);
        }

        public List<Occurrence> occurrences() { return Collections.unmodifiableList(entries); }
        public boolean openEnded() {
            for (Occurrence entry : entries) if (entry.openEnded) return true;
            return false;
        }
    }

    public Timetable(List<Occurrence> occurrences, ZoneId zone, int eventCount) {
        this.zone = zone;
        this.eventCount = eventCount;
        Map<List<Object>, Block> groups = new LinkedHashMap<>();
        TreeSet<String> courses = new TreeSet<>();
        for (Occurrence entry : occurrences) {
            courses.add(entry.title);
            LocalDate date = entry.start.toLocalDate();
            while (date.atStartOfDay().isBefore(entry.end)) {
                LocalDateTime from = date.equals(entry.start.toLocalDate())
                        ? entry.start : date.atStartOfDay();
                LocalDateTime until = entry.end.isBefore(date.plusDays(1).atStartOfDay())
                        ? entry.end : date.plusDays(1).atStartOfDay();
                int start = from.toLocalTime().toSecondOfDay() / 60;
                int end = until.toLocalDate().isAfter(date) ? 1440
                        : (until.toLocalTime().toSecondOfDay() + 59) / 60;
                int day = date.getDayOfWeek().getValue() - 1;
                List<Object> key = java.util.Arrays.asList(entry.title, entry.location,
                        entry.allDay, day, start, end);
                Block block = groups.get(key);
                if (block == null) {
                    if (groups.size() >= MAX_BLOCKS)
                        throw new IllegalArgumentException("课程时段过多，请分别导入不同学期的课表");
                    block = new Block(entry, day, start, end);
                    groups.put(key, block);
                }
                block.add(entry);
                date = date.plusDays(1);
            }
        }
        ArrayList<Block> sorted = new ArrayList<>(groups.values());
        sorted.sort(Comparator.comparingInt((Block b) -> b.day)
                .thenComparingInt(b -> b.startMinute).thenComparingInt(b -> b.endMinute)
                .thenComparing(b -> b.title));
        for (Block block : sorted) block.entries.sort(Comparator.comparing(o -> o.start));
        blocks = Collections.unmodifiableList(sorted);
        courseCount = courses.size();
    }

    public static String time(int minute) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }
}
