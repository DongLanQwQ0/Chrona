package com.donglan.chrona;

import android.content.Context;
import android.graphics.Color;
import android.view.View;
import android.widget.RemoteViews;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Random;

/** Launcher-owned ViewFlippers animate locally, without repeated app broadcasts. */
final class WidgetDanmaku {
    private static final int MIN_INTERVAL_MS = 8000;
    private static final int MAX_INTERVAL_MS = 12000;
    // The right-to-left animation lasts 8s; the reverse lasts 12s. Never cut either short.
    private static final int SLOW_LANE_EXTRA_INTERVAL_MS = 4000;
    private static final int TEXT_ALPHA = 128;
    private static final int MIN_WIDTH_DP = 140;
    private static final int MIN_HEIGHT_DP = 160;
    private WidgetDanmaku() { }

    static void bind(Context context, RemoteViews views, WidgetSize size, int color, int id, long now) {
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        android.graphics.Paint textPaint = new android.graphics.Paint();
        textPaint.setTextSize(12 * metrics.scaledDensity);
        android.graphics.Paint.FontMetricsInt font = textPaint.getFontMetricsInt();
        int textHeight = Math.max(22, (int) Math.ceil((font.bottom - font.top) / metrics.density) + 4);
        WidgetDanmakuLayout placement = new WidgetDanmakuLayout(size.height, textHeight);
        boolean visible = size.width >= MIN_WIDTH_DP && size.height >= MIN_HEIGHT_DP
                && placement.visible();
        for (int lane : new int[]{R.id.widget_bullets_left, R.id.widget_bullets_right}) {
            // Child 0 is always blank. Selecting it clears the shared in-animation from
            // every text child before RemoteViews removes them. Otherwise ViewGroup
            // keeps removed, animated phrases as disappearing children until a draw,
            // which may be delayed while the launcher is hidden. Clear hidden lanes too.
            views.setDisplayedChild(lane, 0);
            views.removeAllViews(lane);
        }
        views.setViewVisibility(R.id.widget_danmaku, visible ? View.VISIBLE : View.GONE);
        if (!visible) return;
        views.setViewPadding(R.id.widget_danmaku, Math.round(12 * metrics.density),
                Math.round(WidgetDanmakuLayout.HEADER_CLEARANCE_DP * metrics.density),
                Math.round(12 * metrics.density),
                Math.round(WidgetDanmakuLayout.BOTTOM_PADDING_DP * metrics.density));
        views.setViewVisibility(R.id.widget_bullets_right, placement.twoLanes ? View.VISIBLE : View.GONE);
        Random random = new Random(now / 60000L + id);
        ArrayList<String> phrases = new ArrayList<>(Arrays.asList(
                context.getResources().getStringArray(R.array.widget_encouragements)));
        Collections.shuffle(phrases, random);
        int translucent = Color.argb(TEXT_ALPHA, Color.red(color), Color.green(color), Color.blue(color));
        for (int lane : new int[]{R.id.widget_bullets_left, R.id.widget_bullets_right}) {
            if (lane == R.id.widget_bullets_right && !placement.twoLanes) {
                continue;
            }
            int interval = MIN_INTERVAL_MS + random.nextInt(MAX_INTERVAL_MS - MIN_INTERVAL_MS + 1)
                    + (lane == R.id.widget_bullets_right ? SLOW_LANE_EXTRA_INTERVAL_MS : 0);
            // Start empty so the first phrase also waits for its lane's interval.
            views.addView(lane, new RemoteViews(context.getPackageName(), R.layout.widget_bullet));
            views.setInt(lane, "setFlipInterval", interval);
            Collections.shuffle(phrases, random);
            for (String phrase : phrases) {
                RemoteViews child = new RemoteViews(context.getPackageName(), R.layout.widget_bullet);
                child.setTextViewText(R.id.widget_bullet_text, phrase);
                child.setTextColor(R.id.widget_bullet_text, translucent);
                child.setViewPadding(R.id.widget_bullet_frame, 0,
                        Math.round(placement.randomTop(random) * metrics.density), 0, 0);
                views.addView(lane, child);
            }
            // Reapplied flippers keep running; consume their first-view flag on the blank.
            views.setDisplayedChild(lane, 0);
        }
    }
}
