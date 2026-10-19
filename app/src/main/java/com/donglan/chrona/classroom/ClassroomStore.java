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
    private final File root;
    public ClassroomStore(Context context,String account) throws IOException {
        if(!account.matches("[a-zA-Z0-9-]+"))throw new IOException("请先登录学校账号");
        root=new File(context.getNoBackupFilesDir(),"classroom/"+account);
        if(!root.isDirectory()&&!root.mkdirs())throw new IOException("无法创建课堂缓存");
    }
    public File lesson(ClassroomLesson lesson) throws IOException {
        if(!lesson.courseId.matches("[0-9]+")||!lesson.id.matches("[0-9]+"))throw new IOException("课堂编号无效");
        File dir=new File(root,lesson.courseId+"-"+lesson.id);
        if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("无法创建课堂缓存");
        return dir;
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
    public List<JSONObject> history() throws IOException {
        List<JSONObject> entries=new ArrayList<>(); File[] dirs=root.listFiles(File::isDirectory);
        if(dirs!=null)for(File dir:dirs){JSONObject value=read(new File(dir,"summary.json"));if(value!=null)entries.add(value);}
        entries.sort((a,b)->Long.compare(b.optLong("updated"),a.optLong("updated")));return entries;
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
