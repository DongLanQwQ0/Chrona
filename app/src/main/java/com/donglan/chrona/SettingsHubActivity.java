package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
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
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        UiStyle.back(this, header);
        View back = header.getChildAt(0);
        LinearLayout.LayoutParams backParams = (LinearLayout.LayoutParams) back.getLayoutParams();
        backParams.bottomMargin = 0;
        back.setLayoutParams(backParams);
        TextView title = new TextView(this);
        title.setText("设置");
        title.setTextSize(28);
        UiStyle.title(title);
        title.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1f));
        UiStyle.addSpaced(root, header, 0, 20);

        section(root, "外观与配色", "跟随系统、浅色、深色与主题配色",
                AppearanceActivity.class);
        timelineLimitSection(root);
        widgetPreviewSection(root);
        section(root, "备份与恢复", "导出或恢复收件箱、附件、模型输出和设置",
                BackupRestoreActivity.class);
        section(root, "AI 服务", "地址、模型、密钥和图片支持",
                SettingsActivity.class);
        section(root, "使用引导", "从记录、审核到系统日历提醒", GuideActivity.class);
        section(root, "检查更新", "GitHub Release · 版本说明与 APK 下载", UpdateActivity.class);
        if ((getApplicationInfo().flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            section(root, "诊断信息", "查看本机运行状态与日志", DebugActivity.class);
        }
        TextView version = new TextView(this);
        version.setText("拾时 · Chrona  " + appVersionName());
        version.setGravity(Gravity.CENTER);
        UiStyle.muted(version);
        UiStyle.addSpaced(root, version, 24, 16);
        TextView github = new TextView(this);
        github.setText("GitHub主页 https://github.com/DongLanQwQ0");
        github.setGravity(Gravity.CENTER);
        github.setTextColor(UiStyle.colors(this).primary);
        github.setOnClickListener(view -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        android.net.Uri.parse("https://github.com/DongLanQwQ0")));
            } catch (android.content.ActivityNotFoundException exception) {
                android.widget.Toast.makeText(this, "没有可用的浏览器", android.widget.Toast.LENGTH_SHORT).show();
            }
        });
        UiStyle.addSpaced(root, github, 0, 8);
        TextView qq = new TextView(this);
        qq.setText("QQ:2590339284(可以长按复制)");
        qq.setGravity(Gravity.CENTER);
        UiStyle.muted(qq);
        qq.setOnLongClickListener(view -> {
            android.content.ClipboardManager clipboard = getSystemService(android.content.ClipboardManager.class);
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("QQ", "2590339284"));
            android.widget.Toast.makeText(this, "QQ号已复制", android.widget.Toast.LENGTH_SHORT).show();
            return true;
        });
        UiStyle.addSpaced(root, qq, 0, 8);
        TextView welcome = new TextView(this);
        welcome.setText("欢迎来找我喵~QwQ");
        welcome.setGravity(Gravity.CENTER);
        UiStyle.muted(welcome);
        UiStyle.addSpaced(root, welcome, 0, 16);
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

    private void section(LinearLayout root, String title, String subtitle,
            Class<? extends Activity> target) {
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

    private String appVersionName() {
        try {
            PackageManager packageManager = getPackageManager();
            if (Build.VERSION.SDK_INT >= 33) {
                return packageManager.getPackageInfo(getPackageName(),
                        PackageManager.PackageInfoFlags.of(0)).versionName;
            }
            return packageManager.getPackageInfo(getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException ignored) {
            return "版本信息不可用";
        }
    }

    private void timelineLimitSection(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        UiStyle.glass(card);
        UiStyle.pressable(card);
        TextView heading = new TextView(this);
        heading.setText("首页时间线数量   →");
        heading.setTextSize(18);
        UiStyle.title(heading);
        card.addView(heading);
        TextView description = new TextView(this);
        UiStyle.muted(description);
        card.addView(description);
        Runnable updateLabel = () -> description.setText("每次最多显示 "
                + HomeTimelinePreferences.getItemLimit(this) + " 项（5–50）");
        updateLabel.run();
        card.setOnClickListener(view -> {
            String[] options = new String[10];
            for (int i = 0; i < options.length; i++) options[i] = (5 + i * 5) + " 项";
            int selected = (HomeTimelinePreferences.getItemLimit(this) - 5) / 5;
            UiStyle.choiceDialog(this, "首页时间线数量", options, selected, choice -> {
                HomeTimelinePreferences.setItemLimit(this, 5 + choice * 5);
                updateLabel.run();
            });
        });
        UiStyle.addSpaced(root, card, 0, 8);
    }

    private void widgetPreviewSection(LinearLayout root) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        UiStyle.glass(card);
        UiStyle.pressable(card);
        TextView heading = new TextView(this);
        heading.setText("明日安排预览时间   →");
        heading.setTextSize(18);
        UiStyle.title(heading);
        card.addView(heading);
        TextView description = new TextView(this);
        UiStyle.muted(description);
        UiStyle.addSpaced(card, description, 7, 0);
        Runnable updateLabel = () -> {
            int minutes = WidgetPreferences.previewMinutes(this);
            description.setText(String.format(java.util.Locale.ROOT,
                    "每天 %02d:%02d 起，小组件显示「现在与明天」", minutes / 60, minutes % 60));
        };
        updateLabel.run();
        card.setOnClickListener(view -> {
            int minutes = WidgetPreferences.previewMinutes(this);
            new android.app.TimePickerDialog(this, (picker, hour, minute) -> {
                WidgetPreferences.setPreviewMinutes(this, hour * 60 + minute);
                updateLabel.run();
            }, minutes / 60, minutes % 60, true).show();
        });
        UiStyle.addSpaced(root, card, 0, 8);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

}
