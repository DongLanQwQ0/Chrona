package com.donglan.chrona.zju;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Synthetic, offline examples; no credentials or personal timetable data. */
public final class ZjuCalendarCheck {
    private static int checks;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    private static void require(boolean condition, String label) {
        checks++;
        if (!condition) throw new AssertionError(label);
    }

    private static JSONObject row(int day, int parity) {
        return new JSONObject().put("kcb", "CourseA<br>秋冬{第1-8周|2节/周}<br>Teacher<br>RoomA考试2027年01月10日(08:00-10:00)")
                .put("djj", "1").put("skcd", "2").put("xqj", day).put("dsz", parity);
    }

    private static LocalDate date(ZjuSnapshot.Lesson lesson) {
        return Instant.ofEpochMilli(lesson.start).atZone(ZONE).toLocalDate();
    }

    public static void main(String[] args) throws Exception {
        require(ZjuAcademicCalendar.actualDate(LocalDate.of(2026, 10, 6)).equals(LocalDate.of(2026, 9, 20)), "Oct6 move");
        require(ZjuAcademicCalendar.actualDate(LocalDate.of(2026, 10, 7)).equals(LocalDate.of(2026, 10, 10)), "Oct7 move");
        require(ZjuAcademicCalendar.actualDate(LocalDate.of(2026, 10, 2)).equals(LocalDate.of(2026, 10, 17)), "Oct2 move");
        require(ZjuAcademicCalendar.actualDate(LocalDate.of(2026, 12, 31)).equals(LocalDate.of(2027, 1, 4)), "student day move");
        require(ZjuAcademicCalendar.actualDate(LocalDate.of(2026, 9, 25)) == null, "mid autumn");
        require(ZjuAcademicCalendar.actualDate(LocalDate.of(2026, 10, 24)) == null, "sports day");

        List<String> warnings = new ArrayList<>();
        List<ZjuSnapshot.Lesson> lessons = ZjuAcademicCalendar.expand("2026-2027", "秋", new JSONArray().put(row(2, 2)), warnings);
        require(lessons.size() == 8, "eight Tuesdays including makeup");
        require(lessons.stream().anyMatch(x -> date(x).equals(LocalDate.of(2026, 9, 20))), "makeup actual date");
        require(lessons.stream().allMatch(x -> x.location.equals("RoomA")), "exam is not lecture location");
        require(Instant.ofEpochMilli(lessons.get(0).end).atZone(ZONE).toLocalTime().equals(LocalTime.of(9, 35)), "last period end");

        lessons = ZjuAcademicCalendar.expand("2026-2027", "秋", new JSONArray().put(row(1, 0)), warnings);
        require(lessons.size() == 4, "odd weeks");
        lessons = ZjuAcademicCalendar.expand("2026-2027", "秋", new JSONArray().put(row(1, 1)), warnings);
        require(lessons.size() == 3, "even weeks exclude national holiday");
        lessons = ZjuAcademicCalendar.expand("2026-2027", "冬", new JSONArray().put(row(4, 2)), warnings);
        require(lessons.stream().anyMatch(x -> date(x).equals(LocalDate.of(2027, 1, 4))), "winter makeup");

        lessons = ZjuAcademicCalendar.expand("2027-2028", "秋", new JSONArray().put(row(1, 2)), warnings);
        require(lessons.isEmpty() && !warnings.isEmpty(), "unknown year explicit warning");
        lessons = ZjuAcademicCalendar.expand("2026-2027", "春", new JSONArray().put(row(1, 2)), warnings);
        require(lessons.isEmpty(), "unverified quarter no dates");
        lessons = ZjuAcademicCalendar.expand("2026-2027", "秋", new JSONArray().put(row(1, 2).put("djj", 13).put("skcd", 2)), warnings);
        require(lessons.isEmpty(), "invalid period skipped");
        ZjuSnapshot legacy = new ZjuSnapshot(List.of(), List.of(), List.of(), List.of());
        require(legacy.completeTimetableTerms.isEmpty(), "four argument constructor has no completed terms");
        List<String> terms = new ArrayList<>(List.of("2026-2027 秋"));
        ZjuSnapshot complete = new ZjuSnapshot(List.of(), List.of(), List.of(), List.of(), terms);
        terms.clear();
        require(complete.completeTimetableTerms.equals(List.of("2026-2027 秋")), "completed terms defensive copy");
        boolean immutable = false;
        try { complete.completeTimetableTerms.clear(); }
        catch (UnsupportedOperationException expected) { immutable = true; }
        require(immutable, "completed terms immutable");
        System.out.println("calendarChecks=" + checks + " passed");
    }
}
