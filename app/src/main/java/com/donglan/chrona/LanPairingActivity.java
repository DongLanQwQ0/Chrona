package com.donglan.chrona;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.lang.ref.WeakReference;

/** Native, non-exported consent. A web request can never approve itself. */
public final class LanPairingActivity extends Activity {
    private static WeakReference<Activity> foreground = new WeakReference<>(null);
    private static boolean opening;
    private static long openingAt;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() { refresh(); if (!isFinishing()) handler.postDelayed(this, 500); }
    };
    private TextView device, remote, verification;
    private String requestId = "";
    private boolean decided;

    static void activityResumed(Activity activity) {
        foreground = new WeakReference<>(activity);
        activity.getWindow().getDecorView().post(LanPairingActivity::present);
    }
    static void activityPaused(Activity activity) {
        if (foreground.get() == activity) foreground.clear();
    }
    static boolean present() {
        Activity activity = foreground.get();
        if (LanAccessService.pendingApproval() == null) return false;
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) return false;
        if (activity instanceof LanPairingActivity consent) { consent.refresh(); return true; }
        if (opening && android.os.SystemClock.uptimeMillis() - openingAt < 3000) return true;
        if (!activity.hasWindowFocus() || activity.getSystemService(KeyguardManager.class).isKeyguardLocked()) return false;
        opening = true; openingAt = android.os.SystemClock.uptimeMillis();
        activity.startActivity(new Intent(activity, LanPairingActivity.class));
        return true;
    }
    @Override protected void onCreate(Bundle saved) {
        ThemeStore.apply(this); super.onCreate(saved); opening = false;
        LinearLayout root = SettingsPageLayout.content(this);
        SettingsPageLayout.header(this, root, "浏览器授权");
        LinearLayout identity = new LinearLayout(this); identity.setOrientation(LinearLayout.VERTICAL);
        identity.setPadding(dp(16), dp(16), dp(16), dp(16)); UiStyle.card(identity);
        root.addView(identity, new LinearLayout.LayoutParams(-1, -2));
        device = label(identity, "", 20); device.setTypeface(null, Typeface.BOLD);
        remote = label(identity, "", 15); UiStyle.muted(remote);
        TextView caption = label(identity, "核对码", 14); UiStyle.muted(caption);
        LinearLayout.LayoutParams captionLayout = (LinearLayout.LayoutParams)caption.getLayoutParams(); captionLayout.topMargin = dp(16);
        verification = label(identity, "", 32); verification.setTypeface(Typeface.MONOSPACE);
        verification.setTextColor(UiStyle.colors(this).primary);
        LinearLayout actions = new LinearLayout(this); actions.setGravity(Gravity.CENTER_VERTICAL);
        Button deny = button("拒绝", false, () -> decide(false));
        Button allow = button("允许访问", true, () -> decide(true));
        allow.setFilterTouchesWhenObscured(true);
        LinearLayout.LayoutParams left = new LinearLayout.LayoutParams(0, -2, 1); left.rightMargin = dp(8);
        actions.addView(deny, left); actions.addView(allow, new LinearLayout.LayoutParams(0, -2, 1));
        UiStyle.addSpaced(root, actions, 16, 0);
        if (saved != null) requestId = saved.getString("request", "");
        SettingsPageLayout.show(this, root, saved == null); refresh();
    }
    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); setIntent(intent); refresh(); }
    @Override protected void onResume() { super.onResume(); handler.post(poll); }
    @Override protected void onPause() { handler.removeCallbacks(poll); super.onPause(); }
    @Override protected void onSaveInstanceState(Bundle state) { state.putString("request", requestId); super.onSaveInstanceState(state); }
    @Override protected void onDestroy() {
        handler.removeCallbacks(poll);
        if (isFinishing() && !decided && !requestId.isEmpty()) LanAccessService.decideApproval(requestId, false);
        super.onDestroy();
    }
    private void refresh() {
        if (isFinishing() || device == null) return;
        LanSecurity.Approval request = LanAccessService.pendingApproval();
        if (request == null) { decided = true; finish(); return; }
        if (request.id.equals(requestId) && device.length() > 0) return;
        if (!request.id.equals(requestId)) { requestId = request.id; decided = false; }
        device.setText(request.device); remote.setText(request.remote); verification.setText(request.verification);
    }
    private void decide(boolean allow) {
        if (getSystemService(KeyguardManager.class).isKeyguardLocked()) return;
        decided = LanAccessService.decideApproval(requestId, allow);
        if (decided) finish(); else refresh();
    }
    private TextView label(LinearLayout parent, String text, int size) {
        TextView view = new TextView(this); view.setText(text); view.setTextSize(size);
        view.setTextColor(UiStyle.colors(this).text);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2); layout.bottomMargin = dp(6);
        parent.addView(view, layout); return view;
    }
    private Button button(String text, boolean primary, Runnable action) {
        Button button = new Button(this); button.setText(text); button.setAllCaps(false); UiStyle.button(button, primary);
        button.setOnClickListener(v -> action.run()); return button;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
