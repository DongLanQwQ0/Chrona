package com.donglan.chrona;

/** One-finger horizontal intent; preserve vertical and nested horizontal scrolling. */
final class CalendarSwipe {
    private float startX, startY;
    private boolean blocked, claimed;
    void begin(float x, float y, boolean enabled) { startX = x; startY = y; blocked = !enabled; claimed = false; }
    boolean move(float x, float y, float slop, boolean childCanScroll) {
        if (blocked || claimed) return claimed;
        float dx = Math.abs(x - startX), dy = Math.abs(y - startY);
        if (Math.max(dx, dy) <= slop) return false;
        if (dx <= dy * 1.5f || childCanScroll) { blocked = true; return false; }
        claimed = true; return true;
    }
    void cancel() { blocked = true; }
    boolean claimed() { return claimed; }
    int finish(float x, float y, float minimumDistance) {
        int direction = claimed && !blocked && Math.abs(x - startX) >= minimumDistance
                && Math.abs(x - startX) > Math.abs(y - startY) * 1.5f ? (x < startX ? 1 : -1) : 0;
        blocked = true; claimed = false; return direction;
    }
}
