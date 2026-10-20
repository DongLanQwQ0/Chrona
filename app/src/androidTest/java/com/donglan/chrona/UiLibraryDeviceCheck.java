package com.donglan.chrona;

import android.app.Activity;
import android.app.Dialog;
import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.time.LocalDate;
import java.util.function.BooleanSupplier;

/** Real modal callbacks and nested restoration; synthetic validation package, no task writes. */
final class UiLibraryDeviceCheck {
    private static int checks;

    static String run(Instrumentation test, Bundle args) throws Exception {
        var context = test.getTargetContext();
        if (!context.getPackageName().equals("com.donglan.chrona.followup.validation"))
            throw new IllegalStateException("Isolated package only");
        String oldTheme = ThemeStore.mode(context), theme = args.getString("theme", "light");
        checks = 0;
        ThemeStore.setMode(context, theme);
        Activity owner = test.startActivitySync(new Intent(context, AppearanceActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        java.util.ArrayList<Dialog> owned = new java.util.ArrayList<>();
        try {
            test.waitForIdleSync();
            waitFor(test, owner::hasWindowFocus, "test page is actually foreground");
            int[] confirmed = {0};
            main(test, () -> UiDialogs.confirmDialog(owner, "取消验收", "取消保持原值", "应用", () -> confirmed[0]++));
            click(test, "取消验收", "取消");
            require(confirmed[0] == 0, "cancel preserves value");
            main(test, () -> UiDialogs.confirmDialog(owner, "确认验收", "提交一次", "应用", () -> confirmed[0]++));
            capture(test, "ui-library-confirm-" + theme);
            click(test, "确认验收", "应用");
            require(confirmed[0] == 1, "confirm dispatches once");

            String[] edited = {null};
            main(test, () -> UiDialogs.textEditorDialog(owner, "编辑验收", "初始值", "保存", "保留完整内容", value -> edited[0] = value));
            waitFor(test, () -> dialogRoot(owner, "编辑验收") != null, "editor shown");
            main(test, () -> find(dialogRoot(owner, "编辑验收"), EditText.class).setText("修改后的内容\n第二行"));
            capture(test, "ui-library-editor-" + theme);
            click(test, "编辑验收", "保存");
            require("修改后的内容\n第二行".equals(edited[0]), "editor preserves multiline callback value");

            LocalDate initial = LocalDate.of(2026, 10, 20);
            LocalDate[] date = {null};
            main(test, () -> UiDialogs.dateDialog(owner, "日期验收", initial, value -> date[0] = value));
            capture(test, "ui-library-date-" + theme);
            click(test, "日期验收", "确定");
            require(initial.equals(date[0]), "date picker retains initial date");
            int[] minutes = {-1};
            main(test, () -> UiDialogs.timeDialog(owner, "时间验收", 23 * 60 + 42, value -> minutes[0] = value));
            capture(test, "ui-library-time-" + theme);
            click(test, "时间验收", "保存");
            require(minutes[0] == 23 * 60 + 42, "time picker retains hour and minute");

            LinearLayout[] parent = {null};
            main(test, () -> {
                Dialog dialog = new Dialog(owner); owned.add(dialog);
                parent[0] = UiDialogs.dialogPanel(owner, "外层验收");
                Button open = new Button(owner); open.setText("打开内层"); UiStyle.button(open, true);
                open.setOnClickListener(v -> UiDialogs.confirmDialog(owner, "内层验收", "返回后恢复外层", "确定", () -> confirmed[0]++));
                parent[0].addView(open); UiDialogs.showFloatingDialog(dialog, parent[0]);
            });
            click(test, "外层验收", "打开内层");
            waitFor(test, () -> dialogRoot(owner, "内层验收") != null && parent[0].getAlpha() == .4f, "nested parent subdued");
            capture(test, "ui-library-nested-" + theme);
            click(test, "内层验收", "取消");
            waitFor(test, () -> parent[0].getAlpha() == 1f, "nested parent restored");
            require(confirmed[0] == 1, "nested cancellation never confirms");
            main(test, () -> owned.get(0).dismiss());
            waitFor(test, () -> Math.abs(owner.findViewById(android.R.id.content).getScaleX() - 1f) < .001f,
                    "last dialog restores page scale");
            return "PASS: " + checks + " native modal checks, " + theme + "; confirm/cancel/editor/date/time/nested restoration";
        } finally {
            main(test, () -> {
                for (Dialog dialog : owned) dialog.dismiss();
                owner.finish();
            });
            ThemeStore.setMode(context, oldTheme);
        }
    }

    private static void click(Instrumentation test, String title, String label) throws Exception {
        waitFor(test, () -> {
            View root = dialogRoot(null, title);
            return root != null && text(root, label) != null;
        }, "visible action " + label);
        main(test, () -> {
            TextView button = text(dialogRoot(null, title), label);
            require(button.isShown() && button.isEnabled(), "action visible and enabled");
            require(button.performClick(), "action dispatched");
        });
        test.waitForIdleSync();
    }
    private static View dialogRoot(Activity owner, String title) {
        for (View root : android.view.inspector.WindowInspector.getGlobalWindowViews())
            if (root.hasWindowFocus() && (owner == null || root != owner.getWindow().getDecorView())
                    && text(root, title) != null) return root;
        return null;
    }
    private static TextView text(View view, String value) {
        if (view instanceof TextView label && value.contentEquals(label.getText())) return label;
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            TextView result = text(group.getChildAt(i), value); if (result != null) return result;
        }
        return null;
    }
    private static <T extends View> T find(View view, Class<T> type) {
        if (type.isInstance(view)) return type.cast(view);
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            T result = find(group.getChildAt(i), type); if (result != null) return result;
        }
        return null;
    }
    private static void waitFor(Instrumentation test, BooleanSupplier ready, String label) throws Exception {
        long until = SystemClock.uptimeMillis() + 5000;
        boolean[] ok = {false};
        do {
            main(test, () -> ok[0] = ready.getAsBoolean());
            if (ok[0]) { require(true, label); return; }
            SystemClock.sleep(50);
        } while (SystemClock.uptimeMillis() < until);
        throw new AssertionError(label);
    }
    private static void main(Instrumentation test, Runnable action) {
        Throwable[] failure = {null};
        test.runOnMainSync(() -> { try { action.run(); } catch (Throwable t) { failure[0] = t; } });
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }
    private static void capture(Instrumentation test, String name) throws Exception {
        test.waitForIdleSync(); SystemClock.sleep(300);
        Bitmap bitmap = test.getUiAutomation().takeScreenshot();
        try (FileOutputStream out = new FileOutputStream(new File(test.getTargetContext().getExternalFilesDir(null), name + ".png"))) {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out);
        } finally { bitmap.recycle(); }
    }
    private static void require(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
}
