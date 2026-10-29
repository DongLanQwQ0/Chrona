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
    private static final class Incomplete extends IOException{final String output;Incomplete(String output){super("当前分段被截断，原始回复已保存");this.output=output;}}
    String text(String stage,String prompt,List<byte[]> images)throws Exception{
        StringBuilder combined=new StringBuilder();
        for(int attempt=0;attempt<3;attempt++)try{return combined.append(ask(attempt==0?stage:stage+"-continue-"+attempt,
                attempt==0?prompt:"接续上一条被长度限制截断的回复，只输出剩余部分，不重写、概括或重复此前内容；保持当前Markdown或JSON格式直到完成。",attempt==0?images:Collections.emptyList())).toString();}
        catch(Incomplete partial){combined.append(partial.output);}
        throw new IOException("本段连续三次达到输出上限，完整上下文已保存，请手动继续或选择更大输出预算的模型");
    }
    private String ask(String stage,String prompt,List<byte[]> images)throws Exception{
        JSONObject outputs=conversation.getJSONObject("outputs");
        if(outputs.has(stage)){
            String finish=conversation.getJSONObject("finishes").optString(stage);
            if("length".equals(finish))throw new Incomplete(outputs.getString(stage));
            if("content_filter".equals(finish))throw new IOException("AI 服务未允许返回本段内容，原始响应已保留");
            if(outputs.getString(stage).isBlank())throw new IOException("AI 未返回课堂内容，原始响应已保留");
            return outputs.getString(stage);
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
            if("length".equals(choice.optString("finish_reason")))throw new Incomplete(output);
            if("content_filter".equals(choice.optString("finish_reason")))throw new IOException("AI 服务未允许返回本段内容，原始响应已保留");
            if(output.isBlank())throw new IOException("AI 未返回课堂内容");
            return output;
        }catch(Exception e){audit.put("status","failed").put("type",e.getClass().getSimpleName()).put("error",e.getMessage());throw e;}
        finally{audit.put("finished",System.currentTimeMillis());save();}
    }
    private void save()throws IOException{ClassroomStore.write(new File(dir,"run.json"),state);}
}
