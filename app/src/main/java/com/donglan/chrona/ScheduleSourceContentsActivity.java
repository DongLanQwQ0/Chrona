package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** A bounded, read-only source browser. Source inclusion switches do not filter this page. */
public final class ScheduleSourceContentsActivity extends Activity {
    private static final int PAGE_SIZE = 50;
    private static final int READ_PERMISSION = 761;
    private String source;
    private LocalDate week;
    private LinearLayout rows;
    private TextView range;
    private int generation;
    private boolean all = true, loading;
    private int offset;
    private long afterStart = Long.MIN_VALUE, afterId = -1;
    private LinearLayout navigation;
    private Button mode, more;
    private List<Entry> localSnapshot;
    private boolean firstDataPresentation;
    private int loadDirection;

    static Intent intent(Context context, String source, String name) {
        return new Intent(context, ScheduleSourceContentsActivity.class)
                .putExtra("source", source).putExtra("source_name", name);
    }

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state);
        firstDataPresentation=state==null||state.getBoolean("first_data_presentation",false);
        source = getIntent().getStringExtra("source");
        if (hiddenZju()) { finish(); return; }
        week = state != null && state.containsKey("week") ? LocalDate.ofEpochDay(state.getLong("week"))
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LinearLayout root = SettingsPageLayout.content(this);
        String name = getIntent().getStringExtra("source_name");
        SettingsPageLayout.header(this, root, name == null ? "来源内容" : name);
        all = state == null || state.getBoolean("all", true);
        mode = navigationButton("", () -> { all = !all; load(1); });
        UiStyle.addSpaced(root, mode, 0, 12);
        navigation = new LinearLayout(this); navigation.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = navigationButton("上周", () -> changeWeek(-1));
        navigation.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        Button current = navigationButton("本周", () -> {
            LocalDate target=LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            int direction=target.compareTo(week);week=target;load(direction);
        });
        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, -2, 1);
        middle.setMargins(dp(8), 0, dp(8), 0); navigation.addView(current, middle);
        navigation.addView(navigationButton("下周", () -> changeWeek(1)), new LinearLayout.LayoutParams(0, -2, 1));
        UiStyle.addSpaced(root, navigation, 0, 12);
        range = text(root, "", 13); UiStyle.muted(range);
        rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        FrameLayout rowsHost=new FrameLayout(this);rowsHost.addView(rows,new FrameLayout.LayoutParams(-1,-2));
        UiStyle.addSpaced(root, rowsHost, 12, 0);
        more = navigationButton("加载更多", this::loadPage);
        more.setVisibility(android.view.View.GONE);
        UiStyle.addSpaced(root, more, 8, 0);
        SettingsPageLayout.show(this, root, state == null);
    }

    @Override protected void onResume() { super.onResume(); if (!isFinishing()) load(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("week", week.toEpochDay()); state.putBoolean("all", all);state.putBoolean("first_data_presentation",firstDataPresentation); super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { generation++; super.onDestroy(); }
    private void changeWeek(int direction) { week = week.plusWeeks(direction); load(direction); }
    private boolean hiddenZju() { return "zju".equals(source) && !new ZjuSettingsStore(this).enabled(); }

    private void load() {
        load(0);
    }
    private void load(int direction) {
        loadDirection=direction;
        ++generation; loading = false; offset = 0; localSnapshot = null;
        afterStart = Long.MIN_VALUE; afterId = -1;
        more.setVisibility(android.view.View.GONE);
        if (hiddenZju()) { finish(); return; }
        mode.setText(all ? "全部日程 · 切换按周查看" : "按周查看 · 切换全部日程");
        navigation.setVisibility(all ? android.view.View.GONE : android.view.View.VISIBLE);
        DateTimeFormatter day = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA);
        range.setText(all ? "全部日程" : day.format(week) + " — " + day.format(week.plusDays(6)));
        loadPage();
    }

    private void loadPage() {
        if (loading || hiddenZju()) return;
        int request = generation, pageOffset = offset;
        int direction=loadDirection;
        boolean entireSource = all;
        long cursorStart = afterStart, cursorId = afterId;
        List<Entry> retained = localSnapshot;
        ZoneId zone = ZoneId.systemDefault();
        long begin = week.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = week.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli();
        if (source != null && source.startsWith("system:") &&
                checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Button permission = navigationButton("允许读取系统日历", () -> requestPermissions(
                    new String[]{Manifest.permission.READ_CALENDAR}, READ_PERMISSION));
            rows.removeAllViews();rows.addView(permission, new LinearLayout.LayoutParams(-1, -2)); return;
        }
        loading = true; more.setEnabled(false);
        if (pageOffset == 0&&rows.getChildCount()==0) text(rows, "正在读取日程…", 16);
        new Thread(() -> {
            try {
                boolean system = source != null && source.startsWith("system:");
                List<Entry> snapshot = system ? null : retained == null ? read(begin, end, zone, entireSource) : retained;
                List<Entry> result = system ? readSystem(begin, end, entireSource, cursorStart, cursorId)
                        : new ArrayList<>(snapshot.subList(Math.min(pageOffset, snapshot.size()),
                                Math.min(pageOffset + PAGE_SIZE + 1, snapshot.size())));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != request) return;
                    loading = false; localSnapshot = snapshot;
                    if (hiddenZju()) { finish(); return; }
                    int count = Math.min(PAGE_SIZE, result.size());
                    Runnable bind=()->{
                        if(pageOffset==0)rows.removeAllViews();
                        if(result.isEmpty()&&pageOffset==0)text(rows,entireSource?"暂无日程":"本周暂无日程",16);
                        for(int i=0;i<count;i++)addEntry(result.get(i));
                    };
                    boolean entering=firstDataPresentation;
                    if(pageOffset==0)firstDataPresentation=false;
                    if(pageOffset==0&&direction!=0)UiMotion.swap(rows,rows,bind,direction);
                    else{bind.run();if(entering)UiMotion.enterPage(rows);}
                    offset += count;
                    if (count > 0) { Entry last = result.get(count - 1); afterStart = last.start; afterId = last.providerId; }
                    more.setText("加载更多"); more.setEnabled(true);
                    more.setVisibility(result.size() > PAGE_SIZE ? android.view.View.VISIBLE : android.view.View.GONE);
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != request) return;
                    loading = false;
                    if (pageOffset == 0) rows.removeAllViews();
                    if (hiddenZju()) { finish(); return; }
                    more.setText("读取日程失败 · 重试"); more.setEnabled(true);
                    more.setVisibility(android.view.View.VISIBLE);
                });
            }
        }, "chrona-source-contents").start();
    }
    @Override protected void onPause(){if(rows!=null)UiMotion.finishSwap(rows);super.onPause();}

    private List<Entry> read(long begin, long end, ZoneId zone, boolean entireSource) throws Exception {
        return entries(ScheduleSourceRepository.local(this, source, begin, end, zone, entireSource));
    }
    private List<Entry> readSystem(long begin, long end, boolean entireSource, long after, long idAfter) {
        return entries(ScheduleSourceRepository.system(this, source, begin, end, entireSource, after, idAfter, 0));
    }
    private List<Entry> entries(List<ScheduleSourceRepository.Item> items) {
        List<Entry> result = new ArrayList<>();
        for (ScheduleSourceRepository.Item item : items) {
            Intent details;
            if (item.utcDate) {
                details = SystemAgendaDetailActivity.intent(this, item.providerId, item.start, item.end, item.allDay,
                        item.title, item.location, item.sourceName).putExtra("source_series", item.series);
            } else if (item.imported != null) {
                details = item.imported.activeTerm ? CourseAgenda.intent(this, item.imported) : null;
            } else {
                details = ExternalCourseDetailActivity.sourceIntent(this, "zju", item.key, item.start, item.end);
            }
            Entry entry = new Entry(item.title, item.location, item.start, item.end, item.allDay, item.utcDate, details);
            entry.providerId = item.providerId; entry.series = item.series; result.add(entry);
        }
        return result;
    }

    private void addEntry(Entry item) {
        LinearLayout card = new LinearLayout(this); card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(16), dp(12), dp(16), dp(12)); UiStyle.card(card);
        LinearLayout copy = new LinearLayout(this); copy.setOrientation(LinearLayout.VERTICAL);
        TextView title = text(copy, item.title, 16); title.setMaxLines(2); UiStyle.title(title);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView time = text(copy, item.time(), 13); UiStyle.muted(time);
        LinearLayout.LayoutParams timeParams=(LinearLayout.LayoutParams)time.getLayoutParams();timeParams.topMargin=dp(4);time.setLayoutParams(timeParams);
        if (!item.location.isBlank()) { TextView location = text(copy, item.location, 13); UiStyle.muted(location); }
        card.addView(copy, new LinearLayout.LayoutParams(0, -2, 1));
        ImageView arrow = new ImageView(this); arrow.setImageResource(R.drawable.ic_chevron_right);
        arrow.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).muted));
        LinearLayout.LayoutParams icon = new LinearLayout.LayoutParams(dp(20), dp(20));
        icon.leftMargin = dp(12); card.addView(arrow, icon);
        card.setOnClickListener(view -> {
            if (hiddenZju()) return;
            if (item.details != null) { startActivity(item.details); return; }
            // Inactive imported terms remain inspectable without enabling them or selecting another term.
            android.app.Dialog dialog = new android.app.Dialog(this);
            LinearLayout panel = UiDialogs.dialogPanel(this, item.title);
            LinearLayout body = new LinearLayout(this); body.setOrientation(LinearLayout.VERTICAL);
            text(body, item.time(), 16);
            if (!item.location.isBlank()) text(body, item.location, 16);
            UiDialogs.dialogBody(panel, body);
            Button close = navigationButton("关闭", dialog::dismiss); panel.addView(close);
            UiDialogs.showFloatingDialog(dialog, panel);
        });
        UiStyle.pressable(card);UiStyle.addSpaced(rows, card, 0, 8);
    }
    private Button navigationButton(String title, Runnable click) {
        Button button = new Button(this); button.setText(title); UiStyle.button(button, false);
        button.setOnClickListener(view -> click.run()); return button;
    }
    private TextView text(LinearLayout parent, String value, int size) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size);
        view.setTextColor(UiStyle.colors(this).text); parent.addView(view, new LinearLayout.LayoutParams(-1, -2)); return view;
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == READ_PERMISSION) load();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private static final class Entry {
        final String title, location;
        final long start, end;
        final boolean allDay, utcDate;
        final Intent details;
        long providerId;
        boolean series;
        Entry(String title, String location, long start, long end, boolean allDay, boolean utcDate, Intent details) {
            this.title = title == null || title.isBlank() ? "未命名日程" : title;
            this.location = location == null ? "" : location;
            this.start = start; this.end = end; this.allDay = allDay; this.utcDate = utcDate; this.details = details;
        }
        String time() {
            ZoneId zone = allDay && utcDate ? ZoneOffset.UTC : ZoneId.systemDefault();
            DateTimeFormatter format = DateTimeFormatter.ofPattern(allDay ? "M月d日 E" : "M月d日 E HH:mm", Locale.CHINA).withZone(zone);
            String first = format.format(Instant.ofEpochMilli(start));
            String last = format.format(Instant.ofEpochMilli(allDay && end > start ? end - 1 : end));
            return first + (end <= start || allDay && first.equals(last) ? "" : " — " + last)
                    + (allDay ? " · 全天" : "") + (series ? " · 重复日程" : "");
        }
    }
}
