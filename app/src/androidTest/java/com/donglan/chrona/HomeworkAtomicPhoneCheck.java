package com.donglan.chrona;

import android.app.Instrumentation;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Bundle;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.util.List;

/** Explicit live-phone notice check. Never inserts or modifies a homework/task row. */
final class HomeworkAtomicPhoneCheck {
    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context=test.getTargetContext();
        require(context.getPackageName().equals("com.donglan.chrona") && VivoAtomicNotification.supportedManufacturer(),
                "official package on vivo phone required");
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        String scope=args.getString("scope","status");
        if("post".equals(scope)) {
            require(java.util.Arrays.stream(manager.getActiveNotifications()).noneMatch(n->n.getId()==HomeworkNotice.ID),
                    "a current homework notice must not be replaced by this check");
            require(new ZjuHomeworkStore(context).state().captureId>0,"existing homework capture required; fixture never creates one");
            require(VivoAtomicNotification.available(context,"zju_homework_updates"),"existing atomic scene and notification channel must be enabled");
            HomeworkNotice.show(context,List.of(new ZjuSnapshot.Homework("notice-render-check","fixture",
                    "原子岛样式验收 · 作业信息","机器人与无人机基础 · 自主导航实验报告","","",
                    System.currentTimeMillis()+86400000L,false)),List.of(0L));
            long until=System.currentTimeMillis()+5000;
            while(!VivoAtomicNotification.active(manager,HomeworkNotice.ID)&&System.currentTimeMillis()<until)
                android.os.SystemClock.sleep(100);
            require(VivoAtomicNotification.active(manager,HomeworkNotice.ID),"OEM owns a real atomic homework notification");
            long expiry=context.getSharedPreferences("homework_notice",0).getLong("expiry",0);
            require(expiry-System.currentTimeMillis()>HomeworkNotice.DURATION-10000,"full ten-minute expiry");
            context.getSharedPreferences("homework_atomic_phone_check",0).edit().putLong("expiry",expiry).apply();
            if (Boolean.parseBoolean(args.getString("hold", "false"))) {
                // Finishing instrumentation can tear down the OEM host before its next
                // screen event. Keep this explicit render check alive through that event.
                Bundle progress = new Bundle(); progress.putString("stream", "Preview posted; waiting for actual screen-on\n");
                test.sendStatus(1, progress);
                long wakeLimit = System.currentTimeMillis() + 120000;
                while (HomeworkNotice.awaitingScreen(context) && System.currentTimeMillis() < wakeLimit)
                    android.os.SystemClock.sleep(50);
                require(!HomeworkNotice.awaitingScreen(context), "actual first screen event received while preview process is alive");
                expiry = context.getSharedPreferences("homework_notice",0).getLong("expiry",0);
                context.getSharedPreferences("homework_atomic_phone_check",0).edit().putLong("expiry",expiry).commit();
                progress = new Bundle(); progress.putString("stream", "First screen-on deadline="+expiry+"\n"); test.sendStatus(2, progress);
                boolean closeExpected = Boolean.parseBoolean(args.getString("until_closed", "false"));
                while (System.currentTimeMillis() < expiry + 2000 && (!closeExpected
                        || java.util.Arrays.stream(manager.getActiveNotifications()).anyMatch(n->n.getId()==HomeworkNotice.ID)))
                    android.os.SystemClock.sleep(500);
                require(java.util.Arrays.stream(manager.getActiveNotifications()).noneMatch(n->n.getId()==HomeworkNotice.ID),
                        "actual ten-minute OEM notice expired without closing it");
                if (closeExpected) {
                    require(System.currentTimeMillis() < expiry, "OEM X closed preview before timeout");
                    return "PASS: real OEM X closed preview before timeout; no task/homework writes";
                }
                return "PASS: actual first-screen ten-minute OEM expiry; no task/homework writes; expiry="+expiry;
            }
            return "PASS: demo atomic notice posted using existing capture; no task/homework writes; expiry="+expiry;
        }
        boolean active=java.util.Arrays.stream(manager.getActiveNotifications()).anyMatch(n->n.getId()==HomeworkNotice.ID);
        long expiry=context.getSharedPreferences("homework_atomic_phone_check",0).getLong("expiry",0);
        if("started".equals(scope)) {
            require(active && !HomeworkNotice.awaitingScreen(context), "first real wake started the posted preview");
            expiry=context.getSharedPreferences("homework_notice",0).getLong("expiry",0);
            require(expiry>System.currentTimeMillis() && expiry<=System.currentTimeMillis()+HomeworkNotice.DURATION,"first-screen deadline is active");
            context.getSharedPreferences("homework_atomic_phone_check",0).edit().putLong("expiry",expiry).commit();
            return "PASS: actual first-screen deadline="+expiry+"; no task/homework writes";
        }
        if("closed".equals(scope)) {require(!active,"OEM X removed the homework notice");return "PASS: OEM close removed notice";}
        if("expired".equals(scope)) {
            require(expiry>0&&System.currentTimeMillis()>=expiry,"wait for actual ten minutes");
            require(!active,"OEM homework notice absent after ten minutes");
            return "PASS: actual ten-minute OEM expiry; no task/homework writes";
        }
        return "active="+active+" atomic="+VivoAtomicNotification.active(manager,HomeworkNotice.ID)
                +" remainingMs="+Math.max(0,expiry-System.currentTimeMillis());
    }
    private static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
}
