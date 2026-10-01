package com.donglan.chrona;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.GradientDrawable;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
    private float downX, downY, highlightCenter;
    private int selectedIndex, tapIndex, labelIndex = -1;
    private boolean tracking, dragging, ignoreTouch;
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
        labelIndex = -1;
        if (!dragging) updateLabels(index);
        invalidate();
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

    private void moveHighlight(float x) {
        if (getChildCount() == 0) return;
        // Clamp using physical bounds, which also works with RTL child ordering.
        float left = Math.min(center(0), center(getChildCount() - 1));
        float right = Math.max(center(0), center(getChildCount() - 1));
        highlightCenter = Math.max(left, Math.min(right, x));
        updateLabels(nearest(highlightCenter));
        invalidate();
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
            float x = dragging ? highlightCenter : center(selectedIndex);
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
        cancelChildTouch();
        dragging = true;
        getParent().requestDisallowInterceptTouchEvent(true);
        moveHighlight(x);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            tracking = true;
            ignoreTouch = false;
            dragging = false;
            downX = event.getX();
            downY = event.getY();
            postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            removeCallbacks(longPress);
            if (!dragging) cancelChildTouch();
            dragging = false;
            ignoreTouch = true;
            updateLabels(selectedIndex);
            invalidate();
        } else if (action == MotionEvent.ACTION_MOVE && tracking && !ignoreTouch) {
            float dx = Math.abs(event.getX() - downX);
            float dy = Math.abs(event.getY() - downY);
            if (!dragging && (dx > touchSlop || dy > touchSlop)) {
                removeCallbacks(longPress);
                if (dx > dy * 1.25f) beginDrag(event.getX());
                else {
                    cancelChildTouch();
                    ignoreTouch = true;
                }
            }
            if (dragging) moveHighlight(event.getX());
        }
        boolean owned = dragging || ignoreTouch;
        boolean handled = owned || super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPress);
            boolean commit = dragging && action == MotionEvent.ACTION_UP
                    && event.getX() >= -touchSlop && event.getX() <= getWidth() + touchSlop
                    && event.getY() >= -touchSlop && event.getY() <= getHeight() + touchSlop;
            if (commit) moveHighlight(event.getX());
            int destination = nearest(highlightCenter);
            tracking = false;
            dragging = false;
            ignoreTouch = false;
            if (commit) {
                selectedIndex = destination;
                select.accept(destination);
            }
            updateLabels(selectedIndex);
            invalidate();
            finished.run();
        }
        return handled;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        // Padding and gaps are also valid starts for a dock gesture.
        if (event.getActionMasked() == MotionEvent.ACTION_UP && !dragging && !ignoreTouch) {
            tapIndex = nearest(event.getX());
            performClick();
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        if (getChildCount() > 0) select.accept(tapIndex);
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        removeCallbacks(longPress);
        tracking = dragging = ignoreTouch = false;
        super.onDetachedFromWindow();
    }
}
