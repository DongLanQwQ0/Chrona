package com.donglan.chrona;

import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.StateListAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import java.util.WeakHashMap;

/** Shared pressed-state feedback without replacing touch or click handlers. */
final class PressFeedback {
    private static final float PRESSED_SCALE = 1.05f;
    private static final WeakHashMap<View, Boolean> installed = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> roots = new WeakHashMap<>();

    static void apply(View view) {
        if (installed.containsKey(view) || view instanceof DockNavigationLayout
                || view instanceof ScrollView || view instanceof HorizontalScrollView) return;
        for (android.view.ViewParent parent = view.getParent(); parent instanceof View;
                parent = parent.getParent()) if (parent instanceof DockNavigationLayout) return;
        StateListAnimator states = new StateListAnimator();
        states.addState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed},
                scale(view, PRESSED_SCALE, UiMotion.PRESS));
        states.addState(new int[]{}, scale(view, 1f, UiMotion.RELEASE));
        view.setStateListAnimator(states);
        installed.put(view, Boolean.TRUE);
    }

    private static AnimatorSet scale(View view, float value, long duration) {
        AnimatorSet pair = new AnimatorSet();
        pair.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, value),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, value));
        pair.setDuration(UiMotion.isEnabled() ? duration : 0);
        pair.setInterpolator(UiMotion.SETTLE);
        return pair;
    }

    private static void scan(View view) {
        // Dock owns the scale of its plate and highlight, including all of its buttons.
        if (view instanceof DockNavigationLayout) return;
        if (view.isClickable() || view.isLongClickable()) apply(view);
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i));
    }

    static void install(View root) {
        if (root == null || roots.containsKey(root)) return;
        roots.put(root, Boolean.TRUE);
        scan(root);
        // Layout changes cover dynamic results and newly opened dialog controls. Animating
        // scale does not request layout, so this never scans the tree on each motion frame.
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> scan(root);
        root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) {
                if (view.getViewTreeObserver().isAlive())
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
                roots.remove(view);
                view.removeOnAttachStateChangeListener(this);
            }
        });
    }
}
