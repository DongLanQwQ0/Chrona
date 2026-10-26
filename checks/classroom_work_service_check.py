"""Offline lifecycle execution of the real foreground service against small Android adapters."""
from pathlib import Path
import os
import shutil
import subprocess
root = Path(__file__).resolve().parents[1]
out = root / 'build/classroom-work-service-check'
stubs = {
'android/content/Context.java': '''package android.content; public class Context {
public boolean reject;public int starts; public Object startForegroundService(Intent i){starts++;if(reject)throw new IllegalStateException();return null;}
public <T>T getSystemService(Class<T> type){return null;}}''',
'android/content/Intent.java': 'package android.content; public class Intent {public Intent(Context c,Class<?> type){}}',
'android/content/pm/ServiceInfo.java': 'package android.content.pm; public class ServiceInfo {public static final int FOREGROUND_SERVICE_TYPE_DATA_SYNC=1;}',
'android/os/Build.java': 'package android.os; public class Build {public static class VERSION {public static int SDK_INT=36;}}',
'android/os/IBinder.java': 'package android.os; public interface IBinder {}',
'android/app/Notification.java': 'package android.app; public class Notification {}',
'android/app/NotificationManager.java': 'package android.app; public class NotificationManager {public void notify(int id,Notification n){}}',
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
