package com.donglan.chrona;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/** Keeps live atomic cards fresh while visible; no wake lock, network or permanent idle service. */
public final class AgendaReminderLiveService extends Service {
    private static final String CHANNEL = "live_agenda_updates";
    private static final int ID = 0x4348f1;
    private static volatile AgendaReminderLiveService live;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable tick = () -> AgendaReminder.requestRefresh(this);
    private final Runnable seconds = new Runnable() {
        @Override public void run() {
            AgendaReminder.WORKER.execute(() -> {
                AtomicCountdownUpdates.tick(AgendaReminderLiveService.this);
                long delay = AtomicCountdownUpdates.nextDelay(AgendaReminderLiveService.this);
                handler.post(() -> { if (live == AgendaReminderLiveService.this) scheduleSeconds(delay); });
            });
        }
    };
    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { AgendaReminder.requestRefresh(AgendaReminderLiveService.this); }
    };
    static void update(Context context, boolean needed, long nextAt) {
        needed = needed && NotificationDeliveryStatus.canPostReminders(context);
        if (!needed) { context.stopService(new Intent(context, AgendaReminderLiveService.class)); return; }
        AgendaReminderLiveService current = live;
        if (current != null) {
            current.handler.post(() -> { if (live == current) { current.scheduleTick(nextAt); current.scheduleSeconds(1000); } });
            return;
        }
        try { context.startForegroundService(new Intent(context, AgendaReminderLiveService.class).putExtra("next_at", nextAt)); }
        catch (RuntimeException failure) {
            android.util.Log.w("ChronaReminder", "Live updates could not start; alarm delivery remains scheduled", failure);
        }
    }
    @Override public void onCreate() {
        super.onCreate(); live = this;
        handler.postDelayed(seconds, 1000);
        IntentFilter events = new IntentFilter(Intent.ACTION_SCREEN_ON); events.addAction(Intent.ACTION_USER_PRESENT);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screen, events, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, events);
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (!AgendaReminderPreferences.enabled(this) || !NotificationDeliveryStatus.canPostReminders(this)) { stopSelf(); return START_NOT_STICKY; }
        NotificationManager manager = getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL, "日程后台更新", NotificationManager.IMPORTANCE_MIN);
        channel.setSound(null, null); channel.enableVibration(false); channel.setShowBadge(false);
        manager.createNotificationChannel(channel);
        PendingIntent open = PendingIntent.getActivity(this, ID,
                new Intent(this, DashboardActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL).setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle("日程提醒正在更新").setContentText("提醒结束后自动停止")
                .setContentIntent(open).setOngoing(true).setOnlyAlertOnce(true).setShowWhen(false).build();
        if (Build.VERSION.SDK_INT >= 34) startForeground(ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        else startForeground(ID, notification);
        scheduleTick(intent == null ? System.currentTimeMillis() + ReminderTimeline.PROGRESS_REFRESH
                : intent.getLongExtra("next_at", System.currentTimeMillis() + ReminderTimeline.PROGRESS_REFRESH));
        AgendaReminder.requestRefresh(this);
        android.util.Log.i("ChronaReminder", "Live atomic updates started");
        return START_STICKY;
    }
    private void scheduleTick(long nextAt) {
        handler.removeCallbacks(tick);
        long delay = Math.max(1000, Math.min(ReminderTimeline.PROGRESS_REFRESH, nextAt - System.currentTimeMillis()));
        handler.postDelayed(tick, delay);
    }
    private void scheduleSeconds(long delay) {
        handler.removeCallbacks(seconds); handler.postDelayed(seconds, delay);
    }
    @Override public void onDestroy() {
        handler.removeCallbacksAndMessages(null); unregisterReceiver(screen);
        if (live == this) live = null;
        stopForeground(STOP_FOREGROUND_REMOVE);
        android.util.Log.i("ChronaReminder", "Live atomic updates stopped"); super.onDestroy();
    }
    @Override public IBinder onBind(Intent intent) { return null; }
}
