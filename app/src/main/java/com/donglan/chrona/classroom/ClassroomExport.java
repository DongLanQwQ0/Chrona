package com.donglan.chrona.classroom;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.attribute.FileTime;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** A portable Markdown document with relative image and transcript references. */
public final class ClassroomExport {
    private ClassroomExport() { }
    public enum Layout { FLAT, BY_COURSE }
    private static final int MAX_LABEL_LENGTH=100;
    private static final long WINDOWS_EPOCH_OFFSET_MILLIS=11644473600000L;
    private static final long WINDOWS_TICKS_PER_MILLISECOND=10000L;
    private static final Pattern PERIOD=Pattern.compile("第\\s*(\\d+)(?:\\s*[-—–－~～至]\\s*(\\d+))?\\s*节");
    public static String fileName(ClassroomLesson lesson){return flatName(lesson)+".zip";}
    /** Use the final configured period on this recording's Shanghai calendar date. */
    public static long periodEnd(ClassroomLesson lesson,java.util.List<String> periodEnds){
        if(lesson.startsAt<=0)return 0;
        Matcher period=PERIOD.matcher(lesson.title);if(!period.find())return 0;
        try{
            int first=Integer.parseInt(period.group(1)),last=period.group(2)==null?first:Integer.parseInt(period.group(2));
            if(first<1||last<first||last>periodEnds.size())return 0;
            return lesson.date().atTime(java.time.LocalTime.parse(periodEnds.get(last-1))).atZone(ClassroomLesson.ZONE).toInstant().toEpochMilli();
        }catch(NumberFormatException|java.time.DateTimeException invalid){return 0;}
    }
    public static String time(double seconds) {
        long s=(long)Math.max(0,seconds);return String.format(Locale.ROOT,"%02d:%02d:%02d",s/3600,(s/60)%60,s%60);
    }
    public static void write(File dir,JSONObject summary,OutputStream output) throws Exception {
        write(dir,summary,output,value->{});
    }
    public static void write(File dir,JSONObject summary,OutputStream output,ClassroomImages.Progress progress) throws Exception {
        write(dir,summary,output,progress,0);
    }
    public static void write(File dir,JSONObject summary,OutputStream output,ClassroomImages.Progress progress,long endsAt) throws Exception {
        FileTime time=lessonTime(ClassroomLesson.fromJson(summary.getJSONObject("lesson")),endsAt);
        try(ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)){
            writeEntries(dir,summary,zip,"",progress,time);
        }
    }
    /** Same single-lesson content writer, without owning or nesting the enclosing ZIP. */
    private static void writeEntries(File dir,JSONObject summary,ZipOutputStream zip,String prefix,ClassroomImages.Progress progress,FileTime time) throws Exception {
        progress.update(ClassroomProgress.waiting("生成 Markdown 文档","整理笔记、字幕来源与图片引用"));
        JSONObject materials=ClassroomStore.read(new File(dir,"materials.json"));
        if(materials==null)throw new IOException("字幕缓存不存在，无法导出来源");
        JSONArray segments=materials.getJSONArray("segments");ClassroomLesson lesson=ClassroomLesson.fromJson(summary.getJSONObject("lesson"));
        Set<String> images=ClassroomDocument.referencedImages(summary);StringBuilder md=new StringBuilder("# ").append(escape(lesson.course)).append("\n\n").append(escape(lesson.title)).append("\n\n");
        if(!summary.optBoolean("complete"))md.append("**未完成**\n\n");
        if(!summary.optString("overview").isBlank())md.append("## 重点概览\n\n").append(ClassroomDocument.exportMarkdown(summary.getString("overview"))).append("\n\n");
        JSONArray parts=summary.getJSONArray("parts");
        for(int p=0;p<parts.length();p++) {
            JSONObject part=parts.getJSONObject(p);
            JSONArray notes=part.getJSONArray("notes");
            for(int i=0;i<notes.length();i++) {
                JSONObject note=notes.getJSONObject(i);md.append("## ").append(ClassroomDocument.exportMarkdown(note.getString("title"))).append("\n\n").append(ClassroomDocument.exportMarkdown(note.getString("text"))).append("\n\n");
                refs(md,note.getJSONArray("sources"),segments);
                JSONArray pics=note.getJSONArray("images");
                Set<String> inline=ClassroomDocument.inlineImages(note.getString("text"));
                for(int n=0;n<pics.length();n++){String file=safeImage(pics.getString(n));if(!inline.contains(file))md.append("![课堂截图](images/").append(file).append(")\n\n");}
            }
        }
        boolean assignments=false;
        for(int p=0;p<parts.length();p++){JSONArray tasks=parts.getJSONObject(p).getJSONArray("assignments");
            for(int i=0;i<tasks.length();i++){if(!assignments){md.append("## 课堂安排\n\n");assignments=true;}JSONObject task=tasks.getJSONObject(i);md.append(ClassroomDocument.exportMarkdown(task.getString("text"))).append("\n\n");refs(md,task.getJSONArray("sources"),segments);}}
        JSONArray selected=summary.getJSONArray("images");
        md.append("原始截图 ").append(summary.getInt("raw_images")).append(" 张；去重候选 ").append(summary.getInt("candidate_images"))
                .append(" 张；分析 ").append(selected.length()).append(" 张。\n");
        String transcript=transcript(segments);
        for(String image:images)if(!new File(dir,image).isFile())throw new IOException("总结引用的图片缺失："+image);
        {
            String corrected=summary.optString("corrected_transcript");int total=images.size()+2+(corrected.isBlank()?0:1),done=0;
            progress.update(new ClassroomProgress("写入 ZIP",done,total,"课堂总结.md"));
            entry(zip,prefix+"课堂总结.md",md.toString().getBytes(StandardCharsets.UTF_8),time);done++;
            progress.update(new ClassroomProgress("写入 ZIP",done,total,"sources.md"));
            entry(zip,prefix+"sources.md",transcript.getBytes(StandardCharsets.UTF_8),time);done++;
            if(!corrected.isBlank()){entry(zip,prefix+"更正文稿.md",ClassroomDocument.normalizeMarkdown(corrected).getBytes(StandardCharsets.UTF_8),time);done++;}
            for(String image:images){progress.update(new ClassroomProgress("写入 ZIP",done,total,image));entry(zip,prefix+"images/"+image,ClassroomStore.bytes(new File(dir,image)),time);done++;}
            progress.update(new ClassroomProgress("ZIP 内容已写入",done,total,images.size()+" 张图片"));
        }
    }
    public static final class LessonFiles {
        public final ClassroomLesson lesson;public final File dir;
        public final long endsAt;
        public LessonFiles(ClassroomLesson lesson,File dir){this(lesson,dir,0);}
        public LessonFiles(ClassroomLesson lesson,File dir,long endsAt){this.lesson=lesson;this.dir=dir;this.endsAt=endsAt;}
    }
    public interface Guard {void check(ClassroomLesson lesson)throws Exception;}
    public interface ImageFormat {String extension(File file)throws Exception;}
    public static final class BatchResult {
        public int success;
        public final java.util.LinkedHashMap<String,String> failures=new java.util.LinkedHashMap<>();
        public String text(){return "导出完成 · 成功 "+success+" · 失败 "+failures.size();}
    }
    /** One archive; identity stays internal while visible folders use readable collision-safe names. */
    public static BatchResult writeBatch(java.util.List<LessonFiles> lessons,Layout layout,OutputStream output,ClassroomImages.Progress progress,Guard guard,ImageFormat format)throws Exception {
        BatchResult result=new BatchResult();java.util.Set<String> seen=new java.util.HashSet<>();
        ArchiveNames names=new ArchiveNames(java.util.Objects.requireNonNull(layout));
        try(ZipOutputStream zip=new ZipOutputStream(output,StandardCharsets.UTF_8)){
            for(LessonFiles item:lessons){ClassroomLesson lesson=item.lesson;String key=lesson.courseId+"-"+lesson.id;if(!seen.add(key))continue;
                if(!lesson.courseId.matches("[0-9]+")||!lesson.id.matches("[0-9]+"))throw new IOException("课堂编号无效");
                boolean leased=false;
                try{
                    if(!item.dir.getName().equals(key)||java.nio.file.Files.isSymbolicLink(item.dir.toPath()))throw new IOException("课堂目录与身份不匹配");
                    guard.check(lesson);ClassroomStore.begin(item.dir);leased=true;
                    JSONObject summary=ClassroomStore.read(new File(item.dir,"summary.json"));
                    JSONObject materials=ClassroomStore.read(new File(item.dir,"materials.json"));
                    if(materials==null||materials.optJSONArray("segments")==null)throw new IOException("字幕缓存不存在");
                    if(summary!=null&&!ClassroomDocument.key(summary.getJSONObject("lesson")).equals(key))throw new IOException("总结与课堂不匹配");
                    // Resolve and validate every input before writing the first entry for this lesson.
                    java.util.LinkedHashMap<String,File> raw=new java.util.LinkedHashMap<>();
                    if(summary==null){
                        JSONObject candidates=ClassroomStore.read(new File(item.dir,"candidates.json"));
                        if(materials.optBoolean("monitoring")||materials.getJSONArray("segments").length()==0||candidates==null)throw new IOException("本节素材尚未完整下载");
                        JSONObject candidateLesson=candidates.optJSONObject("lesson");if(candidateLesson!=null&&!ClassroomDocument.key(candidateLesson).equals(key))throw new IOException("素材与课堂不匹配");
                        JSONArray images=materials.getJSONArray("images");
                        for(int i=0;i<images.length();i++){
                            int index=images.getJSONObject(i).optInt("cache_index",i);if(index<0)throw new IOException("截图缓存编号无效");
                            File file=new File(item.dir,"raw-"+index+".img");if(!file.isFile())throw new IOException("课堂PPT缺失：第 "+(i+1)+" 张");
                            String extension=format.extension(file);if(extension==null||!extension.matches("[a-zA-Z0-9]+"))throw new IOException("课堂图片格式无效");
                            raw.put("PPT/"+String.format(Locale.ROOT,"%04d",i+1)+"-raw-"+index+"."+extension,file);
                        }
                    }else for(String image:ClassroomDocument.referencedImages(summary))if(!new File(item.dir,image).isFile())throw new IOException("总结引用的图片缺失："+image);
                    guard.check(lesson);
                    FileTime time=lessonTime(lesson,item.endsAt);
                    String prefix=names.directory(lesson)+"/";
                    if(summary!=null)writeEntries(item.dir,summary,zip,prefix,value->{try{guard.check(lesson);}catch(Exception stopped){throw new BatchStopped(stopped);}progress.update(value);},time);
                    for(java.util.Map.Entry<String,File> image:raw.entrySet()){guard.check(lesson);entry(zip,prefix+image.getKey(),ClassroomStore.bytes(image.getValue()),time);}
                    if(summary==null){guard.check(lesson);entry(zip,prefix+"sources.md",transcript(materials.getJSONArray("segments")).getBytes(StandardCharsets.UTF_8),time);}result.success++;
                    progress.update(new ClassroomProgress("批量导出",result.success+result.failures.size(),lessons.size(),lesson.course));
                }catch(BatchStopped stopped){throw stopped;}
                catch(Exception failure){
                    // Output failures cannot safely be recovered by pretending the next ZIP entry succeeded.
                    if(failure instanceof java.io.InterruptedIOException||failure instanceof OutputFailure)throw failure;
                    result.failures.put(key,failure.getMessage()==null?"导出失败":failure.getMessage());
                }finally{if(leased)ClassroomStore.end(item.dir);}
            }
            if(!result.failures.isEmpty()){
                StringBuilder errors=new StringBuilder(result.text()).append("\n");
                for(java.util.Map.Entry<String,String> failure:result.failures.entrySet())errors.append(failure.getKey()).append(": ").append(failure.getValue()).append("\n");
                entry(zip,"导出结果.txt",errors.toString().getBytes(StandardCharsets.UTF_8));
            }
        }return result;
    }
    private static final class BatchStopped extends RuntimeException {BatchStopped(Exception cause){super(cause);}}
    private static String transcript(JSONArray segments)throws Exception {
        StringBuilder text=new StringBuilder("# 字幕来源\n\n");
        for(int i=0;i<segments.length();i++){JSONObject segment=segments.getJSONObject(i);text.append("<a id=\"s").append(i).append("\"></a>\n\n### ")
                .append(time(segment.getDouble("start"))).append(" — ").append(time(segment.getDouble("end"))).append("\n\n").append(escape(segment.getString("text"))).append("\n\n");}
        return text.toString();
    }
    private static String lessonName(ClassroomLesson lesson){
        String title=lesson.title.trim();Matcher period=PERIOD.matcher(title);
        if(period.find())title=period.group().replaceAll("\\s+","").replaceAll("[—–－~～至]","-");
        else if(lesson.startsAt>0)title=title.replace(lesson.date().toString(),"").replaceAll("^[\\s·:_-]+|[\\s·:_-]+$","");
        if(lesson.startsAt<=0)return "日期未定-"+safeName(title,"课堂",MAX_LABEL_LENGTH-5);
        java.time.LocalDate date=lesson.date();String start=String.format(Locale.ROOT,"%02d-%02d-",date.getMonthValue(),date.getDayOfMonth()),end="-"+date.getYear();
        return start+safeName(title,"课堂",MAX_LABEL_LENGTH-start.length()-end.length())+end;
    }
    private static String flatName(ClassroomLesson lesson){
        return safeName(lesson.course,"课程",MAX_LABEL_LENGTH)+"-"+lessonName(lesson);
    }
    private static String safeName(String value,String fallback,int limit){
        String name=value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_").trim();
        if(name.length()>limit){int end=limit;if(Character.isHighSurrogate(name.charAt(end-1)))end--;name=name.substring(0,end);}
        name=name.replaceAll("[. ]+$","");if(name.isEmpty())name=fallback;
        if(name.matches("(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(?:\\..*)?"))name="_"+name;
        return name;
    }
    private static final class ArchiveNames {
        final Layout layout;
        final Set<String> paths=new java.util.HashSet<>(),courses=new java.util.HashSet<>();
        final java.util.Map<String,String> courseFolders=new java.util.HashMap<>();
        ArchiveNames(Layout layout){this.layout=layout;}
        String directory(ClassroomLesson lesson){
            if(layout==Layout.FLAT)return unique(flatName(lesson),"",paths);
            String course=courseFolders.get(lesson.courseId);
            if(course==null){course=unique(safeName(lesson.course,"课程",MAX_LABEL_LENGTH),"",courses);courseFolders.put(lesson.courseId,course);}
            return course+"/"+unique(lessonName(lesson),course+"/",paths);
        }
        private static String unique(String base,String parent,Set<String> used){
            String name=base;int suffix=2;
            while(!used.add((parent+name).toLowerCase(Locale.ROOT)))name=base+" ("+suffix+++")";
            return name;
        }
    }
    private static void refs(StringBuilder md,JSONArray refs,JSONArray segments) throws Exception {
        md.append("来源：");for(int i=0;i<refs.length();i++){int id=refs.getInt(i);md.append("[").append(time(segments.getJSONObject(id).getDouble("start"))).append("](sources.md#s").append(id).append(") ");}md.append("\n\n");
    }
    public static String safeImage(String name) throws IOException { return ClassroomDocument.imageName(name); }
    private static String escape(String text) {return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\\","\\\\").replace("*","\\*").replace("_","\\_").replace("[","\\[").replace("]","\\]").replace("#","\\#").replace("`","\\`");}
    private static final class OutputFailure extends IOException {OutputFailure(IOException cause){super("ZIP 写入失败",cause);}}
    private static FileTime lessonTime(ClassroomLesson lesson,long endsAt){return endsAt>0&&endsAt>lesson.startsAt?FileTime.fromMillis(endsAt):null;}
    private static void entry(ZipOutputStream zip,String name,byte[] bytes) throws IOException {entry(zip,name,bytes,null);}
    private static void entry(ZipOutputStream zip,String name,byte[] bytes,FileTime time) throws IOException {
        ZipEntry entry=new ZipEntry(name);
        if(time!=null){
            entry.setCreationTime(time);entry.setLastModifiedTime(time);
            // PKWARE APPNOTE 4.5.5: NTFS times survive central-directory readers and retain
            // milliseconds; Java's extended timestamp alone drops creation time from that directory.
            long ticks=Math.multiplyExact(Math.addExact(time.toMillis(),WINDOWS_EPOCH_OFFSET_MILLIS),WINDOWS_TICKS_PER_MILLISECOND);
            entry.setExtra(ByteBuffer.allocate(36).order(ByteOrder.LITTLE_ENDIAN)
                    .putShort((short)0x000a).putShort((short)32).putInt(0)
                    .putShort((short)1).putShort((short)24).putLong(ticks).putLong(ticks).putLong(ticks).array());
        }
        try{zip.putNextEntry(entry);zip.write(bytes);zip.closeEntry();}catch(IOException failure){throw new OutputFailure(failure);}
    }
}
