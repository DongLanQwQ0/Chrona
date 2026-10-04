package com.donglan.chrona;

import android.app.job.*;
import android.content.ComponentName;
import android.content.Context;

/** Low-frequency network-constrained periodic sync, explicitly opt-in. */
public final class SyncJobService extends JobService {
    private static final int ID = 73109;
    static void schedule(Context context) {
        JobScheduler jobs = context.getSystemService(JobScheduler.class);
        if (jobs == null) return;
        WebDavSettingsStore settings = new WebDavSettingsStore(context);
        if (!settings.automatic() || !settings.configured()) { jobs.cancel(ID); return; }
        jobs.schedule(new JobInfo.Builder(ID, new ComponentName(context, SyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY).setPeriodic(30 * 60_000L).setPersisted(true).build());
    }
    @Override public boolean onStartJob(JobParameters parameters) {
        WebDavSettingsStore settings = new WebDavSettingsStore(this);
        if (!settings.automatic() || !settings.configured()) return false;
        return AndroidSync.request(this, result -> jobFinished(parameters, false));
    }
    @Override public boolean onStopJob(JobParameters parameters) { AndroidSync.cancelNetwork(); return false; }
}
