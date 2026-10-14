package com.donglan.chrona;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public final class AgendaReminderReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (!"refresh".equals(action) && !"hide".equals(action) && !"complete".equals(action) && !"snooze".equals(action)
                && !Intent.ACTION_BOOT_COMPLETED.equals(action) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)
                && !Intent.ACTION_TIME_CHANGED.equals(action) && !Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                && !android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED.equals(action)) return;
        PendingResult result = goAsync();
        AgendaReminderJobService.schedule(context);
        AgendaReminder.WORKER.execute(() -> {
            try { AgendaReminder.refreshSafely(context.getApplicationContext(), intent); }
            finally { result.finish(); }
        });
    }
}
