package com.donglan.chrona.classroom;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A portable Markdown document with relative image and transcript references. */
public final class ClassroomExport {
    private ClassroomExport() { }
    public static String time(double seconds) {
        long s=(long)Math.max(0,seconds);return String.format(Locale.ROOT,"%02d:%02d:%02d",s/3600,(s/60)%60,s%60);
    }
    public static void write(File dir,JSONObject summary,OutputStream output) throws Exception {
        write(dir,summary,output,value->{});
    }
    public static void write(File dir,JSONObject summary,OutputStream output,ClassroomImages.Progress progress) throws Exception {
        progress.update(ClassroomProgress.waiting("生成 Markdown 文档","整理笔记、字幕来源与图片引用"));
        JSONObject materials=ClassroomStore.read(new File(dir,"materials.json"));
        if(materials==null)throw new IOException("字幕缓存不存在，无法导出来源");
        JSONArray segments=materials.getJSONArray("segments");ClassroomLesson lesson=ClassroomLesson.fromJson(summary.getJSONObject("lesson"));
        Set<String> images=new LinkedHashSet<>();StringBuilder md=new StringBuilder("# ").append(escape(lesson.course)).append("\n\n").append(escape(lesson.title)).append("\n\n");
        if(!summary.optBoolean("complete"))md.append("**字幕草稿 · 图片分析尚未完成**\n\n");
        md.append("## 重点概览\n\n").append(escape(summary.optString("overview"))).append("\n\n");
        JSONArray parts=summary.getJSONArray("parts");
        for(int p=0;p<parts.length();p++) {
            JSONObject part=parts.getJSONObject(p);
            JSONArray notes=part.getJSONArray("notes");
            for(int i=0;i<notes.length();i++) {
                JSONObject note=notes.getJSONObject(i);md.append("## ").append(escape(note.getString("title"))).append("\n\n").append(escape(note.getString("text"))).append("\n\n");
                refs(md,note.getJSONArray("sources"),segments);
                JSONArray pics=note.getJSONArray("images");
                for(int n=0;n<pics.length();n++){String file=safeImage(pics.getString(n));images.add(file);md.append("![课堂截图](images/").append(file).append(")\n\n");}
            }
        }
        md.append("## 课堂安排\n\n");
        for(int p=0;p<parts.length();p++){JSONArray tasks=parts.getJSONObject(p).getJSONArray("assignments");
            for(int i=0;i<tasks.length();i++){JSONObject task=tasks.getJSONObject(i);md.append("- ").append(escape(task.getString("text"))).append("\n\n");refs(md,task.getJSONArray("sources"),segments);}}
        // Include every analysed image, including those not cited in a particular topic.
        md.append("## 已分析图片\n\n");JSONArray selected=summary.getJSONArray("images");
        for(int i=0;i<selected.length();i++){JSONObject image=selected.getJSONObject(i);String file=safeImage(image.getString("file"));
            if(images.add(file))md.append("### ").append(time(image.getDouble("second"))).append("\n\n![课堂截图](images/").append(file).append(")\n\n");}
        md.append("原始截图 ").append(summary.getInt("raw_images")).append(" 张；去重候选 ").append(summary.getInt("candidate_images"))
                .append(" 张；分析 ").append(selected.length()).append(" 张。\n");
        StringBuilder transcript=new StringBuilder("# 字幕来源\n\n");
        for(int i=0;i<segments.length();i++){JSONObject s=segments.getJSONObject(i);transcript.append("<a id=\"s").append(i).append("\"></a>\n\n### ")
                .append(time(s.getDouble("start"))).append(" — ").append(time(s.getDouble("end"))).append("\n\n").append(escape(s.getString("text"))).append("\n\n");}
        try(ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)) {
            int total=images.size()+2,done=0;
            progress.update(new ClassroomProgress("写入 ZIP",done,total,"课堂总结.md"));
            entry(zip,"课堂总结.md",md.toString().getBytes(StandardCharsets.UTF_8));done++;
            progress.update(new ClassroomProgress("写入 ZIP",done,total,"sources.md"));
            entry(zip,"sources.md",transcript.toString().getBytes(StandardCharsets.UTF_8));done++;
            for(String image:images){progress.update(new ClassroomProgress("写入 ZIP",done,total,image));entry(zip,"images/"+image,ClassroomStore.bytes(new File(dir,image)));done++;}
            progress.update(new ClassroomProgress("ZIP 内容已写入",done,total,images.size()+" 张图片"));
        }
    }
    private static void refs(StringBuilder md,JSONArray refs,JSONArray segments) throws Exception {
        md.append("来源：");for(int i=0;i<refs.length();i++){int id=refs.getInt(i);md.append("[").append(time(segments.getJSONObject(id).getDouble("start"))).append("](sources.md#s").append(id).append(") ");}md.append("\n\n");
    }
    public static String safeImage(String name) throws IOException { if(!name.matches("image-[0-9]+\\.jpg"))throw new IOException("图片文件名无效");return name; }
    private static String escape(String text) {return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\\","\\\\").replace("*","\\*").replace("_","\\_").replace("[","\\[").replace("]","\\]").replace("#","\\#").replace("`","\\`");}
    private static void entry(ZipOutputStream zip,String name,byte[] bytes) throws IOException {zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();}
}
