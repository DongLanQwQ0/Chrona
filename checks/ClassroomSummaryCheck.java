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
    private static final List<Integer> requestBytes = new ArrayList<>();
    private static final Map<Integer,Integer> chunkCalls = new HashMap<>();
    private static final ClassroomLesson LESSON = new ClassroomLesson("1","2","测试课程","单课","教室","6",1);
    private static final AiSettings AI = new AiSettings("https://api.openai.com/v1","gpt-5.1","synthetic-test-key","high",AiSettings.CHAT_COMPLETIONS);
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
                .put("affairs",new JSONArray()).put("assignments",new JSONArray().put(new JSONObject().put("text","下周交作业").put("sources",new JSONArray().put(id))));
    }
    private static byte[] image(int gray)throws Exception {
        BufferedImage image=new BufferedImage(96,96,BufferedImage.TYPE_INT_RGB);int color=gray*0x010101;
        for(int x=0;x<96;x++)for(int y=0;y<96;y++)image.setRGB(x,y,color);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);return out.toByteArray();
    }
    private static void reset(){calls=0;failCall=-1;mode="normal";requests.clear();requestBytes.clear();chunkCalls.clear();}
    private static String referencedPrompt(JSONArray history,String prompt)throws Exception {
        var reference=java.util.regex.Pattern.compile("完整要求见历史第(\\d+)条消息").matcher(prompt);
        return reference.find()?history.getJSONObject(Integer.parseInt(reference.group(1))-1).getJSONArray("content").getJSONObject(0).getString("text"):prompt;
    }
    private static HttpServer server()throws Exception {
        HttpServer server=HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(),0),0);
        server.setExecutor(Executors.newCachedThreadPool(r->{Thread t=new Thread(r);t.setDaemon(true);return t;}));
        server.createContext("/",exchange->{try {
            byte[] wire=exchange.getRequestBody().readAllBytes();requestBytes.add(wire.length);
            JSONObject body=new JSONObject(new String(wire,StandardCharsets.UTF_8));
            calls++;requests.add(body);JSONArray history=body.getJSONArray("messages");String prompt=referencedPrompt(history,history.getJSONObject(history.length()-1).getJSONArray("content").getJSONObject(0).getString("text"));
            boolean second=prompt.contains("本次images清单：\n")||prompt.contains("上一条最终总结未通过校验");
            JSONObject chunk=null;
            if(second) {
                String first=history.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text");
                chunk=new JSONObject(first.substring(first.indexOf("资料：\n")+4)).put("images",new JSONArray((prompt.contains("本次images清单：\n")?prompt:history.getJSONObject(3).getJSONArray("content").getJSONObject(0).getString("text")).split("本次images清单：\n",2)[1]));
            } else if(prompt.contains("资料：\n"))chunk=new JSONObject(prompt.substring(prompt.indexOf("资料：\n")+4)); else if(prompt.contains("上一条总结校验失败")){String original=history.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text");chunk=new JSONObject(original.substring(original.indexOf("资料：\n")+4));}
            if(chunk!=null){int id=chunk.getJSONArray("segments").getJSONObject(0).getInt("id");chunkCalls.merge(id,1,Integer::sum);}
            if(mode.equals("slow"))Thread.sleep(500);
            if(mode.equals("silent-reasoning"))Thread.sleep(31_000);
            int status=200;String payload,type="text/event-stream";
            if(calls==failCall){status=503;type="application/json";payload="{\"error\":{\"message\":\"synthetic failure\"}}";}
            else if(mode.equals("downgrade")&&body.has("stream_options")){status=400;type="application/json";payload="{\"error\":{\"message\":\"unsupported parameter stream_options\"}}";}
            else {
                JSONObject generated=chunk==null?new JSONObject().put("overview","合并重点"):part(chunk);
                if(second&&chunk!=null){JSONArray reviews=new JSONArray();JSONArray files=chunk.getJSONArray("images");
                    for(int i=0;i<files.length();i++)reviews.put(new JSONObject().put("file",files.getJSONObject(i).getString("file")).put("status","usable").put("confidence","high").put("visible","合成测试图中的黑白线条").put("reason","合成图支持本条笔记"));generated.put("image_reviews",reviews);}
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
                if(chunk!=null&&!second&&mode.equals("invalid-draft-markdown"))generated.getJSONArray("notes").getJSONObject(0).put("text","![尚未看到的板书](lecture-slide-1.jpeg)");
                if(second&&mode.equals("invalid-final"))generated.getJSONArray("notes").getJSONObject(0).put("images",new JSONArray().put("image-999.jpg"));
                if(second&&mode.equals("prefixed-final")){
                    JSONObject note=generated.getJSONArray("notes").getJSONObject(0);JSONArray prefixed=new JSONArray();
                    for(int i=0;i<chunk.getJSONArray("images").length();i++)prefixed.put("images/"+chunk.getJSONArray("images").getJSONObject(i).getString("file"));
                    note.put("images",prefixed).put("text","保留公式 $x^2$。![已上传的图]("+prefixed.getString(0)+")");
                }
                boolean defect=mode.equals("json-always")||mode.equals("json-manual-malformed")
                        ||mode.startsWith("json-first-")&&calls==1||mode.startsWith("json-final-")&&calls==2;
                if(defect&&mode.endsWith("fields"))generated.remove(second?"notes":"image_requests");
                if(defect&&mode.endsWith("container"))generated.put("notes",new JSONObject().put("private","RAW_PRIVATE_PAYLOAD"));
                if(defect&&mode.endsWith("nested")){generated.getJSONObject("image_assessment").put("image_requests",generated.getJSONArray("image_requests"));generated.remove("image_requests");}
                String result=chunk!=null&&!second?"```json\n"+generated.toString(2)+"\n```":generated.toString();
                if(defect&&(mode.endsWith("malformed")||mode.equals("json-always")))result="{\"overview\":\"RAW_PRIVATE_PAYLOAD\"";
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
        for(int i=0;i<3;i++){segments.put(segment(i*100,"字幕 "+i+"甲".repeat(5000)));BufferedImage board=new BufferedImage(96,96,BufferedImage.TYPE_INT_RGB);for(int y=0;y<96;y++)for(int x=0;x<96;x++)board.setRGB(x,y,(x%12<2||y%12<2)?0x101010:0xeeeeee);ImageIO.write(board,"png",new File(dir,"raw-"+i+".img"));candidates.put(new JSONObject().put("raw","raw-"+i+".img").put("second",i*100).put("index",i));}
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
        check(firstPrompt.contains("应主动核图")&&firstPrompt.contains("六个顶层字段")&&!firstPrompt.contains("仅在确有需要时请求"),"first prompt requires explicit visual-gap review instead of optional image requests");
        String shapeMarker="完整对象：\n";int shapeStart=firstPrompt.indexOf(shapeMarker),shapeEnd=firstPrompt.indexOf("\n字段规则：",shapeStart);
        JSONObject draftShape=new JSONObject(firstPrompt.substring(shapeStart+shapeMarker.length(),shapeEnd));
        check(draftShape.keySet().equals(java.util.Set.of("overview","notes","affairs","assignments","image_assessment","image_requests")),"first-round complete JSON example includes affairs and the mandatory visual decision and requests");
        JSONObject imageAudit=result.getJSONObject("image_audit");
        check(imageAudit.getJSONObject("assessment").getString("decision").equals("request")&&!imageAudit.getJSONObject("assessment").getString("reason").isBlank(),"summary preserves the model's reason for requesting images");
        check(imageAudit.getInt("selected_count")==3&&imageAudit.getInt("analysed_count")==3&&checkpoint.getJSONObject("image_audit").getInt("analysed_count")==0,"audit distinguishes planned selection from actually analysed frames across failure");
        check(ClassroomStore.read(new File(dir,"run.json")).getJSONObject("image_audit").getInt("analysed_count")==3,"final run log records actual analysed count");
        check(new JSONObject(firstPrompt.substring(firstPrompt.indexOf("资料：\n")+4)).getJSONArray("segments").length()==3,"first round includes entire long transcript without chunks");
        JSONArray secondMessages=requests.get(2).getJSONArray("messages");
        JSONObject context=new JSONObject().put("first_system",secondMessages.getJSONObject(0).getString("content"));
        check(secondMessages.length()==5&&secondMessages.getJSONObject(1).toString().equals(firstMessages.getJSONObject(1).toString()),"full restart appends after the failed image request without repackaging the first message");
        for(int i=0;i<4;i++)check(secondMessages.getJSONObject(i).toString().equals(requests.get(1).getJSONArray("messages").getJSONObject(i).toString()),"image restart preserves exact already-sent history "+i);
        check(secondMessages.getJSONObject(4).getJSONArray("content").length()==1&&secondMessages.getJSONObject(4).toString().contains("从头完整重新生成当前板块"),"restart adds only complete-generation instructions, not another image upload");

        check(context.getString("first_system").contains("保留英文术语"),"classroom custom prompt reaches both rounds");
        check(context.getString("first_system").contains("数学符号与记法偏好中国大陆教材的常用风格。"),"general Chinese mathematical notation style reaches both rounds");
        for(String topic:List.of("评分规则","考试与测试安排","考核范围与题型","重点与透题线索","作业、实验与报告",
                "截止与补救机会","出勤与课堂表现","规则变更与纠错","复习与学习资源","其他重要提醒"))
            check(context.getString("first_system").contains(topic),"both real requests retain student concern: "+topic);
        check(context.getString("first_system").contains("优先突出前八类")&&context.getString("first_system").contains("区分明确要求、参考建议与含糊暗示")
                &&context.getString("first_system").contains("不补猜时间、比例、范围或承诺"),"priority and uncertainty are explicit");
        check(requests.get(2).getJSONArray("messages").getJSONObject(0).getString("content").equals(context.getString("first_system")),"final image round retains the same Chinese math style instructions");
        check(!ClassroomSummary.configKey(AI,20,"other").equals(ClassroomSummary.configKey(AI,20,"保留英文术语")),"custom prompt changes invalidate the previous run");
        check(secondMessages.getJSONObject(2).getString("content").equals(checkpoint.getString("first_output")),"second round contains exact raw first response");
        JSONArray secondContent=secondMessages.getJSONObject(3).getJSONArray("content");
        check(secondContent.length()==7&&checkpoint.getJSONArray("selected").length()==3,"second round includes matching inventory and three individually labelled actual images");
        for(int i=0;i<3;i++){
            check(secondContent.getJSONObject(i*2+1).getString("text").contains("图片文件：image-"+i+".jpg；"),"ordinary image label matches the immediately following attachment "+i);
            check(secondContent.getJSONObject(i*2+2).getJSONObject("image_url").getString("url").equals("data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(ClassroomStore.bytes(new File(dir,"image-"+i+".jpg")))),"second-round actual JPEG matches inventory "+i);
        }
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
        check(md.contains("## 课程事务")&&md.contains("下周交作业"),"new assignments export within course affairs without calendar writes");
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
                &&requests.get(2).getJSONArray("messages").getJSONObject(3).getJSONArray("content").length()==81&&requests.get(2).getJSONArray("messages").getJSONObject(5).getJSONArray("content").length()==1,"HTTP requests send zero then exactly forty labelled images, never reuploading on retry");
        JSONObject state=ClassroomStore.read(new File(dir,"run.json"));
        check(state.getJSONObject("final_validation").getString("status").equals("succeeded")&&!state.getString("final_output").contains("image-999.jpg"),"successful continuation replaces stale failed-final evidence with current response");
        check(state.getString("first_output").startsWith("```json\n")&&state.getString("first_output").contains("\n  "),"raw first output retains original fences and whitespace");
        check(state.getString("config").equals(ClassroomSummary.configKey(AI,40,"保留英文术语"))&&state.getString("config").endsWith("\n11\nfalse\n保留英文术语\nfigure_policy=1\naffairs_schema=1"),"shared configuration key records unchanged pipeline and explicit affairs policy version");
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
            JSONObject evidence=ClassroomStore.read(new File(malformed,"run.json"));check(calls==(scenario.equals("missing-assessment")?2:1)&&!evidence.has("draft")&&evidence.has("first_output"),"structural invalidity gets one repair; semantic decisions stop without repair, preserving raw evidence: "+scenario);
        }
        reset();File noFrames=materialDir("unavailable"+System.nanoTime());ClassroomStore.write(new File(noFrames,"candidates.json"),new JSONObject().put("images",new JSONArray()));
        try(RequestControl c=new RequestControl(20000)){JSONObject summary=ClassroomSummary.generate(noFrames,LESSON,AI,40,c,s->{},"");check(calls==1&&summary.getJSONObject("image_audit").getString("selection").equals("unavailable"),"unavailable frames are audited without a second AI request");}
    }
    private static JSONObject ordinary(File dir,int images)throws Exception {
        try(RequestControl control=new RequestControl(20000)){return ClassroomSummary.generate(dir,LESSON,AI,images,control,p->{},"");}
    }
    private static JSONObject run(File dir)throws Exception {return ClassroomStore.read(new File(dir,"run.json"));}
    private static String lastPrompt(int request)throws Exception {
        JSONArray messages=requests.get(request).getJSONArray("messages");
        return messages.getJSONObject(messages.length()-1).getJSONArray("content").getJSONObject(0).getString("text");
    }
    private static void draftImageBoundary(String prompt,String label){
        for(String rule:List.of("只处理字幕与PPT时间目录","尚未提供任何实际图片","所有images数组必须为[]",
                "所有文本字段均禁止插入Markdown图片、图片占位链接或自行命名文件","不得新增schema之外的字段",
                "只通过image_requests的时间范围请求","收到实际附图后的核图阶段才能引用给定file"))
            check(prompt.contains(rule),label+": "+rule);
    }
    private static void draftImageReferences()throws Exception {
        reset();mode="invalid-draft-markdown";File dir=materialDir("draft-unseen-markdown-"+System.nanoTime());
        JSONObject previous=new JSONObject().put("complete",true).put("marker","existing-published-document");
        ClassroomStore.write(new File(dir,"summary.json"),previous);
        rejects(()->ordinary(dir,3),"unseen noncanonical JPEG Markdown stops the ordinary draft");
        JSONObject failed=run(dir),conversation=failed.getJSONObject("conversation");String identity=failed.getString("run");
        check(calls==1&&!failed.has("draft")&&!failed.has("selected")&&!failed.has("final"),"semantic image failure gets no automatic repair or image upload and never commits a draft");
        check(ClassroomStore.read(new File(dir,"summary.json")).toString().equals(previous.toString()),"semantic image failure preserves the published document");
        check(failed.getString("first_output").contains("lecture-slide-1.jpeg")&&conversation.getJSONObject("outputs").getString("first").equals(failed.getString("first_output")),"exact failed image response remains available for audit");
        check(failed.getInt("first_attempt")==1&&failed.getString("first_error").contains("图片引用"),"semantic image error is persisted for explicit continuation");
        draftImageBoundary(lastPrompt(0),"ordinary initial draft boundary");
        check(requests.get(0).getJSONArray("messages").getJSONObject(1).getJSONArray("content").length()==1,"ordinary draft has no actual attached images");
        JSONArray prefix=new JSONArray(conversation.getJSONArray("messages").toString());String options=conversation.getJSONObject("options").toString();
        mode="prefixed-final";JSONObject result=ordinary(dir,3);
        check(result.getBoolean("complete")&&result.getString("run").equals(identity)&&calls==3,"explicit continuation submits one valid draft then one actual-image final in the same run");
        draftImageBoundary(lastPrompt(1),"ordinary appended semantic recovery boundary");
        JSONArray resumed=requests.get(1).getJSONArray("messages");
        check(resumed.length()==prefix.length()+1,"explicit continuation appends exactly one recovery user message");
        for(int i=0;i<prefix.length();i++)check(resumed.getJSONObject(i).toString().equals(prefix.getJSONObject(i).toString()),"ordinary original request and failed assistant stay exact "+i);
        JSONObject expectedOptions=new JSONObject(options).put("response_format",new JSONObject().put("type","json_object"));
        for(int n=0;n<requests.size();n++){JSONObject frozen=new JSONObject(requests.get(n).toString());frozen.remove("messages");check(frozen.similar(expectedOptions),"ordinary wire retains exact frozen base plus declared native JSON format "+n);}
        check(run(dir).getJSONObject("conversation").getJSONObject("options").toString().equals(options),"ordinary durable frozen options remain exact");
        check(!lastPrompt(2).contains(ClassroomSummary.DRAFT_IMAGE_RULE),"actual-image final does not inherit the draft-only ban");
        check(result.getJSONArray("parts").getJSONObject(0).getJSONArray("notes").getJSONObject(0).getString("text").contains("![已上传的图](images/image-"),"actual attached-image Markdown is still accepted after recovery");
        ClassroomStore.write(new File(root,"ordinary-draft-image-recovery-http.json"),new JSONObject().put("synthetic",true).put("fixture",dir.getName()).put("requests",new JSONArray(requests)).put("failed_run",failed).put("final_run",run(dir)));

        reset();mode="json-first-fields";File repair=materialDir("draft-image-format-repair-"+System.nanoTime());
        check(ordinary(repair,3).getBoolean("complete")&&calls==3,"ordinary malformed draft gets exactly one structural repair before actual-image final");
        draftImageBoundary(lastPrompt(1),"ordinary appended sole format repair boundary");
        check(!lastPrompt(2).contains(ClassroomSummary.DRAFT_IMAGE_RULE),"ordinary final stays free of draft boundary after structural repair");

        reset();failCall=1;File interrupted=materialDir("draft-image-transport-recovery-"+System.nanoTime());
        rejects(()->ordinary(interrupted,0),"ordinary draft transport interruption stops without retry");
        JSONArray sent=new JSONArray(run(interrupted).getJSONObject("conversation").getJSONArray("messages").toString());
        failCall=-1;check(ordinary(interrupted,0).getBoolean("complete")&&calls==2,"explicit recovery restarts only the interrupted ordinary draft");
        draftImageBoundary(lastPrompt(1),"ordinary appended interrupted-stage recovery boundary");
        JSONArray restarted=requests.get(1).getJSONArray("messages");
        for(int i=0;i<sent.length();i++)check(sent.getJSONObject(i).toString().equals(restarted.getJSONObject(i).toString()),"interrupted ordinary sent prefix stays exact "+i);
    }
    private static void jsonRepair()throws Exception {
        for(String phase:List.of("first","final"))for(String defect:List.of("malformed","fields","container","nested")){
            if(phase.equals("final")&&defect.equals("nested"))continue;
            reset();mode="json-"+phase+"-"+defect;File dir=materialDir(mode+System.nanoTime());
            check(ordinary(dir,phase.equals("final")?3:0).getBoolean("complete"),"complete structural regeneration: "+mode);
            JSONObject state=run(dir);int repair=phase.equals("final")?2:1;
            check(calls==repair+1&&state.getJSONObject("conversation").getJSONObject("outputs").has(phase+"-repair"),"exactly one appended format repair: "+mode);
            check(state.getString(phase+"_output").equals(state.getJSONObject("conversation").getJSONObject("outputs").getString(phase+"-repair")),"latest output is the actual repair response: "+mode);
            JSONArray turns=state.getJSONObject("conversation").getJSONArray("turns");for(int i=0;i<turns.length();i++)check(turns.getJSONObject(i).getInt("request_bytes")==requestBytes.get(i),"request_bytes is the actual UTF-8 wire length: "+mode+":"+i);
            check(lastPrompt(repair).contains("完整JSON对象")&&!lastPrompt(repair).contains("RAW_PRIVATE_PAYLOAD"),"repair prompt contains only safe failure projection: "+mode);
            check(lastPrompt(repair).contains(ClassroomSummary.DRAFT_IMAGE_RULE)==phase.equals("first"),"only draft structural repair adds the no-image boundary: "+mode);
            JSONArray before=requests.get(repair-1).getJSONArray("messages"),after=requests.get(repair).getJSONArray("messages");
            for(int i=0;i<before.length();i++)check(before.getJSONObject(i).toString().equals(after.getJSONObject(i).toString()),"repair preserves sent messages "+mode+":"+i);
            check(after.getJSONObject(after.length()-1).getJSONArray("content").length()==1,"repair does not append images again: "+mode);
        }
        reset();mode="json-always";File exhausted=materialDir("json-exhausted"+System.nanoTime());
        JSONObject previous=new JSONObject().put("complete",true).put("marker","prior-saved-summary");ClassroomStore.write(new File(exhausted,"summary.json"),previous);
        rejects(()->ordinary(exhausted,0),"malformed original and repair stop atomically");
        check(calls==2&&!run(exhausted).has("draft")&&!run(exhausted).has("final")&&ClassroomStore.read(new File(exhausted,"summary.json")).toString().equals(previous.toString()),"failed draft repair preserves prior published document");
        String identity=run(exhausted).getString("run");
        check(!run(exhausted).getString("first_error").contains("RAW_PRIVATE_PAYLOAD")&&run(exhausted).getString("first_output").contains("RAW_PRIVATE_PAYLOAD"),"safe failure is separate from exact raw output");
        mode="json-manual-malformed";rejects(()->ordinary(exhausted,0),"manual invalid JSON cannot buy a second repair");
        check(calls==3&&run(exhausted).getInt("first_attempt")==2,"repair budget remains exhausted across explicit retries");
        check(!lastPrompt(2).contains("RAW_PRIVATE_PAYLOAD"),"manual retry does not echo parser payload");
        JSONObject legacy=run(exhausted);legacy.put("first_error","Unterminated object at character 9210 of {RAW_PRIVATE_PAYLOAD");ClassroomStore.write(new File(exhausted,"run.json"),legacy);
        mode="normal";check(ordinary(exhausted,0).getString("run").equals(identity)&&calls==4,"next explicit generation recovers same run without successful-stage repetition");
        check(!run(exhausted).getString("first_error").contains("RAW_PRIVATE_PAYLOAD")&&!lastPrompt(3).contains("RAW_PRIVATE_PAYLOAD"),"legacy durable parser error is sanitized before manual continuation");
        for(String phase:List.of("first","final")){
            reset();mode="json-"+phase+"-malformed";File interrupted=materialDir("json-repair-network-"+phase+System.nanoTime());int repair=phase.equals("first")?2:3;failCall=repair;
            rejects(()->ordinary(interrupted,phase.equals("first")?0:3),"transport interruption during sole repair stops: "+phase);
            check(calls==repair&&run(interrupted).optInt(phase+"_attempt")==0,"repair transport failure does not consume validation attempt: "+phase);
            mode="normal";failCall=-1;check(ordinary(interrupted,phase.equals("first")?0:3).getBoolean("complete"),"manual recovery restarts same repair identity: "+phase);
            check(calls==repair+1&&lastPrompt(repair).contains("当前阶段："+phase+"-repair；重试序号：1")&&lastPrompt(repair).contains("从头完整重新生成"),"repair restarts with full response, no splicing: "+phase);
            JSONArray messages=requests.get(repair).getJSONArray("messages");check(messages.getJSONObject(messages.length()-1).getJSONArray("content").length()==1,"repair transport recovery never reuploads images: "+phase);
        }
        reset();mode="json-final-malformed";File finalBad=materialDir("json-final-exhausted"+System.nanoTime());failCall=3;
        rejects(()->ordinary(finalBad,3),"first final repair transport checkpoint created");
        failCall=-1;mode="json-always";rejects(()->ordinary(finalBad,3),"restarted repair may itself be malformed");
        JSONObject failure=run(finalBad);
        check(!failure.has("final")&&failure.has("draft")&&!ClassroomStore.read(new File(finalBad,"summary.json")).getBoolean("complete"),"failed final repair preserves draft without publishing incomplete final");
        check(!failure.getString("final_error").contains("RAW_PRIVATE_PAYLOAD")&&!failure.getJSONObject("final_validation").getString("error").contains("RAW_PRIVATE_PAYLOAD"),"final durable error projections exclude raw model content");
        reset();mode="length";File length=materialDir("json-length"+System.nanoTime());rejects(()->ordinary(length,0),"finish reason length stops before JSON repair");
        check(calls==1&&!run(length).getJSONObject("conversation").getJSONObject("outputs").has("first-repair"),"truncation never spends structural repair budget");
        mode="normal";check(ordinary(length,0).getBoolean("complete")&&calls==2&&lastPrompt(1).contains("当前阶段：first；重试序号：1"),"length recovers by complete stage restart");
        for(String raw:List.of("Unterminated object at character 9210 of {RAW_PRIVATE_PAYLOAD", "Value {RAW_PRIVATE_PAYLOAD} of type org.json.JSONObject cannot be converted to JSONArray", "JSONObject[\"notes\"] is not a JSONArray: RAW_PRIVATE_PAYLOAD"))
            check(!ClassroomJsonFailure.stored(raw).contains("RAW_PRIVATE_PAYLOAD"),"legacy parser detail projects safely");
        check(!ClassroomJsonFailure.detail(new JSONException("RAW_PRIVATE_PAYLOAD")).contains("RAW_PRIVATE_PAYLOAD"),"direct JSONException never exposes response or conversion values");
        for(String error:List.of("AI 服务 HTTP 503，请手动继续","JSON 配置字段尚未保存","图片引用不存在：image-999.jpg"))check(error.equals(ClassroomJsonFailure.stored(error)),"normal actionable errors survive stored projection: "+error);
    }
    private static final String SOURCE_SENTINEL="LARGE_SOURCE_SENTINEL_KEEP_EXACT";
    private static File largeMaterial(String name)throws Exception {
        File dir=materialDir(name+System.nanoTime());JSONObject materials=ClassroomStore.read(new File(dir,"materials.json"));
        JSONObject first=materials.getJSONArray("segments").getJSONObject(0);first.put("text",SOURCE_SENTINEL+first.getString("text"));
        ClassroomStore.write(new File(dir,"materials.json"),materials);return dir;
    }
    private static void shortHistory(File dir)throws Exception {
        JSONObject history=run(dir).getJSONObject("conversation");JSONArray messages=history.getJSONArray("messages");
        for(int n=0;n<requests.size();n++){
            JSONArray sent=requests.get(n).getJSONArray("messages");String all=sent.toString();
            check(all.split(java.util.regex.Pattern.quote(SOURCE_SENTINEL),-1).length-1==1,"large source appears only in its original user message for request "+n);
            check(lastPrompt(n).contains("首、末非空白字符必须分别为{和}")&&lastPrompt(n).contains("换行用\\n编码"),"every new JSON user request carries the wire format contract "+n);
            for(int i=0;i<sent.length();i++)check(sent.getJSONObject(i).toString().equals(messages.getJSONObject(i).toString()),"final state retains every sent message unchanged "+n+":"+i);
        }
        check(requests.get(0).getJSONArray("messages").getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text").startsWith("当前JSON阶段：first。"),"first physical JSON request exposes the exact stage anchor");
    }
    private static void shortJsonRequests()throws Exception {
        reset();mode="json-first-malformed";File repaired=largeMaterial("short-repair");check(ordinary(repaired,0).getBoolean("complete"),"short repair reuses original full schema and source");shortHistory(repaired);
        check(lastPrompt(1).contains("完整要求见历史第2条消息")&&!lastPrompt(1).contains("资料：\n"),"repair points to exact original user message rather than reposting source");
        JSONObject state=run(repaired);String safe="";try{ClassroomSummary.parseObject(state.getJSONObject("conversation").getJSONObject("outputs").getString("first"));}catch(JSONException invalid){safe=ClassroomJsonFailure.detail(invalid);}
        String oldPrompt="上一条回复的JSON结构或字段不完整："+safe+"。本阶段结果尚未提交。保留可核实事实、来源和图片编号，重新返回完整JSON对象，不输出补丁或剩余片段，不编造未见图片或新事实。这是该块唯一一次格式修复。\n本块要求："+state.getString("first_prompt");
        int currentBytes=requestBytes.get(1);JSONObject repeated=new JSONObject(requests.get(1).toString());JSONArray repeatedMessages=repeated.getJSONArray("messages");repeatedMessages.getJSONObject(repeatedMessages.length()-1).getJSONArray("content").getJSONObject(0).put("text",oldPrompt);
        mode="normal";new ChatCompletionClient(AI).completeConversation(repeated);int repeatedBytes=requestBytes.get(2);
        check(currentBytes<repeatedBytes&&repeatedBytes-currentBytes>40_000,"actual loopback wire payload removes the duplicated large source, without assuming a service limit");
        ClassroomStore.write(new File(root,"short-json-payloads.json"),new JSONObject().put("synthetic",true).put("short_repair_request_bytes",currentBytes).put("previous_repeated_prompt_request_bytes",repeatedBytes).put("saved_bytes",repeatedBytes-currentBytes));
        System.out.println("JSON loopback payload: short="+currentBytes+" bytes; previous repeated="+repeatedBytes+" bytes; saved="+(repeatedBytes-currentBytes)+" bytes");
        reset();mode="json-always";File manual=largeMaterial("short-manual");rejects(()->ordinary(manual,0),"single short repair may fail atomically");mode="json-manual-malformed";rejects(()->ordinary(manual,0),"manual bad JSON does not gain a second short repair");mode="normal";check(ordinary(manual,0).getBoolean("complete")&&calls==4,"manual continuation succeeds without repeating schema or source");shortHistory(manual);
        check(lastPrompt(2).contains("完整要求见历史第2条消息")&&lastPrompt(3).contains("完整要求见历史第2条消息"),"all manual attempts reference the same original stage request");
        for(boolean duringRepair:new boolean[]{false,true}){
            reset();mode=duringRepair?"json-first-malformed":"normal";File network=largeMaterial("short-network");failCall=duringRepair?2:1;
            rejects(()->ordinary(network,0),"interrupted request stops without automatic transport retry");int sent=calls;failCall=-1;mode="normal";
            check(ordinary(network,0).getBoolean("complete")&&calls==sent+1,"network recovery appends one complete-generation request only");shortHistory(network);
            check(!lastPrompt(sent).contains(SOURCE_SENTINEL)&&lastPrompt(sent).contains("完整要求见历史第2条消息"),"attempt-zero network recovery does not repeat the large prompt");
        }
        reset();mode="slow";File cancelled=largeMaterial("short-cancel");
        try(RequestControl control=new RequestControl(20000)){Thread stop=new Thread(()->{try{Thread.sleep(150);control.cancel();}catch(InterruptedException e){Thread.currentThread().interrupt();}});stop.start();rejects(()->ClassroomSummary.generate(cancelled,LESSON,AI,0,control,p->{},""),"in-flight JSON cancellation remains available");stop.join();}
        check(!run(cancelled).has("draft")&&!run(cancelled).has("final"),"cancellation never commits an unfinished block");Thread.sleep(550);mode="normal";check(ordinary(cancelled,0).getBoolean("complete"),"explicit continuation recovers the cancelled block");shortHistory(cancelled);
        reset();mode="json-first-malformed";File legacy=largeMaterial("short-legacy");JSONObject oldState=new JSONObject().put("run","legacy-short-reference");
        JSONObject material=ClassroomStore.read(new File(legacy,"materials.json"));JSONObject full=ClassroomSummary.lessonInput(material.getJSONArray("segments"),new JSONArray()).put("image_catalogue",ClassroomSummary.imageCatalogue(new JSONArray(),0));
        String original="本阶段完整schema与原始资料；"+ClassroomSummary.draftSchema(full.getJSONObject("image_catalogue"),0)+"资料：\n"+ClassroomSummary.uploadChunk(full);
        try(RequestControl control=new RequestControl(20000)){
            ClassroomConversation conversation=new ClassroomConversation(legacy,oldState,new ChatCompletionClient(AI,control),"LEGACY_SYSTEM_KEEP_EXACT");conversation.text("first",original,List.of());mode="normal";
            new ClassroomJsonStage(legacy,oldState,conversation,control).generate("first",original,List.of(),value->value.getJSONArray("notes"),"-retry-","用户授权继续：",null);
        }
        JSONArray oldMessages=oldState.getJSONObject("conversation").getJSONArray("messages");
        check(oldMessages.getJSONObject(0).getString("content").equals("LEGACY_SYSTEM_KEEP_EXACT")&&oldMessages.getJSONObject(1).getJSONArray("content").getJSONObject(0).getString("text").equals(original),"legacy system and unanchored original user are never rewritten");
        check(lastPrompt(1).contains("当前尚未提交板块")&&lastPrompt(1).contains("上文最近一次包含完整schema与原始资料")&&!lastPrompt(1).contains("沿用该消息中的阶段锚点"),"unanchored old history is referenced without inventing an old stage label");
        check(requests.get(1).toString().split(java.util.regex.Pattern.quote(SOURCE_SENTINEL),-1).length-1==1,"legacy repair preserves original source exactly once");
        String system=ClassroomSummary.systemPrompt("");check(system.contains("JSON字符串内的段落换行用\\n编码")&&!system.contains("段落使用真正换行"),"new system prompt correctly distinguishes JSON wire encoding from rendered paragraphs");
    }
    private static void jsonDiagnostics()throws Exception {
        for(String[] item:new String[][]{
            {"No value for image_requests","字段缺失：image_requests"},
            {"JSONObject[\"notes\"] not found.","字段缺失：notes"},
            {"JSONObject[\"notes\"] is not a JSONArray: RAW_PRIVATE_PAYLOAD","notes 应为数组"},
            {"Value {RAW_PRIVATE_PAYLOAD} at notes of type org.json.JSONObject cannot be converted to JSONArray","notes 应为数组"},
            {"Unterminated object at character 9210 of {RAW_PRIVATE_PAYLOAD","对象未闭合（字符 9210）"},
            {"Unterminated array at character 71 of [RAW_PRIVATE_PAYLOAD","数组未闭合（字符 71）"},
            {"Unterminated string at 8 [character 9 line 1] RAW_PRIVATE_PAYLOAD","字符串未闭合（字符 8）"},
            {"Invalid escape sequence at character 19 of RAW_PRIVATE_PAYLOAD","字符串转义无效（字符 19）"},
            {"Expected ':' after RAW_PRIVATE_PAYLOAD at character 3 of RAW_PRIVATE_PAYLOAD","缺少冒号分隔符（字符 3）"},
            {"Expected a ',' or '}' at character 4 of RAW_PRIVATE_PAYLOAD","对象缺少逗号或右花括号（字符 4）"}}){
            String safe=ClassroomJsonFailure.stored(item[0]);check(safe.contains(item[1])&&!safe.contains("RAW_PRIVATE_PAYLOAD"),"specific safe diagnostic: "+item[1]);
            check(ClassroomJsonFailure.isJsonFailure(item[0])&&ClassroomJsonFailure.isJsonFailure(safe),"old and safe JSON errors are recognised");
        }
        for(String hostile:List.of("No value for RAW_PRIVATE_PAYLOAD","No value for RAW_PRIVATE_PAYLOAD with spaces {\"x\":1}","JSONObject[\"RAW_PRIVATE_PAYLOAD\"] not found.","Value x at RAW_PRIVATE_PAYLOAD of type JSONObject cannot be converted to JSONArray","JSONObject[\"notes\"] is not a RAW_PRIVATE_PAYLOAD"))
            check(!ClassroomJsonFailure.stored(hostile).contains("RAW_PRIVATE_PAYLOAD"),"unknown field/type never becomes output");
        for(String business:List.of("课堂缓存无法读取","JSON 配置尚未保存","AI 请求失败（HTTP 413）"))check(!ClassroomJsonFailure.isJsonFailure(business)&&ClassroomJsonFailure.stored(business).equals(business),"ordinary business failure is not classified as model JSON");
        for(String[] root:new String[][]{{"org.json.JSONArray","数组"},{"java.lang.String","字符串"}}){
            JSONException android=new JSONException("Value RAW_PRIVATE_PAYLOAD of type "+root[0]+" cannot be converted to JSONObject");
            String parsed=ClassroomJsonFailure.parsedFailure(android);
            check(parsed.equals("JSON 语法错误：顶层必须是对象（实际为"+root[1]+"）")&&!parsed.contains("RAW_PRIVATE_PAYLOAD"),"Android root conversion has fixed safe type in parser catch: "+root[0]);
            check(!ClassroomJsonFailure.detail(android).contains("顶层"),"ordinary validator detail does not infer a root conversion");
        }
        for(String member:List.of("0","notes","RAW_PRIVATE_PAYLOAD")){
            String safe=ClassroomJsonFailure.parsedFailure(new JSONException("Value RAW_PRIVATE_PAYLOAD at "+member+" of type java.lang.String cannot be converted to JSONObject"));
            check(!safe.contains("顶层")&&!safe.contains("RAW_PRIVATE_PAYLOAD"),"member conversion is not misclassified as a root error: "+member);
        }
        check(!ClassroomJsonFailure.parsedFailure(new JSONException("Value RAW_PRIVATE_PAYLOAD of type private.Secret cannot be converted to JSONObject")).contains("private.Secret"),"unrecognised root type remains private");
        for(String raw:List.of("[]","\"RAW_PRIVATE_PAYLOAD\"","RAW_PRIVATE_PAYLOAD\n{}")){
            String safe=ClassroomJsonFailure.responseDetail(raw,"draft-0",new JSONObject());
            check(safe.contains("顶层必须是对象")&&!safe.contains("RAW_PRIVATE_PAYLOAD"),"host non-object parser projection remains specific and private");
        }
        JSONObject state=new JSONObject().put("affairs_schema",1).put("figure_policy",1);String original=state.toString();
        for(String stage:new String[]{"correct-0","correct-0-restart-1","","unknown-stage",null}){
            check(ClassroomJsonFailure.responseDetail("# 更正文稿\n[0] 合法完整的 Markdown。",stage,state).isEmpty(),"non-JSON stage is excluded before parsing Markdown: "+stage);
            check(ClassroomJsonFailure.responseDetail("{\"RAW_PRIVATE_PAYLOAD\":",stage,state).isEmpty(),"unknown or correction stage is not reclassified by its response syntax: "+stage);
        }
        for(String stage:List.of("first","first-repair","draft-0","final","final-retry-1","edit-plan","edit-assignments-repair","edit-note-0"))
            check(!ClassroomJsonFailure.responseDetail("{\"RAW_PRIVATE_PAYLOAD\":",stage,state).isEmpty(),"known JSON stage still diagnoses malformed JSON: "+stage);
        JSONObject good=new JSONObject().put("overview","").put("notes",new JSONArray()).put("affairs",new JSONArray()).put("assignments",new JSONArray())
                .put("image_assessment",new JSONObject().put("decision","disabled").put("reason","RAW_PRIVATE_PAYLOAD")).put("image_requests",new JSONArray()).put("image_reviews",new JSONArray());
        for(String stage:List.of("first","draft-0","draft-0-repair","final","final-repair","final-0","final-0-repair","edit-note-0"))
            check(ClassroomJsonFailure.responseDetail(good.toString(),stage,state).isEmpty(),"structurally valid response receives no failure diagnosis: "+stage);
        for(String field:List.of("notes","affairs","assignments","image_assessment","image_requests")){
            JSONObject bad=new JSONObject(good.toString());bad.remove(field);
            check(ClassroomJsonFailure.responseDetail(bad.toString(),"draft-0-repair",state).equals("JSON 字段缺失："+field),"stage required field identified: "+field);
        }
        JSONObject note=new JSONObject().put("title","RAW_PRIVATE_PAYLOAD").put("text","text").put("sources",new JSONArray().put(999)).put("images",new JSONArray().put("unseen.jpg"));
        JSONObject nested=new JSONObject(good.toString()).put("notes",new JSONArray().put(note));
        check(ClassroomJsonFailure.responseDetail(nested.toString(),"draft-0",state).isEmpty(),"read-only diagnosis does not infer invalid sources or unseen image semantics");
        for(String field:List.of("title","text","sources","images")){JSONObject bad=new JSONObject(nested.toString());bad.getJSONArray("notes").getJSONObject(0).remove(field);
            check(ClassroomJsonFailure.responseDetail(bad.toString(),"first",state).equals("JSON 字段缺失：notes[0]."+field),"nested known path identified: "+field);}
        JSONObject task=new JSONObject().put("text","RAW_PRIVATE_PAYLOAD").put("sources",new JSONArray().put(0));JSONObject tasks=new JSONObject(good.toString()).put("assignments",new JSONArray().put(task));
        check(ClassroomJsonFailure.responseDetail(tasks.toString(),"final-repair",state).isEmpty(),"ordinary final repair never gains enhanced evidence requirement");
        for(Object badImages:new Object[]{JSONObject.NULL,new JSONObject().put("RAW_PRIVATE_PAYLOAD",true),"RAW_PRIVATE_PAYLOAD"}){
            JSONObject bad=new JSONObject(tasks.toString());bad.getJSONArray("assignments").getJSONObject(0).put("images",badImages);
            check(ClassroomJsonFailure.responseDetail(bad.toString(),"final",state).equals("JSON 字段类型错误：assignments[0].images 应为数组"),"optional assignment images has a safe exact container path");
        }
        for(Object badImage:new Object[]{new JSONObject().put("RAW_PRIVATE_PAYLOAD",true),17,JSONObject.NULL}){
            JSONObject bad=new JSONObject(tasks.toString());bad.getJSONArray("assignments").getJSONObject(0).put("images",new JSONArray().put(badImage));
            check(ClassroomJsonFailure.responseDetail(bad.toString(),"final",state).equals("JSON 字段类型错误：assignments[0].images[0] 应为字符串"),"optional assignment image element reports only known path and type");
        }
        JSONObject withImages=new JSONObject(tasks.toString());withImages.getJSONArray("assignments").getJSONObject(0).put("images",new JSONArray().put("image-123.jpg"));
        check(ClassroomJsonFailure.responseDetail(withImages.toString(),"final",state).isEmpty(),"optional assignment image strings do not trigger semantic image validation");
        check(ClassroomJsonFailure.responseDetail(tasks.toString(),"final-0-repair",state).equals("JSON 字段缺失：assignments[0].evidence"),"enhanced evidence requirement is stage-specific");
        JSONObject legacy=new JSONObject().put("affairs_schema",0).put("figure_policy",0);JSONObject legacyValue=new JSONObject(good.toString());legacyValue.remove("affairs");legacyValue.remove("image_reviews");
        check(ClassroomJsonFailure.responseDetail(legacyValue.toString(),"final",legacy).isEmpty(),"legacy final does not acquire new affairs or image reviews requirement");
        check(ClassroomJsonFailure.responseDetail("{\"RAW_PRIVATE_PAYLOAD\":", "first",state).contains("JSON"),"shared parser diagnoses original malformed output without repair");
        check(ClassroomJsonFailure.responseDetail("{}","edit-plan",state).equals("JSON 字段缺失：overview"),"editor plan has its own shape");
        check(ClassroomJsonFailure.responseDetail("{\"groups\":[{}]}","edit-assignments",state).equals("JSON 字段类型错误：groups[0] 应为数组"),"assignment grouping shape is independent of note schema");
        check(state.toString().equals(original),"read-only diagnostic leaves run unchanged");
        check(ClassroomJsonObject.parseObject("```json\n{\"text\":\"甲\\\\n乙\"}\n```").getString("text").equals("甲\\n乙"),"shared syntax parser preserves escaped payload without normalization");
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
    private static void noteStrength()throws Exception {
        check(ClassroomNoteStrength.DEFAULT==3,"new tasks default to strength three");
        check(ClassroomNoteStrength.instructions(1).isEmpty(),"level one leaves the established system prompt unchanged");
        check(ClassroomSummary.configKey(AI,3,"",false,1).equals(ClassroomSummary.configKey(AI,3,"",false)),"level one preserves legacy pending config identity");
        Set<String> keys=new HashSet<>();
        for(int level=1;level<=3;level++){
            keys.add(ClassroomSummary.configKey(AI,0,"自定义末尾",false,level));
            String system=ClassroomSummary.systemPrompt("自定义末尾",level);
            check(system.endsWith("自定义末尾"),"custom style remains last and authoritative at level "+level);
            check(system.contains("扣分点")&&system.contains("不确定性"),"grading and uncertainty guard retained at level "+level);
            check(system.contains("自行复算数学与代码")&&system.contains("不根据答案反造原题"),"all strengths check mathematics without reconstructing missing question facts "+level);
            check(system.contains("标题的确定性不得高于正文证据")&&system.contains("可选做法合为一项"),"all strengths preserve evidence and whole conditional actions "+level);
            reset();File dir=materialDir("strength-"+level+"-"+System.nanoTime());JSONObject result;
            try(RequestControl c=new RequestControl(20000)){result=ClassroomSummary.generate(dir,LESSON,AI,0,c,s->{},"自定义末尾",false,level);}
            JSONObject run=ClassroomStore.read(new File(dir,"run.json"));
            check(result.getInt("note_strength")==level&&run.getInt("note_strength")==level,"style recorded in checkpoint and published result "+level);
            check(requests.get(0).getJSONArray("messages").getJSONObject(0).getString("content").equals(system),"real loopback request uses selected system prompt "+level);
            String actual=requests.get(0).getJSONArray("messages").getJSONObject(0).getString("content");
            check(actual.contains("标题标为‘补充例题’")&&actual.contains("不把补充题设当作老师原题"),"actual request permits clearly labelled complete replacement examples without invented original questions "+level);
            if(level==3)check(actual.contains("同类型补充例题")&&actual.contains("不编造考核承诺"),"actual strength-three request preserves the teaching direction without fabricating assessment facts");
        }
        check(keys.size()==3,"each strength has a distinct new-run identity");
        check(ClassroomSummary.systemPrompt("",3).contains("保持整理深度，压紧表达")&&ClassroomSummary.systemPrompt("",3).contains("更正文稿仍逐条保留内容"),"concise handout never compresses correction transcript");
        rejects(()->ClassroomNoteStrength.validate(0),"invalid low strength rejected");rejects(()->ClassroomNoteStrength.validate(4),"invalid high strength rejected");
        for(int original:new int[]{1,3}){
            reset();failCall=2;File dir=materialDir("strength-resume-"+original+"-"+System.nanoTime());
            try(RequestControl c=new RequestControl(20000)){rejects(()->ClassroomSummary.generate(dir,LESSON,AI,3,c,s->{},"",false,original),"synthetic image-stage failure saved checkpoint");}
            JSONObject pending=ClassroomStore.read(new File(dir,"run.json"));String id=pending.getString("run");
            if(original==1){pending.remove("note_strength");ClassroomStore.write(new File(dir,"run.json"),pending);}
            JSONArray prefix=new JSONArray(pending.getJSONObject("conversation").getJSONArray("messages").toString());
            failCall=-1;JSONObject result;
            try(RequestControl c=new RequestControl(20000)){result=ClassroomSummary.generate(dir,LESSON,AI,3,c,s->{},"",false,original==1?3:1);}
            check(result.getString("run").equals(id)&&result.getInt("note_strength")==original,"continuation keeps original strength, including legacy history "+original);
            JSONArray resumed=requests.get(requests.size()-1).getJSONArray("messages");
            for(int n=0;n<prefix.length();n++)check(resumed.getJSONObject(n).toString().equals(prefix.getJSONObject(n).toString()),"sent message remains exact through strength change "+original+":"+n);
            check(calls==3,"continuation does not repeat first extraction or paid image stage");
            try(RequestControl c=new RequestControl(20000)){result=ClassroomSummary.generate(dir,LESSON,AI,0,c,s->{},"",false,2);}
            check(!result.getString("run").equals(id)&&result.getInt("note_strength")==2,"completed classroom regeneration adopts new strength");
        }
    }
    public static void main(String[] args)throws Exception {
        root=new File(args[0]);root.mkdirs();
        if(args.length>1&&"local-lifecycle".equals(args[1])){test("published image and local deletion boundaries",ClassroomSummaryCheck::localLessonLifecycle);System.out.println("LocalLessonLifecycle: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Local lifecycle check failed");return;}
        HttpServer server=server();try {
            if(args.length>1&&"draft-images".equals(args[1])){test("ordinary draft image boundary and append-only recovery",ClassroomSummaryCheck::draftImageReferences);System.out.println("ClassroomDraftImages: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Draft image checks failed");return;}
            if(args.length>1&&"json-repair".equals(args[1])){test("short wire-safe JSON stage requests",ClassroomSummaryCheck::shortJsonRequests);test("safe specific JSON diagnostics",ClassroomSummaryCheck::jsonDiagnostics);test("bounded complete JSON repair and safe errors",ClassroomSummaryCheck::jsonRepair);System.out.println("ClassroomJsonRepair: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("JSON repair check failed");return;}
            if(args.length>1&&"note-strength".equals(args[1])){test("strength selection and immutable continuation",ClassroomSummaryCheck::noteStrength);System.out.println("NoteStrength: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Note strength check failed");return;}
            if(args.length>1&&"image-references".equals(args[1])){test("image reference validation",ClassroomSummaryCheck::validation);test("final validation evidence and continuation",ClassroomSummaryCheck::capAndFinalValidation);test("prefixed final and export",ClassroomSummaryCheck::prefixedFinal);test("final transport and cancellation audit",ClassroomSummaryCheck::finalTransportAudit);System.out.println("ImageReferences: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError("Image reference check failed");return;}
            test("published image and local deletion boundaries",ClassroomSummaryCheck::localLessonLifecycle);
            test("safe specific JSON diagnostics",ClassroomSummaryCheck::jsonDiagnostics);
            test("ordinary draft image boundary and append-only recovery",ClassroomSummaryCheck::draftImageReferences);
            test("bounded complete JSON repair and safe errors",ClassroomSummaryCheck::jsonRepair);
            test("strength selection and immutable continuation",ClassroomSummaryCheck::noteStrength);
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
