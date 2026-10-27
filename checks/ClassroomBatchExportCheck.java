package com.donglan.chrona.classroom;

import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;

/** Real archive/store regression; local generated fixtures only, no account or network. */
public final class ClassroomBatchExportCheck {
    private static int checks;
    private static void require(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    private static ClassroomLesson lesson(String id){return new ClassroomLesson("41",id,"同名/课程","相同课堂","教室","6",1700000000000L);}
    private static JSONArray segments()throws Exception{return new JSONArray().put(new JSONObject().put("start",3).put("end",8).put("text","字幕 * 原文"));}
    private static byte[] image(String format)throws Exception{var image=new java.awt.image.BufferedImage(13,9,java.awt.image.BufferedImage.TYPE_INT_RGB);var out=new ByteArrayOutputStream();javax.imageio.ImageIO.write(image,format,out);return out.toByteArray();}
    private static File material(File root,ClassroomLesson lesson)throws Exception{
        File dir=new File(root,lesson.courseId+"-"+lesson.id);dir.mkdirs();
        ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("segments",segments()).put("images",new JSONArray().put(new JSONObject().put("cache_index",0)).put(new JSONObject().put("cache_index",2))));
        ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject().put("lesson",lesson.json()).put("images",new JSONArray()));
        Files.write(new File(dir,"raw-0.img").toPath(),image("png"));Files.write(new File(dir,"raw-2.img").toPath(),image("jpg"));return dir;
    }
    private static JSONObject summary(ClassroomLesson lesson)throws Exception{
        JSONObject note=new JSONObject().put("title","稀疏图片").put("text","正文\n![图](images/image-17.jpg)").put("sources",new JSONArray().put(0)).put("images",new JSONArray().put("images/image-17.jpg"));
        return new JSONObject().put("lesson",lesson.json()).put("complete",false).put("overview","概览").put("raw_images",20).put("candidate_images",19)
                .put("images",new JSONArray().put(new JSONObject().put("file","image-17.jpg").put("second",17)))
                .put("parts",new JSONArray().put(new JSONObject().put("notes",new JSONArray().put(note)).put("assignments",new JSONArray())));
    }
    private static LinkedHashMap<String,byte[]> unzip(byte[] bytes)throws Exception{
        LinkedHashMap<String,byte[]> entries=new LinkedHashMap<>();try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){ZipEntry entry;while((entry=zip.getNextEntry())!=null){require(!entries.containsKey(entry.getName()),"unique archive names");entries.put(entry.getName(),zip.readAllBytes());}}return entries;
    }
    private static ClassroomExport.ImageFormat format(){return file->{byte[] b=Files.readAllBytes(file.toPath());return b[0]==(byte)0x89?"png":"jpg";};}
    public static void main(String[] args)throws Exception{
        File root=new File(args[0]);root.mkdirs();var first=lesson("101");var second=lesson("102");
        File notes=material(root,first),raw=material(root,second);JSONObject summary=summary(first);ClassroomStore.write(new File(notes,"summary.json"),summary);
        byte[] original=image("png");Files.write(new File(notes,"image-17.jpg").toPath(),original);
        var lessons=List.of(new ClassroomExport.LessonFiles(first,notes),new ClassroomExport.LessonFiles(second,raw));
        ByteArrayOutputStream output=new ByteArrayOutputStream();var result=ClassroomExport.writeBatch(lessons,output,p->{},l->{},format());var entries=unzip(output.toByteArray());
        require(result.success==2&&result.failures.isEmpty(),"summary and downloaded-only export");
        require(entries.size()==6,"one summary, one referenced image, two raw PPTs, two readable transcripts");
        Set<String> directories=new HashSet<>();for(String name:entries.keySet()){directories.add(name.substring(0,name.indexOf('/')));require(!name.contains("../")&&!name.startsWith("/"),"safe names");require(!name.endsWith(".zip"),"no nested archives");require(!name.contains("materials.json")&&!name.contains("run.json"),"no internal cache or URLs");}
        require(directories.size()==2,"same-title lessons retain unique identity directories");
        String markdown=new String(entries.entrySet().stream().filter(e->e.getKey().endsWith("课堂总结.md")).findFirst().get().getValue(),StandardCharsets.UTF_8);
        require(markdown.contains("images/image-17.jpg")&&!markdown.contains("images/images/"),"prefixed sparse image normalizes once");
        require(markdown.indexOf("images/image-17.jpg")==markdown.lastIndexOf("images/image-17.jpg"),"inline image not duplicated by notes.images");
        require(markdown.contains("字幕草稿"),"draft remains explicitly incomplete");
        require(Arrays.equals(original,entries.entrySet().stream().filter(e->e.getKey().endsWith("images/image-17.jpg")).findFirst().get().getValue()),"summary image original bytes");
        require(entries.entrySet().stream().filter(e->e.getKey().endsWith("sources.md")).allMatch(e->new String(e.getValue(),StandardCharsets.UTF_8).contains("00:00:03 — 00:00:08")),"same readable time-anchor transcript for every lesson");
        require(Arrays.equals(Files.readAllBytes(new File(raw,"raw-0.img").toPath()),entries.entrySet().stream().filter(e->e.getKey().endsWith("PPT/0001-raw-0.png")).findFirst().get().getValue()),"PPT PNG preserves actual format and bytes");
        require(Arrays.equals(Files.readAllBytes(new File(raw,"raw-2.img").toPath()),entries.entrySet().stream().filter(e->e.getKey().endsWith("PPT/0002-raw-2.jpg")).findFirst().get().getValue()),"PPT JPEG preserves sparse cache index and bytes");
        ByteArrayOutputStream single=new ByteArrayOutputStream();ClassroomExport.write(notes,summary,single);var singleEntries=unzip(single.toByteArray());require(singleEntries.keySet().equals(new LinkedHashSet<>(List.of("课堂总结.md","sources.md","images/image-17.jpg"))),"single export filenames unchanged");
        Files.delete(new File(notes,"image-17.jpg").toPath());output.reset();result=ClassroomExport.writeBatch(lessons,output,p->{},l->{},format());entries=unzip(output.toByteArray());
        require(result.success==1&&result.failures.size()==1,"missing referenced image produces per-lesson failure");require(entries.keySet().stream().noneMatch(n->n.endsWith("课堂总结.md")),"missing image does not publish incomplete notes");require(entries.containsKey("导出结果.txt"),"partial export includes failure report");
        ClassroomStore.begin(raw);output.reset();result=ClassroomExport.writeBatch(List.of(new ClassroomExport.LessonFiles(second,raw)),output,p->{},l->{},format());require(result.success==0&&result.failures.size()==1,"active local lease skipped");require(ClassroomStore.active(raw),"another owner's lease remains active");ClassroomStore.end(raw);
        output.reset();result=ClassroomExport.writeBatch(lessons,output,p->{},l->{throw new IOException("学校账号已停用或更换");},format());require(result.success==0&&result.failures.size()==2,"account gate checked per lesson");
        boolean failed=false;try{ClassroomExport.writeBatch(List.of(new ClassroomExport.LessonFiles(second,raw)),new OutputStream(){public void write(int b)throws IOException{throw new IOException("disk full");}},p->{},l->{},format());}catch(IOException expected){failed=true;}require(failed,"output failure never reports partial success");
        File accountRoot=new File(root,"deletion");var store=new ClassroomStore(new android.content.Context(accountRoot),"account");File downloaded=store.localLesson(second);material(downloaded.getParentFile(),second);int[] callbacks={0};store.deleteLesson(second,()->callbacks[0]++);require(!downloaded.exists()&&callbacks[0]==1,"downloaded-only deletion uses locked beforeDelete callback");
        File active=store.localLesson(second);material(active.getParentFile(),second);ClassroomStore.begin(active);failed=false;try{store.deleteLesson(second,()->callbacks[0]++);}catch(IOException expected){failed=true;}ClassroomStore.end(active);require(failed&&active.exists()&&callbacks[0]==1,"active deletion preserves data and does not mark monitor deleted");
        System.out.println("ClassroomBatchExportCheck PASS: "+checks+" checks; mixed archive, sparse prefixed image, real bytes, partial failure, account/active/output gates, downloaded-only deletion");
    }
}
