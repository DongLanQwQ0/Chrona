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

    @Override protected boolean drawChild(android.graphics.Canvas canvas,View child,long drawingTime) {
        View first=null,last=null;
        for(int i=0;i<getChildCount();i++){View item=getChildAt(i);if(item.getVisibility()!=GONE&&item.getWidth()>dp(1)){if(first==null)first=item;last=item;}}
        if(first==last||child!=first&&child!=last)return super.drawChild(canvas,child,drawingTime);
        // Rounded end cells have less visible area at their outer corners. Centre the
        // glyph on that visible area; keep the full rectangular touch cell unchanged.
        double radius=Math.min(getHeight()/2.0,dp(UiStyle.radius(getContext(),UiStyle.RADIUS_PILL)));
        double width=child.getWidth(),area=width*getHeight();
        double cutArea=(2-Math.PI/2)*radius*radius;
        double cutMoment=(5.0/3-Math.PI/2)*radius*radius*radius;
        float shift=area>cutArea?(float)((area*width/2-cutMoment)/(area-cutArea)-width/2):0;
        int saved=canvas.save();canvas.translate(child==first?shift:-shift,0);
        boolean result=super.drawChild(canvas,child,drawingTime);canvas.restoreToCount(saved);return result;
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
