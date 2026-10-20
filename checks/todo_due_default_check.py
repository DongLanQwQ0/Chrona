"""Offline model/home projections and actual TaskStore SQL; no Android UI or Provider claims."""
import base64
import os
from pathlib import Path
import sqlite3
import subprocess
from recurrence_dependencies import recurrence_java

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / 'app/src/main/java/com/donglan/chrona'
OUT = ROOT / 'build/todo-due-default-check'
OUT.mkdir(parents=True, exist_ok=True)
JAVA = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'


def method(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


store = (SRC / 'data/TaskStore.java').read_text(encoding='utf-8')
methods = '\n'.join(method(store, signature) for signature in (
    'public List<EventCandidate> calendarCandidates(',
    'public List<EventCandidate> widgetCandidates(long begin, long end)',
    'public List<EventCandidate> widgetCandidates(long begin, long end,',
    'public List<EventCandidate> queryHomeUpcoming('))
sql_java = '''package com.donglan.chrona.data;
import java.util.*;import java.time.*;
class Cursor implements AutoCloseable {public boolean moveToNext(){return false;}public void close(){}}
class Database {
 Cursor query(String t,Object c,String where,String[] args,Object g,Object h,String order,String limit){
  Base64.Encoder e=Base64.getEncoder();
  System.out.println("SQL\\t"+e.encodeToString(where.getBytes(java.nio.charset.StandardCharsets.UTF_8))+"\\t"+String.join("|",args)+"\\t"+e.encodeToString(order.getBytes(java.nio.charset.StandardCharsets.UTF_8)));return new Cursor();}
}
public class DueSqlCheck {
 static final int WIDGET_ITEM_LIMIT=200;static final long ALL_DAY_DURATION_MILLIS=86400000;
 Database getReadableDatabase(){return new Database();}EventCandidate readCandidate(Cursor c){return null;}
 List<EventCandidate> repeatingCandidates(){return List.of();}
 List<EventCandidate> mergeRepeats(List<EventCandidate> a,long b,long c,long d,long e,int l){return a;}
''' + methods + '''
 public static void main(String[] args){
  DueSqlCheck c=new DueSqlCheck();
  c.calendarCandidates(10000000,20000000,40000000,50000000);
  c.widgetCandidates(10000000,20000000);
  c.widgetCandidates(10000000,20000000,40000000,50000000);
  c.queryHomeUpcoming(10000000,ZoneId.of("UTC"),200);
  ScheduleQuery q=new ScheduleQuery(3,null,0,"",LocalDate.of(1970,1,1),LocalDate.of(1970,1,2),10000000,ZoneId.of("UTC"));
  c.getReadableDatabase().query("event_candidates",null,q.selection,q.arguments,null,null,q.orderBy(true),"200");
 }
}'''
sql_path = OUT / 'DueSqlCheck.java'
sql_path.write_text(sql_java, encoding='utf-8')
model_path = OUT / 'DueModelCheck.java'
model_path.write_text(r'''package com.donglan.chrona;
import com.donglan.chrona.data.*;import java.time.*;import java.util.*;
public class DueModelCheck {
 static void yes(boolean ok,String why){if(!ok)throw new AssertionError(why);}
 static EventCandidate item(String c,Long s,Long e,Long d,boolean record,boolean all,Long done){
  return new EventCandidate(1,2,"todo",s,e,"Asia/Shanghai","","",10,false,null,c,all,false,0,d,done,record);
 }
 public static void main(String[] args){
  ZoneId z=ZoneId.of("Asia/Shanghai");
  long due=LocalDate.of(2026,10,21).atTime(0,10).atZone(z).toInstant().toEpochMilli();
  EventCandidate t=item("task",null,null,due,false,false,null);
  yes(t.hasDefaultExecutionInterval()&&t.executionStartAtMillis()==due-1800000&&t.executionEndAtMillis()==due,"30 minute interval");
  yes(!t.point()&&t.startAtMillis==null&&t.endAtMillis==null&&t.anchor()==due&&t.reminderAnchor()==due,"source and reminder identity intact");
  yes(t.calendarStart()==due&&t.calendarEnd()==due+60000,"Provider write contract unchanged");
  yes(new HomeTimelineEntry(t).timestampMillis==due-1800000,"home begins before midnight");
  yes(HomeTimelineEntry.hasUpcomingTime(t,due-1,z)&&!HomeTimelineEntry.hasUpcomingTime(t,due,z),"active until exact end");
  yes(HomeTimelineEntry.executionEnd(t)==due,"home ends at due");
  EventCandidate a=item("task",null,null,due,false,true,null);
  yes(a.allDay&&!a.executionAllDay()&&new HomeTimelineEntry(a).timestampMillis==due-1800000,"raw all-day preserved, projection timed");
  for(EventCandidate n:List.of(item("task",due,null,due,false,false,null),item("task",null,due,due,false,false,null),item("task",due-10,due,due,false,false,null),item("task",null,null,null,false,false,null),item("homework",null,null,due,false,false,null),item("event",null,null,due,false,false,null),item("task",null,null,due,true,false,null)))yes(!n.hasDefaultExecutionInterval(),"negative default guard");
  EventCandidate h=item("homework",null,null,due,false,false,null);
  yes(h.point()&&h.calendarStart()==due&&h.calendarEnd()==due+60000&&new HomeTimelineEntry(h).timestampMillis==due,"homework remains deadline point");
  yes(!HomeTimelineEntry.hasUpcomingTime(item("task",null,null,due,false,false,1L),due-1,z),"completed excluded");
  yes(item("task",null,null,due+1000,false,false,null).executionStartAtMillis()==due+1000-1800000,"due edits recompute");
  yes(item("task",null,null,null,false,false,null).executionStartAtMillis()==null,"due cleared removes interval");
  EventCandidate s=t.withRepeat(new RepeatRule("daily",1,List.of(),null,3));
  long midnight=LocalDate.of(2026,10,21).atStartOfDay(z).toInstant().toEpochMilli();
  List<EventCandidate> xs=RepeatOccurrences.between(s,midnight-3600000,midnight,10);
  yes(xs.size()==1&&xs.get(0).occurrenceAtMillis==due&&xs.get(0).executionStartAtMillis()==due-1800000,"query ending before due includes preceding interval");
  EventCandidate moved=s.withRepeatState("{\""+due+"\":{\"start\":"+(due+86400000)+"}}",null);
  EventCandidate e=RepeatOccurrences.at(moved,due);
  yes(e.startAtMillis==null&&e.endAtMillis==null&&e.dueAtMillis==due+86400000&&e.occurrenceAtMillis==due,"repeat move preserves raw null and original identity");
  EventCandidate stopped=s.withRepeatState("{}",due-600000);
  yes(RepeatOccurrences.at(stopped,due)!=null,"already-started interval survives stop");
  ScheduleQuery q=new ScheduleQuery(3,null,0,"",LocalDate.of(2026,10,20),LocalDate.of(2026,10,21),due-3600000,z);
  yes(q.seriesRow(s)!=null,"series date window uses interval, not due anchor");
  long boundary=LocalDate.of(2026,10,21).atStartOfDay(z).toInstant().toEpochMilli();
  EventCandidate midnightTask=item("task",null,null,boundary,false,false,null)
    .withRepeat(new RepeatRule("daily",1,List.of(),null,3));
  ScheduleQuery dueToday=new ScheduleQuery(3,null,0,"",null,null,boundary+3600000,z,ScheduleQuery.TODO_TODAY_DUE);
  yes(dueToday.seriesRow(midnightTask).dueAtMillis==boundary,"today deadline scope includes exact midnight");
  System.out.println("Due default model/home/repeat checks passed");
 }
}''', encoding='utf-8')
sources = [sql_path, model_path, SRC/'data/ScheduleQuery.java', SRC/'HomeTimelineEntry.java',
           SRC/'calendar/AllDayDates.java', SRC/'calendar/CalendarOccurrence.java']
subprocess.run(recurrence_java([str(JAVA/'javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                              '-d', str(OUT), *map(str, sources)]), check=True)
subprocess.run(recurrence_java([str(JAVA/'java.exe'), '-cp', str(OUT), 'com.donglan.chrona.DueModelCheck']), check=True)
output = subprocess.check_output(recurrence_java([str(JAVA/'java.exe'), '-cp', str(OUT), 'com.donglan.chrona.data.DueSqlCheck']), text=True)
queries = []
for line in output.splitlines():
    if line.startswith('SQL\t'):
        _, where, args, order = line.split('\t')
        queries.append((base64.b64decode(where).decode(), args.split('|'), base64.b64decode(order).decode()))
db = sqlite3.connect(':memory:')
db.execute('CREATE TABLE event_candidates(id INTEGER PRIMARY KEY,category TEXT,all_day INTEGER DEFAULT 0,start_at_millis INTEGER,end_at_millis INTEGER,due_at_millis INTEGER,is_record INTEGER DEFAULT 0,completed_at_millis INTEGER,repeat_rule TEXT)')
for id_, category, start, end, due, all_day, record, done in [
    (1,'task',None,None,20_100_000,0,0,None), # starts inside, due outside
    (2,'homework',None,None,20_100_000,0,0,None),
    (3,'task',None,None,20_100_000,1,0,None), # timed despite raw all_day
    (4,'task',None,None,20_100_000,0,1,None),
    (5,'task',None,None,20_100_000,0,0,1),
    (6,'task',None,None,None,0,0,None),
    (7,'task',None,None,9_999_999,0,0,None),
    (8,'task',19_000_000,21_000_000,50_000_000,0,0,None),
    (9,'task',None,21_000_000,20_100_000,0,0,None),
]:
    db.execute('INSERT INTO event_candidates(id,category,start_at_millis,end_at_millis,due_at_millis,all_day,is_record,completed_at_millis) VALUES(?,?,?,?,?,?,?,?)',(id_,category,start,end,due,all_day,record,done))
for where, args, order in queries[:3]:
    ids = [r[0] for r in db.execute('SELECT id FROM event_candidates WHERE '+where+' ORDER BY '+order,args)]
    assert set(ids)=={1,3,8}, ids
where, args, order = queries[3]
ids = [r[0] for r in db.execute('SELECT id FROM event_candidates WHERE '+where+' ORDER BY '+order,args)]
assert set(ids)=={1,2,3,8}, ids
db.execute('DELETE FROM event_candidates')
for id_,category,due,all_day in [(1,'task',87_000_000,0),(2,'task',87_000_000,1),(3,'homework',87_000_000,0),
                                (4,'task',88_200_000,0),(5,'task',0,0)]:
    db.execute('INSERT INTO event_candidates(id,category,due_at_millis,all_day) VALUES(?,?,?,?)',(id_,category,due,all_day))
where,args,order=queries[4]
ids=[r[0] for r in db.execute('SELECT id FROM event_candidates WHERE '+where+' ORDER BY '+order,args)]
assert set(ids)=={1,2}, ids
print('Due default actual SQL checks passed: calendar, both widget overloads, home; raw-null, exclusions and interval ordering')
