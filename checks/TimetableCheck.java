package com.donglan.chrona.timetable;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

public class TimetableCheck {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static String calendar(String... events) {
        return "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Chrona Test//CN\r\n"
                + String.join("", events) + "END:VCALENDAR\r\n";
    }
    private static String event(String id, String extra) {
        return "BEGIN:VEVENT\r\nUID:" + id + "\r\nDTSTAMP:20260901T000000Z\r\n" + extra + "END:VEVENT\r\n";
    }
    private static String course(String start, String end) {
        return "SUMMARY:微积分\r\nLOCATION:东2-204\r\nDTSTART:" + start + "\r\nDTEND:" + end + "\r\n";
    }
    private static Timetable parse(String... events) throws IOException {
        return TimetableParser.parse(calendar(events), ZONE);
    }

    public void repeatedDatesCollapseAndLongClassStaysWhole() throws Exception {
        Timetable table = parse(event("a", course("20261005T080000", "20261005T103500")),
                event("b", course("20261012T080000", "20261012T103500")));
        assertEquals(1, table.blocks.size());
        Timetable.Block block = table.blocks.get(0);
        assertEquals(0, block.day);
        assertEquals(480, block.startMinute);
        assertEquals(635, block.endMinute);
        assertEquals(2, block.occurrences().size());
        assertEquals(1, new TimetableLayout(table.blocks).placements.size());
    }

    public void foldingAndEscapingPreserveChineseDetails() throws Exception {
        Timetable table = parse(event("a", "SUMMARY:数字音视频基础与\r\n 应用\r\n"
                + "DESCRIPTION:教师\\, 张老师\\n课堂\\;讨论\\\\实践\r\n"
                + "DTSTART:20261005T080000\r\nDURATION:PT95M\r\n"));
        assertEquals("数字音视频基础与应用", table.blocks.get(0).title);
        assertEquals("教师, 张老师\n课堂;讨论\\实践", table.blocks.get(0).description);
        assertEquals(575, table.blocks.get(0).endMinute);
    }

    public void weeklyExceptionsMovedAndCancelledInstances() throws Exception {
        Timetable table = parse(event("a", course("20261005T080000", "20261005T093500")
                + "RRULE:FREQ=WEEKLY;COUNT=5\r\nEXDATE:20261012T080000\r\n"),
                event("a", "RECURRENCE-ID:20261019T080000\r\n" + course("20261020T100000", "20261020T113500")),
                event("a", "RECURRENCE-ID:20261026T080000\r\nSTATUS:CANCELLED\r\n"));
        assertEquals(2, table.blocks.size());
        assertEquals(2, table.blocks.get(0).occurrences().size());
        assertEquals(1, table.blocks.get(1).day);
        assertEquals(600, table.blocks.get(1).startMinute);
    }

    public void weekdaysAndOddWeeksAreExpandedBeforeGrouping() throws Exception {
        Timetable table = parse(event("a", course("20261005T080000", "20261005T093500")
                + "RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO,WE;COUNT=6\r\n"));
        assertEquals(2, table.blocks.size());
        assertEquals(3, table.blocks.get(0).occurrences().size());
        assertEquals(3, table.blocks.get(1).occurrences().size());
        assertEquals(19, table.blocks.get(0).occurrences().get(1).start.getDayOfMonth());
    }

    public void rdateIncludesDtstartAndExdateWins() throws Exception {
        Timetable table = parse(event("a", course("20261005T080000", "20261005T093500")
                + "RDATE:20261012T080000,20261019T080000\r\nEXDATE:20261019T080000\r\n"));
        assertEquals(2, table.blocks.get(0).occurrences().size());
    }

    public void rdatePeriodRetainsOwnDuration() throws Exception {
        Timetable table = parse(event("a", course("20261005T000000Z", "20261005T010000Z")
                + "RDATE;VALUE=PERIOD:20261006T020000Z/20261006T043000Z\r\n"));
        assertEquals(2, table.blocks.size());
        assertEquals(600, table.blocks.get(1).startMinute);
        assertEquals(750, table.blocks.get(1).endMinute);
    }

    public void utcNamedAndFloatingTimesAgree() throws Exception {
        Timetable table = parse(event("utc", course("20261005T000000Z", "20261005T010000Z")),
                event("floating", course("20261012T080000", "20261012T090000")),
                event("tz", "SUMMARY:微积分\r\nLOCATION:东2-204\r\n"
                        + "DTSTART;TZID=Asia/Shanghai:20261019T080000\r\nDTEND;TZID=Asia/Shanghai:20261019T090000\r\n"));
        assertEquals(1, table.blocks.size());
        assertEquals(3, table.blocks.get(0).occurrences().size());
        assertEquals(480, table.blocks.get(0).startMinute);
    }

    public void daylightSavingChangesLocalDisplayWithoutLosingInstances() throws Exception {
        Timetable table = parse(event("dst", "SUMMARY:课程\r\n"
                + "DTSTART;TZID=America/New_York:20261026T080000\r\n"
                + "DTEND;TZID=America/New_York:20261026T090000\r\nRRULE:FREQ=WEEKLY;COUNT=3\r\n"));
        assertEquals(2, table.blocks.size());
        assertEquals(1200, table.blocks.get(0).startMinute);
        assertEquals(1260, table.blocks.get(1).startMinute);
        assertEquals(2, table.blocks.get(1).occurrences().size());
    }

    public void allDayDoesNotOccupyTheTimeGridAndEndIsExclusive() throws Exception {
        Timetable table = parse(event("day", "SUMMARY:实习\r\nDTSTART;VALUE=DATE:20261005\r\nDTEND;VALUE=DATE:20261007\r\n"));
        assertEquals(2, table.blocks.size());
        assertTrue(table.blocks.get(0).allDay);
        TimetableLayout layout = new TimetableLayout(table.blocks);
        assertEquals(0, layout.minutes.length);
        assertEquals(TimetableLayout.ALL_DAY_HEIGHT, layout.allDayHeight, .01);
    }

    public void sundayOvernightSplitsAcrossWeekBoundary() throws Exception {
        Timetable table = parse(event("night", course("20261004T233000", "20261005T003000")));
        assertEquals(2, table.blocks.size());
        assertEquals(0, table.blocks.get(0).day);
        assertEquals(0, table.blocks.get(0).startMinute);
        assertEquals(30, table.blocks.get(0).endMinute);
        assertEquals(6, table.blocks.get(1).day);
        assertEquals(1440, table.blocks.get(1).endMinute);
    }

    public void newestRevisionReplacesOlderUid() throws Exception {
        Timetable table = parse(event("a", course("20261005T080000", "20261005T090000") + "SEQUENCE:1\r\n"),
                event("a", course("20261006T100000", "20261006T110000") + "SEQUENCE:2\r\n"));
        assertEquals(1, table.blocks.size());
        assertEquals(1, table.blocks.get(0).day);
    }

    public void unboundedPlainWeeklyRemainsMarkedAsUnbounded() throws Exception {
        Timetable table = parse(event("a", course("20261005T080000", "20261005T090000")
                + "RRULE:FREQ=WEEKLY;BYDAY=MO,TH\r\n"));
        assertEquals(2, table.blocks.size());
        assertTrue(table.blocks.get(0).openEnded());
        assertTrue(table.blocks.get(1).openEnded());
    }

    public void invalidFileCannotBecomeAnEmptySuccessfulImport() throws Exception {
        for (String invalid : Arrays.asList("not a calendar", "BEGIN:VCALENDAR\nBEGIN:VEVENT\n",
                calendar(event("a", "SUMMARY:缺时间\r\n")),
                calendar(event("a", "DTSTART:20261005T080000\r\n")),
                calendar(event("a", course("20261005T100000", "20261005T090000"))),
                calendar(event("a", "DTSTART:bad\r\nDURATION:PT1H\r\n")),
                calendar(event("a", course("20261005T100000", "20261005T110000")
                        + "RRULE:FREQ=SECONDLY;COUNT=100000\r\n")),
                calendar(event("a", "DTSTART;TZID=Unknown/Zone:20261005T080000\r\nDURATION:PT1H\r\n")))) {
            try { TimetableParser.parse(invalid, ZONE); fail("accepted invalid file: " + invalid); }
            catch (IOException expected) { assertFalse(expected.getMessage().isEmpty()); }
        }
    }

    public void geometrySeparatesConflictsAndPreservesShortClassTouchTargets() {
        List<Timetable.Occurrence> events = Arrays.asList(occurrence("long", 480, 650),
                occurrence("inside", 490, 491), occurrence("after", 650, 651), occurrence("late", 1100, 1200));
        Timetable table = new Timetable(events, ZONE, 4);
        TimetableLayout layout = new TimetableLayout(table.blocks);
        assertEquals(2, layout.dayColumns[0]);
        for (TimetableLayout.Placement p : layout.placements) {
            assertTrue(p.height >= TimetableLayout.MIN_BLOCK_HEIGHT);
            for (TimetableLayout.Placement q : layout.placements) if (p != q && p.column == q.column)
                assertTrue(p.top + p.height <= q.top || q.top + q.height <= p.top);
        }
        assertTrue(layout.y(1100) - layout.y(651) <= 26);
    }

    public void varyingNotesKeepOneCourseBlockAndAllNotes() throws Exception {
        Timetable table = parse(event("a", course("20261005T080000", "20261005T103500") + "DESCRIPTION:第一周实验\r\n"),
                event("b", course("20261012T080000", "20261012T103500") + "DESCRIPTION:第二周讨论\r\n"));
        assertEquals(1, table.blocks.size());
        assertEquals(2, table.blocks.get(0).occurrences().size());
        assertEquals("第二周讨论", table.blocks.get(0).occurrences().get(1).description);
    }

    private static Timetable.Occurrence occurrence(String title, int start, int end) {
        LocalDateTime day = LocalDateTime.of(2026, 10, 5, 0, 0);
        return new Timetable.Occurrence(title, "", "", day.plusMinutes(start), day.plusMinutes(end), false, false, "");
    }

    public void alternatingLocationsShareOneBlockWithoutLosingDates() throws Exception {
        Timetable table = parse(event("odd", course("20261005T080000", "20261005T093500")
                        + "RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=3\r\n"),
                event("even", course("20261012T080000", "20261012T093500")
                        .replace("东2-204", "东6-328") + "RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=3\r\n"));
        assertEquals(1, table.blocks.size());
        assertEquals(6, table.occurrences.size());
        assertEquals(6, table.blocks.get(0).occurrences().size());
        assertEquals("", table.blocks.get(0).location);
        assertEquals("东2-204", table.blocks.get(0).occurrences().get(0).location);
        assertEquals("东6-328", table.blocks.get(0).occurrences().get(1).location);
        assertEquals(12, table.blocks.get(0).occurrences().get(1).start.getDayOfMonth());
        assertEquals(1, table.courseCount);
        assertEquals(1, new TimetableLayout(table.blocks).dayColumns[0]);
        AcademicTerms.View view = new AcademicTerms.View(table.occurrences, ZONE,
                java.util.Collections.emptyMap());
        assertEquals(1, view.regular.blocks.size());
        assertEquals(0, view.special.size());
        assertEquals(1, view.courseCount);
        assertEquals(570L, view.minutes);
    }

    public void sameTitleAtOtherTimesOrWeekdaysStillHasSeparateBlocks() throws Exception {
        Timetable table = parse(event("a", course("20261005T080000", "20261005T093500")),
                event("b", course("20261005T100000", "20261005T113500")),
                event("c", course("20261006T080000", "20261006T093500")),
                event("d", course("20261005T080000", "20261005T093500").replace("微积分", "线性代数")));
        assertEquals(4, table.blocks.size());
        assertEquals(2, new TimetableLayout(table.blocks).dayColumns[0]);
    }

    public void simultaneousDifferentLocationsRemainDistinctOccurrences() throws Exception {
        Timetable table = parse(event("a", course("20261005T080000", "20261005T093500")),
                event("b", course("20261005T080000", "20261005T093500").replace("东2-204", "东6-328")));
        assertEquals(2, table.blocks.size());
        assertEquals(2, new TimetableLayout(table.blocks).dayColumns[0]);
        assertEquals(2, table.occurrences.size());
        assertEquals(1, table.blocks.get(0).occurrences().size());
        assertFalse(table.occurrences.get(0).key().equals(table.occurrences.get(1).key()));
    }

    public void shortAlternatingSeriesRetainsExistingAutomaticClassification() throws Exception {
        Timetable table = parse(event("odd", course("20261005T080000", "20261005T093500")
                        + "RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=2\r\n"),
                event("even", course("20261012T080000", "20261012T093500")
                        .replace("东2-204", "东6-328") + "RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=2\r\n"));
        assertEquals(1, table.blocks.size());
        AcademicTerms.View view = new AcademicTerms.View(table.occurrences, ZONE,
                java.util.Collections.emptyMap());
        assertEquals(0, view.regular.blocks.size());
        assertEquals(4, view.special.size());
        assertEquals(380L, view.minutes);
    }

    private static void assertTrue(boolean value) { if (!value) throw new AssertionError("Expected true"); }
    private static void assertFalse(boolean value) { assertTrue(!value); }
    private static void fail(String message) { throw new AssertionError(message); }
    private static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) throw new AssertionError(expected + " != " + actual);
    }
    private static void assertEquals(double expected, double actual, double tolerance) {
        if (Math.abs(expected - actual) > tolerance) throw new AssertionError(expected + " != " + actual);
    }
    public static void main(String[] args) throws Exception {
        TimetableCheck check = new TimetableCheck();
        int count = 0;
        for (java.lang.reflect.Method test : TimetableCheck.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(test.getModifiers()) || test.getParameterCount() != 0) continue;
            try { test.invoke(check); }
            catch (java.lang.reflect.InvocationTargetException failure) {
                throw new AssertionError(test.getName(), failure.getCause());
            }
            count++;
        }
        System.out.println("Timetable checks passed: " + count + " scenarios");
    }
}
