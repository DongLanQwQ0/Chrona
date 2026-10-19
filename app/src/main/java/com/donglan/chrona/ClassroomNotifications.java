package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.SystemClock;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomProgress;

/** One private, replaceable notice per classroom operation. No course content on the lock screen. */
final class ClassroomNotifications {
    static final int PERMISSION_REQUEST = 2042;
    private static final String RUNNING = "chrona_classroom_running", RESULTS = "chrona_classroom_results";
    private final Context app;
    private final String tag;
    private final String account;
    private final ClassroomLesson lesson;
    private static SharedPreferences observedPreferences;
    private static SharedPreferences.OnSharedPreferenceChangeListener accountChanges;
    private Notification latest;
    private long lastPosted;

    ClassroomNotifications(Context context, String account, ClassroomLesson lesson) {
        app = context.getApplicationContext();
        this.account = account;
        this.lesson = lesson;
        tag = "classroom:" + account + ":" + lesson.courseId + ":" + lesson.id;
        try { observeAccount(app); }
        catch (RuntimeException unavailable) { /* Account gating is also checked at each post and detail open. */ }
    }

    private static synchronized void observeAccount(Context app) {
        if (observedPreferences == null) {
            observedPreferences = new ZjuSettingsStore(app).prefs;
            accountChanges = (preferences, key) -> clearInactive(app);
            observedPreferences.registerOnSharedPreferenceChangeListener(accountChanges);
        }
        clearInactive(app);
    }

    static void clearInactive(Context context) {
        try {
            ZjuSettingsStore settings = new ZjuSettingsStore(context);
            String active = settings.enabled() && settings.configured() ? "classroom:" + settings.account() + ":" : "";
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager == null) return;
            for (android.service.notification.StatusBarNotification notice : manager.getActiveNotifications()) {
                String tag = notice.getTag();
                if (tag != null && tag.startsWith("classroom:") && (active.isEmpty() || !tag.startsWith(active)))
                    manager.cancel(tag, notice.getId());
            }
        } catch (RuntimeException unavailable) { /* Notifications must not interrupt account changes. */ }
    }

    static void requestPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= 33 && activity.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
            try { activity.requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, PERMISSION_REQUEST); }
            catch (RuntimeException unavailable) { /* Processing also works without notification permission. */ }
    }

    static Notification foreground(Context context, int count) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(RUNNING, "课堂处理进度", NotificationManager.IMPORTANCE_LOW));
        PendingIntent open = PendingIntent.getActivity(context, 2043,
                new Intent(context, ClassroomSummaryActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(context, RUNNING).setSmallIcon(R.drawable.ic_chrona_foreground)
                .setContentTitle("课堂后台处理").setContentText("正在处理 " + count + " 节课堂")
                .setVisibility(Notification.VISIBILITY_PRIVATE).setOngoing(true).setOnlyAlertOnce(true)
                .setContentIntent(open).setCategory(Notification.CATEGORY_PROGRESS).build();
    }

    synchronized void progress(ClassroomProgress value, boolean force) {
        try {
        String text = value.text();
        latest = builder(RUNNING, "课堂总结处理中", text).setOngoing(true).setOnlyAlertOnce(true)
                .setContentIntent(openLesson()).setProgress(value.total, value.completed, value.total == 0).build();
        if (force || SystemClock.elapsedRealtime() - lastPosted >= 1000) repost();
        } catch (Exception unavailable) { /* Notification failure is independent of classroom work. */ }
    }

    synchronized void finished(String result, boolean success) {
        try {
        latest = builder(RESULTS, success ? "课堂处理完成" : "课堂处理已结束", result)
                .setAutoCancel(true).setOngoing(false).setContentIntent(openLesson()).build();
        repost();
        } catch (Exception unavailable) { clear(); }
    }

    private PendingIntent openLesson() throws org.json.JSONException {
        Intent intent = new Intent(app, ClassroomLessonActivity.class)
                .setData(new Uri.Builder().scheme("chrona").authority("classroom").appendPath(account)
                        .appendPath(lesson.courseId).appendPath(lesson.id).build())
                .putExtra(ClassroomLessonActivity.EXTRA_ACCOUNT, account)
                .putExtra(ClassroomLessonActivity.EXTRA_LESSON, lesson.json().toString());
        return PendingIntent.getActivity(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification.Builder builder(String channel, String title, String text) {
        return new Notification.Builder(app, channel).setSmallIcon(R.drawable.ic_chrona_foreground)
                .setContentTitle(title).setContentText(text).setStyle(new Notification.BigTextStyle().bigText(text))
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setCategory(Notification.CATEGORY_PROGRESS);
    }

    synchronized void repost() {
        try {
        ZjuSettingsStore settings = new ZjuSettingsStore(app);
        if (!settings.enabled() || !settings.configured() || !account.equals(settings.account())) { clear(); return; }
        if (latest == null || Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        if (manager == null || !manager.areNotificationsEnabled()) return;
        manager.createNotificationChannel(new NotificationChannel(RUNNING, "课堂处理进度", NotificationManager.IMPORTANCE_LOW));
        manager.createNotificationChannel(new NotificationChannel(RESULTS, "课堂处理结果", NotificationManager.IMPORTANCE_DEFAULT));
        manager.notify(tag, 0, latest); lastPosted = SystemClock.elapsedRealtime();
        } catch (RuntimeException unavailable) { /* Permission or notification service may change at any time. */ }
    }

    synchronized void clear() {
        latest = null;
        try {
        NotificationManager manager = app.getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(tag, 0);
        } catch (RuntimeException unavailable) { /* Notification cleanup must not fail classroom work. */ }
    }
}
