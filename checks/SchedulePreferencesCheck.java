import android.content.Context;
import com.donglan.chrona.ConfigBackup;
import com.donglan.chrona.ai.SchedulePreferences;
import com.donglan.chrona.ai.SchedulePrompt;
import org.json.JSONObject;
import org.json.JSONException;

public final class SchedulePreferencesCheck {
    private static int count;
    public static void main(String[] args) throws Exception {
        Context source = new Context(), target = new Context();
        SchedulePreferences defaults = SchedulePreferences.load(source);
        check(defaults.workStartMinutes == 600 && defaults.workEndMinutes == 1320
                && defaults.customPrompt.isEmpty() && defaults.classroomPrompt.isEmpty()
                && !defaults.enhancedClassroom, "default 10:00–22:00 and enhanced classroom off");
        check(!new SchedulePreferences(600, 1320, "", "").enhancedClassroom,
                "existing constructor keeps enhanced classroom off");
        check(defaults.classroomConcurrency == 3
                && new SchedulePreferences(600,1320,"", "",true).classroomConcurrency == 3,
                "new and original constructor defaults allow three classroom summaries");
        check(defaults.classroomNoteStrength == 3
                && new SchedulePreferences(600,1320,"", "").classroomNoteStrength == 3
                && new SchedulePreferences(600,1320,"", "",true).classroomNoteStrength == 3
                && new SchedulePreferences(600,1320,"", "",true,1).classroomNoteStrength == 3,
                "new and all original constructors default note strength to three");
        String custom = "优先安排上午\n保留引号：\"时间\"";
        new SchedulePreferences(22 * 60, 6 * 60, custom, "按课堂知识点分层", true, 2, 2).save(source);
        SchedulePreferences saved = SchedulePreferences.load(source);
        check(saved.workStartMinutes == 1320 && saved.workEndMinutes == 360
                && saved.customPrompt.equals(custom) && saved.enhancedClassroom,
                "overnight settings and enhanced classroom persist");
        check(SchedulePreferences.fromJson(saved.toJson()).enhancedClassroom,
                "enhanced classroom JSON roundtrip");
        check(saved.classroomConcurrency == 2 && SchedulePreferences.fromJson(saved.toJson()).classroomConcurrency == 2,
                "concurrency persists and JSON roundtrips");
        check(saved.classroomNoteStrength == 2 && SchedulePreferences.fromJson(saved.toJson()).classroomNoteStrength == 2,
                "note strength persists and JSON roundtrips");
        String system = saved.systemPrompt();
        check(system.startsWith(SchedulePrompt.SYSTEM), "default schema and safety retained");
        check(system.contains(JSONObject.quote(custom)) && system.contains("优先于上文默认解析规则"),
                "quoted explicit user preferences outrank defaults");
        check(system.contains("不能更改 JSON schema") && system.contains("建议时间仍须标记存疑"),
                "output and confirmation boundaries retained");
        check(system.contains("22:00–06:00") && system.contains("保留明确时间和截止 due"),
                "work window and due preservation sent");
        check(!system.contains("按课堂知识点分层"), "classroom preferences never enter schedule prompt");
        String portable = ConfigBackup.export(source, false);
        ConfigBackup.apply(target, portable);
        check(SchedulePreferences.load(target).classroomNoteStrength == 2,"backup restores note strength");
        JSONObject oldStrength = new JSONObject(portable);
        oldStrength.getJSONObject("schedulePreferences").remove("classroomNoteStrength");
        ConfigBackup.apply(target, oldStrength.toString());
        check(SchedulePreferences.load(target).classroomNoteStrength == 3,"old backup defaults note strength to three");
        ConfigBackup.apply(target, portable);
        for(Object invalid : new Object[]{0,4,-1,1.5,"2",true,JSONObject.NULL,new JSONObject(),Long.MAX_VALUE}) {
            JSONObject broken = new JSONObject(portable);
            broken.getJSONObject("schedulePreferences").put("classroomNoteStrength",invalid);
            try { ConfigBackup.apply(target,broken.toString());throw new AssertionError("invalid note strength accepted"); }
            catch(JSONException expected) { count++; }
            check(SchedulePreferences.load(target).classroomNoteStrength == 2,"invalid note strength preserves saved setting");
        }
        for(int strength:new int[]{1,2,3}) {
            SchedulePreferences preferences = new SchedulePreferences(600,1320,"","",false,2,strength);
            preferences.save(source);
            check(SchedulePreferences.load(source).classroomNoteStrength == strength
                    && SchedulePreferences.fromJson(preferences.toJson()).classroomNoteStrength == strength,
                    "each supported note strength saves and JSON roundtrips");
        }
        for(int invalid:new int[]{0,4,-1}) {
            try { new SchedulePreferences(600,1320,"","",false,2,invalid);throw new AssertionError("invalid constructor note strength accepted"); }
            catch(IllegalArgumentException expected) { count++; }
        }
        for(String invalid:new String[]{"0","4","-1","1.5","bad"}) {
            source.getSharedPreferences("chrona_schedule_preferences",0).edit().putString("classroomNoteStrength",invalid).apply();
            try { SchedulePreferences.load(source);throw new AssertionError("invalid stored note strength accepted"); }
            catch(IllegalArgumentException expected) { count++; }
        }
        new SchedulePreferences(1320,360,custom,"按课堂知识点分层",true,2,2).save(source);
        check(SchedulePreferences.load(target).classroomConcurrency == 2,"backup restores concurrency");
        JSONObject oldConcurrency = new JSONObject(portable);
        oldConcurrency.getJSONObject("schedulePreferences").remove("classroomConcurrency");
        ConfigBackup.apply(target, oldConcurrency.toString());
        check(SchedulePreferences.load(target).classroomConcurrency == 3,"old backup defaults concurrency to three");
        ConfigBackup.apply(target, portable);
        for(Object invalid : new Object[]{0,4,-1,1.5,"2",true,JSONObject.NULL,new JSONObject(),Long.MAX_VALUE}) {
            JSONObject broken = new JSONObject(portable);
            broken.getJSONObject("schedulePreferences").put("classroomConcurrency",invalid);
            try { ConfigBackup.apply(target,broken.toString());throw new AssertionError("invalid concurrency accepted"); }
            catch(JSONException expected) { count++; }
            check(SchedulePreferences.load(target).classroomConcurrency == 2,"invalid concurrency preserves saved setting");
        }
        for(int limit:new int[]{1,2,3}) {
            SchedulePreferences preferences = new SchedulePreferences(600,1320,"","",false,limit);
            preferences.save(source);
            check(SchedulePreferences.load(source).classroomConcurrency == limit,"each supported concurrency saves");
        }
        for(int invalid:new int[]{0,4,-1}) {
            try { new SchedulePreferences(600,1320,"","",false,invalid);throw new AssertionError("invalid constructor concurrency accepted"); }
            catch(IllegalArgumentException expected) { count++; }
        }
        check(SchedulePreferences.load(target).customPrompt.equals(custom)
                && SchedulePreferences.load(target).workEndMinutes == 360
                && SchedulePreferences.load(target).classroomPrompt.equals("按课堂知识点分层")
                && SchedulePreferences.load(target).enhancedClassroom,
                "backup restores both independent prompts and enhanced classroom without API key");
        JSONObject without = new JSONObject(portable);
        without.remove("schedulePreferences");
        ConfigBackup.apply(target, without.toString());
        check(SchedulePreferences.load(target).customPrompt.equals(custom)
                && SchedulePreferences.load(target).enhancedClassroom
                && SchedulePreferences.load(target).classroomNoteStrength == 2,
                "absent preference section preserves local settings");
        JSONObject legacy = new JSONObject(portable);
        legacy.getJSONObject("schedulePreferences").remove("enhancedClassroom");
        ConfigBackup.apply(target, legacy.toString());
        check(!SchedulePreferences.load(target).enhancedClassroom,
                "old preference section without enhanced classroom defaults off");
        ConfigBackup.apply(target, portable);
        for (Object invalid : new Object[]{"true", 1, JSONObject.NULL, new JSONObject()}) {
            JSONObject broken = new JSONObject(portable);
            broken.getJSONObject("schedulePreferences").put("enhancedClassroom", invalid);
            try { ConfigBackup.apply(target, broken.toString()); throw new AssertionError("invalid enhanced switch accepted"); }
            catch (JSONException expected) { count++; }
            check(SchedulePreferences.load(target).enhancedClassroom,
                    "invalid enhanced switch import preserves saved setting");
        }
        new SchedulePreferences(600, 1320, "", "", false).save(source);
        check(!SchedulePreferences.load(source).enhancedClassroom,
                "enhanced classroom can be saved off after enabled");
        check(!SchedulePreferences.fromJson(SchedulePreferences.load(source).toJson()).enhancedClassroom,
                "disabled enhanced classroom JSON roundtrip");
        for (int[] pair : new int[][]{{600,600},{-1,100},{100,1440}}) {
            try { new SchedulePreferences(pair[0], pair[1], "", ""); throw new AssertionError("invalid window accepted"); }
            catch (IllegalArgumentException expected) { count++; }
        }
        try { new SchedulePreferences(600,1320,"x".repeat(8001), ""); throw new AssertionError("unbounded prompt"); }
        catch (IllegalArgumentException expected) { count++; }
        try { new SchedulePreferences(600,1320,"", "x".repeat(8001)); throw new AssertionError("unbounded classroom prompt"); }
        catch (IllegalArgumentException expected) { count++; }
        for (Object invalid : new Object[]{600.5,"600",JSONObject.NULL}) {
            JSONObject broken = new JSONObject(portable);
            broken.getJSONObject("schedulePreferences").put("workStartMinutes", invalid);
            try { ConfigBackup.apply(target, broken.toString()); throw new AssertionError("invalid import accepted"); }
            catch (JSONException expected) { count++; }
            check(SchedulePreferences.load(target).workStartMinutes == 1320, "invalid import leaves preference intact");
        }
        JSONObject invalidClassroom = new JSONObject(portable);
        invalidClassroom.getJSONObject("schedulePreferences").put("classroomPrompt", 7);
        try { ConfigBackup.apply(target, invalidClassroom.toString()); throw new AssertionError("invalid classroom prompt accepted"); }
        catch (JSONException expected) { count++; }
        check(SchedulePreferences.load(target).classroomPrompt.equals("按课堂知识点分层"), "invalid classroom import preserves saved prompt");
        System.out.println("SchedulePreferencesCheck: " + count + " checks passed");
    }
    private static void check(boolean condition, String label) {
        if (!condition) throw new AssertionError(label);
        count++;
    }
}
