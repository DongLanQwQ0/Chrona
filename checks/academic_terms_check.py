"""Real semester/store logic, with minimal Android file-system doubles. Optional local ICS path stays local."""
from pathlib import Path
import os
import subprocess
import sys

root = Path(__file__).resolve().parents[1]
out = root / 'build/academic-terms-check'
out.mkdir(parents=True, exist_ok=True)
fixtures = {
    'android/content/Intent.java': '''package android.content;
public class Intent { public final Class<?> target; private final java.util.Map<String,String> extras=new java.util.HashMap<>();
public Intent(Context context,Class<?> target) { this.target=target; }
public Intent putExtra(String key,String value) { extras.put(key,value); return this; }
public String getStringExtra(String key) { return extras.get(key); } }''',
    'com/donglan/chrona/TimetableActivity.java': 'package com.donglan.chrona; public class TimetableActivity {}',
    'com/donglan/chrona/AgendaWidgetProvider.java': '''package com.donglan.chrona;
public class AgendaWidgetProvider { public static int refreshes;
public static void requestRefresh(android.content.Context context) { refreshes++; } }''',
    'android/content/Context.java': '''package android.content;
public class Context { private final java.io.File directory;
public Context(java.io.File directory) { this.directory=directory; }
public java.io.File getFilesDir() { return directory; } }''',
    'android/util/AtomicFile.java': '''package android.util;
import java.io.*;import java.nio.file.*;
public class AtomicFile {
public static boolean failNext; private final File base, pending;
public AtomicFile(File file) { base=file; pending=new File(file+".new"); }
public File getBaseFile() { return base; }
public InputStream openRead() throws IOException { return new FileInputStream(base); }
public FileOutputStream startWrite() throws IOException {
 if(failNext) { failNext=false; throw new IOException("Injected write failure"); }
 return new FileOutputStream(pending); }
public void finishWrite(FileOutputStream stream) throws IOException {
 stream.close(); Files.move(pending.toPath(),base.toPath(),StandardCopyOption.REPLACE_EXISTING); }
public void failWrite(FileOutputStream stream) { try { stream.close(); } catch(IOException ignored) {} pending.delete(); }
public void delete() { base.delete(); pending.delete(); }
}'''
}
stubs = []
for name, content in fixtures.items():
    target = out / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(content, encoding='utf-8')
    stubs.append(target)
cache = Path(os.environ['GRADLE_USER_HOME']) / 'caches/modules-2/files-2.1'
jars = [root / 'build/ai-checks/json.jar']
for dependency, version in [('net.sf.biweekly/biweekly', '0.6.8'), ('com.github.mangstadt/vinnie', '2.0.2')]:
    jars.extend((cache / dependency / version).glob('*/*.jar'))
if not all(path.is_file() for path in jars):
    raise RuntimeError('Use the existing JSON regression dependency and resolved Gradle cache')
classpath = os.pathsep.join(map(str, jars))
java = Path(os.environ['JAVA_HOME']) / 'bin'
sources = list((root / 'app/src/main/java/com/donglan/chrona/timetable').glob('*.java'))
sources += [root / 'app/src/main/java/com/donglan/chrona/TimetableStore.java',
            root / 'app/src/main/java/com/donglan/chrona/CourseAgenda.java',
            root / 'checks/CourseAgendaCheck.java',
            root / 'checks/AcademicTermsCheck.java', root / 'checks/TimetableLibraryCheck.java', *stubs]
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', classpath,
                '-d', str(out), *map(str, sources)], check=True)
run = [str(java / 'java.exe'), '-cp', str(out) + os.pathsep + classpath]
subprocess.run([*run, 'com.donglan.chrona.timetable.AcademicTermsCheck', *sys.argv[1:]], check=True)
subprocess.run([*run, 'com.donglan.chrona.TimetableLibraryCheck'], check=True)
subprocess.run([*run, 'com.donglan.chrona.CourseAgendaCheck'], check=True)
