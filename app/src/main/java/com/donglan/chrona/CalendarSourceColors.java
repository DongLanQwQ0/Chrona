package com.donglan.chrona;

/** Keep a calendar's identity while bringing its accent into the active theme. */
final class CalendarSourceColors {
    private static final int SOURCE_PERCENT = 65;
    static int mix(Integer calendarColor, int themeColor) {
        if (calendarColor == null) return themeColor;
        int source = calendarColor;
        int result = 0xFF000000;
        for (int shift : new int[]{16, 8, 0}) {
            int channel = (((source >>> shift) & 255) * SOURCE_PERCENT
                    + ((themeColor >>> shift) & 255) * (100 - SOURCE_PERCENT) + 50) / 100;
            result |= channel << shift;
        }
        return result;
    }
}
