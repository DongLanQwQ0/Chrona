package com.donglan.chrona;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.widget.FrameLayout;

/** One progress value coordinates the home-only capture action and the mobile dock bounds. */
final class DashboardDockChrome {
    static final int START_DP = 36, HOME_END_DP = 104, BOTTOM_DP = 8;
    private static final float HIDDEN_SCALE = .6f;
    private static final float CENTER_WIDTH_GAIN = .06f;
    private static final int MAX_WIDTH_GAIN_DP = 16, EXIT_BLUR_DP = 18;
    static final int BLUR_GUTTER_DP = EXIT_BLUR_DP * 2;
    private static final long EXIT_DURATION_MS = 460L;
    private static final float CENTER_DURATION_MULTIPLIER = .5f;
    private final DockNavigationLayout dock;
    private final FrameLayout mobileHost;
    private final View captureHost, captureButton;
    private final int dockHeight;
    private float homeFraction, dockHomeFraction, dragDockFrom;
    /** 1 while the dock is collapsed by scrolling; the capture action leaves with it. */
    private float minimize;
    private boolean home;
    private ValueAnimator animator;

    DashboardDockChrome(DockNavigationLayout dock, FrameLayout mobileHost, View captureHost,
                        View captureButton, int dockHeightDp, boolean home) {
        this.dock = dock; this.mobileHost = mobileHost;
        this.captureHost = captureHost; this.captureButton = captureButton;
        dockHeight = dp(dockHeightDp);
        setHome(home);
    }

    float fraction() { return homeFraction; }

    void beginDrag() { cancel(); dragDockFrom = dockHomeFraction; }

    void cancel() {
        if (animator == null) return;
        ValueAnimator previous = animator; animator = null;
        previous.removeAllUpdateListeners(); previous.removeAllListeners(); previous.cancel();
    }

    void setHome(boolean value) {
        // A page may finish before its deliberately slower chrome exit.
        if (animator != null && home == value && UiMotion.isEnabled()) return;
        cancel(); home = value; apply(value ? 1f : 0f);
    }

    /** Page drags are reversible previews; the capture action is not clickable mid-gesture. */
    void drag(float from, boolean destinationHome, float progress) {
        cancel(); home = false;
        float t = Math.max(0f, Math.min(1f, progress));
        float target = destinationHome ? 1f : 0f;
        apply(from + (target - from) * t, dragDockFrom + (target - dragDockFrom) * t);
    }

    void animateHome(boolean value, long duration) {
        cancel(); home = value;
        float target = value ? 1f : 0f;
        captureButton.setEnabled(false);
        if (!UiMotion.isEnabled() || duration <= 0 || Math.abs(homeFraction - target) < .001f) {
            apply(target); return;
        }
        ValueAnimator motion = ValueAnimator.ofFloat(homeFraction, target);
        long chromeDuration = value ? duration : Math.max(duration, EXIT_DURATION_MS);
        long dockDuration = value ? chromeDuration : Math.max(1L, Math.round(chromeDuration * CENTER_DURATION_MULTIPLIER));
        float dockFrom = dockHomeFraction;
        android.view.animation.Interpolator curve = value ? UiMotion.SETTLE : input -> input * input * (3f - 2f * input);
        animator = motion; motion.setDuration(chromeDuration); motion.setInterpolator(curve);
        motion.addUpdateListener(frame -> {
            if (!UiMotion.isEnabled()) { setHome(value); return; }
            float progress = Math.min(1f, frame.getCurrentPlayTime() / (float) dockDuration);
            apply((Float) frame.getAnimatedValue(), dockFrom + (target - dockFrom) * curve.getInterpolation(progress));
        });
        motion.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator ignored) {
                if (animator != motion) return;
                animator = null; apply(target);
            }
        });
        motion.start();
    }

    /** Collapses the capture action together with the dock; 0 keeps it fully present. */
    void setMinimize(float value) {
        float clamped = Math.max(0f, Math.min(1f, value));
        if (Math.abs(clamped - minimize) < .001f) return;
        minimize = clamped;
        apply(homeFraction, dockHomeFraction);
    }

    private void apply(float fraction) {
        apply(fraction, fraction);
    }

    private void apply(float fraction, float dockFraction) {
        float previousHome = homeFraction, previousDock = dockHomeFraction;
        homeFraction = Math.max(0f, Math.min(1f, fraction));
        dockHomeFraction = Math.max(0f, Math.min(1f, dockFraction));
        float presence = homeFraction * homeFraction * (3f - 2f * homeFraction) * (1f - minimize);
        captureHost.setVisibility(homeFraction > 0f ? View.VISIBLE : View.INVISIBLE);
        captureHost.setAlpha(presence);
        // Scale a wrapper, leaving the button's own pressed-state animation intact.
        float scale = HIDDEN_SCALE + (1f - HIDDEN_SCALE) * presence;
        captureHost.setScaleX(scale); captureHost.setScaleY(scale);
        if (Build.VERSION.SDK_INT >= 31) {
            float blur = dp(EXIT_BLUR_DP) * (1f - presence);
            captureHost.setRenderEffect(homeFraction == 0f || blur < .01f ? null
                    : RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.DECAL));
        }
        boolean interactive = home && homeFraction == 1f && animator == null && minimize == 0f;
        captureButton.setEnabled(interactive);
        captureHost.setImportantForAccessibility(interactive ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        captureHost.invalidate();
        captureButton.invalidate();
        // Only the page-switch progress changes the dock's width; a collapse frame must not
        // trigger a measure/layout pass from inside the render step.
        if (homeFraction != previousHome || dockHomeFraction != previousDock) layoutMobile();
    }

    /** Remeasure only the dock; never stretch its labels or relayout the page behind it. */
    void layoutMobile() {
        if (mobileHost == null || mobileHost.getWidth() <= 0 || mobileHost.getHeight() <= 0) return;
        int available = mobileHost.getWidth() - mobileHost.getPaddingLeft() - mobileHost.getPaddingRight();
        float homeWidth = Math.max(1, available - dp(START_DP) - dp(HOME_END_DP));
        float centeredWidth = homeWidth + Math.min(dp(MAX_WIDTH_GAIN_DP), homeWidth * CENTER_WIDTH_GAIN);
        float homeCenter = mobileHost.getPaddingLeft() + dp(START_DP) + homeWidth / 2f;
        float centeredAt = mobileHost.getPaddingLeft() + available / 2f;
        float center = centeredAt + (homeCenter - centeredAt) * dockHomeFraction;
        int width = Math.round(centeredWidth + (homeWidth - centeredWidth) * dockHomeFraction);
        int left = Math.round(center - width / 2f);
        int right = left + width;
        int bottom = mobileHost.getHeight() - mobileHost.getPaddingBottom() - dp(BOTTOM_DP);
        if (dock.getMeasuredWidth() != width || dock.getMeasuredHeight() != dockHeight)
            dock.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(dockHeight, View.MeasureSpec.EXACTLY));
        if (dock.getLeft() != left || dock.getRight() != right || dock.getTop() != bottom - dockHeight
                || dock.getBottom() != bottom) {
            dock.layout(left, bottom - dockHeight, right, bottom);
            dock.invalidate();
        }
    }

    private int dp(int value) { return Math.round(value * dock.getResources().getDisplayMetrics().density); }
}
