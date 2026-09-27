package com.donglan.chrona;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;

import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskFileAttachment;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.image.ImageStore;
import com.donglan.chrona.processing.ProcessingJobService;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/** Portable snapshot of Chrona's local inputs, attachments, drafts, and settings. */
public final class ChronaDataBackup {
    private static final String APP = "ChronaData";
    private static final int FORMAT = 1;
    private static final long MAX_ARCHIVE_BYTES = 1024L * 1024 * 1024;
    private static final long MAX_ENTRY_BYTES = 512L * 1024 * 1024;

    private ChronaDataBackup() { }

    public static final class Prepared {
        final File archive;
        final File directory;
        final JSONObject manifest;
        final String config;

        Prepared(File archive, File directory, JSONObject manifest, String config) {
            this.archive = archive;
            this.directory = directory;
            this.manifest = manifest;
            this.config = config;
        }

        public String summary() throws Exception {
            return "· 收件箱 " + manifest.optInt("tasks") + " 条，日程 "
                    + manifest.optInt("candidates") + " 项\n· 图片 "
                    + manifest.optInt("imageCount") + " 张，普通附件 "
                    + manifest.optInt("fileCount") + " 个\n· "
                    + ConfigBackup.describe(config)
                    + "· 导入会替换本机收件箱与日程状态；公共下载副本和系统日历中未关联的事件会保留。";
        }

        public void cleanup() {
            archive.delete();
            deleteTree(directory);
        }
    }

    public static void write(Context context, Uri target, boolean includeApiKey) throws Exception {
        File archive = createArchive(context, includeApiKey);
        try (OutputStream output = context.getContentResolver().openOutputStream(target, "w")) {
            if (output == null) throw new IOException("无法写入备份文件");
            copy(new FileInputStream(archive), output);
        } finally {
            archive.delete();
        }
    }

    private static File createArchive(Context context, boolean includeApiKey) throws Exception {
        File archive = File.createTempFile("chrona-backup-", ".zip", context.getCacheDir());
        try {
            File database = context.getDatabasePath("chrona.db");
            JSONArray files = new JSONArray();
            JSONArray images = new JSONArray();
            JSONArray outputs = new JSONArray();
            JSONArray events = new JSONArray();
            int taskCount;
            int candidateCount;
            try (TaskStore store = new TaskStore(context)) {
                SQLiteDatabase db = store.getWritableDatabase();
                try (Cursor checkpoint = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
                    if (checkpoint.moveToFirst() && checkpoint.getInt(0) != 0)
                        throw new IOException("数据库仍在写入，请稍后重试备份");
                }
                List<TaskRecord> tasks = store.listTasks();
                List<EventCandidate> candidates = store.listCandidates();
                taskCount = tasks.size();
                candidateCount = candidates.size();
                Map<String, String> copiedUris = new HashMap<>();
                int fileSequence = 0;
                for (TaskRecord task : tasks) {
                    for (TaskFileAttachment file : store.getFileAttachments(task.id)) {
                        String entry = copiedUris.get(file.storedName);
                        if (entry == null) {
                            entry = "files/" + (fileSequence++) + ".bin";
                            copiedUris.put(file.storedName, entry);
                        }
                        JSONObject item = new JSONObject();
                        item.put("rowId", file.id);
                        item.put("entry", entry);
                        item.put("name", file.displayName);
                        item.put("mime", file.mimeType);
                        item.put("size", file.sizeBytes);
                        files.put(item);
                    }
                }
                Set<String> uniqueImages = new HashSet<>();
                for (String name : store.listImageNames()) {
                    if (!uniqueImages.add(name)) continue;
                    File image = new ImageStore(context).fileFor(name);
                    if (!image.isFile()) throw new IOException("图片文件丢失：" + name);
                    JSONObject item = new JSONObject();
                    item.put("name", name);
                    item.put("entry", "images/" + name);
                    images.put(item);
                }
                File outputDir = new File(context.getFilesDir(), "model-output");
                for (TaskRecord task : tasks) {
                    File output = new File(outputDir, "task-" + task.id + ".txt");
                    if (output.isFile()) outputs.put(task.id);
                }
                CalendarStore calendar = new CalendarStore(context);
                for (EventCandidate candidate : candidates) {
                    if (candidate.calendarEventId == null) continue;
                    JSONObject item = new JSONObject();
                    item.put("candidateId", candidate.id);
                    item.put("oldEventId", candidate.calendarEventId);
                    item.put("title", candidate.title);
                    item.put("start", candidate.startAtMillis);
                    item.put("end", candidate.endAtMillis);
                    item.put("timeZone", candidate.timeZoneId == null ? "UTC" : candidate.timeZoneId);
                    item.put("allDay", candidate.allDay);
                    item.put("description", candidate.description == null ? JSONObject.NULL
                            : candidate.description);
                    item.put("location", candidate.location == null ? JSONObject.NULL
                            : candidate.location);
                    JSONArray reminders = new JSONArray();
                    if (candidate.reminderMinutesBefore != null)
                        reminders.put(candidate.reminderMinutesBefore);
                    item.put("reminders", reminders);
                    if (calendar.hasReadPermission()) {
                        try {
                            CalendarStore.EventRecord event = calendar.getEvent(
                                    candidate.calendarEventId);
                            if (event != null) {
                                item.put("title", event.title);
                                item.put("start", event.startMillis);
                                item.put("end", event.endMillis);
                                item.put("timeZone", event.timeZoneId);
                                item.put("allDay", event.allDay);
                                item.put("description", event.description == null ? JSONObject.NULL
                                        : event.description);
                                item.put("location", event.location == null ? JSONObject.NULL
                                        : event.location);
                                reminders = new JSONArray();
                                for (int minutes : event.reminderMinutes) reminders.put(minutes);
                                item.put("reminders", reminders);
                            }
                        } catch (RuntimeException ignored) { }
                    }
                    events.put(item);
                }
            }
            if (!database.isFile()) throw new IOException("找不到 Chrona 数据库");
            JSONObject manifest = new JSONObject();
            manifest.put("app", APP);
            manifest.put("format", FORMAT);
            manifest.put("tasks", taskCount);
            manifest.put("candidates", candidateCount);
            manifest.put("imageCount", images.length());
            manifest.put("fileCount", files.length());
            manifest.put("files", files);
            manifest.put("images", images);
            manifest.put("outputs", outputs);
            manifest.put("events", events);
            manifest.put("homeTimelineLimit", HomeTimelinePreferences.getItemLimit(context));
            manifest.put("config", ConfigBackup.export(context, includeApiKey));
            try (ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(
                    new FileOutputStream(archive)))) {
                addFile(zip, database, "chrona.db");
                for (int i = 0; i < images.length(); i++) {
                    JSONObject item = images.getJSONObject(i);
                    addFile(zip, new ImageStore(context).fileFor(item.getString("name")),
                            item.getString("entry"));
                }
                Map<String, File> fileEntries = new HashMap<>();
                for (int i = 0; i < files.length(); i++) {
                    JSONObject item = files.getJSONObject(i);
                    String entry = item.getString("entry");
                    if (fileEntries.containsKey(entry)) continue;
                    TaskFileAttachment found = findAttachment(context, item.getLong("rowId"));
                    if (found == null) throw new IOException("普通附件记录不存在");
                    File extracted = new File(context.getCacheDir(), "chrona-source-"
                            + UUID.randomUUID());
                    try (InputStream input = context.getContentResolver().openInputStream(
                            Uri.parse(found.storedName)); OutputStream output = new FileOutputStream(extracted)) {
                        if (input == null) throw new IOException("无法读取附件：" + found.displayName);
                        copy(input, output);
                    }
                    fileEntries.put(entry, extracted);
                }
                for (Map.Entry<String, File> entry : fileEntries.entrySet()) {
                    addFile(zip, entry.getValue(), entry.getKey());
                    entry.getValue().delete();
                }
                File outputDir = new File(context.getFilesDir(), "model-output");
                for (int i = 0; i < outputs.length(); i++) {
                    long id = outputs.getLong(i);
                    File output = new File(outputDir, "task-" + id + ".txt");
                    if (output.isFile()) addFile(zip, output, "outputs/" + id + ".txt");
                }
                zip.putNextEntry(new ZipEntry("manifest.json"));
                zip.write(manifest.toString().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            return archive;
        } catch (Exception exception) {
            archive.delete();
            throw exception;
        }
    }

    private static TaskFileAttachment findAttachment(Context context, long rowId) {
        try (TaskStore store = new TaskStore(context)) {
            for (TaskRecord task : store.listTasks())
                for (TaskFileAttachment file : store.getFileAttachments(task.id))
                    if (file.id == rowId) return file;
        }
        return null;
    }

    public static Prepared prepare(Context context, Uri source) throws Exception {
        File archive = File.createTempFile("chrona-import-", ".zip", context.getCacheDir());
        File directory = new File(context.getCacheDir(), "chrona-import-" + UUID.randomUUID());
        if (!directory.mkdirs()) throw new IOException("无法创建临时恢复目录");
        try {
            try (InputStream input = context.getContentResolver().openInputStream(source);
                 OutputStream output = new FileOutputStream(archive)) {
                if (input == null) throw new IOException("无法读取所选备份");
                copyLimited(input, output, MAX_ARCHIVE_BYTES);
            }
            unzip(archive, directory);
            File manifestFile = new File(directory, "manifest.json");
            if (!manifestFile.isFile() || manifestFile.length() > 4 * 1024 * 1024)
                throw new IOException("备份清单缺失或过大");
        JSONObject manifest = new JSONObject(readText(manifestFile));
            if (!APP.equals(manifest.optString("app")) || manifest.optInt("format") != FORMAT)
                throw new IOException("备份来源或版本不受支持");
            String config = manifest.getString("config");
            ConfigBackup.inspect(config);
            inspectDatabase(new File(directory, "chrona.db"));
            validateManifestPaths(manifest, directory);
            return new Prepared(archive, directory, manifest, config);
        } catch (Exception exception) {
            archive.delete();
            deleteTree(directory);
            throw exception;
        }
    }

    private static void validateManifestPaths(JSONObject manifest, File directory)
            throws Exception {
        JSONArray images = manifest.getJSONArray("images");
        if (manifest.optInt("imageCount", -1) != images.length())
            throw new IOException("备份图片清单数量不匹配");
        Set<String> imageNames = new HashSet<>();
        for (int i = 0; i < images.length(); i++) {
            JSONObject item = images.getJSONObject(i);
            String name = item.getString("name");
            String entry = item.getString("entry");
            if (!ImageStore.isStoredName(name) || !imageNames.add(name)
                    || !safeEntry(entry) || !entry.equals("images/" + name)
                    || !new File(directory, entry).isFile())
                throw new IOException("备份图片不完整");
        }
        JSONArray files = manifest.getJSONArray("files");
        if (manifest.optInt("fileCount", -1) != files.length())
            throw new IOException("备份附件清单数量不匹配");
        Set<Long> fileRows = new HashSet<>();
        for (int i = 0; i < files.length(); i++) {
            JSONObject item = files.getJSONObject(i);
            File payload = new File(directory, item.getString("entry"));
            long rowId = item.getLong("rowId");
            String name = item.getString("name");
            if (!safeEntry(item.getString("entry")) || rowId <= 0
                    || !fileRows.add(rowId) || name.trim().isEmpty() || name.length() > 512
                    || !payload.isFile() || item.optLong("size", -1) < 0)
                throw new IOException("备份附件不完整");
        }
        JSONArray outputs = manifest.getJSONArray("outputs");
        Set<Long> outputIds = new HashSet<>();
        for (int i = 0; i < outputs.length(); i++) {
            long outputId = outputs.getLong(i);
            if (outputId <= 0 || !outputIds.add(outputId)) throw new IOException("备份解析草稿清单无效");
            String entry = "outputs/" + outputId + ".txt";
            if (!new File(directory, entry).isFile()) throw new IOException("备份解析草稿不完整");
        }
        if (manifest.optInt("tasks", -1) < 0 || manifest.optInt("candidates", -1) < 0)
            throw new IOException("备份记录数量无效");
        JSONArray events = manifest.optJSONArray("events");
        if (events == null) throw new IOException("备份日程清单缺失");
    }

    private static void inspectDatabase(File database) throws Exception {
        if (!database.isFile()) throw new IOException("备份中没有数据库");
        SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY);
        try {
            try (Cursor cursor = db.rawQuery("PRAGMA integrity_check", null)) {
                if (!cursor.moveToFirst() || !"ok".equalsIgnoreCase(cursor.getString(0)))
                    throw new IOException("备份数据库完整性检查失败");
            }
            try (Cursor cursor = db.rawQuery("PRAGMA user_version", null)) {
                if (!cursor.moveToFirst() || cursor.getInt(0) != 6)
                    throw new IOException("备份数据库版本与当前应用不兼容");
            }
        } finally {
            db.close();
        }
    }

    public static String restore(Context context, Prepared prepared) throws Exception {
        try (TaskStore current = new TaskStore(context)) {
            for (TaskRecord task : current.listTasks()) {
                if (TaskRecord.PROCESSING.equals(task.status))
                    throw new IOException("当前仍有任务正在解析，请完成或停止解析后再恢复备份");
            }
        }
        File stagedDb = new File(prepared.directory, "chrona.db");
        ArrayList<File> createdImages = new ArrayList<>();
        ArrayList<Uri> createdFiles = new ArrayList<>();
        ArrayList<Long> createdEvents = new ArrayList<>();
        File installedPrevious = null;
        boolean databaseInstalled = false;
        File outputDirectory = new File(context.getFilesDir(), "model-output");
        File previousOutputs = new File(context.getFilesDir(), "model-output.previous");
        String previousConfig = ConfigBackup.export(context, true);
        int previousTimelineLimit = HomeTimelinePreferences.getItemLimit(context);
        Map<Long, Long> calendarLinks = new HashMap<>();
        try {
        Map<String, String> imageNames = restoreImages(context, prepared, createdImages);
        Map<Long, TaskFileAttachment> restoredFiles = restoreFiles(context, prepared, createdFiles);
        calendarLinks = restoreCalendarLinks(context, prepared, createdEvents);
        SQLiteDatabase staged = SQLiteDatabase.openDatabase(stagedDb.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READWRITE);
        try {
            for (Map.Entry<String, String> entry : imageNames.entrySet()) {
                ContentValues values = new ContentValues();
                values.put("image_path", entry.getValue());
                staged.update("tasks", values, "image_path = ?", new String[]{entry.getKey()});
                staged.update("task_attachments", values, "image_path = ?",
                        new String[]{entry.getKey()});
            }
            for (Map.Entry<Long, TaskFileAttachment> entry : restoredFiles.entrySet()) {
                TaskFileAttachment file = entry.getValue();
                ContentValues values = new ContentValues();
                values.put("stored_name", file.storedName);
                values.put("display_name", file.displayName);
                values.put("mime_type", file.mimeType);
                values.put("size_bytes", file.sizeBytes);
                staged.update("task_files", values, "id = ?",
                        new String[]{Long.toString(entry.getKey())});
            }
            for (Map.Entry<Long, Long> entry : calendarLinks.entrySet()) {
                ContentValues values = new ContentValues();
                if (entry.getValue() == null) {
                    values.putNull("calendar_event_id");
                    values.put("needs_confirmation", 1);
                } else {
                    values.put("calendar_event_id", entry.getValue());
                    values.put("needs_confirmation", 0);
                }
                staged.update("event_candidates", values, "id = ?",
                        new String[]{Long.toString(entry.getKey())});
            }
            staged.execSQL("UPDATE tasks SET status='failed', error_message=? WHERE status IN ('processing','queued')",
                    new Object[]{"从备份恢复后需要重新解析；请手动重试"});
            try (Cursor checkpoint = staged.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
                if (checkpoint.moveToFirst() && checkpoint.getInt(0) != 0)
                    throw new IOException("备份数据库仍在写入，无法完成恢复准备");
            }
        } finally {
            staged.close();
        }
        JobSchedulerBridge.cancel(context);
        installedPrevious = installDatabase(context, stagedDb);
        databaseInstalled = true;
        File stagedOutputs = prepareOutputs(context, prepared);
        if (previousOutputs.exists()) deleteTree(previousOutputs);
        if (outputDirectory.exists() && !outputDirectory.renameTo(previousOutputs))
            throw new IOException("无法创建解析草稿回滚点");
        if (!stagedOutputs.renameTo(outputDirectory)) {
            if (previousOutputs.exists()) previousOutputs.renameTo(outputDirectory);
            throw new IOException("无法安装解析草稿");
        }
        ConfigBackup.apply(context, prepared.config);
        HomeTimelinePreferences.setItemLimit(context,
                prepared.manifest.optInt("homeTimelineLimit", HomeTimelinePreferences.DEFAULT_ITEM_LIMIT));
        if (installedPrevious != null) installedPrevious.delete();
        deleteTree(previousOutputs);
        return calendarLinks.containsValue(null)
                ? "数据已恢复；部分日程因日历权限不可用已转为待确认。"
                : "收件箱、日程与设置已恢复。";
        } catch (Exception exception) {
            try { ConfigBackup.apply(context, previousConfig); } catch (Exception ignored) { }
            HomeTimelinePreferences.setItemLimit(context, previousTimelineLimit);
            if (databaseInstalled && installedPrevious != null && installedPrevious.exists()) {
                File current = context.getDatabasePath("chrona.db");
                current.delete();
                installedPrevious.renameTo(current);
            } else if (databaseInstalled) {
                File current = context.getDatabasePath("chrona.db");
                current.delete();
                new File(current.getPath() + "-wal").delete();
                new File(current.getPath() + "-shm").delete();
            }
            if (previousOutputs.exists()) {
                deleteTree(outputDirectory);
                previousOutputs.renameTo(outputDirectory);
            }
            for (File file : createdImages) file.delete();
            for (Uri uri : createdFiles) context.getContentResolver().delete(uri, null, null);
            CalendarStore calendar = new CalendarStore(context);
            for (Long eventId : createdEvents) {
                try { calendar.deleteEvent(eventId); } catch (RuntimeException ignored) { }
            }
            rescheduleQueuedTasks(context);
            throw exception;
        }
    }

    private static void rescheduleQueuedTasks(Context context) {
        try (TaskStore store = new TaskStore(context)) {
            for (TaskRecord task : store.listTasks()) {
                if (TaskRecord.QUEUED.equals(task.status)) {
                    try { ProcessingJobService.enqueue(context, task.id); }
                    catch (RuntimeException ignored) { }
                }
            }
        } catch (RuntimeException ignored) { }
    }

    private static Map<String, String> restoreImages(Context context, Prepared prepared)
            throws IOException, org.json.JSONException {
        return restoreImages(context, prepared, null);
    }

    private static Map<String, String> restoreImages(Context context, Prepared prepared,
            List<File> created) throws IOException, org.json.JSONException {
        Map<String, String> result = new HashMap<>();
        File directory = new File(context.getFilesDir(), "attachments");
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建图片目录");
        JSONArray images = prepared.manifest.getJSONArray("images");
        for (int i = 0; i < images.length(); i++) {
            JSONObject item = images.getJSONObject(i);
            String oldName = item.getString("name");
            String newName = UUID.randomUUID() + ".jpg";
            File target = new File(directory, newName);
            copyFile(new File(prepared.directory, item.getString("entry")), target);
            if (created != null) created.add(target);
            result.put(oldName, newName);
        }
        return result;
    }

    private static Map<Long, TaskFileAttachment> restoreFiles(Context context, Prepared prepared)
            throws Exception {
        return restoreFiles(context, prepared, null);
    }

    private static Map<Long, TaskFileAttachment> restoreFiles(Context context, Prepared prepared,
            List<Uri> created) throws Exception {
        Map<Long, TaskFileAttachment> result = new HashMap<>();
        JSONArray files = prepared.manifest.getJSONArray("files");
        Map<String, TaskFileAttachment> byEntry = new HashMap<>();
        for (int i = 0; i < files.length(); i++) {
            JSONObject item = files.getJSONObject(i);
            String entry = item.getString("entry");
            TaskFileAttachment restored = byEntry.get(entry);
            if (restored == null) {
                restored = writeDownload(context, new File(prepared.directory, entry),
                        item.getString("name"), item.getString("mime"));
                if (created != null) created.add(Uri.parse(restored.storedName));
                byEntry.put(entry, restored);
            }
            result.put(item.getLong("rowId"), new TaskFileAttachment(item.getLong("rowId"), 0,
                    restored.storedName, item.getString("name"), item.getString("mime"),
                    new File(prepared.directory, entry).length()));
        }
        return result;
    }

    private static TaskFileAttachment writeDownload(Context context, File source, String name,
            String mime) throws IOException {
        android.content.ContentResolver resolver = context.getContentResolver();
        Uri uri;
        long size = source.length();
        if (Build.VERSION.SDK_INT >= 29) {
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, safeFileName(name));
            values.put(MediaStore.Downloads.MIME_TYPE, mime);
            values.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS + "/Chrona");
            values.put(MediaStore.Downloads.IS_PENDING, 1);
            uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new IOException("无法创建公共 Downloads/Chrona 文件");
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = resolver.openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("无法写入公共附件");
                copy(input, output);
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.Downloads.IS_PENDING, 0);
                resolver.update(uri, ready, null, null);
            } catch (Exception exception) {
                resolver.delete(uri, null, null);
                if (exception instanceof IOException) throw (IOException) exception;
                throw new IOException(exception);
            }
        } else {
            if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED)
                throw new IOException("请授予存储权限后恢复普通附件");
            File downloads = Environment.getExternalStoragePublicDirectory(
                    Environment.DIRECTORY_DOWNLOADS);
            File directory = new File(downloads, "Chrona");
            if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("无法创建 Downloads/Chrona");
            File target = uniqueFile(directory, safeFileName(name));
            copyFile(source, target);
            ContentValues values = new ContentValues();
            values.put(MediaStore.MediaColumns.DATA, target.getAbsolutePath());
            values.put(MediaStore.MediaColumns.DISPLAY_NAME, target.getName());
            values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            values.put(MediaStore.MediaColumns.SIZE, size);
            uri = resolver.insert(MediaStore.Files.getContentUri("external"), values);
            if (uri == null) throw new IOException("无法登记恢复后的公共附件");
        }
        return new TaskFileAttachment(0, 0, uri.toString(), safeFileName(name), mime, size);
    }

    private static Map<Long, Long> restoreCalendarLinks(Context context, Prepared prepared,
            List<Long> createdEvents) {
        Map<Long, Long> links = new HashMap<>();
        Set<Long> usedEventIds = new HashSet<>();
        CalendarStore calendar = new CalendarStore(context);
        JSONArray events = prepared.manifest.optJSONArray("events");
        if (events == null) return links;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            long candidateId = event.optLong("candidateId", -1);
            if (candidateId <= 0) continue;
            try {
                if (!calendar.hasReadPermission() || !calendar.hasWritePermission()) {
                    links.put(candidateId, null);
                    continue;
                }
                JSONArray remindersJson = event.optJSONArray("reminders");
                ArrayList<Integer> reminders = new ArrayList<>();
                if (remindersJson != null) for (int j = 0; j < remindersJson.length(); j++)
                    reminders.add(remindersJson.getInt(j));
                CalendarStore.EventInput input = new CalendarStore.EventInput(
                        event.getString("title"), event.getLong("start"), event.getLong("end"),
                        event.optString("timeZone", "UTC"), event.optBoolean("allDay"),
                        nullable(event, "description"), nullable(event, "location"), reminders);
                Long id = null;
                long oldId = event.optLong("oldEventId", -1);
                if (oldId > 0) {
                    CalendarStore.EventRecord old = calendar.getEvent(oldId);
                    if (sameEvent(old, input) && !usedEventIds.contains(old.id)) id = old.id;
                }
                if (id == null) {
                    List<Long> matches = calendar.findMatchingEvents(input);
                    for (Long match : matches) {
                        if (!usedEventIds.contains(match)) {
                            id = match;
                            break;
                        }
                    }
                }
                if (id == null) {
                    id = calendar.insertEvent(input);
                    createdEvents.add(id);
                }
                usedEventIds.add(id);
                links.put(candidateId, id);
            } catch (Exception exception) {
                links.put(candidateId, null);
            }
        }
        return links;
    }

    private static boolean sameEvent(CalendarStore.EventRecord event,
            CalendarStore.EventInput input) {
        return event != null && event.title.equals(input.title)
                && event.startMillis == input.startMillis && event.endMillis == input.endMillis
                && event.allDay == input.allDay;
    }

    private static String nullable(JSONObject object, String key) {
        Object value = object.opt(key);
        return value == null || value == JSONObject.NULL ? null : String.valueOf(value);
    }

    private static File installDatabase(Context context, File staged) throws IOException {
        try (TaskStore store = new TaskStore(context)) {
            SQLiteDatabase db = store.getWritableDatabase();
            try (Cursor checkpoint = db.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
                if (checkpoint.moveToFirst() && checkpoint.getInt(0) != 0)
                    throw new IOException("数据库仍在使用，无法恢复");
            }
        }
        File target = context.getDatabasePath("chrona.db");
        File temp = new File(target.getParentFile(), "chrona.db.restore");
        File previous = new File(target.getParentFile(), "chrona.db.previous");
        copyFile(staged, temp);
        if (previous.exists() && !previous.delete()) throw new IOException("无法清理旧恢复点");
        if (target.exists() && !target.renameTo(previous)) throw new IOException("无法创建恢复回滚点");
        if (!temp.renameTo(target)) {
            if (previous.exists()) previous.renameTo(target);
            throw new IOException("无法安装恢复数据库");
        }
        new File(target.getPath() + "-wal").delete();
        new File(target.getPath() + "-shm").delete();
        return previous.exists() ? previous : null;
    }

    private static File prepareOutputs(Context context, Prepared prepared) throws Exception {
        File directory = new File(prepared.directory, "restored-model-output");
        if (!directory.mkdirs()) throw new IOException("无法准备解析草稿");
        JSONArray outputs = prepared.manifest.getJSONArray("outputs");
        for (int i = 0; i < outputs.length(); i++) {
            long id = outputs.getLong(i);
            copyFile(new File(prepared.directory, "outputs/" + id + ".txt"),
                    new File(directory, "task-" + id + ".txt"));
        }
        return directory;
    }

    private static void unzip(File archive, File directory) throws IOException {
        long total = 0;
        Set<String> seen = new HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(
                new FileInputStream(archive)))) {
            ZipEntry entry;
            byte[] buffer = new byte[32 * 1024];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if (!safeEntry(name)) throw new IOException("备份含有非法路径");
                if (!seen.add(name)) throw new IOException("备份中存在重复文件项");
                File target = new File(directory, name);
                if (entry.isDirectory()) {
                    if (!target.isDirectory() && !target.mkdirs()) throw new IOException("无法解压备份");
                    continue;
                }
                File parent = target.getParentFile();
                if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("无法创建恢复目录");
                long entrySize = 0;
                try (OutputStream output = new BufferedOutputStream(new FileOutputStream(target))) {
                    int count;
                    while ((count = zip.read(buffer)) != -1) {
                        entrySize += count;
                        total += count;
                        if (entrySize > MAX_ENTRY_BYTES || total > MAX_ARCHIVE_BYTES)
                            throw new IOException("备份文件过大");
                        output.write(buffer, 0, count);
                    }
                }
                zip.closeEntry();
            }
        }
    }

    private static boolean safeEntry(String name) {
        return "manifest.json".equals(name) || "chrona.db".equals(name)
                || name.matches("images/[0-9a-fA-F\\-]{36}\\.jpg")
                || name.matches("files/[0-9]+\\.bin")
                || name.matches("outputs/[0-9]+\\.txt");
    }

    private static String safeFileName(String name) {
        String value = name == null ? "附件" : name.replaceAll("[\\p{Cntrl}/\\\\]", "_")
                .trim();
        if (value.isEmpty()) value = "附件";
        return value.length() > 160 ? value.substring(value.length() - 160) : value;
    }

    private static File uniqueFile(File directory, String name) throws IOException {
        int dot = name.lastIndexOf('.');
        String stem = dot <= 0 ? name : name.substring(0, dot);
        String suffix = dot <= 0 ? "" : name.substring(dot);
        for (int i = 0; i < 1000; i++) {
            File candidate = new File(directory, i == 0 ? name : stem + " (" + i + ")" + suffix);
            if (candidate.createNewFile()) return candidate;
        }
        throw new IOException("公共附件目录同名文件过多");
    }

    private static void addFile(ZipOutputStream zip, File file, String entry) throws IOException {
        zip.putNextEntry(new ZipEntry(entry));
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) zip.write(buffer, 0, count);
        }
        zip.closeEntry();
    }

    private static String readText(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            copyLimited(input, output, 4 * 1024 * 1024);
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static void copyLimited(InputStream input, OutputStream output, long max)
            throws IOException {
        byte[] buffer = new byte[32 * 1024];
        long total = 0;
        int count;
        while ((count = input.read(buffer)) != -1) {
            total += count;
            if (total > max) throw new IOException("备份文件过大");
            output.write(buffer, 0, count);
        }
    }

    private static void copy(InputStream input, OutputStream output) throws IOException {
        try (InputStream source = input; OutputStream target = output) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = source.read(buffer)) != -1) target.write(buffer, 0, count);
            target.flush();
        }
    }

    private static void copyFile(File source, File target) throws IOException {
        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs())
            throw new IOException("无法创建备份目录");
        try (InputStream input = new FileInputStream(source);
             OutputStream output = new FileOutputStream(target)) {
            copy(input, output);
        }
    }

    private static void deleteTree(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        file.delete();
    }

    private static final class JobSchedulerBridge {
        static void cancel(Context context) {
            android.app.job.JobScheduler scheduler = context.getSystemService(
                    android.app.job.JobScheduler.class);
            if (scheduler != null) scheduler.cancelAll();
        }
    }
}
