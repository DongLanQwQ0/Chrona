package com.donglan.chrona;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Handler;
import android.os.Looper;
import android.os.OperationCanceledException;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.donglan.chrona.calendar.CalendarStore;

import java.lang.ref.WeakReference;

/** Read-only reminder prerequisites, with independent system-calendar checks. */
public final class ReminderDiagnosticsActivity extends Activity {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private CancellationSignal pendingRead;
    private ScrollView page;
    private TextView permissions;
    private TextView calendar;
    private TextView reminderStatus, notificationStatus, channelStatus, exactStatus;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        LinearLayout root = SettingsPageLayout.content(this);
        SettingsPageLayout.header(this, root, "提醒诊断");

        section(root, "Chrona 原生提醒");
        LinearLayout nativeChecks = new LinearLayout(this);
        nativeChecks.setOrientation(LinearLayout.VERTICAL);
        UiStyle.glass(nativeChecks);
        UiStyle.addSpaced(root, nativeChecks, 0, 10);
        reminderStatus = row(nativeChecks, "提前 30 分钟提醒", () ->
                startActivity(new Intent(this, NotificationSettingsActivity.class)));
        notificationStatus = row(nativeChecks, "通知权限", () -> open(
                NotificationDeliveryStatus.appSettings(this), "未找到可打开的通知设置"));
        channelStatus = row(nativeChecks, "提醒频道", this::openReminderChannelSettings);
        exactStatus = row(nativeChecks, "准时提醒", () -> {
            if (Build.VERSION.SDK_INT >= 31) open(
                    new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                            Uri.fromParts("package", getPackageName(), null)),
                    "未找到可打开的准时提醒设置");
            else Feedback.show(this, "当前系统无需单独授权");
        });
        row(nativeChecks, "后台运行", this::openBackgroundSettings).setText("查看系统限制");

        section(root, "系统日历提醒");
        permissions = card(root, "日历权限");
        calendar = card(root, "拾时本地日历");
        TextView reminder = card(root, "系统日历独立发送");
        reminder.setText("请在系统日历核对日程的提醒时间，并允许日历应用的通知和后台运行。\n\n"
                + "日程写入成功表示已保存。可设置一个临近提醒，锁屏确认实际送达。");
        button(root, "打开系统日历", true, () -> {
            Uri time = ContentUris.withAppendedId(
                    CalendarContract.CONTENT_URI.buildUpon().appendPath("time").build(),
                    System.currentTimeMillis());
            open(new Intent(Intent.ACTION_VIEW, time), "未找到可打开的系统日历应用");
        });
        button(root, "拾时应用权限设置", false, () -> open(
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", getPackageName(), null)),
                "未找到可打开的应用设置"));

        SettingsPageLayout.show(this, root, state == null);
        page = (ScrollView) root.getParent();
        if (state != null) {
            int scroll = state.getInt("scroll_y");
            page.post(() -> page.scrollTo(0, scroll));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        NotificationDeliveryStatus delivery = NotificationDeliveryStatus.read(this);
        reminderStatus.setText(AgendaReminderPreferences.enabled(this) ? "已开启" : "未开启");
        notificationStatus.setText(delivery.appAllowed ? "已允许" : "未允许");
        channelStatus.setText(delivery.channelSummary());
        exactStatus.setText(AgendaReminder.exactAllowed(this) ? "已允许" : "未允许，可能延迟");
        CalendarStore store = new CalendarStore(this);
        boolean read = store.hasReadPermission();
        permissions.setText("读取日历：" + (read ? "已允许" : "未允许")
                + "\n写入日历：" + (store.hasWritePermission() ? "已允许" : "未允许"));
        if (!read) {
            calendar.setText("未获读取日历权限，无法检查拾时本地日历。可点击下方入口查看应用权限。");
            return;
        }
        calendar.setText("正在读取本地日历…");
        CancellationSignal signal = new CancellationSignal();
        pendingRead = signal;
        Context app = getApplicationContext();
        WeakReference<ReminderDiagnosticsActivity> owner = new WeakReference<>(this);
        Handler handler = mainHandler;
        new Thread(() -> {
            String result = readCalendar(app, signal);
            if (signal.isCanceled()) return;
            handler.post(() -> {
                ReminderDiagnosticsActivity activity = owner.get();
                if (activity == null || activity.isFinishing() || activity.isDestroyed()
                        || signal.isCanceled() || activity.pendingRead != signal) return;
                activity.calendar.setText(result);
            });
        }, "chrona-reminder-diagnostics").start();
    }

    @Override protected void onPause() {
        if (pendingRead != null) pendingRead.cancel();
        pendingRead = null;
        mainHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("scroll_y", page.getScrollY());
    }

    private static String readCalendar(Context context, CancellationSignal signal) {
        try {
            signal.throwIfCanceled();
            // Reuse the exact account/name identity maintained by CalendarStore, without creating it.
            Long id = new CalendarStore(context).findCalendarId();
            signal.throwIfCanceled();
            if (id == null) return "未找到拾时本地日历。首次成功写入日程后会创建；已创建的日历也可能被移除。";
            try (Cursor cursor = context.getContentResolver().query(
                    ContentUris.withAppendedId(Calendars.CONTENT_URI, id), null,
                    null, null, null, signal)) {
                if (cursor == null) return "系统日历服务未返回数据，暂时无法检查。返回此页可重新读取。";
                if (!cursor.moveToFirst()) return "拾时本地日历已被移除。";
                return "本地日历：已找到\n显示状态：" + flag(cursor, Calendars.VISIBLE)
                        + "\n事件同步标记：" + flag(cursor, Calendars.SYNC_EVENTS)
                        + "\n\n这些状态由系统日历提供，无法确认通知是否开启或提醒是否送达。"
                        + "本地日历的同步标记不表示已备份到云端。";
            }
        } catch (OperationCanceledException exception) {
            return "";
        } catch (SecurityException exception) {
            return "读取日历被系统拒绝，权限可能已变更。请查看应用权限后返回此页。";
        } catch (RuntimeException exception) {
            return "系统日历读取失败，暂时无法检查。返回此页可重试，或打开系统日历核对。";
        }
    }

    private static String flag(Cursor cursor, String column) {
        int index = cursor.getColumnIndex(column);
        if (index < 0 || cursor.isNull(index)) return "系统未提供";
        int value = cursor.getInt(index);
        return value == 1 ? "开启" : value == 0 ? "关闭" : "系统返回未知状态";
    }

    private TextView card(LinearLayout root, String heading) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(14), dp(16), dp(16));
        UiStyle.glass(box);
        TextView label = text(heading, 18);
        UiStyle.title(label);
        box.addView(label);
        TextView body = text("", 14);
        UiStyle.muted(body);
        UiStyle.addSpaced(box, body, 8, 0);
        UiStyle.addSpaced(root, box, 0, 10);
        return body;
    }

    private TextView text(String value, int size) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(UiStyle.colors(this).text);
        view.setIncludeFontPadding(false);
        return view;
    }

    private void section(LinearLayout root, String heading) {
        TextView title = text(heading, 12);
        UiStyle.muted(title);
        UiStyle.addSpaced(root, title, root.getChildCount() == 1 ? 0 : 16, 7);
    }

    private TextView row(LinearLayout group, String label, Runnable action) {
        if (group.getChildCount() > 0) {
            View line = new View(this);
            line.setBackgroundColor(UiStyle.colors(this).outline);
            LinearLayout.LayoutParams divider = new LinearLayout.LayoutParams(-1, dp(1));
            divider.setMargins(dp(16), 0, dp(16), 0);
            group.addView(line, divider);
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(54));
        row.setPadding(dp(16), dp(10), dp(16), dp(10));
        row.addView(text(label, 16), new LinearLayout.LayoutParams(0, -2, 1));
        TextView status = text("", 13);
        UiStyle.muted(status);
        row.addView(status);
        android.widget.ImageView chevron = new android.widget.ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_right_line);
        chevron.setImageTintList(android.content.res.ColorStateList.valueOf(UiStyle.colors(this).muted));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams arrow = new LinearLayout.LayoutParams(dp(16), dp(16));
        arrow.leftMargin = dp(8);
        row.addView(chevron, arrow);
        row.setOnClickListener(view -> action.run());
        UiStyle.pressable(row);
        group.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return status;
    }

    private void button(LinearLayout root, String label, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setContentDescription(label);
        UiStyle.button(button, primary);
        button.setMinHeight(dp(48));
        button.setOnClickListener(view -> action.run());
        UiStyle.addSpaced(root, button, 0, 8);
    }

    private void open(Intent intent, String unavailable) {
        try { startActivity(intent); }
        catch (ActivityNotFoundException | SecurityException exception) {
            Feedback.show(this, unavailable);
        }
    }

    private void openReminderChannelSettings() {
        NotificationDeliveryStatus delivery = NotificationDeliveryStatus.read(this);
        open(delivery.channelCreated ? NotificationDeliveryStatus.channelSettings(this)
                : NotificationDeliveryStatus.appSettings(this), "未找到可打开的提醒频道设置");
    }

    private void openBackgroundSettings() {
        if (VivoAtomicNotification.supportedManufacturer())
            Feedback.show(this, "电量 → 后台耗电管理 → 允许后台耗电");
        open(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", getPackageName(), null)), "未找到可打开的应用设置");
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
