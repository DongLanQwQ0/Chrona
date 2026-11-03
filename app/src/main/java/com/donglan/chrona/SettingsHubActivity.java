package com.donglan.chrona;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
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
        LinearLayout root = SettingsPageLayout.content(this);
        page.addView(root);
        header(root);

        LinearLayout display = SettingsPageLayout.group(this, root, "外观与显示");
        destination(display, "外观与配色", AppearanceActivity.class);
        timelineLimit(display);

        LinearLayout scheduling = SettingsPageLayout.group(this, root, "日程处理");
        destination(scheduling, "AI 服务", SettingsActivity.class);
        destination(scheduling, "日程来源", ScheduleSourceSettingsActivity.class);
        destination(scheduling, "系统日程", SystemCalendarSettingsActivity.class);
        destination(scheduling, "折姜星辰", ZjuSettingsActivity.class);

        LinearLayout widgets = SettingsPageLayout.group(this, root, "桌面小组件");
        widgetPreview(widgets);
        widgetDanmaku(widgets);
        widgetSystemCalendar = calendarToggle(widgets, "包含系统其他日程",
                WidgetPreferences.includesSystemCalendar(this), 702,
                enabled -> WidgetPreferences.setIncludesSystemCalendar(this, enabled));

        LinearLayout reminders = SettingsPageLayout.group(this, root, "通知");
        destination(reminders, "通知与提醒", NotificationSettingsActivity.class);

        LinearLayout services = SettingsPageLayout.group(this, root, "服务与数据");
        destination(services, "跨设备同步", SyncSettingsActivity.class);
        destination(services, "局域网访问", LanSettingsActivity.class);
        destination(services, "备份与恢复", BackupRestoreActivity.class);

        LinearLayout help = SettingsPageLayout.group(this, root, "帮助与关于");
        destination(help, "使用引导", GuideActivity.class);
        destination(help, "检查更新", UpdateActivity.class);
        SettingsPageLayout.row(this, help, "关于拾时", appVersionName(), this::showAbout);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0)
            destination(help, "诊断信息", DebugActivity.class);

        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        root.setFitsSystemWindows(false);
        UiStyle.applyInsets(stage, page);
        setContentView(stage);
        if (state == null) UiMotion.enterPage(root);
        if (state != null) {
            int scrollY = state.getInt("scroll_y");
            page.post(() -> page.scrollTo(0, scrollY));
        }
    }

    private void header(LinearLayout root) {
        SettingsPageLayout.header(this, root, "设置");
    }

    private void destination(LinearLayout group, String title, Class<? extends Activity> target) {
        SettingsPageLayout.row(this, group, title, null, () -> startActivity(new Intent(this, target)));
    }

    private void timelineLimit(LinearLayout group) {
        TextView[] value = new TextView[1];
        value[0] = SettingsPageLayout.row(this, group, "首页时间线数量", HomeTimelinePreferences.getItemLimit(this) + " 项", () -> {
            String[] options = new String[10];
            for (int i = 0; i < options.length; i++) options[i] = (5 + i * 5) + " 项";
            int selected = (HomeTimelinePreferences.getItemLimit(this) - 5) / 5;
            UiDialogs.choiceDialog(this, "首页时间线数量", options, selected, choice -> {
                HomeTimelinePreferences.setItemLimit(this, 5 + choice * 5);
                value[0].setText(HomeTimelinePreferences.getItemLimit(this) + " 项");
            });
        });
    }
    private Switch widgetSystemCalendar;
    private Switch calendarToggle(LinearLayout group, String title, boolean checked, int request,
            java.util.function.Consumer<Boolean> save) {
        Switch control = SettingsPageLayout.toggle(this, group, title, checked);
        control.setOnCheckedChangeListener((button, enabled) -> {
            if (enabled && !new com.donglan.chrona.calendar.CalendarStore(this).hasReadPermission()) {
                control.setChecked(false);
                requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, request);
                return;
            }
            save.accept(enabled);
        });
        return control;
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 702) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            widgetSystemCalendar.setChecked(granted);
            if (!granted) Feedback.show(this, "未获得读取日历权限");
        }
    }

    private void widgetPreview(LinearLayout group) {
        TextView[] value = new TextView[1];
        value[0] = SettingsPageLayout.row(this, group, "明日安排预览", previewTime(), () ->
                UiDialogs.timeDialog(this, "明日安排预览时间", WidgetPreferences.previewMinutes(this), selected -> {
                    WidgetPreferences.setPreviewMinutes(this, selected);
                    value[0].setText(previewTime());
                }));
    }

    private String previewTime() {
        int minutes = WidgetPreferences.previewMinutes(this);
        return String.format(java.util.Locale.ROOT, "%02d:%02d", minutes / 60, minutes % 60);
    }

    private void widgetDanmaku(LinearLayout group) {
        SettingsPageLayout.divider(this, group);
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
        SettingsPageLayout.row(this, links, "GitHub 主页", null, () -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/DongLanQwQ0")));
            } catch (android.content.ActivityNotFoundException exception) {
                Feedback.show(this, "没有可用的浏览器");
            }
        });
        TextView qq = SettingsPageLayout.row(this, links, "联系作者", "2590339284", this::copyQq);
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
        UiDialogs.showFloatingDialog(aboutDialog, scroll);
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
