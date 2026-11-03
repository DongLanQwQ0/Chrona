package com.donglan.chrona.classroom;

import org.json.JSONArray;
import org.json.JSONObject;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/** Read-only projection: messages, response bodies, image bytes and credentials never leave the checkpoint. */
public final class ClassroomAudit {
    private static final Pattern HTTP_PAYLOAD_TOO_LARGE=Pattern.compile("\\bHTTP\\s+413\\b");
    private ClassroomAudit() { }

    public static JSONObject project(JSONObject run,JSONObject summary)throws Exception {
        JSONObject result=new JSONObject();JSONArray turns=new JSONArray(),decisions=new JSONArray();
        result.put("turns",turns).put("decisions",decisions);
        JSONObject execution=run==null?null:run.optJSONObject("execution");
        String state=execution==null?"":known(execution.opt("state"),"queued","running","complete","interrupted","failed");
        if(!state.isEmpty()){
            JSONObject current=new JSONObject().put("state",state);
            Object detail=execution.opt("detail");
            if(("failed".equals(state)||"interrupted".equals(state))&&detail instanceof String text&&!text.isBlank())
                current.put("detail",ClassroomJsonFailure.stored(text));
            result.put("execution",current);
        }
        JSONObject conversation=run==null?null:run.optJSONObject("conversation");
        if(conversation!=null){
            result.put("protocol",conversation.optString("protocol","chat_completions"));
            JSONArray source=conversation.optJSONArray("turns"),messages=conversation.optJSONArray("messages");
            JSONObject last=source==null||source.length()==0?null:source.optJSONObject(source.length()-1);
            JSONObject current=result.optJSONObject("execution");
            boolean failed="failed".equals(state)||"interrupted".equals(state);
            String detail=current==null?"":current.optString("detail");
            if(failed&&last!=null&&"received".equals(last.opt("status"))&&ClassroomJsonFailure.isJsonFailure(detail)){
                JSONObject outputs=conversation.optJSONObject("outputs");Object stage=last.opt("stage");
                Object raw=outputs==null||!(stage instanceof String)?null:outputs.opt((String)stage);
                if(raw instanceof String text){
                    String diagnostic=ClassroomJsonFailure.responseDetail(text,(String)stage,run);
                    if(!diagnostic.isBlank()&&!diagnostic.equals(detail))current.put("diagnostic",diagnostic);
                }
            }
            boolean payloadFailure=failed&&HTTP_PAYLOAD_TOO_LARGE.matcher(detail).find();
            if(source!=null)for(int i=0;i<source.length();i++){
                JSONObject turn=source.optJSONObject(i);if(turn==null)continue;
                JSONObject usage=turn.optJSONObject("usage");
                JSONObject row=new JSONObject().put("stage",turn.optString("stage"))
                        .put("status",turn.optString("status"))
                        .put("finish_reason",finishReason(turn.opt("finish_reason")))
                        .put("request_bytes",number(turn,"request_bytes"))
                        .put("started",number(turn,"started")).put("finished",number(turn,"finished"))
                        .put("input",firstNumber(usage,"prompt_tokens","input_tokens")).put("output",firstNumber(usage,"completion_tokens","output_tokens"))
                        .put("total",number(usage,"total_tokens"));
                Object cached=number(usage==null?null:usage.optJSONObject("prompt_tokens_details"),"cached_tokens");
                if(cached==JSONObject.NULL)cached=number(usage==null?null:usage.optJSONObject("input_tokens_details"),"cached_tokens");
                if(cached==JSONObject.NULL)cached=number(usage,"prompt_cache_hit_tokens");
                row.put("cached",cached);
                JSONObject response=turn.optJSONObject("response"),nativeResponse=response==null?null:response.optJSONObject("response");
                String nativeStatus=known(nativeResponse==null?null:nativeResponse.opt("status"),"completed","incomplete","failed","cancelled","queued","in_progress");
                JSONObject incomplete=nativeResponse==null?null:nativeResponse.optJSONObject("incomplete_details");
                String incompleteReason=known(incomplete==null?null:incomplete.opt("reason"),"max_output_tokens","content_filter");
                String terminal=known(turn.opt("terminal_type"),"response.completed","response.incomplete","response.failed","error");
                Object reasoningTokens=number(usage==null?null:usage.optJSONObject("output_tokens_details"),"reasoning_tokens");
                if(reasoningTokens==JSONObject.NULL)reasoningTokens=number(usage==null?null:usage.optJSONObject("completion_tokens_details"),"reasoning_tokens");
                row.put("native_status",nativeStatus.isEmpty()?JSONObject.NULL:nativeStatus)
                        .put("incomplete_reason",incompleteReason.isEmpty()?JSONObject.NULL:incompleteReason)
                        .put("terminal_type",terminal.isEmpty()?JSONObject.NULL:terminal)
                        .put("reasoning_tokens",reasoningTokens)
                        .put("output_limit",firstNumber(conversation.optJSONObject("options"),"max_output_tokens","max_completion_tokens","max_tokens"));
                Object count=number(turn,"message_count");
                if(messages!=null&&count instanceof Long n&&n<=messages.length()){
                    int images=0;for(int m=0;m<n;m++){
                        JSONObject message=messages.optJSONObject(m);JSONArray content=message==null?null:message.optJSONArray("content");
                        if(content!=null)for(int p=0;p<content.length();p++){
                            JSONObject part=content.optJSONObject(p);if(part!=null&&("image_url".equals(part.optString("type"))||"input_image".equals(part.optString("type"))))images++;
                        }
                    }
                    row.put("context_images",images);
                }
                if(turn==last&&payloadFailure&&"failed".equals(turn.opt("status"))&&!turn.has("request_bytes"))
                    row.put("estimated_request_bytes",requestBytes(conversation,messages,count));
                turns.put(row);
            }
        }
        // A new run may coexist with the previous complete summary. Never mix their audits.
        JSONObject imageAudit=run==null?(summary==null?null:summary.optJSONObject("image_audit")):run.optJSONObject("image_audit");
        if(imageAudit!=null){
            result.put("selected",number(imageAudit,"selected_count"));
            Object analysed=number(imageAudit,"analysed_count");
            JSONArray actual=run==null?null:run.optJSONArray("analysed");
            if(actual!=null)analysed=(long)actual.length();
            result.put("analysed",analysed);
        }
        JSONArray drafts=run==null?null:run.optJSONArray("drafts");
        if(drafts==null&&imageAudit!=null)drafts=imageAudit.optJSONArray("blocks");
        if(drafts!=null){for(int i=0;i<drafts.length();i++)decision(decisions,drafts.optJSONObject(i),i+1);}
        else{
            JSONObject draft=run==null?null:run.optJSONObject("draft");
            if(draft!=null)decision(decisions,draft,0);
            else if(imageAudit!=null&&imageAudit.optJSONObject("assessment")!=null)
                decision(decisions,new JSONObject().put("image_assessment",imageAudit.getJSONObject("assessment"))
                        .put("image_requests",imageAudit.optJSONArray("requests")),0);
        }
        JSONObject warningSource=run==null?summary:run;
        JSONArray warnings=new JSONArray(),saved=warningSource==null?null:warningSource.optJSONArray("source_scope_warnings");
        if(saved!=null)for(int i=0;i<saved.length();i++){
            JSONObject row=saved.optJSONObject(i);if(row==null)continue;
            String stage=row.optString("stage"),field=row.optString("field");
            if(!stage.matches("(?:draft|final|edit-note)-[0-9]+")||!field.matches("(?:notes|affairs|assignments)\\[[0-9]+\\]\\.sources"))continue;
            JSONArray sources=new JSONArray(),ids=row.optJSONArray("sources");
            if(ids!=null)for(int n=0;n<ids.length();n++){
                Object value=number(new JSONObject().put("id",ids.opt(n)),"id");if(value!=JSONObject.NULL)sources.put(value);
            }
            if(sources.length()>0)warnings.put(new JSONObject().put("stage",stage).put("field",field).put("sources",sources));
        }
        result.put("source_scope_warnings",warnings);
        return result;
    }
    private static Object requestBytes(JSONObject conversation,JSONArray messages,Object count)throws Exception {
        JSONObject options=conversation.optJSONObject("options");
        if(options==null||messages==null||!(count instanceof Long n)||n<=0||n>messages.length())return JSONObject.NULL;
        JSONArray prefix=new JSONArray();
        for(int i=0;i<n;i++){
            JSONObject message=messages.optJSONObject(i);if(message==null)return JSONObject.NULL;
            prefix.put(message);
        }
        JSONObject request=new JSONObject(options.toString()).put("responses".equals(conversation.optString("protocol"))?"input":"messages",prefix);
        return (long)request.toString().getBytes(StandardCharsets.UTF_8).length;
    }
    private static Object finishReason(Object value){
        String reason=known(value,"stop","length","content_filter","tool_calls","function_call");
        return reason.isEmpty()?JSONObject.NULL:reason;
    }
    private static Object firstNumber(JSONObject source,String... keys){
        for(String key:keys){Object value=number(source,key);if(value!=JSONObject.NULL)return value;}
        return JSONObject.NULL;
    }
    private static String known(Object value,String... allowed){
        if(value instanceof String text)for(String candidate:allowed)if(candidate.equals(text))return text;
        return "";
    }
    private static void decision(JSONArray rows,JSONObject draft,int block)throws Exception {
        JSONObject assessment=draft==null?null:draft.optJSONObject("image_assessment");if(assessment==null)return;
        JSONObject row=new JSONObject().put("block",block).put("decision",assessment.optString("decision"))
                .put("reason",assessment.optString("reason"));JSONArray requests=new JSONArray(),source=draft.optJSONArray("image_requests");
        if(source!=null)for(int i=0;i<source.length();i++){
            JSONObject request=source.optJSONObject(i);if(request==null)continue;
            requests.put(new JSONObject().put("start",seconds(request,"start")).put("end",seconds(request,"end"))
                    .put("reason",request.optString("reason")));
        }
        rows.put(row.put("requests",requests));
    }
    private static Object seconds(JSONObject value,String key){
        Object raw=value.opt(key);if(raw instanceof Number n&&Double.isFinite(n.doubleValue())&&n.doubleValue()>=0)return raw;
        return JSONObject.NULL;
    }
    private static Object number(JSONObject value,String key){
        Object raw=value==null?null:value.opt(key);if(!(raw instanceof Number n))return JSONObject.NULL;
        try{long result=new BigDecimal(n.toString()).longValueExact();return result>=0?result:JSONObject.NULL;}
        catch(ArithmeticException|NumberFormatException invalid){return JSONObject.NULL;}
    }
}
