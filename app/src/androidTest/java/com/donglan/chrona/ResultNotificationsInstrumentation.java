package com.donglan.chrona;

import android.app.Instrumentation;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.os.Bundle;
import android.os.SystemClock;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.processing.ProcessingJobService;
import com.donglan.chrona.processing.ResultNotifications;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import org.json.JSONArray;
import org.json.JSONObject;

/** Real NotificationManager and SQLite lifecycle checks; synthetic v33 emulator records only. */
public final class ResultNotificationsInstrumentation extends Instrumentation {
    private final List<Long> fixtures = new ArrayList<>();
    private final List<String> sentinels = new ArrayList<>();
    private Context context;
    private NotificationManager manager;
    private ProcessingJobService service;
    private Method notifyResult;
    private int assertions;

    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }

    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            check();
            result.putString("result_notifications_report", "PASS: " + assertions
                    + " assertions; actual NotificationManager auto suppression, manual TaskStore-close cleanup,"
                    + " mixed batch, failed/processing/queued/empty/permission drafts, legacy/orphan cleanup,"
                    + " unrelated channels, idempotence; raw source/candidates/parse audit retained in SQLite");
            finish(-1, result);
        } catch (Throwable failure) {
            result.putString("error", android.util.Log.getStackTraceString(failure));
            finish(0, result);
        }
    }

    private void check() throws Exception {
        context = getTargetContext(); FixtureIsolation.require(context);
        manager = context.getSystemService(NotificationManager.class);
        getUiAutomation().grantRuntimePermission(context.getPackageName(), android.Manifest.permission.POST_NOTIFICATIONS);
        service = new ProcessingJobService();
        Method attach = ContextWrapper.class.getDeclaredMethod("attachBaseContext", Context.class);
        attach.setAccessible(true); attach.invoke(service, context);
        notifyResult = ProcessingJobService.class.getDeclaredMethod("notifyResult", long.class, String.class);
        notifyResult.setAccessible(true);
        try {
            long manual = fixture(TaskRecord.NEEDS_REVIEW, true, false, false, true);
            String originalAudit = audit(manual);
            emit(manual); await(() -> active(null, manual), "manual pending result posted by production publisher");
            try (TaskStore store = new TaskStore(context)) {
                EventCandidate candidate = store.getCandidates(manual).get(0);
                require(store.updateCandidate(copy(candidate, false)), "manual review candidate saved");
                require(store.updateReviewStatus(manual), "manual review status saved");
            }
            // No explicit refresh: the production TaskStore.close revision hook must do this.
            await(() -> !active(null, manual), "manual confirmation automatically retracts result after store close");
            require(originalAudit.equals(audit(manual)), "successful parse run and exact field evidence retained");
            retained(manual, 1);

            long automatic = fixture(TaskRecord.READY, false, false, false, true);
            post(null, automatic, ResultNotifications.CHANNEL_ID);
            emit(automatic);
            await(() -> !active(null, automatic), "automatic settled result cancels old result and publishes nothing");
            emit(automatic); SystemClock.sleep(400);
            require(!active(null, automatic), "automatic settled result remains suppressed on repeat publication");
            retained(automatic, 1);

            long event = fixture(TaskRecord.READY, false, false, true, false);
            try (TaskStore store = new TaskStore(context)) {
                store.appendCandidate(new EventCandidate(0, event, "synthetic saved event",
                        1_900_000_000_000L, 1_900_003_600_000L, "Asia/Shanghai", "", "", 15, false, 9_000_036L));
            }
            emit(event);
            require(!active(null, event), "confirmed calendar-linked event does not publish an unnecessary result");
            retained(event, 1);

            long mixed = fixture(TaskRecord.READY, false, true, false, true);
            emit(mixed); await(() -> active(null, mixed), "mixed batch with outstanding confirmation remains visible");
            ResultNotifications.refresh(context);
            require(active(null, mixed), "background cleanup preserves pending portion of mixed batch");
            try (TaskStore store = new TaskStore(context)) {
                EventCandidate pending = store.getCandidates(mixed).get(1);
                require(store.updateCandidate(copy(pending, false)), "last mixed item confirmed");
                store.updateReviewStatus(mixed);
            }
            await(() -> !active(null, mixed), "last mixed confirmation automatically retracts result");
            retained(mixed, 2);

            for (String status : List.of(TaskRecord.FAILED, TaskRecord.PROCESSING, TaskRecord.QUEUED)) {
                long task = fixture(status, false, false, false, false);
                emit(task); await(() -> active(null, task), status + " result posts despite settled candidate");
                ResultNotifications.refresh(context);
                require(active(null, task), status + " result survives cleanup");
                retained(task, 1);
            }
            long empty = fixture(TaskRecord.READY, false, false, true, false);
            emit(empty); await(() -> active(null, empty), "empty result remains available");
            ResultNotifications.refresh(context); require(active(null, empty), "empty READY result not considered confirmed");
            retained(empty, 0);

            long permission = fixture(TaskRecord.READY, false, false, true, false);
            try (TaskStore store = new TaskStore(context)) {
                store.appendCandidate(new EventCandidate(0, permission, "synthetic permission draft",
                        1_900_000_000_000L, 1_900_003_600_000L, "Asia/Shanghai", "", "", 15, false));
            }
            emit(permission); await(() -> active(null, permission), "unlinked event after calendar permission failure remains visible");
            ResultNotifications.refresh(context); require(active(null, permission), "unlinked event is not settled");

            // Same numeric task id must never authorize cancellation on another notification channel.
            for (String channel : List.of("chrona_parsing", "upcoming_agenda", "live_agenda_updates", "zju_homework_updates")) {
                String tag = "result-check-" + channel; sentinels.add(tag); post(tag, automatic, channel);
            }
            post("result-check-legacy", automatic, "chrona_processing"); sentinels.add("result-check-legacy");
            int orphan = Integer.MAX_VALUE - 36;
            post("result-check-orphan", orphan, ResultNotifications.CHANNEL_ID); sentinels.add("result-check-orphan");
            ResultNotifications.refresh(context);
            await(() -> !active("result-check-legacy", automatic) && !active("result-check-orphan", orphan),
                    "legacy result and missing-source result cancelled");
            ResultNotifications.refresh(context); ResultNotifications.refresh(context);
            for (String channel : List.of("chrona_parsing", "upcoming_agenda", "live_agenda_updates", "zju_homework_updates"))
                require(active("result-check-" + channel, automatic), channel + " untouched despite same task id");
            require(active(null, empty) && active(null, permission), "repeated cleanup idempotent and preserves outstanding results");
            retained(manual, 1); retained(automatic, 1); retained(mixed, 2);
            require(originalAudit.equals(audit(manual)), "all cleanup passes preserve original audit bytes");
        } finally {
            for (long id : fixtures) manager.cancel((int) id);
            for (String tag : sentinels) for (var notice : manager.getActiveNotifications())
                if (tag.equals(notice.getTag())) manager.cancel(tag, notice.getId());
            try (TaskStore store = new TaskStore(context)) {
                for (long id : fixtures) if (store.getTask(id) != null) store.deleteTask(id);
            }
        }
    }

    private long fixture(String status, boolean pending, boolean mixed, boolean empty, boolean audited) throws Exception {
        try (TaskStore store = new TaskStore(context)) {
            long id = store.insertTask("result-notification-check: original source retained", null,
                    "result_notification_fixture", System.currentTimeMillis()); fixtures.add(id);
            List<EventCandidate> candidates = new ArrayList<>();
            if (!empty) candidates.add(candidate(0, id, pending));
            if (mixed) candidates.add(candidate(0, id, true));
            if (audited) {
                String run = store.beginParseRun(id, System.currentTimeMillis(), new JSONObject().put("synthetic", true));
                JSONArray entries = new JSONArray();
                for (int i = 0; i < candidates.size(); i++) entries.put(new JSONObject().put("position", i)
                        .put("plan_group", JSONObject.NULL).put("model", new JSONObject().put("basis", "synthetic source"))
                        .put("resolved", new JSONObject().put("synthetic", true)));
                store.replaceParsedCandidates(id, candidates, candidates, run, new JSONObject().put("entries", entries).toString());
            } else store.replaceCandidates(id, candidates);
            store.updateStatus(id, status, TaskRecord.FAILED.equals(status) ? "synthetic failure" : null);
            return id;
        }
    }
    private EventCandidate candidate(long id, long task, boolean pending) {
        return new EventCandidate(id, task, "synthetic confirmed task", null, null, "Asia/Shanghai", "", "source detail", null,
                pending, null, "task", false);
    }
    private EventCandidate copy(EventCandidate saved, boolean pending) { return candidate(saved.id, saved.taskId, pending); }
    private String audit(long task) throws Exception {
        try (TaskStore store = new TaskStore(context)) {
            JSONArray audit = store.getParseAudits(task, null, null, null, 0, 10);
            require(audit.length() == 1 && "succeeded".equals(audit.getJSONObject(0).getString("status")), "real SQLite successful parse audit readable");
            require(audit.getJSONObject(0).getJSONArray("entries").length() == 1, "candidate-linked time evidence readable");
            return audit.toString();
        }
    }
    private void retained(long task, int count) {
        try (TaskStore store = new TaskStore(context)) {
            require(store.getTask(task).rawText.equals("result-notification-check: original source retained"), "original received source preserved");
            require(store.getCandidates(task).size() == count, "saved schedules preserved");
        }
    }
    private void emit(long task) throws Exception { notifyResult.invoke(service, task, "synthetic parsing result"); SystemClock.sleep(300); }
    private void post(String tag, long id, String channel) {
        manager.createNotificationChannel(new NotificationChannel(channel, channel, NotificationManager.IMPORTANCE_DEFAULT));
        manager.notify(tag, (int) id, new Notification.Builder(context, channel).setSmallIcon(R.drawable.ic_notifications)
                .setContentTitle("synthetic isolated result check").setContentText(channel).build());
        await(() -> active(tag, id), "real notification sentinel posted on " + channel); SystemClock.sleep(300);
    }
    private boolean active(String tag, long id) {
        return Arrays.stream(manager.getActiveNotifications()).anyMatch(n -> n.getId() == (int) id && java.util.Objects.equals(tag, n.getTag()));
    }
    private void await(BooleanSupplier ready, String reason) {
        long until = SystemClock.uptimeMillis() + 7000;
        do { if (ready.getAsBoolean()) { assertions++; return; } SystemClock.sleep(50); } while (SystemClock.uptimeMillis() < until);
        throw new AssertionError(reason);
    }
    private void require(boolean value, String reason) { if (!value) throw new AssertionError(reason); assertions++; }
}
