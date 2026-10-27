package com.donglan.chrona;

import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.View;
import android.view.ViewTreeObserver;
import java.util.WeakHashMap;
import com.donglan.chrona.UiStyle.Palette;
import static com.donglan.chrona.UiStyle.colors;
import static com.donglan.chrona.UiStyle.radius;
import static com.donglan.chrona.UiStyle.alpha;
import static com.donglan.chrona.UiStyle.RADIUS_CARD;
import static com.donglan.chrona.UiStyle.RADIUS_PANEL;

/** Acrylic drawing, sampling, masks and invalidation; no page or business state. */
final class UiSurfaces {
    private static final WeakHashMap<View, Boolean> DIALOG_BLUR_ROOTS = new WeakHashMap<>();
    private static final WeakHashMap<View, AcrylicScrollWatcher> ACRYLIC_SCROLL_WATCHERS =
            new WeakHashMap<>();
    private UiSurfaces() { }

    /** Window blur is already supplied for these roots; child surfaces must not sample twice. */
    static void setDialogBlurRoot(View root, boolean enabled) {
        synchronized (DIALOG_BLUR_ROOTS) {
            if (enabled) DIALOG_BLUR_ROOTS.put(root, Boolean.TRUE);
            else DIALOG_BLUR_ROOTS.remove(root);
        }
    }

    private static boolean insideDialogBlur(View view) {
        for (android.view.ViewParent parent = view.getParent(); parent instanceof View ancestor;
                parent = ancestor.getParent()) {
            synchronized (DIALOG_BLUR_ROOTS) {
                if (DIALOG_BLUR_ROOTS.containsKey(ancestor)) return true;
            }
        }
        return false;
    }

    /** Invalidates visible translucent surfaces when a parent scroll changes their backdrop. */
    private static final class AcrylicScrollWatcher
            implements ViewTreeObserver.OnScrollChangedListener, ViewTreeObserver.OnGlobalLayoutListener,
            ViewTreeObserver.OnPreDrawListener, Runnable,
            View.OnAttachStateChangeListener {
        private final java.lang.ref.WeakReference<ViewTreeObserver> observer;
        private final java.lang.ref.WeakReference<View> root;
        private boolean queued;
        private final java.util.ArrayList<java.lang.ref.WeakReference<View>> hosts =
                new java.util.ArrayList<>();
        private final android.graphics.Rect visible = new android.graphics.Rect();
        private final WeakHashMap<View, android.graphics.Matrix> positions = new WeakHashMap<>();
        private final android.graphics.Matrix currentPosition = new android.graphics.Matrix();
        AcrylicScrollWatcher(View root, ViewTreeObserver observer) {
            this.root = new java.lang.ref.WeakReference<>(root);
            this.observer = new java.lang.ref.WeakReference<>(observer);
            root.addOnAttachStateChangeListener(this);
        }
        void addHost(View host) {
            java.util.Iterator<java.lang.ref.WeakReference<View>> iterator = hosts.iterator();
            while (iterator.hasNext()) {
                View existing = iterator.next().get();
                if (existing == null) iterator.remove();
                else if (existing == host) return;
            }
            hosts.add(new java.lang.ref.WeakReference<>(host));
        }
        @Override public void onScrollChanged() {
            View view = root.get();
            if (queued || view == null || !view.isAttachedToWindow()) return;
            queued = true;
            view.postOnAnimation(this);
        }
        @Override public void onGlobalLayout() {
            // A retained card can move without changing its own size (for example
            // when the todo above it folds). Re-record its wallpaper sample now,
            // before this layout's draw, rather than rebuilding the card hierarchy.
            View view = root.get();
            if (view == null || !view.isAttachedToWindow()) return;
            view.removeCallbacks(this);
            run();
        }
        @Override public boolean onPreDraw() {
            // ChangeBounds and property animations can move a retained RenderNode without
            // another layout or scroll event. Re-record its sampled background in this frame.
            for (java.lang.ref.WeakReference<View> reference : hosts) {
                View host = reference.get();
                if (host == null || !host.isAttachedToWindow()
                        || !host.getGlobalVisibleRect(visible)) continue;
                currentPosition.reset();
                transformToRoot(host, currentPosition);
                android.graphics.Matrix previous = positions.get(host);
                if (previous == null) {
                    positions.put(host, new android.graphics.Matrix(currentPosition));
                    host.invalidate();
                } else if (!previous.equals(currentPosition)) {
                    previous.set(currentPosition);
                    host.invalidate();
                }
            }
            return true;
        }
        @Override public void run() {
            queued = false;
            java.util.Iterator<java.lang.ref.WeakReference<View>> iterator = hosts.iterator();
            while (iterator.hasNext()) {
                View host = iterator.next().get();
                if (host == null) {
                    iterator.remove();
                } else if (host.isAttachedToWindow()
                        && host.getGlobalVisibleRect(visible)) {
                    host.invalidate();
                }
            }
        }
        @Override public void onViewAttachedToWindow(View view) { }
        @Override public void onViewDetachedFromWindow(View view) {
            view.removeCallbacks(this);
            queued = false;
            ViewTreeObserver tree = observer.get();
            if (tree != null && tree.isAlive()) {
                tree.removeOnScrollChangedListener(this);
                tree.removeOnGlobalLayoutListener(this);
                tree.removeOnPreDrawListener(this);
            }
            hosts.clear();
            positions.clear();
            ACRYLIC_SCROLL_WATCHERS.remove(view);
            view.removeOnAttachStateChangeListener(this);
        }
    }

    static boolean isCardSurface(View view) {
        return view.getBackground() instanceof AcrylicSurfaceDrawable surface
                && (surface.radius == RADIUS_CARD || surface.radius == RADIUS_PANEL);
    }

    /** Maps local pixels into the shared root, including every ancestor's scroll and transform. */
    static void transformToRoot(View view, android.graphics.Matrix matrix) {
        if (view.getParent() instanceof View parent) {
            transformToRoot(parent, matrix);
            matrix.preTranslate(-parent.getScrollX(), -parent.getScrollY());
        }
        matrix.preTranslate(view.getLeft(), view.getTop());
        if (!view.getMatrix().isIdentity()) matrix.preConcat(view.getMatrix());
    }

    private static void watchAcrylicScroll(View host) {
        View root = host.getRootView();
        ViewTreeObserver observer = root.getViewTreeObserver();
        if (!observer.isAlive()) return;
        synchronized (ACRYLIC_SCROLL_WATCHERS) {
            AcrylicScrollWatcher watcher = ACRYLIC_SCROLL_WATCHERS.get(root);
            if (watcher == null || watcher.observer.get() != observer) {
                if (watcher != null) watcher.onViewDetachedFromWindow(root);
                watcher = new AcrylicScrollWatcher(root, observer);
                ACRYLIC_SCROLL_WATCHERS.put(root, watcher);
                observer.addOnScrollChangedListener(watcher);
                observer.addOnGlobalLayoutListener(watcher);
                observer.addOnPreDrawListener(watcher);
            }
            watcher.addHost(host);
        }
    }

    static void acrylicSurface(View view, int radius) {
        Palette colors = colors(view.getContext());
        boolean dark = ThemeStore.dark(view.getContext());
        GradientDrawable blurred = surfaceDrawable(view, colors, radius,
                ThemeStore.surfaceMix(view.getContext()) * 255 / 100,
                ThemeStore.surfaceMix(view.getContext()) * 225 / 100);
        GradientDrawable fallback = surfaceDrawable(view, colors, radius,
                dark ? 246 : 248, dark ? 238 : 242);
        view.setBackground(new AcrylicSurfaceDrawable(view, blurred, fallback, radius,
                false, true));
        view.setElevation(dp(view, 8));
    }

    static GradientDrawable surfaceDrawable(View view, Palette colors, int radius,
            int firstAlpha, int secondAlpha) {
        GradientDrawable surface = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{alpha(colors.surface, firstAlpha), alpha(colors.surfaceAlt, secondAlpha)});
        boolean dark = ThemeStore.dark(view.getContext());
        surface.setCornerRadius(dp(view, radius(view.getContext(), radius)));
        surface.setStroke(dp(view, 1), alpha(dark ? colors.outline : Color.WHITE,
                dark ? 210 : 225));
        return surface;
    }

    /** Blurs only the wallpaper sampled behind this surface; labels remain separate and sharp. */
    private static final class AcrylicSurfaceDrawable extends Drawable {
        private final GradientDrawable blurredSurface;
        private final GradientDrawable fallbackSurface;
        private final int radius;
        private GlassBackdropView backdrop;
        private int lastMix = -1;
        private int lastRadius = -1;
        private int lastSurface, lastAlt, lastOutline;
        private int lastText;
        private boolean lastDark;
        private boolean lastSelected;
        private int drawableAlpha = 255;
        private int minimumMix;
        private Integer calendarColor;
        private final View host;
        private boolean selected;
        private boolean outline;
        private Bitmap railSurface;
        private Canvas railCanvas;
        private boolean railDirty = true, railStatic, railAcrylic;
        private final android.graphics.Rect railSource = new android.graphics.Rect();
        private static final int RAIL_RASTER_BLOCK_PX = 256;

        AcrylicSurfaceDrawable(View host, GradientDrawable blurredSurface,
                GradientDrawable fallbackSurface, int radius, boolean selected, boolean outline) {
            this.host = host;
            this.blurredSurface = blurredSurface;
            this.fallbackSurface = fallbackSurface;
            this.radius = radius;
            this.selected = selected;
            this.outline = outline;
            applyAcrylicStroke(host, selected, outline, blurredSurface, fallbackSurface, calendarColor);
        }

        boolean belongsTo(View candidate, int candidateRadius) {
            return host == candidate && radius == candidateRadius;
        }

        void updateSelection(boolean selected, boolean outline) {
            if (this.selected == selected && this.outline == outline) return;
            this.selected = selected;
            this.outline = outline;
            railDirty = true;
            applyAcrylicStroke(host, selected, outline, blurredSurface, fallbackSurface, calendarColor);
            invalidateSelf();
        }

        @Override protected void onBoundsChange(android.graphics.Rect bounds) {
            railDirty = true;
        }

        @Override public void draw(android.graphics.Canvas canvas) {
            int resolved = radius(host.getContext(), radius);
            if (lastRadius != resolved) {
                lastRadius = resolved;
                blurredSurface.setCornerRadius(dp(host, resolved));
                fallbackSurface.setCornerRadius(dp(host, resolved));
                host.invalidateOutline();
                railDirty = true;
            }
            Palette palette = colors(host.getContext());
            boolean acrylic = ThemeStore.acrylicEnabled(host.getContext());
            updateBlurredSurface(host, palette);
            if (host instanceof DockNavigationLayout && canvas.isHardwareAccelerated()) {
                android.graphics.Rect bounds = getBounds();
                if (bounds.isEmpty()) return;
                // Nested glass inherits its parent's backdrop: its plate pixels depend on
                // style and bounds, not the moving highlight or screen position.
                boolean staticRaster = !acrylic || insideAcrylicSurface(host) || insideDialogBlur(host);
                if (railSurface == null || railSurface.getWidth() < bounds.width()
                        || railSurface.getHeight() < bounds.height()) {
                    // The dashboard dock changes width continuously. Reserve reusable blocks
                    // rather than allocating a new bitmap for each animation frame.
                    int capacity = ((bounds.width() + RAIL_RASTER_BLOCK_PX - 1) / RAIL_RASTER_BLOCK_PX) * RAIL_RASTER_BLOCK_PX;
                    int rasterWidth = railSurface == null ? capacity : Math.max(capacity, railSurface.getWidth());
                    int rasterHeight = railSurface == null ? bounds.height() : Math.max(bounds.height(), railSurface.getHeight());
                    railSurface = Bitmap.createBitmap(rasterWidth, rasterHeight, Bitmap.Config.ARGB_8888);
                    railCanvas = new Canvas(railSurface);
                    railDirty = true;
                }
                if (!staticRaster || !railStatic || railAcrylic != acrylic || railDirty) {
                    railSurface.eraseColor(Color.TRANSPARENT);
                    int save = railCanvas.save();
                    railCanvas.translate(-bounds.left, -bounds.top);
                    drawAcrylic(railCanvas, palette, acrylic);
                    railCanvas.restoreToCount(save);
                    railDirty = false;
                    railStatic = staticRaster;
                    railAcrylic = acrylic;
                }
                // Rasterize only the static glass plate: GPU round-rect gradients can
                // develop a centre seam while scaling. Text and the overflowing highlight
                // remain independent hardware nodes; the dock itself has no bitmap layer.
                railSource.set(0, 0, bounds.width(), bounds.height());
                canvas.drawBitmap(railSurface, railSource, bounds, null);
                return;
            }
            drawAcrylic(canvas, palette, acrylic);
        }

        private void drawAcrylic(android.graphics.Canvas canvas, Palette palette, boolean acrylic) {
            boolean backdropReady = false;
            if (acrylic && (insideAcrylicSurface(host) || insideDialogBlur(host))) {
                // The ancestor already drew a sampled surface or owns a blurred dialog window.
                backdropReady = true;
            } else if (acrylic) {
                watchAcrylicScroll(host);
                if (backdrop == null) {
                    backdrop = GlassBackdropView.findFor(host);
                }
                backdropReady = backdrop != null && backdrop.drawBlurredWallpaper(canvas, host,
                        getBounds(), dp(host, radius(host.getContext(), radius)), palette);
            }
            // A translucent mix is only safe when the matching blurred wallpaper was actually
            // drawn underneath. Until then, use the opaque fallback; cache completion invalidates
            // acrylic hosts so they switch to the mixed surface on their next frame.
            blurredSurface.setBounds(getBounds());
            fallbackSurface.setBounds(getBounds());
            if (!backdropReady) {
                fallbackSurface.setAlpha(drawableAlpha);
                fallbackSurface.draw(canvas);
                return;
            }
            fallbackSurface.setAlpha(drawableAlpha);
            blurredSurface.draw(canvas);
        }

        private void updateBlurredSurface(View host, Palette palette) {
            int mix = Math.max(minimumMix, ThemeStore.surfaceMix(host.getContext()));
            boolean dark = ThemeStore.dark(host.getContext());
            int accent = CalendarSourceColors.mix(calendarColor, palette.primary);
            int outlineColor = selected ? accent
                    : dark ? palette.outline : Color.WHITE;
            if (lastMix == mix && lastSurface == palette.surface && lastAlt == palette.surfaceAlt
                    && lastOutline == outlineColor && lastText == palette.text && lastDark == dark
                    && lastSelected == selected)
                return;
            lastMix = mix;
            lastSurface = palette.surface;
            lastAlt = palette.surfaceAlt;
            lastOutline = outlineColor;
            lastText = palette.text;
            lastDark = dark;
            lastSelected = selected;
            railDirty = true;
            // A gentle theme-aware RGB shift makes the wallpaper blur read through the tint:
            // dark surfaces lift toward the text color; light surfaces settle toward black.
            float toneShift = dark ? .20f : .12f;
            int toneTarget = dark ? palette.text : Color.BLACK;
            int mixedSurface = blendRgb(palette.surface, toneTarget, toneShift);
            int mixedAlt = blendRgb(palette.surfaceAlt, toneTarget, toneShift);
            if (selected) {
                int selection = calendarColor == null ? palette.primaryContainer : accent;
                mixedSurface = blendRgb(mixedSurface, selection, .34f);
                mixedAlt = blendRgb(mixedAlt, selection, .34f);
            }
            fallbackSurface.setColors(new int[]{
                    alpha(calendarColor == null ? palette.surface : blendRgb(palette.surface, accent, .20f), dark ? 246 : 248),
                    alpha(calendarColor == null ? palette.surfaceAlt : blendRgb(palette.surfaceAlt, accent, .20f), dark ? 238 : 242)});
            blurredSurface.setColors(new int[]{alpha(mixedSurface, mix * 255 / 100),
                    alpha(mixedAlt, mix * 225 / 100)});
            applyAcrylicStroke(host, selected, outline, blurredSurface, fallbackSurface, calendarColor);
        }

        @Override public void setAlpha(int alpha) {
            if (drawableAlpha != alpha) railDirty = true;
            drawableAlpha = alpha;
            blurredSurface.setAlpha(alpha);
            fallbackSurface.setAlpha(alpha);
        }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) {
            railDirty = true;
            blurredSurface.setColorFilter(filter);
            fallbackSurface.setColorFilter(filter);
        }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    private static void applyAcrylicStroke(View host, boolean selected, boolean outline,
            GradientDrawable blurredSurface, GradientDrawable fallbackSurface, Integer calendarColor) {
        Palette palette = colors(host.getContext());
        boolean dark = ThemeStore.dark(host.getContext());
        int width = outline ? dp(host, selected ? 2 : 1) : 0;
        int strokeColor = selected ? CalendarSourceColors.mix(calendarColor, palette.primary) : dark ? palette.outline : Color.WHITE;
        int strokeAlpha = selected ? 240 : dark ? 210 : 225;
        int color = outline ? alpha(strokeColor, strokeAlpha) : Color.TRANSPARENT;
        blurredSurface.setStroke(width, color);
        fallbackSurface.setStroke(width, color);
    }

    private static boolean insideAcrylicSurface(View view) {
        android.view.ViewParent parent = view.getParent();
        while (parent instanceof View ancestor) {
            if (ancestor.getBackground() instanceof AcrylicSurfaceDrawable) return true;
            parent = ancestor.getParent();
        }
        return false;
    }

    static void invalidateAcrylicSurfaces(View root) {
        invalidateAcrylicSurfaces(root, new android.graphics.Rect());
    }

    /** Collect once for a page gesture; ordinary text and icons need no per-frame traversal. */
    static void collectAcrylicSurfaces(View root, java.util.List<View> surfaces) {
        if (root.getBackground() instanceof AcrylicSurfaceDrawable) surfaces.add(root);
        if (root instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++)
                collectAcrylicSurfaces(group.getChildAt(i), surfaces);
        }
    }

    private static void invalidateAcrylicSurfaces(View root, android.graphics.Rect visible) {
        if (!root.getGlobalVisibleRect(visible)) return;
        if (root.getBackground() instanceof AcrylicSurfaceDrawable) root.invalidate();
        if (root instanceof android.view.ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++)
                invalidateAcrylicSurfaces(group.getChildAt(i), visible);
        }
    }

    /** The mask follows the control's surface and the nearest enclosing plate. */
    private static final class ControlHighlightMask extends Drawable {
        private final View host;
        private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.Path path = new android.graphics.Path();
        private final android.graphics.RectF rect = new android.graphics.RectF();
        private final android.graphics.Matrix hostMatrix = new android.graphics.Matrix(), parentMatrix = new android.graphics.Matrix(), toHost = new android.graphics.Matrix();
        ControlHighlightMask(View host) { this.host = host; paint.setColor(Color.WHITE); }
        @Override public void draw(Canvas canvas) {
            int save = canvas.save();
            hostMatrix.reset(); transformToRoot(host, hostMatrix);
            boolean invertible = hostMatrix.invert(toHost), enclosed = false;
            for (android.view.ViewParent ancestor = host.getParent(); ancestor instanceof View parent;
                    ancestor = parent.getParent()) {
                if (!(parent.getBackground() instanceof AcrylicSurfaceDrawable surface)) continue;
                if (!invertible) break;
                parentMatrix.reset(); transformToRoot(parent, parentMatrix);
                parentMatrix.setConcat(toHost, parentMatrix);
                rect.set(0, 0, parent.getWidth(), parent.getHeight());
                path.reset();
                float corner = dp(parent, radius(parent.getContext(), surface.radius));
                path.addRoundRect(rect, corner, corner, android.graphics.Path.Direction.CW);
                path.transform(parentMatrix);
                canvas.clipPath(path);
                enclosed = true;
                break;
            }
            rect.set(getBounds());
            float radius = host.getBackground() instanceof AcrylicSurfaceDrawable surface
                    ? dp(host, radius(host.getContext(), surface.radius)) : enclosed ? 0 : dp(host, 6);
            canvas.drawRoundRect(rect, radius, radius, paint);
            canvas.restoreToCount(save);
        }
        @Override public void setAlpha(int alpha) { paint.setAlpha(alpha); }
        @Override public void setColorFilter(android.graphics.ColorFilter filter) { paint.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }

    /** Keep busy wallpaper from washing out a surface without dimming its children. */
    static void minimumAcrylicMix(View view, int percent) {
        if (view.getBackground() instanceof AcrylicSurfaceDrawable acrylic) {
            acrylic.minimumMix = Math.max(0, Math.min(100, percent));
            view.invalidate();
        }
    }

    static void acrylicSourceColor(View view, Integer calendarColor) {
        if (view.getBackground() instanceof AcrylicSurfaceDrawable acrylic) {
            acrylic.calendarColor = calendarColor; acrylic.lastMix = -1; view.invalidate();
        }
    }

    private static int blendRgb(int color, int target, float amount) {
        float inverse = 1f - amount;
        return Color.argb(Color.alpha(color),
                Math.round(Color.red(color) * inverse + Color.red(target) * amount),
                Math.round(Color.green(color) * inverse + Color.green(target) * amount),
                Math.round(Color.blue(color) * inverse + Color.blue(target) * amount));
    }

    static void acrylicChoice(View view, boolean selected, int radius, boolean outline) {
        Palette colors = colors(view.getContext());
        Drawable existing = view.getBackground();
        if (existing instanceof AcrylicSurfaceDrawable acrylic
                && acrylic.belongsTo(view, radius)) {
            acrylic.updateSelection(selected, outline);
        } else {
            boolean dark = ThemeStore.dark(view.getContext());
            GradientDrawable blurred = surfaceDrawable(view, colors, radius,
                    ThemeStore.surfaceMix(view.getContext()) * 255 / 100,
                    ThemeStore.surfaceMix(view.getContext()) * 225 / 100);
            GradientDrawable fallback = surfaceDrawable(view, colors, radius,
                    dark ? 246 : 248, dark ? 238 : 242);
            view.setBackground(new AcrylicSurfaceDrawable(view, blurred, fallback, radius,
                    selected, outline));
        }
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(alpha(colors.primary, 34)),
                null, new ControlHighlightMask(view)));
    }

    static void installRipple(View view) {
        // Styled controls already carry a ripple mask matched to their own surface radius.
        if (view.getForeground() instanceof RippleDrawable) return;
        view.setForeground(new RippleDrawable(ColorStateList.valueOf(
                alpha(colors(view.getContext()).primary, 32)), null,
                new ControlHighlightMask(view)));
    }

    private static int dp(View view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }
}
