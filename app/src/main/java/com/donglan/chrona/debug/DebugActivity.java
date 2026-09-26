package com.donglan.chrona.debug;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.util.TypedValue;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ThemeStore;
import com.donglan.chrona.UiStyle;
import com.donglan.chrona.GlassBackdropView;
import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.image.ImageStore;

import java.util.List;
import java.util.TimeZone;

/**
 * Read-only summary of what the app currently sees, so a failure on the phone can be reported
 * without a connected PC. Reachable only from a debuggable build.
 */
public final class DebugActivity extends Activity {
    private TextView output;

    @Override
    protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        page = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setFitsSystemWindows(false);
        page.addView(root);
        UiStyle.back(this, root);

        TextView title = new TextView(this);
        title.setText("诊断信息");
        title.setTextSize(28);
        UiStyle.title(title);
        root.addView(title);
        TextView description = new TextView(this);
        description.setText("查看设备状态与处理日志。复制后可用于排查问题。");
        description.setTextSize(14);
        UiStyle.muted(description);
        UiStyle.addSpaced(root, description, 8, 18);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        addAction(actions, action("刷新", view -> refresh()));
        addAction(actions, action("复制全部", view -> copyAll()));
        UiStyle.addSpaced(root, actions, 0, 8);
        Button clear = action("清空日志", view -> clearLog());
        UiStyle.addSpaced(root, clear, 0, 16);

        LinearLayout reportCard = new LinearLayout(this);
        reportCard.setOrientation(LinearLayout.VERTICAL);
        reportCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        UiStyle.glass(reportCard);
        TextView reportTitle = new TextView(this);
        reportTitle.setText("运行报告");
        reportTitle.setTextSize(18);
        UiStyle.title(reportTitle);
        UiStyle.addSpaced(reportCard, reportTitle, 0, 14);
        output = new TextView(this);
        output.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        output.setTextIsSelectable(true);
        output.setTypeface(android.graphics.Typeface.MONOSPACE);
        UiStyle.muted(output);
        reportCard.addView(output);
        root.addView(reportCard);
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.applyInsets(stage, page);
        setContentView(stage);
        if (state != null) {
            int scrollY = state.getInt("scroll_y");
            page.post(() -> page.scrollTo(0, scrollY));
        }
        refresh();
    }

    private ScrollView page;

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("scroll_y", page.getScrollY());
    }

    private void addAction(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1);
        params.setMargins(0, 0, dp(8), 0);
        row.addView(button, params);
    }

    private Button action(String text, android.view.View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        UiStyle.button(button, false);
        button.setOnClickListener(listener);
        return button;
    }

    private void refresh() {
        output.setText(report());
    }

    private void copyAll() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) {
            Toast.makeText(this, "剪贴板不可用", Toast.LENGTH_SHORT).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Chrona 诊断", output.getText()));
        Toast.makeText(this, "已复制，可直接粘贴发送", Toast.LENGTH_SHORT).show();
    }

    private void clearLog() {
        DiagLog.clear(this);
        DiagLog.add(this, "log cleared");
        refresh();
        Toast.makeText(this, "日志已清空", Toast.LENGTH_SHORT).show();
    }

    /** Everything below is read-only: no key material is ever printed. */
    private String report() {
        StringBuilder text = new StringBuilder();
        appendEnvironment(text);
        appendServices(text);
        appendStorage(text);
        appendJobs(text);
        text.append("\n--- 事件日志（新→旧倒序请看文件末尾） ---\n");
        String log = DiagLog.read(this);
        text.append(log.isEmpty() ? "（暂无记录）" : log);
        return text.toString();
    }

    private void appendEnvironment(StringBuilder text) {
        text.append("应用: ").append(versionName()).append(" (versionCode ").append(versionCode())
                .append(") debuggable=").append(isDebuggable()).append('\n');
        text.append("设备: ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL)
                .append(" / Android ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n");
        text.append("时区: ").append(TimeZone.getDefault().getID()).append('\n');
        // A short uptime next to an input stuck in "parsing" means the system killed the process.
        text.append("进程已运行: ").append(elapsedText(
                SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime())).append('\n');
    }

    private static String elapsedText(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        if (seconds < 60) return seconds + " 秒";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " 分 " + (seconds % 60) + " 秒";
        return (minutes / 60) + " 小时 " + (minutes % 60) + " 分";
    }

    private void appendServices(StringBuilder text) {
        try {
            AiSettingsStore store = new AiSettingsStore(this);
            AiSettings settings = store.load();
            if (settings == null) {
                text.append("AI 服务: 未配置\n");
            } else {
                text.append("AI 服务: ").append(settings.baseUrl)
                        .append(" model=").append(settings.model)
                        .append(" key=已保存")
                        .append(" 图片入口=")
                        .append(store.isImageUnsupported(settings) ? "已停用（该模型拒过图片）" : "正常")
                        .append('\n');
            }
        } catch (Exception exception) {
            text.append("AI 服务: 读取失败 ").append(exception).append('\n');
        }
    }

    private void appendStorage(StringBuilder text) {
        try (TaskStore store = new TaskStore(this)) {
            text.append(store.describe());
        } catch (Exception exception) {
            text.append("数据库: 读取失败 ").append(exception).append('\n');
        }
        try {
            text.append("附件: ").append(new ImageStore(this).describe()).append('\n');
        } catch (Exception exception) {
            text.append("附件: 读取失败 ").append(exception).append('\n');
        }
        text.append("日志文件: ").append(DiagLog.sizeBytes(this) / 1024).append(" KB\n");
        text.append("内存中日志: ").append(DiagLog.recent().size()).append(" 行\n");
    }

    private void appendJobs(StringBuilder text) {
        JobScheduler scheduler = getSystemService(JobScheduler.class);
        List<JobInfo> pending = scheduler == null ? null : scheduler.getAllPendingJobs();
        text.append("待处理解析任务: ").append(pending == null ? "不可用" : pending.size()).append(" 个\n");
        if (pending != null) {
            for (JobInfo job : pending) {
                text.append("  jobId=").append(job.getId())
                        .append(" task=").append(job.getExtras().getLong("task_id", -1))
                        .append(" 用户发起=").append(userInitiated(job))
                        .append('\n');
            }
        }
    }

    /** User-initiated jobs get priority over background cleaners; only Android 14+ has them. */
    private static String userInitiated(JobInfo job) {
        if (Build.VERSION.SDK_INT < 34) return "不适用";
        return job.isUserInitiated() ? "是" : "否";
    }

    private boolean isDebuggable() {
        return (getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0;
    }

    private String versionName() {
        PackageInfo info = packageInfo();
        return info == null || info.versionName == null ? "?" : info.versionName;
    }

    private String versionCode() {
        PackageInfo info = packageInfo();
        if (info == null) return "?";
        if (Build.VERSION.SDK_INT >= 28) return Long.toString(info.getLongVersionCode());
        return Integer.toString(info.versionCode);
    }

    @SuppressWarnings("deprecation")
    private PackageInfo packageInfo() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0);
        } catch (PackageManager.NameNotFoundException exception) {
            return null;
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
