package com.donglan.chrona;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
import android.widget.HorizontalScrollView;

/** A pager whose content tracks the finger, then visibly settles on the selected page. */
final class CandidatePagerScrollView extends HorizontalScrollView {
    private final int touchSlop;
    private final int systemEdgeInset;
    private int pageWidth;
    private int pageCount;
    private float downX;
    private float downY;
    /** Where the drag itself starts: the touch slop is consumed instead of being applied. */
    private float dragOriginX;
    private int startScrollX;
    private int startPage;
    private boolean tracking;
    private boolean startedAtSystemEdge;
    private boolean startedAtBoundary;
    private boolean yieldingToTaskPager;
    private ValueAnimator settleAnimator;

    CandidatePagerScrollView(Context context) {
        super(context);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        systemEdgeInset = dp(32);
        setHorizontalScrollBarEnabled(false);
        setFillViewport(true);
        setClipToPadding(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
    }

    void setPages(int pageWidth, int pageCount) {
        this.pageWidth = Math.max(1, pageWidth);
        this.pageCount = Math.max(1, pageCount);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            if (settleAnimator != null) settleAnimator.cancel();
            downX = event.getX();
            downY = event.getY();
            dragOriginX = downX;
            startScrollX = getScrollX();
            startPage = clamp(Math.round(startScrollX / (float) pageWidth));
            int maxScroll = Math.max(0, (pageCount - 1) * pageWidth);
            startedAtBoundary = pageCount > 1
                    && (startScrollX <= touchSlop || startScrollX >= maxScroll - touchSlop);
            startedAtSystemEdge = downX <= systemEdgeInset
                    || downX >= getWidth() - systemEdgeInset;
            tracking = false;
            yieldingToTaskPager = false;
            return super.dispatchTouchEvent(event);
        }
        if (yieldingToTaskPager) {
            if (action == MotionEvent.ACTION_CANCEL) {
                yieldingToTaskPager = false;
                startedAtBoundary = false;
                return super.dispatchTouchEvent(event);
            }
            yieldingToTaskPager = false;
        }
        if (action == MotionEvent.ACTION_MOVE && !tracking && !startedAtSystemEdge) {
            float dx = event.getX() - downX;
            float dy = event.getY() - downY;
            if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                tracking = true;
                // Track from the frame the gesture is recognised so the first followed frame is
                // still at the resting offset, not already displaced by the touch slop.
                dragOriginX = event.getX();
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                super.dispatchTouchEvent(cancel);
                cancel.recycle();
                if (getParent() != null)
                    getParent().requestDisallowInterceptTouchEvent(true);
            }
        }
        if (!tracking) return super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_MOVE) {
            int maxScroll = Math.max(0, (pageCount - 1) * pageWidth);
            scrollTo(Math.max(0, Math.min(maxScroll,
                    startScrollX - Math.round(event.getX() - dragOriginX))), 0);
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            float dx = event.getX() - downX;
            int threshold = Math.max(dp(48), Math.round(getWidth() * .14f));
            int target = Math.abs(dx) >= threshold
                    ? startPage + (dx < 0 ? 1 : -1)
                    : Math.round(getScrollX() / (float) pageWidth);
            settleOn(clamp(target));
            finishTracking();
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            settleOn(clamp(Math.round(getScrollX() / (float) pageWidth)));
            finishTracking();
            return true;
        }
        return super.dispatchTouchEvent(event);
    }

    private void settleOn(int page) {
        int targetX = page * pageWidth;
        int startX = getScrollX();
        if (startX == targetX || !ValueAnimator.areAnimatorsEnabled()) {
            scrollTo(targetX, 0);
            return;
        }
        if (settleAnimator != null) settleAnimator.cancel();
        // A release that never reached the threshold stays on its page; give that glide-back more
        // time and a stronger ease-out so it reads as settling instead of snapping.
        boolean recoil = page == startPage;
        int distance = Math.abs(targetX - startX);
        long duration = Math.max(recoil ? 240L : 260L,
                Math.min(380L, Math.round(380f * distance / pageWidth)));
        ValueAnimator animator = ValueAnimator.ofInt(startX, targetX);
        settleAnimator = animator;
        animator.setDuration(duration);
        animator.setInterpolator(new DecelerateInterpolator(recoil ? 1.9f : 1.5f));
        animator.addUpdateListener(value -> scrollTo((int) value.getAnimatedValue(), 0));
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (settleAnimator == animator) settleAnimator = null;
            }
        });
        animator.start();
    }

    private void finishTracking() {
        tracking = false;
        startedAtBoundary = false;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    boolean canYieldToTaskPager(float fingerDistanceX) {
        if (!startedAtBoundary || startedAtSystemEdge
                || Math.abs(fingerDistanceX) <= touchSlop) return false;
        int maxScroll = Math.max(0, (pageCount - 1) * pageWidth);
        return fingerDistanceX > 0 && startScrollX <= touchSlop
                || fingerDistanceX < 0 && startScrollX >= maxScroll - touchSlop;
    }

    void prepareForTaskPagerHandoff() {
        if (settleAnimator != null) settleAnimator.cancel();
        tracking = false;
        yieldingToTaskPager = true;
        if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
    }

    private int clamp(int page) {
        return Math.max(0, Math.min(pageCount - 1, page));
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
