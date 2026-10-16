package com.donglan.chrona;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Inbox's calendar destination, using the same local occurrences as schedule detail. */
public final class CalendarOverviewActivity extends Activity {
    private static final int YEAR = 0, MONTH = 1, WEEK = 2, DAY = 3, AGENDA = 4;
    private static final int LIGHT_DAY_MAX = 2, MEDIUM_DAY_MAX = 5;
    private LocalDate date = LocalDate.now();
    private int mode = MONTH, generation;
    private boolean includeSystem;
    private String query = "";
    private final java.util.concurrent.ThreadPoolExecutor reader = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.MILLISECONDS, new java.util.concurrent.LinkedBlockingQueue<>());
    private java.util.concurrent.Future<?> readFuture;
    private android.os.CancellationSignal readCancellation;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AgendaRepository.Snapshot snapshot = new AgendaRepository.Snapshot();
    private LinearLayout root;
    private DockNavigationLayout tabs;
    private int presentationGeneration;
    private boolean bodyTransitionRunning, loadingStale, bodyTouchActive;
    private final CalendarSwipe swipe = new CalendarSwipe();
    private float swipeStartX;
    private boolean animateNext;
    private int transitionDirection;
    private FrameLayout body;
    private FrameLayout bodyStage;
    private FrameLayout bodyViewport;
    private FrameLayout outgoingBody;
    private android.animation.ValueAnimator bodySlide;
    private android.view.ViewTreeObserver bodyObserver;
    private android.view.ViewTreeObserver.OnPreDrawListener bodyPreDraw;
    private boolean pendingBodyRender;
    private final Runnable renderPendingBody = this::renderBody;
    private static final long BUILD_FRAME_BUDGET_NANOS = 2_000_000L;
    private static final int BUILD_FRAME_MAX_STEPS = 16;
    private java.util.ArrayDeque<Runnable> calendarBuildSteps;
    private Runnable calendarBuildDone;
    private final Runnable calendarBuildFrame = this::buildCalendarFrame;
    private void cancelCalendarBuild() {
        if (calendarBuildSteps == null) return;
        body.removeCallbacks(calendarBuildFrame); calendarBuildSteps = null; calendarBuildDone = null;
        renderedKey = "";
    }
    private void enqueueCalendarBuild(Runnable step) {
        if (calendarBuildSteps == null) step.run(); else calendarBuildSteps.add(step);
    }
    private void buildCalendarFrame() {
        if (calendarBuildSteps == null) return;
        long started = System.nanoTime();
        int steps = 0;
        while (!calendarBuildSteps.isEmpty()) {
            calendarBuildSteps.removeFirst().run(); steps++;
            if (steps >= BUILD_FRAME_MAX_STEPS || System.nanoTime() - started >= BUILD_FRAME_BUDGET_NANOS) break;
        }
        if (!calendarBuildSteps.isEmpty()) { body.postOnAnimation(calendarBuildFrame); return; }
        calendarBuildSteps = null;
        Runnable done = calendarBuildDone; calendarBuildDone = null;
        if (done != null) done.run();
    }
    private ScrollView verticalViewport;
    private HorizontalScrollView horizontalViewport;
    private final java.util.LinkedHashMap<String, int[]> positions = new java.util.LinkedHashMap<>(16, .75f, true);
    private String renderedKey = "", renderedQuery = "";
    private LocalDate renderedDate, renderedToday;
    private int renderedMode;
    private boolean renderedSystem;
    private CalendarDayCounts dayCounts;
    private View monthDates;
    private LinearLayout monthDetails;
    private long lastScrollAt;
    private AgendaRepository.Snapshot deferredSnapshot;
    private String deferredKey;
    private int deferredToken;
    private final Runnable deferredApply = this::applyDeferred;
    private final List<EntryStatus> statuses = new ArrayList<>();
    private static final class EntryStatus {
        final AgendaEntry entry;
        final TextView title, time;
        EntryStatus(AgendaEntry entry, TextView title, TextView time) { this.entry = entry; this.title = title; this.time = time; }
    }
    private TextView dateTitle;
    private ImageButton weekExportButton;
    private static final int CREATE_WEEK_IMAGE = 811;
    private static final java.util.concurrent.ExecutorService EXPORTS = java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean exportingWeek;
    private float weekZoom = 1f;
    private String weekExportName;
    private android.widget.Switch sourceSwitch;
    private View timeLines;
    private android.app.Dialog allDayDialog;
    private boolean resumed;
    private boolean loading;
    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            if (timeLines != null) timeLines.invalidate();
            if (!LocalDate.now().equals(renderedToday) && !loading) renderBody();
            else for (EntryStatus status : statuses) updateStatus(status);
            handler.removeCallbacks(this);
            if (resumed) handler.postDelayed(this, nextTickDelay());
        }
    };
    private android.database.ContentObserver calendarObserver;
    private volatile int providerRevision;
    private final java.util.LinkedHashMap<String, AgendaRepository.Snapshot> snapshotCache = new java.util.LinkedHashMap<>(4, .75f, true);
    private final Runnable calendarReload = this::load;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state);
        includeSystem = CalendarSourcePreferences.includesSystem(this);
        mode = getPreferences(MODE_PRIVATE).getInt("mode", MONTH);
        if (getIntent().getBooleanExtra("open_week", false) && state == null) { mode = WEEK; date = LocalDate.now(); }
        if (state != null) {
            weekZoom = state.getFloat("week_zoom", 1f);
            weekExportName = state.getString("week_export_name");
            date = LocalDate.parse(state.getString("date", date.toString()));
            mode = state.getInt("mode", mode); query = state.getString("query", "");
            String[] keys = state.getStringArray("position_keys");
            int[] xs = state.getIntArray("position_x"), ys = state.getIntArray("position_y");
            if (keys != null && xs != null && ys != null && keys.length == xs.length && keys.length == ys.length)
                for (int i = 0; i < Math.min(12, keys.length); i++) positions.put(keys[i], new int[]{xs[i], ys[i]});
        }
        root = column(); root.setPadding(dp(20), dp(12), dp(20), 0); UiStyle.page(this, root);
        root.setClipChildren(false); root.setClipToPadding(false);
        root.setFitsSystemWindows(false); root.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout header = row();
        header.addView(icon(R.drawable.ic_arrow_left, "返回", this::finish), size(44, 44));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, dp(44), 1);
        searchParams.setMargins(dp(8), 0, dp(8), 0); header.addView(searchField(), searchParams);
        weekExportButton = icon(R.drawable.ic_week_export, "导出周日程为图片", this::exportWeek);
        weekExportButton.setVisibility(mode == WEEK ? View.VISIBLE : View.GONE);
        header.addView(weekExportButton, size(44, 44));
        header.addView(icon(R.drawable.ic_add, "记录新日程", () -> startActivity(new Intent(this, MainActivity.class))), size(44, 44));
        root.addView(header);
        LinearLayout navigation = row();
        navigation.addView(icon(R.drawable.ic_chevron_left, "上一段日期", () -> shift(-1)), size(40, 44));
        dateTitle = text("", 22, true); dateTitle.setGravity(Gravity.CENTER);
        dateTitle.setTextColor(UiStyle.colors(this).primary); dateTitle.setOnClickListener(v -> pickDate());
        dateTitle.setContentDescription("选择日期");
        dateTitle.setMaxLines(1); dateTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        dateTitle.setAutoSizeTextTypeUniformWithConfiguration(14, 22, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        navigation.addView(dateTitle, new LinearLayout.LayoutParams(0, dp(48), 1));
        navigation.addView(icon(R.drawable.ic_chevron_right, "下一段日期", () -> shift(1)), size(40, 44));
        TextView today = action("今天", () -> navigateDate(LocalDate.now()));
        navigation.addView(today, new LinearLayout.LayoutParams(-2, dp(44))); root.addView(navigation);
        tabs = new DockNavigationLayout(this, this::changeMode, () -> { });
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        String[] labels = {"年", "月", "周", "日", "日程"};
        for (int i = 0; i < labels.length; i++) {
            int target = i;
            TextView tab = text(labels[i], 14, true); tab.setGravity(Gravity.CENTER);
            tab.setOnClickListener(v -> changeMode(target));
            tabs.addItem(tab, new LinearLayout.LayoutParams(0, dp(42), 1));
        }
        tabs.setSelectedIndex(mode);
        updateTabSemantics();
        // Leave room for a held edge highlight and the plate's scale, on narrow windows too.
        int dockWidth = Math.min(dp(280), Math.round(getResources().getDisplayMetrics().widthPixels * .70f));
        LinearLayout.LayoutParams tabParams = new LinearLayout.LayoutParams(dockWidth, -2);
        tabParams.gravity = Gravity.CENTER_HORIZONTAL; tabParams.topMargin = dp(4); tabParams.bottomMargin = dp(10);
        root.addView(tabs, tabParams);
        sourceSwitch = new android.widget.Switch(this);
        sourceSwitch.setText("包含系统日程"); sourceSwitch.setTextSize(14); sourceSwitch.setChecked(includeSystem);
        sourceSwitch.setPadding(dp(6), 0, dp(6), 0); sourceSwitch.setSwitchPadding(dp(12)); UiStyle.toggle(sourceSwitch);
        sourceSwitch.setOnCheckedChangeListener((button, enabled) -> {
            if (enabled == includeSystem) return;
            if (enabled && !new com.donglan.chrona.calendar.CalendarStore(this).hasReadPermission()) {
                sourceSwitch.setChecked(false);
                requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, 810); return;
            }
            includeSystem = enabled; CalendarSourcePreferences.setIncludesSystem(this, enabled); load();
        });
        UiStyle.addSpaced(root, sourceSwitch, 0, 6);
        bodyStage = new FrameLayout(this);
        body = createCalendarBody();
        bodyViewport = new FrameLayout(this);
        PressFeedback.protectViewport(bodyViewport);
        bodyViewport.addView(body, new FrameLayout.LayoutParams(-1, -1));
        bodyStage.addView(bodyViewport, new FrameLayout.LayoutParams(-1, -1));
        root.addView(bodyStage, new LinearLayout.LayoutParams(-1, 0, 1));
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(root, new FrameLayout.LayoutParams(-1, -1)); UiStyle.applyInsets(stage, root); setContentView(stage);
    }

    private FrameLayout createCalendarBody() {
        FrameLayout page = new FrameLayout(this) {
            @Override public boolean dispatchTouchEvent(android.view.MotionEvent event) {
                int action = event.getActionMasked();
                if (action == android.view.MotionEvent.ACTION_DOWN) {
                    bodyTouchActive = true; swipeStartX = event.getX();
                    swipe.begin(event.getX(), event.getY(), mode != WEEK && !loading && !bodyTransitionRunning);
                }
                if (action == android.view.MotionEvent.ACTION_POINTER_DOWN || action == android.view.MotionEvent.ACTION_CANCEL) swipe.cancel();
                if (action == android.view.MotionEvent.ACTION_MOVE && !swipe.claimed()) {
                    boolean childCanScroll = horizontalViewport != null
                            && horizontalViewport.canScrollHorizontally(event.getX() < swipeStartX ? 1 : -1);
                    if (swipe.move(event.getX(), event.getY(), android.view.ViewConfiguration.get(CalendarOverviewActivity.this).getScaledTouchSlop(), childCanScroll)) {
                        android.view.MotionEvent cancel = android.view.MotionEvent.obtain(event);
                        cancel.setAction(android.view.MotionEvent.ACTION_CANCEL);
                        super.dispatchTouchEvent(cancel); cancel.recycle();
                    }
                }
                if (swipe.claimed()) {
                    if (action == android.view.MotionEvent.ACTION_UP || action == android.view.MotionEvent.ACTION_CANCEL) {
                        int direction = swipe.finish(event.getX(), event.getY(), Math.max(dp(48), getWidth() * .15f));
                        bodyTouchActive = false;
                        handler.removeCallbacks(deferredApply); handler.postDelayed(deferredApply, 120);
                        if (direction != 0 && (horizontalViewport == null || !horizontalViewport.canScrollHorizontally(direction))) shift(direction);
                    }
                    return true;
                }
                boolean handled = super.dispatchTouchEvent(event);
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_UP || event.getActionMasked() == android.view.MotionEvent.ACTION_CANCEL) {
                    bodyTouchActive = false; handler.removeCallbacks(deferredApply); handler.postDelayed(deferredApply, 120);
                }
                return handled;
            }
            @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) {
                return loadingStale || bodyTransitionRunning || super.onInterceptTouchEvent(event);
            }
            // A temporary input gate exposes no click action; active child controls retain theirs.
            @android.annotation.SuppressLint("ClickableViewAccessibility")
            @Override public boolean onTouchEvent(android.view.MotionEvent event) {
                return loadingStale || bodyTransitionRunning || super.onTouchEvent(event);
            }
        };
        page.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public boolean onRequestSendAccessibilityEvent(android.view.ViewGroup host, View child,
                    android.view.accessibility.AccessibilityEvent event) {
                if (event.getEventType() == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    finishBodyMotion();
                return super.onRequestSendAccessibilityEvent(host, child, event);
            }
        });
        return page;
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true;
        if (includeSystem) { providerRevision++; synchronized (snapshotCache) { snapshotCache.clear(); } }
        includeSystem = CalendarSourcePreferences.includesSystem(this);
        sourceSwitch.setChecked(includeSystem);
        load(); handler.post(minuteTick);
        if (new com.donglan.chrona.calendar.CalendarStore(this).hasReadPermission()) {
            calendarObserver = new android.database.ContentObserver(handler) {
                @Override public void onChange(boolean selfChange) {
                    providerRevision++; synchronized (snapshotCache) { snapshotCache.clear(); }
                    handler.removeCallbacks(calendarReload); handler.postDelayed(calendarReload, 400);
                }
            };
            try { getContentResolver().registerContentObserver(android.provider.CalendarContract.CONTENT_URI, true, calendarObserver); }
            catch (SecurityException ignored) { calendarObserver = null; }
        }
    }
    @Override protected void onPause() {
        resumed = false; handler.removeCallbacks(minuteTick); handler.removeCallbacks(calendarReload);
        handler.removeCallbacks(renderPendingBody); pendingBodyRender = false;
        rememberPosition(); cancelRead(); swipe.cancel(); bodyTouchActive = false;
        if (allDayDialog != null) { allDayDialog.dismiss(); allDayDialog = null; }
        finishTabMotion(); finishBodyMotion(); animateNext = false;
        if (calendarObserver != null) { getContentResolver().unregisterContentObserver(calendarObserver); calendarObserver = null; }
        super.onPause();
    }
    @Override protected void onDestroy() { cancelRead(); reader.shutdownNow(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("date", date.toString()); state.putInt("mode", mode); state.putString("query", query);
        state.putString("week_export_name", weekExportName);
        state.putFloat("week_zoom", weekZoom);
        rememberPosition();
        String[] keys = positions.keySet().toArray(new String[0]); int[] xs = new int[keys.length], ys = new int[keys.length];
        for (int i = 0; i < keys.length; i++) { int[] position = positions.get(keys[i]); xs[i] = position[0]; ys[i] = position[1]; }
        state.putStringArray("position_keys", keys); state.putIntArray("position_x", xs); state.putIntArray("position_y", ys);
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 810) {
            includeSystem = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            CalendarSourcePreferences.setIncludesSystem(this, includeSystem); load();
            if (!includeSystem) Feedback.show(this, "未获得读取日历权限");
        }
    }
    private void pickDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
            navigateDate(LocalDate.of(year, month + 1, day));
        }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()); dialog.show();
        if (dialog.getWindow() != null) PressFeedback.install(dialog.getWindow().getDecorView());
    }
    private void shift(int direction) {
        date = mode == YEAR ? date.plusYears(direction) : mode == WEEK ? date.plusWeeks(direction)
                : mode == DAY ? date.plusDays(direction) : date.plusMonths(direction);
        animatedLoad(direction);
    }

    private void navigateDate(LocalDate target) {
        if (selectMonthDay(target)) return;
        int direction = target.compareTo(date);
        date = target; animatedLoad(Integer.signum(direction));
    }

    /** Keep the existing month grid, scroll host and focused date cells when only selection changes. */
    private boolean selectMonthDay(LocalDate target) {
        if (mode != MONTH || renderedMode != MONTH || loadingStale || monthDates == null || monthDetails == null
                || !renderedKey.equals(windowKey(snapshot.zone))
                || !java.time.YearMonth.from(target).equals(java.time.YearMonth.from(renderedDate))) return false;
        if (date.equals(target)) return true;
        rememberPosition();
        LocalDate previous = date; date = target; renderedDate = target;
        updateHeader(); updateMonthCell(previous); updateMonthCell(target);
        updateMonthDetails(matching()); restorePosition();
        handler.removeCallbacks(minuteTick);
        if (resumed) handler.postDelayed(minuteTick, nextTickDelay());
        return true;
    }
    private void updateMonthCell(LocalDate day) {
        View cell = monthDates.findViewWithTag("day:" + day);
        if (!(cell instanceof FrameLayout frame)) return;
        boolean selected = day.equals(date), today = day.equals(LocalDate.now());
        ((TextView) frame.getChildAt(0)).setTextColor(selected || today ? UiStyle.colors(this).primary : UiStyle.colors(this).text);
        calendarSurface(frame, selected, 15, selected || today); frame.setSelected(selected);
        frame.setContentDescription(day + (today ? "，今天" : "") + (selected ? "，已选中" : "") + "，本月，" + dayCounts.on(day) + "项日程");
    }
    private void updateMonthDetails(List<AgendaEntry> entries) {
        monthDetails.removeAllViews(); statuses.clear();
        TextView selected = text(date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)), 17, true);
        UiStyle.addSpaced(monthDetails, selected, 16, 8); dayList(monthDetails, entries, date);
    }

    private void changeMode(int target) {
        if (mode == target) return;
        int direction = Integer.compare(target, mode);
        mode = target;
        getPreferences(MODE_PRIVATE).edit().putInt("mode", mode).apply();
        moveTabHighlight(); animatedLoad(direction);
    }

    private void moveTabHighlight() {
        updateTabSemantics();
        tabs.finishPageSelection(mode);
    }

    private void finishTabMotion() {
        tabs.finishPageSelection(mode);
        updateTabSemantics();
    }

    private void updateTabSemantics() {
        for (int i = 0; i < tabs.itemCount(); i++) {
            View tab = tabs.itemAt(i); tab.setSelected(i == mode);
            tab.setContentDescription(((TextView) tab).getText() + "视图" + (i == mode ? "，已选中" : ""));
        }
    }

    private void animatedLoad(int direction) {
        animateNext = resumed; transitionDirection = direction; load();
    }

    private void finishBodyMotion() {
        cancelCalendarBuild();
        presentationGeneration++; bodyTransitionRunning = false;
        if (bodyPreDraw != null && bodyObserver != null && bodyObserver.isAlive())
            bodyObserver.removeOnPreDrawListener(bodyPreDraw);
        bodyPreDraw = null; bodyObserver = null;
        android.animation.ValueAnimator old = bodySlide; bodySlide = null;
        if (old != null) old.cancel();
        body.setVisibility(View.VISIBLE); body.setTranslationX(0f);
        if (outgoingBody != null) {
            outgoingBody.setVisibility(View.INVISIBLE);
            bodyViewport.removeView(outgoingBody); outgoingBody = null;
        }
        if (pendingBodyRender) { pendingBodyRender = false; if (resumed) handler.post(renderPendingBody); }
    }
    private void prepareBodySlide(int presentation, int direction) {
        // Wait for measured scroll extents before restoring offsets and exposing either page.
        body.post(() -> {
            if (presentation != presentationGeneration || outgoingBody == null || !resumed) return;
            bodyObserver = body.getViewTreeObserver();
            boolean[] restored = {false};
            bodyPreDraw = () -> {
                if (presentation != presentationGeneration || bodyObserver == null || bodyPreDraw == null) return true;
                // Keep drawing the outgoing page until all queued layout work has settled.
                if (body.isLayoutRequested()) { bodyViewport.postInvalidateOnAnimation(); return true; }
                if (!restored[0]) {
                    restored[0] = true;
                    int[] position = positions.get(renderedKey);
                    if (position != null) {
                        if (verticalViewport != null) verticalViewport.scrollTo(0, position[1]);
                        if (horizontalViewport != null) horizontalViewport.scrollTo(position[0], 0);
                    }
                }
                // Restoring scroll may request another layout; do not expose that first frame.
                if (body.isLayoutRequested()) { bodyViewport.postInvalidateOnAnimation(); return true; }
                if (bodyObserver.isAlive()) bodyObserver.removeOnPreDrawListener(bodyPreDraw);
                bodyPreDraw = null; bodyObserver = null;
                startBodySlide(presentation, direction);
                return true;
            };
            // The incoming body is INVISIBLE until the slide starts; invalidating it
            // can be ignored once the Dock's animation has stopped drawing frames.
            bodyObserver.addOnPreDrawListener(bodyPreDraw); bodyViewport.postInvalidateOnAnimation();
        });
    }
    private void startBodySlide(int presentation, int direction) {
        if (outgoingBody == null) return;
        float width = bodyViewport.getWidth();
        if (!UiMotion.isEnabled() || width <= 0 || body.hasFocus()) { finishBodyMotion(); return; }
        body.setTranslationX(CalendarSlideMotion.incoming(width, direction, 0f));
        outgoingBody.setTranslationX(0f);
        // Visibility preserves measurement without allocating an alpha-composited page layer.
        body.setVisibility(View.VISIBLE);
        android.animation.ValueAnimator motion = android.animation.ValueAnimator.ofFloat(0f, 1f);
        bodySlide = motion; motion.setDuration(UiMotion.ENTER); motion.setInterpolator(UiMotion.SETTLE);
        motion.addUpdateListener(value -> {
            if (!UiMotion.isEnabled() || body.hasFocus()) { finishBodyMotion(); return; }
            float progress = (float) value.getAnimatedValue();
            body.setTranslationX(CalendarSlideMotion.incoming(width, direction, progress));
            outgoingBody.setTranslationX(CalendarSlideMotion.outgoing(width, direction, progress));
        });
        motion.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (bodySlide == motion && presentation == presentationGeneration) finishBodyMotion();
            }
        });
        motion.start();
    }
    private LocalDate windowStart() {
        if (mode == YEAR) return date.withDayOfYear(1);
        if (mode == WEEK) return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (mode == DAY) return date;
        return date.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    private LocalDate windowEnd(LocalDate first) {
        return mode == YEAR ? first.plusYears(1) : mode == WEEK ? first.plusWeeks(1)
                : mode == DAY ? first.plusDays(1) : first.plusDays(42);
    }
    private String windowKey(ZoneId zone) { return mode + ":" + windowStart() + ":" + includeSystem + ":" + zone.getId(); }
    private void rememberPosition() {
        if (renderedKey.isEmpty()) return;
        positions.put(renderedKey, new int[]{horizontalViewport == null ? 0 : horizontalViewport.getScrollX(),
                verticalViewport == null ? 0 : verticalViewport.getScrollY()});
        if (positions.size() > 12) positions.remove(positions.keySet().iterator().next());
    }
    private void restorePosition() {
        ScrollView vertical = verticalViewport; HorizontalScrollView horizontal = horizontalViewport;
        int[] position = positions.get(renderedKey);
        if (position == null) return;
        body.post(() -> {
            if (vertical != verticalViewport || horizontal != horizontalViewport) return;
            if (vertical != null) vertical.scrollTo(0, position[1]);
            if (horizontal != null) horizontal.scrollTo(position[0], 0);
        });
    }
    private void watchScroll(View viewport) {
        viewport.setOnScrollChangeListener((view, x, y, oldX, oldY) -> lastScrollAt = android.os.SystemClock.uptimeMillis());
    }
    private void cancelRead() {
        generation++;
        if (readCancellation != null) readCancellation.cancel();
        if (readFuture != null) readFuture.cancel(true);
        reader.purge(); readCancellation = null; readFuture = null;
        handler.removeCallbacks(deferredApply); deferredSnapshot = null;
        loading = loadingStale = false;
        if (body != null) body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
    }
    private void applyDeferred() {
        if (deferredSnapshot == null || !resumed) return;
        if (bodyTouchActive || (allDayDialog != null && allDayDialog.isShowing())) return;
        if (android.os.SystemClock.uptimeMillis() - lastScrollAt < 120) {
            handler.postDelayed(deferredApply, 120); return;
        }
        AgendaRepository.Snapshot loaded = deferredSnapshot; deferredSnapshot = null;
        applyLoaded(loaded, deferredKey, deferredToken);
    }
    private void applyLoaded(AgendaRepository.Snapshot loaded, String key, int token) {
        if (isDestroyed() || token != generation) return;
        if (renderedKey.equals(key) && snapshot.sameContents(loaded) && query.equals(renderedQuery)
                && date.equals(renderedDate) && LocalDate.now().equals(renderedToday)) {
            snapshot = loaded; loading = loadingStale = false;
            body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO); return;
        }
        if (!loadingStale && (bodyTouchActive || (allDayDialog != null && allDayDialog.isShowing())
                || android.os.SystemClock.uptimeMillis() - lastScrollAt < 120)) {
            deferredSnapshot = loaded; deferredKey = key; deferredToken = token;
            handler.removeCallbacks(deferredApply); handler.postDelayed(deferredApply, 120); return;
        }
        snapshot = loaded; loading = loadingStale = false;
        body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO); renderBody();
    }
    private void load() {
        if (body == null || isDestroyed()) return;
        rememberPosition(); cancelRead();
        int token = generation;
        loading = true;
        LocalDate first = windowStart();
        LocalDate last = windowEnd(first);
        ZoneId zone = ZoneId.systemDefault();
        String key = windowKey(zone);
        loadingStale = !renderedKey.equals(key);
        body.setImportantForAccessibility(loadingStale ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                : View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
        boolean system = includeSystem;
        updateHeader();
        tabs.finishPageSelection(mode);
        if (body.getChildCount() == 0) body.addView(text("正在读取日程…", 14, false));
        android.os.CancellationSignal cancellation = new android.os.CancellationSignal();
        readCancellation = cancellation;
        readFuture = reader.submit(() -> {
            try {
                long revision;
                try (com.donglan.chrona.data.TaskStore store = new com.donglan.chrona.data.TaskStore(getApplicationContext())) { revision = store.dataRevision(); }
                String cacheKey = key + ":" + revision + ":" + providerRevision;
                AgendaRepository.Snapshot loaded;
                synchronized (snapshotCache) { loaded = snapshotCache.get(cacheKey); }
                if (loaded == null) {
                    loaded = AgendaRepository.load(getApplicationContext(),
                            first.atStartOfDay(zone).toInstant().toEpochMilli(), last.atStartOfDay(zone).toInstant().toEpochMilli(), system, zone, cancellation);
                    cancellation.throwIfCanceled();
                    if (!loaded.systemUnavailable) synchronized (snapshotCache) {
                        snapshotCache.put(cacheKey, loaded);
                        while (snapshotCache.size() > 4) snapshotCache.remove(snapshotCache.keySet().iterator().next());
                    }
                }
                AgendaRepository.Snapshot result = loaded;
                handler.post(() -> applyLoaded(result, key, token));
            } catch (android.os.OperationCanceledException cancelled) { /* Superseded or paused request. */ }
            catch (RuntimeException exception) {
                handler.post(() -> {
                    if (isDestroyed() || token != generation) return;
                    loading = loadingStale = false;
                    body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO);
                    finishBodyMotion(); animateNext = false;
                    if (renderedKey.isEmpty()) { body.removeAllViews(); body.addView(action("读取失败，点击重试", this::load)); }
                    else {
                        mode = renderedMode; date = renderedDate; includeSystem = renderedSystem;
                        getPreferences(MODE_PRIVATE).edit().putInt("mode", mode).putBoolean("system", includeSystem).apply();
                        updateHeader(); moveTabHighlight();
                        Feedback.show(this, "读取失败，已保留上次日历");
                    }
                });
            }
        });
    }
    private void updateHeader() {
        dateTitle.setText(mode == YEAR ? date.getYear() + "" : date.format(DateTimeFormatter.ofPattern(mode == DAY ? "yyyy / M / d" : "yyyy / M")));
        dateTitle.setContentDescription(date + "，选择日期"); sourceSwitch.setChecked(includeSystem);
        if (weekExportButton != null) weekExportButton.setVisibility(mode == WEEK ? View.VISIBLE : View.GONE);
    }
    private void exportWeek() {
        if (mode != WEEK || exportingWeek) return;
        if (loading || !renderedKey.equals(windowKey(snapshot.zone))) { Feedback.show(this, "日程仍在读取，请稍候"); return; }
        if (snapshot.truncated || includeSystem && snapshot.systemUnavailable) { Feedback.show(this, "日程尚未完整读取，请缩小范围或重试后导出"); return; }
        android.app.Dialog dialog = new android.app.Dialog(this);
        LinearLayout content = column(); content.setPadding(dp(20), dp(20), dp(20), dp(20));
        UiStyle.addSpaced(content, text("导出周日程", 20, true), 0, 16);
        android.widget.Switch index = new android.widget.Switch(this);
        index.setText("附带日程索引"); index.setTextSize(15); index.setTextColor(UiStyle.colors(this).text);
        index.setMinHeight(dp(48)); index.setChecked(getPreferences(MODE_PRIVATE).getBoolean("week_export_index", false));
        index.setSwitchPadding(dp(16)); UiStyle.toggle(index); content.addView(index, new LinearLayout.LayoutParams(-1, -2));
        UiStyle.addSpaced(content, action("保存图片", () -> {
            boolean includeIndex = index.isChecked();
            getPreferences(MODE_PRIVATE).edit().putBoolean("week_export_index", includeIndex).apply();
            dialog.dismiss(); writeWeekImage(includeIndex);
        }), 16, 0);
        UiStyle.showFloatingDialog(dialog, content);
    }
    private void writeWeekImage(boolean includeIndex) {
        if (mode != WEEK || exportingWeek || loading || !renderedKey.equals(windowKey(snapshot.zone))) return;
        if (snapshot.truncated || includeSystem && snapshot.systemUnavailable) { Feedback.show(this, "日程尚未完整读取，请缩小范围或重试后导出"); return; }
        exportingWeek = true; weekExportButton.setEnabled(false);
        LocalDate first = windowStart(); ZoneId zone = snapshot.zone; List<AgendaEntry> entries = List.copyOf(matching());
        UiStyle.Palette palette = UiStyle.colors(this); String search = query; boolean system = includeSystem;
        android.content.Context app = getApplicationContext(); Handler responses = handler;
        java.lang.ref.WeakReference<CalendarOverviewActivity> owner = new java.lang.ref.WeakReference<>(this);
        EXPORTS.execute(() -> {
            try {
                java.io.File image = WeekAgendaImage.write(app.getCacheDir(), first, zone, entries, palette, search, system, includeIndex);
                responses.post(() -> {
                    CalendarOverviewActivity screen = owner.get();
                    if (screen == null || screen.isDestroyed() || screen.isFinishing()) { image.delete(); return; }
                    screen.exportingWeek = false; screen.weekExportButton.setEnabled(true); screen.weekExportName = image.getName();
                    Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/png")
                            .putExtra(Intent.EXTRA_TITLE, "Chrona-周日程-" + first + ".png");
                    try { screen.startActivityForResult(save, CREATE_WEEK_IMAGE); }
                    catch (android.content.ActivityNotFoundException error) { image.delete(); screen.weekExportName = null; Feedback.show(screen, "未找到文件保存应用"); }
                });
            } catch (Exception | OutOfMemoryError error) {
                responses.post(() -> {
                    CalendarOverviewActivity screen = owner.get(); if (screen == null || screen.isDestroyed()) return;
                    screen.exportingWeek = false; screen.weekExportButton.setEnabled(true);
                    Feedback.showLong(screen, error instanceof OutOfMemoryError ? "图片过大，请缩小日程范围后导出" : "导出失败：" + error.getMessage());
                });
            }
        });
    }
    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != CREATE_WEEK_IMAGE) return;
        String name = weekExportName; weekExportName = null;
        if (name == null || !name.matches("week-export-[A-Za-z0-9-]+\\.png")) return;
        java.io.File image = new java.io.File(getCacheDir(), name);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) { image.delete(); return; }
        android.net.Uri destination = data.getData(); android.content.Context app = getApplicationContext();
        java.lang.ref.WeakReference<CalendarOverviewActivity> owner = new java.lang.ref.WeakReference<>(this);
        EXPORTS.execute(() -> {
            try (java.io.InputStream input = new java.io.FileInputStream(image);
                    java.io.OutputStream output = app.getContentResolver().openOutputStream(destination, "wt")) {
                if (output == null) throw new java.io.IOException("无法打开保存位置");
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                output.flush(); new Handler(Looper.getMainLooper()).post(() -> {
                    CalendarOverviewActivity screen = owner.get(); if (screen != null && !screen.isDestroyed()) Feedback.show(screen, "周日程图片已保存");
                });
            } catch (Exception error) {
                new Handler(Looper.getMainLooper()).post(() -> {
                    CalendarOverviewActivity screen = owner.get(); if (screen != null && !screen.isDestroyed()) Feedback.showLong(screen, "保存失败：" + error.getMessage());
                });
            } finally { image.delete(); }
        });
    }
    private List<AgendaEntry> matching() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<AgendaEntry> entries = new ArrayList<>();
        for (AgendaEntry e : snapshot.entries) if (needle.isEmpty()
                || (e.title + " " + e.location + " " + e.description + " " + e.source).toLowerCase(Locale.ROOT).contains(needle)) entries.add(e);
        return entries;
    }
    private void renderBody() {
        if (body == null || loading) return;
        handler.removeCallbacks(renderPendingBody);
        if (bodyTransitionRunning) { pendingBodyRender = true; return; }
        rememberPosition();
        View focus = body.findFocus(); String focusTag = focus != null && focus.getTag() instanceof String ? (String) focus.getTag() : null;
        View accessible = accessibilityFocus(body);
        String accessibleTag = accessible != null && accessible.getTag() instanceof String ? (String) accessible.getTag() : null;
        boolean animated = animateNext && resumed && UiMotion.isEnabled() && body.getChildCount() > 0
                && focus == null && accessible == null;
        animateNext = false;
        int presentation = ++presentationGeneration;
        int direction = transitionDirection == 0 ? 1 : transitionDirection;
        Runnable rebuild = () -> {
            body.setTranslationX(animated ? direction * Math.max(bodyViewport.getWidth(), getResources().getDisplayMetrics().widthPixels) : 0);
            buildBody();
        };
        if (animated) {
            // Keep the outgoing native subtree. No synchronous software screenshot or
            // bitmap allocation on the release frame, and never stack interrupted pages.
            outgoingBody = body;
            outgoingBody.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            body = createCalendarBody();
            bodyViewport.addView(body, new FrameLayout.LayoutParams(-1, -1));
            body.setVisibility(View.INVISIBLE); bodyTransitionRunning = true;
            calendarBuildSteps = new java.util.ArrayDeque<>();
            rebuild.run();
            calendarBuildDone = () -> prepareBodySlide(presentation, direction);
            if (calendarBuildSteps.isEmpty()) {
                calendarBuildSteps = null; Runnable done = calendarBuildDone; calendarBuildDone = null; done.run();
            } else body.postOnAnimation(calendarBuildFrame);
        } else { finishBodyMotion(); rebuild.run(); restorePosition(); }
        View presented = body.getChildAt(0);
        if (focusTag != null || accessibleTag != null) body.post(() -> {
            if (presented != body.getChildAt(0)) return;
            View target = body.findViewWithTag(accessibleTag == null ? focusTag : accessibleTag);
            if (target == null || !resumed) return;
            if (accessibleTag != null) target.performAccessibilityAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS, null);
            else target.requestFocus();
        });
        handler.removeCallbacks(minuteTick);
        if (resumed) handler.postDelayed(minuteTick, nextTickDelay());
    }
    private View accessibilityFocus(View view) {
        if (view.isAccessibilityFocused()) return view;
        if (view instanceof android.view.ViewGroup group) for (int i = 0; i < group.getChildCount(); i++) {
            View result = accessibilityFocus(group.getChildAt(i)); if (result != null) return result;
        }
        return null;
    }
    private void buildBody() {
        body.removeAllViews(); timeLines = null; verticalViewport = null; horizontalViewport = null; statuses.clear();
        monthDates = null; monthDetails = null;
        renderedKey = windowKey(snapshot.zone); renderedQuery = query; renderedDate = date; renderedToday = LocalDate.now();
        renderedMode = mode; renderedSystem = includeSystem; updateHeader();
        List<AgendaEntry> entries = matching();
        LocalDate first = windowStart(); dayCounts = new CalendarDayCounts(entries, first, windowEnd(first), snapshot.zone);
        if (mode == WEEK || mode == DAY) { timeline(entries); return; }
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false);
        verticalViewport = scroll; watchScroll(scroll);
        int edge = PressFeedback.edgeSpace(body.getWidth() > 0 ? body.getWidth()
                : getResources().getDisplayMetrics().widthPixels);
        LinearLayout content = column(); content.setPadding(edge, 0, edge, dp(24)); scroll.addView(content);
        if (snapshot.systemUnavailable) notice(content, "系统日程暂时无法读取");
        if (snapshot.truncated) notice(content, "日程数量较多，请缩小日期范围");
        if (mode == YEAR) {
            for (int pair = 0; pair < 6; pair++) {
                final int monthPair = pair;
                final int year = date.getYear();
                enqueueCalendarBuild(() -> {
                LinearLayout months = row(); months.setGravity(Gravity.TOP);
                for (int offset = 0; offset < 2; offset++) {
                    LocalDate month = LocalDate.of(year, monthPair * 2 + offset + 1, 1);
                    LinearLayout tile = column(); tile.setPadding(dp(8), dp(10), dp(8), dp(10)); calendarSurface(tile, false, UiStyle.RADIUS_PANEL, true);
                    TextView label = text(month.getMonthValue() + "月", 17, true); label.setTextColor(UiStyle.colors(this).primary); tile.addView(label);
                    tile.addView(new YearMonthGridView(this, month, date, dayCounts, day -> { date = day; changeMode(MONTH); }));
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); params.setMargins(0, 0, offset == 0 ? dp(8) : 0, 0);
                    months.addView(tile, params);
                }
                UiStyle.addSpaced(content, months, 0, 8);
                });
            }
        } else if (mode == MONTH) {
            monthDates = monthGrid(date.withDayOfMonth(1), entries, false); content.addView(monthDates);
            monthDetails = column(); content.addView(monthDetails); updateMonthDetails(entries);
        } else {
            LocalDate month = date.withDayOfMonth(1);
            int shown = 0;
            for (LocalDate day = month; day.isBefore(month.plusMonths(1)); day = day.plusDays(1)) {
                List<AgendaEntry> items = onDay(entries, day);
                if (items.isEmpty()) continue; shown++;
                TextView heading = text(day.format(DateTimeFormatter.ofPattern("M / d  EEEE", Locale.CHINA)), 21, true);
                if (day.equals(LocalDate.now())) heading.setTextColor(UiStyle.colors(this).primary);
                UiStyle.addSpaced(content, heading, 16, 10); dayList(content, items, day);
            }
            if (shown == 0) notice(content, "本月暂无日程");
        }
        body.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
    }
    private View monthGrid(LocalDate month, List<AgendaEntry> entries, boolean mini) {
        LinearLayout grid = column();
        LinearLayout week = row(); String[] days = {"一", "二", "三", "四", "五", "六", "日"};
        for (String day : days) { TextView label = text(day, mini ? 10 : 13, false); label.setGravity(Gravity.CENTER); week.addView(label, new LinearLayout.LayoutParams(0, dp(mini ? 24 : 32), 1)); }
        grid.addView(week);
        LocalDate first = month.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int r = 0; r < 6; r++) {
            final int weekRow = r;
            LinearLayout line = row();
            grid.addView(line);
            for (int c = 0; c < 7; c++) {
                final LocalDate day = first.plusDays(weekRow * 7L + c);
                enqueueCalendarBuild(() -> {
                boolean ownMonth = day.getMonth() == month.getMonth();
                int count = dayCounts.on(day);
                FrameLayout cell = new FrameLayout(this);
                TextView number = text("" + day.getDayOfMonth(), mini ? 12 : 18, day.equals(LocalDate.now()));
                number.setGravity(Gravity.CENTER);
                number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                if (!ownMonth) number.setTextColor(UiStyle.colors(this).muted);
                else if (day.equals(date) || day.equals(LocalDate.now())) number.setTextColor(UiStyle.colors(this).primary);
                int markerHeight = mini ? dp(4) : Math.round(android.util.TypedValue.applyDimension(
                        android.util.TypedValue.COMPLEX_UNIT_SP, 11, getResources().getDisplayMetrics())) + dp(3);
                FrameLayout.LayoutParams numberParams = new FrameLayout.LayoutParams(-1, -1);
                numberParams.bottomMargin = markerHeight; cell.addView(number, numberParams);
                if (!mini) {
                    calendarSurface(cell, day.equals(date) && ownMonth, 15,
                            ownMonth && (day.equals(date) || day.equals(LocalDate.now())));
                    cell.setAlpha(ownMonth ? 1f : .65f);
                }
                if (count > 0) {
                    int color = count <= LIGHT_DAY_MAX ? UiStyle.colors(this).primary
                            : count <= MEDIUM_DAY_MAX ? (ThemeStore.dark(this) ? 0xFFE8BD79 : 0xFF9B742C)
                            : (ThemeStore.dark(this) ? 0xFFF2A69B : 0xFFAA6058);
                    LinearLayout marker = row(); marker.setGravity(Gravity.CENTER);
                    View dot = new View(this); android.graphics.drawable.GradientDrawable dotShape = new android.graphics.drawable.GradientDrawable();
                    dotShape.setShape(android.graphics.drawable.GradientDrawable.OVAL); dotShape.setColor(color); dot.setBackground(dotShape);
                    marker.addView(dot, new LinearLayout.LayoutParams(dp(3), dp(3)));
                    if (!mini) {
                        TextView countLabel = text(count > 99 ? "99+" : "" + count, 11, false); countLabel.setTextColor(color);
                        countLabel.setPadding(dp(5), 0, 0, 0); marker.addView(countLabel);
                    }
                    marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
                    cell.addView(marker, new FrameLayout.LayoutParams(-1, markerHeight, Gravity.BOTTOM));
                }
                cell.setTag("day:" + day); cell.setSelected(day.equals(date));
                cell.setContentDescription(day + (day.equals(LocalDate.now()) ? "，今天" : "")
                        + (day.equals(date) ? "，已选中" : "") + (ownMonth ? "，本月" : "，相邻月份") + "，" + count + "项日程");
                cell.setFocusable(true);
                cell.setOnClickListener(v -> {
                    if (mini) { date = day; changeMode(MONTH); }
                    else navigateDate(day);
                });
                int cellHeight = Math.max(dp(mini ? 25 : 56), Math.round(number.getTextSize()) + markerHeight + dp(mini ? 4 : 16));
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, cellHeight, 1);
                if (!mini) cellParams.setMargins(dp(2), dp(2), dp(2), dp(2));
                line.addView(cell, cellParams);
                });
            }
        }
        return grid;
    }
    private List<AgendaEntry> onDay(List<AgendaEntry> entries, LocalDate day) {
        ZoneId zone = snapshot.zone;
        long begin = day.atStartOfDay(zone).toInstant().toEpochMilli(), end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        List<AgendaEntry> matches = new ArrayList<>();
        for (AgendaEntry e : entries) if (e.overlaps(begin, end)) matches.add(e);
        return matches;
    }
    private void dayList(LinearLayout content, List<AgendaEntry> entries, LocalDate day) {
        List<AgendaEntry> items = onDay(entries, day);
        if (items.isEmpty()) { notice(content, "当天暂无日程"); return; }
        LinearLayout group = column(); calendarSurface(group, false, UiStyle.RADIUS_PANEL, true);
        for (AgendaEntry e : items) {
            enqueueCalendarBuild(() -> {
            LinearLayout card = column(); card.setPadding(dp(16), dp(14), dp(16), dp(14));
            TextView title = text(e.title, 17, true); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout titleRow = row();
            View sourceDot = new View(this); android.graphics.drawable.GradientDrawable dot = new android.graphics.drawable.GradientDrawable();
            dot.setShape(android.graphics.drawable.GradientDrawable.OVAL);
            dot.setColor(CalendarSourceColors.mix(e.system ? e.calendarColor : null, UiStyle.colors(this).primary)); sourceDot.setBackground(dot);
            sourceDot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(dp(7), dp(7)); dotParams.rightMargin = dp(9);
            titleRow.addView(sourceDot, dotParams); titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1)); card.addView(titleRow);
            TextView time = text("", 13, false);
            EntryStatus status = new EntryStatus(e, title, time); statuses.add(status); updateStatus(status);
            UiStyle.addSpaced(card, time, 6, 0);
            if (!e.location.trim().isEmpty()) { TextView place = text(e.location, 13, false); UiStyle.muted(place); UiStyle.addSpaced(card, place, 6, 0); }
            TextView source = text(e.source + (e.confirmed ? "" : " · 待确认"), 11, false); UiStyle.muted(source); UiStyle.addSpaced(card, source, 5, 0);
            card.setOnClickListener(v -> open(e)); UiStyle.pressable(card); group.addView(card);
            card.setTag("entry:" + e.key() + ":" + day); card.setFocusable(true);
            if (e != items.get(items.size() - 1)) { View divider = new View(this); divider.setBackgroundColor(UiStyle.colors(this).outline); group.addView(divider, new LinearLayout.LayoutParams(-1, dp(1))); }
            if (items.size() == 1) PressFeedback.group(group, 1.05f);
            });
        }
        // A single-row card owns one glass surface; scale that surface with its action.
        content.addView(group);
    }
    private void updateStatus(EntryStatus status) {
        long now = System.currentTimeMillis(); AgendaEntry e = status.entry;
        String label = "task".equals(e.category) ? e.dueAtMillis!=null&&e.dueAtMillis<now ? "已逾期" : "待完成" : e.finish() <= now ? "已结束" : e.start <= now ? "现在" : e.start - now <= 24 * 60 * 60_000L
                ? Math.max(1, (e.start - now + 59_999) / 60_000) + "分钟后开始" : "";
        status.title.setTextColor(UiStyle.colors(this).text);
        status.time.setText(time(e) + (label.isEmpty() ? "" : "  ·  " + label));
        status.time.setTextColor(e.start <= now && e.finish() > now ? UiStyle.colors(this).primary : UiStyle.colors(this).muted);
    }
    private long nextTickDelay() {
        long now = System.currentTimeMillis(), next = now + 60_000 - Math.floorMod(now, 60_000);
        for (EntryStatus status : statuses) {
            if (status.entry.start > now) next = Math.min(next, status.entry.start);
            if (status.entry.finish() > now) next = Math.min(next, status.entry.finish());
        }
        return Math.max(100, next - now);
    }
    private String time(AgendaEntry e) {
        if (e.allDay) return "全天";
        ZoneId zone = snapshot.zone; DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");
        if ("task".equals(e.category) && e.end==e.start) return (e.dueAtMillis!=null ? "截止 · " : "待办 · ") + clock.format(Instant.ofEpochMilli(e.start).atZone(zone));
        boolean cross = !Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate().equals(Instant.ofEpochMilli(e.end).atZone(zone).toLocalDate());
        return (cross ? DateTimeFormatter.ofPattern("M/d HH:mm") : clock).format(Instant.ofEpochMilli(e.start).atZone(zone))
                + " — " + (cross ? DateTimeFormatter.ofPattern("M/d HH:mm") : clock).format(Instant.ofEpochMilli(e.end).atZone(zone));
    }
    private void timeline(List<AgendaEntry> entries) {
        int days = mode == WEEK ? 7 : 1;
        LocalDate first = windowStart(); ZoneId zone = snapshot.zone;
        List<List<CalendarTimelineLayout.Block>> dayBlocks = new ArrayList<>();
        for (int d = 0; d < days; d++) dayBlocks.add(CalendarTimelineLayout.day(entries, first.plusDays(d), zone));
        boolean compressMorning = days == 7 && !snapshot.systemUnavailable && !snapshot.truncated
                && dayBlocks.stream().flatMap(List::stream).noneMatch(block ->
                        WeekTimelineScale.occupiesQuietHours(block.startMinute, block.endMinute));
        int viewport = Math.max(dp(240), body.getWidth() > 0 ? body.getWidth() : getResources().getDisplayMetrics().widthPixels - dp(40));
        int edge = PressFeedback.edgeSpace(viewport);
        int gutter = dp(40);
        int baseWidth = Math.max(viewport - 2 * edge, days == 7 ? dp(670) : viewport - 2 * edge);
        class Geometry {
            int width, dayWidth, hourHeight, height;
            WeekTimelineScale timeScale;
            float y(double minute) { return (float) timeScale.y(minute); }
            void update(float scale) {
                width = Math.round(gutter + (baseWidth - gutter) * scale);
                dayWidth = (width - gutter) / days; width = gutter + days * dayWidth;
                hourHeight = Math.max(1, Math.round(dp(72) * scale));
                timeScale = new WeekTimelineScale(compressMorning, hourHeight, dp(32));
                height = (int) Math.ceil(timeScale.y(1440));
            }
        }
        Geometry geometry = new Geometry();
        float minimumZoom = Math.max(.1f, (viewport - 2f * edge - gutter) / (baseWidth - gutter));
        if (days == 7) weekZoom = Math.max(minimumZoom, Math.min(2f, weekZoom));
        geometry.update(days == 7 ? weekZoom : 1f);
        List<Runnable> geometryBindings = new ArrayList<>();
        HorizontalScrollView horizontal = mode == WEEK ? new FreeCalendarScrollView(this) : new HorizontalScrollView(this); horizontal.setHorizontalScrollBarEnabled(false);
        horizontalViewport = horizontal; watchScroll(horizontal);
        LinearLayout layout = column(); layout.setPadding(edge, dp(4), edge, dp(4));
        horizontal.addView(layout, new FrameLayout.LayoutParams(geometry.width + 2 * edge, -1));
        LinearLayout dates = row(); View space = new View(this); dates.addView(space, new LinearLayout.LayoutParams(gutter, dp(60)));
        for (int d = 0; d < days; d++) {
            LocalDate day = first.plusDays(d);
            TextView label = action(day.format(DateTimeFormatter.ofPattern("E\nd", Locale.CHINA)), () -> {
                date = day; changeMode(DAY);
            });
            label.setTag("day:" + day);
            if (day.equals(LocalDate.now())) calendarSurface(label, true, 16, true);
            dates.addView(label, new LinearLayout.LayoutParams(geometry.dayWidth, dp(60)));
            geometryBindings.add(() -> { android.view.ViewGroup.LayoutParams p = label.getLayoutParams(); p.width = geometry.dayWidth; label.setLayoutParams(p); });
            if (days == 7) { label.setPadding(dp(2), 0, dp(2), 0); label.setTextSize(12); }
        }
        layout.addView(dates);
        if (snapshot.systemUnavailable || snapshot.truncated) notice(layout, snapshot.systemUnavailable ? "系统日程暂时无法读取" : "请缩小日期范围");
        LinearLayout allDays = row(); TextView allLabel = text("全天", 11, false); UiStyle.muted(allLabel); allDays.addView(allLabel, new LinearLayout.LayoutParams(gutter, -2));
        boolean hasAllDay = false;
        for (int d = 0; d < days; d++) {
            LinearLayout column = column();
            LocalDate day = first.plusDays(d); List<AgendaEntry> allDayItems = new ArrayList<>();
            for (AgendaEntry e : onDay(entries, day)) if (e.allDay) allDayItems.add(e);
            int visible = Math.min(3, allDayItems.size());
            for (int i = 0; i < visible; i++) {
                AgendaEntry e = allDayItems.get(i);
                hasAllDay = true;
                enqueueCalendarBuild(() -> {
                TextView card = action(e.title, () -> open(e)); card.setTextSize(11); card.setMaxLines(2);
                eventSurface(card, e); card.setTextColor(UiStyle.colors(this).text); column.addView(card, new LinearLayout.LayoutParams(-1, -2));
                });
            }
            if (allDayItems.size() > visible) enqueueCalendarBuild(() ->
                    column.addView(action("+" + (allDayItems.size() - visible), () -> showAllDay(day, allDayItems))));
            allDays.addView(column, new LinearLayout.LayoutParams(geometry.dayWidth, -2));
            geometryBindings.add(() -> { android.view.ViewGroup.LayoutParams p = column.getLayoutParams(); p.width = geometry.dayWidth; column.setLayoutParams(p); });
        }
        if (hasAllDay) layout.addView(allDays);
        ScrollView vertical = new ScrollView(this); vertical.setVerticalScrollBarEnabled(false);
        if (horizontal instanceof FreeCalendarScrollView free) free.vertical(vertical);
        verticalViewport = vertical; watchScroll(vertical);
        FrameLayout grid = new FrameLayout(this);
        int maxCardHeight = 0;
        UiStyle.Palette gridColors = UiStyle.colors(this);
        String[] hourLabels = new String[24];
        for (int hour = 0; hour < 24; hour++) hourLabels[hour] = String.format(Locale.ROOT, "%02d", hour);
        timeLines = new View(this) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                paint.setStrokeWidth(dp(1)); paint.setTextSize(dp(11));
                for (int hour = 0; hour <= 24; hour++) {
                    if (compressMorning && hour > 0 && hour < 8) continue;
                    float y = geometry.y(hour * 60); paint.setColor(gridColors.outline);
                    canvas.drawLine(gutter, y, geometry.width, y, paint);
                    if (hour < 24) { paint.setColor(gridColors.muted); canvas.drawText(compressMorning && hour == 0
                            ? "00–08" : hourLabels[hour], dp(4), y + dp(13), paint); }
                }
                paint.setColor(gridColors.outline);
                for (int d = 0; d <= days; d++) canvas.drawLine(gutter + d * geometry.dayWidth, 0, gutter + d * geometry.dayWidth, geometry.height, paint);
            }
        };
        grid.addView(timeLines, new FrameLayout.LayoutParams(-1, -1));
        android.graphics.Paint.FontMetrics smallMetrics = text("", 12, true).getPaint().getFontMetrics();
        android.graphics.Paint.FontMetrics largeMetrics = text("", 14, true).getPaint().getFontMetrics();
        List<java.util.function.IntSupplier> cardHeights = new ArrayList<>();
        for (int d = 0; d < days; d++) for (CalendarTimelineLayout.Block block : dayBlocks.get(d)) {
            final int dayIndex = d;
            AgendaEntry e = block.entry;
            // Keep view creation on the existing frame queue; zoom only relays out live views.
            java.util.function.IntSupplier measureHeight = () -> {
                int duration = (int) (geometry.y(block.endMinute) - geometry.y(block.startMinute)) - dp(2);
                boolean compact = duration < dp(36);
                android.graphics.Paint.FontMetrics metrics = !compact && days == 1 ? largeMetrics : smallMetrics;
                int lineHeight = Math.max(1, (int) Math.ceil(metrics.descent - metrics.ascent));
                return Math.max(lineHeight + dp(compact ? 4 : 8), duration);
            };
            cardHeights.add(measureHeight); maxCardHeight = Math.max(maxCardHeight, measureHeight.getAsInt());
            enqueueCalendarBuild(() -> {
            TextView card = text("", days == 1 ? 14 : 12, true);
            card.setGravity(Gravity.TOP); card.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.setTextColor(UiStyle.colors(this).text); eventSurface(card, e);
            int[] appearance = {-1, -1};
            Runnable place = () -> {
                int duration = (int) (geometry.y(block.endMinute) - geometry.y(block.startMinute)) - dp(2);
                boolean compact = duration < dp(36);
                android.graphics.Paint.FontMetrics metrics = !compact && days == 1 ? largeMetrics : smallMetrics;
                int lineHeight = Math.max(1, (int) Math.ceil(metrics.descent - metrics.ascent));
                int cardHeight = measureHeight.getAsInt();
                int shape = compact ? 1 : 0;
                if (appearance[0] != shape) {
                card.setText(compact ? ("task".equals(e.category) && e.end==e.start ? "• " : "") + e.title : e.title + "\n" + time(e) + (e.location.trim().isEmpty() ? "" : "\n" + e.location));
                card.setTextSize(compact ? 12 : days == 1 ? 14 : 12);
                card.setPadding(dp(5), dp(compact ? 1 : 4), dp(4), dp(compact ? 1 : 3));
                appearance[0] = shape;
                }
                int lines = compact ? 1 : Math.max(1, (cardHeight - dp(7)) / lineHeight);
                if (appearance[1] != lines) { card.setMaxLines(lines); appearance[1] = lines; }
                FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Math.max(days == 1 ? dp(12) : 1, geometry.dayWidth / block.lanes - dp(4)), cardHeight);
                params.leftMargin = gutter + dayIndex * geometry.dayWidth + block.lane * geometry.dayWidth / block.lanes + dp(2);
                params.topMargin = Math.max(0, Math.min(geometry.height - cardHeight, (int) geometry.y(block.startMinute) + dp(1)));
                card.setLayoutParams(params);
            };
            place.run(); geometryBindings.add(place);
            card.setContentDescription(e.title + "，" + time(e) + "，" + e.location + "，" + e.source);
            card.setTag("entry:" + e.key() + ":" + first.plusDays(dayIndex)); card.setFocusable(true);
            card.setOnClickListener(v -> open(e)); grid.addView(card);
            });
        }
        View nowLine = new View(this) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                LocalDate today = LocalDate.now(); long index = java.time.temporal.ChronoUnit.DAYS.between(first, today);
                if (index < 0 || index >= days) return;
                float y = geometry.y(CalendarTimelineLayout.minute(System.currentTimeMillis(), zone));
                paint.setColor(UiStyle.colors(CalendarOverviewActivity.this).primary); paint.setStrokeWidth(dp(2));
                float left = gutter + index * geometry.dayWidth;
                canvas.drawLine(left, y, left + geometry.dayWidth, y, paint); canvas.drawCircle(left + dp(3), y, dp(3), paint);
            }
        };
        nowLine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        enqueueCalendarBuild(() -> grid.addView(nowLine, new FrameLayout.LayoutParams(-1, -1)));
        timeLines = nowLine;
        FrameLayout gridContent = new FrameLayout(this);
        int verticalEdge = PressFeedback.edgeSpace(maxCardHeight);
        gridContent.setPadding(edge, verticalEdge, edge, verticalEdge);
        gridContent.addView(grid, new FrameLayout.LayoutParams(geometry.width, geometry.height));
        vertical.addView(gridContent, new ScrollView.LayoutParams(geometry.width + 2 * edge, geometry.height + 2 * verticalEdge));
        LinearLayout.LayoutParams gridViewportParams = new LinearLayout.LayoutParams(geometry.width + 2 * edge, 0, 1);
        gridViewportParams.setMargins(-edge, 0, -edge, 0);
        layout.addView(vertical, gridViewportParams);
        body.addView(horizontal, new FrameLayout.LayoutParams(-1, -1));
        if (horizontal instanceof FreeCalendarScrollView free) {
            final View gridLines = grid.getChildAt(0);
            class ZoomLayout implements FreeCalendarScrollView.Zoom, Runnable {
                float pending = weekZoom, focusX, focusY;
                boolean scheduled;
                @Override public void scale(float factor, float x, float y) {
                    pending = Math.max(minimumZoom, Math.min(2f, pending * factor));
                    focusX = x; focusY = y;
                    if (!scheduled) { scheduled = true; horizontal.postOnAnimation(this); }
                }
                @Override public void run() {
                    scheduled = false;
                    if (horizontal != horizontalViewport || bodyTransitionRunning) return;
                    float oldScale = weekZoom;
                    if (Math.abs(pending - oldScale) < .0001f) return;
                    // Express the focal point in day/hour coordinates before changing layout.
                    float dayAtFocus = (horizontal.getScrollX() + focusX - edge - gutter) / geometry.dayWidth;
                    float gridFocusY = focusY - vertical.getTop();
                    double minuteAtFocus = geometry.timeScale.minuteAt(vertical.getScrollY() + gridFocusY - gridContent.getPaddingTop());
                    weekZoom = pending; geometry.update(weekZoom);
                    int currentMaxCardHeight = 0;
                    for (java.util.function.IntSupplier measure : cardHeights) currentMaxCardHeight = Math.max(currentMaxCardHeight, measure.getAsInt());
                    int newVerticalEdge = PressFeedback.edgeSpace(currentMaxCardHeight);
                    gridContent.setPadding(edge, newVerticalEdge, edge, newVerticalEdge);
                    android.view.ViewGroup.LayoutParams p = layout.getLayoutParams(); p.width = geometry.width + 2 * edge; layout.setLayoutParams(p);
                    p = grid.getLayoutParams(); p.width = geometry.width; p.height = geometry.height; grid.setLayoutParams(p);
                    p = gridContent.getLayoutParams(); p.width = geometry.width + 2 * edge; p.height = geometry.height + 2 * newVerticalEdge; gridContent.setLayoutParams(p);
                    p = vertical.getLayoutParams(); p.width = geometry.width + 2 * edge; vertical.setLayoutParams(p);
                    for (Runnable binding : geometryBindings) binding.run();
                    gridLines.invalidate(); nowLine.invalidate();
                    int targetX = Math.round(edge + gutter + dayAtFocus * geometry.dayWidth - focusX);
                    // Scroll only after the new dimensions have been measured.
                    horizontal.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
                        @Override public boolean onPreDraw() {
                            horizontal.getViewTreeObserver().removeOnPreDrawListener(this);
                            int targetY = Math.round(newVerticalEdge + geometry.y(minuteAtFocus) - (focusY - vertical.getTop()));
                            horizontal.scrollTo(Math.max(0, targetX), 0); vertical.scrollTo(0, Math.max(0, targetY));
                            return true;
                        }
                    });
                }
            }
            ZoomLayout zoom = new ZoomLayout();
            free.gestures(direction -> { if (!loading && !bodyTransitionRunning) shift(direction); }, zoom);
        }
        vertical.post(() -> {
            if (vertical != verticalViewport || positions.containsKey(renderedKey)) return;
            int hour = CalendarViewport.hour(first, days, LocalDate.now(), Instant.now().atZone(zone).toLocalTime());
            vertical.scrollTo(0, verticalEdge + Math.round(geometry.y(hour * 60)));
            int focus = CalendarViewport.column(first, days, date);
            horizontal.scrollTo(Math.max(0, gutter + focus * geometry.dayWidth + geometry.dayWidth / 2 - viewport / 2), 0);
        });
    }
    private void showAllDay(LocalDate day, List<AgendaEntry> items) {
        if (allDayDialog != null) allDayDialog.dismiss();
        android.app.Dialog dialog = new android.app.Dialog(this); allDayDialog = dialog;
        LinearLayout content = column(); content.setPadding(dp(16), dp(16), dp(16), dp(16));
        TextView title = text(day.format(DateTimeFormatter.ofPattern("M月d日 · 全天")), 20, true);
        UiStyle.addSpaced(content, title, 0, 12);
        int firstStatus = statuses.size(); dayList(content, items, day);
        List<EntryStatus> dialogStatuses = new ArrayList<>(statuses.subList(firstStatus, statuses.size()));
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false); scroll.addView(content);
        dialog.setOnDismissListener(value -> {
            statuses.removeAll(dialogStatuses); if (allDayDialog == dialog) allDayDialog = null;
            handler.removeCallbacks(deferredApply); handler.post(deferredApply);
        });
        UiStyle.showFloatingDialog(dialog, scroll);
    }
    private void open(AgendaEntry entry) {
        try { startActivity(AgendaRepository.details(this, entry)); }
        catch (android.content.ActivityNotFoundException exception) { Feedback.show(this, "没有可打开此日程的日历应用"); }
    }
    private TextView action(String title, Runnable click) {
        TextView view = text(title, 14, true); view.setGravity(Gravity.CENTER); view.setMinHeight(dp(44)); view.setPadding(dp(10), 0, dp(10), 0);
        view.setTextColor(UiStyle.colors(this).primary); view.setOnClickListener(v -> click.run()); UiStyle.pressable(view); return view;
    }
    private EditText searchField() {
        EditText search = new EditText(this); search.setSingleLine(); search.setHint("搜索日程");
        search.setContentDescription("日历，搜索日程"); search.setTextSize(14); search.setTextColor(UiStyle.colors(this).text);
        search.setHintTextColor(UiStyle.colors(this).muted); search.setPadding(dp(12), 0, dp(12), 0);
        calendarSurface(search, false, UiStyle.RADIUS_PILL, false); search.setText(query);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { query = s.toString(); renderBody(); }
        });
        return search;
    }
    private ImageButton icon(int resource, String description, Runnable click) {
        ImageButton button = new ImageButton(this); button.setImageResource(resource); button.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        button.setPadding(dp(11), dp(11), dp(11), dp(11)); button.setContentDescription(description);
        calendarSurface(button, false, UiStyle.RADIUS_PILL, false); button.setOnClickListener(v -> click.run()); return button;
    }
    private void calendarSurface(View view, boolean selected, int radius, boolean outline) {
        UiStyle.acrylicChoice(view, selected, radius, outline);
        UiStyle.minimumAcrylicMix(view, ThemeStore.dark(this) ? 70 : 80);
    }
    private void eventSurface(View view, AgendaEntry entry) {
        calendarSurface(view, true, 8, true);
        if (entry.system) UiStyle.acrylicSourceColor(view, entry.calendarColor);
    }
    private void notice(LinearLayout container, String message) { TextView label = text(message, 13, false); UiStyle.muted(label); UiStyle.addSpaced(container, label, 12, 12); }
    private TextView text(String title, int sp, boolean bold) {
        TextView view = new TextView(this); view.setText(title); view.setTextSize(sp); view.setTextColor(UiStyle.colors(this).text); view.setIncludeFontPadding(false);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD); return view;
    }
    private LinearLayout column() { LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view; }
    private LinearLayout row() { LinearLayout view = new LinearLayout(this); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private LinearLayout.LayoutParams size(int width, int height) { return new LinearLayout.LayoutParams(dp(width), dp(height)); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
