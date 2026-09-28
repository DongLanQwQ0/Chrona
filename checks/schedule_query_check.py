"""Exercise the Java-generated predicates against real SQLite, without a device."""
import base64
import datetime as dt
import sqlite3
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
output = subprocess.check_output([
    r'D:\Minecraft\java21\bin\java.exe', '-cp', str(ROOT / 'build/schedule-checks'),
    'com.donglan.chrona.data.ScheduleQueryCheck'], text=True)
queries = {}
for line in output.splitlines():
    name, *parts = line.split('\t')
    queries[name] = [base64.b64decode(p).decode() for p in parts]
db = sqlite3.connect(':memory:')
db.execute('CREATE TABLE event_candidates (id INTEGER PRIMARY KEY,title TEXT,location TEXT,'
           'description TEXT,category TEXT,all_day INTEGER,start_at_millis INTEGER,'
           'end_at_millis INTEGER,calendar_event_id INTEGER)')

def ms(value):
    return int(dt.datetime.fromisoformat(value).timestamp() * 1000)

rows = [
    (1, '会议', None, '备注', 'event', 0, ms('2026-09-28T11:00+08:00'), ms('2026-09-28T13:00+08:00'), 100),
    (2, '过去', '', '', 'task', 0, ms('2026-09-28T09:00+08:00'), ms('2026-09-28T10:00+08:00'), None),
    (3, '全天', '', '', 'task', 1, ms('2026-09-28T00:00+00:00'), ms('2026-09-29T00:00+00:00'), None),
    (4, '无日期', '', '', 'task', 0, None, None, None),
    (5, '跨日', '', '', 'event', 0, ms('2026-09-27T23:00+08:00'), ms('2026-09-28T02:00+08:00'), None),
    (6, '次日', '', '', 'event', 0, ms('2026-09-29T00:00+08:00'), ms('2026-09-29T01:00+08:00'), None),
    (7, '%_确切文字', '', '', 'note', 0, None, None, None),
    (8, '百分号匹配陷阱', '', '', 'note', 0, None, None, None),
]
db.executemany('INSERT INTO event_candidates VALUES (?,?,?,?,?,?,?,?,?)', rows)

def ids(name):
    where, order, *args = queries[name]
    return [r[0] for r in db.execute('SELECT id FROM event_candidates WHERE ' + where + ' ORDER BY ' + order, args)]

assert set(ids('upcoming')) == {1, 3, 6}
assert set(ids('past')) == {2, 5}
assert set(ids('incomplete')) == {4, 7, 8}
assert set(ids('day')) == {1, 2, 3, 5}
assert set(ids('combined')) == {2, 3}
assert set(ids('literal')) == {7}
assert set(ids('published')) == {1}
assert set(ids('label')) == {2, 3, 4}

# Identical timestamps must retain stable unique IDs across every page boundary.
db.executemany('INSERT INTO event_candidates VALUES (?,?,?,?,?,?,?,?,?)', [
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
db.executemany('INSERT INTO event_candidates VALUES (?,?,?,?,?,?,?,?,?)', [
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
subprocess.run([r'D:\Minecraft\java21\bin\javac.exe', '--release', '17', '-encoding', 'UTF-8',
                '-d', str(generated.parent), str(generated)], check=True)
schema = subprocess.check_output([r'D:\Minecraft\java21\bin\java.exe', '-cp',
                                  str(generated.parent), 'SchemaExport'], text=True)
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
