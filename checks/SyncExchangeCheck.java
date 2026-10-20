package com.donglan.chrona;

import com.donglan.chrona.sync.*;
import com.sun.net.httpserver.HttpServer;
import org.json.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/** Real exchange/codec/HTTP integration; all credentials and records are local fixtures. */
public final class SyncExchangeCheck {
    private static final String A="00000000-0000-0000-0000-000000000001";
    private static final String B="00000000-0000-0000-0000-000000000002";
    private static final String SOURCE="00000000-0000-0000-0000-000000000003";
    private static int checks;
    private static void check(boolean value,String message) { if(!value)throw new AssertionError(message); checks++; }
    private static JSONObject task(String text) {
        return new JSONObject().put("kind","task").put("rawText",text).put("source","test")
                .put("createdAt",100).put("status","ready").put("linkText","")
                .put("candidates",new JSONArray()).put("attachments",new JSONArray());
    }
    public static void main(String[] args) throws Exception {
        for(boolean strong:new boolean[]{false,true}) exchange(strong);
        System.out.println("Sync exchange checks passed: "+checks+" (real HTTP, missing/strong ETag, repeated and two-device exchange, merge markers)");
    }
    private static void exchange(boolean strong) throws Exception {
        Map<String,byte[]> cloud=new TreeMap<>();
        SyncState remote=new SyncState(A); remote.put("task_"+A,task("remote"));
        cloud.put(A+".json",remote.toJson().toString().getBytes(StandardCharsets.UTF_8));
        byte[] untouched=cloud.get(A+".json").clone();
        AtomicInteger writes=new AtomicInteger(); List<String> conditions=new ArrayList<>();
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.createContext("/",request->{
            String method=request.getRequestMethod(),path=request.getRequestURI().getPath();
            String name=path.substring(path.lastIndexOf('/')+1); byte[] body=new byte[0]; int code=200;
            if(method.equals("MKCOL")) code=405;
            else if(method.equals("PROPFIND")) {
                code=207; StringBuilder xml=new StringBuilder("<d:multistatus xmlns:d='DAV:'>");
                for(String file:cloud.keySet()) if(file.endsWith(".json")) xml.append("<d:response><d:href>/ChronaSync/v1/heads/").append(file)
                    .append("</d:href><d:propstat><d:prop>")
                    .append(strong?"<d:getetag>&quot;v1&quot;</d:getetag>":"")
                    .append("</d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>");
                body=xml.append("</d:multistatus>").toString().getBytes(StandardCharsets.UTF_8);
            } else if(method.equals("GET")) {
                body=cloud.get(name); if(body==null){body=new byte[0];code=404;}
                else if(strong)request.getResponseHeaders().add("ETag","\"v1\"");
            } else if(method.equals("PUT")) {
                String match=request.getRequestHeaders().getFirst("If-Match");
                String create=request.getRequestHeaders().getFirst("If-None-Match");
                if(name.endsWith(".json"))conditions.add(match+"|"+create);
                if("*".equals(create)&&cloud.containsKey(name))code=412;
                else {cloud.put(name,request.getRequestBody().readAllBytes());writes.incrementAndGet();code=201;}
            } else code=405;
            request.sendResponseHeaders(code,body.length==0?-1:body.length);
            if(body.length>0)request.getResponseBody().write(body);request.close();
        });
        server.start(); Path directory=Files.createTempDirectory("chrona-exchange-");
        String url="http://127.0.0.1:"+server.getAddress().getPort();
        try {
            SyncState local=new SyncState(B); local.put("task_"+B,task("local"));
            local.put("merge_"+SOURCE,CandidateMergeCheck.record(SOURCE,A));
            byte[] picture=new byte[]{(byte)255,(byte)216,1,2,3,(byte)255,(byte)217};
            String sha=SyncState.sha256(picture);Files.write(directory.resolve(sha),picture);
            JSONObject lesson=new JSONObject().put("course_id","100").put("id","1000").put("course","测试课堂").put("title","极限").put("status","6").put("starts_at",100);
            JSONObject summary=new JSONObject().put("lesson",lesson).put("complete",true).put("updated",100)
                .put("overview","![图](images/image-0.jpg)").put("parts",new JSONArray()).put("images",new JSONArray().put(new JSONObject().put("file","image-0.jpg")));
            JSONObject classroom=new JSONObject().put("kind","classroom").put("document",new JSONObject().put("summary",summary).put("segments",new JSONArray()))
                .put("attachments",new JSONArray().put(new JSONObject().put("name","image-0.jpg").put("sha",sha).put("size",picture.length).put("image",true).put("mime","image/jpeg")));
            String classroomId=com.donglan.chrona.classroom.ClassroomDocument.syncId(lesson);local.put(classroomId,classroom);
            SyncState merged;
            try(WebDavClient client=new WebDavClient(url,"fixture","fixture",true)) {
                merged=SyncExchange.exchange(local,directory.toFile(),client);
            }
            check(merged.records().size()==4,"exchange lost task, merge or classroom");
            check(Arrays.equals(picture,cloud.get(sha)),"classroom blob not uploaded before head");
            check(Arrays.equals(untouched,cloud.get(A+".json")),"another device head overwritten");
            check("null|*".equals(conditions.get(0)),"new head must use create condition");
            try(WebDavClient client=new WebDavClient(url,"fixture","fixture",true)) {
                merged=SyncExchange.exchange(merged,directory.toFile(),client);
            }
            check(conditions.get(1).equals(strong?"\"v1\"|null":"null|null"),"existing own head condition incorrect");
            check(merged.records().containsKey("merge_"+SOURCE),"repeated exchange lost merge");
            try(WebDavClient client=new WebDavClient(url,"fixture","fixture",true)) {
                Files.delete(directory.resolve(sha));
                SyncState second=SyncExchange.exchange(remote,directory.toFile(),client);
                check(second.records().size()==4,"second device did not receive all records");
                check(Arrays.equals(picture,Files.readAllBytes(directory.resolve(sha))),"second device did not download classroom image");
                check(second.records().get(classroomId).get(0).payload.getJSONObject("document").getJSONObject("summary").getString("overview").contains("image-0.jpg"),"classroom content lost in exchange");
            }
            int prior=writes.get(); SyncState invalid=merged.copy(); invalid.put("unknown_record",task("invalid"));
            try(WebDavClient client=new WebDavClient(url,"fixture","fixture",true)) {
                try {SyncExchange.exchange(invalid,directory.toFile(),client);throw new AssertionError("unknown identity accepted");}
                catch(java.io.IOException expected){check(writes.get()==prior,"invalid state was published");}
            }
            check(SyncState.fromJson(new JSONObject(new String(cloud.get(B+".json"),StandardCharsets.UTF_8)))
                    .records().containsKey("merge_"+SOURCE),"published head missing permanent merge");
        } finally {server.stop(0);try(var files=Files.list(directory)){for(Path file:files.toList())Files.delete(file);}Files.delete(directory);}
    }
}
