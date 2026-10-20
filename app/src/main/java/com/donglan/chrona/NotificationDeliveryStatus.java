package com.donglan.chrona;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.provider.Settings;

/** Read-only delivery prerequisites shared by reminder UI and notification publishers. */
final class NotificationDeliveryStatus {
    final boolean appAllowed;
    final boolean channelCreated;
    final boolean channelAllowed;

    private NotificationDeliveryStatus(boolean appAllowed, boolean channelCreated, boolean channelAllowed) {
        this.appAllowed = appAllowed;
        this.channelCreated = channelCreated;
        this.channelAllowed = channelAllowed;
    }

    static NotificationDeliveryStatus read(Context context) {
        return read(context, AgendaReminder.CHANNEL);
    }

    static NotificationDeliveryStatus read(Context context, String channelId) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        boolean permission = Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        NotificationChannel channel = manager.getNotificationChannel(channelId);
        // The publisher creates this channel on its first real reminder. Absence is not a user block.
        return new NotificationDeliveryStatus(permission && manager.areNotificationsEnabled(),
                channel != null, channel == null || channel.getImportance() != NotificationManager.IMPORTANCE_NONE);
    }

    boolean canPostReminders() { return appAllowed && channelAllowed; }

    static boolean canPostReminders(Context context) { return read(context).canPostReminders(); }

    String channelSummary() {
        if (!channelAllowed) return "提醒频道已关闭";
        return channelCreated ? "已开启" : "首次提醒时创建";
    }

    static Intent appSettings(Context context) {
        return new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName());
    }

    static Intent channelSettings(Context context) {
        return new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.getPackageName())
                .putExtra(Settings.EXTRA_CHANNEL_ID, AgendaReminder.CHANNEL);
    }
}
