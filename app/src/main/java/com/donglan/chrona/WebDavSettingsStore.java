package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-only credentials. Neither these preferences nor the key enter data backups. */
final class WebDavSettingsStore {
    static final String DEFAULT_URL = "https://dav.jianguoyun.com/dav/";
    private static final String ALIAS = "chrona_webdav_password";
    final SharedPreferences prefs;
    WebDavSettingsStore(Context c) { prefs = c.getSharedPreferences("chrona_webdav", Context.MODE_PRIVATE); }
    String url() { return prefs.getString("url", DEFAULT_URL); }
    String user() { return prefs.getString("user", ""); }
    boolean configured() { return !user().isEmpty() && prefs.contains("password"); }
    boolean automatic() { return prefs.getBoolean("automatic", false); }
    void automatic(boolean enabled) { prefs.edit().putBoolean("automatic", enabled).apply(); }
    String status() { return prefs.getString("status", "尚未同步"); }
    void status(String s) { prefs.edit().putString("status", s).apply(); }
    private SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS) && create) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        if (!store.containsAlias(ALIAS)) throw new java.io.IOException("请重新输入应用密码");
        return (SecretKey) store.getKey(ALIAS, null);
    }
    String password() throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128,
                Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(prefs.getString("password", ""), Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    void save(String url, String user, String password) throws Exception {
        java.net.URI uri = new java.net.URI(url.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) throw new java.io.IOException("请输入有效的 HTTPS WebDAV 地址");
        if (user.trim().isEmpty() || password.isEmpty()) throw new java.io.IOException("请输入账号和应用密码");
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(true));
        byte[] encrypted = cipher.doFinal(password.getBytes(StandardCharsets.UTF_8));
        if (!prefs.edit().putString("url", url.trim()).putString("user", user.trim())
                .putString("password", Base64.encodeToString(encrypted, Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).commit())
            throw new java.io.IOException("无法保存同步设置");
    }
}
