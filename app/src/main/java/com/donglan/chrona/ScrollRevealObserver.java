package com.donglan.chrona;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ScrollView;
import java.util.ArrayList;

/** One frame callback and a flat candidate cache per vertical viewport. */
final class ScrollRevealObserver implements ViewTreeObserver.OnScrollChangedListener,
        ViewTreeObserver.OnGlobalLayoutListener, View.OnAttachStateChangeListener, Runnable {
    private final ScrollView viewport;
    final ViewGroup content;
    private final ArrayList<View> candidates = new ArrayList<>();
    private final Rect visible = new Rect();
    private ViewTreeObserver tree;
    private boolean queued;
    private boolean presented;

    ScrollRevealObserver(ScrollView viewport, ViewGroup content) {
        this.viewport = viewport;
        this.content = content;
        viewport.addOnAttachStateChangeListener(this);
        if (viewport.isAttachedToWindow()) onViewAttachedToWindow(viewport);
    }

    void refresh() {
        boolean unchanged = candidates.size() == content.getChildCount();
        for (int i = 0; unchanged && i < candidates.size(); i++) {
            unchanged = candidates.get(i) == content.getChildAt(i);
        }
        if (unchanged) {
            schedule();
            return;
        }
        // Rebuilds are data updates by default. Call reveal explicitly for active navigation.
        ArrayList<View> previous = new ArrayList<>(candidates);
        candidates.clear();
        for (int i = 0; i < content.getChildCount(); i++) {
            View child = content.getChildAt(i);
            if (presented && !previous.contains(child)) UiMotion.remember(child);
            candidates.add(child);
        }
        schedule();
    }

    @Override public void onGlobalLayout() { refresh(); }
    @Override public void onScrollChanged() { schedule(); }

    private void schedule() {
        if (queued || !viewport.isAttachedToWindow()) return;
        queued = true;
        viewport.postOnAnimation(this);
    }

    @Override public void run() {
        queued = false;
        if (!viewport.isAttachedToWindow()) return;
        // Keep the first presentation pending until this viewport really becomes visible.
        if (!viewport.isShown() || !viewport.getGlobalVisibleRect(visible)) return;
        int stagger = 0;
        for (View child : candidates) {
            if (UiMotion.wasRevealed(child)) continue;
            if (child.isShown() && child.getGlobalVisibleRect(visible)) {
                UiMotion.reveal(child, stagger++);
            }
        }
        presented |= !candidates.isEmpty();
    }

    @Override public void onViewAttachedToWindow(View view) {
        tree = viewport.getViewTreeObserver();
        tree.addOnScrollChangedListener(this);
        tree.addOnGlobalLayoutListener(this);
        refresh();
    }

    @Override public void onViewDetachedFromWindow(View view) {
        viewport.removeCallbacks(this);
        queued = false;
        if (tree != null && tree.isAlive()) {
            tree.removeOnScrollChangedListener(this);
            tree.removeOnGlobalLayoutListener(this);
        }
        tree = null;
        candidates.clear();
    }

    void dispose() {
        onViewDetachedFromWindow(viewport);
        viewport.removeOnAttachStateChangeListener(this);
    }
}
