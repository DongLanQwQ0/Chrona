"""Real JVM classroom pipeline with synthetic responses over loopback only.

Shares the existing bitmap/AtomicFile/Context adapters and existing JAR/JDK.
Synthetic cached-token usage verifies persistence, not provider cache hits.
"""
import ast
import argparse
import os
from pathlib import Path
import shutil
import subprocess
from recurrence_dependencies import recurrence_java

root = Path(__file__).resolve().parents[1]
parser = argparse.ArgumentParser()
parser.add_argument('--case', choices=['edge-cases', 'validation-resume', 'markdown-fences', 'failure-detail', 'schema-repair'])
args = parser.parse_args()
source = ast.parse((root / 'checks/classroom_summary_check.py').read_text(encoding='utf-8'))
shared = {}
for node in source.body:
    if isinstance(node, ast.Assign):
        for target in node.targets:
            if isinstance(target, ast.Name) and target.id in ('fixtures', 'relative'):
                shared[target.id] = ast.literal_eval(node.value)
out = root / 'build/classroom-enhanced-check'
out.mkdir(parents=True, exist_ok=True)
paths = []
for name, content in shared['fixtures'].items():
    path = out / 'stubs' / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    paths.append(path)
paths += [root / 'app/src/main/java/com/donglan/chrona' / name for name in shared['relative']]
paths += [root / 'shared/src/main/java/com/donglan/chrona/classroom/ClassroomDocument.java',
          root / 'app/src/main/java/com/donglan/chrona/net/NetworkFailure.java',
          root / 'checks/ClassroomEnhancedCheck.java']
# Extract only the independent production error/restore methods; no Android UI or account writes.
def method(file, marker):
    text = file.read_text(encoding='utf-8')
    start = text.index(marker)
    opening = text.index('{', start)
    depth = 1
    end = opening + 1
    while depth:
        depth += (text[end] == '{') - (text[end] == '}')
        end += 1
    return text[start:end]

ui = root / 'app/src/main/java/com/donglan/chrona'
wrappers = {
    'ClassroomAutoSummary.java': 'import com.donglan.chrona.ai.ChatCompletionClient; import com.donglan.chrona.net.NetworkFailure; import com.donglan.chrona.classroom.ClassroomStore; import org.json.JSONObject; import java.io.File; public class ClassroomAutoSummary {'
        + method(ui / 'ClassroomAutoSummary.java', 'static String failureDetail(')
        + method(ui / 'ClassroomAutoSummary.java', 'static boolean enhancedMode(')
        + method(ui / 'ClassroomAutoSummary.java', 'private static void execution(') + '}',
    'ClassroomLessonActivity.java': 'import org.json.JSONObject; import java.io.File; public class ClassroomLessonActivity { public static class Session { public File dir; public int imageLimit=40; public Session(File file){dir=file;} }'
        + method(ui / 'ClassroomLessonActivity.java', 'static String restoredFailure(') + '}',
}
for name, body in wrappers.items():
    path = out / 'stubs/com/donglan/chrona' / name
    path.write_text('package com.donglan.chrona; ' + body, encoding='utf-8')
    paths.append(path)
assert 'message=ClassroomAutoSummary.failureDetail(' in (ui / 'ClassroomLessonActivity.java').read_text(encoding='utf-8')
assert 'execution(job,stopped?"interrupted":"failed",failureDetail(failure,stopped))' in (ui / 'ClassroomAutoSummary.java').read_text(encoding='utf-8')
assert 'job.enhancedClassroom=enhancedMode(prior,Boolean.TRUE.equals(job.enhancedClassroom))' in (ui / 'ClassroomAutoSummary.java').read_text(encoding='utf-8')
jar = root / 'build/ai-checks/json.jar'
if not jar.is_file():
    raise RuntimeError('Existing JSON jar required; nothing will be downloaded')

def executable(name):
    suffix = '.exe' if os.name == 'nt' else ''
    home = os.environ.get('JAVA_HOME')
    candidate = Path(home) / 'bin' / (name + suffix) if home else None
    found = str(candidate) if candidate and candidate.is_file() else shutil.which(name)
    if not found:
        raise RuntimeError(f'Existing {name} required')
    return found

classes = out / 'classes'
classes.mkdir(exist_ok=True)
subprocess.run(recurrence_java([executable('javac'), '--release', '17', '-encoding', 'UTF-8',
                '-cp', str(jar), '-d', str(classes), *map(str, paths)]), check=True)
subprocess.run(recurrence_java([executable('java'), '-Djava.awt.headless=true', '-cp',
                str(classes) + os.pathsep + str(jar), 'com.donglan.chrona.classroom.ClassroomEnhancedCheck',
                str(out / 'fixtures'), *([args.case] if args.case else [])]), check=True)
