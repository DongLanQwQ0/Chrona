package com.donglan.chrona.classroom;

import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.net.RequestControl;
import com.sun.net.httpserver.HttpServer;
import org.json.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

/** Production classes, synthetic material and model output, real loopback HTTP. */
public final class ClassroomEnhancedCheck {
    private static final ClassroomLesson LESSON=new ClassroomLesson("1","2","合成课程","合成课堂","教室","6",1);
    private static final AiSettings AI=new AiSettings("https://api.openai.com/v1","gpt-5.1","synthetic-only-key","high");
    private static final List<JSONObject> requests=new ArrayList<>(), replies=new ArrayList<>();
    private static final List<String> stages=new ArrayList<>();
    private static File root;private static int checks,failures,failCall=-1,slowCall=-1;
    private static String mode="normal",lastStage="";private static boolean images;
    private static HttpURLConnection lastConnection;
    private static CountDownLatch requestSeen=new CountDownLatch(1);
    interface Task{void run()throws Exception;}
    private static void check(boolean ok,String message){checks++;if(!ok){failures++;System.err.println("FAIL: "+message);}}
    private static void test(String label,Task task){try{task.run();System.out.println("DONE: "+label);}catch(Throwable error){failures++;System.err.println("ERROR: "+label);error.printStackTrace();}}
    private static void rejects(Task task,String label)throws Exception{try{task.run();check(false,label);}catch(IOException|JSONException expected){check(true,label);}}
    private static void reset(String value,boolean withImages){requests.clear();replies.clear();stages.clear();mode=value;images=withImages;lastStage="";failCall=-1;slowCall=-1;requestSeen=new CountDownLatch(1);}
    private static String prompt(JSONObject request)throws Exception{JSONArray messages=request.getJSONArray("messages");return messages.getJSONObject(messages.length()-1).getJSONArray("content").getJSONObject(0).getString("text");}
    private static String stage(String prompt){
        Matcher restart=Pattern.compile("当前阶段：([^；]+)；重试序号：(\\d+)").matcher(prompt);if(restart.find())return restart.group(1)+"-restart-"+restart.group(2);
        if(prompt.contains("唯一一次格式修复"))return lastStage+"-repair";
        if(prompt.contains("用户授权继续当前板块"))return lastStage+"-user-retry";
        if(prompt.contains("合并全课课堂安排中的同义重复"))return "edit-assignments";
        if(prompt.contains("现在编排一篇与普通模式相同的全课笔记"))return "edit-plan";
        Matcher editor=Pattern.compile("全课整稿主题 (\\d+)/").matcher(prompt);if(editor.find())return "edit-note-"+(Integer.parseInt(editor.group(1))-1);
        Matcher correction=Pattern.compile("更正文稿第 (\\d+)/").matcher(prompt);if(correction.find())return "correct-"+(Integer.parseInt(correction.group(1))-1);
        Matcher draft=Pattern.compile("现在只整理第 (\\d+) 段").matcher(prompt);if(draft.find())return "draft-"+(Integer.parseInt(draft.group(1))-1);
        Matcher fin=Pattern.compile("只核实并完善第 (\\d+) 段").matcher(prompt);if(fin.find())return "final-"+(Integer.parseInt(fin.group(1))-1);
        return "legacy";
    }
    private static JSONObject part(int id,boolean draft,String prompt)throws Exception{
        JSONArray pictures=new JSONArray();Matcher names=Pattern.compile("\\\"file\\\":\\\"(image-[0-9]+\\.jpg)\\\"").matcher(prompt);
        if(!draft)while(names.find())if(!pictures.toString().contains(names.group(1)))pictures.put(names.group(1));
        if(!draft&&mode.equals("reuse-images")&&id==1)pictures.put("image-0.jpg");
        if(!draft&&mode.equals("final-retry")&&prompt.contains("用户授权继续当前板块"))pictures.put("image-0.jpg");
        JSONObject value=new JSONObject().put("overview","合成概览 "+id)
                .put("notes",new JSONArray().put(new JSONObject().put("title","题目条件与评分线索 "+id)
                    .put("text","题干 5 kg；步骤分 2 分；待核对 $\\nabla x$。\\n \\n下一段")
                    .put("sources",new JSONArray().put(mode.equals("invalid-source")||(!draft&&mode.equals("invalid-final-source"))?999:id))
                    .put("images",mode.equals("invalid-image")||(!draft&&mode.equals("invalid-final-image"))?new JSONArray().put("image-999.jpg"):pictures)))
                .put("assignments",new JSONArray());
        if(mode.startsWith("task-")&&id==0)value.put("assignments",new JSONArray()
                .put(new JSONObject().put("text","今天进入作业页面查看成绩。").put("sources",new JSONArray().put(0)).put("evidence","题干 5 kg"))
                .put(new JSONObject().put("text","查看成绩。").put("sources",new JSONArray().put(0)).put("evidence","步骤分 2 分")));
        if(mode.equals("cross-draft")&&draft&&id==0||mode.equals("cross-final")&&!draft&&id==0)value.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(1));
        if(mode.equals("cross-context")&&!draft&&id==1)value.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(1).put(0));
        if(mode.equals("empty-tail")&&id==1)value.put("overview","").put("notes",new JSONArray());
        if(draft)value.put("image_assessment",new JSONObject().put("decision",images?"request":"disabled").put("reason","合成板书需核对题目条件"))
                .put("image_requests",images?new JSONArray().put(new JSONObject().put("start",mode.equals("reuse-images")?0:id*100).put("end",mode.equals("reuse-images")?120:id*100+20).put("reason","核对题干数字与步骤分")):new JSONArray());
        if(!draft){JSONArray reviews=new JSONArray();Set<String> reviewed=new LinkedHashSet<>();for(int i=0;i<pictures.length();i++)if(reviewed.add(pictures.getString(i)))reviews.put(new JSONObject().put("file",pictures.getString(i)).put("status","usable").put("confidence","high").put("visible","合成图的黑白网格").put("reason","合成画面用于对应题目"));value.put("image_reviews",reviews);}
        return value;
    }
    private static HttpServer server()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;}));
        server.createContext("/",exchange->{try{
            JSONObject body=new JSONObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            requests.add(body);String text=prompt(body),current=stage(text);stages.add(current);int call=requests.size();
            if(!current.contains("continue")&&!current.contains("repair")&&!current.contains("user-retry"))lastStage=current;
            if(call==slowCall){requestSeen.countDown();Thread.sleep(1200);}
            if(call==failCall){byte[] error="{\"error\":{\"message\":\"synthetic unavailable\"}}".getBytes(StandardCharsets.UTF_8);replies.add(null);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(503,error.length);exchange.getResponseBody().write(error);return;}
            String output,finish="stop";int id=current.matches(".*-[01].*")?Integer.parseInt(current.replaceAll(".*?-(\\d+).*","$1")):0;
            if(current.startsWith("correct")){
                output="# 更正文稿\n["+id+"] 完整题干、改卷细节与 $\\nabla x$。";
                if(mode.equals("length-resume")&&current.equals("correct-0")){output="# 更正文稿\n[0] 前半";finish="length";}
            }else if(current.startsWith("edit-assignments")){
                output=new JSONObject().put("groups",new JSONArray().put(new JSONArray().put("0:0").put(mode.equals("task-invalid")?"0:9":"0:1"))).toString();
                if(mode.equals("task-length")){output="{\"groups\":[[\"0:0\"";finish="length";}
            }else if(current.startsWith("edit-plan")){
                JSONArray index=new JSONArray(text.substring(text.indexOf("已完成笔记索引：\n")+"已完成笔记索引：\n".length())),sections=new JSONArray();
                if(mode.equals("merge-topics")){
                    JSONArray refs=new JSONArray();for(int n=0;n<index.length();n++)refs.put(index.getJSONObject(n).getString("ref"));
                    sections.put(new JSONObject().put("title","统一主题").put("refs",refs));
                }else for(int n=0;n<index.length();n++)sections.put(new JSONObject().put("title",index.getJSONObject(n).getString("title")).put("refs",new JSONArray().put(index.getJSONObject(n).getString("ref"))));
                if(mode.equals("bad-plan")&&sections.length()>0)sections.put(sections.getJSONObject(0));
                output=new JSONObject().put("overview","统一全课重点").put("sections",sections).toString();
            }else if(current.startsWith("edit-note")){
                JSONArray input=new JSONArray(text.substring(text.indexOf("本主题提取稿：\n")+"本主题提取稿：\n".length()));
                JSONArray refs=new JSONArray(),pics=new JSONArray();Set<Integer> sourceSet=new LinkedHashSet<>();Set<String> imageSet=new LinkedHashSet<>();
                for(int n=0;n<input.length();n++){JSONObject note=input.getJSONObject(n);JSONArray source=note.getJSONArray("sources"),pictures=note.getJSONArray("images");for(int k=0;k<source.length();k++)sourceSet.add(source.getInt(k));for(int k=0;k<pictures.length();k++)imageSet.add(pictures.getString(k));}
                for(int source:sourceSet)refs.put(source);for(String image:imageSet)pics.put(image);
                if(mode.equals("bad-editor-source"))refs=new JSONArray().put(999);
                JSONObject note=new JSONObject(input.getJSONObject(0).toString()).put("sources",refs).put("images",pics);
                output=new JSONObject().put("overview","").put("notes",new JSONArray().put(note)).put("assignments",new JSONArray()).toString();
            }else if(mode.equals("bad-json")&&current.equals("draft-0"))output="{\"overview\":";
            else if(mode.equals("bad-repair")&&(current.equals("draft-0")||current.endsWith("repair")))output="{\"overview\":";
            else if(mode.equals("bad-manual")&&current.endsWith("user-retry"))output="{\"overview\":";
            else {JSONObject value=part(id,current.startsWith("draft")||current.equals("legacy")&&!text.contains("本次images清单：\n")&&!text.contains("上一条最终总结"),text);
                if(current.equals("draft-0")&&mode.equals("missing-requests")||mode.equals("missing-always")&&current.startsWith("draft"))value.remove("image_requests");
                if(current.equals("draft-0")&&mode.equals("missing-assessment"))value.remove("image_assessment");
                if(current.equals("draft-0")&&mode.equals("request-type"))value.put("image_requests",new JSONObject());
                output="  ```json\n"+value.toString(2)+"\n```  ";}
            if(mode.equals("length-editor")&&current.equals("edit-note-1")){output="{\"overview\":\"截断不得拼接\"";finish="length";}
            if(mode.equals("length-final")&&current.equals("final-0")){output="{\"overview\":\"截断不得拼接\"";finish="length";}
            JSONObject reply=new JSONObject().put("role","assistant").put("content",output).put("reasoning_content","合成推理\ncall="+call);
            replies.add(reply);
            JSONObject usage=new JSONObject().put("prompt_tokens",1000+call).put("completion_tokens",100)
                    .put("total_tokens",1100+call).put("prompt_tokens_details",new JSONObject().put("cached_tokens",700+call)).put("prompt_cache_hit_tokens",600+call);
            String payload,type;
            if(mode.equals("json-envelope")){
                reply.put("provider_extension",new JSONObject().put("opaque","unchanged"));
                payload=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",reply).put("finish_reason",finish))).put("usage",usage).toString();type="application/json";
            }else{
                JSONObject delta=new JSONObject(reply.toString());delta.remove("role");
                JSONObject frame=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",delta).put("finish_reason",finish)));
                payload="data: "+frame+"\n\ndata: "+new JSONObject().put("choices",new JSONArray()).put("usage",usage)+"\n\ndata: [DONE]\n\n";type="text/event-stream";
            }
            byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type",type);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);
        }catch(Exception error){if(!(error instanceof IOException))error.printStackTrace();}finally{exchange.close();}});
        server.start();
        URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL url)throws IOException{
            if(!url.getHost().equals("api.openai.com"))throw new IOException("Non-loopback destination forbidden");
            lastConnection=(HttpURLConnection)new URL("http://127.0.0.1:"+server.getAddress().getPort()+url.getFile()).openConnection();return lastConnection;
        }}:null);return server;
    }
    private static File materials(String name,boolean longText)throws Exception{
        File dir=new File(root,name+"-"+System.nanoTime());dir.mkdirs();JSONArray segments=new JSONArray(),candidates=new JSONArray();
        for(int i=0;i<(longText?2:1);i++){
            segments.put(new JSONObject().put("start",i*100).put("end",i*100+20).put("text","题干 5 kg，步骤分 2 分。"+(longText?"甲".repeat(3500):"完整短字幕")));
            BufferedImage pic=new BufferedImage(48,48,BufferedImage.TYPE_INT_RGB);var g=pic.createGraphics();g.setColor(new java.awt.Color(i*60+30,i*60+30,i*60+30));g.fillRect(0,0,48,48);g.dispose();
            for(int y=0;y<48;y++)for(int x=0;x<48;x++)pic.setRGB(x,y,(x%6<1||y%6<1)?0x101010:0xeeeeee);
            File raw=new File(dir,"raw-"+i+".img");ImageIO.write(pic,"png",raw);
            candidates.put(new JSONObject().put("raw",raw.getName()).put("index",i).put("second",i*100));
        }
        ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("segments",segments).put("raw_images",candidates.length()));
        ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject().put("images",candidates));return dir;
    }
    private static JSONObject generate(File dir,boolean enhanced)throws Exception{try(RequestControl c=new RequestControl(20000)){return ClassroomSummary.generate(dir,LESSON,AI,images?2:0,c,p->{},"保留题干条件及步骤分",enhanced);}}
    private static JSONObject state(File dir)throws Exception{return ClassroomStore.read(new File(dir,"run.json"));}
    private static void evidence(String name,File dir)throws Exception{
        JSONArray req=new JSONArray(),rep=new JSONArray();for(JSONObject value:requests)req.put(value);for(JSONObject value:replies)rep.put(value==null?JSONObject.NULL:value);
        ClassroomStore.write(new File(root,name+"-http.json"),new JSONObject().put("synthetic",true).put("fixture",dir.getName()).put("stages",stages).put("requests",req).put("replies",rep));
    }
    private static void appendOnly(File dir)throws Exception{
        JSONObject firstOptions=new JSONObject(requests.get(0).toString());firstOptions.remove("messages");
        for(int n=0;n<requests.size();n++){
            JSONObject options=new JSONObject(requests.get(n).toString());options.remove("messages");
            check(options.similar(firstOptions),"frozen options for request "+n);
            if(n==0)continue;
            JSONArray before=requests.get(n-1).getJSONArray("messages"),after=requests.get(n).getJSONArray("messages");
            check(after.length()>=before.length(),"history never shrinks "+n);
            for(int i=0;i<before.length();i++)check(before.getJSONObject(i).toString().equals(after.getJSONObject(i).toString()),"exact message prefix "+n+":"+i);
            if(replies.get(n-1)!=null){check(after.length()==before.length()+2,"one assistant and user appended "+n);check(after.getJSONObject(before.length()).similar(replies.get(n-1)),"raw assistant including reasoning preserved "+n);}
            else check(after.length()==before.length()+1&&prompt(requests.get(n)).contains("从头完整重新生成当前板块"),"failed attempt is retired; explicit restart appends without splicing unknown output "+n);
        }
        JSONObject conversation=state(dir).getJSONObject("conversation");JSONArray turns=conversation.getJSONArray("turns");
        check(turns.length()==requests.size(),"every sent attempt retained in turns");
        for(int n=0;n<turns.length();n++)if(replies.get(n)!=null){JSONObject usage=turns.getJSONObject(n).getJSONObject("usage");check(usage.getJSONObject("prompt_tokens_details").getInt("cached_tokens")==701+n&&usage.getInt("prompt_cache_hit_tokens")==601+n,"synthetic cache usage retained verbatim "+n);}
        check(!state(dir).toString().contains(AI.apiKey),"run excludes API credential");
    }
    private static void multiBlock()throws Exception{
        reset("normal",true);File dir=materials("multi",true);JSONObject result=generate(dir,true);evidence("multi",dir);appendOnly(dir);
        check(stages.equals(List.of("correct-0","correct-1","draft-0","draft-1","final-0","final-1","edit-plan","edit-note-0","edit-note-1")),"correction, scoped extraction and images precede the bounded editorial pass");
        check(result.getBoolean("complete")&&result.getInt("source_blocks")==2&&result.getInt("total_parts")==1&&result.getJSONArray("parts").length()==1,"multiple internal blocks publish one ordinary document");
        check(result.getString("corrected_transcript").contains("[0]")&&result.getString("corrected_transcript").contains("[1]"),"all corrected transcript blocks published");
        for(int i=0;i<4;i++)check(requests.get(i).getJSONArray("messages").getJSONObject(requests.get(i).getJSONArray("messages").length()-1).getJSONArray("content").length()==1,"no image upload before drafts finish "+i);
        check(result.getJSONArray("images").length()==2&&state(dir).getJSONObject("image_audit").getInt("selected_count")==2,"image budget and analysed inventory");
        check(state(dir).getJSONArray("drafts").getJSONObject(0).has("image_assessment")&&state(dir).getJSONArray("drafts").getJSONObject(1).getJSONArray("image_requests").length()==1,"per-block image assessment and requests persisted");
        check(result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(0).getString("text").contains("。\n\n下一段"),"parsed Markdown normalizes escaped paragraph breaks");
    }
    private static void assignmentEditorial()throws Exception{
        JSONObject original=new JSONObject().put("0:0",new JSONObject().put("text","今天查看成绩并核对单选题。").put("sources",new JSONArray().put(0)).put("evidence","原始依据甲"))
                .put("1:0",new JSONObject().put("text","查看成绩。").put("sources",new JSONArray().put(1)).put("evidence","原始依据乙"));
        String untouched=original.toString();JSONObject grouping=new JSONObject().put("groups",new JSONArray().put(new JSONArray().put("0:0").put("1:0")));
        JSONArray grouped=ClassroomEnhancedSummary.groupAssignments(grouping,original);
        check(grouped.length()==1&&grouped.getJSONObject(0).getString("text").equals(original.getJSONObject("0:0").getString("text")),"semantic grouping keeps the complete original task text without model rewriting");
        check(grouped.getJSONObject(0).getJSONArray("sources").toString().equals("[0,1]")&&grouped.getJSONObject(0).getString("evidence").equals("原始依据甲"),"grouping unions real sources and retains the kept original evidence");
        check(original.toString().equals(untouched),"grouping never mutates extracted tasks or evidence");
        for(JSONArray groups:new JSONArray[]{new JSONArray(),new JSONArray().put(new JSONArray()),new JSONArray().put(new JSONArray().put("0:0").put("missing")),new JSONArray().put(new JSONArray().put("0:0").put("0:0").put("1:0"))})
            rejects(()->ClassroomEnhancedSummary.groupAssignments(new JSONObject().put("groups",groups),original),"empty, missing, invented and duplicate task indices fail closed");
        reset("task-normal",false);File dir=materials("task-editor",false);JSONObject result=generate(dir,true);
        check(result.getJSONArray("parts").getJSONObject(0).getJSONArray("assignments").length()==1&&state(dir).has("editor_assignments"),"task grouping is checkpointed and only consolidated tasks reach the final document");
        check(stages.get(stages.size()-1).equals("edit-assignments"),"task editorial follows completed note editorial");appendOnly(dir);
        reset("task-length",false);File interrupted=materials("task-rollback",false);rejects(()->generate(interrupted,true),"interrupted task group rolls back without partial publication");
        check(state(interrupted).getJSONArray("editor_notes").length()==1&&!state(interrupted).has("editor_assignments")&&!state(interrupted).has("final"),"task failure preserves successful notes without committing the partial grouping");
        mode="task-normal";JSONObject resumed=generate(interrupted,true);
        check(resumed.getBoolean("complete")&&Collections.frequency(stages,"correct-0")==1&&Collections.frequency(stages,"edit-note-0")==1&&stages.get(stages.size()-1).equals("edit-assignments-restart-1"),"only interrupted assignment grouping restarts, not transcript, images or notes");appendOnly(interrupted);
        reset("task-invalid",false);File invalid=materials("task-invalid",false);rejects(()->generate(invalid,true),"invalid task reference is not auto repaired");
        check(!state(invalid).has("editor_assignments")&&state(invalid).getInt("edit-assignments_attempt")==1,"invalid semantic grouping remains a manual retry checkpoint");
        mode="task-normal";check(generate(invalid,true).getBoolean("complete"),"explicit retry appends a valid task grouping");appendOnly(invalid);
    }
    private static void classroomTimeout()throws Exception{
        reset("normal",false);var client=new com.donglan.chrona.ai.ChatCompletionClient(AI);
        JSONObject request=client.classroomOptions().put("messages",new JSONArray().put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","text").put("text","synthetic classroom timeout check")))));
        client.completeConversation(request);check(lastConnection.getReadTimeout()==600_000,"classroom transport allows ten minutes of silent long-context processing");
        check(lastConnection.getConnectTimeout()==30_000,"classroom connection timeout remains thirty seconds");
        client.complete("s","p",List.of());check(lastConnection.getReadTimeout()==180_000,"ordinary completion retains its original three-minute contract");
        try(RequestControl control=new RequestControl(2500)){new com.donglan.chrona.ai.ChatCompletionClient(AI,control).completeConversation(request);check(lastConnection.getReadTimeout()>0&&lastConnection.getReadTimeout()<=2500,"classroom allowance is still bounded by the caller deadline");}
    }
    private static void manualResume()throws Exception{
        reset("normal",true);File dir=materials("network",true);failCall=4;rejects(()->generate(dir,true),"network failure propagated without automatic resubmit");
        check(requests.size()==4,"network failure made exactly one attempt");JSONObject failed=state(dir);String run=failed.getString("run");
        check(failed.getJSONArray("corrected").length()==2&&failed.getJSONArray("drafts").length()==1,"successful stages checkpointed before failure");
        check(!failed.getJSONObject("conversation").has("pending")&&failed.getJSONObject("conversation").getJSONObject("restarts").getInt("draft-1")==1,"failed draft is rolled back, not left pending for a continuation");
        failCall=-1;JSONObject result=generate(dir,true);check(requests.size()==10&&result.getString("run").equals(run),"manual resume only retries failed draft then remaining image and editorial stages");
        appendOnly(dir);evidence("network-resume",dir);
    }
    private static void repair()throws Exception{
        reset("bad-json",false);File dir=materials("repair",false);check(generate(dir,true).getBoolean("complete"),"single local JSON repair completes");
        check(stages.equals(List.of("correct-0","draft-0","draft-0-repair","edit-plan","edit-note-0")),"one repair appends without recomputing transcript");appendOnly(dir);evidence("repair",dir);
        reset("bad-repair",false);File bad=materials("repair-limit",false);rejects(()->generate(bad,true),"invalid single repair fails");
        check(requests.size()==3,"repair budget is one");mode="bad-manual";rejects(()->generate(bad,true),"manual retry may fail but does not loop on another automatic repair");check(requests.size()==4,"explicit manual continue appends exactly one new request after failed repair");evidence("repair-limit",bad);
    }
    private static void whitelist()throws Exception{
        for(String invalid:List.of("invalid-source","invalid-image")){
            reset(invalid,false);File dir=materials(invalid,false);rejects(()->generate(dir,true),invalid+" rejected");
            check(requests.size()==2&&!state(dir).has("final"),"semantic invalid reference never triggers structural repair or publication");
            check(state(dir).getJSONObject("conversation").getJSONObject("outputs").has("draft-0"),"invalid raw output retained");evidence(invalid,dir);
        }
        for(String invalid:List.of("invalid-final-source","invalid-final-image")){
            reset(invalid,true);File dir=materials(invalid,false);rejects(()->generate(dir,true),invalid+" rejected after actual image upload");
            JSONObject failed=state(dir);check(requests.size()==3&&!failed.has("final")&&failed.getJSONArray("parts").length()==0,"invalid final neither published nor automatically resent");
            check(failed.getJSONArray("corrected").length()==1&&failed.getJSONArray("drafts").length()==1,"invalid final retains successful transcript and draft");
            check(failed.getJSONObject("conversation").getJSONObject("outputs").has("final-0"),"invalid final raw output retained");evidence(invalid,dir);
        }
    }
    private static void lengthResume()throws Exception{
        reset("length-resume",false);File dir=materials("length",true);rejects(()->generate(dir,true),"truncated segment stops without continuation");check(requests.size()==1,"no automatic continuation or retry after truncated response");
        JSONObject failed=state(dir);String run=failed.getString("run");
        check(failed.getJSONArray("corrected").length()==0&&!new File(dir,"corrected.json").exists(),"partial correction is never committed");
        check(failed.getJSONObject("conversation").getJSONArray("messages").getJSONObject(2).getString("content").contains("前半"),"raw partial reply remains in immutable audit history");
        JSONObject result=generate(dir,true);
        check(result.getString("run").equals(run)&&!result.getString("corrected_transcript").contains("前半")&&result.getString("corrected_transcript").contains("完整题干"),"manual retry replaces the failed unit with a complete response, never joins fragments");
        check(Collections.frequency(stages,"correct-0")==1&&Collections.frequency(stages,"correct-0-restart-1")==1&&stages.stream().noneMatch(s->s.contains("continue")),"one complete restart, no continuation calls");appendOnly(dir);evidence("length-rollback",dir);
        reset("normal",false);File crash=materials("process-interrupted",true);failCall=2;rejects(()->generate(crash,true),"synthetic request interruption after the first committed correction");
        JSONObject crashState=state(crash),conversation=crashState.getJSONObject("conversation");
        // Model an AtomicFile checkpoint persisted immediately before a process died in HTTP.
        conversation.getJSONObject("restarts").remove("correct-1");conversation.put("pending","correct-1");ClassroomStore.write(new File(crash,"run.json"),crashState);
        failCall=-1;JSONObject recovered=generate(crash,true);
        check(recovered.getBoolean("complete")&&Collections.frequency(stages,"correct-0")==1&&Collections.frequency(stages,"correct-1-restart-1")==1,"persisted pending crash retires unknown attempt and regenerates only its unit");
        check(prompt(requests.get(2)).contains("从头完整重新生成当前板块"),"process recovery appends a full restart instead of resending the pending request");appendOnly(crash);evidence("process-rollback",crash);
        for(String modeName:List.of("length-final","length-editor")){
            reset(modeName,true);File interrupted=materials(modeName,true);rejects(()->generate(interrupted,true),"truncated image/editor stage rolls back: "+modeName);
            JSONObject checkpoint=state(interrupted);String before=checkpoint.getString("run");
            if(modeName.equals("length-editor"))check(checkpoint.getJSONArray("editor_notes").length()==1,"completed edited topic survives later truncation");
            else check(checkpoint.getJSONArray("parts").length()==0&&checkpoint.getJSONArray("analysed").length()==0,"truncated image response commits neither notes nor analysed images");
            JSONObject finished=generate(interrupted,true);check(finished.getString("run").equals(before)&&!finished.toString().contains("截断不得拼接"),"complete retry never splices truncated image/editor content");
            check(Collections.frequency(stages,"correct-0")==1&&stages.stream().noneMatch(s->s.contains("continue")),"successful correction is reused, unfinished unit alone regenerates");
            if(modeName.equals("length-final")){JSONArray retried=requests.get(5).getJSONArray("messages");check(retried.getJSONObject(retried.length()-1).getJSONArray("content").length()==1,"image-stage full restart keeps original attached image in history without reupload");}
            appendOnly(interrupted);evidence(modeName,interrupted);
        }
    }
    private static void cancellation()throws Exception{
        reset("normal",false);File dir=materials("cancel",true);slowCall=2;
        try(RequestControl control=new RequestControl(20000)){
            Thread cancel=new Thread(()->{try{if(requestSeen.await(5,TimeUnit.SECONDS))control.cancel();}catch(InterruptedException error){Thread.currentThread().interrupt();}});cancel.start();
            rejects(()->ClassroomSummary.generate(dir,LESSON,AI,0,control,p->{},"保留题干条件及步骤分",true),"in-flight cancellation stops pipeline");cancel.join();
        }
        check(requests.size()==2&&state(dir).getJSONArray("corrected").length()==1&&!state(dir).has("final"),"cancel preserves completed correction without publication");
        // Wait for the synthetic slow handler to leave; no unrelated operation is stopped.
        Thread.sleep(1300);slowCall=-1;String run=state(dir).getString("run");JSONObject result=generate(dir,true);
        check(result.getBoolean("complete")&&result.getString("run").equals(run),"cancelled pipeline resumes its own checkpoint");
        check(Collections.frequency(stages,"correct-0")==1,"cancel resume does not repeat successful stage");evidence("cancel-resume",dir);
    }
    private static void defaultFlow()throws Exception{
        reset("normal",false);File dir=materials("default",true);JSONObject result;
        try(RequestControl c=new RequestControl(20000)){result=ClassroomSummary.generate(dir,LESSON,AI,0,c,p->{},"保留题干条件及步骤分");}
        check(requests.size()==1&&stages.get(0).equals("legacy")&&!result.has("corrected_transcript"),"seven-argument default remains one whole-lesson draft stage");
        draftExample(prompt(requests.get(0)));
        reset("normal",false);check(generate(materials("explicit-false",true),false).getBoolean("complete")&&requests.size()==1,"explicit false preserves default stage count");
        reset("normal",true);File illustrated=materials("default-images",true);
        try(RequestControl c=new RequestControl(20000)){result=ClassroomSummary.generate(illustrated,LESSON,AI,2,c,p->{},"保留题干条件及步骤分");}
        check(result.getBoolean("complete")&&requests.size()==2&&!result.has("corrected_transcript"),"seven-argument illustrated default keeps original two stages over whole lesson");
        draftExample(prompt(requests.get(0)));
        appendOnly(illustrated);evidence("default-images",illustrated);
    }
    private static void jsonEnvelope()throws Exception{
        reset("json-envelope",false);File dir=materials("json-envelope",false);check(generate(dir,true).getBoolean("complete"),"non-streaming JSON service response accepted");appendOnly(dir);evidence("json-envelope",dir);
        check(state(dir).getJSONObject("conversation").getJSONArray("messages").getJSONObject(2).has("provider_extension"),"JSON assistant extra fields preserved");
    }
    private static void markdown(){
        String actual="# 标题\n\n正文\n下一行";check(ClassroomDocument.normalizeMarkdown(actual).equals(actual),"real newlines remain exact");
        check(ClassroomDocument.normalizeMarkdown("甲\\n\\n乙").equals("甲\n\n乙"),"literal double escaped newline repaired");
        check(ClassroomDocument.normalizeMarkdown("甲\\n \\n乙").equals("甲\n\n乙"),"whitespace-separated escaped paragraph repaired");
        for(String protectedText:List.of("$\\nabla x$","$$\\nabla f \\n \\n x$$","`a\\n\\nb`","```java\nString x=\"\\n \\n\";\n```"))check(ClassroomDocument.normalizeMarkdown(protectedText).equals(protectedText),"math/code escape preserved: "+protectedText);
    }
    private static void oversizedSubtitle()throws Exception{
        String text="甲".repeat(5999)+"\uD83D\uDE00"+"乙".repeat(6200);
        JSONObject original=new JSONObject().put("id",7).put("start",12.5).put("end",70.25).put("text",text);String snapshot=original.toString();
        JSONArray split=ClassroomEnhancedSummary.blocks(new JSONArray().put(original));StringBuilder joined=new StringBuilder();
        check(split.length()>=3,"one oversized subtitle is split into bounded output blocks");
        for(int i=0;i<split.length();i++){
            JSONArray rows=split.getJSONArray(i);int chars=0;
            for(int n=0;n<rows.length();n++){
                JSONObject row=rows.getJSONObject(n);String fragment=row.getString("text");chars+=fragment.length();joined.append(fragment);
                check(row.getInt("id")==7&&row.getDouble("start")==12.5&&row.getDouble("end")==70.25,"split retains original source id and timing "+i+":"+n);
                check(!fragment.isEmpty()&&!Character.isLowSurrogate(fragment.charAt(0))&&!Character.isHighSurrogate(fragment.charAt(fragment.length()-1)),"split does not cut surrogate pairs "+i+":"+n);
            }
            check(chars<=6000,"each split block within 6000 UTF-16 characters "+i);
        }
        check(joined.toString().equals(text),"split fragments reconstruct complete original subtitle text");
        check(original.toString().equals(snapshot),"splitting never mutates original subtitle object");
    }
    private static void reuseImages()throws Exception{
        reset("reuse-images",true);File dir=materials("reuse-images",true);JSONObject result=generate(dir,true);
        JSONArray first=requests.get(4).getJSONArray("messages"),second=requests.get(5).getJSONArray("messages");
        check(first.getJSONObject(first.length()-1).getJSONArray("content").length()==3,"first final stage uploads both requested images");
        check(second.getJSONObject(second.length()-1).getJSONArray("content").length()==1,"later overlapping image request uploads no image again");
        check(result.getJSONArray("images").length()==2&&state(dir).getJSONArray("analysed").length()==2,"analysed inventory retains two unique images");
        check(result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(1).getJSONArray("images").getString(0).equals("image-0.jpg"),"later topic may cite image actually supplied in earlier message");
        appendOnly(dir);evidence("reuse-images",dir);
    }
    private static void editorial()throws Exception{
        for(String defect:List.of("cross-draft","cross-final")){
            reset(defect,defect.equals("cross-final"));File dir=materials(defect,true);rejects(()->generate(dir,true),"another valid lesson source cannot leak into this block: "+defect);
            check(!state(dir).has("editor_plan")&&!state(dir).has("final"),"cross-block semantic error never publishes or automatically retries");
        }
        reset("cross-context",true);JSONObject contextual=generate(materials("cross-context",true),true);
        check(contextual.getBoolean("complete")&&contextual.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(1).getJSONArray("sources").length()==2,"image refinement can retain a genuine earlier comparison while anchored in the current block");
        JSONObject referenced=part(0,false,"");JSONArray block=new JSONArray().put(new JSONObject().put("id",1).put("text","当前块"));
        JSONObject lecture=new JSONObject().put("segments",new JSONArray().put(new JSONObject().put("id",0)).put(new JSONObject().put("id",1)).put(new JSONObject().put("id",2))).put("images",new JSONArray());
        rejects(()->ClassroomEnhancedSummary.validateRefinement(referenced,lecture,block),"previous-only refinement cannot masquerade as the current block");
        referenced.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(1).put(2));
        rejects(()->ClassroomEnhancedSummary.validateRefinement(referenced,lecture,block),"current anchor does not authorize references to later blocks");
        reset("merge-topics",true);File merged=materials("merge-topics",true);JSONObject result=generate(merged,true);
        JSONObject document=result.getJSONArray("parts").getJSONObject(0);JSONArray notes=document.getJSONArray("notes");
        check(result.getJSONArray("parts").length()==1&&notes.length()==1,"same topic across extraction blocks is edited into one section");
        check(result.getString("overview").equals("统一全课重点")&&!result.getString("overview").contains("合成概览"),"one unified overview replaces concatenated block recaps");
        check(notes.getJSONObject(0).getJSONArray("sources").length()==2&&notes.getJSONObject(0).getJSONArray("images").length()==2,"merged topic keeps distinct source and image support");
        check(state(merged).getJSONArray("parts").length()==2&&state(merged).getJSONArray("corrected").length()==2,"raw extraction and corrected transcript remain checkpointed separately");
        check(result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").length()!=state(merged).getJSONArray("parts").length(),"reading copy is not a blind concatenation of extraction blocks");
        ClassroomDocument.validate(ClassroomDocument.portable(result,ClassroomStore.read(new File(merged,"materials.json"))));check(true,"edited single-document result keeps the existing portable/sync contract");
        ByteArrayOutputStream archive=new ByteArrayOutputStream();ClassroomExport.write(merged,result,archive);String markdown="";
        try(var zip=new java.util.zip.ZipInputStream(new ByteArrayInputStream(archive.toByteArray()),StandardCharsets.UTF_8)){java.util.zip.ZipEntry entry;while((entry=zip.getNextEntry())!=null)if(entry.getName().equals("课堂总结.md"))markdown=new String(zip.readAllBytes(),StandardCharsets.UTF_8);}
        check(markdown.contains("统一全课重点")&&!markdown.contains("合成概览")&&markdown.indexOf("## 题目条件与评分线索")==markdown.lastIndexOf("## 题目条件与评分线索"),"ZIP exports the same unified document without extraction recaps or repeated section");
        for(int n=0;n<requests.size();n++){
            String text=prompt(requests.get(n));
            if(stages.get(n).startsWith("draft-")){int id=stages.get(n).endsWith("0")?0:1;check(text.contains("["+id+"] 完整题干"),"draft appends the correct corrected slice rather than relying on recency");check(text.contains("本次允许的字幕来源id仅为：["+id+"]"),"ordinary subtitle boundary gives a disjoint extraction scope");}
            if(stages.get(n).startsWith("final-"))check(text.contains("本次唯一要修订的初稿"),"image revision identifies the actual target draft");
        }
        appendOnly(merged);evidence("editorial-merge",merged);
        reset("bad-plan",false);File invalid=materials("bad-plan",true);rejects(()->generate(invalid,true),"duplicate editorial reference rejected");
        check(requests.size()==5&&!state(invalid).has("final"),"invalid plan stops without automatic semantic repair");
        mode="normal";check(generate(invalid,true).getBoolean("complete"),"manual plan retry resumes the original extraction");
        check(Collections.frequency(stages,"correct-0")==1&&Collections.frequency(stages,"draft-0")==1,"manual editor retry never regenerates paid extraction");appendOnly(invalid);
        reset("bad-editor-source",false);File badSource=materials("bad-editor-source",false);rejects(()->generate(badSource,true),"edited prose cannot invent a source");
        check(!state(badSource).has("final")&&state(badSource).getJSONArray("editor_notes").length()==0,"invalid edited topic never reaches completed reading copy");
        reset("normal",true);File interrupted=materials("editor-interrupted",true);failCall=9;rejects(()->generate(interrupted,true),"second editorial section transport failure stops");
        check(state(interrupted).getJSONArray("editor_notes").length()==1&&!state(interrupted).has("final"),"first edited topic is saved before interruption");
        JSONObject partial=ClassroomStore.read(new File(interrupted,"summary.json"));check(partial.getJSONArray("parts").length()==1&&partial.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").length()==1,"partial reading copy shows only edited topics, never raw duplicated blocks");
        failCall=-1;check(generate(interrupted,true).getBoolean("complete"),"editor continuation finishes at its saved checkpoint");
        check(Collections.frequency(stages,"edit-note-0")==1&&Collections.frequency(stages,"edit-note-1")==1&&Collections.frequency(stages,"edit-note-1-restart-1")==1,"only interrupted topic restarts in full; completed topic stays committed");appendOnly(interrupted);evidence("editorial-resume",interrupted);
        reset("empty-tail",false);result=generate(materials("empty-tail",true),true);
        check(result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").length()==1,"non-teaching tail needs no filler section");
        JSONArray raw=new JSONArray().put(new JSONObject().put("id",7).put("text","请下周五之前提交实验报告。"));
        JSONObject task=new JSONObject().put("text","提交实验报告").put("sources",new JSONArray().put(7)).put("evidence","请下周五之前提交实验报告。");
        JSONObject tasks=new JSONObject().put("assignments",new JSONArray().put(task));ClassroomSummary.validateAssignmentEvidence(tasks,raw);check(true,"explicit assignment preserves verbatim source evidence");
        task.put("evidence","建议复习所有知识");rejects(()->ClassroomSummary.validateAssignmentEvidence(tasks,raw),"invented study suggestion lacks raw evidence");
        task.put("evidence","");rejects(()->ClassroomSummary.validateAssignmentEvidence(tasks,raw),"blank action evidence is rejected");
        JSONArray discontinuous=new JSONArray().put(new JSONObject().put("id",0).put("text","请交")).put(new JSONObject().put("id",1).put("text","并非该事项")).put(new JSONObject().put("id",2).put("text","报告"));
        task.put("sources",new JSONArray().put(0).put(2)).put("evidence","请交报告");rejects(()->ClassroomSummary.validateAssignmentEvidence(tasks,discontinuous),"evidence cannot stitch nonadjacent sources into a new instruction");
        JSONArray adjacent=new JSONArray().put(new JSONObject().put("id",24).put("text","之前作业3分。"))
                .put(new JSONObject().put("id",25).put("text","新增47分。"))
                .put(new JSONObject().put("id",26).put("text","确认单选是否正确。"));
        task.put("sources",new JSONArray().put(24).put(26)).put("evidence","之前作业3分。新增47分。确认单选是否正确。");
        try{ClassroomSummary.validateAssignmentEvidence(tasks,adjacent);check(false,"missing evidence source must fail");}catch(IOException missing){check(missing.getMessage().contains("[25]"),"diagnostic identifies the missing source rather than reporting a generic quote mismatch");}
        task.put("sources",new JSONArray().put(24).put(25).put(26));ClassroomSummary.validateAssignmentEvidence(tasks,adjacent);check(true,"complete evidence citation succeeds without weakening provenance checks");
        JSONArray parts=new JSONArray().put(new JSONObject().put("notes",new JSONArray().put(new JSONObject().put("title","同一主题"))));
        rejects(()->ClassroomEnhancedSummary.validatePlan(new JSONObject().put("overview","").put("sections",new JSONArray()),parts),"plan cannot silently drop an extracted note");
        parts.put(new JSONObject().put("notes",new JSONArray().put(new JSONObject().put("title","同一主题"))));
        JSONArray separate=new JSONArray().put(new JSONObject().put("title","第一节").put("refs",new JSONArray().put("0:0"))).put(new JSONObject().put("title","第二节").put("refs",new JSONArray().put("1:0")));
        rejects(()->ClassroomEnhancedSummary.validatePlan(new JSONObject().put("overview","概览").put("sections",separate),parts),"identical topic headings across blocks must be grouped rather than repeated");
    }
    private static void validationResume()throws Exception{
        reset("invalid-source",false);File draft=materials("draft-validation-resume",false);
        rejects(()->generate(draft,true),"invalid draft source stops without automatic retry");String run=state(draft).getString("run");
        check(requests.size()==2&&state(draft).getInt("draft-0_attempt")==1,"draft semantic failure persists one manual retry generation");
        check(!state(draft).getString("draft-0_error").isBlank(),"draft semantic failure retains actual validation reason");
        mode="normal";JSONObject result=generate(draft,true);
        check(result.getBoolean("complete")&&result.getString("run").equals(run),"draft manual continue succeeds in same run");
        check(stages.equals(List.of("correct-0","draft-0","draft-0-user-retry","edit-plan","edit-note-0")),"draft continue appends only failed-stage correction then editorial requests");
        check(state(draft).getJSONObject("conversation").getJSONArray("turns").getJSONObject(2).getString("stage").equals("draft-0-user-retry-1"),"draft retry has separate persisted stage identity");
        appendOnly(draft);evidence("draft-validation-resume",draft);

        reset("invalid-final-image",true);File fin=materials("final-validation-resume",false);
        rejects(()->generate(fin,true),"invalid final image stops without automatic retry");run=state(fin).getString("run");
        check(requests.size()==3&&state(fin).getInt("final-0_attempt")==1,"final semantic failure advances only final retry generation");
        JSONArray imageRequest=requests.get(2).getJSONArray("messages");check(imageRequest.getJSONObject(imageRequest.length()-1).getJSONArray("content").length()==2,"first final request really supplies one image");
        mode="final-retry";result=generate(fin,true);JSONArray retried=requests.get(3).getJSONArray("messages");
        check(retried.getJSONObject(retried.length()-1).getJSONArray("content").length()==1,"manual final retry appends text without uploading image again");
        check(stages.equals(List.of("correct-0","draft-0","final-0","final-0-user-retry","edit-plan","edit-note-0")),"final retry does not recompute corrected transcript or draft");
        check(result.getBoolean("complete")&&result.getString("run").equals(run)&&result.getJSONArray("images").length()==1,"valid final retry publishes original selected image in same run");
        check(result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(0).getJSONArray("images").getString(0).equals("image-0.jpg"),"retry may cite image retained in original message");
        appendOnly(fin);evidence("final-validation-resume",fin);

        reset("bad-repair",false);File repaired=materials("repair-validation-resume",false);
        rejects(()->generate(repaired,true),"initial malformed draft and its sole automatic repair both fail");run=state(repaired).getString("run");
        check(requests.size()==3&&state(repaired).getInt("draft-0_attempt")==1,"failed automatic repair leaves resumable manual generation");
        mode="bad-manual";rejects(()->generate(repaired,true),"malformed manual retry stops without second automatic repair");
        check(requests.size()==4&&state(repaired).getInt("draft-0_attempt")==2,"manual bad JSON uses exactly one request and increments retry generation");
        check(Collections.frequency(stages,"draft-0-repair")==1,"automatic repair budget remains one across manual attempts");
        mode="normal";result=generate(repaired,true);
        check(requests.size()==7&&result.getBoolean("complete")&&result.getString("run").equals(run),"next explicit manual continue completes without revisiting failed raw output");
        check(state(repaired).getJSONObject("conversation").getJSONArray("turns").getJSONObject(4).getString("stage").equals("draft-0-user-retry-2"),"second manual retry retains its own append-only stage identity");
        check(Collections.frequency(stages,"correct-0")==1&&Collections.frequency(stages,"draft-0-repair")==1,"manual attempts never repeat successful transcript or automatic repair");
        appendOnly(repaired);evidence("repair-validation-resume",repaired);
    }
    private static JSONObject draftExample(String prompt)throws Exception{
        String start="完整对象：\n";int from=prompt.indexOf(start),to=prompt.indexOf("\n字段规则：",from);
        check(from>=0&&to>from,"draft prompt contains one complete JSON example");
        JSONObject example=new JSONObject(prompt.substring(from+start.length(),to));
        check(example.keySet().equals(Set.of("overview","notes","assignments","image_assessment","image_requests")),"the JSON example contains all five mandatory top-level fields");
        return example;
    }
    private static void schemaRepair()throws Exception{
        var modeMethod=Class.forName("com.donglan.chrona.ClassroomAutoSummary").getDeclaredMethod("enhancedMode",JSONObject.class,boolean.class);modeMethod.setAccessible(true);
        for(boolean preferred:new boolean[]{false,true}){
            check((boolean)modeMethod.invoke(null,null,preferred)==preferred,"new classroom uses current mode preference");
            check((boolean)modeMethod.invoke(null,new JSONObject().put("conversation",new JSONObject()).put("final",new JSONObject()),preferred)==preferred,"completed classroom regeneration uses current mode preference");
        }
        for(boolean original:new boolean[]{false,true}){
            reset("invalid-source",false);File dir=materials("mode-resume-"+original,false);rejects(()->generate(dir,original),"mode-resume fixture stops on semantic validation");JSONObject run=state(dir);String id=run.getString("run"),before=run.toString();
            boolean retained=(boolean)modeMethod.invoke(null,run,!original);
            check(retained==original&&run.toString().equals(before),"changed global default retains unfinished pipeline without editing checkpoint");mode="normal";
            JSONObject result=generate(dir,retained);check(result.getBoolean("complete")&&result.getString("run").equals(id),"resume retains original run identity after global mode changes");appendOnly(dir);
        }
        for(int limit:new int[]{0,40})for(int count:new int[]{0,3}){
            JSONObject example=draftExample(ClassroomSummary.draftSchema(new JSONObject().put("count",count),limit));
            String expected=limit==0?"disabled":count==0?"unavailable":"request";
            check(example.getJSONObject("image_assessment").getString("decision").equals(expected),"example follows actual image permission and availability");
            check((example.getJSONArray("image_requests").length()>0)==expected.equals("request"),"disabled/unavailable example explicitly retains an empty request array");
        }
        for(String defect:List.of("missing-requests","missing-assessment","request-type")){
            reset(defect,false);File dir=materials("schema-"+defect,false);
            check(generate(dir,true).getBoolean("complete"),"missing or mistyped image schema is repaired: "+defect);
            check(stages.equals(List.of("correct-0","draft-0","draft-0-repair","edit-plan","edit-note-0")),"one appended repair without transcript regeneration: "+defect);
            draftExample(prompt(requests.get(1)));draftExample(prompt(requests.get(2)));
            check(prompt(requests.get(2)).contains("image_requests")&&prompt(requests.get(2)).contains("image_assessment"),"repair retains the full stage-specific schema: "+defect);appendOnly(dir);
        }
        reset("missing-always",false);File bounded=materials("schema-budget",false);rejects(()->generate(bounded,true),"persistently missing schema stops after the single repair");
        check(requests.size()==3&&Collections.frequency(stages,"draft-0-repair")==1,"missing schema never creates an automatic repair loop");
        reset("invalid-source",false);File manual=materials("schema-unused-budget",false);rejects(()->generate(manual,true),"semantic failure still requires manual authorization");
        mode="bad-manual";check(generate(manual,true).getBoolean("complete"),"manual retry may use a format repair that was never spent");
        check(stages.equals(List.of("correct-0","draft-0","draft-0-user-retry","draft-0-repair","edit-plan","edit-note-0")),"manual repair uses the same block and one repair identity");appendOnly(manual);
        reset("bad-json",false);File network=materials("schema-repair-network",false);failCall=3;rejects(()->generate(network,true),"network failure during repair stops without retry");
        check(requests.size()==3,"no automatic transport retry");mode="normal";failCall=-1;
        check(generate(network,true).getBoolean("complete"),"explicit retry restarts the failed repair with a full response");
        check(prompt(requests.get(3)).contains("从头完整重新生成当前板块")&&!requests.get(2).toString().equals(requests.get(3).toString()),"repair rollback appends a restart instruction without editing original messages");appendOnly(network);
        defaultFlow();
    }
    private static void markdownFences()throws Exception{
        String literal="甲\\n \\n乙",paragraph="甲\n\n乙";
        String tilde="~~~java\n"+literal+"\n`inline` and ```backticks```\n~~~\n";
        String longer="~~~\n"+literal+"\n~~~~\n";
        String indented="   ~~~text\n"+literal+"\n   ~~~\n";
        String shortClose="~~~~\n"+literal+"\n~~~\n"+literal+"\n~~~~\n";
        String wrongMarker="~~~\n"+literal+"\n```\n"+literal+"\n~~~\n";
        String suffixMarker="~~~\n"+literal+"\n~~~not-a-close\n"+literal+"\n~~~\n";
        String whitespaceClose="~~~\n"+literal+"\n~~~ \t\r\n";
        String spaceCode="    "+literal+" `literal` $\\nabla$\n";
        String tabCode="\t"+literal+" ```literal``` $\\nabla$\n";
        String backticks="```java\n"+literal+"\n~~~\n"+literal+"\n```\n";
        String longBackticks="```java\n"+literal+"\n````\n";
        String quotedBackticks="```java\nString marker=\"```\";\n"+literal+"\n```\n";
        String backtickSuffix="```java\n"+literal+"\n```not-a-close\n"+literal+"\n```\n";
        String tripleInline="正文 ```"+literal+" and `single` inside``` ";
        String inline="`"+literal+"`";
        String math="$\\nabla x \\n \\n y$",display="$$\\nabla f \\n \\n x$$";
        String[][] cases={
            {"tilde code and inner backticks stay exact",tilde,tilde},
            {"paragraph resumes after tilde close",tilde+literal,tilde+paragraph},
            {"longer tilde marker closes fence",longer+literal,longer+paragraph},
            {"three-space indented tilde markers",indented+literal,indented+paragraph},
            {"shorter tilde marker does not close",shortClose+literal,shortClose+paragraph},
            {"backticks never close tilde fence",wrongMarker+literal,wrongMarker+paragraph},
            {"tilde marker with text suffix cannot close",suffixMarker+literal,suffixMarker+paragraph},
            {"tilde close permits whitespace suffix",whitespaceClose+literal,whitespaceClose+paragraph},
            {"four-space code line stays exact",spaceCode,spaceCode},
            {"body resumes after four-space code line",spaceCode+literal,spaceCode+paragraph},
            {"tab-indented code line stays exact",tabCode,tabCode},
            {"body resumes after tab-indented code line",tabCode+literal,tabCode+paragraph},
            {"four-space tilde line cannot open fence","    ~~~\n"+literal,"    ~~~\n"+paragraph},
            {"tilde marker never closes backtick fence",backticks+literal,backticks+paragraph},
            {"longer backtick close retains protection",longBackticks+literal,longBackticks+paragraph},
            {"backticks in quoted source string cannot close fence",quotedBackticks,quotedBackticks},
            {"body resumes after quoted-backtick fenced code",quotedBackticks+literal,quotedBackticks+paragraph},
            {"backtick marker with text suffix cannot close",backtickSuffix+literal,backtickSuffix+paragraph},
            {"inline triple backticks close within text",tripleInline+literal,tripleInline+paragraph},
            {"inline code escapes stay exact",inline,inline},
            {"paragraph resumes after inline code",inline+literal,inline+paragraph},
            {"inline TeX escapes stay exact",math,math},
            {"display TeX escapes stay exact",display,display},
            {"paragraph resumes after inline math",math+literal,math+paragraph},
            {"paragraph resumes after display math",display+literal,display+paragraph},
            {"single nabla command stays exact","\\nabla x","\\nabla x"},
            {"literal paired newline remains recoverable","甲\\n\\n乙",paragraph},
            {"ordinary actual newlines stay exact","# 标题\n\n正文\n下一行","# 标题\n\n正文\n下一行"}
        };
        JSONArray evidence=new JSONArray();
        for(String[] item:cases){String actual=ClassroomDocument.normalizeMarkdown(item[1]);check(actual.equals(item[2]),item[0]);evidence.put(new JSONObject().put("case",item[0]).put("input",item[1]).put("expected",item[2]).put("actual",actual).put("passed",actual.equals(item[2])));}
        ClassroomStore.write(new File(root,"markdown-fences.json"),new JSONObject().put("cases",evidence).put("assertions",cases.length).put("failures",failures));
    }
    private static void failureDetails()throws Exception{
        Class<?> auto=Class.forName("com.donglan.chrona.ClassroomAutoSummary"),activity=Class.forName("com.donglan.chrona.ClassroomLessonActivity"),session=Class.forName("com.donglan.chrona.ClassroomLessonActivity$Session");
        var detail=auto.getDeclaredMethod("failureDetail",Exception.class,boolean.class);detail.setAccessible(true);
        var record=auto.getDeclaredMethod("execution",session,String.class,String.class);record.setAccessible(true);
        var restore=activity.getDeclaredMethod("restoredFailure",JSONObject.class,JSONObject.class);restore.setAccessible(true);
        var rejected=com.donglan.chrona.ai.ChatCompletionClient.RequestException.class.getDeclaredConstructor(int.class,String.class);rejected.setAccessible(true);
        File directory=new File(root,"failure-detail");directory.mkdirs();Object job=session.getConstructor(File.class).newInstance(directory);
        for(int code:new int[]{400,401,403,407,429,502,503}){
            Exception failure=rejected.newInstance(code,"synthetic-secret https://untrusted.invalid/private?token=synthetic-secret");
            String safe=(String)detail.invoke(null,failure,false);
            check(safe.contains("HTTP "+code),"HTTP status remains actionable: "+code);
            String action=code==401||code==403?"AI 服务密钥":code==407?"代理":code==429?"限流":code>=500?"手动继续":"模型及请求配置";
            check(safe.contains(action)&&!safe.contains("课程权限"),"recovery advice belongs to AI service rather than school access: "+code);
            check(!safe.contains("synthetic-secret")&&!safe.contains("untrusted.invalid"),"server text and URL never enter durable detail: "+code);
            ClassroomStore.write(new File(directory,"run.json"),new JSONObject().put("run","original-run").put("conversation",new JSONObject().put("marker","untouched")));
            record.invoke(null,job,"failed",safe);JSONObject saved=ClassroomStore.read(new File(directory,"run.json"));
            check(saved.getJSONObject("execution").getString("detail").equals(safe)&&saved.getJSONObject("execution").getInt("imageLimit")==40,"exact safe error persisted with existing image permission: "+code);
            check(saved.getString("run").equals("original-run")&&saved.getJSONObject("conversation").getString("marker").equals("untouched"),"failure metadata preserves existing conversation: "+code);
            check(safe.equals(restore.invoke(null,null,saved)),"reopened page restores actual HTTP cause: "+code);
        }
        String invalid="图片引用不存在：image-999.jpg";
        check(invalid.equals(detail.invoke(null,new IOException(invalid),false)),"reference validation keeps concrete cause");
        check(((String)detail.invoke(null,new IOException(),false)).contains("手动授权"),"missing message remains recoverable");
        check(((String)detail.invoke(null,new IOException(" "),false)).contains("手动授权"),"blank message remains recoverable");
        String stopped=(String)detail.invoke(null,rejected.newInstance(502,"synthetic-secret"),true);
        check(stopped.equals("已停止，已完成内容已保存"),"explicit cancellation takes precedence over late HTTP error");
        record.invoke(null,job,"interrupted",stopped);
        check(stopped.equals(restore.invoke(null,null,ClassroomStore.read(new File(directory,"run.json")))),"cancelled checkpoint restores stop state");
        String network=com.donglan.chrona.net.NetworkFailure.describe(com.donglan.chrona.net.NetworkFailure.http("素材下载","media.example.edu",503));
        check(network.equals(detail.invoke(null,com.donglan.chrona.net.NetworkFailure.http("素材下载","media.example.edu",503),false)),"existing safe network context remains intact");
        record.invoke(null,job,"complete","");
        check("".equals(restore.invoke(null,null,ClassroomStore.read(new File(directory,"run.json")))),"completion never shows stale failed detail");
    }
    private static void noteStrength()throws Exception{
        JSONArray original=new JSONArray().put(new JSONObject().put("notes",new JSONArray().put(new JSONObject().put("title","同一主题"))));
        for(String image:List.of("image-48.jpg","image-999.jpg")){
            JSONObject badPlan=new JSONObject().put("overview","![概览复活](images/"+image+")")
                    .put("sections",new JSONArray().put(new JSONObject().put("title","主题").put("refs",new JSONArray().put("0:0"))));
            rejects(()->ClassroomEnhancedSummary.validatePlan(badPlan,original),"editor overview cannot reintroduce rejected or unuploaded image");
        }
        reset("normal",false);File dir=materials("strength-three",false);JSONObject result;
        try(RequestControl c=new RequestControl(20000)){result=ClassroomSummary.generate(dir,LESSON,AI,0,c,p->{},"保留题干条件及步骤分",true,3);}
        check(result.getInt("note_strength")==3&&state(dir).getInt("note_strength")==3,"enhanced result and checkpoint retain selected strength");
        check(requests.get(0).getJSONArray("messages").getJSONObject(0).getString("content").contains("保持整理深度，压紧表达"),"enhanced requests use concise handout system");
        String plan="",edit="";for(int i=0;i<stages.size();i++){
            if(stages.get(i).equals("edit-plan"))plan=prompt(requests.get(i));
            if(stages.get(i).equals("edit-note-0"))edit=prompt(requests.get(i));
        }
        check(plan.contains("按知识依赖重排为精简讲义"),"handout plan allows stronger organization instead of lecture-order lock");
        check(edit.contains("保留自学需要的过程")&&edit.contains("不堆砌标题或小结"),"final editorial stage stays concise and self-study usable");
        check(prompt(requests.get(0)).contains("不总结或删减内容"),"style never shortens correction stage");
        appendOnly(dir);evidence("note-strength-three",dir);
    }
    public static void main(String[] args)throws Exception{
        root=new File(args[0]);root.mkdirs();
        if(args.length==1||args[1].equals("failure-detail")){
            test("durable safe failure detail and page restore",ClassroomEnhancedCheck::failureDetails);
            if(args.length>1){System.out.println("ClassroomFailureDetail: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Failure detail checks failed");return;}
        }
        if(args.length>1&&args[1].equals("markdown-fences")){
            test("Markdown fences preserve code and TeX escapes",ClassroomEnhancedCheck::markdownFences);
            System.out.println("ClassroomEnhancedMarkdownFences: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Enhanced Markdown fences checks failed");return;
        }
        HttpServer server=server();
        if(args.length==1){test("enhanced note strength and immutable prefix",ClassroomEnhancedCheck::noteStrength);test("required image schema bounded repair",ClassroomEnhancedCheck::schemaRepair);test("classroom-specific timeout and shared deadline",ClassroomEnhancedCheck::classroomTimeout);test("atomic whole-lesson assignment deduplication",ClassroomEnhancedCheck::assignmentEditorial);}
        if(args.length>1&&args[1].equals("schema-repair")){
            try{test("required image schema bounded repair",ClassroomEnhancedCheck::schemaRepair);test("existing semantic and manual budgets",ClassroomEnhancedCheck::validationResume);}
            finally{server.stop(0);}System.out.println("ClassroomSchemaRepair: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Schema repair checks failed");return;
        }
        if(args.length>1&&args[1].equals("validation-resume")){
            try{test("semantic validation and repair manual continuation",ClassroomEnhancedCheck::validationResume);}
            finally{server.stop(0);}System.out.println("ClassroomEnhancedValidationResume: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Enhanced validation resume checks failed");return;
        }
        if(args.length>1&&args[1].equals("edge-cases")){
            try{test("oversized single subtitle preserves text and identity",ClassroomEnhancedCheck::oversizedSubtitle);test("reuse earlier image without reupload",ClassroomEnhancedCheck::reuseImages);}
            finally{server.stop(0);}System.out.println("ClassroomEnhancedEdges: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Enhanced edge checks failed");return;
        }
        try{test("scoped extraction and single-document editorial checkpoints",ClassroomEnhancedCheck::editorial);test("multi-block immutable-prefix pipeline",ClassroomEnhancedCheck::multiBlock);test("network manual checkpoint resume",ClassroomEnhancedCheck::manualResume);test("one structural repair",ClassroomEnhancedCheck::repair);test("source and image whitelist",ClassroomEnhancedCheck::whitelist);test("length continuation and resume",ClassroomEnhancedCheck::lengthResume);test("cancel and resume",ClassroomEnhancedCheck::cancellation);test("default stage preservation",ClassroomEnhancedCheck::defaultFlow);test("raw JSON assistant envelope",ClassroomEnhancedCheck::jsonEnvelope);test("Markdown paragraph and TeX/code guards",ClassroomEnhancedCheck::markdown);test("oversized single subtitle preserves text and identity",ClassroomEnhancedCheck::oversizedSubtitle);test("reuse earlier image without reupload",ClassroomEnhancedCheck::reuseImages);test("semantic validation and repair manual continuation",ClassroomEnhancedCheck::validationResume);test("Markdown fences preserve code and TeX escapes",ClassroomEnhancedCheck::markdownFences);}
        finally{server.stop(0);}System.out.println("ClassroomEnhancedCheck: "+checks+" assertions, "+failures+" failures; usage is synthetic, not provider cache-hit evidence");
        if(failures>0)throw new AssertionError(failures+" enhanced pipeline failures");
    }
}
