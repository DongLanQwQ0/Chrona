package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.RepeatOccurrences;
import com.donglan.chrona.data.RepeatRule;
import com.donglan.chrona.data.TaskStore;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/** Series controls keep original occurrence identities separate from their displayed times. */
public final class RepeatScheduleActivity extends Activity {
    private static final ExecutorService WORKER = Executors.newSingleThreadExecutor();
    private static final String[] FREQUENCIES = {"daily", "weekly", "monthly", "yearly"};
    private static final String[] FREQUENCY_LABELS = {"每天", "每周", "每月", "每年"};
    private static final String[] DAY_LABELS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    private long taskId, candidateId;
    private Long selectedOriginal;
    private YearMonth month;
    private EventCandidate series;
    private boolean busy;
    private LinearLayout root;
    private int frequency, ending;
    private String interval = "1", count = "10";
    private LocalDate until;
    private final boolean[] weekdays = new boolean[7];
    private boolean daysInitialized;
    private EditText intervalInput, countInput;
    private List<EventCandidate> occurrences = List.of();
    private DateIntervalBar monthBar;
    private LinearLayout occurrenceBody;
    private TextView seriesState;
    private ImageButton headerAction;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        taskId = getIntent().getLongExtra("task_id", -1);
        candidateId = getIntent().getLongExtra("candidate_id", -1);
        if (getIntent().hasExtra("occurrence_at")) selectedOriginal = getIntent().getLongExtra("occurrence_at", 0);
        if (state != null) {
            if (state.containsKey("selected")) selectedOriginal = state.getLong("selected");
            if (state.containsKey("month")) month = YearMonth.parse(state.getString("month"));
            frequency = state.getInt("frequency"); ending = state.getInt("ending");
            interval = state.getString("interval", "1"); count = state.getString("count", "10");
            if (state.containsKey("until")) until = LocalDate.parse(state.getString("until"));
            boolean[] savedDays = state.getBooleanArray("days");
            if (savedDays != null) System.arraycopy(savedDays, 0, weekdays, 0, 7);
            daysInitialized = state.getBoolean("daysInitialized");
        }
        load();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        snapshotForm();
        if (selectedOriginal != null) state.putLong("selected", selectedOriginal);
        if (month != null) state.putString("month", month.toString());
        state.putInt("frequency", frequency); state.putInt("ending", ending);
        state.putString("interval", interval); state.putString("count", count);
        if (until != null) state.putString("until", until.toString());
        state.putBooleanArray("days", weekdays); state.putBoolean("daysInitialized", daysInitialized);
        super.onSaveInstanceState(state);
    }

    private ZoneId zone() { return series.allDay ? ZoneOffset.UTC : ZoneId.of(series.timeZoneId); }
    private boolean alive() { return !isFinishing() && !isDestroyed(); }
    private void load() {
        if (busy) return;
        busy = true;
        if (monthBar == null || !monthBar.isAttachedToWindow()) render();
        WORKER.execute(() -> {
            try (TaskStore store = new TaskStore(getApplicationContext())) {
                EventCandidate found = null;
                for (EventCandidate item : store.getCandidates(taskId)) if (item.id == candidateId) found = item;
                if (found == null) throw new IllegalStateException("日程已不存在");
                EventCandidate loaded = found;
                ZoneId zone = loaded.allDay ? ZoneOffset.UTC : ZoneId.of(loaded.timeZoneId);
                YearMonth target = month;
                if (target == null) {
                    Long anchor = selectedOriginal != null ? selectedOriginal : loaded.anchor();
                    if (selectedOriginal != null && loaded.repeatRule != null) {
                        EventCandidate selected = RepeatOccurrences.at(loaded, selectedOriginal);
                        if (selected != null) anchor = selected.anchor();
                    } else if (loaded.repeatRule != null) {
                        long now = System.currentTimeMillis();
                        anchor = loaded.repeatStoppedAtMillis != null ? loaded.repeatStoppedAtMillis
                                : loaded.anchor() == null ? now : Math.max(loaded.anchor(), now);
                    }
                    target = anchor == null ? YearMonth.now(zone) : YearMonth.from(Instant.ofEpochMilli(anchor).atZone(zone));
                }
                List<EventCandidate> projected = project(loaded, target, zone);
                YearMonth finalMonth = target;
                runOnUiThread(() -> {
                    if (!alive()) return;
                    series = loaded; month = finalMonth; occurrences = projected; busy = false;
                    if (!daysInitialized && series.anchor() != null) {
                        weekdays[Instant.ofEpochMilli(series.anchor()).atZone(zone()).getDayOfWeek().getValue() - 1] = true;
                        daysInitialized = true;
                    }
                    if (monthBar != null && monthBar.isAttachedToWindow() && series.repeatRule != null) refreshSeries();
                    else render();
                });
            } catch (Exception error) { failed(error); }
        });
    }

    private static List<EventCandidate> project(EventCandidate item, YearMonth target, ZoneId zone) {
        if (item.repeatRule == null) return List.of();
        return RepeatOccurrences.between(item, target.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
                target.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(), 1000);
    }

    private void render() {
        root = SettingsPageLayout.content(this);
        monthBar = null; occurrenceBody = null; headerAction = null;
        if (series != null && !series.isRecord && series.anchor() != null) {
            boolean existing = series.repeatRule != null;
            headerAction = UiStyle.navigationIcon(this, existing ? R.drawable.ic_circle_stop : R.drawable.ic_check,
                    existing ? "停止重复" : "建立重复日程", existing ? this::confirmStop : this::createRepeat);
            headerAction.setEnabled(!busy && (!existing || series.repeatStoppedAtMillis == null));
        }
        SettingsPageLayout.header(this, root, "重复日程", headerAction);
        intervalInput = null; countInput = null;
        if (series == null) text(root, busy ? "正在读取…" : "无法读取日程", 16, false);
        else {
            seriesState = new TextView(this);
            seriesState.setTextSize(16); UiStyle.title(seriesState);
            root.addView(seriesState, new LinearLayout.LayoutParams(-1, -2));
            if (series.repeatRule == null) renderForm();
            else renderSeries();
        }
        if (busy) setEnabled(root, false);
        SettingsPageLayout.show(this, root, false);
    }

    private void renderForm() {
        setSeriesSummary(series.anchor() == null ? "" : "首次 " + format(series.anchor()));
        if (series.anchor() == null || series.isRecord) {
            text(root, "请先设置首次日程时间", 16, false);
            return;
        }
        Button frequencyChoice = choiceRow("频率", FREQUENCY_LABELS[frequency], () -> {
            snapshotForm();
            UiDialogs.choiceDialog(this, "重复频率", FREQUENCY_LABELS, frequency, index -> { frequency = index; render(); });
        });
        frequencyChoice.setContentDescription("重复频率，" + FREQUENCY_LABELS[frequency]);
        text(root, "间隔", 14, false);
        intervalInput = number(root, interval);
        if (frequency == 1) {
            text(root, "星期", 14, false);
            for (int row = 0; row < 4; row++) {
                LinearLayout days = new LinearLayout(this);
                for (int day = row * 2; day < Math.min(7, row * 2 + 2); day++) {
                    final int index = day;
                    CheckBox check = new CheckBox(this);
                    check.setText(DAY_LABELS[day]); check.setChecked(weekdays[day]); UiStyle.toggle(check);
                    check.setMinHeight(dp(48));
                    check.setOnCheckedChangeListener((view, value) -> weekdays[index] = value);
                    days.addView(check, new LinearLayout.LayoutParams(0, -2, 1));
                }
                root.addView(days);
            }
        }
        String endLabel = ending == 1 ? "结束日期  " + (until == null ? "选择日期" : until)
                : ending == 2 ? "按次数结束" : "持续重复";
        choiceRow("结束", endLabel, () -> {
            snapshotForm();
            UiDialogs.choiceDialog(this, "结束条件", new String[]{"持续重复", "结束日期", "重复次数"}, ending, index -> {
                ending = index;
                if (index == 1) pickDate(until == null ? Instant.ofEpochMilli(series.anchor()).atZone(zone()).toLocalDate() : until,
                        date -> { until = date; render(); });
                else render();
            });
        });
        if (ending == 2) { text(root, "次数（包含首次）", 14, false); countInput = number(root, count); }
    }

    private void createRepeat() {
        if (busy) return;
        snapshotForm();
        try {
            ArrayList<Integer> days = new ArrayList<>();
            if (frequency == 1) {
                for (int i = 0; i < 7; i++) if (weekdays[i]) days.add(i + 1);
                if (days.isEmpty()) throw new IllegalArgumentException("请选择重复星期");
            }
            if (ending == 1 && until == null) throw new IllegalArgumentException("请选择结束日期");
            RepeatRule rule = new RepeatRule(FREQUENCIES[frequency], Integer.parseInt(interval.trim()), days,
                    ending == 1 ? until : null, ending == 2 ? Integer.valueOf(count.trim()) : null);
            rule.validateAnchor(series.anchor(), zone().getId());
            change("rule", null, null, rule);
        } catch (NumberFormatException error) { Feedback.show(this, "请输入有效的间隔和次数"); }
        catch (IllegalArgumentException error) { Feedback.show(this, error.getMessage()); }
    }

    private void confirmStop() {
        if (busy || series.repeatStoppedAtMillis != null) return;
        UiDialogs.confirmDialog(this, "停止重复", "过往日程将保留，停止今后的重复。", "停止重复",
                () -> change("stop", null, null, null));
    }

    private void renderSeries() {
        monthBar = new DateIntervalBar(this,
                () -> { if (!busy) pickDate(month.atDay(1), value -> { month = YearMonth.from(value); monthBar.setText(monthLabel()); load(); }); },
                direction -> { if (busy) return false; month = month.plusMonths(direction); monthBar.setText(monthLabel()); load(); return true; });
        UiStyle.addSpaced(root, monthBar, 8, 8);
        occurrenceBody = new LinearLayout(this); occurrenceBody.setOrientation(LinearLayout.VERTICAL);
        root.addView(occurrenceBody, new LinearLayout.LayoutParams(-1,-2));
        refreshSeries();
    }

    private String monthLabel() { return month.getYear() + "年" + month.getMonthValue() + "月"; }

    private void refreshSeries() {
        setEnabled(root, !busy);
        headerAction.setEnabled(!busy && series.repeatStoppedAtMillis == null);
        setSeriesSummary(series.repeatRule.label() + (series.repeatStoppedAtMillis == null ? "" : " · 已停止"));
        monthBar.setText(monthLabel());
        occurrenceBody.removeAllViews();
        if (busy) text(occurrenceBody, "正在读取…", 16, false);
        else if (occurrences.isEmpty()) text(occurrenceBody, "本月无日程", 16, false);
        for (EventCandidate item : busy ? List.<EventCandidate>of() : occurrences) {
            long original = item.occurrenceAtMillis;
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(14), dp(12), dp(14), dp(12));
            boolean selected = selectedOriginal != null && selectedOriginal == original;
            row.setTag(original);
            UiStyle.acrylicChoice(row, selected, 8, true);
            text(row, format(item.anchor()), 17, true);
            String state = item.completed() ? "已完成" : "";
            if (original != item.anchor()) state += (state.isEmpty() ? "" : " · ") + "已改期";
            if (!state.isEmpty()) text(row, state, 14, false);
            row.setOnClickListener(view -> {
                if (busy) return;
                selectedOriginal = original;
                for (int i = 0; i < occurrenceBody.getChildCount(); i++) {
                    View candidate = occurrenceBody.getChildAt(i);
                    UiStyle.acrylicChoice(candidate, Long.valueOf(original).equals(candidate.getTag()), 8, true);
                }
                occurrenceActions(item);
            });
            UiStyle.pressable(row);
            UiStyle.addSpaced(occurrenceBody, row, 0, 8);
        }
    }

    private void setSeriesSummary(String detail) {
        android.text.SpannableString summary = new android.text.SpannableString(series.title
                + (detail.isEmpty() ? "" : " · " + detail));
        if (!detail.isEmpty()) {
            int start = series.title.length();
            summary.setSpan(new android.text.style.RelativeSizeSpan(13f / 16f), start, summary.length(), 0);
            summary.setSpan(new android.text.style.StyleSpan(android.graphics.Typeface.NORMAL), start, summary.length(), 0);
            summary.setSpan(new android.text.style.ForegroundColorSpan(UiStyle.colors(this).muted), start, summary.length(), 0);
        }
        seriesState.setText(summary);
    }

    private void occurrenceActions(EventCandidate item) {
        String[] options = item.isTask() ? new String[]{"编辑本次日程", "跳过这一次", item.completed() ? "恢复待完成" : "标记已完成"}
                : new String[]{"编辑本次日程", "跳过这一次"};
        UiDialogs.choiceDialog(this, format(item.anchor()), options, -1, choice -> {
            if (busy) return;
            if (choice == 0) startActivity(new android.content.Intent(this,TaskDetailActivity.class).putExtra("task_id",item.taskId)
                    .putExtra("candidate_id",item.id).putExtra("occurrence_at",item.occurrenceAtMillis.longValue()));
            else if (choice == 1) UiDialogs.confirmDialog(this, "跳过这一次", format(item.anchor()), "跳过",
                    () -> change("skip", item.occurrenceAtMillis, null, null));
            else change(item.completed() ? "reopen" : "complete", item.occurrenceAtMillis, null, null);
        });
    }

    private void pickDate(LocalDate seed, Consumer<LocalDate> save) {
        picker("选择日期", seed.atStartOfDay(), true, value -> save.accept(value.toLocalDate()));
    }

    private void picker(String title, LocalDateTime seed, boolean dateOnly, Consumer<LocalDateTime> save) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = UiDialogs.dialogPanel(this, title);
        GlassDateTimePickerView picker = new GlassDateTimePickerView(this, seed);
        TextView selected = text(panel, "", 16, true);
        selected.setGravity(Gravity.CENTER);
        Runnable display = () -> selected.setText(dateOnly ? picker.value().toLocalDate().toString()
                : picker.value().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
        picker.onChanged(display); display.run();
        panel.addView(picker, new LinearLayout.LayoutParams(-1, dp(240)));
        LinearLayout actions = new LinearLayout(this);
        Button cancel = new Button(this); cancel.setText("取消"); UiStyle.button(cancel, false);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button confirm = new Button(this); confirm.setText(dateOnly ? "确定" : "选择时间"); UiStyle.button(confirm, true);
        boolean[] timeStep = {false};
        selected.setContentDescription("当前选择，点按返回日期选择");
        selected.setOnClickListener(view -> {
            picker.finishSelection(); timeStep[0] = false; picker.showTime(false);
            confirm.setText(dateOnly ? "确定" : "选择时间");
        });
        confirm.setOnClickListener(view -> {
            if (busy) return;
            picker.finishSelection();
            if (!dateOnly && !timeStep[0]) {
                timeStep[0] = true; picker.showTime(true); confirm.setText("确定"); return;
            }
            LocalDateTime value = picker.value(); dialog.dismiss(); save.accept(value);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(52), 1); params.leftMargin = dp(8);
        actions.addView(confirm, params); UiStyle.addSpaced(panel, actions, 12, 0);
        UiDialogs.fitPicker(this,panel,picker,240);
        UiDialogs.showFloatingDialog(dialog, panel);
    }

    private void change(String action, Long original, Long moved, RepeatRule rule) {
        if (busy || series == null) return;
        if (series.calendarEventId != null && (checkSelfPermission(Manifest.permission.READ_CALENDAR) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR}, 42);
            Feedback.show(this, "授权日历权限后请再次点击"); return;
        }
        String baseline = CandidateEditBaseline.of(series);
        busy = true; setEnabled(root, false);
        WORKER.execute(() -> {
            try {
                RepeatActions.change(getApplicationContext(), taskId, candidateId, baseline, action, original, moved, rule);
                runOnUiThread(() -> {
                    if (alive()) {
                        if ("move".equals(action) && moved != null)
                            month = YearMonth.from(Instant.ofEpochMilli(moved).atZone(zone()));
                        busy = false; setResult(RESULT_OK); load();
                    }
                });
            } catch (Exception error) { failed(error); }
        });
    }

    private void failed(Exception error) {
        runOnUiThread(() -> {
            if (!alive()) return;
            busy = false; render();
            Feedback.show(this, error.getMessage() == null ? "操作失败，请重试" : error.getMessage());
        });
    }
    private String format(long millis) {
        return DateTimeFormatter.ofPattern(series.allDay ? "M月d日 EEEE · 全天" : "M月d日 EEEE HH:mm", java.util.Locale.CHINA)
                .format(Instant.ofEpochMilli(millis).atZone(zone()));
    }
    private void snapshotForm() {
        if (intervalInput != null) interval = intervalInput.getText().toString();
        if (countInput != null) count = countInput.getText().toString();
    }
    private EditText number(LinearLayout parent, String value) {
        EditText field = new EditText(this); field.setSingleLine(true); field.setInputType(InputType.TYPE_CLASS_NUMBER);
        field.setText(value); UiStyle.input(field); UiStyle.addSpaced(parent, field, 4, 8); return field;
    }
    private TextView text(LinearLayout parent, String value, int size, boolean strong) {
        TextView text = new TextView(this); text.setText(value); text.setTextSize(size);
        if (strong) UiStyle.title(text); else UiStyle.muted(text);
        UiStyle.addSpaced(parent, text, 3, 5); return text;
    }
    private Button choiceRow(String label, String value, Runnable action) {
        LinearLayout row = new LinearLayout(this); row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = new TextView(this); name.setText(label); name.setTextSize(16); UiStyle.muted(name);
        row.addView(name, new LinearLayout.LayoutParams(dp(56), -2));
        Button button = new Button(this); button.setText(value); UiStyle.button(button, false);
        button.setOnClickListener(view -> { if (!busy) action.run(); });
        row.addView(button, new LinearLayout.LayoutParams(0, -2, 1));
        UiStyle.addSpaced(root, row, 8, 4); return button;
    }
    private void setEnabled(View view, boolean enabled) {
        view.setEnabled(enabled);
        if (view instanceof android.view.ViewGroup) {
            android.view.ViewGroup group = (android.view.ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) setEnabled(group.getChildAt(i), enabled);
        }
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
