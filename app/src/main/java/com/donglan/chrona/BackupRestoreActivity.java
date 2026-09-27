package com.donglan.chrona;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Full portable snapshot flow, separate from the AI connection settings screen. */
public final class BackupRestoreActivity extends Activity {
    private static final int CREATE_BACKUP = 41;
    private static final int PICK_BACKUP = 42;
    private CheckBox includeApiKey;
    private Button exportButton;
    private Button importButton;
    private TextView progress;

    @Override protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        ScrollView page = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        UiStyle.back(this, root);

        TextView title = new TextView(this);
        title.setText("备份与恢复");
        title.setTextSize(26);
        UiStyle.title(title);
        UiStyle.addSpaced(root, title, 4, 8);
        TextView description = new TextView(this);
        description.setText("完整备份收件箱、日程草稿、图片、普通附件、模型输出和应用设置。"
                + "系统日历会按日程内容重新关联；壁纸图片不包含在备份中。");
        description.setTextSize(14);
        UiStyle.muted(description);
        root.addView(description);

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(16));
        UiStyle.glass(card);
        includeApiKey = new CheckBox(this);
        includeApiKey.setText("备份中包含 API 密钥");
        includeApiKey.setContentDescription("选择是否把 API 密钥写入备份文件");
        card.addView(includeApiKey);
        TextView warning = new TextView(this);
        warning.setText("默认不包含密钥。勾选后密钥会明文写入 ZIP，请只保存到可信位置。");
        warning.setTextSize(13);
        UiStyle.muted(warning);
        UiStyle.addSpaced(card, warning, 4, 12);
        exportButton = new Button(this);
        exportButton.setText("导出完整备份 ZIP");
        UiStyle.button(exportButton, true);
        exportButton.setOnClickListener(view -> chooseBackupDestination());
        UiStyle.addSpaced(card, exportButton, 0, 8);
        importButton = new Button(this);
        importButton.setText("从 ZIP 恢复");
        UiStyle.button(importButton, false);
        importButton.setOnClickListener(view -> chooseBackupSource());
        card.addView(importButton);
        UiStyle.addSpaced(root, card, 14, 8);

        progress = new TextView(this);
        progress.setTextSize(14);
        progress.setVisibility(View.GONE);
        UiStyle.muted(progress);
        root.addView(progress);

        page.addView(root);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        root.setFitsSystemWindows(false);
        UiStyle.applyInsets(stage, page);
        setContentView(stage);
    }

    private void chooseBackupDestination() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "Chrona-backup.zip");
        startActivityForResult(intent, CREATE_BACKUP);
    }

    private void chooseBackupSource() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        startActivityForResult(intent, PICK_BACKUP);
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == CREATE_BACKUP) exportTo(uri);
        else if (requestCode == PICK_BACKUP) prepareImport(uri);
    }

    private void exportTo(Uri destination) {
        setBusy(true, "正在打包本机数据…");
        boolean withKey = includeApiKey.isChecked();
        new Thread(() -> {
            String error = null;
            try {
                ChronaDataBackup.write(this, destination, withKey);
            } catch (Exception exception) {
                error = exception.getMessage();
            }
            String result = error;
            runOnUiThread(() -> {
                setBusy(false, null);
                Feedback.showLong(this, result == null ? "完整备份已导出" : "导出失败：" + result);
            });
        }, "chrona-backup-export").start();
    }

    private void prepareImport(Uri source) {
        setBusy(true, "正在校验备份…");
        new Thread(() -> {
            ChronaDataBackup.Prepared prepared = null;
            String error = null;
            try {
                prepared = ChronaDataBackup.prepare(this, source);
            } catch (Exception exception) {
                error = exception.getMessage();
            }
            ChronaDataBackup.Prepared result = prepared;
            String failure = error;
            runOnUiThread(() -> {
                setBusy(false, null);
                if (failure != null) {
                    Feedback.showLong(this, "备份校验失败：" + failure);
                    return;
                }
                showRestoreConfirmation(result);
            });
        }, "chrona-backup-inspect").start();
    }

    private void showRestoreConfirmation(ChronaDataBackup.Prepared prepared) {
        Dialog dialog = new Dialog(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(18), dp(16), dp(18), dp(16));
        UiStyle.card(panel);
        TextView title = new TextView(this);
        title.setText("确认恢复备份");
        title.setTextSize(19);
        title.setGravity(Gravity.CENTER);
        UiStyle.title(title);
        panel.addView(title);
        TextView summary = new TextView(this);
        try {
            summary.setText(prepared.summary());
        } catch (Exception exception) {
            prepared.cleanup();
            Feedback.showLong(this, "无法读取备份摘要：" + exception.getMessage());
            return;
        }
        summary.setTextSize(14);
        summary.setLineSpacing(dp(3), 1f);
        UiStyle.muted(summary);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(summary);
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, dp(240)));
        TextView warning = new TextView(this);
        warning.setText("继续后会替换本机收件箱、日程草稿和相关设置。现有公共附件文件不会删除。");
        warning.setTextSize(13);
        UiStyle.muted(warning);
        UiStyle.addSpaced(panel, warning, 8, 12);
        LinearLayout actions = new LinearLayout(this);
        Button cancel = new Button(this);
        cancel.setText("取消");
        UiStyle.button(cancel, false);
        cancel.setOnClickListener(view -> dialog.cancel());
        Button restore = new Button(this);
        restore.setText("恢复并替换");
        UiStyle.button(restore, true);
        restore.setOnClickListener(view -> {
            dialog.dismiss();
            restorePrepared(prepared);
        });
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        actionParams.setMargins(0, 0, dp(6), 0);
        actions.addView(cancel, actionParams);
        LinearLayout.LayoutParams restoreParams = new LinearLayout.LayoutParams(0, dp(50), 1f);
        restoreParams.setMargins(dp(6), 0, 0, 0);
        actions.addView(restore, restoreParams);
        panel.addView(actions);
        UiStyle.showFloatingDialog(dialog, panel);
        dialog.setOnCancelListener(ignored -> prepared.cleanup());
        dialog.setCanceledOnTouchOutside(false);
    }

    private void restorePrepared(ChronaDataBackup.Prepared prepared) {
        setBusy(true, "正在恢复数据…请勿退出应用");
        new Thread(() -> {
            String message = null;
            String error = null;
            try {
                message = ChronaDataBackup.restore(this, prepared);
            } catch (Exception exception) {
                error = exception.getMessage();
            } finally {
                prepared.cleanup();
            }
            String result = message;
            String failure = error;
            runOnUiThread(() -> {
                setBusy(false, null);
                if (failure != null) Feedback.showLong(this, "恢复失败：" + failure);
                else {
                    Feedback.showLong(this, result);
                    recreate();
                }
            });
        }, "chrona-backup-restore").start();
    }

    private void setBusy(boolean busy, String message) {
        exportButton.setEnabled(!busy);
        importButton.setEnabled(!busy);
        includeApiKey.setEnabled(!busy);
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        if (busy && message != null) progress.setText(message);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + .5f);
    }
}
