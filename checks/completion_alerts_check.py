"""Exercise production completion orchestration with isolated calendar/preference adapters."""
from recurrence_dependencies import recurrence_java

import os
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
out = root / 'build/completion-alerts-check'
out.mkdir(parents=True, exist_ok=True)
sources = {
'com/donglan/chrona/RepeatActions.java': '''package com.donglan.chrona;class RepeatActions {static void change(android.content.Context c,long task,long id,String baseline,String action,Long occurrence,com.donglan.chrona.data.EventCandidate edited,Long now){throw new AssertionError("Non-repeating fixture must not call series actions");}}''',
'android/content/Context.java': '''package android.content;
public class Context {public static final int MODE_PRIVATE=0;public SharedPreferences prefs=new SharedPreferences();public SharedPreferences getSharedPreferences(String key,int mode){return prefs;}}''',
'android/content/SharedPreferences.java': '''package android.content;
import java.util.*;
public class SharedPreferences {Map<String,Object> values=new HashMap<>();public Set<String> getStringSet(String k,Set<String> fallback){return (Set<String>)values.getOrDefault(k,fallback);}public boolean getBoolean(String k,boolean fallback){return (Boolean)values.getOrDefault(k,fallback);}public boolean contains(String k){return values.containsKey(k);}public String getString(String k,String fallback){return (String)values.getOrDefault(k,fallback);}public long getLong(String k,long fallback){return (Long)values.getOrDefault(k,fallback);}public Editor edit(){return new Editor();}public class Editor {Map<String,Object> next=new HashMap<>();Set<String> gone=new HashSet<>();public Editor putLong(String k,long v){next.put(k,v);return this;}public Editor putStringSet(String k,Set<String> v){next.put(k,new HashSet<>(v));return this;}public Editor putBoolean(String k,boolean v){next.put(k,v);return this;}public Editor putString(String k,String v){next.put(k,v);return this;}public Editor remove(String k){gone.add(k);return this;}public void apply(){commit();}public boolean commit(){for(String k:gone)values.remove(k);values.putAll(next);return true;}}}''',
'android/database/Cursor.java': '''package android.database;public class Cursor implements AutoCloseable {java.util.Iterator<Long> ids;long current;public Cursor(java.util.Collection<Long> values){ids=values.iterator();}public boolean moveToNext(){if(!ids.hasNext())return false;current=ids.next();return true;}public long getLong(int i){return current;}public void close(){}}''',
'android/database/sqlite/SQLiteDatabase.java': '''package android.database.sqlite;
import java.util.*;import com.donglan.chrona.data.*;
public class SQLiteDatabase {
public static boolean failCommit;private Map<Long,EventCandidate> snapshot;private long revision;private boolean success;
public void beginTransaction(){if(snapshot!=null)throw new IllegalStateException("unexpected nested transaction");snapshot=new HashMap<>(TaskStore.rows);revision=TaskStore.revision;success=false;}
public void setTransactionSuccessful(){if(snapshot==null)throw new IllegalStateException("no transaction");success=true;}
public void endTransaction(){if(snapshot==null)throw new IllegalStateException("no transaction");boolean failed=success&&failCommit;if(!success||failed){TaskStore.rows.clear();TaskStore.rows.putAll(snapshot);TaskStore.revision=revision;}snapshot=null;if(failed)throw new IllegalStateException("injected SQLite commit failure");}
public android.database.Cursor rawQuery(String sql,String[] args){List<Long> ids=new ArrayList<>();for(var e:TaskStore.rows.values())if(e.completed()&&e.calendarEventId!=null)ids.add(e.id);return new android.database.Cursor(ids);}
}''',
'android/util/Log.java': '''package android.util;public class Log {public static int w(String tag,String message,Throwable error){return 0;}}''',
'com/donglan/chrona/AgendaReminderPreferences.java': '''package com.donglan.chrona;class AgendaReminderPreferences {static android.content.SharedPreferences prefs(android.content.Context c){return c.getSharedPreferences("agenda_reminder",0);}}''',
'com/donglan/chrona/AndroidSync.java': '''package com.donglan.chrona;class AndroidSync {static final Object LOCK=new Object();}''',
'com/donglan/chrona/AgendaReminder.java': '''package com.donglan.chrona;class AgendaReminder {static int refreshes;static void requestRefresh(android.content.Context c){refreshes++;}}''',
'com/donglan/chrona/data/TaskStore.java': '''package com.donglan.chrona.data;
import java.util.*;import android.content.Context;
public class TaskStore implements AutoCloseable {
public static Map<Long,EventCandidate> rows=new HashMap<>();public static long revision;
private final android.database.sqlite.SQLiteDatabase db=new android.database.sqlite.SQLiteDatabase();
public TaskStore(Context c){}public android.database.sqlite.SQLiteDatabase getReadableDatabase(){return db;}public android.database.sqlite.SQLiteDatabase getWritableDatabase(){return db;}
public long dataRevision(){return revision;}
private static EventCandidate copy(EventCandidate p,Long event,Long completed){return new EventCandidate(p.id,p.taskId,p.title,p.startAtMillis,p.endAtMillis,p.timeZoneId,p.location,p.description,p.reminderMinutesBefore,p.needsConfirmation,event,p.category,p.allDay,p.endAutoGenerated,p.uncertaintyLevel,p.dueAtMillis,completed,p.isRecord,p.repeatRule,p.repeatExceptions,p.repeatStoppedAtMillis,p.occurrenceAtMillis);}
public boolean setCandidateCompleted(long id,long taskId,boolean done){EventCandidate p=rows.get(id);if(p==null||p.taskId!=taskId||!p.isTask())return false;rows.put(id,copy(p,p.calendarEventId,done?System.currentTimeMillis():null));revision++;return true;}
public boolean hasOtherCalendarReferences(long event,Collection<Long> excluding){for(var p:rows.values())if(Objects.equals(p.calendarEventId,event)&&!excluding.contains(p.id))return true;return false;}
public boolean replaceCalendarEventId(long id,long task,long expected,Long replacement){EventCandidate p=rows.get(id);if(p==null||p.taskId!=task||!Objects.equals(p.calendarEventId,expected))return false;rows.put(id,copy(p,replacement,p.completedAtMillis));revision++;return true;}
public boolean setCalendarEventIdIfUnlinked(long id,long task,long event){EventCandidate p=rows.get(id);if(p==null||p.taskId!=task||p.calendarEventId!=null)return false;rows.put(id,copy(p,event,p.completedAtMillis));revision++;return true;}
public List<EventCandidate> getCandidatesByIds(Collection<Long> ids){List<EventCandidate> result=new ArrayList<>();for(Long id:ids)if(rows.containsKey(id))result.add(rows.get(id));return result;}
public int clearMissingCalendarLinks(List<Long> ids){int count=0;for(var p:new ArrayList<>(rows.values()))if(ids.contains(p.calendarEventId)){rows.put(p.id,copy(p,null,p.completedAtMillis));count++;}return count;}
public void close(){}
}''',
'com/donglan/chrona/calendar/CalendarStore.java': '''package com.donglan.chrona.calendar;
import java.util.*;import android.content.Context;
public class CalendarStore {
public static boolean permission=true,fail=false;public static final Map<Long,EventRecord> events=new HashMap<>();public static final List<Long> deleted=new ArrayList<>();
public CalendarStore(Context c){}public boolean hasReadPermission(){return permission;}public boolean hasWritePermission(){return permission;}public EventRecord getEvent(long id){return events.get(id);}
public long insertEvent(EventInput input){if(fail)throw new IllegalStateException("provider unavailable");long id=100;while(events.containsKey(id))id++;events.put(id,new EventRecord(input));return id;}
public boolean updateEvent(long id,EventInput input){if(fail)throw new IllegalStateException("provider unavailable");if(!events.containsKey(id))return false;events.put(id,new EventRecord(input));return true;}
public boolean deleteEvent(long id){deleted.add(id);return events.remove(id)!=null;}
public boolean updateDescription(long id,String text){EventRecord e=getEvent(id);if(e==null)return false;e.description=text;return true;}
public boolean replaceReminders(long id,List<Integer> alerts){if(fail)throw new IllegalStateException("provider unavailable");EventRecord e=events.get(id);if(e==null)return false;e.reminderMinutes=new ArrayList<>(alerts);return true;}
public static class EventRecord {
public String title="provider title",timeZoneId="Asia/Shanghai",description="provider description",location="provider location";public boolean allDay;public long startMillis,endMillis;public List<Integer> reminderMinutes;
public EventRecord(long start,List<Integer> reminders){startMillis=start;endMillis=start+60000;reminderMinutes=new ArrayList<>(reminders);}
public EventRecord(EventInput i){this(i.startMillis,i.reminderMinutes);title=i.title;endMillis=i.endMillis;timeZoneId=i.timeZoneId;allDay=i.allDay;description=i.description;location=i.location;}}
public static class EventInput {
public String title,timeZoneId,description,location;public boolean allDay;public long startMillis,endMillis;public List<Integer> reminderMinutes;
public EventInput(String title,long start,long end,String zone,boolean day,String description,String location,List<Integer> reminders){this.title=title;startMillis=start;endMillis=end;timeZoneId=zone;allDay=day;this.description=description;this.location=location;reminderMinutes=new ArrayList<>(reminders);}
public EventInput withRepeat(EventRecord record){return this;}}
}''',
'com/donglan/chrona/CompletionCheck.java': '''package com.donglan.chrona;
import android.content.Context;import com.donglan.chrona.data.*;import com.donglan.chrona.calendar.CalendarStore;import java.util.*;
public class CompletionCheck {
__ACKNOWLEDGEMENT_UNTIL__
static int checks;static void check(boolean yes,String why){checks++;if(!yes)throw new AssertionError(why);}
public static void main(String[] args){Context c=new Context();long future=System.currentTimeMillis()+3600000;
EventCandidate item=new EventCandidate(1,2,"待办",future,null,"Asia/Shanghai","","",10,false,7L,"task",false,false,0,future,null,false);TaskStore.rows.put(1L,item);
CalendarStore.events.put(7L,new CalendarStore.EventRecord(future,List.of(10,30)));CalendarStore.events.put(8L,new CalendarStore.EventRecord(future,List.of(5)));
check(TaskCompletion.set(c,1,2,true)==null,"complete success");check(TaskStore.rows.get(1L).completed(),"local completed");check(CalendarStore.events.get(7L).reminderMinutes.isEmpty(),"linked pending alerts disabled");check(CalendarStore.events.get(8L).reminderMinutes.equals(List.of(5)),"unrelated event untouched");
String key=AgendaAcknowledgement.key(item);AgendaAcknowledgement.set(c,key,future,true);
c.prefs.edit().putLong("snooze_until:"+key,future).putLong("snooze_end:"+key,future+1000).apply();
check(AgendaAcknowledgement.isAcknowledged(c,key),"hidden occurrence acknowledged");
check(TaskCompletion.set(c,1,2,false)==null,"restore success");
check(!AgendaAcknowledgement.isAcknowledged(c,key)&&!c.prefs.contains("snooze_until:"+key)&&!c.prefs.contains("snooze_end:"+key),"restore clears current occurrence hidden and snooze");check(CalendarStore.events.get(7L).reminderMinutes.equals(List.of(10,30)),"original future alerts restored");
CalendarStore.permission=false;check(TaskCompletion.set(c,1,2,true)!=null,"permission warning");check(TaskStore.rows.get(1L).completed(),"completion not blocked by calendar permission");CalendarStore.permission=true;TaskCompletion.retryPending(c);check(CalendarStore.events.get(7L).reminderMinutes.isEmpty(),"pending permission repair retried");
CalendarStore.fail=true;check(TaskCompletion.set(c,1,2,false)!=null,"provider failure visible");check(!TaskStore.rows.get(1L).completed(),"restore local state kept");CalendarStore.fail=false;TaskCompletion.retryPending(c);check(CalendarStore.events.get(7L).reminderMinutes.equals(List.of(10,30)),"provider recovery retains original alerts");
TaskCompletion.set(c,1,2,true);CalendarStore.events.get(7L).startMillis=System.currentTimeMillis()-60000;TaskCompletion.set(c,1,2,false);check(CalendarStore.events.get(7L).reminderMinutes.isEmpty(),"expired alerts not replayed");
long revision=TaskStore.revision;try{TaskCompletion.set(c,1,2,true,revision-1);throw new AssertionError("stale edit accepted");}catch(ConcurrentModificationException expected){check(TaskStore.revision==revision,"stale revision cannot mutate");}
EventCandidate event=new EventCandidate(10,2,"活动",future,future+3600000,"Asia/Shanghai","","",10,true);
check(acknowledgementUntil(event)==future+3600000,"explicit end retained");
for(Long end:new Long[]{null,future,future-1000}) {
EventCandidate point=new EventCandidate(11,2,"活动",future,end,"Asia/Shanghai","","",10,false);
check(acknowledgementUntil(point)==future+60000,"missing or non-positive interval gets minute window");
}
EventCandidate noTime=new EventCandidate(12,2,"活动",null,null,"Asia/Shanghai","","",null,false);
check(acknowledgementUntil(noTime)==0L,"missing anchor unavailable");
EventCandidate expired=new EventCandidate(13,2,"活动",future-7200000,future-3600000,"Asia/Shanghai","","",10,false);
check(acknowledgementUntil(expired)<=System.currentTimeMillis(),"expired activity unavailable");
String eventKey=AgendaAcknowledgement.key(event),other=AgendaAcknowledgement.key(44,future+86400000);
check(eventKey.equals("chrona:10:"+future),"candidate occurrence key");
AgendaAcknowledgement.set(c,other,future+86400000,true);
long rev=c.prefs.getLong("acknowledgement_revision",0);int refresh=AgendaReminder.refreshes;
AgendaAcknowledgement.set(c,eventKey,future,true);
check(AgendaAcknowledgement.isAcknowledged(c,eventKey)&&!event.completed()&&event.needsConfirmation,"ack separate from completion and review");
check(c.prefs.getLong("acknowledgement_revision",0)==rev+1&&AgendaReminder.refreshes==refresh+1,"ack revision and refresh");
AgendaAcknowledgement.set(c,eventKey,0,false);
check(!AgendaAcknowledgement.isAcknowledged(c,eventKey)&&AgendaAcknowledgement.isAcknowledged(c,other),"cancel scopes one occurrence");
AgendaAcknowledgement.set(c,eventKey,System.currentTimeMillis()-1,true);
check(!AgendaAcknowledgement.isAcknowledged(c,eventKey),"expired hidden is unacknowledged");
rev=c.prefs.getLong("acknowledgement_revision",0);AgendaAcknowledgement.set(c,"",future,true);
check(!AgendaAcknowledgement.isAcknowledged(c,"")&&rev==c.prefs.getLong("acknowledgement_revision",0),"empty keys have no state");
check(CalendarStore.events.size()==2,"event history not deleted");
EventCandidate sharedA=new EventCandidate(20,21,"shared A",future,future+60000,"Asia/Shanghai","","",10,false,70L,"task",false,false,0,future,null,false);
EventCandidate sharedB=new EventCandidate(22,23,"shared B",future,future+60000,"Asia/Shanghai","","",10,false,70L,"task",false,false,0,future,null,false);
TaskStore.rows.put(20L,sharedA);TaskStore.rows.put(22L,sharedB);CalendarStore.events.put(70L,new CalendarStore.EventRecord(future,List.of(10,30)));
CalendarStore.EventRecord original=CalendarStore.events.get(70L);
check(TaskCompletion.set(c,20,21,true)==null,"shared completion succeeds");
long split=TaskStore.rows.get(20L).calendarEventId;
check(split!=70&&TaskStore.rows.get(22L).calendarEventId==70L,"shared completion relinks only A");
check(CalendarStore.events.get(split).reminderMinutes.isEmpty(),"only A alerts disabled on new event");
check(CalendarStore.events.get(70L)==original&&original.reminderMinutes.equals(List.of(10,30)),"B original provider alerts untouched");
check(!TaskStore.rows.get(22L).completed(),"B local completion untouched");
check(CalendarStore.events.get(split).title.equals(original.title)&&CalendarStore.events.get(split).description.equals(original.description),"shared completion copies provider fields");
check(TaskCompletion.set(c,20,21,false)==null,"split A can be reopened");
check(CalendarStore.events.get(split).reminderMinutes.equals(List.of(10,30)),"split A original alerts restored");
check(original.reminderMinutes.equals(List.of(10,30)),"reopening A leaves B alerts unchanged");
TaskStore.rows.put(30L,new EventCandidate(30,31,"commit A",future,future+60000,"Asia/Shanghai","","",5,false,80L,"task",false,false,0,future,null,false));
TaskStore.rows.put(32L,new EventCandidate(32,33,"commit B",future,future+60000,"Asia/Shanghai","","",5,false,80L,"task",false,false,0,future,null,false));
CalendarStore.events.put(80L,new CalendarStore.EventRecord(future,List.of(5,15)));int beforeEvents=CalendarStore.events.size();
android.database.sqlite.SQLiteDatabase.failCommit=true;
check(TaskCompletion.set(c,30,31,true)!=null,"completion commit failure reported");
check(TaskStore.rows.get(30L).completed(),"calendar commit failure retains local-first completed state");
check(TaskStore.rows.get(30L).calendarEventId==80L&&TaskStore.rows.get(32L).calendarEventId==80L,"calendar commit failure rolls back relink only");
check(CalendarStore.events.size()==beforeEvents&&CalendarStore.events.get(80L).reminderMinutes.equals(List.of(5,15)),"calendar commit failure removes new event, preserves old alerts");
check(!CalendarStore.deleted.contains(70L)&&!CalendarStore.deleted.contains(80L),"compensation never deletes shared originals");
android.database.sqlite.SQLiteDatabase.failCommit=false;TaskCompletion.retryPending(c);
long repaired=TaskStore.rows.get(30L).calendarEventId;
check(repaired!=80&&CalendarStore.events.get(repaired).reminderMinutes.isEmpty(),"pending commit failure repairs after recovery");
check(CalendarStore.events.get(80L).reminderMinutes.equals(List.of(5,15)),"recovery preserves B reminders");
System.out.println("Completion alert orchestration: "+checks+" checks passed");}}
'''
}
detail = (root / 'app/src/main/java/com/donglan/chrona/TaskDetailActivity.java').read_text(encoding='utf-8')
begin = detail.index('    private static long acknowledgementUntil(')
end = detail.index('\n    }', begin) + len('\n    }')
sources['com/donglan/chrona/CompletionCheck.java'] = sources['com/donglan/chrona/CompletionCheck.java'].replace('__ACKNOWLEDGEMENT_UNTIL__', detail[begin:end])
assert '&& acknowledgementUntil(candidate) > System.currentTimeMillis()' in detail
assert 'if (!key.isEmpty() && until > System.currentTimeMillis())' in detail
assert detail.index('cardHeader.addView(acknowledgement, acknowledgementParams)') < detail.index('candidateActions.addView(removeCandidate,')
assert 'action.setContentDescription(confirmed ? "取消确认活动" : "确认活动")' in detail
assert 'complete.setOnClickListener(view -> changeTaskCompletion(candidate))' in detail
assert detail.index('cardHeader.addView(createCandidate,') < detail.index('cardHeader.addView(complete,') < detail.index('candidateActions.addView(removeCandidate,')
assert 'new LinearLayout.LayoutParams(dp(48), dp(48))' in detail
assert 'candidate.isTask()) button(card,candidate.completed()' not in detail
system_detail = (root / 'app/src/main/java/com/donglan/chrona/SystemAgendaDetailActivity.java').read_text(encoding='utf-8')
assert 'long remaining = acknowledgementUntil - System.currentTimeMillis();' in system_detail
assert 'boolean available = occurrenceAvailable && !getIntent().getBooleanExtra("source_series", false)\n                    && remaining > 0;' in system_detail
assert 'acknowledgement.setEnabled(available)' in system_detail
assert 'acknowledgement.setVisibility(available ? android.view.View.VISIBLE : android.view.View.GONE)' in system_detail
assert 'if (resumed && available) expiryHandler.postDelayed(expiryRefresh, remaining)' in system_detail
assert 'if (!occurrenceAvailable || acknowledgementUntil <= System.currentTimeMillis())' in system_detail
paths = []
sources['com/donglan/chrona/data/TaskStore.java'] = sources['com/donglan/chrona/data/TaskStore.java'].replace(
    'public static long revision;',
    'public static long revision;public static boolean sourceEnabled=true;public boolean allowsReminder(long id){return sourceEnabled;}')
sources['com/donglan/chrona/CompletionCheck.java'] = sources['com/donglan/chrona/CompletionCheck.java'].replace(
    'check(CalendarStore.events.size()==2,"event history not deleted");',
    '''CalendarStore.events.get(7L).startMillis=future;CalendarStore.events.get(7L).reminderMinutes=new ArrayList<>(List.of(5,10));TaskStore.sourceEnabled=false;TaskCompletion.set(c,1,2,false);
check(CalendarStore.events.get(7L).reminderMinutes.isEmpty(),"disabled school source suppresses linked alerts without completing item");
check(!TaskStore.rows.get(1L).completed(),"source off keeps task unfinished");
TaskStore.sourceEnabled=true;TaskCompletion.enqueue(c,1);TaskCompletion.retryPending(c);
check(CalendarStore.events.get(7L).reminderMinutes.equals(List.of(5,10)),"source reenable restores original future reminders");
check(CalendarStore.events.size()==2,"event history not deleted");''')
for name, text in sources.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding='utf-8')
    paths.append(path)
src = root / 'app/src/main/java/com/donglan/chrona'
paths += [src / 'CandidateEditBaseline.java', src / 'TaskCompletion.java', src / 'AgendaAcknowledgement.java', src / 'calendar/LinkedCalendarEvents.java', src / 'data/EventCandidate.java', src / 'data/EventCategory.java']
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
cp = str(root / 'build/ai-checks/json.jar')
subprocess.run(recurrence_java([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', cp, '-d', str(out), *map(str, paths)]), check=True)
subprocess.run(recurrence_java([str(java / 'java.exe'), '-cp', str(out) + os.pathsep + cp, 'com.donglan.chrona.CompletionCheck']), check=True)
