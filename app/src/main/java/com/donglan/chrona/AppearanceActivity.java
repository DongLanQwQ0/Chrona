package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.SeekBar;
import android.widget.Switch;

/** Selects a durable mode and palette, with immediate visual feedback. */
public final class AppearanceActivity extends Activity {
    private static final int PICK_BACKGROUND = 23;
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
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
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
        if (ThemeStore.wallpaperAvailable()) {
            option(root, "壁纸配色", "跟随 Android 系统配色",
                    ThemeStore.WALLPAPER.equals(ThemeStore.color(this)),
                    () -> selectColor(ThemeStore.WALLPAPER));
        }
        option(root, "青绿", "拾时经典", ThemeStore.TEAL.equals(ThemeStore.color(this)),
                () -> selectColor(ThemeStore.TEAL));
        option(root, "晴蓝", "明朗沉静", ThemeStore.BLUE.equals(ThemeStore.color(this)),
                () -> selectColor(ThemeStore.BLUE));
        option(root, "暖珊瑚", "柔和温暖", ThemeStore.CORAL.equals(ThemeStore.color(this)),
                () -> selectColor(ThemeStore.CORAL));
        heading(root, "毛玻璃与层级");
        acrylicControls(root);
        heading(root, "背景图片");
        option(root, "选择本地图片", "为页面设置自己的背景，卡片仍保持清晰可读",
                ThemeStore.background(this) != null, this::pickBackground);
        if (ThemeStore.background(this) != null) {
            option(root, "恢复默认背景", "移除自定义图片", false, () -> {
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
        TextView title = text("一级面板背景模糊", 16, true);
        TextView description = text("卡片模糊的是静态壁纸；弹窗会实时模糊底层页面，文字保持清晰（Android 12 及以上）", 13, false);
        labels.addView(title);
        UiStyle.addSpaced(labels, description, 3, 0);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch toggle = new Switch(this);
        toggle.setChecked(ThemeStore.acrylicEnabled(this));
        row.addView(toggle);
        card.addView(row);
        toggle.setOnCheckedChangeListener((button, checked) ->
                ThemeStore.setAcrylicEnabled(this, checked));

        TextView blurLabel = text("模糊方式", 15, true);
        UiStyle.addSpaced(card, blurLabel, 14, 4);
        TextView blurHint = text("普通模糊更轻；高斯模糊更柔和", 12, false);
        UiStyle.addSpaced(card, blurHint, 0, 4);
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

        TextView strengthLabel = text("模糊强度 · " + ThemeStore.blurStrength(this) + " / 5",
                15, true);
        UiStyle.addSpaced(card, strengthLabel, 12, 0);
        SeekBar strengthSlider = new SeekBar(this);
        strengthSlider.setMax(4);
        strengthSlider.setProgress(ThemeStore.blurStrength(this) - 1);
        card.addView(strengthSlider, new LinearLayout.LayoutParams(-1, dp(44)));
        strengthSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                int value = progress + 1;
                strengthLabel.setText("模糊强度 · " + value + " / 5");
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                ThemeStore.setBlurStrength(AppearanceActivity.this,
                        seekBar.getProgress() + 1);
            }
        });

        TextView mixLabel = text("面板纯色浓度 · " + ThemeStore.surfaceMix(this) + "%",
                15, true);
        UiStyle.addSpaced(card, mixLabel, 10, 0);
        SeekBar mixSlider = new SeekBar(this);
        mixSlider.setMax(60);
        mixSlider.setProgress(ThemeStore.surfaceMix(this) - 10);
        card.addView(mixSlider, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout mixRange = new LinearLayout(this);
        mixRange.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView clearer = text("更透", 12, false);
        TextView moreSolid = text("更实", 12, false);
        mixRange.addView(clearer, new LinearLayout.LayoutParams(0, -2, 1));
        moreSolid.setGravity(android.view.Gravity.END);
        mixRange.addView(moreSolid, new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(mixRange);
        mixSlider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                int value = progress + 10;
                mixLabel.setText("面板纯色浓度 · " + value + "%");
                if (fromUser) ThemeStore.previewSurfaceMix(AppearanceActivity.this, value);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { }
        });

        TextView transparency = text("二级控件透明度 · "
                + ThemeStore.childTransparency(this) + "%", 15, true);
        UiStyle.addSpaced(card, transparency, 15, 0);
        SeekBar slider = new SeekBar(this);
        slider.setMax(55);
        slider.setProgress(ThemeStore.childTransparency(this) - 10);
        card.addView(slider, new LinearLayout.LayoutParams(-1, dp(44)));
        LinearLayout transparencyRange = new LinearLayout(this);
        transparencyRange.setGravity(android.view.Gravity.CENTER_VERTICAL);
        TextView moreOpaque = text("更实", 12, false);
        TextView moreTransparent = text("更透明", 12, false);
        transparencyRange.addView(moreOpaque,
                new LinearLayout.LayoutParams(0, -2, 1));
        moreTransparent.setGravity(android.view.Gravity.END);
        transparencyRange.addView(moreTransparent,
                new LinearLayout.LayoutParams(0, -2, 1));
        card.addView(transparencyRange, new LinearLayout.LayoutParams(-1, -2));
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress,
                    boolean fromUser) {
                transparency.setText("二级控件透明度 · " + (progress + 10) + "%");
                if (fromUser) ThemeStore.previewChildTransparency(AppearanceActivity.this,
                        progress + 10);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) {
                ThemeStore.setChildTransparency(AppearanceActivity.this, seekBar.getProgress() + 10);
            }
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
        TextView detail = text(description, 14, false);
        if (selected) detail.setTextColor(UiStyle.colors(this).onPrimaryContainer);
        UiStyle.addSpaced(card, detail, 5, 0);
        card.setOnClickListener(view -> action.run());
        UiStyle.addSpaced(root, card, 3, 7);
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
        if ("壁纸配色".equals(name) && ThemeStore.wallpaperAvailable())
            return getColor(android.R.color.system_accent1_700);
        return 0;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
