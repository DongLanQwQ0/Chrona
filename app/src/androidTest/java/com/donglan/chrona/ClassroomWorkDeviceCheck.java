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
            releaseA.countDown();
            await(test,()->!sessions[0].busy&&ClassroomWorkService.find(ACCOUNT,lessonA)==null,"first completion released registry");
            test.runOnMainSync(()->require(sessions[1].busy&&!sessions[1].control.isCancelled()
                    &&ClassroomWorkService.find(ACCOUNT,lessonB)==sessions[1]&&serviceRunning(context,true),"second lesson keeps foreground service"));
            releaseB.countDown();
            await(test,()->!sessions[1].busy&&ClassroomWorkService.find(ACCOUNT,lessonB)==null&&!serviceRunning(context,false),"last completion stops service");
            Notification result=(Notification)field(ClassroomNotifications.class,"latest").get(sessions[0].notifications);
            require(result!=null&&result.contentIntent!=null&&(result.flags&Notification.FLAG_ONGOING_EVENT)==0
                    &&"课堂处理完成".contentEquals(result.extras.getCharSequence(Notification.EXTRA_TITLE)),"completion notification and detail link");
            NotificationManager manager=context.getSystemService(NotificationManager.class);
            boolean postable=manager.areNotificationsEnabled()&&(Build.VERSION.SDK_INT<33||context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)==android.content.pm.PackageManager.PERMISSION_GRANTED);
            if(postable)require(java.util.Arrays.stream(manager.getActiveNotifications()).anyMatch(n->("classroom:"+ACCOUNT+":"+lessonA.courseId+":"+lessonA.id).equals(n.getTag())),"result present in system notifications");
            return new JSONObject().put("synthetic",true).put("returnContinues",true).put("reopenSameSession",true)
                    .put("multipleLessons",true).put("foregroundLifecycle",true).put("completionNotification",true)
                    .put("systemPostingVerified",postable).put("permissionDenialTest","classroom_notifications separate phase").toString();
        } finally {
            releaseA.countDown();releaseB.countDown();
            test.runOnMainSync(()->{for(var s:sessions)if(s!=null)s.cancel();for(Activity a:screens)if(a!=null&&!a.isFinishing())a.finish();});
            try { await(test,()->{for(var s:sessions)if(s!=null&&s.busy)return false;return true;},"fixture workers stopped"); }
            finally {
                test.runOnMainSync(()->{for(var s:sessions)if(s!=null){if(s.notifications!=null)s.notifications.clear();s.detached=true;s.owner.clear();s.disposeIfDetached();}});
                restore(settings.prefs,before);
                // loadLocal creates only these unique empty directories; never remove pre-existing fixtures.
                for(ClassroomLesson l:new ClassroomLesson[]{lessonA,lessonB}){
                    java.io.File dir=new java.io.File(context.getNoBackupFilesDir(),"classroom/"+ACCOUNT+"/"+l.courseId+"-"+l.id);
                    if(dir.isDirectory()&&dir.list()!=null&&dir.list().length==0)dir.delete();
                }
            }
        }
    }
    private static void hold(ClassroomLessonActivity.Session session,CountDownLatch entered,CountDownLatch release)throws Exception{
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
