"""Offline regression of real batch archive/store; generated local inputs, no school or AI calls."""
from pathlib import Path
import os
import shutil
import subprocess
from recurrence_dependencies import recurrence_java

root = Path(__file__).resolve().parents[1]
out = root / 'build/classroom-batch-export-check'
out.mkdir(parents=True, exist_ok=True)
stubs = {
    'android/content/Context.java': '''package android.content; public class Context {
private final java.io.File root;public Context(java.io.File root){this.root=root;}public java.io.File getNoBackupFilesDir(){return root;}}''',
    'android/util/AtomicFile.java': '''package android.util;import java.io.*;import java.nio.file.*;
public class AtomicFile {private final File file,stage;public AtomicFile(File f){file=f;stage=new File(f+".new");}
public byte[] readFully()throws IOException{return Files.readAllBytes(file.toPath());}public void delete(){file.delete();stage.delete();}
public FileOutputStream startWrite()throws IOException{return new FileOutputStream(stage);}
public void finishWrite(FileOutputStream s)throws IOException{s.close();Files.move(stage.toPath(),file.toPath(),StandardCopyOption.REPLACE_EXISTING);}
public void failWrite(FileOutputStream s){try{s.close();Files.deleteIfExists(stage.toPath());}catch(IOException ignored){}}}''',
    'com/donglan/chrona/classroom/ClassroomImages.java': '''package com.donglan.chrona.classroom;
public class ClassroomImages {public interface Progress {void update(ClassroomProgress p);}}''',
}
sources = []
for name, content in stubs.items():
    path = out / 'stubs' / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    sources.append(path)
src = root / 'app/src/main/java/com/donglan/chrona/classroom'
sources += [src / name for name in ['ClassroomStore.java', 'ClassroomExport.java', 'ClassroomLesson.java', 'ClassroomProgress.java']]
sources += [root / 'shared/src/main/java/com/donglan/chrona/classroom/ClassroomDocument.java', root / 'checks/ClassroomBatchExportCheck.java']
activity = (root / 'app/src/main/java/com/donglan/chrona/ClassroomLessonActivity.java').read_text(encoding='utf-8')
start = activity.index('    static String restoredFailure(')
end = activity.index('{', start) + 1
depth = 1
while depth:
    depth += (activity[end] == '{') - (activity[end] == '}')
    end += 1
restore = out / 'ClassroomFailureRestoreCheck.java'
restore.write_text('''package com.donglan.chrona;import org.json.*;
public class ClassroomFailureRestoreCheck {
METHOD
static void check(String actual,String expected){if(!actual.equals(expected))throw new AssertionError(actual+" != "+expected);}
public static void main(String[] args)throws Exception{
JSONObject summary=new JSONObject().put("run","same"),run=new JSONObject().put("run","same");
check(restoredFailure(summary,null),"");check(restoredFailure(summary,run),"");
for(String state:new String[]{"failed","cancelled"}){run.put("final_validation",new JSONObject().put("status",state).put("error","image-999.jpg"));check(restoredFailure(summary,run),"image-999.jpg");check(restoredFailure(null,run),"image-999.jpg");}
run.put("final",new JSONObject());check(restoredFailure(summary,run),"");run.remove("final");
summary.put("run","other");check(restoredFailure(summary,run),"");summary.put("run","same");
run.getJSONObject("final_validation").put("status","succeeded");check(restoredFailure(summary,run),"");
System.out.println("ClassroomFailureRestoreCheck PASS: 9 production recovery assertions");
}}'''.replace('METHOD', activity[start:end]), encoding='utf-8')
sources.append(restore)
page = (root / 'app/src/main/java/com/donglan/chrona/ClassroomSummaryPage.java').read_text(encoding='utf-8')
start = page.index('    static LinkedHashMap<String,String> historyCourseLabels(')
end = page.index('{', start) + 1
depth = 1
while depth:
    depth += (page[end] == '{') - (page[end] == '}')
    end += 1
course_labels = out / 'ClassroomHistoryCourseCheck.java'
course_labels.write_text('''package com.donglan.chrona;import org.json.*;import java.util.*;
public class ClassroomHistoryCourseCheck {
METHOD
static JSONObject row(String id,String name)throws Exception{return new JSONObject().put("lesson",new JSONObject().put("course_id",id).put("course",name));}
public static void main(String[] args)throws Exception{
var labels=historyCourseLabels(List.of(row("701","同名课程"),row("701","同名课程"),row("00000000000000000000702","同名课程"),row("703","独立课程")));
if(labels.size()!=4)throw new AssertionError("dedupe by ID only");
if(!labels.get("701").equals("同名课程（701）"))throw new AssertionError("first duplicate label");
if(!labels.get("00000000000000000000702").equals("同名课程（00000000000000000000702）"))throw new AssertionError("complete second ID, no truncation");
if(!labels.get("703").equals("独立课程"))throw new AssertionError("unique name stays unchanged");
if(!labels.keySet().iterator().next().isEmpty()||!labels.get("").equals("全部课程"))throw new AssertionError("all courses entry retained");
System.out.println("ClassroomHistoryCourseCheck PASS: 5 production label assertions, same-name distinct identity counterexample");
}}'''.replace('METHOD', page[start:end]), encoding='utf-8')
sources.append(course_labels)
jar = root / 'build/ai-checks/json.jar'
if not jar.is_file():
    raise RuntimeError('Existing build/ai-checks/json.jar required; nothing will be downloaded')
def tool(name):
    home = Path(os.environ.get('JAVA_HOME', '')) / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
    return str(home) if home.is_file() else shutil.which(name)
classes = out / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run(recurrence_java([tool('javac'), '--release', '17', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(classes), *map(str, sources)]), check=True)
subprocess.run(recurrence_java([tool('java'), '-Djava.awt.headless=true', '-cp', str(classes) + os.pathsep + str(jar), 'com.donglan.chrona.classroom.ClassroomBatchExportCheck', str(out / 'fixtures')]), check=True)
subprocess.run(recurrence_java([tool('java'), '-cp', str(classes) + os.pathsep + str(jar), 'com.donglan.chrona.ClassroomFailureRestoreCheck']), check=True)
subprocess.run(recurrence_java([tool('java'), '-cp', str(classes) + os.pathsep + str(jar), 'com.donglan.chrona.ClassroomHistoryCourseCheck']), check=True)
