package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.calendar.CalendarOccurrence;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.text.Normalizer;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Explicit local snapshot export. No network fetch and no changes to other calendar apps. */
final class CourseCalendarExport {
    static List<CalendarOccurrence> load(Context context, boolean imported, boolean school) throws Exception {
        Map<String, CalendarOccurrence> unique = new LinkedHashMap<>();
        ZjuSettingsStore settings = new ZjuSettingsStore(context);
        if (school && settings.enabled()) {
            for (ZjuSnapshot.Lesson lesson : new ZjuTimetableStore(context).load(settings.account())) {
                CalendarOccurrence value = new CalendarOccurrence(0, lesson.start, lesson.end, lesson.title,
                        false, lesson.location, "折姜星辰", null, CourseSourceMatching.ZJU, "", lesson.notes);
                unique.putIfAbsent(key(value), value);
            }
        }
        if (imported) for (CourseAgenda.Item item : CourseAgenda.all(context, ZoneId.systemDefault())) {
            long start = item.allDay ? utcDate(item.start) : item.start;
            long end = item.allDay ? utcDate(item.end) : item.end;
            CalendarOccurrence value = new CalendarOccurrence(0, start, end, item.title, item.allDay,
                    item.location, "导入课表", null, CourseSourceMatching.IMPORTED, "", item.description);
            unique.putIfAbsent(key(value), value);
        }
        return new ArrayList<>(unique.values());
    }

    static final class Result {
        int inserted, skipped;
    }

    static Result write(Context context, List<CalendarOccurrence> lessons) throws Exception {
        Result result = new Result();
        synchronized (AndroidSync.LOCK) {
            CalendarStore calendar = new CalendarStore(context);
            if (!calendar.hasReadPermission() || !calendar.hasWritePermission())
                throw new IllegalStateException("请允许读取和写入系统日历");
            // Query each occupied month just once, without the user's display-source filters.
            Set<String> existing = new HashSet<>();
            Set<java.time.YearMonth> months = new HashSet<>();
            for (CalendarOccurrence item : lessons)
                months.add(java.time.YearMonth.from(Instant.ofEpochMilli(item.startMillis).atZone(ZoneOffset.UTC)));
            for (java.time.YearMonth month : months) {
                long begin = month.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                long end = month.plusMonths(1).atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
                for (CalendarOccurrence item : calendar.listAllInstances(begin, end)) existing.add(key(item));
            }
            for (CalendarOccurrence item : lessons) {
                if (existing.contains(key(item))) { result.skipped++; continue; }
                // The school master switch may be turned off while export is queued.
                if (item.calendarId == CourseSourceMatching.ZJU && !new ZjuSettingsStore(context).enabled())
                    throw new IllegalStateException("折姜星辰已关闭，请重新选择导出来源");
                try {
                    calendar.insertEvent(new CalendarStore.EventInput(item.title, item.startMillis,
                            item.endMillis, item.allDay ? "UTC" : ZoneId.systemDefault().getId(),
                            item.allDay, item.description, item.location, Collections.emptyList()));
                } catch (Exception failure) {
                    throw new IllegalStateException("已写入 " + result.inserted + " 节，跳过 " + result.skipped
                            + " 节；其余写入失败，可重试且不会重复", failure);
                }
                existing.add(key(item));
                result.inserted++;
            }
        }
        return result;
    }

    static String key(CalendarOccurrence item) {
        String title = text(item.title), location = text(item.location);
        return title.length() + ":" + title + location.length() + ":" + location
                + ":" + item.startMillis + ":" + item.endMillis + ":" + item.allDay;
    }
    private static String text(String value) {
        return Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFKC)
                .toLowerCase(Locale.ROOT).replaceAll("[\\s\\p{Z}\\u0085]+", "");
    }
    private static long utcDate(long value) {
        return Instant.ofEpochMilli(value).atZone(ZoneId.systemDefault()).toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
    }
    private CourseCalendarExport() { }
}
