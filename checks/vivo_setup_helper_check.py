"""Compile the unchanged restricted setup runner against framework doubles; no phone writes."""
import os
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
out = root / 'build/vivo-setup-helper-check'
out.mkdir(parents=True, exist_ok=True)
sources = {
'android/os/Bundle.java': '''package android.os;
public class Bundle extends java.util.HashMap<String,String> {
 public void putString(String k,String v){put(k,v);}
 public String getString(String k,String fallback){return getOrDefault(k,fallback);}
}''',
'android/os/Build.java': '''package android.os;
public class Build {public static String MANUFACTURER="vivo", DISPLAY="fixture";}''',
'android/content/pm/PackageManager.java': '''package android.content.pm;
public class PackageManager {public static final int PERMISSION_GRANTED=0;}''',
'android/content/Context.java': '''package android.content;
public class Context {
 public String getPackageName(){return android.app.NotificationManager.target;}
 public <T> T getSystemService(Class<T> type){return type.cast(new android.app.NotificationManager());}
 public int checkSelfPermission(String p){return android.app.NotificationManager.permission ? 0 : -1;}
}''',
'android/app/UiAutomation.java': '''package android.app;
public class UiAutomation {
 public static boolean active; public static int adopts,drops;
 public void adoptShellPermissionIdentity(String p){if(!p.equals("android.permission.STATUS_BAR_SERVICE"))throw new AssertionError();active=true;adopts++;}
 public void dropShellPermissionIdentity(){active=false;drops++;}
}''',
'android/app/Instrumentation.java': '''package android.app;
public class Instrumentation {
 public static int resultCode; public static android.os.Bundle result;
 public void onCreate(android.os.Bundle b){} public void onStart(){} public void start(){}
 public android.content.Context getTargetContext(){return new android.content.Context();}
 public UiAutomation getUiAutomation(){return new UiAutomation();}
 public void finish(int code,android.os.Bundle b){resultCode=code;result=b;}
}''',
'android/util/Log.java': '''package android.util;
public class Log {public static String getStackTraceString(Throwable t){return t.toString()+java.util.Arrays.toString(t.getSuppressed());}}''',
'android/app/NotificationManager.java': '''package android.app;
public class NotificationManager {
 public static boolean state,permission,readFail,mismatch,writeFail,recoveryFail; public static int writes,reads;
 public static String target="com.donglan.chrona";
 public boolean getSceneStatus(String pkg,String scene){
  if(UiAutomation.active)throw new AssertionError("readback must use ordinary identity");
  if(!pkg.equals("com.donglan.chrona")||!scene.equals("CHRONA_AGENDA"))throw new AssertionError("scope");
  reads++; if(readFail)throw new IllegalStateException("unsupported read"); return state;
 }
 public void setSuperXInfosSceneList(java.util.List<String> scenes,java.util.List<String> enabled,
   java.util.List<String> packages,java.util.List<String> packageEnabled){
  if(!UiAutomation.active)throw new AssertionError("missing shell identity");
  if(!scenes.equals(java.util.List.of("CHRONA_AGENDA"))||!packages.equals(java.util.List.of("com.donglan.chrona"))
    ||!enabled.equals(packageEnabled))throw new AssertionError("scope");
  writes++; boolean next=Boolean.parseBoolean(enabled.get(0));
  if(!mismatch)state=next;
  if(writeFail&&writes==1||recoveryFail&&writes==2)throw new IllegalStateException("write failed");
 }
}''',
'HelperCheck.java': '''import android.app.*; import android.os.*; import org.json.JSONObject;
public class HelperCheck {
 static void require(boolean b,String message){if(!b)throw new AssertionError(message);}
 static void reset(boolean state){
  NotificationManager.state=state;NotificationManager.permission=true;NotificationManager.readFail=false;
  NotificationManager.mismatch=false;NotificationManager.writeFail=false;NotificationManager.recoveryFail=false;
  NotificationManager.writes=NotificationManager.reads=0;NotificationManager.target="com.donglan.chrona";
  UiAutomation.active=false;UiAutomation.adopts=UiAutomation.drops=0;Build.MANUFACTURER="vivo";
  Instrumentation.resultCode=99;Instrumentation.result=null;
 }
 static void run(String mode,boolean ok){
  var runner=new com.donglan.chrona.VivoAtomicSetupInstrumentation();
  Bundle args=new Bundle();if(mode!=null)args.putString("mode",mode);runner.onCreate(args);runner.onStart();
  require((Instrumentation.resultCode==-1)==ok,"result "+mode+" "+Instrumentation.result);
  require(!UiAutomation.active&&UiAutomation.adopts==UiAutomation.drops,"shell identity released");
 }
 public static void main(String[] unused)throws Exception{
  int checks=0;
  for(boolean before:new boolean[]{false,true})for(String mode:new String[]{"Enable","Disable","Status"}){
   reset(before);run(mode,true);var data=new JSONObject(Instrumentation.result.get("setup_report"));
   boolean expected=mode.equals("Status")?before:mode.equals("Enable");
   require(data.getBoolean("before")==before&&data.getBoolean("enabled")==expected,"readback");
   require(data.getBoolean("changed")== (before!=expected),"changed");
   require(NotificationManager.writes==(before==expected?0:1),"idempotent write");checks++;
  }
  reset(true);run(null,true);require(NotificationManager.writes==0,"default Status");checks++;
  reset(false);Build.MANUFACTURER="other";run("Enable",false);require(NotificationManager.writes==0,"manufacturer gate");checks++;
  reset(false);NotificationManager.target="another.app";run("Enable",false);require(NotificationManager.writes==0,"package gate");checks++;
  reset(false);run("Invalid",false);require(NotificationManager.writes==0,"mode gate");checks++;
  reset(false);NotificationManager.readFail=true;run("Enable",false);require(NotificationManager.writes==0,"read before write");checks++;
  reset(false);NotificationManager.permission=false;run("Enable",false);require(NotificationManager.writes==0&&!NotificationManager.state,"permission gate");checks++;
  reset(false);NotificationManager.writeFail=true;run("Enable",false);require(!NotificationManager.state&&NotificationManager.writes==2,"enable recovery");checks++;
  reset(true);NotificationManager.writeFail=true;run("Disable",false);require(NotificationManager.state&&NotificationManager.writes==2,"disable recovery");checks++;
  reset(false);NotificationManager.mismatch=true;run("Enable",false);require(!NotificationManager.state&&NotificationManager.writes==2,"unconfirmed recovery");checks++;
  reset(false);NotificationManager.writeFail=NotificationManager.recoveryFail=true;run("Enable",false);
  require(Instrumentation.result.get("error").contains("InvocationTargetException"),"failed recovery reported");checks++;
  System.out.println("Restricted setup runner: "+checks+" exact-source scenarios passed; scope, identity, modes, idempotency and recovery");
 }
}'''
}
files = []
for name, text in sources.items():
    file = out / name
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(text, encoding='utf-8')
    files.append(str(file))
java = Path(os.environ.get('JAVA_HOME', r'D:\Minecraft\java21')) / 'bin'
jar = root / 'build/ai-checks/json.jar'
runner = root / 'app/src/vivoSetup/java/com/donglan/chrona/VivoAtomicSetupInstrumentation.java'
subprocess.run([str(java/'javac.exe'), '-encoding', 'UTF-8', '-cp', str(jar), '-d', str(out), *files, str(runner)], check=True)
subprocess.run([str(java/'java.exe'), '-cp', os.pathsep.join((str(out),str(jar))), 'HelperCheck'], check=True)
