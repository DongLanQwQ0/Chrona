package com.donglan.chrona;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Uses native RemoteViews: no activity, model request or device wake lock needed to render. */
public class AgendaWidgetProvider extends AppWidgetProvider {
    static final String REFRESH = "com.donglan.chrona.WIDGET_REFRESH";
    private static final String MANUAL_REFRESH = "manual_refresh";
    private static final String TRANSITION = "com.donglan.chrona.WIDGET_TRANSITION";
    private static final String POSITION_PREFERENCES = "widget_positions";
    private static final ExecutorService UPDATES = Executors.newSingleThreadExecutor();
    private static final android.util.LruCache<String, Bitmap> ICONS = new android.util.LruCache<>(16);
    private static final android.util.LruCache<String, Bitmap> BACKGROUNDS = new android.util.LruCache<>(8);
    private static final android.os.Handler REFRESH_HANDLER =
            new android.os.Handler(android.os.Looper.getMainLooper());
    private static Runnable scheduledRefresh;

    public static synchronized void requestRefresh(Context context) {
        if (scheduledRefresh != null) REFRESH_HANDLER.removeCallbacks(scheduledRefresh);
        Context app = context.getApplicationContext();
        scheduledRefresh = () -> {
            synchronized (AgendaWidgetProvider.class) { scheduledRefresh = null; }
            broadcastRefresh(app);
        };
        REFRESH_HANDLER.postDelayed(scheduledRefresh, 300);
    }

    private static void broadcastRefresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        for (Class<?> type : new Class<?>[]{AgendaWidgetProvider.class, NextWidgetProvider.class}) {
            ComponentName component = new ComponentName(context, type);
            if (manager.getAppWidgetIds(component).length > 0)
                context.sendBroadcast(new Intent(REFRESH).setComponent(component));
        }
    }

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (REFRESH.equals(action) || TRANSITION.equals(action)
                || Intent.ACTION_BOOT_COMPLETED.equals(action)
                || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            PendingResult pending = goAsync();
            Context app = context.getApplicationContext();
            boolean next = this instanceof NextWidgetProvider;
            boolean manual = intent.getBooleanExtra(MANUAL_REFRESH, false);
            int requestedId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            UPDATES.execute(() -> {
                try {
                    AppWidgetManager manager = AppWidgetManager.getInstance(app);
                    int[] ids = manager.getAppWidgetIds(new ComponentName(app,
                            next ? NextWidgetProvider.class : AgendaWidgetProvider.class));
                    if (ids.length == 0) {
                        if (!next) scheduleTransition(app, 0);
                        return;
                    }
                    WidgetAgenda data = WidgetAgenda.load(app);
                    if (!next) scheduleTransition(app, data.nextTransition);
                    for (int id : ids) {
                        if (!manual || requestedId == id)
                            update(app, manager, id, next, data, manual);
                    }
                } finally { pending.finish(); }
            });
        } else super.onReceive(context, intent);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
            int id, Bundle options) { requestRefresh(context); }

    private static void scheduleTransition(Context context, long at) {
        android.app.AlarmManager alarms = context.getSystemService(android.app.AlarmManager.class);
        PendingIntent intent = PendingIntent.getBroadcast(context, 0,
                new Intent(TRANSITION).setComponent(new ComponentName(context, AgendaWidgetProvider.class)),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (at == 0) alarms.cancel(intent);
        // Inexact, non-wakeup alarm: no extra permission or background service.
        else alarms.set(android.app.AlarmManager.RTC, at, intent);
    }

    @Override public void onDisabled(Context context) {
        if (!(this instanceof NextWidgetProvider)) scheduleTransition(context, 0);
    }

    @Override public void onDeleted(Context context, int[] ids) {
        android.content.SharedPreferences.Editor editor = context.getSharedPreferences(
                POSITION_PREFERENCES, Context.MODE_PRIVATE).edit();
        for (int id : ids) editor.remove("day_" + id).remove("pending_" + id);
        editor.apply();
    }

    private static void update(Context context, AppWidgetManager manager, int id, boolean next,
            WidgetAgenda data, boolean manual) {
        Bundle options = manager.getAppWidgetOptions(id);
        android.content.SharedPreferences positions = context.getSharedPreferences(
                POSITION_PREFERENCES, Context.MODE_PRIVATE);
        String day = java.time.Instant.ofEpochMilli(data.now).atZone(data.zone).toLocalDate().toString()
                + (data.previewTomorrow ? ":preview" : ":today");
        boolean focus = !next && !data.failed && !data.today.isEmpty()
                && (manual || !day.equals(positions.getString("day_" + id, "")));
        if (Build.VERSION.SDK_INT >= 31) {
            java.util.ArrayList<android.util.SizeF> sizes = options.getParcelableArrayList(
                    AppWidgetManager.OPTION_APPWIDGET_SIZES);
            if (sizes != null && !sizes.isEmpty() && sizes.size() <= 16) {
                java.util.Map<android.util.SizeF, RemoteViews> variants = new java.util.LinkedHashMap<>();
                boolean hasList = false;
                for (android.util.SizeF size : sizes)
                    hasList |= !next && !new WidgetSize(Math.round(size.getWidth()),
                            Math.round(size.getHeight())).compact();
                if (focus && hasList) positions.edit().putBoolean("pending_" + id, true).apply();
                for (android.util.SizeF size : sizes) variants.put(size, render(context, id, next,
                        data, new WidgetSize(Math.round(size.getWidth()), Math.round(size.getHeight())),
                        sizes.size()));
                manager.updateAppWidget(id, new RemoteViews(variants));
                if (focus && hasList) {
                    positions.edit().putString("day_" + id, day).apply();
                    positionAfterLoad(context, id, data);
                }
                return;
            }
        }
        WidgetSize size = new WidgetSize(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180));
        boolean hasList = !next && !size.compact();
        if (focus && hasList) positions.edit().putBoolean("pending_" + id, true).apply();
        manager.updateAppWidget(id, render(context, id, next, data, size, 1));
        if (focus && hasList) positions.edit().putString("day_" + id, day).apply();
        if (!next && !size.compact() && Build.VERSION.SDK_INT < 31)
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list);
        else if (focus && hasList) positionAfterLoad(context, id, data);
    }

    /** Separate from adapter binding: legacy service calls this after its data has loaded. */
    static void positionAfterLoad(Context context, int id, WidgetAgenda data) {
        android.content.SharedPreferences positions = context.getSharedPreferences(
                POSITION_PREFERENCES, Context.MODE_PRIVATE);
        if (data.failed || data.today.isEmpty() || !positions.getBoolean("pending_" + id, false)) return;
        positions.edit().remove("pending_" + id).apply();
        Context app = context.getApplicationContext();
        REFRESH_HANDLER.postDelayed(() -> UPDATES.execute(() -> {
            AppWidgetManager manager = AppWidgetManager.getInstance(app);
            if (manager.getAppWidgetInfo(id) == null) return;
            WidgetAgenda fresh = WidgetAgenda.load(app);
            if (fresh.failed || fresh.today.isEmpty()) return;
            int target = fresh.todayStartPosition();
            Bundle options = manager.getAppWidgetOptions(id);
            if (Build.VERSION.SDK_INT >= 31) {
                java.util.ArrayList<android.util.SizeF> sizes = options.getParcelableArrayList(
                        AppWidgetManager.OPTION_APPWIDGET_SIZES);
                if (sizes != null && !sizes.isEmpty() && sizes.size() <= 16) {
                    java.util.Map<android.util.SizeF, RemoteViews> variants = new java.util.LinkedHashMap<>();
                    for (android.util.SizeF size : sizes) {
                        WidgetSize dimensions = new WidgetSize(Math.round(size.getWidth()),
                                Math.round(size.getHeight()));
                        RemoteViews view = render(app, id, false, fresh, dimensions, sizes.size());
                        if (!dimensions.compact()) view.setScrollPosition(R.id.widget_list, target);
                        variants.put(size, view);
                    }
                    // Partial updates do not merge actions into size-specific child RemoteViews.
                    manager.updateAppWidget(id, new RemoteViews(variants));
                    return;
                }
            }
            WidgetSize dimensions = new WidgetSize(options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180), options.getInt(
                    AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 180));
            if (dimensions.compact()) return;
            RemoteViews scroll = new RemoteViews(app.getPackageName(), R.layout.widget_today);
            // Supported by RemoteViews; the launcher decides the exact visible alignment.
            scroll.setScrollPosition(R.id.widget_list, target);
            manager.partiallyUpdateAppWidget(id, scroll);
        }), 350);
    }

    private static RemoteViews render(Context context, int id, boolean next, WidgetAgenda data,
            WidgetSize size, int variantCount) {
        boolean single = next || size.compact();
        RemoteViews views = new RemoteViews(context.getPackageName(),
                size.compact() ? R.layout.widget_compact
                        : next ? R.layout.widget_next : R.layout.widget_today);
        UiStyle.Palette palette = UiStyle.colors(context);
        WidgetDanmaku.bind(context, views, size, palette.text, id, data.now);
        views.setInt(R.id.widget_background, "setBackgroundColor", android.graphics.Color.TRANSPARENT);
        views.setImageViewBitmap(R.id.widget_background,
                background(context, palette.surface, size, variantCount));
        int pad = Math.round(size.padding() * context.getResources().getDisplayMetrics().density);
        views.setViewPadding(R.id.widget_body, pad, pad, pad, pad);
        views.setViewVisibility(R.id.widget_header, size.header() ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setViewVisibility(R.id.widget_heading, size.heading() ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setViewVisibility(R.id.widget_refresh, size.refresh() ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setTextColor(R.id.widget_heading, palette.text);
        views.setTextColor(R.id.widget_empty, palette.muted);
        views.setTextColor(R.id.widget_notice, palette.muted);
        views.setTextViewText(R.id.widget_notice, data.calendarUnavailable && data.courseUnavailable
                ? "系统日历与课表未能读取 · 点击查看"
                : data.courseUnavailable ? "课表未能读取 · 点击查看"
                : "系统日历未能读取 · 点击此处检查权限");
        views.setViewVisibility(R.id.widget_notice, (data.calendarUnavailable || data.courseUnavailable) && size.height >= 160
                ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setTextViewText(R.id.widget_heading, size.width < 220
                ? next ? "下一件" : data.previewTomorrow ? "现在与明天" : "今日"
                : next ? "下一件事" : data.previewTomorrow ? "现在与明天" : "今天的安排");
        views.setImageViewBitmap(R.id.widget_add, icon(context, R.drawable.ic_add, palette.primary));
        views.setImageViewBitmap(R.id.widget_refresh,
                icon(context, R.drawable.ic_refresh, palette.primary));
        views.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context, id,
                new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE));
        Class<?> provider = next ? NextWidgetProvider.class : AgendaWidgetProvider.class;
        views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, id,
                new Intent(REFRESH).setComponent(new ComponentName(context, provider))
                        .putExtra(MANUAL_REFRESH, true)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        views.setOnClickPendingIntent(R.id.widget_heading, PendingIntent.getActivity(context, id,
                new Intent(context, DashboardActivity.class), PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE));
        PendingIntent openHome = PendingIntent.getActivity(context, id,
                new Intent(context, DashboardActivity.class), PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_notice, openHome);
        views.setOnClickPendingIntent(R.id.widget_empty, openHome);
        String empty = data.failed ? "读取失败，点击刷新重试"
                : data.calendarUnavailable ? "系统日历未能读取，点击检查权限"
                : data.courseUnavailable ? "课表未能读取，点击查看"
                : next ? "未来一个月暂无安排"
                : data.previewTomorrow ? "现在与明天暂无安排" : "今天暂无安排";
        if (single) {
            WidgetAgenda.Item item = next ? data.next : data.today.isEmpty() ? null
                    : data.today.get(data.todayStartPosition());
            boolean hasItem = item != null && !data.failed;
            views.setViewVisibility(R.id.widget_content, hasItem ? android.view.View.VISIBLE
                    : android.view.View.GONE);
            views.setViewVisibility(R.id.widget_empty, hasItem ? android.view.View.GONE
                    : android.view.View.VISIBLE);
            views.setTextViewText(R.id.widget_empty, empty);
            if (hasItem) {
                fill(context, views, item, data.now, data.zone);
                if (size.compact()) {
                    views.setTextViewText(R.id.widget_time, compactTime(item, data));
                    views.setViewVisibility(R.id.widget_location_row, android.view.View.GONE);
                    views.setViewVisibility(R.id.widget_meta, android.view.View.GONE);
                    views.setViewVisibility(R.id.widget_time_icon, size.width < 100
                            ? android.view.View.GONE : android.view.View.VISIBLE);
                    views.setTextViewTextSize(R.id.widget_title, android.util.TypedValue.COMPLEX_UNIT_SP,
                            size.width < 100 || size.height < 70 ? 12 : 15);
                    views.setTextViewTextSize(R.id.widget_time, android.util.TypedValue.COMPLEX_UNIT_SP,
                            size.width < 100 || size.height < 70 ? 10 : 12);
                    views.setInt(R.id.widget_title, "setMaxLines", size.height < 90 ? 1 : 2);
                    if (size.height >= 180 && !item.location.isEmpty())
                        views.setViewVisibility(R.id.widget_location_row, android.view.View.VISIBLE);
                }
                views.setOnClickPendingIntent(R.id.widget_content, PendingIntent.getActivity(
                        context, id + 100000, launchExtras(item).setClass(context, WidgetLaunchActivity.class),
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
            }
        } else {
            views.setTextViewText(R.id.widget_empty, empty);
            views.setEmptyView(R.id.widget_list, R.id.widget_empty);
            int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
            views.setPendingIntentTemplate(R.id.widget_list, PendingIntent.getActivity(context, id,
                    new Intent(context, WidgetLaunchActivity.class),
                    PendingIntent.FLAG_UPDATE_CURRENT | mutable));
            if (Build.VERSION.SDK_INT >= 31) {
                RemoteViews.RemoteCollectionItems.Builder items =
                        new RemoteViews.RemoteCollectionItems.Builder().setHasStableIds(false)
                                .setViewTypeCount(1);
                for (int i = 0; i < data.today.size(); i++) items.addItem(i,
                        row(context, data.today.get(i), data.now, data.zone));
                views.setRemoteAdapter(R.id.widget_list, items.build());
            } else {
                Intent service = new Intent(context, AgendaWidgetService.class)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id);
                service.setData(android.net.Uri.parse("chrona-widget://today/" + id));
                views.setRemoteAdapter(R.id.widget_list, service);
            }
        }
        return views;
    }

    private static String compactTime(WidgetAgenda.Item item, WidgetAgenda data) {
        java.time.ZonedDateTime start = java.time.Instant.ofEpochMilli(item.start).atZone(data.zone);
        java.time.LocalDate today = java.time.Instant.ofEpochMilli(data.now).atZone(data.zone).toLocalDate();
        if (item.allDay) return start.toLocalDate().equals(today) ? "全天"
                : java.time.format.DateTimeFormatter.ofPattern("M/d ").format(start) + "全天";
        return java.time.format.DateTimeFormatter.ofPattern(start.toLocalDate().equals(today)
                ? "HH:mm" : "M/d HH:mm").format(start);
    }

    static RemoteViews row(Context context, WidgetAgenda.Item item, long now,
            java.time.ZoneId zone) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_item);
        fill(context, row, item, now, zone);
        row.setOnClickFillInIntent(R.id.widget_content, launchExtras(item));
        return row;
    }

    private static Intent launchExtras(WidgetAgenda.Item item) {
        return new Intent()
                .putExtra("task_id", item.taskId).putExtra("event_id", item.system ? item.id : 0)
                .putExtra(TaskDetailActivity.EXTRA_CANDIDATE_ID, item.system ? 0 : item.id)
                .putExtra("begin", item.start).putExtra("end", item.end)
                .putExtra(CourseAgenda.EXTRA_TERM, item.course == null ? null : item.course.termId)
                .putExtra(CourseAgenda.EXTRA_OCCURRENCE, item.course == null ? null : item.course.key);
    }

    private static void fill(Context context, RemoteViews views, WidgetAgenda.Item item, long now,
            java.time.ZoneId zone) {
        UiStyle.Palette palette = UiStyle.colors(context);
        views.setTextViewText(R.id.widget_title, item.title);
        views.setTextViewText(R.id.widget_time, item.time(now, zone));
        views.setTextViewText(R.id.widget_location, item.location);
        views.setViewVisibility(R.id.widget_location_row, item.location.isEmpty()
                ? android.view.View.GONE : android.view.View.VISIBLE);
        String state = item.start <= now && item.end > now ? " · 进行中" : "";
        views.setTextViewText(R.id.widget_meta, item.category + " · " + item.source + state);
        for (int text : new int[]{R.id.widget_title, R.id.widget_time})
            views.setTextColor(text, palette.text);
        for (int text : new int[]{R.id.widget_location, R.id.widget_meta})
            views.setTextColor(text, palette.muted);
        views.setImageViewBitmap(R.id.widget_time_icon,
                icon(context, R.drawable.ic_time_start, palette.primary));
        views.setImageViewBitmap(R.id.widget_location_icon,
                icon(context, R.drawable.ic_place, palette.muted));
    }

    private static synchronized Bitmap icon(Context context, int resource, int color) {
        int size = Math.round(24 * context.getResources().getDisplayMetrics().density);
        String key = resource + ":" + color + ":" + size;
        Bitmap cached = ICONS.get(key);
        if (cached != null) return cached;
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Drawable drawable = context.getDrawable(resource).mutate();
        drawable.setTint(color);
        drawable.setBounds(0, 0, size, size);
        drawable.draw(new Canvas(bitmap));
        ICONS.put(key, bitmap);
        return bitmap;
    }

    private static Bitmap background(Context context, int color, WidgetSize size, int variantCount) {
        android.util.DisplayMetrics metrics = context.getResources().getDisplayMetrics();
        // The layouts have 4dp outer padding on each side. Render at physical-pixel density,
        // sharing a bounded parcel budget across the launcher's responsive variants.
        float physicalWidth = Math.max(1, size.width - 8) * metrics.density;
        float physicalHeight = Math.max(1, size.height - 8) * metrics.density;
        float pixelBudget = Math.min(768f * 768f,
                .9f * metrics.widthPixels * metrics.heightPixels / Math.max(1, variantCount));
        float scale = Math.min(1f, Math.min(1280f / Math.max(physicalWidth, physicalHeight),
                (float) Math.sqrt(pixelBudget / (physicalWidth * physicalHeight))));
        int width = Math.max(1, Math.round(physicalWidth * scale));
        int height = Math.max(1, Math.round(physicalHeight * scale));
        String key = ThemeStore.background(context) + ":" + ThemeStore.revision(context)
                + ":" + width + ":" + height + ":" + color + ":" + size.width + ":" + size.height
                + ":" + metrics.widthPixels + ":" + metrics.heightPixels + ":" + metrics.density;
        Bitmap cached = BACKGROUNDS.get(key);
        if (cached != null) return cached;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        Bitmap wallpaper = GlassBackdropView.blurredForWidget(context);
        if (wallpaper != null) {
            paint.setAlpha(150);
            // Crop a window from the full-screen wallpaper. Its scale never depends on the
            // widget's aspect ratio; resizing changes only the visible area around the centre.
            float screenWidth = metrics.widthPixels * scale;
            float screenHeight = metrics.heightPixels * scale;
            canvas.drawBitmap(wallpaper, null, new RectF((width - screenWidth) / 2f,
                    (height - screenHeight) / 2f, (width + screenWidth) / 2f,
                    (height + screenHeight) / 2f), paint);
        }
        paint.setColor(color);
        paint.setAlpha(wallpaper == null ? 190 : Math.round(ThemeStore.surfaceMix(context) * 2.55f));
        canvas.drawRect(0, 0, width, height, paint);
        Bitmap rounded = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        edge.setShader(new android.graphics.BitmapShader(bitmap,
                android.graphics.Shader.TileMode.CLAMP, android.graphics.Shader.TileMode.CLAMP));
        float radius = Math.min(22f * metrics.density * scale, Math.min(width, height) / 2f);
        new Canvas(rounded).drawRoundRect(new RectF(0, 0, width, height), radius, radius, edge);
        bitmap.recycle();
        BACKGROUNDS.put(key, rounded);
        return rounded;
    }
}
