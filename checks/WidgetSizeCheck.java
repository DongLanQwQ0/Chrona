package com.donglan.chrona;

public final class WidgetSizeCheck {
    public static void main(String[] args) {
        if (!new WidgetSize(60, 60).compact() || new WidgetSize(60, 60).header())
            throw new AssertionError("single cell must prioritize content");
        if (!new WidgetSize(110, 240).compact() || new WidgetSize(110, 240).refresh())
            throw new AssertionError("narrow column must not crowd controls");
        if (!new WidgetSize(300, 70).compact() || new WidgetSize(300, 70).header())
            throw new AssertionError("short row must hide header");
        if (!new WidgetSize(150, 120).refresh() || new WidgetSize(150, 120).heading())
            throw new AssertionError("two-column compact controls");
        if (new WidgetSize(180, 160).compact() || !new WidgetSize(180, 160).heading())
            throw new AssertionError("full list threshold");
        if (new WidgetSize(600, 700).compact()) throw new AssertionError("large remains full");
        if (new WidgetSize(0, 0).width != 40) throw new AssertionError("invalid dimensions clamped");
        if (new WidgetSize(60, 40).padding() != 2) throw new AssertionError("tiny height must reduce padding");
        System.out.println("Widget size checks passed: 8");
    }
}
