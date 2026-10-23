"""Exercise the actual audit GET branch and TaskStore over real isolated SQLite.

Run candidate_merge_check.py first to prepare the existing Android/SQLite JVM adapters.
Transport/authentication are existing LAN boundaries; this checks route/query behavior.
"""
from pathlib import Path
import os
import shutil
import subprocess
from recurrence_dependencies import recurrence_classpath

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/parse-audit-api-check'
OUT.mkdir(parents=True, exist_ok=True)
ADAPTERS = ROOT / 'build/candidate-merge-checks'
JAVA = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
source = (ROOT / 'app/src/main/java/com/donglan/chrona/LanWebServer.java').read_text(encoding='utf-8')

def block(marker):
    start = source.index(marker)
    opening = source.index('{', start)
    end, depth = opening + 1, 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

assert source.index('if (!security.authenticated(') < source.index('if (method == Method.GET) return read(')
route = block('if (path.equals("/api/parse-audit")) {')
helpers = '\n'.join(block(marker) for marker in ('private static long positive(', 'private static int integer('))
harness = '''package com.donglan.chrona;
import java.util.*;import java.io.*;import java.nio.file.Files;import org.json.*;
import android.content.Context;import com.donglan.chrona.data.*;
public final class ParseAuditApiCheck {
 static int checks;
 static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
 static class Request {final Map<String,String> params;Request(Map<String,String> p){params=p;}Map<String,String> getParms(){return params;}}
 static class Response {enum Status {NOT_FOUND}}
 static JSONObject data(JSONObject value){return value;}
 static JSONObject json(Response.Status status,String message)throws Exception{return new JSONObject().put("status",404).put("error",message);}
 HELPERS
 static JSONObject read(TaskStore store,Map<String,String> params)throws Exception {
  Request request=new Request(params);String path="/api/parse-audit";JSONObject result=new JSONObject();
  ROUTE
  throw new AssertionError("audit route not reached");
 }
 static void rejected(TaskStore store,Map<String,String> params)throws Exception {
  try{read(store,params);throw new AssertionError("invalid query accepted: "+params);}catch(IllegalArgumentException expected){checks++;}
 }
 public static void main(String[] args)throws Exception {
  Context context=new Context(Files.createTempDirectory(new File(args[0]).toPath(),"audit-api-").toFile());
  try(TaskStore store=new TaskStore(context)) {
   long id=store.insertTask("合成审计",null,"test",1);String task=Long.toString(id);
   check(read(store,Map.of("id",task)).getJSONArray("audits").length()==0,"old source has no invented audit");
   String run=store.beginParseRun(id,1,new JSONObject().put("model","test"));
   store.failParseRun(run,false,"ResponseException");
   JSONObject response=read(store,Map.of("id",task,"runId",run));
   check(response.getJSONArray("audits").getJSONObject(0).getString("runId").equals(run),"exact run query");
   check(response.getInt("offset")==0&&response.getInt("limit")==10,"bounded route defaults");
   check(read(store,Map.of("id",task,"offset","1","limit","1")).getJSONArray("audits").length()==0,"paging respected");
   check(read(store,Map.of("id",task,"candidateId","9999")).getJSONArray("audits").length()==0,"candidate filter respected");
   check(read(store,Map.of("id","9999")).getInt("status")==404,"missing source returns not found");
   for(Map<String,String> params:List.of(Map.of("id","0"),Map.of("id",task,"limit","21"),
     Map.of("id",task,"offset","-1"),Map.of("id",task,"runId","bad"),
     Map.of("id",task,"planGroup","方案"),Map.of("id",task,"runId",run,"planGroup"," "),
     Map.of("id",task,"candidateId","-1")))rejected(store,params);
  }
  System.out.println("Parse audit API: "+checks+" checks passed; actual GET branch and TaskStore/SQLite, transport boundary excluded");
 }
}'''.replace('HELPERS', helpers).replace('ROUTE', route)
target = OUT / 'ParseAuditApiCheck.java'
target.write_text(harness, encoding='utf-8')
cp = os.pathsep.join([str(OUT), str(ADAPTERS), recurrence_classpath()])
subprocess.run([str(JAVA / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-cp', cp,
                '-d', str(OUT), str(ROOT / 'app/src/main/java/com/donglan/chrona/data/TaskStore.java'), str(target)], check=True)
subprocess.run([str(JAVA / 'java.exe'), f'-Dpython={shutil.which("python")}', f'-Dbridge={ROOT / "checks/sqlite_bridge.py"}',
                '-cp', cp, 'com.donglan.chrona.ParseAuditApiCheck', str(OUT)], check=True)
