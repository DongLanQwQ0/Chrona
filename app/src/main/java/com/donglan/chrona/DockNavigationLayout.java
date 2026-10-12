package com.donglan.chrona;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.VelocityTracker;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntConsumer;

/** A shared selection surface that follows a finger across the mobile dock. */
final class DockNavigationLayout extends LinearLayout {
    private final GradientDrawable highlight = new GradientDrawable();
    private final int touchSlop;
    private final IntConsumer select;
    private final Runnable finished;
    private final DockSpring spring = new DockSpring();
    private static final int HIGHLIGHT_ALPHA = 82;
    private VelocityTracker fingerVelocity;
    private float downX, downY, fingerPosition;
    private long followFrameTime;
    private boolean followPosted;
    private final Runnable followFinger = this::advanceFingerFollow;
    private float visualPosition = Float.NaN;
    private float bodyDragFrom = Float.NaN;
    private float settleFrom, settleTo, settleFraction, settleBaseFraction;
    private boolean pageSettling;
    private boolean coastSelection;
    private int selectedIndex, tapIndex, labelIndex = -1;
    private boolean tracking, dragging, ignoreTouch, tapLeftBounds;
    private final Runnable finishTouch = this::finishTouch;

    private void finishTouch() {
        // The child posts its normal PerformClick before this runnable. Detaching it earlier
        // removes that callback and silently loses the tap.
        if (!tracking && !coastSelection) finished.run();
    }
    private final Runnable longPress = () -> {
        if (tracking && !dragging && !ignoreTouch) {
            beginDrag(downX);
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    };

    DockNavigationLayout(Context context, IntConsumer select, Runnable finished) {
        super(context);
        this.select = select;
        this.finished = finished;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false); setClipToPadding(false);
        highlight.setCornerRadius(UiStyle.RADIUS_PANEL * getResources().getDisplayMetrics().density);
    }

    boolean isTrackingTouch() { return tracking; }
    boolean isAnimating() { return followPosted; }

    void setSelectedIndex(int index) {
        selectedIndex = index;
        if (Float.isNaN(visualPosition)) { visualPosition = index; spring.position = spring.target = index; }
        labelIndex = -1;
        if (!dragging) updateLabels(Math.round(visualPosition));
        invalidate();
    }

    void showPageProgress(int from, int to, float progress) {
        if (dragging) return;
        pageSettling = false;
        float start = Float.isNaN(bodyDragFrom) ? from : bodyDragFrom;
        animateTo(start + (to - start) * Math.max(0f, Math.min(1f, progress)));
    }

    void beginBodyDrag() {
        coastSelection = spring.coasting = false;
        cancelFingerFollow();
        spring.velocity = 0f; spring.target = spring.position;
        pageSettling = false;
        bodyDragFrom = visualPosition;
        press(false);
    }

    /** Use the page's animator, starting at the actual highlight position after a dock drag. */
    void beginPageSettle(int destination) {
        cancelFingerFollow();
        settleFrom = Float.isNaN(visualPosition) ? selectedIndex : visualPosition;
        settleTo = destination;
        settleFraction = settleBaseFraction = 0f;
        bodyDragFrom = Float.NaN;
        pageSettling = true;
        animateTo(destination);
    }

    void updatePageSettle(float fraction) {
        settleFraction = fraction;
        if (dragging || coastSelection || !pageSettling) return;
        float progress = settleBaseFraction >= 1f ? 1f
                : Math.max(0f, Math.min(1f, (fraction - settleBaseFraction) / (1f - settleBaseFraction)));
        animateTo(settleFrom + (settleTo - settleFrom) * progress);
    }

    void finishPageSelection(int index) {
        pageSettling = false;
        bodyDragFrom = Float.NaN;
        setSelectedIndex(index);
        if (!dragging) animateTo(index);
    }

    private void setVisualPosition(float position) {
        visualPosition = position;
        updateLabels(Math.max(0, Math.min(getChildCount() - 1, Math.round(position))));
        invalidate();
    }

    private void resumeOrReturnHighlight() {
        if (pageSettling) {
            // A dock drag may have moved it while the page was already animating.
            settleFrom = visualPosition;
            settleBaseFraction = settleFraction;
        } else {
            animateTo(selectedIndex);
        }
    }

    private float center(int index) {
        View child = getChildAt(index);
        return child == null ? 0 : (child.getLeft() + child.getRight()) / 2f;
    }

    private int nearest(float x) {
        int result = 0;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < getChildCount(); i++) {
            float candidate = Math.abs(x - center(i));
            if (candidate < distance) { result = i; distance = candidate; }
        }
        return result;
    }

    private float centerAt(float position) {
        if (getChildCount() < 2) return center(0);
        int from = Math.max(0, Math.min(getChildCount() - 2, (int) Math.floor(position)));
        int to = from + 1;
        return center(from) + (center(to) - center(from)) * (position - from);
    }

    private float positionAt(float x) {
        for (int i = 0; i < getChildCount() - 1; i++) {
            float a = center(i), b = center(i + 1);
            if (a != b && x >= Math.min(a, b) && x <= Math.max(a, b))
                return i + (x - a) / (b - a);
        }
        return nearest(x);
    }

    private void moveHighlight(float x) {
        if (getChildCount() == 0) return;
        // Clamp using physical bounds, which also works with RTL child ordering.
        float left = Math.min(center(0), center(getChildCount() - 1));
        float right = Math.max(center(0), center(getChildCount() - 1));
        fingerPosition = positionAt(Math.max(left, Math.min(right, x)));
        animateTo(fingerPosition);
    }
    private void animateTo(float position) {
        if (coastSelection) return;
        spring.target = position;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            cancelFingerFollow(); spring.snap(); applySpring();
        } else if (!followPosted) {
            followFrameTime = android.os.SystemClock.uptimeMillis();
            followPosted = true;
            postOnAnimation(followFinger);
        }
    }

    private void advanceFingerFollow() {
        followPosted = false;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            if (coastSelection) { spring.target = Math.round(spring.landing()); commitCoast(); }
            spring.snap(); applySpring();
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        long elapsed = Math.max(0L, Math.min(64L, now - followFrameTime));
        followFrameTime = now;
        boolean moving = spring.step(elapsed / 1000f); applySpring();
        if (coastSelection && !spring.coasting) commitCoast();
        if (moving && !followPosted) {
            followPosted = true;
            postOnAnimation(followFinger);
        }
    }
    private void commitCoast() {
        coastSelection = false;
        int destination = Math.max(0, Math.min(getChildCount() - 1, Math.round(spring.target)));
        select.accept(destination);
        if (!pageSettling) animateTo(destination);
        post(finishTouch);
    }
    private void applySpring() {
        setVisualPosition(spring.position); setScaleX(spring.bar); setScaleY(spring.bar);
    }
    private void press(boolean pressed) { spring.press(pressed); animateTo(spring.target); }

    private void cancelFingerFollow() {
        removeCallbacks(followFinger);
        followPosted = false;
    }

    private void updateLabels(int active) {
        if (labelIndex == active) return;
        labelIndex = active;
        UiStyle.Palette palette = UiStyle.colors(getContext());
        for (int i = 0; i < getChildCount(); i++) {
            View cell = getChildAt(i);
            if (cell instanceof FrameLayout wrapper) cell = wrapper.getChildAt(0);
            if (cell instanceof TextView label) {
                int color = i == active ? palette.primary : palette.muted;
                label.setTextColor(color);
                label.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
            }
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        View cell = getChildAt(selectedIndex);
        if (cell != null) {
            float x = centerAt(visualPosition);
            float width = cell.getWidth() * spring.scale * spring.width;
            float height = cell.getHeight() * spring.scale;
            highlight.setCornerRadius(UiStyle.RADIUS_PANEL * getResources().getDisplayMetrics().density * spring.scale);
            int left = Math.round(x - width / 2f), top = Math.round((cell.getTop() + cell.getBottom() - height) / 2f);
            float right = x + width / 2f;
            // Pin the inner end once half the standard width crosses the board. The outer
            // end continues stretching with momentum; it is not capped at half the width.
            float standardWidth = cell.getWidth();
            left = Math.min(left, Math.round(getWidth() - standardWidth / 2f));
            right = Math.max(right, standardWidth / 2f);
            // Alpha blends the accent over the dock's existing sampled acrylic background.
            highlight.setColor((UiStyle.colors(getContext()).primary & 0x00FFFFFF) | (HIGHLIGHT_ALPHA << 24));
            highlight.setBounds(left, top, Math.round(right), Math.round(top + height));
            highlight.draw(canvas);
        }
        super.dispatchDraw(canvas);
    }

    private void cancelChildTouch() {
        MotionEvent cancel = MotionEvent.obtain(android.os.SystemClock.uptimeMillis(),
                android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, downX, downY, 0);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    private void beginDrag(float x) {
        removeCallbacks(longPress);
        cancelChildTouch();
        dragging = true;
        getParent().requestDisallowInterceptTouchEvent(true);
        moveHighlight(x);
    }

    private void recordVelocity(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) { recycleVelocity(); return; }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            recycleVelocity(); fingerVelocity = VelocityTracker.obtain();
        }
        if (fingerVelocity == null) return;
        // Use physical screen samples; the dock's own 1.1x scaling must not attenuate velocity.
        MotionEvent screen = MotionEvent.obtain(event.getDownTime(), event.getEventTime(),
                event.getActionMasked(), event.getRawX(), event.getRawY(), 0);
        fingerVelocity.addMovement(screen); screen.recycle();
    }
    private void recycleVelocity() {
        if (fingerVelocity != null) { fingerVelocity.recycle(); fingerVelocity = null; }
    }
    private float releasedVelocity() {
        if (fingerVelocity == null || getChildCount() < 2) return 0f;
        fingerVelocity.computeCurrentVelocity(1000);
        float pixelsPerSlot = (center(1) - center(0)) * getScaleX();
        return Math.abs(pixelsPerSlot) < 1f ? 0f : fingerVelocity.getXVelocity() / pixelsPerSlot;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        recordVelocity(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            coastSelection = spring.coasting = false;
            cancelFingerFollow();
            removeCallbacks(finishTouch);
            tracking = true;
            ignoreTouch = false;
            dragging = false;
            tapLeftBounds = false;
            downX = event.getX();
            downY = event.getY();
            press(true);
            getParent().requestDisallowInterceptTouchEvent(true);
            postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            recycleVelocity();
            cancelFingerFollow();
            removeCallbacks(longPress);
            if (!dragging) cancelChildTouch();
            dragging = false;
            ignoreTouch = true;
            press(false);
            resumeOrReturnHighlight();
        } else if (action == MotionEvent.ACTION_MOVE && tracking && !ignoreTouch) {
            if (!insideDock(event)) tapLeftBounds = true;
            float dx = Math.abs(event.getX() - downX);
            float dy = Math.abs(event.getY() - downY);
            if (!dragging && (dx > touchSlop || dy > touchSlop)) {
                removeCallbacks(longPress);
                if (dx > dy * 1.25f) beginDrag(event.getX());
                // Vertical movement within a button is still an ordinary Android tap.
            }
            if (dragging) moveHighlight(event.getX());
        }
        boolean owned = dragging || ignoreTouch;
        boolean handled = owned || super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPress);
            boolean commit = dragging && action == MotionEvent.ACTION_UP;
            float releaseSpeed = commit ? releasedVelocity() : 0f;
            cancelFingerFollow();
            tracking = false;
            dragging = false;
            ignoreTouch = false;
            press(false);
            if (commit) {
                pageSettling = false;
                coastSelection = true;
                spring.coast(releaseSpeed, Math.max(0, getChildCount() - 1));
                if (!ValueAnimator.areAnimatorsEnabled()) {
                    spring.target = Math.round(spring.landing()); commitCoast(); spring.snap(); applySpring();
                }
            } else if (owned) {
                resumeOrReturnHighlight();
            }
            invalidate();
            recycleVelocity();
            post(finishTouch);
        }
        return handled;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        // Padding and gaps are also valid starts for a dock gesture.
        if (event.getActionMasked() == MotionEvent.ACTION_UP && !dragging && !ignoreTouch
                && !tapLeftBounds && insideDock(event)) {
            tapIndex = nearest(event.getX());
            performClick();
        }
        return true;
    }

    private boolean insideDock(MotionEvent event) {
        return event.getX() >= -touchSlop && event.getX() <= getWidth() + touchSlop
                && event.getY() >= -touchSlop && event.getY() <= getHeight() + touchSlop;
    }

    @Override public boolean performClick() {
        super.performClick();
        if (getChildCount() > 0) select.accept(tapIndex);
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        coastSelection = spring.coasting = false;
        recycleVelocity();
        cancelFingerFollow();
        removeCallbacks(longPress);
        removeCallbacks(finishTouch);
        spring.press(false); spring.target = spring.position; spring.snap(); applySpring();
        tracking = dragging = ignoreTouch = false;
        super.onDetachedFromWindow();
    }
}
