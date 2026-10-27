"""Execute the production batch queue and foreground owner with local Android/session adapters."""
from pathlib import Path
import ast
import os
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/classroom-batch-work-check'
out.mkdir(parents=True, exist_ok=True)
tmp = out / 'tmp'
tmp.mkdir(exist_ok=True)
tree = ast.parse((root / 'checks/classroom_work_service_check.py').read_text(encoding='utf-8'))
stubs = ast.literal_eval(next(node.value for node in tree.body if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'stubs' for t in node.targets)))
stubs.update({
'android/content/SharedPreferences.java': '''package android.content;public class SharedPreferences {
public interface OnSharedPreferenceChangeListener{void onSharedPreferenceChanged(SharedPreferences p,String k);}
public final java.util.List<OnSharedPreferenceChangeListener> listeners=new java.util.ArrayList<>();
public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.add(l);}
public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l){listeners.remove(l);}
public void changed(){for(var l:new java.util.ArrayList<>(listeners))l.onSharedPreferenceChanged(this,"account");}}''',
'com/donglan/chrona/ZjuSettingsStore.java': '''package com.donglan.chrona;class ZjuSettingsStore {
static String current="account";static boolean enabled=true;static final android.content.SharedPreferences shared=new android.content.SharedPreferences();
final android.content.SharedPreferences prefs=shared;ZjuSettingsStore(android.content.Context c){}boolean enabled(){return enabled;}boolean configured(){return true;}String account(){return current;}}''',
'com/donglan/chrona/classroom/ClassroomLesson.java': '''package com.donglan.chrona.classroom;public class ClassroomLesson {
public final String courseId="1",id,course="Fixture",title="Class";public ClassroomLesson(String id){this.id=id;}public String date(){return "2026-10-27";}}''',
'com/donglan/chrona/classroom/ClassroomStore.java': '''package com.donglan.chrona.classroom;public class ClassroomStore {
public java.io.File localLesson(ClassroomLesson l)throws Exception{if(l.id.equals("bad-dir"))throw new java.io.IOException("bad directory");return new java.io.File("fixture-"+l.id);}
public static Object read(java.io.File f){return null;}}''',
'com/donglan/chrona/classroom/ClassroomSummary.java': '''package com.donglan.chrona.classroom;public class ClassroomSummary {
public static int calls;public static Object prepare(java.io.File f,ClassroomLesson l,Object c,Object control,java.util.function.Consumer<Object> p)throws Exception{
calls++;if(l.id.equals("fail"))throw new java.io.IOException("素材失败");return new Object();}}''',
'com/donglan/chrona/ClassroomLiveTranscripts.java': '''package com.donglan.chrona;class ClassroomLiveTranscripts {
static final java.util.Set<String> active=new java.util.HashSet<>();static boolean active(String a,com.donglan.chrona.classroom.ClassroomLesson l){return active.contains(l.id);}}''',
'com/donglan/chrona/ClassroomLessonActivity.java': '''package com.donglan.chrona;public class ClassroomLessonActivity {static class Session {
final android.content.Context app;final String account;com.donglan.chrona.classroom.ClassroomLesson selected;
boolean failed,cancelled,monitorOwned,busy,cancelRequested,succeeded,detached;Object batchOwner,prepared,materials,control;java.io.File dir;boolean localInitialized;
String message="";Runnable completed;Work pending;static Session active;Progress progress=new Progress();static class Progress{String stage="fixture";}
Session(String a,String id){this(new android.content.Context(),a);selected=new com.donglan.chrona.classroom.ClassroomLesson(id);}
Session(android.content.Context c,String a){app=c;account=a;}
boolean allowed(){return ZjuSettingsStore.enabled&&ZjuSettingsStore.current.equals(account);}void checkpoint(Object p){}Object connect(){return null;}
com.donglan.chrona.classroom.ClassroomStore store(){return new com.donglan.chrona.classroom.ClassroomStore();}
void cancel(){cancelled=true;cancelRequested=true;}void disposeIfDetached(){}
interface Work{void run()throws Exception;}
void run(String stage,boolean notices,Work work){busy=true;pending=work;ClassroomWorkService.submitBatch(batchOwner,this,()->active=this);}
void complete(){try{if(!cancelRequested){pending.run();succeeded=true;}}catch(Exception e){message=e.getMessage();}busy=false;ClassroomWorkService.finished(this);if(completed!=null)completed.run();}
void startFailed(){failed=true;busy=false;message="start failed";if(completed!=null)completed.run();}
}}''',
})
paths = []
for name, content in stubs.items():
    path = out / 'stubs' / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    paths.append(path)
classes = out / 'classes'
classes.mkdir(exist_ok=True)
def tool(name):
    home = Path(os.environ.get('JAVA_HOME', '')) / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
    return str(home) if home.is_file() else shutil.which(name)
paths += [root / 'app/src/main/java/com/donglan/chrona/ClassroomBatchWork.java', root / 'app/src/main/java/com/donglan/chrona/ClassroomWorkService.java', root / 'checks/ClassroomBatchWorkCheck.java']
subprocess.run([tool('javac'), f'-J-Djava.io.tmpdir={tmp}', '--release', '17', '-encoding', 'UTF-8', '-d', str(classes), *map(str, paths)], check=True)
subprocess.run([tool('java'), f'-Djava.io.tmpdir={tmp}', '-cp', str(classes), 'com.donglan.chrona.ClassroomBatchWorkCheck'], check=True)
