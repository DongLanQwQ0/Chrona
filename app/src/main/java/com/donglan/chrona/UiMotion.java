package com.donglan.chrona;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import java.util.WeakHashMap;

/** Motion for in-page content only. Activity transitions belong to the Android system. */
final class UiMotion {
    static final long ENTER = 240L;
    static final long EXIT = 180L;
    static final Interpolator SETTLE = new PathInterpolator(.2f, 0f, 0f, 1f);
    static final long PRESS = 90L;
    static final long RELEASE = 160L;
    private static final WeakHashMap<View, Boolean> REVEALED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> SCROLL_SEEN = new WeakHashMap<>();
    private static final WeakHashMap<View, java.lang.ref.WeakReference<Runnable>> ACTIVE =
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
        UiStyle.collectAcrylicSurfaces(view, surfaces);
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

}
