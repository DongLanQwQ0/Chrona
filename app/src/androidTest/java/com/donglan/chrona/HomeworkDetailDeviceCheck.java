package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;

/** Local multi-homework fixture. Never fetches school data or edits a user task. */
final class HomeworkDetailDeviceCheck {
    static String sourceRead(Context context) throws Exception {
        JSONObject report = new JSONObject();
        java.time.ZoneId zone = java.time.ZoneId.systemDefault();
        long begin = java.time.LocalDate.now().atStartOfDay(zone).toInstant().toEpochMilli();
        long end = begin + 7 * 86_400_000L;
        try { report.put("imported", CourseAgenda.load(context, begin, end, zone).size()); }
        catch (Throwable failure) { report.put("importedError", android.util.Log.getStackTraceString(failure)); }
        try { report.put("merged", ExternalCourseAgenda.merge(context, List.of(), begin, end).size()); }
        catch (Throwable failure) { report.put("mergedError", android.util.Log.getStackTraceString(failure)); }
        try { report.put("calendar", AgendaRepository.load(context, begin, end, true, zone, new android.os.CancellationSignal()).entries.size()); }
        catch (Throwable failure) { report.put("calendarError", android.util.Log.getStackTraceString(failure)); }
        return report.toString();
    }
    static String run(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        long task = 0, selected = 0;
        Activity activity = null;
        try {
            try (TaskStore store = new TaskStore(context)) {
                task = store.insertTask("作业详情性能测试（自动清理）", null, "device_check", System.currentTimeMillis());
                for (int index = 0; index < 60; index++) {
                    long candidate = store.appendCandidate(new EventCandidate(0, task, "测试课程 · 作业 " + index,
                            null, null, "Asia/Shanghai", "", "本地性能测试内容", null,
                            false, null, "task", false, false, 0,
                            System.currentTimeMillis() + 7 * 86_400_000L, null, false));
                    if (index == 30) selected = candidate;
                }
                store.updateStatus(task, TaskRecord.READY, null);
            }
            long started = SystemClock.uptimeMillis();
            activity = test.startActivitySync(new Intent(context, TaskDetailActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("task_id", task)
                    .putExtra(TaskDetailActivity.EXTRA_CANDIDATE_ID, selected));
            long firstOpen = SystemClock.uptimeMillis() - started;
            Activity screen = activity;
            int[] editors = {0}, page = {-1};
            test.runOnMainSync(() -> {
                try {
                    var readers = TaskDetailActivity.class.getDeclaredField("draftReaders"); readers.setAccessible(true);
                    editors[0] = ((Map<?, ?>) readers.get(screen)).size();
                    var current = TaskDetailActivity.class.getDeclaredField("candidatePageIndex"); current.setAccessible(true);
                    page[0] = current.getInt(screen);
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            if (page[0] != 30) throw new AssertionError("Wrong initial homework: " + page[0]);
            if (editors[0] > 3) throw new AssertionError("Opening built " + editors[0] + " editors for one visible homework");
            test.runOnMainSync(() -> {
                EditText title = title(screen.getWindow().getDecorView(), "测试课程 · 作业 30");
                if (title == null) throw new AssertionError("Selected homework editor not found");
                title.setText("测试课程 · 保留草稿");
                CandidatePagerScrollView pager = pager(screen.getWindow().getDecorView());
                if (pager == null) throw new AssertionError("Candidate pager not found");
                try {
                    var width = CandidatePagerScrollView.class.getDeclaredField("pageWidth"); width.setAccessible(true);
                    int pageWidth = width.getInt(pager);
                    pager.scrollTo(31 * pageWidth, 0); pager.scrollTo(30 * pageWidth, 0);
                    if (title(screen.getWindow().getDecorView(), "测试课程 · 保留草稿") != title)
                        throw new AssertionError("Paging replaced an edited field");
                } catch (ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            });
            return new JSONObject().put("fixtureCandidates", 60).put("openedCandidateIndex", page[0])
                    .put("openedInMs", firstOpen).put("builtEditors", editors[0])
                    .put("draftPreservedOnPaging", true).put("networkRequests", 0).put("temporaryFixtureOnly", true).toString();
        } finally {
            Activity screen = activity;
            if (screen != null) test.runOnMainSync(screen::finish);
            if (task > 0) try (TaskStore store = new TaskStore(context)) { store.deleteTask(task); }
        }
    }
    private static EditText title(View view, String value) {
        if (view instanceof EditText text && value.contentEquals(text.getText())) return text;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            EditText found = title(group.getChildAt(i), value); if (found != null) return found;
        }
        return null;
    }
    private static CandidatePagerScrollView pager(View view) {
        if (view instanceof CandidatePagerScrollView pager) return pager;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            CandidatePagerScrollView found = pager(group.getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
}
