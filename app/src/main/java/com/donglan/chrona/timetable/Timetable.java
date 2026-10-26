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
    /** Normal short teaching breaks may be included in a continuous display block, never a lunch gap. */
    private static final int MAX_TEACHING_BREAK_MINUTES = 20;
    public final List<Block> blocks;
    public final List<Occurrence> occurrences;
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

        /** Stable across imports and day-split blocks; descriptions do not change identity. */
        public String key() {
            return title.length() + ":" + title + location.length() + ":" + location
                    + "/" + start + "/" + end + "/" + allDay;
        }
    }

    /** A multi-hour class is a single block; only midnight splits it into day segments. */
    public static final class Block {
        public final int day, startMinute, endMinute;
        public final String title, description;
        /** Shared location only; varying locations remain on the individual occurrences. */
        public String location;
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

        /** A catalogue position with no asserted duration, separate from imported occurrences. */
        private Block(String title, String location, int day, int minute) {
            this.title=title;this.location=location;this.day=day;startMinute=endMinute=minute;
            description="";allDay=false;
        }
        public static Block startingAt(String title,String location,int day,int minute) {
            if(day<0||day>6||minute<0||minute>=1440)throw new IllegalArgumentException("Invalid classroom start");
            return new Block(title,location,day,minute);
        }
        public boolean startOnly(){return !allDay&&startMinute==endMinute;}

        void add(Occurrence entry) {
            String key = entry.key() + "/" + entry.description + "/" + entry.recurrence;
            if (seen.add(key)) entries.add(entry);
            if (!location.equals(entry.location)) location = "";
        }

        boolean accepts(Occurrence entry) {
            for (Occurrence existing : entries)
                if (!existing.location.equals(entry.location)
                        && existing.start.isBefore(entry.end) && entry.start.isBefore(existing.end))
                    return false;
            return true;
        }

        public List<Occurrence> occurrences() { return Collections.unmodifiableList(entries); }
        public boolean openEnded() {
            for (Occurrence entry : entries) if (entry.openEnded) return true;
            return false;
        }
    }

    public Timetable(List<Occurrence> occurrences, ZoneId zone, int eventCount) {
        Map<String, Occurrence> unique = new LinkedHashMap<>();
        for (Occurrence entry : occurrences)
            unique.putIfAbsent(entry.key() + "/" + entry.description.length() + ":" + entry.description + "/" + entry.recurrence, entry);
        this.occurrences = Collections.unmodifiableList(new ArrayList<>(unique.values()));
        this.zone = zone;
        this.eventCount = eventCount;
        Map<List<Object>, List<Block>> groups = new LinkedHashMap<>();
        ArrayList<Block> sorted = new ArrayList<>();
        TreeSet<String> courses = new TreeSet<>();
        for (Occurrence entry : this.occurrences) {
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
                List<Object> key = java.util.Arrays.asList(entry.title,
                        entry.allDay, day, start, end);
                List<Block> candidates = groups.computeIfAbsent(key, ignored -> new ArrayList<>());
                Block block = null;
                for (Block candidate : candidates) if (candidate.accepts(entry)) {
                    block = candidate;
                    break;
                }
                if (block == null) {
                    if (sorted.size() >= MAX_BLOCKS)
                        throw new IllegalArgumentException("课程时段过多，请分别导入不同学期的课表");
                    block = new Block(entry, day, start, end);
                    candidates.add(block);
                    sorted.add(block);
                }
                block.add(entry);
                date = date.plusDays(1);
            }
        }
        sorted.sort(Comparator.comparingInt((Block b) -> b.day)
                .thenComparingInt(b -> b.startMinute).thenComparingInt(b -> b.endMinute)
                .thenComparing(b -> b.title));
        joinConsecutive(sorted);
        for (Block block : sorted) block.entries.sort(Comparator.comparing(o -> o.start));
        blocks = Collections.unmodifiableList(sorted);
        courseCount = courses.size();
    }

    private static void joinConsecutive(ArrayList<Block> blocks) {
        for (int i = 0; i < blocks.size(); i++) {
            Block left = blocks.get(i);
            if (left.allDay) continue;
            for (int j = i + 1; j < blocks.size();) {
                Block right = blocks.get(j);
                if (right.day != left.day || right.startMinute - left.endMinute > MAX_TEACHING_BREAK_MINUTES) break;
                if (!right.allDay && right.startMinute >= left.endMinute && left.title.equals(right.title)
                        && sameMeetings(left, right) && clearBreak(blocks, left, right)) {
                    Block joined = new Block(left.entries.get(0), left.day, left.startMinute, right.endMinute);
                    for (Occurrence occurrence : left.entries) joined.add(occurrence);
                    for (Occurrence occurrence : right.entries) joined.add(occurrence);
                    blocks.set(i, joined); blocks.remove(j); left = joined;
                } else j++;
            }
        }
    }

    /** Matching actual dates and per-date rooms prevent joining alternate-week or moving classes. */
    private static boolean sameMeetings(Block left, Block right) {
        Map<LocalDate, TreeSet<String>> a = meetings(left), b = meetings(right);
        return a != null && a.equals(b);
    }

    private static Map<LocalDate, TreeSet<String>> meetings(Block block) {
        Map<LocalDate, TreeSet<String>> dates = new LinkedHashMap<>();
        for (Occurrence entry : block.entries) {
            if (!entry.start.toLocalDate().equals(entry.end.toLocalDate())
                    || entry.start.getDayOfWeek().getValue() - 1 != block.day) return null;
            dates.computeIfAbsent(entry.start.toLocalDate(), ignored -> new TreeSet<>()).add(entry.location);
        }
        return dates;
    }

    private static boolean clearBreak(List<Block> blocks, Block left, Block right) {
        if(left.endMinute==right.startMinute)return true;
        java.util.Set<LocalDate> dates=meetings(left).keySet();
        for (Block other : blocks) if (other != left && other != right && !other.allDay && other.day == left.day
                && other.startMinute < right.startMinute && other.endMinute > left.endMinute)
            for(Occurrence entry:other.entries)for(LocalDate date:dates)
                if(entry.start.isBefore(date.atStartOfDay().plusMinutes(right.startMinute))
                        &&date.atStartOfDay().plusMinutes(left.endMinute).isBefore(entry.end))return false;
        return true;
    }

    public static String time(int minute) {
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }
}
