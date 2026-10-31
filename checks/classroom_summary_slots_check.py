"""Run the production process-wide AI gate and deadline/cancellation control offline."""
from pathlib import Path
import os
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/classroom-summary-slots-check'
tmp = out / 'tmp'
tmp.mkdir(parents=True, exist_ok=True)
classes = out / 'classes'
classes.mkdir(exist_ok=True)

def tool(name):
    home = Path(os.environ.get('JAVA_HOME', '')) / 'bin' / (name + ('.exe' if os.name == 'nt' else ''))
    return str(home) if home.is_file() else shutil.which(name)

sources = [root / 'app/src/main/java/com/donglan/chrona/ClassroomSummarySlots.java',
           root / 'app/src/main/java/com/donglan/chrona/net/RequestControl.java',
           root / 'checks/ClassroomSummarySlotsCheck.java']
subprocess.run([tool('javac'), f'-J-Djava.io.tmpdir={tmp}', '--release', '17', '-encoding', 'UTF-8',
                '-d', str(classes), *map(str, sources)], check=True)
subprocess.run([tool('java'), f'-Djava.io.tmpdir={tmp}', '-cp', str(classes),
                'com.donglan.chrona.ClassroomSummarySlotsCheck'], check=True)
