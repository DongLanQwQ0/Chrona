package com.donglan.chrona;

import java.util.Random;

/** Two disjoint vertical bands; each phrase chooses a fresh position inside its band. */
final class WidgetDanmakuLayout {
    static final int HEADER_CLEARANCE_DP = 60;
    static final int BOTTOM_PADDING_DP = 12;
    static final int GAP_DP = 8;
    private static final int OUTER_PADDING_DP = 8;
    final int availableHeight, laneHeight, textHeight;
    final boolean twoLanes;

    WidgetDanmakuLayout(int widgetHeight, int textHeight) {
        this.textHeight = Math.max(1, textHeight);
        availableHeight = Math.max(0, widgetHeight - OUTER_PADDING_DP
                - HEADER_CLEARANCE_DP - BOTTOM_PADDING_DP);
        twoLanes = availableHeight >= this.textHeight * 2 + GAP_DP;
        laneHeight = twoLanes ? (availableHeight - GAP_DP) / 2 : availableHeight;
    }

    boolean visible() { return laneHeight >= textHeight; }

    int randomTop(Random random) {
        return random.nextInt(Math.max(0, laneHeight - textHeight) + 1);
    }
}
