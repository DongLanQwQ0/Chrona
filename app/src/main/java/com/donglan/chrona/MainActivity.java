package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.processing.ProcessingJobService;

import java.text.DateFormat;
import java.util.Date;

/** Quick inbox for text and Android's share sheet. */
public final class MainActivity extends Activity {
    private EditText input;
    private LinearLayout taskList;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(28), dp(20), dp(12));
        root.setFitsSystemWindows(true);

        TextView title = new TextView(this);
        title.setText("拾时 · Chrona");
        title.setTextSize(26);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("先把事情记下来，后台解析后再确认日程。");
        hint.setPadding(0, dp(6), 0, dp(12));
        root.addView(hint);

        input = new EditText(this);
        input.setHint("粘贴消息、链接或描述一件事…");
        input.setMinLines(4);
        input.setGravity(android.view.Gravity.TOP);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        root.addView(input, new LinearLayout.LayoutParams(-1, -2));

        Button save = new Button(this);
        save.setText("保存并解析");
        save.setOnClickListener(view -> submit());
        root.addView(save);

        Button settings = new Button(this);
        settings.setText("AI 服务设置");
        settings.setOnClickListener(view -> startActivity(new Intent(this, SettingsActivity.class)));
        root.addView(settings);

        Button refresh = new Button(this);
        refresh.setText("刷新收件箱");
        refresh.setOnClickListener(view -> refreshTasks());
        root.addView(refresh);

        ScrollView scroll = new ScrollView(this);
        taskList = new LinearLayout(this);
        taskList.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(taskList);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        receiveSharedText(getIntent());
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        receiveSharedText(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshTasks();
    }

    private void receiveSharedText(Intent intent) {
        if (intent != null && Intent.ACTION_SEND.equals(intent.getAction())
                && "text/plain".equals(intent.getType())) {
            String shared = intent.getStringExtra(Intent.EXTRA_TEXT);
            if (shared != null) input.setText(shared);
        }
    }

    private void submit() {
        String text = input.getText().toString().trim();
        if (text.isEmpty()) {
            input.setError("请输入内容");
            return;
        }
        long taskId;
        try (TaskStore store = new TaskStore(this)) {
            boolean configured = new AiSettingsStore(this).load() != null;
            taskId = store.insertTask(text,
                    Intent.ACTION_SEND.equals(getIntent().getAction()) ? "share" : "app",
                    System.currentTimeMillis());
            if (!configured) {
                store.updateStatus(taskId, TaskRecord.NEEDS_REVIEW, "请先配置 AI 服务");
            } else {
                ProcessingJobService.enqueue(this, taskId);
            }
        } catch (Exception exception) {
            Toast.makeText(this, "保存失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        input.setText("");
        setIntent(new Intent(this, MainActivity.class));
        Toast.makeText(this, "已存入收件箱", Toast.LENGTH_SHORT).show();
        refreshTasks();
    }

    private void refreshTasks() {
        taskList.removeAllViews();
        try (TaskStore store = new TaskStore(this)) {
            for (TaskRecord task : store.listTasks()) {
                TextView row = new TextView(this);
                String preview = task.rawText.replace('\n', ' ');
                if (preview.length() > 90) preview = preview.substring(0, 90) + "…";
                row.setText(preview + "\n" + statusText(task.status) + " · "
                        + DateFormat.getDateTimeInstance().format(new Date(task.createdAtMillis)));
                row.setTextSize(16);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                row.setOnClickListener(view -> startActivity(new Intent(this, TaskDetailActivity.class)
                        .putExtra("task_id", task.id)));
                taskList.addView(row, new LinearLayout.LayoutParams(-1, -2));
                View divider = new View(this);
                divider.setBackgroundColor(0xFFDDDDDD);
                taskList.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            }
        }
    }

    private String statusText(String status) {
        if (TaskRecord.QUEUED.equals(status)) return "排队中";
        if (TaskRecord.PROCESSING.equals(status)) return "解析中";
        if (TaskRecord.NEEDS_REVIEW.equals(status)) return "待确认";
        if (TaskRecord.READY.equals(status)) return "已写入日历";
        return "处理失败";
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
