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
        require(lessons.stream().allMatch(x -> x.notes.contains("教师：Teacher") && x.notes.contains("排课：秋冬{第1-8周|2节/周}") && x.notes.contains("节次：第1—2节")), "notes preserve teacher and scheduling facts");
        require(!lessons.get(0).notes.contains("课程编号") && !lessons.get(0).notes.contains("选课课号"), "missing metadata omitted");
        List<ZjuSnapshot.Lesson> metadata = ZjuAcademicCalendar.expand("2026-2027", "秋", new JSONArray().put(row(2, 0).put("jsxm", "Teacher B").put("kcdm", "001234").put("xkkh", "(2026)-001234-1")), new ArrayList<>());
        require(metadata.get(0).notes.contains("教师：Teacher B") && !metadata.get(0).notes.contains("教师：Teacher\n"), "explicit teacher replaces display teacher");
        require(metadata.get(0).notes.contains("课程编号：001234") && metadata.get(0).notes.contains("选课课号：(2026)-001234-1") && metadata.get(0).notes.contains("周次：单周"), "present metadata and parity retained");
        require(new ZjuSnapshot.Lesson("legacy", "title", "", "term", 1, 2).notes.isEmpty(), "six argument lesson has empty notes");
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
        require(!lessons.isEmpty(), "spring verified official anchor");
        require(date(lessons.get(0)).equals(LocalDate.of(2027,2,22)), "spring starts Feb22");
        ZjuCalendarConfig custom=ZjuCalendarConfig.parse(ZjuCalendarConfig.ordinary().json().toString());
        custom.terms.add(new ZjuCalendarConfig.Term("2027-2028","秋","2027-09-13","2027-11-07","2027-11-07"));
        custom.validate();
        lessons=ZjuAcademicCalendar.expand(custom,"2027-2028","秋",new JSONArray().put(row(1,2)),new ArrayList<>());
        require(lessons.size()==8,"configured future year runs");
        require(ZjuCalendarConfig.haining().actualDate(LocalDate.of(2026,9,25)).equals(LocalDate.of(2026,9,20)),"haining own makeup");
        require(ZjuCalendarConfig.haining().term("2026-2027","秋")==null,"haining never ordinary quarter");
        lessons=ZjuAcademicCalendar.expand("2026-2027","夏",new JSONArray().put(row(2,2)),new ArrayList<>());
        require(lessons.stream().noneMatch(x->date(x).isAfter(LocalDate.of(2027,6,14))),"summer stops before school exams");
        require(ZjuCalendarConfig.ordinary().actualDate(LocalDate.of(2027,3,27))==null,"spring sports closed");
        require(ZjuCalendarConfig.ordinary().actualDate(LocalDate.of(2027,6,9))==null,"dragon boat holiday closed");
        ZjuCalendarConfig shortTerm=new ZjuCalendarConfig();shortTerm.campus="custom";
        shortTerm.terms.add(new ZjuCalendarConfig.Term("2027-2028","暑","2028-07-03","2028-07-16","2028-07-14"));
        shortTerm.starts.clear();shortTerm.ends.clear();shortTerm.starts.add("09:00");shortTerm.ends.add("09:40");shortTerm.validate();
        lessons=ZjuAcademicCalendar.expand(shortTerm,"2027-2028","暑",new JSONArray().put(row(1,2).put("skcd",1)),new ArrayList<>());
        require(lessons.size()==2,"custom short term uses configured teaching boundary");
        require(Instant.ofEpochMilli(lessons.get(0).start).atZone(ZONE).toLocalTime().equals(LocalTime.of(9,0)),"custom campus periods applied");
        boolean badPeriod=false;shortTerm.ends.set(0,"08:59");try{shortTerm.validate();}catch(IllegalArgumentException e){badPeriod=true;}require(badPeriod,"reject negative teaching period");
        boolean badMonday=false;ZjuCalendarConfig bad=new ZjuCalendarConfig();bad.terms.add(new ZjuCalendarConfig.Term("2027-2028","短","2028-07-04","2028-07-16","2028-07-14"));try{bad.validate();}catch(IllegalArgumentException e){badMonday=true;}require(badMonday,"reject non Monday anchor");
        boolean invalid=false;try{custom.makeup.put(LocalDate.of(2026,10,6),LocalDate.of(2026,9,20));custom.validate();}catch(IllegalArgumentException e){invalid=true;}require(invalid,"reject makeup cycles");
        invalid=false;try{ZjuCalendarConfig.parse(ZjuCalendarConfig.ordinary().json().put("password","forbidden").toString());}catch(IllegalArgumentException e){invalid=true;}require(invalid,"reject credentials in JSON");
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
