"""Exercise production refresh and slot assignment using Android transport doubles."""
import os
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
source = (src / 'AgendaReminder.java').read_text(encoding='utf-8')
start = source.index('    private static void refresh(')
end = source.index('{', start) + 1
depth = 1
while depth:
    depth += (source[end] == '{') - (source[end] == '}')
    end += 1
refresh = source[start:end]
pure = '\n'.join((src / f'{name}.java').read_text(encoding='utf-8').replace('package com.donglan.chrona;', '').replace('import java.util.*;', '').replace('import java.util.List;', '') for name in ('AgendaEntry', 'ReminderTimeline', 'ReminderSlots'))
harness = r'''
import java.util.*;import java.time.*;
class Context {SharedPreferences prefs=new SharedPreferences();NotificationManager manager=new NotificationManager();<T>T getSystemService(Class<T> type){return type.cast(manager);}}
class Intent {String key,action;Intent(String key,String action){this.key=key;this.action=action;}String getStringExtra(String k){return key;}String getAction(){return action;}}
class SharedPreferences {
 Map<String,Object> values=new HashMap<>();String getString(String k,String d){return (String)values.getOrDefault(k,d);}long getLong(String k,long d){return (Long)values.getOrDefault(k,d);}Map<String,?> getAll(){return new HashMap<>(values);}Editor edit(){return new Editor();}
 class Editor {Map<String,Object> additions=new HashMap<>();Set<String> removed=new HashSet<>();Editor putString(String k,String v){additions.put(k,v);return this;}Editor putLong(String k,long v){additions.put(k,v);return this;}Editor remove(String k){removed.add(k);return this;}void apply(){for(String k:removed)values.remove(k);values.putAll(additions);}}
}
class AgendaReminderPreferences {static boolean enabled=true;static SharedPreferences prefs(Context c){return c.prefs;}static boolean enabled(Context c){return enabled;}static boolean includesSystemCalendar(Context c){return false;}}
class NotificationManager {static final int IMPORTANCE_HIGH=4;boolean allowed=true;Map<Integer,String> cards=new HashMap<>();boolean areNotificationsEnabled(){return allowed;}void createNotificationChannel(NotificationChannel c){}void notify(int id,String key){cards.put(id,key);}}
class NotificationChannel {NotificationChannel(String id,String name,int importance){}void setDescription(String s){}}
class VivoAtomicNotification {static boolean vivo=true;static List<Integer> ended=new ArrayList<>();static boolean supportedManufacturer(){return vivo;}static void end(Context c,NotificationManager m,int id){ended.add(id);m.cards.remove(id);}}
class EventCandidate {long id,taskId,startAtMillis;Long endAtMillis;String title="title",location="",description="";EventCandidate(long id,long start,long end){this.id=id;this.startAtMillis=start;this.endAtMillis=end;this.taskId=1;}String key(){return "chrona:"+id+":"+startAtMillis;}}
class TaskStore implements AutoCloseable {static List<EventCandidate> rows=new ArrayList<>();TaskStore(Context c){}List<EventCandidate> nextReminderCandidates(long now,Set<String> hidden){List<EventCandidate> r=new ArrayList<>();for(EventCandidate e:rows)if(!hidden.contains(e.key()))r.add(e);return r;}List<Long> linkedCalendarIds(){return List.of();}public void close(){}}
class CalendarOccurrence {long eventId,startMillis,endMillis;String title,location,calendarName;boolean allDay;}
class CalendarStore {CalendarStore(Context c){}boolean hasReadPermission(){return false;}List<CalendarOccurrence> reminderInstances(long n,long h,Set<Long> linked,Set<String> hidden){return List.of();}}
__PURE__
public class AgendaSlotsCheck {
 static final String CHANNEL="upcoming_agenda";static final int NOTIFICATION_ID=0x434852;
 static long alarm;static int checks;
 static void schedule(Context c,long at){alarm=at;}
 static String notification(Context c,AgendaEntry e,long now,boolean same,int id){return e.key();}
 __REFRESH__
 static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
 static String slot(Context c,int i){return c.prefs.getString("slot_key:"+i,"");}
 public static void main(String[] args){
  long now=System.currentTimeMillis();Context c=new Context();
  EventCandidate a=new EventCandidate(1,now+600000,now+3600000),b=new EventCandidate(2,now+700000,now+3600000),d=new EventCandidate(3,now+800000,now+3600000),e=new EventCandidate(4,now+900000,now+3600000);
  TaskStore.rows=new ArrayList<>(List.of(a,b,d,e));refresh(c,null);
  check(c.manager.cards.size()==3,"vivo three-card cap");check(slot(c,0).equals(a.key())&&slot(c,1).equals(b.key())&&slot(c,2).equals(d.key()),"initial slot identities");
  check(alarm<=System.currentTimeMillis()+ReminderTimeline.PROGRESS_REFRESH,"vivo progress refresh");
  VivoAtomicNotification.ended.clear();Collections.reverse(TaskStore.rows);refresh(c,null);
  check(VivoAtomicNotification.ended.isEmpty()&&slot(c,0).equals(a.key())&&slot(c,1).equals(b.key())&&slot(c,2).equals(d.key()),"reordering does not end retained cards");
  VivoAtomicNotification.ended.clear();refresh(c,new Intent(a.key(),"snooze"));
  check(slot(c,0).equals(e.key())&&slot(c,1).equals(b.key())&&slot(c,2).equals(d.key()),"deferred occurrence frees only its slot");
  check(VivoAtomicNotification.ended.equals(List.of(NOTIFICATION_ID)),"operation2 only on replaced slot");
  check(c.prefs.getLong("snooze_until:"+a.key(),0)>now,"per-occurrence snooze saved");
  refresh(c,new Intent(b.key(),"snooze"));check(c.prefs.getLong("snooze_until:"+a.key(),0)>now&&c.prefs.getLong("snooze_until:"+b.key(),0)>now,"two independent snoozes survive");
  check(c.manager.cards.size()==2,"two deferred occurrences leave remaining cards");
  refresh(c,new Intent(a.key(),"hide"));check(!c.prefs.values.containsKey("hidden:"+a.key())&&!c.prefs.values.containsKey("hidden:"+e.key()),"stale action cannot hide old or replacement");
  c.prefs.edit().putLong("snooze_until:"+a.key(),now-1).apply();refresh(c,null);
  check(!c.prefs.values.containsKey("snooze_until:"+a.key())&&c.manager.cards.containsValue(a.key()),"expired snooze cleans up and returns occurrence");
  refresh(c,new Intent(d.key(),"hide"));check(c.prefs.getLong("hidden:"+d.key(),0)==d.endAtMillis&&!c.manager.cards.containsValue(d.key()),"hide one occurrence only");
  EventCandidate changed=new EventCandidate(3,d.startAtMillis+1,d.endAtMillis);TaskStore.rows.add(changed);refresh(c,null);
  check(c.manager.cards.containsValue(changed.key()),"hidden previous key does not hide edited occurrence");
  c.manager.allowed=false;VivoAtomicNotification.ended.clear();refresh(c,null);
  check(c.manager.cards.isEmpty()&&VivoAtomicNotification.ended.size()==3,"permission disabled ends all slots");
  for(int i=0;i<3;i++)check(slot(c,i).isEmpty(),"permission disabled clears slot key");
  c.manager.allowed=true;refresh(c,null);AgendaReminderPreferences.enabled=false;VivoAtomicNotification.ended.clear();refresh(c,null);
  check(c.manager.cards.isEmpty()&&alarm==0&&VivoAtomicNotification.ended.size()==3,"disabled ends all slots and cancels alarm");
  AgendaReminderPreferences.enabled=true;VivoAtomicNotification.vivo=false;refresh(c,null);
  check(c.manager.cards.size()==1&&alarm>System.currentTimeMillis()+ReminderTimeline.PROGRESS_REFRESH,"ordinary platform single card boundary refresh");
  c.prefs.edit().putLong("slot_end:0",now-1).apply();refresh(c,new Intent(a.key(),"hide"));
  check(!c.prefs.values.containsKey("hidden:"+a.key()),"expired card action rejected before refresh");
  for(EventCandidate row:TaskStore.rows){row.startAtMillis=now-120000;row.endAtMillis=now-1;}refresh(c,null);
  check(c.manager.cards.isEmpty(),"ended occurrences end notifications");
  TaskStore.rows=List.of();refresh(c,null);check(c.manager.cards.isEmpty(),"empty agenda ends remaining notification");
  System.out.println("Production reminder refresh passed: "+checks+" checks; stable slots, stale actions, independent snooze/hide, permission, disabled and platform limits");
 }
}
'''.replace('__PURE__', pure).replace('__REFRESH__', refresh)
out = root / 'build/agenda-slots-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'AgendaSlotsCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'AgendaSlotsCheck'], check=True)
