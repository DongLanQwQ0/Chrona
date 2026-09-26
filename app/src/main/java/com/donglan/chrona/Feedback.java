package com.donglan.chrona;

import android.app.Activity;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Short-lived in-app message. A system toast is drawn by the platform in its own style, which is why
 * it looked out of place next to the app's glass surfaces; this one is part of the same vocabulary.
 * Never throws: it is used on failure paths that must still report something.
 */
public final class Feedback {
    private static final Object TAG = new Object();
    private static final int SHORT_MILLIS = 2_400;
    private static final int LONG_MILLIS = 4_200;

    private Feedback() {
    }

    public static void show(Activity activity, CharSequence message) {
        show(activity, message, SHORT_MILLIS);
    }

    public static void showLong(Activity activity, CharSequence message) {
        show(activity, message, LONG_MILLIS);
    }

    private static void show(Activity activity, CharSequence message, int durationMillis) {
        try {
            ViewGroup stage = activity.findViewById(android.R.id.content);
            if (stage == null || message == null) return;
            // One message at a time: a newer one replaces whatever is on screen.
            dismiss(stage.findViewWithTag(TAG));
            TextView pill = new TextView(activity);
            pill.setTag(TAG);
            pill.setText(message);
            pill.setTextSize(14);
            pill.setGravity(Gravity.CENTER_VERTICAL);
            pill.setPadding(dp(pill, 18), dp(pill, 12), dp(pill, 18), dp(pill, 12));
            pill.setTextColor(UiStyle.colors(activity).text);
            pill.setBackground(shape(activity));
            pill.setElevation(dp(pill, 12));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-2, -2,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            int margin = dp(pill, 20);
            params.setMargins(margin, 0, margin, dp(pill, 96) + navigationInset(activity));
            stage.addView(pill, params);
            if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
                pill.postDelayed(() -> dismiss(pill), durationMillis);
                return;
            }
            pill.setAlpha(0f);
            pill.setTranslationY(dp(pill, 18));
            pill.animate().alpha(1f).translationY(0f).setDuration(200)
                    .setInterpolator(new DecelerateInterpolator()).start();
            pill.postDelayed(() -> dismiss(pill), durationMillis);
        } catch (RuntimeException ignored) {
            // Reporting a result must never be the thing that crashes the screen.
        }
    }

    private static void dismiss(View pill) {
        if (pill == null || !(pill.getParent() instanceof ViewGroup)) return;
        ViewGroup parent = (ViewGroup) pill.getParent();
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            parent.removeView(pill);
            return;
        }
        pill.animate().cancel();
        pill.animate().alpha(0f).translationY(dp(pill, 14)).setDuration(160)
                .withEndAction(() -> parent.removeView(pill)).start();
    }

    private static int navigationInset(Activity activity) {
        if (Build.VERSION.SDK_INT < 30) return 0;
        WindowInsets insets = activity.getWindow().getDecorView().getRootWindowInsets();
        if (insets == null) return 0;
        return insets.getInsets(WindowInsets.Type.navigationBars()).bottom;
    }

    private static android.graphics.drawable.Drawable shape(Activity activity) {
        UiStyle.Palette colors = UiStyle.colors(activity);
        android.graphics.drawable.GradientDrawable sheet =
                new android.graphics.drawable.GradientDrawable(
                        android.graphics.drawable.GradientDrawable.Orientation.TL_BR,
                        new int[]{colors.surface, colors.surfaceAlt});
        sheet.setCornerRadius(dp(activity, 999));
        sheet.setStroke(dp(activity, 1), colors.outline);
        return sheet;
    }

    private static int dp(View view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }

    private static int dp(Activity activity, int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
