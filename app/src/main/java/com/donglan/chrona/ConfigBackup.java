package com.donglan.chrona;

import android.content.Context;

import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ai.AiSettingsStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * A portable copy of what the user typed into the app, written as JSON they can keep, move to a new
 * phone, or hand over. The endpoint and model always travel; the API key only when the user asks
 * for it, so a shared file does not leak a secret by default.
 */
public final class ConfigBackup {
    private static final String APP = "Chrona";
    private static final int FORMAT = 1;

    private ConfigBackup() {
    }

    /** Always carries the endpoint and model; the key only when {@code includeKey} is set. */
    public static String export(Context context, boolean includeKey)
            throws JSONException, java.security.GeneralSecurityException {
        JSONObject root = new JSONObject();
        root.put("app", APP);
        root.put("format", FORMAT);
        root.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                .format(new Date()));
        AiSettings settings = new AiSettingsStore(context).load();
        if (settings != null) {
            JSONObject ai = new JSONObject();
            ai.put("baseUrl", settings.baseUrl);
            ai.put("model", settings.model);
            if (includeKey) ai.put("apiKey", settings.apiKey == null ? "" : settings.apiKey);
            root.put("ai", ai);
        }
        JSONObject appearance = new JSONObject();
        appearance.put("mode", ThemeStore.mode(context));
        appearance.put("color", ThemeStore.color(context));
        root.put("appearance", appearance);
        return root.toString(2);
    }

    /** One line per thing the file carries, shown before anything is applied. */
    public static String describe(String json) throws JSONException {
        JSONObject root = parse(json);
        StringBuilder text = new StringBuilder();
        JSONObject ai = root.optJSONObject("ai");
        if (ai == null) {
            text.append("· 不含解析服务设置\n");
        } else {
            text.append("· 基础地址 ").append(empty(ai.optString("baseUrl"))).append('\n');
            text.append("· 模型 ").append(empty(ai.optString("model"))).append('\n');
            text.append(ai.has("apiKey")
                    ? "· 含 API 密钥，将覆盖本机密钥\n"
                    : "· 不含 API 密钥，保留本机密钥\n");
        }
        JSONObject appearance = root.optJSONObject("appearance");
        if (appearance != null) {
            text.append("· 外观 ").append(appearance.optString("mode", ThemeStore.SYSTEM))
                    .append(" / ").append(appearance.optString("color", ThemeStore.TEAL))
                    .append('\n');
        }
        String exportedAt = root.optString("exportedAt", "");
        if (!exportedAt.isEmpty()) text.append("· 导出于 ").append(exportedAt).append('\n');
        return text.toString();
    }

    /** Applies an exported file. Anything the file leaves out stays as it is on this device. */
    public static void apply(Context context, String json)
            throws JSONException, java.security.GeneralSecurityException {
        JSONObject root = parse(json);
        JSONObject ai = root.optJSONObject("ai");
        if (ai != null) {
            AiSettingsStore store = new AiSettingsStore(context);
            AiSettings existing = store.load();
            String key = ai.has("apiKey") ? ai.optString("apiKey", "")
                    : existing == null ? "" : existing.apiKey;
            store.save(ai.optString("baseUrl", "").trim(), ai.optString("model", "").trim(), key);
        }
        JSONObject appearance = root.optJSONObject("appearance");
        if (appearance != null) {
            // Last: applying appearance recreates every live screen, this one included.
            ThemeStore.setMode(context, appearance.optString("mode", ThemeStore.SYSTEM));
            ThemeStore.setColor(context, appearance.optString("color", ThemeStore.TEAL));
        }
    }

    private static JSONObject parse(String json) throws JSONException {
        if (json == null || json.trim().isEmpty()) throw new JSONException("文件是空的");
        JSONObject root = new JSONObject(json);
        if (!APP.equals(root.optString("app"))) throw new JSONException("这不是拾时的配置文件");
        return root;
    }

    private static String empty(String value) {
        return value == null || value.isEmpty() ? "（空）" : value;
    }
}
