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
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.calendar.AllDayDates;
import com.donglan.chrona.calendar.CalendarStore;
import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Three clear destinations: overview, incoming inputs, and extracted schedule. */
public final class DashboardActivity extends Activity {
    private static final int CALENDAR_PERMISSION_REQUEST = 12;
    private static final int MOBILE_DOCK_HEIGHT_DP = 64;
    private static final int MOBILE_BOTTOM_AREA_HEIGHT_DP = 88;
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

        HomeTimelineEntry(EventCandidate candidate) {
            this.timestampMillis = candidate.startAtMillis;
            this.candidate = candidate;
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
    private int section = HOME;
    private int categoryIndex, statusIndex;
    private int scheduleTab;
    private int inboxShown = 12, scheduleShown = 12;
    private LinearLayout content;
    private LinearLayout navigation;
    private LinearLayout results;
    private LinearLayout filterChips;
    private ImageButton categoryChip;
    private LinearLayout selectionBar;
    private TextView selectionCount;
    private Button selectAllButton;
    private Button deleteSelectedButton;
    private final Set<Long> selectedInboxIds = new LinkedHashSet<>();
    private final Map<Long, InboxRow> inboxRows = new HashMap<>();
    private List<Long> matchingInboxIds = new ArrayList<>();
    private ScrollView scroll;
    private GlassBackdropView backdrop;
    private boolean wide;
    /** Cards stagger in only for a screen the user just opened, never for a rebuild. */
    private boolean animateEntrances;
    private String snapshot = "";
    private int restoredScrollY;
    private final List<ElapsedLabel> elapsedLabels = new ArrayList<>();
    private final Handler refresh = new Handler(Looper.getMainLooper());
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
            inboxShown = state.getInt("inbox_shown", 12);
            scheduleShown = state.getInt("schedule_shown", 12);
            long[] selected = state.getLongArray("selected_inbox_ids");
            if (selected != null) for (long id : selected) selectedInboxIds.add(id);
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
        state.putInt("inbox_shown", inboxShown);
        state.putInt("schedule_shown", scheduleShown);
        state.putInt("scroll_y", scroll.getScrollY());
        long[] selected = new long[selectedInboxIds.size()];
        int selectedIndex = 0;
        for (long id : selectedInboxIds) selected[selectedIndex++] = id;
        state.putLongArray("selected_inbox_ids", selected);
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        section = intent.getIntExtra(EXTRA_SECTION, section);
        render();
        scroll.scrollTo(0, 0);
    }

    @Override protected void onResume() {
        super.onResume();
        int recovered = ProcessingJobService.reconcile(this);
        if (recovered > 0) Feedback.showLong(this, recovered + " 条解析已中断，请在收件箱重试");
        int oldScroll = restoredScrollY != 0 ? restoredScrollY : scroll.getScrollY();
        restoredScrollY = 0;
        if ((section == INBOX || section == SCHEDULE) && results != null) updateResults();
        else render();
        scroll.post(() -> scroll.scrollTo(0, oldScroll));
        snapshot = dataSnapshot();
        refresh.postDelayed(poll, 5000L);
        refresh.postDelayed(elapsedTicker, 1000L);
    }

    @Override protected void onPause() {
        refresh.removeCallbacks(poll);
        refresh.removeCallbacks(elapsedTicker);
        backdrop.stop();
        super.onPause();
    }

    private void pollChanges() {
        String current = dataSnapshot();
        if (!current.equals(snapshot)) {
            int scrollY = scroll.getScrollY();
            if ((section == INBOX || section == SCHEDULE) && results != null) updateResults();
            else render();
            scroll.post(() -> scroll.scrollTo(0, scrollY));
            snapshot = current;
        }
        refresh.postDelayed(poll, 5000L);
    }

    private String dataSnapshot() {
        try (TaskStore store = new TaskStore(this)) {
            StringBuilder value = new StringBuilder();
            for (TaskRecord task : store.listTasks()) {
                value.append(task.id).append(':').append(task.status).append(';');
            }
            // While something is parsing the snapshot also moves every five seconds, so a row's
            // "已 N" keeps counting without a second timer of its own.
            value.append('/').append(store.listCandidates().size());
            return value.toString();
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
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int horizontal = wide ? Math.max(32,
                (getResources().getConfiguration().screenWidthDp - 90 - 720) / 2) : 20;
        content.setPadding(dp(horizontal), dp(wide ? 18 : 14), dp(horizontal),
                dp(wide ? 42 : MOBILE_BOTTOM_AREA_HEIGHT_DP + 24));
        scroll.addView(content);
        navigation = new LinearLayout(this);
        navigation.setOrientation(wide ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(wide ? 8 : 8), dp(wide ? 24 : 6),
                dp(wide ? 8 : 8), dp(wide ? 10 : 6));
        if (wide) UiStyle.glass(navigation);
        FrameLayout mobileBottomArea = null;
        if (wide) {
            shell.addView(navigation, new LinearLayout.LayoutParams(dp(90), -1));
            shell.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        } else {
            shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            mobileBottomArea = new FrameLayout(this);
            mobileBottomArea.setClipChildren(false);
            mobileBottomArea.setClipToPadding(false);
            UiStyle.glass(navigation);
            navigation.setElevation(dp(8));
            FrameLayout.LayoutParams dockParams = new FrameLayout.LayoutParams(-1,
                    dp(MOBILE_DOCK_HEIGHT_DP), Gravity.BOTTOM | Gravity.START);
            dockParams.setMargins(dp(14), 0, dp(94), dp(8));
            mobileBottomArea.addView(navigation, dockParams);
            LinearLayout record = mobileRecordButton();
            FrameLayout.LayoutParams recordParams = new FrameLayout.LayoutParams(dp(52), dp(52),
                    Gravity.BOTTOM | Gravity.END);
            recordParams.setMargins(0, 0, dp(14), dp(14));
            mobileBottomArea.addView(record, recordParams);
        }
        stage.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        if (wide) {
            Button capture = button("＋ 记录", true, () ->
                    startActivity(new Intent(this, MainActivity.class)));
            capture.setElevation(dp(12));
            FrameLayout.LayoutParams floating = new FrameLayout.LayoutParams(-2, dp(54),
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
        filterChips = null;
        categoryChip = null;
        elapsedLabels.clear();
        content.removeAllViews();
        addHeader();
        try (TaskStore store = new TaskStore(this)) {
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
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        ImageButton settings = new ImageButton(this);
        settings.setImageResource(R.drawable.ic_settings);
        settings.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        settings.setScaleType(android.widget.ImageView.ScaleType.CENTER_INSIDE);
        settings.setPadding(dp(13), dp(13), dp(13), dp(13));
        settings.setContentDescription("打开设置");
        UiStyle.pill(settings, false);
        settings.setOnClickListener(view ->
                startActivity(new Intent(this, SettingsHubActivity.class)));
        row.addView(settings, new LinearLayout.LayoutParams(dp(50), dp(50)));
        UiStyle.addSpaced(content, row, 0, section == HOME ? 10 : 14);
    }

    private void home(TaskStore store) {
        List<TaskRecord> tasks = store.listTasks();
        int review = 0;
        for (TaskRecord task : tasks) {
            if (TaskRecord.NEEDS_REVIEW.equals(task.status)) review++;
        }
        long now = System.currentTimeMillis();
        List<EventCandidate> candidates = store.listCandidates();
        showUpcomingCarousel(candidates, now);

        if (review > 0) inboxShortcut(review);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate endDate = today.plusMonths(1);
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
        all.sort(java.util.Comparator.comparingLong(entry -> entry.timestampMillis));
        int limit = HomeTimelinePreferences.getItemLimit(this);
        if (all.size() > limit) all = new ArrayList<>(all.subList(0, limit));
        Map<LocalDate, List<HomeTimelineEntry>> byDate = new java.util.TreeMap<>();
        for (HomeTimelineEntry entry : all) {
            LocalDate date = timelineDate(entry, today, zone);
            byDate.computeIfAbsent(date, ignored -> new ArrayList<>()).add(entry);
        }
        for (Map.Entry<LocalDate, List<HomeTimelineEntry>> day : byDate.entrySet())
            timelineDay(timelineDayTitle(day.getKey(), today), day.getKey(), day.getValue());
    }

    private void showUpcomingCarousel(List<EventCandidate> candidates, long now) {
        TextView heading = text("下一件事", 15, true);
        UiStyle.addSpaced(content, heading, 0, 6);
        List<EventCandidate> upcoming = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (EventCandidate candidate : candidates) {
            if (isUpcoming(candidate, now) && seen.add(candidate.id)) upcoming.add(candidate);
        }
        upcoming.sort(java.util.Comparator.comparingLong(item -> item.startAtMillis));
        if (upcoming.size() > 5) upcoming = new ArrayList<>(upcoming.subList(0, 5));
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setFillViewport(false);
        strip.setClipToPadding(false);
        strip.setPadding(0, 0, dp(12), 0);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        if (upcoming.isEmpty()) {
            LinearLayout empty = card();
            empty.setPadding(dp(14), dp(12), dp(14), dp(12));
            empty.addView(text("还没有即将到来的日程", 14, false));
            cards.addView(empty, new LinearLayout.LayoutParams(dp(240), -2));
        } else {
            for (EventCandidate candidate : upcoming) {
                LinearLayout item = card();
                item.setPadding(dp(14), dp(12), dp(14), dp(12));
                TextView title = text(candidate.title, 15, true);
                title.setMaxLines(2);
                title.setEllipsize(android.text.TextUtils.TruncateAt.END);
                item.addView(title);
                UiStyle.addSpaced(item, text(formatWhen(candidate), 12, false), 7, 0);
                item.setOnClickListener(view -> openTask(candidate.taskId));
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
        LocalDate date = entry.candidate.allDay
                ? LocalDate.parse(AllDayDates.displayStart(entry.candidate.startAtMillis))
                : Instant.ofEpochMilli(entry.timestampMillis).atZone(zone).toLocalDate();
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
                    updateResults();
                }, inboxCategoryButton());
        selectionBar = new LinearLayout(this);
        selectionBar.setOrientation(LinearLayout.VERTICAL);
        selectionBar.setPadding(dp(14), dp(10), dp(14), dp(10));
        UiStyle.glass(selectionBar);
        LinearLayout selectionActions = new LinearLayout(this);
        selectionActions.setGravity(Gravity.CENTER_VERTICAL);
        selectionCount = text("已选择 0 项", 14, true);
        selectionActions.addView(selectionCount, new LinearLayout.LayoutParams(0, -2, 1));
        selectAllButton = button("选中本筛选", false, this::toggleSelectAll);
        deleteSelectedButton = button("删除", true, this::confirmDeleteSelected);
        selectionActions.addView(selectAllButton);
        selectionActions.addView(deleteSelectedButton);
        selectionBar.addView(selectionActions);
        selectionBar.setVisibility(View.GONE);
        UiStyle.addSpaced(content, selectionBar, 7, 6);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        renderInboxResults(store);
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
                updateResults();
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
        List<TaskRecord> visible = new ArrayList<>();
        for (TaskRecord task : tasks) if (matches(task)) visible.add(task);
        matchingInboxIds = new ArrayList<>();
        for (TaskRecord task : visible) matchingInboxIds.add(task.id);
        selectedInboxIds.retainAll(matchingInboxIds);
        updateSelectionUi();
        if (visible.isEmpty()) {
            UiStyle.addSpaced(results, text("共 0 条收件", 13, false), 12, 2);
            LinearLayout emptyCard = card();
            emptyCard.setGravity(Gravity.CENTER);
            emptyCard.setMinimumHeight(dp(168));
            TextView emptyCopy = text("这里空空\n终于没事了~", 17, false);
            emptyCopy.setGravity(Gravity.CENTER);
            emptyCard.addView(emptyCopy, new LinearLayout.LayoutParams(-1, -2));
            UiStyle.addSpaced(results, emptyCard, 3, 8);
        } else {
            sectionTitle(results, "共 " + visible.size() + " 条收件");
        }
        for (int i = 0; i < Math.min(inboxShown, visible.size()); i++)
            taskRow(results, visible.get(i), i);
        if (visible.size() > inboxShown) {
            Button more = button("继续浏览 · 还有 " + (visible.size() - inboxShown) + " 条",
                    false, () -> { inboxShown += 12; updateResults(); });
            UiStyle.addSpaced(results, more, 8, 0);
        }
    }

    private void schedule(TaskStore store) {
        message(content, "按时间浏览解析出的事项，点开即可修改或写入日历。");
        filterChips = chipRow(new String[]{"即将到来", "待补全", "较早的"}, scheduleTab, index -> {
            if (scheduleTab == index) return;
            scheduleTab = index;
            scheduleShown = 12;
            updateChipSelection(scheduleTab);
            updateResults();
        });
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        renderScheduleResults(store);
    }

    private void renderScheduleResults(TaskStore store) {
        results.removeAllViews();
        List<EventCandidate> candidates = store.listCandidates();
        long now = System.currentTimeMillis();
        List<EventCandidate> visible = new ArrayList<>();
        for (EventCandidate item : candidates) {
            boolean complete = item.startAtMillis != null && item.endAtMillis != null;
            if ((scheduleTab == 0 && isUpcoming(item, now))
                    || (scheduleTab == 1 && !complete)
                    || (scheduleTab == 2 && complete && !isUpcoming(item, now)))
                visible.add(item);
        }
        if (scheduleTab == 2) java.util.Collections.reverse(visible);
        sectionTitle(results, (scheduleTab == 0 ? "即将到来" : scheduleTab == 1
                ? "待补全时间" : "较早的日程") + " · " + visible.size());
        if (visible.isEmpty()) {
            empty(results, scheduleTab == 0 ? "暂无即将到来的日程。"
                    : scheduleTab == 1 ? "没有待补全时间的事项。" : "还没有较早的日程。");
        }
        for (int i = 0; i < Math.min(scheduleShown, visible.size()); i++)
            candidateRow(results, visible.get(i), i);
        if (visible.size() > scheduleShown) {
            Button more = button("继续浏览 · 还有 " + (visible.size() - scheduleShown) + " 条",
                    false, () -> { scheduleShown += 12; updateResults(); });
            UiStyle.addSpaced(results, more, 8, 0);
        }
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
    }

    private void updateChipSelection(int selected) {
        if (filterChips == null) return;
        for (int i = 0; i < filterChips.getChildCount(); i++) {
            TextView chip = (TextView) filterChips.getChildAt(i);
            chip.setTextColor(i == selected ? UiStyle.colors(this).onPrimaryContainer
                    : UiStyle.colors(this).primary);
            applyThemeControlSurface(chip, i == selected, UiStyle.RADIUS_PILL);
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
        card.addView(title);
        String when = item.startAtMillis == null ? "时间待补全" : formatWhen(item);
        TextView meta = text(EventCategory.label(item.category) + " · " + when
                + (item.calendarEventId == null ? " · 待确认" : " · 已写入"), 13, false);
        UiStyle.addSpaced(card, meta, 6, 0);
        card.setOnClickListener(view -> openTask(item.taskId));
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
        if (selectedInboxIds.containsAll(matchingInboxIds)) {
            selectedInboxIds.removeAll(matchingInboxIds);
        } else {
            selectedInboxIds.addAll(matchingInboxIds);
        }
        updateSelectionUi();
    }

    private void clearInboxSelection() {
        selectedInboxIds.clear();
        updateSelectionUi();
    }

    private void updateSelectionUi() {
        if (selectionBar == null) return;
        boolean active = !selectedInboxIds.isEmpty();
        boolean wasVisible = selectionBar.getVisibility() == View.VISIBLE;
        selectionBar.animate().cancel();
        if (active && !wasVisible && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            selectionBar.setAlpha(0f);
            selectionBar.setTranslationY(-dp(5));
            selectionBar.setVisibility(View.VISIBLE);
            selectionBar.animate().alpha(1f).translationY(0f).setDuration(170).start();
        } else if (!active && wasVisible
                && android.animation.ValueAnimator.areAnimatorsEnabled()) {
            selectionBar.animate().alpha(0f).translationY(-dp(4)).setDuration(120)
                    .withEndAction(() -> {
                        selectionBar.setVisibility(View.GONE);
                        selectionBar.setAlpha(1f);
                        selectionBar.setTranslationY(0f);
                    }).start();
        } else {
            selectionBar.setAlpha(1f);
            selectionBar.setTranslationY(0f);
            selectionBar.setVisibility(active ? View.VISIBLE : View.GONE);
        }
        selectionCount.setText("已选 " + selectedInboxIds.size() + " / "
                + matchingInboxIds.size() + " 条");
        boolean all = !matchingInboxIds.isEmpty()
                && selectedInboxIds.containsAll(matchingInboxIds);
        selectAllButton.setText(all ? "取消全选" : "选中本筛选");
        deleteSelectedButton.setText("删除 " + selectedInboxIds.size() + " 项");
        for (Map.Entry<Long, InboxRow> entry : inboxRows.entrySet())
            applyInboxRowSelection(entry.getKey());
    }

    private void applyInboxRowSelection(long taskId) {
        InboxRow row = inboxRows.get(taskId);
        if (row == null) return;
        boolean selected = selectedInboxIds.contains(taskId);
        row.marker.setVisibility(selectedInboxIds.isEmpty() ? View.GONE : View.VISIBLE);
        row.marker.setText(selected ? "✓" : "○");
        row.marker.setTextColor(selected ? UiStyle.colors(this).primary
                : UiStyle.colors(this).muted);
        // Keep the acrylic card background; selection is already shown by the marker and toolbar.
        UiStyle.pressable(row.card);
    }

    private void confirmDeleteSelected() {
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
        deleteSelectedButton.setEnabled(false);
        selectAllButton.setEnabled(false);
        selectionCount.setText("正在清理关联日程…");
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
                deleteSelectedButton.setEnabled(true);
                selectAllButton.setEnabled(true);
                try (TaskStore store = new TaskStore(this)) {
                    renderInboxResults(store);
                } catch (Exception exception) {
                    Feedback.showLong(this, "刷新收件箱失败：" + exception.getMessage());
                }
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
            navigation.addView(item, params);
        }
    }

    /** Theme-tinted, borderless surface for dashboard controls. */
    private void applyThemeControlSurface(View view, boolean selected, int radius) {
        UiStyle.acrylicChoice(view, selected, radius, false);
    }

    private LinearLayout mobileRecordButton() {
        LinearLayout item = new LinearLayout(this);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER);
        TextView plus = text("＋", 24, true);
        plus.setGravity(Gravity.CENTER);
        plus.setTextColor(UiStyle.colors(this).onPrimary);
        GradientDrawable circle = new GradientDrawable();
        circle.setShape(GradientDrawable.OVAL);
        circle.setColor(UiStyle.colors(this).primary);
        plus.setBackground(circle);
        plus.setElevation(dp(8));
        item.addView(plus, new LinearLayout.LayoutParams(dp(52), dp(52)));
        item.setContentDescription("记录一件事");
        item.setFocusable(true);
        item.setMinimumWidth(dp(52));
        item.setMinimumHeight(dp(52));
        item.setElevation(dp(6));
        item.setOnClickListener(view -> startActivity(new Intent(this, MainActivity.class)));
        return item;
    }

    private void switchTo(int destination) {
        if (destination == section) return;
        if (section == INBOX && destination != INBOX) clearInboxSelection();
        section = destination;
        UiStyle.swap(content, () -> {
            render();
            scroll.scrollTo(0, 0);
        });
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(16), dp(17), dp(16));
        UiStyle.card(card);
        return card;
    }

    private LinearLayout chipRow(String[] labels, int selected, java.util.function.IntConsumer action) {
        return chipRow(labels, selected, action, null);
    }

    private LinearLayout chipRow(String[] labels, int selected,
            java.util.function.IntConsumer action, View trailingAction) {
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipChildren(true);
        strip.setClipToPadding(true);
        strip.setFillViewport(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        // Keep the final chip fully inside the scroll viewport at maximum scroll. The filter
        // button is a sibling in the bar below, so it never covers the scrolling chip content.
        if (trailingAction != null) row.setPadding(0, 0, dp(28), 0);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = text(labels[i], 14, true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(48));
            int horizontalPadding = trailingAction == null ? 18 : 12;
            chip.setPadding(dp(horizontalPadding), 0, dp(horizontalPadding), 0);
            chip.setTextColor(i == selected ? UiStyle.colors(this).onPrimaryContainer
                    : UiStyle.colors(this).primary);
            applyThemeControlSurface(chip, i == selected, UiStyle.RADIUS_PILL);
            chip.setOnClickListener(view -> action.accept(index));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, 0, dp(8), 0);
            row.addView(chip, params);
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
            View selectedChip = row.getChildAt(selected);
            int viewportWidth = strip.getWidth() - strip.getPaddingLeft()
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

    private void inboxShortcut(int reviewCount) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(48));
        row.setPadding(dp(2), 0, dp(2), 0);
        TextView title = text("收件箱", 16, true);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView action = text("查看收件箱", 13, true);
        action.setTextColor(UiStyle.colors(this).primary);
        row.addView(action);
        if (reviewCount > 0) {
            TextView badge = text(reviewCount > 99 ? "99+" : Integer.toString(reviewCount), 12, true);
            badge.setGravity(Gravity.CENTER);
            badge.setTextColor(UiStyle.colors(this).onPrimary);
            badge.setMinWidth(dp(24));
            badge.setMinHeight(dp(24));
            badge.setPadding(dp(6), 0, dp(6), 0);
            GradientDrawable badgeShape = new GradientDrawable();
            badgeShape.setColor(UiStyle.colors(this).primary);
            badgeShape.setCornerRadius(dp(30));
            badge.setBackground(badgeShape);
            LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, dp(24));
            badgeParams.setMargins(dp(8), 0, 0, 0);
            row.addView(badge, badgeParams);
        }
        row.setContentDescription("查看收件箱，待确认 " + reviewCount + " 项");
        row.setOnClickListener(view -> {
            statusIndex = 1;
            categoryIndex = 0;
            switchTo(INBOX);
        });
        UiStyle.pressable(row);
        UiStyle.addSpaced(content, row, 0, 10);
    }

    private void timelineDay(String title, LocalDate date, List<HomeTimelineEntry> entries) {
        if (entries.isEmpty()) return;
        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        View marker = new View(this);
        GradientDrawable markerShape = new GradientDrawable();
        markerShape.setShape(GradientDrawable.OVAL);
        markerShape.setColor(UiStyle.colors(this).primary);
        marker.setBackground(markerShape);
        heading.addView(marker, new LinearLayout.LayoutParams(dp(9), dp(9)));
        TextView label = text(title, 15, true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(-2, -2);
        labelParams.setMargins(dp(9), 0, dp(8), 0);
        heading.addView(label, labelParams);
        TextView dateLabel = text(date.getMonthValue() + "月" + date.getDayOfMonth() + "日", 13, false);
        heading.addView(dateLabel);
        View divider = new View(this);
        divider.setBackgroundColor(UiStyle.colors(this).outline);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(0, dp(1), 1);
        dividerParams.setMargins(dp(10), 0, 0, 0);
        heading.addView(divider, dividerParams);
        UiStyle.addSpaced(content, heading, 7, 2);

        for (int i = 0; i < entries.size(); i++) {
            HomeTimelineEntry entry = entries.get(i);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.TOP);
            TextView time = text(timelineTime(entry), 12, true);
            time.setGravity(Gravity.TOP | Gravity.END);
            time.setMinWidth(dp(54));
            LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(dp(58), -2);
            timeParams.setMargins(0, dp(13), dp(8), 0);
            row.addView(time, timeParams);

            FrameLayout rail = new FrameLayout(this);
            LinearLayout.LayoutParams railParams = new LinearLayout.LayoutParams(dp(12), -1);
            railParams.setMargins(0, 0, dp(8), 0);
            row.addView(rail, railParams);
            if (i < entries.size() - 1) {
                View line = new View(this);
                line.setBackgroundColor(UiStyle.colors(this).outline);
                rail.addView(line, new FrameLayout.LayoutParams(dp(2), -1, Gravity.CENTER));
            }
            View dot = new View(this);
            GradientDrawable dotShape = new GradientDrawable();
            dotShape.setShape(GradientDrawable.OVAL);
            dotShape.setColor(UiStyle.colors(this).primary);
            dot.setBackground(dotShape);
            FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dp(8), dp(8),
                    Gravity.TOP | Gravity.CENTER_HORIZONTAL);
            dotParams.topMargin = dp(15);
            rail.addView(dot, dotParams);

            LinearLayout itemCard = card();
            itemCard.setPadding(dp(14), dp(12), dp(14), dp(12));
            String headline = entry.candidate.title;
            TextView headlineView = text(headline, 15, true);
            headlineView.setMaxLines(2);
            headlineView.setEllipsize(android.text.TextUtils.TruncateAt.END);
            itemCard.addView(headlineView);
            String metadata = EventCategory.label(entry.candidate.category)
                    + (entry.candidate.location == null || entry.candidate.location.trim().isEmpty()
                            ? "" : " · " + entry.candidate.location);
            UiStyle.addSpaced(itemCard, text(metadata, 12, false), 4, 0);
            itemCard.setOnClickListener(view -> openTask(entry.candidate.taskId));
            UiStyle.pressable(itemCard);
            row.addView(itemCard, new LinearLayout.LayoutParams(0, -2, 1));
            UiStyle.addSpaced(content, row, 2, 7);
        }
    }

    private String timelineTime(HomeTimelineEntry entry) {
        if (entry.candidate != null && entry.candidate.allDay) return "全天";
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
