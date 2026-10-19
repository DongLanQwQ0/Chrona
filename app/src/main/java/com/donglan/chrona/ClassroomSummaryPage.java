package com.donglan.chrona;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
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
    private Session s;
    private final ZjuSettingsStore settings;
    private LinearLayout content, browseBody, listBody;
    private TextView status;
    private DockNavigationLayout navigation;
    private Button refresh, cancel;
    private ScrollView scroll;
    private int shownMode = -1, listCount;
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
        int mode, page = 50, scrollY;
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
                s.page = Math.max(50, state.getInt("page", 50)); s.scrollY = state.getInt("scroll_y");
                s.week = LocalDate.parse(state.getString("week", s.week.toString()));
            }
        }
        if (s != null) s.owner = new WeakReference<>(this);
        LinearLayout root = SettingsPageLayout.content(activity);
        SettingsPageLayout.header(activity, root, "课堂总结");
        status = label("", 14); root.addView(status);
        content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL); root.addView(content);
        scroll = SettingsPageLayout.viewport(activity, root, false); setContentView(scroll);
        update();
        if (s != null) scroll.post(() -> scroll.scrollTo(0, s.scrollY));
    }
    private void checkAccount() {
        if (s != null && !s.allowed()) { s.dispose(); s = null; navigation = null; }
        if (s == null && settings.enabled() && settings.configured()) {
            s = new Session(getApplicationContext(), settings.account()); s.owner = new WeakReference<>(this);
            navigation = null;
        }
        update(); if (active && s != null && !s.initialized && !s.busy) s.load(false);
    }
    private void update() {
        if (content == null) return;
        if (s == null || !s.allowed()) {
            status.setVisibility(View.GONE);
            if (!gated) {
                gated = true; navigation = null; content.removeAllViews();
                button(content, "设置学校账号", () -> startActivity(new Intent(activity, ZjuSettingsActivity.class)));
            }
            return;
        }
        gated = false;
        status.setText(s.message); status.setVisibility(s.message.isEmpty() ? View.GONE : View.VISIBLE);
        if (navigation == null) {
            content.removeAllViews(); shownMode = -1;
            navigation = new DockNavigationLayout(activity, index -> { s.mode = index; s.page = 50; update(); }, () -> { });
            navigation.setPadding(dp(4), dp(3), dp(4), dp(3));
            for (String tab : new String[]{"课表", "课堂", "历史"}) {
                TextView item = label(tab, 15); item.setGravity(Gravity.CENTER); UiStyle.title(item);
                navigation.addItem(item, new LinearLayout.LayoutParams(0, dp(48), 1));
            }
            content.addView(navigation.motionHost());
            browseBody = new LinearLayout(activity); browseBody.setOrientation(LinearLayout.VERTICAL); content.addView(browseBody);
            refresh = button(content, "刷新课堂", () -> s.load(true));
            cancel = button(content, "停止", () -> s.cancel());
        }
        navigation.setSelectedIndex(s.mode);
        refresh.setEnabled(!s.busy); cancel.setVisibility(s.busy ? View.VISIBLE : View.GONE);
        boolean changed = shownMode != s.mode || shownCatalog != s.catalog || shownHistory != s.history || !s.week.equals(shownWeek);
        if (changed) {
            browseBody.removeAllViews(); listBody = null; listCount = 0;
            shownMode = s.mode; shownCatalog = s.catalog; shownHistory = s.history; shownWeek = s.week;
            if (s.mode == 0) {
                button(browseBody, s.week.toString(), () -> UiStyle.dateDialog(activity, "选择日期", s.week, value -> {
                    s.week = value.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); update();
                }));
                LinearLayout row = new LinearLayout(activity);
                action(row, "上一周", () -> { s.week = s.week.minusWeeks(1); update(); });
                action(row, "下一周", () -> { s.week = s.week.plusWeeks(1); update(); }); browseBody.addView(row);
                browseBody.addView(new ClassroomLessonPickerView(activity, s.catalog, s.week, this::open));
            } else if (s.mode == 1) {
                listBody = new LinearLayout(activity); listBody.setOrientation(LinearLayout.VERTICAL); browseBody.addView(listBody);
                button(browseBody, "加载更多", () -> { s.page += 50; update(); });
            } else {
                for (JSONObject record : s.history) try {
                    ClassroomLesson lesson = ClassroomLesson.fromJson(record.getJSONObject("lesson"));
                    button(browseBody, lesson.course + "\n" + lesson.date() + " · " + (record.optBoolean("complete") ? "已完成" : "未完成"), () -> open(lesson));
                } catch (Exception ignored) { }
                if (s.history.isEmpty()) browseBody.addView(label("暂无历史总结", 16));
            }
        }
        if (s.mode == 1) {
            List<ClassroomLesson> recent = new ArrayList<>();
            for (ClassroomLesson lesson : s.catalog) if (lesson.startsAt <= System.currentTimeMillis()) recent.add(lesson);
            recent.sort((a,b) -> Long.compare(b.startsAt, a.startsAt));
            for (int i = listCount; i < Math.min(s.page, recent.size()); i++) {
                ClassroomLesson lesson = recent.get(i);
                Button item = button(listBody, lesson.date() + " " + clock(lesson) + " · " + lesson.state(System.currentTimeMillis())
                        + "\n" + lesson.course + " · " + lesson.title, () -> open(lesson));
                item.setEnabled(lesson.selectable(System.currentTimeMillis())); listCount++;
            }
            browseBody.getChildAt(1).setVisibility(listCount < recent.size() ? View.VISIBLE : View.GONE);
            if (changed && recent.isEmpty()) listBody.addView(label("暂无课堂", 16));
        }
    }
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
    private void action(LinearLayout parent, String title, Runnable action) {
        Button view = new Button(activity); view.setText(title); UiStyle.button(view, false); view.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); params.setMargins(dp(2),dp(4),dp(2),dp(6)); parent.addView(view, params);
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
