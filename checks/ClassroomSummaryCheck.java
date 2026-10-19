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
            JSONObject chunk=prompt.contains("资料：\n")?new JSONObject(prompt.substring(prompt.indexOf("资料：\n")+4)):null;
            if(chunk!=null){int id=chunk.getJSONArray("segments").getJSONObject(0).getInt("id");chunkCalls.merge(id,1,Integer::sum);}
            if(mode.equals("slow"))Thread.sleep(500);
            int status=200;String payload,type="text/event-stream";
            if(calls==failCall){status=503;type="application/json";payload="{\"error\":{\"message\":\"synthetic failure\"}}";}
            else if(mode.equals("downgrade")&&body.has("stream_options")){status=400;type="application/json";payload="{\"error\":{\"message\":\"unsupported parameter stream_options\"}}";}
            else {
                JSONObject generated=chunk==null?new JSONObject().put("overview","合并重点"):part(chunk);
                if(chunk!=null&&mode.equals("invalid-source"))generated.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(999));
                if(chunk!=null&&mode.equals("invalid-image"))generated.getJSONArray("notes").getJSONObject(0).put("images",new JSONArray().put("image-999.jpg"));
                String result=generated.toString();
                if(mode.equals("schedule"))result="{\"events\":[]}";
                String finish=mode.equals("length")?"length":mode.equals("filter")?"content_filter":"stop";
                JSONObject frame=new JSONObject().put("choices",new JSONArray().put(new JSONObject().put("delta",new JSONObject().put("reasoning_content","合成推理").put("content",result)).put("finish_reason",finish)));
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
    private static void chunkBoundaries()throws Exception {
        JSONArray segments=new JSONArray().put(segment(0,"字幕")),pics=new JSONArray();
        for(int i=0;i<13;i++)pics.put(picture(i,i+1));
        JSONArray chunks=ClassroomSummary.chunks(segments,pics);int total=0,max=0;
        for(int i=0;i<chunks.length();i++){JSONObject chunk=chunks.getJSONObject(i);int count=chunk.getJSONArray("images").length();total+=count;max=Math.max(max,count);check(chunk.getJSONArray("segments").length()>0,"image-only tail retains real subtitle source");}
        check(max<=4,"tail images obey four-image chunk budget; observed max="+max);check(total==13,"all selected images retained exactly once");
        JSONArray longText=ClassroomSummary.chunks(new JSONArray().put(segment(0,"甲".repeat(5000))).put(segment(10,"乙".repeat(5000))),new JSONArray());
        check(longText.length()==2,"subtitle character budget splits ordinary long transcript");
    }
    private static void validation()throws Exception {
        JSONObject chunk=ClassroomSummary.chunks(new JSONArray().put(segment(0,"来源")),new JSONArray().put(picture(0,0))).getJSONObject(0);
        JSONObject valid=part(chunk);ClassroomSummary.validate(valid,chunk);check(true,"valid actual source and uploaded image accepted");
        JSONObject bad=new JSONObject(valid.toString());bad.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put(99));rejects(()->ClassroomSummary.validate(bad,chunk),"out-of-chunk subtitle rejected");
        JSONObject missing=new JSONObject(valid.toString());missing.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray());rejects(()->ClassroomSummary.validate(missing,chunk),"missing source rejected");
        JSONObject string=new JSONObject(valid.toString());string.getJSONArray("notes").getJSONObject(0).put("sources",new JSONArray().put("0"));rejects(()->ClassroomSummary.validate(string,chunk),"string source ID rejected");
        JSONObject unuploaded=new JSONObject(valid.toString());unuploaded.getJSONArray("notes").getJSONObject(0).put("images",new JSONArray().put("image-9.jpg"));rejects(()->ClassroomSummary.validate(unuploaded,chunk),"image not uploaded in this chunk rejected");
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
        try(RequestControl c=new RequestControl(20000)){rejects(()->ClassroomSummary.generate(dir,LESSON,AI,3,c,s->{}),"mid-run HTTP failure propagated");}
        JSONObject checkpoint=ClassroomStore.read(new File(dir,"run.json")),partial=ClassroomStore.read(new File(dir,"summary.json"));
        check(checkpoint.getJSONArray("parts").length()==1,"successful segment checkpoint persisted before later failure");
        check(!partial.getBoolean("complete")&&partial.getJSONArray("parts").length()==1&&partial.getInt("total_parts")==3,"partial result explicitly incomplete");
        check(partial.getInt("planned_images")==3,"partial result distinguishes planned from analysed image count");
        check(partial.getJSONArray("images").length()==1,"partial image inventory includes only analysed segment; observed="+partial.getJSONArray("images").length());
        ByteArrayOutputStream partialZip=new ByteArrayOutputStream();ClassroomExport.write(dir,partial,partialZip);Map<String,byte[]> partialEntries=unzip(partialZip.toByteArray());String partialMd=new String(partialEntries.get("课堂总结.md"),StandardCharsets.UTF_8);
        check(partialMd.contains("未完成：1 / 3"),"partial export reports incomplete status");
        check(partialEntries.keySet().stream().filter(s->s.startsWith("images/")).count()==1,"partial export excludes not-yet-analysed images");
        failCall=-1;JSONObject result;try(RequestControl c=new RequestControl(20000)){result=ClassroomSummary.generate(dir,LESSON,AI,3,c,s->{});}
        check(result.getBoolean("complete")&&result.getJSONArray("parts").length()==3,"retry completes remaining segments and final overview");
        check(chunkCalls.get(0)==1&&chunkCalls.get(1)==2&&chunkCalls.get(2)==1,"resume does not resend completed segment: "+chunkCalls);
        check(result.getString("overview").equals("合并重点"),"final overview persisted");
        check(result.getString("run").equals(partial.getString("run")),"resumed run keeps identity for UI replacement");
        int before=calls;try(RequestControl c=new RequestControl(10000)){ClassroomSummary.generate(dir,LESSON,AI,3,c,s->{});}check(calls==before,"completed run retry makes no HTTP requests");
        ByteArrayOutputStream zip=new ByteArrayOutputStream();ClassroomExport.write(dir,result,zip);Map<String,byte[]> entries=unzip(zip.toByteArray());String md=new String(entries.get("课堂总结.md"),StandardCharsets.UTF_8),transcript=new String(entries.get("sources.md"),StandardCharsets.UTF_8);
        check(entries.size()==5,"ZIP contains Markdown, subtitle source, three local images");
        for(int i=0;i<3;i++){check(md.contains("images/image-"+i+".jpg")&&entries.containsKey("images/image-"+i+".jpg"),"offline image reference resolves "+i);check(md.contains("sources.md#s"+i)&&transcript.contains("id=\"s"+i+"\""),"offline subtitle anchor resolves "+i);}
        check(md.contains("## 课堂安排")&&md.contains("下周交作业"),"assignments exported as suggestions without calendar writes");
        check(!md.contains(AI.apiKey)&&!checkpoint.toString().contains(AI.apiKey)&&!result.toString().contains(AI.apiKey),"credential absent from run/summary/export");
        rejects(()->ClassroomExport.safeImage("../secret"),"ZIP path traversal rejected");
        try(RequestControl c=new RequestControl(20000)){JSONObject changed=ClassroomSummary.generate(dir,LESSON,AI,0,c,s->{});check(!changed.getString("run").equals(result.getString("run")),"changed image budget creates distinct run identity");check(changed.getJSONArray("images").length()==0&&changed.getInt("planned_images")==0,"changed config cannot reuse old image inventory");}
        try(RequestControl c=new RequestControl(10000)){rejects(()->ClassroomSummary.generate(dir,LESSON,AI,-1,c,s->{}),"negative image budget rejected");rejects(()->ClassroomSummary.generate(dir,LESSON,AI,101,c,s->{}),"over-100 image budget rejected");}
    }
    private static void isolation()throws Exception {
        android.content.Context context=new android.content.Context(new File(root,"accounts-"+System.nanoTime()));ClassroomStore a=new ClassroomStore(context,"anonymous-a"),b=new ClassroomStore(context,"anonymous-b");
        a.catalog(List.of(LESSON));check(a.catalog().size()==1&&b.catalog().isEmpty(),"catalog isolated by anonymous account");
        File adir=a.lesson(LESSON),bdir=b.lesson(LESSON);ClassroomStore.write(new File(adir,"summary.json"),new JSONObject().put("updated",1));check(a.history().size()==1&&b.history().isEmpty()&&!adir.equals(bdir),"history and lesson files isolated by anonymous account");
        rejects(()->new ClassroomStore(context,"../outside"),"unsafe account path rejected");rejects(()->a.lesson(new ClassroomLesson("../1","2","","","","6",1)),"unsafe lesson path rejected");
        check(LESSON.selectable(2)&&!LESSON.selectable(0),"only occurred completed lesson selectable");check(!new ClassroomLesson("1","2","","","","4",1).selectable(2),"unfinished recording cannot be selected");
    }
    private static void textOnlyAndInvalidRetry()throws Exception {
        reset();File dir=materialDir("text-only-"+System.nanoTime());
        check(ClassroomSummary.DEFAULT_IMAGES==20,"default image budget is twenty");
        try(RequestControl c=new RequestControl(20000)){JSONObject summary=ClassroomSummary.generate(dir,LESSON,AI,0,c,s->{});check(summary.getBoolean("complete")&&summary.getJSONArray("images").length()==0,"zero-budget pipeline completes without images");}
        for(JSONObject request:requests)check(request.getJSONArray("messages").getJSONObject(1).getJSONArray("content").length()==1,"text-only HTTP request has no image_url item");
        reset();File invalid=materialDir("invalid-reference-"+System.nanoTime());mode="invalid-source";
        try(RequestControl c=new RequestControl(20000)){rejects(()->ClassroomSummary.generate(invalid,LESSON,AI,0,c,s->{}),"invalid AI subtitle citation fails before checkpoint");}
        JSONObject run=ClassroomStore.read(new File(invalid,"run.json"));check(run==null||run.getJSONArray("parts").length()==0,"invalid subtitle response never persisted as successful segment");
        mode="invalid-image";
        try(RequestControl c=new RequestControl(20000)){rejects(()->ClassroomSummary.generate(invalid,LESSON,AI,0,c,s->{}),"invalid AI image citation fails before checkpoint");}
        mode="normal";try(RequestControl c=new RequestControl(20000)){check(ClassroomSummary.generate(invalid,LESSON,AI,0,c,s->{}).getBoolean("complete"),"invalid-response retry resumes successfully");}
        check(chunkCalls.get(0)==3&&chunkCalls.get(1)==1&&chunkCalls.get(2)==1,"invalid segment retried, successful later segments sent once");
    }
    private static void transport()throws Exception {
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
    public static void main(String[] args)throws Exception {
        root=new File(args[0]);root.mkdirs();HttpServer server=server();try {
            test("chunk boundaries",ClassroomSummaryCheck::chunkBoundaries);test("reference validation",ClassroomSummaryCheck::validation);test("low-resolution dedup and budget",ClassroomSummaryCheck::images);test("transport options/cancel/deadline/SSE",ClassroomSummaryCheck::transport);test("checkpoint, retry and offline ZIP",ClassroomSummaryCheck::pipeline);test("text-only and invalid-response retry",ClassroomSummaryCheck::textOnlyAndInvalidRetry);test("anonymous account isolation",ClassroomSummaryCheck::isolation);
        }finally{server.stop(0);}System.out.println("ClassroomSummaryCheck: "+checks+" assertions, "+failures+" failures");if(failures>0)throw new AssertionError(failures+" classroom regression failures");
    }
}
