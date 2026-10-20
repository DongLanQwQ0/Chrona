package com.donglan.chrona.classroom;

import android.content.Context;
import org.json.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

/** Completed study documents shared by backup and sync. Source-download checkpoints stay local. */
public final class ClassroomArchive {
    private ClassroomArchive() { }
    public static final class Entry {
        public final File directory;
        public final JSONObject document;
        Entry(File directory,JSONObject document){this.directory=directory;this.document=document;}
    }
    public static Map<String,Entry> snapshot(Context context)throws Exception {
        synchronized(ClassroomStore.LOCK){
            Map<String,Entry> entries=new TreeMap<>();Set<String> busy=new HashSet<>();
            for(File dir:ClassroomStore.lessonDirectories(context)){
                if(ClassroomStore.active(dir)){busy.add(dir.getName());continue;}
                JSONObject summary=ClassroomStore.read(new File(dir,"summary.json"));
                if(summary==null||!summary.optBoolean("complete"))continue;
                JSONObject materials=ClassroomStore.read(new File(dir,"materials.json"));
                if(materials==null)throw new IOException("课堂字幕缺失，无法保存完整总结");
                JSONObject document=ClassroomDocument.portable(summary,materials);
                String key=ClassroomDocument.key(summary.getJSONObject("lesson"));
                if(!dir.getName().equals(key))throw new IOException("课堂缓存身份不一致");
                Entry previous=entries.get(key);
                if(previous==null||previous.document.getJSONObject("summary").getLong("updated")<summary.getLong("updated"))entries.put(key,new Entry(dir,document));
            }
            for(String key:busy)entries.remove(key);
            return entries;
        }
    }
    public static boolean busy(Context context,String key)throws Exception {
        synchronized(ClassroomStore.LOCK){for(File dir:ClassroomStore.lessonDirectories(context))if(dir.getName().equals(key)){
            if(ClassroomStore.active(dir))return true;
            JSONObject summary=ClassroomStore.read(new File(dir,"summary.json")),run=ClassroomStore.read(new File(dir,"run.json"));
            if(summary!=null&&!summary.optBoolean("complete"))return true;
            if(run!=null&&(!run.has("final")||summary==null||!run.optString("run").equals(summary.optString("run"))))return true;
        }return false;}
    }
    /** Write an isolated portable lesson directory; used for staging both restore and sync. */
    public static void write(File directory,JSONObject document,Map<String,File> images)throws Exception {
        ClassroomDocument.validate(document);
        if(!directory.isDirectory()&&!directory.mkdirs())throw new IOException("无法创建课堂文档目录");
        JSONObject summary=document.getJSONObject("summary");Set<String> required=ClassroomDocument.referencedImages(summary);
        if(!images.keySet().equals(required))throw new IOException("课堂图片清单与正文引用不一致");
        for(String name:required){File source=images.get(name);if(source==null||!source.isFile())throw new IOException("课堂引用图片缺失");Files.copy(source.toPath(),new File(directory,name).toPath(),StandardCopyOption.REPLACE_EXISTING);}
        ClassroomStore.write(new File(directory,"materials.json"),new JSONObject().put("segments",document.getJSONArray("segments"))
                .put("images",new JSONArray()).put("raw_images",summary.getInt("raw_images")).put("portable",true));
        ClassroomStore.write(new File(directory,"summary.json"),summary);
    }
    public static Map<String,File> images(Entry entry)throws Exception {
        Map<String,File> result=new TreeMap<>();for(String name:ClassroomDocument.referencedImages(entry.document.getJSONObject("summary")))result.put(name,new File(entry.directory,name));return result;
    }
    public static void install(Context context,JSONObject document,Map<String,File> images)throws Exception {
        synchronized(ClassroomStore.LOCK){
            String key=ClassroomDocument.key(document.getJSONObject("summary").getJSONObject("lesson"));
            if(busy(context,key))throw new IOException("课堂正在整理或等待续跑，请完成后重新同步");
            List<File> targets=new ArrayList<>();for(File dir:ClassroomStore.lessonDirectories(context))if(dir.getName().equals(key))targets.add(dir);
            File shared=new File(ClassroomStore.root(context),"shared/"+key);if(!targets.contains(shared))targets.add(shared);
            List<File> installed=new ArrayList<>(),backups=new ArrayList<>(),stages=new ArrayList<>();
            try{
                for(File target:targets){File stage=new File(target.getParentFile(),key+".incoming");remove(stage);write(stage,document,images);stages.add(stage);}
                for(int i=0;i<targets.size();i++){
                    File target=targets.get(i),backup=new File(target.getParentFile(),key+".previous");remove(backup);
                    if(target.exists()&&!target.renameTo(backup))throw new IOException("无法保留课堂原文档");backups.add(backup);
                    if(!stages.get(i).renameTo(target))throw new IOException("无法安装课堂文档");installed.add(target);
                }
            }catch(Exception failure){
                for(File target:installed)remove(target);
                for(int i=0;i<backups.size();i++)if(backups.get(i).exists()&&!backups.get(i).renameTo(targets.get(i)))failure.addSuppressed(new IOException("课堂原文档恢复失败"));
                throw failure;
            }finally{for(File stage:stages)remove(stage);}
            for(File backup:backups)remove(backup);
        }
    }
    public static void remove(File file)throws IOException {
        if(!file.exists())return;File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);
        if(!file.delete())throw new IOException("课堂临时文档无法移除");
    }
}
