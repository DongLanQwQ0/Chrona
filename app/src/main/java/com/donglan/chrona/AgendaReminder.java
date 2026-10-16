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

/** Stable notification slots, boundary alarms and scoped live atomic updates. No network work. */
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
        ZjuHomeworkCalendar.retryPending(context);
        TaskCompletion.retryPending(context);
        SharedPreferences prefs = AgendaReminderPreferences.prefs(context);
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        long now = System.currentTimeMillis();
        String[] previous = new String[ReminderSlots.COUNT];
        for (int i = 0; i < previous.length; i++) previous[i] = prefs.getString("slot_key:" + i, "");
        if (action != null) {
            // OEM RemoteViews dispatch may strip extras. The immutable PendingIntent URI
            // is already the occurrence identity and survives that dispatch intact.
            String key = action.getData() == null ? null : action.getData().getLastPathSegment();
            int slot = ReminderSlots.find(previous, key);
            // Replaced or expired occurrence actions cannot affect a current slot.
            if (slot >= 0 && prefs.getLong("slot_end:" + slot, 0) > now) {
                if ("complete".equals(action.getAction()) && key.startsWith("chrona:")) {
                    try(TaskStore store=new TaskStore(context)) {
                        long id=Long.parseLong(key.split(":")[1]);
                        for(EventCandidate candidate:store.getCandidatesByIds(java.util.Collections.singletonList(id)))
                            if(candidate.isTask() && candidate.reminderAnchor()!=null && key.equals("chrona:"+id+":"+candidate.reminderAnchor())) {
                                String warning = TaskCompletion.set(context,id,candidate.taskId,true);
                                if (warning != null) HANDLER.post(() -> android.widget.Toast.makeText(context,warning,android.widget.Toast.LENGTH_LONG).show());
                            }
                    }
                }
                if ("hide".equals(action.getAction()) || "complete".equals(action.getAction()))
                    AgendaAcknowledgement.set(context, key, prefs.getLong("slot_end:" + slot, now), true);
                if ("snooze".equals(action.getAction())) {
                    long until = Math.min(now + ReminderTimeline.SNOOZE, prefs.getLong("slot_snooze_limit:" + slot, Long.MAX_VALUE));
                    if (until > now) {
                        SharedPreferences.Editor edit = prefs.edit().putLong("snooze_until:" + key, until);
                        if (prefs.getBoolean("slot_task:" + slot, false)) edit.putLong("snooze_end:" + key, until + ReminderTimeline.STARTED_DISPLAY_WINDOW);
                        edit.apply();
                    }
                }
            }
        }
        if (!AgendaReminderPreferences.enabled(context)) {
            SharedPreferences.Editor clear = prefs.edit();
            for (int i = 0; i < ReminderSlots.COUNT; i++) {
                VivoAtomicNotification.end(context, manager, NOTIFICATION_ID + i);
                clear.remove("slot_key:" + i).remove("slot_end:" + i).remove("slot_started:" + i);
            }
            clear.apply(); schedule(context, 0); AgendaReminderLiveService.update(context, false, 0); return;
        }
        java.util.Set<String> hidden = new HashSet<>();
        Map<String, Long> snoozeUntil = new HashMap<>();
        SharedPreferences.Editor cleanup = prefs.edit();
        for (java.util.Map.Entry<String, ?> item : prefs.getAll().entrySet()) {
            String name = item.getKey();
            if (!name.startsWith("hidden:") && !name.startsWith("snooze_until:")) continue;
            long validUntil = name.startsWith("snooze_until:")
                    ? prefs.getLong("snooze_end:" + name.substring(13), (Long)item.getValue()) : (Long)item.getValue();
            if (validUntil > now) {
                if (name.startsWith("hidden:")) hidden.add(name.substring(7));
                else snoozeUntil.put(name.substring(13), (Long) item.getValue());
            } else { cleanup.remove(name); if(name.startsWith("snooze_until:"))cleanup.remove("snooze_end:"+name.substring(13)); }
        }
        cleanup.apply();
        java.util.Set<String> excluded = new HashSet<>(hidden);
        excluded.addAll(snoozeUntil.keySet());
        List<AgendaEntry> entries = new ArrayList<>();
        try (TaskStore store = new TaskStore(context)) {
            // Explicitly deferred tasks outlive their original deadline window, not the task itself.
            List<Long> deferred = new ArrayList<>();
            for (String key : snoozeUntil.keySet()) if(key.startsWith("chrona:")) deferred.add(Long.parseLong(key.split(":")[1]));
            for (EventCandidate local : store.getCandidatesByIds(deferred)) {
                if (!local.isTask() || local.completed() || local.needsConfirmation || local.reminderAnchor()==null || !store.allowsReminder(local.id)) continue;
                String key="chrona:"+local.id+":"+local.reminderAnchor();
                if (snoozeUntil.containsKey(key)) entries.add(new AgendaEntry(local.id,local.taskId,local.reminderAnchor(),local.reminderAnchor(),local.title,
                        local.location,local.description,"拾时",false,false,true,null,local.category,local.reminderMinutesBefore,local.dueAtMillis));
            }
            for (EventCandidate local : store.nextReminderCandidates(now, excluded)) entries.add(new AgendaEntry(local.id, local.taskId, local.reminderAnchor(),
                    local.isTask() ? local.reminderAnchor() : local.calendarEnd(), local.title,
                    local.location, local.description, "拾时", false, false, true, null, local.category, local.reminderMinutesBefore,local.dueAtMillis));
            List<CalendarOccurrence> other = new ArrayList<>();
            java.util.Set<Long> linkedIds = new HashSet<>(store.linkedCalendarIds());
            long horizon = Instant.ofEpochMilli(now).atZone(ZoneId.systemDefault()).plusYears(1).toInstant().toEpochMilli();
            if (AgendaReminderPreferences.includesSystemCalendar(context)) {
                CalendarStore calendar = new CalendarStore(context);
                if (calendar.hasReadPermission()) {
                    ZoneId zone = ZoneId.systemDefault();
                    for (CalendarOccurrence e : calendar.reminderInstances(now, horizon,
                            linkedIds, excluded)) {
                        other.add(e);
                    }
                }
            }
            for (CalendarOccurrence e : ExternalCourseAgenda.merge(context,other,now-86_400_000L,horizon,linkedIds)) {
                if (!e.allDay) entries.add(new AgendaEntry(e.eventId,0,e.startMillis,e.endMillis,
                        e.title,e.location,"",e.calendarName,true,false,true));
            }
        }
        boolean atomicAvailable = VivoAtomicNotification.available(context);
        ReminderTimeline timeline = new ReminderTimeline(entries, now, hidden, snoozeUntil, atomicAvailable ? ReminderSlots.COUNT : 1);
        long refreshAt = timeline.wakeAt;
        if (!timeline.entries.isEmpty()) {
            NotificationChannel channel = new NotificationChannel(CHANNEL, "即将开始的日程", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("活动持续至结束；待办到点后展示 3 分钟，稍后提醒从再次出现时计时");
            channel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            manager.createNotificationChannel(channel);
        }
        boolean deliverable = NotificationDeliveryStatus.canPostReminders(context);
        AgendaEntry[] slots = ReminderSlots.assign(deliverable ? timeline.entries : List.of(), previous);
        SharedPreferences.Editor state = prefs.edit();
        boolean liveUpdates = false;
        for (int i = 0; i < slots.length; i++) {
            AgendaEntry entry = slots[i];
            boolean same = entry != null && entry.key().equals(previous[i]);
            boolean atomic = entry != null && AgendaReminderPreferences.useAtomic(context,
                    entry.system && !ExternalCourseAgenda.isExternal(entry.id), atomicAvailable);
            boolean atomicActive = VivoAtomicNotification.active(manager, NOTIFICATION_ID + i);
            if (!same || atomicActive != atomic)
                VivoAtomicNotification.end(context, manager, NOTIFICATION_ID + i);
            if (entry == null) { AtomicCountdownUpdates.clear(NOTIFICATION_ID + i); state.remove("slot_key:" + i).remove("slot_end:" + i).remove("slot_started:" + i).remove("slot_task:"+i).remove("slot_snooze_limit:"+i); continue; }
            long notifiedAt = same ? prefs.getLong("slot_started:" + i, now) : now;
            // Persisted occurrence identity survives force-stop; the system notification may not.
            boolean update = same && (!atomic || atomicActive);
            Notification notice = notification(context, entry, now, update,
                    NOTIFICATION_ID + i, notifiedAt, ReminderTimeline.nextTitle(entries, entry, now), atomic);
            manager.notify(NOTIFICATION_ID + i, notice);
            long expires = ReminderTimeline.expiresAt(entry,snoozeUntil.getOrDefault(entry.key(),0L));
            if (atomic) AtomicCountdownUpdates.remember(NOTIFICATION_ID + i, notice, entry, now, expires);
            else AtomicCountdownUpdates.clear(NOTIFICATION_ID + i);
            state.putString("slot_key:" + i, entry.key()).putLong("slot_end:" + i, expires)
                    .putBoolean("slot_task:"+i,"task".equals(entry.category))
                    .putLong("slot_snooze_limit:"+i,"event".equals(entry.category)?entry.finish()-30_000L:Long.MAX_VALUE);
            state.putLong("slot_started:" + i, notifiedAt);
            if (atomic) {
                liveUpdates = true;
                refreshAt = Math.min(refreshAt, now + ReminderTimeline.PROGRESS_REFRESH);
                long countdownAt = ReminderTimeline.capsuleDeadline(entry, now) - ReminderTimeline.CAPSULE_COUNTDOWN_WINDOW;
                if (countdownAt > now) refreshAt = Math.min(refreshAt, countdownAt);
            }
        }
        state.apply();
        schedule(context, Math.min(refreshAt, now + 24 * 60 * 60_000L));
        AgendaReminderLiveService.update(context, liveUpdates, refreshAt);
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
        String status = "task".equals(e.category) ? now<e.start ? e.dueAtMillis!=null ? "即将截止" : "待办提醒" : e.dueAtMillis!=null ? "已逾期" : "待完成"
                : now < e.start ? "即将开始" : "进行中";
        long remaining = ReminderTimeline.remainingMillis(e, now);
        boolean startedNotice = now >= e.start && !"event".equals(e.category);
        String targetAction = "task".equals(e.category) ? e.dueAtMillis != null ? "截止" : "提醒" : "开始";
        String remainingText = startedNotice ? status : remaining < 60_000L ? (now < e.start ? "即将" + targetAction : "即将结束")
                : (now < e.start ? ((remaining + 59_999L) / 60_000L) + "分钟后" + targetAction
                        : "还剩" + ((remaining + 59_999L) / 60_000L) + "分钟");
        ZoneId zone = ZoneId.systemDefault();
        DateTimeFormatter time = DateTimeFormatter.ofPattern("M月d日 HH:mm");
        java.time.ZonedDateTime start = Instant.ofEpochMilli(e.start).atZone(zone);
        java.time.ZonedDateTime end = Instant.ofEpochMilli(e.end).atZone(zone);
        String range = "task".equals(e.category) ? time.format(start) : time.format(start) + " — " + (start.toLocalDate().equals(end.toLocalDate())
                ? DateTimeFormatter.ofPattern("HH:mm").format(end) : time.format(end));
        Intent open = AgendaRepository.details(context, e);
        if (!e.system) open.setData(android.net.Uri.parse("chrona://agenda/" + e.key()));
        PendingIntent details = PendingIntent.getActivity(context, notificationId, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = new Notification.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle(e.title).setContentText(status + " · " + range)
                .setContentIntent(details)
                .setOngoing(true).setOnlyAlertOnce(same).setWhen(e.start).setShowWhen(false)
                .setCategory(Notification.CATEGORY_EVENT).setVisibility(Notification.VISIBILITY_PUBLIC)
                .setTimeoutAfter(Math.max(1000, ReminderTimeline.expiresAt(e,
                        AgendaReminderPreferences.prefs(context).getLong("snooze_until:"+e.key(),0)) - now));
        if ("task".equals(e.category) && !e.system)
            builder.addAction(new Notification.Action.Builder(null,"完成",action(context,e,"complete",4)).build());
        else builder.addAction(new Notification.Action.Builder(null,"确认活动",action(context,e,"complete",4)).build());
        if (ReminderTimeline.snoozeAt(e,now)>0)
            builder.addAction(new Notification.Action.Builder(null,"event".equals(e.category)?"稍后提醒":"5 分钟后提醒",action(context,e,"snooze",2)).build());
        builder.addAction(new Notification.Action.Builder(null,"隐藏本次",action(context,e,"hide",3)).build());
        if (atomic) {
            RemoteViews card = AtomicAgendaCard.create(context, e, now, notifiedAt, nextTitle, details);
            String clock = startedNotice ? status : DateTimeFormatter.ofPattern("HH:mm")
                    .format(Instant.ofEpochMilli(now < e.start ? e.start : e.finish()).atZone(zone));
            String capsuleText = startedNotice || !AgendaReminderPreferences.capsuleCountdown(context) ? clock
                    : VivoAtomicNotification.countdownText(ReminderTimeline.capsuleDeadline(e, now) - now,
                            AgendaReminderPreferences.showSeconds(context));
            builder.addExtras(VivoAtomicNotification.extras(context, e.title, status, range,
                    clock,
                    remainingText, details, same && VivoAtomicNotification.active(
                            context.getSystemService(NotificationManager.class), notificationId),
                    card, AtomicAgendaCard.forIsland(context, e, now, notifiedAt, nextTitle, details),
                    AtomicAgendaCard.marqueeConfiguration(), capsuleText));
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
            if (startedNotice) {
                large.setChronometer(R.id.agenda_countdown,SystemClock.elapsedRealtime(),null,false);
                large.setTextViewText(R.id.agenda_countdown,status);
            } else {
                long target = now < e.start ? e.start : e.finish();
                String label = now >= e.start ? "距结束 %s" : "task".equals(e.category)
                        ? e.dueAtMillis!=null ? "距截止 %s" : "距提醒 %s" : "距开始 %s";
                large.setChronometer(R.id.agenda_countdown,SystemClock.elapsedRealtime()+target-now,label,true);
                large.setChronometerCountDown(R.id.agenda_countdown,true);
            }
            builder.setCustomContentView(small).setCustomBigContentView(large).setCustomHeadsUpContentView(small)
                    .setStyle(new Notification.DecoratedCustomViewStyle());
        }
        return builder.build();
    }
    static PendingIntent action(Context context, AgendaEntry e, String action, int code) {
        return PendingIntent.getBroadcast(context, code,
                new Intent(context, AgendaReminderReceiver.class).setAction(action)
                        .setData(android.net.Uri.parse("chrona://reminder/" + e.key())).putExtra("key", e.key()),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    private AgendaReminder() { }
}
