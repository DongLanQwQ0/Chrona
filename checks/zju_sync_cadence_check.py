"""Run the production sync owner and scheduler against deterministic, network-free doubles."""
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/zju-sync-cadence-check'
src = out / 'src'
files = {
'com/donglan/chrona/ZjuHomeworkPlanner.java': '''package com.donglan.chrona;class ZjuHomeworkPlanner {static String processOne(android.content.Context c){return "";}static void cancel(){}static void cancel(int source){}}''',
'android/content/SharedPreferences.java': '''package android.content;public interface SharedPreferences {long getLong(String k,long d);default boolean getBoolean(String k,boolean d){return d;}Editor edit();interface Editor {Editor putLong(String k,long v);default Editor putString(String k,String v){return this;}default Editor putBoolean(String k,boolean v){return this;}void apply();}}''',
'android/content/Context.java': '''package android.content;public class Context {public Context getApplicationContext(){return this;}public <T>T getSystemService(Class<T> type){return type.cast(android.app.job.JobScheduler.INSTANCE);}}''',
'android/content/ComponentName.java': '''package android.content;public class ComponentName {public ComponentName(Context c,Class<?> type){}}''',
'android/os/Looper.java': '''package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}''',
'android/os/Handler.java': '''package android.os;public class Handler {public static final java.util.Queue<Runnable> queue=new java.util.concurrent.ConcurrentLinkedQueue<>();public static int delayed;public Handler(Looper l){}public void post(Runnable r){queue.add(r);}public void postDelayed(Runnable r,long t){if(t!=60000)throw new AssertionError();delayed++;}public void removeCallbacks(Runnable r){delayed=0;}public static void flush(){Runnable r;while((r=queue.poll())!=null)r.run();}}''',
'android/app/job/JobInfo.java': '''package android.app.job;public class JobInfo {public long delay;public boolean persisted;public int network;public static final int NETWORK_TYPE_ANY=1;public static class Builder {JobInfo job=new JobInfo();public Builder(int id,android.content.ComponentName c){if(id!=73111)throw new AssertionError();}public Builder setRequiredNetworkType(int n){job.network=n;return this;}public Builder setMinimumLatency(long n){job.delay=n;return this;}public Builder setPersisted(boolean p){job.persisted=p;return this;}public JobInfo build(){return job;}}}''',
'android/app/job/JobScheduler.java': '''package android.app.job;public class JobScheduler {public static final JobScheduler INSTANCE=new JobScheduler();public JobInfo latest;public int schedules,cancels;public void cancel(int id){cancels++;latest=null;}public int schedule(JobInfo j){latest=j;schedules++;return 1;}}''',
'android/app/job/JobParameters.java': '''package android.app.job;public class JobParameters {}''',
'android/app/job/JobService.java': '''package android.app.job;public class JobService extends android.content.Context {public boolean onStartJob(JobParameters p){return false;}public boolean onStopJob(JobParameters p){return false;}public void jobFinished(JobParameters p,boolean retry){}}''',
'com/donglan/chrona/ZjuSettingsStore.java': '''package com.donglan.chrona;class ZjuSettingsStore {static boolean on=true,configured=true,table=true,credentialFailure;static String account="account",status="";static final Prefs PREFS=new Prefs();final Prefs prefs=PREFS;ZjuSettingsStore(android.content.Context c){}boolean enabled(){return on;}boolean configured(){return configured;}boolean timetable(){return on&&table;}String account(){return account;}String status(){return status;}void status(String s){status=s;}String[] credentials()throws java.io.IOException{if(credentialFailure)throw new java.io.IOException("credentials unavailable");return new String[]{"fixture","fixture"};}static class Prefs implements android.content.SharedPreferences,android.content.SharedPreferences.Editor {java.util.Map<String,Long> data=new java.util.concurrent.ConcurrentHashMap<>();public long getLong(String k,long d){return data.getOrDefault(k,d);}public Editor edit(){return this;}public Editor putLong(String k,long v){data.put(k,v);return this;}public void apply(){}}}''',
'com/donglan/chrona/ZjuHomeworkStore.java': """package com.donglan.chrona;class ZjuHomeworkStore {
static boolean enabled=true;static int applies;int source=1;static class State {boolean enabled;long generation=1;}
ZjuHomeworkStore(android.content.Context c){}ZjuHomeworkStore(android.content.Context c,int source){this.source=source;}
static volatile java.util.concurrent.CountDownLatch readEntered,readRelease;
State state(){
 if(source==2&&readEntered!=null){readEntered.countDown();try{if(!readRelease.await(4,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("read not released");}catch(InterruptedException e){throw new AssertionError(e);}}
 State s=new State();s.enabled=source==2?PtaSettingsStore.on:enabled;return s;}
java.util.Set<String> trackedCourses(String a,long g){return java.util.Set.of("tracked");}
static class Result {int added=1,updated,completed;java.util.List<com.donglan.chrona.zju.ZjuSnapshot.Homework> newHomework=java.util.List.of(new com.donglan.chrona.zju.ZjuSnapshot.Homework());java.util.List<Long> newHomeworkIds=java.util.List.of(123L);}
Result apply(String a,long g,java.util.List<com.donglan.chrona.zju.ZjuSnapshot.Homework> h){applies++;return new Result();}}""",
'com/donglan/chrona/ZjuTimetableStore.java': '''package com.donglan.chrona;class ZjuTimetableStore {static int saves;ZjuTimetableStore(android.content.Context c){}void save(String a,java.util.List<Object> l,java.util.List<String> t){saves++;}}''',
'com/donglan/chrona/ZjuHomeworkCalendar.java': '''package com.donglan.chrona;class ZjuHomeworkCalendar {static int pendingCount(android.content.Context c){return 0;}}''',
'com/donglan/chrona/AndroidSync.java': '''package com.donglan.chrona;class AndroidSync {static final Object LOCK=new Object();}''',
'com/donglan/chrona/AgendaWidgetProvider.java': '''package com.donglan.chrona;class AgendaWidgetProvider {static void requestRefresh(android.content.Context c){}}''',
'com/donglan/chrona/AgendaReminder.java': '''package com.donglan.chrona;class AgendaReminder {static void requestRefresh(android.content.Context c){}}''',
'com/donglan/chrona/HomeworkNotice.java': '''package com.donglan.chrona;class HomeworkNotice {static int shows;static void show(android.content.Context c,java.util.List<?> i,java.util.List<Long> ids){if(i.size()!=ids.size())throw new AssertionError("notice identities");shows++;}}''',
'com/donglan/chrona/zju/ZjuCalendarStore.java': '''package com.donglan.chrona.zju;public class ZjuCalendarStore {public static Object load(android.content.Context c){return new Object();}}''',
'com/donglan/chrona/zju/ZjuSnapshot.java': '''package com.donglan.chrona.zju;public class ZjuSnapshot {public static class Homework {public String key="pta:10",title="PTA test";}public java.util.List<Homework> homework=java.util.List.of();public java.util.List<Object> lessons=java.util.List.of();public java.util.List<String> warnings=java.util.List.of(),completeTimetableTerms=java.util.List.of("term");}''',
'com/donglan/chrona/zju/ZjuClient.java': '''package com.donglan.chrona.zju;public class ZjuClient implements AutoCloseable {public static final java.util.List<Integer> calls=new java.util.concurrent.CopyOnWriteArrayList<>();public static boolean failHomework,failTimetable;public static volatile java.util.concurrent.CountDownLatch entered,release;volatile boolean cancelled;public ZjuClient(String u,String p,Object c){}public ZjuSnapshot fetch(boolean h,boolean t,java.util.Set<String> tracked)throws java.io.IOException{if(h==t)throw new AssertionError("sources must fetch independently");if(h&&!tracked.contains("tracked"))throw new AssertionError();calls.add(h?1:2);if(entered!=null){entered.countDown();try{if(!release.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new AssertionError("latch timeout");}catch(InterruptedException e){throw new java.io.IOException(e);}}if(cancelled)throw new java.io.IOException("cancelled");if(h?failHomework:failTimetable)throw new java.io.IOException("domain failed");return new ZjuSnapshot();}public void cancel(){cancelled=true;}public void close(){}}''',
'com/donglan/chrona/ZjuSyncCadenceCheck.java': r'''package com.donglan.chrona;
import android.content.Context;import android.os.Handler;import com.donglan.chrona.zju.ZjuClient;import java.util.*;import java.util.concurrent.*;
public class ZjuSyncCadenceCheck {
 static int checks;static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
 static void scheduled()throws Exception{ZjuSync.SCHEDULER.submit(()->{}).get(2,TimeUnit.SECONDS);Handler.flush();}
 static void idle()throws Exception{long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(8);java.lang.reflect.Field field=ZjuSync.class.getDeclaredField("runningScopes");field.setAccessible(true);for(;;){synchronized(ZjuSync.class){if(field.getInt(null)==0)break;}if(System.nanoTime()>until)throw new AssertionError("worker timeout");Thread.sleep(10);}Handler.flush();}
 static void due(long homeworkAgo,long courseAgo){long now=System.currentTimeMillis();ZjuSettingsStore.PREFS.data.put("homework_attempt",now-homeworkAgo);ZjuSettingsStore.PREFS.data.put("timetable_attempt",now-courseAgo);}
 public static void main(String[] args)throws Exception{Context c=new Context();try{
 check(ZjuSync.HOMEWORK_INTERVAL==300000&&ZjuSync.TIMETABLE_INTERVAL==1800000,"5m homework / 30m timetable");
 long now=System.currentTimeMillis();ZjuSettingsStore.PREFS.data.clear();check(ZjuSync.nextDelay(c,now)==0,"new binding due immediately");
 due(120000,120000);long delay=ZjuSync.nextDelay(c,System.currentTimeMillis());check(delay>179000&&delay<=180000,"independent next homework due");
 ZjuHomeworkStore.enabled=false;delay=ZjuSync.nextDelay(c,System.currentTimeMillis());check(delay>1679000&&delay<=1680000,"disabled homework leaves course cadence");
 ZjuSettingsStore.table=false;check(ZjuSync.nextDelay(c,now)==-1,"both sources off cancels scheduling");ZjuHomeworkStore.enabled=true;ZjuSettingsStore.table=true;
 ZjuSettingsStore.on=false;ZjuSyncJobService.schedule(c);scheduled();check(android.app.job.JobScheduler.INSTANCE.latest==null,"service off cancels job");ZjuSettingsStore.on=true;
 due(120000,120000);ZjuSyncJobService.schedule(c);scheduled();var job=android.app.job.JobScheduler.INSTANCE.latest;check(job.delay>179000&&job.delay<=180000&&job.persisted&&job.network==1,"one-shot persisted connected job, not a clamped periodic job");
 due(301000,120000);List<String> delivered=new ArrayList<>();check(ZjuSync.requestDue(c,delivered::add),"due homework accepted");idle();check(ZjuClient.calls.equals(List.of(1))&&ZjuHomeworkStore.applies==1&&ZjuTimetableStore.saves==0,"homework-only poll never fetches/saves timetable");check(delivered.size()==1&&HomeworkNotice.shows==1,"callback and new homework delivered");
 due(120000,1801000);ZjuClient.calls.clear();ZjuSync.requestDue(c,null);idle();check(ZjuClient.calls.equals(List.of(2))&&ZjuHomeworkStore.applies==1&&ZjuTimetableStore.saves==1,"course-only poll never applies empty homework");
 due(301000,1801000);ZjuClient.calls.clear();ZjuClient.failHomework=true;ZjuSync.requestDue(c,null);idle();check(ZjuClient.calls.equals(List.of(1,2))&&ZjuHomeworkStore.applies==1&&ZjuTimetableStore.saves==2,"homework failure preserves its data while course succeeds");ZjuClient.failHomework=false;
 due(301000,1801000);ZjuClient.failTimetable=true;ZjuSync.requestDue(c,null);idle();check(ZjuHomeworkStore.applies==2&&ZjuTimetableStore.saves==2,"course failure preserves its data while homework succeeds");ZjuClient.failTimetable=false;
 due(301000,1801000);ZjuSettingsStore.credentialFailure=true;ZjuSync.requestDue(c,null);idle();check(ZjuSync.nextDelay(c,System.currentTimeMillis())>299000,"credential failures do not create zero-delay retry storms");ZjuSettingsStore.credentialFailure=false;
 due(301000,120000);ZjuClient.calls.clear();ZjuClient.entered=new CountDownLatch(1);ZjuClient.release=new CountDownLatch(1);ZjuSync.requestDue(c,null);check(ZjuClient.entered.await(2,TimeUnit.SECONDS),"homework worker entered");ZjuSync.request(c,delivered::add);check(delivered.size()==1,"full-refresh callback waits for queued course scope");ZjuClient.release.countDown();idle();check(ZjuClient.calls.equals(List.of(1,1,2))&&delivered.size()==2,"full manual request during homework poll retains missing course scope");ZjuClient.entered=null;
 due(301000,120000);ZjuClient.calls.clear();ZjuClient.entered=new CountDownLatch(1);ZjuClient.release=new CountDownLatch(1);ZjuSync.requestDue(c,null);check(ZjuClient.entered.await(2,TimeUnit.SECONDS),"second worker entered");ZjuSync.cancel();ZjuSync.request(c,null);ZjuClient.release.countDown();idle();check(ZjuClient.calls.equals(List.of(1,1,2)),"post-cancellation request is queued under a fresh epoch");ZjuClient.entered=null;
 int beforeWork=ZjuHomeworkStore.applies,beforeCourse=ZjuTimetableStore.saves;ZjuClient.calls.clear();List<String> manual=new ArrayList<>();due(120000,120000);check(ZjuSync.requestHomework(c,manual::add),"manual homework refresh bypasses cadence");idle();check(ZjuClient.calls.equals(List.of(1))&&ZjuHomeworkStore.applies==beforeWork+1&&ZjuTimetableStore.saves==beforeCourse&&manual.size()==1,"home refresh fetches only homework and returns callback");
 due(120000,120000);ZjuSync.foreground(c);scheduled();check(Handler.delayed==1,"foreground continues checking while screen remains open");ZjuSync.background();check(Handler.delayed==0,"background removes foreground timer");
 check(new ZjuSyncJobService().onStopJob(new android.app.job.JobParameters()),"system interruption requests reschedule");
 ZjuSettingsStore.on=false;ZjuSettingsStore.table=false;PtaSettingsStore.on=true;
 PtaSettingsStore.PREFS.data.clear();int schoolCalls=com.donglan.chrona.zju.ZjuClient.calls.size();
 check(ZjuSync.nextDelay(c,System.currentTimeMillis())==0,"PTA due with school disabled");
 java.util.concurrent.CountDownLatch ptaDone=new java.util.concurrent.CountDownLatch(1);
 ZjuSync.requestDue(c,value->ptaDone.countDown());
 for(int i=0;i<100&&ptaDone.getCount()>0;i++){Thread.sleep(10);android.os.Handler.flush();}
 check(ptaDone.getCount()==0&&PtaClient.calls==1,"PTA-only background request completes");
 check(com.donglan.chrona.zju.ZjuClient.calls.size()==schoolCalls,"PTA does not fetch disabled school");
 long ptaDelay=ZjuSync.nextDelay(c,System.currentTimeMillis());check(ptaDelay>290000&&ptaDelay<=300000,"PTA has independent five-minute clock");
 PtaSettingsStore.PREFS.expired=true;check(ZjuSync.nextDelay(c,System.currentTimeMillis())==-1,"expired PTA pauses automatic requests");
 PtaSettingsStore.PREFS.expired=false;
 // Hold a database read exactly where online PTA admission queries its binding.
 // The simulated main thread must process lifecycle and JobService callbacks before release.
 ZjuHomeworkStore.readEntered=new CountDownLatch(1);ZjuHomeworkStore.readRelease=new CountDownLatch(1);
 ZjuSyncJobService.schedule(c);
 check(ZjuHomeworkStore.readEntered.await(2,TimeUnit.SECONDS),"job scheduling reached blocked PTA state read");
 ExecutorService main=Executors.newSingleThreadExecutor();
 try {
   main.submit(()->{
     ZjuSync.foreground(c);
     var service=new ZjuSyncJobService();var params=new android.app.job.JobParameters();
     if(!service.onStartJob(params)||!service.onStopJob(params))throw new AssertionError("job callback contract");
     ZjuSync.background();
   }).get(1,TimeUnit.SECONDS);
   check(true,"startup/resume and job stop return while PTA state read is blocked");
 }finally{ZjuHomeworkStore.readRelease.countDown();main.shutdownNow();}
 scheduled();
 check(Handler.delayed==0,"old foreground check cannot rearm after background");
 PtaSettingsStore.on=false;
 System.out.println("PASS: "+checks+" production cadence/scope/queue/cancel/failure/lifecycle checks");
 }finally{ZjuSync.SCHEDULER.shutdownNow();ZjuSync.WORKER.shutdownNow();}}
}'''
}

files['com/donglan/chrona/PtaSettingsStore.java'] = """package com.donglan.chrona;class PtaSettingsStore {
static boolean on;static final Prefs PREFS=new Prefs();final Prefs prefs=PREFS;PtaSettingsStore(android.content.Context c){}
boolean enabled(){return on;}String account(){return "pta";}String session(){return "synthetic";}java.util.Set<String> excluded(){return java.util.Set.of();}
static class Prefs extends ZjuSettingsStore.Prefs {boolean expired;public boolean getBoolean(String k,boolean d){return k.equals("auth_expired")?expired:d;}public Editor putBoolean(String k,boolean value){if(k.equals("auth_expired"))expired=value;return this;}}
}"""
files['com/donglan/chrona/PtaClient.java'] = """package com.donglan.chrona;class PtaClient implements AutoCloseable {
static int calls;PtaClient(String cookie){}java.util.List<com.donglan.chrona.zju.ZjuSnapshot.Homework> fetch(PtaSettingsStore settings){calls++;return java.util.List.of(new com.donglan.chrona.zju.ZjuSnapshot.Homework());}
void cancel(){}public void close(){}static class AuthExpired extends java.io.IOException{}}
"""

for path, text in files.items():
    target = src / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')
production = root / 'app/src/main/java/com/donglan/chrona'
java = Path(os.environ['JAVA_HOME']) / 'bin'
sources = list(src.rglob('*.java')) + [production / 'ZjuSync.java', production / 'ZjuSyncJobService.java']
subprocess.run([str(java/'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', str(root/'build/ai-checks/json.jar'), '-d', str(out), *map(str,sources)], check=True)
subprocess.run([str(java/'java.exe'), '-cp', str(out)+os.pathsep+str(root/'build/ai-checks/json.jar'), 'com.donglan.chrona.ZjuSyncCadenceCheck'], check=True)
