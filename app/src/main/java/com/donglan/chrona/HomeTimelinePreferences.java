package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;

/** Small persistent preferences for the home timeline. */
public final class HomeTimelinePreferences {
    public static final int DEFAULT_ITEM_LIMIT = 20;
    public static final int MIN_ITEM_LIMIT = 5;
    public static final int MAX_ITEM_LIMIT = 50;
    private static final String PREFS = "home_timeline";
    private static final String KEY_ITEM_LIMIT = "item_limit";

    private HomeTimelinePreferences() { }

    public static int getItemLimit(Context context) {
        return preferences(context).getInt(KEY_ITEM_LIMIT, DEFAULT_ITEM_LIMIT);
    }

    public static void setItemLimit(Context context, int limit) {
        preferences(context).edit().putInt(KEY_ITEM_LIMIT,
                Math.max(MIN_ITEM_LIMIT, Math.min(MAX_ITEM_LIMIT, limit))).apply();
    }

    private static SharedPreferences preferences(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
