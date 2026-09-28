package com.donglan.chrona;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import java.time.LocalDateTime;
import java.util.Locale;

/** Compact theme-aware wheels. No native picker or nested scrolling parent is involved. */
final class GlassDateTimePickerView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final DateTimeSelection selection;
    private boolean date = true;
    private int column;
    private float dragOffset, downY, previousY;
    private VelocityTracker velocity;
    private ValueAnimator settle;
    private Runnable changed;

    GlassDateTimePickerView(Context context, LocalDateTime seed) {
        super(context);
        selection = new DateTimeSelection(seed);
        setFocusable(true);
        setClickable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        paint.setTextAlign(Paint.Align.CENTER);
        describe();
    }
    LocalDateTime value() { return selection.value(); }
    void onChanged(Runnable callback) { changed = callback; }
    void showTime(boolean time) {
        stopMotion();
        date = !time;
        column = 0;
        dragOffset = 0;
        describe();
        invalidate();
    }
    private int columns() { return date ? 3 : 2; }
    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
    private float sp(float value) { return value * getResources().getDisplayMetrics().scaledDensity; }
    private float header() { return Math.min(dp(30), getHeight() * .18f); }
    private float rowHeight() { return Math.max(1, (getHeight() - header()) / 5f); }
    @Override protected void onMeasure(int widthSpec, int heightSpec) {
        setMeasuredDimension(resolveSize((int) dp(300), widthSpec), resolveSize((int) dp(240), heightSpec));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        UiStyle.Palette colors = UiStyle.colors(getContext());
        float row = rowHeight(), center = header() + row * 2.5f;
        float width = getWidth() / (float) columns();
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(withAlpha(colors.primary, 24));
        canvas.drawRoundRect(new RectF(dp(2), center - row / 2, getWidth() - dp(2), center + row / 2), dp(14), dp(14), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(withAlpha(colors.outline, 100));
        canvas.drawRoundRect(new RectF(dp(2), center - row / 2, getWidth() - dp(2), center + row / 2), dp(14), dp(14), paint);
        paint.setStyle(Paint.Style.FILL);
        String[] labels = date ? new String[]{"年", "月", "日"} : new String[]{"时", "分"};
        for (int col = 0; col < columns(); col++) {
            float x = width * (col + .5f);
            paint.setColor(colors.muted);
            paint.setTextSize(Math.min(sp(12), header() * .58f));
            canvas.drawText(labels[col], x, header() * .7f, paint);
            canvas.save();
            canvas.clipRect(col * width, header(), (col + 1) * width, getHeight());
            float offset = col == column ? dragOffset : 0;
            for (int delta = -3; delta <= 3; delta++) {
                float y = center + delta * row + offset;
                float distance = Math.abs(y - center) / row;
                int number = selection.offset(date, col, delta);
                if (date && col == 0 && selection.number(date, col) + delta != number) continue;
                paint.setColor(withAlpha(distance < .5f ? colors.primary : colors.text,
                        (int) Math.max(40, 255 - distance * 65)));
                paint.setTextSize(Math.min(sp(distance < .5f ? 21 : 16), row * .54f));
                Paint.FontMetrics metrics = paint.getFontMetrics();
                String text = date && col == 0 ? Integer.toString(number)
                        : String.format(Locale.ROOT, "%02d", number);
                canvas.drawText(text, x, y - (metrics.ascent + metrics.descent) / 2, paint);
            }
            canvas.restore();
        }
    }
    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }
    private void moveBy(float amount) {
        dragOffset += amount;
        float row = rowHeight();
        while (dragOffset >= row) { selection.move(date, column, -1); dragOffset -= row; }
        while (dragOffset <= -row) { selection.move(date, column, 1); dragOffset += row; }
        describe();
        if (changed != null) changed.run();
        invalidate();
    }
    @Override public boolean onTouchEvent(MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                stopMotion();
                dragOffset = 0;
                column = Math.min(columns() - 1, Math.max(0, (int) (event.getX() * columns() / Math.max(1, getWidth()))));
                downY = previousY = event.getY();
                velocity = VelocityTracker.obtain();
                velocity.addMovement(event);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                if (velocity == null) return true;
                velocity.addMovement(event);
                moveBy(event.getY() - previousY);
                previousY = event.getY();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean tapped = event.getActionMasked() == MotionEvent.ACTION_UP && Math.abs(event.getY() - downY) < dp(6);
                float speed = 0;
                if (velocity != null) {
                    velocity.addMovement(event);
                    velocity.computeCurrentVelocity(1000);
                    speed = velocity.getYVelocity();
                    velocity.recycle(); velocity = null;
                }
                if (tapped) {
                    float center = header() + rowHeight() * 2.5f;
                    int steps = Math.round((event.getY() - center) / rowHeight());
                    if (event.getY() >= header()) selection.move(date, column, steps);
                    performClick();
                    describe();
                    if (changed != null) changed.run();
                }
                finishMove(tapped || event.getActionMasked() == MotionEvent.ACTION_CANCEL ? 0 : speed);
                if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(false);
                return true;
            default: return true;
        }
    }
    private void finishMove(float speed) {
        float row = rowHeight();
        int steps = Math.round((dragOffset + Math.max(-3 * row, Math.min(3 * row, speed * .10f))) / row);
        float distance = steps * row - dragOffset;
        float[] previous = {0};
        settle = ValueAnimator.ofFloat(0, distance);
        settle.setDuration(220L);
        settle.setInterpolator(new DecelerateInterpolator());
        settle.addUpdateListener(animation -> {
            float position = (float) animation.getAnimatedValue();
            moveBy(position - previous[0]);
            previous[0] = position;
        });
        settle.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                selection.move(date, column, -Math.round(dragOffset / rowHeight()));
                dragOffset = 0;
                describe(); if (changed != null) changed.run(); invalidate();
            }
        });
        settle.start();
    }
    /** Complete a pending snap before the dialog reads the value. */
    void finishSelection() { if (settle != null && settle.isRunning()) settle.end(); }
    private void stopMotion() {
        if (settle != null) { settle.cancel(); settle = null; }
        if (velocity != null) { velocity.recycle(); velocity = null; }
    }
    private void describe() {
        String label = date ? new String[]{"年", "月", "日"}[column] : column == 0 ? "时" : "分";
        setContentDescription(selection.value().toString() + "，当前调整" + label + "；上下滑动调整，点按切换列");
    }
    @Override public boolean performClick() { super.performClick(); return true; }
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setScrollable(true);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }
    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (action == AccessibilityNodeInfo.ACTION_CLICK) {
            finishSelection(); column = (column + 1) % columns(); describe(); invalidate(); return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            stopMotion(); dragOffset = 0;
            selection.move(date, column, action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1);
            describe(); if (changed != null) changed.run(); invalidate(); return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }
    @Override protected void onDetachedFromWindow() { stopMotion(); super.onDetachedFromWindow(); }
}
