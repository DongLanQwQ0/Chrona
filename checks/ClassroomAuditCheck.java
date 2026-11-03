package com.donglan.chrona.classroom;

import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;

/** Offline projection checks. No school, model, Android account or checkpoint writes. */
public final class ClassroomAuditCheck {
    private static int checks;
    public static void main(String[] args)throws Exception {
        JSONObject run=fixture();String original=run.toString();
        JSONObject projection=ClassroomAudit.project(run,null),turn=turn(projection);
        check(projection.getString("protocol").equals("chat_completions"),"missing frozen protocol keeps original Chat projection");
        check(original.equals(run.toString()),"projection leaves saved source unchanged");
        check(!projection.toString().contains("PRIVATE-"),"private messages, responses, options and turn errors stay local");
        check(turn.getLong("input")==0&&turn.getLong("output")==0&&turn.getLong("total")==0&&turn.getLong("cached")==0,
                "real provider zero usage remains zero");
        check(turn.getLong("context_images")==1,"context image count reads only the recorded prefix");
        JSONObject source=run.getJSONObject("conversation").getJSONArray("turns").getJSONObject(0);
        for(String reason:new String[]{"stop","length","content_filter","tool_calls","function_call"}){
            source.put("finish_reason",reason);
            check(turn(ClassroomAudit.project(run,null)).getString("finish_reason").equals(reason),"known finish reason "+reason);
        }
        source.remove("finish_reason");
        check(turn(ClassroomAudit.project(run,null)).isNull("finish_reason"),"missing finish reason stays unknown");
        for(Object reason:new Object[]{JSONObject.NULL,0,1,true,"", "STOP","stop ","PRIVATE-PROVIDER-REASON",new JSONObject().put("raw","PRIVATE-RAW")}){
            source.put("finish_reason",reason);JSONObject row=turn(ClassroomAudit.project(run,null));
            check(row.has("finish_reason")&&row.isNull("finish_reason"),"untrusted finish reason is explicit unknown");
        }
        source.remove("usage");source.put("message_count",0);
        turn=turn(ClassroomAudit.project(run,null));
        check(turn.isNull("input")&&turn.isNull("output")&&turn.isNull("total")&&turn.isNull("cached"),"missing usage is never inferred as zero");
        check(turn.getLong("context_images")==0,"explicit empty message prefix counts zero images");
        JSONObject execution=new JSONObject();run.put("execution",execution);
        for(String state:new String[]{"queued","running","complete","interrupted","failed"}){
            execution.put("state",state).put("detail","JSONException: Unterminated object at character 10 of {\"PRIVATE-RAW\":\"https://private.invalid/?token=PRIVATE-TOKEN\"}");
            JSONObject current=ClassroomAudit.project(run,null).getJSONObject("execution");
            check(current.getString("state").equals(state),"known current execution state "+state);
            check(!current.toString().contains("PRIVATE-")&&!current.toString().contains("https://"),"old JSON failure is safe in "+state);
            check((state.equals("failed")||state.equals("interrupted"))==current.has("detail"),"only current failed or interrupted state carries a detail");
        }
        execution.put("state","failed");
        for(Object detail:new Object[]{JSONObject.NULL,0,true,new JSONObject().put("raw","PRIVATE-RAW"),""," "}){
            execution.put("detail",detail);
            check(!ClassroomAudit.project(run,null).getJSONObject("execution").has("detail"),"invalid or blank detail is omitted");
        }
        for(Object state:new Object[]{JSONObject.NULL,0,true,"PRIVATE-STATE","FAILED",new JSONObject().put("raw","PRIVATE-RAW")}){
            execution.put("state",state).put("detail","PRIVATE-RAW");
            check(!ClassroomAudit.project(run,null).has("execution"),"unrecognized execution is omitted");
        }
        run.remove("execution");
        JSONObject summary=new JSONObject().put("execution",new JSONObject().put("state","failed").put("detail","PRIVATE-OLD-ERROR"))
                .put("image_audit",new JSONObject().put("selected_count",7).put("analysed_count",6)
                        .put("assessment",new JSONObject().put("decision","not_needed").put("reason","仅旧轮依据")));
        check(!ClassroomAudit.project(run,summary).has("execution"),"missing current execution never inherits summary errors");
        check(ClassroomAudit.project(run,summary).getJSONArray("decisions").length()==0,"current run never mixes previous summary image audit");
        check(!ClassroomAudit.project(null,summary).has("execution"),"summary-only records never invent execution state");
        check(ClassroomAudit.project(null,summary).getJSONArray("decisions").length()==1,"summary-only existing image audit remains readable");
        check(ClassroomAudit.project(null,null).getJSONArray("turns").length()==0,"missing records stay empty");
        diagnostics();requestSizes();responsesProjection();nativeFailureProjection();scopeWarnings();
        System.out.println("ClassroomAudit: "+checks+" checks passed; paid calls=0, checkpoint writes=0");
    }
    private static void scopeWarnings()throws Exception{
        JSONArray saved=new JSONArray()
                .put(new JSONObject().put("stage","draft-0").put("field","assignments[1].sources").put("sources",new JSONArray().put(269)).put("raw","PRIVATE-OUTPUT"))
                .put(new JSONObject().put("stage","edit-note-2").put("field","notes[0].sources").put("sources",new JSONArray().put(0).put(-1).put("PRIVATE-ID")))
                .put(new JSONObject().put("stage","PRIVATE-STAGE").put("field","notes[0].sources").put("sources",new JSONArray().put(1)))
                .put(new JSONObject().put("stage","final-1").put("field","PRIVATE-FIELD").put("sources",new JSONArray().put(1)));
        JSONObject run=fixture().put("source_scope_warnings",saved),summary=new JSONObject().put("source_scope_warnings",saved);
        String before=run.toString();JSONArray rows=ClassroomAudit.project(run,null).getJSONArray("source_scope_warnings");
        check(rows.length()==2&&rows.getJSONObject(0).getJSONArray("sources").getInt(0)==269,"real cross-block source warning survives safe projection");
        check(rows.getJSONObject(1).getJSONArray("sources").toString().equals("[0]"),"warnings expose only nonnegative numeric source ids");
        check(!rows.toString().contains("PRIVATE-"),"warning projection never leaks body or arbitrary fields");
        check(run.toString().equals(before),"warning projection preserves immutable checkpoint");
        check(ClassroomAudit.project(null,summary).getJSONArray("source_scope_warnings").length()==2,"published summary retains source warnings without a run");
        check(ClassroomAudit.project(fixture(),summary).getJSONArray("source_scope_warnings").length()==0,"new run never borrows previous summary warnings");
    }
    private static void diagnostics()throws Exception{
        JSONObject malformed=jsonRun("{\"overview\":\"PRIVATE-RAW https://private.invalid/?token=PRIVATE-TOKEN\"");
        String before=malformed.toString(),detail=diagnostic(malformed);
        check(detail.contains("JSON")&&!detail.contains("PRIVATE-")&&!detail.contains("https://"),"malformed current JSON has a safe syntax diagnosis");
        check(before.equals(malformed.toString()),"diagnosing stored output never mutates source");
        JSONObject missing=validReply();missing.remove("affairs");
        check(diagnostic(jsonRun(missing.toString())).contains("affairs"),"current contract identifies missing affairs");
        JSONObject nested=validReply();nested.put("notes",new JSONArray().put(new JSONObject().put("title","PRIVATE-TITLE").put("sources",new JSONArray()).put("images",new JSONArray())));
        check(diagnostic(jsonRun(nested.toString())).contains("text"),"nested note failure names only its required field");
        JSONObject wrong=validReply();wrong.put("notes",new JSONObject().put("PRIVATE-KEY","PRIVATE-VALUE"));
        check(diagnostic(jsonRun(wrong.toString())).contains("notes"),"wrong container identifies the safe notes field");
        JSONObject valid=jsonRun(validReply().toString());
        check(!ClassroomAudit.project(valid,null).getJSONObject("execution").has("diagnostic"),"valid stored reply is not diagnosed as a structural failure");
        JSONObject history=valid.getJSONObject("conversation");history.getJSONArray("turns").put(0,new JSONObject().put("stage","previous").put("status","received"));
        history.getJSONArray("turns").put(new JSONObject().put("stage","draft-0-repair").put("status","received"));
        history.getJSONObject("outputs").put("previous","{PRIVATE-OLD-INVALID");
        check(!ClassroomAudit.project(valid,null).getJSONObject("execution").has("diagnostic"),"old invalid response never replaces the valid last response");
        for(String state:new String[]{"complete","running","queued"}){
            malformed.getJSONObject("execution").put("state",state);
            check(!ClassroomAudit.project(malformed,null).getJSONObject("execution").has("diagnostic"),"no failure diagnosis in "+state);
        }
        malformed.getJSONObject("execution").put("state","interrupted");
        check(!diagnostic(malformed).isBlank(),"interrupted current JSON failure can retain its safe diagnosis");
        JSONObject last=malformed.getJSONObject("conversation").getJSONArray("turns").getJSONObject(0);last.put("status","failed");
        check(!ClassroomAudit.project(malformed,null).getJSONObject("execution").has("diagnostic"),"a transport failure never diagnoses a prior received stage");
        last.put("status","received").put("stage","missing-output");
        check(!ClassroomAudit.project(malformed,null).getJSONObject("execution").has("diagnostic"),"output must belong to the physical last turn stage");
        last.put("stage","draft-0-repair");malformed.getJSONObject("conversation").getJSONObject("outputs").put("draft-0-repair",new JSONObject().put("PRIVATE-RAW","PRIVATE-VALUE"));
        check(!ClassroomAudit.project(malformed,null).getJSONObject("execution").has("diagnostic"),"non-string output is never coerced into a diagnosis");
        malformed=jsonRun("{PRIVATE-BROKEN");malformed.getJSONObject("execution").put("detail","AI 请求失败（HTTP 413）");
        check(!ClassroomAudit.project(malformed,null).getJSONObject("execution").has("diagnostic"),"HTTP failure never reinterprets a stored response as a JSON failure");
        check(!ClassroomAudit.project(malformed,null).toString().contains("PRIVATE-"),"neither raw syntax, nested values nor old output is projected");
    }
    private static void requestSizes()throws Exception{
        JSONObject run=fixture(),conversation=run.getJSONObject("conversation");
        JSONArray messages=conversation.getJSONArray("messages");
        messages.put(new JSONObject().put("role","assistant").put("content","PRIVATE-中文😀"));
        messages.put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","image_url")
                .put("image_url",new JSONObject().put("url","data:image/jpeg;base64,PRIVATE-"+"A".repeat(65536))))));
        messages.put(new JSONObject().put("role","user").put("content","PRIVATE-LATER-NOT-IN-PREFIX"));
        JSONObject last=new JSONObject().put("stage","final-0").put("status","failed").put("message_count",3);
        conversation.getJSONArray("turns").put(last);run.put("execution",new JSONObject().put("state","failed").put("detail","AI 请求失败（HTTP 413）· 请检查 AI 服务、模型及请求配置"));
        JSONArray prefix=new JSONArray();for(int i=0;i<3;i++)prefix.put(messages.getJSONObject(i));
        String body=new JSONObject(conversation.getJSONObject("options").toString()).put("messages",prefix).toString();
        long expected=body.getBytes(StandardCharsets.UTF_8).length;String before=run.toString();
        JSONObject projected=ClassroomAudit.project(run,null),row=projected.getJSONArray("turns").getJSONObject(1);
        check(row.getLong("estimated_request_bytes")==expected&&expected>body.length(),"legacy 413 uses exact UTF-8 request prefix including actual image bytes");
        check(projected.getJSONArray("turns").getJSONObject(0).isNull("request_bytes")&&!projected.getJSONArray("turns").getJSONObject(0).has("estimated_request_bytes"),"legacy estimate applies only to the current failed last turn");
        check(before.equals(run.toString()),"request reconstruction never changes frozen options or messages");
        check(!projected.toString().contains("PRIVATE-")&&!projected.toString().contains("base64")&&!projected.toString().contains("data:image"),"request size projection contains no request text or image bytes");
        for(Object size:new Object[]{0,123456L}){
            last.put("request_bytes",size);row=ClassroomAudit.project(run,null).getJSONArray("turns").getJSONObject(1);
            check(row.getLong("request_bytes")==((Number)size).longValue()&&!row.has("estimated_request_bytes"),"recorded byte count takes precedence including actual zero");
        }
        for(Object size:new Object[]{JSONObject.NULL,"123",-1,1.5,true,new JSONObject().put("raw","PRIVATE-RAW")}){
            last.put("request_bytes",size);row=ClassroomAudit.project(run,null).getJSONArray("turns").getJSONObject(1);
            check(row.isNull("request_bytes")&&!row.has("estimated_request_bytes"),"invalid recorded byte count is unknown and never reclassified as estimated");
        }
        last.remove("request_bytes");
        for(Object count:new Object[]{JSONObject.NULL,"3",-1,0,1.5,99}){
            last.put("message_count",count);row=ClassroomAudit.project(run,null).getJSONArray("turns").getJSONObject(1);
            check(row.isNull("estimated_request_bytes"),"unreconstructable message prefix remains unknown");
        }
        last.put("message_count",3);JSONObject options=conversation.getJSONObject("options");conversation.remove("options");
        check(ClassroomAudit.project(run,null).getJSONArray("turns").getJSONObject(1).isNull("estimated_request_bytes"),"missing frozen options are never guessed");
        conversation.put("options",options);run.getJSONObject("execution").put("detail","AI 请求失败（HTTP 429）· 稍后继续");
        check(!ClassroomAudit.project(run,null).getJSONArray("turns").getJSONObject(1).has("estimated_request_bytes"),"other HTTP failures never acquire a legacy estimate");
        run.getJSONObject("execution").put("detail","AI 请求失败（HTTP 413）").put("state","complete");
        check(!ClassroomAudit.project(run,null).getJSONArray("turns").getJSONObject(1).has("estimated_request_bytes"),"completed run never estimates an old 413 failure");
        run.getJSONObject("execution").put("state","failed");messages.put(1,JSONObject.NULL);
        check(ClassroomAudit.project(run,null).getJSONArray("turns").getJSONObject(1).isNull("estimated_request_bytes"),"invalid prefix message cannot be reconstructed");
        messages.put(1,new JSONObject().put("role","assistant").put("content","PRIVATE-中文😀"));
        conversation.getJSONArray("turns").put(JSONObject.NULL);
        check(!ClassroomAudit.project(run,null).getJSONArray("turns").getJSONObject(1).has("estimated_request_bytes"),"a trailing invalid record never sends estimation back to an earlier turn");
    }
    private static void responsesProjection()throws Exception{
        JSONObject run=fixture().put("protocol","chat_completions");
        JSONObject conversation=run.getJSONObject("conversation").put("protocol","responses");
        JSONObject options=new JSONObject().put("model","PRIVATE-MODEL").put("stream",true).put("max_output_tokens",32768)
                .put("store",false).put("truncation","disabled")
                .put("include",new JSONArray().put("reasoning.encrypted_content"));
        JSONArray messages=new JSONArray()
                .put(new JSONObject().put("role","user").put("content",new JSONArray()
                        .put(new JSONObject().put("type","input_text").put("text","PRIVATE-中文😀"))
                        .put(new JSONObject().put("type","input_image").put("image_url","data:image/jpeg;base64,PRIVATE-"+"A".repeat(65536)))))
                .put(new JSONObject().put("type","reasoning").put("id","PRIVATE-REASONING-ID").put("encrypted_content","PRIVATE-ENCRYPTED")
                        .put("summary",new JSONArray().put(new JSONObject().put("type","summary_text").put("text","PRIVATE-THOUGHT input_image"))))
                .put(new JSONObject().put("type","message").put("role","assistant").put("content",new JSONArray()
                        .put(new JSONObject().put("type","output_text").put("text","PRIVATE-OUTPUT input_image"))))
                .put(new JSONObject().put("role","user").put("content",new JSONArray()
                        .put(new JSONObject().put("type","input_image").put("image_url","PRIVATE-NATIVE-IMAGE"))
                        .put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","PRIVATE-CHAT-IMAGE")))))
                .put(new JSONObject().put("role","user").put("content",new JSONArray()
                        .put(new JSONObject().put("type","input_image").put("image_url","PRIVATE-LATER-NATIVE-IMAGE"))));
        // Persisted Responses usage retains native fields beside its common counter projection.
        JSONObject usage=new JSONObject().put("input_tokens",101).put("output_tokens",17).put("total_tokens",118)
                .put("input_tokens_details",new JSONObject().put("cached_tokens",64))
                .put("output_tokens_details",new JSONObject().put("reasoning_tokens",11))
                .put("prompt_tokens",101).put("completion_tokens",17)
                .put("prompt_tokens_details",new JSONObject().put("cached_tokens",64));
        JSONObject first=new JSONObject().put("stage","native-first").put("status","received").put("message_count",1)
                .put("request_bytes",123).put("usage",usage);
        JSONObject last=new JSONObject().put("stage","native-failed").put("status","failed").put("message_count",4);
        conversation.put("options",options).put("messages",messages).put("turns",new JSONArray().put(first).put(last));
        run.put("execution",new JSONObject().put("state","failed").put("detail","AI 请求失败（HTTP 413）"));
        JSONObject summary=new JSONObject().put("protocol","chat_completions");String original=run.toString();
        JSONObject projected=ClassroomAudit.project(run,summary);JSONArray rows=projected.getJSONArray("turns");
        check(projected.getString("protocol").equals("responses"),"projection takes frozen conversation protocol rather than run/current-summary fields");
        JSONObject row=rows.getJSONObject(0);
        check(row.getLong("input")==101&&row.getLong("output")==17&&row.getLong("total")==118&&row.getLong("cached")==64,
                "saved native usage mapping projects input/output/total/cache counters");
        check(row.getLong("request_bytes")==123&&!row.has("estimated_request_bytes"),"recorded native request bytes remain authoritative");
        check(row.getLong("context_images")==1,"native first-turn image count is limited to its recorded input prefix");
        check(rows.getJSONObject(1).getLong("context_images")==3,"native prefix counts input_image and image_url, excludes later images and reasoning/text mentions");
        JSONArray prefix=new JSONArray();for(int i=0;i<4;i++)prefix.put(messages.getJSONObject(i));
        String nativeBody=new JSONObject(options.toString()).put("input",prefix).toString();
        long expected=nativeBody.getBytes(StandardCharsets.UTF_8).length;
        long chatBytes=new JSONObject(options.toString()).put("messages",prefix).toString().getBytes(StandardCharsets.UTF_8).length;
        check(expected!=chatBytes&&expected>nativeBody.length()&&rows.getJSONObject(1).getLong("estimated_request_bytes")==expected,
                "native 413 estimate reconstructs exact UTF-8 input body, including encrypted items and image bytes, never Chat messages");
        check(original.equals(run.toString()),"native projection never mutates frozen options, original output items or usage");
        check(!projected.toString().contains("PRIVATE-")&&!projected.toString().contains("base64")&&!projected.toString().contains("encrypted_content"),
                "native projection exposes no input, image bytes, encrypted reasoning or original outputs");
        last.put("request_bytes",expected+10);
        row=ClassroomAudit.project(run,null).getJSONArray("turns").getJSONObject(1);
        check(row.getLong("request_bytes")==expected+10&&!row.has("estimated_request_bytes"),"native recorded request bytes suppress legacy estimation");
        last.remove("request_bytes");
        conversation.put("protocol","chat_completions");
        projected=ClassroomAudit.project(run,null);
        check(projected.getString("protocol").equals("chat_completions")&&projected.getJSONArray("turns").getJSONObject(1).getLong("estimated_request_bytes")==chatBytes,
                "explicit frozen Chat retains its original messages-key estimate");
        conversation.remove("protocol");
        projected=ClassroomAudit.project(run,null);
        check(projected.getString("protocol").equals("chat_completions")&&projected.getJSONArray("turns").getJSONObject(1).getLong("estimated_request_bytes")==chatBytes,
                "missing legacy protocol keeps Chat estimate rather than inferring Responses from content parts");
    }
    private static void nativeFailureProjection()throws Exception{
        JSONObject run=fixture(),conversation=run.getJSONObject("conversation").put("protocol","responses");
        JSONObject options=conversation.getJSONObject("options").put("max_output_tokens",32768);
        JSONObject usage=new JSONObject().put("input_tokens",101).put("output_tokens",17).put("total_tokens",118)
                .put("input_tokens_details",new JSONObject().put("cached_tokens",64))
                .put("output_tokens_details",new JSONObject().put("reasoning_tokens",11));
        JSONObject nativeResponse=new JSONObject().put("id","PRIVATE-RESPONSE-ID").put("status","incomplete")
                .put("usage",usage).put("incomplete_details",new JSONObject().put("reason","max_output_tokens").put("private","PRIVATE-DETAIL"))
                .put("output",new JSONArray().put(new JSONObject().put("type","reasoning").put("encrypted_content","PRIVATE-ENCRYPTED"))
                        .put(new JSONObject().put("type","message").put("content","PRIVATE-BODY")));
        JSONObject evidence=new JSONObject().put("response",nativeResponse).put("usage",usage).put("finish_reason","length")
                .put("terminal_type","response.incomplete").put("content_chars",0).put("reasoning_chars",37);
        JSONObject turn=conversation.getJSONArray("turns").getJSONObject(0).put("status","failed").put("response",evidence)
                .put("usage",usage).put("finish_reason","length").put("terminal_type","response.incomplete");
        run.put("execution",new JSONObject().put("state","failed").put("detail","当前板块输出被截断，未提交内容已回滚"))
                .put("max_output_tokens",999999);
        JSONObject summary=new JSONObject().put("max_output_tokens",888888);String original=run.toString();
        JSONObject projected=ClassroomAudit.project(run,summary),row=turn(projected);
        check(row.getLong("input")==101&&row.getLong("output")==17&&row.getLong("total")==118&&row.getLong("cached")==64,"native-only failed usage projects real input/output/total/cache");
        check(row.getString("finish_reason").equals("length")&&row.getString("native_status").equals("incomplete")
                &&row.getString("incomplete_reason").equals("max_output_tokens"),"failed native output limit remains a length failure with its original incomplete reason");
        check(row.getLong("reasoning_tokens")==11&&row.getLong("output_limit")==32768,"reasoning cost and frozen conversation output limit remain visible");
        check(row.getString("terminal_type").equals("response.incomplete"),"actual SSE incomplete terminal is safely projected");
        check(original.equals(run.toString()),"failure projection never changes raw evidence or frozen options");
        check(!projected.toString().contains("PRIVATE-")&&!projected.toString().contains("encrypted_content")&&!projected.toString().contains("provider_extension"),"failure projection exposes no original output, encrypted reasoning, provider payload or private metadata");
        nativeResponse.getJSONObject("incomplete_details").put("reason","content_filter");
        check(turn(ClassroomAudit.project(run,null)).getString("incomplete_reason").equals("content_filter"),"known content filter reason is safe to project");
        for(Object reason:new Object[]{"PRIVATE-UNKNOWN",JSONObject.NULL,7,new JSONObject().put("raw","PRIVATE-RAW")}){
            nativeResponse.getJSONObject("incomplete_details").put("reason",reason);
            check(turn(ClassroomAudit.project(run,null)).isNull("incomplete_reason"),"unknown malformed native reason remains null");
        }
        for(String status:new String[]{"completed","failed","incomplete"}){
            nativeResponse.put("status",status);
            check(turn(ClassroomAudit.project(run,null)).getString("native_status").equals(status),"known native status is preserved: "+status);
        }
        for(Object status:new Object[]{"PRIVATE-STATUS",JSONObject.NULL,7,new JSONObject().put("raw","PRIVATE-RAW")}){
            nativeResponse.put("status",status);
            check(turn(ClassroomAudit.project(run,null)).isNull("native_status"),"unknown native status never escapes whitelist");
        }
        nativeResponse.put("status","incomplete");
        for(String terminal:new String[]{"response.completed","response.incomplete","response.failed","error"}){
            turn.put("terminal_type",terminal);
            check(turn(ClassroomAudit.project(run,null)).getString("terminal_type").equals(terminal),"known actual SSE terminal is retained: "+terminal);
        }
        for(Object terminal:new Object[]{"PRIVATE-TERMINAL",JSONObject.NULL,7,new JSONObject().put("raw","PRIVATE-RAW")}){
            turn.put("terminal_type",terminal);
            check(turn(ClassroomAudit.project(run,null)).isNull("terminal_type"),"unknown malformed terminal remains null");
        }
        usage.put("prompt_tokens",7).put("completion_tokens",8).put("prompt_tokens_details",new JSONObject().put("cached_tokens",9));
        row=turn(ClassroomAudit.project(run,null));
        check(row.getLong("input")==7&&row.getLong("output")==8&&row.getLong("cached")==9,"explicit common usage remains authoritative over native aliases");
        usage.remove("prompt_tokens");usage.remove("completion_tokens");usage.remove("prompt_tokens_details");
        for(String key:new String[]{"input_tokens","output_tokens","total_tokens"})usage.put(key,0);
        usage.getJSONObject("input_tokens_details").put("cached_tokens",0);
        usage.getJSONObject("output_tokens_details").put("reasoning_tokens",0);options.put("max_output_tokens",0);
        row=turn(ClassroomAudit.project(run,null));
        check(row.getLong("input")==0&&row.getLong("output")==0&&row.getLong("total")==0&&row.getLong("cached")==0
                &&row.getLong("reasoning_tokens")==0&&row.getLong("output_limit")==0,"actual native zero values stay distinct from unknown");
        for(Object invalid:new Object[]{JSONObject.NULL,"17",-1,1.5,true,new JSONObject().put("raw","PRIVATE-RAW")}){
            usage.put("input_tokens",invalid).put("output_tokens",invalid).put("total_tokens",invalid);
            usage.getJSONObject("input_tokens_details").put("cached_tokens",invalid);
            usage.getJSONObject("output_tokens_details").put("reasoning_tokens",invalid);options.put("max_output_tokens",invalid);
            row=turn(ClassroomAudit.project(run,null));
            check(row.isNull("input")&&row.isNull("output")&&row.isNull("total")&&row.isNull("cached")
                    &&row.isNull("reasoning_tokens")&&row.isNull("output_limit"),"invalid native counts and output limit remain unknown");
        }
        usage.remove("input_tokens");usage.remove("output_tokens");usage.remove("total_tokens");
        usage.remove("input_tokens_details");usage.remove("output_tokens_details");options.remove("max_output_tokens");
        row=turn(ClassroomAudit.project(run,summary));
        check(row.isNull("input")&&row.isNull("output")&&row.isNull("total")&&row.isNull("cached")
                &&row.isNull("reasoning_tokens")&&row.isNull("output_limit"),"missing usage and frozen limit never borrow mutable run or summary settings");
        options.put("max_tokens",1024).put("max_completion_tokens",2048);
        check(turn(ClassroomAudit.project(run,null)).getLong("output_limit")==2048,"legacy frozen completion budget precedes legacy max_tokens");
        options.remove("max_completion_tokens");
        check(turn(ClassroomAudit.project(run,null)).getLong("output_limit")==1024,"legacy frozen max_tokens remains readable without guessing current budget");
        options.put("max_output_tokens",4096);
        check(turn(ClassroomAudit.project(run,null)).getLong("output_limit")==4096,"frozen native budget precedes legacy aliases");
        usage.put("completion_tokens_details",new JSONObject().put("reasoning_tokens",6));
        check(turn(ClassroomAudit.project(run,null)).getLong("reasoning_tokens")==6,"Chat reasoning token details remain readable");
        turn.remove("response");
        row=turn(ClassroomAudit.project(run,null));
        check(row.isNull("native_status")&&row.isNull("incomplete_reason"),"legacy or EOF turn has no fabricated native status or incomplete reason");
        check(!ClassroomAudit.project(run,null).toString().contains("PRIVATE-"),"all malformed native metadata remains private");
    }
    private static JSONObject validReply()throws Exception{
        return new JSONObject().put("overview","").put("notes",new JSONArray()).put("affairs",new JSONArray()).put("assignments",new JSONArray())
                .put("image_assessment",new JSONObject().put("decision","disabled").put("reason","未授权图片"))
                .put("image_requests",new JSONArray());
    }
    private static JSONObject jsonRun(String raw)throws Exception{
        JSONObject run=fixture().put("affairs_schema",1).put("blocks",new JSONArray());
        run.put("execution",new JSONObject().put("state","failed").put("detail","Unterminated object at character 1 of PRIVATE-OLD-RAW"));
        run.getJSONObject("conversation").getJSONArray("turns").getJSONObject(0).put("stage","draft-0-repair").put("finish_reason","stop");
        run.getJSONObject("conversation").put("outputs",new JSONObject().put("draft-0-repair",raw));return run;
    }
    private static String diagnostic(JSONObject run)throws Exception{return ClassroomAudit.project(run,null).getJSONObject("execution").getString("diagnostic");}
    private static JSONObject fixture()throws Exception {
        return new JSONObject().put("conversation",new JSONObject()
                .put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",new JSONArray()
                        .put(new JSONObject().put("type","text").put("text","PRIVATE-PROMPT"))
                        .put(new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url","PRIVATE-URL"))))))
                .put("options",new JSONObject().put("secret","PRIVATE-OPTIONS"))
                .put("turns",new JSONArray().put(new JSONObject().put("stage","first").put("status","received")
                        .put("message_count",1).put("started",0).put("finished",0).put("response","PRIVATE-RESPONSE").put("error","PRIVATE-ERROR")
                        .put("usage",new JSONObject().put("prompt_tokens",0).put("completion_tokens",0).put("total_tokens",0)
                                .put("prompt_tokens_details",new JSONObject().put("cached_tokens",0))))));
    }
    private static JSONObject turn(JSONObject projection)throws Exception{return projection.getJSONArray("turns").getJSONObject(0);}
    private static void check(boolean value,String description){checks++;if(!value)throw new AssertionError(description);}
}
