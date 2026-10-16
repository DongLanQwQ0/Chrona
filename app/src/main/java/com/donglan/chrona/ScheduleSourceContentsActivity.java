package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.ContentUris;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.view.Gravity;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

/** A bounded, read-only source browser. Source inclusion switches do not filter this page. */
public final class ScheduleSourceContentsActivity extends Activity {
    private static final int LIMIT = 300;
    private static final int READ_PERMISSION = 761;
    private String source;
    private LocalDate week;
    private LinearLayout rows;
    private TextView range;
    private int generation;

    static Intent intent(Context context, String source, String name) {
        return new Intent(context, ScheduleSourceContentsActivity.class)
                .putExtra("source", source).putExtra("source_name", name);
    }

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state);
        source = getIntent().getStringExtra("source");
        if (hiddenZju()) { finish(); return; }
        week = state != null && state.containsKey("week") ? LocalDate.ofEpochDay(state.getLong("week"))
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LinearLayout root = SettingsPageLayout.content(this);
        String name = getIntent().getStringExtra("source_name");
        SettingsPageLayout.header(this, root, name == null ? "来源内容" : name);
        LinearLayout navigation = new LinearLayout(this); navigation.setGravity(Gravity.CENTER_VERTICAL);
        Button previous = navigationButton("上周", () -> changeWeek(-1));
        navigation.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
        Button current = navigationButton("本周", () -> {
            week = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); load();
        });
        LinearLayout.LayoutParams middle = new LinearLayout.LayoutParams(0, -2, 1);
        middle.setMargins(dp(8), 0, dp(8), 0); navigation.addView(current, middle);
        navigation.addView(navigationButton("下周", () -> changeWeek(1)), new LinearLayout.LayoutParams(0, -2, 1));
        UiStyle.addSpaced(root, navigation, 0, 12);
        range = text(root, "", 13); UiStyle.muted(range);
        rows = new LinearLayout(this); rows.setOrientation(LinearLayout.VERTICAL);
        UiStyle.addSpaced(root, rows, 12, 0);
        SettingsPageLayout.show(this, root, state == null);
    }

    @Override protected void onResume() { super.onResume(); if (!isFinishing()) load(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putLong("week", week.toEpochDay()); super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() { generation++; super.onDestroy(); }
    private void changeWeek(int direction) { week = week.plusWeeks(direction); load(); }
    private boolean hiddenZju() { return "zju".equals(source) && !new ZjuSettingsStore(this).enabled(); }

    private void load() {
        int request = ++generation;
        rows.removeAllViews();
        if (hiddenZju()) { finish(); return; }
        DateTimeFormatter day = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA);
        range.setText(day.format(week) + " — " + day.format(week.plusDays(6)));
        ZoneId zone = ZoneId.systemDefault();
        long begin = week.atStartOfDay(zone).toInstant().toEpochMilli();
        long end = week.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli();
        if (source != null && source.startsWith("system:") &&
                checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            Button permission = navigationButton("允许读取系统日历", () -> requestPermissions(
                    new String[]{Manifest.permission.READ_CALENDAR}, READ_PERMISSION));
            rows.addView(permission, new LinearLayout.LayoutParams(-1, -2)); return;
        }
        text(rows, "正在读取日程…", 16);
        new Thread(() -> {
            try {
                List<Entry> result = read(begin, end, zone);
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != request) return;
                    rows.removeAllViews();
                    if (hiddenZju()) { finish(); return; }
                    if (result.isEmpty()) text(rows, "本周暂无日程", 16);
                    for (int i = 0; i < Math.min(LIMIT, result.size()); i++) addEntry(result.get(i));
                    if (result.size() > LIMIT) text(rows, "本周日程较多，已显示前 " + LIMIT + " 条", 13);
                });
            } catch (Exception failure) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed() || generation != request) return;
                    rows.removeAllViews();
                    if (hiddenZju()) { finish(); return; }
                    Button retry = navigationButton("读取日程失败 · 重试", this::load);
                    rows.addView(retry, new LinearLayout.LayoutParams(-1, -2));
                });
            }
        }, "chrona-source-contents").start();
    }

    private List<Entry> read(long begin, long end, ZoneId zone) throws Exception {
        List<Entry> result = new ArrayList<>();
        if ("imported".equals(source)) {
            for (CourseAgenda.Item item : CourseAgenda.load(this, begin, end, zone)) {
                result.add(new Entry(item.title, item.location, item.start, item.end, item.allDay, false,
                        CourseAgenda.intent(this, item)));
                if (result.size() > LIMIT) break;
            }
        } else if ("zju".equals(source)) {
            ZjuSettingsStore settings = new ZjuSettingsStore(this);
            if (!settings.enabled()) return result;
            for (ZjuSnapshot.Lesson item : new ZjuTimetableStore(this).load(settings.account())) {
                if (item.start >= end || item.end <= begin) continue;
                result.add(new Entry(item.title, item.location, item.start, item.end, false, false,
                        ExternalCourseDetailActivity.sourceIntent(this, "zju", item.key, item.start, item.end)));
            }
        } else if (source != null && source.startsWith("system:")) {
            long id = Long.parseLong(source.substring(7));
            android.net.Uri.Builder uri = CalendarContract.Instances.CONTENT_URI.buildUpon();
            ContentUris.appendId(uri, begin); ContentUris.appendId(uri, end);
            String[] projection = {CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.BEGIN,
                    CalendarContract.Instances.END, CalendarContract.Instances.TITLE,
                    CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.EVENT_LOCATION,
                    CalendarContract.Instances.CALENDAR_DISPLAY_NAME};
            try (Cursor cursor = getContentResolver().query(uri.build(), projection,
                    CalendarContract.Instances.CALENDAR_ID + " = ?", new String[]{Long.toString(id)},
                    CalendarContract.Instances.BEGIN + " ASC")) {
                if (cursor == null) throw new IllegalStateException("No calendar cursor");
                while (cursor.moveToNext()) {
                    long event = cursor.getLong(0), start = cursor.getLong(1), finish = cursor.getLong(2);
                    String title = cursor.getString(3), location = cursor.getString(5);
                    boolean allDay = cursor.getInt(4) != 0;
                    result.add(new Entry(title, location, start, finish, allDay, true,
                            SystemAgendaDetailActivity.intent(this, event, start, finish, allDay,
                                    title, location, cursor.getString(6))));
                    if (result.size() > LIMIT) break;
                }
            }
        } else throw new IllegalArgumentException("Unknown source");
        result.sort(Comparator.comparingLong(item -> item.start));
        if (result.size() > LIMIT + 1) return new ArrayList<>(result.subList(0, LIMIT + 1));
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
        card.setOnClickListener(view -> { if (!hiddenZju()) startActivity(item.details); });
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
            return first + (allDay && first.equals(last) ? "" : " — " + last) + (allDay ? " · 全天" : "");
        }
    }
}
