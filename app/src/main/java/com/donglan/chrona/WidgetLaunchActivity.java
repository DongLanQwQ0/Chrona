package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.content.ContentUris;
import android.os.Bundle;
import android.provider.CalendarContract;

/** Explicit, non-exported target for a collection's mutable click template. */
public final class WidgetLaunchActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        long task = getIntent().getLongExtra("task_id", 0);
        long event = getIntent().getLongExtra("event_id", 0);
        Intent target = task > 0 ? new Intent(this, TaskDetailActivity.class).putExtra("task_id", task)
                : event > 0 ? new Intent(Intent.ACTION_VIEW,
                        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event))
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                                getIntent().getLongExtra("begin", 0))
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,
                                getIntent().getLongExtra("end", 0))
                : new Intent(this, DashboardActivity.class);
        try { startActivity(target); }
        catch (android.content.ActivityNotFoundException exception) {
            startActivity(new Intent(this, DashboardActivity.class));
        }
        finish();
    }
}
