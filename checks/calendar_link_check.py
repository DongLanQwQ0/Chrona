"""Verify production calendar-link SQL predicates against SQLite (provider calls need device tests)."""
import json
import re
import sqlite3
from pathlib import Path

source = (Path(__file__).resolve().parents[1] /
          "app/src/main/java/com/donglan/chrona/data/TaskStore.java").read_text(encoding="utf-8")
clear = source.split("public int clearMissingCalendarLinks", 1)[1].split("/** Returns candidates", 1)[0]
candidate_where = re.search(r'db.update\("event_candidates", values, "([^"]+)"', clear)[1]
task_where = re.search(r'db.update\("tasks", values, "([^"]+)"', clear)[1]
link = source.split("private boolean setCalendarEventId", 1)[1].split("/** Clear only", 1)[0]
parts = re.findall(r'"(?:[^"\\]|\\.)*"', link.split('update("event_candidates", values,', 1)[1]
                   .split('new String[]', 1)[0])
link_where = json.loads(parts[0]) + json.loads(parts[1])
db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE tasks(id INTEGER PRIMARY KEY,status TEXT,error_message TEXT);
CREATE TABLE event_candidates(id INTEGER PRIMARY KEY,task_id INTEGER,title TEXT,
 start_at_millis INTEGER,calendar_event_id INTEGER);
INSERT INTO tasks VALUES(1,'ready',NULL),(2,'failed','preserve error');
INSERT INTO event_candidates VALUES(11,1,'保留标题',1790640000000,101),
 (12,1,'另一个日程',1790643600000,102),(21,2,'解析失败草稿',NULL,101);
""")
before = list(db.execute("SELECT id,title,start_at_millis FROM event_candidates"))
affected = [row[0] for row in db.execute(
    f"SELECT DISTINCT task_id FROM event_candidates WHERE {candidate_where}", ("101",))]
db.execute(f"UPDATE event_candidates SET calendar_event_id=NULL WHERE {candidate_where}", ("101",))
for task in affected:
    db.execute(f"UPDATE tasks SET status='needs_review',error_message=NULL WHERE {task_where}",
               (str(task), "ready"))
assert list(db.execute("SELECT id,title,start_at_millis FROM event_candidates")) == before
assert list(db.execute("SELECT calendar_event_id FROM event_candidates ORDER BY id")) == [(None,), (102,), (None,)]
assert list(db.execute("SELECT status,error_message FROM tasks ORDER BY id")) == [
    ("needs_review", None), ("failed", "preserve error")]
assert db.execute(f"UPDATE event_candidates SET calendar_event_id=201 WHERE {link_where}",
                  ("11", "1")).rowcount == 1
assert db.execute(f"UPDATE event_candidates SET calendar_event_id=202 WHERE {link_where}",
                  ("11", "1")).rowcount == 0
assert db.execute(f"UPDATE event_candidates SET calendar_event_id=NULL WHERE {candidate_where}",
                  ("101",)).rowcount == 0  # an old deletion notice cannot clear new ID 201
assert db.execute("SELECT calendar_event_id FROM event_candidates WHERE id=11").fetchone() == (201,)
print("Calendar link SQL checks passed: stale ID, draft preservation, status reset, new-link protection")
