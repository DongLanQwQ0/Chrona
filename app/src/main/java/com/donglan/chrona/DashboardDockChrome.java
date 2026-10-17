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
    private final DockNavigationLayout dock;
    private final FrameLayout mobileHost;
    private final View captureHost, captureButton;
    private final int dockHeight;
    private float homeFraction;
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

    void cancel() {
        if (animator == null) return;
        ValueAnimator previous = animator; animator = null;
        previous.removeAllUpdateListeners(); previous.removeAllListeners(); previous.cancel();
    }

    void setHome(boolean value) {
        cancel(); home = value; apply(value ? 1f : 0f);
    }

    /** Page drags are reversible previews; the capture action is not clickable mid-gesture. */
    void drag(float from, boolean destinationHome, float progress) {
        cancel(); home = false;
        float t = Math.max(0f, Math.min(1f, progress));
        apply(from + ((destinationHome ? 1f : 0f) - from) * t);
    }

    void animateHome(boolean value, long duration) {
        cancel(); home = value;
        float target = value ? 1f : 0f;
        captureButton.setEnabled(false);
        if (!UiMotion.isEnabled() || duration <= 0 || Math.abs(homeFraction - target) < .001f) {
            apply(target); return;
        }
        ValueAnimator motion = ValueAnimator.ofFloat(homeFraction, target);
        animator = motion; motion.setDuration(duration); motion.setInterpolator(UiMotion.SETTLE);
        motion.addUpdateListener(frame -> {
            if (!UiMotion.isEnabled()) { setHome(value); return; }
            apply((Float) frame.getAnimatedValue());
        });
        motion.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator ignored) {
                if (animator != motion) return;
                animator = null; apply(target);
            }
        });
        motion.start();
    }

    private void apply(float fraction) {
        homeFraction = Math.max(0f, Math.min(1f, fraction));
        float presence = homeFraction * homeFraction * (3f - 2f * homeFraction);
        captureHost.setVisibility(homeFraction > 0f ? View.VISIBLE : View.INVISIBLE);
        captureHost.setAlpha(presence);
        // Scale a wrapper, leaving the button's own pressed-state animation intact.
        float scale = HIDDEN_SCALE + (1f - HIDDEN_SCALE) * presence;
        captureHost.setScaleX(scale); captureHost.setScaleY(scale);
        if (Build.VERSION.SDK_INT >= 31) {
            float blur = dp(EXIT_BLUR_DP) * (1f - presence);
            captureHost.setRenderEffect(homeFraction == 0f || blur < .01f ? null
                    : RenderEffect.createBlurEffect(blur, blur, Shader.TileMode.CLAMP));
        }
        boolean interactive = home && homeFraction == 1f && animator == null;
        captureButton.setEnabled(interactive);
        captureHost.setImportantForAccessibility(interactive ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        captureHost.invalidate();
        captureButton.invalidate();
        layoutMobile();
    }

    /** Remeasure only the dock; never stretch its labels or relayout the page behind it. */
    void layoutMobile() {
        if (mobileHost == null || mobileHost.getWidth() <= 0 || mobileHost.getHeight() <= 0) return;
        int available = mobileHost.getWidth() - mobileHost.getPaddingLeft() - mobileHost.getPaddingRight();
        float homeWidth = Math.max(1, available - dp(START_DP) - dp(HOME_END_DP));
        float centeredWidth = homeWidth + Math.min(dp(MAX_WIDTH_GAIN_DP), homeWidth * CENTER_WIDTH_GAIN);
        float homeCenter = mobileHost.getPaddingLeft() + dp(START_DP) + homeWidth / 2f;
        float centeredAt = mobileHost.getPaddingLeft() + available / 2f;
        float center = centeredAt + (homeCenter - centeredAt) * homeFraction;
        int width = Math.round(centeredWidth + (homeWidth - centeredWidth) * homeFraction);
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
