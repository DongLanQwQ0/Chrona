"""Exercise the production upload receiver and TaskFileStore against isolated Android/provider adapters."""
from pathlib import Path
import os
import subprocess
import textwrap

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/lan-attachment-upload-check"
SRC = OUT / "src"
JAVA = Path(os.environ.get("JAVA_HOME", "D:/Minecraft/java21")) / "bin"
JSON = ROOT / "build/ai-checks/json.jar"

def write(path, source):
    target = SRC / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(textwrap.dedent(source), encoding="utf-8")

for file in ["LanAttachmentUpload.java", "data/TaskFileStore.java", "data/TaskFileAttachment.java", "data/TaskRecord.java"]:
    source = ROOT / "app/src/main/java/com/donglan/chrona" / file
    write("com/donglan/chrona/" + file, source.read_text(encoding="utf-8"))

write("android/Manifest.java", '''
package android; public class Manifest {public static class permission {public static final String WRITE_EXTERNAL_STORAGE="write";}}
''')
write("android/content/pm/PackageManager.java", 'package android.content.pm; public class PackageManager {public static final int PERMISSION_GRANTED=0;}')
write("android/os/Build.java", 'package android.os; public class Build {public static class VERSION {public static int SDK_INT=36;}}')
write("android/os/Environment.java", '''
package android.os; public class Environment {public static final String DIRECTORY_DOWNLOADS="Downloads";
public static java.io.File directory;public static java.io.File getExternalStoragePublicDirectory(String n){return directory;}}
''')
write("android/net/Uri.java", '''
package android.net; public class Uri {final String s;private Uri(String s){this.s=s;}
public static Uri parse(String s){return new Uri(s);}public static Uri fromFile(java.io.File f){return parse(f.toURI().toString());}
public String toString(){return s;}}
''')
write("android/content/ContentValues.java", '''
package android.content; public class ContentValues extends java.util.HashMap<String,Object> {
public void put(String k,String v){super.put(k,v);}public void put(String k,int v){super.put(k,v);}public void put(String k,long v){super.put(k,v);}}
''')
write("android/database/Cursor.java", '''
package android.database; public interface Cursor extends AutoCloseable {boolean moveToFirst();String getString(int n);
long getLong(int n);boolean isNull(int n);void close();}
''')
write("android/provider/OpenableColumns.java", 'package android.provider;public class OpenableColumns {public static String DISPLAY_NAME="name",SIZE="size";}')
write("android/provider/MediaStore.java", '''
package android.provider;public class MediaStore {
public static class MediaColumns {public static String DATA="data",DISPLAY_NAME="name",MIME_TYPE="mime",SIZE="size";}
public static class Downloads extends MediaColumns {public static String RELATIVE_PATH="path",IS_PENDING="pending";
public static android.net.Uri EXTERNAL_CONTENT_URI=android.net.Uri.parse("content://downloads");}
public static class Files {public static android.net.Uri getContentUri(String s){return android.net.Uri.parse("content://files");}}}
''')
write("android/content/Context.java", '''
package android.content;public class Context {public final java.io.File root;public int permission=0;public final ContentResolver resolver=new ContentResolver();
public Context(java.io.File root){this.root=root;root.mkdirs();}public Context getApplicationContext(){return this;}
public java.io.File getCacheDir(){return root;}public ContentResolver getContentResolver(){return resolver;}public int checkSelfPermission(String p){return permission;}}
''')
write("android/content/ContentResolver.java", '''
package android.content;import java.io.*;import android.net.Uri;public class ContentResolver {
public final java.util.Map<String,ByteArrayOutputStream> files=new java.util.HashMap<>();public ContentValues metadata;public boolean failInsert,failOutput;public String nativeType;
public String getType(Uri u){return nativeType;}public android.database.Cursor query(Uri u,String[] a,String b,String[] c,String d){return null;}
public Uri insert(Uri u,ContentValues v){metadata=v;if(failInsert)return null;Uri dest=Uri.parse("content://files/"+java.util.UUID.randomUUID());files.put(dest.toString(),new ByteArrayOutputStream());return dest;}
public InputStream openInputStream(Uri u)throws IOException{return new FileInputStream(new File(java.net.URI.create(u.toString())));}
public OutputStream openOutputStream(Uri u,String mode)throws IOException{if(failOutput)throw new IOException("provider write failure");return files.get(u.toString());}
public OutputStream openOutputStream(Uri u)throws IOException{return openOutputStream(u,"w");}
public int update(Uri u,ContentValues v,String a,String[] b){return 1;}public int delete(Uri u,String a,String[] b){return files.remove(u.toString())==null?0:1;}}
''')
write("android/webkit/MimeTypeMap.java", '''
package android.webkit;public class MimeTypeMap {public static MimeTypeMap getSingleton(){return new MimeTypeMap();}
public String getMimeTypeFromExtension(String x){return x.equals("pdf")?"application/pdf":null;}}
''')
write("android/database/sqlite/SQLiteDatabase.java", '''
package android.database.sqlite;public class SQLiteDatabase {boolean success;long revision;int associations;
public void beginTransaction(){success=false;revision=com.donglan.chrona.data.TaskStore.revision;associations=com.donglan.chrona.data.TaskStore.associations;}
public void setTransactionSuccessful(){success=true;}public void endTransaction(){if(!success){com.donglan.chrona.data.TaskStore.revision=revision;com.donglan.chrona.data.TaskStore.associations=associations;}}}
''')
write("com/donglan/chrona/AndroidSync.java", 'package com.donglan.chrona;public class AndroidSync {public static final Object LOCK=new Object();}')
write("com/donglan/chrona/data/TaskStore.java", '''
package com.donglan.chrona.data;public class TaskStore implements AutoCloseable {
public static long revision=5;public static int associations;public static boolean managed,failAdd,missing;public static String status=TaskRecord.READY;
public TaskStore(android.content.Context c){if(!Thread.holdsLock(com.donglan.chrona.AndroidSync.LOCK))throw new AssertionError("DB outside lock");}
final android.database.sqlite.SQLiteDatabase db=new android.database.sqlite.SQLiteDatabase();public android.database.sqlite.SQLiteDatabase getWritableDatabase(){return db;}
public long dataRevision(){return revision;}public TaskRecord getTask(long id){return missing?null:new TaskRecord(id,"original","fixture",0,status,null);}
public boolean isHomeworkCapture(long id){return managed;}public boolean addImageAttachment(long id,String p){return add();}
public boolean addFileAttachment(long id,TaskFileAttachment f){return add();}boolean add(){associations++;revision++;if(failAdd)throw new IllegalStateException("registration failed");return true;}
public void close(){}}
''')
write("com/donglan/chrona/image/ImageStore.java", '''
package com.donglan.chrona.image;public class ImageStore {public static int imports,live;public ImageStore(android.content.Context c){}
public String importImage(android.net.Uri u)throws java.io.IOException {imports++;live++;return "image.jpg";}public boolean delete(String s){live--;return true;}}
''')
write("fi/iki/elonen/NanoHTTPD.java", '''
package fi.iki.elonen;public class NanoHTTPD {public interface IHTTPSession {java.util.Map<String,String> getHeaders();java.io.InputStream getInputStream();}}
''')
write("com/donglan/chrona/LanAttachmentUploadCheck.java", r'''
package com.donglan.chrona;
import java.io.*;import java.util.*;import com.donglan.chrona.data.*;import com.donglan.chrona.image.ImageStore;
public class LanAttachmentUploadCheck {
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
interface Run {void run()throws Exception;}
static void rejects(Class<?> type,Run work)throws Exception {try{work.run();throw new AssertionError("accepted invalid upload");}catch(Exception e){if(!type.isInstance(e))throw e;}}
static class Request implements fi.iki.elonen.NanoHTTPD.IHTTPSession {
Map<String,String> h=new HashMap<>();InputStream input;Request(byte[] b){input=new ByteArrayInputStream(b);h.put("content-type","application/octet-stream");h.put("content-length",Long.toString(b.length));h.put("x-chrona-task-id","1");h.put("x-chrona-revision",Long.toString(TaskStore.revision));h.put("x-chrona-filename","%E9%99%84%E4%BB%B6.PDF");h.put("x-chrona-image","false");}
public Map<String,String> getHeaders(){return h;}public InputStream getInputStream(){check(!Thread.holdsLock(AndroidSync.LOCK),"network body inside LOCK");return input;}}
static android.content.Context app;
static Request req(){return new Request(new byte[]{1,2,3});}
static void noWrite(Class<?> type,Request request)throws Exception {
int n=TaskStore.associations,files=app.resolver.files.size(),images=ImageStore.live;long rev=TaskStore.revision;
rejects(type,()->LanAttachmentUpload.receive(app,request));check(TaskStore.associations==n&&TaskStore.revision==rev,"invalid request wrote DB");
check(app.resolver.files.size()==files&&ImageStore.live==images,"failed request left durable import");check(app.root.list().length==0,"temporary upload leaked");}
public static void main(String[] args)throws Exception {
app=new android.content.Context(java.nio.file.Files.createTempDirectory("chrona-upload-fixture-").toFile());
for(String n:new String[]{"0","-1","20971521","9223372036854775808","3,3","3 ",""}){Request r=req();r.h.put("content-length",n);noWrite(IllegalArgumentException.class,r);}
Request r=req();r.h.remove("content-length");noWrite(IllegalArgumentException.class,r);
for(String key:new String[]{"x-chrona-task-id","x-chrona-revision"}){r=req();r.h.put(key,"-1");noWrite(IllegalArgumentException.class,r);}
r=req();r.h.put("transfer-encoding","chunked");noWrite(IllegalArgumentException.class,r);
r=req();r.h.put("content-type","text/plain");noWrite(IllegalArgumentException.class,r);
r=req();r.h.put("x-chrona-image","yes");noWrite(IllegalArgumentException.class,r);
for(String name:new String[]{"","%XX","%FF",".."}){r=req();r.h.put("x-chrona-filename",name);noWrite(IllegalArgumentException.class,r);}
r=req();r.h.put("content-length","4");noWrite(IOException.class,r);
r=req();r.h.put("x-chrona-revision","0");noWrite(ConcurrentModificationException.class,r);
TaskStore.missing=true;noWrite(ConcurrentModificationException.class,req());TaskStore.missing=false;
for(String s:new String[]{TaskRecord.QUEUED,TaskRecord.PROCESSING}){TaskStore.status=s;noWrite(ConcurrentModificationException.class,req());}TaskStore.status=TaskRecord.READY;
TaskStore.managed=true;noWrite(IllegalArgumentException.class,req());TaskStore.managed=false;
android.os.Build.VERSION.SDK_INT=28;app.permission=-1;noWrite(SecurityException.class,req());android.os.Build.VERSION.SDK_INT=36;app.permission=0;
app.resolver.failOutput=true;noWrite(IOException.class,req());app.resolver.failOutput=false;
app.resolver.failInsert=true;noWrite(IOException.class,req());app.resolver.failInsert=false;
TaskStore.failAdd=true;noWrite(IllegalStateException.class,req());r=req();r.h.put("x-chrona-image","true");noWrite(IllegalStateException.class,r);TaskStore.failAdd=false;
check(LanAttachmentUpload.filename("..%2Ffolder%5Cfile%00.pdf").equals(".._folder_file_.pdf"),"path/control sanitization");
check(LanAttachmentUpload.filename("a+b.pdf").equals("a+b.pdf"),"literal plus");check(LanAttachmentUpload.filename("x".repeat(170)+".pdf").length()==160,"name bound");
r=req();r.h.put("x-chrona-filename","content%3A%2F%2Fevil%2Fdocument.PDF");org.json.JSONObject result=LanAttachmentUpload.receive(app,r);
check(result.getBoolean("ok")&&result.getString("id").equals("1")&&result.getLong("revision")==TaskStore.revision,"result contract");
check(app.resolver.metadata.get("name").equals("content:__evil_document.PDF"),"untrusted name used as URI");check(app.resolver.metadata.get("mime").equals("application/pdf"),"extension MIME");
check(Arrays.equals(app.resolver.files.values().iterator().next().toByteArray(),new byte[]{1,2,3}),"import bytes");
int files=app.resolver.files.size();r=req();r.h.put("x-chrona-image","true");LanAttachmentUpload.receive(app,r);check(ImageStore.live==1&&app.resolver.files.size()==files,"image import route");
TaskStore.failAdd=true;noWrite(IllegalStateException.class,req());TaskStore.failAdd=false;
LanAttachmentUpload.receive(app,req());check(app.resolver.files.size()==files+1,"failed attachment must retain prior files and permit retry");
long[] read={0};InputStream sized=new InputStream(){public int read(){throw new AssertionError();}public int read(byte[] b,int o,int n){check(n<=32768,"unbounded read");int got=(int)Math.min(n,LanAttachmentUpload.MAX_BYTES-read[0]);if(got==0)return -1;read[0]+=got;return got;}};
LanAttachmentUpload.copy(sized,OutputStream.nullOutputStream(),LanAttachmentUpload.MAX_BYTES);check(read[0]==LanAttachmentUpload.MAX_BYTES,"maximum upload");
check(app.root.list().length==0,"successful upload leaked temp");
File nativeSource=File.createTempFile("native", ".txt",app.root);java.nio.file.Files.write(nativeSource.toPath(),new byte[]{9});
TaskFileStore fileStore=new TaskFileStore(app);app.resolver.nativeType="text/plain; charset=utf-8";
check(fileStore.importFile(android.net.Uri.fromFile(nativeSource)).mimeType.equals(app.resolver.nativeType),"native provider MIME contract changed");
rejects(IllegalArgumentException.class,()->fileStore.importFile(android.net.Uri.fromFile(nativeSource),"../escape.pdf","application/pdf"));
rejects(IllegalArgumentException.class,()->fileStore.importFile(android.net.Uri.fromFile(nativeSource),"a\nb\nc.pdf","application/pdf"));
nativeSource.delete();System.out.println("PASS: production upload limits, short reads, name/URI boundaries, permission, revision/status, streamed bytes, rollback and retry preservation; native import metadata preserved");
}}
''')
classes = OUT / "classes"
classes.mkdir(parents=True, exist_ok=True)
subprocess.run([str(JAVA / "javac"), "-encoding", "UTF-8", "-cp", str(JSON), "-d", str(classes),
                *map(str, SRC.rglob("*.java"))], check=True)
subprocess.run([str(JAVA / "java"), "-cp", str(classes) + os.pathsep + str(JSON),
                "com.donglan.chrona.LanAttachmentUploadCheck"], check=True)
