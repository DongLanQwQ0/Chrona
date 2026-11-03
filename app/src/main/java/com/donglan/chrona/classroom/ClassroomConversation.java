package com.donglan.chrona.classroom;

import com.donglan.chrona.ai.ChatCompletionClient;
import com.donglan.chrona.ai.AiSettings;
import org.json.*;
import java.io.*;
import java.util.*;
import java.nio.charset.StandardCharsets;

/** Persist before sending; frozen base options and exact messages stay intact across stage formats. */
final class ClassroomConversation {
    private final File dir;
    private final JSONObject state,conversation;
    private final ChatCompletionClient client;
    private final boolean responses;
    private final boolean nativeJson;
    ClassroomConversation(File dir,JSONObject state,ChatCompletionClient client,String system)throws Exception{
        this.dir=dir;this.state=state;this.client=client;
        if(!state.has("conversation"))state.put("conversation",new JSONObject().put("options",client.classroomOptions())
                .put("protocol",client.protocol())
                .put("messages",new JSONArray().put(new JSONObject().put("role","system").put("content",system)))
                .put("turns",new JSONArray()).put("outputs",new JSONObject()));
        conversation=state.getJSONObject("conversation");
        nativeJson=state.optBoolean("native_json",false);
        // Saved pre-selection runs were sent with Chat Completions. Never translate their prefix.
        String protocol=conversation.optString("protocol",AiSettings.CHAT_COMPLETIONS);
        AiSettings.validateProtocol(protocol);responses=AiSettings.RESPONSES.equals(protocol);
    }
    boolean attempted(String stage)throws JSONException{
        JSONArray turns=conversation.getJSONArray("turns");
        for(int i=0;i<turns.length();i++)if(stage.equals(turns.getJSONObject(i).optString("stage")))return true;
        return false;
    }
    String text(String stage,String prompt,List<byte[]> images)throws Exception{
        return generate(stage,prompt,images,Collections.emptyList(),false);
    }
    String json(String stage,String prompt,List<byte[]> images)throws Exception{
        return json(stage,prompt,images,Collections.emptyList());
    }
    String json(String stage,String prompt,List<byte[]> images,List<String> imageNames)throws Exception{
        if(!imageNames.isEmpty()&&imageNames.size()!=images.size())throw new IOException("图片标识与附件数量不一致");
        List<String> names=new ArrayList<>();for(String imageName:imageNames)names.add(ClassroomDocument.imageName(imageName));
        return generate(stage,prompt,images,names,nativeJson);
    }
    private String generate(String stage,String prompt,List<byte[]> images,List<String> imageNames,boolean json)throws Exception{
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
        try{return ask(attempt,request,generation==0?images:Collections.emptyList(),generation==0?imageNames:Collections.emptyList(),json);}
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
    private String ask(String stage,String prompt,List<byte[]> images,List<String> imageNames,boolean json)throws Exception{
        JSONObject outputs=conversation.getJSONObject("outputs");
        if(outputs.has(stage)){
            return complete(outputs.getString(stage),conversation.getJSONObject("finishes").optString(stage));
        }
        JSONArray messages=conversation.getJSONArray("messages");
        if(!conversation.has("pending")){
            JSONArray content=new JSONArray().put(new JSONObject().put("type",responses?"input_text":"text").put("text",prompt));
            for(int i=0;i<images.size();i++){
                byte[] image=images.get(i);
                if(!imageNames.isEmpty())content.put(new JSONObject().put("type",responses?"input_text":"text")
                        .put("text","图片文件："+imageNames.get(i)+"；紧接的图片仅对应此file。"));
                String url="data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(image);
                content.put(responses?new JSONObject().put("type","input_image").put("image_url",url):
                        new JSONObject().put("type","image_url").put("image_url",new JSONObject().put("url",url)));
            }
            messages.put(new JSONObject().put("role","user").put("content",content));
            conversation.put("pending",stage);save();
        }else if(!stage.equals(conversation.getString("pending")))throw new IOException("上次请求结果未确认，请先继续原阶段");
        // A limit is an explicit stop, never a reason to compact or replace history.
        if(messages.toString().length()>32_000_000)throw new IOException("课堂上下文已达本地上限，完整历史已保留，未裁剪或重新发送");
        JSONObject request=new JSONObject(conversation.getJSONObject("options").toString()).put(responses?"input":"messages",new JSONArray(messages.toString()));
        if(json){
            JSONObject format=new JSONObject().put("type","json_object");
            if(responses){
                JSONObject text=request.optJSONObject("text");
                if(text==null){text=new JSONObject();request.put("text",text);}
                text.put("format",format);
            }else request.put("response_format",format);
        }
        JSONObject audit=new JSONObject().put("stage",stage).put("message_count",messages.length()).put("started",System.currentTimeMillis())
                .put("output_mode",json?"json_object":"text")
                .put("request_bytes",request.toString().getBytes(StandardCharsets.UTF_8).length)
                .put("status","running");conversation.getJSONArray("turns").put(audit);save();
        try{
            JSONObject response=client.completeConversation(request);
            audit.put("response",response).put("usage",response.optJSONObject("usage")==null?JSONObject.NULL:response.getJSONObject("usage"));
            JSONObject choice=response.getJSONArray("choices").getJSONObject(0),reply=choice.getJSONObject("message");
            if(responses){
                // Replay every complete native item, including encrypted reasoning, without rebuilding it.
                JSONArray items=response.getJSONObject("response").getJSONArray("output");
                for(int i=0;i<items.length();i++)messages.put(new JSONObject(items.getJSONObject(i).toString()));
            }else{
                if(!reply.has("role"))reply.put("role","assistant");
                messages.put(reply);
            }
            String output=reply.opt("content") instanceof String text?text:"";
            if(!conversation.has("finishes"))conversation.put("finishes",new JSONObject());
            conversation.getJSONObject("finishes").put(stage,choice.optString("finish_reason"));
            outputs.put(stage,output);conversation.remove("pending");
            audit.put("finish_reason",choice.opt("finish_reason")).put("status","received");save();
            return complete(output,choice.optString("finish_reason"));
        }catch(Exception e){
            if(e instanceof ChatCompletionClient.ResponseException failure&&failure.evidence!=null){
                JSONObject evidence=failure.evidence;
                audit.put("response",evidence).put("usage",evidence.optJSONObject("usage")==null?JSONObject.NULL:evidence.getJSONObject("usage"));
                for(String key:new String[]{"finish_reason","terminal_type","content_chars","reasoning_chars"})
                    if(evidence.has(key))audit.put(key,evidence.get(key));
            }
            audit.put("status","failed").put("type",e.getClass().getSimpleName()).put("error",e.getMessage());throw e;
        }
        finally{audit.put("finished",System.currentTimeMillis());save();}
    }
    private void save()throws IOException{ClassroomStore.write(new File(dir,"run.json"),state);}
}
