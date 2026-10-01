package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CheckBox;
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
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        UiStyle.back(this, root);
        text(root, "版本更新", 28, true);
        text(root, "当前版本 " + ReleaseUpdates.installedVersion(this), 15, false);
        CheckBox automatic = new CheckBox(this);
        automatic.setText("每天自动检查新版本");
        automatic.setChecked(ReleaseUpdates.preferences(this).getBoolean("automatic", true));
        automatic.setTextColor(UiStyle.colors(this).text);
        automatic.setButtonTintList(android.content.res.ColorStateList.valueOf(UiStyle.colors(this).primary));
        automatic.setOnCheckedChangeListener((button, checked) -> ReleaseUpdates.preferences(this)
                .edit().putBoolean("automatic", checked).apply());
        UiStyle.addSpaced(root, automatic, 12, 4);
        text(root, "有新版本时提示一次；下载后由系统安装，保留现有数据。", 14, false);
        check = new Button(this);
        check.setText("检查更新");
        UiStyle.button(check, true);
        check.setOnClickListener(view -> check());
        UiStyle.addSpaced(root, check, 12, 12);
        result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setPadding(dp(18), dp(16), dp(18), dp(16));
        UiStyle.glass(result);
        root.addView(result);
        Button releases = new Button(this);
        releases.setText("打开 GitHub Releases");
        UiStyle.button(releases, false);
        releases.setOnClickListener(view -> open(GitHubRelease.RELEASES_URL));
        UiStyle.addSpaced(root, releases, 12, 0);
        page.addView(root);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.applyInsets(stage, page);
        setContentView(stage);
        check();
    }

    private void check() {
        check.setEnabled(false);
        check.setText("正在检查…");
        result.removeAllViews();
        text(result, "正在连接 GitHub…", 15, false);
        reader.execute(() -> {
            try {
                GitHubRelease release = GitHubRelease.fetch();
                boolean newer = GitHubRelease.newer(release.version, ReleaseUpdates.installedVersion(this));
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    result.removeAllViews();
                    text(result, newer ? "有新版本 " + release.version : "当前已是最新版本", 20, true);
                    text(result, "GitHub 最新正式版：" + release.version, 14, false);
                    text(result, release.notes.trim().isEmpty() ? "此版本没有更新说明" : release.notes, 15, false);
                    if (newer) {
                        if (release.apkUrl.isEmpty()) text(result, "此版本尚未提供 APK，请查看发布页。", 15, false);
                        Button download = new Button(this);
                        download.setText(release.apkUrl.isEmpty() ? "查看发布页" : "下载 APK");
                        UiStyle.button(download, true);
                        download.setOnClickListener(view -> open(release.apkUrl.isEmpty()
                                ? release.pageUrl : release.apkUrl));
                        UiStyle.addSpaced(result, download, 12, 0);
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
