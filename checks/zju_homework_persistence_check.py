"""Run production managed-homework schema/deletion triggers against SQLite, without user data."""
import json
import re
import sqlite3
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/data/TaskStore.java').read_text(encoding='utf-8')
method = source.split('public static void addZjuHomework(SQLiteDatabase db) {', 1)[1].split('public boolean ignoredZjuCandidate', 1)[0]
db = sqlite3.connect(':memory:')
db.execute('PRAGMA foreign_keys=ON')
db.executescript('''
CREATE TABLE tasks(id INTEGER PRIMARY KEY);
CREATE TABLE event_candidates(id INTEGER PRIMARY KEY,task_id INTEGER REFERENCES tasks(id) ON DELETE CASCADE,start_at_millis INTEGER,due_at_millis INTEGER);
CREATE TABLE sync_task_map(local_id INTEGER,sync_id TEXT);
CREATE TABLE data_revision(id INTEGER PRIMARY KEY,revision INTEGER);
INSERT INTO data_revision VALUES(1,0);
''')
for expression in re.findall(r'db\.execSQL\(((?:"(?:\\.|[^"\\])*"|[^"])*?)\);', method, re.S):
    # Execute every static SQL statement verbatim; revision-trigger names use loop variables.
    without_strings = re.sub(r'"(?:\\.|[^"\\])*"', '', expression)
    if re.search(r'[A-Za-z_]', without_strings):
        continue
    strings = re.findall(r'"(?:\\.|[^"\\])*"', expression)
    db.execute(''.join(json.loads(value) for value in strings))
db.executescript('''
INSERT INTO tasks VALUES(10);
INSERT INTO event_candidates VALUES(20,10,NULL,10000);
INSERT INTO sync_task_map VALUES(10,'task_test');
INSERT INTO zju_homework_captures VALUES(10,'account-a');
UPDATE managed_zju_state SET enabled=1,account='account-a',generation=7,capture_id=10;
INSERT INTO zju_homework_map(account,remote_key,capture_id,candidate_id,sync_id,ignored,snapshot) VALUES('account-a','opaque-key',10,20,'candidate_test',0,'{}');
''')
allowed = re.search(r'private static final String ZJU_REMINDER_ALLOWED = "([^"]+)";', source).group(1)
assert db.execute('SELECT 1 FROM event_candidates WHERE id=20 AND ' + allowed).fetchone()
db.execute('UPDATE managed_zju_state SET enabled=0')
assert not db.execute('SELECT 1 FROM event_candidates WHERE id=20 AND ' + allowed).fetchone()
db.execute("UPDATE managed_zju_state SET enabled=1,capture_id=99,account='account-b'")
assert not db.execute('SELECT 1 FROM event_candidates WHERE id=20 AND ' + allowed).fetchone()
db.execute("UPDATE managed_zju_state SET capture_id=10,account='account-a'")
db.execute('UPDATE event_candidates SET due_at_millis=NULL')
assert not db.execute('SELECT 1 FROM event_candidates WHERE id=20 AND ' + allowed).fetchone()
db.execute('UPDATE event_candidates SET start_at_millis=20000')
assert db.execute('SELECT 1 FROM event_candidates WHERE id=20 AND ' + allowed).fetchone()
db.commit()
db.execute('BEGIN')
db.execute('DELETE FROM tasks WHERE id=10')
assert db.execute('SELECT enabled,generation,capture_id FROM managed_zju_state').fetchone() == (0, 8, None)
assert db.execute('SELECT ignored FROM zju_homework_map').fetchone() == (1,)
assert db.execute('SELECT sync_id FROM zju_homework_deleted_tasks').fetchone() == ('task_test',)
db.rollback()
assert db.execute('SELECT enabled,generation,capture_id FROM managed_zju_state').fetchone() == (1, 7, 10)
assert db.execute('SELECT ignored FROM zju_homework_map').fetchone() == (0,)
db.execute('DELETE FROM event_candidates WHERE id=20')
assert db.execute('SELECT ignored FROM zju_homework_map').fetchone() == (1,)
assert db.execute('SELECT enabled,generation,capture_id FROM managed_zju_state').fetchone() == (1, 7, 10)
for table in ('managed_zju_state', 'zju_homework_captures', 'zju_homework_map', 'zju_homework_history', 'zju_homework_deleted_tasks'):
    assert f'"{table}"' in source.split('public void createSnapshot', 1)[1].split('@Override', 1)[0]
sync = (root / 'app/src/main/java/com/donglan/chrona/AndroidSyncData.java').read_text(encoding='utf-8')
assert 'store.deletedZjuTask(id)' in sync and 'store.ignoredZjuCandidate(stable)' in sync
print('PASS: production DB12 schema, transactional deletion/cascade/rollback, ignored tombstones, disabled/account reminder filtering, snapshot and sync hooks')
