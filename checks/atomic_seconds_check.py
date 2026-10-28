"""Compile production cards against Android 36; execute text countdown/reapply on host doubles.

OEM RemoteViews inflation, actual second ticks and typography still require device acceptance.
"""
import os
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
out = root / 'build/atomic-seconds-check'
out.mkdir(parents=True, exist_ok=True)
src = root / 'app/src/main/java/com/donglan/chrona'
card = (src / 'AtomicAgendaCard.java').read_text(encoding='utf-8')
updates = (src / 'AtomicCountdownUpdates.java').read_text(encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'

# Compile complete production files against the real SDK, not invented RemoteViews signatures.
sdk_line = re.search(r'^sdk.dir=(.+)$', (root / 'local.properties').read_text(), re.M).group(1)
sdk = Path(sdk_line.replace('\\:', ':').replace('\\\\', '\\'))
android_jar = sdk / 'platforms/android-36/android.jar'
assert android_jar.is_file(), android_jar
ids = sorted(set(re.findall(r'R\.id\.(\w+)', card)))
resources = []
for kind in ('id', 'layout', 'drawable', 'array'):
    names = sorted(set(re.findall(r'R\.' + kind + r'\.(\w+)', card)))
    resources.append('static class ' + kind + '{' + ''.join(
        f'static final int {name}={index + 1};' for index, name in enumerate(names)) + '}')
stubs = '''package com.donglan.chrona;
import android.content.Context;import android.app.PendingIntent;import android.graphics.Bitmap;import android.os.Bundle;
class R {__RESOURCES__}
class UiStyle {static class Palette {int text,muted,primary,surface;}static Palette colors(Context c){return new Palette();}}
class AgendaReminderPreferences {static boolean showSeconds(Context c){return true;}static boolean enabled(Context c){return true;}static boolean capsuleCountdown(Context c){return true;}static boolean atomicWallpaper(Context c){return true;}}
class GlassBackdropView {static Bitmap blurredForNotification(Context c,int w,int h){return null;}}
class AgendaReminder {static PendingIntent action(Context c,AgendaEntry e,String a,int i){return null;}static void requestRefresh(Context c){}}
class AtomicCapsuleController {static void countdown(Bundle data,String text){}static void apply(Context c,Bundle data){}}
'''.replace('__RESOURCES__', ''.join(resources))
stub_file = out / 'ProductionStubs.java'
stub_file.write_text(stubs, encoding='utf-8')
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                '-cp', str(android_jar), '-d', str(out / 'compiled'), str(stub_file),
                str(src / 'AtomicAgendaCard.java'), str(src / 'AtomicCountdownUpdates.java'),
                str(src / 'AgendaEntry.java'), str(src / 'ReminderTimeline.java'),
                str(src / 'data/EventCategory.java')], check=True)


def strip_java(source):
    return re.sub(r'^package .*;\s*|^import .*;\s*', '', source, flags=re.M)


start = card.index('    static void applyRemaining(')
end = card.index('    static void applyBackground(', start)
remaining_start = card.index('    static String remainingText(long millis)')
remaining_end = card.index('    static int cameraTopPadding(', remaining_start)
methods = (card[start:end] + card[remaining_start:remaining_end]).replace(
    'android.os.SystemClock', 'SystemClock').replace('android.util.TypedValue.COMPLEX_UNIT_SP', '1')
entry = strip_java((src / 'AgendaEntry.java').read_text(encoding='utf-8')).replace(
    'com.donglan.chrona.data.EventCategory', 'EventCategory')
category = strip_java((src / 'data/EventCategory.java').read_text(encoding='utf-8')).replace(
    'public final class EventCategory', 'final class EventCategory')
timeline = strip_java((src / 'ReminderTimeline.java').read_text(encoding='utf-8'))
tick = strip_java(updates).replace('System.currentTimeMillis()', 'TestClock.now').replace('android.util.Log', 'Log').replace('android.util.TypedValue.COMPLEX_UNIT_SP', '1')
harness = r'''
import java.util.*;
class R {static class id {static final int atomic_remaining=1,atomic_top_countdown=2;}}
class TestClock {static long now=1_000_000;}
class Host {Map<Integer,String> texts=new HashMap<>();Map<Integer,Float> sizes=new HashMap<>();String at(int id){return texts.get(id);}}
class RemoteViews {List<java.util.function.Consumer<Host>> actions=new ArrayList<>();
void setTextViewText(int id,String text){actions.add(h->h.texts.put(id,text));}
void setTextViewTextSize(int id,int unit,float size){actions.add(h->h.sizes.put(id,size));}
void apply(Host h){for(var a:actions)a.accept(h);}public RemoteViews clone(){RemoteViews copy=new RemoteViews();copy.actions.addAll(actions);return copy;}}
class Bundle extends HashMap<String,Object> {Bundle(){}Bundle(Bundle b){putAll(b);}Bundle getBundle(String k){return (Bundle)get(k);}Object getParcelable(String k){return get(k);}CharSequence getCharSequence(String k){return (CharSequence)get(k);}void putInt(String k,int v){put(k,v);}void putString(String k,String v){put(k,v);}void putCharSequence(String k,CharSequence v){put(k,v);}void putBoolean(String k,boolean v){put(k,v);}void putBundle(String k,Bundle v){put(k,v);}void putParcelable(String k,Object v){put(k,v);}}
class Notification {long when,timeout;boolean onlyAlert;Bundle extras=new Bundle();static class Builder {Notification n;Builder(Notification n){this.n=n;}static Builder recoverBuilder(Context c,Notification n){Notification copy=new Notification();copy.when=n.when;copy.extras.putAll(n.extras);return new Builder(copy);}Builder addExtras(Bundle b){n.extras.putAll(b);return this;}Builder setTimeoutAfter(long ms){n.timeout=ms;return this;}Builder setOnlyAlertOnce(boolean b){n.onlyAlert=b;return this;}Notification build(){return n;}}}
class StatusBarNotification {int id;Notification n;String tag="VIVO_SUPERX_TAG";StatusBarNotification(int id,Notification n){this.id=id;this.n=n;}int getId(){return id;}Notification getNotification(){return n;}String getTag(){return tag;}}
class NotificationManager {StatusBarNotification[] active=new StatusBarNotification[0];Map<Integer,Notification> posted=new HashMap<>();StatusBarNotification[] getActiveNotifications(){return active;}void notify(int id,Notification n){posted.put(id,n);}}
class Context {NotificationManager manager=new NotificationManager();<T>T getSystemService(Class<T> c){return c.cast(manager);}}
class AgendaReminderPreferences {static boolean seconds=true,enabled=true,capsule=true;static boolean enabled(Context c){return enabled;}static boolean showSeconds(Context c){return seconds;}static boolean capsuleCountdown(Context c){return capsule;}}
class AgendaReminder {static int refreshes;static void requestRefresh(Context c){refreshes++;}}
class AtomicCapsuleController {static void countdown(Bundle b,String text){}static void apply(Context c,Bundle b){}}
class Log {static void w(String t,String m,RuntimeException e){throw e;}}
class AtomicAgendaCard {__METHODS__}
__CATEGORY__
__ENTRY__
__TIMELINE__
__TICK__
public class AtomicSecondsCheck {
static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
static AgendaEntry entry(String category,long start,long end,Long due,Long overdue){return new AgendaEntry(1,1,start,end,"title","location","","Chrona",false,false,true,null,category,null,due,null,overdue);}
static RemoteViews card(AgendaEntry e,long now,boolean seconds){RemoteViews v=new RemoteViews();boolean active=now>=e.start&&"event".equals(e.category);AtomicAgendaCard.applyRemaining(v,e,now,active?e.finish():e.start,active,seconds);return v;}
static void state(Host host,AgendaEntry e,long now,boolean seconds,String text){card(e,now,seconds).apply(host);for(int id:new int[]{1,2})check(host.at(id).equals(text),text);}
static void rendered(RemoteViews view,Host host,String text){view.apply(host);for(int id:new int[]{1,2}){check(host.at(id).equals(text),"both expanded labels "+text);check(host.sizes.get(id)==14,"existing long-text size");}}
public static void main(String[] args){
long now=TestClock.now;
AgendaEntry upcoming=entry("event",now+3_661_000,now+7_200_000,null,null);
Host host=new Host();RemoteViews baseline=card(upcoming,now,true);baseline.apply(host);
rendered(baseline,host,"1:01:01");
long[] values={3_600_001,3_600_000,3_599_001,3_599_000,121_000,120_001,120_000,60_000,59_001,59_000,1_000,1,0,-1,-1000,Long.MIN_VALUE};
String[] labels={"1:00:01","1:00:00","1:00:00","59:59","02:01","02:01","02:00","01:00","01:00","00:59","00:01","00:01","00:00","00:00","00:00","00:00"};
for(int i=0;i<values.length;i++)check(AtomicAgendaCard.expandedRemainingText(values[i]).equals(labels[i]),"rounded nonnegative format "+values[i]);
check(!AtomicAgendaCard.expandedRemainingText(Long.MAX_VALUE).contains("-"),"positive overflow safe");
state(host,upcoming,now,false,"62分");state(host,upcoming,now,true,"1:01:01");
state(host,entry("task",now-1,now+3600,null,null),now,true,"待完成");
state(host,entry("homework",now-1,now+3600,now-1,now-1),now,true,"已逾期");
state(host,entry("homework",now+3600,now+7200,now-1,now-1),now,true,"已逾期");
state(host,entry("event",now-3600,now,null,null),now,true,"已结束");
state(host,entry("other",now,now+3600,null,null),now,true,"已开始");
AgendaEntry active=entry("event",now-1000,now+600_000,null,null);state(host,active,now,true,"10:00");
AgendaEntry deadline=entry("task",now+600_000,now+900_000,now+900_000,now+900_000);state(host,deadline,now,true,"10:00");
// Every application tick clones the fixed baseline, never a previously updated template.
Context context=new Context();RemoteViews islandCard=card(active,now,true);int baselineActions=baseline.actions.size(),islandActions=islandCard.actions.size();
Notification notice=new Notification();notice.when=upcoming.start;notice.extras.put("notification.superx.customTemplate",baseline);
Bundle base=new Bundle();base.putCharSequence("notification.superx.baseInfos.title","title");base.putCharSequence("notification.superx.baseInfos.content","old\nrange");notice.extras.putBundle("notification.superx.baseInfos",base);
Bundle shortInfo=new Bundle();shortInfo.putString("notification.superx.shortInfos.coreInfoShort","oldShort");notice.extras.putBundle("notification.superx.shortInfos",shortInfo);
Bundle island=new Bundle(),left=new Bundle();left.putCharSequence("island.superx.leftInfo.content","old");island.putBundle("island.superx.leftInfo",left);island.put("island.superx.customTemplate",islandCard);notice.extras.putBundle("notification.superx.island",island);
Bundle capsule=new Bundle();capsule.putString("notification.superx.capsule.content","oldCapsule");notice.extras.putBundle("notification.superx.capsule",capsule);
context.manager.active=new StatusBarNotification[]{new StatusBarNotification(99,notice)};
AtomicCountdownUpdates.remember(99,notice,upcoming,now);check(AtomicCountdownUpdates.nextDelay(context)==1000,"whole countdown window second frequency");
for(long advance:new long[]{0,1000,61_000,62_000,3_540_000}){TestClock.now=now+advance;AtomicCountdownUpdates.tick(context);Notification posted=context.manager.posted.get(99);check(posted!=null,">two minute seconds post");String expected=AtomicAgendaCard.expandedRemainingText(upcoming.start-TestClock.now);RemoteViews cardCopy=(RemoteViews)posted.extras.get("notification.superx.customTemplate"),islandCopy=(RemoteViews)posted.extras.getBundle("notification.superx.island").get("island.superx.customTemplate");rendered(cardCopy,host,expected);rendered(islandCopy,host,expected);check(cardCopy!=baseline&&islandCopy!=islandCard,"both templates cloned");check(cardCopy.actions.size()==baselineActions+4&&islandCopy.actions.size()==islandActions+4,"bounded clone actions");check(posted.extras.getBundle("notification.superx.baseInfos")==base&&posted.extras.getBundle("notification.superx.shortInfos")==shortInfo,"far compact base untouched");check(posted.extras.getBundle("notification.superx.island").get("island.superx.leftInfo")==left&&posted.extras.getBundle("notification.superx.capsule")==capsule,"far capsule fields untouched");check(posted.onlyAlert&&posted.timeout==upcoming.finish()-TestClock.now,"original expiry and only-alert-once");check(posted.extras.get("notification.superx.operation").equals(1)&&posted.extras.getBundle("notification.superx.island").get("island.superx.forceShowCard").equals(false),"update never reopens card");}
TestClock.now=upcoming.start-120_001;AtomicCountdownUpdates.tick(context);Notification beforeCompact=context.manager.posted.get(99);check(beforeCompact.extras.getBundle("notification.superx.shortInfos")==shortInfo,"120001ms preserves compact contract");rendered((RemoteViews)beforeCompact.extras.get("notification.superx.customTemplate"),host,"02:01");
TestClock.now=upcoming.start-120_000;AtomicCountdownUpdates.tick(context);Notification atCompact=context.manager.posted.get(99);check(atCompact.extras.getBundle("notification.superx.shortInfos").get("notification.superx.shortInfos.coreInfoShort").equals("120秒"),"120000ms enters compact seconds");rendered((RemoteViews)atCompact.extras.get("notification.superx.customTemplate"),host,"02:00");
for(int frame=0;frame<5;frame++){TestClock.now=upcoming.start-119_000+frame*1000;AtomicCountdownUpdates.tick(context);Notification posted=context.manager.posted.get(99);rendered((RemoteViews)posted.extras.get("notification.superx.customTemplate"),host,String.format(java.util.Locale.ROOT,"01:%02d",59-frame));check(baseline.actions.size()==baselineActions&&islandCard.actions.size()==islandActions,"fixed baselines unchanged");check(posted.extras.getBundle("notification.superx.shortInfos").get("notification.superx.shortInfos.coreInfoShort").equals((119-frame)+"秒"),"last two-minute protocol still ticks");}
TestClock.now=upcoming.start-1;AtomicCountdownUpdates.tick(context);rendered((RemoteViews)context.manager.posted.get(99).extras.get("notification.superx.customTemplate"),host,"00:01");
TestClock.now=upcoming.start;context.manager.posted.clear();AtomicCountdownUpdates.tick(context);check(AgendaReminder.refreshes==1&&context.manager.posted.isEmpty(),"deadline requests boundary refresh");check(host.at(1).equals("00:01")&&host.at(2).equals("00:01"),"delayed boundary keeps last nonnegative text");check(AtomicCountdownUpdates.nextDelay(context)==60_000,"no future deadline idle frequency");
TestClock.now=now;AgendaReminderPreferences.seconds=false;AtomicCountdownUpdates.tick(context);check(context.manager.posted.isEmpty()&&AtomicCountdownUpdates.nextDelay(context)==60_000,"seconds disabled");AgendaReminderPreferences.seconds=true;
AgendaReminderPreferences.enabled=false;AtomicCountdownUpdates.tick(context);check(context.manager.posted.isEmpty(),"master disabled");AgendaReminderPreferences.enabled=true;
Notification stale=new Notification();stale.when=notice.when+1;context.manager.active=new StatusBarNotification[]{new StatusBarNotification(99,stale)};AtomicCountdownUpdates.tick(context);check(context.manager.posted.isEmpty(),"stale identity not updated");
stale.when=notice.when;StatusBarNotification nonAtomic=new StatusBarNotification(99,stale);nonAtomic.tag=null;context.manager.active=new StatusBarNotification[]{nonAtomic};AtomicCountdownUpdates.tick(context);check(context.manager.posted.isEmpty(),"non-atomic identity not updated");
context.manager.active=new StatusBarNotification[0];AtomicCountdownUpdates.tick(context);check(context.manager.posted.isEmpty(),"missing notification not recreated");
context.manager.active=new StatusBarNotification[]{new StatusBarNotification(99,notice)};AtomicCountdownUpdates.clear(99);check(AtomicCountdownUpdates.nextDelay(context)==60_000,"cleared snapshots idle");
AtomicCountdownUpdates.remember(99,notice,entry("task",now-1,now+999999,null,null),now);AtomicCountdownUpdates.tick(context);check(context.manager.posted.isEmpty()&&AtomicCountdownUpdates.nextDelay(context)==60_000,"started task stays static");
AtomicCountdownUpdates.remember(99,notice,entry("homework",now+999999,now+1999999,now-1,now-1),now);AtomicCountdownUpdates.tick(context);check(context.manager.posted.isEmpty()&&AtomicCountdownUpdates.nextDelay(context)==60_000,"overdue future occurrence stays static");
System.out.println("Atomic seconds production-method contracts passed: "+checks+" checks; device rendering is separate");
}}
'''
for key, value in {'METHODS': methods, 'CATEGORY': category, 'ENTRY': entry,
                   'TIMELINE': timeline, 'TICK': tick}.items():
    harness = harness.replace('__' + key + '__', value)
test_file = out / 'AtomicSecondsCheck.java'
test_file.write_text(harness, encoding='utf-8')
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8',
                '-d', str(out / 'host'), str(test_file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out / 'host'), 'AtomicSecondsCheck'], check=True)

layout = ET.parse(root / 'app/src/main/res/layout/notification_atomic_card.xml')
a = '{http://schemas.android.com/apk/res/android}'
nodes = {node.get(a + 'id'): node for node in layout.iter()}
for name in ('atomic_remaining', 'atomic_top_countdown'):
    node = nodes['@+id/' + name]
    assert node.tag == 'TextView'
    assert node.get(a + 'layout_width') == 'wrap_content'
    assert node.get(a + 'textSize') == '17sp'
    assert node.get(a + 'autoSizeTextType') is None and node.get(a + 'ellipsize') is None
for name in ('atomic_top_remaining', 'atomic_bottom_remaining'):
    assert nodes['@+id/' + name].get(a + 'layout_width') == 'wrap_content'
    assert nodes['@+id/' + name].get(a + 'minWidth') == '56dp'
assert 'AtomicAgendaCard.remainingText(remaining, true)' in updates
assert 'Chronometer' not in card and not list(layout.iter('Chronometer'))
assert 'baseline.clone()' in updates
assert 'long to = active ? entry.finish() : entry.start;' in card
assert 'now < to&&!entry.overdue(now)' in card
print('Full AtomicAgendaCard/AtomicCountdownUpdates Android 36 javac compilation and adaptive XML contracts passed')
