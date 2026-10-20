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
    private Session s;
    private final ZjuSettingsStore settings;
    private LinearLayout content, browseBody, listBody;
    private TextView status;
    private DockNavigationLayout navigation;
    private ImageButton refresh;
    private DateIntervalBar weekBar;
    private ScrollView scroll;
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
        void publish() { main.post(() -> { ClassroomSummaryPage page = owner.get(); if (page != null && !page.isDestroyed()) page.update(); }); }
        void load(boolean remote) {
            if (busy || !allowed()) return;
            busy = true; cancelled = false; message = remote ? "正在获取课堂列表" : "正在读取课堂记录"; publish();
            worker.execute(() -> {
                try {
                    ClassroomStore store = new ClassroomStore(app, account);
                    List<ClassroomLesson> loaded = store.catalog();
                    List<JSONObject> savedHistory = store.history();
                    if ((remote || !initialized && loaded.isEmpty()) && !cancelled && allowed()) {
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
            if (s != null) { if (s.busy) s.cancel(); else s.load(true); }
        });
        UiStyle.addSpaced(root, new WorkspacePageHeader(activity, navigation.headerMotionHost(), refresh), 0, 8);
        weekBar = new DateIntervalBar(activity, () -> {
            if (s != null) UiStyle.dateDialog(activity, "选择日期", s.week, value -> {
                setWeek(value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            });
        }, direction -> {
            if (s == null) return false;
            setWeek(s.week.plusWeeks(direction)); return true;
        });
        status = label("", 14); root.addView(status);
        content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL); root.addView(content);
        scroll = SettingsPageLayout.viewport(activity, root, false); setContentView(scroll);
        update();
        if (s != null) scroll.post(() -> scroll.scrollTo(0, s.scrollY));
    }
    private void selectMode(int index) {
        if (s == null || s.mode == index) return;
        s.mode = index; s.page = 0; update();
    }
    private void setWeek(LocalDate week) {
        if (s == null || s.week.equals(week)) return;
        s.week = week; update();
    }
    private void checkAccount() {
        if (s != null && !s.allowed()) { s.dispose(); s = null; browseBody = null; }
        if (s == null && settings.enabled() && settings.configured()) {
            s = new Session(getApplicationContext(), settings.account()); s.owner = new WeakReference<>(this);
            browseBody = null;
        }
        update(); if (active && s != null && !s.initialized && !s.busy) s.load(false);
    }
    private void update() {
        if (content == null) return;
        if (s == null || !s.allowed()) {
            finishWeekMotion();
            refresh.setEnabled(false);
            status.setVisibility(View.GONE);
            if (!gated) {
                gated = true; browseBody = null; content.removeAllViews();
                button(content, "设置学校账号", () -> startActivity(new Intent(activity, ZjuSettingsActivity.class)));
            }
            return;
        }
        gated = false;
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
            boolean animateWeek = s.mode == 0 && shownMode == 0 && weekPicker != null
                    && !s.week.equals(shownWeek) && active && UiMotion.isEnabled();
            LocalDate previousWeek = shownWeek;
            boolean keepWeekBar = s.mode == 0 && weekBar.getParent() == browseBody;
            if (!keepWeekBar) {
                finishWeekMotion(); weekPicker = null; weekViewport = null;
                if (weekBar.getParent() instanceof android.view.ViewGroup parent) parent.removeView(weekBar);
                browseBody.removeAllViews();
            }
            listBody = null;
            shownMode = s.mode; shownPage=s.page; shownCatalog = s.catalog; shownHistory = s.history; shownWeek = s.week;
            if (s.mode == 0) {
                if (!keepWeekBar) {
                    LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-1, -2);
                    dateParams.bottomMargin = dp(8); browseBody.addView(weekBar, dateParams);
                }
                if (weekViewport == null) {
                    weekViewport = new FrameLayout(activity) {
                        @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                            return weekTransition || super.dispatchTouchEvent(event);
                        }
                    };
                    PressFeedback.protectViewport(weekViewport);
                    browseBody.addView(weekViewport, new LinearLayout.LayoutParams(-1, -2));
                }
                showWeek(animateWeek, previousWeek);
            } else {
                renderPage();
            }
        }
    }
    private void showWeek(boolean animate, LocalDate previousWeek) {
        ClassroomLessonPickerView next = new ClassroomLessonPickerView(activity, s.catalog, s.week, this::open);
        next.vertical(scroll);
        next.gestures(direction -> { if (s != null && s.mode == 0) setWeek(s.week.plusWeeks(direction)); }, null);
        ClassroomLessonPickerView old = weekPicker;
        int x = old == null ? 0 : old.getScrollX();
        weekPicker = next;
        if (!animate || weekViewport.getWidth() == 0) {
            next.addOnLayoutChangeListener(new View.OnLayoutChangeListener() {
                @Override public void onLayoutChange(View view, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    next.scrollTo(x, 0); next.removeOnLayoutChangeListener(this);
                }
            });
            weekViewport.removeAllViews(); weekViewport.addView(next, new FrameLayout.LayoutParams(-1, -2));
            return;
        }
        int direction = s.week.isAfter(previousWeek) ? 1 : -1;
        int width = weekViewport.getWidth(), oldHeight = old.getHeight();
        outgoingWeek = old; weekTransition = true;
        old.setLayoutParams(new FrameLayout.LayoutParams(-1, oldHeight));
        next.setTranslationX(width * direction);
        weekViewport.addView(next, new FrameLayout.LayoutParams(-1, -2));
        weekReady = () -> {
            int prior = next.getScrollX(); next.scrollTo(x, 0);
            if (next.isLayoutRequested() || prior != next.getScrollX()) {
                weekViewport.postInvalidateOnAnimation(); return false;
            }
            int newHeight = next.getHeight();
            weekViewport.getViewTreeObserver().removeOnPreDrawListener(weekReady); weekReady = null;
            next.setLayoutParams(new FrameLayout.LayoutParams(-1, newHeight));
            android.animation.ValueAnimator motion = android.animation.ValueAnimator.ofFloat(0f, 1f);
            weekMotion = motion; motion.setDuration(UiMotion.ENTER); motion.setInterpolator(UiMotion.SETTLE);
            motion.addUpdateListener(value -> {
                if (!UiMotion.isEnabled()) { finishWeekMotion(); update(); return; }
                float p = (float) value.getAnimatedValue();
                next.setTranslationX(CalendarSlideMotion.incoming(width, direction, p));
                old.setTranslationX(CalendarSlideMotion.outgoing(width, direction, p));
                android.view.ViewGroup.LayoutParams params = weekViewport.getLayoutParams();
                params.height = Math.round(oldHeight + (newHeight - oldHeight) * p); weekViewport.setLayoutParams(params);
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
            weekPicker.setTranslationX(0f); weekPicker.setLayoutParams(new FrameLayout.LayoutParams(-1, -2));
        }
        if (weekViewport != null) {
            android.view.ViewGroup.LayoutParams params = weekViewport.getLayoutParams();
            params.height = -2; weekViewport.setLayoutParams(params);
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
    @Override protected void onResume() {
        active = true; settings.prefs.registerOnSharedPreferenceChangeListener(preference); checkAccount();
        if (s != null && s.initialized && !s.busy) s.load(false);
    }
    @Override protected void onPause() {
        active = false; if (s != null) s.scrollY = scroll.getScrollY();
        finishWeekMotion(); update();
        settings.prefs.unregisterOnSharedPreferenceChangeListener(preference);
    }
    @Override protected void onDestroy() {
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
