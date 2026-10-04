"""Production baseline/network logic plus native transaction wiring; no Android runtime."""
import os
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/com/donglan/chrona"
out = root / "build/edit-conflict-check"
out.mkdir(parents=True, exist_ok=True)
java = Path(os.environ.get("JAVA_HOME", "D:/Minecraft/java21")) / "bin"
files = [src / "CandidateEditBaseline.java", src / "LanNetworkIdentity.java",
         src / "data/EventCandidate.java", src / "data/EventCategory.java",
         root / "checks/EditConflictCheck.java"]
subprocess.run([str(java / "javac.exe"), "--release", "17", "-encoding", "UTF-8", "-d", str(out), *map(str, files)], check=True)
subprocess.run([str(java / "java.exe"), "-cp", str(out), "com.donglan.chrona.EditConflictCheck"], check=True)
text = (src / "TaskDetailActivity.java").read_text(encoding="utf-8")
for name, next_name in [("private void publish(", "private void checkCalendarDuplicate"),
                        ("private void associateExistingEvent(", "private static EventCandidate requireCandidate"),
                        ("private void showNoteEditorDialog(", "/** Date/time values")]:
    body = text[text.index(name):text.index(next_name, text.index(name))]
    assert "synchronized (AndroidSync.LOCK)" in body
    assert body.index("beginTransaction()") < body.index("requireCandidateBaseline(") < body.index("endTransaction()")
    assert body.index("endTransaction()") < body.index("deliverSave(owner ->")
    assert body.index("requireCandidateBaseline(") < body.index("new CalendarStore") if name.endswith("Dialog(") else True
assert "owner.publish(edited, expected)" in text and "owner.associateExistingEvent(edited, expected, eventId)" in text
assert 'draft.putString("baseline", savedBaseline)' in text
raw = text[text.index("private void editCaptureContent(TaskRecord task, String draft)"):text.index("private void addAttachmentSection")]
assert raw.index("new Thread(() ->") < raw.index("synchronized (AndroidSync.LOCK)")
assert raw.index("endTransaction()") < raw.index("deliverSave(owner ->")
assert "owner.editCaptureContent(task, updated)" in raw
assert "current.rawText, task.rawText" in raw and "current.status, task.status" in raw
service = (src / "LanAccessService.java").read_text(encoding="utf-8")
assert "binding.lost(network.getNetworkHandle())" in service and "!binding.matches(lanBinding())" in service
print("Native transaction wiring passed: lock, baseline before side effects, committed callbacks and duplicate-dialog baseline retention")
