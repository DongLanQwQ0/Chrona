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
import android.view.animation.DecelerateInterpolator;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Soft, locally drawn color light beneath translucent surfaces. */
public final class GlassBackdropView extends View {
    private static final Object WALLPAPER_CACHE_LOCK = new Object();
    private static final Object WALLPAPER_DISK_CACHE_LOCK = new Object();
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
    private ValueAnimator wallpaperEntrance;
    private float wallpaperOpacity = 1f;
    private Bitmap backgroundImage;
    private Bitmap blurredWallpaper;
    private Bitmap pendingBackgroundImage;
    private int pendingBackgroundLoadToken;
    private volatile int blurBuildToken;
    private int requestedBlurStrength = -1;
    private boolean requestedGaussian;
    private int backgroundLoadToken;
    private String loadedBackground;
    private boolean blurFailed;
    private final Paint imagePaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final RectF blurDestination = new RectF();
    private final int[] backdropLocation = new int[2];
    private final int[] targetLocation = new int[2];
    private final Path surfaceClip = new Path();
    private final RectF surfaceBounds = new RectF();

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
        blurFailed = false;
        blurBuildToken++;
        backgroundLoadToken++;
        if (requested == null) {
            pendingBackgroundImage = null;
            backgroundImage = null;
            clearBlurredWallpaper();
            invalidate();
            if (getParent() instanceof View parent) parent.invalidate();
        } else {
            // Keep drawing the theme surface while the image and its matching blur prepare.
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
            } catch (Exception | OutOfMemoryError ignored) {
                post(() -> {
                    if (loadToken == backgroundLoadToken) {
                        invalidateAcrylicHosts();
                    }
                });
            }
        }, "chrona-background-image").start();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        refreshBackground();
    }

    @Override protected void onDetachedFromWindow() {
        stop();
        blurBuildToken++;
        backgroundLoadToken++;
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
        if (backgroundImage != null && width > 0 && height > 0
                && (!ThemeStore.acrylicEnabled(getContext()) || blurredWallpaper != null || blurFailed)) {
            imagePaint.setAlpha(Math.round(255 * wallpaperOpacity));
            drawWallpaper(canvas, backgroundImage, width, height, imagePaint);
            imagePaint.setAlpha(255);
            if (ThemeStore.dark(getContext())) {
                canvas.drawColor(Color.argb(Math.round(128 * wallpaperOpacity), 0, 0, 0));
                canvas.drawColor(tint(palette.background, Math.round(76 * wallpaperOpacity)));
            } else {
                canvas.drawColor(tint(palette.background, Math.round(164 * wallpaperOpacity)));
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
        String wallpaperUri = loadedBackground;
        requestedGaussian = gaussian;
        requestedBlurStrength = strength;
        Bitmap cached = cachedBlurred(wallpaperUri, width, height, gaussian, strength);
        if (cached != null) {
            blurredWallpaper = cached;
            invalidateAcrylicHosts();
            return;
        }
        Thread worker = new Thread(() -> {
            String diskKey = diskBlurKey(wallpaperUri, source, width, height, gaussian,
                    strength);
            Bitmap result = loadDiskBlur(diskKey, width, height);
            boolean writeDiskCache = result == null;
            if (result == null)
                result = safeCreateBlurredWallpaper(source, width, height, gaussian, strength);
            Bitmap readyResult = result;
            post(() -> {
                if (token == blurBuildToken && isAttachedToWindow() && backgroundImage == source) {
                    if (readyResult != null) {
                        storeBlurred(wallpaperUri, source, readyResult, width, height, gaussian,
                                strength);
                        replaceBlurredWallpaper(readyResult);
                    } else {
                        blurFailed = true;
                        invalidateAcrylicHosts();
                    }
                } else if (readyResult != null && !readyResult.isRecycled()) {
                    readyResult.recycle();
                }
            });
            if (writeDiskCache && readyResult != null && !readyResult.isRecycled())
                saveDiskBlur(diskKey, readyResult, token);
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
        String wallpaperUri = loadedBackground;
        requestedGaussian = gaussian;
        requestedBlurStrength = strength;
        Bitmap cached = cachedBlurred(wallpaperUri, width, height, gaussian, strength);
        if (cached != null) {
            pendingBackgroundImage = null;
            backgroundImage = source;
            blurredWallpaper = cached;
            invalidateAcrylicHosts();
            return;
        }
        Thread worker = new Thread(() -> {
            String diskKey = diskBlurKey(wallpaperUri, source, width, height, gaussian,
                    strength);
            Bitmap result = loadDiskBlur(diskKey, width, height);
            boolean writeDiskCache = result == null;
            if (result == null)
                result = safeCreateBlurredWallpaper(source, width, height, gaussian, strength);
            Bitmap readyResult = result;
            post(() -> {
                if (blurToken == blurBuildToken && loadToken == backgroundLoadToken
                        && isAttachedToWindow() && source == pendingBackgroundImage) {
                    pendingBackgroundImage = null;
                    backgroundImage = source;
                    if (readyResult != null) storeBlurred(wallpaperUri, source, readyResult,
                            width, height, gaussian, strength);
                    else storeSource(wallpaperUri, source);
                    replaceBlurredWallpaper(readyResult);
                } else if (readyResult != null && !readyResult.isRecycled()) {
                    readyResult.recycle();
                }
            });
            if (writeDiskCache && readyResult != null && !readyResult.isRecycled())
                saveDiskBlur(diskKey, readyResult, blurToken);
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

    /** Stable, bounded disk cache for the expensive reduced-size wallpaper blur. */
    private String diskBlurKey(String uri, Bitmap source, int width, int height,
            boolean gaussian, int strength) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(uri.getBytes(StandardCharsets.UTF_8));
            digestInt(digest, source.getWidth());
            digestInt(digest, source.getHeight());
            digestInt(digest, width);
            digestInt(digest, height);
            digestInt(digest, gaussian ? 1 : 0);
            digestInt(digest, strength);
            // Detect providers that replace image bytes behind the same document URI without
            // hashing the full image on the startup path.
            for (int gy = 0; gy < 5; gy++) {
                int y = gy * Math.max(0, source.getHeight() - 1) / 4;
                for (int gx = 0; gx < 5; gx++) {
                    int x = gx * Math.max(0, source.getWidth() - 1) / 4;
                    digestInt(digest, source.getPixel(x, y));
                }
            }
            StringBuilder key = new StringBuilder(64);
            for (byte value : digest.digest()) key.append(String.format(java.util.Locale.ROOT,
                    "%02x", value & 0xff));
            return key.toString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static void digestInt(MessageDigest digest, int value) {
        digest.update(ByteBuffer.allocate(4).putInt(value).array());
    }

    private Bitmap loadDiskBlur(String key, int width, int height) {
        if (key == null) return null;
        File file = new File(getContext().getCacheDir(), "chrona-wallpaper-blur-" + key + ".png");
        if (!file.isFile()) return null;
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap cached;
        try {
            cached = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
        } catch (RuntimeException | OutOfMemoryError ignored) {
            file.delete();
            return null;
        }
        int expectedWidth = sampleDimension(width, height, true);
        int expectedHeight = sampleDimension(width, height, false);
        if (cached != null && cached.getWidth() == expectedWidth
                && cached.getHeight() == expectedHeight) return cached;
        if (cached != null && !cached.isRecycled()) cached.recycle();
        // A corrupt/obsolete cache is disposable; the original wallpaper remains untouched.
        file.delete();
        return null;
    }

    private void saveDiskBlur(String key, Bitmap bitmap, int buildToken) {
        if (key == null || bitmap == null || bitmap.isRecycled()) return;
        File directory = getContext().getCacheDir();
        File target = new File(directory, "chrona-wallpaper-blur-" + key + ".png");
        File temporary = new File(directory, "chrona-wallpaper-blur-" + key + ".tmp");
        synchronized (WALLPAPER_DISK_CACHE_LOCK) {
            if (buildToken != blurBuildToken || target.isFile()) return;
            try {
                try (FileOutputStream output = new FileOutputStream(temporary)) {
                    if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                        temporary.delete();
                        return;
                    }
                    output.flush();
                    output.getFD().sync();
                }
                if (!temporary.renameTo(target)) {
                    temporary.delete();
                    return;
                }
                File[] stale = directory.listFiles((dir, name) ->
                        name.startsWith("chrona-wallpaper-blur-")
                                && !name.equals(target.getName()));
                if (stale != null) for (File file : stale) file.delete();
            } catch (Exception ignored) {
                temporary.delete();
                // Cache I/O is best-effort; the in-memory result is already available to this view.
            }
        }
    }

    private static int sampleDimension(int width, int height, boolean horizontal) {
        float scale = Math.min(1f, Math.min(540f / width, 1080f / height));
        return Math.max(1, Math.round((horizontal ? width : height) * scale));
    }

    private void replaceBlurredWallpaper(Bitmap replacement) {
        blurredWallpaper = replacement;
        blurFailed = replacement == null;
        if (wallpaperEntrance != null) wallpaperEntrance.cancel();
        wallpaperOpacity = 1f;
        if (ValueAnimator.areAnimatorsEnabled() && isShown()) {
            wallpaperEntrance = ValueAnimator.ofFloat(0f, 1f);
            wallpaperEntrance.setDuration(UiMotion.EXIT);
            wallpaperEntrance.setInterpolator(UiMotion.SETTLE);
            java.util.List<View> surfaces = new java.util.ArrayList<>();
            UiStyle.collectAcrylicSurfaces(getRootView(), surfaces);
            wallpaperEntrance.addUpdateListener(animation -> {
                wallpaperOpacity = (float) animation.getAnimatedValue();
                invalidate();
                for (View surface : surfaces) if (surface.isAttachedToWindow()) surface.invalidate();
            });
            wallpaperEntrance.start();
        }
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

    private static final android.util.LruCache<String, Bitmap> WIDGET_WALLPAPERS =
            new android.util.LruCache<>(2);

    /** Background worker only. Borrowed full-screen bitmap; callers must not recycle it. */
    static Bitmap blurredForWidget(Context context) {
        String uri = ThemeStore.background(context);
        if (uri == null) return null;
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        int width = Math.max(1, metrics.widthPixels);
        int height = Math.max(1, metrics.heightPixels);
        boolean gaussian = ThemeStore.gaussianBlur(context);
        int strength = ThemeStore.blurStrength(context);
        String key = uri + ":" + ThemeStore.revision(context) + ":" + width + ":" + height
                + ":" + gaussian + ":" + strength;
        Bitmap cached = WIDGET_WALLPAPERS.get(key);
        if (cached != null) return cached;
        Bitmap source = cachedSource(uri);
        boolean owned = source == null;
        try {
            if (source == null) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(uri))) {
                    BitmapFactory.decodeStream(input, null, bounds);
                }
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (Math.max(bounds.outWidth, bounds.outHeight) / options.inSampleSize > 1024)
                    options.inSampleSize *= 2;
                try (InputStream input = context.getContentResolver().openInputStream(Uri.parse(uri))) {
                    source = BitmapFactory.decodeStream(input, null, options);
                }
            }
            if (source == null) return null;
            Bitmap blurred = createBlurredWallpaper(source, width, height, gaussian, strength);
            WIDGET_WALLPAPERS.put(key, blurred);
            return blurred;
        } catch (Exception | OutOfMemoryError ignored) {
            return null;
        } finally {
            if (owned && source != null) source.recycle();
        }
    }

    private static Bitmap createBlurredWallpaper(Bitmap source, int width, int height,
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

    private static void drawWallpaper(Canvas canvas, Bitmap image, float width, float height, Paint paint) {
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
        getLocationInWindow(backdropLocation);
        target.getLocationInWindow(targetLocation);
        int left = targetLocation[0] - backdropLocation[0];
        int top = targetLocation[1] - backdropLocation[1];
        int save = canvas.save();
        surfaceClip.rewind();
        surfaceBounds.set(destination);
        surfaceClip.addRoundRect(surfaceBounds, cornerRadius, cornerRadius, Path.Direction.CW);
        canvas.clipPath(surfaceClip);
        canvas.drawColor(palette.background);
        canvas.translate(-left, -top);
        imagePaint.setAlpha(Math.round(255 * wallpaperOpacity));
        canvas.drawBitmap(blurredWallpaper, null, blurDestination, imagePaint);
        imagePaint.setAlpha(255);
        canvas.restoreToCount(save);

        save = canvas.save();
        canvas.clipPath(surfaceClip);
        if (ThemeStore.dark(getContext())) {
            canvas.drawColor(Color.argb(Math.round(128 * wallpaperOpacity), 0, 0, 0));
            canvas.drawColor(tint(palette.background, Math.round(76 * wallpaperOpacity)));
        } else {
            canvas.drawColor(tint(palette.background, Math.round(164 * wallpaperOpacity)));
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
        if (wallpaperEntrance != null) {
            wallpaperEntrance.cancel();
            wallpaperEntrance = null;
            wallpaperOpacity = 1f;
            invalidateAcrylicHosts();
        }
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
