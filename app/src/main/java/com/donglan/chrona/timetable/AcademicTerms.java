package com.donglan.chrona.timetable;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Semester boundaries are confirmed by the user; regularity is a reversible display suggestion. */
public final class AcademicTerms {
    public static final String[] SEASONS = {"秋", "冬", "春", "夏"};
    public static final String[] MODES = {"秋冬", "春夏", "秋", "冬", "春", "夏"};
    private static final int SUGGESTED_TERM_WEEKS = 8;
    private static final int MIN_REGULAR_DATES = 3;
    private static final int MIN_REGULAR_SPAN_DAYS = 14;

    public static final class Plan {
        public final int year, mode;
        public final LocalDate start, split, end; // inclusive start, exclusive end
        public Plan(int year, int mode, LocalDate start, LocalDate split, LocalDate end) {
            if (year < 1900 || year > 9998 || mode < 0 || mode >= MODES.length)
                throw new IllegalArgumentException("请检查学年和学期");
            if (!start.isBefore(end) || ChronoUnit.DAYS.between(start, end) > 370)
                throw new IllegalArgumentException("学期日期范围需在一年内，且结束日期晚于开始日期");
            if (mode < 2 && (!split.isAfter(start) || !split.isBefore(end)))
                throw new IllegalArgumentException("第二学期开始日期需位于起止日期之间");
            this.year = year; this.mode = mode; this.start = start; this.split = split; this.end = end;
        }
        public int firstSeason() { return mode < 2 ? mode * 2 : mode - 2; }
        public int count() { return mode < 2 ? 2 : 1; }
        public String id(int index) { return year + "/" + (firstSeason() + index); }
        public String group() { return year + "/" + (firstSeason() / 2); }
        public String groupLabel() {
            return String.format(java.util.Locale.ROOT, "%02d–%02d %s", year % 100, (year + 1) % 100,
                    firstSeason() < 2 ? "秋冬" : "春夏");
        }
        public String label(int index) { return SEASONS[firstSeason() + index] + "学期"; }
        public LocalDate from(int index) { return index == 0 ? start : split; }
        public LocalDate until(int index) { return index + 1 == count() ? end : split; }
        public List<Timetable.Occurrence> entries(Timetable table, int index) {
            List<Timetable.Occurrence> selected = new ArrayList<>();
            for (Timetable.Occurrence entry : table.occurrences) {
                LocalDate date = entry.start.toLocalDate();
                if (!date.isBefore(from(index)) && date.isBefore(until(index))) selected.add(entry);
            }
            return selected;
        }
        public void validateCoverage(Timetable table) {
            for (Timetable.Occurrence entry : table.occurrences) {
                if (entry.openEnded) throw new IllegalArgumentException("请导出带学期截止日期的 ICS，当前文件含无限重复课程");
                if (entry.start.toLocalDate().isBefore(start) || !entry.start.toLocalDate().isBefore(end))
                    throw new IllegalArgumentException("日期范围需要覆盖文件内全部课程");
            }
        }
    }

    public static Plan suggest(Timetable table, String source) {
        LocalDate first = table.occurrences.stream().map(o -> o.start.toLocalDate()).min(LocalDate::compareTo).orElseThrow();
        LocalDate last = table.occurrences.stream().map(o -> o.start.toLocalDate()).max(LocalDate::compareTo).orElseThrow();
        String title = "";
        for (String line : source.replaceAll("\\r?\\n[ \\t]", "").split("\\r?\\n"))
            if (line.startsWith("X-WR-CALNAME:")) { title = line.substring(13); break; }
        int mode = first.getMonthValue() >= 8 ? 0 : 1;
        for (int i = 0; i < MODES.length; i++) if (title.contains(MODES[i])) { mode = i; break; }
        int year = mode == 0 || mode == 2 || mode == 3
                ? (first.getMonthValue() < 8 ? first.getYear() - 1 : first.getYear()) : first.getYear() - 1;
        Matcher namedYear = Pattern.compile("(20\\d{2})\\s*[-–]\\s*(20\\d{2})").matcher(title);
        if (namedYear.find() && Integer.parseInt(namedYear.group(2)) == Integer.parseInt(namedYear.group(1)) + 1)
            year = Integer.parseInt(namedYear.group(1));
        LocalDate start = first.minusDays(first.getDayOfWeek().getValue() - 1);
        LocalDate split = start.plusWeeks(SUGGESTED_TERM_WEEKS);
        LocalDate end = last.plusDays(1);
        // A short file can belong to just one season; the import form makes the suggestion editable.
        if (mode < 2 && !split.isBefore(end)) mode = mode == 0 ? 2 : 4;
        Plan plan = new Plan(year, mode, start, split, end);
        plan.validateCoverage(table);
        return plan;
    }

    public static final class View {
        public final Timetable regular;
        public final List<Timetable.Occurrence> all, special;
        public final int courseCount;
        public final long minutes;
        public View(List<Timetable.Occurrence> entries, ZoneId zone, Map<String, Boolean> overrides) {
            List<Timetable.Occurrence> ordered = new ArrayList<>(entries);
            ordered.sort(Comparator.comparing((Timetable.Occurrence o) -> o.start).thenComparing(o -> o.title));
            all = Collections.unmodifiableList(ordered);
            Map<String, Set<LocalDate>> dates = new HashMap<>();
            for (Timetable.Occurrence entry : all)
                dates.computeIfAbsent(slot(entry), unused -> new HashSet<>()).add(entry.start.toLocalDate());
            List<Timetable.Occurrence> grid = new ArrayList<>(), exceptions = new ArrayList<>();
            Set<String> courses = new HashSet<>();
            Set<String> counted = new HashSet<>();
            long duration = 0;
            for (Timetable.Occurrence entry : all) {
                Set<LocalDate> days = dates.get(slot(entry));
                boolean recurring = !entry.allDay && days.size() >= MIN_REGULAR_DATES
                        && ChronoUnit.DAYS.between(Collections.min(days), Collections.max(days)) >= MIN_REGULAR_SPAN_DAYS;
                boolean inGrid = overrides.getOrDefault(entry.key(), recurring);
                (inGrid ? grid : exceptions).add(entry);
                courses.add(entry.title);
                if (counted.add(entry.key()))
                    duration += Math.max(0, Duration.between(entry.start.atZone(zone), entry.end.atZone(zone)).toMinutes());
            }
            regular = new Timetable(grid, zone, grid.size());
            special = Collections.unmodifiableList(exceptions);
            courseCount = courses.size(); minutes = duration;
        }
        private static String slot(Timetable.Occurrence entry) {
            return entry.title.length() + ":" + entry.title + entry.location.length() + ":" + entry.location
                    + "/" + entry.start.getDayOfWeek() + "/" + entry.start.toLocalTime()
                    + "/" + Duration.between(entry.start, entry.end) + "/" + entry.allDay;
        }
    }

    private AcademicTerms() { }
}
