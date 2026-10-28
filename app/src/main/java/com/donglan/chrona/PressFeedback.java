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
    static final float CARD_PRESSED_SCALE = 1.08f;
    private static final WeakHashMap<View, Float> installed = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> roots = new WeakHashMap<>();
    private static final WeakHashMap<ViewGroup, int[]> viewports = new WeakHashMap<>();

    /** Pixels needed on either edge when the whole control reaches its held scale. */
    static int edgeSpace(int extentPx) {
        return (int) Math.ceil(Math.max(0, extentPx) * (CARD_PRESSED_SCALE - 1f) / 2f) + 1;
    }

    /** Keep a viewport's own bounds independent from its parent's child-clipping flags. */
    static void protectViewport(ViewGroup viewport) {
        if (!viewports.containsKey(viewport)) {
            viewports.put(viewport, new int[]{-1, -1});
            viewport.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) ->
                    updateViewportBounds(viewport));
        }
        updateViewportBounds(viewport);
    }

    private static void updateViewportBounds(ViewGroup viewport) {
        int width = viewport.getWidth(), height = viewport.getHeight();
        if (width <= 0 || height <= 0) return;
        int[] previous = viewports.get(viewport);
        if (previous[0] == width && previous[1] == height) return;
        previous[0] = width; previous[1] = height;
        viewport.setClipBounds(new android.graphics.Rect(0, 0, width, height));
    }

    static void apply(View view) {
        if (!installed.containsKey(view)) apply(view,
                UiSurfaces.isCardSurface(view) ? CARD_PRESSED_SCALE : PRESSED_SCALE);
        else if (!Float.isNaN(installed.get(view))) allowOverflow(view);
    }

    static void apply(View view, float pressedScale) {
        if (view instanceof DockNavigationLayout
                || view instanceof ScrollView || view instanceof HorizontalScrollView) return;
        for (android.view.ViewParent parent = view.getParent(); parent instanceof View;
                parent = parent.getParent()) if (parent instanceof DockNavigationLayout) return;
        Float previous = installed.get(view);
        if (previous != null && previous == pressedScale) { allowOverflow(view); return; }
        StateListAnimator states = new StateListAnimator();
        states.addState(new int[]{android.R.attr.state_enabled, android.R.attr.state_pressed},
                scale(view, pressedScale, UiMotion.PRESS, true));
        states.addState(new int[]{}, scale(view, 1f, UiMotion.RELEASE, false));
        view.setStateListAnimator(states);
        installed.put(view, pressedScale);
        allowOverflow(view);
    }

    /** One surface responds to its child actions, without multiplying their scales. */
    static void group(ViewGroup plate, float pressedScale) {
        plate.setAddStatesFromChildren(true);
        apply(plate, pressedScale);
        for (int i = 0; i < plate.getChildCount(); i++) {
            View child = plate.getChildAt(i);
            if (!child.isClickable() && !child.isLongClickable()) continue;
            child.setStateListAnimator(null);
            child.setScaleX(1f); child.setScaleY(1f);
            installed.put(child, Float.NaN);
        }
    }

    private static void allowOverflow(View view) {
        // A scale changes drawing bounds, not layout bounds. Intermediate layout parents
        // must permit overflow, while scrolling/page/media viewports keep their boundaries.
        for (android.view.ViewParent parent = view.getParent(); parent instanceof ViewGroup group;
                parent = parent.getParent()) {
            if (group instanceof ScrollView || group instanceof HorizontalScrollView
                    || group instanceof SwipePagerLayout) { protectViewport(group); break; }
            if (group.getClipToOutline()) break;
            group.setClipChildren(false); group.setClipToPadding(false);
        }
    }

    private static AnimatorSet scale(View view, float value, long duration, boolean pressed) {
        AnimatorSet pair = new AnimatorSet();
        pair.playTogether(ObjectAnimator.ofFloat(view, View.SCALE_X, value),
                ObjectAnimator.ofFloat(view, View.SCALE_Y, value),
                ObjectAnimator.ofFloat(view, InteractionLayers.PRESSED, pressed ? 1f : 0f));
        // ValueAnimator applies the current system duration scale when each run starts.
        // Baking zero into a reused StateListAnimator would survive re-enabling motion.
        pair.setDuration(duration);
        pair.setInterpolator(UiMotion.SETTLE);
        return pair;
    }

    private static void scan(View view) {
        if (view instanceof ScrollView || view instanceof HorizontalScrollView || view instanceof SwipePagerLayout)
            protectViewport((ViewGroup) view);
        // Dock owns the scale of its plate and highlight, including all of its buttons.
        if (view instanceof DockNavigationLayout) return;
        if (view.isClickable() || view.isLongClickable() || installed.containsKey(view)) apply(view);
        if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) scan(group.getChildAt(i));
    }

    static void install(View root) {
        if (root == null || roots.containsKey(root)) return;
        roots.put(root, Boolean.TRUE);
        scan(root);
        // Layout changes cover dynamic results and newly opened dialog controls. Animating
        // scale does not request layout, so this never scans the tree on each motion frame.
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> { scan(root); InteractionLayers.refresh(); };
        ViewTreeObserver.OnGlobalFocusChangeListener focus = InteractionLayers::focus;
        root.getViewTreeObserver().addOnGlobalLayoutListener(listener);
        root.getViewTreeObserver().addOnGlobalFocusChangeListener(focus);
        InteractionLayers.focus(null, root.findFocus());
        root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) {
                InteractionLayers.focus(view.findFocus(), null);
                if (view.getViewTreeObserver().isAlive()) {
                    view.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
                    view.getViewTreeObserver().removeOnGlobalFocusChangeListener(focus);
                }
                roots.remove(view);
                view.removeOnAttachStateChangeListener(this);
            }
        });
    }
}
