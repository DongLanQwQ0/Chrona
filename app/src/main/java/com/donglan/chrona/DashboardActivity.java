package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.TaskRecord;
import com.donglan.chrona.data.TaskStore;
import com.donglan.chrona.processing.ProcessingJobService;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/** Three clear destinations: overview, incoming inputs, and extracted schedule. */
public final class DashboardActivity extends Activity {
    static final String EXTRA_SECTION = "section";
    static final int HOME = 0, INBOX = 1, SCHEDULE = 2;
    private int section = HOME;
    private int categoryIndex, statusIndex;
    private LinearLayout content;
    private LinearLayout navigation;
    private ScrollView scroll;
    private String appliedAppearance;
    private String snapshot = "";
    private final Handler refresh = new Handler(Looper.getMainLooper());
    private final Runnable poll = this::pollChanges;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        if (state != null) {
            section = state.getInt(EXTRA_SECTION, HOME);
            categoryIndex = state.getInt("category");
            statusIndex = state.getInt("status");
        } else {
            section = getIntent().getIntExtra(EXTRA_SECTION, HOME);
        }
        appliedAppearance = appearanceKey();
        buildShell();
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
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        section = intent.getIntExtra(EXTRA_SECTION, section);
        render();
    }

    @Override protected void onResume() {
        super.onResume();
        if (!appearanceKey().equals(appliedAppearance)) {
            recreate();
            return;
        }
        int recovered = ProcessingJobService.reconcile(this);
        if (recovered > 0) Toast.makeText(this,
                recovered + " 条解析已中断，请在收件箱重试", Toast.LENGTH_LONG).show();
        render();
        snapshot = dataSnapshot();
        refresh.postDelayed(poll, 5000L);
    }

    @Override protected void onPause() {
        refresh.removeCallbacks(poll);
        super.onPause();
    }

    private void pollChanges() {
        String current = dataSnapshot();
        if (!current.equals(snapshot)) {
            int scrollY = scroll.getScrollY();
            render();
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
            value.append('/').append(store.listCandidates().size());
            return value.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private String appearanceKey() {
        return ThemeStore.mode(this) + "/" + ThemeStore.color(this) + "/"
                + ThemeStore.dark(this);
    }

    private void buildShell() {
        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        UiStyle.page(this, shell);
        scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(28));
        scroll.addView(content);
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        navigation = new LinearLayout(this);
        navigation.setPadding(dp(12), dp(8), dp(12), dp(10));
        navigation.setBackgroundColor(UiStyle.colors(this).surface);
        shell.addView(navigation);
        setContentView(shell);
    }

    private void render() {
        content.removeAllViews();
        addHeader();
        try (TaskStore store = new TaskStore(this)) {
            if (section == HOME) home(store);
            else if (section == INBOX) inbox(store);
            else schedule(store);
        } catch (Exception exception) {
            message(content, "暂时无法读取日程：" + exception.getMessage());
        }
        drawNavigation();
        UiStyle.enter(content, 0);
    }

    private void addHeader() {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        TextView brand = text("拾时", 14, true);
        brand.setTextColor(UiStyle.colors(this).primary);
        words.addView(brand);
        TextView title = text(section == HOME ? "把时间留给生活"
                : section == INBOX ? "收件箱" : "日程", 28, true);
        words.addView(title);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1));
        Button settings = new Button(this);
        settings.setText("设置");
        settings.setContentDescription("打开设置");
        UiStyle.button(settings, false);
        settings.setOnClickListener(view ->
                startActivity(new Intent(this, SettingsHubActivity.class)));
        row.addView(settings);
        UiStyle.addSpaced(content, row, 0, 18);
    }

    private void home(TaskStore store) {
        List<TaskRecord> tasks = store.listTasks();
        int review = 0, processing = 0, failed = 0;
        for (TaskRecord task : tasks) {
            if (TaskRecord.NEEDS_REVIEW.equals(task.status)) review++;
            if (TaskRecord.QUEUED.equals(task.status)
                    || TaskRecord.PROCESSING.equals(task.status)) processing++;
            if (TaskRecord.FAILED.equals(task.status)) failed++;
        }
        LinearLayout hero = card();
        UiStyle.pill(hero, true);
        TextView kicker = text("轻松收集 · 从容安排", 14, true);
        kicker.setTextColor(UiStyle.colors(this).onPrimaryContainer);
        hero.addView(kicker);
        TextView lead = text("想到的事，先记下来。", 22, true);
        lead.setTextColor(UiStyle.colors(this).onPrimaryContainer);
        UiStyle.addSpaced(hero, lead, 8, 8);
        TextView sub = text("拾时会整理出可确认的日程，再由你决定何时写入日历。", 15, false);
        sub.setTextColor(UiStyle.colors(this).onPrimaryContainer);
        hero.addView(sub);
        Button capture = button("＋ 记录一件事", true, () ->
                startActivity(new Intent(this, MainActivity.class)));
        UiStyle.addSpaced(hero, capture, 16, 0);
        UiStyle.addSpaced(content, hero, 0, 18);

        sectionTitle("现在需要你");
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stat(stats, review, "待确认", () -> { statusIndex = 1; switchTo(INBOX); });
        stat(stats, processing, "处理中", () -> { statusIndex = 3; switchTo(INBOX); });
        content.addView(stats);
        if (failed > 0) {
            Button failedLink = button(failed + " 条处理失败 · 点此查看", false,
                    () -> { statusIndex = 4; switchTo(INBOX); });
            UiStyle.addSpaced(content, failedLink, 10, 0);
        }

        sectionTitle("最近收到");
        if (tasks.isEmpty()) {
            empty("还没有记录。点击上方按钮，写下第一件事。");
        } else {
            for (int i = 0; i < Math.min(tasks.size(), 3); i++) taskRow(content, tasks.get(i), i);
            Button all = button("查看全部收件  →", false, () -> switchTo(INBOX));
            UiStyle.addSpaced(content, all, 8, 0);
        }
    }

    private void inbox(TaskStore store) {
        message(content, "所有输入和解析进度都在这里。点开一条可确认、编辑或重试。");
        Button capture = button("＋ 记录一件事", true, () ->
                startActivity(new Intent(this, MainActivity.class)));
        UiStyle.addSpaced(content, capture, 12, 14);
        sectionTitle("筛选");
        Spinner category = spinner(categoryOptions());
        category.setSelection(categoryIndex);
        category.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    View view, int position, long id) {
                if (categoryIndex != position) { categoryIndex = position; render(); }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        UiStyle.addSpaced(content, category, 0, 8);
        Spinner status = spinner(new String[]{"全部状态", "待确认", "已写入日历",
                "处理中", "处理失败"});
        status.setSelection(statusIndex);
        status.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(android.widget.AdapterView<?> parent,
                    View view, int position, long id) {
                if (statusIndex != position) { statusIndex = position; render(); }
            }
            @Override public void onNothingSelected(android.widget.AdapterView<?> parent) { }
        });
        UiStyle.addSpaced(content, status, 0, 12);
        String selectedCategory = categoryIndex == 0 ? null
                : EventCategory.VALUES[categoryIndex - 1];
        List<TaskRecord> tasks = statusIndex == 2
                ? store.listPublishedTasks(selectedCategory)
                : selectedCategory == null ? store.listTasks()
                        : store.listTasksByCategory(selectedCategory);
        List<TaskRecord> visible = new ArrayList<>();
        for (TaskRecord task : tasks) if (matches(task)) visible.add(task);
        sectionTitle("共 " + visible.size() + " 条");
        if (visible.isEmpty()) empty("没有符合条件的收件。");
        for (int i = 0; i < visible.size(); i++) taskRow(content, visible.get(i), i);
    }

    private void schedule(TaskStore store) {
        message(content, "按日期查看已识别事项。需要修改或写入日历时，点开对应条目。");
        List<EventCandidate> candidates = store.listCandidates();
        sectionTitle("即将到来");
        long now = System.currentTimeMillis();
        int upcoming = 0;
        for (EventCandidate item : candidates) {
            if (item.startAtMillis != null && item.endAtMillis != null
                    && item.startAtMillis >= now) {
                candidateRow(item, upcoming++);
            }
        }
        if (upcoming == 0) empty("暂无即将到来的日程。");
        sectionTitle("待补全时间");
        int unresolved = 0;
        for (EventCandidate item : candidates) {
            if (item.startAtMillis == null || item.endAtMillis == null)
                candidateRow(item, unresolved++);
        }
        if (unresolved == 0) empty("没有待补全时间的事项。");
        int past = 0;
        for (EventCandidate item : candidates) {
            if (item.startAtMillis != null && item.endAtMillis != null
                    && item.startAtMillis < now) past++;
        }
        if (past > 0) {
            sectionTitle("较早的日程");
            int index = 0;
            for (int i = candidates.size() - 1; i >= 0; i--) {
                EventCandidate item = candidates.get(i);
                if (item.startAtMillis != null && item.endAtMillis != null
                        && item.startAtMillis < now)
                    candidateRow(item, index++);
            }
        }
    }

    private void candidateRow(EventCandidate item, int index) {
        LinearLayout card = card();
        card.addView(text(item.title, 17, true));
        String when = item.startAtMillis == null ? "时间待补全"
                : DateFormat.getDateTimeInstance(DateFormat.MEDIUM,
                        item.allDay ? DateFormat.DEFAULT : DateFormat.SHORT)
                        .format(new Date(item.startAtMillis));
        TextView meta = text(EventCategory.label(item.category) + " · " + when
                + (item.calendarEventId == null ? " · 待确认" : " · 已写入"), 13, false);
        UiStyle.addSpaced(card, meta, 6, 0);
        card.setOnClickListener(view -> openTask(item.taskId));
        UiStyle.addSpaced(content, card, 4, 7);
        UiStyle.enter(card, index);
    }

    private void taskRow(LinearLayout parent, TaskRecord task, int index) {
        LinearLayout card = card();
        String preview = task.rawText.replace('\n', ' ').trim();
        if (task.imagePath != null) preview = "图片 · " + preview;
        if (preview.isEmpty()) preview = "图片收件";
        if (preview.length() > 92) preview = preview.substring(0, 92) + "…";
        card.addView(text(preview, 16, true));
        TextView meta = text(statusText(task.status) + " · "
                + DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                .format(new Date(task.createdAtMillis)), 13, false);
        UiStyle.addSpaced(card, meta, 7, 0);
        card.setOnClickListener(view -> openTask(task.id));
        UiStyle.addSpaced(parent, card, 4, 7);
        UiStyle.enter(card, index);
    }

    private void openTask(long taskId) {
        startActivity(new Intent(this, TaskDetailActivity.class).putExtra("task_id", taskId));
    }

    private boolean matches(TaskRecord task) {
        if (statusIndex == 0 || statusIndex == 2) return true;
        if (statusIndex == 1) return TaskRecord.NEEDS_REVIEW.equals(task.status);
        if (statusIndex == 3) return TaskRecord.QUEUED.equals(task.status)
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

    private String[] categoryOptions() {
        String[] result = new String[EventCategory.LABELS.length + 1];
        result[0] = "全部类型";
        System.arraycopy(EventCategory.LABELS, 0, result, 1, EventCategory.LABELS.length);
        return result;
    }

    private Spinner spinner(String[] options) {
        Spinner spinner = new Spinner(this);
        spinner.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, options));
        UiStyle.card(spinner);
        spinner.setPadding(dp(10), dp(6), dp(10), dp(6));
        return spinner;
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
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            params.setMargins(dp(3), 0, dp(3), 0);
            navigation.addView(item, params);
        }
    }

    private void switchTo(int destination) {
        if (destination == section) return;
        section = destination;
        render();
        scroll.scrollTo(0, 0);
    }

    private void stat(LinearLayout parent, int count, String label, Runnable action) {
        LinearLayout box = card();
        TextView number = text(Integer.toString(count), 26, true);
        number.setTextColor(UiStyle.colors(this).primary);
        box.addView(number);
        box.addView(text(label, 13, false));
        box.setOnClickListener(view -> action.run());
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
        TextView label = text(title, 19, true);
        UiStyle.addSpaced(content, label, 16, 9);
    }
    private void message(LinearLayout parent, String text) {
        UiStyle.addSpaced(parent, text(text, 14, false), 0, 0);
    }
    private void empty(String text) {
        LinearLayout box = card();
        box.addView(text(text, 15, false));
        UiStyle.addSpaced(content, box, 3, 8);
    }
    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
