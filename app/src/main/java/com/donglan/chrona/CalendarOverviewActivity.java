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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Inbox's calendar destination, using the same local occurrences as schedule detail. */
public final class CalendarOverviewActivity extends Activity {
    private static final int YEAR = 0, MONTH = 1, WEEK = 2, DAY = 3, AGENDA = 4;
    private static final int LIGHT_DAY_MAX = 2, MEDIUM_DAY_MAX = 5;
    private LocalDate date = LocalDate.now();
    private int mode = MONTH, generation;
    private boolean includeSystem;
    private String query = "";
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());
    private AgendaRepository.Snapshot snapshot = new AgendaRepository.Snapshot();
    private LinearLayout root;
    private DockNavigationLayout tabs;
    private android.animation.ValueAnimator tabMotion, bodyMotion;
    private View outgoingLayer;
    private boolean animateNext;
    private int transitionDirection;
    private FrameLayout body;
    private TextView dateTitle, sourceButton;
    private View timeLines;
    private boolean resumed;
    private boolean loading;
    private final Runnable minuteTick = new Runnable() {
        @Override public void run() {
            if (timeLines != null) timeLines.invalidate();
            if (resumed) handler.postDelayed(this, 60_000);
        }
    };
    private android.database.ContentObserver calendarObserver;
    private final Runnable calendarReload = this::load;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this); super.onCreate(state);
        includeSystem = getPreferences(MODE_PRIVATE).getBoolean("system", false);
        mode = getPreferences(MODE_PRIVATE).getInt("mode", MONTH);
        if (state != null) {
            date = LocalDate.parse(state.getString("date", date.toString()));
            mode = state.getInt("mode", mode); query = state.getString("query", "");
        }
        root = column(); root.setPadding(dp(20), dp(12), dp(20), 0); UiStyle.page(this, root);
        root.setFitsSystemWindows(false); root.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout header = row();
        header.addView(icon(R.drawable.ic_arrow_left, "返回", this::finish), size(44, 44));
        TextView name = text("日历", 24, true);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
        nameParams.setMargins(dp(12), 0, 0, 0); header.addView(name, nameParams);
        header.addView(icon(R.drawable.ic_add, "记录新日程", () -> startActivity(new Intent(this, MainActivity.class))), size(44, 44));
        root.addView(header);
        LinearLayout navigation = row();
        navigation.addView(icon(R.drawable.ic_chevron_left, "上一段日期", () -> shift(-1)), size(40, 44));
        dateTitle = text("", 22, true); dateTitle.setGravity(Gravity.CENTER);
        dateTitle.setTextColor(UiStyle.colors(this).primary); dateTitle.setOnClickListener(v -> pickDate());
        dateTitle.setContentDescription("选择日期");
        navigation.addView(dateTitle, new LinearLayout.LayoutParams(0, dp(48), 1));
        navigation.addView(icon(R.drawable.ic_chevron_right, "下一段日期", () -> shift(1)), size(40, 44));
        TextView today = action("今天", () -> navigateDate(LocalDate.now()));
        navigation.addView(today, new LinearLayout.LayoutParams(-2, dp(44))); root.addView(navigation);
        tabs = new DockNavigationLayout(this, this::changeMode, () -> { });
        tabs.setPadding(dp(3), dp(3), dp(3), dp(3));
        UiStyle.acrylicChoice(tabs, false, UiStyle.RADIUS_PILL, false);
        String[] labels = {"年", "月", "周", "日", "日程"};
        for (int i = 0; i < labels.length; i++) {
            int target = i;
            TextView tab = action(labels[i], () -> changeMode(target));
            tabs.addView(tab, new LinearLayout.LayoutParams(0, dp(42), 1));
        }
        tabs.setSelectedIndex(mode);
        UiStyle.addSpaced(root, tabs, 4, 10);
        LinearLayout filters = row();
        EditText search = new EditText(this); search.setSingleLine(); search.setHint("搜索日程");
        search.setTextSize(14); search.setTextColor(UiStyle.colors(this).text);
        search.setHintTextColor(UiStyle.colors(this).muted); search.setPadding(dp(12), 0, dp(12), 0);
        UiStyle.acrylicChoice(search, false, UiStyle.RADIUS_PILL, false); search.setText(query);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            public void onTextChanged(CharSequence s, int a, int b, int c) { }
            public void afterTextChanged(Editable s) { query = s.toString(); renderBody(); }
        });
        filters.addView(search, new LinearLayout.LayoutParams(0, dp(40), 1));
        sourceButton = action("", this::chooseSource); filters.addView(sourceButton, new LinearLayout.LayoutParams(-2, dp(44)));
        UiStyle.addSpaced(root, filters, 0, 8);
        body = new FrameLayout(this); root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        body.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public boolean onRequestSendAccessibilityEvent(android.view.ViewGroup host, View child,
                    android.view.accessibility.AccessibilityEvent event) {
                if (event.getEventType() == android.view.accessibility.AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED)
                    finishBodyMotion();
                return super.onRequestSendAccessibilityEvent(host, child, event);
            }
        });
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(root, new FrameLayout.LayoutParams(-1, -1)); UiStyle.applyInsets(stage, root); setContentView(stage);
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true; load(); handler.post(minuteTick);
        if (new com.donglan.chrona.calendar.CalendarStore(this).hasReadPermission()) {
            calendarObserver = new android.database.ContentObserver(handler) {
                @Override public void onChange(boolean selfChange) {
                    handler.removeCallbacks(calendarReload); handler.postDelayed(calendarReload, 400);
                }
            };
            try { getContentResolver().registerContentObserver(android.provider.CalendarContract.CONTENT_URI, true, calendarObserver); }
            catch (SecurityException ignored) { calendarObserver = null; }
        }
    }
    @Override protected void onPause() {
        resumed = false; handler.removeCallbacks(minuteTick); handler.removeCallbacks(calendarReload);
        finishTabMotion(); finishBodyMotion(); animateNext = false;
        if (calendarObserver != null) { getContentResolver().unregisterContentObserver(calendarObserver); calendarObserver = null; }
        super.onPause();
    }
    @Override protected void onDestroy() { generation++; reader.shutdownNow(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state); state.putString("date", date.toString()); state.putInt("mode", mode); state.putString("query", query);
    }

    private void chooseSource() {
        UiStyle.choiceDialog(this, "日历来源", new String[]{"仅拾时日程", "拾时与系统其他日程"}, includeSystem ? 1 : 0, choice -> {
            if (choice == 1 && !new com.donglan.chrona.calendar.CalendarStore(this).hasReadPermission()) {
                requestPermissions(new String[]{android.Manifest.permission.READ_CALENDAR}, 810); return;
            }
            includeSystem = choice == 1; getPreferences(MODE_PRIVATE).edit().putBoolean("system", includeSystem).apply(); load();
        });
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request == 810) {
            includeSystem = results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED;
            getPreferences(MODE_PRIVATE).edit().putBoolean("system", includeSystem).apply(); load();
            if (!includeSystem) Feedback.show(this, "未获得读取日历权限");
        }
    }
    private void pickDate() {
        DatePickerDialog dialog = new DatePickerDialog(this, (picker, year, month, day) -> {
            navigateDate(LocalDate.of(year, month + 1, day));
        }, date.getYear(), date.getMonthValue() - 1, date.getDayOfMonth()); dialog.show();
    }
    private void shift(int direction) {
        date = mode == YEAR ? date.plusYears(direction) : mode == WEEK ? date.plusWeeks(direction)
                : mode == DAY ? date.plusDays(direction) : date.plusMonths(direction);
        animatedLoad(direction);
    }

    private void navigateDate(LocalDate target) {
        int direction = target.compareTo(date);
        date = target; animatedLoad(Integer.signum(direction));
    }

    private void changeMode(int target) {
        if (mode == target) return;
        int direction = Integer.compare(target, mode);
        mode = target;
        getPreferences(MODE_PRIVATE).edit().putInt("mode", mode).apply();
        moveTabHighlight(); animatedLoad(direction);
    }

    private void moveTabHighlight() {
        android.animation.ValueAnimator old = tabMotion; tabMotion = null;
        if (old != null) old.cancel();
        if (!resumed || !UiMotion.isEnabled()) { tabs.finishPageSelection(mode); return; }
        tabs.beginPageSettle(mode);
        android.animation.ValueAnimator motion = android.animation.ValueAnimator.ofFloat(0f, 1f);
        tabMotion = motion; motion.setDuration(UiMotion.ENTER); motion.setInterpolator(UiMotion.SETTLE);
        motion.addUpdateListener(value -> {
            if (!UiMotion.isEnabled()) { finishTabMotion(); return; }
            tabs.updatePageSettle((float) value.getAnimatedValue());
        });
        motion.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (tabMotion != motion) return; tabMotion = null; tabs.finishPageSelection(mode);
            }
        });
        motion.start();
    }

    private void finishTabMotion() {
        android.animation.ValueAnimator old = tabMotion; tabMotion = null;
        if (old != null) old.cancel();
        tabs.finishPageSelection(mode);
    }

    private void animatedLoad(int direction) {
        animateNext = resumed; transitionDirection = direction; load();
    }

    private void finishBodyMotion() {
        android.animation.ValueAnimator old = bodyMotion; bodyMotion = null;
        if (old != null) old.cancel();
        if (outgoingLayer != null) { body.removeView(outgoingLayer); outgoingLayer = null; }
        if (body.getChildCount() > 0) {
            View current = body.getChildAt(body.getChildCount() - 1);
            current.setAlpha(1f); current.setTranslationX(0f); current.setTranslationY(0f);
        }
    }

    private void presentBody(View previous, boolean animated) {
        if (body.getChildCount() == 0) return;
        View current = body.getChildAt(body.getChildCount() - 1);
        if (!animated || previous == null || !resumed || !UiMotion.isEnabled() || previous.hasFocus()
                || hasAccessibilityFocus(previous)) return;
        FrameLayout outgoing = new FrameLayout(this) {
            @Override public boolean onInterceptTouchEvent(android.view.MotionEvent event) { return true; }
            @Override public boolean onTouchEvent(android.view.MotionEvent event) { return true; }
        };
        outgoing.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        outgoing.addView(previous, new FrameLayout.LayoutParams(-1, -1));
        body.addView(outgoing, 0, new FrameLayout.LayoutParams(-1, -1)); outgoingLayer = outgoing;
        float distance = dp(18) * transitionDirection;
        current.setAlpha(0f); current.setTranslationX(distance);
        android.animation.ValueAnimator motion = android.animation.ValueAnimator.ofFloat(0f, 1f);
        bodyMotion = motion; motion.setDuration(UiMotion.ENTER); motion.setInterpolator(UiMotion.SETTLE);
        motion.addUpdateListener(value -> {
            if (!UiMotion.isEnabled() || current.hasFocus() || current.isAccessibilityFocused()) { finishBodyMotion(); return; }
            float progress = (float) value.getAnimatedValue();
            current.setAlpha(progress); current.setTranslationX(distance * (1f - progress));
            outgoing.setAlpha(1f - progress); outgoing.setTranslationX(-distance * progress * .5f);
        });
        motion.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (bodyMotion == motion) finishBodyMotion();
            }
        });
        motion.start();
    }

    private boolean hasAccessibilityFocus(View view) {
        if (view.isAccessibilityFocused()) return true;
        if (view instanceof android.view.ViewGroup group)
            for (int i = 0; i < group.getChildCount(); i++) if (hasAccessibilityFocus(group.getChildAt(i))) return true;
        return false;
    }
    private LocalDate windowStart() {
        if (mode == YEAR) return date.withDayOfYear(1);
        if (mode == WEEK) return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        if (mode == DAY) return date;
        return date.withDayOfMonth(1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
    private void load() {
        if (body == null || isDestroyed()) return;
        int token = ++generation;
        loading = true;
        LocalDate first = windowStart();
        LocalDate last = mode == YEAR ? first.plusYears(1) : mode == WEEK ? first.plusWeeks(1)
                : mode == DAY ? first.plusDays(1) : first.plusDays(42);
        ZoneId zone = ZoneId.systemDefault();
        boolean system = includeSystem;
        dateTitle.setText(mode == YEAR ? date.getYear() + "" : date.format(DateTimeFormatter.ofPattern(mode == DAY ? "yyyy / M / d" : "yyyy / M")));
        sourceButton.setText(system ? "全部来源" : "拾时");
        if (tabMotion == null) tabs.finishPageSelection(mode);
        if (body.getChildCount() == 0) body.addView(text("正在读取日程…", 14, false));
        reader.execute(() -> {
            try {
                AgendaRepository.Snapshot loaded = AgendaRepository.load(getApplicationContext(),
                        first.atStartOfDay(zone).toInstant().toEpochMilli(), last.atStartOfDay(zone).toInstant().toEpochMilli(), system);
                handler.post(() -> {
                    if (isDestroyed() || token != generation) return; loading = false; snapshot = loaded; renderBody();
                });
            } catch (RuntimeException exception) {
                handler.post(() -> {
                    if (isDestroyed() || token != generation) return;
                    loading = false;
                    finishBodyMotion(); animateNext = false;
                    body.removeAllViews(); body.addView(action("读取失败，点击重试", this::load));
                });
            }
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
        finishBodyMotion();
        View previous = body.getChildCount() == 0 ? null : body.getChildAt(body.getChildCount() - 1);
        boolean animated = animateNext; animateNext = false;
        body.removeAllViews(); timeLines = null;
        List<AgendaEntry> entries = matching();
        if (mode == WEEK || mode == DAY) { timeline(entries); presentBody(previous, animated); return; }
        ScrollView scroll = new ScrollView(this); scroll.setVerticalScrollBarEnabled(false);
        LinearLayout content = column(); content.setPadding(0, 0, 0, dp(24)); scroll.addView(content);
        if (snapshot.systemUnavailable) notice(content, "系统日程暂时无法读取");
        if (snapshot.truncated) notice(content, "日程数量较多，请缩小日期范围");
        if (mode == YEAR) {
            for (int pair = 0; pair < 6; pair++) {
                LinearLayout months = row(); months.setGravity(Gravity.TOP);
                for (int offset = 0; offset < 2; offset++) {
                    LocalDate month = LocalDate.of(date.getYear(), pair * 2 + offset + 1, 1);
                    LinearLayout tile = column(); tile.setPadding(dp(8), dp(10), dp(8), dp(10)); UiStyle.glass(tile);
                    TextView label = text(month.getMonthValue() + "月", 17, true); label.setTextColor(UiStyle.colors(this).primary); tile.addView(label);
                    tile.addView(monthGrid(month, entries, true));
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1); params.setMargins(0, 0, offset == 0 ? dp(8) : 0, 0);
                    months.addView(tile, params);
                }
                UiStyle.addSpaced(content, months, 0, 8);
            }
        } else if (mode == MONTH) {
            content.addView(monthGrid(date.withDayOfMonth(1), entries, false));
            TextView selected = text(date.format(DateTimeFormatter.ofPattern("M月d日 EEEE", Locale.CHINA)), 17, true);
            UiStyle.addSpaced(content, selected, 16, 8); dayList(content, entries, date);
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
        presentBody(previous, animated);
    }
    private View monthGrid(LocalDate month, List<AgendaEntry> entries, boolean mini) {
        LinearLayout grid = column();
        LinearLayout week = row(); String[] days = {"一", "二", "三", "四", "五", "六", "日"};
        for (String day : days) { TextView label = text(day, mini ? 10 : 13, false); label.setGravity(Gravity.CENTER); week.addView(label, new LinearLayout.LayoutParams(0, dp(mini ? 24 : 32), 1)); }
        grid.addView(week);
        LocalDate first = month.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (int r = 0; r < 6; r++) {
            LinearLayout line = row();
            for (int c = 0; c < 7; c++) {
                LocalDate day = first.plusDays(r * 7L + c);
                boolean ownMonth = day.getMonth() == month.getMonth();
                int count = onDay(entries, day).size();
                FrameLayout cell = new FrameLayout(this);
                TextView number = text("" + day.getDayOfMonth(), mini ? 12 : 18, day.equals(LocalDate.now()));
                number.setGravity(Gravity.CENTER);
                number.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                if (!ownMonth) number.setTextColor(UiStyle.colors(this).muted);
                else if (day.equals(date) || day.equals(LocalDate.now())) number.setTextColor(UiStyle.colors(this).primary);
                FrameLayout.LayoutParams numberParams = new FrameLayout.LayoutParams(-1, -1);
                numberParams.bottomMargin = dp(mini ? 3 : 9); cell.addView(number, numberParams);
                if (!mini) {
                    UiStyle.acrylicChoice(cell, day.equals(date) && ownMonth, 15, day.equals(LocalDate.now()) && ownMonth);
                    cell.setAlpha(ownMonth ? 1f : .55f);
                }
                if (count > 0) {
                    View marker = new View(this) {
                        final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                        @Override protected void onDraw(Canvas canvas) {
                            int color = count <= LIGHT_DAY_MAX ? UiStyle.colors(CalendarOverviewActivity.this).primary
                                    : count <= MEDIUM_DAY_MAX ? (ThemeStore.dark(CalendarOverviewActivity.this) ? 0xFFE8BD79 : 0xFF9B742C)
                                    : (ThemeStore.dark(CalendarOverviewActivity.this) ? 0xFFF2A69B : 0xFFAA6058);
                            paint.setColor(color); paint.setAlpha(ownMonth ? 170 : 110);
                            if (mini) canvas.drawCircle(getWidth() / 2f, getHeight() / 2f, dp(1), paint);
                            else {
                                String label = count > 99 ? "99+" : "" + count;
                                paint.setTextSize(dp(10)); float textWidth = paint.measureText(label);
                                float left = (getWidth() - textWidth - dp(7)) / 2f;
                                canvas.drawCircle(left + dp(1), getHeight() / 2f, dp(1), paint);
                                canvas.drawText(label, left + dp(7), (getHeight() - paint.ascent() - paint.descent()) / 2f, paint);
                            }
                        }
                    };
                    marker.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
                    cell.addView(marker, new FrameLayout.LayoutParams(-1, dp(mini ? 4 : 13), Gravity.BOTTOM));
                }
                cell.setContentDescription(day + "，" + count + "项日程");
                cell.setFocusable(true);
                cell.setOnClickListener(v -> {
                    if (mini) { date = day; changeMode(MONTH); }
                    else if (!ownMonth) navigateDate(day);
                    else { date = day; animateNext = resumed; transitionDirection = 0; renderBody(); }
                });
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, dp(mini ? 25 : 56), 1);
                if (!mini) cellParams.setMargins(dp(2), dp(2), dp(2), dp(2));
                line.addView(cell, cellParams);
            }
            grid.addView(line);
        }
        return grid;
    }
    private List<AgendaEntry> onDay(List<AgendaEntry> entries, LocalDate day) {
        ZoneId zone = ZoneId.systemDefault();
        long begin = day.atStartOfDay(zone).toInstant().toEpochMilli(), end = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();
        List<AgendaEntry> matches = new ArrayList<>();
        for (AgendaEntry e : entries) if (e.overlaps(begin, end)) matches.add(e);
        return matches;
    }
    private void dayList(LinearLayout content, List<AgendaEntry> entries, LocalDate day) {
        List<AgendaEntry> items = onDay(entries, day);
        if (items.isEmpty()) { notice(content, "当天暂无日程"); return; }
        LinearLayout group = column(); UiStyle.glass(group);
        long now = System.currentTimeMillis();
        for (AgendaEntry e : items) {
            LinearLayout card = column(); card.setPadding(dp(16), dp(14), dp(16), dp(14));
            TextView title = text(e.title, 17, true); title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
            if (e.finish() <= now) title.setTextColor(UiStyle.colors(this).muted); card.addView(title);
            String status = e.finish() <= now ? "已结束" : e.start <= now ? "现在" : e.start - now <= 24 * 60 * 60_000L ? "" + Math.max(1, (e.start - now) / 60_000) + "分钟后开始" : "";
            TextView time = text(time(e) + (status.isEmpty() ? "" : "  ·  " + status), 13, false);
            time.setTextColor(e.start <= now && e.finish() > now ? UiStyle.colors(this).primary : UiStyle.colors(this).muted);
            UiStyle.addSpaced(card, time, 6, 0);
            if (!e.location.trim().isEmpty()) { TextView place = text(e.location, 13, false); UiStyle.muted(place); UiStyle.addSpaced(card, place, 6, 0); }
            TextView source = text(e.source + (e.confirmed ? "" : " · 待确认"), 11, false); UiStyle.muted(source); UiStyle.addSpaced(card, source, 5, 0);
            card.setOnClickListener(v -> open(e)); UiStyle.pressable(card); group.addView(card);
            if (e != items.get(items.size() - 1)) { View divider = new View(this); divider.setBackgroundColor(UiStyle.colors(this).outline); group.addView(divider, new LinearLayout.LayoutParams(-1, dp(1))); }
        }
        content.addView(group);
    }
    private String time(AgendaEntry e) {
        if (e.allDay) return "全天";
        ZoneId zone = ZoneId.systemDefault(); DateTimeFormatter clock = DateTimeFormatter.ofPattern("HH:mm");
        boolean cross = !Instant.ofEpochMilli(e.start).atZone(zone).toLocalDate().equals(Instant.ofEpochMilli(e.end).atZone(zone).toLocalDate());
        return (cross ? DateTimeFormatter.ofPattern("M/d HH:mm") : clock).format(Instant.ofEpochMilli(e.start).atZone(zone))
                + " — " + (cross ? DateTimeFormatter.ofPattern("M/d HH:mm") : clock).format(Instant.ofEpochMilli(e.end).atZone(zone));
    }
    private void timeline(List<AgendaEntry> entries) {
        int days = mode == WEEK ? 7 : 1;
        LocalDate first = windowStart(); ZoneId zone = ZoneId.systemDefault();
        int viewport = Math.max(dp(240), body.getWidth() > 0 ? body.getWidth() : getResources().getDisplayMetrics().widthPixels - dp(40));
        int gutter = dp(40), width = Math.max(viewport, days == 7 ? dp(670) : viewport), dayWidth = (width - gutter) / days;
        HorizontalScrollView horizontal = new HorizontalScrollView(this); horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout layout = column(); horizontal.addView(layout, new FrameLayout.LayoutParams(width, -1));
        LinearLayout dates = row(); View space = new View(this); dates.addView(space, new LinearLayout.LayoutParams(gutter, dp(60)));
        for (int d = 0; d < days; d++) {
            LocalDate day = first.plusDays(d);
            TextView label = action(day.format(DateTimeFormatter.ofPattern("E\nd", Locale.CHINA)), () -> {
                date = day; changeMode(DAY);
            });
            if (day.equals(LocalDate.now())) UiStyle.acrylicChoice(label, true, 16, false);
            dates.addView(label, new LinearLayout.LayoutParams(dayWidth, dp(60)));
        }
        layout.addView(dates);
        if (snapshot.systemUnavailable || snapshot.truncated) notice(layout, snapshot.systemUnavailable ? "系统日程暂时无法读取" : "请缩小日期范围");
        LinearLayout allDays = row(); TextView allLabel = text("全天", 11, false); UiStyle.muted(allLabel); allDays.addView(allLabel, new LinearLayout.LayoutParams(gutter, -2));
        boolean hasAllDay = false;
        for (int d = 0; d < days; d++) {
            LinearLayout column = column();
            for (AgendaEntry e : onDay(entries, first.plusDays(d))) if (e.allDay) {
                hasAllDay = true; TextView card = action(e.title, () -> open(e)); card.setTextSize(11); card.setMaxLines(2);
                UiStyle.acrylicChoice(card, true, 8, false); column.addView(card, new LinearLayout.LayoutParams(-1, -2));
            }
            allDays.addView(column, new LinearLayout.LayoutParams(dayWidth, -2));
        }
        if (hasAllDay) layout.addView(allDays);
        ScrollView vertical = new ScrollView(this); vertical.setVerticalScrollBarEnabled(false);
        FrameLayout grid = new FrameLayout(this); int hourHeight = dp(72), height = hourHeight * 24;
        timeLines = new View(this) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                paint.setStrokeWidth(dp(1)); paint.setTextSize(dp(11));
                for (int hour = 0; hour <= 24; hour++) {
                    float y = hour * hourHeight; paint.setColor(UiStyle.colors(CalendarOverviewActivity.this).outline);
                    canvas.drawLine(gutter, y, width, y, paint);
                    if (hour < 24) { paint.setColor(UiStyle.colors(CalendarOverviewActivity.this).muted); canvas.drawText(String.format(Locale.ROOT, "%02d", hour), dp(4), y + dp(13), paint); }
                }
                paint.setColor(UiStyle.colors(CalendarOverviewActivity.this).outline);
                for (int d = 0; d <= days; d++) canvas.drawLine(gutter + d * dayWidth, 0, gutter + d * dayWidth, height, paint);
            }
        };
        grid.addView(timeLines, new FrameLayout.LayoutParams(-1, -1));
        for (int d = 0; d < days; d++) for (CalendarTimelineLayout.Block block : CalendarTimelineLayout.day(entries, first.plusDays(d), zone)) {
            AgendaEntry e = block.entry;
            TextView card = text(e.title + "\n" + time(e) + (e.location.trim().isEmpty() ? "" : "\n" + e.location), days == 1 ? 14 : 12, true);
            card.setPadding(dp(7), dp(4), dp(4), dp(3)); card.setGravity(Gravity.TOP); card.setEllipsize(android.text.TextUtils.TruncateAt.END);
            card.setTextColor(UiStyle.colors(this).text); UiStyle.acrylicChoice(card, true, 8, false);
            if (e.system) card.setAlpha(.78f);
            int cardHeight = Math.max(dp(18), (int) ((block.endMinute - block.startMinute) / 60 * hourHeight) - dp(2));
            card.setMaxLines(Math.max(1, cardHeight / dp(days == 1 ? 18 : 16)));
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(Math.max(dp(12), dayWidth / block.lanes - dp(4)), cardHeight);
            params.leftMargin = gutter + d * dayWidth + block.lane * dayWidth / block.lanes + dp(2);
            params.topMargin = (int) (block.startMinute / 60 * hourHeight) + dp(1);
            card.setContentDescription(e.title + "，" + time(e) + "，" + e.location + "，" + e.source);
            card.setOnClickListener(v -> open(e)); grid.addView(card, params);
        }
        View nowLine = new View(this) {
            final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                LocalDate today = LocalDate.now(); long index = java.time.temporal.ChronoUnit.DAYS.between(first, today);
                if (index < 0 || index >= days) return;
                float y = (float) (CalendarTimelineLayout.minute(System.currentTimeMillis(), zone) / 60 * hourHeight);
                paint.setColor(UiStyle.colors(CalendarOverviewActivity.this).primary); paint.setStrokeWidth(dp(2));
                float left = gutter + index * dayWidth;
                canvas.drawLine(left, y, left + dayWidth, y, paint); canvas.drawCircle(left + dp(3), y, dp(3), paint);
            }
        };
        nowLine.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        grid.addView(nowLine, new FrameLayout.LayoutParams(-1, -1)); timeLines = nowLine;
        vertical.addView(grid, new ScrollView.LayoutParams(width, height));
        layout.addView(vertical, new LinearLayout.LayoutParams(-1, 0, 1));
        body.addView(horizontal, new FrameLayout.LayoutParams(-1, -1));
        vertical.post(() -> {
            int hour = first.isAfter(LocalDate.now()) || first.plusDays(days).isBefore(LocalDate.now()) ? 8
                    : Math.max(0, Instant.now().atZone(zone).getHour() - 1);
            vertical.scrollTo(0, hour * hourHeight);
        });
    }
    private void open(AgendaEntry entry) {
        try { startActivity(AgendaRepository.details(this, entry)); }
        catch (android.content.ActivityNotFoundException exception) { Feedback.show(this, "没有可打开此日程的日历应用"); }
    }
    private TextView action(String title, Runnable click) {
        TextView view = text(title, 14, true); view.setGravity(Gravity.CENTER); view.setMinHeight(dp(44)); view.setPadding(dp(10), 0, dp(10), 0);
        view.setTextColor(UiStyle.colors(this).primary); view.setOnClickListener(v -> click.run()); UiStyle.pressable(view); return view;
    }
    private ImageButton icon(int resource, String description, Runnable click) {
        ImageButton button = new ImageButton(this); button.setImageResource(resource); button.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        button.setPadding(dp(11), dp(11), dp(11), dp(11)); button.setContentDescription(description);
        UiStyle.acrylicChoice(button, false, UiStyle.RADIUS_PILL, false); button.setOnClickListener(v -> click.run()); return button;
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
