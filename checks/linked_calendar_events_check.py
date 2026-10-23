"""Run production linked-event writes and exact TaskStore SQL against real SQLite.

Only the provider transport and Android database transport are replaced. The
production helper, candidate model, TaskStore methods, and provider input/record
copy logic are compiled unchanged. No device, account, or network is used.
"""
from pathlib import Path
import ast
import os
import subprocess
import sys
import textwrap
from recurrence_dependencies import recurrence_classpath

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/linked-calendar-events-check'
SRC = OUT / 'src'
PRODUCTION = ROOT / 'app/src/main/java/com/donglan/chrona'
JAVA = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
SRC.mkdir(parents=True, exist_ok=True)


def write(path, source):
    target = SRC / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(textwrap.dedent(source), encoding='utf-8')


def member(source, signature):
    start = source.index(signature)
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


# Reuse the established SQLite transport without importing/running its suite.
adapters = {
    'android/content/Context.java', 'android/content/ContentValues.java',
    'android/database/Cursor.java', 'android/database/sqlite/SQLiteDatabase.java',
}
tree = ast.parse((ROOT / 'checks/candidate_merge_check.py').read_text(encoding='utf-8'))
found = set()
for node in tree.body:
    if (isinstance(node, ast.Expr) and isinstance(node.value, ast.Call)
            and isinstance(node.value.func, ast.Name) and node.value.func.id == 'write'):
        call = node.value
        path = ast.literal_eval(call.args[0])
        if path in adapters:
            write(path, ast.literal_eval(call.args[1]))
            found.add(path)
assert found == adapters, f'Missing established adapters: {adapters - found}'

task_store = (PRODUCTION / 'data/TaskStore.java').read_text(encoding='utf-8')
write('com/donglan/chrona/data/TaskStore.java', '''
package com.donglan.chrona.data;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
public final class TaskStore {
    private final SQLiteDatabase db;
    public TaskStore(SQLiteDatabase db) { this.db = db; }
    public SQLiteDatabase getReadableDatabase() { return db; }
    public SQLiteDatabase getWritableDatabase() { return db; }
''' + '\n'.join(member(task_store, signature) for signature in (
    'public boolean hasOtherCalendarReferences(', 'public boolean replaceCalendarEventId(',
    'public boolean setCalendarEventIdIfUnlinked(', 'private boolean setCalendarEventId(',
)) + '\n}')

calendar = (PRODUCTION / 'calendar/CalendarStore.java').read_text(encoding='utf-8')
write('com/donglan/chrona/calendar/CalendarStore.java', '''
package com.donglan.chrona.calendar;
import java.util.*;
import java.time.*;
import com.donglan.chrona.data.*;
import biweekly.component.VEvent;
import biweekly.util.Frequency;
import biweekly.util.Recurrence;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
public final class CalendarStore {
    private static final int MAX_REMINDERS = 5;
    private static final class Events { static final int STATUS_CANCELED = 2; }
    public final Map<Long, EventRecord> records = new LinkedHashMap<>();
    public final List<Long> deleted = new ArrayList<>(), updated = new ArrayList<>();
    public final Set<Long> failDelete = new HashSet<>();
    public final Set<Long> refuseDelete = new HashSet<>();
    public boolean failInsert;
    private long next = 100;
    public EventRecord getEvent(long id) { return records.get(id); }
    public long insertEvent(EventInput input) {
        if (failInsert) throw new IllegalStateException("provider insert failed");
        long id = next++; records.put(id, record(id, input)); return id;
    }
    public boolean updateEvent(long id, EventInput input) {
        if (!records.containsKey(id)) return false;
        updated.add(id); records.put(id, record(id, input)); return true;
    }
    public boolean updateDescription(long id, String text) {
        EventRecord record = getEvent(id); if (record == null) return false;
        return updateEvent(id, new EventInput(record.title, record.startMillis, record.endMillis,
            record.timeZoneId, record.allDay, text, record.location, record.reminderMinutes).withRepeat(record));
    }
    public boolean replaceReminders(long id, List<Integer> minutes) {
        EventRecord record = getEvent(id); if (record == null) return false;
        return updateEvent(id, new EventInput(record.title, record.startMillis, record.endMillis,
            record.timeZoneId, record.allDay, record.description, record.location, minutes).withRepeat(record));
    }
    public boolean deleteEvent(long id) {
        deleted.add(id);
        if (failDelete.contains(id)) throw new IllegalStateException("provider delete failed: " + id);
        if (refuseDelete.contains(id)) return false;
        return records.remove(id) != null;
    }
    public void seed(long id, EventInput input) { records.put(id, record(id, input)); }
    private static EventRecord record(long id, EventInput input) {
        return new EventRecord(id, 7, input.title, input.startMillis, input.endMillis,
            input.timeZoneId, input.allDay, input.description, input.location, input.reminderMinutes,
            input.rrule, null, null, null, null, input.cancelMaster ? 2 : 1, input.syncId, null,
            input.repeatRuleJson, input.repeatExceptions, input.repeatStoppedAtMillis);
    }
''' + '\n'.join(member(calendar, signature) for signature in (
    'public static final class EventInput', 'static String stoppedRule(',
    'public static final class EventRecord',
)) + '\n}')

sources = list(SRC.rglob('*.java')) + [
    PRODUCTION / 'calendar/LinkedCalendarEvents.java',
    *[PRODUCTION / 'data' / name for name in ('EventCandidate.java', 'EventCategory.java', 'RepeatRule.java')],
    ROOT / 'checks/LinkedCalendarEventsCheck.java',
]
classpath = recurrence_classpath()
subprocess.run([str(JAVA / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                '-cp', classpath, '-d', str(OUT), *map(str, sources)], check=True)
subprocess.run([str(JAVA / 'java.exe'), f'-Dpython={sys.executable}',
                f'-Dbridge={ROOT / "checks/sqlite_bridge.py"}', '-cp', str(OUT) + os.pathsep + classpath,
                'com.donglan.chrona.calendar.LinkedCalendarEventsCheck', str(OUT / 'databases')], check=True)
