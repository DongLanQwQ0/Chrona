"""Offline school-cache projection check; no Android runtime or Gradle needed."""
from pathlib import Path
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/zju-timetable-view-check'
out.mkdir(parents=True, exist_ok=True)
source = root / 'app/src/main/java/com/donglan/chrona'
javac = shutil.which('javac')
if not javac:
    raise RuntimeError('A Java 17+ compiler is required')
java = Path(javac).with_name('java.exe' if javac.endswith('.exe') else 'java')
subprocess.run([javac, '--release', '17', '-encoding', 'UTF-8', '-d', str(out),
                str(source / 'timetable/Timetable.java'), str(source / 'zju/ZjuSnapshot.java'),
                str(source / 'zju/ZjuTimetableView.java'), str(root / 'checks/ZjuTimetableViewCheck.java')], check=True)
subprocess.run([str(java), '-cp', str(out), 'ZjuTimetableViewCheck'], check=True)
