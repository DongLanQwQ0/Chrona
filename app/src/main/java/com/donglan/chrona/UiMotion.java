package com.donglan.chrona;

import android.app.Activity;
import android.os.Build;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** Shared, short settling motion. Window transitions keep system predictive back support. */
final class UiMotion {
    static final long ENTER = 240L;
    static final long EXIT = 180L;
    static final Interpolator SETTLE = new PathInterpolator(.2f, 0f, 0f, 1f);

    private UiMotion() { }

    static void install(Activity activity) {
        // Let the launcher own cold launch and back-to-home, including its icon transform.
        if (activity.isTaskRoot() || activity instanceof WidgetLaunchActivity) return;
        if (Build.VERSION.SDK_INT >= 34) {
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_OPEN,
                    R.anim.page_enter, R.anim.page_exit);
            activity.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE,
                    R.anim.page_return, R.anim.page_close);
        } else {
            activity.getWindow().setWindowAnimations(R.style.Animation_Chrona_Page);
        }
    }
}
