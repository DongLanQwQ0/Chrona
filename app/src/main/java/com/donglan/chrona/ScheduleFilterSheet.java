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
import android.widget.FrameLayout;
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
    private int renderGeneration;
    private TextView intervalLabel;
    private final java.util.Map<Integer, Expansion> expansions = new java.util.HashMap<>();

    private static final class Expansion {
        final LinearLayout options;
        final ImageView arrow;
        android.animation.ValueAnimator animator;
        Expansion(LinearLayout options, ImageView arrow) { this.options = options; this.arrow = arrow; }
    }

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
        renderGeneration++;
        for (Expansion item : expansions.values()) if (item.animator != null) item.animator.cancel();
        expansions.clear();
        intervalLabel = null;
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
        LinearLayout.LayoutParams presetsParams = new LinearLayout.LayoutParams(-1, -2);
        presetsParams.bottomMargin = dp(12);
        body.addView(presets, presetsParams);
        LocalDate[] window = draft.window(LocalDate.now());
        if (window[0] != null) {
            FrameLayout interval = new FrameLayout(activity);
            interval.setClipChildren(true);
            UiStyle.acrylicChoice(interval, false, UiStyle.RADIUS_PILL, true);
            interval.setOnClickListener(view -> pickCustomRange());
            FrameLayout labelHost = new FrameLayout(activity);
            labelHost.setClipChildren(true);
            FrameLayout.LayoutParams labelHostParams = new FrameLayout.LayoutParams(-1, -1);
            labelHostParams.setMargins(dp(30), dp(4), dp(30), dp(4));
            interval.addView(labelHost, labelHostParams);
            TextView value = label(window[0] + " — " + window[1].minusDays(1), 13, false);
            value.setGravity(Gravity.CENTER);
            value.setMaxLines(2);
            labelHost.addView(value, new FrameLayout.LayoutParams(-1, -1));
            intervalLabel = value;
            for (int side = 0; side < 2; side++) {
                ImageView arrow = new ImageView(activity);
                arrow.setImageResource(side == 0 ? R.drawable.ic_chevron_left : R.drawable.ic_chevron_right);
                arrow.setImageTintList(ColorStateList.valueOf(UiStyle.colors(activity).muted));
                FrameLayout.LayoutParams arrowParams = new FrameLayout.LayoutParams(dp(20), dp(20),
                        Gravity.CENTER_VERTICAL | (side == 0 ? Gravity.LEFT : Gravity.RIGHT));
                arrowParams.leftMargin = arrowParams.rightMargin = dp(8);
                interval.addView(arrow, arrowParams);
            }
            installIntervalSwipe(interval, value);
            LinearLayout.LayoutParams intervalParams = new LinearLayout.LayoutParams(-1, dp(48));
            intervalParams.bottomMargin = dp(12);
            body.addView(interval, intervalParams);
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
        ImageView arrow = new ImageView(activity);
        if (enabled) {
            arrow.setImageResource(R.drawable.ic_chevron_down);
            arrow.setRotation(expanded == field ? 180f : 0f);
            arrow.setImageTintList(ColorStateList.valueOf(UiStyle.colors(activity).muted));
            LinearLayout.LayoutParams arrowParams = new LinearLayout.LayoutParams(dp(18), dp(18));
            arrowParams.leftMargin = dp(6);
            setting.addView(arrow, arrowParams);
            setting.setContentDescription(name + "，" + value + "，展开选项");
            setting.setOnClickListener(view -> toggleExpansion(field));
        }
        body.addView(setting);
        if (enabled) {
            LinearLayout choicesHost = column();
            for (int i = 0; i < choices.length; i += 3) {
                LinearLayout options = row();
                for (int j = i; j < Math.min(i + 3, choices.length); j++) {
                    final int index = j;
                    Button option = pill(choices[j], () -> {
                        chosen.accept(index);
                        // Synchronize labels/selected styles now, independent of collapse completion.
                        render();
                        expanded = -1;
                        animateExpansion(expansions.get(field), false);
                    }, j == selected);
                    LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(38), 1);
                    params.setMargins(0, 0, dp(4), 0);
                    options.addView(option, params);
                }
                UiStyle.addSpaced(choicesHost, options, 0, 5);
            }
            choicesHost.setVisibility(expanded == field ? View.VISIBLE : View.GONE);
            body.addView(choicesHost, new LinearLayout.LayoutParams(-1, -2));
            expansions.put(field, new Expansion(choicesHost, arrow));
        }
    }

    private void toggleExpansion(int field) {
        int previous = expanded;
        expanded = previous == field ? -1 : field;
        if (previous != -1) animateExpansion(expansions.get(previous), false);
        if (expanded != -1) animateExpansion(expansions.get(expanded), true);
    }

    private void animateExpansion(Expansion item, boolean opening) {
        if (item == null) return;
        if (item.animator != null) item.animator.cancel();
        LinearLayout options = item.options;
        options.measure(View.MeasureSpec.makeMeasureSpec(body.getWidth(), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        // A synchronous render creates a visible host before its first layout.
        int initial = options.getVisibility() == View.GONE ? 0
                : options.isLaidOut() ? options.getHeight() : options.getMeasuredHeight();
        float initialAlpha = initial == 0 ? 0f : options.getAlpha();
        int target = opening ? options.getMeasuredHeight() : 0;
        item.arrow.animate().cancel();
        item.arrow.animate().rotation(opening ? 180f : 0f).setDuration(220).start();
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            options.setVisibility(opening ? View.VISIBLE : View.GONE);
            options.getLayoutParams().height = -2;
            options.setAlpha(1f);
            options.requestLayout();
            return;
        }
        int generation = renderGeneration;
        options.setVisibility(View.VISIBLE);
        android.animation.ValueAnimator animator = android.animation.ValueAnimator.ofInt(initial, target);
        item.animator = animator;
        animator.setDuration(220);
        animator.setInterpolator(new android.view.animation.DecelerateInterpolator());
        animator.addUpdateListener(value -> {
            int height = (int) value.getAnimatedValue();
            options.getLayoutParams().height = height;
            float targetAlpha = opening ? 1f : 0f;
            options.setAlpha(initialAlpha + (targetAlpha - initialAlpha) * value.getAnimatedFraction());
            options.requestLayout();
        });
        animator.addListener(new android.animation.AnimatorListenerAdapter() {
            boolean cancelled;
            @Override public void onAnimationCancel(android.animation.Animator animation) { cancelled = true; }
            @Override public void onAnimationEnd(android.animation.Animator animation) {
                if (cancelled || generation != renderGeneration) return;
                item.animator = null;
                options.setVisibility(opening ? View.VISIBLE : View.GONE);
                options.getLayoutParams().height = -2;
                options.setAlpha(1f);
                options.requestLayout();
            }
        });
        animator.start();
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

    private void installIntervalSwipe(View interval, TextView value) {
        interval.setContentDescription("日期区间，左右滑动切换，点击修改起止日期");
        interval.setOnTouchListener(new View.OnTouchListener() {
            float x, y, initialTranslation; boolean horizontal;
            @Override public boolean onTouch(View view, android.view.MotionEvent event) {
                float dx = event.getX() - x, dy = event.getY() - y;
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        value.animate().cancel();
                        LocalDate[] active = draft.window(LocalDate.now());
                        if (active[0] != null) value.setText(active[0] + " — " + active[1].minusDays(1));
                        value.setAlpha(1f);
                        initialTranslation = value.getTranslationX();
                        x = event.getX(); y = event.getY(); horizontal = false; break;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(dx) > dp(10) && Math.abs(dx) > Math.abs(dy)) {
                            horizontal = true;
                            view.getParent().requestDisallowInterceptTouchEvent(true);
                            view.setPressed(false);
                        }
                        if (horizontal) value.setTranslationX(Math.max(-value.getWidth(),
                                Math.min(value.getWidth(), initialTranslation + dx)));
                        return horizontal;
                    case MotionEvent.ACTION_UP:
                        horizontal |= Math.abs(dx) >= dp(36) && Math.abs(dx) > Math.abs(dy);
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        if (horizontal) {
                            view.setPressed(false);
                            if (Math.abs(dx) >= dp(36)) {
                                finishIntervalSwipe(value, dx);
                            } else settleInterval(value);
                            return true;
                        }
                        value.setTranslationX(0f);
                        break;
                    case MotionEvent.ACTION_CANCEL:
                        view.getParent().requestDisallowInterceptTouchEvent(false);
                        view.setPressed(false);
                        settleInterval(value);
                        return horizontal;
                    default: break;
                }
                return false;
            }
        });
    }

    private void settleInterval(TextView value) {
        value.animate().translationX(0f).alpha(1f).setDuration(180).start();
    }

    private void finishIntervalSwipe(TextView value, float distance) {
        int direction = distance < 0 ? 1 : -1;
        LocalDate today = LocalDate.now();
        ScheduleFilterState next = draft.copy();
        if (!next.shift(direction, today)) {
            Feedback.show(activity, "系统日历每次最多查询一年");
            settleInterval(value);
            return;
        }
        int generation = renderGeneration;
        // Apply must see the released gesture immediately, even if the exit animation is interrupted.
        draft.shift(direction, today);
        if (!android.animation.ValueAnimator.areAnimatorsEnabled()) {
            render(); return;
        }
        value.animate().translationX(direction > 0 ? -value.getWidth() : value.getWidth())
                .alpha(0f).setDuration(110).withEndAction(() -> {
                    if (generation != renderGeneration || !dialog.isShowing()) return;
                    render();
                    TextView incoming = intervalLabel;
                    if (incoming == null) return;
                    incoming.setTranslationX(direction > 0 ? value.getWidth() : -value.getWidth());
                    incoming.setAlpha(0f);
                    incoming.animate().translationX(0f).alpha(1f).setDuration(180).start();
                }).start();
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
