package com.donglan.chrona;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.SystemClock;
import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.classroom.*;
import com.donglan.chrona.zju.ZjuClient;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import okhttp3.*;
import org.json.*;

/** Production queue/Session dispatch, with a controlled generator: never a paid-model check. */
final class ClassroomAutoSummaryChecks {
    private final Instrumentation test;
    private final Context app;
    private final String account;
    private final ClassroomMonitorStore store;
    private final AtomicInteger calls=new AtomicInteger();
    private int assertions,sequence=0;
    private final long identityBase=System.currentTimeMillis();
    private volatile int phase,lastLimit=-1;
    private volatile boolean owner;
    private final AiSettings ai=new AiSettings("https://fixture.invalid/v1","fixture","fixture","low");
    private ClassroomAutoSummaryChecks(Instrumentation test){this.test=test;app=test.getTargetContext();account=new ZjuSettingsStore(app).account();store=new ClassroomMonitorStore(app,account);}
    static int run(Instrumentation test,Bundle args)throws Exception{
        Context app=test.getTargetContext();
        if(!"com.donglan.chrona.monitor.validation".equals(app.getPackageName()))throw new SecurityException("Fixture package required");
        if(!android.os.Build.HARDWARE.contains("ranchu"))throw new SecurityException("Owned emulator required");
        if(!new ZjuSettingsStore(app).enabled()||!new ZjuSettingsStore(app).configured())throw new IllegalStateException("Runner school fixture required");
        SharedPreferences monitor=app.getSharedPreferences("chrona_classroom_monitor",0);Map<String,?> before=monitor.getAll();
        ClassroomAutoSummary.Generator oldGenerator=ClassroomAutoSummary.generator;ClassroomAutoSummary.Settings oldSettings=ClassroomAutoSummary.settings;
        java.util.function.BooleanSupplier oldForeground=ClassroomAutoSummary.foreground;
        java.util.function.BiFunction<String,String,ClassroomClient> oldClients=ClassroomMonitor.clients;
        ClassroomAutoSummaryChecks checks=new ClassroomAutoSummaryChecks(test);
        try{
            monitor.edit().clear().commit();
            ClassroomAutoSummary.foreground=()->checks.owner;ClassroomAutoSummary.settings=context->checks.ai;
            ClassroomAutoSummary.generator=(job,ai,prompt)->{
                checks.calls.incrementAndGet();checks.lastLimit=job.imageLimit;
                if(!ClassroomStore.active(job.dir))throw new AssertionError("Generator must own the production material lease");
                JSONObject result=new JSONObject().put("lesson",job.selected.json()).put("complete",true).put("updated",System.currentTimeMillis());
                ClassroomStore.write(new File(job.dir,"summary.json"),result);return result;
            };
            checks.policyDefaults();
            checks.materialGate();checks.queueAndDedup();checks.savedAndMissing();checks.unknownAndScope();checks.accountCancellation();
            checks.legacyFinishedRecovery();
            checks.deletedLessonIsTerminal();
            checks.policyIdentityAndScope();checks.policyMatrix();checks.policyMaterialAndRecovery();checks.policyRunningAndLegacySnapshots();
            checks.ownerLossBeforeDestroy(false);checks.ownerLossBeforeDestroy(true);
            return checks.assertions;
        }finally{
            checks.owner=false;test.runOnMainSync(ClassroomAutoSummary::ownerStopped);
            try{checks.awaitIdle();}finally{
                ClassroomAutoSummary.generator=oldGenerator;ClassroomAutoSummary.settings=oldSettings;ClassroomAutoSummary.foreground=oldForeground;ClassroomMonitor.clients=oldClients;
                SharedPreferences.Editor edit=monitor.edit().clear();for(Map.Entry<String,?> entry:before.entrySet())edit.putString(entry.getKey(),(String)entry.getValue());edit.commit();
            }
        }
    }
    private ClassroomLesson lesson(){return new ClassroomLesson("991000",Long.toString(identityBase+(++sequence)),"自动总结验证"+sequence,"课堂","教室","6",System.currentTimeMillis()-600_000);}
    private ClassroomLesson policyLesson(){ClassroomLesson lesson=lesson();return new ClassroomLesson("992"+lesson.id,lesson.id,"策略验证课程",lesson.title,lesson.room,lesson.status,lesson.startsAt);}
    private ClassroomLesson nextLesson(ClassroomLesson lesson){ClassroomLesson next=lesson();return new ClassroomLesson(lesson.courseId,next.id,lesson.course,"后续课堂",lesson.room,"6",lesson.startsAt+3_600_000);}
    private void check(boolean condition,String label){assertions++;if(!condition)throw new AssertionError(label);}
    private String state(ClassroomLesson lesson)throws Exception{JSONObject task=ClassroomMonitorStore.auto(store.read(),lesson);return task==null?"":task.optString("state");}
    private void dispatch(){test.runOnMainSync(()->ClassroomAutoSummary.dispatch(app));}
    private void waitFor(ClassroomLesson lesson,String expected)throws Exception{
        long end=SystemClock.elapsedRealtime()+10_000;while(SystemClock.elapsedRealtime()<end){test.waitForIdleSync();if(expected.equals(state(lesson))){awaitIdle();return;}SystemClock.sleep(30);}
        throw new AssertionError("Expected "+expected+", got "+state(lesson)+": "+store.read());
    }
    private void awaitIdle()throws Exception{
        long end=SystemClock.elapsedRealtime()+10_000;
        while(SystemClock.elapsedRealtime()<end){final boolean[] busy={false};test.runOnMainSync(()->{try{for(JSONObject task:ClassroomMonitorStore.autoTasks(store.read()))if(ClassroomWorkService.find(account,ClassroomLesson.fromJson(task.getJSONObject("lesson")))!=null)busy[0]=true;}catch(Exception e){throw new RuntimeException(e);}});if(!busy[0])return;SystemClock.sleep(30);}
        throw new AssertionError("Automatic Session did not release registry");
    }
    private void materialGate()throws Exception{
        ClassroomLesson lesson=lesson();store.add(lesson,false);store.catalog(java.util.List.of(lesson),System.currentTimeMillis());
        ClassroomMonitor.clients=(user,password)->fake(lesson);phase=0;pass();
        check(state(lesson).isEmpty(),"Live/incomplete materials do not queue AI");
        check(!new File(new ClassroomStore(app,account).localLesson(lesson),"materials.json").exists(),"Live cache is not formal material");
        phase=1;pass();
        File dir=new ClassroomStore(app,account).localLesson(lesson);
        check("queued".equals(state(lesson)),"Complete server replay queues automatic summary");
        check(!ClassroomStore.read(new File(dir,"materials.json")).optBoolean("monitoring")&&new File(dir,"candidates.json").isFile(),"Queue follows formal transcript/candidate commit");
        check(ClassroomMonitorStore.pending(store.read()).isEmpty(),"Done material is not downloaded again while summary queues");
        check(calls.get()==0,"Without foreground owner no generator or second FGS is started");
        check(ClassroomMonitorStore.next(store.read(),System.currentTimeMillis())>0,"Queued summary retains the existing recovery schedule");
        owner=true;dispatch();waitFor(lesson,"complete");check(calls.get()==1&&lastLimit==0,"Default automatic request uploads text only");
    }
    private void deletedLessonIsTerminal()throws Exception{
        owner=false;ClassroomLesson removed=lesson();prepared(removed);
        store.add(removed,true);ClassroomLesson future=new ClassroomLesson(removed.courseId,removed.id+"8",removed.course,"后续课堂","","6",removed.startsAt+3_600_000);
        store.catalog(java.util.List.of(removed,future),System.currentTimeMillis());int before=calls.get();
        store.deleted(removed);owner=true;dispatch();dispatch();
        check("deleted".equals(state(removed))&&calls.get()==before,"deleted completed lesson never rebuilds its automatic summary");
        check(!store.recoverFinished(removed)&&!ClassroomMonitorStore.matches(store.read(),removed),"deleted terminal state prevents download and legacy queue recovery");
        check(ClassroomMonitorStore.matches(store.read(),future)&&ClassroomMonitorStore.ruleFor(store.read(),future)!=null,"deleting one lesson preserves course future monitoring");
        store.stop(removed);
    }
    private void prepared(ClassroomLesson lesson)throws Exception{
        materialFiles(lesson);
        store.add(lesson,false);store.finished(lesson);
    }
    private void materialFiles(ClassroomLesson lesson)throws Exception{
        File dir=new ClassroomStore(app,account).localLesson(lesson);
        ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("monitoring",false).put("raw_images",0).put("segments",new JSONArray().put(new JSONObject().put("start",0).put("end",1).put("text","完整字幕"))));
        ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject().put("lesson",lesson.json()).put("images",new JSONArray()));
    }
    private void policyDefaults()throws Exception{
        ClassroomLesson lesson=policyLesson();
        check(!store.read().has("autoSummary")&&store.autoSummary(),"Missing automatic-summary preference preserves enabled default");
        check(store.summaryPolicy(lesson)==ClassroomMonitorStore.SUMMARY_FOLLOW_GLOBAL
                &&ClassroomMonitorStore.automaticSummary(store.read(),lesson),"New course follows the enabled global default");
        check(!store.summaryImages()&&ClassroomAutoSummary.imageLimit(app,account)==0,"Automatic default does not grant image uploads");
    }
    private void policyIdentityAndScope()throws Exception{
        owner=false;ClassroomLesson first=policyLesson(),sibling=nextLesson(first),sameName=policyLesson();
        store.add(first,true);store.catalog(java.util.List.of(first,sibling,sameName),System.currentTimeMillis());
        String rules=store.read().getJSONArray("rules").toString(),catalog=store.read().getJSONArray("catalog").toString();
        store.summaryPolicy(first,ClassroomMonitorStore.SUMMARY_OFF);store.autoSummary(false);store.summaryImages(true);
        check(rules.equals(store.read().getJSONArray("rules").toString())&&catalog.equals(store.read().getJSONArray("catalog").toString()),"Saving preferences cannot replace registrations or their discovered lessons");
        check(store.summaryPolicy(sibling)==ClassroomMonitorStore.SUMMARY_OFF&&!ClassroomMonitorStore.automaticSummary(store.read(),sibling),"Course override applies to another lesson with the same course ID");
        check(first.course.equals(sameName.course)&&store.summaryPolicy(sameName)==ClassroomMonitorStore.SUMMARY_FOLLOW_GLOBAL,"Identical course names with different IDs have independent overrides");
        ClassroomMonitorStore reopened=new ClassroomMonitorStore(app,account);
        check(!reopened.autoSummary()&&reopened.summaryPolicy(sibling)==ClassroomMonitorStore.SUMMARY_OFF&&reopened.summaryImages(),"Account preferences survive a new store instance");
        ClassroomMonitorStore other=new ClassroomMonitorStore(app,"policy-other-"+identityBase);
        check(other.autoSummary()&&!other.summaryImages()&&other.summaryPolicy(first)==ClassroomMonitorStore.SUMMARY_FOLLOW_GLOBAL,"Another account never inherits this account's preferences");
        other.autoSummary(false);other.summaryPolicy(first,ClassroomMonitorStore.SUMMARY_ON);
        check(ClassroomMonitorStore.automaticSummary(other.read(),first)&&!ClassroomMonitorStore.automaticSummary(store.read(),first),"Same real course ID remains isolated across accounts");
        check(store.summaryImages()&&ClassroomAutoSummary.imageLimit(app,account)==ClassroomSummary.MAX_IMAGES,"Global automatic-summary off retains image permission for explicit manual and batch requests");
        store.summaryPolicy(first,ClassroomMonitorStore.SUMMARY_FOLLOW_GLOBAL);
        check(!store.read().getJSONObject("summaryPolicies").has(first.courseId)&&store.summaryPolicy(sibling)==ClassroomMonitorStore.SUMMARY_FOLLOW_GLOBAL,"Following global removes the course override rather than storing a lesson override");
        check(ClassroomMonitorStore.ruleFor(store.read(),sibling)!=null&&ClassroomMonitorStore.matches(store.read(),sibling),"Preference changes leave repeat monitoring active");
        store.stop(first);store.autoSummary(true);store.summaryImages(false);
    }
    private void policyMatrix()throws Exception{
        owner=false;int before=calls.get();java.util.List<ClassroomLesson> queued=new java.util.ArrayList<>(),skipped=new java.util.ArrayList<>();
        store.summaryImages(true);
        for(boolean global:new boolean[]{false,true})for(int policy:new int[]{ClassroomMonitorStore.SUMMARY_FOLLOW_GLOBAL,ClassroomMonitorStore.SUMMARY_OFF,ClassroomMonitorStore.SUMMARY_ON}){
            ClassroomLesson lesson=policyLesson();store.add(lesson,false);String rules=store.read().getJSONArray("rules").toString();
            store.autoSummary(global);store.summaryPolicy(lesson,policy);
            boolean enabled=policy==ClassroomMonitorStore.SUMMARY_ON||policy==ClassroomMonitorStore.SUMMARY_FOLLOW_GLOBAL&&global;
            String label="global="+global+", course="+policy;
            check(rules.equals(store.read().getJSONArray("rules").toString())&&state(lesson).isEmpty(),"Registration does not snapshot automatic policy before material completion: "+label);
            check(ClassroomMonitorStore.automaticSummary(store.read(),lesson)==enabled,"Resolve all global/course policy combinations: "+label);
            materialFiles(lesson);store.finished(lesson);JSONObject task=ClassroomMonitorStore.auto(store.read(),lesson);
            check((enabled?"queued":"skipped").equals(task.getString("state"))&&task.getBoolean("automaticSummary")==enabled,"Material commit persists the resolved automatic decision: "+label);
            check(task.getInt("imageLimit")== (enabled?ClassroomSummary.MAX_IMAGES:0),"Skipped tasks never retain image upload authorization: "+label);
            if(enabled)queued.add(lesson);else{
                skipped.add(lesson);check(ClassroomMonitorStore.ruleFor(store.read(),lesson)==null&&!ClassroomMonitorStore.matches(store.read(),lesson),"Skipped single registration completes normally: "+label);
            }
        }
        for(ClassroomLesson lesson:queued){
            String snapshot=ClassroomMonitorStore.auto(store.read(),lesson).toString();store.autoSummary(false);store.summaryPolicy(lesson,ClassroomMonitorStore.SUMMARY_OFF);store.summaryImages(false);
            check(snapshot.equals(ClassroomMonitorStore.auto(store.read(),lesson).toString()),"Later global/course/image choices do not rewrite a queued decision");
        }
        owner=true;dispatch();for(ClassroomLesson lesson:queued)waitFor(lesson,"complete");
        check(calls.get()==before+queued.size()&&queued.size()==3,"Only the three enabled matrix cells dispatch their production Sessions");
        for(ClassroomLesson lesson:skipped){store.autoSummary(true);store.summaryPolicy(lesson,ClassroomMonitorStore.SUMMARY_ON);store.add(lesson,false);store.finished(lesson);dispatch();
            check("skipped".equals(state(lesson))&&!store.recoverFinished(lesson),"Enabling later cannot revive a skipped terminal outcome");store.stop(lesson);}
        check(calls.get()==before+queued.size(),"Skipped terminal entries never bill after preference changes");store.autoSummary(true);store.summaryImages(false);
    }
    private void policyMaterialAndRecovery()throws Exception{
        owner=false;int before=calls.get();ClassroomLesson lesson=policyLesson();store.add(lesson,false);store.catalog(java.util.List.of(lesson),System.currentTimeMillis());
        ClassroomMonitor.clients=(user,password)->fake(lesson);phase=0;pass();
        check(state(lesson).isEmpty(),"Live material has no automatic-policy snapshot");
        store.autoSummary(false);store.summaryImages(true);phase=1;pass();File dir=new ClassroomStore(app,account).localLesson(lesson);
        JSONObject materials=ClassroomStore.read(new File(dir,"materials.json"));
        check(materials!=null&&!materials.optBoolean("monitoring")&&materials.getJSONArray("segments").length()>0&&new File(dir,"candidates.json").isFile(),"Automatic off still publishes formal complete transcript and image candidates through the real monitor pass");
        check("skipped".equals(state(lesson))&&ClassroomMonitorStore.auto(store.read(),lesson).getInt("imageLimit")==0,"Policy is resolved at complete material publication, not at initial registration");
        check(ClassroomMonitorStore.ruleFor(store.read(),lesson)==null&&ClassroomMonitorStore.pending(store.read()).isEmpty(),"Disabled summary still finishes single monitoring and download work");
        AtomicInteger settingsReads=new AtomicInteger();ClassroomAutoSummary.Settings priorSettings=ClassroomAutoSummary.settings;
        ClassroomAutoSummary.settings=context->{settingsReads.incrementAndGet();return ai;};
        try{
            owner=true;dispatch();check(calls.get()==before&&settingsReads.get()==0&&!new File(dir,"summary.json").exists(),"Skipped material does not load AI settings or dispatch a generator");
            owner=false;ClassroomLesson recovered=policyLesson();materialFiles(recovered);legacyFinished(store,recovered);dispatch();
            check("skipped".equals(state(recovered))&&ClassroomMonitorStore.auto(store.read(),recovered).getInt("imageLimit")==0,"Legacy finished material recovers using current disabled policy without billing");
            String terminal=ClassroomMonitorStore.auto(store.read(),recovered).toString();store.autoSummary(true);store.summaryPolicy(recovered,ClassroomMonitorStore.SUMMARY_ON);
            owner=true;dispatch();check(!store.recoverFinished(recovered)&&terminal.equals(ClassroomMonitorStore.auto(store.read(),recovered).toString())&&calls.get()==before&&settingsReads.get()==0,"Recovered skip remains terminal when preferences are enabled later");
        }finally{ClassroomAutoSummary.settings=priorSettings;}
        owner=false;store.autoSummary(false);ClassroomLesson repeat=policyLesson(),future=nextLesson(repeat);materialFiles(repeat);store.add(repeat,true);store.catalog(java.util.List.of(repeat,future),System.currentTimeMillis());store.finished(repeat);
        check("skipped".equals(state(repeat))&&ClassroomMonitorStore.ruleFor(store.read(),future)!=null&&ClassroomMonitorStore.matches(store.read(),future),"Skipped current lesson retains repeat registration and its future lesson");
        store.summaryPolicy(future,ClassroomMonitorStore.SUMMARY_ON);materialFiles(future);store.finished(future);
        check("queued".equals(state(future))&&ClassroomMonitorStore.auto(store.read(),future).getInt("imageLimit")==ClassroomSummary.MAX_IMAGES,"Course-on overrides global-off for a future lesson with retained image permission");
        owner=true;dispatch();waitFor(future,"complete");check(calls.get()==before+1,"Only the newly enabled future lesson dispatches once");store.stop(repeat);
        owner=false;store.autoSummary(true);ClassroomLesson failed=policyLesson();prepared(failed);store.summaryState(failed,"queued","failed","controlled terminal failure");String terminal=ClassroomMonitorStore.auto(store.read(),failed).toString();
        store.autoSummary(false);store.summaryPolicy(failed,ClassroomMonitorStore.SUMMARY_OFF);store.autoSummary(true);store.summaryPolicy(failed,ClassroomMonitorStore.SUMMARY_ON);store.add(failed,false);store.finished(failed);owner=true;dispatch();
        check(!store.recoverFinished(failed)&&terminal.equals(ClassroomMonitorStore.auto(store.read(),failed).toString())&&calls.get()==before+1,"Policy toggles and re-registration cannot retry a failed terminal task");store.stop(failed);store.summaryImages(false);
    }
    private void policyRunningAndLegacySnapshots()throws Exception{
        owner=false;store.autoSummary(true);store.summaryImages(true);ClassroomLesson running=policyLesson();prepared(running);
        ClassroomAutoSummary.Generator prior=ClassroomAutoSummary.generator;CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);int before=calls.get();
        ClassroomAutoSummary.generator=(job,ai,prompt)->{entered.countDown();while(!release.await(20,TimeUnit.MILLISECONDS))job.control.check();job.control.check();return prior.generate(job,ai,prompt);};
        try{
            owner=true;dispatch();check(entered.await(5,TimeUnit.SECONDS)&&"running".equals(state(running)),"Controlled generator enters the actual running Session");
            String snapshot=ClassroomMonitorStore.auto(store.read(),running).toString();store.autoSummary(false);store.summaryPolicy(running,ClassroomMonitorStore.SUMMARY_OFF);store.summaryImages(false);dispatch();
            check(snapshot.equals(ClassroomMonitorStore.auto(store.read(),running).toString()),"Changing all preferences neither cancels nor rewrites running work");
            release.countDown();waitFor(running,"complete");check(calls.get()==before+1&&lastLimit==ClassroomSummary.MAX_IMAGES,"Running Session completes with its original image snapshot after preferences are disabled");
        }finally{release.countDown();ClassroomAutoSummary.generator=prior;}
        owner=false;store.autoSummary(true);ClassroomLesson legacy=policyLesson();prepared(legacy);
        synchronized(ClassroomMonitorStore.LOCK){JSONObject state=store.read();ClassroomMonitorStore.auto(state,legacy).remove("automaticSummary");
            if(!app.getSharedPreferences("chrona_classroom_monitor",0).edit().putString(account,state.toString()).commit())throw new IOException("Fixture legacy queue write failed");}
        store.autoSummary(false);store.summaryPolicy(legacy,ClassroomMonitorStore.SUMMARY_OFF);owner=true;dispatch();waitFor(legacy,"complete");
        check(calls.get()==before+2&&lastLimit==0,"Already queued legacy task without an automatic-summary flag still dispatches despite current disabled preferences");store.autoSummary(true);store.summaryImages(false);
    }
    private void queueAndDedup()throws Exception{
        owner=false;ClassroomLesson lesson=lesson();prepared(lesson);store.finished(lesson);
        check(new ClassroomMonitorStore(app,account).read().getJSONObject("summaries").has(ClassroomMonitorStore.key(lesson)),"Queued work survives a new store instance");
        check(ClassroomMonitorStore.auto(store.read(),lesson).getInt("imageLimit")==0,"Permission snapshot defaults to zero");
        store.summaryImages(true);check(ClassroomMonitorStore.auto(store.read(),lesson).getInt("imageLimit")==0,"Later permission changes do not upgrade queued requests");
        int before=calls.get();owner=true;dispatch();waitFor(lesson,"complete");dispatch();check(calls.get()==before+1,"Repeated dispatch and completion invoke generator once");
        store.add(lesson,false);store.finished(lesson);dispatch();check(calls.get()==before+1,"Registering the same real lesson does not bill again");store.stop(lesson);
        ClassroomLesson images=lesson();owner=false;prepared(images);check(ClassroomMonitorStore.auto(store.read(),images).getInt("imageLimit")==40,"Explicit image permission is snapshotted");
        store.summaryImages(false);owner=true;dispatch();waitFor(images,"complete");check(lastLimit==40,"Queued request retains its explicit image mode");
    }
    private void savedAndMissing()throws Exception{
        owner=false;ClassroomLesson saved=lesson();prepared(saved);File dir=new ClassroomStore(app,account).localLesson(saved);
        ClassroomStore.write(new File(dir,"summary.json"),new JSONObject().put("complete",true));int before=calls.get();owner=true;dispatch();waitFor(saved,"complete");check(calls.get()==before,"Existing complete summary is never regenerated automatically");
        owner=false;ClassroomLesson missing=lesson();prepared(missing);ClassroomAutoSummary.settings=context->null;owner=true;dispatch();waitFor(missing,"failed");
        check(calls.get()==before&&new File(new ClassroomStore(app,account).localLesson(missing),"materials.json").isFile(),"Missing AI configuration fails visibly without losing materials or billing");
        check(ClassroomMonitorStore.auto(store.read(),missing).optString("detail").contains("配置"),"Missing configuration has an actionable persisted failure");
        ClassroomAutoSummary.settings=context->ai;
    }
    private void unknownAndScope()throws Exception{
        owner=false;ClassroomLesson unknown=lesson();prepared(unknown);store.summaryState(unknown,"queued","running","fixture request started");
        int before=calls.get();owner=true;dispatch();waitFor(unknown,"interrupted");check(calls.get()==before,"Lost running request is not automatically resent");
        check(ClassroomMonitorStore.auto(store.read(),unknown).optString("detail").contains("未知"),"Unknown billing result remains explicit");
        owner=false;ClassroomLesson stopped=lesson(),other=lesson();prepared(stopped);prepared(other);store.stop(stopped);owner=true;dispatch();waitFor(other,"complete");
        check("interrupted".equals(state(stopped))&&calls.get()==before+1,"Stopping one scope cancels only its automatic work");
        owner=false;ClassroomLesson running=lesson();prepared(running);CountDownLatch entered=new CountDownLatch(1);
        ClassroomAutoSummary.Generator prior=ClassroomAutoSummary.generator;
        ClassroomAutoSummary.generator=(job,ai,prompt)->{entered.countDown();while(true){job.control.check();SystemClock.sleep(10);}};
        try{owner=true;dispatch();check(entered.await(5,TimeUnit.SECONDS),"Production Session starts controlled request");store.stop(running);dispatch();awaitIdle();check("interrupted".equals(state(running)),"Scope stop cancels the active monitor-owned Session");}
        finally{ClassroomAutoSummary.generator=prior;}
    }
    private void accountCancellation()throws Exception{
        owner=false;ClassroomLesson queued=lesson();prepared(queued);ZjuSettingsStore school=new ZjuSettingsStore(app);
        try{school.prefs.edit().putString("account","different-fixture-account").commit();dispatch();check("interrupted".equals(state(queued)),"Account switch interrupts queued work without requesting AI");}
        finally{school.prefs.edit().putString("account",account).commit();}
    }
    private void legacyFinished(ClassroomMonitorStore target,ClassroomLesson lesson)throws Exception{
        // Old downloads kept the registration and done identity without an automatic task.
        // Do not call today's finished(): a disabled policy correctly removes its single rule.
        synchronized(ClassroomMonitorStore.LOCK){JSONObject state=target.read();String key=ClassroomMonitorStore.key(lesson);
            JSONArray rules=state.getJSONArray("rules"),done=state.getJSONArray("done");
            for(int i=rules.length()-1;i>=0;i--)if(key.equals(ClassroomMonitorStore.key(ClassroomLesson.fromJson(rules.getJSONObject(i).getJSONObject("lesson")))))rules.remove(i);
            rules.put(new JSONObject().put("lesson",lesson.json()).put("repeat",false));
            boolean downloaded=false;for(int i=0;i<done.length();i++)if(key.equals(done.getString(i)))downloaded=true;
            if(!downloaded)done.put(key);
            JSONObject tasks=state.optJSONObject("summaries");if(tasks!=null)tasks.remove(key);
            if(!app.getSharedPreferences("chrona_classroom_monitor",0).edit().putString(target.account,state.toString()).commit())throw new IOException("Fixture legacy state write failed");
            check(ClassroomMonitorStore.ruleFor(target.read(),lesson)!=null&&ClassroomMonitorStore.auto(target.read(),lesson)==null
                    &&ClassroomMonitorStore.recoverableFinished(target.read()).stream().anyMatch(item->key.equals(ClassroomMonitorStore.key(item))),"Legacy fixture retains an active rule plus downloaded identity and no automatic task");}
    }
    private void legacyFinishedRecovery()throws Exception{
        owner=false;int before=calls.get();ClassroomLesson saved=lesson();prepared(saved);legacyFinished(store,saved);
        File savedDir=new ClassroomStore(app,account).localLesson(saved);ClassroomStore.write(new File(savedDir,"summary.json"),new JSONObject().put("complete",true));
        owner=true;dispatch();waitFor(saved,"complete");check(calls.get()==before,"Old done without task adopts existing manual summary without AI");
        owner=false;ClassroomLesson fresh=lesson();prepared(fresh);legacyFinished(store,fresh);store.summaryImages(true);dispatch();
        check("queued".equals(state(fresh))&&ClassroomMonitorStore.auto(store.read(),fresh).getInt("imageLimit")==40,"Old completed material recovers one queued request with current explicit image permission");
        long queuedAt=ClassroomMonitorStore.auto(store.read(),fresh).getLong("updated");store.summaryImages(false);dispatch();
        check(!store.recoverFinished(fresh)&&ClassroomMonitorStore.auto(store.read(),fresh).getLong("updated")==queuedAt
                &&ClassroomMonitorStore.auto(store.read(),fresh).getInt("imageLimit")==40,"Recovery reentry cannot reset queued work or its permission snapshot");
        owner=true;dispatch();waitFor(fresh,"complete");dispatch();check(calls.get()==before+1,"Recovered no-summary lesson requests AI once");
        owner=false;ClassroomLesson stopped=lesson();prepared(stopped);legacyFinished(store,stopped);store.stop(stopped);dispatch();
        check(state(stopped).isEmpty()&&!store.recoverFinished(stopped),"Stopped monitor range is never recreated for recovery");
        ClassroomLesson missing=lesson();legacyFinished(store,missing);dispatch();check(state(missing).isEmpty(),"Old done with no formal material is not queued");
        for(String invalid:new String[]{"monitoring","portable","empty","identity","candidates"}){
            ClassroomLesson lesson=lesson();prepared(lesson);legacyFinished(store,lesson);File dir=new ClassroomStore(app,account).localLesson(lesson);
            JSONObject materials=ClassroomStore.read(new File(dir,"materials.json")),candidates=ClassroomStore.read(new File(dir,"candidates.json"));
            if(invalid.equals("monitoring")||invalid.equals("portable")){materials.put(invalid,true);ClassroomStore.write(new File(dir,"materials.json"),materials);}
            else if(invalid.equals("empty")){materials.put("segments",new JSONArray());ClassroomStore.write(new File(dir,"materials.json"),materials);}
            else if(invalid.equals("identity")){candidates.getJSONObject("lesson").put("id",saved.id);ClassroomStore.write(new File(dir,"candidates.json"),candidates);}
            else new android.util.AtomicFile(new File(dir,"candidates.json")).delete();
            dispatch();check(state(lesson).isEmpty(),"Recovery rejects incomplete or mismatched formal material: "+invalid);store.stop(lesson);
        }
        ClassroomMonitorStore other=new ClassroomMonitorStore(app,"auto-recovery-other-account");ClassroomLesson foreign=lesson();
        File foreignDir=new ClassroomStore(app,other.account).localLesson(foreign);
        ClassroomStore.write(new File(foreignDir,"materials.json"),new JSONObject().put("monitoring",false).put("segments",new JSONArray().put(new JSONObject().put("text","other account"))));
        ClassroomStore.write(new File(foreignDir,"candidates.json"),new JSONObject().put("lesson",foreign.json()).put("images",new JSONArray()));
        legacyFinished(other,foreign);legacyFinished(store,foreign);dispatch();
        check(ClassroomMonitorStore.auto(other.read(),foreign)==null&&state(foreign).isEmpty(),"Recovery never queues inactive-account history or borrows its material");
        store.stop(foreign);other.stop(foreign);
        ClassroomLesson unknown=lesson(),base=lesson();prepared(unknown);legacyFinished(store,unknown);store.stop(unknown);
        ClassroomLesson anchor=new ClassroomLesson(base.courseId,base.id,base.course,base.title,base.room,base.status,unknown.startsAt-60_000);store.add(anchor,true);
        check(ClassroomMonitorStore.ruleFor(store.read(),unknown)!=null,"Unmapped fixture remains inside the real course scope");
        dispatch();check(state(unknown).isEmpty(),"Unmapped done identities are not found by scanning material directories");store.stop(anchor);
        check(calls.get()==before+1,"Invalid, stopped and cross-account legacy entries never invoke AI");
    }
    /** Exercise the actual timeout and credential listener, keeping destroy deliberately absent. */
    private void ownerLossBeforeDestroy(boolean credentials)throws Exception{
        owner=false;ClassroomMonitor.initialize(app);
        ClassroomLesson first=lesson(),queued=lesson();prepared(first);
        ClassroomAutoSummary.Generator prior=ClassroomAutoSummary.generator;
        java.util.function.BooleanSupplier priorOwner=ClassroomAutoSummary.foreground;
        CountDownLatch entered=new CountDownLatch(1);AtomicInteger requests=new AtomicInteger();
        ZjuSettingsStore school=new ZjuSettingsStore(app);String savedCredentials=school.prefs.getString("credentials",null);
        ClassroomMonitorService service=new ClassroomMonitorService();
        java.lang.reflect.Method attach=android.content.ContextWrapper.class.getDeclaredMethod("attachBaseContext",Context.class);
        attach.setAccessible(true);attach.invoke(service,app);
        Field running=ClassroomMonitorService.class.getDeclaredField("running");running.setAccessible(true);boolean wasRunning=running.getBoolean(null);
        ClassroomAutoSummary.foreground=ClassroomMonitorService::running;
        ClassroomAutoSummary.generator=(job,ai,prompt)->{requests.incrementAndGet();entered.countDown();while(true){job.control.check();SystemClock.sleep(10);}};
        try{
            test.runOnMainSync(()->{try{running.setBoolean(null,true);}catch(Exception e){throw new RuntimeException(e);}});
            dispatch();check(entered.await(5,TimeUnit.SECONDS),"Real-owner Session enters controlled AI request");
            prepared(queued);check("queued".equals(state(queued)),"Second lesson queues while the first request runs");
            final boolean[] closedBeforeReturn={false};
            test.runOnMainSync(()->{
                if(credentials)school.prefs.edit().putString("credentials",savedCredentials+" ").commit();
                else service.onTimeout(1,android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
                closedBeforeReturn[0]=!ClassroomMonitorService.running();
            });
            check(closedBeforeReturn[0],credentials?"Credentials listener closes owner before dispatch":"Actual timeout closes owner synchronously");
            waitFor(first,"interrupted");
            check("queued".equals(state(queued))&&requests.get()==1,
                    credentials?"Credentials callback and first completion cannot start queued AI before destroy":"Timeout cancellation completion cannot start queued AI before destroy");
            final boolean[] absent={false};test.runOnMainSync(()->absent[0]=ClassroomWorkService.find(account,first)==null&&ClassroomWorkService.find(account,queued)==null);
            check(absent[0],"First completion releases registry while the second stays undispatched");
        }finally{
            test.runOnMainSync(ClassroomMonitorService::ownerStopping);awaitIdle();
            if(credentials)school.prefs.edit().putString("credentials",savedCredentials).commit();
            store.stop(queued);ClassroomMonitor.schedule(app);
            running.setBoolean(null,wasRunning);ClassroomAutoSummary.foreground=priorOwner;ClassroomAutoSummary.generator=prior;
        }
    }
    private void pass()throws Exception{
        CountDownLatch done=new CountDownLatch(1);final boolean[] started={false};test.runOnMainSync(()->started[0]=ClassroomMonitor.run(app,active->done.countDown()));
        check(started[0]&&done.await(10,TimeUnit.SECONDS),"Production monitor pass completes");
    }
    private static Object field(Object object,String name)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);return field.get(object);}
    private static void field(Object object,String name,Object value)throws Exception{Field field=object.getClass().getDeclaredField(name);field.setAccessible(true);field.set(object,value);}
    private ClassroomClient fake(ClassroomLesson lesson){try{
        ClassroomClient client=new ClassroomClient("fixture","fixture");ZjuClient school=(ZjuClient)field(client,"school");field(school,"classroomLoggedIn",true);
        OkHttpClient http=(OkHttpClient)field(school,"http");field(school,"http",http.newBuilder().addInterceptor(chain->{
            Request request=chain.request();String path=request.url().encodedPath();JSONObject json;
            try{
                if(path.endsWith("catalogue"))json=new JSONObject().put("success",true).put("result",new JSONObject().put("code",200).put("data",new JSONArray().put(new JSONObject().put("course_id",lesson.courseId).put("sub_id",lesson.id).put("start_at",lesson.startsAt/1000).put("status",phase==0?"1":"6").put("title",lesson.title))));
                else if(path.endsWith("search-trans-result"))json=new JSONObject().put("code",phase==0?10002:0).put("total",1).put("list",new JSONArray().put(new JSONObject().put("sub_id",lesson.id).put("all_content",new JSONArray().put(new JSONObject().put("BeginSec",0).put("EndSec",1).put("Text","完整字幕")))));
                else if(path.endsWith("search-ppt"))json=new JSONObject().put("code",0).put("total",0).put("list",new JSONArray());
                else throw new IOException("Unexpected fixture route: "+path);
            }catch(JSONException invalid){throw new IOException(invalid);}
            return new Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("fixture").header("Content-Type","application/json")
                    .body(ResponseBody.create(json.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8),MediaType.get("application/json"))).build();
        }).build());return client;
    }catch(Exception failure){throw new IllegalStateException(failure);}}
}
