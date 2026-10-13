"""Exercise actual background preference methods with an in-memory preferences double."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/ThemeStore.java').read_text(encoding='utf-8')
methods = []
for signature in ('static String background(', 'static void setBackground(',
                  'static boolean backgroundEnabled(', 'static String activeBackground(',
                  'static void setBackgroundEnabled(', 'static String color(',
                  'static void setColor(', 'private static void publish('):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    methods.append(source[start:end])

harness = r'''
import java.util.*;
class BackgroundPreferenceCheck {
 static final String KEY_BACKGROUND="background", KEY_BACKGROUND_ENABLED="background_enabled";
 static final String KEY_REVISION="revision",KEY_COLOR="color",TEAL="teal";
 static class Context {}
 static class Activity extends Context {int recreates;boolean isFinishing(){return false;}boolean isDestroyed(){return false;}void recreate(){recreates++;}}
 static class AppearanceActivity extends Activity {int repaints;void refreshAppearance(){repaints++;}}
 static class AgendaWidgetProvider {static int updates;static void requestRefresh(Context c){updates++;}}
 static class AgendaReminder {static int updates;static void requestRefresh(Context c){updates++;}}
 static final Object LIVE=new Object();static final Map<Activity,Integer> SEEN=new HashMap<>();
 static class Prefs {
  Map<String,Object> data=new HashMap<>();
  String getString(String k,String d){return (String)data.getOrDefault(k,d);}
  boolean getBoolean(String k,boolean d){return (boolean)data.getOrDefault(k,d);}
  int getInt(String k,int d){return (int)data.getOrDefault(k,d);}
  Prefs edit(){return this;}
  Prefs putString(String k,String v){if(v==null)data.remove(k);else data.put(k,v);return this;}
  Prefs putBoolean(String k,boolean v){data.put(k,v);return this;}
  Prefs putInt(String k,int v){data.put(k,v);return this;}
  void apply(){}
 }
 static class SharedPreferences extends Prefs {}
 static SharedPreferences p=new SharedPreferences(); static int refreshes;
 static SharedPreferences prefs(Context c){return p;}
 static int revision(Context c){return p.getInt(KEY_REVISION,0);}
 static void refreshWallpaper(Context c){refreshes++;}
 METHODS
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  Context c=new Context();check(!backgroundEnabled(c));
  setBackgroundEnabled(c,true);check(activeBackground(c)==null);
  p.putString(KEY_BACKGROUND,"old");check(backgroundEnabled(c));
  setBackgroundEnabled(c,false);check(background(c).equals("old")&&activeBackground(c)==null);
  int before=refreshes;setBackgroundEnabled(c,false);check(refreshes==before);
  setBackgroundEnabled(c,true);check(activeBackground(c).equals("old"));
  setBackgroundEnabled(c,false);setBackground(c,"old");check(backgroundEnabled(c));
  setBackgroundEnabled(c,false);setBackground(c,"new");check(activeBackground(c).equals("new"));
  setBackground(c,null);check(background(c)==null&&!backgroundEnabled(c));
  int reminders=AgendaReminder.updates,widgets=AgendaWidgetProvider.updates;
  setColor(c,"purple");check(AgendaReminder.updates==reminders+1&&AgendaWidgetProvider.updates==widgets+1);
  setColor(c,"purple");check(AgendaReminder.updates==reminders+1);
  AppearanceActivity appearance=new AppearanceActivity();setColor(appearance,"blue");
  check(appearance.repaints==1&&appearance.recreates==0&&SEEN.get(appearance)==revision(appearance));
  Activity other=new Activity();setColor(other,"teal");check(other.recreates==1);
  System.out.println("Background preference checks passed: legacy default, disable retains URI, reenable, repick and reset");
 }
}
'''.replace('METHODS', '\n'.join(methods))
out = root / 'build/background-preference-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'BackgroundPreferenceCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'BackgroundPreferenceCheck'], check=True)
