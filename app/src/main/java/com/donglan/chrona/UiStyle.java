package com.donglan.chrona;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
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
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.function.IntConsumer;

/** Semantic colors, shapes and motion for the native View interface. */
public final class UiStyle {
    /** One radius scale for the whole app: fields and buttons, cards, sheets, and chips. */
    static final int RADIUS_FIELD = 18;
    static final int RADIUS_CARD = 22;
    static final int RADIUS_PANEL = 28;
    static final int RADIUS_PILL = 24;

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
        int background, surface, surfaceAlt, text, muted, outline;
        String scheme = ThemeStore.color(context);
        int primary, container, onContainer;
        if (ThemeStore.WALLPAPER.equals(scheme) && ThemeStore.wallpaperAvailable()) {
            background = context.getColor(dark ? android.R.color.system_neutral1_900
                    : android.R.color.system_neutral1_10);
            surface = context.getColor(dark ? android.R.color.system_neutral1_800
                    : android.R.color.system_neutral1_50);
            surfaceAlt = context.getColor(dark ? android.R.color.system_neutral2_800
                    : android.R.color.system_neutral2_100);
            text = context.getColor(dark ? android.R.color.system_neutral1_50
                    : android.R.color.system_neutral1_900);
            muted = context.getColor(dark ? android.R.color.system_neutral2_200
                    : android.R.color.system_neutral2_700);
            outline = context.getColor(dark ? android.R.color.system_neutral2_700
                    : android.R.color.system_neutral2_200);
            primary = context.getColor(dark ? android.R.color.system_accent1_200
                    : android.R.color.system_accent1_700);
            container = context.getColor(dark ? android.R.color.system_accent1_800
                    : android.R.color.system_accent1_100);
            onContainer = context.getColor(dark ? android.R.color.system_accent1_100
                    : android.R.color.system_accent1_900);
        } else if (ThemeStore.BLUE.equals(scheme)) {
            background = dark ? 0xFF101722 : 0xFFF7F9FF;
            surface = dark ? 0xFF1C2636 : 0xFFFFFFFF;
            surfaceAlt = dark ? 0xFF27354B : 0xFFEAF0FB;
            text = dark ? 0xFFE4ECFA : 0xFF17263E;
            muted = dark ? 0xFFAFBED6 : 0xFF586B89;
            outline = dark ? 0xFF42536C : 0xFFD7E2F1;
            primary = dark ? 0xFFA9C7FF : 0xFF315CA7;
            container = dark ? 0xFF243F6A : 0xFFD8E5FF;
            onContainer = dark ? 0xFFD8E5FF : 0xFF17345F;
        } else if (ThemeStore.CORAL.equals(scheme)) {
            background = dark ? 0xFF201613 : 0xFFFFF8F5;
            surface = dark ? 0xFF30221D : 0xFFFFFFFF;
            surfaceAlt = dark ? 0xFF403029 : 0xFFFFEEE8;
            text = dark ? 0xFFF8E8E0 : 0xFF37231C;
            muted = dark ? 0xFFD6B9AB : 0xFF806457;
            outline = dark ? 0xFF644A3F : 0xFFF0DCD2;
            primary = dark ? 0xFFFFB4A1 : 0xFF9B4B32;
            container = dark ? 0xFF603426 : 0xFFFFDED4;
            onContainer = dark ? 0xFFFFDED4 : 0xFF622B1A;
        } else {
            background = dark ? 0xFF101B1B : 0xFFF6F8F5;
            surface = dark ? 0xFF1A2928 : Color.WHITE;
            surfaceAlt = dark ? 0xFF233431 : 0xFFEAF2EE;
            text = dark ? 0xFFE5F3EE : 0xFF172B2A;
            muted = dark ? 0xFFADC5BB : 0xFF566F67;
            outline = dark ? 0xFF40554D : 0xFFD9E6DE;
            primary = dark ? 0xFF80D8C9 : 0xFF006B60;
            container = dark ? 0xFF12483F : 0xFFC8F1E6;
            onContainer = dark ? 0xFFC8F1E6 : 0xFF004C44;
        }
        return new Palette(background, surface, surfaceAlt, text, muted, outline,
                primary, dark ? background : Color.WHITE, container, onContainer);
    }

    public static void page(Activity activity, LinearLayout root) {
        Palette colors = colors(activity);
        root.setBackgroundColor(colors.background);
        activity.getWindow().setStatusBarColor(colors.background);
        activity.getWindow().setNavigationBarColor(colors.background);
        activity.getWindow().getDecorView().setSystemUiVisibility(ThemeStore.dark(activity) ? 0
                : View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        root.setFitsSystemWindows(true);
    }

    public static void title(TextView view) {
        view.setTextColor(colors(view.getContext()).text);
        view.setTypeface(null, Typeface.BOLD);
        view.setLetterSpacing(-0.025f);
    }
    public static void muted(TextView view) { view.setTextColor(colors(view.getContext()).muted); }

    static void input(EditText view) {
        Palette colors = colors(view.getContext());
        view.setTextColor(colors.text);
        view.setTextSize(16);
        view.setHintTextColor(colors.muted);
        view.setBackground(shape(view, colors.surface, RADIUS_FIELD, colors.outline));
        view.setPadding(dp(view, 16), dp(view, 14), dp(view, 16), dp(view, 14));
    }

    /**
     * A dropdown trigger that reads as one of the form's fields, so a filled-in value lines up with
     * the fields around it. The marker is a compound drawable, which keeps it pinned to the right
     * edge however long the selected label is.
     */
    static void fieldTrigger(TextView view) {
        Palette colors = colors(view.getContext());
        view.setTextColor(colors.text);
        view.setTextSize(16);
        view.setTypeface(null, Typeface.NORMAL);
        view.setLetterSpacing(0f);
        view.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        view.setSingleLine(true);
        view.setMinHeight(dp(view, 52));
        view.setPadding(dp(view, 16), dp(view, 14), dp(view, 16), dp(view, 14));
        view.setCompoundDrawablePadding(dp(view, 8));
        view.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_chevron_down, 0);
        view.setCompoundDrawableTintList(ColorStateList.valueOf(colors.muted));
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(colors.primary, 34)),
                shape(view, colors.surface, RADIUS_FIELD, colors.outline), null));
    }

    public static void button(Button view, boolean primary) {
        Palette colors = colors(view.getContext());
        view.setAllCaps(false);
        view.setTextColor(primary ? colors.onPrimary : colors.primary);
        view.setTextSize(15);
        view.setTypeface(null, Typeface.BOLD);
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(
                primary ? 0x44FFFFFF : alpha(colors.primary, 34)),
                shape(view, primary ? colors.primary : colors.surface, RADIUS_FIELD,
                        primary ? colors.primary : colors.outline), null));
        view.setMinimumHeight(dp(view, 52));
        StateListAnimator press = new StateListAnimator();
        press.addState(new int[]{android.R.attr.state_pressed}, scale(view, 0.97f, 110));
        press.addState(new int[]{}, scale(view, 1f, 160));
        view.setStateListAnimator(press);
    }

    public static void card(View view) {
        Palette colors = colors(view.getContext());
        view.setBackground(shape(view, colors.surface, RADIUS_CARD, colors.outline));
        view.setElevation(dp(view, 2));
    }

    /** Translucent surface over the softly colored backdrop; text stays opaque. */
    public static void glass(View view) {
        Palette colors = colors(view.getContext());
        boolean dark = ThemeStore.dark(view.getContext());
        GradientDrawable sheet = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(colors.surface, dark ? 226 : 238),
                        alpha(colors.surfaceAlt, dark ? 180 : 205)});
        sheet.setCornerRadius(dp(view, RADIUS_PANEL));
        sheet.setStroke(dp(view, 1), alpha(dark ? colors.outline : Color.WHITE,
                dark ? 210 : 225));
        view.setBackground(sheet);
        view.setElevation(dp(view, 8));
    }

    static void pressable(View view) {
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(
                alpha(colors(view.getContext()).primary, 32)), null,
                shape(view, Color.WHITE, RADIUS_CARD, Color.TRANSPARENT)));
    }

    /** Selectable surface: chips use the pill radius, dialog rows the field radius. */
    static void choice(View view, boolean selected, int radius) {
        Palette colors = colors(view.getContext());
        view.setBackground(new RippleDrawable(ColorStateList.valueOf(alpha(colors.primary, 34)),
                shape(view, selected ? colors.primaryContainer : colors.surface, radius,
                        selected ? colors.primaryContainer : colors.outline), null));
    }

    static void pill(View view, boolean selected) {
        choice(view, selected, RADIUS_PILL);
    }

    /** The single marker used by every list of choices: a trailing check on the selected row. */
    static String marked(String label, boolean selected) {
        return selected ? label + "   ✓" : label;
    }

    static void enter(View view, int index) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        view.setAlpha(0f);
        view.setTranslationY(dp(view, 12));
        view.animate().alpha(1f).translationY(0f).setStartDelay(Math.min(index, 6) * 35L)
                .setDuration(240).start();
    }

    public static void pop(View view) {
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

    public static void addSpaced(LinearLayout parent, View view, int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(view, top), 0, dp(view, bottom));
        parent.addView(view, params);
    }

    public static void back(Activity activity, LinearLayout parent) {
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

    /** A themed, scrollable choice sheet shared by settings and filters. */
    public static void choiceDialog(Activity activity, String title, String[] options,
            int selected, IntConsumer onChoice) {
        Dialog dialog = dialog(activity);
        LinearLayout panel = dialogPanel(activity, title);
        ScrollView scroll = new ScrollView(activity);
        LinearLayout choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextView item = new TextView(activity);
            item.setText(marked(options[i], i == selected));
            item.setTextSize(16);
            item.setGravity(Gravity.CENTER_VERTICAL);
            item.setMinHeight(dp(item, 52));
            item.setPadding(dp(item, 18), 0, dp(item, 18), 0);
            item.setTextColor(i == selected ? colors(activity).onPrimaryContainer
                    : colors(activity).text);
            choice(item, i == selected, RADIUS_FIELD);
            item.setOnClickListener(view -> {
                dialog.dismiss();
                onChoice.accept(index);
            });
            addSpaced(choices, item, 2, 5);
        }
        scroll.addView(choices);
        int maxHeight = (int) (activity.getResources().getDisplayMetrics().heightPixels * .58f);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1,
                Math.min(maxHeight, options.length * dp(scroll, 60))));
        showDialog(dialog, panel);
    }

    public static void confirmDialog(Activity activity, String title, String message,
            String positive, Runnable onConfirm) {
        Dialog dialog = dialog(activity);
        LinearLayout panel = dialogPanel(activity, title);
        TextView explanation = new TextView(activity);
        explanation.setText(message);
        explanation.setTextSize(15);
        muted(explanation);
        addSpaced(panel, explanation, 4, 20);
        LinearLayout actions = new LinearLayout(activity);
        Button cancel = new Button(activity);
        cancel.setText("取消");
        button(cancel, false);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(cancel, 52), 1));
        Button confirm = new Button(activity);
        confirm.setText(positive);
        button(confirm, true);
        confirm.setOnClickListener(view -> {
            dialog.dismiss();
            onConfirm.run();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(confirm, 52), 1);
        params.setMargins(dp(confirm, 8), 0, 0, 0);
        actions.addView(confirm, params);
        panel.addView(actions);
        showDialog(dialog, panel);
    }

    private static Dialog dialog(Activity activity) {
        Dialog dialog = new Dialog(activity);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.48f;
            window.setAttributes(params);
        }
        return dialog;
    }

    private static LinearLayout dialogPanel(Activity activity, String title) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(panel, 20), dp(panel, 20), dp(panel, 20), dp(panel, 20));
        glass(panel);
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextSize(21);
        title(heading);
        addSpaced(panel, heading, 0, 12);
        return panel;
    }

    private static void showDialog(Dialog dialog, View content) {
        dialog.setContentView(content);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) window.setLayout(
                Math.min((int) (content.getResources().getDisplayMetrics().widthPixels * .90f),
                        dp(content, 460)), -2);
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        // A sheet that fades and settles into place instead of appearing between two frames.
        content.setAlpha(0f);
        content.setScaleX(0.93f);
        content.setScaleY(0.93f);
        content.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(210)
                .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }

    /** Keep controls clear of Android 15+ system bars while the backdrop draws behind them. */
    public static void applyInsets(View stage, View safeContent) {
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
