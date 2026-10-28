package com.donglan.chrona;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import java.util.ArrayList;
import java.util.List;

/** Real Android animator scaling on the same controls; no production preferences or task writes. */
final class PressFeedbackDeviceCheck {
    static String run(Instrumentation test, Bundle args) throws Exception {
        var context = test.getTargetContext();
        if (!context.getPackageName().equals("com.donglan.chrona.followup.validation"))
            throw new IllegalStateException("Isolated package only");
        String original = Settings.Global.getString(context.getContentResolver(), "animator_duration_scale");
        Activity page = null;
        try {
            page = test.startActivitySync(new Intent(context, AppearanceActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            test.waitForIdleSync();
            scale(test, "0");
            Activity owner = page;
            View[] pressed = new View[3], targets = new View[3];
            test.runOnMainSync(() -> {
                LinearLayout root = new LinearLayout(owner); root.setOrientation(LinearLayout.VERTICAL);
                int padding = Math.round(28 * owner.getResources().getDisplayMetrics().density);
                root.setPadding(padding, padding, padding, padding);
                Button button = new Button(owner); button.setText("按钮"); UiStyle.button(button, true);
                button.setOnClickListener(v -> {}); root.addView(button); pressed[0] = targets[0] = button;
                LinearLayout card = new LinearLayout(owner); UiStyle.card(card); card.setClickable(true);
                card.setOnClickListener(v -> {}); Button label = new Button(owner); label.setText("卡片"); label.setEnabled(false);
                card.addView(label); root.addView(card); PressFeedback.apply(card); pressed[1] = targets[1] = card;
                HeaderActionGroup group = new HeaderActionGroup(owner, 48);
                View action = UiStyle.navigationIcon(owner, R.drawable.ic_refresh, "刷新", () -> {});
                group.addAction(action, 48); group.finish(); root.addView(group); pressed[2] = group; targets[2] = action;
                SettingsPageLayout.show(owner, root, false); PressFeedback.install(root);
            });
            test.waitForIdleSync(); SystemClock.sleep(150);
            Object[] identities = new Object[3];
            test.runOnMainSync(() -> { for (int i = 0; i < 3; i++) identities[i] = pressed[i].getStateListAnimator(); });
            for (int i = 0; i < 3; i++) gesture(test, targets[i], pressed[i], false, new float[]{1.05f, 1.08f, 1.10f}[i]);
            scale(test, "1");
            StringBuilder report = new StringBuilder("PASS: same-instance button/card/header-group; ");
            for (int i = 0; i < 3; i++) {
                int index = i;
                test.runOnMainSync(() -> require(pressed[index].getStateListAnimator() == identities[index], "same animator instance"));
                report.append(gesture(test, targets[i], pressed[i], true, new float[]{1.05f, 1.08f, 1.10f}[i])).append(';');
            }
            return report.toString();
        } finally {
            if (original == null) shell(test, "settings delete global animator_duration_scale"); else scale(test, original);
            if (page != null) { Activity owner = page; test.runOnMainSync(owner::finish); }
        }
    }
    private static String gesture(Instrumentation test, View target, View surface, boolean animate, float expected) throws Exception {
        List<Float> downFrames = new ArrayList<>(), upFrames = new ArrayList<>();
        final boolean[] releasing = {false};
        android.view.ViewTreeObserver.OnPreDrawListener observer = () -> {
            (releasing[0] ? upFrames : downFrames).add(surface.getScaleX()); return true;
        };
        int[] at = new int[2];
        test.runOnMainSync(() -> { target.getLocationOnScreen(at); surface.getViewTreeObserver().addOnPreDrawListener(observer); });
        long down = SystemClock.uptimeMillis();
        try {
            pointer(test, target, at, down, MotionEvent.ACTION_DOWN); SystemClock.sleep(200);
            test.runOnMainSync(() -> {
                require(Math.abs(surface.getScaleX() - expected) < .002f, "held scale " + expected);
                require(Math.abs(surface.getScaleY() - expected) < .002f, "held Y scale " + expected);
                releasing[0] = true;
            });
            pointer(test, target, at, down, MotionEvent.ACTION_UP); SystemClock.sleep(280);
            test.runOnMainSync(() -> {
                require(Math.abs(surface.getScaleX() - 1) < .001f && Math.abs(surface.getScaleY() - 1) < .001f, "release identity");
                if (animate) {
                    require(downFrames.stream().anyMatch(v -> v > 1.0001f && v < expected - .0001f), "rendered DOWN intermediate " + downFrames);
                    require(upFrames.stream().anyMatch(v -> v > 1.0001f && v < expected - .0001f), "rendered UP intermediate " + upFrames);
                } else require(downFrames.stream().noneMatch(v -> v > 1.0001f && v < expected - .0001f), "disabled snaps without frames");
            });
            return expected + " DOWN=" + downFrames + " UP=" + upFrames;
        } finally {
            pointer(test, target, at, down, MotionEvent.ACTION_CANCEL);
            test.runOnMainSync(() -> surface.getViewTreeObserver().removeOnPreDrawListener(observer));
        }
    }
    private static void pointer(Instrumentation test, View target, int[] at, long down, int action) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                at[0] + target.getWidth() / 2f, at[1] + target.getHeight() / 2f, 0);
        try { test.sendPointerSync(event); } finally { event.recycle(); }
    }
    private static void scale(Instrumentation test, String value) throws Exception {
        shell(test, "settings put global animator_duration_scale " + Float.parseFloat(value));
        boolean enabled = Float.parseFloat(value) != 0;
        long until = SystemClock.uptimeMillis() + 3000;
        while (UiMotion.isEnabled() != enabled && SystemClock.uptimeMillis() < until) SystemClock.sleep(25);
        require(UiMotion.isEnabled() == enabled, "system animator scale applied");
    }
    private static void shell(Instrumentation test, String command) throws Exception {
        try (var fd = test.getUiAutomation().executeShellCommand(command);
             var in = new android.os.ParcelFileDescriptor.AutoCloseInputStream(fd)) { in.readAllBytes(); }
    }
    private static void require(boolean value, String label) { if (!value) throw new AssertionError(label); }
}
