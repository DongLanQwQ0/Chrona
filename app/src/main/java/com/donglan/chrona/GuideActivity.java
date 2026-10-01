package com.donglan.chrona;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Concrete instructions with optional practice in the real app. */
public final class GuideActivity extends Activity {
    private static final int GUIDE_VERSION = 2;
    private static final String PRACTICE_TEXT = "明天下午3点到4点，在图书馆学习，提前10分钟提醒我。";

    private static final class Step {
        final String title, introduction, outcome, tip, action;
        final String[] instructions;
        final Class<? extends Activity> target;

        Step(String title, String introduction, String[] instructions, String outcome,
                String tip, String action, Class<? extends Activity> target) {
            this.title = title;
            this.introduction = introduction;
            this.instructions = instructions;
            this.outcome = outcome;
            this.tip = tip;
            this.action = action;
            this.target = target;
        }
    }

    private static final Step[] STEPS = {
        new Step("先认识拾时", "记下事情 → AI 生成草稿 → 逐项审核 → 系统日历提醒。", new String[]{
                "「首页」查看近期安排；点主界面的 + 开始记录。",
                "「收件箱」保存你投入的原始内容，查看解析进度、待确认和失败记录。",
                "「日程」查看识别出的事项，使用搜索与筛选找到需要的日程。"
        }, "知道去哪里记录、查看进度和浏览安排。", "一条记录可以生成多个日程。解析完成后仍需要你确认，才会写入系统日历。", null, null),
        new Step("配置 AI 服务", "先连接自己的 AI 服务，拾时才能理解文字和截图。", new String[]{
                "打开「设置 → AI 服务」。",
                "填写服务地址、模型名称和 API 密钥；也可以先选常用模型预设，再填写密钥。",
                "点「保存设置」，然后返回拾时。"
        }, "保存好配置，就可以提交第一条记录。", "图片需要支持视觉的模型。API 密钥在本机加密保存，请使用服务商提供的准确地址和模型名称。", "打开 AI 服务", SettingsActivity.class),
        new Step("记录第一件事", "尽量说清楚做什么、什么时候、在哪里，以及提前多久提醒。", new String[]{
                "点主界面的 +，在「记录一件事」中输入或粘贴内容。",
                "需要时点标题旁的剪贴板、图片或文件图标；也可以从其他应用的分享菜单选择拾时。",
                "检查内容后点右上角 ✓。保存后自动解析，你可以继续记录下一件事。"
        }, "看到「已存入收件箱」，说明记录已保存。接下来去收件箱等待结果。", "复制下方示例后，可以去记录页点剪贴板图标粘贴。只有你点 ✓ 才会提交给 AI；普通文件只提供名称等信息，内容不会发送。", "去记录一件事", MainActivity.class),
        new Step("查看结果，逐项审核", "AI 返回的是日程草稿，需要你核对后确认。", new String[]{
                "进入「收件箱」，点击刚才的记录，查看排队、解析中或待确认状态。",
                "核对日程标题、类型、时间和地点；点时间字段选择日期和时间，点备注可展开编辑。",
                "如果一条记录生成多个日程，在日程区域左右滑动，按「日程 1 / N」逐项检查。"
        }, "每项日程的内容与原始记录一致；不确定的时间已补全。", "解析失败时先检查网络和 AI 配置，再重试。尚未写入日历的记录也可以编辑原始内容后重新解析。", null, null),
        new Step("确认并开启提醒", "审核草稿后，把当前这项日程写入系统日历。", new String[]{
                "在详情中检查「提醒」：数字表示提前多少分钟；留空表示不提醒。",
                "点右上角 ✓ 保存并确认当前日程；有多项时，需要分别检查和确认。",
                "按系统提示允许日历权限，并检查系统日历的通知和提醒开关。"
        }, "确认成功后，日程显示已写入日历；到设定时间由系统日历提醒。", "拾时的通知用于解析进度与结果。日程提醒由系统日历负责，系统日历通知被关闭时可能收不到提醒。", null, null),
        new Step("日常查看与维护", "完成一次「记录 → 审核 → 确认」，就掌握了基本用法。", new String[]{
                "在首页查看近期安排；在「日程」中搜索标题或关键词，按类型与时间筛选。",
                "点击日程打开对应的一项，修改后点 ✓ 更新；删除日程时，已关联的系统日历条目也会删除。",
                "在设置里调整外观、备份与恢复、检查更新；桌面可添加拾时小组件查看安排。"
        }, "现在可以用拾时记录真实任务，并从设置里的「使用引导」随时重看步骤。", "换手机或进行重要操作前，先通过「设置 → 备份与恢复」导出完整备份。", "打开备份与恢复", BackupRestoreActivity.class)
    };

    private ScrollView page;
    private LinearLayout content;
    private TextView progress;
    private Button previous, next;
    private int currentStep;

    static boolean showIfNeeded(Activity activity) {
        if (activity.getSharedPreferences("usage_guide", Context.MODE_PRIVATE)
                .getInt("version", 0) >= GUIDE_VERSION) return false;
        activity.startActivity(new Intent(activity, GuideActivity.class));
        return true;
    }

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        // Mark this guide version as shown before entering practice, which may return to Home.
        getSharedPreferences("usage_guide", MODE_PRIVATE).edit()
                .putBoolean("shown", true).putInt("version", GUIDE_VERSION).apply();
        if (state != null) currentStep = Math.max(0, Math.min(STEPS.length - 1, state.getInt("step")));
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(20), dp(16), dp(20), dp(16));
        UiStyle.page(this, shell);
        shell.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("使用引导");
        title.setTextSize(26);
        UiStyle.title(title);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        Button skip = new Button(this);
        skip.setText("稍后再看");
        UiStyle.button(skip, false);
        skip.setOnClickListener(view -> finish());
        header.addView(skip, new LinearLayout.LayoutParams(-2, dp(48)));
        shell.addView(header);
        progress = new TextView(this);
        progress.setTextSize(14);
        progress.setTextColor(UiStyle.colors(this).primary);
        progress.setAccessibilityLiveRegion(android.view.View.ACCESSIBILITY_LIVE_REGION_POLITE);
        UiStyle.addSpaced(shell, progress, 12, 8);
        page = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(12));
        page.addView(content);
        shell.addView(page, new LinearLayout.LayoutParams(-1, 0, 1f));
        LinearLayout controls = new LinearLayout(this);
        previous = new Button(this);
        previous.setText("上一步");
        UiStyle.button(previous, false);
        previous.setOnClickListener(view -> showStep(currentStep - 1));
        controls.addView(previous, new LinearLayout.LayoutParams(0, dp(52), 1f));
        next = new Button(this);
        UiStyle.button(next, true);
        next.setOnClickListener(view -> {
            if (currentStep == STEPS.length - 1) finish(); else showStep(currentStep + 1);
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, dp(52), 1f);
        nextParams.leftMargin = dp(10);
        controls.addView(next, nextParams);
        UiStyle.addSpaced(shell, controls, 12, 0);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.applyInsets(stage, shell);
        setContentView(stage);
        showStep(currentStep);
        if (state != null) page.post(() -> page.scrollTo(0, state.getInt("scroll_y")));
    }

    private void showStep(int index) {
        currentStep = Math.max(0, Math.min(STEPS.length - 1, index));
        Step step = STEPS[currentStep];
        content.removeAllViews();
        progress.setText("第 " + (currentStep + 1) + " / " + STEPS.length + " 步");
        text(content, step.title, 25, true);
        text(content, step.introduction, 16, false);
        LinearLayout instructions = card();
        text(instructions, "操作步骤", 18, true);
        for (int i = 0; i < step.instructions.length; i++)
            text(instructions, (i + 1) + ". " + step.instructions[i], 16, false);
        UiStyle.addSpaced(content, instructions, 8, 12);
        text(content, "完成后", 17, true);
        text(content, step.outcome, 15, false);
        if (currentStep == 2) {
            LinearLayout sample = card();
            text(sample, "可以试试这样说", 17, true);
            text(sample, PRACTICE_TEXT, 16, false);
            Button copy = new Button(this);
            copy.setText("复制练习示例");
            UiStyle.button(copy, false);
            copy.setOnClickListener(view -> {
                ClipboardManager clipboard = getSystemService(ClipboardManager.class);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("拾时练习示例", PRACTICE_TEXT));
                    Feedback.show(this, "示例已复制，可在记录页粘贴");
                }
            });
            sample.addView(copy);
            UiStyle.addSpaced(content, sample, 4, 12);
        }
        if (step.target != null) {
            Button practice = new Button(this);
            practice.setText(step.action);
            UiStyle.button(practice, false);
            practice.setOnClickListener(view -> startActivity(new Intent(this, step.target)));
            UiStyle.addSpaced(content, practice, 4, 12);
        }
        text(content, step.tip, 14, false);
        previous.setEnabled(currentStep > 0);
        next.setText(currentStep == STEPS.length - 1 ? "开始使用" : "下一步");
        page.scrollTo(0, 0);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(8));
        UiStyle.glass(card);
        return card;
    }

    private void text(LinearLayout parent, String value, int size, boolean heading) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setLineSpacing(dp(4), 1f);
        if (heading) UiStyle.title(text); else UiStyle.muted(text);
        UiStyle.addSpaced(parent, text, 0, 12);
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("step", currentStep);
        state.putInt("scroll_y", page.getScrollY());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
