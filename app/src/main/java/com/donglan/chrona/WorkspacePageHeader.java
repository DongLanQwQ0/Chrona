package com.donglan.chrona;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;

/** Navigation occupies the title slot; its motion shares the header's surrounding space. */
final class WorkspacePageHeader extends LinearLayout {
    WorkspacePageHeader(Activity activity, View title, View action) {
        super(activity);
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false); setClipToPadding(false);
        setMinimumHeight(dp(UiStyle.HEADER_HEIGHT_DP));
        addView(icon(activity, R.drawable.ic_arrow_left, "返回", activity::finish),
                new LinearLayout.LayoutParams(dp(48), dp(48)));
        LinearLayout.LayoutParams center = new LinearLayout.LayoutParams(0, -2, 1);
        center.setMargins(dp(4), 0, dp(4), 0);
        addView(title, center);
        if (action != null) addView(action, new LinearLayout.LayoutParams(dp(48), dp(48)));
    }

    static ImageButton icon(Activity activity, int resource, String label, Runnable click) {
        return UiStyle.navigationIcon(activity, resource, label, click);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
