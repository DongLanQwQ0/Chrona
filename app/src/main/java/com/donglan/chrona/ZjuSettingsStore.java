package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import org.json.JSONObject;

/** Device-only school identity and encrypted credentials. Never exported by data sync. */
final class ZjuSettingsStore {
    private static final String ALIAS = "chrona_zju_credentials";
    final SharedPreferences prefs;
    ZjuSettingsStore(Context context) { prefs=context.getSharedPreferences("chrona_zju",Context.MODE_PRIVATE); }
    boolean configured(){return prefs.contains("credentials");}
    boolean enabled(){return prefs.getBoolean("enabled",false);}
    boolean timetable(){return enabled() && prefs.getBoolean("timetable",false);}
    String account(){return prefs.getString("account","");}
    String status(){return prefs.getString("status","尚未同步");}
    void status(String status){prefs.edit().putString("status",status).apply();}
    long lastSuccess(){return prefs.getLong("last_success",0);}
    private SecretKey key(boolean create)throws Exception{
        KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
        if(!store.containsAlias(ALIAS)&&create){
            KeyGenerator generator=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
            generator.init(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());
            generator.generateKey();
        }
        if(!store.containsAlias(ALIAS))throw new java.io.IOException("请重新填写浙大账号");
        return (SecretKey)store.getKey(ALIAS,null);
    }
    String[] credentials()throws Exception{
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,key(false),new GCMParameterSpec(128,Base64.decode(prefs.getString("iv",""),Base64.NO_WRAP)));
        JSONObject value=new JSONObject(new String(cipher.doFinal(Base64.decode(prefs.getString("credentials",""),Base64.NO_WRAP)),StandardCharsets.UTF_8));
        return new String[]{value.getString("user"),value.getString("password")};
    }
    void save(String user,String password)throws Exception{
        user=user.trim();
        if(user.isEmpty())throw new java.io.IOException("请填写学号");
        String[] previous=password.isEmpty()&&configured()?credentials():null;
        if(password.isEmpty()&&previous!=null&&previous[0].equals(user))password=previous[1];
        if(password.isEmpty())throw new java.io.IOException("请填写密码");
        String salt=prefs.getString("identity_salt",UUID.randomUUID().toString());
        String identity=identityKey(salt,user);
        String account=prefs.getString(identity,UUID.randomUUID().toString());
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.ENCRYPT_MODE,key(true));
        byte[] encrypted=cipher.doFinal(new JSONObject().put("user",user).put("password",password).toString().getBytes(StandardCharsets.UTF_8));
        if(!prefs.edit().putString("credentials",Base64.encodeToString(encrypted,Base64.NO_WRAP))
                .putString("iv",Base64.encodeToString(cipher.getIV(),Base64.NO_WRAP)).putString("account",account)
                .putString("identity_salt",salt).putString(identity,account).commit())
            throw new java.io.IOException("无法保存账号");
        if(previous!=null)java.util.Arrays.fill(previous,"");
    }
    void options(boolean enabled,boolean timetable){prefs.edit().putBoolean("enabled",enabled).putBoolean("timetable",timetable).apply();}
    static String identityKey(String salt,String user){return "identity:"+UUID.nameUUIDFromBytes((salt+"\0"+user.trim()).getBytes(StandardCharsets.UTF_8));}
    void clear()throws Exception{
        // Keep opaque local identity mappings so signing back into the same account
        // reuses its capture and deletion markers. No username/password is retained here.
        SharedPreferences.Editor edit=prefs.edit();
        edit.remove("credentials").remove("iv").remove("account").remove("enabled").remove("timetable")
                .remove("homework").remove("last_success").remove("status");
        if(!edit.commit())throw new java.io.IOException("无法清除登录信息");
    }
}
