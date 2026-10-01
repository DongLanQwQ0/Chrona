"""Run real job coordinator/cancel methods with a blocked work body and fake scheduler."""
from pathlib import Path
import os
import re
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/processing/ProcessingJobService.java').read_text(encoding='utf-8')
def block(marker):
    start = source.index(marker)
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        if source[end] == '{': depth += 1
        elif source[end] == '}': depth -= 1
        end += 1
    return source[start:end]
parts = [block(marker) for marker in ('private static final class Execution',
         'public static void cancel(', 'private void process(JobParameters params, long taskId, Execution',
         'private static boolean sleep(', 'private static boolean wasStopped(')]
fields = '\n'.join(re.findall(r'private static final ConcurrentHashMap<[^;]+;', source))
harness = r'''
import java.io.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.net.*;
import com.donglan.chrona.net.RequestControl;
class Context {
    final JobScheduler scheduler = new JobScheduler();
    <T> T getSystemService(Class<T> type) { return type.cast(scheduler); }
}
class JobScheduler { void cancel(int id) { } } // Stop callback deliberately delayed.
class JobParameters { int getJobId() { return 10001; } }
public final class JobExecutionCheck extends Context {
    private static final int JOB_ID_BASE=10000;
    private static final long RETRY_BUDGET_MILLIS=240000;
    FIELDS
    METHODS
    final AtomicInteger calls=new AtomicInteger(), failures=new AtomicInteger(), finishes=new AtomicInteger();
    final CountDownLatch firstStarted=new CountDownLatch(1), releaseFirst=new CountDownLatch(1);
    final CountDownLatch secondStarted=new CountDownLatch(1), releaseSecond=new CountDownLatch(1);
    void processOnce(JobParameters params,long taskId,RequestControl control) throws IOException {
        int call=calls.incrementAndGet();
        CountDownLatch release=call==1?releaseFirst:releaseSecond;
        (call==1?firstStarted:secondStarted).countDown();
        boolean done=false;
        while(!done) try { release.await(); done=true; }
        catch(InterruptedException ignored) { } // Emulates a provider still unwinding cancellation.
        control.check();
    }
    void recordFailure(long taskId,String message) { failures.incrementAndGet(); }
    static String message(Exception error) { return error.getMessage(); }
    void jobFinished(JobParameters params,boolean retry) { finishes.incrementAndGet(); }
    Execution start(Execution preceding) {
        Execution run=new Execution();
        if(preceding==null) IN_FLIGHT.put(1L,run);
        run.thread=new Thread(()->process(new JobParameters(),1L,run,preceding));
        workers.put(10001,run);run.thread.start();return run;
    }
    public static void main(String[] args) throws Exception {
        try(RequestControl control=new RequestControl(2000)) {
            control.cancel();
            check(!Thread.currentThread().isInterrupted()&&wasStopped(control),"cancel flag before interrupt");
        }
        JobExecutionCheck test=new JobExecutionCheck();
        Execution old=test.start(null);
        check(test.firstStarted.await(1,TimeUnit.SECONDS),"owner starts");
        old.control.cancel(); // System stopped the old run; local unwinding is still blocked.
        Execution fresh=test.start(old);
        Thread.sleep(70);
        check(test.calls.get()==1,"waiter cannot overlap owner");
        test.releaseFirst.countDown();old.thread.join(1000);
        check(test.secondStarted.await(1,TimeUnit.SECONDS),"rescheduled run survives");
        check(workers.get(10001)==fresh&&IN_FLIGHT.get(1L)==fresh,"old finally preserves new identity");
        test.releaseSecond.countDown();fresh.thread.join(1000);
        check(test.failures.get()==0&&test.finishes.get()==1,"cancelled owner never fails or finishes new job");
        check(workers.isEmpty()&&IN_FLIGHT.isEmpty(),"registry cleanup");

        test=new JobExecutionCheck();
        old=test.start(null);check(test.firstStarted.await(1,TimeUnit.SECONDS),"delete owner starts");
        fresh=test.start(old);Thread.sleep(70);
        JobExecutionCheck deleting=test;
        Thread ownerThread=old.thread;
        old.control.register(new HttpURLConnection(new URL("https://offline.test/owner")) {
            public void connect() { } public boolean usingProxy() { return false; }
            public void disconnect() {
                // Release the claim during cancel(), before the caller can reach the next run.
                deleting.releaseFirst.countDown();
                try { ownerThread.join(1000);Thread.sleep(150); }
                catch(InterruptedException error) { Thread.currentThread().interrupt(); }
            }
        });
        cancel(test,1L);
        check(old.control.isCancelled()&&fresh.control.isCancelled(),"delete immediately cancels owner and waiter");
        test.releaseFirst.countDown();test.releaseSecond.countDown();
        old.thread.join(1000);fresh.thread.join(1000);
        check(!old.thread.isAlive()&&!fresh.thread.isAlive()&&test.calls.get()==1,"deleted waiter never runs");
        check(test.failures.get()==0&&test.finishes.get()==0,"no failed state or stale jobFinished on cancel");
        check(workers.isEmpty()&&IN_FLIGHT.isEmpty(),"cancel cleanup");
        System.out.println("JobExecutionCheck passed: cancellation flag before interrupt, serialized reschedule, identity cleanup, delayed scheduler stop and owner release during waiter cancellation");
    }
    static void check(boolean value,String label) { if(!value) throw new AssertionError(label); }
}
'''.replace('FIELDS', fields).replace('METHODS', '\n'.join(parts))
directory = root / 'build/job-execution-check'
directory.mkdir(parents=True, exist_ok=True)
path = directory / 'JobExecutionCheck.java'
path.write_text(harness, encoding='utf-8')
java = Path(os.environ.get('JAVA_HOME','D:/Minecraft/java21')) / 'bin'
subprocess.run([str(java/'javac.exe'),'--release','17','-sourcepath',str(root/'app/src/main/java'),'-d',str(directory),str(path)],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(directory),'JobExecutionCheck'],check=True)
