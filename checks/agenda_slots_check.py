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
class AgendaReminderPreferences {static boolean enabled=true,atomic=true;static SharedPreferences prefs(Context c){return c.prefs;}static boolean enabled(Context c){return enabled;}static boolean includesSystemCalendar(Context c){return false;}static boolean useAtomic(Context c,boolean system,boolean available){return enabled&&available&&atomic;}}
class NotificationManager {static final int IMPORTANCE_HIGH=4;boolean allowed=true;Map<Integer,String> cards=new HashMap<>();Map<Integer,Boolean> atomic=new HashMap<>();List<String> events=new ArrayList<>();boolean areNotificationsEnabled(){return allowed;}void createNotificationChannel(NotificationChannel c){}void notify(int id,String key){events.add("notify:"+id);cards.put(id,key);}}
class Notification {static final int VISIBILITY_PUBLIC=1;}
class NotificationChannel {NotificationChannel(String id,String name,int importance){}void setDescription(String s){}void setLockscreenVisibility(int visibility){if(visibility!=Notification.VISIBILITY_PUBLIC)throw new AssertionError("reminder channel must be lockscreen visible");}}
class VivoAtomicNotification {static boolean vivo=true;static List<Integer> ended=new ArrayList<>();static boolean available(Context c){return vivo&&c.manager.allowed;}static boolean active(NotificationManager m,int id){return m.atomic.getOrDefault(id,false);}static void end(Context c,NotificationManager m,int id){ended.add(id);m.events.add("end:"+id);m.cards.remove(id);m.atomic.remove(id);}}
class EventCandidate {long id,taskId,startAtMillis;Long endAtMillis;Integer reminderMinutesBefore;String title="title",location="",description="",category="event";EventCandidate(long id,long start,long end){this.id=id;this.startAtMillis=start;this.endAtMillis=end;this.taskId=1;}String key(){return "chrona:"+id+":"+startAtMillis;}}
class TaskStore implements AutoCloseable {static List<EventCandidate> rows=new ArrayList<>();TaskStore(Context c){}List<EventCandidate> nextReminderCandidates(long now,Set<String> hidden){List<EventCandidate> r=new ArrayList<>();for(EventCandidate e:rows)if(!hidden.contains(e.key()))r.add(e);return r;}List<Long> linkedCalendarIds(){return List.of();}public void close(){}}
class CalendarOccurrence {long eventId,startMillis,endMillis;String title,location,calendarName;boolean allDay;}
class CalendarStore {CalendarStore(Context c){}boolean hasReadPermission(){return false;}List<CalendarOccurrence> reminderInstances(long n,long h,Set<Long> linked,Set<String> hidden){return List.of();}}
__PURE__
public class AgendaSlotsCheck {
 static final String CHANNEL="upcoming_agenda";static final int NOTIFICATION_ID=0x434852;
 static long alarm;static int checks;
 static void schedule(Context c,long at){alarm=at;}
 static Map<Integer,Boolean> updates=new HashMap<>();static Map<Integer,Long> notified=new HashMap<>();static Map<Integer,String> nextTitles=new HashMap<>(),categories=new HashMap<>();static String notification(Context c,AgendaEntry e,long now,boolean same,int id,long notifiedAt,String nextTitle,boolean atomic){updates.put(id,same);notified.put(id,notifiedAt);nextTitles.put(id,nextTitle);categories.put(id,e.category);c.manager.atomic.put(id,atomic);return e.key();}
 __REFRESH__
 static void check(boolean condition,String reason){checks++;if(!condition)throw new AssertionError(reason);}
 static String slot(Context c,int i){return c.prefs.getString("slot_key:"+i,"");}
 public static void main(String[] args){
  long now=System.currentTimeMillis();Context c=new Context();
  AgendaEntry current=new AgendaEntry(90,1,now+1000,now+2000,"current","","","",false,false,true);
  AgendaEntry early=new AgendaEntry(91,1,now+3000,now+4000,"early","","","",false,false,true);
  AgendaEntry tie=new AgendaEntry(92,1,early.start,early.end,"tie","","","",false,false,true);
  AgendaEntry later=new AgendaEntry(93,1,now+5000,now+6000,"later","","","",false,false,true);
  AgendaEntry allDay=new AgendaEntry(94,1,now+2000,now+4000,"allDay","","","",false,true,true);
  AgendaEntry unconfirmed=new AgendaEntry(95,1,now+2000,now+4000,"unconfirmed","","","",false,false,false);
  check(ReminderTimeline.nextTitle(List.of(later,tie,allDay,current,unconfirmed,early),current,now).equals("early"),"next title earliest eligible and stable key tie");
  check(ReminderTimeline.nextTitle(List.of(early,later,tie),current,early.start).equals("later"),"next title excludes already started items");
  check(ReminderTimeline.nextTitle(List.of(current,allDay,unconfirmed),current,now).isEmpty(),"next title absent for ineligible or same occurrence");
  EventCandidate a=new EventCandidate(1,now+600000,now+3600000),b=new EventCandidate(2,now+700000,now+3600000),d=new EventCandidate(3,now+800000,now+3600000),e=new EventCandidate(4,now+900000,now+3600000);
  a.title="first";b.title="second";d.title="third";e.title="fourth";e.category="todo";
  TaskStore.rows=new ArrayList<>(List.of(a,b,d,e));refresh(c,null);
  check(nextTitles.get(NOTIFICATION_ID).equals("second")&&nextTitles.get(NOTIFICATION_ID+2).equals("fourth"),"refresh forwards next title including occurrence beyond card cap");
  check(c.manager.cards.size()==3,"vivo three-card cap");check(slot(c,0).equals(a.key())&&slot(c,1).equals(b.key())&&slot(c,2).equals(d.key()),"initial slot identities");
  check(alarm<=System.currentTimeMillis()+ReminderTimeline.PROGRESS_REFRESH,"vivo progress refresh");
  AgendaReminderPreferences.atomic=false;VivoAtomicNotification.ended.clear();c.manager.events.clear();refresh(c,null);
  check(c.manager.cards.size()==3&&!c.manager.atomic.containsValue(true),"available vivo retains three ordinary cards when source atomic disabled");
  for(int i=0;i<3;i++)check(c.manager.events.indexOf("end:"+(NOTIFICATION_ID+i))<c.manager.events.indexOf("notify:"+(NOTIFICATION_ID+i)),"atomic to ordinary ends before notify");
  check(VivoAtomicNotification.ended.size()==3&&alarm>System.currentTimeMillis()+ReminderTimeline.PROGRESS_REFRESH,"ordinary conversion clears each atomic card and progress alarm");
  VivoAtomicNotification.ended.clear();refresh(c,null);check(VivoAtomicNotification.ended.isEmpty(),"same ordinary format needs no cancellation");
  AgendaReminderPreferences.atomic=true;VivoAtomicNotification.ended.clear();c.manager.events.clear();refresh(c,null);
  check(c.manager.atomic.size()==3&&!c.manager.atomic.containsValue(false)&&VivoAtomicNotification.ended.size()==3,"ordinary to atomic replaces each slot");
  for(int i=0;i<3;i++)check(c.manager.events.indexOf("end:"+(NOTIFICATION_ID+i))<c.manager.events.indexOf("notify:"+(NOTIFICATION_ID+i)),"ordinary to atomic ends before notify");
  long originalStarted=c.prefs.getLong("slot_started:0",0);check(originalStarted>0&&notified.get(NOTIFICATION_ID)==originalStarted,"initial slot start passed to card");
  c.prefs.edit().putLong("slot_started:0",now-12345).apply();
  VivoAtomicNotification.ended.clear();Collections.reverse(TaskStore.rows);refresh(c,null);
  check(c.prefs.getLong("slot_started:0",0)==now-12345&&notified.get(NOTIFICATION_ID)==now-12345,"retained slot preserves animation origin");
  check(VivoAtomicNotification.ended.isEmpty()&&slot(c,0).equals(a.key())&&slot(c,1).equals(b.key())&&slot(c,2).equals(d.key()),"reordering does not end retained cards");
  VivoAtomicNotification.ended.clear();refresh(c,new Intent(a.key(),"snooze"));
  check(slot(c,0).equals(e.key())&&slot(c,1).equals(b.key())&&slot(c,2).equals(d.key()),"deferred occurrence frees only its slot");
  check(VivoAtomicNotification.ended.equals(List.of(NOTIFICATION_ID)),"operation2 only on replaced slot");
  check(c.prefs.getLong("slot_started:0",0)>=originalStarted&&c.prefs.getLong("slot_started:0",0)!=now-12345&&notified.get(NOTIFICATION_ID)==c.prefs.getLong("slot_started:0",0),"replacement resets animation origin");
  check(categories.get(NOTIFICATION_ID).equals("todo")&&categories.get(NOTIFICATION_ID+1).equals("event"),"local categories propagate into notification entries");
  check(c.prefs.getLong("slot_end:0",0)==e.startAtMillis+180000,"todo slot expires three minutes after start");
  check(c.prefs.getLong("slot_end:1",0)==b.endAtMillis,"event slot expiration remains its finish");
  check(c.prefs.getLong("snooze_until:"+a.key(),0)>now,"per-occurrence snooze saved");
  refresh(c,new Intent(b.key(),"snooze"));check(c.prefs.getLong("snooze_until:"+a.key(),0)>now&&c.prefs.getLong("snooze_until:"+b.key(),0)>now,"two independent snoozes survive");
  check(c.manager.cards.size()==2,"two deferred occurrences leave remaining cards");
  refresh(c,new Intent(a.key(),"hide"));check(!c.prefs.values.containsKey("hidden:"+a.key())&&!c.prefs.values.containsKey("hidden:"+e.key()),"stale action cannot hide old or replacement");
  c.prefs.edit().putLong("snooze_until:"+a.key(),now-1).apply();refresh(c,null);
  check(!c.prefs.values.containsKey("snooze_until:"+a.key())&&c.manager.cards.containsValue(a.key()),"expired snooze cleans up and returns occurrence");
  refresh(c,new Intent(d.key(),"complete"));check(c.prefs.getLong("hidden:"+d.key(),0)==d.endAtMillis&&!c.manager.cards.containsValue(d.key()),"completion ends one occurrence only");
  refresh(c,null);check(!c.manager.cards.containsValue(d.key()),"completed occurrence is not re-pushed during refresh");
  EventCandidate changed=new EventCandidate(3,d.startAtMillis+1,d.endAtMillis);TaskStore.rows.add(changed);refresh(c,null);
  check(c.manager.cards.containsValue(changed.key()),"hidden previous key does not hide edited occurrence");
  c.manager.allowed=false;VivoAtomicNotification.ended.clear();refresh(c,null);
  check(c.manager.cards.isEmpty()&&VivoAtomicNotification.ended.size()==3,"permission disabled ends all slots");
  for(int i=0;i<3;i++)check(slot(c,i).isEmpty()&&!c.prefs.values.containsKey("slot_started:"+i),"permission disabled clears slot key and start");
  c.manager.allowed=true;refresh(c,null);AgendaReminderPreferences.enabled=false;VivoAtomicNotification.ended.clear();refresh(c,null);
  check(c.manager.cards.isEmpty()&&alarm==0&&VivoAtomicNotification.ended.size()==3,"disabled ends all slots and cancels alarm");
  AgendaReminderPreferences.enabled=true;VivoAtomicNotification.vivo=false;refresh(c,null);
  check(c.manager.cards.size()==1&&alarm>System.currentTimeMillis()+ReminderTimeline.PROGRESS_REFRESH,"ordinary platform single card boundary refresh");
  c.prefs.edit().putLong("slot_end:0",now-1).apply();refresh(c,new Intent(a.key(),"hide"));
  check(!c.prefs.values.containsKey("hidden:"+a.key()),"expired card action rejected before refresh");
  for(EventCandidate row:TaskStore.rows){row.startAtMillis=now-180000;row.endAtMillis=now-1;}refresh(c,null);
  check(c.manager.cards.isEmpty(),"ended occurrences end notifications");
  TaskStore.rows=List.of();refresh(c,null);check(c.manager.cards.isEmpty(),"empty agenda ends remaining notification");
  long boundaryNow=System.currentTimeMillis();EventCandidate close=new EventCandidate(99,boundaryNow+150000,boundaryNow+3600000);
  TaskStore.rows=List.of(close);VivoAtomicNotification.vivo=true;AgendaReminderPreferences.atomic=true;refresh(c,null);
  check(alarm==close.startAtMillis-ReminderTimeline.CAPSULE_COUNTDOWN_WINDOW,"atomic schedules exact two-minute mode boundary before normal refresh");
  AgendaReminderPreferences.atomic=false;refresh(c,null);
  check(alarm>close.startAtMillis-ReminderTimeline.CAPSULE_COUNTDOWN_WINDOW,"ordinary notification does not schedule capsule mode boundary");
  AgendaReminderPreferences.atomic=true;Context restarted=new Context();
  EventCandidate running=new EventCandidate(100,boundaryNow-1200000,boundaryNow+3600000);TaskStore.rows=List.of(running);
  refresh(restarted,null);check(restarted.manager.cards.containsValue(running.key())&&!updates.get(NOTIFICATION_ID),"ongoing event creates island on first launch");
  long origin=restarted.prefs.getLong("slot_started:0",0);restarted.manager.cards.clear();restarted.manager.atomic.clear();refresh(restarted,null);
  check(restarted.manager.cards.containsValue(running.key())&&!updates.get(NOTIFICATION_ID),"force-stop retained slot recreates missing island, not update");
  check(restarted.prefs.getLong("slot_started:0",0)==origin,"restart preserves notification origin");
  refresh(restarted,null);check(updates.get(NOTIFICATION_ID),"live island uses update");
  refresh(restarted,new Intent(running.key(),"complete"));restarted.manager.cards.clear();restarted.manager.atomic.clear();refresh(restarted,null);
  check(restarted.manager.cards.isEmpty(),"restart never restores completed occurrence");
  System.out.println("Production reminder refresh passed: "+checks+" checks; stable slots, restart restoration, stale actions, independent snooze/hide, permission, disabled and platform limits");
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
