"""Actual ReleaseUpdates with queued main-thread, preferences and network test doubles."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / "build/automatic-update-check"
out.mkdir(parents=True, exist_ok=True)
files = {
"android/os/Looper.java": '''package android.os;
public class Looper { public static final Thread MAIN=Thread.currentThread();
public static Looper getMainLooper(){return new Looper();} }''',
"android/os/Handler.java": '''package android.os;
import java.util.concurrent.ConcurrentLinkedQueue;
public class Handler {
public static final ConcurrentLinkedQueue<Runnable> delayed=new ConcurrentLinkedQueue<>(), ready=new ConcurrentLinkedQueue<>();
public Handler(Looper loop){} public boolean post(Runnable task){ready.add(task);return true;}
public boolean postDelayed(Runnable task,long delay){if(delay<1000)throw new AssertionError("no startup deferral");delayed.add(task);return true;}
public static void flush(){Runnable task;while((task=ready.poll())!=null)task.run();}
public static void advance(){Runnable task;while((task=delayed.poll())!=null)task.run();}
}''',
"android/os/Process.java": '''package android.os; public class Process {
public static final int THREAD_PRIORITY_BACKGROUND=10; public static void setThreadPriority(int p){} }''',
"android/content/SharedPreferences.java": '''package android.content;
public interface SharedPreferences {
boolean getBoolean(String k,boolean d);long getLong(String k,long d);String getString(String k,String d);
Editor edit(); interface Editor {Editor putLong(String k,long v);Editor putString(String k,String v);Editor remove(String k);boolean commit();}
}''',
"android/content/Context.java": '''package android.content;
import java.util.*;import java.util.concurrent.*;
public class Context {
public static final int MODE_PRIVATE=0;
public static final Map<String,Object> data=new ConcurrentHashMap<>();
public static void worker(){if(Thread.currentThread()==android.os.Looper.MAIN)throw new AssertionError("main thread IO");}
public Context getApplicationContext(){return this;}
public String getPackageName(){return "chrona.test";}
public android.content.pm.PackageManager getPackageManager(){return new android.content.pm.PackageManager();}
public SharedPreferences getSharedPreferences(String name,int mode){worker();return new Prefs();}
static class Prefs implements SharedPreferences {
public boolean getBoolean(String k,boolean d){worker();return (boolean)data.getOrDefault(k,d);}
public long getLong(String k,long d){worker();return (long)data.getOrDefault(k,d);}
public String getString(String k,String d){worker();return (String)data.getOrDefault(k,d);}
public Editor edit(){worker();return new Edit();}}
static class Edit implements SharedPreferences.Editor {
final Map<String,Object> writes=new HashMap<>();
public Edit putLong(String k,long v){writes.put(k,v);return this;}
public Edit putString(String k,String v){writes.put(k,v);return this;}
public Edit remove(String k){writes.put(k,null);return this;}
public boolean commit(){worker();writes.forEach((k,v)->{if(v==null)data.remove(k);else data.put(k,v);});return true;}}
}''',
"android/content/pm/PackageManager.java": '''package android.content.pm;
public class PackageManager {public static class NameNotFoundException extends Exception{}
public static class PackageInfo {public String versionName="1.0.0";}
public PackageInfo getPackageInfo(String p,int f)throws NameNotFoundException{android.content.Context.worker();return new PackageInfo();}}''',
"android/content/Intent.java": '''package android.content; public class Intent {public Intent(Context c,Class<?> t){}}''',
"android/app/Activity.java": '''package android.app; public class Activity extends android.content.Context {
public boolean destroyed,focus=true;public boolean isFinishing(){return false;}public boolean isDestroyed(){return destroyed;}
public boolean hasWindowFocus(){return focus;}public void startActivity(android.content.Intent i){} }''',
"com/donglan/chrona/GitHubRelease.java": '''package com.donglan.chrona;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;
class GitHubRelease {final String version="1.0.1";static AtomicInteger calls=new AtomicInteger();
static boolean fail;static CountDownLatch started,release;
static GitHubRelease fetch()throws Exception {android.content.Context.worker();calls.incrementAndGet();
if(started!=null)started.countDown();if(release!=null)release.await();if(fail)throw new Exception("offline");return new GitHubRelease();}
static boolean newer(String v,String current){return v.compareTo(current)>0;}}
''',
"com/donglan/chrona/UiStyle.java": '''package com.donglan.chrona;
class UiStyle {static int prompts;static android.app.Activity last;
static void confirmDialog(android.app.Activity a,String t,String b,String p,Runnable r){
if(Thread.currentThread()!=android.os.Looper.MAIN)throw new AssertionError("off-main dialog");prompts++;last=a;}}
''',
"com/donglan/chrona/UpdateActivity.java": '''package com.donglan.chrona;class UpdateActivity {}''',
"com/donglan/chrona/AutomaticUpdateCheck.java": '''package com.donglan.chrona;
import android.app.Activity;import android.content.Context;import android.os.*;import java.time.*;
import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class AutomaticUpdateCheck {
static AtomicBoolean scheduled;static ExecutorService reader;
static void check(boolean v,String s){if(!v)throw new AssertionError(s);}
static void drain()throws Exception {long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
while(scheduled.get()&&System.nanoTime()<until){Handler.flush();Thread.sleep(5);}Handler.flush();
check(!scheduled.get(),"request completed");reader.submit(()->{}).get(2,TimeUnit.SECONDS);}
static void launch(Activity a)throws Exception{ReleaseUpdates.checkAutomatically(a);Handler.advance();drain();}
static long at(String value){return Instant.parse(value).toEpochMilli();}
public static void main(String[] args)throws Exception{
Looper.getMainLooper();
var s=ReleaseUpdates.class.getDeclaredField("SCHEDULED");s.setAccessible(true);scheduled=(AtomicBoolean)s.get(null);
var r=ReleaseUpdates.class.getDeclaredField("READER");r.setAccessible(true);reader=(ExecutorService)r.get(null);
try {
ZoneId zone=ZoneId.of("Asia/Shanghai");
check(ReleaseCheckPolicy.due(at("2026-10-01T16:01:00Z"),at("2026-10-01T15:59:00Z"),zone),"local midnight");
check(!ReleaseCheckPolicy.due(at("2026-10-01T15:59:00Z"),at("2026-10-01T00:01:00Z"),zone),"same local day");
check(ReleaseCheckPolicy.due(at("2026-10-01T10:00:00Z"),0,zone),"first check");
check(ReleaseCheckPolicy.due(at("2026-10-01T10:00:00Z"),at("2026-10-02T10:00:00Z"),zone),"clock moved back");
Activity old=new Activity();for(int i=0;i<20;i++)ReleaseUpdates.checkAutomatically(old);
check(Handler.delayed.size()==1&&GitHubRelease.calls.get()==0,"deferred coalesced trigger");
GitHubRelease.started=new CountDownLatch(1);GitHubRelease.release=new CountDownLatch(1);
Handler.advance();check(GitHubRelease.started.await(2,TimeUnit.SECONDS),"background request starts");
old.destroyed=true;Activity fresh=new Activity();ReleaseUpdates.checkAutomatically(fresh);
GitHubRelease.release.countDown();drain();
check(GitHubRelease.calls.get()==1&&UiStyle.prompts==1&&UiStyle.last==fresh,"rotation delivers current owner once");
launch(fresh);check(GitHubRelease.calls.get()==1&&UiStyle.prompts==1,"same-day relaunch skips request and prompt");
Context.data.clear();GitHubRelease.fail=true;launch(fresh);launch(fresh);
check(GitHubRelease.calls.get()==2&&UiStyle.prompts==1,"failure silent and no same-day retry");
Context.data.clear();Context.data.put("automatic",false);launch(fresh);
check(GitHubRelease.calls.get()==2,"disabled skips IO request");
Context.data.clear();GitHubRelease.fail=false;fresh.focus=false;launch(fresh);
check(UiStyle.prompts==1&&Context.data.containsKey("pending_version"),"background retains notice");
fresh.focus=true;launch(fresh);check(UiStyle.prompts==2&&GitHubRelease.calls.get()==3,"resume delivers without fetching again");
System.out.println("Automatic update checks passed: background-only IO, deferral, single-flight, daily policy, failure, rotation and pending notice");
}finally{reader.shutdownNow();}}
}'''
}
sources = []
for name, value in files.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(value, encoding="utf-8")
    sources.append(str(path))
sources += [str(root / "app/src/main/java/com/donglan/chrona" / (name + ".java"))
            for name in ("ReleaseUpdates", "ReleaseCheckPolicy")]
java = Path(os.environ["JAVA_HOME"]) / "bin"
subprocess.run([str(java / "javac.exe"), "--release", "17", "-encoding", "UTF-8", "-d", str(out), *sources], check=True)
subprocess.run([str(java / "java.exe"), "-cp", str(out), "com.donglan.chrona.AutomaticUpdateCheck"], check=True)
