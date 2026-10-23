package com.donglan.chrona.processing;

import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.StatusBarNotification;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.util.List;

/** Retracts completed parsing results without changing their source or saved schedules. */
public final class ResultNotifications {
    public static final String CHANNEL_ID = "chrona_results";
    /** The pre-0.13.29 result channel, replaced because its importance cannot be raised. */
    static final String LEGACY_CHANNEL_ID = "chrona_processing";

    private ResultNotifications() { }

    public static boolean shouldClear(TaskRecord task, List<EventCandidate> candidates) {
        if (task == null) return true;
        if (!TaskRecord.READY.equals(task.status) || candidates.isEmpty()) return false;
        for (EventCandidate candidate : candidates) if (!candidate.settled()) return false;
        return true;
    }

    public static void refresh(Context context) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) return;
        StatusBarNotification[] active = manager.getActiveNotifications();
        boolean hasResults = false;
        for (StatusBarNotification notice : active) if (isResult(notice)) { hasResults = true; break; }
        if (!hasResults) return;
        try (TaskStore store = new TaskStore(context)) {
            for (StatusBarNotification notice : active) {
                if (!isResult(notice)) continue;
                TaskRecord task = store.getTask(notice.getId());
                if (shouldClear(task, task == null ? List.of() : store.getCandidates(task.id)))
                    manager.cancel(notice.getTag(), notice.getId());
            }
        }
    }

    static void cancel(Context context, long taskId) {
        try {
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            for (StatusBarNotification notice : manager.getActiveNotifications())
                if (notice.getId() == (int) taskId && isResult(notice))
                    manager.cancel(notice.getTag(), notice.getId());
        } catch (RuntimeException failure) {
            // A host failure cannot turn successfully saved schedules into a parsing failure.
            android.util.Log.w("Chrona", "Unable to retract parsing result", failure);
        }
    }

    private static boolean isResult(StatusBarNotification notice) {
        String channel = notice.getNotification().getChannelId();
        return CHANNEL_ID.equals(channel) || LEGACY_CHANNEL_ID.equals(channel);
    }
}
