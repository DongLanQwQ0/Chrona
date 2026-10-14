package com.donglan.chrona;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;

/** Local vivo protocol: https://dev.vivo.com.cn/documentCenter/doc/896. */
final class VivoAtomicNotification {
    private static final String PREFIX = "notification.superx.";
    private static final String SCENE = "CHRONA_AGENDA";
    // Firmware SuperXUtils display mask: notification, keyguard and notification shade.
    private static final int REMINDER_DISPLAYS = 0x1 | 0x10 | 0x100;
    static boolean supportedManufacturer() { return "vivo".equalsIgnoreCase(Build.MANUFACTURER); }
    static boolean available(Context context) {
        return supportedManufacturer() && Boolean.TRUE.equals(sceneEnabled(context))
                && context.getSystemService(NotificationManager.class).areNotificationsEnabled();
    }
    /** OEM-documented optional API; null means the system cannot report this capability. */
    static Boolean sceneEnabled(Context context) {
        if (!supportedManufacturer()) return null;
        try {
            java.lang.reflect.Method method = NotificationManager.class.getDeclaredMethod(
                    "getSceneStatus", String.class, String.class);
            method.setAccessible(true);
            return (Boolean) method.invoke(context.getSystemService(NotificationManager.class),
                    context.getPackageName(), SCENE);
        } catch (ReflectiveOperationException | SecurityException failure) {
            android.util.Log.w("ChronaAtomic", "Unable to read atomic scene status", failure);
            return null;
        }
    }
    static Bundle extras(Context context, String title, String status, String range,
            String startTime, String remainingText, PendingIntent details, boolean same,
            android.widget.RemoteViews card, android.widget.RemoteViews islandCard, String animations, String capsuleText) {
        Bundle data = new Bundle();
        if (!supportedManufacturer()) return data;
        Icon icon = Icon.createWithResource(context, R.drawable.ic_chrona_foreground);
        data.putInt(PREFIX + "operation", same ? 1 : 0);
        // This device's CustomSuperXTemplate uses 7, not the public island enum 6.
        data.putInt(PREFIX + "template", 7);
        // Custom template 7 has no default display locations in SuperXUtils.
        data.putInt(PREFIX + "displays", REMINDER_DISPLAYS);
        data.putParcelable(PREFIX + "customTemplate", card);
        data.putBoolean(PREFIX + "disableInvertColor", true);
        data.putString(PREFIX + "animationsConfig", animations);
        data.putString(PREFIX + "scene", SCENE);
        android.util.Log.i("ChronaAtomic", "scene=" + SCENE + " enabled=" + sceneEnabled(context));
        data.putBoolean(PREFIX + "showNotify", true);
        data.putBoolean(PREFIX + "dismissWhenKill", false);
        data.putParcelable(PREFIX + "clickResp", details);
        Bundle base = new Bundle();
        base.putParcelable(PREFIX + "baseInfos.icon", icon);
        base.putCharSequence(PREFIX + "baseInfos.title", title);
        base.putCharSequence(PREFIX + "baseInfos.content", remainingText + "\n" + range);
        data.putBundle(PREFIX + "baseInfos", base);
        Bundle shortInfo = new Bundle();
        shortInfo.putParcelable(PREFIX + "shortInfos.image", icon);
        shortInfo.putString(PREFIX + "shortInfos.describeShort", status);
        shortInfo.putString(PREFIX + "shortInfos.coreInfoShort", remainingText);
        data.putBundle(PREFIX + "shortInfos", shortInfo);
        Bundle island = new Bundle(), left = new Bundle(), right = new Bundle();
        left.putParcelable("island.superx.leftInfo.icon", icon);
        left.putCharSequence("island.superx.leftInfo.content", capsuleText);
        right.putCharSequence("island.superx.rightInfo.content", title);
        island.putInt("island.superx.leftTemplate", 1);
        // Template 4 uses the OEM IslandTextView overflow marquee; retain the full title.
        island.putInt("island.superx.rightTemplate", 4);
        island.putBundle("island.superx.leftInfo", left);
        island.putBundle("island.superx.rightInfo", right);
        // First tap expands the OEM card; its blank area then opens details.
        island.putInt("island.superx.islandClick", 0);
        island.putInt("island.superx.template", 7);
        island.putParcelable("island.superx.customTemplate", islandCard);
        island.putParcelable("island.superx.clickResp", details);
        data.putBundle(PREFIX + "island", island);
        Bundle capsule = new Bundle();
        capsule.putInt(PREFIX + "capsule.state", 1);
        capsule.putParcelable(PREFIX + "capsule.icon", icon);
        capsule.putString(PREFIX + "capsule.content", startTime + " " + title);
        capsule.putParcelable(PREFIX + "capsule.clickResp", details);
        data.putBundle(PREFIX + "capsule", capsule);
        return data;
    }
    static String countdownText(long remainingMillis) {
        return countdownText(remainingMillis, true);
    }
    static String countdownText(long remainingMillis, boolean seconds) {
        if (!seconds) return AtomicAgendaCard.remainingText(remainingMillis);
        if (remainingMillis <= ReminderTimeline.CAPSULE_COUNTDOWN_WINDOW)
            return AtomicAgendaCard.remainingText(remainingMillis, true);
        long base = android.os.SystemClock.elapsedRealtime() + Math.max(0, remainingMillis);
        // SystemUI SuperXChronometer consumes this JSON and ticks locally each second.
        return "{\"base\":" + base + ",\"countDown\":true,\"started\":true,\"minUnit\":\"M\",\"elapsedFormatMS\":\"%02d:%02d\",\"elapsedFormatHMS\":\"%d:%02d:%02d\"}";
    }
    static boolean active(NotificationManager manager, int id) {
        if (!supportedManufacturer()) return false;
        try {
            for (android.service.notification.StatusBarNotification item : manager.getActiveNotifications())
                if (item.getId() == id && item.getNotification().extras.containsKey(PREFIX + "operation")) return true;
        } catch (RuntimeException failure) {
            android.util.Log.w("Chrona", "Unable to inspect vivo atomic notification", failure);
        }
        return false;
    }
    static void end(Context context, NotificationManager manager, int id) {
        if (supportedManufacturer()) {
            try {
                for (android.service.notification.StatusBarNotification item : manager.getActiveNotifications()) {
                    if (item.getId() != id || !item.getNotification().extras.containsKey(PREFIX + "operation")) continue;
                    Bundle data = new Bundle(); data.putInt(PREFIX + "operation", 2);
                    manager.notify(id, Notification.Builder.recoverBuilder(context, item.getNotification())
                            .addExtras(data).setOnlyAlertOnce(true).build());
                }
            } catch (RuntimeException failure) {
                android.util.Log.w("Chrona", "Unable to end vivo atomic notification", failure);
            }
        }
        manager.cancel(id);
    }
    private VivoAtomicNotification() { }
}
