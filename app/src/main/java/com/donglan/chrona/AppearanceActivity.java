package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.app.Dialog;
import android.os.Bundle;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Switch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Selects a durable mode and palette, with immediate visual feedback. */
public final class AppearanceActivity extends Activity {
    private static final int PICK_BACKGROUND = 23;
    /** One preset of the two-column colour grid. */
    private static final class ColorOption {
        final String name;
        final String description;
        final String key;
        ColorOption(String name, String description, String key) {
            this.name = name;
            this.description = description;
            this.key = key;
        }
    }
    private ScrollView page;
    private FrameLayout stage;
    private GlassBackdropView backdrop;
    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        buildAppearance(state == null ? 0 : state.getInt("scroll_y"));
    }

    /** Updates the appearance page in place and keeps its scroll position. */
    void refreshAppearance() {
        ThemeStore.apply(this);
        buildAppearance(page == null ? 0 : page.getScrollY());
    }

    private void buildAppearance(int scrollY) {
        if (page == null) page = new ScrollView(this);
        else page.removeAllViews();
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(8), dp(20), dp(28));
        UiStyle.page(this, root);
        root.setFitsSystemWindows(false);
        root.setBackgroundColor(Color.TRANSPARENT);
        page.addView(root);
        UiStyle.back(this, root);

        TextView title = text("外观与配色", 28, true);
        root.addView(title);
        TextView lead = text("选一个看着舒服的样子。更改会立即应用到整个应用。", 15, false);
        UiStyle.addSpaced(root, lead, 8, 18);

        heading(root, "显示模式");
        option(root, "跟随系统", "随设备的浅色或深色模式切换",
                ThemeStore.SYSTEM.equals(ThemeStore.mode(this)),
                () -> selectMode(ThemeStore.SYSTEM));
        option(root, "浅色", "清爽明亮的页面",
                ThemeStore.LIGHT.equals(ThemeStore.mode(this)),
                () -> selectMode(ThemeStore.LIGHT));
        option(root, "深色", "适合夜间使用",
                ThemeStore.DARK.equals(ThemeStore.mode(this)),
                () -> selectMode(ThemeStore.DARK));

        heading(root, "主题配色");
        List<ColorOption> colors = new ArrayList<>();
        if (ThemeStore.wallpaperAvailable())
            colors.add(new ColorOption("壁纸配色", "跟随 Android 系统配色", ThemeStore.WALLPAPER));
        colors.add(new ColorOption("青绿", "拾时经典", ThemeStore.TEAL));
        colors.add(new ColorOption("晴蓝", "明朗沉静", ThemeStore.BLUE));
        colors.add(new ColorOption("暖珊瑚", "柔和温暖", ThemeStore.CORAL));
        colors.add(new ColorOption("紫罗兰", "清晰而沉静", ThemeStore.PURPLE));
        colors.add(new ColorOption("琥珀", "温暖明亮", ThemeStore.AMBER));
        colors.add(new ColorOption("玫瑰", "柔和醒目", ThemeStore.ROSE));
        colors.add(new ColorOption("森林绿", "自然沉稳", ThemeStore.FOREST));
        colors.add(new ColorOption("自定义颜色", ThemeStore.customColorHex(this), ThemeStore.CUSTOM));
        LinearLayout colorGrid = new LinearLayout(this);
        colorGrid.setOrientation(LinearLayout.VERTICAL);
        UiStyle.addSpaced(root, colorGrid, 2, 4);
        LinearLayout colorRow = null;
        for (int index = 0; index < colors.size(); index++) {
            if (index % 2 == 0) {
                colorRow = new LinearLayout(this);
                colorRow.setOrientation(LinearLayout.HORIZONTAL);
                colorRow.setBaselineAligned(false);
                colorGrid.addView(colorRow, new LinearLayout.LayoutParams(-1, -2));
            }
            ColorOption item = colors.get(index);
            themeOption(colorRow, item, item.key.equals(ThemeStore.color(this)),
                    () -> selectColor(item.key));
        }
        // An odd preset count would leave the final cell stretched across the row.
        if (colorRow != null && colors.size() % 2 == 1)
            colorRow.addView(new View(this), new LinearLayout.LayoutParams(0, dp(1), 1));
        heading(root, "毛玻璃与层级");
        acrylicControls(root);
        heading(root, "背景图片");
        option(root, "选择本地图片", null,
                ThemeStore.background(this) != null, this::pickBackground);
        if (ThemeStore.background(this) != null) {
            option(root, "恢复默认背景", null, false, () -> {
                releaseBackgroundGrant();
                ThemeStore.setBackground(this, null);
            });
        }
        if (stage == null) {
            stage = new FrameLayout(this);
            backdrop = new GlassBackdropView(this);
            stage.addView(backdrop, new FrameLayout.LayoutParams(-1, -1));
            stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
            setContentView(stage);
            UiStyle.applyInsets(stage, page);
        }
        if (backdrop != null) backdrop.refreshTheme();
        stage.invalidate();
        restoreScrollBeforeDraw(scrollY);
    }

    private void restoreScrollBeforeDraw(int scrollY) {
        ScrollView target = page;
        ViewTreeObserver observer = target.getViewTreeObserver();
        if (!observer.isAlive()) {
            target.post(() -> target.scrollTo(0, scrollY));
            return;
        }
        ViewTreeObserver.OnPreDrawListener[] listener = new ViewTreeObserver.OnPreDrawListener[1];
        listener[0] = () -> {
            ViewTreeObserver current = target.getViewTreeObserver();
            if (current.isAlive()) current.removeOnPreDrawListener(listener[0]);
            target.scrollTo(0, scrollY);
            return true;
        };
        observer.addOnPreDrawListener(listener[0]);
        target.invalidate();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("scroll_y", page.getScrollY());
    }

    private void selectMode(String mode) {
        ThemeStore.setMode(this, mode);
    }
    private void selectColor(String color) {
        ThemeStore.setColor(this, color);
    }

    private void pickBackground() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_BACKGROUND);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != PICK_BACKGROUND || resultCode != RESULT_OK
                || data == null || data.getData() == null) return;
        try {
            getContentResolver().takePersistableUriPermission(data.getData(),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
            if (!data.getData().toString().equals(ThemeStore.background(this)))
                releaseBackgroundGrant();
            ThemeStore.setBackground(this, data.getData().toString());
        } catch (Exception exception) {
            Feedback.showLong(this, "无法读取所选图片：" + exception.getMessage());
        }
    }

    private void releaseBackgroundGrant() {
        String old = ThemeStore.background(this);
        if (old == null) return;
        try {
            getContentResolver().releasePersistableUriPermission(Uri.parse(old),
                    Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // The document provider may already have revoked the old grant.
        }
    }

    private void heading(LinearLayout root, String label) {
        UiStyle.addSpaced(root, text(label, 19, true), 16, 10);
    }

    private void acrylicControls(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(14), dp(18), dp(16));
        UiStyle.glass(card);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout labels = new LinearLayout(this);
        labels.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("启用背景模糊", 16, true);
        labels.addView(title);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(ThemeStore.acrylicEnabled(this));
        toggle.setThumbTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        toggle.setTrackTintList(ColorStateList.valueOf(
                withAlpha(UiStyle.colors(this).primary, 96)));
        row.addView(toggle);
        card.addView(row);
        toggle.setOnCheckedChangeListener((button, checked) ->
                ThemeStore.setAcrylicEnabled(this, checked));

        TextView blurLabel = text("模糊方式", 15, true);
        UiStyle.addSpaced(card, blurLabel, 14, 4);
        LinearLayout blurChoices = new LinearLayout(this);
        blurChoices.setOrientation(LinearLayout.HORIZONTAL);
        boolean gaussian = ThemeStore.gaussianBlur(this);
        TextView ordinary = blurOption("普通模糊", !gaussian);
        TextView gaussianOption = blurOption("高斯模糊", gaussian);
        ordinary.setOnClickListener(view -> ThemeStore.setGaussianBlur(this, false));
        gaussianOption.setOnClickListener(view -> ThemeStore.setGaussianBlur(this, true));
        blurChoices.addView(ordinary, new LinearLayout.LayoutParams(0, dp(46), 1));
        LinearLayout.LayoutParams gaussianParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        gaussianParams.setMargins(dp(8), 0, 0, 0);
        blurChoices.addView(gaussianOption, gaussianParams);
        card.addView(blurChoices);

        TextView strengthLabel = text("强度 · " + ThemeStore.blurStrength(this) + " / 5",
                15, true);
        UiStyle.addSpaced(card, strengthLabel, 12, 0);
        SeekBar strengthSlider = new SeekBar(this);
        tintSeekBar(strengthSlider);
        strengthSlider.setMax(4);
        strengthSlider.setProgress(ThemeStore.blurStrength(this) - 1);
        card.addView(strengthSlider, new LinearLayout.LayoutParams(-1, dp(44)));
        strengthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                int value = progress + 1;
                strengthLabel.setText("强度 · " + value + " / 5");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                ThemeStore.setBlurStrength(AppearanceActivity.this,
                        seekBar.getProgress() + 1);
            }
        });

        TextView mixLabel = text("浓度 · " + ThemeStore.surfaceMix(this) + "%", 15, true);
        UiStyle.addSpaced(card, mixLabel, 10, 0);
        SeekBar mixSlider = new SeekBar(this);
        tintSeekBar(mixSlider);
        mixSlider.setMax(60);
        mixSlider.setProgress(ThemeStore.surfaceMix(this) - 10);
        card.addView(mixSlider, new LinearLayout.LayoutParams(-1, dp(44)));
        mixSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                int value = progress + 10;
                mixLabel.setText("浓度 · " + value + "%");
                if (fromUser) ThemeStore.previewSurfaceMix(AppearanceActivity.this, value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        UiStyle.addSpaced(root, card, 4, 7);
    }

    private TextView blurOption(String label, boolean selected) {
        TextView option = text(label, 14, true);
        option.setGravity(android.view.Gravity.CENTER);
        UiStyle.pill(option, selected);
        option.setTextColor(selected ? UiStyle.colors(this).onPrimaryContainer
                : UiStyle.colors(this).text);
        return option;
    }

    private void tintSeekBar(SeekBar seekBar) {
        int color = UiStyle.colors(this).primary;
        seekBar.setThumbTintList(ColorStateList.valueOf(color));
        seekBar.setProgressTintList(ColorStateList.valueOf(color));
        seekBar.setProgressBackgroundTintList(ColorStateList.valueOf(
                withAlpha(color, 72)));
    }

    private int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private void option(LinearLayout root, String name, String description, boolean selected,
            Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(15), dp(18), dp(15));
        UiStyle.choice(card, selected, UiStyle.RADIUS_PANEL);
        UiStyle.pressable(card);
        LinearLayout headline = new LinearLayout(this);
        headline.setOrientation(LinearLayout.HORIZONTAL);
        headline.setGravity(android.view.Gravity.CENTER_VERTICAL);
        int swatch = swatch(name);
        if (swatch != 0) {
            View color = new View(this);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(swatch);
            color.setBackground(circle);
            LinearLayout.LayoutParams dot = new LinearLayout.LayoutParams(dp(24), dp(24));
            dot.setMargins(0, 0, dp(10), 0);
            headline.addView(color, dot);
        }
        TextView heading = text(UiStyle.marked(name, selected), 17, true);
        heading.setTextColor(selected ? UiStyle.colors(this).onPrimaryContainer
                : UiStyle.colors(this).text);
        headline.addView(heading);
        card.addView(headline);
        if (description != null && !description.isEmpty()) {
            TextView detail = text(description, 14, false);
            if (selected) detail.setTextColor(UiStyle.colors(this).onPrimaryContainer);
            UiStyle.addSpaced(card, detail, 5, 0);
        }
        card.setOnClickListener(view -> action.run());
        UiStyle.addSpaced(root, card, 3, 7);
    }

    /** One cell of the two-column colour grid: swatch + name, then the short description. */
    private void themeOption(LinearLayout row, ColorOption option, boolean selected,
            Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(android.view.Gravity.CENTER);
        card.setPadding(dp(10), dp(10), dp(10), dp(10));
        card.setMinimumHeight(dp(82));
        UiStyle.choice(card, selected, UiStyle.RADIUS_PANEL);
        UiStyle.pressable(card);

        LinearLayout headline = new LinearLayout(this);
        headline.setOrientation(LinearLayout.HORIZONTAL);
        headline.setGravity(android.view.Gravity.CENTER);
        int swatch = swatch(option.name);
        if (swatch != 0) {
            View color = new View(this);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(swatch);
            color.setBackground(circle);
            LinearLayout.LayoutParams dot = new LinearLayout.LayoutParams(dp(18), dp(18));
            dot.setMargins(0, 0, dp(8), 0);
            headline.addView(color, dot);
        }
        // The name is centred in what is left of the cell and ellipsizes when it still overflows,
        // so neither the swatch nor the marker is ever pushed out of the narrow cell.
        TextView heading = text(option.name, 15, true);
        heading.setGravity(android.view.Gravity.CENTER);
        heading.setMaxLines(1);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setTextColor(selected ? UiStyle.colors(this).onPrimaryContainer
                : UiStyle.colors(this).text);
        headline.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));
        // Always present so the centred group does not shift when the marker appears.
        TextView marker = text("✓", 15, true);
        marker.setTextColor(UiStyle.colors(this).onPrimaryContainer);
        marker.setVisibility(selected ? View.VISIBLE : View.INVISIBLE);
        headline.addView(marker);
        card.addView(headline, new LinearLayout.LayoutParams(-1, -2));
        if (option.description != null && !option.description.isEmpty()) {
            TextView detail = text(option.description, 12, false);
            detail.setGravity(android.view.Gravity.CENTER);
            detail.setMaxLines(2);
            if (selected) detail.setTextColor(UiStyle.colors(this).onPrimaryContainer);
            UiStyle.addSpaced(card, detail, 4, 0);
        }
        card.setOnClickListener(view -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        row.addView(card, params);
    }

    private TextView text(String value, int size, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        if (bold) UiStyle.title(view); else UiStyle.muted(view);
        return view;
    }

    private int swatch(String name) {
        if ("青绿".equals(name)) return 0xFF006B60;
        if ("晴蓝".equals(name)) return 0xFF315CA7;
        if ("暖珊瑚".equals(name)) return 0xFF9B4B32;
        if ("紫罗兰".equals(name)) return ThemeStore.paletteSeed(this, ThemeStore.PURPLE);
        if ("琥珀".equals(name)) return ThemeStore.paletteSeed(this, ThemeStore.AMBER);
        if ("玫瑰".equals(name)) return ThemeStore.paletteSeed(this, ThemeStore.ROSE);
        if ("森林绿".equals(name)) return ThemeStore.paletteSeed(this, ThemeStore.FOREST);
        if ("自定义颜色".equals(name)) return ThemeStore.customColor(this);
        if ("壁纸配色".equals(name) && ThemeStore.wallpaperAvailable())
            return getColor(android.R.color.system_accent1_700);
        return 0;
    }

    private void showCustomColorDialog() {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(22), dp(20), dp(22), dp(20));
        UiStyle.glass(panel);
        TextView title = text("自定义主题色", 20, true);
        panel.addView(title);
        TextView hint = text("拖动 HSV 滑杆或输入 #RRGGBB，确认后应用。", 13, false);
        UiStyle.addSpaced(panel, hint, 4, 12);

        int[] chosen = {ThemeStore.customColor(this)};
        float[] hsv = new float[3];
        Color.colorToHSV(chosen[0], hsv);
        boolean[] syncing = {false};
        View preview = new View(this);
        panel.addView(preview, new LinearLayout.LayoutParams(-1, dp(52)));
        EditText hex = new EditText(this);
        hex.setSingleLine(true);
        hex.setHint("#RRGGBB");
        hex.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        UiStyle.input(hex);
        UiStyle.addSpaced(panel, hex, 10, 6);

        TextView hueLabel = text("色相 · " + Math.round(hsv[0]) + "°", 14, true);
        UiStyle.addSpaced(panel, hueLabel, 4, 0);
        SeekBar hue = new SeekBar(this);
        tintSeekBar(hue);
        hue.setMax(359);
        panel.addView(hue, new LinearLayout.LayoutParams(-1, dp(40)));
        TextView saturationLabel = text("饱和度 · " + Math.round(hsv[1] * 100) + "%", 14, true);
        UiStyle.addSpaced(panel, saturationLabel, 4, 0);
        SeekBar saturation = new SeekBar(this);
        tintSeekBar(saturation);
        saturation.setMax(100);
        panel.addView(saturation, new LinearLayout.LayoutParams(-1, dp(40)));
        TextView valueLabel = text("明度 · " + Math.round(hsv[2] * 100) + "%", 14, true);
        UiStyle.addSpaced(panel, valueLabel, 4, 0);
        SeekBar value = new SeekBar(this);
        tintSeekBar(value);
        value.setMax(100);
        panel.addView(value, new LinearLayout.LayoutParams(-1, dp(40)));

        LinearLayout actions = new LinearLayout(this);
        Button cancel = new Button(this);
        cancel.setText("取消");
        UiStyle.button(cancel, false);
        cancel.setOnClickListener(view -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(50), 1));
        Button apply = new Button(this);
        apply.setText("应用颜色");
        UiStyle.button(apply, true);
        LinearLayout.LayoutParams applyParams = new LinearLayout.LayoutParams(0, dp(50), 1);
        applyParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(apply, applyParams);
        UiStyle.addSpaced(panel, actions, 12, 0);

        Runnable paintPreview = () -> {
            GradientDrawable swatch = new GradientDrawable();
            swatch.setColor(chosen[0]);
            swatch.setCornerRadius(dp(16));
            swatch.setStroke(dp(1), UiStyle.colors(this).outline);
            preview.setBackground(swatch);
        };
        Runnable updateHex = () -> {
            syncing[0] = true;
            hex.setText(String.format(Locale.US, "#%06X", chosen[0] & 0xFFFFFF));
            hex.setSelection(hex.length());
            syncing[0] = false;
        };
        Runnable updateFromHsv = () -> {
            chosen[0] = Color.HSVToColor(hsv);
            paintPreview.run();
            updateHex.run();
        };
        Runnable syncSliders = () -> {
            syncing[0] = true;
            hue.setProgress(Math.round(hsv[0]));
            saturation.setProgress(Math.round(hsv[1] * 100));
            value.setProgress(Math.round(hsv[2] * 100));
            syncing[0] = false;
        };
        hue.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                hueLabel.setText("色相 · " + progress + "°");
                if (!syncing[0]) { hsv[0] = progress; updateFromHsv.run(); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        saturation.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                saturationLabel.setText("饱和度 · " + progress + "%");
                if (!syncing[0]) { hsv[1] = progress / 100f; updateFromHsv.run(); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        value.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                valueLabel.setText("明度 · " + progress + "%");
                if (!syncing[0]) { hsv[2] = progress / 100f; updateFromHsv.run(); }
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
        hex.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (syncing[0] || !ThemeStore.isHexColor(s.toString().trim())) {
                    apply.setEnabled(syncing[0] || ThemeStore.isHexColor(s.toString().trim()));
                    return;
                }
                chosen[0] = Color.parseColor(s.charAt(0) == '#' ? s.toString().trim()
                        : "#" + s.toString().trim());
                Color.colorToHSV(chosen[0], hsv);
                syncSliders.run();
                paintPreview.run();
                apply.setEnabled(true);
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        apply.setOnClickListener(view -> {
            if (!ThemeStore.isHexColor(hex.getText().toString().trim())) return;
            dialog.dismiss();
            ThemeStore.setCustomColor(this, chosen[0]);
        });
        paintPreview.run();
        updateHex.run();
        syncSliders.run();
        UiStyle.showFloatingDialog(dialog, panel);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
