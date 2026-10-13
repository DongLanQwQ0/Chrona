package com.donglan.chrona;

/** Both pages use the same progress, so their edges meet without overlapping text. */
final class CalendarSlideMotion {
    static float incoming(float width, int direction, float progress) {
        return width * direction * (1f - progress);
    }
    static float outgoing(float width, int direction, float progress) {
        return -width * direction * progress;
    }
}
