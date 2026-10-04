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
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try { check(); result.putString("stream", "LAN integration: PASS (real Android SQLite and HTTP)\n"); finish(-1, result); }
        catch (Throwable error) { result.putString("stream", "LAN integration: FAIL " + error.getClass().getSimpleName() + ": " + error.getMessage()+"\n"); finish(0, result); }
    }
    private String cookie, csrf;
    private int port;
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
