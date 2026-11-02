"""Native Responses mapping, typed SSE and actual-client fixtures; no external I/O/downloads."""
from pathlib import Path
import os
import shutil
import subprocess
from recurrence_dependencies import recurrence_java

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/responses-protocol-check/classes'
out.mkdir(parents=True, exist_ok=True)
sources = [src / relative for relative in (
    'ai/AiSettings.java', 'ai/ResponsesProtocol.java', 'ai/ChatCompletionClient.java',
    'ai/ParseResult.java', 'ai/SchedulePrompt.java', 'ai/SemanticEventNormalizer.java',
    'ai/TimeAssessment.java', 'ai/RequestOptions.java', 'ai/StreamChunkContent.java',
    'data/EventTimeDefaults.java', 'data/CandidateRules.java', 'calendar/AllDayDates.java',
    'net/RequestControl.java', 'net/NetworkFailure.java')]
sources.append(root / 'checks/ResponsesProtocolCheck.java')
jar = root / 'build/ai-checks/json.jar'
if not jar.is_file():
    raise RuntimeError('Existing JSON jar required; nothing will be downloaded')

def executable(name):
    home = os.environ.get('JAVA_HOME')
    candidate = Path(home) / 'bin' / (name + ('.exe' if os.name == 'nt' else '')) if home else None
    found = str(candidate) if candidate and candidate.is_file() else shutil.which(name)
    if not found:
        raise RuntimeError(f'Existing {name} required')
    return found

subprocess.run(recurrence_java([executable('javac'), '--release', '17', '-encoding', 'UTF-8',
               '-cp', str(jar), '-d', str(out), *map(str, sources)]), check=True)
subprocess.run(recurrence_java([executable('java'), '-cp', str(out) + os.pathsep + str(jar),
               'com.donglan.chrona.ai.ResponsesProtocolCheck']), check=True)
