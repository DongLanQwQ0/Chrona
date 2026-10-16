package com.donglan.chrona;

import android.content.Context;
import android.animation.ValueAnimator;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.VelocityTracker;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntConsumer;

/** Shared horizontal/vertical dock; glass, highlight and buttons have separate render nodes. */
final class DockNavigationLayout extends FrameLayout {
    private final GradientDrawable highlight = new GradientDrawable();
    private final View highlightView;
    private final LinearLayout items;
    private int orientation = LinearLayout.HORIZONTAL;
    private final int touchSlop;
    private final IntConsumer select;
    private final Runnable finished;
    private final DockSpring spring = new DockSpring();
    private static final int HIGHLIGHT_ALPHA = 82;
    private static final int MIN_LABEL_TEXT_SP = 8;
    private static final float MOTION_PLATE_SCALE = 1.12f;
    private VelocityTracker fingerVelocity;
    private float downX, downY, fingerPosition;
    private long followFrameTime;
    private boolean followPosted;
    private final Runnable followFinger = this::advanceFingerFollow;
    private float visualPosition = Float.NaN;
    private float bodyDragFrom = Float.NaN;
    private float settleTo;
    private boolean pageSettling;
    private boolean coastSelection;
    private int coastDestination;
    private int selectedIndex, tapIndex, labelIndex = -1;
    private boolean tracking, dragging, ignoreTouch, tapLeftBounds;
    private final Runnable finishTouch = this::finishTouch;

    private void finishTouch() {
        // The child posts its normal PerformClick before this runnable. Detaching it earlier
        // removes that callback and silently loses the tap.
        if (!tracking && !coastSelection) finished.run();
    }
    private final Runnable longPress = () -> {
        if (tracking && !dragging && !ignoreTouch) {
            beginDrag(axis(downX, downY));
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
        }
    };

    DockNavigationLayout(Context context, IntConsumer select, Runnable finished) {
        super(context);
        this.select = select;
        this.finished = finished;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(false); setClipToPadding(false);
        highlightView = new View(context);
        highlightView.setBackground(highlight);
        highlightView.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        highlightView.setPivotX(0f); highlightView.setPivotY(0f);
        addView(highlightView, new FrameLayout.LayoutParams(1, 1));
        items = new LinearLayout(context);
        // Auto-sized labels must share cell centers, not different text baselines.
        items.setBaselineAligned(false);
        items.setClipChildren(false); items.setClipToPadding(false);
        addView(items, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.glass(this);
        UiStyle.minimumAcrylicMix(this, 54);
        highlight.setCornerRadius(UiStyle.RADIUS_PANEL * getResources().getDisplayMetrics().density);
        highlight.setColor((UiStyle.colors(context).primary & 0x00FFFFFF) | (HIGHLIGHT_ALPHA << 24));
    }
    void setOrientation(int value) { orientation = value; items.setOrientation(value); }
    int itemCount() { return items.getChildCount(); }
    View itemAt(int index) { return items.getChildAt(index); }
    void addItem(View view, LinearLayout.LayoutParams params) { fitLabel(view); items.addView(view, params); }
    void addItem(View view) { fitLabel(view); items.addView(view); }
    /** Reserve the complete animated envelope inside a clipping scroll/dialog viewport. */
    FrameLayout motionHost() {
        return motionHost(false);
    }
    /** In dense forms the highlight may overlap adjacent rows, but never their labels' layout. */
    FrameLayout motionHost(boolean overlapRows) {
        return motionHost(overlapRows, 0f);
    }
    /** A shared widest-item fraction keeps grouped selector plates aligned. */
    FrameLayout motionHost(boolean overlapRows, float sharedWidestFraction) {
        DockNavigationLayout dock = this;
        FrameLayout host = new FrameLayout(getContext()) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = View.MeasureSpec.getSize(widthSpec);
                // Reserve the held 1.5x highlight and plate expansion. Fling overflow
                // can draw through intermediate parents; only the viewport is a boundary.
                float plate = MOTION_PLATE_SCALE;
                float totalWeight = 0f, widestWeight = 0f;
                for (int i = 0; i < dock.itemCount(); i++) {
                    float weight = ((LinearLayout.LayoutParams)dock.itemAt(i).getLayoutParams()).weight;
                    totalWeight += weight; widestWeight = Math.max(widestWeight, weight);
                }
                float widest = totalWeight > 0f ? widestWeight / totalWeight : 1f / Math.max(1, dock.itemCount());
                  if (sharedWidestFraction > 0f) widest = sharedWidestFraction;
                float ratio = (plate - 1f) / 2f + .25f * plate * widest;
                int clearance = (int)Math.ceil(2f * getResources().getDisplayMetrics().density);
                int edge = (int)Math.ceil(available * ratio / (1f + 2f * ratio)) + clearance + Math.round(4 * getResources().getDisplayMetrics().density);
                if (getPaddingLeft() != edge || getPaddingRight() != edge)
                    setPadding(edge, getPaddingTop(), edge, getPaddingBottom());
                super.onMeasure(widthSpec, heightSpec);
                int cross = overlapRows ? clearance
                        : (int)Math.ceil(dock.getMeasuredHeight() * (plate * 1.53f - 1f) / 2f) + clearance;
                if (getPaddingTop() != cross || getPaddingBottom() != cross) {
                    setPadding(edge, cross, edge, cross);
                    super.onMeasure(widthSpec, heightSpec);
                }
            }
        };
        host.setClipChildren(false);
        host.setClipToPadding(false);
        host.addView(dock, new FrameLayout.LayoutParams(-1, -2));
        return host;
    }
    private static void fitLabel(View view) {
        if (view instanceof FrameLayout wrapper) view = wrapper.getChildAt(0);
        if (!(view instanceof TextView label)) return;
        android.util.DisplayMetrics metrics = label.getResources().getDisplayMetrics();
        // Android 14+ scales sp nonlinearly; retain the caller's original sp ceiling.
        float requestedSp = Build.VERSION.SDK_INT >= 34
                ? TypedValue.deriveDimension(TypedValue.COMPLEX_UNIT_SP, label.getTextSize(), metrics)
                : label.getTextSize() / metrics.scaledDensity;
        int maximumSp = Math.max(MIN_LABEL_TEXT_SP, Math.round(requestedSp));
        label.setSingleLine(true);
        // Single-line text normally scrolls horizontally, which would bypass width fitting.
        label.setHorizontallyScrolling(false);
        label.setIncludeFontPadding(false);
        label.setAutoSizeTextTypeUniformWithConfiguration(MIN_LABEL_TEXT_SP, maximumSp, 1, TypedValue.COMPLEX_UNIT_SP);
    }
    void clearItems() { items.removeAllViews(); }
    private boolean vertical() { return orientation == LinearLayout.VERTICAL; }
    private float axis(float x, float y) { return vertical() ? y : x; }
    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom); applySpring();
    }

    boolean isTrackingTouch() { return tracking; }
    boolean isAnimating() { return followPosted; }

    void setSelectedIndex(int index) {
        selectedIndex = index;
        if (Float.isNaN(visualPosition)) { visualPosition = index; spring.position = spring.target = index; }
        labelIndex = -1;
        if (!dragging) updateLabels(Math.round(visualPosition));
        updateHighlight();
        invalidate();
    }

    void showPageProgress(int from, int to, float progress) {
        if (dragging) return;
        pageSettling = false;
        float start = Float.isNaN(bodyDragFrom) ? from : bodyDragFrom;
        animateTo(start + (to - start) * Math.max(0f, Math.min(1f, progress)));
    }

    void beginBodyDrag() {
        coastSelection = spring.coasting = false;
        cancelFingerFollow();
        spring.velocity = 0f; spring.target = spring.position;
        pageSettling = false;
        bodyDragFrom = visualPosition;
        press(false);
    }

    /** Move toward the known selection immediately, independently of expensive page transitions. */
    void beginPageSettle(int destination) {
        if (coastSelection) return;
        cancelFingerFollow();
        settleTo = destination;
        bodyDragFrom = Float.NaN;
        pageSettling = true;
        animateTo(destination);
    }

    void updatePageSettle(float fraction) {
        if (dragging || coastSelection || !pageSettling) return;
        animateTo(settleTo);
    }

    void finishPageSelection(int index) {
        pageSettling = false;
        bodyDragFrom = Float.NaN;
        setSelectedIndex(index);
        if (!dragging) animateTo(index);
    }

    private void setVisualPosition(float position) {
        visualPosition = position;
        updateLabels(Math.max(0, Math.min(itemCount() - 1, Math.round(position))));
    }

    private void resumeOrReturnHighlight() {
        if (pageSettling) {
            animateTo(settleTo);
        } else {
            animateTo(selectedIndex);
        }
    }

    private float center(int index) {
        View child = itemAt(index);
        return child == null ? 0 : vertical() ? items.getTop() + (child.getTop() + child.getBottom()) / 2f
                : items.getLeft() + (child.getLeft() + child.getRight()) / 2f;
    }

    private int nearest(float x) {
        int result = 0;
        float distance = Float.MAX_VALUE;
        for (int i = 0; i < itemCount(); i++) {
            float candidate = Math.abs(x - center(i));
            if (candidate < distance) { result = i; distance = candidate; }
        }
        return result;
    }

    private float centerAt(float position) {
        if (itemCount() < 2) return center(0);
        int from = Math.max(0, Math.min(itemCount() - 2, (int) Math.floor(position)));
        int to = from + 1;
        return center(from) + (center(to) - center(from)) * (position - from);
    }

    private float extentAt(float position) {
        int from = Math.max(0, Math.min(itemCount() - 1, (int)Math.floor(position)));
        int to = Math.min(itemCount() - 1, from + 1);
        View a = itemAt(from), b = itemAt(to);
        float first = vertical() ? a.getHeight() : a.getWidth();
        float last = vertical() ? b.getHeight() : b.getWidth();
        return first + (last - first) * Math.max(0f, Math.min(1f, position - from));
    }

    private float positionAt(float x) {
        for (int i = 0; i < itemCount() - 1; i++) {
            float a = center(i), b = center(i + 1);
            if (a != b && x >= Math.min(a, b) && x <= Math.max(a, b))
                return i + (x - a) / (b - a);
        }
        return nearest(x);
    }

    private void moveHighlight(float x) {
        if (itemCount() == 0) return;
        // Clamp using physical bounds, which also works with RTL child ordering.
        float left = Math.min(center(0), center(itemCount() - 1));
        float right = Math.max(center(0), center(itemCount() - 1));
        fingerPosition = positionAt(Math.max(left, Math.min(right, x)));
        spring.follow(fingerPosition, releasedVelocity());
        applySpring();
        animateTo(fingerPosition);
    }
    private void animateTo(float position) {
        if (coastSelection) return;
        spring.target = position;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            cancelFingerFollow(); spring.snap(); applySpring();
        } else if (!followPosted) {
            followFrameTime = android.os.SystemClock.uptimeMillis();
            followPosted = true;
            postOnAnimation(followFinger);
        }
    }

    private void advanceFingerFollow() {
        followPosted = false;
        if (!ValueAnimator.areAnimatorsEnabled()) {
            if (coastSelection) commitCoast();
            spring.snap(); applySpring();
            return;
        }
        long now = android.os.SystemClock.uptimeMillis();
        long elapsed = Math.max(0L, Math.min(64L, now - followFrameTime));
        followFrameTime = now;
        boolean moving = spring.step(elapsed / 1000f); applySpring();
        if (coastSelection && !spring.coasting) commitCoast();
        if (moving && !followPosted) {
            followPosted = true;
            postOnAnimation(followFinger);
        }
    }
    private void commitCoast() {
        coastSelection = false;
        spring.coasting = false;
        spring.target = coastDestination;
        animateTo(coastDestination);
        post(finishTouch);
    }
    private void applySpring() {
        setVisualPosition(spring.position); setScaleX(spring.bar); setScaleY(spring.bar);
        updateHighlight();
    }
    private void press(boolean pressed) { spring.press(pressed); animateTo(spring.target); }

    private void cancelFingerFollow() {
        removeCallbacks(followFinger);
        followPosted = false;
    }

    private void updateLabels(int active) {
        if (labelIndex == active) return;
        labelIndex = active;
        UiStyle.Palette palette = UiStyle.colors(getContext());
        highlight.setColor((palette.primary & 0x00FFFFFF) | (HIGHLIGHT_ALPHA << 24));
        for (int i = 0; i < itemCount(); i++) {
            View cell = itemAt(i);
            if (cell instanceof FrameLayout wrapper) cell = wrapper.getChildAt(0);
            if (cell instanceof TextView label) {
                int color = i == active ? palette.primary : palette.muted;
                label.setTextColor(color);
                label.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
            }
        }
    }

    private void updateHighlight() {
        View cell = itemAt(selectedIndex);
        if (cell != null) {
            float x = centerAt(visualPosition);
            float standardWidth = extentAt(visualPosition);
            float emphasis = spring.scale;
            float width = standardWidth * emphasis * spring.width;
            float height = (vertical() ? cell.getWidth() : cell.getHeight()) * emphasis * spring.trackingHeight;
            float board = vertical() ? getHeight() : getWidth();
            float left = x - width / 2f;
            float cross = vertical() ? items.getLeft() + (cell.getLeft() + cell.getRight()) / 2f
                    : items.getTop() + (cell.getTop() + cell.getBottom()) / 2f;
            int top = Math.round(cross - height / 2f);
            float right = x + width / 2f;
            // Reduce inner-end speed before its anchor; both approach and rebound are smooth.
            left = DockSpring.softUpperBoundary(left, board - standardWidth / 2f, standardWidth / 2f);
            right = -DockSpring.softUpperBoundary(-right, -standardWidth / 2f, standardWidth / 2f);
            float maximum = standardWidth * DockSpring.MAX_OUTWARD_WIDTH;
            if (left < 0) left = -Math.round(DockSpring.outwardExtension(-left, maximum));
            if (right > board) right = board + DockSpring.outwardExtension(right - board, maximum);
            int baseWidth = Math.max(1, cell.getWidth()), baseHeight = Math.max(1, cell.getHeight());
            android.view.ViewGroup.LayoutParams layout = highlightView.getLayoutParams();
            if (layout.width != baseWidth || layout.height != baseHeight)
                highlightView.setLayoutParams(new FrameLayout.LayoutParams(baseWidth, baseHeight));
            if (highlightView.getWidth() != baseWidth || highlightView.getHeight() != baseHeight)
                highlightView.layout(0, 0, baseWidth, baseHeight);
            // Geometry is in dock coordinates. FrameLayout already positions this child
            // at its padded layout origin; translation must only add the remaining offset.
            highlightView.setTranslationX((vertical() ? top : left) - highlightView.getLeft());
            highlightView.setTranslationY((vertical() ? left : top) - highlightView.getTop());
            highlightView.setScaleX((vertical() ? height : right - left) / baseWidth);
            highlightView.setScaleY((vertical() ? right - left : height) / baseHeight);
        }
    }

    private void cancelChildTouch() {
        MotionEvent cancel = MotionEvent.obtain(android.os.SystemClock.uptimeMillis(),
                android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, downX, downY, 0);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    private void beginDrag(float x) {
        removeCallbacks(longPress);
        cancelChildTouch();
        dragging = true;
        getParent().requestDisallowInterceptTouchEvent(true);
        moveHighlight(x);
    }

    private void recordVelocity(MotionEvent event) {
        if (event.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN) { recycleVelocity(); return; }
        if (event.getActionMasked() == MotionEvent.ACTION_DOWN) {
            recycleVelocity(); fingerVelocity = VelocityTracker.obtain();
        }
        if (fingerVelocity == null) return;
        // Use physical screen samples; the dock's own 1.1x scaling must not attenuate velocity.
        MotionEvent screen = MotionEvent.obtain(event.getDownTime(), event.getEventTime(),
                event.getActionMasked(), event.getRawX(), event.getRawY(), 0);
        fingerVelocity.addMovement(screen); screen.recycle();
    }
    private void recycleVelocity() {
        if (fingerVelocity != null) { fingerVelocity.recycle(); fingerVelocity = null; }
    }
    private float releasedVelocity() {
        if (fingerVelocity == null || itemCount() < 2) return 0f;
        fingerVelocity.computeCurrentVelocity(1000);
        int from = Math.max(0, Math.min(itemCount() - 2, (int)Math.floor(visualPosition)));
        float pixelsPerSlot = (center(from + 1) - center(from)) * (vertical() ? getScaleY() : getScaleX());
        return Math.abs(pixelsPerSlot) < 1f ? 0f
                : (vertical() ? fingerVelocity.getYVelocity() : fingerVelocity.getXVelocity()) / pixelsPerSlot;
    }

    @Override public void setEnabled(boolean enabled) {
        if (!enabled && tracking) {
            // Cancel while still enabled so the ordinary touch cleanup releases children,
            // held scale, velocity tracking and the current drag without committing it.
            MotionEvent cancel = MotionEvent.obtain(android.os.SystemClock.uptimeMillis(),
                    android.os.SystemClock.uptimeMillis(), MotionEvent.ACTION_CANCEL, downX, downY, 0);
            try { dispatchTouchEvent(cancel); } finally { cancel.recycle(); }
            removeCallbacks(finishTouch);
            if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
        }
        super.setEnabled(enabled);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        recordVelocity(event);
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            coastSelection = spring.coasting = false;
            cancelFingerFollow();
            removeCallbacks(finishTouch);
            tracking = true;
            ignoreTouch = false;
            dragging = false;
            tapLeftBounds = false;
            downX = event.getX();
            downY = event.getY();
            press(true);
            getParent().requestDisallowInterceptTouchEvent(true);
            postDelayed(longPress, ViewConfiguration.getLongPressTimeout());
        } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
            recycleVelocity();
            cancelFingerFollow();
            removeCallbacks(longPress);
            if (!dragging) cancelChildTouch();
            dragging = false;
            ignoreTouch = true;
            press(false);
            resumeOrReturnHighlight();
        } else if (action == MotionEvent.ACTION_MOVE && tracking && !ignoreTouch) {
            if (!insideDock(event)) tapLeftBounds = true;
            float dx = Math.abs(axis(event.getX() - downX, event.getY() - downY));
            float dy = Math.abs(axis(event.getY() - downY, event.getX() - downX));
            if (!dragging && (dx > touchSlop || dy > touchSlop)) {
                removeCallbacks(longPress);
                if (dx > dy * 1.25f) beginDrag(axis(event.getX(), event.getY()));
                // Cross-axis movement within a button is still an ordinary Android tap.
            }
            if (dragging) moveHighlight(axis(event.getX(), event.getY()));
        }
        boolean owned = dragging || ignoreTouch;
        boolean handled = owned || super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            removeCallbacks(longPress);
            boolean commit = dragging && action == MotionEvent.ACTION_UP;
            boolean pulse = action == MotionEvent.ACTION_UP && !ignoreTouch && (commit || !tapLeftBounds);
            float releaseSpeed = commit ? releasedVelocity() : 0f;
            cancelFingerFollow();
            tracking = false;
            dragging = false;
            ignoreTouch = false;
            press(false);
            if (pulse && ValueAnimator.areAnimatorsEnabled()) {
                spring.releaseWithPulse();
                animateTo(spring.target);
            }
            if (commit) {
                pageSettling = false;
                coastDestination = spring.predictLanding(releaseSpeed, Math.max(0, itemCount() - 1));
                coastSelection = true;
                spring.coast(releaseSpeed, Math.max(0, itemCount() - 1));
                select.accept(coastDestination);
                if (!ValueAnimator.areAnimatorsEnabled()) {
                    commitCoast(); spring.snap(); applySpring();
                }
            } else if (owned) {
                resumeOrReturnHighlight();
            }
            invalidate();
            recycleVelocity();
            post(finishTouch);
        }
        return handled;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        // Padding and gaps are also valid starts for a dock gesture.
        if (event.getActionMasked() == MotionEvent.ACTION_UP && !dragging && !ignoreTouch
                && !tapLeftBounds && insideDock(event)) {
            tapIndex = nearest(axis(event.getX(), event.getY()));
            performClick();
        }
        return true;
    }

    private boolean insideDock(MotionEvent event) {
        return event.getX() >= -touchSlop && event.getX() <= getWidth() + touchSlop
                && event.getY() >= -touchSlop && event.getY() <= getHeight() + touchSlop;
    }

    @Override public boolean performClick() {
        super.performClick();
        if (itemCount() > 0) select.accept(tapIndex);
        return true;
    }

    @Override protected void onDetachedFromWindow() {
        coastSelection = spring.coasting = false;
        recycleVelocity();
        cancelFingerFollow();
        removeCallbacks(longPress);
        removeCallbacks(finishTouch);
        spring.press(false); spring.target = spring.position; spring.snap(); applySpring();
        tracking = dragging = ignoreTouch = false;
        super.onDetachedFromWindow();
    }
}
