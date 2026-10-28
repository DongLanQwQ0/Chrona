package com.donglan.chrona;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.animation.ValueAnimator;
import android.animation.ObjectAnimator;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import java.util.WeakHashMap;

/** Motion for in-page content only. Activity transitions belong to the Android system. */
public final class UiMotion {
    static final long ENTER = 240L;
    static final long EXIT = 180L;
    static final Interpolator SETTLE = new PathInterpolator(.2f, 0f, 0f, 1f);
    static final long PRESS = 90L;
    static final long RELEASE = 160L;
    private static final WeakHashMap<View, Boolean> REVEALED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> SCROLL_SEEN = new WeakHashMap<>();
    private static final WeakHashMap<View, java.lang.ref.WeakReference<Runnable>> ACTIVE =
            new WeakHashMap<>();
    private static final WeakHashMap<View, java.lang.ref.WeakReference<Runnable>> PENDING_ENTRANCES =
            new WeakHashMap<>();
    private static final WeakHashMap<ScrollView, java.lang.ref.WeakReference<ScrollRevealObserver>>
            SCROLLS = new WeakHashMap<>();

    private UiMotion() { }

    public static boolean isEnabled() { return ValueAnimator.areAnimatorsEnabled(); }

    /** Idempotent; observes without replacing a caller's scroll listener. */
    public static void observeScroll(ScrollView viewport, ViewGroup content) {
        java.lang.ref.WeakReference<ScrollRevealObserver> reference = SCROLLS.get(viewport);
        ScrollRevealObserver observer = reference == null ? null : reference.get();
        if (observer != null && observer.content == content) {
            observer.refresh();
            return;
        }
        if (observer != null) observer.dispose();
        observer = new ScrollRevealObserver(viewport, content);
        SCROLLS.put(viewport, new java.lang.ref.WeakReference<>(observer));
    }

    /** Data refreshes and restored pages are immediately visible, without another entrance. */
    public static void settleScroll(ScrollView viewport, ViewGroup content) {
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            remember(child);
            java.lang.ref.WeakReference<Runnable> reference = ACTIVE.get(child);
            Runnable finish = reference == null ? null : reference.get();
            if (finish != null) finish.run();
        }
        observeScroll(viewport, content);
    }

    /** Only a visible, newly presented card may enter; repeated calls never restart it. */
    public static void reveal(View view, int index) {
        if (REVEALED.containsKey(view)) return;
        if (!view.isAttachedToWindow() || !view.isShown()
                || !view.getGlobalVisibleRect(new android.graphics.Rect())) return;
        REVEALED.put(view, true);
        remember(view);
        if (!isEnabled() || protectsInteraction(view)) return;
        view.animate().cancel();
        view.setAlpha(0f);
        view.setTranslationY(8f * view.getResources().getDisplayMetrics().density);
        java.util.ArrayList<View> surfaces = new java.util.ArrayList<>();
        UiSurfaces.collectAcrylicSurfaces(view, surfaces);
        java.util.ArrayList<View> interactionTargets = new java.util.ArrayList<>();
        collectInteractionTargets(view, interactionTargets);
        Runnable finish = () -> {
            view.animate().setUpdateListener(null).cancel();
            view.setAlpha(1f);
            view.setTranslationY(0f);
            ACTIVE.remove(view);
        };
        ACTIVE.put(view, new java.lang.ref.WeakReference<>(finish));
        View.OnAttachStateChangeListener cleanup = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View ignored) { }
            @Override public void onViewDetachedFromWindow(View ignored) {
                finish.run();
                view.removeOnAttachStateChangeListener(this);
            }
        };
        view.addOnAttachStateChangeListener(cleanup);
        view.animate().alpha(1f).translationY(0f)
                .setStartDelay(Math.min(Math.max(index, 0), 5) * 18L)
                .setDuration(ENTER).setInterpolator(SETTLE)
                .setUpdateListener(animation -> {
                    if (!isEnabled() || view.hasFocus() || hasAccessibilityFocus(interactionTargets)) {
                        finish.run();
                        view.removeOnAttachStateChangeListener(cleanup);
                        return;
                    }
                    for (View surface : surfaces) surface.invalidate();
                })
                .withEndAction(() -> {
                    view.animate().setUpdateListener(null);
                    ACTIVE.remove(view);
                    view.removeOnAttachStateChangeListener(cleanup);
                }).start();
    }

    static void remember(View view) { SCROLL_SEEN.put(view, true); }
    /** One result-list transition shared by schedule and inbox; no bitmap ghost or per-row burst. */
    static void resetList(View list) {
        list.animate().cancel(); list.setAlpha(1f); list.setTranslationY(0f);
    }
    static void exitList(View list, Runnable replace) {
        resetList(list);
        if (!isEnabled() || protectsInteraction(list)) { replace.run(); return; }
        list.animate().alpha(0f).translationY(-8f * list.getResources().getDisplayMetrics().density)
                .setStartDelay(0).setDuration(EXIT).setInterpolator(SETTLE)
                .withEndAction(() -> { resetList(list); replace.run(); }).start();
    }
    static void enterList(View list) {
        resetList(list);
        if (!isEnabled() || protectsInteraction(list)) return;
        list.setAlpha(0f); list.setTranslationY(8f * list.getResources().getDisplayMetrics().density);
        list.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(ENTER)
                .setInterpolator(SETTLE).start();
    }
    static boolean wasRevealed(View view) { return SCROLL_SEEN.containsKey(view); }

    private static void collectInteractionTargets(View view, java.util.ArrayList<View> targets) {
        targets.add(view);
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                collectInteractionTargets(group.getChildAt(i), targets);
            }
        }
    }

    private static boolean hasAccessibilityFocus(java.util.ArrayList<View> targets) {
        for (View target : targets) if (target.isAccessibilityFocused()) return true;
        return false;
    }

    // This scan runs only when a card first enters, never on every scroll frame.
    static boolean protectsInteraction(View view) {
        if (view instanceof EditText || view instanceof HorizontalScrollView
                || view.isFocused() || view.isAccessibilityFocused()) return true;
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) {
                if (protectsInteraction(group.getChildAt(i))) return true;
            }
        }
        return false;
    }

    /** Length of the in-place cross-fade used when a screen rebuilds itself. */
    static final long SWAP_MILLIS = 220L;
    /** Marks the temporary snapshot view a swap leaves over the container while it fades. */
    private static final Object SWAP_GHOST = new Object();
    private static final int SNAPSHOT_MAX_EDGE = 1024;
    private static final int SNAPSHOT_MAX_PIXELS = 524288;

    static void enter(View view, int index) {
        view.post(() -> UiMotion.reveal(view, index));
    }

    /** Explicit first presentation of a whole page; its scrollable descendants stay intact. */
    static void enterPage(View view) {
        finishSwap(view);
        if (!isEnabled()) return;
        android.view.ViewTreeObserver.OnPreDrawListener[] firstFrame = new android.view.ViewTreeObserver.OnPreDrawListener[1];
        Runnable[] start = new Runnable[1];
        Runnable[] cancel = new Runnable[1];
        View.OnAttachStateChangeListener cleanup = new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View attached) { }
            @Override public void onViewDetachedFromWindow(View detached) {
                if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(firstFrame[0]);
                view.removeCallbacks(start[0]);
                finishSwap(view); view.removeOnAttachStateChangeListener(this);
            }
        };
        cancel[0] = () -> {
            PENDING_ENTRANCES.remove(view);
            if (view.getViewTreeObserver().isAlive()) view.getViewTreeObserver().removeOnPreDrawListener(firstFrame[0]);
            view.removeCallbacks(start[0]);
            view.removeOnAttachStateChangeListener(cleanup);
        };
        PENDING_ENTRANCES.put(view, new java.lang.ref.WeakReference<>(cancel[0]));
        view.addOnAttachStateChangeListener(cleanup);
        view.setAlpha(0f);
        view.setTranslationY(8f * view.getResources().getDisplayMetrics().density);
        start[0] = () -> {
            cancel[0].run();
            if (!view.isAttachedToWindow() || !isEnabled()) {
                finishSwap(view); view.removeOnAttachStateChangeListener(cleanup); return;
            }
            view.addOnAttachStateChangeListener(cleanup);
            view.animate().alpha(1f).translationY(0f).setStartDelay(0).setDuration(ENTER)
                .setInterpolator(SETTLE).setUpdateListener(animation -> {
                    if (!isEnabled()) finishSwap(view);
                }).withEndAction(() -> {
                    view.animate().setUpdateListener(null); view.removeOnAttachStateChangeListener(cleanup);
                }).start();
        };
        // The entrance clock starts after layout has produced a drawable frame, not while a
        // cold activity or resized window is still being attached by WindowManager.
        firstFrame[0] = () -> {
            view.getViewTreeObserver().removeOnPreDrawListener(firstFrame[0]);
            view.postOnAnimation(start[0]);
            return true;
        };
        view.getViewTreeObserver().addOnPreDrawListener(firstFrame[0]);
    }

    /**
     * Staggers a card list into view. Only for a screen that is being opened: rebuilding a screen
     * for another reason (theme change, returning from another page) must not look like a reload.
     */
    public static void enterChildren(LinearLayout parent) {
        for (int i = 0; i < parent.getChildCount(); i++) enter(parent.getChildAt(i), i);
    }

    /**
     * Rebuilds a container's contents as a cross-fade: a snapshot of the outgoing content fades
     * out while the rebuilt content fades in. The earlier version dipped the container towards
     * transparent first and only rebuilt afterwards, so the new content appeared once the old one
     * was already gone.
     *
     * <p>The snapshot is placed in the container's own parent, at the container's layout position,
     * so the parent's scroll offset applies to it exactly as it does to the rebuilt content.
     * Pinning it to window coordinates instead left it behind whenever the rebuild scrolled the
     * page (a section switch resets the scroll), which only lined up at zero offset.
     */
    public static void swap(View container, Runnable rebuild) {
        swap(container, container, rebuild);
    }

    /** Explicit lifecycle/focus completion for a composed in-page transition. */
    static void finishSwap(View container) {
        java.lang.ref.WeakReference<Runnable> pending = PENDING_ENTRANCES.remove(container);
        Runnable cancel = pending == null ? null : pending.get();
        if (cancel != null) cancel.run();
        container.animate().setListener(null).setUpdateListener(null).withEndAction(null).cancel();
        if (container.getParent() instanceof FrameLayout host && hostsSeveralChildren(host)) dropSwapGhosts(host);
        container.setAlpha(1f); container.setTranslationX(0f); container.setTranslationY(0f);
    }

    /**
     * Cross-fades a rebuild where the outgoing look is taken from {@code snapshotOf} but
     * {@code container} is the view that fades back in. Use this when the rebuilt content sits on
     * a transparent background — for example an activity page over the shared backdrop — so the
     * ghost covers the whole visual, background included.
     */
    public static void swap(View snapshotOf, View container, Runnable rebuild) {
        swap(snapshotOf, container, rebuild, 0);
    }

    /** Positive direction advances leftwards; zero retains the established cross-fade. */
    static void swap(View snapshotOf, View container, Runnable rebuild, int direction) {
        // Capture the currently composed frame, including an unfinished previous cross-fade.
        Snapshot outgoing = UiMotion.isEnabled() && snapshotOf.getParent() instanceof FrameLayout candidate
                && hostsSeveralChildren(candidate) ? snapshot(snapshotOf, direction != 0) : null;
        java.lang.ref.WeakReference<Runnable> pending = PENDING_ENTRANCES.remove(container);
        Runnable cancel = pending == null ? null : pending.get();
        if (cancel != null) cancel.run();
        container.animate().setUpdateListener(null).withEndAction(null).cancel();
        container.setAlpha(1f);
        container.setTranslationX(0f); container.setTranslationY(0f);
        if (snapshotOf.getParent() instanceof FrameLayout previousHost
                && hostsSeveralChildren(previousHost)) dropSwapGhosts(previousHost);
        if (!ValueAnimator.areAnimatorsEnabled()) {
            if (outgoing != null) outgoing.bitmap.recycle();
            rebuild.run();
            return;
        }
        if (snapshotOf.getWidth() <= 0
                || !(snapshotOf.getParent() instanceof FrameLayout host)
                || !hostsSeveralChildren(host)) {
            if (outgoing != null) outgoing.bitmap.recycle();
            rebuild.run();
            return;
        }
        dropSwapGhosts(host);
        if (outgoing == null) {
            rebuild.run();
            return;
        }
        ImageView ghost = new ImageView(snapshotOf.getContext());
        ghost.setImageBitmap(outgoing.bitmap);
        ghost.setScaleType(ImageView.ScaleType.FIT_XY);
        ghost.setTag(SWAP_GHOST);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                outgoing.bounds.width(), outgoing.bounds.height());
        params.leftMargin = snapshotOf.getLeft() + outgoing.bounds.left;
        params.topMargin = snapshotOf.getTop() + outgoing.bounds.top;
        ghost.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) {
                ghost.animate().cancel();
                ghost.setImageDrawable(null);
                outgoing.bitmap.recycle();
                ghost.removeOnAttachStateChangeListener(this);
                container.animate().setUpdateListener(null).withEndAction(null).cancel();
                container.setAlpha(1f); container.setTranslationX(0f); container.setTranslationY(0f);
            }
        });
        // Added after the snapshot source so it draws on top of it.
        host.addView(ghost, params);

        try {
            rebuild.run();
        } catch (RuntimeException | Error failure) {
            host.removeView(ghost);
            throw failure;
        }
        container.animate().cancel();
        container.setAlpha(0f);
        float distance = Integer.signum(direction) * snapshotOf.getWidth();
        container.setTranslationX(distance);
        container.animate().alpha(1f).translationX(0f).setStartDelay(0).setDuration(SWAP_MILLIS)
                .setInterpolator(UiMotion.SETTLE)
                .setUpdateListener(animation -> { if (!isEnabled()) finishSwap(container); })
                .withEndAction(() -> container.animate().setUpdateListener(null))
                .start();
        ghost.animate().alpha(0f).translationX(-distance).setDuration(SWAP_MILLIS)
                .setInterpolator(UiMotion.SETTLE)
                .withEndAction(() -> host.removeView(ghost)).start();
    }

    /**
     * The ghost is a second child, so the host has to accept one. Scrolling containers extend
     * FrameLayout but raise {@code IllegalStateException} on a second child, which crashed the
     * section switch while its body still lived directly in a ScrollView.
     */
    private static boolean hostsSeveralChildren(ViewGroup host) {
        return !(host instanceof ScrollView)
                && !(host instanceof android.widget.HorizontalScrollView);
    }

    private static final class Snapshot {
        final Bitmap bitmap;
        final android.graphics.Rect bounds;
        Snapshot(Bitmap bitmap, android.graphics.Rect bounds) {
            this.bitmap = bitmap;
            this.bounds = bounds;
        }
    }

    /** Only the visible viewport; bounded to 2 MiB even for very long settings pages. */
    private static Snapshot snapshot(View container, boolean fixedViewport) {
        android.graphics.Rect bounds = new android.graphics.Rect();
        if (!container.isAttachedToWindow()) return null;
        if (fixedViewport && container.getParent() instanceof ViewGroup parent) {
            // A reverse mid-slide captures the entire composed viewport, not just the portion
            // of the incoming (currently translated) child that happens to be visible.
            if (!parent.getLocalVisibleRect(bounds)) return null;
            bounds.offset(-parent.getScrollX() - container.getLeft(), -parent.getScrollY() - container.getTop());
        } else {
            if (!container.getLocalVisibleRect(bounds)) return null;
            bounds.offset(-container.getScrollX(), -container.getScrollY());
        }
        if (!bounds.intersect(0, 0, container.getWidth(), container.getHeight())) return null;
        int width = bounds.width(), height = bounds.height();
        float scale = (float) Math.min(.5, Math.min(
                (double) SNAPSHOT_MAX_EDGE / Math.max(width, height),
                Math.sqrt((double) SNAPSHOT_MAX_PIXELS / ((double) width * height))));
        Bitmap bitmap = null;
        try {
            bitmap = Bitmap.createBitmap(Math.max(1, (int) (width * scale)),
                    Math.max(1, (int) (height * scale)),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            canvas.scale((float) bitmap.getWidth() / width, (float) bitmap.getHeight() / height);
            ViewGroup parent = (ViewGroup) container.getParent();
            canvas.translate(-container.getLeft() + parent.getScrollX() - bounds.left,
                    -container.getTop() + parent.getScrollY() - bounds.top);
            parent.draw(canvas);
            return new Snapshot(bitmap, bounds);
        } catch (OutOfMemoryError error) {
            if (bitmap != null) bitmap.recycle();
            return null;
        }
    }

    /** A second swap replaces the previous cross-fade instead of stacking ghosts. */
    private static void dropSwapGhosts(ViewGroup host) {
        for (int i = host.getChildCount() - 1; i >= 0; i--) {
            View child = host.getChildAt(i);
            if (child.getTag() != SWAP_GHOST) continue;
            child.animate().cancel();
            host.removeView(child);
        }
    }

    public static void pop(View view) {
        view.animate().cancel();
        if (!UiMotion.isEnabled()) {
            view.setScaleX(1f);
            view.setScaleY(1f);
            return;
        }
        if (view.getScaleX() == 1f && view.getScaleY() == 1f) {
            view.setScaleX(0.97f);
            view.setScaleY(0.97f);
        }
        view.animate().scaleX(1f).scaleY(1f).setStartDelay(0)
                .setDuration(UiMotion.ENTER).setInterpolator(UiMotion.SETTLE).start();
    }

    /** A quiet breathing cue for something that is still running; stops itself when detached. */
    public static void pulse(View view) {
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        ObjectAnimator pulse = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.6f, 1f);
        pulse.setDuration(1600);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.start();
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View attached) { }

            @Override public void onViewDetachedFromWindow(View detached) {
                pulse.cancel();
            }
        });
    }
}
