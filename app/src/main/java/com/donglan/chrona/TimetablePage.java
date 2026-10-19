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
import com.donglan.chrona.zju.ZjuTimetableView;
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
final class TimetablePage extends SchedulePage {
    TimetablePage(ScheduleWorkspaceActivity activity, Object retained) {
        super(activity, "TimetableActivity", retained);
    }
    private static final int PICK_ICS = 61;
    private static final ExecutorService IO = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd", Locale.CHINA);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("MM.dd HH:mm", Locale.CHINA);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.CHINA);

    private static final class Session {
        WeakReference<TimetablePage> owner = new WeakReference<>(null);
        TimetableStore.Library library;
        TimetableStore.Document pending;
        String importYear;
        int importMode;
        LocalDate importStart, importSplit, importEnd;
        boolean initialized, busy, courseOpened;
        String error, operation;
        int scrollX, scrollY;
        Boolean schoolSource;
        String schoolAccount = "", schoolSignature = "", schoolTerm = "";
        long schoolRevision = -1, schoolGeneration;
        boolean schoolLoading, schoolSyncing;
        String schoolError;
        java.util.Map<String, Timetable> schoolTerms = Collections.emptyMap();
    }

    private Session session;
    private LinearLayout shell;
    private FrameLayout body;
    private ImageButton refreshButton;
    private DockNavigationLayout sourceDock;
    private ScrollView vertical;
    private HorizontalScrollView horizontal;
    private LinearLayout courseContent;
    private String animatedSemester;
    private int semesterDirection;
    private Dialog floating, nested;
    private boolean resumed;
    private int gridWidth;
    private final Runnable schoolWatch = new Runnable() {
        @Override public void run() {
            if (!resumed) return;
            refreshSchool(false);
            MAIN.postDelayed(this, 1000);
        }
    };

    @Override protected void onCreate(Bundle state) {


        session = getLastNonConfigurationInstance() instanceof Session retained ? retained : new Session();
        session.owner = new WeakReference<>(this);
        if (state != null) {
            session.scrollX = state.getInt("scroll_x");
            session.scrollY = state.getInt("scroll_y");
        }
        FrameLayout stage = new FrameLayout(activity);

        shell = new LinearLayout(activity);
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setPadding(dp(20), dp(UiStyle.PAGE_TOP_DP), dp(20), dp(12));
        UiStyle.page(activity, shell);
        shell.setBackgroundColor(Color.TRANSPARENT);
        sourceDock = new DockNavigationLayout(this, this::selectSource, () -> { });
        sourceDock.setPadding(dp(6), dp(6), dp(6), dp(6));
        String[] sourceNames = {"浙大", "导入"};
        for (int i = 0; i < sourceNames.length; i++) {
            final int index = i;
            TextView item = label(sourceNames[i], 15, true);
            item.setGravity(Gravity.CENTER); item.setPadding(dp(4), 0, dp(4), 0);
            item.setContentDescription(i == 0 ? "浙大课表" : "导入课表");
            item.setOnClickListener(view -> { selectSource(index); sourceDock.finishPageSelection(index); });
            sourceDock.addItem(item, new LinearLayout.LayoutParams(0, dp(48), 1));
        }
        refreshButton = WorkspacePageHeader.icon(activity, R.drawable.ic_refresh, "刷新课表", this::syncSchool);
        UiStyle.addSpaced(shell, new WorkspacePageHeader(activity, sourceDock.motionHost(true), refreshButton), 0, 8);
        body = new FrameLayout(activity);
        shell.addView(body, new LinearLayout.LayoutParams(-1, 0, 1));
        stage.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        shell.setFitsSystemWindows(false);
        setContentView(stage);
        body.addOnLayoutChangeListener((view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            if (right - left > 0 && gridWidth != right - left) {
                rememberScroll();
                render();
            }
        });
        render();
        refreshSchool(false);
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
        catch (android.content.ActivityNotFoundException exception) { Feedback.show(activity, "没有可用的文件选择器"); }
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
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = panel("确认学期");
        LinearLayout form = new LinearLayout(activity);
        form.setOrientation(LinearLayout.VERTICAL);
        TextView filename = label(pending.name, 13, false);
        filename.setMaxLines(2);
        filename.setEllipsize(android.text.TextUtils.TruncateAt.END);
        UiStyle.addSpaced(form, filename, 0, 12);
        UiStyle.addSpaced(form, label("学年起始年份", 12, false), 0, 6);
        EditText year = new EditText(activity);
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
        LinearLayout actions = new LinearLayout(activity);
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
                session.schoolSource = false;
                dialog.dismiss();
                save(merged);
            } catch (RuntimeException error) { Feedback.showLong(activity, message(error)); }
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
        Dialog dialog = new Dialog(activity);
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
        Dialog dialog = new Dialog(activity);
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
        save(library, false);
    }

    private void save(TimetableStore.Library library, boolean preserveScroll) {
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
                    if (!preserveScroll) target.scrollX = target.scrollY = 0;
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
        settleSemesterContent();
        refreshButton.setEnabled(!session.schoolSyncing);
        refreshButton.setVisibility(Boolean.TRUE.equals(session.schoolSource) ? View.VISIBLE : View.INVISIBLE);
        ZjuSettingsStore settings = new ZjuSettingsStore(this);
        String refreshLabel = !settings.configured() || !settings.enabled() ? "学校设置"
                : !settings.timetable() ? "启用浙大课表" : session.schoolSyncing ? "刷新中" : "刷新课表";
        refreshButton.setContentDescription(refreshLabel); refreshButton.setTooltipText(refreshLabel);
        body.removeAllViews();
        vertical = null;
        horizontal = null;
        courseContent = null;
        gridWidth = body.getWidth();
        sourceDock.finishPageSelection(Boolean.TRUE.equals(session.schoolSource) ? 0 : 1);
        if (Boolean.TRUE.equals(session.schoolSource)) {
            renderSchool();
            if (session.pending != null && resumed) confirmImport();
            return;
        }
        TimetableStore.Semester term = currentTerm();
        boolean animateSemester = !session.busy && session.error == null && term != null
                && term.id().equals(animatedSemester) && resumed;
        if (!session.busy) animatedSemester = null;
        if (term == null) {
            LinearLayout empty = new LinearLayout(activity);
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
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
            body.addView(empty, params);
        } else {
            vertical = new ScrollView(activity);
            vertical.setVerticalScrollBarEnabled(false);
            LinearLayout content = new LinearLayout(activity);
            content.setOrientation(LinearLayout.VERTICAL);
            addSemesterControls(content, term);
            courseContent = new LinearLayout(activity);
            courseContent.setOrientation(LinearLayout.VERTICAL);
            content.addView(courseContent, new LinearLayout.LayoutParams(-1, -2));
            horizontal = new HorizontalScrollView(activity);
            horizontal.setHorizontalScrollBarEnabled(false);
            horizontal.setFillViewport(true);
            UiStyle.glass(horizontal);
            horizontal.setElevation(0f);
            horizontal.setOutlineProvider(new android.view.ViewOutlineProvider() {
                @Override public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(UiStyle.radius(view.getContext(), UiStyle.RADIUS_PANEL)));
                }
            });
            horizontal.setClipToOutline(true);
            int available = gridWidth > 0 ? gridWidth : getResources().getDisplayMetrics().widthPixels - dp(40);
            if (!term.view.regular.blocks.isEmpty()) {
                TimetableGrid grid = new TimetableGrid(this, term.view.regular, available, block -> showCourse(block, false));
                // Keep the grid's coordinates unchanged; its outer content supplies motion room.
                FrameLayout gridContent = new FrameLayout(activity);
                int edgeX = PressFeedback.edgeSpace(grid.getLayoutParams().width);
                int edgeY = gridMotionEdge(grid);
                gridContent.setPadding(edgeX, edgeY, edgeX, edgeY);
                gridContent.addView(grid);
                horizontal.addView(gridContent);
                UiStyle.addSpaced(courseContent, horizontal, 0, 16);
            } else {
                TextView emptyGrid = label("本学期的安排均在下方展示", 14, false);
                emptyGrid.setPadding(dp(16), dp(20), dp(16), dp(20));
                UiStyle.glass(emptyGrid);
                emptyGrid.setElevation(0f);
                UiStyle.addSpaced(courseContent, emptyGrid, 0, 16);
            }
            addSpecialArrangements(courseContent, term);
            vertical.addView(content, new ScrollView.LayoutParams(-1, -2));
            body.addView(vertical, new FrameLayout.LayoutParams(-1, -1));
            ScrollView scroll = vertical;
            HorizontalScrollView sideways = horizontal;
            int x = session.scrollX, y = session.scrollY;
            LinearLayout renderedCourses = courseContent;
            int direction = semesterDirection;
            scroll.post(() -> {
                if (vertical != scroll) return;
                scroll.scrollTo(0, y);
                sideways.scrollTo(x, 0);
                if (animateSemester && resumed && UiMotion.isEnabled())
                    enterSemesterContent(renderedCourses, direction);
            });
            if (session.error != null && resumed) {
                Feedback.showLong(activity, session.error);
                session.error = null;
            }
        }
        if (session.pending != null && resumed) confirmImport();
    }

    private void selectSource(int index) {
        boolean school = index == 0;
        if (session.schoolSource != null && session.schoolSource == school) return;
        session.schoolSource = school;
        session.scrollX = session.scrollY = 0;
        // Keep the navigation view alive while its highlight is still moving.
        render();
    }

    private void refreshSchool(boolean force) {
        ZjuSettingsStore settings = new ZjuSettingsStore(this);
        String account = settings.account();
        String signature = account + "/" + settings.configured() + "/" + settings.enabled() + "/" + settings.timetable();
        long revision = ZjuTimetableStore.revision();
        boolean changed = !signature.equals(session.schoolSignature);
        if (!force && !changed && revision == session.schoolRevision) return;
        boolean accountChanged = !account.equals(session.schoolAccount);
        if (session.schoolSource == null || accountChanged || changed && settings.enabled())
            session.schoolSource = settings.configured() && settings.enabled();
        if (getIntent().hasExtra(CourseAgenda.EXTRA_TERM) && !session.courseOpened)
            session.schoolSource = false;
        if (accountChanged || !settings.configured()) {
            session.schoolTerms = Collections.emptyMap();
            session.schoolTerm = "";
        }
        session.schoolAccount = account;
        session.schoolSignature = signature;
        session.schoolRevision = revision;
        session.schoolLoading = true;
        session.schoolError = null;
        Session target = session;
        long generation = ++target.schoolGeneration;
        Context app = getApplicationContext();
        boolean configured = settings.configured();
        render();
        IO.execute(() -> {
            java.util.Map<String, Timetable> terms = null;
            String error = null;
            try { terms = configured ? ZjuTimetableView.terms(new ZjuTimetableStore(app).load(account)) : Collections.emptyMap(); }
            catch (Exception failure) { error = "未能读取浙大课表，请重试"; }
            java.util.Map<String, Timetable> result = terms;
            String failure = error;
            MAIN.post(() -> {
                if (generation != target.schoolGeneration) return;
                target.schoolLoading = false;
                target.schoolError = failure;
                if (result != null) {
                    target.schoolTerms = result;
                    if (!result.containsKey(target.schoolTerm))
                        target.schoolTerm = ZjuTimetableView.current(result, LocalDate.now(ZjuTimetableView.ZONE));
                }
                notifyOwner(target);
            });
        });
    }

    private void syncSchool() {
        ZjuSettingsStore settings = new ZjuSettingsStore(this);
        if (!settings.configured() || !settings.enabled()) {
            startActivity(new Intent(activity, ZjuSettingsActivity.class));
            return;
        }
        if (session.schoolSyncing) return;
        if (!settings.timetable()) settings.options(true, true);
        session.schoolSyncing = true;
        render();
        Session target = session;
        ZjuSync.request(getApplicationContext(), status -> {
            target.schoolSyncing = false;
            TimetablePage owner = target.owner.get();
            if (owner == null || owner.isDestroyed()) return;
            owner.refreshSchool(true);
            if (owner.resumed) Feedback.showLong(owner.activity, status);
        });
    }

    private void renderSchool() {
        ZjuSettingsStore settings = new ZjuSettingsStore(this);
        vertical = new ScrollView(activity);
        vertical.setVerticalScrollBarEnabled(false);
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        Timetable table = settings.configured() && settings.enabled()
                ? session.schoolTerms.get(session.schoolTerm) : null;
        if (table == null) {
            TextView empty = label(session.schoolLoading ? "读取课表…"
                    : session.schoolError != null ? session.schoolError : "暂无浙大课表", 18, true);
            UiStyle.addSpaced(content, empty, 16, 16);
        } else {
            LinearLayout card = new LinearLayout(activity);
            card.setOrientation(LinearLayout.VERTICAL); UiStyle.glass(card);
            card.setPadding(0, 0, 0, 0);
            LinearLayout heading = new LinearLayout(activity); heading.setGravity(Gravity.CENTER_VERTICAL);
            heading.setPadding(dp(12), dp(4), dp(12), 0);
            TextView title = label(session.schoolTerm.isEmpty() ? "未标注学期" : session.schoolTerm, 18, true);
            title.setMinHeight(dp(44)); title.setGravity(Gravity.CENTER_VERTICAL);
            title.setContentDescription(title.getText() + "，选择学期");
            title.setOnClickListener(view -> chooseSchoolTerm()); UiStyle.pressable(title);
            heading.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
            TextView count = label(table.courseCount + " 门", 13, false);
            count.setContentDescription(table.courseCount + " 门课程"); heading.addView(count);
            card.addView(heading);
            horizontal = new HorizontalScrollView(activity);
            horizontal.setHorizontalScrollBarEnabled(false);
            horizontal.setFillViewport(true);
            int available = gridWidth > 0 ? gridWidth : getResources().getDisplayMetrics().widthPixels - dp(40);
            TimetableGrid grid = new TimetableGrid(this, table, available, block -> showCourse(block, false));
            FrameLayout gridContent = new FrameLayout(activity);
            int edgeX = PressFeedback.edgeSpace(grid.getLayoutParams().width);
            // Course cells scale individually; the full day's height is not a pressed surface.
            int edgeY = gridMotionEdge(grid);
            gridContent.setPadding(edgeX, edgeY, edgeX, edgeY);
            gridContent.addView(grid);
            horizontal.addView(gridContent);
            card.addView(horizontal); content.addView(card);
            if (session.schoolError != null) UiStyle.addSpaced(content, label(session.schoolError, 14, false), 8, 0);
        }
        vertical.addView(content);
        body.addView(vertical, new FrameLayout.LayoutParams(-1, -1));
        ScrollView scroll = vertical;
        HorizontalScrollView sideways = horizontal;
        int x = session.scrollX, y = session.scrollY;
        scroll.post(() -> {
            if (vertical != scroll) return;
            scroll.scrollTo(0, y);
            if (sideways != null) sideways.scrollTo(x, 0);
        });
    }

    private int gridMotionEdge(TimetableGrid grid) {
        int largestCell = 0;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View cell = grid.getChildAt(i);
            if (cell.isClickable()) largestCell = Math.max(largestCell, cell.getLayoutParams().height);
        }
        return Math.max(dp(4), PressFeedback.edgeSpace(largestCell));
    }

    private void chooseSchoolTerm() {
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = panel("学期");
        LinearLayout rows = new LinearLayout(activity);
        rows.setOrientation(LinearLayout.VERTICAL);
        for (String term : session.schoolTerms.keySet())
            UiStyle.addSpaced(rows, action(term.isEmpty() ? "未标注学期" : term, term.equals(session.schoolTerm), () -> {
                rememberScroll();
                session.schoolTerm = term;
                dialog.dismiss();
                render();
            }), 0, 8);
        boundedContent(panel, rows, .55f);
        floating = dialog;
        UiStyle.showFloatingDialog(dialog, panel);
    }

    private TimetableStore.Semester currentTerm() {
        return session.library == null ? null : session.library.current(LocalDate.now());
    }

    private void addSemesterControls(LinearLayout parent, TimetableStore.Semester selected) {
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(12), dp(12), dp(12), dp(12));
        UiStyle.glass(card);
        HorizontalScrollView groups = new HorizontalScrollView(activity);
        groups.setHorizontalScrollBarEnabled(false);
        LinearLayout groupRow = new LinearLayout(activity);
        groupRow.setPadding(dp(6), dp(6), dp(6), dp(6));
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
        LinearLayout seasons = new LinearLayout(activity);
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
        LinearLayout stats = new LinearLayout(activity);
        stats.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout summary = new LinearLayout(activity);
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
        TimetableStore.Semester current = currentTerm();
        semesterDirection = current == null || id.compareTo(current.id()) > 0 ? 1 : -1;
        animatedSemester = id;
        save(session.library.select(id), true);
    }

    private void settleSemesterContent() {
        if (courseContent == null) return;
        courseContent.animate().setUpdateListener(null).cancel();
        courseContent.setAlpha(1f);
        courseContent.setTranslationX(0f);
        UiStyle.invalidateAcrylicSurfaces(courseContent);
    }

    private void enterSemesterContent(LinearLayout courses, int direction) {
        ArrayList<View> surfaces = new ArrayList<>();
        UiStyle.collectAcrylicSurfaces(courses, surfaces);
        courses.setAlpha(.65f);
        courses.setTranslationX(direction * dp(10));
        courses.animate().alpha(1f).translationX(0f).setDuration(UiMotion.ENTER)
                .setInterpolator(new android.view.animation.DecelerateInterpolator(1.5f))
                .setUpdateListener(animation -> {
                    if (!UiMotion.isEnabled()) settleSemesterContent();
                    else for (View surface : surfaces) surface.invalidate();
                }).withEndAction(() -> {
                    courses.animate().setUpdateListener(null);
                    courses.setAlpha(1f);
                    courses.setTranslationX(0f);
                    for (View surface : surfaces) surface.invalidate();
                }).start();
    }

    private static String duration(long minutes) {
        return minutes / 60 + " 小时" + (minutes % 60 == 0 ? "" : " " + minutes % 60 + " 分");
    }

    private void addSpecialArrangements(LinearLayout parent, TimetableStore.Semester term) {
        if (term.view.special.isEmpty()) return;
        LinearLayout card = new LinearLayout(activity);
        card.setOrientation(LinearLayout.VERTICAL);
        UiStyle.glass(card);
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        UiStyle.addSpaced(card, label("特殊安排 · " + term.view.special.size(), 18, true), 0, 12);
        LinearLayout rows = new LinearLayout(activity);
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
            LinearLayout navigation = new LinearLayout(activity);
            navigation.addView(previous, new LinearLayout.LayoutParams(0, -2, 1));
            navigation.addView(next, new LinearLayout.LayoutParams(0, -2, 1));
            card.addView(navigation);
        }
        renderRows.run();
        parent.addView(card);
    }

    private void manageSemester(TimetableStore.Semester term) {
        if (session.busy) return;
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = panel(term.document.plan.groupLabel() + " · " + term.document.plan.label(term.index));
        UiStyle.addSpaced(panel, action("恢复自动分类", false, () -> {
            dialog.dismiss(); save(session.library.classify(term, term.view.all, null));
        }), 0, 8);
        UiStyle.addSpaced(panel, action("删除此学期", false, () -> {
            Dialog confirmation = new Dialog(activity);
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
        boolean school = Boolean.TRUE.equals(session.schoolSource);
        if (!school && term == null) return;
        Dialog dialog = new Dialog(activity);
        LinearLayout panel = panel(block.title);
        String when = TimetableGrid.DAYS[block.day] + " · " + (block.allDay ? "全天"
                : Timetable.time(block.startMinute) + "–" + Timetable.time(block.endMinute));
        TextView time = label(when, 16, true);
        time.setTextColor(UiStyle.colors(this).primary);
        UiStyle.addSpaced(panel, time, 0, 12);
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout details = new LinearLayout(activity);
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
                rules.add(entry.recurrence + (block.location.isEmpty() && !entry.location.isEmpty()
                        ? " · " + entry.location : ""));
            } else {
                String value = entry.allDay ? DATE.format(entry.start) : DATE.format(entry.start) + "  " + TIME.format(entry.start);
                value += entry.allDay ? (entry.end.toLocalDate().minusDays(1).isAfter(entry.start.toLocalDate())
                        ? "–" + DATE.format(entry.end.minusDays(1)) : "")
                        : "–" + (entry.start.toLocalDate().equals(entry.end.toLocalDate())
                                ? TIME.format(entry.end) : DATE_TIME.format(entry.end));
                if (block.location.isEmpty() && !entry.location.isEmpty()) value += " · " + entry.location;
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
                LinearLayout navigation = new LinearLayout(activity);
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
        detail(details, "时区", school ? ZjuTimetableView.ZONE.getId() : term.document.table.zone.getId());
        scroll.addView(details);
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * .48f);
        details.measure(View.MeasureSpec.makeMeasureSpec(Math.max(dp(200), (int) (getResources().getDisplayMetrics().widthPixels * .9f) - dp(40)), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(maxHeight, details.getMeasuredHeight())));
        Button classify = action(special ? "放入课表" : "移至特殊安排", false, () -> {
            dialog.dismiss();
            save(session.library.classify(term, block.occurrences(), special));
        });
        if (!school) UiStyle.addSpaced(panel, classify, 12, 0);
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
        ScrollView scroll = new ScrollView(activity);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(content);
        int maxHeight = (int) (getResources().getDisplayMetrics().heightPixels * screenFraction);
        content.measure(View.MeasureSpec.makeMeasureSpec(Math.max(dp(200), (int)
                (getResources().getDisplayMetrics().widthPixels * .9f) - dp(40)), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, Math.min(maxHeight, content.getMeasuredHeight())));
    }

    private LinearLayout panel(String title) {
        LinearLayout panel = new LinearLayout(activity);
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
        TextView label = new TextView(activity);
        label.setText(value);
        label.setTextSize(size);
        label.setIncludeFontPadding(false);
        if (heading) UiStyle.title(label); else UiStyle.muted(label);
        return label;
    }
    private Button action(String title, boolean primary, Runnable action) {
        Button button = new Button(activity);
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
        TimetablePage owner = target.owner.get();
        if (owner != null) { owner.render(); owner.openRequestedCourse(); }
    }

    private void openRequestedCourse() {
        if (!resumed || session.busy || !session.initialized || session.courseOpened) return;
        String termId = getIntent().getStringExtra(CourseAgenda.EXTRA_TERM);
        String key = getIntent().getStringExtra(CourseAgenda.EXTRA_OCCURRENCE);
        if (termId == null || key == null) return;
        session.schoolSource = false;
        session.courseOpened = true;
        if (session.library != null) for (TimetableStore.Semester term : session.library.semesters) {
            if (!term.id().equals(termId)) continue;
            session.library = session.library.select(termId);
            render();
            for (Timetable.Occurrence occurrence : term.view.all) if (occurrence.key().equals(key)) {
                Timetable single = new Timetable(Collections.singletonList(occurrence), term.document.table.zone, 1);
                if (!single.blocks.isEmpty()) {
                    boolean special = term.view.special.stream().anyMatch(entry -> entry.key().equals(key));
                    showCourse(single.blocks.get(0), special);
                    return;
                }
            }
            break;
        }
        Feedback.show(activity, "这条课程安排已更新或删除");
    }
    private void rememberScroll() {
        if (vertical != null) session.scrollY = vertical.getScrollY();
        if (horizontal != null) session.scrollX = horizontal.getScrollX();
    }
    @Override protected void onResume() {
        super.onResume();
        resumed = true;
        refreshSchool(false);
        MAIN.removeCallbacks(schoolWatch);
        MAIN.postDelayed(schoolWatch, 1000);
        openRequestedCourse();
        if (session.pending != null) confirmImport();
        if (session.error != null && session.library != null) {
            Feedback.showLong(activity, session.error);
            session.error = null;
        }
    }
    @Override protected void onPause() {
        resumed = false;
        MAIN.removeCallbacks(schoolWatch);
        animatedSemester = null;
        settleSemesterContent();
        rememberScroll();
        super.onPause();
    }
    @Override public Object onRetainNonConfigurationInstance() { rememberScroll(); return session; }
    @Override protected void onSaveInstanceState(Bundle state) {
        rememberScroll();
        state.putInt("scroll_x", session.scrollX);
        state.putInt("scroll_y", session.scrollY);
        super.onSaveInstanceState(state);
    }
    @Override protected void onDestroy() {
        MAIN.removeCallbacks(schoolWatch);
        if (session.owner.get() == this) session.owner.clear();
        if (nested != null && nested.isShowing()) nested.dismiss();
        if (floating != null && floating.isShowing()) floating.dismiss();
        super.onDestroy();
    }
    private int dp(float value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
