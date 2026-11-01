"""Run production read-only audit projection against synthetic metadata, without Android or AI."""
from pathlib import Path
import os
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/classroom-audit-check'
classes = out / 'classes'
classes.mkdir(parents=True, exist_ok=True)
temporary = out / 'tmp'
temporary.mkdir(exist_ok=True)
jar = root / 'build/ai-checks/json.jar'
if not jar.is_file():
    raise RuntimeError('Existing build/ai-checks/json.jar required; nothing will be downloaded')


def tool(name):
    home = Path(os.environ.get('JAVA_HOME', '')) / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
    return str(home) if home.is_file() else shutil.which(name)


source = root / 'app/src/main/java/com/donglan/chrona/classroom'
sources = [source / 'ClassroomAudit.java', source / 'ClassroomJsonFailure.java', source / 'ClassroomJsonObject.java', root / 'checks/ClassroomAuditCheck.java']
subprocess.run([tool('javac'), '-J-Djava.io.tmpdir=' + str(temporary), '--release', '17', '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(classes), *map(str, sources)], check=True)
subprocess.run([tool('java'), '-Djava.io.tmpdir=' + str(temporary), '-cp', str(classes) + os.pathsep + str(jar), 'com.donglan.chrona.classroom.ClassroomAuditCheck'], check=True)
