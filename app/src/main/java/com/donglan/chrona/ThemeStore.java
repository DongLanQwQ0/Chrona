package com.donglan.chrona;

import android.app.Activity;
import android.content.Context;
import android.content.res.Configuration;
import android.os.Build;

/** Small, local appearance preference shared by every screen. */
public final class ThemeStore {
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";
    static final String WALLPAPER = "wallpaper";
    static final String TEAL = "teal";
    static final String BLUE = "blue";
    static final String CORAL = "coral";

    private static final String PREFS = "appearance";
    private static final String KEY_MODE = "mode";
    private static final String KEY_COLOR = "color";

    private ThemeStore() { }

    static String mode(Context context) {
        return context.getSharedPreferences(PREFS, 0).getString(KEY_MODE, SYSTEM);
    }

    static String color(Context context) {
        return context.getSharedPreferences(PREFS, 0).getString(KEY_COLOR, TEAL);
    }

    static void setMode(Context context, String mode) {
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY_MODE, mode).apply();
    }

    static void setColor(Context context, String color) {
        context.getSharedPreferences(PREFS, 0).edit().putString(KEY_COLOR, color).apply();
    }

    static boolean dark(Context context) {
        String mode = mode(context);
        if (DARK.equals(mode)) return true;
        if (LIGHT.equals(mode)) return false;
        return (context.getResources().getConfiguration().uiMode
                & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    static boolean wallpaperAvailable() { return Build.VERSION.SDK_INT >= 31; }

    public static void apply(Activity activity) {
        boolean dark = dark(activity);
        String color = color(activity);
        int style;
        if (WALLPAPER.equals(color) && wallpaperAvailable()) {
            style = dark ? R.style.Theme_Chrona_Wallpaper_Dark
                    : R.style.Theme_Chrona_Wallpaper_Light;
        } else if (BLUE.equals(color)) {
            style = dark ? R.style.Theme_Chrona_Blue_Dark : R.style.Theme_Chrona_Blue_Light;
        } else if (CORAL.equals(color)) {
            style = dark ? R.style.Theme_Chrona_Coral_Dark : R.style.Theme_Chrona_Coral_Light;
        } else {
            style = dark ? R.style.Theme_Chrona_Dark : R.style.Theme_Chrona_Light;
        }
        activity.setTheme(style);
    }
}
