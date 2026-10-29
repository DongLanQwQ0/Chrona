package com.donglan.chrona.ai;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.Locale;

/** User-owned schedule interpretation settings, independent of connection credentials. */
public final class SchedulePreferences {
    public static final int DEFAULT_WORK_START = 10 * 60;
    public static final int DEFAULT_WORK_END = 22 * 60;
    public static final int MAX_PROMPT_CHARS = 8000;
    private static final String STORE = "chrona_schedule_preferences";
    public final int workStartMinutes;
    public final int workEndMinutes;
    public final String customPrompt;
    public final String classroomPrompt;
    public final boolean enhancedClassroom;

    public SchedulePreferences(int start, int end, String prompt, String classroomPrompt) {
        this(start, end, prompt, classroomPrompt, false);
    }

    public SchedulePreferences(int start, int end, String prompt, String classroomPrompt,
            boolean enhancedClassroom) {
        if (start < 0 || start >= 1440 || end < 0 || end >= 1440 || start == end)
            throw new IllegalArgumentException("工作时间起止必须不同，且为有效钟点");
        if (prompt == null || prompt.length() > MAX_PROMPT_CHARS
                || classroomPrompt == null || classroomPrompt.length() > MAX_PROMPT_CHARS)
            throw new IllegalArgumentException("自定义提示词最多 8000 字符");
        workStartMinutes = start;
        workEndMinutes = end;
        customPrompt = prompt;
        this.classroomPrompt = classroomPrompt;
        this.enhancedClassroom = enhancedClassroom;
    }

    public static SchedulePreferences load(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE);
        return new SchedulePreferences(Integer.parseInt(prefs.getString("workStartMinutes", Integer.toString(DEFAULT_WORK_START))),
                Integer.parseInt(prefs.getString("workEndMinutes", Integer.toString(DEFAULT_WORK_END))),
                prefs.getString("customPrompt", ""), prefs.getString("classroomPrompt", ""),
                prefs.getBoolean("enhancedClassroom", false));
    }

    public void save(Context context) {
        context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
                .putString("workStartMinutes", Integer.toString(workStartMinutes))
                .putString("workEndMinutes", Integer.toString(workEndMinutes))
                .putString("customPrompt", customPrompt)
                .putString("classroomPrompt", classroomPrompt)
                .putBoolean("enhancedClassroom", enhancedClassroom).apply();
    }

    public String systemPrompt() {
        return SchedulePrompt.configuredSystem(customPrompt, time(workStartMinutes), time(workEndMinutes));
    }

    public JSONObject toJson() throws JSONException {
        return new JSONObject().put("workStartMinutes", workStartMinutes)
                .put("workEndMinutes", workEndMinutes).put("customPrompt", customPrompt)
                .put("classroomPrompt", classroomPrompt).put("enhancedClassroom", enhancedClassroom);
    }

    public static SchedulePreferences fromJson(JSONObject value) throws JSONException {
        if (value == null || !(value.opt("customPrompt") instanceof String)
                || !(value.opt("classroomPrompt") instanceof String))
            throw new JSONException("日程解析偏好格式无效");
        if (value.has("enhancedClassroom") && !(value.opt("enhancedClassroom") instanceof Boolean))
            throw new JSONException("增强整理设置格式无效");
        for (String key : new String[]{"workStartMinutes", "workEndMinutes"}) {
            Object number = value.opt(key);
            if (!(number instanceof Number) || ((Number) number).doubleValue() != ((Number) number).intValue())
                throw new JSONException("工作时间格式无效");
        }
        try {
            return new SchedulePreferences(value.getInt("workStartMinutes"), value.getInt("workEndMinutes"),
                    value.getString("customPrompt"), value.getString("classroomPrompt"),
                    value.optBoolean("enhancedClassroom", false));
        } catch (IllegalArgumentException exception) {
            throw new JSONException(exception.getMessage());
        }
    }

    public static String time(int minutes) {
        return String.format(Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }
}
