package com.donglan.chrona;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.donglan.chrona.ai.*;
import com.donglan.chrona.classroom.*;
import java.io.File;
import java.io.IOException;
import org.json.JSONObject;

/** Once-only dispatch of fully downloaded monitor lessons through the existing Session registry. */
final class ClassroomAutoSummary {
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    interface Generator { JSONObject generate(ClassroomLessonActivity.Session job,AiSettings ai,String prompt)throws Exception; }
    interface Settings { AiSettings load(Context app)throws Exception; }
    static Generator generator=(job,ai,prompt)->ClassroomSummary.generate(job.dir,job.selected,ai,job.imageLimit,job.control,job::checkpoint,prompt);
    static Settings settings=app->new AiSettingsStore(app).load();
    static java.util.function.BooleanSupplier foreground=ClassroomMonitorService::running;
    static boolean hasForegroundOwner(){return foreground.getAsBoolean();}

    /** Called on service loss, timeout and account changes. Never restarts requests. */
    static void ownerStopped(){
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(ClassroomAutoSummary::ownerStopped);return;}
        ClassroomWorkService.cancelMonitored();
    }
    static void dispatch(Context context){
        Context app=context.getApplicationContext();
        if(Looper.myLooper()!=Looper.getMainLooper()){MAIN.post(()->dispatch(app));return;}
        ClassroomWorkService.reconcileMonitored(app);
        String account=new ZjuSettingsStore(app).account();
        try{
            for(String other:ClassroomMonitorStore.accounts(app))if(!ClassroomMonitor.allowed(app,other)){
                ClassroomMonitorStore inactive=new ClassroomMonitorStore(app,other);
                for(JSONObject task:ClassroomMonitorStore.autoTasks(inactive.read()))if("queued".equals(task.optString("state"))||"running".equals(task.optString("state"))){
                    ClassroomLesson lesson=ClassroomLesson.fromJson(task.getJSONObject("lesson"));
                    inactive.summaryState(lesson,task.optString("state"),"interrupted",lesson.course+" · 学校账号已停用或更换，可手动继续");
                }
            }
            if(!ClassroomMonitor.allowed(app,account))return;
            ClassroomMonitorStore store=new ClassroomMonitorStore(app,account);
            for(JSONObject task:ClassroomMonitorStore.autoTasks(store.read())){
                ClassroomLesson lesson=ClassroomLesson.fromJson(task.getJSONObject("lesson"));String state=task.optString("state");
                if(!"queued".equals(state)&&!"running".equals(state))continue;
                ClassroomLessonActivity.Session existing=ClassroomWorkService.find(account,lesson);
                if(ClassroomMonitorStore.ruleFor(store.read(),lesson)==null){
                    if(existing!=null&&existing.monitorOwned)existing.cancel();
                    store.summaryState(lesson,state,"interrupted",lesson.course+" · 自动总结已停止，可手动继续");continue;
                }
                if(existing!=null)continue;
                ClassroomStore cache=new ClassroomStore(app,account);File dir=cache.localLesson(lesson),effective=cache.lesson(lesson);
                if(ClassroomStore.active(dir)||ClassroomStore.active(effective))continue;
                JSONObject saved=ClassroomStore.read(new File(effective,"summary.json"));
                if(saved!=null&&saved.optBoolean("complete")){
                    store.summaryState(lesson,state,"complete",lesson.course+" · 总结已完成");continue;
                }
                if("running".equals(state)){
                    store.summaryState(lesson,"running","interrupted",lesson.course+" · 上次 AI 结果未知，请手动继续");continue;
                }
                if(!hasForegroundOwner())continue;
                ClassroomLessonActivity.Session job=new ClassroomLessonActivity.Session(app,account);
                job.selected=lesson;job.dir=dir;job.summary=saved;job.imageLimit=task.optInt("imageLimit",0);
                job.monitorOwned=true;job.detached=true;job.localInitialized=true;
                job.materials=ClassroomStore.read(new File(dir,"materials.json"));
                JSONObject candidates=ClassroomStore.read(new File(dir,"candidates.json"));
                if(job.materials!=null&&candidates!=null)job.prepared=new JSONObject().put("segments",job.materials.getJSONArray("segments").length())
                        .put("raw",job.materials.optInt("raw_images")).put("candidates",candidates.getJSONArray("images").length());
                job.completed=()->{job.monitorOwned=false;job.completed=null;ClassroomMonitor.schedule(app);dispatch(app);};
                job.run("自动整理课堂",true,()->execute(store,job));
            }
        }catch(Exception failure){android.util.Log.w("ChronaClassroomMonitor","auto summary: "+failure.getClass().getSimpleName());}
    }
    private static void execute(ClassroomMonitorStore store,ClassroomLessonActivity.Session job)throws Exception{
        boolean requested=false;
        try{
            if(ClassroomMonitorStore.ruleFor(store.read(),job.selected)==null)throw new IOException("监视已停止");
            JSONObject materials=ClassroomStore.read(new File(job.dir,"materials.json"));
            if(materials==null||materials.optBoolean("monitoring")||materials.getJSONArray("segments").length()==0
                    ||ClassroomStore.read(new File(job.dir,"candidates.json"))==null)throw new IOException("完整课堂素材尚未就绪");
            AiSettings ai=settings.load(job.app);if(ai==null)throw new IOException("请先在 AI 设置中配置服务，素材已保留");
            if(job.imageLimit>0&&new AiSettingsStore(job.app).isImageUnsupported(ai))throw new IOException("当前 AI 服务不支持图片，请手动选择文字模式继续");
            String prompt=SchedulePreferences.load(job.app).classroomPrompt;
            job.control.check();
            if(!store.summaryState(job.selected,"queued","running",job.selected.course+" · AI 正在整理"))throw new IOException("自动总结已停止");requested=true;
            job.control.check();if(!job.allowed())throw new IOException("学校账号已停用或更换");
            job.summary=generator.generate(job,ai,prompt);
            store.summaryState(job.selected,"running","complete",job.selected.course+" · 总结已完成");job.message="总结已完成";
        }catch(Exception failure){
            if(failure instanceof ChatCompletionClient.RequestException rejected&&job.imageLimit>0&&rejected.rejectsImage()){
                AiSettings ai=settings.load(job.app);if(ai!=null)new AiSettingsStore(job.app).markImageUnsupported(ai);
            }
            String detail=job.cancelRequested||!job.allowed()?"自动总结已停止，可手动继续":
                    requested?"自动总结未完成，请手动继续":"自动总结未启动："+(failure.getMessage()==null?"请重试":failure.getMessage());
            store.summaryState(job.selected,requested?"running":"queued",job.cancelRequested||!job.allowed()?"interrupted":"failed",job.selected.course+" · "+detail);
            throw failure;
        }
    }
}
