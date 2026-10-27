"""Compile production clients against cached dependencies; no Gradle or Android device required.

Pass --live ACCOUNT_FILE only for an explicitly authorized, read-only school check.
The Java probe prints counts/status only and never prints credentials, URLs or lesson text.
"""
import os
import subprocess
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[1]
out = root / 'build/classroom-client-check'
out.mkdir(parents=True, exist_ok=True)
cache = Path(os.environ.get('GRADLE_USER_HOME', 'F:/Android/GradleCache')) / 'caches/modules-2/files-2.1'
jars = [root / 'build/ai-checks/json.jar']
for module, version in [('com.squareup.okhttp3/okhttp', '4.12.0'), ('com.squareup.okio/okio-jvm', '3.6.0'), ('org.jetbrains.kotlin/kotlin-stdlib', '1.9.10')]:
    candidates = list((cache / module / version).glob('*/*.jar'))
    if len(candidates) != 1:
        raise RuntimeError('Required cached dependency unavailable: ' + module)
    jars.extend(candidates)
cp = os.pathsep.join(map(str, jars))
stub = out / 'android/text/Html.java'
stub.parent.mkdir(parents=True, exist_ok=True)
stub.write_text('''package android.text;
public final class Html {
 public static final int FROM_HTML_MODE_LEGACY=0;
 public static String fromHtml(String html,int flags) {
  return html.replaceAll("<[^>]*>", "").replace("&amp;", "&").replace("&quot;", "\\\"")
   .replace("&#39;", "'").replace("&lt;", "<").replace("&gt;", ">").replace("&nbsp;", " ");
 }
}''', encoding='utf-8')
src = root / 'app/src/main/java/com/donglan/chrona'
sources = [stub, root / 'checks/ClassroomClientCheck.java']
sources.append(src / 'net/NetworkFailure.java')
sources.append(root / 'checks/NetworkFailureCheck.java')
sources += [src / ('zju/' + name + '.java') for name in ['ZjuClient', 'ZjuExam', 'ZjuSnapshot', 'ZjuCalendarConfig', 'ZjuAcademicCalendar']]
sources += [src / ('classroom/' + name + '.java') for name in ['ClassroomClient', 'ClassroomLesson']]
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', cp, '-d', str(out), *map(str, sources)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out) + os.pathsep + cp, 'ClassroomClientCheck', *sys.argv[1:]], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out) + os.pathsep + cp, 'NetworkFailureCheck'], check=True)
