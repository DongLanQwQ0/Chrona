package com.donglan.chrona;

import android.content.Context;

/** Persistent local setting for the desktop agenda's evening preview. */
final class WidgetPreferences {
    static final int DEFAULT_PREVIEW_MINUTES = 22 * 60;
    private static final String KEY = "widget_preview_minutes";

    private WidgetPreferences() { }

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
