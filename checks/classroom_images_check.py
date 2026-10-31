"""Offline image cache regression; reuse the summary suite's Android adapters only."""
import ast
import os
from pathlib import Path
import subprocess
from recurrence_dependencies import recurrence_classpath
import sys

root = Path(__file__).resolve().parents[1]
out = root / 'build/classroom-images-check'
out.mkdir(parents=True, exist_ok=True)
temporary = out / 'tmp'
temporary.mkdir(parents=True, exist_ok=True)
tree = ast.parse((root / 'checks/classroom_summary_check.py').read_text(encoding='utf-8-sig'))
fixtures = next(ast.literal_eval(node.value) for node in tree.body if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'fixtures' for t in node.targets))
fixtures = {name: body for name, body in fixtures.items() if name.startswith('android/')}
fixtures['com/donglan/chrona/classroom/ClassroomClient.java'] = '''package com.donglan.chrona.classroom;
public class ClassroomClient {public byte[] bytes;public int downloads;public java.io.IOException failure;public byte[] image(String url)throws java.io.IOException{downloads++;if(failure!=null)throw failure;return bytes;}}'''
fixtures['com/donglan/chrona/zju/ZjuClient.java'] = '''package com.donglan.chrona.zju;
public class ZjuClient {public static class SchoolException extends java.io.IOException {}}'''
fixtures['com/donglan/chrona/classroom/ClassroomExport.java'] = '''package com.donglan.chrona.classroom;
public class ClassroomExport {public static String time(double n){return "fixture";}}'''
sources = []
for name, body in fixtures.items():
    path = out / 'stubs' / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(body, encoding='utf-8')
    sources.append(path)
src = root / 'app/src/main/java/com/donglan/chrona'
sources += [src / name for name in ['classroom/ClassroomImages.java', 'classroom/ClassroomImageQuality.java', 'classroom/ClassroomMaterialUpdates.java', 'classroom/ClassroomTranscript.java', 'classroom/ClassroomProgress.java', 'classroom/ClassroomStore.java', 'classroom/ClassroomLesson.java', 'net/RequestControl.java']]
sources.append(root / 'checks/ClassroomImagesCheck.java')
sources.append(root / 'shared/src/main/java/com/donglan/chrona/classroom/ClassroomDocument.java')
sources.append(root / 'shared/src/main/java/com/donglan/chrona/classroom/ClassroomMarkdownImages.java')
sources.append(src / 'net/NetworkFailure.java')
jar = root / 'build/ai-checks/json.jar'
classpath = recurrence_classpath()
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
subprocess.run([str(java / 'javac.exe'), f'-J-Djava.io.tmpdir={temporary}', '--release', '17', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(out), *map(str, sources)], check=True)
subprocess.run([str(java / 'java.exe'), f'-Djava.io.tmpdir={temporary}', '-Djava.awt.headless=true', '-cp', str(out) + os.pathsep + classpath, 'ClassroomImagesCheck', str(root / 'build/classroom-image-dedup'), *sys.argv[1:]], check=True)
