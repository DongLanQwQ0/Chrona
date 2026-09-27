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
    private static final String KEY_ACRYLIC = "acrylic_enabled";
    private static final String KEY_CHILD_TRANSPARENCY = "child_transparency";
    private static final String KEY_GAUSSIAN_BLUR = "gaussian_blur";
    private static final String KEY_SURFACE_MIX = "surface_mix";
    private static final String KEY_BLUR_STRENGTH = "blur_strength";
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
     * Refreshes the screen that made the change. Background screens notice the new revision when
     * they resume, avoiding a cascade of off-screen recreations and preserving their saved state.
     */
    private static void publish(Context context) {
        SharedPreferences preferences = prefs(context);
        preferences.edit().putInt(KEY_REVISION, preferences.getInt(KEY_REVISION, 0) + 1).apply();
        if (context instanceof AppearanceActivity appearance && !appearance.isFinishing()
                && !appearance.isDestroyed()) {
            appearance.refreshAppearance();
            synchronized (LIVE) {
                SEEN.put(appearance, revision(appearance));
            }
        } else if (context instanceof Activity activity && !activity.isFinishing()
                && !activity.isDestroyed()) activity.recreate();
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
        if (mode(context).equals(mode)) return;
        prefs(context).edit().putString(KEY_MODE, mode).apply();
        publish(context);
    }

    static void setColor(Context context, String color) {
        if (color(context).equals(color)) return;
        prefs(context).edit().putString(KEY_COLOR, color).apply();
        publish(context);
    }

    /**
     * Applies a whole appearance in one write, so screens are rebuilt exactly once. Importing a
     * configuration would otherwise rebuild everything twice in a row.
     */
    static void applyAppearance(Context context, String mode, String color) {
        if (mode(context).equals(mode) && color(context).equals(color)) return;
        prefs(context).edit().putString(KEY_MODE, mode).putString(KEY_COLOR, color).apply();
        publish(context);
    }

    static void applyAppearance(Context context, String mode, String color,
            Boolean acrylicEnabled, Integer childTransparency, Boolean gaussianBlur) {
        applyAppearance(context, mode, color, acrylicEnabled, childTransparency, gaussianBlur,
                null, null);
    }

    static void applyAppearance(Context context, String mode, String color,
            Boolean acrylicEnabled, Integer childTransparency, Boolean gaussianBlur,
            Integer surfaceMix, Integer blurStrength) {
        SharedPreferences preferences = prefs(context);
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = !mode(context).equals(mode) || !color(context).equals(color);
        boolean blurChanged = false;
        editor.putString(KEY_MODE, mode).putString(KEY_COLOR, color);
        if (acrylicEnabled != null) {
            changed |= acrylicEnabled(context) != acrylicEnabled;
            editor.putBoolean(KEY_ACRYLIC, acrylicEnabled);
        }
        if (childTransparency != null) {
            int value = Math.max(10, Math.min(65, childTransparency));
            changed |= childTransparency(context) != value;
            editor.putInt(KEY_CHILD_TRANSPARENCY, value);
        }
        if (gaussianBlur != null) {
            blurChanged = gaussianBlur(context) != gaussianBlur;
            changed |= blurChanged;
            editor.putBoolean(KEY_GAUSSIAN_BLUR, gaussianBlur);
        }
        if (surfaceMix != null) {
            int value = Math.max(10, Math.min(70, surfaceMix));
            changed |= surfaceMix(context) != value;
            editor.putInt(KEY_SURFACE_MIX, value);
        }
        if (blurStrength != null) {
            int value = Math.max(1, Math.min(5, blurStrength));
            blurChanged |= blurStrength(context) != value;
            changed |= blurStrength(context) != value;
            editor.putInt(KEY_BLUR_STRENGTH, value);
        }
        if (changed) {
            editor.apply();
            if (blurChanged) refreshWallpaperBlur(context);
            publish(context);
        }
    }

    static String background(Context context) {
        return prefs(context).getString(KEY_BACKGROUND, null);
    }

    static void setBackground(Context context, String uri) {
        if (java.util.Objects.equals(background(context), uri)) return;
        prefs(context).edit().putString(KEY_BACKGROUND, uri).apply();
        refreshWallpaper(context);
        publish(context);
    }

    static boolean acrylicEnabled(Context context) {
        return prefs(context).getBoolean(KEY_ACRYLIC, true);
    }

    static void setAcrylicEnabled(Context context, boolean enabled) {
        if (acrylicEnabled(context) == enabled) return;
        prefs(context).edit().putBoolean(KEY_ACRYLIC, enabled).apply();
        publish(context);
    }

    static boolean gaussianBlur(Context context) {
        return prefs(context).getBoolean(KEY_GAUSSIAN_BLUR, true);
    }

    static void setGaussianBlur(Context context, boolean gaussian) {
        if (gaussianBlur(context) == gaussian) return;
        prefs(context).edit().putBoolean(KEY_GAUSSIAN_BLUR, gaussian).apply();
        refreshWallpaperBlur(context);
        publish(context);
    }

    static int surfaceMix(Context context) {
        return Math.max(10, Math.min(70, prefs(context).getInt(KEY_SURFACE_MIX, 40)));
    }

    static int blurStrength(Context context) {
        return Math.max(1, Math.min(5, prefs(context).getInt(KEY_BLUR_STRENGTH, 2)));
    }

    static void previewSurfaceMix(Context context, int percent) {
        int value = Math.max(10, Math.min(70, percent));
        if (surfaceMix(context) == value) return;
        prefs(context).edit().putInt(KEY_SURFACE_MIX, value).apply();
        refreshLiveAppearance(context);
    }

    static void setSurfaceMix(Context context, int percent) {
        previewSurfaceMix(context, percent);
    }

    static void previewChildTransparency(Context context, int percent) {
        int value = Math.max(10, Math.min(65, percent));
        if (childTransparency(context) == value) return;
        prefs(context).edit().putInt(KEY_CHILD_TRANSPARENCY, value).apply();
        refreshLiveAppearance(context);
    }

    static void setBlurStrength(Context context, int strength) {
        int value = Math.max(1, Math.min(5, strength));
        if (blurStrength(context) == value) return;
        prefs(context).edit().putInt(KEY_BLUR_STRENGTH, value).apply();
        refreshWallpaperBlur(context);
        refreshLiveAppearance(context);
    }

    private static void refreshLiveAppearance(Context context) {
        synchronized (LIVE) {
            Iterator<WeakReference<Activity>> iterator = LIVE.iterator();
            while (iterator.hasNext()) {
                Activity activity = iterator.next().get();
                if (activity == null) {
                    iterator.remove();
                } else if (!activity.isFinishing() && !activity.isDestroyed()) {
                    UiStyle.refreshAppearancePreview(activity);
                }
            }
        }
    }

    private static void refreshWallpaperBlur(Context context) {
        if (context instanceof Activity activity) {
            android.view.View content = activity.findViewById(android.R.id.content);
            GlassBackdropView backdrop = content == null ? null : GlassBackdropView.findFor(content);
            if (backdrop != null) backdrop.refreshBlur();
        }
    }

    private static void refreshWallpaper(Context context) {
        if (context instanceof Activity activity) {
            android.view.View content = activity.findViewById(android.R.id.content);
            GlassBackdropView backdrop = content == null ? null : GlassBackdropView.findFor(content);
            if (backdrop != null) backdrop.refreshBackground();
        }
    }

    /** A restrained transparency range keeps nested controls readable over custom wallpapers. */
    static int childTransparency(Context context) {
        return Math.max(10, Math.min(65,
                prefs(context).getInt(KEY_CHILD_TRANSPARENCY, 32)));
    }

    static void setChildTransparency(Context context, int percent) {
        int value = Math.max(10, Math.min(65, percent));
        if (childTransparency(context) == value) return;
        prefs(context).edit().putInt(KEY_CHILD_TRANSPARENCY, value).apply();
        refreshLiveAppearance(context);
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
