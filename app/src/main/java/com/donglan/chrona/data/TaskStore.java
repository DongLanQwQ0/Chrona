package com.donglan.chrona.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.donglan.chrona.image.ImageStore;

import java.util.ArrayList;
import java.util.List;

/** Local persistence for submitted inputs and the calendar entries proposed for each input. */
public final class TaskStore extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "chrona.db";
    private static final int DATABASE_VERSION = 2;

    public TaskStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
        super.onConfigure(db);
        db.setForeignKeyConstraintsEnabled(true);
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
                + "total_tokens INTEGER)");
        db.execSQL("CREATE INDEX tasks_by_created_at ON tasks(created_at_millis DESC, id DESC)");
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
                + "calendar_event_id INTEGER, "
                + "UNIQUE(task_id, position))");
        db.execSQL("CREATE INDEX candidates_by_task ON event_candidates(task_id, position)");
    }

    /**
     * Applies migrations in order. Version 1 is already on devices, so every later change must
     * add a step here instead of editing the older statements; existing rows must be preserved.
     */
    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE tasks ADD COLUMN image_path TEXT");
        }
    }

    /** Saves one input in queued state and returns its database ID. */
    public long insertTask(String rawText, String imagePath, String source, long createdAtMillis) {
        boolean hasText = rawText != null && !rawText.trim().isEmpty();
        if (imagePath != null && !ImageStore.isStoredName(imagePath)) {
            throw new IllegalArgumentException("Unknown attached image");
        }
        if (!hasText && imagePath == null) {
            throw new IllegalArgumentException("An input needs text or an attached image");
        }
        requireNonEmpty(source, "source");
        ContentValues values = new ContentValues();
        values.put("raw_text", hasText ? rawText : "");
        values.put("image_path", imagePath);
        values.put("source", source);
        values.put("created_at_millis", createdAtMillis);
        values.put("status", TaskRecord.QUEUED);
        return getWritableDatabase().insertOrThrow("tasks", null, values);
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
        return getWritableDatabase().delete("tasks", "id = ?",
                new String[] { Long.toString(taskId) }) > 0;
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
        ContentValues values = new ContentValues();
        values.putNull("image_path");
        getWritableDatabase().update("tasks", values, "id = ?",
                new String[] { Long.toString(taskId) });
        return task.imagePath;
    }

    /** Every image name still referenced by an input; used to collect orphaned files. */
    public List<String> listImageNames() {
        List<String> names = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("tasks", new String[] { "image_path" },
                "image_path IS NOT NULL", null, null, null, null)) {
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0));
            }
        }
        return names;
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
            Integer completionTokens, Integer totalTokens) {
        ContentValues values = new ContentValues();
        values.put("prompt_tokens", promptTokens);
        values.put("completion_tokens", completionTokens);
        values.put("total_tokens", totalTokens);
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
                values.put("calendar_event_id", candidate.calendarEventId);
                db.insertOrThrow("event_candidates", null, values);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
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
        return getWritableDatabase().update("event_candidates", values,
                "id = ? AND task_id = ?", new String[] {
                        Long.toString(candidate.id), Long.toString(candidate.taskId) }) > 0;
    }

    public boolean setCalendarEventId(long candidateId, long taskId, long calendarEventId) {
        if (calendarEventId <= 0) throw new IllegalArgumentException("calendarEventId must be positive");
        ContentValues values = new ContentValues();
        values.put("calendar_event_id", calendarEventId);
        values.put("needs_confirmation", 0);
        return getWritableDatabase().update("event_candidates", values,
                "id = ? AND task_id = ?", new String[] {
                        Long.toString(candidateId), Long.toString(taskId) }) > 0;
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

    private static TaskRecord readTask(Cursor cursor) {
        int promptIndex = cursor.getColumnIndexOrThrow("prompt_tokens");
        int completionIndex = cursor.getColumnIndexOrThrow("completion_tokens");
        int totalIndex = cursor.getColumnIndexOrThrow("total_tokens");
        return new TaskRecord(cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("raw_text")),
                cursor.getString(cursor.getColumnIndexOrThrow("image_path")),
                cursor.getString(cursor.getColumnIndexOrThrow("source")),
                cursor.getLong(cursor.getColumnIndexOrThrow("created_at_millis")),
                cursor.getString(cursor.getColumnIndexOrThrow("status")),
                cursor.getString(cursor.getColumnIndexOrThrow("error_message")),
                cursor.isNull(promptIndex) ? null : cursor.getInt(promptIndex),
                cursor.isNull(completionIndex) ? null : cursor.getInt(completionIndex),
                cursor.isNull(totalIndex) ? null : cursor.getInt(totalIndex));
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
                cursor.isNull(calendarEventIndex) ? null : cursor.getLong(calendarEventIndex));
    }

    private static void requireNonEmpty(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " must not be empty");
        }
    }
}
