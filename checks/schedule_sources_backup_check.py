"""Exercise production ScheduleSources JSON validation and atomic preference replacement."""
import os
import subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
out=root/'build/schedule-sources-backup-check'
files={
'android/content/SharedPreferences.java':r'''package android.content;import java.util.*;public interface SharedPreferences {Map<String,?> getAll();boolean getBoolean(String k,boolean d);String getString(String k,String d);long getLong(String k,long d);Editor edit();interface Editor {Editor putBoolean(String k,boolean v);Editor putString(String k,String v);Editor putLong(String k,long v);Editor clear();Editor remove(String k);void apply();boolean commit();}}''',
'android/content/Context.java':r'''package android.content;import java.util.*;public class Context {public static final int MODE_PRIVATE=0;public int commits;public final Map<String,Object> values=new HashMap<>();public Context getApplicationContext(){return this;}public SharedPreferences getSharedPreferences(String name,int mode){return new SharedPreferences(){public Map<String,?> getAll(){return new HashMap<>(values);}public boolean getBoolean(String k,boolean d){return values.containsKey(k)?(Boolean)values.get(k):d;}public String getString(String k,String d){return (String)values.getOrDefault(k,d);}public long getLong(String k,long d){return (Long)values.getOrDefault(k,d);}public Editor edit(){return new Editor(){Map<String,Object> pending=new HashMap<>();boolean clear;public Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}public Editor putString(String k,String v){pending.put(k,v);return this;}public Editor putLong(String k,long v){pending.put(k,v);return this;}public Editor clear(){clear=true;return this;}public Editor remove(String k){pending.put(k,null);return this;}public void apply(){commit();}public boolean commit(){commits++;if(clear)values.clear();for(var e:pending.entrySet()){if(e.getValue()==null)values.remove(e.getKey());else values.put(e.getKey(),e.getValue());}return true;}};}};}}''',
'com/donglan/chrona/AgendaWidgetProvider.java':'''package com.donglan.chrona;class AgendaWidgetProvider {static int refreshes;static void requestRefresh(android.content.Context c){refreshes++;}}''',
'com/donglan/chrona/WeekCalendarWidgetProvider.java':'''package com.donglan.chrona;class WeekCalendarWidgetProvider {static void requestRefresh(android.content.Context c){}}''',
'com/donglan/chrona/AgendaReminder.java':'''package com.donglan.chrona;class AgendaReminder {static void requestRefresh(android.content.Context c){}}''',
'com/donglan/chrona/SourcesBackupCheck.java':r'''package com.donglan.chrona;import android.content.Context;import org.json.*;import java.util.*;public class SourcesBackupCheck {
static int count;static void check(boolean ok,String why){count++;if(!ok)throw new AssertionError(why);}
static void reject(Context c,JSONObject json)throws Exception{Map<String,Object> before=new HashMap<>(c.values);int commits=c.commits;try{ScheduleSources.importJson(c,json.toString());throw new AssertionError("accepted invalid input");}catch(JSONException expected){}check(before.equals(c.values)&&commits==c.commits,"invalid snapshot makes no writes");}
public static void main(String[] args)throws Exception{
Context c=new Context();ScheduleSources.setCalendarEnabled(c,42,false);ScheduleSources.setImportedEnabled(c,false);ScheduleSources.setZjuEnabled(c,false);ScheduleSources.setPrimary(c,"system:42");
String pair="imported\n高数\u001fsystem:42\n高等数学";ScheduleSources.setCourseRule(c,pair,"system:42");
JSONObject export=new JSONObject(ScheduleSources.exportJson(c));check(!export.has("revision"),"runtime revision excluded");
Map<String,Object> unchanged=new HashMap<>(c.values);int untouchedCommits=c.commits,untouchedRefreshes=AgendaWidgetProvider.refreshes;
ScheduleSources.validateJson(export.toString());check(c.values.equals(unchanged)&&c.commits==untouchedCommits&&AgendaWidgetProvider.refreshes==untouchedRefreshes,"valid preflight has no writes or refreshes");
try{ScheduleSources.validateJson(new JSONObject(export.toString()).put("imported","false").toString());throw new AssertionError("invalid preflight accepted");}catch(JSONException expected){}
check(c.values.equals(unchanged)&&c.commits==untouchedCommits&&AgendaWidgetProvider.refreshes==untouchedRefreshes,"invalid preflight has no writes or refreshes");
check(!export.getJSONObject("calendars").getBoolean("42")&&export.getString("primary").equals("system:42"),"device local IDs exported unchanged");
ScheduleSources.setCalendarEnabled(c,999,false);ScheduleSources.setCourseRule(c,"imported\n另一门\u001fzju\n另课","reject");long revision=ScheduleSources.revision(c);int commits=c.commits,refreshes=AgendaWidgetProvider.refreshes;
ScheduleSources.importJson(c,export.toString());check(c.commits==commits+1,"one preference commit");check(ScheduleSources.revision(c)==revision+1,"revision advances from live state");check(AgendaWidgetProvider.refreshes==refreshes+1,"refresh after import");check(ScheduleSources.allowsCalendar(c,999),"snapshot replaces stale switches");check(ScheduleSources.courseRules(c).equals(Map.of(pair,"system:42")),"pair map roundtrips and replaces stale rules");check(!ScheduleSources.importedEnabled(c)&&!ScheduleSources.zjuEnabled(c),"source toggles roundtrip");
reject(c,new JSONObject(export.toString()).put("revision",99));reject(c,new JSONObject(export.toString()).put("imported","false"));reject(c,new JSONObject(export.toString()).put("version",2));reject(c,new JSONObject(export.toString()).put("primary","system:0"));
JSONObject bad=new JSONObject(export.toString());bad.getJSONObject("calendars").put("-3",false);reject(c,bad);
bad=new JSONObject(export.toString());bad.getJSONObject("coursePairs").put(pair,"system:999");reject(c,bad);
bad=new JSONObject(export.toString());bad.getJSONObject("coursePairs").put("broken","reject");reject(c,bad);
bad=new JSONObject(export.toString());bad.getJSONObject("coursePairs").put("imported\na\u001fimported\nb","imported");reject(c,bad);
System.out.println("Schedule source backup checks: "+count+" passed");}}
'''}
paths=[]
for name,text in files.items():
    path=out/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8');paths.append(path)
paths.append(root/'app/src/main/java/com/donglan/chrona/ScheduleSources.java')
java=Path(os.environ.get('JAVA_HOME','D:/Minecraft/java21'))/'bin'
cp=str(root/'build/ai-checks/json.jar')
subprocess.run([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-cp',cp,'-d',str(out),*map(str,paths)],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out)+os.pathsep+cp,'com.donglan.chrona.SourcesBackupCheck'],check=True)
