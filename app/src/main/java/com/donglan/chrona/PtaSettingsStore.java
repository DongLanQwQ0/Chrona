package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Device-only PTA credentials. Never part of portable tasks, logs or backups. */
final class PtaSettingsStore {
    private static final String ALIAS = "chrona_pta_session";
    final SharedPreferences prefs;
    PtaSettingsStore(Context context) { prefs = context.getSharedPreferences("chrona_pta", Context.MODE_PRIVATE); }
    boolean configured() { return prefs.contains("session"); }
    boolean enabled() { return configured() && prefs.getBoolean("enabled", false); }
    String account() { return prefs.getString("account", ""); }
    String nickname() { return prefs.getString("nickname", ""); }
    String status() { return prefs.getString("status", "尚未同步"); }
    boolean autoPlan() { return prefs.getBoolean("auto_plan", false); }
    Set<String> excluded() { return prefs.getStringSet("excluded:" + account(), Set.of()); }
    String course(String setId) { return prefs.getString("course:" + account() + ":" + setId, ""); }
    private SecretKey key(boolean create) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore"); store.load(null);
        if (!store.containsAlias(ALIAS) && create) {
            KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        if (!store.containsAlias(ALIAS)) throw new java.io.IOException("请重新登录 PTA");
        return (SecretKey) store.getKey(ALIAS, null);
    }
    String session() throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(false), new GCMParameterSpec(128, Base64.decode(prefs.getString("iv", ""), Base64.NO_WRAP)));
        return new String(cipher.doFinal(Base64.decode(prefs.getString("session", ""), Base64.NO_WRAP)), StandardCharsets.UTF_8);
    }
    void save(String session, String userId, String nickname) throws Exception {
        if (session == null || !session.matches("[A-Za-z0-9._~%+\\-=/]+") || userId == null || userId.isBlank())
            throw new java.io.IOException("PTA 登录尚未完成");
        String salt = prefs.getString("salt", UUID.randomUUID().toString());
        String identity = "identity:" + UUID.nameUUIDFromBytes((salt + "\0" + userId).getBytes(StandardCharsets.UTF_8));
        String account = prefs.getString(identity, "pta:" + UUID.randomUUID());
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding"); cipher.init(Cipher.ENCRYPT_MODE, key(true));
        if (!prefs.edit().putString("session", Base64.encodeToString(cipher.doFinal(session.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP))
                .putString("iv", Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)).putString("salt", salt)
                .putString(identity, account).putString("account", account).putString("nickname", nickname)
                .putBoolean("enabled", true).putBoolean("auto_plan",account.equals(account())&&autoPlan()).putLong("attempt", 0).putString("status", "已登录").commit())
            throw new java.io.IOException("无法保存 PTA 登录");
    }
    void clear() {
        if (!prefs.edit().remove("session").remove("iv").remove("account").remove("nickname")
                .remove("enabled").remove("auto_plan").remove("attempt").remove("status").commit())
            throw new IllegalStateException("无法退出 PTA");
    }
}
