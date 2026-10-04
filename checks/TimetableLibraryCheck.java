package com.donglan.chrona;

import com.donglan.chrona.timetable.*;
import java.time.*;
import java.util.*;
import java.nio.file.*;
import org.json.JSONObject;

public final class TimetableLibraryCheck {
    static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    static String source(String title, String... dates) {
        StringBuilder text = new StringBuilder("BEGIN:VCALENDAR\r\nVERSION:2.0\r\nPRODID:-//Chrona Test//CN\r\nX-WR-CALNAME:")
                .append(title).append("\r\n");
        for (int i = 0; i < dates.length; i++) text.append("BEGIN:VEVENT\r\nUID:").append(i)
                .append("\r\nDTSTAMP:20261002T000000Z\r\nSUMMARY:课程\r\nDTSTART:").append(dates[i])
                .append("T080000\r\nDURATION:PT95M\r\nEND:VEVENT\r\n");
        return text.append("END:VCALENDAR\r\n").toString();
    }
    public static void main(String[] args) throws Exception {
        TimetableStore.Document original = TimetableStore.prepare("test.ics",
                source("2026-2027秋冬", "20260914", "20260921", "20260928", "20261109", "20261116", "20261123"), ZoneId.of("Asia/Shanghai"));
        TimetableStore.Library library = new TimetableStore.Library(Collections.singletonList(original), "");
        check(library.semesters.size() == 2, "two semesters");
        check(library.current(LocalDate.of(2026, 10, 2)).id().equals("2026/0"), "current autumn");
        check(library.current(LocalDate.of(2026, 11, 9)).id().equals("2026/1"), "winter starts at boundary");
        check(library.current(LocalDate.of(2027, 1, 1)).id().equals("2026/1"), "nearest term during holiday");
        check(library.select("2026/0").current(LocalDate.of(2026, 11, 9)).id().equals("2026/0"), "remember manual selection");
        TimetableStore.Library pinned = library.select("2026/0");
        TimetableStore.Library automatic = pinned.select("");
        check(automatic.current(LocalDate.of(2026, 11, 9)).id().equals("2026/1"), "browser ignores old selection at winter boundary");
        check(pinned.selected.equals("2026/0"), "automatic lookup does not change phone selection");
        check(automatic.current(LocalDate.of(2026, 11, 8)).id().equals("2026/0"), "day before boundary remains autumn");
        check(automatic.current(LocalDate.of(2027, 1, 1)).id().equals("2026/1"), "cross-year nearest winter");
        check(automatic.current(LocalDate.of(2026, 8, 1)).id().equals("2026/0"), "before all terms nearest autumn");
        check(new TimetableStore.Library(Collections.emptyList(), "stale").select("").current(LocalDate.now()) == null, "empty automatic library");
        TimetableStore.Document springSummer = TimetableStore.prepare("spring.ics", source("2026-2027春夏", "20270301", "20270308", "20270315", "20270426", "20270503", "20270510"), ZoneId.of("Asia/Shanghai"));
        TimetableStore.Library fourSeasons = pinned.merge(springSummer).select("");
        check(fourSeasons.current(LocalDate.of(2027, 3, 1)).id().equals("2026/2"), "spring after cross-year");
        check(fourSeasons.current(LocalDate.of(2027, 4, 25)).id().equals("2026/2"), "spring until summer boundary");
        check(fourSeasons.current(LocalDate.of(2027, 4, 26)).id().equals("2026/3"), "summer at confirmed boundary");
        TimetableStore.Document winter = TimetableStore.prepare("new.ics", source("2026-2027冬", "20261110", "20261117", "20261124"), ZoneId.of("Asia/Shanghai"));
        TimetableStore.Library merged = library.merge(winter).select("2026/1");
        check(merged.semesters.size() == 2 && merged.documents.size() == 2, "replace winter but retain autumn");
        check(merged.select("").current(LocalDate.of(2026, 10, 4)).document.source.equals(original.source), "automatic multi-source autumn");
        check(merged.select("").current(LocalDate.of(2026, 11, 17)).document == winter, "automatic multi-source winter");
        check(merged.current(LocalDate.now()).document == winter, "selected replacement source");
        TimetableStore.Semester selected = merged.current(LocalDate.now());
        merged = merged.classify(selected, selected.view.all, false);
        check(merged.current(LocalDate.now()).view.special.size() == 3, "manual classification applies");
        String snapshot = TimetableStore.encode(merged);
        TimetableStore.Library restored = TimetableStore.inspect(snapshot);
        check(restored.semesters.size() == 2 && restored.current(LocalDate.now()).view.special.size() == 3, "snapshot keeps terms and classification");
        check(restored.selected.equals("2026/1"), "snapshot keeps selection");
        TimetableStore.Library auto = restored.classify(restored.current(LocalDate.now()), restored.current(LocalDate.now()).view.all, null);
        check(auto.current(LocalDate.now()).view.special.isEmpty(), "reset classification");
        TimetableStore.Library removed = restored.remove(restored.current(LocalDate.now()));
        check(removed.semesters.size() == 1 && removed.semesters.get(0).id().equals("2026/0"), "delete only chosen semester");
        JSONObject ungrouped = new JSONObject().put("format", 1).put("name", original.name).put("ics", original.source)
                .put("zone", "Asia/Shanghai").put("importedAt", 1);
        check(TimetableStore.inspect(ungrouped.toString()).semesters.size() == 2, "saved ICS gains grouping without losing source");
        Path temp = Files.createTempDirectory("chrona-term-check");
        TimetableStore store = new TimetableStore(new android.content.Context(temp.toFile()));
        store.save(merged);
        String before = store.snapshot();
        android.util.AtomicFile.failNext = true;
        try { store.save(removed); throw new AssertionError("write failure ignored"); }
        catch (java.io.IOException expected) { }
        check(store.snapshot().equals(before), "failed write retains prior semesters");
        try { store.restore("{}"); throw new AssertionError("corrupt backup accepted"); } catch (org.json.JSONException expected) { }
        check(store.snapshot().equals(before), "failed restore retains prior semesters");
        store.restore(null);
        check(store.load() == null, "empty library deletion");
        Files.delete(temp);
        System.out.println("Timetable library checks passed: multi-term merge, replacement, selection, classification, deletion, snapshot and atomic failure");
    }
}
