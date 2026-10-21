package com.donglan.chrona;

import android.util.Property;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;

/** Drawing priority within a window; layout order and resting elevations stay intact. */
final class InteractionLayers {
    private static final WeakHashMap<View, Float> pressed = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> focused = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> pinned = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> observed = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> detached = new WeakHashMap<>();
    private static final WeakHashMap<View, Float> original = new WeakHashMap<>();
    static final Property<View, Float> PRESSED = new Property<>(Float.class, "interactionLayer") {
        @Override public Float get(View view) { return pressed.getOrDefault(view, 0f); }
        @Override public void set(View view, Float value) { setPressed(view, value); }
    };

    static void pin(View view) { pinned.put(view, true); observe(view); refresh(); }

    static void focus(View oldFocus, View newFocus) {
        if (oldFocus != null) focused.remove(oldFocus);
        if (newFocus != null) { focused.put(newFocus, true); observe(newFocus); }
        refresh();
    }

    static void setPressed(View view, float amount) {
        boolean before = pressed.getOrDefault(view, 0f) > 0;
        if (amount > 0) pressed.put(view, amount); else pressed.remove(view);
        observe(view);
        if (before != (amount > 0)) refresh();
    }

    private static void observe(View view) {
        if (observed.put(view, true) != null) return;
        view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { detached.remove(view); refresh(); }
            @Override public void onViewDetachedFromWindow(View view) {
                // Android still reports isAttachedToWindow() during this callback.
                detached.put(view, true);
                pressed.remove(view); focused.remove(view); refresh();
            }
        });
    }

    private static void branches(View view, int priority, Map<View, Integer> priorities) {
        if (!view.isAttachedToWindow() || detached.containsKey(view)) return;
        for (View node = view; node.getParent() instanceof ViewGroup parent; node = parent)
            priorities.merge(node, priority, Math::max);
    }

    private static float baseline(View view) {
        return view.getElevation() + original.getOrDefault(view, view.getTranslationZ());
    }

    static void refresh() {
        Map<View, Integer> priorities = new IdentityHashMap<>();
        for (View view : new ArrayList<>(pressed.keySet())) if (view != null) branches(view, 1, priorities);
        for (View view : new ArrayList<>(focused.keySet())) if (view != null) branches(view, 1, priorities);
        for (View view : new ArrayList<>(pinned.keySet())) if (view != null) branches(view, 2, priorities);
        Map<View, Float> targets = new IdentityHashMap<>();
        for (var entry : priorities.entrySet()) {
            View view = entry.getKey();
            ViewGroup parent = (ViewGroup) view.getParent();
            float highest = baseline(view);
            for (int i = 0; i < parent.getChildCount(); i++) highest = Math.max(highest, baseline(parent.getChildAt(i)));
            float step = view.getResources().getDisplayMetrics().density;
            targets.put(view, highest + entry.getValue() * step - view.getElevation());
        }
        for (View view : new ArrayList<>(original.keySet())) if (view != null && !targets.containsKey(view))
            view.setTranslationZ(original.remove(view));
        for (var entry : targets.entrySet()) {
            View view = entry.getKey();
            original.putIfAbsent(view, view.getTranslationZ());
            if (Math.abs(view.getTranslationZ() - entry.getValue()) > .001f) view.setTranslationZ(entry.getValue());
        }
    }

    private InteractionLayers() { }
}
