package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.timetable.AcademicTerms;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.*;
import java.util.*;

public final class CourseAgendaCheck {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static final ZoneId SHANGHAI = ZoneId.of("Asia/Shanghai");
    static final ZoneId LA = ZoneId.of("America/Los_Angeles");
    static long day(String date, ZoneId zone) { return LocalDate.parse(date).atStartOfDay(zone).toInstant().toEpochMilli(); }
    static String event(String uid, String title, String dateLines, String description) {
        return "BEGIN:VEVENT\r\nUID:" + uid + "\r\nDTSTAMP:20261002T000000Z\r\nSUMMARY:" + title
                + "\r\nLOCATION:教室\r\nDESCRIPTION:" + description + "\r\n" + dateLines + "\r\nEND:VEVENT\r\n";
    }
    static TimetableStore.Document document(String contents, int mode) throws Exception {
        TimetableStore.Document parsed = TimetableStore.prepare("test.ics",
                "BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Chrona//CN\r\n" + contents + "END:VCALENDAR\r\n", SHANGHAI);
        return parsed.configured(new AcademicTerms.Plan(2026, mode, LocalDate.of(2026,9,1),
                LocalDate.of(2026,11,1), LocalDate.of(2027,1,1)));
    }
    public static void main(String[] args) throws Exception {
        Path directory = Files.createTempDirectory("chrona-course-agenda");
        Context context = new Context(directory.toFile());
        TimetableStore store = new TimetableStore(context);
        String overnight = "DTSTART:20261002T233000\r\nDTEND:20261003T013000";
        TimetableStore.Document first = document(event("a", "跨日课", overnight, "说明一")
                + event("b", "跨日课", overnight, "说明二")
                + event("c", "全天安排", "DTSTART;VALUE=DATE:20261003\r\nDTEND;VALUE=DATE:20261005", "")
                + event("d", "冬季课", "DTSTART:20261103T080000\r\nDTEND:20261103T090000", ""), 0);
        TimetableStore.Library library = new TimetableStore.Library(List.of(first), "2026/1");
        store.save(library);
        check(store.load() == store.load(), "parsed library reused without reparsing ICS");
        List<CourseAgenda.Item> today = CourseAgenda.load(context, day("2026-10-03", SHANGHAI), day("2026-10-04", SHANGHAI), SHANGHAI);
        check(today.size() == 2, "all enabled terms independent of selected winter; description variants deduplicated");
        check(today.get(0).title.equals("跨日课") && today.get(0).end > day("2026-10-03", SHANGHAI), "midnight overlap included and chronological");
        check(CourseAgenda.load(context, day("2026-10-03", SHANGHAI), day("2026-10-03", SHANGHAI), SHANGHAI).isEmpty(), "empty half-open window");
        check(CourseAgenda.load(context, today.get(0).end, day("2026-10-04", SHANGHAI), SHANGHAI).size() == 1, "exclusive end boundary");
        CourseAgenda.Item allDay = CourseAgenda.load(context, day("2026-10-04", LA), day("2026-10-05", LA), LA).get(0);
        check(allDay.allDay && allDay.start == day("2026-10-03", LA) && allDay.end == day("2026-10-05", LA), "all-day dates retain local dates in display zone");
        var launch = CourseAgenda.intent(context, today.get(0));
        check(launch.target == TimetableActivity.class && launch.getStringExtra(CourseAgenda.EXTRA_TERM).equals("2026/0")
                && launch.getStringExtra(CourseAgenda.EXTRA_OCCURRENCE).equals(first.table.occurrences.get(0).key()), "precise semester and occurrence click payload");
        long revision = CourseAgenda.revision();
        java.lang.reflect.Field lockField = TimetableStore.class.getDeclaredField("LOCK");
        lockField.setAccessible(true);
        java.util.concurrent.CountDownLatch revisionRead = new java.util.concurrent.CountDownLatch(1);
        synchronized (lockField.get(null)) {
            Thread reader = new Thread(() -> { CourseAgenda.revision(); revisionRead.countDown(); });
            reader.start();
            check(revisionRead.await(3, java.util.concurrent.TimeUnit.SECONDS),
                    "UI revision reads never wait for the ICS parser lock");
        }
        String snapshot = store.snapshot();
        TimetableStore.Document duplicateTerm = document(event("z", "跨日课", overnight, "跨学期说明"), 3);
        store.save(new TimetableStore.Library(List.of(first.withState(Set.of(0), Map.of()), duplicateTerm), ""));
        check(CourseAgenda.load(context, day("2026-10-03", SHANGHAI), day("2026-10-04", SHANGHAI), SHANGHAI).size() == 2,
                "same actual arrangement across distinct enabled semesters deduplicated");
        check(CourseAgenda.revision() > revision, "save increments revision");
        store.restore(snapshot);
        check(CourseAgenda.load(context, day("2026-11-03", SHANGHAI), day("2026-11-04", SHANGHAI), SHANGHAI).size() == 1, "restore invalidates replacement cache");
        TimetableStore.Library loaded = store.load();
        TimetableStore.Semester autumn = loaded.semesters.stream().filter(term -> term.id().equals("2026/0")).findFirst().orElseThrow();
        store.save(loaded.remove(autumn));
        check(CourseAgenda.load(context, day("2026-10-03", SHANGHAI), day("2026-10-04", SHANGHAI), SHANGHAI).isEmpty(), "deleted term disappears");
        TimetableStore.Document replacement = document(event("new", "替换课程", overnight, ""), 2);
        store.save(store.load().merge(replacement));
        check(CourseAgenda.load(context, day("2026-10-03", SHANGHAI), day("2026-10-04", SHANGHAI), SHANGHAI).get(0).title.equals("替换课程"), "replacement source visible immediately");
        revision = CourseAgenda.revision();
        int refreshes = AgendaWidgetProvider.refreshes;
        android.util.AtomicFile.failNext = true;
        try { store.save(library); throw new AssertionError("failure ignored"); } catch (java.io.IOException expected) { }
        check(CourseAgenda.revision() == revision && AgendaWidgetProvider.refreshes == refreshes, "failed save neither invalidates nor refreshes");
        store.restore(null);
        check(CourseAgenda.revision() > revision && AgendaWidgetProvider.refreshes > refreshes, "successful deletion invalidates and refreshes widgets");
        check(CourseAgenda.load(context, 0, Long.MAX_VALUE, LA).isEmpty(), "deleted library clears cache");
        Path bad = Files.createTempDirectory("chrona-course-corrupt");
        Files.writeString(bad.resolve("course-timetable.json"), "corrupt");
        try { CourseAgenda.load(new Context(bad.toFile()), 0, Long.MAX_VALUE, LA); throw new AssertionError("corruption hidden as empty"); }
        catch (org.json.JSONException expected) { }
        Files.delete(bad.resolve("course-timetable.json")); Files.delete(bad);
        Files.delete(directory);
        System.out.println("Course agenda checks passed: enabled terms, overlap, all-day zone, deduplication, exact intent, cache, replacement, restore, deletion and failures");
    }
}
