package com.donglan.chrona.processing;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.PersistableBundle;

import com.donglan.chrona.TaskDetailActivity;
import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.ai.ChatCompletionClient;
import com.donglan.chrona.ai.ParseResult;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.debug.DiagLog;
import com.donglan.chrona.image.ImageStore;
import com.donglan.chrona.web.LinkFetcher;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/** Durable, network-constrained parsing work for saved inputs. */
public final class ProcessingJobService extends JobService {
    private static final String EXTRA_TASK_ID = "task_id";
    private static final int JOB_ID_BASE = 10_000;
    private static final String CHANNEL_ID = "chrona_processing";
    /** The same input must never be parsed by two workers at once. */
    private static final Set<Long> IN_FLIGHT = Collections.synchronizedSet(new HashSet<>());
    private static final int MAX_REQUEST_ATTEMPTS = 3;
    private static final long RETRY_DELAY_MILLIS = 3_000L;
    /** Keeps retries inside the job's runtime budget even when a request burns its whole timeout. */
    private static final long RETRY_BUDGET_MILLIS = 240_000L;
    private final ConcurrentHashMap<Integer, Thread> workers = new ConcurrentHashMap<>();

    public static void enqueue(Context context, long taskId) {
        if (taskId <= 0 || taskId > Integer.MAX_VALUE - JOB_ID_BASE) {
            throw new IllegalArgumentException("Unsupported task ID");
        }
        PersistableBundle extras = new PersistableBundle();
        extras.putLong(EXTRA_TASK_ID, taskId);
        JobInfo job = new JobInfo.Builder(JOB_ID_BASE + (int) taskId,
                new ComponentName(context, ProcessingJobService.class))
                .setExtras(extras)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build();
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null || scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            DiagLog.add(context, "enqueue failed task=" + taskId);
            throw new IllegalStateException("Could not schedule parsing work");
        }
        DiagLog.add(context, "enqueued task=" + taskId + " jobId=" + (JOB_ID_BASE + (int) taskId));
    }

    /** Cancels queued or running parsing before an input is removed. */
    public static void cancel(Context context, long taskId) {
        if (taskId <= 0 || taskId > Integer.MAX_VALUE - JOB_ID_BASE) {
            throw new IllegalArgumentException("Unsupported task ID");
        }
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) scheduler.cancel(JOB_ID_BASE + (int) taskId);
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        long taskId = params.getExtras().getLong(EXTRA_TASK_ID, -1L);
        if (taskId <= 0) return false;
        Thread worker = new Thread(() -> process(params, taskId), "chrona-parse-" + taskId);
        workers.put(params.getJobId(), worker);
        worker.start();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Thread worker = workers.remove(params.getJobId());
        if (worker != null) worker.interrupt();
        return true;
    }

    private void process(JobParameters params, long taskId) {
        if (!IN_FLIGHT.add(taskId)) {
            // A second run would upload the image and pay for the request twice over.
            DiagLog.add(this, "job skipped task=" + taskId + " (already being processed)");
            workers.remove(params.getJobId());
            jobFinished(params, false);
            return;
        }
        try {
            processOnce(params, taskId);
        } finally {
            IN_FLIGHT.remove(taskId);
        }
    }

    private void processOnce(JobParameters params, long taskId) {
        AiSettings settings = null;
        boolean sentImage = false;
        DiagLog.add(this, "job start task=" + taskId);
        try (TaskStore store = new TaskStore(this)) {
            TaskRecord task = store.getTask(taskId);
            if (task == null) {
                DiagLog.add(this, "job dropped: task=" + taskId + " no longer exists");
                return;
            }
            settings = new AiSettingsStore(this).load();
            if (settings == null) {
                store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW, "请先配置 AI 服务");
                notifyResult(taskId, "请配置 AI 服务后重试");
                DiagLog.add(this, "job skipped: no AI settings");
                return;
            }
            DiagLog.add(this, "settings model=" + settings.model + " base=" + settings.baseUrl);
            byte[] image = null;
            if (task.imagePath != null) {
                try {
                    image = new ImageStore(this).read(task.imagePath);
                    DiagLog.add(this, "image task=" + taskId + " bytes=" + image.length);
                } catch (IOException exception) {
                    store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW, "图片不可用，请移除图片后重试");
                    notifyResult(taskId, "图片不可用，点按查看详情");
                    DiagLog.add(this, "image unreadable task=" + taskId
                            + " path=" + task.imagePath + " " + exception);
                    return;
                }
            }
            sentImage = image != null;
            store.updateStatus(taskId, TaskRecord.PROCESSING, null);
            String linkText = fetchLinks(store, task);
            DiagLog.add(this, "request task=" + taskId
                    + " text=" + task.rawText.length() + "chars"
                    + " image=" + (sentImage ? image.length + "B" : "none")
                    + " linkText=" + (linkText == null ? 0 : linkText.length()) + "chars"
                    + " readTimeout=" + (ChatCompletionClient.readTimeoutMillis(sentImage) / 1000)
                    + "s");
            long startedAt = System.currentTimeMillis();
            ParseResult result = requestWithRetries(settings, task, image, linkText, taskId);
            long elapsed = System.currentTimeMillis() - startedAt;
            if (Thread.currentThread().isInterrupted()) return;
            store.replaceCandidates(taskId, result.candidates);
            store.updateUsage(taskId, result.promptTokens, result.completionTokens,
                    result.totalTokens);
            store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW,
                    result.candidates.isEmpty() ? "未识别到日程，请检查原文或重试" : null);
            DiagLog.add(this, "response task=" + taskId + " ok in " + elapsed + "ms events="
                    + result.candidates.size() + " tokens=" + result.totalTokens);
            notifyResult(taskId, result.candidates.isEmpty() ? "未识别到日程" : "日程草稿待确认");
        } catch (ChatCompletionClient.RequestException exception) {
            if (!Thread.currentThread().isInterrupted()) {
                DiagLog.add(this, "request rejected task=" + taskId + " HTTP "
                        + exception.statusCode + " " + message(exception));
                if (sentImage && settings != null && exception.rejectsImage()) {
                    // The provider refused the image itself, so stop offering images for this model.
                    new AiSettingsStore(this).markImageUnsupported(settings);
                    recordFailure(taskId, "该模型不支持图片输入，可在设置中重新启用："
                            + message(exception));
                } else {
                    recordFailure(taskId, message(exception));
                }
            }
        } catch (Exception exception) {
            if (!Thread.currentThread().isInterrupted()) {
                DiagLog.add(this, "failed task=" + taskId + " "
                        + exception.getClass().getSimpleName() + ": " + message(exception));
                recordFailure(taskId, message(exception));
            }
        } finally {
            workers.remove(params.getJobId());
            if (!Thread.currentThread().isInterrupted()) jobFinished(params, false);
        }
    }

    /**
     * Reads the pages behind links in the input and remembers the outcome. A link that cannot be
     * read never fails the parse: the input is simply parsed without that extra text.
     */
    private String fetchLinks(TaskStore store, TaskRecord task) {
        List<String> urls = LinkFetcher.extractUrls(task.rawText);
        if (urls.isEmpty()) return null;
        long startedAt = System.currentTimeMillis();
        String text = LinkFetcher.fetch(urls);
        DiagLog.add(this, "links task=" + task.id + " urls=" + urls.size() + " chars="
                + text.length() + " in " + (System.currentTimeMillis() - startedAt) + "ms");
        store.updateLinkFetch(task.id, text.isEmpty() ? null : text, System.currentTimeMillis());
        return text.isEmpty() ? null : text;
    }

    /**
     * Sends the request, retrying failures a later attempt can plausibly fix. A phone on a shaky
     * network otherwise fails on a dropped DNS lookup or an aborted connection and makes the user
     * press retry by hand.
     */
    private ParseResult requestWithRetries(AiSettings settings, TaskRecord task, byte[] image,
            String linkText, long taskId) throws IOException {
        long startedAt = System.currentTimeMillis();
        for (int attempt = 1; ; attempt++) {
            try {
                return new ChatCompletionClient(settings).parse(taskId, task.rawText, image,
                        linkText, System.currentTimeMillis(), TimeZone.getDefault().getID());
            } catch (IOException exception) {
                boolean giveUp = attempt >= MAX_REQUEST_ATTEMPTS
                        || System.currentTimeMillis() - startedAt > RETRY_BUDGET_MILLIS
                        || !isTransient(exception);
                if (giveUp) throw exception;
                DiagLog.add(this, "request attempt " + attempt + " failed task=" + taskId + " "
                        + exception.getClass().getSimpleName() + ": " + message(exception)
                        + " -> retry in " + (RETRY_DELAY_MILLIS / 1000) + "s");
                if (!sleep(RETRY_DELAY_MILLIS)) throw exception;
            }
        }
    }

    /** True for failures worth another attempt; a server that answered 4xx will not change. */
    private static boolean isTransient(IOException exception) {
        if (exception instanceof ChatCompletionClient.RequestException) {
            int status = ((ChatCompletionClient.RequestException) exception).statusCode;
            return status == 429 || status >= 500;
        }
        return true;
    }

    /** Returns false when the worker was interrupted while waiting. */
    private static boolean sleep(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void recordFailure(long taskId, String message) {
        try (TaskStore store = new TaskStore(this)) {
            store.updateStatus(taskId, TaskRecord.FAILED, message);
        }
        notifyResult(taskId, "处理失败，点按查看详情");
    }

    private static String message(Exception exception) {
        return exception.getMessage() == null ? "处理失败" : exception.getMessage();
    }

    private void notifyResult(long taskId, String text) {
        try (TaskStore store = new TaskStore(this)) {
            if (store.getTask(taskId) == null) return;
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null) return;
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;
        manager.createNotificationChannel(new NotificationChannel(CHANNEL_ID, "处理结果",
                NotificationManager.IMPORTANCE_DEFAULT));
        Intent intent = new Intent(this, TaskDetailActivity.class).putExtra("task_id", taskId);
        PendingIntent pending = PendingIntent.getActivity(this, (int) taskId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("拾时 · Chrona")
                .setContentText(text)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build();
        manager.notify((int) taskId, notification);
    }
}
