package com.donglan.chrona.classroom;

import com.donglan.chrona.ai.ChatCompletionClient;
import org.json.*;
import java.io.*;
import java.util.*;

/** Persist before sending, append exact assistant replies, never rewrite a sent message or options. */
final class ClassroomConversation {
    private final File dir;
    private final JSONObject state,conversation;
    private final ChatCompletionClient client;
    ClassroomConversation(File dir,JSONObject state,ChatCompletionClient client,String system)throws Exception{
        this.dir=dir;this.state=state;this.client=client;
        if(!state.has("conversation"))state.put("conversation",new JSONObject().put("options",client.classroomOptions())
                .put("messages",new JSONArray().put(new JSONObject().put("role","system").put("content",system)))
                .put("turns",new JSONArray()).put("outputs",new JSONObject()));
        conversation=state.getJSONObject("conversation");
    }
    boolean attempted(String stage)throws JSONException{
        JSONArray turns=conversation.getJSONArray("turns");
        for(int i=0;i<turns.length();i++)if(stage.equals(turns.getJSONObject(i).optString("stage")))return true;
        return false;
    }
    String text(String stage,String prompt,List<byte[]> images)throws Exception{
        JSONObject restarts=conversation.optJSONObject("restarts");
        if(restarts==null){restarts=new JSONObject();conversation.put("restarts",restarts);}
        int generation=restarts.optInt(stage);String attempt=generation==0?stage:stage+"-restart-"+generation;
        String pending=conversation.optString("pending");
        if(!pending.isEmpty()&&!pending.equals(attempt))throw new IOException("另一个板块尚未完成，请先处理原阶段");
        if(!pending.isEmpty()){
            // A pending request already present on entry survived an interrupted process.
            // Explicit recovery retires it rather than resending or continuing unknown output.
            generation++;restarts.put(stage,generation);conversation.remove("pending");save();
            attempt=stage+"-restart-"+generation;
        }
        String request=generation==0?prompt:"上次当前板块未完整成功，已回滚该板块未提交的结果。历史中的失败回复仅供审计，不作为本次输出的前半段。"
                +"现在从头完整重新生成当前板块，禁止续写或拼接此前片段。当前阶段："+stage+"；重试序号："+generation+"。\n本次完整要求：\n"+prompt;
        try{return ask(attempt,request,generation==0?images:Collections.emptyList());}
        catch(Exception failure){
            // Retire only this uncommitted attempt. Sent messages and actual replies stay exact.
            if(attempt.equals(conversation.optString("pending")))conversation.remove("pending");
            restarts.put(stage,generation+1);save();throw failure;
        }
    }
    private static String complete(String output,String finish)throws IOException{
        if("length".equals(finish))throw new IOException("当前板块输出被截断，未提交内容已回滚；请从头重新生成本板块");
        if("content_filter".equals(finish))throw new IOException("AI 服务未允许返回本板块内容，未提交内容已回滚");
        if(output.isBlank())throw new IOException("AI 未返回课堂内容，未提交内容已回滚");
        return output;
    }
    private String ask(String stage,String prompt,List<byte[]> images)throws Exception{
        JSONObject outputs=conversation.getJSONObject("outputs");
        if(outputs.has(stage)){
            return complete(outputs.getString(stage),conversation.getJSONObject("finishes").optString(stage));
        }
        JSONArray messages=conversation.getJSONArray("messages");
        if(!conversation.has("pending")){
            JSONArray content=new JSONArray().put(new JSONObject().put("type","text").put("text",prompt));
            for(byte[] image:images)content.put(new JSONObject().put("type","image_url").put("image_url",new JSONObject()
                    .put("url","data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(image))));
            messages.put(new JSONObject().put("role","user").put("content",content));
            conversation.put("pending",stage);save();
        }else if(!stage.equals(conversation.getString("pending")))throw new IOException("上次请求结果未确认，请先继续原阶段");
        // A limit is an explicit stop, never a reason to compact or replace history.
        if(messages.toString().length()>32_000_000)throw new IOException("课堂上下文已达本地上限，完整历史已保留，未裁剪或重新发送");
        JSONObject request=new JSONObject(conversation.getJSONObject("options").toString()).put("messages",new JSONArray(messages.toString()));
        JSONObject audit=new JSONObject().put("stage",stage).put("message_count",messages.length()).put("started",System.currentTimeMillis())
                .put("status","running");conversation.getJSONArray("turns").put(audit);save();
        try{
            JSONObject response=client.completeConversation(request);
            audit.put("response",response).put("usage",response.optJSONObject("usage")==null?JSONObject.NULL:response.getJSONObject("usage"));
            JSONObject choice=response.getJSONArray("choices").getJSONObject(0),reply=choice.getJSONObject("message");
            // Include service-returned reasoning fields exactly; do not synthesize or edit its content.
            if(!reply.has("role"))reply.put("role","assistant");
            messages.put(reply);
            String output=reply.opt("content") instanceof String text?text:"";
            if(!conversation.has("finishes"))conversation.put("finishes",new JSONObject());
            conversation.getJSONObject("finishes").put(stage,choice.optString("finish_reason"));
            outputs.put(stage,output);conversation.remove("pending");
            audit.put("finish_reason",choice.opt("finish_reason")).put("status","received");save();
            return complete(output,choice.optString("finish_reason"));
        }catch(Exception e){audit.put("status","failed").put("type",e.getClass().getSimpleName()).put("error",e.getMessage());throw e;}
        finally{audit.put("finished",System.currentTimeMillis());save();}
    }
    private void save()throws IOException{ClassroomStore.write(new File(dir,"run.json"),state);}
}
