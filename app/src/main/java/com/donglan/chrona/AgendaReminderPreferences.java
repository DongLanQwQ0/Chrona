package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;

final class AgendaReminderPreferences {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("agenda_reminder", Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", true); }
    static boolean includesSystemCalendar(Context c) { return prefs(c).getBoolean("include_system", false); }
    static void setEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("enabled", enabled).apply();
        AgendaReminderJobService.schedule(c); AgendaReminder.requestRefresh(c);
    }
    static void setIncludesSystemCalendar(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("include_system", enabled).apply(); AgendaReminder.requestRefresh(c);
    }
    private AgendaReminderPreferences() { }
}
