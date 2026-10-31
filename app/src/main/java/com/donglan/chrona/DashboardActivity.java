package com.donglan.chrona;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RotateDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.calendar.AllDayDates;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.calendar.LinkedCalendarEvents;
import com.donglan.chrona.calendar.CalendarOccurrence;
import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.HomeTodoSummary;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.data.ScheduleQuery;
import com.donglan.chrona.data.InboxQuery;
import com.donglan.chrona.processing.ProcessingJobService;
import com.donglan.chrona.processing.StreamingOutputStore;
import com.donglan.chrona.image.ImageStore;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Three clear destinations: overview, incoming inputs, and extracted schedule. */
public final class DashboardActivity extends Activity {
    private static final int CALENDAR_PERMISSION_REQUEST = 12;
    private static final int HOME_CALENDAR_PERMISSION_REQUEST = 13;
    private static final long SYSTEM_CALENDAR_REFRESH_MILLIS = 5000L;
    private static final int MOBILE_DOCK_HEIGHT_DP = 64;
    /** Scrolling this far past the top collapses the dock to the active tab. */
    private static final int MINIMIZE_AFTER_DP = 160;
    private static final int MOBILE_BOTTOM_AREA_HEIGHT_DP = 88;
    private static final int MOBILE_DOCK_PRESS_CLEARANCE_DP = 12;
    /** How far the page follows a drag towards a section that does not exist. */
    private static final float NO_NEIGHBOUR_RESISTANCE = 0.3f;
    private static final int RECORD_ENTRY_SIZE_DP = 52;
    private static final int HOME_CARD_RAIL_CENTER_DP = 24;
    private static final int HOME_CARD_TIME_DOT_DP = 10;
    private static final class InboxRow {
        final LinearLayout card;
        final TextView marker;
        InboxRow(LinearLayout card, TextView marker) {
            this.card = card;
            this.marker = marker;
        }
    }
    private static final class ElapsedLabel {
        final TextView view;
        final long startedAt;
        final String status;
        ElapsedLabel(TextView view, long startedAt, String status) {
            this.view = view;
            this.startedAt = startedAt;
            this.status = status;
        }
    }
    static final String EXTRA_SECTION = "section";
    static final int HOME = 0, INBOX = 1, SCHEDULE = 2;
    private static final int[] SECTION_ORDER = {HOME, SCHEDULE, INBOX};
    private int section = HOME;
    private final SectionHistory sectionHistory = new SectionHistory();
    private int categoryIndex, statusIndex;
    private int scheduleTab;
    private int schedulePage, scheduleRange, scheduleCategory, schedulePublication, scheduleSource;
    private int scheduleTodoScope = ScheduleQuery.TODO_ANY;
    private LocalDate scheduleDate = LocalDate.now(), scheduleUntil = LocalDate.now();
    private LinearLayout scheduleControls;
    private int inboxPage;
    private DateIntervalBar inboxPagination, schedulePagination;
    private int inboxPageCount = 1, schedulePageCount = 1;
    private String inboxIdCacheKey = "";
    private long inboxIdCacheRevision = -1;
    private List<Long> inboxIdCache = new ArrayList<>();
    /** The two browsable lists keep their own keyword and time direction. */
    private String inboxQuery = "";
    private String scheduleQuery = "";
    private boolean scheduleOldestFirst = true;
    private boolean inboxOldestFirst = false;
    private LinearLayout content;
    private DockNavigationLayout navigation;
    private DashboardDockChrome dockChrome;
    private float dockDragFrom;
    private LinearLayout results;
    private DockNavigationLayout filterChips;
    private ImageButton categoryChip;
    private TextView orderToggle;
    private LinearLayout selectionBar;
    private TextView selectionCount;
    private Button selectAllButton;
    private Button deleteSelectedButton;
    private Button publishSelectedButton;
    private boolean bulkCalendarWriting;
    private boolean homeworkRefreshRunning;
    private static final int HOMEWORK_UNREAD_COLOR = 0xffed4256;
    private static final long HOMEWORK_FOCUS_DURATION = 2000L;
    private long unreadHomeworkId, pendingHomeworkFocus;
    private int homeworkFocusTop, homeworkFocusBottom;
    private String homeworkFocusFilter = "";
    private final Map<ImageButton, GradientDrawable> homeworkDots = new java.util.WeakHashMap<>();
    private final Runnable homeworkFocus = this::focusNewHomework;
    private static final int BULK_CALENDAR_PERMISSION_REQUEST = 14;
    private final Set<Long> selectedInboxIds = new LinkedHashSet<>();
    private final Set<Long> selectedScheduleIds = new LinkedHashSet<>();
    private boolean selectionBackRegistered;
    private android.window.OnBackInvokedCallback selectionBack;
    private Map<Long, InboxRow> inboxRows = new LinkedHashMap<>();
    private List<Long> matchingInboxIds = new ArrayList<>();
    private ScrollView scroll;
    private SwipePagerLayout pager;
    /** The neighbouring section, built on demand while a horizontal drag is in flight. */
    private SectionPage adjacentPage;
    /** At most three retained view trees, owned only by this Activity. */
    private final Map<Integer, SectionPage> sectionPages = new LinkedHashMap<>();
    private final Map<Integer, String> sectionVersions = new LinkedHashMap<>();
    private final Set<Integer> dirtySections = new java.util.HashSet<>();
    private int dragDirection;
    private boolean dragTargetLoaded;
    private boolean pageTransitionRunning;
    private int pendingSection = -1;
    private boolean pendingSectionFromHistory;
    private boolean sectionSwitchFromHistory;
    private final List<View> sectionAcrylicSurfaces = new ArrayList<>();
    private final android.graphics.Rect sectionVisibleRect = new android.graphics.Rect();
    /** A neighbouring page is being rendered off-screen; the dock must not follow it. */
    private boolean previewRender;
    private DashboardSectionData preparedSection;
    private boolean sectionLoadRunning;
    private boolean dockSectionSwitch;
    private int sectionLoadGeneration, loadingSection;
    private int navigationAttentionCount = -1;
    private TextView navigationBadge;
    private final Runnable deferredSectionRefresh = this::refreshSettledSection;
    private final DashboardCardReconciler completionRows = new DashboardCardReconciler();
    private final Set<Long> completingCandidates = new HashSet<>();
    private final Set<Long> awaitingCompletionRows = new HashSet<>();
    private boolean completionRefreshRunning, completionRefreshPending;
    private int completionMotionGeneration;
    private final Runnable homeCalendarToggleRefresh = this::refreshSystemCalendar;
    /** Entries waiting for review or already failed; shown on the dock and on the inbox filters. */
    private int pendingReview;
    private int pendingFailed;
    private final List<View> strips = new ArrayList<>();
    private GlassBackdropView backdrop;
    private boolean wide;
    /** Recreated pages and restored sessions must not replay their card entrances. */
    private final Set<Integer> motionSections = new HashSet<>();
    private boolean restoringMotion;
    private int resultMotionGeneration;
    private int scheduleResultGeneration;
    private boolean scheduleResultLoading;
    private LinearLayout scheduleBuildTarget;
    private java.util.ArrayDeque<Runnable> scheduleBuildSteps;
    private Runnable scheduleBuildDone;
    private final Runnable scheduleBuildFrame = this::buildScheduleResultFrame;
    private String snapshot = "";
    private int restoredScrollY;
    private List<ElapsedLabel> elapsedLabels = new ArrayList<>();
    private final Handler refresh = new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService startupReader =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private boolean initialDataReady, initialLoadRunning, startupReported, startupReadFailed;
    private int resumeGeneration;
    private boolean updateChecksReady;
    private HomeData startupHome;
    private long startupRevision;
    private boolean applyingStartupData;
    private final long createdAt = android.os.SystemClock.elapsedRealtime();
    private final java.util.concurrent.ExecutorService calendarReader =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private List<CalendarOccurrence> systemCalendarEvents = java.util.Collections.emptyList();
    private boolean calendarLoading, foreground, calendarErrorShown, homeRefreshPending, scheduleRefreshPending;
    private long calendarGeneration, lastCalendarFetch;
    private String calendarWindow = "";
    private String calendarResultWindow = "";
    private android.os.CancellationSignal calendarCancellation;
    private final Runnable poll = this::pollChanges;
    private final Runnable elapsedTicker = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            for (ElapsedLabel item : elapsedLabels) {
                if (item.view.isAttachedToWindow())
                    item.view.setText(statusText(item.status) + " · 已 "
                            + elapsedText(now - item.startedAt));
            }
            refresh.postDelayed(this, 1000L);
        }
    };

    @Override protected void onCreate(Bundle state) {
        restoringMotion = state != null;
        ThemeStore.apply(this);
        super.onCreate(state);
        if (state != null) {
            section = state.getInt(EXTRA_SECTION, HOME);
            sectionHistory.restore(state.getIntArray("section_history"));
            categoryIndex = Math.max(0, Math.min(EventCategory.VALUES.length,state.getInt("category")));
            statusIndex = state.getInt("status");
            scheduleTab = state.getInt("schedule_tab");
            schedulePage = state.getInt("schedule_page");
            pendingHomeworkFocus = state.getLong("homework_focus", 0);
            homeworkFocusTop = state.getInt("homework_focus_top", 0);
            homeworkFocusBottom = state.getInt("homework_focus_bottom", 0);
            homeworkFocusFilter = state.getString("homework_focus_filter", "");
            scheduleRange = state.getInt("schedule_range");
            scheduleCategory = Math.max(0, Math.min(com.donglan.chrona.data.ScheduleFilterState.EXAM_CATEGORY,
                    state.getInt("schedule_category")));
            schedulePublication = state.getInt("schedule_publication");
            scheduleSource = state.getInt("schedule_source");
            scheduleTodoScope = state.getInt("schedule_todo_scope", ScheduleQuery.TODO_ANY);
            scheduleDate = LocalDate.parse(state.getString("schedule_date", LocalDate.now().toString()));
            scheduleUntil = LocalDate.parse(state.getString("schedule_until", scheduleDate.toString()));
            inboxPage = Math.max(0, state.getInt("inbox_page",
                    state.getInt("inbox_shown", 12) / InboxQuery.PAGE_SIZE - 1));
            inboxQuery = state.getString("inbox_query", "");
            scheduleQuery = state.getString("schedule_query", "");
            inboxOldestFirst = state.getBoolean("inbox_oldest_first", false);
            long[] selected = state.getLongArray("selected_inbox_ids");
            if (selected != null) for (long id : selected) selectedInboxIds.add(id);
            long[] selectedSchedules = state.getLongArray("selected_schedule_ids");
            if (selectedSchedules != null) for (long id : selectedSchedules) selectedScheduleIds.add(id);
            restoredScrollY = state.getInt("scroll_y");
        } else {
            section = getIntent().getIntExtra(EXTRA_SECTION, HOME);
        }
        scheduleOldestFirst = ScheduleOrderPreferences.oldestFirst(this, scheduleTab);
        buildShell();
        // First draw contains working navigation; data preparation never blocks its frame.
        addHeader();
        message(content, "正在读取日程…");
        drawNavigation();
        updateSelectionBack();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(EXTRA_SECTION, section);
        state.putIntArray("section_history", sectionHistory.snapshot());
        state.putInt("category", categoryIndex);
        state.putInt("status", statusIndex);
        state.putInt("schedule_tab", scheduleTab);
        state.putInt("schedule_page", schedulePage);
        state.putLong("homework_focus", pendingHomeworkFocus);
        state.putInt("homework_focus_top", homeworkFocusTop);
        state.putInt("homework_focus_bottom", homeworkFocusBottom);
        state.putString("homework_focus_filter", homeworkFocusFilter);
        state.putInt("schedule_range", scheduleRange);
        state.putInt("schedule_category", scheduleCategory);
        state.putInt("schedule_publication", schedulePublication);
        state.putInt("schedule_source", scheduleSource);
        state.putInt("schedule_todo_scope", scheduleTodoScope);
        state.putString("schedule_date", scheduleDate.toString());
        state.putString("schedule_until", scheduleUntil.toString());
        state.putInt("inbox_page", inboxPage);
        state.putString("inbox_query", inboxQuery);
        state.putString("schedule_query", scheduleQuery);
        state.putBoolean("inbox_oldest_first", inboxOldestFirst);
        state.putInt("scroll_y", scroll.getScrollY());
        long[] selected = new long[selectedInboxIds.size()];
        int selectedIndex = 0;
        for (long id : selectedInboxIds) selected[selectedIndex++] = id;
        state.putLongArray("selected_inbox_ids", selected);
        state.putLongArray("selected_schedule_ids", selectedScheduleIds.stream().mapToLong(Long::longValue).toArray());
    }

    /** Every piece of state render() writes to, so a neighbour can be built and swapped in. */
    private final class SectionPage {
        final int section;
        final ScrollView scroll;
        final LinearLayout content;
        final LinearLayout results;
        final DockNavigationLayout filterChips;
        final ImageButton categoryChip;
        final TextView orderToggle;
        final LinearLayout selectionBar;
        final TextView selectionCount;
        final Button selectAllButton;
        final Button deleteSelectedButton;
        final Button publishSelectedButton;
        final Map<Long, InboxRow> inboxRows;
        final List<Long> matchingInboxIds;
        final List<ElapsedLabel> elapsedLabels;
        final List<View> strips;
        final LinearLayout scheduleControls;

        SectionPage() {
            this.section = DashboardActivity.this.section;
            this.scroll = DashboardActivity.this.scroll;
            this.content = DashboardActivity.this.content;
            this.results = DashboardActivity.this.results;
            this.filterChips = DashboardActivity.this.filterChips;
            this.categoryChip = DashboardActivity.this.categoryChip;
            this.orderToggle = DashboardActivity.this.orderToggle;
            this.selectionBar = DashboardActivity.this.selectionBar;
            this.selectionCount = DashboardActivity.this.selectionCount;
            this.selectAllButton = DashboardActivity.this.selectAllButton;
            this.deleteSelectedButton = DashboardActivity.this.deleteSelectedButton;
            this.publishSelectedButton = DashboardActivity.this.publishSelectedButton;
            this.inboxRows = DashboardActivity.this.inboxRows;
            this.matchingInboxIds = DashboardActivity.this.matchingInboxIds;
            this.elapsedLabels = DashboardActivity.this.elapsedLabels;
            this.strips = new ArrayList<>(DashboardActivity.this.strips);
            this.scheduleControls = DashboardActivity.this.scheduleControls;
        }
    }

    /** Every section body drives the same collapse; a switch drops back to the full dock. */
    private void attachDockMinimize(ScrollView view) {
        view.setOnScrollChangeListener((target, scrollX, scrollY, oldX, oldY) -> {
            if (!wide && navigation != null) navigation.setMinimized(scrollY > dp(MINIMIZE_AFTER_DP));
        });
    }

    private void activateSectionPage(SectionPage page) {
        if (navigation != null) navigation.setMinimized(false);
        if (results != null && results != page.results) {
            resultMotionGeneration++;
            UiMotion.resetList(results);
        }
        if (scheduleResultLoading && page.results != results) cancelScheduleResults();
        section = page.section;
        scroll = page.scroll;
        content = page.content;
        results = page.results;
        filterChips = page.filterChips;
        categoryChip = page.categoryChip;
        orderToggle = page.orderToggle;
        selectionBar = page.selectionBar;
        selectionCount = page.selectionCount;
        selectAllButton = page.selectAllButton;
        deleteSelectedButton = page.deleteSelectedButton;
        publishSelectedButton = page.publishSelectedButton;
        inboxRows = page.inboxRows;
        matchingInboxIds = page.matchingInboxIds;
        elapsedLabels = page.elapsedLabels;
        strips.clear();
        strips.addAll(page.strips);
        // Filters belong to the Activity; an old hidden page must not roll them back.
        scheduleControls = page.scheduleControls;
        if (pager != null) pager.setGesturePriorityChildren(strips);
    }

    /** Renders a neighbouring section off-screen, then puts the live page's state back. */
    private SectionPage buildAdjacentSection(int destination) {
        SectionPage current = new SectionPage();
        sectionPages.put(section, current);
        SectionPage cached = sectionPages.get(destination);
        if (cached != null) {
            return cached;
        }
        ScrollView targetScroll = new ScrollView(this);
        targetScroll.setFillViewport(true);
        targetScroll.setVerticalScrollBarEnabled(false);
        attachDockMinimize(targetScroll);
        LinearLayout targetContent = new LinearLayout(this);
        targetContent.setOrientation(LinearLayout.VERTICAL);
        int horizontal = 20;
        targetContent.setPadding(dp(horizontal), dp(UiStyle.PAGE_TOP_DP), dp(horizontal),
                dp(wide ? 42 : MOBILE_BOTTOM_AREA_HEIGHT_DP + 24));
        FrameLayout targetHost = new FrameLayout(this);
        allowSectionDockOverflow(targetContent, targetHost);
        targetHost.addView(targetContent, new FrameLayout.LayoutParams(-1, -2));
        targetScroll.addView(targetHost, new FrameLayout.LayoutParams(-1, -2));

        scroll = targetScroll;
        content = targetContent;
        section = destination;
        results = null;
        filterChips = null;
        categoryChip = null;
        orderToggle = null;
        selectionBar = null;
        selectionCount = null;
        selectAllButton = null;
        deleteSelectedButton = null;
        publishSelectedButton = null;
        inboxRows = new LinkedHashMap<>();
        matchingInboxIds = new ArrayList<>();
        elapsedLabels = new ArrayList<>();
        strips.clear();
        previewRender = true;
        try {
            render();
        } finally {
            previewRender = false;
        }

        SectionPage target = new SectionPage();
        sectionPages.put(destination, target);
        activateSectionPage(current);
        return target;
    }

    private void rememberRenderedPage() {
        awaitingCompletionRows.clear();
        sectionPages.put(section, new SectionPage());
        sectionVersions.put(section, dataSnapshot());
        dirtySections.remove(section);
        // A calendar/poll refresh can rebuild the page during an end-of-strip drag.
        // Replace references to detached cards before the next translated frame.
        if (!previewRender && !sectionAcrylicSurfaces.isEmpty()) collectSectionSurfaces();
    }

    private void refreshActivePageIfNeeded() {
        if (completionRefreshRunning || !completingCandidates.isEmpty() || completionRefreshPending) return;
        if (section == SCHEDULE && scheduleResultLoading) return;
        String version = dataSnapshot();
        if (dirtySections.contains(section) || !version.equals(sectionVersions.get(section))) {
            if (section == HOME) {
                refreshCompletionRows();
                return;
            }
            int y = scroll.getScrollY();
            int x = section == HOME && !strips.isEmpty() ? strips.get(0).getScrollX() : 0;
            if (section != HOME && results != null) updateResults();
            else render();
            ScrollView refreshed = scroll;
            View strip = section == HOME && !strips.isEmpty() ? strips.get(0) : null;
            refreshed.post(() -> {
                refreshed.scrollTo(0, y);
                if (strip != null) strip.scrollTo(x, 0);
            });
            if (section == HOME) homeRefreshPending = false;
        }
    }

    private final SwipePagerLayout.Listener sectionPagerListener = new SwipePagerLayout.Listener() {
        @Override public void onStart() {
            if (dockChrome != null) { dockChrome.beginDrag(); dockDragFrom = dockChrome.fraction(); }
            sectionLoadGeneration++; sectionLoadRunning = false;
            scroll.animate().cancel();
            if (adjacentPage != null) {
                adjacentPage.scroll.animate().cancel();
                pager.removeView(adjacentPage.scroll);
                adjacentPage = null;
            }
            pageTransitionRunning = false;
            // A new body gesture supersedes any older dock request waiting on that animation.
            pendingSection = -1;
            pendingSectionFromHistory = false;
            sectionSwitchFromHistory = false;
            dragTargetLoaded = false;
            dragDirection = 0;
            scroll.setTranslationX(0f);
            if (navigation != null && !dockSectionSwitch) navigation.beginBodyDrag();
            collectSectionSurfaces();
        }

        @Override public void onDrag(float distanceX) {
            if (pageTransitionRunning) return;
            float width = sectionPageWidth();
            float offset = Math.max(-width, Math.min(width, distanceX));
            int direction = offset < 0 ? 1 : -1;
            if (!dragTargetLoaded && Math.abs(offset) >= dp(10)) {
                int targetPosition = sectionPosition(section) + direction;
                if (targetPosition < 0 || targetPosition >= SECTION_ORDER.length) {
                    // Nothing lies that way: the page only gives a little, but it still has to
                    // resample its acrylic cards every frame like a normal drag does — skipping
                    // that here left them showing the snapshot taken when the drag began.
                    scroll.setTranslationX(offset * NO_NEIGHBOUR_RESISTANCE);
                    if (navigation != null) navigation.showPageProgress(
                            sectionPosition(section), sectionPosition(section), 0f);
                    invalidateSectionSurfaces();
                    return;
                }
                int destination = SECTION_ORDER[targetPosition];
                dragDirection = direction;
                SectionPage preview = buildAdjacentSection(destination);
                // Positioned before it joins the pager so no frame can show it at the origin.
                preview.scroll.setTranslationX(direction > 0 ? width : -width);
                pager.addView(preview.scroll, 1, new FrameLayout.LayoutParams(-1, -1));
                adjacentPage = preview;
                dragTargetLoaded = true;
                collectSectionSurfaces();
            }
            if (dragTargetLoaded && adjacentPage != null) {
                if (dragDirection > 0 && offset > 0 || dragDirection < 0 && offset < 0) offset = 0;
                scroll.setTranslationX(offset);
                adjacentPage.scroll.setTranslationX((dragDirection > 0 ? width : -width) + offset);
            } else {
                scroll.setTranslationX(offset);
            }
            if (navigation != null) navigation.showPageProgress(sectionPosition(section),
                    adjacentPage == null ? sectionPosition(section) : sectionPosition(adjacentPage.section),
                    Math.abs(offset) / width);
            if (dockChrome != null) dockChrome.drag(dockDragFrom,
                    (adjacentPage == null ? section : adjacentPage.section) == HOME, Math.abs(offset) / width);
            invalidateSectionSurfaces();
        }

        @Override public void onRelease(float distanceX, float velocityX) {
            if (pageTransitionRunning) return;
            float width = sectionPageWidth();
            boolean quick = Math.abs(velocityX) > dp(900) && Math.abs(distanceX) > dp(24);
            boolean commit = dragTargetLoaded && adjacentPage != null
                    && (Math.abs(distanceX) >= Math.max(dp(64), width * .18f) || quick);
            finishSectionDrag(commit, width);
        }

        @Override public void onCancel() {
            if (pageTransitionRunning) return;
            finishSectionDrag(false, sectionPageWidth());
        }
    };

    private float sectionPageWidth() {
        return Math.max(scroll.getWidth(), getResources().getDisplayMetrics().widthPixels);
    }

    private void finishSectionDrag(boolean commit, float width) {
        if (adjacentPage == null) {
            settleCurrentSection();
            return;
        }
        float direction = dragDirection > 0 ? -1f : 1f;
        float currentTarget = commit ? direction * width : 0f;
        float adjacentTarget = commit ? 0f : -direction * width;
        pageTransitionRunning = true;
        if (navigation != null) navigation.beginPageSettle(sectionPosition(
                commit ? adjacentPage.section : section));
        scroll.animate().cancel();
        adjacentPage.scroll.animate().cancel();
        if (!android.animation.ValueAnimator.areAnimatorsEnabled() || width <= 0) {
            if (dockChrome != null) dockChrome.setHome((commit ? adjacentPage.section : section) == HOME);
            scroll.setTranslationX(currentTarget);
            adjacentPage.scroll.setTranslationX(adjacentTarget);
            if (navigation != null) navigation.updatePageSettle(1f);
            completeSectionDrag(commit);
            return;
        }
        float remaining = Math.abs(currentTarget - scroll.getTranslationX());
        long duration = commit
                ? Math.max(120L, Math.min(UiMotion.ENTER, Math.round(UiMotion.ENTER * remaining / width)))
                : SwipePagerLayout.recoilDuration(remaining, width);
        android.view.animation.Interpolator interpolator = commit
                ? new android.view.animation.DecelerateInterpolator(1.35f)
                : SwipePagerLayout.RECOIL_INTERPOLATOR;
        if (dockChrome != null) dockChrome.animateHome((commit ? adjacentPage.section : section) == HOME,
                duration);
        // Live shader sampling follows the page in window coordinates throughout release.
        // A frozen whole-page layer would preserve offscreen wallpaper samples.
        scroll.animate().translationX(currentTarget).setDuration(duration)
                .setInterpolator(interpolator)
                .setUpdateListener(animation -> {
                    if (navigation != null) navigation.updatePageSettle(animation.getAnimatedFraction());
                    invalidateSectionSurfaces();
                }).start();
        adjacentPage.scroll.animate().translationX(adjacentTarget).setDuration(duration)
                .setInterpolator(interpolator)
                .setUpdateListener(null)
                .withEndAction(() -> completeSectionDrag(commit)).start();
    }

    private void settleCurrentSection() {
        if (pageTransitionRunning) return;
        scroll.animate().cancel();
        if (navigation != null) navigation.beginPageSettle(sectionPosition(section));
        if (!UiMotion.isEnabled()) {
            if (dockChrome != null) dockChrome.setHome(section == HOME);
            scroll.setTranslationX(0f);
            if (navigation != null) navigation.finishPageSelection(sectionPosition(section));
            sectionAcrylicSurfaces.clear();
            invalidateSectionSurfaces();
            switchToPendingSection();
            return;
        }
        long duration = SwipePagerLayout.recoilDuration(Math.abs(scroll.getTranslationX()), sectionPageWidth());
        if (dockChrome != null) dockChrome.animateHome(section == HOME, duration);
        scroll.animate().translationX(0f)
                .setDuration(duration)
                .setInterpolator(SwipePagerLayout.RECOIL_INTERPOLATOR)
                .setUpdateListener(animation -> {
                    if (navigation != null) navigation.updatePageSettle(animation.getAnimatedFraction());
                    invalidateSectionSurfaces();
                })
                .withEndAction(() -> {
                    if (navigation != null) navigation.finishPageSelection(sectionPosition(section));
                    sectionAcrylicSurfaces.clear();
                    invalidateSectionSurfaces();
                    switchToPendingSection();
                }).start();
    }

    /**
     * Acrylic cards sample the blurred backdrop at their own position, so every translated frame
     * has to invalidate them; otherwise they keep the snapshot taken before the drag started.
     */
    private void invalidateSectionSurfaces() {
        if (pager == null || !pager.isAttachedToWindow()) return;
        if (sectionAcrylicSurfaces.isEmpty()) {
            UiSurfaces.invalidateAcrylicSurfaces(pager);
        } else {
            for (View surface : sectionAcrylicSurfaces) {
                if (surface.isAttachedToWindow() && surface.getGlobalVisibleRect(sectionVisibleRect))
                    surface.invalidate();
            }
        }
    }

    private void collectSectionSurfaces() {
        sectionAcrylicSurfaces.clear();
        UiSurfaces.collectAcrylicSurfaces(pager, sectionAcrylicSurfaces);
    }

    private void switchToPendingSection() {
        int destination = pendingSection;
        boolean fromHistory = pendingSectionFromHistory;
        pendingSection = -1;
        pendingSectionFromHistory = false;
        if (destination >= 0) switchTo(destination, fromHistory);
    }

    private void completeSectionDrag(boolean commit) {
        boolean fromHistory = sectionSwitchFromHistory;
        sectionSwitchFromHistory = false;
        SectionPage target = adjacentPage;
        adjacentPage = null;
        dragTargetLoaded = false;
        dragDirection = 0;
        pageTransitionRunning = false;
        sectionAcrylicSurfaces.clear();
        if (target == null) {
            scroll.setTranslationX(0f);
            if (dockChrome != null) dockChrome.setHome(section == HOME);
            return;
        }
        if (commit) {
            int previousSection = section;
            clearCurrentSelection();
            sectionPages.put(section, new SectionPage());
            pager.removeView(scroll);
            target.scroll.setTranslationX(0f);
            activateSectionPage(target);
            if (fromHistory) sectionHistory.commitBack(section);
            else sectionHistory.record(previousSection, section);
            if (navigation != null) navigation.finishPageSelection(sectionPosition(section));
            refresh.removeCallbacks(deferredSectionRefresh); refresh.post(deferredSectionRefresh);
            updateSelectionBack();
            drawNavigation();
            if (section == SCHEDULE) queueHomeworkFocus();
        } else {
            pager.removeView(target.scroll);
            target.scroll.setTranslationX(0f);
            scroll.setTranslationX(0f);
            if (navigation != null) navigation.finishPageSelection(sectionPosition(section));
            refresh.removeCallbacks(deferredSectionRefresh); refresh.post(deferredSectionRefresh);
        }
        if (dockChrome != null) dockChrome.setHome(section == HOME);
        invalidateSectionSurfaces();
        // Revision checks and stale-page refresh wait until both page and dock motion finish.
        refresh.removeCallbacks(deferredSectionRefresh); refresh.post(deferredSectionRefresh);
        if (section == HOME || (section == SCHEDULE && scheduleSource == 1))
            refresh.post(this::refreshSystemCalendar);
        switchToPendingSection();
        if (!startupReported) afterDraw(scroll, this::maybeReportStartup);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        switchTo(intent.getIntExtra(EXTRA_SECTION, section));
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        if (!initialDataReady) {
            loadInitialData();
            return;
        }
        resumeDashboard();
    }

    private void loadInitialData() {
        if (initialLoadRunning) return;
        initialLoadRunning = true;
        final boolean home = section == HOME;
        startupReader.execute(() -> {
            HomeData loaded = null;
            long revision = 0;
            String error = null;
            try (TaskStore store = new TaskStore(getApplicationContext())) {
                android.database.sqlite.SQLiteDatabase db = store.getReadableDatabase();
                db.beginTransaction();
                try {
                    if (home) loaded = new HomeData(getApplicationContext(), store);
                    revision = store.dataRevision();
                    db.setTransactionSuccessful();
                } finally { db.endTransaction(); }
            } catch (RuntimeException exception) {
                error = exception.toString();
            }
            HomeData result = loaded;
            long resultRevision = revision;
            String failure = error;
            refresh.post(() -> {
                if (isDestroyed() || isFinishing()) return;
                initialLoadRunning = false;
                initialDataReady = true;
                startupReadFailed = failure != null;
                pager.setTaskPagingEnabled(true);
                startupHome = result;
                startupRevision = resultRevision;
                applyingStartupData = true;
                try {
                    if (failure != null) {
                        content.removeAllViews();
                        addHeader();
                        message(content, "暂时无法读取日程，请稍后重试");
                    } else render();
                    snapshot = dataSnapshot();
                } finally {
                    applyingStartupData = false;
                    startupHome = null;
                }
                switchToPendingSection();
                if (foreground) finishResume();
            });
        });
    }

    private void resumeDashboard() {
        refreshActivePageIfNeeded();
        snapshot = dataSnapshot();
        finishResume();
        switchToPendingSection();
    }

    private void finishResume() {
        int oldScroll = restoredScrollY != 0 ? restoredScrollY : scroll.getScrollY();
        restoredScrollY = 0;
        ScrollView resumed = scroll;
        resumed.post(() -> resumed.scrollTo(0, oldScroll));
        refresh.removeCallbacks(poll);
        refresh.removeCallbacks(elapsedTicker);
        refresh.postDelayed(poll, 5000L);
        refresh.postDelayed(elapsedTicker, 1000L);
        refresh.post(this::refreshSystemCalendar);
        // Wait for one actual traversal before permissions, maintenance or update prompts.
        int generation = resumeGeneration;
        afterDraw(resumed, () -> {
            if (generation != resumeGeneration || !foreground || isDestroyed() || isFinishing()) return;
            maybeReportStartup();
            afterFirstContent();
        });
    }

    private void afterDraw(View view, Runnable action) {
        view.getViewTreeObserver().addOnDrawListener(new android.view.ViewTreeObserver.OnDrawListener() {
            private boolean posted;
            @Override public void onDraw() {
                if (posted) return;
                posted = true;
                view.post(() -> {
                    if (view.getViewTreeObserver().isAlive())
                        view.getViewTreeObserver().removeOnDrawListener(this);
                    if (!foreground || isDestroyed() || isFinishing()) return;
                    action.run();
                });
            }
        });
        view.invalidate();
    }

    private void maybeReportStartup() {
        if (startupReported || startupReadFailed || !initialDataReady || calendarLoading
                || pageTransitionRunning || dragTargetLoaded) return;
        if (!calendarErrorShown
                && !calendarWindowKey(new LocalDate[]{LocalDate.now(), LocalDate.now().plusMonths(1)})
                        .equals(calendarResultWindow) && section == HOME) return;
        startupReported = true;
        reportFullyDrawn();
        android.util.Log.i("ChronaStartup", "content_ready activity_ms="
                + (android.os.SystemClock.elapsedRealtime() - createdAt)
                + " run=" + getIntent().getLongExtra("startup_measurement", 0));
    }

    private void afterFirstContent() {
        if (!GuideActivity.showIfNeeded(this)) {
            StartupPermissions.requestFirstLaunch(this);
            updateChecksReady = true;
            ReleaseUpdates.checkAutomatically(this);
        }
        startupReader.execute(() -> {
            int recovered = ProcessingJobService.reconcile(getApplicationContext());
            if (recovered > 0) refresh.post(() -> {
                if (!foreground || isDestroyed()) return;
                Feedback.showLong(this, recovered + " 条解析已中断，请在收件箱重试");
                if (!pageTransitionRunning && !dragTargetLoaded) refreshActivePageIfNeeded();
            });
        });
    }

    @Override protected void onPause() {
        resultMotionGeneration++;
        if (results != null) UiMotion.resetList(results);
        cancelScheduleResults();
        if (sectionLoadRunning && pendingSection < 0) {
            pendingSection = loadingSection;
            pendingSectionFromHistory = sectionSwitchFromHistory;
        }
        if (sectionLoadRunning) sectionSwitchFromHistory = false;
        sectionLoadGeneration++; sectionLoadRunning = false;
        refresh.removeCallbacks(deferredSectionRefresh);
        foreground = false;
        updateChecksReady = false;
        resumeGeneration++;
        calendarGeneration++;
        if (calendarCancellation != null) {
            calendarCancellation.cancel();
            lastCalendarFetch = 0;
        }
        refresh.removeCallbacks(poll);
        refresh.removeCallbacks(elapsedTicker);
        backdrop.stop();
        super.onPause();
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus && pendingHomeworkFocus > 0) queueHomeworkFocus();
        if (hasFocus && updateChecksReady) ReleaseUpdates.checkAutomatically(this);
    }

    @Override protected void onDestroy() {
        if (dockChrome != null) dockChrome.cancel();
        cancelScheduleResults();
        refresh.removeCallbacksAndMessages(null);
        sectionPages.clear();
        sectionAcrylicSurfaces.clear();
        sectionVersions.clear();
        dirtySections.clear();
        calendarReader.shutdownNow();
        startupReader.shutdownNow();
        super.onDestroy();
    }

    private void pollChanges() {
        if (completionRefreshRunning || !completingCandidates.isEmpty() || completionRefreshPending
                || scheduleResultLoading || sectionLoadRunning || pageTransitionRunning || dragTargetLoaded
                || (navigation != null && navigation.isAnimating())) {
            refresh.postDelayed(poll, 500L); return;
        }
        if (section == HOME || (section == SCHEDULE && scheduleSource == 1)) refreshSystemCalendar();
        String current = dataSnapshot();
        if (dirtySections.contains(section) || !current.equals(snapshot)) {
            if (!pageTransitionRunning && !dragTargetLoaded) {
                refreshActivePageIfNeeded();
                snapshot = current;
            }
        }
        refresh.postDelayed(poll, 5000L);
    }

    private void refreshSystemCalendar() {
        if (!foreground || isDestroyed() || calendarLoading) return;
        boolean systemSchedule = section == SCHEDULE && scheduleSource == 1;
        boolean readSystem = HomeTimelinePreferences.includesSystemCalendar(this)
                && new CalendarStore(this).hasReadPermission();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate[] window = systemSchedule ? scheduleWindow(true)
                : new LocalDate[]{today, today.plusMonths(1)};
        long begin = window[0].atStartOfDay(zone).toInstant().toEpochMilli();
        long end = window[1].atStartOfDay(zone).toInstant().toEpochMilli();
        String windowKey = calendarWindowKey(window);
        long elapsed = android.os.SystemClock.elapsedRealtime();
        if (windowKey.equals(calendarWindow) && lastCalendarFetch != 0
                && elapsed - lastCalendarFetch < SYSTEM_CALENDAR_REFRESH_MILLIS) return;
        calendarWindow = windowKey;
        lastCalendarFetch = elapsed;
        calendarLoading = true;
        long generation = calendarGeneration;
        android.os.CancellationSignal cancellation = new android.os.CancellationSignal();
        calendarCancellation = cancellation;
        calendarReader.execute(() -> {
            List<CalendarOccurrence> loaded = new ArrayList<>();
            boolean failed = false;
            try {
                // UTC all-day bounds may fall outside the local midnight window.
                long padding = java.time.Duration.ofDays(1).toMillis();
                if (readSystem) for (CalendarOccurrence event : new CalendarStore(getApplicationContext())
                        .listInstances(begin - padding, end + padding, cancellation)) {
                    if (event.displayEnd(zone) > begin && event.displayStart(zone) < end)
                        loaded.add(event);
                }
            } catch (RuntimeException exception) {
                failed = !cancellation.isCanceled();
            }
            if (!cancellation.isCanceled()) try (TaskStore linkedStore = new TaskStore(getApplicationContext())) {
                loaded = ExternalCourseAgenda.merge(getApplicationContext(), loaded, begin, end,
                        new HashSet<>(linkedStore.linkedCalendarIds()));
            } catch (RuntimeException exception) { failed = true; }
            List<CalendarOccurrence> result = java.util.Collections.unmodifiableList(loaded);
            boolean error = failed;
            refresh.post(() -> {
                calendarLoading = false;
                calendarCancellation = null;
                if (generation != calendarGeneration || !foreground || isDestroyed()) {
                    lastCalendarFetch = 0;
                    if (foreground && !isDestroyed()) refresh.post(this::refreshSystemCalendar);
                    return;
                }
                LocalDate[] desired = section == SCHEDULE && scheduleSource == 1
                        ? scheduleWindow(true) : new LocalDate[]{LocalDate.now(), LocalDate.now().plusMonths(1)};
                if (!windowKey.equals(calendarWindowKey(desired))) {
                    refresh.post(this::refreshSystemCalendar);
                    return;
                }
                if (error) {
                    if (!calendarErrorShown) Feedback.show(this, "部分日程无法读取，请稍后重试");
                    calendarErrorShown = true;
                    lastCalendarFetch = 0;
                } else calendarErrorShown = false;
                if (!systemCalendarEvents.equals(result) || !windowKey.equals(calendarResultWindow)) {
                    systemCalendarEvents = result;
                    calendarResultWindow = windowKey;
                    dirtySections.add(HOME);
                    dirtySections.add(SCHEDULE);
                    if (section == SCHEDULE && scheduleSource == 1) {
                        if (completionRefreshRunning || !completingCandidates.isEmpty()
                                || pageTransitionRunning || dragTargetLoaded || sectionLoadRunning
                                || (navigation != null && navigation.isAnimating())) {
                            scheduleRefreshPending = true;
                            refresh.removeCallbacks(deferredSectionRefresh); refresh.post(deferredSectionRefresh);
                        }
                        else updateResults();
                    }
                    else refreshHomeContent();
                }
                afterDraw(scroll, this::maybeReportStartup);
            });
        });
    }

    private void refreshHomeContent() {
        if (section != HOME || previewRender) return;
        if (completionRefreshRunning || !completingCandidates.isEmpty() || completionRefreshPending) {
            // A calendar result may arrive after the local rows have been built but
            // before their transition finishes. That completion drains this queue,
            // not homeRefreshPending; otherwise the new courses wait for re-entry.
            homeRefreshPending = true;
            completionRefreshPending = true;
            android.util.Log.d("ChronaHome", "external_refresh_queued_during_local_reconcile");
            return;
        }
        if (pageTransitionRunning || dragTargetLoaded || sectionLoadRunning
                || (navigation != null && navigation.isAnimating())) {
            homeRefreshPending = true;
            refresh.removeCallbacks(deferredSectionRefresh); refresh.post(deferredSectionRefresh);
            return;
        }
        homeRefreshPending = false;
        refreshCompletionRows();
    }


    private String calendarWindowKey(LocalDate[] window) {
        ZoneId zone = ZoneId.systemDefault();
        return window[0].atStartOfDay(zone).toInstant().toEpochMilli() + ":"
                + window[1].atStartOfDay(zone).toInstant().toEpochMilli()
                + ":" + HomeTimelinePreferences.includesSystemCalendar(this)
                + ":" + new CalendarStore(this).hasReadPermission()
                + ":" + ExternalCourseAgenda.revision(this);
    }

    private List<CalendarOccurrence> calendarEventsForWindow(LocalDate[] window) {
        return calendarResultWindow.equals(calendarWindowKey(window)) ? systemCalendarEvents
                : java.util.Collections.emptyList();
    }

    private String homeEntryWhen(HomeTimelineEntry entry) {
        if (entry.candidate != null) return formatWhen(entry.candidate);
        CalendarOccurrence event = entry.systemEvent;
        if (event.allDay) {
            String start = AllDayDates.displayStart(event.startMillis);
            String end = AllDayDates.displayEnd(event.endMillis);
            return (start.equals(end) ? start : start + " 至 " + end) + " · 全天";
        }
        return dateTime(event.startMillis);
    }

    private void openHomeEntry(HomeTimelineEntry entry) {
        if (entry.candidate != null) {
            openCandidate(entry.candidate);
            return;
        }
        CalendarOccurrence event = entry.systemEvent;
        Intent intent = SystemAgendaDetailActivity.intent(this, event.eventId, event.startMillis,
                event.endMillis, event.allDay, event.title, event.location, event.calendarName)
                .putExtra("description", event.description);
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException exception) {
            Feedback.show(this, "未找到可打开日程的日历应用");
        }
    }

    private String dataSnapshot() {
        if (preparedSection != null) return snapshotForRevision(preparedSection.revision);
        if (applyingStartupData) return snapshotForRevision(startupRevision);
        try (TaskStore store = new TaskStore(this)) {
            // Database triggers track edits too; time buckets move ended events between tabs.
            return snapshotForRevision(store.dataRevision());
        } catch (Exception ignored) {
            return "";
        }
    }

    private String snapshotForRevision(long revision) {
        return revision + ":" + (System.currentTimeMillis() / 60000L)
                + ":" + HomeTimelinePreferences.getItemLimit(this)
                + ":" + HomeTimelinePreferences.includesSystemCalendar(this)
                + ":" + ExternalCourseAgenda.revision(this)
                + ":" + ZjuExamStore.revision() + ":" + new ZjuSettingsStore(this).configured()
                + ":" + AgendaReminderPreferences.prefs(this).getLong("acknowledgement_revision", 0L)
                + ":" + ZoneId.systemDefault()
                + ":" + new CalendarStore(this).hasReadPermission();
    }

    private void buildShell() {
        wide = getResources().getConfiguration().screenWidthDp >= 600;
        FrameLayout stage = new FrameLayout(this);
        stage.setClipChildren(false); stage.setClipToPadding(false);
        backdrop = new GlassBackdropView(this);
        stage.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        shell.setClipChildren(false); shell.setClipToPadding(false);
        UiStyle.page(this, shell);
        shell.setBackgroundColor(Color.TRANSPARENT);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        attachDockMinimize(scroll);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = 20;
        content.setPadding(dp(horizontal), dp(UiStyle.PAGE_TOP_DP), dp(horizontal),
                dp(wide ? 42 : MOBILE_BOTTOM_AREA_HEIGHT_DP + 24));
        // The cross-fade of a section switch drops a snapshot of the outgoing body next to it, so
        // the body needs a parent that accepts a second child; a ScrollView raises instead.
        FrameLayout contentHost = new FrameLayout(this);
        allowSectionDockOverflow(content, contentHost);
        contentHost.addView(content, new FrameLayout.LayoutParams(-1, -2));
        scroll.addView(contentHost, new FrameLayout.LayoutParams(-1, -2));
        pager = new SwipePagerLayout(this, sectionPagerListener);
        pager.setTaskPagingEnabled(false);
        pager.setFramePacingEnabled(true);
        pager.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        navigation = new DockNavigationLayout(this,
                index -> switchTo(SECTION_ORDER[index]), this::drawNavigation);
        navigation.setHighlightMovementSpeed(1f);
        navigation.setOrientation(wide ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(8), dp(6), dp(8), dp(6));
        FrameLayout mobileBottomArea = null;
        if (wide) {
            FrameLayout rail = new FrameLayout(this); rail.setClipChildren(false); rail.setClipToPadding(false);
            InteractionLayers.pin(rail);
            FrameLayout.LayoutParams railDock = new FrameLayout.LayoutParams(dp(78), LinearLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER);
            rail.addView(navigation, railDock);
            shell.addView(rail, new LinearLayout.LayoutParams(dp(104), -1));
            shell.addView(pager, new LinearLayout.LayoutParams(0, -1, 1));
        } else {
            shell.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
            mobileBottomArea = new FrameLayout(this) {
                @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
                    super.onLayout(changed, left, top, right, bottom);
                    if (dockChrome != null) dockChrome.layoutMobile();
                }
            };
            mobileBottomArea.setClipChildren(false);
            InteractionLayers.pin(mobileBottomArea);
            mobileBottomArea.setClipToPadding(false);
            navigation.setElevation(dp(8));
            FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(-1,
                    dp(MOBILE_DOCK_HEIGHT_DP), Gravity.BOTTOM | Gravity.START);
            dockParams.setMargins(dp(DashboardDockChrome.START_DP), 0, dp(DashboardDockChrome.HOME_END_DP),
                    dp(DashboardDockChrome.BOTTOM_DP));
            mobileBottomArea.addView(navigation, dockParams);
            ImageButton record = UiStyle.recordEntry(this, this::openCapture);
            FrameLayout captureHost = new FrameLayout(this);
            int blurGutter = dp(DashboardDockChrome.BLUR_GUTTER_DP);
            captureHost.setPadding(blurGutter, blurGutter, blurGutter, blurGutter);
            captureHost.setClipChildren(false); captureHost.setClipToPadding(false);
            captureHost.addView(record, new FrameLayout.LayoutParams(-1, -1));
            FrameLayout.LayoutParams recordParams = new FrameLayout.LayoutParams(
                    dp(RECORD_ENTRY_SIZE_DP) + 2 * blurGutter, dp(RECORD_ENTRY_SIZE_DP) + 2 * blurGutter,
                    Gravity.BOTTOM | Gravity.END);
            recordParams.setMargins(0, 0, dp(14) - blurGutter, dp(14) - blurGutter);
            mobileBottomArea.addView(captureHost, recordParams);
            if (navigation != null) navigation.setMinimizeListener(value -> {
                if (dockChrome != null) dockChrome.setMinimize((float) value);
            });
            dockChrome = new DashboardDockChrome(navigation, mobileBottomArea, captureHost, record,
                    MOBILE_DOCK_HEIGHT_DP, section == HOME);
        }
        stage.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        if (wide) {
            ImageButton capture = UiStyle.recordEntry(this, this::openCapture);
            FrameLayout captureHost = new FrameLayout(this);
            int blurGutter = dp(DashboardDockChrome.BLUR_GUTTER_DP);
            captureHost.setPadding(blurGutter, blurGutter, blurGutter, blurGutter);
            captureHost.setClipChildren(false); captureHost.setClipToPadding(false);
            captureHost.addView(capture, new FrameLayout.LayoutParams(-1, -1));
            FrameLayout.LayoutParams floating = new FrameLayout.LayoutParams(dp(56) + 2 * blurGutter, dp(56) + 2 * blurGutter,
                    Gravity.END | Gravity.BOTTOM);
            floating.setMargins(0, 0, dp(24) - blurGutter, dp(24) - blurGutter);
            stage.addView(captureHost, floating);
            dockChrome = new DashboardDockChrome(navigation, null, captureHost, capture,
                    MOBILE_DOCK_HEIGHT_DP, section == HOME);
            UiStyle.applyInsets(stage, shell, captureHost);
        } else {
            FrameLayout.LayoutParams dockOverlay = new FrameLayout.LayoutParams(-1,
                    dp(MOBILE_BOTTOM_AREA_HEIGHT_DP), Gravity.BOTTOM | Gravity.START);
            dockOverlay.setMargins(0, 0, 0, dp(8 + MOBILE_DOCK_PRESS_CLEARANCE_DP));
            stage.addView(mobileBottomArea, dockOverlay);
            UiStyle.applyInsets(stage, shell, mobileBottomArea);
        }
        setContentView(stage);
    }

    private void render() {
        cancelScheduleResults();
        results = null;
        scheduleControls = null;
        filterChips = null;
        categoryChip = null;
        orderToggle = null;
        strips.clear();
        elapsedLabels.clear();
        content.removeAllViews();
        addHeader();
        if (preparedSection != null) {
            pendingReview = preparedSection.review; pendingFailed = preparedSection.failed;
            if (section == HOME) home(preparedSection.home);
            else if (section == INBOX) inbox(null);
            else schedule(null);
        } else if (section == HOME && startupHome != null) {
            pendingReview = startupHome.review;
            pendingFailed = startupHome.failed;
            home(startupHome);
        } else try (TaskStore store = new TaskStore(this)) {
            if (section == HOME) {
                HomeData data = new HomeData(this, store);
                pendingReview = data.review;
                pendingFailed = data.failed;
                home(data);
            } else {
                pendingReview = store.taskCountByStatus(TaskRecord.NEEDS_REVIEW);
                pendingFailed = store.taskCountByStatus(TaskRecord.FAILED);
                if (section == INBOX) inbox(store);
                else schedule(store);
            }
        } catch (Exception exception) {
            message(content, "暂时无法读取日程：" + exception.getMessage());
        }
        ViewGroup cards = results == null ? content : results;
        if (!restoringMotion && motionSections.add(section)) UiMotion.observeScroll(scroll, cards);
        else UiMotion.settleScroll(scroll, cards);
        drawNavigation();
        if ((section == HOME || (section == SCHEDULE && scheduleSource == 1)) && !previewRender)
            scroll.post(this::refreshSystemCalendar);
        if (pager != null) pager.setGesturePriorityChildren(strips);
        rememberRenderedPage();
    }

    /** Leading children owned by the title row, so a reconcile never re-adds the first card. */
    private int headerChildCount = 1;

    private void addHeader() {
        // One title row per page: the date rides beside the home title instead of claiming a row
        // above it, so the first content starts at the shared top offset and the icon centre lands
        // on the 40dp baseline.
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(UiStyle.HEADER_HEIGHT_DP));
        TextView title = text(section == HOME ? "今天的安排"
                : section == INBOX ? "收件箱" : "日程", 24, true);
        title.setMaxLines(section == HOME ? 2 : 1);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        // Without this the large CJK glyphs ride above the centre line and no longer match the
        // search field, the order arrow and the gear they share the row with.
        title.setIncludeFontPadding(false);
        title.setGravity(Gravity.CENTER_VERTICAL);
        if (section == HOME) {
            FrameLayout titleSlot = new FrameLayout(this);
            titleSlot.addView(underlinedTitle(title, "今天的安排，点击查看课表", TimetableActivity.class),
                    new FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_VERTICAL));
            row.addView(titleSlot, new LinearLayout.LayoutParams(-2, -2));
            TextView date = text(new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date()), 13, false);
            LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(-2, -2);
            dateParams.leftMargin = dp(8);
            row.addView(date, dateParams);
            row.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        } else {
            // The search field sits between the page title and the trailing control, so filtering
            // never moves the settings entry and the keyword stays reachable while scrolling.
            if (section == SCHEDULE) {
                row.addView(underlinedTitle(title, "日程，点击打开课表对比", ScheduleCompareActivity.class),
                        new LinearLayout.LayoutParams(-2, -2));
            } else row.addView(underlinedTitle(title, "收件箱，点击打开日历", CalendarOverviewActivity.class),
                    new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, dp(48), 1);
            searchParams.setMargins(dp(8), 0, dp(8), 0);
            row.addView(buildSearchField(), searchParams);
        }
        LinearLayout.LayoutParams settingsParams = new LinearLayout.LayoutParams(-2, dp(48));
        if (section == HOME) settingsParams.leftMargin = dp(8);
        row.addView(buildHeaderControls(), settingsParams);
        UiStyle.addSpaced(content, row, 0, 12);
        headerChildCount = content.getChildCount();
    }

    /** Shared title entry: center the underline on the glyph ink, with equal side extension. */
    private View underlinedTitle(TextView title, String description, Class<? extends Activity> destination) {
        LinearLayout entry = new LinearLayout(this);
        entry.setOrientation(LinearLayout.VERTICAL);
        entry.setGravity(Gravity.CENTER_VERTICAL);
        entry.setMinimumHeight(dp(44));
        entry.addView(title, new LinearLayout.LayoutParams(-2, -2));
        View underline = new View(this) {
            private final android.graphics.Rect ink = new android.graphics.Rect();
            private final android.graphics.Paint paint = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(android.graphics.Canvas canvas) {
                super.onDraw(canvas);
                android.text.Layout layout = title.getLayout();
                if (layout == null) return;
                String label = title.getText().toString();
                title.getPaint().getTextBounds(label, 0, label.length(), ink);
                float origin = title.getLeft() - getLeft() + title.getTotalPaddingLeft() + layout.getLineLeft(0);
                float left = origin + ink.left, right = origin + ink.right;
                float extension = Math.max(0, Math.min(dp(2), Math.min(left, getWidth() - right)));
                paint.setColor(UiStyle.colors(DashboardActivity.this).primary);
                canvas.drawRoundRect(left - extension, 0, right + extension, getHeight(), dp(1), dp(1), paint);
            }
        };
        underline.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(2));
        line.topMargin = dp(3);
        entry.addView(underline, line);
        entry.setContentDescription(description);
        entry.setFocusable(true);
        UiStyle.pressable(entry);
        entry.setOnClickListener(view -> startActivity(new Intent(this, destination)));
        return entry;
    }

    /**
     * The order arrow and the settings entry share a single pill: two separate bubbles in the same
     * corner read as unrelated controls and cost width the search field needs.
     */
    private LinearLayout buildHeaderControls() {
        HeaderActionGroup group = new HeaderActionGroup(this, 48);
        orderToggle = null;
        if (section == HOME) {
            if (new ZjuSettingsStore(this).enabled()) {
                ImageButton homework = WorkspacePageHeader.icon(this, R.drawable.ic_homework,
                        "最近作业", this::openHomeHomework);
                homework.setTag("home-homework");
                GradientDrawable dot = new GradientDrawable();
                dot.setShape(GradientDrawable.OVAL); dot.setColor(HOMEWORK_UNREAD_COLOR);
                dot.setAlpha(unreadHomeworkId > 0 ? 255 : 0);
                homework.getOverlay().add(dot); homeworkDots.put(homework, dot);
                homework.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
                    int x = (view.getWidth()+view.getPaddingLeft()-view.getPaddingRight()) / 2 + dp(9), y = view.getHeight() / 2 - dp(9), radius = dp(4);
                    dot.setBounds(x - radius, y - radius, x + radius, y + radius);
                });
                homework.setContentDescription(unreadHomeworkId > 0 ? "最近作业，有新作业" : "最近作业");
                group.addAction(homework, 48);
            }
            group.addAction(homeSystemCalendarToggle(), 48);
        } else {
            TextView order = text("", 15, true);
            order.setGravity(Gravity.CENTER);
            order.setMinWidth(dp(48));
            order.setPadding(dp(10), 0, dp(10), 0);
            order.setOnClickListener(view -> toggleOrder());
            UiStyle.pressable(order);
            orderToggle = order;
            refreshOrderToggle();
            group.addAction(order, 48);
        }
        ImageButton settings = new ImageButton(this);
        settings.setImageResource(R.drawable.ic_settings);
        settings.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        settings.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        settings.setPadding(dp(10), dp(10), dp(10), dp(10));
        settings.setBackgroundColor(Color.TRANSPARENT);
        settings.setContentDescription("打开设置");
        settings.setOnClickListener(view ->
                startActivity(new Intent(this, SettingsHubActivity.class)));
        UiStyle.pressable(settings);
        group.addAction(settings, 48);
        group.finish();
        return group;
    }

    private void toggleOrder() {
        if (section == INBOX) {
            inboxOldestFirst = !inboxOldestFirst;
            inboxPage = 0;
        } else {
            scheduleOldestFirst = !scheduleOldestFirst;
            ScheduleOrderPreferences.setOldestFirst(this, scheduleTab, scheduleOldestFirst);
            schedulePage = 0;
        }
        refreshOrderToggle();
        updateResultsWithEntrance();
    }

    /** A compact keyword field for the two browsable lists; typing only re-renders the results. */
    private EditText buildSearchField() {
        final int ownerSection = section;
        EditText field = new EditText(this);
        if (ownerSection == SCHEDULE) field.setTag("schedule_search");
        field.setSingleLine(true);
        field.setHint(section == INBOX ? "搜索收件" : "搜索日程");
        field.setTextSize(13);
        field.setIncludeFontPadding(false);
        field.setTextColor(UiStyle.colors(this).text);
        field.setHintTextColor(UiStyle.colors(this).muted);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setGravity(Gravity.CENTER_VERTICAL);
        field.setPadding(dp(12), 0, dp(12), 0);
        UiStyle.acrylicChoice(field, false, UiStyle.RADIUS_PILL, false);
        String current = section == INBOX ? inboxQuery : scheduleQuery;
        field.setText(current);
        if (!current.isEmpty()) field.setSelection(current.length());
        field.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence value, int a, int b, int c) { }
            @Override public void afterTextChanged(Editable value) {
                String keyword = value.toString();
                if (keyword.equals(ownerSection == INBOX ? inboxQuery : scheduleQuery)) return;
                if (ownerSection == INBOX) {
                    inboxQuery = keyword;
                    inboxPage = 0;
                } else {
                    scheduleQuery = keyword;
                    schedulePage = 0;
                }
                if (section != ownerSection) {
                    dirtySections.add(ownerSection);
                    return;
                }
                updateResults();
                scroll.scrollTo(0, 0);
            }
        });
        return field;
    }

    /** One arrow is enough: it flips between time-ascending and time-descending. */
    private void refreshOrderToggle() {
        if (orderToggle == null) return;
        boolean oldestFirst = section == INBOX ? inboxOldestFirst : scheduleOldestFirst;
        orderToggle.setText(oldestFirst ? "↑" : "↓");
        orderToggle.setContentDescription(oldestFirst
                ? "时间顺序排列，最早在前；点击改为最近在前"
                : "时间倒序排列，最近在前；点击改为最早在前");
        orderToggle.setTextColor(UiStyle.colors(this).primary);
    }

    private static String normalizedQuery(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean containsQuery(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query);
    }

    private CheckBox homeSystemCalendarToggle() {
        CheckBox include = new CalendarVisibilityToggle(this);
        include.setButtonDrawable((android.graphics.drawable.Drawable) null);
        include.setContentDescription("包含系统其他日程");
        include.setTooltipText("包含系统其他日程");
        include.setPadding(dp(10), 0, dp(10), 0);
        include.setGravity(Gravity.CENTER_VERTICAL);
        include.setBackgroundColor(Color.TRANSPARENT);
        UiStyle.toggle(include);
        include.setMinHeight(dp(44));
        include.setMinimumHeight(dp(44));
        UiStyle.pressable(include);
        include.setChecked(HomeTimelinePreferences.includesSystemCalendar(this));
        Runnable describe = () -> {
            String state = include.isChecked() ? "已开启" : "已关闭";
            include.setTooltipText("包含系统其他日程：" + state);
            if (android.os.Build.VERSION.SDK_INT >= 30) include.setStateDescription(state);
            include.invalidate();
        };
        describe.run();
        include.setOnCheckedChangeListener((button, enabled) -> {
            describe.run();
            if (enabled && !new CalendarStore(this).hasReadPermission()) {
                include.setChecked(false);
                describe.run();
                requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR},
                        HOME_CALENDAR_PERMISSION_REQUEST);
                return;
            }
            HomeTimelinePreferences.setIncludesSystemCalendar(this, enabled);
            calendarGeneration++;
            lastCalendarFetch = 0;
            systemCalendarEvents = java.util.Collections.emptyList();
            calendarResultWindow = "";
            // Keep the current header alive until its checked-state transition finishes.
            refresh.removeCallbacks(homeCalendarToggleRefresh);
            refresh.postDelayed(homeCalendarToggleRefresh, UiMotion.isEnabled() ? UiMotion.ENTER : 0L);
        });
        return include;
    }

    private void home(HomeData data) {
        updateHomeworkAttention(data.unreadHomework);
        long now = data.now;
        ZoneId zone = data.zone;
        LocalDate today = data.today;
        LocalDate endDate = data.endDate;
        int limit = data.limit;
        List<EventCandidate> candidates = data.candidates;
        List<HomeTimelineEntry> upcoming = new ArrayList<>();
        for (EventCandidate candidate : data.upcoming) upcoming.add(new HomeTimelineEntry(candidate));
        List<CalendarOccurrence> external = CalendarOccurrence.unlinked(
                calendarEventsForWindow(new LocalDate[]{today, endDate}), new HashSet<>(data.linkedIds));
        for (CalendarOccurrence event : external)
            if (event.displayEnd(zone) > now) upcoming.add(new HomeTimelineEntry(event));
        showUpcomingCarousel(upcoming);
        showHomeTodos(data.todos, now, zone);

        long rangeStart = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long rangeEnd = endDate.atStartOfDay(zone).toInstant().toEpochMilli();
        List<HomeTimelineEntry> all = new ArrayList<>();
        Set<String> candidateIds = new HashSet<>();
        for (EventCandidate item : candidates) {
            if (!candidateIntersects(item, today, endDate, rangeStart, rangeEnd, now))
                continue;
            if (!candidateIds.add(item.occurrenceKey())) continue;
            all.add(new HomeTimelineEntry(item));
        }
        for (CalendarOccurrence event : external) {
            if (event.displayEnd(zone) > now && event.displayStart(zone) < rangeEnd)
                all.add(new HomeTimelineEntry(event));
        }
        all.sort(java.util.Comparator.comparingLong(entry -> entry.timestampMillis));
        if (all.size() > limit) all = new ArrayList<>(all.subList(0, limit));
        Map<LocalDate, List<HomeTimelineEntry>> byDate = new java.util.TreeMap<>();
        for (HomeTimelineEntry entry : all) {
            LocalDate date = timelineDate(entry, today, zone);
            byDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<LocalDate, List<HomeTimelineEntry>> day : byDate.entrySet()) {
            timelineDay(timelineDayTitle(day.getKey(), today), day.getKey(), day.getValue());
        }
    }

    private void showHomeTodos(HomeTodoSummary todos, long now, ZoneId zone) {
        android.content.SharedPreferences preferences = getSharedPreferences("home_timeline", MODE_PRIVATE);
        boolean[] expanded = {preferences.getBoolean("todos_expanded", false)};
        android.animation.ValueAnimator[] expansion = {null};
        LinearLayout summary = card();
        completionRows.branch(summary, "home-todos");
        UiStyle.cardInset(summary);
        summary.setClipChildren(false);
        summary.setClipToPadding(false);
        TextView title = text("待办 · " + todos.total, 16, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setMinimumHeight(dp(48));
        TextView all = text("全部待办", 13, true);
        all.setTextColor(UiStyle.colors(this).primary);
        all.setGravity(Gravity.CENTER);
        all.setMinimumHeight(dp(48));
        all.setMaxLines(2);
        all.setPadding(dp(8), 0, dp(8), 0);
        all.setContentDescription("查看全部未完成待办，共 " + todos.total + " 项");
        all.setOnClickListener(view -> openHomeTodos(ScheduleQuery.TODO_OPEN, false));
        UiStyle.pressable(all);
        int actionWidth=dp(todos.focus.isEmpty()?56:105);
        LinearLayout heading = new LinearLayout(this) {
            @Override protected void onMeasure(int widthSpec,int heightSpec) {
                int available=View.MeasureSpec.getSize(widthSpec)-getPaddingLeft()-getPaddingRight();
                float required=title.getPaint().measureText(title.getText().toString())
                        +all.getPaint().measureText(all.getText().toString())+all.getPaddingLeft()+all.getPaddingRight()+actionWidth;
                LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)all.getLayoutParams();
                params.width=available>0&&required>available
                        ?(int)Math.ceil(all.getPaint().measureText("全部"))+all.getPaddingLeft()+all.getPaddingRight():-2;
                super.onMeasure(widthSpec,heightSpec);
            }
        };
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setMinimumHeight(dp(48));
        heading.addView(title, new LinearLayout.LayoutParams(0, -1, 1));
        heading.addView(all);
        HeaderActionGroup todoActions=new HeaderActionGroup(this,48);
        ImageButton homeworkRefresh=UiStyle.navigationIcon(this,R.drawable.ic_refresh,"刷新作业",this::refreshHomeHomework);
        homeworkRefresh.setTag("home-homework-refresh");
        homeworkRefresh.setEnabled(!homeworkRefreshRunning);
        homeworkRefresh.setAlpha(homeworkRefreshRunning?.45f:1f);
        homeworkRefresh.setContentDescription(homeworkRefreshRunning?"正在刷新作业":"刷新作业");
        todoActions.addAction(homeworkRefresh,48);
        ImageButton disclosure = new ImageButton(this);
        RotateDrawable disclosureIcon = new RotateDrawable();
        disclosureIcon.setDrawable(getDrawable(R.drawable.ic_chevron_down));
        disclosureIcon.setFromDegrees(0f);
        disclosureIcon.setToDegrees(180f);
        disclosure.setImageDrawable(disclosureIcon);
        disclosure.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        disclosure.setPadding(dp(12), dp(12), dp(12), dp(12));
        disclosure.setBackgroundColor(Color.TRANSPARENT);
        UiStyle.pressable(disclosure);
        if(!todos.focus.isEmpty())todoActions.addAction(disclosure,48);
        todoActions.finish();
        LinearLayout.LayoutParams todoActionParams=new LinearLayout.LayoutParams(-2,dp(48));
        todoActionParams.leftMargin=dp(8);
        heading.addView(todoActions,todoActionParams);
        summary.addView(heading);

        LinearLayout counts = new LinearLayout(this);
        counts.setGravity(Gravity.CENTER_VERTICAL);
        addHomeTodoCount(counts, "今日截止", todos.todayDue,
                () -> openHomeTodos(ScheduleQuery.TODO_TODAY_DUE, false));
        addHomeTodoCount(counts, "已逾期", todos.overdue,
                () -> openHomeTodos(ScheduleQuery.TODO_OVERDUE, false));
        addHomeTodoCount(counts, "待安排", todos.unscheduled,
                () -> openHomeTodos(ScheduleQuery.TODO_ANY, true));
        UiStyle.addSpaced(summary, counts, 0, 0);
        LinearLayout details = new LinearLayout(this);
        completionRows.branch(details, "home-todo-details");
        details.setTag("home-todo-details");
        details.setOrientation(LinearLayout.VERTICAL);
        details.setPadding(0, dp(6), 0, dp(4));
        details.setClipChildren(false);
        details.setClipToPadding(false);
        for (EventCandidate candidate : todos.focus) {
            LinearLayout focusRow = new LinearLayout(this);
            focusRow.setOrientation(LinearLayout.VERTICAL);
            completionRows.row(focusRow, "focus:" + candidate.id, completionState(candidate, null));
            View divider = new View(this);
            divider.setBackgroundColor(UiStyle.colors(this).outline);
            focusRow.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setMinimumHeight(dp(64));
            LinearLayout labels = new LinearLayout(this);
            labels.setOrientation(LinearLayout.VERTICAL);
            labels.setGravity(Gravity.CENTER_VERTICAL);
            labels.setMinimumHeight(dp(56));
            TextView label = text(candidate.title, 14, true);
            label.setMaxLines(1);
            label.setEllipsize(android.text.TextUtils.TruncateAt.END);
            labels.addView(label);
            TextView when = text(homeTodoWhen(candidate, now, zone), 12, false);
            if (candidate.isHomework()) {
                android.graphics.drawable.Drawable homeworkIcon = getDrawable(R.drawable.ic_homework);
                homeworkIcon.setBounds(0, 0, dp(16), dp(16));
                when.setCompoundDrawablesRelative(homeworkIcon, null, null, null);
                when.setCompoundDrawableTintList(android.content.res.ColorStateList.valueOf(UiStyle.colors(this).primary));
                when.setCompoundDrawablePadding(dp(4));
            }
            when.setMaxLines(1);
            when.setEllipsize(android.text.TextUtils.TruncateAt.END);
            UiStyle.addSpaced(labels, when, 3, 0);
            labels.setOnClickListener(view -> openCandidate(candidate));
            UiStyle.pressable(labels);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(taskCompletionButton(candidate), new LinearLayout.LayoutParams(dp(48), dp(48)));
            PressFeedback.group(row, 1f);
            focusRow.addView(row);
            details.addView(focusRow);
        }
        summary.addView(details, new LinearLayout.LayoutParams(-1, -2));
        details.setVisibility(expanded[0] && !todos.focus.isEmpty() ? View.VISIBLE : View.GONE);
        disclosureIcon.setLevel(expanded[0] ? 10000 : 0);
        Runnable describeExpansion = () -> {
            String action = expanded[0] ? "收起重点待办" : "展开重点待办";
            title.setContentDescription("待办，共 " + todos.total + " 项，" + action);
            disclosure.setContentDescription(action);
        };
        describeExpansion.run();
        View.OnClickListener toggle = view -> {
            View owner=view;
            while(owner.findViewWithTag("home-todo-details")==null)owner=(View)owner.getParent();
            LinearLayout liveDetails = owner.findViewWithTag("home-todo-details");
            expanded[0] = !expanded[0];
            preferences.edit().putBoolean("todos_expanded", expanded[0]).apply();
            describeExpansion.run();
            android.animation.ValueAnimator previous = expansion[0];
            expansion[0] = null;
            if (previous != null) previous.cancel();
            View actualSummary = (View) liveDetails.getParent();
            int detailsWidth = actualSummary.getWidth() - actualSummary.getPaddingLeft() - actualSummary.getPaddingRight();
            if (!UiMotion.isEnabled() || detailsWidth <= 0) {
                liveDetails.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
                liveDetails.getLayoutParams().height = -2;
                liveDetails.setAlpha(1f);
                liveDetails.setClipChildren(false);
                disclosureIcon.setLevel(expanded[0] ? 10000 : 0);
                liveDetails.requestLayout();
                return;
            }
            int initialHeight = liveDetails.getVisibility() == View.GONE ? 0 : liveDetails.getHeight();
            float initialAlpha = initialHeight == 0 ? 0f : liveDetails.getAlpha();
            float initialRotation = disclosureIcon.getLevel();
            liveDetails.setVisibility(View.VISIBLE);
            liveDetails.measure(View.MeasureSpec.makeMeasureSpec(detailsWidth, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int targetHeight = expanded[0] ? liveDetails.getMeasuredHeight() : 0;
            float targetAlpha = expanded[0] ? 1f : 0f;
            float targetRotation = expanded[0] ? 10000f : 0f;
            liveDetails.setClipChildren(true);
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
            expansion[0] = animator;
            animator.setDuration(expanded[0] ? UiMotion.ENTER : UiMotion.EXIT);
            animator.setInterpolator(UiMotion.SETTLE);
            animator.addUpdateListener(value -> {
                float fraction = (float) value.getAnimatedValue();
                liveDetails.getLayoutParams().height = initialHeight + Math.round((targetHeight - initialHeight) * fraction);
                liveDetails.setAlpha(initialAlpha + (targetAlpha - initialAlpha) * fraction);
                disclosureIcon.setLevel(Math.round(initialRotation + (targetRotation - initialRotation) * fraction));
                liveDetails.requestLayout();
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (expansion[0] != animator) return;
                    expansion[0] = null;
                    liveDetails.setVisibility(expanded[0] ? View.VISIBLE : View.GONE);
                    liveDetails.getLayoutParams().height = -2;
                    liveDetails.setAlpha(1f);
                    liveDetails.setClipChildren(false);
                    disclosureIcon.setLevel(Math.round(targetRotation));
                    liveDetails.requestLayout();
                }
            });
            animator.start();
        };
        if (todos.focus.isEmpty()) {
            disclosure.setVisibility(View.GONE);
            title.setContentDescription("待办，共 " + todos.total + " 项");
        } else {
            title.setOnClickListener(toggle);
            disclosure.setOnClickListener(toggle);
            UiStyle.pressable(title);
        }
        PressFeedback.group(heading, 1f);
        heading.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override public void onViewAttachedToWindow(View view) { }
            @Override public void onViewDetachedFromWindow(View view) {
                if (expansion[0] != null) expansion[0].cancel();
            }
        });
        PressFeedback.group(counts, 1f);
        details.setAddStatesFromChildren(true);
        PressFeedback.group(summary, PressFeedback.CARD_PRESSED_SCALE);
        UiStyle.addSpaced(content, summary, 4, 14);
        summary.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            int clearance = PressFeedback.edgeSpace(b - t);
            LinearLayout.LayoutParams params = (LinearLayout.LayoutParams) summary.getLayoutParams();
            int top = dp(4) + clearance, bottom = dp(14) + clearance;
            if (params.topMargin != top || params.bottomMargin != bottom) {
                params.topMargin = top; params.bottomMargin = bottom; summary.setLayoutParams(params);
            }
        });
    }

    private void addHomeTodoCount(LinearLayout parent, String label, int count, Runnable action) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setPadding(dp(3), dp(7), dp(3), dp(7));
        TextView number = text(Integer.toString(count), 17, true);
        number.setIncludeFontPadding(false);
        number.setTextColor(count > 0 ? UiStyle.colors(this).primary : UiStyle.colors(this).muted);
        cell.addView(number);
        TextView caption = text(label, 12, false);
        caption.setIncludeFontPadding(false);
        UiStyle.addSpaced(cell, caption, 5, 0);
        cell.setContentDescription(label + "，" + count + " 项，点击查看");
        cell.setOnClickListener(view -> action.run());
        UiStyle.pressable(cell);
        parent.addView(cell, new LinearLayout.LayoutParams(0, -2, 1));
    }

    private String homeTodoWhen(EventCandidate candidate, long now, ZoneId zone) {
        Long deadline = candidate.effectiveDueAtMillis();
        if (deadline != null) {
            LocalDate today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate();
            LocalDate due = Instant.ofEpochMilli(deadline).atZone(zone).toLocalDate();
            String prefix = (candidate.isHomework() ? "作业" : "") + (today.equals(due) ? "今日截止 · " : "截止 · ");
            String pattern = today.equals(due) ? "HH:mm"
                    : due.getYear() == today.getYear() ? "M月d日 HH:mm" : "yyyy年M月d日 HH:mm";
            return prefix + new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(deadline));
        }
        return candidate.startAtMillis == null ? "待安排" : "执行 · " + formatWhen(candidate);
    }

    private void refreshHomeHomework() {
        if(homeworkRefreshRunning)return;
        ZjuSettingsStore settings=new ZjuSettingsStore(this);
        if(!settings.enabled()||!settings.configured()||!new ZjuHomeworkStore(this).state().enabled) {
            startActivity(new Intent(this,ZjuSettingsActivity.class));return;
        }
        homeworkRefreshRunning=true;
        updateHomeworkRefreshButtons(getWindow().getDecorView());
        ZjuSync.requestHomework(this,status->{
            homeworkRefreshRunning=false;
            if(isDestroyed())return;
            updateHomeworkRefreshButtons(getWindow().getDecorView());
            dirtySections.add(HOME);dirtySections.add(SCHEDULE);dirtySections.add(INBOX);
            refreshHomeContent();
            Feedback.show(this,status);
        });
    }
    private void updateHomeworkRefreshButtons(View view) {
        if("home-homework-refresh".equals(view.getTag())) {
            view.setEnabled(!homeworkRefreshRunning);
            view.setAlpha(homeworkRefreshRunning?.45f:1f);
            view.setContentDescription(homeworkRefreshRunning?"正在刷新作业":"刷新作业");
        }
        if(view instanceof ViewGroup group)for(int i=0;i<group.getChildCount();i++)updateHomeworkRefreshButtons(group.getChildAt(i));
    }

    private void openHomeTodos(int scope, boolean unscheduled) {
        scheduleTab = unscheduled ? 1 : 3;
        scheduleTodoScope = unscheduled ? ScheduleQuery.TODO_OPEN : scope;
        scheduleRange = schedulePublication = scheduleSource = schedulePage = 0;
        scheduleCategory = 0;
        scheduleDate = scheduleUntil = LocalDate.now();
        scheduleQuery = "";
        scheduleOldestFirst = ScheduleOrderPreferences.oldestFirst(this, scheduleTab);
        selectedScheduleIds.clear();
        sectionPages.remove(SCHEDULE);
        sectionVersions.remove(SCHEDULE);
        dirtySections.add(SCHEDULE);
        switchTo(SCHEDULE);
    }

    private void openHomeHomework() {
        if (!new ZjuSettingsStore(this).enabled()) return;
        pendingHomeworkFocus = unreadHomeworkId;
        homeworkFocusTop = homeworkFocusBottom = 0; homeworkFocusFilter = "";
        scheduleTab = 3; scheduleTodoScope = ScheduleQuery.TODO_OPEN;
        scheduleCategory = com.donglan.chrona.data.ScheduleFilterState.HOMEWORK_CATEGORY;
        scheduleRange = schedulePublication = scheduleSource = schedulePage = 0;
        scheduleDate = scheduleUntil = LocalDate.now(); scheduleQuery = "";
        scheduleOldestFirst = true;
        selectedScheduleIds.clear();
        sectionPages.remove(SCHEDULE); sectionVersions.remove(SCHEDULE); dirtySections.add(SCHEDULE);
        switchTo(SCHEDULE);
    }

    private void updateHomeworkAttention(long candidateId) {
        unreadHomeworkId = candidateId;
        for (var entry : homeworkDots.entrySet()) {
            entry.getValue().setAlpha(candidateId > 0 ? 255 : 0);
            entry.getKey().setContentDescription(candidateId > 0 ? "最近作业，有新作业" : "最近作业");
            entry.getKey().invalidate();
        }
    }

    private void queueHomeworkFocus() {
        refresh.removeCallbacks(homeworkFocus);
        if (pendingHomeworkFocus > 0) refresh.postDelayed(homeworkFocus, 16);
    }

    private void focusNewHomework() {
        if (isDestroyed() || isFinishing() || !foreground || !hasWindowFocus() || section != SCHEDULE || pendingHomeworkFocus <= 0) return;
        if (pageTransitionRunning || sectionLoadRunning || scheduleResultLoading) { queueHomeworkFocus(); return; }
        InboxRow row = inboxRows.get(pendingHomeworkFocus);
        if (row == null) {
            pendingHomeworkFocus = 0;
            Feedback.show(this, "这项作业已更新，请查看当前作业列表");
            return;
        }
        View card = row.card;
        if (!card.isLaidOut() || scroll.getHeight() <= 0 || card.isLayoutRequested()) { queueHomeworkFocus(); return; }
        int[] point = new int[2], viewport = new int[2];
        card.getLocationOnScreen(point); scroll.getLocationOnScreen(viewport);
        int desired = scroll.getScrollY() + point[1] + card.getHeight() / 2 - viewport[1] - scroll.getHeight() / 2;
        View body = scroll.getChildAt(0);
        int maximum = Math.max(0, body.getHeight() + scroll.getPaddingTop() + scroll.getPaddingBottom() - scroll.getHeight());
        // Only the explicit focus route adds the real scroll room needed to center edge rows.
        if (desired < 0 || desired > maximum) {
            homeworkFocusTop += Math.max(0, -desired);
            homeworkFocusBottom += Math.max(0, desired - maximum);
            homeworkFocusFilter = completionFilterKey();
            results.setPadding(0, homeworkFocusTop, 0, homeworkFocusBottom);
            queueHomeworkFocus(); return;
        }
        scroll.scrollTo(0, desired);
        long shown = pendingHomeworkFocus;
        card.getViewTreeObserver().addOnPreDrawListener(new android.view.ViewTreeObserver.OnPreDrawListener() {
            @Override public boolean onPreDraw() {
                if (card.getViewTreeObserver().isAlive()) card.getViewTreeObserver().removeOnPreDrawListener(this);
                // Consume attention after the centered frame has actually been drawn, not
                // while a notification shade or another window still covers this page.
                card.post(() -> completeHomeworkFocus(card, shown));
                return true;
            }
        });
        card.invalidate();
    }

    private void completeHomeworkFocus(View card, long shown) {
        if (pendingHomeworkFocus != shown || isDestroyed() || isFinishing() || !foreground
                || !hasWindowFocus() || section != SCHEDULE || !card.isAttachedToWindow()) return;
        android.graphics.Rect visible = new android.graphics.Rect();
        if (!card.getGlobalVisibleRect(visible)) return;
        int[] point = new int[2]; card.getLocationOnScreen(point);
        if (!visible.contains(point[0] + card.getWidth() / 2, point[1] + card.getHeight() / 2)) return;
        pendingHomeworkFocus = 0;
        GradientDrawable focus = new GradientDrawable();
        int primary = UiStyle.colors(this).primary;
        focus.setColor(Color.argb(36, Color.red(primary), Color.green(primary), Color.blue(primary)));
        focus.setStroke(dp(2), primary); focus.setCornerRadius(dp(Math.round(UiStyle.radius(this, UiStyle.RADIUS_CARD))));
        android.graphics.drawable.Drawable previous = card.getForeground();
        card.setForeground(focus);
        if (android.os.Build.VERSION.SDK_INT >= 30) card.setStateDescription("新作业");
        if (UiMotion.isEnabled()) {
            android.animation.ValueAnimator fade = android.animation.ValueAnimator.ofInt(255, 0);
            fade.setStartDelay(UiMotion.ENTER); fade.setDuration(HOMEWORK_FOCUS_DURATION);
            fade.addUpdateListener(animation -> focus.setAlpha((int) animation.getAnimatedValue()));
            fade.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (card.getForeground() == focus) { card.setForeground(previous); if (android.os.Build.VERSION.SDK_INT >= 30) card.setStateDescription(null); }
                }
            });
            fade.start();
        } else card.postDelayed(() -> { if (card.getForeground() == focus) { card.setForeground(previous); if (android.os.Build.VERSION.SDK_INT >= 30) card.setStateDescription(null); } }, HOMEWORK_FOCUS_DURATION);
        startupReader.execute(() -> {
            ZjuHomeworkStore store = new ZjuHomeworkStore(getApplicationContext());
            store.markSeen(shown); long next = store.newestUnread();
            refresh.post(() -> { if (!isDestroyed()) updateHomeworkAttention(next); });
        });
    }

    private void showUpcomingCarousel(List<HomeTimelineEntry> upcoming) {
        TextView heading = text("下一件事", 15, true);
        UiStyle.addSpaced(content, heading, 0, 6);
        upcoming.sort(java.util.Comparator.comparingLong(item -> item.timestampMillis));
        if (upcoming.size() > 5) upcoming = new ArrayList<>(upcoming.subList(0, 5));
        HorizontalScrollView strip = new HorizontalScrollView(this);
        completionRows.branch(strip, "upcoming-strip");
        strip.setHorizontalScrollBarEnabled(false);
        strip.setFillViewport(false);
        strip.setClipToPadding(false);
        // Keep the first card aligned with page content, with room for its pressed scale.
        strip.setPadding(0, 0, dp(12), 0);
        strips.add(strip);
        LinearLayout cards = new LinearLayout(this);
        completionRows.branch(cards, "upcoming-cards");
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setClipChildren(false); cards.setClipToPadding(false);
        // The viewport clips its direct child: motion room belongs inside that child.
        cards.setPadding(dp(8), dp(6), dp(8), dp(6));
        if (upcoming.isEmpty()) {
            LinearLayout empty = card();
            empty.setPadding(dp(14), dp(12), dp(14), dp(12));
            empty.addView(text("暂无即将开始或进行中的安排", 14, false));
            cards.addView(empty, new LinearLayout.LayoutParams(dp(240), -2));
        } else {
            for (HomeTimelineEntry entry : upcoming) {
                LinearLayout item = card();
                completionRows.row(item, "upcoming:" + completionKey(entry.candidate, entry.systemEvent),
                        completionState(entry.candidate, entry.systemEvent));
                UiStyle.cardInset(item);
                TextView title = text(entry.title(), 16, true);
                title.setIncludeFontPadding(false);
                title.setMinLines(2); title.setMaxLines(2);
                title.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
                int summaryHeight = title.getMeasuredHeight();
                String location = entry.location();
                title.setMinLines(location.isEmpty() ? 2 : 1);
                title.setMaxLines(location.isEmpty() ? 2 : 1);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                FrameLayout summary = new FrameLayout(this);
                summary.addView(title, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP));
                if (!location.isEmpty()) {
                    TextView place = text(location, 13, false);
                    place.setIncludeFontPadding(false); place.setSingleLine(true);
                    place.setEllipsize(android.text.TextUtils.TruncateAt.END);
                    place.setContentDescription("地点：" + location);
                    summary.addView(place, new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM));
                }
                item.addView(summary, new LinearLayout.LayoutParams(-1, summaryHeight));
                UiStyle.addSpaced(item, scheduleAttribute(entry.candidate != null && entry.candidate.isHomework()
                                ? R.drawable.ic_homework : R.drawable.ic_schedule,
                        homeCardBoundary(entry.candidate, entry.systemEvent, false, true),
                        "时间", 14, 1, false), 6, 0);
                item.setOnClickListener(view -> openHomeEntry(entry));
                UiStyle.pressable(item);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(240), -2);
                params.setMargins(0, 0, dp(10), 0);
                cards.addView(item, params);
            }
        }
        strip.addView(cards);
        LinearLayout.LayoutParams stripParams = new LinearLayout.LayoutParams(-1, -2);
        stripParams.setMargins(-dp(8), 0, 0, dp(10));
        content.addView(strip, stripParams);
    }

    private boolean candidateIntersects(EventCandidate item, LocalDate today, LocalDate endDate,
            long rangeStart, long rangeEnd, long now) {
        if (!HomeTimelineEntry.hasUpcomingTime(item, now, ZoneId.systemDefault())) return false;
        if (HomeTimelineEntry.deadlineOnly(item)) return item.dueAtMillis >= rangeStart && item.dueAtMillis < rangeEnd;
        if (item.executionAllDay()) {
            LocalDate start = LocalDate.parse(AllDayDates.displayStart(item.executionStartAtMillis()));
            LocalDate inclusiveEnd = LocalDate.parse(AllDayDates.displayEnd(HomeTimelineEntry.executionEnd(item)));
            return !inclusiveEnd.isBefore(today) && start.isBefore(endDate);
        }
        return HomeTimelineEntry.executionEnd(item) >= rangeStart && item.executionStartAtMillis() < rangeEnd;
    }

    private LocalDate timelineDate(HomeTimelineEntry entry, LocalDate today, ZoneId zone) {
        LocalDate date = Instant.ofEpochMilli(entry.timestampMillis).atZone(zone).toLocalDate();
        return date.isBefore(today) ? today : date;
    }

    private String timelineDayTitle(LocalDate date, LocalDate today) {
        if (date.equals(today)) return "今天";
        if (date.equals(today.plusDays(1))) return "明天";
        DayOfWeek day = date.getDayOfWeek();
        String[] week = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
        return week[day.getValue() - 1];
    }

    private void inbox(TaskStore store) {
        filterChips = filterDock(new String[]{"全部", "待确认", "处理中", "失败", "已写入"},
                statusIndex, index -> {
                    if (statusIndex == index) return;
                    clearInboxSelection();
                    statusIndex = index;
                    inboxPage = 0;
                    updateChipSelection(statusIndex);
                    updateResultsWithEntrance();
                },
                new int[]{0, pendingReview, 0, pendingFailed, 0});
        LinearLayout filters = new LinearLayout(this); filters.setGravity(Gravity.CENTER_VERTICAL);
        filters.setClipChildren(false); filters.setClipToPadding(false);
        filters.addView(filterControlHost(filterChips), new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout.LayoutParams category = new LinearLayout.LayoutParams(dp(48), dp(48));
        category.setMargins(dp(8), 0, 0, 0); filters.addView(inboxCategoryButton(), category);
        UiStyle.addSpaced(content, filters, 4, 8);
        buildSelectionBar();
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        renderInboxResults(store);
    }

    private void buildSelectionBar() {
        selectionBar = new LinearLayout(this);
        selectionBar.setOrientation(LinearLayout.HORIZONTAL);
        selectionBar.setGravity(Gravity.CENTER_VERTICAL);
        selectionCount = text("0/0", 12, false);
        selectionCount.setSingleLine(true);
        selectionBar.addView(selectionCount);
        selectAllButton = inboxSelectionAction("全选", this::toggleSelectAll);
        publishSelectedButton = inboxSelectionAction("写入日历", this::publishSelected);
        deleteSelectedButton = inboxSelectionAction("删除", this::confirmDeleteSelected);
        selectionBar.addView(selectAllButton);
        selectionBar.addView(publishSelectedButton);
        selectionBar.addView(deleteSelectedButton);
        selectionBar.setVisibility(View.GONE);
    }

    private ImageButton inboxCategoryButton() {
        ImageButton category = new ImageButton(this);
        category.setImageResource(R.drawable.ic_filter_alt);
        category.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        category.setPadding(dp(12), dp(12), dp(12), dp(12));
        category.setContentDescription("类型筛选：" + categoryOptions()[categoryIndex]);
        category.setImageTintList(ColorStateList.valueOf(categoryIndex == 0
                ? UiStyle.colors(this).muted : UiStyle.colors(this).primary));
        applyThemeControlSurface(category, categoryIndex > 0, UiStyle.RADIUS_CHIP);
        UiStyle.pressable(category);
        categoryChip = category;
        category.setOnClickListener(view -> {
            String[] choices = categoryOptions();
            UiDialogs.choiceDialog(this, "选择收件类型", choices, categoryIndex, selected -> {
                if (categoryIndex == selected) return;
                clearInboxSelection();
                categoryIndex = selected;
                inboxPage = 0;
                categoryChip.setContentDescription("类型筛选：" + choices[categoryIndex]);
                categoryChip.setImageTintList(ColorStateList.valueOf(categoryIndex == 0
                        ? UiStyle.colors(this).muted : UiStyle.colors(this).primary));
                applyThemeControlSurface(categoryChip, categoryIndex > 0,
                        UiStyle.RADIUS_PILL);
                updateResultsWithEntrance();
            });
        });
        return category;
    }

    private void renderInboxResults(TaskStore store) {
        results.removeAllViews();
        elapsedLabels.clear();
        inboxRows.clear();
        String selectedCategory = inboxCategory();
        InboxQuery query = new InboxQuery(statusIndex, selectedCategory, inboxQuery);
        String key = statusIndex + ":" + categoryIndex + ":" + inboxOldestFirst + ":" + query.keyword;
        long revision = preparedSection == null ? store.dataRevision() : preparedSection.revision;
        if (preparedSection != null) {
            inboxIdCache = preparedSection.ids; inboxIdCacheKey = key; inboxIdCacheRevision = revision;
            inboxPage = preparedSection.page;
        }
        if (!key.equals(inboxIdCacheKey) || revision != inboxIdCacheRevision) {
            inboxIdCache = store.queryInboxIds(query, inboxOldestFirst);
            inboxIdCacheKey = key;
            inboxIdCacheRevision = revision;
        }
        matchingInboxIds = inboxIdCache;
        int total = matchingInboxIds.size();
        inboxPage = Math.min(inboxPage, Math.max(0, (total - 1) / InboxQuery.PAGE_SIZE));
        List<TaskRecord> visible = preparedSection == null ? store.queryInboxPage(query, inboxOldestFirst, inboxPage, matchingInboxIds) : preparedSection.tasks;
        selectedInboxIds.retainAll(matchingInboxIds);
        addInboxResultsHeading(total);
        updateSelectionUi();
        if (visible.isEmpty()) {
            if (!query.keyword.isEmpty()) {
                empty(results, "没有匹配「" + inboxQuery.trim() + "」的收件。");
            } else {
                UiStyle.emptyState(results, R.drawable.ic_inbox_outline, "这里空空", "去记录", this::openCapture);
            }
        }
        for (int i = 0; i < visible.size(); i++)
            taskRow(results, visible.get(i), i);
        inboxPageControls(total);
    }

    private void inboxPageControls(int total) {
        inboxPageCount = Math.max(1, (total + InboxQuery.PAGE_SIZE - 1) / InboxQuery.PAGE_SIZE);
        if (inboxPagination == null) inboxPagination = new DateIntervalBar(this,
                () -> chooseListPage(false), this::changeInboxPage);
        bindListPagination(inboxPagination, false, inboxPage, inboxPageCount);
    }

    private void bindListPagination(DateIntervalBar bar, boolean schedule, int page, int pages) {
        String kind = schedule ? "日程" : "收件";
        bar.setTag(schedule ? "schedule_pagination" : "inbox_pagination");
        bar.setText((page + 1) + " / " + pages);
        bar.setContentDescription(kind + "页码，左右滑动切换，点击选择页码");
        // DateIntervalBar owns the common arrow geometry and feedback; only semantics differ.
        for (int i = 1; i < bar.getChildCount(); i++) {
            View arrow = bar.getChildAt(i);
            arrow.setContentDescription((i == 1 ? "上一页" : "下一页") + kind);
            arrow.setEnabled(i == 1 ? page > 0 : page + 1 < pages);
        }
        bar.setVisibility(pages > 1 ? View.VISIBLE : View.GONE);
        // Keep the rail outside the rebuilt rows: detaching it cancels its own text motion.
        // This is still the same scrolling footer, with the existing twelve-dp top gap.
        if (bar.getParent() != content) {
            if (bar.getParent() instanceof ViewGroup old) old.removeView(bar);
            UiStyle.addSpaced(content, bar, 12, 0);
        }
        if (!strips.contains(bar)) strips.add(bar);
        if (!previewRender && pager != null) pager.addGesturePriorityChild(bar);
    }

    private void chooseListPage(boolean schedule) {
        int pages = schedule ? schedulePageCount : inboxPageCount;
        int current = schedule ? schedulePage : inboxPage;
        String[] choices = new String[pages];
        for (int i = 0; i < pages; i++) choices[i] = "第 " + (i + 1) + " 页";
        UiDialogs.choiceDialog(this, "选择页码", choices, current, chosen -> {
            // Counts can change while the menu is open; the change methods clamp again.
            if (schedule) changeSchedulePage(chosen - schedulePage);
            else changeInboxPage(chosen - inboxPage);
        });
    }

    private boolean changeInboxPage(int delta) {
        int next = Math.max(0, Math.min(inboxPageCount - 1, inboxPage + delta));
        if (next == inboxPage) return false;
        inboxPage = next;
        updateResultsWithEntrance();
        scroll.scrollTo(0, Math.max(0, results.getTop() - dp(8)));
        return true;
    }

    private Button inboxSelectionAction(String label, Runnable action) {
        return BatchSelectionUi.action(this, label, action);
    }

    private void addInboxResultsHeading(int count) {
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("共 " + count + (section == SCHEDULE
                ? scheduleCategory == com.donglan.chrona.data.ScheduleFilterState.HOMEWORK_CATEGORY ? " 条作业" : " 条日程"
                : " 条收件"), 16, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int height = Math.max(dp(40), (int) Math.ceil(title.getPaint().getFontSpacing()) + dp(8));
        heading.addView(title, new LinearLayout.LayoutParams(0, height, 1f));
        BatchSelectionUi.attach(heading, selectionBar, height, selectAllButton, deleteSelectedButton);
        // This height exists before selection too, so entering selection never moves the cards.
        UiStyle.addSpaced(results, heading, 0, 4);
    }

    /** User-driven filter/order changes animate cards, while polling remains still. */
    private void updateResultsWithEntrance() {
        if (section == SCHEDULE) { requestScheduleResults(true); return; }
        if (results == null) return;
        LinearLayout target = results;
        dirtySections.add(section);
        int token = ++resultMotionGeneration;
        UiMotion.exitList(target, () -> {
            if (results != target || token != resultMotionGeneration || isDestroyed() || isFinishing()) return;
            updateResults(); UiMotion.enterList(target);
        });
    }

    private void schedule(TaskStore store) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setClipChildren(false); bar.setClipToPadding(false);
        String[] labels = {"待处理", "待安排", "历史", "全部"};
        filterChips = filterDock(labels, scheduleTab, tab -> {
            if (scheduleTab == tab && scheduleTodoScope == ScheduleQuery.TODO_ANY) return;
            scheduleTab = tab;
            scheduleTodoScope = ScheduleQuery.TODO_ANY;
            scheduleOldestFirst = ScheduleOrderPreferences.oldestFirst(this, scheduleTab);
            refreshOrderToggle();
            schedulePage = 0;
            updateChipSelection(tab);
            updateResultsWithEntrance();
        }, null);
        updateChipSelection(scheduleTab);
        bar.addView(filterControlHost(filterChips), new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton filters = new ImageButton(this);
        filters.setImageResource(R.drawable.ic_filter_alt);
        filters.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        filters.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        filters.setPadding(dp(10), dp(10), dp(10), dp(10));
        applyThemeControlSurface(filters, false, UiStyle.RADIUS_CHIP);
        filters.setContentDescription("筛选日程：日期、类型、待办、写入状态和来源");
        filters.setOnClickListener(view -> showScheduleFilters());
        UiStyle.pressable(filters);
        LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        filterParams.setMargins(dp(8), 0, 0, 0); bar.addView(filters, filterParams);
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setClipChildren(false); controls.setClipToPadding(false);
        ScheduleFilterSummaryLayout filterState = new ScheduleFilterSummaryLayout();
        filterState.setTag("schedule_filter_summary");
        controls.addView(filterState, new LinearLayout.LayoutParams(-1, -2));
        controls.addView(bar, new LinearLayout.LayoutParams(-1, -2));
        UiStyle.addSpaced(content, controls, 4, 8);
        scheduleControls = controls;
        buildSelectionBar();
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        renderScheduleResults(store);
    }

    private void renderScheduleResults(TaskStore store) {
        results.removeAllViews();
        if (!homeworkFocusFilter.equals(completionFilterKey())) {
            homeworkFocusTop = homeworkFocusBottom = 0; homeworkFocusFilter = "";
        }
        results.setPadding(0, homeworkFocusTop, 0, homeworkFocusBottom);
        addScheduleFilterSummary();
        if (scheduleSource == 1) {
            renderSystemSchedule(store);
            return;
        }
        long now = System.currentTimeMillis();
        ScheduleQuery query = buildScheduleQuery(now, ZoneId.systemDefault());
        inboxRows.clear();
        ScheduleListing.Page listing=preparedSection==null?ScheduleListing.load(this,store,query,scheduleOldestFirst,schedulePage,0):preparedSection.schedule;
        matchingInboxIds = listing.ids;
        selectedScheduleIds.retainAll(matchingInboxIds);
        int total = listing.total;
        schedulePage = Math.min(schedulePage, Math.max(0, (total - 1) / ScheduleQuery.PAGE_SIZE));
        schedulePage = listing.page;
        List<ScheduleListing.Row> visible = listing.rows;
        addInboxResultsHeading(total);
        updateSelectionUi();
        if(!listing.warning.isEmpty())message(results,listing.warning);
        if (visible.isEmpty()) {
            if (query.officialExamOnly() && !listing.examsLoaded)
                    UiStyle.emptyState(results, R.drawable.ic_schedule, "尚未获取考试安排", "刷新", this::requestScheduleResults);
                else UiStyle.emptyState(results, R.drawable.ic_schedule, "这里空空", "修改筛选", this::showScheduleFilters);
            if(query.officialExamOnly()){
                ZjuSettingsStore settings=new ZjuSettingsStore(this);
                boolean school=settings.enabled()&&settings.configured();
                Button exams=scheduleAction(school?"查看考试安排":"启用折姜星辰",()->startActivity(new Intent(this,school?ZjuExamsActivity.class:ZjuSettingsActivity.class)));
                UiStyle.addSpaced(results,exams,4,4);
            }
        }
        String previousDate = "";
        for (int i = 0; i < visible.size(); i++) {
            ScheduleListing.Row row=visible.get(i);
            EventCandidate item = row.candidate;
            boolean deadlineScope = scheduleTodoScope == ScheduleQuery.TODO_TODAY_DUE
                    || scheduleTodoScope == ScheduleQuery.TODO_OVERDUE
                    || scheduleCategory == com.donglan.chrona.data.ScheduleFilterState.HOMEWORK_CATEGORY;
            Long dateMillis = scheduleTodoScope == ScheduleQuery.TODO_OVERDUE ? item.overdueAtMillis()
                    : deadlineScope ? item.effectiveDueAtMillis()
                    : item.hasDefaultExecutionInterval() ? item.executionStartAtMillis() : item.anchor();
            String date = dateMillis == null ? "待安排" : item.executionAllDay() && !deadlineScope
                    ? AllDayDates.displayStart(dateMillis)
                    : AllDayDates.localDate(dateMillis, ZoneId.systemDefault());
            boolean newDay = !date.equals(previousDate);
            previousDate = date;
            final int index = i;
            enqueueScheduleResult(() -> {
                if (newDay) scheduleDayHeading(date);
                if(row.exam==null)candidateRow(results, item, index);
                else if(ScheduleListing.schoolCurrent(this,listing.account)){
                    LinearLayout card=card();
                    card.setTag(item.id);
                    addScheduleCardContent(card,item,null,true,false,null,row.exam);
                    completionRows.row(card,"exam:"+item.id,row.exam.time+":"+row.exam.room+":"+row.exam.seat);
                    card.setOnClickListener(v->startActivity(new Intent(this,ExternalCourseDetailActivity.class)
                            .putExtra("source","exam").putExtra("event_id",item.id)));
                    UiStyle.pressable(card);UiStyle.addSpaced(results,card,4,7);
                }
            });
        }
        enqueueScheduleResult(() -> { schedulePageControls(total); queueHomeworkFocus(); });
    }

    private LocalDate[] scheduleWindow(boolean system) {
        com.donglan.chrona.data.ScheduleFilterState state =
                new com.donglan.chrona.data.ScheduleFilterState(scheduleRange, scheduleCategory,
                        schedulePublication, system ? 1 : 0, scheduleTab, scheduleDate, scheduleUntil,
                        scheduleTodoScope);
        LocalDate[] window = state.window(LocalDate.now());
        if (system && window[0] != null && window[1].isAfter(window[0].plusYears(1)))
            window[1] = window[0].plusYears(1);
        return window;
    }

    private ScheduleQuery buildScheduleQuery(long now, ZoneId zone) {
        LocalDate[] window = scheduleWindow(false);
        return new ScheduleQuery(scheduleTab,
                com.donglan.chrona.data.ScheduleFilterState.categoryValue(scheduleCategory),
                schedulePublication, scheduleQuery, window[0], window[1], now, zone, scheduleTodoScope);
    }

    private void addScheduleFilterSummary() {
        if (scheduleControls == null) return;
        ScheduleFilterSummaryLayout host = scheduleControls.findViewWithTag("schedule_filter_summary");
        if (host == null) return;
        com.donglan.chrona.data.ScheduleFilterState state =
                new com.donglan.chrona.data.ScheduleFilterState(scheduleRange, scheduleCategory,
                        schedulePublication, scheduleSource, scheduleTab, scheduleDate, scheduleUntil,
                        scheduleTodoScope);
        String taskFilter = scheduleTodoScope == ScheduleQuery.TODO_TODAY_DUE ? "今日截止的待办"
                : scheduleTodoScope == ScheduleQuery.TODO_OVERDUE ? "已逾期的待办"
                : scheduleTodoScope == ScheduleQuery.TODO_OPEN ? (scheduleTab == 1 ? "未安排的待办" : "未完成的待办")
                : scheduleTab == 1 && scheduleCategory == EventCategory.indexOf(EventCategory.TASK) + 1
                        ? "未安排的待办" : "";
        boolean homeworkFilter = scheduleCategory == com.donglan.chrona.data.ScheduleFilterState.HOMEWORK_CATEGORY;
        if (homeworkFilter) state.category = 0;
        if (!taskFilter.isEmpty() && !homeworkFilter) {
            state.category = 0;
            state.todoScope = ScheduleQuery.TODO_ANY;
        }
        String summary = com.donglan.chrona.data.ScheduleFilterSummary.describe(state, scheduleQuery, LocalDate.now());
        if (homeworkFilter) summary = "作业" + (summary.isEmpty() ? "" : " · " + summary);
        if (!taskFilter.isEmpty() && !homeworkFilter) summary = taskFilter + (summary.isEmpty() ? "" : " · " + summary);
        if (summary.equals(host.summary)) return;
        if (summary.isEmpty()) {
            host.showSummary(summary, null);
            return;
        }
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(10), 0, dp(2), 0);
        row.setClipChildren(false); row.setClipToPadding(false);
        UiStyle.acrylicChoice(row, true, UiStyle.RADIUS_CHIP, true);
        LinearLayout open = new LinearLayout(this);
        open.setGravity(Gravity.CENTER_VERTICAL);
        open.setMinimumHeight(dp(48));
        open.setPadding(0, dp(7), dp(6), dp(7));
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_filter_alt);
        icon.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(20), dp(20));
        iconParams.rightMargin = dp(8);
        open.addView(icon, iconParams);
        TextView active = text(summary, 14, true);
        active.setTextColor(UiStyle.colors(this).onPrimaryContainer);
        active.setMaxLines(3);
        active.setEllipsize(android.text.TextUtils.TruncateAt.END);
        open.addView(active, new LinearLayout.LayoutParams(0, -2, 1));
        open.setContentDescription("当前筛选：" + summary + "，点击修改");
        open.setOnClickListener(view -> showScheduleFilters());
        UiStyle.pressable(open);
        row.addView(open, new LinearLayout.LayoutParams(0, -2, 1));
        View divider = new View(this);
        divider.setBackgroundColor(UiStyle.colors(this).outline);
        row.addView(divider, new LinearLayout.LayoutParams(dp(1), dp(18)));
        TextView reset = text("清除筛选", 13, true);
        reset.setTextColor(UiStyle.colors(this).primary);
        reset.setPadding(dp(10), 0, dp(10), 0);
        reset.setMinimumHeight(dp(48));
        reset.setGravity(Gravity.CENTER);
        reset.setContentDescription("清除日期、类型、状态、来源和搜索条件");
        reset.setOnClickListener(view -> {
            scheduleRange = scheduleCategory = schedulePublication = scheduleSource = 0;
            scheduleTodoScope = ScheduleQuery.TODO_ANY;
            scheduleDate = scheduleUntil = LocalDate.now();
            scheduleQuery = "";
            EditText search = content.findViewWithTag("schedule_search");
            if (search != null) search.setText("");
            applyScheduleFilters();
        });
        UiStyle.pressable(reset);
        row.addView(reset, new LinearLayout.LayoutParams(-2, -2));
        host.showSummary(summary, row);
    }

    /** Only this row changes height; the filter dock follows its normal layout position. */
    private final class ScheduleFilterSummaryLayout extends LinearLayout {
        private String summary;
        private boolean expanded;
        private android.animation.ValueAnimator expansion;

        ScheduleFilterSummaryLayout() {
            super(DashboardActivity.this);
            setOrientation(VERTICAL);
            setPadding(0, 0, 0, dp(8));
            setVisibility(GONE);
            setClipChildren(false);
            setClipToPadding(false);
        }

        void showSummary(String value, View row) {
            boolean first = summary == null;
            boolean showing = !value.isEmpty();
            boolean changingVisibility = expanded != showing || expansion != null;
            int initialHeight = getVisibility() == GONE ? 0 : getHeight();
            float initialAlpha = initialHeight == 0 ? 0f : getAlpha();
            summary = value;
            expanded = showing;
            stopExpansion();
            if (row != null) {
                removeAllViews();
                addView(row, new LinearLayout.LayoutParams(-1, -2));
            }
            ViewGroup parent = (ViewGroup) getParent();
            int width = parent.getWidth() - parent.getPaddingLeft() - parent.getPaddingRight();
            if (first || !changingVisibility || !UiMotion.isEnabled() || !isAttachedToWindow()
                    || !parent.isLaidOut() || !parent.isShown() || width <= 0 || previewRender || restoringMotion) {
                settleExpansion();
                return;
            }
            setVisibility(VISIBLE);
            View child = getChildAt(0);
            child.getLayoutParams().height = -2;
            measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            int fullHeight = getMeasuredHeight();
            int targetHeight = expanded ? fullHeight : 0;
            float targetAlpha = expanded ? 1f : 0f;
            // Keep the row's natural size while its host reveals or clips it.
            child.getLayoutParams().height = fullHeight - getPaddingBottom();
            getLayoutParams().height = initialHeight;
            setAlpha(initialAlpha);
            setClipChildren(true);
            android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofFloat(0f, 1f);
            expansion = animator;
            animator.setDuration(expanded ? UiMotion.ENTER : UiMotion.EXIT);
            animator.setInterpolator(UiMotion.SETTLE);
            animator.addUpdateListener(animation -> {
                if (!UiMotion.isEnabled()) {
                    stopExpansion();
                    settleExpansion();
                    return;
                }
                float fraction = (float) animation.getAnimatedValue();
                getLayoutParams().height = initialHeight + Math.round((targetHeight - initialHeight) * fraction);
                setAlpha(initialAlpha + (targetAlpha - initialAlpha) * fraction);
                requestLayout();
            });
            animator.addListener(new android.animation.AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(android.animation.Animator animation) {
                    if (expansion != animator) return;
                    expansion = null;
                    settleExpansion();
                }
            });
            animator.start();
        }

        private void stopExpansion() {
            android.animation.ValueAnimator previous = expansion;
            expansion = null;
            if (previous != null) previous.cancel();
        }

        private void settleExpansion() {
            setVisibility(expanded ? VISIBLE : GONE);
            getLayoutParams().height = -2;
            setAlpha(1f);
            setClipChildren(false);
            if (expanded && getChildCount() > 0) getChildAt(0).getLayoutParams().height = -2;
            else if (!expanded) removeAllViews();
            requestLayout();
        }

        @Override protected void onDetachedFromWindow() {
            stopExpansion();
            settleExpansion();
            super.onDetachedFromWindow();
        }
    }

    private void scheduleDayHeading(String date) {
        String label = date;
        if (!"待安排".equals(date)) {
            LocalDate day = LocalDate.parse(date);
            LocalDate today = LocalDate.now();
            label = (day.equals(today) || day.equals(today.plusDays(1))
                    ? timelineDayTitle(day, today) + " · " : "")
                    + day.format(java.time.format.DateTimeFormatter.ofPattern("M月d日", Locale.CHINA));
        }
        TextView heading = text(label, 15, true);
        heading.setTextColor(UiStyle.colors(this).primary);
        UiStyle.addSpaced(results, heading, results.getChildCount() == 1 ? 4 : 12, 5);
    }

    private void schedulePageControls(int total) {
        schedulePageCount = Math.max(1, (total + ScheduleQuery.PAGE_SIZE - 1) / ScheduleQuery.PAGE_SIZE);
        if (schedulePagination == null) schedulePagination = new DateIntervalBar(this,
                () -> chooseListPage(true), this::changeSchedulePage);
        bindListPagination(schedulePagination, true, schedulePage, schedulePageCount);
    }

    private boolean changeSchedulePage(int delta) {
        int next = Math.max(0, Math.min(schedulePageCount - 1, schedulePage + delta));
        if (next == schedulePage) return false;
        schedulePage = next;
        updateResultsWithEntrance();
        scroll.scrollTo(0, Math.max(0, scheduleControls.getTop() - dp(8)));
        return true;
    }

    private Button scheduleAction(String title, Runnable action) {
        Button control = inboxSelectionAction(title, action);
        control.setMinimumHeight(dp(40));
        control.setPadding(dp(10), 0, dp(10), 0);
        return control;
    }

    private void applyScheduleFilters() {
        scheduleOldestFirst = ScheduleOrderPreferences.oldestFirst(this, scheduleTab);
        refreshOrderToggle();
        schedulePage = 0;
        selectedScheduleIds.clear();
        lastCalendarFetch = 0;
        calendarGeneration++;
        updateChipSelection(scheduleTab);
        updateResultsWithEntrance();
        scroll.scrollTo(0, 0);
        if (scheduleSource == 1) scroll.post(this::refreshSystemCalendar);
    }

    private void showScheduleFilters() {
        com.donglan.chrona.data.ScheduleFilterState initial =
                new com.donglan.chrona.data.ScheduleFilterState(scheduleRange, scheduleCategory,
                        schedulePublication, scheduleSource, scheduleTab, scheduleDate, scheduleUntil,
                        scheduleTodoScope);
        ScheduleFilterSheet.show(this, initial, this::pickScheduleDate, chosen -> {
            scheduleRange = chosen.range;
            scheduleCategory = chosen.category;
            schedulePublication = chosen.publication;
            scheduleSource = chosen.source;
            scheduleTab = chosen.tab;
            scheduleTodoScope = chosen.todoScope;
            scheduleDate = chosen.date;
            scheduleUntil = chosen.until;
            applyScheduleFilters();
        });
    }

    private void pickScheduleDate(String title, LocalDate initial, java.util.function.Consumer<LocalDate> chosen) {
        // The shared date dialog owns the panel, the picker height and the action row, so this page
        // keeps only the posted callback its schedule reload depends on.
        UiDialogs.dateDialog(this, title, initial, date -> refresh.post(() -> chosen.accept(date)));
    }

    private void renderSystemSchedule(TaskStore store) {
        inboxRows.clear(); matchingInboxIds = new ArrayList<>(); selectedScheduleIds.clear();
        LocalDate[] window = scheduleWindow(true);
        ZoneId zone = ZoneId.systemDefault();
        long start = window[0].atStartOfDay(zone).toInstant().toEpochMilli();
        long end = window[1].atStartOfDay(zone).toInstant().toEpochMilli();
        long now = System.currentTimeMillis();
        List<CalendarOccurrence> visible = new ArrayList<>();
        Set<Long> linked = new HashSet<>();
        for (long id : preparedSection == null ? store.linkedCalendarIds() : preparedSection.linked) linked.add(id);
        for (CalendarOccurrence event : CalendarOccurrence.unlinked(calendarEventsForWindow(window), linked)) {
            boolean future = event.displayEnd(zone) > now;
            if (event.displayStart(zone) >= end || event.displayEnd(zone) <= start
                    || scheduleTab == 1 || (scheduleTab == 0 && !future) || (scheduleTab == 2 && future)
                    || schedulePublication == 1 || schedulePublication == 3 || (scheduleCategory != 0 && scheduleCategory != 1)) continue;
            String keyword = normalizedQuery(scheduleQuery);
            if (!keyword.isEmpty() && !containsQuery(event.title, keyword)
                    && !containsQuery(event.location, keyword)) continue;
            visible.add(event);
        }
        visible.sort(java.util.Comparator.comparingLong((CalendarOccurrence event) -> event.displayStart(zone))
                .thenComparingLong(event -> event.eventId));
        if (!scheduleOldestFirst) java.util.Collections.reverse(visible);
        int total = visible.size();
        schedulePage = Math.min(schedulePage, Math.max(0, (total - 1) / ScheduleQuery.PAGE_SIZE));
        addInboxResultsHeading(total);
        updateSelectionUi();
        if (total == 0) UiStyle.emptyState(results, R.drawable.ic_schedule, "这里空空", "修改筛选", this::showScheduleFilters);
        String day = "";
        for (int i = schedulePage * ScheduleQuery.PAGE_SIZE;
                i < Math.min(total, (schedulePage + 1) * ScheduleQuery.PAGE_SIZE); i++) {
            CalendarOccurrence event = visible.get(i);
            String date = AllDayDates.localDate(event.displayStart(zone), zone);
            boolean newDay = !day.equals(date); day = date;
            enqueueScheduleResult(() -> {
                if (newDay) scheduleDayHeading(date);
                LinearLayout item = card();
                addScheduleCardContent(item, null, event, true, false, null);
                item.setOnClickListener(view -> openHomeEntry(new HomeTimelineEntry(event)));
                UiStyle.pressable(item);
                UiStyle.addSpaced(results, item, 4, 4);
            });
        }
        enqueueScheduleResult(() -> {
            message(results, "其他来源日程只读；按月份或日期缩小范围。");
            schedulePageControls(total);
        });
    }

    private void cancelScheduleResults() {
        scheduleResultGeneration++;
        if (scheduleResultLoading) dirtySections.add(SCHEDULE);
        scheduleResultLoading = false;
        if (scheduleBuildTarget != null) scheduleBuildTarget.removeCallbacks(scheduleBuildFrame);
        if (scheduleBuildTarget != null) UiMotion.resetList(scheduleBuildTarget);
        scheduleBuildTarget = null;
        scheduleBuildSteps = null;
        scheduleBuildDone = null;
    }

    /** Query on the existing worker; keep the dock and current rows alive until data arrives. */
    private void requestScheduleResults() { requestScheduleResults(false); }
    private void requestScheduleResults(boolean animate) {
        if (results == null || section != SCHEDULE) return;
        addScheduleFilterSummary();
        cancelScheduleResults();
        final int token = scheduleResultGeneration;
        final LinearLayout target = results;
        final ScrollView targetScroll = scroll;
        ScheduleQuery query = buildScheduleQuery(System.currentTimeMillis(), ZoneId.systemDefault());
        InboxQuery inbox = new InboxQuery(statusIndex,
                inboxCategory(), inboxQuery);
        boolean oldest = scheduleOldestFirst, system = scheduleSource == 1;
        int requestedPage = schedulePage;
        long focusCandidate = pendingHomeworkFocus;
        scheduleResultLoading = true;
        dirtySections.add(SCHEDULE);
        startupReader.execute(() -> {
            DashboardSectionData data;
            try {
                data = DashboardSectionData.load(getApplicationContext(), SCHEDULE,
                        inbox, query, oldest, requestedPage, system, focusCandidate);
            } catch (RuntimeException failure) {
                refresh.post(() -> {
                    if (!scheduleResultCurrent(token, target)) return;
                    cancelScheduleResults();
                    Feedback.show(this, "暂时无法读取日程，请重试");
                });
                return;
            }
            refresh.post(() -> {
                if (!scheduleResultCurrent(token, target)) return;
                if(data.schedule!=null&&!data.schedule.scope.equals(ScheduleListing.scope(this))){requestScheduleResults(animate);return;}
                scheduleBuildTarget = target;
                Runnable build = () -> {
                if (!scheduleResultCurrent(token, target)) return;
                updatePendingCounts(data.review, data.failed);
                final int previousY = targetScroll.getScrollY();
                resultMotionGeneration++;
                scheduleBuildTarget = target;
                scheduleBuildSteps = new java.util.ArrayDeque<>();
                scheduleBuildDone = () -> {
                    scheduleResultLoading = false;
                    awaitingCompletionRows.clear();
                    UiMotion.settleScroll(targetScroll, target);
                    sectionPages.put(SCHEDULE, new SectionPage());
                    sectionVersions.put(SCHEDULE, snapshotForRevision(data.revision));
                    dirtySections.remove(SCHEDULE);
                    targetScroll.post(() -> {
                        if (scheduleResultCurrent(token, target)) targetScroll.scrollTo(0, previousY);
                    });
                };
                DashboardSectionData previous = preparedSection;
                preparedSection = data;
                try { renderScheduleResults(null); }
                catch (RuntimeException failure) {
                    cancelScheduleResults();
                    Feedback.show(this, "暂时无法显示日程，请重试");
                    return;
                } finally { preparedSection = previous; }
                // One row now, then at most one row per display frame; avoid a large layout burst.
                buildScheduleResultFrame();
                if (animate) UiMotion.enterList(target);
                };
                if (animate) UiMotion.exitList(target, build); else build.run();
            });
        });
    }

    private boolean scheduleResultCurrent(int token, LinearLayout target) {
        return token == scheduleResultGeneration && results == target && section == SCHEDULE
                && !isDestroyed() && !isFinishing();
    }

    private void enqueueScheduleResult(Runnable step) {
        if (scheduleBuildSteps == null) step.run(); else scheduleBuildSteps.add(step);
    }

    private void buildScheduleResultFrame() {
        if (scheduleBuildSteps == null) return;
        if (section != SCHEDULE || results != scheduleBuildTarget) { cancelScheduleResults(); return; }
        try {
            if (!scheduleBuildSteps.isEmpty()) scheduleBuildSteps.removeFirst().run();
        } catch (RuntimeException failure) {
            cancelScheduleResults();
            Feedback.show(this, "暂时无法显示日程，请重试");
            return;
        }
        if (!scheduleBuildSteps.isEmpty()) {
            scheduleBuildTarget.postOnAnimation(scheduleBuildFrame);
            return;
        }
        scheduleBuildSteps = null;
        scheduleBuildTarget = null;
        Runnable done = scheduleBuildDone;
        scheduleBuildDone = null;
        if (done != null) done.run();
    }

    private void updateResults() {
        if (results == null) return;
        UiMotion.resetList(results);
        if (section == SCHEDULE) { requestScheduleResults(); return; }
        resultMotionGeneration++;
        int previous = scroll.getScrollY();
        try (TaskStore store = new TaskStore(this)) {
            updatePendingCounts(store.taskCountByStatus(TaskRecord.NEEDS_REVIEW),
                    store.taskCountByStatus(TaskRecord.FAILED));
            if (section == INBOX) renderInboxResults(store);
            else if (section == SCHEDULE) renderScheduleResults(store);
        } catch (Exception exception) {
            results.removeAllViews();
            message(results, "暂时无法读取日程：" + exception.getMessage());
        }
        scroll.scrollTo(0, previous);
        UiMotion.settleScroll(scroll, results);
        rememberRenderedPage();
    }

    private void updatePendingCounts(int review, int failed) {
        pendingReview = review;
        pendingFailed = failed;
        SectionPage cachedInbox = sectionPages.get(INBOX);
        DockNavigationLayout inboxChoices = section == INBOX ? filterChips
                : cachedInbox == null ? null : cachedInbox.filterChips;
        if (inboxChoices != null) {
            updateFilterCount(inboxChoices, 1, review, "待确认");
            updateFilterCount(inboxChoices, 3, failed, "失败");
        }
        if (navigation != null) drawNavigation();
    }

    private void updateFilterCount(DockNavigationLayout dock, int index, int count, String label) {
        TextView badge = dock.itemAt(index).findViewWithTag("filter_count_badge");
        badge.setText(count > 99 ? "99+" : Integer.toString(count));
        badge.setContentDescription(label + " " + count + " 条收件");
        badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
    }

    private void updateChipSelection(int selected) {
        if (filterChips == null) return;
        filterChips.finishPageSelection(selected);
        for (int i = 0; i < filterChips.itemCount(); i++) {
            View cell = filterChips.itemAt(i);
            // A counted chip is wrapped so its badge can sit on the corner; the pill is inside.
            TextView chip = (TextView) (cell instanceof FrameLayout
                    ? ((FrameLayout) cell).getChildAt(0) : cell);
            cell.setSelected(i == selected);
            cell.setContentDescription(chip.getText() + (i == selected ? "，已选中" : ""));
        }
        updateCompactFilterSelection(filterChips, selected);
    }

    private void candidateRow(LinearLayout parent, EventCandidate item, int index) {
        LinearLayout card = card();
        card.setTag(Long.valueOf(item.id));
        completionRows.row(card, "schedule:" + item.id, completionState(item, null));
        boolean selectable = section == SCHEDULE && parent == results;
        TextView marker = text("○", 22, false);
        marker.setTag("schedule-selection-marker");
        marker.setGravity(Gravity.CENTER);
        addScheduleCardContent(card, item, null, true, false, selectable ? marker : null);
        if (selectable) {
            inboxRows.put(item.id, new InboxRow(card, marker));
            card.setOnLongClickListener(view -> {
                selectedScheduleIds.add(item.id);
                updateSelectionUi();
                return true;
            });
            card.setOnClickListener(view -> {
                if (!selectedScheduleIds.isEmpty()) {
                    if (!selectedScheduleIds.remove(item.id)) selectedScheduleIds.add(item.id);
                    updateSelectionUi();
                } else openCandidate(item);
            });
            applyInboxRowSelection(item.id);
        } else card.setOnClickListener(view -> openCandidate(item));
        UiStyle.pressable(card);
        UiStyle.addSpaced(parent, card, 4, 4);
    }

    /** Shared hierarchy for the timeline, upcoming strip and schedule results. */
    private void addScheduleCardContent(LinearLayout card, EventCandidate candidate,
            CalendarOccurrence systemEvent, boolean showTime, boolean home, TextView selection) {
        addScheduleCardContent(card,candidate,systemEvent,showTime,home,selection,null);
    }
    private void addScheduleCardContent(LinearLayout card, EventCandidate candidate,
            CalendarOccurrence systemEvent, boolean showTime, boolean home, TextView selection,
            com.donglan.chrona.zju.ZjuExam exam) {
        String category = candidate == null ? EventCategory.EVENT : candidate.category;
        String titleText = candidate == null ? systemEvent.title : candidate.title;
        String location = candidate == null ? systemEvent.location : candidate.location;
        String source = exam!=null?"折姜星辰 · 教务":candidate == null ? systemEvent.calendarName : "拾时";
        UiStyle.cardInset(card);
        LinearLayout body = card;
        FrameLayout interval = null;
        if (home) {
            // The rail spans the whole themed surface, independently of the content height.
            card.setPadding(0, 0, 0, 0);
            card.setClipToOutline(true);
            android.graphics.Paint railPaint = new android.graphics.Paint();
            railPaint.setColor(UiStyle.colors(this).outline);
            interval = new FrameLayout(this) {
                @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
                    // Drawing the rail avoids a MATCH_PARENT child stretching a wrap-content card.
                    canvas.drawRect(dp(HOME_CARD_RAIL_CENTER_DP - 1), 0,
                            dp(HOME_CARD_RAIL_CENTER_DP + 1), getHeight(), railPaint);
                    super.dispatchDraw(canvas);
                }
            };
            body = new LinearLayout(this);
            body.setOrientation(LinearLayout.VERTICAL);
            body.setPadding(dp(48), dp(12), dp(16), dp(12));
            interval.addView(body, new FrameLayout.LayoutParams(-1, -2));
            card.addView(interval, new LinearLayout.LayoutParams(-1, -2));
        }

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(titleText, home ? 16 : 17, true);
        title.setIncludeFontPadding(false);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (!home) {
            LinearLayout type = scheduleAttribute(categoryIcon(category),
                    exam!=null?"考试":EventCategory.label(category), "类型", 12, 1, true);
            LinearLayout.LayoutParams typeParams = new LinearLayout.LayoutParams(-2, -2);
            typeParams.leftMargin = dp(8);
            heading.addView(type, typeParams);
        }
        if (selection != null) heading.addView(selection,
                new LinearLayout.LayoutParams(dp(34), dp(34)));
        body.addView(heading);

        if (home) {
            addHomeTimeNode(body, interval, homeCardBoundary(candidate, systemEvent, false, showTime),
                    candidate != null && HomeTimelineEntry.deadlineOnly(candidate) ? "作业截止时间" : "起始时间");
        } else if (showTime) {
            String when = exam!=null?exam.kind+" · "+exam.displayTime():candidate == null ? homeEntryWhen(new HomeTimelineEntry(systemEvent))
                    : candidate.anchor() == null ? "尚未安排时间" : formatWhen(candidate);
            UiStyle.addSpaced(body, scheduleAttribute(candidate != null && candidate.isHomework() ? R.drawable.ic_homework : R.drawable.ic_schedule, when,
                    "时间", 14, 2, false), 6, 0);
        }
        if (location != null && !location.trim().isEmpty()) {
            UiStyle.addSpaced(body, scheduleAttribute(R.drawable.ic_place, location.trim(),
                    "地点", 14, 2, false), home ? 5 : 6, 0);
        }
        if (home && (candidate == null || !candidate.point())) {
            addHomeTimeNode(body, interval, homeCardBoundary(candidate, systemEvent, true, showTime),
                    "终止时间");
        }
        if (candidate != null && candidate.isTask() && candidate.executionStartAtMillis() != null
                && candidate.effectiveDueAtMillis() != null) {
            String deadline = (candidate.isHomework() ? "作业截止 · " : "截止 · ")
                    + dateTime(candidate.effectiveDueAtMillis());
            UiStyle.addSpaced(body, scheduleAttribute(candidate.isHomework() ? R.drawable.ic_homework : R.drawable.ic_schedule, deadline,
                    "截止时间", 14, 2, false), home ? 5 : 8, 0);
        }

        LinearLayout footer = new LinearLayout(this);
        footer.setGravity(Gravity.CENTER_VERTICAL);
        if (home) {
            footer.addView(scheduleAttribute(categoryIcon(category), EventCategory.label(category),
                    "类型", 13, 1, true), new LinearLayout.LayoutParams(-2, -2));
        }
        if (source != null && !source.trim().isEmpty()) {
            LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(0, -2, 1);
            sourceParams.leftMargin = home ? dp(10) : 0;
            footer.addView(scheduleAttribute(candidate == null ? R.drawable.ic_calendar_source
                    : R.drawable.ic_inbox_outline, source.trim(), "来源", home ? 13 : 12, 1, false),
                    sourceParams);
        } else {
            footer.addView(new View(this), new LinearLayout.LayoutParams(0, 0, 1));
        }
        // A published item is settled even if its original AI uncertainty flag remains set.
        boolean written = exam!=null || candidate == null || candidate.calendarEventId != null;
        boolean confirmation = candidate != null && !written && candidate.needsConfirmation;
        boolean calendarTime = candidate != null && candidate.calendarStart() != null
                && (candidate.isTask() || candidate.endAtMillis != null);
        if (candidate != null && !written && (confirmation || (calendarTime && !candidate.completed()))) {
            String status = confirmation ? "待确认 · " + UiStyle.uncertaintyLabel(candidate.uncertaintyLevel)
                    : "未写入";
            LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-2, -2);
            statusParams.leftMargin = dp(8);
            footer.addView(scheduleAttribute(R.drawable.ic_x, status,
                    confirmation ? "解析待确认" : "尚未写入系统日历", 12, 1, true), statusParams);
        }
        if(candidate!=null&&candidate.isTask()) {
            footer.addView(taskCompletionButton(candidate), new LinearLayout.LayoutParams(dp(48), dp(48)));
        } else if (exam==null && (candidate == null || !candidate.isRecord && !candidate.needsConfirmation)
                && "event".equals(category)) {
            Long anchor = candidate == null ? systemEvent.displayStart(ZoneId.systemDefault()) : candidate.reminderAnchor();
            Long finish = candidate == null ? systemEvent.displayEnd(ZoneId.systemDefault()) : candidate.endAtMillis;
            if (anchor != null) {
                long until = finish != null && finish > anchor ? finish : anchor + 60000L;
                if (until > System.currentTimeMillis()) {
                    String key = candidate == null ? AgendaAcknowledgement.key(systemEvent.eventId, anchor)
                            : AgendaAcknowledgement.key(candidate);
                    footer.addView(activityAcknowledgementButton(key, until), new LinearLayout.LayoutParams(dp(48), dp(48)));
                }
            }
        }
        UiStyle.addSpaced(body, footer, home ? 6 : 8, 0);
    }

    private ImageButton taskCompletionButton(EventCandidate candidate) {
        ImageButton done = new ImageButton(this);
        long now = System.currentTimeMillis();
        boolean overdue = candidate.overdue(now);
        done.setImageResource(candidate.completed() ? R.drawable.ic_todo_check
                : overdue ? R.drawable.ic_todo_alert : R.drawable.ic_todo_clock);
        done.setImageTintList(ColorStateList.valueOf(candidate.completed() || overdue
                ? UiStyle.colors(this).primary : UiStyle.colors(this).muted));
        done.setContentDescription(candidate.taskState(now) + "，"
                + (candidate.completed() ? "恢复" + (candidate.isHomework() ? "作业" : "待办")
                : "标记" + (candidate.isHomework() ? "作业" : "待办") + "完成"));
        done.setPadding(dp(12), dp(12), dp(12), dp(12));
        UiStyle.acrylicChoice(done, candidate.completed(), UiStyle.RADIUS_PILL, false);
        UiStyle.pressable(done);
        done.setOnClickListener(view -> {
            if (awaitingCompletionRows.contains(candidate.id)) return;
            if (!completingCandidates.add(candidate.id)) return;
            done.setEnabled(false);
            new Thread(() -> {
                try {
                    String warning = TaskCompletion.set(getApplicationContext(), candidate, !candidate.completed());
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        awaitingCompletionRows.add(candidate.id);
                        completingCandidates.remove(candidate.id);
                        refreshCompletionRows();
                        if (warning != null) Feedback.show(this, warning);
                    });
                } catch (Exception error) {
                    runOnUiThread(() -> {
                        if (isDestroyed() || isFinishing()) return;
                        completingCandidates.remove(candidate.id);
                        done.setEnabled(true);
                        if (completionRefreshPending) refreshCompletionRows();
                        Feedback.show(this, error.getMessage());
                    });
                }
            }, "chrona-task-complete").start();
        });
        return done;
    }

    private ImageButton activityAcknowledgementButton(String key, long until) {
        ImageButton confirmed = new ImageButton(this);
        confirmed.setPadding(dp(12), dp(12), dp(12), dp(12));
        Runnable update = () -> {
            boolean checked = AgendaAcknowledgement.isAcknowledged(this, key);
            confirmed.setImageResource(checked ? R.drawable.ic_todo_check : R.drawable.ic_check);
            confirmed.setImageTintList(ColorStateList.valueOf(checked
                    ? UiStyle.colors(this).primary : UiStyle.colors(this).muted));
            confirmed.setContentDescription(checked ? "活动已确认，点击取消确认并恢复原子岛提醒" : "确认活动并隐藏本次原子岛提醒");
            confirmed.setSelected(checked);
            if (android.os.Build.VERSION.SDK_INT >= 30) confirmed.setStateDescription(checked ? "已确认" : "未确认");
            UiStyle.acrylicChoice(confirmed, checked, UiStyle.RADIUS_PILL, false);
        };
        update.run();
        UiStyle.pressable(confirmed);
        confirmed.setOnClickListener(view -> {
            AgendaAcknowledgement.set(this, key, until, !AgendaAcknowledgement.isAcknowledged(this, key));
            update.run();
            refreshCompletionRows();
        });
        return confirmed;
    }

    private String completionKey(EventCandidate candidate, CalendarOccurrence event) {
        return candidate != null ? "candidate:" + candidate.occurrenceKey()
                : "system:" + event.eventId + ":" + event.startMillis;
    }

    private String completionState(EventCandidate candidate, CalendarOccurrence event) {
        if (candidate == null) return event.title + ":" + event.startMillis + ":" + event.endMillis
                + ":" + event.location + ":" + event.calendarName + ":"
                + AgendaAcknowledgement.isAcknowledged(this,
                        AgendaAcknowledgement.key(event.eventId, event.displayStart(ZoneId.systemDefault())));
        return candidate.title + ":" + candidate.startAtMillis + ":" + candidate.endAtMillis
                + ":" + candidate.dueAtMillis + ":" + candidate.completedAtMillis + ":"
                + candidate.location + ":" + candidate.category + ":" + candidate.calendarEventId
                + ":" + candidate.needsConfirmation + ":" + candidate.uncertaintyLevel + ":"
                + candidate.taskState(System.currentTimeMillis()) + ":"
                + AgendaAcknowledgement.isAcknowledged(this, AgendaAcknowledgement.key(candidate));
    }

    /** Only successful writes request a new local snapshot. Other sections stay dirty until visited. */
    private void refreshCompletionRows() {
        dirtySections.add(HOME); dirtySections.add(SCHEDULE);
        completionRefreshPending = true;
        if (completionRefreshRunning || !completingCandidates.isEmpty()) return;
        if (section != HOME && section != SCHEDULE) {
            completionRefreshPending = false; return;
        }
        if (pageTransitionRunning || dragTargetLoaded || sectionLoadRunning || scheduleResultLoading
                || (navigation != null && (navigation.isAnimating() || navigation.isTrackingTouch()))) {
            refresh.removeCallbacks(deferredSectionRefresh);
            refresh.postDelayed(deferredSectionRefresh, 80L); return;
        }
        completionRefreshPending = false;
        completionRefreshRunning = true;
        final int destination = section;
        final LinearLayout target = destination == HOME ? content : results;
        final ScrollView viewport = scroll;
        final ScheduleQuery query = buildScheduleQuery(System.currentTimeMillis(), ZoneId.systemDefault());
        final InboxQuery inbox = new InboxQuery(statusIndex, inboxCategory(), inboxQuery);
        final boolean oldest = scheduleOldestFirst, system = scheduleSource == 1;
        final int page = schedulePage;
        final String filterKey = completionFilterKey();
        startupReader.execute(() -> {
            DashboardSectionData data;
            try {
                data = DashboardSectionData.load(getApplicationContext(), destination, inbox,
                        query, oldest, page, system, 0);
            } catch (RuntimeException failure) {
                refresh.post(this::finishCompletionReadFailure);
                return;
            }
            refresh.post(() -> {
                if (isDestroyed() || isFinishing()) return;
                boolean filtersChanged = destination == SCHEDULE && (!filterKey.equals(completionFilterKey())
                        || data.schedule!=null&&!data.schedule.scope.equals(ScheduleListing.scope(this)));
                if (section != destination || viewport != scroll || !completingCandidates.isEmpty()
                        || pageTransitionRunning || dragTargetLoaded || scheduleResultLoading || filtersChanged) {
                    completionRefreshRunning = false;
                    refreshCompletionRows(); return;
                }
                applyCompletionRows(data, target, viewport, destination);
            });
        });
    }

    private void finishCompletionReadFailure() {
        completionRefreshRunning = false;
        completionRefreshPending = false;
        // Keep dirtySections: the next ordinary poll/resume retries, without a tight loop.
        if (!isDestroyed()) Feedback.show(this, "列表暂时无法刷新，请稍后重试");
    }

    private void applyCompletionRows(DashboardSectionData data, LinearLayout target,
            ScrollView viewport, int destination) {
        int y = viewport.getScrollY();
        int x = destination == HOME && !strips.isEmpty() ? strips.get(0).getScrollX() : 0;
        LinearLayout incoming = new LinearLayout(this);
        incoming.setOrientation(LinearLayout.VERTICAL);
        DashboardSectionData oldPrepared = preparedSection;
        LinearLayout oldContent = content, oldResults = results;
        Map<Long, InboxRow> oldRows = inboxRows;
        List<View> oldStrips = new ArrayList<>(strips);
        preparedSection = data;
        try {
            if (destination == HOME) {
                content = incoming; strips.clear(); home(data.home);
            } else {
                results = incoming; inboxRows = new LinkedHashMap<>(); renderScheduleResults(null);
            }
        } finally {
            preparedSection = oldPrepared;
            content = oldContent; results = oldResults;
            inboxRows = oldRows;
            strips.clear(); strips.addAll(oldStrips);
        }
        // Only retained rows move: a container-wide fade made every card blink whenever a poll or
        // the calendar toggle changed one row. Bounds animation keeps insertions and the resulting
        // displacement continuous without washing out the page.
        if (UiMotion.isEnabled() && target.isLaidOut()) {
            android.transition.TransitionSet motion = new android.transition.TransitionSet();
            motion.setOrdering(android.transition.TransitionSet.ORDERING_TOGETHER);
            motion.addTransition(new android.transition.ChangeBounds());
            motion.setDuration(UiMotion.ENTER);
            motion.setInterpolator(UiMotion.SETTLE);
            android.transition.TransitionManager.beginDelayedTransition(target, motion);
        }
        completionRows.apply(target, incoming, destination == HOME ? headerChildCount : 0);
        if (destination == HOME) {
            strips.clear();
            collectHomeStrips(content);
            if (!strips.isEmpty()) strips.get(0).scrollTo(x, 0);
            if (pager != null) pager.setGesturePriorityChildren(strips);
            homeRefreshPending = false;
        } else {
            inboxRows.clear();
            for (EventCandidate candidate : data.candidates) {
                for (int i = 0; i < target.getChildCount(); i++) {
                    View child = target.getChildAt(i);
                    if (child instanceof LinearLayout card
                            && Long.valueOf(candidate.id).equals(card.getTag())) {
                        TextView marker = card.findViewWithTag("schedule-selection-marker");
                        inboxRows.put(candidate.id, new InboxRow(card, marker)); break;
                    }
                }
            }
            updateSelectionUi(); scheduleRefreshPending = false;
        }
        updatePendingCounts(data.review, data.failed);
        UiMotion.settleScroll(viewport, target);
        sectionPages.put(destination, new SectionPage());
        snapshot = snapshotForRevision(data.revision);
        sectionVersions.put(destination, snapshot);
        dirtySections.remove(destination);
        awaitingCompletionRows.clear();
        viewport.scrollTo(0, y);
        int motion = ++completionMotionGeneration;
        refresh.postDelayed(() -> {
            if (motion != completionMotionGeneration || isDestroyed()) return;
            completionRefreshRunning = false;
            if (completionRefreshPending) refreshCompletionRows();
        }, UiMotion.isEnabled() ? UiMotion.ENTER + 32L : 0L);
    }

    private void collectHomeStrips(View view) {
        if (view instanceof HorizontalScrollView) strips.add(view);
        else if (view instanceof ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) collectHomeStrips(group.getChildAt(i));
    }

    private String completionFilterKey() {
        return scheduleTab + ":" + scheduleCategory + ":" + schedulePublication + ":" + scheduleSource
                + ":" + scheduleRange + ":" + scheduleDate + ":" + scheduleUntil + ":" + scheduleTodoScope
                + ":" + scheduleQuery + ":" + scheduleOldestFirst + ":" + schedulePage;
    }

    private String homeCardBoundary(EventCandidate candidate, CalendarOccurrence systemEvent,
            boolean end, boolean includeDate) {
        if (candidate != null && HomeTimelineEntry.deadlineOnly(candidate)) return formatWhen(candidate);
        Long startMillis = candidate == null ? systemEvent.startMillis : candidate.executionStartAtMillis();
        Long endMillis = candidate == null ? systemEvent.endMillis : HomeTimelineEntry.executionEnd(candidate);
        Long millis = end ? endMillis : startMillis;
        if (millis == null) return end ? "终止时间未定" : "起始时间未定";
        boolean allDay = candidate == null ? systemEvent.allDay : candidate.executionAllDay();
        if (allDay) {
            // CalendarContract all-day ends are exclusive UTC dates, not local clock times.
            String date = end ? AllDayDates.displayEnd(millis) : AllDayDates.displayStart(millis);
            return date + " · 全天";
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate boundaryDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
        LocalDate startDate = startMillis == null ? null
                : Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate();
        boolean crossesDay = end && startDate != null && !startDate.equals(boundaryDate);
        boolean startedEarlier = startDate != null && startDate.isBefore(today);
        boolean showYear = boundaryDate.getYear() != today.getYear()
                || (startDate != null && endMillis != null && startDate.getYear()
                    != Instant.ofEpochMilli(endMillis).atZone(zone).getYear());
        String pattern = showYear ? "yyyy年M月d日 HH:mm"
                : includeDate || crossesDay || startedEarlier ? "M月d日 HH:mm" : "HH:mm";
        return new SimpleDateFormat(pattern, Locale.CHINA).format(new Date(millis));
    }

    private void addHomeTimeNode(LinearLayout body, FrameLayout interval, String value, String label) {
        LinearLayout timeRow = scheduleAttribute("作业截止时间".equals(label) ? R.drawable.ic_homework
                : "起始时间".equals(label) ? R.drawable.ic_time_start : R.drawable.ic_time_end, value, label, 14, 2, false);
        TextView caption = (TextView) timeRow.getChildAt(1);
        UiStyle.addSpaced(body, timeRow, 5, 0);
        View dot = new View(this);
        dot.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(UiStyle.colors(this).primary);
        dot.setBackground(circle);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                dp(HOME_CARD_TIME_DOT_DP), dp(HOME_CARD_TIME_DOT_DP));
        params.leftMargin = dp(HOME_CARD_RAIL_CENTER_DP) - dp(HOME_CARD_TIME_DOT_DP) / 2;
        interval.addView(dot, params);
        // Compute the first-line center after layout; this also follows Android font scaling.
        timeRow.addOnLayoutChangeListener((view, left, top, right, bottom,
                oldLeft, oldTop, oldRight, oldBottom) -> {
            android.graphics.Paint.FontMetricsInt metrics = caption.getPaint().getFontMetricsInt();
            int center = body.getTop() + timeRow.getTop() + caption.getTop() + caption.getBaseline()
                    + (metrics.ascent + metrics.descent) / 2;
            int dotTop = center - dp(HOME_CARD_TIME_DOT_DP) / 2;
            FrameLayout.LayoutParams current = (FrameLayout.LayoutParams) dot.getLayoutParams();
            if (current.topMargin != dotTop) {
                current.topMargin = dotTop;
                dot.setLayoutParams(current);
            }
        });
    }

    private int categoryIcon(String category) {
        switch (category) {
            case EventCategory.TASK: return R.drawable.ic_content_paste;
            case EventCategory.HOMEWORK: return R.drawable.ic_homework;
            default: return R.drawable.ic_event;
        }
    }

    /** Explicit vector bounds avoid intrinsic-size differences and font baseline offsets. */
    private LinearLayout scheduleAttribute(int resource, String value, String label,
            int textSize, int maxLines, boolean emphasized) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(maxLines == 1 ? Gravity.CENTER_VERTICAL : Gravity.TOP);
        TextView caption = text(value, textSize, false);
        caption.setIncludeFontPadding(false);
        caption.setMaxLines(maxLines);
        caption.setEllipsize(android.text.TextUtils.TruncateAt.END);
        caption.setTextColor(emphasized ? UiStyle.colors(this).primary
                : textSize >= 14 ? UiStyle.colors(this).text : UiStyle.colors(this).muted);
        caption.setContentDescription(label + "：" + value);
        ImageView icon = new ImageView(this);
        icon.setImageResource(resource);
        icon.setImageTintList(ColorStateList.valueOf(emphasized
                ? UiStyle.colors(this).primary : UiStyle.colors(this).muted));
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setPadding(0, 0, 0, 0);
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp(18), dp(18));
        iconParams.rightMargin = dp(6);
        if (maxLines > 1) iconParams.topMargin = Math.max(0, (caption.getLineHeight() - dp(18)) / 2);
        row.addView(icon, iconParams);
        row.addView(caption, new LinearLayout.LayoutParams(-2, -2));
        return row;
    }

    private void taskRow(LinearLayout parent, TaskRecord task, int index) {
        LinearLayout card = card();
        boolean inboxRow = section == INBOX && parent == results;
        TextView marker = new TextView(this);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(preview(task), 16, true);
        title.setMaxLines(3);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (inboxRow) {
            marker.setText("○");
            marker.setGravity(Gravity.CENTER);
            marker.setTextSize(22);
            marker.setTextColor(UiStyle.colors(this).primary);
            marker.setVisibility(selectedInboxIds.isEmpty() ? View.GONE : View.VISIBLE);
            titleRow.addView(marker, new LinearLayout.LayoutParams(dp(34), dp(34)));
        }
        card.addView(titleRow);
        boolean waiting = TaskRecord.PROCESSING.equals(task.status)
                || TaskRecord.QUEUED.equals(task.status);
        // A waiting row counts up instead of showing when it arrived, so "解析中" has a sign of life.
        TextView meta = text(statusText(task.status) + " · " + (waiting
                ? "已 " + elapsedText(System.currentTimeMillis() - task.createdAtMillis)
                : dateTime(task.createdAtMillis)), 13, false);
        if (waiting) {
            // A quiet pulse so "解析中" reads as alive rather than as a stuck row.
            UiMotion.pulse(meta);
            elapsedLabels.add(new ElapsedLabel(meta, task.createdAtMillis, task.status));
        }
        UiStyle.addSpaced(card, meta, 7, 0);
        if (inboxRow) {
            inboxRows.put(task.id, new InboxRow(card, marker));
            card.setOnLongClickListener(view -> {
                selectedInboxIds.add(task.id);
                updateSelectionUi();
                return true;
            });
            card.setOnClickListener(view -> {
                if (!selectedInboxIds.isEmpty()) toggleInboxSelection(task.id);
                else openTask(task.id);
            });
        } else {
            card.setOnClickListener(view -> openTask(task.id));
        }
        UiStyle.pressable(card);
        if (inboxRow) applyInboxRowSelection(task.id);
        UiStyle.addSpaced(parent, card, 4, 4);
    }

    private void toggleInboxSelection(long taskId) {
        if (!selectedInboxIds.remove(taskId)) selectedInboxIds.add(taskId);
        updateSelectionUi();
    }

    private void toggleSelectAll() {
        if (matchingInboxIds.isEmpty()) return;
        Set<Long> selected = currentSelection();
        if (selected.containsAll(matchingInboxIds)) {
            selected.removeAll(matchingInboxIds);
        } else {
            selected.addAll(matchingInboxIds);
        }
        updateSelectionUi();
    }

    private void clearInboxSelection() {
        selectedInboxIds.clear();
        updateSelectionUi();
    }

    private Set<Long> currentSelection() {
        return section == SCHEDULE ? selectedScheduleIds : selectedInboxIds;
    }

    private void clearCurrentSelection() {
        currentSelection().clear();
        updateSelectionUi();
    }

    // Legacy devices use this path; API 33+ gestures use selectionBack registered above.
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() {
        if (!handleDashboardBack()) super.onBackPressed();
    }

    private boolean handleDashboardBack() {
        if ((section == INBOX || section == SCHEDULE) && !currentSelection().isEmpty()) {
            clearCurrentSelection();
            return true;
        }
        if (sectionHistory.previous() < 0) return false;
        // One Back consumes one committed transition, never an in-flight preview or load.
        if (pageTransitionRunning || dragTargetLoaded || sectionLoadRunning || !initialDataReady)
            return true;
        switchTo(sectionHistory.previous(), true);
        return true;
    }

    private void updateSelectionBack() {
        if (android.os.Build.VERSION.SDK_INT < 33 || previewRender) return;
        boolean active = ((section == INBOX || section == SCHEDULE) && !currentSelection().isEmpty())
                || sectionHistory.previous() >= 0;
        if (active == selectionBackRegistered) return;
        if (active) {
            if (selectionBack == null) selectionBack = this::handleDashboardBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, selectionBack);
        }
        else getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(selectionBack);
        selectionBackRegistered = active;
    }

    private void updateSelectionUi() {
        updateSelectionBack();
        if (selectionBar == null) return;
        Set<Long> selected = currentSelection();
        boolean active = !selected.isEmpty();
        BatchSelectionUi.visible(selectionBar, active);
        selectionCount.setText(selected.size() + "/" + matchingInboxIds.size());
        selectionCount.setContentDescription("已选 " + selected.size() + " 条，共 " + matchingInboxIds.size() + " 条");
        boolean all = !matchingInboxIds.isEmpty()
                && selected.containsAll(matchingInboxIds);
        selectAllButton.setText(all ? "取消" : "全选");
        selectAllButton.setContentDescription(all ? "取消全选" : "选中本筛选的全部事项");
        deleteSelectedButton.setText("删除");
        deleteSelectedButton.setContentDescription("删除已选 " + selected.size() + " 项");
        boolean canPublish = (section == INBOX && statusIndex == 1)
                || (section == SCHEDULE && (scheduleTab == 1 || schedulePublication == 3) && scheduleSource != 1);
        publishSelectedButton.setVisibility(canPublish ? View.VISIBLE : View.GONE);
        publishSelectedButton.setEnabled(!bulkCalendarWriting);
        selectAllButton.setEnabled(!bulkCalendarWriting);
        deleteSelectedButton.setEnabled(!bulkCalendarWriting);
        for (Map.Entry<Long, InboxRow> entry : inboxRows.entrySet())
            applyInboxRowSelection(entry.getKey());
    }

    private void applyInboxRowSelection(long taskId) {
        InboxRow row = inboxRows.get(taskId);
        if (row == null) return;
        boolean selected = currentSelection().contains(taskId);
        BatchSelectionUi.marker(row.marker, !currentSelection().isEmpty(), selected);
        // Keep the acrylic card background; selection is already shown by the marker and toolbar.
        UiStyle.pressable(row.card);
    }

    private void publishSelected() {
        if (bulkCalendarWriting || currentSelection().isEmpty()) return;
        if (!new CalendarStore(this).hasReadPermission() || !new CalendarStore(this).hasWritePermission()) {
            requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR,
                    android.Manifest.permission.WRITE_CALENDAR}, BULK_CALENDAR_PERMISSION_REQUEST);
            return;
        }
        final boolean inbox = section == INBOX;
        final List<Long> selected = new ArrayList<>(currentSelection());
        bulkCalendarWriting = true;
        updateSelectionUi();
        new Thread(() -> {
            com.donglan.chrona.processing.BulkCalendarPublisher.Result result = null;
            String error = null;
            try { result = com.donglan.chrona.processing.BulkCalendarPublisher.publish(
                    getApplicationContext(), selected, inbox); }
            catch (Exception failure) {
                android.util.Log.w("Chrona", "Could not read selected calendar drafts", failure);
                error = "无法完成批量写入，请刷新后重试";
            }
            final com.donglan.chrona.processing.BulkCalendarPublisher.Result written = result;
            final String problem = error;
            runOnUiThread(() -> {
                bulkCalendarWriting = false;
                if (isFinishing() || isDestroyed()) return;
                if (inbox) selectedInboxIds.removeAll(selected);
                else selectedScheduleIds.removeAll(selected);
                dirtySections.add(HOME); dirtySections.add(INBOX); dirtySections.add(SCHEDULE);
                if (written != null) updatePendingCounts(written.review, written.failedTasks);
                lastCalendarFetch = 0;
                updateResults();
                updateSelectionUi();
                AgendaWidgetProvider.requestRefresh(this);
                AgendaReminder.requestRefresh(this);
                Feedback.showLong(this, written == null ? problem : written.summary());
                snapshot = dataSnapshot();
            });
        }, "chrona-calendar-bulk-write").start();
    }

    private void confirmDeleteSelected() {
        if (section == SCHEDULE) { confirmDeleteSchedules(); return; }
        if (selectedInboxIds.isEmpty()) return;
        int publishedCount = 0;
        try (TaskStore store = new TaskStore(this)) {
            for (long id : selectedInboxIds) {
                for (EventCandidate candidate : store.getCandidates(id))
                    if (candidate.calendarEventId != null) publishedCount++;
            }
        } catch (Exception exception) {
            Feedback.showLong(this, "无法读取所选任务：" + exception.getMessage());
            return;
        }
        if (publishedCount > 0 && !requestCalendarPermission()) return;
        int taskCount = selectedInboxIds.size();
        String linked = publishedCount == 0 ? ""
                : "及其关联的 " + publishedCount + " 条系统日历日程";
        UiDialogs.confirmDialog(this, "删除所选收件？",
                "将删除 " + taskCount + " 条收件及其待确认日程" + linked
                        + "。普通文件副本会保留在公共存储中；Android 10 及以上可在 Downloads/Chrona 找到。此操作无法撤销。",
                "删除", () -> deleteSelectedTasks());
    }

    private void confirmDeleteSchedules() {
        List<EventCandidate> chosen = new ArrayList<>();
        try (TaskStore store = new TaskStore(this)) {
            chosen.addAll(store.getCandidatesByIds(selectedScheduleIds));
        }
        if (chosen.isEmpty()) return;
        boolean linked = false;
        for (EventCandidate item : chosen) if (item.calendarEventId != null) linked = true;
        if (linked && !requestCalendarPermission()) return;
        UiDialogs.confirmDialog(this, "删除所选日程？", "将删除 " + chosen.size()
                + " 条日程及其系统日历关联，原始收件会保留。此操作无法撤销。", "删除",
                () -> deleteSchedules(chosen));
    }

    private void deleteSchedules(List<EventCandidate> chosen) {
        Button deleteAction = deleteSelectedButton;
        Button selectAction = selectAllButton;
        deleteAction.setEnabled(false);
        selectAction.setEnabled(false);
        new Thread(() -> {
            int failed = 0;
          synchronized (AndroidSync.LOCK) {
            try (TaskStore store = new TaskStore(this)) {
                CalendarStore calendar = new CalendarStore(this);
                Set<Long> affectedTasks = new LinkedHashSet<>();
                for (EventCandidate item : chosen) {
                    try {
                        new LinkedCalendarEvents(store,calendar).delete(store.getCandidatesByIds(java.util.Collections.singletonList(item.id)));
                        if (!store.deleteCandidate(item.id, item.taskId))
                            throw new IllegalStateException("日程已变化，请刷新后重试");
                        affectedTasks.add(item.taskId);
                    } catch (Exception exception) { failed++; }
                }
                for (long taskId : affectedTasks) {
                    List<EventCandidate> remaining = store.getCandidates(taskId);
                    boolean allPublished = !remaining.isEmpty();
                    for (EventCandidate item : remaining)
                        if (item.calendarEventId == null) allPublished = false;
                    store.updateStatus(taskId, allPublished ? TaskRecord.READY : TaskRecord.NEEDS_REVIEW, null);
                }
            } catch (Exception exception) { failed = chosen.size(); }
          }
            int failures = failed;
            runOnUiThread(() -> {
                deleteAction.setEnabled(true);
                selectAction.setEnabled(true);
                selectedScheduleIds.clear();
                if (isFinishing() || isDestroyed()) return;
                updateResults();
                updateSelectionBack();
                Feedback.showLong(this, failures == 0 ? "已删除 " + chosen.size() + " 条日程"
                        : "有 " + failures + " 条未完成删除，请检查后重试");
                snapshot = dataSnapshot();
            });
        }, "chrona-schedule-bulk-delete").start();
    }

    private boolean requestCalendarPermission() {
        boolean granted = checkSelfPermission(android.Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED
                && checkSelfPermission(android.Manifest.permission.WRITE_CALENDAR)
                        == PackageManager.PERMISSION_GRANTED;
        if (granted) return true;
        requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR,
                android.Manifest.permission.WRITE_CALENDAR}, CALENDAR_PERMISSION_REQUEST);
        Feedback.show(this, "授权日历权限后请再次点击删除");
        return false;
    }

    private void deleteSelectedTasks() {
        if (selectedInboxIds.isEmpty()) return;
        List<Long> taskIds = new ArrayList<>(selectedInboxIds);
        Button deleteAction = deleteSelectedButton;
        Button selectAction = selectAllButton;
        deleteSelectedButton.setEnabled(false);
        selectAllButton.setEnabled(false);
        selectionCount.setText("…");
        selectionCount.setContentDescription("正在清理关联日程");
        new Thread(() -> {
            int deleted = 0;
            int failed = 0;
          synchronized (AndroidSync.LOCK) {
            try (TaskStore store = new TaskStore(this)) {
                CalendarStore calendar = new CalendarStore(this);
                ImageStore images = new ImageStore(this);
                StreamingOutputStore outputs = new StreamingOutputStore(this);
                NotificationManager notifications = getSystemService(NotificationManager.class);
                for (long id : taskIds) {
                    try {
                        TaskRecord task = store.getTask(id);
                        if (task == null) continue;
                        List<EventCandidate> candidates = store.getCandidates(id);
                        ProcessingJobService.cancel(this, id);
                        new LinkedCalendarEvents(store,calendar).delete(candidates);
                        List<String> imagePaths = store.getImagePaths(id);
                        if (!store.deleteTask(id)) throw new IllegalStateException("任务已变化");
                        outputs.delete(id);
                        for (String imagePath : imagePaths) images.delete(imagePath);
                        if (notifications != null) notifications.cancel((int) id);
                        deleted++;
                    } catch (Exception exception) {
                        failed++;
                        android.util.Log.w("Chrona", "Could not delete inbox task " + id,
                                exception);
                    }
                }
            } catch (Exception exception) {
                failed += taskIds.size() - deleted - failed;
                android.util.Log.e("Chrona", "Could not delete selected inbox tasks", exception);
            }
          }
            int deletedCount = deleted;
            int failedCount = failed;
            runOnUiThread(() -> {
                selectedInboxIds.clear();
                deleteAction.setEnabled(true);
                selectAction.setEnabled(true);
                if (isFinishing() || isDestroyed()) return;
                updateResults();
                updateSelectionBack();
                Feedback.showLong(this, failedCount == 0
                        ? "已删除 " + deletedCount + " 条收件"
                        : "已删除 " + deletedCount + " 条，" + failedCount + " 条未删除，可重试");
                snapshot = dataSnapshot();
            });
        }, "chrona-inbox-bulk-delete").start();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions,
            int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == BULK_CALENDAR_PERMISSION_REQUEST) {
            boolean granted = new CalendarStore(this).hasReadPermission()
                    && new CalendarStore(this).hasWritePermission();
            Feedback.show(this, granted ? "日历权限已授权，请再次点击写入日历"
                    : "未获得日历权限，所选日程已保留");
            return;
        }
        if (requestCode == StartupPermissions.REQUEST_CODE) {
            lastCalendarFetch = 0;
            refreshSystemCalendar();
            AgendaWidgetProvider.requestRefresh(this);
            return;
        }
        if (requestCode == HOME_CALENDAR_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            HomeTimelinePreferences.setIncludesSystemCalendar(this, granted);
            calendarGeneration++;
            lastCalendarFetch = 0;
            systemCalendarEvents = java.util.Collections.emptyList();
            calendarResultWindow = "";
            refreshSystemCalendar();
            if (!granted) Feedback.show(this, "未获得读取日历权限");
        }
        if (requestCode == CALENDAR_PERMISSION_REQUEST) {
            Feedback.show(this, grantResults.length >= 2
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && grantResults[1] == PackageManager.PERMISSION_GRANTED
                    ? "日历权限已授权，请再次点击删除"
                    : "未获得日历权限，关联日程不会删除");
        }
    }

    private void openTask(long taskId) {
        startActivity(new Intent(this, TaskDetailActivity.class).putExtra("task_id", taskId));
    }

    private void openCandidate(EventCandidate candidate) {
        Intent intent = new Intent(this, candidate.repeatRule == null || candidate.occurrenceAtMillis!=null ? TaskDetailActivity.class : RepeatScheduleActivity.class)
                .putExtra("task_id", candidate.taskId)
                .putExtra(TaskDetailActivity.EXTRA_CANDIDATE_ID, candidate.id);
        if (candidate.occurrenceAtMillis != null) intent.putExtra("occurrence_at", candidate.occurrenceAtMillis.longValue());
        startActivity(intent);
    }

    private String statusText(String status) {
        if (TaskRecord.QUEUED.equals(status)) return "排队中";
        if (TaskRecord.PROCESSING.equals(status)) return "解析中";
        if (TaskRecord.NEEDS_REVIEW.equals(status)) return "待确认";
        if (TaskRecord.READY.equals(status)) return "已确认";
        return "处理失败";
    }

    private static String elapsedText(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        if (seconds < 60) return seconds + " 秒";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " 分 " + (seconds % 60) + " 秒";
        return (minutes / 60) + " 小时 " + (minutes % 60) + " 分";
    }

    private String inboxCategory() { return categoryIndex==0 ? null : EventCategory.VALUES[categoryIndex-1]; }
    private String[] categoryOptions() {
        String[] result = new String[EventCategory.LABELS.length + 1];
        result[0] = "全部类型";
        System.arraycopy(EventCategory.LABELS, 0, result, 1, EventCategory.LABELS.length);
        return result;
    }

    private void drawNavigation() {
        if (previewRender) return;
        if (navigation != null && (navigation.isTrackingTouch() || pageTransitionRunning)) return;
        if (navigation.itemCount() == 3 && navigationAttentionCount == attentionCount()) {
            for (int i = 0; i < 3; i++) {
                View cell = navigation.itemAt(i);
                if (cell instanceof FrameLayout wrapper) cell = wrapper.getChildAt(0);
                TextView item = (TextView) cell; boolean active = section == SECTION_ORDER[i];
                int color = active ? UiStyle.colors(this).onPrimaryContainer : UiStyle.colors(this).muted;
                item.setTextColor(color); item.setCompoundDrawableTintList(ColorStateList.valueOf(color));
                item.setContentDescription(item.getText() + (active ? "，当前页面" : ""));
            }
            if (navigationBadge != null) navigationBadge.setContentDescription("待处理 " + attentionCount() + " 项：待确认 " + pendingReview + "，失败 " + pendingFailed);
            if (navigation != null) navigation.setSelectedIndex(sectionPosition(section));
            return;
        }
        navigationAttentionCount = attentionCount(); navigationBadge = null;
        navigation.clearItems();
        String[] labels = {"首页", "日程", "收件箱"};
        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_schedule,
                R.drawable.ic_nav_inbox};
        for (int i = 0; i < labels.length; i++) {
            final int destination = SECTION_ORDER[i];
            TextView item = text(labels[i], 13, true);
            item.setGravity(Gravity.CENTER);
            item.setCompoundDrawablesWithIntrinsicBounds(0, icons[i], 0, 0);
            item.setCompoundDrawablePadding(dp(4));
            item.setPadding(0, dp(wide ? 8 : 2), 0, dp(wide ? 8 : 2));
            item.setTextColor(section == destination ? UiStyle.colors(this).onPrimaryContainer
                    : UiStyle.colors(this).muted);
            item.setCompoundDrawableTintList(ColorStateList.valueOf(section == destination
                    ? UiStyle.colors(this).onPrimaryContainer : UiStyle.colors(this).muted));
            item.setContentDescription(labels[i] + (section == destination ? "，当前页面" : ""));
            item.setBackgroundColor(Color.TRANSPARENT);
            item.setOnClickListener(view -> switchTo(destination));
            LinearLayout.LayoutParams params = wide
                    ? new LinearLayout.LayoutParams(-1, dp(78))
                    : new LinearLayout.LayoutParams(0, -1, 1);
            params.setMargins(dp(4), 0, dp(4), 0);
            navigation.addItem(destination == INBOX && attentionCount() > 0
                    ? withInboxBadge(item, destination) : (View) item, params);
        }
        if (navigation != null) navigation.setSelectedIndex(sectionPosition(section));
    }

    /** Everything that still wants the user's attention, whichever way it is shown. */
    private int attentionCount() {
        return pendingReview + pendingFailed;
    }

    /** Translucent count badge on the inbox entry; the dock keeps the page numbers off the list. */
    private View withInboxBadge(View item, int destination) {
        int count = attentionCount();
        FrameLayout wrapper = new FrameLayout(this);
        // The item's own elevation is what keeps the icon above the badge; move it to the wrapper
        // so the badge sits on top of the glyph instead of disappearing behind it.
        float itemElevation = item.getElevation();
        item.setElevation(0f);
        wrapper.setElevation(itemElevation);
        wrapper.addView(item, new FrameLayout.LayoutParams(-1, -1));
        TextView badge = countBadge(count);
        navigationBadge = badge;
        badge.setContentDescription("待处理 " + count + " 项：待确认 " + pendingReview
                + "，失败 " + pendingFailed);
        badge.setOnClickListener(view -> switchTo(destination));
        // Centred on the icon's own top-right corner: the glyph is 24dp wide and sits centred in
        // the item, so the badge centre belongs 12dp right of the item centre and level with the
        // glyph's top edge rather than halfway down its side.
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, dp(18),
                Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        badgeParams.setMargins(0, dp(2), 0, 0);
        wrapper.addView(badge, badgeParams);
        badge.setTranslationX(dp(13));
        return wrapper;
    }

    /** Theme-tinted, borderless surface for dashboard controls. */
    private void applyThemeControlSurface(View view, boolean selected, int radius) {
        UiStyle.acrylicChoice(view, selected, radius, false);
    }

    private void openCapture() {
        startActivity(new Intent(this, MainActivity.class));
    }

    private static int sectionPosition(int destination) {
        for (int i = 0; i < SECTION_ORDER.length; i++) {
            if (SECTION_ORDER[i] == destination) return i;
        }
        return -1;
    }

    private void switchTo(int destination) {
        switchTo(destination, false);
    }

    private void switchTo(int destination, boolean fromHistory) {
        if (destination < HOME || destination > SCHEDULE) return;
        if (!initialDataReady) {
            pendingSection = destination;
            pendingSectionFromHistory = fromHistory;
            return;
        }
        if (pageTransitionRunning || dragTargetLoaded || sectionLoadRunning) {
            pendingSection = destination;
            pendingSectionFromHistory = fromHistory;
            return;
        }
        if (destination == section) {
            if (navigation != null) navigation.finishPageSelection(sectionPosition(section));
            return;
        }
        if (!sectionPages.containsKey(destination) && preparedSection == null) {
            sectionSwitchFromHistory = fromHistory;
            prepareSectionSwitch(destination); return;
        }
        dockSectionSwitch = true;
        try { sectionPagerListener.onStart(); } finally { dockSectionSwitch = false; }
        sectionSwitchFromHistory = fromHistory;
        dragDirection = sectionPosition(destination) > sectionPosition(section) ? 1 : -1;
        adjacentPage = buildAdjacentSection(destination);
        float width = sectionPageWidth();
        adjacentPage.scroll.setTranslationX(dragDirection > 0 ? width : -width);
        pager.addView(adjacentPage.scroll, 1, new FrameLayout.LayoutParams(-1, -1));
        dragTargetLoaded = true;
        collectSectionSurfaces();
        finishSectionDrag(true, width);
    }

    private void prepareSectionSwitch(int destination) {
        boolean fromHistory = sectionSwitchFromHistory;
        sectionLoadRunning = true; loadingSection = destination;
        int token = ++sectionLoadGeneration;
        if (navigation != null) navigation.beginPageSettle(sectionPosition(destination));
        InboxQuery inbox = new InboxQuery(statusIndex, inboxCategory(), inboxQuery);
        ScheduleQuery schedule = buildScheduleQuery(System.currentTimeMillis(), ZoneId.systemDefault());
        boolean oldest = destination == INBOX ? inboxOldestFirst : scheduleOldestFirst;
        int page = destination == INBOX ? inboxPage : schedulePage;
        long focusCandidate = destination == SCHEDULE ? pendingHomeworkFocus : 0;
        boolean system = scheduleSource == 1;
        startupReader.execute(() -> {
            DashboardSectionData data;
            try { data = DashboardSectionData.load(getApplicationContext(), destination, inbox, schedule, oldest, page, system, focusCandidate); }
            catch (RuntimeException failure) {
                refresh.post(() -> {
                    if (token != sectionLoadGeneration || isDestroyed()) return;
                    sectionLoadRunning = false;
                    sectionSwitchFromHistory = false;
                    if (navigation != null) navigation.finishPageSelection(sectionPosition(section));
                    Feedback.show(this, "暂时无法读取页面，请重试"); switchToPendingSection();
                }); return;
            }
            refresh.post(() -> {
                if (token != sectionLoadGeneration || isDestroyed() || isFinishing()) return;
                sectionLoadRunning = false;
                sectionSwitchFromHistory = false;
                if (pendingSection >= 0 && pendingSection != destination) { switchToPendingSection(); return; }
                boolean requestedFromHistory = pendingSection >= 0 ? pendingSectionFromHistory : fromHistory;
                pendingSection = -1; pendingSectionFromHistory = false; preparedSection = data;
                try { switchTo(destination, requestedFromHistory); } finally { preparedSection = null; }
            });
        });
    }

    private void refreshSettledSection() {
        if (!foreground || isDestroyed()) return;
        if (completionRefreshPending) { refreshCompletionRows(); return; }
        if (completionRefreshRunning || !completingCandidates.isEmpty()) {
            refresh.postDelayed(deferredSectionRefresh, 80L); return;
        }
        if (pageTransitionRunning || dragTargetLoaded || sectionLoadRunning
                || (navigation != null && (navigation.isAnimating() || navigation.isTrackingTouch()))) {
            refresh.postDelayed(deferredSectionRefresh, 80L); return;
        }
        if (homeRefreshPending && section == HOME) refreshHomeContent();
        if (scheduleRefreshPending && section == SCHEDULE) { scheduleRefreshPending = false; updateResults(); }
        refreshActivePageIfNeeded(); snapshot = dataSnapshot();
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        UiStyle.card(card);
        UiStyle.cardInset(card);
        return card;
    }

    private void allowSectionDockOverflow(LinearLayout pageContent, FrameLayout host) {
        // Intermediate parents must not crop scaled controls to their ordinary row bounds.
        // ScrollView still clips at the actual page viewport.
        pageContent.setClipChildren(false); pageContent.setClipToPadding(false);
        host.setClipChildren(false); host.setClipToPadding(false);
    }

    private DockNavigationLayout filterDock(String[] labels, int selected,
            java.util.function.IntConsumer action, int[] counts) {
        DockNavigationLayout dock = new DockNavigationLayout(this, action, () -> {});
        // SwipePagerLayout otherwise forcibly re-enables interception on horizontal MOVE.
        strips.add(dock);
        dock.setPadding(dp(4), dp(2), dp(4), dp(2));
        int minimumWidth = dock.getPaddingLeft() + dock.getPaddingRight();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView label = text(labels[i], 14, true);
            label.setGravity(Gravity.CENTER); label.setIncludeFontPadding(false);
            label.setPadding(dp(8), 0, dp(8), 0);
            int labelWidth = (int)Math.ceil(label.getPaint().measureText(labels[i])) + dp(16);
            minimumWidth += labelWidth;
            label.setOnClickListener(view -> { action.accept(index); dock.finishPageSelection(index); updateCompactFilterSelection(dock, index); });
            View item = label;
            int count = counts == null ? 0 : counts[i];
            if (counts != null) {
                FrameLayout cell = new FrameLayout(this); cell.setClipChildren(false); cell.setClipToPadding(false);
                cell.addView(label, new FrameLayout.LayoutParams(-1, -1));
                TextView badge = countBadge(count);
                badge.setTag("filter_count_badge");
                badge.setVisibility(count > 0 ? View.VISIBLE : View.GONE);
                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, dp(18), Gravity.TOP | Gravity.END);
                badgeParams.setMargins(0, 0, dp(2), 0); cell.addView(badge, badgeParams); item = cell;
            }
            dock.addItem(item, new LinearLayout.LayoutParams(0, dp(44), labelWidth));
            item.setSelected(i == selected);
            label.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
        }
        dock.setMinimumWidth(minimumWidth);
        dock.setSelectedIndex(selected);
        return dock;
    }

    private View filterControlHost(DockNavigationLayout dock) {
        LinearLayout compact = new LinearLayout(this);
        compact.setTag("compact_filter");
        compact.setGravity(Gravity.CENTER);
        compact.setMinimumHeight(dp(48));
        compact.setPadding(dp(12), dp(6), dp(12), dp(6));
        compact.setClipChildren(false); compact.setClipToPadding(false);
        TextView value = text("",14,true);
        value.setGravity(Gravity.CENTER);
        value.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        compact.addView(value,new LinearLayout.LayoutParams(0,-2,1));
        ImageView chevron = new ImageView(this);
        chevron.setImageResource(R.drawable.ic_chevron_down);
        chevron.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).muted));
        chevron.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        compact.addView(chevron,new LinearLayout.LayoutParams(dp(24),dp(24)));
        applyThemeControlSurface(compact,false,UiStyle.RADIUS_CHIP);
        UiStyle.pressable(compact);
        dock.setTag(compact);
        for (int i=0;i<dock.itemCount();i++) if(dock.itemAt(i).isSelected()) updateCompactFilterSelection(dock,i);
        compact.setOnClickListener(v -> {
            String[] choices = new String[dock.itemCount()];
            int selected=0;
            for(int i=0;i<choices.length;i++) {
                View cell=dock.itemAt(i);
                TextView label=(TextView)(cell instanceof FrameLayout group?group.getChildAt(0):cell);
                choices[i]=label.getText().toString();
                if(cell instanceof FrameLayout group && group.getChildCount()>1 && group.getChildAt(1).getVisibility()==View.VISIBLE)
                    choices[i]+=" · "+((TextView)group.getChildAt(1)).getText();
                if(cell.isSelected()) selected=i;
            }
            UiDialogs.choiceDialog(this,"筛选",choices,selected,index -> {
                View cell=dock.itemAt(index);
                ((TextView)(cell instanceof FrameLayout group?group.getChildAt(0):cell)).performClick();
            });
        });
        FrameLayout adaptive = new FrameLayout(this) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                boolean overflow = View.MeasureSpec.getSize(widthSpec) < dock.getMinimumWidth();
                View child=overflow?compact:dock;
                if(getChildCount()==0 || getChildAt(0)!=child) {
                    removeAllViews();
                    addView(child,new FrameLayout.LayoutParams(-1,overflow?-2:dp(48)));
                }
                super.onMeasure(widthSpec,heightSpec);
            }
        };
        adaptive.setClipChildren(false);adaptive.setClipToPadding(false);
        strips.add(adaptive);
        return adaptive;
    }

    private void updateCompactFilterSelection(DockNavigationLayout dock, int selected) {
        if (!(dock.getTag() instanceof LinearLayout compact)) return;
        View cell = dock.itemAt(selected);
        TextView label=(TextView)(cell instanceof FrameLayout group?group.getChildAt(0):cell);
        ((TextView)compact.getChildAt(0)).setText(label.getText());
        compact.setContentDescription("筛选："+label.getText());
    }

    /** The one count marker: a translucent, theme-tinted pill used by every corner badge. */
    private TextView countBadge(int count) {
        int primary = UiStyle.colors(this).primary;
        TextView badge = text(count > 99 ? "99+" : Integer.toString(count), 11, true);
        badge.setGravity(Gravity.CENTER);
        badge.setTextColor(primary);
        badge.setMinWidth(dp(18));
        badge.setPadding(dp(5), 0, dp(5), 0);
        GradientDrawable shape = new GradientDrawable();
        shape.setCornerRadius(dp(9));
        shape.setColor(Color.argb(52, Color.red(primary), Color.green(primary), Color.blue(primary)));
        badge.setBackground(shape);
        // Anchors carry an elevation (the dock items animate theirs); without this the badge is
        // drawn underneath them, so its digit disappears behind the icon and only the tint shows.
        badge.setElevation(dp(6));
        return badge;
    }

    private String preview(TaskRecord task) {
        String value = task.rawText.replace('\n', ' ').trim();
        if (task.imagePath != null) value = "图片 · " + value;
        if (value.isEmpty()) value = "图片收件";
        return value.length() > 92 ? value.substring(0, 92) + "…" : value;
    }

    /** One date-time format for the whole page, independent of the device language. */
    private static String dateTime(long millis) {
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.CHINA)
                .format(new Date(millis));
    }

    private String formatWhen(EventCandidate item) {
        if (item.hasDefaultExecutionInterval()) {
            return dateTime(item.executionStartAtMillis()) + " — "
                    + dateTime(item.executionEndAtMillis());
        }
        if (item.isTask() && item.startAtMillis == null && item.effectiveDueAtMillis() != null) {
            return (item.isHomework() ? "作业截止 · " : "截止 · ")
                    + dateTime(item.effectiveDueAtMillis());
        }
        if (item.allDay && !HomeTimelineEntry.deadlineOnly(item)) {
            String start = AllDayDates.displayStart(item.startAtMillis);
            String end = AllDayDates.displayEnd(item.endAtMillis);
            return start.equals(end) ? start + " · 全天"
                    : start + " 至 " + end + " · 全天";
        }
        if (item.anchor()==null) return "尚未安排时间";
        return dateTime(item.anchor());
    }

    private void timelineDay(String title, LocalDate date, List<HomeTimelineEntry> entries) {
        if (entries.isEmpty()) return;
        android.graphics.Paint railPaint = new android.graphics.Paint();
        railPaint.setColor(UiStyle.colors(this).outline);
        FrameLayout dayGroup = new FrameLayout(this) {
            @Override protected void dispatchDraw(android.graphics.Canvas canvas) {
                // Card interiors draw the same rail. Only draw the gaps here, so translucent
                // surfaces never receive a duplicate line underneath their own line.
                android.graphics.Rect bounds = new android.graphics.Rect();
                int previousBottom = 0;
                ViewGroup column = (ViewGroup) getChildAt(0);
                if (column.getChildCount() < 2) { super.dispatchDraw(canvas); return; }
                View heading = column.getChildAt(0);
                ViewGroup rows = (ViewGroup) column.getChildAt(1);
                if (heading != null) {
                    bounds.set(0, 0, heading.getWidth(), heading.getHeight());
                    offsetDescendantRectToMyCoords(heading, bounds);
                    previousBottom = bounds.centerY();
                }
                for (int i = 0; i < rows.getChildCount(); i++) {
                    View item = rows.getChildAt(i);
                    bounds.set(0, 0, item.getWidth(), item.getHeight());
                    offsetDescendantRectToMyCoords(item, bounds);
                    canvas.drawRect(dp(HOME_CARD_RAIL_CENTER_DP - 1), previousBottom,
                            dp(HOME_CARD_RAIL_CENTER_DP + 1), bounds.top, railPaint);
                    previousBottom = bounds.bottom;
                }
                super.dispatchDraw(canvas);
            }
        };
        completionRows.branch(dayGroup, "timeline-day:" + date);
        LinearLayout dayContent = new LinearLayout(this);
        completionRows.branch(dayContent, "timeline-content:" + date);
        dayContent.setOrientation(LinearLayout.VERTICAL);
        dayGroup.addView(dayContent, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout markerSlot = new FrameLayout(this);
        View marker = new View(this);
        GradientDrawable markerShape = new GradientDrawable();
        markerShape.setShape(GradientDrawable.OVAL);
        markerShape.setColor(UiStyle.colors(this).primary);
        marker.setBackground(markerShape);
        markerSlot.addView(marker, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
        heading.addView(markerSlot, new LinearLayout.LayoutParams(dp(48), dp(18)));
        TextView label = text(title, 15, true);
        label.setIncludeFontPadding(false);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.setMargins(0, 0, dp(8), 0);
        heading.addView(label, labelParams);
        TextView dateLabel = text(date.getMonthValue() + "月" + date.getDayOfMonth() + "日", 13, false);
        dateLabel.setIncludeFontPadding(false);
        heading.addView(dateLabel);
        View divider = new View(this);
        divider.setBackgroundColor(UiStyle.colors(this).outline);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(0, dp(1), 1);
        dividerParams.setMargins(dp(10), 0, 0, 0);
        heading.addView(divider, dividerParams);
        UiStyle.addSpaced(dayContent, heading, 7, 2);

        LinearLayout dayRows = new LinearLayout(this);
        completionRows.branch(dayRows, "timeline-rows:" + date);
        dayRows.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < entries.size(); i++) {
            HomeTimelineEntry entry = entries.get(i);
            LinearLayout itemCard = card();
            completionRows.row(itemCard, "timeline:" + completionKey(entry.candidate, entry.systemEvent),
                    completionState(entry.candidate, entry.systemEvent));
            addScheduleCardContent(itemCard, entry.candidate, entry.systemEvent, false, true, null);
            itemCard.setOnClickListener(view -> openHomeEntry(entry));
            UiStyle.pressable(itemCard);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(-1, -2);
            cardParams.setMargins(0, dp(4), 0, dp(4));
            dayRows.addView(itemCard, cardParams);
        }
        UiStyle.addSpaced(dayContent, dayRows, 2, 7);
        UiStyle.addSpaced(content, dayGroup, 0, 0);
    }

    private TextView text(String content, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(size);
        if (bold) UiStyle.title(view); else UiStyle.muted(view);
        return view;
    }

    private void message(LinearLayout parent, String text) {
        UiStyle.addSpaced(parent, text(text, 14, false), 0, 0);
    }
    private void empty(LinearLayout parent, String text) {
        UiStyle.emptyState(parent, R.drawable.ic_inbox_outline, text, null, null);
    }
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
