package com.donglan.chrona;

import android.app.job.JobParameters;
import android.app.job.JobService;

/** Persisted, bounded recovery; never tries a forbidden foreground-service launch from a job. */
public final class ClassroomMonitorJobService extends JobService {
    private int generation;
    @Override public boolean onStartJob(JobParameters parameters){
        int token=++generation;
        return ClassroomMonitor.run(this,active->{if(token==generation)jobFinished(parameters,false);ClassroomMonitor.schedule(this);});
    }
    @Override public boolean onStopJob(JobParameters parameters){generation++;ClassroomMonitor.cancel();return true;}
}
