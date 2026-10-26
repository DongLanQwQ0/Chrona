package com.donglan.chrona;

import android.app.Activity;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

/** Navigation occupies the title slot; its motion shares the header's surrounding space. */
final class WorkspacePageHeader extends LinearLayout {
    private final View title;
    private final View action;
    private final int actionWidthDp;
    private ValueAnimator actionMotion;
    private float actionProgress = 1f;
    private boolean actionVisible = true;

    WorkspacePageHeader(Activity activity, View title, View action) {
        this(activity, title, action, 48);
    }

    WorkspacePageHeader(Activity activity, View title, View action, int actionWidthDp) {
        super(activity);
        this.title = title;
        this.action = action;
        this.actionWidthDp = actionWidthDp;
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false); setClipToPadding(false);
        setChildrenDrawingOrderEnabled(true);
        setMinimumHeight(dp(UiStyle.HEADER_HEIGHT_DP));
        addView(icon(activity, R.drawable.ic_arrow_left, "返回", activity::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams center = new LinearLayout.LayoutParams(0, -2, 1);
        center.setMargins(dp(8), 0, action == null ? 0 : dp(8), 0);
        addView(title, center);
        if (action != null) addView(action, new LinearLayout.LayoutParams(dp(actionWidthDp), dp(48)));
    }

    void setActionVisible(boolean visible) {
        if (action == null) return;
        action.setClickable(visible);
        action.setImportantForAccessibility(visible ? IMPORTANT_FOR_ACCESSIBILITY_AUTO
                : IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if (actionVisible == visible && isLaidOut()) return;
        actionVisible = visible;
        if (actionMotion != null) { actionMotion.cancel(); actionMotion = null; }
        float target = visible ? 1f : 0f;
        if (!isAttachedToWindow() || !isLaidOut() || !UiMotion.isEnabled()) {
            applyActionProgress(target);
            return;
        }
        actionMotion = ValueAnimator.ofFloat(actionProgress, target);
        actionMotion.setDuration(visible ? UiMotion.ENTER : UiMotion.EXIT);
        actionMotion.setInterpolator(UiMotion.SETTLE);
        actionMotion.addUpdateListener(animation -> applyActionProgress((float) animation.getAnimatedValue()));
        actionMotion.start();
    }

    private void applyActionProgress(float progress) {
        actionProgress = progress;
        action.setVisibility(progress == 0f ? GONE : VISIBLE);
        action.setAlpha(progress);
        action.setScaleX(.85f + .15f * progress);
        action.setScaleY(.85f + .15f * progress);
        LinearLayout.LayoutParams slot = (LinearLayout.LayoutParams) action.getLayoutParams();
        slot.width = Math.round(dp(actionWidthDp) * progress);
        action.setLayoutParams(slot);
        LinearLayout.LayoutParams center = (LinearLayout.LayoutParams) title.getLayoutParams();
        center.rightMargin = Math.round(dp(8) * progress);
        title.setLayoutParams(center);
    }

    @Override protected void onDetachedFromWindow() {
        if (actionMotion != null) { actionMotion.cancel(); actionMotion = null; }
        if (action != null) applyActionProgress(actionVisible ? 1f : 0f);
        super.onDetachedFromWindow();
    }

    @Override protected int getChildDrawingOrder(int childCount, int drawingPosition) {
        // Paint the title's overflowing highlight above the action without reordering layout.
        if (childCount == 3) return drawingPosition == 1 ? 2 : drawingPosition == 2 ? 1 : 0;
        return drawingPosition;
    }

    static ImageButton icon(Activity activity, int resource, String label, Runnable click) {
        return UiStyle.navigationIcon(activity, resource, label, click);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
