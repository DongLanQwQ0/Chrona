package com.donglan.chrona.processing;

import android.content.Context;
import com.donglan.chrona.AndroidSync;
import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.calendar.LinkedCalendarEvents;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import java.security.GeneralSecurityException;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.List;

/** One reparse contract for Activity and authenticated LAN actions. */
public final class CaptureReparser {
    private CaptureReparser() { }

    public static void enqueue(Context context, long taskId, boolean remote, long expectedRevision)
            throws GeneralSecurityException {
        reparse(context, taskId, expectedRevision, () -> {
            if (remote) ProcessingJobService.enqueueRemote(context, taskId);
            else ProcessingJobService.enqueue(context, taskId);
        });
    }

    // Scheduling is injected only for device checks; production always uses the Android scheduler.
    static void reparse(Context context, long taskId, long expectedRevision, Runnable schedule)
            throws GeneralSecurityException {
        synchronized (AndroidSync.LOCK) {
            try (TaskStore store = new TaskStore(context)) {
                CalendarStore calendar = new CalendarStore(context);
                boolean cleaning = false;
                try (LinkedCalendarEvents writes = new LinkedCalendarEvents(store, calendar)) {
                    // Read and replace the same candidate snapshot; another local writer cannot
                    // publish a newly linked event between our lookup and the replacement.
                    store.getWritableDatabase().beginTransaction();
                    try {
                        if (expectedRevision >= 0 && expectedRevision != store.dataRevision())
                            throw new ConcurrentModificationException();
                        TaskRecord task = store.getTask(taskId);
                        if (task == null) throw new IllegalStateException("任务不存在或已删除");
                        if (store.isHomeworkCapture(taskId))
                            throw new IllegalStateException("作业收件由同步管理，无需重新解析");
                        if (TaskRecord.QUEUED.equals(task.status) || TaskRecord.PROCESSING.equals(task.status))
                            throw new ConcurrentModificationException("这条内容正在解析");
                        if (task.rawText.trim().isEmpty() && store.getImagePaths(taskId).isEmpty()
                                && store.getFileAttachments(taskId).isEmpty())
                            throw new IllegalStateException("没有可解析的内容，请补充文字或附件");
                        if (new AiSettingsStore(context).load() == null)
                            throw new IllegalStateException("请先在设置中配置 AI 服务");
                        List<EventCandidate> previous = store.getCandidates(taskId);
                        if (previous.stream().anyMatch(item -> item.calendarEventId != null)
                                && (!calendar.hasReadPermission() || !calendar.hasWritePermission()))
                            throw new SecurityException("请先授予日历权限，再重新解析");
                        cleaning = true;
                        // Shared and archived references retain their events. Missing events are
                        // already clean; a provider failure stops before local results are cleared.
                        writes.delete(previous);
                        store.replaceCandidates(taskId, Collections.emptyList());
                        store.updateStatus(taskId, TaskRecord.QUEUED, null);
                        store.getWritableDatabase().setTransactionSuccessful();
                    } finally { store.getWritableDatabase().endTransaction(); }
                    writes.committed();
                } catch (RuntimeException failure) {
                    if (!cleaning) throw failure;
                    throw new IllegalStateException("旧日程清理未完成，尚未开始解析，请重试；部分系统日程可能已移除", failure);
                }
                new StreamingOutputStore(context).delete(taskId);
                // A worker must only see committed QUEUED data, never the previous READY result.
                try { schedule.run(); }
                catch (RuntimeException failure) {
                    String message = "旧日程已清理，解析未能排队，请重试";
                    store.updateStatus(taskId, TaskRecord.FAILED, message);
                    throw new IllegalStateException(message, failure);
                }
            }
        }
    }
}
