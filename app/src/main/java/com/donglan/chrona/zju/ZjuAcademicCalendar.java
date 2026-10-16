package com.donglan.chrona.zju;

import android.text.Html;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Official-calendar expansion, deliberately limited to verified academic years. */
public final class ZjuAcademicCalendar {
    public static final String SOURCE = "https://ugrs.zju.edu.cn/2026/0710/c28218a3187939/page.htm";
    public static final String PDF_SOURCE = "https://ugrs.zju.edu.cn/_upload/article/files/28/cc/ce06b4d9483eb58be317e94a7749/eeeb30e1-336a-4d3f-8aca-5890d2cc2ac4.pdf";
    public static final String ENGLISH_SOURCE = "https://www.zju.edu.cn/english/19600/list.psp";
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("Asia/Shanghai");
    // Official xskbcx.js arrKbjcsj: thirteen teaching periods, not exam times in kcb.
    private static final String[] START = {"08:00", "08:50", "10:00", "10:50", "11:40", "13:25", "14:15", "15:05", "16:15", "17:05", "18:50", "19:40", "20:30"};
    private static final String[] END = {"08:45", "09:35", "10:45", "11:35", "12:25", "14:10", "15:00", "15:50", "17:00", "17:50", "19:35", "20:25", "21:15"};
    private static final Pattern WEEKS = Pattern.compile("第\\s*(\\d{1,2})\\s*[-—~至]\\s*(\\d{1,2})\\s*周");
    private static final Pattern SINGLE_WEEK = Pattern.compile("第\\s*(\\d{1,2})\\s*周");

    private ZjuAcademicCalendar() { }

    public static List<ZjuSnapshot.Lesson> expand(String year, String quarter, JSONArray rows,
                                                  List<String> warnings) throws JSONException {
        List<ZjuSnapshot.Lesson> result = new ArrayList<>();
        LocalDate monday = firstMonday(year, quarter);
        if (monday == null) {
            warnings.add("教务课表的 " + year + " " + quarter + " 学期尚无已核实校历，未写入日程");
            return result;
        }
        Set<String> keys = new HashSet<>();
        int invalid = 0;
        for (int i = 0; i < rows.length(); i++) {
            JSONObject row = rows.getJSONObject(i);
            String[] lines = row.optString("kcb", "").replace("zwf", "<br>").split("(?i)<br\\s*/?\\s*>");
            if (lines.length < 2) { invalid++; continue; }
            String title = text(lines[0]), rule = text(lines[1]);
            Matcher week = WEEKS.matcher(rule);
            int from, to;
            if (week.find()) { from = Integer.parseInt(week.group(1)); to = Integer.parseInt(week.group(2)); }
            else {
                week = SINGLE_WEEK.matcher(rule);
                if (!week.find()) { invalid++; continue; }
                from = to = Integer.parseInt(week.group(1));
            }
            int period = row.optInt("djj", -1), count = row.optInt("skcd", -1), day = row.optInt("xqj", -1);
            String parity = row.optString("dsz", "");
            if (title.isEmpty() || from < 1 || to < from || to > 10 || period < 1 || count < 1 || period + count - 1 > START.length
                    || day < 1 || day > 7 || !(parity.equals("0") || parity.equals("1") || parity.equals("2"))) { invalid++; continue; }
            String location = lines.length > 3 ? text(lines[3]) : "";
            int exam = location.indexOf("考试");
            if (exam >= 0) location = location.substring(0, exam).trim();
            for (int w = from; w <= to; w++) {
                if ((parity.equals("0") && w % 2 == 0) || (parity.equals("1") && w % 2 != 0)) continue;
                LocalDate teachingDate = monday.plusWeeks(w - 1).plusDays(day - 1);
                if (("秋".equals(quarter) && teachingDate.isAfter(LocalDate.of(2026, 11, 8)))
                        || ("冬".equals(quarter) && teachingDate.isAfter(LocalDate.of(2027, 1, 5)))) continue;
                LocalDate date = actualDate(teachingDate);
                if (date == null) continue;
                long start = date.atTime(LocalTime.parse(START[period - 1])).atZone(SCHOOL_ZONE).toInstant().toEpochMilli();
                long end = date.atTime(LocalTime.parse(END[period + count - 2])).atZone(SCHOOL_ZONE).toInstant().toEpochMilli();
                String key = year + "/" + quarter + "/" + title + "/" + location + "/" + start;
                if (keys.add(key)) result.add(new ZjuSnapshot.Lesson(key, title, location, year + " " + quarter, start, end));
            }
        }
        if (invalid > 0) warnings.add("教务课表有 " + invalid + " 项排课缺少可识别的周次或节次，已跳过");
        return result;
    }

    private static LocalDate firstMonday(String year, String quarter) {
        if (!"2026-2027".equals(year)) return null;
        if ("秋".equals(quarter)) return LocalDate.of(2026, 9, 14);
        if ("冬".equals(quarter)) return LocalDate.of(2026, 11, 9);
        return null;
    }

    /** Date swaps in the ordinary undergraduate campus calendar; Haining has its own calendar. */
    static LocalDate actualDate(LocalDate teachingDate) {
        if (teachingDate.equals(LocalDate.of(2026, 10, 6))) return LocalDate.of(2026, 9, 20);
        if (teachingDate.equals(LocalDate.of(2026, 10, 7))) return LocalDate.of(2026, 10, 10);
        if (teachingDate.equals(LocalDate.of(2026, 10, 2))) return LocalDate.of(2026, 10, 17);
        if (teachingDate.equals(LocalDate.of(2026, 12, 31))) return LocalDate.of(2027, 1, 4);
        if (teachingDate.equals(LocalDate.of(2026, 9, 20)) || teachingDate.equals(LocalDate.of(2026, 10, 10))
                || teachingDate.equals(LocalDate.of(2026, 10, 17)) || teachingDate.equals(LocalDate.of(2027, 1, 4))) return null;
        if (between(teachingDate, "2026-09-25", "2026-09-27") || between(teachingDate, "2026-10-01", "2026-10-07")
                || between(teachingDate, "2026-10-24", "2026-10-25") || teachingDate.equals(LocalDate.of(2027, 1, 1))) return null;
        return teachingDate;
    }
    private static boolean between(LocalDate date, String first, String last) {
        return !date.isBefore(LocalDate.parse(first)) && !date.isAfter(LocalDate.parse(last));
    }
    private static String text(String html) { return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim(); }
}
