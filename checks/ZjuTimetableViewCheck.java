import com.donglan.chrona.zju.ZjuSnapshot;
import com.donglan.chrona.zju.ZjuTimetableView;
import java.time.LocalDate;
import java.util.List;
import com.donglan.chrona.timetable.AcademicTerms;
import java.util.Collections;

public final class ZjuTimetableViewCheck {
    private static ZjuSnapshot.Lesson lesson(String term, String date) {
        long start = LocalDate.parse(date).atTime(8, 0).atZone(ZjuTimetableView.ZONE).toInstant().toEpochMilli();
        return new ZjuSnapshot.Lesson(date, "测试课程", "东一", term, start, start + 90 * 60_000L, "课程备注");
    }
    public static void main(String[] args) {
        var terms = ZjuTimetableView.terms(List.of(lesson("2026短学期", "2026-07-06"),
                lesson("2026短学期", "2026-07-13"), lesson("2026秋", "2026-10-19")));
        if (terms.size() != 2 || !terms.containsKey("2026短学期")) throw new AssertionError("raw term");
        var summer = terms.get("2026短学期");
        if (summer.blocks.size() != 1 || summer.occurrences.size() != 2) throw new AssertionError("repeat grouping");
        var entry = summer.occurrences.get(0);
        if (entry.start.getHour() != 8 || !entry.description.equals("课程备注") || !entry.location.equals("东一"))
            throw new AssertionError("projection");
        if (!ZjuTimetableView.current(terms, LocalDate.of(2026, 7, 10)).equals("2026短学期"))
            throw new AssertionError("current term");
        if (!ZjuTimetableView.current(terms, LocalDate.of(2026, 10, 1)).equals("2026秋"))
            throw new AssertionError("nearest term");
        if (!ZjuTimetableView.terms(List.of()).isEmpty()) throw new AssertionError("empty cache");
        var raw = ZjuTimetableView.terms(List.of(lesson("秋", "2026-10-05"), lesson("秋", "2026-10-12"),
                lesson("秋", "2026-10-19"), lesson("秋", "2026-10-10"),
                lesson("秋", "2026-10-04"), lesson("秋", "2026-10-11"), lesson("秋", "2026-10-18"))).get("秋");
        var view = new AcademicTerms.View(raw.occurrences, raw.zone, Collections.emptyMap());
        if (view.regular.blocks.size() != 2 || view.special.size() != 1
                || view.special.get(0).start.getDayOfWeek() != java.time.DayOfWeek.SATURDAY)
            throw new AssertionError("one-off makeup separated; genuine recurring weekend course retained");
        if (raw.occurrences.size() != 7 || view.all.size() != 7 || view.regular.occurrences.size() != 6)
            throw new AssertionError("classification preserves every actual school occurrence");
        var shortView = new AcademicTerms.View(summer.occurrences, summer.zone, Collections.emptyMap());
        if (shortView.special.size() != 2 || !shortView.regular.blocks.isEmpty())
            throw new AssertionError("short-term dates remain available as special arrangements");
        System.out.println("ZjuTimetableViewCheck passed");
    }
}
