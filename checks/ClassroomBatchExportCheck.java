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
    private static ClassroomLesson dated(String courseId,String id,String course,String title,int day){
        long at=java.time.LocalDate.of(2026,9,day).atTime(14,15).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli();
        return new ClassroomLesson(courseId,id,course,title,"教室","6",at);
    }
    private static void periodEnds(){
        var ends=new ArrayList<>(List.of("08:45","09:35","10:45","11:35","13:45","14:35","15:45","16:35"));
        long last=java.time.LocalDate.of(2026,9,23).atTime(16,35).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli();
        for(String separator:new String[]{"-","—","–","－","~","～","至"}){
            var lesson=dated("1","2","课程","2026-09-23 第 7 "+separator+" 8 节",23);
            require(ClassroomExport.periodEnd(lesson,ends)==last,"final period uses configured end for separator "+separator);
        }
        var single=dated("1","2","课程","2026-09-23第8节",23);
        require(ClassroomExport.periodEnd(single,ends)==last,"single period uses its own configured end");
        long midnight=java.time.LocalDate.of(2026,9,23).atTime(0,5).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli();
        require(ClassroomExport.periodEnd(new ClassroomLesson("1","2","课程","第8节","","6",midnight),ends)==last,"period timestamp uses Shanghai date even across the UTC date boundary");
        require(ClassroomExport.periodEnd(dated("1","2","课程","第8节",30),ends)==last+7*24*60*60000L,"each recording retains its own calendar date");
        ends.set(7,"18:17");
        require(ClassroomExport.periodEnd(single,ends)==java.time.LocalDate.of(2026,9,23).atTime(18,17).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli(),"edited period configuration changes timestamp without a fixed duration");
        for(String title:new String[]{"无节次","第0节","第9节","第8-7节","第999999999999999999999节"})
            require(ClassroomExport.periodEnd(dated("1","2","课程",title,23),ends)==0,"missing or invalid period does not invent a timestamp: "+title);
        require(ClassroomExport.periodEnd(new ClassroomLesson("1","2","课程","第8节","","6",0),ends)==0,"missing lesson date does not fabricate an epoch date");
        require(ClassroomExport.periodEnd(single,List.of())==0,"empty configuration does not invent a period end");
        ends.set(7,"invalid");require(ClassroomExport.periodEnd(single,ends)==0,"invalid configured clock does not invent an end");
    }
    private static void requireTime(ZipEntry entry,Long expected,String reader){
        require(expected!=null,"every "+reader+" entry has an expected timestamp: "+entry.getName());
        if(expected==0){
            require(entry.getCreationTime()==null,"unknown lesson end and package report keep default creation metadata: "+reader);
            require(entry.getLastModifiedTime()!=null&&entry.getLastModifiedTime().toMillis()>0,"unknown lesson end and package report retain normal ZIP modified time: "+reader);return;
        }
        require(entry.getCreationTime()!=null&&entry.getCreationTime().toMillis()==expected,"creation time preserves lesson end including odd second/millisecond: "+reader+" "+entry.getName());
        require(entry.getLastModifiedTime()!=null&&entry.getLastModifiedTime().toMillis()==expected,"modified time preserves lesson end including odd second/millisecond: "+reader+" "+entry.getName());
    }
    private static void requireTimes(File file,byte[] bytes,Map<String,Long> expected)throws Exception{
        Files.write(file.toPath(),bytes);
        try(ZipFile zip=new ZipFile(file,StandardCharsets.UTF_8)){
            require(zip.size()==expected.size(),"central directory includes every expected file");
            var entries=zip.entries();while(entries.hasMoreElements()){ZipEntry entry=entries.nextElement();requireTime(entry,expected.get(entry.getName()),"central");}
        }
        int count=0;try(ZipInputStream zip=new ZipInputStream(new ByteArrayInputStream(bytes),StandardCharsets.UTF_8)){
            ZipEntry entry;while((entry=zip.getNextEntry())!=null){requireTime(entry,expected.get(entry.getName()),"local");count++;}
        }require(count==expected.size(),"local headers include every expected file");
    }
    private static void timestamps(File root)throws Exception{
        root.mkdirs();
        var first=dated("201","301","程序设计","2026-09-23第7-8节",23);
        var second=dated("202","302","微积分","2026-09-30第3-4节",30);
        long firstEnd=first.startsAt+95*60000+1123,secondEnd=second.startsAt+95*60000+3987;
        File notes=material(root,first),raw=material(root,second);JSONObject summary=summary(first).put("corrected_transcript","更正后的完整文稿");
        ClassroomStore.write(new File(notes,"summary.json"),summary);Files.write(new File(notes,"image-17.jpg").toPath(),image("png"));
        var lessons=List.of(new ClassroomExport.LessonFiles(first,notes,firstEnd),new ClassroomExport.LessonFiles(second,raw,secondEnd));
        for(var layout:ClassroomExport.Layout.values()){
            var output=new ByteArrayOutputStream();var result=ClassroomExport.writeBatch(lessons,layout,output,p->{},l->{},format());
            require(result.success==2&&result.failures.isEmpty(),"dated summary and raw assets export in "+layout);
            var expected=new LinkedHashMap<String,Long>();
            for(String entry:unzip(output.toByteArray()).keySet())expected.put(entry,entry.startsWith("程序设计")?firstEnd:secondEnd);
            require(expected.size()==7,"timed entries cover summary, original/corrected transcript, images and raw PPT");
            requireTimes(new File(root,layout.name().toLowerCase(Locale.ROOT)+"-times.zip"),output.toByteArray(),expected);
        }
        var single=new ByteArrayOutputStream();ClassroomExport.write(notes,summary,single,p->{},firstEnd);
        var expected=new LinkedHashMap<String,Long>();for(String entry:unzip(single.toByteArray()).keySet())expected.put(entry,firstEnd);
        requireTimes(new File(root,"single-times.zip"),single.toByteArray(),expected);
        single.reset();ClassroomExport.write(notes,summary,single);expected.replaceAll((name,time)->0L);
        requireTimes(new File(root,"single-unknown-end.zip"),single.toByteArray(),expected);
        for(long unknown:new long[]{0,first.startsAt,-1}){
            single.reset();var item=unknown==0?new ClassroomExport.LessonFiles(first,notes):new ClassroomExport.LessonFiles(first,notes,unknown);
            var result=ClassroomExport.writeBatch(List.of(item),ClassroomExport.Layout.FLAT,single,p->{},l->{},format());
            require(result.success==1&&result.failures.isEmpty(),"unknown or invalid end preserves existing export availability");
            var unknownExpected=new LinkedHashMap<String,Long>();for(String entry:unzip(single.toByteArray()).keySet())unknownExpected.put(entry,0L);
            requireTimes(new File(root,"batch-unknown-"+unknown+".zip"),single.toByteArray(),unknownExpected);
        }
        Files.delete(new File(notes,"image-17.jpg").toPath());single.reset();
        var result=ClassroomExport.writeBatch(lessons,ClassroomExport.Layout.FLAT,single,p->{},l->{},format());
        require(result.success==1&&result.failures.size()==1,"timed partial export retains per-lesson failure");
        var partialExpected=new LinkedHashMap<String,Long>();for(String entry:unzip(single.toByteArray()).keySet())partialExpected.put(entry,entry.equals("导出结果.txt")?0L:secondEnd);
        requireTimes(new File(root,"partial-times.zip"),single.toByteArray(),partialExpected);
    }
    private static void layouts(File root)throws Exception{
        var first=dated("87476","1972361","C程序设计基础","2026-09-23第7-8节",23);
        require(ClassroomExport.fileName(first).equals("C程序设计基础-09-23-第7-8节-2026.zip"),"single ZIP has course, month, day, periods, year with no repeated date or internal ID");
        var duplicate=dated("87476","1972362","C程序设计基础","2026-09-23第7-8节",23);
        var next=dated("87476","1972363","C程序设计基础","2026-09-30第7-8节",30);
        var otherCourse=dated("87477","1972364","C程序设计基础","2026-09-23第7-8节",23);
        var math=dated("87478","1972365","微积分","2026-09-23第3-4节",23);
        var chosen=new ArrayList<ClassroomExport.LessonFiles>();
        for(var lesson:List.of(first,duplicate,next,otherCourse,math))chosen.add(new ClassroomExport.LessonFiles(lesson,material(root,lesson)));
        chosen.add(chosen.get(0));
        for(var layout:ClassroomExport.Layout.values()){
            var out=new ByteArrayOutputStream();var result=ClassroomExport.writeBatch(chosen,layout,out,p->{},l->{},format());var entries=unzip(out.toByteArray());
            require(result.success==5&&result.failures.isEmpty(),"every identity exports once in "+layout);
            String firstPath=layout==ClassroomExport.Layout.FLAT?"C程序设计基础-09-23-第7-8节-2026/":"C程序设计基础/09-23-第7-8节-2026/";
            String duplicatePath=layout==ClassroomExport.Layout.FLAT?"C程序设计基础-09-23-第7-8节-2026 (2)/":"C程序设计基础/09-23-第7-8节-2026 (2)/";
            String otherPath=layout==ClassroomExport.Layout.FLAT?"C程序设计基础-09-23-第7-8节-2026 (3)/":"C程序设计基础 (2)/09-23-第7-8节-2026/";
            String nextPath=layout==ClassroomExport.Layout.FLAT?"C程序设计基础-09-30-第7-8节-2026/":"C程序设计基础/09-30-第7-8节-2026/";
            require(entries.containsKey(firstPath+"sources.md")&&entries.containsKey(duplicatePath+"sources.md")&&entries.containsKey(otherPath+"sources.md")&&entries.containsKey(nextPath+"sources.md"),"course grouping, lesson collisions, same-name distinct course identities and date order: "+layout);
            require(entries.keySet().stream().noneMatch(n->n.contains("87476")||n.contains("1972361")||n.contains("2026-09-23")),"internal IDs and duplicate ISO date do not leak into directory names");
            require(Arrays.equals(entries.get(firstPath+"PPT/0001-raw-0.png"),Files.readAllBytes(new File(chosen.get(0).dir,"raw-0.img").toPath())),"layout choice preserves raw asset bytes");
            Files.write(new File(root,layout.name().toLowerCase(Locale.ROOT)+".zip").toPath(),out.toByteArray());
        }
        var noPeriods=dated("80","81","微积分","2026-09-23-绪论",23);
        require(ClassroomExport.fileName(noPeriods).equals("微积分-09-23-绪论-2026.zip"),"missing period keeps meaningful title instead of inventing a period");
        require(ClassroomExport.fileName(new ClassroomLesson("80","82","课程","","","6",0)).equals("课程-日期未定-课堂.zip"),"unknown start does not fabricate an epoch date");
        var reserved=dated("90","91","CON","2026-09-23第7—8节",23);var caseTwin=dated("92","93","con","2026-09-23第7-8节",23);
        var out=new ByteArrayOutputStream();ClassroomExport.writeBatch(List.of(new ClassroomExport.LessonFiles(reserved,material(root,reserved)),new ClassroomExport.LessonFiles(caseTwin,material(root,caseTwin))),ClassroomExport.Layout.BY_COURSE,out,p->{},l->{},format());
        var entries=unzip(out.toByteArray());require(entries.containsKey("_CON/09-23-第7-8节-2026/sources.md")&&entries.containsKey("_con (2)/09-23-第7-8节-2026/sources.md"),"Windows-reserved names and case-insensitive collisions are safe");
        var unsafe=dated("94","95","../坏:课程. ","2026-09-23../../课",23);
        require(!ClassroomExport.fileName(unsafe).contains("/")&&!ClassroomExport.fileName(unsafe).contains(":"),"filename cannot create paths or invalid Windows components");
        var lengthy=dated("96","97","课".repeat(99)+"😀课程","标题".repeat(100),23);String filename=ClassroomExport.fileName(lengthy);
        require(filename.endsWith("-2026.zip")&&filename.length()<255,"truncation retains final year and stays within one filename component");
        for(int i=0;i<filename.length();i++)if(Character.isSurrogate(filename.charAt(i))){require(Character.isHighSurrogate(filename.charAt(i))&&i+1<filename.length()&&Character.isLowSurrogate(filename.charAt(i+1)),"truncation never splits a surrogate pair");i++;}
    }
    public static void main(String[] args)throws Exception{
        File root=new File(args[0]);root.mkdirs();periodEnds();layouts(new File(root,"layouts"));timestamps(new File(root,"timestamps"));var first=lesson("101");var second=lesson("102");
        File notes=material(root,first),raw=material(root,second);JSONObject summary=summary(first);ClassroomStore.write(new File(notes,"summary.json"),summary);
        byte[] original=image("png");Files.write(new File(notes,"image-17.jpg").toPath(),original);
        var lessons=List.of(new ClassroomExport.LessonFiles(first,notes),new ClassroomExport.LessonFiles(second,raw));
        ByteArrayOutputStream output=new ByteArrayOutputStream();var result=ClassroomExport.writeBatch(lessons,ClassroomExport.Layout.FLAT,output,p->{},l->{},format());var entries=unzip(output.toByteArray());
        require(result.success==2&&result.failures.isEmpty(),"summary and downloaded-only export");
        require(entries.size()==6,"one summary, one referenced image, two raw PPTs, two readable transcripts");
        Set<String> directories=new HashSet<>();for(String name:entries.keySet()){directories.add(name.substring(0,name.indexOf('/')));require(!name.contains("../")&&!name.startsWith("/"),"safe names");require(!name.endsWith(".zip"),"no nested archives");require(!name.contains("materials.json")&&!name.contains("run.json"),"no internal cache or URLs");}
        require(directories.size()==2,"same-title lessons retain collision-safe readable directories");
        String markdown=new String(entries.entrySet().stream().filter(e->e.getKey().endsWith("课堂总结.md")).findFirst().get().getValue(),StandardCharsets.UTF_8);
        require(markdown.contains("images/image-17.jpg")&&!markdown.contains("images/images/"),"prefixed sparse image normalizes once");
        require(markdown.indexOf("images/image-17.jpg")==markdown.lastIndexOf("images/image-17.jpg"),"inline image not duplicated by notes.images");
        require(markdown.contains("**未完成**")&&!markdown.contains("图片分析尚未完成"),"draft remains explicitly incomplete without inventing an image stage");
        require(Arrays.equals(original,entries.entrySet().stream().filter(e->e.getKey().endsWith("images/image-17.jpg")).findFirst().get().getValue()),"summary image original bytes");
        require(entries.entrySet().stream().filter(e->e.getKey().endsWith("sources.md")).allMatch(e->new String(e.getValue(),StandardCharsets.UTF_8).contains("00:00:03 — 00:00:08")),"same readable time-anchor transcript for every lesson");
        require(Arrays.equals(Files.readAllBytes(new File(raw,"raw-0.img").toPath()),entries.entrySet().stream().filter(e->e.getKey().endsWith("PPT/0001-raw-0.png")).findFirst().get().getValue()),"PPT PNG preserves actual format and bytes");
        require(Arrays.equals(Files.readAllBytes(new File(raw,"raw-2.img").toPath()),entries.entrySet().stream().filter(e->e.getKey().endsWith("PPT/0002-raw-2.jpg")).findFirst().get().getValue()),"PPT JPEG preserves sparse cache index and bytes");
        ByteArrayOutputStream single=new ByteArrayOutputStream();ClassroomExport.write(notes,summary,single);var singleEntries=unzip(single.toByteArray());require(singleEntries.keySet().equals(new LinkedHashSet<>(List.of("课堂总结.md","sources.md","images/image-17.jpg"))),"single export filenames unchanged");
        Files.delete(new File(notes,"image-17.jpg").toPath());output.reset();result=ClassroomExport.writeBatch(lessons,ClassroomExport.Layout.FLAT,output,p->{},l->{},format());entries=unzip(output.toByteArray());
        require(result.success==1&&result.failures.size()==1,"missing referenced image produces per-lesson failure");require(entries.keySet().stream().noneMatch(n->n.endsWith("课堂总结.md")),"missing image does not publish incomplete notes");require(entries.containsKey("导出结果.txt"),"partial export includes failure report");
        ClassroomStore.begin(raw);output.reset();result=ClassroomExport.writeBatch(List.of(new ClassroomExport.LessonFiles(second,raw)),ClassroomExport.Layout.FLAT,output,p->{},l->{},format());require(result.success==0&&result.failures.size()==1,"active local lease skipped");require(ClassroomStore.active(raw),"another owner's lease remains active");ClassroomStore.end(raw);
        output.reset();result=ClassroomExport.writeBatch(lessons,ClassroomExport.Layout.FLAT,output,p->{},l->{throw new IOException("学校账号已停用或更换");},format());require(result.success==0&&result.failures.size()==2,"account gate checked per lesson");
        boolean failed=false;try{ClassroomExport.writeBatch(List.of(new ClassroomExport.LessonFiles(second,raw)),ClassroomExport.Layout.FLAT,new OutputStream(){public void write(int b)throws IOException{throw new IOException("disk full");}},p->{},l->{},format());}catch(IOException expected){failed=true;}require(failed,"output failure never reports partial success");
        File accountRoot=new File(root,"deletion");var store=new ClassroomStore(new android.content.Context(accountRoot),"account");File downloaded=store.localLesson(second);material(downloaded.getParentFile(),second);int[] callbacks={0};store.deleteLesson(second,()->callbacks[0]++);require(!downloaded.exists()&&callbacks[0]==1,"downloaded-only deletion uses locked beforeDelete callback");
        File active=store.localLesson(second);material(active.getParentFile(),second);ClassroomStore.begin(active);failed=false;try{store.deleteLesson(second,()->callbacks[0]++);}catch(IOException expected){failed=true;}ClassroomStore.end(active);require(failed&&active.exists()&&callbacks[0]==1,"active deletion preserves data and does not mark monitor deleted");
        System.out.println("ClassroomBatchExportCheck PASS: "+checks+" checks; layouts, local/central ZIP lesson-end timestamps, mixed archive, sparse prefixed image, real bytes, partial failure, account/active/output gates, downloaded-only deletion");
    }
}
