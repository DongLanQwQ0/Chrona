package com.donglan.chrona;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.*;
import android.widget.*;

/** Same backdrop, grouped surfaces, inputs and safe area as the native settings. */
public final class LanSettingsActivity extends Activity {
    private Switch enabled;
    private TextView status, address, code;
    private ImageView qr;
    private boolean refreshing;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() { public void run() { refresh(); handler.postDelayed(this, 1000); } };
    @Override protected void onCreate(Bundle saved) {
        ThemeStore.apply(this); super.onCreate(saved);
        LinearLayout root = SettingsPageLayout.content(this);
        SettingsPageLayout.header(this, root, "局域网访问");
        LinearLayout session = group(root, "连接");
        enabled = new Switch(this); enabled.setText("允许电脑浏览器访问"); enabled.setTextSize(16);
        enabled.setTextColor(UiStyle.colors(this).text); enabled.setPadding(dp(16), dp(12), dp(16), dp(12)); UiStyle.toggle(enabled);
        session.addView(enabled, new LinearLayout.LayoutParams(-1, -2));
        enabled.setOnCheckedChangeListener((button, checked) -> { if (refreshing) return; if (!checked) LanAccessService.stop(this); else enable(); refresh(); });
        status = text("", 14); UiStyle.muted(status); add(session, status);
        LinearLayout access = group(root, "浏览器配对"); address = text("", 16); address.setTextIsSelectable(true); add(access, address);
        qr = new ImageView(this); qr.setContentDescription("访问地址二维码");
        qr.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams qrLayout = new LinearLayout.LayoutParams(dp(200), dp(200));
        qrLayout.gravity = android.view.Gravity.CENTER_HORIZONTAL;
        qrLayout.setMargins(dp(16), dp(4), dp(16), dp(12));
        access.addView(qr, qrLayout);
        code = text("", 16); code.setTextIsSelectable(true); code.setTypeface(android.graphics.Typeface.MONOSPACE); add(access, code);
        Button rotate = new Button(this); rotate.setText("重新配对所有浏览器"); UiStyle.button(rotate, false);
        rotate.setOnClickListener(v -> { LanAccessService.stop(this); enable(); }); UiStyle.addSpaced(root, rotate, 16, 0);
        TextView note = text("手机和电脑连接同一可信局域网，在浏览器输入上方地址，再输入配对码。HTTP 连接仅适合可信网络。关闭会立即撤销所有连接；应用退出或网络变化后需重新开启。", 13);
        UiStyle.muted(note); UiStyle.addSpaced(root, note, 18, 0);
        SettingsPageLayout.show(this, root);
    }
    private void enable() {
        java.util.ArrayList<String> permissions = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= 33) {
            if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.POST_NOTIFICATIONS);
            if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED) permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES);
        }
        if (!permissions.isEmpty()) requestPermissions(permissions.toArray(new String[0]), 81);
        else LanAccessService.start(this);
    }
    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
        super.onRequestPermissionsResult(request, permissions, grants);
        if (request != 81) return;
        boolean allowed = grants.length == permissions.length && grants.length > 0;
        for (int grant : grants) allowed &= grant == PackageManager.PERMISSION_GRANTED;
        if (allowed) LanAccessService.start(this);
        else android.widget.Toast.makeText(this, "请允许通知和附近设备权限后开启", android.widget.Toast.LENGTH_LONG).show();
        refresh();
    }
    private void refresh() {
        refreshing = true; enabled.setChecked(LanAccessService.running() || LanAccessService.starting()); refreshing = false;
        status.setText(LanAccessService.status());
        address.setText(LanAccessService.running() ? LanAccessService.url() : "开启后显示访问地址");
        String pairing = LanAccessService.pairing();
        code.setText(!LanAccessService.running() ? "开启后生成临时配对码"
                : pairing.isEmpty() ? "配对码已过期，请重新配对" : pairing);
        try { LanAddressQr.update(qr, LanAccessService.running() ? LanAccessService.url() : ""); }
        catch (com.google.zxing.WriterException error) {
            qr.setImageDrawable(null); qr.setTag(null); qr.setVisibility(android.view.View.GONE);
            status.setText("无法生成二维码，请复制访问地址");
        }
    }
    @Override protected void onResume() { super.onResume(); handler.post(poll); }
    @Override protected void onPause() { handler.removeCallbacks(poll); super.onPause(); }
    private LinearLayout group(LinearLayout root, String name) {
        TextView title = text(name, 12); UiStyle.muted(title); title.setPadding(dp(4),0,dp(4),0); if (Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true); UiStyle.addSpaced(root,title,16,7);
        LinearLayout group = new LinearLayout(this); group.setOrientation(LinearLayout.VERTICAL); UiStyle.glass(group); root.addView(group,new LinearLayout.LayoutParams(-1,-2)); return group;
    }
    private void add(LinearLayout root, TextView view) { view.setPadding(dp(16),dp(12),dp(16),dp(12)); root.addView(view,new LinearLayout.LayoutParams(-1,-2)); }
    private TextView text(String value, int size) { TextView view=new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(UiStyle.colors(this).text); return view; }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
