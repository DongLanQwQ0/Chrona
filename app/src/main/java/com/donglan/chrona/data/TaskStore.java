package com.donglan.chrona.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.donglan.chrona.debug.DiagLog;
import com.donglan.chrona.image.ImageStore;

import java.util.ArrayList;
import java.util.List;
import java.text.Normalizer;
import java.util.Locale;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.io.IOException;
import java.io.File;

/** Local persistence for submitted inputs and the calendar entries proposed for each input. */
public final class TaskStore extends SQLiteOpenHelper {
    public static final int WIDGET_ITEM_LIMIT = 200;
    private static final long ALL_DAY_DURATION_MILLIS = 86_400_000L;
    private static final String DATABASE_NAME = "chrona.db";
    public static final int DATABASE_VERSION = 16;

    private final Context context;
    private final LiveDatabaseAccess.Lease liveAccess;
    private SQLiteDatabase openedDatabase;
    private long openedRevision;

    public TaskStore(Context context) {
        this(context, DATABASE_NAME);
    }

    private TaskStore(Context context, String databaseName) {
        super(context.getApplicationContext(), databaseName, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
        liveAccess = DATABASE_NAME.equals(databaseName) ? LiveDatabaseAccess.acquire() : null;
    }

    /** Independent read source for backups; never reads the live database again. */
    public static TaskStore openSnapshot(Context context, File snapshot) {
        return new TaskStore(context, snapshot.getAbsolutePath());
    }

    /** Copies all tables in one SQLite transaction, preserving deleted-ID high-water marks. */
    public void createSnapshot(File snapshot) {
        try (SQLiteDatabase target = SQLiteDatabase.openOrCreateDatabase(snapshot, null)) {
            onCreate(target);
            target.setVersion(DATABASE_VERSION);
        }
        SQLiteDatabase source = getWritableDatabase();
        source.execSQL("ATTACH DATABASE ? AS backup_snapshot",
                new Object[]{snapshot.getAbsolutePath()});
        try {
            source.beginTransaction();
            try {
                for (String table : new String[]{"tasks", "event_candidates", "task_attachments",
                        "task_files", "candidate_merges", "sync_task_map", "sync_candidate_map",
                        "sync_meta", "managed_zju_state", "zju_homework_captures", "zju_homework_map",
                        "zju_homework_history", "zju_homework_deleted_tasks", "parse_runs", "parse_time_entries", "data_revision", "sqlite_sequence"}) {
                    // ALTER TABLE appends columns; upgraded databases need not share onCreate's order.
                    List<String> columns = new ArrayList<>();
                    try (Cursor cursor = source.rawQuery("PRAGMA main.table_info(" + table + ")", null)) {
                        int nameColumn = cursor.getColumnIndexOrThrow("name");
                        while (cursor.moveToNext())
                            columns.add("\"" + cursor.getString(nameColumn).replace("\"", "\"\"") + "\"");
                    }
                    String columnNames = String.join(",", columns);
                    source.execSQL("DELETE FROM backup_snapshot." + table);
                    source.execSQL("INSERT INTO backup_snapshot." + table
                            + " (" + columnNames + ") SELECT " + columnNames + " FROM main." + table);
                }
                source.setTransactionSuccessful();
            } finally { source.endTransaction(); }
        } finally {
            source.execSQL("DETACH DATABASE backup_snapshot");
        }
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
    }

    @Override public void onOpen(SQLiteDatabase db) {
        super.onOpen(db);
        openedDatabase = db;
        openedRevision = revisionOf(db);
    }

    private static long revisionOf(SQLiteDatabase db) {
        try (Cursor cursor = db.rawQuery("SELECT revision FROM data_revision WHERE id=1", null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }

    @Override public synchronized void close() {
        boolean changed = false;
        try {
            if (openedDatabase != null && openedDatabase.isOpen())
                changed = openedRevision != revisionOf(openedDatabase);
        } finally {
            try {
                openedDatabase = null;
                super.close();
                if (changed) {
                    com.donglan.chrona.AgendaWidgetProvider.requestRefresh(context);
                    com.donglan.chrona.AgendaReminder.requestRefresh(context);
                }
            } finally { if (liveAccess != null) liveAccess.close(); }
        }
    }

    /** Bounded widget window; avoids hydrating the full task/candidate history. */
    public List<EventCandidate> calendarCandidates(long begin, long end, long allDayBegin, long allDayEnd) {
        List<EventCandidate> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                "repeat_rule IS NULL AND is_record=0 AND completed_at_millis IS NULL AND " + ScheduleQuery.EXECUTION_START_SQL + " IS NOT NULL AND ((NOT " + ScheduleQuery.EXECUTION_ALL_DAY_SQL + " AND " + ScheduleQuery.EXECUTION_START_SQL + "<CAST(? AS INTEGER) "
                        + "AND " + ScheduleQuery.CALENDAR_END_SQL + ">CAST(? AS INTEGER)) "
                        + "OR (" + ScheduleQuery.EXECUTION_ALL_DAY_SQL + " AND " + ScheduleQuery.EXECUTION_START_SQL + "<CAST(? AS INTEGER) AND COALESCE(" + ScheduleQuery.EXECUTION_END_SQL + "," + ScheduleQuery.EXECUTION_START_SQL + ")>=CAST(? AS INTEGER)))",
                new String[]{Long.toString(end), Long.toString(begin), Long.toString(allDayEnd), Long.toString(allDayBegin)},
                null, null, ScheduleQuery.EXECUTION_START_SQL + " ASC,id ASC", "5001")) {
            while (cursor.moveToNext()) result.add(readCandidate(cursor));
        }
        return mergeRepeats(result, begin, end, allDayBegin, allDayEnd, 5001);
    }

    /** Three active and four future: one extra occurrence supplies the final card's next title. */
    public List<EventCandidate> nextReminderCandidates(long now, java.util.Set<String> hidden) {
        final long startedDisplayWindow = 3 * 60_000L;
        List<EventCandidate> result = new ArrayList<>();
        String anchor = "(CASE WHEN category IN ('task','homework') THEN "
                + ScheduleQuery.TASK_DUE_SQL + " ELSE start_at_millis END)";
        String exclusion = hidden.isEmpty() ? "" : " AND ('chrona:'||id||':'||" + anchor + ") NOT IN ("
                + String.join(",", java.util.Collections.nCopies(hidden.size(), "?")) + ")";
        final long defaultLead = 30 * 60_000L;
        String appearsAt = "(" + anchor + "-CASE WHEN category='event' THEN MAX(" + defaultLead
                + ",COALESCE(reminder_minutes_before,30)*60000) ELSE " + defaultLead + " END)";
        // Keep started entries, already-due upcoming entries and the next future boundary separately.
        // A far-away activity with an early reminder must not be lost behind four nearer entries.
        for (int group = 0; group < 3; group++) {
            boolean started = group == 0;
            List<String> args = new ArrayList<>(); args.add(Long.toString(now)); args.add(Long.toString(now));
            if (!started) args.add(Long.toString(now));
            args.addAll(hidden);
            try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                    "repeat_rule IS NULL AND is_record=0 AND completed_at_millis IS NULL AND all_day=0 AND needs_confirmation=0 AND " + ZJU_REMINDER_ALLOWED + " AND " + anchor + " IS NOT NULL "
                            + "AND CASE WHEN category!='event' THEN " + anchor + "+" + startedDisplayWindow
                            + " WHEN end_at_millis>" + anchor + " THEN end_at_millis ELSE " + anchor + "+60000 END>CAST(? AS INTEGER) "
                            + "AND " + anchor + (started ? "<=" : ">") + "CAST(? AS INTEGER)"
                            + (started ? "" : " AND " + appearsAt + (group == 1 ? "<=" : ">") + "CAST(? AS INTEGER)") + exclusion,
                    args.toArray(new String[0]), null, null,
                    (started ? anchor + " DESC" : group == 1 ? anchor + " ASC" : appearsAt + " ASC")
                            + ",id ASC", started ? "3" : "4")) {
                while (cursor.moveToNext()) result.add(readCandidate(cursor));
            }
        }
        for (EventCandidate series : repeatingCandidates()) {
            if (series.allDay || series.needsConfirmation) continue;
            long duration = Math.max(3 * 60_000L, series.calendarEnd() - series.anchor());
            for (EventCandidate occurrence : RepeatOccurrences.between(series, now - duration,
                    253_402_300_799_999L, hidden.size() + 8)) {
                if (occurrence.completed() || hidden.contains("chrona:" + occurrence.occurrenceKey())) continue;
                Long reminder = occurrence.reminderAnchor();
                if (reminder != null && (occurrence.isTask() ? reminder + startedDisplayWindow : occurrence.calendarEnd()) > now)
                    result.add(occurrence);
            }
        }
        return result;
    }

    public List<EventCandidate> widgetCandidates(long begin, long end) {
        List<EventCandidate> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                "repeat_rule IS NULL AND is_record=0 AND completed_at_millis IS NULL AND " + ScheduleQuery.EXECUTION_START_SQL + " IS NOT NULL AND " + ScheduleQuery.EXECUTION_START_SQL + "<CAST(? AS INTEGER) "
                        + "AND COALESCE(" + ScheduleQuery.EXECUTION_END_SQL + "," + ScheduleQuery.EXECUTION_START_SQL + ")>=CAST(? AS INTEGER)",
                new String[]{Long.toString(end), Long.toString(begin)}, null, null,
                ScheduleQuery.EXECUTION_START_SQL + " ASC,id ASC", Integer.toString(WIDGET_ITEM_LIMIT))) {
            while (cursor.moveToNext()) result.add(readCandidate(cursor));
        }
        return mergeRepeats(result, begin, end, begin, end, WIDGET_ITEM_LIMIT);
    }

    /** Timed instants and all-day UTC dates use separate bounds before applying the limit. */
    public List<EventCandidate> widgetCandidates(long begin, long end,
            long allDayBegin, long allDayEnd) {
        List<EventCandidate> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                "repeat_rule IS NULL AND is_record=0 AND completed_at_millis IS NULL AND " + ScheduleQuery.EXECUTION_START_SQL + " IS NOT NULL AND ((NOT " + ScheduleQuery.EXECUTION_ALL_DAY_SQL + " AND " + ScheduleQuery.EXECUTION_START_SQL + "<CAST(? AS INTEGER) "
                        + "AND (COALESCE(" + ScheduleQuery.EXECUTION_END_SQL + "," + ScheduleQuery.EXECUTION_START_SQL + ")>CAST(? AS INTEGER) "
                        + "OR " + ScheduleQuery.EXECUTION_START_SQL + ">=CAST(? AS INTEGER))) "
                        + "OR (" + ScheduleQuery.EXECUTION_ALL_DAY_SQL + " AND " + ScheduleQuery.EXECUTION_START_SQL + "<CAST(? AS INTEGER) "
                        + "AND (COALESCE(" + ScheduleQuery.EXECUTION_END_SQL + "," + ScheduleQuery.EXECUTION_START_SQL + ")>CAST(? AS INTEGER) "
                        + "OR " + ScheduleQuery.EXECUTION_START_SQL + ">=CAST(? AS INTEGER))))",
                new String[]{Long.toString(end), Long.toString(begin), Long.toString(begin),
                        Long.toString(allDayEnd), Long.toString(allDayBegin),
                        Long.toString(allDayBegin)}, null, null,
                ScheduleQuery.EXECUTION_START_SQL + " ASC,id ASC", Integer.toString(WIDGET_ITEM_LIMIT))) {
            while (cursor.moveToNext()) result.add(readCandidate(cursor));
        }
        return mergeRepeats(result, begin, end, allDayBegin, allDayEnd, WIDGET_ITEM_LIMIT);
    }

    private List<EventCandidate> repeatingCandidates() {
        List<EventCandidate> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                "repeat_rule IS NOT NULL AND is_record=0", null, null, null, "id ASC")) {
            while (cursor.moveToNext()) result.add(readCandidate(cursor));
        }
        return result;
    }

    private List<EventCandidate> mergeRepeats(List<EventCandidate> result, long begin, long end,
            long allDayBegin, long allDayEnd, int limit) {
        for (EventCandidate series : repeatingCandidates())
            for (EventCandidate occurrence : RepeatOccurrences.between(series,
                    series.executionAllDay() ? allDayBegin : begin, series.executionAllDay() ? allDayEnd : end, limit))
                if (!occurrence.completed()) result.add(occurrence);
        result.sort(java.util.Comparator.comparingLong((EventCandidate e) -> ScheduleQuery.executionBegin(e)).thenComparingLong(e -> e.id));
        return result.size() <= limit ? result : new ArrayList<>(result.subList(0, limit));
    }
    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE tasks ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "raw_text TEXT NOT NULL, "
                + "image_path TEXT, "
                + "source TEXT NOT NULL, "
                + "created_at_millis INTEGER NOT NULL, "
                + "status TEXT NOT NULL CHECK(status IN "
                + "('queued','processing','needs_review','ready','failed')), "
                + "error_message TEXT, "
                + "prompt_tokens INTEGER, "
                + "completion_tokens INTEGER, "
                + "total_tokens INTEGER, "
                + "cached_tokens INTEGER, "
                + "link_text TEXT, "
                + "link_fetched_at INTEGER)");
        db.execSQL("CREATE INDEX tasks_by_created_at ON tasks(created_at_millis DESC, id DESC)");
        createTaskAttachments(db);
        createTaskFiles(db);
        db.execSQL("CREATE TABLE event_candidates ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE, "
                + "position INTEGER NOT NULL, "
                + "title TEXT NOT NULL, "
                + "start_at_millis INTEGER, "
                + "end_at_millis INTEGER, "
                + "time_zone_id TEXT, "
                + "location TEXT, "
                + "description TEXT, "
                + "reminder_minutes_before INTEGER CHECK(reminder_minutes_before IS NULL "
                + "OR reminder_minutes_before >= 0), "
                + "needs_confirmation INTEGER NOT NULL CHECK(needs_confirmation IN (0,1)), "
                + "uncertainty_level INTEGER NOT NULL DEFAULT 0 CHECK(uncertainty_level IN (0,1,2)), "
                + "calendar_event_id INTEGER, "
                + "category TEXT NOT NULL DEFAULT 'event', "
                + "all_day INTEGER NOT NULL DEFAULT 0 CHECK(all_day IN (0,1)), "
                + "end_auto_generated INTEGER NOT NULL DEFAULT 0 CHECK(end_auto_generated IN (0,1)), "
                + "due_at_millis INTEGER, completed_at_millis INTEGER, is_record INTEGER NOT NULL DEFAULT 0 CHECK(is_record IN (0,1)), "
                + "UNIQUE(task_id, position))");
        db.execSQL("CREATE INDEX candidates_by_task ON event_candidates(task_id, position)");
        addScheduleBrowsing(db);
        addCandidateMerges(db);
        createTaskIndexes(db);
        addZjuHomework(db);
        addHomeworkAttention(db);
        addRecurrence(db);
        addParseAudits(db);
    }

    /**
     * Applies migrations in order. Versions 1 and 2 are already on devices, so every later change
     * must add a step here instead of editing the older statements; existing rows must be kept.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        DiagLog.add(context, "db upgrade " + oldVersion + " -> " + newVersion);
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN image_path TEXT");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN link_text TEXT");
            db.execSQL("ALTER TABLE tasks ADD COLUMN link_fetched_at INTEGER");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE event_candidates ADD COLUMN category TEXT NOT NULL DEFAULT 'event'");
            db.execSQL("ALTER TABLE event_candidates ADD COLUMN all_day INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE tasks ADD COLUMN cached_tokens INTEGER");
        }
        if (oldVersion < 5) {
            createTaskAttachments(db);
            db.execSQL("INSERT INTO task_attachments(task_id, image_path, position) "
                    + "SELECT id, image_path, 0 FROM tasks WHERE image_path IS NOT NULL");
        }
        if (oldVersion < 6) createTaskFiles(db);
        if (oldVersion < 7) {
            addEndTimeProvenance(db);
        }
        if (oldVersion < 8) addScheduleBrowsing(db);
        if (oldVersion < 9) addUncertaintyLevel(db);
        if (oldVersion < 10) addCandidateMerges(db);
        if (oldVersion < 11) addTaskSemantics(db);
        if (oldVersion < 12) addZjuHomework(db);
        if (oldVersion < 13) db.execSQL("UPDATE event_candidates SET category='homework' WHERE category='task' AND (id IN (SELECT candidate_id FROM zju_homework_map) OR task_id IN (SELECT id FROM tasks WHERE source='zju-homework'))");
        if (oldVersion < 14) addRecurrence(db);
        if (oldVersion < 15) addHomeworkAttention(db);
        if (oldVersion < 16) addParseAudits(db);
        DiagLog.add(context, "db upgrade done, rows=" + countIn(db, "tasks"));
    }

    public static void addHomeworkAttention(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE zju_homework_map ADD COLUMN unread INTEGER NOT NULL DEFAULT 0 CHECK(unread IN (0,1))");
    }

    private static void addParseAudits(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE parse_runs (id TEXT PRIMARY KEY, task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE, "
                + "started_at INTEGER NOT NULL, finished_at INTEGER, status TEXT NOT NULL CHECK(status IN ('running','succeeded','failed','cancelled')), "
                + "request_json TEXT NOT NULL, result_json TEXT, error TEXT)");
        db.execSQL("CREATE INDEX parse_runs_by_task ON parse_runs(task_id,started_at DESC,id)");
        // Historical IDs deliberately survive candidate replacement/deletion. Deleting the source task removes its audit.
        db.execSQL("CREATE TABLE parse_time_entries (run_id TEXT NOT NULL REFERENCES parse_runs(id) ON DELETE CASCADE, "
                + "position INTEGER NOT NULL, candidate_id INTEGER NOT NULL, candidate_sync_id TEXT NOT NULL, "
                + "plan_group TEXT, evidence_json TEXT NOT NULL, PRIMARY KEY(run_id,position))");
        db.execSQL("CREATE INDEX parse_time_by_candidate ON parse_time_entries(candidate_id,run_id)");
        db.execSQL("CREATE INDEX parse_time_by_plan ON parse_time_entries(run_id,plan_group)");
    }

    public String beginParseRun(long taskId, long startedAt, org.json.JSONObject request) {
        String id = java.util.UUID.randomUUID().toString();
        ContentValues values = new ContentValues();
        values.put("id", id); values.put("task_id", taskId); values.put("started_at", startedAt);
        values.put("status", "running"); values.put("request_json", request.toString());
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // The processing coordinator owns this task exclusively. Any preceding running row
            // belongs to an interrupted process/attempt, not a concurrent request.
            finishInterruptedParseRuns(db, taskId, "InterruptedBeforeNewAttempt");
            db.insertOrThrow("parse_runs", null, values);
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return id;
    }

    /** Called only after the processing owner is gone, or on the offline restore database. */
    public static void finishInterruptedParseRuns(SQLiteDatabase db, Long taskId, String error) {
        ContentValues values = new ContentValues(); values.put("status", "failed");
        values.put("finished_at", System.currentTimeMillis()); values.put("error", error);
        db.update("parse_runs", values, taskId == null ? "status='running'" : "task_id=? AND status='running'",
                taskId == null ? null : new String[]{Long.toString(taskId)});
    }

    /** Does not overwrite a committed successful parse when later notification/calendar work fails. */
    public void failParseRun(String runId, boolean cancelled, String error) {
        ContentValues values = new ContentValues();
        values.put("status", cancelled ? "cancelled" : "failed");
        values.put("finished_at", System.currentTimeMillis()); values.put("error", error);
        getWritableDatabase().update("parse_runs", values, "id=? AND status='running'", new String[]{runId});
    }

    /** Read-only paged audit; a plan is identified by the pair (runId, planGroup), never its label alone. */
    public org.json.JSONArray getParseAudits(long taskId, Long candidateId, String runId,
            String planGroup, int offset, int limit) throws org.json.JSONException {
        if (taskId <= 0 || offset < 0 || limit < 1 || limit > 20 || (planGroup != null && runId == null))
            throw new IllegalArgumentException("Invalid audit query");
        String where = "task_id=?";
        List<String> arguments = new ArrayList<>(); arguments.add(Long.toString(taskId));
        if (runId != null) { where += " AND id=?"; arguments.add(runId); }
        if (candidateId != null || planGroup != null) {
            where += " AND EXISTS(SELECT 1 FROM parse_time_entries e WHERE e.run_id=parse_runs.id";
            if (candidateId != null) { where += " AND e.candidate_id=?"; arguments.add(Long.toString(candidateId)); }
            if (planGroup != null) { where += " AND e.plan_group=?"; arguments.add(planGroup); }
            where += ")";
        }
        org.json.JSONArray result = new org.json.JSONArray();
        try (Cursor runs = getReadableDatabase().query("parse_runs", null, where,
                arguments.toArray(new String[0]), null, null, "started_at DESC,id DESC", offset + "," + limit)) {
            while (runs.moveToNext()) {
                String id = runs.getString(runs.getColumnIndexOrThrow("id"));
                org.json.JSONObject value = new org.json.JSONObject().put("runId", id).put("taskId", taskId)
                        .put("startedAt", runs.getLong(runs.getColumnIndexOrThrow("started_at")))
                        .put("finishedAt", jsonColumn(runs, "finished_at"))
                        .put("status", runs.getString(runs.getColumnIndexOrThrow("status")))
                        .put("request", new org.json.JSONObject(runs.getString(runs.getColumnIndexOrThrow("request_json"))))
                        .put("error", jsonColumn(runs, "error"));
                String raw = runs.getString(runs.getColumnIndexOrThrow("result_json"));
                value.put("result", raw == null ? org.json.JSONObject.NULL : new org.json.JSONObject(raw));
                org.json.JSONArray entries = new org.json.JSONArray();
                String filter = "run_id=?"; List<String> args = new ArrayList<>(); args.add(id);
                if (candidateId != null) { filter += " AND candidate_id=?"; args.add(Long.toString(candidateId)); }
                if (planGroup != null) { filter += " AND plan_group=?"; args.add(planGroup); }
                try (Cursor rows = getReadableDatabase().query("parse_time_entries", null, filter,
                        args.toArray(new String[0]), null, null, "position")) {
                    while (rows.moveToNext()) entries.put(new org.json.JSONObject()
                            .put("candidateId", rows.getLong(rows.getColumnIndexOrThrow("candidate_id")))
                            .put("candidateSyncId", rows.getString(rows.getColumnIndexOrThrow("candidate_sync_id")))
                            .put("evidence", new org.json.JSONObject(rows.getString(rows.getColumnIndexOrThrow("evidence_json")))));
                }
                result.put(value.put("entries", entries));
            }
        }
        return result;
    }

    private static Object jsonColumn(Cursor cursor, String name) {
        int index = cursor.getColumnIndexOrThrow(name);
        if (cursor.isNull(index)) return org.json.JSONObject.NULL;
        return name.equals("finished_at") ? cursor.getLong(index) : cursor.getString(index);
    }

    private static void addRecurrence(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE event_candidates ADD COLUMN repeat_rule TEXT");
        db.execSQL("ALTER TABLE event_candidates ADD COLUMN repeat_exceptions TEXT NOT NULL DEFAULT '{}'");
        db.execSQL("ALTER TABLE event_candidates ADD COLUMN repeat_stopped_at INTEGER");
    }

    private static void putRepeat(ContentValues values, EventCandidate candidate) {
        if (candidate.occurrenceAtMillis != null)
            throw new IllegalArgumentException("请使用单次日程操作，不能将单次覆盖整个重复系列");
        if (candidate.repeatRule != null) {
            if (candidate.isRecord || candidate.anchor() == null) throw new IllegalArgumentException("重复日程需要首次时间");
            candidate.repeatRule.validateAnchor(candidate.anchor(), candidate.timeZoneId);
        }
        values.put("repeat_rule", candidate.repeatRule == null ? null : candidate.repeatRule.toJson().toString());
        values.put("repeat_exceptions", candidate.repeatExceptions);
        values.put("repeat_stopped_at", candidate.repeatStoppedAtMillis);
    }

    /** Managed-source metadata remains local; portable task results use normal sync identities. */
    public static void addZjuHomework(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE managed_zju_state(id INTEGER PRIMARY KEY CHECK(id=1),enabled INTEGER NOT NULL DEFAULT 0,generation INTEGER NOT NULL DEFAULT 0,account TEXT NOT NULL DEFAULT '',capture_id INTEGER)");
        db.execSQL("INSERT INTO managed_zju_state(id) VALUES(1)");
        db.execSQL("CREATE TABLE zju_homework_captures(capture_id INTEGER PRIMARY KEY,account TEXT NOT NULL)");
        db.execSQL("CREATE TABLE zju_homework_map(account TEXT NOT NULL,remote_key TEXT NOT NULL,capture_id INTEGER NOT NULL,candidate_id INTEGER,sync_id TEXT,ignored INTEGER NOT NULL DEFAULT 0,snapshot TEXT NOT NULL,pending_calendar INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(account,remote_key))");
        db.execSQL("CREATE INDEX zju_homework_candidate ON zju_homework_map(candidate_id)");
        db.execSQL("CREATE TABLE zju_homework_history(id INTEGER PRIMARY KEY AUTOINCREMENT,account TEXT NOT NULL,title TEXT NOT NULL,change TEXT NOT NULL,at_millis INTEGER NOT NULL,before_json TEXT,after_json TEXT,remote_updated_at TEXT)");
        db.execSQL("CREATE TABLE zju_homework_deleted_tasks(sync_id TEXT PRIMARY KEY)");
        // Triggers cover UI deletion, sync tombstones and direct SQL equally.
        db.execSQL("CREATE TRIGGER zju_capture_deleted BEFORE DELETE ON tasks BEGIN "
                + "INSERT OR IGNORE INTO zju_homework_deleted_tasks SELECT sync_id FROM sync_task_map WHERE local_id=OLD.id AND EXISTS(SELECT 1 FROM zju_homework_captures WHERE capture_id=OLD.id); "
                + "UPDATE managed_zju_state SET enabled=0,generation=generation+1,capture_id=NULL WHERE capture_id=OLD.id; END");
        db.execSQL("CREATE TRIGGER zju_candidate_deleted BEFORE DELETE ON event_candidates BEGIN "
                + "UPDATE zju_homework_map SET ignored=1 WHERE candidate_id=OLD.id; END");
        for (String table : new String[]{"managed_zju_state", "zju_homework_map", "zju_homework_history", "zju_homework_captures", "zju_homework_deleted_tasks"})
            for (String operation : new String[]{"INSERT", "UPDATE", "DELETE"})
                db.execSQL("CREATE TRIGGER revision_" + table + "_" + operation + " AFTER " + operation + " ON " + table + " BEGIN UPDATE data_revision SET revision=revision+1 WHERE id=1; END");
    }

    public boolean ignoredZjuCandidate(String stableId) {
        return scalar("SELECT 1 FROM zju_homework_map WHERE sync_id=? AND ignored=1 LIMIT 1", stableId) != null;
    }

    public boolean deletedZjuTask(String stableId) {
        return scalar("SELECT 1 FROM zju_homework_deleted_tasks WHERE sync_id=?", stableId) != null;
    }

    /** Disabling a source keeps its results visible while stopping its local reminders. */
    public boolean allowsReminder(long candidateId) {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT 1 FROM event_candidates WHERE id=? AND " + ZJU_REMINDER_ALLOWED, new String[]{Long.toString(candidateId)})) {
            return cursor.moveToFirst();
        }
    }

    private static final String ZJU_REMINDER_ALLOWED = "(NOT EXISTS(SELECT 1 FROM zju_homework_captures z WHERE z.capture_id=task_id) OR EXISTS(SELECT 1 FROM managed_zju_state s WHERE s.enabled=1 AND s.capture_id=task_id)) AND (" + ScheduleQuery.TASK_DUE_SQL + " IS NOT NULL OR NOT EXISTS(SELECT 1 FROM zju_homework_map m WHERE m.candidate_id=event_candidates.id))";

    /** One-time conversion preserves source rows, IDs, calendar associations and explicit times. */
    public static void addTaskSemantics(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE event_candidates ADD COLUMN due_at_millis INTEGER");
        db.execSQL("ALTER TABLE event_candidates ADD COLUMN completed_at_millis INTEGER");
        db.execSQL("ALTER TABLE event_candidates ADD COLUMN is_record INTEGER NOT NULL DEFAULT 0 CHECK(is_record IN (0,1))");
        db.execSQL("UPDATE event_candidates SET is_record=1,category='event',needs_confirmation=0,uncertainty_level=0 WHERE category='note'");
        db.execSQL("UPDATE event_candidates SET due_at_millis=COALESCE(end_at_millis,start_at_millis) WHERE category='deadline'");
        db.execSQL("UPDATE event_candidates SET start_at_millis=NULL,end_at_millis=NULL,end_auto_generated=0 WHERE category='deadline' AND (start_at_millis IS NULL OR end_at_millis-start_at_millis<=60000)");
        db.execSQL("UPDATE event_candidates SET end_at_millis=NULL,end_auto_generated=0 WHERE category IN ('task','reminder') AND end_auto_generated=1");
        db.execSQL("UPDATE event_candidates SET category='task' WHERE category IN ('reminder','deadline')");
        try (Cursor cursor=db.rawQuery("SELECT source_candidate,record FROM candidate_merges",null)) {
            while(cursor.moveToNext()) {
                try {
                    org.json.JSONObject archived=new org.json.JSONObject(cursor.getString(1));
                    org.json.JSONObject item=archived.getJSONObject("source").getJSONObject("candidate");
                    String kind=item.getString("category");
                    item.put("record","note".equals(kind)).put("completedAt",org.json.JSONObject.NULL)
                            .put("dueAt","deadline".equals(kind)?item.opt("endAt")!=org.json.JSONObject.NULL?item.opt("endAt"):item.opt("startAt"):org.json.JSONObject.NULL);
                    item.put("category","note".equals(kind)||"event".equals(kind)?"event":"task");
                    ContentValues values=new ContentValues();values.put("record",archived.toString());
                    db.update("candidate_merges",values,"source_candidate=?",new String[]{cursor.getString(0)});
                } catch(org.json.JSONException invalid) { throw new IllegalStateException("合并来源记录无法转换，原数据保留",invalid); }
            }
        }
        createTaskIndexes(db);
        db.execSQL("UPDATE tasks SET status='ready',error_message=NULL WHERE status='needs_review' AND EXISTS(SELECT 1 FROM event_candidates WHERE task_id=tasks.id) AND NOT EXISTS(SELECT 1 FROM event_candidates WHERE task_id=tasks.id AND is_record=0 AND needs_confirmation=1)");
    }

    private static void createTaskIndexes(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS candidates_by_due ON event_candidates(due_at_millis,id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS candidates_by_anchor ON event_candidates(COALESCE(start_at_millis,due_at_millis),id)");
    }

    /** Shared with staged backup migration; old ambiguous records have no finer provenance. */
    public static void addUncertaintyLevel(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE event_candidates ADD COLUMN uncertainty_level INTEGER NOT NULL DEFAULT 0 CHECK(uncertainty_level IN (0,1,2))");
        db.execSQL("UPDATE event_candidates SET uncertainty_level=2 WHERE needs_confirmation=1");
    }

    /** Also used when upgrading a staged v6 backup before installation. */
    public static void addEndTimeProvenance(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE event_candidates ADD COLUMN end_auto_generated INTEGER NOT NULL DEFAULT 0 CHECK(end_auto_generated IN (0,1))");
    }

    /** Shared with staged backup migration. Revision avoids loading all rows during polling. */
    public static void addScheduleBrowsing(SQLiteDatabase db) {
        db.execSQL("CREATE INDEX IF NOT EXISTS candidates_by_start ON event_candidates(start_at_millis,id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS candidates_by_end ON event_candidates(end_at_millis,id)");
        db.execSQL("CREATE INDEX IF NOT EXISTS tasks_by_status ON tasks(status)");
        db.execSQL("CREATE TABLE IF NOT EXISTS data_revision (id INTEGER PRIMARY KEY CHECK(id=1), revision INTEGER NOT NULL)");
        db.execSQL("INSERT OR IGNORE INTO data_revision VALUES(1,0)");
        for (String table : new String[]{"tasks", "event_candidates", "task_attachments", "task_files"}) {
            for (String operation : new String[]{"INSERT", "UPDATE", "DELETE"}) {
                db.execSQL("CREATE TRIGGER IF NOT EXISTS revision_" + table + "_" + operation
                        + " AFTER " + operation + " ON " + table
                        + " BEGIN UPDATE data_revision SET revision=revision+1 WHERE id=1; END");
            }
        }
    }

    public long dataRevision() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT revision FROM data_revision WHERE id=1", null)) {
            return cursor.moveToFirst() ? cursor.getLong(0) : 0;
        }
    }
    /** Local audit survives task deletion; stable IDs are shared with WebDAV. */
    public static void addCandidateMerges(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_task_map(sync_id TEXT PRIMARY KEY,local_id INTEGER UNIQUE,baseline TEXT,baseline_clock TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_candidate_map(local_id INTEGER PRIMARY KEY,sync_id TEXT UNIQUE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_meta(id INTEGER PRIMARY KEY CHECK(id=1),identity TEXT NOT NULL)");
        db.execSQL("INSERT OR IGNORE INTO sync_meta(id,identity) VALUES(1,?)",new Object[]{java.util.UUID.randomUUID().toString()});
        db.execSQL("CREATE TABLE IF NOT EXISTS candidate_merges(source_candidate TEXT PRIMARY KEY,record TEXT NOT NULL,calendar_event_id INTEGER)");
        for(String operation:new String[]{"INSERT","UPDATE","DELETE"})db.execSQL("CREATE TRIGGER IF NOT EXISTS revision_candidate_merges_"+operation+" AFTER "+operation+" ON candidate_merges BEGIN UPDATE data_revision SET revision=revision+1 WHERE id=1; END");
    }
    private String scalar(String sql,String argument) {
        try(Cursor cursor=getReadableDatabase().rawQuery(sql,new String[]{argument})) {return cursor.moveToFirst()?cursor.getString(0):null;}
    }
    public String stableTaskId(long local) {
        String key=Long.toString(local),existing=scalar("SELECT sync_id FROM sync_task_map WHERE local_id=?",key);
        if(existing==null){String identity=scalar("SELECT identity FROM sync_meta WHERE id=?","1");String value="task_"+java.util.UUID.nameUUIDFromBytes((identity+":task:"+local).getBytes(java.nio.charset.StandardCharsets.UTF_8));getWritableDatabase().execSQL("INSERT OR IGNORE INTO sync_task_map(sync_id,local_id) VALUES(?,?)",new Object[]{value,local});existing=scalar("SELECT sync_id FROM sync_task_map WHERE local_id=?",key);}return existing;
    }
    public String stableCandidateId(long local) {
        String key=Long.toString(local),existing=scalar("SELECT sync_id FROM sync_candidate_map WHERE local_id=?",key);
        if(existing==null){String identity=scalar("SELECT identity FROM sync_meta WHERE id=?","1");String value=java.util.UUID.nameUUIDFromBytes((identity+":candidate:"+local).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();getWritableDatabase().execSQL("INSERT OR IGNORE INTO sync_candidate_map(local_id,sync_id) VALUES(?,?)",new Object[]{local,value});existing=scalar("SELECT sync_id FROM sync_candidate_map WHERE local_id=?",key);}return existing;
    }
    public long localTaskId(String stable) {String value=scalar("SELECT local_id FROM sync_task_map WHERE sync_id=?",stable);return value==null?0:Long.parseLong(value);}
    public long localCandidateId(String stable) {String value=scalar("SELECT local_id FROM sync_candidate_map WHERE sync_id=?",stable);return value==null?0:Long.parseLong(value);}
    /** Existing restored entities keep their UUIDs; future reused local IDs get a new namespace. */
    public void rotateRestoredSyncIdentity() {
        if(!getWritableDatabase().inTransaction())throw new IllegalStateException("恢复身份必须在事务中更新");
        for(TaskRecord task:listTasks()){
            stableTaskId(task.id);
            for(EventCandidate candidate:getCandidates(task.id))stableCandidateId(candidate.id);
        }
        getWritableDatabase().execSQL("UPDATE sync_meta SET identity=? WHERE id=1",new Object[]{java.util.UUID.randomUUID().toString()});
    }
    public com.donglan.chrona.sync.MergeLedger mergeLedger() throws Exception {
        com.donglan.chrona.sync.MergeLedger ledger=new com.donglan.chrona.sync.MergeLedger();
        try(Cursor cursor=getReadableDatabase().rawQuery("SELECT record FROM candidate_merges ORDER BY source_candidate",null)){while(cursor.moveToNext())ledger.add(new org.json.JSONObject(cursor.getString(0)));}return ledger;
    }
    public void acceptMerge(org.json.JSONObject incoming) throws Exception {
        if(!getWritableDatabase().inTransaction())throw new IllegalStateException("合并记录必须在事务中写入");
        org.json.JSONObject record=com.donglan.chrona.sync.TaskCodec.validate(incoming);String source=record.getString("sourceCandidate");
        com.donglan.chrona.sync.TaskCodec.validateId("merge_"+source,record);
        String previous=scalar("SELECT record FROM candidate_merges WHERE source_candidate=?",source);
        org.json.JSONObject chosen=com.donglan.chrona.sync.MergeLedger.preferred(previous==null?null:new org.json.JSONObject(previous),record);
        String candidate=scalar("SELECT local_id FROM sync_candidate_map WHERE sync_id=?",source);Long calendar=null;
        if(candidate!=null){String task=scalar("SELECT task_id FROM event_candidates WHERE id=?",candidate);if(task!=null){if(!stableTaskId(Long.parseLong(task)).equals(record.getString("sourceTask")))throw new IOException("合并来源与本机日程身份不一致");String linked=scalar("SELECT calendar_event_id FROM event_candidates WHERE id=?",candidate);if(linked!=null)calendar=Long.parseLong(linked);}}
        if(previous==null||!com.donglan.chrona.sync.SyncState.canonical(chosen).equals(com.donglan.chrona.sync.SyncState.canonical(new org.json.JSONObject(previous)))){
            ContentValues values=new ContentValues();values.put("source_candidate",source);values.put("record",chosen.toString());if(calendar!=null)values.put("calendar_event_id",calendar);
            if(previous==null)getWritableDatabase().insertOrThrow("candidate_merges",null,values);else getWritableDatabase().update("candidate_merges",values,"source_candidate=?",new String[]{source});
        }else if(calendar!=null){ContentValues values=new ContentValues();values.put("calendar_event_id",calendar);getWritableDatabase().update("candidate_merges",values,"source_candidate=? AND calendar_event_id IS NULL",new String[]{source});}
        if(candidate!=null)getWritableDatabase().delete("event_candidates","id=?",new String[]{candidate});
        String baseline=scalar("SELECT baseline FROM sync_task_map WHERE sync_id=?",record.getString("sourceTask"));
        if(baseline!=null&&!baseline.equals("null")){
            // This deletion is sync metadata, not a new user edit. Preserve all other fields
            // and its applied clock so real local raw-text/attachment changes still conflict.
            org.json.JSONObject payload=new org.json.JSONObject(baseline);
            String filtered=com.donglan.chrona.sync.SyncState.canonical(withoutMergedCandidates(payload));
            getWritableDatabase().execSQL("UPDATE sync_task_map SET baseline=? WHERE sync_id=?",new Object[]{filtered,record.getString("sourceTask")});
        }
    }

    public org.json.JSONObject withoutMergedCandidates(org.json.JSONObject payload)throws Exception {
        org.json.JSONObject result=new org.json.JSONObject(payload.toString());
        org.json.JSONArray input=result.getJSONArray("candidates"),kept=new org.json.JSONArray();
        com.donglan.chrona.sync.MergeLedger ledger=mergeLedger();
        for(int i=0;i<input.length();i++)if(!ledger.removed(input.getJSONObject(i).getString("id")) && !ignoredZjuCandidate(input.getJSONObject(i).getString("id")))kept.put(input.getJSONObject(i));
        return result.put("candidates",kept);
    }

    public int taskCountByStatus(String status) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM tasks WHERE status=?", new String[]{status})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public List<EventCandidate> queryScheduleFirst(ScheduleQuery query, int limit) {
        return scheduleRows(query, true, Math.max(1, limit));
    }

    private List<EventCandidate> scheduleRows(ScheduleQuery query, boolean oldestFirst, int limit) {
        List<EventCandidate> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                "repeat_rule IS NULL AND (" + query.selection + ")", query.arguments, null, null, query.orderBy(oldestFirst),
                Integer.toString(Math.max(1, limit)))) {
            while (cursor.moveToNext()) items.add(readCandidate(cursor));
        }
        for (EventCandidate series : repeatingCandidates()) {
            EventCandidate row = query.seriesRow(series);
            if (row != null) items.add(row);
        }
        items.sort(query.comparator(oldestFirst));
        return items.size() <= limit ? items : new ArrayList<>(items.subList(0, limit));
    }

    /** Homework deadlines are milestones; ordinary due-only tasks use their derived execution interval. */
    public List<EventCandidate> queryHomeUpcoming(long now, java.time.ZoneId zone, int limit) {
        long todayUtc = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
                .atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli();
        String selection = "repeat_rule IS NULL AND is_record=0 AND completed_at_millis IS NULL AND ((start_at_millis IS NOT NULL"
                + " AND (category IN ('task','homework') OR (category='event' AND end_at_millis>start_at_millis))"
                + " AND ((all_day=0 AND (CASE WHEN end_at_millis>start_at_millis THEN end_at_millis>CAST(? AS INTEGER)"
                + " ELSE start_at_millis>=CAST(? AS INTEGER) END))"
                + " OR (all_day=1 AND (CASE WHEN end_at_millis>start_at_millis THEN end_at_millis"
                + " ELSE start_at_millis+" + ALL_DAY_DURATION_MILLIS + " END)>CAST(? AS INTEGER))))"
                + " OR (category='homework' AND start_at_millis IS NULL AND due_at_millis>=CAST(? AS INTEGER))"
                + " OR (" + ScheduleQuery.DEFAULT_EXECUTION_SQL + " AND due_at_millis>CAST(? AS INTEGER)))";
        String order = ScheduleQuery.DAY_SQL + " ASC, " + ScheduleQuery.EXECUTION_ALL_DAY_SQL + " DESC, " + ScheduleQuery.EXECUTION_START_SQL + " ASC, id ASC";
        List<EventCandidate> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null, selection,
                new String[]{Long.toString(now), Long.toString(now), Long.toString(todayUtc), Long.toString(now), Long.toString(now)}, null, null, order,
                Integer.toString(Math.max(1, limit)))) {
            while (cursor.moveToNext()) items.add(readCandidate(cursor));
        }
        for (EventCandidate series : repeatingCandidates()) {
            if (series.executionStartAtMillis() == null && !series.isHomework()) continue;
            for (EventCandidate occurrence : RepeatOccurrences.between(series, series.executionAllDay() ? todayUtc : now,
                    253_402_300_799_999L, Math.max(1, limit)))
                if (!occurrence.completed()) items.add(occurrence);
        }
        items.sort(java.util.Comparator.comparingLong((EventCandidate e) -> ScheduleQuery.executionBegin(e)).thenComparingLong(e -> e.id));
        return items.size() <= limit ? items : new ArrayList<>(items.subList(0, limit));
    }

    public HomeTodoSummary homeTodoSummary(long now, java.time.ZoneId zone, int focusLimit) {
        java.time.LocalDate today = java.time.Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
        long dayStart = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long dayEnd = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        String open = "repeat_rule IS NULL AND is_record=0 AND category IN ('task','homework') AND completed_at_millis IS NULL";
        String due = ScheduleQuery.TASK_DUE_SQL;
        int total = 0, todayDue = 0, overdue = 0, unscheduled = 0;
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*),"
                + " SUM(CASE WHEN " + due + ">=CAST(? AS INTEGER) AND " + due + "<CAST(? AS INTEGER) THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN " + due + "<CAST(? AS INTEGER) THEN 1 ELSE 0 END),"
                + " SUM(CASE WHEN " + due + " IS NULL THEN 1 ELSE 0 END)"
                + " FROM event_candidates WHERE " + open,
                new String[]{Long.toString(dayStart), Long.toString(dayEnd), Long.toString(now)})) {
            if (cursor.moveToFirst()) {
                total = cursor.getInt(0); todayDue = cursor.getInt(1);
                overdue = cursor.getInt(2); unscheduled = cursor.getInt(3);
            }
        }
        List<EventCandidate> focus = new ArrayList<>();
        // Today's deadlines first, then overdue work, then the remaining dated and undated tasks.
        String priority = "CASE WHEN " + due + ">=" + dayStart + " AND " + due + "<" + dayEnd
                + " THEN 0 WHEN " + due + "<" + now + " THEN 1 WHEN " + due + " IS NOT NULL"
                + " THEN 2 ELSE 3 END";
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null, open, null,
                null, null, priority + ", " + due + " ASC, id ASC",
                Integer.toString(Math.max(1, focusLimit)))) {
            while (cursor.moveToNext()) focus.add(readCandidate(cursor));
        }
        ScheduleQuery repeatedOpen = new ScheduleQuery(3, null, 0, "", null, null, now, zone, ScheduleQuery.TODO_OPEN);
        for (EventCandidate series : repeatingCandidates()) {
            EventCandidate row = repeatedOpen.seriesRow(series);
            if (row == null) continue;
            total++;
            Long deadline = row.effectiveDueAtMillis();
            if (deadline != null && deadline >= dayStart && deadline < dayEnd) todayDue++;
            if (deadline != null && deadline < now) overdue++;
            focus.add(row);
        }
        focus.sort(java.util.Comparator.comparingInt((EventCandidate e) -> {
            Long deadline = e.effectiveDueAtMillis();
            return deadline == null ? 3 : deadline >= dayStart && deadline < dayEnd ? 0 : deadline < now ? 1 : 2;
        }).thenComparing(EventCandidate::effectiveDueAtMillis, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                .thenComparingLong(e -> e.id));
        if (focus.size() > focusLimit) focus = new ArrayList<>(focus.subList(0, Math.max(0, focusLimit)));
        return new HomeTodoSummary(total, todayDue, overdue, unscheduled, focus);
    }

    public List<Long> queryScheduleIds(ScheduleQuery query) {
        List<Long> ids = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", new String[]{"id"},
                "repeat_rule IS NULL AND (" + query.selection + ")", query.arguments, null, null, "id ASC")) {
            while (cursor.moveToNext()) ids.add(cursor.getLong(0));
        }
        for (EventCandidate series : repeatingCandidates()) if (query.seriesRow(series) != null) ids.add(series.id);
        return ids;
    }

    public List<EventCandidate> querySchedulePage(ScheduleQuery query, boolean oldestFirst, int page) {
        long offset = (long) Math.max(0, page) * ScheduleQuery.PAGE_SIZE;
        int end = (int) Math.min(Integer.MAX_VALUE, offset + ScheduleQuery.PAGE_SIZE);
        List<EventCandidate> items = scheduleRows(query, oldestFirst, end);
        return offset >= items.size() ? new ArrayList<>() : new ArrayList<>(items.subList((int) offset, items.size()));
    }

    /** Explicit deep navigation uses the same projected order as the visible paginated list. */
    public int schedulePageForCandidate(ScheduleQuery query, boolean oldestFirst, long candidateId) {
        List<EventCandidate> rows = scheduleRows(query, oldestFirst, Integer.MAX_VALUE);
        for (int i = 0; i < rows.size(); i++) if (rows.get(i).id == candidateId) return i / ScheduleQuery.PAGE_SIZE;
        return -1;
    }

    public List<EventCandidate> getCandidatesByIds(java.util.Collection<Long> ids) {
        List<EventCandidate> items = new ArrayList<>();
        java.util.Iterator<Long> iterator = ids.iterator();
        // Stay below old SQLite versions' bind parameter limit during large bulk selections.
        final int batchSize = 400;
        while (iterator.hasNext()) {
            List<String> values = new ArrayList<>();
            while (iterator.hasNext() && values.size() < batchSize)
                values.add(Long.toString(iterator.next()));
            String placeholders = String.join(",", java.util.Collections.nCopies(values.size(), "?"));
            try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                    "id IN (" + placeholders + ")", values.toArray(new String[0]), null, null, "id ASC")) {
                while (cursor.moveToNext()) items.add(readCandidate(cursor));
            }
        }
        return items;
    }

    public List<Long> linkedCalendarIds() {
        List<Long> ids = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT calendar_event_id FROM event_candidates WHERE calendar_event_id IS NOT NULL UNION SELECT calendar_event_id FROM candidate_merges WHERE calendar_event_id IS NOT NULL", null)) {
            while (cursor.moveToNext()) ids.add(cursor.getLong(0));
        }
        return ids;
    }

    private static void createTaskAttachments(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE task_attachments ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "task_id INTEGER NOT NULL, "
                + "image_path TEXT NOT NULL, "
                + "position INTEGER NOT NULL, "
                + "UNIQUE(task_id, position))");
        db.execSQL("CREATE INDEX attachments_by_task ON task_attachments(task_id, position)");
    }

    private static void createTaskFiles(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE task_files ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "task_id INTEGER NOT NULL REFERENCES tasks(id) ON DELETE CASCADE, "
                + "stored_name TEXT NOT NULL, "
                + "display_name TEXT NOT NULL, "
                + "mime_type TEXT NOT NULL, "
                + "size_bytes INTEGER NOT NULL CHECK(size_bytes >= 0), "
                + "position INTEGER NOT NULL, "
                + "UNIQUE(task_id, position))");
        db.execSQL("CREATE INDEX files_by_task ON task_files(task_id, position)");
    }

    /** One line per fact about the local database; read by the debug screen. */
    public String describe() {
        StringBuilder text = new StringBuilder();
        text.append("数据库: user_version=").append(schemaVersion()).append('\n');
        text.append("表列: ");
        try (Cursor cursor = getReadableDatabase().rawQuery("PRAGMA table_info(tasks)", null)) {
            boolean first = true;
            while (cursor.moveToNext()) {
                if (!first) text.append(',');
                text.append(cursor.getString(cursor.getColumnIndexOrThrow("name")));
                first = false;
            }
        }
        text.append('\n');
        text.append("记录: tasks=").append(count("tasks"))
                .append(" candidates=").append(count("event_candidates"))
                .append(" 带图片=").append(countWhere("tasks", "image_path IS NOT NULL"))
                .append(" 带抓取正文=").append(countWhere("tasks", "link_text IS NOT NULL"))
                .append('\n');
        return text.toString();
    }

    private int schemaVersion() {
        try (Cursor cursor = getReadableDatabase().rawQuery("PRAGMA user_version", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        }
    }

    private int count(String table) {
        return countIn(getReadableDatabase(), table);
    }

    /** Counts through the given handle; onUpgrade must not re-enter getReadableDatabase. */
    private static int countIn(SQLiteDatabase db, String table) {
        try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + table, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    private int countWhere(String table, String where) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM " + table + " WHERE " + where, null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    /** Saves one input in queued state and returns its database ID. */
    public long insertTask(String rawText, String imagePath, String source, long createdAtMillis) {
        return insertTask(rawText, imagePath, new ArrayList<>(), source, createdAtMillis);
    }

    /** Saves an input and its private file attachments atomically. */
    public long insertTask(String rawText, String imagePath, List<TaskFileAttachment> files,
            String source, long createdAtMillis) {
        boolean hasText = rawText != null && !rawText.trim().isEmpty();
        if (imagePath != null && !ImageStore.isStoredName(imagePath)) {
            throw new IllegalArgumentException("Unknown attached image");
        }
        List<TaskFileAttachment> attachments = files == null ? new ArrayList<>() : files;
        if (!hasText && imagePath == null && attachments.isEmpty()) {
            throw new IllegalArgumentException("An input needs text or an attachment");
        }
        for (TaskFileAttachment file : attachments) validateFileAttachment(file);
        requireNonEmpty(source, "source");
        ContentValues values = new ContentValues();
        values.put("raw_text", hasText ? rawText : "");
        values.put("image_path", imagePath);
        values.put("source", source);
        values.put("created_at_millis", createdAtMillis);
        values.put("status", TaskRecord.QUEUED);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            long taskId = db.insertOrThrow("tasks", null, values);
            if (imagePath != null) {
                ContentValues attachment = new ContentValues();
                attachment.put("task_id", taskId);
                attachment.put("image_path", imagePath);
                attachment.put("position", 0);
                db.insertOrThrow("task_attachments", null, attachment);
            }
            insertTaskFiles(db, taskId, attachments);
            db.setTransactionSuccessful();
            return taskId;
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Saves one batch atomically and returns its IDs in submission order. Entries are stamped a
     * millisecond apart so the newest-first inbox lists a batch in the order it was pasted.
     */
    public List<Long> insertTasks(List<String> rawTexts, String source, long createdAtMillis) {
        if (rawTexts == null || rawTexts.isEmpty()) {
            throw new IllegalArgumentException("rawTexts must not be empty");
        }
        requireNonEmpty(source, "source");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            List<Long> taskIds = new ArrayList<>(rawTexts.size());
            for (int position = 0; position < rawTexts.size(); position++) {
                String rawText = rawTexts.get(position);
                requireNonEmpty(rawText, "rawText");
                ContentValues values = new ContentValues();
                values.put("raw_text", rawText);
                values.put("source", source);
                values.put("created_at_millis", createdAtMillis + rawTexts.size() - 1 - position);
                values.put("status", TaskRecord.QUEUED);
                taskIds.add(db.insertOrThrow("tasks", null, values));
            }
            db.setTransactionSuccessful();
            return taskIds;
        } finally {
            db.endTransaction();
        }
    }

    /** Finds an existing capture with the same normalized text and attachment identities. */
    public TaskRecord findDuplicateTask(String rawText, String imagePath,
            List<TaskFileAttachment> files) throws IOException {
        String normalizedText = normalizeInputText(rawText);
        String incomingAttachments = attachmentIdentity(
                imagePath == null ? new ArrayList<>() : java.util.Collections.singletonList(imagePath),
                files == null ? new ArrayList<>() : files);
        for (TaskRecord candidate : listTasks()) {
            if (!normalizedText.equals(normalizeInputText(candidate.rawText))) continue;
            String existingAttachments = attachmentIdentity(getImagePaths(candidate.id),
                    getFileAttachments(candidate.id));
            if (incomingAttachments.equals(existingAttachments)) return candidate;
        }
        return null;
    }

    /** Canonical text form shared with batch duplicate detection. */
    public static String normalizeInputText(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim()
                .replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private String attachmentIdentity(List<String> images, List<TaskFileAttachment> files)
            throws IOException {
        StringBuilder identity = new StringBuilder();
        identity.append("images:").append(images.size()).append(';');
        ImageStore imageStore = new ImageStore(context);
        for (String image : images) {
            identity.append(sha256(imageStore.read(image))).append(';');
        }
        identity.append("files:").append(files.size()).append(';');
        for (TaskFileAttachment file : files) {
            identity.append(normalizeInputText(file.displayName)).append('|')
                    .append(normalizeInputText(file.mimeType)).append('|')
                    .append(file.sizeBytes).append(';');
        }
        return identity.toString();
    }

    private static String sha256(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte value : digest) hex.append(String.format(Locale.ROOT, "%02x", value & 0xff));
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 unavailable", impossible);
        }
    }

    /** Scan IDs only; text columns are read only when a keyword search needs them. */
    public List<Long> queryInboxIds(InboxQuery query, boolean oldestFirst) {
        List<Long> ids = new ArrayList<>();
        String[] columns = query.keyword.isEmpty() ? new String[]{"id"}
                : new String[]{"id", "raw_text", "link_text",
                    "(SELECT group_concat(title, char(10)) FROM event_candidates WHERE task_id=tasks.id)",
                    "(SELECT group_concat(description, char(10)) FROM event_candidates WHERE task_id=tasks.id)"};
        try (Cursor cursor = getReadableDatabase().query("tasks", columns, query.selection,
                query.arguments, null, null, query.orderBy(oldestFirst))) {
            while (cursor.moveToNext()) {
                if (query.keyword.isEmpty() || query.matchesSearch(cursor.getString(1), cursor.getString(2),
                        cursor.getString(3), cursor.getString(4)))
                    ids.add(cursor.getLong(0));
            }
        }
        return ids;
    }

    /** Fetch only the page IDs; counts/full-filter selection never materialize every TaskRecord. */
    public List<TaskRecord> queryInboxPage(InboxQuery query, boolean oldestFirst,
            int page, List<Long> matchingIds) {
        List<TaskRecord> tasks = new ArrayList<>();
        long offset = (long) Math.max(0, page) * InboxQuery.PAGE_SIZE;
        if (offset >= matchingIds.size()) return tasks;
        int end = (int) Math.min(offset + InboxQuery.PAGE_SIZE, matchingIds.size());
        List<String> ids = new ArrayList<>();
        for (int i = (int) offset; i < end; i++) ids.add(Long.toString(matchingIds.get(i)));
        String placeholders = String.join(",", java.util.Collections.nCopies(ids.size(), "?"));
        try (Cursor cursor = getReadableDatabase().query("tasks", null, "id IN (" + placeholders + ")",
                ids.toArray(new String[0]), null, null, query.orderBy(oldestFirst),
                Integer.toString(InboxQuery.PAGE_SIZE))) {
            while (cursor.moveToNext()) tasks.add(readTask(cursor));
        }
        return tasks;
    }

    /** Returns newest inputs first, breaking equal timestamps by database ID. */
    public List<TaskRecord> listTasks() {
        List<TaskRecord> tasks = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("tasks", null, null, null, null,
                null, "created_at_millis DESC, id DESC")) {
            while (cursor.moveToNext()) {
                tasks.add(readTask(cursor));
            }
        }
        return tasks;
    }

    /** Recovery only needs IDs in this state, never full text and the entire task history. */
    public List<Long> processingTaskIds() {
        List<Long> ids = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("tasks", new String[]{"id"},
                "status = ?", new String[]{TaskRecord.PROCESSING}, null, null, "id ASC")) {
            while (cursor.moveToNext()) ids.add(cursor.getLong(0));
        }
        return ids;
    }

    public boolean failInterruptedProcessing(long id, String message) {
        ContentValues values = new ContentValues();
        values.put("status", TaskRecord.FAILED);
        values.put("error_message", message);
        SQLiteDatabase db = getWritableDatabase(); db.beginTransaction();
        try {
            boolean changed = db.update("tasks", values, "id = ? AND status = ?",
                    new String[]{Long.toString(id), TaskRecord.PROCESSING}) > 0;
            if (changed) finishInterruptedParseRuns(db, id, "InterruptedProcess");
            db.setTransactionSuccessful(); return changed;
        } finally { db.endTransaction(); }
    }

    /** Newest inputs whose proposed entries include this category. */
    public List<TaskRecord> listTasksByCategory(String category) {
        List<TaskRecord> tasks = new ArrayList<>();
        String sql = "SELECT DISTINCT tasks.* FROM tasks JOIN event_candidates ON "
                + "event_candidates.task_id = tasks.id WHERE event_candidates.is_record=0 AND event_candidates.category = ? "
                + "ORDER BY tasks.created_at_millis DESC, tasks.id DESC";
        try (Cursor cursor = getReadableDatabase().rawQuery(sql,
                new String[]{EventCategory.normalize(category)})) {
            while (cursor.moveToNext()) tasks.add(readTask(cursor));
        }
        return tasks;
    }

    /** Inputs with at least one calendar entry, optionally restricted to a category. */
    public List<TaskRecord> listPublishedTasks(String category) {
        List<TaskRecord> tasks = new ArrayList<>();
        String sql = "SELECT DISTINCT tasks.* FROM tasks JOIN event_candidates ON "
                + "event_candidates.task_id = tasks.id "
                + "WHERE event_candidates.is_record=0 AND event_candidates.calendar_event_id IS NOT NULL"
                + (category == null ? "" : " AND event_candidates.category = ?")
                + " ORDER BY tasks.created_at_millis DESC, tasks.id DESC";
        String[] args = category == null ? null
                : new String[]{EventCategory.normalize(category)};
        try (Cursor cursor = getReadableDatabase().rawQuery(sql, args)) {
            while (cursor.moveToNext()) tasks.add(readTask(cursor));
        }
        return tasks;
    }

    /** Returns null if no task has the given ID. */
    public TaskRecord getTask(long taskId) {
        try (Cursor cursor = getReadableDatabase().query("tasks", null, "id = ?",
                new String[] { Long.toString(taskId) }, null, null, null)) {
            return cursor.moveToFirst() ? readTask(cursor) : null;
        }
    }

    /** Deletes an input and its candidate rows through the database foreign key. */
    public boolean deleteTask(long taskId) {
        if (taskId <= 0) throw new IllegalArgumentException("taskId must be positive");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String[] id = { Long.toString(taskId) };
            db.delete("task_attachments", "task_id = ?", id);
            db.delete("task_files", "task_id = ?", id);
            boolean deleted = db.delete("tasks", "id = ?", id) > 0;
            db.setTransactionSuccessful();
            return deleted;
        } finally {
            db.endTransaction();
        }
    }

    /** Removes one draft. Positions keep their gaps so calendar links stay untouched. */
    public boolean deleteCandidate(long candidateId, long taskId) {
        if (candidateId <= 0) throw new IllegalArgumentException("candidateId must be positive");
        if (taskId <= 0) throw new IllegalArgumentException("taskId must be positive");
        return getWritableDatabase().delete("event_candidates", "id = ? AND task_id = ?",
                new String[] { Long.toString(candidateId), Long.toString(taskId) }) > 0;
    }

    /** Drops the image reference of an input and returns the name that was attached, or null. */
    public String clearImage(long taskId) {
        TaskRecord task = getTask(taskId);
        if (task == null || task.imagePath == null) return null;
        getWritableDatabase().delete("task_attachments", "task_id = ?",
                new String[] { Long.toString(taskId) });
        ContentValues values = new ContentValues();
        values.putNull("image_path");
        getWritableDatabase().update("tasks", values, "id = ?",
                new String[] { Long.toString(taskId) });
        return task.imagePath;
    }

    /** Returns every image in capture order, including legacy rows during migration recovery. */
    public List<String> getImagePaths(long taskId) {
        List<String> paths = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("task_attachments",
                new String[] { "image_path" }, "task_id = ?",
                new String[] { Long.toString(taskId) }, null, null, "position ASC")) {
            while (cursor.moveToNext()) paths.add(cursor.getString(0));
        }
        if (paths.isEmpty()) {
            TaskRecord task = getTask(taskId);
            if (task != null && task.imagePath != null) paths.add(task.imagePath);
        }
        return paths;
    }

    /** Attaches an already-imported app-private image to an existing capture. */
    public boolean addImageAttachment(long taskId, String imagePath) {
        if (!ImageStore.isStoredName(imagePath))
            throw new IllegalArgumentException("Unknown attached image");
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            TaskRecord task = getTask(taskId);
            if (task == null) return false;
            int position = 0;
            try (Cursor cursor = db.rawQuery(
                    "SELECT COALESCE(MAX(position), -1) + 1 FROM task_attachments WHERE task_id = ?",
                    new String[] { Long.toString(taskId) })) {
                if (cursor.moveToFirst()) position = cursor.getInt(0);
            }
            ContentValues attachment = new ContentValues();
            attachment.put("task_id", taskId);
            attachment.put("image_path", imagePath);
            attachment.put("position", position);
            db.insertOrThrow("task_attachments", null, attachment);
            if (task.imagePath == null) {
                ContentValues primary = new ContentValues();
                primary.put("image_path", imagePath);
                db.update("tasks", primary, "id = ?",
                        new String[] { Long.toString(taskId) });
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    /** Removes one attachment and keeps the legacy primary-image column aligned. */
    public String removeImageAttachment(long taskId, String imagePath) {
        if (!ImageStore.isStoredName(imagePath)) return null;
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            int removed = db.delete("task_attachments", "task_id = ? AND image_path = ?",
                    new String[] { Long.toString(taskId), imagePath });
            if (removed == 0) return null;
            String next = null;
            try (Cursor cursor = db.query("task_attachments", new String[] { "image_path" },
                    "task_id = ?", new String[] { Long.toString(taskId) }, null, null,
                    "position ASC", "1")) {
                if (cursor.moveToFirst()) next = cursor.getString(0);
            }
            ContentValues primary = new ContentValues();
            if (next == null) primary.putNull("image_path");
            else primary.put("image_path", next);
            db.update("tasks", primary, "id = ?", new String[] { Long.toString(taskId) });
            db.setTransactionSuccessful();
            return imagePath;
        } finally {
            db.endTransaction();
        }
    }

    public boolean updateRawText(long taskId, String rawText) {
        String value = rawText == null ? "" : rawText;
        TaskRecord task = getTask(taskId);
        if (task == null) return false;
        if (value.trim().isEmpty() && getImagePaths(taskId).isEmpty()
                && getFileAttachments(taskId).isEmpty())
            throw new IllegalArgumentException("文字、图片或普通文件至少保留一项");
        ContentValues values = new ContentValues();
        values.put("raw_text", value);
        return getWritableDatabase().update("tasks", values, "id = ?",
                new String[] { Long.toString(taskId) }) > 0;
    }

    public List<TaskFileAttachment> getFileAttachments(long taskId) {
        List<TaskFileAttachment> files = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("task_files", null, "task_id = ?",
                new String[] { Long.toString(taskId) }, null, null, "position ASC")) {
            while (cursor.moveToNext()) {
                files.add(new TaskFileAttachment(cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("task_id")),
                        cursor.getString(cursor.getColumnIndexOrThrow("stored_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("mime_type")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("size_bytes"))));
            }
        }
        return files;
    }

    public boolean addFileAttachment(long taskId, TaskFileAttachment file) {
        validateFileAttachment(file);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            if (getTask(taskId) == null) return false;
            int position = 0;
            try (Cursor cursor = db.rawQuery(
                    "SELECT COALESCE(MAX(position), -1) + 1 FROM task_files WHERE task_id = ?",
                    new String[] { Long.toString(taskId) })) {
                if (cursor.moveToFirst()) position = cursor.getInt(0);
            }
            insertTaskFile(db, taskId, file, position);
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public TaskFileAttachment removeFileAttachment(long taskId, long fileId) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            TaskFileAttachment found = null;
            try (Cursor cursor = db.query("task_files", null, "task_id = ? AND id = ?",
                    new String[] { Long.toString(taskId), Long.toString(fileId) },
                    null, null, null)) {
                if (cursor.moveToFirst()) found = new TaskFileAttachment(fileId, taskId,
                        cursor.getString(cursor.getColumnIndexOrThrow("stored_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("display_name")),
                        cursor.getString(cursor.getColumnIndexOrThrow("mime_type")),
                        cursor.getLong(cursor.getColumnIndexOrThrow("size_bytes")));
            }
            if (found == null) return null;
            db.delete("task_files", "task_id = ? AND id = ?",
                    new String[] { Long.toString(taskId), Long.toString(fileId) });
            db.setTransactionSuccessful();
            return found;
        } finally {
            db.endTransaction();
        }
    }

    private static void insertTaskFiles(SQLiteDatabase db, long taskId,
            List<TaskFileAttachment> files) {
        for (int i = 0; i < files.size(); i++) insertTaskFile(db, taskId, files.get(i), i);
    }

    private static void insertTaskFile(SQLiteDatabase db, long taskId, TaskFileAttachment file,
            int position) {
        ContentValues values = new ContentValues();
        values.put("task_id", taskId);
        values.put("stored_name", file.storedName);
        values.put("display_name", file.displayName);
        values.put("mime_type", file.mimeType);
        values.put("size_bytes", file.sizeBytes);
        values.put("position", position);
        db.insertOrThrow("task_files", null, values);
    }

    private static void validateFileAttachment(TaskFileAttachment file) {
        if (file == null || !TaskFileStore.isStoredName(file.storedName)
                || file.displayName == null || file.mimeType == null || file.sizeBytes < 0)
            throw new IllegalArgumentException("Invalid file attachment");
    }

    /** Every image name still referenced by an input; used to collect orphaned files. */
    public List<String> listImageNames() {
        List<String> names = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("task_attachments",
                new String[] { "image_path" }, null, null, null, null, null)) {
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0));
            }
        }
        return names;
    }

    /** Records the last link fetch: a null text means that attempt read nothing. */
    public void updateLinkFetch(long taskId, String linkText, long fetchedAtMillis) {
        ContentValues values = new ContentValues();
        values.put("link_text", linkText);
        values.put("link_fetched_at", fetchedAtMillis);
        getWritableDatabase().update("tasks", values, "id = ?",
                new String[] { Long.toString(taskId) });
    }

    /** Returns false when the task does not exist. A null error clears the previous error. */
    public boolean updateStatus(long taskId, String status, String errorMessage) {
        if (!TaskRecord.isValidStatus(status)) {
            throw new IllegalArgumentException("Unknown task status: " + status);
        }
        ContentValues values = new ContentValues();
        values.put("status", status);
        values.put("error_message", errorMessage);
        return getWritableDatabase().update("tasks", values, "id = ?",
                new String[] { Long.toString(taskId) }) > 0;
    }

    public boolean updateUsage(long taskId, Integer promptTokens,
            Integer completionTokens, Integer totalTokens, Integer cachedTokens) {
        ContentValues values = new ContentValues();
        values.put("prompt_tokens", promptTokens);
        values.put("completion_tokens", completionTokens);
        values.put("total_tokens", totalTokens);
        values.put("cached_tokens", cachedTokens);
        return getWritableDatabase().update("tasks", values, "id = ?",
                new String[] { Long.toString(taskId) }) > 0;
    }

    /** Atomically replaces all candidates for a task; list order is retained. */
    public void replaceCandidates(long taskId, List<EventCandidate> candidates) {
        replaceCandidates(taskId, candidates, null, null, null);
    }

    /** Candidates and their successful parse evidence commit together, before calendar publication. */
    public void replaceParsedCandidates(long taskId, List<EventCandidate> retained,
            List<EventCandidate> original, String runId, String auditJson) {
        if (runId == null || auditJson == null || original == null)
            throw new IllegalArgumentException("Parsed schedules require a parse audit");
        replaceCandidates(taskId, retained, original, runId, auditJson);
    }

    private void replaceCandidates(long taskId, List<EventCandidate> candidates,
            List<EventCandidate> original, String runId, String auditJson) {
        if (isHomeworkCapture(taskId))
            throw new IllegalArgumentException("作业收件由同步管理，不能重新解析替换");
        if (candidates == null) {
            throw new IllegalArgumentException("candidates must not be null");
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            try (Cursor cursor = db.query("tasks", new String[] { "id" }, "id = ?",
                    new String[] { Long.toString(taskId) }, null, null, null)) {
                if (!cursor.moveToFirst()) {
                    throw new IllegalArgumentException("Unknown task ID: " + taskId);
                }
            }
            db.delete("event_candidates", "task_id = ?", new String[] { Long.toString(taskId) });
            List<Long> savedIds = new ArrayList<>();
            for (int position = 0; position < candidates.size(); position++) {
                EventCandidate candidate = candidates.get(position);
                if (candidate == null) {
                    throw new IllegalArgumentException("Candidate must not be null");
                }
                requireNonEmpty(candidate.title, "candidate.title");
                if (candidate.reminderMinutesBefore != null
                        && candidate.reminderMinutesBefore < 0) {
                    throw new IllegalArgumentException("Reminder minutes must be nonnegative");
                }
                ContentValues values = new ContentValues();
                values.put("task_id", taskId);
                values.put("position", position);
                values.put("title", candidate.title);
                values.put("start_at_millis", candidate.startAtMillis);
                values.put("end_at_millis", candidate.endAtMillis);
                values.put("time_zone_id", candidate.timeZoneId);
                values.put("location", candidate.location);
                values.put("description", candidate.description);
                values.put("reminder_minutes_before", candidate.reminderMinutesBefore);
                values.put("needs_confirmation", candidate.needsConfirmation ? 1 : 0);
                values.put("uncertainty_level", candidate.uncertaintyLevel);
                values.put("calendar_event_id", candidate.calendarEventId);
                values.put("category", candidate.category);
                values.put("all_day", candidate.allDay ? 1 : 0);
                values.put("end_auto_generated", candidate.endAutoGenerated ? 1 : 0);
                values.put("due_at_millis", candidate.dueAtMillis);
                values.put("completed_at_millis", candidate.completedAtMillis);
                values.put("is_record", candidate.isRecord ? 1 : 0);
                putRepeat(values, candidate);
                savedIds.add(db.insertOrThrow("event_candidates", null, values));
            }
            if (runId != null) {
                try {
                    org.json.JSONObject audit = new org.json.JSONObject(auditJson);
                    org.json.JSONArray entries = audit.getJSONArray("entries");
                    if (entries.length() != original.size()) throw new IllegalArgumentException("Audit item count differs from parsed schedules");
                    for (int i = 0; i < entries.length(); i++) {
                        long savedId = savedIds.get(CandidateRules.retainedPosition(candidates, original.get(i)));
                        org.json.JSONObject evidence = entries.getJSONObject(i);
                        ContentValues values = new ContentValues(); values.put("run_id", runId);
                        values.put("position", i); values.put("candidate_id", savedId);
                        values.put("candidate_sync_id", stableCandidateId(savedId));
                        values.put("plan_group", evidence.isNull("plan_group") ? null : evidence.getString("plan_group"));
                        values.put("evidence_json", evidence.toString());
                        db.insertOrThrow("parse_time_entries", null, values);
                    }
                    audit.remove("entries");
                    ContentValues values = new ContentValues(); values.put("status", "succeeded");
                    values.put("finished_at", System.currentTimeMillis()); values.put("result_json", audit.toString());
                    if (db.update("parse_runs", values, "id=? AND task_id=? AND status='running'",
                            new String[]{runId, Long.toString(taskId)}) != 1)
                        throw new IllegalArgumentException("Parse audit is no longer active for this input");
                } catch (org.json.JSONException invalid) {
                    throw new IllegalArgumentException("Invalid parse audit", invalid);
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Appends a new candidate without changing existing candidates or their associations. */
    public long appendCandidate(EventCandidate candidate) {
        if (candidate == null || candidate.id != 0 || candidate.taskId <= 0) {
            throw new IllegalArgumentException("New candidate with a valid task is required");
        }
        requireNonEmpty(candidate.title, "candidate.title");
        if (candidate.reminderMinutesBefore != null && candidate.reminderMinutesBefore < 0) {
            throw new IllegalArgumentException("Reminder minutes must be nonnegative");
        }
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            String[] taskArguments = {Long.toString(candidate.taskId)};
            try (Cursor cursor = db.query("tasks", new String[] {"id"}, "id = ?",
                    taskArguments, null, null, null)) {
                if (!cursor.moveToFirst()) {
                    throw new IllegalArgumentException("Unknown task ID: " + candidate.taskId);
                }
            }
            int position;
            try (Cursor cursor = db.rawQuery(
                    "SELECT COALESCE(MAX(position), -1) + 1 FROM event_candidates WHERE task_id = ?",
                    taskArguments)) {
                cursor.moveToFirst();
                position = cursor.getInt(0);
            }
            ContentValues values = new ContentValues();
            values.put("task_id", candidate.taskId);
            values.put("position", position);
            values.put("title", candidate.title);
            values.put("start_at_millis", candidate.startAtMillis);
            values.put("end_at_millis", candidate.endAtMillis);
            values.put("time_zone_id", candidate.timeZoneId);
            values.put("location", candidate.location);
            values.put("description", candidate.description);
            values.put("reminder_minutes_before", candidate.reminderMinutesBefore);
            values.put("needs_confirmation", candidate.needsConfirmation ? 1 : 0);
            values.put("uncertainty_level", candidate.uncertaintyLevel);
            values.put("calendar_event_id", candidate.calendarEventId);
            values.put("category", candidate.category);
            values.put("all_day", candidate.allDay ? 1 : 0);
            values.put("end_auto_generated", candidate.endAutoGenerated ? 1 : 0);
                values.put("due_at_millis", candidate.dueAtMillis);
                values.put("completed_at_millis", candidate.completedAtMillis);
                values.put("is_record", candidate.isRecord ? 1 : 0);
            putRepeat(values, candidate);
            long id = db.insertOrThrow("event_candidates", null, values);
            db.setTransactionSuccessful();
            return id;
        } finally {
            db.endTransaction();
        }
    }

    /** Updates a reviewed candidate without changing its calendar association. */
    public boolean updateCandidateDescription(long candidateId, long taskId, String description) {
        if (isManagedHomework(candidateId, taskId))
            throw new IllegalArgumentException("作业内容由学在浙大同步，请到原平台修改");
        ContentValues values = new ContentValues();
        values.put("description", description);
        return getWritableDatabase().update("event_candidates", values, "id=? AND task_id=?",
                new String[]{Long.toString(candidateId), Long.toString(taskId)}) == 1;
    }

    /** Updates a reviewed candidate without changing its calendar association. */
    public boolean updateCandidate(EventCandidate candidate) {
        if (candidate == null || candidate.id <= 0) {
            throw new IllegalArgumentException("Saved candidate is required");
        }
        requireNonEmpty(candidate.title, "candidate.title");
        requireCandidateEditAllowed(candidate);
        if (candidate.reminderMinutesBefore != null && candidate.reminderMinutesBefore < 0) {
            throw new IllegalArgumentException("Reminder minutes must be nonnegative");
        }
        ContentValues values = new ContentValues();
        values.put("title", candidate.title);
        values.put("start_at_millis", candidate.startAtMillis);
        values.put("end_at_millis", candidate.endAtMillis);
        values.put("time_zone_id", candidate.timeZoneId);
        values.put("location", candidate.location);
        values.put("description", candidate.description);
        values.put("reminder_minutes_before", candidate.reminderMinutesBefore);
        values.put("needs_confirmation", candidate.needsConfirmation ? 1 : 0);
        values.put("uncertainty_level", candidate.uncertaintyLevel);
        values.put("category", candidate.category);
        values.put("all_day", candidate.allDay ? 1 : 0);
        values.put("end_auto_generated", candidate.endAutoGenerated ? 1 : 0);
                values.put("due_at_millis", candidate.dueAtMillis);
                values.put("completed_at_millis", candidate.completedAtMillis);
                values.put("is_record", candidate.isRecord ? 1 : 0);
        putRepeat(values, candidate);
        return getWritableDatabase().update("event_candidates", values,
                "id = ? AND task_id = ?", new String[] {
                        Long.toString(candidate.id), Long.toString(candidate.taskId) }) > 0;
    }

    /** A capture is ready once it has at least one candidate and all candidates are settled. */
    public static String reviewStatus(List<EventCandidate> candidates) {
        if (candidates.isEmpty() || candidates.stream().anyMatch(candidate -> !candidate.settled()))
            return TaskRecord.NEEDS_REVIEW;
        return TaskRecord.READY;
    }

    public boolean updateReviewStatus(long taskId) {
        return updateStatus(taskId, reviewStatus(getCandidates(taskId)), null);
    }

    public boolean setCandidateCompleted(long id, long taskId, boolean completed) {
        if (!completed && isSubmittedHomework(id, taskId))
            throw new IllegalArgumentException("此作业已提交，提交状态由学在浙大管理");
        SQLiteDatabase db=getWritableDatabase(); db.beginTransaction();
        try {
            ContentValues values=new ContentValues();
            if(completed) values.put("completed_at_millis",System.currentTimeMillis()); else values.putNull("completed_at_millis");
            boolean changed=db.update("event_candidates",values,"id=? AND task_id=? AND category IN ('task','homework') AND is_record=0 AND task_id IN (SELECT id FROM tasks WHERE status NOT IN ('queued','processing'))",new String[]{Long.toString(id),Long.toString(taskId)})==1;
            if(changed) updateReviewStatus(taskId);
            db.setTransactionSuccessful();return changed;
        } finally {db.endTransaction();}
    }

    /** Call before touching CalendarProvider; storage also enforces this at commit. */
    public void requireCandidateEditAllowed(EventCandidate candidate) {
        List<EventCandidate> existing = getCandidatesByIds(java.util.List.of(candidate.id));
        if (!existing.isEmpty() && existing.get(0).repeatRule != null) {
            EventCandidate series = existing.get(0);
            if (candidate.repeatRule == null || !series.repeatRule.toJson().toString().equals(candidate.repeatRule.toJson().toString())
                    || !java.util.Objects.equals(series.startAtMillis, candidate.startAtMillis)
                    || !java.util.Objects.equals(series.endAtMillis, candidate.endAtMillis)
                    || !java.util.Objects.equals(series.dueAtMillis, candidate.dueAtMillis)
                    || !java.util.Objects.equals(series.timeZoneId, candidate.timeZoneId)
                    || !java.util.Objects.equals(series.category, candidate.category)
                    || series.allDay != candidate.allDay || candidate.isRecord)
                throw new IllegalArgumentException("请在重复日程中更改单次时间；更换重复规则请停止后新建");
        }
        if (!isManagedHomework(candidate.id, candidate.taskId)) return;
        EventCandidate current = getCandidatesByIds(java.util.List.of(candidate.id)).get(0);
        if (!java.util.Objects.equals(current.title, candidate.title)
                || !java.util.Objects.equals(current.description, candidate.description)
                || !java.util.Objects.equals(current.dueAtMillis, candidate.dueAtMillis)
                || !java.util.Objects.equals(current.category, candidate.category)
                || current.isRecord != candidate.isRecord)
            throw new IllegalArgumentException("作业标题、内容、截止时间和类型由学在浙大管理");
        if (current.completed() && !candidate.completed() && isSubmittedHomework(candidate.id, candidate.taskId))
            throw new IllegalArgumentException("此作业已提交，提交状态由学在浙大管理");
    }

    public boolean isManagedHomework(long id, long taskId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM zju_homework_map WHERE candidate_id=? AND capture_id=? LIMIT 1",
                new String[]{Long.toString(id), Long.toString(taskId)})) {
            return cursor.moveToFirst();
        }
    }

    public boolean isHomeworkCapture(long taskId) {
        return scalar("SELECT 1 FROM zju_homework_captures WHERE capture_id=?", Long.toString(taskId)) != null;
    }

    public boolean isSubmittedHomework(long id, long taskId) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT snapshot FROM zju_homework_map WHERE candidate_id=? AND capture_id=? LIMIT 1",
                new String[]{Long.toString(id), Long.toString(taskId)})) {
            if (!cursor.moveToFirst()) return false;
            try { return new org.json.JSONObject(cursor.getString(0)).optBoolean("submitted", false); }
            catch (org.json.JSONException failure) { throw new IllegalStateException("作业状态无法读取", failure); }
        }
    }

    public boolean setCalendarEventId(long candidateId, long taskId, long calendarEventId) {
        return setCalendarEventId(candidateId, taskId, calendarEventId, false);
    }

    /** Merge audit links retain their provider event even after the source candidate is removed. */
    public boolean hasOtherCalendarReferences(long eventId, java.util.Collection<Long> excluding) {
        java.util.Set<Long> excluded = new java.util.HashSet<>(excluding);
        String[] args = {Long.toString(eventId)};
        try (Cursor rows = getReadableDatabase().query("event_candidates", new String[]{"id"},
                "calendar_event_id=?", args, null, null, null)) {
            while (rows.moveToNext()) if (!excluded.contains(rows.getLong(0))) return true;
        }
        try (Cursor rows = getReadableDatabase().query("candidate_merges", new String[]{"calendar_event_id"},
                "calendar_event_id=?", args, null, null, null, "1")) {
            return rows.moveToFirst();
        }
    }

    /** Change only this candidate's association, after rechecking the provider identity. */
    public boolean replaceCalendarEventId(long candidateId, long taskId, long expected, Long replacement) {
        if (replacement != null && replacement <= 0) throw new IllegalArgumentException("Invalid calendar ID");
        ContentValues values = new ContentValues();
        values.put("calendar_event_id", replacement);
        return getWritableDatabase().update("event_candidates", values,
                "id=? AND task_id=? AND calendar_event_id=?", new String[]{Long.toString(candidateId),
                        Long.toString(taskId), Long.toString(expected)}) == 1;
    }

    public boolean setCalendarEventIdIfUnlinked(long candidateId, long taskId, long calendarEventId) {
        return setCalendarEventId(candidateId, taskId, calendarEventId, true);
    }

    private boolean setCalendarEventId(long candidateId, long taskId, long calendarEventId,
            boolean onlyUnlinked) {
        if (calendarEventId <= 0) throw new IllegalArgumentException("calendarEventId must be positive");
        ContentValues values = new ContentValues();
        values.put("calendar_event_id", calendarEventId);
        values.put("needs_confirmation", 0);
        values.put("uncertainty_level", EventCandidate.CERTAIN);
        return getWritableDatabase().update("event_candidates", values,
                "id = ? AND task_id = ?" + (onlyUnlinked ? " AND calendar_event_id IS NULL" : ""), new String[] {
                        Long.toString(candidateId), Long.toString(taskId) }) > 0;
    }

    /** Clear only verified stale IDs; preserve drafts and atomically update publication status. */
    public int clearMissingCalendarLinks(java.util.Collection<Long> missingIds) {
        if (missingIds.isEmpty()) return 0;
        SQLiteDatabase db = getWritableDatabase();
        int changed = 0;
        java.util.Set<Long> tasks = new java.util.HashSet<>();
        db.beginTransaction();
        try {
            for (long eventId : missingIds) {
                String[] arguments = {Long.toString(eventId)};
                try (Cursor cursor = db.query("event_candidates", new String[]{"task_id"},
                        "calendar_event_id=?", arguments, null, null, null)) {
                    while (cursor.moveToNext()) tasks.add(cursor.getLong(0));
                }
                ContentValues values = new ContentValues();
                values.putNull("calendar_event_id");
                changed += db.update("event_candidates", values, "calendar_event_id=?", arguments);
            }
            for (long id : tasks) {
                ContentValues values = new ContentValues();
                values.put("status",getCandidates(id).stream().allMatch(EventCandidate::settled)?TaskRecord.READY:TaskRecord.NEEDS_REVIEW);
                values.putNull("error_message");
                db.update("tasks", values, "id=? AND status=?",
                        new String[]{Long.toString(id), TaskRecord.READY});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
        return changed;
    }

    /** Returns candidates in the order supplied by the parser; empty for an unknown task. */
    public List<EventCandidate> getCandidates(long taskId) {
        List<EventCandidate> candidates = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                "task_id = ?", new String[] { Long.toString(taskId) }, null, null,
                "position ASC")) {
            while (cursor.moveToNext()) {
                candidates.add(readCandidate(cursor));
            }
        }
        return candidates;
    }

    /** All extracted entries, ordered by their date for the schedule surface. */
    public List<EventCandidate> listCandidates() {
        List<EventCandidate> candidates = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                null, null, null, null,
                "CASE WHEN start_at_millis IS NULL THEN 1 ELSE 0 END, "
                        + "start_at_millis ASC, task_id DESC, position ASC")) {
            while (cursor.moveToNext()) candidates.add(readCandidate(cursor));
        }
        return candidates;
    }

    private static TaskRecord readTask(Cursor cursor) {
        int promptIndex = cursor.getColumnIndexOrThrow("prompt_tokens");
        int completionIndex = cursor.getColumnIndexOrThrow("completion_tokens");
        int totalIndex = cursor.getColumnIndexOrThrow("total_tokens");
        int cachedIndex = cursor.getColumnIndexOrThrow("cached_tokens");
        int linkTextIndex = cursor.getColumnIndexOrThrow("link_text");
        int linkFetchedIndex = cursor.getColumnIndexOrThrow("link_fetched_at");
        return new TaskRecord(cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("raw_text")),
                cursor.getString(cursor.getColumnIndexOrThrow("image_path")),
                cursor.getString(cursor.getColumnIndexOrThrow("source")),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at_millis")),
                cursor.getString(cursor.getColumnIndexOrThrow("status")),
                cursor.getString(cursor.getColumnIndexOrThrow("error_message")),
                cursor.isNull(promptIndex) ? null : cursor.getInt(promptIndex),
                cursor.isNull(completionIndex) ? null : cursor.getInt(completionIndex),
                cursor.isNull(totalIndex) ? null : cursor.getInt(totalIndex),
                cursor.isNull(cachedIndex) ? null : cursor.getInt(cachedIndex),
                cursor.isNull(linkTextIndex) ? null : cursor.getString(linkTextIndex),
                cursor.isNull(linkFetchedIndex) ? null : cursor.getLong(linkFetchedIndex));
    }

    private static EventCandidate readCandidate(Cursor cursor) {
        int startIndex = cursor.getColumnIndexOrThrow("start_at_millis");
        int endIndex = cursor.getColumnIndexOrThrow("end_at_millis");
        int reminderIndex = cursor.getColumnIndexOrThrow("reminder_minutes_before");
        int calendarEventIndex = cursor.getColumnIndexOrThrow("calendar_event_id");
        return new EventCandidate(cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getLong(cursor.getColumnIndexOrThrow("task_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("title")),
                cursor.isNull(startIndex) ? null : cursor.getLong(startIndex),
                cursor.isNull(endIndex) ? null : cursor.getLong(endIndex),
                cursor.getString(cursor.getColumnIndexOrThrow("time_zone_id")),
                cursor.getString(cursor.getColumnIndexOrThrow("location")),
                cursor.getString(cursor.getColumnIndexOrThrow("description")),
                cursor.isNull(reminderIndex) ? null : cursor.getInt(reminderIndex),
                cursor.getInt(cursor.getColumnIndexOrThrow("needs_confirmation")) != 0,
                cursor.isNull(calendarEventIndex) ? null : cursor.getLong(calendarEventIndex),
                cursor.getString(cursor.getColumnIndexOrThrow("category")),
                cursor.getInt(cursor.getColumnIndexOrThrow("all_day")) != 0,
                cursor.getInt(cursor.getColumnIndexOrThrow("end_auto_generated")) != 0,
                cursor.getInt(cursor.getColumnIndexOrThrow("uncertainty_level")),
                cursor.isNull(cursor.getColumnIndexOrThrow("due_at_millis")) ? null : cursor.getLong(cursor.getColumnIndexOrThrow("due_at_millis")),
                cursor.isNull(cursor.getColumnIndexOrThrow("completed_at_millis")) ? null : cursor.getLong(cursor.getColumnIndexOrThrow("completed_at_millis")),
                cursor.getInt(cursor.getColumnIndexOrThrow("is_record")) != 0,
                readRepeat(cursor.getString(cursor.getColumnIndexOrThrow("repeat_rule"))),
                cursor.getString(cursor.getColumnIndexOrThrow("repeat_exceptions")),
                cursor.isNull(cursor.getColumnIndexOrThrow("repeat_stopped_at")) ? null
                        : cursor.getLong(cursor.getColumnIndexOrThrow("repeat_stopped_at")), null);
    }

    private static RepeatRule readRepeat(String value) {
        if (value == null) return null;
        try { return RepeatRule.fromJson(new org.json.JSONObject(value)); }
        catch (org.json.JSONException invalid) { throw new IllegalStateException("重复规则损坏", invalid); }
    }

    private static void requireNonEmpty(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }
}
