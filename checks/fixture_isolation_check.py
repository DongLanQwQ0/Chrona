"""Run the production test-fixture guard on JVM stubs; never touch devices or accounts."""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/fixture-isolation-check'
OUT.mkdir(parents=True, exist_ok=True)
JAVA = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
stubs = {
    'Context.java': '''package android.content; public class Context {
      public String packageName="com.donglan.chrona.followup.validation.v33";
      public String getPackageName(){return packageName;}
    }''',
    'Build.java': '''package android.os; public class Build {
      public static String HARDWARE="ranchu", MODEL="sdk_gphone64_x86_64", FINGERPRINT="google/sdk_gphone64/test";
    }''',
    'FixtureIsolationCheck.java': '''package com.donglan.chrona;
import android.content.Context;
import android.os.Build;
class ZjuSettingsStore {
  static boolean enabled, configured; static String account="";
  ZjuSettingsStore(Context c){} boolean enabled(){return enabled;}
  boolean configured(){return configured;} String account(){return account;}
}
class WebDavSettingsStore {
  static boolean configured, automatic; WebDavSettingsStore(Context c){}
  boolean configured(){return configured;} boolean automatic(){return automatic;}
}
class LanAccessService {
  static boolean running, starting;
  static boolean running(){return running;} static boolean starting(){return starting;}
}
public class FixtureIsolationCheck {
  static int checks;
  static void check(Context c, boolean allowed) {
    boolean passed=true;
    try {FixtureIsolation.require(c);} catch(IllegalStateException expected){passed=false;}
    if(passed!=allowed)throw new AssertionError("Guard case "+checks);
    checks++;
  }
  public static void main(String[] args){
    Context c=new Context(); check(c,true);
    for(String pkg:new String[]{"com.donglan.chrona","com.donglan.chrona.validation",
        "com.donglan.chrona.followup.validation.v32"}){
      c.packageName=pkg;check(c,false);
    }
    c.packageName="com.donglan.chrona.followup.validation.v33";
    Build.HARDWARE="qcom";check(c,false);Build.HARDWARE="ranchu";
    Build.MODEL="V2527A";check(c,false);Build.MODEL="sdk_gphone64_x86_64";
    Build.FINGERPRINT="vivo/device/release";check(c,false);Build.FINGERPRINT="google/sdk_gphone64/test";
    ZjuSettingsStore.account="real-account";check(c,false);
    ZjuSettingsStore.account="00000000-0000-0000-0000-000000000036";check(c,true);
    ZjuSettingsStore.enabled=true;check(c,false);ZjuSettingsStore.enabled=false;
    ZjuSettingsStore.account="";ZjuSettingsStore.configured=true;check(c,false);ZjuSettingsStore.configured=false;
    WebDavSettingsStore.configured=true;check(c,false);WebDavSettingsStore.configured=false;
    WebDavSettingsStore.automatic=true;check(c,false);WebDavSettingsStore.automatic=false;
    LanAccessService.running=true;check(c,false);LanAccessService.running=false;
    LanAccessService.starting=true;check(c,false);LanAccessService.starting=false;
    check(c,true);System.out.println("FixtureIsolationCheck: "+checks+" cases passed");
  }
}''',
}
files = []
for name, text in stubs.items():
    path = OUT / name
    path.write_text(text, encoding='utf-8')
    files.append(str(path))
source = ROOT / 'app/src/androidTest/java/com/donglan/chrona'
subprocess.run([str(JAVA / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(OUT),
                *files, str(source / 'FixtureIsolation.java')], check=True)
subprocess.run([str(JAVA / 'java.exe'), '-cp', str(OUT), 'com.donglan.chrona.FixtureIsolationCheck'], check=True)
for name in ('RepeatDeviceCheck.java', 'BrowserDeviceCheck.java'):
    text = (source / name).read_text(encoding='utf-8')
    guard = text.index('FixtureIsolation.require(context)')
    for mutation in ('store.insertTask(', 'watchdog.start()', 'stop.delete()', 'test.startActivitySync('):
        if mutation in text:
            assert guard < text.index(mutation), (name, mutation)
print('Both fixture entry points guard before mutations')
