package com.donglan.chrona;

/** Shared damped motion state; retargeting preserves velocity instead of restarting animations. */
final class DockSpring {
    private static final float PRESS_STIFFNESS = 900f, PRESS_DAMPING = 50f;
    private static final float PRESS_TIME_MULTIPLIER = 1.5f;
    static final float MAX_OUTWARD_WIDTH = .75f;
    static final float TAP_SCALE = 1.16f;
    private boolean following, tapPulse;
    float position, velocity, target;
    static final float COAST_DAMPING = 7.5f;
    boolean coasting;
    float lastSlot;
    void coast(float speed, float end) { following = false; velocity = speed; lastSlot = end; coasting = true; }
    void follow(float value, float speed) { following = true; coasting = false; position = target = value; velocity = speed; }
    int predictLanding(float speed, float end) {
        DockSpring prediction = new DockSpring(); prediction.position = position; prediction.coast(speed, end);
        for (int frame = 0; frame < 2400 && prediction.coasting; frame++) prediction.step(1f / 120f);
        return Math.round(Math.max(0f, Math.min(end, prediction.coasting ? prediction.position : prediction.target)));
    }
    static float outwardExtension(float distance, float maximum) {
        return maximum <= 0f ? 0f : maximum * (float) Math.tanh(Math.max(0f, distance) / maximum);
    }
    static float softUpperBoundary(float value, float limit, float approach) {
        if (approach <= 0f) return Math.min(value, limit);
        float start = limit - approach;
        return value <= start ? value : start + approach * (float) Math.tanh((value - start) / approach);
    }
    float scale = 1f, scaleVelocity, scaleTarget = 1f;
    float width = 1f, widthVelocity;
    float bar = 1f, barVelocity, barTarget = 1f;
    void press(boolean pressed) { tapPulse = false; if (!pressed) following = false; scaleTarget = pressed ? 1.5f : 1f; barTarget = pressed ? 1.1f : 1f; }
    void releaseWithPulse() {
        following = false; barTarget = 1f;
        tapPulse = scale < TAP_SCALE; scaleTarget = tapPulse ? TAP_SCALE : 1f;
    }
    void snap() { following = coasting = tapPulse = false; position = target; velocity = scaleVelocity = widthVelocity = barVelocity = 0f;
        scale = scaleTarget; bar = barTarget; width = 1f; }
    boolean step(float seconds) {
        int steps = Math.max(1, (int) Math.ceil(Math.min(.064f, Math.max(0f, seconds)) * 120f));
        float dt = Math.min(.064f, Math.max(0f, seconds)) / steps;
        for (int i = 0; i < steps; i++) {
            if (following) {
                position = target;
                velocity *= (float) Math.exp(-24f * dt);
            } else if (coasting) {
                float edge = position < 0f ? 0f : position > lastSlot ? lastSlot : position;
                float overflow = position - edge;
                if (overflow != 0f) {
                    velocity += (-180f * overflow - 18f * velocity) * dt;
                    position += velocity * dt;
                } else {
                    float decay = (float) Math.exp(-COAST_DAMPING * dt);
                    position += velocity * (1f - decay) / COAST_DAMPING;
                    velocity *= decay;
                }
            } else {
                velocity += (720f * (target - position) - 40f * velocity) * dt;
                position += velocity * dt;
            }
            float pressTime = scaleTarget > 1f ? PRESS_TIME_MULTIPLIER : 1f;
            float stiffness = PRESS_STIFFNESS / (pressTime * pressTime), damping = PRESS_DAMPING / pressTime;
            scaleVelocity += (stiffness * (scaleTarget - scale) - damping * scaleVelocity) * dt; scale += scaleVelocity * dt;
            float stretch = 1f + Math.min(.25f, Math.abs(velocity) * .025f);
            widthVelocity += (200f * (stretch - width) - 18f * widthVelocity) * dt; width += widthVelocity * dt;
            barVelocity += (stiffness * (barTarget - bar) - damping * barVelocity) * dt; bar += barVelocity * dt;
            if (tapPulse && scale >= TAP_SCALE - .005f) { tapPulse = false; scaleTarget = 1f; }
        }
        if (coasting && Math.abs(velocity) < .08f && position >= -.02f && position <= lastSlot + .02f) {
            coasting = false; target = Math.round(Math.max(0f, Math.min(lastSlot, position)));
        }
        if (!coasting && Math.abs(target - position) < .001f && Math.abs(velocity) < .01f
                && Math.abs(scaleTarget - scale) < .001f && Math.abs(scaleVelocity) < .01f
                && Math.abs(width - 1f) < .001f && Math.abs(widthVelocity) < .01f
                && Math.abs(barTarget - bar) < .001f && Math.abs(barVelocity) < .01f) { snap(); return false; }
        return true;
    }
}
