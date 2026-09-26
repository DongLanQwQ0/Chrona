package com.donglan.chrona;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
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

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(20));
        root.setFitsSystemWindows(true);
        TextView title = new TextView(this);
        title.setText("AI 服务设置");
        title.setTextSize(24);
        root.addView(title);
        TextView help = new TextView(this);
        help.setText("填写兼容 OpenAI Chat Completions 的 HTTPS 地址，例如 https://example.com/v1。密钥保存在本机加密存储中。留空密钥可保留已有密钥。");
        help.setPadding(0, dp(12), 0, dp(12));
        root.addView(help);
        baseUrl = field(root, "API 基础地址（含 /v1）", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        model = field(root, "模型名称", InputType.TYPE_CLASS_TEXT);
        apiKey = field(root, "API 密钥（留空表示不更改）", InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        Button save = new Button(this);
        save.setText("保存设置");
        save.setOnClickListener(view -> save());
        root.addView(save);
        imageState = new TextView(this);
        imageState.setTextSize(14);
        imageState.setPadding(0, dp(16), 0, dp(6));
        root.addView(imageState);
        Button allowImages = new Button(this);
        allowImages.setText("重新允许图片输入");
        allowImages.setOnClickListener(view -> {
            new AiSettingsStore(this).clearImageSupport();
            refreshImageState();
            Toast.makeText(this, "图片输入已重新启用", Toast.LENGTH_SHORT).show();
        });
        root.addView(allowImages);
        setContentView(root);
        try {
            AiSettings existing = new AiSettingsStore(this).load();
            if (existing != null) {
                baseUrl.setText(existing.baseUrl);
                model.setText(existing.model);
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
            } else if (store.isImageUnsupported(settings)) {
                imageState.setText("该模型上次拒绝了图片输入，收件箱的图片入口已停用。"
                        + "确认模型支持视觉后可重新启用。");
            } else {
                imageState.setText("当前模型未发现图片输入问题。");
            }
        } catch (Exception exception) {
            imageState.setText("无法读取现有设置：" + exception.getMessage());
        }
    }

    private EditText field(LinearLayout root, String hint, int type) {
        EditText edit = new EditText(this);
        edit.setHint(hint);
        edit.setSingleLine(true);
        edit.setInputType(type);
        root.addView(edit, new LinearLayout.LayoutParams(-1, -2));
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
