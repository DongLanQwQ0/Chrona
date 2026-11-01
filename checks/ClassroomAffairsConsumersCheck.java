package com.donglan.chrona.classroom;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.*;

/** Production document/export contracts; generated local inputs, no AI, school or device. */
public final class ClassroomAffairsConsumersCheck {
    private static int checks;
    private static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
    private interface Attempt {void run()throws Exception;}
    private static void rejects(Attempt work,String label)throws Exception {boolean rejected=false;try{work.run();}catch(Exception expected){rejected=true;}check(rejected,label);}
    private static JSONObject copy(JSONObject value)throws Exception{return new JSONObject(value.toString());}
    private static JSONObject note(String title,String text)throws Exception{return new JSONObject().put("title",title).put("text",text).put("sources",new JSONArray().put(0)).put("images",new JSONArray());}
    private static JSONObject task(String text)throws Exception{return new JSONObject().put("text",text).put("sources",new JSONArray().put(0));}
    private static JSONArray segments()throws Exception{return new JSONArray().put(new JSONObject().put("start",3).put("end",8).put("text","原字幕"));}
    private static JSONObject summary()throws Exception{
        JSONObject first=new JSONObject().put("notes",new JSONArray().put(note("知识一","概念一"))).put("affairs",new JSONArray().put(note("评分规则","平时分30%。\n\n![图][slide]\n\n[slide]: images/image-17.jpg").put("images",new JSONArray().put("image-17.jpg").put("images/image-17.jpg"))))
                .put("assignments",new JSONArray().put(task("第一项行动")));
        JSONObject second=new JSONObject().put("notes",new JSONArray().put(note("知识二","概念二"))).put("affairs",new JSONArray().put(note("考核范围","第三章不考。"))).put("assignments",new JSONArray().put(task("第二项行动")));
        return new JSONObject().put("lesson",new ClassroomLesson("41","51","课程","第1节","教室","6",1700000000000L).json()).put("complete",true).put("updated",1)
                .put("overview","统一概览").put("affairs_schema",1).put("raw_images",20).put("candidate_images",19).put("images",new JSONArray().put(new JSONObject().put("file","image-17.jpg")))
                .put("parts",new JSONArray().put(first).put(second));
    }
    private static String markdown(File dir,JSONObject summary)throws Exception{
        ByteArrayOutputStream output=new ByteArrayOutputStream();ClassroomExport.write(dir,summary,output);
        Set<String> names=new LinkedHashSet<>();String markdown=null;
        try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(output.toByteArray()),StandardCharsets.UTF_8)){
            ZipEntry entry;while((entry=zip.getNextEntry())!=null){check(names.add(entry.getName()),"unique ZIP entry");byte[] bytes=zip.readAllBytes();if(entry.getName().equals("课堂总结.md"))markdown=new String(bytes,StandardCharsets.UTF_8);}
        }
        check(names.contains("sources.md"),"subtitle source included");return markdown;
    }
    private static int count(String text,String match){return (text.length()-text.replace(match,"").length())/match.length();}
    private static void projection()throws Exception {
        JSONObject later=summary();later.getJSONObject("lesson").put("starts_at",2000);later.put("updated",1);
        JSONObject earlier=copy(later);earlier.getJSONObject("lesson").put("id","50").put("starts_at",1000);earlier.put("updated",9999).put("complete",false);
        JSONObject otherCourse=copy(later);otherCourse.getJSONObject("lesson").put("course_id","42");
        JSONObject legacy=copy(later);legacy.getJSONObject("lesson").put("id","53").put("starts_at",3000);legacy.remove("affairs_schema");
        for(int p=0;p<legacy.getJSONArray("parts").length();p++)legacy.getJSONArray("parts").getJSONObject(p).remove("affairs");
        legacy.getJSONArray("parts").getJSONObject(0).put("notes",new JSONArray().put(note("考试评分与出勤","期末范围混在教学知识中，不可靠分类。")));
        JSONObject oldNotesOnly=copy(legacy);oldNotesOnly.getJSONObject("lesson").put("id","54");
        for(int p=0;p<oldNotesOnly.getJSONArray("parts").length();p++)oldNotesOnly.getJSONArray("parts").getJSONObject(p).put("assignments",new JSONArray());
        JSONObject downloaded=new JSONObject().put("lesson",new ClassroomLesson("41","55","课程","仅下载","教室","6",4000).json()).put("downloaded",true);
        List<JSONObject> history=List.of(later,otherCourse,downloaded,legacy,earlier,oldNotesOnly);
        String snapshot=new JSONArray(history).toString();JSONArray projected=ClassroomAffairs.project(history,"41");
        check(projected.length()==3,"projection includes actual affairs/actions only, not downloaded or unclassified notes");
        check(projected.getJSONObject(0).getString("key").equals("41-50")&&projected.getJSONObject(1).getString("key").equals("41-51")&&projected.getJSONObject(2).getString("key").equals("41-53"),"actual lesson time orders same-course results, not update time/input order/course name");
        check(!projected.getJSONObject(0).getJSONObject("summary").getBoolean("complete"),"draft affair content retained");
        check(projected.getJSONObject(0).getJSONObject("summary").getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").getJSONObject(0).getString("text").equals(projected.getJSONObject(1).getJSONObject("summary").getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").getJSONObject(0).getString("text")),"repeated cross-lesson affair remains separately preserved");
        JSONObject projectedLegacy=projected.getJSONObject(2).getJSONObject("summary");
        check(projectedLegacy.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").length()==0&&projectedLegacy.getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").length()==0,"legacy exam keywords never classify old knowledge notes");
        check(projectedLegacy.getJSONArray("parts").getJSONObject(0).getJSONArray("assignments").toString().equals(legacy.getJSONArray("parts").getJSONObject(0).getJSONArray("assignments").toString()),"legacy assignments included with original source arrays");
        check(projected.getJSONObject(0).getJSONObject("summary").getJSONArray("parts").getJSONObject(1).getJSONArray("assignments").getJSONObject(0).getString("text").equals("第二项行动"),"within-lesson part/action order retained");
        check(projected.getJSONObject(1).getJSONArray("images").toString().equals(new JSONArray().put("image-17.jpg").toString())&&projected.getJSONObject(2).getJSONArray("images").length()==0,"projected image whitelist includes only original affairs/actions references");
        check(new JSONArray(history).toString().equals(snapshot),"projection leaves original full source records unchanged");
        projected.getJSONObject(0).getJSONObject("summary").getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").getJSONObject(0).put("text","changed projection");
        check(new JSONArray(history).toString().equals(snapshot),"projection owns deep copies and cannot mutate stored source");
        check(ClassroomAffairs.project(history,"999").length()==0,"unmatched course creates no records");
    }
    public static void main(String[] args)throws Exception{
        File dir=new File(args[0]);dir.mkdirs();ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("segments",segments()));Files.write(new File(dir,"image-17.jpg").toPath(),new byte[]{1,2,3});
        JSONObject value=summary(),materials=new JSONObject().put("segments",segments());String before=value.toString();
        check(ClassroomDocument.referencedImages(value).equals(Set.of("image-17.jpg")),"affairs image references join canonical inventory");
        JSONObject portable=ClassroomDocument.portable(value,materials);ClassroomDocument.validate(portable);
        check(portable.getJSONObject("summary").toString().equals(before),"portable preserves complete affairs and source arrays");
        check(value.toString().equals(before),"portable and references do not mutate original");
        for(Object invalid:List.of("wrong",JSONObject.NULL,new JSONObject())){
            JSONObject bad=copy(value);bad.getJSONArray("parts").getJSONObject(0).put("affairs",invalid);
            rejects(()->ClassroomDocument.referencedImages(bad),"present invalid affairs type rejected by references");
            rejects(()->ClassroomDocument.portable(bad,materials),"present invalid affairs type rejected by portable");
        }
        for(Object invalid:List.of(-1,1,"0")){
            JSONObject bad=copy(value);bad.getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").getJSONObject(0).put("sources",new JSONArray().put(invalid));
            rejects(()->ClassroomDocument.portable(bad,materials),"affairs source must be actual integer segment ID");
        }
        for(String field:List.of("title","text")){
            JSONObject bad=copy(value);bad.getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").getJSONObject(0).put(field,"![bad](images/image-999.jpg)");
            rejects(()->ClassroomDocument.portable(bad,materials),"affairs inline image whitelist in "+field);
        }
        for(Object invalid:List.of("wrong",JSONObject.NULL,new JSONObject())){
            JSONObject bad=copy(value);bad.getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").getJSONObject(0).put("images",invalid);
            rejects(()->ClassroomDocument.referencedImages(bad),"present invalid affair images type rejected");
        }
        for(String path:List.of("image-999.jpg","../image-17.jpg","https://example.com/image-17.jpg")){
            JSONObject bad=copy(value);bad.getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").getJSONObject(0).put("images",new JSONArray().put(path));
            rejects(()->ClassroomDocument.portable(bad,materials),"affairs attachment whitelist: "+path);
        }
        String md=markdown(dir,value);
        check(md.indexOf("## 重点概览")<md.indexOf("## 学习笔记")&&md.indexOf("概念二")<md.indexOf("## 课程事务"),"all learning precedes affairs");
        check(count(md,"## 课程事务\n")==1&&!md.contains("## 课堂安排"),"one affairs section combines facts and actions");
        check(md.contains("### 评分规则")&&md.contains("第三章不考。")&&md.indexOf("第一项行动")<md.indexOf("第二项行动"),"affairs titles and original action order preserved");
        check(count(md,"![图](images/image-17.jpg)")==1&&!md.contains("![课堂截图]")&&md.contains("[00:00:03](sources.md#s0)"),"affairs CommonMark fallback dedupe and actual sources exported");
        JSONObject only=copy(value);only.put("overview","");for(int p=0;p<only.getJSONArray("parts").length();p++)only.getJSONArray("parts").getJSONObject(p).put("notes",new JSONArray());
        String onlyMd=markdown(dir,only);check(!onlyMd.contains("## 学习笔记")&&!onlyMd.contains("## 重点概览")&&onlyMd.contains("## 课程事务"),"affairs-only output has no empty learning or overview shells");
        JSONObject empty=copy(only);for(int p=0;p<empty.getJSONArray("parts").length();p++)empty.getJSONArray("parts").getJSONObject(p).put("affairs",new JSONArray()).put("assignments",new JSONArray());
        check(!markdown(dir,empty).contains("## 课程事务"),"empty affairs section omitted");
        JSONObject legacy=copy(value);legacy.remove("affairs_schema");for(int p=0;p<legacy.getJSONArray("parts").length();p++)legacy.getJSONArray("parts").getJSONObject(p).remove("affairs");
        ClassroomDocument.portable(legacy,materials);String oldMd=markdown(dir,legacy);
        check(oldMd.contains("## 知识一")&&oldMd.contains("## 课堂安排")&&!oldMd.contains("## 学习笔记")&&!oldMd.contains("## 课程事务"),"old results retain unclassified export layout");
        projection();
        System.out.println("ClassroomAffairsConsumers: "+checks+" production document/export assertions passed (local generated fixtures; no visual or device acceptance)");
    }
}
