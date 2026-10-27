package com.donglan.chrona;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Shared schedule/inbox/classroom selection chrome. Selection ownership stays with each page. */
final class BatchSelectionUi {
    private BatchSelectionUi() { }
    private static int dp(Context c,int value){return Math.round(value*c.getResources().getDisplayMetrics().density);}
    static Button action(Context context,String label,Runnable action){
        Button control=new Button(context);control.setText(label);control.setAllCaps(false);control.setSingleLine(true);
        control.setTextSize(13);control.setTextColor(UiStyle.colors(context).primary);control.setIncludeFontPadding(false);
        control.setMinWidth(0);control.setMinimumWidth(0);control.setMinHeight(0);control.setMinimumHeight(0);
        control.setPadding(dp(context,8),0,dp(context,8),0);UiStyle.glassPill(control);UiStyle.pressable(control);
        control.setOnClickListener(v->action.run());return control;
    }
    static int headingHeight(TextView title){return Math.max(dp(title.getContext(),40),(int)Math.ceil(title.getPaint().getFontSpacing())+dp(title.getContext(),8));}
    static void attach(LinearLayout heading,View bar,int height,Button... actions){
        ViewGroup previous=(ViewGroup)bar.getParent();if(previous!=null)previous.removeView(bar);
        for(Button control:actions){LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-2,height);p.leftMargin=dp(heading.getContext(),6);control.setLayoutParams(p);}
        heading.addView(bar,new LinearLayout.LayoutParams(-2,height));
    }
    static void visible(View bar,boolean active){
        boolean wasVisible=bar.getVisibility()==View.VISIBLE;bar.animate().cancel();
        if(active&&!wasVisible&&android.animation.ValueAnimator.areAnimatorsEnabled()){
            bar.setAlpha(0f);bar.setTranslationY(-dp(bar.getContext(),5));bar.setVisibility(View.VISIBLE);
            bar.animate().alpha(1f).translationY(0f).setDuration(170).start();
        }else if(!active&&wasVisible&&android.animation.ValueAnimator.areAnimatorsEnabled()){
            bar.animate().alpha(0f).translationY(-dp(bar.getContext(),4)).setDuration(120).withEndAction(()->{
                bar.setVisibility(View.GONE);bar.setAlpha(1f);bar.setTranslationY(0f);
            }).start();
        }else{bar.setAlpha(1f);bar.setTranslationY(0f);bar.setVisibility(active?View.VISIBLE:View.GONE);}
    }
    static void marker(TextView marker,boolean active,boolean selected){
        marker.setGravity(Gravity.CENTER);marker.setVisibility(active?View.VISIBLE:View.GONE);marker.setText("");
        android.graphics.drawable.Drawable icon=marker.getContext().getDrawable(selected?R.drawable.ic_check:R.drawable.ic_circle).mutate();
        icon.setBounds(0,0,dp(marker.getContext(),22),dp(marker.getContext(),22));
        icon.setTint(selected?UiStyle.colors(marker.getContext()).primary:UiStyle.colors(marker.getContext()).muted);
        marker.setCompoundDrawables(icon,null,null,null);marker.setTextColor(selected?UiStyle.colors(marker.getContext()).primary:UiStyle.colors(marker.getContext()).muted);
    }
}
