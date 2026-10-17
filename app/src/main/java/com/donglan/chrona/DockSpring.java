package com.donglan.chrona;

/** Shared damped motion state; retargeting preserves velocity instead of restarting animations. */
final class DockSpring {
    private static final float PRESS_STIFFNESS = 1000f, PRESS_DAMPING = 48f;
    private static final float PRESS_TIME_MULTIPLIER = 1f;
    private static final float RELEASE_TIME_MULTIPLIER = PRESS_TIME_MULTIPLIER * 1.5f;
    static final float MAX_OUTWARD_WIDTH = .75f;
    static final float TAP_SCALE = 1.16f;
    private boolean following, tapPulse;
    float position, velocity, target;
    float movementSpeed = 1f;
    // The leading position responds first; the trailing position catches up independently.
    // Rendering stretches between them, rather than multiplying two uniform scale factors.
    float tailPosition, tailVelocity;
    float lean, leanVelocity, leanTarget, crossLean, crossLeanVelocity, crossLeanTarget;
    static final float COAST_DAMPING = 7.5f;
    private static final float EDGE_APPROACH_SLOTS = .25f;
    private static final float EDGE_APPROACH_DAMPING = 70f, EDGE_SPEED_DRAG = 5f;
    private static final float EDGE_RETURN_STIFFNESS = 600f, EDGE_RETURN_DAMPING = 80f;
    boolean coasting;
    float lastSlot;
    void coast(float speed, float end) { following = false; velocity = speed; lastSlot = end; coasting = true; }
    void follow(float value, float speed) { following = true; coasting = false; target = value; }
    int predictLanding(float speed, float end) {
        DockSpring prediction = new DockSpring(); prediction.movementSpeed = movementSpeed;
        prediction.position = position; prediction.coast(speed, end);
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
    float bar = 1f, barVelocity, barTarget = 1f;
    float trackingHeight = 1f, trackingHeightVelocity;
    void press(boolean pressed) {
        tapPulse = false;
        if (!pressed) { following = false; leanTarget = crossLeanTarget = 0f; }
        scaleTarget = pressed ? 1.5f : 1f; barTarget = pressed ? 1.1f : 1f;
    }
    void leanToward(float along, float across) {
        leanTarget = (float)Math.tanh(along); crossLeanTarget = (float)Math.tanh(across);
    }
    static float coverage(float center, float left, float right, float feather) {
        if (feather <= 0f) return center >= left && center <= right ? 1f : 0f;
        float distance = Math.min(center - left, right - center);
        float t = Math.max(0f, Math.min(1f, .5f + distance / (2f * feather)));
        return t * t * (3f - 2f * t);
    }
    void releaseWithPulse() {
        following = false; barTarget = 1f;
        tapPulse = scale < TAP_SCALE; scaleTarget = tapPulse ? TAP_SCALE : 1f;
    }
    void snap() { following = coasting = tapPulse = false; position = tailPosition = target;
        velocity = tailVelocity = scaleVelocity = barVelocity = 0f;
        lean = leanTarget; crossLean = crossLeanTarget; leanVelocity = crossLeanVelocity = 0f;
        scale = scaleTarget; bar = barTarget; trackingHeight = 1f; trackingHeightVelocity = 0f; }
    boolean step(float seconds) {
        int steps = Math.max(1, (int) Math.ceil(Math.min(.064f, Math.max(0f, seconds)) * 120f));
        float dt = Math.min(.064f, Math.max(0f, seconds)) / steps;
        float moveDt = dt * movementSpeed;
        for (int i = 0; i < steps; i++) {
            if (following) {
                // Retarget the existing spring, never teleport to a new touch sample.
                velocity += (1800f * (target - position) - 70f * velocity) * moveDt;
                position += velocity * moveDt;
            } else if (coasting) {
                float edge = position < 0f ? 0f : position > lastSlot ? lastSlot : position;
                float overflow = position - edge;
                if (overflow != 0f) {
                    velocity += (-EDGE_RETURN_STIFFNESS * overflow - EDGE_RETURN_DAMPING * velocity) * moveDt;
                    position += velocity * moveDt;
                } else {
                    float distance = velocity > 0f ? lastSlot - position : position;
                    float edgeDrag = Math.max(0f, 1f - distance / EDGE_APPROACH_SLOTS);
                    // Dissipate fast throws before crossing the edge, rather than letting
                    // them travel far outside and relying on a visibly large spring-back.
                    float resistance = COAST_DAMPING
                            + (EDGE_APPROACH_DAMPING + EDGE_SPEED_DRAG * Math.abs(velocity)) * edgeDrag * edgeDrag;
                    float decay = (float) Math.exp(-resistance * moveDt);
                    position += velocity * (1f - decay) / resistance;
                    velocity *= decay;
                }
            } else {
                velocity += (180f * (target - position) - 20f * velocity) * moveDt;
                position += velocity * moveDt;
            }
            tailVelocity += (300f * (position - tailPosition) - 30f * tailVelocity) * moveDt;
            tailPosition += tailVelocity * moveDt;
            float pressTime = scaleTarget > 1f ? PRESS_TIME_MULTIPLIER : RELEASE_TIME_MULTIPLIER;
            float stiffness = PRESS_STIFFNESS / (pressTime * pressTime), damping = PRESS_DAMPING / pressTime;
            scaleVelocity += (stiffness * (scaleTarget - scale) - damping * scaleVelocity) * dt; scale += scaleVelocity * dt;
            barVelocity += (stiffness * (barTarget - bar) - damping * barVelocity) * dt; bar += barVelocity * dt;
            leanVelocity += (500f * (leanTarget - lean) - 34f * leanVelocity) * dt; lean += leanVelocity * dt;
            crossLeanVelocity += (500f * (crossLeanTarget - crossLean) - 34f * crossLeanVelocity) * dt;
            crossLean += crossLeanVelocity * dt;
            float heightTarget = following && (Math.abs(target - position) > .015f || Math.abs(velocity) > .15f) ? .95f : 1f;
            trackingHeightVelocity += (500f * (heightTarget - trackingHeight) - 36f * trackingHeightVelocity) * dt;
            trackingHeight += trackingHeightVelocity * dt;
            if (tapPulse && scale >= TAP_SCALE - .005f) { tapPulse = false; scaleTarget = 1f; }
        }
        if (coasting && Math.abs(velocity) < .08f && position >= -.02f && position <= lastSlot + .02f) {
            coasting = false; target = Math.round(Math.max(0f, Math.min(lastSlot, position)));
        }
        if (!coasting && Math.abs(target - position) < .001f && Math.abs(velocity) < .01f
                && Math.abs(scaleTarget - scale) < .001f && Math.abs(scaleVelocity) < .01f
                && Math.abs(tailPosition - position) < .001f && Math.abs(tailVelocity) < .01f
                && Math.abs(leanTarget - lean) < .001f && Math.abs(leanVelocity) < .01f
                && Math.abs(crossLeanTarget - crossLean) < .001f && Math.abs(crossLeanVelocity) < .01f
                && Math.abs(trackingHeight - 1f) < .001f && Math.abs(trackingHeightVelocity) < .01f
                && Math.abs(barTarget - bar) < .001f && Math.abs(barVelocity) < .01f) { snap(); return false; }
        return true;
    }
}
