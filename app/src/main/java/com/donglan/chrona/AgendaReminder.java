package com.donglan.chrona;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import android.widget.RemoteViews;
import com.donglan.chrona.calendar.CalendarOccurrence;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskStore;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Stable notification slots and one next alarm. No foreground service or network work. */
public final class AgendaReminder {
    static final String CHANNEL = "upcoming_agenda";
    static final int NOTIFICATION_ID = 0x434852;
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final android.os.Handler HANDLER = new android.os.Handler(android.os.Looper.getMainLooper());
    private static Runnable pending;

    public static synchronized void requestRefresh(Context context) {
        Context app = context.getApplicationContext();
        if (pending != null) HANDLER.removeCallbacks(pending);
        pending = () -> {
            synchronized (AgendaReminder.class) { pending = null; }
            WORKER.execute(() -> refreshSafely(app, null));
        };
        HANDLER.postDelayed(pending, 350);
    }

    static void refreshSafely(Context context, Intent action) {
        try { refresh(context, action); }
        catch (RuntimeException exception) {
            android.util.Log.w("Chrona", "Upcoming reminder refresh failed", exception);
            schedule(context, System.currentTimeMillis() + ReminderTimeline.SNOOZE);
        }
    }

    private static void refresh(Context context, Intent action) {
        SharedPreferences prefs = AgendaReminderPreferences.prefs(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        long now = System.currentTimeMillis();
        String[] previous = new String[ReminderSlots.COUNT];
        for (int i = 0; i < previous.length; i++) previous[i] = prefs.getString("slot_key:" + i, "");
        if (action != null) {
            String key = action.getStringExtra("key");
            int slot = ReminderSlots.find(previous, key);
            // Replaced or expired occurrence actions cannot affect a current slot.
            if (slot >= 0 && prefs.getLong("slot_end:" + slot, 0) > now) {
                if ("hide".equals(action.getAction())) prefs.edit().putLong("hidden:" + key, prefs.getLong("slot_end:" + slot, now)).apply();
                if ("snooze".equals(action.getAction())) prefs.edit()
                        .putLong("snooze_until:" + key, now + ReminderTimeline.SNOOZE).apply();
            }
        }
        if (!AgendaReminderPreferences.enabled(context)) {
            SharedPreferences.Editor clear = prefs.edit();
            for (int i = 0; i < ReminderSlots.COUNT; i++) {
                VivoAtomicNotification.end(context, manager, NOTIFICATION_ID + i);
                clear.remove("slot_key:" + i).remove("slot_end:" + i).remove("slot_started:" + i);
            }
            clear.apply(); schedule(context, 0); return;
        }
        java.util.Set<String> hidden = new HashSet<>();
        Map<String, Long> snoozeUntil = new HashMap<>();
        SharedPreferences.Editor cleanup = prefs.edit();
        for (java.util.Map.Entry<String, ?> item : prefs.getAll().entrySet()) {
            String name = item.getKey();
            if (!name.startsWith("hidden:") && !name.startsWith("snooze_until:")) continue;
            if (item.getValue() instanceof Long && (Long) item.getValue() > now) {
                if (name.startsWith("hidden:")) hidden.add(name.substring(7));
                else snoozeUntil.put(name.substring(13), (Long) item.getValue());
            } else cleanup.remove(name);
        }
        cleanup.apply();
        java.util.Set<String> excluded = new HashSet<>(hidden);
        excluded.addAll(snoozeUntil.keySet());
        List<AgendaEntry> entries = new ArrayList<>();
        try (TaskStore store = new TaskStore(context)) {
            for (EventCandidate local : store.nextReminderCandidates(now, excluded)) entries.add(new AgendaEntry(local.id, local.taskId, local.startAtMillis,
                    local.endAtMillis == null ? local.startAtMillis : local.endAtMillis, local.title,
                    local.location, local.description, "拾时", false, false, true, null, local.category));
            if (AgendaReminderPreferences.includesSystemCalendar(context)) {
                CalendarStore calendar = new CalendarStore(context);
                if (calendar.hasReadPermission()) {
                    ZoneId zone = ZoneId.systemDefault();
                    long horizon = Instant.ofEpochMilli(now).atZone(zone).plusYears(1).toInstant().toEpochMilli();
                    for (CalendarOccurrence e : calendar.reminderInstances(now, horizon,
                            new HashSet<>(store.linkedCalendarIds()), excluded)) {
                        if (!e.allDay) entries.add(new AgendaEntry(e.eventId, 0, e.startMillis, e.endMillis,
                                e.title, e.location, "", e.calendarName, true, false, true));
                    }
                }
            }
        }
        boolean atomicAvailable = VivoAtomicNotification.available(context);
        ReminderTimeline timeline = new ReminderTimeline(entries, now, hidden, snoozeUntil, atomicAvailable ? ReminderSlots.COUNT : 1);
        long refreshAt = timeline.wakeAt;
        AgendaEntry[] slots = ReminderSlots.assign(manager.areNotificationsEnabled() ? timeline.entries : List.of(), previous);
        if (!timeline.entries.isEmpty() && manager.areNotificationsEnabled()) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "即将开始的日程", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("日程开始前 30 分钟展示详细卡片，持续至日程结束");
            manager.createNotificationChannel(channel);
        }
        SharedPreferences.Editor state = prefs.edit();
        for (int i = 0; i < slots.length; i++) {
            AgendaEntry entry = slots[i];
            boolean same = entry != null && entry.key().equals(previous[i]);
            boolean atomic = entry != null && AgendaReminderPreferences.useAtomic(context, entry.system, atomicAvailable);
            if (!same || VivoAtomicNotification.active(manager, NOTIFICATION_ID + i) != atomic)
                VivoAtomicNotification.end(context, manager, NOTIFICATION_ID + i);
            if (entry == null) { state.remove("slot_key:" + i).remove("slot_end:" + i).remove("slot_started:" + i); continue; }
            long notifiedAt = same ? prefs.getLong("slot_started:" + i, now) : now;
            manager.notify(NOTIFICATION_ID + i, notification(context, entry, now, same,
                    NOTIFICATION_ID + i, notifiedAt, ReminderTimeline.nextTitle(entries, entry, now), atomic));
            state.putString("slot_key:" + i, entry.key()).putLong("slot_end:" + i, entry.finish());
            state.putLong("slot_started:" + i, notifiedAt);
            if (atomic) refreshAt = Math.min(refreshAt, now + ReminderTimeline.PROGRESS_REFRESH);
        }
        state.apply();
        schedule(context, Math.min(refreshAt, now + 24 * 60 * 60_000L));
    }

    static boolean exactAllowed(Context context) {
        return Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager.class).canScheduleExactAlarms();
    }

    private static void schedule(Context context, long at) {
        AlarmManager alarms = context.getSystemService(AlarmManager.class);
        PendingIntent alarm = PendingIntent.getBroadcast(context, 0,
                new Intent(context, AgendaReminderReceiver.class).setAction("refresh"),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (at == 0) { alarms.cancel(alarm); return; }
        at = Math.max(System.currentTimeMillis() + 1000, at);
        if (exactAllowed(context)) {
            try { alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm); return; }
            catch (SecurityException ignored) { /* Permission may be revoked between check and call. */ }
        }
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, alarm);
    }

    private static Notification notification(Context context, AgendaEntry e, long now, boolean same, int notificationId) {
        return notification(context, e, now, same, notificationId, e.start - ReminderTimeline.LEAD, "",
                AgendaReminderPreferences.useAtomic(context, e.system, VivoAtomicNotification.available(context)));
    }

    private static Notification notification(Context context, AgendaEntry e, long now, boolean same,
            int notificationId, long notifiedAt, String nextTitle, boolean atomic) {
        String status = now < e.start ? "即将开始" : "event".equals(e.category) ? "进行中" : "已开始";
        long remaining = ReminderTimeline.remainingMillis(e, now);
        String remainingText = remaining < 60_000L ? (now < e.start ? "即将开始" : "即将结束")
                : (now < e.start ? ((remaining + 59_999L) / 60_000L) + "分钟后开始"
                        : "还剩" + ((remaining + 59_999L) / 60_000L) + "分钟");
        ZoneId zone = ZoneId.systemDefault();
        DateTimeFormatter time = DateTimeFormatter.ofPattern("M月d日 HH:mm");
        java.time.ZonedDateTime start = Instant.ofEpochMilli(e.start).atZone(zone);
        java.time.ZonedDateTime end = Instant.ofEpochMilli(e.end).atZone(zone);
        String range = time.format(start) + " — " + (start.toLocalDate().equals(end.toLocalDate())
                ? DateTimeFormatter.ofPattern("HH:mm").format(end) : time.format(end));
        Intent open = AgendaRepository.details(context, e);
        if (!e.system) open.setData(android.net.Uri.parse("chrona://agenda/" + e.key()));
        PendingIntent details = PendingIntent.getActivity(context, notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(e.title).setContentText(status + " · " + range)
                .setContentIntent(details)
                .setOngoing(true).setOnlyAlertOnce(same).setWhen(e.start).setShowWhen(false)
                .setCategory(Notification.CATEGORY_EVENT).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setTimeoutAfter(Math.max(1000, e.finish() - now))
                .addAction(new Notification.Action.Builder(null, "查看详情", details).build())
                .addAction(new Notification.Action.Builder(null, "5 分钟后提醒", action(context, e, "snooze", 2)).build())
                .addAction(new Notification.Action.Builder(null, "隐藏本次", action(context, e, "hide", 3)).build());
        if (atomic) {
            RemoteViews card = AtomicAgendaCard.create(context, e, now, notifiedAt, nextTitle, details);
            builder.addExtras(VivoAtomicNotification.extras(context, e.title, status, range,
                    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(now < e.start ? e.start : e.finish()).atZone(zone)),
                    remainingText, details, same && VivoAtomicNotification.active(
                            context.getSystemService(NotificationManager.class), notificationId),
                    card, AtomicAgendaCard.marqueeConfiguration()));
            String detail = range + "\n" + (e.location.trim().isEmpty() ? "地点未填写" : e.location)
                    + "\n" + e.source + " · " + status
                    + (e.description.trim().isEmpty() ? "" : "\n" + e.description);
            builder.setStyle(new Notification.BigTextStyle().bigText(detail));
        } else {
            RemoteViews small = new RemoteViews(context.getPackageName(), R.layout.notification_agenda_small);
            small.setTextViewText(R.id.agenda_title, e.title);
            small.setTextViewText(R.id.agenda_time, status + " · " + range);
            RemoteViews large = new RemoteViews(context.getPackageName(), R.layout.notification_agenda_large);
            large.setTextViewText(R.id.agenda_title, e.title);
            large.setTextViewText(R.id.agenda_time, range);
            large.setTextViewText(R.id.agenda_location, e.location.trim().isEmpty() ? "地点未填写" : e.location);
            large.setTextViewText(R.id.agenda_source, e.source + " · " + status);
            large.setTextViewText(R.id.agenda_description, e.description);
            large.setViewVisibility(R.id.agenda_description, e.description.trim().isEmpty() ? android.view.View.GONE : android.view.View.VISIBLE);
            large.setChronometer(R.id.agenda_countdown, SystemClock.elapsedRealtime() + e.start - now,
                    now < e.start ? "距开始 %s" : "已开始 %s", true);
            large.setChronometerCountDown(R.id.agenda_countdown, now < e.start);
            builder.setCustomContentView(small).setCustomBigContentView(large).setCustomHeadsUpContentView(small)
                    .setStyle(new Notification.DecoratedCustomViewStyle());
        }
        return builder.build();
    }
    private static PendingIntent action(Context context, AgendaEntry e, String action, int code) {
        return PendingIntent.getBroadcast(context, code,
                new Intent(context, AgendaReminderReceiver.class).setAction(action)
                        .setData(android.net.Uri.parse("chrona://reminder/" + e.key())).putExtra("key", e.key()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private AgendaReminder() { }
}
