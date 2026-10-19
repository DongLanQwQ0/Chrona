package com.donglan.chrona.data;

import org.json.JSONArray;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** The supported recurrence vocabulary; dates are inclusive, count includes the first occurrence. */
public final class RepeatRule {
    public final String frequency;
    public final int interval;
    public final List<Integer> weekdays;
    public final LocalDate until;
    public final Integer count;

    public RepeatRule(String frequency, int interval, List<Integer> weekdays,
            LocalDate until, Integer count) {
        if (!java.util.Arrays.asList("daily", "weekly", "monthly", "yearly").contains(frequency))
            throw new IllegalArgumentException("不支持的重复频率");
        if (interval < 1 || interval > 999) throw new IllegalArgumentException("重复间隔应为 1–999");
        if (count != null && (count < 1 || count > 100000))
            throw new IllegalArgumentException("重复次数应为 1–100000");
        if (count != null && until != null) throw new IllegalArgumentException("结束日期和重复次数只能选择一项");
        ArrayList<Integer> days = new ArrayList<>();
        if (weekdays != null) for (Integer day : weekdays) {
            if (day == null || day < 1 || day > 7 || days.contains(day))
                throw new IllegalArgumentException("重复星期无效");
            days.add(day);
        }
        if (!days.isEmpty() && !"weekly".equals(frequency))
            throw new IllegalArgumentException("只有每周重复可选择星期");
        Collections.sort(days);
        this.frequency = frequency; this.interval = interval;
        this.weekdays = Collections.unmodifiableList(days); this.until = until; this.count = count;
    }

    public static RepeatRule fromJson(JSONObject value) {
        if (value == null) return null;
        try {
            List<Integer> days = new ArrayList<>();
            JSONArray array = value.getJSONArray("weekdays");
            for (int i = 0; i < array.length(); i++) days.add(exactInt(array.get(i)));
            return new RepeatRule(value.getString("frequency"), exactInt(value.get("interval")), days,
                    value.isNull("until") ? null : LocalDate.parse(value.getString("until")),
                    value.isNull("count") ? null : exactInt(value.get("count")));
        } catch (Exception invalid) { throw new IllegalArgumentException("重复规则无效：" + invalid.getMessage(), invalid); }
    }

    private static int exactInt(Object raw) {
        if (!(raw instanceof Integer) && !(raw instanceof Long)) throw new IllegalArgumentException("重复数值必须为整数");
        return Math.toIntExact(((Number) raw).longValue());
    }

    public JSONObject toJson() {
        try {
            return new JSONObject().put("frequency", frequency).put("interval", interval)
                    .put("weekdays", new JSONArray(weekdays))
                    .put("until", until == null ? JSONObject.NULL : until.toString())
                    .put("count", count == null ? JSONObject.NULL : count);
        } catch (org.json.JSONException exception) { throw new IllegalStateException(exception); }
    }

    public void validateAnchor(long anchor, String zone) {
        LocalDate first = Instant.ofEpochMilli(anchor).atZone(ZoneId.of(zone)).toLocalDate();
        if (until != null && until.isBefore(first)) throw new IllegalArgumentException("重复结束日期早于首次日程");
        if (!weekdays.isEmpty() && !weekdays.contains(first.getDayOfWeek().getValue()))
            throw new IllegalArgumentException("首次日程与重复星期不一致");
    }

    public String rrule(String zone, boolean allDay) {
        StringBuilder rule = new StringBuilder("FREQ=").append(frequency.toUpperCase(java.util.Locale.ROOT))
                .append(";INTERVAL=").append(interval).append(";WKST=MO");
        if (!weekdays.isEmpty()) {
            String[] names = {"MO", "TU", "WE", "TH", "FR", "SA", "SU"};
            List<String> days = new ArrayList<>();
            for (int day : weekdays) days.add(names[day - 1]);
            rule.append(";BYDAY=").append(String.join(",", days));
        }
        if (count != null) rule.append(";COUNT=").append(count);
        if (until != null) {
            rule.append(";UNTIL=");
            if (allDay) rule.append(until.format(DateTimeFormatter.BASIC_ISO_DATE));
            else rule.append(DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(java.time.ZoneOffset.UTC)
                    .format(until.plusDays(1).atStartOfDay(ZoneId.of(zone)).toInstant().minusSeconds(1)));
        }
        return rule.toString();
    }

    public String label() {
        String unit = "daily".equals(frequency) ? "天" : "weekly".equals(frequency) ? "周"
                : "monthly".equals(frequency) ? "月" : "年";
        String text = "每" + (interval == 1 ? "" : interval) + unit;
        if (!weekdays.isEmpty()) {
            String[] names = {"一", "二", "三", "四", "五", "六", "日"};
            List<String> days = new ArrayList<>();
            for (int day : weekdays) days.add(names[day - 1]);
            text += " " + String.join("、", days);
        }
        return text + (count != null ? " · 共 " + count + " 次" : until != null ? " · 至 " + until : "");
    }
}
