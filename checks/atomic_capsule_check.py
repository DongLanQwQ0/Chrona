"""Execute production capsule timing and payload projection; OEM rendering needs the vivo phone."""
import os
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
gradle = (root / 'app/build.gradle').read_text(encoding='utf-8')


def gradle_block(name):
    start = re.search(r'\b' + re.escape(name) + r'\s*\{', gradle)
    assert start, f'Missing Gradle block: {name}'
    depth = 1
    end = start.end()
    while depth:
        assert end < len(gradle), f'Unclosed Gradle block: {name}'
        depth += (gradle[end] == '{') - (gradle[end] == '}')
        end += 1
    return gradle[start.end():end - 1]


def configured_durations(block):
    return {
        name: int(value)
        for name, value in re.findall(
            r"buildConfigField\s+'long',\s*'(CAPSULE_DESKTOP_MILLIS)',\s*'(\d+)L'", block
        )
    }


release_durations = configured_durations(gradle_block('defaultConfig'))
short_durations = configured_durations(gradle_block('capsuleTest'))
assert release_durations == {'CAPSULE_DESKTOP_MILLIS': 30000}
assert short_durations == {'CAPSULE_DESKTOP_MILLIS': 5000}
test_build = gradle_block('capsuleTest')
assert re.search(r'\binitWith\s+release\b', test_build)
assert "versionNameSuffix '-capsule-test-5s'" in test_build
assert not re.search(r'\b(?:applicationId|applicationIdSuffix|signingConfig)\b', test_build)
assert not configured_durations(gradle_block('release'))
assert len(re.findall(r"buildConfigField\s+'long',\s*'CAPSULE_DESKTOP_MILLIS'", gradle)) == 2
assert 'CAPSULE_CLICK_MILLIS' not in gradle
assert 'buildConfig true' in gradle
assert 'signingConfig signingConfigs.debug' in gradle_block('release')
source = (root / 'app/src/main/java/com/donglan/chrona/AtomicCapsuleController.java').read_text(encoding='utf-8')
assert 'WAKE_MILLIS = BuildConfig.CAPSULE_DESKTOP_MILLIS' in source
assert 'CLICK_MILLIS' not in source and 'capsuleClicked' not in source
assert 'AtomicCapsuleIdentity' not in source and 'AtomicCapsuleAccessibilityService' not in source
source = re.sub(r'^package .*;\s*|^import .*;\s*', '', source, flags=re.M)
source = source.replace('public final class AtomicCapsuleController', 'final class AtomicCapsuleController')
source = source.replace('android.os.Build.VERSION.SDK_INT', '36').replace('android.util.Log', 'Log')
harness = r'''
import java.util.*;
import java.text.BreakIterator;
__BUILD_CONFIG__
class Bundle extends HashMap<String,Object>{Bundle(){}Bundle(Bundle b){putAll(b);}void putInt(String k,int v){put(k,v);}int getInt(String k){return (int)get(k);}void putString(String k,String v){put(k,v);}String getString(String k){return (String)get(k);}String getString(String k,String v){return (String)getOrDefault(k,v);}void putBoolean(String k,boolean v){put(k,v);}void putParcelable(String k,Object v){put(k,v);}void putCharSequence(String k,CharSequence v){put(k,v);}CharSequence getCharSequence(String k){return (CharSequence)get(k);}void putBundle(String k,Bundle v){put(k,v);}Bundle getBundle(String k){return (Bundle)get(k);}}
class SharedPreferences{Map<String,Object> values=new HashMap<>();int getInt(String k,int d){return (int)values.getOrDefault(k,d);}long getLong(String k,long d){return (long)values.getOrDefault(k,d);}String getString(String k,String d){return (String)values.getOrDefault(k,d);}boolean contains(String k){return values.containsKey(k);}Map<String,?>getAll(){return new HashMap<>(values);}Editor edit(){return new Editor();}class Editor{Map<String,Object> pending=new HashMap<>();boolean clear;Editor clear(){clear=true;return this;}Editor putInt(String k,int v){pending.put(k,v);return this;}Editor putLong(String k,long v){pending.put(k,v);return this;}Editor putString(String k,String v){pending.put(k,v);return this;}Editor remove(String k){pending.put(k,null);return this;}boolean commit(){if(clear)values.clear();pending.forEach((k,v)->{if(v==null)values.remove(k);else values.put(k,v);});return true;}}}
class Context{static final int MODE_PRIVATE=0;SharedPreferences prefs=new SharedPreferences();AlarmManager alarms=new AlarmManager();PowerManager power=new PowerManager();NotificationManager notifications=new NotificationManager();Context getApplicationContext(){return this;}SharedPreferences getSharedPreferences(String n,int m){return prefs;}Object getContentResolver(){return this;}<T>T getSystemService(Class<T> c){return c.cast(c==AlarmManager.class?alarms:c==PowerManager.class?power:notifications);}}
class Settings{static class Global{static int boot=5;static String BOOT_COUNT="boot";static int getInt(Object r,String k,int d){return boot;}}}
class SystemClock{static long now=1000;static long elapsedRealtime(){return now;}}
class Looper{static Looper getMainLooper(){return new Looper();}}
class Handler{static Runnable delayed;static long delay;Handler(Looper l){}void post(Runnable r){r.run();}void removeCallbacks(Runnable r){if(delayed==r)delayed=null;}void postDelayed(Runnable r,long d){delayed=r;delay=d;}}
class PowerManager{boolean awake=true;boolean isInteractive(){return awake;}}
class AlarmManager{static int ELAPSED_REALTIME_WAKEUP=2;long at;boolean exact=true,deny;void cancel(PendingIntent p){at=0;}boolean canScheduleExactAlarms(){return exact;}void setExact(int t,long a,PendingIntent p){if(deny)throw new SecurityException();at=a;}void set(int t,long a,PendingIntent p){at=a;}}
class PendingIntent{static int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;Intent intent;int flags;static PendingIntent getBroadcast(Context c,int id,Intent i,int flags){PendingIntent p=new PendingIntent();p.intent=i;p.flags=flags;return p;}}
class Uri{String text="",scheme,authority;List<String>paths=new ArrayList<>();String getScheme(){return scheme;}String getAuthority(){return authority;}List<String>getPathSegments(){return paths;}static class Builder{Uri u=new Uri();Builder scheme(String s){u.scheme=s;u.text=s+":";return this;}Builder authority(String s){u.authority=s;u.text+="//"+s;return this;}Builder appendPath(String s){u.paths.add(s);u.text+="/"+s;return this;}Uri build(){return u;}}}
class Intent{String action;Uri data;Map<String,Object>extras=new HashMap<>();Intent(Context c,Class<?>cls){}Intent setAction(String a){action=a;return this;}String getAction(){return action;}Intent setData(Uri d){data=d;return this;}Uri getData(){return data;}Intent putExtra(String k,int v){extras.put(k,v);return this;}Intent putExtra(String k,String v){extras.put(k,v);return this;}int getIntExtra(String k,int d){return (int)extras.getOrDefault(k,d);}String getStringExtra(String k){return (String)extras.get(k);}}
class BroadcastReceiver{static int finished;class PendingResult{void finish(){finished++;}}PendingResult goAsync(){return new PendingResult();}public void onReceive(Context c,Intent i){}}
class NotificationManager{Set<Integer> active=new HashSet<>();}
class VivoAtomicNotification{static boolean vivo=true;static boolean supportedManufacturer(){return vivo;}static boolean active(NotificationManager m,int id){return m.active.contains(id);}}
class HomeworkNotice{static int forced;static void refreshCapsule(Context c){forced++;}}
class AgendaReminder{static java.util.concurrent.Executor WORKER=Runnable::run;static int requests,refreshes;static Runnable onRefresh=()->{};static void requestRefresh(Context c){requests++;}static void refreshSafely(Context c,Intent i){refreshes++;onRefresh.run();}}
class Log{static void w(String a,String b,Exception e){}}
__SOURCE__
public class AtomicCapsuleCheck{
 static int checks;static String P="notification.superx.",M="chrona.atomic.";
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static Bundle full(){Bundle data=new Bundle(),base=new Bundle(),island=new Bundle(),left=new Bundle(),right=new Bundle(),capsule=new Bundle();base.putString(P+"baseInfos.title","数字音视频技术课程长标题");data.putBundle(P+"baseInfos",base);left.putString("island.superx.leftInfo.content","12:00");left.putString("icon","unchanged");right.putString("island.superx.rightInfo.content","数字音视频技术课程长标题");island.putBundle("island.superx.leftInfo",left);island.putBundle("island.superx.rightInfo",right);island.putString("card","full-card-unchanged");data.putBundle(P+"island",island);capsule.putString(P+"capsule.content","12:00 数字音视频技术课程长标题");data.putBundle(P+"capsule",capsule);return data;}
 static String right(Bundle d){return d.getBundle(P+"island").getBundle("island.superx.rightInfo").getCharSequence("island.superx.rightInfo.content").toString();}
 static String left(Bundle d){return d.getBundle(P+"island").getBundle("island.superx.leftInfo").getCharSequence("island.superx.leftInfo.content").toString();}
 static Bundle decorate(Context c,int id,String identity){return AtomicCapsuleController.decorate(c,id,identity,full());}
 public static void main(String[]args){
  Context c=new Context();AtomicCapsuleController.initialize(c);check(c.alarms.at==0,"no empty background alarm");
  Bundle first=decorate(c,10,"occurrence-A");check(right(first).equals("数字音视频技术课程长标题"),"new full title");check(c.alarms.at==31000,"30 second deadline");
  check(!AtomicCapsuleController.compact(30999,31000,0)&&AtomicCapsuleController.compact(31000,31000,0),"exact wake boundary");
  SystemClock.now=20000;decorate(c,10,"occurrence-A");check(c.alarms.at==31000,"ordinary refresh does not extend deadline");
  SystemClock.now=31000;Bundle small=decorate(c,10,"occurrence-A");check(right(small).equals("数字"),"two character title");check(left(small).isEmpty(),"compact removes time width");check(small.getBundle(P+"capsule").getString(P+"capsule.content").equals("数字"),"non-island capsule is compact too");check(small.getBundle(P+"baseInfos").getString(P+"baseInfos.title").endsWith("长标题")&&small.getBundle(P+"island").getString("card").equals("full-card-unchanged"),"card and normal notification remain complete");
  Bundle cloned=new Bundle(small);AtomicCapsuleController.countdown(cloned,"12秒");AtomicCapsuleController.apply(c,cloned);check(right(cloned).equals("数字")&&left(cloned).isEmpty(),"seconds cannot expand compact title");check(small.getString(M+"left").equals("12:00"),"snapshot metadata not mutated");
  SystemClock.now=32000;AtomicCapsuleController.screenOn(c,SystemClock.now);Bundle awake=decorate(c,10,"occurrence-A");check(right(awake).endsWith("长标题")&&left(awake).equals("12:00"),"screen on restores complete content");check(c.alarms.at==62000,"wake creates 30 second boundary");
  Bundle original=full();Object details=new Object();original.getBundle(P+"island").putInt("island.superx.islandClick",0);original.getBundle(P+"island").putParcelable("island.superx.clickResp",details);original.getBundle(P+"capsule").putParcelable(P+"capsule.clickResp",details);AtomicCapsuleController.decorate(c,10,"occurrence-A",original);
  check(original.getBundle(P+"island").getInt("island.superx.islandClick")==0,"native tap opens card");check(original.getBundle(P+"island").get("island.superx.clickResp")==details&&original.getBundle(P+"capsule").get(P+"capsule.clickResp")==details,"original detail actions preserved");check(!original.getBundle(P+"island").containsKey("island.superx.forceShowCard"),"never force-open or block outside dismissal");
  int refreshes=AgendaReminder.refreshes;new AtomicCapsuleController().onReceive(c,new Intent(c,AtomicCapsuleController.class).setAction("capsule_click"));check(AgendaReminder.refreshes==refreshes,"obsolete click broadcasts cannot open or restore notices");
  SystemClock.now=40000;AtomicCapsuleController.initialize(c);check(c.prefs.getLong("until:10",0)==31000&&c.prefs.getLong("wake_until",0)==62000,"recovery preserves running wake window");
  SystemClock.now=61999;AtomicCapsuleController.apply(c,original);check(right(original).endsWith("长标题"),"wake window remains full");SystemClock.now=62000;AtomicCapsuleController.apply(c,original);check(right(original).equals("数字"),"wake window expires at 30 seconds");
  AtomicCapsuleController.clear(c,10);check(!c.prefs.contains("identity:10")&&c.alarms.at==0,"clear cancels final presentation alarm");
  SystemClock.now=110000;decorate(c,11,"slot-B");long persisted=c.prefs.getLong("until:11",0);AtomicCapsuleController.initialize(c);check(c.prefs.getLong("until:11",0)==persisted,"process initialization preserves presentation deadline");
  c.power.awake=false;AtomicCapsuleController.screenOff(c);check(c.alarms.at==0&&Handler.delayed==null,"screen off stops presentation wakeups");c.power.awake=true;SystemClock.now=150000;AtomicCapsuleController.screenOn(c,SystemClock.now);decorate(c,11,"slot-B");check(c.alarms.at==180000,"next actual wake resets 30 seconds");
  c.alarms.exact=false;decorate(c,11,"slot-B");check(c.alarms.at==180000&&Handler.delay==30000,"no exact permission retains handler and durable inexact alarm");c.alarms.exact=true;c.alarms.deny=true;decorate(c,11,"slot-B");check(c.alarms.at==180000,"permission revocation does not crash notification");
  c.alarms.deny=false;SystemClock.now=200000;int forces=HomeworkNotice.forced;AtomicCapsuleController.initialize(c);check(c.prefs.getLong("wake_until",0)==230000,"interactive recovery after missed wake gets one reading window");check(HomeworkNotice.forced==forces+1,"recovered homework is republished during full window, not only at its end");
  new AtomicCapsuleController().onReceive(c,new Intent(c,AtomicCapsuleController.class).setAction("capsule_expire"));check(AgendaReminder.refreshes==refreshes+1,"expiry still refreshes real notifications");
  Settings.Global.boot++;SystemClock.now=5;AtomicCapsuleController.initialize(c);check(!c.prefs.contains("identity:11")&&c.prefs.getLong("wake_until",0)==30005,"reboot invalidates old monotonic timestamps");
  check(AtomicCapsuleController.shortTitle("").isEmpty()&&AtomicCapsuleController.shortTitle("课").equals("课")&&AtomicCapsuleController.shortTitle("课程").equals("课程"),"zero to two characters unchanged");check(AtomicCapsuleController.shortTitle("😀课表").equals("😀课"),"surrogate pair not split");check(AtomicCapsuleController.shortTitle("a\u0301bc").equals("a\u0301b"),"combining mark remains attached");
  SystemClock.now=100000;decorate(c,20,"exit-A");
  SystemClock.now=140000;Bundle exited=decorate(c,20,"exit-A");check(right(exited).equals("数字"),"existing notice is compact before leaving app");
  AtomicCapsuleController.appExited(c,SystemClock.now);AtomicCapsuleController.apply(c,exited);
  check(c.prefs.getLong("wake_until",0)==170000&&right(exited).endsWith("长标题"),"leaving Chrona restores full capsule for 30 seconds");
  SystemClock.now=150000;new AtomicCapsuleController().onReceive(c,new Intent(c,AtomicCapsuleController.class).setAction("capsule_click"));
  check(c.prefs.getLong("wake_until",0)==170000&&c.prefs.getLong("until:20",0)==130000,"native click has no app timer and cannot renew either window");
  AtomicCapsuleController.initialize(c);check(c.prefs.getLong("wake_until",0)==170000,"recovery preserves app exit window");
  SystemClock.now=169999;AtomicCapsuleController.apply(c,exited);check(right(exited).endsWith("长标题"),"app exit remains full just before 30 seconds");
  SystemClock.now=170000;AtomicCapsuleController.apply(c,exited);check(right(exited).equals("数字"),"app exit contracts exactly at 30 seconds");
  c.power.awake=false;AtomicCapsuleController.screenOff(c);SystemClock.now=180000;AtomicCapsuleController.appExited(c,SystemClock.now);
  check(c.prefs.getLong("wake_until",0)==170000&&c.alarms.at==0,"screen-off activity stop does not create a reading window");
  c.power.awake=true;VivoAtomicNotification.vivo=false;AtomicCapsuleController.appExited(c,SystemClock.now);
  check(c.prefs.getLong("wake_until",0)==170000,"other manufacturers do not create capsule windows");
  VivoAtomicNotification.vivo=true;AtomicCapsuleController.clear(c,20);
  System.out.println("Atomic capsule: PASS ("+checks+" production-controller checks; OEM display pending)");
 }
}
class AtomicCapsuleShortCheck{
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[]args){
  check(AtomicCapsuleController.WAKE_MILLIS==5000,"short variant BuildConfig reaches actual controller");
  Context c=new Context();SystemClock.now=1000;AtomicCapsuleController.initialize(c);
  Bundle data=AtomicCapsuleCheck.decorate(c,50,"short-A");
  check(c.prefs.getLong("until:50",0)==6000&&c.alarms.at==6000,"new capsule receives five seconds");
  SystemClock.now=2000;AtomicCapsuleController.appExited(c,SystemClock.now);
  check(c.prefs.getLong("wake_until",0)==7000&&c.alarms.at==7000,"app exit receives five seconds");
  SystemClock.now=4000;AtomicCapsuleCheck.decorate(c,50,"short-A");
  check(c.prefs.getLong("until:50",0)==6000&&c.prefs.getLong("wake_until",0)==7000&&c.alarms.at==7000,"ordinary refresh renews neither deadline");
  SystemClock.now=6999;AtomicCapsuleController.apply(c,data);check(AtomicCapsuleCheck.right(data).endsWith("长标题"),"app exit remains expanded before five-second boundary");
  SystemClock.now=7000;AtomicCapsuleController.apply(c,data);check(AtomicCapsuleCheck.right(data).equals("数字")&&AtomicCapsuleCheck.left(data).isEmpty(),"app exit contracts exactly at five seconds");
  SystemClock.now=8000;new AtomicCapsuleController().onReceive(c,new Intent(c,AtomicCapsuleController.class).setAction("capsule_click"));
  check(c.prefs.getLong("until:50",0)==6000&&c.prefs.getLong("wake_until",0)==7000,"click does not renew short window");
  AtomicCapsuleController.apply(c,data);check(AtomicCapsuleCheck.right(data).equals("数字"),"click leaves expired capsule compact");
  SystemClock.now=9000;AtomicCapsuleController.screenOn(c,SystemClock.now);
  check(c.prefs.getLong("wake_until",0)==14000&&c.alarms.at==14000,"screen on restores five seconds");
  SystemClock.now=12000;AtomicCapsuleCheck.decorate(c,50,"short-A");
  check(c.prefs.getLong("until:50",0)==6000&&c.prefs.getLong("wake_until",0)==14000,"refresh cannot renew item or reading window");
  AtomicCapsuleController.initialize(c);check(c.prefs.getLong("wake_until",0)==14000,"recovery preserves five-second screen window");
  SystemClock.now=13999;AtomicCapsuleController.apply(c,data);check(AtomicCapsuleCheck.right(data).endsWith("长标题"),"screen window remains full before five seconds");
  SystemClock.now=14000;AtomicCapsuleController.apply(c,data);check(AtomicCapsuleCheck.right(data).equals("数字"),"screen window contracts exactly at five seconds");
  check(data.getBundle(AtomicCapsuleCheck.P+"island").getString("card").equals("full-card-unchanged"),"native expanded card stays complete after contraction");
  check(!data.getBundle(AtomicCapsuleCheck.P+"island").containsKey("island.superx.forceShowCard"),"timing never forces native card open or closed");
  AtomicCapsuleController.clear(c,50);
  check(!c.prefs.contains("identity:50")&&!c.prefs.contains("until:50")&&c.alarms.at==0&&Handler.delayed==null,"ended notice clears identity, deadline and scheduled work");
  System.out.println("Atomic capsule capsuleTest: PASS ("+checks+" short-duration production-controller checks; OEM display pending)");
 }
}
'''.replace('__SOURCE__', source)
java = Path(os.environ['JAVA_HOME']) / 'bin'
for variant, durations, main in (
    ('release', release_durations, 'AtomicCapsuleCheck'),
    ('capsuleTest', short_durations, 'AtomicCapsuleShortCheck'),
):
    # Compile the unchanged controller against each variant's BuildConfig values,
    # not a source rewrite of its presentation constants or its existing assertions.
    config = 'class BuildConfig{' + ''.join(
        f'static final long {name}={value}L;' for name, value in durations.items()
    ) + '}'
    out = root / 'build/atomic-capsule-30s/check' / variant
    out.mkdir(parents=True, exist_ok=True)
    file = out / 'AtomicCapsuleCheck.java'
    file.write_text(harness.replace('__BUILD_CONFIG__', config), encoding='utf-8')
    subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
    subprocess.run([str(java / 'java.exe'), '-cp', str(out), main], check=True)
print('Build variants: PASS (release 30s; isolated capsuleTest 5s and version suffix; source contracts)', flush=True)

# The host doubles above execute the controller; these contracts verify every production publisher.
def read(name):
    return (root / 'app/src/main/java/com/donglan/chrona' / name).read_text(encoding='utf-8')
assert 'AtomicCapsuleController.decorate(context, notificationId, e.key() + "@" + notifiedAt' in read('AgendaReminder.java')
assert 'AtomicCapsuleController.decorate(context, ID, "homework:" + expiry' in read('HomeworkNotice.java')
updates = read('AtomicCountdownUpdates.java')
assert updates.index('AtomicCapsuleController.apply(context, data)') < updates.index('manager.notify(item.getKey()')
assert 'putBoolean("island.superx.forceShowCard", false)' in updates
assert 'AtomicCapsuleController.clear(context, id)' in read('VivoAtomicNotification.java')
assert 'HomeworkNotice.activeAtomic(context)' in read('AgendaReminderLiveService.java')
print('Publisher/seconds/cleanup/liveness wiring: PASS (source contracts, not device interaction)')

# Execute the actual app-local lifecycle methods, including internal navigation and rotation.
app = read('ChronaApp.java')
assert 'private int startedActivities;' in app


def lifecycle_method(name):
    start = app.index('public void ' + name + '(')
    brace = app.index('{', start)
    depth, end = 1, brace + 1
    while depth:
        depth += (app[end] == '{') - (app[end] == '}')
        end += 1
    return app[start:end].replace('android.os.SystemClock', 'SystemClock')


lifecycle = r'''
class Activity { boolean rotating; boolean isChangingConfigurations(){return rotating;} }
class SystemClock { static long elapsedRealtime(){return 12345;} }
class AtomicCapsuleController { static int exits; static long at; static void appExited(ChronaApp app,long time){exits++;at=time;} }
class ChronaApp {
 private int startedActivities;
 class Lifecycle {
 __STARTED__
 __STOPPED__
 }
}
public class AtomicCapsuleLifecycleCheck {
 static int checks;
 static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  ChronaApp app=new ChronaApp();ChronaApp.Lifecycle lifecycle=app.new Lifecycle();
  Activity first=new Activity(),second=new Activity();
  lifecycle.onActivityStarted(first);lifecycle.onActivityStarted(second);lifecycle.onActivityStopped(first);
  check(AtomicCapsuleController.exits==0,"internal navigation does not renew capsule");
  lifecycle.onActivityStopped(second);
  check(AtomicCapsuleController.exits==1&&AtomicCapsuleController.at==12345,"last stopped activity starts one monotonic exit window");
  Activity rotating=new Activity();rotating.rotating=true;lifecycle.onActivityStarted(rotating);lifecycle.onActivityStopped(rotating);
  check(AtomicCapsuleController.exits==1,"configuration change does not renew capsule");
  Activity replacement=new Activity();lifecycle.onActivityStarted(replacement);lifecycle.onActivityStopped(replacement);
  check(AtomicCapsuleController.exits==2,"real exit after rotation still detected");
  lifecycle.onActivityStarted(first);lifecycle.onActivityStarted(second);lifecycle.onActivityStopped(second);
  check(AtomicCapsuleController.exits==2,"return within app does not renew capsule");
  lifecycle.onActivityStopped(first);check(AtomicCapsuleController.exits==3,"returning to external screen detected");
  System.out.println("Atomic capsule app lifecycle: PASS ("+checks+" production-method checks)");
 }
}
'''.replace('__STARTED__', lifecycle_method('onActivityStarted')).replace('__STOPPED__', lifecycle_method('onActivityStopped'))
out = root / 'build/atomic-capsule-30s/check/lifecycle'
out.mkdir(parents=True, exist_ok=True)
file = out / 'AtomicCapsuleLifecycleCheck.java'
file.write_text(lifecycle, encoding='utf-8')
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'AtomicCapsuleLifecycleCheck'], check=True)

manifest = (root / 'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8')
settings = read('NotificationSettingsActivity.java')
assert 'BIND_ACCESSIBILITY_SERVICE' not in manifest and 'android.accessibilityservice' not in manifest
assert 'android.intent.category.HOME' not in manifest
assert 'ACTION_ACCESSIBILITY_SETTINGS' not in settings and '胶囊计时' not in settings
for name in ('AtomicCapsuleAccessibilityService.java', 'AtomicCapsuleIdentity.java'):
    assert not (root / 'app/src/main/java/com/donglan/chrona' / name).exists()
assert not (root / 'app/src/main/res/xml/atomic_capsule_accessibility.xml').exists()
for path in (root / 'app/src/main/java').rglob('*.java'):
    text = path.read_text(encoding='utf-8')
    assert 'AtomicCapsuleAccessibilityService' not in text and 'AtomicCapsuleIdentity' not in text, path
assert 'PACKAGE_USAGE_STATS' not in manifest
assert all(term not in app for term in ('UsageStatsManager', 'CATEGORY_HOME', 'getRunningTasks'))
for title in ('Chrona 日程', '系统日程', '使用壁纸背景', '胶囊显示倒计时', '显示秒数', '原子通知权限'):
    assert title in settings
print('Privacy/settings removal: PASS (no production accessibility service, launcher query or usage access; other atomic controls retained)')
