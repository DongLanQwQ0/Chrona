"""Exercise actual background preference methods with an in-memory preferences double."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/ThemeStore.java').read_text(encoding='utf-8')
methods = []
for signature in ('static String background(', 'static void setBackground(',
                  'static boolean backgroundEnabled(', 'static String activeBackground(',
                  'static void setBackgroundEnabled('):
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
 static class Context {}
 static class Prefs {
  Map<String,Object> data=new HashMap<>();
  String getString(String k,String d){return (String)data.getOrDefault(k,d);}
  boolean getBoolean(String k,boolean d){return (boolean)data.getOrDefault(k,d);}
  Prefs edit(){return this;}
  Prefs putString(String k,String v){if(v==null)data.remove(k);else data.put(k,v);return this;}
  Prefs putBoolean(String k,boolean v){data.put(k,v);return this;}
  void apply(){}
 }
 static Prefs p=new Prefs(); static int refreshes;
 static Prefs prefs(Context c){return p;}
 static void refreshWallpaper(Context c){refreshes++;}
 static void publish(Context c){}
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
