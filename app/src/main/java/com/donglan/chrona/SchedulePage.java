package com.donglan.chrona;

import android.app.Activity;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import java.util.List;

/** One retained view tree in the schedule workspace; the host owns Android lifecycle/results. */
abstract class SchedulePage extends ContextWrapper {
    protected final ScheduleWorkspaceActivity activity;
    private final String preferencesName;
    private final Object retained;
    private boolean destroyed;
    private View content;
    private final java.util.WeakHashMap<View,Integer> bottomPadding = new java.util.WeakHashMap<>();
    static final int RESULT_OK = Activity.RESULT_OK;

    SchedulePage(ScheduleWorkspaceActivity activity, String preferencesName, Object retained) {
        super(activity);
        this.activity = activity;
        this.preferencesName = preferencesName;
        this.retained = retained;
    }
    protected abstract void onCreate(Bundle state);
    protected void onResume() { }
    protected void onPause() { }
    protected void onDestroy() { destroyed = true; }
    protected void onSaveInstanceState(Bundle state) { }
    public Object onRetainNonConfigurationInstance() { return null; }
    protected void onActivityResult(int request, int result, Intent data) { }
    public void onRequestPermissionsResult(int request, String[] permissions, int[] results) { }
    protected final Object getLastNonConfigurationInstance() { return retained; }
    protected final Intent getIntent() { return activity.getIntent(); }
    protected final SharedPreferences getPreferences(int mode) {
        return getSharedPreferences(preferencesName, mode);
    }
    protected final void setContentView(View content) {
        this.content = content;
        content.setFitsSystemWindows(false);
    }
    final View content() { return content; }
    final void setBottomOverlayInset(int inset) { reserveBottom(content, inset); }
    private void reserveBottom(View view, int inset) {
        if(SettingsPageLayout.isToolsViewport(view))return;
        if(view instanceof android.widget.ScrollView scroll && scroll.getChildCount()>0) {
            View child=scroll.getChildAt(0);
            int base=bottomPadding.computeIfAbsent(child,key->key.getPaddingBottom());
            if(child.getPaddingBottom()!=base+inset)child.setPadding(child.getPaddingLeft(),child.getPaddingTop(),child.getPaddingRight(),base+inset);
            return;
        }
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)reserveBottom(group.getChildAt(i),inset);
    }
    protected final boolean isDestroyed() { return destroyed || activity.isDestroyed(); }
    protected final boolean isFinishing() { return activity.isFinishing(); }
    protected final void finish() { activity.finish(); }
    protected final void startActivityForResult(Intent intent, int request) {
        activity.startPageActivityForResult(this, intent, request);
    }
    protected final void requestPermissions(String[] permissions, int request) {
        activity.requestPagePermissions(this, permissions, request);
    }
    void collectGesturePriority(List<View> views) { collectGesturePriority(content, views); }
    private static void collectGesturePriority(View view, List<View> views) {
        if (view == null) return;
        if (view instanceof DockNavigationLayout || view instanceof DateIntervalBar || view instanceof HorizontalScrollView
                || view instanceof android.widget.SeekBar) { views.add(view); return; }
        if (view instanceof ViewGroup group) for (int i = 0; i < group.getChildCount(); i++)
            collectGesturePriority(group.getChildAt(i), views);
    }
}
