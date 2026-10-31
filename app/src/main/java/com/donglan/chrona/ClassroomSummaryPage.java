package com.donglan.chrona;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.text.Editable;
import android.text.TextWatcher;
import com.donglan.chrona.classroom.ClassroomClient;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomStore;
import com.donglan.chrona.classroom.ClassroomExport;
import org.json.JSONObject;
import java.lang.ref.WeakReference;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Classroom catalogue only. Each lesson opens its own Activity and retains this browser. */
final class ClassroomSummaryPage extends SchedulePage {
    private static final int PAGE_SIZE=50;
    private static final long CATALOG_REFRESH_MILLIS = 5 * 60_000L;
    private static final long STATUS_REFRESH_MILLIS = 30_000L;
    private enum Load { CACHE, LOCAL, REFRESH, AUTOMATIC }
    private Session s;
    private final ZjuSettingsStore settings;
    private LinearLayout content, browseBody, listBody;
    private TextView status;
    private DockNavigationLayout navigation;
    private TextView compactMode;
    private static final String[] MODES={"课表","课堂","监视","历史"};
    private ImageButton refresh;
    private ImageButton monitorSettings;
    private HeaderActionGroup headerActions;
    private EditText search;
    private LinearLayout historyTools;
    private TextView historyOrder;
    private TextView historyCourse;
    private String shownHistoryCourse="";
    private ImageButton historyDirection;
    private static final String[] HISTORY_ORDERS={"课程时间","总结时间","未总结"};
    private String shownQuery="";
    private int shownHistoryOrder=-1;
    private boolean shownHistoryAscending;
    private DateIntervalBar weekBar, pageBar;
    private ScrollView toolsViewport;
    private int totalPages = 1;
    private ScrollView scroll, listScroll;
    private FrameLayout weekViewport, bodyHost;
    private ClassroomLessonPickerView weekPicker, outgoingWeek;
    private android.animation.ValueAnimator weekMotion;
    private android.view.ViewTreeObserver.OnPreDrawListener weekReady;
    private boolean weekTransition;
    private int shownMode = -1, shownPage=-1;
    private List<ClassroomLesson> shownCatalog;
    private List<JSONObject> shownHistory;
    private JSONObject shownMonitoring;
    private LocalDate shownWeek;
    private boolean active, gated, firstPresentation;
    private static final int BATCH_EXPORT=2046;
    private LinearLayout selectionBar, resultsHeading;
    private TextView selectionCount,resultsCount,batchStatus;
    private Button selectAll,batchDownload,batchExport,batchDelete,batchMenu;
    private final LinkedHashMap<String,TextView> selectionMarkers=new LinkedHashMap<>();
    private List<ClassroomLesson> matchingLessons=new ArrayList<>();
    private final Handler refreshHandler = new Handler(Looper.getMainLooper());
    private final Runnable refreshTick = () -> {
        if (!active || s == null || !s.allowed()) return;
        if (weekPicker != null) weekPicker.refreshStates(System.currentTimeMillis());
        if (!s.busy) {
            ClassroomBatchWork batch=ClassroomBatchWork.find(s.account);
            if(s.mode != 0&&(batch==null||!batch.running))s.load(Load.LOCAL);
            else if(s.mode==0&&android.os.SystemClock.elapsedRealtime() >= s.nextRemoteAt)s.load(Load.AUTOMATIC);
        }
        updateBatchStatus();updateSelectionUi();
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
        volatile boolean busy, cancelled, refreshing;
        volatile long nextRemoteAt;
        String message = "";
        List<ClassroomLesson> catalog = new ArrayList<>();
        List<JSONObject> history = new ArrayList<>();
        JSONObject monitoring=new JSONObject();
        int mode, page, scrollY, weekScrollX;
        String query="";
        String historyCourse="";
        boolean selecting,localWork;
        final LinkedHashSet<String> selected=new LinkedHashSet<>();
        final List<ClassroomLesson> pendingExport=new ArrayList<>();
        ClassroomExport.Layout exportLayout=ClassroomExport.Layout.FLAT;
        Uri exportUri,shareUri;
        String operationMessage="";
        int historyOrder=1;
        boolean historyAscending;
        LocalDate week = LocalDate.now(ClassroomLesson.ZONE).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        boolean initialized;
        Session(Context app, String account) { this.app = app; this.account = account; }
        ClassroomStore storeForBatch()throws IOException{return new ClassroomStore(app,account);}
        boolean allowed() {
            ZjuSettingsStore store = new ZjuSettingsStore(app);
            return store.enabled() && store.configured() && account.equals(store.account());
        }
        void cancel() { cancelled = true; ClassroomClient current = client; if (current != null) current.cancel(); }
        void dispose() { cancel(); worker.shutdown(); owner.clear(); }
        void publish() { main.post(() -> { ClassroomSummaryPage page = owner.get(); if (page != null && !page.isDestroyed()) { page.update(); page.scheduleRefresh(); } }); }
        void accept(List<ClassroomLesson> loaded,List<JSONObject> savedHistory)throws Exception {
            org.json.JSONArray before=new org.json.JSONArray(),after=new org.json.JSONArray();
            for(ClassroomLesson lesson:catalog)before.put(lesson.json());
            for(ClassroomLesson lesson:loaded)after.put(lesson.json());
            if(!before.toString().equals(after.toString()))catalog=loaded;
            if(!history.toString().equals(savedHistory.toString()))history=savedHistory;
            java.util.Set<String> valid=new java.util.HashSet<>();for(ClassroomLesson lesson:loaded)valid.add(ClassroomBatchWork.key(lesson));
            for(JSONObject record:savedHistory){JSONObject lesson=record.optJSONObject("lesson");if(lesson!=null)valid.add(lesson.optString("course_id")+"/"+lesson.optString("id"));}
            main.post(()->selected.retainAll(valid));
            JSONObject next=new ClassroomMonitorStore(app,account).read();
            if(!monitoring.toString().equals(next.toString()))monitoring=next;
            initialized=true;
        }
        void load(Load kind) {
            if (busy || !allowed()) return;
            boolean remote = kind == Load.REFRESH || kind == Load.AUTOMATIC;
            if (remote || nextRemoteAt == 0) nextRemoteAt = android.os.SystemClock.elapsedRealtime() + CATALOG_REFRESH_MILLIS;
            busy = true; refreshing = remote; cancelled = false; message = ""; publish();
            worker.execute(() -> {
                try {
                    ClassroomStore store = new ClassroomStore(app, account);
                    List<ClassroomLesson> loaded = store.catalog();
                    List<JSONObject> savedHistory = store.history();
                    boolean initial=!initialized;
                    if (initial && !remote && store.catalogUpdatedAt()>0) nextRemoteAt = android.os.SystemClock.elapsedRealtime()
                            + Math.max(0, CATALOG_REFRESH_MILLIS - Math.max(0, System.currentTimeMillis() - store.catalogUpdatedAt()));
                    if(!cancelled&&allowed()){accept(loaded,savedHistory);publish();}
                    if ((remote || initial && loaded.isEmpty() && savedHistory.isEmpty()) && !cancelled && allowed()) {
                        refreshing = true; publish();
                        nextRemoteAt = android.os.SystemClock.elapsedRealtime() + CATALOG_REFRESH_MILLIS;
                        String[] credentials = new ZjuSettingsStore(app).credentials();
                        try { client = new ClassroomClient(credentials[0], credentials[1]); }
                        finally { Arrays.fill(credentials, ""); }
                        if (cancelled) client.cancel();
                        loaded = client.lessons();
                        if (cancelled || !allowed()) return;
                        store.catalog(loaded);
                    }
                    if (!cancelled && allowed()) {
                        accept(loaded,savedHistory); message = "";
                    }
                } catch (Exception error) {
                    message = cancelled ? "已停止刷新" : com.donglan.chrona.net.NetworkFailure.hasDetails(error)
                            ? com.donglan.chrona.net.NetworkFailure.describe(error)
                            : error.getMessage() == null ? "课堂列表读取失败" : error.getMessage();
                } finally {
                    ClassroomClient current = client; if (current != null) current.close(); client = null;
                    refreshing = false; busy = false; publish();
                }
            });
        }
    }
    @Override protected void onCreate(Bundle state) {
        firstPresentation = state == null ? getLastNonConfigurationInstance() == null : state.getBoolean("first_presentation",false);
        s = getLastNonConfigurationInstance() instanceof Session value ? value : null;
        if (s == null && settings.enabled() && settings.configured()) {
            s = new Session(getApplicationContext(), settings.account());
            if (state != null) {
                s.mode = Math.max(0, Math.min(3, state.getInt("mode")));
                s.page = Math.max(0, state.getInt("page", 0)); s.scrollY = state.getInt("scroll_y");
                s.weekScrollX = state.getInt("week_scroll_x", 0);
                s.query=state.getString("query","");s.historyOrder=Math.max(0,Math.min(2,state.getInt("history_order",1)));s.historyAscending=state.getBoolean("history_ascending",false);
                s.historyCourse=state.getString("history_course","");s.selecting=state.getBoolean("selecting",false);
                ArrayList<String> selected=state.getStringArrayList("selected");if(selected!=null)s.selected.addAll(selected);
                ArrayList<String> exports=state.getStringArrayList("batch_export_lessons");if(exports!=null)for(String value:exports)try{s.pendingExport.add(ClassroomLesson.fromJson(new JSONObject(value)));}catch(Exception ignored){}
                s.exportLayout=state.getBoolean("batch_export_by_course",false)?ClassroomExport.Layout.BY_COURSE:ClassroomExport.Layout.FLAT;
                String uri=state.getString("batch_export_uri");if(uri!=null)s.exportUri=Uri.parse(uri);
                String share=state.getString("batch_share_uri");if(share!=null)s.shareUri=Uri.parse(share);
                s.week = LocalDate.parse(state.getString("week", s.week.toString()));
            }
            else s.mode=Math.max(0,Math.min(3,getIntent().getIntExtra("classroom_mode",0)));
        }
        if (s != null) s.owner = new WeakReference<>(this);
        CollapsingPageLayout root = SettingsPageLayout.workspaceContent(activity);
        root.setPadding(dp(20), dp(UiStyle.PAGE_TOP_DP), dp(20), dp(12));
        navigation = new DockNavigationLayout(activity, this::selectMode, () -> { });
        navigation.setPadding(dp(6), dp(6), dp(6), dp(6));
        String[] modes = MODES;
        for (int i = 0; i < modes.length; i++) {
            final int index = i;
            TextView item = label(modes[i], 15); item.setGravity(Gravity.CENTER); UiStyle.title(item);
            item.setOnClickListener(view -> { selectMode(index); navigation.finishPageSelection(index); });
            navigation.addItem(item, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        refresh = WorkspacePageHeader.icon(activity, R.drawable.ic_refresh, "刷新课堂", () -> {
            if (s != null) { if (s.refreshing) s.cancel(); else if(!s.busy) s.load(Load.REFRESH); }
        });
        monitorSettings = WorkspacePageHeader.icon(activity, R.drawable.ic_settings, "监视运行设置",
                () -> ClassroomMonitorUi.runtimeSettings(activity));
        headerActions = new HeaderActionGroup(activity, 48);
        headerActions.addAction(refresh, 48); headerActions.addAction(monitorSettings, 48); headerActions.finish();
        FrameLayout dockHost=navigation.headerMotionHost();
        compactMode=new TextView(activity);UiStyle.fieldTrigger(compactMode);
        compactMode.setOnClickListener(v->{if(s!=null)UiDialogs.choiceDialog(activity,"课堂",MODES,s.mode,this::selectMode);});
        FrameLayout adaptive=new FrameLayout(activity){@Override protected void onMeasure(int w,int h){
            float widest=0;for(String label:MODES)widest=Math.max(widest,compactMode.getPaint().measureText(label));
            boolean compact=View.MeasureSpec.getSize(w)<dp(12)+Math.ceil(widest)*MODES.length;
            dockHost.setVisibility(compact?View.GONE:View.VISIBLE);compactMode.setVisibility(compact?View.VISIBLE:View.GONE);super.onMeasure(w,h);
        }};adaptive.setClipChildren(false);adaptive.setClipToPadding(false);adaptive.addView(dockHost,new FrameLayout.LayoutParams(-1,-2));adaptive.addView(compactMode,new FrameLayout.LayoutParams(-1,-2));
        UiStyle.addSpaced(root, new WorkspacePageHeader(activity, adaptive, headerActions, LinearLayout.LayoutParams.WRAP_CONTENT), 0, 8);
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
        LinearLayout browserTools = new LinearLayout(activity); browserTools.setOrientation(LinearLayout.VERTICAL);
        toolsViewport = SettingsPageLayout.toolsViewport(activity,browserTools);
        toolsViewport.setClipToPadding(false); toolsViewport.setClipChildren(false);
        browserTools.setClipToPadding(false); browserTools.setClipChildren(false);
        PressFeedback.protectViewport(toolsViewport);
        // Real space inside the protected scrolling bounds carries the rail's press envelope.
        toolsViewport.setPadding(dp(20), dp(4), dp(20), dp(4));
        LinearLayout.LayoutParams toolsParams = new LinearLayout.LayoutParams(-1, -2);
        toolsParams.setMargins(-dp(20), -dp(4), -dp(20), -dp(4));
        root.addView(toolsViewport, toolsParams);
        search=new EditText(activity);UiStyle.input(search);search.setSingleLine(true);search.setHint("搜索课堂");search.setContentDescription("搜索课堂");
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
        if(s!=null)search.setText(s.query);
        UiStyle.addSpaced(browserTools,search,0,8);
        search.addTextChangedListener(new TextWatcher(){
            public void beforeTextChanged(CharSequence text,int start,int count,int after){}
            public void onTextChanged(CharSequence text,int start,int before,int count){if(s==null)return;s.query=text.toString();clearSelection();s.page=0;s.scrollY=0;update();listScroll.scrollTo(0,0);}
            public void afterTextChanged(Editable text){}
        });
        historyCourse=new TextView(activity);UiStyle.fieldTrigger(historyCourse,true);historyCourse.setOnClickListener(v->pickHistoryCourse());
        historyTools=new LinearLayout(activity);historyTools.setGravity(Gravity.CENTER_VERTICAL);
        historyTools.addView(historyCourse,new LinearLayout.LayoutParams(0,-2,1));
        historyOrder=new TextView(activity);UiStyle.fieldTrigger(historyOrder,true);historyOrder.setOnClickListener(v->{if(s!=null)UiDialogs.choiceDialog(activity,"排序与筛选",HISTORY_ORDERS,s.historyOrder,index->{if(s.historyOrder!=index){s.historyOrder=index;clearSelection();resetListOrder();}});});
        LinearLayout.LayoutParams orderParams=new LinearLayout.LayoutParams(0,-2,1);orderParams.leftMargin=dp(8);historyTools.addView(historyOrder,orderParams);
        historyDirection=UiStyle.navigationIcon(activity,R.drawable.ic_arrow_down,"切换排序方向",()->{if(s!=null){s.historyAscending=!s.historyAscending;resetListOrder();}});
        LinearLayout.LayoutParams directionParams=new LinearLayout.LayoutParams(dp(48),dp(48));directionParams.leftMargin=dp(8);historyTools.addView(historyDirection,directionParams);UiStyle.addSpaced(browserTools,historyTools,0,8);
        pageBar = new DateIntervalBar(activity, this::pickPage, this::changePage);
        pageBar.getChildAt(1).setContentDescription("上一页");
        pageBar.getChildAt(2).setContentDescription("下一页");
        UiStyle.addSpaced(browserTools, pageBar, 0, 8);
        batchStatus=label("",14);batchStatus.setOnClickListener(v->showBatchResult());UiStyle.addSpaced(browserTools,batchStatus,0,4);
        buildSelectionBar();
        content = new LinearLayout(activity); content.setOrientation(LinearLayout.VERTICAL);
        scroll = listScroll = SettingsPageLayout.motionViewport(activity, content);
        bodyHost = new FrameLayout(activity);
        bodyHost.addView(listScroll, new FrameLayout.LayoutParams(-1, -1));
        weekViewport = new FrameLayout(activity) {
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                return weekTransition || super.dispatchTouchEvent(event);
            }
        };
        PressFeedback.protectViewport(weekViewport);
        FrameLayout.LayoutParams weekParams=new FrameLayout.LayoutParams(-1,-1);weekParams.leftMargin=weekParams.rightMargin=dp(20);
        bodyHost.addView(weekViewport, weekParams);
        FrameLayout transitionHost = new FrameLayout(activity);
        PressFeedback.protectViewport(transitionHost);
        transitionHost.addView(bodyHost, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout.LayoutParams bodyParams=new LinearLayout.LayoutParams(-1,0,1);bodyParams.leftMargin=bodyParams.rightMargin=-dp(20);
        root.addView(transitionHost,bodyParams); root.setScrollingBody(transitionHost); setContentView(root);
        update();
        if (s != null) scroll.post(() -> scroll.scrollTo(0, s.scrollY));
    }
    private void selectMode(int index) {
        if (s == null || s.mode == index) return;
        int direction = Integer.compare(index, s.mode);
        if (weekPicker != null) s.weekScrollX = weekPicker.getScrollX();
        clearSelection();
        s.mode = index; s.page = 0; update(direction); scheduleRefresh();
        if(!s.busy)s.load(Load.CACHE);
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
        update(0);
    }
    private void update(int direction) {
        if (direction != 0 && active && bodyHost != null && bodyHost.isAttachedToWindow())
            UiMotion.swap(bodyHost, bodyHost, this::updateContents, direction);
        else updateContents();
    }
    private void updateContents() {
        if (content == null) return;
        if (s == null || !s.allowed()) {
            finishWeekMotion();
            weekBar.setVisibility(View.GONE);
            weekViewport.setVisibility(View.GONE); listScroll.setVisibility(View.VISIBLE); scroll = listScroll;
            refresh.setEnabled(false);
            monitorSettings.setEnabled(false);
            headerActions.setActionVisible(monitorSettings,false);
            status.setVisibility(View.GONE);
            search.setVisibility(View.GONE);historyTools.setVisibility(View.GONE);historyCourse.setVisibility(View.GONE);toolsViewport.setVisibility(View.GONE);
            if (!gated) {
                gated = true; browseBody = null; content.removeAllViews();
                button(content, "设置学校账号", () -> startActivity(new Intent(activity, ZjuSettingsActivity.class)));
            }
            return;
        }
        gated = false;
        toolsViewport.setVisibility(s.mode == 1 || s.mode == 3 ? View.VISIBLE : View.GONE);
        pageBar.setVisibility((s.mode == 1 || s.mode == 3) && totalPages > 1 ? View.VISIBLE : View.GONE);
        search.setVisibility(s.mode==1?View.VISIBLE:View.GONE);historyTools.setVisibility(s.mode==3?View.VISIBLE:View.GONE);
        historyCourse.setVisibility(s.mode==3?View.VISIBLE:View.GONE);historyCourse.setText(historyCourseTitle());historyCourse.setContentDescription("历史课程："+historyCourseTitle());
        updateBatchStatus();
        historyOrder.setText(HISTORY_ORDERS[s.historyOrder]);historyOrder.setContentDescription("排序："+HISTORY_ORDERS[s.historyOrder]);
        historyDirection.setRotation(s.historyAscending?180f:0f);
        historyDirection.setContentDescription(s.historyAscending?"当前升序，切换为降序":"当前降序，切换为升序");historyDirection.setTooltipText(s.historyAscending?"升序":"降序");
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
        compactMode.setText(MODES[s.mode]);compactMode.setContentDescription("课堂视图："+MODES[s.mode]+"，选择视图");
        refresh.setEnabled(true);
        monitorSettings.setEnabled(s.mode==2);
        headerActions.setActionVisible(monitorSettings,s.mode==2);
        refresh.setImageResource(s.refreshing ? R.drawable.ic_x : R.drawable.ic_refresh);
        refresh.setContentDescription(s.refreshing ? "停止刷新" : "刷新课堂");
        refresh.setTooltipText(s.refreshing ? "停止刷新" : "刷新课堂");
        if (s.mode == 0) weekBar.setText(s.week + " — " + s.week.plusDays(6));
        if (weekTransition && s.mode == 0) return;
        boolean changed = shownMode != s.mode || shownPage!=s.page || shownCatalog != s.catalog || shownHistory != s.history || s.mode==2&&shownMonitoring!=s.monitoring || !s.week.equals(shownWeek)
                ||s.mode==1&&!shownQuery.equals(s.query)||s.mode==3&&(!shownHistoryCourse.equals(s.historyCourse)||shownHistoryOrder!=s.historyOrder||shownHistoryAscending!=s.historyAscending);
        if (changed) {
            if (s.mode == 0 && shownMode == 0 && s.week.equals(shownWeek) && weekPicker != null
                    && weekPicker.refreshLessons(s.catalog)) {
                shownCatalog = s.catalog; shownHistory = s.history; shownPage = s.page;
                return;
            }
            boolean animateWeek = s.mode == 0 && shownMode == 0 && weekPicker != null
                    && !s.week.equals(shownWeek) && active && UiMotion.isEnabled();
            LocalDate previousWeek = shownWeek;
            int oldScroll=scroll.getScrollY();
            boolean retainScroll=s.mode!=0&&shownMode==s.mode&&shownPage==s.page
                    &&(s.mode!=1||shownQuery.equals(s.query))&&(s.mode!=3||shownHistoryOrder==s.historyOrder&&shownHistoryAscending==s.historyAscending);
            if (s.mode != 0) { finishWeekMotion(); browseBody.removeAllViews(); }
            listBody = null;
            shownMode = s.mode; shownPage=s.page; shownCatalog = s.catalog; shownHistory = s.history; shownMonitoring=s.monitoring; shownWeek = s.week;
            shownQuery=s.query;shownHistoryOrder=s.historyOrder;shownHistoryAscending=s.historyAscending;
            shownHistoryCourse=s.historyCourse;
            if (s.mode == 0) {
                showWeek(animateWeek, previousWeek);
            } else {
                renderPage();
                if(retainScroll)listScroll.post(()->listScroll.scrollTo(0,oldScroll));
            }
        }
        updateSelectionUi();
        if(active&&s.shareUri!=null&&!s.localWork){Uri uri=s.shareUri;s.shareUri=null;ExportShare.show(activity,uri,"application/zip");}
        if(s.exportUri!=null&&!s.localWork)writeBatchExport();
    }
    private void showWeek(boolean animate, LocalDate previousWeek) {
        ClassroomLessonPickerView next = new ClassroomLessonPickerView(activity, s.catalog, s.week, this::open);
        next.gestures(direction -> { if (s != null && s.mode == 0) setWeek(s.week.plusWeeks(direction)); }, null);
        ClassroomLessonPickerView old = weekPicker;
        int x = old == null ? s.weekScrollX : old.getScrollX();
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
            selectionMarkers.clear();matchingLessons=new ArrayList<>();
            listBody=new LinearLayout(activity);listBody.setOrientation(LinearLayout.VERTICAL);browseBody.addView(listBody);
            if(s.mode==2){ClassroomMonitorUi.overview(activity,listBody,s.monitoring);return;}
            List<ClassroomLesson> recent = new ArrayList<>();
            java.util.Set<String> saved=new java.util.HashSet<>();
            for(JSONObject record:s.history){JSONObject lesson=record.optJSONObject("lesson");
                if(lesson!=null)saved.add(lesson.optString("course_id")+"/"+lesson.optString("id"));}
            for (ClassroomLesson lesson : s.catalog) if (lesson.startsAt <= System.currentTimeMillis()
                    && !saved.contains(lesson.courseId+"/"+lesson.id)&&matchesQuery(lesson,s.query)) recent.add(lesson);
            recent.sort((a,b) -> Long.compare(b.startsAt, a.startsAt));
            List<JSONObject> orderedHistory=new ArrayList<>();for(JSONObject record:s.history){JSONObject lesson=record.optJSONObject("lesson");if(lesson!=null&&(s.historyCourse.isEmpty()||s.historyCourse.equals(lesson.optString("course_id")))&&(s.historyOrder!=2||!record.optBoolean("complete")))orderedHistory.add(record);}orderedHistory.sort(historyComparator(s.historyOrder==2?0:s.historyOrder,s.historyAscending));
            int count=s.mode==1?recent.size():orderedHistory.size(),pages=Math.max(1,(count+PAGE_SIZE-1)/PAGE_SIZE);
            if(s.mode==1)matchingLessons.addAll(recent);else for(JSONObject record:orderedHistory)try{matchingLessons.add(ClassroomLesson.fromJson(record.getJSONObject("lesson")));}catch(Exception ignored){}
            if(s.initialized){java.util.Set<String> valid=new java.util.HashSet<>();for(ClassroomLesson lesson:matchingLessons)valid.add(ClassroomBatchWork.key(lesson));s.selected.retainAll(valid);}
            addResultsHeading(count);
            s.page=Math.min(s.page,pages-1);shownPage=s.page;
            for (int i = s.page*PAGE_SIZE; i < Math.min((s.page+1)*PAGE_SIZE,count); i++) {
              if(s.mode==1){ClassroomLesson lesson = recent.get(i);
                lessonRow(lesson,lesson.date()+" "+clock(lesson)+" · "+lesson.state(System.currentTimeMillis())+"\n"+lesson.course+" · "+lesson.title,false);
              }else try{JSONObject record=orderedHistory.get(i);ClassroomLesson lesson=ClassroomLesson.fromJson(record.getJSONObject("lesson"));
                lessonRow(lesson,lesson.course+"\n"+lesson.date()+" · "+(record.optBoolean("complete")?"已完成":record.optBoolean("downloaded")?"已下载":"未完成"),true);
              }catch(Exception ignored){}
            }
            if(count==0)listBody.addView(label(s.mode==1?(s.query.trim().isEmpty()?"暂无课堂":"未找到课堂"):"暂无历史记录",16));
            totalPages = pages;
            pageBar.setText((s.page + 1) + " / " + pages);
            pageBar.setContentDescription("第" + (s.page + 1) + "页，共" + pages + "页，左右滑动翻页，点击选择页码");
            pageBar.getChildAt(1).setEnabled(s.page > 0);
            pageBar.getChildAt(2).setEnabled(s.page + 1 < pages);
            pageBar.setVisibility(pages > 1 ? View.VISIBLE : View.GONE);
            updateSelectionUi();
    }
    static boolean matchesQuery(ClassroomLesson lesson,String query){
        String value=java.text.Normalizer.normalize(query,java.text.Normalizer.Form.NFKC).trim().toLowerCase(java.util.Locale.ROOT);
        String text=lesson.course+"\n"+lesson.title+"\n"+lesson.room+"\n"+lesson.date()+" "+clock(lesson);
        return java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC).toLowerCase(java.util.Locale.ROOT).contains(value);
    }
    private void buildSelectionBar(){
        selectionBar=new LinearLayout(activity){@Override protected void onMeasure(int width,int height){
            if(s!=null&&s.selecting){
                selectionCount.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED),height);int wanted=selectionCount.getMeasuredWidth();
                for(Button control:new Button[]{selectAll,batchDownload,batchExport,batchDelete}){
                    control.measure(View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED),height);wanted+=control.getMeasuredWidth()+dp(6);
                }
                boolean compact=wanted>View.MeasureSpec.getSize(width);
                batchDownload.setVisibility(compact?View.GONE:View.VISIBLE);batchExport.setVisibility(compact?View.GONE:View.VISIBLE);batchDelete.setVisibility(compact?View.GONE:View.VISIBLE);batchMenu.setVisibility(compact?View.VISIBLE:View.GONE);
            }super.onMeasure(width,height);
        }};
        selectionBar.setGravity(Gravity.CENTER_VERTICAL);selectionBar.setClipChildren(false);selectionBar.setClipToPadding(false);
        selectionCount=label("0/0",12);selectionCount.setSingleLine(true);selectionBar.addView(selectionCount);
        selectAll=BatchSelectionUi.action(activity,"全选",this::toggleAll);
        batchDownload=BatchSelectionUi.action(activity,"总结",this::downloadSelected);batchDownload.setContentDescription("下载并总结所选课堂");
        batchExport=BatchSelectionUi.action(activity,"导出",this::exportSelected);
        batchDelete=BatchSelectionUi.action(activity,"删除",this::confirmDeleteSelected);
        batchMenu=BatchSelectionUi.action(activity,"操作",this::chooseBatchAction);
        for(Button control:new Button[]{selectAll,batchDownload,batchExport,batchDelete,batchMenu})selectionBar.addView(control);
        selectionBar.setVisibility(View.GONE);
    }
    private void addResultsHeading(int count){
        LinearLayout heading=new LinearLayout(activity);heading.setGravity(Gravity.CENTER_VERTICAL);heading.setClipChildren(false);heading.setClipToPadding(false);
        resultsHeading=heading;
        resultsCount=label("共 "+count+" 节课堂",19);UiStyle.title(resultsCount);resultsCount.setSingleLine(true);resultsCount.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int height=BatchSelectionUi.headingHeight(resultsCount);heading.addView(resultsCount,new LinearLayout.LayoutParams(0,height,1));
        LinearLayout selectionHeading=new LinearLayout(activity);selectionHeading.setClipChildren(false);selectionHeading.setClipToPadding(false);
        BatchSelectionUi.attach(selectionHeading,selectionBar,height,selectAll,batchDownload,batchExport,batchDelete,batchMenu);
        ((LinearLayout.LayoutParams)selectionBar.getLayoutParams()).width=-1;
        FrameLayout chrome=new FrameLayout(activity){@Override public boolean onInterceptTouchEvent(android.view.MotionEvent event){return s==null||!s.selecting&&selectionBar.getVisibility()==View.VISIBLE||super.onInterceptTouchEvent(event);}};
        chrome.setClipChildren(false);chrome.setClipToPadding(false);
        chrome.addView(heading,new FrameLayout.LayoutParams(-1,height));chrome.addView(selectionHeading,new FrameLayout.LayoutParams(-1,height));
        UiStyle.addSpaced(listBody,chrome,0,4);
    }
    private void lessonRow(ClassroomLesson lesson,String text,boolean history){
        LinearLayout row=new LinearLayout(activity);row.setGravity(Gravity.CENTER_VERTICAL);row.setPadding(dp(16),dp(12),dp(16),dp(12));UiStyle.card(row);UiStyle.pressable(row);
        TextView title=label(text,16);UiStyle.title(title);row.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        TextView marker=label("",22);row.addView(marker,new LinearLayout.LayoutParams(dp(34),dp(34)));selectionMarkers.put(ClassroomBatchWork.key(lesson),marker);
        row.setContentDescription(text);
        row.setOnClickListener(v->{if(s!=null&&s.selecting)toggleSelection(lesson);else if(history)openSaved(lesson);else open(lesson);});
        row.setOnLongClickListener(v->{if(s!=null){s.selecting=true;s.selected.add(ClassroomBatchWork.key(lesson));updateSelectionUi();}return true;});
        UiStyle.addSpaced(listBody,row,4,6);
    }
    private void clearSelection(){if(s!=null){s.selecting=false;s.selected.clear();}}
    @Override boolean hasBackAction(){return s!=null&&s.selecting;}
    @Override boolean handleBack(){if(!hasBackAction())return false;clearSelection();updateSelectionUi();return true;}
    private void toggleSelection(ClassroomLesson lesson){String key=ClassroomBatchWork.key(lesson);if(!s.selected.remove(key))s.selected.add(key);updateSelectionUi();}
    private void toggleAll(){
        if(s==null)return;java.util.Set<String> keys=new LinkedHashSet<>();for(ClassroomLesson lesson:matchingLessons)keys.add(ClassroomBatchWork.key(lesson));
        if(!keys.isEmpty()&&s.selected.containsAll(keys))s.selected.removeAll(keys);else s.selected.addAll(keys);updateSelectionUi();
    }
    private void updateSelectionUi(){
        updateBackAction();
        if(s==null||selectionBar==null||resultsCount==null||s.mode!=1&&s.mode!=3)return;
        boolean selecting=s.selecting;resultsCount.setVisibility(selecting?View.GONE:View.VISIBLE);
        BatchSelectionUi.visible(selectionBar,selecting,resultsHeading);
        selectionCount.setText(s.selected.size()+"/"+matchingLessons.size());selectionCount.setContentDescription("已选 "+s.selected.size()+" 节，共 "+matchingLessons.size()+" 节");
        boolean all=!matchingLessons.isEmpty();for(ClassroomLesson lesson:matchingLessons)all&=s.selected.contains(ClassroomBatchWork.key(lesson));
        selectAll.setText(all?"取消":"全选");selectAll.setContentDescription(all?"取消本筛选的全部选择":"选中本筛选的全部课堂，包含其它分页");
        ClassroomBatchWork running=ClassroomBatchWork.find(s.account);boolean enabled=!s.localWork&&(running==null||!running.running)&&!s.selected.isEmpty();
        for(Button action:new Button[]{batchDownload,batchExport,batchDelete})action.setEnabled(enabled);
        batchMenu.setEnabled(enabled);
        for(java.util.Map.Entry<String,TextView> marker:selectionMarkers.entrySet()){boolean selected=s.selected.contains(marker.getKey());BatchSelectionUi.marker(marker.getValue(),selecting,selected);((View)marker.getValue().getParent()).setSelected(selected);}
        selectionBar.requestLayout();
    }
    private LinkedHashMap<String,ClassroomLesson> allLessons(){
        LinkedHashMap<String,ClassroomLesson> lessons=new LinkedHashMap<>();if(s==null)return lessons;
        for(ClassroomLesson lesson:s.catalog)lessons.put(ClassroomBatchWork.key(lesson),lesson);
        for(JSONObject record:s.history)try{ClassroomLesson lesson=ClassroomLesson.fromJson(record.getJSONObject("lesson"));lessons.put(ClassroomBatchWork.key(lesson),lesson);}catch(Exception ignored){}
        return lessons;
    }
    private List<ClassroomLesson> selectionSnapshot(){List<ClassroomLesson> result=new ArrayList<>();if(s!=null)for(ClassroomLesson lesson:allLessons().values())if(s.selected.contains(ClassroomBatchWork.key(lesson)))result.add(lesson);return result;}
    private void chooseBatchAction(){
        if(s==null)return;ClassroomBatchWork batch=ClassroomBatchWork.find(s.account);boolean actionable=!s.selected.isEmpty()&&!s.localWork&&(batch==null||!batch.running);
        if(!actionable)return;
        String[] actions=new String[]{"总结","导出 ZIP","删除本地资料"};
        UiDialogs.choiceDialog(activity,"已选 "+s.selected.size()+" 节课堂",actions,-1,index->{if(index==0)downloadSelected();else if(index==1)exportSelected();else confirmDeleteSelected();});
    }
    private String historyCourseTitle(){
        if(s==null||s.historyCourse.isEmpty())return "全部课程";
        return historyCourseLabels(s.history).getOrDefault(s.historyCourse,"课程（"+s.historyCourse+"）");
    }
    static LinkedHashMap<String,String> historyCourseLabels(List<JSONObject> history){
        LinkedHashMap<String,String> names=new LinkedHashMap<>();
        for(JSONObject record:history){JSONObject lesson=record.optJSONObject("lesson");if(lesson!=null&&!lesson.optString("course_id").isEmpty())names.putIfAbsent(lesson.optString("course_id"),lesson.optString("course","课程"));}
        java.util.Map<String,Integer> counts=new java.util.HashMap<>();for(String name:names.values())counts.put(name,counts.getOrDefault(name,0)+1);
        LinkedHashMap<String,String> labels=new LinkedHashMap<>();labels.put("","全部课程");
        for(java.util.Map.Entry<String,String> course:names.entrySet())labels.put(course.getKey(),counts.get(course.getValue())>1?course.getValue()+"（"+course.getKey()+"）":course.getValue());return labels;
    }
    private void pickHistoryCourse(){
        if(s==null)return;LinkedHashMap<String,String> courses=historyCourseLabels(s.history);
        List<String> ids=new ArrayList<>(courses.keySet());String[] titles=courses.values().toArray(new String[0]);
        UiDialogs.choiceDialog(activity,"历史课程",titles,ids.indexOf(s.historyCourse),index->{String value=ids.get(index);if(!s.historyCourse.equals(value)){s.historyCourse=value;clearSelection();resetListOrder();}});
    }
    private boolean canStartBatch(){
        if(s==null||!s.allowed()||s.localWork)return false;
        ClassroomBatchWork batch=ClassroomBatchWork.find(s.account);if(batch!=null&&batch.running){Feedback.show(activity,"已有批量总结正在处理");return false;}return true;
    }
    private void downloadSelected(){
        if(!canStartBatch())return;List<ClassroomLesson> snapshot=selectionSnapshot();if(snapshot.isEmpty())return;
        Session owner=s;owner.localWork=true;updateSelectionUi();
        owner.worker.execute(()->{
            LinkedHashSet<String> retry=new LinkedHashSet<>();String error="";
            try{ClassroomStore store=owner.storeForBatch();JSONObject monitor=new ClassroomMonitorStore(owner.app,owner.account).read();for(ClassroomLesson lesson:snapshot){File dir=store.lesson(lesson);JSONObject summary=ClassroomStore.read(new File(dir,"summary.json")),automatic=ClassroomMonitorStore.auto(monitor,lesson);if(ClassroomAutoSummary.needsAuthorization(summary,ClassroomStore.read(new File(dir,"run.json")))||(summary==null||!summary.optBoolean("complete"))&&automatic!=null&&java.util.Set.of("failed","interrupted").contains(automatic.optString("state")))retry.add(ClassroomBatchWork.key(lesson));}}
            catch(Exception failure){error="无法读取课堂资料";}
            java.util.Set<String> retries=java.util.Set.copyOf(retry);String failure=error;
            owner.main.post(()->{owner.localWork=false;if(s!=owner||!owner.allowed()||isDestroyed())return;updateSelectionUi();
                if(!failure.isEmpty()){Feedback.show(activity,failure);return;}
                Runnable start=()->{if(s!=owner||!canStartBatch())return;try{ClassroomNotifications.requestPermission(activity);owner.operationMessage="";ClassroomBatchWork.start(getApplicationContext(),owner.account,snapshot,retries);clearSelection();updateBatchStatus();updateSelectionUi();scheduleRefresh();}catch(Exception unavailable){Feedback.show(activity,"无法启动课堂总结");}};
                if(!retries.isEmpty())UiDialogs.confirmDialog(activity,"继续总结 "+retries.size()+" 节课堂","将使用当前 AI 服务继续未完成的总结。","总结",start);else start.run();
            });
        });
    }
    private void confirmDeleteSelected(){
        if(!canStartBatch())return;List<ClassroomLesson> snapshot=selectionSnapshot();if(snapshot.isEmpty())return;
        Session owner=s;
        UiDialogs.confirmDialog(activity,"删除 "+snapshot.size()+" 节本地课堂资料","将删除已选课堂的总结、字幕和图片。学校原课堂及后续课堂监视保留。","删除",()->{
            if(s!=owner||!owner.allowed()||!canStartBatch())return;owner.localWork=true;owner.operationMessage="正在删除 0/"+snapshot.size();update();
            owner.worker.execute(()->{
                LinkedHashSet<String> removed=new LinkedHashSet<>();StringBuilder failures=new StringBuilder();
                for(ClassroomLesson lesson:snapshot){try{
                    owner.storeForBatch().deleteLesson(lesson,()->{checkBatchLesson(owner,lesson);new ClassroomMonitorStore(owner.app,owner.account).deleted(lesson);});removed.add(ClassroomBatchWork.key(lesson));
                }catch(Exception failure){failures.append("\n").append(lesson.course).append(" · ").append(lesson.date()).append(": ").append(failure.getMessage()==null?"删除失败":failure.getMessage());}}
                String report="删除完成 · 成功 "+removed.size()+" · 失败 "+(snapshot.size()-removed.size())+failures;
                owner.main.post(()->{owner.localWork=false;owner.operationMessage=report;owner.selected.removeAll(removed);if(owner.allowed())owner.load(Load.LOCAL);owner.publish();});
            });
        });
    }
    private static void checkBatchLesson(Session owner,ClassroomLesson lesson)throws Exception {
        if(!owner.allowed())throw new IOException("学校账号已停用或更换");
        if(ClassroomWorkService.find(owner.account,lesson)!=null||ClassroomLiveTranscripts.active(owner.account,lesson))throw new IOException("本节课堂正在处理，已跳过");
    }
    private void exportSelected(){
        if(!canStartBatch())return;Session owner=s;List<ClassroomLesson> snapshot=selectionSnapshot();if(snapshot.isEmpty())return;
        if(snapshot.size()==1){chooseExport(owner,snapshot,ClassroomExport.Layout.FLAT);return;}
        UiDialogs.choiceDialog(activity,"导出方式",new String[]{"平铺","层级"},owner.exportLayout==ClassroomExport.Layout.BY_COURSE?1:0,
                index->chooseExport(owner,snapshot,index==1?ClassroomExport.Layout.BY_COURSE:ClassroomExport.Layout.FLAT));
    }
    private void chooseExport(Session owner,List<ClassroomLesson> snapshot,ClassroomExport.Layout layout){
        if(s!=owner||!canStartBatch())return;owner.pendingExport.clear();owner.pendingExport.addAll(snapshot);owner.exportLayout=layout;
        String name=snapshot.size()==1?ClassroomExport.fileName(snapshot.get(0)):"课堂资料-"+LocalDate.now(ClassroomLesson.ZONE)+".zip";
        Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("application/zip").putExtra(Intent.EXTRA_TITLE,name);
        try{startActivityForResult(intent,BATCH_EXPORT);}catch(Exception failure){s.pendingExport.clear();Feedback.show(activity,"无法打开文件保存器");}
    }
    @Override protected void onActivityResult(int request,int result,Intent data){
        if(request!=BATCH_EXPORT)return;
        if(s==null||!s.allowed())return;
        if(result!=RESULT_OK||data==null||data.getData()==null){s.pendingExport.clear();return;}
        s.exportUri=data.getData();boolean read=(data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION)!=0,write=(data.getFlags()&Intent.FLAG_GRANT_WRITE_URI_PERMISSION)!=0;
        try{if(read&&write)getContentResolver().takePersistableUriPermission(s.exportUri,Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            else if(read)getContentResolver().takePersistableUriPermission(s.exportUri,Intent.FLAG_GRANT_READ_URI_PERMISSION);
            else if(write)getContentResolver().takePersistableUriPermission(s.exportUri,Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        }catch(SecurityException ignored){}
        writeBatchExport();
    }
    private void writeBatchExport(){
        if(s==null||!s.allowed()||s.localWork||s.exportUri==null)return;
        Session owner=s;Uri uri=owner.exportUri;owner.exportUri=null;List<ClassroomLesson> snapshot=new ArrayList<>(owner.pendingExport);ClassroomExport.Layout layout=owner.exportLayout;owner.pendingExport.clear();if(snapshot.isEmpty())return;
        owner.localWork=true;owner.operationMessage="正在导出 0/"+snapshot.size();updateBatchStatus();updateSelectionUi();
        owner.worker.execute(()->{
            try{
                List<ClassroomExport.LessonFiles> files=new ArrayList<>();ClassroomStore store=owner.storeForBatch();for(ClassroomLesson lesson:snapshot)files.add(new ClassroomExport.LessonFiles(lesson,store.lesson(lesson),ClassroomExportTiming.endsAt(owner.app,owner.account,lesson)));
                ClassroomExport.BatchResult result;
                try(OutputStream output=owner.app.getContentResolver().openOutputStream(uri,"w")){
                    if(output==null)throw new IOException("无法写入文件");
                    result=ClassroomExport.writeBatch(files,layout,output,value->{owner.operationMessage="正在导出 · "+value.text();owner.publish();},lesson->checkBatchLesson(owner,lesson),file->{
                        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;android.graphics.BitmapFactory.decodeFile(file.getPath(),bounds);
                        if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outMimeType==null||!bounds.outMimeType.startsWith("image/"))throw new IOException("课堂图片格式无效");
                        return android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(bounds.outMimeType);
                    });
                }
                StringBuilder report=new StringBuilder(result.text());for(java.util.Map.Entry<String,String> failure:result.failures.entrySet())report.append("\n").append(failure.getKey()).append(": ").append(failure.getValue());owner.operationMessage=report.toString();
                if(result.success>0&&owner.allowed())owner.shareUri=uri;
            }catch(Exception failure){owner.operationMessage="导出失败："+(failure.getMessage()==null?"无法写入 ZIP":failure.getMessage());}
            finally{owner.main.post(()->{owner.localWork=false;owner.publish();});}
        });
    }
    private void updateBatchStatus(){
        if(s==null||batchStatus==null)return;ClassroomBatchWork batch=ClassroomBatchWork.find(s.account);
        String message=s.operationMessage;if(message.isEmpty()&&batch!=null)message=batch.text();String first=message.split("\n",2)[0];
        batchStatus.setText(first);batchStatus.setVisibility(first.isEmpty()?View.GONE:View.VISIBLE);batchStatus.setContentDescription(first+"，查看处理结果");
    }
    private void showBatchResult(){
        if(s==null)return;ClassroomBatchWork batch=ClassroomBatchWork.find(s.account);
        if(batch!=null&&batch.running){UiDialogs.confirmDialog(activity,"批量总结",batch.details(),"停止总结",()->{batch.cancel("已取消");updateBatchStatus();scheduleRefresh();});return;}
        String result=!s.operationMessage.isEmpty()?s.operationMessage:batch==null?"":batch.details();if(result.isEmpty())return;
        android.app.Dialog dialog=new android.app.Dialog(activity);LinearLayout panel=UiDialogs.dialogPanel(activity,"批量处理结果");LinearLayout body=new LinearLayout(activity);body.setOrientation(LinearLayout.VERTICAL);body.addView(label(result,15));button(body,"关闭",dialog::dismiss);UiDialogs.dialogBody(panel,body);UiDialogs.showFloatingDialog(dialog,panel);
    }
    /** history() returns summary.json unchanged; downloaded-only rows reuse 'updated' for material time. */
    private static long summaryTime(JSONObject record){return record.optBoolean("downloaded")?0:Math.max(0,record.optLong("updated"));}
    static java.util.Comparator<JSONObject> historyComparator(int order,boolean ascending){
        return (a,b)->{
            JSONObject left=a.optJSONObject("lesson"),right=b.optJSONObject("lesson");
            long leftStart=left==null?0:left.optLong("starts_at"),rightStart=right==null?0:right.optLong("starts_at");
            long l=order==0?leftStart:summaryTime(a),r=order==0?rightStart:summaryTime(b);
            if(order==1&&(l==0||r==0)&&l!=r)return l==0?1:-1;
            int result=ascending?Long.compare(l,r):Long.compare(r,l);if(result!=0)return result;
            result=Long.compare(leftStart,rightStart);if(result!=0)return result;
            String leftKey=left==null?"":left.optString("course_id")+"/"+left.optString("id"),rightKey=right==null?"":right.optString("course_id")+"/"+right.optString("id");
            return leftKey.compareTo(rightKey);
        };
    }
    private void resetListOrder(){s.page=0;s.scrollY=0;update();listScroll.post(()->listScroll.scrollTo(0,0));}
    private boolean changePage(int delta) {
        if (s == null || s.mode != 1 && s.mode != 3) return false;
        return selectPage(s.page + delta);
    }
    private boolean selectPage(int page) {
        if (s == null || page < 0 || page >= totalPages || page == s.page) return false;
        int direction = Integer.compare(page, s.page);
        s.page = page; s.scrollY = 0; update(direction);
        listScroll.post(() -> listScroll.scrollTo(0, 0));
        return true;
    }
    private void pickPage() {
        if (s == null || totalPages < 2) return;
        String[] pages = new String[totalPages];
        for (int i = 0; i < pages.length; i++) pages[i] = "第 " + (i + 1) + " 页";
        UiDialogs.choiceDialog(activity, "选择页码", pages, s.page, this::selectPage);
    }
    private void open(ClassroomLesson lesson) {
        if (s == null || !s.allowed()) return;
        if(lesson.selectable(System.currentTimeMillis()))openSaved(lesson);
        else ClassroomMonitorUi.open(activity,lesson);
    }
    private void openSaved(ClassroomLesson lesson) {
        if(s == null || !s.allowed())return;
        try { startActivity(new Intent(activity, ClassroomLessonActivity.class)
                .putExtra(ClassroomLessonActivity.EXTRA_LESSON, lesson.json().toString())); }
        catch (Exception error) { Feedback.show(activity, "无法打开课堂：" + error.getMessage()); }
    }
    private static String clock(ClassroomLesson lesson) {
        return Instant.ofEpochMilli(lesson.startsAt).atZone(ClassroomLesson.ZONE).toLocalTime().toString();
    }
    private void scheduleRefresh() {
        refreshHandler.removeCallbacks(refreshTick);
        if (!active || s == null || !s.allowed()) return;
        long now = System.currentTimeMillis(), delay = STATUS_REFRESH_MILLIS;
        ClassroomBatchWork batch=ClassroomBatchWork.find(s.account);if(batch!=null&&batch.running)delay=1000;
        if(s.mode==0)for (ClassroomLesson lesson : s.catalog)
            if (lesson.startsAt > now) delay = Math.min(delay, lesson.startsAt - now);
        if (s.mode==0&&!s.busy) delay = Math.min(delay, Math.max(1, s.nextRemoteAt - android.os.SystemClock.elapsedRealtime()));
        refreshHandler.postDelayed(refreshTick, Math.max(1, delay));
    }
    @Override protected void onResume() {
        active = true; settings.prefs.registerOnSharedPreferenceChangeListener(preference); checkAccount();
        if (firstPresentation) bodyHost.post(() -> {
            if (!isDestroyed() && active && firstPresentation) {
                UiMotion.enterPage(bodyHost,()->firstPresentation=false);
            }
        });
        if (s != null && s.initialized && !s.busy) s.load(Load.CACHE);
        if (weekPicker != null) weekPicker.refreshStates(System.currentTimeMillis());
        scheduleRefresh();
    }
    @Override protected void onPause() {
        active = false; if (s != null) s.scrollY = scroll.getScrollY();
        if (s != null && weekPicker != null) s.weekScrollX = weekPicker.getScrollX();
        UiMotion.finishSwap(bodyHost);
        refreshHandler.removeCallbacks(refreshTick);
        finishWeekMotion(); update();
        settings.prefs.unregisterOnSharedPreferenceChangeListener(preference);
    }
    @Override protected void onDestroy() {
        UiMotion.finishSwap(bodyHost);
        refreshHandler.removeCallbacks(refreshTick);
        if (s != null) { if (isFinishing() || !s.allowed()) s.dispose(); else if (s.owner.get() == this) s.owner.clear(); }
        super.onDestroy();
    }
    @Override public Object onRetainNonConfigurationInstance() { return s != null && s.allowed() ? s : null; }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putBoolean("first_presentation",firstPresentation);
        if (s == null) return;
        state.putInt("mode", s.mode); state.putInt("page", s.page); state.putString("week", s.week.toString());
        state.putInt("scroll_y", scroll.getScrollY());
        state.putInt("week_scroll_x", weekPicker == null ? s.weekScrollX : weekPicker.getScrollX());
        state.putString("query",s.query);state.putInt("history_order",s.historyOrder);state.putBoolean("history_ascending",s.historyAscending);
        state.putString("history_course",s.historyCourse);state.putBoolean("selecting",s.selecting);state.putStringArrayList("selected",new ArrayList<>(s.selected));
        ArrayList<String> exports=new ArrayList<>();for(ClassroomLesson lesson:s.pendingExport)try{exports.add(lesson.json().toString());}catch(Exception ignored){}state.putStringArrayList("batch_export_lessons",exports);
        state.putBoolean("batch_export_by_course",s.exportLayout==ClassroomExport.Layout.BY_COURSE);
        if(s.exportUri!=null)state.putString("batch_export_uri",s.exportUri.toString());if(s.shareUri!=null)state.putString("batch_share_uri",s.shareUri.toString());
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
