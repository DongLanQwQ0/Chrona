"""Compile the real ICS parser and geometry using dependencies resolved by Gradle.

Run assembleDebug once, then set JAVA_HOME and GRADLE_USER_HOME and run this file.
Uses the existing standalone-Java check workflow; no Android/launcher simulation.
"""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/timetable-check'
out.mkdir(parents=True, exist_ok=True)
cache = Path(os.environ['GRADLE_USER_HOME']) / 'caches/modules-2/files-2.1'
jars = []
for dependency, version in [('net.sf.biweekly/biweekly', '0.6.8'), ('com.github.mangstadt/vinnie', '2.0.2')]:
    found = list((cache / dependency / version).glob('*/*.jar'))
    if len(found) != 1:
        raise RuntimeError(f'Build the app first to resolve {dependency}:{version}')
    jars.extend(found)
classpath = os.pathsep.join(str(jar) for jar in jars)
java = Path(os.environ['JAVA_HOME']) / 'bin'
sources = list((root / 'app/src/main/java/com/donglan/chrona/timetable').glob('*.java'))
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', classpath,
                '-d', str(out), *(str(source) for source in sources), str(root / 'checks/TimetableCheck.java')], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out) + os.pathsep + classpath,
                'com.donglan.chrona.timetable.TimetableCheck'], check=True)
