import com.donglan.chrona.data.ScheduleRangeStep;
import com.donglan.chrona.data.ScheduleFilterState;
import java.time.LocalDate;

public final class ScheduleRangeStepCheck {
    private static void check(String from, String until, boolean month, int direction,
            String expectedFrom, String expectedUntil) {
        LocalDate[] actual = ScheduleRangeStep.shift(LocalDate.parse(from), LocalDate.parse(until), month, direction);
        if (!actual[0].equals(LocalDate.parse(expectedFrom)) || !actual[1].equals(LocalDate.parse(expectedUntil)))
            throw new AssertionError(java.util.Arrays.toString(actual));
    }
    public static void main(String[] args) {
        check("2026-09-28", "2026-09-29", false, 1, "2026-09-29", "2026-09-30");
        check("2026-09-28", "2026-10-01", false, -1, "2026-09-25", "2026-09-28");
        check("2026-12-28", "2027-01-04", false, 1, "2027-01-04", "2027-01-11");
        check("2024-01-01", "2024-02-01", true, 1, "2024-02-01", "2024-03-01");
        check("2026-03-01", "2026-04-01", true, -1, "2026-02-01", "2026-03-01");
        check("2026-03-07", "2026-03-10", false, 1, "2026-03-10", "2026-03-13");
        LocalDate[] leapSpan = ScheduleRangeStep.shift(LocalDate.parse("2024-01-01"),
                LocalDate.parse("2025-01-01"), false, 1);
        if (!leapSpan[1].isAfter(leapSpan[0].plusYears(1))) throw new AssertionError("Year limit guard");
        LocalDate today = LocalDate.parse("2026-09-28");
        ScheduleFilterState original = new ScheduleFilterState(5, 2, 1, 0, 0,
                today, today.plusDays(2));
        ScheduleFilterState draft = original.copy();
        if (!draft.shift(1, today) || !draft.date.equals(today.plusDays(3)))
            throw new AssertionError("Inclusive custom interval");
        draft.category = 4;
        if (original.category != 2 || !original.date.equals(today))
            throw new AssertionError("Cancelled draft modified original filters");
        draft.reset(today);
        if (draft.window(today)[0] != null || draft.category != 0 || original.range != 5)
            throw new AssertionError("Draft reset must remain isolated");
        draft.source = 1;
        if (!draft.window(today)[0].equals(LocalDate.parse("2026-09-01")))
            throw new AssertionError("System ALL uses month");
        draft = new ScheduleFilterState(5, 0, 0, 1, 3,
                LocalDate.parse("2024-01-01"), LocalDate.parse("2024-12-31"));
        if (draft.shift(1, today) || !draft.date.equals(LocalDate.parse("2024-01-01")))
            throw new AssertionError("Rejected system shift changed draft");
        draft.until = draft.date.plusYears(2);
        if (draft.validSystemRange(today)) throw new AssertionError("Invalid system apply");
        System.out.println("Schedule range stepping checks passed");
    }
}
