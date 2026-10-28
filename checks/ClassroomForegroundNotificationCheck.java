package com.donglan.chrona;

import android.app.Notification;
import android.app.NotificationManager;
import android.os.Handler;
import android.os.SystemClock;
import com.donglan.chrona.classroom.ClassroomProgress;

/** Both production classes execute here; adapters only replace the Android system boundary. */
public final class ClassroomForegroundNotificationCheck {
    private static final NotificationManager manager=NotificationManager.INSTANCE;
    private static void require(boolean condition,String label){if(!condition)throw new AssertionError(label);}
    private static Notification foreground(){return manager.notices.get("null@2043");}
    private static String tag(ClassroomLessonActivity.Session s){return "classroom:"+s.account+":"+s.selected.courseId+":"+s.selected.id+"@0";}
    private static ClassroomLessonActivity.Session session(String id){
        var session=new ClassroomLessonActivity.Session(id);
        session.notifications.progress(new ClassroomProgress("准备素材",2,5,"下载截图"),true);return session;
    }
    private static void stop(ClassroomWorkService service,ClassroomLessonActivity.Session... sessions){
        for(var s:sessions){ClassroomWorkService.finished(s);s.notifications.clear();}service.onDestroy();Handler.drain();
    }
    public static void main(String[] args){
        var a=session("A");var b=session("B");var service=new ClassroomWorkService();
        require(manager.notices.containsKey(tag(a)),"without a foreground owner progress is not swallowed");
        ClassroomWorkService.submit(a,()->require(service.promoted,"legal foreground promotion precedes work"));service.onStartCommand(null,0,1);Handler.drain();
        require(!manager.notices.containsKey(tag(a))&&foreground()==a.notifications.runningSnapshot(),"single detailed foreground replaces former running tag");
        require(foreground().extras.getInt(Notification.EXTRA_PROGRESS)==2&&foreground().extras.getInt(Notification.EXTRA_PROGRESS_MAX)==5,"actual measured progress transferred");
        require(foreground().contentIntent.intent.target==ClassroomLessonActivity.class&&foreground().visibility==Notification.VISIBILITY_PRIVATE,"single detail entry and lockscreen privacy");
        int posts=manager.posts;
        a.notifications.progress(new ClassroomProgress("准备素材",3,5,"下载截图"),false);Handler.drain();
        require(manager.posts==posts,"one-second progress throttle preserved");
        SystemClock.now+=1000;a.notifications.progress(new ClassroomProgress("准备素材",4,5,"下载截图"),false);
        require(foreground().extras.getInt(Notification.EXTRA_PROGRESS)==2,"worker callback does not mutate foreground before main dispatch");Handler.drain();
        require(foreground().extras.getInt(Notification.EXTRA_PROGRESS)==4,"main dispatch updates foreground");
        ClassroomWorkService.submit(b,()->{});service.onStartCommand(null,0,2);Handler.drain();
        String text=foreground().extras.getCharSequence(Notification.EXTRA_BIG_TEXT).toString();
        require(text.contains("A")&&text.contains("B")&&text.contains("4 / 5")&&text.contains("2 / 5"),"multi foreground contains every active lesson and progress");
        require(foreground().contentIntent.intent.target==ClassroomSummaryActivity.class&&!manager.notices.containsKey(tag(a))&&!manager.notices.containsKey(tag(b)),"multi catalog entry; no duplicate running tags");
        a.notifications.finished("已完成",true);ClassroomWorkService.finished(a);Handler.drain();
        require(manager.notices.get(tag(a)).autoCancel&&!manager.notices.get(tag(a)).ongoing,"result remains independent");
        require(foreground()==b.notifications.runningSnapshot()&&foreground().contentIntent.intent.target==ClassroomLessonActivity.class,"remaining lesson detail and entry restored");
        var monitor=session("monitor");monitor.monitorOwned=true;ClassroomWorkService.submitMonitor(monitor,()->{});
        monitor.notifications.repost();Handler.drain();
        require(manager.notices.containsKey(tag(monitor)),"monitor owner progress not swallowed by unrelated manual owner");
        b.notifications.progress(ClassroomProgress.waiting("稍后到达",""),true);ClassroomWorkService.finished(b);posts=manager.posts;Handler.drain();
        require(service.stopped&&!service.promoted&&foreground()==null&&manager.posts==posts,"completed owner cannot be revived by delayed callback; monitor does not retain it");
        stop(service,a,b,monitor);
        batchGap();rejected();permissionsAndAccount();resultIdentity();
        System.out.println("ClassroomForegroundNotificationCheck PASS: single/multi/remaining/result entry; 1s throttle/main dispatch/no resurrection; batch gap; start and promotion denial; notification permission off; monitor isolation; account cleanup; same-name result date/time/lesson identity and success/failure/unknown-time privacy");
    }
    private static void batchGap(){
        Object owner=new Object();var service=new ClassroomWorkService();var app=new android.content.Context();
        ClassroomWorkService.holdBatch(owner,app,()->require(service.promoted,"batch starts after promotion"),()->{});service.onStartCommand(null,0,3);
        var a=session("batch-A");ClassroomWorkService.submitBatch(owner,a,()->{});Handler.drain();
        require(foreground()==a.notifications.runningSnapshot()&&!manager.notices.containsKey(tag(a)),"batch detail shares foreground slot");
        ClassroomWorkService.finished(a);require(service.promoted&&!service.stopped&&foreground()!=null,"foreground survives empty batch interval");
        var b=session("batch-B");ClassroomWorkService.submitBatch(owner,b,()->{});Handler.drain();
        require(foreground()==b.notifications.runningSnapshot(),"next batch lesson replaces same foreground slot");
        ClassroomWorkService.finished(b);ClassroomWorkService.releaseBatch(owner);require(service.stopped&&foreground()==null,"batch release removes foreground");stop(service,a,b);
    }
    private static void rejected(){
        var a=session("start-denied");a.app.reject=true;ClassroomWorkService.submit(a,()->{throw new AssertionError("denied start ran");});
        require(a.failed&&manager.notices.containsKey(tag(a)),"start rejection does not silently swallow ownerless progress");a.notifications.clear();
        var b=session("promotion-denied");var service=new ClassroomWorkService();service.rejectPromotion=true;
        ClassroomWorkService.submit(b,()->{throw new AssertionError("unpromoted work ran");});service.onStartCommand(null,0,4);
        require(b.failed&&service.stopped&&!ClassroomWorkService.updateProgress(b.notifications),"failed promotion cannot claim notification ownership");stop(service,b);
    }
    private static void permissionsAndAccount(){
        var a=new ClassroomLessonActivity.Session("permission-off");a.app.denied=true;manager.enabled=false;
        a.notifications.progress(ClassroomProgress.waiting("整理课堂",""),true);var service=new ClassroomWorkService();
        ClassroomWorkService.submit(a,()->require(service.promoted,"permission denial still legally promotes before work"));service.onStartCommand(null,0,5);Handler.drain();
        require(service.promotions==1&&service.promoted&&manager.notices.isEmpty(),"permission denial retains FGS lifecycle without extra drawer posts");
        a.notifications.progress(new ClassroomProgress("整理课堂",1,2,""),true);Handler.drain();
        require(service.promoted&&manager.notices.isEmpty(),"permission denial does not interrupt work");
        a.app.denied=false;manager.enabled=true;a.notifications.repost();Handler.drain();require(foreground()!=null&&!manager.notices.containsKey(tag(a)),"permission restore reposts only shared foreground");
        ZjuSettingsStore.account="other";a.notifications.repost();Handler.drain();
        require(a.notifications.runningSnapshot()==null&&!manager.notices.containsKey(tag(a))&&foreground().extras.getCharSequence(Notification.EXTRA_TITLE).equals("课堂后台处理"),"account switch removes detailed foreground and tagged stale content");
        stop(service,a);ZjuSettingsStore.account="account";
    }
    private static void resultIdentity(){
        var context=new android.content.Context();
        long first=java.time.ZonedDateTime.of(2026,10,28,9,0,0,0,com.donglan.chrona.classroom.ClassroomLesson.ZONE).toInstant().toEpochMilli();
        long second=java.time.ZonedDateTime.of(2026,10,29,13,30,0,0,com.donglan.chrona.classroom.ClassroomLesson.ZONE).toInstant().toEpochMilli();
        var a=new ClassroomNotifications(context,"account",new com.donglan.chrona.classroom.ClassroomLesson("result-a","高等数学","第 3 讲",first));
        var b=new ClassroomNotifications(context,"account",new com.donglan.chrona.classroom.ClassroomLesson("result-b","高等数学","第 4 讲",second));
        a.finished("素材已就绪",true);b.finished("素材已就绪",true);
        Notification noticeA=manager.notices.get("classroom:account:course:result-a@0"),noticeB=manager.notices.get("classroom:account:course:result-b@0");
        require(noticeA!=null&&noticeB!=null,"same-name lessons keep distinct result tags");
        require("课堂处理完成 · 高等数学".contentEquals(noticeA.extras.getCharSequence(Notification.EXTRA_TITLE)),"result title includes actual course and success outcome");
        require("2026-10-28 09:00 · 第 3 讲\n素材已就绪".contentEquals(noticeA.extras.getCharSequence(Notification.EXTRA_TEXT)),"first download identifies actual local date/start/lesson and result");
        require("2026-10-29 13:30 · 第 4 讲\n素材已就绪".contentEquals(noticeB.extras.getCharSequence(Notification.EXTRA_TEXT)),"same-name second download is visibly distinguishable");
        for(Notification notice:new Notification[]{noticeA,noticeB}){
            require(notice.extras.getCharSequence(Notification.EXTRA_TEXT).equals(notice.extras.getCharSequence(Notification.EXTRA_BIG_TEXT)),"compact and expanded result carry same identity");
            require(notice.visibility==Notification.VISIBILITY_PRIVATE&&notice.autoCancel&&!notice.ongoing&&notice.contentIntent.intent.target==ClassroomLessonActivity.class,"result privacy/flags/detail entry preserved");
            require("chrona_classroom_results".equals(notice.channel),"result alert channel preserved");
        }
        b.finished("操作失败，请重试",false);noticeB=manager.notices.get("classroom:account:course:result-b@0");
        require("课堂处理已结束 · 高等数学".contentEquals(noticeB.extras.getCharSequence(Notification.EXTRA_TITLE))
                &&"2026-10-29 13:30 · 第 4 讲\n操作失败，请重试".contentEquals(noticeB.extras.getCharSequence(Notification.EXTRA_TEXT)),"failure preserves same lesson identity and original reason");
        var unknown=new ClassroomNotifications(context,"account",new com.donglan.chrona.classroom.ClassroomLesson("result-unknown","高等数学","已知课堂标题",0));
        unknown.finished("素材已就绪",true);Notification unknownNotice=manager.notices.get("classroom:account:course:result-unknown@0");
        require("已知课堂标题\n素材已就绪".contentEquals(unknownNotice.extras.getCharSequence(Notification.EXTRA_TEXT)),"unknown start time adds no invented date or period");
        a.clear();b.clear();unknown.clear();
    }
}
