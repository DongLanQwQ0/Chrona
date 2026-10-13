package com.donglan.chrona;

import android.animation.ValueAnimator;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.os.SystemClock;
import android.widget.RemoteViews;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Host-owned animations: the app still wakes only at reminder boundaries/minute updates. */
final class AtomicAgendaCard {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final int BACKGROUND_WIDTH = 320, BACKGROUND_HEIGHT = 180;
    private static int cachedBase, cachedAccent;
    private static Bitmap[] cachedBackgrounds;
    private static final int[] TICKER_IDS = {R.id.atomic_ticker_1, R.id.atomic_ticker_2,
            R.id.atomic_ticker_3, R.id.atomic_ticker_4, R.id.atomic_ticker_5, R.id.atomic_ticker_6};

    static RemoteViews create(Context context, AgendaEntry entry, long now, long notifiedAt,
            String nextTitle, PendingIntent details) {
        UiStyle.Palette palette = UiStyle.colors(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.notification_atomic_card);
        boolean active = now >= entry.start && "event".equals(entry.category);
        long from = active ? entry.start : Math.min(entry.start, notifiedAt);
        long to = active ? entry.finish() : entry.start;
        double ratio = ReminderTimeline.elapsedRatio(entry, now, notifiedAt);
        ZoneId zone = ZoneId.systemDefault();
        views.setTextViewText(R.id.atomic_title, entry.title);
        views.setTextViewText(R.id.atomic_location, entry.location);
        views.setTextViewText(R.id.atomic_remaining_label, active ? "距结束" : "距开始");
        views.setChronometer(R.id.atomic_remaining, SystemClock.elapsedRealtime() + to - now, null, now < to);
        views.setChronometerCountDown(R.id.atomic_remaining, true);
        views.setViewVisibility(R.id.atomic_remaining_label, now < to ? android.view.View.VISIBLE : android.view.View.GONE);
        if (now >= to) {
            views.setTextViewText(R.id.atomic_remaining, active ? "已结束" : "已开始");
        }
        views.setTextViewText(R.id.atomic_from, CLOCK.format(Instant.ofEpochMilli(from).atZone(zone)));
        views.setTextViewText(R.id.atomic_to, CLOCK.format(Instant.ofEpochMilli(to).atZone(zone)));
        views.setTextColor(R.id.atomic_title, palette.text);
        for (int id : new int[]{R.id.atomic_location, R.id.atomic_remaining_label, R.id.atomic_from, R.id.atomic_to})
            views.setTextColor(id, palette.muted);
        views.setTextColor(R.id.atomic_remaining, palette.primary);
        views.setImageViewBitmap(R.id.atomic_icon, icon(palette.primary));
        views.setImageViewBitmap(R.id.atomic_from_dot, dot(palette.primary, true));
        views.setImageViewBitmap(R.id.atomic_to_dot, dot(palette.primary, ratio >= 1));
        views.setImageViewBitmap(R.id.atomic_progress, progress(palette.primary, ratio));
        views.setContentDescription(R.id.atomic_progress, Math.round(ratio * 100) + "%");
        views.setInt(R.id.atomic_card, "setBackgroundColor", palette.surface);
        views.setOnClickPendingIntent(R.id.atomic_card, details);

        boolean motion = ValueAnimator.areAnimatorsEnabled();
        String[] phrases = context.getResources().getStringArray(R.array.widget_encouragements);
        int seed = Math.floorMod(entry.key().hashCode(), Math.max(1, phrases.length));
        Paint textMeasure = new Paint();
        textMeasure.setTextSize(11 * context.getResources().getDisplayMetrics().scaledDensity);
        float longestDp = 0;
        // A quiet interval between encouragements shows the next real occurrence.
        for (int i = 0; i < TICKER_IDS.length; i++) {
            String text = i % 2 == 1 && nextTitle != null && !nextTitle.isBlank() ? "→ " + nextTitle
                    : phrases.length > 0 ? phrases[(seed + i / 2) % phrases.length] : "";
            views.setTextViewText(TICKER_IDS[i], text);
            views.setTextColor(TICKER_IDS[i], palette.muted);
            longestDp = Math.max(longestDp, textMeasure.measureText(text)
                    / context.getResources().getDisplayMetrics().density);
        }
        int tickerInterval = (int)Math.min(Integer.MAX_VALUE, Math.max(16000, (longestDp / 30 + 6) * 1000));
        views.setInt(R.id.atomic_ticker, "setFlipInterval", motion ? tickerInterval : Integer.MAX_VALUE);
        Bitmap[] backgrounds = backgrounds(palette.surface, palette.primary);
        // Keep the same children during reapply: replacing animated children leaves ghosts.
        views.setImageViewBitmap(R.id.atomic_background_first, backgrounds[0]);
        views.setImageViewBitmap(R.id.atomic_background_second, backgrounds[1]);
        views.setInt(R.id.atomic_background, "setFlipInterval", motion ? 16000 : Integer.MAX_VALUE);
        return views;
    }

    static String marqueeConfiguration() {
        if (!ValueAnimator.areAnimatorsEnabled()) return "{\"animations\":[]}";
        StringBuilder json = new StringBuilder("{\"animations\":[");
        boolean first = true;
        for (int id : TICKER_IDS) for (int location : new int[]{1, 2}) {
            if (!first) json.append(',');
            first = false;
            json.append("{\"elementId\":").append(id).append(",\"displayLocation\":").append(location)
                    .append(",\"animationType\":1,\"animationProperties\":[")
                    .append("{\"propertyKey\":\"widget_marquee_repeat_count\",\"propertyType\":1,\"propertyValue\":-1},")
                    .append("{\"propertyKey\":\"widget_marquee_during_time\",\"propertyType\":2,\"propertyValue\":0},")
                    .append("{\"propertyKey\":\"widget_marquee_ellipsize_type\",\"propertyType\":1,\"propertyValue\":0}]}");
        }
        return json.append("]}").toString();
    }

    private static Bitmap progress(int color, double ratio) {
        Bitmap image = Bitmap.createBitmap(320, 4, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor((color & 0xffffff) | 0x33000000); canvas.drawRect(0, 0, 320, 4, paint);
        paint.setColor(color); canvas.drawRect(0, 0, (float)(320 * ratio), 4, paint);
        return image;
    }

    private static Bitmap dot(int color, boolean filled) {
        Bitmap image = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(color);
        paint.setStyle(filled ? Paint.Style.FILL : Paint.Style.STROKE); paint.setStrokeWidth(3);
        new Canvas(image).drawCircle(12, 12, 9, paint);
        return image;
    }

    private static Bitmap icon(int color) {
        Bitmap image = Bitmap.createBitmap(72, 72, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(color);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(5); paint.setStrokeCap(Paint.Cap.ROUND);
        canvas.drawCircle(36, 36, 27, paint);
        canvas.drawLine(36, 21, 36, 36, paint); canvas.drawLine(36, 36, 45, 42, paint);
        return image;
    }

    private static synchronized Bitmap[] backgrounds(int base, int accent) {
        if (cachedBackgrounds != null && cachedBase == base && cachedAccent == accent) return cachedBackgrounds;
        Bitmap[] result = new Bitmap[2];
        for (int i = 0; i < 2; i++) {
            result[i] = Bitmap.createBitmap(BACKGROUND_WIDTH, BACKGROUND_HEIGHT, Bitmap.Config.ARGB_8888);
            Paint paint = new Paint();
            int tint = mix(base, accent, i == 0 ? .13f : .23f);
            paint.setShader(new LinearGradient(i == 0 ? 0 : BACKGROUND_WIDTH, 0,
                    i == 0 ? BACKGROUND_WIDTH : 0, BACKGROUND_HEIGHT,
                    new int[]{tint, base, mix(base, accent, .06f)}, null, Shader.TileMode.CLAMP));
            new Canvas(result[i]).drawRect(0, 0, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, paint);
        }
        cachedBase = base; cachedAccent = accent; cachedBackgrounds = result;
        return result;
    }
    private static int mix(int a, int b, float weight) {
        return Color.rgb(Math.round(Color.red(a) * (1 - weight) + Color.red(b) * weight),
                Math.round(Color.green(a) * (1 - weight) + Color.green(b) * weight),
                Math.round(Color.blue(a) * (1 - weight) + Color.blue(b) * weight));
    }
    private AtomicAgendaCard() { }
}
