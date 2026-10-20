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
