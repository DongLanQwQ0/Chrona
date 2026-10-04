package com.donglan.chrona.sync;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.security.MessageDigest;
import java.util.*;

/** A multi-value register per record. Deletions participate in causality. */
public final class SyncState {
    public final String deviceId;
    public static final int MAX_RECORDS = 10000, MAX_SIBLINGS = 64, MAX_DEVICES = 128;
    private final Map<String,List<Version>> data = new TreeMap<>();
    public static final class Version {
        public final Map<String,Long> clock;
        public final boolean deleted;
        public final JSONObject payload;
        private Version(Map<String,Long> clock, boolean deleted, JSONObject payload) {
            this.clock = Collections.unmodifiableMap(new TreeMap<>(clock));
            this.deleted = deleted;
            try { this.payload = payload == null ? null : new JSONObject(payload.toString()); }
            catch (JSONException e) { throw new IllegalArgumentException("Invalid sync payload",e); }
        }
    }
    public SyncState(String deviceId) { validId(deviceId); this.deviceId = deviceId; }
    public String deviceId() { return deviceId; }
    public static void validId(String id) {
        if (id == null || !id.matches("[a-zA-Z0-9_-]{1,100}")) throw new IllegalArgumentException("Invalid sync identifier");
    }
    public void put(String id, JSONObject payload) {
        if (payload == null) throw new IllegalArgumentException("Missing payload");
        update(id, false, payload);
    }
    public void delete(String id) { update(id, true, null); }
    /** Local edits branch from the version actually applied on this device, not pending remote versions. */
    public Version branch(String id, JSONObject payload, Map<String,Long> appliedClock) {
        validId(id);
        Map<String,Long> clock = new TreeMap<>();
        if (appliedClock != null) for (Map.Entry<String,Long> entry : appliedClock.entrySet()) {
            validId(entry.getKey());
            if (entry.getValue() == null || entry.getValue() <= 0) throw new IllegalArgumentException("Invalid applied clock");
            clock.put(entry.getKey(), entry.getValue());
        }
        long own = clock.getOrDefault(deviceId, 0L);
        for (Version existing : data.getOrDefault(id, Collections.emptyList()))
            own = Math.max(own, existing.clock.getOrDefault(deviceId, 0L));
        clock.put(deviceId, Math.addExact(own, 1L));
        if (clock.size() > MAX_DEVICES) throw new IllegalArgumentException("Too many sync devices");
        Version version = new Version(clock, payload == null, payload);
        SyncState branch = new SyncState(deviceId);
        branch.data.put(id, new ArrayList<>(Collections.singletonList(version)));
        merge(branch);
        return new Version(version.clock, version.deleted, version.payload);
    }
    private void update(String id, boolean deleted, JSONObject payload) {
        validId(id);
        if(id.startsWith("merge_")&&deleted)throw new IllegalArgumentException("合并标记不可删除");
        if(id.startsWith("merge_"))validateMergeIdentity(id,payload);
        List<Version> existing = data.getOrDefault(id, Collections.emptyList());
        if(id.startsWith("merge_"))for(Version version:existing)payload=preferredMerge(payload,version.payload);
        if (existing.size() == 1 && existing.get(0).deleted == deleted
                && canonical(existing.get(0).payload).equals(canonical(payload))) return;
        if (!data.containsKey(id) && data.size() >= MAX_RECORDS) throw new IllegalArgumentException("Too many sync records");
        Map<String,Long> clock = new TreeMap<>();
        for (Version v : data.getOrDefault(id, Collections.emptyList()))
            v.clock.forEach((k,n) -> clock.merge(k,n,Math::max));
        clock.put(deviceId, Math.addExact(clock.getOrDefault(deviceId,0L),1L));
        if (clock.size() > MAX_DEVICES) throw new IllegalArgumentException("Too many sync devices");
        data.put(id, new ArrayList<>(Collections.singletonList(new Version(clock,deleted,payload))));
    }
    public void resolve(String id, Version chosen) {
        validId(id);
        if (chosen == null || data.getOrDefault(id, Collections.emptyList()).stream().noneMatch(v -> same(v,chosen)))
            throw new IllegalArgumentException("Resolution must choose an existing version");
        update(id, chosen.deleted, chosen.payload);
    }
    public Map<String,List<Version>> records() {
        Map<String,List<Version>> result = new TreeMap<>();
        data.forEach((id,vs) -> {
            List<Version> copies = new ArrayList<>();
            for (Version v : vs) copies.add(new Version(v.clock,v.deleted,v.payload));
            result.put(id,Collections.unmodifiableList(copies));
        });
        return Collections.unmodifiableMap(result);
    }
    public SyncState copy() { return fromJson(toJson()); }
    public void merge(SyncState other) {
        // Validate into a temporary map so malformed remote input cannot partially mutate us.
        Map<String,List<Version>> merged = new TreeMap<>();
        Set<String> ids = new TreeSet<>(data.keySet()); ids.addAll(other.data.keySet());
        if (ids.size() > MAX_RECORDS) throw new IllegalArgumentException("Too many sync records");
        for (String id : ids) {
            List<Version> union = new ArrayList<>(data.getOrDefault(id, Collections.emptyList()));
            union.addAll(other.data.getOrDefault(id, Collections.emptyList()));
            if(id.startsWith("merge_")) {
                JSONObject chosen=null;Map<String,Long> clock=new TreeMap<>();
                for(Version version:union){
                    if(version.deleted)throw new IllegalArgumentException("合并标记不可删除");
                    validateMergeIdentity(id,version.payload);
                    chosen=preferredMerge(chosen,version.payload);
                    version.clock.forEach((device,count)->clock.merge(device,count,Math::max));
                }
                if(clock.size()>MAX_DEVICES)throw new IllegalArgumentException("Too many sync devices");
                merged.put(id,new ArrayList<>(Collections.singletonList(new Version(clock,false,chosen))));
                continue;
            }
            for (Version a : union) for (Version b : union)
                if (a.clock.equals(b.clock) && !same(a,b)) throw new IllegalArgumentException("Inconsistent sync version");
            List<Version> keep = new ArrayList<>();
            for (Version a : union) {
                boolean dominated = false;
                for (Version b : union) if (dominates(b.clock,a.clock)) { dominated = true; break; }
                if (!dominated && keep.stream().noneMatch(v -> same(v,a))) keep.add(new Version(a.clock,a.deleted,a.payload));
            }
            keep.sort(Comparator.comparing(v -> canonical(versionJson(v))));
            if (keep.size() > MAX_SIBLINGS) throw new IllegalArgumentException("Too many concurrent sync versions");
            merged.put(id,keep);
        }
        data.clear(); data.putAll(merged);
    }
    private static void validateMergeIdentity(String id,JSONObject payload) {
        String source=payload==null?"":payload.optString("sourceCandidate"),target=payload==null?"":payload.optString("targetCandidate");
        if(payload==null||!"merge".equals(payload.optString("kind"))||!id.equals("merge_"+source)
                ||!UUID.fromString(source).toString().equals(source)||!UUID.fromString(target).toString().equals(target)||source.compareTo(target)<=0)
            throw new IllegalArgumentException("合并记录身份无效");
    }
    public static JSONObject preferredMerge(JSONObject first,JSONObject second) {
        if(first==null)return second;if(second==null)return first;
        int order=first.optString("targetCandidate").compareTo(second.optString("targetCandidate"));
        return order<0||(order==0&&canonical(first).compareTo(canonical(second))<=0)?first:second;
    }
    private static boolean dominates(Map<String,Long> a, Map<String,Long> b) {
        if (a.equals(b)) return false;
        for (Map.Entry<String,Long> e : b.entrySet()) if (a.getOrDefault(e.getKey(),0L) < e.getValue()) return false;
        return true;
    }
    private static boolean same(Version a, Version b) {
        return a.clock.equals(b.clock) && a.deleted == b.deleted && canonical(a.payload).equals(canonical(b.payload));
    }
    private static JSONObject versionJson(Version v) {
        try { return new JSONObject().put("clock",new JSONObject(v.clock)).put("deleted",v.deleted)
                .put("payload",v.payload == null ? JSONObject.NULL : new JSONObject(v.payload.toString()));
        } catch (JSONException e) { throw new IllegalArgumentException("Invalid sync version",e); }
    }
    public JSONObject toJson() {
        try {
        JSONObject records = new JSONObject();
        for (Map.Entry<String,List<Version>> entry : data.entrySet()) {
            JSONArray arr = new JSONArray(); for (Version v : entry.getValue()) arr.put(versionJson(v)); records.put(entry.getKey(),arr);
        }
        return new JSONObject().put("format",1).put("deviceId",deviceId).put("records",records);
        } catch (JSONException e) { throw new IllegalArgumentException("Invalid sync state",e); }
    }
    public static SyncState fromJson(JSONObject json) {
        try {
        if (!Integer.valueOf(1).equals(json.get("format"))) throw new IllegalArgumentException("Unsupported sync format");
        SyncState result = new SyncState(json.getString("deviceId"));
        JSONObject records = json.getJSONObject("records");
        if (records.length() > MAX_RECORDS) throw new IllegalArgumentException("Too many sync records");
        for (String id : keys(records)) {
            validId(id); JSONArray arr = records.getJSONArray(id);
            if (arr.length() == 0 || arr.length() > MAX_SIBLINGS) throw new IllegalArgumentException("Invalid sync version count");
            List<Version> vs = new ArrayList<>();
            for (int i=0;i<arr.length();i++) {
                JSONObject obj = arr.getJSONObject(i), c = obj.getJSONObject("clock");
                Map<String,Long> clock = new TreeMap<>();
                if (c.length() == 0 || c.length() > MAX_DEVICES) throw new IllegalArgumentException("Invalid version clock size");
                for (String key : keys(c)) {
                    validId(key); Object raw = c.get(key);
                    if (!(raw instanceof Number) || !raw.toString().matches("[1-9][0-9]*")) throw new IllegalArgumentException("Invalid version clock");
                    long n = Long.parseLong(raw.toString()); clock.put(key,n);
                }
                Object flag = obj.get("deleted");
                if (!(flag instanceof Boolean)) throw new IllegalArgumentException("Invalid deletion flag");
                boolean deleted = (Boolean)flag;
                if (deleted && !obj.isNull("payload")) throw new IllegalArgumentException("Tombstone has payload");
                vs.add(new Version(clock,deleted,deleted ? null : obj.getJSONObject("payload")));
            }
            result.data.put(id,vs);
        }
        result.merge(new SyncState(result.deviceId));
        return result;
        } catch (JSONException e) { throw new IllegalArgumentException("Invalid sync state",e); }
    }
    public static String canonical(Object value) {
        try {
        if (value == null || value == JSONObject.NULL) return "null";
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject)value; StringJoiner out = new StringJoiner(",","{","}");
            for (String key : keys(object)) out.add(JSONObject.quote(key)+":"+canonical(object.get(key)));
            return out.toString();
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray)value; StringJoiner out = new StringJoiner(",","[","]");
            for (int i=0;i<arr.length();i++) out.add(canonical(arr.get(i))); return out.toString();
        }
        if (value instanceof String) return JSONObject.quote((String)value);
        if (value instanceof Number) return JSONObject.numberToString((Number)value);
        if (value instanceof Boolean) return value.toString();
        throw new IllegalArgumentException("Unsupported JSON value");
        } catch (JSONException e) { throw new IllegalArgumentException("Invalid JSON value",e); }
    }
    private static Set<String> keys(JSONObject obj) {
        Set<String> keys = new TreeSet<>(); Iterator<String> it = obj.keys(); while (it.hasNext()) keys.add(it.next()); return keys;
    }
    public static String sha256(byte[] bytes) {
        try { return hex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    static String hex(byte[] bytes) {
        StringBuilder out = new StringBuilder(); for (byte b : bytes) out.append(String.format(Locale.ROOT,"%02x",b & 255)); return out.toString();
    }
}
