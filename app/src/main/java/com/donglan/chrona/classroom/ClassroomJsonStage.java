package com.donglan.chrona.classroom;

import com.donglan.chrona.net.RequestControl;
import org.json.*;
import java.io.*;
import java.util.*;

/** One complete structural repair per stage, with explicit continuation after interruption. */
final class ClassroomJsonStage {
    private static final String WIRE="整条响应首、末非空白字符必须分别为{和}；禁止根字符串、根数组、前置说明、思考文字和代码围栏。Markdown仅置于字段值中。JSON字符串内换行用\\n编码，反斜杠按JSON转义；解析后显示正常段落，不要双重转义。"
            +"结尾须闭合全部嵌套数组、对象及最外层对象，末字符是}不代表整体已经闭合；输出前在本次生成内核对引号与括号配对，不输出检查报告。";
    interface Validator { void check(JSONObject result)throws Exception; }
    private final File dir;
    private final JSONObject state;
    private final ClassroomConversation conversation;
    private final RequestControl control;
    ClassroomJsonStage(File dir,JSONObject state,ClassroomConversation conversation,RequestControl control) {
        this.dir=dir;this.state=state;this.conversation=conversation;this.control=control;
    }
    JSONObject generate(String stage,String prompt,List<byte[]> images,Validator validator,String retrySuffix,
            String retryIntroduction,String outputKey)throws Exception {
        return generate(stage,prompt,images,Collections.emptyList(),validator,retrySuffix,retryIntroduction,outputKey);
    }
    JSONObject generate(String stage,String prompt,List<byte[]> images,List<String> imageNames,Validator validator,String retrySuffix,
            String retryIntroduction,String outputKey)throws Exception {
        JSONObject shape=responseShape(stage);
        int attempt=state.optInt(stage+"_attempt");
        if(state.has(stage+"_error")){
            state.put(stage+"_error",ClassroomJsonFailure.stored(state.getString(stage+"_error")));save();
        }
        if(attempt>0){
            JSONObject recovered=revalidateReceived(stage,validator);
            if(recovered!=null)return recovered;
        }
        boolean sent=firstTurn(stage)!=null;
        String request=attempt==0&&!sent?"当前JSON阶段："+stage+"。"+WIRE+"\n"+prompt:
                (attempt>0?retryIntroduction+ClassroomJsonFailure.stored(state.optString(stage+"_error"))+"。":"恢复此前未完成的JSON阶段。")
                +reference(stage)+"本阶段未提交回复已回滚；只依据其中的完整schema、原始资料和历史实际附图，从头完整生成本阶段JSON对象，不续写、不拼接、不编造来源或图片。"+WIRE+stageBoundary(stage)+assignmentEvidenceDiagnostic(stage);
        String raw=text(attempt==0?stage:stage+retrySuffix+attempt,request,attempt==0?images:Collections.emptyList(),attempt==0?imageNames:Collections.emptyList(),outputKey,shape);
        try {return validate(raw,validator);}
        catch(JSONException malformed) {
            // A transport-interrupted repair keeps its identity and attempt, and is restarted by
            // ClassroomConversation. A completed invalid repair advances attempt and exhausts it.
            if(conversation.attempted(stage+"-repair")&&state.optInt(stage+"_repair_attempt",0)!=attempt){
                invalid(stage,attempt,malformed);throw malformed;
            }
            state.put(stage+"_repair_attempt",attempt);save();
            String repaired=text(stage+"-repair","上一条回复的JSON结构或字段不完整："+ClassroomJsonFailure.detail(malformed)
                    +"。这是该块唯一一次格式修复。"+reference(stage)+"本阶段未提交回复已回滚；按该原始请求中的完整schema、原始资料和历史实际附图，从头返回完整JSON对象，不输出补丁或剩余片段，不编造来源、未见图片或课堂事实；学习笔记可按原要求使用标明的补充例题。"+WIRE+stageBoundary(stage)+assignmentEvidenceDiagnostic(stage),Collections.emptyList(),Collections.emptyList(),outputKey,shape);
            try {return validate(repaired,validator);}
            catch(JSONException|IOException invalid){invalid(stage,attempt,invalid);throw invalid;}
        }
        catch(IOException invalid){invalid(stage,attempt,invalid);throw invalid;}
    }
    private static JSONObject validate(String raw,Validator validator)throws Exception {
        JSONObject result=ClassroomSummary.parseObject(raw);validator.check(result);return result;
    }
    /** Re-check a whole completed reply after a local validation fix; never join failed fragments. */
    private JSONObject revalidateReceived(String stage,Validator validator)throws Exception{
        control.check();JSONObject history=state.getJSONObject("conversation"),outputs=history.getJSONObject("outputs");
        if(!history.optString("pending").isEmpty())return null;
        JSONArray turns=history.getJSONArray("turns");
        for(int i=turns.length()-1;i>=0;i--){
            JSONObject turn=turns.getJSONObject(i);String name=turn.optString("stage");
            if(!(name.equals(stage)||name.startsWith(stage+"-")))continue;
            if(!outputs.has(name)){
                if(!emptyServiceFailure(turn))return null;
                continue;
            }
            if(!"received".equals(turn.optString("status"))||!"stop".equals(turn.optString("finish_reason")))return null;
            JSONObject result;
            try{result=validate(outputs.getString(name),validator);}
            catch(JSONException|IOException stillInvalid){return null;}
            control.check();
            state.put(stage+"_revalidated_from",name);state.remove(stage+"_error");save();return result;
        }
        return null;
    }
    private static boolean emptyServiceFailure(JSONObject turn){
        Object finish=turn.opt("finish_reason");
        if(!"failed".equals(turn.optString("status"))||turn.optLong("content_chars")>0||turn.optLong("reasoning_chars")>0
                ||finish!=null&&finish!=JSONObject.NULL&&!"".equals(finish))return false;
        JSONObject evidence=turn.optJSONObject("response");
        if(evidence==null)return "RequestException".equals(turn.optString("type"));
        if(evidence.optLong("content_chars")>0||evidence.optLong("reasoning_chars")>0)return false;
        JSONObject nativeResponse=evidence.optJSONObject("response"),error;
        if(nativeResponse!=null){
            JSONArray output=nativeResponse.optJSONArray("output");
            if(output!=null&&output.length()>0||"incomplete".equals(nativeResponse.optString("status"))
                    ||!nativeResponse.isNull("incomplete_details"))return false;
            error=nativeResponse.optJSONObject("error");
        }else{
            JSONObject event=evidence.optJSONObject("event");
            if(!"error".equals(evidence.optString("terminal_type"))||event==null)return false;
            error=event.optJSONObject("error");if(error==null)error=event;
        }
        if(error==null)return false;
        String code=error.optString("code");
        return code.equals("server_is_overloaded")||code.equals("server_error")||code.equals("rate_limit_exceeded");
    }
    private String stageBoundary(String stage)throws Exception{
        if(stage.equals("first")||stage.startsWith("draft-"))return ClassroomSummary.DRAFT_IMAGE_RULE;
        if(stage.matches("final-[0-9]+")&&state.has("blocks")){
            int index=Integer.parseInt(stage.substring("final-".length()));
            JSONArray groups=ClassroomEnhancedSummary.contentGroups(state.getJSONArray("blocks"),state.optInt("content_batch_size",1));
            return ClassroomEnhancedSummary.refinementScope(groups.getJSONArray(index),state.optInt("affairs_schema",0));
        }
        return "";
    }
    private String assignmentEvidenceDiagnostic(String stage)throws Exception{
        if(!stage.equals("whole-note")&&!stage.matches("(?:draft|final)-[0-9]+"))return "";
        JSONArray blocks=state.optJSONArray("blocks");if(blocks==null)return "";
        JSONObject history=state.getJSONObject("conversation"),outputs=history.getJSONObject("outputs"),reply=null;
        if(!history.optString("pending").isEmpty())return "";
        JSONArray turns=history.getJSONArray("turns");
        for(int i=turns.length()-1;i>=0;i--){
            JSONObject turn=turns.getJSONObject(i);String name=turn.optString("stage");
            if(!(name.equals(stage)||name.startsWith(stage+"-")))continue;
            if(!"received".equals(turn.optString("status"))||!"stop".equals(turn.optString("finish_reason"))||!outputs.has(name))return "";
            try{reply=ClassroomSummary.parseObject(outputs.getString(name));}catch(JSONException malformed){return "";}
            break;
        }
        if(reply==null)return "";JSONArray tasks=reply.optJSONArray("assignments");if(tasks==null)return "";
        JSONArray originals=new JSONArray();Set<Integer> known=new HashSet<>();
        for(int i=0;i<blocks.length();i++){
            JSONArray block=blocks.getJSONArray(i);for(int n=0;n<block.length();n++){JSONObject row=block.getJSONObject(n);originals.put(row);known.add(row.getInt("id"));}
        }
        JSONArray diagnostics=new JSONArray();
        for(int i=0;i<tasks.length();i++){
            JSONObject task=tasks.optJSONObject(i);if(task==null||!(task.opt("evidence") instanceof String))continue;
            JSONArray ids=task.optJSONArray("sources");if(ids==null||ids.length()==0)continue;Set<Integer> refs=new HashSet<>();boolean real=true;
            for(int n=0;n<ids.length();n++){Object id=ids.get(n);if(!(id instanceof Integer)||!known.contains((Integer)id)){real=false;break;}refs.add((Integer)id);}
            if(!real)continue;
            try{ClassroomSummary.validateAssignmentEvidence(new JSONObject().put("assignments",new JSONArray().put(task)),originals);}
            catch(IOException mismatch){
                JSONArray source=new JSONArray();for(int n=0;n<originals.length();n++){JSONObject row=originals.getJSONObject(n);if(refs.contains(row.getInt("id")))source.put(new JSONObject().put("id",row.getInt("id")).put("text",row.getString("text")));}
                diagnostics.put(new JSONObject().put("field","assignments["+i+"].evidence").put("discarded_quote",task.getString("evidence")).put("original_sources",source));
            }
        }
        if(diagnostics.length()==0)return "";
        return "\n本次安排原话诊断（错误引文已作废，仅供纠错，不能作为笔记内容）："+diagnostics
                +"\n请从所指原始字幕逐字摘录表达真实要求的最短有效原话，并列全覆盖的sources；即使原始字幕含ASR错字，也不可在evidence中自动纠正。"
                    +"任务内容按原要求完整保留，从头重新生成本阶段完整JSON；不拼接失败正文或把作废引文当作有效依据。";
    }
    private String reference(String stage)throws JSONException {
        JSONObject turn=firstTurn(stage);
        if(turn!=null){int count=turn.getInt("message_count");
            JSONObject original=state.getJSONObject("conversation").getJSONArray("messages").getJSONObject(count-1);
            JSONArray content=original.getJSONArray("content");String anchor="当前JSON阶段："+stage+"。";
            boolean anchored=false;for(int i=0;i<content.length();i++)anchored|=content.getJSONObject(i).optString("text").contains(anchor);
            return "当前JSON阶段："+stage+"；完整要求见历史第"+count+"条消息（system为第1条），即本阶段最初的user请求。"
                    +(anchored?"沿用该消息中的阶段锚点。":"这是当前尚未提交板块，沿用上文最近一次包含完整schema与原始资料的该板块user请求。");}
        throw new JSONException("当前JSON阶段缺少原始请求记录");
    }
    private JSONObject firstTurn(String stage)throws JSONException {
        JSONArray turns=state.getJSONObject("conversation").getJSONArray("turns");
        for(int i=0;i<turns.length();i++){JSONObject turn=turns.getJSONObject(i);String name=turn.optString("stage");if(stage.equals(name)||name.startsWith(stage+"-restart-"))return turn;}
        return null;
    }
    private JSONObject responseShape(String stage)throws Exception{
        if(!state.optBoolean("native_json_schema"))return null;
        JSONObject shapes=state.optJSONObject("response_shapes");
        if(shapes==null){shapes=new JSONObject();state.put("response_shapes",shapes);}
        if(!shapes.has(stage)){
            shapes.put(stage,ClassroomResponseShape.contract(stage,state.optInt("affairs_schema"),state.optInt("figure_policy")));save();
        }
        return new JSONObject(shapes.getJSONObject(stage).toString());
    }
    private String text(String stage,String prompt,List<byte[]> images,List<String> imageNames,String outputKey,JSONObject shape)throws Exception {
        control.check();
        try {return conversation.json(stage,prompt,images,imageNames,shape);}
        finally {
            if(outputKey!=null){
                JSONObject history=state.getJSONObject("conversation"),outputs=history.getJSONObject("outputs");
                JSONArray turns=history.getJSONArray("turns");
                for(int i=turns.length()-1;i>=0;i--){String sent=turns.getJSONObject(i).optString("stage");
                    if((sent.equals(stage)||sent.startsWith(stage+"-restart-"))&&outputs.has(sent)){
                        state.put(outputKey,outputs.getString(sent));save();break;
                    }
                }
            }
        }
    }
    private void invalid(String stage,int attempt,Exception error)throws Exception {
        state.put(stage+"_attempt",attempt+1).put(stage+"_error",ClassroomJsonFailure.detail(error));save();
    }
    private void save()throws IOException {ClassroomStore.write(new File(dir,"run.json"),state);}
}
