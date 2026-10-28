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
import android.widget.RemoteViews;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Host-owned animations; countdown text shares the existing reminder update worker. */
final class AtomicAgendaCard {
    private static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    private static final int BACKGROUND_WIDTH = 320, BACKGROUND_HEIGHT = 180;
    private static final int ISLAND_HEADER_SAFE_BOTTOM_DP = 40;
    private static final int CAMERA_CLEARANCE_DP = 4;
    private static int cachedBase, cachedAccent;
    private static Bitmap[] cachedBackgrounds;
    private static Bitmap cachedWallpaper;
    private static final int[] TICKER_IDS = {R.id.atomic_ticker_1, R.id.atomic_ticker_2,
            R.id.atomic_ticker_3, R.id.atomic_ticker_4, R.id.atomic_ticker_5, R.id.atomic_ticker_6};

    static RemoteViews create(Context context, AgendaEntry entry, long now, long notifiedAt,
            String nextTitle, PendingIntent details) {
        return create(context, entry, now, notifiedAt, nextTitle, details, false);
    }
    static RemoteViews forIsland(Context context, AgendaEntry entry, long now, long notifiedAt,
            String nextTitle, PendingIntent details) {
        return create(context, entry, now, notifiedAt, nextTitle, details, true);
    }
    private static RemoteViews create(Context context, AgendaEntry entry, long now, long notifiedAt,
            String nextTitle, PendingIntent details, boolean cameraSafe) {
        UiStyle.Palette palette = UiStyle.colors(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.notification_atomic_card);
        boolean active = now >= entry.start && "event".equals(entry.category);
        long from = active ? entry.start : Math.min(entry.start, notifiedAt);
        long to = active ? entry.finish() : entry.start;
        double ratio = ReminderTimeline.elapsedRatio(entry, now, notifiedAt);
        ZoneId zone = ZoneId.systemDefault();
        views.setTextViewText(R.id.atomic_title, entry.title);
        views.setTextViewText(R.id.atomic_island_title, entry.title);
        views.setTextColor(R.id.atomic_island_title, palette.text);
        views.setViewVisibility(R.id.atomic_title, cameraSafe ? android.view.View.GONE : android.view.View.VISIBLE);
        views.setViewVisibility(R.id.atomic_island_title, cameraSafe ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setViewVisibility(R.id.atomic_camera_space, cameraSafe ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setViewVisibility(R.id.atomic_top_remaining, cameraSafe ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setViewVisibility(R.id.atomic_bottom_remaining, cameraSafe ? android.view.View.GONE : android.view.View.VISIBLE);
        views.setViewPadding(R.id.atomic_header, 0, cameraSafe ? cameraTopPadding(context) : 0, 0, 0);
        views.setTextViewText(R.id.atomic_location, locationText(entry.location));
        views.setImageViewBitmap(R.id.atomic_location_icon, locationIcon(context, palette.muted));
        views.setViewVisibility(R.id.atomic_location_icon, android.view.View.VISIBLE);
        views.setTextViewText(R.id.atomic_remaining_label, active ? "距结束" : entry.dueAtMillis!=null ? "距截止" : "距开始");
        views.setTextViewText(R.id.atomic_top_remaining_label, active ? "距结束" : entry.dueAtMillis!=null ? "距截止" : "距开始");
        views.setTextColor(R.id.atomic_top_remaining_label, palette.muted);
        views.setTextColor(R.id.atomic_top_countdown, palette.primary);
        applyRemaining(views, entry, now, to, active, AgendaReminderPreferences.showSeconds(context));
        views.setViewVisibility(R.id.atomic_top_remaining_label, now < to&&!entry.overdue(now) ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setViewVisibility(R.id.atomic_remaining_label, now < to&&!entry.overdue(now) ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setTextViewText(R.id.atomic_from, CLOCK.format(Instant.ofEpochMilli(from).atZone(zone)));
        views.setTextViewText(R.id.atomic_to, CLOCK.format(Instant.ofEpochMilli(to).atZone(zone)));
        views.setTextColor(R.id.atomic_title, palette.text);
        for (int id : new int[]{R.id.atomic_location, R.id.atomic_remaining_label, R.id.atomic_from, R.id.atomic_to})
            views.setTextColor(id, palette.muted);
        views.setTextColor(R.id.atomic_remaining, palette.primary);
        views.setImageViewBitmap(R.id.atomic_icon, icon(palette.primary));
        views.setImageViewBitmap(R.id.atomic_from_dot, dot(palette.primary));
        views.setImageViewBitmap(R.id.atomic_to_dot, dot(palette.primary));
        views.setImageViewBitmap(R.id.atomic_progress, progress(palette.primary, palette.surface, ratio));
        views.setContentDescription(R.id.atomic_progress, Math.round(ratio * 100) + "%");
        views.setInt(R.id.atomic_card, "setBackgroundColor", palette.surface);
        views.setOnClickPendingIntent(R.id.atomic_card, details);
        android.graphics.drawable.Drawable complete = context.getDrawable(R.drawable.ic_check).mutate();
        complete.setTint(palette.primary);
        Bitmap completeIcon = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        complete.setBounds(0, 0, 48, 48); complete.draw(new Canvas(completeIcon));
        views.setImageViewBitmap(R.id.atomic_complete, completeIcon);
        views.setContentDescription(R.id.atomic_complete,entry.isTask()?"homework".equals(entry.category)?"标记作业完成":"标记待办完成":"结束本次提醒");
        views.setOnClickPendingIntent(R.id.atomic_complete, AgendaReminder.action(context, entry, "complete", 4));

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
        applyBackground(context, views, R.id.atomic_background,
                R.id.atomic_background_first, R.id.atomic_background_second);
        return views;
    }
    static void applyRemaining(RemoteViews views, AgendaEntry entry, long now, long to,
            boolean active, boolean showSeconds) {
        boolean ended = entry.overdue(now) || now >= to;
        boolean seconds = showSeconds && !ended;
        String remaining = seconds ? expandedRemainingText(to - now) : remainingText(to - now);
        String text = ended ? entry.overdue(now) ? "已逾期" : active ? "已结束"
                : entry.isTask() ? "待完成" : "已开始" : remaining;
        float size = seconds || remaining.length() > 3 ? 14 : 17;
        for (int id : new int[]{R.id.atomic_remaining, R.id.atomic_top_countdown}) {
            views.setTextViewText(id, text);
            views.setTextViewTextSize(id, android.util.TypedValue.COMPLEX_UNIT_SP, size);
        }
    }
    static void applyBackground(Context context, RemoteViews views, int flipper, int first, int second) {
        UiStyle.Palette palette = UiStyle.colors(context);
        Bitmap wallpaper = AgendaReminderPreferences.atomicWallpaper(context)
                ? GlassBackdropView.blurredForNotification(context, BACKGROUND_WIDTH, BACKGROUND_HEIGHT) : null;
        Bitmap[] backgrounds = backgrounds(palette.surface, palette.primary, wallpaper);
        // Keep the same children during reapply: replacing animated children leaves ghosts.
        views.setImageViewBitmap(first, backgrounds[0]);
        views.setImageViewBitmap(second, backgrounds[1]);
        views.setInt(flipper, "setFlipInterval", ValueAnimator.areAnimatorsEnabled() ? 16000 : Integer.MAX_VALUE);
    }
    static String locationText(String location) {
        return location == null || location.isBlank() ? "在你手里~" : location.trim();
    }
    static String remainingText(long millis) {
        long minutes = Math.max(0, millis + 59_999L) / 60_000L;
        if (minutes >= 1440) return ((minutes + 1439) / 1440) + "天";
        return minutes + "分";
    }
    static String remainingText(long millis, boolean showSeconds) {
        if (showSeconds && millis > 0 && millis <= ReminderTimeline.CAPSULE_COUNTDOWN_WINDOW)
            return ((millis + 999) / 1000) + "秒";
        return remainingText(millis);
    }
    static String expandedRemainingText(long millis) {
        long positive = Math.max(0, millis);
        long seconds = positive / 1000 + (positive % 1000 > 0 ? 1 : 0);
        return seconds >= 3600
                ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
                : String.format(java.util.Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }
    static int cameraTopPadding(Context context) {
        if (android.os.Build.VERSION.SDK_INT < 30) return 0;
        try {
            android.view.WindowMetrics metrics = context.getSystemService(android.view.WindowManager.class).getCurrentWindowMetrics();
            android.view.DisplayCutout cutout = metrics.getWindowInsets().getDisplayCutout();
            if (cutout == null) return 0;
            float density = context.getResources().getDisplayMetrics().density;
            android.graphics.Rect rect = cutout.getBoundingRectTop();
            int bottom = rect.isEmpty() ? 0 : Math.max(0, rect.bottom - metrics.getBounds().top);
            // Content starts at 8dp; the minimum 28dp header plus 4dp body margin
            // reserve 40dp. The countdown may grow the row further at large fonts.
            return cameraHeaderPaddingPixels(bottom, density);
        } catch (RuntimeException failure) {
            android.util.Log.w("ChronaAtomic", "Unable to read camera cutout", failure);
            return 0;
        }
    }
    static int cameraHeaderPaddingPixels(int cutoutBottom, float density) {
        return Math.max(0, cutoutBottom + Math.round(CAMERA_CLEARANCE_DP * density)
                - Math.round(ISLAND_HEADER_SAFE_BOTTOM_DP * density));
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

    private static Bitmap progress(int color, int base, double ratio) {
        Bitmap image = Bitmap.createBitmap(320, 4, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(image);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        // An opaque track remains visible against both wallpaper and aurora, joining the end dot.
        paint.setColor(mix(base, color, .45f)); canvas.drawRect(0, 0, 320, 4, paint);
        paint.setColor(color); canvas.drawRect(0, 0, (float)(320 * ratio), 4, paint);
        return image;
    }

    private static Bitmap dot(int color) {
        Bitmap image = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG); paint.setColor(color);
        paint.setStyle(Paint.Style.FILL);
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
    private static Bitmap locationIcon(Context context, int color) {
        Bitmap image = Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888);
        android.graphics.drawable.Drawable icon = context.getDrawable(R.drawable.ic_atomic_location).mutate();
        icon.setTint(color); icon.setBounds(0, 0, 48, 48); icon.draw(new Canvas(image));
        return image;
    }

    private static synchronized Bitmap[] backgrounds(int base, int accent, Bitmap wallpaper) {
        if (cachedBackgrounds != null && cachedBase == base && cachedAccent == accent
                && cachedWallpaper == wallpaper) return cachedBackgrounds;
        Bitmap[] result = new Bitmap[2];
        for (int i = 0; i < 2; i++) {
            result[i] = Bitmap.createBitmap(BACKGROUND_WIDTH, BACKGROUND_HEIGHT, Bitmap.Config.ARGB_8888);
            Paint paint = new Paint();
            Canvas canvas = new Canvas(result[i]);
            if (wallpaper != null) {
                paint.setFilterBitmap(true);
                canvas.drawBitmap(wallpaper, 0, 0, paint);
                // Keep the photo readable as colour and texture without competing with text.
                int washAlpha = Color.luminance(base) > .5 ? 205 : 165;
                canvas.drawColor((base & 0xffffff) | (washAlpha << 24));
            }
            int tint = mix(base, accent, i == 0 ? .20f : .32f);
            int[] colors = wallpaper == null ? new int[]{tint, base, mix(base, accent, .08f)}
                    : new int[]{(accent & 0xffffff) | 0x24000000, Color.TRANSPARENT,
                            (accent & 0xffffff) | 0x14000000};
            paint.setShader(new LinearGradient(i == 0 ? 0 : BACKGROUND_WIDTH, 0,
                    i == 0 ? BACKGROUND_WIDTH : 0, BACKGROUND_HEIGHT,
                    colors, null, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, BACKGROUND_WIDTH, BACKGROUND_HEIGHT, paint);
        }
        cachedBase = base; cachedAccent = accent; cachedWallpaper = wallpaper; cachedBackgrounds = result;
        return result;
    }
    private static int mix(int a, int b, float weight) {
        return Color.rgb(Math.round(Color.red(a) * (1 - weight) + Color.red(b) * weight),
                Math.round(Color.green(a) * (1 - weight) + Color.green(b) * weight),
                Math.round(Color.blue(a) * (1 - weight) + Color.blue(b) * weight));
    }
    private AtomicAgendaCard() { }
}
