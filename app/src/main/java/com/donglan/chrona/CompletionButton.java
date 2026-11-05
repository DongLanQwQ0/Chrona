package com.donglan.chrona;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.animation.LinearInterpolator;
import android.widget.ImageButton;
import android.widget.Button;

/** A committed checkmark lands in the existing button, independent of press motion and row layout. */
final class CompletionButton extends ImageButton {
    private static final long TASK_MILLIS = 420;
    private static final long ACTIVITY_MILLIS = 230;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private ValueAnimator pulse;
    private float progress = 1f;
    private boolean task;
    private int accent;

    CompletionButton(Context context) { super(context); }

    void reward(boolean task, Runnable settled) {
        if (!canReward(this)) { settled.run(); return; }
        this.task = task;
        accent = UiStyle.colors(getContext()).primary;
        setImageResource(R.drawable.ic_todo_check);
        setImageTintList(ColorStateList.valueOf(accent));
        if (task) {
            setContentDescription("已完成");
            if (Build.VERSION.SDK_INT >= 30) setStateDescription("已完成");
        }
        confirmHaptic(this, task);
        if (!UiMotion.isEnabled()) { settled.run(); return; }
        if (pulse != null) pulse.cancel();
        ValueAnimator animation = ValueAnimator.ofFloat(0f, 1f);
        pulse = animation;
        progress = 0f;
        animation.setDuration(task ? TASK_MILLIS : ACTIVITY_MILLIS);
        animation.setInterpolator(new LinearInterpolator());
        animation.addUpdateListener(value -> {
            progress = (float) value.getAnimatedValue();
            invalidate();
        });
        animation.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator ended) {
                if (pulse == animation) {
                    pulse = null;
                    progress = 1f;
                    invalidate();
                }
                // Detach may be inside a parent reconciliation; do not mutate that parent reentrantly.
                new Handler(Looper.getMainLooper()).post(settled);
            }
        });
        animation.start();
    }

    static boolean canReward(View view) {
        return view.isAttachedToWindow() && view.isShown() && view.hasWindowFocus()
                && view.getLocalVisibleRect(new Rect());
    }

    static void confirmHaptic(View view, boolean task) {
        int feedback = !task ? HapticFeedbackConstants.CLOCK_TICK : Build.VERSION.SDK_INT >= 30
                ? HapticFeedbackConstants.CONFIRM : HapticFeedbackConstants.VIRTUAL_KEY;
        view.performHapticFeedback(feedback);
    }

    /** Text-only confirmations use the same light pulse without adding an icon or changing layout. */
    static final class ActivityLabel extends Button {
        private ValueAnimator pulse;
        private float progress = 1f;

        ActivityLabel(Context context) {
            super(context);
            UiStyle.button(this, false);
        }

        void bind(boolean checked) {
            if (isSelected() != checked) {
                if (!checked && pulse != null) pulse.cancel();
                UiStyle.button(this, checked);
                setSelected(checked);
            }
            setText(checked ? "取消确认" : "确认活动");
        }

        void reward() {
            if (!canReward(this)) return;
            confirmHaptic(this, false);
            if (!UiMotion.isEnabled()) return;
            if (pulse != null) pulse.cancel();
            ValueAnimator animation = ValueAnimator.ofFloat(0f, 1f);
            pulse = animation;
            animation.setDuration(ACTIVITY_MILLIS);
            animation.setInterpolator(new LinearInterpolator());
            animation.addUpdateListener(value -> {
                progress = (float) value.getAnimatedValue();
                invalidate();
            });
            animation.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator ended) {
                    if (pulse != animation) return;
                    pulse = null;
                    progress = 1f;
                    invalidate();
                }
            });
            animation.start();
        }

        @Override protected void onDraw(Canvas canvas) {
            if (progress >= 1f) { super.onDraw(canvas); return; }
            int saved = canvas.save();
            float scale = scaleAt(progress, false);
            canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
            super.onDraw(canvas);
            canvas.restoreToCount(saved);
        }

        @Override protected void onDetachedFromWindow() {
            if (pulse != null) pulse.cancel();
            super.onDetachedFromWindow();
        }
    }

    void stopReward() { if (pulse != null) pulse.cancel(); }

    @Override protected void onDraw(Canvas canvas) {
        if (progress >= 1f) { super.onDraw(canvas); return; }
        float centerX = getWidth() / 2f, centerY = getHeight() / 2f;
        float half = Math.min(centerX, centerY), p = progress;
        float wave = 1f - (1f - p) * (1f - p);
        paint.setColor(accent);
        paint.setStyle(Paint.Style.FILL);
        paint.setAlpha(Math.round((task ? 80 : 46) * (1f - p)));
        canvas.drawCircle(centerX, centerY, half * (.47f + .35f * wave), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(half * .075f);
        float fade = task ? Math.min(1f, (1f - p) / .60f) : (1f - p) * (1f - p);
        paint.setAlpha(Math.round((task ? 210 : 135) * fade));
        canvas.drawCircle(centerX, centerY, half * (task ? .65f + .30f * wave : .53f + .40f * wave), paint);
        float scale = scaleAt(p, task);
        int state = canvas.save();
        canvas.scale(scale, scale, centerX, centerY);
        super.onDraw(canvas);
        canvas.restoreToCount(state);
    }

    private static float scaleAt(float p, boolean task) {
        float peak = task ? 1.22f : 1.08f;
        float start = task ? .72f : .92f;
        float rise = 1f - p / .38f;
        return p < .38f ? start + (peak - start) * (1f - rise * rise)
                : 1f + (peak - 1f) * (1f - (p - .38f) / .62f);
    }

    @Override protected void onDetachedFromWindow() {
        stopReward();
        super.onDetachedFromWindow();
    }
}
