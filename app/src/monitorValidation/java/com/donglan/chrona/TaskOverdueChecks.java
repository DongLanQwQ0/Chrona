package com.donglan.chrona;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.HomeTodoSummary;
import com.donglan.chrona.data.RepeatOccurrences;
import com.donglan.chrona.data.RepeatRule;
import com.donglan.chrona.data.ScheduleQuery;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real SQLite fixture. It only appends one synthetic capture and removes that capture. */
final class TaskOverdueChecks {
    private static final long HOUR = 3_600_000L, DAY = 24 * HOUR;
    private final Context app;
    private final long now = System.currentTimeMillis();
    private final ZoneId zone = ZoneId.systemDefault();
    private final String token = "overdue-check-" + now + "-" + android.os.Process.myPid();
    private final Set<Long> expectedOverdue = new LinkedHashSet<>();
    private final List<Long> expectedOpen = new ArrayList<>();
    private final JSONArray evidence = new JSONArray();
    private long taskId;
    private int assertions, expectedToday, expectedUndated;
    private Instrumentation instrumentation;
    private Bundle arguments;

    private TaskOverdueChecks(Context app) { this.app = app; }

    static int run(Instrumentation test, Bundle args) throws Exception {
        Context app = test.getTargetContext();
        if (!"com.donglan.chrona.monitor.validation".equals(app.getPackageName()))
            throw new SecurityException("Isolated monitor fixture package required");
        if (!android.os.Build.HARDWARE.contains("ranchu"))
            throw new SecurityException("Owned shared emulator required");
        TaskOverdueChecks checks = new TaskOverdueChecks(app);
        checks.instrumentation=test;checks.arguments=args;
        return checks.run();
    }

    private int run() throws Exception {
        try (TaskStore store = new TaskStore(app)) {
            HomeTodoSummary baseline = store.homeTodoSummary(now, zone, 0);
            ScheduleQuery all = query("", ScheduleQuery.TODO_ANY);
            Set<Long> baselineIds = new HashSet<>(store.queryScheduleIds(all));
            try {
                try {
                    taskId = store.insertTask(token, null, "device_check", now);
                    store.updateStatus(taskId, TaskRecord.READY, null);
                    seedCases(store);
                    verifyStore(store, baseline, "non-repeating cases");
                    if("true".equals(arguments.getString("visual"))) verifyDetail();
                    verifyRepeats(store, baseline);
                } finally {
                    if (taskId > 0) check(store.deleteTask(taskId), "remove only this fixture capture");
                    check(new HashSet<>(store.queryScheduleIds(all)).equals(baselineIds),
                            "pre-existing schedule IDs preserved after cleanup");
                    HomeTodoSummary after = store.homeTodoSummary(now, zone, 0);
                    check(after.total == baseline.total && after.todayDue == baseline.todayDue
                            && after.overdue == baseline.overdue && after.unscheduled == baseline.unscheduled,
                            "pre-existing homepage counts restored after cleanup");
                }
                writeReport(true, null);
                return assertions;
            } catch (Exception | AssertionError failure) {
                writeReport(false, failure.toString());
                throw failure;
            }
        }
    }

    private void verifyDetail()throws Exception{
        android.app.Activity detail=instrumentation.startActivitySync(new android.content.Intent(app,TaskDetailActivity.class)
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK|android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .putExtra("task_id",taskId).putExtra("candidate_id",expectedOverdue.iterator().next()));
        try{
            long until=android.os.SystemClock.uptimeMillis()+8000;
            while(!visibleText(detail.getWindow().getDecorView(),"已逾期")&&android.os.SystemClock.uptimeMillis()<until)android.os.SystemClock.sleep(100);
            check(visibleText(detail.getWindow().getDecorView(),"已逾期"),"real native detail labels missed execution overdue despite future submission deadline");
            android.graphics.Bitmap raw=instrumentation.getUiAutomation().takeScreenshot();float ratio=Math.min(1f,1200f/Math.max(raw.getWidth(),raw.getHeight()));
            android.graphics.Bitmap small=android.graphics.Bitmap.createScaledBitmap(raw,Math.max(1,Math.round(raw.getWidth()*ratio)),Math.max(1,Math.round(raw.getHeight()*ratio)),true);
            File directory=new File(app.getExternalFilesDir(null),"monitor-validation");if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Cannot create evidence directory");
            try(FileOutputStream out=new FileOutputStream(new File(directory,"task-overdue-detail-"+arguments.getString("theme","light")+".jpg"))){small.compress(android.graphics.Bitmap.CompressFormat.JPEG,75,out);}finally{if(small!=raw)small.recycle();raw.recycle();}
        }finally{instrumentation.runOnMainSync(detail::finish);}
    }
    private boolean visibleText(android.view.View view,String text){
        android.graphics.Rect rect=new android.graphics.Rect();
        if(view instanceof android.widget.TextView label&&label.getText().toString().contains(text)&&view.isShown()&&view.getGlobalVisibleRect(rect)&&!rect.isEmpty())return true;
        if(view instanceof android.view.ViewGroup group)for(int i=0;i<group.getChildCount();i++)if(visibleText(group.getChildAt(i),text))return true;
        return false;
    }

    private void seedCases(TaskStore store) throws Exception {
        for (String category : new String[]{"task", "homework"}) {
            add(store, category + " ended deadline future", category, now-2*HOUR, now-HOUR, now+DAY, null, false, true);
            add(store, category + " ongoing", category, now-HOUR, now+HOUR, now+DAY, null, false, false);
            add(store, category + " start only", category, now-HOUR, null, now+DAY, null, false, true);
            add(store, category + " end only", category, null, now-HOUR, now+DAY, null, false, true);
            add(store, category + " earlier deadline", category, now+HOUR, now+2*HOUR, now-HOUR, null, false, true);
            add(store, category + " equal end", category, now-HOUR, now, now+DAY, null, false, false);
            add(store, category + " equal due", category, null, null, now, null, false, false);
            add(store, category + " completed", category, now-2*HOUR, now-HOUR, now+DAY, now, false, false);
            add(store, category + " record", category, now-2*HOUR, now-HOUR, now+DAY, null, true, false);
            add(store, category + " due only expired", category, null, null, now-HOUR, null, false, true);
        }
        add(store, "default start passed", "task", null, null, now+10*60_000L, null, false, false);
        add(store, "no time", "task", null, null, null, null, false, false);
        add(store, "event ended", "event", now-2*HOUR, now-HOUR, now-HOUR, null, false, false);
        add(store, "no due ended", "task", now-2*HOUR, now-HOUR, null, null, false, true);
        add(store, "no due start only", "task", now-HOUR, null, null, null, false, true);
        add(store, "no due end only", "task", null, now-HOUR, null, null, false, true);
        // Equal timestamps span several real 24-row pages in both directions.
        for (int i = 0; i < 53; i++) add(store, "page " + i, i % 2 == 0 ? "task" : "homework",
                now-2*HOUR, now-HOUR, now+2*DAY, null, false, true);
    }

    private EventCandidate candidate(String label, String category, Long start, Long end,
            Long due, Long completed, boolean record) {
        return new EventCandidate(0, taskId, token + " " + label, start, end, zone.getId(), "",
                "Synthetic overdue validation", 10, false, null, category, false, false,
                EventCandidate.CERTAIN, due, completed, record);
    }

    private EventCandidate add(TaskStore store, String label, String category, Long start, Long end,
            Long due, Long completed, boolean record, boolean overdue) throws Exception {
        EventCandidate source = candidate(label, category, start, end, due, completed, record);
        long id = store.appendCandidate(source);
        EventCandidate saved = store.getCandidatesByIds(List.of(id)).get(0);
        check(java.util.Objects.equals(saved.startAtMillis, start) && java.util.Objects.equals(saved.endAtMillis, end)
                && java.util.Objects.equals(saved.dueAtMillis, due), label + " raw fields round trip");
        check(saved.overdue(now) == overdue, label + " model overdue");
        check(saved.reminderMinutesBefore == 10, label + " reminder minutes preserved");
        Long effective = !saved.isTask() ? null : due != null ? due : end != null ? end : start;
        check(java.util.Objects.equals(saved.effectiveDueAtMillis(), effective), label + " effective deadline unchanged");
        if (saved.isTask()) check(java.util.Objects.equals(saved.reminderAnchor(), effective), label + " reminder anchor unchanged");
        if ("default start passed".equals(label)) {
            check(saved.hasDefaultExecutionInterval() && saved.executionStartAtMillis() < now
                    && saved.executionEndAtMillis().equals(due) && !saved.overdue(now),
                    "derived default execution interval does not trigger early overdue");
        }
        register(saved, overdue);
        return saved;
    }

    private void register(EventCandidate item, boolean overdue) {
        if (item.isTask() && !item.completed()) {
            expectedOpen.add(item.id);
            Long deadline = item.effectiveDueAtMillis();
            if (deadline == null) expectedUndated++;
            else if (Instant.ofEpochMilli(deadline).atZone(zone).toLocalDate()
                    .equals(Instant.ofEpochMilli(now).atZone(zone).toLocalDate())) expectedToday++;
        }
        if (overdue) expectedOverdue.add(item.id);
    }

    private void verifyRepeats(TaskStore store, HomeTodoSummary baseline) throws Exception {
        for (String category : new String[]{"task", "homework"}) {
            EventCandidate template = candidate(category + " repeat", category, now-2*DAY,
                    now-2*DAY+HOUR, now+5*DAY, null, false)
                    .withRepeat(new RepeatRule("daily", 1, List.of(), null, 4));
            long id = store.appendCandidate(template);
            EventCandidate series = store.getCandidatesByIds(List.of(id)).get(0);
            EventCandidate row = query(token, ScheduleQuery.TODO_OVERDUE).seriesRow(series);
            check(row != null && row.overdue(now) && row.dueAtMillis > now,
                    category + " repeat execution missed despite future deadline");
            long firstOriginal = row.occurrenceAtMillis;
            register(row, true);
            verifyStore(store, baseline, category + " repeat appended");

            JSONObject completed = new JSONObject().put(Long.toString(firstOriginal),
                    new JSONObject().put("completed", now));
            series = series.withRepeatState(completed.toString(), null);
            check(store.updateCandidate(series), "persist one completed repeat instance");
            row = query(token, ScheduleQuery.TODO_OVERDUE).seriesRow(series);
            check(row != null && row.occurrenceAtMillis == firstOriginal+DAY && !row.completed(),
                    "completed first instance advances to next overdue instance");
            verifyStore(store, baseline, category + " one completed occurrence");

            series = RepeatOccurrences.edit(series, row.occurrenceAtMillis,
                    new JSONObject().put("start", now+DAY).put("end", now+DAY+HOUR));
            check(store.updateCandidate(series), "persist single future reschedule");
            check(query(token, ScheduleQuery.TODO_OVERDUE).seriesRow(series) == null,
                    "completed plus future-rescheduled instances leave no overdue row");
            expectedOverdue.remove(id);
            verifyStore(store, baseline, category + " overdue instance rescheduled");
        }
        EventCandidate future = candidate("future repeat moved backward", "task", now+2*DAY,
                now+2*DAY+HOUR, now+9*DAY, null, false)
                .withRepeat(new RepeatRule("daily", 1, List.of(), null, 2));
        long id = store.appendCandidate(future);
        future = store.getCandidatesByIds(List.of(id)).get(0);
        long futureOriginal = RepeatOccurrences.between(future, future.anchor(),
                future.anchor()+3*DAY, 2).get(0).occurrenceAtMillis;
        EventCandidate moved = RepeatOccurrences.edit(future, futureOriginal,
                new JSONObject().put("start", now-2*HOUR).put("end", now-HOUR));
        check(store.updateCandidate(moved), "persist future original moved into overdue window");
        EventCandidate row = query(token, ScheduleQuery.TODO_OVERDUE).seriesRow(moved);
        check(row != null && row.occurrenceAtMillis == futureOriginal && row.overdue(now),
                "backward single-instance reschedule remains visible as overdue");
        register(row, true);
        verifyStore(store, baseline, "future occurrence moved backward");
    }

    private void verifyStore(TaskStore store, HomeTodoSummary baseline, String phase) throws Exception {
        ScheduleQuery overdue = query(token, ScheduleQuery.TODO_OVERDUE);
        List<Long> ids = store.queryScheduleIds(overdue);
        check(new HashSet<>(ids).equals(expectedOverdue) && ids.size() == expectedOverdue.size(), phase + " IDs/count");
        for (boolean oldest : new boolean[]{true, false}) {
            List<EventCandidate> paged = new ArrayList<>();
            for (int page = 0; page <= ids.size()/ScheduleQuery.PAGE_SIZE+1; page++) {
                List<EventCandidate> rows = store.querySchedulePage(overdue, oldest, page);
                check(rows.size() <= ScheduleQuery.PAGE_SIZE, phase + " bounded page");
                paged.addAll(rows);
                if (rows.isEmpty()) break;
            }
            List<Long> pageIds = new ArrayList<>();
            for (int index = 0; index < paged.size(); index++) {
                EventCandidate row = paged.get(index);
                pageIds.add(row.id);
                check(row.overdue(now), phase + " displayed row overdue " + row.id);
                check(store.schedulePageForCandidate(overdue, oldest, row.id) == index/ScheduleQuery.PAGE_SIZE,
                        phase + " deep link page agrees " + row.id);
                if (index > 0) check(overdue.comparator(oldest).compare(paged.get(index-1), row) <= 0,
                        phase + " stable overdue order");
            }
            check(pageIds.size() == ids.size() && new HashSet<>(pageIds).equals(expectedOverdue), phase + " pages vs selected IDs");
        }
        HomeTodoSummary home = store.homeTodoSummary(now, zone, 0);
        check(home.total-baseline.total == expectedOpen.size(), phase + " home total");
        check(home.todayDue-baseline.todayDue == expectedToday, phase + " today deadline semantics unchanged");
        check(home.overdue-baseline.overdue == expectedOverdue.size(), phase + " home overdue agrees with filter");
        check(home.unscheduled-baseline.unscheduled == expectedUndated, phase + " home undated");
        evidence.put(new JSONObject().put("phase", phase).put("fixture_overdue", expectedOverdue.size())
                .put("fixture_open", expectedOpen.size()).put("actual_overdue_delta", home.overdue-baseline.overdue));
    }

    private ScheduleQuery query(String keyword, int scope) {
        return new ScheduleQuery(3, null, 0, keyword, null, null, now, zone, scope);
    }

    private void check(boolean valid, String message) {
        assertions++;
        if (!valid) throw new AssertionError(message);
    }

    private void writeReport(boolean passed, String failure) throws Exception {
        File file = new File(app.getExternalFilesDir(null), "monitor-validation/task-overdue.json");
        if (!file.getParentFile().isDirectory() && !file.getParentFile().mkdirs())
            throw new java.io.IOException("Cannot create fixture report directory");
        JSONObject result = new JSONObject().put("passed", passed).put("assertions", assertions)
                .put("now", now).put("zone", zone.getId()).put("capture_id", taskId)
                .put("phases", evidence).put("native_ui_checked", false)
                .put("school_requests", false).put("provider_writes", false);
        if (failure != null) result.put("failure", failure);
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(result.toString(2).getBytes(StandardCharsets.UTF_8));
        }
    }
}
