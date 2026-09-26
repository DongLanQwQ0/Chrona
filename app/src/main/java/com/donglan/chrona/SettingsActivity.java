package com.donglan.chrona;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ai.AiSettingsStore;

/** User-supplied HTTPS Chat Completions endpoint and model. */
public final class SettingsActivity extends Activity {
    private EditText baseUrl;
    private EditText model;
    private EditText apiKey;
    private TextView imageState;
    private Button allowImages;

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
        preset.setText(presets[0] + "  ▾");
        preset.setTextSize(16);
        preset.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        preset.setPadding(dp(16), 0, dp(16), 0);
        preset.setMinHeight(dp(52));
        UiStyle.title(preset);
        UiStyle.pill(preset, false);
        UiStyle.addSpaced(form, preset, 5, 10);
        baseUrl = field(form, "API 基础地址（含 /v1）", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        model = field(form, "模型名称", InputType.TYPE_CLASS_TEXT);
        preset.setOnClickListener(view -> UiStyle.choiceDialog(this, "常用模型预设",
                presets, selectedPreset[0], position -> {
                selectedPreset[0] = position;
                preset.setText(presets[position] + "  ▾");
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
            Toast.makeText(this, "图片输入已重新启用", Toast.LENGTH_SHORT).show();
        });
        UiStyle.button(allowImages, false);
        UiStyle.addSpaced(imageCard, allowImages, 5, 5);
        UiStyle.addSpaced(root, imageCard, 0, 8);
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
                    preset.setText(presets[selectedPreset[0]] + "  ▾");
                }
            }
        } catch (Exception exception) {
            Toast.makeText(this, "无法读取现有设置，请重新填写密钥", Toast.LENGTH_LONG).show();
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

    private void save() {
        try {
            AiSettingsStore store = new AiSettingsStore(this);
            String key = apiKey.getText().toString().trim();
            if (key.isEmpty()) {
                AiSettings existing = store.load();
                if (existing != null) key = existing.apiKey;
            }
            store.save(baseUrl.getText().toString().trim(), model.getText().toString().trim(), key);
            Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show();
            finish();
        } catch (Exception exception) {
            Toast.makeText(this, "保存失败：" + exception.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
