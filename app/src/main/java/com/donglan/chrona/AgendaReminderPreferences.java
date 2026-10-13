package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;

final class AgendaReminderPreferences {
    static SharedPreferences prefs(Context c) { return c.getSharedPreferences("agenda_reminder", Context.MODE_PRIVATE); }
    static boolean enabled(Context c) { return prefs(c).getBoolean("enabled", true); }
    static boolean includesSystemCalendar(Context c) { return prefs(c).getBoolean("include_system", false); }
    static boolean atomicChrona(Context c) { return prefs(c).getBoolean("atomic_chrona", true); }
    static boolean atomicSystem(Context c) { return prefs(c).getBoolean("atomic_system", true); }
    static boolean atomicWallpaper(Context c) { return prefs(c).getBoolean("atomic_wallpaper", true); }
    static void setAtomicWallpaper(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("atomic_wallpaper", enabled).apply(); AgendaReminder.requestRefresh(c);
    }
    static boolean useAtomic(Context c, boolean system, boolean available) {
        return enabled(c) && available && (!system || includesSystemCalendar(c))
                && (system ? atomicSystem(c) : atomicChrona(c));
    }
    static void setAtomicChrona(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("atomic_chrona", enabled).apply(); AgendaReminder.requestRefresh(c);
    }
    static void setAtomicSystem(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("atomic_system", enabled).apply(); AgendaReminder.requestRefresh(c);
    }
    static void setEnabled(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("enabled", enabled).apply();
        AgendaReminderJobService.schedule(c); AgendaReminder.requestRefresh(c);
    }
    static void setIncludesSystemCalendar(Context c, boolean enabled) {
        prefs(c).edit().putBoolean("include_system", enabled).apply(); AgendaReminder.requestRefresh(c);
    }
    private AgendaReminderPreferences() { }
}
