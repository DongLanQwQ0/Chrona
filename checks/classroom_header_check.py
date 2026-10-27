"""Run the production classroom load method: local reads must not advertise remote cancellation."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'app/src/main/java/com/donglan/chrona/ClassroomSummaryPage.java').read_text(encoding='utf-8')
start = source.index('        void load(Load kind) {')
end = source.index('{', start) + 1
depth = 1
while depth:
    depth += (source[end] == '{') - (source[end] == '}')
    end += 1
method = source[start:end]
code = r'''
import java.util.*;
public class ClassroomHeaderCheck {
 static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 enum Load { CACHE, LOCAL, REFRESH, AUTOMATIC }
 static final long CATALOG_REFRESH_MILLIS=300000;
 static class android {static class os {static class SystemClock {static long elapsedRealtime(){return 100000;}}}}
 static class com {static class donglan {static class chrona {static class net {static class NetworkFailure {
  static boolean hasDetails(Exception e){return false;}static String describe(Exception e){return e.getMessage();}
 }}}}}
 static class ClassroomLesson{}static class JSONObject{}
 static class Queue {List<Runnable> tasks=new ArrayList<>();void execute(Runnable task){tasks.add(task);}void drain(){while(!tasks.isEmpty())tasks.remove(0).run();}}
 static class ZjuSettingsStore {ZjuSettingsStore(Object app){}String[] credentials(){return new String[]{"fixture","fixture"};}}
 static class ClassroomStore {
  static List<ClassroomLesson> rows;static List<JSONObject> history;static boolean fail;static int writes;
  ClassroomStore(Object app,String account){}List<ClassroomLesson> catalog()throws Exception{if(fail)throw new Exception("local failure");return rows;}
  List<JSONObject> history(){return history;}long catalogUpdatedAt(){return 1;}void catalog(List<ClassroomLesson> value){rows=value;writes++;}
 }
 static class ClassroomClient {
  static int calls,closes;static boolean fail;static Runnable during;
  ClassroomClient(String a,String b){}void cancel(){}void close(){closes++;}
  List<ClassroomLesson> lessons()throws Exception{calls++;if(during!=null)during.run();if(fail)throw new Exception("remote failure");return List.of(new ClassroomLesson());}
 }
 final Object app=new Object();final String account="owned";final Queue worker=new Queue();
 ClassroomClient client;boolean busy,refreshing,cancelled,initialized=true,permitted=true;long nextRemoteAt;String message="";
 List<ClassroomLesson> catalog=new ArrayList<>();List<JSONObject> history=new ArrayList<>();List<String> signals=new ArrayList<>();
 boolean allowed(){return permitted;}void publish(){signals.add(busy+":"+refreshing);}
 void accept(List<ClassroomLesson> c,List<JSONObject> h){catalog=c;history=h;initialized=true;}
 void cancel(){cancelled=true;if(client!=null)client.cancel();}
 METHOD
 static ClassroomHeaderCheck fresh(){ClassroomStore.rows=List.of(new ClassroomLesson());ClassroomStore.history=List.of();ClassroomStore.fail=false;ClassroomStore.writes=0;ClassroomClient.calls=0;ClassroomClient.closes=0;ClassroomClient.fail=false;ClassroomClient.during=null;return new ClassroomHeaderCheck();}
 public static void main(String[] args){
  for(Load kind:new Load[]{Load.CACHE,Load.LOCAL}){
   ClassroomHeaderCheck s=fresh();s.load(kind);check(s.busy&&!s.refreshing,"queued local work has no cancel state");s.worker.drain();
   check(!s.busy&&!s.refreshing&&ClassroomClient.calls==0,"local load finishes without network");
   check(s.signals.stream().noneMatch(v->v.endsWith(":true")),"no publication flashes remote busy during local work");
  }
  for(Load kind:new Load[]{Load.REFRESH,Load.AUTOMATIC}){
   ClassroomHeaderCheck s=fresh();s.load(kind);check(s.busy&&s.refreshing,"remote action is cancellable immediately");
   ClassroomClient.during=()->check(s.busy&&s.refreshing,"actual remote request keeps cancellation state");s.worker.drain();
   check(!s.busy&&!s.refreshing&&ClassroomClient.calls==1&&ClassroomClient.closes==1,"remote completion clears both states");
  }
  ClassroomHeaderCheck s=fresh();s.initialized=false;s.load(Load.CACHE);s.worker.drain();check(ClassroomClient.calls==0&&!s.signals.contains("true:true"),"cached first load stays local");
  s=fresh();s.initialized=false;ClassroomStore.rows=List.of();s.load(Load.CACHE);check(!s.refreshing,"empty first load begins with local discovery");s.worker.drain();check(ClassroomClient.calls==1&&s.signals.contains("true:true")&&!s.refreshing,"empty first load advertises cancellation only after real remote fallback");
  s=fresh();ClassroomClient.fail=true;s.load(Load.REFRESH);s.worker.drain();check(!s.busy&&!s.refreshing&&s.message.equals("remote failure"),"remote failure clears cancel state and retains error");
  s=fresh();ClassroomStore.fail=true;s.load(Load.LOCAL);s.worker.drain();check(!s.busy&&!s.refreshing&&!s.signals.contains("true:true"),"failed local load never becomes cancellable network work");
  s=fresh();s.permitted=false;s.load(Load.REFRESH);check(!s.busy&&!s.refreshing&&s.worker.tasks.isEmpty(),"disabled account starts no work");
  s=fresh();s.load(Load.CACHE);s.load(Load.REFRESH);check(s.worker.tasks.size()==1&&!s.refreshing,"overlapping refresh cannot relabel a queued local read");s.worker.drain();
  final ClassroomHeaderCheck cancelled=fresh();ClassroomClient.during=cancelled::cancel;cancelled.load(Load.REFRESH);cancelled.worker.drain();check(!cancelled.busy&&!cancelled.refreshing&&ClassroomStore.writes==0,"cancellation skips cache replacement and clears visual state");
  System.out.println("PASS: "+checks+" production classroom load-state assertions");
 }
}
'''.replace('METHOD', method)
out = ROOT / 'build/classroom-header-check';out.mkdir(parents=True, exist_ok=True)
java = Path(os.environ['JAVA_HOME']) / 'bin'
file = out / 'ClassroomHeaderCheck.java';file.write_text(code, encoding='utf-8')
subprocess.run([str(java / 'javac'), '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java'), '-cp', str(out), 'ClassroomHeaderCheck'], check=True)
