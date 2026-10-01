package com.donglan.chrona;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import java.lang.ref.WeakReference;
import java.time.ZoneId;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

final class ReleaseUpdates {
    private static final long AFTER_CONTENT_DELAY_MILLIS = 1500L;
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean SCHEDULED = new AtomicBoolean();
    private static WeakReference<Activity> owner = new WeakReference<>(null);
    private static final ExecutorService READER = Executors.newSingleThreadExecutor(task ->
            new Thread(() -> {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);
                task.run();
            }, "chrona-release-check"));

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

    /** After content is drawn: no disk, package lookup or network work on the calling thread. */
    static void checkAutomatically(Activity activity) {
        owner = new WeakReference<>(activity);
        if (!SCHEDULED.compareAndSet(false, true)) return;
        Context context = activity.getApplicationContext();
        MAIN.postDelayed(() -> READER.execute(() -> checkInBackground(context)),
                AFTER_CONTENT_DELAY_MILLIS);
    }

    private static void checkInBackground(Context context) {
        boolean delivering = false;
        try {
            SharedPreferences prefs = preferences(context);
            if (!prefs.getBoolean("automatic", true)) return;
            String current = installedVersion(context);
            long now = System.currentTimeMillis();
            if (ReleaseCheckPolicy.due(now, prefs.getLong("last_attempt", 0), ZoneId.systemDefault())) {
                // Failure also counts today; startup never repeatedly retries a bad network.
                if (!prefs.edit().putLong("last_attempt", now).commit()) return;
                try {
                    GitHubRelease release = GitHubRelease.fetch();
                    if (GitHubRelease.newer(release.version, current))
                        prefs.edit().putString("pending_version", release.version).commit();
                } catch (Exception ignored) {
                    // Settings can report the failure and retry immediately.
                }
            }
            if (!prefs.getBoolean("automatic", true)) return;
            String version = prefs.getString("pending_version", "");
            if (version.isEmpty()) return;
            if (!GitHubRelease.newer(version, current)
                    || version.equals(prefs.getString("notified_version", ""))) {
                prefs.edit().remove("pending_version").commit();
                return;
            }
            delivering = MAIN.post(() -> {
                try {
                    Activity target = owner.get();
                    if (target == null || target.isFinishing() || target.isDestroyed()
                            || !target.hasWindowFocus()) return;
                    UiStyle.confirmDialog(target, "发现新版本 " + version,
                            "当前版本 " + current + "，可查看更新说明并下载 APK。",
                            "查看更新", () -> target.startActivity(new Intent(target, UpdateActivity.class)));
                    READER.execute(() -> prefs.edit().putString("notified_version", version)
                            .remove("pending_version").commit());
                } finally { SCHEDULED.set(false); }
            });
        } catch (Exception ignored) {
            // Metadata failures must never interrupt the app.
        } finally {
            if (!delivering) SCHEDULED.set(false);
        }
    }

    private ReleaseUpdates() { }
}
