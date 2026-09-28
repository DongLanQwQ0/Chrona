package com.donglan.chrona;

import android.content.Context;
import com.donglan.chrona.data.TaskFileAttachment;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

/** One local, private capture draft. Attachment bytes stay in their existing stores. */
final class CaptureDraftStore {
    static final class Draft {
        String text = "";
        final List<String> images = new ArrayList<>();
        final List<TaskFileAttachment> files = new ArrayList<>();
    }

    private final android.content.SharedPreferences prefs;
    CaptureDraftStore(Context context) {
        this(context.getApplicationContext().getSharedPreferences("capture_draft", 0));
    }
    CaptureDraftStore(android.content.SharedPreferences prefs) { this.prefs = prefs; }
    boolean hasDraft() { return prefs.getString("content", null) != null; }

    Draft load() {
        Draft result = new Draft();
        try {
            JSONObject value = new JSONObject(prefs.getString("content", "{}"));
            result.text = value.optString("text", "");
            JSONArray images = value.optJSONArray("images");
            if (images != null) for (int i = 0; i < images.length(); i++)
                result.images.add(images.getString(i));
            JSONArray files = value.optJSONArray("files");
            if (files != null) for (int i = 0; i < files.length(); i++) {
                JSONObject file = files.getJSONObject(i);
                result.files.add(new TaskFileAttachment(0, 0, file.getString("stored"),
                        file.getString("name"), file.getString("type"), file.getLong("size")));
            }
        } catch (org.json.JSONException ignored) {
            // A damaged draft must not prevent opening capture or affect saved tasks.
        }
        return result;
    }

    void save(String text, List<String> images, List<TaskFileAttachment> files) {
        try {
            JSONObject value = new JSONObject();
            value.put("text", text);
            value.put("images", new JSONArray(images));
            JSONArray attachments = new JSONArray();
            for (TaskFileAttachment file : files) {
                JSONObject item = new JSONObject();
                item.put("stored", file.storedName);
                item.put("name", file.displayName);
                item.put("type", file.mimeType);
                item.put("size", file.sizeBytes);
                attachments.put(item);
            }
            value.put("files", attachments);
            prefs.edit().putString("content", value.toString()).apply();
        } catch (org.json.JSONException exception) {
            throw new IllegalStateException("无法保存草稿", exception);
        }
    }

    void clear() { prefs.edit().remove("content").apply(); }
}
