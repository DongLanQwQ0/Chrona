package com.donglan.chrona;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Selects a durable mode and palette, with immediate visual feedback. */
public final class AppearanceActivity extends Activity {
    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        ScrollView page = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(28));
        UiStyle.page(this, root);
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
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        root.setFitsSystemWindows(false);
        UiStyle.applyInsets(stage, page);
        setContentView(stage);
        UiStyle.enter(root, 0);
    }

    private void selectMode(String mode) {
        ThemeStore.setMode(this, mode);
        recreate();
    }
    private void selectColor(String color) {
        ThemeStore.setColor(this, color);
        recreate();
    }

    private void heading(LinearLayout root, String label) {
        UiStyle.addSpaced(root, text(label, 19, true), 16, 10);
    }

    private void option(LinearLayout root, String name, String description, boolean selected,
            Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(15), dp(18), dp(15));
        if (selected) UiStyle.pill(card, true); else UiStyle.glass(card);
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
        TextView heading = text((selected ? "✓  " : "     ") + name, 17, true);
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
