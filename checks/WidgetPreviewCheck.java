package com.donglan.chrona;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Checks the real selector at the evening boundary without a device. */
public final class WidgetPreviewCheck {
    private static WidgetAgenda.Item item(long id, long start, long end) {
        return new WidgetAgenda.Item(id, 1, start, end, "事项", "", "活动", "拾时", false, false);
    }
    private static void check(boolean value, String label) {
        if (!value) throw new AssertionError(label);
    }
    public static void main(String[] args) {
        ZoneId zone = ZoneId.of("Asia/Shanghai");
        long begin = LocalDate.of(2026, 9, 29).atStartOfDay(zone).toInstant().toEpochMilli();
        long hour = 3600000L, tomorrow = begin + 24 * hour;
        List<WidgetAgenda.Item> items = List.of(
                item(1, begin + hour, begin + 2 * hour),
                item(2, begin + 21 * hour, begin + 23 * hour),
                item(3, tomorrow, tomorrow + hour),
                item(4, tomorrow + 24 * hour, tomorrow + 25 * hour),
                item(5, begin + 22 * hour, begin + 22 * hour));
        WidgetAgenda evening = new WidgetAgenda(begin + 22 * hour, zone);
        evening.select(new ArrayList<>(items), begin, tomorrow, tomorrow + 30 * 24 * hour);
        check(evening.today.stream().map(i -> i.id).toList().equals(List.of(2L, 5L, 3L)),
                "at 22:00 keep ongoing/point events and tomorrow, omit past and day after");
        check(evening.next.id == 2, "next widget still selects ongoing event");
        WidgetAgenda before = new WidgetAgenda(begin + 22 * hour - 1, zone);
        before.select(new ArrayList<>(items), begin, tomorrow, tomorrow + 30 * 24 * hour);
        check(before.today.stream().map(i -> i.id).toList().equals(List.of(1L, 2L, 5L)),
                "before preview keep today's historical rows");
        WidgetAgenda midnight = new WidgetAgenda(tomorrow, zone);
        midnight.select(new ArrayList<>(items), tomorrow, tomorrow + 24 * hour,
                tomorrow + 30 * 24 * hour);
        check(midnight.today.size() == 1 && midnight.today.get(0).id == 3,
                "midnight returns to today's window");
        WidgetAgenda custom = new WidgetAgenda(begin + 20 * hour, zone, 20 * 60);
        custom.select(new ArrayList<>(items), begin, tomorrow, tomorrow + 30 * 24 * hour);
        check(custom.previewTomorrow && custom.today.size() == 3,
                "custom preview time applies to the same selector");
        check(evening.nextTransition == tomorrow, "evening schedules midnight reset");
        check(before.nextTransition == begin + 22 * hour, "daytime schedules configured time");
        ZoneId dst = ZoneId.of("America/New_York");
        long dstNow = LocalDate.of(2026, 10, 31).atTime(22, 0).atZone(dst).toInstant().toEpochMilli();
        WidgetAgenda dstAgenda = new WidgetAgenda(dstNow, dst);
        long dstTomorrow = LocalDate.of(2026, 11, 1).atStartOfDay(dst).toInstant().toEpochMilli();
        long dstEnd = LocalDate.of(2026, 11, 2).atStartOfDay(dst).toInstant().toEpochMilli();
        dstAgenda.select(new ArrayList<>(List.of(item(6, dstEnd - 1, dstEnd),
                item(7, dstEnd, dstEnd + hour))), dstTomorrow - 24 * hour,
                dstTomorrow, dstEnd + 24 * hour);
        check(dstAgenda.today.size() == 1 && dstAgenda.today.get(0).id == 6,
                "tomorrow window follows calendar days across DST");
        long overlapNow = java.time.Instant.parse("2026-11-01T06:10:00Z").toEpochMilli();
        WidgetAgenda overlap = new WidgetAgenda(overlapNow, dst, 90);
        check(!overlap.previewTomorrow && overlap.nextTransition
                == java.time.Instant.parse("2026-11-01T06:30:00Z").toEpochMilli(),
                "second repeated hour schedules upcoming preview, never an alarm in the past");
        System.out.println("Widget preview checks passed: evening, pre-boundary, midnight, next widget");
    }
}
