"""Real SQLite and production scheduling/parser, with a deterministic fake AI provider only."""
import ast
import os
from pathlib import Path
import shutil
import subprocess
import sys
from recurrence_dependencies import recurrence_java

root = Path(__file__).resolve().parents[1]
out = root / 'build/zju-homework-planner-check'
src = out / 'src'
src.mkdir(parents=True, exist_ok=True)
temporary = out / 'tmp'
temporary.mkdir(exist_ok=True)
os.environ['TEMP'] = os.environ['TMP'] = str(temporary)

def write(name, text):
    target = src / name
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text, encoding='utf-8')

tree = ast.parse((root / 'checks/candidate_merge_check.py').read_text(encoding='utf-8'))
needed = {'android/content/Context.java', 'android/content/ContentValues.java', 'android/database/Cursor.java',
          'android/database/sqlite/SQLiteDatabase.java', 'android/database/sqlite/SQLiteOpenHelper.java',
          'com/donglan/chrona/debug/DiagLog.java', 'com/donglan/chrona/image/ImageStore.java',
          'com/donglan/chrona/AgendaWidgetProvider.java', 'com/donglan/chrona/AgendaReminder.java',
          'com/donglan/chrona/AndroidSync.java'}
for node in tree.body:
    if isinstance(node, ast.Expr) and isinstance(node.value, ast.Call) and isinstance(node.value.func, ast.Name) and node.value.func.id == 'write':
        call = node.value
        if isinstance(call.args[0], ast.Constant) and call.args[0].value in needed:
            text = ast.literal_eval(call.args[1])
            if call.args[0].value == 'android/content/Context.java':
                text = text.replace('public class Context {', '''public class Context {
 public static final int MODE_PRIVATE=0;
 private final java.util.Map<String,SharedPreferences> preferences=new java.util.HashMap<>();
 public SharedPreferences getSharedPreferences(String name,int mode){return preferences.computeIfAbsent(name,key->new SharedPreferences());}
''')
            write(call.args[0].value, text)
completion_tree = ast.parse((root / 'checks/completion_alerts_check.py').read_text(encoding='utf-8'))
completion_sources = next(ast.literal_eval(node.value) for node in completion_tree.body
                         if isinstance(node, ast.Assign) and any(isinstance(t, ast.Name) and t.id == 'sources' for t in node.targets))
write('android/content/SharedPreferences.java', completion_sources['android/content/SharedPreferences.java'])
# Exercise the production immediate-revocation method, not a duplicate settings implementation.
(src / 'com/donglan/chrona/ZjuSettingsStore.java').unlink(missing_ok=True)
recovery_tree = ast.parse((root / 'checks/zju_sync_recovery_check.py').read_text(encoding='utf-8'))
crypto_adapters = {'android/util/Base64.java', 'android/security/keystore/KeyProperties.java',
                   'android/security/keystore/KeyGenParameterSpec.java'}
for node in recovery_tree.body:
    if isinstance(node, ast.Expr) and isinstance(node.value, ast.Call) and isinstance(node.value.func, ast.Name) and node.value.func.id == 'write':
        call = node.value
        if isinstance(call.args[0], ast.Constant) and call.args[0].value in crypto_adapters:
            write(call.args[0].value, ast.literal_eval(call.args[1]))
write('com/donglan/chrona/ai/AiSettingsStore.java', '''package com.donglan.chrona.ai;
public class AiSettingsStore {public AiSettingsStore(android.content.Context c){}public AiSettings load(){return null;}}
''')
write('com/donglan/chrona/ZjuHomeworkCalendar.java', '''package com.donglan.chrona;
public class ZjuHomeworkCalendar {static int retries;static boolean fail;public static void retryPending(android.content.Context c){if(fail)throw new IllegalStateException("synthetic provider down");retries++;}}
''')
write('com/donglan/chrona/TaskCompletion.java', '''package com.donglan.chrona;
public class TaskCompletion {public static void enqueue(android.content.Context c,long id){}public static void retryPending(android.content.Context c){}}
''')
production = root / 'app/src/main/java/com/donglan/chrona'
classes = root / 'app/build/intermediates/javac/debug/compileDebugJavaWithJavac/classes'
classpath = os.pathsep.join(map(str, (root / 'build/ai-checks/json.jar', classes)))
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
names = ('data/TaskStore.java', 'data/TaskRecord.java', 'data/ScheduleQuery.java', 'zju/ZjuSnapshot.java',
         'ZjuHomeworkStore.java', 'ZjuHomeworkPlanner.java', 'ZjuSettingsStore.java', 'HomeworkLinks.java', 'ai/AiSettings.java',
         'ai/ParseResult.java', 'ai/SchedulePreferences.java', 'ai/SchedulePrompt.java', 'ai/TimeAssessment.java',
         'ai/ChatCompletionClient.java', 'ai/SemanticEventNormalizer.java', 'ai/RequestOptions.java',
         'ai/StreamChunkContent.java', 'net/RequestControl.java')
sources = list(src.rglob('*.java')) + [production / name for name in names] + [root / 'checks' / name for name in
    ('ZjuHomeworkPlannerCheck.java', 'ParseAuditStoreCheck.java', 'SnapshotBackupCheck.java')]
(out/'databases').mkdir(exist_ok=True)
subprocess.run(recurrence_java([str(java/'javac.exe'), f'-J-Djava.io.tmpdir={temporary}', '--release', '17', '-encoding', 'UTF-8', '-cp', classpath,
                              '-d', str(out), *map(str, sources)]), check=True)
checks = ('ZjuHomeworkPlannerCheck',) if '--planner-only' in sys.argv else ('ZjuHomeworkPlannerCheck', 'ParseAuditStoreCheck', 'SnapshotBackupCheck')
for name in checks:
    subprocess.run(recurrence_java([str(java/'java.exe'), f'-Djava.io.tmpdir={temporary}', f'-Dpython={shutil.which("python")}',
                                  f'-Dbridge={root / "checks/sqlite_bridge.py"}', '-cp', str(out)+os.pathsep+classpath,
                                  'com.donglan.chrona.'+name, str(out/'databases')]), check=True)
