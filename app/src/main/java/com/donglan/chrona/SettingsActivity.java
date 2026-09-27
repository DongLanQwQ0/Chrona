package com.donglan.chrona;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ai.AiSettingsStore;

/** User-supplied HTTPS Chat Completions endpoint and model. */
public final class SettingsActivity extends Activity {
    private static final int CREATE_CONFIG = 31;
    private static final int PICK_CONFIG = 32;
    private static final int MAX_CONFIG_BYTES = 256 * 1024;
    private EditText baseUrl;
    private EditText model;
    private EditText apiKey;
    private TextView imageState;
    private Button allowImages;
    private CheckBox includeKey;

    @Override
    protected void onCreate(Bundle state) {
        ThemeStore.apply(this);
        super.onCreate(state);
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(20));
        root.setFitsSystemWindows(true);
        UiStyle.page(this, root);
        root.setBackgroundColor(Color.TRANSPARENT);
        page.addView(root);
        UiStyle.back(this, root);
        TextView title = new TextView(this);
        title.setText("AI 服务设置");
        title.setTextSize(28);
        UiStyle.title(title);
        root.addView(title);
        TextView help = new TextView(this);
        help.setText("配置解析服务。密钥保存在本机加密存储中，留空可保留已有密钥。");
        help.setPadding(0, dp(12), 0, dp(12));
        UiStyle.muted(help);
        root.addView(help);
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(dp(16), dp(16), dp(16), dp(16));
        UiStyle.glass(form);
        TextView presetTitle = new TextView(this);
        presetTitle.setText("常用模型预设（选择后仍可编辑）");
        presetTitle.setTextSize(15);
        UiStyle.muted(presetTitle);
        form.addView(presetTitle);
        String[] presets = {"自定义服务", "DeepSeek Flash", "DeepSeek Pro"};
        int[] selectedPreset = {0};
        TextView preset = new TextView(this);
        preset.setText(presets[0]);
        UiStyle.fieldTrigger(preset);
        UiStyle.addSpaced(form, preset, 5, 10);
        baseUrl = field(form, "API 基础地址（含 /v1）", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        model = field(form, "模型名称", InputType.TYPE_CLASS_TEXT);
        preset.setOnClickListener(view -> UiStyle.choiceDialog(this, "常用模型预设",
                presets, selectedPreset[0], position -> {
                selectedPreset[0] = position;
                preset.setText(presets[position]);
                if (position == 0) return;
                baseUrl.setText("https://api.deepseek.com");
                model.setText(position == 1 ? "deepseek-flash" : "deepseek-v4-pro");
        }));
        apiKey = field(form, "API 密钥（留空表示不更改）", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button save = new Button(this);
        save.setText("保存设置");
        save.setOnClickListener(view -> save());
        UiStyle.button(save, true);
        UiStyle.addSpaced(form, save, 12, 4);
        UiStyle.addSpaced(root, form, 8, 18);

        LinearLayout imageCard = new LinearLayout(this);
        imageCard.setOrientation(LinearLayout.VERTICAL);
        imageCard.setPadding(dp(16), dp(13), dp(16), dp(13));
        UiStyle.glass(imageCard);
        TextView imageTitle = new TextView(this);
        imageTitle.setText("图片支持");
        imageTitle.setTextSize(18);
        UiStyle.title(imageTitle);
        imageCard.addView(imageTitle);
        imageState = new TextView(this);
        imageState.setTextSize(14);
        imageState.setPadding(0, dp(8), 0, dp(6));
        UiStyle.muted(imageState);
        imageCard.addView(imageState);
        allowImages = new Button(this);
        allowImages.setText("重新允许图片输入");
        allowImages.setOnClickListener(view -> {
            new AiSettingsStore(this).clearImageSupport();
            refreshImageState();
            Feedback.show(this, "图片输入已重新启用");
        });
        UiStyle.button(allowImages, false);
        UiStyle.addSpaced(imageCard, allowImages, 5, 5);
        UiStyle.addSpaced(root, imageCard, 0, 8);

        LinearLayout backupCard = new LinearLayout(this);
        backupCard.setOrientation(LinearLayout.VERTICAL);
        backupCard.setPadding(dp(16), dp(13), dp(16), dp(13));
        UiStyle.glass(backupCard);
        TextView backupTitle = new TextView(this);
        backupTitle.setText("备份与恢复");
        backupTitle.setTextSize(18);
        UiStyle.title(backupTitle);
        backupCard.addView(backupTitle);
        TextView backupState = new TextView(this);
        backupState.setText("导出的文件总是包含基础地址与模型。API 密钥默认不导出，"
                + "取消勾选时密钥不会写入文件。");
        backupState.setTextSize(14);
        backupState.setPadding(0, dp(8), 0, dp(6));
        UiStyle.muted(backupState);
        backupCard.addView(backupState);
        includeKey = new CheckBox(this);
        includeKey.setText("导出时包含 API 密钥");
        includeKey.setChecked(false);
        UiStyle.addSpaced(backupCard, includeKey, 0, 8);
        TextView keyNote = new TextView(this);
        keyNote.setText("勾选后密钥会以明文写入导出文件，只保存到可信位置。");
        keyNote.setTextSize(13);
        UiStyle.muted(keyNote);
        UiStyle.addSpaced(backupCard, keyNote, 0, 10);
        LinearLayout backupActions = new LinearLayout(this);
        backupActions.setOrientation(LinearLayout.HORIZONTAL);
        Button export = new Button(this);
        export.setText("导出配置");
        export.setOnClickListener(view -> exportConfig());
        UiStyle.button(export, false);
        backupActions.addView(export, new LinearLayout.LayoutParams(0, dp(52), 1));
        Button importButton = new Button(this);
        importButton.setText("导入配置");
        importButton.setOnClickListener(view -> importConfig());
        UiStyle.button(importButton, false);
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(0, dp(52), 1);
        importParams.setMargins(dp(8), 0, 0, 0);
        backupActions.addView(importButton, importParams);
        UiStyle.addSpaced(backupCard, backupActions, 0, 0);
        UiStyle.addSpaced(root, backupCard, 0, 8);
        FrameLayout stage = new FrameLayout(this);
        stage.addView(new GlassBackdropView(this), new FrameLayout.LayoutParams(-1, -1));
        stage.addView(page, new FrameLayout.LayoutParams(-1, -1));
        root.setFitsSystemWindows(false);
        UiStyle.applyInsets(stage, page);
        setContentView(stage);
        try {
            AiSettings existing = new AiSettingsStore(this).load();
            if (existing != null) {
                baseUrl.setText(existing.baseUrl);
                model.setText(existing.model);
                if ("https://api.deepseek.com".equals(existing.baseUrl)) {
                    if ("deepseek-flash".equals(existing.model)) selectedPreset[0] = 1;
                    else if ("deepseek-v4-pro".equals(existing.model)) selectedPreset[0] = 2;
                    preset.setText(presets[selectedPreset[0]]);
                }
            }
        } catch (Exception exception) {
            Feedback.showLong(this, "无法读取现有设置，请重新填写密钥");
        }
        ConfigBackup.Imported pending = ConfigBackup.pending(this);
        if (pending != null) {
            try {
                if (new AiSettingsStore(this).load() == null) {
                    baseUrl.setText(pending.baseUrl);
                    model.setText(pending.model);
                }
            } catch (Exception ignored) { }
        }
        refreshImageState();
    }

    /** Shows whether this endpoint and model were already found to reject images. */
    private void refreshImageState() {
        try {
            AiSettingsStore store = new AiSettingsStore(this);
            AiSettings settings = store.load();
            if (settings == null) {
                imageState.setText("尚未配置服务，无法判断是否支持图片。");
                allowImages.setVisibility(android.view.View.GONE);
            } else if (store.isKnownImageUnsupported(settings)) {
                imageState.setText("DeepSeek Pro 当前不支持视觉输入，图片入口已隐藏。"
                        + "如需识别图片，请改用支持视觉的模型。");
                allowImages.setVisibility(android.view.View.GONE);
            } else if (store.isImageUnsupported(settings)) {
                imageState.setText("该模型上次拒绝了图片输入，收件箱的图片入口已停用。"
                        + "确认模型支持视觉后可重新启用。");
                allowImages.setVisibility(android.view.View.VISIBLE);
            } else {
                imageState.setText("当前模型未发现图片输入问题。");
                allowImages.setVisibility(android.view.View.GONE);
            }
        } catch (Exception exception) {
            imageState.setText("无法读取现有设置：" + exception.getMessage());
            allowImages.setVisibility(android.view.View.GONE);
        }
    }

    private EditText field(LinearLayout root, String hint, int type) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setInputType(type);
        edit.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        UiStyle.input(edit);
        UiStyle.addSpaced(root, edit, 5, 5);
        return edit;
    }

    /** Writes the settings the user filled in to a file they choose. */
    private void exportConfig() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "chrona-config.json");
        try {
            startActivityForResult(intent, CREATE_CONFIG);
        } catch (Exception exception) {
            Feedback.showLong(this, "无法打开文件选择器：" + exception.getMessage());
        }
    }

    private void importConfig() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        try {
            startActivityForResult(intent, PICK_CONFIG);
        } catch (Exception exception) {
            Feedback.showLong(this, "无法打开文件选择器：" + exception.getMessage());
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        if (requestCode == CREATE_CONFIG) {
            writeConfig(data.getData());
        } else if (requestCode == PICK_CONFIG) {
            readConfig(data.getData());
        }
    }

    private void writeConfig(Uri target) {
        boolean withKey = includeKey != null && includeKey.isChecked();
        try {
            String json = ConfigBackup.export(this, withKey, baseUrl.getText().toString(),
                    model.getText().toString(), apiKey.getText().toString());
            try (java.io.OutputStream output = getContentResolver().openOutputStream(target)) {
                if (output == null) throw new java.io.IOException("无法写入所选位置");
                output.write(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            }
            Feedback.showLong(this, ConfigBackup.containsApiKey(json)
                    ? "已导出配置（含 API 密钥，请妥善保管该文件）"
                    : "已导出配置（不含 API 密钥）");
        } catch (Exception exception) {
            Feedback.showLong(this, "导出失败：" + exception.getMessage());
        }
    }

    private void readConfig(Uri source) {
        String json;
        try (java.io.InputStream input = getContentResolver().openInputStream(source)) {
            if (input == null) throw new java.io.IOException("无法读取所选文件");
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) {
                if (buffer.size() + read > MAX_CONFIG_BYTES)
                    throw new java.io.IOException("配置文件超过 256 KB");
                buffer.write(chunk, 0, read);
            }
            json = new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
            if (json.startsWith("\uFEFF")) json = json.substring(1);
        } catch (Exception exception) {
            Feedback.showLong(this, "读取失败：" + exception.getMessage());
            return;
        }
        final String content = json;
        String summary;
        try {
            summary = ConfigBackup.describe(content);
        } catch (Exception exception) {
            Feedback.showLong(this, "这不是可用的配置文件：" + exception.getMessage());
            return;
        }
        UiStyle.confirmDialog(this, "导入这份配置？", summary + "现有的同名字段会被覆盖。",
                "导入", () -> applyImported(content));
    }

    private void applyImported(String json) {
        try {
            ConfigBackup.Imported imported = ConfigBackup.apply(this, json);
            if (imported.hasAi) {
                baseUrl.setText(imported.baseUrl);
                model.setText(imported.model);
                apiKey.setText("");
            }
            Feedback.show(this, "配置已导入");
        } catch (Exception exception) {
            Feedback.showLong(this, "导入失败：" + exception.getMessage());
        }
    }

    private void save() {
        try {
            AiSettingsStore store = new AiSettingsStore(this);
            String key = apiKey.getText().toString().trim();
            if (key.isEmpty()) {
                AiSettings existing = store.load();
                if (existing != null) key = existing.apiKey;
            }
            store.save(baseUrl.getText().toString().trim(), model.getText().toString().trim(), key);
            ConfigBackup.clearDraft(this);
            Feedback.show(this, "设置已保存");
            finish();
        } catch (Exception exception) {
            Feedback.showLong(this, "保存失败：" + exception.getMessage());
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
