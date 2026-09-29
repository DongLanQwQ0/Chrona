package com.donglan.chrona;

import android.content.Intent;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

/** Collection fallback for Android 8–11; Android 12+ uses direct collection items. */
public final class AgendaWidgetService extends RemoteViewsService {
    @Override public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int id = intent.getIntExtra(android.appwidget.AppWidgetManager.EXTRA_APPWIDGET_ID,
                android.appwidget.AppWidgetManager.INVALID_APPWIDGET_ID);
        return new RemoteViewsFactory() {
            private WidgetAgenda data;
            @Override public void onCreate() { }
            @Override public void onDataSetChanged() {
                data = WidgetAgenda.load(getApplicationContext());
                AgendaWidgetProvider.positionAfterLoad(getApplicationContext(), id, data);
            }
            @Override public void onDestroy() { data = null; }
            @Override public int getCount() { return data == null ? 0 : data.today.size(); }
            @Override public RemoteViews getViewAt(int position) {
                if (data == null || position < 0 || position >= data.today.size()) return null;
                return AgendaWidgetProvider.row(getApplicationContext(), data.today.get(position),
                        data.now, data.zone);
            }
            @Override public RemoteViews getLoadingView() { return null; }
            @Override public int getViewTypeCount() { return 1; }
            @Override public long getItemId(int position) { return position; }
            @Override public boolean hasStableIds() { return false; }
        };
    }
}
