"""Execute the real live-update scheduling and lifecycle with Android transport stand-ins."""
from pathlib import Path
import os, subprocess, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1];src=root/'app/src/main/java/com/donglan/chrona';out=root/'build/live-reminder-service-check'
a='{http://schemas.android.com/apk/res/android}';manifest=ET.parse(root/'app/src/main/AndroidManifest.xml')
service=next(n for n in manifest.iter('service') if n.get(a+'name')=='.AgendaReminderLiveService')
assert service.get(a+'foregroundServiceType')=='specialUse' and service.get(a+'exported')=='false'
assert any(n.get(a+'name')=='android.permission.FOREGROUND_SERVICE_SPECIAL_USE' for n in manifest.iter('uses-permission'))
files={
'android/content/Context.java':'''package android.content;public class Context {public static final int RECEIVER_NOT_EXPORTED=4;public int starts,stops;public boolean deny;public void startForegroundService(Intent i){if(deny)throw new IllegalStateException("blocked");starts++;}public boolean stopService(Intent i){stops++;return true;}public <T>T getSystemService(Class<T> c){return c.cast(new android.app.NotificationManager());}public void registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){}public void registerReceiver(BroadcastReceiver r,IntentFilter f){}public void unregisterReceiver(BroadcastReceiver r){}}''',
'android/content/Intent.java':'''package android.content;public class Intent {public static final String ACTION_SCREEN_ON="screen",ACTION_USER_PRESENT="present";long at;public Intent(Context c,Class<?> k){}public Intent putExtra(String k,long v){at=v;return this;}public long getLongExtra(String k,long d){return at==0?d:at;}}''',
'android/content/IntentFilter.java':'package android.content;public class IntentFilter {public IntentFilter(String s){}public void addAction(String s){}}',
'android/content/BroadcastReceiver.java':'package android.content;public abstract class BroadcastReceiver {public abstract void onReceive(Context c,Intent i);}',
'android/os/IBinder.java':'package android.os;public interface IBinder {}',
'android/os/Build.java':'package android.os;public class Build {public static class VERSION {public static int SDK_INT=36;}}',
'android/os/Looper.java':'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java':'''package android.os;public class Handler {public static long delay;public static Runnable pending;public Handler(Looper l){}public void post(Runnable r){r.run();}public void postDelayed(Runnable r,long d){pending=r;delay=d;}public void removeCallbacks(Runnable r){if(pending==r)pending=null;}public void removeCallbacksAndMessages(Object o){pending=null;}}''',
'android/util/Log.java':'package android.util;public class Log {public static int w(String a,String b,Throwable c){return 0;}public static int i(String a,String b){return 0;}}',
'android/content/pm/ServiceInfo.java':'package android.content.pm;public class ServiceInfo {public static final int FOREGROUND_SERVICE_TYPE_SPECIAL_USE=16384;}',
'android/app/Service.java':'''package android.app;public class Service extends android.content.Context {public static int START_STICKY=1,START_NOT_STICKY=2,STOP_FOREGROUND_REMOVE=1;public boolean foreground,stopped;public void onCreate(){}public int onStartCommand(android.content.Intent i,int f,int id){return 0;}public void onDestroy(){}public android.os.IBinder onBind(android.content.Intent i){return null;}public void startForeground(int id,Notification n){foreground=true;}public void startForeground(int id,Notification n,int t){foreground=true;}public void stopForeground(int flags){foreground=false;}public void stopSelf(){stopped=true;}}''',
'android/app/Notification.java':'''package android.app;public class Notification {public static class Builder {public Builder(android.content.Context c,String s){}public Builder setSmallIcon(int i){return this;}public Builder setContentTitle(String s){return this;}public Builder setContentText(String s){return this;}public Builder setContentIntent(PendingIntent p){return this;}public Builder setOngoing(boolean b){return this;}public Builder setOnlyAlertOnce(boolean b){return this;}public Builder setShowWhen(boolean b){return this;}public Notification build(){return new Notification();}}}''',
'android/app/NotificationChannel.java':'package android.app;public class NotificationChannel {public NotificationChannel(String a,String b,int i){}public void setSound(Object a,Object b){}public void enableVibration(boolean b){}public void setShowBadge(boolean b){}}',
'android/app/NotificationManager.java':'package android.app;public class NotificationManager {public static int IMPORTANCE_MIN=1;public void createNotificationChannel(NotificationChannel c){}}',
'android/app/PendingIntent.java':'package android.app;public class PendingIntent {public static int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;public static PendingIntent getActivity(android.content.Context c,int id,android.content.Intent i,int f){return new PendingIntent();}}',
'com/donglan/chrona/AgendaReminder.java':'package com.donglan.chrona;class AgendaReminder {static java.util.concurrent.Executor WORKER=Runnable::run;static int refreshes;static void requestRefresh(android.content.Context c){refreshes++;}}',
'com/donglan/chrona/AgendaReminderPreferences.java':'package com.donglan.chrona;class AgendaReminderPreferences {static boolean enabled=true;static boolean enabled(android.content.Context c){return enabled;}}',
'com/donglan/chrona/ReminderTimeline.java':'package com.donglan.chrona;class ReminderTimeline {static long PROGRESS_REFRESH=60000;}',
'com/donglan/chrona/AtomicCountdownUpdates.java':'package com.donglan.chrona;class AtomicCountdownUpdates {static void tick(android.content.Context c){}static long nextDelay(android.content.Context c){return 1000;}}',
'com/donglan/chrona/DashboardActivity.java':'package com.donglan.chrona;class DashboardActivity {}',
'com/donglan/chrona/R.java':'package com.donglan.chrona;class R {static class drawable {static int ic_notifications=1;}}',
'com/donglan/chrona/LiveReminderServiceCheck.java':'''package com.donglan.chrona;public class LiveReminderServiceCheck {
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}public static void main(String[]args){
android.content.Context c=new android.content.Context();AgendaReminderLiveService.update(c,true,System.currentTimeMillis()+10000);check(c.starts==1,"starts only for live cards");
AgendaReminderLiveService s=new AgendaReminderLiveService();s.onCreate();int result=s.onStartCommand(new android.content.Intent(c,AgendaReminderLiveService.class).putExtra("next_at",System.currentTimeMillis()+5000),0,1);
check(s.foreground&&result==android.app.Service.START_STICKY,"foreground lifecycle");check(android.os.Handler.delay>0&&android.os.Handler.delay<=5000,"nearest boundary, not always one minute");
android.os.Handler.pending.run();check(AgendaReminder.refreshes==2,"background tick refreshes without Activity");
AgendaReminderLiveService.update(c,true,System.currentTimeMillis()+3600000);check(c.starts==1&&android.os.Handler.delay==1000,"existing service starts seconds boundary probe");
AgendaReminderLiveService.update(c,false,0);check(c.stops==1,"no cards stops service");s.onDestroy();check(!s.foreground&&android.os.Handler.pending==null,"destroy releases callbacks and foreground notification");
c.deny=true;AgendaReminderLiveService.update(c,true,0);check(c.starts==1,"OS denial does not crash reminder path");
AgendaReminderPreferences.enabled=false;AgendaReminderLiveService disabled=new AgendaReminderLiveService();disabled.onCreate();disabled.onStartCommand(null,0,1);check(disabled.stopped&&!disabled.foreground,"disabled reminder cannot restart updater");disabled.onDestroy();
System.out.println("Live reminder service passed: foreground lifetime, minute updates, boundary wake, stop and OS denial");}}'''}
paths=[]
for name,value in files.items():
 p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value,encoding='utf-8');paths.append(p)
paths.append(src/'AgendaReminderLiveService.java');j=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(j/'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out),*map(str,paths)],check=True)
subprocess.run([str(j/'java.exe'),'-cp',str(out),'com.donglan.chrona.LiveReminderServiceCheck'],check=True)
