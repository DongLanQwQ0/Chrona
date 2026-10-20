package com.donglan.chrona;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

public final class ZjuSyncJobService extends JobService {
    private static final int ID=73111;
    static void schedule(Context context){
        JobScheduler jobs=context.getSystemService(JobScheduler.class);if(jobs==null)return;
        long delay=ZjuSync.nextDelay(context,System.currentTimeMillis());
        if(delay<0){jobs.cancel(ID);return;}
        // Periodic jobs have a 15-minute floor. Rearm the earliest independently
        // due source; Android may still defer network work in Doze.
        jobs.schedule(new JobInfo.Builder(ID,new ComponentName(context,ZjuSyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setMinimumLatency(delay).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters parameters){
        return ZjuSync.requestDue(this,result->{jobFinished(parameters,false);ZjuSync.rearmIfIdle(this);});
    }
    @Override public boolean onStopJob(JobParameters parameters){ZjuSync.cancel();return true;}
}
