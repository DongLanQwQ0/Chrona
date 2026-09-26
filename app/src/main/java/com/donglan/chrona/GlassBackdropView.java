package com.donglan.chrona;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

/** Soft, locally drawn color light beneath translucent surfaces. */
final class GlassBackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float reveal;
    private ValueAnimator entrance;

    GlassBackdropView(Context context) { super(context); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        playEntrance();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        UiStyle.Palette palette = UiStyle.colors(getContext());
        canvas.drawColor(palette.background);
        float width = getWidth();
        float height = getHeight();
        float spread = Math.max(width, height);
        float drift = dp(22) * reveal;
        glow(canvas, width * .82f - drift, height * .10f + drift,
                spread * .54f, tint(palette.primaryContainer,
                        ThemeStore.dark(getContext()) ? 105 : 168));
        glow(canvas, width * .08f + drift, height * .69f - drift,
                spread * .42f, tint(palette.primary,
                        ThemeStore.dark(getContext()) ? 43 : 29));
        glow(canvas, width * .96f, height * .90f,
                spread * .38f, tint(palette.surfaceAlt, 145));
    }

    private void glow(Canvas canvas, float x, float y, float radius, int color) {
        if (radius <= 0) return;
        paint.setShader(new RadialGradient(x, y, radius,
                new int[]{color, color & 0x00FFFFFF},
                new float[]{0f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, paint);
        paint.setShader(null);
    }

    void playEntrance() {
        if (!ValueAnimator.areAnimatorsEnabled() || entrance != null) return;
        entrance = ValueAnimator.ofFloat(0f, 1f);
        entrance.setDuration(850);
        entrance.setInterpolator(new DecelerateInterpolator());
        entrance.addUpdateListener(animation -> {
            reveal = (float) animation.getAnimatedValue();
            invalidate();
        });
        entrance.start();
    }

    void stop() {
        if (entrance != null) {
            entrance.cancel();
            entrance = null;
        }
    }

    private int tint(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
    private float dp(int value) {
        return value * getResources().getDisplayMetrics().density;
    }
}
