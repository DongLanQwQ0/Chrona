package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

/** Notification controls live here; unrelated display/widget preferences retain their entries. */
public final class NotificationSettingsActivity extends Activity {
    private static final int CALENDAR_PERMISSION = 703, NOTIFICATION_PERMISSION = 704;
    private LinearLayout atomicSection;
    private Switch enabled, system, chronaAtomic, systemAtomic, wallpaper, countdown, seconds;
    private TextView notificationStatus, channelStatus, exactStatus, atomicStatus, capsuleTimingStatus;
    private boolean atomicAvailable;
    private int generation;
    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state);
        LinearLayout root = SettingsPageLayout.content(this);
        SettingsPageLayout.header(this, root, "通知与提醒");
        LinearLayout reminders = group(root, "日程提醒");
        enabled = toggle(reminders, "开始前 30 分钟提醒", AgendaReminderPreferences.enabled(this), value -> {
            AgendaReminderPreferences.setEnabled(this, value); updateControls();
            if (value && Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
        });
        system = toggle(reminders, "包含系统其他日程", AgendaReminderPreferences.includesSystemCalendar(this), value -> {
            if (value && !new com.donglan.chrona.calendar.CalendarStore(this).hasReadPermission()) {
                system.setChecked(false);
                requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, CALENDAR_PERMISSION); return;
            }
            AgendaReminderPreferences.setIncludesSystemCalendar(this, value); updateControls();
        });
        atomicSection = new LinearLayout(this); atomicSection.setOrientation(LinearLayout.VERTICAL);
        root.addView(atomicSection);
        LinearLayout permissions = group(root, "权限与后台");
        notificationStatus = row(permissions, "通知权限", () -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED)
                requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, NOTIFICATION_PERMISSION);
            else openSettings(NotificationDeliveryStatus.appSettings(this));
        });
        channelStatus = row(permissions, "提醒频道", () -> openReminderChannelSettings());
        exactStatus = row(permissions, "准时提醒", () -> {
            if (Build.VERSION.SDK_INT >= 31) openSettings(new Intent(android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    android.net.Uri.parse("package:" + getPackageName())));
        });
        row(permissions, "后台运行", () -> {
            if (VivoAtomicNotification.supportedManufacturer()) Feedback.show(this, "电量 → 后台耗电管理 → 允许后台耗电");
            openSettings(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    android.net.Uri.parse("package:" + getPackageName())));
        });
        row(permissions, "提醒诊断", () -> startActivity(new Intent(this, ReminderDiagnosticsActivity.class)));
        updateControls(); SettingsPageLayout.show(this, root, state == null);
    }
    private void createAtomicControls() {
        if (chronaAtomic != null) return;
        LinearLayout group = group(atomicSection, "原子岛");
        chronaAtomic = toggle(group, "Chrona 日程", AgendaReminderPreferences.atomicChrona(this), value -> AgendaReminderPreferences.setAtomicChrona(this, value));
        systemAtomic = toggle(group, "系统日程", AgendaReminderPreferences.atomicSystem(this), value -> AgendaReminderPreferences.setAtomicSystem(this, value));
        wallpaper = toggle(group, "使用壁纸背景", AgendaReminderPreferences.atomicWallpaper(this), value -> AgendaReminderPreferences.setAtomicWallpaper(this, value));
        countdown = toggle(group, "胶囊显示倒计时", AgendaReminderPreferences.capsuleCountdown(this), value -> AgendaReminderPreferences.setCapsuleCountdown(this, value));
        seconds = toggle(group, "显示秒数", AgendaReminderPreferences.showSeconds(this), value -> AgendaReminderPreferences.setShowSeconds(this, value));
        atomicStatus = row(group, "原子通知权限", () -> {
            NotificationDeliveryStatus delivery = NotificationDeliveryStatus.read(this);
            if (delivery.appAllowed && !delivery.channelAllowed) openReminderChannelSettings();
            else openSettings(NotificationDeliveryStatus.appSettings(this));
        });
        capsuleTimingStatus = row(group, "胶囊计时", () -> {
            openSettings(new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS));
        });
    }
    private void updateControls() {
        if (system != null) system.setEnabled(AgendaReminderPreferences.enabled(this));
        if (chronaAtomic == null) return;
        boolean available = AgendaReminderPreferences.enabled(this) && atomicAvailable;
        chronaAtomic.setEnabled(available);
        systemAtomic.setEnabled(available && AgendaReminderPreferences.includesSystemCalendar(this));
        wallpaper.setEnabled(available); countdown.setEnabled(available); seconds.setEnabled(available);
    }
    @Override protected void onResume() {
        super.onResume();
        refreshStatus();
    }
    private void refreshStatus() {
        NotificationDeliveryStatus delivery = NotificationDeliveryStatus.read(this);
        notificationStatus.setText(delivery.appAllowed ? "已允许" : "未允许");
        channelStatus.setText(delivery.channelSummary());
        exactStatus.setText(AgendaReminder.exactAllowed(this) ? "已允许" : "系统可能延迟");
        atomicAvailable = false;
        updateControls();
        if (!VivoAtomicNotification.supportedManufacturer()) return;
        int request = ++generation;
        new Thread(() -> {
            Boolean scene = VivoAtomicNotification.sceneEnabled(this);
            runOnUiThread(() -> {
                if (isDestroyed() || isFinishing() || request != generation) return;
                atomicSection.setVisibility(scene == null ? View.GONE : View.VISIBLE);
                if (scene != null) {
                    NotificationDeliveryStatus current = NotificationDeliveryStatus.read(this);
                    createAtomicControls();
                    atomicAvailable = Boolean.TRUE.equals(scene) && current.canPostReminders();
                    atomicStatus.setText(!current.appAllowed ? "通知权限未允许"
                            : !current.channelAllowed ? "提醒频道已关闭"
                            : Boolean.TRUE.equals(scene) ? "可用" : "未启用");
                    capsuleTimingStatus.setText(AtomicCapsuleAccessibilityService.enabled(this) ? "已开启" : "未开启");
                    updateControls();
                }
            });
        }, "chrona-atomic-capability").start();
    }
    @Override protected void onPause() {
        generation++;
        super.onPause();
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == CALENDAR_PERMISSION) {
            boolean granted = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            system.setChecked(granted);
            if (!granted) Feedback.show(this, "未获得读取日历权限");
        }
        AgendaReminder.requestRefresh(this); refreshStatus(); updateControls();
    }
    private void openSettings(Intent intent) {
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException | SecurityException failure) { Feedback.show(this, "系统未提供此设置入口"); }
    }
    private void openReminderChannelSettings() {
        NotificationDeliveryStatus delivery = NotificationDeliveryStatus.read(this);
        openSettings(delivery.channelCreated ? NotificationDeliveryStatus.channelSettings(this)
                : NotificationDeliveryStatus.appSettings(this));
    }
    private LinearLayout group(LinearLayout root, String label) {
        TextView title = text(label, 12); UiStyle.muted(title); UiStyle.addSpaced(root, title, root.getChildCount() == 1 ? 0 : 16, 7);
        LinearLayout group = new LinearLayout(this); group.setOrientation(LinearLayout.VERTICAL); UiStyle.glass(group);
        root.addView(group, new LinearLayout.LayoutParams(-1, -2)); return group;
    }
    private void divider(LinearLayout group) {
        if (group.getChildCount() == 0) return;
        View line = new View(this); line.setBackgroundColor(UiStyle.colors(this).outline);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(1)); p.setMargins(dp(16), 0, dp(16), 0); group.addView(line, p);
    }
    private Switch toggle(LinearLayout group, String title, boolean checked, java.util.function.Consumer<Boolean> save) {
        divider(group); Switch control = new Switch(this); control.setText(title); control.setTextSize(16);
        control.setChecked(checked); control.setMinHeight(dp(54)); control.setPadding(dp(16), dp(6), dp(16), dp(6));
        control.setSwitchPadding(dp(12)); UiStyle.toggle(control);
        control.setOnCheckedChangeListener((button, value) -> save.accept(value));
        group.addView(control, new LinearLayout.LayoutParams(-1, -2)); return control;
    }
    private TextView row(LinearLayout group, String label, Runnable action) {
        divider(group); LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(54)); row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.addView(text(label, 16), new LinearLayout.LayoutParams(0, -2, 1));
        TextView value = text("", 13); UiStyle.muted(value); row.addView(value);
        android.widget.ImageView chevron = new android.widget.ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right_line);
        chevron.setImageTintList(android.content.res.ColorStateList.valueOf(UiStyle.colors(this).muted));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams arrow = new LinearLayout.LayoutParams(dp(16), dp(16)); arrow.leftMargin = dp(8);
        row.addView(chevron, arrow);
        row.setOnClickListener(v -> action.run()); UiStyle.pressable(row); group.addView(row); return value;
    }
    private TextView text(String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(UiStyle.colors(this).text); view.setIncludeFontPadding(false); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
