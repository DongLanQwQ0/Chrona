package com.donglan.chrona;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.donglan.chrona.classroom.ClassroomClient;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomStore;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Classroom catalogue only. Each lesson opens its own Activity and retains this browser. */
final class ClassroomSummaryPage extends SchedulePage {
    private static final int PAGE_SIZE=50;
    private static final long CATALOG_REFRESH_MILLIS = 5 * 60_000L;
    private static final long STATUS_REFRESH_MILLIS = 30_000L;
    private enum Load { CACHE, REFRESH, AUTOMATIC }
    private Session s;
    private final ZjuSettingsStore settings;
    private LinearLayout content, browseBody, listBody;
    private TextView status;
    private DockNavigationLayout navigation;
    private ImageButton refresh;
    private DateIntervalBar weekBar;
    private ScrollView scroll, listScroll;
    private FrameLayout weekViewport;
    private ClassroomLessonPickerView weekPicker, outgoingWeek;
    private android.animation.ValueAnimator weekMotion;
    private android.view.ViewTreeObserver.OnPreDrawListener weekReady;
    private boolean weekTransition;
    private int shownMode = -1, shownPage=-1;
    private List<ClassroomLesson> shownCatalog;
    private List<JSONObject> shownHistory;
    private LocalDate shownWeek;
    private boolean active, gated;
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTick = () -> {
        if (!active || s == null || !s.allowed() || s.mode != 0) return;
        if (weekPicker != null) weekPicker.refreshStates(System.currentTimeMillis());
        if (!s.busy && android.os.SystemClock.elapsedRealtime() >= s.nextRemoteAt) s.load(Load.AUTOMATIC);
        scheduleRefresh();
    };
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener preference = (p,k) -> checkAccount();

    ClassroomSummaryPage(ScheduleWorkspaceActivity activity, Object retained) {
        super(activity, "ClassroomSummaryActivity", retained); settings = new ZjuSettingsStore(activity);
    }
    private static final class Session {
        final Context app;
        final String account;
        final ExecutorService worker = Executors.newSingleThreadExecutor();
        final Handler main = new Handler(Looper.getMainLooper());
        WeakReference<ClassroomSummaryPage> owner = new WeakReference<>(null);
        volatile ClassroomClient client;
        volatile boolean busy, cancelled;
        volatile long nextRemoteAt;
        String message = "";
        List<ClassroomLesson> catalog = new ArrayList<>();
        List<JSONObject> history = new ArrayList<>();
        int mode, page, scrollY;
        LocalDate week = LocalDate.now(ClassroomLesson.ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean initialized;
        Session(Context app, String account) { this.app = app; this.account = account; }
        boolean allowed() {
            ZjuSettingsStore store = new ZjuSettingsStore(app);
            return store.enabled() && store.configured() && account.equals(store.account());
        }
        void cancel() { cancelled = true; ClassroomClient current = client; if (current != null) current.cancel(); }
        void dispose() { cancel(); worker.shutdown(); owner.clear(); }
        void publish() { main.post(() -> { ClassroomSummaryPage page = owner.get(); if (page != null && !page.isDestroyed()) { page.update(); page.scheduleRefresh(); } }); }
        void load(Load kind) {
            if (busy || !allowed()) return;
            boolean remote = kind != Load.CACHE;
            if (remote || nextRemoteAt == 0) nextRemoteAt = android.os.SystemClock.elapsedRealtime() + CATALOG_REFRESH_MILLIS;
            busy = true; cancelled = false; message = kind == Load.AUTOMATIC ? "" : remote ? "正在获取课堂列表" : "正在读取课堂记录"; publish();
            worker.execute(() -> {
                try {
                    ClassroomStore store = new ClassroomStore(app, account);
                    List<ClassroomLesson> loaded = store.catalog();
                    List<JSONObject> savedHistory = store.history();
                    if (!initialized && !remote) nextRemoteAt = android.os.SystemClock.elapsedRealtime()
                            + Math.max(0, CATALOG_REFRESH_MILLIS - Math.max(0, System.currentTimeMillis() - store.catalogUpdatedAt()));
                    if ((remote || !initialized && loaded.isEmpty()) && !cancelled && allowed()) {
                        nextRemoteAt = android.os.SystemClock.elapsedRealtime() + CATALOG_REFRESH_MILLIS;
                        String[] credentials = new ZjuSettingsStore(app).credentials();
                        try { client = new ClassroomClient(credentials[0], credentials[1]); }
                        finally { Arrays.fill(credentials, ""); }
                        if (cancelled) client.cancel();
                        loaded = client.lessons();
                        if (cancelled || !allowed()) return;
                        store.catalog(loaded);
                    }
                    if (!cancelled && allowed()) { catalog = loaded; history = savedHistory; initialized = true; message = ""; }
                } catch (Exception error) {
                    message = cancelled ? "已停止刷新" : com.donglan.chrona.net.NetworkFailure.hasDetails(error)
                            ? com.donglan.chrona.net.NetworkFailure.describe(error)
                            : error.getMessage() == null ? "课堂列表读取失败" : error.getMessage();
                } finally {
                    ClassroomClient current = client; if (current != null) current.close(); client = null;
                    busy = false; publish();
                }
            });
        }
    }
    @Override protected void onCreate(Bundle state) {
        s = getLastNonConfigurationInstance() instanceof Session value ? value : null;
        if (s == null && settings.enabled() && settings.configured()) {
            s = new Session(getApplicationContext(), settings.account());
            if (state != null) {
                s.mode = Math.max(0, Math.min(2, state.getInt("mode")));
                s.page = Math.max(0, state.getInt("page", 0)); s.scrollY = state.getInt("scroll_y");
                s.week = LocalDate.parse(state.getString("week", s.week.toString()));
            }
        }
        if (s != null) s.owner = new WeakReference<>(this);
        LinearLayout root = SettingsPageLayout.content(activity);
        root.setPadding(dp(20), dp(UiStyle.PAGE_TOP_DP), dp(20), dp(12));
        navigation = new DockNavigationLayout(activity, this::selectMode, () -> { });
        navigation.setPadding(dp(6), dp(6), dp(6), dp(6));
        String[] modes = {"课表", "课堂", "历史"};
        for (int i = 0; i < modes.length; i++) {
            final int index = i;
            TextView item = label(modes[i], 15); item.setGravity(Gravity.CENTER); UiStyle.title(item);
            item.setOnClickListener(view -> { selectMode(index); navigation.finishPageSelection(index); });
            navigation.addItem(item, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        refresh = WorkspacePageHeader.icon(activity, R.drawable.ic_refresh, "刷新课堂", () -> {
            if (s != null) { if (s.busy) s.cancel(); else s.load(Load.REFRESH); }
        });
        UiStyle.addSpaced(root, new WorkspacePageHeader(activity, navigation.headerMotionHost(), refresh), 0, 8);
        weekBar = new DateIntervalBar(activity, () -> {
            if (s != null) UiDialogs.dateDialog(activity, "选择日期", s.week, value -> {
                setWeek(value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            });
        }, direction -> {
            if (s == null) return false;
            setWeek(s.week.plusWeeks(direction)); return true;
        });
        status = label("", 14); root.addView(status);
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-1, -2);
        dateParams.bottomMargin = dp(8); root.addView(weekBar, dateParams);
        content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        scroll = listScroll = SettingsPageLayout.viewport(activity, content, false);
        FrameLayout body = new FrameLayout(activity);
        body.addView(listScroll, new FrameLayout.LayoutParams(-1, -1));
        weekViewport = new FrameLayout(activity) {
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                return weekTransition || super.dispatchTouchEvent(event);
            }
        };
        PressFeedback.protectViewport(weekViewport);
        body.addView(weekViewport, new FrameLayout.LayoutParams(-1, -1));
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1)); setContentView(root);
        update();
        if (s != null) scroll.post(() -> scroll.scrollTo(0, s.scrollY));
    }
    private void selectMode(int index) {
        if (s == null || s.mode == index) return;
        s.mode = index; s.page = 0; update(); scheduleRefresh();
    }
    private void setWeek(LocalDate week) {
        if (s == null || s.week.equals(week)) return;
        s.week = week; update(); scheduleRefresh();
    }
    private void checkAccount() {
        if (s != null && !s.allowed()) { s.dispose(); s = null; browseBody = null; }
        if (s == null && settings.enabled() && settings.configured()) {
            s = new Session(getApplicationContext(), settings.account()); s.owner = new WeakReference<>(this);
            browseBody = null;
        }
        update(); if (active && s != null && !s.initialized && !s.busy) s.load(Load.CACHE);
        scheduleRefresh();
    }
    private void update() {
        if (content == null) return;
        if (s == null || !s.allowed()) {
            finishWeekMotion();
            weekBar.setVisibility(View.GONE);
            weekViewport.setVisibility(View.GONE); listScroll.setVisibility(View.VISIBLE); scroll = listScroll;
            refresh.setEnabled(false);
            status.setVisibility(View.GONE);
            if (!gated) {
                gated = true; browseBody = null; content.removeAllViews();
                button(content, "设置学校账号", () -> startActivity(new Intent(activity, ZjuSettingsActivity.class)));
            }
            return;
        }
        gated = false;
        weekBar.setVisibility(s.mode == 0 ? View.VISIBLE : View.GONE);
        weekViewport.setVisibility(s.mode == 0 ? View.VISIBLE : View.GONE);
        listScroll.setVisibility(s.mode == 0 ? View.GONE : View.VISIBLE);
        if (s.mode != 0) scroll = listScroll;
        status.setText(s.message); status.setVisibility(s.message.isEmpty() ? View.GONE : View.VISIBLE);
        if (browseBody == null) {
            content.removeAllViews(); shownMode = -1;
            browseBody = new LinearLayout(activity); browseBody.setOrientation(LinearLayout.VERTICAL); content.addView(browseBody);
        }
        navigation.finishPageSelection(s.mode);
        refresh.setEnabled(true);
        refresh.setImageResource(s.busy ? R.drawable.ic_x : R.drawable.ic_refresh);
        refresh.setContentDescription(s.busy ? "停止刷新" : "刷新课堂");
        refresh.setTooltipText(s.busy ? "停止刷新" : "刷新课堂");
        if (s.mode == 0) weekBar.setText(s.week + " — " + s.week.plusDays(6));
        if (weekTransition && s.mode == 0) return;
        boolean changed = shownMode != s.mode || shownPage!=s.page || shownCatalog != s.catalog || shownHistory != s.history || !s.week.equals(shownWeek);
        if (changed) {
            if (s.mode == 0 && shownMode == 0 && s.week.equals(shownWeek) && weekPicker != null
                    && weekPicker.refreshLessons(s.catalog)) {
                shownCatalog = s.catalog; shownHistory = s.history; shownPage = s.page;
                return;
            }
            boolean animateWeek = s.mode == 0 && shownMode == 0 && weekPicker != null
                    && !s.week.equals(shownWeek) && active && UiMotion.isEnabled();
            LocalDate previousWeek = shownWeek;
            if (s.mode != 0) { finishWeekMotion(); browseBody.removeAllViews(); }
            listBody = null;
            shownMode = s.mode; shownPage=s.page; shownCatalog = s.catalog; shownHistory = s.history; shownWeek = s.week;
            if (s.mode == 0) {
                showWeek(animateWeek, previousWeek);
            } else {
                renderPage();
            }
        }
    }
    private void showWeek(boolean animate, LocalDate previousWeek) {
        ClassroomLessonPickerView next = new ClassroomLessonPickerView(activity, s.catalog, s.week, this::open);
        next.gestures(direction -> { if (s != null && s.mode == 0) setWeek(s.week.plusWeeks(direction)); }, null);
        ClassroomLessonPickerView old = weekPicker;
        int x = old == null ? 0 : old.getScrollX();
        int y = old == null ? s.scrollY : old.verticalViewport().getScrollY();
        weekPicker = next; scroll = next.verticalViewport();
        if (!animate || weekViewport.getWidth() == 0) {
            next.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                @Override public boolean onPreDraw() {
                    next.scrollTo(x, 0);
                    next.verticalViewport().scrollTo(0, y);
                    next.getViewTreeObserver().removeOnPreDrawListener(this);
                    return true;
                }
            });
            weekViewport.removeAllViews(); weekViewport.addView(next, new FrameLayout.LayoutParams(-1, -1));
            return;
        }
        int direction = s.week.isAfter(previousWeek) ? 1 : -1;
        int width = weekViewport.getWidth();
        outgoingWeek = old; weekTransition = true;
        next.setTranslationX(width * direction);
        weekViewport.addView(next, new FrameLayout.LayoutParams(-1, -1));
        weekReady = () -> {
            int prior = next.getScrollX(); next.scrollTo(x, 0);
            next.verticalViewport().scrollTo(0, y);
            if (next.isLayoutRequested() || prior != next.getScrollX()) {
                weekViewport.postInvalidateOnAnimation(); return false;
            }
            weekViewport.getViewTreeObserver().removeOnPreDrawListener(weekReady); weekReady = null;
            android.animation.ValueAnimator motion = android.animation.ValueAnimator.ofFloat(0f, 1f);
            weekMotion = motion; motion.setDuration(UiMotion.ENTER); motion.setInterpolator(UiMotion.SETTLE);
            motion.addUpdateListener(value -> {
                if (!UiMotion.isEnabled()) { finishWeekMotion(); update(); return; }
                float p = (float) value.getAnimatedValue();
                next.setTranslationX(CalendarSlideMotion.incoming(width, direction, p));
                old.setTranslationX(CalendarSlideMotion.outgoing(width, direction, p));
            });
            motion.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (weekMotion == motion) { finishWeekMotion(); update(); }
                }
            });
            motion.start(); return false;
        };
        weekViewport.getViewTreeObserver().addOnPreDrawListener(weekReady);
    }
    private void finishWeekMotion() {
        if (weekReady != null && weekViewport != null && weekViewport.getViewTreeObserver().isAlive())
            weekViewport.getViewTreeObserver().removeOnPreDrawListener(weekReady);
        weekReady = null;
        if (weekMotion != null) {
            android.animation.ValueAnimator motion = weekMotion; weekMotion = null;
            motion.removeAllListeners(); motion.removeAllUpdateListeners(); motion.cancel();
        }
        if (outgoingWeek != null && weekViewport != null) weekViewport.removeView(outgoingWeek);
        outgoingWeek = null; weekTransition = false;
        if (weekPicker != null) {
            weekPicker.setTranslationX(0f);
        }
    }
    private void renderPage(){
            listBody=new LinearLayout(activity);listBody.setOrientation(LinearLayout.VERTICAL);browseBody.addView(listBody);
            List<ClassroomLesson> recent = new ArrayList<>();
            for (ClassroomLesson lesson : s.catalog) if (lesson.startsAt <= System.currentTimeMillis()) recent.add(lesson);
            recent.sort((a,b) -> Long.compare(b.startsAt, a.startsAt));
            int count=s.mode==1?recent.size():s.history.size(),pages=Math.max(1,(count+PAGE_SIZE-1)/PAGE_SIZE);
            s.page=Math.min(s.page,pages-1);shownPage=s.page;
            for (int i = s.page*PAGE_SIZE; i < Math.min((s.page+1)*PAGE_SIZE,count); i++) {
              if(s.mode==1){ClassroomLesson lesson = recent.get(i);
                Button item = button(listBody, lesson.date() + " " + clock(lesson) + " · " + lesson.state(System.currentTimeMillis())
                        + "\n" + lesson.course + " · " + lesson.title, () -> open(lesson));
                item.setEnabled(lesson.selectable(System.currentTimeMillis()));
              }else try{JSONObject record=s.history.get(i);ClassroomLesson lesson=ClassroomLesson.fromJson(record.getJSONObject("lesson"));
                button(listBody,lesson.course+"\n"+lesson.date()+" · "+(record.optBoolean("complete")?"已完成":"未完成"),()->open(lesson));
              }catch(Exception ignored){}
            }
            if(count==0)listBody.addView(label(s.mode==1?"暂无课堂":"暂无历史总结",16));
            if(pages>1){LinearLayout paging=new LinearLayout(activity);paging.setGravity(Gravity.CENTER_VERTICAL);
                ImageButton previous=UiStyle.navigationIcon(activity,R.drawable.ic_chevron_left,"上一页",()->changePage(-1));previous.setEnabled(s.page>0);paging.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));
                TextView number=label((s.page+1)+" / "+pages,15);number.setGravity(Gravity.CENTER);paging.addView(number,new LinearLayout.LayoutParams(0,-2,1));
                ImageButton next=UiStyle.navigationIcon(activity,R.drawable.ic_chevron_right,"下一页",()->changePage(1));next.setEnabled(s.page+1<pages);paging.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));
                UiStyle.addSpaced(browseBody,paging,8,8);
            }
    }
    private void changePage(int delta){s.page=Math.max(0,s.page+delta);update();scroll.post(()->scroll.scrollTo(0,0));}
    private void open(ClassroomLesson lesson) {
        if (s == null || !s.allowed() || !lesson.selectable(System.currentTimeMillis())) return;
        try { startActivity(new Intent(activity, ClassroomLessonActivity.class)
                .putExtra(ClassroomLessonActivity.EXTRA_LESSON, lesson.json().toString())); }
        catch (Exception error) { Feedback.show(activity, "无法打开课堂：" + error.getMessage()); }
    }
    private static String clock(ClassroomLesson lesson) {
        return Instant.ofEpochMilli(lesson.startsAt).atZone(ClassroomLesson.ZONE).toLocalTime().toString();
    }
    private void scheduleRefresh() {
        refreshHandler.removeCallbacks(refreshTick);
        if (!active || s == null || !s.allowed() || s.mode != 0) return;
        long now = System.currentTimeMillis(), delay = STATUS_REFRESH_MILLIS;
        for (ClassroomLesson lesson : s.catalog)
            if (lesson.startsAt > now) delay = Math.min(delay, lesson.startsAt - now);
        if (!s.busy) delay = Math.min(delay, Math.max(1, s.nextRemoteAt - android.os.SystemClock.elapsedRealtime()));
        refreshHandler.postDelayed(refreshTick, Math.max(1, delay));
    }
    @Override protected void onResume() {
        active = true; settings.prefs.registerOnSharedPreferenceChangeListener(preference); checkAccount();
        if (s != null && s.initialized && !s.busy) s.load(Load.CACHE);
        if (weekPicker != null) weekPicker.refreshStates(System.currentTimeMillis());
        scheduleRefresh();
    }
    @Override protected void onPause() {
        active = false; if (s != null) s.scrollY = scroll.getScrollY();
        refreshHandler.removeCallbacks(refreshTick);
        finishWeekMotion(); update();
        settings.prefs.unregisterOnSharedPreferenceChangeListener(preference);
    }
    @Override protected void onDestroy() {
        refreshHandler.removeCallbacks(refreshTick);
        if (s != null) { if (isFinishing()) s.dispose(); else if (s.owner.get() == this) s.owner.clear(); }
        super.onDestroy();
    }
    @Override public Object onRetainNonConfigurationInstance() { return s; }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (s == null) return;
        state.putInt("mode", s.mode); state.putInt("page", s.page); state.putString("week", s.week.toString());
        state.putInt("scroll_y", scroll.getScrollY());
    }
    private TextView label(String text, int sp) {
        TextView view = new TextView(activity); view.setText(text); view.setTextSize(sp); view.setTextColor(UiStyle.colors(activity).text); return view;
    }
    private Button button(LinearLayout parent, String title, Runnable action) {
        Button view = new Button(activity); view.setText(title); UiStyle.button(view, false);
        view.setOnClickListener(v -> action.run()); UiStyle.addSpaced(parent, view, 4, 6); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
