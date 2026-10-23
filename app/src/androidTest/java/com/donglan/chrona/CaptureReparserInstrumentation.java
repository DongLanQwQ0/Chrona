package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.LinearLayout;
import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskFileAttachment;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.processing.CaptureReparser;
import com.donglan.chrona.processing.StreamingOutputStore;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real SQLite and CalendarProvider, disposable v33 emulator only; never schedules AI. */
public final class CaptureReparserInstrumentation extends Instrumentation {
    private static final String RAW = "reparse-check 原始内容 逐字保留";
    private Context context;
    private CalendarStore calendar;
    private Bundle args;
    private final List<Long> tasks = new ArrayList<>(), events = new ArrayList<>();
    private final List<File> files = new ArrayList<>();
    private final List<android.net.Uri> downloads = new ArrayList<>();
    private final List<String> archives = new ArrayList<>();
    private int assertions, schedules;
    private Method reparse;
    private Activity activity;

    @Override public void onCreate(Bundle value) { args = value == null ? new Bundle() : value; super.onCreate(value); start(); }
    @Override public Activity newActivity(ClassLoader loader, String name, Intent intent)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        Activity result = super.newActivity(loader, name, intent);
        if (TaskDetailActivity.class.getName().equals(name) && "true".equals(args.getString("render"))) {
            Configuration config = new Configuration();
            int width = Integer.parseInt(args.getString("widthDp", "400"));
            float font = Float.parseFloat(args.getString("font", "1.0"));
            var metrics = getTargetContext().getResources().getDisplayMetrics();
            config.densityDpi = Math.round(metrics.widthPixels * 160f / width);
            config.screenWidthDp = width;
            config.screenHeightDp = Math.round(metrics.heightPixels * 160f / config.densityDpi);
            config.smallestScreenWidthDp = width; config.fontScale = font;
            result.applyOverrideConfiguration(config);
        }
        return result;
    }
    @Override public void onStart() {
        Bundle report = new Bundle();
        try {
            context = getTargetContext(); FixtureIsolation.require(context);
            getUiAutomation().grantRuntimePermission(context.getPackageName(), Manifest.permission.READ_CALENDAR);
            getUiAutomation().grantRuntimePermission(context.getPackageName(), Manifest.permission.WRITE_CALENDAR);
            calendar = new CalendarStore(context);
            reparse = CaptureReparser.class.getDeclaredMethod("reparse", Context.class, long.class, long.class, Runnable.class);
            reparse.setAccessible(true);
            boolean render = "true".equals(args.getString("render"));
            if (render) render(); else check();
            report.putString("reparse_report", "PASS: " + assertions + (render
                    ? " assertions; actual TaskDetail touch opens linked-capture confirmation; cancel preserves SQLite/provider; compressed render saved; no AI requests"
                    : " assertions; actual SQLite/CalendarProvider; exclusive/duplicate/shared/archive/missing, source+attachment+audit preservation, guards, provider/SQL/scheduler failure retry; no AI requests"));
            finish(-1, report);
        } catch (Throwable failure) {
            report.putString("error", android.util.Log.getStackTraceString(failure)); finish(0, report);
        }
    }
    private void check() throws Exception {
        SharedPreferences prefs = context.getSharedPreferences("chrona_ai_settings", 0);
        Map<String, ?> old = prefs.getAll();
        try {
            configure();
            long exclusive = event(), shared = event(), archived = event(), unrelated = event(), missing = event();
            calendar.deleteEvent(missing);
            long task = fixture(RAW, exclusive, exclusive, shared, archived, missing);
            long other = fixture(RAW, shared);
            String archiveKey = "reparse-check-" + task; archives.add(archiveKey);
            String audit;
            String image = "00000000-0000-0000-0000-000000000036.jpg";
            File imageFile = new File(context.getFilesDir(), "attachments/" + image);
            require(!imageFile.exists(), "fixture image is not an existing attachment");
            imageFile.getParentFile().mkdirs(); files.add(imageFile);
            try (FileOutputStream out = new FileOutputStream(imageFile)) { out.write(new byte[]{3, 6, 9, 12}); }
            ContentValues download = new ContentValues();
            download.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "chrona-reparse-" + System.nanoTime() + ".txt");
            download.put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain");
            download.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/ChronaValidation");
            android.net.Uri fileUri = context.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, download);
            require(fileUri != null, "isolated ordinary file fixture created in real MediaStore"); downloads.add(fileUri);
            byte[] fileBytes = "synthetic original file bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            try (var out = context.getContentResolver().openOutputStream(fileUri)) { out.write(fileBytes); }
            try (TaskStore store = new TaskStore(context)) {
                store.getWritableDatabase().execSQL("INSERT INTO candidate_merges(source_candidate,record,calendar_event_id) VALUES(?,?,?)",
                        new Object[]{archiveKey, "{\"synthetic\":true}", archived});
                store.addImageAttachment(task, image);
                store.addFileAttachment(task, new TaskFileAttachment(0, task, fileUri.toString(), "reparse.txt", "text/plain", fileBytes.length));
                audit = audit(store, task);
            }
            try (var out = new StreamingOutputStore(context).begin(task)) { out.append("old synthetic model preview"); }
            invoke(context, task, revision(), () -> {
                schedules++;
                try (TaskStore store = new TaskStore(context)) {
                    require(TaskRecord.QUEUED.equals(store.getTask(task).status), "scheduler observes committed QUEUED");
                    require(store.getCandidates(task).isEmpty(), "scheduler observes no old local candidates");
                }
            });
            require(calendar.getEvent(exclusive) == null, "exclusive duplicated link removed from actual provider");
            require(calendar.getEvent(shared) != null, "another capture preserves shared provider event");
            require(calendar.getEvent(archived) != null, "archived merge reference preserves provider event");
            require(calendar.getEvent(unrelated) != null, "unrelated provider event untouched");
            try (TaskStore store = new TaskStore(context)) {
                require(store.getCandidates(other).size() == 1 && store.getCandidates(other).get(0).calendarEventId == shared,
                        "other capture and link untouched");
                require(RAW.equals(store.getTask(task).rawText), "exact original source retained");
                require(store.getImagePaths(task).equals(List.of(image)), "image attachment references retained");
                require(store.getFileAttachments(task).size() == 1 && store.getFileAttachments(task).get(0).sizeBytes == fileBytes.length
                        && store.getFileAttachments(task).get(0).storedName.equals(fileUri.toString())
                        && store.getFileAttachments(task).get(0).displayName.equals("reparse.txt")
                        && store.getFileAttachments(task).get(0).mimeType.equals("text/plain"), "ordinary file attachment metadata retained");
                require(audit.equals(audit(store, task)), "historical successful parse and exact evidence JSON retained");
            }
            require(java.util.Arrays.equals(java.nio.file.Files.readAllBytes(imageFile.toPath()), new byte[]{3, 6, 9, 12}), "source image bytes untouched");
            try (var input = context.getContentResolver().openInputStream(fileUri)) {
                require(java.util.Arrays.equals(input.readAllBytes(), fileBytes), "real ordinary file bytes untouched");
            }
            require(new StreamingOutputStore(context).length(task) == 0, "old transient model preview cleared");
            guards();
            providerFailureAndRetry();
            sqlFailureAndRetry();
            schedulerFailureAndRetry();
            attachmentOnly();
            require(schedules == 9, "only nine successful scheduling callbacks; all negative guards schedule nothing");
        } finally { restore(prefs, old); cleanup(); }
    }
    private void guards() throws Exception {
        long event = event(), task = fixture(RAW, event);
        long current = revision();
        blocked(context, task, current - 1, ConcurrentModificationException.class, "stale revision");
        try (TaskStore store = new TaskStore(context)) { store.updateStatus(task, TaskRecord.QUEUED, null); }
        blocked(context, task, -1, ConcurrentModificationException.class, "queued busy");
        try (TaskStore store = new TaskStore(context)) { store.updateStatus(task, TaskRecord.PROCESSING, null); }
        blocked(context, task, -1, ConcurrentModificationException.class, "processing busy");
        try (TaskStore store = new TaskStore(context)) { store.updateStatus(task, TaskRecord.READY, null); }
        new AiSettingsStore(context).clear();
        blocked(context, task, -1, IllegalStateException.class, "missing AI settings"); configure();
        var prefs = context.getSharedPreferences("chrona_ai_settings", 0); String encrypted = prefs.getString("encrypted_api_key", null);
        prefs.edit().putString("encrypted_api_key", "invalid").commit();
        blocked(context, task, -1, java.security.GeneralSecurityException.class, "invalid encrypted AI settings");
        prefs.edit().putString("encrypted_api_key", encrypted).commit();
        for (String denied : List.of(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) {
            Context gate = new ContextWrapper(context) {
                @Override public Context getApplicationContext() { return this; }
                @Override public int checkPermission(String permission, int pid, int uid) {
                    return denied.equals(permission) ? PackageManager.PERMISSION_DENIED : super.checkPermission(permission, pid, uid);
                }
            };
            blocked(gate, task, -1, SecurityException.class, denied + " gate");
        }
        long empty = fixture(RAW, event());
        try (TaskStore store = new TaskStore(context)) { store.getWritableDatabase().execSQL("UPDATE tasks SET raw_text='  ' WHERE id=?", new Object[]{empty}); }
        blocked(context, empty, -1, IllegalStateException.class, "empty text and attachments");
        long managed = fixture(RAW, event());
        try (TaskStore store = new TaskStore(context)) {
            store.getWritableDatabase().execSQL("INSERT INTO zju_homework_captures(capture_id,account) VALUES(?,?)", new Object[]{managed, "synthetic"});
        }
        blocked(context, managed, -1, IllegalStateException.class, "managed homework");
        int before = schedules;
        try { invoke(context, Long.MAX_VALUE, -1, () -> schedules++); throw new AssertionError("missing capture accepted"); }
        catch (IllegalStateException expected) { assertions++; }
        require(schedules == before, "missing capture not queued");
    }
    private void blocked(Context target, long task, long rev, Class<? extends Throwable> type, String label) throws Exception {
        String before = snapshot(task); int scheduled = schedules;
        List<Long> linked = new ArrayList<>();
        try (TaskStore store = new TaskStore(context)) { for (EventCandidate row : store.getCandidates(task)) if (row.calendarEventId != null) linked.add(row.calendarEventId); }
        try { invoke(target, task, rev, () -> schedules++); throw new AssertionError(label + " accepted"); }
        catch (Throwable failure) { require(type.isInstance(failure), label + " returns expected error, got " + failure); }
        require(before.equals(snapshot(task)), label + " preserves status/candidates/audit/revision");
        for (long event : linked) require(calendar.getEvent(event) != null, label + " prevents provider deletion");
        require(schedules == scheduled, label + " prevents enqueue");
    }
    private void sqlFailureAndRetry() throws Exception {
        long event = event(), task = fixture(RAW, event); String before = snapshot(task); int scheduled = schedules;
        try (TaskStore store = new TaskStore(context)) {
            store.getWritableDatabase().execSQL("CREATE TRIGGER reparse_check_fail BEFORE DELETE ON event_candidates WHEN OLD.task_id=" + task
                    + " BEGIN SELECT RAISE(ABORT,'synthetic local failure after provider delete'); END");
        }
        try {
            try { invoke(context, task, -1, () -> schedules++); throw new AssertionError("local deletion failure accepted"); }
            catch (IllegalStateException expected) { assertions++; }
            require(before.equals(snapshot(task)), "local SQL failure rolls back candidates/status/revision and preserves audit");
            require(calendar.getEvent(event) == null, "external provider deletion cannot roll back with SQLite");
            require(scheduled == schedules, "local cleanup failure never queued");
        } finally { try (TaskStore store = new TaskStore(context)) { store.getWritableDatabase().execSQL("DROP TRIGGER reparse_check_fail"); } }
        invoke(context, task, -1, () -> schedules++);
        require(TaskRecord.QUEUED.equals(status(task)), "retry tolerates externally missing event after partial failure");
    }
    private void providerFailureAndRetry() throws Exception {
        for (String mode : List.of("throw", "refuse", "partial")) {
            long first = event(), second = event();
            long task = "partial".equals(mode) ? fixture(RAW, first, second) : fixture(RAW, first);
            String before = snapshot(task); int scheduled = schedules;
            ContentProvider transport = new ContentProvider() {
                int calls;
                @Override public boolean onCreate() { return true; }
                @Override public android.database.Cursor query(android.net.Uri uri, String[] projection, String selection, String[] selectionArgs, String order) {
                    return context.getContentResolver().query(uri, projection, selection, selectionArgs, order);
                }
                @Override public String getType(android.net.Uri uri) { return context.getContentResolver().getType(uri); }
                @Override public android.net.Uri insert(android.net.Uri uri, ContentValues values) { throw new AssertionError("reparse must not insert provider events"); }
                @Override public int update(android.net.Uri uri, ContentValues values, String selection, String[] selectionArgs) { throw new AssertionError("reparse must not update provider events"); }
                @Override public int delete(android.net.Uri uri, String selection, String[] selectionArgs) {
                    calls++;
                    if ("partial".equals(mode) && calls == 1) return context.getContentResolver().delete(uri, selection, selectionArgs);
                    if ("refuse".equals(mode)) return 0;
                    throw new IllegalStateException("synthetic CalendarProvider delete refusal");
                }
            };
            transport.attachInfo(context, new android.content.pm.ProviderInfo());
            ContentResolver resolver = ContentResolver.wrap(transport);
            Context target = new ContextWrapper(context) {
                @Override public Context getApplicationContext() { return this; }
                @Override public ContentResolver getContentResolver() { return resolver; }
            };
            try { invoke(target, task, -1, () -> schedules++); throw new AssertionError(mode + " provider failure accepted"); }
            catch (IllegalStateException expected) { require(expected.getMessage().contains("旧日程清理未完成"), mode + " provider failure actionable"); }
            require(before.equals(snapshot(task)), mode + " provider failure retains local results/status/audit/revision");
            require(schedules == scheduled, mode + " provider failure prevents enqueue");
            if ("partial".equals(mode)) require((calendar.getEvent(first) == null) != (calendar.getEvent(second) == null),
                    "partial provider failure leaves exactly one irreversible external deletion");
            else require(calendar.getEvent(first) != null, mode + " provider refusal leaves real old event");
            invoke(context, task, -1, () -> schedules++);
            require(TaskRecord.QUEUED.equals(status(task)) && calendar.getEvent(first) == null, mode + " provider failure retry succeeds");
        }
    }
    private void schedulerFailureAndRetry() throws Exception {
        long event = event(), task = fixture(RAW, event);
        try { invoke(context, task, -1, () -> { throw new IllegalStateException("synthetic scheduler refusal"); }); throw new AssertionError("scheduler failure accepted"); }
        catch (IllegalStateException failure) { require(failure.getMessage().contains("解析未能排队"), "scheduler failure actionable"); }
        require(calendar.getEvent(event) == null, "scheduler failure does not recreate deleted provider event");
        require(TaskRecord.FAILED.equals(status(task)), "scheduler failure becomes FAILED instead of stuck QUEUED");
        try (TaskStore store = new TaskStore(context)) {
            require(store.getCandidates(task).isEmpty(), "scheduler failure retains cleared candidates");
            require(store.getTask(task).rawText.equals(RAW) && store.getParseAudits(task, null, null, null, 0, 20).length() == 1,
                    "scheduler failure retains source and old parse evidence");
        }
        invoke(context, task, -1, () -> schedules++);
        require(TaskRecord.QUEUED.equals(status(task)), "FAILED scheduling can be retried");
    }
    private void attachmentOnly() throws Exception {
        for (boolean image : List.of(false, true)) {
            long task = fixture(RAW);
            try (TaskStore store = new TaskStore(context)) {
                if (image) store.addImageAttachment(task, "00000000-0000-0000-0000-000000000036.jpg");
                else store.addFileAttachment(task, new TaskFileAttachment(0, task, "content://synthetic/only.txt", "only.txt", "text/plain", 36));
                store.getWritableDatabase().execSQL("UPDATE tasks SET raw_text='' WHERE id=?", new Object[]{task});
            }
            invoke(context, task, -1, () -> schedules++);
            require(TaskRecord.QUEUED.equals(status(task)), (image ? "image" : "ordinary file") + "-only source accepted");
        }
        long unlinked = fixture(RAW);
        Context denied = new ContextWrapper(context) {
            @Override public Context getApplicationContext() { return this; }
            @Override public int checkPermission(String p, int pid, int uid) { return p.endsWith("CALENDAR") ? PackageManager.PERMISSION_DENIED : super.checkPermission(p, pid, uid); }
        };
        invoke(denied, unlinked, -1, () -> schedules++);
        require(TaskRecord.QUEUED.equals(status(unlinked)), "unlinked capture preserves calendar-permission-free reparse");
    }
    private long event() {
        long id = calendar.insertEvent(new CalendarStore.EventInput("reparse synthetic calendar event", 1_900_000_000_000L,
                1_900_003_600_000L, "Asia/Shanghai", false, "synthetic only", "", List.of()));
        events.add(id); return id;
    }
    private long fixture(String raw, Long... linked) throws Exception {
        try (TaskStore store = new TaskStore(context)) {
            long id = store.insertTask(raw, null, "reparse_check", System.currentTimeMillis()); tasks.add(id);
            List<EventCandidate> rows = new ArrayList<>(); JSONArray entries = new JSONArray();
            for (Long link : linked) {
                rows.add(new EventCandidate(0, id, "synthetic saved event", 1_900_000_000_000L, 1_900_003_600_000L,
                        "Asia/Shanghai", "", "original description", 15, false, link));
                entries.put(new JSONObject().put("position", entries.length()).put("plan_group", JSONObject.NULL)
                        .put("model", new JSONObject().put("basis", "原文时间依据"))
                        .put("resolved", new JSONObject().put("source", "synthetic fixture")));
            }
            String run = store.beginParseRun(id, System.currentTimeMillis(), new JSONObject().put("source", "synthetic fixture"));
            store.replaceParsedCandidates(id, rows, rows, run, new JSONObject().put("entries", entries).toString());
            store.updateStatus(id, TaskRecord.READY, null); return id;
        }
    }
    private String audit(TaskStore store, long task) throws Exception { return store.getParseAudits(task, null, null, null, 0, 20).toString(); }
    private String snapshot(long task) throws Exception {
        try (TaskStore store = new TaskStore(context)) {
            StringBuilder result = new StringBuilder(status(task)).append('/').append(store.dataRevision()).append('/').append(audit(store, task));
            for (EventCandidate row : store.getCandidates(task)) result.append('/').append(row.id).append(':').append(row.calendarEventId);
            return result.toString();
        }
    }
    private String status(long task) { try (TaskStore store = new TaskStore(context)) { return store.getTask(task).status; } }
    private long revision() { try (TaskStore store = new TaskStore(context)) { return store.dataRevision(); } }
    private void configure() throws Exception { new AiSettingsStore(context).save("https://synthetic.invalid/v1", "synthetic-model", "synthetic-no-network"); }
    private void invoke(Context target, long task, long revision, Runnable schedule) throws Exception {
        try { reparse.invoke(null, target, task, revision, schedule); }
        catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof Exception e) throw e;
            if (failure.getCause() instanceof Error e) throw e;
            throw failure;
        }
    }
    private void cleanup() {
        if (activity != null) runOnMainSync(() -> activity.finish());
        try (TaskStore store = new TaskStore(context)) {
            for (String key : archives) store.getWritableDatabase().delete("candidate_merges", "source_candidate=?", new String[]{key});
            for (long id : tasks) {
                store.getWritableDatabase().delete("zju_homework_captures", "capture_id=?", new String[]{Long.toString(id)});
                if (store.getTask(id) != null) store.deleteTask(id);
                new StreamingOutputStore(context).delete(id);
            }
        }
        for (long id : events) calendar.deleteEvent(id);
        for (File file : files) file.delete();
        for (android.net.Uri uri : downloads) context.getContentResolver().delete(uri, null, null);
    }
    private void require(boolean value, String label) { if (!value) throw new AssertionError(label); assertions++; }
    private static void restore(SharedPreferences prefs, Map<String, ?> old) {
        var edit = prefs.edit().clear();
        for (var entry : old.entrySet()) {
            Object value = entry.getValue(); String key = entry.getKey();
            if (value instanceof String s) edit.putString(key, s);
            else if (value instanceof Boolean b) edit.putBoolean(key, b);
            else if (value instanceof Integer n) edit.putInt(key, n);
            else if (value instanceof Long n) edit.putLong(key, n);
            else if (value instanceof Float n) edit.putFloat(key, n);
        }
        if (!edit.commit()) throw new IllegalStateException("fixture settings restoration failed");
    }
    private void render() throws Exception {
        SharedPreferences appearance = context.getSharedPreferences("appearance", 0); Map<String, ?> old = appearance.getAll();
        try {
            appearance.edit().putString("mode", args.getString("mode", "light")).commit();
            long event = event(), task = fixture(RAW, event); String before = snapshot(task);
            activity = startActivitySync(new Intent(context, TaskDetailActivity.class).putExtra("task_id", task).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            waitForIdleSync(); SystemClock.sleep(500);
            TextView reparseButton = find(activity.getWindow().getDecorView(), "重新解析");
            require(reparseButton != null, "reparse remains available with linked system event");
            runOnMainSync(() -> reparseButton.requestRectangleOnScreen(new Rect(0, 0, reparseButton.getWidth(), reparseButton.getHeight()), true));
            waitForIdleSync(); SystemClock.sleep(400); tap(reparseButton); SystemClock.sleep(500);
            TextView title = dialogText("重新解析这条捕获？"); require(title != null, "actual touch opens reparse confirmation");
            require(dialogText("这将替换已有日程") != null, "confirmation body exactly matches the approved short copy without punctuation");
            getUiAutomation().waitForIdle(300, 5000); waitForIdleSync(); SystemClock.sleep(1500);
            TextView cancel = dialogText("取消"); require(cancel != null, "cancel action visible");
            require(cancel.getParent() instanceof LinearLayout, "shared action row owns paired controls");
            LinearLayout row = (LinearLayout) cancel.getParent();
            require(row.getChildCount() == 2 && row.getChildAt(1) instanceof TextView, "paired submit action exists");
            TextView submit = (TextView) row.getChildAt(1);
            require("重新解析".contentEquals(submit.getText()), "submit retains complete action label");
            boolean narrowLarge = "320".equals(args.getString("widthDp")) && "1.5".equals(args.getString("font"));
            require(row.getOrientation() == (narrowLarge ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL),
                    "narrow large-text row stacks; ordinary row preserves horizontal geometry");
            textFits(cancel); textFits(submit);
            capture(); tap(cancel); SystemClock.sleep(500);
            require(dialogText("重新解析这条捕获？") == null, "cancel dismisses confirmation");
            require(before.equals(snapshot(task)), "cancel leaves local capture, candidates and audit unchanged");
            require(calendar.getEvent(event) != null, "cancel never deletes linked provider event");
        } finally { cleanup(); restore(appearance, old); }
    }
    private void textFits(TextView view) {
        android.text.Layout layout = view.getLayout();
        require(layout != null && layout.getLineCount() > 0, "action has actual rendered text layout");
        require(layout.getLineEnd(layout.getLineCount() - 1) == view.getText().length(), "actual layout covers complete action label");
        for (int line = 0; line < layout.getLineCount(); line++) {
            require(layout.getEllipsisCount(line) == 0, "action label has no ellipsis");
            require(layout.getLineWidth(line) <= view.getWidth() - view.getCompoundPaddingLeft() - view.getCompoundPaddingRight() + 1,
                    "action line fits available text width");
        }
        int[] xy = new int[2]; view.getLocationOnScreen(xy);
        var display = context.getResources().getDisplayMetrics();
        require(xy[0] >= 0 && xy[1] >= 0 && xy[0] + view.getWidth() <= display.widthPixels
                && xy[1] + view.getHeight() <= display.heightPixels, "complete action bounds stay inside physical viewport");
        require(view.getHeight() >= view.getTotalPaddingTop() + view.getTotalPaddingBottom() + layout.getHeight(),
                "action height contains full text and vertical padding");
    }
    private TextView dialogText(String label) {
        for (View root : android.view.inspector.WindowInspector.getGlobalWindowViews()) { TextView result = find(root, label); if (result != null) return result; }
        return null;
    }
    private static TextView find(View root, String label) {
        if (root instanceof TextView text && label.contentEquals(text.getText())) return text;
        if (root instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) { TextView result = find(group.getChildAt(i), label); if (result != null) return result; }
        return null;
    }
    private void tap(View view) {
        int[] xy = new int[2]; view.getLocationOnScreen(xy); long now = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(now, now, MotionEvent.ACTION_DOWN, xy[0] + view.getWidth() / 2f, xy[1] + view.getHeight() / 2f, 0);
        MotionEvent up = MotionEvent.obtain(now, now + 80, MotionEvent.ACTION_UP, xy[0] + view.getWidth() / 2f, xy[1] + view.getHeight() / 2f, 0);
        try { sendPointerSync(down); sendPointerSync(up); } finally { down.recycle(); up.recycle(); }
    }
    private void capture() throws Exception {
        Bitmap original = getUiAutomation().takeScreenshot();
        String name = "reparse-" + args.getString("mode", "light") + "-" + args.getString("widthDp", "400") + "-" + args.getString("font", "1.0");
        File directory = context.getExternalFilesDir(null);
        try (var out = new FileOutputStream(new File(directory, name + ".png"))) { original.compress(Bitmap.CompressFormat.PNG, 100, out); }
        Bitmap compressed = Bitmap.createScaledBitmap(original, 540, Math.round(original.getHeight() * 540f / original.getWidth()), true);
        try (var out = new FileOutputStream(new File(directory, name + "-preview.jpg"))) { compressed.compress(Bitmap.CompressFormat.JPEG, 78, out); }
        compressed.recycle(); original.recycle();
    }
}
