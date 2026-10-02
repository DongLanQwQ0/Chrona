package com.donglan.chrona;

import android.content.Context;

/** Persistent local settings for the desktop agenda. */
final class WidgetPreferences {
    static final int DEFAULT_PREVIEW_MINUTES = 22 * 60;
    private static final String KEY = "widget_preview_minutes";
    private static final String KEY_DANMAKU_ENABLED = "widget_danmaku_enabled";

    private WidgetPreferences() { }

    static boolean danmakuEnabled(Context context) {
        return context.getSharedPreferences("home_timeline", Context.MODE_PRIVATE)
                .getBoolean(KEY_DANMAKU_ENABLED, true);
    }

    static void setDanmakuEnabled(Context context, boolean enabled) {
        context.getSharedPreferences("home_timeline", Context.MODE_PRIVATE).edit()
                .putBoolean(KEY_DANMAKU_ENABLED, enabled).apply();
        AgendaWidgetProvider.requestRefresh(context);
    }

    static int previewMinutes(Context context) {
        return Math.max(0, Math.min(1439, context.getSharedPreferences("home_timeline",
                Context.MODE_PRIVATE).getInt(KEY, DEFAULT_PREVIEW_MINUTES)));
    }

    static void setPreviewMinutes(Context context, int minutes) {
        context.getSharedPreferences("home_timeline", Context.MODE_PRIVATE).edit()
                .putInt(KEY, Math.max(0, Math.min(1439, minutes))).apply();
        AgendaWidgetProvider.requestRefresh(context);
    }
}
