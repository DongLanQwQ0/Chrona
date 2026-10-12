package com.donglan.chrona;

import android.content.Context;

/** Each preset owns its direction, including after process death. */
final class ScheduleOrderPreferences {
    static boolean oldestFirst(Context context, int tab) {
        return context.getSharedPreferences("schedule_order", Context.MODE_PRIVATE)
                .getBoolean("tab_" + tab, tab == 0);
    }

    static void setOldestFirst(Context context, int tab, boolean value) {
        context.getSharedPreferences("schedule_order", Context.MODE_PRIVATE).edit()
                .putBoolean("tab_" + tab, value).apply();
    }
    private ScheduleOrderPreferences() { }
}
