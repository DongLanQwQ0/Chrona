package com.donglan.chrona;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.DecelerateInterpolator;
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
    private static final float FOLLOW_TIME_CONSTANT_MILLIS = 45f;
    private float downX, downY, fingerPosition;
    private long followFrameTime;
    private boolean followPosted;
    private final Runnable followFinger = this::advanceFingerFollow;
    private float visualPosition = Float.NaN;
    private float bodyDragFrom = Float.NaN;
    private float settleFrom, settleTo, settleFraction, settleBaseFraction;
    private boolean pageSettling;
    private ValueAnimator recoil;
    private int selectedIndex, tapIndex, labelIndex = -1;
    private boolean tracking, dragging, ignoreTouch, tapLeftBounds;
    private final Runnable finishTouch = this::finishTouch;

    private void finishTouch() {
        // The child posts its normal PerformClick before this runnable. Detaching it earlier
        // removes that callback and silently loses the tap.
        if (!tracking) finished.run();
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
        highlight.setCornerRadius(UiStyle.RADIUS_PANEL * getResources().getDisplayMetrics().density);
    }

    boolean isTrackingTouch() { return tracking; }

    void setSelectedIndex(int index) {
        selectedIndex = index;
        if (Float.isNaN(visualPosition)) visualPosition = index;
        labelIndex = -1;
        if (!dragging) updateLabels(Math.round(visualPosition));
        invalidate();
    }

    void showPageProgress(int from, int to, float progress) {
        if (dragging) return;
        cancelRecoil();
        pageSettling = false;
        float start = Float.isNaN(bodyDragFrom) ? from : bodyDragFrom;
        setVisualPosition(start + (to - start) * Math.max(0f, Math.min(1f, progress)));
    }

    void beginBodyDrag() {
        cancelFingerFollow();
        cancelRecoil();
        pageSettling = false;
        bodyDragFrom = visualPosition;
    }

    /** Use the page's animator, starting at the actual highlight position after a dock drag. */
    void beginPageSettle(int destination) {
        cancelFingerFollow();
        cancelRecoil();
        settleFrom = Float.isNaN(visualPosition) ? selectedIndex : visualPosition;
        settleTo = destination;
        settleFraction = settleBaseFraction = 0f;
        bodyDragFrom = Float.NaN;
        pageSettling = true;
    }

    void updatePageSettle(float fraction) {
        settleFraction = fraction;
        if (dragging || !pageSettling) return;
        float progress = settleBaseFraction >= 1f ? 1f
                : Math.max(0f, Math.min(1f, (fraction - settleBaseFraction) / (1f - settleBaseFraction)));
        setVisualPosition(settleFrom + (settleTo - settleFrom) * progress);
    }

    void finishPageSelection(int index) {
        pageSettling = false;
        bodyDragFrom = Float.NaN;
        setSelectedIndex(index);
        if (!dragging) setVisualPosition(index);
    }

    private void setVisualPosition(float position) {
        visualPosition = Math.max(0f, Math.min(Math.max(0, getChildCount() - 1), position));
        updateLabels(Math.round(visualPosition));
        invalidate();
    }

    private void cancelRecoil() {
        if (recoil != null) { recoil.cancel(); recoil = null; }
    }

    private void resumeOrReturnHighlight() {
        if (pageSettling) {
            // A dock drag may have moved it while the page was already animating.
            settleFrom = visualPosition;
            settleBaseFraction = settleFraction;
        } else {
            cancelRecoil();
            if (!ValueAnimator.areAnimatorsEnabled()) { setVisualPosition(selectedIndex); return; }
            recoil = ValueAnimator.ofFloat(visualPosition, selectedIndex);
            recoil.setDuration(180L);
            recoil.setInterpolator(new DecelerateInterpolator(1.5f));
            recoil.addUpdateListener(animation -> setVisualPosition((float) animation.getAnimatedValue()));
            recoil.start();
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
        int from = (int) Math.floor(position);
        int to = Math.min(getChildCount() - 1, from + 1);
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
        if (!ValueAnimator.areAnimatorsEnabled()) {
            cancelFingerFollow();
            setVisualPosition(fingerPosition);
        } else if (!followPosted) {
            followFrameTime = android.os.SystemClock.uptimeMillis();
            followPosted = true;
            postOnAnimation(followFinger);
        }
    }

    private void advanceFingerFollow() {
        followPosted = false;
        if (!dragging) return;
        long now = android.os.SystemClock.uptimeMillis();
        long elapsed = Math.max(0L, Math.min(64L, now - followFrameTime));
        followFrameTime = now;
        // Retarget a single frame loop instead of restarting an animator on every MOVE.
        float fraction = 1f - (float) Math.exp(-elapsed / FOLLOW_TIME_CONSTANT_MILLIS);
        float position = visualPosition + (fingerPosition - visualPosition) * fraction;
        if (Math.abs(fingerPosition - position) < .001f) {
            setVisualPosition(fingerPosition);
        } else {
            setVisualPosition(position);
            followPosted = true;
            postOnAnimation(followFinger);
        }
    }

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
                int color = i == active ? palette.onPrimaryContainer : palette.muted;
                label.setTextColor(color);
                label.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
            }
        }
    }

    @Override protected void dispatchDraw(Canvas canvas) {
        View cell = getChildAt(selectedIndex);
        if (cell != null) {
            float x = centerAt(visualPosition);
            int left = Math.round(x - cell.getWidth() / 2f);
            highlight.setColor(UiStyle.colors(getContext()).primaryContainer);
            highlight.setBounds(left, cell.getTop(), left + cell.getWidth(), cell.getBottom());
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
        cancelRecoil();
        cancelChildTouch();
        dragging = true;
        getParent().requestDisallowInterceptTouchEvent(true);
        moveHighlight(x);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            cancelFingerFollow();
            removeCallbacks(finishTouch);
            tracking = true;
            ignoreTouch = false;
            dragging = false;
            tapLeftBounds = false;
            downX = event.getX();
            downY = event.getY();
            postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            cancelFingerFollow();
            removeCallbacks(longPress);
            if (!dragging) cancelChildTouch();
            dragging = false;
            ignoreTouch = true;
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
            boolean commit = dragging && action == MotionEvent.ACTION_UP
                    && insideDock(event);
            int destination = nearest(event.getX());
            cancelFingerFollow();
            tracking = false;
            dragging = false;
            ignoreTouch = false;
            if (commit) {
                if (pageSettling) resumeOrReturnHighlight();
                select.accept(destination);
                if (!pageSettling) resumeOrReturnHighlight();
            } else if (owned) {
                resumeOrReturnHighlight();
            }
            invalidate();
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
        cancelFingerFollow();
        removeCallbacks(longPress);
        removeCallbacks(finishTouch);
        cancelRecoil();
        tracking = dragging = ignoreTouch = false;
        super.onDetachedFromWindow();
    }
}
