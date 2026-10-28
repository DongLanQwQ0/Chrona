package com.donglan.chrona;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.SystemClock;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomProgress;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.json.JSONObject;

/** Real Android lifecycle, synthetic latch workers only; never calls school or AI clients. */
final class ClassroomWorkDeviceCheck {
    private static final String ACCOUNT="00000000-0000-0000-0000-000000000036";
    static String run(Instrumentation test) throws Exception {
        Context context=test.getTargetContext();
        require(context.getPackageName().endsWith(".validation"),"Validation package only");
        require(Build.FINGERPRINT.contains("generic")||Build.MODEL.contains("Emulator")
                ||("ranchu".equals(Build.HARDWARE)&&Build.MODEL.startsWith("Android SDK built for")),"Emulator only");
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        require((settings.account().isEmpty()&&!settings.configured())||ACCOUNT.equals(settings.account()),"Existing real account; fixture refused");
        require(!serviceRunning(context,false),"Existing classroom service; fixture refused");
        Field registry=field(ClassroomWorkService.class,"jobs");
        test.runOnMainSync(()->{try{require(((Map<?,?>)registry.get(null)).isEmpty(),"Existing classroom work; fixture refused");}catch(IllegalAccessException e){throw new AssertionError(e);}});
        Map<String,?> before=settings.prefs.getAll();
        CountDownLatch releaseA=new CountDownLatch(1),releaseB=new CountDownLatch(1),enteredA=new CountDownLatch(1),enteredB=new CountDownLatch(1);
        ClassroomLessonActivity[] screens=new ClassroomLessonActivity[3];
        ClassroomLessonActivity.Session[] sessions=new ClassroomLessonActivity.Session[2];
        String unique=Long.toString(System.currentTimeMillis());
        ClassroomLesson lessonA=new ClassroomLesson("990036",unique,"后台验收 A","合成课堂","","6",1);
        ClassroomLesson lessonB=new ClassroomLesson("990037",unique,"后台验收 B","合成课堂","","6",1);
        try {
            require(settings.prefs.edit().putString("account",ACCOUNT).putString("credentials","synthetic-not-a-login")
                    .putBoolean("enabled",true).putBoolean("timetable",false).putLong("last_success",System.currentTimeMillis()).commit(),"fixture prefs");
            screens[0]=open(test,context,lessonA);sessions[0]=session(screens[0]);
            await(test,()->sessions[0].localInitialized&&!sessions[0].busy,"first local load");
            test.runOnMainSync(()->sessions[0].run("后台验收 A",true,()->hold(sessions[0],enteredA,releaseA)));
            require(enteredA.await(10,TimeUnit.SECONDS),"first worker entered");
            await(test,()->serviceRunning(context,true),"foreground service active");
            NotificationManager manager=context.getSystemService(NotificationManager.class);
            boolean postable=manager.areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED);
            if(postable){
                await(test,()->foreground(manager)!=null&&foreground(manager).extras.getInt(Notification.EXTRA_PROGRESS_MAX)==2,"single foreground receives measured progress");
                Notification only=foreground(manager);
                require("课堂总结处理中".contentEquals(only.extras.getCharSequence(Notification.EXTRA_TITLE))
                        &&only.visibility==Notification.VISIBILITY_PRIVATE&&only.contentIntent!=null,"detailed private foreground and lesson entry");
                require(tagCount(manager,lessonA)==0,"no duplicate single lesson running tag");
            }
            test.runOnMainSync(screens[0]::finish);
            await(test,()->screens[0].isDestroyed(),"detail finished");
            SystemClock.sleep(500);
            test.runOnMainSync(()->require(ClassroomWorkService.find(ACCOUNT,lessonA)==sessions[0]&&sessions[0].busy
                    &&sessions[0].control!=null&&!sessions[0].control.isCancelled(),"return keeps session and request alive"));
            screens[1]=open(test,context,lessonA);
            require(session(screens[1])==sessions[0],"reopen attaches to original session");
            screens[2]=open(test,context,lessonB);sessions[1]=session(screens[2]);
            await(test,()->sessions[1].localInitialized&&!sessions[1].busy,"second local load");
            test.runOnMainSync(()->sessions[1].run("后台验收 B",true,()->hold(sessions[1],enteredB,releaseB)));
            require(enteredB.await(10,TimeUnit.SECONDS),"second worker entered");
            if(postable){
                await(test,()->foreground(manager)!=null&&foreground(manager).extras.getCharSequence(Notification.EXTRA_BIG_TEXT,"").toString().contains(lessonB.course),"combined foreground contains second lesson");
                String detail=foreground(manager).extras.getCharSequence(Notification.EXTRA_BIG_TEXT,"").toString();
                require(detail.contains(lessonA.course)&&detail.contains(lessonB.course),"combined foreground retains both active lessons");
                require(tagCount(manager,lessonA)==0&&tagCount(manager,lessonB)==0,"no duplicate running tags with multiple lessons");
            }
            releaseA.countDown();
            await(test,()->!sessions[0].busy&&ClassroomWorkService.find(ACCOUNT,lessonA)==null,"first completion released registry");
            test.runOnMainSync(()->require(sessions[1].busy&&!sessions[1].control.isCancelled()
                    &&ClassroomWorkService.find(ACCOUNT,lessonB)==sessions[1]&&serviceRunning(context,true),"second lesson keeps foreground service"));
            if(postable){
                await(test,()->foreground(manager)!=null&&foreground(manager).extras.getInt(Notification.EXTRA_PROGRESS_MAX)==2,"remaining lesson regains detailed foreground");
                Notification detail=(Notification)field(ClassroomNotifications.class,"latest").get(sessions[1].notifications);
                require(foreground(manager).contentIntent.equals(detail.contentIntent),"remaining foreground opens remaining lesson");
                require(tagCount(manager,lessonB)==0,"remaining progress has no tagged duplicate");
            }
            releaseB.countDown();
            await(test,()->!sessions[1].busy&&ClassroomWorkService.find(ACCOUNT,lessonB)==null&&!serviceRunning(context,false),"last completion stops service");
            Notification result=(Notification)field(ClassroomNotifications.class,"latest").get(sessions[0].notifications);
            require(result!=null&&result.contentIntent!=null&&(result.flags&Notification.FLAG_ONGOING_EVENT)==0
                    &&("课堂处理完成 · "+lessonA.course).contentEquals(result.extras.getCharSequence(Notification.EXTRA_TITLE)),"completion notification course and detail link");
            if(postable)require(java.util.Arrays.stream(manager.getActiveNotifications()).anyMatch(n->("classroom:"+ACCOUNT+":"+lessonA.courseId+":"+lessonA.id).equals(n.getTag())),"result present in system notifications");
            return new JSONObject().put("synthetic",true).put("returnContinues",true).put("reopenSameSession",true)
                    .put("multipleLessons",true).put("foregroundLifecycle",true).put("completionNotification",true)
                    .put("oneRunningNotification",postable).put("combinedProgress",postable).put("remainingLessonDetail",postable)
                    .put("systemPostingVerified",postable).put("permissionDenialTest","classroom_notifications separate phase").toString();
        } finally {
            releaseA.countDown();releaseB.countDown();
            test.runOnMainSync(()->{for(var s:sessions)if(s!=null)s.cancel();for(Activity a:screens)if(a!=null&&!a.isFinishing())a.finish();});
            try { await(test,()->{for(var s:sessions)if(s!=null&&s.busy)return false;return true;},"fixture workers stopped"); }
            finally {
                test.runOnMainSync(()->{for(var s:sessions)if(s!=null){if(s.notifications!=null)s.notifications.clear();s.detached=true;s.owner.clear();s.disposeIfDetached();}});
                restore(settings.prefs,before);
                test.waitForIdleSync();
                // Confirm OS removal after callbacks and preference restoration, using only this run's exact tags.
                NotificationManager manager=context.getSystemService(NotificationManager.class);
                manager.cancel(lessonTag(lessonA),0);manager.cancel(lessonTag(lessonB),0);
                await(test,()->tagCount(manager,lessonA)==0&&tagCount(manager,lessonB)==0,"owned result notifications removed");
                // loadLocal creates only these unique empty directories; never remove pre-existing fixtures.
                for(ClassroomLesson l:new ClassroomLesson[]{lessonA,lessonB}){
                    java.io.File dir=new java.io.File(context.getNoBackupFilesDir(),"classroom/"+ACCOUNT+"/"+l.courseId+"-"+l.id);
                    if(dir.isDirectory()&&dir.list()!=null&&dir.list().length==0)dir.delete();
                }
            }
        }
    }

    /** Explicit recovery for earlier runs of this fixture; never clears unrelated classroom notifications. */
    static String cleanupOwnedResults(Instrumentation test) throws Exception {
        Context context=test.getTargetContext();
        require("com.donglan.chrona.batch.validation".equals(context.getPackageName())
                &&"ranchu".equals(Build.HARDWARE),"Exact synthetic batch package and shared emulator required");
        require(ACCOUNT.equals(new ZjuSettingsStore(context).account()),"Exact synthetic account required");
        require(!serviceRunning(context,false),"Active classroom service; fixture cleanup refused");
        Field jobs=field(ClassroomWorkService.class,"jobs"),batches=field(ClassroomWorkService.class,"batches"),pending=field(ClassroomWorkService.class,"pendingStarts");
        test.runOnMainSync(()->{
            try {
                require(((Map<?,?>)jobs.get(null)).isEmpty()&&((Map<?,?>)batches.get(null)).isEmpty()
                        &&pending.getInt(null)==0,"Active or queued classroom work; fixture cleanup refused");
            } catch(IllegalAccessException failure){throw new AssertionError(failure);}
        });
        NotificationManager manager=context.getSystemService(NotificationManager.class);
        java.util.ArrayList<String> ownedTags=new java.util.ArrayList<>();
        for(var notice:manager.getActiveNotifications()){
            if(!ownedResultTag(notice.getTag()))continue;
            Notification notification=notice.getNotification();
            require(notice.getId()==0&&"chrona_classroom_results".equals(notification.getChannelId())
                    &&(notification.flags&Notification.FLAG_ONGOING_EVENT)==0,"Unexpected owned-prefix notification; cleanup refused");
            ownedTags.add(notice.getTag());
        }
        for(String tag:ownedTags)manager.cancel(tag,0);
        await(test,()->java.util.Arrays.stream(manager.getActiveNotifications()).noneMatch(n->ownedTags.contains(n.getTag())),"prior owned result notifications removed");
        return new JSONObject().put("synthetic",true).put("ownedResultNotificationsRemoved",ownedTags.size())
                .put("scope","account 00000000-0000-0000-0000-000000000036; courses 990036/990037; results only").toString();
    }

    private static boolean ownedResultTag(String tag){
        if(tag==null)return false;
        for(String course:new String[]{"990036","990037"}){
            String prefix="classroom:"+ACCOUNT+":"+course+":";
            if(tag.startsWith(prefix)&&tag.substring(prefix.length()).matches("[0-9]+"))return true;
        }
        return false;
    }

    private static String lessonTag(ClassroomLesson lesson){return "classroom:"+ACCOUNT+":"+lesson.courseId+":"+lesson.id;}

    private static Notification foreground(NotificationManager manager){
        for(var notice:manager.getActiveNotifications())if(notice.getTag()==null&&notice.getId()==2043)return notice.getNotification();
        return null;
    }
    private static long tagCount(NotificationManager manager,ClassroomLesson lesson){
        String tag=lessonTag(lesson);
        return java.util.Arrays.stream(manager.getActiveNotifications()).filter(n->tag.equals(n.getTag())).count();
    }
    private static void hold(ClassroomLessonActivity.Session session,CountDownLatch entered,CountDownLatch release)throws Exception{
        // Cross the real one-second posting throttle before the measured checkpoint.
        SystemClock.sleep(1100);
        session.checkpoint(new ClassroomProgress("合成后台任务",1,2,"等待测试释放"));entered.countDown();
        while(!release.await(100,TimeUnit.MILLISECONDS))session.control.check();
        session.control.check();session.message="合成课堂处理完成";
    }
    private static ClassroomLessonActivity open(Instrumentation test,Context context,ClassroomLesson lesson)throws Exception{
        return (ClassroomLessonActivity)test.startActivitySync(new Intent(context,ClassroomLessonActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK).putExtra(ClassroomLessonActivity.EXTRA_ACCOUNT,ACCOUNT)
                .putExtra(ClassroomLessonActivity.EXTRA_LESSON,lesson.json().toString()));
    }
    private static ClassroomLessonActivity.Session session(Activity activity)throws Exception{return (ClassroomLessonActivity.Session)field(ClassroomLessonActivity.class,"s").get(activity);}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    @SuppressWarnings("deprecation") private static boolean serviceRunning(Context context,boolean foreground){
        for(ActivityManager.RunningServiceInfo service:context.getSystemService(ActivityManager.class).getRunningServices(Integer.MAX_VALUE))
            if(ClassroomWorkService.class.getName().equals(service.service.getClassName()))return !foreground||service.foreground;
        return false;
    }
    private static void await(Instrumentation test,BooleanSupplier condition,String label)throws Exception{
        long deadline=SystemClock.elapsedRealtime()+10000;boolean[] done={false};
        do{test.runOnMainSync(()->done[0]=condition.getAsBoolean());if(done[0])return;SystemClock.sleep(50);}while(SystemClock.elapsedRealtime()<deadline);
        throw new AssertionError("Timed out: "+label);
    }
    @SuppressWarnings("unchecked") private static void restore(SharedPreferences prefs,Map<String,?> values){
        SharedPreferences.Editor edit=prefs.edit().clear();
        for(var entry:values.entrySet()){
            String key=entry.getKey();Object value=entry.getValue();
            if(value instanceof String)edit.putString(key,(String)value);else if(value instanceof Boolean)edit.putBoolean(key,(Boolean)value);
            else if(value instanceof Integer)edit.putInt(key,(Integer)value);else if(value instanceof Long)edit.putLong(key,(Long)value);
            else if(value instanceof Float)edit.putFloat(key,(Float)value);else if(value instanceof java.util.Set)edit.putStringSet(key,(java.util.Set<String>)value);
        }
        require(edit.commit(),"restore synthetic preferences");
    }
    private static void require(boolean condition,String label){if(!condition)throw new AssertionError(label);}
}
