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
        String courseTerm = getIntent().getStringExtra(CourseAgenda.EXTRA_TERM);
        Intent target = courseTerm != null ? new Intent(this, TimetableActivity.class)
                .putExtra(CourseAgenda.EXTRA_TERM, courseTerm)
                .putExtra(CourseAgenda.EXTRA_OCCURRENCE, getIntent().getStringExtra(CourseAgenda.EXTRA_OCCURRENCE))
                : task > 0 ? new Intent(this, TaskDetailActivity.class).putExtra("task_id", task)
                .putExtra(TaskDetailActivity.EXTRA_CANDIDATE_ID,
                        getIntent().getLongExtra(TaskDetailActivity.EXTRA_CANDIDATE_ID, 0))
                : event < 0 ? ExternalCourseAgenda.details(this, event,
                        getIntent().getLongExtra("begin", 0), getIntent().getLongExtra("end", 0))
                : event > 0 ? new Intent(Intent.ACTION_VIEW,
                        ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, event))
                        .putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME,
                                getIntent().getLongExtra("begin", 0))
                        .putExtra(CalendarContract.EXTRA_EVENT_END_TIME,
                                getIntent().getLongExtra("end", 0))
                : new Intent(this, DashboardActivity.class);
        if (task > 0 && getIntent().hasExtra("occurrence_at")) {
            target.setClass(this, RepeatScheduleActivity.class)
                    .putExtra("occurrence_at", getIntent().getLongExtra("occurrence_at", 0));
        }
        try { startActivity(target); }
        catch (android.content.ActivityNotFoundException exception) {
            startActivity(new Intent(this, DashboardActivity.class));
        }
        finish();
    }
}
