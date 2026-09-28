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
import com.donglan.chrona.data.TaskFileAttachment;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.data.CandidateRules;
import com.donglan.chrona.debug.DiagLog;
import com.donglan.chrona.image.ImageStore;
import com.donglan.chrona.web.LinkFetcher;

import java.io.IOException;
import java.util.Collections;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/** Durable, network-constrained parsing work for saved inputs. */
public final class ProcessingJobService extends JobService {
    private static final String EXTRA_TASK_ID = "task_id";
    private static final int JOB_ID_BASE = 10_000;
    private static final String CHANNEL_ID = "chrona_results";
    /** The pre-0.13.29 result channel; deleted once because its importance can never be raised. */
    private static final String LEGACY_CHANNEL_ID = "chrona_processing";
    private static final String RUNNING_CHANNEL_ID = "chrona_parsing";
    /** Keeps the running notice clear of the result notice posted for the same input. */
    private static final int RUNNING_NOTIFICATION_BASE = 20_000;
    /**
     * Order-of-magnitude traffic per parse: a one-image input uploads at most ~215 KB and every
     * response is a few KB of JSON, so one estimate covers both the image and the text path.
     */
    private static final long ESTIMATED_DOWNLOAD_BYTES = 128 * 1024L;
    private static final long ESTIMATED_UPLOAD_BYTES = 512 * 1024L;
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
        JobInfo.Builder builder = new JobInfo.Builder(JOB_ID_BASE + (int) taskId,
                new ComponentName(context, ProcessingJobService.class))
                .setExtras(extras)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setBackoffCriteria(30_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // The user just tapped submit and is waiting for one network round trip, which is what
            // a user-initiated job is for: top priority, exempt from quotas, and the system keeps
            // the process alive for it instead of letting a background cleaner kill it mid-request.
            // Such a job cannot be persisted, so an input abandoned by a reboot is failed by
            // reconcile() on the next launch rather than resumed.
            builder.setUserInitiated(true)
                    .setEstimatedNetworkBytes(ESTIMATED_DOWNLOAD_BYTES, ESTIMATED_UPLOAD_BYTES);
        } else {
            builder.setPersisted(true);
        }
        JobInfo job = builder.build();
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler == null || scheduler.schedule(job) != JobScheduler.RESULT_SUCCESS) {
            DiagLog.add(context, "enqueue failed task=" + taskId);
            throw new IllegalStateException("Could not schedule parsing work");
        }
        DiagLog.add(context, "enqueued task=" + taskId + " jobId=" + (JOB_ID_BASE + (int) taskId)
                + " userInitiated=" + (Build.VERSION.SDK_INT >= 34 && job.isUserInitiated()));
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
        // Claimed on the main thread so a concurrent reconcile() cannot mistake a starting job
        // for one that was lost with a dead process.
        if (!IN_FLIGHT.add(taskId)) {
            // A second run would upload the image and pay for the request twice over.
            DiagLog.add(this, "job skipped task=" + taskId + " (already being processed)");
            return false;
        }
        boolean userInitiated = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
                && params.isUserInitiatedJob();
        if (userInitiated) {
            // A user-initiated job that shows no notification within 10 seconds is stopped by the
            // system, so this comes before anything else the job does.
            setNotification(params, RUNNING_NOTIFICATION_BASE + (int) taskId,
                    runningNotification(taskId), JOB_END_NOTIFICATION_POLICY_REMOVE);
        }
        DiagLog.add(this, "job start task=" + taskId + " jobId=" + params.getJobId()
                + " userInitiated=" + userInitiated);
        Thread worker = new Thread(() -> process(params, taskId), "chrona-parse-" + taskId);
        workers.put(params.getJobId(), worker);
        worker.start();
        return true;
    }

    /** Describes the work the running job is doing, as a user-initiated job's notice must. */
    private Notification runningNotification(long taskId) {
        String preview = "";
        try (TaskStore store = new TaskStore(this)) {
            TaskRecord task = store.getTask(taskId);
            if (task != null) {
                preview = task.rawText.replace('\n', ' ').trim();
                if (task.imagePath != null) preview = "[图片] " + preview;
                if (preview.length() > 40) preview = preview.substring(0, 40) + "…";
            }
        } catch (RuntimeException exception) {
            DiagLog.add(this, "running notice lookup failed task=" + taskId + " " + exception);
        }
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) {
            manager.createNotificationChannel(new NotificationChannel(RUNNING_CHANNEL_ID, "解析进行中",
                    NotificationManager.IMPORTANCE_LOW));
        }
        Intent intent = new Intent(this, TaskDetailActivity.class).putExtra("task_id", taskId);
        PendingIntent pending = PendingIntent.getActivity(this, (int) taskId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Builder(this, RUNNING_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("拾时 · Chrona 正在解析")
                .setContentText(preview.isEmpty() ? "正在解析这条输入" : preview)
                .setOngoing(true)
                .setContentIntent(pending)
                .build();
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        long taskId = params.getExtras().getLong(EXTRA_TASK_ID, -1L);
        Thread worker = workers.remove(params.getJobId());
        DiagLog.add(this, "job stopped task=" + taskId + " worker=" + (worker != null));
        if (worker != null) worker.interrupt();
        return true;
    }

    /**
     * Fails inputs that a dead process left in "parsing": their job is gone and no worker holds
     * them, so nothing will ever finish them and the detail screen keeps the retry button disabled.
     * The input is only failed, never re-parsed on its own, so nothing is billed twice.
     *
     * @return how many inputs were recovered
     */
    public static int reconcile(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        int recovered = 0;
        try (TaskStore store = new TaskStore(context)) {
            for (TaskRecord task : store.listTasks()) {
                if (!TaskRecord.PROCESSING.equals(task.status)) continue;
                if (IN_FLIGHT.contains(task.id)) continue;
                if (scheduler != null
                        && scheduler.getPendingJob(JOB_ID_BASE + (int) task.id) != null) continue;
                store.updateStatus(task.id, TaskRecord.FAILED,
                        "解析被系统中断（应用在后台被清理），请重新解析");
                DiagLog.add(context, "reconciled task=" + task.id + " stuck parsing -> failed");
                recovered++;
            }
        } catch (RuntimeException exception) {
            DiagLog.add(context, "reconcile failed: " + exception);
        }
        return recovered;
    }

    private void process(JobParameters params, long taskId) {
        try {
            processOnce(params, taskId);
        } finally {
            IN_FLIGHT.remove(taskId);
        }
    }

    private void processOnce(JobParameters params, long taskId) {
        AiSettings settings = null;
        boolean sentImage = false;
        try (TaskStore store = new TaskStore(this)) {
            TaskRecord task = store.getTask(taskId);
            if (task == null) {
                DiagLog.add(this, "job dropped: task=" + taskId + " no longer exists");
                return;
            }
            // A job can outlive the parse it was queued for: a persisted job restored after an
            // update, or a run the system rescheduled. Only an input that is still waiting for a
            // parse is worth a request, so a stale run can never pay for the same answer twice.
            if (!TaskRecord.QUEUED.equals(task.status)
                    && !TaskRecord.PROCESSING.equals(task.status)) {
                DiagLog.add(this, "job skipped task=" + taskId + " status=" + task.status
                        + " (no longer waiting to be parsed)");
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
            List<String> imagePaths = store.getImagePaths(taskId);
            List<byte[]> images = new ArrayList<>(imagePaths.size());
            int imageBytes = 0;
            if (!imagePaths.isEmpty()) {
                try {
                    ImageStore imageStore = new ImageStore(this);
                    for (String imagePath : imagePaths) {
                        byte[] image = imageStore.read(imagePath);
                        images.add(image);
                        imageBytes += image.length;
                    }
                    DiagLog.add(this, "images task=" + taskId + " count=" + images.size()
                            + " bytes=" + imageBytes);
                } catch (IOException exception) {
                    store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW, "图片不可用，请移除图片后重试");
                    notifyResult(taskId, "图片不可用，点按查看详情");
                    DiagLog.add(this, "image unreadable task=" + taskId
                            + " " + exception);
                    return;
                }
            }
            sentImage = !images.isEmpty();
            String attachmentMetadata = formatFileMetadata(store.getFileAttachments(taskId));
            store.updateStatus(taskId, TaskRecord.PROCESSING, null);
            LinkFetcher.FetchResult fetched = fetchLinks(store, task);
            String linkText = fetched.text.isEmpty() ? null : fetched.text;
            if (linkText == null && images.isEmpty() && attachmentMetadata.isEmpty()
                    && LinkFetcher.onlyLinks(task.rawText)) {
                recordFailure(taskId, fetched.warning == null ? "链接未能读取，请补充正文或截图" : fetched.warning);
                return;
            }
            DiagLog.add(this, "request task=" + taskId
                    + " text=" + task.rawText.length() + "chars"
                    + " images=" + (sentImage ? images.size() + "/" + imageBytes + "B" : "none")
                    + " files=" + store.getFileAttachments(taskId).size()
                    + " linkText=" + (linkText == null ? 0 : linkText.length()) + "chars"
                    + " readTimeout=" + (ChatCompletionClient.readTimeoutMillis(sentImage) / 1000)
                    + "s");
            long startedAt = System.currentTimeMillis();
            ParseResult result = requestWithRetries(settings, task, images, linkText,
                    attachmentMetadata, taskId);
            long elapsed = System.currentTimeMillis() - startedAt;
            if (Thread.currentThread().isInterrupted()) {
                DiagLog.add(this, "response dropped task=" + taskId + " after " + elapsed
                        + "ms (job stopped before the draft was saved)");
                return;
            }
            List<com.donglan.chrona.data.EventCandidate> candidates = CandidateRules.apply(result.candidates);
            store.replaceCandidates(taskId, candidates);
            store.updateUsage(taskId, result.promptTokens, result.completionTokens,
                    result.totalTokens, result.cachedTokens);
            store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW,
                    candidates.isEmpty() ? (fetched.warning == null ? "未识别到日程，请检查原文或重试"
                            : fetched.warning) : fetched.warning);
            DiagLog.add(this, "response task=" + taskId + " ok in " + elapsed + "ms events="
                    + candidates.size() + " tokens=" + result.totalTokens);
            notifyResult(taskId, candidates.isEmpty() ? "未识别到日程" : "日程草稿待确认");
        } catch (ChatCompletionClient.RequestException exception) {
            if (Thread.currentThread().isInterrupted()) {
                DiagLog.add(this, "request abandoned task=" + taskId + " HTTP "
                        + exception.statusCode + " (job stopped while waiting)");
            } else {
                DiagLog.add(this, "request rejected task=" + taskId + " HTTP "
                        + exception.statusCode);
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
            if (Thread.currentThread().isInterrupted()) {
                DiagLog.add(this, "parse abandoned task=" + taskId + " (job stopped while waiting) "
                        + exception.getClass().getSimpleName());
            } else {
                DiagLog.add(this, "failed task=" + taskId + " "
                        + exception.getClass().getSimpleName());
                recordFailure(taskId, message(exception));
            }
        } finally {
            workers.remove(params.getJobId());
            if (!Thread.currentThread().isInterrupted()) jobFinished(params, false);
        }
    }

    /**
     * Reads the pages behind links in the input and remembers the outcome. A link that cannot be
     * read contributes no model text. Link-only inputs are stopped by the caller when unreadable.
     */
    private LinkFetcher.FetchResult fetchLinks(TaskStore store, TaskRecord task) {
        List<String> urls = LinkFetcher.extractUrls(task.rawText);
        if (urls.isEmpty()) return new LinkFetcher.FetchResult("", null);
        long startedAt = System.currentTimeMillis();
        LinkFetcher.FetchResult fetched = LinkFetcher.fetchResult(urls);
        String text = fetched.text;
        DiagLog.add(this, "links task=" + task.id + " urls=" + urls.size() + " chars="
                + text.length() + " in " + (System.currentTimeMillis() - startedAt) + "ms");
        store.updateLinkFetch(task.id, text.isEmpty() ? null : text, System.currentTimeMillis());
        return fetched;
    }

    /**
     * Sends the request, retrying failures a later attempt can plausibly fix. A phone on a shaky
     * network otherwise fails on a dropped DNS lookup or an aborted connection and makes the user
     * press retry by hand.
     */
    private ParseResult requestWithRetries(AiSettings settings, TaskRecord task, List<byte[]> images,
            String linkText, String attachmentMetadata, long taskId) throws IOException {
        long startedAt = System.currentTimeMillis();
        final long referenceTime = startedAt;
        final String referenceZone = TimeZone.getDefault().getID();
        for (int attempt = 1; ; attempt++) {
            long attemptStartedAt = System.currentTimeMillis();
            DiagLog.add(this, "attempt " + attempt + "/" + MAX_REQUEST_ATTEMPTS
                    + " task=" + taskId);
            try {
                try (StreamingOutputStore.Writer output =
                        new StreamingOutputStore(this).begin(taskId)) {
                    ChatCompletionClient client = new ChatCompletionClient(settings);
                    ChatCompletionClient.PreviewSink preview = new ChatCompletionClient.PreviewSink() {
                        @Override public void append(String chunk) throws IOException {
                            output.append(chunk);
                        }

                        @Override public void appendReasoning(String chunk) throws IOException {
                            output.appendReasoning(chunk);
                        }
                    };
                    return client.parseImages(taskId, task.rawText, images, linkText,
                            attachmentMetadata, referenceTime, referenceZone, preview, true);
                }
            } catch (IOException exception) {
                long attemptMillis = System.currentTimeMillis() - attemptStartedAt;
                boolean giveUp = attempt >= MAX_REQUEST_ATTEMPTS
                        || System.currentTimeMillis() - startedAt > RETRY_BUDGET_MILLIS
                        || !isTransient(exception);
                if (giveUp) {
                    DiagLog.add(this, "giving up task=" + taskId + " after " + attempt
                            + " attempt(s) in " + (System.currentTimeMillis() - startedAt) + "ms: "
                            + exception.getClass().getSimpleName());
                    throw exception;
                }
                DiagLog.add(this, "attempt " + attempt + " failed after " + attemptMillis
                        + "ms task=" + taskId + " " + exception.getClass().getSimpleName()
                        + " -> retry in " + (RETRY_DELAY_MILLIS / 1000) + "s");
                if (!sleep(RETRY_DELAY_MILLIS)) {
                    DiagLog.add(this, "retry abandoned task=" + taskId + " (worker interrupted)");
                    throw exception;
                }
            }
        }
    }

    private static String formatFileMetadata(List<TaskFileAttachment> files) {
        if (files == null || files.isEmpty()) return null;
        StringBuilder text = new StringBuilder();
        for (TaskFileAttachment file : files) {
            String name = file.displayName == null ? "附件" : file.displayName
                    .replace('\n', ' ').replace('\r', ' ').replace('\t', ' ')
                    .replace('"', '\'');
            text.append("- \"").append(name).append("\" | ")
                    .append(file.mimeType).append(" | ").append(file.sizeBytes)
                    .append(" bytes\n");
        }
        return text.toString().trim();
    }

    /** True for failures worth another attempt; a server that answered 4xx will not change. */
    private static boolean isTransient(IOException exception) {
        if (exception instanceof ChatCompletionClient.ResponseException) return false;
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
        // Channel importance is frozen when the channel is created, so the old DEFAULT-importance
        // channel cannot be promoted in place; it is dropped and replaced by a HIGH one.
        manager.deleteNotificationChannel(LEGACY_CHANNEL_ID);
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "处理结果",
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("解析完成的提醒，以悬浮通知弹出");
        manager.createNotificationChannel(channel);
        Intent intent = new Intent(this, TaskDetailActivity.class).putExtra("task_id", taskId);
        PendingIntent pending = PendingIntent.getActivity(this, (int) taskId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("拾时 · Chrona")
                .setContentText(text)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .build();
        manager.notify((int) taskId, notification);
    }
}
