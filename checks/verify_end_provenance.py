"""Exercise the actual v6 -> v7 SQL without Android database stubs."""
from pathlib import Path
import re
import sqlite3
source = (Path(__file__).resolve().parents[1] / 'app/src/main/java/com/donglan/chrona/data/TaskStore.java').read_text(encoding='utf-8')
sql = re.search(r'db.execSQL\("(ALTER TABLE event_candidates ADD COLUMN end_auto_generated [^"]+)"\)', source).group(1)
with sqlite3.connect(':memory:') as db:
    db.execute('CREATE TABLE event_candidates (id INTEGER PRIMARY KEY, title TEXT, start_at_millis INTEGER, end_at_millis INTEGER)')
    db.execute("INSERT INTO event_candidates VALUES (9, 'existing', 100, 200)")
    db.execute('PRAGMA user_version=6')
    db.execute(sql)
    db.execute('PRAGMA user_version=7')
    assert db.execute('SELECT * FROM event_candidates').fetchone() == (9, 'existing', 100, 200, 0)
    db.execute('UPDATE event_candidates SET end_auto_generated=1 WHERE id=9')
    assert db.execute('SELECT end_auto_generated FROM event_candidates').fetchone() == (1,)
    try:
        db.execute('UPDATE event_candidates SET end_auto_generated=2')
        raise AssertionError('invalid provenance accepted')
    except sqlite3.IntegrityError:
        pass
print('v6 -> v7 end provenance migration passed')
