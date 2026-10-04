package com.donglan.chrona;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.AtomicFile;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.sync.*;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/** One process-wide worker; capture/save/network/recapture/save/apply is deliberately ordered. */
public final class AndroidSync {
    public static final Object LOCK = new Object();
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final AtomicBoolean RUNNING = new AtomicBoolean();
    private static volatile long lastForeground;
    private static volatile WebDavClient activeClient;
    private static final long FOREGROUND_INTERVAL = 5 * 60_000L;
    interface Completion { void done(String message); }
    interface Work { String run(Context context) throws Exception; }
    static boolean running() { return RUNNING.get(); }
    private static AtomicFile file(Context context) { return new AtomicFile(new File(context.getFilesDir(), "sync-state.json")); }
    static SyncState read(Context context) throws Exception {
        AtomicFile file = file(context);
        if (!file.getBaseFile().exists() && !new File(file.getBaseFile() + ".bak").exists()) return new SyncState(UUID.randomUUID().toString());
        try (InputStream in = file.openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] bytes = new byte[32768]; int n;
            while ((n = in.read(bytes)) != -1) { if (out.size() + n > 32 * 1024 * 1024) throw new IOException("本机同步记录过大"); out.write(bytes, 0, n); }
            return SyncState.fromJson(new JSONObject(out.toString("UTF-8")));
        }
    }
    static void save(Context context, SyncState state) throws Exception {
        byte[] bytes = state.toJson().toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > WebDavClient.HEAD_LIMIT) throw new IOException("同步记录超过 32 MiB，请减少记录或附件后重试");
        AtomicFile file = file(context); FileOutputStream out = null;
        try { out = file.startWrite(); out.write(bytes); file.finishWrite(out); }
        catch (Exception e) { if (out != null) file.failWrite(out); throw e; }
    }
    /** Backup restoration must hold LOCK during its replacement and this reset. */
    public static void resetAfterRestore(Context context) {
        synchronized (LOCK) {
            file(context).delete();
            try (com.donglan.chrona.data.TaskStore tasks = new com.donglan.chrona.data.TaskStore(context)) {
                android.database.sqlite.SQLiteDatabase db = tasks.getWritableDatabase();
                db.beginTransaction();
                try {
                    db.execSQL("DROP TABLE IF EXISTS sync_task_map");
                    db.execSQL("DROP TABLE IF EXISTS sync_candidate_map");
                    db.execSQL("DROP TABLE IF EXISTS sync_term_map");
                    db.execSQL("DROP TABLE IF EXISTS sync_meta");
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
            }
            new WebDavSettingsStore(context).automatic(false);
            new WebDavSettingsStore(context).status("备份恢复后自动同步已关闭；恢复的日程会作为新记录同步，可能与云端重复。请整理后手动同步。");
            SyncJobService.schedule(context);
        }
    }
    static void cancelNetwork() { WebDavClient client = activeClient; if (client != null) client.close(); }
    static boolean work(Context context, Completion completion, Work work) {
        Context app = context.getApplicationContext();
        if (!RUNNING.compareAndSet(false, true)) return false;
        WORKER.execute(() -> {
            String result;
            try { synchronized (LOCK) { result = work.run(app); } }
            catch (Exception e) {
                // Never include server bodies, URLs or credentials in persisted/UI diagnostics.
                if (e instanceof AndroidSyncData.AttachmentException) result = e.getMessage();
                else if (e instanceof SecurityException) result = "操作失败，请授予日历或附件访问权限后重试";
                else if (Arrays.asList("记录已变化，请重新选择", "记录正在处理", "日历旧事件未能全部移除，请重试",
                        "需要确认移除本机日历旧事件", "课表正在修改，请重新同步", "请输入账号和应用密码",
                        "记录正在修改，请稍后重新同步",
                        "同步记录超过 32 MiB，请减少记录或附件后重试",
                        "请输入有效的 HTTPS WebDAV 地址", "请重新输入应用密码").contains(e.getMessage())) result = e.getMessage();
                else result = "操作失败，请检查网络、应用密码和附件权限后重试（" + e.getClass().getSimpleName() + "）";
            } finally { RUNNING.set(false); }
            new WebDavSettingsStore(app).status(result);
            if (completion != null) { String text = result; new Handler(Looper.getMainLooper()).post(() -> completion.done(text)); }
        });
        return true;
    }
    static boolean request(Context context, Completion completion) {
        return work(context, completion, app -> {
            WebDavSettingsStore settings = new WebDavSettingsStore(app);
            if (!settings.configured()) return "请先保存 WebDAV 账号与应用密码";
            SyncState local = read(app);
            try (AndroidSyncData data = new AndroidSyncData(app)) {
                capture(app, data, local);
                SyncState merged;
                try (WebDavClient client = new WebDavClient(settings.url(), settings.user(), settings.password())) {
                    activeClient = client;
                    merged = SyncExchange.exchange(local, data.blobs, client);
                } finally { activeClient = null; }
                // Edits made while HTTP was in flight branch from the pre-network local knowledge.
                SyncState latest = read(app);
                AndroidSyncData.Snapshot snapshot = data.prepareCapture();
                data.db.beginTransaction();
                try {
                    data.captureTasks(latest, snapshot); data.captureTerms(latest, snapshot);
                    save(app, latest); merged.merge(latest); save(app, merged);
                    data.db.setTransactionSuccessful();
                } finally { data.db.endTransaction(); }
                data.prepareTaskApply(merged, snapshot, null);
                data.db.beginTransaction();
                try {
                    data.requireRevision(snapshot);
                    for (Map.Entry<String, List<SyncState.Version>> entry : merged.records().entrySet())
                        if (entry.getKey().startsWith("task_") && entry.getValue().size() == 1)
                            data.applyTask(entry.getKey(), entry.getValue().get(0));
                    data.db.setTransactionSuccessful();
                } finally { data.db.endTransaction(); }
                data.appliedResourcesCommitted();
                data.applyTerms(merged, snapshot.terms);
            }
            int pending = pending(app).size();
            return "上次同步：" + java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(new Date())
                    + (pending == 0 ? " · 成功" : " · " + pending + " 项待确认");
        });
    }
    private static void capture(Context app, AndroidSyncData data, SyncState state) throws Exception {
        AndroidSyncData.Snapshot snapshot = data.prepareCapture();
        capture(app, data, state, snapshot);
    }
    private static void capture(Context app, AndroidSyncData data, SyncState state, AndroidSyncData.Snapshot snapshot) throws Exception {
        data.db.beginTransaction();
        try { data.captureTasks(state, snapshot); data.captureTerms(state, snapshot); save(app, state); data.db.setTransactionSuccessful(); }
        finally { data.db.endTransaction(); }
    }
    static void foreground(Context context) {
        WebDavSettingsStore settings = new WebDavSettingsStore(context);
        if (!settings.automatic() || !settings.configured()) return;
        long now = android.os.SystemClock.elapsedRealtime();
        if (lastForeground != 0 && now - lastForeground < FOREGROUND_INTERVAL) return;
        if (request(context, null)) lastForeground = now;
    }
    static final class Pending {
        final String id, signature, title;
        final List<SyncState.Version> versions;
        final boolean calendar;
        Pending(String id, List<SyncState.Version> versions, boolean calendar) {
            this.id = id; this.versions = versions; this.calendar = calendar; signature = signature(versions);
            title = (id.startsWith("term_") ? "课表 " + id.substring(5) : "记录") + (calendar ? " · 日历关联待确认" : " · 版本冲突");
        }
    }
    private static String signature(List<SyncState.Version> versions) {
        StringBuilder value = new StringBuilder();
        for (SyncState.Version version : versions) value.append(SyncState.canonical(new JSONObject(version.clock)))
                .append(version.deleted).append(version.deleted ? "" : SyncState.canonical(version.payload));
        return value.toString();
    }
    static List<Pending> pending(Context context) throws Exception {
        SyncState state = read(context); List<Pending> result = new ArrayList<>();
        try (AndroidSyncData data = new AndroidSyncData(context)) {
            for (Map.Entry<String, List<SyncState.Version>> entry : state.records().entrySet()) {
                boolean linked = entry.getKey().startsWith("task_") && data.linked(data.localId(entry.getKey()));
                if (entry.getValue().size() > 1 || (linked && data.taskDifferent(entry.getKey(), entry.getValue().get(0))))
                    result.add(new Pending(entry.getKey(), entry.getValue(), linked));
            }
        }
        return result;
    }
    static String describe(SyncState.Version version) {
        if (version.deleted) return "已删除";
        JSONObject p = version.payload;
        if ("term".equals(p.optString("kind"))) return p.optJSONObject("document").optString("name", "课表");
        String text = p.optString("rawText");
        org.json.JSONArray candidates = p.optJSONArray("candidates");
        StringBuilder titles = new StringBuilder();
        if (candidates != null) for (int i = 0; i < candidates.length(); i++) titles.append(candidates.optJSONObject(i).optString("title")).append("\n");
        return titles + text;
    }
    static boolean resolve(Context context, Pending choice, int index, boolean removeCalendar, Completion completion) {
        return work(context, completion, app -> {
            SyncState state = read(app);
            try (AndroidSyncData data = new AndroidSyncData(app)) {
                AndroidSyncData.Snapshot snapshot = data.prepareCapture();
                capture(app, data, state, snapshot);
                List<SyncState.Version> versions = state.records().get(choice.id);
                if (versions == null || !choice.signature.equals(signature(versions))) throw new IOException("记录已变化，请重新选择");
                long local = data.localId(choice.id);
                if (index < 0) {
                    if (choice.id.startsWith("task_")) {
                        TaskRecord task = data.store.getTask(local);
                        if (AndroidSyncData.busy(task)) throw new IOException("记录正在处理");
                        if (task == null) state.delete(choice.id); else state.put(choice.id, snapshot.payloads.get(local));
                    } else {
                        JSONObject term = snapshot.termPayloads.get(choice.id);
                        if (term == null) state.delete(choice.id); else state.put(choice.id, term);
                    }
                } else state.resolve(choice.id, versions.get(index));
                boolean clearCalendar = index >= 0 && data.linked(local);
                if (clearCalendar && !removeCalendar) throw new IOException("需要确认移除本机日历旧事件");
                data.prepareTaskApply(state, snapshot, clearCalendar ? choice.id : null, choice.id);
                data.requireRevision(snapshot);
                // Provider work runs before the short SQLite commit. If any deletion fails,
                // all local associations remain available for an explicit retry.
                if (clearCalendar) {
                    CalendarStore calendar = new CalendarStore(app);
                    for (EventCandidate candidate : data.store.getCandidates(local)) if (candidate.calendarEventId != null) {
                        if (!calendar.deleteEvent(candidate.calendarEventId) && calendar.getEvent(candidate.calendarEventId) != null)
                            throw new IOException("日历旧事件未能全部移除，请重试");
                    }
                }
                data.db.beginTransaction();
                try {
                    data.requireRevision(snapshot);
                    if (clearCalendar) data.db.execSQL("UPDATE event_candidates SET calendar_event_id=NULL WHERE task_id=?", new Object[]{local});
                    save(app, state);
                    if (choice.id.startsWith("task_")) data.applyTask(choice.id, state.records().get(choice.id).get(0));
                    data.db.setTransactionSuccessful();
                } finally { data.db.endTransaction(); }
                data.appliedResourcesCommitted();
                if (choice.id.startsWith("term_")) data.applyTerms(state, snapshot.terms);
            }
            return "已确认，点击立即同步上传选择";
        });
    }
}
