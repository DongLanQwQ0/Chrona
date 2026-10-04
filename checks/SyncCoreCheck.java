import com.donglan.chrona.sync.SyncState;
import com.donglan.chrona.sync.WebDavClient;
import org.json.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public final class SyncCoreCheck {
    interface Throwing { void run() throws Exception; }
    static void check(boolean ok,String message) { if(!ok) throw new AssertionError(message); }
    static void fails(Throwing fn,String label) throws Exception {
        try { fn.run(); } catch (Exception expected) { return; } throw new AssertionError(label);
    }
    static JSONObject value(String text) { return new JSONObject().put("text",text); }
    static String state(SyncState s) { return SyncState.canonical(s.toJson().getJSONObject("records")); }
    public static void main(String[] args) throws Exception {
        SyncState a=new SyncState("a"),b=new SyncState("b"); a.put("task",value("initial")); b.merge(a);
        String before=state(a); a.put("task",value("initial")); check(before.equals(state(a)),"unchanged payload advanced clock");
        a.put("task",value("a")); b.put("task",value("b")); SyncState left=a.copy(),right=b.copy();
        left.merge(b); right.merge(a); check(state(left).equals(state(right)),"merge convergence");
        check(left.records().get("task").size()==2,"lost concurrent update");
        before=state(left); left.merge(right); check(before.equals(state(left)),"merge idempotence");
        left.records().get("task").get(0).payload.put("text","mutation"); check(before.equals(state(left)),"snapshot leaked payload");
        left.resolve("task",left.records().get("task").get(0)); right.merge(left);
        check(right.records().get("task").size()==1,"resolution failed to dominate siblings");
        SyncState del=new SyncState("deleteDevice"),edit=new SyncState("editDevice"); del.merge(left); edit.merge(left);
        del.delete("task"); edit.put("task",value("edited")); del.merge(edit);
        check(del.records().get("task").size()==2 && del.records().get("task").stream().anyMatch(v->v.deleted),"delete edit conflict lost");
        del.resolve("task",del.records().get("task").stream().filter(v->v.deleted).findFirst().get());
        del.merge(a); check(del.records().get("task").size()==1 && del.records().get("task").get(0).deleted,"old version resurrected tombstone");
        JSONObject corrupted=a.toJson(); corrupted.getJSONObject("records").getJSONArray("task").getJSONObject(0).getJSONObject("payload").put("text","corrupt");
        before=state(a); fails(()->a.merge(SyncState.fromJson(corrupted)),"same clock corruption accepted"); check(before.equals(state(a)),"failed merge mutated state");
        fails(()->a.put("../escape",value("bad")),"path id accepted");
        JSONObject invalid=a.toJson(); invalid.getJSONObject("records").getJSONArray("task").getJSONObject(0).getJSONObject("clock").put("a",-1);
        fails(()->SyncState.fromJson(invalid),"negative clock accepted");
        JSONObject tooMany=a.toJson(); JSONArray versions=tooMany.getJSONObject("records").getJSONArray("task");
        for(int i=0;i<64;i++) versions.put(versions.getJSONObject(0));
        fails(()->SyncState.fromJson(tooMany),"version limit ignored");
        check(SyncState.canonical(new JSONObject("{\"b\":2,\"a\":1}")).equals("{\"a\":1,\"b\":2}"),"canonical keys");
        check("a".equals(SyncState.fromJson(a.toJson()).deviceId),"identity lost");
        transport(); System.out.println("Sync core checks passed");
    }
    static final String NAME="12345678-1234-1234-1234-123456789abc.json";
    static String response(String href,String etag) { return "<d:response><d:href>"+href+"</d:href><d:propstat><d:prop><d:getetag>"+etag+"</d:getetag></d:prop><d:status>HTTP/1.1 200 OK</d:status></d:propstat></d:response>"; }
    static byte[] xml(String entries) { return ("<d:multistatus xmlns:d=\"DAV:\">"+entries+"</d:multistatus>").getBytes(StandardCharsets.UTF_8); }
    static void transport() throws Exception {
        URI collection=URI.create("https://example.com/dav/ChronaSync/v1/heads/");
        byte[] listing=xml(response(collection.getPath()+NAME,"&quot;one&quot;")+response("https://evil.test/"+NAME,"&quot;two&quot;")+response("../"+NAME,"&quot;three&quot;")+response("%2e%2e/"+NAME,"&quot;four&quot;"));
        check(WebDavClient.parseHeads(listing,collection).size()==1,"unsafe listing href accepted");
        fails(()->WebDavClient.parseHeads("<!DOCTYPE x [<!ENTITY e SYSTEM 'file:///secret'>]><d:multistatus xmlns:d='DAV:'>&e;</d:multistatus>".getBytes(StandardCharsets.UTF_8),collection),"external entity accepted");
        fails(()->WebDavClient.parseHeads(xml(response(NAME,"")),collection),"missing ETag accepted");
        fails(()->new WebDavClient("http://example.com","u","p"),"HTTP accepted");
        fails(()->new WebDavClient("https://u:p@example.com","u","p"),"URL credentials accepted");
        fails(()->new WebDavClient("https://example.com/?token=x","u","p"),"URL query accepted");
        HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        AtomicReference<String> mode=new AtomicReference<>("normal"),match=new AtomicReference<>();
        byte[] blob="attachment".getBytes(StandardCharsets.UTF_8); String sha=SyncState.sha256(blob);
        server.createContext("/",exchange->{
            String method=exchange.getRequestMethod(),path=exchange.getRequestURI().getPath();
            byte[] result=new byte[0]; int code=200;
            if (mode.get().equals("redirect")) { code=302; exchange.getResponseHeaders().add("Location","http://127.0.0.1:1/leak"); }
            else if(method.equals("MKCOL")) code=201;
            else if(method.equals("PROPFIND")) { code=207; result=xml(response("/ChronaSync/v1/heads/"+NAME,"&quot;one&quot;")); }
            else if(method.equals("PUT")) { match.set(exchange.getRequestHeaders().getFirst("If-Match")+"|"+exchange.getRequestHeaders().getFirst("If-None-Match")); code=mode.get().equals("conflict")?412:201; }
            else if(path.contains("/blobs/")) result=mode.get().equals("corrupt")?"bad".getBytes(StandardCharsets.UTF_8):blob;
            else { result="{}".getBytes(StandardCharsets.UTF_8); if(!mode.get().equals("noetag")) exchange.getResponseHeaders().add("ETag","\"one\""); }
            if(mode.get().equals("oversize")) { exchange.sendResponseHeaders(200,WebDavClient.HEAD_LIMIT+1L); exchange.close(); return; }
            exchange.sendResponseHeaders(code,result.length==0?-1:result.length);
            if(result.length>0) exchange.getResponseBody().write(result); exchange.close();
        });
        server.start(); Path dir=Files.createTempDirectory("chrona-sync-check");
        try(WebDavClient client=new WebDavClient("http://127.0.0.1:"+server.getAddress().getPort(),"test","secret",true)) {
            client.testConnection(); check(client.listHeads().containsKey(NAME),"DAV listing failed");
            check(client.readHead(NAME).etag.equals("\"one\""),"head ETag");
            client.writeHead(NAME,new byte[0],null); check("null|*".equals(match.get()),"create is unconditional");
            client.writeHead(NAME,new byte[0],"\"one\""); check("\"one\"|null".equals(match.get()),"update is unconditional");
            mode.set("conflict"); fails(()->client.writeHead(NAME,new byte[0],"\"one\""),"412 ignored");
            Path source=dir.resolve("source"),target=dir.resolve("target"); Files.write(source,blob);
            client.putBlob(sha,source.toFile()); // Existing content-addressed blob: 412 is success.
            mode.set("normal"); client.getBlob(sha,target.toFile()); check(Arrays.equals(blob,Files.readAllBytes(target)),"blob download");
            mode.set("corrupt"); fails(()->client.getBlob(sha,target.toFile()),"bad blob accepted"); check(Arrays.equals(blob,Files.readAllBytes(target)),"bad blob overwrote target");
            mode.set("noetag"); fails(()->client.readHead(NAME),"missing head ETag accepted");
            mode.set("redirect"); fails(()->client.readHead(NAME),"redirect accepted");
            mode.set("oversize"); fails(()->client.readHead(NAME),"oversize accepted");
            fails(()->client.writeHead(NAME,new byte[WebDavClient.HEAD_LIMIT+1],null),"oversize upload accepted");
        } finally {
            server.stop(0); try(java.util.stream.Stream<Path> files=Files.list(dir)) { for(Path p:(Iterable<Path>)files::iterator) Files.delete(p); } Files.delete(dir);
        }
    }
}
