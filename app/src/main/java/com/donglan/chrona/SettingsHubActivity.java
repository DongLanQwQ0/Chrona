package com.donglan.chrona;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;

import com.donglan.chrona.debug.DebugActivity;

/** Compact grouped settings; details belong to their destination or dialog. */
public final class SettingsHubActivity extends Activity {
    private ScrollView page;
    private Dialog aboutDialog;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        page = new ScrollView(this);
        page.setVerticalScrollBarEnabled(false);
        LinearLayout root = column();
        root.setPadding(dp(20), dp(16), dp(20), dp(24));
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        page.addView(root);
        header(root);

        LinearLayout display = group(root, "外观与显示");
        destination(display, "外观与配色", AppearanceActivity.class);
        timelineLimit(display);

        LinearLayout widgets = group(root, "桌面小组件");
        widgetPreview(widgets);
        widgetDanmaku(widgets);

        LinearLayout services = group(root, "服务与数据");
        destination(services, "AI 服务", SettingsActivity.class);
        destination(services, "备份与恢复", BackupRestoreActivity.class);
        destination(services, "提醒诊断", ReminderDiagnosticsActivity.class);

        LinearLayout help = group(root, "帮助与关于");
        destination(help, "使用引导", GuideActivity.class);
        destination(help, "检查更新", UpdateActivity.class);
        row(help, "关于拾时", appVersionName(), this::showAbout);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            destination(help, "诊断信息", DebugActivity.class);

        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        root.setFitsSystemWindows(false);
        UiStyle.applyInsets(stage, page);
        setContentView(stage);
        if (state != null) {
            int scrollY = state.getInt("scroll_y");
            page.post(() -> page.scrollTo(0, scrollY));
        }
    }

    private void header(LinearLayout root) {
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_left);
        back.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        back.setContentDescription("返回");
        UiStyle.acrylicChoice(back, false, UiStyle.RADIUS_PILL, false);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = text("设置", 24);
        UiStyle.title(title);
        title.setPadding(dp(12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        UiStyle.addSpaced(root, header, 0, 2);
    }

    private LinearLayout group(LinearLayout root, String name) {
        TextView heading = text(name, 12);
        UiStyle.muted(heading);
        heading.setPadding(dp(4), 0, dp(4), 0);
        if (Build.VERSION.SDK_INT >= 28) heading.setAccessibilityHeading(true);
        UiStyle.addSpaced(root, heading, 16, 7);
        LinearLayout group = column();
        UiStyle.glass(group);
        root.addView(group, new LinearLayout.LayoutParams(-1, -2));
        return group;
    }

    private void destination(LinearLayout group, String title, Class<? extends Activity> target) {
        row(group, title, null, () -> startActivity(new Intent(this, target)));
    }

    /** Full-row targets, wrapping labels, and values aligned before a trailing chevron. */
    private TextView row(LinearLayout group, String title, String value, Runnable action) {
        divider(group);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(54));
        row.setPadding(dp(16), dp(10), dp(12), dp(10));
        row.addView(text(title, 16), new LinearLayout.LayoutParams(0, -2, 1));
        TextView current = text(value == null ? "" : value, 13);
        UiStyle.muted(current);
        current.setGravity(Gravity.END);
        current.setPadding(dp(8), 0, dp(8), 0);
        current.setMaxWidth(dp(110));
        current.setVisibility(value == null ? View.GONE : View.VISIBLE);
        row.addView(current, new LinearLayout.LayoutParams(-2, -2));
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right_line);
        chevron.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).muted));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chevron, new LinearLayout.LayoutParams(dp(16), dp(16)));
        row.setFocusable(true);
        row.setOnClickListener(view -> action.run());
        UiStyle.pressable(row);
        group.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return current;
    }

    private void divider(LinearLayout group) {
        if (group.getChildCount() == 0) return;
        View line = new View(this);
        line.setBackgroundColor(UiStyle.colors(this).outline);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMargins(dp(16), 0, dp(16), 0);
        group.addView(line, params);
    }

    private void timelineLimit(LinearLayout group) {
        TextView[] value = new TextView[1];
        value[0] = row(group, "首页时间线数量", HomeTimelinePreferences.getItemLimit(this) + " 项", () -> {
            String[] options = new String[10];
            for (int i = 0; i < options.length; i++) options[i] = (5 + i * 5) + " 项";
            int selected = (HomeTimelinePreferences.getItemLimit(this) - 5) / 5;
            UiStyle.choiceDialog(this, "首页时间线数量", options, selected, choice -> {
                HomeTimelinePreferences.setItemLimit(this, 5 + choice * 5);
                value[0].setText(HomeTimelinePreferences.getItemLimit(this) + " 项");
            });
        });
    }

    private void widgetPreview(LinearLayout group) {
        TextView[] value = new TextView[1];
        value[0] = row(group, "明日安排预览", previewTime(), () ->
                UiStyle.timeDialog(this, "明日安排预览时间", WidgetPreferences.previewMinutes(this), selected -> {
                    WidgetPreferences.setPreviewMinutes(this, selected);
                    value[0].setText(previewTime());
                }));
    }

    private String previewTime() {
        int minutes = WidgetPreferences.previewMinutes(this);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private void widgetDanmaku(LinearLayout group) {
        divider(group);
        Switch enabled = new Switch(this);
        enabled.setText("鼓励弹幕");
        enabled.setTextSize(16);
        enabled.setPadding(dp(16), dp(3), dp(16), dp(3));
        enabled.setSwitchPadding(dp(12));
        UiStyle.toggle(enabled);
        enabled.setMinHeight(dp(54));
        enabled.setChecked(WidgetPreferences.danmakuEnabled(this));
        enabled.setOnCheckedChangeListener((button, checked) ->
                WidgetPreferences.setDanmakuEnabled(this, checked));
        group.addView(enabled, new LinearLayout.LayoutParams(-1, -2));
    }

    private void showAbout() {
        if (aboutDialog != null && aboutDialog.isShowing()) return;
        aboutDialog = new Dialog(this);
        LinearLayout panel = column();
        panel.setPadding(dp(20), dp(20), dp(20), dp(16));
        TextView title = text("拾时 · Chrona", 22);
        UiStyle.title(title);
        panel.addView(title);
        TextView version = text(appVersionName(), 13);
        UiStyle.muted(version);
        UiStyle.addSpaced(panel, version, 4, 16);
        LinearLayout links = column();
        row(links, "GitHub 主页", null, () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/DongLanQwQ0")));
            } catch (android.content.ActivityNotFoundException exception) {
                Feedback.show(this, "没有可用的浏览器");
            }
        });
        TextView qq = row(links, "联系作者", "2590339284", this::copyQq);
        View qqRow = (View) qq.getParent();
        qqRow.setOnLongClickListener(view -> { copyQq(); return true; });
        qqRow.setContentDescription("作者 QQ 2590339284，点击或长按复制");
        panel.addView(links);
        TextView welcome = text("欢迎来找我喵~QwQ", 13);
        welcome.setGravity(Gravity.CENTER);
        UiStyle.muted(welcome);
        UiStyle.addSpaced(panel, welcome, 16, 2);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(panel);
        UiStyle.showFloatingDialog(aboutDialog, scroll);
    }

    private void copyQq() {
        android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
        if (clipboard == null) { Feedback.show(this, "剪贴板暂时不可用"); return; }
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QQ", "2590339284"));
        Feedback.show(this, "QQ号已复制");
    }

    private String appVersionName() {
        try {
            PackageManager manager = getPackageManager();
            if (Build.VERSION.SDK_INT >= 33)
                return manager.getPackageInfo(getPackageName(), PackageManager.PackageInfoFlags.of(0)).versionName;
            return manager.getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "版本信息不可用";
        }
    }

    private LinearLayout column() {
        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        return column;
    }

    private TextView text(String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(UiStyle.colors(this).text);
        text.setIncludeFontPadding(false);
        return text;
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("scroll_y", page.getScrollY());
    }

    @Override protected void onDestroy() {
        if (aboutDialog != null) aboutDialog.dismiss();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
