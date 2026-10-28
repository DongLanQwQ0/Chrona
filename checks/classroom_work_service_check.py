"""Offline lifecycle execution of the real foreground service against small Android adapters."""
from pathlib import Path
import os
import shutil
import subprocess
import argparse
root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--output', type=Path, default=root / 'build/classroom-work-service-check')
out = parser.parse_args().output
stubs = {
'android/content/Context.java': '''package android.content; public class Context {
public boolean reject;public int starts; public Object startForegroundService(Intent i){starts++;if(reject)throw new IllegalStateException();return null;}
public <T>T getSystemService(Class<T> type){return type.cast(android.app.NotificationManager.INSTANCE);}}''',
'android/content/Intent.java': 'package android.content; public class Intent {public Intent(Context c,Class<?> type){}}',
'android/content/pm/ServiceInfo.java': 'package android.content.pm; public class ServiceInfo {public static final int FOREGROUND_SERVICE_TYPE_DATA_SYNC=1;}',
'android/os/Build.java': 'package android.os; public class Build {public static class VERSION {public static int SDK_INT=36;}}',
'android/os/IBinder.java': 'package android.os; public interface IBinder {}',
'android/app/Notification.java': 'package android.app; public class Notification {public String text;public Notification(){}public Notification(String t){text=t;}}',
'android/app/NotificationManager.java': '''package android.app; public class NotificationManager {
public static final NotificationManager INSTANCE=new NotificationManager();public Notification latest;public int posts;
public void notify(int id,Notification n){latest=n;posts++;}}''',
'android/os/Looper.java': 'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}',
'android/os/Handler.java': '''package android.os;public class Handler {
public static final java.util.ArrayList<Runnable> queue=new java.util.ArrayList<>();public Handler(Looper l){}
public boolean post(Runnable r){queue.add(r);return true;}public void removeCallbacksAndMessages(Object token){queue.clear();}
public static void drain(){while(!queue.isEmpty())queue.remove(0).run();}}''',
'android/app/Service.java': '''package android.app; public class Service extends android.content.Context {
public static final int START_NOT_STICKY=2,STOP_FOREGROUND_REMOVE=1;public boolean promoted,stopped,rejectPromotion;
public int onStartCommand(android.content.Intent i,int f,int s){return 0;}
public void startForeground(int id,Notification n,int type){if(rejectPromotion)throw new IllegalStateException();promoted=true;}
public void startForeground(int id,Notification n){startForeground(id,n,0);}
public void stopForeground(int flags){promoted=false;}public void stopSelf(){stopped=true;}
public void onDestroy(){}public void onTimeout(int startId,int type){}public android.os.IBinder onBind(android.content.Intent i){return null;}}''',
'com/donglan/chrona/classroom/ClassroomLesson.java': '''package com.donglan.chrona.classroom;
public class ClassroomLesson {public String courseId="course",id;public ClassroomLesson(String id){this.id=id;}}''',
'com/donglan/chrona/ClassroomLessonActivity.java': '''package com.donglan.chrona;
public class ClassroomLessonActivity {static class Session {
final android.content.Context app=new android.content.Context();final String account;
final com.donglan.chrona.classroom.ClassroomLesson selected;boolean failed,cancelled,monitorOwned,allowed=true;
ClassroomNotifications notifications;
Session(String a,String id){account=a;selected=new com.donglan.chrona.classroom.ClassroomLesson(id);}
boolean allowed(){return allowed;}void startFailed(){failed=true;}void cancel(){cancelled=true;}}}''',
'com/donglan/chrona/ClassroomAutoSummary.java': '''package com.donglan.chrona;
class ClassroomAutoSummary {static boolean foreground;static boolean hasForegroundOwner(){return foreground;}}''',
'com/donglan/chrona/ClassroomMonitorStore.java': '''package com.donglan.chrona;
class ClassroomMonitorStore {static final java.util.Set<String> stopped=new java.util.HashSet<>();
ClassroomMonitorStore(android.content.Context c,String a){}Object read(){return this;}
static Object ruleFor(Object state,com.donglan.chrona.classroom.ClassroomLesson lesson){return stopped.contains(lesson.id)?null:state;}}''',
'com/donglan/chrona/ClassroomNotifications.java': '''package com.donglan.chrona;
class ClassroomNotifications {static android.app.Notification foreground(android.content.Context c,int n){return new android.app.Notification();}}''',
}
stubs['com/donglan/chrona/ClassroomNotifications.java'] = '''package com.donglan.chrona;
class ClassroomNotifications {
android.app.Notification latest;int reposts;
void repost(){reposts++;ClassroomWorkService.updateProgress(this);}
static android.app.Notification foreground(android.content.Context c,int n){return new android.app.Notification("initial:"+n);}
static android.app.Notification foreground(android.content.Context c,java.util.List<ClassroomLessonActivity.Session> sessions){
StringBuilder text=new StringBuilder();for(var s:sessions){if(text.length()>0)text.append('|');text.append(s.selected.id).append(':').append(s.notifications==null?"waiting":s.notifications.latest.text);}
return new android.app.Notification(text.toString());}}'''
paths=[]
for name,text in stubs.items():
    p=out/'stubs'/name
    p.parent.mkdir(parents=True,exist_ok=True)
    p.write_text(text,encoding='utf-8')
    paths.append(str(p))
classes=out/'classes'
classes.mkdir(parents=True,exist_ok=True)
java_home=Path(os.environ.get('JAVA_HOME',''))/'bin'
def tool(name):
    local=java_home/(name+'.exe' if os.name=='nt' else name)
    return str(local) if local.is_file() else shutil.which(name)
subprocess.run([tool('javac'),'--release','17','-encoding','UTF-8','-d',str(classes),*paths,
    str(root/'app/src/main/java/com/donglan/chrona/ClassroomWorkService.java'),
    str(root/'checks/ClassroomWorkServiceCheck.java')],check=True)
subprocess.run([tool('java'),'-cp',str(classes),'com.donglan.chrona.ClassroomWorkServiceCheck'],check=True)
