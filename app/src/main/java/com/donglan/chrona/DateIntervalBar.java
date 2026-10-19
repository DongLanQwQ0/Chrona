package com.donglan.chrona;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.function.IntPredicate;

/** The filter sheet's date rail and finger-following transition, shared with the calendar. */
final class DateIntervalBar extends FrameLayout {
    private final TextView value;
    private final IntPredicate shift;
    private String desired = "";
    private boolean moving;
    private int generation;

    DateIntervalBar(Context context, Runnable pick, IntPredicate shift) {
        super(context); this.shift = shift;
        setClipChildren(false);
        setClipToPadding(false);
        UiStyle.acrylicChoice(this, false, UiStyle.RADIUS_PILL, true);
        setOnClickListener(view -> pick.run());
        setContentDescription("日期区间，左右滑动切换，点击选择日期");
        FrameLayout labelHost = new FrameLayout(context); labelHost.setClipChildren(true);
        // Keep the text viewport bounded even when shared press feedback opens ancestor
        // clipping for the rail/arrow scale envelope.
        PressFeedback.protectViewport(labelHost);
        FrameLayout.LayoutParams labelParams = new FrameLayout.LayoutParams(-1, -1);
        labelParams.setMargins(dp(44), dp(4), dp(44), dp(4)); addView(labelHost, labelParams);
        value = new TextView(context); value.setTextSize(13); value.setIncludeFontPadding(false);
        value.setTextColor(UiStyle.colors(context).text); value.setGravity(Gravity.CENTER); value.setMaxLines(2);
        labelHost.addView(value, new FrameLayout.LayoutParams(-1, -1));
        for (int side = 0; side < 2; side++) {
            final int direction = side == 0 ? -1 : 1;
            ImageButton arrow = new ImageButton(context);
            arrow.setImageResource(side == 0 ? R.drawable.ic_chevron_left : R.drawable.ic_chevron_right);
            arrow.setImageTintList(ColorStateList.valueOf(UiStyle.colors(context).muted));
            arrow.setScaleType(ImageView.ScaleType.CENTER_INSIDE); arrow.setPadding(dp(12),dp(12),dp(12),dp(12));
            arrow.setBackgroundColor(android.graphics.Color.TRANSPARENT);
            arrow.setContentDescription(side == 0 ? "上一段日期范围" : "下一段日期范围");
            arrow.setOnClickListener(view -> shift(direction)); UiStyle.pressable(arrow);
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(dp(44),dp(44),
                    Gravity.CENTER_VERTICAL | (side == 0 ? Gravity.LEFT : Gravity.RIGHT));
            params.leftMargin = params.rightMargin = dp(2); addView(arrow, params);
        }
        setOnTouchListener(new OnTouchListener() {
            float x,y,initialTranslation; boolean horizontal;
            @Override public boolean onTouch(View view, MotionEvent event) {
                float dx=event.getX()-x, dy=event.getY()-y;
                switch(event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        cancelMotion(); value.setText(desired); value.setAlpha(1f);
                        initialTranslation=value.getTranslationX(); x=event.getX();y=event.getY();horizontal=false;break;
                    case MotionEvent.ACTION_MOVE:
                        if(Math.abs(dx)>dp(10)&&Math.abs(dx)>Math.abs(dy)) {
                            horizontal=true;getParent().requestDisallowInterceptTouchEvent(true);setPressed(false);
                        }
                        if(horizontal)value.setTranslationX(Math.max(-value.getWidth(),Math.min(value.getWidth(),initialTranslation+dx)));
                        return horizontal;
                    case MotionEvent.ACTION_UP:
                        horizontal|=Math.abs(dx)>=dp(36)&&Math.abs(dx)>Math.abs(dy);
                        getParent().requestDisallowInterceptTouchEvent(false);
                        if(horizontal){setPressed(false);if(Math.abs(dx)>=dp(36))shift(dx<0?1:-1);else settle();return true;}
                        value.setTranslationX(0f);break;
                    case MotionEvent.ACTION_CANCEL:
                        getParent().requestDisallowInterceptTouchEvent(false);setPressed(false);settle();return horizontal;
                    default:break;
                }
                return false;
            }
        });
    }
    void setText(String text) { desired=text; if(!moving)value.setText(text); }
    String text() { return desired; }
    private void cancelMotion(){generation++;value.animate().withEndAction(null).cancel();moving=false;}
    private void settle(){cancelMotion();value.setText(desired);value.animate().translationX(0f).alpha(1f).setDuration(UiMotion.isEnabled()?180:0).start();}
    private void shift(int direction) {
        cancelMotion();moving=true;
        if(!shift.test(direction)){settle();return;}
        int token=generation;
        if(!UiMotion.isEnabled()){moving=false;value.setText(desired);value.setTranslationX(0);value.setAlpha(1);return;}
        value.animate().translationX(direction>0?-value.getWidth():value.getWidth()).alpha(0f).setDuration(110).withEndAction(()->{
            if(token!=generation)return;
            value.setText(desired);value.setTranslationX(direction>0?value.getWidth():-value.getWidth());value.setAlpha(0f);
            value.animate().translationX(0f).alpha(1f).setDuration(180).withEndAction(()->{if(token==generation)moving=false;}).start();
        }).start();
    }
    @Override protected void onDetachedFromWindow(){cancelMotion();super.onDetachedFromWindow();}
    private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
}
