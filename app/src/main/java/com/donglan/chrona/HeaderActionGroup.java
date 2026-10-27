package com.donglan.chrona;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;

/** Related header actions share one surface, separators and one press response. */
final class HeaderActionGroup extends LinearLayout {
    private final int height;
    private final java.util.Map<View, int[]> contentPadding = new java.util.IdentityHashMap<>();
    private final java.util.Map<View, Action> actions = new java.util.IdentityHashMap<>();
    private static final class Action {
        final View view, separator;final int width;
        float progress=1f;boolean visible=true;android.animation.ValueAnimator motion;
        Action(View view,View separator,int width){this.view=view;this.separator=separator;this.width=width;}
    }

    HeaderActionGroup(Context context, int heightDp) {
        super(context);
        height = dp(heightDp);
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false); setClipToPadding(false);
        UiStyle.acrylicChoice(this, false, UiStyle.RADIUS_PILL, false);
    }

    void addAction(View action, int widthDp) {
        View separator = null;
        if (getChildCount() > 0) {
            separator = new View(getContext());
            separator.setBackgroundColor(UiStyle.colors(getContext()).outline);
            addView(separator, new LinearLayout.LayoutParams(dp(1), dp(16)));
        }
        action.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        addView(action, new LinearLayout.LayoutParams(dp(widthDp), height));
        actions.put(action,new Action(action,separator,dp(widthDp)));
    }

    void finish() { PressFeedback.group(this, 1.10f); }

    void setActionVisible(View view,boolean visible) {
        Action action=actions.get(view);if(action==null)throw new IllegalArgumentException("Unknown header action");
        view.setClickable(visible);view.setFocusable(visible);
        view.setImportantForAccessibility(visible?IMPORTANT_FOR_ACCESSIBILITY_AUTO:IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        if(action.visible==visible&&isLaidOut())return;
        action.visible=visible;if(action.motion!=null){action.motion.cancel();action.motion=null;}
        float target=visible?1f:0f;
        if(!isAttachedToWindow()||!isLaidOut()||!UiMotion.isEnabled()){apply(action,target);return;}
        action.motion=android.animation.ValueAnimator.ofFloat(action.progress,target);
        action.motion.setDuration(visible?UiMotion.ENTER:UiMotion.EXIT);action.motion.setInterpolator(UiMotion.SETTLE);
        action.motion.addUpdateListener(animation->apply(action,(float)animation.getAnimatedValue()));action.motion.start();
    }

    private void apply(Action action,float progress) {
        action.progress=progress;action.view.setVisibility(progress==0f?GONE:VISIBLE);action.view.setAlpha(progress);
        action.view.setScaleX(.85f+.15f*progress);action.view.setScaleY(.85f+.15f*progress);
        LinearLayout.LayoutParams slot=(LinearLayout.LayoutParams)action.view.getLayoutParams();slot.width=Math.round(action.width*progress);action.view.setLayoutParams(slot);
        if(action.separator!=null){action.separator.setVisibility(progress==0f?GONE:VISIBLE);action.separator.setAlpha(progress);LinearLayout.LayoutParams divider=(LinearLayout.LayoutParams)action.separator.getLayoutParams();divider.width=Math.round(dp(1)*progress);action.separator.setLayoutParams(divider);}
    }

    @Override protected void onDetachedFromWindow() {
        for(Action action:actions.values()){if(action.motion!=null){action.motion.cancel();action.motion=null;}apply(action,action.visible?1f:0f);}
        super.onDetachedFromWindow();
    }

    @Override protected boolean drawChild(android.graphics.Canvas canvas,View child,long drawingTime) {
        View first=null,last=null;
        for(int i=0;i<getChildCount();i++){View item=getChildAt(i);if(item.getVisibility()!=GONE&&item.getWidth()>dp(1)){if(first==null)first=item;last=item;}}
        // Rounded end cells have less visible area at their outer corners. Centre the
        // content, not the View: its foreground must stay on the full touch cell.
        double radius=Math.min(getHeight()/2.0,dp(UiStyle.radius(getContext(),UiStyle.RADIUS_PILL)));
        double width=child.getWidth(),area=width*getHeight();
        double cutArea=(2-Math.PI/2)*radius*radius;
        double cutMoment=(5.0/3-Math.PI/2)*radius*radius*radius;
        int shift=first!=last&&(child==first||child==last)&&area>cutArea
                ?Math.round((float)((area*width/2-cutMoment)/(area-cutArea)-width/2))*(child==first?1:-1):0;
        if(child.getWidth()>dp(1)){
            int[] padding=contentPadding.computeIfAbsent(child,view->new int[]{view.getPaddingLeft(),view.getPaddingTop(),view.getPaddingRight(),view.getPaddingBottom()});
            if(child.getPaddingLeft()!=padding[0]+shift||child.getPaddingRight()!=padding[2]-shift)
                child.setPadding(padding[0]+shift,padding[1],padding[2]-shift,padding[3]);
        }
        return super.drawChild(canvas,child,drawingTime);
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
