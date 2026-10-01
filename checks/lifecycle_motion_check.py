"""Execute production back-registration/recovery methods with minimal Android doubles."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "app/src/main/java/com/donglan/chrona"


def method(file, marker):
    source = file.read_text(encoding="utf-8")
    start = source.index(marker)
    end = source.index("{", start) + 1
    depth = 1
    while depth:
        if source[end] == "{": depth += 1
        if source[end] == "}": depth -= 1
        end += 1
    return source[start:end]


back = method(JAVA / "TaskDetailActivity.java", "private void updateDetailBack()")
back = back.replace("android.os.Build.VERSION.SDK_INT", "SDK")
back = back.replace("android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT", "0")
recover = method(JAVA / "processing/ProcessingJobService.java", "public static int reconcile(")
harness = r'''
import java.util.*;
import java.util.concurrent.*;
public final class LifecycleMotionCheck {
    static int SDK=36;
    boolean hasUnsavedEdits, saveInFlight, detailBackRegistered;
    Runnable detailBack;
    final Dispatcher dispatcher=new Dispatcher();
    Dispatcher getOnBackInvokedDispatcher(){ return dispatcher; }
    void requestExit() { }
    static class Dispatcher {
        int registrations, removals;
        void registerOnBackInvokedCallback(int priority,Runnable callback){ registrations++; }
        void unregisterOnBackInvokedCallback(Runnable callback){ removals++; }
    }
    BACK
    static final Map<Long,Object> IN_FLIGHT=new ConcurrentHashMap<>();
    static final int JOB_ID_BASE=10000;
    static class Context {
        final JobScheduler scheduler=new JobScheduler();
        <T> T getSystemService(Class<T> type){ return type.cast(scheduler); }
    }
    static class JobScheduler {
        final Set<Integer> pending=new HashSet<>();
        Object getPendingJob(int id){ return pending.contains(id)?new Object():null; }
    }
    static class TaskStore implements AutoCloseable {
        static final Map<Long,String> rows=new HashMap<>();
        static int writes;
        TaskStore(Context context) { }
        List<Long> processingTaskIds(){
            List<Long> result=new ArrayList<>();
            rows.forEach((id,status)->{ if(status.equals("processing")) result.add(id); });
            // A completed task changes after the recovery reader saw it.
            rows.put(4L,"ready");
            return result;
        }
        boolean failInterruptedProcessing(long id,String message){
            check(Thread.holdsLock(IN_FLIGHT),"recovery and claims share the lock");
            if(!"processing".equals(rows.get(id))) return false;
            rows.put(id,"failed");writes++;return true;
        }
        public void close() { }
    }
    static class DiagLog { static void add(Context context,String message) { } }
    RECOVER
    static void check(boolean value,String name){ if(!value) throw new AssertionError(name); }
    public static void main(String[] args) {
        LifecycleMotionCheck page=new LifecycleMotionCheck();
        page.updateDetailBack();
        check(page.dispatcher.registrations==0,"clean page keeps system predictive back");
        page.hasUnsavedEdits=true;page.updateDetailBack();page.updateDetailBack();
        check(page.dispatcher.registrations==1,"dirty page registers once");
        page.saveInFlight=true;page.hasUnsavedEdits=false;page.updateDetailBack();
        check(page.dispatcher.removals==0,"busy save keeps protection");
        page.saveInFlight=false;page.updateDetailBack();page.updateDetailBack();
        check(page.dispatcher.removals==1,"finished edit restores system back");
        SDK=26;page.hasUnsavedEdits=true;page.updateDetailBack();
        check(page.dispatcher.registrations==1,"legacy API does not register");
        Context context=new Context();
        for(long id=1;id<=4;id++) TaskStore.rows.put(id,"processing");
        TaskStore.rows.put(5L,"ready");
        IN_FLIGHT.put(2L,new Object());context.scheduler.pending.add(10003);
        check(reconcile(context)==1,"only orphan processing record recovers");
        check(TaskStore.writes==1&&TaskStore.rows.get(1L).equals("failed"),"one conditional write");
        check(TaskStore.rows.get(2L).equals("processing"),"active worker retained");
        check(TaskStore.rows.get(3L).equals("processing"),"scheduled job retained");
        check(TaskStore.rows.get(4L).equals("ready"),"completed row is not overwritten");
        System.out.println("Lifecycle motion checks passed: conditional back and orphan recovery (test doubles)");
    }
}
'''.replace("BACK", back).replace("RECOVER", recover)
directory = ROOT / "build/lifecycle-motion-check"
directory.mkdir(parents=True, exist_ok=True)
java_file = directory / "LifecycleMotionCheck.java"
java_file.write_text(harness, encoding="utf-8")
java_home = Path(os.environ["JAVA_HOME"])
subprocess.run([str(java_home / "bin/javac.exe"), "--release", "17", "-encoding", "UTF-8",
                "-d", str(directory), str(java_file)], check=True)
subprocess.run([str(java_home / "bin/java.exe"), "-cp", str(directory), "LifecycleMotionCheck"], check=True)
