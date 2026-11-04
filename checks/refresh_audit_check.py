"""Deterministic clock/lifecycle checks against the production audit, without a device."""
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/refresh-audit-check'
out.mkdir(parents=True, exist_ok=True)
stubs = {
    'android/app/Activity.java': 'package android.app; public class Activity {}',
    'android/os/Bundle.java': 'package android.os; public class Bundle {}',
    'android/os/Looper.java': 'package android.os; public class Looper { public static Looper getMainLooper(){return new Looper();}}',
    'android/os/SystemClock.java': 'package android.os; public class SystemClock {public static long now; public static long uptimeMillis(){return now;}}',
    'android/os/Handler.java': '''package android.os; import java.util.*;
public class Handler { public static List<Runnable> pending=new ArrayList<>(); public Handler(Looper l){}
 public boolean postDelayed(Runnable r,long delay){pending.add(r);return true;}
 public void removeCallbacks(Runnable r){pending.removeIf(x->x==r);}
 public static void tick(){var copy=new ArrayList<>(pending);pending.clear();for(var r:copy)r.run();}}''',
    'android/app/Application.java': '''package android.app; import android.os.Bundle;
public class Application {public ActivityLifecycleCallbacks callbacks;
 public void registerActivityLifecycleCallbacks(ActivityLifecycleCallbacks c){callbacks=c;}
 public interface ActivityLifecycleCallbacks {
 default void onActivityPreCreated(Activity a,Bundle b){} default void onActivityPostCreated(Activity a,Bundle b){}
 default void onActivityPreResumed(Activity a){} default void onActivityPostResumed(Activity a){}
 void onActivityStarted(Activity a);void onActivityStopped(Activity a);void onActivityCreated(Activity a,Bundle b);
 void onActivityResumed(Activity a);void onActivityPaused(Activity a);void onActivitySaveInstanceState(Activity a,Bundle b);void onActivityDestroyed(Activity a);
 }}''',
    'com/donglan/chrona/debug/DiagLog.java': '''package com.donglan.chrona.debug; import java.util.*;
public class DiagLog {public static List<String> lines=new ArrayList<>(); public static void add(Object c,String s){
 if(!Thread.currentThread().getName().equals("chrona-refresh-audit"))throw new AssertionError("main-thread I/O"); lines.add(s);}}''',
    'com/donglan/chrona/debug/RefreshAuditCheck.java': '''package com.donglan.chrona.debug;
import android.app.*;import android.os.*;import java.util.concurrent.*;
public class RefreshAuditCheck {
 static int checks; static void check(boolean b){checks++;if(!b)throw new AssertionError("check "+checks);}
 public static void main(String[] args)throws Exception {
  Application app=new Application();RefreshAudit.install(app);Object original=app.callbacks;RefreshAudit.install(app);check(original==app.callbacks);
  var writerField=RefreshAudit.class.getDeclaredField("writer");writerField.setAccessible(true);
  ExecutorService writer=(ExecutorService)writerField.get(app.callbacks);
  try {
   check(!RefreshAudit.late(2999,1000));check(RefreshAudit.late(3000,1000));
   check(!RefreshAudit.late(6999,5000));check(RefreshAudit.late(7000,5000));
   check(!RefreshAudit.late(2499,500));check(RefreshAudit.late(2500,500));
   Activity a=new Activity();var audit=app.callbacks;
   audit.onActivityPreCreated(a,null);SystemClock.now=1200;audit.onActivityPostCreated(a,null);
   audit.onActivityStarted(a);audit.onActivityPreResumed(a);SystemClock.now=2300;audit.onActivityPostResumed(a);
   Handler.tick();writer.submit(()->{}).get();check(DiagLog.lines.size()==2);
   SystemClock.now=5300;Handler.tick();writer.submit(()->{}).get();check(DiagLog.lines.size()==3);
   check(DiagLog.lines.get(2).contains("main_loop_late")&&DiagLog.lines.get(2).contains("interval_ms=3000"));
   audit.onActivityStopped(a);check(Handler.pending.isEmpty());
   SystemClock.now=1000000;Handler.tick();audit.onActivityStarted(a);SystemClock.now+=1000;Handler.tick();
   writer.submit(()->{}).get();check(DiagLog.lines.size()==3);
   RefreshAudit.interval("dashboard_poll",-1,5000,SystemClock.now);RefreshAudit.end("fast",RefreshAudit.begin());
   RefreshAudit.interval("dashboard_poll",100,5000,7100);RefreshAudit.interval("dashboard_poll",100,500,2600);
   writer.submit(()->{}).get();check(DiagLog.lines.size()==5);
   check(DiagLog.lines.get(3).contains("expected_ms=5000"));check(DiagLog.lines.get(4).contains("expected_ms=500"));
   for(String line:DiagLog.lines)check(line.contains("at=")&&line.contains("uptime_ms="));
   audit.onActivityDestroyed(a);audit.onActivityStopped(a);check(Handler.pending.isEmpty());
   System.out.println("Refresh audit passed "+checks+" checks: thresholds, lifecycle reset, startup/resume, async logging, scheduled refresh.");
  }finally{writer.shutdownNow();}
 }}'''
}
sources = []
for name, content in stubs.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    sources.append(str(path))
sources.append(str(root / 'app/src/main/java/com/donglan/chrona/debug/RefreshAudit.java'))
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), *sources], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.debug.RefreshAuditCheck'], check=True)

dashboard = (root / 'app/src/main/java/com/donglan/chrona/DashboardActivity.java').read_text(encoding='utf-8')
assert 'RefreshAudit.interval("dashboard_poll", auditLastPoll,' in dashboard
assert 'auditPollExpected = 500L;' in dashboard
assert 'auditLastPoll = android.os.SystemClock.uptimeMillis();' in dashboard
assert 'RefreshAudit.end("dashboard_poll", auditBegan);' in dashboard
print('Dashboard audit integration present; real-device stall remains to reproduce.')
