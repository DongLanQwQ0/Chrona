"""Execute the real live-update scheduling and lifecycle with Android transport stand-ins."""
from pathlib import Path
import os, subprocess, xml.etree.ElementTree as ET
root=Path(__file__).resolve().parents[1];src=root/'app/src/main/java/com/donglan/chrona';out=root/'build/live-reminder-service-check'
a='{http://schemas.android.com/apk/res/android}';manifest=ET.parse(root/'app/src/main/AndroidManifest.xml')
service=next(n for n in manifest.iter('service') if n.get(a+'name')=='.AgendaReminderLiveService')
assert service.get(a+'foregroundServiceType')=='specialUse' and service.get(a+'exported')=='false'
assert any(n.get(a+'name')=='android.permission.FOREGROUND_SERVICE_SPECIAL_USE' for n in manifest.iter('uses-permission'))
files={
'com/donglan/chrona/HomeworkNotice.java': 'package com.donglan.chrona;class HomeworkNotice {static boolean waiting;static int wakes;static boolean awaitingScreen(android.content.Context c){return waiting;}static void screenOn(android.content.Context c,long at){wakes++;}}',
'android/content/Context.java':'''package android.content;public class Context {public static final int RECEIVER_NOT_EXPORTED=4;public int starts,stops,permission=android.content.pm.PackageManager.PERMISSION_GRANTED;public boolean deny;public android.app.NotificationManager manager=new android.app.NotificationManager();public int checkSelfPermission(String p){return permission;}public String getPackageName(){return "com.donglan.chrona";}public void startForegroundService(Intent i){if(deny)throw new IllegalStateException("blocked");starts++;}public boolean stopService(Intent i){stops++;return true;}public <T>T getSystemService(Class<T> c){return c.cast(manager);}public void registerReceiver(BroadcastReceiver r,IntentFilter f,int flags){}public void registerReceiver(BroadcastReceiver r,IntentFilter f){}public void unregisterReceiver(BroadcastReceiver r){}}''',
'android/content/Intent.java':'''package android.content;public class Intent {public static final String ACTION_SCREEN_ON="screen",ACTION_USER_PRESENT="present";long at;String action;public Intent(String action){this.action=action;}public String getAction(){return action;}public Intent(Context c,Class<?> k){}public Intent putExtra(String k,long v){at=v;return this;}public Intent putExtra(String k,String v){return this;}public long getLongExtra(String k,long d){return at==0?d:at;}}''',
'android/Manifest.java':'package android;public class Manifest {public static class permission {public static final String POST_NOTIFICATIONS="notification";}}',
'android/content/pm/PackageManager.java':'package android.content.pm;public class PackageManager {public static final int PERMISSION_GRANTED=0;}',
'android/provider/Settings.java':'package android.provider;public class Settings {public static final String ACTION_APP_NOTIFICATION_SETTINGS="app",ACTION_CHANNEL_NOTIFICATION_SETTINGS="channel",EXTRA_APP_PACKAGE="package",EXTRA_CHANNEL_ID="channel_id";}',
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
'android/app/NotificationChannel.java':'package android.app;public class NotificationChannel {public final int importance;public NotificationChannel(String a,String b,int i){importance=i;}public int getImportance(){return importance;}public void setSound(Object a,Object b){}public void enableVibration(boolean b){}public void setShowBadge(boolean b){}}',
'android/app/NotificationManager.java':'package android.app;public class NotificationManager {public static int IMPORTANCE_NONE=0,IMPORTANCE_MIN=1,IMPORTANCE_HIGH=4;public int creates;public boolean allowed=true;public NotificationChannel channel,homeworkChannel;public boolean areNotificationsEnabled(){return allowed;}public NotificationChannel getNotificationChannel(String id){if(id.equals("zju_homework_updates"))return homeworkChannel;if(!id.equals("upcoming_agenda"))throw new AssertionError(id);return channel;}public void createNotificationChannel(NotificationChannel c){creates++;}}',
'android/app/PendingIntent.java':'package android.app;public class PendingIntent {public static int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;public static PendingIntent getActivity(android.content.Context c,int id,android.content.Intent i,int f){return new PendingIntent();}}',
'com/donglan/chrona/AgendaReminder.java':'package com.donglan.chrona;class AgendaReminder {static final String CHANNEL="upcoming_agenda";static java.util.concurrent.Executor WORKER=Runnable::run;static int refreshes;static void requestRefresh(android.content.Context c){refreshes++;}}',
'com/donglan/chrona/AgendaReminderPreferences.java':'package com.donglan.chrona;class AgendaReminderPreferences {static boolean enabled=true;static boolean enabled(android.content.Context c){return enabled;}}',
'com/donglan/chrona/ReminderTimeline.java':'package com.donglan.chrona;class ReminderTimeline {static long PROGRESS_REFRESH=60000;}',
'com/donglan/chrona/AtomicCountdownUpdates.java':'package com.donglan.chrona;class AtomicCountdownUpdates {static int ticks;static long delay=1000;static void tick(android.content.Context c){ticks++;}static long nextDelay(android.content.Context c){return delay;}}',
'com/donglan/chrona/DashboardActivity.java':'package com.donglan.chrona;class DashboardActivity {}',
'com/donglan/chrona/R.java':'package com.donglan.chrona;class R {static class drawable {static int ic_notifications=1;}}',
'com/donglan/chrona/LiveReminderServiceCheck.java':'''package com.donglan.chrona;public class LiveReminderServiceCheck {
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}public static void main(String[]args)throws Exception{
android.content.Context c=new android.content.Context();
c.manager.allowed=false;AgendaReminderLiveService.update(c,true,0);check(c.starts==0&&c.stops==1,"app notifications blocked stops live updates");
c.manager.allowed=true;c.permission=-1;AgendaReminderLiveService.update(c,true,0);check(c.starts==0&&c.stops==2,"runtime notification permission required");
c.permission=android.content.pm.PackageManager.PERMISSION_GRANTED;c.manager.channel=new android.app.NotificationChannel("upcoming_agenda","reminders",0);AgendaReminderLiveService.update(c,true,0);check(c.starts==0&&c.stops==3,"disabled reminder channel blocks live updates");
c.manager.channel=null;AgendaReminderLiveService.update(c,true,System.currentTimeMillis()+10000);check(c.starts==1,"not-yet-created reminder channel can start for a real live card");
AgendaReminderLiveService s=new AgendaReminderLiveService();s.onCreate();int result=s.onStartCommand(new android.content.Intent(c,AgendaReminderLiveService.class).putExtra("next_at",System.currentTimeMillis()+5000),0,1);
check(s.foreground&&result==android.app.Service.START_STICKY,"foreground lifecycle");check(android.os.Handler.delay>0&&android.os.Handler.delay<=5000,"nearest boundary, not always one minute");
android.os.Handler.pending.run();check(AgendaReminder.refreshes==2,"background tick refreshes without Activity");
AgendaReminderLiveService.update(c,true,System.currentTimeMillis()+3600000);check(c.starts==1&&android.os.Handler.delay==1000,"existing service starts seconds boundary probe");
for(int frame=1;frame<=3;frame++){android.os.Handler.pending.run();check(AtomicCountdownUpdates.ticks==frame&&android.os.Handler.delay==1000,"seconds callback rearms at one second "+frame);}
AtomicCountdownUpdates.delay=60000;android.os.Handler.pending.run();check(android.os.Handler.delay==60000,"idle countdown can wait a minute");
var screenField=AgendaReminderLiveService.class.getDeclaredField("screen");screenField.setAccessible(true);
((android.content.BroadcastReceiver)screenField.get(s)).onReceive(s,new android.content.Intent("screen"));
check(android.os.Handler.delay==0,"waking display refreshes cached seconds immediately, not after minute probe");
AtomicCountdownUpdates.delay=1000;
int stops=c.stops;AgendaReminderLiveService.update(c,false,0);check(c.stops==stops+1,"no cards stops service");s.onDestroy();check(!s.foreground&&android.os.Handler.pending==null,"destroy releases callbacks and foreground notification");
c.deny=true;AgendaReminderLiveService.update(c,true,0);check(c.starts==1,"OS denial does not crash reminder path");
for(int blocked=0;blocked<3;blocked++){
AgendaReminderLiveService restarted=new AgendaReminderLiveService();
if(blocked==0)restarted.manager.allowed=false;
if(blocked==1)restarted.permission=-1;
if(blocked==2)restarted.manager.channel=new android.app.NotificationChannel("upcoming_agenda","reminders",0);
restarted.onCreate();int denied=restarted.onStartCommand(null,0,2);
check(restarted.stopped&&!restarted.foreground&&denied==android.app.Service.START_NOT_STICKY&&restarted.manager.creates==0,"blocked delivery cannot restart foreground updater "+blocked);restarted.onDestroy();
}
AgendaReminderPreferences.enabled=false;AgendaReminderLiveService disabled=new AgendaReminderLiveService();disabled.onCreate();disabled.onStartCommand(null,0,1);check(disabled.stopped&&!disabled.foreground,"disabled reminder cannot restart updater");disabled.onDestroy();
HomeworkNotice.waiting=true;c.deny=false;c.manager.channel=new android.app.NotificationChannel("upcoming_agenda","reminders",0);
int starts=c.starts;AgendaReminderLiveService.update(c,false,0);check(c.starts==starts+1,"pending homework starts observer independently of agenda channel");
AgendaReminderLiveService pending=new AgendaReminderLiveService();pending.manager.channel=c.manager.channel;pending.onCreate();check(pending.onStartCommand(null,0,3)==android.app.Service.START_STICKY&&pending.foreground,"pending homework keeps real screen observer alive");
((android.content.BroadcastReceiver)screenField.get(pending)).onReceive(pending,new android.content.Intent("screen"));check(HomeworkNotice.wakes==2,"screen event forwards first-screen timestamp");pending.onDestroy();
HomeworkNotice.waiting=false;AgendaReminderLiveService raced=new AgendaReminderLiveService();raced.manager.channel=new android.app.NotificationChannel("upcoming_agenda","reminders",0);raced.onCreate();check(raced.onStartCommand(new android.content.Intent(c,AgendaReminderLiveService.class),0,4)==android.app.Service.START_NOT_STICKY&&raced.stopped&&!raced.foreground&&raced.manager.creates==1,"wake/start race fulfils foreground promotion even when agenda channel is off");raced.onDestroy();
System.out.println("Live reminder service passed: notification/channel prerequisites, foreground lifetime, boundary wake, stop and OS denial");}}'''}
paths=[]
for name,value in files.items():
 p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(value,encoding='utf-8');paths.append(p)
paths.extend([src/'AgendaReminderLiveService.java',src/'NotificationDeliveryStatus.java']);j=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(j/'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out),*map(str,paths)],check=True)
subprocess.run([str(j/'java.exe'),'-cp',str(out),'com.donglan.chrona.LiveReminderServiceCheck'],check=True)
