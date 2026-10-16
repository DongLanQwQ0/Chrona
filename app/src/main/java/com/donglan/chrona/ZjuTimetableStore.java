package com.donglan.chrona;

import android.content.Context;
import android.util.AtomicFile;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** A successful read snapshot, never a system-calendar write. */
final class ZjuTimetableStore {
    private static final Object LOCK=new Object();
    private final AtomicFile file;
    private static volatile long revision;
    ZjuTimetableStore(Context context){file=new AtomicFile(new File(context.getFilesDir(),"zju-timetable.json"));}
    static long revision(){return revision;}
    JSONObject snapshot()throws Exception{
        synchronized(LOCK){
            return !file.getBaseFile().isFile()&&!new File(file.getBaseFile()+".bak").isFile()?null
                    :new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
        }
    }
    void restore(JSONObject snapshot)throws Exception{
        synchronized(LOCK){
            if(snapshot==null){clear();return;}
            byte[] bytes=snapshot.toString().getBytes(StandardCharsets.UTF_8);
            FileOutputStream stream=file.startWrite();
            try{stream.write(bytes);file.finishWrite(stream);revision++;}
            catch(Exception failure){file.failWrite(stream);throw failure;}
        }
    }
    List<ZjuSnapshot.Lesson> load(String account)throws Exception{
        synchronized(LOCK){
            List<ZjuSnapshot.Lesson> result=new ArrayList<>();if(!file.getBaseFile().isFile()&&!new File(file.getBaseFile()+".bak").isFile())return result;
            JSONObject root=new JSONObject(new String(file.readFully(),StandardCharsets.UTF_8));
            if(!account.equals(root.getString("account")))return result;
            JSONArray rows=root.getJSONArray("lessons");
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);
                result.add(new ZjuSnapshot.Lesson(row.getString("key"),row.getString("title"),row.getString("location"),row.getString("term"),row.getLong("start"),row.getLong("end"),row.optString("notes","")));
            }
            return result;
        }
    }
    void save(String account,List<ZjuSnapshot.Lesson> lessons,List<String> completeTerms)throws Exception{
        synchronized(LOCK){
            Set<String> replaced=new HashSet<>(completeTerms);
            Map<String,ZjuSnapshot.Lesson> next=new LinkedHashMap<>();
            // Completeness is explicit: even an empty successful term can remove old
            // lessons. Warnings about campus scope or undated practice are unrelated.
            for(ZjuSnapshot.Lesson old:load(account))if(!replaced.contains(old.term))next.put(old.key,old);
            for(ZjuSnapshot.Lesson lesson:lessons)next.put(lesson.key,lesson);
            JSONArray rows=new JSONArray();for(ZjuSnapshot.Lesson l:next.values())rows.put(new JSONObject().put("key",l.key).put("title",l.title)
                    .put("location",l.location).put("term",l.term).put("start",l.start).put("end",l.end).put("notes",l.notes));
            byte[] bytes=new JSONObject().put("account",account).put("lessons",rows).toString().getBytes(StandardCharsets.UTF_8);
            FileOutputStream stream=file.startWrite();try{stream.write(bytes);file.finishWrite(stream);revision++;}catch(Exception e){file.failWrite(stream);throw e;}
        }
    }
    void clear(){synchronized(LOCK){file.delete();revision++;}}
}
