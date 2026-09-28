package com.donglan.chrona.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import com.donglan.chrona.debug.DiagLog;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Saves configuration locally; only an AES/GCM ciphertext of the API key is persisted. */
public final class AiSettingsStore {
    private static final String PREFERENCES_NAME = "chrona_ai_settings";
    private static final String KEY_ALIAS = "chrona_ai_api_key";
    private static final String BASE_URL = "base_url";
    private static final String MODEL = "model";
    private static final String REASONING_EFFORT = "reasoning_effort";
    private static final String ENCRYPTED_KEY = "encrypted_api_key";
    private static final String KEY_IV = "api_key_iv";
    private static final String IMAGE_REJECTED_FOR = "image_rejected_for";

    private final Context context;
    private final SharedPreferences preferences;

    public AiSettingsStore(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    /** Replaces the current settings. A failed encryption leaves the previous settings intact. */
    public void save(String baseUrl, String model, String apiKey) throws GeneralSecurityException {
        save(baseUrl, model, apiKey, "auto");
    }

    public void save(String baseUrl, String model, String apiKey, String reasoningEffort)
            throws GeneralSecurityException {
        AiSettings settings = new AiSettings(baseUrl, model, apiKey, reasoningEffort);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(settings.apiKey.getBytes(StandardCharsets.UTF_8));
        boolean saved = preferences.edit()
                .putString(BASE_URL, settings.baseUrl)
                .putString(MODEL, settings.model)
                .putString(REASONING_EFFORT, settings.reasoningEffort)
                .putString(ENCRYPTED_KEY, Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString(KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .commit();
        if (!saved) {
            throw new IllegalStateException("Could not save AI settings");
        }
        DiagLog.add(context, "settings saved base=" + settings.baseUrl + " model="
                + settings.model);
    }

    /** Returns null if no settings were saved; propagates decryption failures. */
    public AiSettings load() throws GeneralSecurityException {
        String baseUrl = preferences.getString(BASE_URL, null);
        String model = preferences.getString(MODEL, null);
        String encrypted = preferences.getString(ENCRYPTED_KEY, null);
        String iv = preferences.getString(KEY_IV, null);
        if (baseUrl == null && model == null && encrypted == null && iv == null) {
            return null;
        }
        if (baseUrl == null || model == null || encrypted == null || iv == null) {
            throw new GeneralSecurityException("Incomplete AI settings");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, getExistingKey(),
                    new GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)));
            byte[] plain = cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP));
            return new AiSettings(baseUrl, model, new String(plain, StandardCharsets.UTF_8),
                    preferences.getString(REASONING_EFFORT, "auto"));
        } catch (IllegalArgumentException e) {
            throw new GeneralSecurityException("Invalid saved AI settings", e);
        }
    }

    public void clear() {
        if (!preferences.edit().clear().commit()) {
            throw new IllegalStateException("Could not clear AI settings");
        }
    }

    /**
     * True when this exact endpoint and model rejected a request that carried an image, so the
     * image entry stays disabled until the model changes or the user clears the flag.
     */
    public boolean isImageUnsupported(AiSettings settings) {
        return settings != null
                && (isKnownImageUnsupported(settings)
                    || identity(settings).equals(preferences.getString(IMAGE_REJECTED_FOR, null)));
    }

    /** The official DeepSeek Pro endpoint currently has no vision input. */
    public boolean isKnownImageUnsupported(AiSettings settings) {
        if (settings == null || !"deepseek-v4-pro".equals(settings.model)) return false;
        return "https://api.deepseek.com".equalsIgnoreCase(settings.baseUrl)
                || "https://api.deepseek.com/v1".equalsIgnoreCase(settings.baseUrl);
    }

    /** Remembers that this endpoint and model cannot take images. */
    public void markImageUnsupported(AiSettings settings) {
        if (settings == null) throw new IllegalArgumentException("settings are required");
        preferences.edit().putString(IMAGE_REJECTED_FOR, identity(settings)).apply();
        DiagLog.add(context, "image entry disabled for model=" + settings.model);
    }

    /** Clears the learned rejection so the next request tries the image again. */
    public void clearImageSupport() {
        preferences.edit().remove(IMAGE_REJECTED_FOR).apply();
    }

    private static String identity(AiSettings settings) {
        return settings.baseUrl + "\n" + settings.model;
    }

    private static SecretKey getExistingKey() throws GeneralSecurityException {
        KeyStore keyStore = openKeyStore();
        SecretKey key = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (key == null) {
            throw new GeneralSecurityException("AI encryption key is unavailable");
        }
        return key;
    }

    private static SecretKey getOrCreateKey() throws GeneralSecurityException {
        KeyStore keyStore = openKeyStore();
        SecretKey existing = (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        if (existing != null) {
            return existing;
        }
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build());
        return generator.generateKey();
    }

    private static KeyStore openKeyStore() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        try {
            keyStore.load(null);
        } catch (IOException e) {
            throw new GeneralSecurityException("Could not load Android Keystore", e);
        }
        return keyStore;
    }
}
