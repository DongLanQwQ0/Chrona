package com.donglan.chrona.timetable;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

public final class AcademicTermsCheck {
    static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static Timetable.Occurrence event(String name, LocalDate day, int minutes) {
        LocalDateTime start = day.atTime(8, 0);
        return new Timetable.Occurrence(name, "教室", "", start, start.plusMinutes(minutes), false, false, "");
    }
    public static void main(String[] args) throws Exception {
        LocalDate start = LocalDate.of(2026, 9, 14), split = LocalDate.of(2026, 11, 9);
        List<Timetable.Occurrence> events = new ArrayList<>();
        for (int i = 0; i < 16; i++) events.add(event("跨秋冬课程", start.plusWeeks(i), 95));
        for (int i = 0; i < 8; i++) events.add(event("冬季课程", split.plusWeeks(i).plusDays(1), 180));
        Timetable.Occurrence exception = event("跨秋冬课程", start.plusDays(6), 95);
        events.add(exception);
        Timetable source = new Timetable(events, ZONE, events.size());
        AcademicTerms.Plan plan = AcademicTerms.suggest(source, "X-WR-CALNAME:课表-2026-2027秋冬\n");
        check(plan.year == 2026 && plan.mode == 0 && plan.split.equals(split), "metadata and eight-week suggestion");
        AcademicTerms.View autumn = new AcademicTerms.View(plan.entries(source, 0), ZONE, Collections.emptyMap());
        AcademicTerms.View winter = new AcademicTerms.View(plan.entries(source, 1), ZONE, Collections.emptyMap());
        check(autumn.courseCount == 1 && winter.courseCount == 2, "winter-only class excluded from autumn");
        check(autumn.all.size() == 9 && winter.all.size() == 16, "boundary dates partition exactly once");
        check(autumn.regular.occurrences.size() == 8 && autumn.special.size() == 1, "isolated date stays outside grid");
        check(autumn.minutes == 9 * 95 && winter.minutes == 8 * (95 + 180), "actual duration totals include special dates");
        Map<String, Boolean> overrides = new HashMap<>();
        overrides.put(exception.key(), true);
        check(new AcademicTerms.View(plan.entries(source, 0), ZONE, overrides).special.isEmpty(), "manual promotion");
        overrides.put(events.get(0).key(), false);
        check(new AcademicTerms.View(plan.entries(source, 0), ZONE, overrides).special.size() == 1, "manual demotion");
        List<Timetable.Occurrence> weekends = Arrays.asList(event("周末课", start.plusDays(5), 60),
                event("周末课", start.plusDays(19), 60), event("周末课", start.plusDays(33), 60));
        check(new AcademicTerms.View(weekends, ZONE, Collections.emptyMap()).special.isEmpty(), "regular alternate-week weekend classes retained");
        AcademicTerms.Plan shifted = new AcademicTerms.Plan(2026, 0, start, split.plusDays(1), plan.end);
        check(shifted.entries(source, 0).size() == 10, "edited boundary moves actual occurrences");
        try { new AcademicTerms.Plan(2026, 0, start, start, plan.end); throw new AssertionError("invalid split accepted"); }
        catch (IllegalArgumentException expected) { }
        try { new AcademicTerms.Plan(2026, 2, start.plusDays(1), split, plan.end).validateCoverage(source);
            throw new AssertionError("missing first date accepted"); } catch (IllegalArgumentException expected) { }
        List<Timetable.Occurrence> springEvents = Arrays.asList(event("春夏课", LocalDate.of(2027, 2, 22), 60),
                event("春夏课", LocalDate.of(2027, 6, 1), 60));
        AcademicTerms.Plan spring = AcademicTerms.suggest(new Timetable(springEvents, ZONE, 2),
                "X-WR-CALNAME:课表-2026-2027春夏\n");
        check(spring.year == 2026 && spring.mode == 1 && spring.id(1).equals("2026/3"), "spring/summer academic year");
        check(new Timetable(Arrays.asList(exception, exception), ZONE, 2).occurrences.size() == 1, "duplicate instance removed");
        System.out.println("Academic term checks passed: partition, boundaries, stats, special/regular overrides, weekend recurrence, spring/summer and duplicates");
        if (args.length > 0) {
            String ics = Files.readString(Path.of(args[0]));
            Timetable actual = TimetableParser.parse(ics, ZONE);
            AcademicTerms.Plan actualPlan = AcademicTerms.suggest(actual, ics);
            check(actual.courseCount == 11 && actual.occurrences.size() == 255, "real export parsed without loss");
            check(actualPlan.split.equals(split), "real export winter boundary");
            long count = 0;
            for (int i = 0; i < actualPlan.count(); i++) {
                AcademicTerms.View term = new AcademicTerms.View(actualPlan.entries(actual, i), ZONE, Collections.emptyMap());
                count += term.all.size();
                check(term.courseCount == (i == 0 ? 10 : 11), "real export course partition");
                System.out.println("Real ICS " + actualPlan.label(i) + ": courses=" + term.courseCount
                        + ", occurrences=" + term.all.size() + ", special=" + term.special.size()
                        + ", gridBlocks=" + term.regular.blocks.size() + ", minutes=" + term.minutes);
            }
            check(count == 255, "all real events partitioned exactly once");
        }
    }
}
