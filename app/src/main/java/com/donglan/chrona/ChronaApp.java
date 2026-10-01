package com.donglan.chrona;

import android.app.Activity;
import android.app.Application;
import android.os.Bundle;

/**
 * Keeps every live screen in step with the appearance preference. Palettes are baked into view
 * trees when a screen is built, so a screen that stays alive in the back stack would otherwise keep
 * showing the colours it was created with.
 */
public final class ChronaApp extends Application {
    private android.database.ContentObserver calendarObserver;

    private void observeCalendar() {
        if (calendarObserver != null || checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        android.database.ContentObserver observer = new android.database.ContentObserver(
                new android.os.Handler(android.os.Looper.getMainLooper())) {
            @Override public void onChange(boolean selfChange) {
                CalendarLinkReconciler.request(ChronaApp.this);
                AgendaWidgetProvider.requestRefresh(ChronaApp.this);
            }
        };
        try {
            getContentResolver().registerContentObserver(android.provider.CalendarContract.CONTENT_URI,
                    true, observer);
            calendarObserver = observer;
        } catch (SecurityException ignored) {
            // The launcher refresh button and periodic refresh remain available.
        }
    }

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                ThemeStore.watch(activity);
                UiMotion.install(activity);
            }

            @Override public void onActivityDestroyed(Activity activity) {
                ThemeStore.forget(activity);
            }

            @Override public void onActivityStarted(Activity activity) { }

            @Override public void onActivityResumed(Activity activity) {
                observeCalendar();
                if (activity instanceof DashboardActivity || activity instanceof TaskDetailActivity)
                    CalendarLinkReconciler.request(activity);
                if (activity instanceof DashboardActivity) AgendaWidgetProvider.requestRefresh(activity);
                // Covers the change that could not reach a stopped screen, and a system light/dark
                // switch made while the screen sat in the back stack.
                if (ThemeStore.outdated(activity)) activity.recreate();
            }

            @Override public void onActivityPaused(Activity activity) { }

            @Override public void onActivityStopped(Activity activity) { }

            @Override public void onActivitySaveInstanceState(Activity activity, Bundle state) { }
        });
    }
}
