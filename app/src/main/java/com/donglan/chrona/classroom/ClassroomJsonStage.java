package com.donglan.chrona.classroom;

import com.donglan.chrona.net.RequestControl;
import org.json.*;
import java.io.*;
import java.util.*;

/** One complete structural repair per stage, with explicit continuation after interruption. */
final class ClassroomJsonStage {
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
        String request=attempt==0?prompt:retryIntroduction+ClassroomJsonFailure.stored(state.optString(stage+"_error"))
                +"。当前阶段未提交结果已回滚。只依据已有原始资料和实际已附图片，从头完整生成本阶段JSON，不续写、不输出补丁或拼接上次片段，不编造来源和图片。完整要求：\n"+prompt;
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
                    +"。本阶段结果尚未提交。保留可核实事实、来源和图片编号，重新返回完整JSON对象，不输出补丁或剩余片段，不编造未见图片或新事实。这是该块唯一一次格式修复。\n本块要求："+prompt,Collections.emptyList(),outputKey);
            try {return validate(repaired,validator);}
            catch(JSONException|IOException invalid){invalid(stage,attempt,invalid);throw invalid;}
        }
        catch(IOException invalid){invalid(stage,attempt,invalid);throw invalid;}
    }
    private static JSONObject validate(String raw,Validator validator)throws Exception {
        JSONObject result=ClassroomSummary.parseObject(raw);validator.check(result);return result;
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
