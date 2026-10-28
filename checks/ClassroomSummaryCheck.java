package com.donglan.chrona.classroom;

import com.donglan.chrona.ai.*;
import com.donglan.chrona.net.RequestControl;
import com.sun.net.httpserver.HttpServer;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.zip.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;

/** Real production pipeline, real loopback HTTP, synthetic source data only. */
public final class ClassroomSummaryCheck {
    private static int checks, failures, calls, failCall = -1;
    private static String mode = "normal";
    private static final List<JSONObject> requests = new ArrayList<>();
    private static final Map<Integer,Integer> chunkCalls = new HashMap<>();
    private static final ClassroomLesson LESSON = new ClassroomLesson("1","2","测试课程","单课","教室","6",1);
    private static final AiSettings AI = new AiSettings("https://api.openai.com/v1","gpt-5.1","synthetic-test-key","high");
    private static File root;
    interface Task {void run() throws Exception;}
    private static void check(boolean value,String message) {checks++;if(!value){failures++;System.err.println("FAIL: "+message);}}
    private static void test(String label,Task task) {try{task.run();System.out.println("DONE: "+label);}catch(Throwable e){failures++;System.err.println("ERROR: "+label);e.printStackTrace();}}
    private static void rejects(Task task,String label) throws Exception {try{task.run();check(false,label);}catch(IOException|JSONException|IllegalArgumentException expected){check(true,label);}}
    private static JSONObject segment(double start,String text)throws Exception{return new JSONObject().put("start",start).put("end",start+10).put("text",text);}
    private static JSONObject picture(int index,double second)throws Exception{return new JSONObject().put("file","image-"+index+".jpg").put("second",second);}
    private static JSONObject part(JSONObject chunk)throws Exception {
        int id=chunk.getJSONArray("segments").getJSONObject(0).getInt("id");
        JSONArray pics=new JSONArray();for(int i=0;i<chunk.getJSONArray("images").length();i++)pics.put(chunk.getJSONArray("images").getJSONObject(i).getString("file"));
        return new JSONObject().put("overview","本段重点 "+id).put("notes",new JSONArray().put(new JSONObject().put("title","主题 "+id).put("text","概念与推导").put("sources",new JSONArray().put(id)).put("images",pics)))
                .put("assignments",new JSONArray().put(new JSONObject().put("text","下周交作业").put("sources",new JSONArray().put(id))));
    }
    private static byte[] image(int gray)throws Exception {
        BufferedImage image=new BufferedImage(96,96,BufferedImage.TYPE_INT_RGB);int color=gray*0x010101;
        for(int x=0;x<96;x++)for(int y=0;y<96;y++)image.setRGB(x,y,color);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
    private static void reset(){calls=0;failCall=-1;mode="normal";requests.clear();chunkCalls.clear();}
    private static HttpServer server()throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;}));
        server.createContext("/",exchange->{try {
            JSONObject body=new JSONObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
            calls++;requests.add(body);String prompt=body.getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text");
            boolean second=prompt.contains("完整资料包：\n");
            JSONObject chunk=null;
            if(second) {
                JSONObject context=new JSONObject(prompt.substring(prompt.indexOf("完整资料包：\n")+7));
                String first=context.getString("first_prompt");
                chunk=new JSONObject(first.substring(first.indexOf("资料：\n")+4)).put("images",context.getJSONArray("images"));
            } else if(prompt.contains("资料：\n"))chunk=new JSONObject(prompt.substring(prompt.indexOf("资料：\n")+4));
            if(chunk!=null){int id=chunk.getJSONArray("segments").getJSONObject(0).getInt("id");chunkCalls.merge(id,1,Integer::sum);}
            if(mode.equals("slow"))Thread.sleep(500);
            if(mode.equals("silent-reasoning"))Thread.sleep(31_000);
            int status=200;String payload,type="text/event-stream";
            if(calls==failCall){status=503;type="application/json";payload="{\"error\":{\"message\":\"synthetic failure\"}}";}
            else if(mode.equals("downgrade")&&body.has("stream_options")){status=400;type="application/json";payload="{\"error\":{\"message\":\"unsupported parameter stream_options\"}}";}
            else {
                JSONObject generated=chunk==null?new JSONObject().put("overview","合并重点"):part(chunk);
                if(chunk!=null&&!second){
                    JSONObject catalogue=chunk.optJSONObject("image_catalogue");String decision=catalogue==null||catalogue.optInt("limit")==0?"disabled":catalogue.optInt("count")==0?"unavailable":mode.equals("no-request")?"not_needed":"request";
                    generated.put("image_assessment",new JSONObject().put("decision",decision).put("reason",decision.equals("request")?"字幕中矩阵推导依赖板书，需核对符号":"本次材料与上传策略足以确定是否需要图片"));
                    JSONArray windows=new JSONArray();if(decision.equals("request"))windows.put(new JSONObject().put("start",mode.equals("no-match")?9999:0).put("end",10000).put("reason","核对矩阵板书中的列向量与运算步骤"));generated.put("image_requests",windows);
                    if(mode.equals("missing-assessment"))generated.remove("image_assessment");
                    if(mode.equals("missing-decision-reason"))generated.getJSONObject("image_assessment").put("reason","");
                    if(mode.equals("missing-request-reason"))windows.getJSONObject(0).remove("reason");
                    if(mode.equals("conflicting-decision"))generated.getJSONObject("image_assessment").put("decision","not_needed");
                    if(mode.equals("unauthorized-request")){generated.getJSONObject("image_assessment").put("decision","request");windows.put(new JSONObject().put("start",0).put("end",60).put("reason","未授权请求"));}
                }
                if(chunk!=null&&mode.equals("invalid-window"))generated.put("image_requests",new JSONArray().put(new JSONObject().put("start",20).put("end",10)));
                if(chunk!=null&&mode.equals("invalid-source"))generated.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(999));
                if(chunk!=null&&mode.equals("invalid-image"))generated.getJSONArray("notes").getJSONObject(0).put("images",new JSONArray().put("image-999.jpg"));
                if(second&&mode.equals("invalid-final"))generated.getJSONArray("notes").getJSONObject(0).put("images",new JSONArray().put("image-999.jpg"));
                if(second&&mode.equals("prefixed-final")){
                    JSONObject note=generated.getJSONArray("notes").getJSONObject(0);JSONArray prefixed=new JSONArray();
                    for(int i=0;i<chunk.getJSONArray("images").length();i++)prefixed.put("images/"+chunk.getJSONArray("images").getJSONObject(i).getString("file"));
                    note.put("images",prefixed).put("text","保留公式 $x^2$。![已上传的图]("+prefixed.getString(0)+")");
                }
                String result=chunk!=null&&!second?"```json\n"+generated.toString(2)+"\n```":generated.toString();
                if(mode.equals("schedule"))result="{\"events\":[]}";
                String finish=mode.equals("length")?"length":mode.equals("filter")?"content_filter":"stop";
                JSONObject frame=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("reasoning_content","合成推理").put("content",result)).put("finish_reason",finish)));
                if(mode.equals("silent-reasoning"))frame.getJSONArray("choices").getJSONObject(0).getJSONObject("delta").remove("reasoning_content");
                payload="data: "+frame+"\n\ndata: [DONE]\n\n";
                if(mode.equals("json")){type="application/json";payload=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("message",new JSONObject().put("content",result)).put("finish_reason","stop"))).toString();}
            }
            byte[] bytes=payload.getBytes(StandardCharsets.UTF_8);exchange.getResponseHeaders().set("Content-Type",type);exchange.sendResponseHeaders(status,bytes.length);exchange.getResponseBody().write(bytes);
        } catch(Exception ignored) { } finally {exchange.close();}});
        server.start();
        // Test-only HTTPS URL adapter. All production HttpURLConnection IO goes to loopback;
        // original HTTPS hostname remains intact for production RequestOptions selection.
        URL.setURLStreamHandlerFactory(protocol->protocol.equals("https")?new URLStreamHandler(){protected URLConnection openConnection(URL u)throws IOException {
            if(!Set.of("api.openai.com","api.deepseek.com").contains(u.getHost()))throw new IOException("External request forbidden in test");
            return new URL("http://127.0.0.1:"+server.getAddress().getPort()+u.getFile()).openConnection();
        }}:null);
        return server;
    }
    private static void windowSelection()throws Exception {
        JSONArray candidates=new JSONArray();for(int i=39;i>=0;i--)candidates.put(new JSONObject().put("index",i).put("second",i));
        JSONArray windows=new JSONArray().put(new JSONObject().put("start",5).put("end",35)).put(new JSONObject().put("start",10).put("end",20));
        JSONArray chosen=ClassroomSummary.selectRequested(candidates,windows,20);Set<Integer> ids=new HashSet<>();
        for(int i=0;i<chosen.length();i++){JSONObject image=chosen.getJSONObject(i);ids.add(image.getInt("index"));check(image.getDouble("second")>=5&&image.getDouble("second")<=35,"only requested windows selected");}
        check(chosen.length()==20&&ids.size()==20,"overlapping windows share one global twenty-image cap without duplicates");
        check(chosen.getJSONObject(0).getInt("index")==5&&chosen.getJSONObject(19).getInt("index")==35,"selection chronological and covers range endpoints");
        check(ClassroomSummary.selectRequested(candidates,null,20).length()==0,"no image request selects none");
        rejects(()->ClassroomSummary.selectRequested(candidates,new JSONArray().put(new JSONObject().put("start",-1).put("end",2)),20),"negative window rejected");
        rejects(()->ClassroomSummary.selectRequested(candidates,new JSONArray().put(new JSONObject().put("start","0").put("end",2)),20),"string window rejected");
    }
    private static void subtitleCompression()throws Exception {
        JSONArray source=new JSONArray().put(segment(0,"第一句")).put(segment(3,"第二句"))
                .put(segment(30,"间隔后的第三句"));
        source.getJSONObject(0).put("end",2);source.getJSONObject(1).put("end",5);
        JSONObject original=ClassroomSummary.lessonInput(source,new JSONArray());
        JSONObject upload=ClassroomSummary.uploadChunk(original);JSONArray rows=upload.getJSONArray("segments");
        check(rows.length()==3&&rows.getJSONObject(0).has("start"),"first subtitle retains time anchor");
        check(!rows.getJSONObject(1).has("start")&&!rows.getJSONObject(1).has("end"),"nearby subtitle omits repeated timestamps");
        check(rows.getJSONObject(2).getDouble("start")==30,"long gap restores timestamp");
        check(rows.getJSONObject(1).getInt("id")==1&&rows.getJSONObject(1).getString("text").equals("第二句"),"all text and source IDs preserved");
        check(original.getJSONArray("segments").getJSONObject(1).getDouble("start")==3&&original.getJSONArray("segments").getJSONObject(1).getDouble("end")==5,"local source timings unchanged");
        check(upload.toString().length()<original.toString().length(),"upload payload is smaller");
        JSONArray continuous=new JSONArray();for(int i=0;i<40;i++)continuous.put(segment(i*3,"连续讲解").put("end",i*3+3));
        JSONArray timed=ClassroomSummary.uploadChunk(ClassroomSummary.lessonInput(continuous,new JSONArray())).getJSONArray("segments");
        check(timed.getJSONObject(20).getDouble("start")==60&&!timed.getJSONObject(19).has("start"),"continuous speech keeps sparse minute anchors for image requests");
        JSONArray dense=new JSONArray();for(int i=999;i>=0;i--)dense.put(new JSONObject().put("second",i*.25).put("raw","private-"+i));
        JSONObject denseCatalogue=ClassroomSummary.imageCatalogue(dense,40);JSONArray ranges=denseCatalogue.getJSONArray("ranges");
        check(denseCatalogue.getInt("count")==1000&&ranges.length()==1&&ranges.getJSONArray(0).getInt(2)==1000,"dense screenshots collapse to one counted availability range");
        check(ranges.getJSONArray(0).getLong(0)==0&&ranges.getJSONArray(0).getLong(1)==250,"catalogue rounds only displayed boundaries to integer seconds");
        check(denseCatalogue.toString().length()<120&&!denseCatalogue.toString().contains("private"),"compact catalogue sends neither individual timestamps nor raw paths");
        JSONArray sparse=new JSONArray();for(int i=0;i<200;i++)sparse.put(new JSONObject().put("second",i*120));
        JSONObject sparseCatalogue=ClassroomSummary.imageCatalogue(sparse,40);ranges=sparseCatalogue.getJSONArray("ranges");int represented=0;long previous=-1;
        for(int i=0;i<ranges.length();i++){JSONArray range=ranges.getJSONArray(i);represented+=range.getInt(2);check(range.getLong(0)>previous&&range.getLong(1)>=range.getLong(0),"compact ranges remain ordered and non-overlapping");previous=range.getLong(1);}
        check(ranges.length()==40&&represented==200,"sparse catalogues use the forty-range ceiling without losing counts");
        check(ClassroomSummary.imageCatalogue(dense,0).getJSONArray("ranges").length()==0,"disabled upload adds no image timestamps");
        JSONObject withCatalogue=ClassroomSummary.lessonInput(source,new JSONArray()).put("image_catalogue",denseCatalogue);
        check(ClassroomSummary.uploadChunk(withCatalogue).getJSONObject("image_catalogue").toString().equals(denseCatalogue.toString()),"compressed transcript carries the compact availability catalogue");
    }
    private static void validation()throws Exception {
        JSONObject chunk=ClassroomSummary.lessonInput(new JSONArray().put(segment(0,"来源")),new JSONArray().put(picture(0,0)));
        JSONObject valid=part(chunk);ClassroomSummary.validate(valid,chunk);check(true,"valid actual source and uploaded image accepted");
        JSONObject bad=new JSONObject(valid.toString());bad.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(99));rejects(()->ClassroomSummary.validate(bad,chunk),"out-of-chunk subtitle rejected");
        JSONObject missing=new JSONObject(valid.toString());missing.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray());rejects(()->ClassroomSummary.validate(missing,chunk),"missing source rejected");
        JSONObject string=new JSONObject(valid.toString());string.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put("0"));rejects(()->ClassroomSummary.validate(string,chunk),"string source ID rejected");
        JSONObject unuploaded=new JSONObject(valid.toString());unuploaded.getJSONArray("notes").getJSONObject(0).put("images",new JSONArray().put("image-9.jpg"));rejects(()->ClassroomSummary.validate(unuploaded,chunk),"image not uploaded in this chunk rejected");
        JSONObject prefixed=new JSONObject(valid.toString());prefixed.getJSONArray("notes").getJSONObject(0).put("images",new JSONArray().put("images/image-0.jpg"));ClassroomSummary.validate(prefixed,chunk);check(true,"actual uploaded image with canonical directory prefix accepted");
        JSONObject sparse=ClassroomSummary.lessonInput(new JSONArray().put(segment(0,"来源")),new JSONArray().put(picture(17,20)).put(picture(42,40)));
        JSONObject sparsePart=part(sparse);sparsePart.getJSONArray("notes").getJSONObject(0).put("images",new JSONArray().put("images/image-42.jpg")).put("text","![图](images/image-17.jpg)");
        ClassroomSummary.validate(sparsePart,sparse);check(true,"sparse stable image identifiers share one array and markdown whitelist");
        for(String name:List.of("image-1.jpg","image-2.jpg","images/image-999.jpg","raw-17.img","../image-17.jpg","https://example.com/image-17.jpg")){
            JSONObject invalid=new JSONObject(sparsePart.toString());invalid.getJSONArray("notes").getJSONObject(0).put("images",new JSONArray().put(name));rejects(()->ClassroomSummary.validate(invalid,sparse),"unuploaded ordinal or unsafe array reference rejected: "+name);
        }
        for(String field:List.of("overview","title","text","assignment")){
            JSONObject invalid=new JSONObject(sparsePart.toString());String badImage="![不存在](images/image-999.jpg)";
            if(field.equals("overview"))invalid.put(field,badImage);
            else if(field.equals("assignment"))invalid.getJSONArray("assignments").getJSONObject(0).put("text",badImage);
            else invalid.getJSONArray("notes").getJSONObject(0).put(field,badImage);
            rejects(()->ClassroomSummary.validate(invalid,sparse),"unuploaded inline reference rejected in "+field);
        }
        JSONObject assignment=new JSONObject(valid.toString());assignment.getJSONArray("assignments").getJSONObject(0).put("sources",new JSONArray().put(99));rejects(()->ClassroomSummary.validate(assignment,chunk),"assignment source is validated");
        check(ClassroomSummary.parseObject("```json\n{\"x\":1}\n```").getInt("x")==1,"fenced JSON accepted");
    }
    private static void images()throws Exception {
        File dir=new File(root,"dedup");dir.mkdirs();ClassroomClient client=new ClassroomClient();JSONArray frames=new JSONArray();
        int[] values={10,11,100,10};for(int i=0;i<values.length;i++){client.images.put("frame"+i,image(values[i]));frames.put(new JSONObject().put("url","frame"+i).put("second",i));}
        JSONObject materials=new JSONObject().put("images",frames);JSONArray candidates;
        try(RequestControl control=new RequestControl(5000)){candidates=ClassroomImages.prepare(dir,materials,client,control,s->{});}
        check(candidates.length()==3&&candidates.getJSONObject(0).getInt("index")==1,"near-identical consecutive frame retains later image");
        check(candidates.getJSONObject(2).getInt("index")==3,"recurring non-adjacent slide retained (local adjacency comparison)");
        check(ClassroomImages.difference(new int[]{0,10},new int[]{2,14})==3,"mean absolute difference");
        check(ClassroomImages.select(candidates,0).length()==0,"zero budget is text only");
        JSONArray chosen=ClassroomImages.select(candidates,2);check(chosen.length()==2&&chosen.getJSONObject(0).getInt("index")==1&&chosen.getJSONObject(1).getInt("index")==3,"uniform budget retains first/final candidate");
        check(ClassroomImages.select(candidates,100).length()==3,"budget is an upper limit");
        byte[] jpeg=ClassroomImages.jpeg(new File(dir,"raw-0.img"));check(ImageIO.read(new ByteArrayInputStream(jpeg))!=null,"actual image re-encodes to decodable JPEG");
    }
    private static File materialDir(String name)throws Exception {
        File dir=new File(root,name);dir.mkdirs();JSONArray segments=new JSONArray(),candidates=new JSONArray();
        for(int i=0;i<3;i++){segments.put(segment(i*100,"字幕 "+i+"甲".repeat(5000)));Files.write(new File(dir,"raw-"+i+".img").toPath(),image(i*60));candidates.put(new JSONObject().put("raw","raw-"+i+".img").put("second",i*100).put("index",i));}
        ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("segments",segments).put("raw_images",3));ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject().put("images",candidates));return dir;
    }
    private static Map<String,byte[]> unzip(byte[] value)throws Exception {
        Map<String,byte[]> result=new LinkedHashMap<>();try(ZipInputStream in=new ZipInputStream(new ByteArrayInputStream(value),StandardCharsets.UTF_8)){ZipEntry e;while((e=in.getNextEntry())!=null)result.put(e.getName(),in.readAllBytes());}return result;
    }
    private static void pipeline()throws Exception {
        reset();File dir=materialDir("pipeline-"+System.nanoTime());failCall=2;
        try(RequestControl c=new RequestControl(20000)){rejects(()->ClassroomSummary.generate(dir,LESSON,AI,3,c,s->{},"保留英文术语"),"mid-run HTTP failure propagated");}
        JSONObject checkpoint=ClassroomStore.read(new File(dir,"run.json")),partial=ClassroomStore.read(new File(dir,"summary.json"));
        check(checkpoint.has("draft")&&checkpoint.has("first_output")&&!checkpoint.has("final"),"first round checkpoint persisted before second round failure");
        check(!partial.getBoolean("complete")&&partial.getJSONArray("parts").length()==1&&partial.getInt("total_parts")==1,"partial result explicitly incomplete");
        check(partial.getInt("planned_images")==3,"partial result distinguishes planned from analysed image count");
        check(partial.getJSONArray("images").length()==0,"partial image inventory does not claim images analysed");
        ByteArrayOutputStream partialZip=new ByteArrayOutputStream();ClassroomExport.write(dir,partial,partialZip);Map<String,byte[]> partialEntries=unzip(partialZip.toByteArray());String partialMd=new String(partialEntries.get("课堂总结.md"),StandardCharsets.UTF_8);
        check(partialMd.contains("未完成"),"partial export reports incomplete status");
        check(partialEntries.keySet().stream().noneMatch(s->s.startsWith("images/")),"partial export excludes not-yet-analysed images");
        failCall=-1;JSONObject result;List<ClassroomProgress> progress=new ArrayList<>();try(RequestControl c=new RequestControl(20000)){result=ClassroomSummary.generate(dir,LESSON,AI,3,c,progress::add,"保留英文术语");}
        check(progress.stream().anyMatch(p->p.stage.equals("AI 结合图片完成总结")&&p.completed==1&&p.total==2&&p.detail.contains("等待 AI 返回")),"resumed progress reports second-round wait");
        ClassroomProgress done=progress.get(progress.size()-1);check(done.completed==2&&done.total==2&&done.stage.equals("课堂总结已保存"),"final save completes two rounds");
        check(result.getBoolean("complete")&&result.getJSONArray("parts").length()==1,"retry finishes one whole-lesson final result");
        check(calls==3,"retry calls only failed second round");
        JSONArray firstMessages=requests.get(0).getJSONArray("messages");String firstPrompt=firstMessages.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text");
        check(firstMessages.getJSONObject(1).getJSONArray("content").length()==1,"first round sends no image bytes");
        check(firstPrompt.contains("应主动请求相关时间范围")&&firstPrompt.contains("必须返回 image_assessment")&&!firstPrompt.contains("仅在确有需要时请求"),"first prompt requires explicit visual-gap review instead of optional image requests");
        JSONObject imageAudit=result.getJSONObject("image_audit");
        check(imageAudit.getJSONObject("assessment").getString("decision").equals("request")&&!imageAudit.getJSONObject("assessment").getString("reason").isBlank(),"summary preserves the model's reason for requesting images");
        check(imageAudit.getInt("selected_count")==3&&imageAudit.getInt("analysed_count")==3&&checkpoint.getJSONObject("image_audit").getInt("analysed_count")==0,"audit distinguishes planned selection from actually analysed frames across failure");
        check(ClassroomStore.read(new File(dir,"run.json")).getJSONObject("image_audit").getInt("analysed_count")==3,"final run log records actual analysed count");
        check(new JSONObject(firstPrompt.substring(firstPrompt.indexOf("资料：\n")+4)).getJSONArray("segments").length()==3,"first round includes entire long transcript without chunks");
        String secondPrompt=requests.get(2).getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text");
        JSONObject context=new JSONObject(secondPrompt.substring(secondPrompt.indexOf("完整资料包：\n")+7));
        check(context.getString("first_system").equals(firstMessages.getJSONObject(0).getString("content"))&&context.getString("first_prompt").equals(firstPrompt),"second round contains exact full first system and prompt");
        check(context.getString("first_system").contains("保留英文术语"),"classroom custom prompt reaches both rounds");
        check(context.getString("first_system").contains("数学符号与记法偏好中国大陆教材的常用风格。"),"general Chinese mathematical notation style reaches both rounds");
        for(String topic:List.of("评分规则","考试与测试安排","考核范围与题型","重点与透题线索","作业、实验与报告",
                "截止与补救机会","出勤与课堂表现","规则变更与纠错","复习与学习资源","其他重要提醒"))
            check(context.getString("first_system").contains(topic),"both real requests retain student concern: "+topic);
        check(context.getString("first_system").contains("优先突出前八类")&&context.getString("first_system").contains("区分明确要求、参考建议与含糊暗示")
                &&context.getString("first_system").contains("不补猜时间、比例、范围或承诺"),"priority and uncertainty are explicit");
        check(requests.get(2).getJSONArray("messages").getJSONObject(0).getString("content").equals(context.getString("first_system")),"final image round retains the same Chinese math style instructions");
        check(!ClassroomSummary.configKey(AI,20,"other").equals(ClassroomSummary.configKey(AI,20,"保留英文术语")),"custom prompt changes invalidate the previous run");
        check(context.getString("first_output").equals(checkpoint.getString("first_output")),"second round contains exact raw first response");
        JSONArray secondContent=requests.get(2).getJSONArray("messages").getJSONObject(1).getJSONArray("content");
        check(secondContent.length()==4&&context.getJSONArray("images").length()==3,"second round includes matching inventory and three actual images");
        for(int i=0;i<3;i++)check(secondContent.getJSONObject(i+1).getJSONObject("image_url").getString("url").equals("data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(ClassroomStore.bytes(new File(dir,"image-"+i+".jpg")))),"second-round actual JPEG matches inventory "+i);
        check(result.getString("run").equals(partial.getString("run")),"resumed run keeps identity for UI replacement");
        int before=calls;JSONObject regenerated;
        try(RequestControl c=new RequestControl(20000)){regenerated=ClassroomSummary.generate(dir,LESSON,AI,3,c,s->{},"保留英文术语");}
        check(calls==before+2,"regenerate completed summary performs both real HTTP rounds again");
        check(regenerated.getBoolean("complete")&&!regenerated.getString("run").equals(result.getString("run")),"regeneration has a fresh completed run identity with unchanged settings");
        failCall=calls+1;
        try(RequestControl c=new RequestControl(20000)){rejects(()->ClassroomSummary.generate(dir,LESSON,AI,3,c,s->{},"保留英文术语"),"regeneration first-round failure is not reported as cached success");}
        JSONObject interrupted=ClassroomStore.read(new File(dir,"run.json"));
        check(!interrupted.has("draft")&&!interrupted.has("final")&&!interrupted.getString("run").equals(regenerated.getString("run")),"failed new request leaves its own incomplete checkpoint");
        check(ClassroomStore.read(new File(dir,"summary.json")).toString().equals(regenerated.toString()),"first-round regeneration failure preserves the previous saved summary");
        failCall=-1;before=calls;
        try(RequestControl c=new RequestControl(20000)){JSONObject retried=ClassroomSummary.generate(dir,LESSON,AI,3,c,s->{},"保留英文术语");check(retried.getString("run").equals(interrupted.getString("run")),"incomplete regeneration resumes its own identity");}
        check(calls==before+2,"failed first-round regeneration retry actually completes both rounds");
        ByteArrayOutputStream zip=new ByteArrayOutputStream();List<ClassroomProgress> exportProgress=new ArrayList<>();ClassroomExport.write(dir,result,zip,exportProgress::add);Map<String,byte[]> entries=unzip(zip.toByteArray());String md=new String(entries.get("课堂总结.md"),StandardCharsets.UTF_8),transcript=new String(entries.get("sources.md"),StandardCharsets.UTF_8);
        check(exportProgress.get(exportProgress.size()-1).completed==entries.size(),"export count equals actual ZIP entries");
        check(entries.size()==5,"ZIP contains Markdown, subtitle source, three local images");
        for(int i=0;i<3;i++){check(md.contains("images/image-"+i+".jpg")&&entries.containsKey("images/image-"+i+".jpg"),"offline image reference resolves "+i);check(transcript.contains("id=\"s"+i+"\""),"offline subtitle anchor resolves "+i);}
        check(md.contains("## 课堂安排")&&md.contains("下周交作业"),"assignments exported as suggestions without calendar writes");
        check(!md.contains(AI.apiKey)&&!checkpoint.toString().contains(AI.apiKey)&&!result.toString().contains(AI.apiKey),"credential absent from run/summary/export");
        rejects(()->ClassroomExport.safeImage("../secret"),"ZIP path traversal rejected");
        try(RequestControl c=new RequestControl(20000)){JSONObject changed=ClassroomSummary.generate(dir,LESSON,AI,0,c,s->{},"保留英文术语");check(!changed.getString("run").equals(result.getString("run")),"changed image budget creates distinct run identity");check(changed.getJSONArray("images").length()==0&&changed.getInt("planned_images")==0,"changed config cannot reuse old image inventory");}
        try(RequestControl c=new RequestControl(10000)){rejects(()->ClassroomSummary.generate(dir,LESSON,AI,-1,c,s->{},"保留英文术语"),"negative image budget rejected");rejects(()->ClassroomSummary.generate(dir,LESSON,AI,41,c,s->{},"保留英文术语"),"over-forty image budget rejected");}
    }
    private static void isolation()throws Exception {
        android.content.Context context=new android.content.Context(new File(root,"accounts-"+System.nanoTime()));ClassroomStore a=new ClassroomStore(context,"anonymous-a"),b=new ClassroomStore(context,"anonymous-b");
        a.catalog(List.of(LESSON));check(a.catalog().size()==1&&b.catalog().isEmpty(),"catalog isolated by anonymous account");
        File adir=a.lesson(LESSON),bdir=b.lesson(LESSON);ClassroomStore.write(new File(adir,"summary.json"),new JSONObject().put("updated",1));check(a.history().size()==1&&b.history().isEmpty()&&!adir.equals(bdir),"history and lesson files isolated by anonymous account");
        rejects(()->new ClassroomStore(context,"../outside"),"unsafe account path rejected");rejects(()->a.lesson(new ClassroomLesson("../1","2","","","","6",1)),"unsafe lesson path rejected");
        check(LESSON.selectable(2)&&!LESSON.selectable(0),"only occurred completed lesson selectable");check(!new ClassroomLesson("1","2","","","","4",1).selectable(2),"unfinished recording cannot be selected");
    }
    private static void capAndFinalValidation()throws Exception {
        reset();File dir=materialDir("forty-cap"+System.nanoTime());JSONArray candidates=new JSONArray();
        for(int i=0;i<80;i++)candidates.put(new JSONObject().put("raw","raw-"+(i%3)+".img").put("second",i).put("index",i));
        ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject().put("images",candidates));
        mode="invalid-final";
        try(RequestControl c=new RequestControl(20000)){rejects(()->ClassroomSummary.generate(dir,LESSON,AI,40,c,s->{},"保留英文术语"),"final image citation outside selected set rejected");}
        JSONObject partial=ClassroomStore.read(new File(dir,"summary.json"));
        check(!partial.getBoolean("complete")&&partial.getJSONArray("images").length()==0,"invalid final preserves unillustrated draft");
        JSONObject failed=ClassroomStore.read(new File(dir,"run.json"));
        check(failed.getString("final_output").contains("image-999.jpg")&&failed.getJSONObject("final_validation").getString("status").equals("failed"),"failed final preserves raw model output and explicit validation status");
        check(failed.getJSONObject("final_validation").getString("error").contains("image-999.jpg")&&!failed.has("final"),"audit identifies the actual invalid filename without accepting a final summary");
        check(failed.getString("final_prompt").contains("不能自行重编号")&&failed.getString("final_prompt").contains("notes.images仅填写")&&failed.getLong("final_finished_at")>=failed.getLong("final_started_at"),"final request records sparse-filename contract and response timing");
        mode="normal";JSONObject result;
        try(RequestControl c=new RequestControl(20000)){result=ClassroomSummary.generate(dir,LESSON,AI,40,c,s->{},"保留英文术语");}
        check(calls==3&&result.getJSONArray("images").length()==40,"final validation retry reuses first round and keeps forty-image cap");
        check(requests.get(0).getJSONArray("messages").getJSONObject(1).getJSONArray("content").length()==1
                &&requests.get(2).getJSONArray("messages").getJSONObject(1).getJSONArray("content").length()==41,"HTTP requests send zero then exactly forty images");
        JSONObject state=ClassroomStore.read(new File(dir,"run.json"));
        check(state.getJSONObject("final_validation").getString("status").equals("succeeded")&&!state.getString("final_output").contains("image-999.jpg"),"successful continuation replaces stale failed-final evidence with current response");
        check(state.getString("first_output").startsWith("```json\n")&&state.getString("first_output").contains("\n  "),"raw first output retains original fences and whitespace");
        check(state.getString("config").equals(ClassroomSummary.configKey(AI,40,"保留英文术语"))&&state.getString("config").endsWith("\n10\n保留英文术语"),"shared configuration key explicitly upgrades pipeline version");
    }
    private static void prefixedFinal()throws Exception {
        reset();mode="prefixed-final";File dir=materialDir("prefixed-final-"+System.nanoTime());JSONObject result;
        try(RequestControl c=new RequestControl(20000)){result=ClassroomSummary.generate(dir,LESSON,AI,3,c,s->{},"");}
        check(calls==2&&result.getBoolean("complete"),"directory-prefixed actual image result completes without a paid repair request");
        ByteArrayOutputStream zip=new ByteArrayOutputStream();ClassroomExport.write(dir,result,zip);Map<String,byte[]> entries=unzip(zip.toByteArray());
        check(entries.keySet().stream().filter(name->name.startsWith("images/")).count()==3&&!entries.keySet().stream().anyMatch(name->name.contains("images/images/")),"prefixed arrays export original three images with one directory prefix");
        String md=new String(entries.get("课堂总结.md"),StandardCharsets.UTF_8);
        String first=result.getJSONArray("images").getJSONObject(0).getString("file");
        check(md.contains("$x^2$")&&md.indexOf("images/"+first)==md.lastIndexOf("images/"+first),"inline image is not appended twice and math is preserved");
        ClassroomDocument.validate(ClassroomDocument.portable(result,ClassroomStore.read(new File(dir,"materials.json"))));check(true,"prefixed references satisfy the shared portable contract");
    }
    private static void finalTransportAudit()throws Exception {
        reset();File dir=materialDir("final-transport-audit-"+System.nanoTime());failCall=2;
        try(RequestControl c=new RequestControl(20000)){rejects(()->ClassroomSummary.generate(dir,LESSON,AI,3,c,s->{},""),"second-round HTTP failure is preserved");}
        JSONObject failed=ClassroomStore.read(new File(dir,"run.json"));
        check(failed.getJSONObject("final_validation").getString("stage").equals("transport")&&failed.getJSONObject("final_validation").getString("status").equals("failed"),"transport failure is distinct from response validation failure");
        check(!failed.has("final_output")&&!failed.has("final")&&failed.has("draft"),"failed transport never invents a model output or discards the existing draft");
        check(failed.getLong("final_finished_at")>=failed.getLong("final_started_at"),"failed transport closes its own audit timing");
        failCall=-1;mode="slow";
        try(RequestControl c=new RequestControl(20000)){
            Thread cancel=new Thread(()->{try{Thread.sleep(100);c.cancel();}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}});cancel.start();
            rejects(()->ClassroomSummary.generate(dir,LESSON,AI,3,c,s->{},""),"cancelled second-round request is not accepted");cancel.join();
        }
        JSONObject cancelled=ClassroomStore.read(new File(dir,"run.json"));
        check(cancelled.getJSONObject("final_validation").getString("status").equals("cancelled")&&!cancelled.has("final_output"),"explicit cancellation is audited separately without stale model output");
    }
    private static void textOnlyAndInvalidRetry()throws Exception {
        reset();File dir=materialDir("text-only-"+System.nanoTime());
        check(ClassroomSummary.DEFAULT_IMAGES==0&&ClassroomSummary.MAX_IMAGES==40,"default image upload is disabled; authorized budget remains forty");
        try(RequestControl c=new RequestControl(20000)){JSONObject summary=ClassroomSummary.generate(dir,LESSON,AI,0,c,s->{},"保留英文术语");check(summary.getBoolean("complete")&&summary.getJSONArray("images").length()==0,"zero-budget pipeline completes without images");}
        check(calls==1,"text-only performs exactly one model request");
        for(JSONObject request:requests)check(request.getJSONArray("messages").getJSONObject(1).getJSONArray("content").length()==1,"text-only HTTP request has no image_url item");
        reset();File invalid=materialDir("invalid-reference-"+System.nanoTime());mode="invalid-source";
        try(RequestControl c=new RequestControl(20000)){rejects(()->ClassroomSummary.generate(invalid,LESSON,AI,0,c,s->{},"保留英文术语"),"invalid AI subtitle citation fails before checkpoint");}
        JSONObject run=ClassroomStore.read(new File(invalid,"run.json"));check(run==null||!run.has("draft"),"invalid subtitle response never persisted as successful draft");
        mode="invalid-image";
        try(RequestControl c=new RequestControl(20000)){rejects(()->ClassroomSummary.generate(invalid,LESSON,AI,0,c,s->{},"保留英文术语"),"invalid AI image citation fails before checkpoint");}
        mode="normal";try(RequestControl c=new RequestControl(20000)){check(ClassroomSummary.generate(invalid,LESSON,AI,0,c,s->{},"保留英文术语").getBoolean("complete"),"invalid-response retry resumes successfully");}
        check(calls==3,"invalid draft retried and successful full transcript sent once");
        for(String scenario:List.of("no-request","no-match")) {
            reset();mode=scenario;File empty=materialDir(scenario+System.nanoTime());
            try(RequestControl c=new RequestControl(20000)){JSONObject summary=ClassroomSummary.generate(empty,LESSON,AI,20,c,s->{},"保留英文术语");check(summary.getBoolean("complete")&&summary.getJSONArray("images").length()==0,"no requested matching image completes using draft: "+scenario);JSONObject audit=summary.getJSONObject("image_audit");check(audit.getString("selection").equals(scenario.equals("no-request")?"not_needed":"no_matching_frame")&&!audit.getJSONObject("assessment").getString("reason").isBlank(),"declined or unmatched image request retains its distinct audit reason: "+scenario);}
            check(calls==1,"no empty-image second round: "+scenario);
        }
        reset();mode="invalid-window";File window=materialDir("invalid-window"+System.nanoTime());
        try(RequestControl c=new RequestControl(20000)){rejects(()->ClassroomSummary.generate(window,LESSON,AI,20,c,s->{},"保留英文术语"),"reversed window rejected before second round");}
        check(calls==1&&!ClassroomStore.read(new File(window,"run.json")).has("draft"),"invalid window never checkpoints successful draft");
        for(String scenario:List.of("missing-assessment","missing-decision-reason","missing-request-reason","conflicting-decision","unauthorized-request")){
            reset();mode=scenario;File malformed=materialDir(scenario+System.nanoTime());
            try(RequestControl c=new RequestControl(20000)){rejects(()->ClassroomSummary.generate(malformed,LESSON,AI,scenario.equals("unauthorized-request")?0:40,c,s->{},""),"invalid image assessment rejected: "+scenario);}
            JSONObject evidence=ClassroomStore.read(new File(malformed,"run.json"));check(calls==1&&!evidence.has("draft")&&evidence.has("first_output"),"invalid decision has raw audit evidence but no successful draft or image upload: "+scenario);
        }
        reset();File noFrames=materialDir("unavailable"+System.nanoTime());ClassroomStore.write(new File(noFrames,"candidates.json"),new JSONObject().put("images",new JSONArray()));
        try(RequestControl c=new RequestControl(20000)){JSONObject summary=ClassroomSummary.generate(noFrames,LESSON,AI,40,c,s->{},"");check(calls==1&&summary.getJSONObject("image_audit").getString("selection").equals("unavailable"),"unavailable frames are audited without a second AI request");}
    }
    private static void retention()throws Exception {
        check(ClassroomDocument.exportMarkdown("![图](image-0.jpg) $x^2$").equals("![图](images/image-0.jpg) $x^2$"),"export canonicalizes bare image paths without changing math");
        android.content.Context context=new android.content.Context(new File(root,"retention-"+System.nanoTime()));
        File dir=new ClassroomStore(context,"account").lesson(LESSON);
        JSONObject note=new JSONObject().put("title","公式").put("text","$x^2$\n![图](images/image-0.jpg)").put("images",new JSONArray()).put("sources",new JSONArray().put(0));
        JSONObject summary=new JSONObject().put("lesson",LESSON.json()).put("updated",1).put("complete",true).put("run","finished").put("overview","重点").put("raw_images",2).put("candidate_images",2)
                .put("images",new JSONArray().put(picture(0,0)).put(picture(1,1)))
                .put("parts",new JSONArray().put(new JSONObject().put("notes",new JSONArray().put(note)).put("assignments",new JSONArray())));
        JSONObject materials=new JSONObject().put("segments",new JSONArray().put(segment(0,"字幕"))).put("images",new JSONArray());
        ClassroomStore.write(new File(dir,"summary.json"),summary);ClassroomStore.write(new File(dir,"materials.json"),materials);
        for(String name:List.of("raw-0.img","image-0.jpg","image-1.jpg"))ClassroomStore.writeBytes(new File(dir,name),image(100));
        ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject());
        check(ClassroomDocument.referencedImages(summary).equals(Set.of("image-0.jpg")),"inline image is part of retention contract");
        JSONObject portable=ClassroomDocument.portable(summary,materials);check(portable.has("segments")&&!portable.has("materials"),"portable document excludes remote material manifest");
        check(ClassroomStore.cleanUnused(context,false,1000)==0,"seven-day age gate preserves recent completed lesson");
        ClassroomStore.begin(dir);try{check(ClassroomStore.cleanUnused(context,true,Long.MAX_VALUE)==0,"manual cleanup protects active lesson");}finally{ClassroomStore.end(dir);}
        ClassroomStore.write(new File(dir,"run.json"),new JSONObject().put("run","replacement"));
        check(ClassroomStore.cleanUnused(context,true,Long.MAX_VALUE)==0,"interrupted replacement protects previous summary and retry files");
        ClassroomStore.write(new File(dir,"run.json"),new JSONObject().put("run","finished").put("final",new JSONObject()));
        check(ClassroomStore.cleanUnused(context,false,ClassroomStore.UNUSED_RETENTION_MILLIS+2)>0,"aged completed lesson cleaned");
        check(new File(dir,"image-0.jpg").isFile()&&!new File(dir,"image-1.jpg").exists()&&!new File(dir,"raw-0.img").exists(),"cleanup retains inline image only");
        check(new File(dir,"materials.json").isFile()&&new File(dir,"summary.json").isFile(),"cleanup retains complete text and source transcript");
        check(!new File(dir,"candidates.json").exists()&&!new File(dir,"run.json").exists(),"cleanup invalidates raw-dependent retry checkpoints");
        ByteArrayOutputStream archive=new ByteArrayOutputStream();ClassroomExport.write(dir,summary,archive,p->{});
        Map<String,byte[]> entries=unzip(archive.toByteArray());
        check(entries.containsKey("images/image-0.jpg")&&!entries.containsKey("images/image-1.jpg"),"export after cleanup uses actual references rather than analysed inventory");
        check(new String(entries.get("课堂总结.md"),StandardCharsets.UTF_8).contains("$x^2$"),"Markdown math source survives export");
        summary.put("complete",false);ClassroomStore.write(new File(dir,"summary.json"),summary);ClassroomStore.writeBytes(new File(dir,"raw-1.img"),image(100));
        check(ClassroomStore.cleanUnused(context,true,Long.MAX_VALUE)==0&&new File(dir,"raw-1.img").isFile(),"manual cleanup preserves unfinished draft assets");
    }
    private static void transport()throws Exception {
        reset();mode="silent-reasoning";
        try(RequestControl c=new RequestControl(40_000)) {
            long began=System.nanoTime();
            String silent=new ChatCompletionClient(AI,c).complete("s","p",List.of());
            check(silent.contains("合并重点")&&TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-began)>=30_000,
                    "text-only SSE without reasoning survives 31 seconds of real loopback silence");
        }
        reset();ChatCompletionClient client=new ChatCompletionClient(AI);String answer=client.complete("测试任务","合成输入",List.of(new byte[]{1,2,3}));
        check(answer.equals("{\"overview\":\"合并重点\"}"),"general completion returns non-schedule JSON from SSE");JSONObject request=requests.get(0);
        check(!request.has("response_format")&&!request.has("prompt_cache_key"),"general completion removes schedule schema/cache key");check(request.getString("reasoning_effort").equals("high")&&request.getInt("max_completion_tokens")==32768,"reasoning and token options preserved");
        check(request.getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(1).getJSONObject("image_url").getString("url").equals("data:image/jpeg;base64,AQID"),"selected image bytes actually sent");
        reset();mode="json";check(client.complete("s","p",List.of()).contains("overview"),"non-SSE compatible response accepted");
        reset();mode="downgrade";client.complete("s","p",List.of());check(calls==2&&!requests.get(1).has("stream_options")&&requests.get(1).getBoolean("stream"),"one explicit option retry preserves streaming");
        reset();mode="length";rejects(()->client.complete("s","p",List.of()),"truncated completion rejected");mode="filter";rejects(()->client.complete("s","p",List.of()),"filtered completion rejected");
        reset();mode="schedule";StringBuilder preview=new StringBuilder(),reasoning=new StringBuilder();client.parse(1,"明天开会",null,null,0,"UTC",new ChatCompletionClient.PreviewSink(){public void append(String s){preview.append(s);}public void appendReasoning(String s){reasoning.append(s);}});check(preview.toString().equals("{\"events\":[]}")&&reasoning.toString().equals("合成推理"),"existing schedule HTTP SSE preview and reasoning retained");check(requests.get(0).has("response_format"),"schedule parse retains schema");
        reset();try(RequestControl c=new RequestControl(5000)){c.cancel();rejects(()->new ChatCompletionClient(AI,c).complete("s","p",List.of()),"cancelled completion rejected before transport");check(calls==0,"cancel sends no request");}
        reset();mode="slow";try(RequestControl c=new RequestControl(80)){rejects(()->new ChatCompletionClient(AI,c).complete("s","p",List.of()),"deadline interrupts loopback network request");}Thread.sleep(550);reset();
    }
    private static void downloadedHistory()throws Exception {
        File home=new File(root,"history-"+System.nanoTime());android.content.Context context=new android.content.Context(home);
        ClassroomStore store=new ClassroomStore(context,"account-one");store.catalog(List.of(LESSON));
        File dir=store.lesson(LESSON);
        ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("segments",new JSONArray().put(segment(0,"字幕"))));
        check(store.history().isEmpty(),"partial source download is not archived");
        ClassroomStore.write(new File(dir,"candidates.json"),new JSONObject().put("images",new JSONArray()));
        List<JSONObject> rows=store.history();
        check(rows.size()==1&&rows.get(0).getBoolean("downloaded")&&!rows.get(0).optBoolean("complete"),"complete download enters history before AI");
        check(rows.get(0).getJSONObject("lesson").getString("id").equals(LESSON.id),"existing cached download resolved from saved catalogue");
        ClassroomStore.write(new File(dir,"materials.json"),new JSONObject().put("segments",new JSONArray().put(segment(0,"字幕")))
                .put("images",new JSONArray()).put("raw_images",0));
        new android.util.AtomicFile(new File(dir,"candidates.json")).delete();
        try(RequestControl control=new RequestControl(5000)){ClassroomSummary.prepare(dir,LESSON,new ClassroomClient(),control,p->{});}
        check(ClassroomStore.read(new File(dir,"candidates.json")).getJSONObject("lesson").getString("id").equals(LESSON.id),"actual successful preparation persists download identity");
        store.catalog(List.of());check(store.history().size()==1,"download metadata survives removal from remote catalogue");
        check(new ClassroomStore(context,"account-two").history().isEmpty(),"download history remains account isolated");
        JSONObject draft=new JSONObject().put("lesson",LESSON.json()).put("updated",124).put("complete",false);
        ClassroomStore.write(new File(dir,"summary.json"),draft);
        check(store.history().size()==1&&!store.history().get(0).optBoolean("downloaded"),"draft replaces download entry without duplicates");
        draft.put("complete",true);ClassroomStore.write(new File(dir,"summary.json"),draft);
        check(store.history().size()==1&&store.history().get(0).getBoolean("complete"),"finished summary replaces draft in history");
        File shared=new File(ClassroomStore.root(context),"shared/1-2");shared.mkdirs();ClassroomStore.write(new File(shared,"summary.json"),draft);
        check(store.history().size()==1,"shared and account summary deduplicate by course and lesson");
    }
    private static void completeDownloadParity()throws Exception {
        JSONObject fresh=new JSONObject().put("segments",new JSONArray().put(segment(0,"正式字幕一")).put(segment(12,"正式字幕二")))
                .put("images",new JSONArray().put(new JSONObject().put("url","first").put("second",1))
                        .put(new JSONObject().put("url","middle").put("second",4)).put(new JSONObject().put("url","last").put("second",9))).put("raw_images",3);
        JSONObject partial=new JSONObject().put("segments",new JSONArray().put(segment(2,"直播临时修订")))
                .put("images",new JSONArray().put(new JSONObject().put("url","removed").put("second",20))
                        .put(new JSONObject().put("url","middle").put("second",3))).put("raw_images",2).put("updated",1).put("monitoring",true);
        File base=new File(root,"complete-parity-"+System.nanoTime()),direct=new File(base,"direct"),watched=new File(base,"watched"),resumed=new File(base,"manual-after-monitor");
        direct.mkdirs();watched.mkdirs();resumed.mkdirs();
        ClassroomClient[] clients={new ClassroomClient(),new ClassroomClient(),new ClassroomClient()};
        for(ClassroomClient client:clients){client.value=new JSONObject(fresh.toString());client.images.put("first",image(10));client.images.put("middle",image(11));client.images.put("last",image(100));}
        for(File dir:List.of(watched,resumed)){
            ClassroomStore.write(new File(dir,"monitor-materials.json"),partial);
            ClassroomStore.writeBytes(new File(dir,"raw-0.img"),image(70));ClassroomStore.writeBytes(new File(dir,"raw-1.img"),image(11));
        }
        try(RequestControl control=new RequestControl(10000)){
            ClassroomSummary.prepare(direct,LESSON,clients[0],control,p->{});
            ClassroomSummary.prepareComplete(watched,LESSON,fresh,partial,clients[1],control,p->{});
            ClassroomSummary.prepare(resumed,LESSON,clients[2],control,p->{});
            for(File dir:List.of(watched,resumed)){
                JSONObject material=ClassroomStore.read(new File(dir,"materials.json"));
                check(material.getJSONArray("segments").toString().equals(fresh.getJSONArray("segments").toString()),"both completion entrances retain exactly the authoritative transcript");
                check(!material.getBoolean("monitoring")&&material.getInt("raw_images")==3,"complete material only published after all formal images validate");
                JSONArray images=material.getJSONArray("images");check(images.length()==3,"provisional-only PPT is excluded from final download");
                for(int i=0;i<3;i++)check(images.getJSONObject(i).getString("url").equals(fresh.getJSONArray("images").getJSONObject(i).getString("url"))
                        &&images.getJSONObject(i).getDouble("second")==fresh.getJSONArray("images").getJSONObject(i).getDouble("second"),"final PPT source and time match direct download");
                JSONArray expected=ClassroomStore.read(new File(direct,"candidates.json")).getJSONArray("images"),actual=ClassroomStore.read(new File(dir,"candidates.json")).getJSONArray("images");
                check(actual.length()==expected.length(),"direct and completed download use identical near-frame selection");
                for(int i=0;i<actual.length();i++)check(actual.getJSONObject(i).getDouble("second")==expected.getJSONObject(i).getDouble("second")
                        &&Arrays.equals(ClassroomStore.bytes(new File(dir,actual.getJSONObject(i).getString("raw"))),ClassroomStore.bytes(new File(direct,expected.getJSONObject(i).getString("raw")))),"selected PPT timing and actual bytes match despite stable different cache indexes");
            }
            check(clients[0].downloads==3&&clients[1].downloads==2&&clients[2].downloads==2,"completion reuses matching verified PPT and downloads only missing sources");
            ClassroomSummary.prepare(watched,LESSON,clients[1],control,p->{});check(clients[1].downloads==2&&clients[1].materialReads==0,"completed formal cache does not redownload on direct reopen");
        }
    }
    private static void localLessonLifecycle()throws Exception {
        android.content.Context context=new android.content.Context(new File(root,"local-lifecycle-"+System.nanoTime()));
        ClassroomStore cache=new ClassroomStore(context,"owner"),other=new ClassroomStore(context,"other");
        File local=cache.localLesson(LESSON),foreign=other.localLesson(LESSON);
        File shared=new File(ClassroomStore.root(context),"shared/1-2");shared.mkdirs();
        JSONObject summary=new JSONObject().put("lesson",LESSON.json()).put("complete",true).put("overview","![原图](images/image-1.jpg)").put("parts",new JSONArray());
        ClassroomStore.write(new File(local,"summary.json"),summary);ClassroomStore.write(new File(shared,"summary.json"),summary);
        ClassroomStore.write(new File(foreign,"summary.json"),summary);ClassroomStore.writeBytes(new File(local,"image-1.jpg"),image(50));
        ClassroomStore.writeBytes(new File(shared,"image-1.jpg"),image(60));ClassroomStore.writeBytes(new File(local,"image-2.jpg"),image(70));
        ClassroomStore.writeBytes(new File(local,"raw-0.img"),image(80));ClassroomStore.write(new File(local,"run.json"),new JSONObject().put("private",true));
        check(cache.summaryImage(LESSON,"image-1.jpg").equals(new File(local,"image-1.jpg")),"published image resolves actual reference in current local summary");
        rejects(()->cache.summaryImage(LESSON,"image-2.jpg"),"unreferenced published image rejected");
        rejects(()->cache.summaryImage(LESSON,"raw-0.img"),"summary route cannot read raw cache");
        rejects(()->cache.summaryImage(LESSON,"run.json"),"summary route cannot read checkpoints");
        rejects(()->cache.summaryImage(LESSON,"../image-1.jpg"),"summary traversal rejected");
        boolean[] terminal={false};ClassroomStore.begin(local);
        try{rejects(()->cache.deleteLesson(LESSON,()->terminal[0]=true),"active lesson cannot be deleted");check(!terminal[0]&&new File(local,"summary.json").isFile(),"active rejection leaves state and all content intact");}finally{ClassroomStore.end(local);}
        ClassroomStore.begin(shared);try{rejects(()->cache.deleteLesson(LESSON,()->terminal[0]=true),"active restored lesson cannot be deleted");}finally{ClassroomStore.end(shared);}
        rejects(()->cache.deleteLesson(LESSON,()->{throw new IOException("cannot record terminal state");}),"terminal persistence failure aborts deletion");
        check(local.isDirectory()&&shared.isDirectory(),"terminal failure preserves both local copies");
        new File(local,"summary.json").delete();check(cache.summaryImage(LESSON,"image-1.jpg").equals(new File(shared,"image-1.jpg")),"restored summary keeps the same published image source");
        ClassroomStore.write(new File(local,"summary.json"),summary);File sibling=cache.localLesson(new ClassroomLesson("1","3","other","other","","6",1));
        File previous=new File(shared.getParentFile(),"1-2.previous");previous.mkdirs();ClassroomStore.write(new File(previous,"summary.json"),summary);
        ClassroomStore.write(new File(sibling,"summary.json"),summary);cache.catalog(List.of(LESSON));
        cache.deleteLesson(LESSON,()->terminal[0]=true);
        check(terminal[0]&&!local.exists()&&!shared.exists(),"confirmed deletion removes only both copies of the target lesson including references and checkpoints");
        check(!previous.exists(),"old local restore backup cannot resurrect a deleted lesson");
        rejects(()->ClassroomStore.begin(local),"a stale pending worker cannot lease the deleted directory");
        check(foreign.isDirectory()&&sibling.isDirectory()&&cache.catalog().size()==1,"other account, other lesson and school catalogue retained");
        rejects(()->cache.summaryImage(LESSON,"image-1.jpg"),"deleted published reference is immediately unavailable");
    }
    public static void main(String[] args)throws Exception {
        root=new File(args[0]);root.mkdirs();
        if(args.length>1&&"local-lifecycle".equals(args[1])){test("published image and local deletion boundaries",ClassroomSummaryCheck::localLessonLifecycle);System.out.println("LocalLessonLifecycle: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Local lifecycle check failed");return;}
        HttpServer server=server();try {
            if(args.length>1&&"image-references".equals(args[1])){test("image reference validation",ClassroomSummaryCheck::validation);test("final validation evidence and continuation",ClassroomSummaryCheck::capAndFinalValidation);test("prefixed final and export",ClassroomSummaryCheck::prefixedFinal);test("final transport and cancellation audit",ClassroomSummaryCheck::finalTransportAudit);System.out.println("ImageReferences: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Image reference check failed");return;}
            test("published image and local deletion boundaries",ClassroomSummaryCheck::localLessonLifecycle);
            test("direct and monitored download parity",ClassroomSummaryCheck::completeDownloadParity);
            test("downloaded classroom history",ClassroomSummaryCheck::downloadedHistory);
            test("subtitle compression",ClassroomSummaryCheck::subtitleCompression);
            test("inline images, portable document and safe retention",ClassroomSummaryCheck::retention);
            test("HTTP forty-image cap and final validation retry",ClassroomSummaryCheck::capAndFinalValidation);
            test("prefixed final and export",ClassroomSummaryCheck::prefixedFinal);
            test("final transport and cancellation audit",ClassroomSummaryCheck::finalTransportAudit);
            test("requested image windows",ClassroomSummaryCheck::windowSelection);test("reference validation",ClassroomSummaryCheck::validation);test("low-resolution dedup and budget",ClassroomSummaryCheck::images);test("transport options/cancel/deadline/SSE",ClassroomSummaryCheck::transport);test("checkpoint, retry and offline ZIP",ClassroomSummaryCheck::pipeline);test("text-only and invalid-response retry",ClassroomSummaryCheck::textOnlyAndInvalidRetry);test("anonymous account isolation",ClassroomSummaryCheck::isolation);
        }finally{server.stop(0);}System.out.println("ClassroomSummaryCheck: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError(failures+" classroom regression failures");
    }
}
