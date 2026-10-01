package com.donglan.chrona;

import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

/** Motion for in-page content only. Activity transitions belong to the Android system. */
final class UiMotion {
    static final long ENTER = 240L;
    static final long EXIT = 180L;
    static final Interpolator SETTLE = new PathInterpolator(.2f, 0f, 0f, 1f);

    private UiMotion() { }

}
