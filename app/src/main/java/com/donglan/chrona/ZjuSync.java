package com.donglan.chrona;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.donglan.chrona.zju.ZjuClient;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** One owner for school network work. No credentials or response bodies enter logs. */
final class ZjuSync {
    static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final List<Consumer<String>> listeners=new ArrayList<>();
    private static volatile ZjuClient client;
    private static boolean running;
    private static long epoch;
    static synchronized boolean request(Context context,Consumer<String> callback){
        if(callback!=null)listeners.add(callback);
        if(running)return true;
        Context app=context.getApplicationContext();ZjuSettingsStore settings=new ZjuSettingsStore(app);
        if(!settings.enabled()||!settings.configured()){deliver("请先打开折姜星辰并保存账号");return false;}
        running=true;
        long token;
        synchronized(AndroidSync.LOCK){token=epoch;}
        WORKER.execute(()->run(app,token));return true;
    }
    private static void run(Context context,long token){
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        ZjuHomeworkStore store=new ZjuHomeworkStore(context);
        String account=settings.account(), result;
        try{
            ZjuHomeworkStore.State before;
            synchronized(AndroidSync.LOCK){
                if(token!=epoch)throw new java.io.IOException("本次同步已停止");
                before=store.state();
            }
            settings.status("正在同步");
            String[] credentials=settings.credentials();
            ZjuClient active=new ZjuClient(credentials[0],credentials[1],com.donglan.chrona.zju.ZjuCalendarStore.load(context));
            java.util.Arrays.fill(credentials,"");
            synchronized(ZjuSync.class){client=active;}
            ZjuSnapshot snapshot;
            try(active){snapshot=active.fetch(before.enabled,settings.timetable());}
            synchronized(AndroidSync.LOCK){
                if(token!=epoch||!settings.enabled()||!account.equals(settings.account()))throw new java.io.IOException("设置已更改，本次同步已停止");
                ZjuHomeworkStore.Result applied=store.apply(account,before.generation,snapshot.homework);
                if(settings.timetable())new ZjuTimetableStore(context).save(account,snapshot.lessons,snapshot.completeTimetableTerms);
                result="新增 "+applied.added+" · 更新 "+applied.updated+" · 完成 "+applied.completed;
                int pendingCalendar=ZjuHomeworkCalendar.pendingCount(context);
                if(pendingCalendar>0)result+="\n"+pendingCalendar+" 项作业待写入或更新日历";
                if(!snapshot.warnings.isEmpty())result+="\n"+String.join("\n",snapshot.warnings);
                settings.prefs.edit().putLong("last_success",System.currentTimeMillis()).apply();
                if(applied.actionableAdded>0||applied.actionableUpdated>0)notifyHomework(context,applied);
                AgendaWidgetProvider.requestRefresh(context);AgendaReminder.requestRefresh(context);
            }
        }catch(Exception error){
            // Only client-authored IOException messages are suitable for the UI.
            result=error instanceof java.io.IOException&&error.getMessage()!=null?error.getMessage():"同步未完成，请检查网络或重新填写账号";
        }
        synchronized(ZjuSync.class){client=null;settings.status(result);deliver(result);running=false;}
    }
    private static synchronized void deliver(String status){
        List<Consumer<String>> pending=new ArrayList<>(listeners);listeners.clear();
        MAIN.post(()->{for(Consumer<String> listener:pending)listener.accept(status);});
    }
    static void cancel(){
        ZjuClient active;
        synchronized(AndroidSync.LOCK){epoch++;active=client;}
        if(active!=null)active.cancel();
    }
    private static void notifyHomework(Context context,ZjuHomeworkStore.Result result){
        if(android.os.Build.VERSION.SDK_INT>=33&&context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
        android.app.NotificationManager manager=context.getSystemService(android.app.NotificationManager.class);if(manager==null)return;
        String channel="zju_homework_updates";
        manager.createNotificationChannel(new android.app.NotificationChannel(channel,"作业更新",android.app.NotificationManager.IMPORTANCE_DEFAULT));
        long capture=new ZjuHomeworkStore(context).state().captureId;if(capture<=0)return;
        android.content.Intent intent=new android.content.Intent(context,TaskDetailActivity.class).putExtra("task_id",capture);
        android.app.PendingIntent open=android.app.PendingIntent.getActivity(context,73112,intent,android.app.PendingIntent.FLAG_UPDATE_CURRENT|android.app.PendingIntent.FLAG_IMMUTABLE);
        try{manager.notify(73112,new android.app.Notification.Builder(context,channel).setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle("作业已更新").setContentText("新增 "+result.actionableAdded+" · 更新 "+result.actionableUpdated).setContentIntent(open).setAutoCancel(true).build());}
        catch(SecurityException ignored){}
    }
    static void foreground(Context context){
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        if(settings.enabled()&&settings.configured()&&System.currentTimeMillis()-settings.lastSuccess()>30*60_000L)request(context,null);
    }
    private ZjuSync(){}
}
