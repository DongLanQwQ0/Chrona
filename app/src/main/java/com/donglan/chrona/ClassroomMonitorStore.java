package com.donglan.chrona;

import android.content.Context;
import android.content.SharedPreferences;
import com.donglan.chrona.classroom.ClassroomLesson;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Device-local registrations and once-per-lesson automatic-summary decisions. */
final class ClassroomMonitorStore {
    static final Object LOCK = new Object();
    static final long POLL_MILLIS = 30_000L, CATALOG_MILLIS = 5 * 60_000L;
    private final SharedPreferences prefs;
    final String account;
    ClassroomMonitorStore(Context context, String account) {
        prefs = context.getSharedPreferences("chrona_classroom_monitor", Context.MODE_PRIVATE);
        this.account = account;
    }
    JSONObject read() throws Exception {
        synchronized(LOCK) { return new JSONObject(prefs.getString(account,"{\"rules\":[],\"done\":[],\"catalog\":[]}")); }
    }
    private void save(JSONObject state) throws IOException {
        if(!prefs.edit().putString(account,state.toString()).commit())throw new IOException("无法保存课堂监视");
    }
    static String key(ClassroomLesson lesson) { return lesson.courseId+"/"+lesson.id; }
    static java.util.Set<String> accounts(Context app){return app.getSharedPreferences("chrona_classroom_monitor",Context.MODE_PRIVATE).getAll().keySet();}
    void add(ClassroomLesson lesson, boolean repeat) throws Exception {
        synchronized(LOCK) {
            JSONObject state=read(); JSONArray rules=state.getJSONArray("rules");
            for(int i=rules.length()-1;i>=0;i--) {
                JSONObject rule=rules.getJSONObject(i); ClassroomLesson old=ClassroomLesson.fromJson(rule.getJSONObject("lesson"));
                if(old.courseId.equals(lesson.courseId)&&(rule.getBoolean("repeat")||key(old).equals(key(lesson))
                        ||repeat&&old.startsAt>=lesson.startsAt))rules.remove(i);
            }
            rules.put(new JSONObject().put("lesson",lesson.json()).put("repeat",repeat));
            JSONArray done=state.getJSONArray("done");if(auto(state,lesson)==null)for(int i=done.length()-1;i>=0;i--)if(key(lesson).equals(done.getString(i)))done.remove(i);
            state.put("checked",0).put("message",repeat?"已监视该课程后续课堂":"已监视本节课堂");cancelUnregistered(state);save(state);
        }
    }
    void remove(int index) throws Exception {
        synchronized(LOCK) { JSONObject state=read();state.getJSONArray("rules").remove(index);cancelUnregistered(state);save(state); }
    }
    void stopAll() throws Exception {
        synchronized(LOCK) { JSONObject state=read();state.put("rules",new JSONArray()).put("message","已停止监视");cancelUnregistered(state);save(state); }
    }
    boolean hasRules() throws Exception { return read().getJSONArray("rules").length()>0; }
    static JSONObject ruleFor(JSONObject state,ClassroomLesson lesson)throws Exception {
        JSONArray rules=state.getJSONArray("rules");
        for(int i=0;i<rules.length();i++){JSONObject rule=rules.getJSONObject(i);ClassroomLesson selected=ClassroomLesson.fromJson(rule.getJSONObject("lesson"));
            if(rule.getBoolean("repeat")?selected.courseId.equals(lesson.courseId)&&(lesson.id.equals(selected.id)||lesson.startsAt>=selected.startsAt):key(selected).equals(key(lesson)))return rule;}
        return null;
    }
    void stop(ClassroomLesson lesson)throws Exception {
        synchronized(LOCK){JSONObject state=read();JSONArray rules=state.getJSONArray("rules");
            for(int i=rules.length()-1;i>=0;i--){JSONObject rule=rules.getJSONObject(i);ClassroomLesson selected=ClassroomLesson.fromJson(rule.getJSONObject("lesson"));
                if(rule.getBoolean("repeat")?selected.courseId.equals(lesson.courseId)&&(lesson.id.equals(selected.id)||lesson.startsAt>=selected.startsAt):key(selected).equals(key(lesson)))rules.remove(i);}
            state.put("message","已停止监视");cancelUnregistered(state);save(state);}
    }
    static boolean matches(JSONObject state, ClassroomLesson lesson) throws Exception {
        JSONArray rules=state.getJSONArray("rules"),done=state.getJSONArray("done");
        for(int i=0;i<done.length();i++)if(key(lesson).equals(done.getString(i)))return false;
        for(int i=0;i<rules.length();i++) {
            JSONObject rule=rules.getJSONObject(i);ClassroomLesson selected=ClassroomLesson.fromJson(rule.getJSONObject("lesson"));
            if(rule.getBoolean("repeat")?selected.courseId.equals(lesson.courseId)&&(lesson.id.equals(selected.id)||lesson.startsAt>=selected.startsAt):key(selected).equals(key(lesson)))return true;
        }
        return false;
    }
    static List<ClassroomLesson> pending(JSONObject state) throws Exception {
        java.util.LinkedHashMap<String,ClassroomLesson> known=new java.util.LinkedHashMap<>();
        JSONArray rules=state.getJSONArray("rules"),catalog=state.getJSONArray("catalog");
        for(int i=0;i<rules.length();i++){ClassroomLesson l=ClassroomLesson.fromJson(rules.getJSONObject(i).getJSONObject("lesson"));known.put(key(l),l);}
        for(int i=0;i<catalog.length();i++){ClassroomLesson l=ClassroomLesson.fromJson(catalog.getJSONObject(i));known.put(key(l),l);}
        List<ClassroomLesson> result=new ArrayList<>();
        for(ClassroomLesson lesson:known.values())if(matches(state,lesson))result.add(lesson);
        result.sort(java.util.Comparator.comparingLong(lesson->lesson.startsAt));return result;
    }
    void catalog(List<ClassroomLesson> lessons,long now) throws Exception {
        synchronized(LOCK){JSONObject state=read();JSONArray rows=new JSONArray();for(ClassroomLesson lesson:lessons)rows.put(lesson.json());state.put("catalog",rows).put("checked",now);save(state);}
    }
    void attempted(long now) throws Exception { synchronized(LOCK){JSONObject state=read();state.put("checked",now);save(state);} }
    void message(String text) throws Exception { synchronized(LOCK){JSONObject state=read();state.put("message",text);save(state);} }
    void message(ClassroomLesson lesson,String text) throws Exception {
        synchronized(LOCK){JSONObject state=read();JSONObject statuses=state.optJSONObject("statuses");if(statuses==null)statuses=new JSONObject();
            statuses.put("lesson:"+key(lesson),text).put("course:"+lesson.courseId,text);state.put("statuses",statuses).put("message",text);save(state);}
    }
    void finished(ClassroomLesson lesson) throws Exception {
        synchronized(LOCK){JSONObject state=read();if(!matches(state,lesson))return;
            state.getJSONArray("done").put(key(lesson));
            JSONObject tasks=state.optJSONObject("summaries");if(tasks==null)tasks=new JSONObject();
            if(!tasks.has(key(lesson)))tasks.put(key(lesson),new JSONObject().put("lesson",lesson.json()).put("state","queued")
                    .put("imageLimit",state.optBoolean("summaryImages")?com.donglan.chrona.classroom.ClassroomSummary.MAX_IMAGES:0).put("updated",System.currentTimeMillis()));
            state.put("summaries",tasks).put("message",lesson.course+" · 素材已下载，等待自动总结");save(state);
        }
    }
    boolean summaryImages()throws Exception{return read().optBoolean("summaryImages");}
    void summaryImages(boolean enabled)throws Exception{synchronized(LOCK){JSONObject state=read();state.put("summaryImages",enabled);save(state);}}
    static JSONObject auto(JSONObject state,ClassroomLesson lesson){JSONObject tasks=state.optJSONObject("summaries");return tasks==null?null:tasks.optJSONObject(key(lesson));}
    static List<JSONObject> autoTasks(JSONObject state)throws Exception{
        List<JSONObject> result=new ArrayList<>();JSONObject tasks=state.optJSONObject("summaries");
        if(tasks!=null){java.util.Iterator<String> keys=tasks.keys();while(keys.hasNext())result.add(tasks.getJSONObject(keys.next()));}return result;
    }
    static boolean autoWork(JSONObject state)throws Exception{for(JSONObject task:autoTasks(state))if("queued".equals(task.optString("state"))||"running".equals(task.optString("state")))return true;return false;}
    private static void cancelUnregistered(JSONObject state)throws Exception{
        for(JSONObject task:autoTasks(state)){ClassroomLesson lesson=ClassroomLesson.fromJson(task.getJSONObject("lesson"));
            if(("queued".equals(task.optString("state"))||"running".equals(task.optString("state")))&&ruleFor(state,lesson)==null)
                task.put("state","interrupted").put("detail",lesson.course+" · 自动总结已停止，可手动继续").put("updated",System.currentTimeMillis());}
    }
    boolean summaryState(ClassroomLesson lesson,String expected,String next,String text)throws Exception{
        synchronized(LOCK){JSONObject state=read(),task=auto(state,lesson);if(task==null||expected!=null&&!expected.equals(task.optString("state")))return false;
            if("running".equals(next)&&ruleFor(state,lesson)==null)return false;
            task.put("state",next).put("detail",text).put("updated",System.currentTimeMillis());
            JSONObject statuses=state.optJSONObject("statuses");if(statuses==null)statuses=new JSONObject();
            statuses.put("lesson:"+key(lesson),text).put("course:"+lesson.courseId,text);state.put("statuses",statuses).put("message",text);
            if(!"queued".equals(next)&&!"running".equals(next)){JSONArray rules=state.getJSONArray("rules");
                for(int i=rules.length()-1;i>=0;i--)if(!rules.getJSONObject(i).getBoolean("repeat")&&key(ClassroomLesson.fromJson(rules.getJSONObject(i).getJSONObject("lesson"))).equals(key(lesson)))rules.remove(i);}
            save(state);return true;
        }
    }
    static long next(JSONObject state,long now) throws Exception {
        boolean automatic=autoWork(state);
        if(state.getJSONArray("rules").length()==0&&!automatic)return -1;
        long next=automatic?now+POLL_MILLIS:Math.max(now,state.optLong("checked")+CATALOG_MILLIS);
        for(ClassroomLesson lesson:pending(state))next=Math.min(next,lesson.startsAt<=now?now+POLL_MILLIS:lesson.startsAt);
        return next;
    }
}
