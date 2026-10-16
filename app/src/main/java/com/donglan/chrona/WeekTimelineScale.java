package com.donglan.chrona;

/** Shared forward/inverse coordinates for the empty 00:00–08:00 band in a week. */
final class WeekTimelineScale {
    static final int QUIET_START = 0, QUIET_END = 480;
    static boolean occupiesQuietHours(double startMinute, double endMinute) {
        return startMinute < QUIET_END && endMinute > QUIET_START;
    }
    private final boolean compressed;
    private final double hourHeight, bandHeight;
    WeekTimelineScale(boolean compressed, double hourHeight, double minimumBandHeight) {
        this.compressed = compressed;
        this.hourHeight = hourHeight;
        bandHeight = Math.max(minimumBandHeight, hourHeight / 2);
    }
    double y(double minute) {
        if (!compressed || minute <= QUIET_START) return minute / 60 * hourHeight;
        double start = QUIET_START / 60.0 * hourHeight;
        if (minute < QUIET_END) return start + (minute - QUIET_START) / (QUIET_END - QUIET_START) * bandHeight;
        return start + bandHeight + (minute - QUIET_END) / 60 * hourHeight;
    }
    double minuteAt(double y) {
        double start = QUIET_START / 60.0 * hourHeight;
        if (!compressed || y <= start) return y / hourHeight * 60;
        if (y < start + bandHeight) return QUIET_START + (y - start) / bandHeight * (QUIET_END - QUIET_START);
        return QUIET_END + (y - start - bandHeight) / hourHeight * 60;
    }
}
