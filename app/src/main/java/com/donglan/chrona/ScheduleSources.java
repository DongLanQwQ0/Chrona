package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;

/** Global source permissions; page-specific inclusion switches still apply independently. */
public final class ScheduleSources {
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("schedule_sources", Context.MODE_PRIVATE);
    }

    /** Existing device calendars remain enabled until explicitly disabled. */
    public static boolean allowsCalendar(Context context, long calendarId) {
        return prefs(context).getBoolean("calendar:" + calendarId, true);
    }
    public static boolean importedEnabled(Context context) { return prefs(context).getBoolean("imported", true); }
    public static boolean zjuEnabled(Context context) { return prefs(context).getBoolean("zju", true); }
    public static long revision(Context context) { return prefs(context).getLong("revision", 0L); }
    /** Tie-break preference for confirmed duplicates only; it never hides unrelated secondary events. */
    public static String primary(Context context) { return prefs(context).getString("primary", "all"); }
    public static void setCalendarEnabled(Context context, long id, boolean enabled) {
        if (id <= 0) throw new IllegalArgumentException("Calendar ID must be positive");
        setBoolean(context, "calendar:" + id, enabled);
    }
    public static void setImportedEnabled(Context context, boolean enabled) { setBoolean(context, "imported", enabled); }
    public static void setZjuEnabled(Context context, boolean enabled) { setBoolean(context, "zju", enabled); }
    public static synchronized void setPrimary(Context context, String value) {
        if (!("all".equals(value) || "imported".equals(value) || "zju".equals(value)
                || validSystem(value))) throw new IllegalArgumentException("Unknown source");
        SharedPreferences preferences = prefs(context);
        if (value.equals(primary(context))) return;
        save(context, preferences.edit().putString("primary", value));
    }
    private static boolean validSystem(String value) {
        if (value == null || !value.startsWith("system:")) return false;
        try { return Long.parseLong(value.substring(7)) > 0; }
        catch (NumberFormatException invalid) { return false; }
    }
    private static synchronized void setBoolean(Context context, String key, boolean value) {
        SharedPreferences preferences = prefs(context);
        if (preferences.getBoolean(key, true) == value) return;
        save(context, preferences.edit().putBoolean(key, value));
    }
    private static void save(Context context, SharedPreferences.Editor editor) {
        editor.putLong("revision", revision(context) + 1L).apply();
        AgendaWidgetProvider.requestRefresh(context);
        WeekCalendarWidgetProvider.requestRefresh(context);
        AgendaReminder.requestRefresh(context);
    }
    private ScheduleSources() { }
}
