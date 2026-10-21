package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.data.*;
import java.util.List;

/** A bounded section snapshot. Read on a worker; only View creation remains on the UI thread. */
final class DashboardSectionData {
    HomeData home;
    long revision;
    int review, failed, page;
    List<Long> ids = java.util.Collections.emptyList(), linked = java.util.Collections.emptyList();
    List<TaskRecord> tasks = java.util.Collections.emptyList();
    List<EventCandidate> candidates = java.util.Collections.emptyList();
    static DashboardSectionData load(Context context, int destination, InboxQuery inbox,
            ScheduleQuery schedule, boolean oldestFirst, int requestedPage, boolean system, long focusCandidate) {
        DashboardSectionData data = new DashboardSectionData();
        try (TaskStore store = new TaskStore(context)) {
            android.database.sqlite.SQLiteDatabase db = store.getReadableDatabase();
            db.beginTransactionNonExclusive();
            try {
                data.revision = store.dataRevision();
                if (destination == DashboardActivity.HOME) {
                    data.home = new HomeData(context, store);
                    data.review = data.home.review; data.failed = data.home.failed;
                } else {
                    data.review = store.taskCountByStatus(TaskRecord.NEEDS_REVIEW);
                    data.failed = store.taskCountByStatus(TaskRecord.FAILED);
                    if (destination == DashboardActivity.INBOX) {
                        data.ids = store.queryInboxIds(inbox, oldestFirst);
                        data.page = Math.max(0, Math.min(requestedPage, (Math.max(1, data.ids.size()) - 1) / InboxQuery.PAGE_SIZE));
                        data.tasks = store.queryInboxPage(inbox, oldestFirst, data.page, data.ids);
                    } else if (system) data.linked = store.linkedCalendarIds();
                    else {
                        data.ids = store.queryScheduleIds(schedule);
                        if (focusCandidate > 0) {
                            int page = store.schedulePageForCandidate(schedule, oldestFirst, focusCandidate);
                            if (page >= 0) requestedPage = page;
                        }
                        data.page = Math.max(0, Math.min(requestedPage, (Math.max(1, data.ids.size()) - 1) / ScheduleQuery.PAGE_SIZE));
                        data.candidates = store.querySchedulePage(schedule, oldestFirst, data.page);
                    }
                }
                db.setTransactionSuccessful();
            } finally { db.endTransaction(); }
        }
        return data;
    }
}
