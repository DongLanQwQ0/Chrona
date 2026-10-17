package com.donglan.chrona.sync;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.*;
import org.xml.sax.InputSource;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.ResponseBody;
import java.util.concurrent.TimeUnit;

/** Small HTTPS WebDAV transport. A client is cancellable and must not be reused after close. */
public final class WebDavClient implements AutoCloseable {
    /** Safe to show: messages contain no server text, account, URL or credentials. */
    public static final class Failure extends IOException {
        public Failure(String message) { super(message); }
    }
    public static final int HEAD_LIMIT = 32 * 1024 * 1024, BLOB_LIMIT = 20 * 1024 * 1024;
    private final URI base, heads, blobs;
    private final String authorization;
    private final Set<Call> active = Collections.newSetFromMap(new ConcurrentHashMap<Call,Boolean>());
    private final OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).followSslRedirects(false)
            .connectTimeout(15,TimeUnit.SECONDS).readTimeout(30,TimeUnit.SECONDS).writeTimeout(30,TimeUnit.SECONDS).build();
    private volatile boolean closed;
    public static final class Head {
        public final byte[] bytes;
        public final String etag;
        public Head(byte[] bytes, String etag) { this.bytes=bytes; this.etag=etag; }
    }
    public WebDavClient(String baseUrl, String user, String password) { this(baseUrl,user,password,false); }
    /** The HTTP exception is strictly for a local offline test server. */
    public WebDavClient(String baseUrl, String user, String password, boolean allowHttpLoopback) {
        try {
            URI uri = new URI(baseUrl);
            boolean local = allowHttpLoopback && "http".equalsIgnoreCase(uri.getScheme())
                    && ("127.0.0.1".equals(uri.getHost()) || "[::1]".equals(uri.getHost()) || "::1".equals(uri.getHost()));
            if ((!"https".equalsIgnoreCase(uri.getScheme()) && !local) || uri.getHost() == null
                    || uri.getRawUserInfo()!=null || uri.getRawQuery()!=null || uri.getRawFragment()!=null)
                throw new IllegalArgumentException("WebDAV requires an HTTPS server URL without credentials, query or fragment");
            if (user == null || password == null || user.contains(":") || user.contains("\r") || user.contains("\n"))
                throw new IllegalArgumentException("Invalid WebDAV credentials");
            base = new URI(uri.toASCIIString() + (uri.getRawPath().endsWith("/") ? "" : "/"));
            heads = base.resolve("ChronaSync/v1/heads/"); blobs = base.resolve("ChronaSync/v1/blobs/");
            authorization = "Basic " + Base64.getEncoder().encodeToString((user+":"+password).getBytes(StandardCharsets.UTF_8));
        } catch (URISyntaxException e) { throw new IllegalArgumentException("Invalid WebDAV server URL"); }
    }
    private static void status(int status, int... allowed) throws IOException {
        for (int candidate : allowed) if (status == candidate) return;
        String reason;
        if (status == 401) reason = "WebDAV 认证失败，请检查账号和应用密码";
        else if (status == 403) reason = "WebDAV 拒绝访问，请检查目录读写权限";
        else if (status == 404) reason = "WebDAV 目录不存在，请检查服务地址";
        else if (status == 409 || status == 412) reason = "云端记录已变化，请重新同步";
        else if (status == 429) reason = "WebDAV 请求过于频繁，请稍后重试";
        else if (status >= 300 && status < 400) reason = "WebDAV 地址发生跳转，请填写最终服务地址";
        else if (status >= 500) reason = "WebDAV 服务暂时不可用，请稍后重试";
        else reason = "WebDAV 请求失败";
        throw new Failure(reason + "（HTTP " + status + "）");
    }
    private static void filename(String name) {
        if (name == null || !name.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\.json"))
            throw new IllegalArgumentException("Invalid head filename");
    }
    private static void hash(String sha) {
        if (sha == null || !sha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid blob hash");
    }
    private static String etag(String tag) throws IOException {
        if (tag == null || !tag.matches("\"[^\"\\r\\n]+\""))
            throw new Failure("WebDAV 文件缺少有效版本标识，无法安全同步；请检查服务是否支持强 ETag");
        return tag;
    }
    private static byte[] read(InputStream input, long length, int limit) throws IOException {
        try (InputStream in = input; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            if (length > limit) throw new IOException("Sync response exceeds size limit");
            byte[] b = new byte[16384]; int n, total=0;
            while ((n=in.read(b))!=-1) { total+=n; if (total>limit) throw new IOException("Sync response exceeds size limit"); out.write(b,0,n); }
            return out.toByteArray();
        }
    }
    public void ensureRoot() throws IOException {
        for (String folder : new String[]{"ChronaSync/","ChronaSync/v1/","ChronaSync/v1/heads/","ChronaSync/v1/blobs/"}) {
            Response response = request(base.resolve(folder),"MKCOL",null,Collections.emptyMap(),0);
            status(response.code,201,405);
        }
    }
    public void testConnection() throws IOException { ensureRoot(); listHeads(); }
    public Map<String,String> listHeads() throws IOException {
        byte[] body = "<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop><d:getetag/></d:prop></d:propfind>".getBytes(StandardCharsets.UTF_8);
        Map<String,String> headers = new HashMap<>(); headers.put("Depth","1"); headers.put("Content-Type","application/xml; charset=utf-8");
        Response r = request(heads,"PROPFIND",body,headers,HEAD_LIMIT); status(r.code,207);
        return parseHeads(r.body,heads);
    }
    /** Exposed for deterministic XML/path validation tests. */
    public static Map<String,String> parseHeads(byte[] xml, URI collection) throws IOException {
        if (xml.length > HEAD_LIMIT) throw new IOException("Sync response exceeds size limit");
        try {
            // Decode before parsing: a UTF-16 declaration must not conceal a DTD from this gate.
            // Android's default DOM factory does not implement Xerces security feature switches.
            String safeXml = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                    .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                    .decode(java.nio.ByteBuffer.wrap(xml)).toString();
            if (safeXml.startsWith("\uFEFF")) safeXml = safeXml.substring(1);
            if (safeXml.indexOf('\0') >= 0 || java.util.regex.Pattern.compile("<!\\s*(?:DOCTYPE|ENTITY)",
                    java.util.regex.Pattern.CASE_INSENSITIVE).matcher(safeXml).find())
                throw new IOException("WebDAV XML declarations and entities are not allowed");
            DocumentBuilderFactory f = DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
            f.setValidating(false);
            javax.xml.parsers.DocumentBuilder builder = f.newDocumentBuilder();
            builder.setEntityResolver((publicId,systemId)-> { throw new org.xml.sax.SAXException("External entities disabled"); });
            builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
                @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
            });
            Document doc = builder.parse(new InputSource(new StringReader(safeXml)));
            if (!"DAV:".equals(doc.getDocumentElement().getNamespaceURI()) || !"multistatus".equals(doc.getDocumentElement().getLocalName()))
                throw new IOException("Invalid WebDAV listing");
            Map<String,String> out = new TreeMap<>(); NodeList responses = doc.getElementsByTagNameNS("DAV:","response");
            if (responses.getLength() > SyncState.MAX_DEVICES + 1) throw new IOException("Too many sync heads");
            for (int i=0;i<responses.getLength();i++) {
                Element response=(Element)responses.item(i); String href=childText(response,"href");
                if (href == null) continue;
                URI target;
                try { target=collection.resolve(new URI(href)); } catch (IllegalArgumentException | URISyntaxException bad) { continue; }
                if (!Objects.equals(target.getScheme(),collection.getScheme()) || !Objects.equals(target.getRawAuthority(),collection.getRawAuthority())
                        || target.getRawQuery()!=null || target.getRawFragment()!=null || target.getRawUserInfo()!=null) continue;
                String path=target.getRawPath(), prefix=collection.getRawPath();
                if (!path.startsWith(prefix)) continue;
                String name=path.substring(prefix.length());
                try { filename(name); } catch (IllegalArgumentException bad) { continue; }
                String tag=null;
                NodeList props=response.getElementsByTagNameNS("DAV:","propstat");
                for (int p=0;p<props.getLength();p++) {
                    Element prop=(Element)props.item(p); String code=childText(prop,"status");
                    if (code!=null && code.matches("HTTP/\\S+ 200(?: .*)?")) {
                        NodeList tags=prop.getElementsByTagNameNS("DAV:","getetag");
                        if (tags.getLength()==1) tag=tags.item(0).getTextContent().trim();
                    }
                }
                // A listing discovers names only. The authoritative strong ETag comes from
                // readHead's GET, together with the exact bytes protected by If-Match.
                if (tag == null || tag.isEmpty()) tag = null;
                if (out.containsKey(name) && !Objects.equals(out.get(name), tag)) throw new IOException("Inconsistent WebDAV listing");
                out.put(name,tag);
            }
            return out;
        } catch (IOException e) { throw e; }
        catch (Exception e) { throw new IOException("Invalid or unsafe WebDAV XML"); }
    }
    private static String childText(Element parent,String local) {
        for (Node n=parent.getFirstChild();n!=null;n=n.getNextSibling())
            if (n instanceof Element && "DAV:".equals(n.getNamespaceURI()) && local.equals(n.getLocalName())) return n.getTextContent().trim();
        return null;
    }
    public Head readHead(String name) throws IOException {
        filename(name); Response r=request(heads.resolve(name),"GET",null,Collections.emptyMap(),HEAD_LIMIT);
        status(r.code,200);
        // Some WebDAV services omit ETag or return a weak validator. Those remain readable;
        // only a strong validator can be used for a conditional overwrite.
        String tag = r.etag != null && r.etag.matches("\"[^\"\\r\\n]+\"") ? r.etag : null;
        return new Head(r.body, tag);
    }
    /** Only the owning device may publish this head; its coordinator serializes writers. */
    public void writeHead(String name, byte[] body, Head previous) throws IOException {
        filename(name); if (body == null || body.length > HEAD_LIMIT) throw new IOException("Sync head exceeds size limit");
        Map<String,String> h=new HashMap<>(); h.put("Content-Type","application/json; charset=utf-8");
        if (previous == null) h.put("If-None-Match", "*");
        else if (previous.etag != null) h.put("If-Match", etag(previous.etag));
        // Without ETag this updates only our own single-writer head, never another device's.
        // Distinguish an existing untagged head from a missing head: If-None-Match:* would fail.
        Response r=request(heads.resolve(name),"PUT",body,h,0); status(r.code,200,201,204);
    }
    public void putBlob(String sha, File source) throws IOException {
        hash(sha); byte[] body=read(new FileInputStream(source),source.length(),BLOB_LIMIT);
        if (!sha.equals(SyncState.sha256(body))) throw new IOException("Local attachment checksum mismatch");
        Response r=request(blobs.resolve(sha),"PUT",body,Collections.singletonMap("If-None-Match","*"),0);
        status(r.code,200,201,204,412);
    }
    public void getBlob(String sha, File target) throws IOException {
        hash(sha); Response r=request(blobs.resolve(sha),"GET",null,Collections.emptyMap(),BLOB_LIMIT); status(r.code,200);
        if (!sha.equals(SyncState.sha256(r.body))) throw new IOException("Remote attachment checksum mismatch");
        File parent=target.getAbsoluteFile().getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) throw new IOException("Cannot create attachment directory");
        Path temp=Files.createTempFile(parent.toPath(),".chrona-sync-",".tmp");
        try {
            Files.write(temp,r.body);
            Files.move(temp,target.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    private static final class Response {
        final int code; final byte[] body; final String etag;
        Response(int code,byte[] body,String etag) { this.code=code;this.body=body;this.etag=etag; }
    }
    private Response request(URI uri,String method,byte[] body,Map<String,String> headers,int limit) throws IOException {
        if (closed) throw new IOException("Sync cancelled");
        Request.Builder builder=new Request.Builder().url(uri.toASCIIString())
                .header("Authorization",authorization).header("Accept-Encoding","identity");
        for (Map.Entry<String,String> h:headers.entrySet()) builder.header(h.getKey(),h.getValue());
        RequestBody requestBody=body==null ? null : RequestBody.create(body,null);
        builder.method(method,requestBody);
        Call call=client.newCall(builder.build()); active.add(call);
        try {
            if (closed) { call.cancel(); throw new IOException("Sync cancelled"); }
            try (okhttp3.Response response=call.execute()) {
                int code=response.code(); byte[] bytes=new byte[0]; ResponseBody responseBody=response.body();
                if (limit>0 && code>=200 && code<300 && responseBody!=null)
                    bytes=read(responseBody.byteStream(),responseBody.contentLength(),limit);
                return new Response(code,bytes,response.header("ETag"));
            }
        } catch (IOException e) {
            if (closed) throw new Failure("同步已取消");
            if (e instanceof java.net.SocketTimeoutException) throw new Failure("WebDAV 连接超时，请稍后重试");
            if (e instanceof java.net.UnknownHostException) throw new Failure("无法解析 WebDAV 服务地址，请检查网络或服务地址");
            if (e instanceof javax.net.ssl.SSLException) throw new Failure("WebDAV 安全连接失败，请检查证书与设备时间");
            throw new Failure("WebDAV 网络连接失败，请检查网络后重试");
        }
        finally { active.remove(call); }
    }
    @Override public void close() {
        closed=true; for(Call call:active) call.cancel(); active.clear();
        client.connectionPool().evictAll(); client.dispatcher().executorService().shutdown();
    }
}
