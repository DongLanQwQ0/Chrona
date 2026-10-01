package com.donglan.chrona;

import android.app.Activity;
import android.net.Uri;
import android.view.Gravity;
import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;

/** Shared cropped image preview with a themed, accessible remove affordance. */
final class AttachmentImageTile extends FrameLayout {
    private static final java.util.concurrent.ExecutorService IMAGES =
            java.util.concurrent.Executors.newFixedThreadPool(2);
    private static final android.util.LruCache<String, android.graphics.Bitmap> CACHE =
            new android.util.LruCache<>(4 * 1024 * 1024) {
                @Override protected int sizeOf(String key, android.graphics.Bitmap bitmap) {
                    return bitmap.getAllocationByteCount();
                }
            };
    private final ImageView preview;
    private final Uri imageUri;
    private java.util.concurrent.Future<?> pending;
    private int generation;

    AttachmentImageTile(Activity activity, Uri imageUri, String imageName,
            Runnable onOpen, Runnable onRemove) {
        super(activity);
        setClipChildren(true);
        setClipToPadding(true);
        setClipToOutline(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(12));
            }
        });

        this.imageUri = imageUri;
        preview = new ImageView(activity);
        preview.setScaleType(ImageView.ScaleType.CENTER_CROP);
        preview.setBackgroundColor(UiStyle.colors(activity).surfaceAlt);
        preview.setContentDescription("查看图片原图，可放大并保存：" + imageName);
        preview.setOnClickListener(view -> onOpen.run());
        addView(preview, new FrameLayout.LayoutParams(-1, -1));

        Button remove = new Button(activity);
        remove.setText("×");
        remove.setTextSize(16);
        remove.setGravity(Gravity.CENTER);
        remove.setPadding(0, 0, 0, 0);
        remove.setTextColor(UiStyle.colors(activity).primary);
        remove.setContentDescription("移除图片：" + imageName);
        remove.setMinimumWidth(dp(36));
        remove.setMinimumHeight(dp(36));
        UiStyle.glass(remove);
        UiStyle.pressable(remove);
        remove.setOnClickListener(view -> onRemove.run());
        FrameLayout.LayoutParams removeParams = new FrameLayout.LayoutParams(dp(36), dp(36),
                Gravity.TOP | Gravity.END);
        removeParams.setMargins(0, dp(6), dp(6), 0);
        addView(remove, removeParams);
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (preview.getDrawable() != null) return;
        int target = Math.min(512, Math.max(128, dp(180)));
        String key = imageUri + ":" + target;
        android.graphics.Bitmap cached = CACHE.get(key);
        if (cached != null) {
            preview.setImageBitmap(cached);
            return;
        }
        int request = ++generation;
        java.lang.ref.WeakReference<AttachmentImageTile> owner = new java.lang.ref.WeakReference<>(this);
        android.content.Context context = getContext().getApplicationContext();
        Uri uri = imageUri;
        pending = IMAGES.submit(() -> {
            android.graphics.Bitmap image = decode(context, uri, target);
            if (image == null) return;
            CACHE.put(key, image);
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                AttachmentImageTile tile = owner.get();
                if (tile == null || tile.generation != request || !tile.isAttachedToWindow()) return;
                tile.preview.setImageBitmap(image);
                if (android.animation.ValueAnimator.areAnimatorsEnabled()) {
                    tile.preview.setAlpha(0f);
                    tile.preview.animate().alpha(1f).setDuration(UiMotion.EXIT)
                            .setInterpolator(UiMotion.SETTLE).start();
                }
            });
        });
    }

    private static android.graphics.Bitmap decode(android.content.Context context, Uri uri, int target) {
        try {
            android.graphics.BitmapFactory.Options options = new android.graphics.BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            try (java.io.InputStream input = context.getContentResolver().openInputStream(uri)) {
                android.graphics.BitmapFactory.decodeStream(input, null, options);
            }
            options.inJustDecodeBounds = false;
            options.inSampleSize = 1;
            while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > target)
                options.inSampleSize *= 2;
            try (java.io.InputStream input = context.getContentResolver().openInputStream(uri)) {
                return android.graphics.BitmapFactory.decodeStream(input, null, options);
            }
        } catch (java.io.IOException | RuntimeException | OutOfMemoryError ignored) {
            return null;
        }
    }

    @Override protected void onDetachedFromWindow() {
        generation++;
        if (pending != null) pending.cancel(false);
        preview.animate().cancel();
        preview.setAlpha(1f);
        super.onDetachedFromWindow();
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
