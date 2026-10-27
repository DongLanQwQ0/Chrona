package com.donglan.chrona;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.ai.ChatCompletionClient;
import com.donglan.chrona.ai.ParseResult;
import com.donglan.chrona.ai.SchedulePreferences;
import com.donglan.chrona.ai.SchedulePrompt;
import com.donglan.chrona.ai.TimeAssessment;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.net.RequestControl;
import org.json.JSONObject;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;

/** Device-local, once-only scheduling of newly received school homework. */
final class ZjuHomeworkPlanner {
    private static final long REQUEST_BUDGET = 240_000L;
    private static volatile RequestControl active;
    private static final String RULES = "\n学校作业排程专用约束：本次只安排所给的这一项原作业，events必须恰好一项，禁止拆分或新增事项。"
            + "保留官方标题、正文和独立截止，不改提醒、分类、来源或身份。category=homework，start/end=null，"
            + "execution_time_explicit=false，all_day=false，recurrence=null，plan_group/plan_until=null。"
            + "结合实际工作量、已保存的用户偏好和从现在到官方截止的整个周期，给出suggested_start与duration_minutes，"
            + "保留真实的time_assessment和存疑等级。due用给出的官方截止本地日期及钟点，不能移动；"
            + "安排必须有完整工作窗口，无法安排时保留空执行建议。不要访问学校、不要索要账号密码。";

    interface Provider {
        ParseResult parse(AiSettings ai, SchedulePreferences preferences, RequestControl control,
                long taskId, String input, long reference, String zone) throws Exception;
    }
    private static final Provider NETWORK = (ai, preferences, control, taskId, input, reference, zone) ->
            new ChatCompletionClient(ai, control, preferences.systemPrompt() + RULES,
                    preferences.workStartMinutes, preferences.workEndMinutes)
                    .parse(taskId, input, null, null, reference, zone);

    /** Called only for a newly inserted item, in the same SQLite transaction as its source map. */
    static void enqueue(Context context, TaskStore store, ZjuHomeworkStore.State state,
            long candidateId, String stableId, String officialSnapshot) {
        if (!new ZjuSettingsStore(context).homeworkAutoPlan()) return;
        EventCandidate item = candidate(store, candidateId);
        if (!eligible(item, System.currentTimeMillis())) return;
        try {
            JSONObject request = new JSONObject().put("source", "zju_homework_schedule")
                    .put("candidateId", candidateId).put("candidateSyncId", stableId)
                    .put("account", state.account).put("generation", state.generation)
                    .put("official_snapshot", officialSnapshot).put("baseline", baseline(item));
            ContentValues values = new ContentValues(); values.put("candidate_id", candidateId);
            values.put("task_id", item.taskId); values.put("created_at", System.currentTimeMillis());
            values.put("state", "queued"); values.put("request_json", request.toString());
            store.getWritableDatabase().insertOrThrow("zju_homework_plans", null, values);
        } catch (org.json.JSONException invalid) { throw new IllegalArgumentException("作业排程资料无效", invalid); }
    }

    static void cancel() { RequestControl request = active; if (request != null) request.cancel(); }

    static void cancelPending(Context context) {
        cancel();
        try (TaskStore store = new TaskStore(context)) {
            ContentValues values = new ContentValues(); values.put("state", "cancelled"); values.put("error", "作业自动排程已关闭");
            store.getWritableDatabase().update("zju_homework_plans", values, "state IN ('queued','running')", null);
        }
    }

    static String processOne(Context context) { return processOne(context, NETWORK, null); }

    /** The sync owner serializes calls; a fake provider/settings can exercise races without AI traffic. */
    static synchronized String processOne(Context context, Provider provider, AiSettings testSettings) {
        ZjuSettingsStore settings = new ZjuSettingsStore(context);
        if (!settings.enabled() || !settings.configured() || !settings.homeworkAutoPlan()) return "";
        String runId = null; long candidateId = 0;
        try {
            AiSettings ai = testSettings == null ? new AiSettingsStore(context).load() : testSettings;
            if (ai == null) return "";
            SchedulePreferences preferences = SchedulePreferences.load(context);
            JSONObject request; EventCandidate original; long reference = System.currentTimeMillis();
            String zone = ZoneId.systemDefault().getId();
            try (RequestControl control = new RequestControl(REQUEST_BUDGET)) {
                synchronized (AndroidSync.LOCK) {
                    if (!settings.enabled() || !settings.configured() || !settings.homeworkAutoPlan()) return "";
                    active = control;
                    try (TaskStore store = new TaskStore(context)) {
                        SQLiteDatabase db = store.getWritableDatabase(); db.beginTransaction();
                        try {
                            ZjuHomeworkStore.State binding = ZjuHomeworkStore.state(db);
                            // No live request exists here: an old running row has an unknown paid outcome.
                            TaskStore.finishInterruptedParseRuns(db, binding.captureId, "InterruptedHomeworkSchedule");
                            try (Cursor row = db.rawQuery("SELECT candidate_id,request_json FROM zju_homework_plans WHERE state='queued' ORDER BY created_at,candidate_id LIMIT 1", null)) {
                                if (!row.moveToFirst()) { db.setTransactionSuccessful(); return ""; }
                                candidateId = row.getLong(0); request = new JSONObject(row.getString(1));
                            }
                            original = candidate(store, candidateId);
                            String rejected = rejection(context, store, original, request, reference);
                            if (rejected != null) {
                                finishQueue(db, candidateId, "cancelled", null, rejected);
                                db.setTransactionSuccessful(); return "";
                            }
                            String input = input(original, zone);
                            request.put("reference_epoch_millis", reference).put("time_zone_id", zone)
                                    .put("prompt_version", SchedulePrompt.VERSION).put("preferences", preferences.toJson())
                                    .put("system", preferences.systemPrompt() + RULES).put("input", input)
                                    .put("model", ai.model).put("reasoning_effort", ai.reasoningEffort);
                            runId = store.beginParseRun(original.taskId, reference, request);
                            finishQueue(db, candidateId, "running", runId, null);
                            db.setTransactionSuccessful();
                        } finally { db.endTransaction(); }
                    }
                }
                // School sync and notices have committed. No AndroidSync lock is held during AI I/O.
                ParseResult result = provider.parse(ai, preferences, control, original.taskId,
                        request.getString("input"), reference, zone);
                control.check();
                if (result.candidates.size() != 1 || result.timeAuditJson == null)
                    throw new IllegalArgumentException("作业排程必须只返回一项及时间依据");
                EventCandidate planned = result.candidates.get(0);
                JSONObject model = new JSONObject(result.timeAuditJson).getJSONArray("entries").getJSONObject(0).getJSONObject("model");
                boolean singleSuggestion = Boolean.FALSE.equals(model.opt("execution_time_explicit"))
                        && model.isNull("start") && model.isNull("end") && model.isNull("plan_group")
                        && model.isNull("plan_until") && model.isNull("recurrence") && model.isNull("occurrence_update");
                String invalid = !singleSuggestion ? "响应不符合单作业执行建议约束"
                        : validWindow(planned, original.dueAtMillis, System.currentTimeMillis(), zone, preferences)
                        && Objects.equals(planned.dueAtMillis, original.dueAtMillis) ? null : "没有截止前的完整可用工作时段";
                boolean applied = false;
                synchronized (AndroidSync.LOCK) {
                    try (TaskStore store = new TaskStore(context)) {
                        SQLiteDatabase db = store.getWritableDatabase(); db.beginTransaction();
                        try {
                            EventCandidate current = candidate(store, candidateId);
                            String rejected = rejection(context, store, current, request, System.currentTimeMillis());
                            try (Cursor queue = db.rawQuery("SELECT state FROM zju_homework_plans WHERE candidate_id=? AND run_id=?",
                                    new String[]{Long.toString(candidateId), runId})) {
                                if (!queue.moveToFirst() || !"running".equals(queue.getString(0))) rejected = "作业排程已停止";
                            }
                            String reason = rejected == null ? invalid : rejected;
                            if (reason == null && !validWindow(planned, current.dueAtMillis, System.currentTimeMillis(), zone, preferences))
                                reason = "执行时段已过或不在完整工作窗口内";
                            if (reason == null) {
                                ContentValues times = new ContentValues(); times.put("start_at_millis", planned.startAtMillis);
                                times.put("end_at_millis", planned.endAtMillis); times.put("uncertainty_level", planned.uncertaintyLevel);
                                times.put("end_auto_generated", planned.endAutoGenerated ? 1 : 0);
                                db.update("event_candidates", times, "id=?", new String[]{Long.toString(candidateId)});
                                db.execSQL("UPDATE zju_homework_map SET pending_calendar=1 WHERE candidate_id=?", new Object[]{candidateId});
                                applied = true;
                            }
                            if (current == null) store.failParseRun(runId, true, reason);
                            else store.saveHomeworkPlanAudit(original.taskId, candidateId, runId, result.timeAuditJson, applied, reason);
                            finishQueue(db, candidateId, applied ? "succeeded" : "cancelled", runId, reason);
                            db.setTransactionSuccessful();
                        } finally { db.endTransaction(); }
                    }
                }
                if (applied) {
                    try {
                        ZjuHomeworkCalendar.retryPending(context);
                        AgendaWidgetProvider.requestRefresh(context); AgendaReminder.requestRefresh(context);
                    } catch (RuntimeException projectionFailure) {
                        // Execution and evidence already committed; the durable calendar bit retries later.
                        return "新作业已自动排程，日历待更新";
                    }
                    return "新作业已自动排程";
                }
                return "作业自动排程未应用，保留原截止";
            } finally { active = null; }
        } catch (Exception failure) {
            if (runId != null) try (TaskStore store = new TaskStore(context)) {
                store.failParseRun(runId, failure instanceof java.io.InterruptedIOException, failure.getMessage());
                ContentValues failed = new ContentValues(); failed.put("state", "failed"); failed.put("error", failure.getMessage());
                store.getWritableDatabase().update("zju_homework_plans", failed,
                        "candidate_id=? AND run_id=? AND state='running'", new String[]{Long.toString(candidateId), runId});
            }
            return "作业自动排程未完成，保留原截止";
        }
    }

    private static void finishQueue(SQLiteDatabase db, long candidateId, String state, String runId, String error) {
        ContentValues values = new ContentValues(); values.put("state", state); values.put("run_id", runId); values.put("error", error);
        db.update("zju_homework_plans", values, "candidate_id=?", new String[]{Long.toString(candidateId)});
    }

    private static EventCandidate candidate(TaskStore store, long id) {
        List<EventCandidate> rows = store.getCandidatesByIds(List.of(id)); return rows.isEmpty() ? null : rows.get(0);
    }

    static boolean eligible(EventCandidate item, long now) {
        return item != null && item.isHomework() && !item.completed() && item.dueAtMillis != null
                && item.dueAtMillis > now && item.startAtMillis == null && item.endAtMillis == null
                && !item.allDay && item.repeatRule == null;
    }

    private static JSONObject baseline(EventCandidate item) throws org.json.JSONException {
        return TimeAssessment.snapshot(item).put("description", item.description == null ? JSONObject.NULL : item.description)
                .put("location", item.location == null ? JSONObject.NULL : item.location)
                .put("reminder", item.reminderMinutesBefore == null ? JSONObject.NULL : item.reminderMinutesBefore);
    }

    private static String rejection(Context context, TaskStore store, EventCandidate current, JSONObject request, long now)
            throws org.json.JSONException {
        ZjuSettingsStore settings = new ZjuSettingsStore(context);
        ZjuHomeworkStore.State state = ZjuHomeworkStore.state(store.getReadableDatabase());
        if (!settings.enabled() || !settings.configured() || !settings.homeworkAutoPlan() || !state.enabled
                || !Objects.equals(settings.account(), request.getString("account"))
                || !Objects.equals(state.account, request.getString("account")) || state.generation != request.getLong("generation")
                || current == null || current.taskId != state.captureId) return "作业来源或账号已改变";
        if (!eligible(current, now) || !baseline(current).toString().equals(request.getJSONObject("baseline").toString())
                || !store.stableCandidateId(current.id).equals(request.getString("candidateSyncId"))) return "作业已完成、修改或安排";
        try (Cursor row = store.getReadableDatabase().rawQuery("SELECT snapshot FROM zju_homework_map WHERE candidate_id=? AND ignored=0",
                new String[]{Long.toString(current.id)})) {
            if (!row.moveToFirst() || !row.getString(0).equals(request.getString("official_snapshot"))) return "官方作业已改变";
        }
        return null;
    }

    private static String input(EventCandidate item, String zone) throws org.json.JSONException {
        return new JSONObject().put("title", item.title).put("description", item.description)
                .put("official_due_local", Instant.ofEpochMilli(item.dueAtMillis).atZone(ZoneId.of(zone)).toLocalDateTime().toString())
                .put("official_due_epoch_millis", item.dueAtMillis).toString();
    }

    /** Verify a complete work window, including overnight windows and actual current time. */
    static boolean validWindow(EventCandidate planned, Long due, long now, String zone, SchedulePreferences preferences) {
        if (planned == null || due == null || planned.startAtMillis == null || planned.endAtMillis == null
                || planned.allDay || planned.repeatRule != null || planned.startAtMillis < now
                || planned.endAtMillis <= planned.startAtMillis || planned.endAtMillis > due) return false;
        ZoneId timeZone = ZoneId.of(zone);
        LocalDate day = Instant.ofEpochMilli(planned.startAtMillis).atZone(timeZone).toLocalDate();
        for (int offset = -1; offset <= 0; offset++) {
            LocalDate startDay = day.plusDays(offset);
            long start = startDay.atStartOfDay().plusMinutes(preferences.workStartMinutes).atZone(timeZone).toInstant().toEpochMilli();
            long end = startDay.plusDays(preferences.workEndMinutes <= preferences.workStartMinutes ? 1 : 0)
                    .atStartOfDay().plusMinutes(preferences.workEndMinutes).atZone(timeZone).toInstant().toEpochMilli();
            if (planned.startAtMillis >= start && planned.endAtMillis <= end) return true;
        }
        return false;
    }

    private ZjuHomeworkPlanner() { }
}
