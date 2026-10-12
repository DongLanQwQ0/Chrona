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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** One card and one next alarm. No foreground service or network work. */
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
        if (action != null) {
            String key = action.getStringExtra("key");
            // Actions from a replaced card must never hide the new occurrence.
            if (key != null && key.equals(prefs.getString("shown", ""))) {
                if ("hide".equals(action.getAction())) prefs.edit().putLong("hidden:" + key, prefs.getLong("shown_end", now)).apply();
                if ("snooze".equals(action.getAction())) prefs.edit().putString("snoozed", key)
                        .putLong("snooze_until", now + ReminderTimeline.SNOOZE).apply();
            }
        }
        if (!AgendaReminderPreferences.enabled(context)) {
            manager.cancel(NOTIFICATION_ID); schedule(context, 0); return;
        }
        java.util.Set<String> hidden = new HashSet<>();
        SharedPreferences.Editor cleanup = prefs.edit();
        for (java.util.Map.Entry<String, ?> item : prefs.getAll().entrySet()) if (item.getKey().startsWith("hidden:")) {
            if (item.getValue() instanceof Long && (Long) item.getValue() > now) hidden.add(item.getKey().substring(7));
            else cleanup.remove(item.getKey());
        }
        cleanup.apply();
        List<AgendaEntry> entries = new ArrayList<>();
        try (TaskStore store = new TaskStore(context)) {
            for (EventCandidate local : store.nextReminderCandidates(now, hidden)) entries.add(new AgendaEntry(local.id, local.taskId, local.startAtMillis,
                    local.endAtMillis == null ? local.startAtMillis : local.endAtMillis, local.title,
                    local.location, local.description, "拾时", false, false, true));
            if (AgendaReminderPreferences.includesSystemCalendar(context)) {
                CalendarStore calendar = new CalendarStore(context);
                if (calendar.hasReadPermission()) {
                    ZoneId zone = ZoneId.systemDefault();
                    long horizon = Instant.ofEpochMilli(now).atZone(zone).plusYears(1).toInstant().toEpochMilli();
                    for (CalendarOccurrence e : calendar.reminderInstances(now, horizon,
                            new HashSet<>(store.linkedCalendarIds()), hidden)) {
                        if (!e.allDay) entries.add(new AgendaEntry(e.eventId, 0, e.startMillis, e.endMillis,
                                e.title, e.location, "", e.calendarName, true, false, true));
                    }
                }
            }
        }
        ReminderTimeline timeline = new ReminderTimeline(entries, now, hidden,
                prefs.getString("snoozed", ""), prefs.getLong("snooze_until", 0));
        if (!timeline.visible || !manager.areNotificationsEnabled()) {
            manager.cancel(NOTIFICATION_ID);
            prefs.edit().remove("shown").apply();
        } else {
            String key = timeline.focus.key();
            boolean same = key.equals(prefs.getString("shown", ""));
            NotificationChannel channel = new NotificationChannel(CHANNEL, "即将开始的日程", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("日程开始前 30 分钟展示详细卡片，持续至日程结束");
            manager.createNotificationChannel(channel);
            manager.notify(NOTIFICATION_ID, notification(context, timeline.focus, now, same));
            prefs.edit().putString("shown", key).putLong("shown_end", timeline.focus.finish()).apply();
        }
        schedule(context, Math.min(timeline.wakeAt, now + 24 * 60 * 60_000L));
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

    private static Notification notification(Context context, AgendaEntry e, long now, boolean same) {
        String status = now < e.start ? "即将开始" : "进行中";
        ZoneId zone = ZoneId.systemDefault();
        DateTimeFormatter time = DateTimeFormatter.ofPattern("M月d日 HH:mm");
        String range = time.format(Instant.ofEpochMilli(e.start).atZone(zone)) + " — "
                + time.format(Instant.ofEpochMilli(e.end).atZone(zone));
        Intent open = AgendaRepository.details(context, e);
        if (!e.system) open.setData(android.net.Uri.parse("chrona://agenda/" + e.key()));
        PendingIntent details = PendingIntent.getActivity(context, 1, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
        return new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(e.title).setContentText(status + " · " + range)
                .setCustomContentView(small).setCustomBigContentView(large).setCustomHeadsUpContentView(small)
                .setStyle(new Notification.DecoratedCustomViewStyle()).setContentIntent(details)
                .setOngoing(true).setOnlyAlertOnce(same).setWhen(e.start).setShowWhen(false)
                .setCategory(Notification.CATEGORY_EVENT).setVisibility(Notification.VISIBILITY_PRIVATE)
                .setTimeoutAfter(Math.max(1000, e.finish() - now))
                .addAction(new Notification.Action.Builder(null, "查看详情", details).build())
                .addAction(new Notification.Action.Builder(null, "5 分钟后提醒", action(context, e, "snooze", 2)).build())
                .addAction(new Notification.Action.Builder(null, "隐藏本次", action(context, e, "hide", 3)).build())
                .build();
    }
    private static PendingIntent action(Context context, AgendaEntry e, String action, int code) {
        return PendingIntent.getBroadcast(context, code,
                new Intent(context, AgendaReminderReceiver.class).setAction(action)
                        .setData(android.net.Uri.parse("chrona://reminder/" + e.key())).putExtra("key", e.key()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private AgendaReminder() { }
}
