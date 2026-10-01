package com.donglan.chrona;

import java.util.Random;

public final class WidgetDanmakuLayoutCheck {
    public static void main(String[] args) {
        Random random = new Random(17);
        int checks = 0;
        for (int height = 40; height <= 800; height++) {
            for (int textHeight : new int[]{18, 22, 32, 48, 80}) {
                WidgetDanmakuLayout layout = new WidgetDanmakuLayout(height, textHeight);
                if (!layout.visible()) continue;
                for (int i = 0; i < 20; i++) {
                    int top = layout.randomTop(random);
                    if (top < 0 || top + textHeight > layout.laneHeight)
                        throw new AssertionError("bullet must remain inside its lane");
                    if (layout.twoLanes && layout.laneHeight * 2 + WidgetDanmakuLayout.GAP_DP
                            > layout.availableHeight)
                        throw new AssertionError("concurrent lanes must fit without overlap");
                    checks++;
                }
            }
        }
        if (new WidgetDanmakuLayout(160, 48).twoLanes)
            throw new AssertionError("large fonts must fall back to one lane");
        if (new WidgetDanmakuLayout(100, 48).visible())
            throw new AssertionError("hide when not even one bullet fits");
        WidgetDanmakuLayout large = new WidgetDanmakuLayout(400, 22);
        java.util.HashSet<Integer> positions = new java.util.HashSet<>();
        for (int i = 0; i < 100; i++) positions.add(large.randomTop(random));
        if (positions.size() < 10) throw new AssertionError("positions must vary across phrases");
        System.out.println("Widget danmaku placement checks passed: " + checks);
    }
}
