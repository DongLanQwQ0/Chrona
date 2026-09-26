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
import com.donglan.chrona.image.ImageStore;

import java.io.IOException;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/** Durable, network-constrained parsing work for saved inputs. */
public final class ProcessingJobService extends JobService {
    private static final String EXTRA_TASK_ID = "task_id";
    private static final int JOB_ID_BASE = 10_000;
    private static final String CHANNEL_ID = "chrona_processing";
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
            throw new IllegalStateException("Could not schedule parsing work");
        }
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
        AiSettings settings = null;
        boolean sentImage = false;
        try (TaskStore store = new TaskStore(this)) {
            TaskRecord task = store.getTask(taskId);
            if (task == null) return;
            settings = new AiSettingsStore(this).load();
            if (settings == null) {
                store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW, "请先配置 AI 服务");
                notifyResult(taskId, "请配置 AI 服务后重试");
                return;
            }
            byte[] image = null;
            if (task.imagePath != null) {
                try {
                    image = new ImageStore(this).read(task.imagePath);
                } catch (IOException exception) {
                    store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW, "图片不可用，请移除图片后重试");
                    notifyResult(taskId, "图片不可用，点按查看详情");
                    return;
                }
            }
            sentImage = image != null;
            store.updateStatus(taskId, TaskRecord.PROCESSING, null);
            ParseResult result = new ChatCompletionClient(settings).parse(taskId, task.rawText,
                    image, System.currentTimeMillis(), TimeZone.getDefault().getID());
            if (Thread.currentThread().isInterrupted()) return;
            store.replaceCandidates(taskId, result.candidates);
            store.updateUsage(taskId, result.promptTokens, result.completionTokens,
                    result.totalTokens);
            store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW,
                    result.candidates.isEmpty() ? "未识别到日程，请检查原文或重试" : null);
            notifyResult(taskId, result.candidates.isEmpty() ? "未识别到日程" : "日程草稿待确认");
        } catch (ChatCompletionClient.RequestException exception) {
            if (!Thread.currentThread().isInterrupted()) {
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
                recordFailure(taskId, message(exception));
            }
        } finally {
            workers.remove(params.getJobId());
            if (!Thread.currentThread().isInterrupted()) jobFinished(params, false);
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
