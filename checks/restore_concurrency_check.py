"""Latch-driven JVM checks of the real restore gate and extracted job/restore control flow.

Android scheduling, database reads and the restore body are explicit doubles. This
checks admission/lifetime/cleanup ordering, not Android SQLite or scheduler behavior.
"""
from pathlib import Path
import os
import re
import subprocess
import textwrap

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/restore-concurrency-check"
SRC = OUT / "src"
JAVA = Path(os.environ.get("JAVA_HOME", "D:/Minecraft/java21")) / "bin"
PRODUCTION = ROOT / "app/src/main/java/com/donglan/chrona"
job = (PRODUCTION / "processing/ProcessingJobService.java").read_text(encoding="utf-8")
backup = (PRODUCTION / "ChronaDataBackup.java").read_text(encoding="utf-8")
store = (PRODUCTION / "data/TaskStore.java").read_text(encoding="utf-8")


def block(source, marker):
    start = source.index(marker)
    opening = source.index("{", start)
    depth = 1
    tokens = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[{}]')
    for token in tokens.finditer(source, opening + 1):
        if token.group() == "{":
            depth += 1
        elif token.group() == "}":
            depth -= 1
            if depth == 0:
                return source[start:token.end()]
    raise AssertionError("Unclosed production block: " + marker)


def write(name, content):
    path = SRC / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(textwrap.dedent(content), encoding="utf-8")
    return path


# Exact wiring guards: snapshots bypass live admission, while live stores retain
# the lease until close's finally, including revision/super.close failures.
constructor = block(store, "private TaskStore(Context context, String databaseName)")
assert "liveAccess = DATABASE_NAME.equals(databaseName) ? LiveDatabaseAccess.acquire() : null;" in constructor
assert "return new TaskStore(context, snapshot.getAbsolutePath());" in block(store, "public static TaskStore openSnapshot(")
assert "this(context, DATABASE_NAME);" in block(store, "public TaskStore(Context context)")
closing = block(store, "public synchronized void close()")
assert "finally { if (liveAccess != null) liveAccess.close(); }" in closing
assert closing.index("super.close();") < closing.index("liveAccess.close();")
restore = block(backup, "public static String restore(Context context, Prepared prepared)")
assert restore.index("synchronized (AndroidSync.LOCK)") < restore.index("synchronized(ClassroomStore.LOCK)") < restore.index("LiveDatabaseAccess.beginRestore()")
assert restore.index("LiveDatabaseAccess.beginRestore()") < restore.index("restoreExclusive(context, prepared)") < restore.index("AndroidSync.resetAfterRestore(context)")
assert "JobSchedulerBridge.cancel(context);" in block(backup, "private static String restoreExclusive(")

markers = (
    "private static final class Execution", "public boolean onStartJob(",
    "public boolean onStopJob(", "public static void cancel(",
    "private void process(JobParameters params, long taskId, Execution",
    "private static boolean sleep(",
)
methods = "\n".join(block(job, marker) for marker in markers)
fields = "\n".join(re.findall(r"private static final ConcurrentHashMap<[^;]+;", job))
template = (ROOT / "checks/RestoreConcurrencyCheck.java").read_text(encoding="utf-8")
write("RestoreConcurrencyCheck.java", template.replace("/* PRODUCTION_JOB_FIELDS */", fields)
      .replace("/* PRODUCTION_JOB_METHODS */", methods)
      .replace("/* PRODUCTION_RESTORE */", restore)
      .replace("/* PRODUCTION_BRIDGE */", block(backup, "private static final class JobSchedulerBridge")))
write("android/os/Looper.java", """package android.os;
public final class Looper {public static Looper getMainLooper(){return new Looper();}}
""")
write("android/os/Handler.java", """package android.os;
import java.util.concurrent.ConcurrentLinkedQueue;
public final class Handler {
    public static final ConcurrentLinkedQueue<Runnable> pending=new ConcurrentLinkedQueue<>();
    public Handler(Looper looper){}
    public boolean post(Runnable action){pending.add(action);return true;}
    public static void drain(){Runnable r;while((r=pending.poll())!=null)r.run();}
}
""")
write("android/app/job/JobInfo.java", """package android.app.job;
public final class JobInfo {
    private final int id;private final String service;
    public JobInfo(int id,String service){this.id=id;this.service=service;}
    public int getId(){return id;}
    public Service getService(){return new Service();}
    public final class Service {public String getClassName(){return service;}}
}
""")
write("android/app/job/JobScheduler.java", """package android.app.job;
import java.util.*;
public final class JobScheduler {
    public final Map<Integer,JobInfo> jobs=new LinkedHashMap<>();
    public final List<Integer> cancelled=new ArrayList<>();
    public List<JobInfo> getAllPendingJobs(){return new ArrayList<>(jobs.values());}
    public void cancel(int id){cancelled.add(id);jobs.remove(id);}
}
""")
sources = [str(p) for p in SRC.rglob("*.java")]
sources += [str(PRODUCTION / "data/LiveDatabaseAccess.java"), str(PRODUCTION / "net/RequestControl.java")]
subprocess.run([str(JAVA / "javac.exe"), "-encoding", "UTF-8", "--release", "17", "-d", str(OUT), *sources], check=True)
subprocess.run([str(JAVA / "java.exe"), "-cp", str(OUT), "RestoreConcurrencyCheck"], check=True, timeout=25)
print("Restore concurrency wiring passed: live lifecycle, snapshot exclusion, business-lock order, identity-reset scope")
