package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.util.function.BooleanSupplier;

/** Real settings activity in a disposable emulator; OEM capability is a presentation fixture. */
final class CapsuleSettingsDeviceCheck {
    private static int assertions;


    static String run(Instrumentation test, Bundle args) throws Exception {
        Context context = test.getTargetContext();
        FixtureIsolation.require(context);
        assertions = 0;
        String oldTheme = ThemeStore.mode(context);
        Activity activity = null;
        try {
            ThemeStore.setMode(context, args.getString("theme", "light"));
            activity = test.startActivitySync(new Intent(context, NotificationSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Activity page = activity;
            await(test, page::hasWindowFocus, "settings has focus");
            test.runOnMainSync(() -> {
                try {
                    var method = NotificationSettingsActivity.class.getDeclaredMethod("createAtomicControls");
                    method.setAccessible(true); method.invoke(page);
                    ((View) field(page, "atomicSection")).setVisibility(View.VISIBLE);
                    ((TextView) field(page, "atomicStatus")).setText("可用");
                    var available = NotificationSettingsActivity.class.getDeclaredField("atomicAvailable");
                    available.setAccessible(true); available.setBoolean(page, true);
                    var update = NotificationSettingsActivity.class.getDeclaredMethod("updateControls");
                    update.setAccessible(true); update.invoke(page);
                } catch (ReflectiveOperationException error) { throw new AssertionError(error); }
            });
            SystemClock.sleep(600); test.waitForIdleSync();
            var config = page.getResources().getConfiguration();
            require(config.screenWidthDp == Integer.parseInt(args.getString("widthDp", "400")), "activity width override applied");
            require(Math.abs(config.fontScale - Float.parseFloat(args.getString("font", "1.0"))) < .001f, "activity font override applied");
            require(find(page.getWindow().getDecorView(), "胶囊计时") == null, "accessibility timing entry removed");
            for (String title : new String[]{"Chrona 日程", "系统日程", "使用壁纸背景", "胶囊显示倒计时", "显示秒数",
                    "通知权限", "提醒频道", "准时提醒", "后台运行", "提醒诊断"})
                require(find(page.getWindow().getDecorView(), title) != null, "existing setting retained: " + title);
            TextView label = find(page.getWindow().getDecorView(), "原子通知权限");
            require(label != null, "atomic notification permission row retained");
            TextView status = (TextView) field(page, "atomicStatus");
            View row = (View) label.getParent();
            require(((View) field(page, "chronaAtomic")).isEnabled() == AgendaReminderPreferences.enabled(page),
                    "fixture applies production reminder/control availability");
            capture(test, context, args, "top");
            test.runOnMainSync(() -> row.requestRectangleOnScreen(new Rect(0, 0, row.getWidth(), row.getHeight()), true));
            SystemClock.sleep(300); test.waitForIdleSync();
            checkText(label); checkText(status);
            Rect labelBounds = bounds(label), valueBounds = bounds(status), rowBounds = bounds(row);
            require(labelBounds.right <= valueBounds.left, "label and status do not overlap");
            require(rowBounds.contains(labelBounds) && rowBounds.contains(valueBounds), "row contains both text areas");
            Rect visible = new Rect();
            require(row.getGlobalVisibleRect(visible) && visible.height() == row.getHeight(), "entire permission row reachable");
            float density = page.getResources().getDisplayMetrics().density;
            require(row.getHeight() >= Math.round(54 * density), "row retains minimum touch height");
            require(Math.abs(rowBounds.left - Math.round(20 * density)) <= 1, "row respects left content gutter");
            require(Math.abs(page.getWindow().getDecorView().getWidth() - rowBounds.right - Math.round(20 * density)) <= 1,
                    "row respects right content gutter");
            capture(test, context, args, "controls");
            return "PASS: " + assertions + " assertions; actual settings activity, removed accessibility timing entry, retained controls, per-activity width/font, themes and reachable permission row. OEM capability simulated; no permission changes.";
        } finally {
            if (activity != null) { Activity page = activity; test.runOnMainSync(page::finish); test.waitForIdleSync(); }
            ThemeStore.setMode(context, oldTheme);
        }
    }

    private static void checkText(TextView text) {
        require(text.getLayout() != null, "text laid out");
        require(text.getLayout().getHeight() <= text.getHeight() - text.getCompoundPaddingTop() - text.getCompoundPaddingBottom(), "text height not clipped");
        for (int i = 0; i < text.getLayout().getLineCount(); i++) require(text.getLayout().getEllipsisCount(i) == 0, "text not ellipsized");
    }
    private static Rect bounds(View view) { int[] p = new int[2]; view.getLocationOnScreen(p); return new Rect(p[0], p[1], p[0] + view.getWidth(), p[1] + view.getHeight()); }
    private static Object field(Object object, String name) throws ReflectiveOperationException { var field = object.getClass().getDeclaredField(name); field.setAccessible(true); return field.get(object); }
    private static TextView find(View view, String text) { if (view instanceof TextView value && text.contentEquals(value.getText())) return value; if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) { TextView result = find(group.getChildAt(i), text); if (result != null) return result; } return null; }
    private static void await(Instrumentation test, BooleanSupplier condition, String message) { long end = SystemClock.uptimeMillis() + 10000; do { test.waitForIdleSync(); if (condition.getAsBoolean()) return; SystemClock.sleep(50); } while (SystemClock.uptimeMillis() < end); throw new AssertionError(message); }
    private static void capture(Instrumentation test, Context context, Bundle args, String suffix) throws Exception { test.waitForIdleSync(); SystemClock.sleep(120); Bitmap image = test.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES).takeScreenshot(); require(image != null, "screenshot available"); try (FileOutputStream stream = new FileOutputStream(new File(context.getExternalFilesDir(null), "capsule-settings-" + args.getString("theme", "light") + "-" + suffix + ".png"))) { require(image.compress(Bitmap.CompressFormat.PNG, 100, stream), "screenshot saved"); } finally { image.recycle(); } }
    private static void require(boolean condition, String message) { assertions++; if (!condition) throw new AssertionError(message); }
}
