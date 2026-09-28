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
    private static final ExecutorService UPDATES = Executors.newSingleThreadExecutor();
    private static final android.util.LruCache<String, Bitmap> ICONS = new android.util.LruCache<>(16);
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
        if (REFRESH.equals(action) || AppWidgetManager.ACTION_APPWIDGET_UPDATE.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
            PendingResult pending = goAsync();
            Context app = context.getApplicationContext();
            boolean next = this instanceof NextWidgetProvider;
            UPDATES.execute(() -> {
                try {
                    AppWidgetManager manager = AppWidgetManager.getInstance(app);
                    int[] ids = manager.getAppWidgetIds(new ComponentName(app,
                            next ? NextWidgetProvider.class : AgendaWidgetProvider.class));
                    if (ids.length == 0) return;
                    WidgetAgenda data = WidgetAgenda.load(app);
                    for (int id : ids) update(app, manager, id, next, data);
                } finally { pending.finish(); }
            });
        } else super.onReceive(context, intent);
    }

    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager,
            int id, Bundle options) { requestRefresh(context); }

    private static void update(Context context, AppWidgetManager manager, int id, boolean next,
            WidgetAgenda data) {
        RemoteViews views = new RemoteViews(context.getPackageName(),
                next ? R.layout.widget_next : R.layout.widget_today);
        UiStyle.Palette palette = UiStyle.colors(context);
        views.setImageViewBitmap(R.id.widget_background, background(palette.surface));
        views.setTextColor(R.id.widget_heading, palette.text);
        views.setTextColor(R.id.widget_empty, palette.muted);
        views.setTextColor(R.id.widget_notice, palette.muted);
        views.setTextViewText(R.id.widget_notice, "系统日历未能读取 · 点击标题检查权限");
        views.setViewVisibility(R.id.widget_notice, data.calendarUnavailable
                ? android.view.View.VISIBLE : android.view.View.GONE);
        views.setTextViewText(R.id.widget_heading, next ? "下一件事" : "今天的安排");
        views.setImageViewBitmap(R.id.widget_add, icon(context, R.drawable.ic_add, palette.primary));
        views.setImageViewBitmap(R.id.widget_refresh,
                icon(context, R.drawable.ic_refresh, palette.primary));
        views.setOnClickPendingIntent(R.id.widget_add, PendingIntent.getActivity(context, id,
                new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE));
        Class<?> provider = next ? NextWidgetProvider.class : AgendaWidgetProvider.class;
        views.setOnClickPendingIntent(R.id.widget_refresh, PendingIntent.getBroadcast(context, id,
                new Intent(REFRESH).setComponent(new ComponentName(context, provider)),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
        views.setOnClickPendingIntent(R.id.widget_heading, PendingIntent.getActivity(context, id,
                new Intent(context, DashboardActivity.class), PendingIntent.FLAG_UPDATE_CURRENT
                        | PendingIntent.FLAG_IMMUTABLE));
        String empty = data.failed ? "读取失败，点击刷新重试"
                : data.calendarUnavailable ? "系统日历未能读取，点击标题检查权限"
                : next ? "未来一个月暂无安排" : "今天暂无安排";
        if (next) {
            boolean hasItem = data.next != null && !data.failed;
            views.setViewVisibility(R.id.widget_content, hasItem ? android.view.View.VISIBLE
                    : android.view.View.GONE);
            views.setViewVisibility(R.id.widget_empty, hasItem ? android.view.View.GONE
                    : android.view.View.VISIBLE);
            views.setTextViewText(R.id.widget_empty, empty);
            if (hasItem) {
                fill(context, views, data.next, data.now, data.zone);
                views.setOnClickPendingIntent(R.id.widget_content, PendingIntent.getActivity(
                        context, id + 100000, new Intent(context, WidgetLaunchActivity.class)
                                .putExtra("task_id", data.next.taskId)
                                .putExtra("event_id", data.next.system ? data.next.id : 0)
                                .putExtra("begin", data.next.start).putExtra("end", data.next.end),
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
        manager.updateAppWidget(id, views);
        if (!next && Build.VERSION.SDK_INT < 31)
            manager.notifyAppWidgetViewDataChanged(id, R.id.widget_list);
    }

    static RemoteViews row(Context context, WidgetAgenda.Item item, long now,
            java.time.ZoneId zone) {
        RemoteViews row = new RemoteViews(context.getPackageName(), R.layout.widget_item);
        fill(context, row, item, now, zone);
        row.setOnClickFillInIntent(R.id.widget_content, new Intent()
                .putExtra("task_id", item.taskId).putExtra("event_id", item.system ? item.id : 0)
                .putExtra("begin", item.start).putExtra("end", item.end));
        return row;
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

    private static Bitmap background(int color) {
        Bitmap bitmap = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(color);
        new Canvas(bitmap).drawRoundRect(new RectF(0, 0, 240, 240), 20, 20, paint);
        return bitmap;
    }
}
