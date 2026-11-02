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
    private boolean mathAudit;
    private boolean monitorAudit;
    private boolean providerFailure;
    private String reviewKey;
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
        mathAudit = args != null && "true".equals(args.getString("mathAudit"));
        monitorAudit = args != null && "true".equals(args.getString("monitorAudit"));
        providerFailure = args != null && "true".equals(args.getString("providerFailure"));
        reviewKey = args == null ? null : args.getString("reviewKey");
        super.onCreate(args); start();
    }
    @Override public void callApplicationOnCreate(android.app.Application app){
        try{
            if(mathAudit||monitorAudit||aiPing)applicationInitializationSkipped=true;
            else super.callApplicationOnCreate(app);
        }finally{applicationReady.countDown();}
    }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (!getTargetContext().getPackageName().equals("com.donglan.chrona")) throw new IllegalStateException();
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
                    JSONObject audit=com.donglan.chrona.classroom.ClassroomAudit.project(run,summary);
                    row.put("run_turns",audit.getJSONArray("turns"));
                    JSONObject execution=audit.optJSONObject("execution");
                    if(execution!=null)row.put("run_execution_state",execution.optString("state"));
                    JSONObject conversation=run.optJSONObject("conversation");
                    if(conversation!=null){
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
                            row.put("run_last_stream_content_characters",response.opt("content_chars")==null?JSONObject.NULL:response.opt("content_chars"))
                                    .put("run_last_stream_reasoning_characters",response.opt("reasoning_chars")==null?JSONObject.NULL:response.opt("reasoning_chars"));
                            JSONObject nativeResponse=response.optJSONObject("response");JSONArray nativeOutput=nativeResponse==null?null:nativeResponse.optJSONArray("output");
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
                        row.put("run_last_reasoning_characters",reasoningText instanceof String text?text.length():JSONObject.NULL);
                        if(content instanceof String text)try{
                            JSONObject object=com.donglan.chrona.classroom.ClassroomSummary.parseObject(text),fields=new JSONObject();
                            row.put("run_last_image_reference_shapes",imageReferenceShapes(object));
                            row.put("run_last_source_shapes",sourceShapes(object));
                            String stage=last.optString("stage");java.util.regex.Matcher finalBlock=java.util.regex.Pattern.compile("^final-([0-9]+)(?:-.*)?$").matcher(stage);
                            if(finalBlock.matches()&&blocks!=null){
                                int index=Integer.parseInt(finalBlock.group(1));JSONArray block=blocks.optJSONArray(index),ids=new JSONArray();
                                if(block!=null){for(int n=0;n<block.length();n++)ids.put(block.getJSONObject(n).getInt("id"));row.put("run_last_block_sources",ids);}
                                JSONArray drafts=run.optJSONArray("drafts");JSONObject draft=drafts==null?null:drafts.optJSONObject(index);
                                if(draft!=null)row.put("run_last_draft_source_shapes",sourceShapes(draft));
                                row.put("run_committed_refinements",run.optJSONArray("parts")==null?0:run.getJSONArray("parts").length());
                            }
                            for(String name:new String[]{"overview","notes","affairs","assignments","image_assessment","image_requests","image_reviews","sections","groups"})fields.put(name,object.has(name));
                            row.put("run_last_response_shape","object").put("run_last_response_fields",fields);
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
                            if(providerFailure&&(status==401||status==403||status==502))row.put("provider_error_excerpt",providerExcerpt(failure,currentKey));
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
            JSONObject request=client.classroomOptions().put(responses?"input":"messages",new JSONArray()
                    .put(new JSONObject().put("role","system").put("content","Reply briefly."))
                    .put(new JSONObject().put("role","user").put("content","Reply with OK only.")));
            report.put("protocol",settings.protocol).put("request_bytes",request.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length).put("calls",1);
            long began=android.os.SystemClock.elapsedRealtime();
            try{
                JSONObject response=client.completeConversation(request);
                JSONObject choice=response.getJSONArray("choices").getJSONObject(0);
                Object content=choice.getJSONObject("message").opt("content");
                report.put("state","response").put("has_text",content instanceof String&&!((String)content).isBlank());
                String finish=choice.optString("finish_reason");
                if(java.util.Set.of("stop","length","content_filter","tool_calls").contains(finish))report.put("finish_reason",finish);
                JSONObject usage=response.optJSONObject("usage");
                if(usage!=null)for(String field:new String[]{"prompt_tokens","completion_tokens","total_tokens"})
                    if(usage.opt(field) instanceof Number value&&value.longValue()>=0)report.put(field,value.longValue());
            }catch(com.donglan.chrona.ai.ChatCompletionClient.RequestException rejected){
                report.put("state","http_error").put("http",rejected.statusCode).put("category",healthCategory(rejected.getMessage()));
            }catch(java.io.IOException failed){report.put("state","io_error").put("error_type",failed.getClass().getSimpleName());}
            report.put("elapsed_ms",android.os.SystemClock.elapsedRealtime()-began);
        }
        report.put("process_binding_unchanged",connectivity==null||java.util.Objects.equals(bound,connectivity.getBoundNetworkForProcess()));
        return report;
    }
    private static String healthCategory(String message){
        String detail=message==null?"":message.toLowerCase(java.util.Locale.ROOT);
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
