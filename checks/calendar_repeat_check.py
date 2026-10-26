"""Execute provider recurrence-boundary checks with the existing Android SDK API stubs."""
from pathlib import Path
import os
import subprocess
import json
import re
import sqlite3
from recurrence_dependencies import recurrence_java
ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/calendar-repeat-check'
OUT.mkdir(parents=True, exist_ok=True)
JAVA = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
SDK = Path(os.environ.get('ANDROID_HOME', 'F:/Android/Sdk')) / 'platforms/android-36/android.jar'
if not SDK.is_file():
    raise RuntimeError('Existing Android 36 platform JAR is required; nothing will be installed')
source = OUT / 'ScheduleSources.java'
source.write_text('package com.donglan.chrona; public class ScheduleSources { public static boolean allowsCalendar(android.content.Context context, long id) { return true; } }', encoding='utf-8')
SRC = ROOT / 'app/src/main/java/com/donglan/chrona'
sources = [SRC / name for name in ('calendar/CalendarStore.java', 'calendar/AllDayDates.java', 'calendar/CalendarOccurrence.java')]
sources += [source, ROOT / 'checks/CalendarRepeatCheck.java']
subprocess.run(recurrence_java([str(JAVA / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', str(SDK), '-d', str(OUT), *map(str, sources)]), check=True)
subprocess.run(recurrence_java([str(JAVA / 'java.exe'), '-cp', str(OUT) + os.pathsep + str(SDK), 'com.donglan.chrona.calendar.CalendarRepeatCheck']), check=True)

# Exercise the exact production deletion predicate, without assuming Provider cascade support.
store = (SRC / 'calendar/CalendarStore.java').read_text(encoding='utf-8')
deletion = store.split('public boolean deleteEvent(long eventId)', 1)[1].split('public EventRecord getEvent', 1)[0]
expression = deletion.split('resolver.delete(syncUri(Events.CONTENT_URI),', 1)[1].split('new String[]', 1)[0]
columns = {'Events._ID': '_id', 'Events.ORIGINAL_ID': 'original_id', 'Events.CALENDAR_ID': 'calendar_id'}
predicate = ''.join(columns[token] if token in columns else json.loads(token)
                    for token in re.findall(r'"[^"\\]*(?:\\.[^"\\]*)*"|Events\.\w+', expression))
assert 'new String[]{Long.toString(eventId), Long.toString(eventId), Long.toString(calendarId)}' in deletion
db = sqlite3.connect(':memory:')
db.executescript('''
CREATE TABLE events(_id INTEGER PRIMARY KEY, original_id INTEGER, calendar_id INTEGER, status INTEGER);
INSERT INTO events VALUES (10,NULL,18,1),(11,10,18,2),(12,10,18,1),
 (13,10,19,1),(14,NULL,18,1),(15,NULL,19,1),(21,20,18,1),(22,20,19,1);
''')
assert db.execute('DELETE FROM events WHERE ' + predicate, (10, 10, 18)).rowcount == 3
assert list(db.execute('SELECT _id FROM events ORDER BY _id')) == [(13,), (14,), (15,), (21,), (22,)]
assert db.execute('DELETE FROM events WHERE ' + predicate, (10, 10, 18)).rowcount == 0
assert db.execute('DELETE FROM events WHERE ' + predicate, (20, 20, 18)).rowcount == 1
assert db.execute('DELETE FROM events WHERE ' + predicate, (15, 15, 18)).rowcount == 0
assert db.execute('DELETE FROM events WHERE ' + predicate, (14, 14, 18)).rowcount == 1
assert list(db.execute('SELECT _id FROM events ORDER BY _id')) == [(13,), (15,), (22,)]
print('Provider deletion SQL: series, cancelled/moved exceptions, orphans, stale IDs and cross-calendar isolation passed')

# Run the unchanged production batch construction methods with value/operation transport doubles.
# The full CalendarStore above still compiles against the real Android API; no real Provider is used here.
def member(signature):
    start = store.index(signature)
    end = store.index('{', start) + 1
    depth = 1
    while depth:
        depth += (store[end] == '{') - (store[end] == '}')
        end += 1
    return store[start:end]


members = '\n'.join(member(signature) for signature in (
    'private static ContentValues eventValues(', 'private static String duration(',
    'private void appendExceptions(', 'private static List<Integer> occurrenceReminders(',
    'public static final class EventInput',
    'static String stoppedRule(', 'public static final class EventRecord',
))
constants = []
for name in ('Events', 'Reminders'):
    names = sorted(set(re.findall(r'\b' + name + r'\.(\w+)', members)))
    declarations = []
    for constant in names:
        if constant.startswith('STATUS_'):
            declarations.append(f'static final int {constant} = {2 if constant == "STATUS_CANCELED" else 1};')
        else:
            declarations.append(f'static final String {constant} = "{constant}";')
    constants.append(f'static final class {name} {{' + '\n'.join(declarations) + '}')
transport = r'''
static final class ContentValues extends java.util.LinkedHashMap<String,Object> {
    public void putNull(String key) { put(key, null); }
}
static final class ContentProviderOperation {
    final java.util.Map<String,Object> values;
    ContentProviderOperation(java.util.Map<String,Object> values) { this.values = new java.util.LinkedHashMap<>(values); }
    static Builder newInsert(String uri) { return new Builder(); }
    static final class Builder {
        final java.util.Map<String,Object> values = new java.util.LinkedHashMap<>();
        Builder withValues(ContentValues map) { values.putAll(map); return this; }
        Builder withValue(String key,Object value) { values.put(key,value); return this; }
        Builder withValueBackReference(String key,int index) { values.put(key,"back:"+index); return this; }
        ContentProviderOperation build() { return new ContentProviderOperation(values); }
    }
}
private String syncUri(String uri) { return uri; }
'''
provider = OUT / 'provider'
provider.mkdir(parents=True, exist_ok=True)
fixture = provider / 'CalendarStore.java'
fixture.write_text('''package com.donglan.chrona.calendar;
import java.util.*;
import java.time.*;
import com.donglan.chrona.data.*;
import org.json.JSONObject;
import biweekly.component.VEvent;
import biweekly.util.Frequency;
import biweekly.util.Recurrence;
import biweekly.util.com.google.ical.compat.javautil.DateIterator;
public final class CalendarStore {
private static final int MAX_REMINDERS = 5;
''' + '\n'.join(constants) + transport + members + '\n}', encoding='utf-8')
subprocess.run(recurrence_java([str(JAVA / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                '-d', str(provider), str(fixture), str(ROOT / 'checks/CalendarRepeatCheck.java')]), check=True)
subprocess.run(recurrence_java([str(JAVA / 'java.exe'), '-cp', str(provider),
                'com.donglan.chrona.calendar.CalendarRepeatCheck', 'operations']), check=True)
