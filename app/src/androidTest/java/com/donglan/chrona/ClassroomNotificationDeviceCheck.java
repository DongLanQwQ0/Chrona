package com.donglan.chrona;

import android.Manifest;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomProgress;
import java.lang.reflect.Field;
import org.json.JSONObject;

/** Synthetic notifications only; no account credentials, network, activities or permission changes. */
final class ClassroomNotificationDeviceCheck {
    static String run(Instrumentation test) throws Exception {
        Context target = test.getTargetContext();
        NotificationManager manager = target.getSystemService(NotificationManager.class);
        require(java.util.Arrays.stream(manager.getActiveNotifications()).noneMatch(n -> n.getTag() != null
                && n.getTag().startsWith("classroom:")), "Run only when no classroom notifications are active");
        String id = "notice-test-" + java.util.UUID.randomUUID();
        SharedPreferences prefs = target.getSharedPreferences(id, Context.MODE_PRIVATE);
        boolean[] denied = {false};
        Context fixture = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public SharedPreferences getSharedPreferences(String name, int mode) {
                return "chrona_zju".equals(name) ? prefs : super.getSharedPreferences(name, mode);
            }
            @Override public int checkSelfPermission(String permission) {
                return denied[0] && Manifest.permission.POST_NOTIFICATIONS.equals(permission)
                        ? PackageManager.PERMISSION_DENIED : super.checkSelfPermission(permission);
            }
        };
        prefs.edit().putString("account", id).putString("credentials", "synthetic-unused")
                .putBoolean("enabled", true).commit();
        long firstStart=java.time.ZonedDateTime.of(2026,10,28,9,0,0,0,ClassroomLesson.ZONE).toInstant().toEpochMilli();
        long secondStart=java.time.ZonedDateTime.of(2026,10,29,13,30,0,0,ClassroomLesson.ZONE).toInstant().toEpochMilli();
        ClassroomLesson lesson = new ClassroomLesson("fixture", "lesson", "测试课堂", "第 3 讲", "", "6", firstStart);
        String tag = "classroom:" + id + ":fixture:lesson";
        // Preserve the production observer if this process already created one.
        Field observed = field("observedPreferences"), changes = field("accountChanges");
        Object priorPreferences = observed.get(null), priorChanges = changes.get(null);
        ClassroomNotifications notices = null;
        ClassroomNotifications other = null, unknown = null;
        boolean postable = manager.areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33
                || target.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED);
        try {
            notices = new ClassroomNotifications(fixture, id, lesson);
            notices.progress(new ClassroomProgress("整理课堂", 2, 5, ""), true);
            Notification running = latest(notices);
            require((running.flags & Notification.FLAG_ONGOING_EVENT) != 0, "running ongoing");
            require(running.contentIntent != null, "running opens the retained lesson session");
            require(running.extras.getInt(Notification.EXTRA_PROGRESS) == 2
                    && running.extras.getInt(Notification.EXTRA_PROGRESS_MAX) == 5, "measured progress");
            require(running.visibility == Notification.VISIBILITY_PRIVATE, "lock screen privacy");
            if (postable) require(active(manager, tag) == 1, "running posted");
            notices.finished("总结已完成", true);
            Notification result = latest(notices);
            require((result.flags & Notification.FLAG_ONGOING_EVENT) == 0
                    && (result.flags & Notification.FLAG_AUTO_CANCEL) != 0, "result flags");
            require(result.contentIntent != null, "saved detail link");
            require("课堂处理完成 · 测试课堂".contentEquals(result.extras.getCharSequence(Notification.EXTRA_TITLE)),"result course title");
            require("2026-10-28 09:00 · 第 3 讲\n总结已完成".contentEquals(result.extras.getCharSequence(Notification.EXTRA_TEXT))
                    &&result.extras.getCharSequence(Notification.EXTRA_TEXT).equals(result.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),"result date/start/lesson in compact and expanded content");
            require(result.visibility==Notification.VISIBILITY_PRIVATE,"result lockscreen privacy");
            require(!running.getChannelId().equals(result.getChannelId()), "result alert channel");
            if (postable) require(active(manager, tag) == 1, "result replaces progress");
            other=new ClassroomNotifications(fixture,id,new ClassroomLesson("fixture","lesson-two","测试课堂","第 4 讲","","6",secondStart));
            other.finished("素材已就绪",true);
            notices.finished("素材已就绪",true);
            require("2026-10-28 09:00 · 第 3 讲\n素材已就绪".contentEquals(latest(notices).extras.getCharSequence(Notification.EXTRA_TEXT))
                    &&"2026-10-29 13:30 · 第 4 讲\n素材已就绪".contentEquals(latest(other).extras.getCharSequence(Notification.EXTRA_TEXT)),"same-name download results identify distinct dates and lessons");
            if(postable)require(active(manager,tag)==1&&active(manager,"classroom:"+id+":fixture:lesson-two")==1,"same-name results coexist independently");
            unknown=new ClassroomNotifications(fixture,id,new ClassroomLesson("fixture","unknown-start","测试课堂","已知标题","","6",0));
            unknown.finished("素材已就绪",true);
            require("已知标题\n素材已就绪".contentEquals(latest(unknown).extras.getCharSequence(Notification.EXTRA_TEXT)),"unknown start adds no invented date or periods");
            other.clear();unknown.clear();
            for (String outcome : new String[]{"已停止，已完成内容已保存", "操作失败，请重试"}) {
                notices.progress(ClassroomProgress.waiting("整理课堂", ""), true);
                notices.finished(outcome, false);
                require("课堂处理已结束 · 测试课堂".contentEquals(latest(notices).extras.getCharSequence(Notification.EXTRA_TITLE)), "no false success");
                require(("2026-10-28 09:00 · 第 3 讲\n"+outcome).contentEquals(latest(notices).extras.getCharSequence(Notification.EXTRA_TEXT)), "terminal identity and original outcome");
            }
            notices.clear();
            if (Build.VERSION.SDK_INT >= 33) {
                denied[0] = true;
                notices.progress(ClassroomProgress.waiting("整理课堂", ""), true);
                notices.finished("总结已完成", true);
                require(active(manager, tag) == 0, "denied permission does not post");
                denied[0] = false;
                notices.repost();
                if (postable) require(active(manager, tag) == 1, "permission callback posts latest result");
            }
            prefs.edit().putString("account", "another-synthetic-account").commit();
            ClassroomNotifications.clearInactive(fixture);
            notices.progress(ClassroomProgress.waiting("整理课堂", ""), true);
            require(active(manager, tag) == 0 && latest(notices) == null, "account switch blocks stale repost");
            return new JSONObject().put("synthetic", true).put("lifecycle", true)
                    .put("systemPostingVerified", postable).put("permissionDenialVerified", Build.VERSION.SDK_INT >= 33)
                    .put("accountSwitch", true).put("resultLessonIdentity",true).put("sameNameDownloadsDistinct",true).put("unknownStartNotInvented",true).toString();
        } finally {
            if (notices != null) notices.clear();
            if (other != null) other.clear();
            if (unknown != null) unknown.clear();
            if (priorPreferences == null && observed.get(null) == prefs) {
                prefs.unregisterOnSharedPreferenceChangeListener((SharedPreferences.OnSharedPreferenceChangeListener) changes.get(null));
                observed.set(null, priorPreferences); changes.set(null, priorChanges);
            }
            target.deleteSharedPreferences(id);
        }
    }
    private static Field field(String name) throws Exception {
        Field field = ClassroomNotifications.class.getDeclaredField(name); field.setAccessible(true); return field;
    }
    private static Notification latest(ClassroomNotifications notices) throws Exception { return (Notification) field("latest").get(notices); }
    private static long active(NotificationManager manager, String tag) {
        return java.util.Arrays.stream(manager.getActiveNotifications()).filter(n -> tag.equals(n.getTag())).count();
    }
    private static void require(boolean value, String message) { if (!value) throw new AssertionError(message); }
}
