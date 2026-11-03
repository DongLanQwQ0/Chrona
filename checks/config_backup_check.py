"""Exercise real portable configuration logic with in-memory Android preference/store doubles."""
from recurrence_dependencies import recurrence_java
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
directory = root / 'build/config-backup-check'
directory.mkdir(parents=True, exist_ok=True)
fixtures = {
'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences {
 String getString(String key,String fallback); boolean getBoolean(String key,boolean fallback); Editor edit();
 interface Editor { Editor putString(String key,String value); Editor putBoolean(String key,boolean value); Editor clear(); void apply(); }
}''',
'android/content/Context.java': '''package android.content;
import java.util.*;
public class Context {
 public static final int MODE_PRIVATE=0;
 private final Map<String,Prefs> prefs=new HashMap<>();
 public SharedPreferences getSharedPreferences(String name,int mode) { return prefs.computeIfAbsent(name,k->new Prefs()); }
 private static final class Prefs implements SharedPreferences,SharedPreferences.Editor {
  final Map<String,String> values=new HashMap<>();
  public String getString(String key,String fallback) { return values.getOrDefault(key,fallback); }
  public boolean getBoolean(String key,boolean fallback) { return values.containsKey(key)?Boolean.parseBoolean(values.get(key)):fallback; }
  public Editor edit() { return this; }
  public Editor putString(String key,String value) { values.put(key,value); return this; }
  public Editor putBoolean(String key,boolean value) { values.put(key,Boolean.toString(value)); return this; }
  public Editor clear() { values.clear(); return this; } public void apply() { }
 }
}''',
'com/donglan/chrona/ai/AiSettingsStore.java': '''package com.donglan.chrona.ai;
import android.content.Context;import java.util.*;
public final class AiSettingsStore {
 private static final Map<Context,AiSettings> saved=new WeakHashMap<>(); private final Context context;
 public AiSettingsStore(Context context) { this.context=context; }
 public AiSettings load() { return saved.get(context); }
 public void save(String base,String model,String key,String effort) { save(base,model,key,effort,AiSettings.RESPONSES); }
 public void save(String base,String model,String key,String effort,String protocol) { save(base,model,key,effort,protocol,""); }
 public void save(String base,String model,String key,String effort,String protocol,String sessionHeader) { saved.put(context,new AiSettings(base,model,key,effort,protocol,sessionHeader)); }
}''',
'com/donglan/chrona/ThemeStore.java': '''package com.donglan.chrona;
import android.content.Context;
public final class ThemeStore {
 public static final String SYSTEM="system",LIGHT="light",DARK="dark",TEAL="teal",CUSTOM="custom";
 public static String mode(Context c) { return SYSTEM; } public static String color(Context c) { return TEAL; }
 public static String customColorHex(Context c) { return "#123456"; }
 public static boolean acrylicEnabled(Context c) { return false; } public static boolean gaussianBlur(Context c) { return false; }
 public static int surfaceMix(Context c) { return 40; } public static int blurStrength(Context c) { return 2; }
 public static boolean supportedColor(String c) { return TEAL.equals(c)||CUSTOM.equals(c); }
 public static boolean isHexColor(String c) { return c.matches("#[0-9a-fA-F]{6}"); }
 public static int cornerLevel(Context c) { return 4; }
 public static void applyAppearance(Context c,String mode,String color,Boolean acrylic,Boolean gaussian,Integer mix,Integer strength,String custom,Integer corner) { }
}''',
'ConfigBackupCheck.java': '''import android.content.Context;
import com.donglan.chrona.ConfigBackup;
import com.donglan.chrona.ai.*;
import org.json.*;
public final class ConfigBackupCheck {
 public static void main(String[] args) throws Exception {
  Context empty=new Context(), configured=new Context();
  AiSettingsStore saved=new AiSettingsStore(configured);
  saved.save("https://original.test/v1","original-model","local-secret","high",AiSettings.CHAT_COMPLETIONS);
  String emptyExport=ConfigBackup.export(empty,false);
  ConfigBackup.apply(configured,emptyExport);
  check(saved.load().baseUrl.equals("https://original.test/v1")&&saved.load().reasoningEffort.equals("high")
      &&saved.load().protocol.equals(AiSettings.CHAT_COMPLETIONS),"empty export preserves connection and explicit protocol");
  check(new JSONObject(emptyExport).getJSONObject("ai").getString("protocol").equals(AiSettings.RESPONSES),"new configuration defaults Responses");
  check(ConfigBackup.describe(emptyExport).contains("保留本机解析服务设置"),"empty summary matches behavior");
  ConfigBackup.apply(empty,emptyExport);
  check(new AiSettingsStore(empty).load()==null&&ConfigBackup.pending(empty)==null,"empty export on unconfigured device");
  JSONObject root=new JSONObject().put("app","Chrona").put("format",1);
  JSONObject ai=new JSONObject().put("baseUrl","https://new.test/v1").put("model","new-model").put("reasoningEffort","low");
  root.put("ai",ai);
  ConfigBackup.apply(configured,root.toString());
  check(saved.load().model.equals("new-model")&&saved.load().apiKey.equals("local-secret"),"keyless configured import preserves key");
  check(saved.load().protocol.equals(AiSettings.RESPONSES),"old backup missing protocol defaults Responses");
  ConfigBackup.apply(empty,root.toString());
  check(ConfigBackup.pending(empty).model.equals("new-model")&&ConfigBackup.pending(empty).reasoningEffort.equals("low")
      &&ConfigBackup.pending(empty).protocol.equals(AiSettings.RESPONSES),"new device pending fields default Responses");
  for(String protocol:new String[]{AiSettings.RESPONSES,AiSettings.CHAT_COMPLETIONS}) {
   ai.put("protocol",protocol);ConfigBackup.apply(configured,root.toString());ConfigBackup.apply(empty,root.toString());
   check(saved.load().protocol.equals(protocol)&&saved.load().apiKey.equals("local-secret"),"both protocols import retaining key");
   check(ConfigBackup.pending(empty).protocol.equals(protocol),"both protocols survive pending import");
   check(new JSONObject(ConfigBackup.export(configured,false)).getJSONObject("ai").getString("protocol").equals(protocol),"saved protocol export roundtrip");
   check(new JSONObject(ConfigBackup.export(empty,false)).getJSONObject("ai").getString("protocol").equals(protocol),"pending protocol export roundtrip");
   Context keyed=new Context();ConfigBackup.apply(keyed,ConfigBackup.export(configured,true));
   check(new AiSettingsStore(keyed).load().protocol.equals(protocol)
       &&new AiSettingsStore(keyed).load().apiKey.equals("local-secret"),"both protocols keyed backup roundtrip");
  }
  for(Object invalid:new Object[]{"", "chat", "RESPONSES", " responses ", 1, true, JSONObject.NULL, new JSONObject(), new JSONArray()}) {
   ai.put("protocol",invalid);
   try { ConfigBackup.apply(configured,root.toString());throw new AssertionError("invalid protocol accepted"); }
   catch(JSONException expected) { }
   check(saved.load().protocol.equals(AiSettings.CHAT_COMPLETIONS)&&saved.load().apiKey.equals("local-secret"),"invalid protocol leaves configuration intact");
   try { ConfigBackup.apply(empty,root.toString());throw new AssertionError("invalid pending protocol accepted"); }
   catch(JSONException expected) { }
   check(ConfigBackup.pending(empty).protocol.equals(AiSettings.CHAT_COMPLETIONS),"invalid protocol preserves pending configuration");
  }
  ai.put("protocol",AiSettings.CHAT_COMPLETIONS);
  ai.put("sessionHeader","X-Workflow-Session");ConfigBackup.apply(configured,root.toString());ConfigBackup.apply(empty,root.toString());
  check(saved.load().sessionHeader.equals("X-Workflow-Session"),"session header saved without losing local key");
  check(ConfigBackup.pending(empty).sessionHeader.equals("X-Workflow-Session"),"session header kept for keyless new-device import");
  check(new JSONObject(ConfigBackup.export(configured,false)).getJSONObject("ai").getString("sessionHeader").equals("X-Workflow-Session"),"session header portable without key");
  for(Object bad:new Object[]{"Authorization","Expect","Content-Encoding","Upgrade","bad header",12}){
   JSONObject invalid=new JSONObject(root.toString());invalid.getJSONObject("ai").put("sessionHeader",bad);
   try{ConfigBackup.apply(configured,invalid.toString());throw new AssertionError("invalid session header accepted");}catch(org.json.JSONException expected){}
   check(saved.load().sessionHeader.equals("X-Workflow-Session"),"invalid import leaves connection untouched");
  }
  ai.put("model","");
  try { ConfigBackup.inspect(root.toString());throw new AssertionError("half-empty accepted"); }
  catch(JSONException expected) { }
  ai.put("model","new-model").put("apiKey","new-secret");
  ConfigBackup.apply(configured,root.toString());
  check(saved.load().apiKey.equals("new-secret"),"explicit key replacement");
  configured.getSharedPreferences("agenda_reminder",0).edit().putBoolean("atomic_seconds",false).putBoolean("hidden:private",true).apply();
  configured.getSharedPreferences("home_timeline",0).edit().putBoolean("todos_expanded",true).apply();
  configured.getSharedPreferences("CalendarOverviewActivity",0).edit().putBoolean("week_export_index",true).apply();
  String portable=ConfigBackup.export(configured,false);
  check(!portable.contains("hidden:private")&&!portable.contains("new-secret"),"runtime and secrets excluded");
  ConfigBackup.apply(empty,portable);
  check(new AiSettingsStore(empty).load()==null&&ConfigBackup.pending(empty).protocol.equals(AiSettings.CHAT_COMPLETIONS),"keyless portable protocol retained in pending import");
  Context withKey=new Context();ConfigBackup.apply(withKey,ConfigBackup.export(configured,true));
  check(new AiSettingsStore(withKey).load().protocol.equals(AiSettings.CHAT_COMPLETIONS)
      &&new AiSettingsStore(withKey).load().apiKey.equals("new-secret"),"keyed backup restores protocol and credentials together");
  check(!empty.getSharedPreferences("agenda_reminder",0).getBoolean("atomic_seconds",true),"seconds restored");
  check(empty.getSharedPreferences("home_timeline",0).getBoolean("todos_expanded",false),"expansion restored");
  check(empty.getSharedPreferences("CalendarOverviewActivity",0).getBoolean("week_export_index",false),"export restored");
  JSONObject broken=new JSONObject(portable); broken.getJSONObject("features").getJSONObject("agenda_reminder").put("atomic_seconds","yes");
  try { ConfigBackup.apply(empty,broken.toString());throw new AssertionError("invalid switch accepted"); } catch(JSONException expected) { }
  System.out.println("ConfigBackupCheck passed: both protocols keyed/keyless/pending roundtrips, missing protocol defaults Responses, strict protocol rejection without writes, empty AI export, retained/replaced key and incomplete configuration rejection");
 }
 static void check(boolean value,String label) { if(!value)throw new AssertionError(label); }
}'''
}
paths=[]
for relative, content in fixtures.items():
    path=directory/relative
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(content,encoding='utf-8')
    paths.append(str(path))
java=Path(os.environ.get('JAVA_HOME','D:/Minecraft/java21'))/'bin'
jar=root/'build/ai-checks/json.jar'
subprocess.run(recurrence_java([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-cp',str(jar),'-d',str(directory),
               *paths,str(root/'app/src/main/java/com/donglan/chrona/ConfigBackup.java'),
               str(root/'app/src/main/java/com/donglan/chrona/FeaturePreferenceBackup.java'),
               str(root/'app/src/main/java/com/donglan/chrona/ai/SchedulePreferences.java'),
               str(root/'app/src/main/java/com/donglan/chrona/ai/SchedulePrompt.java'),
               str(root/'app/src/main/java/com/donglan/chrona/ai/TimeAssessment.java'),
               str(root/'app/src/main/java/com/donglan/chrona/ai/SemanticEventNormalizer.java'),
               str(root/'app/src/main/java/com/donglan/chrona/data/EventTimeDefaults.java'),
               str(root/'app/src/main/java/com/donglan/chrona/calendar/AllDayDates.java'),
               str(root/'checks/SchedulePreferencesCheck.java'),
               str(root/'app/src/main/java/com/donglan/chrona/ai/AiSettings.java')]),check=True)
subprocess.run(recurrence_java([str(java/'java.exe'),'-cp',str(directory)+os.pathsep+str(jar),'ConfigBackupCheck']),check=True)
subprocess.run(recurrence_java([str(java/'java.exe'),'-cp',str(directory)+os.pathsep+str(jar),'SchedulePreferencesCheck']),check=True)
