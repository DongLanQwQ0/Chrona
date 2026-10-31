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
    private final float[] highlightRadii = new float[8];
    private final View highlightView;
    private final LinearLayout items;
    private int orientation = LinearLayout.HORIZONTAL;
    private final int touchSlop;
    private final IntConsumer select;
    private final Runnable finished;
    private final DockSpring spring = new DockSpring();
    private static final int HIGHLIGHT_ALPHA = 82;
    /** Glass gradient: brighter at the top edge, settling into the plate colour at the bottom. */
    private static final int GLASS_TOP_ALPHA = 94, GLASS_BOTTOM_ALPHA = 70;
    /** The specular sheen that makes the top edge read as glass rather than paint. */
    private static final int GLASS_SHEEN = 0x2EFFFFFF;
    private static final int MIN_LABEL_TEXT_SP = 8;
    private static final float MOTION_PLATE_SCALE = 1.12f;
    private static final float PLATE_LEAN_DP = 6f, CROSS_LEAN_DP = 4f;
    private static final float MAX_TAIL_EXTENSION = .75f, LABEL_FEATHER = .28f;
    /** Extra travel past the last tab, in slots, so an over-drag does not dead-stop at the edge. */
    private static final float EDGE_TRAVEL = .6f;
    /** How far the plate may leave the rail, as a fraction of one tab's width. */
    private static final float EDGE_OVERFLOW = .25f;
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
    private int selectedIndex, tapIndex;
    private int pressedItem = -1;
    /** 0 keeps the full dock; 1 leaves only the active tab at the leading corner. */
    private float minimizeFraction;
    private ValueAnimator minimizeAnimator;
    private boolean labelsHidden;
    /** The capture action outside the dock rides the same collapse so the bar never splits. */
    private java.util.function.DoubleConsumer minimizeListener;
    private final java.util.WeakHashMap<View, String> dockItemLabels = new java.util.WeakHashMap<>();
    private boolean tracking, dragging, ignoreTouch, tapLeftBounds;
    private boolean fullWidthMotion;
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
        spring.movementSpeed = .5f;
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
        UiStyle.dockGlass(this);
        UiSurfaces.minimumAcrylicMix(this, 54);
        float density = getResources().getDisplayMetrics().density;
        highlight.setCornerRadius(UiStyle.RADIUS_DOCK * density);
        int tint = UiStyle.colors(context).primary & 0x00FFFFFF;
        // A glass lens is not a flat block of colour: light gathers at the top edge and the fill
        // deepens towards the bottom, while the rim stays a shade brighter than the plate.
        highlight.setOrientation(GradientDrawable.Orientation.TOP_BOTTOM);
        // Three stops: a white sheen where light hits the top edge, the tinted body, then the
        // deeper base. One drawable, so the plate stays a single sampled surface.
        highlight.setColors(new int[]{GLASS_SHEEN, (tint | (GLASS_TOP_ALPHA << 24)),
                (tint | (GLASS_BOTTOM_ALPHA << 24))});
        highlight.setStroke(Math.max(1, Math.round(density)), 0x59FFFFFF);
    }
    void setOrientation(int value) { orientation = value; items.setOrientation(value); }
    void setHighlightMovementSpeed(float speed) { spring.movementSpeed = speed; }
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
        return motionHostLayout(overlapRows, true);
    }
    /** The title slot already has surrounding header space; do not narrow its labels again. */
    FrameLayout headerMotionHost() {
        return motionHostLayout(true, false);
    }
    /** Fill the content width without altering the highlight's motion or overflow geometry. */
    FrameLayout fullWidthMotionHost() {
        fullWidthMotion = true;
        return motionHostLayout(true, false);
    }
    private FrameLayout motionHostLayout(boolean overlapRows, boolean reserveHorizontal) {
        DockNavigationLayout dock = this;
        FrameLayout host = new FrameLayout(getContext()) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int available = View.MeasureSpec.getSize(widthSpec);
                // A standalone host reserves the outward cap and plate lean. A full-width
                // host keeps the rail width; its ancestors must allow highlight overflow.
                float plate = MOTION_PLATE_SCALE;
                float totalWeight = 0f, widestWeight = 0f;
                for (int i = 0; i < dock.itemCount(); i++) {
                    float weight = ((LinearLayout.LayoutParams)dock.itemAt(i).getLayoutParams()).weight;
                    totalWeight += weight; widestWeight = Math.max(widestWeight, weight);
                }
                float widest = totalWeight > 0f ? widestWeight / totalWeight : 1f / Math.max(1, dock.itemCount());
                float ratio = (plate - 1f) / 2f + DockSpring.MAX_OUTWARD_WIDTH * plate * widest;
                int clearance = (int)Math.ceil(2f * getResources().getDisplayMetrics().density);
                int edge = (int)Math.ceil(available * ratio / (1f + 2f * ratio)) + clearance + Math.round(dp(PLATE_LEAN_DP));
                if (!reserveHorizontal) edge = 0;
                if (getPaddingLeft() != edge || getPaddingRight() != edge)
                    setPadding(edge, getPaddingTop(), edge, getPaddingBottom());
                super.onMeasure(widthSpec, heightSpec);
                int cross = overlapRows ? clearance
                        : (int)Math.ceil(dock.getMeasuredHeight() * (plate * 1.53f - 1f) / 2f) + clearance + Math.round(dp(CROSS_LEAN_DP));
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
        if (Float.isNaN(visualPosition)) { visualPosition = index; spring.tailPosition = spring.position = spring.target = index; }
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
        int last = itemCount() - 1;
        // Outside the rail the plate keeps travelling at the end slot's pace instead of sticking
        // to the edge, so a throw past the last tab still reads as movement.
        if (position < 0f) return center(0) + position * (center(1) - center(0));
        if (position > last) return center(last) + (position - last) * (center(last) - center(last - 1));
        int from = Math.max(0, Math.min(last - 1, (int) Math.floor(position)));
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
        int last = itemCount() - 1;
        if (last <= 0) return 0f;
        // Beyond either end the position keeps counting in slots, so an over-drag still moves the
        // plate instead of collapsing onto the first or last tab.
        float firstA = center(0), firstB = center(1);
        if (firstA != firstB && (firstB > firstA ? x < firstA : x > firstA))
            return (x - firstA) / (firstB - firstA);
        float lastA = center(last), lastB = center(last - 1);
        if (lastA != lastB && (lastA > lastB ? x > lastA : x < lastA))
            return last + (x - lastA) / (lastA - lastB);
        for (int i = 0; i < last; i++) {
            float a = center(i), b = center(i + 1);
            if (a != b && x >= Math.min(a, b) && x <= Math.max(a, b))
                return i + (x - a) / (b - a);
        }
        return nearest(x);
    }

    private void moveHighlight(float x) {
        if (itemCount() == 0) return;
        // Clamp using physical bounds, which also works with RTL child ordering. Both ends keep a
        // slot of travel beyond the last centre so the plate can lean out instead of sticking.
        float left = Math.min(center(0), center(itemCount() - 1));
        float right = Math.max(center(0), center(itemCount() - 1));
        float slot = Math.abs(center(Math.min(1, itemCount() - 1)) - center(0));
        fingerPosition = positionAt(Math.max(left - slot * EDGE_TRAVEL, Math.min(right + slot * EDGE_TRAVEL, x)));
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
        boolean moving = spring.step(elapsed / 1000f);
        if (coastSelection && !spring.coasting) commitCoast();
        if (moving && !followPosted) {
            followPosted = true;
            postOnAnimation(followFinger);
        }
        applySpring();
    }
    private void commitCoast() {
        coastSelection = false;
        spring.coasting = false;
        spring.target = coastDestination;
        animateTo(coastDestination);
        post(finishTouch);
    }
    private void applySpring() {
        InteractionLayers.setPressed(this, tracking || followPosted || coastSelection ? 1f : 0f);
        float scaleX = fullWidthMotion ? 1f : spring.bar;
        float along = fullWidthMotion ? 0f : spring.lean * dp(PLATE_LEAN_DP);
        float across = spring.crossLean * dp(CROSS_LEAN_DP);
        float x = vertical() ? across : along, y = vertical() ? along : across;
        float minScale = 1f - .42f * minimizeFraction;
        scaleX *= minScale;
        // The pivot already keeps the leading edge in place; shifting would push it past the
        // 20dp content axis once collapsed.
        boolean plateMoved = Math.abs(getScaleX() - scaleX) > .001f || Math.abs(getScaleY() - spring.bar) > .001f
                || Math.abs(getTranslationX() - x) > .001f || Math.abs(getTranslationY() - y) > .001f;
        setVisualPosition(spring.position);
        setPivotX(minimizeFraction > 0f ? 0f : getWidth() / 2f);
        setPivotY(minimizeFraction > 0f ? getHeight() : getHeight() / 2f);
        setScaleX(scaleX); setScaleY(spring.bar * minScale);
        setTranslationX(x); setTranslationY(y);
        // The acrylic must sample its new location instead of translating an old sample.
        // Highlight-only motion still leaves the plate's display list untouched.
        if (plateMoved) invalidate();
        if (minimizeListener != null) minimizeListener.accept(minimizeFraction);
        updateHighlight();
        for (int i = 0; i < itemCount(); i++) {
            View item = itemAt(i);
            float alpha = i == selectedIndex ? 1f : 1f - .9f * minimizeFraction;
            if (Math.abs(item.getAlpha() - alpha) > .01f) item.setAlpha(alpha);
        }
        // A collapsed tab bar keeps the glyph only: scaled-down labels are unreadable.
        boolean hideLabels = minimizeFraction > .5f;
        if (hideLabels != labelsHidden) {
            labelsHidden = hideLabels;
            for (int i = 0; i < itemCount(); i++) {
                View item = itemAt(i);
                if (item instanceof FrameLayout wrapper && wrapper.getChildCount() > 0)
                    item = wrapper.getChildAt(0);
                if (!(item instanceof TextView label)) continue;
                if (hideLabels) {
                    dockItemLabels.put(item, String.valueOf(label.getText()));
                    label.setText("");
                } else {
                    String saved = dockItemLabels.remove(item);
                    if (saved != null) label.setText(saved);
                }
            }
        }
    }

    /** Collapses to the active tab while the content scrolls down, as a system tab bar does. */
    void setMinimizeListener(java.util.function.DoubleConsumer listener) { minimizeListener = listener; }

    void setMinimized(boolean value) {
        if (vertical()) return;
        float target = value ? 1f : 0f;
        if (Math.abs(minimizeFraction - target) < .001f && minimizeAnimator == null) return;
        if (minimizeAnimator != null) { minimizeAnimator.cancel(); minimizeAnimator = null; }
        if (!ValueAnimator.areAnimatorsEnabled()) {
            minimizeFraction = target;
            applySpring();
            return;
        }
        minimizeAnimator = ValueAnimator.ofFloat(minimizeFraction, target);
        minimizeAnimator.setDuration(value ? UiMotion.EXIT : UiMotion.ENTER);
        minimizeAnimator.setInterpolator(UiMotion.SETTLE);
        minimizeAnimator.addUpdateListener(animation -> {
            minimizeFraction = (float) animation.getAnimatedValue();
            applySpring();
        });
        minimizeAnimator.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                minimizeAnimator = null;
            }
        });
        minimizeAnimator.start();
    }

    /**
     * The tab under the finger answers on its own: a small lift on top of the shared plate. It runs
     * on the platform animator so the interpolation, duration and refresh-rate pacing are the
     * shared ones, and reduced motion lands immediately.
     */
    private void setPressedItem(int index) {
        if (index == pressedItem) return;
        int previous = pressedItem;
        pressedItem = index;
        animateItemPress(previous, false);
        animateItemPress(index, true);
    }

    private void animateItemPress(int index, boolean pressed) {
        if (index < 0 || index >= itemCount()) return;
        View cell = itemAt(index);
        float scale = pressed ? 1.06f : 1f;
        float lift = pressed ? -dp(1) : 0f;
        cell.animate().cancel();
        if (!ValueAnimator.areAnimatorsEnabled()) {
            cell.setScaleX(scale); cell.setScaleY(scale); cell.setTranslationY(lift);
            return;
        }
        cell.animate().scaleX(scale).scaleY(scale).translationY(lift)
                .setDuration(pressed ? UiMotion.PRESS : UiMotion.RELEASE)
                .setInterpolator(UiMotion.SETTLE)
                .start();
    }
    private void press(boolean pressed) { spring.press(pressed); animateTo(spring.target); }

    private void cancelFingerFollow() {
        removeCallbacks(followFinger);
        followPosted = false;
    }

    private void updateLabels(float left, float right, float feather) {
        // The lens colour is fixed at construction: setting it again would replace the glass
        // gradient with a flat fill on the very first frame.
        UiStyle.Palette palette = UiStyle.colors(getContext());
        for (int i = 0; i < itemCount(); i++) {
            View cell = itemAt(i);
            if (cell instanceof FrameLayout wrapper) cell = wrapper.getChildAt(0);
            if (cell instanceof TextView label) {
                float amount = DockSpring.coverage(center(i), left, right, feather);
                int color = blend(palette.muted, palette.primary, amount);
                if (label.getCurrentTextColor() != color) {
                    label.setTextColor(color);
                    label.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(color));
                }
            }
        }
    }
    private static int blend(int from, int to, float amount) {
        int result = 0;
        for (int shift = 0; shift <= 24; shift += 8) {
            int a = (from >>> shift) & 255, b = (to >>> shift) & 255;
            result |= Math.round(a + (b - a) * amount) << shift;
        }
        return result;
    }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }

    private void updateHighlight() {
        View cell = itemAt(selectedIndex);
        if (cell != null) {
            float x = centerAt(visualPosition);
            float standardWidth = extentAt(visualPosition);
            float emphasis = spring.scale;
            // The leading end follows the finger, while the tail catches up. Limit the
            // extra length in physical pixels so unequal labels cannot produce a ribbon.
            float lag = x - centerAt(spring.tailPosition);
            float heldAmount = Math.max(0f, Math.min(1f, (emphasis - DockSpring.TAP_SCALE) / (1.5f - DockSpring.TAP_SCALE)));
            float tailExtension = .12f + (MAX_TAIL_EXTENSION - .12f) * heldAmount;
            float stretch = Math.copySign(DockSpring.outwardExtension(Math.abs(lag), standardWidth * tailExtension), lag);
            x -= stretch / 2f;
            float width = standardWidth * emphasis + Math.abs(stretch);
            float height = (vertical() ? cell.getWidth() : cell.getHeight()) * emphasis * spring.trackingHeight;
            float board = vertical() ? getHeight() : getWidth();
            float left = x - width / 2f;
            float cross = vertical() ? items.getLeft() + (cell.getLeft() + cell.getRight()) / 2f
                    : items.getTop() + (cell.getTop() + cell.getBottom()) / 2f;
            float top = cross - height / 2f;
            float right = x + width / 2f;
            // Reduce inner-end speed before its anchor; both approach and rebound are smooth. The
            // stop sits a quarter of a tab past the rim: further out than the original half-tab
            // inset, but still a limit, so the plate never leaves the rail entirely.
            float slack = standardWidth * EDGE_OVERFLOW;
            left = DockSpring.softUpperBoundary(left, board + slack, standardWidth * .6f);
            right = -DockSpring.softUpperBoundary(-right, slack, standardWidth * .6f);
            float maximum = standardWidth * DockSpring.MAX_OUTWARD_WIDTH;
            if (left < 0) left = -DockSpring.outwardExtension(-left, maximum);
            if (right > board) right = board + DockSpring.outwardExtension(right - board, maximum);

            int baseWidth = Math.max(1, cell.getWidth()), baseHeight = Math.max(1, cell.getHeight());
            android.view.ViewGroup.LayoutParams layout = highlightView.getLayoutParams();
            if (layout.width != baseWidth || layout.height != baseHeight)
                highlightView.setLayoutParams(new FrameLayout.LayoutParams(baseWidth, baseHeight));
            if (highlightView.getWidth() != baseWidth || highlightView.getHeight() != baseHeight)
                highlightView.layout(0, 0, baseWidth, baseHeight);
            float scaleX = (vertical() ? height : right - left) / baseWidth;
            float scaleY = (vertical() ? right - left : height) / baseHeight;
            float radius = Math.min(baseWidth * scaleX * getScaleX(), baseHeight * scaleY * getScaleY()) / 2f;
            // Counter the render-node stretch: physical end caps stay circular at every
            // width, including during reversal and when changing between unequal labels.
            for (int i = 0; i < highlightRadii.length; i += 2) {
                highlightRadii[i] = radius / (scaleX * getScaleX());
                highlightRadii[i + 1] = radius / (scaleY * getScaleY());
            }
            highlight.setCornerRadii(highlightRadii);
            // Geometry is in dock coordinates. FrameLayout already positions this child
            // at its padded layout origin; translation must only add the remaining offset.
            highlightView.setTranslationX((vertical() ? top : left) - highlightView.getLeft());
            highlightView.setTranslationY((vertical() ? left : top) - highlightView.getTop());
            highlightView.setScaleX(scaleX);
            highlightView.setScaleY(scaleY);
            updateLabels(left, right, standardWidth * LABEL_FEATHER);
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
        press(true);
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

    private void leanToward(MotionEvent event) {
        float alongSize = vertical() ? getHeight() : getWidth();
        float crossSize = vertical() ? getWidth() : getHeight();
        if (alongSize <= 0 || crossSize <= 0) return;
        spring.leanToward(fullWidthMotion ? 0f : (axis(event.getX(), event.getY()) - alongSize / 2f) / (alongSize / 2f),
                (axis(event.getY(), event.getX()) - crossSize / 2f) / (crossSize / 2f));
        animateTo(spring.target);
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
            // Preview immediately, but keep navigation owned by the eventual click/drag.
            // Waiting for Android's long-press timeout makes a pressed pill feel detached.
            spring.follow(nearest(axis(downX, downY)), 0f);
            setPressedItem(nearest(axis(downX, downY)));
            leanToward(event);
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
            leanToward(event);
            if (!insideDock(event)) tapLeftBounds = true;
            float dx = Math.abs(axis(event.getX() - downX, event.getY() - downY));
            float dy = Math.abs(axis(event.getY() - downY, event.getX() - downX));
            if (!dragging && (dx > touchSlop || dy > touchSlop)) {
                removeCallbacks(longPress);
                if (dx > dy * 1.25f) beginDrag(axis(event.getX(), event.getY()));
                // Cross-axis movement within a button is still an ordinary Android tap.
            }
            if (dragging) moveHighlight(axis(event.getX(), event.getY()));
            if (dragging) setPressedItem(Math.round(positionAt(axis(event.getX(), event.getY()))));
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
            setPressedItem(-1);
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
            } else if (owned || action == MotionEvent.ACTION_CANCEL || tapLeftBounds) {
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
