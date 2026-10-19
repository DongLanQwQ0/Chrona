package com.donglan.chrona.classroom;

import com.donglan.chrona.zju.ZjuClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;
import java.io.Closeable;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import okhttp3.HttpUrl;

/** Synchronous read-only Zhiyun client. Call from a worker thread and close after use. */
public final class ClassroomClient implements Closeable {
    /** A retryable material availability state; the lesson remains in the catalogue. */
    public static final class MaterialsPendingException extends IOException {
        public MaterialsPendingException() { super("字幕尚未就绪，请稍后重试"); }
    }
    private final ZjuClient school;
    public ClassroomClient(String user,String password) { school=new ZjuClient(user,password); }
    public void cancel() { school.cancel(); }
    @Override public void close() { school.close(); }
    public List<ClassroomLesson> lessons() throws IOException {
        List<ClassroomLesson> lessons=new ArrayList<>(); Set<String> courses=new HashSet<>(), seen=new HashSet<>();
        try {
            for(int page=1;page<=200;page++) {
                JSONObject response=school.classroomJson("https://education.cmc.zju.edu.cn/personal/courseapi/vlabpassportapi/v1/account-profile/course?per-page=100&force_mycourse=1&nowpage="+page);
                // These services use different success codes: courses 1000, catalogue 200, materials 0.
                requireCode(response,1000,"课堂课程读取失败，请检查登录状态与课程权限");
                JSONObject result=response.getJSONObject("params").getJSONObject("result");
                if(result.getInt("page")!=page||result.getInt("per-page")<=0||result.getInt("total")<0)
                    throw new IOException("课堂课程分页无法识别");
                JSONArray rows=result.getJSONArray("data"); int added=0;
                for(int i=0;i<rows.length();i++) {
                    JSONObject course=rows.getJSONObject(i);String id=identity(course,"Id");
                    if(!courses.add(id))continue; added++;
                    JSONArray items=catalogue(id);
                    for(int n=0;n<items.length();n++) {
                        JSONObject item=items.getJSONObject(n);String sub=identity(item,"sub_id");
                        if(!id.equals(identity(item,"course_id")))throw new IOException("课堂与课程不匹配");
                        if(!seen.add(id+"/"+sub))continue;
                        lessons.add(new ClassroomLesson(id,sub,course.getString("Title"),item.optString("title"),item.optString("room"),
                                identity(item,"status"),Math.multiplyExact(item.getLong("start_at"),1000)));
                    }
                }
                int total=result.getInt("total");
                if(courses.size()>total)throw new IOException("课堂课程分页发生变化，请重试");
                if(courses.size()==total) { lessons.sort(Comparator.comparingLong((ClassroomLesson l)->l.startsAt).reversed().thenComparing(l->l.id));return lessons; }
                if(added==0)throw new IOException("课堂课程分页不完整，请重试");
            }
            throw new IOException("课堂课程分页过多");
        } catch(JSONException|ArithmeticException e) { throw new IOException("课堂目录数据格式无法识别",e); }
    }
    public JSONObject materials(ClassroomLesson lesson) throws IOException {
        try {
            // Recheck the selected recording: a cached ready state can become stale.
            JSONArray catalogue=catalogue(lesson.courseId);JSONObject current=null;
            for(int i=0;i<catalogue.length();i++) {
                JSONObject row=catalogue.getJSONObject(i);
                if(lesson.id.equals(identity(row,"sub_id"))) { current=row;break; }
            }
            if(current==null)throw new IOException("课堂已不可访问，请刷新目录");
            if(!lesson.courseId.equals(identity(current,"course_id")))throw new IOException("课堂与课程不匹配");
            requireAvailable(current);
            long startsAt=Math.multiplyExact(current.getLong("start_at"),1000);
            if(!"6".equals(identity(current,"status"))||startsAt<=0||startsAt>System.currentTimeMillis())throw new IOException("这节课堂尚未就绪");
            JSONObject transcript=school.classroomJson(url("https://yjapi.cmc.zju.edu.cn/courseapi/v3/web-socket/search-trans-result?format=json","sub_id",lesson.id));
            requireAvailable(transcript);
            requireCode(transcript,0,"课堂字幕读取失败，请检查课程权限后重试");
            JSONArray sources=transcript.getJSONArray("list");List<JSONObject> orderedSegments=new ArrayList<>();
            if(transcript.getInt("total")!=sources.length())throw new IOException("课堂字幕未完整返回，请重试");
            for(int i=0;i<sources.length();i++) {
                JSONObject source=sources.getJSONObject(i);
                if(!lesson.id.equals(identity(source,"sub_id")))throw new IOException("字幕与课堂不匹配");
                if(generating(source.opt("all_content")))throw new MaterialsPendingException();
                JSONArray rows=source.getJSONArray("all_content");
                for(int n=0;n<rows.length();n++) {
                    JSONObject row=rows.getJSONObject(n);double start=row.getDouble("BeginSec"),end=row.getDouble("EndSec");
                    if(!Double.isFinite(start)||!Double.isFinite(end)||start<0||end<start)throw new IOException("字幕时间无效");
                    String text=row.getString("Text").trim();
                    if(!text.isEmpty())orderedSegments.add(new JSONObject().put("start",start).put("end",end).put("text",text));
                }
            }
            if(orderedSegments.isEmpty())throw new MaterialsPendingException();
            orderedSegments.sort(Comparator.comparingDouble(o->o.optDouble("start")));
            JSONArray segments=new JSONArray();for(JSONObject segment:orderedSegments)segments.put(segment);
            JSONObject slides=school.classroomJson(url(url("https://classroom.zju.edu.cn/pptnote/v1/schedule/search-ppt","course_id",lesson.courseId),"sub_id",lesson.id));
            requireAvailable(slides);
            requireCode(slides,0,"课堂截图读取失败，请检查课程权限后重试");
            JSONArray rows=slides.getJSONArray("list"), images=new JSONArray();
            if(slides.getInt("total")!=rows.length())throw new IOException("课堂截图未完整返回，请重试");
            Set<String> urls=new HashSet<>();List<JSONObject> orderedImages=new ArrayList<>();
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i),content=new JSONObject(row.getString("content"));
                if(!lesson.courseId.equals(identity(row,"course_id"))||!lesson.id.equals(identity(row,"sub_id")))throw new IOException("截图与课堂不匹配");
                String image=content.getString("pptimgurl");
                HttpUrl parsed=HttpUrl.parse(image);
                if(parsed==null||!parsed.isHttps()||!parsed.host().equals("video.cmc.zju.edu.cn")||parsed.port()!=443
                        ||!parsed.username().isEmpty()||!parsed.password().isEmpty())throw new IOException("截图地址无法识别");
                double second=row.getDouble("created_sec");
                if(!Double.isFinite(second)||second<0)throw new IOException("截图时间无效");
                orderedImages.add(new JSONObject().put("url",parsed.toString()).put("second",second));
            }
            orderedImages.sort(Comparator.comparingDouble(o->o.optDouble("second")));
            for(JSONObject image:orderedImages)if(urls.add(image.getString("url")))images.put(image);
            return new JSONObject().put("segments",segments).put("images",images).put("raw_images",rows.length());
        } catch(JSONException|ArithmeticException e) { throw new IOException("课堂素材数据格式无法识别",e); }
    }
    public byte[] image(String url) throws IOException { return school.classroomImage(url); }
    private static boolean generating(Object value) { return value instanceof String&&((String)value).contains("正在生成"); }
    private static void requireAvailable(JSONObject response) throws MaterialsPendingException {
        if(generating(response.opt("message"))||generating(response.opt("msg")))throw new MaterialsPendingException();
    }
    private JSONArray catalogue(String course) throws IOException,JSONException {
        JSONObject response=school.classroomJson(url("https://yjapi.cmc.zju.edu.cn/courseapi/v2/course/catalogue","course_id",course));
        if(!response.optBoolean("success"))throw new IOException("课堂目录读取失败，请检查课程权限后重试");
        JSONObject result=response.getJSONObject("result");requireCode(result,200,"课堂目录读取失败，请检查课程权限后重试");
        return result.getJSONArray("data");
    }
    private static String identity(JSONObject object,String key) throws JSONException {
        Object value=object.get(key);
        if(!(value instanceof String||value instanceof Number)||value.toString().isEmpty())throw new JSONException("Invalid identity");
        return value.toString();
    }
    private static void requireCode(JSONObject object,int expected,String failure) throws JSONException,IOException {
        if(object.getInt("code")!=expected)throw new IOException(failure);
    }
    private static String url(String base,String key,String value) { return HttpUrl.get(base).newBuilder().addQueryParameter(key,value).build().toString(); }
}
