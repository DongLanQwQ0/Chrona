package com.donglan.chrona;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.widget.RemoteViews;

/** A lightweight launcher shortcut; it does not query calendars on the desktop. */
public final class WeekCalendarWidgetProvider extends AppWidgetProvider {
    @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        update(context, manager, ids);
    }
    static void requestRefresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        update(context, manager, manager.getAppWidgetIds(new ComponentName(context, WeekCalendarWidgetProvider.class)));
    }
    private static void update(Context context, AppWidgetManager manager, int[] ids) {
        if (ids.length == 0) return;
        UiStyle.Palette palette = UiStyle.colors(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_week_calendar);
        float density = context.getResources().getDisplayMetrics().density;
        int width = Math.round(192 * density), height = Math.round(64 * density);
        android.graphics.Bitmap background = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(palette.surface);
        new android.graphics.Canvas(background).drawRoundRect(0, 0, width, height, 22 * density, 22 * density, paint);
        views.setImageViewBitmap(R.id.week_widget_background, background);
        views.setTextColor(R.id.week_widget_title, palette.primary);
        int iconSize = Math.round(28 * density);
        android.graphics.Bitmap icon = android.graphics.Bitmap.createBitmap(iconSize, iconSize, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.drawable.Drawable drawable = context.getDrawable(R.drawable.ic_week_calendar).mutate();
        drawable.setTint(palette.primary); drawable.setBounds(0, 0, iconSize, iconSize);
        drawable.draw(new android.graphics.Canvas(icon));
        views.setImageViewBitmap(R.id.week_widget_icon, icon);
        PendingIntent open = PendingIntent.getActivity(context, 0,
                new Intent(context, CalendarOverviewActivity.class).putExtra("open_week", true)
                        .setData(android.net.Uri.parse("chrona://calendar/week")),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.week_widget_root, open);
        manager.updateAppWidget(ids, views);
    }
}
