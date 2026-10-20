package com.donglan.chrona.classroom;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** One reference contract for reading, export, retention and portable documents. */
public final class ClassroomDocument {
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]\\r\\n]*\\]\\(([^)\\r\\n]*)\\)");
    private ClassroomDocument() { }

    public static String imageName(String path) throws IOException {
        if (path.startsWith("images/")) path = path.substring(7);
        if (!path.matches("image-[0-9]+\\.jpg")) throw new IOException("图片引用必须使用 images/image-编号.jpg");
        return path;
    }
    public static Set<String> inlineImages(String markdown) throws IOException {
        Set<String> result = new LinkedHashSet<>();
        Matcher matches = IMAGE.matcher(markdown);
        while (matches.find()) result.add(imageName(matches.group(1).trim()));
        return result;
    }
    public static String exportMarkdown(String markdown) throws IOException {
        Matcher matches=IMAGE.matcher(markdown);StringBuffer result=new StringBuffer();
        while(matches.find()) {
            String image=imageName(matches.group(1).trim());
            String replacement=matches.group().substring(0,matches.group().indexOf("](")+2)+"images/"+image+")";
            matches.appendReplacement(result,Matcher.quoteReplacement(replacement));
        }
        matches.appendTail(result);return result.toString();
    }
    public static Set<String> referencedImages(JSONObject summary) throws Exception {
        Set<String> result = new LinkedHashSet<>(inlineImages(summary.optString("overview")));
        JSONArray parts = summary.getJSONArray("parts");
        for (int p=0;p<parts.length();p++) {
            JSONObject part=parts.getJSONObject(p);
            result.addAll(inlineImages(part.optString("overview")));
            for (String collection:new String[]{"notes","assignments"}) {
                JSONArray rows=part.getJSONArray(collection);
                for(int i=0;i<rows.length();i++) {
                    JSONObject row=rows.getJSONObject(i);
                    result.addAll(inlineImages(row.optString("title")));
                    result.addAll(inlineImages(row.getString("text")));
                    JSONArray images=row.optJSONArray("images");
                    if(images!=null)for(int n=0;n<images.length();n++)result.add(imageName(images.getString(n)));
                }
            }
        }
        return result;
    }
    public static String key(JSONObject lesson) throws Exception {
        String course=lesson.getString("course_id"),id=lesson.getString("id");
        if(!course.matches("[0-9]+")||!id.matches("[0-9]+"))throw new IOException("课堂编号无效");
        return course+"-"+id;
    }
    public static String syncId(JSONObject lesson) throws Exception {
        return "classroom_"+UUID.nameUUIDFromBytes(key(lesson).getBytes(StandardCharsets.UTF_8));
    }
    /** No remote image URLs, account identity, credentials or generation prompts leave the cache. */
    public static JSONObject portable(JSONObject summary,JSONObject materials) throws Exception {
        JSONObject result=new JSONObject().put("summary",new JSONObject(summary.toString()))
                .put("segments",new JSONArray(materials.getJSONArray("segments").toString()));
        validate(result);
        return result;
    }
    public static void validate(JSONObject document) throws Exception {
        JSONObject summary=document.getJSONObject("summary");
        if(!summary.getBoolean("complete"))throw new IOException("仅已完成的课堂总结参与同步");
        JSONObject lesson=summary.getJSONObject("lesson");key(lesson);
        for(String field:new String[]{"course","title","status"})lesson.getString(field);
        lesson.getLong("starts_at");summary.getLong("updated");summary.getString("overview");
        JSONArray segments=document.getJSONArray("segments");
        for(int i=0;i<segments.length();i++){
            JSONObject row=segments.getJSONObject(i);double start=row.getDouble("start"),end=row.getDouble("end");
            if(!Double.isFinite(start)||!Double.isFinite(end)||start<0||end<start)throw new IOException("字幕时间无效");
            row.getString("text");
        }
        JSONArray parts=summary.getJSONArray("parts");
        for(int p=0;p<parts.length();p++)for(String collection:new String[]{"notes","assignments"}){
            JSONArray rows=parts.getJSONObject(p).getJSONArray(collection);
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);row.getString("text");JSONArray refs=row.getJSONArray("sources");
                for(int n=0;n<refs.length();n++)if(!(refs.get(n) instanceof Integer)||refs.getInt(n)<0||refs.getInt(n)>=segments.length())throw new IOException("字幕引用无效");
            }
        }
        Set<String> inventory=new LinkedHashSet<>();JSONArray images=summary.getJSONArray("images");
        for(int i=0;i<images.length();i++)inventory.add(imageName(images.getJSONObject(i).getString("file")));
        if(!inventory.containsAll(referencedImages(summary)))throw new IOException("总结引用了未分析的图片");
    }
}
