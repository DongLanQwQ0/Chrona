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
    @Override public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id, android.os.Bundle options) {
        update(context, manager, new int[]{id});
    }
    static void requestRefresh(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        update(context, manager, manager.getAppWidgetIds(new ComponentName(context, WeekCalendarWidgetProvider.class)));
    }
    private static void update(Context context, AppWidgetManager manager, int[] ids) {
        if (ids.length == 0) return;
        for (int id : ids) {
            if (android.os.Build.VERSION.SDK_INT >= 31) {
                java.util.Map<android.util.SizeF, RemoteViews> variants = new java.util.LinkedHashMap<>();
                for (int width : new int[]{64, 140}) for (int height : new int[]{64, 140})
                    variants.put(new android.util.SizeF(width, height), render(context, width, height));
                manager.updateAppWidget(id, new RemoteViews(variants));
            } else {
                android.os.Bundle options = manager.getAppWidgetOptions(id);
                manager.updateAppWidget(id, render(context,
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 64),
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 64)));
            }
        }
    }
    private static RemoteViews render(Context context, int widthDp, int heightDp) {
        widthDp = Math.max(40, Math.min(280, widthDp)); heightDp = Math.max(40, Math.min(280, heightDp));
        boolean narrow = widthDp < 100, tall = heightDp >= 100;
        UiStyle.Palette palette = UiStyle.colors(context);
        RemoteViews views = new RemoteViews(context.getPackageName(), narrow || tall
                ? R.layout.widget_week_calendar_vertical : R.layout.widget_week_calendar);
        views.setViewVisibility(R.id.week_widget_title, narrow && !tall ? android.view.View.GONE : android.view.View.VISIBLE);
        float density = context.getResources().getDisplayMetrics().density;
        int padding = Math.round(Math.max(0, Math.min(8, (Math.min(widthDp, heightDp) - 40) / 2)) * density);
        views.setViewPadding(R.id.week_widget_body, padding, padding, padding, padding);
        // Four responsive variants share one RemoteViews parcel; flat surfaces need no full-screen density.
        float backgroundScale = Math.min(density, 1.5f);
        int width = Math.round(widthDp * backgroundScale), height = Math.round(heightDp * backgroundScale);
        android.graphics.Bitmap background = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        paint.setColor(palette.surface);
        new android.graphics.Canvas(background).drawRoundRect(0, 0, width, height, 22 * backgroundScale, 22 * backgroundScale, paint);
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
        return views;
    }
}
