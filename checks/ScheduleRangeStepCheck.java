import com.donglan.chrona.data.ScheduleRangeStep;
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
        System.out.println("Schedule range stepping checks passed");
    }
}
