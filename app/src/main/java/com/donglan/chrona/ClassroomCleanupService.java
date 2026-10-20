package com.donglan.chrona;

import android.app.job.*;
import android.content.*;
import com.donglan.chrona.classroom.ClassroomStore;
import java.util.concurrent.*;

/** Offline maintenance of unused screenshots, never a school or AI request. */
public final class ClassroomCleanupService extends JobService {
    private static final int ID=73118;
    static final ExecutorService WORKER=Executors.newSingleThreadExecutor();
    private Future<?> task;
    static void schedule(Context context){
        JobScheduler jobs=context.getSystemService(JobScheduler.class);if(jobs==null)return;
        if(jobs.getPendingJob(ID)==null)jobs.schedule(new JobInfo.Builder(ID,new ComponentName(context,ClassroomCleanupService.class))
                .setPeriodic(24*60*60*1000L).setPersisted(true).build());
    }
    static void foreground(Context context){
        Context app=context.getApplicationContext();WORKER.execute(()->{
            try{ClassroomStore.cleanUnused(app,false,System.currentTimeMillis());}catch(Exception ignored){/* Periodic maintenance retries; manual cleanup reports errors. */}
        });
    }
    @Override public boolean onStartJob(JobParameters params){
        task=WORKER.submit(()->{boolean retry=false;try{ClassroomStore.cleanUnused(this,false,System.currentTimeMillis());}catch(Exception e){retry=true;}jobFinished(params,retry);});return true;
    }
    @Override public boolean onStopJob(JobParameters params){if(task!=null)task.cancel(true);return true;}
}
