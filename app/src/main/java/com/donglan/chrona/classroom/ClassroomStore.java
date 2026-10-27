package com.donglan.chrona.classroom;

import android.content.Context;
import android.util.AtomicFile;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/** Account-isolated local study material. Atomic checkpoints survive interruption. */
public final class ClassroomStore {
    public static final Object LOCK=new Object();
    private static final java.util.Set<String> ACTIVE=new java.util.HashSet<>();
    public static final long UNUSED_RETENTION_MILLIS=7L*24*60*60*1000;
    private final File root;
    private final File shared;
    public ClassroomStore(Context context,String account) throws IOException {
        if(!account.matches("[a-zA-Z0-9-]+"))throw new IOException("请先登录学校账号");
        root=new File(root(context),account);
        shared=new File(root(context),"shared");
        if(Files.isSymbolicLink(root.toPath())||Files.isSymbolicLink(shared.toPath())
                ||!root.getCanonicalFile().getParentFile().equals(root(context).getCanonicalFile()))throw new IOException("课堂目录无效");
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("无法创建课堂缓存");
    }
    public File lesson(ClassroomLesson lesson) throws IOException {
        if(!lesson.courseId.matches("[0-9]+")||!lesson.id.matches("[0-9]+"))throw new IOException("课堂编号无效");
        File dir=new File(root,lesson.courseId+"-"+lesson.id);
        File imported=new File(shared,dir.getName());
        if(!new File(dir,"summary.json").isFile()&&new File(imported,"summary.json").isFile())return imported;
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建课堂缓存");
        return dir;
    }
    /** Monitoring writes stay in this account, never in a restored/shared summary directory. */
    public File localLesson(ClassroomLesson lesson) throws IOException {
        if(!lesson.courseId.matches("[0-9]+")||!lesson.id.matches("[0-9]+"))throw new IOException("课堂编号无效");
        File dir=new File(root,lesson.courseId+"-"+lesson.id);
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建课堂缓存");
        return dir;
    }
    /** Resolve only an existing local raw image; never a portable/shared import or arbitrary path. */
    public File localImage(String courseId,String lessonId,String raw)throws IOException {
        if(!courseId.matches("[0-9]+")||!lessonId.matches("[0-9]+")||!raw.matches("raw-[0-9]+\\.img"))throw new IOException("课堂图片无效");
        File image=new File(new File(root,courseId+"-"+lessonId),raw);
        if(!image.isFile())throw new IOException("图片已不存在");return image;
    }
    /** Published images are readable only while this exact summary still references them. */
    public File summaryImage(ClassroomLesson lesson,String name)throws Exception {
        name=ClassroomDocument.imageName(name);
        if(!lesson.courseId.matches("[0-9]+")||!lesson.id.matches("[0-9]+"))throw new IOException("课堂编号无效");
        File dir=new File(root,lesson.courseId+"-"+lesson.id),imported=new File(shared,dir.getName());
        if(!new File(dir,"summary.json").isFile()&&new File(imported,"summary.json").isFile())dir=imported;
        JSONObject summary=read(new File(dir,"summary.json"));
        File parent=dir.getCanonicalFile().getParentFile();
        if(Files.isSymbolicLink(dir.toPath())||!parent.equals(root.getCanonicalFile())&&!parent.equals(shared.getCanonicalFile()))throw new IOException("课堂目录无效");
        if(summary==null||!ClassroomDocument.key(summary.getJSONObject("lesson")).equals(lesson.courseId+"-"+lesson.id)
                ||!ClassroomDocument.referencedImages(summary).contains(name))throw new IOException("总结图片已不可用");
        File file=new File(dir,name);
        if(!file.isFile()||Files.isSymbolicLink(file.toPath())||!file.getCanonicalFile().getParentFile().equals(dir.getCanonicalFile()))throw new IOException("图片已不存在");
        return file;
    }
    @FunctionalInterface public interface BeforeDelete {void run()throws Exception;}
    /** Delete only this lesson's local copies; never the catalogue or another account. */
    public void deleteLesson(ClassroomLesson lesson,BeforeDelete beforeDelete)throws Exception {
        if(!lesson.courseId.matches("[0-9]+")||!lesson.id.matches("[0-9]+"))throw new IOException("课堂编号无效");
        synchronized(LOCK){
            File local=new File(root,lesson.courseId+"-"+lesson.id),imported=new File(shared,local.getName());
            java.util.List<File> targets=new ArrayList<>();
            for(File parent:new File[]{root,shared})for(String suffix:new String[]{".previous",".incoming",""}){
                File target=new File(parent,local.getName()+suffix);
                if(!target.getCanonicalFile().getParentFile().equals(parent.getCanonicalFile())||Files.isSymbolicLink(target.toPath()))throw new IOException("课堂目录无效");
                if(active(target))throw new IOException("本节课堂正在处理，请先停止后再删除");
                if(target.exists()){validateDeleteTree(target,target.getCanonicalFile());targets.add(target);}
            }
            if(!new File(local,"summary.json").isFile()&&!new File(imported,"summary.json").isFile())throw new IOException("本节课堂尚无本地总结");
            beforeDelete.run();
            for(File target:targets)deleteTree(target);
        }
    }
    private static void validateDeleteTree(File file,File boundary)throws IOException {
        File canonical=file.getCanonicalFile();
        if(Files.isSymbolicLink(file.toPath())||!canonical.toPath().startsWith(boundary.toPath()))throw new IOException("课堂目录包含无效引用");
        if(file.isDirectory()){File[] children=file.listFiles();if(children==null)throw new IOException("课堂目录无法读取");for(File child:children)validateDeleteTree(child,boundary);}
    }
    private static void deleteTree(File file)throws IOException {
        if(file.isDirectory()){File[] children=file.listFiles();if(children==null)throw new IOException("课堂目录无法读取");
            // Keep the readable summary until all other content has been removed, so a
            // filesystem failure remains visible and the same confirmed action can retry.
            java.util.Arrays.sort(children,java.util.Comparator.comparing(child->child.getName().startsWith("summary.json")));
            for(File child:children)deleteTree(child);}
        if(!file.delete())throw new IOException("部分本地课堂资料无法删除，请重试");
    }
    public List<ClassroomLesson> catalog() throws IOException {
        JSONObject value=read(new File(root,"catalog.json"));List<ClassroomLesson> result=new ArrayList<>();
        if(value==null)return result;
        try { JSONArray rows=value.getJSONArray("lessons");for(int i=0;i<rows.length();i++)result.add(ClassroomLesson.fromJson(rows.getJSONObject(i))); }
        catch(Exception e){throw new IOException("课堂目录缓存无法读取，请刷新",e);}return result;
    }
    public void catalog(List<ClassroomLesson> lessons) throws Exception {
        JSONArray rows=new JSONArray();for(ClassroomLesson lesson:lessons)rows.put(lesson.json());
        write(new File(root,"catalog.json"),new JSONObject().put("lessons",rows));
    }
    public long catalogUpdatedAt() { return new File(root,"catalog.json").lastModified(); }
    public List<JSONObject> history() throws IOException {
        java.util.Map<String,JSONObject> byLesson=new java.util.LinkedHashMap<>();
        java.util.Map<String,ClassroomLesson> known=new java.util.HashMap<>();
        for(ClassroomLesson lesson:catalog())known.put(lesson.courseId+"-"+lesson.id,lesson);
        for(File source:new File[]{shared,root}){File[] dirs=source.listFiles(File::isDirectory);
            if(dirs!=null)for(File dir:dirs){
                JSONObject value=read(new File(dir,"summary.json"));
                if(value!=null){byLesson.put(dir.getName(),value);continue;}
                if(source.equals(shared)||byLesson.containsKey(dir.getName()))continue;
                JSONObject materials=read(new File(dir,"materials.json")), candidates=read(new File(dir,"candidates.json"));
                if(materials==null||materials.optBoolean("monitoring")||materials.optJSONArray("segments")==null||materials.optJSONArray("segments").length()==0
                        ||candidates==null||candidates.optJSONArray("images")==null)continue;
                JSONObject lesson=candidates.optJSONObject("lesson");
                if(lesson==null&&known.containsKey(dir.getName()))try{lesson=known.get(dir.getName()).json();}
                    catch(org.json.JSONException invalid){throw new IOException("课堂记录无法读取",invalid);}
                if(lesson==null)continue;
                try{byLesson.put(dir.getName(),new JSONObject().put("lesson",lesson).put("downloaded",true)
                        .put("updated",candidates.optLong("updated",new File(dir,"candidates.json").lastModified())));}
                catch(org.json.JSONException invalid){throw new IOException("课堂记录无法读取",invalid);}
            }}
        List<JSONObject> entries=new ArrayList<>(byLesson.values());
        entries.sort((a,b)->Long.compare(b.optLong("updated"),a.optLong("updated")));return entries;
    }
    public static void begin(File dir) throws IOException {
        synchronized(LOCK){if(!dir.isDirectory())throw new IOException("本节本地课堂已删除，请重新打开");if(!ACTIVE.add(dir.getCanonicalPath()))throw new IOException("本节课堂正在处理");}
    }
    public static void end(File dir){synchronized(LOCK){try{ACTIVE.remove(dir.getCanonicalPath());}catch(IOException ignored){}}}
    public static boolean active(File dir)throws IOException{synchronized(LOCK){return ACTIVE.contains(dir.getCanonicalPath());}}
    public static boolean anyActive(){synchronized(LOCK){return !ACTIVE.isEmpty();}}
    public static File root(Context context){synchronized(LOCK){File root=new File(context.getNoBackupFilesDir(),"classroom"),previous=new File(context.getNoBackupFilesDir(),"classroom.previous");
        if(!root.exists()&&previous.exists()&&!previous.renameTo(root))throw new IllegalStateException("课堂恢复副本无法还原");return root;}}
    public static List<File> lessonDirectories(Context context){
        List<File> result=new ArrayList<>();File[] accounts=root(context).listFiles(File::isDirectory);
        if(accounts!=null)for(File account:accounts){File[] lessons=account.listFiles(File::isDirectory);
            if(lessons!=null)for(File lesson:lessons)if(lesson.getName().matches("[0-9]+-[0-9]+\\.previous")){
                File original=new File(account,lesson.getName().replace(".previous",""));if(!original.exists())lesson.renameTo(original);
            }
            lessons=account.listFiles(File::isDirectory);if(lessons!=null)for(File lesson:lessons)if(lesson.getName().matches("[0-9]+-[0-9]+"))result.add(lesson);}
        return result;
    }
    /** Only finished, inactive lessons; the original transcript and referenced JPEGs never enter this delete set. */
    public static long cleanUnused(Context context,boolean all,long now) throws Exception {
        synchronized(LOCK){long bytes=0;
            for(File dir:lessonDirectories(context)){
                if(Thread.currentThread().isInterrupted())throw new java.io.InterruptedIOException("清理已停止");
                if(active(dir))continue;
                JSONObject summary=read(new File(dir,"summary.json"));
                if(summary==null||!summary.optBoolean("complete"))continue;
                JSONObject run=read(new File(dir,"run.json"));
                // An interrupted replacement can coexist with the previous finished summary.
                if(run!=null&&(!run.has("final")||!run.optString("run").equals(summary.optString("run"))))continue;
                if(!all&&(summary.optLong("updated",now)>now-UNUSED_RETENTION_MILLIS))continue;
                java.util.Set<String> used=ClassroomDocument.referencedImages(summary);
                // Check every used asset before deleting anything from this lesson.
                for(String name:used)if(!new File(dir,name).isFile())throw new IOException("总结引用的图片缺失，已保留该课堂缓存");
                File[] files=dir.listFiles(File::isFile);boolean removedRaw=false;
                if(files!=null)for(File file:files){String name=file.getName();
                    boolean raw=name.matches("raw-[0-9]+\\.img(?:\\.validating)?");
                    boolean unused=name.matches("image-[0-9]+\\.jpg")&&!used.contains(name);
                    if(raw||unused){long size=file.length();if(!file.delete())throw new IOException("部分课堂截图无法清理，请稍后重试");bytes+=size;removedRaw|=raw;}
                }
                if(removedRaw){
                    // A fresh preparation rebuilds candidates from the retained source manifest.
                    // Completed run metadata is stale once its selected raw files are removed.
                    new AtomicFile(new File(dir,"candidates.json")).delete();
                    new AtomicFile(new File(dir,"run.json")).delete();
                }
            }
            return bytes;
        }
    }
    public static JSONObject read(File file) throws IOException {
        AtomicFile atomic=new AtomicFile(file);
        if(!file.exists()&&!new File(file+".bak").exists())return null;
        try { return new JSONObject(new String(atomic.readFully(),StandardCharsets.UTF_8)); }
        catch(Exception e){throw new IOException("课堂缓存无法读取",e);}
    }
    public static void write(File file,JSONObject value) throws IOException { writeBytes(file,value.toString().getBytes(StandardCharsets.UTF_8)); }
    public static void writeBytes(File file,byte[] bytes) throws IOException {
        AtomicFile atomic=new AtomicFile(file);FileOutputStream stream=null;
        try{stream=atomic.startWrite();stream.write(bytes);atomic.finishWrite(stream);}
        catch(IOException e){if(stream!=null)atomic.failWrite(stream);throw e;}
    }
    public static byte[] bytes(File file) throws IOException { return Files.readAllBytes(file.toPath()); }
}
