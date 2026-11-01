"""Offline checks of regeneration preparation with existing JVM adapters; no device or AI."""
import ast
import os
from pathlib import Path
import shutil
import subprocess
from recurrence_dependencies import recurrence_java

root = Path(__file__).resolve().parents[1]
out = root / 'build/classroom-regeneration-check'
classes = out / 'classes'
classes.mkdir(parents=True, exist_ok=True)
source = ast.parse((root / 'checks/classroom_summary_check.py').read_text(encoding='utf-8'))
shared = {}
for node in source.body:
    if isinstance(node, ast.Assign):
        for target in node.targets:
            if isinstance(target, ast.Name) and target.id in ('fixtures', 'relative'):
                shared[target.id] = ast.literal_eval(node.value)
paths = []
for name, content in shared['fixtures'].items():
    path = out / 'stubs' / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    paths.append(path)
paths += [root / 'app/src/main/java/com/donglan/chrona' / name for name in shared['relative']]
paths += [root / 'shared/src/main/java/com/donglan/chrona/classroom' / name for name in ['ClassroomDocument.java', 'ClassroomMarkdownImages.java']]
paths += [root / 'app/src/main/java/com/donglan/chrona/net/NetworkFailure.java', root / 'checks/ClassroomRegenerationCheck.java']
jar = root / 'build/ai-checks/json.jar'
def tool(name):
    candidate = Path(os.environ.get('JAVA_HOME', '')) / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
    return str(candidate) if candidate.is_file() else shutil.which(name)
subprocess.run(recurrence_java([tool('javac'), '--release', '17', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(classes), *map(str, paths)]), check=True)
subprocess.run(recurrence_java([tool('java'), '-Djava.awt.headless=true', '-cp', str(classes) + os.pathsep + str(jar), 'com.donglan.chrona.classroom.ClassroomRegenerationCheck', str(out)]), check=True)
