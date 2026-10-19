"""Exercise production recurrence AI schema and semantic normalization offline."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/donglan/chrona'
OUT = ROOT / 'build/repeat-ai-check'
OUT.mkdir(parents=True, exist_ok=True)
JAVA = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
JAR = ROOT / 'build/ai-checks/json.jar'
sources = [SRC / name for name in (
    'ai/SchedulePrompt.java', 'ai/SemanticEventNormalizer.java', 'data/EventCandidate.java',
    'data/EventCategory.java', 'data/EventTimeDefaults.java', 'data/RepeatRule.java', 'calendar/AllDayDates.java',
)]
sources += [ROOT / 'checks/RepeatAiCheck.java', ROOT / 'checks/SemanticEventNormalizerCheck.java',
            SRC / 'data/CandidateRules.java']
subprocess.run([str(JAVA / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', str(JAR),
                '-d', str(OUT), *map(str, sources)], check=True)
for check in ('RepeatAiCheck', 'SemanticEventNormalizerCheck'):
    subprocess.run([str(JAVA / 'java.exe'), '-cp', str(OUT) + os.pathsep + str(JAR), check], check=True)
