package com.donglan.chrona;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.os.Build;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/** Small, local appearance preference shared by every screen. */
public final class ThemeStore {
    static final String SYSTEM = "system";
    static final String LIGHT = "light";
    static final String DARK = "dark";
    static final String WALLPAPER = "wallpaper";
    static final String TEAL = "teal";
    static final String BLUE = "blue";
    static final String CORAL = "coral";
    static final String PURPLE = "purple";
    static final String AMBER = "amber";
    static final String ROSE = "rose";
    static final String FOREST = "forest";
    static final String CUSTOM = "custom";

    private static final String PREFS = "appearance";
    private static final String KEY_MODE = "mode";
    private static final String KEY_COLOR = "color";
    private static final String KEY_CUSTOM_COLOR = "custom_color";
    private static final String KEY_BACKGROUND = "background";
    private static final String KEY_ACRYLIC = "acrylic_enabled";
    private static final String REMOVED_CHILD_TRANSPARENCY_KEY = "child_transparency";
    private static final String KEY_GAUSSIAN_BLUR = "gaussian_blur";
    private static final String KEY_SURFACE_MIX = "surface_mix";
    private static final String KEY_BLUR_STRENGTH = "blur_strength";
    private static final String KEY_REVISION = "revision";
    /** Live screens, so an appearance change reaches the ones already sitting behind this one. */
    private static final List<WeakReference<Activity>> LIVE = new ArrayList<>();
    private static volatile boolean removedChildTransparencyPreference;
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
        SharedPreferences preferences = context.getSharedPreferences(PREFS, 0);
        if (!removedChildTransparencyPreference) {
            synchronized (ThemeStore.class) {
                if (!removedChildTransparencyPreference) {
                    preferences.edit().remove(REMOVED_CHILD_TRANSPARENCY_KEY).apply();
                    removedChildTransparencyPreference = true;
                }
            }
        }
        return preferences;
    }

    static String mode(Context context) {
        return prefs(context).getString(KEY_MODE, SYSTEM);
    }

    static String color(Context context) {
        return prefs(context).getString(KEY_COLOR, TEAL);
    }

    static String customColorHex(Context context) {
        int color = prefs(context).getInt(KEY_CUSTOM_COLOR, 0xFF7353BA);
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }

    static int customColor(Context context) {
        return prefs(context).getInt(KEY_CUSTOM_COLOR, 0xFF7353BA);
    }

    static boolean isHexColor(String value) {
        if (value == null) return false;
        String hex = value.startsWith("#") ? value.substring(1) : value;
        return hex.matches("[0-9A-Fa-f]{6}");
    }

    static int paletteSeed(Context context, String scheme) {
        return switch (scheme) {
            case PURPLE -> 0xFF7353BA;
            case AMBER -> 0xFF9A4D00;
            case ROSE -> 0xFFB42362;
            case FOREST -> 0xFF38734F;
            case CUSTOM -> customColor(context);
            default -> 0;
        };
    }

    static boolean supportedColor(String scheme) {
        return TEAL.equals(scheme) || BLUE.equals(scheme) || CORAL.equals(scheme)
                || WALLPAPER.equals(scheme) || PURPLE.equals(scheme) || AMBER.equals(scheme)
                || ROSE.equals(scheme) || FOREST.equals(scheme) || CUSTOM.equals(scheme);
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

    static void setCustomColor(Context context, int color) {
        int opaque = Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
        if (CUSTOM.equals(color(context)) && customColor(context) == opaque) return;
        prefs(context).edit().putInt(KEY_CUSTOM_COLOR, opaque).putString(KEY_COLOR, CUSTOM).apply();
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
            Boolean acrylicEnabled, Boolean gaussianBlur) {
        applyAppearance(context, mode, color, acrylicEnabled, gaussianBlur, null, null, null);
    }

    static void applyAppearance(Context context, String mode, String color,
            Boolean acrylicEnabled, Boolean gaussianBlur,
            Integer surfaceMix, Integer blurStrength) {
        applyAppearance(context, mode, color, acrylicEnabled, gaussianBlur,
                surfaceMix, blurStrength, null);
    }

    static void applyAppearance(Context context, String mode, String color,
            Boolean acrylicEnabled, Boolean gaussianBlur,
            Integer surfaceMix, Integer blurStrength, String customColor) {
        SharedPreferences preferences = prefs(context);
        SharedPreferences.Editor editor = preferences.edit();
        boolean changed = !mode(context).equals(mode) || !color(context).equals(color);
        boolean blurChanged = false;
        editor.putString(KEY_MODE, mode).putString(KEY_COLOR, color);
        if (customColor != null) {
            if (!isHexColor(customColor))
                throw new IllegalArgumentException("Invalid custom theme color");
            int parsed = Color.parseColor(customColor.startsWith("#")
                    ? customColor : "#" + customColor);
            int opaque = Color.rgb(Color.red(parsed), Color.green(parsed), Color.blue(parsed));
            changed |= customColor(context) != opaque;
            editor.putInt(KEY_CUSTOM_COLOR, opaque);
        }
        if (acrylicEnabled != null) {
            changed |= acrylicEnabled(context) != acrylicEnabled;
            editor.putBoolean(KEY_ACRYLIC, acrylicEnabled);
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
