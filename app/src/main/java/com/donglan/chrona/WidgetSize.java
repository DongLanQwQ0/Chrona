package com.donglan.chrona;

/** Density-independent breakpoints; cell dimensions themselves belong to the launcher. */
final class WidgetSize {
    final int width, height;
    WidgetSize(int width, int height) {
        this.width = Math.max(40, width);
        this.height = Math.max(40, height);
    }
    boolean compact() { return width < 180 || height < 160; }
    boolean header() { return height >= 90; }
    boolean heading() { return header() && width >= 180; }
    boolean refresh() { return header() && width >= 130; }
    int padding() { return height < 70 ? 2 : compact() ? 6 : 12; }
}
