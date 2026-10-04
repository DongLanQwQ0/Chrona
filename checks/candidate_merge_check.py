"""Execute production TaskStore/CandidateMerges against real SQLite via JVM adapters."""
from pathlib import Path
import os
import subprocess
import textwrap
import shutil

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/candidate-merge-checks'
SRC = OUT / 'src'
SRC.mkdir(parents=True, exist_ok=True)
JAVA = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'

def write(path, text):
    file = SRC / path
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(textwrap.dedent(text), encoding='utf-8')

write('android/content/Context.java', '''
package android.content;
public class Context {
 public java.io.File root;
 public Context(java.io.File root){this.root=root;root.mkdirs();}
 public Context getApplicationContext(){return this;}
 public java.io.File getDatabasePath(String name){java.io.File file=new java.io.File(name);return file.isAbsolute()?file:new java.io.File(root,name);}
 public java.io.File getFilesDir(){return root;}
 public String getPackageName(){return "com.donglan.chrona";}
}
''')
write('android/content/ContentValues.java', '''
package android.content;
public class ContentValues extends java.util.LinkedHashMap<String,Object> {
 public void putNull(String key){put(key,null);}
}
''')
write('android/database/Cursor.java', '''
package android.database;
import org.json.*;
public final class Cursor implements AutoCloseable {
 private final JSONArray rows,columns;private int index=-1;
 public Cursor(JSONObject result){rows=result.getJSONArray("rows");columns=result.getJSONArray("columns");}
 public boolean moveToFirst(){index=0;return index<rows.length();}
 public boolean moveToNext(){return ++index<rows.length();}
 private Object get(int column){return rows.getJSONArray(index).get(column);}
 public String getString(int column){Object value=get(column);return value==JSONObject.NULL?null:value.toString();}
 public long getLong(int column){return ((Number)get(column)).longValue();}
 public int getInt(int column){return ((Number)get(column)).intValue();}
 public boolean isNull(int column){return get(column)==JSONObject.NULL;}
 public int getColumnIndexOrThrow(String name){for(int i=0;i<columns.length();i++)if(columns.getString(i).equals(name))return i;throw new IllegalArgumentException(name);}
 public void close(){}
}
''')
write('android/database/sqlite/SQLiteDatabase.java', '''
package android.database.sqlite;
import android.content.*;import android.database.*;import org.json.*;import java.io.*;import java.util.*;
public final class SQLiteDatabase implements AutoCloseable {
 private Process worker;private BufferedReader input;private BufferedWriter output;private final List<Boolean> transactions=new ArrayList<>();
 public static SQLiteDatabase openOrCreateDatabase(File file,Object ignored){return new SQLiteDatabase(file);}
 public SQLiteDatabase(File file){try{worker=new ProcessBuilder(System.getProperty("python"),System.getProperty("bridge"),file.getAbsolutePath()).start();input=new BufferedReader(new InputStreamReader(worker.getInputStream(),java.nio.charset.StandardCharsets.UTF_8));output=new BufferedWriter(new OutputStreamWriter(worker.getOutputStream(),java.nio.charset.StandardCharsets.UTF_8));}catch(Exception error){throw new IllegalStateException(error);}}
 private JSONObject execute(String sql,Object[] args){try{output.write(new JSONObject().put("sql",sql).put("args",args==null?new JSONArray():new JSONArray(Arrays.asList(args))).toString());output.newLine();output.flush();String line=input.readLine();if(line==null)throw new IllegalStateException("SQLite worker stopped");JSONObject result=new JSONObject(line);if(result.has("error"))throw new IllegalStateException(result.getString("error")+" ["+sql+"]");return result;}catch(IOException error){throw new IllegalStateException(error);}}
 public void execSQL(String sql){execute(sql,null);}
 public void execSQL(String sql,Object[] args){execute(sql,args);}
 public Cursor rawQuery(String sql,String[] args){return new Cursor(execute(sql,args));}
 public Cursor query(String table,String[] columns,String where,String[] args,String group,String having,String order){return query(table,columns,where,args,group,having,order,null);}
 public Cursor query(String table,String[] columns,String where,String[] args,String group,String having,String order,String limit){String sql="SELECT "+(columns==null?"*":String.join(",",columns))+" FROM "+table;if(where!=null)sql+=" WHERE "+where;if(group!=null)sql+=" GROUP BY "+group;if(having!=null)sql+=" HAVING "+having;if(order!=null)sql+=" ORDER BY "+order;if(limit!=null)sql+=" LIMIT "+limit;return rawQuery(sql,args);}
 public long insertOrThrow(String table,String ignored,ContentValues values){String sql="INSERT INTO "+table+" ("+String.join(",",values.keySet())+") VALUES ("+String.join(",",Collections.nCopies(values.size(),"?"))+")";return execute(sql,values.values().toArray()).getLong("id");}
 public int update(String table,ContentValues values,String where,String[] args){List<Object> binds=new ArrayList<>(values.values());if(args!=null)binds.addAll(Arrays.asList(args));List<String> assignments=new ArrayList<>();for(String key:values.keySet())assignments.add(key+"=?");return execute("UPDATE "+table+" SET "+String.join(",",assignments)+(where==null?"":" WHERE "+where),binds.toArray()).getInt("count");}
 public int delete(String table,String where,String[] args){return execute("DELETE FROM "+table+(where==null?"":" WHERE "+where),args).getInt("count");}
 public void beginTransaction(){execSQL(transactions.isEmpty()?"BEGIN IMMEDIATE":"SAVEPOINT nested_"+transactions.size());transactions.add(false);}
 public void setTransactionSuccessful(){transactions.set(transactions.size()-1,true);}
 public void endTransaction(){int depth=transactions.size()-1;boolean success=transactions.remove(depth);if(depth==0)execSQL(success?"COMMIT":"ROLLBACK");else{if(!success)execSQL("ROLLBACK TO nested_"+depth);execSQL("RELEASE nested_"+depth);}}
 public boolean inTransaction(){return !transactions.isEmpty();}
 public void setForeignKeyConstraintsEnabled(boolean enabled){execSQL("PRAGMA foreign_keys="+(enabled?"ON":"OFF"));}
 public void setVersion(int version){execSQL("PRAGMA user_version="+version);}
 public boolean isOpen(){return worker.isAlive();}
 public void close(){try{output.close();worker.waitFor();}catch(Exception error){throw new IllegalStateException(error);}}
}
''')
write('android/database/sqlite/SQLiteOpenHelper.java', '''
package android.database.sqlite;
import android.content.*;import android.database.*;
public abstract class SQLiteOpenHelper implements AutoCloseable {
 private final Context context;private final String name;private final int version;private SQLiteDatabase db;
 public SQLiteOpenHelper(Context context,String name,Object ignored,int version){this.context=context;this.name=name;this.version=version;}
 public SQLiteDatabase getWritableDatabase(){if(db==null){db=SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name),null);onConfigure(db);int old;try(Cursor cursor=db.rawQuery("PRAGMA user_version",null)){cursor.moveToFirst();old=cursor.getInt(0);}if(old==0)onCreate(db);else if(old<version)onUpgrade(db,old,version);db.setVersion(version);onOpen(db);}return db;}
 public SQLiteDatabase getReadableDatabase(){return getWritableDatabase();}
 public void onConfigure(SQLiteDatabase db){}public void onOpen(SQLiteDatabase db){}
 public abstract void onCreate(SQLiteDatabase db);public abstract void onUpgrade(SQLiteDatabase db,int oldVersion,int newVersion);
 public synchronized void close(){if(db!=null){db.close();db=null;}}
}
''')
write('com/donglan/chrona/debug/DiagLog.java', '''package com.donglan.chrona.debug;public final class DiagLog {public static void add(android.content.Context context,String message){}}''')
write('com/donglan/chrona/image/ImageStore.java', '''package com.donglan.chrona.image;public final class ImageStore {private android.content.Context context;public ImageStore(android.content.Context context){this.context=context;}public java.io.File fileFor(String name){return new java.io.File(context.getFilesDir(),name);}public byte[] read(String name){return name.getBytes(java.nio.charset.StandardCharsets.UTF_8);}public static boolean isStoredName(String name){return name!=null&&!name.contains("/")&&!name.contains("\\\\");}}''')
write('android/graphics/BitmapFactory.java', '''package android.graphics;public final class BitmapFactory {public static final class Options{public boolean inJustDecodeBounds;public int outWidth=1,outHeight=1;}public static void decodeByteArray(byte[] bytes,int start,int length,Options options){}}''')
write('android/net/Uri.java', '''package android.net;public final class Uri {private String value;private Uri(String value){this.value=value;}public static Uri parse(String value){return new Uri(value);}public String toString(){return value;}public static class Builder{private String value="";public Builder scheme(String scheme){value=scheme+"://";return this;}public Builder authority(String authority){value+=authority;return this;}public Builder appendPath(String path){value+="/"+path;return this;}public Builder appendQueryParameter(String name,String value){return this;}public Uri build(){return new Uri(value);}}}''')
write('com/donglan/chrona/AgendaWidgetProvider.java', '''package com.donglan.chrona;public final class AgendaWidgetProvider {public static void requestRefresh(android.content.Context context){}}''')
write('com/donglan/chrona/AndroidSync.java', '''package com.donglan.chrona;public final class AndroidSync {static final Object LOCK=new Object();static void request(android.content.Context context,Object listener){}}''')
sync_source = (ROOT / 'app/src/main/java/com/donglan/chrona/AndroidSyncData.java').read_text(encoding='utf-8')
def method(signature):
    start = sync_source.index(signature)
    opening = sync_source.index('{',start)
    depth = 1
    end = opening+1
    while depth:
        depth += (sync_source[end]=='{')-(sync_source[end]=='}')
        end += 1
    return sync_source[start:end]

write('com/donglan/chrona/AndroidSyncData.java', '''
package com.donglan.chrona;
import android.content.*;import android.database.*;import android.database.sqlite.*;
import com.donglan.chrona.data.*;import com.donglan.chrona.sync.*;import com.donglan.chrona.image.*;import android.net.Uri;import java.util.*;import java.io.*;import java.nio.file.Files;import org.json.*;
public final class AndroidSyncData {
 final TaskStore store;final SQLiteDatabase db;String databaseIdentity;final Context context;final File blobs;
 final List<File> stagedImages=new ArrayList<>();final List<Uri> stagedFiles=new ArrayList<>();
 AndroidSyncData(TaskStore store,Context context){this.context=context;this.store=store;db=store.getWritableDatabase();databaseIdentity=value("SELECT identity FROM sync_meta WHERE id=?","1");blobs=context.getFilesDir();}
 private final Map<String,StagedTask> stagedTasks=new HashMap<>();
 void stage(String id,SyncState.Version version)throws Exception{StagedTask staged=new StagedTask();staged.payload=TaskCodec.validate(version.payload);JSONArray values=staged.payload.getJSONArray("candidates");for(int i=0;i<values.length();i++)staged.candidates.add(TaskCodec.event(values.getJSONObject(i)));stagedTasks.put(id,staged);}
''' + '\n'.join(method(signature) for signature in [
    'static String canonical(', 'String value(', 'long localId(', 'String candidateId(',
    'static boolean busy(', 'boolean linked(', 'boolean taskDifferent(',
    'static final class StagedTask', 'static final class Snapshot', 'void requireRevision(',
    'void prepareTaskApply(SyncState state, Snapshot snapshot, String calendarApprovedId, String onlyId)',
    'void applyMergeRecords(', 'void applyTask(', 'private static Map<String, Long> clock(', 'private String changed('
]) + '\n}')
write('com/donglan/chrona/WebDavSettingsStore.java', '''package com.donglan.chrona;public final class WebDavSettingsStore {WebDavSettingsStore(android.content.Context context){}boolean automatic(){return false;}}''')
write('com/donglan/chrona/SyncJobService.java', '''package com.donglan.chrona;public final class SyncJobService {static void schedule(android.content.Context context){}}''')

data = ROOT / 'app/src/main/java/com/donglan/chrona/data'
shared = ROOT / 'shared/src/main/java/com/donglan/chrona/sync'
sources = list(SRC.rglob('*.java')) + [path for path in data.glob('*.java') if path.name != 'TaskFileStore.java']
write('com/donglan/chrona/data/TaskFileStore.java', '''package com.donglan.chrona.data;public final class TaskFileStore {public TaskFileStore(android.content.Context context){}public TaskFileAttachment importFile(android.net.Uri uri){return new TaskFileAttachment(0,0,uri.toString(),"imported","application/pdf",4);}public static boolean isStoredName(String name){return name!=null&&!name.contains("/");}}''')
sources.append(SRC / 'com/donglan/chrona/data/TaskFileStore.java')
sources += list((ROOT / 'app/src/main/java/com/donglan/chrona/timetable').glob('*.java'))
sources += [ROOT / 'app/src/main/java/com/donglan/chrona/CandidateMerges.java']
sources += [shared / name for name in ('TaskCodec.java','MergeLedger.java','SyncState.java')]
sources += [ROOT / 'checks/CandidateMergeCheck.java']
cache = Path(os.environ.get('GRADLE_USER_HOME','F:/Android/GradleCache')) / 'caches/modules-2/files-2.1'
jars = [ROOT / 'build/ai-checks/json.jar']
for group,module in [('net.sf.biweekly','biweekly'),('com.github.mangstadt','vinnie')]:
    jars += sorted((cache / group / module).glob('*/*/*.jar'))[-1:]
classpath = os.pathsep.join(map(str,jars))
subprocess.run([str(JAVA / 'javac.exe'),'--release','17','-encoding','UTF-8','-cp',classpath,'-d',str(OUT),*map(str,sources)],check=True)
subprocess.run([str(JAVA / 'java.exe'),f'-Dpython={shutil.which("python") or "D:/Python/python.exe"}',f'-Dbridge={ROOT / "checks/sqlite_bridge.py"}','-cp',str(OUT)+os.pathsep+classpath,'com.donglan.chrona.CandidateMergeCheck',str(OUT / 'databases')],check=True)
