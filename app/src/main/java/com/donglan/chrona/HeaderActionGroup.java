package com.donglan.chrona;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

/** Related header actions share one surface, separators and one press response. */
final class HeaderActionGroup extends LinearLayout {
    private final int height;

    HeaderActionGroup(Context context, int heightDp) {
        super(context);
        height = dp(heightDp);
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false); setClipToPadding(false);
        UiStyle.acrylicChoice(this, false, UiStyle.RADIUS_PILL, false);
    }

    void addAction(View action, int widthDp) {
        if (getChildCount() > 0) {
            View separator = new View(getContext());
            separator.setBackgroundColor(UiStyle.colors(getContext()).outline);
            addView(separator, new LinearLayout.LayoutParams(dp(1), dp(16)));
        }
        action.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        addView(action, new LinearLayout.LayoutParams(dp(widthDp), height));
    }

    void finish() { PressFeedback.group(this, 1.10f); }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
