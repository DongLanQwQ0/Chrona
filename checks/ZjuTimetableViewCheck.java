import com.donglan.chrona.zju.ZjuSnapshot;
import com.donglan.chrona.zju.ZjuTimetableView;
import java.time.LocalDate;
import java.util.List;
import com.donglan.chrona.timetable.AcademicTerms;
import java.util.Collections;

public final class ZjuTimetableViewCheck {
    private static com.donglan.chrona.timetable.Timetable.Occurrence occurrence(String date,String room,int start,int end) {
        LocalDate day=LocalDate.parse(date);
        return new com.donglan.chrona.timetable.Timetable.Occurrence("连堂课程",room,"原始备注",day.atStartOfDay().plusMinutes(start),day.atStartOfDay().plusMinutes(end),false,false,"");
    }
    private static void consecutive() {
        var entries=new java.util.ArrayList<com.donglan.chrona.timetable.Timetable.Occurrence>();
        for(String day:List.of("2026-10-05","2026-10-12","2026-10-19","2026-10-26","2026-11-02","2026-11-09")){
            String room=entries.size()%4==0?"乙教室":"甲教室";
            entries.add(occurrence(day,room,805,850));entries.add(occurrence(day,room,855,950));
        }
        var table=new com.donglan.chrona.timetable.Timetable(entries,ZjuTimetableView.ZONE,12);
        if(table.blocks.size()!=1||table.blocks.get(0).startMinute!=805||table.blocks.get(0).endMinute!=950
                ||table.occurrences.size()!=12||table.blocks.get(0).occurrences().size()!=12||!table.blocks.get(0).location.isEmpty())throw new AssertionError("same-date contiguous teaching merges while original dates/rooms survive");
        if(new AcademicTerms.View(table.occurrences,table.zone,Collections.emptyMap()).regular.blocks.size()!=1)throw new AssertionError("real grid classification retains merged display");
        for(var pair:List.of(
                List.of(occurrence("2026-10-12","甲",805,850),occurrence("2026-10-19","甲",855,950)),
                List.of(occurrence("2026-10-12","甲",805,850),occurrence("2026-10-12","乙",855,950)),
                List.of(occurrence("2026-10-12","甲",805,860),occurrence("2026-10-12","甲",855,950)),
                List.of(occurrence("2026-10-12","甲",600,650),occurrence("2026-10-12","甲",740,800))))
            if(new com.donglan.chrona.timetable.Timetable(pair,ZjuTimetableView.ZONE,2).blocks.size()!=2)throw new AssertionError("different dates, room change, overlap and lunch stay separate");
        var unequal=new java.util.ArrayList<>(entries);unequal.remove(unequal.size()-1);
        if(new com.donglan.chrona.timetable.Timetable(unequal,ZjuTimetableView.ZONE,5).blocks.size()!=2)throw new AssertionError("missing session does not acquire an invented duration");
        for(boolean sameDate:List.of(false,true)){
            var conflict=new com.donglan.chrona.timetable.Timetable.Occurrence("课间另一课程","甲","",LocalDate.parse(sameDate?"2026-10-12":"2026-10-19").atStartOfDay().plusMinutes(851),LocalDate.parse(sameDate?"2026-10-12":"2026-10-19").atStartOfDay().plusMinutes(854),false,false,"");
            var sample=List.of(occurrence("2026-10-12","甲",805,850),occurrence("2026-10-12","甲",855,950),conflict);
            if(new com.donglan.chrona.timetable.Timetable(sample,ZjuTimetableView.ZONE,3).blocks.size()!=(sameDate?3:2))throw new AssertionError("only an actual same-date intervening course blocks the merge");
        }
    }
    private static ZjuSnapshot.Lesson lesson(String term, String date) {
        long start = LocalDate.parse(date).atTime(8, 0).atZone(ZjuTimetableView.ZONE).toInstant().toEpochMilli();
        return new ZjuSnapshot.Lesson(date, "测试课程", "东一", term, start, start + 90 * 60_000L, "课程备注");
    }
    public static void main(String[] args) {
        consecutive();
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
