"""JSON-line bridge to real SQLite for production Android persistence checks.

Only the Android transport is replaced; SQL, schema, triggers and transactions
are executed by SQLite rather than an in-memory model of TaskStore behavior.
"""
import json
import sqlite3
import sys

sys.stdin.reconfigure(encoding='utf-8')
sys.stdout.reconfigure(encoding='utf-8')
db = sqlite3.connect(sys.argv[1], isolation_level=None)
for line in sys.stdin:
    try:
        request = json.loads(line)
        cursor = db.execute(request['sql'], request.get('args', []))
        result = {'rows': cursor.fetchall() if cursor.description else [],
                  'columns': [column[0] for column in cursor.description or []],
                  'id': cursor.lastrowid, 'count': cursor.rowcount}
    except Exception as error:
        result = {'error': str(error)}
    print(json.dumps(result), flush=True)
db.close()
