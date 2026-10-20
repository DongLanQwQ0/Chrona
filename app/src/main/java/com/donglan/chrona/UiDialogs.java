package com.donglan.chrona;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.graphics.RenderEffect;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.WeakHashMap;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import com.donglan.chrona.UiStyle.Palette;

/** Shared floating content, selection and modal lifecycle. */
final class UiDialogs {
    private static final long DIALOG_BACKGROUND_SCALE_IN_DURATION_MS = 180L * 2L;
    private static final WeakHashMap<Activity, DialogScaleState> DIALOG_SCALES = new WeakHashMap<>();
    private static final int CHOICE_SHEET_WIDTH_DP = 300;
    private UiDialogs() { }

    private static final class DialogScaleState {
        final View content;
        final float scaleX, scaleY;
        final View.OnAttachStateChangeListener cleanup;
        final java.util.List<View> dialogRoots = new java.util.ArrayList<>();
        int count;
        DialogScaleState(Activity owner, View content) {
            this.content = content;
            scaleX = content.getScaleX();
            scaleY = content.getScaleY();
            count = 1;
            cleanup = new View.OnAttachStateChangeListener() {
                @Override public void onViewAttachedToWindow(View view) { }
                @Override public void onViewDetachedFromWindow(View view) {
                    count = 0;
                    view.animate().cancel();
                    if (Build.VERSION.SDK_INT >= 31) view.setRenderEffect(null);
                    finishDialogScale(owner, DialogScaleState.this);
                }
            };
            content.addOnAttachStateChangeListener(cleanup);
        }
    }

    static void dateDialog(Activity activity, String title, java.time.LocalDate date,
            java.util.function.Consumer<java.time.LocalDate> onSave) {
        Dialog dialog = dialog(activity);
        LinearLayout panel = dialogPanel(activity, title);
        GlassDateTimePickerView picker = new GlassDateTimePickerView(activity, date.atStartOfDay());
        picker.showTime(false);
        panel.addView(picker, new LinearLayout.LayoutParams(-1, dp(panel, 220)));
        LinearLayout actions = actionRow(activity, dialog, "确定", () -> {
            dialog.dismiss(); onSave.accept(picker.value().toLocalDate());
        });
        UiStyle.addSpaced(panel, actions, 12, 0); showFloatingDialog(dialog, panel);
    }

    static void timeDialog(Activity activity, String title, int minutes, IntConsumer onSave) {
        Dialog dialog = dialog(activity);
        LinearLayout panel = dialogPanel(activity, title);
        GlassDateTimePickerView picker = new GlassDateTimePickerView(activity,
                java.time.LocalDate.now().atTime(minutes / 60, minutes % 60));
        picker.showTime(true);
        panel.addView(picker, new LinearLayout.LayoutParams(-1, dp(panel, 220)));
        LinearLayout actions = actionRow(activity, dialog, "保存", () -> {
            java.time.LocalDateTime value = picker.value();
            dialog.dismiss();
            onSave.accept(value.getHour() * 60 + value.getMinute());
        });
        UiStyle.addSpaced(panel, actions, 12, 0);
        showDialog(dialog, panel);
    }

    /** Borderless menu rows share one surface; only adjacent items have a divider. */
    static TextView addMenuItem(LinearLayout parent, String label, boolean selected, Runnable action) {
        if (parent.getChildCount() > 0 && "menu_item".equals(parent.getChildAt(parent.getChildCount()-1).getTag())) {
            View divider = new View(parent.getContext());
            divider.setTag("menu_separator");
            divider.setBackgroundColor(UiStyle.colors(parent.getContext()).outline);
            divider.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            LinearLayout.LayoutParams line = new LinearLayout.LayoutParams(-1, dp(parent,1));
            line.setMargins(dp(parent,14),0,dp(parent,14),0);
            parent.addView(divider,line);
        }
        TextView item = new TextView(parent.getContext());
        item.setTag("menu_item");
        item.setText(label);
        item.setTextSize(16);
        item.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        item.setMinHeight(dp(item,52));
        item.setPadding(dp(item,14),dp(item,12),dp(item,14),dp(item,12));
        item.setTextColor(selected ? UiStyle.colors(parent.getContext()).primary : UiStyle.colors(parent.getContext()).text);
        item.setSelected(selected);
        if (Build.VERSION.SDK_INT >= 30) item.setStateDescription(selected ? "已选中" : null);
        UiStyle.marked(item,selected);
        item.setOnClickListener(v -> action.run());
        UiStyle.pressable(item);
        PressFeedback.apply(item,1f);
        parent.addView(item,new LinearLayout.LayoutParams(-1,-2));
        return item;
    }

    /** A themed, scrollable menu shared by settings, filters and action lists. */
    public static void choiceDialog(Activity activity, String title, String[] options,
            int selected, IntConsumer onChoice) {
        Dialog dialog = dialog(activity);
        LinearLayout panel = dialogPanel(activity, title);
        ScrollView scroll = new ScrollView(activity) {
            @Override protected void onMeasure(int widthSpec,int heightSpec) {
                int limit = Math.round(getResources().getDisplayMetrics().heightPixels * .58f);
                if (View.MeasureSpec.getMode(heightSpec) != View.MeasureSpec.UNSPECIFIED)
                    limit = Math.min(limit,View.MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec,View.MeasureSpec.makeMeasureSpec(limit,View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.VERTICAL);
        int sheetWidth = Math.min(
                (int) (activity.getResources().getDisplayMetrics().widthPixels * .72f),
                dp(panel, CHOICE_SHEET_WIDTH_DP));
        PressFeedback.protectViewport(scroll);
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            addMenuItem(choices,options[i],i==selected,() -> {
                dialog.dismiss();
                onChoice.accept(index);
            });
        }
        scroll.addView(choices);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1,-2));
        showDialog(dialog, panel, sheetWidth);
    }

    public static void confirmDialog(Activity activity, String title, String message,
            String positive, Runnable onConfirm) {
        Dialog dialog = dialog(activity);
        LinearLayout panel = dialogPanel(activity, title);
        TextView explanation = new TextView(activity);
        explanation.setText(message);
        explanation.setTextSize(15);
        UiStyle.muted(explanation);
        UiStyle.addSpaced(panel, explanation, 4, 20);
        LinearLayout actions = actionRow(activity, dialog, positive, () -> {
            dialog.dismiss();
            onConfirm.run();
        });
        panel.addView(actions);
        showDialog(dialog, panel);
    }

    static void textEditorDialog(Activity activity, String title, String initialValue,
            String positive, Consumer<String> onSave) {
        textEditorDialog(activity, title, initialValue, positive, null, onSave);
    }

    static void textEditorDialog(Activity activity, String title, String initialValue,
            String positive, String hint, Consumer<String> onSave) {
        Dialog dialog = dialog(activity);
        LinearLayout panel = dialogPanel(activity, title);
        if (hint != null && !hint.isEmpty()) {
            TextView explanation = new TextView(activity);
            explanation.setText(hint);
            explanation.setTextSize(12);
            UiStyle.muted(explanation);
            UiStyle.addSpaced(panel, explanation, 0, 10);
        }
        EditText editor = new EditText(activity);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setGravity(Gravity.TOP | Gravity.START);
        editor.setMinLines(4);
        editor.setMaxLines(9);
        editor.setText(initialValue == null ? "" : initialValue);
        editor.setSelection(editor.length());
        UiStyle.input(editor);
        UiStyle.addSpaced(panel, editor, 0, 16);
        LinearLayout actions = actionRow(activity, dialog, positive, () -> {
            String value = editor.getText().toString();
            dialog.dismiss();
            onSave.accept(value);
        });
        panel.addView(actions);
        showDialog(dialog, panel);
        editor.requestFocus();
    }

    /** One geometry for paired modal actions; each caller owns its value and dismiss order. */
    private static LinearLayout actionRow(Activity activity, Dialog dialog, String positive, Runnable action) {
        LinearLayout actions = new LinearLayout(activity);
        Button cancel = new Button(activity);
        cancel.setText("取消");
        UiStyle.button(cancel, false);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(cancel, 52), 1));
        Button submit = new Button(activity);
        submit.setText(positive);
        UiStyle.button(submit, true);
        submit.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(submit, 52), 1);
        params.leftMargin = dp(submit, 8);
        actions.addView(submit, params);
        return actions;
    }

    private static Dialog dialog(Activity activity) {
        Dialog dialog = new Dialog(activity);
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            android.view.WindowManager.LayoutParams params = window.getAttributes();
            params.dimAmount = 0.44f;
            window.setAttributes(params);
        }
        return dialog;
    }

    static LinearLayout dialogPanel(Activity activity, String title) {
        LinearLayout panel = new LinearLayout(activity);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(panel, 20), dp(panel, 20), dp(panel, 20), dp(panel, 20));
        UiStyle.glass(panel);
        TextView heading = new TextView(activity);
        heading.setText(title);
        heading.setTextSize(21);
        UiStyle.title(heading);
        UiStyle.addSpaced(panel, heading, 0, 12);
        return panel;
    }

    /** Long modal content scrolls; actions remain outside the scrolling area. */
    static void dialogBody(LinearLayout panel, View body) {
        ScrollView scroll = new ScrollView(panel.getContext()) {
            @Override protected void onMeasure(int widthSpec, int heightSpec) {
                int limit = Math.round(getResources().getDisplayMetrics().heightPixels * .52f);
                if (View.MeasureSpec.getMode(heightSpec) != View.MeasureSpec.UNSPECIFIED)
                    limit = Math.min(limit, View.MeasureSpec.getSize(heightSpec));
                super.onMeasure(widthSpec, View.MeasureSpec.makeMeasureSpec(limit, View.MeasureSpec.AT_MOST));
            }
        };
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(body);
        UiStyle.addSpaced(panel, scroll, 0, 12);
    }

    private static void showDialog(Dialog dialog, View content) {
        showDialog(dialog, content, Math.min(
                (int) (content.getResources().getDisplayMetrics().widthPixels * .90f),
                dp(content, 460)));
    }

    private static void showDialog(Dialog dialog, View content, int widthPx) {
        dialog.setContentView(content);
        Window dialogWindow = dialog.getWindow();
        if (dialogWindow != null) {
            // Some callers create Dialog directly instead of using dialog(Activity). Clear the
            // framework's rectangular window surface so the rounded panel is the outer edge.
            dialogWindow.setBackgroundDrawableResource(android.R.color.transparent);
            dialogWindow.setWindowAnimations(ValueAnimator.areAnimatorsEnabled()
                    ? R.style.Animation_Chrona_Dialog : 0);
        }
        Activity owner = content.getContext() instanceof Activity activity ? activity : null;
        if (owner != null) {
            beginDialogScale(owner);
            registerDialogLayer(owner, content);
            applyDialogSurface(content, owner);
            boolean dialogAcrylic = Build.VERSION.SDK_INT >= 31
                    && ThemeStore.acrylicEnabled(owner);
            if (dialogAcrylic) UiSurfaces.setDialogBlurRoot(content, true);
            dialog.setOnDismissListener(ignored -> {
                if (dialogAcrylic) UiSurfaces.setDialogBlurRoot(content, false);
                removeDialogLayer(owner, content);
                endDialogScale(owner);
            });
        }
        dialog.show();
        PressFeedback.install(content);
        Window window = dialog.getWindow();
        if (window != null) window.setLayout(widthPx, -2);
        // The window owns both enter and exit, including outside-tap and system-back dismissals.
        // No delayed dismiss callback can outlive the Activity or block a confirmation action.
    }

    /** Applies the shared backdrop blur and page scale to a custom floating dialog. */
    static void showFloatingDialog(Dialog dialog, View content) {
        showDialog(dialog, content);
    }

    private static void applyDialogSurface(View content, Activity owner) {
        if (Build.VERSION.SDK_INT >= 31 && ThemeStore.acrylicEnabled(owner)) {
            Palette palette = UiStyle.colors(content.getContext());
            boolean dark = ThemeStore.dark(content.getContext());
            // Lower dialog layers are already blurred. Keep the same translucent surface at
            // every depth so a child picker does not hide that blur behind an opaque panel.
            content.setBackground(UiSurfaces.surfaceDrawable(content, palette, UiStyle.RADIUS_PANEL,
                    dark ? 112 : 124, dark ? 100 : 112));
            content.setElevation(dp(content, 8));
        } else {
            UiStyle.glass(content);
        }
    }

    /** A child modal obscures the previous dialog window as well as the Activity behind it. */
    private static void registerDialogLayer(Activity owner, View root) {
        synchronized (DIALOG_SCALES) {
            DialogScaleState state = DIALOG_SCALES.get(owner);
            if (state == null) return;
            for (View parent : state.dialogRoots) {
                parent.animate().cancel();
                parent.setScaleX(1f);
                parent.setScaleY(1f);
                parent.setAlpha(.4f);
                if (Build.VERSION.SDK_INT >= 31 && ThemeStore.acrylicEnabled(owner))
                    parent.setRenderEffect(RenderEffect.createBlurEffect(dp(parent, 12), dp(parent, 12),
                            android.graphics.Shader.TileMode.CLAMP));
            }
            state.dialogRoots.add(root);
        }
    }

    private static void removeDialogLayer(Activity owner, View root) {
        synchronized (DIALOG_SCALES) {
            DialogScaleState state = DIALOG_SCALES.get(owner);
            if (state == null) return;
            state.dialogRoots.remove(root);
            // These sheet roots have no persistent effects; only the top layer is interactive.
            if (!state.dialogRoots.isEmpty()) {
                View parent = state.dialogRoots.get(state.dialogRoots.size() - 1);
                if (Build.VERSION.SDK_INT >= 31) parent.setRenderEffect(null);
                parent.setAlpha(1f);
            }
        }
    }

    private static void beginDialogScale(Activity activity) {
        if (activity.isFinishing() || activity.isDestroyed())
            return;
        DialogScaleState state;
        synchronized (DIALOG_SCALES) {
            state = DIALOG_SCALES.get(activity);
            if (state != null) {
                if (state.count++ > 0) return;
            } else {
                View content = activity.findViewById(android.R.id.content);
                if (content == null) return;
                // Keep this baseline until the last restoration finishes, including reversals.
                state = new DialogScaleState(activity, content);
                DIALOG_SCALES.put(activity, state);
            }
        }
        if (Build.VERSION.SDK_INT >= 31 && ThemeStore.acrylicEnabled(activity)) {
            state.content.setRenderEffect(RenderEffect.createBlurEffect(dp(state.content, 13),
                    dp(state.content, 13), android.graphics.Shader.TileMode.CLAMP));
        }
        if (!ValueAnimator.areAnimatorsEnabled()) return;
        state.content.animate().cancel();
        state.content.animate().scaleX(state.scaleX * 1.04f).scaleY(state.scaleY * 1.04f)
                .setDuration(DIALOG_BACKGROUND_SCALE_IN_DURATION_MS)
                .setInterpolator(new android.view.animation.DecelerateInterpolator())
                .start();
    }

    private static void endDialogScale(Activity activity) {
        DialogScaleState state;
        synchronized (DIALOG_SCALES) {
            state = DIALOG_SCALES.get(activity);
            if (state == null) return;
            if (--state.count > 0) return;
        }
        View content = state.content;
        content.animate().cancel();
        if (Build.VERSION.SDK_INT >= 31) content.setRenderEffect(null);
        if (!ValueAnimator.areAnimatorsEnabled() || activity.isFinishing() || activity.isDestroyed()) {
            finishDialogScale(activity, state);
        } else {
            content.animate().scaleX(state.scaleX).scaleY(state.scaleY).setDuration(160)
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .withEndAction(() -> finishDialogScale(activity, state)).start();
        }
    }

    private static void finishDialogScale(Activity activity, DialogScaleState state) {
        synchronized (DIALOG_SCALES) {
            if (DIALOG_SCALES.get(activity) != state || state.count > 0) return;
            DIALOG_SCALES.remove(activity);
        }
        state.content.removeOnAttachStateChangeListener(state.cleanup);
        state.content.setScaleX(state.scaleX);
        state.content.setScaleY(state.scaleY);
    }

    private static int dp(View view, int value) {
        return (int) (value * view.getResources().getDisplayMetrics().density + 0.5f);
    }
}
