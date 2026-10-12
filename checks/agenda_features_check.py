"""Execute reminder decisions, calendar overlap layout, independent preferences and production SQL.

Pure Java is production source. Preference/SQLite transport are Android stand-ins;
the SQL built by the actual TaskStore methods is then executed against real SQLite.
This cannot establish Android notification rendering or background delivery.
"""
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
 static ReminderTimeline t(List<AgendaEntry> e,long now){return new ReminderTimeline(e,now,Set.of(),"",0);}
 public static void main(String[] args){
  long start=10_000_000L;AgendaEntry a=e(1,start,start+3_600_000);
  check(!t(List.of(a),start-ReminderTimeline.LEAD-1).visible,"not before boundary");
  check(t(List.of(a),start-ReminderTimeline.LEAD).visible,"visible at 30 minutes");
  check(t(List.of(a),start-1).wakeAt==start,"wake at start");
  check(t(List.of(a),start).visible&&t(List.of(a),start).wakeAt==a.end,"ongoing after start");
  check(t(List.of(a),a.end).focus==null,"removed at end");
  check(t(List.of(e(2,start,start)),start).wakeAt==start+60_000,"point event bounded lifetime");
  check(t(List.of(),start).focus==null,"empty agenda");
  check(t(List.of(new AgendaEntry(3,1,start,start+1_000,"x","","","",false,true,true)),start).focus==null,"all day excluded");
  check(t(List.of(new AgendaEntry(3,1,start,start+1_000,"x","","","",false,false,false)),start).focus==null,"unconfirmed excluded");
  AgendaEntry b=e(2,start+600_000,start+4_000_000);
  check(t(List.of(a,b),start).focus==b,"next due event replaces active card");
  check(t(List.of(a,b),b.start).focus==b,"latest active remains focused");
  AgendaEntry far=e(3,start+7_200_000,start+8_000_000);
  check(t(List.of(a,far),start).focus==a,"keep active when future not due");
  check(t(List.of(a,far),start).wakeAt==Math.min(a.end,far.start-ReminderTimeline.LEAD),"next due boundary tracked");
  check(new ReminderTimeline(List.of(a,b),start,Set.of(a.key(),b.key()),"",0).focus==null,"multiple hidden occurrences stay hidden");
  check(new ReminderTimeline(List.of(a),start,Set.of(),a.key(),start+300_000).wakeAt==start+300_000,"snooze survives refresh");
  check(!new ReminderTimeline(List.of(a),start,Set.of(),a.key(),start+300_000).visible,"snoozed hidden");
  check(new ReminderTimeline(List.of(a),start+300_000,Set.of(),a.key(),start+300_000).visible,"snooze expires");
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
for name in ('AgendaEntry', 'ReminderTimeline', 'CalendarTimelineLayout', 'WidgetPreferences', 'HomeTimelinePreferences', 'ScheduleOrderPreferences', 'AgendaReminderPreferences'):
    files[f'com/donglan/chrona/{name}.java'] = (src / f'{name}.java').read_text(encoding='utf-8')
store = (src / 'data/TaskStore.java').read_text(encoding='utf-8')
sql_methods = '\n'.join(method(store, signature) for signature in ('public List<EventCandidate> calendarCandidates(', 'public List<EventCandidate> nextReminderCandidates('))
files['com/donglan/chrona/SqlCheck.java'] = '''package com.donglan.chrona;
import java.util.*;
class EventCandidate {}
class Cursor implements AutoCloseable {public boolean moveToNext(){return false;}public boolean moveToFirst(){return false;}public void close(){}}
class Database {
 Cursor query(String table,Object columns,String selection,String[] args,Object group,Object having,String sort,String limit){
  System.out.println("SQL\\t"+selection+"\\t"+String.join("|",args)+"\\t"+sort+"\\t"+limit);return new Cursor();}
}
public class SqlCheck {
 Database getReadableDatabase(){return new Database();}EventCandidate readCandidate(Cursor c){return null;}
 ''' + sql_methods + '''
 public static void main(String[]args){SqlCheck c=new SqlCheck();c.calendarCandidates(10000000,20000000,10000000,20000000);c.nextReminderCandidates(10000000,Set.of());c.nextReminderCandidates(10000000,Set.of("chrona:8:9999999"));}
}'''
calendar = (src / 'calendar/CalendarStore.java').read_text(encoding='utf-8')
files['com/donglan/chrona/calendar/CalendarOccurrence.java'] = (src / 'calendar/CalendarOccurrence.java').read_text(encoding='utf-8')
files['com/donglan/chrona/calendar/AllDayDates.java'] = (src / 'calendar/AllDayDates.java').read_text(encoding='utf-8')
files['android/os/CancellationSignal.java'] = 'package android.os; public class CancellationSignal {}'
files['com/donglan/chrona/calendar/ReminderProviderCheck.java'] = '''package com.donglan.chrona.calendar;
import java.util.*;
class Uri {static class Builder {String build(){return "instances";}}String buildUpon(){return "";}}
class ContentUris {static void appendId(Uri.Builder builder,long value){}}
class CalendarContract {static class Instances {static final String EVENT_ID="id",BEGIN="begin",END="end";static class CONTENT_URI {static Uri.Builder buildUpon(){return new Uri.Builder();}}}}
class Calendars {static final String VISIBLE="visible",CALENDAR_DISPLAY_NAME="calendar",CALENDAR_COLOR="calendar_color";}
class Events {static final String TITLE="title",ALL_DAY="all_day",EVENT_LOCATION="place",STATUS="status";static final int STATUS_CANCELED=2;}
class Cursor implements AutoCloseable {
 List<Object[]>rows;int i=-1;Cursor(List<Object[]>r){rows=r;}boolean moveToNext(){return ++i<rows.size();}
 boolean isNull(int c){return rows.get(i)[c]==null;}long getLong(int c){return (long)rows.get(i)[c];}
 String getString(int c){return (String)rows.get(i)[c];}public void close(){}
 int getInt(int c){return rows.get(i)[c]==null?0:((Number)rows.get(i)[c]).intValue();}
}
class Resolver {
 List<Object[]>rows=new ArrayList<>();
 Cursor query(String uri,String[]projection,String selection,String[]args,String sort){
  if(!selection.contains("all_day=0")||!selection.contains("visible=1"))throw new AssertionError("timed visible filter");
  return new Cursor(rows);
 }
 Cursor query(String uri,String[]projection,String selection,String[]args,String sort,android.os.CancellationSignal cancellation){
  if(projection.length!=8||!projection[7].equals("calendar_color")||!selection.contains("visible=1"))throw new AssertionError("calendar color projection");
  return new Cursor(rows);
 }
}
public class ReminderProviderCheck {
 final Resolver resolver=new Resolver();void requireRead(){}
 ''' + method(calendar, 'public List<CalendarOccurrence> reminderInstances(') + '''
 ''' + method(calendar, 'public List<CalendarOccurrence> listInstances(long begin, long end,\n            android.os.CancellationSignal cancellation, int limit)') + '''
 static Object[]e(long id,long start,long end){return new Object[]{id,start,end,"event",null,"room","system"};}
 public static void main(String[]args){
  ReminderProviderCheck c=new ReminderProviderCheck();Set<Long>linked=new HashSet<>();
  for(long i=1;i<1200;i++){linked.add(i);c.resolver.rows.add(e(i,500,3000));}
  c.resolver.rows.add(e(2000,600,900));c.resolver.rows.add(e(2001,700,2000));
  c.resolver.rows.add(e(2002,800,2000));c.resolver.rows.add(e(2003,1100,2200));c.resolver.rows.add(e(2003,1200,2300));
  List<CalendarOccurrence>result=c.reminderInstances(1000,4000,linked,Set.of());
  if(result.size()!=2||result.get(0).eventId!=2002||result.get(1).startMillis!=1100)throw new AssertionError("cap/active/future");
  result=c.reminderInstances(1000,4000,linked,Set.of("system:2002:800","system:2003:1100"));
  if(result.size()!=2||result.get(0).eventId!=2001||result.get(1).startMillis!=1200)throw new AssertionError("hidden recurrence");
  System.out.println("Production reminder provider scan passed: 1199 linked copies, ended, active/future, hidden recurrence");
  c.resolver.rows.clear();c.resolver.rows.add(new Object[]{1L,100L,200L,"color",0,"","source",0xFFFF0000});
  c.resolver.rows.add(new Object[]{2L,100L,200L,"missing",1,"","source",null});
  c.resolver.rows.add(new Object[]{3L,100L,200L,"black",0,"","source",0});
  result=c.listInstances(0,300,null,10);
  if(result.size()!=3||result.get(0).calendarColor!=0xFFFF0000||result.get(1).calendarColor!=null||result.get(2).calendarColor!=0||!result.get(1).allDay)throw new AssertionError("provider source color/null/black/all-day");
  System.out.println("Production instance query passed: CALENDAR_COLOR projection, null and black source colors");
 }
}'''
paths = []
for name, content in files.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    paths.append(path)
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), *map(str, paths)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.AgendaFeaturesCheck'], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.calendar.ReminderProviderCheck'], check=True)
queries = subprocess.check_output([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.SqlCheck'], text=True)
db = sqlite3.connect(':memory:')
db.execute('CREATE TABLE event_candidates(id INTEGER PRIMARY KEY,start_at_millis INTEGER,end_at_millis INTEGER,all_day INTEGER,needs_confirmation INTEGER)')
db.executemany('INSERT INTO event_candidates VALUES(?,?,?,?,?)', [(1,8000000,12000000,0,0),(2,9000000,13000000,0,0),(3,11000000,12000000,0,0),(4,11500000,12000000,0,0),(5,10500000,12000000,0,1),(6,10500000,12000000,1,0),(7,None,None,0,0),(8,9999999,None,0,0)])
db.executemany('INSERT INTO event_candidates VALUES(?,?,?,?,?)', [(i,1,2,0,0) for i in range(100,6200)])
expected = [[1,2,8,5,6,3,4], [8], [3], [2], [3]]
for line, wanted in zip(queries.splitlines(), expected, strict=True):
    tag, selection, arguments, sort, limit = line.split('\t')
    rows = [r[0] for r in db.execute(f'SELECT id FROM event_candidates WHERE {selection} ORDER BY {sort} LIMIT {limit}', arguments.split('|'))]
    assert rows == wanted, (rows, wanted, selection)
print('Production SQLite checks passed: source eligibility, point events, active/future, hidden and large history')
