"""Execute production vivo payload/lifecycle with notification transport doubles."""
import os, re, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/donglan/chrona/VivoAtomicNotification.java').read_text(encoding='utf-8')
source=re.sub(r'^package .*;\s*|^import .*;\s*','',source,flags=re.M).replace('android.service.notification.StatusBarNotification','StatusBarNotification').replace('android.util.Log','Log')
source=source.replace('android.graphics.Bitmap', 'Bitmap').replace('android.widget.RemoteViews', 'RemoteViews')
source=source.replace('android.os.SystemClock', 'SystemClock')
delivery=(root/'app/src/main/java/com/donglan/chrona/NotificationDeliveryStatus.java').read_text(encoding='utf-8')
source += '\n' + re.sub(r'^package .*;\s*|^import .*;\s*','',delivery,flags=re.M).replace('android.Manifest.','Manifest.')
card_source=(root/'app/src/main/java/com/donglan/chrona/AtomicAgendaCard.java').read_text(encoding='utf-8')
a=card_source.index('    static String remainingText(long millis)');b=card_source.index('    static int cameraTopPadding',a)
source += '\nclass ReminderTimeline {static long CAPSULE_COUNTDOWN_WINDOW=120000;static long capsuleDeadline(AgendaEntry e,long now){return e.deadline;}static long expiresAt(AgendaEntry e){return e.deadline;}}\nclass AtomicAgendaCard {' + card_source[a:b] + '}\n'
updates=(root/'app/src/main/java/com/donglan/chrona/AtomicCountdownUpdates.java').read_text(encoding='utf-8')
updates=re.sub(r'^package .*;\s*|^import .*;\s*','',updates,flags=re.M).replace('android.os.PowerManager','PowerManager').replace('android.util.Log','Log').replace('android.util.TypedValue.COMPLEX_UNIT_SP','1')
source += updates.replace('System.currentTimeMillis()', 'TestClock.now')
harness=r'''
import java.util.*;
class Bundle extends HashMap<String,Object> {Bundle(){}Bundle(Bundle b){putAll(b);}Bundle getBundle(String k){return (Bundle)get(k);}Object getParcelable(String k){return get(k);}void putInt(String k,int v){put(k,v);}void putString(String k,String v){put(k,v);}void putCharSequence(String k,CharSequence v){put(k,v);}void putBoolean(String k,boolean v){put(k,v);}void putParcelable(String k,Object v){put(k,v);}void putBundle(String k,Bundle v){put(k,v);}void putParcelableArrayList(String k,ArrayList<Icon> v){put(k,v);}}
class Build {static String MANUFACTURER;static class VERSION {static int SDK_INT=36;}}
class PackageManager {static final int PERMISSION_GRANTED=0;}
class Manifest {static class permission {static final String POST_NOTIFICATIONS="notification";}}
class Settings {static final String ACTION_APP_NOTIFICATION_SETTINGS="app",ACTION_CHANNEL_NOTIFICATION_SETTINGS="channel",EXTRA_APP_PACKAGE="package",EXTRA_CHANNEL_ID="channel_id";}
class Intent {String action;Map<String,String> extras=new HashMap<>();Intent(String action){this.action=action;}Intent putExtra(String key,String value){extras.put(key,value);return this;}}
class NotificationChannel {int importance;NotificationChannel(int importance){this.importance=importance;}int getImportance(){return importance;}}
class SystemClock {static long elapsed=300000;static long elapsedRealtime(){return elapsed;}}
class TestClock {static long now=System.currentTimeMillis();}
class PowerManager {boolean interactive=true;boolean isInteractive(){return interactive;}}
class Context {PowerManager power=new PowerManager();NotificationManager manager=new NotificationManager();int permission=PackageManager.PERMISSION_GRANTED;int checkSelfPermission(String p){return permission;}String getPackageName(){return "com.donglan.chrona";}<T>T getSystemService(Class<T> c){return c.cast(c==PowerManager.class?power:manager);}}
class PendingIntent {}
class RemoteViews {int actions;Map<Integer,String> texts=new HashMap<>();public RemoteViews clone(){RemoteViews v=new RemoteViews();v.actions=actions;v.texts.putAll(texts);return v;}void setTextViewText(int id,String text){actions++;texts.put(id,text);}void setTextViewTextSize(int id,int unit,float size){actions++;}}
class AgendaEntry {long deadline;AgendaEntry(long deadline){this.deadline=deadline;}}
class AgendaReminderPreferences {static boolean seconds=true,capsule=true,enabled=true;static boolean enabled(Context c){return enabled;}static boolean showSeconds(Context c){return seconds;}static boolean capsuleCountdown(Context c){return capsule;}}
class AgendaReminder {static final String CHANNEL="upcoming_agenda";static int refreshes;static void requestRefresh(Context c){refreshes++;}}
class Bitmap {static class Config {static Object ARGB_8888=new Object();}static Bitmap createBitmap(int w,int h,Object config){if(w!=1||h!=1)throw new AssertionError();return new Bitmap();}}
class Icon {int resource;Bitmap bitmap;static Icon createWithResource(Context c,int r){Icon icon=new Icon();icon.resource=r;return icon;}static Icon createWithBitmap(Bitmap bitmap){Icon icon=new Icon();icon.bitmap=bitmap;return icon;}}
class R {static class id {static int atomic_remaining=10,atomic_top_countdown=11;}static class drawable {static int ic_chrona_foreground=2,ic_calendar_source=3,ic_check=4;}}
class UiStyle {static class Palette {int primary=0xff8855ee;}static Palette colors(Context c){return new Palette();}}
class Log {static void w(String a,String b,Exception c){}static void i(String a,String b){}}
class Notification {long when,timeout;Bundle extras=new Bundle();static class Builder {Notification n;Builder(Notification n){this.n=n;}static Builder recoverBuilder(Context c,Notification n){Notification copy=new Notification();copy.when=n.when;copy.extras.putAll(n.extras);return new Builder(copy);}Builder addExtras(Bundle b){n.extras.putAll(b);return this;}Builder setTimeoutAfter(long millis){n.timeout=millis;return this;}Builder setOnlyAlertOnce(boolean b){return this;}Notification build(){return n;}}}
class StatusBarNotification {int id;Notification n;StatusBarNotification(int id,Notification n){this.id=id;this.n=n;}int getId(){return id;}Notification getNotification(){return n;}}
class NotificationManager {static final int IMPORTANCE_NONE=0;NotificationChannel channel;StatusBarNotification[] active=new StatusBarNotification[0];List<Integer> operations=new ArrayList<>();Map<Integer,Notification> posted=new HashMap<>();int cancels;boolean fail,sceneFailure,allowed=true;Boolean scene=true;boolean areNotificationsEnabled(){return allowed;}NotificationChannel getNotificationChannel(String id){if(!id.equals(AgendaReminder.CHANNEL))throw new AssertionError(id);return channel;}public Boolean getSceneStatus(String pkg,String name){if(sceneFailure)throw new IllegalStateException("unavailable API");if(!pkg.equals("com.donglan.chrona")||!name.equals("CHRONA_AGENDA"))throw new AssertionError();return scene;}StatusBarNotification[] getActiveNotifications(){return active;}void notify(int id,Notification n){if(fail)throw new RuntimeException();operations.add((Integer)n.extras.get("notification.superx.operation"));posted.put(id,n);}void cancel(int id){cancels++;}}
__SOURCE__
public class VivoAtomicCheck {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("vivo check "+checks);}
 public static void main(String[] args){
 Context c=new Context();PendingIntent details=new PendingIntent();RemoteViews card=new RemoteViews(),islandCard=new RemoteViews();String animations="{marquee:true}";
 for(String brand:new String[]{"google","samsung","OPPO",null}){Build.MANUFACTURER=brand;check(!VivoAtomicNotification.available(c));check(VivoAtomicNotification.sceneEnabled(c)==null);check(VivoAtomicNotification.extras(c,"课","开始","13:25–15:50","13:25","距开始 15分钟",details,false,card,islandCard,animations).isEmpty());}
 Build.MANUFACTURER="VIVO";
 check(VivoAtomicNotification.sceneEnabled(c).equals(true));check(VivoAtomicNotification.available(c));c.manager.scene=false;check(VivoAtomicNotification.sceneEnabled(c).equals(false));check(!VivoAtomicNotification.available(c));
 c.manager.scene=null;check(VivoAtomicNotification.sceneEnabled(c)==null&&!VivoAtomicNotification.available(c));
 c.manager.scene=true;c.manager.sceneFailure=true;check(VivoAtomicNotification.sceneEnabled(c)==null&&!VivoAtomicNotification.available(c));c.manager.sceneFailure=false;
 c.manager.allowed=false;check(!VivoAtomicNotification.available(c));c.manager.allowed=true;check(VivoAtomicNotification.available(c));
 c.permission=-1;check(!VivoAtomicNotification.available(c));c.permission=PackageManager.PERMISSION_GRANTED;
 c.manager.channel=new NotificationChannel(NotificationManager.IMPORTANCE_NONE);check(!VivoAtomicNotification.available(c));
 c.manager.channel=new NotificationChannel(4);check(VivoAtomicNotification.available(c));
 c.manager.scene=false;check(!VivoAtomicNotification.available(c));c.manager.scene=true;
 Bundle b=VivoAtomicNotification.extras(c,"微积分","即将开始","10:00–12:25","10:00","距开始 15分钟",details,false,card,islandCard,animations);
 check(b.get("notification.superx.operation").equals(0));check(b.get("notification.superx.template").equals(7));check(b.get("notification.superx.scene").equals("CHRONA_AGENDA"));check(b.get("notification.superx.dismissWhenKill").equals(false));check(b.get("notification.superx.showNotify").equals(true));check(b.get("notification.superx.clickResp")==details);
 Bundle base=(Bundle)b.get("notification.superx.baseInfos"), island=(Bundle)b.get("notification.superx.island");
 check(base.get("notification.superx.baseInfos.title").equals("微积分"));
 check(b.get("notification.superx.customTemplate")==card);check(b.get("notification.superx.disableInvertColor").equals(true));check(b.get("notification.superx.animationsConfig").equals(animations));
 check(!b.containsKey("notification.superx.infos"));
 check(b.get("notification.superx.displays").equals(0x111));
 check(island.get("island.superx.leftTemplate").equals(1)&&island.get("island.superx.rightTemplate").equals(4));
 check(((Bundle)island.get("island.superx.leftInfo")).get("island.superx.leftInfo.content").equals("10:00"));check(((Bundle)island.get("island.superx.rightInfo")).get("island.superx.rightInfo.content").equals("微积分"));check(island.get("island.superx.clickResp")==details);
 check(island.get("island.superx.islandClick").equals(0));check(island.get("island.superx.template").equals(7));check(island.get("island.superx.customTemplate")==islandCard&&islandCard!=card);
 check(!((Bundle)island.get("island.superx.rightInfo")).containsKey("island.superx.rightInfo.clickResp"));
 check(base.get("notification.superx.baseInfos.content").equals("距开始 15分钟\n10:00–12:25"));
 check(VivoAtomicNotification.extras(c,"课","进行中","range","10:00","距结束 5分钟",details,true,card,islandCard,animations).get("notification.superx.operation").equals(1));
 Bundle noLocation=VivoAtomicNotification.extras(c,"课","进行中","range","10:00","距结束 5分钟",details,true,card,islandCard,animations);
 check(((Bundle)noLocation.get("notification.superx.baseInfos")).get("notification.superx.baseInfos.content").equals("距结束 5分钟\nrange"));
 Bundle capsule=(Bundle)b.get("notification.superx.capsule");check(capsule.get("notification.superx.capsule.state").equals(1));check(capsule.get("notification.superx.capsule.icon") instanceof Icon);check(capsule.get("notification.superx.capsule.content").equals("10:00 微积分"));check(capsule.get("notification.superx.capsule.clickResp")==details);
 Bundle shortInfo=(Bundle)b.get("notification.superx.shortInfos");check(((Icon)shortInfo.get("notification.superx.shortInfos.image")).resource==R.drawable.ic_chrona_foreground);check(shortInfo.get("notification.superx.shortInfos.describeShort").equals("即将开始"));check(shortInfo.get("notification.superx.shortInfos.coreInfoShort").equals("距开始 15分钟"));
 Notification n=new Notification();n.extras=b;NotificationManager manager=new NotificationManager();manager.active=new StatusBarNotification[]{new StatusBarNotification(7,n)};
 check(VivoAtomicNotification.active(manager,7));check(!VivoAtomicNotification.active(manager,8));
 Notification normal=new Notification();manager.active=new StatusBarNotification[]{new StatusBarNotification(7,normal)};check(!VivoAtomicNotification.active(manager,7));VivoAtomicNotification.end(c,manager,7);check(manager.operations.isEmpty()&&manager.cancels==1);manager.cancels=0;
 manager.active=new StatusBarNotification[]{new StatusBarNotification(7,n)};VivoAtomicNotification.end(c,manager,7);check(manager.operations.equals(List.of(2))&&manager.cancels==1);check(b.get("notification.superx.operation").equals(0));
 manager.fail=true;VivoAtomicNotification.end(c,manager,7);check(manager.cancels==2);
 Build.MANUFACTURER="google";manager.fail=false;VivoAtomicNotification.end(c,manager,7);check(manager.operations.size()==1&&manager.cancels==3);check(!VivoAtomicNotification.active(manager,7));
 Build.MANUFACTURER="vivo";Context tickingContext=new Context();long deadline=System.currentTimeMillis()+119000;
 Notification baseline=new Notification();baseline.when=deadline;baseline.extras=VivoAtomicNotification.extras(tickingContext,"长标题","进行中","range","10:00","remaining",details,true,card,islandCard,animations,"21:15");
 tickingContext.manager.active=new StatusBarNotification[]{new StatusBarNotification(99,baseline)};
 AtomicCountdownUpdates.remember(99,baseline,new AgendaEntry(deadline),System.currentTimeMillis());
 TestClock.now=deadline-119000;
 for(int frame=0;frame<5;frame++){
  TestClock.now=deadline-119000+frame*1000;AtomicCountdownUpdates.tick(tickingContext);
  Notification frameNotice=tickingContext.manager.posted.get(99);
  check(((RemoteViews)frameNotice.extras.get("notification.superx.customTemplate")).texts.get(R.id.atomic_remaining).equals((119-frame)+"秒"));
  check(((Bundle)frameNotice.extras.get("notification.superx.capsule")).get("notification.superx.capsule.content").toString().startsWith((119-frame)+"秒 "));
  check(((Bundle)frameNotice.extras.get("notification.superx.baseInfos")).get("notification.superx.baseInfos.content").equals((119-frame)+"秒\nrange"));
  check(((Bundle)frameNotice.extras.get("notification.superx.shortInfos")).get("notification.superx.shortInfos.coreInfoShort").equals((119-frame)+"秒"));
 }
 Notification updated=tickingContext.manager.posted.get(99);RemoteViews rendered=(RemoteViews)updated.extras.get("notification.superx.customTemplate");
 check(rendered.actions==4&&card.actions==0&&islandCard.actions==0);
 check(updated.timeout>0&&updated.timeout<=119000);
 Notification strippedCountdown=new Notification();strippedCountdown.when=baseline.when;
 StatusBarNotification tagged=new StatusBarNotification(99,strippedCountdown);tagged.tag="VIVO_SUPERX_TAG";
 tickingContext.manager.active=new StatusBarNotification[]{tagged};tickingContext.manager.posted.clear();
 AtomicCountdownUpdates.tick(tickingContext);check(tickingContext.manager.posted.containsKey(99));
 tagged.tag=null;tickingContext.manager.posted.clear();AtomicCountdownUpdates.tick(tickingContext);check(tickingContext.manager.posted.isEmpty());
 tagged.tag="VIVO_SUPERX_TAG";strippedCountdown.when++;AtomicCountdownUpdates.tick(tickingContext);check(tickingContext.manager.posted.isEmpty());
 tickingContext.manager.active=new StatusBarNotification[]{new StatusBarNotification(99,baseline)};
 check(AtomicCountdownUpdates.nextDelay(tickingContext)==1000);
 check(rendered.texts.get(R.id.atomic_remaining).endsWith("秒"));
 Bundle updatedIsland=(Bundle)updated.extras.get("notification.superx.island");
 check(((Bundle)updatedIsland.get("island.superx.leftInfo")).get("island.superx.leftInfo.content").toString().endsWith("秒"));
 check(((Bundle)baseline.extras.get("notification.superx.island")).get("island.superx.leftInfo")!=updatedIsland.get("island.superx.leftInfo"));
 tickingContext.manager.posted.clear();AgendaReminderPreferences.capsule=false;AtomicCountdownUpdates.tick(tickingContext);
 check(((Bundle)((Bundle)tickingContext.manager.posted.get(99).extras.get("notification.superx.island")).get("island.superx.leftInfo")).get("island.superx.leftInfo.content").equals("21:15"));
 AgendaReminderPreferences.capsule=true;tickingContext.manager.posted.clear();AgendaReminderPreferences.seconds=false;AtomicCountdownUpdates.tick(tickingContext);check(tickingContext.manager.posted.isEmpty());AgendaReminderPreferences.seconds=true;
 tickingContext.power.interactive=false;AtomicCountdownUpdates.tick(tickingContext);check(!tickingContext.manager.posted.isEmpty());check(AtomicCountdownUpdates.nextDelay(tickingContext)==1000);tickingContext.power.interactive=true;tickingContext.manager.posted.clear();
 tickingContext.manager.active=new StatusBarNotification[0];AtomicCountdownUpdates.tick(tickingContext);check(tickingContext.manager.posted.isEmpty());
 tickingContext.manager.active=new StatusBarNotification[]{new StatusBarNotification(99,baseline)};AtomicCountdownUpdates.clear(99);AtomicCountdownUpdates.tick(tickingContext);check(tickingContext.manager.posted.isEmpty());
 AtomicCountdownUpdates.remember(99,baseline,new AgendaEntry(System.currentTimeMillis()-1),System.currentTimeMillis());AtomicCountdownUpdates.tick(tickingContext);check(AgendaReminder.refreshes==1&&tickingContext.manager.posted.isEmpty());AtomicCountdownUpdates.clear(99);
 AtomicCountdownUpdates.remember(99,baseline,new AgendaEntry(0),System.currentTimeMillis(),System.currentTimeMillis()+180000);
 AtomicCountdownUpdates.tick(tickingContext);check(AgendaReminder.refreshes==1&&tickingContext.manager.posted.isEmpty());check(AtomicCountdownUpdates.nextDelay(tickingContext)==60000);AtomicCountdownUpdates.clear(99);
 System.out.println("Vivo atomic payload/lifecycle passed: "+checks+" checks (production adapter, notification doubles)");
 }
}
'''.replace('__SOURCE__',source)
harness=harness.replace(',islandCard,animations)', ',islandCard,animations,"10:00")')
harness=harness.replace(' System.out.println("Vivo atomic payload/lifecycle passed:', r'''
 Build.MANUFACTURER="vivo";
 String timer=VivoAtomicNotification.countdownText(120001);
 check(timer.contains("\"base\":420001")&&timer.contains("%02d:%02d"));
 check(VivoAtomicNotification.countdownText(120000).equals("120秒"));
 check(VivoAtomicNotification.countdownText(120000,false).equals("2分"));
 check(VivoAtomicNotification.countdownText(-1).equals(VivoAtomicNotification.countdownText(0)));
 SystemClock.elapsed=450000;check(VivoAtomicNotification.countdownText(1000).equals("1秒"));
 Bundle ticking=VivoAtomicNotification.extras(c,"微积分","即将开始","10:00–12:25","10:00","即将开始",details,true,card,islandCard,animations,timer);
 Bundle tickingIsland=(Bundle)ticking.get("notification.superx.island");
 check(((Bundle)tickingIsland.get("island.superx.leftInfo")).get("island.superx.leftInfo.content").equals(timer));
 check(((Bundle)ticking.get("notification.superx.capsule")).get("notification.superx.capsule.content").equals("10:00 微积分"));
 System.out.println("Vivo atomic payload/lifecycle passed:''')
harness=harness.replace('Bundle getBundle(String k)', 'CharSequence getCharSequence(String k){return (CharSequence)get(k);}Bundle getBundle(String k)')
harness=harness.replace('int getId(){return id;}', 'String tag;int getId(){return id;}String getTag(){return tag;}')
harness=harness.replace('List<Integer> operations=new ArrayList<>();', 'List<String>taggedCancels=new ArrayList<>();List<Integer> operations=new ArrayList<>();')
harness=harness.replace('void cancel(int id){cancels++;}', 'void cancel(String tag,int id){taggedCancels.add(tag);}void cancel(int id){cancels++;}')
harness=harness.replace(' System.out.println("Vivo atomic payload/lifecycle passed:', '''
 Notification stripped=new Notification();StatusBarNotification rewritten=new StatusBarNotification(7,stripped);
 rewritten.tag="VIVO_SUPERX_TAG";manager.active=new StatusBarNotification[]{rewritten};
 check(VivoAtomicNotification.active(manager,7));int posts=manager.operations.size();
 VivoAtomicNotification.end(c,manager,7);
 check(manager.taggedCancels.equals(List.of("VIVO_SUPERX_TAG")));
 check(manager.operations.size()==posts);
 System.out.println("Vivo atomic payload/lifecycle passed:''')
out=root/'build/vivo-atomic-check';out.mkdir(parents=True,exist_ok=True)
f=out/'VivoAtomicCheck.java';f.write_text(harness,encoding='utf-8')
java=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out),str(f)],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out),'VivoAtomicCheck'],check=True)
