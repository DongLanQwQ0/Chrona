package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;

import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ai.AiSettingsStore;

import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/** Portable AI and appearance configuration. API keys are included only by explicit request. */
public final class ConfigBackup {
    private static final String APP = "Chrona";
    private static final int FORMAT = 1;
    private static final String DRAFT = "chrona_config_import_draft";

    private ConfigBackup() { }

    public static final class Imported {
        public final boolean hasAi, hasApiKey, hasAppearance;
        public final boolean hasAcrylic, acrylicEnabled;
        public final boolean hasGaussianBlur, gaussianBlur, hasCustomColor;
        public final boolean hasSurfaceMix, hasBlurStrength;
        public final String baseUrl, model, apiKey, mode, color, customColor;
        public final int surfaceMix, blurStrength;

        private Imported(JSONObject root) {
            JSONObject ai = root.optJSONObject("ai");
            hasAi = ai != null;
            hasApiKey = hasAi && ai.has("apiKey");
            baseUrl = hasAi ? ai.optString("baseUrl", "").trim() : "";
            model = hasAi ? ai.optString("model", "").trim() : "";
            apiKey = hasApiKey ? ai.optString("apiKey", "") : null;
            JSONObject appearance = root.optJSONObject("appearance");
            hasAppearance = appearance != null;
            mode = hasAppearance ? appearance.optString("mode", ThemeStore.SYSTEM) : null;
            color = hasAppearance ? appearance.optString("color", ThemeStore.TEAL) : null;
            hasCustomColor = hasAppearance && appearance.has("customColor");
            customColor = hasCustomColor ? appearance.optString("customColor", null) : null;
            hasAcrylic = hasAppearance && appearance.has("acrylicEnabled");
            acrylicEnabled = hasAcrylic && appearance.optBoolean("acrylicEnabled");
            hasGaussianBlur = hasAppearance && appearance.has("gaussianBlur");
            gaussianBlur = hasGaussianBlur && appearance.optBoolean("gaussianBlur");
            hasSurfaceMix = hasAppearance && appearance.has("surfaceMix");
            surfaceMix = hasSurfaceMix ? appearance.optInt("surfaceMix", 40) : 40;
            hasBlurStrength = hasAppearance && appearance.has("blurStrength");
            blurStrength = hasBlurStrength ? appearance.optInt("blurStrength", 2) : 2;
        }

        private Imported(String baseUrl, String model) {
            hasAi = true;
            hasApiKey = false;
            hasAppearance = false;
            hasAcrylic = false;
            acrylicEnabled = false;
            hasGaussianBlur = false;
            hasCustomColor = false;
            hasSurfaceMix = false;
            hasBlurStrength = false;
            gaussianBlur = false;
            this.baseUrl = baseUrl;
            this.model = model;
            apiKey = null;
            mode = null;
            color = null;
            customColor = null;
            surfaceMix = 40;
            blurStrength = 2;
        }
    }

    /** The address and model are taken from the visible form; only the key follows the checkbox. */
    public static String export(Context context, boolean includeKey, String baseUrl, String model,
            String enteredKey) throws JSONException, java.security.GeneralSecurityException {
        boolean needSavedSettings = baseUrl == null || baseUrl.trim().isEmpty()
                || model == null || model.trim().isEmpty()
                || (includeKey && (enteredKey == null || enteredKey.trim().isEmpty()));
        AiSettings saved = needSavedSettings ? new AiSettingsStore(context).load() : null;
        SharedPreferences draft = context.getSharedPreferences(DRAFT, Context.MODE_PRIVATE);
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            baseUrl = saved != null ? saved.baseUrl : draft.getString("baseUrl", "");
        }
        if (model == null || model.trim().isEmpty()) {
            model = saved != null ? saved.model : draft.getString("model", "");
        }
        JSONObject root = new JSONObject();
        root.put("app", APP);
        root.put("format", FORMAT);
        root.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
                .format(new Date()));
        JSONObject ai = new JSONObject();
        ai.put("baseUrl", baseUrl == null ? "" : baseUrl.trim());
        ai.put("model", model == null ? "" : model.trim());
        String key = enteredKey == null || enteredKey.trim().isEmpty()
                ? saved == null ? null : saved.apiKey : enteredKey.trim();
        if (includeKey && key != null && !key.isEmpty()) ai.put("apiKey", key);
        root.put("ai", ai);
        JSONObject appearance = new JSONObject();
        appearance.put("mode", ThemeStore.mode(context));
        appearance.put("color", ThemeStore.color(context));
        appearance.put("customColor", ThemeStore.customColorHex(context));
        appearance.put("acrylicEnabled", ThemeStore.acrylicEnabled(context));
        appearance.put("gaussianBlur", ThemeStore.gaussianBlur(context));
        appearance.put("surfaceMix", ThemeStore.surfaceMix(context));
        appearance.put("blurStrength", ThemeStore.blurStrength(context));
        root.put("appearance", appearance);
        return root.toString(2);
    }

    /** Kept for callers that export the last saved service settings. */
    public static String export(Context context, boolean includeKey)
            throws JSONException, java.security.GeneralSecurityException {
        AiSettings settings = new AiSettingsStore(context).load();
        return export(context, includeKey, settings == null ? "" : settings.baseUrl,
                settings == null ? "" : settings.model, null);
    }

    public static boolean containsApiKey(String json) throws JSONException {
        JSONObject ai = parse(json).optJSONObject("ai");
        return ai != null && ai.has("apiKey");
    }

    public static Imported inspect(String json) throws JSONException {
        return new Imported(parse(json));
    }

    public static String describe(String json) throws JSONException {
        Imported config = inspect(json);
        StringBuilder text = new StringBuilder();
        if (!config.hasAi) text.append("· 不含解析服务设置\n");
        else {
            text.append("· 基础地址 ").append(empty(config.baseUrl)).append('\n');
            text.append("· 模型 ").append(empty(config.model)).append('\n');
            text.append(config.hasApiKey ? "· 含 API 密钥，将覆盖本机密钥\n"
                    : "· 不含 API 密钥；本机已有密钥会保留，新设备需补填密钥\n");
        }
        if (config.hasAppearance) text.append("· 外观 ").append(config.mode)
                .append(" / ").append(config.color)
                .append(config.hasCustomColor && ThemeStore.CUSTOM.equals(config.color)
                        ? " " + config.customColor : "")
                .append(config.hasAcrylic ? (config.acrylicEnabled ? " / 毛玻璃开启" : " / 毛玻璃关闭") : "")
                .append(config.hasGaussianBlur
                        ? (config.gaussianBlur ? " / 高斯模糊" : " / 普通模糊") : "")
                .append(config.hasSurfaceMix ? " / 面板纯色浓度 " + config.surfaceMix + "%" : "")
                .append(config.hasBlurStrength ? " / 模糊强度 " + config.blurStrength + "/5" : "")
                .append('\n');
        JSONObject root = parse(json);
        String exportedAt = root.optString("exportedAt", "");
        if (!exportedAt.isEmpty()) text.append("· 导出于 ").append(exportedAt).append('\n');
        return text.toString();
    }

    /** Imports settings, preserving the local key when the file does not contain one. */
    public static Imported apply(Context context, String json)
            throws JSONException, java.security.GeneralSecurityException {
        JSONObject root = parse(json);
        Imported imported = new Imported(root);
        if (imported.hasAi) {
            AiSettingsStore store = new AiSettingsStore(context);
            AiSettings existing = store.load();
            if (imported.hasApiKey && imported.apiKey.isEmpty())
                throw new JSONException("API 密钥不能为空；如需保留本机密钥，请重新导出且不包含密钥");
            if (imported.hasApiKey || existing != null) {
                String key = imported.hasApiKey ? imported.apiKey : existing.apiKey;
                AiSettings validated = new AiSettings(imported.baseUrl, imported.model, key);
                store.save(validated.baseUrl, validated.model, validated.apiKey);
                clearDraft(context);
            } else {
                // A keyless file is still useful on a new device: keep its fields until the user
                // enters a key and presses Save in the settings form.
                context.getSharedPreferences(DRAFT, Context.MODE_PRIVATE).edit()
                        .putString("baseUrl", imported.baseUrl)
                        .putString("model", imported.model).apply();
            }
        }
        if (imported.hasAppearance) {
            ThemeStore.applyAppearance(context, imported.mode, imported.color,
                    imported.hasAcrylic ? imported.acrylicEnabled : null,
                    imported.hasGaussianBlur ? imported.gaussianBlur : null,
                    imported.hasSurfaceMix ? imported.surfaceMix : null,
                    imported.hasBlurStrength ? imported.blurStrength : null,
                    imported.hasCustomColor ? imported.customColor : null);
        }
        return imported;
    }

    public static Imported pending(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(DRAFT, Context.MODE_PRIVATE);
        String baseUrl = preferences.getString("baseUrl", null);
        String model = preferences.getString("model", null);
        return baseUrl == null && model == null ? null
                : new Imported(baseUrl == null ? "" : baseUrl, model == null ? "" : model);
    }

    public static void clearDraft(Context context) {
        context.getSharedPreferences(DRAFT, Context.MODE_PRIVATE).edit().clear().apply();
    }

    private static JSONObject parse(String json) throws JSONException {
        if (json == null || json.trim().isEmpty()) throw new JSONException("文件是空的");
        JSONObject root = new JSONObject(json);
        if (!(root.opt("app") instanceof String) || !APP.equals(root.optString("app")))
            throw new JSONException("这不是拾时的配置文件");
        Object format = root.opt("format");
        if (!(format instanceof Number)
                || ((Number) format).doubleValue() != FORMAT)
            throw new JSONException("不支持的配置文件版本");
        JSONObject ai = root.optJSONObject("ai");
        if (ai != null) {
            if (!(ai.opt("baseUrl") instanceof String) || !(ai.opt("model") instanceof String))
                throw new JSONException("解析服务配置缺少基础地址或模型名称");
            if (ai.has("apiKey") && !(ai.opt("apiKey") instanceof String))
                throw new JSONException("API 密钥格式无效");
            String baseUrl = ai.optString("baseUrl", "").trim();
            String model = ai.optString("model", "").trim();
            String key = ai.optString("apiKey", "");
            if (ai.has("apiKey") && (baseUrl.isEmpty() || model.isEmpty() || key.trim().isEmpty()))
                throw new JSONException("包含 API 密钥的配置必须同时包含有效地址和模型");
            if (!baseUrl.isEmpty() && !model.isEmpty()) {
                try {
                    new AiSettings(baseUrl, model, key.isEmpty() ? "import-validation" : key);
                } catch (IllegalArgumentException exception) {
                    throw new JSONException("解析服务配置无效：" + exception.getMessage());
                }
            }
        } else if (root.has("ai") && !root.isNull("ai")) {
            throw new JSONException("解析服务配置格式无效");
        }
        JSONObject appearance = root.optJSONObject("appearance");
        if (appearance != null) {
            if (!(appearance.opt("mode") instanceof String)
                    || !(appearance.opt("color") instanceof String))
                throw new JSONException("外观配置缺少显示模式或主题配色");
            String mode = appearance.optString("mode", "");
            String color = appearance.optString("color", "");
            if (!(ThemeStore.SYSTEM.equals(mode) || ThemeStore.LIGHT.equals(mode)
                    || ThemeStore.DARK.equals(mode))) throw new JSONException("显示模式无效");
            if (!ThemeStore.supportedColor(color)) throw new JSONException("主题配色无效");
            if (appearance.has("customColor")) {
                Object custom = appearance.opt("customColor");
                if (!(custom instanceof String) || !ThemeStore.isHexColor((String) custom))
                    throw new JSONException("自定义主题色无效");
            } else if (ThemeStore.CUSTOM.equals(color)) {
                throw new JSONException("自定义主题色缺少颜色值");
            }
            if (appearance.has("acrylicEnabled")
                    && !(appearance.opt("acrylicEnabled") instanceof Boolean))
                throw new JSONException("毛玻璃设置格式无效");
            if (appearance.has("gaussianBlur")
                    && !(appearance.opt("gaussianBlur") instanceof Boolean))
                throw new JSONException("模糊类型格式无效");
            if (appearance.has("surfaceMix")) {
                Object mix = appearance.opt("surfaceMix");
                if (!(mix instanceof Number) || ((Number) mix).intValue() < 10
                        || ((Number) mix).intValue() > 70)
                    throw new JSONException("面板纯色浓度无效");
            }
            if (appearance.has("blurStrength")) {
                Object strength = appearance.opt("blurStrength");
                if (!(strength instanceof Number) || ((Number) strength).intValue() < 1
                        || ((Number) strength).intValue() > 5)
                    throw new JSONException("模糊强度无效");
            }
        } else if (root.has("appearance") && !root.isNull("appearance")) {
            throw new JSONException("外观配置格式无效");
        }
        return root;
    }

    private static String empty(String value) {
        return value == null || value.isEmpty() ? "（空）" : value;
    }
}
