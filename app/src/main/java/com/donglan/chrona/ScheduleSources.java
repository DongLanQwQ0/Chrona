package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONException;
import org.json.JSONObject;

/** Global source permissions; page-specific inclusion switches still apply independently. */
public final class ScheduleSources {
    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences("schedule_sources", Context.MODE_PRIVATE);
    }

    /** Existing device calendars remain enabled until explicitly disabled. */
    public static boolean allowsCalendar(Context context, long calendarId) {
        return prefs(context).getBoolean("calendar:" + calendarId, true);
    }
    public static boolean importedEnabled(Context context) { return prefs(context).getBoolean("imported", true); }
    public static boolean zjuEnabled(Context context) { return prefs(context).getBoolean("zju", true); }
    public static long revision(Context context) { return prefs(context).getLong("revision", 0L); }
    /** Tie-break preference for confirmed duplicates only; it never hides unrelated secondary events. */
    public static String primary(Context context) { return prefs(context).getString("primary", "all"); }
    /** Snapshot of device-local source IDs and explicit pair decisions; revision is runtime state. */
    public static synchronized String exportJson(Context context) throws JSONException {
        SharedPreferences preferences=prefs(context);
        JSONObject calendars=new JSONObject(),pairs=new JSONObject();
        for(java.util.Map.Entry<String,?> entry:preferences.getAll().entrySet()){
            String key=entry.getKey();
            if(key.startsWith("calendar:") && entry.getValue() instanceof Boolean)
                calendars.put(key.substring(9),entry.getValue());
            else if(key.startsWith("course_pair:") && entry.getValue() instanceof String)
                pairs.put(key.substring(12),entry.getValue());
        }
        return new JSONObject().put("version",1).put("imported",importedEnabled(context))
                .put("zju",zjuEnabled(context)).put("primary",primary(context))
                .put("calendars",calendars).put("coursePairs",pairs).toString();
    }
    /** Validate the complete snapshot before replacing settings in one durable preferences commit. */
    public static synchronized void importJson(Context context,String json) throws JSONException {
        java.util.Map<String,Object> values=parseJson(json);
        SharedPreferences preferences=prefs(context);long nextRevision=revision(context)+1L;
        SharedPreferences.Editor editor=preferences.edit().clear();
        for(java.util.Map.Entry<String,Object> entry:values.entrySet()){
            if(entry.getValue() instanceof Boolean)editor.putBoolean(entry.getKey(),(Boolean)entry.getValue());
            else editor.putString(entry.getKey(),(String)entry.getValue());
        }
        if(!editor.putLong("revision",nextRevision).commit())throw new IllegalStateException("无法保存日程来源配置");
        refresh(context);
    }
    /** Read-only preflight for backup preparation; shares the import parser. */
    public static void validateJson(String json) throws JSONException { parseJson(json); }
    private static java.util.Map<String,Object> parseJson(String json) throws JSONException {
        JSONObject root=new JSONObject(json);
        java.util.Set<String> fields=new java.util.HashSet<>(java.util.Arrays.asList("version","imported","zju","primary","calendars","coursePairs"));
        java.util.Iterator<String> names=root.keys();
        while(names.hasNext())if(!fields.contains(names.next()))throw new JSONException("Unknown schedule-source field");
        Object version=root.get("version"),imported=root.get("imported"),zju=root.get("zju"),primary=root.get("primary");
        if(!(version instanceof Number)||((Number)version).doubleValue()!=1.0
                ||!(imported instanceof Boolean)||!(zju instanceof Boolean)||!(primary instanceof String)
                ||!("all".equals(primary)||validSource((String)primary)))throw new JSONException("Invalid schedule-source settings");
        Object calendarValue=root.get("calendars"),pairValue=root.get("coursePairs");
        if(!(calendarValue instanceof JSONObject)||!(pairValue instanceof JSONObject))throw new JSONException("Invalid schedule-source maps");
        JSONObject calendars=(JSONObject)calendarValue,pairs=(JSONObject)pairValue;
        java.util.Map<String,Object> values=new java.util.HashMap<>();
        values.put("imported",imported);values.put("zju",zju);values.put("primary",primary);
        names=calendars.keys();
        while(names.hasNext()){
            String id=names.next();Object enabled=calendars.get(id);
            if(!validCalendarId(id)||!(enabled instanceof Boolean))throw new JSONException("Invalid calendar source switch");
            values.put("calendar:"+id,enabled);
        }
        names=pairs.keys();
        while(names.hasNext()){
            String pair=names.next();Object preferred=pairs.get(pair);
            String[] identities=pair.split("\u001f",-1);
            if(identities.length!=2||!validIdentity(identities[0])||!validIdentity(identities[1])
                    ||identities[0].compareTo(identities[1])>=0||sourceOf(identities[0]).equals(sourceOf(identities[1]))
                    ||!(preferred instanceof String)||!("reject".equals(preferred)
                    ||sourceOf(identities[0]).equals(preferred)||sourceOf(identities[1]).equals(preferred)))
                throw new JSONException("Invalid course association");
            values.put("course_pair:"+pair,preferred);
        }
        return values;
    }
    private static boolean validCalendarId(String value){
        try{long id=Long.parseLong(value);return id>0&&Long.toString(id).equals(value);}
        catch(NumberFormatException invalid){return false;}
    }
    private static boolean validSource(String value){return "imported".equals(value)||"zju".equals(value)||validSystem(value);}
    private static String sourceOf(String identity){return identity.substring(0,identity.indexOf('\n'));}
    private static boolean validIdentity(String identity){
        int split=identity.indexOf('\n');
        return split>0&&split<identity.length()-1&&identity.indexOf('\n',split+1)<0&&validSource(identity.substring(0,split));
    }
    public static java.util.Map<String,String> courseRules(Context context) {
        java.util.Map<String,String> rules=new java.util.HashMap<>();
        for(java.util.Map.Entry<String,?> entry:prefs(context).getAll().entrySet())
            if(entry.getKey().startsWith("course_pair:") && entry.getValue() instanceof String)
                rules.put(entry.getKey().substring(12),(String)entry.getValue());
        return rules;
    }
    public static synchronized void setCourseRule(Context context,String pair,String preferred) {
        if(preferred!=null && !"reject".equals(preferred) && !"imported".equals(preferred)
                && !"zju".equals(preferred) && !validSystem(preferred))throw new IllegalArgumentException("Unknown course source");
        SharedPreferences.Editor editor=prefs(context).edit();
        if(preferred==null)editor.remove("course_pair:"+pair);else editor.putString("course_pair:"+pair,preferred);
        save(context,editor);
    }
    public static void setCalendarEnabled(Context context, long id, boolean enabled) {
        if (id <= 0) throw new IllegalArgumentException("Calendar ID must be positive");
        setBoolean(context, "calendar:" + id, enabled);
    }
    public static void setImportedEnabled(Context context, boolean enabled) { setBoolean(context, "imported", enabled); }
    public static void setZjuEnabled(Context context, boolean enabled) { setBoolean(context, "zju", enabled); }
    public static synchronized void setPrimary(Context context, String value) {
        if (!("all".equals(value) || "imported".equals(value) || "zju".equals(value)
                || validSystem(value))) throw new IllegalArgumentException("Unknown source");
        SharedPreferences preferences = prefs(context);
        if (value.equals(primary(context))) return;
        save(context, preferences.edit().putString("primary", value));
    }
    private static boolean validSystem(String value) {
        if (value == null || !value.startsWith("system:")) return false;
        return validCalendarId(value.substring(7));
    }
    private static synchronized void setBoolean(Context context, String key, boolean value) {
        SharedPreferences preferences = prefs(context);
        if (preferences.getBoolean(key, true) == value) return;
        save(context, preferences.edit().putBoolean(key, value));
    }
    private static void save(Context context, SharedPreferences.Editor editor) {
        editor.putLong("revision", revision(context) + 1L).apply();
        refresh(context);
    }
    private static void refresh(Context context) {
        AgendaWidgetProvider.requestRefresh(context);
        WeekCalendarWidgetProvider.requestRefresh(context);
        AgendaReminder.requestRefresh(context);
    }
    private ScheduleSources() { }
}
