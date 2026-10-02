package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Switch;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Checks metadata in the background; APK download and installation use the system browser. */
public final class UpdateActivity extends Activity {
    private final ExecutorService reader = Executors.newSingleThreadExecutor();
    private LinearLayout result;
    private Button check;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        ScrollView page = new ScrollView(this);
        page.setVerticalScrollBarEnabled(false);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        UiStyle.back(this, root);
        text(root, "版本更新", 28, true);
        text(root, "当前版本 " + ReleaseUpdates.installedVersion(this), 15, false);
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        actions.setBaselineAligned(false);
        check = new Button(this);
        check.setText("检查更新");
        UiStyle.button(check, true);
        check.setOnClickListener(view -> check());
        actions.addView(check, new LinearLayout.LayoutParams(-2, -2));
        Button releases = new Button(this);
        releases.setText("发布记录");
        UiStyle.button(releases, false);
        releases.setOnClickListener(view -> open(GitHubRelease.RELEASES_URL));
        LinearLayout.LayoutParams releasesParams = new LinearLayout.LayoutParams(-2, -2);
        releasesParams.setMarginStart(dp(8));
        actions.addView(releases, releasesParams);
        UiStyle.addSpaced(root, actions, 4, 20);

        LinearLayout automaticRow = new LinearLayout(this);
        automaticRow.setGravity(Gravity.CENTER_VERTICAL);
        automaticRow.setPadding(dp(18), dp(8), dp(18), dp(8));
        UiStyle.glass(automaticRow);
        TextView automaticLabel = new TextView(this);
        automaticLabel.setText("每日自动检查");
        automaticLabel.setTextSize(16);
        UiStyle.title(automaticLabel);
        automaticRow.addView(automaticLabel, new LinearLayout.LayoutParams(0, -2, 1f));
        Switch automatic = new Switch(this);
        automatic.setContentDescription("每日自动检查");
        automatic.setChecked(ReleaseUpdates.preferences(this).getBoolean("automatic", true));
        UiStyle.toggle(automatic);
        automatic.setOnCheckedChangeListener((button, checked) -> ReleaseUpdates.preferences(this)
                .edit().putBoolean("automatic", checked).apply());
        automaticRow.addView(automatic);
        automaticRow.setOnClickListener(view -> automatic.setChecked(!automatic.isChecked()));
        UiStyle.addSpaced(root, automaticRow, 0, 16);
        result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(dp(18), dp(16), dp(18), dp(16));
        UiStyle.glass(result);
        root.addView(result);
        page.addView(root);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        // The viewport owns system insets; the content keeps its page gutters.
        root.setFitsSystemWindows(false);
        UiStyle.applyInsets(stage, page);
        setContentView(stage);
        check();
    }

    private void check() {
        check.setEnabled(false);
        check.setText("检查中…");
        result.removeAllViews();
        text(result, "正在连接 GitHub…", 15, false);
        reader.execute(() -> {
            try {
                GitHubRelease release = GitHubRelease.fetch();
                boolean newer = GitHubRelease.newer(release.version, ReleaseUpdates.installedVersion(this));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    result.removeAllViews();
                    text(result, newer ? "发现新版本" : "已是最新版本", 20, true);
                    text(result, "v" + release.version, 15, false);
                    if (newer) {
                        if (release.apkUrl.isEmpty()) text(result, "此版本尚未提供 APK，请查看发布页。", 15, false);
                        Button download = new Button(this);
                        download.setText(release.apkUrl.isEmpty() ? "查看发布页" : "下载更新");
                        UiStyle.button(download, true);
                        download.setOnClickListener(view -> open(release.apkUrl.isEmpty()
                                ? release.pageUrl : release.apkUrl));
                        LinearLayout.LayoutParams downloadParams = new LinearLayout.LayoutParams(-2, -2);
                        downloadParams.topMargin = dp(4);
                        downloadParams.bottomMargin = dp(16);
                        result.addView(download, downloadParams);
                    }
                    if (!release.notes.trim().isEmpty()) {
                        text(result, "更新内容", 16, true);
                        TextView notes = new TextView(this);
                        notes.setText(release.notes.trim());
                        notes.setTextSize(15);
                        notes.setTextColor(UiStyle.colors(this).text);
                        notes.setLineSpacing(dp(4), 1f);
                        notes.setTextIsSelectable(true);
                        result.addView(notes, new LinearLayout.LayoutParams(-1, -2));
                    }
                    finishCheck();
                });
            } catch (Exception exception) {
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    result.removeAllViews();
                    text(result, "未能完成检查", 20, true);
                    String detail = exception instanceof java.io.IOException
                            ? exception.getMessage() : "版本信息格式不正确，请查看 GitHub Releases";
                    text(result, detail == null ? "请检查网络后重试" : detail, 15, false);
                    finishCheck();
                });
            }
        });
    }

    private void finishCheck() {
        check.setEnabled(true);
        check.setText("重新检查");
    }

    private void open(String url) {
        try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
        catch (android.content.ActivityNotFoundException exception) {
            Feedback.show(this, "没有可用的浏览器");
        }
    }

    private void text(LinearLayout parent, String value, int size, boolean heading) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setLineSpacing(dp(3), 1f);
        if (heading) UiStyle.title(text); else UiStyle.muted(text);
        UiStyle.addSpaced(parent, text, 0, 10);
    }

    @Override protected void onDestroy() {
        reader.shutdownNow();
        super.onDestroy();
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
