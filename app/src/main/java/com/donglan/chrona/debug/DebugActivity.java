package com.donglan.chrona.debug;

import android.app.Activity;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.donglan.chrona.Feedback;
import com.donglan.chrona.GlassBackdropView;
import com.donglan.chrona.ThemeStore;
import com.donglan.chrona.UiStyle;
import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ai.AiSettingsStore;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.image.ImageStore;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;

/**
 * Read-only summary of what the app currently sees, so a failure on the phone can be reported
 * without a connected PC. Reachable only from a debuggable build.
 *
 * <p>One collected model feeds both the on-screen cards and the copied text, so the two can never
 * disagree. The copied text keeps the flat shape earlier reports were pasted in.
 */
public final class DebugActivity extends Activity {
    /** Keeps the on-screen log readable; the copied report always carries the whole file. */
    private static final int VISIBLE_LOG_LINES = 160;

    private ScrollView page;
    private LinearLayout root;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        page = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(32));
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        root.setFitsSystemWindows(false);
        page.addView(root);
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.applyInsets(stage, page);
        setContentView(stage);
        if (state != null) {
            int scrollY = state.getInt("scroll_y");
            page.post(() -> page.scrollTo(0, scrollY));
        }
        buildShell();
        show(collect());
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("scroll_y", page.getScrollY());
    }

    private void buildShell() {
        UiStyle.back(this, root);
        TextView title = new TextView(this);
        title.setText("诊断信息");
        title.setTextSize(28);
        UiStyle.title(title);
        root.addView(title);
        TextView description = new TextView(this);
        description.setText("设备状态与处理日志。复制后可整段发出去排查问题。");
        description.setTextSize(14);
        UiStyle.muted(description);
        UiStyle.addSpaced(root, description, 8, 16);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        addAction(actions, action("刷新", view -> show(collect())));
        addAction(actions, action("复制全部", view -> copyAll()));
        UiStyle.addSpaced(root, actions, 0, 10);
        Button clear = action("清空日志", view -> clearLog());
        UiStyle.addSpaced(root, clear, 0, 18);
    }

    private void addAction(LinearLayout row, Button button) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1);
        params.setMargins(0, 0, dp(8), 0);
        row.addView(button, params);
    }

    private Button action(String text, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(text);
        UiStyle.button(button, false);
        button.setOnClickListener(listener);
        return button;
    }

    private void copyAll() {
        ClipboardManager clipboard = getSystemService(ClipboardManager.class);
        if (clipboard == null) {
            Feedback.show(this, "剪贴板不可用");
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("Chrona 诊断", asText(collect())));
        Feedback.show(this, "已复制，可直接粘贴发送");
    }

    private void clearLog() {
        DiagLog.clear(this);
        DiagLog.add(this, "log cleared");
        show(collect());
        Feedback.show(this, "日志已清空");
    }

    /** One titled block of label/value rows, optionally followed by a monospace excerpt. */
    private static final class Section {
        final String title;
        final List<String[]> rows = new ArrayList<>();
        /** What the card shows, which may be a readable tail of a long log. */
        String block;
        /** What the copied report carries; falls back to {@link #block} when there is no tail. */
        String fullBlock;
        String blockHint;

        Section(String title) {
            this.title = title;
        }

        void add(String label, String value) {
            rows.add(new String[]{label, value});
        }
    }

    private void show(List<Section> sections) {
        // Everything after the header is rebuilt; the header itself never changes.
        while (root.getChildCount() > 5) root.removeViewAt(root.getChildCount() - 1);
        for (Section section : sections) {
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(18), dp(16), dp(18), dp(16));
            UiStyle.glass(card);
            TextView heading = new TextView(this);
            heading.setText(section.title);
            heading.setTextSize(18);
            UiStyle.title(heading);
            UiStyle.addSpaced(card, heading, 0, 10);
            for (String[] row : section.rows) {
                LinearLayout line = new LinearLayout(this);
                line.setOrientation(LinearLayout.HORIZONTAL);
                TextView label = new TextView(this);
                label.setText(row[0]);
                label.setTextSize(13);
                label.setMinWidth(dp(84));
                UiStyle.muted(label);
                line.addView(label);
                TextView value = new TextView(this);
                value.setText(row[1]);
                value.setTextSize(14);
                value.setTextIsSelectable(true);
                UiStyle.title(value);
                value.setTypeface(android.graphics.Typeface.MONOSPACE,
                        android.graphics.Typeface.NORMAL);
                line.addView(value, new LinearLayout.LayoutParams(0, -2, 1));
                UiStyle.addSpaced(card, line, 2, 4);
            }
            if (section.block != null) {
                if (section.blockHint != null) {
                    TextView hint = new TextView(this);
                    hint.setText(section.blockHint);
                    hint.setTextSize(13);
                    UiStyle.muted(hint);
                    UiStyle.addSpaced(card, hint, 6, 6);
                }
                ScrollView window = new ScrollView(this) {
                    @Override public boolean dispatchTouchEvent(MotionEvent event) {
                        int action = event.getActionMasked();
                        if (getParent() != null && action == MotionEvent.ACTION_DOWN
                                && (canScrollVertically(1) || canScrollVertically(-1))) {
                            getParent().requestDisallowInterceptTouchEvent(true);
                        }
                        boolean handled = super.dispatchTouchEvent(event);
                        if (getParent() != null && (action == MotionEvent.ACTION_UP
                                || action == MotionEvent.ACTION_CANCEL)) {
                            getParent().requestDisallowInterceptTouchEvent(false);
                        }
                        return handled;
                    }
                };
                TextView body = new TextView(this);
                body.setText(section.block);
                body.setTextSize(12);
                body.setTextIsSelectable(true);
                body.setTypeface(android.graphics.Typeface.MONOSPACE);
                UiStyle.muted(body);
                window.addView(body);
                card.addView(window, new LinearLayout.LayoutParams(-1, dp(240)));
            }
            UiStyle.addSpaced(root, card, 0, 10);
        }
        UiStyle.pop(sections.isEmpty() ? root : root.getChildAt(root.getChildCount() - 1));
    }

    /** Everything below is read-only: no key material is ever printed. */
    private List<Section> collect() {
        List<Section> sections = new ArrayList<>();
        sections.add(environment());
        sections.add(services());
        sections.add(storage());
        sections.add(jobs());
        sections.add(log());
        return sections;
    }

    private Section environment() {
        Section section = new Section("应用与设备");
        section.add("应用", versionName() + " (versionCode " + versionCode()
                + ") debuggable=" + isDebuggable());
        section.add("设备", Build.MANUFACTURER + " " + Build.MODEL
                + " / Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
        section.add("时区", TimeZone.getDefault().getID());
        // A short uptime next to an input stuck in "parsing" means the system killed the process.
        section.add("进程已运行", elapsedText(
                SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()));
        return section;
    }

    private Section services() {
        Section section = new Section("解析服务");
        try {
            AiSettingsStore store = new AiSettingsStore(this);
            AiSettings settings = store.load();
            if (settings == null) {
                section.add("AI 服务", "未配置");
            } else {
                section.add("基础地址", settings.baseUrl);
                section.add("模型", settings.model);
                section.add("密钥", "已保存（内容不打印）");
                section.add("图片入口", store.isImageUnsupported(settings)
                        ? "已停用（该模型拒过图片）" : "正常");
            }
        } catch (Exception exception) {
            section.add("AI 服务", "读取失败 " + exception);
        }
        return section;
    }

    private Section storage() {
        Section section = new Section("本机存储");
        try (TaskStore store = new TaskStore(this)) {
            for (String line : store.describe().split("\n")) {
                int split = line.indexOf(':');
                if (split <= 0) {
                    if (!line.trim().isEmpty()) section.add("", line.trim());
                } else {
                    section.add(line.substring(0, split).trim(), line.substring(split + 1).trim());
                }
            }
        } catch (Exception exception) {
            section.add("数据库", "读取失败 " + exception);
        }
        try {
            section.add("附件", new ImageStore(this).describe());
        } catch (Exception exception) {
            section.add("附件", "读取失败 " + exception);
        }
        section.add("日志文件", DiagLog.sizeBytes(this) / 1024 + " KB");
        section.add("内存中日志", DiagLog.recent().size() + " 行");
        return section;
    }

    private Section jobs() {
        Section section = new Section("解析任务队列");
        JobScheduler scheduler = getSystemService(JobScheduler.class);
        List<JobInfo> pending = scheduler == null ? null : scheduler.getAllPendingJobs();
        if (pending == null) {
            section.add("待处理", "不可用");
        } else {
            section.add("待处理", pending.size() + " 个");
            for (JobInfo job : pending) {
                section.add("job " + job.getId(), "task=" + job.getExtras().getLong("task_id", -1)
                        + " 用户发起=" + userInitiated(job));
            }
        }
        return section;
    }

    private Section log() {
        Section section = new Section("事件日志");
        String text = DiagLog.read(this);
        if (text.isEmpty()) {
            section.add("记录", "暂无");
            return section;
        }
        String[] lines = text.split("\n");
        int shown = Math.min(lines.length, VISIBLE_LOG_LINES);
        StringBuilder tail = new StringBuilder();
        for (int i = lines.length - shown; i < lines.length; i++) {
            tail.append(lines[i]).append('\n');
        }
        section.block = tail.toString();
        section.fullBlock = text;
        section.blockHint = shown == lines.length
                ? "共 " + lines.length + " 行（新→旧请从末尾看）"
                : "共 " + lines.length + " 行，此处显示最后 " + shown + " 行；复制全部会包含完整日志";
        return section;
    }

    /** The flat text earlier reports were pasted in, so dumps stay comparable. */
    private String asText(List<Section> sections) {
        StringBuilder text = new StringBuilder();
        for (Section section : sections) {
            for (String[] row : section.rows) {
                text.append(row[0].isEmpty() ? row[1] : row[0] + ": " + row[1]).append('\n');
            }
            String block = section.fullBlock != null ? section.fullBlock : section.block;
            if (block != null) {
                text.append("\n--- 事件日志（新→旧倒序请看文件末尾） ---\n").append(block);
            }
        }
        return text.toString();
    }

    /** User-initiated jobs get priority over background cleaners; only Android 14+ has them. */
    private static String userInitiated(JobInfo job) {
        if (Build.VERSION.SDK_INT < 34) return "不适用";
        return job.isUserInitiated() ? "是" : "否";
    }

    private static String elapsedText(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        if (seconds < 60) return seconds + " 秒";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " 分 " + (seconds % 60) + " 秒";
        return (minutes / 60) + " 小时 " + (minutes % 60) + " 分";
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
