package com.donglan.chrona;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;

/** One progress value coordinates the home-only capture action and the mobile dock bounds. */
final class DashboardDockChrome {
    static final int START_DP = 36, HOME_END_DP = 104, CENTERED_END_DP = 36, BOTTOM_DP = 8;
    private static final float HIDDEN_SCALE = .6f;
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

    void animateHome(boolean value, long duration, Interpolator curve) {
        cancel(); home = value;
        float target = value ? 1f : 0f;
        captureButton.setEnabled(false);
        if (!UiMotion.isEnabled() || duration <= 0 || Math.abs(homeFraction - target) < .001f) {
            apply(target); return;
        }
        ValueAnimator motion = ValueAnimator.ofFloat(homeFraction, target);
        animator = motion; motion.setDuration(duration); motion.setInterpolator(curve);
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
        captureHost.setVisibility(homeFraction > 0f ? View.VISIBLE : View.INVISIBLE);
        captureHost.setAlpha(homeFraction);
        // Scale a wrapper, leaving the button's own pressed-state animation intact.
        float scale = HIDDEN_SCALE + (1f - HIDDEN_SCALE) * homeFraction;
        captureHost.setScaleX(scale); captureHost.setScaleY(scale);
        boolean interactive = home && homeFraction == 1f && animator == null;
        captureButton.setEnabled(interactive);
        captureHost.setImportantForAccessibility(interactive ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        captureHost.invalidate();
        layoutMobile();
    }

    /** Remeasure only the dock; never stretch its labels or relayout the page behind it. */
    void layoutMobile() {
        if (mobileHost == null || mobileHost.getWidth() <= 0 || mobileHost.getHeight() <= 0) return;
        int left = mobileHost.getPaddingLeft() + dp(START_DP);
        int rightInset = Math.round(dp(CENTERED_END_DP) + dp(HOME_END_DP - CENTERED_END_DP) * homeFraction);
        int right = Math.max(left + 1, mobileHost.getWidth() - mobileHost.getPaddingRight() - rightInset);
        int bottom = mobileHost.getHeight() - mobileHost.getPaddingBottom() - dp(BOTTOM_DP);
        int width = right - left;
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
