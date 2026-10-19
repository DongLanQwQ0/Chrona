import com.donglan.chrona.classroom.*;
import com.donglan.chrona.zju.*;
import okhttp3.*;
import okhttp3.Cookie;
import org.json.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;

/** Production transport/client tests with an in-memory HTTP boundary. */
public final class ClassroomClientCheck {
    static int checks;
    static void check(boolean value,String label) { checks++;if(!value)throw new AssertionError(label); }
    interface Work { void run() throws Exception; }
    static void fails(Work work,String label)throws Exception {
        try { work.run();throw new AssertionError(label); }catch(IOException expected) { checks++; }
    }
    static Object field(Object target,String name)throws Exception { Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(target); }
    static void field(Object target,String name,Object value)throws Exception { Field f=target.getClass().getDeclaredField(name);f.setAccessible(true);f.set(target,value); }
    static Response response(Request r,int status,String type,String body) {
        return new Response.Builder().request(r).protocol(Protocol.HTTP_1_1).code(status).message("fixture")
                .header("Content-Type",type).body(ResponseBody.create(body,MediaType.get(type))).build();
    }
    static Response redirect(Request r,String location) { return response(r,302,"text/html","").newBuilder().header("Location",location).build(); }
    static final class Fixture implements AutoCloseable {
        final ClassroomClient client=new ClassroomClient("fixture","password");
        final ZjuClient school;
        String state="6",transcriptCode="0",slideCode="0",transcriptId="20",slideId="20",imageHost="video.cmc.zju.edu.cn";
        int totalTranscript=1,totalSlides=3,materialCalls,requests,imageStatus=200,imageBytes=4;
        boolean emptySubtitles,repeatPages,future,imageHtml,unknownImageLength;
        Fixture()throws Exception {
            school=(ZjuClient)field(client,"school");field(school,"classroomLoggedIn",true);
            OkHttpClient old=(OkHttpClient)field(school,"http");
            field(school,"http",old.newBuilder().addInterceptor(chain->{
                Request r=chain.request();requests++;String p=r.url().encodedPath();
                try {
                    if(p.endsWith("/course")) {
                        int page=Integer.parseInt(r.url().queryParameter("nowpage"));
                        return response(r,200,"application/json",new JSONObject().put("code",1000).put("params",new JSONObject().put("result",new JSONObject()
                                .put("page",page).put("per-page",1).put("total",repeatPages?2:1).put("data",new JSONArray().put(new JSONObject().put("Id",10).put("Title","fixture"))))).toString());
                    }
                    if(p.endsWith("/catalogue")) return response(r,200,"application/json",new JSONObject().put("success",true).put("result",new JSONObject().put("code",200).put("data",new JSONArray().put(new JSONObject()
                            .put("course_id",10).put("sub_id",20).put("title","fixture").put("status",state).put("start_at",future?System.currentTimeMillis()/1000+3600:1)))).toString());
                    if(p.endsWith("search-trans-result")) {
                        materialCalls++;
                        JSONArray segments=emptySubtitles?new JSONArray():new JSONArray().put(new JSONObject().put("BeginSec",8).put("EndSec",10).put("Text","second"))
                                .put(new JSONObject().put("BeginSec",1).put("EndSec",2).put("Text","first"));
                        return response(r,200,"application/json",new JSONObject().put("code",Integer.parseInt(transcriptCode)).put("total",totalTranscript).put("list",new JSONArray().put(new JSONObject().put("sub_id",transcriptId).put("all_content",segments))).toString());
                    }
                    if(p.endsWith("search-ppt")) {
                        JSONArray images=new JSONArray();
                        for(int i=0;i<3;i++)images.put(new JSONObject().put("course_id",10).put("sub_id",slideId).put("created_sec",new int[]{9,2,1}[i])
                                .put("content",new JSONObject().put("pptimgurl","https://"+imageHost+"/"+(i==1?"b":"a")+".jpg").toString()));
                        return response(r,200,"application/json",new JSONObject().put("code",Integer.parseInt(slideCode)).put("total",totalSlides).put("list",images).toString());
                    }
                    if(r.url().host().equals("video.cmc.zju.edu.cn")) {
                        Response image=response(r,imageStatus,imageHtml?"text/html":"image/jpeg","x".repeat(imageBytes));
                        if(unknownImageLength) {
                            final okio.Buffer bytes=new okio.Buffer().writeUtf8("x".repeat(imageBytes));
                            image=image.newBuilder().body(new ResponseBody(){public MediaType contentType(){return MediaType.get("image/jpeg");}public long contentLength(){return -1;}public okio.BufferedSource source(){return bytes;}}).build();
                        }
                        return image;
                    }
                }catch(JSONException e){throw new IOException(e);}
                throw new AssertionError("Unexpected request");
            }).build());
        }
        ClassroomLesson selected(){return new ClassroomLesson("10","20","fixture","fixture","","6",1000);}
        public void close(){client.close();}
    }
    static void synthetic()throws Exception {
        try(Fixture f=new Fixture()) {
            check(f.client.lessons().size()==1,"numeric course identity");
            JSONObject materials=f.client.materials(f.selected());
            check(materials.getJSONArray("segments").getJSONObject(0).getDouble("start")==1,"chronological subtitle ordering");
            check(materials.getJSONArray("images").length()==2,"duplicate image URLs collapse");
            check(materials.getJSONArray("images").getJSONObject(0).getDouble("second")==1,"earliest duplicate image wins");
            check(materials.getInt("raw_images")==3,"raw screenshot count preserved");
            f.state="2";int calls=f.materialCalls;fails(()->f.client.materials(f.selected()),"stale ready rejected");check(calls==f.materialCalls,"no material request before fresh ready check");
            f.state="6";f.future=true;fails(()->f.client.materials(f.selected()),"future recording rejected");f.future=false;
            f.transcriptCode="403";fails(()->f.client.materials(f.selected()),"subtitle denial rejected");f.transcriptCode="0";
            f.transcriptId="21";fails(()->f.client.materials(f.selected()),"subtitle identity checked");f.transcriptId="20";
            f.totalTranscript=2;fails(()->f.client.materials(f.selected()),"partial subtitle pagination rejected");f.totalTranscript=1;
            f.emptySubtitles=true;fails(()->f.client.materials(f.selected()),"empty subtitle rejected");f.emptySubtitles=false;
            f.slideCode="403";fails(()->f.client.materials(f.selected()),"slide denial rejected");f.slideCode="0";
            f.slideId="21";fails(()->f.client.materials(f.selected()),"slide identity checked");f.slideId="20";
            f.totalSlides=4;fails(()->f.client.materials(f.selected()),"partial slides rejected");f.totalSlides=3;
            f.imageHost="evil.example";fails(()->f.client.materials(f.selected()),"untrusted image host rejected");f.imageHost="video.cmc.zju.edu.cn";
            f.repeatPages=true;fails(()->f.client.lessons(),"repeated course page rejected");
            check(f.client.image("https://video.cmc.zju.edu.cn/a.jpg").length==4,"media download");
            int requests=f.requests;fails(()->f.client.image("https://attacker.example/a.jpg"),"untrusted download rejected");check(requests==f.requests,"reject unsafe URL before network");
            fails(()->f.client.image("http://video.cmc.zju.edu.cn/a.jpg"),"http media rejected");
            fails(()->f.client.image("https://user@video.cmc.zju.edu.cn/a.jpg"),"userinfo rejected");
            fails(()->f.client.image("https://video.cmc.zju.edu.cn:444/a.jpg"),"nonstandard port rejected");
            f.imageStatus=302;fails(()->f.client.image("https://video.cmc.zju.edu.cn/a.jpg"),"unexpected redirect rejected");f.imageStatus=403;
            fails(()->f.client.image("https://video.cmc.zju.edu.cn/a.jpg"),"media permission denial");f.imageStatus=200;
            f.imageHtml=true;fails(()->f.client.image("https://video.cmc.zju.edu.cn/a.jpg"),"html media rejected");f.imageHtml=false;
            f.imageBytes=0;fails(()->f.client.image("https://video.cmc.zju.edu.cn/a.jpg"),"empty image rejected");
            f.imageBytes=16*1024*1024+1;fails(()->f.client.image("https://video.cmc.zju.edu.cn/a.jpg"),"oversize image rejected");
            f.unknownImageLength=true;fails(()->f.client.image("https://video.cmc.zju.edu.cn/a.jpg"),"unknown-length stream cap enforced");
            f.imageBytes=16*1024*1024;check(f.client.image("https://video.cmc.zju.edu.cn/a.jpg").length==f.imageBytes,"exact stream size cap permitted");
            f.client.cancel();requests=f.requests;fails(()->f.client.lessons(),"cancel prevents request");check(requests==f.requests,"cancel has no network");
        }
        try(ZjuClient school=new ZjuClient("fixture","password")) {
            OkHttpClient old=(OkHttpClient)field(school,"http");int[] logins={0};
            field(school,"http",old.newBuilder().addInterceptor(chain->{
                Request r=chain.request();String host=r.url().host(),path=r.url().encodedPath();
                if(!r.url().isHttps())throw new AssertionError("all actual requests must remain HTTPS");
                if(path.equals("/cas/login")) {
                    if(r.url().queryParameter("service")!=null&&r.url().queryParameter("service").startsWith("http://"))
                        return redirect(r,r.url().queryParameter("service")+"?ticket=fixture");
                    if(r.method().equals("POST")){logins[0]++;return redirect(r,r.url().queryParameter("service")==null?"https://zjuam.zju.edu.cn/cas/login?success":"https://courses.zju.edu.cn/user/index");}
                    return response(r,200,"text/html","<input name=\"execution\" value=\"fixture\">");
                }
                if(path.endsWith("getPubKey"))return response(r,200,"application/json","{\"modulus\":\"ffffffffffffffffffffffffffffffff\",\"exponent\":\"010001\"}");
                if(host.equals("tgmedia.cmc.zju.edu.cn")&&!path.equals("/finish"))return redirect(r,"https://zjuam.zju.edu.cn/cas/oauth2.0/authorize");
                if(path.endsWith("authorize"))return redirect(r,"https://zjuam.zju.edu.cn/cas/login?service=http%3A%2F%2Fzjuam.zju.edu.cn%2Fcas%2Foauth2.0%2FcallbackAuthorize");
                if(path.endsWith("callbackAuthorize"))return redirect(r,"https://tgmedia.cmc.zju.edu.cn/finish");
                if(path.equals("/finish"))return response(r,200,"text/html","<meta http-equiv=\"refresh\" content=\"0;URL=https://classroom.zju.edu.cn/\">");
                if(path.equals("/"))return response(r,200,"text/html","fixture");
                return response(r,200,"application/json","{}");
            }).build());
            school.classroomJson("https://yjapi.cmc.zju.edu.cn/fixture");check(logins[0]==1,"CAS no-service plus OAuth and meta redirect complete");
            Method safe=ZjuClient.class.getDeclaredMethod("safeUrl",String.class);safe.setAccessible(true);
            try { safe.invoke(null,"http://zjuam.zju.edu.cn/cas/oauth2.0/callbackAuthorize");throw new AssertionError("generic allowlist must not permit HTTP"); }
            catch(InvocationTargetException expected){check(expected.getCause() instanceof IOException,"HTTP upgrade confined to classroom redirect path");}
            Method callback=ZjuClient.class.getDeclaredMethod("legacyOauthCallback",HttpUrl.class);callback.setAccessible(true);
            for(String rejected:List.of("http://evil.example/cas/oauth2.0/callbackAuthorize","http://zjuam.zju.edu.cn/cas/other",
                    "http://zjuam.zju.edu.cn:81/cas/oauth2.0/callbackAuthorize","http://user@zjuam.zju.edu.cn/cas/oauth2.0/callbackAuthorize"))
                check(!(Boolean)callback.invoke(null,HttpUrl.get(rejected)),"callback upgrade rejects altered authority or path");
            fails(()->school.classroomJson("https://unrelated.zju.edu.cn/fixture"),"classroom API limited to named service hosts");
            Method login=ZjuClient.class.getDeclaredMethod("loginService",String.class);login.setAccessible(true);
            check(login.invoke(school,"https://courses.zju.edu.cn/user/index").equals("https://courses.zju.edu.cn/user/index"),"existing CAS service contract preserved");
            Object cookies=field(school,"cookies");CookieJar jar=(CookieJar)cookies;
            HttpUrl url=HttpUrl.get("https://classroom.zju.edu.cn/");
            jar.saveFromResponse(url,List.of(new Cookie.Builder().name("fixture").value("fixture").domain("classroom.zju.edu.cn").expiresAt(Long.MAX_VALUE).build()));
            school.close();check(jar.loadForRequest(url).isEmpty(),"close clears cookies");
            jar.saveFromResponse(url,List.of(new Cookie.Builder().name("late").value("fixture").domain("classroom.zju.edu.cn").build()));
            check(jar.loadForRequest(url).isEmpty(),"close blocks late cookie callback");
        }
        System.out.println("Classroom client synthetic checks: "+checks+" passed");
    }
    static void live(String accountFile)throws Exception {
        List<String> credentials=Files.readAllLines(Path.of(accountFile));
        String user=credentials.get(0).replace("\uFEFF","").trim(),password=credentials.get(1);credentials.clear();
        try(ClassroomClient client=new ClassroomClient(user,password)) {
            ZjuClient school=(ZjuClient)field(client,"school");OkHttpClient original=(OkHttpClient)field(school,"http");
            field(school,"http",original.newBuilder().addNetworkInterceptor(chain->{
                Request request=chain.request();Response result=chain.proceed(request);
                if(!request.url().isHttps())throw new AssertionError("Live requests must remain HTTPS");
                String p=request.url().encodedPath();
                String stage=p.equals("/cas/login")?"cas":p.contains("oauth")?"oauth":request.url().host().equals("tgmedia.cmc.zju.edu.cn")?"media-auth":"data";
                String service=request.url().queryParameter("service");HttpUrl serviceUrl=service==null?null:HttpUrl.parse(service);
                System.out.println("live status="+result.code()+" stage="+stage+" serviceScheme="+(serviceUrl==null?"none":serviceUrl.scheme()));
                HttpUrl next=request.url().resolve(result.header("Location","/"));String ns=next==null?null:next.queryParameter("service");
                HttpUrl n=ns==null?null:HttpUrl.parse(ns);
                if(ns!=null)System.out.println("live redirect_service_schema="+(n==null?"not-absolute":n.scheme())+" encoded="+ns.startsWith("https%")+" relative="+ns.startsWith("/")+" casHost="+(n!=null&&n.host().equals("zjuam.zju.edu.cn"))+" oauthCallback="+(n!=null&&n.encodedPath().equals("/cas/oauth2.0/callbackAuthorize")));
                return result;
            }).build());
            user="";password="";List<ClassroomLesson> lessons=client.lessons();
            System.out.println("live lessons="+lessons.size()+" ready="+lessons.stream().filter(l->l.selectable(System.currentTimeMillis())).count());
            ClassroomLesson selected=lessons.stream().filter(l->l.selectable(System.currentTimeMillis())).findFirst().orElseThrow();
            JSONObject materials=client.materials(selected);
            JSONArray images=materials.getJSONArray("images");
            System.out.println("live segments="+materials.getJSONArray("segments").length()+" image_urls="+images.length()+" raw_images="+materials.getInt("raw_images"));
            if(images.length()>0)System.out.println("live downloaded_image_bytes="+client.image(images.getJSONObject(0).getString("url")).length);
        }catch(Exception e) {
            System.out.println("live failed_type="+e.getClass().getSimpleName());
            // Production client exceptions contain fixed local messages, never server response text.
            if(e instanceof ZjuClient.SchoolException)System.out.println("live failure="+e.getMessage());
            for(StackTraceElement frame:e.getStackTrace())if(frame.getClassName().startsWith("com.donglan.chrona"))
                System.out.println("live frame="+frame.getMethodName()+":"+frame.getLineNumber());
            throw new IOException("Live classroom check failed");
        }
    }
    public static void main(String[]args)throws Exception { synthetic();if(args.length==2&&args[0].equals("--live"))live(args[1]); }
}
