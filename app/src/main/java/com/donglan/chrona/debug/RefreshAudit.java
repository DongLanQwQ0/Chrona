package com.donglan.chrona.debug;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

import java.time.Instant;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Foreground scheduling diagnostics. No frame sampling, content, network calls or wake locks. */
public final class RefreshAudit implements Application.ActivityLifecycleCallbacks {
    static final long HEARTBEAT_MS = 1000;
    static final long LATE_MS = 2000;
    static final long SLOW_MS = 1000;
    private static RefreshAudit instance;
    private final Application app;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService writer = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "chrona-refresh-audit");
        thread.setPriority(Thread.MIN_PRIORITY);
        return thread;
    });
    private final WeakHashMap<Activity, Long> creating = new WeakHashMap<>();
    private final WeakHashMap<Activity, Long> resuming = new WeakHashMap<>();
    private int started;
    private boolean active;
    private String screen = "application";
    private long lastBeat;
    private final Runnable beat = new Runnable() {
        @Override public void run() {
            if (!active) return;
            long now = SystemClock.uptimeMillis();
            long gap = now - lastBeat;
            if (late(gap, HEARTBEAT_MS)) emit("main_loop_late screen=" + screen
                    + " interval_ms=" + gap + " expected_ms=" + HEARTBEAT_MS
                    + " late_ms=" + (gap - HEARTBEAT_MS));
            lastBeat = now;
            main.postDelayed(this, HEARTBEAT_MS);
        }
    };

    private RefreshAudit(Application app) { this.app = app; }

    public static void install(Application app) {
        if (instance != null) return;
        instance = new RefreshAudit(app);
        app.registerActivityLifecycleCallbacks(instance);
    }

    public static long begin() { return SystemClock.uptimeMillis(); }

    public static void end(String stage, long began) {
        long duration = SystemClock.uptimeMillis() - began;
        if (instance != null && duration >= SLOW_MS)
            instance.emit("slow_stage stage=" + stage + " duration_ms=" + duration);
    }

    /** Call at an actual scheduled refresh, not when merely requesting one. */
    public static void interval(String source, long previous, long expected, long now) {
        if (instance != null && previous >= 0 && late(now - previous, expected))
            instance.emit("refresh_late source=" + source + " interval_ms=" + (now - previous)
                    + " expected_ms=" + expected + " late_ms=" + (now - previous - expected));
    }

    static boolean late(long gap, long expected) { return gap - expected >= LATE_MS; }

    private void emit(String event) {
        String line = "refresh_audit at=" + Instant.now() + " uptime_ms="
                + SystemClock.uptimeMillis() + " " + event;
        writer.execute(() -> DiagLog.add(app, line));
    }

    private void activate(Activity activity) {
        screen = activity.getClass().getSimpleName();
        if (active) return;
        active = true;
        lastBeat = SystemClock.uptimeMillis();
        main.postDelayed(beat, HEARTBEAT_MS);
    }

    @Override public void onActivityPreCreated(Activity activity, Bundle state) {
        creating.put(activity, begin());
        activate(activity);
    }
    @Override public void onActivityPostCreated(Activity activity, Bundle state) {
        Long began = creating.remove(activity);
        if (began != null) end("create:" + activity.getClass().getSimpleName(), began);
    }
    @Override public void onActivityPreResumed(Activity activity) {
        resuming.put(activity, begin());
        activate(activity);
    }
    @Override public void onActivityPostResumed(Activity activity) {
        Long began = resuming.remove(activity);
        if (began != null) end("resume:" + activity.getClass().getSimpleName(), began);
    }
    @Override public void onActivityStarted(Activity activity) { started++; activate(activity); }
    @Override public void onActivityStopped(Activity activity) {
        started--;
        if (started == 0) {
            active = false;
            main.removeCallbacks(beat);
        }
    }
    @Override public void onActivityCreated(Activity activity, Bundle state) { }
    @Override public void onActivityResumed(Activity activity) { activate(activity); }
    @Override public void onActivityPaused(Activity activity) { }
    @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
    @Override public void onActivityDestroyed(Activity activity) {
        creating.remove(activity); resuming.remove(activity);
    }
}
