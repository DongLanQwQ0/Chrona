package com.donglan.chrona;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.*;

/** Settings gutters belong to content; system bars belong to the scroll viewport. */
final class SettingsPageLayout {
    static final int GUTTER_DP = 20;
    private SettingsPageLayout() { }
    static LinearLayout content(Activity activity) {
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
    static void header(Activity activity, LinearLayout root, String label) {
        LinearLayout header = new LinearLayout(activity);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(activity);
        back.setImageResource(R.drawable.ic_arrow_left);
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setImageTintList(ColorStateList.valueOf(UiStyle.colors(activity).primary));
        back.setPadding(dp(activity, 12), dp(activity, 12), dp(activity, 12), dp(activity, 12));
        back.setContentDescription("返回");
        UiStyle.acrylicChoice(back, false, UiStyle.RADIUS_PILL, false);
        back.setOnClickListener(view -> activity.finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)));
        TextView title = new TextView(activity);
        title.setText(label); title.setTextSize(24); UiStyle.title(title);
        title.setPadding(dp(activity, 12), 0, 0, 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        root.addView(header, new LinearLayout.LayoutParams(-1, -2));
    }
    static void show(Activity activity, LinearLayout root, boolean animatePresentation) {
        ScrollView viewport = new ScrollView(activity);
        viewport.setVerticalScrollBarEnabled(false);
        viewport.addView(root);
        FrameLayout stage = new FrameLayout(activity);
        stage.addView(new GlassBackdropView(activity), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(viewport, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.applyInsets(stage, viewport);
        activity.setContentView(stage);
        if (animatePresentation) UiMotion.observeScroll(viewport, root);
        else UiMotion.settleScroll(viewport, root);
    }
    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
