package com.donglan.chrona.classroom;

import org.json.JSONObject;
import org.json.JSONException;

/** Information-density heuristics only; neither OCR nor semantic correctness. */
public final class ClassroomImageQuality {
    private static final int TILES = 6;
    // Video-range black is commonly 16 rather than 0; allow mild compression noise.
    private static final int BLACK_LEVEL = 24;
    private static final double BLACK_LINE_FRACTION = .98;
    // Filtered 96px decoding can leave two mixed black/page pixels after the pure black band.
    private static final int LETTERBOX_TRANSITION_PIXELS = 2;
    private static final int DETAIL_DELTA = 18;
    private static final double BLANK_TILE_DEVIATION = 10;
    private static final double BLANK_TILE_DETAIL = .025;
    private static final double LOW_INFORMATION_BLANK_FRACTION = .80;
    private static final double LOW_INFORMATION_DETAIL_FRACTION = .008;
    private static final double DETAIL_SCORE_SATURATION = .18;
    private static final double COVERAGE_SCORE_WEIGHT = .75;
    private ClassroomImageQuality() { }

    public static JSONObject analyze(int[] gray, int width, int height) throws JSONException {
        if (width < TILES || height < TILES || gray.length != width * height)
            throw new IllegalArgumentException("Invalid grayscale dimensions");
        int left = 0, right = width, top = 0, bottom = height;
        // Ignore only near-black outer letterboxing, capped at a quarter per edge.
        while (top < height / 4 && blackRow(gray, width, top, 0, width)) top++;
        while (height - bottom < height / 4 && blackRow(gray, width, bottom - 1, 0, width)) bottom--;
        while (left < width / 4 && blackColumn(gray, width, left, top, bottom)) left++;
        while (width - right < width / 4 && blackColumn(gray, width, right - 1, top, bottom)) right--;
        // Only detected letterbox edges receive a guard; it is part of the same 25% cap.
        // This changes the statistics ROI, never the stored image or duplicate comparison.
        if (top > 0) top = Math.min(height / 4, top + LETTERBOX_TRANSITION_PIXELS);
        if (bottom < height) bottom = Math.max(height - height / 4, bottom - LETTERBOX_TRANSITION_PIXELS);
        if (left > 0) left = Math.min(width / 4, left + LETTERBOX_TRANSITION_PIXELS);
        if (right < width) right = Math.max(width - width / 4, right - LETTERBOX_TRANSITION_PIXELS);
        int tiles = Math.min(TILES, Math.min(right - left, bottom - top));
        int blankTiles = 0, details = 0, total = 0;
        for (int ty = 0; ty < tiles; ty++) for (int tx = 0; tx < tiles; tx++) {
            int x0 = left + tx * (right - left) / tiles, x1 = left + (tx + 1) * (right - left) / tiles;
            int y0 = top + ty * (bottom - top) / tiles, y1 = top + (ty + 1) * (bottom - top) / tiles;
            long sum = 0, squares = 0; int count = 0, edges = 0;
            for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
                int value = gray[y * width + x]; sum += value; squares += value * value; count++;
                if ((x + 1 < right && Math.abs(value - gray[y * width + x + 1]) >= DETAIL_DELTA)
                        || (y + 1 < bottom && Math.abs(value - gray[(y + 1) * width + x]) >= DETAIL_DELTA)) edges++;
            }
            double mean = sum / (double) count;
            double deviation = Math.sqrt(Math.max(0, squares / (double) count - mean * mean));
            if (deviation < BLANK_TILE_DEVIATION && edges / (double) count < BLANK_TILE_DETAIL) blankTiles++;
            total += count; details += edges;
        }
        double blank = blankTiles / (double) (tiles * tiles), detail = details / (double) total;
        double score = COVERAGE_SCORE_WEIGHT * (1 - blank)
                + (1 - COVERAGE_SCORE_WEIGHT) * Math.min(1, detail / DETAIL_SCORE_SATURATION);
        return new JSONObject().put("confidence", blank >= LOW_INFORMATION_BLANK_FRACTION
                || detail < LOW_INFORMATION_DETAIL_FRACTION ? "low" : "normal")
                .put("blank_fraction", blank).put("detail_fraction", detail).put("score", score);
    }

    private static boolean blackRow(int[] gray, int width, int y, int left, int right) {
        int black = 0; for (int x = left; x < right; x++) if (gray[y * width + x] <= BLACK_LEVEL) black++;
        return black >= (right - left) * BLACK_LINE_FRACTION;
    }
    private static boolean blackColumn(int[] gray, int width, int x, int top, int bottom) {
        int black = 0; for (int y = top; y < bottom; y++) if (gray[y * width + x] <= BLACK_LEVEL) black++;
        return black >= (bottom - top) * BLACK_LINE_FRACTION;
    }
}
