package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomStore;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

/** Main-thread sequential download-and-summary owner. Never takes over another job. */
final class ClassroomBatchWork {
    private static final LinkedHashMap<String,ClassroomBatchWork> latest=new LinkedHashMap<>();
    static String key(ClassroomLesson lesson){return lesson.courseId+"/"+lesson.id;}
    static ClassroomBatchWork find(String account){return latest.get(account);}
    static ClassroomBatchWork start(Context app,String account,List<ClassroomLesson> selection,java.util.Set<String> authorizedRetries)throws Exception{
        ClassroomBatchWork previous=latest.get(account);
        if(previous!=null&&previous.running)return previous;
        ClassroomBatchWork batch=new ClassroomBatchWork(app,account,selection,authorizedRetries);latest.put(account,batch);
        batch.preferences.registerOnSharedPreferenceChangeListener(batch.changes);
        ClassroomWorkService.holdBatch(batch,app,batch::next,()->batch.cancel("后台处理已停止"));
        return batch;
    }
    final Context app;final String account;final List<ClassroomLesson> lessons;
    final int imageLimit;final boolean enhancedClassroom;
    final java.util.Set<String> authorizedRetries;
    final LinkedHashMap<String,String> failures=new LinkedHashMap<>();
    final android.content.SharedPreferences preferences;
    final android.content.SharedPreferences.OnSharedPreferenceChangeListener changes;
    boolean running=true,cancelled;int position,success,skipped,cancelledCount;
    ClassroomLessonActivity.Session current;
    String stopReason="已取消";
    private ClassroomBatchWork(Context app,String account,List<ClassroomLesson> selection,java.util.Set<String> authorizedRetries)throws Exception{
        this.app=app;this.account=account;
        this.authorizedRetries=java.util.Set.copyOf(authorizedRetries);
        imageLimit=ClassroomAutoSummary.imageLimit(app,account);
        enhancedClassroom=com.donglan.chrona.ai.SchedulePreferences.load(app).enhancedClassroom;
        LinkedHashMap<String,ClassroomLesson> unique=new LinkedHashMap<>();for(ClassroomLesson lesson:selection)unique.put(key(lesson),lesson);
        lessons=new ArrayList<>(unique.values());preferences=new ZjuSettingsStore(app).prefs;
        changes=(p,k)->{if(!allowed())cancel("学校账号已停用或更换");};
    }
    boolean allowed(){ZjuSettingsStore store=new ZjuSettingsStore(app);return store.enabled()&&store.configured()&&account.equals(store.account());}
    void cancel(String reason){
        if(!running)return;cancelled=true;stopReason=reason;
        if(current!=null)current.cancel();else finishRemaining();
    }
    private void finishRemaining(){
        while(position<lessons.size()){ClassroomLesson lesson=lessons.get(position++);cancelledCount++;failures.put(key(lesson),stopReason);}
        finish();
    }
    private void next(){
        if(!running)return;
        if(cancelled||!allowed()){cancelled=true;if(!allowed())stopReason="学校账号已停用或更换";finishRemaining();return;}
        while(position<lessons.size()){
            ClassroomLesson lesson=lessons.get(position++);
            if(ClassroomWorkService.find(account,lesson)!=null||ClassroomLiveTranscripts.active(account,lesson)){
                skipped++;failures.put(key(lesson),"本节课堂正在处理，已跳过");continue;
            }
            try{org.json.JSONObject saved=ClassroomStore.read(new File(new ClassroomStore(app,account).lesson(lesson),"summary.json"));
                if(saved!=null&&saved.optBoolean("complete")){skipped++;failures.put(key(lesson),"已有完整总结，已跳过");continue;}
            }catch(Exception failure){failures.put(key(lesson),failure.getMessage()==null?"无法读取课堂资料":failure.getMessage());continue;}
            ClassroomLessonActivity.Session job=new ClassroomLessonActivity.Session(app,account);current=job;
            job.selected=lesson;job.batchOwner=this;job.detached=true;
            job.imageLimit=imageLimit;
            job.enhancedClassroom=enhancedClassroom;
            job.summaryAuthorized=authorizedRetries.contains(key(lesson));
            job.completed=()->{
                job.completed=null;current=null;
                if(job.succeeded&&!job.cancelRequested&&allowed())success++;
                else if(cancelled||job.cancelRequested||!allowed()){cancelledCount++;failures.put(key(lesson),allowed()?stopReason:"学校账号已停用或更换");}
                else failures.put(key(lesson),job.message.isEmpty()?"课堂总结失败":job.message);
                next();
            };
            // Resolve before run(), so its existing lease protects this exact account directory.
            try{job.dir=job.store().localLesson(lesson);}
            catch(Exception failure){current=null;job.completed=null;failures.put(key(lesson),failure.getMessage()==null?"无法创建课堂缓存":failure.getMessage());job.disposeIfDetached();continue;}
            job.localInitialized=true;
            job.run("批量总结课堂",true,()->ClassroomAutoSummary.downloadAndSummarize(job));return;
        }
        finish();
    }
    private void finish(){if(!running)return;running=false;preferences.unregisterOnSharedPreferenceChangeListener(changes);ClassroomWorkService.releaseBatch(this);}
    String text(){
        int done=success+failures.size();
        if(running)return "总结 "+done+"/"+lessons.size()+(current==null?"":" · "+current.selected.course+" · "+(current.progress==null?"准备中":current.progress.stage));
        int failed=failures.size()-skipped-cancelledCount;
        return "总结完成 · 成功 "+success+" · 失败 "+failed+" · 跳过 "+skipped+" · 取消 "+cancelledCount;
    }
    String details(){StringBuilder text=new StringBuilder(text());for(ClassroomLesson lesson:lessons){String reason=failures.get(key(lesson));if(reason!=null)text.append("\n").append(lesson.course).append(" · ").append(lesson.date()).append(" · ").append(lesson.title).append(": ").append(reason);}return text.toString();}
}
