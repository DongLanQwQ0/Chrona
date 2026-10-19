package com.donglan.chrona;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.widget.CheckBox;

/** A native accessible toggle with one circular surface and an interruptible state transition. */
final class CalendarVisibilityToggle extends CheckBox {
    private static final int SURFACE_INSET_DP = 4;
    private final Drawable icon;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final android.animation.ArgbEvaluator tint = new android.animation.ArgbEvaluator();
    private ValueAnimator transition;
    private float checkedFraction;

    CalendarVisibilityToggle(Context context) {
        super(context);
        icon = context.getDrawable(R.drawable.ic_week_calendar).mutate();
        setButtonDrawable((Drawable)null);
        setBackgroundColor(Color.TRANSPARENT);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(Color.WHITE);
        setForeground(new RippleDrawable(ColorStateList.valueOf(
                (UiStyle.colors(context).primary & 0x00ffffff) | (32 << 24)), null,
                new InsetDrawable(circle, surfaceInset())));
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
        fill.setColor((palette.primary & 0x00ffffff) | (Math.round(32 * checkedFraction) << 24));
        float density = getResources().getDisplayMetrics().density;
        canvas.drawCircle(getWidth() / 2f, getHeight() / 2f,
                Math.max(0, Math.min(getWidth(), getHeight()) / 2f - surfaceInset()), fill);
        icon.setTint((Integer)tint.evaluate(checkedFraction, palette.muted, palette.primary));
        int size = Math.round(24 * density * (1f + .08f * (float)Math.sin(Math.PI * checkedFraction)));
        int left = (getWidth() - size) / 2, top = (getHeight() - size) / 2;
        icon.setBounds(left, top, left + size, top + size);
        icon.draw(canvas);
    }

    private int surfaceInset() {
        return Math.round(SURFACE_INSET_DP * getResources().getDisplayMetrics().density);
    }

    @Override protected void onDetachedFromWindow() {
        if (transition != null) transition.cancel();
        checkedFraction = isChecked() ? 1f : 0f;
        super.onDetachedFromWindow();
    }
}
