package com.donglan.chrona;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.animation.DecelerateInterpolator;

import java.io.InputStream;

/** Soft, locally drawn color light beneath translucent surfaces. */
public final class GlassBackdropView extends View {
    private static final Object WALLPAPER_CACHE_LOCK = new Object();
    private static WallpaperCache wallpaperCache;

    /** Process-local decoded wallpaper and its one current-size blur result. */
    private static final class WallpaperCache {
        final String uri;
        final Bitmap source;
        Bitmap blurred;
        int width, height;
        boolean gaussian;
        int strength;
        WallpaperCache(String uri, Bitmap source) {
            this.uri = uri;
            this.source = source;
        }
    }

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float reveal;
    private ValueAnimator entrance;
    private Bitmap backgroundImage;
    private Bitmap blurredWallpaper;
    private Bitmap pendingBackgroundImage;
    private int pendingBackgroundLoadToken;
    private int blurBuildToken;
    private int requestedBlurStrength = -1;
    private boolean requestedGaussian;
    private int backgroundLoadToken;
    private String loadedBackground;
    private boolean wallpaperPreparationFailed;
    private boolean firstFrameDrawn;
    private ViewTreeObserver.OnPreDrawListener firstFrameGate;
    private Runnable firstFrameTimeout;
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF blurDestination = new RectF();

    public GlassBackdropView(Context context) {
        super(context);
        loadedBackground = ThemeStore.background(context);
        if (loadedBackground != null) {
            backgroundImage = cachedSource(loadedBackground);
            if (backgroundImage == null) loadImage(loadedBackground);
        }
    }

    void refreshBackground() {
        String requested = ThemeStore.background(getContext());
        if (java.util.Objects.equals(requested, loadedBackground)) return;
        loadedBackground = requested;
        wallpaperPreparationFailed = false;
        blurBuildToken++;
        backgroundLoadToken++;
        if (requested == null) {
            pendingBackgroundImage = null;
            backgroundImage = null;
            clearBlurredWallpaper();
            invalidate();
            if (getParent() instanceof View parent) parent.invalidate();
        } else {
            Bitmap cached = cachedSource(requested);
            if (cached != null) {
                backgroundImage = cached;
                boolean gaussian = ThemeStore.gaussianBlur(getContext());
                Bitmap cachedBlur = cachedBlurred(requested, getWidth(), getHeight(), gaussian,
                        ThemeStore.blurStrength(getContext()));
                if (cachedBlur != null) blurredWallpaper = cachedBlur;
                else rebuildBlurredWallpaper();
                invalidateAcrylicHosts();
            } else loadImage(requested);
        }
    }

    private void loadImage(String value) {
        int loadToken = ++backgroundLoadToken;
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
                    if (loadToken == backgroundLoadToken
                            && java.util.Objects.equals(value, loadedBackground)) {
                        // Publish the decoded image before blur finishes so a page opened during
                        // preparation can reuse it instead of decoding the same URI again.
                        storeSource(value, image);
                        pendingBackgroundImage = image;
                        pendingBackgroundLoadToken = loadToken;
                        preparePendingBackground();
                    } else image.recycle();
                });
            } catch (Exception ignored) {
                post(() -> {
                    if (loadToken == backgroundLoadToken) {
                        wallpaperPreparationFailed = true;
                        releaseFirstFrameGate();
                    }
                });
            }
        }, "chrona-background-image").start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshBackground();
        installFirstFrameGate();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        blurBuildToken++;
        backgroundLoadToken++;
        releaseFirstFrameGate();
        if (blurredWallpaper != null) {
            clearBlurredWallpaper();
        }
        pendingBackgroundImage = null;
        // The blur worker may still be reading this source; let it become collectible afterward.
        backgroundImage = null;
        loadedBackground = null;
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        blurDestination.set(0, 0, width, height);
        if (pendingBackgroundImage != null) preparePendingBackground();
        else if (backgroundImage != null) {
            boolean gaussian = ThemeStore.gaussianBlur(getContext());
            Bitmap cached = cachedBlurred(loadedBackground, width, height, gaussian,
                    ThemeStore.blurStrength(getContext()));
            if (cached != null) {
                blurredWallpaper = cached;
                wallpaperPreparationFailed = false;
                invalidateAcrylicHosts();
            } else rebuildBlurredWallpaper();
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        UiStyle.Palette palette = UiStyle.colors(getContext());
        canvas.drawColor(palette.background);
        float width = getWidth();
        float height = getHeight();
        if (backgroundImage != null && width > 0 && height > 0) {
            drawWallpaper(canvas, backgroundImage, width, height, imagePaint);
            if (ThemeStore.dark(getContext())) {
                canvas.drawColor(Color.argb(128, 0, 0, 0));
                canvas.drawColor(tint(palette.background, 76));
            } else {
                canvas.drawColor(tint(palette.background, 164));
            }
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

    private void rebuildBlurredWallpaper() {
        Bitmap source = backgroundImage;
        int width = getWidth(), height = getHeight();
        int token = ++blurBuildToken;
        if (source == null || width <= 0 || height <= 0) {
            return;
        }
        boolean gaussian = ThemeStore.gaussianBlur(getContext());
        int strength = ThemeStore.blurStrength(getContext());
        requestedGaussian = gaussian;
        requestedBlurStrength = strength;
        Bitmap cached = cachedBlurred(loadedBackground, width, height, gaussian, strength);
        if (cached != null) {
            blurredWallpaper = cached;
            wallpaperPreparationFailed = false;
            invalidateAcrylicHosts();
            return;
        }
        Thread worker = new Thread(() -> {
            Bitmap result = safeCreateBlurredWallpaper(source, width, height, gaussian, strength);
            post(() -> {
                if (token == blurBuildToken && isAttachedToWindow() && backgroundImage == source) {
                    if (result != null) {
                        storeBlurred(loadedBackground, source, result, width, height, gaussian,
                                strength);
                        wallpaperPreparationFailed = false;
                        replaceBlurredWallpaper(result);
                    } else wallpaperPreparationFailed = true;
                } else if (result != null && !result.isRecycled()) {
                    result.recycle();
                }
            });
        }, "chrona-wallpaper-blur");
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private void preparePendingBackground() {
        Bitmap source = pendingBackgroundImage;
        int loadToken = pendingBackgroundLoadToken;
        int width = getWidth(), height = getHeight();
        if (source == null || width <= 0 || height <= 0) return;
        int blurToken = ++blurBuildToken;
        boolean gaussian = ThemeStore.gaussianBlur(getContext());
        int strength = ThemeStore.blurStrength(getContext());
        requestedGaussian = gaussian;
        requestedBlurStrength = strength;
        Bitmap cached = cachedBlurred(loadedBackground, width, height, gaussian, strength);
        if (cached != null) {
            pendingBackgroundImage = null;
            backgroundImage = source;
            blurredWallpaper = cached;
            wallpaperPreparationFailed = false;
            invalidateAcrylicHosts();
            return;
        }
        Thread worker = new Thread(() -> {
            Bitmap result = safeCreateBlurredWallpaper(source, width, height, gaussian, strength);
            post(() -> {
                if (blurToken == blurBuildToken && loadToken == backgroundLoadToken
                        && isAttachedToWindow() && source == pendingBackgroundImage) {
                    pendingBackgroundImage = null;
                    backgroundImage = source;
                    if (result != null) storeBlurred(loadedBackground, source, result,
                            width, height, gaussian, strength);
                    else storeSource(loadedBackground, source);
                    wallpaperPreparationFailed = result == null;
                    replaceBlurredWallpaper(result);
                } else if (result != null && !result.isRecycled()) {
                    result.recycle();
                }
            });
        }, "chrona-wallpaper-prepare");
        worker.setPriority(Thread.NORM_PRIORITY - 1);
        worker.start();
    }

    private Bitmap safeCreateBlurredWallpaper(Bitmap source, int width, int height,
            boolean gaussian, int strength) {
        try {
            return createBlurredWallpaper(source, width, height, gaussian, strength);
        } catch (RuntimeException | OutOfMemoryError ignored) {
            return null;
        }
    }

    private void replaceBlurredWallpaper(Bitmap replacement) {
        blurredWallpaper = replacement;
        if (replacement != null) wallpaperPreparationFailed = false;
        invalidateAcrylicHosts();
    }

    private void invalidateAcrylicHosts() {
        invalidate();
        View root = getRootView();
        root.invalidate();
        UiStyle.invalidateAcrylicSurfaces(root);
    }

    private void clearBlurredWallpaper() {
        blurredWallpaper = null;
    }

    void refreshBlur() {
        if (pendingBackgroundImage != null) preparePendingBackground();
        else rebuildBlurredWallpaper();
        invalidate();
    }

    /** Redraws the wallpaper tint and its sampled acrylic hosts after a palette change. */
    void refreshTheme() {
        invalidate();
        invalidateAcrylicHosts();
    }

    private void installFirstFrameGate() {
        if (firstFrameDrawn || loadedBackground == null || firstFrameGate != null) return;
        View root = getRootView();
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (!observer.isAlive()) return;
        firstFrameGate = () -> {
            if (!wallpaperReady()) return false;
            firstFrameDrawn = true;
            releaseFirstFrameGate();
            return true;
        };
        observer.addOnPreDrawListener(firstFrameGate);
        firstFrameTimeout = () -> {
            if (firstFrameGate == null) return;
            wallpaperPreparationFailed = true;
            firstFrameDrawn = true;
            releaseFirstFrameGate();
            root.invalidate();
        };
        // Providers can stall or revoke access; never leave the Activity blocked indefinitely.
        root.postDelayed(firstFrameTimeout, 2500);
    }

    private boolean wallpaperReady() {
        if (loadedBackground == null || wallpaperPreparationFailed) return true;
        if (backgroundImage == null) return false;
        return !ThemeStore.acrylicEnabled(getContext()) || blurredWallpaper != null;
    }

    private void releaseFirstFrameGate() {
        if (firstFrameGate == null) return;
        View root = getRootView();
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (observer.isAlive()) observer.removeOnPreDrawListener(firstFrameGate);
        if (firstFrameTimeout != null) root.removeCallbacks(firstFrameTimeout);
        firstFrameGate = null;
        firstFrameTimeout = null;
    }

    private static Bitmap cachedSource(String uri) {
        synchronized (WALLPAPER_CACHE_LOCK) {
            return wallpaperCache != null && !wallpaperCache.source.isRecycled()
                    && java.util.Objects.equals(uri, wallpaperCache.uri)
                    ? wallpaperCache.source : null;
        }
    }

    private static Bitmap cachedBlurred(String uri, int width, int height, boolean gaussian,
            int strength) {
        synchronized (WALLPAPER_CACHE_LOCK) {
            if (wallpaperCache == null || !java.util.Objects.equals(uri, wallpaperCache.uri)
                    || wallpaperCache.width != width || wallpaperCache.height != height
                    || wallpaperCache.gaussian != gaussian || wallpaperCache.strength != strength
                    || wallpaperCache.blurred == null
                    || wallpaperCache.blurred.isRecycled()) return null;
            return wallpaperCache.blurred;
        }
    }

    private static void storeSource(String uri, Bitmap source) {
        if (uri == null || source == null || source.isRecycled()) return;
        synchronized (WALLPAPER_CACHE_LOCK) {
            if (wallpaperCache == null || !java.util.Objects.equals(uri, wallpaperCache.uri)
                    || wallpaperCache.source != source)
                wallpaperCache = new WallpaperCache(uri, source);
        }
    }

    private static void storeBlurred(String uri, Bitmap source, Bitmap blurred,
            int width, int height, boolean gaussian, int strength) {
        if (uri == null || source == null || blurred == null) return;
        synchronized (WALLPAPER_CACHE_LOCK) {
            WallpaperCache entry = wallpaperCache;
            if (entry == null || !java.util.Objects.equals(uri, entry.uri)
                    || entry.source != source) {
                entry = new WallpaperCache(uri, source);
                wallpaperCache = entry;
            }
            entry.blurred = blurred;
            entry.width = width;
            entry.height = height;
            entry.gaussian = gaussian;
            entry.strength = strength;
        }
    }

    private Bitmap createBlurredWallpaper(Bitmap source, int width, int height,
            boolean gaussian, int strength) {
        float sampleScale = Math.min(1f, Math.min(540f / width, 1080f / height));
        int sampleWidth = Math.max(1, Math.round(width * sampleScale));
        int sampleHeight = Math.max(1, Math.round(height * sampleScale));
        Bitmap sample = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(sample);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        drawWallpaper(canvas, source, sampleWidth, sampleHeight, paint);
        int[] pixels = new int[sampleWidth * sampleHeight];
        sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight);
        if (gaussian) {
            gaussianBlur(pixels, sampleWidth, sampleHeight, 5 + strength * 2,
                    2.8f + strength * 1.2f);
        } else {
            int radius = 1 + strength;
            boxBlur(pixels, sampleWidth, sampleHeight, radius, true);
            boxBlur(pixels, sampleWidth, sampleHeight, radius, false);
        }
        Bitmap result = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888);
        result.setPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight);
        sample.recycle();
        return result;
    }

    /** Applies a separable Gaussian kernel to the reduced wallpaper bitmap. */
    private static void gaussianBlur(int[] pixels, int width, int height, int radius,
            float sigma) {
        float[] kernel = new float[radius * 2 + 1];
        float total = 0f;
        for (int offset = -radius; offset <= radius; offset++) {
            float weight = (float) Math.exp(-(offset * offset) / (2f * sigma * sigma));
            kernel[offset + radius] = weight;
            total += weight;
        }
        for (int i = 0; i < kernel.length; i++) kernel[i] /= total;
        convolve(pixels, width, height, radius, kernel, true);
        convolve(pixels, width, height, radius, kernel, false);
    }

    private static void convolve(int[] pixels, int width, int height, int radius,
            float[] kernel, boolean horizontal) {
        int[] source = pixels.clone();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                float a = 0f, r = 0f, g = 0f, b = 0f;
                for (int offset = -radius; offset <= radius; offset++) {
                    int sampleX = horizontal ? Math.max(0, Math.min(width - 1, x + offset)) : x;
                    int sampleY = horizontal ? y : Math.max(0, Math.min(height - 1, y + offset));
                    int color = source[sampleY * width + sampleX];
                    float weight = kernel[offset + radius];
                    a += Color.alpha(color) * weight;
                    r += Color.red(color) * weight;
                    g += Color.green(color) * weight;
                    b += Color.blue(color) * weight;
                }
                pixels[y * width + x] = Color.argb(Math.round(a), Math.round(r),
                        Math.round(g), Math.round(b));
            }
        }
    }

    /** Single-pass separable box blur used by the lightweight ordinary mode. */
    private static void boxBlur(int[] pixels, int width, int height, int radius,
            boolean horizontal) {
        int[] source = pixels.clone();
        int major = horizontal ? height : width;
        int minor = horizontal ? width : height;
        int window = radius * 2 + 1;
        for (int line = 0; line < major; line++) {
            long a = 0, r = 0, g = 0, b = 0;
            for (int offset = -radius; offset <= radius; offset++) {
                int sampleOffset = Math.max(0, Math.min(minor - 1, offset));
                int color = source[horizontal ? line * width + sampleOffset
                        : sampleOffset * width + line];
                a += Color.alpha(color); r += Color.red(color);
                g += Color.green(color); b += Color.blue(color);
            }
            for (int position = 0; position < minor; position++) {
                int index = horizontal ? line * width + position : position * width + line;
                pixels[index] = Color.argb((int) (a / window), (int) (r / window),
                        (int) (g / window), (int) (b / window));
                int removeAt = Math.max(0, position - radius);
                int addAt = Math.min(minor - 1, position + radius + 1);
                int remove = source[horizontal ? line * width + removeAt
                        : removeAt * width + line];
                int add = source[horizontal ? line * width + addAt : addAt * width + line];
                a += Color.alpha(add) - Color.alpha(remove);
                r += Color.red(add) - Color.red(remove);
                g += Color.green(add) - Color.green(remove);
                b += Color.blue(add) - Color.blue(remove);
            }
        }
    }

    private void drawWallpaper(Canvas canvas, Bitmap image, float width, float height, Paint paint) {
        int imageWidth = image.getWidth();
        int imageHeight = image.getHeight();
        float scale = Math.max(width / imageWidth, height / imageHeight);
        float drawnWidth = imageWidth * scale;
        float drawnHeight = imageHeight * scale;
        Rect source = new Rect(0, 0, imageWidth, imageHeight);
        RectF destination = new RectF((width - drawnWidth) / 2f, (height - drawnHeight) / 2f,
                (width + drawnWidth) / 2f, (height + drawnHeight) / 2f);
        canvas.drawBitmap(image, source, destination, paint);
    }

    static GlassBackdropView findFor(View target) {
        return findBackdrop(target.getRootView());
    }

    private static GlassBackdropView findBackdrop(View view) {
        if (view instanceof GlassBackdropView backdrop) return backdrop;
        if (view instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                GlassBackdropView found = findBackdrop(group.getChildAt(i));
                if (found != null) return found;
            }
        }
        return null;
    }

    boolean drawBlurredWallpaper(Canvas canvas, View target, Rect destination, int cornerRadius,
            UiStyle.Palette palette) {
        if (requestedBlurStrength != ThemeStore.blurStrength(getContext())
                || requestedGaussian != ThemeStore.gaussianBlur(getContext()))
            rebuildBlurredWallpaper();
        if (blurredWallpaper == null || blurredWallpaper.isRecycled()) return false;
        int[] backdropLocation = new int[2];
        int[] targetLocation = new int[2];
        getLocationInWindow(backdropLocation);
        target.getLocationInWindow(targetLocation);
        int left = targetLocation[0] - backdropLocation[0];
        int top = targetLocation[1] - backdropLocation[1];
        int save = canvas.save();
        Path clip = new Path();
        clip.addRoundRect(new RectF(destination), cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.clipPath(clip);
        canvas.translate(-left, -top);
        canvas.drawBitmap(blurredWallpaper, null, blurDestination, imagePaint);
        canvas.restoreToCount(save);

        save = canvas.save();
        canvas.clipPath(clip);
        if (ThemeStore.dark(getContext())) {
            canvas.drawColor(Color.argb(128, 0, 0, 0));
            canvas.drawColor(tint(palette.background, 76));
        } else {
            canvas.drawColor(tint(palette.background, 164));
        }
        canvas.restoreToCount(save);
        return true;
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
