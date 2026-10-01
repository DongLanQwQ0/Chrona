package com.donglan.chrona;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

final class ReleaseUpdates {
    private static final long CHECK_INTERVAL_MILLIS = 24 * 60 * 60 * 1000L;
    private static final ExecutorService READER = Executors.newSingleThreadExecutor();

    static SharedPreferences preferences(Context context) {
        return context.getSharedPreferences("release_updates", Context.MODE_PRIVATE);
    }

    static String installedVersion(Context context) {
        try {
            return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException exception) {
            throw new IllegalStateException("无法读取当前版本", exception);
        }
    }

    static void checkAutomatically(Activity activity) {
        SharedPreferences prefs = preferences(activity);
        showPending(activity);
        long now = System.currentTimeMillis();
        long previous = prefs.getLong("last_attempt", 0);
        if (!prefs.getBoolean("automatic", true)
                || (now >= previous && now - previous < CHECK_INTERVAL_MILLIS)) return;
        prefs.edit().putLong("last_attempt", now).apply();
        WeakReference<Activity> owner = new WeakReference<>(activity);
        String current = installedVersion(activity);
        READER.execute(() -> {
            try {
                GitHubRelease release = GitHubRelease.fetch();
                if (!GitHubRelease.newer(release.version, current)) return;
                prefs.edit().putString("pending_version", release.version).apply();
                new Handler(Looper.getMainLooper()).post(() -> {
                    Activity target = owner.get();
                    if (target != null) showPending(target);
                });
            } catch (Exception ignored) {
                // Background failures are silent. Settings offers an explicit check and error state.
            }
        });
    }

    /** Keep a discovered version until the launcher is visible, including after rotation. */
    static void showPending(Activity activity) {
        SharedPreferences prefs = preferences(activity);
        if (activity.isFinishing() || activity.isDestroyed() || !activity.hasWindowFocus()
                || !prefs.getBoolean("automatic", true)) return;
        String version = prefs.getString("pending_version", "");
        if (version.isEmpty()) return;
        String current = installedVersion(activity);
        try {
            if (!GitHubRelease.newer(version, current)
                    || version.equals(prefs.getString("notified_version", ""))) {
                prefs.edit().remove("pending_version").apply();
                return;
            }
        } catch (IllegalArgumentException exception) {
            prefs.edit().remove("pending_version").apply();
            return;
        }
        prefs.edit().putString("notified_version", version).remove("pending_version").apply();
        UiStyle.confirmDialog(activity, "发现新版本 " + version,
                "当前版本 " + current + "，可查看更新说明并下载 APK。",
                "查看更新", () -> activity.startActivity(new Intent(activity, UpdateActivity.class)));
    }

    private ReleaseUpdates() { }
}
