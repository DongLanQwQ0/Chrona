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
    private static final int GUIDE_VERSION = 4;
    private static final String PRACTICE_TEXT = "明天下午3点到4点，在图书馆学习，提前10分钟提醒我。";

    private static final class Step {
        final String title, introduction, tip, action;
        final String[] instructions;
        final Class<? extends Activity> target;

        Step(String title, String introduction, String[] instructions,
                String tip, String action, Class<? extends Activity> target) {
            this.title = title;
            this.introduction = introduction;
            this.instructions = instructions;
            this.tip = tip;
            this.action = action;
            this.target = target;
        }
    }

    private static final Step[] STEPS = {
        new Step("三处入口", "首页看安排，日程找事项，收件箱看原文。", new String[]{
                "首页：查看近期安排和待办，点 ＋ 记录。",
                "日程：搜索事项，按日期、类型和状态筛选。",
                "收件箱：查看解析进度，处理待确认或失败记录。"
        }, null, null, null),
        new Step("连接 AI", "填写你自己的 AI 服务。", new String[]{
                "填写服务地址、模型名称和 API 密钥。",
                "点「保存设置」，然后返回。"
        }, "识别截图需要支持图片的模型。", "配置 AI 服务", SettingsActivity.class),
        new Step("记下第一件事", "说清做什么、时间和地点。", new String[]{
                "点首页 ＋，输入文字或添加截图。也可从其他应用分享给拾时。",
                "点右上角 ✓ 保存并开始解析，结果在收件箱查看。"
        }, "文字和截图会发送给你配置的 AI 服务。", "打开记录页", MainActivity.class),
        new Step("核对识别结果", "时间明确的自动写入，存疑的由你确认。", new String[]{
                "在收件箱打开记录，核对标题和时间；有多项时左右滑动逐项查看。",
                "活动有起止时间；待办和作业可设截止时间，也可暂不安排。",
                "改好后点右上角 ✓ 保存。长按列表卡片可多选，再点「写入日历」。"
        }, "解析失败时，检查网络和 AI 配置后重试。", null, null),
        new Step("设置提醒", "允许权限，提醒才能正常送达。", new String[]{
                "在事项详情设置提前几分钟提醒；留空表示不提醒。",
                "打开「通知与提醒」，允许通知、日历和准时提醒所需权限。",
                "系统日历中也需开启通知；常驻卡片可在提醒设置中开启。"
        }, null, "打开通知与提醒", NotificationSettingsActivity.class),
        new Step("查看与处理", "日常用这几个入口就够了。", new String[]{
                "点「今天的安排」打开课表，点「收件箱」标题打开日历。",
                "详情中，新建与删除之间的圆形勾按钮用于完成或恢复待办；未来或进行中的活动可确认或撤销。",
                "换手机前，到「备份与恢复」导出完整备份。"
        }, "需要重看时，打开「设置 → 使用引导」。", "打开备份与恢复", BackupRestoreActivity.class)
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
        CollapsingPageLayout shell = new CollapsingPageLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        UiStyle.page(this, shell);
        shell.setFitsSystemWindows(false);
        shell.setPadding(dp(20), dp(16), dp(20), dp(16));
        shell.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = new TextView(this);
        title.setText("使用引导");
        title.setTextSize(24);
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
        page.setVerticalScrollBarEnabled(false);
        page.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(0, dp(4), 0, dp(12));
        page.addView(content);
        FrameLayout pageHost = new FrameLayout(this);
        pageHost.addView(page, new FrameLayout.LayoutParams(-1, -1));
        shell.addView(pageHost, new LinearLayout.LayoutParams(-1, 0, 1f));
        shell.setScrollingBody(pageHost);
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
        renderStep(currentStep);
        if (state == null) UiMotion.enterPage(shell);
        if (state != null) page.post(() -> page.scrollTo(0, state.getInt("scroll_y")));
    }

    private void showStep(int index) {
        int destination = Math.max(0, Math.min(STEPS.length - 1, index));
        if (destination == currentStep) return;
        // Snapshot only the visible viewport, never the full height of a tutorial step.
        UiMotion.swap(page, () -> renderStep(destination));
    }

    private void renderStep(int index) {
        currentStep = Math.max(0, Math.min(STEPS.length - 1, index));
        Step step = STEPS[currentStep];
        content.removeAllViews();
        progress.setText("第 " + (currentStep + 1) + " / " + STEPS.length + " 步");
        text(content, step.title, 24, true);
        text(content, step.introduction, 16, false);
        LinearLayout instructions = card();
        for (int i = 0; i < step.instructions.length; i++)
            text(instructions, (i + 1) + ". " + step.instructions[i], 16, false);
        UiStyle.addSpaced(content, instructions, 8, 12);
        if (currentStep == 2) {
            LinearLayout sample = card();
            text(sample, "试一句", 16, true);
            text(sample, PRACTICE_TEXT, 16, false);
            Button copy = new Button(this);
            copy.setText("复制示例");
            UiStyle.button(copy, false);
            copy.setOnClickListener(view -> {
                ClipboardManager clipboard = getSystemService(ClipboardManager.class);
                if (clipboard != null) {
                    clipboard.setPrimaryClip(ClipData.newPlainText("拾时练习示例", PRACTICE_TEXT));
                    Feedback.show(this, "示例已复制，可在记录页粘贴");
                }
            });
            // The preceding text already provides 12dp; the final action must keep its own bottom inset.
            LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(-2, -2);
            copyParams.bottomMargin = dp(12);
            sample.addView(copy, copyParams);
            UiStyle.addSpaced(content, sample, 8, 12);
        }
        if (step.target != null) {
            Button practice = new Button(this);
            practice.setText(step.action);
            UiStyle.button(practice, false);
            practice.setOnClickListener(view -> startActivity(new Intent(this, step.target)));
            UiStyle.addSpaced(content, practice, 4, 12);
        }
        if (step.tip != null) text(content, step.tip, 15, false);
        previous.setEnabled(currentStep > 0);
        next.setText(currentStep == STEPS.length - 1 ? "开始使用" : "下一步");
        page.scrollTo(0, 0);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        // Text rows already carry a 12dp bottom margin; a card inset here would double it.
        card.setPadding(dp(16), dp(12), dp(16), 0);
        UiStyle.card(card);
        return card;
    }

    private void text(LinearLayout parent, String value, int size, boolean heading) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setLineSpacing(dp(4), 1f);
        if (heading) UiStyle.title(text); else text.setTextColor(UiStyle.colors(this).text);
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
