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
    static String carouselRestore(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        long[] tasks = new long[2];
        Activity activity = null;
        try {
            try (TaskStore store = new TaskStore(context)) {
                // Remove only this harness's leftovers if an earlier assertion killed instrumentation.
                java.util.ArrayList<Long> leftovers = new java.util.ArrayList<>();
                try(var cursor=store.getReadableDatabase().rawQuery(
                        "SELECT id FROM tasks WHERE source=? AND raw_text IN (?,?)",
                        new String[]{"device_check","详情横滑验收 0","详情横滑验收 1"})) {
                    while(cursor.moveToNext())leftovers.add(cursor.getLong(0));
                }
                for(long leftover:leftovers)store.deleteTask(leftover);
                for (int t = 0; t < 2; t++) {
                    tasks[t] = store.insertTask("详情横滑验收 " + t, null, "device_check", System.currentTimeMillis());
                    for (int i = 0; i < t + 1; i++) store.appendCandidate(new EventCandidate(0, tasks[t],
                            "横滑测试 " + t + "/" + i, null, null, "Asia/Shanghai", "", "临时验收，自动清理", null,
                            false, null, "task", false));
                    store.updateStatus(tasks[t],TaskRecord.READY,null);
                }
            }
            activity = test.startActivitySync(new Intent(context,TaskDetailActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("task_id",tasks[0]));
            Activity screen = activity;
            JSONObject report = new JSONObject();
            for (int pass = 0; pass < 3; pass++) {
                Object[] adjacent = new Object[1];
                test.runOnMainSync(() -> {
                    try {
                        var positions = TaskDetailActivity.class.getDeclaredField("taskCandidatePagePositions"); positions.setAccessible(true);
                        @SuppressWarnings("unchecked") Map<Long,Integer> values = (Map<Long,Integer>)positions.get(screen);
                        values.put(tasks[1],1);
                        var build = TaskDetailActivity.class.getDeclaredMethod("buildAdjacentPage",long.class); build.setAccessible(true);
                        adjacent[0] = build.invoke(screen,tasks[1]);
                        var adjacentField = TaskDetailActivity.class.getDeclaredField("adjacentPage"); adjacentField.setAccessible(true); adjacentField.set(screen,adjacent[0]);
                        var shell = adjacent[0].getClass().getDeclaredField("shell"); shell.setAccessible(true);
                        var pager = TaskDetailActivity.class.getDeclaredField("detailPager"); pager.setAccessible(true);
                        ((android.widget.FrameLayout)pager.get(screen)).addView((View)shell.get(adjacent[0]),new android.widget.FrameLayout.LayoutParams(-1,-1));
                    } catch (ReflectiveOperationException failure) {throw new IllegalStateException(failure);}
                });
                // Let the adjacent page's posted work run while the original task is still active.
                SystemClock.sleep(350); test.waitForIdleSync();
                test.runOnMainSync(() -> {
                    try {
                        var commit = TaskDetailActivity.class.getDeclaredMethod("completeAdjacentPageGesture",boolean.class); commit.setAccessible(true);commit.invoke(screen,true);
                    } catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
                });
                test.waitForIdleSync();
                String[] layoutFailure = {null};
                test.runOnMainSync(() -> {
                    CandidatePagerScrollView carousel = pager(screen.getWindow().getDecorView());
                    int width = carousel.getChildAt(0).getWidth()/2;
                    if (carousel.getScrollX()!=width) {layoutFailure[0]="Restored second page is blank: scrollX="+carousel.getScrollX()+", expected="+width;return;}
                    EditText title = title(screen.getWindow().getDecorView(),"横滑测试 1/1");
                    android.graphics.Rect rect = new android.graphics.Rect();
                    if(title==null || !title.getGlobalVisibleRect(rect)) layoutFailure[0]="Restored candidate not visible without a horizontal nudge";
                });
                if(layoutFailure[0]!=null)throw new AssertionError(layoutFailure[0]);
                test.runOnMainSync(() -> {
                    try {var render=TaskDetailActivity.class.getDeclaredMethod("renderTaskPage",long.class);render.setAccessible(true);render.invoke(screen,tasks[0]);}
                    catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
                });
                test.waitForIdleSync();
            }
            return report.put("adjacentReturnPasses",3).put("secondCandidateVisibleWithoutTouch",true)
                    .put("temporaryFixtureOnly",true).toString();
        } finally {
            Activity screen = activity;
            if(screen!=null)test.runOnMainSync(screen::finish);
            try(TaskStore store=new TaskStore(context)){for(long task:tasks)if(task>0)store.deleteTask(task);}
        }
    }
    static String controls(Instrumentation test) throws Exception {
        Context context = test.getTargetContext();
        long task = 0, candidate = 0;
        Activity activity = null;
        String account = "ui-fixture-" + java.util.UUID.randomUUID();
        try {
            try (TaskStore store = new TaskStore(context)) {
                task = store.insertTask("作业控件验收（自动清理）", null, "device_check", System.currentTimeMillis());
                candidate = store.appendCandidate(new EventCandidate(0, task, "高等数学 · 课后练习",
                        null, null, "Asia/Shanghai", "", "完成课本第 3 章习题。内容与截止时间由课程平台同步。", null,
                        false, null, "task", false, false, 0, System.currentTimeMillis()+7*86_400_000L, null, false));
                store.updateStatus(task, TaskRecord.READY, null);
                store.getWritableDatabase().execSQL("INSERT INTO zju_homework_captures(capture_id,account) VALUES(?,?)",new Object[]{task,account});
                store.getWritableDatabase().execSQL("INSERT INTO zju_homework_map(account,remote_key,capture_id,candidate_id,ignored,snapshot,pending_calendar) VALUES(?,?,?,?,0,?,0)",
                        new Object[]{account,"ui-fixture",task,candidate,"{\"submitted\":false}"});
            }
            activity = test.startActivitySync(new Intent(context, TaskDetailActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("task_id", task)
                    .putExtra(TaskDetailActivity.EXTRA_CANDIDATE_ID, candidate));
            test.waitForIdleSync();
            Activity screen = activity;
            JSONObject report = new JSONObject();
            test.runOnMainSync(() -> {
                try {
                    View root = screen.getWindow().getDecorView();
                    View complete = described(root, "标记完成");
                    if (!(complete instanceof android.widget.ImageButton)) throw new AssertionError("Completion must be an icon button");
                    ViewGroup row = (ViewGroup) complete.getParent();
                    int index = row.indexOfChild(complete);
                    if (index != row.getChildCount()-2 || !(row.getChildAt(index-1) instanceof android.widget.Button)
                            || !String.valueOf(row.getChildAt(index+1).getContentDescription()).startsWith("删除日程"))
                        throw new AssertionError("Required order is new / completion / delete");
                    if (complete.getWidth()!=complete.getHeight()) throw new AssertionError("Completion target must be square");
                    EditText sourceTitle = title(root,"高等数学 · 课后练习");
                    if (sourceTitle == null || sourceTitle.getKeyListener()!=null) throw new AssertionError("Source title editable");
                    var dirty = TaskDetailActivity.class.getDeclaredField("dirtyCandidateIds");
                    dirty.setAccessible(true);
                    if (!((java.util.Set<?>)dirty.get(screen)).isEmpty()) throw new AssertionError("Opening read-only fields creates a dirty draft");
                    boolean linksExpected = new ZjuSettingsStore(context).enabled();
                    if (linksExpected && (text(root,"雪灾浙大M")==null || text(root,"雪灾浙大")==null))
                        throw new AssertionError("Both homework site actions required");
                    report.put("newCompleteDeleteOrder", true).put("completionSquare", true)
                            .put("completionWidthPx", complete.getWidth()).put("sourceTitleReadOnly",true)
                            .put("schoolLinksVisible",linksExpected).put("openingCreatesDirtyDraft",false);
                } catch (org.json.JSONException | ReflectiveOperationException failure) { throw new IllegalStateException(failure); }
            });
            android.graphics.Bitmap image = test.getUiAutomation().takeScreenshot();
            java.io.File screenshot = new java.io.File(context.getExternalFilesDir(null),"homework-controls.png");
            if (image == null) throw new AssertionError("Screenshot unavailable");
            try (java.io.FileOutputStream stream = new java.io.FileOutputStream(screenshot)) {
                image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,stream);
            } finally { image.recycle(); }
            report.put("screenshot",screenshot.getAbsolutePath()).put("temporaryFixtureOnly",true);
            return report.toString();
        } finally {
            Activity screen=activity;
            if(screen!=null)test.runOnMainSync(screen::finish);
            if(task>0)try(TaskStore store=new TaskStore(context)) {
                store.deleteTask(task);
                store.getWritableDatabase().delete("zju_homework_map","account=?",new String[]{account});
                store.getWritableDatabase().delete("zju_homework_captures","account=?",new String[]{account});
            }
        }
    }
    private static View described(View view,String description) {
        if(description.contentEquals(String.valueOf(view.getContentDescription())))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++) {
            View found=described(group.getChildAt(i),description);if(found!=null)return found;
        }
        return null;
    }
    private static View text(View view,String value) {
        if(view instanceof android.widget.TextView label && value.contentEquals(label.getText()))return view;
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++) {
            View found=text(group.getChildAt(i),value);if(found!=null)return found;
        }
        return null;
    }
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
