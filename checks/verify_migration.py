"""Apply TaskStore ALTER statements to populated v1/v2/v3 SQLite fixtures."""

from pathlib import Path
import re
import sqlite3

source = (Path(__file__).resolve().parents[1] / "app/src/main/java/com/donglan/chrona/data/TaskStore.java").read_text(encoding="utf-8")
blocks = [(version, block) for version, block in re.findall(
    r'if \(oldVersion < (\d+)\) \{(.*?)\n        \}', source, re.S) if int(version) <= 4]
assert [int(version) for version, _ in blocks] == [2, 3, 4]

for initial_version in (1, 2, 3):
    with sqlite3.connect(":memory:") as db:
        db.execute("CREATE TABLE tasks (id INTEGER PRIMARY KEY, raw_text TEXT, total_tokens INTEGER)")
        db.execute("CREATE TABLE event_candidates (id INTEGER PRIMARY KEY, task_id INTEGER, title TEXT)")
        db.execute("INSERT INTO tasks VALUES (7, 'existing input', 120)")
        db.execute("INSERT INTO event_candidates VALUES (9, 7, 'existing event')")
        for target_version, block in blocks:
            if int(target_version) <= initial_version:
                for statement in re.findall(r'db\.execSQL\("(ALTER TABLE [^\"]+)"\);', block):
                    db.execute(statement)
        for target_version, block in blocks:
            if int(target_version) > initial_version:
                statements = re.findall(r'db\.execSQL\("(ALTER TABLE [^\"]+)"\);', block)
                assert statements
                for statement in statements:
                    db.execute(statement)
        assert db.execute("SELECT id, raw_text, total_tokens, cached_tokens FROM tasks").fetchone() == (7, "existing input", 120, None)
        assert db.execute("SELECT id, task_id, title, category, all_day FROM event_candidates").fetchone() == (9, 7, "existing event", "event", 0)

print("v1/v2/v3 to v4 migrations passed")
