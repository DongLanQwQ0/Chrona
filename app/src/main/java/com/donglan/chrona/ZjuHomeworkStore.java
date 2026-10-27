package com.donglan.chrona;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.zju.ZjuSnapshot;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** One authoritative device fetches homework; other devices receive ordinary task results. */
public final class ZjuHomeworkStore {
    public static final String SOURCE = "zju-homework";
    private final Context context;
    public ZjuHomeworkStore(Context context) { this.context = context.getApplicationContext(); }

    public static final class State {
        public final boolean enabled;
        public final String account;
        public final long generation, captureId;
        State(boolean enabled, String account, long generation, long captureId) {
            this.enabled = enabled; this.account = account; this.generation = generation; this.captureId = captureId;
        }
    }
    public static final class Result {
        public final int added, updated, completed, actionableAdded, actionableUpdated;
        public final List<ZjuSnapshot.Homework> newHomework;
        public final List<Long> newHomeworkIds;
        Result(int added, int updated, int completed, int actionableAdded, int actionableUpdated,
                List<ZjuSnapshot.Homework> newHomework, List<Long> newHomeworkIds) {
            this.added = added; this.updated = updated; this.completed = completed;
            this.actionableAdded = actionableAdded; this.actionableUpdated = actionableUpdated;
            this.newHomework = List.copyOf(newHomework);
            this.newHomeworkIds = List.copyOf(newHomeworkIds);
        }
    }
    public static final class History {
        public final String title, change, before, after, remoteUpdatedAt;
        public final long at, id;
        History(Cursor cursor) {
            title = cursor.getString(0); change = cursor.getString(1); at = cursor.getLong(2);
            before = cursor.getString(3); after = cursor.getString(4); remoteUpdatedAt = cursor.getString(5);
            id = cursor.getLong(6);
        }
    }

    static State state(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT enabled,account,generation,capture_id FROM managed_zju_state WHERE id=1", null)) {
            if (!cursor.moveToFirst()) throw new IllegalStateException("作业同步状态缺失");
            return new State(cursor.getInt(0) != 0, cursor.getString(1), cursor.getLong(2), cursor.isNull(3) ? 0 : cursor.getLong(3));
        }
    }
    public State state() {
        try (TaskStore store = new TaskStore(context)) { return state(store.getReadableDatabase()); }
    }

    /** Courses belonging to this exact active binding, including completed historical work. */
    public Set<String> trackedCourses(String account, long generation) {
        Set<String> result = new HashSet<>();
        try (TaskStore store = new TaskStore(context)) {
            SQLiteDatabase db = store.getReadableDatabase();
            State current = state(db);
            if (!current.enabled || current.generation != generation || !Objects.equals(current.account, account)) return result;
            try (Cursor cursor = db.rawQuery("SELECT snapshot FROM zju_homework_map WHERE account=? AND capture_id=? AND ignored=0",
                    new String[]{account, Long.toString(current.captureId)})) {
                while (cursor.moveToNext()) {
                    try {
                        String id = new JSONObject(cursor.getString(0)).optString("courseId", "");
                        if (!id.isEmpty()) result.add(id);
                    } catch (org.json.JSONException invalid) { throw new IllegalStateException("已有作业数据无效", invalid); }
                }
            }
        }
        return result;
    }

    HomeworkLinks links(EventCandidate candidate) {
        try (TaskStore store = new TaskStore(context);
             Cursor cursor = store.getReadableDatabase().rawQuery(
                     "SELECT snapshot FROM zju_homework_map WHERE candidate_id=? AND capture_id=? LIMIT 1",
                     new String[]{Long.toString(candidate.id), Long.toString(candidate.taskId)})) {
            if (cursor.moveToFirst()) {
                try {
                    JSONObject snapshot = new JSONObject(cursor.getString(0));
                    return HomeworkLinks.fromUrl(snapshot.optString("url", ""), snapshot.optString("courseId", ""));
                } catch (org.json.JSONException invalid) { return HomeworkLinks.NONE; }
            }
        }
        return HomeworkLinks.fromText(candidate.description);
    }

    /** Only an explicit settings action may create/rebind the managed capture. */
    public void configure(String account, boolean enabled) {
        account = account == null ? "" : account.trim();
        if (enabled && account.isEmpty()) throw new IllegalArgumentException("请先登录浙大账号");
        try (TaskStore store = new TaskStore(context)) {
            List<Long> alerts = new ArrayList<>(); boolean committed = false;
            SQLiteDatabase db = store.getWritableDatabase(); db.beginTransaction();
            try {
                State previous = state(db);
                for (EventCandidate item : store.getCandidates(previous.captureId)) if (item.calendarEventId != null) alerts.add(item.id);
                long capture = 0;
                try (Cursor cursor = db.rawQuery("SELECT z.capture_id FROM zju_homework_captures z JOIN tasks t ON t.id=z.capture_id WHERE z.account=? ORDER BY z.capture_id DESC LIMIT 1", new String[]{account})) {
                    if (cursor.moveToFirst()) capture = cursor.getLong(0);
                }
                if (enabled && capture == 0) {
                    capture = store.insertTask("作业", null, SOURCE, System.currentTimeMillis());
                    store.updateStatus(capture, TaskRecord.READY, null);
                    store.stableTaskId(capture);
                    db.execSQL("INSERT INTO zju_homework_captures(capture_id,account) VALUES(?,?)", new Object[]{capture, account});
                    // Explicitly re-enabling a deleted capture starts a fresh binding, not a sync resurrection.
                    db.delete("zju_homework_map", "account=?", new String[]{account});
                }
                if (previous.enabled != enabled || !Objects.equals(previous.account, account) || previous.captureId != capture) {
                    db.execSQL("UPDATE managed_zju_state SET enabled=?,account=?,generation=generation+1,capture_id=? WHERE id=1",
                            new Object[]{enabled ? 1 : 0, account, capture == 0 ? null : capture});
                }
                for (EventCandidate item : store.getCandidates(capture)) if (item.calendarEventId != null && !alerts.contains(item.id)) alerts.add(item.id);
                db.setTransactionSuccessful(); committed = true;
            } finally { db.endTransaction(); if (committed) reconcileAlerts(alerts); }
        }
    }

    public Result apply(String account, long generation, List<ZjuSnapshot.Homework> items) {
        if (items == null) throw new IllegalArgumentException("作业列表缺失");
        try (TaskStore store = new TaskStore(context)) {
            List<Long> alerts = new ArrayList<>(); boolean committed = false;
            SQLiteDatabase db = store.getWritableDatabase(); db.beginTransaction();
            try {
                State current = state(db);
                if (!current.enabled || current.generation != generation || !Objects.equals(current.account, account)
                        || current.captureId == 0 || store.getTask(current.captureId) == null) return new Result(0, 0, 0, 0, 0, List.of(), List.of());
                int added = 0, updated = 0, completed = 0, actionableAdded = 0, actionableUpdated = 0;
                List<ZjuSnapshot.Homework> newHomework = new ArrayList<>();
                List<Long> newHomeworkIds = new ArrayList<>();
                Map<Long, EventCandidate> candidates = new HashMap<>();
                for (EventCandidate candidate : store.getCandidates(current.captureId)) candidates.put(candidate.id, candidate);
                Set<String> seen = new HashSet<>();
                for (ZjuSnapshot.Homework item : items) {
                    if (item == null || item.key == null || item.key.isEmpty() || item.title == null || item.title.trim().isEmpty())
                        throw new IllegalArgumentException("作业身份或标题缺失");
                    String key = UUID.nameUUIDFromBytes((account + ":" + item.key).getBytes(StandardCharsets.UTF_8)).toString();
                    if (!seen.add(key)) throw new IllegalArgumentException("作业身份重复");
                    String before = null; long candidateId = 0; boolean ignored = false, pendingCalendar = false, unread = false;
                    try (Cursor cursor = db.rawQuery("SELECT candidate_id,ignored,snapshot,pending_calendar,unread FROM zju_homework_map WHERE account=? AND remote_key=?", new String[]{account, key})) {
                        if (cursor.moveToFirst()) { candidateId = cursor.isNull(0) ? 0 : cursor.getLong(0); ignored = cursor.getInt(1) != 0; before = cursor.getString(2); pendingCalendar = cursor.getInt(3) != 0; unread = cursor.getInt(4) != 0; }
                    }
                    if (ignored) continue;
                    String after = snapshot(item, before);
                    EventCandidate prior = candidates.get(candidateId);
                    // A removed row is never reconstructed by an ordinary fetch.
                    if (before != null && prior == null) {
                        db.execSQL("UPDATE zju_homework_map SET ignored=1 WHERE account=? AND remote_key=?", new Object[]{account, key});
                        continue;
                    }
                    Long done = prior == null ? null : prior.completedAtMillis;
                    if (Boolean.TRUE.equals(item.submitted) && done == null) { done = System.currentTimeMillis(); completed++; }
                    String description = description(item);
                    boolean sourceChanged = !Objects.equals(before, after);
                    boolean contentChanged = prior == null || !Objects.equals(prior.title, item.title) || !Objects.equals(prior.description, description)
                            || !Objects.equals(prior.dueAtMillis, item.deadline) || !prior.isHomework();
                    boolean changed = contentChanged || !Objects.equals(prior.completedAtMillis, done);
                    if (!changed && Objects.equals(before, after)) continue;
                    if (prior == null) {
                        candidateId = store.appendCandidate(new EventCandidate(0, current.captureId, item.title, null, null,
                                "Asia/Shanghai", "", description, null, false, null, "homework", false, false, 0, item.deadline, done, false));
                        added++;
                        if (done == null) { actionableAdded++; newHomework.add(item); newHomeworkIds.add(candidateId); unread = true; }
                    } else if (changed) {
                        ContentValues values = new ContentValues();
                        values.put("title", item.title); values.put("description", description);
                        values.put("category", "homework");
                        values.put("due_at_millis", item.deadline); values.put("completed_at_millis", done);
                        db.update("event_candidates", values, "id=? AND task_id=?", new String[]{Long.toString(candidateId), Long.toString(current.captureId)});
                        if (prior.calendarEventId != null && !Objects.equals(prior.completedAtMillis, done)) alerts.add(candidateId);
                        if (prior.calendarEventId != null && contentChanged) pendingCalendar = true;
                    }
                    if (prior != null) { updated++; if (sourceChanged && done == null) actionableUpdated++; }
                    String stable = store.stableCandidateId(candidateId);
                    db.execSQL("INSERT OR REPLACE INTO zju_homework_map(account,remote_key,capture_id,candidate_id,sync_id,ignored,snapshot,pending_calendar,unread) VALUES(?,?,?,?,?,0,?,?,?)",
                            new Object[]{account, key, current.captureId, candidateId, stable, after, pendingCalendar ? 1 : 0, unread ? 1 : 0});
                    if (prior == null) ZjuHomeworkPlanner.enqueue(context, store, current, candidateId, stable, after);
                    if (!Objects.equals(before, after) || changed) {
                        ContentValues history = new ContentValues();
                        history.put("account", account); history.put("title", item.title);
                        history.put("change", prior == null ? "新增作业" : !Objects.equals(prior.completedAtMillis, done) ? "已提交，自动完成" : "作业更新");
                        history.put("at_millis", System.currentTimeMillis()); history.put("before_json", before); history.put("after_json", after);
                        history.put("remote_updated_at", item.updatedAt); db.insertOrThrow("zju_homework_history", null, history);
                    }
                }
                db.setTransactionSuccessful(); committed = true; return new Result(added, updated, completed, actionableAdded, actionableUpdated, newHomework, newHomeworkIds);
            } finally { db.endTransaction(); if (committed) { reconcileAlerts(alerts); ZjuHomeworkCalendar.retryPending(context); } }
        }
    }

    /** Attention is committed with insertion, independently of notification permissions/expiry. */
    long newestUnread() {
        try (TaskStore store = new TaskStore(context)) { return newestUnread(store); }
    }

    long newestUnread(TaskStore store) {
        if (!new ZjuSettingsStore(context).enabled()) return 0;
        try (Cursor rows = store.getReadableDatabase().rawQuery("SELECT m.candidate_id FROM zju_homework_map m "
                     + "JOIN managed_zju_state s ON s.id=1 AND s.enabled=1 AND s.account=m.account AND s.capture_id=m.capture_id "
                     + "JOIN event_candidates c ON c.id=m.candidate_id WHERE m.unread=1 AND m.ignored=0 "
                     + "AND c.category='homework' AND c.completed_at_millis IS NULL ORDER BY m.candidate_id DESC LIMIT 1", null)) {
            return rows.moveToFirst() ? rows.getLong(0) : 0;
        }
    }

    void markSeen(long candidateId) {
        try (TaskStore store = new TaskStore(context)) {
            store.getWritableDatabase().execSQL("UPDATE zju_homework_map SET unread=0 WHERE candidate_id=? "
                    + "AND capture_id=(SELECT capture_id FROM managed_zju_state WHERE id=1 AND enabled=1)", new Object[]{candidateId});
        }
    }

    private void reconcileAlerts(List<Long> ids) {
        if (ids.isEmpty()) return;
        synchronized (AndroidSync.LOCK) {
            for (long id : ids) TaskCompletion.enqueue(context, id);
            TaskCompletion.retryPending(context);
        }
    }

    private static String description(ZjuSnapshot.Homework item) {
        String detail = item.description == null ? "" : item.description;
        return item.url == null || item.url.isEmpty() ? detail : detail.isEmpty() ? item.url : detail + "\n\n" + item.url;
    }
    private static String snapshot(ZjuSnapshot.Homework item, String before) {
        try {
            return new JSONObject().put("title", item.title).put("courseId", item.courseId == null ? "" : item.courseId)
                    .put("description", item.description == null ? "" : item.description).put("url", item.url == null ? "" : item.url)
                    .put("deadline", item.deadline == null ? JSONObject.NULL : item.deadline)
                    .put("submitted", item.submitted == null ? before == null ? JSONObject.NULL : new JSONObject(before).opt("submitted") : item.submitted).toString();
        } catch (org.json.JSONException exception) { throw new IllegalArgumentException("作业数据无效", exception); }
    }
    public List<History> history(int limit) { return history(limit, Long.MAX_VALUE); }
    public List<History> history(int limit, long beforeId) {
        List<History> result = new ArrayList<>();
        try (TaskStore store = new TaskStore(context)) {
            SQLiteDatabase db = store.getReadableDatabase();
            try (Cursor cursor = db.query("zju_homework_history", new String[]{"title", "change", "at_millis", "before_json", "after_json", "remote_updated_at", "id"},
                    "account=? AND id<?", new String[]{state(db).account,Long.toString(beforeId)}, null, null, "id DESC", Integer.toString(Math.max(1, Math.min(1000, limit))))) {
                while (cursor.moveToNext()) result.add(new History(cursor));
            }
        }
        return result;
    }
}
