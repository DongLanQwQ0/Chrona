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
        ZjuSettingsStore settings=new ZjuSettingsStore(context);
        if(!settings.enabled()||!settings.configured()){jobs.cancel(ID);return;}
        jobs.schedule(new JobInfo.Builder(ID,new ComponentName(context,ZjuSyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(30*60_000L).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters parameters){return ZjuSync.request(this,result->jobFinished(parameters,false));}
    @Override public boolean onStopJob(JobParameters parameters){ZjuSync.cancel();return false;}
}
