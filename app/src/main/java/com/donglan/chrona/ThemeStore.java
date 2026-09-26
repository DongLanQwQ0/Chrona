package com.donglan.chrona;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Build;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

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
    private static final String KEY_BACKGROUND = "background";
    private static final String KEY_REVISION = "revision";
    /** Live screens, so an appearance change reaches the ones already sitting behind this one. */
    private static final List<WeakReference<Activity>> LIVE = new ArrayList<>();
    /** Revision each live screen was built at, used to spot a screen the change could not reach. */
    private static final java.util.Map<Activity, Integer> SEEN = new java.util.WeakHashMap<>();

    private ThemeStore() { }

    /**
     * Bumped by every appearance write. Screens compare it on resume, because a palette baked into
     * an existing view tree cannot be repainted without rebuilding that tree.
     */
    static int revision(Context context) {
        return prefs(context).getInt(KEY_REVISION, 0);
    }

    static void watch(Activity activity) {
        synchronized (LIVE) {
            LIVE.add(new WeakReference<>(activity));
            SEEN.put(activity, revision(activity));
        }
    }

    /**
     * True when this screen was built before the current palette. Screens ask once per resume, so a
     * change that could not reach a stopped screen is still applied before the user sees it.
     */
    static boolean outdated(Activity activity) {
        synchronized (LIVE) {
            Integer seen = SEEN.get(activity);
            int current = revision(activity);
            if (seen == null) {
                SEEN.put(activity, current);
                return false;
            }
            if (seen != current) {
                SEEN.put(activity, current);
                return true;
            }
            return false;
        }
    }

    static void forget(Activity activity) {
        synchronized (LIVE) {
            SEEN.remove(activity);
            Iterator<WeakReference<Activity>> iterator = LIVE.iterator();
            while (iterator.hasNext()) {
                Activity candidate = iterator.next().get();
                if (candidate == null || candidate == activity) iterator.remove();
            }
        }
    }

    /**
     * Recreates every live screen after an appearance change. Without this only the screen that
     * wrote the preference changed palette, so tapping a new theme colour left the rest of the app
     * on its original green. Recreated screens restore their scroll position and skip entrance
     * animations, so the change reads as an update rather than a reload.
     */
    private static void publish(Context context) {
        SharedPreferences preferences = prefs(context);
        preferences.edit().putInt(KEY_REVISION, preferences.getInt(KEY_REVISION, 0) + 1).apply();
        List<Activity> alive = new ArrayList<>();
        synchronized (LIVE) {
            Iterator<WeakReference<Activity>> iterator = LIVE.iterator();
            while (iterator.hasNext()) {
                Activity activity = iterator.next().get();
                if (activity == null || activity.isDestroyed()) iterator.remove();
                else if (!activity.isFinishing()) alive.add(activity);
            }
        }
        for (Activity activity : alive) activity.recreate();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, 0);
    }

    static String mode(Context context) {
        return prefs(context).getString(KEY_MODE, SYSTEM);
    }

    static String color(Context context) {
        return prefs(context).getString(KEY_COLOR, TEAL);
    }

    static void setMode(Context context, String mode) {
        prefs(context).edit().putString(KEY_MODE, mode).apply();
        publish(context);
    }

    static void setColor(Context context, String color) {
        prefs(context).edit().putString(KEY_COLOR, color).apply();
        publish(context);
    }

    /**
     * Applies a whole appearance in one write, so screens are rebuilt exactly once. Importing a
     * configuration would otherwise rebuild everything twice in a row.
     */
    static void applyAppearance(Context context, String mode, String color) {
        prefs(context).edit().putString(KEY_MODE, mode).putString(KEY_COLOR, color).apply();
        publish(context);
    }

    static String background(Context context) {
        return prefs(context).getString(KEY_BACKGROUND, null);
    }

    static void setBackground(Context context, String uri) {
        prefs(context).edit().putString(KEY_BACKGROUND, uri).apply();
        publish(context);
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
