package com.donglan.chrona;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import com.donglan.chrona.zju.ZjuClient;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** One network owner; each source has its own cadence and applies only its own results. */
final class ZjuSync {
    static final long HOMEWORK_INTERVAL = 5 * 60_000L, TIMETABLE_INTERVAL = 30 * 60_000L;
    private static final int HOMEWORK = 1, TIMETABLE = 2, ALL = HOMEWORK | TIMETABLE;
    static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final List<Consumer<String>> listeners = new ArrayList<>(), queuedListeners = new ArrayList<>();
    private static volatile ZjuClient client;
    private static volatile long epoch;
    private static long runningEpoch;
    private static int runningScopes, queuedScopes;
    private static Context foregroundContext;
    private static final Runnable FOREGROUND_CHECK = new Runnable() {
        @Override public void run() {
            if (foregroundContext == null) return;
            requestDue(foregroundContext, null);
            long delay = nextDelay(foregroundContext, System.currentTimeMillis());
            MAIN.postDelayed(this, Math.max(1_000L, Math.min(60_000L, delay < 0 ? 60_000L : delay)));
        }
    };

    static boolean request(Context context, Consumer<String> callback) { return request(context, ALL, callback); }
    static boolean requestHomework(Context context, Consumer<String> callback) { return request(context, HOMEWORK, callback); }
    static boolean requestDue(Context context, Consumer<String> callback) {
        return request(context, dueScopes(context, System.currentTimeMillis()), callback);
    }
    private static synchronized boolean request(Context context, int scopes, Consumer<String> callback) {
        Context app = context.getApplicationContext();
        ZjuSettingsStore settings = new ZjuSettingsStore(app);
        if (!settings.enabled() || !settings.configured()) {
            if (callback != null) deliver(List.of(callback), "请先打开折姜星辰并保存账号");
            return false;
        }
        if (scopes == 0) { if (callback != null) deliver(List.of(callback), settings.status()); return false; }
        if (runningScopes != 0) {
            if (runningEpoch == epoch && (scopes & ~runningScopes) == 0) { if (callback != null) listeners.add(callback); }
            else { queuedScopes |= scopes; if (callback != null) queuedListeners.add(callback); }
            return true;
        }
        runningScopes = scopes;
        if (callback != null) listeners.add(callback);
        long token = epoch;
        runningEpoch = token;
        WORKER.execute(() -> run(app, token, scopes));
        return true;
    }

    private static void run(Context context, long token, int scopes) {
        ZjuSettingsStore settings = new ZjuSettingsStore(context);
        ZjuHomeworkStore store = new ZjuHomeworkStore(context);
        String account = settings.account(), result;
        try {
            ZjuHomeworkStore.State before;
            synchronized (AndroidSync.LOCK) { checkCurrent(settings, account, token); before = store.state(); }
            boolean homework = (scopes & HOMEWORK) != 0 && before.enabled;
            boolean timetable = (scopes & TIMETABLE) != 0 && settings.timetable();
            if (!homework && !timetable) result = "未启用作业或课表同步";
            else {
                settings.status("正在同步");
                android.content.SharedPreferences.Editor attempts = settings.prefs.edit();
                long attemptedAt = System.currentTimeMillis();
                if (homework) attempts.putLong(attemptKey(HOMEWORK), attemptedAt);
                if (timetable) attempts.putLong(attemptKey(TIMETABLE), attemptedAt);
                attempts.apply();
                String[] credentials = settings.credentials();
                ZjuClient active;
                try { active = new ZjuClient(credentials[0], credentials[1], com.donglan.chrona.zju.ZjuCalendarStore.load(context)); }
                finally { java.util.Arrays.fill(credentials, ""); }
                client = active;
                List<String> results = new ArrayList<>();
                try (active) {
                    // Separate fetches share the same in-memory authenticated session. A failure
                    // cannot apply an unrequested/failed source's empty list or advance its clock.
                    for (int source : new int[]{HOMEWORK, TIMETABLE}) {
                        if (source == HOMEWORK ? !homework : !timetable) continue;
                        try {
                            synchronized (AndroidSync.LOCK) { checkCurrent(settings, account, token); }
                            boolean work = source == HOMEWORK;
                            ZjuSnapshot snapshot = active.fetch(work, !work, work
                                    ? store.trackedCourses(account, before.generation) : java.util.Set.of());
                            synchronized (AndroidSync.LOCK) {
                                checkCurrent(settings, account, token);
                                if (work) {
                                    ZjuHomeworkStore.Result applied = store.apply(account, before.generation, snapshot.homework);
                                    results.add("新增 " + applied.added + " · 更新 " + applied.updated + " · 完成 " + applied.completed);
                                    if (!applied.newHomework.isEmpty()) HomeworkNotice.show(context, applied.newHomework, applied.newHomeworkIds);
                                    int pending = ZjuHomeworkCalendar.pendingCount(context);
                                    if (pending > 0) results.add(pending + " 项作业待写入或更新日历");
                                } else {
                                    if (!settings.timetable()) throw new java.io.IOException("课表同步已关闭");
                                    new ZjuTimetableStore(context).save(account, snapshot.lessons, snapshot.completeTimetableTerms);
                                    results.add("课表已更新");
                                }
                                results.addAll(snapshot.warnings);
                                settings.prefs.edit().putLong("last_success", System.currentTimeMillis()).apply();
                                AgendaWidgetProvider.requestRefresh(context); AgendaReminder.requestRefresh(context);
                            }
                        } catch (Exception failure) { results.add((source == HOMEWORK ? "作业：" : "课表：") + message(failure)); }
                    }
                }
                result = String.join("\n", results);
            }
        } catch (Exception failure) { result = message(failure); }
        // School results/notifications finish first; one optional AI item never holds AndroidSync.LOCK.
        if (token == epoch) {
            String planning = ZjuHomeworkPlanner.processOne(context);
            if (!planning.isEmpty()) result += "\n" + planning;
        }
        synchronized (ZjuSync.class) {
            client = null; settings.status(result);
            List<Consumer<String>> completed = new ArrayList<>(listeners); listeners.clear();
            runningScopes = 0;
            int next = queuedScopes; queuedScopes = 0;
            List<Consumer<String>> queued = new ArrayList<>(queuedListeners); queuedListeners.clear();
            deliver(completed, result);
            if (next != 0) request(context, next, value -> { for (Consumer<String> listener : queued) listener.accept(value); });
        }
        // Job callbacks must finish the current job before its ID is rearmed.
        MAIN.post(() -> rearmIfIdle(context));
    }

    private static void checkCurrent(ZjuSettingsStore settings, String account, long token) throws java.io.IOException {
        if (token != epoch || !settings.enabled() || !account.equals(settings.account()))
            throw new java.io.IOException("设置已更改，本次同步已停止");
    }
    private static String message(Exception failure) {
        return failure instanceof java.io.IOException && failure.getMessage() != null
                ? failure.getMessage() : "同步未完成，请检查网络或重新填写账号";
    }
    private static void deliver(List<Consumer<String>> callbacks, String status) {
        MAIN.post(() -> { for (Consumer<String> callback : callbacks) callback.accept(status); });
    }
    static void cancel() {
        ZjuClient active;
        synchronized (AndroidSync.LOCK) { epoch++; active = client; }
        if (active != null) active.cancel();
        ZjuHomeworkPlanner.cancel();
    }
    private static String attemptKey(int source) { return source == HOMEWORK ? "homework_attempt" : "timetable_attempt"; }
    private static long remaining(ZjuSettingsStore settings, int source, long now) {
        long last = settings.prefs.getLong(attemptKey(source), 0);
        long interval = source == HOMEWORK ? HOMEWORK_INTERVAL : TIMETABLE_INTERVAL;
        return last <= 0 || last > now ? 0 : Math.max(0, interval - (now - last));
    }
    private static int dueScopes(Context context, long now) {
        ZjuSettingsStore settings = new ZjuSettingsStore(context);
        if (!settings.enabled() || !settings.configured()) return 0;
        int scopes = 0;
        if (new ZjuHomeworkStore(context).state().enabled && remaining(settings, HOMEWORK, now) == 0) scopes |= HOMEWORK;
        if (settings.timetable() && remaining(settings, TIMETABLE, now) == 0) scopes |= TIMETABLE;
        return scopes;
    }
    static long nextDelay(Context context, long now) {
        ZjuSettingsStore settings = new ZjuSettingsStore(context);
        if (!settings.enabled() || !settings.configured()) return -1;
        long delay = Long.MAX_VALUE;
        if (new ZjuHomeworkStore(context).state().enabled) delay = remaining(settings, HOMEWORK, now);
        if (settings.timetable()) delay = Math.min(delay, remaining(settings, TIMETABLE, now));
        return delay == Long.MAX_VALUE ? -1 : delay;
    }
    static void foreground(Context context) {
        foregroundContext = context.getApplicationContext();
        MAIN.removeCallbacks(FOREGROUND_CHECK);
        FOREGROUND_CHECK.run();
    }
    static void background() { foregroundContext = null; MAIN.removeCallbacks(FOREGROUND_CHECK); }
    static synchronized void rearmIfIdle(Context context) {
        if (runningScopes == 0) ZjuSyncJobService.schedule(context);
    }
    private ZjuSync() { }
}
