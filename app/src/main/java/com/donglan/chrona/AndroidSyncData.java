package com.donglan.chrona;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import com.donglan.chrona.data.*;
import com.donglan.chrona.image.ImageStore;
import com.donglan.chrona.sync.SyncState;
import com.donglan.chrona.sync.TaskCodec;
import org.json.*;
import java.io.*;
import java.nio.file.Files;
import java.util.*;

/** SQLite identities are device-local; payloads contain no calendar or reminder settings. */
final class AndroidSyncData implements AutoCloseable {
    static final class AttachmentException extends IOException {
        AttachmentException(String message, Exception cause) { super(message, cause); }
    }
    final Context context;
    final TaskStore store;
    final SQLiteDatabase db;
    final File blobs;
    private final String databaseIdentity;
    AndroidSyncData(Context context) throws IOException {
        this.context = context; store = new TaskStore(context); db = store.getWritableDatabase();
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_task_map(sync_id TEXT PRIMARY KEY,local_id INTEGER UNIQUE,baseline TEXT,baseline_clock TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_candidate_map(local_id INTEGER PRIMARY KEY,sync_id TEXT UNIQUE)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_term_map(sync_id TEXT PRIMARY KEY,baseline TEXT,baseline_clock TEXT)");
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_meta(id INTEGER PRIMARY KEY CHECK(id=1),identity TEXT NOT NULL)");
        db.execSQL("INSERT OR IGNORE INTO sync_meta(id,identity) VALUES(1,?)", new Object[]{UUID.randomUUID().toString()});
        databaseIdentity = value("SELECT identity FROM sync_meta WHERE id=?", "1");
        blobs = new File(context.getFilesDir(), "sync-blobs");
        if (!blobs.isDirectory() && !blobs.mkdirs()) throw new IOException("无法创建同步附件目录");
    }
    private final List<File> stagedImages = new ArrayList<>();
    private final List<Uri> stagedFiles = new ArrayList<>();
    @Override public void close() {
        // Only resources created by this uncommitted attempt are eligible for cleanup.
        for (File image : stagedImages) image.delete();
        for (Uri file : stagedFiles) try { context.getContentResolver().delete(file, null, null); } catch (RuntimeException ignored) { }
        store.close();
    }
    void appliedResourcesCommitted() { stagedImages.clear(); stagedFiles.clear(); }
    static String canonical(JSONObject json) throws Exception { return json == null ? "null" : SyncState.canonical(json); }
    String value(String sql, String argument) {
        try (Cursor c = db.rawQuery(sql, new String[]{argument})) { return c.moveToFirst() ? c.getString(0) : null; }
    }
    long localId(String id) {
        String local = value("SELECT local_id FROM sync_task_map WHERE sync_id=?", id);
        return local == null ? 0 : Long.parseLong(local);
    }
    String candidateId(long local) {
        String id = value("SELECT sync_id FROM sync_candidate_map WHERE local_id=?", Long.toString(local));
        if (id == null) {
            // AUTOINCREMENT local IDs never repeat. Deterministic identity also survives a DB
            // commit failure after the atomic state file has already been written.
            id = UUID.nameUUIDFromBytes((databaseIdentity + ":candidate:" + local).getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
            db.execSQL("INSERT INTO sync_candidate_map(local_id,sync_id) VALUES(?,?)", new Object[]{local, id});
        }
        return id;
    }
    private JSONObject attachment(InputStream source, String name, String mime, boolean image) throws Exception {
        if (source == null) throw new IOException("原附件无法读取：" + name);
        byte[] bytes;
        try (InputStream input = source; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[32768]; int n;
            while ((n = input.read(buffer)) != -1) {
                if ((long) output.size() + n > 20L * 1024 * 1024) throw new IOException("附件超过 20 MiB：" + name);
                output.write(buffer, 0, n);
            }
            bytes = output.toByteArray();
        }
        String sha = SyncState.sha256(bytes); File target = new File(blobs, sha);
        if (!target.isFile() || target.length() != bytes.length) {
            File temp = File.createTempFile("blob-", ".tmp", blobs);
            try { Files.write(temp.toPath(), bytes); Files.move(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
            finally { temp.delete(); }
        }
        return new JSONObject().put("sha", sha).put("name", name).put("mime", mime).put("size", bytes.length).put("image", image);
    }
    JSONObject payload(TaskRecord task) throws Exception {
        JSONArray candidates = new JSONArray(), attachments = new JSONArray();
        for (EventCandidate candidate : store.getCandidates(task.id))
            candidates.put(TaskCodec.candidate(candidate).put("id", candidateId(candidate.id)));
        String stableTask = value("SELECT sync_id FROM sync_task_map WHERE local_id=?", Long.toString(task.id));
        String saved = stableTask == null ? null : value("SELECT baseline FROM sync_task_map WHERE sync_id=?", stableTask);
        JSONArray previous = saved == null || "null".equals(saved) ? new JSONArray() : new JSONObject(saved).getJSONArray("attachments");
        Set<Integer> matchedImages = new HashSet<>();
        try {
        for (String path : store.getImagePaths(task.id)) {
            JSONObject image = attachment(new FileInputStream(new ImageStore(context).fileFor(path)), path, "image/jpeg", true);
            for (int i = 0; i < previous.length(); i++) {
                JSONObject prior = previous.getJSONObject(i);
                if (!matchedImages.contains(i) && prior.getBoolean("image") && prior.getString("sha").equals(image.getString("sha"))) {
                    image.put("name", prior.getString("name")); matchedImages.add(i); break;
                }
            }
            attachments.put(image);
        }
        for (TaskFileAttachment file : store.getFileAttachments(task.id))
            attachments.put(attachment(context.getContentResolver().openInputStream(Uri.parse(file.storedName)),
                    file.displayName, file.mimeType, false));
        } catch (IOException | SecurityException exception) {
            throw new AttachmentException("原附件缺失、超过 20 MiB 或无法读取；请恢复附件或从记录中移除后重试", exception);
        }
        // SQLite keeps images and ordinary files separately; retain their wire ordering.
        JSONArray ordered = new JSONArray(); Set<Integer> used = new HashSet<>();
        for (int i = 0; i < previous.length(); i++) for (int j = 0; j < attachments.length(); j++)
            if (!used.contains(j) && canonical(previous.getJSONObject(i)).equals(canonical(attachments.getJSONObject(j)))) {
                ordered.put(attachments.getJSONObject(j)); used.add(j); break;
            }
        for (int j = 0; j < attachments.length(); j++) if (!used.contains(j)) ordered.put(attachments.getJSONObject(j));
        return TaskCodec.validate(new JSONObject().put("kind", "task").put("rawText", task.rawText)
                .put("source", task.source).put("createdAt", task.createdAtMillis).put("status", task.status)
                .put("linkText", task.linkText == null ? "" : task.linkText).put("candidates", candidates).put("attachments", ordered));
    }
    static boolean busy(TaskRecord task) {
        return task != null && (TaskRecord.QUEUED.equals(task.status) || TaskRecord.PROCESSING.equals(task.status));
    }
    private static Map<String, Long> clock(String encoded) throws Exception {
        Map<String, Long> values = new HashMap<>(); if (encoded == null) return values;
        JSONObject json = new JSONObject(encoded); Iterator<String> keys = json.keys();
        while (keys.hasNext()) { String key = keys.next(); values.put(key, json.getLong(key)); }
        return values;
    }
    private String changed(SyncState state, String id, JSONObject payload, String baseline, String baselineClock) throws Exception {
        String current = canonical(payload);
        if (current.equals(baseline)) return baselineClock;
        List<SyncState.Version> versions = state.records().get(id);
        // A crash after applying data but before its baseline update is safe to replay.
        if (versions != null) for (SyncState.Version v : versions) {
            if (current.equals(v.deleted ? "null" : canonical(v.payload))) return new JSONObject(v.clock).toString();
        }
        return new JSONObject(state.branch(id, payload, clock(baselineClock)).clock).toString();
    }
    static final class Snapshot {
        long revision;
        List<TaskRecord> tasks;
        final Map<Long, JSONObject> payloads = new HashMap<>();
        String terms;
        Map<String, JSONObject> termPayloads;
    }
    /** Hash provider bytes and parse ICS without holding a SQLite transaction. */
    Snapshot prepareCapture() throws Exception {
        if (db.inTransaction()) throw new IllegalStateException("IO staging cannot hold a database transaction");
        Snapshot snapshot = new Snapshot(); snapshot.revision = store.dataRevision(); snapshot.tasks = store.listTasks();
        for (TaskRecord task : snapshot.tasks) if (!busy(task)) snapshot.payloads.put(task.id, payload(task));
        snapshot.terms = new TimetableStore(context).snapshot();
        snapshot.termPayloads = terms(TimetableStore.inspect(snapshot.terms));
        requireRevision(snapshot);
        return snapshot;
    }
    void requireRevision(Snapshot snapshot) throws IOException {
        if (store.dataRevision() != snapshot.revision) throw new IOException("记录正在修改，请稍后重新同步");
    }
    /** Staged content only; caller holds a short transaction for mapping and baseline updates. */
    void captureTasks(SyncState state, Snapshot snapshot) throws Exception {
        requireRevision(snapshot);
        Set<Long> present = new HashSet<>();
        for (TaskRecord task : snapshot.tasks) {
            present.add(task.id);
            if (busy(task)) continue;
            String id = value("SELECT sync_id FROM sync_task_map WHERE local_id=?", Long.toString(task.id));
            if (id == null) {
                id = "task_" + UUID.nameUUIDFromBytes((databaseIdentity + ":task:" + task.id).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                db.execSQL("INSERT INTO sync_task_map(sync_id,local_id) VALUES(?,?)", new Object[]{id, task.id});
            }
            JSONObject payload = snapshot.payloads.get(task.id);
            String nextClock = changed(state, id, payload, value("SELECT baseline FROM sync_task_map WHERE sync_id=?", id),
                    value("SELECT baseline_clock FROM sync_task_map WHERE sync_id=?", id));
            db.execSQL("UPDATE sync_task_map SET baseline=?,baseline_clock=? WHERE sync_id=?", new Object[]{canonical(payload), nextClock, id});
        }
        try (Cursor c = db.rawQuery("SELECT sync_id,local_id,baseline,baseline_clock FROM sync_task_map", null)) {
            while (c.moveToNext()) if (!present.contains(c.getLong(1))) {
                String nextClock = changed(state, c.getString(0), null, c.getString(2), c.getString(3));
                db.execSQL("UPDATE sync_task_map SET baseline='null',baseline_clock=? WHERE sync_id=?", new Object[]{nextClock, c.getString(0)});
            }
        }
    }
    static Map<String, JSONObject> terms(TimetableStore.Library library) throws Exception {
        Map<String, JSONObject> result = new LinkedHashMap<>();
        if (library != null) for (TimetableStore.Semester semester : library.semesters) {
            TimetableStore.Document single = semester.document.withState(Collections.singleton(semester.index), semester.document.overrides);
            JSONObject doc = new JSONObject(TimetableStore.encode(new TimetableStore.Library(Collections.singletonList(single), "")));
            doc.remove("archive"); doc.remove("selected");
            result.put("term_" + semester.id().replace('/', '_'), new JSONObject().put("kind", "term").put("index", semester.index).put("document", doc));
        }
        return result;
    }
    void captureTerms(SyncState state, Snapshot snapshot) throws Exception {
        Map<String, JSONObject> current = snapshot.termPayloads;
        Set<String> ids = new HashSet<>(current.keySet());
        try (Cursor c = db.rawQuery("SELECT sync_id FROM sync_term_map", null)) { while (c.moveToNext()) ids.add(c.getString(0)); }
        for (String id : ids) {
            JSONObject payload = current.get(id);
            String nextClock = changed(state, id, payload, value("SELECT baseline FROM sync_term_map WHERE sync_id=?", id),
                    value("SELECT baseline_clock FROM sync_term_map WHERE sync_id=?", id));
            db.execSQL("INSERT OR REPLACE INTO sync_term_map(sync_id,baseline,baseline_clock) VALUES(?,?,?)", new Object[]{id, canonical(payload), nextClock});
        }
    }
    boolean linked(long taskId) {
        return value("SELECT id FROM event_candidates WHERE task_id=? AND calendar_event_id IS NOT NULL LIMIT 1", Long.toString(taskId)) != null;
    }
    boolean taskDifferent(String id, SyncState.Version version) throws Exception {
        return !Objects.equals(value("SELECT baseline FROM sync_task_map WHERE sync_id=?", id),
                version.deleted ? "null" : canonical(version.payload));
    }
    static final class StagedTask {
        JSONObject payload;
        final List<EventCandidate> candidates = new ArrayList<>();
        List<ContentValues> images, files;
        String firstImage;
    }
    private final Map<String, StagedTask> stagedTasks = new HashMap<>();
    void prepareTaskApply(SyncState state, Snapshot snapshot, String calendarApprovedId) throws Exception {
        prepareTaskApply(state, snapshot, calendarApprovedId, null);
    }
    void prepareTaskApply(SyncState state, Snapshot snapshot, String calendarApprovedId, String onlyId) throws Exception {
        if (db.inTransaction()) throw new IllegalStateException("IO staging cannot hold a database transaction");
        for (Map.Entry<String, List<SyncState.Version>> entry : state.records().entrySet()) {
            String id = entry.getKey(); if (!id.startsWith("task_") || entry.getValue().size() != 1) continue;
            if (onlyId != null && !onlyId.equals(id)) continue;
            SyncState.Version version = entry.getValue().get(0); long local = localId(id);
            TaskRecord existing = local == 0 ? null : store.getTask(local);
            if (busy(existing) || !taskDifferent(id, version) || (linked(local) && !id.equals(calendarApprovedId))) continue;
            if (existing != null && !canonical(snapshot.payloads.get(local)).equals(value("SELECT baseline FROM sync_task_map WHERE sync_id=?", id))) continue;
            StagedTask staged = new StagedTask(); stagedTasks.put(id, staged);
            if (version.deleted) continue;
            staged.payload = TaskCodec.validate(version.payload);
            JSONArray candidates = staged.payload.getJSONArray("candidates");
            for (int i = 0; i < candidates.length(); i++) staged.candidates.add(TaskCodec.event(candidates.getJSONObject(i)));
            JSONArray attachments = staged.payload.getJSONArray("attachments");
            String baseline = value("SELECT baseline FROM sync_task_map WHERE sync_id=?", id);
            JSONArray previous = baseline == null || "null".equals(baseline) ? null : new JSONObject(baseline).optJSONArray("attachments");
            if (existing != null && Objects.equals(SyncState.canonical(attachments), previous == null ? null : SyncState.canonical(previous))) continue;
            staged.images = new ArrayList<>(); staged.files = new ArrayList<>();
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject a = attachments.getJSONObject(i); File blob = new File(blobs, a.getString("sha"));
                if (blob.length() != a.getLong("size") || blob.length() > TaskCodec.MAX_ATTACHMENT_BYTES) throw new IOException("同步附件校验失败");
                byte[] bytes = Files.readAllBytes(blob.toPath());
                if (!SyncState.sha256(bytes).equals(a.getString("sha"))) throw new IOException("同步附件校验失败");
                ContentValues values = new ContentValues();
                if (a.getBoolean("image")) {
                    if (bytes.length < 3 || (bytes[0] & 255) != 255 || (bytes[1] & 255) != 216 || (bytes[2] & 255) != 255)
                        throw new IOException("同步图片必须为 JPEG");
                    android.graphics.BitmapFactory.Options bounds = new android.graphics.BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw new IOException("同步 JPEG 图片无法解码");
                    String name = UUID.randomUUID() + ".jpg"; File target = new ImageStore(context).fileFor(name);
                    if (!target.getParentFile().isDirectory() && !target.getParentFile().mkdirs()) throw new IOException("无法创建图片目录");
                    stagedImages.add(target); Files.write(target.toPath(), bytes);
                    values.put("image_path", name); values.put("position", staged.images.size()); staged.images.add(values);
                    if (staged.firstImage == null) staged.firstImage = name;
                } else {
                    Uri uri = new Uri.Builder().scheme("content").authority(context.getPackageName() + ".syncblobs")
                            .appendPath(a.getString("sha")).appendQueryParameter("name", a.getString("name"))
                            .appendQueryParameter("mime", a.getString("mime")).build();
                    TaskFileAttachment imported = new TaskFileStore(context).importFile(uri);
                    stagedFiles.add(Uri.parse(imported.storedName));
                    values.put("stored_name", imported.storedName); values.put("display_name", a.getString("name"));
                    values.put("mime_type", a.getString("mime")); values.put("size_bytes", a.getLong("size"));
                    values.put("position", staged.files.size()); staged.files.add(values);
                }
            }
        }
        requireRevision(snapshot);
    }
    void applyTask(String id, SyncState.Version version) throws Exception {
        long local = localId(id); TaskRecord existing = local == 0 ? null : store.getTask(local);
        if (busy(existing)) return;
        if (!taskDifferent(id, version)) {
            db.execSQL("UPDATE sync_task_map SET baseline_clock=? WHERE sync_id=?", new Object[]{new JSONObject(version.clock).toString(), id});
            return;
        }
        if (linked(local) || !stagedTasks.containsKey(id)) return;
        StagedTask staged = stagedTasks.get(id);
        if (version.deleted) {
            if (existing != null) store.deleteTask(local);
            db.execSQL("INSERT OR REPLACE INTO sync_task_map(sync_id,local_id,baseline,baseline_clock) VALUES(?,?,?,?)",
                    new Object[]{id, local == 0 ? null : local, "null", new JSONObject(version.clock).toString()});
            return;
        }
        JSONObject p = staged.payload;
        ContentValues task = new ContentValues();
        task.put("raw_text", p.getString("rawText")); task.put("source", p.getString("source"));
        task.put("created_at_millis", p.getLong("createdAt")); task.put("status", p.getString("status"));
        task.put("link_text", p.getString("linkText"));
        task.putNull("error_message");
        if (existing == null) local = db.insertOrThrow("tasks", null, task);
        else db.update("tasks", task, "id=?", new String[]{Long.toString(local)});
        Map<String, EventCandidate> old = new HashMap<>();
        for (EventCandidate c : store.getCandidates(local)) old.put(candidateId(c.id), c);
        // Free unique positions before ordering retained candidates.
        db.execSQL("UPDATE event_candidates SET position=-id WHERE task_id=?", new Object[]{local});
        JSONArray candidates = p.getJSONArray("candidates");
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject json = candidates.getJSONObject(i); String stable = json.getString("id");
            EventCandidate incoming = staged.candidates.get(i), prior = old.remove(stable);
            ContentValues values = new ContentValues(); values.put("task_id", local); values.put("position", i);
            values.put("title", incoming.title); values.put("start_at_millis", incoming.startAtMillis);
            values.put("end_at_millis", incoming.endAtMillis); values.put("time_zone_id", incoming.timeZoneId);
            values.put("location", incoming.location); values.put("description", incoming.description);
            values.put("needs_confirmation", incoming.needsConfirmation ? 1 : 0); values.put("uncertainty_level", incoming.uncertaintyLevel);
            values.put("category", incoming.category); values.put("all_day", incoming.allDay ? 1 : 0);
            values.put("end_auto_generated", incoming.endAutoGenerated ? 1 : 0);
            if (prior == null) {
                String mapped = value("SELECT local_id FROM sync_candidate_map WHERE sync_id=?", stable);
                if (mapped != null && value("SELECT task_id FROM event_candidates WHERE id=?", mapped) != null)
                    throw new IOException("云端日程身份与其他记录重复");
                long candidateLocal = db.insertOrThrow("event_candidates", null, values);
                // A deleted local candidate's UUID can return from a remote version, but gets a new local ID.
                db.execSQL("DELETE FROM sync_candidate_map WHERE sync_id=?", new Object[]{stable});
                db.execSQL("INSERT INTO sync_candidate_map(local_id,sync_id) VALUES(?,?)", new Object[]{candidateLocal, stable});
            } else db.update("event_candidates", values, "id=?", new String[]{Long.toString(prior.id)});
        }
        for (EventCandidate removed : old.values()) db.delete("event_candidates", "id=?", new String[]{Long.toString(removed.id)});
        if (staged.images != null) {
            db.delete("task_attachments", "task_id=?", new String[]{Long.toString(local)});
            db.delete("task_files", "task_id=?", new String[]{Long.toString(local)});
            for (ContentValues values : staged.images) { values.put("task_id", local); db.insertOrThrow("task_attachments", null, values); }
            for (ContentValues values : staged.files) { values.put("task_id", local); db.insertOrThrow("task_files", null, values); }
            ContentValues image = new ContentValues(); image.put("image_path", staged.firstImage);
            db.update("tasks", image, "id=?", new String[]{Long.toString(local)});
        }
        // The local image filename differs from the remote name; preserve protocol attachment names separately.
        db.execSQL("INSERT OR REPLACE INTO sync_task_map(sync_id,local_id,baseline,baseline_clock) VALUES(?,?,?,?)",
                new Object[]{id, local, canonical(p), new JSONObject(version.clock).toString()});
    }
    void applyTerms(SyncState state, String expected) throws Exception {
        if (db.inTransaction()) throw new IllegalStateException("ICS parsing cannot hold a database transaction");
        TimetableStore timetable = new TimetableStore(context);
        TimetableStore.Library library = TimetableStore.inspect(expected);
        if (library == null) library = new TimetableStore.Library(Collections.emptyList(), "");
        Map<String, String> applied = new HashMap<>();
        Map<String, SyncState.Version> appliedVersions = new HashMap<>();
        for (Map.Entry<String, List<SyncState.Version>> entry : state.records().entrySet()) {
            String id = entry.getKey(); if (!id.startsWith("term_") || entry.getValue().size() != 1) continue;
            SyncState.Version v = entry.getValue().get(0); String next = v.deleted ? "null" : canonical(v.payload);
            if (Objects.equals(next, value("SELECT baseline FROM sync_term_map WHERE sync_id=?", id))) {
                appliedVersions.put(id, v); continue;
            }
            for (TimetableStore.Semester s : new ArrayList<>(library.semesters))
                if (id.equals("term_" + s.id().replace('/', '_'))) library = library.remove(s);
            if (!v.deleted) {
                JSONObject p = v.payload; int index = p.getInt("index");
                JSONObject doc = new JSONObject(p.getJSONObject("document").toString());
                doc.remove("archive"); doc.put("selected", ""); doc.put("enabled", new JSONArray().put(index));
                TimetableStore.Library parsed = TimetableStore.inspect(doc.toString());
                if (parsed.semesters.size() != 1 || !id.equals("term_" + parsed.semesters.get(0).id().replace('/', '_')))
                    throw new IOException("云端学期身份无效");
                library = library.merge(parsed.documents.get(0));
            }
            applied.put(id, next);
            appliedVersions.put(id, v);
        }
        if (!applied.isEmpty()) {
            String selected = expected == null ? "" : TimetableStore.inspect(expected).selected;
            library = library.select(selected);
            if (!timetable.compareAndRestore(expected, TimetableStore.encode(library))) throw new IOException("课表正在修改，请重新同步");
        }
        db.beginTransaction();
        try {
            for (Map.Entry<String, SyncState.Version> item : appliedVersions.entrySet()) {
                SyncState.Version version = item.getValue();
                db.execSQL("INSERT OR REPLACE INTO sync_term_map(sync_id,baseline,baseline_clock) VALUES(?,?,?)",
                        new Object[]{item.getKey(), version.deleted ? "null" : canonical(version.payload), new JSONObject(version.clock).toString()});
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
}
