package com.donglan.chrona.timetable;

import biweekly.ICalendar;
import biweekly.component.VEvent;
import biweekly.io.ParseWarning;
import biweekly.io.TimezoneAssignment;
import biweekly.io.text.ICalReader;
import biweekly.property.DateStart;
import biweekly.property.ExceptionDates;
import biweekly.property.ICalProperty;
import biweekly.property.RecurrenceDates;
import biweekly.property.RecurrenceRule;
import biweekly.util.Frequency;
import biweekly.util.ICalDate;
import biweekly.util.Period;
import biweekly.util.Recurrence;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeMap;

/** Offline RFC 5545 import. Invalid or oversized input never replaces the saved timetable. */
public final class TimetableParser {
    public static final int MAX_BYTES = 1024 * 1024;
    private static final int MAX_EVENTS = 5000;
    private static final int MAX_OCCURRENCES = 30000;
    private static final long MAX_PARSE_NANOS = 10_000_000_000L;
    private static final Set<String> TIME_PROPERTIES = new HashSet<>(java.util.Arrays.asList(
            "DTSTART", "DTEND", "DURATION", "RRULE", "RDATE", "EXDATE", "EXRULE", "RECURRENCE-ID"));

    private final ZoneId zone;
    private final long started = System.nanoTime();
    private int generated;
    private final List<Timetable.Occurrence> occurrences = new ArrayList<>();

    private static final class Entry {
        final VEvent event;
        final ICalendar calendar;
        final String uid;
        final String title, location, description;
        Entry(VEvent event, ICalendar calendar, String uid) throws IOException {
            this.event = event;
            this.calendar = calendar;
            this.uid = uid;
            title = event.getSummary() == null ? null : clean(event.getSummary().getValue());
            location = event.getLocation() == null ? null : clean(event.getLocation().getValue());
            description = event.getDescription() == null ? null : clean(event.getDescription().getValue());
            if (title != null && title.length() > 300 || location != null && location.length() > 500
                    || description != null && description.length() > 32000)
                throw new IOException("课程标题、地点或说明过长，请检查 ICS 内容");
        }
    }

    private TimetableParser(ZoneId zone) { this.zone = zone; }

    public static Timetable parse(String source, ZoneId zone) throws IOException {
        if (source == null || source.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES)
            throw new IOException("ICS 文件过大，请导出单个学期的课表（不超过 1 MB）");
        validateEnvelope(source);
        try {
            return new TimetableParser(zone).read(source);
        } catch (RuntimeException exception) {
            throw new IOException(exception.getMessage() == null
                    ? "课表中的日期或重复规则无效" : exception.getMessage(), exception);
        }
    }

    private Timetable read(String source) throws IOException {
        LinkedHashMap<String, Entry> latest = new LinkedHashMap<>();
        int eventCount = 0;
        try (ICalReader reader = new ICalReader(source)) {
            reader.setDefaultTimezone(TimeZone.getTimeZone(zone));
            ICalendar calendar;
            while ((calendar = reader.readNext()) != null) {
                for (ParseWarning warning : reader.getWarnings()) {
                    // IANA TZIDs without a leading slash are common in real exports. The
                    // library reports 37/43 after successfully resolving these definitions.
                    if (Integer.valueOf(37).equals(warning.getCode())
                            || Integer.valueOf(43).equals(warning.getCode())) continue;
                    String property = warning.getPropertyName();
                    if (property == null || TIME_PROPERTIES.contains(property.toUpperCase(Locale.ROOT))
                            || property.toUpperCase(Locale.ROOT).startsWith("TZ"))
                        throw new IOException("ICS 日期或时区格式有误，请重新导出课表"
                                + (warning.getLineNumber() == null ? "" : "（第 " + warning.getLineNumber() + " 行）"));
                }
                for (VEvent event : calendar.getEvents()) {
                    checkBudget();
                    if (++eventCount > MAX_EVENTS) throw new IOException("ICS 日程数量过多，请分学期导入");
                    for (ICalProperty property : event.getProperties().values()) {
                        if (property.getParameters().getTimezoneId() != null)
                            throw new IOException("无法识别时区 " + property.getParameters().getTimezoneId()
                                    + "，请使用标准时区重新导出");
                    }
                    if (event.getRecurrenceId() != null && event.getRecurrenceId().getRange() != null)
                        throw new IOException("课表包含整组改期，请导出改期后的具体日程再导入");
                    String uid = event.getUid() == null ? "missing-" + eventCount : event.getUid().getValue();
                    String key = uid + "/" + (event.getRecurrenceId() == null ? "master"
                            : dateKey(event.getRecurrenceId().getValue()));
                    Entry old = latest.get(key);
                    if (old == null || revision(event, old.event) >= 0)
                        latest.put(key, new Entry(event, calendar, uid));
                }
            }
        }
        Map<String, Entry> masters = new HashMap<>();
        Map<String, Map<String, Entry>> overrides = new HashMap<>();
        for (Entry entry : latest.values()) {
            if (entry.event.getRecurrenceId() == null) masters.put(entry.uid, entry);
            else overrides.computeIfAbsent(entry.uid, ignored -> new HashMap<>())
                    .put(dateKey(entry.event.getRecurrenceId().getValue()), entry);
        }
        for (Entry entry : masters.values()) {
            if (cancelled(entry.event)) continue;
            expand(entry, overrides.getOrDefault(entry.uid, java.util.Collections.emptyMap()));
        }
        // Detached recurrence overrides are valid standalone exported instances.
        for (Entry entry : latest.values()) if (entry.event.getRecurrenceId() != null
                && !cancelled(entry.event)) {
            Entry master = masters.get(entry.uid);
            if (master == null || !cancelled(master.event))
                add(entry, master, requireStart(entry.event), null, false, "");
        }
        if (occurrences.isEmpty()) throw new IOException("所选 ICS 中没有可展示的课程");
        return new Timetable(occurrences, zone, latest.size());
    }

    private void expand(Entry entry, Map<String, Entry> overrides) throws IOException {
        VEvent event = entry.event;
        ICalDate start = requireStart(event);
        TimeZone timezone = recurrenceTimezone(entry);
        boolean open = false;
        long horizon = Long.MAX_VALUE;
        String recurrence = "";
        List<RecurrenceRule> rules = event.getProperties(RecurrenceRule.class);
        if (rules.size() > 1 || !event.getExceptionRules().isEmpty())
            throw new IOException("课程含多重或旧版重复规则，请导出具体上课日期后导入");
        if (!rules.isEmpty()) {
            Recurrence rule = rules.get(0).getValue();
            if (rule == null || rule.getFrequency() == null || rule.getFrequency().ordinal() < Frequency.DAILY.ordinal())
                throw new IOException("课程重复频率过高，请导出按天或按周的日程");
            if (rule.getInterval() != null && rule.getInterval() <= 0
                    || rule.getCount() != null && (rule.getCount() <= 0 || rule.getCount() > MAX_OCCURRENCES))
                throw new IOException("课程重复次数或间隔无效");
            open = rule.getCount() == null && rule.getUntil() == null;
            if (open) {
                horizon = indefiniteHorizon(entry, rule, timezone, overrides);
                int interval = rule.getInterval() == null ? 1 : rule.getInterval();
                recurrence = "每" + (interval == 1 ? "" : interval)
                        + (rule.getFrequency() == Frequency.WEEKLY ? "周" : "天") + "重复 · 未设置结束日期";
                java.util.TreeSet<String> exceptions = new java.util.TreeSet<>();
                for (ExceptionDates exception : event.getExceptionDates())
                    for (ICalDate date : exception.getValues()) exceptions.add(local(date, !start.hasTime()).toLocalDate().toString());
                for (Entry replacement : overrides.values())
                    exceptions.add(local(replacement.event.getRecurrenceId().getValue(), !start.hasTime()).toLocalDate().toString());
                if (!exceptions.isEmpty()) recurrence += "\n停课或改期：" + String.join("、", exceptions);
            }
        }
        TreeMap<Long, Date> starts = new TreeMap<>();
        starts.put(start.getTime(), start);
        DateIterator iterator = event.getDateIterator(timezone);
        while (iterator.hasNext()) {
            checkBudget();
            Date date = iterator.next();
            if (date.getTime() > horizon) break;
            if (++generated > MAX_OCCURRENCES) throw new IOException("重复课程过多，请导出单个学期后导入");
            starts.put(date.getTime(), date);
        }
        for (RecurrenceDates extra : event.getRecurrenceDates())
            for (ICalDate date : extra.getDates()) starts.put(date.getTime(), date);
        Set<String> excluded = new HashSet<>();
        for (ExceptionDates exception : event.getExceptionDates())
            for (ICalDate date : exception.getValues()) excluded.add(dateKey(date));
        for (Date date : starts.values()) {
            String key = occurrenceKey(date, !start.hasTime());
            if (!excluded.contains(key) && !overrides.containsKey(key))
                add(entry, null, date, null, open, recurrence);
        }
        for (RecurrenceDates extra : event.getRecurrenceDates()) for (Period period : extra.getPeriods()) {
            ICalDate date = period.getStartDate();
            if (date == null || excluded.contains(dateKey(date)) || overrides.containsKey(dateKey(date))) continue;
            Date end = period.getEndDate();
            if (end == null && period.getDuration() != null)
                end = period.getDuration().add(date);
            if (end == null) throw new IOException("额外课时缺少结束时间");
            add(entry, null, date, end, false, "");
        }
    }

    /** An unbounded plain daily/weekly pattern needs only a full cycle after all exceptions.
     * Complex unbounded calendars have no finite set of dates to summarize; reject explicitly.
     */
    private long indefiniteHorizon(Entry entry, Recurrence rule, TimeZone timezone,
            Map<String, Entry> overrides) throws IOException {
        TimeZone display = TimeZone.getTimeZone(zone);
        if ((rule.getFrequency() != Frequency.DAILY && rule.getFrequency() != Frequency.WEEKLY)
                || !rule.getByMonth().isEmpty() || !rule.getByMonthDay().isEmpty()
                || !rule.getByYearDay().isEmpty() || !rule.getByWeekNo().isEmpty()
                || !rule.getBySetPos().isEmpty() || !rule.getByHour().isEmpty()
                || !rule.getByMinute().isEmpty() || !rule.getBySecond().isEmpty()
                || !rule.getXRules().isEmpty()
                || rule.getByDay().stream().anyMatch(day -> day.getNum() != null)
                || ((timezone.useDaylightTime() || display.useDaylightTime()) && !timezone.hasSameRules(display)))
            throw new IOException("此重复课程需要结束日期，请导出单个学期的 ICS 后导入");
        int interval = rule.getInterval() == null ? 1 : rule.getInterval();
        if (interval > 366) throw new IOException("重复间隔过长，请导出单个学期后导入");
        long last = requireStart(entry.event).getTime();
        for (ExceptionDates exception : entry.event.getExceptionDates())
            for (ICalDate date : exception.getValues()) last = Math.max(last, date.getTime());
        for (Entry replacement : overrides.values())
            last = Math.max(last, replacement.event.getRecurrenceId().getValue().getTime());
        return Instant.ofEpochMilli(last).atZone(zone).plusDays(14L * interval + 7).toInstant().toEpochMilli();
    }

    private void add(Entry entry, Entry master, Date startDate, Date explicitEnd,
            boolean open, String recurrence) throws IOException {
        checkBudget();
        if (occurrences.size() >= MAX_OCCURRENCES) throw new IOException("课程数量过多，请分学期导入");
        VEvent event = entry.event;
        ICalDate original = requireStart(event);
        boolean allDay = !original.hasTime();
        LocalDateTime start = local(startDate, allDay);
        LocalDateTime end;
        if (explicitEnd != null) end = local(explicitEnd, allDay);
        else if (allDay) {
            if (event.getDateEnd() != null && event.getDateEnd().getValue().hasTime()
                    || event.getDuration() != null && event.getDuration().getValue().hasTime())
                throw new IOException("全天课程的起止日期类型不一致");
            VEvent durationSource = event.getDateEnd() == null && event.getDuration() == null && master != null
                    ? master.event : event;
            long days = durationSource.getDateEnd() != null
                    ? ChronoUnit.DAYS.between(rawDate(requireStart(durationSource)), rawDate(durationSource.getDateEnd().getValue()))
                    : durationSource.getDuration() != null ? durationSource.getDuration().getValue().toMillis() / 86_400_000L : 1;
            end = start.plusDays(days);
        } else if (event.getDateEnd() != null) {
            if (!event.getDateEnd().getValue().hasTime()) throw new IOException("课程起止日期类型不一致");
            long length = event.getDateEnd().getValue().getTime() - original.getTime();
            end = local(new Date(Math.addExact(startDate.getTime(), length)), false);
        } else if (event.getDuration() != null) {
            biweekly.util.Duration duration = event.getDuration().getValue();
            java.util.Calendar endCalendar = java.util.Calendar.getInstance(recurrenceTimezone(entry));
            endCalendar.setTime(startDate);
            if (duration.isPrior()) throw new IOException("课程时长不能为负数");
            int days = Math.addExact(duration.getWeeks() == null ? 0 : Math.multiplyExact(duration.getWeeks(), 7),
                    duration.getDays() == null ? 0 : duration.getDays());
            endCalendar.add(java.util.Calendar.DAY_OF_MONTH, days);
            long seconds = (duration.getHours() == null ? 0L : duration.getHours() * 3600L)
                    + (duration.getMinutes() == null ? 0L : duration.getMinutes() * 60L)
                    + (duration.getSeconds() == null ? 0L : duration.getSeconds());
            end = local(new Date(Math.addExact(endCalendar.getTimeInMillis(), Math.multiplyExact(seconds, 1000))), false);
        } else if (master != null) {
            VEvent base = master.event;
            if (base.getDateEnd() == null && base.getDuration() == null)
                throw new IOException("课程缺少结束时间，请导出包含起止时间的课表");
            long length = base.getDateEnd() == null ? base.getDuration().getValue().toMillis()
                    : base.getDateEnd().getValue().getTime() - requireStart(base).getTime();
            end = local(new Date(Math.addExact(startDate.getTime(), length)), false);
        } else throw new IOException("课程缺少结束时间，请导出包含起止时间的课表");
        long days = Duration.between(start, end).toDays();
        if (!end.isAfter(start) || days > (allDay ? 366 : 7))
            throw new IOException("课程结束时间无效或单次课程跨度过长");
        Entry fallback = master == null ? entry : master;
        String title = entry.title == null ? fallback.title : entry.title;
        String location = entry.location == null ? fallback.location : entry.location;
        String description = entry.description == null ? fallback.description : entry.description;
        occurrences.add(new Timetable.Occurrence(title == null || title.isEmpty() ? "未命名课程" : title,
                location == null ? "" : location, description == null ? "" : description,
                start, end, allDay, open, recurrence));
    }

    private TimeZone recurrenceTimezone(Entry entry) {
        DateStart property = entry.event.getDateStart();
        if (entry.calendar.getTimezoneInfo().isFloating(property)) return TimeZone.getTimeZone(zone);
        TimezoneAssignment assignment = entry.calendar.getTimezoneInfo().getTimezone(property);
        return assignment == null ? TimeZone.getTimeZone("UTC") : assignment.getTimeZone();
    }

    private LocalDateTime local(Date date, boolean allDay) {
        // The recurrence library represents DATE-only iterator values in the process timezone.
        return allDay ? Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate().atStartOfDay()
                : Instant.ofEpochMilli(date.getTime()).atZone(zone).toLocalDateTime();
    }
    private static LocalDate rawDate(ICalDate date) {
        if (date.getRawComponents() == null)
            return Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate();
        var raw = date.getRawComponents();
        return LocalDate.of(raw.getYear(), raw.getMonth(), raw.getDate());
    }
    private static String occurrenceKey(Date date, boolean allDay) {
        return allDay ? Instant.ofEpochMilli(date.getTime()).atZone(ZoneId.systemDefault()).toLocalDate().toString()
                : Long.toString(date.getTime());
    }
    private static String dateKey(ICalDate date) {
        return date.hasTime() ? Long.toString(date.getTime()) : rawDate(date).toString();
    }
    private static ICalDate requireStart(VEvent event) throws IOException {
        if (event.getDateStart() == null || event.getDateStart().getValue() == null)
            throw new IOException("课程缺少开始时间，请重新导出完整课表");
        return event.getDateStart().getValue();
    }
    private static boolean cancelled(VEvent event) {
        return event.getStatus() != null && event.getStatus().isCancelled();
    }
    private static int revision(VEvent one, VEvent two) {
        int a = one.getSequence() == null ? 0 : one.getSequence().getValue();
        int b = two.getSequence() == null ? 0 : two.getSequence().getValue();
        if (a != b) return Integer.compare(a, b);
        long x = one.getLastModified() != null ? one.getLastModified().getValue().getTime()
                : one.getDateTimeStamp() == null ? 0 : one.getDateTimeStamp().getValue().getTime();
        long y = two.getLastModified() != null ? two.getLastModified().getValue().getTime()
                : two.getDateTimeStamp() == null ? 0 : two.getDateTimeStamp().getValue().getTime();
        return Long.compare(x, y);
    }
    private void checkBudget() throws IOException {
        if (Thread.currentThread().isInterrupted() || System.nanoTime() - started > MAX_PARSE_NANOS)
            throw new IOException("课表解析耗时过长，请导出单个学期后重试");
    }
    private static String clean(String value) {
        return value == null ? "" : value.replace("\r\n", "\n").replace('\r', '\n')
                .replace('\u0000', ' ').trim();
    }
    private static void validateEnvelope(String source) throws IOException {
        ArrayList<String> stack = new ArrayList<>();
        boolean found = false;
        for (String raw : source.replace("\uFEFF", "").split("\\r\\n|\\n|\\r")) {
            if (raw.startsWith(" ") || raw.startsWith("\t")) continue;
            String line = raw.trim().toUpperCase(Locale.ROOT);
            if (line.startsWith("BEGIN:")) {
                String component = line.substring(6);
                if (stack.isEmpty() && !"VCALENDAR".equals(component))
                    throw new IOException("请选择标准 ICS 课表文件");
                stack.add(component);
                if (stack.size() > 16) throw new IOException("ICS 内容层级过多");
                if ("VCALENDAR".equals(component)) found = true;
            } else if (line.startsWith("END:")) {
                if (stack.isEmpty() || !stack.remove(stack.size() - 1).equals(line.substring(4)))
                    throw new IOException("ICS 文件不完整，请重新导出");
            } else if (!line.isEmpty() && stack.isEmpty()) throw new IOException("请选择标准 ICS 课表文件");
        }
        if (!found || !stack.isEmpty()) throw new IOException("ICS 文件不完整，请重新导出");
    }
}
