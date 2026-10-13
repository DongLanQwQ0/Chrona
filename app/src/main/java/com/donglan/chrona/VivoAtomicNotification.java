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
    // The OEM progress template requires two node icons. Empty nodes keep the
    // bar clear instead of placing decorative glyphs over its endpoints.
    private static final Icon PROGRESS_NODE = Icon.createWithBitmap(android.graphics.Bitmap.createBitmap(
            1, 1, android.graphics.Bitmap.Config.ARGB_8888));
    static boolean supportedManufacturer() { return "vivo".equalsIgnoreCase(Build.MANUFACTURER); }
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
            String location, String source, String startTime, int remainingPercent, String remainingText,
            PendingIntent details, boolean same) {
        Bundle data = new Bundle();
        if (!supportedManufacturer()) return data;
        Icon icon = Icon.createWithResource(context, R.drawable.ic_chrona_foreground);
        data.putInt(PREFIX + "operation", same ? 1 : 0);
        data.putInt(PREFIX + "template", 2);
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
        Bundle info = new Bundle();
        java.util.ArrayList<Icon> nodes = new java.util.ArrayList<>();
        nodes.add(PROGRESS_NODE); nodes.add(PROGRESS_NODE);
        info.putParcelableArrayList(PREFIX + "infos.nodeIcon", nodes);
        info.putInt(PREFIX + "infos.progress", Math.max(0, Math.min(100, remainingPercent)));
        int accent = UiStyle.colors(context).primary;
        info.putInt(PREFIX + "infos.progressColor", accent);
        info.putInt(PREFIX + "infos.BgColor", (accent & 0x00ffffff) | 0x33000000);
        data.putBundle(PREFIX + "infos", info);
        Bundle shortInfo = new Bundle();
        shortInfo.putParcelable(PREFIX + "shortInfos.image", icon);
        shortInfo.putString(PREFIX + "shortInfos.describeShort", status);
        shortInfo.putString(PREFIX + "shortInfos.coreInfoShort", remainingText);
        data.putBundle(PREFIX + "shortInfos", shortInfo);
        Bundle island = new Bundle(), left = new Bundle(), right = new Bundle();
        left.putParcelable("island.superx.leftInfo.icon", icon);
        left.putCharSequence("island.superx.leftInfo.content", startTime);
        right.putCharSequence("island.superx.rightInfo.content", title);
        island.putInt("island.superx.leftTemplate", 1);
        island.putInt("island.superx.rightTemplate", 4);
        island.putBundle("island.superx.leftInfo", left);
        island.putBundle("island.superx.rightInfo", right);
        // First tap expands the OEM card; its blank area then opens details.
        island.putInt("island.superx.islandClick", 0);
        island.putInt("island.superx.template", 2);
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
