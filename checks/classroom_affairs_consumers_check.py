"""Offline production document/export regression; generated local fixtures only."""
from pathlib import Path
import os
import shutil
import subprocess
from recurrence_dependencies import recurrence_java

root = Path(__file__).resolve().parents[1]
out = root / 'build/course-affairs-consumers-check'
out.mkdir(parents=True, exist_ok=True)
stubs = {
    'android/content/Context.java': '''package android.content;public class Context {
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
shared = root / 'shared/src/main/java/com/donglan/chrona/classroom'
sources += [src / name for name in ['ClassroomStore.java', 'ClassroomExport.java', 'ClassroomLesson.java', 'ClassroomProgress.java', 'ClassroomAffairs.java']]
sources += [shared / name for name in ['ClassroomDocument.java', 'ClassroomMarkdownImages.java']]
sources.append(root / 'checks/ClassroomAffairsConsumersCheck.java')
jar = root / 'build/ai-checks/json.jar'
if not jar.is_file():
    raise RuntimeError('Existing build/ai-checks/json.jar required; nothing will be downloaded')

def tool(name):
    home = Path(os.environ.get('JAVA_HOME', '')) / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
    return str(home) if home.is_file() else shutil.which(name)

classes = out / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run(recurrence_java([tool('javac'), '--release', '17', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(classes), *map(str, sources)]), check=True)
subprocess.run(recurrence_java([tool('java'), '-cp', str(classes) + os.pathsep + str(jar), 'com.donglan.chrona.classroom.ClassroomAffairsConsumersCheck', str(out / 'fixtures')]), check=True)
