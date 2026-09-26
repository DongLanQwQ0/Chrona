package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.donglan.chrona.debug.DebugActivity;

/** Secondary destination for appearance, AI service, and diagnostics. */
public final class SettingsHubActivity extends Activity {
    private ScrollView page;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        page = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        page.addView(root);
        UiStyle.back(this, root);

        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(28);
        UiStyle.title(title);
        UiStyle.addSpaced(root, title, 0, 8);
        TextView help = new TextView(this);
        help.setText("调整外观和解析服务。所有设置仅保存在这台设备上。");
        UiStyle.muted(help);
        UiStyle.addSpaced(root, help, 0, 20);

        section(root, "个性化", "外观与配色", "跟随系统、浅色、深色与主题配色",
                AppearanceActivity.class);
        section(root, "解析", "AI 服务", "地址、模型、密钥和图片支持",
                SettingsActivity.class);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            section(root, "帮助", "诊断信息", "查看本机运行状态与日志", DebugActivity.class);
        }
        TextView version = new TextView(this);
        version.setText("拾时 · Chrona  " + versionName());
        UiStyle.muted(version);
        UiStyle.addSpaced(root, version, 24, 0);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        root.setFitsSystemWindows(false);
        UiStyle.applyInsets(stage, page);
        setContentView(stage);
        if (state != null) {
            int scrollY = state.getInt("scroll_y");
            page.post(() -> page.scrollTo(0, scrollY));
        }
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("scroll_y", page.getScrollY());
    }

    private void section(LinearLayout root, String eyebrow, String title, String subtitle,
            Class<? extends Activity> target) {
        TextView label = new TextView(this);
        label.setText(eyebrow);
        label.setTextSize(13);
        UiStyle.muted(label);
        UiStyle.addSpaced(root, label, 8, 8);
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        UiStyle.glass(card);
        UiStyle.pressable(card);
        TextView heading = new TextView(this);
        heading.setText(title + "   →");
        heading.setTextSize(18);
        UiStyle.title(heading);
        card.addView(heading);
        TextView description = new TextView(this);
        description.setText(subtitle);
        UiStyle.muted(description);
        UiStyle.addSpaced(card, description, 7, 0);
        card.setOnClickListener(view -> startActivity(new Intent(this, target)));
        UiStyle.addSpaced(root, card, 0, 8);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private String versionName() {
        try {
            return getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (android.content.pm.PackageManager.NameNotFoundException ignored) {
            return "";
        }
    }
}
