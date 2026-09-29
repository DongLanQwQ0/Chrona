"""Exercise the production widget window SQL against SQLite."""
import json
import re
import sqlite3
from pathlib import Path

source = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/com/donglan/chrona/data/TaskStore.java").read_text(encoding="utf-8")
method = source.split("public List<EventCandidate> widgetCandidates", 1)[1].split("@Override", 1)[0]
selection = "".join(json.loads(part) for part in re.findall(
    r'"(?:[^"\\]|\\.)*"', method.split('query("event_candidates", null,', 1)[1]
    .split('new String[]', 1)[0]))
limit = int(re.search(r'WIDGET_ITEM_LIMIT\s*=\s*(\d+)', source)[1])
db = sqlite3.connect(":memory:")
db.execute("CREATE TABLE event_candidates(id INTEGER PRIMARY KEY, start_at_millis INTEGER, end_at_millis INTEGER)")
db.executemany("INSERT INTO event_candidates VALUES(?,?,?)", [
    (1, None, None), (2, 5, 9), (3, 5, 11), (4, 20, 22), (5, 10, None),
    (6, 15, 17), (7, 15, 18)])
query = f"SELECT id FROM event_candidates WHERE {selection} ORDER BY start_at_millis ASC,id ASC LIMIT {limit}"
# Android SQLiteDatabase.query binds selectionArgs as strings, not integers.
arguments = ("20", "10")
assert [row[0] for row in db.execute(query, arguments)] == [3, 5, 6, 7]
db.executemany("INSERT INTO event_candidates VALUES(?,?,?)", [(i, 16, 18) for i in range(8, 300)])
rows = list(db.execute(query, arguments))
assert len(rows) == limit and rows[:4] == [(3,), (5,), (6,), (7,)]
print("Widget SQL checks passed: window, null time, overlapping event, stable order, bounded limit")
