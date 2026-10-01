package com.donglan.chrona;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The same guide is available on first launch and from Settings. */
public final class GuideActivity extends Activity {
    static boolean showIfNeeded(Activity activity) {
        if (activity.getSharedPreferences("usage_guide", Context.MODE_PRIVATE)
                .getBoolean("shown", false)) return false;
        activity.startActivity(new Intent(activity, GuideActivity.class));
        return true;
    }

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        getSharedPreferences("usage_guide", MODE_PRIVATE).edit().putBoolean("shown", true).apply();
        ScrollView page = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        UiStyle.back(this, root);
        TextView title = new TextView(this);
        title.setText("把事情交给拾时");
        title.setTextSize(28);
        UiStyle.title(title);
        UiStyle.addSpaced(root, title, 0, 16);
        step(root, "1 · 配好 AI 服务", "在「设置 → AI 服务」填写地址、模型和密钥并保存。图片需要支持视觉的模型；密钥只在本机加密保存。",
                "打开 AI 服务", () -> startActivity(new Intent(this, SettingsActivity.class)));
        step(root, "2 · 记录一件事", "点主界面的 +，输入文字、粘贴剪贴板或添加截图，再点右上角 ✓。也可以从其他应用分享文字、图片或文件给拾时。普通文件只提供名称等信息，内容不会交给 AI。",
                null, null);
        step(root, "3 · 在收件箱审核", "提交后会在后台解析。到「收件箱」查看进度和结果；一条记录可以生成多个日程，详情里左右滑动即可逐项检查。解析失败时，检查网络和 AI 配置后重试。",
                null, null);
        step(root, "4 · 确认后写入日历", "核对标题、日期、时间和提醒，补全不确定的信息，再点 ✓。每个日程都要单独确认；写入后由系统日历提醒。请允许日历权限，并开启系统日历的通知和提醒。拾时的通知用于解析结果。",
                null, null);
        step(root, "5 · 浏览、备份与更新", "「首页」查看近期安排，「日程」搜索和筛选。点日程会直接打开对应一项。设置里可调整外观、导出完整备份和检查新版本；桌面还可以添加拾时小组件。",
                null, null);
        Button done = new Button(this);
        done.setText("开始使用");
        UiStyle.button(done, true);
        done.setOnClickListener(view -> finish());
        UiStyle.addSpaced(root, done, 12, 8);
        page.addView(root);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.applyInsets(stage, page);
        setContentView(stage);
        if (state != null) page.post(() -> page.scrollTo(0, state.getInt("scroll_y")));
        guidePage = page;
    }

    private ScrollView guidePage;

    @Override protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putInt("scroll_y", guidePage.getScrollY());
    }

    private void step(LinearLayout root, String heading, String description,
            String action, Runnable onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        UiStyle.glass(card);
        TextView title = new TextView(this);
        title.setText(heading);
        title.setTextSize(18);
        UiStyle.title(title);
        card.addView(title);
        TextView body = new TextView(this);
        body.setText(description);
        body.setTextSize(15);
        body.setLineSpacing(dp(3), 1f);
        UiStyle.muted(body);
        UiStyle.addSpaced(card, body, 8, 0);
        if (action != null) {
            Button button = new Button(this);
            button.setText(action);
            UiStyle.button(button, false);
            button.setOnClickListener(view -> onClick.run());
            UiStyle.addSpaced(card, button, 12, 0);
        }
        UiStyle.addSpaced(root, card, 0, 10);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
