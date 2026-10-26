package com.donglan.chrona;

import android.app.Instrumentation;
import android.os.Bundle;
import com.donglan.chrona.classroom.ClassroomClient;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.zju.ZjuClient;
import com.donglan.chrona.zju.ZjuSnapshot;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Read existing cache and authenticated school endpoints. No data writes, fixtures or AI calls. */
public final class ClassroomProbeInstrumentation extends Instrumentation {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private boolean network;
    private String requestedDate;
    private boolean assets;
    private boolean cleanup;
    private boolean audit;
    private boolean parseAudit;
    @Override public void onCreate(Bundle args) {
        network = args != null && "true".equals(args.getString("network"));
        requestedDate = args == null ? null : args.getString("date");
        assets = args != null && "true".equals(args.getString("assets"));
        cleanup = args != null && "true".equals(args.getString("cleanup"));
        audit = args != null && "true".equals(args.getString("audit"));
        parseAudit = args != null && "true".equals(args.getString("parseAudit"));
        super.onCreate(args); start();
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!getTargetContext().getPackageName().equals("com.donglan.chrona")) throw new IllegalStateException();
            if(cleanup){result.putInt("removed_public_assets",cleanupPublicAssets());finish(-1,result);return;}
            if(parseAudit){result.putString("parse_audit",recentParseAudits().toString());finish(-1,result);return;}
            ZjuSettingsStore settings = new ZjuSettingsStore(getTargetContext());
            if (!settings.enabled() || !settings.configured()) throw new IllegalStateException("School account unavailable");
            if(audit){result.putString("monitor_audit",auditMonitor(settings).toString());finish(-1,result);return;}
            Map<String, List<ZjuSnapshot.Lesson>> groups = new LinkedHashMap<>();
            for (ZjuSnapshot.Lesson lesson : new ZjuTimetableStore(getTargetContext()).load(settings.account())) {
                if (!(lesson.title.contains("线性代数") || lesson.title.contains("数字音视频"))) continue;
                var from = Instant.ofEpochMilli(lesson.start).atZone(ZONE);
                var until = Instant.ofEpochMilli(lesson.end).atZone(ZONE);
                String key = lesson.term + "/" + lesson.title + "/" + from.getDayOfWeek() + "/"
                        + from.toLocalTime() + "–" + until.toLocalTime() + "/" + lesson.location;
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(lesson);
            }
            JSONArray timetable = new JSONArray();
            for (var group : groups.entrySet()) {
                JSONArray dates = new JSONArray();
                for (ZjuSnapshot.Lesson lesson : group.getValue()) dates.put(Instant.ofEpochMilli(lesson.start).atZone(ZONE).toLocalDate().toString());
                timetable.put(new JSONObject().put("slot", group.getKey()).put("dates", dates));
            }
            JSONObject report = new JSONObject().put("timetable", timetable);
            if(assets)report.put("public_assets",publicAssets());
            if (network) {
                String[] credentials = settings.credentials();
                try (ClassroomClient catalog = new ClassroomClient(credentials[0], credentials[1]);
                     ZjuClient transport = new ZjuClient(credentials[0], credentials[1])) {
                    Arrays.fill(credentials, "");
                    List<ClassroomLesson> lessons = catalog.lessons();
                    long now = System.currentTimeMillis();
                    JSONArray today = new JSONArray(), samples = new JSONArray();
                    ClassroomLesson active = null;
                    LocalDate day = requestedDate == null ? LocalDate.now(ZONE) : LocalDate.parse(requestedDate);
                    for (ClassroomLesson lesson : lessons) if (lesson.date().equals(day)) {
                        today.put(new JSONObject().put("course", lesson.course).put("starts", lesson.startsAt).put("status", lesson.status));
                        if (lesson.course.contains("英语") && lesson.startsAt <= now) active = lesson;
                    }
                    report.put("today", today);
                    if (active != null) {
                        for (int attempt = 0; attempt < 2; attempt++) {
                            JSONObject subtitles = transport.classroomJson("https://yjapi.cmc.zju.edu.cn/courseapi/v3/web-socket/search-trans-result?format=json&sub_id=" + active.id);
                            JSONObject slides = transport.classroomJson("https://classroom.zju.edu.cn/pptnote/v1/schedule/search-ppt?course_id=" + active.courseId + "&sub_id=" + active.id);
                            JSONObject fullSlides=transport.classroomJson("https://classroom.zju.edu.cn/pptnote/v1/schedule/search-ppt?course_id="+active.courseId+"&sub_id="+active.id+"&page=1&per_page=100");
                            JSONObject info=transport.classroomJson("https://yjapi.cmc.zju.edu.cn/courseapi/v3/portal-home-setting/get-sub-info?course_id="+active.courseId+"&sub_id="+active.id);
                            JSONObject sample = new JSONObject().put("at", System.currentTimeMillis()).put("course", active.course).put("status", active.status);
                            sample.put("subtitle", counts(subtitles, true)).put("PPT", counts(slides, false));
                            sample.put("PPT_page_100",counts(fullSlides,false)).put("live_info_code",info.optInt("code",-1));
                            JSONObject detail=info.optJSONObject("data"),content=detail==null?null:detail.optJSONObject("content");
                            if(content!=null){String address=content.optString("trans_socket_url");sample.put("trans_available",!address.isEmpty());
                                if(!address.isEmpty()){android.net.Uri uri=android.net.Uri.parse(address);sample.put("trans_scheme",uri.getScheme()).put("trans_host",uri.getHost()).put("trans_path",uri.getPath().replaceAll("[0-9]{4,}|[A-Za-z0-9_-]{16,}","[redacted]"));
                                    if(attempt==0&&"1".equals(active.status))sample.put("secure_live_socket",socketProbe(transport,address));}}
                            samples.put(sample);
                            if (attempt == 0) Thread.sleep(15000);
                        }
                    }
                    report.put("samples", samples);
                } finally { Arrays.fill(credentials, ""); }
            }
            result.putString("readonly_report", report.toString()); finish(-1, result);
        } catch (Throwable failure) {
            // Never log exceptions from authentication/HTTP with URLs, account data or response bodies.
            result.putString("error_type", failure.getClass().getSimpleName()); finish(0, result);
        }
    }
    private JSONArray recentParseAudits()throws Exception {
        JSONArray result=new JSONArray();
        try(com.donglan.chrona.data.TaskStore store=new com.donglan.chrona.data.TaskStore(getTargetContext());
            android.database.Cursor rows=store.getReadableDatabase().query("parse_runs",new String[]{"id","task_id"},null,null,null,null,"started_at DESC,id DESC","12")){
            while(rows.moveToNext()){
                JSONObject run=store.getParseAudits(rows.getLong(1),null,rows.getString(0),null,0,1).getJSONObject(0);
                JSONObject request=run.getJSONObject("request"),detail=pick(run,"runId","taskId","startedAt","finishedAt","status","error");
                detail.put("request",pick(request,"image_count","attempt","model","reasoning_effort","reference_epoch_millis","time_zone_id"));
                JSONObject parsed=run.optJSONObject("result");if(parsed!=null)detail.put("result",pick(parsed,"prompt_version","reference_epoch_millis","scheduling_epoch_millis","time_zone_id","work_start_minutes","work_end_minutes","usage"));
                JSONArray entries=run.getJSONArray("entries"),safe=new JSONArray();
                for(int i=0;i<entries.length();i++){
                    JSONObject entry=entries.getJSONObject(i),evidence=entry.getJSONObject("evidence");
                    JSONObject item=pick(entry,"candidateId","candidateSyncId").put("evidence",pick(evidence,"position","plan_group","source_time_validation","resolved_time_assessment","adjustments","normalization_note"));
                    for(String key:new String[]{"model","resolved"})if(evidence.optJSONObject(key)!=null)item.getJSONObject("evidence").put(key,pick(evidence.getJSONObject(key),"title","type","kind","category","start","end","start_at","end_at","due_at","deadline","all_day","uncertain","uncertainty_reason","time_assessment"));
                    safe.put(item);
                }
                result.put(detail.put("entries",safe));
            }
        }return result;
    }
    private static JSONObject pick(JSONObject input,String... keys)throws Exception{
        JSONObject result=new JSONObject();for(String key:keys)if(input.has(key))result.put(key,input.get(key));return result;
    }
    private JSONObject auditMonitor(ZjuSettingsStore settings)throws Exception {
        JSONObject state=new ClassroomMonitorStore(getTargetContext(),settings.account()).read(),report=new JSONObject();JSONArray rows=new JSONArray();
        JSONArray rules=state.getJSONArray("rules");report.put("now",System.currentTimeMillis()).put("rules",rules.length())
                .put("done",state.getJSONArray("done")).put("message",state.optString("message"))
                .put("automatic",new JSONArray(ClassroomMonitorStore.autoTasks(state)))
                .put("summary_images",state.optBoolean("summaryImages"));
        com.donglan.chrona.ai.AiSettings ai=new com.donglan.chrona.ai.AiSettingsStore(getTargetContext()).load();
        report.put("ai_configured",ai!=null);if(ai!=null)report.put("model",ai.model);
        for(int i=0;i<rules.length();i++){ClassroomLesson selected=ClassroomLesson.fromJson(rules.getJSONObject(i).getJSONObject("lesson"));
            java.io.File dir=new com.donglan.chrona.classroom.ClassroomStore(getTargetContext(),settings.account()).localLesson(selected);
            JSONObject row=new JSONObject().put("course",selected.course).put("id",selected.id).put("status",selected.status).put("starts",selected.startsAt).put("repeat",rules.getJSONObject(i).getBoolean("repeat"));
            for(String file:new String[]{"live-status.json","monitor-stage.json","live-transcript.json","live-current.json","monitor-materials.json","monitor-candidates.json","materials.json","candidates.json","summary.json","run.json"}){
                java.io.File path=new java.io.File(dir,file);JSONObject saved=com.donglan.chrona.classroom.ClassroomStore.read(path);JSONObject detail=new JSONObject().put("exists",path.isFile()).put("mtime",path.lastModified());
                if(saved!=null){if(saved.has("state"))detail.put("state",saved.optString("state"));if(saved.has("status"))detail.put("status",saved.optString("status"));for(String list:new String[]{"segments","images"})if(saved.optJSONArray(list)!=null)detail.put(list,saved.getJSONArray(list).length());if(saved.optJSONObject("current")!=null)detail.put("current_chars",saved.getJSONObject("current").optString("text").length());
                    for(String flag:new String[]{"complete","monitoring","portable"})if(saved.has(flag))detail.put(flag,saved.optBoolean(flag));
                    if(saved.has("run"))detail.put("run",saved.optString("run"));
                    if(file.equals("run.json")){detail.put("has_draft",saved.has("draft")).put("has_final",saved.has("final")).put("selected",saved.optJSONArray("selected"));
                        JSONObject draft=saved.optJSONObject("draft");if(draft!=null)detail.put("image_requests",draft.optJSONArray("image_requests"));
                        String[] config=saved.optString("config").split("\\n",-1);if(config.length>=4)detail.put("image_limit",config[3]);}
                    if(file.equals("summary.json"))detail.put("referenced_images",com.donglan.chrona.classroom.ClassroomDocument.referencedImages(saved).size());
                }row.put(file,detail);
            }
            if(network){String[] pair=settings.credentials();try(ClassroomClient catalog=new ClassroomClient(pair[0],pair[1]);ZjuClient transport=new ZjuClient(pair[0],pair[1])){Arrays.fill(pair,"");
                for(ClassroomLesson fresh:catalog.courseLessons(selected))if(fresh.id.equals(selected.id)){row.put("fresh_status",fresh.status).put("fresh_starts",fresh.startsAt);break;}
                JSONObject info=transport.classroomJson("https://yjapi.cmc.zju.edu.cn/courseapi/v3/portal-home-setting/get-sub-info?course_id="+selected.courseId+"&sub_id="+selected.id);row.put("info_code",info.optInt("code"));JSONObject data=info.optJSONObject("data"),content=data==null?null:data.optJSONObject("content");if(data!=null)row.put("sub_status",data.opt("sub_status"));if(content!=null){String address=content.optString("trans_socket_url");row.put("live_address",!address.isEmpty());if(!address.isEmpty()&&"1".equals(row.optString("fresh_status"))&&selected.startsAt<=System.currentTimeMillis()&&(data==null||!data.has("sub_status")||"1".equals(data.optString("sub_status"))))row.put("socket_probe",socketProbe(transport,address));}
            }finally{Arrays.fill(pair,"");}}
            rows.put(row);
        }report.put("lessons",rows);return report;
    }
    private static JSONObject counts(JSONObject source, boolean transcript) throws Exception {
        JSONArray rows = source.optJSONArray("list"); int segments = 0; double last = 0;
        if (rows != null && transcript) for (int i = 0; i < rows.length(); i++) {
            JSONArray content = rows.getJSONObject(i).optJSONArray("all_content");
            if (content == null) continue;
            segments += content.length();
            for (int n = 0; n < content.length(); n++) last = Math.max(last, content.getJSONObject(n).optDouble("EndSec", 0));
        }
        return new JSONObject().put("code", source.optInt("code", -1)).put("total", source.optInt("total", -1))
                .put("rows", rows == null ? 0 : rows.length()).put("segments", segments).put("last_second", last)
                .put("generating", source.optString("message").contains("正在生成") || source.optString("msg").contains("正在生成"))
                .put("message", source.optString("message",source.optString("msg")).replaceAll("https?://[^\\s]+", "[URL]").replaceAll("[A-Za-z0-9_-]{16,}|[0-9]{4,}","[redacted]"));
    }
    private JSONArray publicAssets() throws Exception {
        // Public frontend only. This separate client has no CookieJar or school credentials.
        okhttp3.OkHttpClient http=new okhttp3.OkHttpClient.Builder().callTimeout(30,java.util.concurrent.TimeUnit.SECONDS).build();
        okhttp3.HttpUrl root=okhttp3.HttpUrl.get("https://classroom.zju.edu.cn/");String html;
        try(okhttp3.Response response=http.newCall(new okhttp3.Request.Builder().url(root).build()).execute()){
            if(!response.isSuccessful()||response.body()==null)throw new java.io.IOException("Public entry unavailable");html=response.body().string();
        }
        java.io.File directory=new java.io.File(getTargetContext().getExternalFilesDir(null),"classroom-public-assets");
        if(!directory.isDirectory()&&!directory.mkdirs())throw new java.io.IOException("Public asset directory unavailable");
        try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,"public-entry.html"))){out.write(html.getBytes(java.nio.charset.StandardCharsets.UTF_8));}
        java.util.regex.Matcher scripts=java.util.regex.Pattern.compile("<script[^>]+src=[\"']?([^\\s\"'<>]+\\.js[^\\s\"'<>]*)",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(html);
        JSONArray found=new JSONArray();int n=0;
        while(scripts.find()&&n<20){
            okhttp3.HttpUrl url=root.resolve(scripts.group(1));
            if(url==null||!url.isHttps()||!(url.host().equals("classroom.zju.edu.cn")||url.host().equals("resource.cmc.zju.edu.cn")))continue;
            try(okhttp3.Response response=http.newCall(new okhttp3.Request.Builder().url(url).build()).execute()){
                if(!response.isSuccessful()||response.body()==null)continue;byte[] bytes=response.body().bytes();if(bytes.length>12*1024*1024)continue;
                String name="public-"+(n++)+".js";try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,name))){out.write(bytes);}
                found.put(new JSONObject().put("file",name).put("asset",url.encodedPath()).put("bytes",bytes.length));
            }
        }
        // Exact public chunk paths read from the downloaded official webpack manifest/router.
        for(String path: new String[]{"/static/js/0.d83553e0e4be761521a6.js","/static/js/1.c3c2c436ddbebb961ca0.js"}){
            try(okhttp3.Response response=http.newCall(new okhttp3.Request.Builder().url(root.resolve(path)).build()).execute()){
                if(!response.isSuccessful()||response.body()==null)continue;byte[] bytes=response.body().bytes();if(bytes.length>12*1024*1024)continue;
                String name=path.contains("/0.")?"living-common.js":"living-room.js";
                try(java.io.FileOutputStream out=new java.io.FileOutputStream(new java.io.File(directory,name))){out.write(bytes);}
                found.put(new JSONObject().put("file",name).put("asset",path).put("bytes",bytes.length));
            }
        }
        return found;
    }
    private static JSONObject socketProbe(ZjuClient transport,String address)throws Exception {
        okhttp3.HttpUrl source=okhttp3.HttpUrl.parse(address);
        if(source==null||!source.host().matches("qlite[0-9]*\\.cmc\\.zju\\.edu\\.cn")||!source.username().isEmpty()||!source.password().isEmpty())throw new IllegalArgumentException();
        okhttp3.HttpUrl socketUrl=source.newBuilder().scheme("https").port(443).encodedPath(source.encodedPath().replaceAll("/+$","")+"/glue/ws").build();
        java.lang.reflect.Field field=ZjuClient.class.getDeclaredField("http");field.setAccessible(true);
        okhttp3.OkHttpClient http=(okhttp3.OkHttpClient)field.get(transport);
        JSONObject report=new JSONObject();java.util.concurrent.CountDownLatch done=new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.atomic.AtomicInteger frames=new java.util.concurrent.atomic.AtomicInteger();
        okhttp3.WebSocket socket=http.newWebSocket(new okhttp3.Request.Builder().url(socketUrl).header("Origin","https://classroom.zju.edu.cn").build(),new okhttp3.WebSocketListener(){
            public void onOpen(okhttp3.WebSocket ws,okhttp3.Response response){ws.send("in{\"version\":\"1.9.1\"}");}
            public void onMessage(okhttp3.WebSocket ws,String text){try{
                if(text.startsWith("pi")){ws.send("po");return;}
                if(text.startsWith("in")){synchronized(report){report.put("initialized",true);}return;}
                if(text.startsWith("cd")){frames.incrementAndGet();int json=text.indexOf('{');if(json>=0){JSONObject payload=new JSONObject(text.substring(json));synchronized(report){report.put("fields",payload.names());report.put("final", "1".equals(payload.optString("end_time")));report.put("time_fields",new JSONObject().put("time",payload.opt("time")).put("text_begin_time",payload.opt("text_begin_time")).put("text_end_time",payload.opt("text_end_time")));}}}
            }catch(Exception ignored){}}
            public void onFailure(okhttp3.WebSocket ws,Throwable error,okhttp3.Response response){try{synchronized(report){report.put("failure",error.getClass().getSimpleName()).put("http",response==null?0:response.code());}}catch(Exception ignored){}done.countDown();}
        });
        done.await(20,java.util.concurrent.TimeUnit.SECONDS);socket.close(1000,"probe complete");synchronized(report){report.put("frames",frames.get());return new JSONObject(report.toString());}
    }
    private int cleanupPublicAssets()throws Exception {
        java.io.File base=getTargetContext().getExternalFilesDir(null);if(base==null)return 0;
        java.io.File directory=new java.io.File(base,"classroom-public-assets").getCanonicalFile();
        if(!directory.getParentFile().equals(base.getCanonicalFile()))throw new SecurityException();
        java.io.File[] files=directory.listFiles();if(files==null)return 0;int removed=0;
        for(java.io.File file:files){
            if(!file.getName().matches("public-[0-9]+\\.js|public-entry\\.html|living-(common|room)\\.js")
                    ||!file.isFile()||!file.getCanonicalFile().getParentFile().equals(directory))continue;
            if(!file.delete())throw new java.io.IOException("Public asset cleanup incomplete");removed++;
        }
        directory.delete();return removed;
    }
}
