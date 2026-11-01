package com.donglan.chrona.classroom;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Read-only projection: preserve every saved item, never classify or merge old notes. */
public final class ClassroomAffairs {
    private ClassroomAffairs() { }

    public static JSONArray project(List<JSONObject> history,String courseId)throws Exception {
        List<JSONObject> lessons=new ArrayList<>();
        for(JSONObject record:history){
            JSONObject identity=record.optJSONObject("lesson");
            if(identity==null||!courseId.equals(identity.optString("course_id")))continue;
            String key=ClassroomDocument.key(identity);JSONArray original=record.optJSONArray("parts");
            if(original==null)continue;
            JSONArray parts=new JSONArray();boolean present=false;
            for(int p=0;p<original.length();p++){
                JSONObject source=original.getJSONObject(p);
                JSONArray affairs=source.has("affairs")?source.getJSONArray("affairs"):new JSONArray();
                JSONArray tasks=source.getJSONArray("assignments");
                present|=affairs.length()>0||tasks.length()>0;
                parts.put(new JSONObject().put("notes",new JSONArray()).put("affairs",new JSONArray(affairs.toString()))
                        .put("assignments",new JSONArray(tasks.toString())));
            }
            if(!present)continue;
            JSONObject summary=new JSONObject().put("lesson",new JSONObject(identity.toString())).put("complete",record.optBoolean("complete"))
                    .put("overview","").put("parts",parts);
            JSONArray images=new JSONArray();for(String image:ClassroomDocument.referencedImages(summary))images.put(image);
            ClassroomLesson lesson=ClassroomLesson.fromJson(identity);
            String title=lesson.startsAt>0?lesson.title.replace(lesson.date().toString(),"").strip():lesson.title;
            String label=lesson.startsAt>0?lesson.date()+(title.isEmpty()?"":" · "+title):title;
            lessons.add(new JSONObject().put("key",key).put("label",label).put("summary",summary).put("images",images));
        }
        lessons.sort(Comparator.comparingLong((JSONObject row)->row.optJSONObject("summary").optJSONObject("lesson").optLong("starts_at"))
                .thenComparing(row->row.optString("key")));
        JSONArray result=new JSONArray();for(JSONObject lesson:lessons)result.put(lesson);return result;
    }
}
