package com.donglan.chrona;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.util.HashMap;
import java.util.Map;

/** Last-two-minute text updates reuse a fixed snapshot, never reread calendars or accumulate actions.
 * All methods run on AgendaReminder.WORKER, shared with slot replacement and completion. */
final class AtomicCountdownUpdates {
    private static final String PREFIX = "notification.superx.";
    private record Snapshot(Notification notice, long deadline, long expiresAt) { }
    private static final Map<Integer, Snapshot> snapshots = new HashMap<>();
    static void remember(int id, Notification notice, AgendaEntry entry, long now) {
        remember(id,notice,entry,now,ReminderTimeline.expiresAt(entry));
    }
    static void remember(int id, Notification notice, AgendaEntry entry, long now, long expiresAt) {
        snapshots.put(id, new Snapshot(notice, ReminderTimeline.capsuleDeadline(entry, now), expiresAt));
    }
    static void clear(int id) { snapshots.remove(id); }
    static long nextDelay(Context context) {
        if (!AgendaReminderPreferences.showSeconds(context)) return 60_000;
        long now = System.currentTimeMillis(), delay = 60_000;
        for (Snapshot snapshot : snapshots.values()) {
            if (snapshot.deadline <= now) continue;
            delay = Math.min(delay, Math.max(1000, snapshot.deadline - now - ReminderTimeline.CAPSULE_COUNTDOWN_WINDOW));
        }
        return delay;
    }
    static void tick(Context context) {
        if (!AgendaReminderPreferences.enabled(context) || !AgendaReminderPreferences.showSeconds(context)) return;
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        long now = System.currentTimeMillis();
        if (snapshots.values().stream().noneMatch(s -> s.deadline > 0
                && s.deadline - now <= ReminderTimeline.CAPSULE_COUNTDOWN_WINDOW)) return;
        try {
            Map<Integer, Notification> active = new HashMap<>();
            for (var item : manager.getActiveNotifications()) active.put(item.getId(), item.getNotification());
            for (var item : snapshots.entrySet()) {
                Snapshot snapshot = item.getValue();
                long remaining = snapshot.deadline - now;
                if (snapshot.deadline == 0 || remaining > ReminderTimeline.CAPSULE_COUNTDOWN_WINDOW) continue;
                if (remaining <= 0) { AgendaReminder.requestRefresh(context); continue; }
                Notification current = active.get(item.getKey());
                if (current == null || current.when != snapshot.notice.when
                        || !current.extras.containsKey(PREFIX + "operation")) continue;
                Bundle data = new Bundle(snapshot.notice.extras);
                data.putInt(PREFIX + "operation", 1);
                String text = AtomicAgendaCard.remainingText(remaining, true);
                Bundle base = new Bundle(snapshot.notice.extras.getBundle(PREFIX + "baseInfos"));
                String originalContent = base.getCharSequence(PREFIX + "baseInfos.content").toString();
                int rangeAt = originalContent.indexOf('\n');
                base.putCharSequence(PREFIX + "baseInfos.content", text
                        + (rangeAt < 0 ? "" : originalContent.substring(rangeAt)));
                data.putBundle(PREFIX + "baseInfos", base);
                Bundle shortInfo = new Bundle(snapshot.notice.extras.getBundle(PREFIX + "shortInfos"));
                shortInfo.putString(PREFIX + "shortInfos.coreInfoShort", text);
                data.putBundle(PREFIX + "shortInfos", shortInfo);
                RemoteViews card = (RemoteViews) snapshot.notice.extras.getParcelable(PREFIX + "customTemplate");
                if (card != null) data.putParcelable(PREFIX + "customTemplate", countdown(card, text));
                Bundle originalIsland = snapshot.notice.extras.getBundle(PREFIX + "island");
                if (originalIsland != null) {
                    Bundle island = new Bundle(originalIsland);
                    RemoteViews expanded = (RemoteViews) island.getParcelable("island.superx.customTemplate");
                    if (expanded != null) island.putParcelable("island.superx.customTemplate", countdown(expanded, text));
                    if (AgendaReminderPreferences.capsuleCountdown(context)) {
                        Bundle left = new Bundle(island.getBundle("island.superx.leftInfo"));
                        left.putCharSequence("island.superx.leftInfo.content", text);
                        island.putBundle("island.superx.leftInfo", left);
                    }
                    data.putBundle(PREFIX + "island", island);
                }
                // Some vivo surfaces render the compact capsule rather than island.leftInfo.
                Bundle capsule = snapshot.notice.extras.getBundle(PREFIX + "capsule");
                if (capsule != null && AgendaReminderPreferences.capsuleCountdown(context)) {
                    Bundle updatedCapsule = new Bundle(capsule);
                    updatedCapsule.putString(PREFIX + "capsule.content", text + " "
                            + base.getCharSequence(PREFIX + "baseInfos.title"));
                    data.putBundle(PREFIX + "capsule", updatedCapsule);
                }
                manager.notify(item.getKey(), Notification.Builder.recoverBuilder(context, snapshot.notice)
                        .addExtras(data).setTimeoutAfter(Math.max(1000, snapshot.expiresAt - now))
                        .setOnlyAlertOnce(true).build());
            }
        } catch (RuntimeException failure) {
            android.util.Log.w("ChronaAtomic", "Unable to refresh seconds", failure);
        }
    }
    private static RemoteViews countdown(RemoteViews baseline, String text) {
        RemoteViews views = baseline.clone();
        views.setTextViewText(R.id.atomic_remaining, text);
        views.setTextViewText(R.id.atomic_top_countdown, text);
        views.setTextViewTextSize(R.id.atomic_remaining, android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        views.setTextViewTextSize(R.id.atomic_top_countdown, android.util.TypedValue.COMPLEX_UNIT_SP, 14);
        return views;
    }
    private AtomicCountdownUpdates() { }
}
