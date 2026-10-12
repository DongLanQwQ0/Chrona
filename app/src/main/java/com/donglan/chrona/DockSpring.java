package com.donglan.chrona;

/** Shared damped motion state; retargeting preserves velocity instead of restarting animations. */
final class DockSpring {
    private static final float PRESS_STIFFNESS = 900f, PRESS_DAMPING = 50f;
    float position, velocity, target;
    float scale = 1f, scaleVelocity, scaleTarget = 1f;
    float width = 1f, widthVelocity;
    float bar = 1f, barVelocity, barTarget = 1f;
    void press(boolean pressed) { scaleTarget = pressed ? 1.5f : 1f; barTarget = pressed ? 1.1f : 1f; }
    void snap() { position = target; velocity = scaleVelocity = widthVelocity = barVelocity = 0f;
        scale = scaleTarget; bar = barTarget; width = 1f; }
    boolean step(float seconds) {
        int steps = Math.max(1, (int) Math.ceil(Math.min(.064f, Math.max(0f, seconds)) * 120f));
        float dt = Math.min(.064f, Math.max(0f, seconds)) / steps;
        for (int i = 0; i < steps; i++) {
            velocity += (180f * (target - position) - 22f * velocity) * dt;
            position += velocity * dt;
            scaleVelocity += (PRESS_STIFFNESS * (scaleTarget - scale) - PRESS_DAMPING * scaleVelocity) * dt; scale += scaleVelocity * dt;
            float stretch = 1f + Math.min(.25f, Math.abs(velocity) * .025f);
            widthVelocity += (200f * (stretch - width) - 18f * widthVelocity) * dt; width += widthVelocity * dt;
            barVelocity += (PRESS_STIFFNESS * (barTarget - bar) - PRESS_DAMPING * barVelocity) * dt; bar += barVelocity * dt;
        }
        if (Math.abs(target - position) < .001f && Math.abs(velocity) < .01f
                && Math.abs(scaleTarget - scale) < .001f && Math.abs(scaleVelocity) < .01f
                && Math.abs(width - 1f) < .001f && Math.abs(widthVelocity) < .01f
                && Math.abs(barTarget - bar) < .001f && Math.abs(barVelocity) < .01f) { snap(); return false; }
        return true;
    }
}
