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
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
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
    private static final long MAX_WALLPAPER_BYTES = 64L * 1024 * 1024;
    private static final long MAX_MANIFEST_BYTES = 8L * 1024 * 1024;
    private static final String WALLPAPER_ENTRY = "wallpaper/background.bin";

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
            String wallpaperSummary = !manifest.has("wallpaperIncluded")
                    ? "· 旧备份不含背景图；导入会保留本机背景\n"
                    : manifest.optJSONObject("wallpaper") == null
                    ? "· 自定义背景图：未设置\n" : "· 自定义背景图：已包含\n";
            return "· 收件箱 " + manifest.optInt("tasks") + " 条，日程 "
                    + manifest.optInt("candidates") + " 项\n· 图片 "
                    + manifest.optInt("imageCount") + " 张，普通附件 "
                    + manifest.optInt("fileCount") + " 个\n" + wallpaperSummary
                    + (manifest.has("timetable") ? "· 导入课表："
                            + (manifest.isNull("timetable") ? "未设置\n" : "已包含\n") : "") + "· "
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
        File database = File.createTempFile("chrona-snapshot-", ".db", context.getCacheDir());
        List<File> extractedFiles = new ArrayList<>();
        try {
            try (TaskStore live = new TaskStore(context)) { live.createSnapshot(database); }
            JSONArray files = new JSONArray();
            JSONArray images = new JSONArray();
            JSONObject wallpaper = null;
            JSONArray outputs = new JSONArray();
            JSONArray events = new JSONArray();
            int taskCount;
            int candidateCount;
            Map<Long, TaskFileAttachment> attachmentRows = new HashMap<>();
            try (TaskStore store = TaskStore.openSnapshot(context, database)) {
                List<TaskRecord> tasks = store.listTasks();
                List<EventCandidate> candidates = store.listCandidates();
                taskCount = tasks.size();
                candidateCount = candidates.size();
                Map<String, String> copiedUris = new HashMap<>();
                int fileSequence = 0;
                for (TaskRecord task : tasks) {
                    for (TaskFileAttachment file : store.getFileAttachments(task.id)) {
                        attachmentRows.put(file.id, file);
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
            String wallpaperUri = ThemeStore.background(context);
            WallpaperPayload wallpaperPayload = wallpaperUri == null ? null
                    : inspectWallpaper(context, Uri.parse(wallpaperUri));
            if (wallpaperPayload != null) {
                wallpaper = new JSONObject();
                wallpaper.put("entry", WALLPAPER_ENTRY);
                wallpaper.put("mime", wallpaperPayload.mimeType);
                wallpaper.put("size", wallpaperPayload.size);
                wallpaper.put("sha256", wallpaperPayload.sha256);
            }
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
            manifest.put("includeSystemCalendar", HomeTimelinePreferences.includesSystemCalendar(context));
            manifest.put("config", ConfigBackup.export(context, includeApiKey));
            manifest.put("wallpaperIncluded", true);
            manifest.put("wallpaperEnabled", ThemeStore.backgroundEnabled(context));
            manifest.put("wallpaper", wallpaper == null ? JSONObject.NULL : wallpaper);
            String timetable = new TimetableStore(context).snapshot();
            manifest.put("timetable", timetable == null ? JSONObject.NULL : new JSONObject(timetable));
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
                    TaskFileAttachment found = attachmentRows.get(item.getLong("rowId"));
                    if (found == null) throw new IOException("普通附件记录不存在");
                    File extracted = new File(context.getCacheDir(), "chrona-source-"
                            + UUID.randomUUID());
                    extractedFiles.add(extracted);
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
                if (wallpaperPayload != null)
                    addWallpaper(zip, context, Uri.parse(wallpaperUri), wallpaperPayload);
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
        } finally {
            for (File extracted : extractedFiles) extracted.delete();
            for (String suffix : new String[]{"", "-wal", "-shm", "-journal"})
                new File(database.getAbsolutePath() + suffix).delete();
        }
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
            if (!manifestFile.isFile() || manifestFile.length() > MAX_MANIFEST_BYTES)
                throw new IOException("备份清单缺失或过大");
        JSONObject manifest = new JSONObject(readText(manifestFile));
            if (!APP.equals(manifest.optString("app")) || manifest.optInt("format") != FORMAT)
                throw new IOException("备份来源或版本不受支持");
            String config = manifest.getString("config");
            ConfigBackup.inspect(config);
            if (manifest.has("timetable") && !manifest.isNull("timetable"))
                TimetableStore.inspect(manifest.getJSONObject("timetable").toString());
            long[] databaseCounts = inspectDatabase(new File(directory, "chrona.db"));
            if (databaseCounts[0] != manifest.optInt("tasks", -1)
                    || databaseCounts[1] != manifest.optInt("candidates", -1))
                throw new IOException("备份清单与数据库记录数量不一致，已取消恢复");
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
        if (manifest.has("wallpaperIncluded")) {
            if (!manifest.optBoolean("wallpaperIncluded") || !manifest.has("wallpaper"))
                throw new IOException("背景图清单无效");
            if (!JSONObject.NULL.equals(manifest.get("wallpaper"))) {
                JSONObject wallpaper = manifest.getJSONObject("wallpaper");
                String entry = wallpaper.getString("entry");
                String mime = wallpaper.getString("mime");
                long size = wallpaper.getLong("size");
                String sha256 = wallpaper.getString("sha256");
                File payload = new File(directory, entry);
                if (!safeEntry(entry) || !WALLPAPER_ENTRY.equals(entry)
                        || !mime.startsWith("image/") || size <= 0 || size > MAX_WALLPAPER_BYTES
                        || !sha256.matches("[0-9a-f]{64}") || !payload.isFile()
                        || payload.length() != size
                        || !sha256.equals(sha256(payload, MAX_WALLPAPER_BYTES)))
                    throw new IOException("备份背景图缺失或校验失败");
            } else if (new File(directory, WALLPAPER_ENTRY).exists()) {
                throw new IOException("备份背景图清单不匹配");
            }
        }
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

    private static long[] inspectDatabase(File database) throws Exception {
        if (!database.isFile()) throw new IOException("备份中没有数据库");
        SQLiteDatabase db = SQLiteDatabase.openDatabase(database.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READONLY);
        try {
            try (Cursor cursor = db.rawQuery("PRAGMA integrity_check", null)) {
                if (!cursor.moveToFirst() || !"ok".equalsIgnoreCase(cursor.getString(0)))
                    throw new IOException("备份数据库完整性检查失败");
            }
            try (Cursor cursor = db.rawQuery("PRAGMA user_version", null)) {
                if (!cursor.moveToFirst() || cursor.getInt(0) < 6 || cursor.getInt(0) > 9)
                    throw new IOException("备份数据库版本与当前应用不兼容");
            }
            long tasks;
            long candidates;
            try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM tasks", null)) {
                if (!cursor.moveToFirst()) throw new IOException("无法读取备份收件箱数量");
                tasks = cursor.getLong(0);
            }
            try (Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM event_candidates", null)) {
                if (!cursor.moveToFirst()) throw new IOException("无法读取备份日程数量");
                candidates = cursor.getLong(0);
            }
            return new long[]{tasks, candidates};
        } finally {
            db.close();
        }
    }

    public static String restore(Context context, Prepared prepared) throws Exception {
        long[] previousDatabaseCounts = new long[2];
        try (TaskStore current = new TaskStore(context)) {
            List<TaskRecord> currentTasks = current.listTasks();
            for (TaskRecord task : currentTasks) {
                if (TaskRecord.PROCESSING.equals(task.status))
                    throw new IOException("当前仍有任务正在解析，请完成或停止解析后再恢复备份");
            }
            previousDatabaseCounts[0] = currentTasks.size();
            previousDatabaseCounts[1] = current.listCandidates().size();
        }
        File stagedDb = new File(prepared.directory, "chrona.db");
        ArrayList<File> createdImages = new ArrayList<>();
        ArrayList<Uri> createdFiles = new ArrayList<>();
        ArrayList<Long> createdEvents = new ArrayList<>();
        File installedPrevious = null;
        boolean[] databaseInstallStarted = new boolean[]{false};
        boolean databaseInstalled = false;
        String previousWallpaper = ThemeStore.background(context);
        boolean previousWallpaperEnabled = ThemeStore.backgroundEnabled(context);
        WallpaperMedia restoredWallpaper = null;
        boolean wallpaperApplied = false;
        File outputDirectory = new File(context.getFilesDir(), "model-output");
        File previousOutputs = new File(context.getFilesDir(), "model-output.previous");
        String previousConfig = ConfigBackup.export(context, true);
        int previousTimelineLimit = HomeTimelinePreferences.getItemLimit(context);
        boolean previousSystemCalendar = HomeTimelinePreferences.includesSystemCalendar(context);
        String previousTimetable = new TimetableStore(context).snapshot();
        boolean timetableApplied = false;
        Map<Long, Long> calendarLinks = new HashMap<>();
        try {
        Map<String, String> imageNames = restoreImages(context, prepared, createdImages);
        Map<Long, TaskFileAttachment> restoredFiles = restoreFiles(context, prepared, createdFiles);
        if (prepared.manifest.optBoolean("wallpaperIncluded", false)) {
            JSONObject wallpaper = prepared.manifest.optJSONObject("wallpaper");
            if (wallpaper != null) restoredWallpaper = restoreWallpaper(context, prepared, wallpaper);
        }
        calendarLinks = restoreCalendarLinks(context, prepared, createdEvents);
        SQLiteDatabase staged = SQLiteDatabase.openDatabase(stagedDb.getAbsolutePath(), null,
                SQLiteDatabase.OPEN_READWRITE);
        try {
            if (staged.getVersion() == 6) {
                staged.beginTransaction();
                try {
                    TaskStore.addEndTimeProvenance(staged);
                    staged.setVersion(7);
                    staged.setTransactionSuccessful();
                } finally { staged.endTransaction(); }
            }
            if (staged.getVersion() < 8) {
                staged.beginTransaction();
                try {
                    TaskStore.addScheduleBrowsing(staged);
                    staged.setVersion(8);
                    staged.setTransactionSuccessful();
                } finally { staged.endTransaction(); }
            }
            if (staged.getVersion() < 9) {
                staged.beginTransaction();
                try {
                    TaskStore.addUncertaintyLevel(staged);
                    staged.setVersion(9);
                    staged.setTransactionSuccessful();
                } finally { staged.endTransaction(); }
            }
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
                    values.put("uncertainty_level", EventCandidate.DOUBTFUL);
                } else {
                    values.put("calendar_event_id", entry.getValue());
                    values.put("needs_confirmation", 0);
                    values.put("uncertainty_level", EventCandidate.CERTAIN);
                }
                staged.update("event_candidates", values, "id = ?",
                        new String[]{Long.toString(entry.getKey())});
            }
            staged.execSQL("UPDATE tasks SET status='failed', error_message=? WHERE status IN ('processing','queued')",
                    new Object[]{"从备份恢复后需要重新解析；请手动重试"});
            staged.execSQL("UPDATE tasks SET status='needs_review',error_message=NULL WHERE status='ready' "
                    + "AND EXISTS(SELECT 1 FROM event_candidates WHERE task_id=tasks.id "
                    + "AND calendar_event_id IS NULL)");
            try (Cursor checkpoint = staged.rawQuery("PRAGMA wal_checkpoint(FULL)", null)) {
                if (checkpoint.moveToFirst() && checkpoint.getInt(0) != 0)
                    throw new IOException("备份数据库仍在写入，无法完成恢复准备");
            }
        } finally {
            staged.close();
        }
        JobSchedulerBridge.cancel(context);
        installedPrevious = installDatabase(context, stagedDb, databaseInstallStarted);
        databaseInstalled = true;
        long[] installedCounts = inspectDatabase(context.getDatabasePath("chrona.db"));
        if (installedCounts[0] != prepared.manifest.optInt("tasks", -1)
                || installedCounts[1] != prepared.manifest.optInt("candidates", -1))
            throw new IOException("恢复后的数据库记录数量与备份清单不一致");
        File stagedOutputs = prepareOutputs(context, prepared);
        if (previousOutputs.exists()) deleteTree(previousOutputs);
        if (outputDirectory.exists() && !outputDirectory.renameTo(previousOutputs))
            throw new IOException("无法创建解析草稿回滚点");
        if (!stagedOutputs.renameTo(outputDirectory)) {
            if (previousOutputs.exists()) previousOutputs.renameTo(outputDirectory);
            throw new IOException("无法安装解析草稿");
        }
        // Applying appearance preferences may publish a theme update. An Activity context would
        // call Activity.recreate() from this background restore thread. The Activity itself
        // recreates on the main thread after this method succeeds.
        ConfigBackup.apply(context.getApplicationContext(), prepared.config);
        if (prepared.manifest.optBoolean("wallpaperIncluded", false)) {
            wallpaperApplied = true;
            ThemeStore.setBackground(context.getApplicationContext(),
                    restoredWallpaper == null ? null : restoredWallpaper.uri.toString());
            ThemeStore.setBackgroundEnabled(context.getApplicationContext(),
                    prepared.manifest.optBoolean("wallpaperEnabled", true));
        }
        HomeTimelinePreferences.setItemLimit(context,
                prepared.manifest.optInt("homeTimelineLimit", HomeTimelinePreferences.DEFAULT_ITEM_LIMIT));
        HomeTimelinePreferences.setIncludesSystemCalendar(context,
                prepared.manifest.optBoolean("includeSystemCalendar", false));
        if (prepared.manifest.has("timetable")) {
            timetableApplied = true;
            new TimetableStore(context).restore(prepared.manifest.isNull("timetable")
                    ? null : prepared.manifest.getJSONObject("timetable").toString());
        }
        if (installedPrevious != null) installedPrevious.delete();
        deleteTree(previousOutputs);
        return calendarLinks.containsValue(null)
                ? "数据已恢复；部分日程因日历权限不可用已转为待确认。"
                : "收件箱、日程与设置已恢复。";
        } catch (Exception exception) {
            IOException failure = new IOException("恢复失败：" + exception.getMessage(), exception);
            if (timetableApplied) {
                try { new TimetableStore(context).restore(previousTimetable); }
                catch (Exception rollbackTimetableFailure) { failure.addSuppressed(rollbackTimetableFailure); }
            }
            try {
                ConfigBackup.apply(context.getApplicationContext(), previousConfig);
            } catch (Exception rollbackConfigFailure) {
                failure.addSuppressed(rollbackConfigFailure);
            }
            try {
                HomeTimelinePreferences.setItemLimit(context, previousTimelineLimit);
                HomeTimelinePreferences.setIncludesSystemCalendar(context, previousSystemCalendar);
            } catch (Exception rollbackPreferenceFailure) {
                failure.addSuppressed(rollbackPreferenceFailure);
            }
            if (wallpaperApplied) {
                try {
                    ThemeStore.setBackground(context.getApplicationContext(), previousWallpaper);
                    ThemeStore.setBackgroundEnabled(context.getApplicationContext(), previousWallpaperEnabled);
                } catch (Exception rollbackWallpaperFailure) {
                    failure.addSuppressed(rollbackWallpaperFailure);
                }
            }
            if (restoredWallpaper != null
                    && !restoredWallpaper.uri.toString().equals(
                    ThemeStore.background(context.getApplicationContext()))) {
                try {
                    cleanupWallpaper(context, restoredWallpaper);
                } catch (Exception cleanupWallpaperFailure) {
                    failure.addSuppressed(cleanupWallpaperFailure);
                }
            }
            boolean databaseRollbackSucceeded = !databaseInstalled && !databaseInstallStarted[0];
            if (databaseInstalled || databaseInstallStarted[0]) {
                File current = context.getDatabasePath("chrona.db");
                File rollbackCopy = installedPrevious != null
                        ? installedPrevious
                        : new File(current.getParentFile(), "chrona.db.previous");
                try {
                    deleteDatabaseFile(current);
                    if (rollbackCopy.exists()) {
                        if (!rollbackCopy.renameTo(current))
                            throw new IOException("无法恢复旧数据库");
                        long[] restoredCounts = inspectDatabase(current);
                        if (restoredCounts[0] != previousDatabaseCounts[0]
                                || restoredCounts[1] != previousDatabaseCounts[1])
                            throw new IOException("恢复后的旧数据库记录数量不一致");
                    } else if (previousDatabaseCounts[0] != 0
                            || previousDatabaseCounts[1] != 0) {
                        throw new IOException("旧数据库回滚副本不存在");
                    }
                    databaseRollbackSucceeded = true;
                } catch (Exception rollbackDatabaseFailure) {
                    String recoveryPath = rollbackCopy.exists()
                            ? "旧数据库副本保留在 " + rollbackCopy.getAbsolutePath()
                            : "请检查当前数据库文件 " + current.getAbsolutePath();
                    failure.addSuppressed(new IOException("旧数据库未能自动还原；" + recoveryPath,
                            rollbackDatabaseFailure));
                }
            }
            if (databaseRollbackSucceeded) {
                if (previousOutputs.exists()) {
                    try {
                        deleteTree(outputDirectory);
                        if (!previousOutputs.renameTo(outputDirectory))
                            throw new IOException("无法恢复旧解析草稿目录");
                    } catch (Exception rollbackOutputsFailure) {
                        failure.addSuppressed(rollbackOutputsFailure);
                    }
                }
                for (File file : createdImages) {
                    try {
                        if (file.exists() && !file.delete() && file.exists())
                            throw new IOException("无法清理恢复图片：" + file.getName());
                    } catch (Exception cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                for (Uri uri : createdFiles) {
                    try {
                        context.getContentResolver().delete(uri, null, null);
                    } catch (Exception cleanupFailure) {
                        failure.addSuppressed(cleanupFailure);
                    }
                }
                try {
                    CalendarStore calendar = new CalendarStore(context);
                    for (Long eventId : createdEvents) {
                        try {
                            calendar.deleteEvent(eventId);
                        } catch (Exception cleanupFailure) {
                            failure.addSuppressed(cleanupFailure);
                        }
                    }
                } catch (Exception cleanupFailure) {
                    failure.addSuppressed(cleanupFailure);
                }
                rescheduleQueuedTasks(context);
            }
            throw failure;
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

    private static WallpaperPayload inspectWallpaper(Context context, Uri uri) throws Exception {
        String mime = context.getContentResolver().getType(uri);
        if (mime == null || !mime.startsWith("image/"))
            throw new IOException("无法识别自定义背景图格式");
        MessageDigest digest = newSha256();
        long size = 0;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("无法读取自定义背景图");
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                if (size > MAX_WALLPAPER_BYTES) throw new IOException("自定义背景图超过 64 MiB");
                digest.update(buffer, 0, count);
            }
        }
        if (size == 0) throw new IOException("自定义背景图为空");
        return new WallpaperPayload(mime, size, toHex(digest.digest()));
    }

    private static void addWallpaper(ZipOutputStream zip, Context context, Uri uri,
            WallpaperPayload expected) throws IOException {
        zip.putNextEntry(new ZipEntry(WALLPAPER_ENTRY));
        MessageDigest digest = newSha256();
        long size = 0;
        try (InputStream input = context.getContentResolver().openInputStream(uri)) {
            if (input == null) throw new IOException("无法读取自定义背景图");
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                if (size > MAX_WALLPAPER_BYTES) throw new IOException("自定义背景图超过 64 MiB");
                digest.update(buffer, 0, count);
                zip.write(buffer, 0, count);
            }
        }
        zip.closeEntry();
        if (size != expected.size || !toHex(digest.digest()).equals(expected.sha256))
            throw new IOException("备份期间自定义背景图发生变化，请重试");
    }

    private static WallpaperMedia restoreWallpaper(Context context, Prepared prepared,
            JSONObject metadata) throws Exception {
        File source = new File(prepared.directory, metadata.getString("entry"));
        String mime = metadata.getString("mime");
        String sha256 = metadata.getString("sha256");
        long size = metadata.getLong("size");
        String name = "chrona-background-" + UUID.randomUUID() + wallpaperExtension(mime);
        android.content.ContentResolver resolver = context.getContentResolver();
        Uri uri = null;
        File legacyFile = null;
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                values.put(MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/Chrona");
                values.put(MediaStore.MediaColumns.IS_PENDING, 1);
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("无法创建公共背景图条目");
                try (OutputStream output = resolver.openOutputStream(uri, "w")) {
                    if (output == null) throw new IOException("无法写入公共背景图");
                    copyWallpaperVerified(source, output, size, sha256);
                }
                ContentValues ready = new ContentValues();
                ready.put(MediaStore.MediaColumns.IS_PENDING, 0);
                if (resolver.update(uri, ready, null, null) <= 0)
                    throw new IOException("无法发布公共背景图");
            } else {
                if (context.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
                        != android.content.pm.PackageManager.PERMISSION_GRANTED)
                    throw new IOException("请授予存储权限后恢复公共背景图");
                File pictures = Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES);
                File directory = new File(pictures, "Chrona");
                if (!directory.isDirectory() && !directory.mkdirs())
                    throw new IOException("无法创建 Pictures/Chrona");
                legacyFile = new File(directory, name);
                try (OutputStream output = new FileOutputStream(legacyFile)) {
                    copyWallpaperVerified(source, output, size, sha256);
                }
                ContentValues values = new ContentValues();
                values.put("_data", legacyFile.getAbsolutePath());
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name);
                values.put(MediaStore.MediaColumns.TITLE, name);
                values.put(MediaStore.MediaColumns.MIME_TYPE, mime);
                values.put(MediaStore.MediaColumns.SIZE, size);
                uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (uri == null) throw new IOException("无法登记公共背景图");
            }
            return new WallpaperMedia(uri, legacyFile);
        } catch (Exception exception) {
            if (uri != null) {
                try { resolver.delete(uri, null, null); }
                catch (RuntimeException cleanupFailure) { exception.addSuppressed(cleanupFailure); }
            }
            if (legacyFile != null && legacyFile.exists() && !legacyFile.delete())
                exception.addSuppressed(new IOException("无法清理未完成的公共背景图"));
            throw exception;
        }
    }

    private static void copyWallpaperVerified(File source, OutputStream output, long expectedSize,
            String expectedSha256) throws IOException {
        MessageDigest digest = newSha256();
        long size = 0;
        try (InputStream input = new BufferedInputStream(new FileInputStream(source))) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                if (size > MAX_WALLPAPER_BYTES) throw new IOException("自定义背景图超过 64 MiB");
                digest.update(buffer, 0, count);
                output.write(buffer, 0, count);
            }
        }
        output.flush();
        if (size != expectedSize || !toHex(digest.digest()).equals(expectedSha256))
            throw new IOException("恢复背景图校验失败");
    }

    private static void cleanupWallpaper(Context context, WallpaperMedia wallpaper)
            throws IOException {
        IOException failure = null;
        try {
            if (context.getContentResolver().delete(wallpaper.uri, null, null) <= 0)
                failure = new IOException("无法删除本次恢复的背景图条目");
        } catch (RuntimeException exception) {
            failure = new IOException("无法删除本次恢复的背景图条目", exception);
        }
        if (wallpaper.legacyFile != null && wallpaper.legacyFile.exists()
                && !wallpaper.legacyFile.delete() && wallpaper.legacyFile.exists()) {
            IOException fileFailure = new IOException("无法删除本次恢复的公共背景图文件");
            if (failure == null) failure = fileFailure;
            else failure.addSuppressed(fileFailure);
        }
        if (failure != null) throw failure;
    }

    private static String wallpaperExtension(String mime) {
        String extension = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
        if (extension == null || !extension.matches("[A-Za-z0-9]{1,8}")) return ".img";
        return "." + extension.toLowerCase(java.util.Locale.ROOT);
    }

    private static MessageDigest newSha256() throws IOException {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("设备不支持 SHA-256", exception);
        }
    }

    private static String sha256(File file, long maxBytes) throws IOException {
        MessageDigest digest = newSha256();
        long size = 0;
        try (InputStream input = new BufferedInputStream(new FileInputStream(file))) {
            byte[] buffer = new byte[32 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                size += count;
                if (size > maxBytes) throw new IOException("备份文件过大");
                digest.update(buffer, 0, count);
            }
        }
        return toHex(digest.digest());
    }

    private static String toHex(byte[] bytes) {
        StringBuilder result = new StringBuilder(bytes.length * 2);
        for (byte value : bytes) result.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        return result.toString();
    }

    private static final class WallpaperPayload {
        final String mimeType;
        final long size;
        final String sha256;
        WallpaperPayload(String mimeType, long size, String sha256) {
            this.mimeType = mimeType;
            this.size = size;
            this.sha256 = sha256;
        }
    }

    private static final class WallpaperMedia {
        final Uri uri;
        final File legacyFile;
        WallpaperMedia(Uri uri, File legacyFile) {
            this.uri = uri;
            this.legacyFile = legacyFile;
        }
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
        Map<Long, Long> remappedEvents = new HashMap<>();
        Set<Long> usedEventIds = new HashSet<>();
        CalendarStore calendar = new CalendarStore(context);
        JSONArray events = prepared.manifest.optJSONArray("events");
        if (events == null) return links;
        for (int i = 0; i < events.length(); i++) {
            JSONObject event = events.optJSONObject(i);
            if (event == null) continue;
            long candidateId = event.optLong("candidateId", -1);
            if (candidateId <= 0) continue;
            long oldId = event.optLong("oldEventId", -1);
            if (oldId > 0 && remappedEvents.containsKey(oldId)) {
                links.put(candidateId, remappedEvents.get(oldId));
                continue;
            }
            try {
                if (!calendar.hasReadPermission() || !calendar.hasWritePermission()) {
                    links.put(candidateId, null);
                    if (oldId > 0) remappedEvents.put(oldId, null);
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
                if (oldId > 0) remappedEvents.put(oldId, id);
            } catch (Exception exception) {
                links.put(candidateId, null);
                if (oldId > 0) remappedEvents.put(oldId, null);
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

    private static File installDatabase(Context context, File staged, boolean[] installStarted)
            throws IOException {
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
        if (previous.exists())
            throw new IOException("发现未处理的旧数据库恢复副本，已保留：" + previous.getAbsolutePath());
        copyFile(staged, temp);
        if (target.exists()) {
            if (!target.renameTo(previous)) throw new IOException("无法创建恢复回滚点");
            installStarted[0] = true;
        }
        if (!temp.renameTo(target)) {
            throw new IOException("无法安装恢复数据库");
        }
        new File(target.getPath() + "-wal").delete();
        new File(target.getPath() + "-shm").delete();
        return previous.exists() ? previous : null;
    }

    private static void deleteDatabaseFile(File database) throws IOException {
        File[] files = new File[]{database, new File(database.getPath() + "-wal"),
                new File(database.getPath() + "-shm")};
        for (File file : files) {
            if (file.exists() && !file.delete() && file.exists())
                throw new IOException("无法移除恢复数据库文件：" + file.getName());
        }
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
                        long entryLimit = WALLPAPER_ENTRY.equals(name)
                                ? MAX_WALLPAPER_BYTES : MAX_ENTRY_BYTES;
                        if (entrySize > entryLimit || total > MAX_ARCHIVE_BYTES) {
                            throw new IOException("备份文件过大");
                        }
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
                || WALLPAPER_ENTRY.equals(name)
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
