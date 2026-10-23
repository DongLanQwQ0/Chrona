"""Execute the production accessibility observer and marker codec with Android API doubles.

No device operations; native event delivery/RemoteViews transport remain device acceptance.
"""
import os
from pathlib import Path
import re
import subprocess

root = Path(__file__).resolve().parents[1]
source_dir = root / "app/src/main/java/com/donglan/chrona"
production = []
for name in ("AtomicCapsuleAccessibilityService", "AtomicCapsuleIdentity"):
    text = (source_dir / f"{name}.java").read_text(encoding="utf-8")
    text = re.sub(r"^package .*;\s*|^import .*;\s*", "", text, flags=re.M)
    text = text.replace(f"public final class {name}", f"final class {name}")
    text = text.replace("android.view.accessibility.AccessibilityWindowInfo", "AccessibilityWindowInfo")
    text = text.replace("com.donglan.chrona.debug.DiagLog", "DiagLog")
    production.append(text)
fixture = (root / "checks/AtomicCapsuleObserverCheck.java").read_text(encoding="utf-8")
out = root / "build/atomic-observer-check"
out.mkdir(parents=True, exist_ok=True)
file = out / "AtomicCapsuleObserverCheck.java"
file.write_text(fixture.replace("// PRODUCTION_SOURCES", "\n".join(production)), encoding="utf-8")
java = Path(os.environ["JAVA_HOME"]) / "bin"
subprocess.run([str(java / "javac.exe"), "--release", "17", "-encoding", "UTF-8", "-d", str(out), str(file)], check=True)
subprocess.run([str(java / "java.exe"), "-cp", str(out), "AtomicCapsuleObserverCheck"], check=True)

# The temporary observer must reject communication sessions before obtaining UI automation.
probe = (root / "app/src/androidTest/java/com/donglan/chrona/AtomicEventObservationCheck.java").read_text(encoding="utf-8")
assert probe.index("requireIdleAudio(context)") < probe.index("getUiAutomation(")
loop = probe.index("for (int second = 0;")
assert probe.index("requireIdleAudio(context)", loop) < probe.index("AtomicCountdownUpdates.tick(context)", loop)
assert "AudioManager.MODE_NORMAL" in probe
assert "Phone call/communication/ringing active" in probe
assert "if (productionSeconds && !automaticSeconds)" in probe
assert "Automatic seconds probe requires an existing live homework notice" in probe
print("Probe communication precondition: PASS (wiring, not live phone state)")

# A temporary notice must never replace the foreground live-updates notification.
probe_id = int(re.search(r"private static final int ID = (0x[0-9a-f]+);", probe).group(1), 16)
for name in ("AgendaReminderLiveService", "AgendaReminder", "HomeworkNotice", "ClassroomWorkService", "SyncJobService"):
    text = (source_dir / f"{name}.java").read_text(encoding="utf-8")
    ids = [int(value, 0) for value in re.findall(r"(?:ID|NOTIFICATION_ID) = (0x[0-9a-fA-F]+|[0-9]+);", text)]
    assert probe_id not in ids, (name, probe_id)
print("Probe/production notification identities: PASS (distinct IDs plus runtime collision guard)")
