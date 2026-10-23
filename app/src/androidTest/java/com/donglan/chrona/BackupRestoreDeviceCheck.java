package com.donglan.chrona;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import com.donglan.chrona.classroom.ClassroomArchive;
import com.donglan.chrona.classroom.ClassroomStore;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real Android ZIP / SQLite round trip, confined to a disposable emulator's private subdirectory. */
final class BackupRestoreDeviceCheck {
    static String run(Instrumentation instrumentation) throws Exception {
        Context target = instrumentation.getTargetContext();
        FixtureIsolation.require(target);
        File root = new File(target.getCacheDir(), "backup-device-" + UUID.randomUUID());
        Sandbox source = new Sandbox(target, new File(root, "source"));
        Sandbox restored = new Sandbox(target, new File(root, "restored"));
        long taskId;
        try (TaskStore store = new TaskStore(source)) {
            taskId = store.insertTask("备份往返原文", null, "isolated-test", 123456L);
            store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW, "保留提示");
        }
        File output = new File(source.getFilesDir(), "model-output/task-" + taskId + ".txt");
        output.getParentFile().mkdirs();
        Files.write(output.toPath(), "完整解析结果".getBytes(StandardCharsets.UTF_8));
        JSONObject document = document();
        File classroom = new File(ClassroomStore.root(source), "shared/101-202");
        ClassroomArchive.write(classroom, document, Map.of());
        require(document.toString().getBytes(StandardCharsets.UTF_8).length > 4 * 1024 * 1024,
                "fixture crosses the former hidden 4 MiB limit");
        File archive = new File(root, "roundtrip.zip");
        ChronaDataBackup.write(source, Uri.fromFile(archive), false);
        try (TaskStore store = new TaskStore(restored)) {
            store.insertTask("旧数据应由备份替换", null, "isolated-test", 1L);
        }
        File oldOutput = new File(restored.getFilesDir(), "model-output/old.txt");
        oldOutput.getParentFile().mkdirs();
        Files.write(oldOutput.toPath(), "旧输出".getBytes(StandardCharsets.UTF_8));
        ChronaDataBackup.Prepared prepared = ChronaDataBackup.prepare(restored, Uri.fromFile(archive));
        verifySyncConstructionFailure(restored);
        // No credentials are installed: exercise enabled -> disabled without any external access.
        new WebDavSettingsStore(restored).automatic(true);
        new ZjuSettingsStore(restored).options(true, false);
        require(new WebDavSettingsStore(restored).automatic() && new ZjuSettingsStore(restored).enabled(),
                "target sync switches are enabled before restore");
        try (TaskStore held = new TaskStore(restored)) {
            require(held.listTasks().size() == 1, "hold a real old database handle");
            try {
                ChronaDataBackup.restore(restored, prepared);
                throw new AssertionError("restore must refuse an open database handle");
            } catch (IllegalStateException busy) {
                require(busy.getMessage().contains("数据仍在使用"), "busy restore is explicit");
            }
            require("旧数据应由备份替换".equals(held.listTasks().get(0).rawText), "busy refusal preserves old rows");
        }
        restored.failOpen = true;
        try {
            ChronaDataBackup.restore(restored, prepared);
            throw new AssertionError("injected pre-install read failure must escape");
        } catch (android.database.sqlite.SQLiteException expected) {
            require(expected.getMessage().contains("injected"), "failure came from actual restore read");
        } finally { restored.failOpen = false; }
        try (TaskStore afterFailure = new TaskStore(restored)) {
            require("旧数据应由备份替换".equals(afterFailure.listTasks().get(0).rawText), "failed restore releases gate and preserves rows");
        }
        String result;
        restored.failCleanup = true;
        try { result = restoreWithWaitingReader(restored, prepared); }
        finally { prepared.cleanup(); }
        require(restored.injected, "post-commit cleanup scheduling failure was injected");
        require(result.contains("恢复") && result.contains("清理"), "restore reports maintenance failure without rollback");
        try (TaskStore store = new TaskStore(restored)) {
            require(store.listTasks().size() == 1, "restored row count");
            TaskRecord task = store.getTask(taskId);
            require(task != null && "备份往返原文".equals(task.rawText), "restored database survived maintenance failure");
        }
        File restoredOutput = new File(restored.getFilesDir(), "model-output/task-" + taskId + ".txt");
        require("完整解析结果".equals(new String(Files.readAllBytes(restoredOutput.toPath()), StandardCharsets.UTF_8)),
                "restored model output survived maintenance failure");
        require(!oldOutput.exists(), "old output was replaced");
        JSONObject recovered = ClassroomArchive.snapshot(restored).get("101-202").document;
        require(document.getJSONArray("segments").toString().equals(recovered.getJSONArray("segments").toString()),
                "large UTF-8 transcript survived export, prepare and restore");
        require(!new File(restored.getDatabasePath("chrona.db").getParentFile(), "chrona.db.previous").exists(),
                "successful restore retired the database rollback copy");
        require(!new WebDavSettingsStore(restored).automatic() && !new ZjuSettingsStore(restored).enabled(),
                "restored external sync remains disabled");
        // The reminder worker may still be finishing a local refresh; retain these isolated files for evidence.
        return "PASS: real Android SQLite / ZIP round trip, >4 MiB UTF-8 classroom, restored rows and outputs, "
                + "busy-handle refusal, failed-restore gate release, concurrent reader sees installed data, "
                + "post-commit scheduling fault, automatic sync disabled. Fixture: " + root;
    }

    private static String restoreWithWaitingReader(Sandbox context, ChronaDataBackup.Prepared prepared) throws Exception {
        AtomicReference<String> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch readerStarted = new CountDownLatch(1), readerDone = new CountDownLatch(1);
        context.restoreOpened = new CountDownLatch(1);
        context.resumeRestore = new CountDownLatch(1);
        Thread restore = new Thread(() -> {
            try { result.set(ChronaDataBackup.restore(context, prepared)); }
            catch (Throwable error) { failure.compareAndSet(null, error); }
        }, "fixture-restore");
        context.restoreThread = restore;
        Thread reader = new Thread(() -> {
            readerStarted.countDown();
            try (TaskStore store = new TaskStore(context)) {
                require("备份往返原文".equals(store.listTasks().get(0).rawText), "waiting reader sees only installed database");
            } catch (Throwable error) { failure.compareAndSet(null, error); }
            finally { readerDone.countDown(); }
        }, "fixture-waiting-reader");
        restore.start();
        try {
            require(context.restoreOpened.await(10, TimeUnit.SECONDS), "restore has exclusive access and can open its own database");
            reader.start();
            require(readerStarted.await(2, TimeUnit.SECONDS), "concurrent reader starts");
            require(!readerDone.await(150, TimeUnit.MILLISECONDS), "concurrent reader waits across replacement");
        } finally { context.resumeRestore.countDown(); }
        restore.join(30000); reader.join(10000);
        require(!restore.isAlive() && !reader.isAlive(), "restore and waiting reader finish without deadlock");
        if (failure.get() != null) throw new AssertionError("concurrent restore failed", failure.get());
        return result.get();
    }

    private static void verifySyncConstructionFailure(Sandbox context) throws Exception {
        File blocked = new File(context.getFilesDir(), "sync-blobs");
        Files.write(blocked.toPath(), "constructor failure fixture".getBytes(StandardCharsets.UTF_8));
        try {
            try (AndroidSyncData unexpected = new AndroidSyncData(context)) {
                throw new AssertionError("sync directory failure must reject construction");
            }
        } catch (IOException expected) {
            require(expected.getMessage().contains("同步附件目录"), "actual sync constructor directory failure");
        } finally { Files.delete(blocked.toPath()); }
        try (com.donglan.chrona.data.LiveDatabaseAccess.Restore access =
                com.donglan.chrona.data.LiveDatabaseAccess.beginRestore()) { }
        context.failOpen = true;
        try {
            try (AndroidSyncData unexpected = new AndroidSyncData(context)) {
                throw new AssertionError("sync database failure must reject construction");
            }
        } catch (android.database.sqlite.SQLiteException expected) {
            require(expected.getMessage().contains("injected"), "actual sync constructor database failure");
        } finally { context.failOpen = false; }
        try (com.donglan.chrona.data.LiveDatabaseAccess.Restore access =
                com.donglan.chrona.data.LiveDatabaseAccess.beginRestore()) { }
    }

    private static JSONObject document() throws Exception {
        JSONObject lesson = new JSONObject().put("course_id", "101").put("id", "202")
                .put("course", "隔离测试课程").put("title", "大字幕往返").put("status", "ready").put("starts_at", 123456L);
        JSONObject summary = new JSONObject().put("complete", true).put("lesson", lesson)
                .put("updated", System.currentTimeMillis()).put("overview", "课堂笔记")
                .put("parts", new JSONArray()).put("images", new JSONArray()).put("raw_images", 0);
        JSONArray segments = new JSONArray();
        String paragraph = "字幕内容保留。".repeat(2048);
        for (int i = 0; i < 128; i++)
            segments.put(new JSONObject().put("start", i).put("end", i + 1).put("text", paragraph));
        return new JSONObject().put("summary", summary).put("segments", segments);
    }

    private static void require(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static final class Sandbox extends ContextWrapper {
        final File root;
        final String preferencePrefix = "backup-device-" + UUID.randomUUID() + "-";
        volatile boolean failCleanup, injected, failOpen;
        volatile Thread restoreThread;
        volatile CountDownLatch restoreOpened, resumeRestore;
        Sandbox(Context base, File root) throws IOException {
            super(base); this.root = root;
            if (!root.mkdirs()) throw new IOException("Cannot create fixture directory");
        }
        private File directory(String name) {
            File file = new File(root, name);
            if (!file.isDirectory() && !file.mkdirs()) throw new IllegalStateException("Cannot create " + name);
            return file;
        }
        @Override public Context getApplicationContext() { return this; }
        @Override public File getFilesDir() { return directory("files"); }
        @Override public File getCacheDir() { return directory("cache"); }
        @Override public File getNoBackupFilesDir() { return directory("no-backup"); }
        @Override public File getDatabasePath(String name) {
            if ("chrona.db".equals(name)) beforeOpen();
            File requested = new File(name);
            if (!requested.isAbsolute()) return new File(directory("databases"), name);
            try {
                if (!requested.getCanonicalPath().startsWith(root.getCanonicalPath() + File.separator))
                    throw new IllegalStateException("Database escaped fixture directory");
            } catch (IOException error) { throw new IllegalStateException(error); }
            return requested;
        }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name), factory);
        }
        @Override public SQLiteDatabase openOrCreateDatabase(String name, int mode, SQLiteDatabase.CursorFactory factory,
                DatabaseErrorHandler handler) {
            return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getPath(), factory, handler);
        }
        private void beforeOpen() {
            if (failOpen) throw new android.database.sqlite.SQLiteException("injected restore read failure");
            if (Thread.currentThread() == restoreThread && restoreOpened.getCount() > 0) {
                restoreOpened.countDown();
                try {
                    if (!resumeRestore.await(10, TimeUnit.SECONDS)) throw new AssertionError("restore test latch timed out");
                } catch (InterruptedException error) { throw new AssertionError(error); }
            }
        }
        @Override public SharedPreferences getSharedPreferences(String name, int mode) {
            return super.getSharedPreferences(preferencePrefix + name, mode);
        }
        @Override public int checkSelfPermission(String permission) { return PackageManager.PERMISSION_DENIED; }
        @Override public Object getSystemService(String name) {
            if (JOB_SCHEDULER_SERVICE.equals(name)) {
                if (failCleanup) for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
                    if (ClassroomCleanupService.class.getName().equals(frame.getClassName()) && "schedule".equals(frame.getMethodName())) {
                        injected = true;
                        throw new IllegalStateException("injected cleanup scheduler failure");
                    }
                }
            }
            return super.getSystemService(name);
        }
    }
}
