package com.donglan.chrona;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import com.donglan.chrona.ai.*;
import com.donglan.chrona.classroom.*;
import java.io.File;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONObject;

/** Explicit single-lesson paid trial through the production Session, without saving AI settings. */
public final class ClassroomModelTrialInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle args){arguments=args;super.onCreate(args);start();}
    @Override public void onStart(){
        Bundle result=new Bundle();
        try{
            if(!"com.donglan.chrona".equals(getTargetContext().getPackageName())||!"ranchu".equals(android.os.Build.HARDWARE))throw new SecurityException();
            if(arguments==null||!java.util.Set.of("gpt-5.6-luna","gpt-6-luna","configured").contains(arguments.getString("model","")))throw new IllegalArgumentException("Explicit approved trial model or configured service required");
            ZjuSettingsStore school=new ZjuSettingsStore(getTargetContext());
            if(!school.enabled()||!school.configured())throw new IllegalStateException("School account unavailable");
            AiSettings original=new AiSettingsStore(getTargetContext()).load();
            if(original==null)throw new IllegalStateException("AI service unavailable");
            boolean configured="configured".equals(arguments.getString("model"));
            String effort=arguments.getString("reasoningEffort","");
            if(!effort.isEmpty()&&!java.util.Set.of("low","medium","high").contains(effort))throw new IllegalArgumentException("Explicit trial reasoning effort required");
            AiSettings trial=configured&&effort.isEmpty()?original:new AiSettings(original.baseUrl,configured?original.model:arguments.getString("model"),original.apiKey,
                    effort.isEmpty()?original.reasoningEffort:effort,original.protocol,original.sessionHeader);
            String lessonCase=arguments.getString("lesson","calculus");
            boolean programming="programming".equals(lessonCase);
            ClassroomLesson identity=switch(lessonCase){
                case "calculus" -> new ClassroomLesson("86985","1988487","","","","6",0);
                case "programming" -> new ClassroomLesson("87476","1972361","C程序设计基础","","","6",0);
                default -> throw new IllegalArgumentException("Explicit reviewed lesson case required");
            };
            ClassroomStore store=new ClassroomStore(getTargetContext(),school.account());
            File dir=programming?store.localLesson(identity):store.lesson(identity);
            JSONObject saved=ClassroomStore.read(new File(dir,"summary.json"));
            boolean needsMaterials=saved==null||!new File(dir,"materials.json").isFile()||!new File(dir,"candidates.json").isFile();
            if(needsMaterials&&!programming)throw new IllegalStateException("Existing complete materials required");
            ClassroomLesson lesson=saved==null?identity:ClassroomLesson.fromJson(saved.getJSONObject("lesson"));
            if(!lesson.courseId.equals(identity.courseId)||!lesson.id.equals(identity.id))throw new IllegalStateException("Lesson identity mismatch");
            SchedulePreferences prefs=SchedulePreferences.load(getTargetContext());
            CountDownLatch done=new CountDownLatch(1);
            ClassroomLessonActivity.Session job=new ClassroomLessonActivity.Session(getTargetContext(),school.account());
            job.selected=lesson;job.dir=dir;job.summary=saved;job.enhancedClassroom=prefs.enhancedClassroom;
            job.noteStrength=prefs.classroomNoteStrength;job.imageLimit=40;job.summaryAuthorized=true;job.detached=true;
            job.completed=done::countDown;
            startActivitySync(new Intent(getTargetContext(),DashboardActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            boolean newRun="true".equals(arguments.getString("newRun"));
            runOnMainSync(()->job.run("单课模型回归",true,()->{
                if(needsMaterials){
                    ClassroomClient client=job.connect();
                    job.selected=client.courseLessons(identity).stream()
                            .filter(row->identity.id.equals(row.id)&&row.selectable(System.currentTimeMillis()))
                            .findFirst().orElseThrow(()->new IllegalStateException("Reviewed lesson unavailable in school catalogue"));
                    job.prepared=ClassroomSummary.prepare(job.dir,job.selected,client,job.control,job::checkpoint);
                }
                if(newRun){ClassroomCorrectionCache.capture(job.dir);ClassroomStore.beginSummaryRun(job.dir);}
                ClassroomAutoSummary.summarize(job,trial,prefs.classroomPrompt,true);
            }));
            while(!done.await(30,TimeUnit.SECONDS)){
                Bundle progress=new Bundle();progress.putString("trial_state","running");sendStatus(1,progress);
            }
            result.putBoolean("succeeded",job.succeeded);result.putString("model",trial.model);
            result.putBoolean("used_saved_configuration",configured&&effort.isEmpty());
            if(!effort.isEmpty())result.putString("temporary_reasoning_effort",effort);
            AiSettings current=new AiSettingsStore(getTargetContext()).load();
            result.putBoolean("global_model_unchanged",original.model.equals(current.model));
            result.putBoolean("global_reasoning_unchanged",original.reasoningEffort.equals(current.reasoningEffort));
            finish(job.succeeded?-1:0,result);
        }catch(Throwable failure){result.putString("error_type",failure.getClass().getSimpleName());finish(0,result);}
    }
}
