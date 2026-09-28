package com.donglan.chrona;

import android.app.Activity;
import android.app.Dialog;
import android.content.res.ColorStateList;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.donglan.chrona.data.EventCategory;
import com.donglan.chrona.data.ScheduleFilterState;
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
    private int expanded = -1;
    private int scrollY;

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
    }

    private void show() {
        LinearLayout panel = column();
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));
        UiStyle.glass(panel);
        LinearLayout header = row();
        TextView title = label("筛选日程", 18, true);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(40), 1));
        TextView reset = label("重置", 13, false);
        reset.setTextColor(UiStyle.colors(activity).primary);
        reset.setGravity(Gravity.CENTER);
        reset.setPadding(dp(10), 0, dp(10), 0);
        reset.setOnClickListener(view -> { draft.reset(LocalDate.now()); expanded = -1; render(); });
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
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body);
        scroll.setOnScrollChangeListener((view, x, y, oldX, oldY) -> scrollY = y);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
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
        body.removeAllViews();
        TextView dates = label("日期", 13, true);
        dates.setTextColor(UiStyle.colors(activity).muted);
        body.addView(dates, new LinearLayout.LayoutParams(-1, dp(28)));
        LinearLayout presets = row();
        String[] names = {"全部", "今天", "本周", "本月", "自定义"};
        int[] values = {0, 1, 2, 3, 5};
        for (int i = 0; i < names.length; i++) {
            final int range = values[i];
            Button option = pill(names[i], () -> {
                if (range == ScheduleFilterState.CUSTOM) pickCustomRange();
                else {
                    draft.range = range; draft.date = LocalDate.now();
                    if (range != ScheduleFilterState.ALL) draft.tab = 3;
                    render();
                }
            }, draft.range == range || (range == 5 && draft.range == 4));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1);
            if (i != names.length - 1) params.rightMargin = dp(4);
            presets.addView(option, params);
        }
        body.addView(presets);
        LocalDate[] window = draft.window(LocalDate.now());
        if (window[0] != null) {
            Button interval = pill(window[0] + " — " + window[1].minusDays(1),
                    this::pickCustomRange, false);
            interval.setSingleLine(false);
            interval.setMaxLines(2);
            interval.setPadding(dp(8), dp(6), dp(8), dp(6));
            interval.setTextColor(UiStyle.colors(activity).text);
            installIntervalSwipe(interval);
            UiStyle.addSpaced(body, interval, 8, 0);
            TextView hint = label("左右滑动切换 · 点击修改", 11, false);
            hint.setTextColor(UiStyle.colors(activity).muted);
            hint.setGravity(Gravity.CENTER);
            UiStyle.addSpaced(body, hint, 3, 8);
        }
        addSetting("类型", draft.source == 1 ? "系统活动" : categoryOptions()[draft.category],
                R.drawable.ic_event, 0, categoryOptions(), draft.category, value -> draft.category = value,
                draft.source == 0);
        addSetting("日历状态", draft.source == 1 ? "已写入" : publicationOptions()[draft.publication],
                R.drawable.ic_inbox_outline, 1, publicationOptions(), draft.publication, value -> draft.publication = value,
                draft.source == 0);
        addSetting("来源", draft.source == 0 ? "拾时" : "系统日程", R.drawable.ic_filter_alt,
                2, new String[]{"拾时", "系统日程"}, draft.source, value -> {
                    draft.source = value;
                    if (value == 1) { draft.category = draft.publication = 0; }
                }, true);
        if (body.getParent() instanceof ScrollView scroll) {
            int previous = scrollY;
            scroll.post(() -> scroll.scrollTo(0, previous));
        }
    }

    private void addSetting(String name, String value, int iconId, int field,
            String[] choices, int selected, java.util.function.IntConsumer chosen, boolean enabled) {
        View divider = new View(activity);
        divider.setBackgroundColor(UiStyle.colors(activity).muted);
        divider.setAlpha(.15f);
        body.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        LinearLayout setting = row();
        setting.setMinimumHeight(dp(48));
        ImageView icon = new ImageView(activity);
        icon.setImageResource(iconId);
        icon.setImageTintList(ColorStateList.valueOf(UiStyle.colors(activity).muted));
        setting.addView(icon, new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView title = label(name, 14, false);
        title.setPadding(dp(8), 0, 0, 0);
        setting.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView current = label(value, 14, false);
        current.setTextColor(UiStyle.colors(activity).muted);
        setting.addView(current);
        if (enabled) {
            ImageView arrow = new ImageView(activity);
            arrow.setImageResource(expanded == field ? R.drawable.ic_expand_less : R.drawable.ic_chevron_down);
            arrow.setImageTintList(ColorStateList.valueOf(UiStyle.colors(activity).muted));
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            arrowParams.leftMargin = dp(6);
            setting.addView(arrow, arrowParams);
            setting.setContentDescription(name + "，" + value + "，展开选项");
            setting.setOnClickListener(view -> { expanded = expanded == field ? -1 : field; render(); });
        }
        body.addView(setting);
        if (enabled && expanded == field) {
            for (int i = 0; i < choices.length; i += 3) {
                LinearLayout options = row();
                for (int j = i; j < Math.min(i + 3, choices.length); j++) {
                    final int index = j;
                    Button option = pill(choices[j], () -> {
                        chosen.accept(index); expanded = -1; render();
                    }, j == selected);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1);
                    params.setMargins(0, 0, dp(4), 0);
                    options.addView(option, params);
                }
                UiStyle.addSpaced(body, options, 0, 5);
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

    private void installIntervalSwipe(View interval) {
        interval.setContentDescription("日期区间，左右滑动切换，点击修改起止日期");
        interval.setOnTouchListener(new View.OnTouchListener() {
            float x, y; boolean horizontal;
            @Override public boolean onTouch(View view, android.view.MotionEvent event) {
                float dx = event.getX() - x, dy = event.getY() - y;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        x = event.getX(); y = event.getY(); horizontal = false; break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(dx) > dp(10) && Math.abs(dx) > Math.abs(dy)) {
                            horizontal = true;
                            view.getParent().requestDisallowInterceptTouchEvent(true);
                            view.setPressed(false);
                        }
                        return horizontal;
                    case MotionEvent.ACTION_UP:
                        horizontal |= Math.abs(dx) >= dp(36) && Math.abs(dx) > Math.abs(dy);
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        if (horizontal) {
                            view.setPressed(false);
                            if (Math.abs(dx) >= dp(36)) {
                                if (draft.shift(dx < 0 ? 1 : -1, LocalDate.now())) render();
                                else Feedback.show(activity, "系统日历每次最多查询一年");
                            }
                            return true;
                        }
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        view.setPressed(false); return horizontal;
                    default: break;
                }
                return false;
            }
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
    private String[] publicationOptions() { return new String[]{"全部", "待确认", "已写入"}; }
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
