package com.donglan.chrona.classroom;

import java.io.IOException;
import org.json.JSONArray;
import org.json.JSONObject;

/** Wire structure only. Source membership, factual quality and editorial depth stay outside it. */
final class ClassroomResponseShape {
    private ClassroomResponseShape() { }
    static JSONObject contract(String stage,int affairs,int figures)throws Exception{
        JSONObject properties;String name;
        if(stage.equals("image-plan")||stage.equals("affairs-plan")){
            properties=new JSONObject().put("image_assessment",object(new JSONObject().put("decision",type("string")).put("reason",type("string"))))
                    .put("image_requests",array(object(new JSONObject().put("start",type("number")).put("end",type("number")).put("reason",type("string")))));
            if(stage.equals("affairs-plan")){
                JSONObject fields=summary(affairs,true);properties.put("affairs",fields.get("affairs")).put("assignments",fields.get("assignments"));
            }
            name=stage.equals("affairs-plan")?"affairs_plan":"image_plan";
        }else if(stage.equals("edit-affairs")){
            JSONObject note=new JSONObject().put("title",type("string")).put("text",type("string"))
                    .put("sources",array(type("integer"))).put("images",array(type("string")));
            properties=new JSONObject().put("affairs",array(object(note)));name="affairs";
        }else if(stage.equals("edit-plan")){
            JSONObject section=new JSONObject().put("title",type("string")).put("refs",array(type("string")));
            if(affairs>0)section.put("collection",type("string"));
            properties=new JSONObject().put("overview",type("string")).put("sections",array(object(section)));name="plan";
        }else if(stage.equals("edit-assignments")){
            properties=new JSONObject().put("groups",array(array(type("string"))));name="assignments";
        }else{
            boolean draft=stage.equals("first")||stage.matches("draft-[0-9]+"),
                    fin=stage.equals("whole-note")||stage.equals("final")||stage.matches("final-[0-9]+"),edit=stage.matches("edit-note-[0-9]+");
            if(!draft&&!fin&&!edit)throw new IOException("未知课堂JSON阶段");
            boolean evidence=stage.equals("whole-note")||stage.matches("(?:draft|final)-[0-9]+");
            properties=summary(affairs,evidence);
            if(draft){
                properties.put("image_assessment",object(new JSONObject().put("decision",type("string")).put("reason",type("string"))))
                        .put("image_requests",array(object(new JSONObject().put("start",type("number")).put("end",type("number")).put("reason",type("string")))));
                name="draft";
            }else if(fin){
                if(figures>0)properties.put("image_reviews",array(object(new JSONObject().put("file",type("string")).put("status",type("string"))
                        .put("confidence",type("string")).put("visible",type("string")).put("reason",type("string")))));
                name="final";
            }else name="topic";
        }
        return new JSONObject().put("name","chrona_"+name).put("strict",true).put("schema",object(properties));
    }
    private static JSONObject summary(int affairs,boolean evidence)throws Exception{
        JSONObject note=object(new JSONObject().put("title",type("string")).put("text",type("string"))
                .put("sources",array(type("integer"))).put("images",array(type("string"))));
        JSONObject task=new JSONObject().put("text",type("string")).put("sources",array(type("integer")));
        if(evidence)task.put("evidence",type("string"));
        JSONObject fields=new JSONObject().put("overview",type("string")).put("notes",array(note)).put("assignments",array(object(task)));
        if(affairs>0)fields.put("affairs",array(new JSONObject(note.toString())));
        return fields;
    }
    private static JSONObject type(String value)throws Exception{return new JSONObject().put("type",value);}
    private static JSONObject array(JSONObject items)throws Exception{return type("array").put("items",items);}
    private static JSONObject object(JSONObject properties)throws Exception{
        JSONArray required=new JSONArray();java.util.Iterator<String> keys=properties.keys();while(keys.hasNext())required.put(keys.next());
        return type("object").put("properties",properties).put("required",required).put("additionalProperties",false);
    }
}
