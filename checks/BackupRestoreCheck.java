package com.donglan.chrona;

import android.content.Context;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Real production restore flow, real file swaps and ZIPs; Android/SQLite boundaries are doubles. */
public final class BackupRestoreCheck {
    static int checks, failures;
    static File base;
    interface Checked { void run() throws Exception; }
    static void scenario(String label, Checked action) {
        try { action.run(); System.out.println("PASS " + label); }
        catch (Throwable error) { failures++; System.out.println("FAIL " + label + ": " + error); }
    }
    static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    static Object call(Method method, Object... args) throws Exception {
        try { return method.invoke(null, args); }
        catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) throw (Exception)e.getCause();
            throw (Error)e.getCause();
        }
    }
    static Method find(String name, Class<?>... types) throws Exception {
        Method method = ChronaDataBackup.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method;
    }
    static String read(File file, long max) throws Exception {
        return (String)call(find("readText", File.class, long.class), file, max);
    }
    static void write(ZipOutputStream zip, String entry, JSONObject json) throws Exception {
        // Generated wrapper executes the actual exporter call site and its limit.
        call(find("exportJsonEntry", ZipOutputStream.class, String.class, JSONObject.class), zip, entry, json);
    }
    static final class RestoreFixture {
        final Context context;
        final ChronaDataBackup.Prepared prepared;
        final File database, output, classroom;
        RestoreFixture() throws Exception {
            File run = Files.createTempDirectory(base.toPath(), "restore-").toFile();
            context = new Context(new File(run, "app"));
            Fixture.failure = "";
            Fixture.reached.clear();
            database = context.getDatabasePath("chrona.db");
            output = new File(context.getFilesDir(), "model-output/task-1.txt");
            classroom = new File(ClassroomStore.root(context), "shared/1-2/document.json");
            Fixture.put(database, "{\"tasks\":1,\"candidates\":1,\"marker\":\"old-db\"}");
            Fixture.put(output, "old-output");
            Fixture.put(classroom, "old-classroom");
            context.values.put("config", "old-config");
            context.reminders.values.put("setting", true);
            context.reminders.values.put("runtime-slot", "old-runtime");
            File incoming = new File(run, "prepared");
            Fixture.put(new File(incoming, "chrona.db"), "{\"tasks\":2,\"candidates\":2,\"marker\":\"new-db\"}");
            Fixture.put(new File(incoming, "outputs/1.txt"), "new-output");
            JSONObject document = new JSONObject().put("summary", new JSONObject()
                    .put("lesson", new JSONObject().put("key", "1-2")).put("content", "new-classroom"));
            Fixture.put(new File(incoming, "classrooms/1-2/document.json"), document.toString());
            JSONObject manifest = new JSONObject().put("tasks", 2).put("candidates", 2)
                    .put("outputs", new JSONArray().put(1)).put("classrooms", new JSONArray().put("1-2"));
            prepared = new ChronaDataBackup.Prepared(incoming, manifest, "new-config");
        }
        void verify(String expected) throws Exception {
            check(database.isFile(), expected + " database must exist (was deleted)");
            check(Fixture.get(database).contains(expected + "-db"), expected + " database bytes");
            check(output.isFile() && Fixture.get(output).equals(expected + "-output"), expected + " output bytes");
            check(classroom.isFile() && Fixture.get(classroom).contains(expected + "-classroom"), expected + " classroom bytes");
            check(context.values.get("config").equals(expected + "-config"), expected + " configuration");
        }
    }
    static void restore(String point, boolean beforeCommit) throws Exception {
        RestoreFixture fixture = new RestoreFixture();
        Fixture.failure = point;
        Exception thrown = null;
        String result = null;
        try { result = ChronaDataBackup.run(fixture.context, fixture.prepared); }
        catch (Exception error) { thrown = error; }
        if (!point.isEmpty()) check(Fixture.reached.contains(point), "injection point actually reached: " + point);
        if (beforeCommit) check(thrown != null, "precommit failure is reported");
        fixture.verify(beforeCommit ? "old" : "new");
        if (!beforeCommit) {
            check(thrown == null, "committed restore must not be reported as failed: " + thrown);
            check(!fixture.context.getDatabasePath("chrona.db.previous").exists(), "committed database rollback point cleaned");
            check(!new File(fixture.context.getFilesDir(), "model-output.previous").exists(), "committed outputs rollback point cleaned");
            check(!new File(fixture.context.getNoBackupFilesDir(), "classroom.previous").exists(), "committed classroom rollback point cleaned");
            if (point.isEmpty()) {
                check(fixture.context.reminders.values.containsKey("setting"), "restored reminder setting retained");
                check(!fixture.context.reminders.values.containsKey("runtime-slot"), "old reminder runtime state cleared");
            } else {
                check(result.contains("已恢复") && result.contains("\n"), "committed restore reports success with a maintenance warning");
                check(Fixture.reached.contains("classroom-schedule"), "reminder failure does not prevent classroom scheduling");
            }
        }
    }
    static void missingRollbackCopy() throws Exception {
        RestoreFixture f = new RestoreFixture();
        Fixture.failure = "missing-rollback";
        Exception thrown = null;
        try { ChronaDataBackup.run(f.context, f.prepared); }
        catch (Exception e) { thrown = e; }
        check(thrown != null && Fixture.reached.contains("missing-rollback"), "missing-copy failure injected");
        check(f.database.isFile() && Fixture.get(f.database).contains("new-db"), "missing rollback copy must not delete the surviving database");
        check(f.output.isFile() && Fixture.get(f.output).equals("new-output"), "new outputs remain with surviving database");
        check(new File(f.context.getFilesDir(), "model-output.previous/task-1.txt").isFile(), "old outputs retained for recovery");
    }
    static void largeManifestCaller() throws Exception {
        String text = sized(5L * 1024 * 1024, true).toString();
        File file = File.createTempFile("manifest-caller-", ".json", base);
        Fixture.put(file, text);
        try {
            JSONObject value = (JSONObject)call(find("readManifest", File.class), file);
            check(value.toString().equals(text), "actual manifest reader call accepts >4MiB UTF-8");
        } finally { Files.deleteIfExists(file.toPath()); }
    }
    static void largeClassroomRestore() throws Exception {
        RestoreFixture f = new RestoreFixture();
        File source = new File(f.prepared.directory, "classrooms/1-2/document.json");
        JSONObject document = new JSONObject(Fixture.get(source));
        document.getJSONObject("summary").put("transcript", "课".repeat(2 * 1024 * 1024));
        Fixture.put(source, document.toString());
        check(source.length() > 4L * 1024 * 1024, "classroom fixture actually exceeds old reader limit");
        ChronaDataBackup.run(f.context, f.prepared);
        f.verify("new");
        check(Fixture.get(f.classroom).equals(document.toString()), "full restore keeps large UTF-8 classroom document");
    }
    static JSONObject sized(long bytes, boolean unicode) throws Exception {
        int framing = new JSONObject().put("payload", "").toString().getBytes(StandardCharsets.UTF_8).length;
        int content = Math.toIntExact(bytes) - framing;
        String value = unicode ? "课".repeat(content / 3) + "a".repeat(content % 3) : "a".repeat(content);
        JSONObject result = new JSONObject().put("payload", value);
        check(result.toString().getBytes(StandardCharsets.UTF_8).length == bytes, "fixture has exact UTF-8 byte size");
        return result;
    }
    static void readAllowed(long max, long bytes, boolean unicode) throws Exception {
        JSONObject object = sized(bytes, unicode);
        String text = object.toString();
        File file = File.createTempFile("json-read-", ".json", base);
        Fixture.put(file, text);
        try { check(read(file, max).equals(text), "allowed JSON bytes round trip"); }
        finally { Files.deleteIfExists(file.toPath()); }
    }
    static void readRejected(long max) throws Exception {
        File file = File.createTempFile("json-read-limit-", ".json", base);
        Fixture.put(file, sized(max + 1, true).toString());
        boolean rejected = false;
        try { read(file, max); }
        catch (IOException expected) { rejected = true; }
        finally { Files.deleteIfExists(file.toPath()); }
        check(rejected, "JSON reader rejects max + 1 bytes");
    }
    static void zipAllowed(String entry, long max, long bytes, boolean unicode) throws Exception {
        String text = sized(bytes, unicode).toString();
        File file = File.createTempFile("json-write-", ".zip", base);
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
                write(zip, entry, new JSONObject(text));
            }
            try (ZipFile zip = new ZipFile(file)) {
                ZipEntry actual = zip.getEntry(entry);
                check(actual != null && actual.getSize() == bytes, "ZIP entry uses exact UTF-8 bytes");
                try (InputStream in = zip.getInputStream(actual)) {
                    check(new String(in.readAllBytes(), StandardCharsets.UTF_8).equals(text), "ZIP JSON bytes round trip");
                }
            }
        } finally { Files.deleteIfExists(file.toPath()); }
    }
    static void zipRejected(String entry, long max) throws Exception {
        File file = File.createTempFile("json-write-limit-", ".zip", base);
        boolean rejected = false;
        try {
            try (ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(file))) {
                try { write(zip, entry, sized(max + 1, true)); }
                catch (IOException expected) { rejected = true; }
            }
            check(rejected, "JSON exporter must reject max + 1 UTF-8 bytes");
            try (ZipFile zip = new ZipFile(file)) {
                check(zip.getEntry(entry) == null, "oversized JSON must be rejected before opening its ZIP entry");
            }
        } finally { Files.deleteIfExists(file.toPath()); }
    }
    public static void main(String[] args) throws Exception {
        base = new File(args[0]);
        scenario("successful restore keeps new database/output/classroom", () -> restore("", false));
        scenario("precommit configuration failure restores old database/output/classroom", () -> restore("config", true));
        scenario("precommit classroom-write failure restores old database/output/classroom", () -> restore("classroom-write", true));
        scenario("missing rollback copy preserves surviving database", BackupRestoreCheck::missingRollbackCopy);
        for (String point : List.of("reminder-state", "reminder-refresh", "classroom-schedule"))
            scenario("postcommit " + point + " failure retains committed restore", () -> restore(point, false));
        scenario("actual manifest reader caller accepts >4MiB UTF-8", BackupRestoreCheck::largeManifestCaller);
        scenario("actual classroom validation and restore accept >4MiB UTF-8", BackupRestoreCheck::largeClassroomRestore);
        for (String entry : List.of("manifest.json", "classrooms/1-2/document.json")) {
            long max = entry.equals("manifest.json") ? 40L * 1024 * 1024 : 16L * 1024 * 1024;
            scenario(entry + " reads legal >4MiB ASCII", () -> readAllowed(max, 5L * 1024 * 1024, false));
            scenario(entry + " reads exact limit UTF-8", () -> readAllowed(max, max, true));
            scenario(entry + " rejects read limit +1 UTF-8", () -> readRejected(max));
            scenario(entry + " exports legal >4MiB ASCII", () -> zipAllowed(entry, max, 5L * 1024 * 1024, false));
            scenario(entry + " exports exact limit UTF-8", () -> zipAllowed(entry, max, max, true));
            scenario(entry + " rejects export limit +1 UTF-8", () -> zipRejected(entry, max));
        }
        System.out.println("Backup restore: " + checks + " assertions; " + failures + " failed scenarios.");
        System.out.println("Scope: unchanged production restore/install/output/JSON methods; real isolated filesystem and ZIP; Android/SQLite boundary doubles, no device/account data.");
        if (failures != 0) throw new AssertionError(failures + " backup regression scenarios failed");
    }
}
