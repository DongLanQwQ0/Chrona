package com.donglan.chrona;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Semantic colors, shapes and motion for the native View interface. */
final class UiStyle {
    static final class Palette {
        final int background, surface, surfaceAlt, text, muted, outline, primary, onPrimary;
        final int primaryContainer, onPrimaryContainer;
        Palette(int background, int surface, int surfaceAlt, int text, int muted, int outline,
                int primary, int onPrimary, int primaryContainer, int onPrimaryContainer) {
            this.background = background;
            this.surface = surface;
            this.surfaceAlt = surfaceAlt;
            this.text = text;
            this.muted = muted;
            this.outline = outline;
            this.primary = primary;
            this.onPrimary = onPrimary;
            this.primaryContainer = primaryContainer;
            this.onPrimaryContainer = onPrimaryContainer;
        }
    }

    private UiStyle() { }

    static Palette colors(Context context) {
        boolean dark = ThemeStore.dark(context);
        int background = dark ? 0xFF101B1B : 0xFFF6F8F5;
        int surface = dark ? 0xFF1A2928 : Color.WHITE;
        int surfaceAlt = dark ? 0xFF233431 : 0xFFEAF2EE;
        int text = dark ? 0xFFE5F3EE : 0xFF172B2A;
        int muted = dark ? 0xFFADC5BB : 0xFF566F67;
        int outline = dark ? 0xFF40554D : 0xFFD9E6DE;
        String scheme = ThemeStore.color(context);
        int primary, container, onContainer;
        if (ThemeStore.WALLPAPER.equals(scheme) && ThemeStore.wallpaperAvailable()) {
            primary = context.getColor(dark ? android.R.color.system_accent1_200
                    : android.R.color.system_accent1_700);
            container = context.getColor(dark ? android.R.color.system_accent1_800
                    : android.R.color.system_accent1_100);
            onContainer = dark ? 0xFFF7FAF8 : 0xFF172B2A;
        } else if (ThemeStore.BLUE.equals(scheme)) {
            primary = dark ? 0xFFA9C7FF : 0xFF315CA7;
            container = dark ? 0xFF243F6A : 0xFFD8E5FF;
            onContainer = dark ? 0xFFD8E5FF : 0xFF17345F;
        } else if (ThemeStore.CORAL.equals(scheme)) {
            primary = dark ? 0xFFFFB4A1 : 0xFF9B4B32;
            container = dark ? 0xFF603426 : 0xFFFFDED4;
            onContainer = dark ? 0xFFFFDED4 : 0xFF622B1A;
        } else {
            primary = dark ? 0xFF80D8C9 : 0xFF006B60;
            container = dark ? 0xFF12483F : 0xFFC8F1E6;
            onContainer = dark ? 0xFFC8F1E6 : 0xFF004C44;
        }
        return new Palette(background, surface, surfaceAlt, text, muted, outline,
                primary, dark ? 0xFF122321 : Color.WHITE, container, onContainer);
    }

    static void page(Activity activity, LinearLayout root) {
        Palette colors = colors(activity);
        root.setBackgroundColor(colors.background);
        activity.getWindow().setStatusBarColor(colors.background);
        activity.getWindow().setNavigationBarColor(colors.background);
        activity.getWindow().getDecorView().setSystemUiVisibility(ThemeStore.dark(activity) ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root.setFitsSystemWindows(true);
    }

    static void title(TextView view) {
        view.setTextColor(colors(view.getContext()).text);
        view.setTypeface(null, Typeface.BOLD);
        view.setLetterSpacing(-0.025f);
    }
    static void muted(TextView view) { view.setTextColor(colors(view.getContext()).muted); }

    static void input(EditText view) {
        Palette colors = colors(view.getContext());
        view.setTextColor(colors.text);
        view.setHintTextColor(colors.muted);
        view.setBackground(shape(view, colors.surface, 18, colors.outline));
        view.setPadding(dp(view, 16), dp(view, 14), dp(view, 16), dp(view, 14));
    }

    static void button(Button view, boolean primary) {
        Palette colors = colors(view.getContext());
        view.setAllCaps(false);
        view.setTextColor(primary ? colors.onPrimary : colors.primary);
        view.setTextSize(15);
        view.setTypeface(null, Typeface.BOLD);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(
                primary ? 0x44FFFFFF : 0x22006B60),
                shape(view, primary ? colors.primary : colors.surface, 18,
                        primary ? colors.primary : colors.outline), null));
        view.setMinimumHeight(dp(view, 52));
        StateListAnimator press = new StateListAnimator();
        press.addState(new int[]{android.R.attr.state_pressed}, scale(view, 0.97f, 110));
        press.addState(new int[]{}, scale(view, 1f, 160));
        view.setStateListAnimator(press);
    }

    static void card(View view) {
        Palette colors = colors(view.getContext());
        view.setBackground(shape(view, colors.surface, 22, colors.outline));
        view.setElevation(dp(view, 2));
    }

    /** Translucent surface over the softly colored backdrop; text stays opaque. */
    static void glass(View view) {
        Palette colors = colors(view.getContext());
        boolean dark = ThemeStore.dark(view.getContext());
        GradientDrawable sheet = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(colors.surface, dark ? 226 : 238),
                        alpha(colors.surfaceAlt, dark ? 180 : 205)});
        sheet.setCornerRadius(dp(view, 28));
        sheet.setStroke(dp(view, 1), alpha(dark ? colors.outline : Color.WHITE,
                dark ? 210 : 225));
        view.setBackground(sheet);
        view.setElevation(dp(view, 8));
    }

    static void pressable(View view) {
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(
                alpha(colors(view.getContext()).primary, 32)), null,
                shape(view, Color.WHITE, 22, Color.TRANSPARENT)));
    }

    static void pill(View view, boolean selected) {
        Palette colors = colors(view.getContext());
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(0x22006B60),
                shape(view, selected ? colors.primaryContainer : colors.surface, 24,
                        selected ? colors.primaryContainer : colors.outline), null));
    }

    static void enter(View view, int index) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 12));
        view.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(index, 6) * 35L)
                .setDuration(240).start();
    }

    static void pop(View view) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        view.setScaleX(0.94f);
        view.setScaleY(0.94f);
        view.animate().scaleX(1f).scaleY(1f).setDuration(230).start();
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

    static void back(Activity activity, LinearLayout parent) {
        TextView back = new TextView(activity);
        back.setText("←  返回");
        back.setTextSize(15);
        back.setTextColor(colors(activity).primary);
        back.setTypeface(null, Typeface.BOLD);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setMinimumHeight(dp(back, 48));
        back.setContentDescription("返回上一页");
        back.setOnClickListener(view -> activity.finish());
        addSpaced(parent, back, 0, 8);
    }

    /** Keep controls clear of Android 15+ system bars while the backdrop draws behind them. */
    static void applyInsets(View stage, View safeContent) {
        applyInsets(stage, safeContent, null);
    }

    static void applyInsets(View stage, View safeContent, View floating) {
        if (Build.VERSION.SDK_INT < 35) return;
        safeContent.setFitsSystemWindows(false);
        int left = safeContent.getPaddingLeft();
        int top = safeContent.getPaddingTop();
        int right = safeContent.getPaddingRight();
        int bottom = safeContent.getPaddingBottom();
        int floatBottom = floating == null ? 0
                : ((FrameLayout.LayoutParams) floating.getLayoutParams()).bottomMargin;
        stage.setOnApplyWindowInsetsListener((view, insets) -> {
            android.graphics.Insets bars = insets.getInsets(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int keyboard = insets.getInsets(WindowInsets.Type.ime()).bottom;
            int safeBottom = Math.max(bars.bottom, keyboard);
            safeContent.setPadding(left + bars.left, top + bars.top,
                    right + bars.right, bottom + safeBottom);
            if (floating != null) {
                FrameLayout.LayoutParams params =
                        (FrameLayout.LayoutParams) floating.getLayoutParams();
                params.bottomMargin = floatBottom + bars.bottom;
                params.rightMargin = Math.max(params.rightMargin, bars.right);
                floating.setLayoutParams(params);
            }
            return insets;
        });
        stage.requestApplyInsets();
    }

    private static GradientDrawable shape(View view, int fill, int radius, int stroke) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(view, radius));
        drawable.setStroke(dp(view, 1), stroke);
        return drawable;
    }

    private static int alpha(int color, int value) {
        return Color.argb(value, Color.red(color), Color.green(color), Color.blue(color));
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
