package com.donglan.chrona;

import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.VelocityTracker;
import android.widget.FrameLayout;

import java.util.ArrayList;

/** Allows horizontal task paging while leaving vertical drags to the page ScrollView. */
final class TaskDetailPagerLayout extends FrameLayout {
    interface Listener {
        void onStart();
        void onDrag(float distanceX);
        void onRelease(float distanceX, float velocityX);
        void onCancel();
    }

    private final int touchSlop;
    private final Listener listener;
    private final ArrayList<View> gesturePriorityChildren = new ArrayList<>();
    private boolean touchStartedInPriorityChild;
    private float downX;
    private float downY;
    private float dragOriginX;
    private float dispatchDownX;
    private float dispatchDownY;
    private CandidatePagerScrollView candidatePagerAtDown;
    private boolean pagingGesture;
    private boolean startedAtSystemEdge;
    private VelocityTracker velocityTracker;
    private boolean taskPagingEnabled = true;

    TaskDetailPagerLayout(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
    }

    void setGesturePriorityChild(View child) {
        gesturePriorityChildren.clear();
        addGesturePriorityChild(child);
    }

    void addGesturePriorityChild(View child) {
        if (child == null || gesturePriorityChildren.contains(child)) return;
        gesturePriorityChildren.add(child);
    }

    void setTaskPagingEnabled(boolean enabled) {
        taskPagingEnabled = enabled;
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            if (velocityTracker != null) velocityTracker.recycle();
            velocityTracker = VelocityTracker.obtain();
            velocityTracker.addMovement(event);
            dispatchDownX = event.getX();
            dispatchDownY = event.getY();
            candidatePagerAtDown = candidatePagerAt(event.getRawX(), event.getRawY());
            touchStartedInPriorityChild = candidatePagerAtDown != null
                    || containsGesturePriorityChild(event.getRawX(), event.getRawY());
            int edgeInset = dp(32);
            startedAtSystemEdge = dispatchDownX <= edgeInset
                    || dispatchDownX >= getWidth() - edgeInset;
        } else if (event.getActionMasked() == MotionEvent.ACTION_MOVE) {
            float dx = event.getX() - dispatchDownX;
            float dy = event.getY() - dispatchDownY;
            if (candidatePagerAtDown != null && !startedAtSystemEdge
                    && Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.25f
                    && candidatePagerAtDown.canYieldToTaskPager(dx)) {
                candidatePagerAtDown.prepareForTaskPagerHandoff();
                candidatePagerAtDown = null;
                touchStartedInPriorityChild = false;
            }
            if (!touchStartedInPriorityChild && Math.abs(dx) > touchSlop
                    && Math.abs(dx) > Math.abs(dy) * 1.25f) {
                // A nested preview may have asked its vertical parent to keep the gesture.
                super.requestDisallowInterceptTouchEvent(false);
            }
        }
        int action = event.getActionMasked();
        if (action != MotionEvent.ACTION_DOWN && velocityTracker != null)
            velocityTracker.addMovement(event);
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            touchStartedInPriorityChild = false;
            candidatePagerAtDown = null;
            if (velocityTracker != null) {
                velocityTracker.recycle();
                velocityTracker = null;
            }
        }
        return handled;
    }

    @Override public boolean onInterceptTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            downX = event.getX();
            downY = event.getY();
            dragOriginX = downX;
            pagingGesture = false;
            return false;
        }
        if (!taskPagingEnabled || touchStartedInPriorityChild || startedAtSystemEdge) return false;
        if (action != MotionEvent.ACTION_MOVE) return false;
        float dx = event.getX() - downX;
        float dy = event.getY() - downY;
        if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy) * 1.25f) {
            pagingGesture = true;
            // Consume the touch slop: the page starts moving from the point where the gesture was
            // recognised, so it never jumps by the dead-zone distance on the first frame.
            dragOriginX = event.getX();
            listener.onStart();
            return true;
        }
        return false;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!pagingGesture) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_MOVE) {
            listener.onDrag(event.getX() - dragOriginX);
            return true;
        }
        if (action == MotionEvent.ACTION_UP) {
            float velocityX = 0f;
            if (velocityTracker != null) {
                velocityTracker.computeCurrentVelocity(1000);
                velocityX = velocityTracker.getXVelocity();
            }
            listener.onRelease(event.getX() - dragOriginX, velocityX);
            pagingGesture = false;
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            listener.onCancel();
            pagingGesture = false;
        }
        return true;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }

    private boolean contains(View child, float rawX, float rawY) {
        if (child == null || !child.isShown()) return false;
        int[] location = new int[2];
        child.getLocationOnScreen(location);
        return rawX >= location[0] && rawX < location[0] + child.getWidth()
                && rawY >= location[1] && rawY < location[1] + child.getHeight();
    }

    private boolean containsGesturePriorityChild(float rawX, float rawY) {
        for (int i = gesturePriorityChildren.size() - 1; i >= 0; i--) {
            View child = gesturePriorityChildren.get(i);
            if (!child.isAttachedToWindow()) gesturePriorityChildren.remove(i);
            else if (contains(child, rawX, rawY)) return true;
        }
        return false;
    }

    private CandidatePagerScrollView candidatePagerAt(float rawX, float rawY) {
        for (int i = gesturePriorityChildren.size() - 1; i >= 0; i--) {
            View child = gesturePriorityChildren.get(i);
            if (!child.isAttachedToWindow()) {
                gesturePriorityChildren.remove(i);
            } else if (child instanceof CandidatePagerScrollView pager
                    && contains(child, rawX, rawY)) {
                return pager;
            }
        }
        return null;
    }
}
