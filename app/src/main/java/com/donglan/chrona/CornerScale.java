package com.donglan.chrona;

/** Semantic radius tokens stay distinct from fixed dp geometry (circles and the Dock). */
final class CornerScale {
    static final int DEFAULT_LEVEL = 4;
    static final int FIELD = -1, CARD = -2, PANEL = -3, PILL = -4, CHIP = -5, ROUND = -6;
    private static final int[][] RADII = {
        {4, 5, 6, 4, 3, 4},
        {8, 10, 12, 8, 6, 8},
        {13, 16, 20, 16, 9, 18},
        {18, 22, 28, 24, 12, 999},
        {24, 30, 36, 32, 16, 999}
    };
    private CornerScale() { }
    static int clamp(int level) { return Math.max(1, Math.min(5, level)); }
    static int radius(int token, int level) {
        return token < 0 && token >= ROUND ? RADII[clamp(level) - 1][-token - 1] : token;
    }
}
