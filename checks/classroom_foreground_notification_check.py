"""Execute both production notification/foreground classes with deterministic Android adapters."""
from pathlib import Path
import argparse
import os
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=root / 'build/classroom-foreground-notification-check')
out = parser.parse_args().output
stubs = {
'android/Manifest.java': 'package android; public class Manifest {public static class permission {public static final String POST_NOTIFICATIONS="post";}}',
'android/R.java': 'package android; public class R {}',
'android/content/pm/PackageManager.java': 'package android.content.pm; public class PackageManager {public static final int PERMISSION_GRANTED=0;}',
'android/content/pm/ServiceInfo.java': 'package android.content.pm; public class ServiceInfo {public static final int FOREGROUND_SERVICE_TYPE_DATA_SYNC=1;}',
'android/content/Context.java': '''package android.content; public class Context {
public boolean denied,reject;public int starts;
public Context getApplicationContext(){return this;}public int checkSelfPermission(String p){return denied?-1:0;}
public <T>T getSystemService(Class<T> t){return t.cast(android.app.NotificationManager.INSTANCE);}
public Object startForegroundService(Intent i){starts++;if(reject)throw new IllegalStateException();return null;}}''',
'android/content/Intent.java': '''package android.content;public class Intent {public Class<?> target;public Object data;
public Intent(Context c,Class<?> t){target=t;}public Intent setData(Object d){data=d;return this;}public Intent putExtra(String k,String v){return this;}}''',
'android/content/SharedPreferences.java': '''package android.content;public interface SharedPreferences {
interface OnSharedPreferenceChangeListener {void onSharedPreferenceChanged(SharedPreferences p,String k);}
void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener listener);}''',
'android/net/Uri.java': '''package android.net;public class Uri {
public static class Builder {String text="";public Builder scheme(String s){text+=s;return this;}public Builder authority(String s){text+=":"+s;return this;}
public Builder appendPath(String s){text+="/"+s;return this;}public Uri build(){return new Uri();}}}''',
'android/os/Build.java': 'package android.os;public class Build {public static class VERSION {public static int SDK_INT=36;}}',
'android/os/IBinder.java': 'package android.os;public interface IBinder {}',
'android/os/SystemClock.java': 'package android.os;public class SystemClock {public static long now=10000;public static long elapsedRealtime(){return now;}}',
'android/os/Bundle.java': '''package android.os;public class Bundle extends java.util.HashMap<String,Object> {
public CharSequence getCharSequence(String k){return (CharSequence)get(k);}public int getInt(String k){return (Integer)getOrDefault(k,0);}}''',
'android/os/Looper.java': 'package android.os;public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java': '''package android.os;public class Handler {
public static final java.util.List<Runnable> queue=new java.util.ArrayList<>();public Handler(Looper l){}
public boolean post(Runnable r){queue.add(r);return true;}public void removeCallbacksAndMessages(Object o){queue.clear();}
public static void drain(){while(!queue.isEmpty())queue.remove(0).run();}}''',
'android/app/Activity.java': 'package android.app;public class Activity extends android.content.Context {public void requestPermissions(String[] p,int id){}}',
'android/app/PendingIntent.java': '''package android.app;public class PendingIntent {
public static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;public android.content.Intent intent;
public static PendingIntent getActivity(android.content.Context c,int id,android.content.Intent i,int f){var p=new PendingIntent();p.intent=i;return p;}}''',
'android/app/NotificationChannel.java': 'package android.app;public class NotificationChannel {public NotificationChannel(String id,String title,int importance){}}',
'android/service/notification/StatusBarNotification.java': '''package android.service.notification;public class StatusBarNotification {
String tag;int id;public StatusBarNotification(String t,int i){tag=t;id=i;}public String getTag(){return tag;}public int getId(){return id;}}''',
'android/app/NotificationManager.java': '''package android.app;public class NotificationManager {
public static final int IMPORTANCE_LOW=1,IMPORTANCE_DEFAULT=3;public static final NotificationManager INSTANCE=new NotificationManager();
public final java.util.Map<String,Notification> notices=new java.util.LinkedHashMap<>();public int posts;public boolean enabled=true;
public void createNotificationChannel(NotificationChannel c){}public boolean areNotificationsEnabled(){return enabled;}
public void notify(int id,Notification n){notify(null,id,n);}public void notify(String t,int id,Notification n){posts++;if(enabled)notices.put(t+"@"+id,n);}
public void cancel(String t,int id){notices.remove(t+"@"+id);}
public android.service.notification.StatusBarNotification[] getActiveNotifications(){return notices.keySet().stream().map(k->{int at=k.lastIndexOf('@');String t=k.substring(0,at);return new android.service.notification.StatusBarNotification(t.equals("null")?null:t,Integer.parseInt(k.substring(at+1)));}).toArray(android.service.notification.StatusBarNotification[]::new);}}''',
'android/app/Notification.java': '''package android.app;public class Notification {
public static final int VISIBILITY_PRIVATE=0;public static final String CATEGORY_PROGRESS="progress",EXTRA_TEXT="text",EXTRA_TITLE="title",EXTRA_BIG_TEXT="big",EXTRA_PROGRESS="done",EXTRA_PROGRESS_MAX="total";
public android.os.Bundle extras=new android.os.Bundle();public PendingIntent contentIntent;public boolean ongoing,autoCancel;public String channel;public int visibility;
public static class BigTextStyle {CharSequence text;public BigTextStyle bigText(CharSequence t){text=t;return this;}}
public static class Builder {Notification n=new Notification();public Builder(android.content.Context c,String channel){n.channel=channel;}
public static Builder recoverBuilder(android.content.Context c,Notification n){Builder b=new Builder(c,n.channel);b.n=n;return b;}
public Builder setSmallIcon(int i){return this;}public Builder setContentTitle(String s){n.extras.put(EXTRA_TITLE,s);return this;}
public Builder setContentText(String s){n.extras.put(EXTRA_TEXT,s);return this;}public Builder setStyle(BigTextStyle s){n.extras.put(EXTRA_BIG_TEXT,s.text);return this;}
public Builder setVisibility(int v){n.visibility=v;return this;}public Builder setOngoing(boolean v){n.ongoing=v;return this;}
public Builder setOnlyAlertOnce(boolean v){return this;}public Builder setContentIntent(PendingIntent p){n.contentIntent=p;return this;}
public Builder setCategory(String c){return this;}public Builder setAutoCancel(boolean v){n.autoCancel=v;return this;}
public Builder setProgress(int total,int done,boolean indeterminate){n.extras.put(EXTRA_PROGRESS,done);n.extras.put(EXTRA_PROGRESS_MAX,total);return this;}
public Notification build(){return n;}}}''',
'android/app/Service.java': '''package android.app;public class Service extends android.content.Context {
public static final int START_NOT_STICKY=2,STOP_FOREGROUND_REMOVE=1;public boolean promoted,stopped,rejectPromotion;public int promotions;
public int onStartCommand(android.content.Intent i,int f,int s){return 0;}
public void startForeground(int id,Notification n,int type){promotions++;if(rejectPromotion)throw new IllegalStateException();promoted=true;getSystemService(NotificationManager.class).notify(id,n);}
public void startForeground(int id,Notification n){startForeground(id,n,0);}public void stopForeground(int flags){promoted=false;getSystemService(NotificationManager.class).cancel(null,2043);}
public void stopSelf(){stopped=true;}public void onDestroy(){}public void onTimeout(int id,int type){}public android.os.IBinder onBind(android.content.Intent i){return null;}}''',
'org/json/JSONException.java': 'package org.json;public class JSONException extends Exception {}',
'org/json/JSONObject.java': 'package org.json;public class JSONObject {}',
'com/donglan/chrona/R.java': 'package com.donglan.chrona;class R {static class drawable {static int ic_chrona_foreground=1;}}',
'com/donglan/chrona/ClassroomSummaryActivity.java': 'package com.donglan.chrona;class ClassroomSummaryActivity {}',
'com/donglan/chrona/ZjuSettingsStore.java': '''package com.donglan.chrona;class ZjuSettingsStore {
static boolean enabled=true;static String account="account";static final android.content.SharedPreferences prefsValue=l->{};
final android.content.SharedPreferences prefs=prefsValue;ZjuSettingsStore(android.content.Context c){}boolean enabled(){return enabled;}boolean configured(){return true;}String account(){return account;}}''',
'com/donglan/chrona/classroom/ClassroomLesson.java': '''package com.donglan.chrona.classroom;public class ClassroomLesson {
public static final java.time.ZoneId ZONE=java.time.ZoneId.of("Asia/Shanghai");
public final String courseId="course",id,course,title;public final long startsAt;
public ClassroomLesson(String id){this(id,"合成课程",id,0);}
public ClassroomLesson(String id,String course,String title,long startsAt){this.id=id;this.course=course;this.title=title;this.startsAt=startsAt;}
public org.json.JSONObject json()throws org.json.JSONException{return new org.json.JSONObject();}}''',
'com/donglan/chrona/ClassroomLessonActivity.java': '''package com.donglan.chrona;class ClassroomLessonActivity {
static final String EXTRA_ACCOUNT="account",EXTRA_LESSON="lesson";static class Session {
final android.content.Context app=new android.content.Context();final String account="account";final com.donglan.chrona.classroom.ClassroomLesson selected;
ClassroomNotifications notifications;boolean failed,cancelled,monitorOwned;Session(String id){selected=new com.donglan.chrona.classroom.ClassroomLesson(id);notifications=new ClassroomNotifications(app,account,selected);}
boolean allowed(){return ZjuSettingsStore.enabled&&account.equals(ZjuSettingsStore.account);}void startFailed(){failed=true;}void cancel(){cancelled=true;}}}''',
'com/donglan/chrona/ClassroomAutoSummary.java': 'package com.donglan.chrona;class ClassroomAutoSummary {static boolean hasForegroundOwner(){return true;}}',
'com/donglan/chrona/ClassroomMonitorStore.java': '''package com.donglan.chrona;class ClassroomMonitorStore {
ClassroomMonitorStore(android.content.Context c,String a){}Object read(){return this;}static Object ruleFor(Object s,com.donglan.chrona.classroom.ClassroomLesson l){return s;}}''',
}
paths=[]
for name,text in stubs.items():
    path=out/'stubs'/name
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(text,encoding='utf-8')
    paths.append(str(path))
classes=out/'classes'
classes.mkdir(parents=True,exist_ok=True)
def tool(name):
    local=Path(os.environ.get('JAVA_HOME',''))/'bin'/(name+'.exe' if os.name=='nt' else name)
    return str(local) if local.is_file() else shutil.which(name)
sources=[root/'app/src/main/java/com/donglan/chrona'/name for name in ['ClassroomWorkService.java','ClassroomNotifications.java','classroom/ClassroomProgress.java']]
subprocess.run([tool('javac'),'--release','17','-encoding','UTF-8','-d',str(classes),*paths,*map(str,sources),str(root/'checks/ClassroomForegroundNotificationCheck.java')],check=True)
subprocess.run([tool('java'),'-cp',str(classes),'com.donglan.chrona.ClassroomForegroundNotificationCheck'],check=True)
