"""Exercise the Java-generated predicates against real SQLite, without a device."""
from recurrence_dependencies import recurrence_java

import base64
import datetime as dt
import sqlite3
import subprocess
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
JAVA = Path(os.environ['JAVA_HOME']) / 'bin'
OUT = ROOT / 'build/schedule-checks'
OUT.mkdir(parents=True, exist_ok=True)
subprocess.run(recurrence_java([str(JAVA / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(OUT),
                str(ROOT / 'app/src/main/java/com/donglan/chrona/data/ScheduleQuery.java'),
                str(ROOT / 'app/src/main/java/com/donglan/chrona/data/EventCategory.java'),
                str(ROOT / 'checks/ScheduleQueryCheck.java')]), check=True)
output = subprocess.check_output(recurrence_java([
    str(JAVA / 'java.exe'), '-cp', str(OUT),
    'com.donglan.chrona.data.ScheduleQueryCheck']), text=True)
queries = {}
for line in output.splitlines():
    name, *parts = line.split('\t')
    queries[name] = [base64.b64decode(p).decode() for p in parts]
db = sqlite3.connect(':memory:')
db.execute('CREATE TABLE event_candidates (id INTEGER PRIMARY KEY,title TEXT,location TEXT,'
           'description TEXT,category TEXT,all_day INTEGER,start_at_millis INTEGER,'
           'end_at_millis INTEGER,calendar_event_id INTEGER)')
INSERT_SQL = ('INSERT INTO event_candidates(id,title,location,description,category,all_day,'
              'start_at_millis,end_at_millis,calendar_event_id) VALUES (?,?,?,?,?,?,?,?,?)')

def ms(value):
    return int(dt.datetime.fromisoformat(value).timestamp() * 1000)

rows = [
    (1, '会议', None, '备注', 'event', 0, ms('2026-09-28T11:00+08:00'), ms('2026-09-28T13:00+08:00'), 100),
    (2, '过去', '', '', 'task', 0, ms('2026-09-28T09:00+08:00'), ms('2026-09-28T10:00+08:00'), None),
    (3, '全天', '', '', 'task', 1, ms('2026-09-28T00:00+00:00'), ms('2026-09-29T00:00+00:00'), None),
    (4, '无日期', '', '', 'task', 0, None, None, None),
    (5, '跨日', '', '', 'event', 0, ms('2026-09-27T23:00+08:00'), ms('2026-09-28T02:00+08:00'), None),
    (6, '次日', '', '', 'event', 0, ms('2026-09-29T00:00+08:00'), ms('2026-09-29T01:00+08:00'), None),
    (7, '%_确切文字', '', '', 'event', 0, None, None, None),
    (8, '百分号匹配陷阱', '', '', 'event', 0, None, None, None),
]
db.executemany(INSERT_SQL, rows)
for column in ("is_record INTEGER NOT NULL DEFAULT 0","completed_at_millis INTEGER","due_at_millis INTEGER","needs_confirmation INTEGER DEFAULT 1"): db.execute("ALTER TABLE event_candidates ADD COLUMN "+column)
db.execute('ALTER TABLE event_candidates ADD COLUMN task_id INTEGER DEFAULT 0')
db.execute('CREATE TABLE managed_zju_state(capture_id INTEGER)')
db.execute('INSERT INTO managed_zju_state VALUES(42)')

def ids(name):
    where, order, *args = queries[name]
    return [r[0] for r in db.execute('SELECT id FROM event_candidates WHERE ' + where + ' ORDER BY ' + order, args)]

assert set(ids('upcoming')) == {1, 2, 3, 6}
assert set(ids('past')) == {5}
assert set(ids('incomplete')) == {4, 7, 8}
assert set(ids('day')) == {1, 2, 3, 5}
assert set(ids('combined')) == {2, 3}
assert set(ids('literal')) == {7}
assert set(ids('published')) == {1}
assert set(ids('label')) == {2, 3, 4}
# Pending tasks remain pending after their deadline; event history still follows end time.
# Passing an event's time changes its tab, never deletes it from searchable history.
assert {5}.issubset(ids('all')), 'Past items must remain searchable in all items'

# Homework has its own category; unrelated tasks with the same title stay excluded.
for item, start, due, completed, capture, record in [
    (9001, 100, ms('2026-09-29T14:00+08:00'), None, 42, 0),
    (9002, 900, ms('2026-09-28T14:00+08:00'), None, 42, 0),
    (9003, 50, None, None, 42, 0),
    (9004, 25, ms('2026-09-27T14:00+08:00'), 1, 42, 0),
    (9005, 10, ms('2026-09-28T12:00+08:00'), None, 99, 0),
    (9006, 1, ms('2026-09-28T13:00+08:00'), None, 42, 1),
]:
    db.execute('INSERT INTO event_candidates(id,title,category,start_at_millis,due_at_millis,completed_at_millis,task_id,is_record,all_day) VALUES(?, ?, ?, ?, ?, ?, ?, ?, 0)',
               (item, '作业标题', 'homework' if capture == 42 else 'task', start, due, completed, capture, record))
assert ids('homework') == [9003, 9004, 9002, 9001]
assert ids('homeworkOpen') == [9003, 9002, 9001]
assert ids('homeworkToday') == [9002]
db.execute('DELETE FROM event_candidates WHERE id>=9001')

# Missed execution is independent of submission deadline. Keep this fixture bounded
# and remove it before the pre-existing 4008-row pagination/schema assertions.
now = ms('2026-09-28T12:00+08:00')
hour = 3_600_000
day = 24 * hour
overdue_cases = [
    # id, category, start, end, due, completed, record, expected overdue
    (9101, 'task', now-2*hour, now-hour, now+day, None, 0, True),
    (9102, 'homework', now-2*hour, now-hour, now+day, None, 0, True),
    (9103, 'task', now-hour, now+hour, now+day, None, 0, False),
    (9104, 'task', now-hour, None, now+day, None, 0, True),
    (9105, 'homework', None, now-hour, now+day, None, 0, True),
    (9106, 'task', now+hour, now+2*hour, now-hour, None, 0, True),
    (9107, 'task', None, None, now+10*60_000, None, 0, False),
    (9108, 'homework', None, None, now-hour, None, 0, True),
    (9109, 'task', None, None, None, None, 0, False),
    (9110, 'task', now-2*hour, now-hour, now-hour, now, 0, False),
    (9111, 'event', now-2*hour, now-hour, now-hour, None, 0, False),
    (9112, 'task', now-2*hour, now-hour, now-hour, None, 1, False),
    (9113, 'task', now-hour, now, now+day, None, 0, False),
    (9114, 'homework', None, None, now, None, 0, False),
    (9115, 'task', now-2*hour, now-hour, None, None, 0, True),
    (9116, 'task', None, now-hour, None, None, 0, True),
]
overdue_cases.extend((i, 'homework' if i % 2 else 'task', now-2*hour,
                      now-hour, now+2*day, None, 0, True) for i in range(9200, 9253))
for item, category, start, end, due, completed, record, expected in overdue_cases:
    db.execute('INSERT INTO event_candidates(id,title,category,start_at_millis,end_at_millis,'
               'due_at_millis,completed_at_millis,is_record,all_day) VALUES(?,?,?,?,?,?,?,?,0)',
               (item, '逾期筛选验收', category, start, end, due, completed, record))
fixture_ids = {row[0] for row in overdue_cases}
expected = {row[0] for row in overdue_cases if row[-1]}
selected = [item for item in ids('overdue') if item in fixture_ids]
assert set(selected) == expected, (set(selected), expected)
assert {item for item in ids('homeworkOverdue') if item in fixture_ids} == {
    row[0] for row in overdue_cases if row[1] == 'homework' and row[-1]}
# Ordering must use the missed-execution boundary, not the later explicit deadline.
boundary = {row[0]: min(value for value in (row[4], row[3] if row[3] is not None else row[2])
                             if value is not None) for row in overdue_cases if row[-1]}
assert selected == sorted(expected, key=lambda item: (boundary[item], item))
assert [item for item in ids('overdueReverse') if item in fixture_ids] == list(reversed(selected))
where, order, *args = queries['overdue']
count = db.execute('SELECT COUNT(*) FROM event_candidates WHERE ' + where, args).fetchone()[0]
paged = []
for offset in range(0, count, 24):
    paged.extend(row[0] for row in db.execute('SELECT id FROM event_candidates WHERE ' + where
                 + ' ORDER BY ' + order + ' LIMIT ? OFFSET ?', [*args, 24, offset]))
assert paged == ids('overdue') and len(set(paged)) == count
assert db.execute('SELECT start_at_millis,end_at_millis,due_at_millis FROM event_candidates '
                  'WHERE id=9107').fetchone() == (None, None, now+10*60_000)
db.execute('DELETE FROM event_candidates WHERE id>=9100 AND id<9300')
print('Overdue model/SQLite checks passed: execution vs deadline, strict boundary, recurrence, IDs/count/pages')

# Identical timestamps must retain stable unique IDs across every page boundary.
db.executemany(INSERT_SQL, [
    (i, '大量同刻日程', '', '', 'event', 0, ms('2026-10-01T09:00+08:00'),
     ms('2026-10-01T10:00+08:00'), None) for i in range(10, 4010)])
where, order, *args = queries['all']
whole = ids('all')
paged = []
for offset in range(0, len(whole), 24):
    page = list(db.execute('SELECT id FROM event_candidates WHERE ' + where + ' ORDER BY '
                          + order + ' LIMIT ? OFFSET ?', [*args, 24, offset]))
    assert len(page) <= 24
    paged.extend(r[0] for r in page)
assert paged == whole and len(set(paged)) == len(whole)
where, order, *args = queries['reverse']
reverse_pages = []
for offset in range(0, len(whole), 24):
    reverse_pages.extend(r[0] for r in db.execute('SELECT id FROM event_candidates WHERE ' + where
                         + ' ORDER BY ' + order + ' LIMIT ? OFFSET ?', [*args, 24, offset]))
assert reverse_pages == ids('reverse') and len(set(reverse_pages)) == len(whole)
db.executemany(INSERT_SQL, [
    (5000, 'before DST day', '', '', 'event', 0, ms('2026-03-08T07:00+00:00'), ms('2026-03-08T08:00+00:00'), None),
    (5001, 'DST day', '', '', 'event', 0, ms('2026-03-08T08:00+00:00'), ms('2026-03-09T07:00+00:00'), None),
    (5002, 'after DST day', '', '', 'event', 0, ms('2026-03-09T07:00+00:00'), ms('2026-03-09T08:00+00:00'), None),
    (5003, 'all-day DST', '', '', 'event', 1, ms('2026-03-08T00:00+00:00'), ms('2026-03-09T00:00+00:00'), None),
])
assert set(ids('dst')) == {5001, 5003}, 'DST day is 23 hours; all-day bounds stay UTC dates'
print('Schedule SQL checks passed: combined filters, time bounds, literal search, 4008 rows / 24-item pages')

# Compile the exact migration method with a capture-only SQLiteDatabase stub. This executes
# the production Java string construction rather than maintaining a second set of SQL.
source = (ROOT / 'app/src/main/java/com/donglan/chrona/data/TaskStore.java').read_text(encoding='utf-8')
start = source.index('    public static void addScheduleBrowsing(')
finish = source.index('    public long dataRevision()', start)
method = source[start:finish]
wrapper = '''import java.util.Base64;
public final class SchemaExport {
  static class SQLiteDatabase {
    void execSQL(String sql) { System.out.println(Base64.getEncoder().encodeToString(
      sql.getBytes(java.nio.charset.StandardCharsets.UTF_8))); }
  }
  public static void main(String[] args) { addScheduleBrowsing(new SQLiteDatabase()); }
''' + method + '\n}'
generated = ROOT / 'build/schedule-checks/SchemaExport.java'
generated.write_text(wrapper, encoding='utf-8')
subprocess.run(recurrence_java([str(JAVA / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                '-d', str(generated.parent), str(generated)]), check=True)
schema = subprocess.check_output(recurrence_java([str(JAVA / 'java.exe'), '-cp',
                                  str(generated.parent), 'SchemaExport']), text=True)
db.execute('CREATE TABLE tasks(id INTEGER PRIMARY KEY,status TEXT)')
db.execute('CREATE TABLE task_attachments(id INTEGER PRIMARY KEY)')
db.execute('CREATE TABLE task_files(id INTEGER PRIMARY KEY)')
before = ids('all')
for line in schema.splitlines():
    db.execute(base64.b64decode(line).decode())
assert ids('all') == before, 'Index/revision migration must preserve existing rows'
db.commit()
revision = lambda connection: connection.execute('SELECT revision FROM data_revision WHERE id=1').fetchone()[0]
assert revision(db) == 0
db.execute("UPDATE event_candidates SET title='修改但字数相同' WHERE id=1")
assert revision(db) == 1, 'Edits with unchanged row count must refresh the UI'
db.commit()
db.execute('DELETE FROM event_candidates WHERE id=2')
assert revision(db) == 2
db.rollback()
assert revision(db) == 1 and db.execute('SELECT id FROM event_candidates WHERE id=2').fetchone()
restored = sqlite3.connect(':memory:')
db.backup(restored)
restored.execute("INSERT INTO tasks VALUES(1,'needs_review')")
assert revision(restored) == 2, 'Backup copies must retain revision triggers'
print('Schedule schema checks passed: old rows preserved, changes tracked, rollback and backup retained')
