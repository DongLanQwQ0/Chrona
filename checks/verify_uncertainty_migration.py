"""Run the production v8 -> v9 migration SQL against SQLite and preserve existing rows."""
from pathlib import Path
import re
import sqlite3

source = (Path(__file__).resolve().parents[1] / 'app/src/main/java/com/donglan/chrona/data/TaskStore.java').read_text(encoding='utf-8')
method = source.split('public static void addUncertaintyLevel(SQLiteDatabase db) {', 1)[1].split('\n    }', 1)[0]
statements = re.findall(r'db.execSQL\("([^"]+)"\)', method)
assert len(statements) == 2
with sqlite3.connect(':memory:') as db:
    db.execute('CREATE TABLE event_candidates (id INTEGER PRIMARY KEY, title TEXT, needs_confirmation INTEGER)')
    db.executemany('INSERT INTO event_candidates VALUES (?, ?, ?)', [(1, '明确事项', 0), (2, '旧存疑事项', 1)])
    for sql in statements:
        db.execute(sql)
    assert db.execute('SELECT * FROM event_candidates ORDER BY id').fetchall() == [
        (1, '明确事项', 0, 0), (2, '旧存疑事项', 1, 2)]
    db.execute('UPDATE event_candidates SET uncertainty_level=1 WHERE id=2')
    try:
        db.execute('UPDATE event_candidates SET uncertainty_level=3')
        raise AssertionError('invalid level accepted')
    except sqlite3.IntegrityError:
        pass
print('v8 -> v9 uncertainty migration passed')
