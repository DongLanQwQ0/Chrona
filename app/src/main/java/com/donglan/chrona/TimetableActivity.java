package com.donglan.chrona;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.database.Cursor;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import com.donglan.chrona.timetable.Timetable;
import com.donglan.chrona.timetable.AcademicTerms;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.TreeSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** A local, all-courses weekly overview. Imports never write to the system calendar or AI. */
public final class TimetableActivity extends Activity {
    private static final int PICK_ICS = 61;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm", Locale.CHINA);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA);

    private static final class Session {
        WeakReference<TimetableActivity> owner = new WeakReference<>(null);
        TimetableStore.Library library;
        TimetableStore.Document pending;
        String importYear;
        int importMode;
        LocalDate importStart, importSplit, importEnd;
        boolean initialized, busy;
        String error, operation;
        int scrollX, scrollY;
    }

    private Session session;
    private LinearLayout shell;
    private FrameLayout body;
    private TextView source;
    private Button importButton;
    private ScrollView vertical;
    private HorizontalScrollView horizontal;
    private Dialog floating, nested;
    private boolean resumed;
    private int gridWidth;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        session = getLastNonConfigurationInstance() instanceof Session retained ? retained : new Session();
        session.owner = new WeakReference<>(this);
        if (state != null) {
            session.scrollX = state.getInt("scroll_x");
            session.scrollY = state.getInt("scroll_y");
        }
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(20), dp(16), dp(20), dp(12));
        UiStyle.page(this, shell);
        shell.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton back = new ImageButton(this);
        back.setImageResource(R.drawable.ic_arrow_left);
        back.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        back.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        back.setContentDescription("返回");
        UiStyle.acrylicChoice(back, false, UiStyle.RADIUS_PILL, false);
        back.setPadding(dp(12), dp(12), dp(12), dp(12));
        UiStyle.pressable(back);
        back.setOnClickListener(view -> finish());
        header.addView(back, new LinearLayout.LayoutParams(dp(48), dp(48)));
        TextView title = label("课表", 26, true);
        title.setPadding(dp(12), 0, dp(8), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        importButton = new Button(this);
        importButton.setText("导入 ICS");
        UiStyle.button(importButton, false);
        importButton.setOnClickListener(view -> pickIcs());
        header.addView(importButton, new LinearLayout.LayoutParams(-2, dp(48)));
        UiStyle.addSpaced(shell, header, 0, 12);
        source = label("", 12, false);
        source.setMaxLines(2);
        source.setEllipsize(android.text.TextUtils.TruncateAt.END);
        UiStyle.addSpaced(shell, source, 0, 12);
        body = new FrameLayout(this);
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        stage.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.applyInsets(stage, shell);
        setContentView(stage);
        body.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left > 0 && gridWidth != right - left && session.library != null) {
                rememberScroll();
                render();
            }
        });
        render();
        if (!session.initialized && !session.busy) load();
    }

    private void load() {
        Session target = session;
        Context app = getApplicationContext();
        begin("读取课表…");
        IO.execute(() -> {
            TimetableStore.Library loaded = null;
            String error = null;
            try {
                TimetableStore store = new TimetableStore(app);
                loaded = store.load();
                TimetableStore.Semester chosen = loaded == null ? null : loaded.current(LocalDate.now());
                if (chosen != null && !chosen.id().equals(loaded.selected)) {
                    loaded = loaded.select(chosen.id());
                    store.save(loaded);
                }
            }
            catch (Exception exception) { error = message(exception); }
            TimetableStore.Library result = loaded;
            String failure = error;
            MAIN.post(() -> {
                target.initialized = true;
                target.busy = false;
                target.library = result;
                target.error = failure;
                notifyOwner(target);
            });
        });
    }

    private void pickIcs() {
        if (session.busy) return;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        picker.addCategory(Intent.CATEGORY_OPENABLE);
        // Several campus portals label .ics as text/plain or application/octet-stream.
        picker.setType("*/*");
        picker.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try { startActivityForResult(picker, PICK_ICS); }
        catch (android.content.ActivityNotFoundException exception) { Feedback.show(this, "没有可用的文件选择器"); }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_ICS || resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (session.busy) return;
        Uri uri = data.getData();
        Session target = session;
        Context app = getApplicationContext();
        ZoneId zone = ZoneId.systemDefault();
        begin("正在读取 ICS…");
        IO.execute(() -> {
            TimetableStore.Document imported = null;
            String error = null;
            try (InputStream input = app.getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("无法读取所选文件");
                imported = TimetableStore.prepare(displayName(app, uri), TimetableStore.readIcs(input), zone);
            } catch (Exception exception) { error = message(exception); }
            TimetableStore.Document result = imported;
            String failure = error;
            MAIN.post(() -> {
                target.busy = false;
                target.pending = result;
                if (result != null) {
                    target.importYear = String.valueOf(result.plan.year);
                    target.importMode = result.plan.mode;
                    target.importStart = result.plan.start;
                    target.importSplit = result.plan.split;
                    target.importEnd = result.plan.end.minusDays(1);
                }
                target.error = failure;
                notifyOwner(target);
            });
        });
    }

    private void confirmImport() {
        TimetableStore.Document pending = session.pending;
        if (pending == null || floating != null && floating.isShowing()) return;
        Dialog dialog = new Dialog(this);
        LinearLayout panel = panel("确认学期");
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        TextView filename = label(pending.name, 13, false);
        filename.setMaxLines(2);
        filename.setEllipsize(android.text.TextUtils.TruncateAt.END);
        UiStyle.addSpaced(form, filename, 0, 12);
        UiStyle.addSpaced(form, label("学年起始年份", 12, false), 0, 6);
        EditText year = new EditText(this);
        year.setSingleLine();
        year.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        year.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        UiStyle.input(year);
        year.setText(session.importYear);
        UiStyle.addSpaced(form, year, 0, 8);
        Button mode = action(AcademicTerms.MODES[session.importMode], false, () -> { });
        UiStyle.addSpaced(form, mode, 0, 8);
        Button start = action("开始 · " + DATE.format(session.importStart), false, () -> { });
        Button split = action("第二学期开始 · " + DATE.format(session.importSplit), false, () -> { });
        Button end = action("结束 · " + DATE.format(session.importEnd), false, () -> { });
        UiStyle.addSpaced(form, start, 0, 6);
        UiStyle.addSpaced(form, split, 0, 6);
        UiStyle.addSpaced(form, end, 0, 10);
        TextView preview = label("", 13, false);
        form.addView(preview);
        Runnable refresh = () -> {
            mode.setText(AcademicTerms.MODES[session.importMode]);
            start.setText("开始 · " + DATE.format(session.importStart));
            split.setText("第二学期开始 · " + DATE.format(session.importSplit));
            end.setText("结束 · " + DATE.format(session.importEnd));
            split.setVisibility(session.importMode < 2 ? View.VISIBLE : View.GONE);
            try {
                TimetableStore.Document candidate = pending.configured(importPlan());
                StringBuilder text = new StringBuilder();
                List<String> replacing = new ArrayList<>();
                for (int i = 0; i < candidate.plan.count(); i++) {
                    if (!candidate.enabled.contains(i)) continue;
                    AcademicTerms.View view = new AcademicTerms.View(candidate.plan.entries(candidate.table, i),
                            candidate.table.zone, Collections.emptyMap());
                    if (text.length() > 0) text.append('\n');
                    text.append(candidate.plan.label(i)).append(" · ").append(view.courseCount)
                            .append(" 门 · 特殊安排 ").append(view.special.size()).append(" 次");
                    if (session.library != null) for (TimetableStore.Semester saved : session.library.semesters)
                        if (saved.id().equals(candidate.plan.id(i))) replacing.add(candidate.plan.label(i));
                }
                text.append("\n日期为自动建议，可按校历调整");
                if (!replacing.isEmpty()) text.append("\n将替换同学年：").append(String.join("、", replacing));
                preview.setText(text.toString());
            } catch (RuntimeException error) { preview.setText("请检查学年和日期范围"); }
        };
        year.addTextChangedListener(new android.text.TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int count, int after) { }
            public void onTextChanged(CharSequence s, int st, int before, int count) {
                session.importYear = s.toString(); refresh.run();
            }
            public void afterTextChanged(android.text.Editable text) { }
        });
        mode.setOnClickListener(view -> chooseMode(refresh));
        start.setOnClickListener(view -> chooseDate("开始日期", session.importStart,
                value -> { session.importStart = value; refresh.run(); }));
        split.setOnClickListener(view -> chooseDate("第二学期开始", session.importSplit,
                value -> { session.importSplit = value; refresh.run(); }));
        end.setOnClickListener(view -> chooseDate("结束日期", session.importEnd,
                value -> { session.importEnd = value; refresh.run(); }));
        refresh.run();
        boundedContent(panel, form, .55f);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.END);
        Button cancel = action("取消", false, () -> {
            session.pending = null;
            dialog.dismiss();
        });
        actions.addView(cancel);
        Button save = action("确认导入", true, () -> {
            try {
                TimetableStore.Document candidate = pending.configured(importPlan());
                TimetableStore.Library current = session.library == null
                        ? new TimetableStore.Library(Collections.emptyList(), "") : session.library;
                TimetableStore.Library incoming = new TimetableStore.Library(Collections.singletonList(candidate), "");
                TimetableStore.Library merged = current.merge(candidate).select(incoming.current(LocalDate.now()).id());
                dialog.dismiss();
                save(merged);
            } catch (RuntimeException error) { Feedback.showLong(this, message(error)); }
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
        params.setMarginStart(dp(8));
        actions.addView(save, params);
        panel.addView(actions);
        dialog.setOnCancelListener(ignored -> session.pending = null);
        floating = dialog;
        UiStyle.showFloatingDialog(dialog, panel);
    }

    private AcademicTerms.Plan importPlan() {
        return new AcademicTerms.Plan(Integer.parseInt(session.importYear), session.importMode,
                session.importStart, session.importSplit, session.importEnd.plusDays(1));
    }

    private void chooseMode(Runnable changed) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = panel("学期");
        for (int i = 0; i < AcademicTerms.MODES.length; i++) {
            int mode = i;
            UiStyle.addSpaced(panel, action(AcademicTerms.MODES[i], i == session.importMode, () -> {
                session.importMode = mode; changed.run(); dialog.dismiss();
            }), 0, 6);
        }
        nested = dialog;
        UiStyle.showFloatingDialog(dialog, panel);
    }

    private void chooseDate(String title, LocalDate initial, java.util.function.Consumer<LocalDate> chosen) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = panel(title);
        GlassDateTimePickerView picker = new GlassDateTimePickerView(this, initial.atStartOfDay());
        picker.showTime(false);
        panel.addView(picker, new LinearLayout.LayoutParams(-1, dp(220)));
        Button confirm = action("确定", true, () -> { chosen.accept(picker.value().toLocalDate()); dialog.dismiss(); });
        UiStyle.addSpaced(panel, confirm, 12, 0);
        nested = dialog;
        UiStyle.showFloatingDialog(dialog, panel);
    }

    private void save(TimetableStore.Library library) {
        Session target = session;
        Context app = getApplicationContext();
        session.pending = null;
        begin("保存课表…");
        IO.execute(() -> {
            String error = null;
            try { new TimetableStore(app).save(library); }
            catch (Exception exception) { error = message(exception); }
            String failure = error;
            MAIN.post(() -> {
                target.busy = false;
                target.error = failure;
                if (failure == null) {
                    target.library = library;
                    target.scrollX = target.scrollY = 0;
                }
                notifyOwner(target);
            });
        });
    }

    private void begin(String operation) {
        rememberScroll();
        session.busy = true;
        session.operation = operation;
        session.error = null;
        render();
    }

    private void render() {
        if (isFinishing() || isDestroyed()) return;
        importButton.setEnabled(!session.busy);
        importButton.setText(session.busy ? "读取中…" : "导入 ICS");
        body.removeAllViews();
        vertical = null;
        horizontal = null;
        gridWidth = body.getWidth();
        TimetableStore.Semester term = currentTerm();
        if (term == null) {
            source.setVisibility(View.GONE);
            LinearLayout empty = new LinearLayout(this);
            empty.setOrientation(LinearLayout.VERTICAL);
            empty.setGravity(Gravity.CENTER);
            UiStyle.glass(empty);
            empty.setPadding(dp(24), dp(28), dp(24), dp(28));
            TextView heading = label(session.busy ? session.operation
                    : session.error == null ? "导入你的课表" : "未能读取课表", 21, true);
            heading.setGravity(Gravity.CENTER);
            UiStyle.addSpaced(empty, heading, 0, 12);
            TextView hint = label(session.busy ? "" : session.error == null
                    ? "选择 ICS 文件，查看一周课程" : session.error, 14, false);
            hint.setGravity(Gravity.CENTER);
            empty.addView(hint);
            if (!session.busy) {
                Button open = action("选择 ICS 文件", true, this::pickIcs);
                LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
                params.topMargin = dp(20);
                empty.addView(open, params);
            }
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.CENTER);
            body.addView(empty, params);
        } else {
            source.setVisibility(View.VISIBLE);
            source.setText(term.document.name + " · " + term.document.table.zone.getId());
            vertical = new ScrollView(this);
            vertical.setVerticalScrollBarEnabled(false);
            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            addSemesterControls(content, term);
            horizontal = new HorizontalScrollView(this);
            horizontal.setHorizontalScrollBarEnabled(false);
            horizontal.setFillViewport(true);
            UiStyle.glass(horizontal);
            horizontal.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline outline) {
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(UiStyle.RADIUS_PANEL));
                }
            });
            horizontal.setClipToOutline(true);
            int available = gridWidth > 0 ? gridWidth : getResources().getDisplayMetrics().widthPixels - dp(40);
            if (!term.view.regular.blocks.isEmpty()) {
                TimetableGrid grid = new TimetableGrid(this, term.view.regular, available, block -> showCourse(block, false));
                horizontal.addView(grid);
                UiStyle.addSpaced(content, horizontal, 0, 16);
            } else {
                TextView emptyGrid = label("本学期的安排均在下方展示", 14, false);
                emptyGrid.setPadding(dp(16), dp(20), dp(16), dp(20));
                UiStyle.glass(emptyGrid);
                UiStyle.addSpaced(content, emptyGrid, 0, 16);
            }
            addSpecialArrangements(content, term);
            vertical.addView(content, new ScrollView.LayoutParams(-1, -2));
            body.addView(vertical, new FrameLayout.LayoutParams(-1, -1));
            ScrollView scroll = vertical;
            HorizontalScrollView sideways = horizontal;
            int x = session.scrollX, y = session.scrollY;
            scroll.post(() -> { scroll.scrollTo(0, y); sideways.scrollTo(x, 0); });
            if (session.error != null && resumed) {
                Feedback.showLong(this, session.error);
                session.error = null;
            }
        }
        if (session.pending != null && resumed) confirmImport();
    }

    private TimetableStore.Semester currentTerm() {
        return session.library == null ? null : session.library.current(LocalDate.now());
    }

    private void addSemesterControls(LinearLayout parent, TimetableStore.Semester selected) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        UiStyle.glass(card);
        HorizontalScrollView groups = new HorizontalScrollView(this);
        groups.setHorizontalScrollBarEnabled(false);
        LinearLayout groupRow = new LinearLayout(this);
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (TimetableStore.Semester term : session.library.semesters) {
            AcademicTerms.Plan plan = term.document.plan;
            if (!seen.add(plan.group())) continue;
            TimetableStore.Semester target = term;
            for (TimetableStore.Semester other : session.library.semesters)
                if (other.document.plan.group().equals(plan.group())
                        && other.document.plan.firstSeason() + other.index < target.document.plan.firstSeason() + target.index)
                    target = other;
            String targetId = target.id();
            Button chip = action(plan.groupLabel(), plan.group().equals(selected.document.plan.group()),
                    () -> selectSemester(targetId));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            params.setMarginEnd(dp(6));
            groupRow.addView(chip, params);
        }
        groups.addView(groupRow);
        UiStyle.addSpaced(card, groups, 0, 8);
        LinearLayout seasons = new LinearLayout(this);
        List<TimetableStore.Semester> terms = new ArrayList<>();
        for (TimetableStore.Semester term : session.library.semesters)
            if (term.document.plan.group().equals(selected.document.plan.group())) terms.add(term);
        terms.sort(java.util.Comparator.comparing(TimetableStore.Semester::id));
        for (TimetableStore.Semester term : terms) {
            Button button = action(term.document.plan.label(term.index), term.id().equals(selected.id()),
                    () -> selectSemester(term.id()));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
            if (seasons.getChildCount() > 0) params.setMarginStart(dp(8));
            seasons.addView(button, params);
        }
        UiStyle.addSpaced(card, seasons, 0, 10);
        LinearLayout stats = new LinearLayout(this);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout summary = new LinearLayout(this);
        summary.setOrientation(LinearLayout.VERTICAL);
        TextView amount = label(selected.view.courseCount + " 门课程 · " + duration(selected.view.minutes), 16, true);
        amount.setTextColor(UiStyle.colors(this).primary);
        summary.addView(amount);
        TextView range = label(DATE.format(selected.document.plan.from(selected.index)) + "–"
                + DATE.format(selected.document.plan.until(selected.index).minusDays(1)), 11, false);
        UiStyle.addSpaced(summary, range, 6, 0);
        stats.addView(summary, new LinearLayout.LayoutParams(0, -2, 1));
        stats.addView(action("管理", false, () -> manageSemester(selected)), new LinearLayout.LayoutParams(-2, -2));
        card.addView(stats);
        UiStyle.addSpaced(parent, card, 0, 16);
    }

    private void selectSemester(String id) {
        if (session.busy || id.equals(session.library.selected)) return;
        save(session.library.select(id));
    }

    private static String duration(long minutes) {
        return minutes / 60 + " 小时" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + " 分");
    }

    private void addSpecialArrangements(LinearLayout parent, TimetableStore.Semester term) {
        if (term.view.special.isEmpty()) return;
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        UiStyle.glass(card);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        UiStyle.addSpaced(card, label("特殊安排 · " + term.view.special.size(), 18, true), 0, 12);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        card.addView(rows);
        final int pageSize = 20;
        int[] page = {0};
        Button previous = action("上一页", false, () -> { });
        Button next = action("下一页", false, () -> { });
        Runnable renderRows = () -> {
            rows.removeAllViews();
            int from = page[0] * pageSize, until = Math.min(term.view.special.size(), from + pageSize);
            for (int i = from; i < until; i++) {
                Timetable.Occurrence entry = term.view.special.get(i);
                TextView row = label(entry.title + "\n" + DATE.format(entry.start) + " · "
                        + TimetableGrid.DAYS[entry.start.getDayOfWeek().getValue() - 1] + " · "
                        + (entry.allDay ? "全天" : TIME.format(entry.start) + "–" + TIME.format(entry.end)), 14, false);
                row.setTextColor(UiStyle.colors(this).text);
                row.setMaxLines(3);
                row.setEllipsize(android.text.TextUtils.TruncateAt.END);
                UiStyle.acrylicChoice(row, false, UiStyle.RADIUS_CHIP, false);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                row.setMinHeight(dp(52));
                UiStyle.pressable(row);
                row.setOnClickListener(view -> {
                    if (session.busy) return;
                    Timetable single = new Timetable(Collections.singletonList(entry), term.document.table.zone, 1);
                    showCourse(single.blocks.get(0), true);
                });
                UiStyle.addSpaced(rows, row, 0, 8);
            }
            previous.setEnabled(page[0] > 0);
            next.setEnabled(until < term.view.special.size());
        };
        previous.setOnClickListener(view -> { page[0]--; renderRows.run(); });
        next.setOnClickListener(view -> { page[0]++; renderRows.run(); });
        if (term.view.special.size() > pageSize) {
            LinearLayout navigation = new LinearLayout(this);
            navigation.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
            navigation.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(navigation);
        }
        renderRows.run();
        parent.addView(card);
    }

    private void manageSemester(TimetableStore.Semester term) {
        if (session.busy) return;
        Dialog dialog = new Dialog(this);
        LinearLayout panel = panel(term.document.plan.groupLabel() + " · " + term.document.plan.label(term.index));
        UiStyle.addSpaced(panel, action("恢复自动分类", false, () -> {
            dialog.dismiss(); save(session.library.classify(term, term.view.all, null));
        }), 0, 8);
        UiStyle.addSpaced(panel, action("删除此学期", false, () -> {
            Dialog confirmation = new Dialog(this);
            LinearLayout confirmPanel = panel("删除此学期？");
            UiStyle.addSpaced(confirmPanel, label("其他学期会保留", 14, false), 0, 12);
            UiStyle.addSpaced(confirmPanel, action("删除", true, () -> {
                confirmation.dismiss(); dialog.dismiss(); save(session.library.remove(term));
            }), 0, 8);
            confirmPanel.addView(action("取消", false, confirmation::dismiss));
            nested = confirmation;
            UiStyle.showFloatingDialog(confirmation, confirmPanel);
        }), 0, 8);
        panel.addView(action("关闭", false, dialog::dismiss));
        floating = dialog;
        UiStyle.showFloatingDialog(dialog, panel);
    }

    private void showCourse(Timetable.Block block, boolean special) {
        if (session.busy) return;
        TimetableStore.Semester term = currentTerm();
        if (term == null) return;
        Dialog dialog = new Dialog(this);
        LinearLayout panel = panel(block.title);
        String when = TimetableGrid.DAYS[block.day] + " · " + (block.allDay ? "全天"
                : Timetable.time(block.startMinute) + "–" + Timetable.time(block.endMinute));
        TextView time = label(when, 16, true);
        time.setTextColor(UiStyle.colors(this).primary);
        UiStyle.addSpaced(panel, time, 0, 12);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout details = new LinearLayout(this);
        details.setOrientation(LinearLayout.VERTICAL);
        if (!block.location.isEmpty()) detail(details, "地点", block.location);
        LinkedHashSet<String> descriptions = new LinkedHashSet<>();
        for (Timetable.Occurrence entry : block.occurrences())
            if (!entry.description.isEmpty()) descriptions.add(entry.description);
        if (!descriptions.isEmpty()) detail(details, "课程信息", String.join("\n\n", descriptions));
        LinkedHashSet<String> rules = new LinkedHashSet<>();
        TreeSet<String> dates = new TreeSet<>();
        for (Timetable.Occurrence entry : block.occurrences()) {
            if (entry.openEnded) {
                rules.add(entry.recurrence);
            } else {
                String value = entry.allDay ? DATE.format(entry.start) : DATE.format(entry.start) + "  " + TIME.format(entry.start);
                value += entry.allDay ? (entry.end.toLocalDate().minusDays(1).isAfter(entry.start.toLocalDate())
                        ? "–" + DATE.format(entry.end.minusDays(1)) : "")
                        : "–" + (entry.start.toLocalDate().equals(entry.end.toLocalDate())
                                ? TIME.format(entry.end) : DATE_TIME.format(entry.end));
                dates.add(value);
            }
        }
        if (!rules.isEmpty()) {
            detail(details, "重复安排", String.join("\n", rules));
            detail(details, "开始日期", DATE.format(block.occurrences().get(0).start));
        }
        if (!dates.isEmpty()) {
            UiStyle.addSpaced(details, label("上课日期 · " + dates.size() + " 次", 12, false), 0, 5);
            java.util.ArrayList<String> rows = new java.util.ArrayList<>(dates);
            TextView dateList = label("", 15, false);
            dateList.setTextColor(UiStyle.colors(this).text);
            dateList.setTextIsSelectable(true);
            dateList.setLineSpacing(dp(4), 1);
            details.addView(dateList);
            final int pageSize = 64;
            int[] page = {0};
            Runnable updateDates = () -> {
                int first = page[0] * pageSize;
                dateList.setText(String.join("\n", rows.subList(first, Math.min(rows.size(), first + pageSize))));
            };
            updateDates.run();
            if (rows.size() > pageSize) {
                LinearLayout navigation = new LinearLayout(this);
                Button previous = action("上一页", false, () -> { });
                Button next = action("下一页", false, () -> { });
                Runnable updatePage = () -> {
                    updateDates.run();
                    previous.setEnabled(page[0] > 0);
                    next.setEnabled((page[0] + 1) * pageSize < rows.size());
                };
                previous.setOnClickListener(view -> { page[0]--; updatePage.run(); });
                next.setOnClickListener(view -> { page[0]++; updatePage.run(); });
                navigation.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
                navigation.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
                details.addView(navigation);
                updatePage.run();
            }
        }
        detail(details, "时区", term.document.table.zone.getId());
        scroll.addView(details);
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * .48f);
        details.measure(View.MeasureSpec.makeMeasureSpec(Math.max(dp(200), (int) (getResources().getDisplayMetrics().widthPixels * .9f) - dp(40)), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(maxHeight, details.getMeasuredHeight())));
        Button classify = action(special ? "放入课表" : "移至特殊安排", false, () -> {
            dialog.dismiss();
            save(session.library.classify(term, block.occurrences(), special));
        });
        UiStyle.addSpaced(panel, classify, 12, 0);
        Button close = action("关闭", false, dialog::dismiss);
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(-2, -2);
        closeParams.gravity = Gravity.END;
        closeParams.topMargin = dp(12);
        panel.addView(close, closeParams);
        floating = dialog;
        UiStyle.showFloatingDialog(dialog, panel);
    }

    private void detail(LinearLayout parent, String heading, String value) {
        UiStyle.addSpaced(parent, label(heading, 12, false), 0, 5);
        TextView text = label(value, 15, false);
        text.setTextColor(UiStyle.colors(this).text);
        text.setTextIsSelectable(true);
        text.setLineSpacing(dp(4), 1);
        UiStyle.addSpaced(parent, text, 0, 16);
    }

    private void boundedContent(LinearLayout panel, View content, float screenFraction) {
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * screenFraction);
        content.measure(View.MeasureSpec.makeMeasureSpec(Math.max(dp(200), (int)
                (getResources().getDisplayMetrics().widthPixels * .9f) - dp(40)), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(maxHeight, content.getMeasuredHeight())));
    }

    private LinearLayout panel(String title) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        UiStyle.glass(panel);
        panel.setPadding(dp(20), dp(20), dp(20), dp(20));
        TextView heading = label(title, 21, true);
        heading.setMaxLines(3);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        UiStyle.addSpaced(panel, heading, 0, 12);
        return panel;
    }
    private TextView label(String value, int size, boolean heading) {
        TextView label = new TextView(this);
        label.setText(value);
        label.setTextSize(size);
        label.setIncludeFontPadding(false);
        if (heading) UiStyle.title(label); else UiStyle.muted(label);
        return label;
    }
    private Button action(String title, boolean primary, Runnable action) {
        Button button = new Button(this);
        button.setText(title);
        UiStyle.button(button, primary);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private static String displayName(Context context, Uri uri) {
        try (Cursor cursor = context.getContentResolver().query(uri,
                new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) return cursor.getString(0);
        } catch (RuntimeException ignored) { /* The display name is optional metadata. */ }
        return "导入的课表";
    }
    private static String message(Exception exception) {
        if (exception instanceof SecurityException) return "无法读取文件，请重新选择 ICS";
        return exception.getMessage() == null ? "课表读取失败，请检查 ICS 文件" : exception.getMessage();
    }
    private static void notifyOwner(Session target) {
        TimetableActivity owner = target.owner.get();
        if (owner != null) owner.render();
    }
    private void rememberScroll() {
        if (vertical != null) session.scrollY = vertical.getScrollY();
        if (horizontal != null) session.scrollX = horizontal.getScrollX();
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        if (session.pending != null) confirmImport();
        if (session.error != null && session.library != null) {
            Feedback.showLong(this, session.error);
            session.error = null;
        }
    }
    @Override protected void onPause() { resumed = false; rememberScroll(); super.onPause(); }
    @Override public Object onRetainNonConfigurationInstance() { rememberScroll(); return session; }
    @Override protected void onSaveInstanceState(Bundle state) {
        rememberScroll();
        state.putInt("scroll_x", session.scrollX);
        state.putInt("scroll_y", session.scrollY);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        if (session.owner.get() == this) session.owner.clear();
        if (nested != null && nested.isShowing()) nested.dismiss();
        if (floating != null && floating.isShowing()) floating.dismiss();
        super.onDestroy();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
