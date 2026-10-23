"""Execute production restore control flow and JSON I/O in isolated real files.

The extracted Java methods are unchanged. Android services, preferences and the
SQLite boundary are doubles: this is not an Android/SQLite end-to-end restore.
All fixtures, generated sources, ZIPs and results stay in build/backup-restore-check.
"""
from pathlib import Path
import os
import re
import subprocess
import textwrap

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/backup-restore-check"
SRC = OUT / "src"
JAVA = Path(os.environ.get("JAVA_HOME", "D:/Minecraft/java21")) / "bin"
JSON = ROOT / "build/ai-checks/json.jar"
production = (ROOT / "app/src/main/java/com/donglan/chrona/ChronaDataBackup.java").read_text(encoding="utf-8")


def method(name):
    """Find a complete method, ignoring braces inside Java strings/comments."""
    match = re.search(r"(?:private|public) static [^\n{;=]+\b" + name + r"\(", production)
    if not match:
        raise AssertionError(f"Production method missing: {name}")
    opening = production.index("{", match.end())
    depth = 1
    token = re.compile(r'"(?:\\.|[^"\\])*"|\'(?:\\.|[^\'\\])*\'|//[^\n]*|/\*[\s\S]*?\*/|[{}]')
    for brace in token.finditer(production, opening + 1):
        if brace.group() == "{":
            depth += 1
        elif brace.group() == "}":
            depth -= 1
            if depth == 0:
                return production[match.start():brace.end()]
    raise AssertionError(f"Unclosed production method: {name}")


def write(name, source):
    target = SRC / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(textwrap.dedent(source), encoding="utf-8")
    return target


methods = [method(name) for name in (
    "restoreExclusive", "installDatabase", "deleteDatabaseFile", "prepareOutputs",
    "validateClassrooms", "rescheduleQueuedTasks", "readText", "writeJson", "copyLimited", "copy", "copyFile", "deleteTree",
)]
export = method("createArchiveLocked")
manifest_write = re.search(r'writeJson\(zip,\s*"manifest.json",[\s\S]*?\);', export)[0]
document_write = re.search(r'writeJson\(zip,\s*prefix\s*\+\s*"document.json",[\s\S]*?\);', export)[0]
methods.append('''static void exportJsonEntry(ZipOutputStream zip, String name, JSONObject value) throws Exception {
        if (name.equals("manifest.json")) { JSONObject manifest=value; ''' + manifest_write + ''' }
        else { String prefix="classrooms/1-2/";
          Map.Entry<String,ClassroomArchive.Entry> entry=Map.entry("1-2",new ClassroomArchive.Entry(value));
          ''' + document_write + ''' }
    }''')
manifest_read = re.search(r'JSONObject manifest\s*=\s*new JSONObject\(readText\(manifestFile[^;]+;', method("prepare"))[0]
methods.append('''static JSONObject readManifest(File manifestFile)throws Exception {
    ''' + manifest_read + ''' return manifest;
}''')

constant = re.search(r"private static final long MAX_MANIFEST_BYTES\s*=\s*[^;]+;", production)[0]
write("com/donglan/chrona/ChronaDataBackup.java", '''package com.donglan.chrona;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.util.zip.*;
import org.json.*;import android.content.*;import android.net.Uri;import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
public final class ChronaDataBackup {
''' + constant + "\n" + "\n".join(methods) + '''
static String run(Context c,Prepared p)throws Exception{return restoreExclusive(c,p);}
static final class Prepared {final File directory;final JSONObject manifest;final String config;
 Prepared(File d,JSONObject m,String c){directory=d;manifest=m;config=c;}}
static final class WallpaperMedia {final Uri uri;WallpaperMedia(Uri u){uri=u;}}
static Map<String,String> restoreImages(Context c,Prepared p,List<File> made){return new HashMap<>();}
static Map<Long,TaskFileAttachment> restoreFiles(Context c,Prepared p,List<Uri> made){return new HashMap<>();}
static Map<Long,Long> restoreCalendarLinks(Context c,Prepared p,List<Long> made){return new HashMap<>();}
static WallpaperMedia restoreWallpaper(Context c,Prepared p,JSONObject j){return null;}
static void cleanupWallpaper(Context c,WallpaperMedia w){}
static long[] inspectDatabase(File f)throws Exception{return Fixture.counts(f);}
static final class JobSchedulerBridge {static void cancel(Context c){}}
}
''')
write("android/content/Context.java", '''package android.content;
import java.io.*;import java.util.*;
public class Context {
 final File root;public final Map<String,Object> values=new HashMap<>();
 public final SharedPreferences reminders=new SharedPreferences();
 public Context(File r){root=r;getFilesDir().mkdirs();getNoBackupFilesDir().mkdirs();getDatabasePath("chrona.db").getParentFile().mkdirs();}
 public Context getApplicationContext(){return this;}
 public File getFilesDir(){return new File(root,"files");}
 public File getNoBackupFilesDir(){return new File(root,"no-backup");}
 public File getDatabasePath(String n){return new File(root,"databases/"+n);}
 public ContentResolver getContentResolver(){return new ContentResolver();}
 public static class ContentResolver{public int delete(android.net.Uri u,String s,String[] a){return 1;}}
}''')
write("android/content/ContentValues.java", '''package android.content;
public class ContentValues{public void put(String k,Object v){}public void putNull(String k){}}''')
write("android/content/SharedPreferences.java", '''package android.content;
import java.util.*;
public class SharedPreferences {
 public final Map<String,Object> values=new HashMap<>();
 public boolean getBoolean(String k,boolean d){return (Boolean)values.getOrDefault(k,d);}
 public Map<String,?> getAll(){return new HashMap<>(values);}
 public Editor edit(){return new Editor();}
 public class Editor {final Set<String> removed=new HashSet<>();
 public Editor remove(String k){removed.add(k);return this;}
 public void apply(){com.donglan.chrona.Fixture.hit("reminder-state");removed.forEach(values::remove);}}
}''')
write("android/net/Uri.java", '''package android.net;
public class Uri {final String value;public Uri(String v){value=v;}public static Uri parse(String s){return new Uri(s);}public String toString(){return value;}}''')
write("android/database/Cursor.java", '''package android.database;
public class Cursor implements AutoCloseable {public boolean moveToFirst(){return true;}public int getInt(int c){return 0;}public void close(){}}''')
write("android/database/sqlite/SQLiteDatabase.java", '''package android.database.sqlite;
public class SQLiteDatabase implements AutoCloseable {public static final int OPEN_READWRITE=0;
public static SQLiteDatabase openDatabase(String p,Object f,int m){return new SQLiteDatabase();}
public int getVersion(){return 12;}public void setVersion(int v){}public void beginTransaction(){}
public void setTransactionSuccessful(){}public void endTransaction(){}
public void update(String t,android.content.ContentValues v,String w,String[] a){}
public void execSQL(String s){}public void execSQL(String s,Object[] a){}
public android.database.Cursor rawQuery(String s,String[] a){return new android.database.Cursor();}public void close(){}}
''')
write("android/graphics/BitmapFactory.java", '''package android.graphics;
public class BitmapFactory {public static class Options {public boolean inJustDecodeBounds;public int outWidth=1,outHeight=1;}
public static void decodeFile(String p,Options o){}}''')
codec = (ROOT / "shared/src/main/java/com/donglan/chrona/sync/TaskCodec.java").read_text(encoding="utf-8")
codec_limits = [re.search(r"public static final (?:int|long) " + name + r"\s*=\s*[^;]+;", codec)[0]
                for name in ("MAX_TASK_BYTES", "MAX_ATTACHMENT_BYTES")]
write("com/donglan/chrona/sync/TaskCodec.java", "package com.donglan.chrona.sync; public class TaskCodec {"
      + "\n".join(codec_limits) + "}")
write("com/donglan/chrona/zju/ZjuCalendarStore.java", '''package com.donglan.chrona.zju;
public class ZjuCalendarStore {public static String exportJson(android.content.Context c){return (String)c.values.getOrDefault("calendar","{}");}
public static void importJson(android.content.Context c,String s){c.values.put("calendar",s);}}''')
write("com/donglan/chrona/Fixture.java", '''package com.donglan.chrona;
import android.content.*;import android.database.sqlite.SQLiteDatabase;import java.io.*;import java.nio.file.*;import java.util.*;import org.json.*;
public class Fixture {
 public static String failure="";public static List<String> reached=new ArrayList<>();
 public static void hit(String point){reached.add(point);if(failure.equals(point))throw new IllegalStateException("injected:"+point);}
 public static void put(File f,String s)throws IOException{f.getParentFile().mkdirs();Files.writeString(f.toPath(),s,java.nio.charset.StandardCharsets.UTF_8);}
 public static String get(File f)throws IOException{return Files.readString(f.toPath(),java.nio.charset.StandardCharsets.UTF_8);}
 public static long[] counts(File f)throws Exception{JSONObject j=new JSONObject(get(f));return new long[]{j.getLong("tasks"),j.getLong("candidates")};}
 public static void remove(File f){if(f.isDirectory()){File[] a=f.listFiles();if(a!=null)for(File c:a)remove(c);}f.delete();}
}
class TaskRecord {static final String PROCESSING="processing",QUEUED="queued";String status="ready";long id=1;}
class EventCandidate {static final int DOUBTFUL=2,CERTAIN=0;}
class TaskFileAttachment {String storedName,displayName,mimeType;long sizeBytes;}
class TaskStore implements AutoCloseable {final Context c;TaskStore(Context v){c=v;}
 long[] counts(){try{return Fixture.counts(c.getDatabasePath("chrona.db"));}catch(Exception e){throw new IllegalStateException(e);}}
 List<TaskRecord> listTasks(){return Collections.nCopies((int)counts()[0],new TaskRecord());}
 List<EventCandidate> listCandidates(){return Collections.nCopies((int)counts()[1],new EventCandidate());}
 SQLiteDatabase getWritableDatabase(){return new SQLiteDatabase();}public void close(){}
 static void addEndTimeProvenance(SQLiteDatabase d){}static void addScheduleBrowsing(SQLiteDatabase d){}static void addUncertaintyLevel(SQLiteDatabase d){}
 static void addCandidateMerges(SQLiteDatabase d){}static void addTaskSemantics(SQLiteDatabase d){}static void addZjuHomework(SQLiteDatabase d){}
}
class ProcessingJobService {static void enqueue(Context c,long id){}}
class CalendarStore {CalendarStore(Context c){}void deleteEvent(long id){}}
class ThemeStore {
 static String background(Context c){return (String)c.values.get("background");}static boolean backgroundEnabled(Context c){return true;}
 static void setBackground(Context c,String s){c.values.put("background",s);}static void setBackgroundEnabled(Context c,boolean b){}
}
class ConfigBackup {
 static String export(Context c,boolean b){return (String)c.values.getOrDefault("config","old-config");}
 static void apply(Context c,String s)throws Exception{
  c.values.put("config",s);
  if(s.equals("new-config")){
   if(Fixture.failure.equals("missing-rollback")){Files.delete(c.getDatabasePath("chrona.db.previous").toPath());Fixture.hit("missing-rollback");}
   Fixture.hit("config");
  }
 }
}
class ScheduleSources {static String exportJson(Context c){return "{}";}static void importJson(Context c,String s){}}
class SchoolDataBackup {static JSONObject export(Context c)throws Exception{return new JSONObject().put("timetable",true);}static void apply(Context c,JSONObject j){}}
class ZjuSettingsStore {final SharedPreferences prefs=new SharedPreferences();ZjuSettingsStore(Context c){}boolean enabled(){return true;}void options(boolean e,boolean t){}}
class HomeTimelinePreferences {static final int DEFAULT_ITEM_LIMIT=12;
 static int getItemLimit(Context c){return (Integer)c.values.getOrDefault("limit",12);}static boolean includesSystemCalendar(Context c){return false;}
 static void setItemLimit(Context c,int n){c.values.put("limit",n);}static void setIncludesSystemCalendar(Context c,boolean b){}
}
class WidgetPreferences {static int previewMinutes(Context c){return 10;}static boolean danmakuEnabled(Context c){return true;}
 static boolean includesSystemCalendar(Context c){return false;}static void setPreviewMinutes(Context c,int v){}
 static void setDanmakuEnabled(Context c,boolean b){}static void setIncludesSystemCalendar(Context c,boolean b){}
}
class AgendaReminderPreferences {static boolean enabled(Context c){return true;}static boolean includesSystemCalendar(Context c){return false;}
 static void setEnabled(Context c,boolean b){}static void setIncludesSystemCalendar(Context c,boolean b){}static SharedPreferences prefs(Context c){return c.reminders;}}
class FeaturePreferenceBackup {static boolean reminderSetting(String k){return k.equals("setting");}}
class ScheduleOrderPreferences {static boolean oldestFirst(Context c,int tab){return tab==0;}static void setOldestFirst(Context c,int tab,boolean b){}}
class TimetableStore {final Context c;TimetableStore(Context v){c=v;}String snapshot(){return (String)c.values.get("timetable");}void restore(String s){c.values.put("timetable",s);}}
class ClassroomStore {static File root(Context c){return new File(c.getNoBackupFilesDir(),"classroom");}}
class ClassroomDocument {
 static void validate(JSONObject j)throws Exception{j.getJSONObject("summary").getJSONObject("lesson");}
 static String key(JSONObject j)throws Exception{return j.getString("key");}
 static Set<String> referencedImages(JSONObject j){return Collections.emptySet();}
}
class ClassroomArchive {
 static class Entry {final JSONObject document;Entry(JSONObject d){document=d;}}
 static void remove(File f){Fixture.remove(f);}
 static void write(File f,JSONObject d,Map<String,File> images)throws Exception{Fixture.hit("classroom-write");Fixture.put(new File(f,"document.json"),d.toString());}
}
class AgendaReminder {static void requestRefresh(Context c){Fixture.hit("reminder-refresh");}}
class ClassroomCleanupService {static void schedule(Context c){Fixture.hit("classroom-schedule");}}
''')
# No stale generated Java files are included if this harness changes its set of doubles.
sources = [str(SRC / name) for name in (
    "com/donglan/chrona/ChronaDataBackup.java", "android/content/Context.java",
    "android/content/ContentValues.java", "android/content/SharedPreferences.java",
    "android/net/Uri.java", "android/database/Cursor.java", "android/database/sqlite/SQLiteDatabase.java",
    "android/graphics/BitmapFactory.java", "com/donglan/chrona/sync/TaskCodec.java",
    "com/donglan/chrona/zju/ZjuCalendarStore.java", "com/donglan/chrona/Fixture.java",
)]
subprocess.run([str(JAVA / "javac.exe"), "--release", "17", "-encoding", "UTF-8", "-cp", str(JSON),
                "-d", str(OUT), *sources, str(ROOT / "checks/BackupRestoreCheck.java")], check=True)
result = subprocess.run([str(JAVA / "java.exe"), "-Xmx1g", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8", "-cp", str(OUT) + os.pathsep + str(JSON),
                         "com.donglan.chrona.BackupRestoreCheck", str(OUT)], text=True, encoding="utf-8",
                        errors="replace", capture_output=True, timeout=120)
print(result.stdout, end="")
print(result.stderr, end="")
(OUT / "latest-result.txt").write_text(result.stdout + result.stderr, encoding="utf-8")
raise SystemExit(result.returncode)
