import com.donglan.chrona.calendar.AllDayDates;
import com.donglan.chrona.data.EventCategory;

import java.time.ZoneId;
import java.time.Instant;

public final class AllDayDatesCheck {
    public static void main(String[] args) {
        long start = AllDayDates.utcStart("2026-09-26");
        long end = AllDayDates.utcExclusiveEnd("2026-09-28");
        check(end - start == 3L * 86_400_000L, "inclusive multi-day range");
        check("2026-09-26".equals(AllDayDates.displayStart(start)), "start date");
        check("2026-09-28".equals(AllDayDates.displayEnd(end)), "inclusive end date");
        check("2026-09-26".equals(AllDayDates.localDate(
                Instant.parse("2026-09-25T17:00:00Z").toEpochMilli(),
                ZoneId.of("Asia/Shanghai"))), "local date");
        check(EventCategory.EVENT.equals(EventCategory.normalize("unknown")), "legacy category");
        check("待办".equals(EventCategory.label(EventCategory.TASK)), "category label");
        System.out.println("AllDayDatesCheck passed");
    }

    private static void check(boolean result, String label) {
        if (!result) throw new AssertionError(label);
    }
}
