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

    @Override public void onCreate() {
        super.onCreate();
        registerActivityLifecycleCallbacks(new ActivityLifecycleCallbacks() {
            @Override public void onActivityCreated(Activity activity, Bundle state) {
                ThemeStore.watch(activity);
            }

            @Override public void onActivityDestroyed(Activity activity) {
                ThemeStore.forget(activity);
            }

            @Override public void onActivityStarted(Activity activity) { }

            @Override public void onActivityResumed(Activity activity) {
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
