package com.donglan.chrona;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.calendar.AllDayDates;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.calendar.CalendarOccurrence;
import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.data.ScheduleQuery;
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
    private static final int MOBILE_BOTTOM_AREA_HEIGHT_DP = 88;
    /** How far the page follows a drag towards a section that does not exist. */
    private static final float NO_NEIGHBOUR_RESISTANCE = 0.3f;
    private static final int RECORD_ENTRY_SIZE_DP = 52;
    private static final class InboxRow {
        final LinearLayout card;
        final TextView marker;
        InboxRow(LinearLayout card, TextView marker) {
            this.card = card;
            this.marker = marker;
        }
    }
    private static final class HomeTimelineEntry {
        final long timestampMillis;
        final EventCandidate candidate;
        final CalendarOccurrence systemEvent;

        HomeTimelineEntry(EventCandidate candidate) {
            this.timestampMillis = candidate.allDay
                    ? LocalDate.parse(AllDayDates.displayStart(candidate.startAtMillis))
                            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    : candidate.startAtMillis;
            this.candidate = candidate;
            this.systemEvent = null;
        }

        HomeTimelineEntry(CalendarOccurrence event) {
            timestampMillis = event.displayStart(ZoneId.systemDefault());
            candidate = null;
            systemEvent = event;
        }

        String title() { return candidate != null ? candidate.title : systemEvent.title; }
        boolean allDay() { return candidate != null ? candidate.allDay : systemEvent.allDay; }
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
    private int section = HOME;
    private int categoryIndex, statusIndex;
    private int scheduleTab;
    private int schedulePage, scheduleRange, scheduleCategory, schedulePublication, scheduleSource;
    private LocalDate scheduleDate = LocalDate.now(), scheduleUntil = LocalDate.now();
    private LinearLayout scheduleControls;
    private boolean calendarPermissionForSchedule;
    private int inboxShown = 12;
    /** The two browsable lists keep their own keyword and time direction. */
    private String inboxQuery = "";
    private String scheduleQuery = "";
    private boolean scheduleOldestFirst = true;
    private boolean inboxOldestFirst = false;
    private LinearLayout content;
    private LinearLayout navigation;
    private LinearLayout results;
    private LinearLayout filterChips;
    private ImageButton categoryChip;
    private TextView orderToggle;
    private LinearLayout selectionBar;
    private TextView selectionCount;
    private Button selectAllButton;
    private Button deleteSelectedButton;
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
    /** A neighbouring page is being rendered off-screen; the dock must not follow it. */
    private boolean previewRender;
    /** Entries waiting for review or already failed; shown on the dock and on the inbox filters. */
    private int pendingReview;
    private int pendingFailed;
    private final List<View> strips = new ArrayList<>();
    private GlassBackdropView backdrop;
    private boolean wide;
    /** Cards stagger in only for a screen the user just opened, never for a rebuild. */
    private boolean animateEntrances;
    private String snapshot = "";
    private int restoredScrollY;
    private List<ElapsedLabel> elapsedLabels = new ArrayList<>();
    private final Handler refresh = new Handler(Looper.getMainLooper());
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
        ThemeStore.apply(this);
        super.onCreate(state);
        if (state != null) {
            section = state.getInt(EXTRA_SECTION, HOME);
            categoryIndex = state.getInt("category");
            statusIndex = state.getInt("status");
            scheduleTab = state.getInt("schedule_tab");
            schedulePage = state.getInt("schedule_page");
            scheduleRange = state.getInt("schedule_range");
            scheduleCategory = state.getInt("schedule_category");
            schedulePublication = state.getInt("schedule_publication");
            scheduleSource = state.getInt("schedule_source");
            scheduleDate = LocalDate.parse(state.getString("schedule_date", LocalDate.now().toString()));
            scheduleUntil = LocalDate.parse(state.getString("schedule_until", scheduleDate.toString()));
            inboxShown = state.getInt("inbox_shown", 12);
            calendarPermissionForSchedule = state.getBoolean("schedule_permission_pending");
            inboxQuery = state.getString("inbox_query", "");
            scheduleQuery = state.getString("schedule_query", "");
            scheduleOldestFirst = state.getBoolean("schedule_oldest_first", true);
            inboxOldestFirst = state.getBoolean("inbox_oldest_first", false);
            long[] selected = state.getLongArray("selected_inbox_ids");
            if (selected != null) for (long id : selected) selectedInboxIds.add(id);
            long[] selectedSchedules = state.getLongArray("selected_schedule_ids");
            if (selectedSchedules != null) for (long id : selectedSchedules) selectedScheduleIds.add(id);
            restoredScrollY = state.getInt("scroll_y");
        } else {
            section = getIntent().getIntExtra(EXTRA_SECTION, HOME);
            animateEntrances = true;
        }
        buildShell();
        if (state == null) backdrop.playEntrance();
        if (android.os.Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{android.Manifest.permission.POST_NOTIFICATIONS}, 10);
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt(EXTRA_SECTION, section);
        state.putInt("category", categoryIndex);
        state.putInt("status", statusIndex);
        state.putInt("schedule_tab", scheduleTab);
        state.putInt("schedule_page", schedulePage);
        state.putInt("schedule_range", scheduleRange);
        state.putInt("schedule_category", scheduleCategory);
        state.putInt("schedule_publication", schedulePublication);
        state.putInt("schedule_source", scheduleSource);
        state.putString("schedule_date", scheduleDate.toString());
        state.putString("schedule_until", scheduleUntil.toString());
        state.putInt("inbox_shown", inboxShown);
        state.putBoolean("schedule_permission_pending", calendarPermissionForSchedule);
        state.putString("inbox_query", inboxQuery);
        state.putString("schedule_query", scheduleQuery);
        state.putBoolean("schedule_oldest_first", scheduleOldestFirst);
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
        final LinearLayout filterChips;
        final ImageButton categoryChip;
        final TextView orderToggle;
        final LinearLayout selectionBar;
        final TextView selectionCount;
        final Button selectAllButton;
        final Button deleteSelectedButton;
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
            this.inboxRows = DashboardActivity.this.inboxRows;
            this.matchingInboxIds = DashboardActivity.this.matchingInboxIds;
            this.elapsedLabels = DashboardActivity.this.elapsedLabels;
            this.strips = new ArrayList<>(DashboardActivity.this.strips);
            this.scheduleControls = DashboardActivity.this.scheduleControls;
        }
    }

    private void activateSectionPage(SectionPage page) {
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
            activateSectionPage(cached);
            boolean previousPreview = previewRender;
            previewRender = true;
            try {
                refreshActivePageIfNeeded();
                cached = new SectionPage();
                sectionPages.put(destination, cached);
            } finally {
                previewRender = previousPreview;
                activateSectionPage(current);
            }
            return cached;
        }
        ScrollView targetScroll = new ScrollView(this);
        targetScroll.setFillViewport(true);
        targetScroll.setVerticalScrollBarEnabled(false);
        LinearLayout targetContent = new LinearLayout(this);
        targetContent.setOrientation(LinearLayout.VERTICAL);
        int horizontal = wide ? Math.max(32,
                (getResources().getConfiguration().screenWidthDp - 90 - 720) / 2) : 20;
        targetContent.setPadding(dp(horizontal), dp(wide ? 8 : 2), dp(horizontal),
                dp(wide ? 42 : MOBILE_BOTTOM_AREA_HEIGHT_DP + 24));
        FrameLayout targetHost = new FrameLayout(this);
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
        inboxRows = new LinkedHashMap<>();
        matchingInboxIds = new ArrayList<>();
        elapsedLabels = new ArrayList<>();
        strips.clear();
        boolean previousEntrances = animateEntrances;
        animateEntrances = false;
        previewRender = true;
        try {
            render();
        } finally {
            previewRender = false;
            animateEntrances = previousEntrances;
        }

        SectionPage target = new SectionPage();
        sectionPages.put(destination, target);
        activateSectionPage(current);
        return target;
    }

    private void rememberRenderedPage() {
        sectionPages.put(section, new SectionPage());
        sectionVersions.put(section, dataSnapshot());
        dirtySections.remove(section);
    }

    private void refreshActivePageIfNeeded() {
        String version = dataSnapshot();
        if (dirtySections.contains(section) || !version.equals(sectionVersions.get(section))) {
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
            scroll.animate().cancel();
            if (adjacentPage != null) {
                adjacentPage.scroll.animate().cancel();
                pager.removeView(adjacentPage.scroll);
                adjacentPage = null;
            }
            pageTransitionRunning = false;
            dragTargetLoaded = false;
            dragDirection = 0;
            scroll.setTranslationX(0f);
        }

        @Override public void onDrag(float distanceX) {
            if (pageTransitionRunning) return;
            float width = sectionPageWidth();
            float offset = Math.max(-width, Math.min(width, distanceX));
            int direction = offset < 0 ? 1 : -1;
            if (!dragTargetLoaded && Math.abs(offset) >= dp(10)) {
                int destination = section + direction;
                if (destination < HOME || destination > SCHEDULE) {
                    // Nothing lies that way: the page only gives a little, but it still has to
                    // resample its acrylic cards every frame like a normal drag does — skipping
                    // that here left them showing the snapshot taken when the drag began.
                    scroll.setTranslationX(offset * NO_NEIGHBOUR_RESISTANCE);
                    invalidateSectionSurfaces();
                    return;
                }
                dragDirection = direction;
                SectionPage preview = buildAdjacentSection(destination);
                // Positioned before it joins the pager so no frame can show it at the origin.
                preview.scroll.setTranslationX(direction > 0 ? width : -width);
                pager.addView(preview.scroll, 1, new FrameLayout.LayoutParams(-1, -1));
                adjacentPage = preview;
                dragTargetLoaded = true;
            }
            if (dragTargetLoaded && adjacentPage != null) {
                if (dragDirection > 0 && offset > 0 || dragDirection < 0 && offset < 0) offset = 0;
                scroll.setTranslationX(offset);
                adjacentPage.scroll.setTranslationX((dragDirection > 0 ? width : -width) + offset);
            } else {
                scroll.setTranslationX(offset);
            }
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
        scroll.animate().cancel();
        adjacentPage.scroll.animate().cancel();
        if (!android.animation.ValueAnimator.areAnimatorsEnabled() || width <= 0) {
            scroll.setTranslationX(currentTarget);
            adjacentPage.scroll.setTranslationX(adjacentTarget);
            completeSectionDrag(commit);
            return;
        }
        float remaining = Math.abs(currentTarget - scroll.getTranslationX());
        long duration = commit
                ? Math.max(140L, Math.min(280L, Math.round(280f * remaining / width)))
                : SwipePagerLayout.recoilDuration(remaining, width);
        android.view.animation.Interpolator interpolator = commit
                ? new android.view.animation.DecelerateInterpolator(1.35f)
                : SwipePagerLayout.RECOIL_INTERPOLATOR;
        scroll.animate().translationX(currentTarget).setDuration(duration)
                .setInterpolator(interpolator)
                .setUpdateListener(animation -> invalidateSectionSurfaces()).start();
        adjacentPage.scroll.animate().translationX(adjacentTarget).setDuration(duration)
                .setInterpolator(interpolator)
                .setUpdateListener(animation -> invalidateSectionSurfaces())
                .withEndAction(() -> completeSectionDrag(commit)).start();
    }

    private void settleCurrentSection() {
        if (pageTransitionRunning) return;
        scroll.animate().cancel();
        scroll.animate().translationX(0f)
                .setDuration(SwipePagerLayout.recoilDuration(
                        Math.abs(scroll.getTranslationX()), sectionPageWidth()))
                .setInterpolator(SwipePagerLayout.RECOIL_INTERPOLATOR)
                .setUpdateListener(animation -> invalidateSectionSurfaces())
                .withEndAction(this::invalidateSectionSurfaces).start();
    }

    /**
     * Acrylic cards sample the blurred backdrop at their own position, so every translated frame
     * has to invalidate them; otherwise they keep the snapshot taken before the drag started.
     */
    private void invalidateSectionSurfaces() {
        if (pager != null && pager.isAttachedToWindow()) UiStyle.invalidateAcrylicSurfaces(pager);
    }

    private void completeSectionDrag(boolean commit) {
        SectionPage target = adjacentPage;
        adjacentPage = null;
        dragTargetLoaded = false;
        dragDirection = 0;
        pageTransitionRunning = false;
        if (target == null) {
            scroll.setTranslationX(0f);
            return;
        }
        if (commit) {
            clearCurrentSelection();
            sectionPages.put(section, new SectionPage());
            pager.removeView(scroll);
            target.scroll.setTranslationX(0f);
            activateSectionPage(target);
            refreshActivePageIfNeeded();
            updateSelectionBack();
            drawNavigation();
        } else {
            pager.removeView(target.scroll);
            target.scroll.setTranslationX(0f);
            scroll.setTranslationX(0f);
            if (homeRefreshPending) refreshHomeContent();
            refreshActivePageIfNeeded();
        }
        if (scheduleRefreshPending && section == SCHEDULE) {
            scheduleRefreshPending = false;
            updateResults();
        }
        invalidateSectionSurfaces();
        snapshot = dataSnapshot();
        if (section == HOME || (section == SCHEDULE && scheduleSource == 1))
            refresh.post(this::refreshSystemCalendar);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        switchTo(intent.getIntExtra(EXTRA_SECTION, section));
    }

    @Override protected void onResume() {
        super.onResume();
        foreground = true;
        lastCalendarFetch = 0;
        int recovered = ProcessingJobService.reconcile(this);
        if (recovered > 0) Feedback.showLong(this, recovered + " 条解析已中断，请在收件箱重试");
        int oldScroll = restoredScrollY != 0 ? restoredScrollY : scroll.getScrollY();
        restoredScrollY = 0;
        refreshActivePageIfNeeded();
        ScrollView resumed = scroll;
        resumed.post(() -> resumed.scrollTo(0, oldScroll));
        snapshot = dataSnapshot();
        refresh.postDelayed(poll, 5000L);
        refresh.postDelayed(elapsedTicker, 1000L);
        refresh.post(this::refreshSystemCalendar);
    }

    @Override protected void onPause() {
        foreground = false;
        calendarGeneration++;
        if (calendarCancellation != null) calendarCancellation.cancel();
        refresh.removeCallbacks(poll);
        refresh.removeCallbacks(elapsedTicker);
        backdrop.stop();
        super.onPause();
    }

    @Override protected void onDestroy() {
        refresh.removeCallbacksAndMessages(null);
        sectionPages.clear();
        sectionVersions.clear();
        dirtySections.clear();
        calendarReader.shutdownNow();
        super.onDestroy();
    }

    private void pollChanges() {
        if (section == HOME || (section == SCHEDULE && scheduleSource == 1)) refreshSystemCalendar();
        String current = dataSnapshot();
        if (!current.equals(snapshot)) {
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
        if ((!systemSchedule && !HomeTimelinePreferences.includesSystemCalendar(this))
                || !new CalendarStore(this).hasReadPermission()) {
            if (!systemCalendarEvents.isEmpty()) {
                systemCalendarEvents = java.util.Collections.emptyList();
                calendarResultWindow = "";
                if (systemSchedule) updateResults();
                else refreshHomeContent();
            }
            return;
        }
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate[] window = systemSchedule ? scheduleWindow(true)
                : new LocalDate[]{today, today.plusMonths(1)};
        long begin = window[0].atStartOfDay(zone).toInstant().toEpochMilli();
        long end = window[1].atStartOfDay(zone).toInstant().toEpochMilli();
        String windowKey = begin + ":" + end;
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
                for (CalendarOccurrence event : new CalendarStore(getApplicationContext())
                        .listInstances(begin - padding, end + padding, cancellation)) {
                    if (event.displayEnd(zone) > begin && event.displayStart(zone) < end)
                        loaded.add(event);
                }
            } catch (RuntimeException exception) {
                failed = !cancellation.isCanceled();
            }
            List<CalendarOccurrence> result = java.util.Collections.unmodifiableList(loaded);
            boolean error = failed;
            refresh.post(() -> {
                calendarLoading = false;
                calendarCancellation = null;
                if (generation != calendarGeneration || !foreground || isDestroyed()) return;
                LocalDate[] desired = section == SCHEDULE && scheduleSource == 1
                        ? scheduleWindow(true) : new LocalDate[]{LocalDate.now(), LocalDate.now().plusMonths(1)};
                if (!windowKey.equals(calendarWindowKey(desired))) {
                    refresh.post(this::refreshSystemCalendar);
                    return;
                }
                if (!new CalendarStore(this).hasReadPermission()) return;
                if (error) {
                    if (!calendarErrorShown) Feedback.show(this, "无法读取系统日程，请稍后重试");
                    calendarErrorShown = true;
                    return;
                }
                calendarErrorShown = false;
                if (!systemCalendarEvents.equals(result) || !windowKey.equals(calendarResultWindow)) {
                    systemCalendarEvents = result;
                    calendarResultWindow = windowKey;
                    dirtySections.add(HOME);
                    dirtySections.add(SCHEDULE);
                    if (section == SCHEDULE && scheduleSource == 1) {
                        if (pageTransitionRunning || dragTargetLoaded) scheduleRefreshPending = true;
                        else updateResults();
                    }
                    else refreshHomeContent();
                }
            });
        });
    }

    private void refreshHomeContent() {
        if (section != HOME || previewRender) return;
        if (pageTransitionRunning || dragTargetLoaded) {
            homeRefreshPending = true;
            return;
        }
        homeRefreshPending = false;
        int y = scroll.getScrollY();
        int x = strips.isEmpty() ? 0 : strips.get(0).getScrollX();
        render();
        ScrollView refreshed = scroll;
        View strip = strips.isEmpty() ? null : strips.get(0);
        refreshed.post(() -> {
            refreshed.scrollTo(0, y);
            if (strip != null) strip.scrollTo(x, 0);
        });
    }

    private String calendarWindowKey(LocalDate[] window) {
        ZoneId zone = ZoneId.systemDefault();
        return window[0].atStartOfDay(zone).toInstant().toEpochMilli() + ":"
                + window[1].atStartOfDay(zone).toInstant().toEpochMilli();
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
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(event.startMillis));
    }

    private void openHomeEntry(HomeTimelineEntry entry) {
        if (entry.candidate != null) {
            openTask(entry.candidate.taskId);
            return;
        }
        CalendarOccurrence event = entry.systemEvent;
        Intent intent = new Intent(Intent.ACTION_VIEW, android.content.ContentUris.withAppendedId(
                android.provider.CalendarContract.Events.CONTENT_URI, event.eventId));
        intent.putExtra(android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.startMillis);
        intent.putExtra(android.provider.CalendarContract.EXTRA_EVENT_END_TIME, event.endMillis);
        intent.putExtra(android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY, event.allDay);
        try { startActivity(intent); }
        catch (android.content.ActivityNotFoundException exception) {
            Feedback.show(this, "未找到可打开日程的日历应用");
        }
    }

    private String dataSnapshot() {
        try (TaskStore store = new TaskStore(this)) {
            // Database triggers track edits too; time buckets move ended events between tabs.
            return store.dataRevision() + ":" + (System.currentTimeMillis() / 60000L)
                    + ":" + HomeTimelinePreferences.getItemLimit(this)
                    + ":" + HomeTimelinePreferences.includesSystemCalendar(this)
                    + ":" + new CalendarStore(this).hasReadPermission();
        } catch (Exception ignored) {
            return "";
        }
    }

    private void buildShell() {
        wide = getResources().getConfiguration().screenWidthDp >= 600;
        FrameLayout stage = new FrameLayout(this);
        backdrop = new GlassBackdropView(this);
        stage.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(wide ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL);
        UiStyle.page(this, shell);
        shell.setBackgroundColor(Color.TRANSPARENT);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = wide ? Math.max(32,
                (getResources().getConfiguration().screenWidthDp - 90 - 720) / 2) : 20;
        content.setPadding(dp(horizontal), dp(wide ? 8 : 2), dp(horizontal),
                dp(wide ? 42 : MOBILE_BOTTOM_AREA_HEIGHT_DP + 24));
        // The cross-fade of a section switch drops a snapshot of the outgoing body next to it, so
        // the body needs a parent that accepts a second child; a ScrollView raises instead.
        FrameLayout contentHost = new FrameLayout(this);
        contentHost.addView(content, new FrameLayout.LayoutParams(-1, -2));
        scroll.addView(contentHost, new FrameLayout.LayoutParams(-1, -2));
        pager = new SwipePagerLayout(this, sectionPagerListener);
        pager.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        navigation = new LinearLayout(this);
        navigation.setOrientation(wide ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(wide ? 8 : 8), dp(wide ? 24 : 6),
                dp(wide ? 8 : 8), dp(wide ? 10 : 6));
        if (wide) UiStyle.glass(navigation);
        FrameLayout mobileBottomArea = null;
        if (wide) {
            shell.addView(navigation, new LinearLayout.LayoutParams(dp(90), -1));
            shell.addView(pager, new LinearLayout.LayoutParams(0, -1, 1));
        } else {
            shell.addView(pager, new LinearLayout.LayoutParams(-1, 0, 1));
            mobileBottomArea = new FrameLayout(this);
            mobileBottomArea.setClipChildren(false);
            mobileBottomArea.setClipToPadding(false);
            UiStyle.glass(navigation);
            navigation.setElevation(dp(8));
            FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(-1,
                    dp(MOBILE_DOCK_HEIGHT_DP), Gravity.BOTTOM | Gravity.START);
            dockParams.setMargins(dp(14), 0, dp(94), dp(8));
            mobileBottomArea.addView(navigation, dockParams);
            ImageButton record = UiStyle.recordEntry(this, this::openCapture);
            FrameLayout.LayoutParams recordParams = new FrameLayout.LayoutParams(
                    dp(RECORD_ENTRY_SIZE_DP), dp(RECORD_ENTRY_SIZE_DP),
                    Gravity.BOTTOM | Gravity.END);
            recordParams.setMargins(0, 0, dp(14), dp(14));
            mobileBottomArea.addView(record, recordParams);
        }
        stage.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        if (wide) {
            ImageButton capture = UiStyle.recordEntry(this, this::openCapture);
            FrameLayout.LayoutParams floating = new FrameLayout.LayoutParams(dp(56), dp(56),
                    Gravity.END | Gravity.BOTTOM);
            floating.setMargins(0, 0, dp(24), dp(24));
            stage.addView(capture, floating);
            UiStyle.applyInsets(stage, shell, capture);
        } else {
            FrameLayout.LayoutParams dockOverlay = new FrameLayout.LayoutParams(-1,
                    dp(MOBILE_BOTTOM_AREA_HEIGHT_DP), Gravity.BOTTOM | Gravity.START);
            dockOverlay.setMargins(0, 0, 0, dp(8));
            stage.addView(mobileBottomArea, dockOverlay);
            UiStyle.applyInsets(stage, shell, mobileBottomArea);
        }
        setContentView(stage);
    }

    private void render() {
        results = null;
        scheduleControls = null;
        filterChips = null;
        categoryChip = null;
        orderToggle = null;
        strips.clear();
        elapsedLabels.clear();
        content.removeAllViews();
        addHeader();
        try (TaskStore store = new TaskStore(this)) {
            pendingReview = store.taskCountByStatus(TaskRecord.NEEDS_REVIEW);
            pendingFailed = store.taskCountByStatus(TaskRecord.FAILED);
            if (section == HOME) home(store);
            else if (section == INBOX) inbox(store);
            else schedule(store);
        } catch (Exception exception) {
            message(content, "暂时无法读取日程：" + exception.getMessage());
        }
        if (animateEntrances) {
            animateEntrances = false;
            UiStyle.enterChildren(content);
        }
        drawNavigation();
        if ((section == HOME || (section == SCHEDULE && scheduleSource == 1)) && !previewRender)
            scroll.post(this::refreshSystemCalendar);
        if (pager != null) pager.setGesturePriorityChildren(strips);
        rememberRenderedPage();
    }

    private void addHeader() {
        String eyebrow = section == HOME
                ? new SimpleDateFormat("M月d日 EEEE", Locale.CHINA).format(new Date())
                : "拾时 · Chrona";
        TextView brand = text(eyebrow, 13, true);
        brand.setTextColor(UiStyle.colors(this).primary);
        UiStyle.addSpaced(content, brand, 0, 4);
        // The gear shares the title's row, so it centres on the title rather than on the two-line
        // block above it, which used to leave it sitting visibly high.
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(section == HOME ? "今天的安排"
                : section == INBOX ? "收件箱" : "日程", section == HOME ? 25 : 28, true);
        title.setMaxLines(1);
        // Without this the large CJK glyphs ride above the centre line and no longer match the
        // search field, the order arrow and the gear they share the row with.
        title.setIncludeFontPadding(false);
        title.setGravity(Gravity.CENTER_VERTICAL);
        if (section == HOME) {
            row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        } else {
            // The search field sits between the page title and the trailing control, so filtering
            // never moves the settings entry and the keyword stays reachable while scrolling.
            row.addView(title, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(0, dp(36), 1);
            searchParams.setMargins(dp(8), 0, dp(8), 0);
            row.addView(buildSearchField(), searchParams);
        }
        row.addView(buildHeaderControls(), new LinearLayout.LayoutParams(-2, dp(44)));
        UiStyle.addSpaced(content, row, 0, section == HOME ? 10 : 12);
    }

    /**
     * The order arrow and the settings entry share a single pill: two separate bubbles in the same
     * corner read as unrelated controls and cost width the search field needs.
     */
    private LinearLayout buildHeaderControls() {
        LinearLayout group = new LinearLayout(this);
        group.setOrientation(LinearLayout.HORIZONTAL);
        group.setGravity(Gravity.CENTER_VERTICAL);
        UiStyle.acrylicChoice(group, false, UiStyle.RADIUS_PILL, false);
        orderToggle = null;
        if (section != HOME) {
            TextView order = text("", 15, true);
            order.setGravity(Gravity.CENTER);
            order.setMinWidth(dp(40));
            order.setPadding(dp(10), 0, dp(10), 0);
            order.setOnClickListener(view -> toggleOrder());
            UiStyle.pressable(order);
            orderToggle = order;
            refreshOrderToggle();
            group.addView(order, new LinearLayout.LayoutParams(-2, -1));
            View divider = new View(this);
            divider.setBackgroundColor(UiStyle.colors(this).outline);
            group.addView(divider, new LinearLayout.LayoutParams(dp(1), dp(16)));
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
        group.addView(settings, new LinearLayout.LayoutParams(dp(44), -1));
        return group;
    }

    private void toggleOrder() {
        if (section == INBOX) {
            inboxOldestFirst = !inboxOldestFirst;
            inboxShown = 12;
        } else {
            scheduleOldestFirst = !scheduleOldestFirst;
            schedulePage = 0;
        }
        refreshOrderToggle();
        updateResultsWithEntrance();
    }

    /** A compact keyword field for the two browsable lists; typing only re-renders the results. */
    private EditText buildSearchField() {
        final int ownerSection = section;
        EditText field = new EditText(this);
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
                if (ownerSection == INBOX) {
                    inboxQuery = keyword;
                    inboxShown = 12;
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

    private static boolean matchesQuery(TaskRecord task, String query) {
        return containsQuery(task.rawText, query) || containsQuery(task.linkText, query);
    }

    private void home(TaskStore store) {
        CheckBox include = new CheckBox(this);
        include.setText("包含系统其他日程");
        include.setTextSize(13);
        include.setTextColor(UiStyle.colors(this).muted);
        include.setButtonTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        include.setChecked(HomeTimelinePreferences.includesSystemCalendar(this));
        include.setOnCheckedChangeListener((button, enabled) -> {
            if (enabled && !new CalendarStore(this).hasReadPermission()) {
                requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR},
                        HOME_CALENDAR_PERMISSION_REQUEST);
                return;
            }
            HomeTimelinePreferences.setIncludesSystemCalendar(this, enabled);
            calendarGeneration++;
            lastCalendarFetch = 0;
            if (!enabled) systemCalendarEvents = java.util.Collections.emptyList();
            refreshHomeContent();
        });
        UiStyle.addSpaced(content, include, 0, 4);
        long now = System.currentTimeMillis();
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate endDate = today.plusMonths(1);
        int limit = HomeTimelinePreferences.getItemLimit(this);
        List<EventCandidate> candidates = store.queryScheduleFirst(new ScheduleQuery(0, null, 0,
                "", today, endDate, now, zone), limit);
        List<HomeTimelineEntry> upcoming = new ArrayList<>();
        for (EventCandidate candidate : store.queryScheduleFirst(new ScheduleQuery(0, null, 0,
                "", null, null, now, zone), 5)) upcoming.add(new HomeTimelineEntry(candidate));
        List<CalendarOccurrence> external = java.util.Collections.emptyList();
        if (HomeTimelinePreferences.includesSystemCalendar(this)
                && new CalendarStore(this).hasReadPermission()) {
            Set<Long> linkedIds = new HashSet<>(store.linkedCalendarIds());
            external = CalendarOccurrence.unlinked(calendarEventsForWindow(new LocalDate[]{today, endDate}), linkedIds);
            for (CalendarOccurrence event : external)
                if (event.displayEnd(ZoneId.systemDefault()) > now)
                    upcoming.add(new HomeTimelineEntry(event));
        }
        showUpcomingCarousel(upcoming);

        long rangeStart = today.atStartOfDay(zone).toInstant().toEpochMilli();
        long rangeEnd = endDate.atStartOfDay(zone).toInstant().toEpochMilli();
        List<HomeTimelineEntry> all = new ArrayList<>();
        Set<Long> candidateIds = new HashSet<>();
        for (EventCandidate item : candidates) {
            if (!candidateIntersects(item, today, endDate, rangeStart, rangeEnd, now))
                continue;
            if (!candidateIds.add(item.id)) continue;
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
        for (Map.Entry<LocalDate, List<HomeTimelineEntry>> day : byDate.entrySet())
            timelineDay(timelineDayTitle(day.getKey(), today), day.getKey(), day.getValue());
    }

    private void showUpcomingCarousel(List<HomeTimelineEntry> upcoming) {
        TextView heading = text("下一件事", 15, true);
        UiStyle.addSpaced(content, heading, 0, 6);
        upcoming.sort(java.util.Comparator.comparingLong(item -> item.timestampMillis));
        if (upcoming.size() > 5) upcoming = new ArrayList<>(upcoming.subList(0, 5));
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setFillViewport(false);
        strip.setClipToPadding(false);
        strip.setPadding(0, 0, dp(12), 0);
        strips.add(strip);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        if (upcoming.isEmpty()) {
            LinearLayout empty = card();
            empty.setPadding(dp(14), dp(12), dp(14), dp(12));
            empty.addView(text("还没有即将到来的日程", 14, false));
            cards.addView(empty, new LinearLayout.LayoutParams(dp(240), -2));
        } else {
            for (HomeTimelineEntry entry : upcoming) {
                LinearLayout item = card();
                item.setPadding(dp(14), dp(12), dp(14), dp(12));
                TextView title = text(entry.title(), 15, true);
                title.setMaxLines(2);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                item.addView(title);
                UiStyle.addSpaced(item, text(homeEntryWhen(entry), 12, false), 7, 0);
                item.setOnClickListener(view -> openHomeEntry(entry));
                UiStyle.pressable(item);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(240), -2);
                params.setMargins(0, 0, dp(10), 0);
                cards.addView(item, params);
            }
        }
        strip.addView(cards);
        UiStyle.addSpaced(content, strip, 0, 10);
    }

    private boolean candidateIntersects(EventCandidate item, LocalDate today, LocalDate endDate,
            long rangeStart, long rangeEnd, long now) {
        if (item.startAtMillis == null || item.endAtMillis == null) return false;
        if (item.allDay) {
            LocalDate start = LocalDate.parse(AllDayDates.displayStart(item.startAtMillis));
            LocalDate inclusiveEnd = LocalDate.parse(AllDayDates.displayEnd(item.endAtMillis));
            return !inclusiveEnd.isBefore(today) && start.isBefore(endDate);
        }
        return item.endAtMillis > now && item.endAtMillis > rangeStart
                && item.startAtMillis < rangeEnd;
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
        message(content, "所有输入与处理进度。点开一条，可确认、编辑或重试。");
        filterChips = chipRow(new String[]{"全部", "待确认", "处理中", "失败", "已写入"},
                statusIndex, index -> {
                    if (statusIndex == index) return;
                    clearInboxSelection();
                    statusIndex = index;
                    inboxShown = 12;
                    updateChipSelection(statusIndex);
                    updateResultsWithEntrance();
                }, inboxCategoryButton(),
                new int[]{0, pendingReview, 0, pendingFailed, 0});
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
        deleteSelectedButton = inboxSelectionAction("删除", this::confirmDeleteSelected);
        selectionBar.addView(selectAllButton);
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
        applyThemeControlSurface(category, categoryIndex > 0, UiStyle.RADIUS_PILL);
        categoryChip = category;
        category.setOnClickListener(view -> {
            String[] choices = categoryOptions();
            UiStyle.choiceDialog(this, "选择收件类型", choices, categoryIndex, selected -> {
                if (categoryIndex == selected) return;
                clearInboxSelection();
                categoryIndex = selected;
                inboxShown = 12;
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
        String selectedCategory = categoryIndex == 0 ? null
                : EventCategory.VALUES[categoryIndex - 1];
        List<TaskRecord> tasks = statusIndex == 4
                ? store.listPublishedTasks(selectedCategory)
                : selectedCategory == null ? store.listTasks()
                        : store.listTasksByCategory(selectedCategory);
        String query = normalizedQuery(inboxQuery);
        List<TaskRecord> visible = new ArrayList<>();
        for (TaskRecord task : tasks)
            if (matches(task) && (query.isEmpty() || matchesQuery(task, query))) visible.add(task);
        // The store hands the newest input back first; the toggle asks for the other direction.
        if (inboxOldestFirst) java.util.Collections.reverse(visible);
        matchingInboxIds = new ArrayList<>();
        for (TaskRecord task : visible) matchingInboxIds.add(task.id);
        selectedInboxIds.retainAll(matchingInboxIds);
        addInboxResultsHeading(visible.size());
        updateSelectionUi();
        if (visible.isEmpty()) {
            if (!query.isEmpty()) {
                empty(results, "没有匹配「" + inboxQuery.trim() + "」的收件。");
            } else {
                LinearLayout emptyCard = card();
                emptyCard.setGravity(Gravity.CENTER);
                emptyCard.setMinimumHeight(dp(168));
                TextView emptyCopy = text("这里空空\n终于没事了~", 17, false);
                emptyCopy.setGravity(Gravity.CENTER);
                emptyCard.addView(emptyCopy, new LinearLayout.LayoutParams(-1, -2));
                UiStyle.addSpaced(results, emptyCard, 3, 8);
            }
        }
        for (int i = 0; i < Math.min(inboxShown, visible.size()); i++)
            taskRow(results, visible.get(i), i);
        if (visible.size() > inboxShown) {
            Button more = button("继续浏览 · 还有 " + (visible.size() - inboxShown) + " 条",
                    false, () -> { inboxShown += 12; updateResults(); });
            UiStyle.addSpaced(results, more, 8, 0);
        }
    }

    private Button inboxSelectionAction(String label, Runnable action) {
        Button control = new Button(this);
        control.setText(label);
        control.setAllCaps(false);
        control.setSingleLine(true);
        control.setTextSize(13);
        control.setTextColor(UiStyle.colors(this).primary);
        control.setIncludeFontPadding(false);
        control.setMinWidth(0);
        control.setMinimumWidth(0);
        control.setMinHeight(0);
        control.setMinimumHeight(0);
        control.setPadding(dp(8), 0, dp(8), 0);
        UiStyle.glassPill(control);
        UiStyle.pressable(control);
        control.setOnClickListener(view -> action.run());
        return control;
    }

    private void addInboxResultsHeading(int count) {
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("共 " + count + (section == SCHEDULE ? " 条日程" : " 条收件"), 19, true);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        int height = Math.max(dp(40), (int) Math.ceil(title.getPaint().getFontSpacing()) + dp(8));
        heading.addView(title, new LinearLayout.LayoutParams(0, height, 1f));
        ViewGroup previous = (ViewGroup) selectionBar.getParent();
        if (previous != null) previous.removeView(selectionBar);
        for (Button control : new Button[]{selectAllButton, deleteSelectedButton}) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, height);
            params.setMargins(dp(6), 0, 0, 0);
            control.setLayoutParams(params);
        }
        heading.addView(selectionBar, new LinearLayout.LayoutParams(-2, height));
        // This height exists before selection too, so entering selection never moves the cards.
        UiStyle.addSpaced(results, heading, 16, 9);
    }

    /** User-driven filter/order changes animate cards, while polling remains still. */
    private void updateResultsWithEntrance() {
        updateResults();
        animateResultRows();
    }

    private void animateResultRows() {
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) return;
        int index = 0;
        for (InboxRow row : inboxRows.values()) {
            animateResultCard(row.card, index++);
        }
        if (inboxRows.isEmpty() && results != null) {
            for (int i = 0; i < results.getChildCount(); i++) {
                View item = results.getChildAt(i);
                if (item.isClickable()) animateResultCard(item, index++);
            }
        }
        // Empty-state cards enter too; keep the count and selection controls stationary.
        if (index == 0 && results != null && results.getChildCount() > 1)
            animateResultCard(results.getChildAt(1), 0);
    }

    private void animateResultCard(View card, int index) {
        card.animate().cancel();
        card.setAlpha(0f);
        card.setTranslationY(dp(7));
        card.animate().alpha(1f).translationY(0f)
                .setStartDelay(Math.min(index, 7) * 24L)
                .setDuration(180L)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private void schedule(TaskStore store) {
        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        filterChips = new LinearLayout(this);
        String[] labels = {"未来", "未定", "以前", "全部"};
        for (int i = 0; i < labels.length; i++) {
            final int tab = i;
            TextView chip = text(labels[i], 14, true);
            chip.setGravity(Gravity.CENTER);
            chip.setIncludeFontPadding(false);
            chip.setOnClickListener(view -> {
                if (scheduleTab == tab) return;
                scheduleTab = tab;
                schedulePage = 0;
                updateChipSelection(tab);
                updateResultsWithEntrance();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(40), 1);
            params.setMargins(0, 0, dp(5), 0);
            filterChips.addView(chip, params);
        }
        updateChipSelection(scheduleTab);
        bar.addView(filterChips, new LinearLayout.LayoutParams(0, dp(40), 1));
        ImageButton filters = new ImageButton(this);
        filters.setImageResource(R.drawable.ic_filter_alt);
        filters.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        filters.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        filters.setPadding(dp(10), dp(10), dp(10), dp(10));
        applyThemeControlSurface(filters, false, UiStyle.RADIUS_CHIP);
        filters.setContentDescription("筛选日程：日期、类型、写入状态和来源");
        filters.setOnClickListener(view -> showScheduleFilters());
        UiStyle.pressable(filters);
        bar.addView(filters, new LinearLayout.LayoutParams(dp(40), dp(40)));
        UiStyle.addSpaced(content, bar, 6, 6);
        scheduleControls = bar;
        buildSelectionBar();
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        renderScheduleResults(store);
    }

    private void renderScheduleResults(TaskStore store) {
        results.removeAllViews();
        if (scheduleSource == 1) {
            renderSystemSchedule(store);
            return;
        }
        long now = System.currentTimeMillis();
        LocalDate[] window = scheduleWindow(false);
        ScheduleQuery query = new ScheduleQuery(scheduleTab,
                scheduleCategory == 0 ? null : EventCategory.VALUES[scheduleCategory - 1],
                schedulePublication, scheduleQuery, window[0], window[1], now, ZoneId.systemDefault());
        inboxRows.clear();
        matchingInboxIds = store.queryScheduleIds(query);
        selectedScheduleIds.retainAll(matchingInboxIds);
        int total = matchingInboxIds.size();
        schedulePage = Math.min(schedulePage, Math.max(0, (total - 1) / ScheduleQuery.PAGE_SIZE));
        List<EventCandidate> visible = store.querySchedulePage(query, scheduleOldestFirst, schedulePage);
        addInboxResultsHeading(total);
        updateSelectionUi();
        if (visible.isEmpty()) {
            empty(results, "这里空空\n换个日期或筛选看看");
        }
        String previousDate = "";
        for (int i = 0; i < visible.size(); i++) {
            EventCandidate item = visible.get(i);
            String date = item.startAtMillis == null ? "未定" : item.allDay
                    ? AllDayDates.displayStart(item.startAtMillis)
                    : AllDayDates.localDate(item.startAtMillis, ZoneId.systemDefault());
            if (!date.equals(previousDate)) {
                scheduleDayHeading(date);
                previousDate = date;
            }
            candidateRow(results, item, i);
        }
        schedulePageControls(total);
    }

    private LocalDate[] scheduleWindow(boolean system) {
        com.donglan.chrona.data.ScheduleFilterState state =
                new com.donglan.chrona.data.ScheduleFilterState(scheduleRange, scheduleCategory,
                        schedulePublication, system ? 1 : 0, scheduleTab, scheduleDate, scheduleUntil);
        LocalDate[] window = state.window(LocalDate.now());
        if (system && window[0] != null && window[1].isAfter(window[0].plusYears(1)))
            window[1] = window[0].plusYears(1);
        return window;
    }

    private void scheduleDayHeading(String date) {
        String label = date;
        if (!"未定".equals(date)) {
            LocalDate day = LocalDate.parse(date);
            LocalDate today = LocalDate.now();
            label = (day.equals(today) || day.equals(today.plusDays(1))
                    ? timelineDayTitle(day, today) + " · " : "") + date;
        }
        TextView heading = text(label, 15, true);
        heading.setTextColor(UiStyle.colors(this).primary);
        UiStyle.addSpaced(results, heading, 14, 5);
    }

    private void schedulePageControls(int total) {
        if (total <= ScheduleQuery.PAGE_SIZE) return;
        int pages = (total + ScheduleQuery.PAGE_SIZE - 1) / ScheduleQuery.PAGE_SIZE;
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = scheduleAction("‹", () -> changeSchedulePage(-1));
        previous.setContentDescription("上一页日程");
        previous.setEnabled(schedulePage > 0);
        row.addView(previous, new LinearLayout.LayoutParams(dp(48), dp(42)));
        TextView label = text((schedulePage + 1) + " / " + pages, 13, false);
        label.setGravity(Gravity.CENTER);
        row.addView(label, new LinearLayout.LayoutParams(0, dp(42), 1));
        Button next = scheduleAction("›", () -> changeSchedulePage(1));
        next.setContentDescription("下一页日程");
        next.setEnabled(schedulePage + 1 < pages);
        row.addView(next, new LinearLayout.LayoutParams(dp(48), dp(42)));
        UiStyle.addSpaced(results, row, 12, 0);
    }

    private void changeSchedulePage(int delta) {
        schedulePage = Math.max(0, schedulePage + delta);
        updateResultsWithEntrance();
        scroll.scrollTo(0, Math.max(0, scheduleControls.getTop() - dp(8)));
    }

    private Button scheduleAction(String title, Runnable action) {
        Button control = inboxSelectionAction(title, action);
        control.setMinimumHeight(dp(40));
        control.setPadding(dp(10), 0, dp(10), 0);
        return control;
    }

    private void applyScheduleFilters() {
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
                        schedulePublication, scheduleSource, scheduleTab, scheduleDate, scheduleUntil);
        ScheduleFilterSheet.show(this, initial, this::pickScheduleDate, chosen -> {
            scheduleRange = chosen.range;
            scheduleCategory = chosen.category;
            schedulePublication = chosen.publication;
            scheduleSource = chosen.source;
            scheduleTab = chosen.tab;
            scheduleDate = chosen.date;
            scheduleUntil = chosen.until;
            if (scheduleSource == 1 && !new CalendarStore(this).hasReadPermission()) {
                calendarPermissionForSchedule = true;
                requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR},
                        HOME_CALENDAR_PERMISSION_REQUEST);
            }
            applyScheduleFilters();
        });
    }

    private void pickScheduleDate(String title, LocalDate initial, java.util.function.Consumer<LocalDate> chosen) {
        android.app.Dialog dialog = new android.app.Dialog(this);
        dialog.setCanceledOnTouchOutside(true);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(16), dp(16), dp(16));
        UiStyle.glass(panel);
        TextView heading = text(title, 18, true);
        heading.setGravity(Gravity.CENTER);
        panel.addView(heading);
        GlassDateTimePickerView picker = new GlassDateTimePickerView(this, initial.atStartOfDay());
        TextView value = text(initial.toString(), 14, false);
        value.setGravity(Gravity.CENTER);
        picker.onChanged(() -> value.setText(picker.value().toLocalDate().toString()));
        UiStyle.addSpaced(panel, value, 8, 4);
        android.graphics.Rect visible = new android.graphics.Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        int height = Math.max(dp(80), Math.min(dp(210), visible.height() - dp(180)));
        panel.addView(picker, new LinearLayout.LayoutParams(-1, height));
        LinearLayout actions = new LinearLayout(this);
        Button cancel = scheduleAction("取消", dialog::dismiss);
        Button select = scheduleAction("确定", () -> {
            picker.finishSelection();
            LocalDate date = picker.value().toLocalDate();
            dialog.dismiss();
            refresh.post(() -> chosen.accept(date));
        });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1));
        LinearLayout.LayoutParams right = new LinearLayout.LayoutParams(0, dp(44), 1);
        right.setMargins(dp(6), 0, 0, 0);
        actions.addView(select, right);
        UiStyle.addSpaced(panel, actions, 8, 0);
        UiStyle.showFloatingDialog(dialog, panel);
    }

    private void renderSystemSchedule(TaskStore store) {
        inboxRows.clear(); matchingInboxIds = new ArrayList<>(); selectedScheduleIds.clear();
        if (!new CalendarStore(this).hasReadPermission()) {
            addInboxResultsHeading(0);
            updateSelectionUi();
            empty(results, "需要读取日历权限");
            return;
        }
        LocalDate[] window = scheduleWindow(true);
        ZoneId zone = ZoneId.systemDefault();
        long start = window[0].atStartOfDay(zone).toInstant().toEpochMilli();
        long end = window[1].atStartOfDay(zone).toInstant().toEpochMilli();
        long now = System.currentTimeMillis();
        List<CalendarOccurrence> visible = new ArrayList<>();
        Set<Long> linked = new HashSet<>();
        for (long id : store.linkedCalendarIds()) linked.add(id);
        for (CalendarOccurrence event : CalendarOccurrence.unlinked(calendarEventsForWindow(window), linked)) {
            boolean future = event.displayEnd(zone) > now;
            if (event.displayStart(zone) >= end || event.displayEnd(zone) <= start
                    || scheduleTab == 1 || (scheduleTab == 0 && !future) || (scheduleTab == 2 && future)
                    || schedulePublication == 1 || (scheduleCategory != 0 && scheduleCategory != 1)) continue;
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
        if (total == 0) empty(results, "这里空空\n换个日期或筛选看看");
        String day = "";
        for (int i = schedulePage * ScheduleQuery.PAGE_SIZE;
                i < Math.min(total, (schedulePage + 1) * ScheduleQuery.PAGE_SIZE); i++) {
            CalendarOccurrence event = visible.get(i);
            String date = AllDayDates.localDate(event.displayStart(zone), zone);
            if (!day.equals(date)) { scheduleDayHeading(date); day = date; }
            LinearLayout item = card();
            item.addView(text(event.title, 17, true));
            UiStyle.addSpaced(item, text(homeEntryWhen(new HomeTimelineEntry(event))
                    + (event.location.isEmpty() ? "" : " · " + event.location), 13, false), 6, 0);
            item.setOnClickListener(view -> openHomeEntry(new HomeTimelineEntry(event)));
            UiStyle.pressable(item);
            UiStyle.addSpaced(results, item, 4, 7);
        }
        message(results, "系统日程只读；每次最多读取 1000 项。按月份或日期缩小范围。");
        schedulePageControls(total);
    }

    private void updateResults() {
        if (results == null) return;
        int previous = scroll.getScrollY();
        try (TaskStore store = new TaskStore(this)) {
            if (section == INBOX) renderInboxResults(store);
            else if (section == SCHEDULE) renderScheduleResults(store);
        } catch (Exception exception) {
            results.removeAllViews();
            message(results, "暂时无法读取日程：" + exception.getMessage());
        }
        scroll.scrollTo(0, previous);
        rememberRenderedPage();
    }

    private void updateChipSelection(int selected) {
        if (filterChips == null) return;
        for (int i = 0; i < filterChips.getChildCount(); i++) {
            View cell = filterChips.getChildAt(i);
            // A counted chip is wrapped so its badge can sit on the corner; the pill is inside.
            TextView chip = (TextView) (cell instanceof FrameLayout
                    ? ((FrameLayout) cell).getChildAt(0) : cell);
            chip.setTextColor(i == selected ? UiStyle.colors(this).onPrimaryContainer
                    : UiStyle.colors(this).primary);
            applyThemeControlSurface(chip, i == selected, UiStyle.RADIUS_CHIP);
        }
    }

    private void candidateRow(EventCandidate item, int index) {
        candidateRow(content, item, index);
    }

    private void candidateRow(LinearLayout parent, EventCandidate item, int index) {
        LinearLayout card = card();
        TextView title = text(item.title, 17, true);
        title.setMaxLines(2);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        boolean selectable = section == SCHEDULE && parent == results;
        TextView marker = text("○", 22, false);
        marker.setGravity(Gravity.CENTER);
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        if (selectable) titleRow.addView(marker, new LinearLayout.LayoutParams(dp(34), dp(34)));
        card.addView(titleRow);
        String when = item.startAtMillis == null ? "时间待补全" : formatWhen(item);
        TextView meta = text(EventCategory.label(item.category) + " · " + when
                + (item.calendarEventId == null ? " · 待确认" : " · 已写入"), 13, false);
        UiStyle.addSpaced(card, meta, 6, 0);
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
                } else openTask(item.taskId);
            });
            applyInboxRowSelection(item.id);
        } else card.setOnClickListener(view -> openTask(item.taskId));
        UiStyle.pressable(card);
        UiStyle.addSpaced(parent, card, 4, 7);
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
                : DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(new Date(task.createdAtMillis))), 13, false);
        if (waiting) {
            // A quiet pulse so "解析中" reads as alive rather than as a stuck row.
            UiStyle.pulse(meta);
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
        UiStyle.addSpaced(parent, card, 4, 7);
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
        if ((section == INBOX || section == SCHEDULE) && !currentSelection().isEmpty())
            clearCurrentSelection();
        else super.onBackPressed();
    }

    private void updateSelectionBack() {
        if (android.os.Build.VERSION.SDK_INT < 33 || previewRender) return;
        boolean active = (section == INBOX || section == SCHEDULE) && !currentSelection().isEmpty();
        if (active == selectionBackRegistered) return;
        if (active) {
            if (selectionBack == null) selectionBack = this::clearCurrentSelection;
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
        boolean wasVisible = selectionBar.getVisibility() == View.VISIBLE;
        selectionBar.animate().cancel();
        if (active && !wasVisible && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            selectionBar.setAlpha(0f);
            selectionBar.setTranslationY(-dp(5));
            selectionBar.setVisibility(View.VISIBLE);
            selectionBar.animate().alpha(1f).translationY(0f).setDuration(170).start();
        } else if (!active && wasVisible
                && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            LinearLayout outgoingBar = selectionBar;
            outgoingBar.animate().alpha(0f).translationY(-dp(4)).setDuration(120)
                    .withEndAction(() -> {
                        outgoingBar.setVisibility(View.GONE);
                        outgoingBar.setAlpha(1f);
                        outgoingBar.setTranslationY(0f);
                    }).start();
        } else {
            selectionBar.setAlpha(1f);
            selectionBar.setTranslationY(0f);
            selectionBar.setVisibility(active ? View.VISIBLE : View.GONE);
        }
        selectionCount.setText(selected.size() + "/" + matchingInboxIds.size());
        selectionCount.setContentDescription("已选 " + selected.size() + " 条，共 " + matchingInboxIds.size() + " 条");
        boolean all = !matchingInboxIds.isEmpty()
                && selected.containsAll(matchingInboxIds);
        selectAllButton.setText(all ? "取消" : "全选");
        selectAllButton.setContentDescription(all ? "取消全选" : "选中本筛选的全部事项");
        deleteSelectedButton.setText("删除");
        deleteSelectedButton.setContentDescription("删除已选 " + selected.size() + " 项");
        for (Map.Entry<Long, InboxRow> entry : inboxRows.entrySet())
            applyInboxRowSelection(entry.getKey());
    }

    private void applyInboxRowSelection(long taskId) {
        InboxRow row = inboxRows.get(taskId);
        if (row == null) return;
        boolean selected = currentSelection().contains(taskId);
        row.marker.setVisibility(currentSelection().isEmpty() ? View.GONE : View.VISIBLE);
        row.marker.setText(selected ? "✓" : "○");
        row.marker.setTextColor(selected ? UiStyle.colors(this).primary
                : UiStyle.colors(this).muted);
        // Keep the acrylic card background; selection is already shown by the marker and toolbar.
        UiStyle.pressable(row.card);
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
        UiStyle.confirmDialog(this, "删除所选收件？",
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
        UiStyle.confirmDialog(this, "删除所选日程？", "将删除 " + chosen.size()
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
            try (TaskStore store = new TaskStore(this)) {
                CalendarStore calendar = new CalendarStore(this);
                Set<Long> affectedTasks = new LinkedHashSet<>();
                for (EventCandidate item : chosen) {
                    try {
                        if (item.calendarEventId != null) calendar.deleteEvent(item.calendarEventId);
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
                        for (EventCandidate candidate : candidates) {
                            if (candidate.calendarEventId != null)
                                calendar.deleteEvent(candidate.calendarEventId);
                        }
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
        if (requestCode == HOME_CALENDAR_PERMISSION_REQUEST) {
            boolean granted = grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (calendarPermissionForSchedule) {
                calendarPermissionForSchedule = false;
                if (!granted) scheduleSource = 0;
                applyScheduleFilters();
                if (!granted) Feedback.show(this, "未获得读取日历权限");
                return;
            }
            HomeTimelinePreferences.setIncludesSystemCalendar(this, granted);
            calendarGeneration++;
            lastCalendarFetch = 0;
            refreshHomeContent();
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

    private boolean matches(TaskRecord task) {
        if (statusIndex == 0 || statusIndex == 4) return true;
        if (statusIndex == 1) return TaskRecord.NEEDS_REVIEW.equals(task.status);
        if (statusIndex == 2) return TaskRecord.QUEUED.equals(task.status)
                || TaskRecord.PROCESSING.equals(task.status);
        return TaskRecord.FAILED.equals(task.status);
    }

    private String statusText(String status) {
        if (TaskRecord.QUEUED.equals(status)) return "排队中";
        if (TaskRecord.PROCESSING.equals(status)) return "解析中";
        if (TaskRecord.NEEDS_REVIEW.equals(status)) return "待确认";
        if (TaskRecord.READY.equals(status)) return "已写入日历";
        return "处理失败";
    }

    private static String elapsedText(long millis) {
        long seconds = Math.max(0L, millis) / 1000L;
        if (seconds < 60) return seconds + " 秒";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + " 分 " + (seconds % 60) + " 秒";
        return (minutes / 60) + " 小时 " + (minutes % 60) + " 分";
    }

    private String[] categoryOptions() {
        String[] result = new String[EventCategory.LABELS.length + 1];
        result[0] = "全部类型";
        System.arraycopy(EventCategory.LABELS, 0, result, 1, EventCategory.LABELS.length);
        return result;
    }

    private void drawNavigation() {
        if (previewRender) return;
        navigation.removeAllViews();
        String[] labels = {"首页", "收件箱", "日程"};
        int[] icons = {R.drawable.ic_nav_home, R.drawable.ic_nav_inbox,
                R.drawable.ic_nav_schedule};
        for (int i = 0; i < labels.length; i++) {
            final int destination = i;
            TextView item = text(labels[i], 13, true);
            item.setGravity(Gravity.CENTER);
            item.setCompoundDrawablesWithIntrinsicBounds(0, icons[i], 0, 0);
            item.setCompoundDrawablePadding(dp(4));
            item.setPadding(0, dp(wide ? 8 : 2), 0, dp(wide ? 8 : 2));
            item.setTextColor(section == i ? UiStyle.colors(this).onPrimaryContainer
                    : UiStyle.colors(this).muted);
            item.setCompoundDrawableTintList(ColorStateList.valueOf(section == i
                    ? UiStyle.colors(this).onPrimaryContainer : UiStyle.colors(this).muted));
            item.setContentDescription(labels[i] + (section == i ? "，当前页面" : ""));
            if (wide) UiStyle.pill(item, section == i);
            else {
                applyThemeControlSurface(item, section == i, UiStyle.RADIUS_PANEL);
                item.setElevation(dp(section == i ? 3 : 1));
            }
            item.setOnClickListener(view -> switchTo(destination));
            LinearLayout.LayoutParams params = wide
                    ? new LinearLayout.LayoutParams(-1, dp(78))
                    : new LinearLayout.LayoutParams(0, -1, 1);
            params.setMargins(dp(wide ? 3 : 4), dp(wide ? 5 : 0),
                    dp(wide ? 3 : 4), 0);
            navigation.addView(i == INBOX && attentionCount() > 0
                    ? withInboxBadge(item, destination) : (View) item, params);
        }
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

    private void switchTo(int destination) {
        if (destination == section || destination < HOME || destination > SCHEDULE
                || pageTransitionRunning || dragTargetLoaded) return;
        sectionPagerListener.onStart();
        dragDirection = destination > section ? 1 : -1;
        adjacentPage = buildAdjacentSection(destination);
        float width = sectionPageWidth();
        adjacentPage.scroll.setTranslationX(dragDirection > 0 ? width : -width);
        pager.addView(adjacentPage.scroll, 1, new FrameLayout.LayoutParams(-1, -1));
        dragTargetLoaded = true;
        finishSectionDrag(true, width);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(16), dp(17), dp(16));
        UiStyle.card(card);
        return card;
    }

    private LinearLayout chipRow(String[] labels, int selected, java.util.function.IntConsumer action) {
        return chipRow(labels, selected, action, null, null);
    }

    private LinearLayout chipRow(String[] labels, int selected,
            java.util.function.IntConsumer action, View trailingAction) {
        return chipRow(labels, selected, action, trailingAction, null);
    }

    /**
     * @param counts optional per-chip counts, appended to the label in a smaller run so a filter
     *               can say how much is waiting behind it.
     */
    private LinearLayout chipRow(String[] labels, int selected,
            java.util.function.IntConsumer action, View trailingAction, int[] counts) {
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipChildren(true);
        strip.setClipToPadding(true);
        strip.setFillViewport(false);
        strips.add(strip);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        // Keep the final chip fully inside the scroll viewport at maximum scroll. The filter
        // button is a sibling in the bar below, so it never covers the scrolling chip content.
        if (trailingAction != null) row.setPadding(0, 0, dp(28), 0);
        int horizontalPadding = trailingAction == null ? 18 : 12;
        List<TextView> chips = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = text(labels[i], 14, true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(48));
            chip.setPadding(dp(horizontalPadding), 0, dp(horizontalPadding), 0);
            chip.setTextColor(i == selected ? UiStyle.colors(this).onPrimaryContainer
                    : UiStyle.colors(this).primary);
            applyThemeControlSurface(chip, i == selected, UiStyle.RADIUS_CHIP);
            chip.setOnClickListener(view -> action.accept(index));
            chips.add(chip);
        }
        // Measure the widest label so every chip comes out the same width instead of hugging its
        // own text; the count sits in a corner badge, so it never changes the pill's size.
        if (!chips.isEmpty()) {
            float widest = 0;
            for (String label : labels) widest = Math.max(widest, chips.get(0).getPaint().measureText(label));
            int chipWidth = Math.round(widest) + dp(horizontalPadding * 2);
            for (TextView chip : chips) chip.setMinWidth(chipWidth);
        }
        for (int i = 0; i < chips.size(); i++) {
            TextView chip = chips.get(i);
            int count = counts != null && i < counts.length ? counts[i] : 0;
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, 0, dp(8), 0);
            if (count > 0) {
                FrameLayout cell = new FrameLayout(this);
                cell.addView(chip, new FrameLayout.LayoutParams(-1, -1));
                TextView badge = countBadge(count);
                FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(-2, dp(18),
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                badgeParams.setMargins(0, dp(6), 0, 0);
                cell.addView(badge, badgeParams);
                // Sits on the label's top-right corner, inside the pill: pinned to the pill's own
                // corner it was clipped by the chip row and the scrolling strip around it.
                badge.setTranslationX(chip.getPaint().measureText(labels[i]) / 2f - dp(3));
                row.addView(cell, params);
            } else {
                row.addView(chip, params);
            }
        }
        strip.addView(row);
        if (trailingAction == null) {
            UiStyle.addSpaced(content, strip, 10, 12);
        } else {
            LinearLayout bar = new LinearLayout(this);
            bar.setOrientation(LinearLayout.HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            bar.setClipChildren(true);
            bar.setClipToPadding(true);
            bar.addView(strip, new LinearLayout.LayoutParams(0, -2, 1));
            LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(dp(48), dp(48));
            actionParams.setMargins(dp(8), 0, 0, 0);
            bar.addView(trailingAction, actionParams);
            UiStyle.addSpaced(content, bar, 8, 10);
        }
        strip.post(() -> {
            if (selected >= row.getChildCount()) return;
            View selectedChip = row.getChildAt(selected);            int viewportWidth = strip.getWidth() - strip.getPaddingLeft()
                    - strip.getPaddingRight();
            int currentX = strip.getScrollX();
            int visibleRight = currentX + viewportWidth;
            int targetX = currentX;
            if (selectedChip.getLeft() < currentX) {
                targetX = selectedChip.getLeft();
            } else if (selectedChip.getRight() > visibleRight) {
                targetX = selectedChip.getRight() - viewportWidth;
            }
            if (targetX != currentX) strip.scrollTo(Math.max(0, targetX), 0);
        });
        return row;
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

    private String formatWhen(EventCandidate item) {
        if (item.allDay) {
            String start = AllDayDates.displayStart(item.startAtMillis);
            String end = AllDayDates.displayEnd(item.endAtMillis);
            return start.equals(end) ? start + " · 全天"
                    : start + " 至 " + end + " · 全天";
        }
        Date date = new Date(item.startAtMillis);
        return DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                DateFormat.SHORT).format(date);
    }

    private boolean isUpcoming(EventCandidate item, long now) {
        if (item.startAtMillis == null || item.endAtMillis == null) return false;
        if (item.allDay) {
            return !LocalDate.parse(AllDayDates.displayEnd(item.endAtMillis))
                    .isBefore(LocalDate.now());
        }
        return item.endAtMillis > now;
    }

    private void timelineDay(String title, LocalDate date, List<HomeTimelineEntry> entries) {
        if (entries.isEmpty()) return;
        FrameLayout dayGroup = new FrameLayout(this);
        LinearLayout dayContent = new LinearLayout(this);
        dayContent.setOrientation(LinearLayout.VERTICAL);
        View dateToFirstEntry = new View(this);
        dateToFirstEntry.setBackgroundColor(UiStyle.colors(this).outline);
        dayGroup.addView(dateToFirstEntry, new FrameLayout.LayoutParams(dp(2), 0,
                Gravity.TOP | Gravity.LEFT));
        dayGroup.addView(dayContent, new FrameLayout.LayoutParams(-1, -2));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        View markerSpacer = new View(this);
        heading.addView(markerSpacer, new LinearLayout.LayoutParams(dp(49), 1));
        FrameLayout markerSlot = new FrameLayout(this);
        View marker = new View(this);
        GradientDrawable markerShape = new GradientDrawable();
        markerShape.setShape(GradientDrawable.OVAL);
        markerShape.setColor(UiStyle.colors(this).primary);
        marker.setBackground(markerShape);
        markerSlot.addView(marker, new FrameLayout.LayoutParams(dp(14), dp(14), Gravity.CENTER));
        heading.addView(markerSlot, new LinearLayout.LayoutParams(dp(18), dp(18)));
        TextView label = text(title, 17, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.setMargins(dp(6), 0, dp(8), 0);
        heading.addView(label, labelParams);
        TextView dateLabel = text(date.getMonthValue() + "月" + date.getDayOfMonth() + "日", 15, false);
        heading.addView(dateLabel);
        View divider = new View(this);
        divider.setBackgroundColor(UiStyle.colors(this).outline);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(0, dp(1), 1);
        dividerParams.setMargins(dp(10), 0, 0, 0);
        heading.addView(divider, dividerParams);
        UiStyle.addSpaced(dayContent, heading, 7, 2);

        LinearLayout dayRows = new LinearLayout(this);
        dayRows.setOrientation(LinearLayout.VERTICAL);
        View firstEventMarker = null;
        for (int i = 0; i < entries.size(); i++) {
            HomeTimelineEntry entry = entries.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.TOP);
            TextView time = text(timelineTime(entry), 12, true);
            time.setGravity(Gravity.TOP | Gravity.END);
            time.setMinWidth(dp(48));
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(dp(48), -2);
            timeParams.setMargins(0, dp(17), dp(4), 0);
            row.addView(time, timeParams);

            FrameLayout rail = new FrameLayout(this);
            LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(12), -1);
            railParams.setMargins(0, 0, dp(4), 0);
            row.addView(rail, railParams);
            if (entries.size() > 1) {
                View line = new View(this);
                line.setBackgroundColor(UiStyle.colors(this).outline);
                FrameLayout.LayoutParams lineParams = new FrameLayout.LayoutParams(dp(2), -1,
                        Gravity.TOP | Gravity.CENTER_HORIZONTAL);
                int nodeCenter = 25;
                if (i == 0) {
                    lineParams.topMargin = dp(nodeCenter);
                } else if (i == entries.size() - 1) {
                    lineParams.height = dp(nodeCenter);
                }
                rail.addView(line, lineParams);
            }
            View dot = new View(this);
            GradientDrawable dotShape = new GradientDrawable();
            dotShape.setShape(GradientDrawable.OVAL);
            dotShape.setColor(UiStyle.colors(this).primary);
            dot.setBackground(dotShape);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(8), dp(8),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            dotParams.topMargin = dp(21);
            rail.addView(dot, dotParams);
            if (i == 0) firstEventMarker = dot;

            LinearLayout itemCard = card();
            itemCard.setPadding(dp(14), dp(12), dp(14), dp(12));
            String headline = entry.title();
            TextView headlineView = text(headline, 15, true);
            headlineView.setMaxLines(2);
            headlineView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            itemCard.addView(headlineView);
            String location = entry.candidate != null ? entry.candidate.location : entry.systemEvent.location;
            String metadata = entry.candidate != null ? EventCategory.label(entry.candidate.category)
                    : entry.systemEvent.calendarName;
            if (location != null && !location.trim().isEmpty())
                metadata += (metadata.isEmpty() ? "" : " · ") + location;
            if (!metadata.isEmpty()) UiStyle.addSpaced(itemCard, text(metadata, 12, false), 4, 0);
            itemCard.setOnClickListener(view -> openHomeEntry(entry));
            UiStyle.pressable(itemCard);
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(0, -2, 1);
            cardParams.setMargins(0, dp(4), 0, dp(4));
            row.addView(itemCard, cardParams);
            dayRows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        UiStyle.addSpaced(dayContent, dayRows, 2, 7);
        UiStyle.addSpaced(content, dayGroup, 0, 0);
        if (firstEventMarker != null)
            connectTimelineMarkers(dayGroup, marker, firstEventMarker, dateToFirstEntry);
    }

    private void connectTimelineMarkers(FrameLayout container, View start, View end, View line) {
        container.getViewTreeObserver().addOnPreDrawListener(
                new android.view.ViewTreeObserver.OnPreDrawListener() {
                    @Override public boolean onPreDraw() {
                        if (container.getWidth() == 0 || start.getWidth() == 0 || end.getWidth() == 0)
                            return true;
                        int[] containerLocation = new int[2];
                        int[] startLocation = new int[2];
                        int[] endLocation = new int[2];
                        container.getLocationOnScreen(containerLocation);
                        start.getLocationOnScreen(startLocation);
                        end.getLocationOnScreen(endLocation);
                        int left = startLocation[0] - containerLocation[0]
                                + start.getWidth() / 2 - dp(1);
                        int top = startLocation[1] - containerLocation[1]
                                + start.getHeight() / 2;
                        int bottom = endLocation[1] - containerLocation[1]
                                + end.getHeight() / 2;
                        int height = Math.max(0, bottom - top);
                        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) line.getLayoutParams();
                        if (params.leftMargin != left || params.topMargin != top || params.height != height) {
                            params.leftMargin = left;
                            params.topMargin = top;
                            params.height = height;
                            line.setLayoutParams(params);
                            return false;
                        }
                        container.getViewTreeObserver().removeOnPreDrawListener(this);
                        return true;
                    }
                });
    }

    private String timelineTime(HomeTimelineEntry entry) {
        if (entry.allDay()) return "全天";
        return new SimpleDateFormat("HH:mm", Locale.CHINA).format(
                new Date(entry.timestampMillis));
    }

    private void sectionHeading(String title, String action, int destination) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(dp(2), 0, dp(2), 0);
        TextView heading = text(title, 17, true);
        row.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView link = text(action + "  →", 13, true);
        link.setTextColor(UiStyle.colors(this).primary);
        link.setMinHeight(dp(44));
        link.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(link);
        row.setOnClickListener(view -> switchTo(destination));
        UiStyle.pressable(row);
        UiStyle.addSpaced(content, row, 17, 9);
    }

    private TextView text(String content, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(content);
        view.setTextSize(size);
        if (bold) UiStyle.title(view); else UiStyle.muted(view);
        return view;
    }

    private Button button(String label, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        UiStyle.button(button, primary);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private void sectionTitle(String title) {
        sectionTitle(content, title);
    }
    private void sectionTitle(LinearLayout parent, String title) {
        TextView label = text(title, 19, true);
        UiStyle.addSpaced(parent, label, 16, 9);
    }
    private void message(LinearLayout parent, String text) {
        UiStyle.addSpaced(parent, text(text, 14, false), 0, 0);
    }
    private void empty(String text) {
        empty(content, text);
    }
    private void empty(LinearLayout parent, String text) {
        LinearLayout box = card();
        box.addView(text(text, 15, false));
        UiStyle.addSpaced(parent, box, 3, 8);
    }
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
