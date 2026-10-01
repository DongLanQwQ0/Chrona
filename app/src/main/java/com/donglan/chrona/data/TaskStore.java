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
    private static final String DATABASE_NAME = "chrona.db";
    private static final int DATABASE_VERSION = 9;

    private final Context context;
    private SQLiteDatabase openedDatabase;
    private long openedRevision;

    public TaskStore(Context context) {
        this(context, DATABASE_NAME);
    }

    private TaskStore(Context context, String databaseName) {
        super(context.getApplicationContext(), databaseName, null, DATABASE_VERSION);
        this.context = context.getApplicationContext();
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
                        "task_files", "data_revision", "sqlite_sequence"}) {
                    source.execSQL("DELETE FROM backup_snapshot." + table);
                    source.execSQL("INSERT INTO backup_snapshot." + table
                            + " SELECT * FROM main." + table);
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
            openedDatabase = null;
            super.close();
            if (changed) com.donglan.chrona.AgendaWidgetProvider.requestRefresh(context);
        }
    }

    /** Bounded widget window; avoids hydrating the full task/candidate history. */
    public List<EventCandidate> widgetCandidates(long begin, long end) {
        List<EventCandidate> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                "start_at_millis IS NOT NULL AND start_at_millis<? "
                        + "AND COALESCE(end_at_millis,start_at_millis)>=CAST(? AS INTEGER)",
                new String[]{Long.toString(end), Long.toString(begin)}, null, null,
                "start_at_millis ASC,id ASC", Integer.toString(WIDGET_ITEM_LIMIT))) {
            while (cursor.moveToNext()) result.add(readCandidate(cursor));
        }
        return result;
    }

    /** Timed instants and all-day UTC dates use separate bounds before applying the limit. */
    public List<EventCandidate> widgetCandidates(long begin, long end,
            long allDayBegin, long allDayEnd) {
        List<EventCandidate> result = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                "start_at_millis IS NOT NULL AND ((all_day=0 AND start_at_millis<? "
                        + "AND (COALESCE(end_at_millis,start_at_millis)>CAST(? AS INTEGER) "
                        + "OR start_at_millis>=CAST(? AS INTEGER))) "
                        + "OR (all_day=1 AND start_at_millis<? "
                        + "AND (COALESCE(end_at_millis,start_at_millis)>CAST(? AS INTEGER) "
                        + "OR start_at_millis>=CAST(? AS INTEGER))))",
                new String[]{Long.toString(end), Long.toString(begin), Long.toString(begin),
                        Long.toString(allDayEnd), Long.toString(allDayBegin),
                        Long.toString(allDayBegin)}, null, null,
                "start_at_millis ASC,id ASC", Integer.toString(WIDGET_ITEM_LIMIT))) {
            while (cursor.moveToNext()) result.add(readCandidate(cursor));
        }
        return result;
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
                + "UNIQUE(task_id, position))");
        db.execSQL("CREATE INDEX candidates_by_task ON event_candidates(task_id, position)");
        addScheduleBrowsing(db);
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
        DiagLog.add(context, "db upgrade done, rows=" + countIn(db, "tasks"));
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

    public int taskCountByStatus(String status) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM tasks WHERE status=?", new String[]{status})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public List<EventCandidate> queryScheduleFirst(ScheduleQuery query, int limit) {
        List<EventCandidate> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                query.selection, query.arguments, null, null, query.orderBy(true),
                Integer.toString(Math.max(1, limit)))) {
            while (cursor.moveToNext()) items.add(readCandidate(cursor));
        }
        return items;
    }

    public List<Long> queryScheduleIds(ScheduleQuery query) {
        List<Long> ids = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("event_candidates", new String[]{"id"},
                query.selection, query.arguments, null, null, "id ASC")) {
            while (cursor.moveToNext()) ids.add(cursor.getLong(0));
        }
        return ids;
    }

    public List<EventCandidate> querySchedulePage(ScheduleQuery query, boolean oldestFirst, int page) {
        List<EventCandidate> items = new ArrayList<>();
        long offset = (long) Math.max(0, page) * ScheduleQuery.PAGE_SIZE;
        try (Cursor cursor = getReadableDatabase().query("event_candidates", null,
                query.selection, query.arguments, null, null, query.orderBy(oldestFirst),
                offset + "," + ScheduleQuery.PAGE_SIZE)) {
            while (cursor.moveToNext()) items.add(readCandidate(cursor));
        }
        return items;
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
                "SELECT DISTINCT calendar_event_id FROM event_candidates WHERE calendar_event_id IS NOT NULL", null)) {
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
                : new String[]{"id", "raw_text", "link_text"};
        try (Cursor cursor = getReadableDatabase().query("tasks", columns, query.selection,
                query.arguments, null, null, query.orderBy(oldestFirst))) {
            while (cursor.moveToNext()) {
                if (query.keyword.isEmpty() || query.matchesSearch(cursor.getString(1), cursor.getString(2)))
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

    /** Newest inputs whose proposed entries include this category. */
    public List<TaskRecord> listTasksByCategory(String category) {
        List<TaskRecord> tasks = new ArrayList<>();
        String sql = "SELECT DISTINCT tasks.* FROM tasks JOIN event_candidates ON "
                + "event_candidates.task_id = tasks.id WHERE event_candidates.category = ? "
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
                + "WHERE event_candidates.calendar_event_id IS NOT NULL"
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
                db.insertOrThrow("event_candidates", null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Updates a reviewed candidate without changing its calendar association. */
    public boolean updateCandidateDescription(long candidateId, long taskId, String description) {
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
        return getWritableDatabase().update("event_candidates", values,
                "id = ? AND task_id = ?", new String[] {
                        Long.toString(candidate.id), Long.toString(candidate.taskId) }) > 0;
    }

    public boolean setCalendarEventId(long candidateId, long taskId, long calendarEventId) {
        return setCalendarEventId(candidateId, taskId, calendarEventId, false);
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
                values.put("status", TaskRecord.NEEDS_REVIEW);
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
                cursor.getInt(cursor.getColumnIndexOrThrow("uncertainty_level")));
    }

    private static void requireNonEmpty(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }
}
