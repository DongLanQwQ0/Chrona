package com.donglan.chrona.classroom;

import com.donglan.chrona.net.RequestControl;
import org.json.*;
import java.io.*;
import java.util.*;

/** One complete structural repair per stage, with explicit continuation after interruption. */
final class ClassroomJsonStage {
    private static final String WIRE="整条响应首、末非空白字符必须分别为{和}；禁止根字符串、根数组、前置说明、思考文字和代码围栏。Markdown仅置于字段值中。JSON字符串内换行用\\n编码，反斜杠按JSON转义；解析后显示正常段落，不要双重转义。";
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
        int attempt=state.optInt(stage+"_attempt");
        if(state.has(stage+"_error")){
            state.put(stage+"_error",ClassroomJsonFailure.stored(state.getString(stage+"_error")));save();
        }
        boolean sent=firstTurn(stage)!=null;
        String request=attempt==0&&!sent?"当前JSON阶段："+stage+"。"+WIRE+"\n"+prompt:
                (attempt>0?retryIntroduction+ClassroomJsonFailure.stored(state.optString(stage+"_error"))+"。":"恢复此前未完成的JSON阶段。")
                +reference(stage)+"本阶段未提交回复已回滚；只依据其中的完整schema、原始资料和历史实际附图，从头完整生成本阶段JSON对象，不续写、不拼接、不编造来源或图片。"+WIRE;
        String raw=text(attempt==0?stage:stage+retrySuffix+attempt,request,attempt==0?images:Collections.emptyList(),outputKey);
        try {return validate(raw,validator);}
        catch(JSONException malformed) {
            // A transport-interrupted repair keeps its identity and attempt, and is restarted by
            // ClassroomConversation. A completed invalid repair advances attempt and exhausts it.
            if(conversation.attempted(stage+"-repair")&&state.optInt(stage+"_repair_attempt",0)!=attempt){
                invalid(stage,attempt,malformed);throw malformed;
            }
            state.put(stage+"_repair_attempt",attempt);save();
            String repaired=text(stage+"-repair","上一条回复的JSON结构或字段不完整："+ClassroomJsonFailure.detail(malformed)
                    +"。这是该块唯一一次格式修复。"+reference(stage)+"本阶段未提交回复已回滚；按该原始请求中的完整schema、原始资料和历史实际附图，从头返回完整JSON对象，不输出补丁或剩余片段，不编造来源、未见图片或新事实。"+WIRE,Collections.emptyList(),outputKey);
            try {return validate(repaired,validator);}
            catch(JSONException|IOException invalid){invalid(stage,attempt,invalid);throw invalid;}
        }
        catch(IOException invalid){invalid(stage,attempt,invalid);throw invalid;}
    }
    private static JSONObject validate(String raw,Validator validator)throws Exception {
        JSONObject result=ClassroomSummary.parseObject(raw);validator.check(result);return result;
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
    private String text(String stage,String prompt,List<byte[]> images,String outputKey)throws Exception {
        control.check();
        try {return conversation.text(stage,prompt,images);}
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
