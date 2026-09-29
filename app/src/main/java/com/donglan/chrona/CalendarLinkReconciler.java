package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.TaskStore;
import java.util.List;
import java.util.Set;

/** Reconcile local publication links against successful system-calendar reads, off the UI thread. */
final class CalendarLinkReconciler {
    private static final java.util.concurrent.ExecutorService WORKER =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private static final android.os.Handler HANDLER =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static Runnable pending;

    static synchronized void request(Context context) {
        if (pending != null) HANDLER.removeCallbacks(pending);
        Context app = context.getApplicationContext();
        pending = () -> {
            synchronized (CalendarLinkReconciler.class) { pending = null; }
            WORKER.execute(() -> reconcileNow(app));
        };
        HANDLER.postDelayed(pending, 300);
    }

    static void reconcileNow(Context context) {
        CalendarStore calendar = new CalendarStore(context);
        if (!calendar.hasReadPermission()) return;
        try (TaskStore store = new TaskStore(context)) {
            List<Long> ids = store.linkedCalendarIds();
            if (ids.isEmpty()) return;
            Set<Long> existing = calendar.existingEventIds(ids);
            ids.removeAll(existing);
            store.clearMissingCalendarLinks(ids);
        } catch (RuntimeException exception) {
            // Keep every association when permission/provider checks fail.
            android.util.Log.w("Chrona", "Calendar link verification failed", exception);
        }
    }

    private CalendarLinkReconciler() { }
}
