package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.HomeTodoSummary;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/** Bounded local first-screen data, prepared away from the UI thread on cold launch. */
final class HomeData {
    final long revision, now;
    final int review, failed, limit;
    final LocalDate today, endDate;
    final ZoneId zone;
    final List<EventCandidate> candidates, upcoming;
    final HomeTodoSummary todos;
    final List<Long> linkedIds;

    HomeData(Context context, TaskStore store) {
        now = System.currentTimeMillis();
        zone = ZoneId.systemDefault();
        today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        endDate = today.plusMonths(1);
        limit = HomeTimelinePreferences.getItemLimit(context);
        revision = store.dataRevision();
        review = store.taskCountByStatus(TaskRecord.NEEDS_REVIEW);
        failed = store.taskCountByStatus(TaskRecord.FAILED);
        candidates = store.queryHomeUpcoming(now, zone, Math.max(5, limit));
        upcoming = candidates.subList(0, Math.min(5, candidates.size()));
        todos = store.homeTodoSummary(now, zone, 3);
        linkedIds = HomeTimelinePreferences.includesSystemCalendar(context)
                ? store.linkedCalendarIds() : java.util.Collections.emptyList();
    }
}
