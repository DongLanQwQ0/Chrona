package com.donglan.chrona;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.view.ScaleGestureDetector;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/** Owns one gesture and applies both axes, avoiding nested ScrollView axis locking. */
final class FreeCalendarScrollView extends HorizontalScrollView {
    private ScrollView vertical;
    private float downX, downY, lastX, lastY;
    private boolean dragging;
    private boolean multiTouch;
    private int edgeDirection;
    private final CalendarSwipe edgeSwipe = new CalendarSwipe();
    private java.util.function.IntConsumer changeWeek;
    interface Zoom { void scale(float factor, float x, float y); }
    private Zoom zoom;
    private final ScaleGestureDetector detector;
    private VelocityTracker velocity;
    private final int slop, maxVelocity;
    FreeCalendarScrollView(Context context) {
        super(context); ViewConfiguration config = ViewConfiguration.get(context);
        setOverScrollMode(OVER_SCROLL_NEVER);
        slop = config.getScaledTouchSlop(); maxVelocity = config.getScaledMaximumFlingVelocity();
        detector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector d) { return true; }
            @Override public boolean onScale(ScaleGestureDetector d) {
                if (zoom != null) zoom.scale(d.getScaleFactor(), d.getFocusX(), d.getFocusY());
                return true;
            }
        });
    }
    void vertical(ScrollView view) { vertical = view; vertical.setOverScrollMode(OVER_SCROLL_NEVER); }
    void gestures(java.util.function.IntConsumer changeWeek, Zoom zoom) { this.changeWeek = changeWeek; this.zoom = zoom; }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            multiTouch = false; edgeDirection = 0; edgeSwipe.cancel();
            dragging = false; downX = lastX = event.getX(); downY = lastY = event.getY();
            if (velocity != null) velocity.recycle(); velocity = VelocityTracker.obtain(); velocity.addMovement(event);
            // Stop both inherited flings before starting a new finger-following gesture.
            super.onTouchEvent(event);
            if (vertical != null) vertical.onTouchEvent(event);
            MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(cancel); if (vertical != null) vertical.onTouchEvent(cancel); cancel.recycle();
            detector.onTouchEvent(event); return super.dispatchTouchEvent(event);
        }
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            multiTouch = true; edgeSwipe.cancel(); cancelChild(event); recycleVelocity();
            getParent().requestDisallowInterceptTouchEvent(true);
        }
        detector.onTouchEvent(event);
        if (multiTouch) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                dragging = false; getParent().requestDisallowInterceptTouchEvent(false);
            }
            return true;
        }
        if (velocity != null) velocity.addMovement(event);
        if (!dragging && action == MotionEvent.ACTION_MOVE && (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop)) {
            // Dispatch owns arbitration even when the nested vertical view disallows interception.
            cancelChild(event);
            dragging = true; getParent().requestDisallowInterceptTouchEvent(true);
        }
        if (dragging) return onTouchEvent(event);
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) recycleVelocity();
        return handled;
    }
    @Override public boolean onInterceptTouchEvent(MotionEvent event) { return false; }
    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            float fingerDelta = event.getX() - lastX;
            if (edgeDirection != 0 && fingerDelta * edgeDirection > 0) { edgeSwipe.cancel(); edgeDirection = 0; }
            int before = getScrollX();
            scrollBy(Math.round(lastX - event.getX()), 0);
            float outside = fingerDelta + getScrollX() - before;
            int direction = outside < 0 ? 1 : -1;
            if (Math.abs(outside) > .5f && !canScrollHorizontally(direction) && changeWeek != null) {
                if (edgeDirection == 0) {
                    edgeDirection = direction;
                    // Only the unconsumed part beyond the edge counts toward changing weeks.
                    edgeSwipe.begin(event.getX() - outside, lastY, true);
                }
                edgeSwipe.move(event.getX(), event.getY(), slop, false);
            }
            if (vertical != null) vertical.scrollBy(0, Math.round(lastY - event.getY()));
            lastX = event.getX(); lastY = event.getY(); return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (action == MotionEvent.ACTION_CANCEL) edgeSwipe.cancel();
            int direction = edgeSwipe.finish(event.getX(), event.getY(), Math.max(48 * getResources().getDisplayMetrics().density, getWidth() * .15f));
            if (direction != edgeDirection) direction = 0;
            if (direction != 0 && changeWeek != null) changeWeek.accept(direction);
            else if (action == MotionEvent.ACTION_UP && velocity != null) {
                velocity.computeCurrentVelocity(1000, maxVelocity); fling(Math.round(-velocity.getXVelocity()));
                if (vertical != null) vertical.fling(Math.round(-velocity.getYVelocity()));
            }
            recycleVelocity();
            dragging = false; getParent().requestDisallowInterceptTouchEvent(false); return true;
        }
        return true;
    }
    private void cancelChild(MotionEvent event) {
        MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
        super.dispatchTouchEvent(cancel); cancel.recycle();
    }
    private void recycleVelocity() { if (velocity != null) { velocity.recycle(); velocity = null; } }
    @Override protected void onDetachedFromWindow() { recycleVelocity(); super.onDetachedFromWindow(); }
}
