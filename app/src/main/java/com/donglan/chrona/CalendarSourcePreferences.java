package com.donglan.chrona;

import android.content.Context;

/** Same store used by CalendarOverviewActivity.getPreferences, shared with settings. */
final class CalendarSourcePreferences {
    static boolean includesSystem(Context context) {
        return context.getSharedPreferences("CalendarOverviewActivity", Context.MODE_PRIVATE).getBoolean("system", false);
    }
    static void setIncludesSystem(Context context, boolean enabled) {
        context.getSharedPreferences("CalendarOverviewActivity", Context.MODE_PRIVATE).edit().putBoolean("system", enabled).apply();
    }
    private CalendarSourcePreferences() { }
}
