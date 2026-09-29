package com.donglan.chrona;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.SslErrorHandler;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import java.io.OutputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The timetable website keeps its own accounts and data, separate from Chrona's calendar. */
public final class ScheduleCompareActivity extends Activity {
    static final String SITE = "https://111.228.3.50/tongge/";
    private static final int PICK_FILE = 40;
    private static final int SAVE_SCREENSHOT = 41;
    private static final int MAX_SCREENSHOT_BYTES = 16 * 1024 * 1024;
    private static final ExecutorService SAVES = Executors.newSingleThreadExecutor();
    private WebView web;
    private ProgressBar progress;
    private TextView status;
    private ValueCallback<Uri[]> fileCallback;
    private byte[] screenshot;
    private android.window.OnBackInvokedCallback backCallback;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        if (getLastNonConfigurationInstance() instanceof byte[] retained) screenshot = retained;
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(12), dp(12), dp(12), dp(8));
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        LinearLayout header = new LinearLayout(this);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(icon(R.drawable.ic_chevron_left, "返回", this::navigateBack),
                new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView title = new TextView(this);
        title.setText("课表对比");
        title.setTextSize(22);
        title.setIncludeFontPadding(false);
        title.setPadding(dp(12), 0, dp(8), 0);
        UiStyle.title(title);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        UiStyle.acrylicChoice(actions, false, UiStyle.RADIUS_PILL, false);
        round(actions, UiStyle.RADIUS_PILL);
        ImageButton refresh = icon(R.drawable.ic_refresh, "刷新网页", () -> {
            if (web != null) web.reload();
        });
        refresh.setBackgroundColor(Color.TRANSPARENT);
        actions.addView(refresh, new LinearLayout.LayoutParams(dp(44), -1));
        View divider = new View(this);
        divider.setBackgroundColor(UiStyle.colors(this).outline);
        actions.addView(divider, new LinearLayout.LayoutParams(dp(1), dp(16)));
        TextView browser = new TextView(this);
        browser.setText("浏览器");
        browser.setTextSize(14);
        browser.setTextColor(UiStyle.colors(this).primary);
        browser.setTypeface(null, android.graphics.Typeface.BOLD);
        browser.setIncludeFontPadding(false);
        browser.setGravity(Gravity.CENTER);
        browser.setPadding(dp(12), 0, dp(12), 0);
        browser.setContentDescription("在浏览器中打开课表对比");
        UiStyle.pressable(browser);
        browser.setOnClickListener(view -> openBrowser(Uri.parse(web != null
                && isSite(web.getUrl()) ? web.getUrl() : SITE)));
        actions.addView(browser, new LinearLayout.LayoutParams(-2, -1));
        header.addView(actions, new LinearLayout.LayoutParams(-2, dp(44)));
        root.addView(header);
        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setProgressTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        UiStyle.addSpaced(root, progress, 8, 4);
        status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(dp(8), dp(6), dp(8), dp(8));
        UiStyle.muted(status);
        status.setVisibility(View.GONE);
        root.addView(status);
        try {
            web = new WebView(this);
            configureWebView();
            FrameLayout website = new FrameLayout(this);
            round(website, UiStyle.RADIUS_CARD);
            website.addView(web, new FrameLayout.LayoutParams(-1, -1));
            LinearLayout.LayoutParams viewport = new LinearLayout.LayoutParams(-1, 0, 1);
            viewport.topMargin = dp(8);
            root.addView(website, viewport);
        } catch (RuntimeException exception) {
            if (web != null) { web.destroy(); web = null; }
            showError("系统 WebView 不可用，请更新 Android System WebView，或点击浏览器打开。");
        }
        stage.addView(root, new FrameLayout.LayoutParams(-1, -1));
        UiStyle.applyInsets(stage, root);
        setContentView(stage);
        if (Build.VERSION.SDK_INT >= 33) {
            backCallback = this::navigateBack;
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, backCallback);
        }
        if (web != null && (state == null || web.restoreState(state) == null)) web.loadUrl(SITE);
    }

    private ImageButton icon(int resource, String description, Runnable action) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(resource);
        button.setImageTintList(ColorStateList.valueOf(UiStyle.colors(this).primary));
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setPadding(dp(11), dp(11), dp(11), dp(11));
        button.setContentDescription(description);
        UiStyle.acrylicChoice(button, false, UiStyle.RADIUS_PILL, false);
        UiStyle.pressable(button);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    /** Clip the actual child rendering, including WebView's opaque page background. */
    private void round(View view, int radius) {
        view.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override public void getOutline(View target, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, target.getWidth(), target.getHeight(), dp(radius));
            }
        });
        view.setClipToOutline(true);
    }

    private void configureWebView() {
        web.setBackgroundColor(UiStyle.colors(this).background);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(true); // Only files explicitly selected by the user.
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, false);
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (isSite(request.getUrl().toString())) return false;
                if (request.isForMainFrame()) openBrowser(request.getUrl());
                return true;
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                status.setVisibility(View.GONE);
                progress.setVisibility(View.VISIBLE);
            }
            @Override public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
                CookieManager.getInstance().flush();
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request,
                    WebResourceError error) {
                if (request.isForMainFrame()) showError("网页加载失败，请检查网络后刷新，或点击浏览器打开。");
            }
            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request,
                    WebResourceResponse response) {
                if (request.isForMainFrame()) showError("网站暂时无法访问（HTTP "
                        + response.getStatusCode() + "），请稍后刷新。");
            }
            @Override public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel();
                showError("网站证书验证失败，请检查系统时间或网站证书。可点击浏览器检查。");
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onProgressChanged(WebView view, int value) { progress.setProgress(value); }
            @Override public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                    FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                // Extensions such as .ics are not MIME types; a wildcard avoids hiding real exports.
                Intent pick = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE).setType("*/*")
                        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE,
                                params.getMode() == FileChooserParams.MODE_OPEN_MULTIPLE);
                try { startActivityForResult(pick, PICK_FILE); }
                catch (ActivityNotFoundException exception) {
                    fileCallback.onReceiveValue(null);
                    fileCallback = null;
                    Feedback.show(ScheduleCompareActivity.this, "未找到文件选择器");
                }
                return true;
            }
        });
        web.setDownloadListener((url, agent, disposition, mime, length) -> {
            if (url.startsWith("data:image/png;base64,")) saveScreenshot(url);
            else if (url.startsWith("https://") || url.startsWith("http://")) openBrowser(Uri.parse(url));
            else Feedback.show(this, "此下载格式请在浏览器中保存");
        });
    }

    static boolean isSite(String url) {
        if (url == null) return false;
        try {
            java.net.URI uri = java.net.URI.create(url).normalize();
            return "https".equalsIgnoreCase(uri.getScheme()) && "111.228.3.50".equals(uri.getHost())
                    && uri.getRawUserInfo() == null && (uri.getPort() == -1 || uri.getPort() == 443)
                    && ("/tongge".equals(uri.getPath()) || uri.getPath() != null
                            && uri.getPath().startsWith("/tongge/"));
        } catch (IllegalArgumentException exception) { return false; }
    }

    private void saveScreenshot(String url) {
        if (screenshot != null) { Feedback.show(this, "请先完成当前截图保存"); return; }
        String encoded = url.substring("data:image/png;base64,".length());
        if (encoded.length() > (MAX_SCREENSHOT_BYTES / 3L + 1) * 4) {
            Feedback.show(this, "截图过大，请在浏览器中保存"); return;
        }
        try {
            screenshot = android.util.Base64.decode(encoded, android.util.Base64.DEFAULT);
            Intent save = new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/png").putExtra(Intent.EXTRA_TITLE, "课表对比.png");
            startActivityForResult(save, SAVE_SCREENSHOT);
        } catch (IllegalArgumentException | ActivityNotFoundException exception) {
            screenshot = null;
            Feedback.show(this, "无法保存截图，请在浏览器中重试");
        }
    }

    @Override protected void onActivityResult(int request, int result, Intent intent) {
        super.onActivityResult(request, result, intent);
        if (request == PICK_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(result, intent));
            fileCallback = null;
        } else if (request == SAVE_SCREENSHOT) {
            byte[] bytes = screenshot;
            screenshot = null;
            if (result != RESULT_OK || intent == null || intent.getData() == null || bytes == null) return;
            Uri destination = intent.getData();
            android.content.ContentResolver resolver = getContentResolver();
            SAVES.execute(() -> {
                String message;
                try (OutputStream output = resolver.openOutputStream(destination, "wt")) {
                    if (output == null) throw new java.io.IOException("Missing destination");
                    output.write(bytes);
                    message = "课表截图已保存";
                } catch (Exception exception) { message = "截图保存失败，请检查保存位置后重试"; }
                String feedback = message;
                runOnUiThread(() -> { if (!isDestroyed()) Feedback.show(this, feedback); });
            });
        }
    }

    private void openBrowser(Uri uri) {
        String scheme = uri.getScheme();
        if (!"https".equalsIgnoreCase(scheme) && !"http".equalsIgnoreCase(scheme)) {
            Feedback.show(this, "无法打开此链接类型"); return;
        }
        try { startActivity(new Intent(Intent.ACTION_VIEW, uri).addCategory(Intent.CATEGORY_BROWSABLE)); }
        catch (ActivityNotFoundException exception) { Feedback.show(this, "未找到浏览器"); }
    }

    private void showError(String message) {
        status.setText(message);
        status.setVisibility(View.VISIBLE);
        progress.setVisibility(View.GONE);
    }

    // API 33+ uses the registered OnBackInvokedCallback; onBackPressed is only the legacy path.
    @android.annotation.SuppressLint("GestureBackNavigation")
    private void navigateBack() {
        if (web != null && web.canGoBack()) web.goBack();
        else finish();
    }
    @android.annotation.SuppressLint("GestureBackNavigation")
    @Override public void onBackPressed() { navigateBack(); }
    @Override protected void onSaveInstanceState(Bundle state) {
        if (web != null) web.saveState(state);
        super.onSaveInstanceState(state);
    }
    @Override public Object onRetainNonConfigurationInstance() { return screenshot; }
    @Override protected void onPause() {
        if (web != null) { web.onPause(); CookieManager.getInstance().flush(); }
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
    }
    @Override protected void onDestroy() {
        if (Build.VERSION.SDK_INT >= 33 && backCallback != null)
            getOnBackInvokedDispatcher().unregisterOnBackInvokedCallback(backCallback);
        if (fileCallback != null) { fileCallback.onReceiveValue(null); fileCallback = null; }
        if (web != null) {
            web.stopLoading();
            if (web.getParent() instanceof android.view.ViewGroup parent) parent.removeView(web);
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
