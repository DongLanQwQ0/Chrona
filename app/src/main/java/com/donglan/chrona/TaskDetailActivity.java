package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.calendar.AllDayDates;
import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.debug.DiagLog;
import com.donglan.chrona.image.ImageStore;
import com.donglan.chrona.processing.ProcessingJobService;
import com.donglan.chrona.web.LinkFetcher;

import java.text.DateFormat;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Review parsed events before committing them to the device calendar. */
public final class TaskDetailActivity extends Activity {
    private static final int CALENDAR_PERMISSION_REQUEST = 11;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private long taskId;
    private LinearLayout content;
    private boolean deleting;
    private String observedStatus;
    private int observedCandidates = -1;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshPoll = this::pollForResult;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        taskId = getIntent().getLongExtra("task_id", -1);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(30), dp(20), dp(24));
        UiStyle.page(this, content);
        scroll.addView(content);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
        refreshHandler.postDelayed(refreshPoll, 1500L);
    }

    @Override
    protected void onPause() {
        refreshHandler.removeCallbacks(refreshPoll);
        super.onPause();
    }

    private void pollForResult() {
        if (deleting) return;
        try (TaskStore store = new TaskStore(this)) {
            TaskRecord task = store.getTask(taskId);
            int count = store.getCandidates(taskId).size();
            if (task != null && (!task.status.equals(observedStatus)
                    || count != observedCandidates)) render();
        } catch (Exception exception) {
            // A transient database failure should not close a draft being edited.
            android.util.Log.w("Chrona", "Could not refresh task detail", exception);
        }
        refreshHandler.postDelayed(refreshPoll, 1500L);
    }

    private void render() {
        if (deleting) return;
        content.removeAllViews();
        try (TaskStore store = new TaskStore(this)) {
            TaskRecord task = store.getTask(taskId);
            if (task == null) {
                label("任务不存在", 20);
                return;
            }
            label("任务详情", 24);
            label(task.rawText.isEmpty() ? "（仅图片，无文字）" : task.rawText, 16);
            label("状态：" + task.status + (task.errorMessage == null ? "" : "\n" + task.errorMessage), 14);
            if (task.totalTokens != null) label("本次解析 token：" + task.totalTokens, 14);
            if (task.cachedTokens != null && task.promptTokens != null) {
                label("其中缓存命中：" + task.cachedTokens + " / " + task.promptTokens
                        + " 输入 token", 14);
            }
            if (task.imagePath != null) {
                ImageView preview = new ImageView(this);
                preview.setAdjustViewBounds(true);
                preview.setMaxHeight(dp(220));
                preview.setImageURI(Uri.fromFile(new ImageStore(this).fileFor(task.imagePath)));
                content.addView(preview);
                button("移除图片", () -> removeImage());
            }
            List<String> links = LinkFetcher.extractUrls(task.rawText);
            if (!links.isEmpty()) {
                label(linkState(task, links), 14);
                if (task.linkText != null) label(excerpt(task.linkText), 13);
            }
            List<EventCandidate> candidates = store.getCandidates(taskId);
            observedStatus = task.status;
            observedCandidates = candidates.size();
            int publishedCount = countPublished(candidates);
            // Re-parsing re-reads the links, so the same entry doubles as the manual re-check.
            Button retry = button(links.isEmpty() ? "重新解析" : "重新联网检查并解析", () -> retry());
            retry.setEnabled(!TaskRecord.PROCESSING.equals(task.status) && publishedCount == 0);
            if (candidates.isEmpty()) {
                label("暂无日程草稿。可以重新解析，也可以手动添加。", 14);
                button("手动添加日程", () -> addManualCandidate());
            }
            for (int i = 0; i < candidates.size(); i++) addCandidateEditor(candidates.get(i), i + 1);
            button("删除任务及关联日程", () -> confirmDelete(publishedCount));
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void confirmDelete(int publishedCount) {
        if (publishedCount > 0 && !requestCalendarPermission()) return;
        String message = publishedCount == 0
                ? "将删除这条输入和所有日程草稿。此操作无法撤销。"
                : "将删除这条输入、所有日程草稿及已写入系统日历的 "
                        + publishedCount + " 条日程。此操作无法撤销。";
        new AlertDialog.Builder(this)
                .setTitle("确认删除任务？")
                .setMessage(message)
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> {
                    deleting = true;
                    content.removeAllViews();
                    label("正在删除任务与关联日程…", 18);
                    new Thread(this::deleteTask, "chrona-task-delete").start();
                })
                .show();
    }

    private void deleteTask() {
        try (TaskStore store = new TaskStore(this)) {
            ProcessingJobService.cancel(this, taskId);
            CalendarStore calendar = new CalendarStore(this);
            for (EventCandidate candidate : store.getCandidates(taskId)) {
                if (candidate.calendarEventId != null) {
                    // A missing provider row is already deleted; provider errors stop the removal.
                    calendar.deleteEvent(candidate.calendarEventId);
                }
            }
            String imagePath = store.clearImage(taskId);
            if (!store.deleteTask(taskId)) throw new IllegalStateException("任务不存在或已删除");
            if (imagePath != null) new ImageStore(this).delete(imagePath);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.cancel((int) taskId);
            DiagLog.add(this, "task deleted id=" + taskId + " image=" + (imagePath != null));
            runOnUiThread(() -> {
                Toast.makeText(this, "任务已删除", Toast.LENGTH_SHORT).show();
                finish();
            });
        } catch (Exception exception) {
            runOnUiThread(() -> {
                deleting = false;
                render();
                showError(exception);
            });
        }
    }

    /** Drops the attachment so a text-only retry stays possible when images are rejected. */
    private void removeImage() {
        try (TaskStore store = new TaskStore(this)) {
            String imagePath = store.clearImage(taskId);
            if (imagePath != null) new ImageStore(this).delete(imagePath);
            render();
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void confirmRemoveCandidate(EventCandidate candidate, int number) {
        boolean published = candidate.calendarEventId != null;
        if (published && !requestCalendarPermission()) return;
        new AlertDialog.Builder(this)
                .setTitle("删除日程 " + number + "？")
                .setMessage(published
                        ? "将从系统日历中删除这条日程，并移除对应草稿。此操作无法撤销。"
                        : "将移除这条日程草稿。此操作无法撤销。")
                .setNegativeButton("取消", null)
                .setPositiveButton("删除", (dialog, which) -> removeCandidate(candidate, published))
                .show();
    }

    private void removeCandidate(EventCandidate candidate, boolean published) {
        deleting = true;
        content.removeAllViews();
        label("正在删除日程…", 18);
        new Thread(() -> {
            try (TaskStore store = new TaskStore(this)) {
                if (published) {
                    // A missing provider row is already deleted; provider errors stop the removal.
                    new CalendarStore(this).deleteEvent(candidate.calendarEventId);
                }
                if (!store.deleteCandidate(candidate.id, taskId)) {
                    throw new IllegalStateException("日程草稿不存在或已被删除");
                }
                store.updateStatus(taskId, reviewStatus(store.getCandidates(taskId)), null);
                runOnUiThread(() -> {
                    deleting = false;
                    render();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    deleting = false;
                    render();
                    showError(exception);
                });
            }
        }, "chrona-candidate-delete").start();
    }

    private void addCandidateEditor(EventCandidate candidate, int number) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(14), dp(14), dp(14), dp(14));
        UiStyle.card(card);
        UiStyle.addSpaced(content, card, 12, 6);
        UiStyle.enter(card, number - 1);
        label(card, "日程 " + number + (candidate.calendarEventId == null ? " · 待写入" : " · 已写入日历"), 19);
        EditText title = field(card, "标题", candidate.title);
        label(card, "类型", 14);
        Spinner category = new Spinner(this);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, EventCategory.LABELS);
        category.setAdapter(categoryAdapter);
        category.setSelection(EventCategory.indexOf(candidate.category));
        card.addView(category);
        CheckBox allDay = new CheckBox(this);
        allDay.setText("全天日程");
        allDay.setChecked(candidate.allDay);
        card.addView(allDay);
        EditText start = field(card, candidate.allDay ? "开始日期：yyyy-MM-dd" : "开始：yyyy-MM-dd HH:mm",
                candidate.allDay ? dayStart(candidate.startAtMillis) : format(candidate.startAtMillis));
        EditText end = field(card, candidate.allDay ? "结束日期（含当天）：yyyy-MM-dd" : "结束：yyyy-MM-dd HH:mm",
                candidate.allDay ? dayEnd(candidate.endAtMillis) : format(candidate.endAtMillis));
        allDay.setOnCheckedChangeListener((view, checked) -> {
            start.setHint(checked ? "开始日期：yyyy-MM-dd" : "开始：yyyy-MM-dd HH:mm");
            end.setHint(checked ? "结束日期（含当天）：yyyy-MM-dd" : "结束：yyyy-MM-dd HH:mm");
            if (checked) {
                start.setText(datePart(start.getText().toString()));
                end.setText(datePart(end.getText().toString()));
            } else {
                start.setText(withTime(start.getText().toString(), "09:00"));
                end.setText(withTime(end.getText().toString(), "10:00"));
            }
        });
        EditText location = field(card, "地点（可选）", candidate.location);
        EditText description = field(card, "备注（可选）", candidate.description);
        EditText reminder = field(card, "提前提醒分钟数（留空表示不提醒）",
                candidate.reminderMinutesBefore == null ? "" : candidate.reminderMinutesBefore.toString());
        Button[] publish = new Button[1];
        publish[0] = button(card, candidate.calendarEventId == null ? "确认并写入系统日历" : "保存并更新日历", () -> {
            if (!requestCalendarPermission()) return;
            try {
                String titleText = title.getText().toString().trim();
                if (titleText.isEmpty()) throw new IllegalArgumentException("请填写标题");
                ZoneId zone = ZoneId.systemDefault();
                Long startAt = allDay.isChecked()
                        ? parseDayStart(start.getText().toString())
                        : parse(start.getText().toString(), zone);
                Long endAt = allDay.isChecked()
                        ? parseDayEnd(end.getText().toString())
                        : parse(end.getText().toString(), zone);
                if (startAt == null || endAt == null || endAt <= startAt) {
                    throw new IllegalArgumentException("请填写有效的开始和结束日期，结束不能早于开始");
                }
                String reminderText = reminder.getText().toString().trim();
                Integer minutes = reminderText.isEmpty() ? null : Integer.parseInt(reminderText);
                if (minutes != null && minutes < 0) throw new IllegalArgumentException("提醒分钟数不能为负数");
                EventCandidate edited = new EventCandidate(candidate.id, taskId, titleText,
                        startAt, endAt, allDay.isChecked() ? "UTC" : zone.getId(),
                        location.getText().toString().trim(),
                        description.getText().toString().trim(), minutes, false,
                        candidate.calendarEventId,
                        EventCategory.VALUES[category.getSelectedItemPosition()], allDay.isChecked());
                publish[0].setEnabled(false);
                new Thread(() -> publish(edited), "chrona-calendar-write").start();
            } catch (DateTimeParseException | NumberFormatException exception) {
                Toast.makeText(this, "请按提示格式填写日期或时间，提醒填写数字", Toast.LENGTH_LONG).show();
            } catch (Exception exception) {
                showError(exception);
            }
        });
        button(card, "删除此日程", () -> confirmRemoveCandidate(candidate, number));
    }

    private void publish(EventCandidate edited) {
        try (TaskStore store = new TaskStore(this)) {
            CalendarStore calendar = new CalendarStore(this);
            List<Integer> reminders = edited.reminderMinutesBefore == null
                    ? Collections.emptyList() : Collections.singletonList(edited.reminderMinutesBefore);
            CalendarStore.EventInput input = new CalendarStore.EventInput(edited.title,
                    edited.startAtMillis, edited.endAtMillis, edited.timeZoneId, edited.allDay,
                    edited.description, edited.location, reminders);
            if (edited.calendarEventId != null) {
                if (!calendar.updateEvent(edited.calendarEventId, input)) {
                    throw new IllegalStateException("日历中的原日程已被删除，请重新解析或手动添加");
                }
            } else {
                long eventId = calendar.insertEvent(input);
                if (!store.setCalendarEventId(edited.id, taskId, eventId)) {
                    calendar.deleteEvent(eventId);
                    throw new IllegalStateException("无法保存日程关联");
                }
            }
            if (!store.updateCandidate(edited)) throw new IllegalStateException("无法保存日程草稿");
            store.updateStatus(taskId, reviewStatus(store.getCandidates(taskId)), null);
            DiagLog.add(this, "calendar written task=" + taskId + " candidate=" + edited.id
                    + " event=" + edited.calendarEventId);
            runOnUiThread(() -> {
                Toast.makeText(this, "已写入系统日历", Toast.LENGTH_SHORT).show();
                render();
            });
        } catch (Exception exception) {
            runOnUiThread(() -> {
                showError(exception);
                render();
            });
        }
    }

    private void retry() {
        try (TaskStore store = new TaskStore(this)) {
            TaskRecord task = store.getTask(taskId);
            if (task == null) throw new IllegalStateException("任务不存在或已删除");
            if (task.rawText.trim().isEmpty() && task.imagePath == null) {
                Toast.makeText(this, "没有可解析的内容，请补充文字或图片", Toast.LENGTH_LONG).show();
                return;
            }
            for (EventCandidate candidate : store.getCandidates(taskId)) {
                if (candidate.calendarEventId != null) {
                    throw new IllegalStateException("已有日程写入日历，请直接编辑已有日程");
                }
            }
            if (new AiSettingsStore(this).load() == null) {
                Toast.makeText(this, "请先在首页配置 AI 服务", Toast.LENGTH_LONG).show();
                return;
            }
            store.replaceCandidates(taskId, Collections.emptyList());
            store.updateStatus(taskId, TaskRecord.QUEUED, null);
            ProcessingJobService.enqueue(this, taskId);
            DiagLog.add(this, "retry task=" + taskId);
            Toast.makeText(this, "已加入解析队列", Toast.LENGTH_SHORT).show();
            render();
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void addManualCandidate() {
        ArrayList<EventCandidate> entries = new ArrayList<>();
        entries.add(new EventCandidate(0, taskId, "新日程", null, null,
                ZoneId.systemDefault().getId(), "", "", null, true));
        try (TaskStore store = new TaskStore(this)) {
            store.replaceCandidates(taskId, entries);
            store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW, null);
            render();
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private EditText field(String hint, String value) {
        return field(content, hint, value);
    }

    private EditText field(LinearLayout parent, String hint, String value) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        if (value != null) edit.setText(value);
        UiStyle.input(edit);
        UiStyle.addSpaced(parent, edit, 5, 5);
        return edit;
    }

    private void label(String value, int size) {
        label(content, value, size);
    }

    private void label(LinearLayout parent, String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setPadding(0, dp(5), 0, dp(5));
        if (size >= 19) UiStyle.title(text);
        else UiStyle.muted(text);
        parent.addView(text);
    }

    private Button button(String value, Runnable action) {
        return button(content, value, action);
    }

    private Button button(LinearLayout parent, String value, Runnable action) {
        Button button = new Button(this);
        button.setText(value);
        button.setOnClickListener(view -> action.run());
        UiStyle.button(button, value.startsWith("确认并") || value.startsWith("保存并"));
        UiStyle.addSpaced(parent, button, 5, 5);
        return button;
    }

    private static String format(Long millis) {
        return millis == null ? "" : DATE_TIME.format(
                LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(millis), ZoneId.systemDefault()));
    }

    private static Long parse(String value, ZoneId zone) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : LocalDateTime.parse(trimmed, DATE_TIME)
                .atZone(zone).toInstant().toEpochMilli();
    }

    private static Long parseDayStart(String value) {
        return value.trim().isEmpty() ? null : AllDayDates.utcStart(value);
    }

    private static Long parseDayEnd(String value) {
        return value.trim().isEmpty() ? null : AllDayDates.utcExclusiveEnd(value);
    }

    private static String dayStart(Long millis) {
        return millis == null ? "" : AllDayDates.displayStart(millis);
    }

    private static String dayEnd(Long millis) {
        return millis == null ? "" : AllDayDates.displayEnd(millis);
    }

    private static String datePart(String value) {
        return value.length() >= 10 ? value.substring(0, 10) : value;
    }

    private static String withTime(String value, String time) {
        return value.length() == 10 ? value + " " + time : value;
    }

    private void showError(Exception exception) {
        Toast.makeText(this, "操作失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
    }

    /** Returns true when both calendar permissions are already granted. */
    private boolean hasCalendarPermission() {
        return checkSelfPermission(Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(Manifest.permission.WRITE_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Requests the calendar permissions when missing; false means the caller must wait for a retry. */
    private boolean requestCalendarPermission() {
        if (hasCalendarPermission()) return true;
        requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,
                Manifest.permission.WRITE_CALENDAR}, CALENDAR_PERMISSION_REQUEST);
        Toast.makeText(this, "授权日历权限后请再次点击", Toast.LENGTH_SHORT).show();
        return false;
    }

    private static int countPublished(List<EventCandidate> candidates) {
        int count = 0;
        for (EventCandidate candidate : candidates) {
            if (candidate.calendarEventId != null) count++;
        }
        return count;
    }

    /** Describes what the last on-demand retrieval managed to read from the input's links. */
    private static String linkState(TaskRecord task, List<String> links) {
        String found = "联网检索：输入里有 " + links.size() + " 个链接。";
        if (task.linkFetchedAtMillis == null) {
            return found + "解析时会自动抓取正文。";
        }
        String when = DateFormat.getDateTimeInstance().format(new Date(task.linkFetchedAtMillis));
        if (task.linkText == null) {
            return found + when + " 抓取失败或没有正文，已按原文解析。";
        }
        return found + when + " 已抓取 " + task.linkText.length() + " 字。";
    }

    private static String excerpt(String text) {
        String flat = text.replace('\n', ' ').trim();
        return flat.length() <= 220 ? flat : flat.substring(0, 220) + "…";
    }

    /** The input is ready only when at least one draft exists and every draft is in the calendar. */
    private static String reviewStatus(List<EventCandidate> candidates) {
        if (candidates.isEmpty() || countPublished(candidates) < candidates.size()) {
            return TaskRecord.NEEDS_REVIEW;
        }
        return TaskRecord.READY;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
