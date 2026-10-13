"""Execute production vivo payload/lifecycle with notification transport doubles."""
import os, re, subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
source=(root/'app/src/main/java/com/donglan/chrona/VivoAtomicNotification.java').read_text(encoding='utf-8')
source=re.sub(r'^package .*;\s*|^import .*;\s*','',source,flags=re.M).replace('android.service.notification.StatusBarNotification','StatusBarNotification').replace('android.util.Log','Log')
harness=r'''
import java.util.*;
class Bundle extends HashMap<String,Object> {void putInt(String k,int v){put(k,v);}void putString(String k,String v){put(k,v);}void putCharSequence(String k,CharSequence v){put(k,v);}void putBoolean(String k,boolean v){put(k,v);}void putParcelable(String k,Object v){put(k,v);}void putBundle(String k,Bundle v){put(k,v);}}
class Build {static String MANUFACTURER;}
class Context {NotificationManager manager=new NotificationManager();String getPackageName(){return "com.donglan.chrona";}Object getSystemService(Class<?> c){return manager;}}
class PendingIntent {}
class Icon {int resource;static Icon createWithResource(Context c,int r){Icon icon=new Icon();icon.resource=r;return icon;}}
class R {static class drawable {static int ic_chrona_foreground=2;}}
class Log {static void w(String a,String b,Exception c){}static void i(String a,String b){}}
class Notification {Bundle extras=new Bundle();static class Builder {Notification n;Builder(Notification n){this.n=n;}static Builder recoverBuilder(Context c,Notification n){Notification copy=new Notification();copy.extras.putAll(n.extras);return new Builder(copy);}Builder addExtras(Bundle b){n.extras.putAll(b);return this;}Builder setOnlyAlertOnce(boolean b){return this;}Notification build(){return n;}}}
class StatusBarNotification {int id;Notification n;StatusBarNotification(int id,Notification n){this.id=id;this.n=n;}int getId(){return id;}Notification getNotification(){return n;}}
class NotificationManager {StatusBarNotification[] active=new StatusBarNotification[0];List<Integer> operations=new ArrayList<>();int cancels;boolean fail,scene=true;public boolean getSceneStatus(String pkg,String name){if(!pkg.equals("com.donglan.chrona")||!name.equals("CHRONA_AGENDA"))throw new AssertionError();return scene;}StatusBarNotification[] getActiveNotifications(){return active;}void notify(int id,Notification n){if(fail)throw new RuntimeException();operations.add((Integer)n.extras.get("notification.superx.operation"));}void cancel(int id){cancels++;}}
__SOURCE__
public class VivoAtomicCheck {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("vivo check "+checks);}
 public static void main(String[] args){
 Context c=new Context();PendingIntent details=new PendingIntent();
 for(String brand:new String[]{"google","samsung","OPPO",null}){Build.MANUFACTURER=brand;check(VivoAtomicNotification.extras(c,"课","开始","13:25–15:50","教室","13:25",details,false).isEmpty());}
 Build.MANUFACTURER="VIVO";
 check(VivoAtomicNotification.sceneEnabled(c).equals(true));c.manager.scene=false;check(VivoAtomicNotification.sceneEnabled(c).equals(false));
 Bundle b=VivoAtomicNotification.extras(c,"微积分","即将开始","10:00–12:25","东2-204","10:00",details,false);
 check(b.get("notification.superx.operation").equals(0));check(b.get("notification.superx.template").equals(1));check(b.get("notification.superx.scene").equals("CHRONA_AGENDA"));check(b.get("notification.superx.dismissWhenKill").equals(false));check(b.get("notification.superx.showNotify").equals(true));check(b.get("notification.superx.clickResp")==details);
 Bundle base=(Bundle)b.get("notification.superx.baseInfos"), info=(Bundle)b.get("notification.superx.infos"), island=(Bundle)b.get("notification.superx.island");
 check(base.get("notification.superx.baseInfos.title").equals("微积分"));check(info.get("notification.superx.infos.describe").equals("东2-204"));check(info.get("notification.superx.infos.image") instanceof Icon);check(((Icon)info.get("notification.superx.infos.image")).resource==R.drawable.ic_chrona_foreground);
 check(island.get("island.superx.leftTemplate").equals(1)&&island.get("island.superx.rightTemplate").equals(4));
 check(((Bundle)island.get("island.superx.leftInfo")).get("island.superx.leftInfo.content").equals("10:00"));check(((Bundle)island.get("island.superx.rightInfo")).get("island.superx.rightInfo.content").equals("微积分"));check(island.get("island.superx.clickResp")==details);
 check(VivoAtomicNotification.extras(c,"课","进行中","range"," ","10:00",details,true).get("notification.superx.operation").equals(1));
 check(((Bundle)VivoAtomicNotification.extras(c,"课","进行中","range"," ","10:00",details,true).get("notification.superx.infos")).get("notification.superx.infos.describe").equals("range"));
 Bundle capsule=(Bundle)b.get("notification.superx.capsule");check(capsule.get("notification.superx.capsule.state").equals(1));check(capsule.get("notification.superx.capsule.icon") instanceof Icon);check(capsule.get("notification.superx.capsule.content").equals("10:00 微积分"));check(capsule.get("notification.superx.capsule.clickResp")==details);
 Bundle shortInfo=(Bundle)b.get("notification.superx.shortInfos");check(shortInfo.get("notification.superx.shortInfos.image")==info.get("notification.superx.infos.image"));check(shortInfo.get("notification.superx.shortInfos.describeShort").equals("即将开始"));check(shortInfo.get("notification.superx.shortInfos.coreInfoShort").equals("10:00"));
 Notification n=new Notification();n.extras=b;NotificationManager manager=new NotificationManager();manager.active=new StatusBarNotification[]{new StatusBarNotification(7,n)};
 check(VivoAtomicNotification.active(manager,7));check(!VivoAtomicNotification.active(manager,8));VivoAtomicNotification.end(c,manager,7);check(manager.operations.equals(List.of(2))&&manager.cancels==1);check(b.get("notification.superx.operation").equals(0));
 manager.fail=true;VivoAtomicNotification.end(c,manager,7);check(manager.cancels==2);
 Build.MANUFACTURER="google";manager.fail=false;VivoAtomicNotification.end(c,manager,7);check(manager.operations.size()==1&&manager.cancels==3);check(!VivoAtomicNotification.active(manager,7));
 System.out.println("Vivo atomic payload/lifecycle passed: "+checks+" checks (production adapter, notification doubles)");
 }
}
'''.replace('__SOURCE__',source)
out=root/'build/vivo-atomic-check';out.mkdir(parents=True,exist_ok=True)
f=out/'VivoAtomicCheck.java';f.write_text(harness,encoding='utf-8')
java=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out),str(f)],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out),'VivoAtomicCheck'],check=True)
