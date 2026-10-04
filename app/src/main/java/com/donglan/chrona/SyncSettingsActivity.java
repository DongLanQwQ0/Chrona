package com.donglan.chrona;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.*;
import com.donglan.chrona.sync.WebDavClient;
import java.lang.ref.WeakReference;
import java.util.List;
import java.util.concurrent.Executors;

/** Native secondary settings page. Work retains the application, never an Activity. */
public final class SyncSettingsActivity extends Activity {
    private EditText url, user, password;
    private TextView status;
    private LinearLayout pending;
    private final android.os.Handler refresh = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override public void run() {
            status.setText(AndroidSync.running() ? "正在同步…" : new WebDavSettingsStore(SyncSettingsActivity.this).status());
            refresh.postDelayed(this, 1500);
        }
    };
    @Override protected void onCreate(Bundle saved) {
        ThemeStore.apply(this); super.onCreate(saved);
        WebDavSettingsStore settings = new WebDavSettingsStore(this);
        LinearLayout root = SettingsPageLayout.content(this);
        SettingsPageLayout.header(this, root, "跨设备同步");
        LinearLayout account = group(root, "坚果云 WebDAV");
        url = field(account, "WebDAV 地址", settings.url(), false);
        user = field(account, "账号", settings.user(), false);
        password = field(account, settings.configured() ? "应用密码（留空保留）" : "应用密码", "", true);
        button(account, "保存设置", () -> save(false));
        button(account, "测试连接", () -> save(true));
        LinearLayout actions = group(root, "同步");
        Switch auto = new Switch(this); auto.setText("自动同步"); auto.setTextColor(UiStyle.colors(this).text);
        auto.setPadding(dp(14), dp(10), dp(14), dp(10)); auto.setChecked(settings.automatic());
        UiStyle.toggle(auto);
        auto.setOnCheckedChangeListener((v, checked) -> {
            if (checked && !new WebDavSettingsStore(this).configured()) {
                auto.setChecked(false); status.setText("请先保存账号和应用密码"); return;
            }
            new WebDavSettingsStore(this).automatic(checked); SyncJobService.schedule(this);
        }); actions.addView(auto, new LinearLayout.LayoutParams(-1, dp(54)));
        button(actions, "立即同步", () -> {
            if (!AndroidSync.request(this, completion())) status.setText("同步正在进行");
        });
        status = text(settings.status(), 13); UiStyle.muted(status); status.setPadding(dp(14), dp(10), dp(14), dp(14)); actions.addView(status);
        TextView version = text("合并日程后，所有同步设备需使用 0.14.4 或更新版本",13);
        UiStyle.muted(version);version.setPadding(dp(14),0,dp(14),dp(14));actions.addView(version);
        pending = group(root, "待确认记录");
        button(pending, "查看待确认记录", this::loadPending);
        SettingsPageLayout.show(this, root);
    }
    @Override protected void onResume() { super.onResume(); refresh.post(poll); }
    @Override protected void onPause() { refresh.removeCallbacks(poll); super.onPause(); }
    private AndroidSync.Completion completion() {
        WeakReference<SyncSettingsActivity> owner = new WeakReference<>(this);
        return result -> { SyncSettingsActivity activity = owner.get();
            if (activity != null && !activity.isDestroyed() && !activity.isFinishing()) activity.status.setText(result);
        };
    }
    private void save(boolean test) {
        String base = url.getText().toString().trim(), account = user.getText().toString().trim(), secret = password.getText().toString();
        if (AndroidSync.work(this, completion(), app -> {
            WebDavSettingsStore settings = new WebDavSettingsStore(app);
            String actual = secret.isEmpty() && settings.configured() ? settings.password() : secret;
            settings.save(base, account, actual); SyncJobService.schedule(app);
            if (test) try (WebDavClient client = new WebDavClient(base, account, actual)) { client.testConnection(); }
            return test ? "连接成功" : "同步设置已保存";
        })) password.setText("");
        else status.setText("同步正在进行，请稍后保存");
    }
    private void loadPending() {
        WeakReference<SyncSettingsActivity> owner = new WeakReference<>(this);
        android.content.Context app = getApplicationContext();
        java.util.concurrent.ExecutorService reader = Executors.newSingleThreadExecutor();
        reader.execute(() -> {
            List<AndroidSync.Pending> records;
            try { synchronized (AndroidSync.LOCK) { records = AndroidSync.pending(app); } }
            catch (Exception exception) { records = null; }
            List<AndroidSync.Pending> result = records;
            new android.os.Handler(android.os.Looper.getMainLooper()).post(() -> {
                SyncSettingsActivity activity = owner.get();
                if (activity == null || activity.isDestroyed() || activity.isFinishing()) return;
                activity.pending.removeViews(1, activity.pending.getChildCount() - 1);
                if (result == null || result.isEmpty()) activity.pending.addView(activity.text(result == null ? "无法读取待确认记录" : "暂无待确认记录", 14));
                else for (AndroidSync.Pending record : result) activity.button(activity.pending, record.title, () -> activity.choose(record));
            }); reader.shutdown();
        });
    }
    private void choose(AndroidSync.Pending record) {
        String[] labels = new String[record.versions.size() + 1]; labels[0] = "保留本机版本";
        for (int i = 0; i < record.versions.size(); i++) labels[i + 1] = "版本 " + (i + 1) + "\n" + AndroidSync.describe(record.versions.get(i));
        UiStyle.choiceDialog(this, record.title, labels, -1, which -> {
            if (which == 0) { AndroidSync.resolve(this, record, -1, false, completion()); return; }
            if (!record.calendar) { AndroidSync.resolve(this, record, which - 1, false, completion()); return; }
            UiStyle.confirmDialog(this, "移除本机日历中的旧事件并应用云端版本",
                    "此操作将移除这条记录已写入系统日历的全部旧事件及其系统提醒，然后应用所选版本。新日程需再次手动写入日历。若部分移除失败，将保留记录供重试。",
                    "移除旧事件并应用", () -> AndroidSync.resolve(this, record, which - 1, true, completion()));
        });
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size) { TextView text = new TextView(this); text.setText(value); text.setTextSize(size); text.setTextColor(UiStyle.colors(this).text); return text; }
    private LinearLayout group(LinearLayout root, String name) {
        TextView title = text(name, 12); UiStyle.muted(title); title.setPadding(dp(4), 0, dp(4), 0);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        UiStyle.addSpaced(root, title, 16, 7);
        LinearLayout group = new LinearLayout(this); group.setOrientation(LinearLayout.VERTICAL); UiStyle.glass(group);
        root.addView(group, new LinearLayout.LayoutParams(-1, -2)); return group;
    }
    private EditText field(LinearLayout parent, String hint, String value, boolean secret) {
        EditText edit = new EditText(this); edit.setHint(hint); edit.setText(value); edit.setTextSize(15);
        edit.setTextColor(UiStyle.colors(this).text); edit.setHintTextColor(UiStyle.colors(this).muted);
        edit.setSingleLine(true); edit.setPadding(dp(14), dp(12), dp(14), dp(12));
        edit.setInputType(InputType.TYPE_CLASS_TEXT | (secret ? InputType.TYPE_TEXT_VARIATION_PASSWORD : InputType.TYPE_TEXT_VARIATION_URI));
        edit.setSaveEnabled(!secret); UiStyle.input(edit);
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2); layout.setMargins(dp(12), dp(8), dp(12), dp(4));
        parent.addView(edit, layout); return edit;
    }
    private void button(LinearLayout parent, String label, Runnable click) {
        Button button = new Button(this); button.setText(label);
        UiStyle.button(button, label.equals("保存设置") || label.equals("立即同步"));
        button.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams layout = new LinearLayout.LayoutParams(-1, -2); layout.setMargins(dp(12), dp(8), dp(12), dp(8));
        parent.addView(button, layout);
    }
}
