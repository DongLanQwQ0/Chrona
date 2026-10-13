"""Execute production reminder preferences against a persistent Android preference double."""
import os
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/AgendaReminderPreferences.java').read_text(encoding='utf-8')
source = re.sub(r'^package .*;\s*|^import .*;\s*', '', source, flags=re.M)
for name in ('CalendarSourcePreferences', 'HomeTimelinePreferences'):
    extra = (root / f'app/src/main/java/com/donglan/chrona/{name}.java').read_text(encoding='utf-8')
    source += '\n' + re.sub(r'^package .*;\s*|^import .*;\s*', '', extra, flags=re.M).replace('public final class', 'final class')
harness = r'''
import java.util.*;
class Context {
 static final int MODE_PRIVATE=0;static Map<String,SharedPreferences> files=new HashMap<>();
 Context getApplicationContext(){return this;}
 SharedPreferences getSharedPreferences(String name,int mode){if(mode!=MODE_PRIVATE)throw new AssertionError("preference mode");return files.computeIfAbsent(name,k->new SharedPreferences());}
}
class SharedPreferences {
 Map<String,Object> values=new HashMap<>();int writes;
 boolean getBoolean(String key,boolean fallback){return (Boolean)values.getOrDefault(key,fallback);}int getInt(String key,int fallback){return (Integer)values.getOrDefault(key,fallback);}Editor edit(){return new Editor();}
 class Editor {Map<String,Object> changes=new HashMap<>();Editor putBoolean(String key,boolean value){changes.put(key,value);return this;}Editor putInt(String key,int value){changes.put(key,value);return this;}void apply(){values.putAll(changes);writes++;}}
}
class AgendaReminder {static int refreshes;static void requestRefresh(Context c){refreshes++;}}
class AgendaReminderJobService {static int schedules;static void schedule(Context c){schedules++;}}
__SOURCE__
public class ReminderPreferencesCheck {
 static int checks;static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 public static void main(String[] args){
  Context c=new Context();SharedPreferences p=AgendaReminderPreferences.prefs(c);
  check(AgendaReminderPreferences.enabled(c),"master enabled default");
  check(!AgendaReminderPreferences.includesSystemCalendar(c),"system inclusion default disabled");
  check(AgendaReminderPreferences.atomicChrona(c)&&AgendaReminderPreferences.atomicSystem(c),"both atomic source defaults enabled");
  check(AgendaReminderPreferences.atomicWallpaper(c),"wallpaper background enabled default");
  for(boolean value:new boolean[]{false,true,false}){
   int refreshes=AgendaReminder.refreshes,writes=p.writes;
   Map<String,Object> previous=new HashMap<>(p.values);
   AgendaReminderPreferences.setAtomicWallpaper(c,value);
   check(AgendaReminderPreferences.atomicWallpaper(new Context())==value,"wallpaper preference persists");
   check(AgendaReminder.refreshes==refreshes+1&&p.writes==writes+1,"wallpaper setter refreshes notification");
   previous.put("atomic_wallpaper",value);
   check(p.values.equals(previous),"wallpaper switch leaves reminder source and master preferences unchanged");
  }
  for(int mask=0;mask<64;mask++){
   boolean enabled=(mask&1)!=0,include=(mask&2)!=0,chrona=(mask&4)!=0,systemAtomic=(mask&8)!=0,available=(mask&16)!=0,system=(mask&32)!=0;
   p.edit().putBoolean("enabled",enabled).putBoolean("include_system",include).putBoolean("atomic_chrona",chrona).putBoolean("atomic_system",systemAtomic).apply();
   check(AgendaReminderPreferences.enabled(c)==enabled&&AgendaReminderPreferences.includesSystemCalendar(c)==include&&AgendaReminderPreferences.atomicChrona(c)==chrona&&AgendaReminderPreferences.atomicSystem(c)==systemAtomic,"production getters mask "+mask);
   check(AgendaReminderPreferences.useAtomic(c,system,available)==(enabled&&available&&(!system||include)&&(system?systemAtomic:chrona)),"useAtomic truth table mask "+mask);
  }
  for(boolean value:new boolean[]{false,true,false,true}){
   int refreshes=AgendaReminder.refreshes,schedules=AgendaReminderJobService.schedules,writes=p.writes;
   AgendaReminderPreferences.setAtomicChrona(c,value);
   check(AgendaReminderPreferences.atomicChrona(new Context())==value&&p.values.get("atomic_chrona").equals(value),"chrona preference persisted");
   check(p.writes==writes+1&&AgendaReminder.refreshes==refreshes+1&&AgendaReminderJobService.schedules==schedules,"chrona setter refresh exactly once");
   refreshes=AgendaReminder.refreshes;writes=p.writes;AgendaReminderPreferences.setAtomicSystem(c,value);
   check(AgendaReminderPreferences.atomicSystem(new Context())==value&&p.values.get("atomic_system").equals(value),"system preference persisted");
   check(p.writes==writes+1&&AgendaReminder.refreshes==refreshes+1&&AgendaReminderJobService.schedules==schedules,"system atomic setter refresh exactly once");
   refreshes=AgendaReminder.refreshes;writes=p.writes;AgendaReminderPreferences.setIncludesSystemCalendar(c,value);
   check(AgendaReminderPreferences.includesSystemCalendar(new Context())==value&&p.values.get("include_system").equals(value),"system inclusion persisted");
   check(p.writes==writes+1&&AgendaReminder.refreshes==refreshes+1&&AgendaReminderJobService.schedules==schedules,"inclusion setter refresh exactly once");
   refreshes=AgendaReminder.refreshes;writes=p.writes;AgendaReminderPreferences.setEnabled(c,value);
   check(AgendaReminderPreferences.enabled(new Context())==value&&p.values.get("enabled").equals(value),"master preference persisted");
   check(p.writes==writes+1&&AgendaReminder.refreshes==refreshes+1&&AgendaReminderJobService.schedules==schedules+1,"master setter schedules and refreshes exactly once");
  }
  AgendaReminderPreferences.setIncludesSystemCalendar(c,false);
  check(!AgendaReminderPreferences.useAtomic(c,true,true)&&AgendaReminderPreferences.useAtomic(c,false,true),"disabled system inclusion takes priority over enabled atomic system setting");
  AgendaReminderPreferences.setEnabled(c,false);AgendaReminderPreferences.setIncludesSystemCalendar(c,true);
  check(!AgendaReminderPreferences.useAtomic(c,false,true)&&!AgendaReminderPreferences.useAtomic(c,true,true),"master disabled takes priority over atomic settings");
  check(!CalendarSourcePreferences.includesSystem(c)&&!HomeTimelinePreferences.includesSystemCalendar(c),"calendar and home display default off");
  SharedPreferences calendar=c.getSharedPreferences("CalendarOverviewActivity",Context.MODE_PRIVATE),home=c.getSharedPreferences("home_timeline",Context.MODE_PRIVATE);
  for(boolean value:new boolean[]{true,false,true}){
   AgendaReminderPreferences.setIncludesSystemCalendar(c,!value);
   int refreshes=AgendaReminder.refreshes;Map<String,Object> reminderBefore=new HashMap<>(p.values);
   CalendarSourcePreferences.setIncludesSystem(c,value);
   check(calendar.getBoolean("system",!value)==value&&CalendarSourcePreferences.includesSystem(new Context())==value,"calendar helper writes activity namespace");
   calendar.edit().putBoolean("system",!value).apply();
   check(CalendarSourcePreferences.includesSystem(c)==!value,"calendar helper reads activity writes");
   HomeTimelinePreferences.setIncludesSystemCalendar(c,value);
   check(home.getBoolean("include_system_calendar",!value)==value&&HomeTimelinePreferences.includesSystemCalendar(new Context())==value,"home helper writes home namespace");
   check(CalendarSourcePreferences.includesSystem(c)==!value,"home write leaves calendar display independent");
   home.edit().putBoolean("include_system_calendar",!value).apply();
   check(HomeTimelinePreferences.includesSystemCalendar(c)==!value,"home helper reads home writes");
   CalendarSourcePreferences.setIncludesSystem(c,value);
   check(HomeTimelinePreferences.includesSystemCalendar(c)==!value,"calendar write leaves home display independent");
   check(p.values.equals(reminderBefore)&&AgendaReminder.refreshes==refreshes,"display changes do not alter reminder preferences or refresh notifications");
  }
  check(HomeTimelinePreferences.getItemLimit(c)==HomeTimelinePreferences.DEFAULT_ITEM_LIMIT,"home item limit default");
  for(int requested:new int[]{-1,5,20,50,100}){HomeTimelinePreferences.setItemLimit(c,requested);check(HomeTimelinePreferences.getItemLimit(new Context())==Math.max(5,Math.min(50,requested)),"home item limit persisted and bounded");}
  System.out.println("Production reminder preferences passed: "+checks+" checks; all 64 boolean combinations, defaults, persistence and setter refresh/schedule");
 }
}
'''.replace('__SOURCE__', source)
out = root / 'build/reminder-preferences-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'ReminderPreferencesCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'ReminderPreferencesCheck'], check=True)
