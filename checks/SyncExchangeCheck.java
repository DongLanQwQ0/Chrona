package com.donglan.chrona;

import com.donglan.chrona.sync.*;
import com.donglan.chrona.data.*;
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
    private interface Invalid { void run() throws Exception; }
    private static void rejects(Invalid action, String label) throws Exception {
        try { action.run(); } catch (java.io.IOException expected) { checks++; return; }
        throw new AssertionError(label);
    }
    private static void candidate(JSONObject candidate) throws Exception {
        TaskCodec.validate(task("codec fixture").put("candidates", new JSONArray().put(candidate)));
    }
    private static void codecChecks() throws Exception {
        long start=java.time.Instant.parse("2026-01-01T09:00:00Z").toEpochMilli(),original=start+86_400_000L;
        String key=Long.toString(original);
        EventCandidate series=new EventCandidate(1,1,"Template",start,start+3_600_000L,"UTC","A","Notes",10,false)
                .withRepeat(new RepeatRule("daily",1,List.of(),null,3));
        JSONObject fields=new JSONObject().put("title","This week").put("location",JSONObject.NULL)
                .put("start",original+60_000L).put("end",original+5_400_000L).put("reminder_minutes_before",25);
        EventCandidate edited=RepeatOccurrences.edit(series,original,fields);
        JSONObject exceptions=RepeatOccurrences.exceptions(edited);
        JSONObject history=new JSONObject().put("action","edit").put("at",start)
                .put("before",new JSONObject().put("reminder_minutes_before",10).put("title","Template"))
                .put("after",new JSONObject().put("reminder_minutes_before",25).put("title","This week"))
                .put("source",new JSONObject().put("raw","private fixture").put("nested",new JSONArray()
                        .put(new JSONObject().put("reminder_minutes_before",20).put("source_id",A))));
        exceptions.getJSONObject(key).put("history",new JSONArray().put(history));
        edited=edited.withRepeatState(exceptions.toString(),null);
        JSONObject portable=TaskCodec.candidate(edited);
        check(!portable.toString().contains("reminder_minutes_before"),"all nested recurrence reminder settings must remain local");
        check(edited.repeatExceptions.contains("reminder_minutes_before"),"portable export must not mutate stored exceptions");
        candidate(portable);
        EventCandidate restored=TaskCodec.event(portable),occurrence=RepeatOccurrences.at(restored,original);
        check(occurrence.title.equals("This week")&&occurrence.location==null
                &&occurrence.startAtMillis==original+60_000L&&occurrence.endAtMillis==original+5_400_000L,
                "single-occurrence fields survive portable validation and event decoding");
        check(occurrence.reminderMinutesBefore==null&&restored.reminderMinutesBefore==null,"sync does not restore device-local alerts");
        check(RepeatOccurrences.exceptions(restored).getJSONObject(key).getJSONArray("history")
                .getJSONObject(0).getJSONObject("source").getString("raw").equals("private fixture"),"opaque audit source is retained");
        JSONObject skipped=new JSONObject(portable.toString());
        skipped.getJSONObject("repeatExceptions").getJSONObject(key).put("skipped",true).put("completed",start);
        skipped.put("repeatStoppedAt",start);
        candidate(skipped);
        check(TaskCodec.event(skipped).repeatStoppedAtMillis==start,"later skip/stop/completion do not invalidate prior field edits");
        for(JSONObject badFields:List.of(new JSONObject().put("bogus",true),new JSONObject().put("title",12),
                new JSONObject().put("start",original+7_200_000L),new JSONObject().put("all_day",true),
                new JSONObject().put("time_zone_id","No/Zone"),new JSONObject().put("reminder_minutes_before",10))) {
            JSONObject bad=new JSONObject(portable.toString());
            bad.getJSONObject("repeatExceptions").getJSONObject(key).put("fields",badFields);
            rejects(()->candidate(bad),"invalid occurrence fields accepted");
        }
        for(Object badHistory:List.of("bad",new JSONArray().put(10),JSONObject.NULL)) {
            JSONObject bad=new JSONObject(portable.toString());
            bad.getJSONObject("repeatExceptions").getJSONObject(key).put("history",badHistory);
            rejects(()->candidate(bad),"invalid history structure accepted");
        }
        JSONObject nonmember=new JSONObject(portable.toString());
        JSONObject entry=nonmember.getJSONObject("repeatExceptions").getJSONObject(key);
        nonmember.put("repeatExceptions",new JSONObject().put(Long.toString(original+1),entry));
        rejects(()->candidate(nonmember),"nonmember original identity accepted");
        JSONObject deep=new JSONObject(),cursor=deep;
        for(int i=0;i<33;i++){JSONObject next=new JSONObject();cursor.put("nested",next);cursor=next;}
        JSONObject tooDeep=new JSONObject(portable.toString());
        tooDeep.getJSONObject("repeatExceptions").getJSONObject(key).put("history",new JSONArray().put(deep));
        rejects(()->candidate(tooDeep),"unbounded nested audit accepted");
        JSONObject huge=new JSONObject(portable.toString());
        huge.getJSONObject("repeatExceptions").getJSONObject(key).put("history",new JSONArray()
                .put(new JSONObject().put("source","x".repeat(TaskCodec.MAX_TASK_BYTES+1))));
        rejects(()->TaskCodec.event(huge),"oversized direct event audit accepted");

        JSONObject proposal=new JSONObject().put("state","pending").put("runId","parse-run").put("position",0)
                .put("series_id",A).put("original",original).put("fields",new JSONObject().put("location","B"))
                .put("before",new JSONObject().put("title","Template"))
                .put("preview",new JSONObject().put("title","This week"))
                .put("target_snapshot",new JSONObject().put("start",original)).put("applications",new JSONArray());
        for(String state:List.of("pending","invalid","new","applied")) {
            JSONObject operational=new JSONObject(proposal.toString()).put("state",state);
            candidate(new JSONObject(portable.toString()).put("occurrenceUpdate",operational));
            check(operational.getString("state").equals(state),"operational state accepted intact");
        }
        candidate(new JSONObject(portable.toString()).put("occurrenceUpdate",JSONObject.NULL));
        candidate(new JSONObject(portable.toString()).put("occurrenceUpdate",new JSONObject()
                .put("state","invalid").put("runId","parse-run").put("position",0)));
        JSONObject noTarget=new JSONObject(proposal.toString());noTarget.remove("series_id");
        JSONObject noOriginal=new JSONObject(proposal.toString());noOriginal.remove("original");
        JSONObject noRun=new JSONObject(proposal.toString());noRun.remove("runId");
        for(JSONObject bad:List.of(new JSONObject(proposal.toString()).put("state","unknown"),noTarget,noOriginal,noRun,
                new JSONObject(proposal.toString()).put("position",-1),new JSONObject(proposal.toString()).put("position","0"),
                new JSONObject(proposal.toString()).put("runId",12),new JSONObject(proposal.toString()).put("series_id","bad"),
                new JSONObject(proposal.toString()).put("series_id","1-1-1-1-1"),
                new JSONObject(proposal.toString()).put("original",JSONObject.NULL),
                new JSONObject(proposal.toString()).put("original",253_402_300_800_000L),
                new JSONObject(proposal.toString()).put("fields",new JSONObject().put("reminder_minutes_before",10)),
                new JSONObject(proposal.toString()).put("applications",new JSONArray().put("bad"))))
            rejects(()->candidate(new JSONObject(portable.toString()).put("occurrenceUpdate",bad)),"invalid operational update accepted");
        JSONObject local=new JSONObject(proposal.toString()).put("fields",new JSONObject().put("reminder_minutes_before",10))
                .put("applications",new JSONArray().put(new JSONObject().put("after",new JSONObject().put("reminder_minutes_before",15))))
                .put("target_snapshot",new JSONObject().put("reminder_minutes_before",5));
        JSONObject exported=TaskCodec.portableOccurrenceUpdate(local);
        check(!exported.toString().contains("reminder_minutes_before")&&local.toString().contains("reminder_minutes_before"),
                "operational portability strips nested alerts without mutating the audit");
        candidate(new JSONObject(portable.toString()).put("occurrenceUpdate",exported));
        check(TaskCodec.portableOccurrenceUpdate(null)==null,"optional operational metadata stays absent");
        System.out.println("TaskCodec recurrence/operational checks passed: "+checks);
    }
    public static void main(String[] args) throws Exception {
        codecChecks();
        if(args.length>0&&args[0].equals("codec-only"))return;
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
