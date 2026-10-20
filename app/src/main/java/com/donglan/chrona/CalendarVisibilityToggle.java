package com.donglan.chrona;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.widget.CheckBox;

/** Native accessible icon toggle; press fill belongs to the shared control surface. */
final class CalendarVisibilityToggle extends CheckBox {
    private final Drawable icon;
    private final android.animation.ArgbEvaluator tint = new android.animation.ArgbEvaluator();
    private ValueAnimator transition;
    private float checkedFraction;
    CalendarVisibilityToggle(Context context) {
        super(context);
        icon = context.getDrawable(R.drawable.ic_week_calendar).mutate();
        setButtonDrawable((Drawable)null);
        setBackgroundColor(Color.TRANSPARENT);
        UiStyle.pressable(this);
    }

    @Override public void setChecked(boolean checked) {
        boolean changed = checked != isChecked();
        super.setChecked(checked);
        if (!changed) return;
        if (transition != null) transition.cancel();
        // A permission listener can synchronously reject the requested state.
        float target = isChecked() ? 1f : 0f;
        if (!isAttachedToWindow() || !UiMotion.isEnabled()) {
            checkedFraction = target;
            invalidate();
            return;
        }
        transition = ValueAnimator.ofFloat(checkedFraction, target);
        transition.setDuration(UiMotion.ENTER);
        transition.setInterpolator(UiMotion.SETTLE);
        transition.addUpdateListener(value -> {
            checkedFraction = (float)value.getAnimatedValue();
            invalidate();
        });
        transition.start();
    }

    @Override protected void onDraw(Canvas canvas) {
        UiStyle.Palette palette = UiStyle.colors(getContext());
        float density = getResources().getDisplayMetrics().density;
        icon.setTint((Integer)tint.evaluate(checkedFraction, palette.muted, palette.primary));
        int size = Math.round(24 * density * (1f + .08f * (float)Math.sin(Math.PI * checkedFraction)));
        int left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
        icon.setBounds(left, top, left + size, top + size);
        icon.draw(canvas);
    }

    @Override protected void onDetachedFromWindow() {
        if (transition != null) transition.cancel();
        checkedFraction = isChecked() ? 1f : 0f;
        super.onDetachedFromWindow();
    }
}
