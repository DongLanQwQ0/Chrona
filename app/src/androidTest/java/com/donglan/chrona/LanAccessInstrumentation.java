package com.donglan.chrona;

import android.app.Instrumentation;
import android.content.*;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import com.donglan.chrona.data.TaskStore;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Device-only integration check: real server + real Android SQLite, isolated from user data. */
public final class LanAccessInstrumentation extends Instrumentation {
    private Bundle arguments;
    @Override public void onCreate(Bundle arguments) {
        this.arguments = arguments == null ? new Bundle() : new Bundle(arguments);
        super.onCreate(arguments); start();
    }
    @Override public void onStart() {
        if ("ui".equals(arguments.getString("check"))) {
            Bundle report = new Bundle();
            try { report.putString("ui_report", DeviceUiChecks.run(this, arguments)); finish(-1, report); }
            catch (Throwable failure) { report.putString("error", android.util.Log.getStackTraceString(failure)); finish(0, report); }
            return;
        }
        Bundle result = new Bundle();
        try { check(); result.putString("stream", "LAN integration: PASS (real Android SQLite and HTTP)\n"); finish(-1, result); }
        catch (Throwable error) { result.putString("stream", "LAN integration: FAIL " + error.getClass().getSimpleName() + ": " + error.getMessage()+"\n"); finish(0, result); }
    }
    private String cookie, csrf;
    private int port;
    private boolean providerCompletion;
    private void check() throws Exception {
        Context target = getTargetContext();
        File directory = new File(target.getCacheDir(), "lan-integration-" + UUID.randomUUID());
        if (!directory.mkdirs()) throw new IOException("Cannot create isolated test directory");
        Context isolated = new ContextWrapper(target) {
            @Override public Context getApplicationContext() { return this; }
            @Override public File getFilesDir() { return directory; }
            @Override public File getDatabasePath(String name) { return new File(directory,name); }
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name),factory); }
            @Override public SQLiteDatabase openOrCreateDatabase(String name,int mode,SQLiteDatabase.CursorFactory factory,DatabaseErrorHandler handler) { return SQLiteDatabase.openOrCreateDatabase(getDatabasePath(name).getAbsolutePath(),factory,handler); }
            @Override public android.content.SharedPreferences getSharedPreferences(String name,int mode) { return super.getSharedPreferences("lan-test-"+directory.getName()+"-"+name,mode); }
        };
        try(ServerSocket reserve = new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))) { port=reserve.getLocalPort(); }
        LanWebServer server = new LanWebServer(isolated,"127.0.0.1",port);
        try {
            server.start(3000,false);
            expect(request("GET","/api/inbox",null,null),401);
            expect(request("GET","/api/attachment/1/image-0",null,null),401);
            expect(request("POST","/api/pair",new JSONObject().put("code",server.security.pairing()),"http://evil.invalid"),403);
            expect(request("POST","/api/pair",new JSONObject().put("code",server.security.pairing()),origin()),200);
            Reply session=request("GET","/api/session",null,null);expect(session,200);csrf=session.body.getString("csrf");
            Reply created=request("POST","/api/task/create",new JSONObject().put("text","LAN test original").put("process",false),origin());expect(created,200);long id=created.body.getLong("id");
            try(TaskStore store=new TaskStore(isolated)){require(store.getTask(id).rawText.equals("LAN test original"),"HTTP must persist into TaskStore");}
            Reply detail=request("GET","/api/task?id="+id,null,null);long revision=detail.body.getLong("revision");
            JSONObject edit=new JSONObject().put("id",Long.toString(id)).put("revision",revision).put("text","edited");
            String saved=csrf;csrf="wrong";expect(request("POST","/api/task/edit",edit,origin()),403);csrf=saved;
            expect(request("POST","/api/task/edit",edit,"http://evil.invalid"),403);
            expect(request("POST","/api/task/edit",edit,origin()),200);
            expect(request("POST","/api/task/edit",edit,origin()),409);
            detail=request("GET","/api/task?id="+id,null,null);revision=detail.body.getLong("revision");require(detail.body.getJSONObject("task").getString("text").equals("edited"),"read after write");
            JSONObject candidate=new JSONObject().put("id",Long.toString(id)).put("revision",revision).put("title","Meeting").put("start",1791072000000L).put("end",1791075600000L).put("zone","Asia/Shanghai").put("allDay",false).put("category","event").put("location","").put("description","").put("reminder",JSONObject.NULL).put("needsConfirmation",false);
            expect(request("POST","/api/candidate/create",candidate,origin()),200);
            detail=request("GET","/api/task?id="+id,null,null);revision=detail.body.getLong("revision");long candidateId=detail.body.getJSONArray("candidates").getJSONObject(0).getLong("id");candidate.put("candidateId",Long.toString(candidateId)).put("revision",revision).put("end",1);
            expect(request("POST","/api/candidate/save",candidate,origin()),400);
            try(TaskStore store=new TaskStore(isolated)){require(store.getCandidates(id).get(0).endAtMillis==1791075600000L,"invalid edit leaves row unchanged");}
            long memoId, todoId;
            try(TaskStore store=new TaskStore(isolated)){
                memoId=store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,id,"原记录",null,null,"Asia/Shanghai","","原正文",null,false,null,"event",false,false,0,null,null,true));
                todoId=store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,id,"未安排待办",null,null,"Asia/Shanghai","","",null,false,null,"task",false,false,0,null,null,false));
            }
            revision=request("GET","/api/task?id="+id,null,null).body.getLong("revision");
            JSONObject memo=new JSONObject().put("id",Long.toString(id)).put("candidateId",Long.toString(memoId))
                    .put("revision",revision).put("title","独立可见标题").put("description","修改后的正文").put("record",true);
            expect(request("POST","/api/candidate/save",memo,origin()),200);
            try(TaskStore store=new TaskStore(isolated)){
                var row=store.getCandidatesByIds(java.util.List.of(memoId)).get(0);
                require(row.isRecord&&row.title.equals("独立可见标题")&&row.description.equals("修改后的正文"),"record edit retains record identity");
                require(store.getTask(id).rawText.equals("edited"),"record edit preserves independent original");
                require(store.queryInboxIds(new com.donglan.chrona.data.InboxQuery(0,"record","独立可见标题"),false).equals(java.util.List.of(id)),"visible record title is searchable");
                revision=store.dataRevision();
            }
            JSONObject completion=new JSONObject().put("id",Long.toString(id)).put("candidateId",Long.toString(todoId))
                    .put("revision",revision).put("completed",true);
            expect(request("POST","/api/candidate/complete",completion,origin()),200);
            expect(request("POST","/api/candidate/complete",completion,origin()),409);
            try(TaskStore store=new TaskStore(isolated)){
                require(store.getCandidatesByIds(java.util.List.of(todoId)).get(0).completed(),"HTTP completion persisted");
                revision=store.dataRevision();
            }
            completion.put("completed",false).put("revision",revision);
            expect(request("POST","/api/candidate/complete",completion,origin()),200);
            try(TaskStore store=new TaskStore(isolated)){require(!store.getCandidatesByIds(java.util.List.of(todoId)).get(0).completed(),"HTTP restore persisted");revision=store.dataRevision();}
            JSONObject extra=new JSONObject(candidate.toString()).put("revision",revision).put("title","Another event")
                    .put("start",System.currentTimeMillis()+3_600_000L).put("end",System.currentTimeMillis()+7_200_000L);
            expect(request("POST","/api/candidate/create",extra,origin()),200);
            detail=request("GET","/api/task?id="+id,null,null);revision=detail.body.getLong("revision");
            require(detail.body.getJSONArray("candidates").length()==4,"append retains all existing results");
            long extraId=detail.body.getJSONArray("candidates").getJSONObject(3).getLong("id");
            JSONObject acknowledgement=new JSONObject().put("id",Long.toString(id)).put("candidateId",Long.toString(extraId))
                    .put("revision",revision).put("acknowledged",true);
            expect(request("POST","/api/candidate/acknowledge",acknowledgement,origin()),200);
            detail=request("GET","/api/task?id="+id,null,null);
            require(detail.body.getJSONArray("candidates").getJSONObject(3).getBoolean("acknowledged"),"event acknowledgement visible through HTTP");
            expect(request("POST","/api/candidate/acknowledge",acknowledgement.put("acknowledged",false),origin()),200);
            expect(request("GET","/api/schedule?tab=1&category=task&publication=3&todo=3",null,null),200);
            Reply unscheduled=request("GET","/api/schedule?tab=1&category=task&publication=3&todo=3",null,null);
            require(unscheduled.body.getJSONArray("items").length()==1&&unscheduled.body.getJSONArray("items").getJSONObject(0).getLong("id")==todoId,"shared pending task filters");
            expect(request("GET","/api/schedule?tab=3&from=2026-10-02&until=2026-10-01",null,null),400);
            expect(request("GET","/api/inbox?category=record",null,null),200);
            expect(request("GET","/api/agenda?from=2026-10-01&until=2026-11-01",null,null),200);
            expect(request("GET","/api/agenda?from=2026-10-01&until=2028-11-01",null,null),400);
            expect(request("POST","/api/calendar/publish-selected",new JSONObject().put("revision",revision-1)
                    .put("ids",new org.json.JSONArray().put(Long.toString(todoId))).put("inbox",false),origin()),409);
            com.donglan.chrona.calendar.CalendarStore calendar = new com.donglan.chrona.calendar.CalendarStore(isolated);
            if (calendar.hasReadPermission() && calendar.hasWritePermission()) {
                long start=System.currentTimeMillis()+7_200_000L;
                long event=calendar.insertEvent(new com.donglan.chrona.calendar.CalendarStore.EventInput(
                        "Chrona 回归检查 · 临时事项",start,start+60000,"Asia/Shanghai",false,"自动验收后移除","",java.util.List.of(15,30)));
                try {
                    long linked;
                    try(TaskStore store=new TaskStore(isolated)){
                        linked=store.appendCandidate(new com.donglan.chrona.data.EventCandidate(0,id,"临时待办",start,null,"Asia/Shanghai","","",15,false,event,"task",false,false,0,start,null,false));
                    }
                    require(TaskCompletion.set(isolated,linked,id,true)==null,"provider completion sync");
                    require(calendar.getEvent(event)!=null&&calendar.getEvent(event).reminderMinutes.isEmpty(),"completed event history retained and alerts cleared");
                    require(TaskCompletion.set(isolated,linked,id,false)==null,"provider restore sync");
                    require(calendar.getEvent(event).reminderMinutes.equals(java.util.List.of(15,30)),"future original alerts restored");
                    providerCompletion=true;
                } finally { require(calendar.deleteEvent(event),"temporary provider fixture removed"); }
                try(TaskStore store=new TaskStore(isolated)){revision=store.dataRevision();}
            }
            expect(request("GET","/../chrona.db",null,null),404);
            expect(request("POST","/api/ics",new JSONObject(),origin()),405);
            expect(request("POST","/api/task/delete",new JSONObject().put("id",Long.toString(id)).put("revision",revision),origin()),200);
            try(TaskStore store=new TaskStore(isolated)){require(store.getTask(id)==null,"delete must remove real row");require(store.getCandidates(id).isEmpty(),"candidate cascade");}
            expect(request("POST","/api/logout",new JSONObject(),origin()),200);expect(request("GET","/api/inbox",null,null),401);
            server.closeAccess();require(!server.security.authenticated(cookie),"closing revokes connections");
        } finally { server.closeAccess(); delete(directory); }
    }
    private String origin(){return "http://127.0.0.1:"+port;}
    private Reply request(String method,String path,JSONObject body,String origin)throws Exception{
        // Raw loopback sockets avoid changing the release APK's outbound cleartext policy.
        try(Socket socket=new Socket()){
            socket.connect(new InetSocketAddress("127.0.0.1",port),5000);socket.setSoTimeout(5000);
            byte[] bytes=body==null?new byte[0]:body.toString().getBytes(StandardCharsets.UTF_8);
            StringBuilder headers=new StringBuilder(method+" "+path+" HTTP/1.1\r\nHost: 127.0.0.1:"+port+"\r\nConnection: close\r\n");
            if(cookie!=null)headers.append("Cookie: ").append(cookie).append("\r\n");
            if(csrf!=null)headers.append("X-Chrona-CSRF: ").append(csrf).append("\r\n");
            if(origin!=null)headers.append("Origin: ").append(origin).append("\r\n");
            if(body!=null)headers.append("Content-Type: application/json\r\nContent-Length: ").append(bytes.length).append("\r\n");
            headers.append("\r\n");OutputStream output=socket.getOutputStream();output.write(headers.toString().getBytes(StandardCharsets.US_ASCII));output.write(bytes);output.flush();
            BufferedInputStream input=new BufferedInputStream(socket.getInputStream());int status=Integer.parseInt(line(input).split(" ")[1]);int length=-1;String header;
            while(!(header=line(input)).isEmpty()){
                if(header.toLowerCase(java.util.Locale.ROOT).startsWith("content-length:"))length=Integer.parseInt(header.substring(15).trim());
                if(header.toLowerCase(java.util.Locale.ROOT).startsWith("set-cookie:"))cookie=header.substring(11).trim().split(";",2)[0];
            }
            if(length<0||length>1024*1024)throw new IOException("Invalid test response length");
            byte[] response=new byte[length];int offset=0;while(offset<length){int count=input.read(response,offset,length-offset);if(count<0)throw new IOException("Truncated response");offset+=count;}
            return new Reply(status,new JSONObject(new String(response,StandardCharsets.UTF_8)));
        }
    }
    private static String line(InputStream input)throws IOException{ByteArrayOutputStream value=new ByteArrayOutputStream();int next;while((next=input.read())!=-1&&next!='\n'){if(next!='\r')value.write(next);if(value.size()>8192)throw new IOException("Header too long");}if(next==-1)throw new EOFException();return value.toString("US-ASCII");}
    private static final class Reply{final int status;final JSONObject body;Reply(int status,JSONObject body){this.status=status;this.body=body;}}
    private static void expect(Reply reply,int code){require(reply.status==code,"expected HTTP "+code+" got "+reply.status);}
    private static void require(boolean result,String label){if(!result)throw new AssertionError(label);}
    private static void delete(File file){if(file.isDirectory()){File[] children=file.listFiles();if(children!=null)for(File child:children)delete(child);}if(!file.delete())file.deleteOnExit();}
}
