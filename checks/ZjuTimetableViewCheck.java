import com.donglan.chrona.zju.ZjuSnapshot;
import com.donglan.chrona.zju.ZjuTimetableView;
import java.time.LocalDate;
import java.util.List;

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
        System.out.println("ZjuTimetableViewCheck passed");
    }
}
