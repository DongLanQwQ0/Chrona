"""Execute production vivo payload/lifecycle with notification transport doubles."""
import os, re, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/donglan/chrona/VivoAtomicNotification.java').read_text(encoding='utf-8')
source=re.sub(r'^package .*;\s*|^import .*;\s*','',source,flags=re.M).replace('android.service.notification.StatusBarNotification','StatusBarNotification').replace('android.util.Log','Log')
source=source.replace('android.graphics.Bitmap', 'Bitmap').replace('android.widget.RemoteViews', 'RemoteViews')
source=source.replace('android.os.SystemClock', 'SystemClock')
harness=r'''
import java.util.*;
class Bundle extends HashMap<String,Object> {void putInt(String k,int v){put(k,v);}void putString(String k,String v){put(k,v);}void putCharSequence(String k,CharSequence v){put(k,v);}void putBoolean(String k,boolean v){put(k,v);}void putParcelable(String k,Object v){put(k,v);}void putBundle(String k,Bundle v){put(k,v);}void putParcelableArrayList(String k,ArrayList<Icon> v){put(k,v);}}
class Build {static String MANUFACTURER;}
class SystemClock {static long elapsed=300000;static long elapsedRealtime(){return elapsed;}}
class Context {NotificationManager manager=new NotificationManager();String getPackageName(){return "com.donglan.chrona";}<T>T getSystemService(Class<T> c){return c.cast(manager);}}
class PendingIntent {}
class RemoteViews {}
class Bitmap {static class Config {static Object ARGB_8888=new Object();}static Bitmap createBitmap(int w,int h,Object config){if(w!=1||h!=1)throw new AssertionError();return new Bitmap();}}
class Icon {int resource;Bitmap bitmap;static Icon createWithResource(Context c,int r){Icon icon=new Icon();icon.resource=r;return icon;}static Icon createWithBitmap(Bitmap bitmap){Icon icon=new Icon();icon.bitmap=bitmap;return icon;}}
class R {static class drawable {static int ic_chrona_foreground=2,ic_calendar_source=3,ic_check=4;}}
class UiStyle {static class Palette {int primary=0xff8855ee;}static Palette colors(Context c){return new Palette();}}
class Log {static void w(String a,String b,Exception c){}static void i(String a,String b){}}
class Notification {Bundle extras=new Bundle();static class Builder {Notification n;Builder(Notification n){this.n=n;}static Builder recoverBuilder(Context c,Notification n){Notification copy=new Notification();copy.extras.putAll(n.extras);return new Builder(copy);}Builder addExtras(Bundle b){n.extras.putAll(b);return this;}Builder setOnlyAlertOnce(boolean b){return this;}Notification build(){return n;}}}
class StatusBarNotification {int id;Notification n;StatusBarNotification(int id,Notification n){this.id=id;this.n=n;}int getId(){return id;}Notification getNotification(){return n;}}
class NotificationManager {StatusBarNotification[] active=new StatusBarNotification[0];List<Integer> operations=new ArrayList<>();int cancels;boolean fail,sceneFailure,allowed=true;Boolean scene=true;boolean areNotificationsEnabled(){return allowed;}public Boolean getSceneStatus(String pkg,String name){if(sceneFailure)throw new IllegalStateException("unavailable API");if(!pkg.equals("com.donglan.chrona")||!name.equals("CHRONA_AGENDA"))throw new AssertionError();return scene;}StatusBarNotification[] getActiveNotifications(){return active;}void notify(int id,Notification n){if(fail)throw new RuntimeException();operations.add((Integer)n.extras.get("notification.superx.operation"));}void cancel(int id){cancels++;}}
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
 Bundle b=VivoAtomicNotification.extras(c,"微积分","即将开始","10:00–12:25","10:00","距开始 15分钟",details,false,card,islandCard,animations);
 check(b.get("notification.superx.operation").equals(0));check(b.get("notification.superx.template").equals(7));check(b.get("notification.superx.scene").equals("CHRONA_AGENDA"));check(b.get("notification.superx.dismissWhenKill").equals(false));check(b.get("notification.superx.showNotify").equals(true));check(b.get("notification.superx.clickResp")==details);
 Bundle base=(Bundle)b.get("notification.superx.baseInfos"), island=(Bundle)b.get("notification.superx.island");
 check(base.get("notification.superx.baseInfos.title").equals("微积分"));
 check(b.get("notification.superx.customTemplate")==card);check(b.get("notification.superx.disableInvertColor").equals(true));check(b.get("notification.superx.animationsConfig").equals(animations));
 check(!b.containsKey("notification.superx.infos"));
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
 System.out.println("Vivo atomic payload/lifecycle passed: "+checks+" checks (production adapter, notification doubles)");
 }
}
'''.replace('__SOURCE__',source)
harness=harness.replace(',islandCard,animations)', ',islandCard,animations,"10:00")')
harness=harness.replace(' System.out.println("Vivo atomic payload/lifecycle passed:', r'''
 Build.MANUFACTURER="vivo";
 String timer=VivoAtomicNotification.countdownText(120000);
 check(timer.equals("{\"base\":420000,\"countDown\":true,\"started\":true,\"minUnit\":\"M\",\"elapsedFormatMS\":\"%02d:%02d\"}"));
 check(VivoAtomicNotification.countdownText(-1).equals(VivoAtomicNotification.countdownText(0)));
 SystemClock.elapsed=450000;check(VivoAtomicNotification.countdownText(1000).contains("\"base\":451000"));
 Bundle ticking=VivoAtomicNotification.extras(c,"微积分","即将开始","10:00–12:25","10:00","即将开始",details,true,card,islandCard,animations,timer);
 Bundle tickingIsland=(Bundle)ticking.get("notification.superx.island");
 check(((Bundle)tickingIsland.get("island.superx.leftInfo")).get("island.superx.leftInfo.content").equals(timer));
 check(((Bundle)ticking.get("notification.superx.capsule")).get("notification.superx.capsule.content").equals("10:00 微积分"));
 System.out.println("Vivo atomic payload/lifecycle passed:''')
out=root/'build/vivo-atomic-check';out.mkdir(parents=True,exist_ok=True)
f=out/'VivoAtomicCheck.java';f.write_text(harness,encoding='utf-8')
java=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out),str(f)],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out),'VivoAtomicCheck'],check=True)
