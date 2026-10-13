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
    static boolean supportedManufacturer() { return "vivo".equalsIgnoreCase(Build.MANUFACTURER); }
    static Bundle extras(Context context, String title, String status, String range,
            String location, String startTime, PendingIntent details, boolean same) {
        Bundle data = new Bundle();
        if (!supportedManufacturer()) return data;
        Icon icon = Icon.createWithResource(context, R.drawable.ic_notifications);
        data.putInt(PREFIX + "operation", same ? 1 : 0);
        data.putInt(PREFIX + "template", 1);
        data.putString(PREFIX + "scene", "METTING");
        data.putBoolean(PREFIX + "showNotify", true);
        data.putBoolean(PREFIX + "dismissWhenKill", false);
        data.putParcelable(PREFIX + "clickResp", details);
        Bundle base = new Bundle();
        base.putParcelable(PREFIX + "baseInfos.icon", icon);
        base.putCharSequence(PREFIX + "baseInfos.title", title);
        base.putCharSequence(PREFIX + "baseInfos.content", range);
        data.putBundle(PREFIX + "baseInfos", base);
        Bundle info = new Bundle();
        info.putString(PREFIX + "infos.describe", location.trim().isEmpty() ? range : location);
        info.putString(PREFIX + "infos.coreInfo", status + " · " + startTime);
        info.putParcelable(PREFIX + "infos.image", icon);
        info.putParcelable(PREFIX + "infos.imageClickResp", details);
        data.putBundle(PREFIX + "infos", info);
        Bundle island = new Bundle(), left = new Bundle(), right = new Bundle();
        left.putParcelable("island.superx.leftInfo.icon", icon);
        left.putCharSequence("island.superx.leftInfo.content", startTime);
        right.putCharSequence("island.superx.rightInfo.content", title);
        right.putParcelable("island.superx.rightInfo.clickResp", details);
        island.putInt("island.superx.leftTemplate", 1);
        island.putInt("island.superx.rightTemplate", 4);
        island.putBundle("island.superx.leftInfo", left);
        island.putBundle("island.superx.rightInfo", right);
        island.putInt("island.superx.islandClick", 1);
        island.putParcelable("island.superx.clickResp", details);
        data.putBundle(PREFIX + "island", island);
        return data;
    }
    static boolean active(NotificationManager manager, int id) {
        if (!supportedManufacturer()) return false;
        try {
            for (android.service.notification.StatusBarNotification item : manager.getActiveNotifications())
                if (item.getId() == id) return true;
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
