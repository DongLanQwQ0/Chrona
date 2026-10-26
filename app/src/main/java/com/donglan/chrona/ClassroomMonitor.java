package com.donglan.chrona;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import com.donglan.chrona.classroom.*;
import com.donglan.chrona.net.RequestControl;
import com.donglan.chrona.net.NetworkFailure;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.Executors;

/** One network owner shared by active foreground observation and persisted recovery jobs. */
final class ClassroomMonitor {
    static final int JOB = 73114;
    static final String ALARM = "com.donglan.chrona.CLASSROOM_MONITOR";
    private static final java.util.concurrent.ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static boolean busy;
    private static volatile ClassroomClient client;
    private static volatile RequestControl control;
    static java.util.function.BiFunction<String,String,ClassroomClient> clients=ClassroomClient::new;
    private static android.content.SharedPreferences observed;
    private static android.content.SharedPreferences.OnSharedPreferenceChangeListener changes;
    interface Finished { void accept(boolean active); }

    static void initialize(Context context) {
        Context app=context.getApplicationContext();
        if(observed==null){observed=new ZjuSettingsStore(app).prefs;changes=(p,key)->{
            if("account".equals(key)||"enabled".equals(key)||"credentials".equals(key)){
                ClassroomMonitorService.ownerStopping();cancel();ClassroomAutoSummary.dispatch(app);app.stopService(new Intent(app,ClassroomMonitorService.class));schedule(app);
            }
        };observed.registerOnSharedPreferenceChangeListener(changes);}
        schedule(app);ClassroomAutoSummary.dispatch(app);
    }
    static boolean allowed(Context app,String account){ZjuSettingsStore s=new ZjuSettingsStore(app);return s.enabled()&&s.configured()&&account.equals(s.account());}
    /** A visible Activity can legally restore a registered monitor after process/update interruption. */
    static void foreground(Context app){
        if(ClassroomMonitorService.running())return;
        try{String account=new ZjuSettingsStore(app).account();if(!allowed(app,account))return;
            JSONObject state=new ClassroomMonitorStore(app,account).read();
            if(ClassroomMonitorStore.autoWork(state)){promote(app);return;}
            for(ClassroomLesson lesson:ClassroomMonitorStore.pending(state))if(lesson.startsAt<=System.currentTimeMillis()){promote(app);return;}
        }catch(Exception failure){android.util.Log.w("ChronaClassroomMonitor","resume: "+failure.getClass().getSimpleName());}
    }
    static void start(Context app,ClassroomLesson lesson,boolean repeat)throws Exception {
        ZjuSettingsStore settings=new ZjuSettingsStore(app);
        if(!allowed(app,settings.account()))throw new java.io.IOException("学校账号已停用或更换");
        // Validate persistent identity before registering a task.
        if(!lesson.courseId.matches("[0-9]+")||!lesson.id.matches("[0-9]+")||lesson.startsAt<=0)throw new java.io.IOException("课堂信息无效");
        new ClassroomMonitorStore(app,settings.account()).add(lesson,repeat);
        schedule(app);promote(app);
    }
    static void promote(Context app) {
        try{app.startForegroundService(new Intent(app,ClassroomMonitorService.class));}
        catch(RuntimeException denied){
            try{new ClassroomMonitorStore(app,new ZjuSettingsStore(app).account()).message("后台启动受限，已保留监视任务");}catch(Exception ignored){}
            schedule(app);
        }
    }
    static void cancel(){RequestControl c=control;if(c!=null)c.cancel();ClassroomClient network=client;if(network!=null)network.cancel();}
    static void changed(Context app){cancel();ClassroomAutoSummary.dispatch(app);schedule(app);try{if(new ClassroomMonitorStore(app,new ZjuSettingsStore(app).account()).hasRules())promote(app);else app.stopService(new Intent(app,ClassroomMonitorService.class));}catch(Exception ignored){}}
    static void schedule(Context context) {
        Context app=context.getApplicationContext();
        JobScheduler jobs=app.getSystemService(JobScheduler.class);AlarmManager alarms=app.getSystemService(AlarmManager.class);
        PendingIntent alarm=PendingIntent.getBroadcast(app,JOB,new Intent(app,ClassroomMonitorReceiver.class).setAction(ALARM),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        try {
            ZjuSettingsStore settings=new ZjuSettingsStore(app);
            long now=System.currentTimeMillis(),next=allowed(app,settings.account())?ClassroomMonitorStore.next(new ClassroomMonitorStore(app,settings.account()).read(),now):-1;
            if(jobs!=null)jobs.cancel(JOB);if(alarms!=null)alarms.cancel(alarm);
            if(next<0)return;
            if(jobs!=null)jobs.schedule(new JobInfo.Builder(JOB,new ComponentName(app,ClassroomMonitorJobService.class))
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(Math.max(1000,next-now)).setPersisted(true).build());
            // Exact alarms are for the user's requested lesson start, not a 30-second keep-alive loop.
            long start=Long.MAX_VALUE;
            for(ClassroomLesson lesson:ClassroomMonitorStore.pending(new ClassroomMonitorStore(app,settings.account()).read()))if(lesson.startsAt>now)start=Math.min(start,lesson.startsAt);
            if(alarms!=null&&start!=Long.MAX_VALUE){
                if(Build.VERSION.SDK_INT<31||alarms.canScheduleExactAlarms())alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,start,alarm);
                else alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,start,alarm);
            }
        }catch(Exception failure){android.util.Log.w("ChronaClassroomMonitor","schedule: "+failure.getClass().getSimpleName());}
    }
    static synchronized boolean run(Context context,Finished finished) {
        if(busy)return false;busy=true;Context app=context.getApplicationContext();
        worker.execute(()->{
            String account=new ZjuSettingsStore(app).account();ClassroomMonitorStore store=new ClassroomMonitorStore(app,account);
            boolean active=false;
            try {
                if(!allowed(app,account)||!store.hasRules())return;
                control=new RequestControl(120_000L);
                String[] pair=new ZjuSettingsStore(app).credentials();try{client=clients.apply(pair[0],pair[1]);}finally{Arrays.fill(pair,"");}
                JSONObject state=store.read();long now=System.currentTimeMillis();
                if(now-state.optLong("checked")>=ClassroomMonitorStore.CATALOG_MILLIS){
                    store.attempted(now);
                    LinkedHashMap<String,ClassroomLesson> courses=new LinkedHashMap<>();JSONArray rules=state.getJSONArray("rules");
                    for(int i=0;i<rules.length();i++){ClassroomLesson l=ClassroomLesson.fromJson(rules.getJSONObject(i).getJSONObject("lesson"));courses.put(l.courseId,l);}
                    List<ClassroomLesson> catalog=new ArrayList<>();
                    for(ClassroomLesson selected:courses.values()){
                        control.check();
                        try{catalog.addAll(client.courseLessons(selected));}
                        catch(Exception unavailable){check(app,account);JSONArray old=state.getJSONArray("catalog");for(int i=0;i<old.length();i++){ClassroomLesson kept=ClassroomLesson.fromJson(old.getJSONObject(i));if(kept.courseId.equals(selected.courseId))catalog.add(kept);}
                            store.message(selected,selected.course+" · 目录更新失败，稍后重试");}
                    }
                    check(app,account);store.catalog(catalog,now);
                }
                for(ClassroomLesson lesson:ClassroomMonitorStore.pending(store.read())) {
                    check(app,account);if(lesson.startsAt>System.currentTimeMillis())continue;
                    File dir=new ClassroomStore(app,account).localLesson(lesson);
                    if(ClassroomStore.active(dir)){store.message(lesson,lesson.course+" · 等待当前处理完成");continue;}
                    ClassroomStore.begin(dir);
                    try {
                        JSONObject observation=client.monitorMaterials(lesson,stage->{try{
                            ClassroomStore.write(new File(dir,"monitor-stage.json"),new JSONObject().put("status",stage).put("at",System.currentTimeMillis()));
                        }catch(Exception failure){throw new IllegalStateException(failure);}});
                        check(app,account);if(!ClassroomMonitorStore.matches(store.read(),lesson))continue;
                        JSONObject previous=ClassroomStore.read(new File(dir,"monitor-materials.json"));
                        JSONObject ordinary=ClassroomStore.read(new File(dir,"materials.json"));
                        if(previous==null||ordinary!=null&&ordinary.optLong("updated")>previous.optLong("updated"))previous=ordinary;
                        // Portable notes contain no reusable raw-image cache or private source URLs.
                        if(previous!=null&&previous.optBoolean("portable"))previous=null;
                        JSONArray received=ClassroomTranscript.snapshot(ClassroomLiveTranscripts.read(dir));
                        boolean authoritative="6".equals(observation.optString("status"))&&!observation.optBoolean("pending");
                        if(authoritative){
                            ClassroomSummary.prepareComplete(dir,lesson,observation,previous,client,control,p->{});
                            check(app,account);if(ClassroomMonitorStore.matches(store.read(),lesson))store.finished(lesson);
                            continue;
                        }
                        if(previous!=null){JSONArray keep=new JSONArray(),old=previous.getJSONArray("segments");
                            for(int i=0;i<old.length();i++)if(!"live".equals(old.getJSONObject(i).optString("origin")))keep.put(old.getJSONObject(i));
                            previous.put("segments",keep);}
                        if(observation.getJSONArray("segments").length()==0&&received.length()>0)observation.put("segments",received);
                        JSONObject merged=ClassroomMaterialUpdates.merge(previous,observation).put("updated",System.currentTimeMillis());
                        ClassroomStore.write(new File(dir,"monitor-materials.json"),merged);
                        JSONArray images=ClassroomImages.prepare(dir,merged,client,control,p->{});
                        check(app,account);if(!ClassroomMonitorStore.matches(store.read(),lesson))continue;
                        JSONObject candidates=new JSONObject().put("images",images).put("lesson",lesson.json()).put("updated",System.currentTimeMillis());
                        ClassroomStore.write(new File(dir,"monitor-candidates.json"),candidates);
                        String detail=merged.getJSONArray("segments").length()+" 条字幕 · "+merged.getJSONArray("images").length()+" 张PPT";
                        int subtitleError=observation.optInt("subtitle_error"),slideError=observation.optInt("slide_error");
                        if(subtitleError!=0&&received.length()==0)detail+=subtitleError==10002?" · 等待字幕":" · 字幕请求失败（"+subtitleError+"）";
                        if(slideError!=0)detail+=" · PPT请求失败（"+slideError+"）";
                        store.message(lesson,lesson.course+" · "+detail);
                    }catch(Exception failure){
                        check(app,account);
                        store.message(lesson,lesson.course+" · "+(NetworkFailure.hasDetails(failure)?NetworkFailure.describe(failure):safeFailure(failure)));
                    }finally{ClassroomStore.end(dir);}
                }
            }catch(Exception failure){
                if(allowed(app,account))try{store.message(NetworkFailure.hasDetails(failure)?NetworkFailure.describe(failure):safeFailure(failure));}catch(Exception ignored){}
            }finally{
                ClassroomClient c=client;if(c!=null)c.close();client=null;RequestControl request=control;if(request!=null)request.close();control=null;
                try{if(allowed(app,account))for(ClassroomLesson lesson:ClassroomMonitorStore.pending(store.read()))if(lesson.startsAt<=System.currentTimeMillis())active=true;}catch(Exception ignored){}
                boolean keep=active;main.post(()->{synchronized(ClassroomMonitor.class){busy=false;}ClassroomAutoSummary.dispatch(app);
                    boolean automatic=false;try{automatic=allowed(app,account)&&ClassroomMonitorStore.autoWork(store.read());}catch(Exception ignored){}
                    finished.accept(keep||automatic);});
            }
        });return true;
    }
    private static String safeFailure(Exception failure){return failure instanceof java.io.InterruptedIOException?"本次获取已中断，稍后重试":"获取失败（"+failure.getClass().getSimpleName()+"），稍后重试";}
    private static void check(Context app,String account)throws Exception {control.check();if(!allowed(app,account))throw new java.io.InterruptedIOException("学校账号已停用或更换");}
}
