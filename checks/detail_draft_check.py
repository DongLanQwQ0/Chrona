"""Compile the real retained detail session with minimal Activity/Bundle test doubles."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/com/donglan/chrona/TaskDetailActivity.java").read_text(encoding="utf-8")
start = source.index("private static final class SaveSession")
opening = source.index("{", start)
depth = 1
end = opening + 1
while depth:
    if source[end] == "{": depth += 1
    elif source[end] == "}": depth -= 1
    end += 1
session = source[start:end]
directory = root / "build/detail-draft-check"
directory.mkdir(parents=True, exist_ok=True)
harness = r'''
import java.util.*;
import java.util.function.Consumer;
import java.lang.ref.WeakReference;
public final class DetailDraftCheck {
    static final class Bundle {
        final Map<String,String> strings = new HashMap<>();
        void putString(String key, String value) { strings.put(key,value); }
        String getString(String key) { return strings.get(key); }
    }
    static final class TaskDetailActivity {
        boolean destroyed, finishing;
        int callbacks;
        boolean isDestroyed() { return destroyed; }
        boolean isFinishing() { return finishing; }
    }
    SESSION
    public static void main(String[] args) {
        SaveSession session = new SaveSession();
        TaskDetailActivity paused = new TaskDetailActivity();
        session.owner = new WeakReference<>(paused);
        Map<Long,Bundle> oldState = new HashMap<>();
        Bundle titleDraft = new Bundle();
        titleDraft.putString("title","unsaved title");
        titleDraft.putString("description","old note");
        oldState.put(1L,titleDraft);
        oldState.put(2L,new Bundle());
        // Android has saved the old state before the live owner's completion is delivered.
        session.pending = owner -> {
            session.savedNotes.put(1L,"saved new note");
            session.completedCandidates.add(2L);
            owner.callbacks++;
        };
        session.deliver();
        session.restoreDrafts(oldState);
        check(oldState.get(1L).getString("description").equals("saved new note"),"late saved note");
        check(oldState.get(1L).getString("title").equals("unsaved title"),"raw draft preserved");
        check(!oldState.containsKey(2L),"completed candidate no longer dirty");
        session.owner.clear();
        session.pending = owner -> owner.callbacks++;
        session.deliver();
        check(session.pending != null,"detached owner keeps completion");
        TaskDetailActivity recreated = new TaskDetailActivity();
        session.owner = new WeakReference<>(recreated);
        session.deliver(); session.deliver();
        check(recreated.callbacks == 1 && paused.callbacks == 1,"completion exactly once to current owner");
        recreated.destroyed = true;
        session.pending = owner -> owner.callbacks++;
        session.deliver();
        check(recreated.callbacks == 1 && session.pending != null,"destroyed owner ignored");
        System.out.println("DetailDraftCheck passed: stale Bundle after completion, raw title preservation, confirmed draft removal and retained callback ownership");
    }
    static void check(boolean value,String label) { if (!value) throw new AssertionError(label); }
}
'''.replace("SESSION", session)
path = directory / "DetailDraftCheck.java"
path.write_text(harness, encoding="utf-8")
java_home = Path(os.environ.get("JAVA_HOME", "D:/Minecraft/java21")) / "bin"
subprocess.run([str(java_home / "javac.exe"), "--release", "17", "-d", str(directory), str(path)], check=True)
subprocess.run([str(java_home / "java.exe"), "-cp", str(directory), "DetailDraftCheck"], check=True)
