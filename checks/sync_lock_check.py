"""Run the production AndroidSync coordinator with latch-controlled network/storage adapters."""
from pathlib import Path
import os
import subprocess
import textwrap

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/sync-lock-check"
SRC = OUT / "src"
JAVA = Path(os.environ.get("JAVA_HOME", "D:/Minecraft/java21")) / "bin"
JSON = ROOT / "build/ai-checks/json.jar"
TEMPORARY = OUT / "tmp"
TEMPORARY.mkdir(parents=True, exist_ok=True)

def write(path, source):
    target = SRC / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(textwrap.dedent(source), encoding="utf-8")

write("com/donglan/chrona/AndroidSync.java", (ROOT / "app/src/main/java/com/donglan/chrona/AndroidSync.java").read_text(encoding="utf-8"))
write("com/donglan/chrona/calendar/LinkedCalendarEvents.java", (ROOT / "app/src/main/java/com/donglan/chrona/calendar/LinkedCalendarEvents.java").read_text(encoding="utf-8"))
write("com/donglan/chrona/sync/SyncState.java", (ROOT / "shared/src/main/java/com/donglan/chrona/sync/SyncState.java").read_text(encoding="utf-8"))
write("android/content/Context.java", '''
package android.content;
public class Context { final java.io.File root; public Context(java.io.File root){this.root=root;root.mkdirs();}
public Context getApplicationContext(){return this;} public java.io.File getFilesDir(){return root;} }
''')
write("android/os/Looper.java", 'package android.os; public class Looper {public static Looper getMainLooper(){return new Looper();}}')
write("android/os/Handler.java", 'package android.os; public class Handler {public Handler(Looper l){}public void post(Runnable r){r.run();}}')
write("android/os/SystemClock.java", 'package android.os; public class SystemClock {public static long elapsedRealtime(){return 1;}}')
write("android/util/AtomicFile.java", '''
package android.util; import java.io.*;
public class AtomicFile {final File f;public AtomicFile(File f){this.f=f;}public File getBaseFile(){return f;}
public InputStream openRead()throws Exception{return new FileInputStream(f);}public FileOutputStream startWrite()throws Exception{return new FileOutputStream(f);}
public void finishWrite(FileOutputStream o)throws Exception{o.close();}public void failWrite(FileOutputStream o)throws Exception{o.close();}public void delete(){f.delete();}}
''')
write("android/database/sqlite/SQLiteDatabase.java", '''
package android.database.sqlite; public class SQLiteDatabase {public void beginTransaction(){} public void endTransaction(){}
public void setTransactionSuccessful(){}public void execSQL(String s){}public void execSQL(String s,Object[] a){}}
''')
write("com/donglan/chrona/data/TaskRecord.java", 'package com.donglan.chrona.data; public class TaskRecord {}')
write("com/donglan/chrona/data/EventCandidate.java", 'package com.donglan.chrona.data; public class EventCandidate {public long id,taskId;public Long calendarEventId;}')
write("com/donglan/chrona/data/TaskStore.java", '''
package com.donglan.chrona.data; public class TaskStore implements AutoCloseable {
public TaskStore(android.content.Context c){}public android.database.sqlite.SQLiteDatabase getWritableDatabase(){return new android.database.sqlite.SQLiteDatabase();}
public static void addCandidateMerges(android.database.sqlite.SQLiteDatabase d){}public void rotateRestoredSyncIdentity(){}
public boolean hasOtherCalendarReferences(long id,java.util.Collection<Long> excluding){return false;}
public boolean setCalendarEventIdIfUnlinked(long id,long task,long event){throw new AssertionError("sync lock fixture cannot publish events");}
public boolean replaceCalendarEventId(long id,long task,long expected,Long replacement){throw new AssertionError("sync lock fixture cannot relink events");}
public TaskRecord getTask(long id){return null;}public java.util.List<EventCandidate> getCandidates(long id){return java.util.Collections.emptyList();}public void close(){}}
''')
write("com/donglan/chrona/calendar/CalendarStore.java", '''
package com.donglan.chrona.calendar;public class CalendarStore {public CalendarStore(android.content.Context c){}
public boolean deleteEvent(long id){return true;}public EventRecord getEvent(long id){return null;}
public long insertEvent(EventInput input){throw new AssertionError("sync lock fixture cannot insert provider event");}
public boolean updateEvent(long id,EventInput input){throw new AssertionError("sync lock fixture cannot update provider event");}
public boolean updateDescription(long id,String value){throw new AssertionError("sync lock fixture cannot edit provider notes");}
public boolean replaceReminders(long id,java.util.List<Integer> values){throw new AssertionError("sync lock fixture cannot edit provider reminders");}
public static class EventRecord {public String title,timeZoneId,description,location;public long startMillis,endMillis;public boolean allDay;public java.util.List<Integer> reminderMinutes;}
public static class EventInput {
public EventInput(String title,long start,long end,String zone,boolean allDay,String description,String location,java.util.List<Integer> minutes){throw new AssertionError("sync lock fixture cannot build provider input");}
public EventInput withRepeat(EventRecord event){throw new AssertionError("sync lock fixture cannot copy provider event");}}
}
''')
write("com/donglan/chrona/sync/WebDavClient.java", '''
package com.donglan.chrona.sync;public class WebDavClient implements AutoCloseable {
public static final int HEAD_LIMIT=32*1024*1024;public static class Failure extends java.io.IOException {}
public static volatile boolean closed;public WebDavClient(String u,String a,String p){}public void close(){closed=true;}}
''')
write("com/donglan/chrona/sync/SyncExchange.java", '''
package com.donglan.chrona.sync;import java.util.concurrent.*;
public class SyncExchange {
public static CountDownLatch entered,release;public static boolean fail;public static void reset(){entered=new CountDownLatch(1);release=new CountDownLatch(1);fail=false;WebDavClient.closed=false;}
public static SyncState exchange(SyncState local,java.io.File blobs,WebDavClient client)throws Exception {
if(Thread.holdsLock(com.donglan.chrona.AndroidSync.LOCK))throw new AssertionError("network holds LOCK");
if(com.donglan.chrona.AndroidSyncData.opens!=0)throw new AssertionError("network retains database handle");
entered.countDown();if(!release.await(4,TimeUnit.SECONDS))throw new AssertionError("network not released");
if(fail)throw new java.io.IOException("fixture failure");return local.copy();}}
''')
write("com/donglan/chrona/AndroidSyncData.java", '''
package com.donglan.chrona;import java.io.*;import java.util.*;import org.json.*;import com.donglan.chrona.sync.*;
public class AndroidSyncData implements AutoCloseable {
public static volatile int opens,applies;static volatile String text="before";
static final String ID="task_00000000-0000-0000-0000-000000000001";
static class AttachmentException extends IOException {}
final android.database.sqlite.SQLiteDatabase db=new android.database.sqlite.SQLiteDatabase();
final com.donglan.chrona.data.TaskStore store;final File blobs;
AndroidSyncData(android.content.Context c){if(!Thread.holdsLock(AndroidSync.LOCK))throw new AssertionError("DB outside lock");opens++;store=new com.donglan.chrona.data.TaskStore(c);blobs=new File(c.getFilesDir(),"sync-blobs");blobs.mkdirs();}
static class Snapshot {Object terms;Map<Long,JSONObject> payloads=new HashMap<>();Map<String,JSONObject> termPayloads=new HashMap<>(),classroomPayloads=new HashMap<>();String text;}
Snapshot prepareCapture(){Snapshot s=new Snapshot();s.text=text;return s;}
void captureTasks(SyncState s,Snapshot p)throws Exception {JSONObject j=new JSONObject().put("rawText",p.text);java.util.List<SyncState.Version> v=s.records().get(ID);if(v==null||!p.text.equals(v.get(0).payload.optString("rawText")))s.put(ID,j);}
void captureTerms(SyncState s,Snapshot p){}void applyMergeRecords(SyncState s){}void prepareTaskApply(SyncState s,Snapshot p,String id){}
void captureClassrooms(SyncState s,Snapshot p){}void applyClassrooms(SyncState s,Snapshot p,String id){}
void prepareTaskApply(SyncState s,Snapshot p,String id,String only){}void requireRevision(Snapshot s){}
void applyTask(String id,SyncState.Version v){applies++;text=v.payload.optString("rawText");}void appliedResourcesCommitted(){}void applyTerms(SyncState s,Object t){}
long localId(String id){return 1;}boolean linked(long id){return false;}boolean taskDifferent(String id,SyncState.Version v){return false;}
static boolean busy(com.donglan.chrona.data.TaskRecord t){return false;}public void close(){opens--;}}
''')
write("com/donglan/chrona/WebDavSettingsStore.java", '''
package com.donglan.chrona;class WebDavSettingsStore {static volatile String status="";WebDavSettingsStore(android.content.Context c){}
boolean configured(){return true;}boolean automatic(){return true;}void automatic(boolean b){}String url(){return "fixture";}String user(){return "fixture";}String password(){return "fixture";}
void status(String s){status=s;}String status(){return status;}}
''')
write("com/donglan/chrona/SyncJobService.java", 'package com.donglan.chrona;class SyncJobService {static void schedule(android.content.Context c){}}')
write("com/donglan/chrona/SyncLockCheck.java", '''
package com.donglan.chrona;import java.util.concurrent.*;import com.donglan.chrona.sync.*;
public class SyncLockCheck {
static void check(boolean b,String m){if(!b)throw new AssertionError(m);}
static void scenario(boolean restore,boolean fail)throws Exception {
android.content.Context app=new android.content.Context(java.nio.file.Files.createTempDirectory("sync-lock-").toFile());
AndroidSyncData.text="before";AndroidSyncData.applies=0;SyncExchange.reset();SyncExchange.fail=fail;CountDownLatch done=new CountDownLatch(1);String[] result={null};
check(AndroidSync.request(app,s->{result[0]=s;done.countDown();}),"first worker rejected");
check(SyncExchange.entered.await(3,TimeUnit.SECONDS),"network not reached");
check(!AndroidSync.request(app,null),"single-worker guard lost");
ExecutorService reader=Executors.newSingleThreadExecutor();
try {reader.submit(()->{synchronized(AndroidSync.LOCK){try{check(AndroidSync.pending(app).isEmpty(),"pending read");AndroidSyncData.text="during HTTP";
if(restore){check(AndroidSyncData.opens==0,"restore saw open DB");AndroidSync.resetAfterRestore(app);AndroidSyncData.text="restored";}}catch(Exception e){throw new RuntimeException(e);}}}).get(1,TimeUnit.SECONDS);}
finally{reader.shutdownNow();}
if(restore)AndroidSync.cancelNetwork();
if(restore)check(WebDavClient.closed,"cancel did not close active client");
SyncExchange.release.countDown();check(done.await(3,TimeUnit.SECONDS),"worker stuck");check(!AndroidSync.running(),"running flag retained");
if(restore){check(AndroidSyncData.applies==0,"old HTTP state applied after restore");check(!new java.io.File(app.getFilesDir(),"sync-state.json").exists(),"old HTTP state rewritten after restore");
check(result[0].startsWith("备份恢复后自动同步已关闭"),"restore status overwritten");check(AndroidSyncData.text.equals("restored"),"restored edit overwritten");}
else {check(AndroidSyncData.text.equals("during HTTP"),"local edit lost");check(AndroidSync.read(app).records().get(AndroidSyncData.ID).get(0).payload.optString("rawText").equals("during HTTP"),"local edit not persisted");}
}
public static void main(String[] a)throws Exception {try{scenario(false,false);scenario(true,false);scenario(true,true);System.out.println("Sync lock: 3 latch scenarios passed (production coordinator, real SyncState; network/DB adapters)");}finally{java.lang.reflect.Field f=AndroidSync.class.getDeclaredField("WORKER");f.setAccessible(true);((ExecutorService)f.get(null)).shutdownNow();}}}
''')
sources = [str(p) for p in SRC.rglob("*.java")]
subprocess.run([str(JAVA / "javac.exe"), f"-J-Djava.io.tmpdir={TEMPORARY}", "--release", "17", "-encoding", "UTF-8", "-cp", str(JSON), "-d", str(OUT), *sources], check=True)
subprocess.run([str(JAVA / "java.exe"), f"-Djava.io.tmpdir={TEMPORARY}", "-cp", str(OUT) + os.pathsep + str(JSON), "com.donglan.chrona.SyncLockCheck"], check=True, timeout=25)
