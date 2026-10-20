package com.donglan.chrona;

import android.app.Instrumentation;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.SystemClock;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import java.io.File;
import java.io.FileOutputStream;

/** Runs only in the independent validation package; no task or calendar fixtures. */
final class CornerDeviceCheck {
    static String run(Instrumentation test) throws Exception {
        if (!test.getTargetContext().getPackageName().endsWith(".validation"))
            throw new IllegalStateException("Use the validation package");
        AppearanceActivity activity = (AppearanceActivity) test.startActivitySync(new Intent(
                test.getTargetContext(), AppearanceActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        test.waitForIdleSync();
        SeekBar slider = find(activity.getWindow().getDecorView());
        if (slider == null) throw new AssertionError("Missing corner slider");
        test.runOnMainSync(() -> {
            View current = slider;
            int top = 0;
            while (current.getParent() instanceof View parent && !(parent instanceof ScrollView)) {
                top += current.getTop(); current = parent;
            }
            if (current.getParent() instanceof ScrollView scroll) scroll.scrollTo(0, Math.max(0, top - 180));
        });
        for (int level : new int[]{1, 2, 3, 4, 5, 4}) {
            final int expected = level;
            test.runOnMainSync(() -> {
                while (slider.getProgress() != expected - 1) {
                    int key = slider.getProgress() < expected - 1 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
                    slider.onKeyDown(key, new KeyEvent(KeyEvent.ACTION_DOWN, key));
                }
                if (ThemeStore.cornerLevel(activity) != expected) throw new AssertionError("Slider persistence " + expected);
                if (find(activity.getWindow().getDecorView()) != slider) throw new AssertionError("Slider recreated");
                if (UiStyle.radius(activity, UiStyle.RADIUS_CARD) != new int[]{5,10,16,22,30}[expected-1])
                    throw new AssertionError("Card radius " + expected);
            });
            test.waitForIdleSync(); SystemClock.sleep(180);
            screenshot(test, "corner-" + level);
        }
        View card = card(activity.getWindow().getDecorView());
        if (card == null) throw new AssertionError("No interactive card");
        test.runOnMainSync(() -> card.setPressed(true));
        SystemClock.sleep(350);
        test.runOnMainSync(() -> {
            if (Math.abs(card.getScaleX() - 1.08f) > .002f) throw new AssertionError("Pressed card scale " + card.getScaleX());
            card.setPressed(false);
        });
        SystemClock.sleep(400);
        test.runOnMainSync(() -> {
            if (Math.abs(card.getScaleX() - 1f) > .002f) throw new AssertionError("Release scale");
        });
        return "PASS: five slider levels, persisted values, unchanged slider/view tree, card 1.08 held / 1.0 released; saved screenshots";
    }
    private static SeekBar find(View view) {
        if (view instanceof SeekBar slider && "圆角大小".contentEquals(slider.getContentDescription())) return slider;
        if (view instanceof ViewGroup group) for (int i=0; i<group.getChildCount(); i++) {
            SeekBar found = find(group.getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private static View card(View view) {
        if (view.isClickable() && UiSurfaces.isCardSurface(view)) return view;
        if (view instanceof ViewGroup group) for (int i=0; i<group.getChildCount(); i++) {
            View found = card(group.getChildAt(i)); if (found != null) return found;
        }
        return null;
    }
    private static void screenshot(Instrumentation test, String name) throws Exception {
        Bitmap image = test.getUiAutomation().takeScreenshot();
        File file = new File(test.getTargetContext().getExternalFilesDir(null), name + ".png");
        try (FileOutputStream output = new FileOutputStream(file)) { image.compress(Bitmap.CompressFormat.PNG, 100, output); }
        finally { image.recycle(); }
    }
}
