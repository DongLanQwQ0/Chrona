package com.donglan.chrona;

import android.content.Context;
import org.json.JSONException;
import org.json.JSONObject;

/** Explicit portable preferences. Credentials and notification runtime keys never enter backups. */
final class FeaturePreferenceBackup {
    private record Setting(String file, String key, boolean defaultValue) { }
    private static final Setting[] SETTINGS = {
        new Setting("agenda_reminder", "enabled", true),
        new Setting("agenda_reminder", "include_system", false),
        new Setting("agenda_reminder", "atomic_chrona", true),
        new Setting("agenda_reminder", "atomic_system", true),
        new Setting("agenda_reminder", "atomic_wallpaper", true),
        new Setting("agenda_reminder", "capsule_countdown", true),
        new Setting("agenda_reminder", "atomic_seconds", true),
        new Setting("home_timeline", "todos_expanded", false),
        new Setting("CalendarOverviewActivity", "system", false),
        new Setting("CalendarOverviewActivity", "week_export_index", false)
    };

    static JSONObject export(Context context) throws JSONException {
        JSONObject result = new JSONObject();
        for (Setting setting : SETTINGS) {
            JSONObject file = result.optJSONObject(setting.file);
            if (file == null) { file = new JSONObject(); result.put(setting.file, file); }
            file.put(setting.key, context.getSharedPreferences(setting.file, Context.MODE_PRIVATE)
                    .getBoolean(setting.key, setting.defaultValue));
        }
        return result;
    }

    static void validate(JSONObject root) throws JSONException {
        if (!root.has("features")) return;
        if (!(root.opt("features") instanceof JSONObject features)) throw new JSONException("功能设置格式无效");
        for (Setting setting : SETTINGS) {
            if (!features.has(setting.file)) continue;
            if (!(features.opt(setting.file) instanceof JSONObject file)) throw new JSONException("功能设置分组无效");
            if (file.has(setting.key) && !(file.opt(setting.key) instanceof Boolean))
                throw new JSONException("功能开关格式无效：" + setting.key);
        }
    }

    static void apply(Context context, JSONObject features) {
        if (features == null) return;
        for (Setting setting : SETTINGS) {
            JSONObject file = features.optJSONObject(setting.file);
            if (file != null && file.has(setting.key)) context.getSharedPreferences(setting.file, Context.MODE_PRIVATE)
                    .edit().putBoolean(setting.key, file.optBoolean(setting.key)).apply();
        }
    }

    static boolean reminderSetting(String key) {
        for (Setting setting : SETTINGS) if (setting.file.equals("agenda_reminder") && setting.key.equals(key)) return true;
        return false;
    }
    private FeaturePreferenceBackup() { }
}
