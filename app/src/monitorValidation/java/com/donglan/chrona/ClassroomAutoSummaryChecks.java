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
            checks.materialGate();checks.queueAndDedup();checks.savedAndMissing();checks.unknownAndScope();checks.accountCancellation();
            checks.legacyFinishedRecovery();
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
    private void prepared(ClassroomLesson lesson)throws Exception{
        File dir=new ClassroomStore(app,account).localLesson(lesson);
        ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("monitoring",false).put("raw_images",0).put("segments",new JSONArray().put(new JSONObject().put("start",0).put("end",1).put("text","完整字幕"))));
        ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject().put("lesson",lesson.json()).put("images",new JSONArray()));
        store.add(lesson,false);store.finished(lesson);
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
        target.add(lesson,false);target.finished(lesson);
        synchronized(ClassroomMonitorStore.LOCK){JSONObject state=target.read();state.getJSONObject("summaries").remove(ClassroomMonitorStore.key(lesson));
            if(!app.getSharedPreferences("chrona_classroom_monitor",0).edit().putString(target.account,state.toString()).commit())throw new IOException("Fixture legacy state write failed");}
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
