package com.donglan.chrona.classroom;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Merge observations without erasing older chunks or reassigning downloaded image files. */
public final class ClassroomMaterialUpdates {
    private ClassroomMaterialUpdates() { }

    public static JSONObject merge(JSONObject previous, JSONObject observation) throws Exception {
        Map<String, JSONObject> segments = new LinkedHashMap<>();
        Map<String, JSONObject> images = new LinkedHashMap<>();
        int nextIndex = 0;
        for (JSONObject input : new JSONObject[]{previous, observation}) {
            if (input == null) continue;
            JSONArray rows = input.getJSONArray("segments");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                double start = row.getDouble("start"), end = row.getDouble("end");
                if (!Double.isFinite(start) || !Double.isFinite(end) || start < 0 || end < start)
                    throw new IllegalArgumentException("字幕时间无效");
                // A correction to the same timed segment replaces its text; repeated polls do not append it.
                segments.put(start + "/" + end, new JSONObject(row.toString()));
            }
            rows = input.getJSONArray("images");
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                String url = row.getString("url");
                if (images.containsKey(url)) continue;
                JSONObject image = new JSONObject(row.toString());
                // Existing non-monitor downloads used their manifest position as the raw-file index.
                int index = input == previous ? row.optInt("cache_index", i) : nextIndex;
                if (index < 0) throw new IllegalArgumentException("截图缓存编号无效");
                for (JSONObject existing : images.values())
                    if (existing.getInt("cache_index") == index) throw new IllegalArgumentException("截图缓存编号重复");
                image.put("cache_index", index);
                nextIndex = Math.max(nextIndex, Math.addExact(index, 1));
                images.put(url, image);
            }
        }
        List<JSONObject> ordered = new ArrayList<>(segments.values());
        ordered.sort(Comparator.comparingDouble((JSONObject row) -> row.optDouble("start"))
                .thenComparingDouble(row -> row.optDouble("end")));
        JSONArray transcript = new JSONArray(), slides = new JSONArray();
        for (JSONObject row : ordered) transcript.put(row);
        for (JSONObject row : images.values()) slides.put(row);
        return new JSONObject().put("segments", transcript).put("images", slides)
                .put("raw_images", slides.length()).put("monitoring", true);
    }
}
