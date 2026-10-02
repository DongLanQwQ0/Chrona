package com.donglan.chrona;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Activity-owned asynchronous course snapshot; stale dates and revisions are never displayed. */
final class HomeCourses implements AutoCloseable {
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private List<CourseAgenda.Item> items = Collections.emptyList();
    private String completedKey = "";
    private boolean loading, failed, closed;
    private long retryAfter;

    HomeCourses(Context context) { this.context = context.getApplicationContext(); }

    private String key() {
        ZoneId zone = ZoneId.systemDefault();
        return LocalDate.now(zone) + ":" + zone + ":" + CourseAgenda.revision();
    }

    List<CourseAgenda.Item> items() {
        return completedKey.equals(key()) ? items : Collections.emptyList();
    }

    boolean unavailable() { return completedKey.equals(key()) && failed; }
    boolean pending() { return loading || !completedKey.equals(key()); }

    void refresh(Runnable changed) {
        if (closed || loading) return;
        String requestedKey = key();
        if (requestedKey.equals(completedKey)
                && (!failed || android.os.SystemClock.elapsedRealtime() < retryAfter)) return;
        loading = true;
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        long begin = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = today.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli();
        reader.execute(() -> {
            List<CourseAgenda.Item> loaded = Collections.emptyList();
            boolean error = false;
            try { loaded = CourseAgenda.load(context, begin, end, zone); }
            catch (Exception exception) { error = true; }
            List<CourseAgenda.Item> result = loaded;
            boolean failure = error;
            main.post(() -> {
                if (closed) return;
                loading = false;
                if (!requestedKey.equals(key())) { refresh(changed); return; }
                items = result;
                failed = failure;
                completedKey = requestedKey;
                retryAfter = android.os.SystemClock.elapsedRealtime() + 30_000L;
                changed.run();
            });
        });
    }

    @Override public void close() {
        closed = true;
        reader.shutdownNow();
        main.removeCallbacksAndMessages(null);
    }
}
