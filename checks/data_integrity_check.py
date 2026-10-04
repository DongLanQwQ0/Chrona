"""Run production snapshot/window/status SQL against concurrent real SQLite connections."""
from pathlib import Path
import json
import re
import sqlite3
import tempfile
import threading
from contextlib import ExitStack, closing

root = Path(__file__).resolve().parents[1]
store = (root / "app/src/main/java/com/donglan/chrona/data/TaskStore.java").read_text(encoding="utf-8")
backup = (root / "app/src/main/java/com/donglan/chrona/ChronaDataBackup.java").read_text(encoding="utf-8")

def literals(text):
    return [json.loads(value) for value in re.findall(r'"(?:[^"\\]|\\.)*"', text)]

snapshot = store.split("public void createSnapshot", 1)[1].split("@Override", 1)[0]
tables = literals(snapshot.split("new String[]{", 1)[1].split("}", 1)[0])
delete_prefix = literals(snapshot.split("source.execSQL(", 3)[2])[0]
# Extract the actual INSERT concatenation rather than maintaining a duplicate query.
insert_expression = re.search(r'source.execSQL\(("INSERT INTO.*?SELECT.*?);', snapshot, re.S)[1]
insert_parts = literals(insert_expression)

with tempfile.TemporaryDirectory(prefix="chrona-snapshot-check-", dir=root / "build") as directory, ExitStack() as opened:
    live_path = Path(directory) / "live.db"
    snap_path = Path(directory) / "snapshot.db"
    schema = """
    CREATE TABLE tasks(id INTEGER PRIMARY KEY AUTOINCREMENT,status TEXT,error_message TEXT);
    CREATE TABLE event_candidates(id INTEGER PRIMARY KEY AUTOINCREMENT,task_id INTEGER REFERENCES tasks(id),start_at_millis INTEGER,end_at_millis INTEGER,all_day INTEGER,calendar_event_id INTEGER);
    CREATE TABLE task_attachments(id INTEGER PRIMARY KEY AUTOINCREMENT,task_id INTEGER REFERENCES tasks(id));
    CREATE TABLE task_files(id INTEGER PRIMARY KEY AUTOINCREMENT,task_id INTEGER REFERENCES tasks(id));
    CREATE TABLE data_revision(id INTEGER PRIMARY KEY,revision INTEGER);
    CREATE TABLE candidate_merges(source_candidate TEXT PRIMARY KEY,record TEXT,calendar_event_id INTEGER);
    CREATE TABLE sync_task_map(sync_id TEXT PRIMARY KEY,local_id INTEGER UNIQUE,baseline TEXT,baseline_clock TEXT);
    CREATE TABLE sync_candidate_map(local_id INTEGER PRIMARY KEY,sync_id TEXT UNIQUE);
    CREATE TABLE sync_meta(id INTEGER PRIMARY KEY,identity TEXT);
    INSERT INTO data_revision VALUES(1,0);
    """
    trigger_expression = re.search(r'db.execSQL\(("CREATE TRIGGER.*?)\);', store, re.S)[1]
    def create(path):
        db = sqlite3.connect(path, isolation_level=None)
        opened.callback(db.close)
        db.execute("PRAGMA foreign_keys=ON")
        db.executescript(schema)
        for table in tables[:4]:
            for operation in ("INSERT", "UPDATE", "DELETE"):
                tokens = re.findall(r'"(?:[^"\\]|\\.)*"|\btable\b|\boperation\b', trigger_expression)
                statement = "".join(table if token == "table" else operation if token == "operation"
                                    else json.loads(token) for token in tokens)
                db.execute(statement)
        return db
    live = create(live_path)
    target = create(snap_path)
    target.close()
    live.execute("INSERT INTO tasks VALUES(1,'ready',NULL)")
    live.execute("INSERT INTO tasks VALUES(99,'ready',NULL)")
    live.execute("DELETE FROM tasks WHERE id=99")
    live.execute("INSERT INTO event_candidates VALUES(1,1,10,20,0,42)")
    live.execute("INSERT INTO task_attachments VALUES(1,1)")
    live.execute("INSERT INTO task_files VALUES(1,1)")
    original_revision = live.execute("SELECT revision FROM data_revision").fetchone()[0]
    attach_sql = literals(snapshot.split("source.execSQL(", 1)[1])[0]
    live.execute(attach_sql, (str(snap_path),))
    live.execute("BEGIN IMMEDIATE")
    writer_started, writer_finished = threading.Event(), threading.Event()
    failures = []
    def writer():
        try:
            with closing(sqlite3.connect(live_path, timeout=3)) as db:
                writer_started.set()
                db.execute("INSERT INTO tasks(status) VALUES('needs_review')")
                db.commit()
            writer_finished.set()
        except Exception as error: failures.append(error)
    worker = threading.Thread(target=writer)
    worker.start()
    assert writer_started.wait(1)
    for table in tables:
        live.execute(delete_prefix + table)
        live.execute(insert_parts[0] + table + insert_parts[1] + table)
    assert not writer_finished.is_set(), "writer must wait for the snapshot transaction"
    live.execute("COMMIT")
    live.execute("DETACH DATABASE backup_snapshot")
    worker.join(3)
    assert writer_finished.is_set() and not failures
    with closing(sqlite3.connect(snap_path)) as snap:
        assert snap.execute("SELECT COUNT(*) FROM tasks").fetchone()[0] == 1
        assert live.execute("SELECT COUNT(*) FROM tasks").fetchone()[0] == 2
        assert snap.execute("PRAGMA foreign_key_check").fetchall() == []
        assert snap.execute("SELECT revision FROM data_revision").fetchone()[0] == original_revision
        assert snap.execute("SELECT seq FROM sqlite_sequence WHERE name='tasks'").fetchone()[0] == 99
        snap.execute("INSERT INTO tasks(status) VALUES('ready')")
        assert snap.execute("SELECT MAX(id) FROM tasks").fetchone()[0] == 100
        downgrade = re.search(r'staged.execSQL\(("UPDATE tasks SET status=\'needs_review\'.*?)\);', backup, re.S)[1]
        sql = "".join(literals(downgrade))
        snap.execute("UPDATE event_candidates SET calendar_event_id=NULL WHERE task_id=1")
        snap.execute(sql)
        assert snap.execute("SELECT status FROM tasks WHERE id=1").fetchone()[0] == "needs_review"
        assert snap.execute("SELECT status FROM tasks WHERE id=100").fetchone()[0] == "ready"
    live.close()

method = store.split("public List<EventCandidate> widgetCandidates(long begin, long end,", 1)[1].split("@Override", 1)[0]
selection = "".join(literals(method.split('query("event_candidates", null,', 1)[1].split('new String[]', 1)[0]))
with sqlite3.connect(":memory:") as db:
    db.execute("CREATE TABLE event_candidates(id INTEGER PRIMARY KEY,start_at_millis INTEGER,end_at_millis INTEGER,all_day INTEGER)")
    db.executemany("INSERT INTO event_candidates VALUES(?,?,?,0)", [(i, 5, 9) for i in range(1, 201)])
    db.execute("INSERT INTO event_candidates VALUES(201,30,40,0)")
    query = f"SELECT id FROM event_candidates WHERE {selection} ORDER BY start_at_millis ASC,id ASC LIMIT 200"
    assert db.execute(query, ("100", "20", "20", "100", "20", "20")).fetchall() == [(201,)]
    db.execute("INSERT INTO event_candidates VALUES(202,0,24,1)")
    assert db.execute(query, ("100", "20", "20", "48", "0", "0")).fetchall() == [(202,), (201,)]
print("Data integrity checks passed: concurrent snapshot, attached triggers, revision, ID high water, foreign keys, restore status, widget history overflow and all-day bounds")
