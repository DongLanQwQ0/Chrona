// Template: the Python runner inserts unmodified production methods at the markers.
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import com.donglan.chrona.data.LiveDatabaseAccess;
import com.donglan.chrona.net.RequestControl;
import android.app.job.JobScheduler;
import android.app.job.JobInfo;

class Context {
    final JobScheduler scheduler = new JobScheduler();
    <T> T getSystemService(Class<T> type) { return type.cast(scheduler); }
}
class JobParameters {
    final long id;
    final boolean userInitiated;
    JobParameters(long id) { this(id, false); }
    JobParameters(long id, boolean userInitiated) { this.id=id; this.userInitiated=userInitiated; }
    Extras getExtras() { return new Extras(); }
    int getJobId() { return 10000+(int)id; }
    boolean isUserInitiatedJob() { return userInitiated; }
    final class Extras { long getLong(String key, long fallback) { return id; } }
}
class Build {
    static final class VERSION { static final int SDK_INT=34; }
    static final class VERSION_CODES { static final int UPSIDE_DOWN_CAKE=34; }
}
class DiagLog { static void add(Context c, String message) {} }

class ProcessingJobService extends Context {
    private static final String EXTRA_TASK_ID="task_id";
    private static final int JOB_ID_BASE=10000, RUNNING_NOTIFICATION_BASE=20000;
    private static final int JOB_END_NOTIFICATION_POLICY_REMOVE=0;
    private static final long RETRY_BUDGET_MILLIS=240000;
    /* PRODUCTION_JOB_FIELDS */
    /* PRODUCTION_JOB_METHODS */

    final CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
    final AtomicInteger reads=new AtomicInteger(), calls=new AtomicInteger();
    final AtomicInteger finishes=new AtomicInteger(), retries=new AtomicInteger(), failures=new AtomicInteger();
    volatile Thread worker;
    boolean failNotice, failWork, failRecording, failFinish;
    Object runningNotification(long taskId) {
        reads.incrementAndGet();
        if(failNotice) throw new IllegalStateException("notice start failed");
        return new Object();
    }
    void setNotification(JobParameters p,int id,Object notification,int policy) {}
    void processOnce(JobParameters p,long id,RequestControl control) throws IOException {
        worker=Thread.currentThread();calls.incrementAndGet();reads.incrementAndGet();entered.countDown();
        // Simulate a provider which keeps unwinding after the stop interrupt.
        RestoreConcurrencyCheck.awaitUninterruptibly(release);
        if(failWork) throw new IOException("worker failed");
        control.check();
    }
    void recordFailure(long id,String message) {
        failures.incrementAndGet();
        if(failRecording) throw new IllegalStateException("record failed");
    }
    static String message(Exception e) { return e.getMessage(); }
    void jobFinished(JobParameters p,boolean retry) {
        finishes.incrementAndGet();if(retry)retries.incrementAndGet();
        if(failFinish)throw new IllegalStateException("finish failed");
    }
    static boolean empty() { return workers.isEmpty()&&IN_FLIGHT.isEmpty(); }
}

class AndroidSync {
    static final Object LOCK=new Object();
    static void resetAfterRestore(Context c) throws Exception {
        ChronaDataBackup.identityEntered.countDown();
        RestoreConcurrencyCheck.await(ChronaDataBackup.identityRelease,"identity release");
        if(ChronaDataBackup.failIdentity)throw new IOException("identity failed");
    }
}
class ClassroomStore {static final Object LOCK=new Object();static boolean anyActive(){return false;}}
class ZjuSync { static void cancel(){} }
class ZjuSettingsStore {
    final Prefs prefs=new Prefs();ZjuSettingsStore(Context c){}
    void options(boolean enabled,boolean timetable){}String account(){return "fixture";}
    static final class Prefs {boolean getBoolean(String name,boolean fallback){return fallback;}}
}
class ZjuHomeworkStore {ZjuHomeworkStore(Context c){}void configure(String account,boolean enabled){}}

class ChronaDataBackup {
    static final class Prepared {}
    static CountDownLatch bodyEntered,bodyRelease,identityEntered,identityRelease;
    static boolean failBody,failIdentity;
    static void reset(boolean bodyFailure,boolean identityFailure) {
        bodyEntered=new CountDownLatch(1);bodyRelease=new CountDownLatch(1);
        identityEntered=new CountDownLatch(1);identityRelease=new CountDownLatch(1);
        failBody=bodyFailure;failIdentity=identityFailure;
    }
    static String restoreExclusive(Context c,Prepared p)throws Exception {
        // The installing thread must be able to open the new live DB itself.
        try(LiveDatabaseAccess.Lease access=LiveDatabaseAccess.acquire()) {
            bodyEntered.countDown();RestoreConcurrencyCheck.await(bodyRelease,"restore body release");
            if(failBody)throw new IOException("install or rollback failed");
        }
        return "restored";
    }
    /* PRODUCTION_RESTORE */
    /* PRODUCTION_BRIDGE */
    static void cancelParsing(Context c){JobSchedulerBridge.cancel(c);}
}

public final class RestoreConcurrencyCheck {
    static final AtomicInteger assertions=new AtomicInteger();
    static int scenarios;
    static final AtomicReference<Throwable> uncaught=new AtomicReference<>();
    static void check(boolean value,String label) {
        assertions.incrementAndGet();if(!value)throw new AssertionError(label);
    }
    static void await(CountDownLatch latch,String label)throws InterruptedException {
        check(latch.await(2,TimeUnit.SECONDS),label);
    }
    static void awaitUninterruptibly(CountDownLatch latch) {
        boolean done=false;
        while(!done)try{await(latch,"worker release");done=true;}catch(InterruptedException ignored){}
    }
    static void join(Thread thread,String label)throws InterruptedException {
        thread.join(2000);check(!thread.isAlive(),label);
    }
    static Thread start(String name,Runnable action) {
        Thread thread=new Thread(action,name);thread.setDaemon(true);thread.start();return thread;
    }
    static void rejected(String label) {
        try(LiveDatabaseAccess.Restore restore=LiveDatabaseAccess.beginRestore()) {
            throw new AssertionError(label+": unexpectedly admitted");
        }catch(IllegalStateException expected){assertions.incrementAndGet();}
    }
    static void idle(String label) {
        check(ProcessingJobService.empty(),label+" registries cleared");
        try(LiveDatabaseAccess.Restore restore=LiveDatabaseAccess.beginRestore()){assertions.incrementAndGet();}
    }

    static void scannedQueuedThenStarted()throws Exception {
        scenarios++;
        CountDownLatch scanned=new CountDownLatch(1),admitted=new CountDownLatch(1);
        ProcessingJobService service=new ProcessingJobService();
        JobParameters params=new JobParameters(1);
        Thread dispatch=start("after-queued-scan",()->{
            try{await(scanned,"queued scan completes");check(service.onStartJob(params),"worker admitted");}
            catch(Throwable t){uncaught.set(t);}finally{admitted.countDown();}
        });
        // Legacy status-only checks saw QUEUED, not PROCESSING. Admission races next.
        scanned.countDown();await(admitted,"worker admission");await(service.entered,"worker owns live DB");
        rejected("worker admission after queued scan blocks replacement");
        service.release.countDown();join(service.worker,"queued worker exits");join(dispatch,"dispatcher exits");
        idle("queued race");
    }

    static void stoppedStillUnwinding(boolean cancel)throws Exception {
        scenarios++;
        ProcessingJobService service=new ProcessingJobService();JobParameters params=new JobParameters(2);
        check(service.onStartJob(params),"stopped worker starts");await(service.entered,"stop worker entered");
        if(cancel)ProcessingJobService.cancel(service,2);else check(service.onStopJob(params),"stop requests retry");
        rejected((cancel?"cancel":"onStop")+" must retain access until process finally");
        check(service.worker.isAlive(),"stopped provider still unwinding");
        service.release.countDown();join(service.worker,"stopped worker exits");
        check(service.failures.get()==0&&service.finishes.get()==0,"stopped execution avoids failure/finish");
        idle("stop/cancel");
    }

    static void startDuringRestore()throws Exception {
        scenarios++;
        ProcessingJobService service=new ProcessingJobService();
        try(LiveDatabaseAccess.Restore restore=LiveDatabaseAccess.beginRestore()) {
            // UIDT would read the DB for its notification if admission happened too late.
            check(service.onStartJob(new JobParameters(3,true)),"restore defers job asynchronously");
            check(service.calls.get()==0&&service.reads.get()==0,"no processOnce or notification DB read during restore");
            check(service.finishes.get()==0&&android.os.Handler.pending.size()==1,"retry is posted, not inline");
            check(ProcessingJobService.empty(),"deferred job has no worker or owner");
            android.os.Handler.drain();
            check(service.retries.get()==1&&service.finishes.get()==1,"posted jobFinished requests retry");
            check(LiveDatabaseAccess.tryAcquire()==null,"ordinary job admission remains closed");
        }
        idle("deferred start");
    }

    static void ordinaryReaderWaits(boolean failBody,boolean failIdentity)throws Exception {
        scenarios++;
        ChronaDataBackup.reset(failBody,failIdentity);
        AtomicReference<Throwable> restoreError=new AtomicReference<>();
        Thread restoring=start("restore",()->{
            try{ChronaDataBackup.restore(new Context(),new ChronaDataBackup.Prepared());}
            catch(Throwable t){restoreError.set(t);}
        });
        await(ChronaDataBackup.bodyEntered,"restore owner opens DB reentrantly");
        CountDownLatch attempting=new CountDownLatch(1),acquired=new CountDownLatch(1);
        Thread reader=start("ordinary-reader",()->{
            attempting.countDown();
            try(LiveDatabaseAccess.Lease access=LiveDatabaseAccess.acquire()){acquired.countDown();}
        });
        await(attempting,"reader attempting admission");
        check(!acquired.await(70,TimeUnit.MILLISECONDS),"ordinary acquire blocked while install/rollback active");
        reader.interrupt();
        check(!acquired.await(70,TimeUnit.MILLISECONDS),"interruption cannot bypass restore gate");
        ChronaDataBackup.bodyRelease.countDown();
        if(!failBody) {
            await(ChronaDataBackup.identityEntered,"identity reset entered");
            check(!acquired.await(70,TimeUnit.MILLISECONDS),"gate covers identity reset, not just installation");
            ChronaDataBackup.identityRelease.countDown();
        }
        join(restoring,"restore exits");await(acquired,"reader resumes after restore success/failure");join(reader,"reader exits");
        check(reader.isInterrupted(),"waiting reader interrupt preserved");
        check((restoreError.get()!=null)==(failBody||failIdentity),"restore success/failure propagated");
        if(restoreError.get()!=null)check(restoreError.get() instanceof IOException,"expected injected restore failure");
        idle("restore reader release");
    }

    static void neverWaitHoldingBusinessLock()throws Exception {
        scenarios++;
        CountDownLatch workerOwns=new CountDownLatch(1),tryLock=new CountDownLatch(1),workerDone=new CountDownLatch(1);
        CountDownLatch restoreDone=new CountDownLatch(1);
        AtomicReference<Throwable> result=new AtomicReference<>();
        Thread worker=start("worker-needs-business-lock",()->{
            try(LiveDatabaseAccess.Lease lease=LiveDatabaseAccess.acquire()) {
                workerOwns.countDown();awaitUninterruptibly(tryLock);
                synchronized(AndroidSync.LOCK){synchronized(ClassroomStore.LOCK){workerDone.countDown();}}
            }
        });
        await(workerOwns,"worker lease before business lock");
        Thread restore=start("restore-holds-business-locks",()->{
            synchronized(AndroidSync.LOCK){synchronized(ClassroomStore.LOCK){
                tryLock.countDown();
                try{ChronaDataBackup.restore(new Context(),new ChronaDataBackup.Prepared());}
                catch(Throwable t){result.set(t);}finally{restoreDone.countDown();}
            }}
        });
        await(restoreDone,"restore refuses instead of waiting under business locks");
        check(result.get() instanceof IllegalStateException,"busy restore is rejected without replacing data");
        await(workerDone,"worker gets business lock after refusal");join(worker,"business worker exits");join(restore,"busy restore exits");
        idle("business lock refusal");
    }

    static void jobFailuresRelease()throws Exception {
        scenarios++;
        ProcessingJobService startup=new ProcessingJobService();startup.failNotice=true;
        try {startup.onStartJob(new JobParameters(4,true));throw new AssertionError("startup must fail");}
        catch(IllegalStateException expected){assertions.incrementAndGet();}
        check(startup.calls.get()==0,"failed startup never runs work");idle("startup exception");
        for(int mode=0;mode<3;mode++) {
            scenarios++;
            ProcessingJobService service=new ProcessingJobService();service.failWork=true;
            service.failRecording=mode==1;service.failFinish=mode==2;
            check(service.onStartJob(new JobParameters(5)),"failing worker admitted");await(service.entered,"failing worker entered");
            AtomicReference<Throwable> error=new AtomicReference<>();service.worker.setUncaughtExceptionHandler((t,e)->error.set(e));
            service.release.countDown();join(service.worker,"failing worker exits");
            check(service.failures.get()==1,"worker error recorded once");
            check((error.get()!=null)==(mode!=0),"cleanup injection propagates expected exception");
            idle("worker/record/finish failure");
        }
    }

    static void onlyParsingCancelled() {
        scenarios++;
        Context c=new Context();
        c.scheduler.jobs.put(10001,new JobInfo(10001,ProcessingJobService.class.getName()));
        c.scheduler.jobs.put(10002,new JobInfo(10002,ProcessingJobService.class.getName()));
        c.scheduler.jobs.put(17,new JobInfo(17,"com.donglan.chrona.ClassroomCleanupService"));
        c.scheduler.jobs.put(10003,new JobInfo(10003,"other.PeriodicService"));
        ChronaDataBackup.cancelParsing(c);
        check(c.scheduler.cancelled.equals(List.of(10001,10002)),"real bridge cancels parsing service only");
        check(c.scheduler.jobs.keySet().equals(Set.of(17,10003)),"cleanup and unrelated jobs retained regardless of id range");
    }

    static void leaseCloseIsIdempotent() {
        scenarios++;
        LiveDatabaseAccess.Lease first=LiveDatabaseAccess.acquire(),second=LiveDatabaseAccess.acquire();
        first.close();first.close();rejected("double close does not release another live handle");second.close();
        idle("lease idempotence");
    }

    public static void main(String[] args)throws Exception {
        Thread.setDefaultUncaughtExceptionHandler((t,e)->{uncaught.compareAndSet(null,e);e.printStackTrace();});
        scannedQueuedThenStarted();stoppedStillUnwinding(false);stoppedStillUnwinding(true);
        startDuringRestore();ordinaryReaderWaits(false,false);ordinaryReaderWaits(true,false);ordinaryReaderWaits(false,true);
        neverWaitHoldingBusinessLock();jobFailuresRelease();onlyParsingCancelled();leaseCloseIsIdempotent();
        check(uncaught.get()==null,"no background assertion/exception: "+uncaught.get());
        System.out.println("RestoreConcurrencyCheck passed: "+scenarios+" scenarios / "+assertions+" assertions");
    }
}
