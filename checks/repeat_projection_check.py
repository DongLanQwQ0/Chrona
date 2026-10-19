"""Execute production recurrence projections against the established DST/exception fixtures."""
from pathlib import Path
import os
import subprocess
from recurrence_dependencies import recurrence_java
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/repeat-projection-check/classes'
OUT.mkdir(parents=True, exist_ok=True)
JAVA = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
subprocess.run(recurrence_java([str(JAVA / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(OUT), str(ROOT / 'checks/RepeatProjectionCheck.java')]), check=True)
subprocess.run(recurrence_java([str(JAVA / 'java.exe'), '-cp', str(OUT), 'RepeatProjectionCheck']), check=True)
