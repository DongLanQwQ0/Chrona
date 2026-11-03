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
    static final int SHORT_WINDOW_HEIGHT_DP=480;
    static final int NAVIGATION_RAIL_MIN_WIDTH_DP=600;
    private SettingsPageLayout() { }
    static LinearLayout group(Activity activity, LinearLayout root, String name) {
        TextView heading = new TextView(activity); heading.setText(name); heading.setTextSize(12);heading.setIncludeFontPadding(false); UiStyle.muted(heading);
        heading.setPadding(dp(activity,4),0,dp(activity,4),0); if(android.os.Build.VERSION.SDK_INT>=28)heading.setAccessibilityHeading(true);
        UiStyle.addSpaced(root,heading,root.getChildCount()==1?0:16,7);
        LinearLayout group = new LinearLayout(activity); group.setOrientation(LinearLayout.VERTICAL); UiStyle.glass(group);
        root.addView(group,new LinearLayout.LayoutParams(-1,-2)); return group;
    }
    static void divider(Activity activity, LinearLayout group) {
        if(group.getChildCount()==0)return;
        android.view.View line=new android.view.View(activity);line.setBackgroundColor(UiStyle.colors(activity).outline);
        LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(activity,1));p.setMargins(dp(activity,16),0,dp(activity,16),0);group.addView(line,p);
    }
    /** Existing settings-hub row, shared by source settings rather than re-created per page. */
    static TextView row(Activity activity, LinearLayout group, String title, String value, Runnable action) {
        divider(activity,group);
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(activity,54));
        row.setPadding(dp(activity,16),dp(activity,10),dp(activity,12),dp(activity,10));
        TextView label=new TextView(activity);label.setText(title);label.setTextSize(16);label.setTextColor(UiStyle.colors(activity).text);label.setIncludeFontPadding(false);
        row.addView(label,new LinearLayout.LayoutParams(0,-2,1));
        TextView current=new TextView(activity);current.setText(value==null?"":value);current.setTextSize(13);current.setIncludeFontPadding(false);UiStyle.muted(current);
        current.setGravity(Gravity.END);current.setPadding(dp(activity,8),0,dp(activity,8),0);current.setMaxWidth(dp(activity,110));
        current.setVisibility(value==null?android.view.View.GONE:android.view.View.VISIBLE);row.addView(current,new LinearLayout.LayoutParams(-2,-2));
        ImageView chevron=new ImageView(activity);chevron.setImageResource(R.drawable.ic_chevron_right_line);
        chevron.setImageTintList(ColorStateList.valueOf(UiStyle.colors(activity).muted));chevron.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        row.addView(chevron,new LinearLayout.LayoutParams(dp(activity,16),dp(activity,16)));row.setFocusable(true);
        row.setOnClickListener(v->action.run());UiStyle.pressable(row);group.addView(row,new LinearLayout.LayoutParams(-1,-2));return current;
    }
    static Switch toggle(Activity activity,LinearLayout group,String title,boolean checked) {
        divider(activity,group);Switch control=new Switch(activity);control.setText(title);control.setTextSize(16);control.setChecked(checked);
        control.setPadding(dp(activity,16),dp(activity,3),dp(activity,16),dp(activity,3));control.setSwitchPadding(dp(activity,12));
        control.setMinHeight(dp(activity,54));UiStyle.toggle(control);group.addView(control,new LinearLayout.LayoutParams(-1,-2));return control;
    }
    static boolean shortWindow(android.content.Context context){return context.getResources().getConfiguration().screenHeightDp<=SHORT_WINDOW_HEIGHT_DP;}
    public static LinearLayout content(Activity activity) {
        return content(activity,new LinearLayout(activity));
    }
    static CollapsingPageLayout workspaceContent(Activity activity) {
        return content(activity,new CollapsingPageLayout(activity));
    }
    private static <T extends LinearLayout> T content(Activity activity,T root) {
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
        if (action != null) header.addView(action, new LinearLayout.LayoutParams(
                action instanceof HeaderActionGroup ? LinearLayout.LayoutParams.WRAP_CONTENT : dp(activity,48), dp(activity,48)));
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(-1, -2);
        headerParams.bottomMargin = dp(activity, HEADER_CONTENT_GAP_DP);
        root.addView(header, headerParams);
    }
    public static void show(Activity activity, LinearLayout root, boolean animatePresentation) {
        ScrollView viewport = viewport(activity, root, animatePresentation);
        showFixed(activity, viewport);
    }
    /** Keep page tools reachable without allowing them to consume the whole short window. */
    static ScrollView toolsViewport(Activity activity,android.view.View controls){
        ScrollView viewport=new ToolsViewport(activity);
        viewport.setVerticalScrollBarEnabled(false);viewport.addView(controls);return viewport;
    }
    static boolean isToolsViewport(android.view.View view){return view instanceof ToolsViewport;}
    private static final class ToolsViewport extends ScrollView{
        ToolsViewport(Activity activity){super(activity);}
        @Override protected void onMeasure(int widthSpec,int heightSpec){
            if(android.view.View.MeasureSpec.getMode(heightSpec)!=android.view.View.MeasureSpec.UNSPECIFIED)
                heightSpec=android.view.View.MeasureSpec.makeMeasureSpec(android.view.View.MeasureSpec.getSize(heightSpec)/2,android.view.View.MeasureSpec.AT_MOST);
            super.onMeasure(widthSpec,heightSpec);
        }
    }
    /** Secondary readers with their own scrolling content retain the common backdrop and safe area. */
    public static void showFixed(Activity activity, android.view.View viewport) {
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
    /** A full-width list viewport: reuse the page gutter to carry held-card drawing bounds. */
    static ScrollView motionViewport(Activity activity, LinearLayout root) {
        ScrollView viewport = viewport(activity, root, false);
        viewport.setClipChildren(false);
        viewport.setClipToPadding(false);
        PressFeedback.protectViewport(viewport);
        viewport.addOnLayoutChangeListener((view,l,t,r,b,ol,ot,or,ob) -> {
            int edge = Math.max(dp(activity,20), PressFeedback.edgeSpace(r-l));
            if (viewport.getPaddingLeft()!=edge)
                viewport.setPadding(edge,dp(activity,8),edge,dp(activity,8));
        });
        viewport.setPadding(dp(activity,20),dp(activity,8),dp(activity,20),dp(activity,8));
        return viewport;
    }
    private static int dp(Activity activity, int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
