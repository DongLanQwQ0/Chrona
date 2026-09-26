package com.donglan.chrona;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.io.InputStream;

/** Soft, locally drawn color light beneath translucent surfaces. */
public final class GlassBackdropView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float reveal;
    private ValueAnimator entrance;
    private Bitmap backgroundImage;
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Rect imageSource = new Rect();
    private final RectF imageDestination = new RectF();

    public GlassBackdropView(Context context) {
        super(context);
        String background = ThemeStore.background(context);
        if (background != null) loadImage(background);
    }

    private void loadImage(String value) {
        new Thread(() -> {
            try {
                Uri uri = Uri.parse(value);
                BitmapFactory.Options size = new BitmapFactory.Options();
                size.inJustDecodeBounds = true;
                try (InputStream input = getContext().getContentResolver().openInputStream(uri)) {
                    BitmapFactory.decodeStream(input, null, size);
                }
                int sample = 1;
                while (Math.max(size.outWidth / sample, size.outHeight / sample) > 2048)
                    sample *= 2;
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = sample;
                Bitmap image;
                try (InputStream input = getContext().getContentResolver().openInputStream(uri)) {
                    image = BitmapFactory.decodeStream(input, null, options);
                }
                if (image != null) post(() -> {
                    if (isAttachedToWindow()) {
                        backgroundImage = image;
                        invalidate();
                    } else image.recycle();
                });
            } catch (Exception ignored) {
                // A removed or unavailable document falls back to the theme background.
            }
        }, "chrona-background-image").start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        if (backgroundImage != null) {
            backgroundImage.recycle();
            backgroundImage = null;
        }
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        UiStyle.Palette palette = UiStyle.colors(getContext());
        canvas.drawColor(palette.background);
        float width = getWidth();
        float height = getHeight();
        if (backgroundImage != null && width > 0 && height > 0) {
            int imageWidth = backgroundImage.getWidth();
            int imageHeight = backgroundImage.getHeight();
            float scale = Math.max(width / imageWidth, height / imageHeight);
            float drawnWidth = imageWidth * scale;
            float drawnHeight = imageHeight * scale;
            imageSource.set(0, 0, imageWidth, imageHeight);
            imageDestination.set((width - drawnWidth) / 2f, (height - drawnHeight) / 2f,
                    (width + drawnWidth) / 2f, (height + drawnHeight) / 2f);
            canvas.drawBitmap(backgroundImage, imageSource, imageDestination, imagePaint);
            canvas.drawColor(tint(palette.background,
                    ThemeStore.dark(getContext()) ? 160 : 164));
        }
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
