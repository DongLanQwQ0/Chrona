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
        return materials(lesson, false);
    }
    public List<ClassroomLesson> courseLessons(ClassroomLesson selected) throws IOException {
        try {
            List<ClassroomLesson> result = new ArrayList<>();
            JSONArray rows = catalogue(selected.courseId);
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);
                if(!selected.courseId.equals(identity(row,"course_id")))throw new IOException("课堂与课程不匹配");
                result.add(new ClassroomLesson(selected.courseId,identity(row,"sub_id"),selected.course,
                        row.optString("title"),row.optString("room"),identity(row,"status"),
                        Math.multiplyExact(row.getLong("start_at"),1000)));
            }
            result.sort(Comparator.comparingLong(lesson -> lesson.startsAt));
            return result;
        } catch(JSONException|ArithmeticException failure) { throw new IOException("课堂目录数据格式无法识别",failure); }
    }
    /** Live observation permits incomplete materials, but retains catalogue ownership checks. */
    public JSONObject monitorMaterials(ClassroomLesson lesson) throws IOException {
        return materials(lesson, true, null);
    }
    public JSONObject monitorMaterials(ClassroomLesson lesson,java.util.function.Consumer<String> stage) throws IOException {
        return materials(lesson,true,stage);
    }
    public okhttp3.WebSocket liveTranscript(ClassroomLesson lesson,okhttp3.WebSocketListener listener)throws IOException {
        String source=liveAddress(lesson);return source==null?null:school.classroomTranscriptSocket(source,listener);
    }
    /** Revalidate this exact lecture without opening or replacing its healthy socket. */
    public boolean liveNow(ClassroomLesson lesson)throws IOException {return liveAddress(lesson)!=null;}
    private String liveAddress(ClassroomLesson lesson)throws IOException {
        try {
            // Catalogue authorization remains mandatory even if a live URL was cached previously.
            boolean accessible=false,live=false;
            for(ClassroomLesson current:courseLessons(lesson))if(current.id.equals(lesson.id)&&current.startsAt>0&&current.startsAt<=System.currentTimeMillis()){accessible=true;live="1".equals(current.status);}
            if(!accessible)throw new IOException("课堂尚未开始或已不可访问");
            if(!live)return null;
            JSONObject response=school.classroomJson(url(url("https://yjapi.cmc.zju.edu.cn/courseapi/v3/portal-home-setting/get-sub-info","course_id",lesson.courseId),"sub_id",lesson.id));
            requireCode(response,0,"实时字幕信息读取失败，请检查课程权限");
            JSONObject data=response.getJSONObject("data");
            if(data.has("course_id")&&!lesson.courseId.equals(identity(data,"course_id"))||data.has("sub_id")&&!lesson.id.equals(identity(data,"sub_id")))throw new IOException("实时字幕与课堂不匹配");
            if(data.has("sub_status")&&!"1".equals(identity(data,"sub_status")))return null;
            JSONObject content=data.optJSONObject("content");String source=content==null?"":content.optString("trans_socket_url");
            return source.isEmpty()?null:source;
        }catch(JSONException failure){throw new IOException("实时字幕信息无法识别",failure);}
    }
    private JSONObject materials(ClassroomLesson lesson, boolean monitoring) throws IOException {
        return materials(lesson,monitoring,null);
    }
    private JSONObject materials(ClassroomLesson lesson, boolean monitoring,java.util.function.Consumer<String> stage) throws IOException {
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
            if((!monitoring&&!"6".equals(identity(current,"status")))||startsAt<=0||startsAt>System.currentTimeMillis())throw new IOException("这节课堂尚未就绪");
            if(stage!=null)stage.accept(identity(current,"status"));
            JSONObject transcript=school.classroomJson(url("https://yjapi.cmc.zju.edu.cn/courseapi/v3/web-socket/search-trans-result?format=json","sub_id",lesson.id));
            boolean pending = false;
            int subtitleError = 0, slideError = 0;
            try { requireAvailable(transcript); }
            catch (MaterialsPendingException unavailable) {
                if (!monitoring) throw unavailable;
                pending = true;
                transcript = new JSONObject().put("code",0).put("total",0).put("list",new JSONArray());
            }
            if (monitoring && transcript.getInt("code") != 0) {
                subtitleError = transcript.getInt("code"); pending = true;
                transcript = new JSONObject().put("code",0).put("total",0).put("list",new JSONArray());
            }
            requireCode(transcript,0,"课堂字幕读取失败，请检查课程权限后重试");
            JSONArray sources=transcript.getJSONArray("list");List<JSONObject> orderedSegments=new ArrayList<>();
            if(transcript.getInt("total")!=sources.length())throw new IOException("课堂字幕未完整返回，请重试");
            for(int i=0;i<sources.length();i++) {
                JSONObject source=sources.getJSONObject(i);
                if(!lesson.id.equals(identity(source,"sub_id")))throw new IOException("字幕与课堂不匹配");
                if(generating(source.opt("all_content"))) {
                    if(!monitoring)throw new MaterialsPendingException();
                    pending = true; continue;
                }
                JSONArray rows=source.getJSONArray("all_content");
                for(int n=0;n<rows.length();n++) {
                    JSONObject row=rows.getJSONObject(n);double start=row.getDouble("BeginSec"),end=row.getDouble("EndSec");
                    if(!Double.isFinite(start)||!Double.isFinite(end)||start<0||end<start)throw new IOException("字幕时间无效");
                    String text=row.getString("Text").trim();
                    if(!text.isEmpty())orderedSegments.add(new JSONObject().put("start",start).put("end",end).put("text",text));
                }
            }
            if(orderedSegments.isEmpty()) {
                if(!monitoring)throw new MaterialsPendingException();
                pending = true;
            }
            orderedSegments.sort(Comparator.comparingDouble(o->o.optDouble("start")));
            JSONArray segments=new JSONArray();for(JSONObject segment:orderedSegments)segments.put(segment);
            JSONObject slides=pptPages(lesson);
            try { requireAvailable(slides); }
            catch (MaterialsPendingException unavailable) {
                if (!monitoring) throw unavailable;
                pending = true;
                slides = new JSONObject().put("code",0).put("total",0).put("list",new JSONArray());
            }
            if (monitoring && slides.getInt("code") != 0) {
                slideError = slides.getInt("code"); pending = true;
                slides = new JSONObject().put("code",0).put("total",0).put("list",new JSONArray());
            }
            requireCode(slides,0,"课堂截图读取失败，请检查课程权限后重试");
            JSONArray rows=slides.getJSONArray("list"), images=new JSONArray();
            if(slides.getInt("total")!=rows.length())throw new IOException("课堂截图未完整返回，请重试");
            Set<String> urls=new HashSet<>();List<JSONObject> orderedImages=new ArrayList<>();
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);
                Object rawContent=row.get("content");
                JSONObject content=rawContent instanceof JSONObject?(JSONObject)rawContent:new JSONObject(row.getString("content"));
                if(!lesson.courseId.equals(identity(row,"course_id"))||!lesson.id.equals(identity(row,"sub_id")))throw new IOException("截图与课堂不匹配");
                String image=content.optString("pptimgurl","").trim();
                // The official stream also contains text-only/empty PPT entries.
                // ZJU-live-better likewise skips downloadImage when pptimgurl is empty.
                if(image.isEmpty())continue;
                HttpUrl parsed;
                try{parsed=imageUrl(image);}catch(IOException failure){throw new IOException("第 "+(i+1)+"/"+rows.length()+" 条截图："+failure.getMessage(),failure);}
                double second=row.getDouble("created_sec");
                if(!Double.isFinite(second)||second<0)throw new IOException("截图时间无效");
                orderedImages.add(new JSONObject().put("url",parsed.toString()).put("second",second));
            }
            orderedImages.sort(Comparator.comparingDouble(o->o.optDouble("second")));
            for(JSONObject image:orderedImages)if(urls.add(image.getString("url")))images.put(image);
            JSONObject result = new JSONObject().put("segments",segments).put("images",images).put("raw_images",orderedImages.size());
            if(monitoring) result.put("pending",pending).put("status",identity(current,"status"))
                    .put("subtitle_error",subtitleError).put("slide_error",slideError);
            return result;
        } catch(JSONException|ArithmeticException e) { throw new IOException("课堂素材数据格式无法识别",e); }
    }
    public byte[] image(String url) throws IOException { return school.classroomImage(url); }
    private JSONObject pptPages(ClassroomLesson lesson) throws IOException,JSONException {
        String base=url(url("https://classroom.zju.edu.cn/pptnote/v1/schedule/search-ppt","course_id",lesson.courseId),"sub_id",lesson.id);
        JSONArray all=new JSONArray();Set<String> seen=new HashSet<>();
        for(int page=1;page<=200;page++) {
            JSONObject response=school.classroomJson(url(url(base,"page",Integer.toString(page)),"per_page","100"));
            if(generating(response.opt("message"))||generating(response.opt("msg"))||response.getInt("code")!=0)return response;
            int total=response.getInt("total");JSONArray rows=response.getJSONArray("list");
            if(total<0||total>20_000)throw new IOException("课堂截图数量无法识别");
            for(int i=0;i<rows.length();i++) {
                JSONObject row=rows.getJSONObject(i);
                String identity=row.has("id")?row.get("id").toString():row.get("course_id")+"/"+row.get("sub_id")+"/"+row.get("created_sec")+"/"+row.get("content");
                if(!seen.add(identity))throw new IOException("课堂截图分页重复，请重试");
                all.put(row);
            }
            if(all.length()==total)return new JSONObject().put("code",0).put("total",all.length()).put("list",all);
            if(rows.length()==0||all.length()>total)throw new IOException("课堂截图分页发生变化，请重试");
        }
        throw new IOException("课堂截图分页过多，请重试");
    }
    static HttpUrl imageUrl(String source)throws IOException{
        String input=source.trim();
        if(input.startsWith("//"))input="https:"+input;
        HttpUrl url=HttpUrl.parse(input);
        if(url==null)throw new IOException("截图地址格式无效");
        if(!url.host().equals("video.cmc.zju.edu.cn"))throw new IOException("截图媒体域名不受支持（"+url.host()+"）");
        if(!url.username().isEmpty()||!url.password().isEmpty())throw new IOException("截图地址包含用户信息");
        if(url.isHttps()&&url.port()==443)return url;
        // An HTTP media reference is upgraded locally; no plaintext request or credentials are sent.
        if(url.scheme().equals("http")&&url.port()==80)return url.newBuilder().scheme("https").port(443).build();
        throw new IOException("截图协议或端口不受支持（"+url.scheme()+":"+url.port()+"）");
    }
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
