"""Execute reminder decisions, calendar overlap layout, independent preferences and production SQL.

Pure Java is production source. Preference/SQLite transport are Android stand-ins;
the SQL built by the actual TaskStore methods is then executed against real SQLite.
This cannot establish Android notification rendering or background delivery.
"""
from recurrence_dependencies import recurrence_java

from pathlib import Path
import json
import os
import re
import sqlite3
import subprocess

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/agenda-features-check'
out.mkdir(parents=True, exist_ok=True)

def method(source, signature):
    start = source.index(signature)
    brace = source.index('{', start)
    depth = 1
    end = brace + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

files = {
    'android/content/Context.java': '''package android.content;
import java.util.*;
public class Context {
 public static final int MODE_PRIVATE=0;
 public final Map<String,SharedPreferences> prefs=new HashMap<>();
 public Context getApplicationContext(){return this;}
 public SharedPreferences getSharedPreferences(String name,int mode){return prefs.computeIfAbsent(name,k->new SharedPreferences());}
}''',
    'android/content/SharedPreferences.java': '''package android.content;
import java.util.*;
public class SharedPreferences {
 final Map<String,Object> values=new HashMap<>();
 public boolean getBoolean(String k,boolean d){return (boolean)values.getOrDefault(k,d);}
 public int getInt(String k,int d){return (int)values.getOrDefault(k,d);}
 public Editor edit(){return new Editor();}
 public class Editor {
  public Editor putBoolean(String k,boolean v){values.put(k,v);return this;}
  public Editor putInt(String k,int v){values.put(k,v);return this;}
  public void apply(){}
 }
}''',
    'com/donglan/chrona/AgendaFeaturesCheck.java': r'''package com.donglan.chrona;
import java.util.*;import java.time.*;import android.content.Context;
class AgendaWidgetProvider { static void requestRefresh(Context c){} }
class AgendaReminder { static void requestRefresh(Context c){} }
class AgendaReminderJobService { static void schedule(Context c){} }
public class AgendaFeaturesCheck {
 static int checks;
 static void check(boolean ok,String reason){checks++;if(!ok)throw new AssertionError(reason);}
 static AgendaEntry e(long id,long start,long end){return new AgendaEntry(id,1,start,end,"event","room","note","拾时",false,false,true);}
 static ReminderTimeline t(List<AgendaEntry> e,long now){return new ReminderTimeline(e,now,Set.of(),Map.of(),1);}
 public static void main(String[] args){
  long start=10_000_000L;AgendaEntry a=e(1,start,start+3_600_000);
  long notifiedAt=start-ReminderTimeline.LEAD;
  AgendaEntry early=new AgendaEntry(99,1,start,start+3600000,"early","","","拾时",false,false,true,null,"event",120);
  check(ReminderTimeline.appearsAt(early)==start-7200000,"explicit early reminder controls island appearance");
  check(t(List.of(early),start-7200001).entries.isEmpty()&&t(List.of(early),start-7200001).wakeAt==start-7200000,"alarm targets early reminder boundary");
  check(t(List.of(early),start-7200000).entries.size()==1,"island appears exactly at early reminder boundary");
  check(!ReminderTimeline.capsuleCountdown(a,start-120_001),"capsule stays clock above two minutes");
  check(ReminderTimeline.capsuleCountdown(a,start-120_000),"capsule switches exactly at two minutes");
  check(ReminderTimeline.capsuleCountdown(a,start-1),"capsule remains countdown before start");
  check(ReminderTimeline.capsuleDeadline(a,start-120_000)==start,"upcoming countdown targets start");
  check(!ReminderTimeline.capsuleCountdown(a,start)&&ReminderTimeline.capsuleDeadline(a,start)==a.end,"long activity resets capsule target at start");
  check(ReminderTimeline.capsuleCountdown(a,a.end-120_000),"active capsule countdown targets finish");
  check(!ReminderTimeline.capsuleCountdown(a,a.end),"no countdown after finish");
  AgendaEntry reminder=new AgendaEntry(88,1,start,start+60_000,"reminder","","","拾时",false,false,true,null,"reminder");
  check(ReminderTimeline.capsuleCountdown(reminder,start-120_000)&&!ReminderTimeline.capsuleCountdown(reminder,start),"point reminder only counts down before start");
  check(ReminderTimeline.elapsedRatio(a,notifiedAt,notifiedAt)==0,"new lead phase empty");
  check(ReminderTimeline.elapsedRatio(a,start-ReminderTimeline.LEAD/2,notifiedAt)==0.5,"half lead elapsed");
  check(ReminderTimeline.elapsedRatio(a,start,notifiedAt)==0,"event starts new active phase");
  check(ReminderTimeline.elapsedRatio(a,start+1_800_000,notifiedAt)==0.5,"half active elapsed");
  check(ReminderTimeline.elapsedRatio(a,a.end,notifiedAt)==1,"finished progress full");
  check(ReminderTimeline.elapsedRatio(e(9,start,start),start+30_000,notifiedAt)==0.5,"point event uses bounded minute");
  long delayed=start-600_000;
  check(ReminderTimeline.elapsedRatio(a,delayed,delayed)==0,"actual late delivery starts empty");
  check(ReminderTimeline.elapsedRatio(a,delayed+300_000,delayed)==0.5,"late delivery uses actual remaining lead window");
  check(ReminderTimeline.elapsedRatio(a,delayed-1,delayed)==0,"before actual notification clamps zero");
  check(ReminderTimeline.elapsedRatio(a,a.end+60_000,delayed)==1,"after finish clamps one");
  check(ReminderTimeline.elapsedRatio(a,start+1_800_000,start+600_000)==0.5,"delivery during event keeps event start origin");
  AgendaEntry nonEvent=new AgendaEntry(10,1,start,a.end,"todo","","","",false,false,true,null,"todo");
  check(ReminderTimeline.elapsedRatio(nonEvent,start-300_000,delayed)==0.5,"non-event lead still accumulates");
  check(ReminderTimeline.elapsedRatio(nonEvent,start,delayed)==1,"non-event start keeps full progress");
  check(ReminderTimeline.elapsedRatio(nonEvent,start+1_800_000,delayed)==1,"non-event skips active phase");
  check(ReminderTimeline.elapsedRatio(nonEvent,start,start+1)==1,"delivery at or after non-event start remains full");
  check(ReminderTimeline.expiresAt(a)==a.finish(),"activity expiration remains its finish");
  for(AgendaEntry notice:List.of(nonEvent,reminder)){
   check(ReminderTimeline.expiresAt(notice)==start+180000,"non-event expiration is three minutes after start regardless of end");
   check(ReminderTimeline.capsuleDeadline(notice,start)==0,"non-event has no countdown deadline after start");
   check(t(List.of(notice),start).entries.equals(List.of(notice))&&t(List.of(notice),start).wakeAt==start+180000,"started non-event wakes at three-minute expiration");
   check(t(List.of(notice),start+179999).entries.equals(List.of(notice)),"non-event remains one millisecond before expiration");
   check(t(List.of(notice),start+180000).entries.isEmpty(),"non-event removed exactly at three minutes");
  }
  double previous=0;
  for(long at=notifiedAt;at<start;at+=10_000){double value=ReminderTimeline.elapsedRatio(a,at,notifiedAt);check(value>=previous&&value<1,"lead elapsed increases");previous=value;}
  previous=0;
  for(long at=start;at<=a.end;at+=10_000){double value=ReminderTimeline.elapsedRatio(a,at,notifiedAt);check(value>=previous&&value<=1,"active elapsed increases");previous=value;}
  check(t(List.of(a),start-ReminderTimeline.LEAD-1).entries.isEmpty(),"not before boundary");
  check(t(List.of(a),start-ReminderTimeline.LEAD).entries.size()==1,"visible at 30 minutes");
  check(t(List.of(a),start-1).wakeAt==start,"wake at start");
  check(t(List.of(a),start).entries.size()==1&&t(List.of(a),start).wakeAt==a.end,"ongoing after start");
  check(t(List.of(a),a.end).entries.isEmpty(),"removed at end");
  check(t(List.of(e(2,start,start)),start).wakeAt==start+60_000,"point event bounded lifetime");
  check(t(List.of(),start).entries.isEmpty(),"empty agenda");
  check(t(List.of(new AgendaEntry(3,1,start,start+1_000,"x","","","",false,true,true)),start).entries.isEmpty(),"all day excluded");
  check(t(List.of(new AgendaEntry(3,1,start,start+1_000,"x","","","",false,false,false)),start).entries.isEmpty(),"unconfirmed excluded");
  AgendaEntry b=e(2,start+600_000,start+4_000_000);
  check(t(List.of(a,b),start).entries.get(0)==b,"next due event replaces active card");
  check(t(List.of(a,b),b.start).entries.get(0)==b,"latest active remains focused");
  AgendaEntry far=e(3,start+7_200_000,start+8_000_000);
  check(t(List.of(a,far),start).entries.get(0)==a,"keep active when future not due");
  check(t(List.of(a,far),start).wakeAt==Math.min(a.end,far.start-ReminderTimeline.LEAD),"next due boundary tracked");
  check(new ReminderTimeline(List.of(a,b),start,Set.of(a.key(),b.key()),Map.of(),3).entries.isEmpty(),"multiple hidden occurrences stay hidden");
  check(new ReminderTimeline(List.of(a),start,Set.of(),Map.of(a.key(),start+300_000),3).wakeAt==start+300_000,"snooze survives refresh");
  check(new ReminderTimeline(List.of(a),start,Set.of(),Map.of(a.key(),start+300_000),3).entries.isEmpty(),"snoozed hidden");
  check(new ReminderTimeline(List.of(a),start+300_000,Set.of(),Map.of(a.key(),start+300_000),3).entries.size()==1,"snooze expires");
  AgendaEntry deferredTask=new AgendaEntry(77,1,start,start,"task","","","",false,false,true,null,"task");
  long resumed=start+180_000;
  check(ReminderTimeline.snoozeAt(deferredTask,start-120_000)==resumed,"two minutes before deadline snoozes for full five minutes");
  check(new ReminderTimeline(List.of(deferredTask),resumed,Set.of(),Map.of(deferredTask.key(),resumed),3).entries.equals(List.of(deferredTask)),"explicit task snooze survives original three-minute expiry");
  check(ReminderTimeline.expiresAt(deferredTask,resumed)==resumed+180_000,"resumed task has full three-minute window");
  check(new ReminderTimeline(List.of(deferredTask),resumed+180_000,Set.of(),Map.of(deferredTask.key(),resumed),3).entries.isEmpty(),"resumed task exits exactly at new expiry");
  check(ReminderTimeline.snoozeAt(a,a.finish()-60_000)==a.finish()-30_000,"activity returns before end");
  check(ReminderTimeline.snoozeAt(a,a.finish()-30_000)==0,"no postponement offered in final activity seconds");
  AgendaEntry next=e(10,start+100_000,start+4_000_000), later=e(11,start+200_000,start+4_000_000);
  List<AgendaEntry> many=List.of(a,b,next,later,far);
  ReminderTimeline three=new ReminderTimeline(many,start,Set.of(),Map.of(),3);
  check(three.entries.equals(List.of(next,later,b)),"three cap prioritizes upcoming in ascending order");
  check(new ReminderTimeline(many,start,Set.of(next.key()),Map.of(b.key(),start+300_000),3).entries.equals(List.of(later,a)),"independent hidden and deferred occurrences");
  check(new ReminderTimeline(List.of(a),start,Set.of(),Map.of(b.key(),start+300_000),3).wakeAt==start+300_000,"excluded deferred occurrence still wakes");
  AgendaEntry[] slots=ReminderSlots.assign(List.of(a,b,next),new String[]{"","",""});
  String[] keys={slots[0].key(),slots[1].key(),slots[2].key()};
  AgendaEntry[] reordered=ReminderSlots.assign(List.of(next,b,a),keys);
  check(reordered[0]==a&&reordered[1]==b&&reordered[2]==next,"priority reordering keeps notification slots");
  AgendaEntry[] replaced=ReminderSlots.assign(List.of(later,b,next),keys);
  check(replaced[0]==later&&replaced[1]==b&&replaced[2]==next,"reuse only vacated slot");
  check(ReminderSlots.find(new String[]{later.key(),b.key(),next.key()},a.key())==-1,"stale action key rejected");
  check(ReminderSlots.find(keys,e(a.id,a.start+1,a.end).key())==-1,"edited occurrence action rejected");
  check(!a.key().equals(e(1,start+1,start+2).key()),"edited start has fresh identity");
  AgendaEntry recurring=new AgendaEntry(1,0,start,start+1000,"x","","","sys",true,false,true);
  check(!a.key().equals(recurring.key()),"system/local ID spaces differ");
  Context c=new Context();
  for(int tab=0;tab<4;tab++)check(ScheduleOrderPreferences.oldestFirst(c,tab)==(tab==0),"preset defaults");
  ScheduleOrderPreferences.setOldestFirst(c,0,false);ScheduleOrderPreferences.setOldestFirst(c,2,true);
  check(!ScheduleOrderPreferences.oldestFirst(c,0)&&ScheduleOrderPreferences.oldestFirst(c,2),"presets independent");
  check(!WidgetPreferences.includesSystemCalendar(c)&&!HomeTimelinePreferences.includesSystemCalendar(c)&&!AgendaReminderPreferences.includesSystemCalendar(c),"sources off by default");
  HomeTimelinePreferences.setIncludesSystemCalendar(c,true);
  check(!WidgetPreferences.includesSystemCalendar(c)&&!AgendaReminderPreferences.includesSystemCalendar(c),"home does not control other sources");
  WidgetPreferences.setIncludesSystemCalendar(c,true);HomeTimelinePreferences.setIncludesSystemCalendar(c,false);
  check(WidgetPreferences.includesSystemCalendar(c)&&!AgendaReminderPreferences.includesSystemCalendar(c),"widget persists independently");
  AgendaReminderPreferences.setIncludesSystemCalendar(c,true);WidgetPreferences.setIncludesSystemCalendar(c,false);
  check(AgendaReminderPreferences.includesSystemCalendar(c)&&!WidgetPreferences.includesSystemCalendar(c),"reminder independent");
  ZoneId zone=ZoneId.of("Asia/Shanghai");LocalDate day=LocalDate.of(2026,10,12);
  long midnight=day.atStartOfDay(zone).toInstant().toEpochMilli();
  List<AgendaEntry> overlap=List.of(e(1,midnight+9*3_600_000L,midnight+11*3_600_000L),e(2,midnight+10*3_600_000L,midnight+12*3_600_000L),e(3,midnight+12*3_600_000L,midnight+13*3_600_000L));
  List<CalendarTimelineLayout.Block> blocks=CalendarTimelineLayout.day(overlap,day,zone);
  check(blocks.size()==3&&blocks.get(0).lanes==2&&blocks.get(1).lanes==2,"overlap lanes");
  check(blocks.get(0).lane!=blocks.get(1).lane&&blocks.get(2).lanes==1,"touching endpoints separate");
  AgendaEntry cross=e(4,midnight-3_600_000,midnight+3_600_000);
  check(new ReminderTimeline(List.of(cross),midnight,Set.of(),Map.of(),3).entries.equals(List.of(cross)),"active notification crosses midnight");
  CalendarTimelineLayout.Block clipped=CalendarTimelineLayout.day(List.of(cross),day,zone).get(0);
  check(clipped.startMinute==0&&clipped.endMinute==60,"cross midnight clipped");
  check(CalendarTimelineLayout.day(List.of(e(5,midnight-2_000,midnight)),day,zone).isEmpty(),"exclusive day boundary");
  check(CalendarTimelineLayout.day(List.of(e(6,midnight+86_340_000,midnight+86_400_000)),day,zone).get(0).endMinute==1440,"end at midnight");
  for(ZoneId dst:List.of(ZoneId.of("America/New_York"),zone))for(LocalDate date:List.of(LocalDate.of(2026,3,8),LocalDate.of(2026,11,1))){
   long begin=date.atStartOfDay(dst).toInstant().toEpochMilli(),end=date.plusDays(1).atStartOfDay(dst).toInstant().toEpochMilli();
   CalendarTimelineLayout.Block whole=CalendarTimelineLayout.day(List.of(e(7,begin,end)),date,dst).get(0);
   check(whole.startMinute==0&&whole.endMinute==1440,"local day including DST");
  }
  System.out.println("Java agenda feature checks passed: "+checks);
 }
}''',
}
for name in ('AgendaEntry', 'ReminderTimeline', 'ReminderSlots', 'CalendarTimelineLayout', 'WidgetPreferences', 'HomeTimelinePreferences', 'ScheduleOrderPreferences', 'AgendaReminderPreferences'):
    files[f'com/donglan/chrona/{name}.java'] = (src / f'{name}.java').read_text(encoding='utf-8')
store = (src / 'data/TaskStore.java').read_text(encoding='utf-8')
sql_methods = '\n'.join(method(store, signature) for signature in ('public List<EventCandidate> calendarCandidates(', 'public List<EventCandidate> nextReminderCandidates('))
files['com/donglan/chrona/SqlCheck.java'] = '''package com.donglan.chrona;
import java.util.*;
import com.donglan.chrona.data.ScheduleQuery;
import com.donglan.chrona.data.EventCandidate;
import com.donglan.chrona.data.RepeatOccurrences;
class Cursor implements AutoCloseable {public boolean moveToNext(){return false;}public boolean moveToFirst(){return false;}public void close(){}}
class Database {
 Cursor query(String table,Object columns,String selection,String[] args,Object group,Object having,String sort,String limit){
  System.out.println("SQL\\t"+selection+"\\t"+String.join("|",args)+"\\t"+sort+"\\t"+limit);return new Cursor();}
}
public class SqlCheck {
 __ZJU_REMINDER_ALLOWED__
 Database getReadableDatabase(){return new Database();}EventCandidate readCandidate(Cursor c){return null;}
 // These SQL fixtures contain no series; projection behavior is covered by RepeatProjectionCheck.
 List<EventCandidate> repeatingCandidates(){return List.of();}
 List<EventCandidate> mergeRepeats(List<EventCandidate> rows,long a,long b,long c,long d,int limit){return rows;}
 ''' + sql_methods + '''
 public static void main(String[]args){SqlCheck c=new SqlCheck();c.calendarCandidates(10000000,20000000,10000000,20000000);c.nextReminderCandidates(10000000,Set.of());c.nextReminderCandidates(10000000,Set.of("chrona:8:9999999"));c.nextReminderCandidates(30000000,Set.of());c.nextReminderCandidates(18000000,Set.of());}
}'''
allowed_begin = store.index('    private static final String ZJU_REMINDER_ALLOWED =')
allowed_end = store.index(';', allowed_begin) + 1
files['com/donglan/chrona/SqlCheck.java'] = files['com/donglan/chrona/SqlCheck.java'].replace('__ZJU_REMINDER_ALLOWED__', store[allowed_begin:allowed_end])
calendar = (src / 'calendar/CalendarStore.java').read_text(encoding='utf-8')
files['com/donglan/chrona/calendar/CalendarOccurrence.java'] = (src / 'calendar/CalendarOccurrence.java').read_text(encoding='utf-8')
files['com/donglan/chrona/calendar/AllDayDates.java'] = (src / 'calendar/AllDayDates.java').read_text(encoding='utf-8')
files['android/os/CancellationSignal.java'] = 'package android.os; public class CancellationSignal {}'
files['com/donglan/chrona/calendar/ReminderProviderCheck.java'] = '''package com.donglan.chrona.calendar;
import java.util.*;import java.time.*;
class Uri {static class Builder {List<Long> bounds=new ArrayList<>();String build(){return bounds.get(0)+":"+bounds.get(1);}}String buildUpon(){return "";}}
class ContentUris {static void appendId(Uri.Builder builder,long value){builder.bounds.add(value);}}
class CalendarContract {static class Instances {static final String EVENT_ID="id",BEGIN="begin",END="end";static class CONTENT_URI {static Uri.Builder buildUpon(){return new Uri.Builder();}}}}
class Calendars {static final String VISIBLE="visible",CALENDAR_DISPLAY_NAME="calendar",CALENDAR_COLOR="calendar_color",ACCOUNT_NAME="account";}
class Events {static final String TITLE="title",ALL_DAY="all_day",EVENT_LOCATION="place",STATUS="status",CALENDAR_ID="calendar_id",DESCRIPTION="description";static final int STATUS_CANCELED=2;}
class ScheduleSources {static Set<Long> blocked=new HashSet<>();static boolean allowsCalendar(Object context,long id){return !blocked.contains(id);}}
class Cursor implements AutoCloseable {
 List<Object[]>rows;int i=-1;Cursor(List<Object[]>r){rows=r;}boolean moveToNext(){return ++i<rows.size();}
 boolean isNull(int c){return rows.get(i)[c]==null;}long getLong(int c){return (long)rows.get(i)[c];}
 String getString(int c){return (String)rows.get(i)[c];}public void close(){}
 int getInt(int c){return rows.get(i)[c]==null?0:((Number)rows.get(i)[c]).intValue();}
}
class Resolver {
 List<Object[]>rows=new ArrayList<>();String lastUri;
 void projection(String[] p){if(p.length!=11||!p[7].equals("calendar_color")||!p[8].equals("calendar_id")||!p[9].equals("account")||!p[10].equals("description"))throw new AssertionError("calendar source and description projection");}
 Cursor query(String uri,String[]projection,String selection,String[]args,String sort){
  if(!selection.contains("all_day=0")||!selection.contains("visible=1"))throw new AssertionError("timed visible filter");
  projection(projection);lastUri=uri;
  return new Cursor(rows);
 }
 Cursor query(String uri,String[]projection,String selection,String[]args,String sort,android.os.CancellationSignal cancellation){
  projection(projection);if(!selection.contains("visible=1"))throw new AssertionError("visible calendar source");
  return new Cursor(rows);
 }
}
public class ReminderProviderCheck {
 static final int MAX_REMINDER_FUTURE_DAYS=4;
 final Object context=new Object();
 final Resolver resolver=new Resolver();void requireRead(){}
 ''' + method(calendar, 'public List<CalendarOccurrence> reminderInstances(') + '''
 ''' + method(calendar, 'public List<CalendarOccurrence> listInstances(long begin, long end,\n            android.os.CancellationSignal cancellation, int limit)') + '''
 ''' + method(calendar, 'private List<CalendarOccurrence> listInstances(long begin, long end,\n            android.os.CancellationSignal cancellation, int limit, boolean allSources)') + '''
 static Object[]e(long id,long start,long end){return new Object[]{id,start,end,"event",0,"room","system",null,42L,"account","note "+id};}
 public static void main(String[]args){
  ReminderProviderCheck c=new ReminderProviderCheck();Set<Long>linked=new HashSet<>();
  for(long i=1;i<1200;i++){linked.add(i);c.resolver.rows.add(e(i,500,3000));}
  c.resolver.rows.add(e(2000,600,900));c.resolver.rows.add(e(2001,700,2000));
  c.resolver.rows.add(e(2002,800,2000));c.resolver.rows.add(e(2003,1100,2200));c.resolver.rows.add(e(2003,1200,2300));
  List<CalendarOccurrence>result=c.reminderInstances(1000,4000,linked);
  if(result.size()!=5||result.get(0).eventId!=2000||result.get(1).eventId!=2001||result.get(2).eventId!=2002||result.get(3).startMillis!=1100||result.get(4).startMillis!=1200)throw new AssertionError("ended, active and future context retained after linked filtering");
  if(!result.get(0).description.equals("note 2000"))throw new AssertionError("reminder description passthrough");
  c.resolver.rows.clear();
  for(long i=1;i<=7;i++)c.resolver.rows.add(e(i,100*i,3000));
  for(long i=11;i<=17;i++)c.resolver.rows.add(e(i,100*i,3000));
  result=c.reminderInstances(1000,4000,Set.of());
  if(result.size()!=14||result.get(0).eventId!=1||result.get(13).eventId!=17)throw new AssertionError("complete same-day context without active or future card caps");
  ZoneId zone=ZoneId.systemDefault();LocalDate today=LocalDate.of(2026,10,17);
  long now=today.atTime(14,10).atZone(zone).toInstant().toEpochMilli();
  long midnight=today.atStartOfDay(zone).toInstant().toEpochMilli();
  c.resolver.rows.clear();
  c.resolver.rows.add(e(3000,midnight+13*3600000+25*60000,now));
  c.resolver.rows.add(e(3001,now+5*60000,now+100*60000));
  for(int d=1;d<=4;d++)for(int i=0;i<8;i++){
   long at=today.plusDays(d).atTime(8+i,0).atZone(zone).toInstant().toEpochMilli();
   c.resolver.rows.add(e(4000+d*10+i,at,at+1800000));
  }
  result=c.reminderInstances(now,now+7*86400000L,Set.of());
  if(!c.resolver.lastUri.startsWith(midnight+":"))throw new AssertionError("query starts at current local midnight");
  if(result.size()!=26||result.get(0).eventId!=3000||result.get(1).eventId!=3001||result.get(25).eventId!=4037)throw new AssertionError("ended course segment and complete four future dates including today; fifth date excluded");
  c.resolver.rows.remove(1);result=c.reminderInstances(now,now+7*86400000L,Set.of());
  if(result.size()!=33||result.get(32).eventId!=4047)throw new AssertionError("ended-only today does not consume a future date");
  System.out.println("Production reminder provider scan passed: 1199 linked copies, ended course context, complete four-future-date boundaries and description");
  c.resolver.rows.clear();c.resolver.rows.add(new Object[]{1L,100L,200L,"color",0,"","source",0xFFFF0000,42L,"account","first note"});
  c.resolver.rows.add(new Object[]{2L,100L,200L,"missing",1,"","source",null,43L,"account",null});
  c.resolver.rows.add(new Object[]{3L,100L,200L,"black",0,"","source",0,44L,"account","last note"});
  result=c.listInstances(0,300,null,10);
  if(result.size()!=3||result.get(0).calendarColor!=0xFFFF0000||result.get(1).calendarColor!=null||result.get(2).calendarColor!=0||!result.get(1).allDay)throw new AssertionError("provider source color/null/black/all-day");
  if(!result.get(0).description.equals("first note")||!result.get(1).description.isEmpty()||!result.get(2).description.equals("last note"))throw new AssertionError("instance description passthrough");
  ScheduleSources.blocked.add(42L);result=c.listInstances(0,300,null,1);
  if(result.size()!=1||result.get(0).calendarId!=43L)throw new AssertionError("source filter precedes limit");
  c.resolver.rows.clear();Object[]blocked=e(8,1100,3000);Object[]allowed=e(9,1200,3000);allowed[8]=43L;c.resolver.rows.add(blocked);c.resolver.rows.add(allowed);
  Object[]allDay=e(10,1250,3000);allDay[4]=1;allDay[8]=43L;c.resolver.rows.add(allDay);
  Object[]invalid=e(11,1300,1300);invalid[8]=43L;c.resolver.rows.add(invalid);
  result=c.reminderInstances(1000,4000,Set.of());
  if(result.size()!=1||result.get(0).eventId!=9)throw new AssertionError("reminder source filter");
  System.out.println("Production instance query passed: CALENDAR_COLOR projection, null and black source colors");
 }
}'''
paths = []
for name, content in files.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    paths.append(path)
paths.append(src / 'data/EventCategory.java')
paths.append(src / 'data/ScheduleQuery.java')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run(recurrence_java([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), *map(str, paths)]), check=True)
subprocess.run(recurrence_java([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.AgendaFeaturesCheck']), check=True)
subprocess.run(recurrence_java([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.calendar.ReminderProviderCheck']), check=True)
queries = subprocess.check_output(recurrence_java([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.SqlCheck']), text=True)
db = sqlite3.connect(':memory:')
db.execute("CREATE TABLE event_candidates(id INTEGER PRIMARY KEY,start_at_millis INTEGER,end_at_millis INTEGER,all_day INTEGER,needs_confirmation INTEGER,category TEXT NOT NULL DEFAULT 'event',reminder_minutes_before INTEGER)")
db.executemany('INSERT INTO event_candidates(id,start_at_millis,end_at_millis,all_day,needs_confirmation) VALUES(?,?,?,?,?)', [(1,8000000,12000000,0,0),(2,9000000,13000000,0,0),(3,11000000,12000000,0,0),(4,11500000,12000000,0,0),(5,10500000,12000000,0,1),(6,10500000,12000000,1,0),(7,None,None,0,0),(8,9999999,None,0,0)])
db.executemany('INSERT INTO event_candidates(id,start_at_millis,end_at_millis,all_day,needs_confirmation) VALUES(?,?,?,?,?)', [(i,1,2,0,0) for i in range(100,6200)])
db.executemany('INSERT INTO event_candidates(id,start_at_millis,end_at_millis,all_day,needs_confirmation) VALUES(?,?,?,?,?)', [(9,11600000,12000000,0,0),(10,11700000,12000000,0,0)])
db.executemany('INSERT INTO event_candidates(id,start_at_millis,end_at_millis,all_day,needs_confirmation,category) VALUES(?,?,?,?,?,?)', [(20,29600000,33000000,0,0,'event'),(21,29820001,29820002,0,0,'todo'),(22,29820000,33000000,0,0,'reminder'),(23,29819999,33000000,0,0,'todo')])
db.execute("INSERT INTO event_candidates VALUES(30,40000000,41000000,0,0,'event',600)")
for column in ("is_record INTEGER NOT NULL DEFAULT 0","completed_at_millis INTEGER","due_at_millis INTEGER","repeat_rule TEXT","repeat_exceptions TEXT NOT NULL DEFAULT '{}'","repeat_stopped_at INTEGER"): db.execute("ALTER TABLE event_candidates ADD COLUMN "+column)
db.execute("ALTER TABLE event_candidates ADD COLUMN task_id INTEGER NOT NULL DEFAULT 1")
db.execute("CREATE TABLE zju_homework_captures(capture_id INTEGER,account TEXT)")
db.execute("CREATE TABLE managed_zju_state(enabled INTEGER,capture_id INTEGER)")
db.execute("CREATE TABLE zju_homework_map(candidate_id INTEGER,capture_id INTEGER,ignored INTEGER)")
expected = [[1,2,8,5,6,3,4,9,10], [8,2,1], [3,4,9,10], [20,23,22,21], [2,1], [3,4,9,10], [20,23,22,21], [21,20], [30], [], [], [30], [20,23,22,21]]
for line, wanted in zip(queries.splitlines(), expected, strict=True):
    tag, selection, arguments, sort, limit = line.split('\t')
    rows = [r[0] for r in db.execute(f'SELECT id FROM event_candidates WHERE {selection} ORDER BY {sort} LIMIT {limit}', arguments.split('|'))]
    assert rows == wanted, (rows, wanted, selection)
print('Production SQLite checks passed: source eligibility, point events, active/future, hidden and large history')
