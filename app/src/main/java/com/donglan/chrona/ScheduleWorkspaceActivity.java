package com.donglan.chrona;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

/** Shared timetable / classroom / calendar workspace, retaining each page across tab changes. */
public abstract class ScheduleWorkspaceActivity extends Activity {
    private static final int PAGE_COUNT = 3;
    private static final int REQUEST_BITS = 12;
    private static final int NAVIGATION_RAIL_WIDTH_DP = 96;
    private final SchedulePage[] pages = new SchedulePage[PAGE_COUNT];
    private final Bundle[] saved = new Bundle[PAGE_COUNT];
    // Page IDs stay stable for saved state and activity results; positions include visible pages only.
    private int[] visiblePages;
    private boolean classroomEnabled;
    private ZjuSettingsStore schoolSettings;
    private boolean rebuilding;
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener schoolPreference =
            (preferences, key) -> { if ("enabled".equals(key) || key == null) refreshAvailability(); };
    private Object[] retained;
    private SwipePagerLayout pager;
    private DockNavigationLayout navigation;
    private ValueAnimator transition;
    private int selected;
    private float position, dragStart;
    private boolean resumed;
    private FrameLayout dockHost;
    private boolean navigationRail, compactNavigation;
    private TextView compactNavigationLabel;
    protected abstract int initialPage();

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state);
        schoolSettings = new ZjuSettingsStore(this);
        classroomEnabled = schoolSettings.enabled();
        visiblePages = classroomEnabled ? new int[]{0, 1, 2} : new int[]{0, 2};
        selected = state == null ? initialPage() : state.getInt("workspace_page", initialPage());
        selected = Math.max(0, Math.min(PAGE_COUNT - 1, selected));
        if (slot(selected) < 0) selected = 0;
        position = slot(selected);
        retained = getLastNonConfigurationInstance() instanceof Object[] values ? values : new Object[PAGE_COUNT];
        if (state != null) for (int i = 0; i < PAGE_COUNT; i++) saved[i] = state.getBundle("workspace_" + i);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout shell = new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL);
        int windowWidth=getResources().getConfiguration().screenWidthDp;
        boolean shortWindow=SettingsPageLayout.shortWindow(this);
        navigationRail=shortWindow&&windowWidth>=SettingsPageLayout.NAVIGATION_RAIL_MIN_WIDTH_DP;compactNavigation=shortWindow&&!navigationRail;
        pager = new SwipePagerLayout(this, new SwipePagerLayout.Listener() {
            @Override public void onStart() {
                cancelTransition(); dragStart = position; navigation.beginBodyDrag();
            }
            @Override public void onDrag(float distance) {
                float next = dragStart - distance / width();
                int last = visiblePages.length - 1;
                position = next < 0 ? next * .12f : next > last ? last + (next - last) * .12f : next;
                renderPosition();
                int from = Math.max(0, Math.min(last, (int)Math.floor(position)));
                navigation.showPageProgress(from, Math.min(last, from + 1), position - from);
            }
            @Override public void onRelease(float distance, float velocity) {
                int target = Math.round(position);
                boolean quick = Math.abs(velocity) > dp(900) && Math.abs(distance) > dp(24);
                if (Math.abs(distance) > Math.max(dp(64), width() * .18f) || quick)
                    target = Math.round(dragStart) + (distance < 0 ? 1 : -1);
                selectPage(visiblePages[Math.max(0, Math.min(visiblePages.length - 1, target))]);
            }
            @Override public void onCancel() { selectPage(selected); }
        });
        pager.setFramePacingEnabled(true);
        pager.setHorizontalEdgeHandoffEnabled(true);
        navigation = new DockNavigationLayout(this, index -> selectPage(visiblePages[index]), () -> { });
        navigation.setPadding(dp(6), dp(6), dp(6), dp(6));
        for (int id : visiblePages) {
            String label = new String[]{"课表", "课堂", "日程"}[id];
            TextView item = new TextView(this); item.setText(label); item.setTextSize(15);
            item.setPadding(dp(8), 0, dp(8), 0);
            if ("课堂".equals(label)) item.setContentDescription("课堂总结");
            item.setGravity(Gravity.CENTER); UiStyle.title(item);
            navigation.addItem(item, navigationRail?new LinearLayout.LayoutParams(-1,0,1):new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        if(navigationRail){
            shell.setOrientation(LinearLayout.HORIZONTAL);shell.setClipChildren(false);shell.setClipToPadding(false);
            navigation.setOrientation(LinearLayout.VERTICAL);
            dockHost=new FrameLayout(this);dockHost.setClipChildren(false);dockHost.setClipToPadding(false);
            dockHost.addView(navigation,new FrameLayout.LayoutParams(dp(72),dp(visiblePages.length*56+12),Gravity.CENTER));
            shell.addView(dockHost,new LinearLayout.LayoutParams(dp(NAVIGATION_RAIL_WIDTH_DP),-1));
            shell.addView(pager,new LinearLayout.LayoutParams(0,-1,1));
        }else{
            shell.addView(pager,new LinearLayout.LayoutParams(-1,0,1));
            if(compactNavigation){
                dockHost=new FrameLayout(this);compactNavigationLabel=new TextView(this);UiStyle.fieldTrigger(compactNavigationLabel);
                compactNavigationLabel.setGravity(Gravity.CENTER);compactNavigationLabel.setTextSize(15);
                compactNavigationLabel.setOnClickListener(v->{String[] labels=new String[visiblePages.length];for(int i=0;i<labels.length;i++)labels[i]=new String[]{"课表","课堂","日程"}[visiblePages[i]];
                    UiDialogs.choiceDialog(this,"页面",labels,slot(selected),choice->selectPage(visiblePages[choice]));});
                dockHost.addView(compactNavigationLabel,new FrameLayout.LayoutParams(dp(120),dp(48),Gravity.END));
            }else dockHost=navigation.motionHost();
        }
        InteractionLayers.pin(dockHost);
        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        dockParams.setMargins(dp(20), 0, dp(20), dp(4));
        stage.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        if(!navigationRail)stage.addView(dockHost, dockParams);
        UiStyle.applyInsets(stage, shell, navigationRail?null:dockHost); setContentView(stage);
        page(selected); renderPosition(); navigation.setSelectedIndex(slot(selected));
        pager.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> renderPosition());
        pager.getViewTreeObserver().addOnGlobalLayoutListener(this::updateGesturePriority);
        schoolSettings.prefs.registerOnSharedPreferenceChangeListener(schoolPreference);
    }

    final SchedulePage page(int index) {
        if (slot(index) < 0) throw new IllegalArgumentException("Page is not available");
        if (pages[index] == null) {
            pages[index] = switch (index) {
                case 0 -> new TimetablePage(this, retained[index]);
                case 1 -> new ClassroomSummaryPage(this, retained[index]);
                default -> new CalendarOverviewPage(this, retained[index]);
            };
            pages[index].onCreate(saved[index]);
            View view = pages[index].content();
            view.setTranslationX((slot(index) - position) * width());
            pager.addView(view, new FrameLayout.LayoutParams(-1, -1));
        }
        return pages[index];
    }
    final SchedulePage currentPage() { return page(selected); }
    final int selectedPage() { return selected; }
    private int slot(int pageId) {
        for (int i = 0; i < visiblePages.length; i++) if (visiblePages[i] == pageId) return i;
        return -1;
    }
    private float width() { return Math.max(1, pager.getWidth()); }
    private void renderPosition() {
        if(compactNavigationLabel!=null){String value=new String[]{"课表","课堂","日程"}[selected];compactNavigationLabel.setText(value);compactNavigationLabel.setContentDescription("当前页面："+value+"，选择页面");}
        int lower = visiblePages[Math.max(0, Math.min(visiblePages.length - 1, (int)Math.floor(position)))];
        int upper = visiblePages[Math.max(0, Math.min(visiblePages.length - 1, (int)Math.ceil(position)))];
        page(lower); page(upper);
        for (int i = 0; i < PAGE_COUNT; i++) if (pages[i] != null) {
            View view = pages[i].content();
            view.setTranslationX((slot(i) - position) * width());
            boolean visible = i == lower || i == upper;
            view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            view.setImportantForAccessibility(i == selected ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            if (visible) UiSurfaces.invalidateAcrylicSurfaces(view);
        }
    }
    final void selectPage(int index) {
        int target = slot(index);
        if (target < 0) return;
        cancelTransition(); page(index); navigation.beginPageSettle(target);
        if (!UiMotion.isEnabled() || Math.abs(position - target) < .001f) {
            completeSelection(index); return;
        }
        transition = ValueAnimator.ofFloat(position, target);
        transition.setDuration(Math.max(140L, Math.min(320L, Math.round(260f * Math.abs(position - target)))));
        transition.setInterpolator(UiMotion.SETTLE);
        transition.addUpdateListener(value -> { position = (float)value.getAnimatedValue(); renderPosition(); });
        transition.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator animation) { transition = null; completeSelection(index); }
        });
        transition.start();
    }
    private void cancelTransition() {
        if (transition == null) return;
        transition.removeAllListeners(); transition.cancel(); transition = null;
    }
    private void completeSelection(int index) {
        if (selected != index) {
            if (resumed) page(selected).onPause();
            selected = index;
            if (resumed) page(selected).onResume();
        }
        position = slot(index); renderPosition(); navigation.finishPageSelection(slot(index)); updateGesturePriority();
    }
    private void updateGesturePriority() {
        ArrayList<View> children = new ArrayList<>();
        pager.setHorizontalEdgeHandoffEnabled(selected == 0);
        if(dockHost!=null)for(SchedulePage page:pages)if(page!=null)page.setBottomOverlayInset(navigationRail?0:dockHost.getHeight()+dp(12));
        if (pages[selected] != null) pages[selected].collectGesturePriority(children);
        pager.setGesturePriorityChildren(children);
    }
    @Override protected void onResume() {
        super.onResume();
        if (refreshAvailability()) return;
        resumed = true; page(selected).onResume();
    }
    private boolean refreshAvailability() {
        if (classroomEnabled == schoolSettings.enabled()) return false;
        if (!rebuilding && !isFinishing() && !isDestroyed()) { rebuilding = true; recreate(); }
        return true;
    }
    @Override protected void onPause() {
        resumed = false; page(selected).onPause(); cancelTransition();
        position = slot(selected); renderPosition(); navigation.finishPageSelection(slot(selected)); super.onPause();
    }
    @Override protected void onDestroy() {
        schoolSettings.prefs.unregisterOnSharedPreferenceChangeListener(schoolPreference);
        cancelTransition(); for (SchedulePage page : pages) if (page != null) page.onDestroy(); super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putInt("workspace_page", selected);
        for (int i = 0; i < PAGE_COUNT; i++) {
            Bundle value = saved[i] == null ? new Bundle() : new Bundle(saved[i]);
            if (pages[i] != null) pages[i].onSaveInstanceState(value);
            state.putBundle("workspace_" + i, value);
        }
        super.onSaveInstanceState(state);
    }
    @Override public Object onRetainNonConfigurationInstance() {
        Object[] result = retained.clone();
        for (int i = 0; i < PAGE_COUNT; i++) if (pages[i] != null) result[i] = pages[i].onRetainNonConfigurationInstance();
        return result;
    }
    private int pageRequest(SchedulePage page, int request) {
        if (request < 0 || request >= 1 << REQUEST_BITS) throw new IllegalArgumentException("Page request out of range");
        for (int i = 0; i < PAGE_COUNT; i++) if (pages[i] == page) return ((i + 1) << REQUEST_BITS) | request;
        throw new IllegalArgumentException("Page is not attached");
    }
    final void startPageActivityForResult(SchedulePage page, Intent intent, int request) {
        startActivityForResult(intent, pageRequest(page, request));
    }
    final void requestPagePermissions(SchedulePage page, String[] permissions, int request) {
        requestPermissions(permissions, pageRequest(page, request));
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data); int index = (request >> REQUEST_BITS) - 1;
        if (slot(index) >= 0) page(index).onActivityResult(request & ((1 << REQUEST_BITS) - 1), result, data);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results); int index = (request >> REQUEST_BITS) - 1;
        if (slot(index) >= 0) page(index).onRequestPermissionsResult(request & ((1 << REQUEST_BITS) - 1), permissions, results);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
