package com.donglan.chrona.classroom;

import com.donglan.chrona.ai.AiSettings;
import com.donglan.chrona.ai.ChatCompletionClient;
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
    private static final AiSettings AI=new AiSettings("https://api.openai.com/v1","gpt-5.1","synthetic-only-key","high",AiSettings.CHAT_COMPLETIONS);
    private static final List<JSONObject> requests=new ArrayList<>(), replies=new ArrayList<>();
    private static final List<String> stages=new ArrayList<>(),endpoints=new ArrayList<>();
    private static final List<Integer> requestBytes=new ArrayList<>();
    private static File root;private static int checks,failures,failCall=-1,slowCall=-1;
    private static String mode="normal",lastStage="";private static boolean images;
    private static HttpURLConnection lastConnection;
    private static CountDownLatch requestSeen=new CountDownLatch(1);
    interface Task{void run()throws Exception;}
    private static void check(boolean ok,String message){checks++;if(!ok){failures++;System.err.println("FAIL: "+message);}}
    private static void test(String label,Task task){try{task.run();System.out.println("DONE: "+label);}catch(Throwable error){failures++;System.err.println("ERROR: "+label);error.printStackTrace();}}
    private static void rejects(Task task,String label)throws Exception{try{task.run();check(false,label);}catch(IOException|JSONException expected){check(true,label);}}
    private static void reset(String value,boolean withImages){requests.clear();replies.clear();stages.clear();endpoints.clear();requestBytes.clear();mode=value;images=withImages;lastStage="";failCall=-1;slowCall=-1;requestSeen=new CountDownLatch(1);}
    private static JSONArray messages(JSONObject request)throws Exception{return request.getJSONArray(request.has("input")?"input":"messages");}
    private static String prompt(JSONObject request)throws Exception{JSONArray messages=messages(request);return messages.getJSONObject(messages.length()-1).getJSONArray("content").getJSONObject(0).getString("text");}
    private static String referencedPrompt(JSONObject request,String prompt)throws Exception {
        Matcher reference=Pattern.compile("完整要求见历史第(\\d+)条消息").matcher(prompt);
        return reference.find()?messages(request).getJSONObject(Integer.parseInt(reference.group(1))-1).getJSONArray("content").getJSONObject(0).getString("text"):prompt;
    }
    private static boolean affairsContract(JSONObject request,String currentPrompt)throws Exception{
        JSONObject format=request.optJSONObject("response_format");if(format==null&&request.optJSONObject("text")!=null)format=request.getJSONObject("text").optJSONObject("format");
        if(format!=null){JSONObject contract=format.optJSONObject("json_schema");if(contract==null)contract=format;JSONObject schema=contract.optJSONObject("schema");if(schema!=null)return schema.getJSONObject("properties").has("affairs");}
        if(Pattern.compile("\"affairs\"\\s*:").matcher(currentPrompt).find())return true;
        // Persisted synthetic fixtures can have short systems; use their declared contract,
        // not the presence of the complete policy in every individual user message.
        return messages(request).getJSONObject(0).opt("content").toString().contains("成品严格分离");
    }
    private static String stage(String prompt){
        Matcher restart=Pattern.compile("当前阶段：([^；]+)；重试序号：(\\d+)").matcher(prompt);if(restart.find())return restart.group(1)+"-restart-"+restart.group(2);
        if(prompt.contains("唯一一次格式修复"))return lastStage+"-repair";
        if(prompt.contains("用户授权继续当前板块"))return lastStage+"-user-retry";
        if(prompt.contains("现在只依据下方全课原始字幕生成课程事务和明确任务"))return "affairs-plan";
        if(prompt.contains("现在只判断图片需求，不生成初稿"))return "image-plan";
        if(prompt.contains("一次生成最终完整笔记。没有独立提取稿"))return "whole-note";
        if(prompt.contains("全课事务证据：\n"))return "edit-affairs";
        if(prompt.contains("合并全课课堂安排中的同义重复"))return "edit-assignments";
        if(prompt.contains("现在编排一篇与普通模式相同的全课笔记"))return "edit-plan";
        Matcher editor=Pattern.compile("全课整稿主题 (\\d+)/").matcher(prompt);if(editor.find())return "edit-note-"+(Integer.parseInt(editor.group(1))-1);
        Matcher correction=Pattern.compile("更正文稿第 (\\d+)/").matcher(prompt);if(correction.find())return "correct-"+(Integer.parseInt(correction.group(1))-1);
        Matcher draft=Pattern.compile("现在只整理第 (\\d+) (?:段|内容组)").matcher(prompt);if(draft.find())return "draft-"+(Integer.parseInt(draft.group(1))-1);
        Matcher fin=Pattern.compile("只核实并完善第 (\\d+) (?:段|内容组)笔记").matcher(prompt);if(fin.find())return "final-"+(Integer.parseInt(fin.group(1))-1);
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
                .put("affairs",new JSONArray()).put("assignments",new JSONArray());
        JSONArray groupedSources=null;
        if(prompt.contains("内容组")){
            Matcher grouped=Pattern.compile("(?:本段主体字幕来源id为：|当前主体来源id：)(\\[[0-9,]+\\])").matcher(prompt);
            if(grouped.find()){groupedSources=new JSONArray(grouped.group(1));if(value.getJSONArray("notes").getJSONObject(0).getJSONArray("sources").getInt(0)!=999)value.getJSONArray("notes").getJSONObject(0).put("sources",groupedSources);}
        }
        if(mode.startsWith("task-")&&id==0)value.put("assignments",new JSONArray()
                .put(new JSONObject().put("text","今天进入作业页面查看成绩。").put("sources",new JSONArray().put(0)).put("evidence","题干 5 kg"))
                .put(new JSONObject().put("text","查看成绩。").put("sources",new JSONArray().put(0)).put("evidence","步骤分 2 分")));
        if(mode.equals("editor-affairs-to-notes"))value.put("overview","").put("affairs",value.getJSONArray("notes")).put("notes",new JSONArray());
        if(mode.equals("plan-correct-duplicate")){
            value.getJSONArray("notes").getJSONObject(0).put("title","题目评分").put("text","本题总分10分，两问各5分；第一问须证明。");
            value.getJSONArray("affairs").put(content("考试评分细则",id).put("text","这道10分题的两问各5分；第二问须给出反例。"));
        }
        if(mode.equals("cross-draft")&&draft&&id==0||mode.equals("cross-final")&&!draft&&id==0)value.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(1));
        if(mode.equals("cross-context")&&!draft&&id==1)value.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(1).put(0));
        if(!draft&&id==1&&mode.startsWith("scope-")){
            if(mode.equals("scope-affairs-past")||mode.equals("scope-repair-past"))value.put("affairs",new JSONArray().put(content("PRIVATE_OLD_AFFAIR_BODY",0)));
            if(mode.startsWith("scope-invalid"))value.put("assignments",new JSONArray().put(new JSONObject().put("text","老师要求提交额外考核作业").put("sources",new JSONArray().put(0)).put("evidence","PRIVATE_INVENTED_TEACHER_REQUIREMENT")));
            if(mode.equals("scope-legacy"))value.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(0)).put("text","PRIVATE_OLD_NOTE_BODY");
        }
        if(mode.equals("invalid-draft-markdown")&&draft)value.getJSONArray("notes").getJSONObject(0).put("text","![尚未看到的板书](lecture-slide-1.jpeg)");
        if(mode.equals("valid-final-markdown")&&!draft&&pictures.length()>0)value.getJSONArray("notes").getJSONObject(0).put("text","实际核图后的板书 ![已上传的图](images/"+pictures.getString(0)+")");
        if(mode.equals("empty-tail")&&id==1)value.put("overview","").put("notes",new JSONArray());
        if(mode.startsWith("affairs")||mode.startsWith("unified-")){
            JSONObject affair=new JSONObject(value.getJSONArray("notes").getJSONObject(0).toString()).put("title","考核与出勤 "+id).put("text","本章只考证明第一步；步骤分2分，符号错误扣1分，可能作为附加题，范围待核对。");
            value.put("affairs",new JSONArray().put(affair));
            if(!mode.equals("affairs-mixed")&&!mode.startsWith("unified-"))value.put("overview","").put("notes",new JSONArray());
            if(mode.equals("affairs-source"))affair.put("sources",new JSONArray().put(999));
            if(mode.equals("affairs-image"))affair.put("images",new JSONArray().put("image-999.jpg"));
            if(mode.startsWith("unified-"))value.put("assignments",new JSONArray().put(new JSONObject().put("text","核对题干条件").put("sources",new JSONArray().put(id)).put("evidence","题干 5 kg")));
        }
        if(draft)value.put("image_assessment",new JSONObject().put("decision",images?"request":"disabled").put("reason","合成板书需核对题目条件"))
                .put("image_requests",images?new JSONArray().put(new JSONObject().put("start",mode.equals("reuse-images")?0:id*100).put("end",mode.equals("reuse-images")?120:id*100+20).put("reason","核对题干数字与步骤分")):new JSONArray());
        if(draft&&images&&groupedSources!=null)value.put("image_requests",new JSONArray().put(new JSONObject().put("start",groupedSources.getInt(0)*100).put("end",groupedSources.getInt(groupedSources.length()-1)*100+20).put("reason","核对本内容组每个原块的板书")));
        if(!draft){JSONArray reviews=new JSONArray();Set<String> reviewed=new LinkedHashSet<>();for(int i=0;i<pictures.length();i++)if(reviewed.add(pictures.getString(i)))reviews.put(new JSONObject().put("file",pictures.getString(i)).put("status","usable").put("confidence","high").put("visible","合成图的黑白网格").put("reason","合成画面用于对应题目"));value.put("image_reviews",reviews);}
        if(mode.equals("fidelity-raw")||mode.equals("projection-mixed")){
            JSONObject first=value.getJSONArray("notes").getJSONObject(0);first.put("text","PRIVATE_DERIVED_CLAIM_"+id+"_0");
            for(int n=1;n<3;n++)value.getJSONArray("notes").put(new JSONObject(first.toString()).put("title","合成独立主题 "+id+"_"+n).put("text","PRIVATE_DERIVED_CLAIM_"+id+"_"+n));
            if(mode.equals("projection-mixed"))value.getJSONArray("affairs").put(new JSONObject(first.toString()).put("title","初稿误分类知识 "+id).put("text","PRIVATE_DERIVED_WRONG_AFFAIR_"+id));
        }
        return value;
    }
    private static JSONObject editedNote(JSONArray input,String prompt)throws Exception{
        JSONArray refs=new JSONArray(),pics=new JSONArray();Set<Integer> sourceSet=new LinkedHashSet<>();Set<String> imageSet=new LinkedHashSet<>();
        for(int n=0;n<input.length();n++){JSONObject note=input.getJSONObject(n);JSONArray source=note.getJSONArray("sources"),pictures=note.getJSONArray("images");for(int k=0;k<source.length();k++)sourceSet.add(source.getInt(k));for(int k=0;k<pictures.length();k++)imageSet.add(pictures.getString(k));}
        for(int source:sourceSet)refs.put(source);for(String image:imageSet)pics.put(image);
        if(mode.equals("bad-editor-source"))refs=new JSONArray().put(999);
        if(mode.equals("cross-editor"))refs=new JSONArray().put(1);
        if(mode.equals("bad-editor-image"))pics=new JSONArray().put("image-999.jpg");
        JSONObject result=new JSONObject(input.getJSONObject(0).toString()).put("sources",refs).put("images",pics);
        if(input.getJSONObject(0).optString("origin").equals("original_subtitles")){
            Set<Integer> written=new HashSet<>();StringBuilder prose=new StringBuilder();for(int n=0;n<input.length();n++){JSONArray raw=(input.getJSONObject(n).has("text")?new JSONObject(input.getJSONObject(n).getString("text")):editorialShared(prompt)).getJSONArray("segments");for(int k=0;k<raw.length();k++){JSONObject row=raw.getJSONObject(k);if(sourceSet.contains(row.getInt("id"))&&written.add(row.getInt("id"))){if(prose.length()>0)prose.append("\n\n");prose.append(row.getString("text"));}}}
            String marker="\n本组引用图片的已有可见内容核对：";int begin=prompt.indexOf(marker);if(begin>=0){JSONArray visible=new JSONArray(prompt.substring(begin+marker.length(),prompt.indexOf("。准入",begin)));for(int n=0;n<visible.length();n++){JSONObject row=visible.getJSONObject(n);if(imageSet.contains(row.getString("file"))&&row.optBoolean("included")&&!row.optString("visible").isBlank())prose.append("\n\n").append(row.getString("visible"));}}
            result.put("text",prose.toString());
            if(mode.equals("valid-final-markdown")&&pics.length()>0)result.put("text",result.getString("text")+"\n\n![已上传的图](images/"+pics.getString(0)+")");
        }
        if(mode.equals("plan-correct-duplicate"))result.put("text","本题总分10分，两问各5分；第一问须证明；第二问须给出反例。");
        return result;
    }
    private static JSONObject compactReply(String prompt,boolean repaired)throws Exception{
        String marker="\n全课原始字幕：\n";JSONArray raw=new JSONObject(prompt.substring(prompt.lastIndexOf(marker)+marker.length())).getJSONArray("segments"),notes=new JSONArray(),affairs=new JSONArray(),tasks=new JSONArray(),pictures=new JSONArray(),reviews=new JSONArray();
        String imageMarker="\n本次图片按顺序绑定实际file（编号不连续，不重编号）：";JSONArray inventory=new JSONArray(prompt.substring(prompt.indexOf(imageMarker)+imageMarker.length()));for(int n=0;n<inventory.length();n++){String file=inventory.getJSONObject(n).getString("file");pictures.put(file);reviews.put(new JSONObject().put("file",file).put("status","usable").put("confidence","high").put("visible","合成图的黑白网格").put("reason","对应本课实际提供的合成图"));}
        for(int n=0;n<raw.length();n++){JSONObject row=raw.getJSONObject(n);String text=row.getString("text");int id=row.getInt("id");if(text.startsWith("行动原话：")){String quote=text.substring("行动原话：".length());tasks.put(new JSONObject().put("text",quote).put("sources",new JSONArray().put(id)).put("evidence",mode.equals("compact-bad-evidence")?"PRIVATE_WRONG_ACTION_QUOTE":quote));}else if(text.startsWith("事务原话："))affairs.put(content("评分与考试",id).put("text",text.substring("事务原话：".length())));else notes.put(content("完整知识主题 "+id,id).put("text",text+(pictures.length()>0?"\n\n合成图的黑白网格":"")).put("images",new JSONArray(pictures.toString())));}
        if(prompt.contains("课程事务和具体任务已经在affairs-plan完成")){affairs=new JSONArray();tasks=new JSONArray();if(mode.equals("affairs-first-image-extra")){affairs.put(content("图中独有资源",0).put("text","本页提供黑白网格资源。").put("images",new JSONArray(pictures.toString())));tasks.put(new JSONObject().put("text","完成图中网格附题。").put("sources",new JSONArray().put(2)).put("evidence","还需完成图中标明的附题。"));notes.getJSONObject(0).put("text",raw.getJSONObject(0).getString("text"));}if(mode.equals("affairs-first-extra-bad-evidence"))tasks.put(new JSONObject().put("text","图中补充任务").put("sources",new JSONArray().put(2)).put("evidence","PRIVATE_WRONG_EXTRA_QUOTE"));}
        if(mode.equals("affairs-first-echo")){
            JSONObject original=syntheticReply(stages.indexOf("affairs-plan"));
            affairs=new JSONArray(original.getJSONArray("affairs").toString());tasks=new JSONArray(original.getJSONArray("assignments").toString());
        }
        if(mode.equals("compact-bad-source"))notes.getJSONObject(0).put("sources",new JSONArray().put(999));if(mode.equals("compact-bad-image"))notes.getJSONObject(0).put("images",new JSONArray().put("image-999.jpg"));
        if(mode.equals("compact-rejected-only")){notes.getJSONObject(0).put("text","![仅图正文](images/"+pictures.getString(0)+")");for(int n=0;n<reviews.length();n++)reviews.getJSONObject(n).put("confidence","low");}
        JSONObject result=new JSONObject().put("overview",notes.length()>0?"统一学习重点":"").put("notes",notes).put("affairs",affairs).put("assignments",tasks).put("image_reviews",reviews);if(mode.equals("compact-missing")&&!repaired)result.remove("assignments");return result;
    }
    private static HttpServer server()throws Exception{
        HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;}));
        server.createContext("/",exchange->{try{
            byte[] wire=exchange.getRequestBody().readAllBytes();requestBytes.add(wire.length);
            JSONObject body=new JSONObject(new String(wire,StandardCharsets.UTF_8));
            requests.add(body);endpoints.add(exchange.getRequestURI().getPath());String physical=prompt(body),current=stage(physical),text=referencedPrompt(body,physical);stages.add(current);int call=requests.size();
            if(!current.contains("continue")&&!current.contains("repair")&&!current.contains("user-retry"))lastStage=current;
            if(call==slowCall){requestSeen.countDown();Thread.sleep(1200);}
            if(mode.equals("json-mode-rejected")&&(body.has("response_format")||body.optJSONObject("text")!=null&&body.getJSONObject("text").has("format"))){
                byte[] error="{\"error\":{\"message\":\"JSON output mode unsupported\"}}".getBytes(StandardCharsets.UTF_8);replies.add(null);exchange.sendResponseHeaders(400,error.length);exchange.getResponseBody().write(error);return;
            }
            if(call==failCall){byte[] error="{\"error\":{\"message\":\"synthetic unavailable\"}}".getBytes(StandardCharsets.UTF_8);replies.add(null);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(503,error.length);exchange.getResponseBody().write(error);return;}
            if(mode.startsWith("revalidate-sse-")||mode.startsWith("revalidate-native-empty-")){
                String payload;
                if(mode.startsWith("revalidate-sse-")){
                    String code=mode.equals("revalidate-sse-unknown")?"unrecognized_error":mode.equals("revalidate-sse-root-rate-limit")?"rate_limit_exceeded":"server_is_overloaded";
                    JSONObject event=new JSONObject().put("type","error");if(mode.equals("revalidate-sse-root-rate-limit"))event.put("code",code);else event.put("error",new JSONObject().put("code",code).put("message","PRIVATE_SERVICE_MESSAGE"));
                    payload=(mode.equals("revalidate-sse-reasoning")?"event: response.reasoning_summary_text.delta\ndata: "+new JSONObject().put("type","response.reasoning_summary_text.delta").put("delta","PRIVATE_PARTIAL_REASONING")+"\n\n":"")+"event: error\ndata: "+event+"\n\n";
                }else{
                    boolean incomplete=mode.equals("revalidate-native-empty-incomplete")||mode.equals("revalidate-native-empty-filter");
                    JSONObject response=new JSONObject().put("id","resp_empty_"+call).put("object","response").put("status",incomplete?"incomplete":"failed").put("output",new JSONArray()).put("error",new JSONObject().put("code",mode.equals("revalidate-native-empty-unknown")?"unrecognized_error":"server_error").put("message","PRIVATE_SERVICE_MESSAGE"));
                    if(incomplete||mode.equals("revalidate-native-empty-details"))response.put("incomplete_details",new JSONObject().put("reason",mode.equals("revalidate-native-empty-filter")?"content_filter":"max_output_tokens"));
                    if(mode.equals("revalidate-native-empty-refusal"))response.getJSONArray("output").put(new JSONObject().put("id","refusal_"+call).put("type","message").put("role","assistant").put("content",new JSONArray().put(new JSONObject().put("type","refusal").put("refusal","PRIVATE_REFUSAL"))));
                    String terminal=incomplete?"response.incomplete":"response.failed";payload="event: "+terminal+"\ndata: "+new JSONObject().put("type",terminal).put("response",response)+"\n\n";
                }
                replies.add(null);byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type","text/event-stream");exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);return;
            }
            String output,finish="stop";int id=current.matches(".*-[0-9]+.*")?Integer.parseInt(current.replaceAll(".*?-(\\d+).*","$1")):0;
            if(mode.startsWith("evidence-")){
                String responseMode=(current.contains("repair")&&(mode.equals("evidence-repair")||mode.equals("evidence-malformed")))?"evidence-good":mode;
                output=assignmentEvidenceValue(responseMode).toString();
                if(mode.equals("evidence-malformed")&&!current.contains("repair"))output="{\"overview\":\"PRIVATE_PARTIAL_EVIDENCE_BODY\",\"assignments\":[";
                if(mode.equals("evidence-length"))finish="length";
            }else if(current.startsWith("correct")){
                output="# 更正文稿\n["+id+"] 完整题干、改卷细节与 $\\nabla x$。";
                if(mode.startsWith("coverage-"))output=coverageCorrected(id,mode.equals("coverage-headings"));
                if(mode.equals("length-resume")&&current.equals("correct-0")){output="# 更正文稿\n[0] 前半";finish="length";}
            }else if(current.startsWith("affairs-plan")){
                String marker="\nPPT时间目录：",rawMarker="\n全课原始字幕：\n";JSONObject catalogue=plannedCatalogue(text);JSONArray raw=new JSONObject(text.substring(text.lastIndexOf(rawMarker)+rawMarker.length(),text.lastIndexOf(marker))).getJSONArray("segments"),affairs=new JSONArray(),tasks=new JSONArray();for(int n=0;n<raw.length();n++){JSONObject row=raw.getJSONObject(n);String value=row.getString("text");if(value.startsWith("事务原话："))affairs.put(content("评分与考试",row.getInt("id")).put("text",value.substring("事务原话：".length())));if(value.startsWith("行动原话：")){String quote=value.substring("行动原话：".length());if(mode.equals("affairs-first-image-extra"))quote=quote.substring(0,quote.indexOf("还需"));tasks.put(new JSONObject().put("text",quote).put("sources",new JSONArray().put(row.getInt("id"))).put("evidence",mode.equals("affairs-first-bad-evidence")?"PRIVATE_WRONG_FIRST_QUOTE":quote));}}
                if(mode.equals("affairs-first-bad-source"))affairs.getJSONObject(0).put("sources",new JSONArray().put(999));if(mode.equals("affairs-first-bad-image"))affairs.getJSONObject(0).put("images",new JSONArray().put("image-7.jpg"));String decision=!images?"disabled":catalogue.getInt("count")==0?"unavailable":"request";JSONObject value=new JSONObject().put("image_assessment",new JSONObject().put("decision",decision).put("reason","核对原文事务与尚缺的图中信息")).put("image_requests",decision.equals("request")?new JSONArray().put(new JSONObject().put("start",0).put("end",1000).put("reason","核实图中资源")):new JSONArray()).put("affairs",affairs).put("assignments",tasks);if(mode.equals("affairs-first-missing")&&!current.contains("repair"))value.remove("assignments");output=value.toString();
            }else if(current.startsWith("image-plan")){
                String marker="\nPPT时间目录：";JSONObject catalogue=plannedCatalogue(text);String decision=!images?"disabled":catalogue.getInt("count")==0?"unavailable":mode.equals("compact-no-request")?"not_needed":"request";
                output=new JSONObject().put("image_assessment",new JSONObject().put("decision",decision).put("reason","按实际图片授权与目录判断视觉缺口"))
                        .put("image_requests",decision.equals("request")?new JSONArray().put(new JSONObject().put("start",0).put("end",1000).put("reason","核对原图题设及书面规则")):new JSONArray()).toString();
            }else if(current.startsWith("whole-note")){
                output=compactReply(text,current.contains("repair")).toString();if(mode.equals("compact-length")&&!current.contains("restart")){output="{\"overview\":\"PRIVATE_COMPACT_PARTIAL\"";finish="length";}
            }else if(current.startsWith("edit-affairs")){
                JSONObject evidence=new JSONObject(text.substring(text.indexOf("全课事务证据：\n")+"全课事务证据：\n".length()));
                JSONArray raw=evidence.getJSONObject("original_subtitles").getJSONArray("segments"),refs=new JSONArray(),pics=evidence.getJSONArray("images");StringBuilder prose=new StringBuilder();
                for(int n=0;n<raw.length();n++){JSONObject row=raw.getJSONObject(n);refs.put(row.getInt("id"));if(n>0)prose.append('\n');prose.append(row.getString("text"));}
                boolean hasAffairs=mode.startsWith("affairs")||mode.startsWith("unified-")&&!mode.equals("unified-empty")||Set.of("missed-affair","plan-correct-duplicate","editor-notes-to-affairs","editor-affairs-to-notes","bad-editor-mixed").contains(mode);
                JSONArray affairs=new JSONArray();if(hasAffairs)affairs.put(new JSONObject().put("title","课程事务").put("text",prose.toString()).put("sources",mode.equals("unified-invalid-source")?new JSONArray().put(999):refs).put("images",mode.equals("unified-invalid-image")?new JSONArray().put("image-999.jpg"):pics));
                if(mode.equals("unified-rejected-only"))affairs.getJSONObject(0).put("text","![待核图](images/image-0.jpg)").put("images",new JSONArray().put("image-0.jpg"));
                output=new JSONObject().put("affairs",affairs).toString();
            }else if(current.startsWith("edit-assignments")){
                output=new JSONObject().put("groups",new JSONArray().put(new JSONArray().put("0:0").put(mode.equals("task-invalid")?"0:9":"0:1"))).toString();
                if(mode.equals("task-length")){output="{\"groups\":[[\"0:0\"";finish="length";}
            }else if(current.startsWith("edit-plan")){
                JSONArray index=new JSONArray(text.substring(text.indexOf("已完成笔记索引：\n")+"已完成笔记索引：\n".length())),sections=new JSONArray();
                if(mode.equals("merge-topics")||mode.equals("plan-correct-duplicate")){
                    JSONArray refs=new JSONArray();for(int n=0;n<index.length();n++)refs.put(index.getJSONObject(n).getString("ref"));
                    JSONObject section=new JSONObject().put("title","统一主题").put("refs",refs);if(index.length()>0&&index.getJSONObject(0).has("collection"))section.put("collection",mode.equals("plan-correct-duplicate")?"affairs":index.getJSONObject(0).getString("collection"));sections.put(section);
                }else for(int n=0;n<index.length();n++){JSONObject section=new JSONObject().put("title",index.getJSONObject(n).getString("title")).put("refs",new JSONArray().put(index.getJSONObject(n).getString("ref")));if(index.getJSONObject(n).has("collection"))section.put("collection",index.getJSONObject(n).getString("collection"));sections.put(section);}
                if(mode.equals("bad-plan")&&sections.length()>0)sections.put(sections.getJSONObject(0));
                boolean teaching=index.length()>0&&!index.getJSONObject(0).has("collection");for(int n=0;n<index.length();n++)teaching|="notes".equals(index.getJSONObject(n).optString("collection"));
                output=new JSONObject().put("overview",teaching&&!mode.equals("plan-correct-duplicate")?"统一全课重点":"").put("sections",sections).toString();
            }else if(current.startsWith("edit-note")){
                boolean batch=text.contains("本组主题清单及提取稿：\n");
                JSONArray topics=batch?new JSONArray(text.substring(text.indexOf("本组主题清单及提取稿：\n")+"本组主题清单及提取稿：\n".length()))
                        :new JSONArray().put(new JSONObject().put("collection",text.contains("本主题只保留有学生使用价值的课程事务")||text.contains("本次统一整理本组课程事务")?"affairs":"notes").put("inputs",new JSONArray(text.substring(text.indexOf("本主题提取稿：\n")+"本主题提取稿：\n".length()))));
                JSONObject edited=new JSONObject().put("overview",mode.equals("editor-redundant-overview")?"PRIVATE_REDUNDANT_TOPIC_OVERVIEW":"").put("notes",new JSONArray()).put("affairs",new JSONArray()).put("assignments",new JSONArray());
                for(int n=0;n<topics.length();n++){
                    JSONObject topic=topics.getJSONObject(n),note=editedNote(topic.getJSONArray("inputs"),text);boolean affairs=topic.getString("collection").equals("affairs");
                    if(mode.equals("projection-mixed"))affairs=false;
                    if(mode.equals("editor-notes-to-affairs"))affairs=true;
                    if(mode.equals("editor-affairs-to-notes"))affairs=false;
                    if(mode.equals("batch-merge")&&n>0){JSONObject merged=edited.getJSONArray(affairs?"affairs":"notes").getJSONObject(0);merged.put("text",merged.getString("text")+"\n"+note.getString("text"));}
                    else edited.getJSONArray(affairs?"affairs":"notes").put(note);
                    if(mode.equals("bad-editor-multiple"))edited.getJSONArray(affairs?"affairs":"notes").put(new JSONObject(note.toString()));
                    if(mode.equals("bad-editor-mixed"))edited.getJSONArray(affairs?"notes":"affairs").put(new JSONObject(note.toString()));
                    if(mode.equals("bad-editor-assignment"))edited.getJSONArray("assignments").put(new JSONObject().put("text","PRIVATE_EXTRA_ACTION").put("sources",note.getJSONArray("sources")).put("evidence","题干 5 kg"));
                }
                if(mode.startsWith("unified-")&&edited.getJSONArray("notes").length()>0)edited.getJSONArray("affairs").put(new JSONObject(edited.getJSONArray("notes").getJSONObject(0).toString()).put("title","知识整稿纠正的同一事务").put("text","PRIVATE_DERIVED_DUPLICATE"));
                output=edited.toString();
            }else if(mode.equals("bad-json")&&current.equals("draft-0"))output="{\"overview\":";
            else if(mode.startsWith("boundary-repair-")&&current.equals(mode.substring("boundary-repair-".length())))output="{\"overview\":\"PRIVATE_GROUP_BOUNDARY_MALFORMED\"";
            else if((mode.equals("scope-repair-past")||mode.equals("scope-invalid-repair")||mode.equals("scope-legacy"))&&current.equals("final-1"))output="{\"overview\":\"PRIVATE_MALFORMED_BODY\"";
            else if(mode.equals("bad-repair")&&(current.equals("draft-0")||current.endsWith("repair")))output="{\"overview\":\"RAW_PRIVATE_PAYLOAD\"";
            else if(mode.equals("bad-manual")&&current.endsWith("user-retry"))output="{\"overview\":";
            else {JSONObject value=part(id,current.startsWith("draft")||current.equals("legacy")&&!text.contains("本次images清单：\n")&&!text.contains("上一条最终总结"),text);
                if(mode.startsWith("boundary-semantic-")&&current.equals(mode.substring("boundary-semantic-".length())))value.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(999));
                if(current.equals("draft-0")&&mode.equals("missing-requests")||mode.equals("missing-always")&&current.startsWith("draft"))value.remove("image_requests");
                if(current.equals("draft-0")&&mode.equals("missing-assessment"))value.remove("image_assessment");
                if(current.equals("draft-0")&&mode.equals("request-type"))value.put("image_requests",new JSONObject());
                if(current.equals("draft-0")&&mode.equals("affairs-missing"))value.remove("affairs");
                if(current.equals("draft-0")&&mode.equals("affairs-type"))value.put("affairs",new JSONObject());
                if(mode.equals("affairs-missing-always")&&current.startsWith("draft"))value.remove("affairs");
                if(!affairsContract(body,text))value.remove("affairs");
                output="  ```json\n"+value.toString(2)+"\n```  ";}
            if(mode.equals("length-editor")&&current.equals("edit-note-0")){output="{\"overview\":\"截断不得拼接\"";finish="length";}
            if(mode.equals("length-final")&&current.equals("final-0")){output="{\"overview\":\"截断不得拼接\"";finish="length";}
            if(mode.equals("revalidate-malformed"))output="{\"overview\":\"PRIVATE_INCOMPLETE_JSON\"";
            if(mode.equals("revalidate-length"))finish="length";
            if(mode.equals("revalidate-filter"))finish="content_filter";
            if(mode.equals("revalidate-nonstop"))finish="tool_calls";
            if(current.startsWith("affairs-plan")||current.startsWith("image-plan")){
                JSONObject format=body.optJSONObject("response_format");if(format==null&&body.optJSONObject("text")!=null)format=body.getJSONObject("text").optJSONObject("format");
                if(format!=null){JSONObject contract=format.optJSONObject("json_schema");if(contract==null)contract=format;
                    if(contract.has("schema")&&contract.getJSONObject("schema").getJSONObject("properties").getJSONObject("image_requests").getJSONObject("items").getJSONObject("properties").has("count")){
                        JSONObject plan=ClassroomSummary.parseObject(output);JSONArray ranges=plan.getJSONArray("image_requests");for(int w=0;w<ranges.length();w++)ranges.getJSONObject(w).put("count",1);output=plan.toString();
                    }
                }
            }
            if(current.startsWith("affairs-plan")&&(mode.equals("affairs-first-missing-images")||mode.equals("affairs-first-null-images"))){
                JSONObject value=ClassroomSummary.parseObject(output),fact=value.getJSONArray("affairs").getJSONObject(0);
                if(mode.equals("affairs-first-missing-images"))fact.remove("images");else fact.put("images",JSONObject.NULL);
                output=value.toString();
            }
            JSONObject reply=new JSONObject().put("role","assistant").put("content",output).put("reasoning_content","合成推理\ncall="+call);
            replies.add(reply);
            JSONObject usage=new JSONObject().put("prompt_tokens",1000+call).put("completion_tokens",100)
                    .put("total_tokens",1100+call).put("prompt_tokens_details",new JSONObject().put("cached_tokens",700+call)).put("prompt_cache_hit_tokens",600+call);
            String payload,type;
            if(body.has("input")){
                boolean failing=current.equals("draft-1")&&mode.startsWith("native-")||mode.equals("revalidate-partial")||mode.equals("revalidate-length")||mode.equals("revalidate-filter")||mode.equals("evidence-length")||mode.equals("compact-length")&&current.equals("whole-note");
                JSONObject nativeUsage=new JSONObject().put("input_tokens",1000+call).put("output_tokens",100).put("total_tokens",1100+call)
                        .put("input_tokens_details",new JSONObject().put("cached_tokens",700+call)).put("output_tokens_details",new JSONObject().put("reasoning_tokens",30));
                JSONObject nativeResponse=new JSONObject().put("id","resp_loopback_"+call).put("object","response")
                        .put("status",failing&&(mode.equals("native-incomplete")||mode.equals("native-json-incomplete")||mode.equals("revalidate-length")||mode.equals("revalidate-filter")||mode.equals("evidence-length")||mode.equals("compact-length"))?"incomplete":failing&&(mode.equals("native-failed")||mode.equals("native-error")||mode.equals("revalidate-partial"))?"failed":"completed")
                        .put("usage",nativeUsage).put("output",new JSONArray()
                                .put(new JSONObject().put("type","reasoning").put("id","rs_"+call).put("encrypted_content","opaque-native-reasoning-"+call)
                                        .put("summary",new JSONArray().put(new JSONObject().put("type","summary_text").put("text","合成推理 call="+call))))
                                .put(new JSONObject().put("id","msg_"+call).put("type","message").put("role","assistant").put("status","completed")
                                        .put("content",new JSONArray().put(new JSONObject().put("type","output_text").put("text",output)
                                                .put("annotations",new JSONArray()).put("provider_extension",new JSONObject().put("opaque","unchanged"))))));
                if(nativeResponse.getString("status").equals("incomplete"))nativeResponse.put("incomplete_details",new JSONObject().put("reason",mode.equals("revalidate-filter")?"content_filter":"max_output_tokens"));
                if(nativeResponse.getString("status").equals("failed"))nativeResponse.put("error",new JSONObject().put("code","server_error").put("message","PRIVATE-provider-failure"));
                replies.set(replies.size()-1,nativeResponse);
                String terminal=nativeResponse.getString("status").equals("completed")?"response.completed":nativeResponse.getString("status").equals("failed")?"response.failed":"response.incomplete";
                if(failing&&mode.equals("native-error"))terminal="error";
                String delta=failing&&mode.equals("native-invalid-complete")?"contradictory streamed text":output;
                String frames="event: response.output_text.delta\ndata: "+new JSONObject().put("type","response.output_text.delta").put("output_index",1).put("content_index",0).put("delta",delta)+"\n\n"
                        +"event: response.reasoning_summary_text.delta\ndata: "+new JSONObject().put("type","response.reasoning_summary_text.delta").put("delta","合成推理 delta="+call)+"\n\n";
                payload=frames
                        +"event: "+terminal+"\ndata: "+new JSONObject().put("type",terminal).put("response",nativeResponse)+"\n\n";type="text/event-stream";
                if(failing&&mode.equals("native-eof"))payload=frames;
                if(failing&&mode.equals("native-json-incomplete")){payload=nativeResponse.toString();type="application/json";}
            }else if(mode.equals("json-envelope")){
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
        return materials(name,longText?2:1,longText);
    }
    private static File materials(String name,int blockCount,boolean longText)throws Exception{
        File dir=new File(root,name+"-"+System.nanoTime());dir.mkdirs();JSONArray segments=new JSONArray(),candidates=new JSONArray();
        for(int i=0;i<blockCount;i++){
            segments.put(new JSONObject().put("start",i*100).put("end",i*100+20).put("text","题干 5 kg，步骤分 2 分。"
                    +(mode.startsWith("affairs")||mode.startsWith("unified-")?"本章只考证明第一步；步骤分2分，符号错误扣1分，可能作为附加题，范围待核对。":"")
                    +(mode.equals("plan-correct-duplicate")?"本题总分10分，两问各5分；第一问须证明；第二问须给出反例。":"")
                    +(longText?"甲".repeat(3500):"完整短字幕")));
            BufferedImage pic=new BufferedImage(48,48,BufferedImage.TYPE_INT_RGB);var g=pic.createGraphics();g.setColor(new java.awt.Color(i*60+30,i*60+30,i*60+30));g.fillRect(0,0,48,48);g.dispose();
            for(int y=0;y<48;y++)for(int x=0;x<48;x++)pic.setRGB(x,y,(x%6<1||y%6<1)?0x101010:0xeeeeee);
            File raw=new File(dir,"raw-"+i+".img");ImageIO.write(pic,"png",raw);
            candidates.put(new JSONObject().put("raw",raw.getName()).put("index",i).put("second",i*100));
        }
        ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("segments",segments).put("raw_images",candidates.length()));
        ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject().put("images",candidates));return dir;
    }
    /** A persisted 109 run deliberately lacks the new content-size and editorial-char snapshots. */
    private static File previousContentMaterials(String name,String protocol)throws Exception{
        File dir=materials(name,true);JSONObject run=new JSONObject().put("run","unfinished109-"+System.nanoTime())
                .put("config",ClassroomSummary.configKey(AI,images?2:0,"保留题干条件及步骤分",true,1,1,1))
                .put("note_strength",1).put("figure_policy",1).put("affairs_schema",1).put("editor_batch_size",2);
        try(RequestControl control=new RequestControl(20000)){
            AiSettings settings=new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol);
            new ClassroomConversation(dir,run,new com.donglan.chrona.ai.ChatCompletionClient(settings,control),"109已保存的系统消息：成品严格分离知识与课程事务");
        }
        ClassroomStore.write(new File(dir,"run.json"),run);return dir;
    }
    private static JSONObject generate(File dir,boolean enhanced)throws Exception{try(RequestControl c=new RequestControl(20000)){return expandedGenerate(dir,LESSON,AI,images?2:0,c,p->{},"保留题干条件及步骤分",enhanced);}}
    private static JSONObject state(File dir)throws Exception{return ClassroomStore.read(new File(dir,"run.json"));}
    /** Freeze the pre-compact expanded candidate used by the historical regression cases. */
    private static JSONObject expandedGenerate(File dir,ClassroomLesson lesson,AiSettings ai,int limit,RequestControl control,ClassroomImages.Progress progress,String custom)throws Exception{return expandedGenerate(dir,lesson,ai,limit,control,progress,custom,false,1,null);}
    private static JSONObject expandedGenerate(File dir,ClassroomLesson lesson,AiSettings ai,int limit,RequestControl control,ClassroomImages.Progress progress,String custom,boolean enhanced)throws Exception{return expandedGenerate(dir,lesson,ai,limit,control,progress,custom,enhanced,1,null);}
    private static JSONObject expandedGenerate(File dir,ClassroomLesson lesson,AiSettings ai,int limit,RequestControl control,ClassroomImages.Progress progress,String custom,boolean enhanced,int preferred)throws Exception{return expandedGenerate(dir,lesson,ai,limit,control,progress,custom,enhanced,preferred,null);}
    private static JSONObject expandedGenerate(File dir,ClassroomLesson lesson,AiSettings ai,int limit,RequestControl control,ClassroomImages.Progress progress,String custom,boolean enhanced,int preferred,ChatCompletionClient.ConnectionRoute route)throws Exception{
        if(enhanced){JSONObject saved=ClassroomStore.read(new File(dir,"run.json"));int strength=ClassroomNoteStrength.frozen(saved,preferred),figures=ClassroomFigureReview.policy(saved),affairs=ClassroomAffairsPolicy.frozen(saved);String config=ClassroomSummary.configKey(ai,limit,custom,true,strength,figures,affairs);
            if(saved==null||saved.has("final")||!config.equals(saved.optString("config"))){String id="expanded-fixture-"+System.nanoTime();JSONObject run=new JSONObject().put("run",id).put("config",ClassroomSummary.configKey(ai,limit,custom,true,strength,ClassroomFigureReview.VERSION,ClassroomAffairsPolicy.VERSION)).put("note_strength",strength).put("figure_policy",ClassroomFigureReview.VERSION).put("affairs_schema",ClassroomAffairsPolicy.VERSION).put("native_json",true).put("native_json_schema",true).put("affairs_source_first",true).put("editorial_source_first",true).put("unified_affairs",true).put("compact_pipeline",false).put("content_batch_size",2).put("editor_batch_size",3).put("editor_batch_chars",18000);new ClassroomConversation(dir,run,new ChatCompletionClient(ai,control,com.donglan.chrona.ai.SchedulePrompt.SYSTEM,route,"ses_"+id),ClassroomSummary.systemPrompt(custom,strength));ClassroomStore.write(new File(dir,"run.json"),run);}
        }
        return ClassroomSummary.generate(dir,lesson,ai,limit,control,progress,custom,enhanced,preferred,route);
    }
    private static void evidence(String name,File dir)throws Exception{
        JSONArray req=new JSONArray(),rep=new JSONArray();for(JSONObject value:requests)req.put(value);for(JSONObject value:replies)rep.put(value==null?JSONObject.NULL:value);
        ClassroomStore.write(new File(root,name+"-http.json"),new JSONObject().put("synthetic",true).put("fixture",dir.getName()).put("stages",stages).put("requests",req).put("replies",rep));
    }
    private static void appendOnly(File dir)throws Exception{
        for(int n=0;n<requests.size();n++){
            JSONObject options=new JSONObject(requests.get(n).toString());options.remove("messages");
            check(options.similar(stageOptions(dir,n)),"frozen base options plus only the declared stage format for request "+n);
            if(n==0)continue;
            JSONArray before=requests.get(n-1).getJSONArray("messages"),after=requests.get(n).getJSONArray("messages");
            check(after.length()>=before.length(),"history never shrinks "+n);
            for(int i=0;i<before.length();i++)check(before.getJSONObject(i).toString().equals(after.getJSONObject(i).toString()),"exact message prefix "+n+":"+i);
            if(replies.get(n-1)!=null){check(after.length()==before.length()+2,"one assistant and user appended "+n);check(after.getJSONObject(before.length()).similar(replies.get(n-1)),"raw assistant including reasoning preserved "+n);}
            else check(after.length()==before.length()+1&&prompt(requests.get(n)).contains("从头完整重新生成当前板块"),"failed attempt is retired; explicit restart appends without splicing unknown output "+n);
        }
        JSONObject conversation=state(dir).getJSONObject("conversation");JSONArray turns=conversation.getJSONArray("turns");
        check(turns.length()==requests.size(),"every sent attempt retained in turns");
        for(int n=0;n<turns.length();n++)check(turns.getJSONObject(n).getInt("request_bytes")==requestBytes.get(n),"every turn audits exact UTF-8 request bytes including HTTP failure, repair and restart "+n);
        for(int n=0;n<turns.length();n++)if(replies.get(n)!=null){JSONObject usage=turns.getJSONObject(n).getJSONObject("usage");check(usage.getJSONObject("prompt_tokens_details").getInt("cached_tokens")==701+n&&usage.getInt("prompt_cache_hit_tokens")==601+n,"synthetic cache usage retained verbatim "+n);}
        check(!state(dir).toString().contains(AI.apiKey),"run excludes API credential");
    }
    private static JSONObject stageOptions(File dir,int index)throws Exception{
        JSONObject run=state(dir),base=new JSONObject(run.getJSONObject("conversation").getJSONObject("options").toString());
        if(run.optBoolean("native_json")&&!stages.get(index).startsWith("correct-")){
            JSONObject format=new JSONObject().put("type","json_object");
            boolean responses=base.has("max_output_tokens");
            if(run.optBoolean("native_json_schema")){
                String sent=run.getJSONObject("conversation").getJSONArray("turns").getJSONObject(index).getString("stage"),best="";
                JSONObject shapes=run.getJSONObject("response_shapes");for(String key:shapes.keySet())if((sent.equals(key)||sent.startsWith(key+"-"))&&key.length()>best.length())best=key;
                JSONObject shape=new JSONObject(shapes.getJSONObject(best).toString());
                format=responses?shape.put("type","json_schema"):new JSONObject().put("type","json_schema").put("json_schema",shape);
            }
            if(responses){JSONObject text=base.optJSONObject("text");if(text==null){text=new JSONObject();base.put("text",text);}text.put("format",format);}
            else base.put("response_format",format);
        }
        return base;
    }
    private static void multiBlock()throws Exception{
        reset("normal",true);File dir=materials("multi",true);JSONObject result=generate(dir,true);evidence("multi",dir);appendOnly(dir);
        check(stages.equals(List.of("correct-0","correct-1","draft-0","final-0","edit-plan","edit-note-0","edit-affairs")),"two original correction blocks share one extraction and image-refinement group, then scan final affairs");
        check(state(dir).getInt("content_batch_size")==2&&state(dir).getInt("editor_batch_size")==3&&state(dir).getInt("editor_batch_chars")==18000&&state(dir).getInt("editor_section_done")==1,"new enhanced run snapshots the content and editorial batch contracts");
        check(result.getBoolean("complete")&&result.getInt("source_blocks")==2&&result.getInt("total_parts")==1&&result.getJSONArray("parts").length()==1,"multiple internal blocks publish one ordinary document");
        check(result.getString("corrected_transcript").contains("[0]")&&result.getString("corrected_transcript").contains("[1]"),"all corrected transcript blocks published");
        for(int i=0;i<3;i++)check(requests.get(i).getJSONArray("messages").getJSONObject(requests.get(i).getJSONArray("messages").length()-1).getJSONArray("content").length()==1,"no image upload before drafts finish "+i);
        check(result.getJSONArray("images").length()==2&&state(dir).getJSONObject("image_audit").getInt("selected_count")==2,"image budget and analysed inventory");
        check(state(dir).getJSONArray("drafts").length()==1&&state(dir).getJSONArray("drafts").getJSONObject(0).has("image_assessment")&&state(dir).getJSONArray("drafts").getJSONObject(0).getJSONArray("image_requests").length()==1,"group-level image assessment and requests persisted");
        check(state(dir).getJSONArray("drafts").getJSONObject(0).getJSONArray("notes").getJSONObject(0).getString("text").contains("。\n\n下一段")&&result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(0).getString("text").contains("题干 5 kg，步骤分 2 分。"),"draft JSON normalizes escaped paragraphs and source-first editing retains the actual original teacher text");
        for(int i=0;i<requests.size();i++)if(stages.get(i).startsWith("draft-")||stages.get(i).startsWith("final-"))
            check(prompt(requests.get(i)).contains("明确行动须按动作及对象分别提取，不因措辞相同或相邻教学内容而合并不同对象，也不因未规定提交时间或方式而漏掉；只保留原文明确要求，不自行升级成提交作业。"),"actual extraction and image-refinement prompts keep distinct action objects without inventing submission obligations: "+stages.get(i));
    }
    private static JSONObject generateWithProtocol(File dir,String protocol)throws Exception{
        AiSettings settings=new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol);
        try(RequestControl control=new RequestControl(20000)){
            return expandedGenerate(dir,LESSON,settings,images?2:0,control,p->{},"保留题干条件及步骤分",true);
        }
    }
    private static void nativeAppendOnly(File dir)throws Exception{
        for(int n=0;n<requests.size();n++){
            JSONObject request=requests.get(n),currentOptions=new JSONObject(request.toString());currentOptions.remove("input");
            check(endpoints.get(n).equals("/v1/responses")&&request.has("input")&&!request.has("messages"),"native frozen endpoint/body "+n);
            check(currentOptions.similar(stageOptions(dir,n)),"native frozen base options plus only declared stage format "+n);
            check(request.getInt("max_output_tokens")==32768&&!request.has("max_tokens")&&!request.has("max_completion_tokens")&&!request.has("stream_options"),"native output budget has no Chat fields "+n);
            if(n==0)continue;
            JSONArray before=requests.get(n-1).getJSONArray("input"),after=request.getJSONArray("input");
            for(int i=0;i<before.length();i++)check(after.getJSONObject(i).toString().equals(before.getJSONObject(i).toString()),"exact native input prefix "+n+":"+i);
            JSONObject prior=replies.get(n-1);
            boolean success=prior!=null&&prior.optString("status").equals("completed");
            JSONArray output=success?prior.getJSONArray("output"):new JSONArray();
            check(after.length()==before.length()+output.length()+1,"append only service output items plus next user "+n);
            for(int i=0;i<output.length();i++)check(after.getJSONObject(before.length()+i).toString().equals(output.getJSONObject(i).toString()),"native output item including encrypted reasoning unchanged "+n+":"+i);
            if(!success)check(prompt(request).contains("从头完整重新生成当前板块"),"native failure restarts explicitly without failed partial output "+n);
        }
        JSONObject conversation=state(dir).getJSONObject("conversation");
        check(conversation.getString("protocol").equals(AiSettings.RESPONSES),"conversation freezes explicit native protocol");
        JSONArray turns=conversation.getJSONArray("turns");check(turns.length()==requests.size(),"all native attempts audited");
        for(int n=0;n<turns.length();n++){
            JSONObject turn=turns.getJSONObject(n);check(turn.getInt("request_bytes")==requestBytes.get(n),"exact native request bytes audited "+n);
            if(replies.get(n)!=null&&replies.get(n).optString("status").equals("completed")){
                check(turn.getJSONObject("response").getJSONObject("response").toString().equals(replies.get(n).toString()),"full native response preserved in audit "+n);
                check(turn.getJSONObject("usage").getInt("prompt_tokens")==1001+n&&turn.getJSONObject("usage").getJSONObject("prompt_tokens_details").getInt("cached_tokens")==701+n,"native usage maps into classroom audit "+n);
            }
        }
        check(!state(dir).toString().contains(AI.apiKey),"native run excludes credential");
    }
    private static void nativePipeline()throws Exception{
        reset("normal",true);File dir=materials("native-multiblock",true);JSONObject result=generateWithProtocol(dir,AiSettings.RESPONSES);
        check(result.getBoolean("complete")&&result.getJSONArray("parts").length()==1&&result.getInt("source_blocks")==2,"native enhanced multi-block pipeline produces one complete document");
        check(stages.equals(List.of("correct-0","correct-1","draft-0","final-0","edit-plan","edit-note-0","edit-affairs")),"native pipeline retains original corrections while grouping extraction and images, then scans final affairs");
        check(requests.stream().anyMatch(value->value.toString().contains("input_image")),"native pipeline actually attaches selected images as input_image");
        check(requests.stream().noneMatch(value->value.toString().contains("\"type\":\"image_url\"")),"native pipeline never leaks Chat image parts");
        nativeAppendOnly(dir);evidence("native-complete",dir);
        for(boolean incomplete:new boolean[]{false,true}){
            reset(incomplete?"native-incomplete":"normal",false);File failed=previousContentMaterials("native-resume-"+incomplete,AiSettings.RESPONSES);
            if(!incomplete)failCall=4;
            rejects(()->generateWithProtocol(failed,AiSettings.RESPONSES),"native HTTP/incomplete response stops without automatic retry "+incomplete);
            JSONObject before=state(failed);String run=before.getString("run");
            check(requests.size()==4&&before.getJSONArray("corrected").length()==2&&before.getJSONArray("drafts").length()==1&&!before.has("final"),"native failure only rolls back uncommitted block "+incomplete);
            JSONArray prefix=new JSONArray(requests.get(3).getJSONArray("input").toString());
            check(!before.getJSONObject("conversation").has("pending")&&before.getJSONObject("conversation").getJSONObject("restarts").getInt("draft-1")==1,"native failure records restart checkpoint "+incomplete);
            if(incomplete)nativeFailedTurn(before,replies.get(3),"response.incomplete",true,"length");
            mode="normal";failCall=-1;
            // A user protocol change applies to new runs, never a partly completed frozen conversation.
            result=generateWithProtocol(failed,AiSettings.CHAT_COMPLETIONS);
            check(result.getBoolean("complete")&&result.getString("run").equals(run)&&Collections.frequency(stages,"correct-0")==1,"native continuation retains run and committed correction after setting switches to Chat "+incomplete);
            JSONArray resumed=requests.get(4).getJSONArray("input");
            for(int i=0;i<prefix.length();i++)check(resumed.getJSONObject(i).toString().equals(prefix.getJSONObject(i).toString()),"native retry retains exact sent prefix "+incomplete+":"+i);
            check(!resumed.toString().contains("opaque-native-reasoning-4"),"incomplete native output/encrypted items never enter resumed history "+incomplete);
            nativeAppendOnly(failed);evidence("native-resumed-"+incomplete,failed);
        }
        nativeFailureRecovery();
        reset("normal",false);File legacy=materials("native-setting-legacy-chat",false);JSONObject legacyRun=legacyState(legacy,true);
        legacyRun.getJSONObject("conversation").remove("protocol");ClassroomStore.write(new File(legacy,"run.json"),legacyRun);
        JSONArray prefix=new JSONArray(legacyRun.getJSONObject("conversation").getJSONArray("messages").toString());
        result=generateWithProtocol(legacy,AiSettings.RESPONSES);
        check(result.getBoolean("complete")&&result.getString("run").equals(legacyRun.getString("run")),"legacy no-protocol run remains usable under new default");
        for(String endpoint:endpoints)check(endpoint.equals("/v1/chat/completions"),"legacy missing protocol continues only Chat endpoint");
        JSONArray sent=requests.get(0).getJSONArray("messages");
        for(int i=0;i<prefix.length();i++)check(sent.getJSONObject(i).toString().equals(prefix.getJSONObject(i).toString()),"legacy sent Chat history is never converted "+i);
    }
    private static void nativeFailedTurn(JSONObject run,JSONObject raw,String terminal,boolean counters,String finish)throws Exception{
        JSONObject conversation=run.getJSONObject("conversation"),turn=conversation.getJSONArray("turns").getJSONObject(3);
        check(turn.getString("status").equals("failed"),"native exception remains failed, never received");
        check(turn.getJSONObject("response").getJSONObject("response").toString().equals(raw.toString()),"failed run stores full original native output including encrypted reasoning");
        JSONObject usage=turn.getJSONObject("usage");
        check(usage.getInt("input_tokens")==1004&&usage.getInt("output_tokens")==100&&usage.getInt("prompt_tokens")==1004&&usage.getInt("completion_tokens")==100
                &&usage.getInt("total_tokens")==1104&&usage.getJSONObject("prompt_tokens_details").getInt("cached_tokens")==704
                &&usage.getJSONObject("output_tokens_details").getInt("reasoning_tokens")==30,"failed run projects actual usage beside unmodified native usage");
        check(finish==null?turn.isNull("finish_reason"):finish.equals(turn.opt("finish_reason")),"failed turn retains truthful finish reason");
        if(terminal!=null)check(terminal.equals(turn.opt("terminal_type")),"failed turn retains exact SSE terminal type");
        if(counters)check(turn.getInt("content_chars")==(mode.equals("native-invalid-complete")?"contradictory streamed text".length():raw.getJSONArray("output").getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text").length())
                &&turn.getInt("reasoning_chars")=="合成推理 delta=4".length(),"failed turn projects actual streamed body/reasoning character counts");
        else check(!turn.has("content_chars")&&!turn.has("reasoning_chars"),"nonstream failure has no invented stream counters");
        check(!conversation.getJSONObject("outputs").has("draft-1")
                &&(conversation.optJSONObject("finishes")==null||!conversation.getJSONObject("finishes").has("draft-1")),"failed native output is excluded from reusable outputs/finishes");
        check(!conversation.getJSONArray("messages").toString().contains("opaque-native-reasoning-4"),"failed encrypted reasoning never enters model history");
    }
    private static void nativeFailureRecovery()throws Exception{
        for(String failure:List.of("native-failed","native-error","native-json-incomplete","native-eof","native-invalid-complete")){
            reset(failure,false);File dir=previousContentMaterials(failure,AiSettings.RESPONSES);
            rejects(()->generateWithProtocol(dir,AiSettings.RESPONSES),"native failure rejects whole uncommitted block: "+failure);
            JSONObject before=state(dir),conversation=before.getJSONObject("conversation");
            String options=conversation.getJSONObject("options").toString(),prefix=conversation.getJSONArray("messages").toString();
            String corrected=before.getJSONArray("corrected").toString(),drafts=before.getJSONArray("drafts").toString(),run=before.getString("run");
            check(requests.size()==4&&before.getJSONArray("corrected").length()==2&&before.getJSONArray("drafts").length()==1&&!before.has("final"),"no automatic retry, repair or partial publication: "+failure);
            check(!conversation.has("pending")&&conversation.getJSONObject("restarts").getInt("draft-1")==1,"failure checkpoint retires only current attempt: "+failure);
            JSONObject failedTurn=conversation.getJSONArray("turns").getJSONObject(3);String failedSnapshot=failedTurn.toString();
            if(!failure.equals("native-eof"))nativeFailedTurn(before,replies.get(3),failure.equals("native-json-incomplete")?null:failure.equals("native-error")?"error":failure.equals("native-invalid-complete")?"response.completed":"response.failed",!failure.equals("native-json-incomplete"),failure.equals("native-json-incomplete")?"length":failure.equals("native-invalid-complete")?"stop":null);
            else check(failedTurn.getString("status").equals("failed")&&failedTurn.isNull("finish_reason")
                    &&!conversation.getJSONObject("outputs").has("draft-1"),"EOF never receives fabricated completion metadata or output");
            check(conversation.getJSONArray("messages").toString().equals(requests.get(3).getJSONArray("input").toString()),"failure stores no native output items in exact sent history: "+failure);
            mode="normal";JSONObject result=generateWithProtocol(dir,AiSettings.RESPONSES),after=state(dir);
            check(result.getBoolean("complete")&&result.getString("run").equals(run),"explicit continuation finishes same native run: "+failure);
            check(after.getJSONObject("conversation").getJSONObject("options").toString().equals(options),"explicit recovery never raises output budget or changes frozen options: "+failure);
            check(after.getJSONArray("corrected").toString().equals(corrected)&&after.getJSONArray("drafts").getJSONObject(0).toString().equals(new JSONArray(drafts).getJSONObject(0).toString()),"recovery retains all committed blocks: "+failure);
            check(after.getJSONObject("conversation").getJSONArray("turns").getJSONObject(3).toString().equals(failedSnapshot),"recovery never rewrites failed evidence: "+failure);
            JSONArray old=new JSONArray(prefix),resumed=requests.get(4).getJSONArray("input");
            check(resumed.length()==old.length()+1&&prompt(requests.get(4)).contains("从头完整重新生成当前板块"),"recovery appends explicit restart only: "+failure);
            for(int i=0;i<old.length();i++)check(old.getJSONObject(i).toString().equals(resumed.getJSONObject(i).toString()),"recovery retains exact failed-attempt history prefix "+failure+":"+i);
            check(!resumed.toString().contains("opaque-native-reasoning-4"),"recovery never replays failed encrypted output: "+failure);
        }
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
        check(stages.get(stages.size()-2).equals("edit-assignments")&&stages.get(stages.size()-1).equals("edit-affairs"),"task editorial follows completed note editorial and precedes final affair scan");appendOnly(dir);
        reset("task-length",false);File interrupted=materials("task-rollback",false);rejects(()->generate(interrupted,true),"interrupted task group rolls back without partial publication");
        check(state(interrupted).getJSONArray("editor_notes").length()==1&&!state(interrupted).has("editor_assignments")&&!state(interrupted).has("final"),"task failure preserves successful notes without committing the partial grouping");
        mode="task-normal";JSONObject resumed=generate(interrupted,true);
        check(resumed.getBoolean("complete")&&Collections.frequency(stages,"correct-0")==1&&Collections.frequency(stages,"edit-note-0")==1&&stages.get(stages.size()-2).equals("edit-assignments-restart-1")&&stages.get(stages.size()-1).equals("edit-affairs"),"only interrupted assignment grouping restarts before final affairs, not transcript, images or notes");appendOnly(interrupted);
        reset("task-invalid",false);File invalid=materials("task-invalid",false);rejects(()->generate(invalid,true),"invalid task reference is not auto repaired");
        check(!state(invalid).has("editor_assignments")&&state(invalid).getInt("edit-assignments_attempt")==1,"invalid semantic grouping remains a manual retry checkpoint");
        mode="task-normal";check(generate(invalid,true).getBoolean("complete"),"explicit retry appends a valid task grouping");appendOnly(invalid);
    }
    private static void routedPipeline()throws Exception{
        java.util.concurrent.atomic.AtomicInteger opened=new java.util.concurrent.atomic.AtomicInteger(),released=new java.util.concurrent.atomic.AtomicInteger();
        com.donglan.chrona.ai.ChatCompletionClient.ConnectionRoute route=(url,control)->{
            RequestControl.check(control);opened.incrementAndGet();
            return new com.donglan.chrona.ai.ChatCompletionClient.OpenedConnection((HttpURLConnection)url.openConnection(),released::incrementAndGet);
        };
        reset("normal",true);File dir=materials("routed-pipeline",true);JSONObject result;
        try(RequestControl control=new RequestControl(20000)){
            result=expandedGenerate(dir,LESSON,AI,2,control,p->{},"保留题干条件及步骤分",true,3,route);
        }
        check(result.getBoolean("complete")&&result.getJSONArray("parts").length()==1,"scoped connection route completes the same single-document pipeline");
        check(opened.get()==requests.size()&&released.get()==opened.get(),"every successful pipeline HTTP exchange releases its own route lease");
        appendOnly(dir);
        reset("normal",false);File failed=materials("routed-recovery",false);failCall=2;opened.set(0);released.set(0);
        try(RequestControl control=new RequestControl(20000)){
            rejects(()->expandedGenerate(failed,LESSON,AI,0,control,p->{},"",true,3,route),"routed upstream failure remains a manual checkpoint");
        }
        check(opened.get()==2&&released.get()==2&&requests.size()==2,"routed HTTP error releases without fallback or automatic retry");
        failCall=-1;
        try(RequestControl control=new RequestControl(20000)){
            result=expandedGenerate(failed,LESSON,AI,0,control,p->{},"",true,3,route);
        }
        check(result.getBoolean("complete")&&Collections.frequency(stages,"correct-0")==1,"routed explicit continuation reuses committed correction");
        check(released.get()==opened.get()&&opened.get()==requests.size(),"continued pipeline releases all route leases");appendOnly(failed);
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
        check(!failed.getJSONObject("conversation").has("pending")&&failed.getJSONObject("conversation").getJSONObject("restarts").getInt("final-0")==1,"failed grouped refinement is rolled back, not left pending for a continuation");
        failCall=-1;JSONObject result=generate(dir,true);check(requests.size()==8&&result.getString("run").equals(run),"manual resume retries only failed grouped refinement then editorial stages and final affairs");
        appendOnly(dir);evidence("network-resume",dir);
    }
    private static void repair()throws Exception{
        reset("bad-json",false);File dir=materials("repair",false);check(generate(dir,true).getBoolean("complete"),"single local JSON repair completes");
        check(stages.equals(List.of("correct-0","draft-0","draft-0-repair","edit-plan","edit-note-0","edit-affairs")),"one repair appends without recomputing transcript");appendOnly(dir);evidence("repair",dir);
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
            if(modeName.equals("length-editor"))check(checkpoint.getJSONArray("editor_notes").length()==0&&!checkpoint.has("editor_section_done"),"truncated editorial batch commits neither partial notes nor original-section cursor");
            else check(checkpoint.getJSONArray("parts").length()==0&&checkpoint.getJSONArray("analysed").length()==0,"truncated image response commits neither notes nor analysed images");
            JSONObject finished=generate(interrupted,true);check(finished.getString("run").equals(before)&&!finished.toString().contains("截断不得拼接"),"complete retry never splices truncated image/editor content");
            check(Collections.frequency(stages,"correct-0")==1&&stages.stream().noneMatch(s->s.contains("continue")),"successful correction is reused, unfinished unit alone regenerates");
            if(modeName.equals("length-final")){JSONArray retried=requests.get(4).getJSONArray("messages");check(retried.getJSONObject(retried.length()-1).getJSONArray("content").length()==1,"image-stage full restart keeps original attached image in history without reupload");}
            appendOnly(interrupted);evidence(modeName,interrupted);
        }
    }
    private static void cancellation()throws Exception{
        reset("normal",false);File dir=materials("cancel",true);slowCall=2;
        try(RequestControl control=new RequestControl(20000)){
            Thread cancel=new Thread(()->{try{if(requestSeen.await(5,TimeUnit.SECONDS))control.cancel();}catch(InterruptedException error){Thread.currentThread().interrupt();}});cancel.start();
            rejects(()->expandedGenerate(dir,LESSON,AI,0,control,p->{},"保留题干条件及步骤分",true),"in-flight cancellation stops pipeline");cancel.join();
        }
        check(requests.size()==2&&state(dir).getJSONArray("corrected").length()==1&&!state(dir).has("final"),"cancel preserves completed correction without publication");
        // Wait for the synthetic slow handler to leave; no unrelated operation is stopped.
        Thread.sleep(1300);slowCall=-1;String run=state(dir).getString("run");JSONObject result=generate(dir,true);
        check(result.getBoolean("complete")&&result.getString("run").equals(run),"cancelled pipeline resumes its own checkpoint");
        check(Collections.frequency(stages,"correct-0")==1,"cancel resume does not repeat successful stage");evidence("cancel-resume",dir);
    }
    private static void defaultFlow()throws Exception{
        reset("normal",false);File dir=materials("default",true);JSONObject result;
        try(RequestControl c=new RequestControl(20000)){result=expandedGenerate(dir,LESSON,AI,0,c,p->{},"保留题干条件及步骤分");}
        check(requests.size()==1&&stages.get(0).equals("legacy")&&!result.has("corrected_transcript"),"seven-argument default remains one whole-lesson draft stage");
        draftExample(prompt(requests.get(0)));
        reset("normal",false);check(generate(materials("explicit-false",true),false).getBoolean("complete")&&requests.size()==1,"explicit false preserves default stage count");
        reset("normal",true);File illustrated=materials("default-images",true);
        try(RequestControl c=new RequestControl(20000)){result=expandedGenerate(illustrated,LESSON,AI,2,c,p->{},"保留题干条件及步骤分");}
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
        reset("reuse-images",true);File dir=previousContentMaterials("reuse-images",AiSettings.CHAT_COMPLETIONS);JSONObject result=generate(dir,true);
        JSONArray first=requests.get(4).getJSONArray("messages"),second=requests.get(5).getJSONArray("messages");
        check(first.getJSONObject(first.length()-1).getJSONArray("content").length()==5,"first final stage uploads both requested images with adjacent identities");
        check(second.getJSONObject(second.length()-1).getJSONArray("content").length()==1,"later overlapping image request uploads no image again");
        check(result.getJSONArray("images").length()==2&&state(dir).getJSONArray("analysed").length()==2,"analysed inventory retains two unique images");
        check(result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(1).getJSONArray("images").getString(0).equals("image-0.jpg"),"later topic may cite image actually supplied in earlier message");
        appendOnly(dir);evidence("reuse-images",dir);
    }
    private static void editorial()throws Exception{
        for(String cross:List.of("cross-draft","cross-final","cross-editor")){
            reset(cross,true);File dir=previousContentMaterials(cross,AiSettings.CHAT_COMPLETIONS);JSONObject accepted=generate(dir,true);
            check(accepted.getBoolean("complete")&&requests.size()==8,"same-lesson source is accepted without an extra paid retry: "+cross);
            String warningStage=cross.equals("cross-draft")?"draft-0":cross.equals("cross-final")?"final-0":"edit-note-0";
            if(!cross.equals("cross-editor"))scopeWarning(dir,accepted,warningStage,"notes[0].sources",1);
            else check(state(dir).getJSONArray("source_scope_warnings").length()==0,"a source belonging to the other theme in the same batch needs no cross-batch warning");
            appendOnly(dir);
        }
        reset("cross-context",true);JSONObject contextual=generate(previousContentMaterials("cross-context",AiSettings.CHAT_COMPLETIONS),true);
        check(contextual.getBoolean("complete")&&contextual.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(1).getJSONArray("sources").length()==2,"image refinement can retain a genuine earlier comparison while anchored in the current block");
        JSONObject referenced=part(0,false,"");JSONArray block=new JSONArray().put(new JSONObject().put("id",1).put("text","当前块"));
        JSONObject lecture=new JSONObject().put("segments",new JSONArray().put(new JSONObject().put("id",0)).put(new JSONObject().put("id",1)).put(new JSONObject().put("id",2))).put("images",new JSONArray());
        ClassroomEnhancedSummary.validateRefinement(referenced,lecture,block);check(true,"previous-only same-lesson reference is valid");
        referenced.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(1).put(2));
        ClassroomEnhancedSummary.validateRefinement(referenced,lecture,block);check(true,"already-provided later lesson source is valid");
        reset("merge-topics",true);File merged=previousContentMaterials("merge-topics",AiSettings.CHAT_COMPLETIONS);JSONObject result=generate(merged,true);
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
            if(stages.get(n).startsWith("draft-")){int id=stages.get(n).endsWith("0")?0:1;check(text.contains("["+id+"] 完整题干"),"draft appends the correct corrected slice rather than relying on recency");check(text.contains("本段主体字幕来源id为：["+id+"]")&&text.contains("可引用已提供的同课真实来源"),"draft scope keeps a main subject without hard-rejecting cross-block references");}
            if(stages.get(n).startsWith("final-"))check(text.contains("本次唯一要修订的初稿"),"image revision identifies the actual target draft");
        }
        appendOnly(merged);evidence("editorial-merge",merged);
        reset("bad-plan",false);File invalid=previousContentMaterials("bad-plan",AiSettings.CHAT_COMPLETIONS);rejects(()->generate(invalid,true),"duplicate editorial reference rejected");
        check(requests.size()==5&&!state(invalid).has("final"),"invalid plan stops without automatic semantic repair");
        mode="normal";check(generate(invalid,true).getBoolean("complete"),"manual plan retry resumes the original extraction");
        check(Collections.frequency(stages,"correct-0")==1&&Collections.frequency(stages,"draft-0")==1,"manual editor retry never regenerates paid extraction");appendOnly(invalid);
        reset("bad-editor-source",false);File badSource=materials("bad-editor-source",false);rejects(()->generate(badSource,true),"edited prose cannot invent a source");
        check(!state(badSource).has("final")&&state(badSource).getJSONArray("editor_notes").length()==0,"invalid edited topic never reaches completed reading copy");
        reset("normal",true);File interrupted=previousContentMaterials("editor-interrupted",AiSettings.CHAT_COMPLETIONS);failCall=8;rejects(()->generate(interrupted,true),"two-theme editorial batch transport failure stops");
        check(state(interrupted).getJSONArray("editor_notes").length()==0&&!state(interrupted).has("final")&&!state(interrupted).has("editor_section_done"),"failed batch commits no partial theme or cursor");
        JSONObject partial=ClassroomStore.read(new File(interrupted,"summary.json"));check(partial.getJSONArray("parts").length()==1&&partial.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").length()==0,"partial reading copy never exposes raw duplicated extraction blocks");
        failCall=-1;check(generate(interrupted,true).getBoolean("complete"),"editor continuation finishes at its saved checkpoint");
        check(Collections.frequency(stages,"edit-note-0")==1&&Collections.frequency(stages,"edit-note-0-restart-1")==1&&stages.stream().noneMatch(s->s.startsWith("edit-note-1")),"only interrupted two-theme batch restarts in full");appendOnly(interrupted);evidence("editorial-resume",interrupted);
        reset("empty-tail",false);result=generate(previousContentMaterials("empty-tail",AiSettings.CHAT_COMPLETIONS),true);
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
        rejects(()->ClassroomEnhancedSummary.validatePlan(new JSONObject().put("overview","").put("sections",new JSONArray()),parts,0),"legacy plan cannot silently drop an extracted note");
        parts.put(new JSONObject().put("notes",new JSONArray().put(new JSONObject().put("title","同一主题"))));
        JSONArray separate=new JSONArray().put(new JSONObject().put("title","第一节").put("refs",new JSONArray().put("0:0"))).put(new JSONObject().put("title","第二节").put("refs",new JSONArray().put("1:0")));
        rejects(()->ClassroomEnhancedSummary.validatePlan(new JSONObject().put("overview","概览").put("sections",separate),parts,0),"legacy identical topic headings across blocks must be grouped rather than repeated");
    }
    private static void validationResume()throws Exception{
        reset("invalid-source",false);File draft=materials("draft-validation-resume",false);
        rejects(()->generate(draft,true),"invalid draft source stops without automatic retry");String run=state(draft).getString("run");
        check(requests.size()==2&&state(draft).getInt("draft-0_attempt")==1,"draft semantic failure persists one manual retry generation");
        check(!state(draft).getString("draft-0_error").isBlank(),"draft semantic failure retains actual validation reason");
        mode="normal";JSONObject result=generate(draft,true);
        check(result.getBoolean("complete")&&result.getString("run").equals(run),"draft manual continue succeeds in same run");
        check(stages.equals(List.of("correct-0","draft-0","draft-0-user-retry","edit-plan","edit-note-0","edit-affairs")),"draft continue appends only failed-stage correction then editorial requests");
        check(state(draft).getJSONObject("conversation").getJSONArray("turns").getJSONObject(2).getString("stage").equals("draft-0-user-retry-1"),"draft retry has separate persisted stage identity");
        appendOnly(draft);evidence("draft-validation-resume",draft);

        reset("invalid-final-image",true);File fin=materials("final-validation-resume",false);
        rejects(()->generate(fin,true),"invalid final image stops without automatic retry");run=state(fin).getString("run");
        check(requests.size()==3&&state(fin).getInt("final-0_attempt")==1,"final semantic failure advances only final retry generation");
        JSONArray imageRequest=requests.get(2).getJSONArray("messages");check(imageRequest.getJSONObject(imageRequest.length()-1).getJSONArray("content").length()==3,"first final request really supplies one labelled image");
        mode="final-retry";result=generate(fin,true);JSONArray retried=requests.get(3).getJSONArray("messages");
        check(retried.getJSONObject(retried.length()-1).getJSONArray("content").length()==1,"manual final retry appends text without uploading image again");
        check(stages.equals(List.of("correct-0","draft-0","final-0","final-0-user-retry","edit-plan","edit-note-0","edit-affairs")),"final retry does not recompute corrected transcript or draft");
        check(result.getBoolean("complete")&&result.getString("run").equals(run)&&result.getJSONArray("images").length()==1,"valid final retry publishes original selected image in same run");
        check(result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(0).getJSONArray("images").getString(0).equals("image-0.jpg"),"retry may cite image retained in original message");
        appendOnly(fin);evidence("final-validation-resume",fin);

        reset("bad-repair",false);File repaired=materials("repair-validation-resume",false);
        rejects(()->generate(repaired,true),"initial malformed draft and its sole automatic repair both fail");run=state(repaired).getString("run");
        check(requests.size()==3&&state(repaired).getInt("draft-0_attempt")==1,"failed automatic repair leaves resumable manual generation");
        check(!state(repaired).getString("draft-0_error").contains("RAW_PRIVATE_PAYLOAD")&&prompt(requests.get(2)).contains("JSON")&&!prompt(requests.get(2)).contains("RAW_PRIVATE_PAYLOAD"),"enhanced parser failure and repair prompt never project original response");
        mode="bad-manual";rejects(()->generate(repaired,true),"malformed manual retry stops without second automatic repair");
        check(requests.size()==4&&state(repaired).getInt("draft-0_attempt")==2,"manual bad JSON uses exactly one request and increments retry generation");
        check(!prompt(requests.get(3)).contains("RAW_PRIVATE_PAYLOAD"),"enhanced manual continuation uses safe stage error");
        check(Collections.frequency(stages,"draft-0-repair")==1,"automatic repair budget remains one across manual attempts");
        mode="normal";result=generate(repaired,true);
        check(requests.size()==8&&result.getBoolean("complete")&&result.getString("run").equals(run),"next explicit manual continue completes without revisiting failed raw output");
        check(state(repaired).getJSONObject("conversation").getJSONArray("turns").getJSONObject(4).getString("stage").equals("draft-0-user-retry-2"),"second manual retry retains its own append-only stage identity");
        check(Collections.frequency(stages,"correct-0")==1&&Collections.frequency(stages,"draft-0-repair")==1,"manual attempts never repeat successful transcript or automatic repair");
        appendOnly(repaired);evidence("repair-validation-resume",repaired);
    }
    private static void draftImageBoundary(String prompt,String label){
        for(String rule:List.of("只处理字幕与PPT时间目录","尚未提供任何实际图片","所有images数组必须为[]",
                "所有文本字段均禁止插入Markdown图片、图片占位链接或自行命名文件","不得新增schema之外的字段",
                "只通过image_requests的时间范围请求","收到实际附图后的核图阶段才能引用给定file"))
            check(prompt.contains(rule),label+": "+rule);
    }
    private static void draftImageReferences()throws Exception{
        reset("invalid-draft-markdown",true);File dir=materials("draft-unseen-markdown",false);
        rejects(()->generate(dir,true),"unseen noncanonical JPEG Markdown stops the enhanced draft");
        JSONObject failed=state(dir),conversation=failed.getJSONObject("conversation");String identity=failed.getString("run");
        check(stages.equals(List.of("correct-0","draft-0"))&&failed.getJSONArray("drafts").length()==0&&!failed.has("selected")&&!failed.has("final"),"enhanced semantic image failure never retries or submits a draft");
        check(!ClassroomStore.read(new File(dir,"summary.json")).getBoolean("complete"),"enhanced image failure cannot publish a complete document");
        check(failed.getJSONArray("corrected").length()==1&&ClassroomStore.read(new File(dir,"corrected.json")).getBoolean("complete"),"completed correction remains reusable after draft image rejection");
        String raw=conversation.getJSONObject("outputs").getString("draft-0");
        check(raw.contains("lecture-slide-1.jpeg")&&raw.equals(replies.get(1).getString("content")),"enhanced exact invalid image output remains in audit only");
        check(failed.getInt("draft-0_attempt")==1&&failed.getString("draft-0_error").contains("图片引用"),"enhanced semantic image error waits for explicit continuation");
        draftImageBoundary(prompt(requests.get(1)),"enhanced initial draft boundary");
        JSONArray sent=messages(requests.get(1));check(sent.getJSONObject(sent.length()-1).getJSONArray("content").length()==1,"enhanced draft attaches no images");
        JSONArray prefix=new JSONArray(conversation.getJSONArray("messages").toString());String options=conversation.getJSONObject("options").toString();
        mode="valid-final-markdown";JSONObject result=generate(dir,true);
        check(result.getBoolean("complete")&&result.getString("run").equals(identity),"valid enhanced draft recovery completes in the same frozen run");
        check(stages.equals(List.of("correct-0","draft-0","draft-0-user-retry","final-0","edit-plan","edit-note-0","edit-affairs")),"explicit enhanced continuation never regenerates the completed correction or auto-repairs semantic images");
        draftImageBoundary(prompt(requests.get(2)),"enhanced appended semantic recovery boundary");
        JSONArray resumed=messages(requests.get(2));check(resumed.length()==prefix.length()+1,"enhanced continue appends exactly one user recovery message");
        for(int i=0;i<prefix.length();i++)check(prefix.getJSONObject(i).toString().equals(resumed.getJSONObject(i).toString()),"enhanced original request and failed assistant stay exact "+i);
        check(state(dir).getJSONObject("conversation").getJSONObject("options").toString().equals(options),"enhanced durable frozen options remain exact");
        for(int i=3;i<requests.size();i++)check(!prompt(requests.get(i)).contains(ClassroomSummary.DRAFT_IMAGE_RULE),"enhanced actual-image final and editorial stages exclude draft-only ban "+i);
        check(messages(requests.get(3)).getJSONObject(messages(requests.get(3)).length()-1).getJSONArray("content").length()==3,"enhanced final really attaches one individually labelled selected image");
        check(result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(0).getString("text").contains("![已上传的图](images/image-0.jpg)"),"enhanced final and editorial stages retain valid actual-image Markdown");
        appendOnly(dir);evidence("enhanced-draft-image-recovery",dir);

        reset("missing-requests",false);File repair=materials("draft-image-format-repair",false);
        check(generate(repair,true).getBoolean("complete")&&stages.equals(List.of("correct-0","draft-0","draft-0-repair","edit-plan","edit-note-0","edit-affairs")),"enhanced structural repair remains bounded to one appended message");
        draftImageBoundary(prompt(requests.get(2)),"enhanced appended sole format repair boundary");
        for(int i=3;i<requests.size();i++)check(!prompt(requests.get(i)).contains(ClassroomSummary.DRAFT_IMAGE_RULE),"enhanced editorial prompt stays free of draft boundary after repair "+i);
        appendOnly(repair);

        reset("normal",false);failCall=2;File interrupted=materials("draft-image-transport-recovery",false);
        rejects(()->generate(interrupted,true),"enhanced draft transport interruption stops without retry");
        check(requests.size()==2&&state(interrupted).getJSONArray("corrected").length()==1,"enhanced transport failure retains completed correction without another request");
        failCall=-1;check(generate(interrupted,true).getBoolean("complete")&&stages.equals(List.of("correct-0","draft-0","draft-0-restart-1","edit-plan","edit-note-0","edit-affairs")),"explicit enhanced transport recovery restarts only the draft stage");
        draftImageBoundary(prompt(requests.get(2)),"enhanced appended interrupted-stage recovery boundary");
        appendOnly(interrupted);

        reset("invalid-draft-markdown",true);File nativeRun=materials("native-draft-image-recovery",false);
        rejects(()->generateWithProtocol(nativeRun,AiSettings.RESPONSES),"native completed response with invalid draft Markdown stops before commit");
        JSONObject nativeFailure=state(nativeRun);String nativeIdentity=nativeFailure.getString("run");
        check(requests.size()==2&&nativeFailure.getJSONArray("drafts").length()==0&&replies.get(1).getString("status").equals("completed"),"native semantic image failure does not trigger a retry despite successful transport");
        draftImageBoundary(prompt(requests.get(1)),"native initial draft boundary");
        String nativeOptions=nativeFailure.getJSONObject("conversation").getJSONObject("options").toString();
        mode="valid-final-markdown";result=generateWithProtocol(nativeRun,AiSettings.CHAT_COMPLETIONS);
        check(result.getBoolean("complete")&&result.getString("run").equals(nativeIdentity),"native explicit continuation stays in the same run even when preferred protocol changes");
        draftImageBoundary(prompt(requests.get(2)),"native appended semantic recovery boundary");
        check(state(nativeRun).getJSONObject("conversation").getJSONObject("options").toString().equals(nativeOptions),"native semantic recovery preserves exact durable options");
        for(int i=3;i<requests.size();i++)check(!prompt(requests.get(i)).contains(ClassroomSummary.DRAFT_IMAGE_RULE),"native actual-image final and editorial stages exclude draft-only ban "+i);
        check(result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(0).getString("text").contains("![已上传的图](images/image-0.jpg)"),"native recovery still accepts actual-image Markdown");
        nativeAppendOnly(nativeRun);evidence("native-draft-image-recovery",nativeRun);
    }
    private static JSONObject draftExample(String prompt)throws Exception{
        String start="完整对象：\n";int from=prompt.indexOf(start),to=prompt.indexOf("\n字段规则：",from);
        check(from>=0&&to>from,"draft prompt contains one complete JSON example");
        JSONObject example=new JSONObject(prompt.substring(from+start.length(),to));
        check(example.keySet().equals(Set.of("overview","notes","affairs","assignments","image_assessment","image_requests")),"the JSON example contains all six mandatory top-level fields");
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
            String prompt=ClassroomSummary.draftSchema(new JSONObject().put("count",count),limit);
            JSONObject example=draftExample(prompt);
            String expected=limit==0?"disabled":count==0?"unavailable":"request";
            check(example.getJSONObject("image_assessment").getString("decision").equals(expected),"example follows actual image permission and availability");
            check((example.getJSONArray("image_requests").length()>0)==expected.equals("request"),"disabled/unavailable example explicitly retains an empty request array");
            check(prompt.contains("输出前核对全部顶层字段齐全"),"model must preflight mandatory image fields rather than omit requests");
            if(limit>0&&count>0){
                check(prompt.contains("尚未附图不等于图片不可用")&&prompt.contains("禁止disabled或unavailable"),"available catalogue is distinct from first-stage upload absence");
                JSONObject unavailable=new JSONObject(example.toString()).put("image_assessment",new JSONObject().put("decision","unavailable").put("reason","尚未附图" )).put("image_requests",new JSONArray());
                rejects(()->ClassroomSummary.validateImageAssessment(unavailable,new JSONObject().put("count",count).put("limit",limit)),"available images cannot be silently treated as unavailable");
            }
        }
        for(String defect:List.of("missing-requests","missing-assessment","request-type")){
            reset(defect,false);File dir=materials("schema-"+defect,false);
            check(generate(dir,true).getBoolean("complete"),"missing or mistyped image schema is repaired: "+defect);
            check(stages.equals(List.of("correct-0","draft-0","draft-0-repair","edit-plan","edit-note-0","edit-affairs")),"one appended repair without transcript regeneration: "+defect);
            draftExample(prompt(requests.get(1)));draftExample(referencedPrompt(requests.get(2),prompt(requests.get(2))));
            check(prompt(requests.get(2)).contains("完整schema")&&!prompt(requests.get(2)).contains("完整对象："),"repair points to the original full schema rather than copying it: "+defect);appendOnly(dir);
            check(prompt(requests.get(2)).contains("不编造来源、未见图片或课堂事实")&&prompt(requests.get(2)).contains("学习笔记可按原要求使用标明的补充例题"),"actual repair preserves classroom evidence boundaries and labelled supplemental examples: "+defect);
            check(prompt(requests.get(1)).startsWith("当前JSON阶段：draft-0。")&&prompt(requests.get(2)).contains("完整要求见历史第4条消息")&&prompt(requests.get(2)).contains("沿用该消息中的阶段锚点"),"enhanced JSON repair references its visible original stage anchor: "+defect);
        }
        reset("missing-always",false);File bounded=materials("schema-budget",false);rejects(()->generate(bounded,true),"persistently missing schema stops after the single repair");
        check(requests.size()==3&&Collections.frequency(stages,"draft-0-repair")==1,"missing schema never creates an automatic repair loop");
        reset("invalid-source",false);File manual=materials("schema-unused-budget",false);rejects(()->generate(manual,true),"semantic failure still requires manual authorization");
        mode="bad-manual";check(generate(manual,true).getBoolean("complete"),"manual retry may use a format repair that was never spent");
        check(stages.equals(List.of("correct-0","draft-0","draft-0-user-retry","draft-0-repair","edit-plan","edit-note-0","edit-affairs")),"manual repair uses the same block and one repair identity");appendOnly(manual);
        reset("bad-json",false);File network=materials("schema-repair-network",false);failCall=3;rejects(()->generate(network,true),"network failure during repair stops without retry");
        check(requests.size()==3,"no automatic transport retry");mode="normal";failCall=-1;
        check(generate(network,true).getBoolean("complete"),"explicit retry restarts the failed repair with a full response");
        check(state(network).getJSONObject("conversation").getJSONArray("turns").getJSONObject(3).getString("stage").equals("draft-0-repair-restart-1"),"attempt-zero repair interruption resumes the same repair identity");
        check(prompt(requests.get(3)).contains("从头完整重新生成当前板块")&&!requests.get(2).toString().equals(requests.get(3).toString()),"repair rollback appends a restart instruction without editing original messages");appendOnly(network);
        reset("invalid-source",false);File manualNetwork=materials("schema-manual-repair-network",false);rejects(()->generate(manualNetwork,true),"semantic failure preserves unused repair budget");
        mode="bad-manual";failCall=4;rejects(()->generate(manualNetwork,true),"first repair may be interrupted after manual semantic retry");
        check(state(manualNetwork).getInt("draft-0_attempt")==1&&state(manualNetwork).getInt("draft-0_repair_attempt")==1,"manual repair transport failure retains originating validation attempt");
        mode="normal";failCall=-1;check(generate(manualNetwork,true).getBoolean("complete"),"same manual repair identity restarts after transport interruption");
        check(state(manualNetwork).getJSONObject("conversation").getJSONArray("turns").getJSONObject(4).getString("stage").equals("draft-0-repair-restart-1"),"manual repair recovery does not create a second repair budget");appendOnly(manualNetwork);
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
        for(int code:new int[]{400,401,403,407,413,429,502,503}){
            Exception failure=rejected.newInstance(code,"synthetic-secret https://untrusted.invalid/private?token=synthetic-secret");
            String safe=(String)detail.invoke(null,failure,false);
            check(safe.contains("HTTP "+code),"HTTP status remains actionable: "+code);
            String action=code==401||code==403?"AI 服务密钥":code==407?"代理":code==413?"请求体上限":code==429?"限流":code>=500?"手动继续":"模型及请求配置";
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
        for(String json:List.of("{\"overview\":\"RAW_PRIVATE_PAYLOAD\"","{\"notes\":{\"RAW_PRIVATE_PAYLOAD\":1}}")){
            try{JSONObject value=ClassroomSummary.parseObject(json);value.getJSONArray("notes");check(false,"real malformed/type JSON throws");}
            catch(JSONException failure){String safe=(String)detail.invoke(null,failure,false);check(safe.contains("JSON")&&!safe.contains("RAW_PRIVATE_PAYLOAD"),"real parser exception is safely projected by auto-summary");}
        }
        for(String old:List.of("Unterminated object at character 9210 of {RAW_PRIVATE_PAYLOAD", "Value {RAW_PRIVATE_PAYLOAD} of type org.json.JSONObject cannot be converted to JSONArray")){
            JSONObject stored=new JSONObject().put("run","same-run").put("execution",new JSONObject().put("state","failed").put("detail",old));
            check(!((String)restore.invoke(null,null,stored)).contains("RAW_PRIVATE_PAYLOAD"),"old execution detail restores without original response");
            stored.remove("execution");stored.put("final_validation",new JSONObject().put("status","failed").put("error",old));
            check(!((String)restore.invoke(null,new JSONObject().put("run","same-run"),stored)).contains("RAW_PRIVATE_PAYLOAD"),"old final validation detail restores without original response");
            check(stored.getJSONObject("final_validation").getString("error").equals(old),"display projection leaves original stored evidence unchanged");
        }
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
        JSONArray original=new JSONArray().put(new JSONObject().put("notes",new JSONArray().put(new JSONObject().put("title","同一主题"))).put("affairs",new JSONArray()));
        JSONObject textPlan=new JSONObject().put("overview","纯文字知识概览").put("sections",new JSONArray().put(section("notes","0:notes:0")));
        ClassroomEnhancedSummary.validatePlan(textPlan,original);check(true,"valid schema-one plan accepts a plain-text overview before image rejection checks");
        for(String image:List.of("image-48.jpg","image-999.jpg")){
            JSONObject badPlan=new JSONObject(textPlan.toString()).put("overview","![概览复活](images/"+image+")");
            try{ClassroomEnhancedSummary.validatePlan(badPlan,original);check(false,"editor overview cannot reintroduce rejected or unuploaded image");}
            catch(IOException invalid){check(invalid.getMessage().equals("整稿概览只允许文字，配图须在主题笔记中核对"),"overview image is rejected by the image guard rather than an unrelated schema failure: "+image);}
        }
        reset("normal",false);File dir=materials("strength-three",false);JSONObject result;
        try(RequestControl c=new RequestControl(20000)){result=expandedGenerate(dir,LESSON,AI,0,c,p->{},"保留题干条件及步骤分",true,3);}
        check(result.getInt("note_strength")==3&&state(dir).getInt("note_strength")==3,"enhanced result and checkpoint retain selected strength");
        String system=requests.get(0).getJSONArray("messages").getJSONObject(0).getString("content");
        fidelitySystemRules(system);
        check(system.contains("保持整理深度，压紧表达"),"enhanced requests use concise handout system");
        check(system.contains("自行核对所讲知识及示例")&&system.contains("程序检查执行逻辑与边界"),"actual request checks examples in a subject-appropriate way");
        check(system.contains("完整题干")&&system.contains("补齐必要推导")&&system.contains("不根据答案反造原题"),"actual request preserves questions while distinguishing valid derivation from invented facts");
        check(system.contains("同类型")&&system.contains("题目标为‘补充例题’")&&system.contains("课程事务以教师实际说过的内容为准"),"actual strength-three system allows labelled replacement examples with source-based course affairs");
        check(system.contains("AI自主组织具体教学细节")&&system.contains("不要求跟随课堂的措辞、讲解顺序、具体推导或解法"),"third level follows knowledge direction rather than reproducing teacher process");
        check(system.contains("不限于原题无法确定的情况")&&system.contains("不与同机制原题重复展开"),"third level may choose better teaching examples without duplicating the original");
        check(system.contains("不写‘有一道题但无法复原’的空话"),"actual handout system replaces or omits unusable incomplete-example narration");
        check(system.contains("适用对象、时间和范围直接写进对应句子")&&system.contains("后文修正或澄清更新前面的理解"),"actual affairs instructions express concrete scope and later clarification directly");
        check(system.contains("教师指令的动作与方向按原文语境表达")&&system.contains("其余含混闲话省略"),"actual request preserves teacher direction and omits unhelpful uncertain chatter");
        check(system.contains("保留决定含义的对象、数字、条件和变化方向"),"actual request retains material assessment facts without requiring every minor example");
        check(system.contains("只有影响理解或行动的具体疑点")&&system.contains("省去识别纠正"),"actual request only mentions useful concrete uncertainty and removes processing narration");
        check(system.contains("不再叠加课堂口述版")&&system.contains("同一道题将题干"),"handout combines each example and proof rather than repeating presentation layers");
        check(system.contains("可选做法合为一项")&&system.contains("概览不混入考试"),"actions retain alternative paths and overview stays educational");
        String plan="",edit="";for(int i=0;i<stages.size();i++){
            if(stages.get(i).equals("edit-plan"))plan=prompt(requests.get(i));
            if(stages.get(i).equals("edit-note-0"))edit=prompt(requests.get(i));
        }
        check(plan.contains("按知识依赖重排为精简讲义"),"handout plan allows stronger organization instead of lecture-order lock");
        check(plan.contains("索引以sources定位下方唯一原始字幕集合中的语境")&&plan.contains("题名和原集合仅用于定位，不作为事实或分类结论"),"new plan groups actual original evidence rather than treating derived titles or repeated prose as facts");
        check(plan.contains("不同题目条件、评分例外和相反结论不可误并"),"global grouping preserves distinct conditions and grading exceptions");
        check(edit.contains("保留自学需要的过程")&&edit.contains("不堆砌标题或小结"),"final editorial stage stays concise and self-study usable");
        check(edit.contains("原题无法确定时可换用同主线、同类型及相近难度的完整题目")&&edit.contains("标题标为‘补充例题’")&&edit.contains("不冒充老师原题或考核要求"),"actual final-topic request explicitly permits labelled complete replacement examples");
        check(edit.contains("先核对本主题的题干、公式、定义域和证明")&&edit.contains("最终结果"),"topic output requests complete self-consistent working rather than calculation placeholders");
        check(edit.contains("应纠正到affairs")&&edit.contains("具体行动保持在assignments，不重复抄录"),"knowledge topics correct misplaced affairs while keeping actions in their separate collection");
        check(edit.contains("关联用主题名称")&&edit.contains("重复事实只在所属主题完整讲一次"),"topic editing avoids stale relative references and cross-topic repetition");
        check(prompt(requests.get(0)).contains("不总结或删减内容"),"style never shortens correction stage");
        appendOnly(dir);evidence("note-strength-three",dir);
    }
    private static JSONObject content(String title,int source)throws Exception{return new JSONObject().put("title",title).put("text","保留条件与不确定性").put("sources",new JSONArray().put(source)).put("images",new JSONArray());}
    private static File editorialFixture(String protocol,int[] lengths,boolean mixed,boolean batch)throws Exception{
        File dir=materials("editorial-batch-"+protocol,false);JSONArray notes=new JSONArray(),affairs=new JSONArray(),sections=new JSONArray();
        for(int i=0;i<lengths.length;i++){
            String collection=mixed&&i%2==1?"affairs":"notes";JSONArray rows=collection.equals("notes")?notes:affairs;
            JSONObject note=content("原计划主题 "+i,0).put("text","甲".repeat(lengths[i]));
            sections.put(section(collection,"0:"+collection+":"+rows.length()).put("title",note.getString("title")));rows.put(note);
        }
        JSONObject part=new JSONObject().put("overview","已有知识概览").put("notes",notes).put("affairs",affairs).put("assignments",new JSONArray());
        JSONObject run=new JSONObject().put("run","batched-"+System.nanoTime()).put("config",ClassroomSummary.configKey(AI,0,"保留题干条件及步骤分",true,1,1,1))
                .put("note_strength",1).put("figure_policy",1).put("affairs_schema",1)
                .put("blocks",new JSONArray().put(new JSONArray().put(new JSONObject().put("id",0).put("text","已有完整更正来源"))))
                .put("corrected",new JSONArray().put("[0] 已成功更正的完整稿，不重做"))
                .put("drafts",new JSONArray().put(new JSONObject(part.toString()))).put("parts",new JSONArray().put(part))
                .put("selected",new JSONArray()).put("analysed",new JSONArray())
                .put("editor_plan",new JSONObject().put("overview","原计划知识概览").put("sections",sections));
        if(batch)run.put("editor_batch_size",2);
        try(RequestControl control=new RequestControl(20000)){
            AiSettings settings=new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol);
            new ClassroomConversation(dir,run,new com.donglan.chrona.ai.ChatCompletionClient(settings,control),"已有不可变合成系统消息：成品严格分离知识与课程事务");
        }
        ClassroomStore.write(new File(dir,"run.json"),run);return dir;
    }
    private static void batchHistory(File dir,String protocol)throws Exception{if(protocol.equals(AiSettings.RESPONSES))nativeAppendOnly(dir);else appendOnly(dir);}
    private static JSONObject generateContent(File dir,String protocol,List<ClassroomProgress> progress)throws Exception{
        AiSettings settings=new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol);
        try(RequestControl control=new RequestControl(20000)){
            return expandedGenerate(dir,LESSON,settings,images?40:0,control,progress::add,"保留题干条件及步骤分",true);
        }
    }
    private static void contentBatchRows()throws Exception{
        check(ClassroomEnhancedSummary.contentGroups(new JSONArray(),2).length()==0,"empty original blocks produce no content groups");
        JSONArray original=new JSONArray();
        for(int i=0;i<5;i++)original.put(new JSONArray().put(new JSONObject().put("id",i/2).put("text",i%2==0?"😀原文\n甲":"乙\t𝄞").put("start",i+.25).put("end",i+.75)));
        String snapshot=original.toString();JSONArray grouped=ClassroomEnhancedSummary.contentGroups(original,2);int row=0;
        check(grouped.length()==3&&grouped.getJSONArray(2).length()==1,"odd original-block count leaves one final content group");
        for(int i=0;i<grouped.length();i++)for(int n=0;n<grouped.getJSONArray(i).length();n++,row++)check(grouped.getJSONArray(i).getJSONObject(n).toString().equals(original.getJSONArray(row).getJSONObject(0).toString()),"content groups preserve exact row order, duplicate ids, Unicode, text and timing "+row);
        check(row==5&&original.toString().equals(snapshot),"grouping neither drops rows nor rewrites original correction blocks");
        check(ClassroomEnhancedSummary.contentGroups(original,1).toString().equals(snapshot),"legacy size one retains its exact original blocks");
        JSONArray split=ClassroomEnhancedSummary.blocks(new JSONArray().put(new JSONObject().put("id",7).put("text","甲".repeat(5999)+"😀"+"乙".repeat(12500)).put("start",1.25).put("end",100.5))),groups=ClassroomEnhancedSummary.contentGroups(split,2);
        StringBuilder rejoined=new StringBuilder();for(int i=0;i<groups.length();i++)for(int n=0;n<groups.getJSONArray(i).length();n++)rejoined.append(groups.getJSONArray(i).getJSONObject(n).getString("text"));
        check(rejoined.toString().equals("甲".repeat(5999)+"😀"+"乙".repeat(12500)),"content grouping retains every UTF16 code unit across original surrogate-safe correction slices");
    }
    private static void contentBatches()throws Exception{
        contentBatchRows();
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES)){
            reset("normal",true);File four=materials("content-four-"+protocol,4,true);List<ClassroomProgress> progress=new ArrayList<>();JSONObject result=generateContent(four,protocol,progress),run=state(four);
            check(stages.equals(List.of("correct-0","correct-1","correct-2","correct-3","draft-0","draft-1","final-0","final-1","edit-plan","edit-note-0","edit-affairs")),"four original blocks keep correction/content/image requests and share deduplicated original-context editorial inputs within the existing budget: "+protocol);
            check(run.getInt("content_batch_size")==2&&run.getInt("editor_batch_size")==3&&run.getInt("editor_batch_chars")==18000&&run.getJSONArray("blocks").length()==4&&run.getJSONArray("drafts").length()==2&&run.getJSONArray("parts").length()==2,"new run freezes two content blocks and three editorial themes without changing correction blocks");
            check(result.getBoolean("complete")&&result.getInt("source_blocks")==4&&result.getJSONArray("parts").length()==1&&result.getJSONArray("images").length()==4&&run.getJSONArray("analysed").length()==4,"grouped pipeline publishes one document and never misses a selected image");
            JSONArray groups=ClassroomEnhancedSummary.contentGroups(run.getJSONArray("blocks"),2),corrected=run.getJSONArray("corrected");
            for(int i=0;i<2;i++){
                String prompt=prompt(requests.get(4+i)),marker="本次要提取的更正文稿（历史原消息保持不变，此处仅追加定位）：\n",raw="\n事实核对及安排原话定位用原始字幕";
                String actual=prompt.substring(prompt.indexOf(marker)+marker.length(),prompt.indexOf(raw));
                StringJoiner bounded=new StringJoiner("\n\n");for(int n=i*2;n<i*2+2;n++)bounded.add("【原始段 "+(n+1)+" 开始】\n"+corrected.getString(n)+"\n【原始段 "+(n+1)+" 结束】");
                check(actual.equals(bounded.toString()),"draft appends both committed correction texts unchanged inside explicit original-block boundaries "+i);
                check(prompt.contains(ClassroomSummary.uploadChunk(new JSONObject().put("segments",groups.getJSONArray(i)).put("images",new JSONArray())).toString()),"raw evidence and scope cover every original row of content group "+i);
                check(run.getJSONArray("drafts").getJSONObject(i).getJSONArray("notes").getJSONObject(0).getJSONArray("sources").toString().equals(new JSONArray().put(i*2).put(i*2+1).toString()),"actual extraction fixture references both original-block source ids "+i);
            }
            for(ClassroomProgress event:progress)if(event.stage.equals("AI 分段整理与核图计划")||event.stage.equals("AI 分段完成笔记"))check(event.total==2,"content progress denominator is groups, not correction blocks");
            batchHistory(four,protocol);evidence("content-four-two-"+protocol,four);
            reset("normal",true);File odd=materials("content-odd-"+protocol,3,true);result=generateContent(odd,protocol,new ArrayList<>());run=state(odd);
            check(Collections.frequency(stages,"draft-0")==1&&Collections.frequency(stages,"draft-1")==1&&run.getJSONArray("blocks").length()==3&&run.getJSONArray("parts").length()==2&&result.getJSONArray("images").length()==3,"odd block count retains the last correction, content and figure exactly once: "+protocol);batchHistory(odd,protocol);evidence("content-odd-"+protocol,odd);
            for(String failure:List.of("draft","final","pending")){
                reset("normal",true);File interrupted=materials("content-"+failure+"-"+protocol,4,true);failCall=failure.equals("draft")?6:8;
                rejects(()->generateContent(interrupted,protocol,new ArrayList<>()),"content-group failure does not auto retry: "+failure+" "+protocol);JSONObject before=state(interrupted);
                check(before.getJSONArray("corrected").length()==4&&before.getJSONArray("drafts").length()==(failure.equals("draft")?1:2)&&!before.has("final"),"only the current content unit stays uncommitted");
                if(!failure.equals("draft"))check(before.getJSONArray("parts").length()==1&&before.getJSONArray("analysed").length()==2,"later image-group failure preserves exactly the first successful group and its images");
                if(failure.equals("pending")){before.getJSONObject("conversation").put("pending","final-1-restart-1");ClassroomStore.write(new File(interrupted,"run.json"),before);}
                JSONObject history=new JSONObject(before.getJSONObject("conversation").toString());int sent=requests.size();failCall=-1;result=generateContent(interrupted,protocol,new ArrayList<>());run=state(interrupted);
                check(result.getBoolean("complete")&&result.getString("run").equals(before.getString("run"))&&result.getJSONArray("images").length()==4&&run.getJSONArray("drafts").length()==2&&run.getJSONArray("parts").length()==2,"resumed content run preserves identity, all source groups and all four images");
                for(String stage:List.of("correct-0","correct-1","correct-2","correct-3","draft-0"))check(Collections.frequency(stages,stage)==1,"successful correction/content group never regenerates after "+failure+": "+stage);
                if(!failure.equals("draft"))check(Collections.frequency(stages,"final-0")==1&&messages(requests.get(sent)).getJSONObject(messages(requests.get(sent)).length()-1).getJSONArray("content").length()==1,"image retry retains successful group and its original image positions without reupload");
                check(run.getJSONArray("corrected").toString().equals(before.getJSONArray("corrected").toString())&&run.getJSONArray("drafts").getJSONObject(0).toString().equals(before.getJSONArray("drafts").getJSONObject(0).toString()),"successful correction and first draft remain byte-for-byte exact");
                revalidationHistory(history,run.getJSONObject("conversation"),true);batchHistory(interrupted,protocol);evidence("content-resume-"+failure+"-"+protocol,interrupted);
            }
            reset("normal",false);File cached=materials("content-correction-cache-"+protocol,4,true);failCall=5;rejects(()->generateContent(cached,protocol,new ArrayList<>()),"stop after four successful corrections before extraction");JSONObject prior=state(cached);ClassroomCorrectionCache.capture(cached);Files.delete(new File(cached,"run.json").toPath());
            reset("normal",false);result=generateContent(cached,protocol,new ArrayList<>());run=state(cached);
            check(stages.equals(List.of("draft-0","draft-1","edit-plan","edit-note-0","edit-affairs"))&&run.getJSONArray("corrected").toString().equals(prior.getJSONArray("corrected").toString())&&run.getJSONArray("blocks").toString().equals(prior.getJSONArray("blocks").toString())&&run.getString("correction_reused_from").equals(prior.getString("run")),"new content grouping restores the unchanged four-block correction cache without a correction request: "+protocol);batchHistory(cached,protocol);evidence("content-cache-"+protocol,cached);
            reset("normal",false);File old=previousContentMaterials("content-legacy-"+protocol,protocol);JSONObject oldBefore=state(old);result=generateWithProtocol(old,protocol);run=state(old);
            check(stages.equals(List.of("correct-0","correct-1","draft-0","draft-1","edit-plan","edit-note-0"))&&!run.has("content_batch_size")&&!run.has("editor_batch_chars")&&run.getJSONArray("drafts").length()==2&&result.getString("run").equals(oldBefore.getString("run")),"persisted old run without the new snapshots still extracts each original block separately: "+protocol);batchHistory(old,protocol);evidence("content-legacy-one-block-"+protocol,old);
            reset("normal",false);File nine=editorialFixture(protocol,new int[]{6000,6000,6000,6000,6000,6000,6000,6000,6000},false,true);JSONObject nineRun=state(nine);nineRun.put("editor_batch_size",3).put("editor_batch_chars",18000);ClassroomStore.write(new File(nine,"run.json"),nineRun);result=generateWithProtocol(nine,protocol);
            check(stages.equals(List.of("edit-note-0","edit-note-3","edit-note-6"))&&result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").length()==9&&state(nine).getInt("editor_section_done")==9,"nine original themes at exactly 18000 UTF16 per three make three HTTP requests: "+protocol);batchHistory(nine,protocol);evidence("editorial-nine-three-"+protocol,nine);
            reset("normal",false);File bounded=editorialFixture(protocol,new int[]{6000,6000,6000,6001,6000,6000,18001,1},false,true);JSONObject boundRun=state(bounded);boundRun.put("editor_batch_size",3).put("editor_batch_chars",18000);ClassroomStore.write(new File(bounded,"run.json"),boundRun);generateWithProtocol(bounded,protocol);
            check(stages.equals(List.of("edit-note-0","edit-note-3","edit-note-5","edit-note-6","edit-note-7"))&&state(bounded).getInt("editor_section_done")==8,"18001 splits a three-theme group while a single oversized theme remains independent: "+protocol);batchHistory(bounded,protocol);
            reset("normal",false);File oldBound=editorialFixture(protocol,new int[]{6000,6001,6000},false,true);generateWithProtocol(oldBound,protocol);
            check(stages.equals(List.of("edit-note-0","edit-note-1","edit-note-2"))&&!state(oldBound).has("editor_batch_chars"),"existing two-theme snapshots retain the absent-field 12000 UTF16 limit: "+protocol);batchHistory(oldBound,protocol);
        }
    }
    private static void contentRetryBoundary()throws Exception{
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES))for(String topology:List.of("group-first","group-second","group-tail","legacy-first","legacy-second"))for(boolean repair:new boolean[]{false,true}){
            boolean legacy=topology.startsWith("legacy"),first=topology.endsWith("first");int index=first?0:1,blocks=topology.equals("group-tail")?3:4;String target="final-"+index;
            reset((repair?"boundary-repair-":"boundary-semantic-")+target,true);
            File dir=legacy?previousContentMaterials("boundary-"+topology+"-"+repair+"-"+protocol,protocol):materials("boundary-"+topology+"-"+repair+"-"+protocol,blocks,true);
            JSONObject before=null,result;int requestIndex;
            if(repair){
                result=legacy?generateWithProtocol(dir,protocol):generateContent(dir,protocol,new ArrayList<>());requestIndex=stages.indexOf(target+"-repair");
                check(Collections.frequency(stages,target+"-repair")==1&&stages.stream().noneMatch(s->s.contains("user-retry")),"grouped final has exactly one structural repair and no semantic retry: "+topology+" "+protocol);
            }else{
                rejects(()->{if(legacy)generateWithProtocol(dir,protocol);else generateContent(dir,protocol,new ArrayList<>());},"invalid source leaves current image group uncommitted: "+topology+" "+protocol);
                before=state(dir);requestIndex=requests.size();check(before.getJSONArray("parts").length()==index&&!before.has("final"),"semantic failure cannot commit the current grouped final");
                mode="normal";result=legacy?generateWithProtocol(dir,protocol):generateContent(dir,protocol,new ArrayList<>());
                check(stages.get(requestIndex).equals(target+"-user-retry")&&Collections.frequency(stages,target)==1&&stages.stream().noneMatch(s->s.contains("repair")),"manual semantic continuation appends only the failed final's retry");
            }
            JSONObject run=state(dir);JSONArray expected=ClassroomEnhancedSummary.contentGroups(run.getJSONArray("blocks"),legacy?1:2).getJSONArray(index);
            String expectedIds=legacy?(first?"[0]":"[1]"):first?"[0,1]":topology.equals("group-tail")?"[2]":"[2,3]";
            check(sourceIds(expected).toString().equals(expectedIds),"fixture distinguishes original blocks from actual final group source ids: "+topology);
            check(requestIndex>=0,"actual HTTP request contains the repair or manual retry");String physical=prompt(requests.get(requestIndex));
            refinementBoundary(physical,expected,1,(repair?"repair":"manual retry")+" "+topology+" "+protocol);
            check(physical.contains("当前核图块的主体来源id："+expectedIds)&&messages(requests.get(requestIndex)).getJSONObject(messages(requests.get(requestIndex)).length()-1).getJSONArray("content").length()==1,"last appended text-only request identifies the entire true group and reuses original images");
            check(result.getBoolean("complete")&&result.getJSONArray("images").length()==(legacy?2:blocks),"repair or semantic recovery preserves every selected image and finishes one document");
            if(before!=null){check(run.getJSONArray("corrected").toString().equals(before.getJSONArray("corrected").toString())&&run.getJSONArray("drafts").toString().equals(before.getJSONArray("drafts").toString()),"boundary repair does not redo committed correction or extraction");for(int i=0;i<index;i++)check(run.getJSONArray("parts").getJSONObject(i).toString().equals(before.getJSONArray("parts").getJSONObject(i).toString()),"earlier successful image group remains exact");}
            if(legacy)check(!run.has("content_batch_size"),"legacy absent-field grouping stays untouched");
            batchHistory(dir,protocol);evidence("content-boundary-"+topology+"-"+(repair?"repair":"manual")+"-"+protocol,dir);
        }
    }
    private static String coverageCorrected(int index,boolean headings){
        if(!headings)return "无标题原段 "+index+"\r\n逐条教学内容😀  \r\n```python\n# CODE_ONLY_COMMENT_"+index+"\n```\n末尾原话 "+index+"\n";
        return switch(index){
            case 0->"# 组一首题\r\n  逐条原文😀  \r\n## 重复主题\r\n过程 A \r\n````python\n# CODE_BACKTICK_COMMENT\n```\n## CODE_SHORT_CLOSE\n~~~~\n# CODE_WRONG_TYPE\n````still-code\n# CODE_NONBLANK_CLOSE";
            case 1->"## CODE_CROSS_PART\n````` \t\n## 重复主题\n内容 B\n### 组一末题\n原段尾部  \n~~~~~bash\n# CODE_CROSS_GROUP_OPEN";
            case 2->"# CODE_FROM_PREVIOUS_GROUP\n~~~~~~ \t\n# 组二首题\n原文 C\n## 仅组二主题\n内容 D\n~~~~bash\n# CODE_TILDE_COMMENT\n````\n## CODE_WRONG_TYPE";
            default->"### CODE_TILDE_CROSS_PART\n~~~\n# CODE_SHORT_CLOSE\n~~~~still-code\n# CODE_NONBLANK_CLOSE\n~~~~~ \t\n  ## 仅组二主题  \r\n内容 E\r\n###### 组二末题\r\n原尾😀";
        };
    }
    private static void contentCoverage()throws Exception{
        String transcriptMarker="本次要提取的更正文稿（历史原消息保持不变，此处仅追加定位）：\n",rawMarker="\n事实核对及安排原话定位用原始字幕（更正文稿与其冲突时核对原话；evidence逐字摘录并列全覆盖的sources）：\n",outlineMarker="本组更正文稿已有标题，仅作覆盖提醒，不是成品目录：";
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES))for(String scenario:List.of("new-headings","new-plain","old-resume")){
            boolean legacy=scenario.equals("old-resume"),headings=!scenario.equals("new-plain");reset(headings?"coverage-headings":"coverage-plain",true);
            File dir=legacy?previousContentMaterials("coverage-"+scenario+"-"+protocol,protocol):materials("coverage-"+scenario+"-"+protocol,4,true);JSONObject frozen=legacy?state(dir):null,before=null,result;
            if(legacy){
                failCall=4;rejects(()->generateWithProtocol(dir,protocol),"old unfinished single-block extraction can stop after its first successful draft");before=state(dir);
                check(before.getJSONArray("drafts").length()==1&&before.getJSONArray("corrected").length()==2,"old interrupted fixture has a real successful draft and committed correction prefix");failCall=-1;
            }
            result=legacy?generateWithProtocol(dir,protocol):generateContent(dir,protocol,new ArrayList<>());JSONObject run=state(dir);JSONArray corrected=run.getJSONArray("corrected");int size=legacy?1:2,draftRequests=0;
            for(int n=0;n<corrected.length();n++)check(corrected.getString(n).equals(coverageCorrected(n,headings)),"stored successful correction keeps CRLF, trailing spaces, Unicode and original wording unchanged "+n);
            for(int r=0;r<requests.size();r++){
                String stage=stages.get(r),physical=prompt(requests.get(r)),logical=referencedPrompt(requests.get(r),physical);
                if(!stage.startsWith("correct-"))check(physical.contains("结尾须闭合全部嵌套数组、对象及最外层对象"),"JSON generation carries general balanced-envelope guidance without local completion: "+stage);
                if(stage.equals("edit-plan")){
                    String marker="已完成笔记索引：\n";JSONArray entries=new JSONArray(logical.substring(logical.indexOf(marker)+marker.length()));
                    for(int e=0;e<entries.length();e++){
                        JSONObject entry=entries.getJSONObject(e);String[] ref=entry.getString("ref").split(":");
                        JSONObject original=run.getJSONArray("parts").getJSONObject(Integer.parseInt(ref[0])).getJSONArray(ref.length==2?"notes":ref[1]).getJSONObject(Integer.parseInt(ref[ref.length-1]));
                        check(legacy?entry.getString("text").equals(original.getString("text")):entry.getString("origin").equals("original_subtitles")&&!entry.has("text")&&entry.getJSONArray("sources").similar(original.getJSONArray("sources"))&&logical.contains("\n编排原始字幕集合：\n"),"editorial grouping retains complete legacy prose or new metadata plus shared original evidence, never titles alone: "+protocol+" "+scenario);
                    }
                }
                if(stage.startsWith("final-"))check(logical.contains("核图只补充或纠正证据")&&logical.contains("仍有原话依据的行动完整保留"),"visual refinement preserves text-supported tasks without adding validation or calls: "+protocol);
                if(stage.startsWith("edit-note-"))check(logical.contains("不能把候选值计算当成存在性证明")&&logical.contains("正文内的小节使用简短加粗引导")&&logical.contains("不再插入与外层主题同级的Markdown标题"),"editorial request separates invalid reasoning and keeps nested hierarchy: "+protocol);
                if(stage.startsWith("draft-")||stage.startsWith("final-")){
                    check(logical.contains("仅回顾已结束或仅属往届、其他班级且未要求本班当前执行的任务不提取")&&logical.contains("不能因出现‘布置作业’等词就创建本班当前行动"),"actual extraction/refinement request excludes finished or irrelevant other-class task stories: "+stage);
                    check(logical.contains("本班仍有效的既有任务，其截止提醒、补交或继续执行要求仍须保留，不要求重新布置才算行动"),"actual extraction/refinement request retains active existing tasks and deadline, makeup or continuation reminders: "+stage);
                }
                if(!stage.startsWith("draft-"))continue;draftRequests++;int group=Integer.parseInt(stage.replaceFirst("draft-([0-9]+).*","$1")),first=group*size,last=Math.min(corrected.length(),first+size);
                int begin=logical.indexOf(transcriptMarker)+transcriptMarker.length(),rawStart=logical.indexOf(rawMarker);String body=logical.substring(begin,rawStart);StringJoiner expected=new StringJoiner("\n\n");
                for(int n=first;n<last;n++)expected.add("【原始段 "+(n+1)+" 开始】\n"+coverageCorrected(n,headings)+"\n【原始段 "+(n+1)+" 结束】");
                check(body.equals(expected.toString()),"actual draft carries every current original part completely and verbatim within its explicit boundaries: "+scenario+" "+stage);
                for(int n=0;n<corrected.length();n++)check(body.contains("【原始段 "+(n+1)+" 开始】")==(n>=first&&n<last),"draft boundaries include only the current group's original parts "+n);
                int titleStart=logical.indexOf(outlineMarker)+outlineMarker.length();JSONArray outline=new JSONArray(logical.substring(titleStart,logical.indexOf("。输出前在本次处理内",titleStart)));
                JSONArray expectedOutline=!headings?new JSONArray():legacy?(group==0?new JSONArray().put("组一首题").put("重复主题"):new JSONArray().put("重复主题").put("组一末题"))
                        :group==0?new JSONArray().put("组一首题").put("重复主题").put("组一末题"):new JSONArray().put("组二首题").put("仅组二主题").put("组二末题");
                check(outline.toString().equals(expectedOutline.toString()),"soft reminder keeps current-group headings in first-to-last order, deduplicates repeats and excludes fenced code comments: "+scenario+" "+stage);
                if(!legacy)check(!outline.toString().contains("CODE_")&&body.contains("CODE_"),"actual heading reminder filters code inside backtick/tilde fences, including short or wrong-type/nonblank closes and cross-part state, without deleting code from the original transcript");
                for(String rule:List.of("本请求须完整整理下列整组的全部原始段，从头至尾扫描，不仅摘要第一段或开头","逐项保留独特概念、定理及证明、例题及必要计算、考核评分线索和不同对象的明确行动","不能以精简为由省去整项教学内容","仅作覆盖提醒，不是成品目录","不因某标题缺失而忽略无标题内容","标题可合并重排，但实质内容不能漏掉","不额外输出检查报告"))check(logical.contains(rule),"actual draft retains soft all-unit coverage, including headingless content: "+rule);
                JSONObject raw=new JSONObject(logical.substring(rawStart+rawMarker.length(),logical.indexOf("\n全课PPT可用目录",rawStart)));
                JSONArray current=ClassroomEnhancedSummary.contentGroups(run.getJSONArray("blocks"),size).getJSONArray(group);JSONObject expectedRaw=ClassroomSummary.uploadChunk(new JSONObject().put("segments",current).put("images",new JSONArray()));
                check(raw.similar(expectedRaw)&&logical.contains("更正文稿与其冲突时核对原话")&&!logical.contains("只作evidence逐字定位"),"raw subtitles are sent as independent teacher-fact and conflict evidence, not evidence-only placeholders");
                check(logical.contains("evidence，逐字摘录原始字幕")&&logical.contains("不拼接非连续片段")&&logical.contains("sources须列全引文覆盖的每条来源"),"broader raw-fact use retains the original verbatim assignment evidence boundaries");
            }
            check(result.getBoolean("complete")&&requests.size()==(legacy?9:11)&&draftRequests==(legacy?3:2)&&run.getJSONArray("drafts").length()==2&&run.getJSONArray("parts").length()==2,"coverage keeps draft groups and uses real original-context editorial budgets only for new runs: "+scenario);
            check(run.getJSONArray("drafts").getJSONObject(0).keySet().equals(Set.of("overview","notes","affairs","assignments","image_assessment","image_requests")),"coverage reminders do not add wire fields or checks to the existing draft schema");
            if(legacy){
                check(!run.has("content_batch_size")&&!run.has("editor_batch_chars")&&run.getInt("editor_batch_size")==2&&run.getString("run").equals(before.getString("run")),"old unfinished run keeps single-block content units and frozen editorial batching");
                check(run.getJSONArray("corrected").toString().equals(before.getJSONArray("corrected").toString())&&run.getJSONArray("drafts").getJSONObject(0).toString().equals(before.getJSONArray("drafts").getJSONObject(0).toString())&&run.getJSONObject("conversation").getJSONObject("options").toString().equals(frozen.getJSONObject("conversation").getJSONObject("options").toString()),"recovery never rewrites successful correction, first draft or original request options");
            }else check(run.getInt("content_batch_size")==2&&run.getInt("editor_batch_size")==3&&run.getInt("editor_batch_chars")==18000,"new coverage prompts keep the agreed two-block/three-topic defaults");
            batchHistory(dir,protocol);evidence("content-coverage-"+scenario+"-"+protocol,dir);
        }
    }
    private static JSONObject batchSchemaExample(String text)throws Exception{
        String marker="完整整稿对象：\n";int start=text.lastIndexOf(marker)+marker.length();
        return new JSONObject(text.substring(start,text.indexOf("\n按实际内容填写各集合",start)));
    }
    private static void fidelitySystemRules(String system){
        for(String rule:List.of("先完整准确地陈述适用条件、符号含义和结论","再自然接一段简短通俗的理解阐释","正文不使用‘标准表述’‘直观理解’这类写作标签","数学检查条件、符号与计算","程序检查执行逻辑与边界","人文观点交代论据与语境","解释、推导和示例与采用的完整条件一致","课程事务以教师实际说过的内容为准"))
            check(system.contains(rule),"actual system prompt carries a useful subject-appropriate teaching rule: "+rule);
        check(!system.contains("PRIVATE_RAW_WORDS_")&&!system.contains("PRIVATE_DERIVED_CLAIM_"),"generic system rules do not embed fixture lecture text or an answer");
    }
    private static void editorialRawFidelity()throws Exception{
        String marker="\n对应原始字幕（用于核对教师事实、指令方向及与更正文稿的冲突，不仅用于安排引文定位）：\n",endMarker="\n原字幕是教师事实依据";
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES))for(String contract:List.of("new-three","old-two","old-single")){
            reset("fidelity-raw",false);boolean fresh=contract.equals("new-three");
            File dir=fresh?materials("fidelity-"+contract+"-"+protocol,4,true):previousContentMaterials("fidelity-"+contract+"-"+protocol,protocol);
            JSONObject frozen=fresh?null:state(dir);
            if(contract.equals("old-single")){frozen.remove("editor_batch_size");ClassroomStore.write(new File(dir,"run.json"),frozen);}
            JSONObject materials=ClassroomStore.read(new File(dir,"materials.json"));JSONArray original=materials.getJSONArray("segments");
            for(int i=0;i<original.length();i++)original.getJSONObject(i).put("text","PRIVATE_RAW_WORDS_"+i+" 教师原话，区分事实与指令。\n"+original.getJSONObject(i).getString("text"));
            ClassroomStore.write(new File(dir,"materials.json"),materials);JSONObject result;
            try(RequestControl control=new RequestControl(20000)){
                AiSettings settings=new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol);
                result=expandedGenerate(dir,LESSON,settings,0,control,p->{},"保留题干条件及步骤分",true,3);
            }
            JSONObject run=state(dir);JSONArray full=ClassroomSummary.lessonInput(original,new JSONArray()).getJSONArray("segments"),sections=run.getJSONObject("editor_plan").getJSONArray("sections");int batch=fresh?3:contract.equals("old-two")?2:1,editorRequests=0;
            check(result.getBoolean("complete")&&run.getJSONArray("drafts").length()==2&&run.getJSONArray("parts").length()==2&&sections.length()==6,"raw grounding keeps the existing two content groups and six independent synthetic themes: "+contract+" "+protocol);
            check(run.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(0).getString("text").startsWith("PRIVATE_DERIVED_CLAIM_"),"fixture deliberately distinguishes extracted prose from raw teacher subtitles");
            for(int r=0;r<requests.size();r++)if(stages.get(r).startsWith("edit-note-")){
                editorRequests++;int start=Integer.parseInt(stages.get(r).substring("edit-note-".length()));Set<Integer> expectedIds=new LinkedHashSet<>();
                if(fresh){String physical=prompt(requests.get(r));JSONArray inputs=editorialInputs(physical);JSONObject shared=editorialShared(physical);check(!physical.contains("PRIVATE_DERIVED_CLAIM_")&&shared.getJSONArray("segments").similar(sharedRows(inputs,full))&&occurrences(physical,shared.toString())==1,"new source-first request carries one exact deduplicated original pool without derived prose");for(int n=0;n<inputs.length();n++)check(!inputs.getJSONObject(n).has("text")&&inputs.getJSONObject(n).getString("origin").equals("original_subtitles"),"new editorial inputs are only source/image reference locators");check(physical.contains("更正文稿与提取稿都是待核对的派生稿")&&physical.contains("不因多份派生稿重复同一解释就认定为真")&&physical.contains("删除无依据的事实分支"),"new shared-input instructions retain evidence conflict resolution");continue;}
                for(int s=start;s<Math.min(sections.length(),start+batch);s++){
                    JSONArray refs=sections.getJSONObject(s).getJSONArray("refs");for(int n=0;n<refs.length();n++){
                        String[] ref=refs.getString(n).split(":");JSONObject note=run.getJSONArray("parts").getJSONObject(Integer.parseInt(ref[0])).getJSONArray(ref[1]).getJSONObject(Integer.parseInt(ref[2]));
                        JSONArray sources=note.getJSONArray("sources");for(int i=0;i<sources.length();i++)expectedIds.add(sources.getInt(i));
                    }
                }
                JSONArray selected=new JSONArray();for(int i=0;i<full.length();i++)if(expectedIds.contains(full.getJSONObject(i).getInt("id")))selected.put(full.getJSONObject(i));
                JSONObject expected=ClassroomSummary.uploadChunk(new JSONObject().put("segments",selected).put("images",new JSONArray()));String physical=prompt(requests.get(r));int begin=physical.indexOf(marker);
                check(begin>=0&&physical.indexOf(marker,begin+marker.length())<0,"each actual editorial request appends one explicit raw-subtitle block");
                JSONObject raw=new JSONObject(physical.substring(begin+marker.length(),physical.indexOf(endMarker,begin)));
                check(raw.similar(expected),"actual editorial raw rows exactly match the union of this batch's original refs, with no unrelated source rows: "+contract+" "+start);
                check(raw.getJSONArray("segments").length()==expectedIds.size()&&raw.getJSONArray("images").length()==0&&!raw.toString().contains("PRIVATE_DERIVED_CLAIM_")&&!raw.toString().contains("完整题干、改卷细节"),"raw grounding contains original teacher text rather than either extracted or corrected prose");
                for(int i=0;i<raw.getJSONArray("segments").length();i++){JSONObject row=raw.getJSONArray("segments").getJSONObject(i);check(row.getString("text").equals(original.getJSONObject(row.getInt("id")).getString("text")),"every appended raw subtitle retains its exact original wording "+row.getInt("id"));}
                check(physical.contains("更正文稿与提取稿都是待核对的派生稿")&&physical.contains("不因多份派生稿重复同一解释就认定为真")&&physical.contains("删除无依据的事实分支"),"actual editor instructions ground conflicting derived claims in teacher evidence instead of repetitions");
            }
            check(editorRequests==(6/batch)&&requests.size()==(fresh?10:5+6/batch)&&stages.stream().allMatch(s->s.matches("(?:correct|draft|edit-note)-[0-9]+|edit-plan")||fresh&&s.equals("edit-affairs"))&&Collections.frequency(stages,"edit-affairs")== (fresh?1:0),"new runs budget deduplicated shared original context while legacy runs preserve derived-input batches and stage types: "+contract);
            String system=messages(requests.get(0)).getJSONObject(0).getString("content");
            if(fresh){fidelitySystemRules(system);check(run.getInt("content_batch_size")==2&&run.getInt("editor_batch_size")==3&&run.getInt("editor_batch_chars")==18000&&run.getInt("note_strength")==3,"new fidelity prompts retain the two-block, three-theme, 18000-UTF16 snapshots");}
            else{
                JSONObject history=run.getJSONObject("conversation"),prior=frozen.getJSONObject("conversation");
                check(system.equals(prior.getJSONArray("messages").getJSONObject(0).getString("content"))&&history.getJSONObject("options").toString().equals(prior.getJSONObject("options").toString())&&run.getInt("note_strength")==1,"new preferred strength never rewrites an unfinished run's frozen system, style or options");
                check(!run.has("content_batch_size")&&!run.has("editor_batch_chars")&&(contract.equals("old-single")?!run.has("editor_batch_size"):run.getInt("editor_batch_size")==2),"old absent content/char snapshots and singleton or two-topic batching remain unchanged");
            }
            batchHistory(dir,protocol);evidence("editorial-raw-fidelity-"+contract+"-"+protocol,dir);
        }
    }
    private static void editorialBatchSchema()throws Exception{
        for(int schema:new int[]{0,1}){
            JSONObject example=batchSchemaExample(ClassroomSummary.topicBatchSchema(schema));
            check(example.keySet().equals(schema==0?Set.of("overview","notes","assignments"):Set.of("overview","notes","affairs","assignments")),"editor batch example contains only its own wire fields: "+schema);
            check(example.getString("overview").isEmpty()&&example.getJSONArray("assignments").length()==0,"editor batch example never suggests an overview or new actions: "+schema);
        }
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES)){
            reset("normal",false);File dir=editorialFixture(protocol,new int[]{20,30},true,true);JSONObject result=generateWithProtocol(dir,protocol);String sent=prompt(requests.get(0));JSONObject example=batchSchemaExample(sent);
            check(requests.size()==1&&result.getBoolean("complete")&&example.getJSONArray("assignments").length()==0&&example.getString("overview").isEmpty(),"actual two-topic HTTP prompt ends its complete schema with empty actions and overview: "+protocol);
            check(example.getJSONArray("notes").length()==1&&example.getJSONArray("affairs").length()==1&&example.length()==4,"actual batch example demonstrates separate knowledge and affair arrays without extra fields: "+protocol);
            check(!sent.contains("image_assessment")&&!sent.contains("image_requests")&&!sent.contains("image_reviews")&&!sent.contains("\"evidence\""),"actual editorial prompt has no draft, image-review or nonempty-action example fields: "+protocol);
            evidence("editorial-batch-schema-"+protocol,dir);
        }
        editorialRawFidelity();
    }
    private static void editorialBatches()throws Exception{
        rejects(()->ClassroomEnhancedSummary.validateEditorial(topicResult(0,0),1),"empty batch cannot silently skip all themes");
        ClassroomEnhancedSummary.validateEditorial(topicResult(3,2),1);check(true,"three input themes may separate into three knowledge and two affairs without a paid rejection");
        JSONObject actions=topicResult(1,1);actions.getJSONArray("assignments").put(new JSONObject());
        rejects(()->ClassroomEnhancedSummary.validateEditorial(actions,1),"batch cannot insert new action items");
        ClassroomEnhancedSummary.validateEditorial(topicResult(1,1),1);check(true,"mixed batch retains separate knowledge and affairs collections");
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES)){
            reset("normal",false);File two=editorialFixture(protocol,new int[]{20,30},false,true);JSONObject before=state(two),result=generateWithProtocol(two,protocol),after=state(two);
            check(stages.equals(List.of("edit-note-0"))&&requests.size()==1,"two topics now make one actual HTTP request: "+protocol);
            check(result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").length()==2&&after.getInt("editor_section_done")==2,"one batch commits both original themes: "+protocol);
            for(String field:List.of("corrected","drafts","parts","editor_plan"))check(before.get(field).toString().equals(after.get(field).toString()),"batch keeps paid source work and original plan exact: "+field);
            batchHistory(two,protocol);evidence("editorial-two-to-one-"+protocol,two);
            reset("normal",false);File six=editorialFixture(protocol,new int[]{20,30,40,50,60,70},true,true);result=generateWithProtocol(six,protocol);JSONObject document=result.getJSONArray("parts").getJSONObject(0);
            check(stages.equals(List.of("edit-note-0","edit-note-2","edit-note-4"))&&requests.size()==3,"six topics make three actual HTTP requests: "+protocol);
            check(document.getJSONArray("notes").length()==3&&document.getJSONArray("affairs").length()==3&&document.getJSONArray("assignments").length()==0,"mixed batches never flatten affairs into knowledge: "+protocol);
            check(state(six).getInt("editor_section_done")==6&&state(six).getJSONArray("editor_notes").length()==6,"cursor counts original topics independently of output collection: "+protocol);
            for(JSONObject request:requests)check(prompt(request).contains("保留各主题的独立标题")&&prompt(request).contains("禁止将事务混入知识")&&prompt(request).contains("\"collection\":\"notes\"")&&prompt(request).contains("\"collection\":\"affairs\""),"actual batch prompt supplies both categories and independent titles");
            batchHistory(six,protocol);evidence("editorial-six-to-three-"+protocol,six);
            reset("plan-correct-duplicate",false);File corrected=materials("plan-correct-cross-category-"+protocol,false);result=generateWithProtocol(corrected,protocol);document=result.getJSONArray("parts").getJSONObject(0);after=state(corrected);
            check(stages.equals(List.of("correct-0","draft-0","edit-plan","edit-note-0","edit-affairs"))&&requests.size()==5,"cross-category duplicate planning is followed by one final affair integration: "+protocol);
            JSONObject mergedSection=after.getJSONObject("editor_plan").getJSONArray("sections").getJSONObject(0);
            check(after.getJSONObject("editor_plan").getJSONArray("sections").length()==1&&mergedSection.getString("collection").equals("affairs")&&mergedSection.getJSONArray("refs").toString().contains("0:notes:0")&&mergedSection.getJSONArray("refs").toString().contains("0:affairs:0"),"plan keeps true mixed original refs while grouping their same affair once");
            check(document.getJSONArray("notes").length()==0&&document.getJSONArray("affairs").length()==1&&document.getString("overview").isEmpty()&&document.getJSONArray("affairs").getJSONObject(0).getString("text").contains("第一问须证明")&&document.getJSONArray("affairs").getJSONObject(0).getString("text").contains("第二问须给出反例"),"corrected affair has one final section, no invented knowledge overview, and both unique facts");batchHistory(corrected,protocol);evidence("editorial-cross-category-plan-"+protocol,corrected);
            reset("normal",false);File boundary=editorialFixture(protocol,new int[]{6000,6000,6001,6000,12001,1},false,true);result=generateWithProtocol(boundary,protocol);
            check(stages.equals(List.of("edit-note-0","edit-note-2","edit-note-3","edit-note-4","edit-note-5")),"exact 12000 joins; 12001 splits; oversized single theme remains independent: "+protocol);
            check(state(boundary).getInt("editor_section_done")==6&&result.getBoolean("complete"),"length-bound singleton and paired batches cover every original theme");batchHistory(boundary,protocol);
            reset("normal",false);File unicode=editorialFixture(protocol,new int[]{1,6000},false,true);JSONObject unicodeRun=state(unicode);unicodeRun.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(0).put("text","😀".repeat(3000)+"甲");ClassroomStore.write(new File(unicode,"run.json"),unicodeRun);
            generateWithProtocol(unicode,protocol);check(stages.equals(List.of("edit-note-0","edit-note-1")),"batch length is UTF16 rather than Unicode-codepoint count: "+protocol);
            for(String failure:List.of("network","pending","semantic")){
                reset("batch-merge",false);File interrupted=editorialFixture(protocol,new int[]{20,30,40,50,60,70},false,true);JSONObject initial=state(interrupted);failCall=2;
                rejects(()->generateWithProtocol(interrupted,protocol),"later batch interruption stops without automatic retries: "+failure+" "+protocol);
                JSONObject checkpoint=state(interrupted);check(checkpoint.getInt("editor_section_done")==2&&checkpoint.getJSONArray("editor_notes").length()==1&&!checkpoint.has("final"),"merged first batch commits one note but advances two original sections");
                check(requests.size()==2&&stages.equals(List.of("edit-note-0","edit-note-2")),"failure consumes exactly one request for the current batch");
                String first=checkpoint.getJSONArray("editor_notes").getJSONObject(0).toString(),options=checkpoint.getJSONObject("conversation").getJSONObject("options").toString();
                if(failure.equals("pending")){
                    checkpoint.getJSONObject("conversation").put("pending","edit-note-2-restart-1");ClassroomStore.write(new File(interrupted,"run.json"),checkpoint);
                }else if(failure.equals("semantic")){
                    failCall=-1;mode="bad-editor-source";rejects(()->generateWithProtocol(interrupted,protocol),"current two-theme batch rejects an invented source atomically");
                    JSONObject invalid=state(interrupted);check(invalid.getInt("editor_section_done")==2&&invalid.getJSONArray("editor_notes").length()==1,"semantic failure advances neither cursor nor notes");
                }
                JSONObject history=new JSONObject(state(interrupted).getJSONObject("conversation").toString());failCall=-1;mode="normal";int sent=requests.size();result=generateWithProtocol(interrupted,protocol);after=state(interrupted);
                check(result.getBoolean("complete")&&requests.size()==sent+2&&Collections.frequency(stages,"edit-note-0")==1&&stages.get(sent+1).equals("edit-note-4"),"resume retries only current batch then the remaining batch: "+failure);
                check(after.getInt("editor_section_done")==6&&after.getJSONArray("editor_notes").length()==5&&after.getJSONArray("editor_notes").getJSONObject(0).toString().equals(first),"merged successful notes are not overwritten or mistaken for section count");
                check(after.getJSONObject("editor_plan").toString().equals(initial.getJSONObject("editor_plan").toString())&&after.getJSONArray("corrected").toString().equals(initial.getJSONArray("corrected").toString())&&after.getJSONObject("conversation").getJSONObject("options").toString().equals(options),"resume preserves original plan, corrected transcript and frozen options");
                revalidationHistory(history,after.getJSONObject("conversation"),true);batchHistory(interrupted,protocol);evidence("editorial-batch-"+failure+"-"+protocol,interrupted);
            }
            reset("normal",false);File old=editorialFixture(protocol,new int[]{20,30,40,50,60,70},false,false);JSONObject oldRun=state(old);JSONArray successful=new JSONArray();
            for(int i=0;i<5;i++)successful.put(new JSONObject(oldRun.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(i).toString()).put("collection","notes"));
            oldRun.put("editor_notes",successful);ClassroomStore.write(new File(old,"run.json"),oldRun);result=generateWithProtocol(old,protocol);after=state(old);
            check(stages.equals(List.of("edit-note-5"))&&requests.size()==1&&!after.has("editor_batch_size")&&after.getInt("editor_section_done")==6,"unfinished six-topic legacy without batch field resumes only old final topic: "+protocol);
            for(int i=0;i<5;i++)check(after.getJSONArray("editor_notes").getJSONObject(i).toString().equals(successful.getJSONObject(i).toString()),"legacy successful topic remains exact "+i);
            check(prompt(requests.get(0)).contains("不按输入条数限定输出条数")&&!prompt(requests.get(0)).contains("本组主题清单"),"missing batch field preserves singleton input scheduling without imposing an output count");batchHistory(old,protocol);evidence("editorial-legacy-sixth-only-"+protocol,old);
            reset("normal",false);File singletons=editorialFixture(protocol,new int[]{20,30},false,false);generateWithProtocol(singletons,protocol);
            check(requests.size()==2&&stages.equals(List.of("edit-note-0","edit-note-1")),"legacy two-theme unfinished run keeps two requests while new run uses one: "+protocol);batchHistory(singletons,protocol);
            reset("normal",false);File figures=editorialFixture(protocol,new int[]{20,30},false,true);JSONObject figureRun=state(figures);JSONArray reviews=new JSONArray(),analysed=new JSONArray();
            for(int i=0;i<40;i++){String file="image-"+i+".jpg";analysed.put(new JSONObject().put("file",file));reviews.put(new JSONObject().put("file",file).put("included",i!=1).put("visible","PRIVATE_LONG_VISIBLE_"+"甲".repeat(500)).put("reason","PRIVATE_LONG_REASON_"+"乙".repeat(500)));}
            figureRun.put("analysed",analysed).put("figure_reviews",reviews);JSONObject source=figureRun.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(0);source.put("images",new JSONArray().put("image-0.jpg").put("image-1.jpg")).put("text","保留图片 ![准入](images/image-0.jpg) ![排除](images/image-1.jpg)");ClassroomStore.write(new File(figures,"run.json"),figureRun);
            result=generateWithProtocol(figures,protocol);String sent=prompt(requests.get(0));String marker="最终图片准入表（included=false不得引用）：";JSONArray admission=new JSONArray(sent.substring(sent.indexOf(marker)+marker.length(),sent.indexOf("只返回一个JSON",sent.indexOf(marker))));
            check(admission.length()==40&&admission.getJSONObject(1).getBoolean("included")==false&&admission.getJSONObject(0).length()==2,"editor receives file/included for all forty analysed figures");
            String evidenceMarker="本组引用图片的已有可见内容核对：";int evidenceStart=sent.indexOf(evidenceMarker)+evidenceMarker.length();
            JSONArray visualEvidence=new JSONArray(sent.substring(evidenceStart,sent.indexOf("。准入只表示",evidenceStart)));
            check(visualEvidence.length()==2&&visualEvidence.getJSONObject(0).getString("file").equals("image-0.jpg")&&visualEvidence.getJSONObject(1).getString("file").equals("image-1.jpg"),"editor receives visible evidence only for referenced topic images, including rejected ones");
            check(visualEvidence.getJSONObject(0).getString("visible").equals(reviews.getJSONObject(0).getString("visible"))&&!sent.contains("PRIVATE_LONG_REASON_")&&!sent.contains("\"reason\""),"topic visual evidence is copied verbatim without repeating other image reviews or reasons");
            JSONObject imageNote=result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(0);check(imageNote.getJSONArray("images").length()==1&&imageNote.getJSONArray("images").getString(0).equals("image-0.jpg")&&!imageNote.getString("text").contains("image-1.jpg"),"local final filter cannot unlock a rejected image in array or Markdown");
            check(state(figures).getJSONArray("figure_reviews").toString().equals(reviews.toString()),"compact admission leaves full original review evidence intact");batchHistory(figures,protocol);evidence("editorial-compact-figures-"+protocol,figures);
            String savedReviews=reviews.toString();
            JSONArray imageInputs=new JSONArray().put(new JSONObject().put("images",new JSONArray().put("images/image-0.jpg")).put("text",""))
                    .put(new JSONObject().put("text","![related](images/image-1.jpg)").put("images",new JSONArray()));
            JSONArray selectedEvidence=ClassroomEnhancedSummary.topicFigureEvidence(imageInputs,reviews);
            check(selectedEvidence.length()==2&&selectedEvidence.getJSONObject(0).getString("file").equals("image-0.jpg")&&selectedEvidence.getJSONObject(1).getString("file").equals("image-1.jpg"),"prefixed-array-only images normalize and deduplicate with plain and inline topic images");
            imageInputs.getJSONObject(0).getJSONArray("images").put("image-0.jpg");
            check(ClassroomEnhancedSummary.topicFigureEvidence(imageInputs,reviews).toString().equals(selectedEvidence.toString()),"adding a plain duplicate does not change prefix-only and inline selection");
            check(reviews.toString().equals(savedReviews),"topic evidence selection leaves all original forty reviews unchanged");
            for(String defect:List.of("bad-editor-source","bad-editor-image","bad-editor-assignment")){
                reset(defect,false);File invalid=editorialFixture(protocol,new int[]{20,30},true,true);rejects(()->generateWithProtocol(invalid,protocol),"batch retains all source, image and action guards: "+defect);
                JSONObject checkpoint=state(invalid);check(requests.size()==1&&checkpoint.getJSONArray("editor_notes").length()==0&&!checkpoint.has("editor_section_done")&&!checkpoint.has("final"),"whole mixed batch remains uncommitted after any invalid member");
                mode="normal";check(generateWithProtocol(invalid,protocol).getBoolean("complete")&&requests.size()==2&&stages.get(1).equals("edit-note-0-user-retry"),"manual semantic continuation retries only the rejected whole batch");batchHistory(invalid,protocol);
            }
        }
    }
    private static JSONObject section(String collection,String...refs)throws Exception{return new JSONObject().put("title",collection).put("collection",collection).put("refs",new JSONArray(Arrays.asList(refs)));}
    private static JSONObject legacyState(File dir,boolean enhanced)throws Exception{
        JSONObject run=new JSONObject().put("run","legacy-"+System.nanoTime()).put("config",ClassroomSummary.configKey(AI,0,"保留题干条件及步骤分",enhanced,1,0,0));
        try(RequestControl control=new RequestControl(20000)){
            new ClassroomConversation(dir,run,new com.donglan.chrona.ai.ChatCompletionClient(AI,control),"legacy-system-prefix");
        }
        run.getJSONObject("conversation").getJSONArray("messages")
                .put(new JSONObject().put("role","user").put("content",new JSONArray().put(new JSONObject().put("type","text").put("text","legacy-sent-source"))))
                .put(new JSONObject().put("role","assistant").put("content","legacy-sent-reply").put("reasoning_content","opaque-legacy-reasoning"));
        ClassroomStore.write(new File(dir,"run.json"),run);return run;
    }
    private static void legacyExtraAffairsValidation()throws Exception{
        JSONObject input=new JSONObject().put("segments",new JSONArray().put(new JSONObject().put("id",0).put("text","本课字幕"))).put("images",new JSONArray().put(new JSONObject().put("file","image-3.jpg")));
        JSONObject legacy=new JSONObject().put("overview","原有知识概览").put("notes",new JSONArray().put(content("原有笔记",0))).put("assignments",new JSONArray());
        ClassroomSummary.validate(legacy,input,false,0);check(true,"schema zero still accepts its original response without affairs");
        JSONObject extra=new JSONObject(legacy.toString()).put("affairs",new JSONArray().put(content("额外课程事务",0)));
        ClassroomSummary.validate(extra,input,false,0);check(true,"schema zero accepts present affairs with valid support");
        JSONObject invalidSource=new JSONObject(extra.toString());invalidSource.getJSONArray("affairs").getJSONObject(0).put("sources",new JSONArray().put(999));
        try{ClassroomSummary.validate(invalidSource,input,false,0);check(false,"schema zero must reject extra affairs whose source is outside the lesson");}
        catch(IOException invalid){check(invalid.getMessage().equals("AI 引用了课堂之外的字幕，请重试"),"present legacy affairs reaches the actual source guard");}
        JSONObject invalidImage=new JSONObject(extra.toString());invalidImage.getJSONArray("affairs").getJSONObject(0).put("images",new JSONArray().put("image-999.jpg"));rejects(()->ClassroomSummary.validate(invalidImage,input,false,0),"present legacy affairs uses image whitelist");
        JSONObject invalidStructure=new JSONObject(extra.toString());invalidStructure.getJSONArray("affairs").getJSONObject(0).remove("sources");rejects(()->ClassroomSummary.validate(invalidStructure,input,false,0),"present legacy affairs requires complete source structure");
        JSONObject invalidText=new JSONObject(extra.toString());invalidText.getJSONArray("affairs").getJSONObject(0).put("text","");rejects(()->ClassroomSummary.validate(invalidText,input,false,0),"present legacy affairs cannot contain empty prose");
        JSONObject wrongType=new JSONObject(extra.toString()).put("affairs",new JSONObject());rejects(()->ClassroomSummary.validate(wrongType,input,false,0),"present legacy affairs must be an array");
        JSONArray lecture=new JSONArray().put(new JSONObject().put("id",0).put("text","前文")).put(new JSONObject().put("id",1).put("text","当前块")).put(new JSONObject().put("id",2).put("text","后文"));
        JSONObject refinement=new JSONObject(extra.toString());refinement.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(1));
        JSONObject wholeInput=new JSONObject(input.toString()).put("segments",lecture);JSONArray block=new JSONArray().put(lecture.getJSONObject(1));
        ClassroomEnhancedSummary.validateRefinement(refinement,wholeInput,block,0);check(true,"present legacy affairs may reference a real earlier lesson source");
        refinement.getJSONArray("affairs").getJSONObject(0).put("sources",new JSONArray().put(1));ClassroomEnhancedSummary.validateRefinement(refinement,wholeInput,block,0);check(true,"valid extra affairs can anchor with the original legacy schema");
        refinement.getJSONArray("affairs").getJSONObject(0).put("sources",new JSONArray().put(1).put(2));ClassroomEnhancedSummary.validateRefinement(refinement,wholeInput,block,0);check(true,"present legacy affairs may cite already-provided later lesson sources");
    }
    private static void refinementBoundary(String actual,JSONArray block,int schema,String label)throws Exception{
        String boundary=ClassroomEnhancedSummary.refinementScope(block,schema);
        int diagnostic=actual.indexOf("\n本次安排原话诊断（");String scoped=diagnostic<0?actual:actual.substring(0,diagnostic);
        check(scoped.endsWith(boundary),label+": complete current-block boundary ends the recovery instructions before any specific evidence diagnostic");
        check(boundary.contains("当前核图块的主体来源id："+sourceIds(block)),label+": source ids come from the actual block");
        for(String rule:List.of("允许引用已提供的同课真实字幕","跨块引用只记审计提示，不影响本阶段提交","不得编造不存在的编号","跨块重复在后续整稿合并","安排原话按同课真实字幕核验","完整JSON对象","来源编号示例只表示结构"))
            check(boundary.contains(rule),label+": "+rule);
        check(!boundary.contains("sources至少含一条对应的本块id")&&!boundary.contains("assignments仍只引用本块"),label+": no hard per-block reference gate");
    }
    private static JSONArray sourceIds(JSONArray block)throws Exception{
        JSONArray ids=new JSONArray();for(int i=0;i<block.length();i++)ids.put(block.getJSONObject(i).getInt("id"));return ids;
    }
    private static void refinementDiagnostic(Task task,String expected,String label)throws Exception{
        try{task.run();check(false,label+": expected rejection");}
        catch(IOException invalid){check(invalid.getMessage().equals(expected),label+": precise collection, index and numeric sources");check(!invalid.getMessage().contains("PRIVATE_"),label+": no model body in diagnosis");}
    }
    private static void refinementScopeValidation()throws Exception{
        JSONArray all=new JSONArray();for(int id:new int[]{3,7,9,12})all.put(new JSONObject().put("id",id).put("text","来源 "+id));
        JSONArray block=new JSONArray().put(all.getJSONObject(1)).put(all.getJSONObject(2));JSONObject lecture=new JSONObject().put("segments",all).put("images",new JSONArray());
        refinementBoundary(ClassroomEnhancedSummary.refinementScope(block,1),block,1,"non-contiguous schema-one helper");
        refinementBoundary(ClassroomEnhancedSummary.refinementScope(block,0),block,0,"non-contiguous legacy helper");
        for(String collection:List.of("notes","affairs")){
            JSONObject value=new JSONObject().put("overview","").put("notes",new JSONArray()).put("affairs",new JSONArray()).put("assignments",new JSONArray());
            value.getJSONArray(collection).put(content("本块事实",7)).put(content("PRIVATE_OLD_BODY",3));
            ClassroomEnhancedSummary.validateRefinement(value,lecture,block);check(true,collection+": previous-only independent item is valid within the lesson");
            JSONObject note=value.getJSONArray(collection).getJSONObject(1);note.put("sources",new JSONArray().put(7).put(3)).put("text","本块事实与前文同条对照");
            ClassroomEnhancedSummary.validateRefinement(value,lecture,block);check(true,collection+": current source plus earlier comparison remains valid");
            note.put("sources",new JSONArray().put(9).put(12));
            ClassroomEnhancedSummary.validateRefinement(value,lecture,block);check(true,collection+": already-provided later lesson source remains valid");
            note.put("sources",new JSONArray().put(999));refinementDiagnostic(()->ClassroomEnhancedSummary.validateRefinement(value,lecture,block),"AI 引用了课堂之外的字幕，请重试",collection+": fabricated source id is still rejected");
            note.put("sources",new JSONArray().put(3)).put("images",new JSONArray().put("image-999.jpg"));rejects(()->ClassroomEnhancedSummary.validateRefinement(value,lecture,block),collection+": unseen image remains rejected");
        }
        JSONObject task=new JSONObject().put("overview","").put("notes",new JSONArray()).put("affairs",new JSONArray()).put("assignments",new JSONArray().put(new JSONObject().put("text","PRIVATE_TASK_BODY").put("sources",new JSONArray().put(3)).put("evidence","来源 3")));
        ClassroomEnhancedSummary.validateRefinement(task,lecture,block);check(true,"assignment may quote a genuine source in another lesson block");
        task.getJSONArray("assignments").getJSONObject(0).put("evidence","PRIVATE_INVENTED_TEACHER_REQUIREMENT");rejects(()->ClassroomEnhancedSummary.validateRefinement(task,lecture,block),"false teacher evidence remains rejected despite a valid source id");
        task.put("assignments",new JSONArray());ClassroomEnhancedSummary.validateRefinement(task,lecture,block);check(true,"empty current-block output remains legal");
    }
    private static void refinementActualSourceTopology()throws Exception{
        JSONArray all=new JSONArray().put(new JSONObject().put("id",887).put("text","PRIVATE_EARLIER_SOURCE")),block=new JSONArray();
        for(int id=896;id<=972;id++){JSONObject source=new JSONObject().put("id",id).put("text","PRIVATE_CURRENT_SOURCE_"+id);all.put(source);block.put(source);}
        JSONObject lecture=new JSONObject().put("segments",all).put("images",new JSONArray());
        JSONArray firstRefs=new JSONArray("[896,897,898,899,900,901,902,903,904,905,906,907,908,909,910,911,915]"),lastRefs=new JSONArray("[918,919,920,921,922,923,924,928,929,930,935,937,938,947,948]");
        JSONObject middle=content("PRIVATE_INSERTED_EARLIER_NOTE_BODY",887);
        JSONObject value=new JSONObject().put("overview","").put("notes",new JSONArray().put(content("PRIVATE_FIRST_CURRENT_NOTE_BODY",896).put("sources",firstRefs)).put(middle).put(content("PRIVATE_LAST_CURRENT_NOTE_BODY",918).put("sources",lastRefs))).put("affairs",new JSONArray()).put("assignments",new JSONArray());
        check(block.length()==77&&block.getJSONObject(0).getInt("id")==896&&block.getJSONObject(76).getInt("id")==972,"actual numeric topology retains the complete current block 896..972");
        ClassroomEnhancedSummary.validateRefinement(value,lecture,block);check(true,"actual middle notes[1] source 887 is accepted as a genuine earlier lesson source without a false current anchor");
        JSONArray comparison=new JSONArray().put(916).put(887);middle.put("sources",comparison).put("text","PRIVATE_CURRENT_FACT_WITH_EARLIER_COMPARISON");
        ClassroomEnhancedSummary.validateRefinement(value,lecture,block);check(true,"actual source ids allow a current fact with a real earlier comparison in the same note");
        ClassroomStore.write(new File(root,"actual-source-topology.json"),new JSONObject().put("synthetic",true).put("basis","source-scope.json numeric topology").put("current_block_sources",sourceIds(block)).put("accepted_field","notes[1].sources").put("accepted_sources",new JSONArray().put(887)).put("surrounding_valid_sources",new JSONArray().put(firstRefs).put(lastRefs)).put("accepted_comparison_sources",comparison));
    }
    private static void scopeWarning(File dir,JSONObject result,String stage,String field,int source)throws Exception{
        JSONArray warnings=state(dir).getJSONArray("source_scope_warnings");boolean found=false;
        for(int i=0;i<warnings.length();i++){JSONObject warning=warnings.getJSONObject(i);check(warning.keySet().equals(Set.of("stage","field","sources")),"scope audit contains only stage, field and numeric source ids");for(int n=0;n<warning.getJSONArray("sources").length();n++)check(warning.getJSONArray("sources").get(n) instanceof Number,"scope audit never persists model prose as a source");if(warning.getString("stage").equals(stage)&&warning.getString("field").equals(field)&&warning.getJSONArray("sources").toString().equals("["+source+"]"))found=true;}
        check(found,"soft warning identifies exact cross-block field: "+stage+" "+field);
        check(result.getJSONArray("source_scope_warnings").toString().equals(warnings.toString()),"final summary retains soft warnings from the same candidate");
        check(!warnings.toString().contains("PRIVATE_"),"scope audit excludes source and model bodies");
    }
    private static void refinementScopePipeline()throws Exception{
        editorial();
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES))for(boolean repair:new boolean[]{false,true}){
            reset(repair?"scope-repair-past":"scope-affairs-past",true);File dir=previousContentMaterials("scope-accepted-"+protocol+"-"+repair,protocol);JSONObject result=generateWithProtocol(dir,protocol);
            check(result.getBoolean("complete")&&requests.size()==(repair?10:9),"earlier-only affairs commit directly; batched editing needs no extra paid retry: "+protocol+" "+repair);
            scopeWarning(dir,result,"final-1","affairs[0].sources",0);
            check(Collections.frequency(stages,"final-1")==1&&Collections.frequency(stages,"final-1-repair")== (repair?1:0)&&stages.stream().noneMatch(s->s.contains("user-retry")),"soft cross-block audit never starts a semantic retry");
            if(protocol.equals(AiSettings.RESPONSES))nativeAppendOnly(dir);else appendOnly(dir);evidence("refinement-scope-accepted-"+protocol+"-"+repair,dir);
        }
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES))for(boolean repair:new boolean[]{false,true}){
            reset(repair?"scope-invalid-repair":"scope-invalid-evidence",true);File dir=previousContentMaterials("scope-"+protocol+"-"+repair,protocol);
            rejects(()->generateWithProtocol(dir,protocol),"fabricated teacher evidence stops without semantic auto retry");
            JSONObject before=state(dir);int sent=requests.size();String run=before.getString("run"),options=before.getJSONObject("conversation").getJSONObject("options").toString();
            check(sent==(repair?7:6)&&before.getInt("final-1_attempt")==1,"only initial final and optional single structural repair are sent");
            check(before.getJSONArray("corrected").length()==2&&before.getJSONArray("drafts").length()==2&&before.getJSONArray("parts").length()==1&&before.getJSONArray("analysed").length()==1&&!before.has("final"),"only failed second refinement is uncommitted");
            String error=before.getString("final-1_error");check(error.contains("原话")&&!error.contains("PRIVATE_"),"persisted failure identifies the evidence guard without response prose");
            JSONArray block=before.getJSONArray("blocks").getJSONArray(1);
            refinementBoundary(prompt(requests.get(5)),block,1,"second initial refinement "+protocol);
            if(repair){refinementBoundary(prompt(requests.get(6)),block,1,"second refinement repair "+protocol);check(messages(requests.get(6)).getJSONObject(messages(requests.get(6)).length()-1).getJSONArray("content").length()==1,"repair sends text only, retaining original image position");}
            if(repair){
                mode="bad-manual";rejects(()->generateWithProtocol(dir,protocol),"malformed manual final response cannot create a second automatic repair");
                check(requests.size()==sent+1&&state(dir).getInt("final-1_attempt")==2&&Collections.frequency(stages,"final-1-repair")==1,"spent final repair budget persists across a malformed manual attempt");
                refinementBoundary(prompt(requests.get(sent)),block,1,"malformed manual final "+protocol);sent++;
            }
            mode="normal";JSONObject result=generateWithProtocol(dir,protocol.equals(AiSettings.RESPONSES)?AiSettings.CHAT_COMPLETIONS:protocol),after=state(dir);
            check(result.getBoolean("complete")&&result.getString("run").equals(run),"manual continuation completes same run");
            check(stages.get(sent).equals("final-1-user-retry"),"manual continuation starts only failed second refinement");
            refinementBoundary(prompt(requests.get(sent)),block,1,"manual refinement "+protocol);
            check(messages(requests.get(sent)).getJSONObject(messages(requests.get(sent)).length()-1).getJSONArray("content").length()==1,"manual retry never repeats image attachments");
            check(after.getJSONObject("conversation").getJSONObject("options").toString().equals(options),"manual recovery leaves frozen options exactly unchanged");
            for(String field:List.of("corrected","drafts"))check(after.getJSONArray(field).toString().equals(before.getJSONArray(field).toString()),"manual recovery preserves all committed "+field);
            check(after.getJSONArray("parts").getJSONObject(0).toString().equals(before.getJSONArray("parts").getJSONObject(0).toString()),"manual recovery preserves earlier completed refinement");
            for(String stage:List.of("correct-0","correct-1","draft-0","draft-1","final-0","final-1"))check(Collections.frequency(stages,stage)==1,"never recomputes successful stages: "+stage);
            check(Collections.frequency(stages,"final-1-repair")== (repair?1:0),"one structural repair budget and no new semantic retry");
            if(protocol.equals(AiSettings.RESPONSES))nativeAppendOnly(dir);else appendOnly(dir);evidence("refinement-scope-"+protocol+"-"+repair,dir);
        }
        reset("scope-legacy",true);File legacy=materials("scope-legacy",true);JSONObject seeded=legacyState(legacy,true);
        seeded.put("config",ClassroomSummary.configKey(AI,2,"保留题干条件及步骤分",true,1,0,0));ClassroomStore.write(new File(legacy,"run.json"),seeded);
        JSONObject legacyResult=generate(legacy,true);
        JSONObject before=state(legacy);check(requests.size()==10&&before.getInt("affairs_schema")==0&&before.getString("run").equals(seeded.getString("run"))&&before.getString("config").equals(seeded.getString("config"))&&before.getString("config").contains("\n11\n"),"legacy keeps original run, pipeline-eleven config and schema through final repair");
        JSONArray block=before.getJSONArray("blocks").getJSONArray(1);refinementBoundary(prompt(requests.get(5)),block,0,"legacy initial final");refinementBoundary(prompt(requests.get(6)),block,0,"legacy final repair");
        check(legacyResult.getBoolean("complete"),"legacy earlier-only refinement succeeds after its one structural repair");scopeWarning(legacy,legacyResult,"final-1","notes[0].sources",0);
        for(JSONObject request:requests)check(!prompt(request).contains("affairs"),"legacy initial, repair and subsequent prompts never introduce affairs");
        check(Collections.frequency(stages,"final-1-repair")==1&&state(legacy).getInt("affairs_schema")==0,"legacy repair remains bounded and schema remains frozen");appendOnly(legacy);evidence("refinement-scope-legacy",legacy);
    }
    private static final String REVALIDATION_STAGE="edit-note-5";
    private static final String ASR_QUOTE="请把这条可郑的结论整理成文。",WRONG_QUOTE="请把这条可证的结论整理成文。",EVIDENCE_MARKER="\n本次安排原话诊断（错误引文已作废，仅供纠错，不能作为笔记内容）：";
    private static JSONArray evidenceOriginals()throws Exception{return new JSONArray().put(new JSONObject().put("id",217).put("text","请把这条可郑的结论")).put(new JSONObject().put("id",217).put("text","整理成文。")).put(new JSONObject().put("id",218).put("text","请继续核对记录。"));}
    private static JSONObject assignmentEvidenceValue(String scenario)throws Exception{
        boolean wrong=scenario.equals("evidence-bad")||scenario.equals("evidence-repair")||scenario.equals("evidence-length");
        JSONObject result=new JSONObject().put("overview","完整新稿").put("notes",new JSONArray().put(content("完整新主题",scenario.equals("evidence-source")?999:217).put("text",wrong?"PRIVATE_FAILED_NOTE_BODY":"COMPLETE_SUCCESS_NOTE")))
                .put("affairs",new JSONArray()).put("assignments",new JSONArray()
                        .put(new JSONObject().put("text","PRIVATE_ACTION_TEXT_完整整理结论").put("sources",new JSONArray().put(scenario.equals("evidence-unknown")?999:217)).put("evidence",wrong?WRONG_QUOTE:ASR_QUOTE))
                        .put(new JSONObject().put("text","核对记录").put("sources",new JSONArray().put(218)).put("evidence","请继续核对记录。")));
        if(scenario.equals("evidence-repair"))result.remove("overview");
        if(scenario.equals("evidence-editor-good"))result.put("overview","").put("assignments",new JSONArray());return result;
    }
    private static JSONObject evidenceGenerate(File dir,JSONObject run,String protocol,String stage)throws Exception{
        AiSettings settings=new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol);JSONArray originals=evidenceOriginals();
        JSONObject lecture=new JSONObject().put("segments",originals).put("images",new JSONArray().put(new JSONObject().put("file","image-0.jpg")));
        try(RequestControl control=new RequestControl(20000)){
            ClassroomConversation conversation=new ClassroomConversation(dir,run,new com.donglan.chrona.ai.ChatCompletionClient(settings,control),"frozen evidence synthetic system：成品严格分离");
            boolean editorial=stage.startsWith("edit-note-");
            String prompt=(editorial?"全课整稿主题 1/1：完整知识主题。":stage.startsWith("final")?"只核实并完善第 1 内容组笔记。":"现在只整理第 1 内容组。")+(editorial?ClassroomSummary.topicSchema(1,"notes"):ClassroomSummary.schema(true,1))+"原始字幕："+originals;
            return new ClassroomJsonStage(dir,run,conversation,control).generate(stage,prompt,stage.startsWith("final")?Collections.singletonList(new byte[]{1,2,3}):Collections.emptyList(),value->{ClassroomSummary.validateBlock(value,lecture,1);if(editorial)ClassroomEnhancedSummary.validateEditorial(value,1);else ClassroomSummary.validateAssignmentEvidence(value,originals);},"-user-retry-","用户授权继续当前板块。",null);
        }
    }
    private static void assignmentEvidenceRecovery()throws Exception{
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES))for(String scenario:List.of("draft","final","repair","malformed","source","unknown","length","no-blocks","no-output","pending","latest-non-evidence","latest-length")){
            String initial=scenario.equals("repair")?"evidence-repair":scenario.equals("malformed")?"evidence-malformed":scenario.equals("source")?"evidence-source":scenario.equals("unknown")?"evidence-unknown":scenario.equals("length")?"evidence-length":"evidence-bad";
            reset(initial,false);File dir=new File(root,"evidence-recovery-"+protocol+"-"+scenario+"-"+System.nanoTime());dir.mkdirs();JSONArray originals=evidenceOriginals();
            JSONObject run=new JSONObject().put("run","evidence-synthetic").put("content_batch_size",2).put("affairs_schema",1)
                    .put("blocks",new JSONArray().put(new JSONArray().put(originals.getJSONObject(0))).put(new JSONArray().put(originals.getJSONObject(1)).put(originals.getJSONObject(2))))
                    .put("corrected",new JSONArray().put("DEPRECATED_DERIVED_请把这条可证的结论整理成文。"));
            if(scenario.equals("no-blocks"))run.remove("blocks");String stage=scenario.equals("draft")?"draft-0":"final-0";JSONObject result;int recovery;
            if(scenario.equals("repair")||scenario.equals("malformed")){
                result=evidenceGenerate(dir,run,protocol,stage);recovery=1;
                check(requests.size()==2&&stages.get(1).equals(stage+"-repair"),"structural recovery keeps exactly its original one-repair HTTP budget: "+scenario+" "+protocol);
            }else{
                rejects(()->evidenceGenerate(dir,run,protocol,stage),"bad or incomplete assignment response waits for explicit continuation: "+scenario);
                check(requests.size()==1,"initial evidence failure never adds an automatic AI request");
                String error=run.optString(stage+"_error");check(!error.contains(WRONG_QUOTE)&&!error.contains(ASR_QUOTE)&&!error.contains("PRIVATE_FAILED_NOTE_BODY")&&!error.contains("本次安排原话诊断"),"stored error contains no diagnostic, raw quotation or failed note body");
                if(scenario.equals("no-output"))run.getJSONObject("conversation").getJSONObject("outputs").remove(stage);
                if(scenario.equals("pending"))run.getJSONObject("conversation").put("pending",stage+"-user-retry-1");
                if(scenario.startsWith("latest-")){
                    mode=scenario.equals("latest-length")?"evidence-length":"evidence-source";rejects(()->evidenceGenerate(dir,run,protocol,stage),"newer failed attempt supersedes an older invalid quote");
                }
                JSONObject before=new JSONObject(run.getJSONObject("conversation").toString());recovery=requests.size();mode="evidence-good";
                result=evidenceGenerate(dir,run,protocol,stage);
                check(requests.size()==recovery+1,"explicit continuation makes exactly one request without a repair loop");revalidationHistory(before,run.getJSONObject("conversation"),true);
            }
            String physical=prompt(requests.get(recovery));boolean diagnostic=Set.of("draft","final","repair").contains(scenario);
            check(physical.contains(EVIDENCE_MARKER)==diagnostic,"only the latest complete same-stage mismatched quote receives dynamic raw diagnostics: "+scenario+" "+protocol);
            if(diagnostic){
                int begin=physical.indexOf(EVIDENCE_MARKER)+EVIDENCE_MARKER.length();JSONArray rows=new JSONArray(physical.substring(begin,physical.indexOf("\n请从所指原始字幕",begin)));JSONObject row=rows.getJSONObject(0);
                check(rows.length()==1&&row.keySet().equals(Set.of("field","discarded_quote","original_sources"))&&row.getString("field").equals("assignments[0].evidence")&&row.getString("discarded_quote").equals(WRONG_QUOTE),"diagnostic identifies only the mismatched assignment and labels its exact old quote as discarded");
                check(row.getJSONArray("original_sources").toString().equals(new JSONArray().put(originals.getJSONObject(0)).put(originals.getJSONObject(1)).toString())&&row.getJSONArray("original_sources").getJSONObject(0).getString("text").contains("可郑"),"diagnostic uses ordered original block slices and the genuine ASR typo, never the corrected derivative or a valid other assignment");
                check(physical.contains("逐字摘录表达真实要求的最短有效原话")&&physical.contains("即使原始字幕含ASR错字，也不可在evidence中自动纠正")&&physical.contains("任务内容按原要求完整保留，从头重新生成本阶段完整JSON"),"recovery preserves the real action and requests a complete fresh JSON with literal original evidence");
                check(!physical.contains("PRIVATE_FAILED_NOTE_BODY")&&!physical.contains("PRIVATE_ACTION_TEXT_")&&!physical.contains("DEPRECATED_DERIVED_"),"diagnostic never copies failed notes, task paraphrases or derived transcript bodies into recovery instructions");
            }else check(!physical.contains("PRIVATE_PARTIAL_EVIDENCE_BODY")&&!physical.contains("PRIVATE_FAILED_NOTE_BODY"),"unrelated or truncated failures never inject their response fragments into a new request");
            check(result.getJSONArray("assignments").getJSONObject(0).getString("evidence").equals(ASR_QUOTE)&&result.getJSONArray("notes").getJSONObject(0).getString("text").equals("COMPLETE_SUCCESS_NOTE")&&!result.toString().contains("PRIVATE_FAILED_NOTE_BODY"),"successful fresh output uses the unchanged original quote without splicing a failed note");
            check(messages(requests.get(recovery)).getJSONObject(messages(requests.get(recovery)).length()-1).getJSONArray("content").length()==1,"recovery sends text only and never uploads the original image a second time");
            for(String key:run.keySet())if(key.endsWith("_error"))check(!run.getString(key).contains("本次安排原话诊断")&&!run.getString(key).contains("可郑"),"diagnostics remain confined to appended conversation user messages");
            batchHistory(dir,protocol);evidence("assignment-evidence-"+scenario+"-"+protocol,dir);
        }
    }
    private static void assignmentEvidenceStageScope()throws Exception{
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES))for(String stage:List.of("draft-0","final-0","edit-note-0")){
            reset("evidence-bad",false);File dir=new File(root,"evidence-stage-scope-"+protocol+"-"+stage+"-"+System.nanoTime());dir.mkdirs();JSONArray originals=evidenceOriginals();
            JSONObject run=new JSONObject().put("run","evidence-stage-scope").put("content_batch_size",2).put("affairs_schema",1).put("blocks",new JSONArray().put(new JSONArray().put(originals.getJSONObject(0))).put(new JSONArray().put(originals.getJSONObject(1)).put(originals.getJSONObject(2))));
            rejects(()->evidenceGenerate(dir,run,protocol,stage),"initial wrong quote or illegal editorial action requires explicit recovery: "+stage);
            check(requests.size()==1&&stages.get(0).equals(stage),"stage-scope fixture sends the actual intended HTTP stage exactly once");
            JSONObject before=new JSONObject(run.getJSONObject("conversation").toString());boolean editorial=stage.startsWith("edit-note-");
            if(editorial)check(prompt(requests.get(0)).contains("\"assignments\":[]")&&run.getString(stage+"_error").contains("具体安排保留在安排集合"),"editorial fixture explicitly requires empty actions and fails for returning an illegal action despite its mismatched quote");
            mode=editorial?"evidence-editor-good":"evidence-good";JSONObject result=evidenceGenerate(dir,run,protocol,stage);String recovery=prompt(requests.get(1));
            check(requests.size()==2&&stages.get(1).equals(stage+"-user-retry"),"stage-scoped diagnostic changes no retry budget or stage count");
            check(recovery.contains(EVIDENCE_MARKER)==!editorial,"only draft/final evidence recovery receives quote diagnostics, never the empty-actions editorial stage: "+stage+" "+protocol);
            if(editorial)check(result.getJSONArray("assignments").length()==0&&!recovery.contains("discarded_quote")&&!recovery.contains("任务内容按原要求完整保留")&&!recovery.contains(WRONG_QUOTE),"editorial recovery neither preserves an illegal task nor injects its failed quote");
            else check(recovery.contains("可郑")&&recovery.contains(WRONG_QUOTE)&&result.getJSONArray("assignments").getJSONObject(0).getString("evidence").equals(ASR_QUOTE),"permitted content-stage recovery still sends original ASR wording and succeeds with literal evidence");
            check(messages(requests.get(1)).getJSONObject(messages(requests.get(1)).length()-1).getJSONArray("content").length()==1,"stage-scope correction never retransmits an image");
            revalidationHistory(before,run.getJSONObject("conversation"),true);batchHistory(dir,protocol);evidence("assignment-evidence-stage-scope-"+stage+"-"+protocol,dir);
        }
    }
    private static String revalidationPrompt()throws Exception{
        return "全课整稿主题 6/6：合成主题。只返回完整JSON。\n本主题提取稿：\n"+new JSONArray().put(content("原计划知识主题",0));
    }
    private static JSONObject revalidationGenerate(File dir,JSONObject state,String protocol,ClassroomJsonStage.Validator validator)throws Exception{
        AiSettings settings=new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol);
        try(RequestControl control=new RequestControl(20000)){
            ClassroomConversation conversation=new ClassroomConversation(dir,state,new com.donglan.chrona.ai.ChatCompletionClient(settings,control),"unchanged-synthetic-system-prefix");
            return new ClassroomJsonStage(dir,state,conversation,control).generate(REVALIDATION_STAGE,revalidationPrompt(),Collections.emptyList(),validator,"-user-retry-","用户授权继续当前板块。",null);
        }
    }
    private static void revalidationHistory(JSONObject before,JSONObject after,boolean append)throws Exception{
        check(after.getJSONObject("options").toString().equals(before.getJSONObject("options").toString())&&after.getString("protocol").equals(before.getString("protocol")),"revalidation keeps frozen protocol and request options");
        for(String field:List.of("messages","turns")){JSONArray previous=before.getJSONArray(field),current=after.getJSONArray(field);check(append?current.length()>=previous.length():current.length()==previous.length(),"revalidation history is immutable or append-only: "+field);for(int i=0;i<previous.length();i++)check(previous.getJSONObject(i).toString().equals(current.getJSONObject(i).toString()),"revalidation preserves the exact historical "+field+" item "+i);}
        JSONObject outputs=before.getJSONObject("outputs");for(String key:outputs.keySet())check(outputs.getString(key).equals(after.getJSONObject("outputs").getString(key)),"revalidation preserves exact raw output: "+key);
        if(!append)check(after.toString().equals(before.toString()),"zero-request recovery leaves the entire conversation byte-for-byte unchanged");
    }
    private static List<String> revalidationScenarios(String protocol){
        List<String> cases=new ArrayList<>(List.of("complete","service-error","length","filter","latest-invalid","malformed","pending","unsafe-source","unsafe-image","unknown-running","unknown-transport"));
        if(protocol.equals(AiSettings.RESPONSES))cases.addAll(List.of("partial","sse-overloaded","sse-root-rate-limit","sse-unknown","sse-reasoning","native-empty-overloaded","native-empty-incomplete","native-empty-filter","native-empty-refusal","native-empty-details","native-empty-unknown"));else cases.add("nonstop");
        return cases;
    }
    private static void completedReplyRevalidation()throws Exception{
        JSONObject lecture=new JSONObject().put("segments",new JSONArray().put(new JSONObject().put("id",0).put("text","题干 5 kg，步骤分 2 分。"))).put("images",new JSONArray());
        ClassroomJsonStage.Validator valid=value->{ClassroomSummary.validateBlock(value,lecture,1);ClassroomEnhancedSummary.validateEditorial(value,1);};
        ClassroomJsonStage.Validator old=value->{valid.check(value);throw new IOException("旧本地分类校验拒绝完整主题");};
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES))for(String scenario:revalidationScenarios(protocol)){
            reset(scenario.equals("unsafe-source")?"bad-editor-source":scenario.equals("unsafe-image")?"bad-editor-image":"editor-notes-to-affairs",false);File dir=new File(root,"revalidation-"+protocol+"-"+scenario+"-"+System.nanoTime());dir.mkdirs();JSONObject run=new JSONObject().put("run","synthetic-revalidation").put("affairs_schema",1);
            rejects(()->revalidationGenerate(dir,run,protocol,old),"old local validator rejects a complete whole JSON reply: "+scenario);
            check(requests.size()==1&&run.getInt(REVALIDATION_STAGE+"_attempt")==1&&run.has(REVALIDATION_STAGE+"_error"),"old validation failure retains one received whole response without an automatic request");
            if(scenario.equals("service-error")||scenario.startsWith("unknown-")){
                failCall=2;rejects(()->revalidationGenerate(dir,run,protocol,old),"pure service error after the complete reply remains uncommitted");
                JSONObject history=run.getJSONObject("conversation"),turn=history.getJSONArray("turns").getJSONObject(1);
                if(scenario.equals("unknown-running"))turn.put("status","running");
                if(scenario.equals("unknown-transport"))turn.put("type","IOException");
            }
            else if(scenario.equals("pending"))run.getJSONObject("conversation").put("pending",REVALIDATION_STAGE+"-user-retry-1");
            else if(!scenario.equals("complete")&&!scenario.startsWith("unsafe-")){
                mode=scenario.equals("latest-invalid")?"bad-editor-source":"revalidate-"+scenario;
                rejects(()->revalidationGenerate(dir,run,protocol,old),"newer failed/invalid attempt blocks falling back to an older complete reply: "+scenario);
                if(scenario.equals("partial")){JSONObject history=run.getJSONObject("conversation"),turn=history.getJSONArray("turns").getJSONObject(1);check(turn.getInt("content_chars")>0&&!history.getJSONObject("outputs").has(turn.getString("stage")),"real native partial failure retains content evidence without a reusable output");}
                if(scenario.startsWith("sse-")||scenario.startsWith("native-empty-")){
                    JSONObject history=run.getJSONObject("conversation"),turn=history.getJSONArray("turns").getJSONObject(1);check(turn.getString("status").equals("failed")&&!history.getJSONObject("outputs").has(turn.getString("stage")),"real SSE/native terminal failure has evidence but no reusable response: "+scenario);
                    if(scenario.equals("sse-reasoning"))check(turn.optLong("content_chars")==0&&turn.getLong("reasoning_chars")>0,"reasoning-only failure cannot be treated as empty overload");
                    if(scenario.equals("sse-overloaded"))check(turn.getJSONObject("response").getJSONObject("event").getJSONObject("error").getString("code").equals("server_is_overloaded")&&turn.optLong("content_chars")==0&&turn.optLong("reasoning_chars")==0,"actual overloaded event retains its safe error code and proves zero partial content");
                }
            }
            JSONObject before=new JSONObject(run.getJSONObject("conversation").toString());int sent=requests.size();mode="normal";failCall=-1;
            JSONObject result=revalidationGenerate(dir,run,protocol,valid);boolean recovered=Set.of("complete","service-error","sse-overloaded","sse-root-rate-limit","native-empty-overloaded").contains(scenario);
            check(requests.size()==sent+(recovered?0:1),"only a previously complete valid reply is reused without another paid call: "+protocol+" "+scenario);
            check(!run.has(REVALIDATION_STAGE+"_error")||!recovered,"successful local revalidation clears its obsolete local error");
            check(recovered?run.getString(REVALIDATION_STAGE+"_revalidated_from").equals(REVALIDATION_STAGE):!run.has(REVALIDATION_STAGE+"_revalidated_from"),"revalidation marker records the actual reused response and never claims a fresh generation");
            if(recovered)check(run.getInt(REVALIDATION_STAGE+"_attempt")==1,"free local recovery does not consume a new retry generation");
            check(result.getJSONArray(recovered?"affairs":"notes").length()==1,"recovery returns exactly the valid whole response rather than joining or reverting content");
            revalidationHistory(before,run.getJSONObject("conversation"),!recovered);
            if(scenario.equals("malformed")){int repairs=0;JSONArray turns=run.getJSONObject("conversation").getJSONArray("turns");for(int i=0;i<turns.length();i++)if(turns.getJSONObject(i).getString("stage").equals(REVALIDATION_STAGE+"-repair"))repairs++;check(repairs==1,"one structural repair budget survives revalidation and manual whole-response regeneration");}
            ClassroomStore.write(new File(dir,"revalidation-result.json"),new JSONObject().put("synthetic",true).put("scenario",scenario).put("protocol",protocol).put("requests_before",sent).put("requests_after",requests.size()).put("result",result).put("run",run));
        }
    }
    private static JSONObject topicResult(int noteCount,int affairCount)throws Exception{
        JSONArray notes=new JSONArray(),affairs=new JSONArray();for(int i=0;i<noteCount;i++)notes.put(content("知识 "+i,0));for(int i=0;i<affairCount;i++)affairs.put(content("事务 "+i,0));
        return new JSONObject().put("overview","").put("notes",notes).put("affairs",affairs).put("assignments",new JSONArray());
    }
    private static void editorialClassificationContract()throws Exception{
        for(int[] counts:new int[][]{{2,0},{0,2},{1,1},{3,2}}){
            ClassroomEnhancedSummary.validateEditorial(topicResult(counts[0],counts[1]),1);
            check(true,"all split knowledge and affairs survive without an output-count cap: "+Arrays.toString(counts));
        }
        rejects(()->ClassroomEnhancedSummary.validateEditorial(topicResult(0,0),1),"empty result cannot silently discard all themes");
        JSONObject withAction=topicResult(1,0);withAction.getJSONArray("assignments").put(new JSONObject().put("text","不能丢弃的行动"));rejects(()->ClassroomEnhancedSummary.validateEditorial(withAction,1),"actions stay in the independent action collection");
        JSONObject legacy=topicResult(1,0);legacy.remove("affairs");ClassroomEnhancedSummary.validateEditorial(legacy,0);check(true,"legacy notes-only shape remains usable");
        ClassroomEnhancedSummary.validateEditorial(topicResult(0,1),0);check(true,"complete legacy extra affairs may be retained in the original notes destination");
    }
    private static void editorialClassificationPipeline()throws Exception{
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES)){
            reset("bad-editor-mixed",false);File dir=editorialFixture(protocol,new int[]{20},false,true);JSONObject run=state(dir);
            run.put("affairs_schema",0).put("config",ClassroomSummary.configKey(AI,0,"保留题干条件及步骤分",true,1,1,0));
            run.getJSONObject("editor_plan").getJSONArray("sections").getJSONObject(0).put("refs",new JSONArray().put("0:0"));
            ClassroomStore.write(new File(dir,"run.json"),run);JSONObject result=generateWithProtocol(dir,protocol),document=result.getJSONArray("parts").getJSONObject(0);
            check(result.getBoolean("complete")&&requests.size()==1&&document.getJSONArray("notes").length()==2&&!document.has("affairs"),"legacy complete mixed reply retains both entries in its original notes destination: "+protocol);
            batchHistory(dir,protocol);
        }
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES))for(String fixture:List.of("editor-notes-to-affairs","editor-affairs-to-notes","editor-redundant-overview")){
            reset(fixture,false);File dir=materials(fixture+"-"+protocol,false);JSONObject result=generateWithProtocol(dir,protocol),run=state(dir),document=result.getJSONArray("parts").getJSONObject(0);
            String original=fixture.equals("editor-affairs-to-notes")?"affairs":"notes",actual=fixture.equals("editor-notes-to-affairs")?"affairs":"notes";
            List<String> expectedStages=List.of("correct-0","draft-0","edit-plan","edit-note-0","edit-affairs");
            check(result.getBoolean("complete")&&stages.equals(expectedStages),"every new category-correction run gets one final original-subtitle affairs scan: "+fixture+" "+protocol);
            check(run.getJSONObject("editor_plan").getJSONArray("sections").getJSONObject(0).getString("collection").equals(original)&&run.getJSONArray("editor_notes").getJSONObject(0).getString("collection").equals(actual),"editor checkpoint keeps the actual returned collection rather than the plan guess: "+fixture);
            check(document.getJSONArray(actual).length()==1&&document.getJSONArray(actual.equals("notes")?"affairs":"notes").length()==(original.equals("affairs")?1:0)&&document.getJSONArray("assignments").length()==0,"assembly preserves the corrected knowledge and replaces all candidate affairs with the final integration: "+fixture);
            check(run.getJSONArray("editor_notes").getJSONObject(0).getString("origin").equals("original_subtitles")&&run.getJSONArray("editor_notes").getJSONObject(0).getString("text").contains(ClassroomStore.read(new File(dir,"materials.json")).getJSONArray("segments").getJSONObject(0).getString("text"))&&run.getJSONArray("parts").getJSONObject(0).getJSONArray(original).getJSONObject(0).getString("text").equals(run.getJSONArray("drafts").getJSONObject(0).getJSONArray(original).getJSONObject(0).getString("text")),"classification correction uses original teacher evidence while preserving the complete paid extraction checkpoint: "+fixture);
            String expectedOverview=document.getJSONArray("notes").length()==0?"":run.getJSONObject("editor_plan").getString("overview");
            check(result.getString("overview").equals(expectedOverview)&&document.getString("overview").equals(expectedOverview)&&!document.toString().contains("PRIVATE_REDUNDANT_TOPIC_OVERVIEW"),"affairs-only output clears knowledge overview; remaining knowledge uses only the existing plan overview without inventing or duplicating one");
            String sent=prompt(requests.get(3));check(sent.contains("不按输入条数限定输出条数"),"actual editorial request permits correction to the real content category");
            check(Collections.frequency(stages,"edit-note-0")==1&&stages.stream().noneMatch(s->s.contains("repair")||s.contains("retry")),"valid category correction never consumes a retry or repair");
            if(protocol.equals(AiSettings.RESPONSES))nativeAppendOnly(dir);else appendOnly(dir);evidence(fixture+"-"+protocol,dir);
        }
        for(String accepted:List.of("bad-editor-multiple","bad-editor-mixed")){
            reset(accepted,false);File dir=materials("accepted-split-"+accepted,false);JSONObject result=generate(dir,true),document=result.getJSONArray("parts").getJSONObject(0);
            check(result.getBoolean("complete")&&requests.size()==5&&document.getJSONArray("notes").length()+document.getJSONArray("affairs").length()==2,"complete split response retains its knowledge and scans final original-subtitle affairs: "+accepted);
            appendOnly(dir);
        }
        for(String defect:List.of("bad-editor-assignment","bad-editor-source","bad-editor-image")){
            reset(defect,false);File dir=materials(defect,false);rejects(()->generate(dir,true),"editor still rejects an unsafe or lossy response: "+defect);JSONObject failed=state(dir);String run=failed.getString("run");
            check(requests.size()==4&&!failed.has("final")&&failed.getJSONArray("editor_notes").length()==0&&failed.getInt("edit-note-0_attempt")==1,"invalid category/source/image response cannot commit, silently discard data or auto-retry: "+defect);
            mode="normal";JSONObject result=generate(dir,true);check(result.getBoolean("complete")&&result.getString("run").equals(run)&&requests.size()==6&&stages.get(4).equals("edit-note-0-user-retry")&&stages.get(5).equals("edit-affairs"),"explicit continuation retries only the rejected editorial topic before final affairs: "+defect);
            for(String completed:List.of("correct-0","draft-0","edit-plan"))check(Collections.frequency(stages,completed)==1,"manual editorial recovery preserves paid "+completed+": "+defect);
            appendOnly(dir);evidence(defect,dir);
        }
    }
    private static void courseAffairs()throws Exception{
        check(ClassroomAffairsPolicy.frozen(null)==1,"new runs use affairs schema one");
        check(ClassroomAffairsPolicy.frozen(new JSONObject().put("conversation",new JSONObject()))==0,"missing schema in an unfinished conversation remains legacy");
        check(ClassroomAffairsPolicy.frozen(new JSONObject().put("conversation",new JSONObject()).put("final",new JSONObject()))==1,"completed legacy run regenerates with new contract");
        check(ClassroomSummary.configKey(AI,0,"",true,1,0,0).endsWith("\n11\ntrue\n")&&!ClassroomSummary.configKey(AI,0,"",true,1,0,0).contains("affairs_schema"),"legacy config retains exact pipeline eleven key");
        check(ClassroomSummary.configKey(AI,0,"",true,1,0).endsWith("affairs_schema=1"),"existing public config overload selects new schema");
        JSONArray segments=new JSONArray().put(new JSONObject().put("id",0).put("text","原始事务")),pics=new JSONArray().put(new JSONObject().put("file","image-3.jpg"));
        JSONObject input=new JSONObject().put("segments",segments).put("images",pics);
        JSONObject only=new JSONObject().put("overview","").put("notes",new JSONArray()).put("affairs",new JSONArray().put(content("考试范围",0))).put("assignments",new JSONArray());
        ClassroomSummary.validate(only,input);check(true,"affairs-only class needs no invented knowledge overview or note");
        JSONObject missing=new JSONObject(only.toString());missing.remove("affairs");rejects(()->ClassroomSummary.validate(missing,input),"new schema requires affairs even when empty");
        JSONObject bad=new JSONObject(only.toString());bad.getJSONArray("affairs").getJSONObject(0).put("sources",new JSONArray().put(99));rejects(()->ClassroomSummary.validate(bad,input),"affairs source validation is identical to notes");
        JSONObject badArray=new JSONObject(only.toString());badArray.getJSONArray("affairs").getJSONObject(0).put("images",new JSONArray().put("image-999.jpg"));rejects(()->ClassroomSummary.validate(badArray,input),"affairs array cannot reference an unseen picture");
        JSONObject inline=new JSONObject(only.toString());inline.getJSONArray("affairs").getJSONObject(0).put("text","![考核细节](images/image-999.jpg)");rejects(()->ClassroomSummary.validate(inline,input),"affairs markdown cannot bypass image whitelist");
        JSONObject image=new JSONObject(only.toString());image.getJSONArray("affairs").getJSONObject(0).put("text","![考核细节](images/image-3.jpg)").put("images",new JSONArray().put("image-3.jpg"));
        ClassroomSummary.validate(image,input);check(ClassroomDocument.referencedImages(new JSONObject().put("parts",new JSONArray().put(image))).contains("image-3.jpg"),"affairs images enter shared portable inventory");
        ClassroomFigureReview.filter(image,new JSONArray().put(new JSONObject().put("file","image-3.jpg").put("included",false)));check(image.getJSONArray("affairs").getJSONObject(0).getJSONArray("images").length()==0&&!image.toString().contains("![考核细节]"),"rejected affairs pictures are filtered in both forms");
        JSONObject mixed=new JSONObject(only.toString()).put("overview","学习重点").put("notes",new JSONArray().put(content("考试范围",0)));JSONArray parts=new JSONArray().put(mixed);
        JSONObject plan=new JSONObject().put("overview","学习重点").put("sections",new JSONArray().put(section("notes","0:notes:0")).put(section("affairs","0:affairs:0")));
        ClassroomEnhancedSummary.validatePlan(plan,parts);check(true,"same named knowledge and affairs are separate complete categories");
        JSONObject omitted=new JSONObject(plan.toString());omitted.getJSONArray("sections").remove(1);rejects(()->ClassroomEnhancedSummary.validatePlan(omitted,parts),"plan cannot omit affairs indexes");
        JSONObject duplicate=new JSONObject(plan.toString());duplicate.getJSONArray("sections").getJSONObject(1).getJSONArray("refs").put("0:affairs:0");rejects(()->ClassroomEnhancedSummary.validatePlan(duplicate,parts),"plan cannot reuse an affairs index");
        JSONObject correctedPlan=new JSONObject(plan.toString()).put("overview","").put("sections",new JSONArray().put(section("affairs","0:notes:0","0:affairs:0")));
        ClassroomEnhancedSummary.validatePlan(correctedPlan,parts);check(true,"plan can merge the same affair from notes and affairs into its correct final category");
        JSONObject reversePlan=new JSONObject(plan.toString()).put("sections",new JSONArray().put(section("notes","0:notes:0","0:affairs:0")));
        ClassroomEnhancedSummary.validatePlan(reversePlan,parts);check(true,"plan can merge the same knowledge from affairs and notes into its correct final category");
        JSONObject invalidCategory=new JSONObject(correctedPlan.toString());invalidCategory.getJSONArray("sections").getJSONObject(0).put("collection","unknown");rejects(()->ClassroomEnhancedSummary.validatePlan(invalidCategory,parts),"cross-collection planning still rejects an unknown final category");
        JSONObject unknownReference=new JSONObject(correctedPlan.toString());unknownReference.getJSONArray("sections").getJSONObject(0).getJSONArray("refs").put("0:affairs:99");rejects(()->ClassroomEnhancedSummary.validatePlan(unknownReference,parts),"cross-collection planning still rejects a fabricated original ref");
        JSONObject affairsOnly=new JSONObject(only.toString());
        ClassroomEnhancedSummary.validatePlan(new JSONObject().put("overview","知识重点").put("sections",new JSONArray().put(section("notes","0:affairs:0"))),new JSONArray().put(affairsOnly));check(true,"correcting all raw affairs to knowledge permits a knowledge overview");
        JSONObject notesOnly=new JSONObject(only.toString()).put("notes",new JSONArray().put(content("原错分事务",0))).put("affairs",new JSONArray());
        ClassroomEnhancedSummary.validatePlan(new JSONObject().put("overview","").put("sections",new JSONArray().put(section("affairs","0:notes:0"))),new JSONArray().put(notesOnly));check(true,"correcting all raw notes to affairs does not require an invented knowledge overview");
        JSONObject crossed=new JSONObject(plan.toString());crossed.getJSONArray("sections").getJSONObject(1).put("collection","notes");rejects(()->ClassroomEnhancedSummary.validatePlan(crossed,parts),"the same final-category topic cannot remain duplicated after correcting classification");
        JSONObject legacyPart=new JSONObject(only.toString());legacyPart.remove("affairs");legacyPart.put("overview","旧重点").put("notes",new JSONArray().put(content("旧知识",0)));ClassroomSummary.validate(legacyPart,input,false,0);check(true,"explicit legacy remains valid without affairs");
        for(boolean enhanced:new boolean[]{false,true}){
            reset("affairs-only",true);File dir=materials("affairs-only-"+enhanced,false);JSONObject result=generate(dir,enhanced),document=result.getJSONArray("parts").getJSONObject(0);
            check(result.getBoolean("complete")&&document.getJSONArray("notes").length()==0&&result.getString("overview").isEmpty()&&document.getJSONArray("affairs").length()==1,"ordinary/enhanced affairs-only output remains useful without knowledge shells: "+enhanced);
            check(document.getJSONArray("affairs").getJSONObject(0).getString("text").contains("扣1分")&&document.getJSONArray("affairs").getJSONObject(0).getJSONArray("sources").length()==1&&document.getJSONArray("affairs").getJSONObject(0).getJSONArray("images").length()==1,"affairs preserve fine grading detail, uncertainty, sources and actual pictures: "+enhanced);
            check(state(dir).getInt("affairs_schema")==1&&result.getInt("affairs_schema")==1&&!document.getJSONArray("affairs").getJSONObject(0).has("collection"),"schema is frozen and saved in summary while editor collection metadata does not escape: "+enhanced);appendOnly(dir);
        }
        reset("affairs-mixed",false);JSONObject result=generate(materials("affairs-mixed",true),true),doc=result.getJSONArray("parts").getJSONObject(0);
        check(doc.getJSONArray("notes").length()==1&&doc.getJSONArray("affairs").length()==1,"mixed grouped editor assembles each category separately without duplicating original blocks");
        String planPrompt=prompt(requests.get(stages.indexOf("edit-plan"))),exampleMarker="只输出紧凑JSON ";
        JSONObject planExample=new JSONObject(planPrompt.substring(planPrompt.indexOf(exampleMarker)+exampleMarker.length(),planPrompt.indexOf("。collection")));
        JSONObject exampleSection=planExample.getJSONArray("sections").getJSONObject(0);
        check(exampleSection.getString("collection").equals("notes")&&exampleSection.getJSONArray("refs").getString(0).equals("0:notes:0"),"new editorial example uses a valid collection matching its reference");
        check(!planPrompt.contains("notes|affairs")&&planPrompt.contains("collection只可为notes或affairs，表示本主题按真实正文确定的最终类别")&&planPrompt.contains("分别来自初稿notes和affairs"),"new editorial prompt corrects classification and merges same facts across original collections without mixing final categories");
        for(boolean enhanced:new boolean[]{false,true})for(String defect:List.of("affairs-source","affairs-image")){
            reset(defect,false);File dir=materials(defect+"-"+enhanced,false);rejects(()->generate(dir,enhanced),"invalid affairs support rolls back block: "+defect+enhanced);String run=state(dir).getString("run");
            check((enhanced?state(dir).getJSONArray("drafts").length()==0:!state(dir).has("draft"))&&!state(dir).has("final")&&requests.size()==(enhanced?2:1),"affairs semantic failure never commits or auto-repairs: "+defect+enhanced);
            mode="affairs-only";check(generate(dir,enhanced).getString("run").equals(run),"explicit resume keeps successful correction and original affairs contract: "+defect+enhanced);appendOnly(dir);
        }
        reset("affairs-missing",false);File repaired=materials("affairs-required-repair",false);check(generate(repaired,true).getBoolean("complete"),"missing affairs field receives the single structural repair");check(Collections.frequency(stages,"draft-0-repair")==1&&prompt(requests.get(2)).contains("affairs"),"structural repair carries complete new schema");appendOnly(repaired);
        reset("affairs-type",false);File wrongType=materials("affairs-type-repair",false);check(generate(wrongType,true).getBoolean("complete")&&Collections.frequency(stages,"draft-0-repair")==1,"wrong affairs container receives the single structural repair");
        reset("affairs-missing-always",false);File bounded=materials("affairs-repair-budget",false);rejects(()->generate(bounded,true),"persistently missing affairs stops after bounded repair");check(requests.size()==3&&state(bounded).getJSONArray("drafts").length()==0,"failed affairs repair leaves no committed extraction");
        for(boolean enhanced:new boolean[]{false,true}){
            reset("normal",false);File dir=materials("legacy-affairs-frozen-"+enhanced,false);JSONObject before=legacyState(dir,enhanced);JSONArray prefix=new JSONArray(before.getJSONObject("conversation").getJSONArray("messages").toString());String config=before.getString("config"),run=before.getString("run");
            result=generate(dir,enhanced);check(result.getString("run").equals(run)&&state(dir).getString("config").equals(config)&&state(dir).getInt("affairs_schema")==0,"unfinished legacy run retains identity and original config: "+enhanced);
            JSONArray sent=requests.get(0).getJSONArray("messages");for(int i=0;i<prefix.length();i++)check(sent.getJSONObject(i).toString().equals(prefix.getJSONObject(i).toString()),"legacy sent prefix and reasoning survive unchanged: "+enhanced+":"+i);
            for(JSONObject request:requests){String p=prompt(request);check(!p.contains("affairs")&&!p.contains("collection"),"legacy subsequent prompts retain old schema: "+enhanced);}
            JSONObject legacyDocument=result.getJSONArray("parts").getJSONObject(0);check(result.getInt("affairs_schema")==0&&!legacyDocument.has("affairs"),"legacy assembled result records version zero and retains its original part shape: "+enhanced);
            reset("affairs-only",false);result=generate(dir,enhanced);check(!result.getString("run").equals(run)&&state(dir).getInt("affairs_schema")==1&&result.getJSONArray("parts").getJSONObject(0).has("affairs"),"completed legacy regeneration opts into current contract: "+enhanced);
        }
    }
    private static void structuralOnly(JSONObject schema)throws Exception{
        for(String key:schema.keySet())check(Set.of("type","properties","required","additionalProperties","items").contains(key),"stage schema contains structure only, no content/length/range constraint: "+key);
        if(schema.getString("type").equals("object")){
            JSONObject fields=schema.getJSONObject("properties");JSONArray required=schema.getJSONArray("required");Set<String> names=new HashSet<>();for(int n=0;n<required.length();n++)names.add(required.getString(n));
            check(!schema.getBoolean("additionalProperties")&&names.equals(fields.keySet()),"object grammar declares each existing field exactly at its own level");
            for(String key:fields.keySet())structuralOnly(fields.getJSONObject(key));
        }else if(schema.getString("type").equals("array"))structuralOnly(schema.getJSONObject("items"));
    }
    private static int occurrences(String text,String fragment){int count=0,from=0;while((from=text.indexOf(fragment,from))>=0){count++;from+=fragment.length();}return count;}
    private static JSONObject affairsFirstGenerate(File dir,String protocol)throws Exception{try(RequestControl control=new RequestControl(20000)){return ClassroomSummary.generate(dir,LESSON,new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol),images?2:0,control,p->{},"保留题干条件及步骤分",true,3);}}
    /** Preserve the complete 127 compact regression candidate instead of silently migrating it. */
    private static JSONObject compactGenerate(File dir,String protocol)throws Exception{
        JSONObject saved=ClassroomStore.read(new File(dir,"run.json"));if(saved==null||saved.has("final")){AiSettings ai=new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol);String id="compact127-fixture-"+System.nanoTime();JSONObject run=new JSONObject().put("run",id).put("config",ClassroomSummary.configKey(ai,images?2:0,"保留题干条件及步骤分",true,3,1,1)).put("note_strength",3).put("figure_policy",1).put("affairs_schema",1).put("native_json",true).put("native_json_schema",true).put("affairs_source_first",true).put("editorial_source_first",true).put("unified_affairs",true).put("compact_pipeline",ClassroomEnhancedSummary.compactEligible(ClassroomStore.read(new File(dir,"materials.json")).getJSONArray("segments"))).put("compact_affairs_first",false).put("content_batch_size",2).put("editor_batch_size",3).put("editor_batch_chars",18000);try(RequestControl control=new RequestControl(20000)){new ClassroomConversation(dir,run,new ChatCompletionClient(ai,control,com.donglan.chrona.ai.SchedulePrompt.SYSTEM,null,"ses_"+id),ClassroomSummary.systemPrompt("保留题干条件及步骤分",3));}ClassroomStore.write(new File(dir,"run.json"),run);}return affairsFirstGenerate(dir,protocol);
    }
    private static File compactMaterials(String name)throws Exception{
        File dir=materials(name,3,false);JSONObject material=ClassroomStore.read(new File(dir,"materials.json"));JSONArray rows=material.getJSONArray("segments");rows.getJSONObject(0).put("text","知识原话：在非零条件下合法约分，保留题干、操作与结论。");rows.getJSONObject(1).put("text","事务原话：小测只考本节，步骤分2分，符号错误扣1分。");rows.getJSONObject(2).put("text","行动原话：本周完成练习甲并核对步骤。");ClassroomStore.write(new File(dir,"materials.json"),material);JSONObject pictures=ClassroomStore.read(new File(dir,"candidates.json"));for(int n=0;n<pictures.getJSONArray("images").length();n++)pictures.getJSONArray("images").getJSONObject(n).put("index",7+n*12);ClassroomStore.write(new File(dir,"candidates.json"),pictures);return dir;
    }
    private static void compactPipeline()throws Exception{
        JSONArray exact=new JSONArray().put(new JSONObject().put("text","😀".repeat(30000))),over=new JSONArray().put(new JSONObject().put("text","😀".repeat(30000)+"甲"));check(ClassroomEnhancedSummary.compactEligible(exact)&&!ClassroomEnhancedSummary.compactEligible(over),"compact eligibility uses total original UTF16 length, including supplementary Unicode, at exactly 60000");
        JSONObject planFields=ClassroomResponseShape.contract("image-plan",1,1).getJSONObject("schema").getJSONObject("properties"),wholeFields=ClassroomResponseShape.contract("whole-note",1,1).getJSONObject("schema").getJSONObject("properties");check(planFields.keySet().equals(Set.of("image_assessment","image_requests")),"image planning has only its two existing image-decision fields and never an intermediate notebook");check(wholeFields.keySet().equals(Set.of("overview","notes","affairs","assignments","image_reviews"))&&wholeFields.getJSONObject("assignments").getJSONObject("items").getJSONObject("properties").has("evidence"),"one whole-note schema keeps independent knowledge, affairs, actions, literal action evidence and actual image reviews");structuralOnly(ClassroomResponseShape.contract("image-plan",1,1).getJSONObject("schema"));structuralOnly(ClassroomResponseShape.contract("whole-note",1,1).getJSONObject("schema"));
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES)){
            for(String scenario:List.of("disabled","images","unavailable","not-needed")){
                reset(scenario.equals("not-needed")?"compact-no-request":"compact-normal",!scenario.equals("disabled"));File dir=compactMaterials("compact-"+scenario+"-"+protocol);if(scenario.equals("unavailable")){ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject().put("images",new JSONArray()));JSONObject m=ClassroomStore.read(new File(dir,"materials.json"));m.put("raw_images",0);ClassroomStore.write(new File(dir,"materials.json"),m);}
                check(!new File(dir,"run.json").exists(),"127 compact fixture begins before its historical snapshot is frozen");JSONObject result=compactGenerate(dir,protocol),run=state(dir),doc=result.getJSONArray("parts").getJSONObject(0);check(result.getBoolean("complete")&&run.getBoolean("compact_pipeline")&&run.getJSONArray("blocks").length()==1&&stages.equals(scenario.equals("disabled")||scenario.equals("unavailable")?List.of("correct-0","whole-note"):List.of("correct-0","image-plan","whole-note")),"ordinary-length fresh enhanced lesson is correction, only a meaningful model image decision and one complete publication: "+scenario+" "+protocol);
                check(!run.has("drafts")&&!run.has("editor_plan")&&!run.has("editor_notes")&&!run.has("editor_affairs")&&!run.has("editor_assignments")&&run.has("compact_note")&&run.getJSONObject("image_plan").keySet().equals(Set.of("image_assessment","image_requests")),"compact path does not create extraction/planning/editorial/affair rewrite checkpoints");
                boolean local=scenario.equals("disabled")||scenario.equals("unavailable");check(run.getString("image_plan_origin").equals(local?"local_availability":"model")&&run.getJSONObject("image_audit").getString("assessment_source").equals(local?"local_availability":"model")&&run.getJSONObject("image_plan").getJSONObject("image_assessment").getString("decision").equals(scenario.equals("images")?"request":scenario.equals("not-needed")?"not_needed":scenario)&&!run.getJSONObject("image_plan").getJSONObject("image_assessment").getString("reason").isBlank(),"local upload/availability decisions are truthful and auditable, never represented as a paid model assessment");
                check(doc.getJSONArray("notes").length()==1&&doc.getJSONArray("affairs").length()==1&&doc.getJSONArray("assignments").length()==1&&doc.getJSONArray("notes").getJSONObject(0).getString("text").contains("合法约分")&&doc.getJSONArray("affairs").getJSONObject(0).getString("text").contains("符号错误扣1分")&&doc.getJSONArray("assignments").getJSONObject(0).getString("evidence").equals("本周完成练习甲并核对步骤。"),"whole publication retains separate useful knowledge, fine grading detail and exact non-invented action evidence");
                JSONArray originals=ClassroomSummary.lessonInput(ClassroomStore.read(new File(dir,"materials.json")).getJSONArray("segments"),new JSONArray()).getJSONArray("segments");String expectedRaw=ClassroomSummary.uploadChunk(new JSONObject().put("segments",originals).put("images",new JSONArray())).toString();for(int r=1;r<requests.size();r++)check(prompt(requests.get(r)).contains("\n全课原始字幕：\n"+expectedRaw),"each compact JSON request receives the complete original lesson with unchanged IDs and words");check(messages(requests.get(0)).getJSONObject(0).getString("content").equals(ClassroomSummary.systemPrompt("保留题干条件及步骤分",3)),"fresh compact factory retains the actual preferred-strength system contract");
                JSONArray inventory=run.getJSONArray("analysed");int expectedPictures=scenario.equals("images")?2:0;check(run.getJSONObject("image_audit").getInt("analysed_count")==expectedPictures&&inventory.length()==expectedPictures&&doc.getJSONArray("notes").getJSONObject(0).getJSONArray("images").length()==expectedPictures,"only actually requested/attached images enter the complete note");for(int r=0;r<requests.size();r++){JSONArray content=messages(requests.get(r)).getJSONObject(messages(requests.get(r)).length()-1).getJSONArray("content");int actual=0;for(int n=0;n<content.length();n++)if(content.getJSONObject(n).getString("type").equals(protocol.equals(AiSettings.RESPONSES)?"input_image":"image_url"))actual++;check(actual==(r==requests.size()-1?expectedPictures:0),"image bytes upload only at the single whole-note stage");}for(int n=0;n<inventory.length();n++){String file=inventory.getJSONObject(n).getString("file");check(file.matches("image-(7|19|31)\\.jpg")&&doc.getJSONArray("notes").getJSONObject(0).getJSONArray("images").toString().contains(file),"nonconsecutive actual picture identities survive without renumbering");}
                int sent=requests.size();String history=run.getJSONObject("conversation").toString();run.remove("final");ClassroomStore.write(new File(dir,"run.json"),run);check(compactGenerate(dir,protocol).getBoolean("complete")&&requests.size()==sent&&state(dir).getJSONObject("conversation").toString().equals(history),"saved complete whole-note reassembles without another request or history change");batchHistory(dir,protocol);evidence("compact-"+scenario+"-"+protocol,dir);
            }
            for(String failure:List.of("plan-network","whole-network","compact-bad-source","compact-bad-image","compact-bad-evidence","compact-length","compact-rejected-only")){
                reset(failure.startsWith("compact-")?failure:"compact-normal",true);File dir=compactMaterials("compact-failure-"+failure+"-"+protocol);if(failure.endsWith("network"))failCall=failure.startsWith("plan")?2:3;rejects(()->compactGenerate(dir,protocol),"failed compact stage is not automatically regenerated or partially published: "+failure);JSONObject checkpoint=state(dir);int sent=requests.size();String corrected=checkpoint.getJSONArray("corrected").toString(),savedPlan=checkpoint.optJSONObject("image_plan")==null?null:checkpoint.getJSONObject("image_plan").toString();check(!checkpoint.has("compact_note")&&!checkpoint.has("final")&&(!checkpoint.has("analysed")||checkpoint.getJSONArray("analysed").length()==0)&&!ClassroomStore.read(new File(dir,"summary.json")).optBoolean("complete"),"invalid whole reply, rejected picture-only body or interrupted plan never commits compact_note/analysed/final");mode="compact-normal";failCall=-1;JSONObject recovered=compactGenerate(dir,protocol),after=state(dir);int extra=failure.equals("plan-network")?2:1;check(recovered.getBoolean("complete")&&requests.size()==sent+extra&&after.getJSONArray("corrected").toString().equals(corrected)&&(savedPlan==null||after.getJSONObject("image_plan").toString().equals(savedPlan))&&Collections.frequency(stages,"correct-0")==1,"manual compact recovery pays only the failed stage and still-required downstream stage, retaining corrections and the accepted plan: "+failure);if(!failure.equals("plan-network")){JSONArray content=messages(requests.get(sent)).getJSONObject(messages(requests.get(sent)).length()-1).getJSONArray("content");check(content.length()==1&&stages.get(sent).startsWith("whole-note"),"whole-note recovery appends text only and never uploads its original pictures again");}if(failure.equals("compact-bad-evidence"))check(prompt(requests.get(sent)).contains(EVIDENCE_MARKER)&&prompt(requests.get(sent)).contains("本周完成练习甲并核对步骤。"),"whole-note manual quote recovery includes genuine original evidence diagnostics");batchHistory(dir,protocol);evidence("compact-recovery-"+failure+"-"+protocol,dir);
            }
            reset("compact-missing",false);File repair=compactMaterials("compact-schema-repair-"+protocol);check(compactGenerate(repair,protocol).getBoolean("complete")&&stages.equals(List.of("correct-0","whole-note","whole-note-repair")),"whole-note missing field receives exactly the established one structural repair, without a second editorial pipeline");batchHistory(repair,protocol);evidence("compact-repair-"+protocol,repair);
            for(int chars:new int[]{60000,60001}){reset("compact-normal",false);File boundary=materials("compact-boundary-"+chars+"-"+protocol,false);JSONObject material=ClassroomStore.read(new File(boundary,"materials.json"));material.getJSONArray("segments").getJSONObject(0).put("text","甲".repeat(chars));ClassroomStore.write(new File(boundary,"materials.json"),material);failCall=1;rejects(()->compactGenerate(boundary,protocol),"freeze exact factory selection at the original UTF16 length boundary");JSONObject run=state(boundary);check(run.getBoolean("compact_pipeline")== (chars==60000)&&run.getJSONArray("blocks").length()==(chars==60000?1:11)&&stages.equals(List.of("correct-0")),"fresh factory freezes compact at 60000 and expanded above it, with matching correction block sizes: "+chars);evidence("compact-boundary-"+chars+"-"+protocol,boundary);}
            for(boolean explicitFalse:new boolean[]{false,true}){reset("normal",false);File legacy=previousContentMaterials("compact-legacy-"+explicitFalse+"-"+protocol,protocol);JSONObject before=state(legacy);if(explicitFalse)before.put("compact_pipeline",false);ClassroomStore.write(new File(legacy,"run.json"),before);JSONObject result=compactGenerate(legacy,protocol),after=state(legacy);check(result.getBoolean("complete")&&result.getString("run").equals(before.getString("run"))&&!stages.contains("image-plan")&&!stages.contains("whole-note")&&(explicitFalse?!after.getBoolean("compact_pipeline"):!after.has("compact_pipeline"))&&after.getJSONObject("conversation").getJSONObject("options").toString().equals(before.getJSONObject("conversation").getJSONObject("options").toString()),"old missing/false compact flag retains its exact expanded run and saved options: "+protocol+" "+explicitFalse);batchHistory(legacy,protocol);evidence("compact-legacy-"+explicitFalse+"-"+protocol,legacy);}
        }
        compactCorrectionCaches();
        affairsFirstPipeline();
    }
    private static JSONObject syntheticReply(int index)throws Exception{
        JSONObject reply=replies.get(index);if(!reply.has("output"))return ClassroomSummary.parseObject(reply.getString("content"));JSONArray output=reply.getJSONArray("output");for(int n=0;n<output.length();n++){JSONObject item=output.getJSONObject(n);if(!item.optString("type").equals("message"))continue;JSONArray content=item.getJSONArray("content");for(int k=0;k<content.length();k++)if(content.getJSONObject(k).optString("type").equals("output_text"))return ClassroomSummary.parseObject(content.getJSONObject(k).getString("text"));}throw new IOException("No complete synthetic message");
    }
    private static void affairsFirstPipeline()throws Exception{
        echoedAffairsCollections();
        JSONObject counted=ClassroomResponseShape.contract("affairs-plan",1,1,true).getJSONObject("schema");structuralOnly(counted);
        check(counted.getJSONObject("properties").getJSONObject("image_requests").getJSONObject("items").getJSONObject("properties").getJSONObject("count").getString("type").equals("integer"),"new counted image grammar exposes only structural integer demand");
        JSONArray scope=new JSONArray().put(new JSONObject().put("id",8).put("text","概念"))
                .put(new JSONObject().put("id",29).put("text","以后作业需在周末提交。"))
                .put(new JSONObject().put("id",45).put("text","某个知识点"))
                .put(new JSONObject().put("id",91).put("text","那是往届的安排，没让你们做。"))
                .put(new JSONObject().put("id",107).put("text","The exam grade includes attendance points."));
        String originalScope=scope.toString();JSONArray indices=ClassroomEnhancedSummary.affairsScopeIndex(scope);
        check(indices.toString().equals("[8,29,45,91,107]")&&scope.toString().equals(originalScope),"affairs scope index highlights sparse source ids, later clarifications and English rules without rewriting or duplicating raw evidence");
        JSONArray numericScope=new JSONArray();
        String[] clues={"纯知识甲","纯知识乙","总共十二分，不能计成十五分。","纯知识丙","纯知识丁","纯知识戊","引用这个结论之前先验算条件。","纯知识己","纯知识庚","纯知识辛","这一步可以拿2.5分。","纯知识壬","纯知识癸","纯知识子","这部分先证一遍才能使用。","纯知识丑","纯知识寅","纯知识卯","一分之三与十分钟只是计算和时长。","纯知识辰"};
        for(int i=0;i<clues.length;i++)numericScope.put(new JSONObject().put("id",100+i*7).put("text",clues[i]));
        String numericOriginal=numericScope.toString();JSONArray numericIds=ClassroomEnhancedSummary.affairsScopeIndex(numericScope);
        check(numericIds.toString().equals("[107,114,121,135,142,149,163,170,177,191,198,205]"),"score amounts and answer prerequisites highlight only source ids and immediate context, not fractions or minutes");
        check(numericScope.toString().equals(numericOriginal),"expanded affairs navigation never filters or rewrites the complete raw subtitles");
        JSONObject schema=ClassroomResponseShape.contract("affairs-plan",1,1).getJSONObject("schema"),fields=schema.getJSONObject("properties");check(fields.keySet().equals(Set.of("image_assessment","image_requests","affairs","assignments"))&&!fields.has("notes")&&!fields.has("overview")&&fields.getJSONObject("assignments").getJSONObject("items").getJSONObject("properties").has("evidence"),"128 first-stage grammar has only image planning, original course affairs and literal-action evidence");structuralOnly(schema);
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES)){
            legacyCountedSnapshot(protocol);
            for(String empty:List.of("affairs-first-missing-images","affairs-first-null-images")){
                reset(empty,true);File emptyDir=compactMaterials(empty+"-"+protocol);
                JSONObject emptyResult=affairsFirstGenerate(emptyDir,protocol),rawFact=syntheticReply(stages.indexOf("affairs-plan")).getJSONArray("affairs").getJSONObject(0),savedFact=state(emptyDir).getJSONObject("image_plan").getJSONArray("affairs").getJSONObject(0);
                check(emptyResult.getBoolean("complete")&&stages.equals(List.of("correct-0","affairs-plan","whole-note")),"known-empty metadata does not regenerate a paid text-only plan: "+empty+" "+protocol);
                check(savedFact.getJSONArray("images").length()==0&&rawFact.optJSONArray("images")==null&&savedFact.getString("text").equals(rawFact.getString("text"))&&savedFact.getJSONArray("sources").similar(rawFact.getJSONArray("sources")),"hydrate only the authoritative empty image list, retaining teacher facts, sources and the raw reply: "+empty+" "+protocol);
            }
            reset("affairs-first-echo",true);File echoedDir=compactMaterials("echoed-affairs-"+protocol);
            JSONObject echoedResult=affairsFirstGenerate(echoedDir,protocol),echoedRun=state(echoedDir),echoedDoc=echoedResult.getJSONArray("parts").getJSONObject(0);
            for(String field:List.of("affairs","assignments"))check(echoedDoc.getJSONArray(field).similar(echoedRun.getJSONObject("image_plan").getJSONArray(field)),"an exact model echo does not duplicate or rewrite first-stage "+field+": "+protocol);
            check(stages.equals(List.of("correct-0","affairs-plan","whole-note"))&&syntheticReply(stages.indexOf("whole-note")).getJSONArray("affairs").length()==1,"raw echoed reply stays in original history without an extra AI repair: "+protocol);
            String firstRequest=prompt(requests.get(stages.indexOf("affairs-plan")));
            check(firstRequest.lastIndexOf("\n本轮执行要求：")>firstRequest.lastIndexOf("\nPPT时间目录：")&&firstRequest.lastIndexOf("\nPPT时间目录：")>firstRequest.lastIndexOf("\n全课原始字幕：\n"),"affairs execution reminder follows all raw material and the PPT catalogue: "+protocol);
            check(firstRequest.contains("先读完全课，确定最终语境中面向本班且仍有效的要求")&&firstRequest.contains("在先确定最终有效要求之后")&&firstRequest.contains("不能只抽离对象或时态的动作短句")&&firstRequest.contains("涉及对象或有效性的后文澄清也列入相关来源"),"actual action request determines current scope before quoting a local command and retains later clarifications: "+protocol);
            check(firstRequest.contains("事务正文按具体情景忠实摘记教师原话")&&firstRequest.contains("原话只有结果时就记结果，不补因果解释")&&firstRequest.contains("范围词必须与它限制的具体知识对象写在同一句"),"actual affairs request preserves separate teacher scenarios without invented causal explanations or detached scope: "+protocol);
            check(prompt(requests.get(stages.indexOf("whole-note"))).contains("程序覆盖实际边界")&&prompt(requests.get(stages.indexOf("whole-note"))).contains("不强加公式与解题模板"),"actual knowledge request adapts to subject needs instead of a fixed calculus template: "+protocol);
            String wholeRequest=prompt(requests.get(stages.indexOf("whole-note")));
            check(wholeRequest.lastIndexOf("\n本轮成稿要求：")>wholeRequest.lastIndexOf("\n全课原始字幕：\n")&&occurrences(wholeRequest,"定理完整陈述条件、符号与结论后")==1&&wholeRequest.contains("在证明或例题之前直接接一小段不堆公式的自然语言")&&wholeRequest.contains("条件补充或例外提醒不能代替这段理解")&&wholeRequest.contains("操作描述与实际步骤对应"),"whole-note places one concrete teaching instruction after all material, before proof/examples in the expected output, without editing history: "+protocol);
            check(wholeRequest.contains("适用范围与限制需说明何时能用")&&wholeRequest.contains("区分不能套用与结论不成立")&&wholeRequest.contains("同一定理不同形式共用解释"),"actual final request includes applicability limits and a shared explanation for theorem variants: "+protocol);
            for(boolean withImages:new boolean[]{false,true})for(boolean cached:new boolean[]{false,true}){
                reset("affairs-first-normal",withImages);File dir=compactMaterials("affairs-first-"+withImages+"-"+cached+"-"+protocol);JSONArray cachedText=new JSONArray().put("PRIVATE_CACHED_DERIVED_INTERPRETATION\n完整更正文稿，原字不得改动。");if(cached){JSONArray all=ClassroomSummary.lessonInput(ClassroomStore.read(new File(dir,"materials.json")).getJSONArray("segments"),new JSONArray()).getJSONArray("segments");ClassroomStore.write(new File(dir,"correction-cache.json"),new JSONObject().put("blocks",ClassroomEnhancedSummary.blocks(all,60000)).put("parts",cachedText).put("source_run","prior-correction-only"));}
                check(!new File(dir,"run.json").exists(),"128 enters the current factory without a synthetic run checkpoint");JSONObject result=affairsFirstGenerate(dir,protocol),run=state(dir),doc=result.getJSONArray("parts").getJSONObject(0);List<String> expected=cached?List.of("affairs-plan","whole-note"):List.of("correct-0","affairs-plan","whole-note");check(result.getBoolean("complete")&&run.getBoolean("compact_pipeline")&&run.getBoolean("compact_affairs_first")&&stages.equals(expected),"fresh lessons add one correction; cached lessons always pay exactly two substantive stages, with or without pictures: "+protocol+" "+withImages+" "+cached);
                int first=stages.indexOf("affairs-plan"),whole=stages.indexOf("whole-note");JSONObject originalPlan=syntheticReply(first),rawWhole=syntheticReply(whole);check(originalPlan.keySet().equals(Set.of("image_assessment","image_requests","affairs","assignments"))&&originalPlan.getJSONArray("affairs").length()==1&&originalPlan.getJSONArray("assignments").length()==1&&rawWhole.getJSONArray("notes").length()==1&&rawWhole.getJSONArray("affairs").length()==0&&rawWhole.getJSONArray("assignments").length()==0,"the first reply owns original affairs/actions while the whole-note reply can omit both without losing them");
                for(String collection:List.of("affairs","assignments"))check(doc.getJSONArray(collection).similar(originalPlan.getJSONArray(collection))&&run.getJSONObject("image_plan").getJSONArray(collection).similar(originalPlan.getJSONArray(collection)),"local prepend preserves accepted "+collection+" exactly and never rewrites the first-stage checkpoint");check(doc.getJSONArray("notes").getJSONObject(0).getString("text").contains("合法约分")&&!doc.getJSONArray("notes").getJSONObject(0).getString("text").contains("符号错误扣1分")&&doc.getJSONArray("affairs").getJSONObject(0).getString("text").contains("符号错误扣1分")&&doc.getJSONArray("assignments").getJSONObject(0).getString("evidence").equals("本周完成练习甲并核对步骤。"),"knowledge, fine grading rules and genuine action evidence remain independently useful and separate");
                String firstPrompt=prompt(requests.get(first)),wholePrompt=prompt(requests.get(whole));check(!firstPrompt.contains("PRIVATE_CACHED_DERIVED_INTERPRETATION")&&!firstPrompt.contains("\n复用更正文稿：\n")&&firstPrompt.contains("全课原始字幕")&&wholePrompt.contains("课程事务和具体任务已经在affairs-plan完成"),"affairs extraction sees original teacher words rather than cached derived explanations, and whole-note does not rewrite accepted affairs");if(cached){int copies=0;for(JSONObject request:requests)copies+=occurrences(prompt(request),cachedText.toString());check(copies==1&&wholePrompt.contains(cachedText.toString())&&run.getJSONArray("corrected").similar(cachedText),"cached corrected text is preserved byte-for-byte and appended exactly once, only in whole-note");}
                check(run.getString("image_plan_origin").equals("model")&&run.getJSONObject("image_audit").getString("assessment_source").equals("model")&&run.getJSONArray("analysed").length()==(withImages?1:0),"affairs-plan remains substantive with disabled images and attaches only authorized pictures to whole-note");int sent=requests.size();String planBefore=run.getJSONObject("image_plan").toString(),history=run.getJSONObject("conversation").toString();run.remove("final");ClassroomStore.write(new File(dir,"run.json"),run);JSONObject restored=affairsFirstGenerate(dir,protocol);check(requests.size()==sent&&restored.getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").length()==1&&restored.getJSONArray("parts").getJSONObject(0).getJSONArray("assignments").length()==1&&state(dir).getJSONObject("image_plan").toString().equals(planBefore)&&state(dir).getJSONObject("conversation").toString().equals(history),"saved compact-note restoration appends no API requests and never prepends original collections a second time");batchHistory(dir,protocol);evidence("affairs-first-"+withImages+"-"+cached+"-"+protocol,dir);
            }
            reset("affairs-first-image-extra",true);File extra=compactMaterials("affairs-first-image-extra-"+protocol);JSONObject material=ClassroomStore.read(new File(extra,"materials.json"));material.getJSONArray("segments").getJSONObject(2).put("text","行动原话：本周完成练习甲并核对步骤。还需完成图中标明的附题。");ClassroomStore.write(new File(extra,"materials.json"),material);JSONObject extraResult=affairsFirstGenerate(extra,protocol),extraRun=state(extra),extraDoc=extraResult.getJSONArray("parts").getJSONObject(0),first=syntheticReply(stages.indexOf("affairs-plan")),whole=syntheticReply(stages.indexOf("whole-note"));check(extraDoc.getJSONArray("affairs").length()==2&&extraDoc.getJSONArray("assignments").length()==2&&extraDoc.getJSONArray("affairs").getJSONObject(0).similar(first.getJSONArray("affairs").getJSONObject(0))&&extraDoc.getJSONArray("assignments").getJSONObject(0).similar(first.getJSONArray("assignments").getJSONObject(0))&&extraDoc.getJSONArray("affairs").getJSONObject(1).similar(whole.getJSONArray("affairs").getJSONObject(0)),"new actually attached picture facts append after immutable original affairs/actions, not replace them");check(extraDoc.getJSONArray("affairs").getJSONObject(1).getJSONArray("images").length()==1&&extraDoc.getJSONArray("assignments").getJSONObject(1).getString("evidence").equals("还需完成图中标明的附题。")&&extraRun.getJSONObject("image_plan").getJSONArray("assignments").length()==1,"pictures can identify a new task object only while its action evidence remains a real continuous original subtitle quote");batchHistory(extra,protocol);evidence("affairs-first-extra-"+protocol,extra);
            for(String failure:List.of("first-network","affairs-first-bad-source","affairs-first-bad-image","affairs-first-bad-evidence","whole-network","compact-bad-source","compact-bad-image","compact-rejected-only","affairs-first-extra-bad-evidence")){
                reset(failure.endsWith("network")?"affairs-first-normal":failure,true);File dir=compactMaterials("affairs-first-failure-"+failure+"-"+protocol);boolean firstFailure=failure.equals("first-network")||failure.startsWith("affairs-first-bad-");if(failure.endsWith("network"))failCall=firstFailure?2:3;rejects(()->affairsFirstGenerate(dir,protocol),"128 invalid/interrupted stage waits for explicit continuation: "+failure);JSONObject before=state(dir);String plan=before.optJSONObject("image_plan")==null?null:before.getJSONObject("image_plan").toString();int sent=requests.size();check(!before.has("compact_note")&&!before.has("final")&&(!before.has("analysed")||before.getJSONArray("analysed").length()==0)&&(firstFailure?!before.has("image_plan"):before.getJSONObject("image_plan").getJSONArray("affairs").length()==1)&&!ClassroomStore.read(new File(dir,"summary.json")).optBoolean("complete"),"neither stage failure publishes partial merged collections; accepted first-stage affairs remain intact");mode="affairs-first-normal";failCall=-1;JSONObject recovered=affairsFirstGenerate(dir,protocol),after=state(dir),doc=recovered.getJSONArray("parts").getJSONObject(0);check(recovered.getBoolean("complete")&&requests.size()==sent+(firstFailure?2:1)&&Collections.frequency(stages,"correct-0")==1&&(plan==null||after.getJSONObject("image_plan").toString().equals(plan))&&doc.getJSONArray("affairs").length()==1&&doc.getJSONArray("assignments").length()==1,"manual retry requests only the failed substantive stage plus unfinished downstream work and merges original collections once: "+failure);if(failure.contains("bad-evidence"))check(prompt(requests.get(sent)).contains(EVIDENCE_MARKER),"affairs-plan and whole-note both retain original-quote diagnostics; pure image text cannot become subtitle evidence");if(!firstFailure){JSONArray content=messages(requests.get(sent)).getJSONObject(messages(requests.get(sent)).length()-1).getJSONArray("content");check(content.length()==1,"whole-note retry retains original actual images in history and never uploads them again");}batchHistory(dir,protocol);evidence("affairs-first-recovery-"+failure+"-"+protocol,dir);
            }
            reset("affairs-first-missing",false);File repair=compactMaterials("affairs-first-repair-"+protocol);check(affairsFirstGenerate(repair,protocol).getBoolean("complete")&&stages.equals(List.of("correct-0","affairs-plan","affairs-plan-repair","whole-note")),"first-stage incomplete fields receive exactly the established one format repair, then one complete knowledge result");batchHistory(repair,protocol);
            for(boolean explicitFalse:new boolean[]{false,true})for(boolean withImages:new boolean[]{false,true}){reset("compact-normal",withImages);File old=compactMaterials("affairs-first-frozen127-"+explicitFalse+"-"+withImages+"-"+protocol);failCall=1;rejects(()->compactGenerate(old,protocol),"persist a genuine interrupted 127 compact candidate");JSONObject before=state(old);if(!explicitFalse)before.remove("compact_affairs_first");ClassroomStore.write(new File(old,"run.json"),before);String options=before.getJSONObject("conversation").getJSONObject("options").toString();failCall=-1;JSONObject restored=affairsFirstGenerate(old,protocol),after=state(old);check(restored.getBoolean("complete")&&!stages.contains("affairs-plan")&&stages.contains("whole-note")&&stages.contains("image-plan")==withImages&&(explicitFalse?!after.getBoolean("compact_affairs_first"):!after.has("compact_affairs_first"))&&after.getJSONObject("conversation").getJSONObject("options").toString().equals(options),"127 absent/false affairs-first snapshot preserves its original image-plan or local-availability path and saved options");batchHistory(old,protocol);evidence("affairs-first-frozen127-"+explicitFalse+"-"+withImages+"-"+protocol,old);}
        }
    }
    private static void legacyCountedSnapshot(String protocol)throws Exception{
        reset("affairs-first-normal",true);File dir=compactMaterials("legacy-uncounted-affairs-"+protocol);
        AiSettings ai=new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol);
        String id="synthetic-legacy-uncounted-"+protocol;
        JSONObject old=new JSONObject().put("run",id).put("config",ClassroomSummary.configKey(ai,2,"保留题干条件及步骤分",true,3,1,1))
                .put("note_strength",3).put("figure_policy",1).put("affairs_schema",1).put("native_json",true).put("native_json_schema",true)
                .put("affairs_source_first",true).put("editorial_source_first",true).put("compact_pipeline",true).put("compact_affairs_first",true);
        try(RequestControl control=new RequestControl(20000)){
            new ClassroomConversation(dir,old,new ChatCompletionClient(ai,control,com.donglan.chrona.ai.SchedulePrompt.SYSTEM,null,"ses_"+id),ClassroomSummary.systemPrompt("保留题干条件及步骤分",3));
        }
        ClassroomStore.write(new File(dir,"run.json"),old);failCall=3;
        rejects(()->affairsFirstGenerate(dir,protocol),"synthetic old uncounted first-stage checkpoint survives a failed knowledge request: "+protocol);
        JSONObject before=state(dir);String savedPlan=before.getJSONObject("image_plan").toString(),savedShape=before.getJSONObject("response_shapes").getJSONObject("affairs-plan").toString();
        JSONArray prefix=new JSONArray(before.getJSONObject("conversation").getJSONArray("messages").toString());
        check(!before.has("counted_image_requests")&&before.getJSONArray("selected").length()==2&&!before.getJSONObject("image_plan").getJSONArray("image_requests").getJSONObject(0).has("count"),"missing old snapshot flag uses the old full-budget selection and uncounted plan: "+protocol);
        failCall=-1;JSONObject complete=affairsFirstGenerate(dir,protocol),after=state(dir);
        check(complete.getBoolean("complete")&&!after.has("counted_image_requests")&&after.getJSONArray("selected").length()==2&&savedPlan.equals(after.getJSONObject("image_plan").toString())&&savedShape.equals(after.getJSONObject("response_shapes").getJSONObject("affairs-plan").toString()),"old uncounted plan, native shape and selected frames remain exact through recovery: "+protocol);
        JSONArray history=after.getJSONObject("conversation").getJSONArray("messages");
        for(int n=0;n<prefix.length();n++)check(prefix.getJSONObject(n).toString().equals(history.getJSONObject(n).toString()),"old uncounted history prefix remains unchanged: "+protocol+" "+n);
    }
    private static void echoedAffairsCollections()throws Exception{
        JSONObject original=content("规则",7).put("sources",new JSONArray().put(7).put(8)).put("images",new JSONArray().put("image-1.jpg"));
        JSONArray first=new JSONArray().put(original),extras=new JSONArray().put(new JSONObject(original.toString()))
                .put(new JSONObject(original.toString()).put("sources",new JSONArray().put(7)).put("images",new JSONArray()))
                .put(new JSONObject(original.toString()).put("sources",new JSONArray().put(7).put(8).put(9)))
                .put(new JSONObject(original.toString()).put("images",new JSONArray().put("image-2.jpg")))
                .put(new JSONObject(original.toString()).put("text","不同事实"))
                .put(new JSONObject(original.toString()).put("evidence","新字段不忽略"));
        String savedFirst=first.toString(),savedExtras=extras.toString();JSONArray combined=ClassroomEnhancedSummary.appendUncovered(first,extras);
        check(combined.length()==5&&combined.getJSONObject(0).similar(original),"only exact content with already-covered references is removed; new source/image/fact/field remains");
        check(first.toString().equals(savedFirst)&&extras.toString().equals(savedExtras),"echo removal never mutates either stage checkpoint or raw response arrays");
        combined.getJSONObject(0).getJSONArray("sources").put(99);
        check(first.toString().equals(savedFirst),"publication owns a deep copy rather than altering the immutable first-stage data");
    }
    private static JSONObject plannedCatalogue(String prompt)throws Exception{
        String marker="\nPPT时间目录：";int begin=prompt.lastIndexOf(marker)+marker.length(),end=prompt.indexOf("\n本轮执行要求：",begin);
        return new JSONObject(prompt.substring(begin,end<0?prompt.length():end));
    }
    private static void compactCorrectionCaches()throws Exception{
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES))for(int committed:new int[]{2,4})for(boolean withImages:new boolean[]{false,true}){
            reset("normal",withImages);File dir=materials("compact-cached-legacy-"+committed+"-"+protocol+"-"+withImages,4,true);failCall=committed+1;rejects(()->generateWithProtocol(dir,protocol),"prepare a real committed legacy correction prefix before compact regeneration");JSONObject before=state(dir);check(before.getJSONArray("blocks").length()==4&&before.getJSONArray("corrected").length()==committed,"cache fixture retains its original four 6000-char blocks and exact successful prefix");ClassroomCorrectionCache.capture(dir);Files.delete(new File(dir,"run.json").toPath());reset("compact-normal",withImages);JSONObject result=compactGenerate(dir,protocol),after=state(dir);List<String> expected=new ArrayList<>();for(int n=committed;n<4;n++)expected.add("correct-"+n);if(withImages)expected.add("image-plan");expected.add("whole-note");check(result.getBoolean("complete")&&after.getBoolean("compact_pipeline")&&stages.equals(expected)&&after.getJSONArray("blocks").toString().equals(before.getJSONArray("blocks").toString())&&after.getString("correction_reused_from").equals(before.getString("run")),"compact factory keeps valid legacy block identity and only requests uncommitted corrections: "+committed+" "+protocol);for(int n=0;n<committed;n++)check(after.getJSONArray("corrected").getString(n).equals(before.getJSONArray("corrected").getString(n)),"every previously committed legacy correction remains byte-for-byte unchanged");int copies=0;for(JSONObject request:requests)copies+=occurrences(prompt(request),after.getJSONArray("corrected").toString());check(copies==1&&prompt(requests.get(stages.indexOf(withImages?"image-plan":"whole-note"))).contains(after.getJSONArray("corrected").toString()),"cached corrections are appended verbatim exactly once: in the model image plan or directly in the whole note when planning is local");batchHistory(dir,protocol);evidence("compact-cache-legacy-"+committed+"-"+protocol+"-"+withImages,dir);
        }
        reset("compact-normal",false);File whole=compactMaterials("compact-cache-whole");compactGenerate(whole,AiSettings.CHAT_COMPLETIONS);JSONObject before=state(whole);ClassroomCorrectionCache.capture(whole);JSONObject captured=ClassroomStore.read(new File(whole,"correction-cache.json"));check(captured.getJSONArray("blocks").similar(before.getJSONArray("blocks"))&&captured.getJSONArray("parts").similar(before.getJSONArray("corrected")),"capture accepts the new whole-lesson correction layout rather than discarding it as a legacy mismatch");Files.delete(new File(whole,"run.json").toPath());reset("compact-normal",false);compactGenerate(whole,AiSettings.CHAT_COMPLETIONS);check(stages.equals(List.of("whole-note"))&&state(whole).getJSONArray("corrected").similar(before.getJSONArray("corrected")),"a completed new 60000-char correction cache restarts the compact notebook with no correction request");appendOnly(whole);evidence("compact-cache-whole",whole);
        for(String stale:List.of("incomplete","invalid","mismatched-summary"))for(int committed:new int[]{1,4}){
            File dir=materials("compact-cache-stale-whole-"+stale+"-"+committed,4,true);JSONArray all=ClassroomSummary.lessonInput(ClassroomStore.read(new File(dir,"materials.json")).getJSONArray("segments"),new JSONArray()).getJSONArray("segments"),legacy=ClassroomEnhancedSummary.blocks(all),layout=ClassroomEnhancedSummary.blocks(all,60000),valid=new JSONArray();for(int n=0;n<committed;n++)valid.put("LEGACY_COMMITTED_"+n);ClassroomStore.write(new File(dir,"correction-cache.json"),new JSONObject().put("blocks",legacy).put("parts",valid).put("source_run","valid-legacy"));ClassroomStore.write(new File(dir,"corrected.json"),new JSONObject().put("run","stale-whole").put("blocks",layout).put("parts",new JSONArray().put(stale.equals("invalid")?new JSONObject().put("uncommitted",true):"STALE_WHOLE")).put("complete",!stale.equals("incomplete")));ClassroomStore.write(new File(dir,"summary.json"),new JSONObject().put("run",stale.equals("mismatched-summary")?"different-run":"stale-whole").put("source_blocks",layout.length()));ClassroomCorrectionCache.capture(dir);JSONObject cache=ClassroomStore.read(new File(dir,"correction-cache.json"));check(cache.getJSONArray("blocks").similar(legacy)&&cache.getJSONArray("parts").similar(valid)&&cache.getString("source_run").equals("valid-legacy")&&ClassroomCorrectionCache.compactBlocks(dir,all).similar(legacy),"invalid/incomplete/mismatched whole corrected.json cannot discard a valid partial or complete legacy cache: "+stale+" "+committed);
        }
        File reverse=materials("compact-cache-valid-whole-stale-legacy",4,true);JSONArray all=ClassroomSummary.lessonInput(ClassroomStore.read(new File(reverse,"materials.json")).getJSONArray("segments"),new JSONArray()).getJSONArray("segments"),layout=ClassroomEnhancedSummary.blocks(all,60000),legacy=ClassroomEnhancedSummary.blocks(all),valid=new JSONArray().put("VALID_WHOLE_CORRECTION");ClassroomStore.write(new File(reverse,"correction-cache.json"),new JSONObject().put("blocks",layout).put("parts",valid).put("source_run","valid-whole"));ClassroomStore.write(new File(reverse,"corrected.json"),new JSONObject().put("run","stale-legacy").put("blocks",legacy).put("parts",new JSONArray().put("STALE_LEGACY")).put("complete",false));ClassroomStore.write(new File(reverse,"summary.json"),new JSONObject().put("run","stale-legacy").put("source_blocks",legacy.length()));ClassroomCorrectionCache.capture(reverse);JSONObject cache=ClassroomStore.read(new File(reverse,"correction-cache.json"));check(cache.getJSONArray("blocks").similar(layout)&&cache.getJSONArray("parts").similar(valid)&&cache.getString("source_run").equals("valid-whole")&&ClassroomCorrectionCache.compactBlocks(reverse,all).similar(layout),"invalid legacy corrected.json likewise cannot discard the valid new whole-layout cache");ClassroomStore.write(new File(reverse,"run.json"),new JSONObject().put("run","current-invalid-prior").put("blocks",layout).put("corrected",new JSONArray().put(new JSONObject().put("notCommittedText",true))));ClassroomCorrectionCache.capture(reverse);check(ClassroomStore.read(new File(reverse,"correction-cache.json")).getJSONArray("parts").length()==0,"a present invalid current correction run remains authoritative and cannot fall back to older paid text");
    }
    private static JSONObject fixtureRef(JSONArray parts,String ref)throws Exception{String[] path=ref.split(":");return parts.getJSONObject(Integer.parseInt(path[0])).getJSONArray(path[1]).getJSONObject(Integer.parseInt(path[2]));}
    private static JSONObject generateProjection(File dir,String protocol)throws Exception{try(RequestControl control=new RequestControl(20000)){return expandedGenerate(dir,LESSON,new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol),40,control,p->{},"保留题干条件及步骤分",true,3);}}
    private static JSONArray projectedRows(JSONObject input,JSONArray all)throws Exception{
        Set<Integer> cited=new HashSet<>(),positions=new TreeSet<>();JSONArray ids=input.getJSONArray("sources");for(int n=0;n<ids.length();n++)cited.add(ids.getInt(n));for(int n=0;n<all.length();n++)if(cited.contains(all.getJSONObject(n).getInt("id")))for(int k=Math.max(0,n-1);k<=Math.min(all.length()-1,n+1);k++)positions.add(k);
        JSONArray expected=new JSONArray();for(int position:positions)expected.put(all.getJSONObject(position));return ClassroomSummary.uploadChunk(new JSONObject().put("segments",expected).put("images",new JSONArray())).getJSONArray("segments");
    }
    private static JSONArray editorialInputs(String prompt)throws Exception{
        String batch="本组主题清单及提取稿：\n",single="本主题提取稿：\n";JSONArray inputs=new JSONArray();if(prompt.contains(batch)){JSONArray topics=new JSONArray(prompt.substring(prompt.indexOf(batch)+batch.length()));for(int n=0;n<topics.length();n++){JSONArray rows=topics.getJSONObject(n).getJSONArray("inputs");for(int k=0;k<rows.length();k++)inputs.put(rows.getJSONObject(k));}}else inputs=new JSONArray(prompt.substring(prompt.indexOf(single)+single.length()));return inputs;
    }
    private static JSONObject editorialShared(String prompt)throws Exception{String marker="\n对应原始字幕（用于核对教师事实、指令方向及与更正文稿的冲突，不仅用于安排引文定位）：\n";int from=prompt.indexOf(marker)+marker.length();return new JSONObject(prompt.substring(from,prompt.indexOf("\n原字幕是教师事实依据",from)));}
    private static JSONArray sharedRows(JSONArray inputs,JSONArray all)throws Exception{JSONArray ids=new JSONArray();for(int n=0;n<inputs.length();n++){JSONArray refs=inputs.getJSONObject(n).getJSONArray("sources");for(int k=0;k<refs.length();k++)ids.put(refs.getInt(k));}return projectedRows(new JSONObject().put("sources",ids),all);}
    private static void sourceProjection()throws Exception{
        JSONArray sparse=new JSONArray();for(int n=0;n<5;n++)sparse.put(new JSONObject().put("id",10+20*n).put("start",n*10).put("end",n*10+5).put("text","原字 "+n+" 引号\"和换行\n保留。"));
        JSONObject note=content("知识主题",50).put("text","PRIVATE_UNSUPPORTED_MATH ![只在图中的知识](images/image-7.jpg)").put("images",new JSONArray().put("images/image-8.jpg")).put("explanation","PRIVATE_DERIVED_EXPLANATION").put("solution",new JSONObject().put("claim","PRIVATE_DERIVED_SOLUTION"));String before=note.toString()+sparse;
        JSONObject reference=ClassroomEnhancedSummary.editorialReference(note);JSONArray references=new JSONArray().put(reference).put(new JSONObject(reference.toString()).put("sources",new JSONArray().put(70)));String context=ClassroomEnhancedSummary.editorialContext(references,sparse);JSONArray rows=new JSONObject(context).getJSONArray("segments");
        check(reference.keySet().equals(Set.of("title","sources","images","origin"))&&!context.contains("PRIVATE_")&&reference.getJSONArray("images").toString().equals("[\"image-8.jpg\",\"image-7.jpg\"]")&&before.equals(note.toString()+sparse),"reference whitelist excludes arbitrary derived text/explanation/solution, preserves inline image identity, and leaves original objects untouched");
        check(rows.length()==4&&rows.getJSONObject(0).getInt("id")==30&&rows.getJSONObject(3).getInt("id")==90&&rows.similar(sharedRows(references,sparse))&&ClassroomEnhancedSummary.editorialContext(new JSONArray().put(reference).put(reference),sparse).equals(ClassroomEnhancedSummary.editorialContext(new JSONArray().put(reference),sparse)),"one shared pool preserves original words, sparse identity, chronology and neighbours, counting overlapping sources exactly once");
        reference.getJSONArray("sources").put(999);check(note.getJSONArray("sources").toString().equals("[50]")&&before.equals(note.toString()+sparse),"reference sources are a deep copy rather than a mutable alias into saved upstream material");
        JSONArray budgetNotes=new JSONArray().put(content("短派生主题一",10)).put(content("短派生主题二",30)).put(content("短派生主题三",50)),budgetParts=new JSONArray().put(new JSONObject().put("notes",budgetNotes)),budgetSections=new JSONArray().put(section("notes","0:notes:0")).put(section("notes","0:notes:1")).put(section("notes","0:notes:2"));JSONArray longRaw=new JSONArray(sparse.toString());for(int n=0;n<longRaw.length();n++)longRaw.getJSONObject(n).put("text","原".repeat(8000));
        check(ClassroomEnhancedSummary.editorBatchEnd(budgetParts,budgetSections,0,3,18000,false,longRaw,true)==1&&ClassroomEnhancedSummary.editorBatchEnd(budgetParts,budgetSections,0,3,18000,false,longRaw,false)==3,"unique original pool, not short derived prose, determines new budgets while legacy batching remains unchanged");
        int exact=ClassroomEnhancedSummary.editorialContext(new JSONArray().put(budgetNotes.getJSONObject(0)).put(budgetNotes.getJSONObject(1)),longRaw).length();check(ClassroomEnhancedSummary.editorBatchEnd(budgetParts,budgetSections,0,3,exact,false,longRaw,true)==2&&ClassroomEnhancedSummary.editorBatchEnd(budgetParts,budgetSections,0,3,exact-1,false,longRaw,true)==1,"shared-pool UTF16 length joins at the exact budget and separates one character below it");
        JSONArray duplicates=new JSONArray().put(section("notes","0:notes:0")).put(section("notes","0:notes:0")).put(section("notes","0:notes:0")).put(section("notes","0:notes:0"));check(ClassroomEnhancedSummary.editorBatchEnd(budgetParts,duplicates,0,3,18000,false,longRaw,true)==3,"three same-source themes count one 16000-character pool instead of three copies, while preserving the three-theme cap");
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES)){
            reset("projection-mixed",true);File dir=materials("source-projection-new-"+protocol,4,true);failCall=5;rejects(()->generateProjection(dir,protocol),"capture four completed corrections before source-first regeneration");JSONObject prior=state(dir);ClassroomCorrectionCache.capture(dir);Files.delete(new File(dir,"run.json").toPath());reset("projection-mixed",true);JSONObject result=generateProjection(dir,protocol),run=state(dir);JSONArray all=ClassroomSummary.lessonInput(ClassroomStore.read(new File(dir,"materials.json")).getJSONArray("segments"),new JSONArray()).getJSONArray("segments"),parts=run.getJSONArray("parts"),sections=run.getJSONObject("editor_plan").getJSONArray("sections");
            check(run.getBoolean("editorial_source_first")&&result.getBoolean("complete")&&run.getJSONArray("corrected").toString().equals(prior.getJSONArray("corrected").toString())&&run.getJSONArray("blocks").toString().equals(prior.getJSONArray("blocks").toString())&&run.getString("correction_reused_from").equals(prior.getString("run"))&&stages.stream().noneMatch(s->s.startsWith("correct-")),"new shared projection reuses all four correction blocks without a correction request: "+protocol);
            String planPrompt=prompt(requests.get(stages.indexOf("edit-plan"))),marker="已完成笔记索引：\n",poolMarker="\n编排原始字幕集合：\n";JSONArray index=new JSONArray(planPrompt.substring(planPrompt.indexOf(marker)+marker.length(),planPrompt.indexOf(poolMarker)));JSONObject planPool=new JSONObject(planPrompt.substring(planPrompt.indexOf(poolMarker)+poolMarker.length()));Set<String> expectedRefs=new HashSet<>(),seen=new HashSet<>();for(int p=0;p<parts.length();p++)for(String collection:List.of("notes","affairs"))for(int n=0;n<parts.getJSONObject(p).getJSONArray(collection).length();n++)expectedRefs.add(p+":"+collection+":"+n);
            check(!planPrompt.contains("PRIVATE_DERIVED_")&&index.length()==8&&occurrences(planPrompt,poolMarker)==1&&planPool.getJSONArray("segments").similar(sharedRows(index,all))&&planPool.getJSONArray("segments").length()==4,"actual planning request carries all original rows in one deduplicated pool and no repeated derived errors");
            for(int n=0;n<index.length();n++){JSONObject row=index.getJSONObject(n),original=fixtureRef(parts,row.getString("ref"));check(seen.add(row.getString("ref"))&&row.keySet().equals(Set.of("title","sources","images","origin","ref","collection"))&&row.getString("origin").equals("original_subtitles")&&row.getString("title").equals(original.getString("title"))&&row.getJSONArray("sources").similar(original.getJSONArray("sources"))&&row.getJSONArray("images").similar(original.getJSONArray("images")),"plan ref/title/source/image/classification survives without text or unknown derived fields: "+row.getString("ref"));}check(seen.equals(expectedRefs),"every notes and affairs reference reaches planning exactly once");
            int editorialCount=0;Set<String> editedRefs=new HashSet<>();for(int r=0;r<requests.size();r++)if(stages.get(r).startsWith("edit-note-")){
                editorialCount++;String physical=prompt(requests.get(r));JSONArray inputs=editorialInputs(physical);JSONObject pool=editorialShared(physical);int start=Integer.parseInt(stages.get(r).substring("edit-note-".length())),end=ClassroomEnhancedSummary.editorBatchEnd(parts,sections,start,3,18000,true,all,true),at=0;JSONArray expectedInputs=new JSONArray();
                for(int s=start;s<end;s++){JSONArray refs=sections.getJSONObject(s).getJSONArray("refs");for(int k=0;k<refs.length();k++){editedRefs.add(refs.getString(k));JSONObject original=fixtureRef(parts,refs.getString(k)),supplied=inputs.getJSONObject(at++);expectedInputs.put(original);check(supplied.keySet().equals(Set.of("title","sources","images","origin"))&&supplied.getString("origin").equals("original_subtitles")&&supplied.getString("title").equals(original.getString("title"))&&supplied.getJSONArray("sources").similar(original.getJSONArray("sources"))&&supplied.getJSONArray("images").similar(original.getJSONArray("images")),"actual knowledge/affairs input preserves only original reference metadata");}}
                check(!physical.contains("PRIVATE_DERIVED_")&&pool.getJSONArray("segments").similar(sharedRows(expectedInputs,all))&&occurrences(physical,pool.toString())==1&&(end==start+1||pool.toString().length()<=18000),"each real editor request carries one exact chronological neighbour pool, counts it once, and fits the existing budget");
                String imageMarker="\n本组引用图片的已有可见内容核对：";int imageStart=physical.indexOf(imageMarker)+imageMarker.length();JSONArray visible=new JSONArray(physical.substring(imageStart,physical.indexOf("。准入",imageStart)));check(visible.similar(ClassroomEnhancedSummary.topicFigureEvidence(inputs,run.optJSONArray("figure_reviews")))&&physical.contains("原题无法确定时可换用同主线、同类型及相近难度的完整题目")&&physical.contains("补充例题")&&!physical.contains("本组原提取稿正文合计"),"shared context preserves actual image-visible evidence and labelled replacement examples without a lower-strength length restriction");
            }
            check(editedRefs.equals(expectedRefs)&&editorialCount==4&&run.getInt("editor_section_done")==sections.length()&&run.getJSONArray("editor_notes").length()==8&&result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").length()==8,"shared pooling edits each of eight themes, allows affairs-to-knowledge correction, and batches same-source notes without duplicated context cost");
            check(result.toString().contains("合成图的黑白网格")&&!ClassroomStore.read(new File(dir,"materials.json")).toString().contains("黑白网格")&&parts.toString().contains("PRIVATE_DERIVED_CLAIM_")&&parts.toString().contains("PRIVATE_DERIVED_WRONG_AFFAIR_")&&stages.stream().allMatch(s->s.matches("(?:draft|final|edit-note)-[0-9]+|edit-plan|edit-affairs")),"image-only unique knowledge and every paid upstream checkpoint survive without a new AI stage type");batchHistory(dir,protocol);evidence("source-projection-new-"+protocol,dir);
            for(int strength:new int[]{1,2}){reset("normal",false);File bounded=editorialFixture(protocol,new int[]{20,30},false,true);JSONObject frozen=state(bounded);frozen.put("editorial_source_first",true).put("note_strength",strength).put("config",ClassroomSummary.configKey(AI,0,"保留题干条件及步骤分",true,strength,1,1));ClassroomStore.write(new File(bounded,"run.json"),frozen);generateWithProtocol(bounded,protocol);String physical=prompt(requests.get(0));check(requests.size()==1&&physical.contains("本组原提取稿正文合计50个UTF16字符，仅用此数值控制成品长度，不超过该合计")&&!physical.contains("甲".repeat(20))&&!editorialInputs(physical).getJSONObject(0).has("text"),"strength "+strength+" receives an explicit correct original-body length budget without forwarding the old prose: "+protocol);batchHistory(bounded,protocol);evidence("source-projection-strength-"+strength+"-"+protocol,bounded);}
            for(boolean explicitFalse:new boolean[]{false,true}){
                reset("normal",false);File old=editorialFixture(protocol,new int[]{20,30,40,50},false,true);JSONObject frozen=state(old);if(explicitFalse)frozen.put("editorial_source_first",false);ClassroomStore.write(new File(old,"run.json"),frozen);failCall=2;rejects(()->generateWithProtocol(old,protocol),"old projected-text contract interruption remains resumable");JSONObject checkpoint=state(old),history=new JSONObject(checkpoint.getJSONObject("conversation").toString());String savedParts=checkpoint.getJSONArray("parts").toString(),savedOptions=history.getJSONObject("options").toString();int sent=requests.size();failCall=-1;JSONObject restored=generateWithProtocol(old,protocol),after=state(old);check(restored.getBoolean("complete")&&requests.size()==sent+1&&stages.equals(List.of("edit-note-0","edit-note-2","edit-note-2-restart-1"))&&after.getJSONObject("conversation").getJSONObject("options").toString().equals(savedOptions)&&after.getJSONArray("parts").toString().equals(savedParts)&&(explicitFalse?!after.getBoolean("editorial_source_first"):!after.has("editorial_source_first")),"old absent/false flag preserves derived-input batching, saved options and unchanged upstream work: "+protocol+" "+explicitFalse);for(int r=0;r<requests.size();r++){JSONArray supplied=editorialInputs(referencedPrompt(requests.get(r),prompt(requests.get(r))));for(int n=0;n<supplied.length();n++)check(!supplied.getJSONObject(n).has("origin")&&supplied.getJSONObject(n).getString("text").matches("甲+"),"old run still sends its original derived prose instead of silently acquiring new projection");}revalidationHistory(history,after.getJSONObject("conversation"),true);batchHistory(old,protocol);evidence("source-projection-legacy-"+explicitFalse+"-"+protocol,old);
            }
        }
    }
    private static void policyPrefix()throws Exception{
        String policy=ClassroomAffairsPolicy.instructions(1);
        check(!policy.isEmpty()&&ClassroomAffairsPolicy.instructions(0).isEmpty(),"affairs policy remains versioned and absent from legacy schema zero");
        for(int strength:new int[]{1,2,3})check(occurrences(ClassroomSummary.systemPrompt("",strength),policy)==1,"factory system contains the full shared policy once at every unchanged strength: "+strength);
        check(ClassroomNoteStrength.DEFAULT==3&&ClassroomNoteStrength.instructions(3).contains("说明关键步骤为什么成立")&&ClassroomNoteStrength.instructions(3).contains("指出错误时说明真正失效的前提"),"current preferred strength explains valid steps and locates invalid premises across subjects");
        for(String schema:List.of(ClassroomSummary.schema(false,1),ClassroomSummary.schema(true,1),ClassroomSummary.topicSchema(1,"notes"),ClassroomSummary.topicSchema(1,"affairs"),ClassroomSummary.topicBatchSchema(1),ClassroomSummary.draftSchema(new JSONObject().put("count",1),1,true,1))){
            check(!schema.contains(policy),"stage format retains its local contract without repeating the full shared affairs policy");for(String field:List.of("overview","notes","affairs","assignments","sources","images"))check(schema.contains("\""+field+"\""),"stage examples retain existing field "+field);
        }
        check(ClassroomSummary.schema(true,1).contains("\"text\":\"完整知识笔记\"")&&!ClassroomSummary.schema(true,1).contains("详细笔记"),"knowledge example describes completeness without requesting extra verbosity");
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES)){
            for(String fixture:List.of("affairs-mixed","task-normal")){
                reset(fixture,true);File dir=materials("policy-prefix-"+fixture+"-"+protocol,false);JSONObject result=generateWithProtocol(dir,protocol),run=state(dir),doc=result.getJSONArray("parts").getJSONObject(0);String expectedSystem=ClassroomSummary.systemPrompt("保留题干条件及步骤分",1);
                check(result.getBoolean("complete")&&doc.getJSONArray("notes").length()==1&&doc.getJSONArray("affairs").length()==(fixture.equals("affairs-mixed")?1:0)&&doc.getJSONArray("assignments").length()==(fixture.equals("task-normal")?1:0),"real new-run factory retains separate learning, affairs and action results: "+fixture+" "+protocol);
                for(int n=0;n<requests.size();n++){
                    JSONArray sent=messages(requests.get(n));String system=sent.getJSONObject(0).getString("content");check(system.equals(expectedSystem)&&occurrences(system,policy)==1,"every actual request keeps the exact stable factory system with one policy copy: "+protocol+" "+stages.get(n));
                    for(int m=1;m<sent.length();m++)if(sent.getJSONObject(m).optString("role").equals("user"))check(!sent.getJSONObject(m).getJSONArray("content").getJSONObject(0).getString("text").contains(policy),"stage/user history never repeats the complete policy: "+protocol+" "+stages.get(n));
                    if(!stages.get(n).startsWith("correct-")){JSONObject shapes=run.getJSONObject("response_shapes");String logical=stages.get(n);JSONObject shape=shapes.getJSONObject(logical);Set<String> actual=shape.getJSONObject("schema").getJSONObject("properties").keySet();Set<String> expected=logical.equals("edit-plan")?Set.of("overview","sections"):logical.equals("edit-assignments")?Set.of("groups"):logical.equals("edit-affairs")?Set.of("affairs"):logical.startsWith("draft-")?Set.of("overview","notes","affairs","assignments","image_assessment","image_requests"):logical.startsWith("final-")?Set.of("overview","notes","affairs","assignments","image_reviews"):Set.of("overview","notes","affairs","assignments");check(actual.equals(expected),"native stage grammar retains every existing root field without policy duplication: "+logical);}
                }
                batchHistory(dir,protocol);evidence("policy-prefix-"+fixture+"-"+protocol,dir);
            }
            reset("affairs-mixed",false);File old=previousContentMaterials("policy-prefix-saved-"+protocol,protocol);JSONObject before=state(old);String savedSystem="旧已发送系统前缀\n"+policy+"\n旧冻结末尾";before.getJSONObject("conversation").getJSONArray("messages").getJSONObject(0).put("content",savedSystem);ClassroomStore.write(new File(old,"run.json"),before);String options=before.getJSONObject("conversation").getJSONObject("options").toString(),config=before.getString("config");JSONObject resumed=generateWithProtocol(old,protocol),after=state(old);
            check(resumed.getBoolean("complete")&&resumed.getString("run").equals(before.getString("run"))&&after.getString("config").equals(config)&&after.getJSONObject("conversation").getJSONObject("options").toString().equals(options),"saved run identity, config and frozen options survive new policy placement: "+protocol);
            for(JSONObject request:requests)check(messages(request).getJSONObject(0).getString("content").equals(savedSystem),"old already-sent system prefix is never replaced by today's factory: "+protocol);batchHistory(old,protocol);evidence("policy-prefix-saved-"+protocol,old);
            reset("normal",false);File zero=previousContentMaterials("policy-prefix-legacy-zero-"+protocol,protocol);JSONObject zeroRun=state(zero);zeroRun.put("affairs_schema",0).put("config",ClassroomSummary.configKey(AI,0,"保留题干条件及步骤分",true,1,1,0));zeroRun.getJSONObject("conversation").getJSONArray("messages").getJSONObject(0).put("content","原有schema零系统，未包含新事务契约");ClassroomStore.write(new File(zero,"run.json"),zeroRun);JSONObject zeroResult=generateWithProtocol(zero,protocol);check(zeroResult.getInt("affairs_schema")==0&&!zeroResult.getJSONArray("parts").getJSONObject(0).has("affairs")&&!stages.contains("edit-affairs"),"legacy schema zero preserves its original fields and stage behavior: "+protocol);batchHistory(zero,protocol);
        }
    }
    private static void unifiedAffairs()throws Exception{
        JSONArray raw=new JSONArray();for(int n=0;n<5;n++)raw.put(new JSONObject().put("id",10+n*20).put("start",n*10).put("end",n*10+5).put("text","原始事务原话 "+n));
        JSONObject first=content("候选事务",30).put("text","PRIVATE_DERIVED_UNSUPPORTED").put("images",new JSONArray().put("image-7.jpg"));
        JSONArray parts=new JSONArray().put(new JSONObject().put("affairs",new JSONArray().put(first))),edited=new JSONArray().put(new JSONObject(first.toString()).put("collection","affairs").put("sources",new JSONArray().put(50)));
        JSONArray reviews=new JSONArray().put(new JSONObject().put("file","image-7.jpg").put("included",true).put("visible","图中的明确原话"));String original=parts.toString()+edited+raw+reviews;
        JSONObject projected=ClassroomEnhancedSummary.affairsEvidence(parts,edited,raw,reviews);JSONArray rows=projected.getJSONObject("original_subtitles").getJSONArray("segments");
        check(projected.keySet().equals(Set.of("original_subtitles","images","visible"))&&!projected.toString().contains("PRIVATE_DERIVED_UNSUPPORTED")&&!projected.toString().contains("候选事务"),"whole-affair evidence excludes derived titles and prose");
        check(rows.similar(ClassroomSummary.uploadChunk(new JSONObject().put("segments",raw).put("images",new JSONArray())).getJSONArray("segments")),"whole-affair evidence includes every original row, with exact text and sparse source identity independent of candidate coverage");
        check(projected.getJSONArray("images").toString().equals("[\"image-7.jpg\"]")&&projected.getJSONArray("visible").getJSONObject(0).getString("visible").equals("图中的明确原话"),"whole-affair evidence preserves actual image identity and visible evidence");
        check(original.equals(parts.toString()+edited+raw+reviews),"whole-affair projection never mutates old inputs");
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES)){
            reset("unified-duplicate",true);File dir=materials("whole-affairs-"+protocol,false);JSONObject result=generateWithProtocol(dir,protocol),run=state(dir),doc=result.getJSONArray("parts").getJSONObject(0);
            check(run.getBoolean("unified_affairs")&&Collections.frequency(stages,"edit-affairs")==1&&stages.get(stages.size()-1).equals("edit-affairs"),"new run performs one final affair integration after topic editing: "+protocol);
            JSONObject preservedNote=new JSONObject(run.getJSONArray("editor_notes").getJSONObject(0).toString());preservedNote.remove("collection");
            check(doc.getJSONArray("notes").length()==1&&doc.getJSONArray("notes").getJSONObject(0).similar(preservedNote)&&doc.getJSONArray("assignments").length()==1&&doc.getJSONArray("assignments").similar(run.getJSONArray("editor_assignments"))&&doc.getJSONArray("affairs").length()==1&&run.getJSONArray("editor_notes").length()>doc.getJSONArray("affairs").length(),"knowledge-editor and original affair duplicates publish once while knowledge/actions stay exact: "+protocol);
            JSONObject affair=doc.getJSONArray("affairs").getJSONObject(0);check(affair.keySet().equals(Set.of("title","text","sources","images"))&&affair.getJSONArray("sources").toString().equals("[0]")&&affair.getJSONArray("images").toString().equals("[\"image-0.jpg\"]")&&affair.getString("text").contains("符号错误扣1分")&&!affair.getString("text").contains("PRIVATE_DERIVED_DUPLICATE"),"integrated result retains teacher detail and original source/image references without derived repetitions");
            int sent=requests.size();JSONObject history=new JSONObject(run.getJSONObject("conversation").toString());run.remove("final");ClassroomStore.write(new File(dir,"run.json"),run);JSONObject restored=generateWithProtocol(dir,protocol);
            check(restored.getBoolean("complete")&&requests.size()==sent&&state(dir).getJSONObject("conversation").toString().equals(history.toString()),"completed affair checkpoint reassembles with zero extra requests or history changes: "+protocol);batchHistory(dir,protocol);evidence("unified-affairs-"+protocol,dir);
            for(String defect:List.of("unified-invalid-source","unified-invalid-image","network")){
                reset("unified-duplicate",true);File failed=materials("whole-affairs-fail-"+defect+"-"+protocol,false);mode=defect.equals("network")?"unified-duplicate":defect;if(defect.equals("network"))failCall=7;
                rejects(()->generateWithProtocol(failed,protocol),"invalid/interrupted final affair response leaves resumable checkpoint: "+defect);JSONObject checkpoint=state(failed);int before=requests.size();
                check(!checkpoint.has("editor_affairs")&&!checkpoint.has("final")&&checkpoint.getJSONArray("editor_notes").length()>0,"failed final integration never commits final affairs or final document: "+defect);
                JSONObject partial=ClassroomStore.read(new File(failed,"summary.json"));check(!partial.optBoolean("complete"),"failed integration cannot publish a complete summary: "+defect);
                String notes=checkpoint.getJSONArray("editor_notes").toString(),source=checkpoint.getJSONArray("parts").toString();mode="unified-duplicate";failCall=-1;JSONObject recovered=generateWithProtocol(failed,protocol);
                check(recovered.getBoolean("complete")&&requests.size()==before+1&&stages.get(before).startsWith("edit-affairs")&&state(failed).getJSONArray("editor_notes").toString().equals(notes)&&state(failed).getJSONArray("parts").toString().equals(source),"resume pays only final affair stage and preserves completed upstream checkpoints: "+defect);batchHistory(failed,protocol);
            }
            reset("normal",false);File noAffairs=materials("whole-affairs-empty-"+protocol,false);JSONObject noAffairsResult=generateWithProtocol(noAffairs,protocol);check(Collections.frequency(stages,"edit-affairs")==1&&noAffairsResult.getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").length()==0,"non-affair lesson still scans original subtitles once and returns an empty saved collection: "+protocol);
            reset("missed-affair",false);File missed=materials("whole-affairs-missed-"+protocol,true);JSONObject missedMaterials=ClassroomStore.read(new File(missed,"materials.json"));missedMaterials.getJSONArray("segments").getJSONObject(1).put("text","独特原话：下周小测前完成这一节，常考的判断方法保留。评分时按原答题方向判定。");ClassroomStore.write(new File(missed,"materials.json"),missedMaterials);JSONObject missedResult=generateWithProtocol(missed,protocol),missedRun=state(missed);int finalIndex=stages.indexOf("edit-affairs");String finalPrompt=prompt(requests.get(finalIndex));JSONObject sentEvidence=new JSONObject(finalPrompt.substring(finalPrompt.indexOf("全课事务证据：\n")+"全课事务证据：\n".length()));
            check(missedRun.getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").length()==0&&missedRun.getJSONArray("editor_notes").getJSONObject(0).getString("collection").equals("notes")&&Collections.frequency(stages,"edit-affairs")==1,"missing upstream affair candidates never suppress the final original-subtitle scan: "+protocol);
            check(!sentEvidence.has("titles")&&sentEvidence.getJSONObject("original_subtitles").getJSONArray("segments").similar(ClassroomSummary.uploadChunk(ClassroomSummary.lessonInput(missedMaterials.getJSONArray("segments"),new JSONArray())).getJSONArray("segments"))&&missedResult.getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").getJSONObject(0).getString("text").contains("下周小测前"),"actual final request preserves the complete original lesson and publishes the previously missed teacher rule without title anchoring: "+protocol);batchHistory(missed,protocol);evidence("unified-affairs-missed-"+protocol,missed);
            reset("unified-duplicate",true);File rejected=materials("whole-affairs-rejected-only-"+protocol,false);failCall=7;rejects(()->generateWithProtocol(rejected,protocol),"prepare interrupted final affair checkpoint with genuinely analysed images");JSONObject rejectedRun=state(rejected);JSONArray admission=rejectedRun.getJSONArray("figure_reviews");for(int n=0;n<admission.length();n++)admission.getJSONObject(n).put("included",false);ClassroomStore.write(new File(rejected,"run.json"),rejectedRun);
            failCall=-1;mode="unified-rejected-only";int previous=requests.size();rejects(()->generateWithProtocol(rejected,protocol),"removing a rejected actual picture cannot commit an empty affair body");check(requests.size()==previous+1&&!state(rejected).has("editor_affairs")&&!state(rejected).has("final"),"post-filter validation leaves the whole affair stage uncommitted");mode="unified-duplicate";JSONObject repaired=generateWithProtocol(rejected,protocol);check(repaired.getBoolean("complete")&&requests.size()==previous+2&&stages.get(previous+1).startsWith("edit-affairs")&&repaired.getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").getJSONObject(0).getJSONArray("images").length()==0,"explicit continuation retries only final affairs, retains raw prose and removes rejected images");batchHistory(rejected,protocol);
            reset("normal",false);File legacy=editorialFixture(protocol,new int[]{20,30},true,false);generateWithProtocol(legacy,protocol);check(stages.equals(List.of("edit-note-0","edit-note-1"))&&!state(legacy).has("unified_affairs")&&!state(legacy).has("editor_affairs"),"absent old-run flag retains item-by-item historical editing without a new stage: "+protocol);batchHistory(legacy,protocol);
            reset("unified-empty",false);File empty=materials("whole-affairs-empty-result-"+protocol,false);JSONObject emptyResult=generateWithProtocol(empty,protocol);check(emptyResult.getJSONArray("parts").getJSONObject(0).getJSONArray("affairs").length()==0&&state(empty).getJSONArray("editor_affairs").length()==0,"empty integrated affairs is a valid saved replacement: "+protocol);
        }
        JSONObject fields=ClassroomResponseShape.contract("edit-affairs",1,1).getJSONObject("schema").getJSONObject("properties");check(fields.keySet().equals(Set.of("affairs"))&&fields.getJSONObject("affairs").getJSONObject("items").getJSONObject("properties").keySet().equals(Set.of("title","text","sources","images")),"final affairs native grammar uses only the established affairs item fields");structuralOnly(ClassroomResponseShape.contract("edit-affairs",1,1).getJSONObject("schema"));
    }
    private static void nativeSchemas()throws Exception{
        for(int affairs:new int[]{0,1})for(int figures:new int[]{0,1})for(String stage:List.of("first","draft-0","final","final-0","edit-plan","edit-note-0","edit-assignments","edit-affairs","image-plan","whole-note")){
            JSONObject contract=ClassroomResponseShape.contract(stage,affairs,figures),schema=contract.getJSONObject("schema"),fields=schema.getJSONObject("properties");
            check(contract.getBoolean("strict"),"wire grammar is explicitly strict: "+stage);structuralOnly(schema);
            if(stage.equals("first")||stage.startsWith("draft-")){
                check(fields.has("image_requests")&&!fields.getJSONObject("image_assessment").getJSONObject("properties").has("image_requests"),"requested image ranges are root siblings, never assessment members: "+stage);
                check(fields.has("affairs")== (affairs>0),"draft grammar follows the frozen affairs schema");
            }
            if(stage.equals("whole-note")||stage.equals("final")||stage.startsWith("final-"))check(fields.has("image_reviews")== (figures>0),"final grammar follows frozen image-review policy");
            if(fields.has("assignments"))check(fields.getJSONObject("assignments").getJSONObject("items").getJSONObject("properties").has("evidence")== (stage.equals("whole-note")||stage.matches("(?:draft|final)-[0-9]+")),"enhanced content generation, including one-shot whole-note, requests original action quotes");
        }
        check(ClassroomResponseShape.contract("draft-0",1,1).toString().equals(ClassroomResponseShape.contract("draft-19",1,1).toString()),"same-shape groups use identical schema names and content for cache reuse");
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES)){
            reset("normal",false);File old=previousContentMaterials("legacy-json-object-"+protocol,protocol);JSONObject oldState=state(old);oldState.put("native_json",true);ClassroomStore.write(new File(old,"run.json"),oldState);
            generateWithProtocol(old,protocol);
            check(!state(old).optBoolean("native_json_schema")&&!state(old).has("response_shapes"),"old json_object run never silently acquires schema output: "+protocol);
            for(int n=0;n<requests.size();n++)if(!stages.get(n).startsWith("correct-")){
                JSONObject request=requests.get(n);String type=protocol.equals(AiSettings.RESPONSES)?request.getJSONObject("text").getJSONObject("format").getString("type"):request.getJSONObject("response_format").getString("type");
                check(type.equals("json_object"),"old JSON stages retain exact json_object request mode: "+protocol);
            }
            reset("normal",false);File dir=materials("saved-schema-"+protocol,false);JSONObject state=new JSONObject().put("run","saved-schema-fixture").put("native_json",true).put("native_json_schema",true).put("affairs_schema",1).put("figure_policy",1);
            JSONObject frozen=ClassroomResponseShape.contract("draft-0",1,1).put("name","saved_prior_shape");state.put("response_shapes",new JSONObject().put("draft-0",new JSONObject(frozen.toString())));
            AiSettings service=new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol);JSONObject lecture=ClassroomSummary.lessonInput(ClassroomStore.read(new File(dir,"materials.json")).getJSONArray("segments"),new JSONArray());
            try(RequestControl control=new RequestControl(20000)){
                ClassroomConversation conversation=new ClassroomConversation(dir,state,new ChatCompletionClient(service,control),"SYNTHETIC"+ClassroomAffairsPolicy.instructions(1));ClassroomJsonStage json=new ClassroomJsonStage(dir,state,conversation,control);
                failCall=1;rejects(()->json.generate("draft-0","现在只整理第 1 段。JSON affairs",List.of(),value->ClassroomSummary.validateBlock(value,lecture,1),"-retry-","用户授权继续：",null),"native schema request interruption retains its logical checkpoint: "+protocol);
                failCall=-1;json.generate("draft-0","unused regenerated prompt",List.of(),value->ClassroomSummary.validateBlock(value,lecture,1),"-retry-","用户授权继续：",null);
            }
            check(requests.size()==2&&state.getJSONObject("response_shapes").getJSONObject("draft-0").toString().equals(frozen.toString()),"resume uses exact saved grammar, not a freshly rebuilt default: "+protocol);
            for(JSONObject request:requests){JSONObject format=protocol.equals(AiSettings.RESPONSES)?new JSONObject(request.getJSONObject("text").getJSONObject("format").toString()):request.getJSONObject("response_format").getJSONObject("json_schema");if(protocol.equals(AiSettings.RESPONSES))format.remove("type");check(format.toString().equals(frozen.toString()),"each real HTTP attempt retains the exact saved schema: "+protocol);}
            JSONArray before=messages(requests.get(0)),after=messages(requests.get(1));for(int n=0;n<before.length();n++)check(before.getJSONObject(n).toString().equals(after.getJSONObject(n).toString()),"schema recovery preserves every old message unchanged: "+protocol+" "+n);
        }
    }
    private static void groundedInputs()throws Exception{
        JSONArray all=new JSONArray();for(int n=0;n<5;n++)all.put(new JSONObject().put("id",10+n*20).put("start",n*10).put("end",n*10+5).put("text","原字幕 "+n+"：保留引号\"、换行\n与 ASR 原字。"));
        JSONObject note=content("PRIVATE_DERIVED_UNSUPPORTED_CLAIM",50).put("title","教师规则");String before=note.toString(),rawBefore=all.toString();
        JSONObject supplied=ClassroomEnhancedSummary.editorialInput(note,all,true);JSONArray evidence=new JSONObject(supplied.getString("text")).getJSONArray("segments");
        check(supplied.getString("origin").equals("original_subtitles")&&evidence.length()==3,"affair input uses original subtitle and immediate neighbours rather than derived prose");
        for(int n=0;n<3;n++)check(evidence.getJSONObject(n).getInt("id")==all.getJSONObject(n+1).getInt("id")&&evidence.getJSONObject(n).getString("text").equals(all.getJSONObject(n+1).getString("text")),"raw subtitle text and sparse IDs remain exact "+n);
        check(!supplied.getString("text").contains("PRIVATE_DERIVED_UNSUPPORTED_CLAIM")&&note.toString().equals(before)&&all.toString().equals(rawBefore),"projection removes unsupported derived prose only from new input, leaving saved material and history unchanged");
        check(ClassroomEnhancedSummary.editorialInput(note,all,false).toString().equals(before),"knowledge and legacy editorial inputs preserve their full existing body");
        JSONArray union=ClassroomEnhancedSummary.unifiedAffairsInput(new JSONArray().put(note).put(new JSONObject(note.toString()).put("sources",new JSONArray().put(70))),all);
        JSONArray unique=new JSONObject(union.getJSONObject(0).getString("text")).getJSONArray("segments");
        check(union.length()==1&&unique.length()==4,"overlapping affair sources and neighbours form one chronological stream");
        for(int i=0;i<unique.length();i++)check(unique.getJSONObject(i).getInt("id")==all.getJSONObject(i+1).getInt("id"),"union preserves exact sparse original identity "+i);
        check(!union.toString().contains("PRIVATE_DERIVED_UNSUPPORTED_CLAIM")&&note.toString().equals(before)&&all.toString().equals(rawBefore),"unified evidence excludes derived prose and never mutates historical inputs");
        JSONObject inline=new JSONObject(note.toString()).put("text","依据 ![板书](images/image-7.jpg)").put("images",new JSONArray().put("images/image-8.jpg").put("image-8.jpg"));
        String inlineBefore=inline.toString();JSONObject projected=ClassroomEnhancedSummary.editorialInput(inline,all,true);
        check(projected.getJSONArray("images").length()==2&&projected.getJSONArray("images").toString().contains("image-7.jpg"),"original inline-only reference survives raw-source projection and explicit alias duplicates collapse");
        JSONArray imageUnion=ClassroomEnhancedSummary.unifiedAffairsInput(new JSONArray().put(projected).put(projected),all).getJSONObject(0).getJSONArray("images");
        check(imageUnion.length()==2&&inline.toString().equals(inlineBefore),"union retains inline and explicit images once without rewriting original prose");
        JSONArray parts=new JSONArray().put(new JSONObject().put("notes",new JSONArray().put(content("abcdefghij",50))).put("affairs",new JSONArray().put(content("abcdefghij",50))));
        JSONArray sections=new JSONArray();for(int n=0;n<7;n++)sections.put(new JSONObject().put("title","主题"+n).put("collection",n<2?"notes":"affairs").put("refs",new JSONArray().put(n<2?"0:notes:0":"0:affairs:0")));
        int rawChars=ClassroomEnhancedSummary.editorialInput(parts.getJSONObject(0).getJSONArray("affairs").getJSONObject(0),all,true).getString("text").length();
        check(ClassroomEnhancedSummary.editorBatchEnd(parts,sections,0,3,rawChars*5,true,all)==2,"new grouping keeps knowledge separate from raw-grounded affairs");
        check(ClassroomEnhancedSummary.editorBatchEnd(parts,sections,2,3,rawChars*5,true,all)==7,"compatible affairs fit in one bounded batch rather than repeat across arbitrary three-topic boundaries");
        check(ClassroomEnhancedSummary.editorBatchEnd(parts,sections,2,3,rawChars*2+1,true,all)==4,"whole-affair grouping budgets actual supplied raw text rather than short derived prose");
        JSONArray longRaw=new JSONArray().put(new JSONObject().put("id",50).put("start",0).put("end",5).put("text","原".repeat(18000)));
        check(ClassroomEnhancedSummary.editorBatchEnd(parts,sections,2,3,18000,true,longRaw)==3,"short derived affairs backed by oversized raw material stay standalone rather than silently over-batching");
        check(ClassroomEnhancedSummary.editorBatchEnd(parts,sections,0,3,200)==3,"legacy grouping retains its exact existing contract");
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES)){
            reset("affairs-only",false);File dir=materials("grounded-affairs-"+protocol,false);generateWithProtocol(dir,protocol);
            String sent=prompt(requests.get(stages.indexOf("edit-note-0"))),marker="本主题提取稿：\n";int from=sent.indexOf(marker)+marker.length();JSONArray inputs=new JSONArray(sent.substring(from,sent.indexOf("\n本组引用图片",from)));
            check(state(dir).getBoolean("affairs_source_first")&&inputs.getJSONObject(0).getString("origin").equals("original_subtitles"),"actual new-run editorial HTTP carries raw source projection: "+protocol);
            JSONArray raw=editorialShared(sent).getJSONArray("segments");
            check(raw.getJSONObject(0).getString("text").equals(ClassroomStore.read(new File(dir,"materials.json")).getJSONArray("segments").getJSONObject(0).getString("text")),"actual teacher facts use original material rather than repeated model claims: "+protocol);
            if(protocol.equals(AiSettings.RESPONSES))nativeAppendOnly(dir);else appendOnly(dir);
            reset("normal",false);File labelled=materials("named-images-"+protocol,false);JSONObject state=new JSONObject().put("run","label-fixture").put("native_json",true);
            AiSettings service=new AiSettings(AI.baseUrl,AI.model,AI.apiKey,AI.reasoningEffort,protocol);List<byte[]> bytes=new ArrayList<>();List<String> names=new ArrayList<>();
            for(int n=0;n<40;n++){BufferedImage image=new BufferedImage(4,4,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,(n+1)*12345);ByteArrayOutputStream output=new ByteArrayOutputStream();ImageIO.write(image,"jpg",output);bytes.add(output.toByteArray());names.add((n==0?"images/":"")+"image-"+(n*3+7)+".jpg");}
            try(RequestControl control=new RequestControl(20000)){
                ClassroomConversation conversation=new ClassroomConversation(labelled,state,new ChatCompletionClient(service,control),"SYNTHETIC");String original=state.toString();
                rejects(()->conversation.json("final-0","schema",bytes,List.of("image-7.jpg")),"mismatched new attachment labels fail before sending: "+protocol);
                rejects(()->conversation.json("final-0","schema",List.of(bytes.get(0)),List.of("../secret.jpg")),"invalid new attachment label fails before sending: "+protocol);
                check(requests.isEmpty()&&state.toString().equals(original),"invalid labels do not mutate durable history or call service: "+protocol);
                conversation.json("final-0","synthetic final JSON",bytes,names);
                JSONArray content=messages(requests.get(0)).getJSONObject(1).getJSONArray("content");check(content.length()==81,"forty images each have one adjacent identity text part: "+protocol);
                for(int n=0;n<40;n++){
                    JSONObject label=content.getJSONObject(n*2+1),image=content.getJSONObject(n*2+2);String file=ClassroomDocument.imageName(names.get(n));
                    check(label.getString("type").equals(protocol.equals(AiSettings.RESPONSES)?"input_text":"text")&&label.getString("text").contains("图片文件："+file+"；"),"adjacent label preserves nonsequential stable file "+n+" "+protocol);
                    String url=protocol.equals(AiSettings.RESPONSES)?image.getString("image_url"):image.getJSONObject("image_url").getString("url");
                    check(Arrays.equals(Base64.getDecoder().decode(url.substring(url.indexOf(',')+1)),bytes.get(n)),"label is adjacent to exact corresponding JPEG bytes "+n+" "+protocol);
                }
                JSONArray prefix=new JSONArray(messages(requests.get(0)).toString());conversation.json("followup","Return the next synthetic JSON object without more images.",List.of());
                JSONArray after=messages(requests.get(1));for(int n=0;n<prefix.length();n++)check(after.getJSONObject(n).toString().equals(prefix.getJSONObject(n).toString()),"labelled image prefix stays byte-for-byte unchanged in next turn "+n+" "+protocol);
                check(after.getJSONObject(after.length()-1).getJSONArray("content").length()==1,"later stage never reuploads image labels or attachments: "+protocol);
            }
        }
    }
    private static void nativeJsonModes()throws Exception{
        for(String protocol:List.of(AiSettings.CHAT_COMPLETIONS,AiSettings.RESPONSES)){
            reset("normal",true);File dir=materials("json-modes-"+protocol,true);JSONObject result=generateWithProtocol(dir,protocol),run=state(dir);
            check(result.getBoolean("complete")&&run.getBoolean("native_json"),"new run freezes native JSON mode: "+protocol);
            JSONObject options=run.getJSONObject("conversation").getJSONObject("options");
            check(!options.has("response_format")&&(!options.has("text")||!options.getJSONObject("text").has("format")),"frozen base options remain plain for correction: "+protocol);
            JSONArray turns=run.getJSONObject("conversation").getJSONArray("turns");
            for(int n=0;n<requests.size();n++){
                boolean json=!stages.get(n).startsWith("correct-");JSONObject request=requests.get(n);
                String format=protocol.equals(AiSettings.RESPONSES)?request.optJSONObject("text")==null?"":request.getJSONObject("text").getJSONObject("format").getString("type")
                        :request.optJSONObject("response_format")==null?"":request.getJSONObject("response_format").getString("type");
                check(format.equals(json?"json_schema":""),"only JSON stages use protocol-native structured mode: "+protocol+" "+stages.get(n));
                check(turns.getJSONObject(n).getString("output_mode").equals(json?"json_schema":"text"),"turn audits actual output mode: "+protocol+" "+stages.get(n));
            }
            if(protocol.equals(AiSettings.RESPONSES))nativeAppendOnly(dir);else appendOnly(dir);
            reset("normal",true);File legacy=previousContentMaterials("json-mode-legacy-"+protocol,protocol);JSONObject frozen=state(legacy);String base=frozen.getJSONObject("conversation").getJSONObject("options").toString();
            generateWithProtocol(legacy,protocol);
            check(!state(legacy).optBoolean("native_json")&&state(legacy).getJSONObject("conversation").getJSONObject("options").toString().equals(base),"existing unfinished run keeps its prior format and base options: "+protocol);
            for(JSONObject request:requests)check(!request.has("response_format")&&(!request.has("text")||!request.getJSONObject("text").has("format")),"legacy request never silently gains native JSON: "+protocol);
            reset("json-mode-rejected",false);File rejected=materials("json-mode-rejected-"+protocol,false);
            rejects(()->generateWithProtocol(rejected,protocol),"unsupported native JSON explicitly fails: "+protocol);
            check(requests.size()==2&&state(rejected).getJSONArray("drafts").length()==0&&!state(rejected).has("final"),"no silent plain-text retry or invalid commit after format rejection: "+protocol);
            reset("normal",false);File resume=materials("json-mode-resume-"+protocol,false);failCall=2;
            rejects(()->generateWithProtocol(resume,protocol),"new JSON stage interruption keeps checkpoint: "+protocol);String identity=state(resume).getString("run");failCall=-1;
            check(generateWithProtocol(resume,protocol).getBoolean("complete")&&state(resume).getString("run").equals(identity)&&Collections.frequency(stages,"correct-0")==1,"resume keeps native mode, same run and committed correction: "+protocol);
            if(protocol.equals(AiSettings.RESPONSES))nativeAppendOnly(resume);else appendOnly(resume);
        }
    }
    public static void main(String[] args)throws Exception{
        root=new File(args[0]);root.mkdirs();
        if(args.length>1&&args[1].equals("native-schema")){
            HttpServer server=server();try{test("typed stage grammar, root fields and immutable saved-schema recovery",ClassroomEnhancedCheck::nativeSchemas);}finally{server.stop(0);}
            System.out.println("ClassroomNativeSchema: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Native schema checks failed");return;
        }
        if(args.length>1&&args[1].equals("grounded-inputs")){
            HttpServer server=server();try{test("original teacher source and adjacent image identities",ClassroomEnhancedCheck::groundedInputs);}finally{server.stop(0);}
            System.out.println("ClassroomGroundedInputs: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Grounded input checks failed");return;
        }
        if(args.length>1&&args[1].equals("native-json")){
            HttpServer server=server();try{test("stage-specific native JSON, legacy freeze, recovery and rejection",ClassroomEnhancedCheck::nativeJsonModes);}finally{server.stop(0);}
            System.out.println("ClassroomNativeJson: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Native JSON mode checks failed");return;
        }
        if(args.length>1&&args[1].equals("refinement-scope-actual")){
            test("actual middle-note numeric source topology",ClassroomEnhancedCheck::refinementActualSourceTopology);
            System.out.println("ClassroomRefinementScopeActual: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Actual refinement source topology checks failed");return;
        }
        if(args.length>1&&args[1].equals("legacy-extra-affairs")){
            test("legacy response validates present out-of-contract affairs",ClassroomEnhancedCheck::legacyExtraAffairsValidation);
            System.out.println("ClassroomLegacyExtraAffairs: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Legacy extra affairs checks failed");return;
        }
        if(args.length==1||args[1].equals("failure-detail")){
            test("durable safe failure detail and page restore",ClassroomEnhancedCheck::failureDetails);
            if(args.length>1){System.out.println("ClassroomFailureDetail: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Failure detail checks failed");return;}
        }
        if(args.length>1&&args[1].equals("markdown-fences")){
            test("Markdown fences preserve code and TeX escapes",ClassroomEnhancedCheck::markdownFences);
            System.out.println("ClassroomEnhancedMarkdownFences: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Enhanced Markdown fences checks failed");return;
        }
        HttpServer server=server();
        if(args.length>1&&args[1].equals("compact-affairs-first")){
            try{test("immutable original affairs before complete knowledge",ClassroomEnhancedCheck::affairsFirstPipeline);}
            finally{server.stop(0);}System.out.println("ClassroomCompactAffairsFirst: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Compact affairs-first checks failed");return;
        }
        if(args.length==1)test("compact correction, image-only planning and single complete publication",ClassroomEnhancedCheck::compactPipeline);
        if(args.length>1&&args[1].equals("compact-pipeline")){
            try{test("compact correction, image-only planning and single complete publication",ClassroomEnhancedCheck::compactPipeline);}
            finally{server.stop(0);}System.out.println("ClassroomCompactPipeline: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Compact pipeline checks failed");return;
        }
        if(args.length==1)test("source-first editorial projection and frozen legacy recovery",ClassroomEnhancedCheck::sourceProjection);
        if(args.length>1&&args[1].equals("source-projection")){
            try{test("source-first editorial projection and frozen legacy recovery",ClassroomEnhancedCheck::sourceProjection);}
            finally{server.stop(0);}System.out.println("ClassroomSourceProjection: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Source projection checks failed");return;
        }
        if(args.length==1)test("single stable shared policy and unchanged stage contracts",ClassroomEnhancedCheck::policyPrefix);
        if(args.length>1&&args[1].equals("policy-prefix")){
            try{test("single stable shared policy and unchanged stage contracts",ClassroomEnhancedCheck::policyPrefix);}
            finally{server.stop(0);}System.out.println("ClassroomPolicyPrefix: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Policy prefix checks failed");return;
        }
        if(args.length==1)test("whole-lesson final affairs and stage-only recovery",ClassroomEnhancedCheck::unifiedAffairs);
        if(args.length>1&&args[1].equals("unified-affairs")){
            try{test("whole-lesson final affairs and stage-only recovery",ClassroomEnhancedCheck::unifiedAffairs);}
            finally{server.stop(0);}System.out.println("ClassroomUnifiedAffairs: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Unified affairs checks failed");return;
        }
        if(args.length==1)test("evidence diagnostics exclude empty-actions editorial stages",ClassroomEnhancedCheck::assignmentEvidenceStageScope);
        if(args.length>1&&args[1].equals("assignment-evidence-stage-scope")){
            try{test("evidence diagnostics exclude empty-actions editorial stages",ClassroomEnhancedCheck::assignmentEvidenceStageScope);}
            finally{server.stop(0);}System.out.println("ClassroomAssignmentEvidenceStageScope: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Assignment evidence stage-scope checks failed");return;
        }
        if(args.length==1)test("literal assignment-evidence diagnostics in bounded recovery requests",ClassroomEnhancedCheck::assignmentEvidenceRecovery);
        if(args.length>1&&args[1].equals("assignment-evidence-recovery")){
            try{test("literal assignment-evidence diagnostics in bounded recovery requests",ClassroomEnhancedCheck::assignmentEvidenceRecovery);}
            finally{server.stop(0);}System.out.println("ClassroomAssignmentEvidenceRecovery: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Assignment evidence recovery checks failed");return;
        }
        if(args.length==1)test("current-group full coverage and exact corrected-part boundaries",ClassroomEnhancedCheck::contentCoverage);
        if(args.length>1&&args[1].equals("content-coverage")){
            try{test("current-group full coverage and exact corrected-part boundaries",ClassroomEnhancedCheck::contentCoverage);}
            finally{server.stop(0);}System.out.println("ClassroomContentCoverage: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Content coverage checks failed");return;
        }
        if(args.length==1)test("repair and manual retry append the actual grouped-final boundary",ClassroomEnhancedCheck::contentRetryBoundary);
        if(args.length>1&&args[1].equals("content-retry-boundary")){
            try{test("repair and manual retry append the actual grouped-final boundary",ClassroomEnhancedCheck::contentRetryBoundary);}
            finally{server.stop(0);}System.out.println("ClassroomContentRetryBoundary: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Content retry boundary checks failed");return;
        }
        if(args.length==1)test("original correction blocks, grouped content and frozen editorial limits",ClassroomEnhancedCheck::contentBatches);
        if(args.length>1&&args[1].equals("content-batches")){
            try{test("original correction blocks, grouped content and frozen editorial limits",ClassroomEnhancedCheck::contentBatches);test("fresh two-block content pipeline",ClassroomEnhancedCheck::multiBlock);test("grouped network checkpoint recovery",ClassroomEnhancedCheck::manualResume);test("grouped truncation and process recovery",ClassroomEnhancedCheck::lengthResume);}
            finally{server.stop(0);}System.out.println("ClassroomContentBatches: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Content batch checks failed");return;
        }
        if(args.length>1&&args[1].equals("assignment-action-prompt")){
            try{test("actual extraction and refinement prompts distinguish action objects",ClassroomEnhancedCheck::multiBlock);}
            finally{server.stop(0);}System.out.println("ClassroomAssignmentActionPrompt: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Assignment action prompt checks failed");return;
        }
        if(args.length>1&&args[1].equals("editorial-batch-schema")){
            try{test("actual two-topic editorial wire example has empty actions",ClassroomEnhancedCheck::editorialBatchSchema);}
            finally{server.stop(0);}System.out.println("ClassroomEditorialBatchSchema: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Editorial batch schema checks failed");return;
        }
        if(args.length>1&&args[1].equals("editorial-batches")){
            try{test("actual two-topic editorial wire example has empty actions",ClassroomEnhancedCheck::editorialBatchSchema);test("bounded two-topic HTTP batches, durable cursor and legacy continuation",ClassroomEnhancedCheck::editorialBatches);test("new enhanced run snapshot and actual extraction-to-editor flow",ClassroomEnhancedCheck::multiBlock);}
            finally{server.stop(0);}System.out.println("ClassroomEditorialBatches: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Editorial batch checks failed");return;
        }
        if(args.length>1&&args[1].equals("received-revalidation")){
            try{test("completed JSON local revalidation and paid-retry barriers",ClassroomEnhancedCheck::completedReplyRevalidation);}
            finally{server.stop(0);}System.out.println("ClassroomReceivedRevalidation: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Received revalidation checks failed");return;
        }
        if(args.length>1&&args[1].equals("editorial-classification")){
            try{test("editorial actual-category contract",ClassroomEnhancedCheck::editorialClassificationContract);test("editorial category correction, overview and safe recovery",ClassroomEnhancedCheck::editorialClassificationPipeline);}
            finally{server.stop(0);}System.out.println("ClassroomEditorialClassification: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Editorial classification checks failed");return;
        }
        if(args.length>1&&args[1].equals("refinement-scope")){
            try{test("refinement scoped sources and safe indexed diagnostics",ClassroomEnhancedCheck::refinementScopeValidation);test("actual middle-note numeric source topology",ClassroomEnhancedCheck::refinementActualSourceTopology);test("loopback append-only refinement retry, repair and frozen legacy",ClassroomEnhancedCheck::refinementScopePipeline);}
            finally{server.stop(0);}System.out.println("ClassroomRefinementScope: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Refinement scope checks failed");return;
        }
        if(args.length>1&&args[1].equals("draft-images")){
            try{test("enhanced draft image boundary and append-only recovery",ClassroomEnhancedCheck::draftImageReferences);}
            finally{server.stop(0);}System.out.println("ClassroomEnhancedDraftImages: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Enhanced draft image checks failed");return;
        }
        if(args.length>1&&args[1].equals("responses")){
            try{test("native Responses pipeline, immutable items and protocol-frozen recovery",ClassroomEnhancedCheck::nativePipeline);}
            finally{server.stop(0);}System.out.println("ClassroomResponsesCheck: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Native Responses pipeline checks failed");return;
        }
        if(args.length==1){test("actual two-topic editorial wire example has empty actions",ClassroomEnhancedCheck::editorialBatchSchema);test("bounded two-topic HTTP batches, durable cursor and legacy continuation",ClassroomEnhancedCheck::editorialBatches);test("completed JSON local revalidation and paid-retry barriers",ClassroomEnhancedCheck::completedReplyRevalidation);test("editorial actual-category contract",ClassroomEnhancedCheck::editorialClassificationContract);test("editorial category correction, overview and safe recovery",ClassroomEnhancedCheck::editorialClassificationPipeline);test("enhanced draft image boundary and append-only recovery",ClassroomEnhancedCheck::draftImageReferences);test("native Responses pipeline, immutable items and protocol-frozen recovery",ClassroomEnhancedCheck::nativePipeline);}
        if(args.length==1)test("scoped routing retains the full pipeline and immutable checkpoints",ClassroomEnhancedCheck::routedPipeline);
        if(args.length>1&&args[1].equals("note-strength")){
            try{test("enhanced note strength and overview image guard",ClassroomEnhancedCheck::noteStrength);}
            finally{server.stop(0);}System.out.println("ClassroomEnhancedNoteStrength: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Enhanced note strength checks failed");return;
        }
        if(args.length>1&&args[1].equals("affairs")){
            try{test("course affairs schema, editorial coverage and frozen legacy",ClassroomEnhancedCheck::courseAffairs);}
            finally{server.stop(0);}System.out.println("ClassroomAffairsCheck: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Course affairs checks failed");return;
        }
        if(args.length==1){test("typed stage grammar, root fields and immutable saved-schema recovery",ClassroomEnhancedCheck::nativeSchemas);test("original teacher source and adjacent image identities",ClassroomEnhancedCheck::groundedInputs);test("stage-specific native JSON, legacy freeze, recovery and rejection",ClassroomEnhancedCheck::nativeJsonModes);test("refinement scoped sources and safe indexed diagnostics",ClassroomEnhancedCheck::refinementScopeValidation);test("loopback append-only refinement retry, repair and frozen legacy",ClassroomEnhancedCheck::refinementScopePipeline);test("legacy response validates present out-of-contract affairs",ClassroomEnhancedCheck::legacyExtraAffairsValidation);test("course affairs schema, editorial coverage and frozen legacy",ClassroomEnhancedCheck::courseAffairs);test("enhanced note strength and immutable prefix",ClassroomEnhancedCheck::noteStrength);test("required image schema bounded repair",ClassroomEnhancedCheck::schemaRepair);test("classroom-specific timeout and shared deadline",ClassroomEnhancedCheck::classroomTimeout);test("atomic whole-lesson assignment deduplication",ClassroomEnhancedCheck::assignmentEditorial);}
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
