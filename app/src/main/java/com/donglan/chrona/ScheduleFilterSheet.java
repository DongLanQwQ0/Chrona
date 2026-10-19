package com.donglan.chrona;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.ScheduleFilterState;
import com.donglan.chrona.data.ScheduleQuery;
import java.time.LocalDate;
import java.util.function.Consumer;

/** One retained sheet with inline choices and a private, cancellable filter draft. */
final class ScheduleFilterSheet {
    interface DatePicker {
        void pick(String title, LocalDate initial, Consumer<LocalDate> chosen);
    }

    private final Activity activity;
    private final Dialog dialog;
    private final ScheduleFilterState draft;
    private final DatePicker picker;
    private final Consumer<ScheduleFilterState> apply;
    private final LinearLayout body;
    private int scrollY;
    private DateIntervalBar interval;
    private ChoiceDock dates, category, todo, publication, source;

    static void show(Activity activity, ScheduleFilterState initial, DatePicker picker,
            Consumer<ScheduleFilterState> apply) {
        new ScheduleFilterSheet(activity, initial, picker, apply).show();
    }

    private ScheduleFilterSheet(Activity activity, ScheduleFilterState initial,
            DatePicker picker, Consumer<ScheduleFilterState> apply) {
        this.activity = activity; this.draft = initial.copy(); this.picker = picker; this.apply = apply;
        dialog = new Dialog(activity);
        dialog.setCanceledOnTouchOutside(true);
        body = new LinearLayout(activity);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setClipChildren(false);
        body.setClipToPadding(false);
        body.setPadding(0, dp(8), 0, dp(18));
    }

    private void show() {
        LinearLayout panel = column();
        panel.setClipChildren(false);
        panel.setClipToPadding(false);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        UiStyle.glass(panel);
        LinearLayout header = row();
        TextView title = label("筛选日程", 18, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView reset = label("重置", 13, false);
        reset.setTextColor(UiStyle.colors(activity).primary);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(dp(10), 0, dp(10), 0);
        reset.setOnClickListener(view -> { draft.reset(LocalDate.now()); render(); });
        header.addView(reset, new LinearLayout.LayoutParams(-2, dp(40)));
        panel.addView(header);
        Rect visible = new Rect();
        activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(visible);
        int visibleHeight = visible.isEmpty()
                ? activity.getResources().getDisplayMetrics().heightPixels : visible.height();
        int maxHeight = Math.max(0, visibleHeight - dp(180));
        ScrollView scroll = new ScrollView(activity) {
            @Override protected void onMeasure(int width, int height) {
                int limit = View.MeasureSpec.getMode(height) == View.MeasureSpec.UNSPECIFIED
                        ? maxHeight : Math.min(maxHeight, View.MeasureSpec.getSize(height));
                super.onMeasure(width, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setFillViewport(false);
        // Keep the rail aligned with the controls while drawing into the panel's existing
        // side padding. Do not contain the highlight by moving it inward.
        scroll.setPadding(dp(16), 0, dp(16), 0);
        scroll.setClipChildren(false);
        scroll.setClipToPadding(false);
        PressFeedback.protectViewport(scroll);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body);
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> scrollY = y);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(-1, -2);
        scrollParams.setMargins(-dp(16), 0, -dp(16), 0);
        panel.addView(scroll, scrollParams);
        Button save = pill("应用筛选", () -> {
            if (!draft.validSystemRange(LocalDate.now())) {
                Feedback.show(activity, "系统日历每次最多查询一年，请调整日期范围");
                return;
            }
            dialog.dismiss();
            apply.accept(draft.copy());
        }, true);
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(-1, dp(44));
        saveParams.topMargin = dp(10);
        panel.addView(save, saveParams);
        render();
        UiStyle.showFloatingDialog(dialog, panel);
    }

    private void render() {
        if (dates == null) buildChoices();
        int dateIndex = draft.range == ScheduleFilterState.DATE || draft.range == ScheduleFilterState.CUSTOM
                ? 4 : draft.range;
        dates.sync(dateIndex, true);
        category.sync(draft.source == 1 ? EventCategory.indexOf(EventCategory.EVENT) + 1 : draft.category,
                draft.source == 0);
        todo.sync(draft.source == 1 ? ScheduleQuery.TODO_ANY : draft.todoScope, draft.source == 0);
        int publicationIndex = draft.source == 1 ? 3
                : draft.publication == 3 ? 1 : draft.publication == 1 ? 2 : draft.publication == 2 ? 3 : 0;
        publication.sync(publicationIndex, draft.source == 0);
        source.sync(draft.source, true);
        LocalDate[] window = draft.window(LocalDate.now());
        interval.setVisibility(window[0] == null ? View.GONE : View.VISIBLE);
        if (window[0] != null) interval.setText(window[0] + " — " + window[1].minusDays(1));
        if (body.getParent() instanceof ScrollView scroll) {
            int previous = scrollY;
            scroll.post(() -> scroll.scrollTo(0, previous));
        }
    }

    private void buildChoices() {
        String[] dateNames = {"全部", "今天", "本周", "本月", "自选"};
        int[] dateValues = {0, 1, 2, 3, 5};
        dates = new ChoiceDock("日期", R.drawable.ic_schedule, dateNames, index -> {
            int range = dateValues[index];
            if (range == ScheduleFilterState.CUSTOM) pickCustomRange();
            else {
                draft.range = range; draft.date = LocalDate.now();
                if (range != ScheduleFilterState.ALL) draft.tab = 3;
            }
        });
        interval = new DateIntervalBar(activity, this::pickCustomRange, direction -> {
            LocalDate today = LocalDate.now();
            ScheduleFilterState next = draft.copy();
            if (!next.shift(direction, today)) {
                Feedback.show(activity, "系统日历每次最多查询一年"); return false;
            }
            draft.shift(direction, today); render(); return true;
        });
        LinearLayout.LayoutParams intervalParams = new LinearLayout.LayoutParams(-1, dp(48));
        intervalParams.bottomMargin = dp(8);
        body.addView(interval, intervalParams);
        category = new ChoiceDock("类型", R.drawable.ic_event, categoryOptions(), value -> {
            draft.category = value;
            if (value != EventCategory.indexOf(EventCategory.TASK) + 1 && value != ScheduleFilterState.HOMEWORK_CATEGORY)
                draft.todoScope = ScheduleQuery.TODO_ANY;
        });
        String[] todoChoices = {"不限", "今日截止", "已逾期", "未完成"};
        todo = new ChoiceDock("待办", R.drawable.ic_content_paste, todoChoices, value -> {
            draft.todoScope = value;
            if (value != ScheduleQuery.TODO_ANY) {
                if (draft.category != ScheduleFilterState.HOMEWORK_CATEGORY)
                    draft.category = EventCategory.indexOf(EventCategory.TASK) + 1;
                draft.tab = 3;
            }
        });
        publication = new ChoiceDock("日历状态", R.drawable.ic_inbox_outline, publicationOptions(),
                value -> draft.publication = new int[]{0, 3, 1, 2}[value]);
        source = new ChoiceDock("来源", R.drawable.ic_filter_alt, new String[]{"拾时", "系统日程"}, value -> {
            draft.source = value;
            if (value == 1) {
                draft.category = draft.publication = 0;
                draft.todoScope = ScheduleQuery.TODO_ANY;
            }
        });
    }

    private final class ChoiceDock {
        final DockNavigationLayout dock;
        final String name;
        final String[] choices;
        final java.util.function.IntConsumer changed;
        int selected = -1;

        ChoiceDock(String name, int iconId, String[] choices, java.util.function.IntConsumer changed) {
            this.name = name; this.choices = choices; this.changed = changed;
            LinearLayout heading = row();
            ImageView icon = new ImageView(activity);
            icon.setImageResource(iconId);
            icon.setImageTintList(ColorStateList.valueOf(UiStyle.colors(activity).muted));
            heading.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));
            TextView title = label(name, 13, true);
            title.setPadding(dp(7), 0, 0, 0);
            heading.addView(title);
            UiStyle.addSpaced(body, heading, 6, 2);
            dock = new DockNavigationLayout(activity, this::select, () -> { });
            dock.setPadding(dp(2), dp(3), dp(2), dp(3));
            dock.setOrientation(LinearLayout.HORIZONTAL);
            for (int i = 0; i < choices.length; i++) {
                final int index = i;
                TextView option = label(choices[i], 14, true);
                option.setGravity(Gravity.CENTER);
                option.setPadding(dp(4), dp(6), dp(4), dp(6));
                option.setMinHeight(dp(44));
                option.setOnClickListener(view -> select(index));
                float labelWidth = option.getPaint().measureText(choices[i]) + option.getPaddingLeft() + option.getPaddingRight() + dp(8);
                dock.addItem(option, new LinearLayout.LayoutParams(0, dp(44), labelWidth));
                // Preserve the requested readable size and one-line labels; each cell's
                // share includes its text and padding rather than enforcing equal widths.
                option.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE);
                option.setSingleLine(true);
                option.setHorizontallyScrolling(false);
            }
            UiStyle.addSpaced(body, dock.fullWidthMotionHost(), 0, 6);
        }

        void select(int index) {
            if (!dock.isEnabled()) return;
            changed.accept(index);
            render();
        }

        void sync(int index, boolean enabled) {
            if (selected < 0) dock.setSelectedIndex(index);
            else dock.finishPageSelection(index);
            selected = index;
            dock.setEnabled(enabled);
            dock.setAlpha(enabled ? 1f : .45f);
            dock.setContentDescription(name + "选择栏，" + choices[index] + (enabled ? "" : "，当前来源不可修改"));
            for (int i = 0; i < dock.itemCount(); i++) {
                View option = dock.itemAt(i);
                option.setEnabled(enabled);
                option.setSelected(i == index);
                option.setContentDescription(name + "，" + choices[i]
                        + (i == index ? "，已选中" : "") + (enabled ? "" : "，不可修改"));
            }
        }
    }

    private void pickCustomRange() {
        // Neither child picker dismisses this sheet; cancelling either leaves the draft unchanged.
        picker.pick("起始日期", draft.date, from -> {
            if (!dialog.isShowing()) return;
            picker.pick("结束日期",
                draft.until.isBefore(from) ? from : draft.until, until -> {
                    if (!dialog.isShowing()) return;
                    if (until.isBefore(from)) { Feedback.show(activity, "结束日期不能早于起始日期"); return; }
                    if (draft.source == 1 && until.plusDays(1).isAfter(from.plusYears(1))) {
                        Feedback.show(activity, "系统日历每次最多查询一年"); return;
                    }
                    draft.date = from; draft.until = until; draft.range = 5; draft.tab = 3;
                    render();
                });
        });
    }

    private Button pill(String title, Runnable action, boolean selected) {
        Button button = new Button(activity);
        button.setText(title); button.setAllCaps(false); button.setSingleLine(true);
        button.setTextSize(13); button.setIncludeFontPadding(false);
        button.setMinWidth(0); button.setMinimumWidth(0);
        button.setMinHeight(0); button.setMinimumHeight(dp(38));
        button.setPadding(dp(5), 0, dp(5), 0);
        button.setTextColor(selected ? UiStyle.colors(activity).primary : UiStyle.colors(activity).text);
        UiStyle.acrylicChoice(button, selected, UiStyle.RADIUS_PILL, true);
        UiStyle.pressable(button);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private String[] categoryOptions() {
        String[] options = new String[EventCategory.LABELS.length + 1];
        options[0] = "全部";
        System.arraycopy(EventCategory.LABELS, 0, options, 1, EventCategory.LABELS.length);
        return options;
    }
    private String[] publicationOptions() { return new String[]{"全部", "未写入", "待确认", "已写入"}; }
    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(activity);
        view.setOrientation(LinearLayout.VERTICAL);
        return view;
    }
    private LinearLayout row() { LinearLayout view = new LinearLayout(activity); view.setGravity(Gravity.CENTER_VERTICAL); return view; }
    private TextView label(String title, int size, boolean bold) {
        TextView view = new TextView(activity); view.setText(title); view.setTextSize(size);
        view.setIncludeFontPadding(false); view.setGravity(Gravity.CENTER_VERTICAL);
        view.setTextColor(UiStyle.colors(activity).text);
        if (bold) view.setTypeface(null, android.graphics.Typeface.BOLD);
        return view;
    }
}
