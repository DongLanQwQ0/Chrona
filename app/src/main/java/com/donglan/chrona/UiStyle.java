package com.donglan.chrona;

import android.animation.ValueAnimator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Small shared visual system for the app's native views. */
final class UiStyle {
    static final int INK = 0xFF172B2A;
    static final int MUTED = 0xFF60736F;
    static final int TEAL = 0xFF006B60;
    static final int PAPER = 0xFFF5F7F2;
    static final int SURFACE = Color.WHITE;

    private UiStyle() { }

    static void page(Activity activity, LinearLayout root) {
        root.setBackgroundColor(PAPER);
        activity.getWindow().setStatusBarColor(PAPER);
        activity.getWindow().setNavigationBarColor(PAPER);
        activity.getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
    }

    static void title(TextView view) {
        view.setTextColor(INK);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        view.setLetterSpacing(-0.025f);
    }

    static void muted(TextView view) { view.setTextColor(MUTED); }

    static void input(EditText view) {
        view.setTextColor(INK);
        view.setHintTextColor(MUTED);
        view.setBackground(shape(SURFACE, 18, 0xFFE0E9E3));
        int horizontal = dp(view, 14);
        int vertical = dp(view, 12);
        view.setPadding(horizontal, vertical, horizontal, vertical);
    }

    static void button(Button view, boolean primary) {
        view.setAllCaps(false);
        view.setTextColor(primary ? Color.WHITE : TEAL);
        view.setTextSize(15);
        view.setTypeface(null, android.graphics.Typeface.BOLD);
        GradientDrawable background = shape(primary ? TEAL : SURFACE, 16,
                primary ? TEAL : 0xFFD8E7DF);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(
                primary ? 0x55FFFFFF : 0x22006B60), background, null));
        view.setMinimumHeight(dp(view, 48));
        StateListAnimator press = new StateListAnimator();
        press.addState(new int[]{android.R.attr.state_pressed}, scale(view, 0.98f, 110));
        press.addState(new int[]{}, scale(view, 1f, 150));
        view.setStateListAnimator(press);
    }

    static void card(View view) {
        view.setBackground(shape(SURFACE, 20, 0xFFE4ECE6));
        view.setElevation(dp(view, 2));
    }

    static void enter(View view, int index) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 10));
        view.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(index, 8) * 35L)
                .setDuration(220).start();
    }

    static ObjectAnimator pulse(View view) {
        if (!ValueAnimator.areAnimatorsEnabled()) return null;
        ObjectAnimator pulse = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.72f, 1f);
        pulse.setDuration(1800);
        pulse.setStartDelay(250);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();
        return pulse;
    }

    static void addSpaced(LinearLayout parent, View view, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(view, top), 0, dp(view, bottom));
        parent.addView(view, params);
    }

    private static GradientDrawable shape(int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(radius * android.content.res.Resources.getSystem()
                .getDisplayMetrics().density);
        drawable.setStroke(1, stroke);
        return drawable;
    }

    private static AnimatorSet scale(View view, float value, long duration) {
        AnimatorSet pair = new AnimatorSet();
        pair.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, value),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, value));
        pair.setDuration(duration);
        return pair;
    }

    private static int dp(View view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }
}
