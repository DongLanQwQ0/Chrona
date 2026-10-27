package com.donglan.chrona;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** A short workspace spends vertical scroll on its tools before scrolling its content. */
final class CollapsingPageLayout extends LinearLayout {
    private View scrollingBody;
    private int hidden, measuredHidden, range, nestedAxes;
    private final boolean compact;

    CollapsingPageLayout(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setClipChildren(false); setClipToPadding(false);
        compact = SettingsPageLayout.shortWindow(context)
                || getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
    }

    void setScrollingBody(View body) { scrollingBody = body; requestLayout(); }

    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        super.onMeasure(widthSpec, heightSpec);
        if (!compact || scrollingBody == null) return;
        range = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == scrollingBody) break;
            if (child.getVisibility() == GONE) continue;
            LayoutParams slot = (LayoutParams)child.getLayoutParams();
            range += child.getMeasuredHeight() + slot.topMargin + slot.bottomMargin;
        }
        // LinearLayout may probe the tools with a provisional height before its final
        // weight pass. Measurement must not discard the user's committed scroll offset.
        measuredHidden = Math.max(0, Math.min(hidden, range));
        scrollingBody.measure(MeasureSpec.makeMeasureSpec(scrollingBody.getMeasuredWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(scrollingBody.getMeasuredHeight() + measuredHidden, MeasureSpec.EXACTLY));
        enableNestedScroll(this);
    }

    private void enableNestedScroll(ViewGroup parent) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ScrollView&&!child.isNestedScrollingEnabled()) child.setNestedScrollingEnabled(true);
            if (child instanceof ViewGroup group) enableNestedScroll(group);
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (!compact || scrollingBody == null) return;
        hidden = measuredHidden;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).offsetTopAndBottom(-hidden);
        // Only a scrolled-away toolbar is bounded by this viewport. At rest, keep its motion envelope.
        setClipBounds(hidden == 0 ? null : new Rect(0, getPaddingTop(), getWidth(), getHeight()));
    }

    @Override public boolean onStartNestedScroll(View child, View target, int axes) {
        var insets = getRootWindowInsets();
        if (findFocus() instanceof android.widget.EditText && insets != null) {
            boolean editing = android.os.Build.VERSION.SDK_INT >= 30
                    ? insets.isVisible(android.view.WindowInsets.Type.ime())
                    : insets.getSystemWindowInsetBottom() > insets.getStableInsetBottom();
            if (editing) return false;
        }
        return compact && scrollingBody != null && (axes & SCROLL_AXIS_VERTICAL) != 0;
    }

    @Override public void onNestedScrollAccepted(View child, View target, int axes) { nestedAxes = axes; }
    @Override public void onStopNestedScroll(View target) { nestedAxes = 0; }
    @Override public int getNestedScrollAxes() { return nestedAxes; }

    private int consume(int dy) {
        int next = Math.max(0, Math.min(range, hidden + dy));
        int consumed = next - hidden;
        if (consumed != 0) {
            hidden = next;
            // Nested scrolling measures the target's window offset before returning.
            // Move it now; the next layout grows the body into the reclaimed height.
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i); child.offsetTopAndBottom(-consumed);
                if (child == scrollingBody) break;
            }
            requestLayout();
        }
        return consumed;
    }

    @Override public void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {
        if (dy > 0 || !target.canScrollVertically(-1)) consumed[1] += consume(dy);
    }

    @Override public void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {
        if (dyUnconsumed < 0) consume(dyUnconsumed);
    }
}
