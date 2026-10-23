"""Exercise production homework upserts and durable provider repair using real SQLite.

Reuses the existing SQLite bridge/adapters; calendar provider responses are isolated fixtures.
Requires the existing debug Java classes and cached JSON dependency, downloads nothing.
"""
from recurrence_dependencies import recurrence_java

from pathlib import Path
import ast
import os
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/zju-homework-update-check'
src = out / 'src'
src.mkdir(parents=True, exist_ok=True)

def write(path, text):
    file = src / path
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(text, encoding='utf-8')

# Reuse literal adapters rather than rerunning unrelated merge tests.
adapter_source = ast.parse((root / 'checks/candidate_merge_check.py').read_text(encoding='utf-8'))
needed = {'android/content/Context.java', 'android/content/ContentValues.java', 'android/database/Cursor.java',
          'android/database/sqlite/SQLiteDatabase.java', 'android/database/sqlite/SQLiteOpenHelper.java',
          'com/donglan/chrona/debug/DiagLog.java', 'com/donglan/chrona/image/ImageStore.java',
          'com/donglan/chrona/AgendaWidgetProvider.java', 'com/donglan/chrona/AgendaReminder.java',
          'com/donglan/chrona/AndroidSync.java'}
for node in adapter_source.body:
    if isinstance(node, ast.Expr) and isinstance(node.value, ast.Call) and isinstance(node.value.func, ast.Name) and node.value.func.id == 'write':
        call = node.value
        if isinstance(call.args[0], ast.Constant) and call.args[0].value in needed:
            text = ast.literal_eval(call.args[1])
            if call.args[0].value == 'android/content/Context.java':
                text = text.replace('public class Context {', '''public class Context {
 public static final int MODE_PRIVATE=0;
 private final java.util.Map<String,SharedPreferences> preferences=new java.util.HashMap<>();
 public SharedPreferences getSharedPreferences(String name,int mode){return preferences.computeIfAbsent(name,key->new SharedPreferences());}
''')
            write(call.args[0].value, text)
write('android/util/Log.java', 'package android.util;public class Log {public static int w(String a,String b,Throwable c){return 0;}}')
# Use the real completion coordinator for removed deadlines and shared reminders.
# Only its platform/preferences/unused repeating-action adapters are reused.
completion_tree = ast.parse((root / 'checks/completion_alerts_check.py').read_text(encoding='utf-8'))
completion_sources = next(ast.literal_eval(node.value) for node in completion_tree.body
                          if isinstance(node, ast.Assign) and any(isinstance(target, ast.Name)
                          and target.id == 'sources' for target in node.targets))
for name in ('android/content/SharedPreferences.java', 'com/donglan/chrona/AgendaReminderPreferences.java',
             'com/donglan/chrona/RepeatActions.java'):
    write(name, completion_sources[name])
# Remove the obsolete generated no-op so a previous run cannot shadow production.
(src / 'com/donglan/chrona/TaskCompletion.java').unlink(missing_ok=True)
write('com/donglan/chrona/calendar/CalendarStore.java', '''package com.donglan.chrona.calendar;
import android.content.Context;import java.util.*;
public class CalendarStore {
 public static Map<Long,EventRecord> rows=new HashMap<>();public static boolean permission=true,fail;public static int updates;
 public CalendarStore(Context c){}public boolean hasReadPermission(){return permission;}public boolean hasWritePermission(){return permission;}
 public EventRecord getEvent(long id){if(fail)throw new IllegalStateException("provider down");EventRecord r=rows.get(id);return r!=null&&r.owned?r:null;}
 public long insertEvent(EventInput input){if(fail)throw new IllegalStateException("provider down");long id=1000;while(rows.containsKey(id))id++;rows.put(id,new EventRecord(input,true));return id;}
 public boolean deleteEvent(long id){return rows.remove(id)!=null;}
 public List<Long> findMatchingEvents(EventInput input){if(fail)throw new IllegalStateException("provider down");List<Long> ids=new ArrayList<>();for(var e:rows.entrySet()){EventInput i=e.getValue().input;if(e.getValue().owned&&i.title.equals(input.title)&&i.startMillis==input.startMillis&&i.endMillis==input.endMillis&&i.allDay==input.allDay)ids.add(e.getKey());}return ids;}
 public boolean updateEvent(long id,EventInput input){if(fail)throw new IllegalStateException("provider down");EventRecord old=getEvent(id);if(old==null)return false;updates++;rows.put(id,new EventRecord(input,true));return true;}
 public boolean updateDescription(long id,String description){EventRecord old=getEvent(id);if(old==null)return false;old.description=description;old.input.description=description;return true;}
 public boolean replaceReminders(long id,List<Integer> reminders){EventRecord old=getEvent(id);if(old==null)return false;old.reminderMinutes=new ArrayList<>(reminders);return true;}
 public static class EventRecord {public EventInput input;public boolean owned,allDay;public String title,timeZoneId,description,location;public long startMillis,endMillis;public List<Integer> reminderMinutes;public EventRecord(EventInput input,boolean owned){this.input=input;this.owned=owned;this.reminderMinutes=new ArrayList<>(input.reminderMinutes);description=input.description;location=input.location;title=input.title;timeZoneId=input.timeZoneId;startMillis=input.startMillis;endMillis=input.endMillis;allDay=input.allDay;}}
 public static class EventInput {public String title,description,location,timeZoneId;public long startMillis,endMillis;public boolean allDay;public List<Integer> reminderMinutes;
 public EventInput(String title,long start,long end,String zone,boolean day,String desc,String loc,List<Integer> reminders){if(end<=start)throw new IllegalArgumentException();this.title=title;startMillis=start;endMillis=end;timeZoneId=zone;allDay=day;description=desc;location=loc;reminderMinutes=new ArrayList<>(reminders);}
 public EventInput withRepeat(EventRecord record){return this;}}
}''')
classes = root / 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
json_jar = root / 'build/ai-checks/json.jar'
classpath = os.pathsep.join(map(str, (json_jar, classes)))
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
production = root / 'app/src/main/java/com/donglan/chrona'
sources = list(src.rglob('*.java')) + [production / name for name in
    ('data/TaskStore.java', 'data/EventCandidate.java', 'data/EventCategory.java', 'data/TaskRecord.java', 'data/ScheduleQuery.java',
     'zju/ZjuSnapshot.java', 'ZjuHomeworkStore.java', 'ZjuHomeworkCalendar.java', 'HomeworkLinks.java',
     'TaskCompletion.java', 'CandidateEditBaseline.java', 'AgendaAcknowledgement.java', 'calendar/LinkedCalendarEvents.java')]
sources += [root / 'checks/ZjuHomeworkUpdateCheck.java']
subprocess.run(recurrence_java([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', classpath, '-d', str(out), *map(str, sources)]), check=True)
subprocess.run(recurrence_java([str(java / 'java.exe'), f'-Dpython={shutil.which("python")}', f'-Dbridge={root / "checks/sqlite_bridge.py"}',
                '-cp', str(out) + os.pathsep + classpath, 'com.donglan.chrona.ZjuHomeworkUpdateCheck', str(out / 'databases')]), check=True)
