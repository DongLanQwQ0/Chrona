package com.donglan.chrona;

import android.app.Instrumentation;
import android.os.Bundle;
import com.donglan.chrona.classroom.ClassroomClient;
import com.donglan.chrona.classroom.ClassroomLesson;
import com.donglan.chrona.classroom.ClassroomStore;
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

/** Read existing cache/endpoints. The explicit aiPing mode sends one small authorized AI request. */
public final class ClassroomProbeInstrumentation extends Instrumentation {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private boolean network;
    private String requestedDate;
    private boolean assets;
    private boolean cleanup;
    private boolean audit;
    private boolean parseAudit;
    private boolean aiPing;
    private boolean classroomRoute;
    private boolean healthImage;
    private boolean healthJson;
    private boolean healthSchema;
    private int healthImageCount=1;
    private String healthModel;
    private boolean mathAudit;
    private boolean monitorAudit;
    private boolean providerFailure;
    private boolean timetableAudit;
    private String reviewKey;
    private boolean reviewExport;
    private final java.util.concurrent.CountDownLatch applicationReady=new java.util.concurrent.CountDownLatch(1);
    private volatile boolean applicationInitializationSkipped;
    @Override public void onCreate(Bundle args) {
        network = args != null && "true".equals(args.getString("network"));
        requestedDate = args == null ? null : args.getString("date");
        assets = args != null && "true".equals(args.getString("assets"));
        cleanup = args != null && "true".equals(args.getString("cleanup"));
        audit = args != null && "true".equals(args.getString("audit"));
        parseAudit = args != null && "true".equals(args.getString("parseAudit"));
        aiPing = args != null && "true".equals(args.getString("aiPing"));
        classroomRoute = args != null && "true".equals(args.getString("classroomRoute"));
        healthImage = args != null && "true".equals(args.getString("healthImage"));
        healthJson = args != null && "true".equals(args.getString("healthJson"));
        healthSchema = args != null && "true".equals(args.getString("healthSchema"));
        healthJson|=healthSchema;
        if(args!=null&&args.containsKey("healthImageCount"))healthImageCount=Integer.parseInt(args.getString("healthImageCount"));
        healthModel = args == null ? null : args.getString("healthModel");
        mathAudit = args != null && "true".equals(args.getString("mathAudit"));
        monitorAudit = args != null && "true".equals(args.getString("monitorAudit"));
        providerFailure = args != null && "true".equals(args.getString("providerFailure"));
        timetableAudit = args != null && "true".equals(args.getString("timetableAudit"));
        reviewKey = args == null ? null : args.getString("reviewKey");
        reviewExport = args != null && "true".equals(args.getString("reviewExport"));
        super.onCreate(args); start();
    }
    @Override public void callApplicationOnCreate(android.app.Application app){
        try{
            if(mathAudit||monitorAudit||aiPing||timetableAudit)applicationInitializationSkipped=true;
            else super.callApplicationOnCreate(app);
        }finally{applicationReady.countDown();}
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!getTargetContext().getPackageName().equals("com.donglan.chrona")) throw new IllegalStateException();
            if(timetableAudit){
                if(!applicationReady.await(10,java.util.concurrent.TimeUnit.SECONDS)||!applicationInitializationSkipped)
                    throw new IllegalStateException("Isolated timetable read required");
                result.putString("timetable_audit",timetableResponseAudit().toString());finish(-1,result);return;
            }
            if(mathAudit||monitorAudit){
                if(!applicationReady.await(10,java.util.concurrent.TimeUnit.SECONDS)||!applicationInitializationSkipped)
                    throw new IllegalStateException("Read-only application initialization unavailable");
                ZjuSettingsStore current=new ZjuSettingsStore(getTargetContext());
                if(!current.enabled()||!current.configured())throw new IllegalStateException("School account unavailable");
                if(mathAudit)result.putString("math_audit",mathHistory(current).toString());
                if(monitorAudit)result.putString("monitor_audit",auditMonitor(current).toString());
                finish(-1,result);return;
            }
            if(aiPing){
                if(!applicationReady.await(10,java.util.concurrent.TimeUnit.SECONDS)||!applicationInitializationSkipped)
                    throw new IllegalStateException("Isolated AI health initialization unavailable");
                result.putString("ai_health",aiHealth().toString());finish(-1,result);return;
            }
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
    /** Explicit school read, bounded C-course fields only; no cookie, credential or account export. */
    private JSONObject timetableResponseAudit()throws Exception{
        ZjuSettingsStore settings=new ZjuSettingsStore(getTargetContext());
        if(!settings.enabled()||!settings.configured())throw new IllegalStateException("School account unavailable");
        JSONArray responses=new JSONArray(),cached=new JSONArray(),expanded=new JSONArray();
        JSONObject calendar=com.donglan.chrona.zju.ZjuCalendarStore.load(getTargetContext()).json();
        for(ZjuSnapshot.Lesson row:new ZjuTimetableStore(getTargetContext()).load(settings.account()))
            if(row.title.contains("C程序设计"))cached.put(courseRow(row));
        if(!network)return new JSONObject().put("explicit_network_requests",false).put("cache_written",false).put("calendar",calendar).put("cached",cached);
        String[] credentials=settings.credentials();
        try(ZjuClient client=new ZjuClient(credentials[0],credentials[1],com.donglan.chrona.zju.ZjuCalendarStore.load(getTargetContext()))){
            java.lang.reflect.Field field=ZjuClient.class.getDeclaredField("http");field.setAccessible(true);
            okhttp3.OkHttpClient transport=(okhttp3.OkHttpClient)field.get(client);
            field.set(client,transport.newBuilder().addInterceptor(chain->{
                okhttp3.Response response=chain.proceed(chain.request());
                if(chain.request().url().host().equals("zdbk.zju.edu.cn")&&chain.request().url().encodedPath().endsWith("/kbcx/xskbcx_cxXsKb.html")&&response.isSuccessful()){
                    try{
                        JSONObject data=new JSONObject(response.peekBody(2*1024*1024).string()),projection=new JSONObject();
                        if(chain.request().body() instanceof okhttp3.FormBody form)for(int i=0;i<form.size();i++)
                            if(form.name(i).equals("xnm")||form.name(i).equals("xqm"))projection.put(form.name(i),form.value(i));
                        for(String key:new String[]{"kbList","sjkList","jxkList","xskList","ywkList","fKbList"}){
                            JSONArray rows=data.optJSONArray(key),selected=new JSONArray();
                            if(rows!=null)for(int i=0;i<rows.length();i++){
                                JSONObject row=rows.optJSONObject(i);if(row==null||!(row.optString("kcb")+row.optString("kcmc")).contains("C程序设计"))continue;
                                java.util.Set<String> names=new java.util.TreeSet<>();for(java.util.Iterator<String> it=row.keys();it.hasNext();)names.add(it.next());
                                JSONObject safe=new JSONObject().put("field_names",new JSONArray(names));
                                for(String name:new String[]{"kcb","kcmc","djj","skcd","xqj","dsz","zcd","qsrq","jzrq","skrq","jcs","cdmc"}){
                                    Object value=row.opt(name);if(value instanceof Number||value instanceof String text&&text.length()<16000)safe.put(name,value);
                                }
                                selected.put(safe);
                            }
                            projection.put(key,new JSONObject().put("count",rows==null?0:rows.length()).put("selected",selected));
                        }
                        responses.put(projection);
                    }catch(org.json.JSONException invalid){throw new java.io.IOException("Timetable diagnostic projection failed");}
                }
                return response;
            }).build());
            ZjuSnapshot snapshot=client.fetch(false,true,java.util.Collections.emptySet());
            for(ZjuSnapshot.Lesson row:snapshot.lessons)if(row.title.contains("C程序设计"))expanded.put(courseRow(row));
        }finally{Arrays.fill(credentials,"");}
        return new JSONObject().put("explicit_network_requests",true).put("cache_written",false).put("calendar",calendar).put("cached",cached).put("responses",responses).put("expanded",expanded);
    }
    private static JSONObject courseRow(ZjuSnapshot.Lesson row)throws Exception{
        return new JSONObject().put("title",row.title).put("start",Instant.ofEpochMilli(row.start).atZone(ZONE).toString())
                .put("end",Instant.ofEpochMilli(row.end).atZone(ZONE).toString()).put("location",row.location).put("term",row.term);
    }
    /** Final-document metadata only; no messages, raw replies, account identity or model options leave. */
    private JSONObject mathHistory(ZjuSettingsStore settings)throws Exception{
        // ClassroomStore's normal reader may recover AtomicFile backups. Refuse pending recovery
        // and missing roots before entering it, rather than mutating the cache during an audit.
        java.io.File root=new java.io.File(getTargetContext().getNoBackupFilesDir(),"classroom");
        if(!settings.account().matches("[a-zA-Z0-9-]+")||!root.isDirectory()
                ||!new java.io.File(root,settings.account()).isDirectory())throw new java.io.IOException("Existing classroom cache unavailable");
        requireStableCache(root);
        JSONObject report=new JSONObject().put("application_initialization_skipped",applicationInitializationSkipped)
                .put("pending_atomic_recovery",false).put("explicit_network_requests",false);
        JSONArray rows=new JSONArray();JSONObject courses=new JSONObject();int complete=0;
        java.util.Set<String> unique=new java.util.HashSet<>();JSONArray duplicates=new JSONArray();
        String currentKey=null;
        if(providerFailure){
            com.donglan.chrona.ai.AiSettings current=new com.donglan.chrona.ai.AiSettingsStore(getTargetContext()).load();
            if(current==null)throw new IllegalStateException("AI configuration unavailable");
            currentKey=current.apiKey;
            // This fixed fixture contains no real credential and never leaves process memory.
            String fixture="Bearer fixtureCredentialValue123 api_key=fixtureKeyValue123 sk-fixtureKey01234567890123456789 "+currentKey
                    +"\nauthorization=\"Basic dXNlcjpwYXNz\"\nCookie: session=abc123; sid=def456\n"
                    +"password=\"one two three\"\naccess_token='shortTokenValue'\nBasic c2VjcmV0";
            String masked=providerExcerpt(fixture,currentKey);
            if(masked.contains(currentKey)||masked.contains("fixtureCredentialValue123")||masked.contains("fixtureKeyValue123")
                    ||masked.contains("dXNlcjpwYXNz")||masked.contains("abc123")||masked.contains("def456")
                    ||masked.contains("one two three")||masked.contains("shortTokenValue")||masked.contains("c2VjcmV0")||masked.length()>320)
                throw new IllegalStateException("Provider error redaction failed");
            report.put("provider_error_redaction_checked",true);
        }
        com.donglan.chrona.classroom.ClassroomStore store=new com.donglan.chrona.classroom.ClassroomStore(getTargetContext(),settings.account());
        synchronized(com.donglan.chrona.classroom.ClassroomStore.LOCK){
            List<JSONObject> history=store.history();report.put("history_total",history.size());
            for(JSONObject summary:history){
                JSONObject identity=summary.optJSONObject("lesson");if(identity==null)continue;
                ClassroomLesson lesson=ClassroomLesson.fromJson(identity);
                if(!(lesson.course.contains("微积分")||lesson.course.contains("线性代数")))continue;
                String key=com.donglan.chrona.classroom.ClassroomDocument.key(identity);
                if(!unique.add(key))duplicates.put(key);
                courses.put(lesson.course,courses.optInt(lesson.course)+1);
                boolean finished=summary.optBoolean("complete");if(finished)complete++;
                JSONObject row=new JSONObject().put("key",key).put("course",lesson.course).put("date",lesson.date().toString())
                        .put("starts_at",lesson.startsAt).put("title",lesson.title).put("complete",finished)
                        .put("updated",summary.optLong("updated")).put("affairs_schema",summary.optInt("affairs_schema",0))
                        .put("note_strength",summary.optInt("note_strength",1)).put("corrected_characters",summary.optString("corrected_transcript").length());
                rows.put(row);
                java.io.File directory;
                try{directory=store.existingSummary(lesson);}catch(java.io.IOException busy){row.put("detail_state","busy_or_unavailable");continue;}
                if(directory==null){
                    // existingSummary has already validated numeric IDs, canonical parents and
                    // active leases. A downloaded/failed first run may have no published summary.
                    java.io.File local=new java.io.File(new java.io.File(root,settings.account()),key);
                    java.io.File imported=new java.io.File(new java.io.File(root,"shared"),key);
                    directory=new java.io.File(local,"run.json").isFile()?local:
                            new java.io.File(imported,"run.json").isFile()?imported:
                            local.isDirectory()?local:imported.isDirectory()?imported:null;
                    if(directory==null){row.put("detail_state","unavailable");continue;}
                    row.put("detail_state","unpublished");
                }
                boolean documentPresent=new java.io.File(directory,"summary.json").isFile();
                row.put("published_summary",documentPresent);
                JSONArray parts=summary.optJSONArray("parts"),headings=new JSONArray();int notes=0,affairs=0,actions=0,noteCharacters=0,affairsCharacters=0,duplicateNoteTexts=0;
                if(documentPresent&&key.equals(reviewKey)){
                    // Only the selected published notebook, including an explicitly labelled draft.
                    // Never return run history, raw model replies or subtitles.
                    JSONObject review=new JSONObject().put("complete",finished);
                    if(parts!=null)review.put("parts",new JSONArray(parts.toString()));
                    else{
                        review.put("overview",summary.optString("overview"));
                        for(String collection:new String[]{"notes","affairs","assignments"}){
                            JSONArray entries=summary.optJSONArray(collection);if(entries!=null)review.put(collection,new JSONArray(entries.toString()));
                        }
                    }
                    if(reviewExport){
                        java.io.File cache=getTargetContext().getExternalCacheDir();if(cache==null)throw new java.io.IOException("Review export cache unavailable");
                        java.io.File output=new java.io.File(cache,"chrona-notebook-review108");
                        if(!output.isDirectory()&&!output.mkdirs())throw new java.io.IOException("Review export directory unavailable");
                        java.io.File zip=new java.io.File(output,"review.zip");
                        try(java.io.FileOutputStream stream=new java.io.FileOutputStream(zip)){
                            long end=com.donglan.chrona.classroom.ClassroomExport.periodEnd(ClassroomLesson.fromJson(summary.getJSONObject("lesson")),com.donglan.chrona.zju.ZjuCalendarStore.load(getTargetContext()).ends);
                            com.donglan.chrona.classroom.ClassroomExport.write(directory,summary,stream,value->{},end);
                        }
                        review.put("export_cache",zip.getAbsolutePath());
                    }
                    row.put("notebook_review",review);
                }
                java.util.Set<String> noteTexts=new java.util.HashSet<>();
                if(parts!=null)for(int p=0;p<parts.length();p++){
                    JSONObject part=parts.getJSONObject(p);
                    for(String field:new String[]{"notes","affairs","assignments"}){
                        JSONArray entries=part.optJSONArray(field);if(entries==null)continue;
                        if(field.equals("notes"))notes+=entries.length();else if(field.equals("affairs"))affairs+=entries.length();else actions+=entries.length();
                        if(!field.equals("assignments"))for(int n=0;n<entries.length();n++){
                            String text=entries.getJSONObject(n).optString("text");
                            if(field.equals("notes")){noteCharacters+=text.length();if(!noteTexts.add(text))duplicateNoteTexts++;}
                            else affairsCharacters+=text.length();
                        }
                        if(!field.equals("assignments"))for(int n=0;n<entries.length();n++)headings.put(new JSONObject().put("area",field).put("title",entries.getJSONObject(n).optString("title")));
                    }
                }
                row.put("notes",notes).put("affairs",affairs).put("assignments",actions).put("headings",headings);
                row.put("document_parts",parts==null?0:parts.length()).put("note_characters",noteCharacters)
                        .put("affairs_characters",affairsCharacters).put("duplicate_note_texts",duplicateNoteTexts)
                        .put("overview_characters",summary.optString("overview").length());
                JSONObject materials=com.donglan.chrona.classroom.ClassroomStore.read(new java.io.File(directory,"materials.json"));
                if(materials==null)row.put("source_validation","materials_unavailable");
                else if(!finished)row.put("source_validation","unfinished_document");
                else try{com.donglan.chrona.classroom.ClassroomDocument.portable(summary,materials);row.put("source_validation","passed");}
                    catch(Exception invalid){row.put("source_validation","failed").put("source_error_type",invalid.getClass().getSimpleName());}
                if(!documentPresent)row.put("image_validation","unpublished_document");
                else try{
                    java.util.Set<String> images=com.donglan.chrona.classroom.ClassroomDocument.referencedImages(summary);int valid=0;
                    for(String name:images){java.io.File image=store.summaryImage(lesson,name);
                        android.graphics.BitmapFactory.Options bounds=new android.graphics.BitmapFactory.Options();bounds.inJustDecodeBounds=true;
                        android.graphics.BitmapFactory.decodeFile(image.getPath(),bounds);if(image.length()>0&&bounds.outWidth>0&&bounds.outHeight>0)valid++;}
                    row.put("referenced_images",images.size()).put("readable_images",valid);
                }catch(Exception invalid){row.put("image_validation","failed").put("image_error_type",invalid.getClass().getSimpleName());}
                JSONObject run=com.donglan.chrona.classroom.ClassroomStore.read(new java.io.File(directory,"run.json"));
                row.put("run_present",run!=null);
                boolean matches=run!=null&&!summary.optString("run").isEmpty()&&summary.optString("run").equals(run.optString("run"));
                row.put("run_matches_summary",matches);
                if(run!=null){
                    row.put("run_affairs_schema",run.optInt("affairs_schema",0)).put("run_note_strength",run.optInt("note_strength",1));
                    row.put("correction_reused",!run.optString("correction_reused_from").isEmpty());
                    JSONArray corrected=run.optJSONArray("corrected"),blocks=run.optJSONArray("blocks");
                    row.put("correction_parts",corrected==null?0:corrected.length()).put("source_blocks",blocks==null?0:blocks.length());
                    row.put("run_native_json",run.optBoolean("native_json"));
                    row.put("run_native_json_schema",run.optBoolean("native_json_schema"));
                    row.put("run_affairs_source_first",run.optBoolean("affairs_source_first"));
                    row.put("run_editorial_source_first",run.optBoolean("editorial_source_first"));
                    row.put("run_compact_pipeline",run.optBoolean("compact_pipeline"));
                    row.put("run_compact_affairs_first",run.optBoolean("compact_affairs_first"));
                    row.put("run_counted_image_requests",run.optBoolean("counted_image_requests"));
                    if(run.optBoolean("counted_image_requests")&&run.has("image_plan")){
                        JSONArray counts=new JSONArray(),requests=run.getJSONObject("image_plan").getJSONArray("image_requests");
                        for(int n=0;n<requests.length();n++){JSONObject request=requests.getJSONObject(n);counts.put(new JSONObject().put("start",request.getDouble("start")).put("end",request.getDouble("end")).put("count",request.getInt("count")));}
                        row.put("run_requested_image_counts",counts);
                    }
                    if(run.optBoolean("compact_affairs_first")&&run.has("image_plan")&&run.has("compact_note")){
                        for(String field:new String[]{"affairs","assignments"}){
                            JSONArray first=run.getJSONObject("image_plan").getJSONArray(field),published=run.getJSONObject("compact_note").getJSONArray(field);
                            boolean preserved=published.length()>=first.length();
                            for(int n=0;preserved&&n<first.length();n++)preserved=first.getJSONObject(n).toString().equals(published.getJSONObject(n).toString());
                            row.put("run_first_"+field+"_count",first.length()).put("run_first_"+field+"_preserved",preserved);
                        }
                    }
                    row.put("run_image_plan_origin",run.optString("image_plan_origin"));
                    if(providerFailure&&key.equals(reviewKey)&&run.optBoolean("editorial_source_first"))
                        row.put("run_editorial_input_audit",editorialInputAudit(run,materials));
                    JSONArray actualModes=new JSONArray();JSONObject savedConversation=run.optJSONObject("conversation");
                    if(providerFailure&&key.equals(reviewKey)&&savedConversation!=null){
                        JSONObject repair=new JSONObject();JSONArray reasons=new JSONArray(),messages=savedConversation.optJSONArray("messages");
                        String prefix="上一条回复的JSON结构或字段不完整：",suffix="。这是该块唯一一次格式修复";
                        if(messages!=null)for(int m=0;m<messages.length();m++){
                            JSONObject message=messages.optJSONObject(m);if(message==null||!message.optString("role").equals("user"))continue;
                            Object content=message.opt("content");JSONArray messageParts=content instanceof JSONArray?(JSONArray)content:new JSONArray().put(new JSONObject().put("text",content));
                            for(int p=0;p<messageParts.length();p++){
                                JSONObject part=messageParts.optJSONObject(p);String text=part==null?"":part.optString("text");int end=text.indexOf(suffix);
                                if(text.startsWith(prefix)&&end>prefix.length()){
                                    String detail=text.substring(prefix.length(),end);
                                    reasons.put(detail.length()<160&&com.donglan.chrona.classroom.ClassroomJsonFailure.isJsonFailure(detail)?detail:"结构诊断已隐去");
                                }
                            }
                        }
                        repair.put("reasons",reasons);JSONObject outputs=savedConversation.optJSONObject("outputs");
                        if(outputs!=null&&outputs.has("affairs-plan")){
                            try{
                                JSONObject first=com.donglan.chrona.classroom.ClassroomSummary.parseObject(outputs.getString("affairs-plan")),fields=new JSONObject();
                                for(String field:new String[]{"affairs","assignments","image_requests","image_assessment"}){
                                    Object value=first.opt(field);fields.put(field,value instanceof JSONArray?((JSONArray)value).length():value instanceof JSONObject?"object":value==null?"missing":"other");
                                }
                                JSONArray requests=first.optJSONArray("image_requests");int missing=0;
                                if(requests!=null)for(int n=0;n<requests.length();n++)if(!requests.getJSONObject(n).has("count"))missing++;
                                repair.put("first_fields",fields).put("first_missing_count",missing);
                            }catch(Exception ignored){repair.put("first_parseable",false);}
                        }
                        row.put("run_repair_diagnostic",repair);
                        if(outputs!=null&&outputs.has("whole-note")){
                            // Project only the requested note field, never conversation or settings.
                            String rawNote=outputs.getString("whole-note");
                            java.util.regex.Matcher overview=java.util.regex.Pattern.compile("\"overview\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(rawNote);
                            if(overview.find())row.put("whole_overview_native_literal",overview.group(1).substring(0,Math.min(2000,overview.group(1).length())));
                            try{row.put("whole_overview_parsed",com.donglan.chrona.classroom.ClassroomSummary.parseObject(rawNote).optString("overview"));}
                            catch(Exception ignored){row.put("whole_overview_parseable",false);}
                        }
                    }
                    JSONArray savedTurns=savedConversation==null?null:savedConversation.optJSONArray("turns");
                    if(savedTurns!=null)for(int t=0;t<savedTurns.length();t++){
                        JSONObject turn=savedTurns.getJSONObject(t);String mode=turn.optString("output_mode");
                        actualModes.put(new JSONObject().put("mode",java.util.Set.of("text","json_object","json_schema").contains(mode)?mode:"unknown"));
                    }
                    row.put("run_output_modes",actualModes);
                    JSONObject audit=com.donglan.chrona.classroom.ClassroomAudit.project(run,summary);
                    row.put("run_turns",audit.getJSONArray("turns")).put("run_source_scope_warnings",audit.getJSONArray("source_scope_warnings"))
                            .put("run_revalidations",audit.getJSONArray("revalidations"));
                    JSONObject phases=new JSONObject();for(String name:new String[]{"drafts","parts","editor_notes"})
                        phases.put(name,run.optJSONArray(name)==null?0:run.getJSONArray(name).length());
                    JSONObject editorPlan=run.optJSONObject("editor_plan");JSONArray editorSections=editorPlan==null?null:editorPlan.optJSONArray("sections");
                    phases.put("editor_planned_sections",editorSections==null?0:editorSections.length())
                            .put("editor_section_done",run.optInt("editor_section_done",phases.optInt("editor_notes")))
                            .put("editor_batch_size",run.optInt("editor_batch_size",1));
                    row.put("run_phase_counts",phases);
                    if(key.equals(reviewKey)){
                        if(providerFailure){
                            JSONObject catalogue=ClassroomStore.read(new java.io.File(directory,"candidates.json"));
                            JSONArray candidates=catalogue==null?null:catalogue.optJSONArray("images"),chosen=run.optJSONArray("selected"),drafts=run.optJSONArray("drafts"),coverage=new JSONArray();
                            if(drafts!=null)for(int d=0;d<drafts.length();d++){
                                JSONArray windows=drafts.getJSONObject(d).optJSONArray("image_requests");if(windows==null)continue;
                                for(int w=0;w<windows.length();w++){
                                    JSONObject window=windows.getJSONObject(w);double start=window.getDouble("start"),end=window.getDouble("end");int possible=0;JSONArray files=new JSONArray();
                                    if(candidates!=null)for(int c=0;c<candidates.length();c++){double at=candidates.getJSONObject(c).getDouble("second");if(at>=start&&at<=end)possible++;}
                                    if(chosen!=null)for(int c=0;c<chosen.length();c++){JSONObject image=chosen.getJSONObject(c);double at=image.getDouble("second");if(at>=start&&at<=end)files.put(image.getString("file"));}
                                    coverage.put(new JSONObject().put("group",d).put("window",w).put("start",start).put("end",end).put("candidate_matches",possible).put("selected_matches",files.length()).put("selected_files",files));
                                }
                            }
                            row.put("run_image_window_coverage",coverage);
                        }
                        JSONObject inventory=new JSONObject();
                        for(String phase:new String[]{"drafts","parts"}){
                            JSONArray entries=new JSONArray(),values=run.optJSONArray(phase);
                            if(values!=null)for(int p=0;p<values.length();p++)for(String collection:new String[]{"notes","affairs","assignments"}){
                                JSONArray items=values.getJSONObject(p).optJSONArray(collection);
                                if(items!=null)for(int n=0;n<items.length();n++){
                                    JSONObject item=items.getJSONObject(n);
                                    entries.put(new JSONObject().put("group",p).put("collection",collection)
                                            .put("title",item.optString("title")).put("characters",item.optString("text").length())
                                            .put("sources",item.optJSONArray("sources")));
                                }
                            }
                            inventory.put(phase,entries);
                        }
                        if(editorSections!=null)inventory.put("plan",editorSections);
                        row.put("run_content_inventory",inventory);
                    }
                    JSONObject execution=audit.optJSONObject("execution");
                    if(execution!=null){row.put("run_execution_state",execution.optString("state"));if(key.equals(reviewKey)&&execution.has("detail"))row.put("run_execution_detail",execution.getString("detail"));}
                    JSONObject conversation=run.optJSONObject("conversation");
                    if(conversation!=null){
                        if(key.equals(reviewKey)){
                            int imageCount=0,labelCount=0,mismatches=0;JSONArray sent=conversation.optJSONArray("messages");
                            if(providerFailure&&sent!=null&&sent.length()>0){
                                Object first=sent.getJSONObject(0).opt("content");String system="";
                                if(first instanceof String text)system=text;
                                else if(first instanceof JSONArray pieces)for(int c=0;c<pieces.length();c++)system+=pieces.getJSONObject(c).optString("text");
                                String marker="\n用户自定义要求：\n";int at=system.lastIndexOf(marker);
                                String custom=at<0?"":system.substring(at+marker.length());
                                String current=com.donglan.chrona.ai.SchedulePreferences.load(getTargetContext()).classroomPrompt;
                                row.put("run_custom_prompt_characters",custom.length()).put("current_custom_prompt_characters",current.length())
                                        .put("run_custom_prompt_matches_current",custom.equals(current));
                            }
                            if(sent!=null)for(int m=0;m<sent.length();m++){
                                JSONArray content=sent.getJSONObject(m).optJSONArray("content");if(content==null)continue;
                                for(int c=0;c<content.length();c++){
                                    JSONObject part=content.optJSONObject(c);if(part==null||!java.util.Set.of("input_image","image_url").contains(part.optString("type")))continue;
                                    imageCount++;JSONObject preceding=c==0?null:content.optJSONObject(c-1);String label=preceding==null?"":preceding.optString("text");
                                    java.util.regex.Matcher named=java.util.regex.Pattern.compile("^图片文件：(image-[0-9]+\\.jpg)；紧接的图片仅对应此file。$").matcher(label);
                                    if(!named.matches())continue;labelCount++;
                                    String url=part.opt("image_url") instanceof String text?text:part.getJSONObject("image_url").getString("url");
                                    byte[] actual=java.util.Base64.getDecoder().decode(url.substring(url.indexOf(',')+1));
                                    java.io.File image=new java.io.File(directory,named.group(1));
                                    if(!image.isFile()||!java.util.Arrays.equals(actual,ClassroomStore.bytes(image)))mismatches++;
                                }
                            }
                            row.put("run_attached_images",imageCount).put("run_labelled_images",labelCount).put("run_label_file_mismatches",mismatches);
                            if(providerFailure&&sent!=null){
                                JSONObject original=ClassroomStore.read(new java.io.File(directory,"materials.json"));
                                JSONArray originals=original==null?null:original.optJSONArray("segments");
                                for(int m=0;m<sent.length();m++){
                                    JSONObject message=sent.getJSONObject(m);if(!"user".equals(message.optString("role")))continue;
                                    JSONArray content=message.optJSONArray("content");if(content==null)continue;
                                    for(int c=0;c<content.length();c++){
                                        String text=content.getJSONObject(c).optString("text"),marker="\n全课事务证据：\n";int at=text.lastIndexOf(marker);if(at<0)continue;
                                        JSONObject supplied=new JSONObject(text.substring(at+marker.length()));JSONArray segments=supplied.getJSONObject("original_subtitles").getJSONArray("segments"),ids=new JSONArray();
                                        boolean exact=originals!=null;for(int n=0;n<segments.length();n++){
                                            JSONObject segment=segments.getJSONObject(n);int id=segment.getInt("id");ids.put(id);
                                            exact&=originals!=null&&id>=0&&id<originals.length()&&segment.getString("text").equals(originals.getJSONObject(id).getString("text"));
                                        }
                                        row.put("run_affairs_input_sources",ids).put("run_affairs_input_has_titles",supplied.has("titles"))
                                                .put("run_affairs_input_original_text_exact",exact);
                                    }
                                }
                            }
                        }
                        String protocol=conversation.optString("protocol",com.donglan.chrona.ai.AiSettings.CHAT_COMPLETIONS);
                        if(java.util.Set.of(com.donglan.chrona.ai.AiSettings.RESPONSES,com.donglan.chrona.ai.AiSettings.CHAT_COMPLETIONS).contains(protocol))
                            row.put("run_protocol",protocol);
                    }
                    JSONObject options=conversation==null?null:conversation.optJSONObject("options"),limits=new JSONObject();
                    if(options!=null){
                        Object requestedModel=options.opt("model");
                        if(requestedModel instanceof String model&&model.matches("[A-Za-z0-9_./:-]{1,120}"))limits.put("model",model);
                        for(String name:new String[]{"max_completion_tokens","max_tokens","max_output_tokens"}){
                            Object value=options.opt(name);if(value instanceof Number n&&n.longValue()>=0)limits.put(name,n.longValue());
                        }
                        Object effort=options.opt("reasoning_effort");
                        if(effort==null&&options.optJSONObject("reasoning")!=null)effort=options.getJSONObject("reasoning").opt("effort");
                        if(effort instanceof String text&&java.util.Set.of("none","low","medium","high","max").contains(text))limits.put("reasoning_effort",text);
                    }
                    row.put("frozen_output_options",limits);
                    String[] config=run.optString("config").split("\n",5);
                    if(config.length>=4)try{
                        java.net.URI endpoint=new java.net.URI(config[0]);String host=endpoint.getHost();
                        if(host!=null&&host.matches("[A-Za-z0-9.-]+"))row.put("run_endpoint_host",host);
                        if(java.util.Set.of("auto","none","low","medium","high","max").contains(config[2]))row.put("run_configured_effort",config[2]);
                        if(config[3].matches("[0-9]{1,3}"))row.put("run_image_limit",Integer.parseInt(config[3]));
                    }catch(java.net.URISyntaxException invalid){row.put("run_endpoint_host","invalid");}
                    JSONArray turns=conversation==null?null:conversation.optJSONArray("turns");
                    JSONObject last=turns==null||turns.length()==0?null:turns.optJSONObject(turns.length()-1);
                    if(last!=null){
                        JSONObject usage=last.optJSONObject("usage"),details=usage==null?null:usage.optJSONObject("completion_tokens_details");
                        if(details==null&&usage!=null)details=usage.optJSONObject("output_tokens_details");
                        Object reasoning=details==null?null:details.opt("reasoning_tokens");
                        row.put("run_last_reasoning_tokens",reasoning instanceof Number n&&n.longValue()>=0?n.longValue():JSONObject.NULL);
                        JSONObject response=last.optJSONObject("response");JSONArray choices=response==null?null:response.optJSONArray("choices");
                        if(response!=null){
                            if(providerFailure&&"failed".equals(last.optString("status"))){
                                JSONObject nativeFailure=response.optJSONObject("response"),event=response.optJSONObject("event");
                                JSONObject error=nativeFailure==null?null:nativeFailure.optJSONObject("error");
                                if(error==null&&event!=null)error=event.optJSONObject("error");
                                if(error==null&&event!=null)error=event;
                                if(error!=null){
                                    JSONObject labels=new JSONObject();
                                    for(String field:new String[]{"code","type","message"}){
                                        Object value=error.opt(field);
                                        if(value instanceof String||value instanceof Number)labels.put(field,value);
                                    }
                                    row.put("provider_event_excerpt",providerExcerpt(labels.toString(),currentKey));
                                }
                            }
                            row.put("run_last_stream_content_characters",response.opt("content_chars")==null?JSONObject.NULL:response.opt("content_chars"))
                                    .put("run_last_stream_reasoning_characters",response.opt("reasoning_chars")==null?JSONObject.NULL:response.opt("reasoning_chars"));
                            JSONObject nativeResponse=response.optJSONObject("response");JSONArray nativeOutput=nativeResponse==null?null:nativeResponse.optJSONArray("output");
                            JSONObject nativeTextConfig=nativeResponse==null?null:nativeResponse.optJSONObject("text"),nativeFormat=nativeTextConfig==null?null:nativeTextConfig.optJSONObject("format");
                            String format=nativeFormat==null?"":nativeFormat.optString("type");
                            row.put("run_last_native_format",java.util.Set.of("text","json_object","json_schema").contains(format)?format:"not_reported");
                            JSONArray shapes=new JSONArray();StringBuilder nativeText=new StringBuilder();
                            if(nativeOutput!=null)for(int n=0;n<nativeOutput.length();n++){
                                JSONObject item=nativeOutput.optJSONObject(n);if(item==null)continue;
                                String type=item.optString("type");JSONObject shape=new JSONObject().put("type",java.util.Set.of("message","reasoning","function_call").contains(type)?type:"other");
                                shape.put("encrypted_characters",item.optString("encrypted_content").length());
                                JSONArray content=item.optJSONArray("content");int textChars=0,thoughtChars=0;
                                if(content!=null)for(int c=0;c<content.length();c++){
                                    JSONObject part=content.optJSONObject(c);if(part==null)continue;
                                    if("output_text".equals(part.optString("type"))){String text=part.optString("text");textChars+=text.length();nativeText.append(text);}
                                }
                                JSONArray thoughts=item.optJSONArray("summary");if(thoughts!=null)for(int t=0;t<thoughts.length();t++){
                                    JSONObject part=thoughts.optJSONObject(t);if(part!=null)thoughtChars+=part.optString("text").length();
                                }
                                shapes.put(shape.put("text_characters",textChars).put("summary_characters",thoughtChars));
                            }
                            row.put("run_last_native_output",shapes);
                            java.util.Map<String,Integer> lines=new java.util.HashMap<>();int nonblank=0,maxRepeated=0,repeatedCharacters=0;
                            for(String line:nativeText.toString().split("\\R")){line=line.strip();if(line.length()<20)continue;nonblank++;
                                int count=lines.merge(line,1,Integer::sum);maxRepeated=Math.max(maxRepeated,count);if(count>1)repeatedCharacters+=line.length();}
                            row.put("run_last_native_text_characters",nativeText.length()).put("run_last_long_lines",nonblank)
                                    .put("run_last_max_repeated_line",maxRepeated).put("run_last_repeated_line_characters",repeatedCharacters);
                        }
                        JSONObject choice=choices==null||choices.length()==0?null:choices.optJSONObject(0),message=choice==null?null:choice.optJSONObject("message");
                        Object content=message==null?null:message.opt("content"),reasoningText=message==null?null:message.opt("reasoning_content");
                        row.put("run_last_content_characters",content instanceof String text?text.length():JSONObject.NULL);
                        if(key.equals(reviewKey)&&content instanceof String text){
                            int objects=0,arrays=0;boolean quoted=false,escaped=false;
                            for(int c=0;c<text.length();c++){
                                char ch=text.charAt(c);
                                if(quoted){if(escaped)escaped=false;else if(ch=='\\')escaped=true;else if(ch=='"')quoted=false;continue;}
                                if(ch=='"')quoted=true;else if(ch=='{')objects++;else if(ch=='}')objects--;else if(ch=='[')arrays++;else if(ch==']')arrays--;
                            }
                            String trimmed=text.strip();
                            row.put("run_last_syntax_shape",new JSONObject().put("open_objects",objects).put("open_arrays",arrays)
                                    .put("unfinished_string",quoted).put("last_codepoint",trimmed.isEmpty()?-1:trimmed.codePointBefore(trimmed.length())));
                            if(providerFailure&&objects==1&&arrays==0&&!quoted){
                                try{
                                    JSONObject hypothetical=new JSONObject(text+"}"),required=new JSONObject();
                                    for(String field:new String[]{"overview","notes","affairs","assignments","image_assessment","image_requests"})required.put(field,hypothetical.has(field));
                                    JSONObject assessment=hypothetical.optJSONObject("image_assessment");
                                    row.put("run_last_outer_close_hypothesis",new JSONObject().put("parses",true).put("required_fields",required)
                                            .put("image_requests_inside_assessment",assessment!=null&&assessment.has("image_requests")));
                                }catch(org.json.JSONException stillInvalid){row.put("run_last_outer_close_hypothesis",new JSONObject().put("parses",false));}
                            }
                        }
                        row.put("run_last_reasoning_characters",reasoningText instanceof String text?text.length():JSONObject.NULL);
                        if(content instanceof String text)try{
                            JSONObject object=com.donglan.chrona.classroom.ClassroomSummary.parseObject(text),fields=new JSONObject();
                            row.put("run_last_image_reference_shapes",imageReferenceShapes(object));
                            row.put("run_last_source_shapes",sourceShapes(object));
                            if(key.equals(reviewKey)&&object.optJSONArray("assignments")!=null){
                                JSONArray actionEvidence=new JSONArray(),items=object.getJSONArray("assignments");
                                for(int a=0;a<items.length();a++){
                                    JSONObject item=items.getJSONObject(a);
                                    actionEvidence.put(pick(item,"text","evidence","sources"));
                                }
                                row.put("run_last_assignment_evidence",actionEvidence);
                            }
                            String stage=last.optString("stage");java.util.regex.Matcher finalBlock=java.util.regex.Pattern.compile("^(?:final|draft)-([0-9]+)(?:-.*)?$").matcher(stage);
                            if(finalBlock.matches()&&blocks!=null){
                                int index=Integer.parseInt(finalBlock.group(1));JSONArray block=blocks.optJSONArray(index),ids=new JSONArray();
                                if(block!=null){for(int n=0;n<block.length();n++)ids.put(block.getJSONObject(n).getInt("id"));row.put("run_last_block_sources",ids);}
                                JSONArray correctedParts=run.optJSONArray("corrected");
                                if(block!=null&&correctedParts!=null&&index<correctedParts.length()){
                                    java.util.Set<Integer> allowed=new java.util.HashSet<>(),observed=new java.util.TreeSet<>();
                                    for(int n=0;n<ids.length();n++)allowed.add(ids.getInt(n));
                                    java.util.regex.Matcher markers=java.util.regex.Pattern.compile("(?i)\\[(?:id:)?\\s*([0-9]{1,9})\\s*\\]").matcher(correctedParts.getString(index));
                                    while(markers.find())observed.add(Integer.parseInt(markers.group(1)));
                                    JSONArray outside=new JSONArray();for(int id:observed)if(!allowed.contains(id))outside.put(id);
                                    row.put("run_last_corrected_marker_ids",new JSONArray(observed)).put("run_last_corrected_marker_outside_block",outside);
                                }
                                JSONArray drafts=run.optJSONArray("drafts");JSONObject draft=drafts==null?null:drafts.optJSONObject(index);
                                if(draft!=null)row.put("run_last_draft_source_shapes",sourceShapes(draft));
                                row.put("run_committed_refinements",run.optJSONArray("parts")==null?0:run.getJSONArray("parts").length());
                            }
                            for(String name:new String[]{"overview","notes","affairs","assignments","image_assessment","image_requests","image_reviews","sections","groups"})fields.put(name,object.has(name));
                            row.put("run_last_response_shape","object").put("run_last_response_fields",fields);
                            JSONObject counts=new JSONObject().put("overview_characters",object.optString("overview").length());
                            for(String name:new String[]{"notes","affairs","assignments"})counts.put(name,object.optJSONArray(name)==null?-1:object.getJSONArray(name).length());
                            row.put("run_last_collection_counts",counts);
                            JSONObject plan=run.optJSONObject("editor_plan");JSONArray topics=plan==null?null:plan.optJSONArray("sections");
                            JSONArray edited=run.optJSONArray("editor_notes");int nextTopic=edited==null?0:edited.length();
                            if(topics!=null&&nextTopic<topics.length())row.put("run_next_topic_collection",topics.getJSONObject(nextTopic).optString("collection","notes"));
                            Object requests=object.opt("image_requests");
                            row.put("run_last_image_requests_shape",!object.has("image_requests")?"missing":requests==JSONObject.NULL?"null":requests instanceof JSONArray?"array":requests instanceof JSONObject?"object":requests instanceof String?"string":"other");
                            if(requests instanceof JSONArray list)row.put("run_last_image_requests",list.length());
                            JSONObject assessment=object.optJSONObject("image_assessment");
                            if(assessment!=null){
                                String decision=assessment.optString("decision");
                                row.put("run_last_image_decision",java.util.Set.of("request","not_needed","disabled","unavailable").contains(decision)?decision:"unrecognized");
                            }
                        }catch(Exception invalid){row.put("run_last_response_shape","not_valid_object");}
                    }
                    if(last!=null&&"failed".equals(last.optString("status"))){
                        String failure=last.optString("error");
                        java.util.regex.Matcher http=java.util.regex.Pattern.compile("\\bHTTP\\s+(\\d{3})\\b").matcher(failure);
                        if(http.find()){
                            int status=Integer.parseInt(http.group(1));row.put("run_last_http",status);
                            if(providerFailure&&(status==401||status==403||status==502||status==503))row.put("provider_error_excerpt",providerExcerpt(failure,currentKey));
                        }
                        row.put("run_last_error_category",healthCategory(failure));
                    }
                }
            }
        }
        return report.put("courses",courses).put("math_rows",rows.length()).put("math_unique",unique.size())
                .put("duplicate_keys",duplicates).put("complete_rows",complete).put("rows",rows);
    }
    /** Numeric source identities only: no model prose, raw transcript or private conversation. */
    /** Inspect sent editorial projections without exporting messages, subtitles or credentials. */
    private static JSONArray editorialInputAudit(JSONObject run,JSONObject materials)throws org.json.JSONException{
        JSONArray result=new JSONArray(),originals=materials==null?null:materials.optJSONArray("segments");
        java.util.Map<Integer,String> originalText=new java.util.HashMap<>();
        if(originals!=null)for(int i=0;i<originals.length();i++){
            // materials keeps service rows without IDs; lessonInput assigns the array index.
            JSONObject row=originals.getJSONObject(i);originalText.put(i,row.getString("text"));
        }
        JSONObject conversation=run.optJSONObject("conversation");
        JSONArray turns=conversation==null?null:conversation.optJSONArray("turns"),messages=conversation==null?null:conversation.optJSONArray("messages");
        if(turns==null||messages==null)return result;
        for(int t=0;t<turns.length();t++){
            JSONObject turn=turns.getJSONObject(t);String stage=turn.optString("stage");
            if(!stage.equals("edit-plan")&&!stage.matches("edit-note-[0-9]+"))continue;
            JSONObject audit=new JSONObject().put("stage",stage);result.put(audit);
            try{
                JSONObject message=messages.getJSONObject(turn.getInt("message_count")-1);Object body=message.get("content");String text="";
                if(body instanceof String plain)text=plain;
                else if(body instanceof JSONArray parts)for(int p=0;p<parts.length();p++)text+=parts.getJSONObject(p).optString("text");
                String marker=null;
                for(String candidate:new String[]{"\n已完成笔记索引：\n","\n本组主题清单及提取稿：\n","\n本主题提取稿：\n"})if(text.contains(candidate)){marker=candidate;break;}
                if(marker==null){audit.put("parseable",false);continue;}
                JSONArray supplied=(JSONArray)new org.json.JSONTokener(text.substring(text.indexOf(marker)+marker.length())).nextValue(),inputs=new JSONArray();
                for(int i=0;i<supplied.length();i++){
                    JSONObject row=supplied.getJSONObject(i);JSONArray nested=row.optJSONArray("inputs");
                    if(nested==null)inputs.put(row);else for(int n=0;n<nested.length();n++)inputs.put(nested.getJSONObject(n));
                }
                int sourceInputs=0,segments=0;boolean exact=originals!=null,covered=true,unique=true;
                String contextMarker=stage.equals("edit-plan")?"\n编排原始字幕集合：\n":"\n对应原始字幕（用于核对教师事实、指令方向及与更正文稿的冲突，不仅用于安排引文定位）：\n";
                if(!text.contains(contextMarker)){audit.put("parseable",false);continue;}
                JSONObject context=(JSONObject)new org.json.JSONTokener(text.substring(text.indexOf(contextMarker)+contextMarker.length())).nextValue();
                JSONArray rows=context.getJSONArray("segments");java.util.Set<Integer> present=new java.util.HashSet<>();
                for(int n=0;n<rows.length();n++){
                    JSONObject row=rows.getJSONObject(n);segments++;unique&=present.add(row.getInt("id"));
                    exact&=row.getString("text").equals(originalText.get(row.getInt("id")));
                }
                for(int i=0;i<inputs.length();i++){
                    JSONObject input=inputs.getJSONObject(i);
                    if(!"original_subtitles".equals(input.optString("origin"))){exact=false;continue;}
                    sourceInputs++;JSONArray refs=input.getJSONArray("sources");
                    for(int n=0;n<refs.length();n++)covered&=present.contains(refs.getInt(n));
                }
                audit.put("parseable",true).put("inputs",inputs.length()).put("original_inputs",sourceInputs)
                        .put("subtitle_occurrences",segments).put("original_text_exact",exact)
                        .put("source_ids_present",covered).put("unique_subtitles",unique);
            }catch(org.json.JSONException|ClassCastException invalid){audit.put("parseable",false);}
        }
        return result;
    }

    private static JSONArray sourceShapes(JSONObject object)throws Exception{
        JSONArray result=new JSONArray();
        for(String collection:new String[]{"notes","affairs","assignments"}){
            JSONArray entries=object.optJSONArray(collection);if(entries==null)continue;
            for(int i=0;i<entries.length();i++){
                JSONObject entry=entries.optJSONObject(i);JSONArray refs=entry==null?null:entry.optJSONArray("sources"),safe=new JSONArray();
                if(refs!=null)for(int n=0;n<refs.length();n++){Object value=refs.opt(n);safe.put(value instanceof Number?value:JSONObject.NULL);}
                result.put(new JSONObject().put("field",collection+"["+i+"].sources").put("sources",safe));
            }
        }
        return result;
    }
    /** Explicit diagnostic mode only; conversation messages and model replies stay private. */
    private static JSONArray imageReferenceShapes(JSONObject object)throws Exception{
        JSONArray shapes=new JSONArray();
        addMarkdownImageShapes(shapes,"overview",object.optString("overview"));
        for(String collection:new String[]{"notes","affairs","assignments"}){
            JSONArray entries=object.optJSONArray(collection);if(entries==null)continue;
            for(int n=0;n<entries.length();n++){
                JSONObject entry=entries.optJSONObject(n);if(entry==null)continue;
                String path=collection+"["+n+"]";
                for(String field:new String[]{"title","text"})addMarkdownImageShapes(shapes,path+"."+field,entry.optString(field));
                JSONArray images=entry.optJSONArray("images");if(images!=null)for(int i=0;i<images.length();i++)
                    shapes.put(new JSONObject().put("field",path+".images["+i+"]").put("kind",imagePathKind(images.opt(i))));
            }
        }
        return shapes;
    }
    private static void addMarkdownImageShapes(JSONArray shapes,String field,String markdown){
        org.commonmark.parser.Parser.builder().build().parse(markdown).accept(new org.commonmark.node.AbstractVisitor(){
            @Override public void visit(org.commonmark.node.Image image){
                try{shapes.put(new JSONObject().put("field",field).put("kind",imagePathKind(image.getDestination())));}
                catch(org.json.JSONException invalid){throw new IllegalStateException(invalid);}
            }
        });
    }
    private static String imagePathKind(Object value){
        if(value==null||value==JSONObject.NULL)return "null";
        if(!(value instanceof String path))return value instanceof Number?"number":"other_type";
        if(path.matches("(?:images/)?image-[0-9]+\\.jpg"))return "canonical_jpeg";
        if(path.isBlank())return "empty_string";
        if(path.equals("[]"))return "array_encoded_as_string";
        if(path.matches("[0-9]+"))return "numeric_string";
        if(path.contains("://")||path.startsWith("data:"))return "external_destination";
        if(path.endsWith(".png"))return "png_filename";
        if(path.endsWith(".jpg"))return "noncanonical_jpeg_name";
        return "noncanonical_path";
    }
    private static String providerExcerpt(String error,String key){
        String value=error==null?"":error;
        if(key!=null&&!key.isEmpty())value=value.replace(key,"[credential]");
        value=value.replaceAll("(?is)<script\\b[^>]*>.*?</script>|<style\\b[^>]*>.*?</style>"," ")
                .replaceAll("(?s)<[^>]*>"," ")
                .replaceAll("(?i)\\b(?:Bearer|Basic)\\s+[^\\s,;<>]+","[authorization]")
                .replaceAll("(?is)([\\\"']?(?:api[_-]?key|(?:proxy[_-]?)?authorization|(?:access[_-]?|refresh[_-]?)?token|secret|password|(?:set[_-]?)?cookie|session(?:[_-]?(?:id|token))?|sid)[\\\"']?\\s*[:=]\\s*)(?:\"(?:\\\\.|[^\"\\\\])*\"|'(?:\\\\.|[^'\\\\])*')","$1[credential]")
                // Whole-line fallback deliberately over-redacts headers/cookies and their tail.
                .replaceAll("(?im)([\\\"']?(?:api[_-]?key|(?:proxy[_-]?)?authorization|(?:access[_-]?|refresh[_-]?)?token|secret|password|(?:set[_-]?)?cookie|session(?:[_-]?(?:id|token))?|sid)[\\\"']?\\s*[:=]\\s*)[^\\r\\n]*","$1[credential]")
                .replaceAll("\\bsk-[A-Za-z0-9_-]+","[credential]")
                .replaceAll("https?://[^\\s<>]+","[url]")
                .replaceAll("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}","[email]")
                .replaceAll("[A-Za-z0-9_+/=-]{24,}","[identifier]")
                .replaceAll("\\s+"," ").trim();
        return value.length()<=320?value:value.substring(0,319)+"…";
    }
    private static void requireStableCache(java.io.File path)throws java.io.IOException{
        if(java.nio.file.Files.isSymbolicLink(path.toPath()))throw new java.io.IOException("Symbolic cache path unavailable");
        if(path.getName().endsWith(".bak")||path.getName().endsWith(".new"))throw new java.io.IOException("Pending atomic recovery");
        if(path.isDirectory()){
            java.io.File[] children=path.listFiles();if(children==null)throw new java.io.IOException("Unreadable cache directory");
            for(java.io.File child:children)requireStableCache(child);
        }
    }
    /** Credentials stay in the target app's normal Keystore path; only whitelisted metadata leaves. */
    private JSONObject aiHealth()throws Exception{
        JSONObject report=new JSONObject().put("calls",0).put("classroom_data_sent",false).put("settings_changed",false);
        com.donglan.chrona.ai.AiSettings settings=new com.donglan.chrona.ai.AiSettingsStore(getTargetContext()).load();
        if(settings==null)return report.put("state","not_configured");
        if(healthModel!=null)settings=new com.donglan.chrona.ai.AiSettings(settings.baseUrl,healthModel,settings.apiKey,settings.reasoningEffort,settings.protocol,settings.sessionHeader);
        report.put("model",settings.model);
        report.put("session_header_configured",!settings.sessionHeader.isEmpty());
        android.net.ConnectivityManager connectivity=(android.net.ConnectivityManager)getTargetContext()
                .getSystemService(android.content.Context.CONNECTIVITY_SERVICE);
        android.net.Network bound=connectivity==null?null:connectivity.getBoundNetworkForProcess();
        android.net.Network active=connectivity==null?null:connectivity.getActiveNetwork();
        android.net.NetworkCapabilities caps=active==null?null:connectivity.getNetworkCapabilities(active);
        report.put("classroom_route",classroomRoute)
                .put("cellular_preference",com.donglan.chrona.ai.SchedulePreferences.load(getTargetContext()).classroomCellular)
                .put("default_wifi",caps!=null&&caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI))
                .put("app_vpn",caps!=null&&caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN));
        try(com.donglan.chrona.net.RequestControl control=new com.donglan.chrona.net.RequestControl(60000)){
            com.donglan.chrona.ai.ChatCompletionClient client=classroomRoute
                    ?new com.donglan.chrona.ai.ChatCompletionClient(settings,control,com.donglan.chrona.ai.SchedulePrompt.SYSTEM,new CellularClassroomRoute(getTargetContext()))
                    :new com.donglan.chrona.ai.ChatCompletionClient(settings,control);
            boolean responses=com.donglan.chrona.ai.AiSettings.RESPONSES.equals(settings.protocol);
            Object userContent=healthSchema?"Return a JSON object with ok=true and details.ready=true.":healthJson?"Return a JSON object with one boolean field ok set to true.":"Reply with OK only.";
            if(healthImage){
                if(healthImageCount<1||healthImageCount>40)throw new IllegalArgumentException("Health image count must be 1 to 40");
                android.graphics.Bitmap sample=android.graphics.Bitmap.createBitmap(32,32,android.graphics.Bitmap.Config.ARGB_8888);
                sample.eraseColor(android.graphics.Color.RED);java.io.ByteArrayOutputStream jpeg=new java.io.ByteArrayOutputStream();
                sample.compress(android.graphics.Bitmap.CompressFormat.JPEG,80,jpeg);sample.recycle();
                String url="data:image/jpeg;base64,"+java.util.Base64.getEncoder().encodeToString(jpeg.toByteArray());
                JSONArray content=new JSONArray().put(new JSONObject().put("type",responses?"input_text":"text").put("text","Reply with OK only."));
                for(int i=0;i<healthImageCount;i++)content.put(responses?new JSONObject().put("type","input_image").put("image_url",url):new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",url)));
                userContent=content;
            }
            JSONObject request=client.classroomOptions().put(responses?"input":"messages",new JSONArray()
                    .put(new JSONObject().put("role","system").put("content","Reply briefly."))
                    .put(new JSONObject().put("role","user").put("content",userContent)));
            if(healthJson){
                JSONObject format=new JSONObject().put("type","json_object");
                if(healthSchema){
                    JSONObject detail=new JSONObject().put("type","object").put("properties",new JSONObject().put("ready",new JSONObject().put("type","boolean")))
                            .put("required",new JSONArray().put("ready")).put("additionalProperties",false);
                    JSONObject schema=new JSONObject().put("type","object").put("properties",new JSONObject().put("ok",new JSONObject().put("type","boolean")).put("details",detail))
                            .put("required",new JSONArray().put("ok").put("details")).put("additionalProperties",false);
                    JSONObject config=new JSONObject().put("name","chrona_health").put("strict",true).put("schema",schema);
                    format=responses?config.put("type","json_schema"):new JSONObject().put("type","json_schema").put("json_schema",config);
                    report.put("json_schema_requested",true);
                }
                if(responses)request.put("text",new JSONObject().put("format",format));
                else request.put("response_format",format);
                report.put("json_mode_requested",true);
            }
            report.put("synthetic_images",healthImage?healthImageCount:0);
            report.put("protocol",settings.protocol).put("request_bytes",request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).put("calls",1);
            long began=android.os.SystemClock.elapsedRealtime();
            try{
                JSONObject response=client.completeConversation(request);
                JSONObject choice=response.getJSONArray("choices").getJSONObject(0);
                Object content=choice.getJSONObject("message").opt("content");
                report.put("state","response").put("has_text",content instanceof String&&!((String)content).isBlank());
                if(healthJson&&content instanceof String text)try{report.put("json_object_valid",new JSONObject(text).optBoolean("ok"));}catch(org.json.JSONException invalid){report.put("json_object_valid",false);}
                if(healthSchema&&content instanceof String text)report.put("synthetic_reply",providerExcerpt(text,settings.apiKey));
                String finish=choice.optString("finish_reason");
                if(java.util.Set.of("stop","length","content_filter","tool_calls").contains(finish))report.put("finish_reason",finish);
                JSONObject usage=response.optJSONObject("usage");
                if(usage!=null)for(String field:new String[]{"prompt_tokens","completion_tokens","total_tokens"})
                    if(usage.opt(field) instanceof Number value&&value.longValue()>=0)report.put(field,value.longValue());
            }catch(com.donglan.chrona.ai.ChatCompletionClient.RequestException rejected){
                report.put("state","http_error").put("http",rejected.statusCode).put("category",healthCategory(rejected.getMessage()));
                report.put("detail",providerExcerpt(rejected.getMessage(),settings.apiKey));
            }catch(java.io.IOException failed){report.put("state","io_error").put("error_type",failed.getClass().getSimpleName()).put("category",healthCategory(failed.getMessage()));}
            report.put("elapsed_ms",android.os.SystemClock.elapsedRealtime()-began);
        }
        report.put("process_binding_unchanged",connectivity==null||java.util.Objects.equals(bound,connectivity.getBoundNetworkForProcess()));
        return report;
    }
    private static String healthCategory(String message){
        String detail=message==null?"":message.toLowerCase(java.util.Locale.ROOT);
        if(detail.contains("server_is_overloaded")||detail.contains("server overloaded"))return "service_overloaded";
        if(detail.contains("group_disabled"))return "service_group_disabled";
        if(detail.contains("invalid_api_key")||detail.contains("invalid api key")||detail.contains("incorrect api key")||detail.contains("invalid token"))return "credential_rejected";
        if(detail.contains("country")||detail.contains("region")||detail.contains("territory"))return "region_restriction";
        if(detail.contains("context_length")||detail.contains("context length")||detail.contains("maximum context")||detail.contains("too many tokens"))return "context_limit";
        if(detail.contains("payload too large")||detail.contains("request entity too large")||detail.contains("body size"))return "request_size_limit";
        if(detail.contains("cloudflare")||detail.contains("firewall")||detail.contains("waf"))return "gateway_policy";
        if(detail.contains("quota")||detail.contains("balance")||detail.contains("billing")||detail.contains("insufficient"))return "quota_or_billing";
        if(detail.contains("no available channel")||detail.contains("no channel")||detail.contains("无可用渠道"))return "route_unavailable";
        if(detail.contains("permission")||detail.contains("not permitted")||detail.contains("model access"))return "permission_rejected";
        return "unclassified";
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
                .put("catalog_checked",state.optLong("checked")).put("catalog_count",state.getJSONArray("catalog").length())
                .put("done",state.getJSONArray("done")).put("message",state.optString("message"))
                .put("automatic",new JSONArray(ClassroomMonitorStore.autoTasks(state)))
                .put("summary_images",new ClassroomMonitorStore(getTargetContext(),settings.account()).summaryImages());
        com.donglan.chrona.ai.AiSettings ai=new com.donglan.chrona.ai.AiSettingsStore(getTargetContext()).load();
        report.put("ai_configured",ai!=null);if(ai!=null)report.put("model",ai.model);
        Map<String,ClassroomLesson> inspected=new LinkedHashMap<>();
        for(int i=0;i<rules.length();i++){ClassroomLesson selected=ClassroomLesson.fromJson(rules.getJSONObject(i).getJSONObject("lesson"));inspected.put(ClassroomMonitorStore.key(selected),selected);}
        String day=requestedDate==null?LocalDate.now(ZONE).toString():requestedDate;
        for(ClassroomLesson selected:new ClassroomStore(getTargetContext(),settings.account()).catalog())if(day.equals(selected.date().toString()))inspected.put(ClassroomMonitorStore.key(selected),selected);
        JSONArray monitoredCatalog=state.getJSONArray("catalog");
        for(int i=0;i<monitoredCatalog.length();i++){ClassroomLesson selected=ClassroomLesson.fromJson(monitoredCatalog.getJSONObject(i));if(day.equals(selected.date().toString()))inspected.put(ClassroomMonitorStore.key(selected),selected);}
        for(ClassroomLesson selected:inspected.values()){
            java.io.File dir=new com.donglan.chrona.classroom.ClassroomStore(getTargetContext(),settings.account()).localLesson(selected);
            JSONObject rule=ClassroomMonitorStore.ruleFor(state,selected);
            JSONObject row=new JSONObject().put("course",selected.course).put("course_id",selected.courseId).put("id",selected.id).put("status",selected.status).put("starts",selected.startsAt)
                    .put("timetable_end",ClassroomMonitorTiming.endsAt(getTargetContext(),settings.account(),selected)).put("registered",rule!=null)
                    .put("repeat",rule!=null&&rule.getBoolean("repeat"));
            for(String file:new String[]{"live-status.json","monitor-stage.json","live-transcript.json","live-current.json","monitor-materials.json","monitor-candidates.json","materials.json","candidates.json","summary.json","run.json"}){
                java.io.File path=new java.io.File(dir,file);JSONObject saved=com.donglan.chrona.classroom.ClassroomStore.read(path);JSONObject detail=new JSONObject().put("exists",path.isFile()).put("mtime",path.lastModified());
                if(saved!=null){if(saved.has("at"))detail.put("at",saved.optLong("at"));if(saved.has("state"))detail.put("state",saved.optString("state"));if(saved.has("status"))detail.put("status",saved.optString("status"));for(String list:new String[]{"segments","images"})if(saved.optJSONArray(list)!=null)detail.put(list,saved.getJSONArray(list).length());if(saved.optJSONObject("current")!=null)detail.put("current_chars",saved.getJSONObject("current").optString("text").length());
                    for(String flag:new String[]{"complete","monitoring","portable"})if(saved.has(flag))detail.put(flag,saved.optBoolean(flag));
                    if(saved.has("run"))detail.put("run",saved.optString("run"));
                    if(file.equals("run.json")){detail.put("has_draft",saved.has("draft")).put("has_final",saved.has("final")).put("selected",saved.optJSONArray("selected"));
                        JSONObject draft=saved.optJSONObject("draft");if(draft!=null)detail.put("image_requests",draft.optJSONArray("image_requests"));
                        String[] config=saved.optString("config").split("\\n",-1);if(config.length>=4)detail.put("image_limit",config[3]);}
                    if(file.equals("summary.json"))detail.put("referenced_images",com.donglan.chrona.classroom.ClassroomDocument.referencedImages(saved).size());
                }row.put(file,detail);
            }
            if(network){String[] pair=settings.credentials();try(ClassroomClient catalog=new ClassroomClient(pair[0],pair[1]);ZjuClient transport=new ZjuClient(pair[0],pair[1])){Arrays.fill(pair,"");
                JSONArray freshToday=new JSONArray();
                for(ClassroomLesson fresh:catalog.courseLessons(selected)){
                    if(fresh.id.equals(selected.id))row.put("fresh_status",fresh.status).put("fresh_starts",fresh.startsAt);
                    if(day.equals(fresh.date().toString()))freshToday.put(new JSONObject().put("id",fresh.id).put("status",fresh.status).put("starts",fresh.startsAt));
                }row.put("fresh_today",freshToday);
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
