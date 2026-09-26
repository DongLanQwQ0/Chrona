package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
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
import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.processing.ProcessingJobService;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Three clear destinations: overview, incoming inputs, and extracted schedule. */
public final class DashboardActivity extends Activity {
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
    private TextView categoryChip;
    private ScrollView scroll;
    private GlassBackdropView backdrop;
    private boolean wide;
    /** Cards stagger in only for a screen the user just opened, never for a rebuild. */
    private boolean animateEntrances;
    private String snapshot = "";
    private int restoredScrollY;
    private final Handler refresh = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::pollChanges;

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
    }

    @Override protected void onPause() {
        refresh.removeCallbacks(poll);
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
            boolean processing = false;
            for (TaskRecord task : store.listTasks()) {
                value.append(task.id).append(':').append(task.status).append(';');
                if (TaskRecord.PROCESSING.equals(task.status)
                        || TaskRecord.QUEUED.equals(task.status)) processing = true;
            }
            // While something is parsing the snapshot also moves every five seconds, so a row's
            // "已 N" keeps counting without a second timer of its own.
            value.append('/').append(store.listCandidates().size())
                    .append('/').append(processing
                            ? System.currentTimeMillis() / 5000L
                            : System.currentTimeMillis() / 300000L);
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
        content.setPadding(dp(horizontal), dp(18), dp(horizontal),
                dp(wide ? 42 : 104));
        scroll.addView(content);
        navigation = new LinearLayout(this);
        navigation.setOrientation(wide ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        navigation.setPadding(dp(wide ? 8 : 12), dp(wide ? 24 : 8),
                dp(wide ? 8 : 12), dp(10));
        UiStyle.glass(navigation);
        if (wide) {
            shell.addView(navigation, new LinearLayout.LayoutParams(dp(90), -1));
            shell.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1));
        } else {
            shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
            shell.addView(navigation);
        }
        stage.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        Button capture = button("＋ 记录", true, () ->
                startActivity(new Intent(this, MainActivity.class)));
        capture.setElevation(dp(12));
        FrameLayout.LayoutParams floating = new FrameLayout.LayoutParams(-2, dp(54),
                Gravity.END | Gravity.BOTTOM);
        floating.setMargins(0, 0, dp(24), dp(wide ? 24 : 87));
        stage.addView(capture, floating);
        UiStyle.applyInsets(stage, shell, capture);
        setContentView(stage);
    }

    private void render() {
        results = null;
        filterChips = null;
        categoryChip = null;
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
                : section == INBOX ? "收件箱" : "日程", 28, true);
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
        UiStyle.addSpaced(content, row, 0, 14);
    }

    private void home(TaskStore store) {
        List<TaskRecord> tasks = store.listTasks();
        int review = 0, processing = 0, failed = 0;
        TaskRecord firstReview = null;
        for (TaskRecord task : tasks) {
            if (TaskRecord.NEEDS_REVIEW.equals(task.status)) {
                review++;
                if (firstReview == null) firstReview = task;
            }
            if (TaskRecord.QUEUED.equals(task.status)
                    || TaskRecord.PROCESSING.equals(task.status)) processing++;
            if (TaskRecord.FAILED.equals(task.status)) failed++;
        }
        EventCandidate next = null;
        long now = System.currentTimeMillis();
        List<EventCandidate> candidates = store.listCandidates();
        for (EventCandidate item : candidates) {
            if (isUpcoming(item, now)) {
                next = item;
                break;
            }
        }

        LinearLayout hero = card();
        UiStyle.glass(hero);
        TextView kicker = text(firstReview != null ? "需要你确认"
                : next != null ? "下一件事" : "轻松开始", 13, true);
        kicker.setTextColor(UiStyle.colors(this).primary);
        hero.addView(kicker);
        String headline = firstReview != null ? preview(firstReview)
                : next != null ? next.title : "把想到的事交给拾时";
        TextView lead = text(headline, 23, true);
        lead.setMaxLines(2);
        lead.setEllipsize(android.text.TextUtils.TruncateAt.END);
        UiStyle.addSpaced(hero, lead, 8, 7);
        String description = firstReview != null ? "点开检查解析结果，再决定是否写入日历"
                : next != null ? formatWhen(next)
                : "记录文字、图片或语音，日程稍后再确认";
        TextView sub = text(description, 14, false);
        hero.addView(sub);
        TextView link = text(firstReview != null ? "去确认  →"
                : next != null ? "查看日程  →" : "开始记录  →", 14, true);
        link.setTextColor(UiStyle.colors(this).primary);
        UiStyle.addSpaced(hero, link, 18, 0);
        final TaskRecord selectedReview = firstReview;
        final EventCandidate selectedNext = next;
        hero.setOnClickListener(view -> {
            if (selectedReview != null) openTask(selectedReview.id);
            else if (selectedNext != null) openTask(selectedNext.taskId);
            else startActivity(new Intent(this, MainActivity.class));
        });
        UiStyle.pressable(hero);
        UiStyle.addSpaced(content, hero, 0, 10);

        sectionHeading("处理进度", "查看收件箱", INBOX);
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stat(stats, review, "待确认", () -> { statusIndex = 1; switchTo(INBOX); });
        stat(stats, processing, "处理中", () -> { statusIndex = 2; switchTo(INBOX); });
        content.addView(stats);
        if (failed > 0) {
            Button failedLink = button(failed + " 条处理失败 · 查看", false,
                    () -> { statusIndex = 3; switchTo(INBOX); });
            UiStyle.addSpaced(content, failedLink, 10, 0);
        }

        sectionHeading("近期日程", "查看全部", SCHEDULE);
        int upcoming = 0;
        for (EventCandidate item : candidates) {
            if (isUpcoming(item, now) && upcoming < 2) {
                candidateRow(item, upcoming++);
            }
        }
        if (upcoming == 0) empty("暂无近期日程，记录后会在这里出现。");

        if (!tasks.isEmpty()) {
            sectionHeading("最近收件", "查看全部", INBOX);
            for (int i = 0; i < Math.min(tasks.size(), 2); i++)
                taskRow(content, tasks.get(i), i);
        }
    }

    private void inbox(TaskStore store) {
        message(content, "所有输入与处理进度。点开一条，可确认、编辑或重试。");
        filterChips = chipRow(new String[]{"全部", "待确认", "处理中", "失败", "已写入"},
                statusIndex, index -> {
                    if (statusIndex == index) return;
                    statusIndex = index;
                    inboxShown = 12;
                    updateChipSelection(statusIndex);
                    updateResults();
                });
        TextView category = text("类型：" + categoryOptions()[categoryIndex], 16, false);
        categoryChip = category;
        UiStyle.fieldTrigger(category);
        category.setOnClickListener(view -> {
            String[] choices = categoryOptions();
            UiStyle.choiceDialog(this, "选择收件类型", choices, categoryIndex, selected -> {
                if (categoryIndex == selected) return;
                categoryIndex = selected;
                inboxShown = 12;
                categoryChip.setText("类型：" + choices[categoryIndex]);
                updateResults();
            });
        });
        UiStyle.addSpaced(content, category, 4, 4);
        results = new LinearLayout(this);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results);
        renderInboxResults(store);
    }

    private void renderInboxResults(TaskStore store) {
        results.removeAllViews();
        String selectedCategory = categoryIndex == 0 ? null
                : EventCategory.VALUES[categoryIndex - 1];
        List<TaskRecord> tasks = statusIndex == 4
                ? store.listPublishedTasks(selectedCategory)
                : selectedCategory == null ? store.listTasks()
                        : store.listTasksByCategory(selectedCategory);
        List<TaskRecord> visible = new ArrayList<>();
        for (TaskRecord task : tasks) if (matches(task)) visible.add(task);
        sectionTitle(results, "共 " + visible.size() + " 条收件");
        if (visible.isEmpty()) empty(results, "没有符合条件的收件。");
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
        // Fade through the swap: filtering must read as new content, not as a page reload.
        UiStyle.swap(results, () -> {
            try (TaskStore store = new TaskStore(this)) {
                if (section == INBOX) renderInboxResults(store);
                else if (section == SCHEDULE) renderScheduleResults(store);
            } catch (Exception exception) {
                results.removeAllViews();
                message(results, "暂时无法读取日程：" + exception.getMessage());
            }
            scroll.scrollTo(0, previous);
        });
    }

    private void updateChipSelection(int selected) {
        if (filterChips == null) return;
        for (int i = 0; i < filterChips.getChildCount(); i++) {
            TextView chip = (TextView) filterChips.getChildAt(i);
            chip.setTextColor(i == selected ? UiStyle.colors(this).onPrimaryContainer
                    : UiStyle.colors(this).primary);
            UiStyle.pill(chip, i == selected);
        }
    }

    private void candidateRow(EventCandidate item, int index) {
        candidateRow(content, item, index);
    }

    private void candidateRow(LinearLayout parent, EventCandidate item, int index) {
        LinearLayout card = card();
        card.addView(text(item.title, 17, true));
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
        card.addView(text(preview(task), 16, true));
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
        }
        UiStyle.addSpaced(card, meta, 7, 0);
        card.setOnClickListener(view -> openTask(task.id));
        UiStyle.pressable(card);
        UiStyle.addSpaced(parent, card, 4, 7);
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
            item.setPadding(0, dp(8), 0, dp(8));
            item.setTextColor(section == i ? UiStyle.colors(this).onPrimaryContainer
                    : UiStyle.colors(this).muted);
            item.setCompoundDrawableTintList(ColorStateList.valueOf(section == i
                    ? UiStyle.colors(this).onPrimaryContainer : UiStyle.colors(this).muted));
            item.setContentDescription(labels[i] + (section == i ? "，当前页面" : ""));
            UiStyle.pill(item, section == i);
            item.setOnClickListener(view -> switchTo(destination));
            LinearLayout.LayoutParams params = wide
                    ? new LinearLayout.LayoutParams(-1, dp(78))
                    : new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(dp(3), dp(wide ? 5 : 0), dp(3), 0);
            navigation.addView(item, params);
        }
    }

    private void switchTo(int destination) {
        if (destination == section) return;
        section = destination;
        UiStyle.swap(content, () -> {
            render();
            scroll.scrollTo(0, 0);
        });
    }

    private void stat(LinearLayout parent, int count, String label, Runnable action) {
        LinearLayout box = card();
        UiStyle.glass(box);
        TextView number = text(Integer.toString(count), 26, true);
        number.setTextColor(UiStyle.colors(this).primary);
        box.addView(number);
        box.addView(text(label, 13, false));
        box.setOnClickListener(view -> action.run());
        UiStyle.pressable(box);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(0, 0, dp(8), 0);
        parent.addView(box, params);
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(17), dp(16), dp(17), dp(16));
        UiStyle.card(card);
        return card;
    }

    private LinearLayout chipRow(String[] labels, int selected, java.util.function.IntConsumer action) {
        HorizontalScrollView strip = new HorizontalScrollView(this);
        strip.setHorizontalScrollBarEnabled(false);
        strip.setClipToPadding(false);
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < labels.length; i++) {
            final int index = i;
            TextView chip = text(labels[i], 14, true);
            chip.setGravity(Gravity.CENTER);
            chip.setMinHeight(dp(44));
            chip.setPadding(dp(18), 0, dp(18), 0);
            chip.setTextColor(i == selected ? UiStyle.colors(this).onPrimaryContainer
                    : UiStyle.colors(this).primary);
            UiStyle.pill(chip, i == selected);
            chip.setOnClickListener(view -> action.accept(index));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMargins(0, 0, dp(8), 0);
            row.addView(chip, params);
        }
        strip.addView(row);
        UiStyle.addSpaced(content, strip, 10, 12);
        strip.post(() -> {
            if (selected < row.getChildCount()) strip.scrollTo(
                    Math.max(0, row.getChildAt(selected).getLeft() - dp(20)), 0);
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

    private void sectionHeading(String title, String action, int destination) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(title, 19, true);
        row.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        TextView link = text(action + "  →", 13, true);
        link.setTextColor(UiStyle.colors(this).primary);
        link.setMinHeight(dp(44));
        link.setGravity(Gravity.CENTER_VERTICAL);
        link.setOnClickListener(view -> switchTo(destination));
        row.addView(link);
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
