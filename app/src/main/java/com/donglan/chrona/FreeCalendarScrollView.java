package com.donglan.chrona;

import android.content.Context;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.ViewConfiguration;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;

/** Owns one gesture and applies both axes, avoiding nested ScrollView axis locking. */
final class FreeCalendarScrollView extends HorizontalScrollView {
    private ScrollView vertical;
    private float downX, downY, lastX, lastY;
    private boolean dragging;
    private VelocityTracker velocity;
    private final int slop, maxVelocity;
    FreeCalendarScrollView(Context context) {
        super(context); ViewConfiguration config = ViewConfiguration.get(context);
        slop = config.getScaledTouchSlop(); maxVelocity = config.getScaledMaximumFlingVelocity();
    }
    void vertical(ScrollView view) { vertical = view; }
    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            dragging = false; downX = lastX = event.getX(); downY = lastY = event.getY();
            if (velocity != null) velocity.recycle(); velocity = VelocityTracker.obtain(); velocity.addMovement(event);
            // Stop both inherited flings before starting a new finger-following gesture.
            super.onTouchEvent(event);
            if (vertical != null) vertical.onTouchEvent(event);
            MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.onTouchEvent(cancel); if (vertical != null) vertical.onTouchEvent(cancel); cancel.recycle();
            return super.dispatchTouchEvent(event);
        }
        if (velocity != null) velocity.addMovement(event);
        if (!dragging && action == MotionEvent.ACTION_MOVE && (Math.abs(event.getX() - downX) > slop || Math.abs(event.getY() - downY) > slop)) {
            // Dispatch owns arbitration even when the nested vertical view disallows interception.
            MotionEvent cancel = MotionEvent.obtain(event); cancel.setAction(MotionEvent.ACTION_CANCEL);
            super.dispatchTouchEvent(cancel); cancel.recycle();
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
            scrollBy(Math.round(lastX - event.getX()), 0);
            if (vertical != null) vertical.scrollBy(0, Math.round(lastY - event.getY()));
            lastX = event.getX(); lastY = event.getY(); return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            if (action == MotionEvent.ACTION_UP && velocity != null) {
                velocity.computeCurrentVelocity(1000, maxVelocity); fling(Math.round(-velocity.getXVelocity()));
                if (vertical != null) vertical.fling(Math.round(-velocity.getYVelocity()));
            }
            recycleVelocity();
            dragging = false; getParent().requestDisallowInterceptTouchEvent(false); return true;
        }
        return true;
    }
    private void recycleVelocity() { if (velocity != null) { velocity.recycle(); velocity = null; } }
    @Override protected void onDetachedFromWindow() { recycleVelocity(); super.onDetachedFromWindow(); }
}
