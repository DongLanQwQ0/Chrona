package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.Arrays;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Portable opaque account binding and read snapshots; never exports credentials or sessions. */
final class SchoolDataBackup {
    static JSONObject export(Context context) throws Exception {
        ZjuSettingsStore settings = new ZjuSettingsStore(context);
        JSONObject bindings = new JSONObject();
        for (String key : settings.prefs.getAll().keySet())
            if (key.equals("identity_salt") || key.startsWith("identity:"))
                bindings.put(key, settings.prefs.getString(key, ""));
        JSONObject result = new JSONObject().put("bindings", bindings)
                .put("homework", settings.prefs.getBoolean("homework", false))
                .put("timetable", settings.prefs.getBoolean("timetable", false));
        JSONObject cache = new ZjuTimetableStore(context).snapshot();
        result.put("timetableCache", cache == null ? JSONObject.NULL : cache);
        validate(result);
        return result;
    }

    static void validate(JSONObject data) throws JSONException {
        JSONObject bindings = data.getJSONObject("bindings");
        for (java.util.Iterator<String> keys = bindings.keys(); keys.hasNext();) {
            String key = keys.next();
            if (!key.equals("identity_salt") && !key.matches("identity:[0-9a-f-]{36}"))
                throw new JSONException("学校备份包含无效绑定字段");
            uuid(bindings.getString(key));
        }
        if (bindings.length() > 0 && !bindings.has("identity_salt")) throw new JSONException("学校账号绑定不完整");
        if (!(data.get("homework") instanceof Boolean) || !(data.get("timetable") instanceof Boolean))
            throw new JSONException("学校同步选项无效");
        if (!data.isNull("timetableCache")) {
            JSONObject cache = data.getJSONObject("timetableCache"); uuid(cache.getString("account"));
            JSONArray rows = cache.getJSONArray("lessons");
            java.util.Set<String> keys = new java.util.HashSet<>();
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                if (!keys.add(row.getString("key")) || row.getLong("end") <= row.getLong("start"))
                    throw new JSONException("课表备份包含重复或无效课程");
                row.getString("title"); row.getString("location"); row.getString("term");
            }
        }
    }

    static void apply(Context context, JSONObject data) throws Exception {
        validate(data);
        ZjuSettingsStore settings = new ZjuSettingsStore(context);
        JSONObject bindings = data.getJSONObject("bindings");
        SharedPreferences.Editor edit = settings.prefs.edit();
        for (String key : settings.prefs.getAll().keySet())
            if (key.equals("identity_salt") || key.startsWith("identity:")) edit.remove(key);
        for (java.util.Iterator<String> keys = bindings.keys(); keys.hasNext();) {
            String key = keys.next(); edit.putString(key, bindings.getString(key));
        }
        // Keep this device's encrypted credentials, but bind the same user to the restored capture.
        if (settings.configured()) {
            String[] credentials = settings.credentials();
            try {
                String salt = bindings.optString("identity_salt", UUID.randomUUID().toString());
                String identity = ZjuSettingsStore.identityKey(salt, credentials[0]);
                String account = bindings.optString(identity, settings.account());
                edit.putString("identity_salt", salt).putString(identity, account).putString("account", account);
            } finally { Arrays.fill(credentials, ""); }
        }
        edit.putBoolean("enabled", false).putBoolean("homework", data.getBoolean("homework"))
                .putBoolean("timetable", data.getBoolean("timetable"));
        if (!edit.commit()) throw new java.io.IOException("无法恢复学校账号绑定");
        new ZjuTimetableStore(context).restore(data.isNull("timetableCache") ? null : data.getJSONObject("timetableCache"));
    }

    private static void uuid(String value) throws JSONException {
        try { if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException(); }
        catch (IllegalArgumentException failure) { throw new JSONException("学校账号绑定标识无效"); }
    }
    private SchoolDataBackup() { }
}
