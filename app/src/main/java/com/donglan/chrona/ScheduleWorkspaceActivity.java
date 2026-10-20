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
    private final SchedulePage[] pages = new SchedulePage[PAGE_COUNT];
    private final Bundle[] saved = new Bundle[PAGE_COUNT];
    private Object[] retained;
    private SwipePagerLayout pager;
    private DockNavigationLayout navigation;
    private ValueAnimator transition;
    private int selected;
    private float position, dragStart;
    private boolean resumed;
    private FrameLayout dockHost;
    protected abstract int initialPage();

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state);
        selected = state == null ? initialPage() : state.getInt("workspace_page", initialPage());
        selected = Math.max(0, Math.min(PAGE_COUNT - 1, selected)); position = selected;
        retained = getLastNonConfigurationInstance() instanceof Object[] values ? values : new Object[PAGE_COUNT];
        if (state != null) for (int i = 0; i < PAGE_COUNT; i++) saved[i] = state.getBundle("workspace_" + i);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout shell = new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL);
        pager = new SwipePagerLayout(this, new SwipePagerLayout.Listener() {
            @Override public void onStart() {
                cancelTransition(); dragStart = position; navigation.beginBodyDrag();
            }
            @Override public void onDrag(float distance) {
                float next = dragStart - distance / width();
                position = next < 0 ? next * .12f : next > 2 ? 2 + (next - 2) * .12f : next;
                renderPosition();
                int from = Math.max(0, Math.min(2, (int)Math.floor(position)));
                navigation.showPageProgress(from, Math.min(2, from + 1), position - from);
            }
            @Override public void onRelease(float distance, float velocity) {
                int target = Math.round(position);
                boolean quick = Math.abs(velocity) > dp(900) && Math.abs(distance) > dp(24);
                if (Math.abs(distance) > Math.max(dp(64), width() * .18f) || quick)
                    target = Math.round(dragStart) + (distance < 0 ? 1 : -1);
                selectPage(Math.max(0, Math.min(2, target)));
            }
            @Override public void onCancel() { selectPage(selected); }
        });
        pager.setFramePacingEnabled(true);
        pager.setHorizontalEdgeHandoffEnabled(true);
        shell.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = new DockNavigationLayout(this, this::selectPage, () -> { });
        navigation.setPadding(dp(6), dp(6), dp(6), dp(6));
        for (String label : new String[]{"课表", "课堂", "日程"}) {
            TextView item = new TextView(this); item.setText(label); item.setTextSize(15);
            item.setPadding(dp(8), 0, dp(8), 0);
            if ("课堂".equals(label)) item.setContentDescription("课堂总结");
            item.setGravity(Gravity.CENTER); UiStyle.title(item);
            navigation.addItem(item, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        dockHost = navigation.motionHost();
        FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
        dockParams.setMargins(dp(20), 0, dp(20), dp(4));
        stage.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        stage.addView(dockHost, dockParams);
        UiStyle.applyInsets(stage, shell, dockHost); setContentView(stage);
        page(selected); renderPosition(); navigation.setSelectedIndex(selected);
        pager.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or,ob) -> renderPosition());
        pager.getViewTreeObserver().addOnGlobalLayoutListener(this::updateGesturePriority);
    }

    final SchedulePage page(int index) {
        if (pages[index] == null) {
            pages[index] = switch (index) {
                case 0 -> new TimetablePage(this, retained[index]);
                case 1 -> new ClassroomSummaryPage(this, retained[index]);
                default -> new CalendarOverviewPage(this, retained[index]);
            };
            pages[index].onCreate(saved[index]);
            View view = pages[index].content();
            view.setTranslationX((index - position) * width());
            pager.addView(view, new FrameLayout.LayoutParams(-1, -1));
        }
        return pages[index];
    }
    final SchedulePage currentPage() { return page(selected); }
    final int selectedPage() { return selected; }
    private float width() { return Math.max(1, pager.getWidth()); }
    private void renderPosition() {
        int lower = Math.max(0, Math.min(2, (int)Math.floor(position)));
        int upper = Math.max(0, Math.min(2, (int)Math.ceil(position)));
        page(lower); page(upper);
        for (int i = 0; i < PAGE_COUNT; i++) if (pages[i] != null) {
            View view = pages[i].content();
            view.setTranslationX((i - position) * width());
            boolean visible = i == lower || i == upper;
            view.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
            view.setImportantForAccessibility(i == selected ? View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
                    : View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            if (visible) UiSurfaces.invalidateAcrylicSurfaces(view);
        }
    }
    final void selectPage(int index) {
        if (index < 0 || index >= PAGE_COUNT) return;
        cancelTransition(); page(index); navigation.beginPageSettle(index);
        if (!UiMotion.isEnabled() || Math.abs(position - index) < .001f) {
            completeSelection(index); return;
        }
        transition = ValueAnimator.ofFloat(position, index);
        transition.setDuration(Math.max(140L, Math.min(320L, Math.round(260f * Math.abs(position - index)))));
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
        position = index; renderPosition(); navigation.finishPageSelection(index); updateGesturePriority();
    }
    private void updateGesturePriority() {
        ArrayList<View> children = new ArrayList<>();
        pager.setHorizontalEdgeHandoffEnabled(selected == 0);
        if(dockHost!=null)for(SchedulePage page:pages)if(page!=null)page.setBottomOverlayInset(dockHost.getHeight()+dp(12));
        if (pages[selected] != null) pages[selected].collectGesturePriority(children);
        pager.setGesturePriorityChildren(children);
    }
    @Override protected void onResume() { super.onResume(); resumed = true; page(selected).onResume(); }
    @Override protected void onPause() {
        resumed = false; page(selected).onPause(); cancelTransition();
        position = selected; renderPosition(); navigation.finishPageSelection(selected); super.onPause();
    }
    @Override protected void onDestroy() {
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
        if (index >= 0 && index < PAGE_COUNT) page(index).onActivityResult(request & ((1 << REQUEST_BITS) - 1), result, data);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results); int index = (request >> REQUEST_BITS) - 1;
        if (index >= 0 && index < PAGE_COUNT) page(index).onRequestPermissionsResult(request & ((1 << REQUEST_BITS) - 1), permissions, results);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
