package com.donglan.chrona.sync;

import org.json.JSONObject;
import java.util.*;

/** Grow-only candidate removals. UUID edges strictly decrease, so chains cannot cycle. */
public final class MergeLedger {
    private final Map<String, JSONObject> records = new TreeMap<>();
    public static String candidateId(String value) {
        if (!UUID.fromString(value).toString().equals(value))
            throw new IllegalArgumentException("合并日程身份无效");
        return value;
    }
    public static JSONObject preferred(JSONObject first, JSONObject second) {
        return SyncState.preferredMerge(first,second);
    }
    public void add(JSONObject input) throws Exception {
        JSONObject value=TaskCodec.validate(input);
        if (!"merge".equals(value.getString("kind"))) throw new IllegalArgumentException("合并记录类型无效");
        String source=value.getString("sourceCandidate");
        records.put(source,preferred(records.get(source),value));
    }
    public boolean removed(String candidate) { return records.containsKey(candidate); }
    public String target(String candidate) {
        String current=candidate;
        while (records.containsKey(current)) current=records.get(current).optString("targetCandidate");
        return current;
    }
    public List<JSONObject> records() { return new ArrayList<>(records.values()); }
    public List<JSONObject> sources(String candidate) {
        List<JSONObject> result=new ArrayList<>();
        for(String source:records.keySet()) if(target(source).equals(candidate)) result.add(records.get(source));
        return result;
    }
}
