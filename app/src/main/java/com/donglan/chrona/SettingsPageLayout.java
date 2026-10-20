package com.donglan.chrona;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;

/** Settings gutters belong to content; system bars belong to the scroll viewport. */
public final class SettingsPageLayout {
    static final int GUTTER_DP = 20;
    static final int HEADER_CONTENT_GAP_DP = 16;
    private SettingsPageLayout() { }
    public static LinearLayout content(Activity activity) {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        UiStyle.page(activity, root);
        // page() enables fitsSystemWindows: disable it before Android dispatches insets.
        root.setFitsSystemWindows(false);
        root.setPadding(dp(activity, GUTTER_DP), dp(activity, 8),
                dp(activity, GUTTER_DP), dp(activity, 28));
        root.setBackgroundColor(Color.TRANSPARENT);
        return root;
    }
    public static void header(Activity activity, LinearLayout root, String label) {
        header(activity, root, label, null);
    }
    public static void header(Activity activity, LinearLayout root, String label, android.view.View action) {
        LinearLayout header = UiStyle.headerRow(activity);
        ImageButton back = UiStyle.navigationIcon(activity, R.drawable.ic_arrow_left, "返回", activity::finish);
        header.addView(back, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        TextView title = new TextView(activity);
        title.setText(label); title.setTextSize(24); UiStyle.title(title);
        title.setPadding(dp(activity, 12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (action != null) header.addView(action, new LinearLayout.LayoutParams(dp(activity,48), dp(activity,48)));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
        headerParams.bottomMargin = dp(activity, HEADER_CONTENT_GAP_DP);
        root.addView(header, headerParams);
    }
    public static void show(Activity activity, LinearLayout root, boolean animatePresentation) {
        ScrollView viewport = viewport(activity, root, animatePresentation);
        FrameLayout stage = new FrameLayout(activity);
        stage.addView(new GlassBackdropView(activity), new FrameLayout.LayoutParams(-1, -1));
        // Give ScrollView the actual visible height, so focused fields scroll above the IME.
        FrameLayout safeArea = new FrameLayout(activity);
        safeArea.addView(viewport, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(safeArea, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.applyInsets(stage, safeArea);
        activity.setContentView(stage);
    }
    static ScrollView viewport(Activity activity, LinearLayout root, boolean animatePresentation) {
        ScrollView viewport = new ScrollView(activity);
        viewport.setVerticalScrollBarEnabled(false);
        viewport.addView(root);
        if (animatePresentation) UiMotion.observeScroll(viewport, root);
        else UiMotion.settleScroll(viewport, root);
        return viewport;
    }
    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
