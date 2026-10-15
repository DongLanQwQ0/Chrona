"""Execute production reminder preferences against a persistent Android preference double."""
import os
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/AgendaReminderPreferences.java').read_text(encoding='utf-8')
source = re.sub(r'^package .*;\s*|^import .*;\s*', '', source, flags=re.M)
for name in ('CalendarSourcePreferences', 'HomeTimelinePreferences', 'NotificationDeliveryStatus'):
    extra = (root / f'app/src/main/java/com/donglan/chrona/{name}.java').read_text(encoding='utf-8')
    source += '\n' + re.sub(r'^package .*;\s*|^import .*;\s*', '', extra, flags=re.M).replace('public final class', 'final class').replace('android.Manifest.', 'Manifest.')
harness = r'''
import java.util.*;
class Context {
 static final int MODE_PRIVATE=0;static Map<String,SharedPreferences> files=new HashMap<>();
 NotificationManager manager=new NotificationManager();int permission=PackageManager.PERMISSION_GRANTED;
 int checkSelfPermission(String p){return permission;}String getPackageName(){return "com.donglan.chrona";}<T>T getSystemService(Class<T> c){return c.cast(manager);}
 Context getApplicationContext(){return this;}
 SharedPreferences getSharedPreferences(String name,int mode){if(mode!=MODE_PRIVATE)throw new AssertionError("preference mode");return files.computeIfAbsent(name,k->new SharedPreferences());}
}
class SharedPreferences {
 Map<String,Object> values=new HashMap<>();int writes;
 boolean getBoolean(String key,boolean fallback){return (Boolean)values.getOrDefault(key,fallback);}int getInt(String key,int fallback){return (Integer)values.getOrDefault(key,fallback);}Editor edit(){return new Editor();}
 class Editor {Map<String,Object> changes=new HashMap<>();Editor putBoolean(String key,boolean value){changes.put(key,value);return this;}Editor putInt(String key,int value){changes.put(key,value);return this;}void apply(){values.putAll(changes);writes++;}}
}
class NotificationManager {static final int IMPORTANCE_NONE=0;boolean allowed=true;NotificationChannel channel;boolean areNotificationsEnabled(){return allowed;}NotificationChannel getNotificationChannel(String id){if(!id.equals(AgendaReminder.CHANNEL))throw new AssertionError(id);return channel;}}
class NotificationChannel {int importance;NotificationChannel(int importance){this.importance=importance;}int getImportance(){return importance;}}
class Build {static class VERSION {static int SDK_INT=36;}}
class PackageManager {static final int PERMISSION_GRANTED=0;}
class Manifest {static class permission {static final String POST_NOTIFICATIONS="notification";}}
class Settings {static final String ACTION_APP_NOTIFICATION_SETTINGS="app",ACTION_CHANNEL_NOTIFICATION_SETTINGS="channel",EXTRA_APP_PACKAGE="package",EXTRA_CHANNEL_ID="channel_id";}
class Intent {Intent(String action){}Intent putExtra(String key,String value){return this;}}
class AgendaReminder {static final String CHANNEL="upcoming_agenda";static int refreshes;static void requestRefresh(Context c){refreshes++;}}
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
  check(AgendaReminderPreferences.capsuleCountdown(c)&&AgendaReminderPreferences.showSeconds(c),"countdown/seconds defaults");
  for(boolean value:new boolean[]{false,true}){
   AgendaReminderPreferences.setCapsuleCountdown(c,value);check(AgendaReminderPreferences.capsuleCountdown(new Context())==value,"capsule countdown persisted");
   AgendaReminderPreferences.setShowSeconds(c,value);check(AgendaReminderPreferences.showSeconds(new Context())==value,"seconds persisted");
  }
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
  p.edit().putBoolean("enabled",true).putBoolean("include_system",true).putBoolean("atomic_chrona",true).putBoolean("atomic_system",true).apply();
  for(int mask=0;mask<16;mask++){
   boolean scene=(mask&1)!=0,app=(mask&2)!=0,permission=(mask&4)!=0,channel=(mask&8)!=0;
   c.manager.allowed=app;c.permission=permission?PackageManager.PERMISSION_GRANTED:-1;c.manager.channel=new NotificationChannel(channel?4:0);
   Map<String,Object> previous=new HashMap<>(p.values);int writes=p.writes,refreshes=AgendaReminder.refreshes;
   boolean available=scene&&NotificationDeliveryStatus.canPostReminders(c);
   check(AgendaReminderPreferences.useAtomic(c,false,available)==(scene&&app&&permission&&channel),"Chrona atomic includes real delivery prerequisites "+mask);
   check(AgendaReminderPreferences.useAtomic(c,true,available)==(scene&&app&&permission&&channel),"system atomic includes real delivery prerequisites "+mask);
   check(p.values.equals(previous)&&p.writes==writes&&AgendaReminder.refreshes==refreshes,"reading delivery status leaves saved toggles unchanged "+mask);
  }
  c.manager.allowed=true;c.permission=PackageManager.PERMISSION_GRANTED;c.manager.channel=null;
  check(AgendaReminderPreferences.useAtomic(c,false,NotificationDeliveryStatus.canPostReminders(c)),"first real reminder may create its channel without changing source preferences");
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
  System.out.println("Production reminder preferences passed: "+checks+" checks; boolean combinations, notification/channel prerequisites, persistence and independent display settings");
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
