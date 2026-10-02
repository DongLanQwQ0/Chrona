"""Execute actual widget preferences and extracted backup preference statements.

Android preference storage/refresh and JSON access are test doubles. Backup export,
capture, restore and rollback statements come directly from ChronaDataBackup;
this does not simulate its database, file or calendar restoration transaction.
"""
from pathlib import Path
import os
import re
import subprocess

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/widget-preferences-check'
out.mkdir(parents=True, exist_ok=True)
preferences = (src / 'WidgetPreferences.java').read_text(encoding='utf-8')
preferences = preferences.replace('import android.content.Context;', '')
backup = (src / 'ChronaDataBackup.java').read_text(encoding='utf-8')

def statements(pattern, expected):
    matches = re.findall(pattern, backup)
    assert len(matches) == expected, (pattern, len(matches))
    return '\n'.join(matches)

export = statements(r'manifest\.put\("widget(?:PreviewMinutes|DanmakuEnabled)"[^;]+;', 2)
capture = statements(r'(?:int|boolean) previousWidget\w+ = WidgetPreferences\.[^;]+;', 2)
restore = statements(r'if \(prepared\.manifest\.has\("widget(?:PreviewMinutes|DanmakuEnabled)"\)\) \{[^}]+\}', 2)
rollback = statements(r'WidgetPreferences\.set\w+\(context, previousWidget\w+\);', 2)

harness = r'''
package com.donglan.chrona;
import java.util.*;
class Context {
 static final int MODE_PRIVATE=0;
 final Map<String,Preferences> files;
 Context(){files=new HashMap<>();} Context(Context prior){files=prior.files;}
 Preferences getSharedPreferences(String name,int mode){return files.computeIfAbsent(name,k->new Preferences());}
}
class Preferences {
 final Map<String,Object> values=new HashMap<>();
 int getInt(String key,int fallback){return (int)values.getOrDefault(key,fallback);}
 boolean getBoolean(String key,boolean fallback){return (boolean)values.getOrDefault(key,fallback);}
 Editor edit(){return new Editor();}
 class Editor {
  final Map<String,Object> pending=new HashMap<>();
  Editor putInt(String k,int v){pending.put(k,v);return this;}
  Editor putBoolean(String k,boolean v){pending.put(k,v);return this;}
  void apply(){values.putAll(pending);}
 }
}
class AgendaWidgetProvider {
 static int refreshes;
 static void requestRefresh(Context c){refreshes++;}
}
class JSONObject {
 Map<String,Object> values=new HashMap<>();
 void put(String k,Object v){values.put(k,v);}
 boolean has(String k){return values.containsKey(k);}
 int optInt(String k,int fallback){Object v=values.get(k);return v instanceof Number?((Number)v).intValue():fallback;}
 boolean optBoolean(String k,boolean fallback){Object v=values.get(k);return v instanceof Boolean?(boolean)v:fallback;}
}
class Prepared {JSONObject manifest;Prepared(JSONObject m){manifest=m;}}
public class WidgetPreferencesCheck {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static JSONObject export(Context context){JSONObject manifest=new JSONObject();
 EXPORT
 return manifest;}
 static void restore(Context context,Prepared prepared,boolean fail){
 CAPTURE
 try {
 RESTORE
 if(fail)throw new IllegalStateException("simulated failure after preferences");
 }catch(IllegalStateException expected){
 ROLLBACK
 }
 }
 static void state(Context c,int preview,boolean enabled,String why){
  check(WidgetPreferences.previewMinutes(c)==preview,why+" preview");
  check(WidgetPreferences.danmakuEnabled(c)==enabled,why+" enabled");
 }
 public static void main(String[] args){
  Context c=new Context();state(c,1320,true,"defaults");
  check(AgendaWidgetProvider.refreshes==0,"reads do not refresh");
  WidgetPreferences.setDanmakuEnabled(c,false);
  check(AgendaWidgetProvider.refreshes==1,"toggle requests refresh");
  state(new Context(c),1320,false,"persist across context recreation");
  WidgetPreferences.setDanmakuEnabled(c,true);
  check(AgendaWidgetProvider.refreshes==2,"re-enable requests refresh");
  state(c,1320,true,"toggle on");
  WidgetPreferences.setPreviewMinutes(c,-10);state(c,0,true,"lower clamp");
  WidgetPreferences.setPreviewMinutes(c,2000);state(c,1439,true,"upper clamp");
  WidgetPreferences.setPreviewMinutes(c,73);WidgetPreferences.setDanmakuEnabled(c,false);
  JSONObject saved=export(c);
  check(saved.optInt("widgetPreviewMinutes",-1)==73,"export preview");
  check(!saved.optBoolean("widgetDanmakuEnabled",true),"export disabled");
  Context target=new Context();restore(target,new Prepared(saved),false);
  state(target,73,false,"round trip");
  int before=AgendaWidgetProvider.refreshes;
  restore(target,new Prepared(new JSONObject()),false);
  state(target,73,false,"old backup keeps current settings");
  check(before==AgendaWidgetProvider.refreshes,"absent fields cause no widget refresh");
  JSONObject changed=new JSONObject();changed.put("widgetPreviewMinutes",999);changed.put("widgetDanmakuEnabled",true);
  restore(target,new Prepared(changed),true);state(target,73,false,"failed restore rolls back");
  changed.put("widgetPreviewMinutes",-5);restore(target,new Prepared(changed),false);state(target,0,true,"restore lower clamp");
  changed.put("widgetPreviewMinutes",9000);restore(target,new Prepared(changed),false);state(target,1439,true,"restore upper clamp");
  JSONObject partial=new JSONObject();partial.put("widgetDanmakuEnabled",false);
  restore(target,new Prepared(partial),false);state(target,1439,false,"partial backup preserves preview");
  System.out.println("Widget preference checks passed: "+checks+"; backup preference statements executed, full restore not simulated");
 }
}
'''
for marker, content in [('EXPORT', export), ('CAPTURE', capture), ('RESTORE', restore), ('ROLLBACK', rollback)]:
    harness = harness.replace('\n '+marker+'\n', '\n'+content+'\n')
(out / 'WidgetPreferences.java').write_text(preferences, encoding='utf-8')
(out / 'WidgetPreferencesCheck.java').write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out),
               str(out / 'WidgetPreferences.java'), str(out / 'WidgetPreferencesCheck.java')], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.WidgetPreferencesCheck'], check=True)
