"""Exercise real portable configuration logic with in-memory Android preference/store doubles."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
directory = root / 'build/config-backup-check'
directory.mkdir(parents=True, exist_ok=True)
fixtures = {
'android/content/SharedPreferences.java': '''package android.content;
public interface SharedPreferences {
 String getString(String key,String fallback); Editor edit();
 interface Editor { Editor putString(String key,String value); Editor clear(); void apply(); }
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
  public Editor edit() { return this; }
  public Editor putString(String key,String value) { values.put(key,value); return this; }
  public Editor clear() { values.clear(); return this; } public void apply() { }
 }
}''',
'com/donglan/chrona/ai/AiSettingsStore.java': '''package com.donglan.chrona.ai;
import android.content.Context;import java.util.*;
public final class AiSettingsStore {
 private static final Map<Context,AiSettings> saved=new WeakHashMap<>(); private final Context context;
 public AiSettingsStore(Context context) { this.context=context; }
 public AiSettings load() { return saved.get(context); }
 public void save(String base,String model,String key,String effort) { saved.put(context,new AiSettings(base,model,key,effort)); }
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
 public static void applyAppearance(Context c,String mode,String color,Boolean acrylic,Boolean gaussian,Integer mix,Integer strength,String custom) { }
}''',
'ConfigBackupCheck.java': '''import android.content.Context;
import com.donglan.chrona.ConfigBackup;
import com.donglan.chrona.ai.*;
import org.json.*;
public final class ConfigBackupCheck {
 public static void main(String[] args) throws Exception {
  Context empty=new Context(), configured=new Context();
  AiSettingsStore saved=new AiSettingsStore(configured);
  saved.save("https://original.test/v1","original-model","local-secret","high");
  String emptyExport=ConfigBackup.export(empty,false);
  ConfigBackup.apply(configured,emptyExport);
  check(saved.load().baseUrl.equals("https://original.test/v1")&&saved.load().reasoningEffort.equals("high"),"empty export preserves connection");
  check(ConfigBackup.describe(emptyExport).contains("保留本机解析服务设置"),"empty summary matches behavior");
  ConfigBackup.apply(empty,emptyExport);
  check(new AiSettingsStore(empty).load()==null&&ConfigBackup.pending(empty)==null,"empty export on unconfigured device");
  JSONObject root=new JSONObject().put("app","Chrona").put("format",1);
  JSONObject ai=new JSONObject().put("baseUrl","https://new.test/v1").put("model","new-model").put("reasoningEffort","low");
  root.put("ai",ai);
  ConfigBackup.apply(configured,root.toString());
  check(saved.load().model.equals("new-model")&&saved.load().apiKey.equals("local-secret"),"keyless configured import preserves key");
  ConfigBackup.apply(empty,root.toString());
  check(ConfigBackup.pending(empty).model.equals("new-model")&&ConfigBackup.pending(empty).reasoningEffort.equals("low"),"new device pending fields");
  ai.put("model","");
  try { ConfigBackup.inspect(root.toString());throw new AssertionError("half-empty accepted"); }
  catch(JSONException expected) { }
  ai.put("model","new-model").put("apiKey","new-secret");
  ConfigBackup.apply(configured,root.toString());
  check(saved.load().apiKey.equals("new-secret"),"explicit key replacement");
  System.out.println("ConfigBackupCheck passed: empty AI export on both devices, truthful summary, retained/replaced key, keyless pending configuration and incomplete configuration rejection");
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
subprocess.run([str(java/'javac.exe'),'--release','17','-cp',str(jar),'-d',str(directory),
               *paths,str(root/'app/src/main/java/com/donglan/chrona/ConfigBackup.java'),
               str(root/'app/src/main/java/com/donglan/chrona/ai/AiSettings.java')],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(directory)+os.pathsep+str(jar),'ConfigBackupCheck'],check=True)
