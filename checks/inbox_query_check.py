"""Execute production inbox predicates against SQLite, plus Java Unicode search checks."""
import base64
import os
from pathlib import Path
import shutil
import sqlite3
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / "app/build/inbox-query-check"
out.mkdir(parents=True, exist_ok=True)
suffix = ".exe" if os.name == "nt" else ""
java_home = os.environ.get("JAVA_HOME")
def java_tool(name):
    return str(Path(java_home) / "bin" / (name + suffix)) if java_home else shutil.which(name)
sources = [root / "app/src/main/java/com/donglan/chrona/data" / (name + ".java")
           for name in ("TaskRecord", "EventCategory", "InboxQuery")]
subprocess.run([java_tool("javac"), "--release", "17", "-encoding", "UTF-8", "-d", str(out),
                *map(str, sources), str(root / "checks/InboxQueryCheck.java")], check=True)
plans = subprocess.check_output([java_tool("java"), "-cp", str(out), "InboxQueryCheck"], text=True)
decode = lambda value: base64.b64decode(value).decode("utf-8")
db = sqlite3.connect(":memory:")
db.executescript("""
CREATE TABLE tasks(id INTEGER PRIMARY KEY, status TEXT, created_at_millis INTEGER);
CREATE TABLE event_candidates(task_id INTEGER, category TEXT, calendar_event_id INTEGER);
INSERT INTO event_candidates VALUES(1,'event',101),(1,'event',102),(1,'note',NULL),
 (2,'event',103),(3,'note',104);
""")
statuses = {1: "needs_review", 2: "queued", 3: "processing", 4: "failed", 5: "ready"}
db.executemany("INSERT INTO tasks VALUES(?,?,?)",
               [(i, statuses.get(i, "needs_review"), i // 3) for i in range(1, 31)])
expected = {"all": list(range(30, 0, -1)), "oldest": list(range(1, 31)),
            "pending": list(range(30, 5, -1)) + [1], "processing": [3, 2], "failed": [4],
            "published_event": [2, 1], "published_note": [3], "note": [3, 1]}
for line in plans.splitlines():
    name, encoded_selection, encoded_args, encoded_order = line.split("\t")
    selection, order = decode(encoded_selection), decode(encoded_order)
    args = [decode(value) for value in encoded_args.split(",")] if encoded_args else []
    ids = [row[0] for row in db.execute(f"SELECT id FROM tasks WHERE {selection} ORDER BY {order}", args)]
    assert ids == expected[name], (name, ids, expected[name])
    # Pages fetched by the selected ID window retain ordering and contain at most 12 rows.
    pages = []
    for offset in range(0, len(ids), 12):
        selected = ids[offset:offset + 12]
        rows = [row[0] for row in db.execute(
            f"SELECT id FROM tasks WHERE id IN ({','.join('?' for _ in selected)}) ORDER BY {order} LIMIT 12",
            selected)]
        assert len(rows) <= 12
        pages.extend(rows)
    assert pages == ids, (name, "page gaps or duplicates")
print("Inbox checks passed: 8 SQLite filter/order/page cases and 4 Java search cases")
