package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.processing.ProcessingJobService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Review parsed events before committing them to the device calendar. */
public final class TaskDetailActivity extends Activity {
    private static final int CALENDAR_PERMISSION_REQUEST = 11;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private long taskId;
    private LinearLayout content;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        taskId = getIntent().getLongExtra("task_id", -1);
        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(30), dp(20), dp(24));
        scroll.addView(content);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        render();
    }

    private void render() {
        content.removeAllViews();
        try (TaskStore store = new TaskStore(this)) {
            TaskRecord task = store.getTask(taskId);
            if (task == null) {
                label("任务不存在", 20);
                return;
            }
            label("任务详情", 24);
            label(task.rawText, 16);
            label("状态：" + task.status + (task.errorMessage == null ? "" : "\n" + task.errorMessage), 14);
            if (task.totalTokens != null) label("本次解析 token：" + task.totalTokens, 14);
            List<EventCandidate> candidates = store.getCandidates(taskId);
            boolean hasPublishedEvent = false;
            for (EventCandidate candidate : candidates) {
                if (candidate.calendarEventId != null) hasPublishedEvent = true;
            }
            Button retry = button("重新解析", () -> retry());
            retry.setEnabled(!TaskRecord.PROCESSING.equals(task.status) && !hasPublishedEvent);
            if (candidates.isEmpty()) {
                label("暂无日程草稿。可以重新解析，也可以手动添加。", 14);
                button("手动添加日程", () -> addManualCandidate());
            }
            for (int i = 0; i < candidates.size(); i++) addCandidateEditor(candidates.get(i), i + 1);
        } catch (Exception exception) {
            showError(exception);
        }
    }

    private void addCandidateEditor(EventCandidate candidate, int number) {
        label("日程 " + number + (candidate.calendarEventId == null ? " · 待写入" : " · 已写入日历"), 19);
        EditText title = field("标题", candidate.title);
        EditText start = field("开始：yyyy-MM-dd HH:mm", format(candidate.startAtMillis));
        EditText end = field("结束：yyyy-MM-dd HH:mm", format(candidate.endAtMillis));
        EditText location = field("地点（可选）", candidate.location);
        EditText description = field("备注（可选）", candidate.description);
        EditText reminder = field("提前提醒分钟数（留空表示不提醒）",
                candidate.reminderMinutesBefore == null ? "" : candidate.reminderMinutesBefore.toString());
        Button[] publish = new Button[1];
        publish[0] = button(candidate.calendarEventId == null ? "确认并写入系统日历" : "保存并更新日历", () -> {
            if (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.READ_CALENDAR,
                        Manifest.permission.WRITE_CALENDAR}, CALENDAR_PERMISSION_REQUEST);
                return;
            }
            try {
                String titleText = title.getText().toString().trim();
                if (titleText.isEmpty()) throw new IllegalArgumentException("请填写标题");
                ZoneId zone = ZoneId.systemDefault();
                Long startAt = parse(start.getText().toString(), zone);
                Long endAt = parse(end.getText().toString(), zone);
                if (startAt == null || endAt == null || endAt <= startAt) {
                    throw new IllegalArgumentException("请填写有效的开始和结束时间，结束须晚于开始");
                }
                String reminderText = reminder.getText().toString().trim();
                Integer minutes = reminderText.isEmpty() ? null : Integer.parseInt(reminderText);
                if (minutes != null && minutes < 0) throw new IllegalArgumentException("提醒分钟数不能为负数");
                EventCandidate edited = new EventCandidate(candidate.id, taskId, titleText,
                        startAt, endAt, zone.getId(), location.getText().toString().trim(),
                        description.getText().toString().trim(), minutes, false,
                        candidate.calendarEventId);
                publish[0].setEnabled(false);
                new Thread(() -> publish(edited), "chrona-calendar-write").start();
            } catch (DateTimeParseException | NumberFormatException exception) {
                Toast.makeText(this, "请按 yyyy-MM-dd HH:mm 填写时间，提醒填写数字", Toast.LENGTH_LONG).show();
            } catch (Exception exception) {
                showError(exception);
            }
        });
        View line = new View(this);
        line.setBackgroundColor(0xFFDDDDDD);
        LinearLayout.LayoutParams divider = new LinearLayout.LayoutParams(-1, dp(1));
        divider.setMargins(0, dp(20), 0, dp(20));
        content.addView(line, divider);
    }

    private void publish(EventCandidate edited) {
        try (TaskStore store = new TaskStore(this)) {
            CalendarStore calendar = new CalendarStore(this);
            List<Integer> reminders = edited.reminderMinutesBefore == null
                    ? Collections.emptyList() : Collections.singletonList(edited.reminderMinutesBefore);
            CalendarStore.EventInput input = new CalendarStore.EventInput(edited.title,
                    edited.startAtMillis, edited.endAtMillis, edited.timeZoneId, false,
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
            boolean allPublished = true;
            for (EventCandidate item : store.getCandidates(taskId)) {
                if (item.calendarEventId == null) allPublished = false;
            }
            store.updateStatus(taskId, allPublished ? TaskRecord.READY : TaskRecord.NEEDS_REVIEW, null);
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
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        if (value != null) edit.setText(value);
        content.addView(edit);
        return edit;
    }

    private void label(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setPadding(0, dp(5), 0, dp(5));
        content.addView(text);
    }

    private Button button(String value, Runnable action) {
        Button button = new Button(this);
        button.setText(value);
        button.setOnClickListener(view -> action.run());
        content.addView(button);
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

    private void showError(Exception exception) {
        Toast.makeText(this, "操作失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
