"""Execute production capsule timing and payload projection; OEM rendering needs the vivo phone."""
import os
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/AtomicCapsuleController.java').read_text(encoding='utf-8')
source = re.sub(r'^package .*;\s*|^import .*;\s*', '', source, flags=re.M)
source = source.replace('public final class AtomicCapsuleController', 'final class AtomicCapsuleController')
source = source.replace('android.os.Build.VERSION.SDK_INT', '36').replace('android.util.Log', 'Log')
harness = r'''
import java.util.*;
import java.text.BreakIterator;
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
  PendingIntent click=(PendingIntent)awake.getBundle(P+"island").get("island.superx.clickResp");check(click.flags==3&&click.intent.data.text.contains("occurrence-A"),"immutable click with occurrence identity");check(awake.getBundle(P+"island").getInt("island.superx.islandClick")==1,"tap reaches app for timing");
  c.notifications.active.add(10);SystemClock.now=40000;Bundle[]posted={null};AgendaReminder.onRefresh=()->posted[0]=decorate(c,10,"occurrence-A");check(click.intent.extras.isEmpty(),"timing does not depend on OEM preserving extras");new AtomicCapsuleController().onReceive(c,click.intent);
  check(c.prefs.getLong("until:10",0)==100000,"click gives exactly one minute");check(Boolean.TRUE.equals(posted[0].getBundle(P+"island").get("island.superx.forceShowCard")),"tap asks OEM to show complete card");
  Bundle later=decorate(c,10,"occurrence-A");check(!Boolean.TRUE.equals(later.getBundle(P+"island").get("island.superx.forceShowCard")),"later refresh does not reopen card");
  SystemClock.now=90000;AtomicCapsuleController.initialize(c);check(c.prefs.getLong("until:10",0)==100000&&c.prefs.getLong("wake_until",0)==62000,"recovery cannot extend pending click deadline");
  SystemClock.now=99999;AtomicCapsuleController.apply(c,later);check(right(later).endsWith("长标题"),"click window remains full");SystemClock.now=100000;AtomicCapsuleController.apply(c,later);check(right(later).equals("数字"),"click window expires at 60 seconds");
  int refreshes=AgendaReminder.refreshes;decorate(c,10,"replacement");new AtomicCapsuleController().onReceive(c,click.intent);check(AgendaReminder.refreshes==refreshes,"old tap cannot reopen replacement");
  PendingIntent replacement=(PendingIntent)decorate(c,10,"replacement").getBundle(P+"island").get("island.superx.clickResp");c.notifications.active.clear();new AtomicCapsuleController().onReceive(c,replacement.intent);check(AgendaReminder.refreshes==refreshes,"dismissed notification cannot be resurrected");
  AtomicCapsuleController.clear(c,10);check(!c.prefs.contains("identity:10")&&c.alarms.at==0,"clear cancels final presentation alarm");
  SystemClock.now=110000;decorate(c,11,"slot-B");long persisted=c.prefs.getLong("until:11",0);AtomicCapsuleController.initialize(c);check(c.prefs.getLong("until:11",0)==persisted,"process initialization preserves presentation deadline");
  c.power.awake=false;AtomicCapsuleController.screenOff(c);check(c.alarms.at==0&&Handler.delayed==null,"screen off stops presentation wakeups");c.power.awake=true;SystemClock.now=150000;AtomicCapsuleController.screenOn(c,SystemClock.now);decorate(c,11,"slot-B");check(c.alarms.at==180000,"next actual wake resets 30 seconds");
  c.alarms.exact=false;decorate(c,11,"slot-B");check(c.alarms.at==180000&&Handler.delay==30000,"no exact permission retains handler and durable inexact alarm");c.alarms.exact=true;c.alarms.deny=true;decorate(c,11,"slot-B");check(c.alarms.at==180000,"permission revocation does not crash notification");
  c.alarms.deny=false;SystemClock.now=200000;int forces=HomeworkNotice.forced;AtomicCapsuleController.initialize(c);check(c.prefs.getLong("wake_until",0)==230000,"interactive recovery after missed wake gets one reading window");check(HomeworkNotice.forced==forces+1,"recovered homework is republished during full window, not only at its end");
  Context queuedContext=new Context();SystemClock.now=1000;Bundle queuedNotice=decorate(queuedContext,22,"queued-click");queuedContext.notifications.active.add(22);PendingIntent queuedClick=(PendingIntent)queuedNotice.getBundle(P+"island").get("island.superx.clickResp");Runnable[]queued={null};AgendaReminder.WORKER=task->queued[0]=task;AgendaReminder.onRefresh=()->{};SystemClock.now=10000;new AtomicCapsuleController().onReceive(queuedContext,queuedClick.intent);SystemClock.now=45000;queued[0].run();check(queuedContext.prefs.getLong("until:22",0)==70000,"queued click uses actual receive time, not worker completion");AgendaReminder.WORKER=Runnable::run;AtomicCapsuleController.clear(queuedContext,22);
  Settings.Global.boot++;SystemClock.now=5;AtomicCapsuleController.initialize(c);check(!c.prefs.contains("identity:11")&&c.prefs.getLong("wake_until",0)==30005,"reboot invalidates old monotonic timestamps");
  check(AtomicCapsuleController.shortTitle("").isEmpty()&&AtomicCapsuleController.shortTitle("课").equals("课")&&AtomicCapsuleController.shortTitle("课程").equals("课程"),"zero to two characters unchanged");check(AtomicCapsuleController.shortTitle("😀课表").equals("😀课"),"surrogate pair not split");check(AtomicCapsuleController.shortTitle("a\u0301bc").equals("a\u0301b"),"combining mark remains attached");
  System.out.println("Atomic capsule: PASS ("+checks+" production-controller checks; OEM display pending)");
 }
}
'''.replace('__SOURCE__', source)
out = root / 'build/atomic-capsule-30s/check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'AtomicCapsuleCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'AtomicCapsuleCheck'], check=True)

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
