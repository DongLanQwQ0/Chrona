package com.donglan.chrona;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;

/** Provider changes while the app process is absent are picked up by a light periodic read. */
public final class AgendaReminderJobService extends JobService {
    private static final int JOB = 0x434853;
    static void schedule(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (!AgendaReminderPreferences.enabled(context)) { scheduler.cancel(JOB); return; }
        if (scheduler.getPendingJob(JOB) == null) scheduler.schedule(new JobInfo.Builder(JOB,
                new ComponentName(context, AgendaReminderJobService.class)).setPersisted(true)
                .setPeriodic(15 * 60_000L).build());
    }
    @Override public boolean onStartJob(JobParameters params) {
        AgendaReminder.WORKER.execute(() -> {
            AgendaReminder.refreshSafely(this, null); jobFinished(params, false);
        });
        return true;
    }
    @Override public boolean onStopJob(JobParameters params) { return true; }
}
