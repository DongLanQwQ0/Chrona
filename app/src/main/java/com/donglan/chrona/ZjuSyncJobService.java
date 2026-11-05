package com.donglan.chrona;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public final class ZjuSyncJobService extends JobService {
    private static final int ID=73111;
    // Main-thread owned. Ignore a callback from a stopped/replaced invocation.
    private volatile JobParameters activeJob;
    private JobParameters requestedJob; // Scheduler-thread owned.

    static void schedule(Context context){
        Context app = context.getApplicationContext();
        ZjuSync.SCHEDULER.execute(() -> scheduleNow(app));
    }
    static void rearmIfIdle(Context context) {
        Context app = context.getApplicationContext();
        ZjuSync.SCHEDULER.execute(() -> { if (ZjuSync.idle()) scheduleNow(app); });
    }
    private static void scheduleNow(Context context){
        JobScheduler jobs=context.getSystemService(JobScheduler.class);if(jobs==null)return;
        long delay=ZjuSync.nextDelay(context,System.currentTimeMillis());
        if(delay<0){jobs.cancel(ID);return;}
        // Periodic jobs have a 15-minute floor. Rearm the earliest independently
        // due source; Android may still defer network work in Doze.
        jobs.schedule(new JobInfo.Builder(ID,new ComponentName(context,ZjuSyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(delay).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters parameters){
        activeJob = parameters;
        Context app = getApplicationContext();
        ZjuSync.SCHEDULER.execute(() -> {
            if (activeJob != parameters) return;
            if (ZjuSync.requestDue(app, result -> {
                if (activeJob != parameters) return;
                activeJob = null;
                jobFinished(parameters, false);
                rearmIfIdle(app);
            })) requestedJob = parameters;
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters parameters){
        if (activeJob != parameters) return true;
        activeJob = null;
        ZjuSync.SCHEDULER.execute(() -> {
            if (requestedJob != parameters) return;
            requestedJob = null;
            ZjuSync.cancel();
        });
        return true;
    }
}
