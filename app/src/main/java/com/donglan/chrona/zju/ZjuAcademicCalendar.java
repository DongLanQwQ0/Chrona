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
    private static final Pattern WEEKS = Pattern.compile("第\\s*(\\d{1,2})\\s*[-—~至]\\s*(\\d{1,2})\\s*周");
    private static final Pattern SINGLE_WEEK = Pattern.compile("第\\s*(\\d{1,2})\\s*周");

    private ZjuAcademicCalendar() { }

    public static List<ZjuSnapshot.Lesson> expand(String year, String quarter, JSONArray rows,
                                                  List<String> warnings) throws JSONException {
        return expand(ZjuCalendarConfig.ordinary(), year, quarter, rows, warnings);
    }

    public static List<ZjuSnapshot.Lesson> expand(ZjuCalendarConfig config, String year, String quarter, JSONArray rows, List<String> warnings) throws JSONException {
        List<ZjuSnapshot.Lesson> result = new ArrayList<>();
        ZjuCalendarConfig.Term term = config.term(year, quarter);
        LocalDate monday = term == null ? null : term.start;
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
            if (title.isEmpty() || from < 1 || to < from || to > 53 || period < 1 || count < 1 || period + count - 1 > config.starts.size()
                    || day < 1 || day > 7 || !(parity.equals("0") || parity.equals("1") || parity.equals("2"))) { invalid++; continue; }
            String location = lines.length > 3 ? text(lines[3]) : "";
            int exam = location.indexOf("考试");
            if (exam >= 0) location = location.substring(0, exam).trim();
            for (int w = from; w <= to; w++) {
                if ((parity.equals("0") && w % 2 == 0) || (parity.equals("1") && w % 2 != 0)) continue;
                LocalDate teachingDate = monday.plusWeeks(w - 1).plusDays(day - 1);
                if (teachingDate.isAfter(term.teachingEnd)) continue;
                LocalDate date = config.actualDate(teachingDate);
                if (date == null) continue;
                long start = date.atTime(LocalTime.parse(config.starts.get(period - 1))).atZone(SCHOOL_ZONE).toInstant().toEpochMilli();
                long end = date.atTime(LocalTime.parse(config.ends.get(period + count - 2))).atZone(SCHOOL_ZONE).toInstant().toEpochMilli();
                String key = year + "/" + quarter + "/" + title + "/" + location + "/" + start;
                if (keys.add(key)) result.add(new ZjuSnapshot.Lesson(key, title, location, year + " " + quarter, start, end));
            }
        }
        if (invalid > 0) warnings.add("教务课表有 " + invalid + " 项排课缺少可识别的周次或节次，已跳过");
        return result;
    }

    static LocalDate actualDate(LocalDate teachingDate) { return ZjuCalendarConfig.ordinary().actualDate(teachingDate); }
    private static String text(String html) { return Html.fromHtml(html, Html.FROM_HTML_MODE_LEGACY).toString().trim(); }
}
