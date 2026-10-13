"""Run the production reminder notification factory with Android transport doubles."""
import os
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/AgendaReminder.java').read_text(encoding='utf-8')

def method(marker, occurrence=0):
    start = source.index(marker)
    for _ in range(occurrence):
        start = source.index(marker, start + len(marker))
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end].replace('android.net.Uri', 'Uri').replace('android.view.View', 'View')

entry = (root / 'app/src/main/java/com/donglan/chrona/AgendaEntry.java').read_text(encoding='utf-8').replace('package com.donglan.chrona;', '')
timeline = (root / 'app/src/main/java/com/donglan/chrona/ReminderTimeline.java').read_text(encoding='utf-8').replace('package com.donglan.chrona;', '').replace('import java.util.*;', '')
harness = r'''
import java.util.*;
import java.time.*;
import java.time.format.*;
class Context {NotificationManager manager=new NotificationManager();String getPackageName(){return "com.donglan.chrona";}<T>T getSystemService(Class<T> type){return type.cast(manager);}}
class NotificationManager {boolean active;}
class Uri {String value;Uri(String v){value=v;}static Uri parse(String v){return new Uri(v);}}
class Intent {String action;Uri data;Map<String,String> extras=new HashMap<>();Intent(){}Intent(Context c,Class<?> type){}Intent setData(Uri d){data=d;return this;}Intent setAction(String a){action=a;return this;}Intent putExtra(String k,String v){extras.put(k,v);return this;}}
class PendingIntent {static final int FLAG_UPDATE_CURRENT=1,FLAG_IMMUTABLE=2;Intent intent;int flags,code;boolean broadcast;static PendingIntent make(int code,Intent i,int flags,boolean broadcast){PendingIntent p=new PendingIntent();p.intent=i;p.flags=flags;p.code=code;p.broadcast=broadcast;return p;}static PendingIntent getActivity(Context c,int code,Intent i,int flags){return make(code,i,flags,false);}static PendingIntent getBroadcast(Context c,int code,Intent i,int flags){return make(code,i,flags,true);}}
class Bundle {boolean update;double progress;String remaining,animations,capsuleText,clock;RemoteViews card,islandCard;}
class VivoAtomicNotification {static boolean vivo;static boolean available(Context c){return vivo;}static boolean active(NotificationManager m,int id){return vivo&&m.active;}static String countdownText(long remaining){return "native-timer:"+remaining;}static Bundle extras(Context c,String title,String status,String range,String time,String remaining,PendingIntent details,boolean same,RemoteViews card,RemoteViews islandCard,String animations,String capsuleText){Bundle b=new Bundle();b.update=same;b.progress=card.progress;b.remaining=remaining;b.card=card;b.islandCard=islandCard;b.animations=animations;b.capsuleText=capsuleText;b.clock=time;return b;}}
class AgendaReminderPreferences {static boolean useAtomic(Context c,boolean system,boolean available){return available;}}
class AtomicAgendaCard {static RemoteViews create(Context c,AgendaEntry e,long now,long notifiedAt,String nextTitle,PendingIntent details){RemoteViews v=new RemoteViews(c.getPackageName(),7);v.progress=ReminderTimeline.elapsedRatio(e,now,notifiedAt);v.notifiedAt=notifiedAt;v.nextTitle=nextTitle;v.details=details;return v;}static RemoteViews forIsland(Context c,AgendaEntry e,long now,long notifiedAt,String nextTitle,PendingIntent details){return create(c,e,now,notifiedAt,nextTitle,details);}static String marqueeConfiguration(){return "marquee-config";}}
class AgendaRepository {static Intent details(Context c,AgendaEntry e){return new Intent().setData(Uri.parse("content://calendar/events/"+e.id));}}
class AgendaReminderReceiver {}
class View {static final int GONE=8,VISIBLE=0;}
class SystemClock {static long elapsedRealtime(){return 300000;}}
class R {static class drawable {static int ic_notifications=1;}static class layout{static int notification_agenda_small=1,notification_agenda_large=2;}static class id{static int agenda_title=1,agenda_time=2,agenda_location=3,agenda_source=4,agenda_description=5,agenda_countdown=6;}}
class RemoteViews {Map<Integer,String> texts=new HashMap<>();boolean countdown;double progress;long notifiedAt;String nextTitle;PendingIntent details;RemoteViews(String pkg,int id){}void setTextViewText(int id,String text){texts.put(id,text);}void setViewVisibility(int id,int visibility){}void setChronometer(int id,long base,String format,boolean running){}void setChronometerCountDown(int id,boolean countdown){this.countdown=countdown;}}
class Notification {
 static final String CATEGORY_EVENT="event";static final int VISIBILITY_PUBLIC=1;
 String title,text;long timeout,when;boolean ongoing,onlyAlertOnce,showWhen;int visibility;
 PendingIntent details;Bundle extras;Object style;RemoteViews small,big,heads;List<Action> actions=new ArrayList<>();
 static class DecoratedCustomViewStyle {}
 static class BigTextStyle {String text;BigTextStyle bigText(String t){text=t;return this;}}
 static class Action {String label;PendingIntent intent;static class Builder {Action a=new Action();Builder(Object icon,String label,PendingIntent intent){a.label=label;a.intent=intent;}Action build(){return a;}}}
 static class Builder {Notification n=new Notification();Builder(Context c,String channel){}Builder setSmallIcon(int r){return this;}Builder setContentTitle(String t){n.title=t;return this;}Builder setContentText(String t){n.text=t;return this;}Builder setContentIntent(PendingIntent p){n.details=p;return this;}Builder setOngoing(boolean b){n.ongoing=b;return this;}Builder setOnlyAlertOnce(boolean b){n.onlyAlertOnce=b;return this;}Builder setWhen(long t){n.when=t;return this;}Builder setShowWhen(boolean b){n.showWhen=b;return this;}Builder setCategory(String s){return this;}Builder setVisibility(int v){n.visibility=v;return this;}Builder addExtras(Bundle b){n.extras=b;return this;}Builder setTimeoutAfter(long t){n.timeout=t;return this;}Builder addAction(Action a){n.actions.add(a);return this;}Builder setStyle(Object s){n.style=s;return this;}Builder setCustomContentView(RemoteViews v){n.small=v;return this;}Builder setCustomBigContentView(RemoteViews v){n.big=v;return this;}Builder setCustomHeadsUpContentView(RemoteViews v){n.heads=v;return this;}Notification build(){return n;}}
}
__ENTRY__
__TIMELINE__
public class AgendaNotificationCheck {
 static final String CHANNEL="upcoming_agenda";static final int NOTIFICATION_ID=0x434852;
 __FACTORY__
 __ACTION__
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("reminder check "+checks);}
 public static void main(String[] args){
  Context c=new Context();long now=1000000;
  AgendaEntry entry=new AgendaEntry(15,3,now+600000,now+3600000,"微积分", "东2-204","带上讲义","拾时",false,false,true);
  VivoAtomicNotification.vivo=true;Notification n=notification(c,entry,now,false);
  check(n.small==null&&n.big==null&&n.heads==null&&n.style instanceof Notification.BigTextStyle);
  check(Math.abs(n.extras.progress-2d/3)<1e-9&&n.extras.remaining.equals("10分钟后开始"));
  check(n.extras.card.details==n.details&&n.extras.animations.equals("marquee-config"));
  Notification custom=notification(c,entry,now,false,NOTIFICATION_ID,now-5000,"线性代数",true);check(custom.extras.card.notifiedAt==now-5000&&custom.extras.card.nextTitle.equals("线性代数"));
  Notification ordinary=notification(c,entry,now,false,NOTIFICATION_ID,now-5000,"线性代数",false);check(ordinary.extras==null&&ordinary.small!=null&&ordinary.style instanceof Notification.DecoratedCustomViewStyle);
  check(n.extras.card.notifiedAt==entry.start-ReminderTimeline.LEAD&&n.extras.card.nextTitle.isEmpty());
  check(notification(c,entry,entry.start-ReminderTimeline.LEAD,false).extras.progress==0);
  check(notification(c,entry,entry.start,false).extras.progress==0);
  check(notification(c,entry,entry.start+(entry.end-entry.start)/2,false).extras.progress==0.5);
  check(notification(c,entry,entry.end,false).extras.progress==1);
  check(notification(c,entry,entry.start-ReminderTimeline.LEAD-60000,false).extras.progress==0);
  check(notification(c,entry,entry.end+60000,false).extras.progress==1);
  String detail=((Notification.BigTextStyle)n.style).text;check(detail.contains("东2-204")&&detail.contains("带上讲义")&&detail.contains("拾时"));
  String compactRange=n.text.substring(n.text.indexOf(" · ")+3);
  check(compactRange.indexOf("月")==compactRange.lastIndexOf("月"));
  check(compactRange.endsWith(DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(entry.end).atZone(ZoneId.systemDefault()))));
  check(n.ongoing&&!n.showWhen&&n.visibility==Notification.VISIBILITY_PUBLIC&&n.timeout==3600000);
  check(n.actions.size()==3&&n.actions.get(0).intent==n.details);
  check(n.details.intent.data.value.equals("chrona://agenda/"+entry.key()));
  check(n.details.code==NOTIFICATION_ID);
  Notification second=notification(c,entry,now,false,NOTIFICATION_ID+1);check(second.details.code!=n.details.code);
  check(notification(c,entry,entry.start-1,false,NOTIFICATION_ID).extras.remaining.equals("即将开始"));
  check(notification(c,entry,entry.end-1,true,NOTIFICATION_ID).extras.remaining.equals("即将结束"));
  check(notification(c,entry,entry.start,true,NOTIFICATION_ID).extras.remaining.equals("还剩50分钟"));
  for(Notification.Action action:n.actions)check((action.intent.flags&PendingIntent.FLAG_IMMUTABLE)!=0);
  for(int i=1;i<3;i++){PendingIntent action=n.actions.get(i).intent;check(action.broadcast);check(action.intent.extras.get("key").equals(entry.key()));check(action.intent.data.value.equals("chrona://reminder/"+entry.key()));}
  check(n.actions.get(1).intent.intent.action.equals("snooze")&&n.actions.get(2).intent.intent.action.equals("hide"));
  c.manager.active=false;n=notification(c,entry,now,true);check(n.onlyAlertOnce&&!n.extras.update);
  c.manager.active=true;n=notification(c,entry,now,true);check(n.extras.update);
  n=notification(c,entry,entry.start+60000,true);check(n.text.startsWith("进行中")&&n.timeout==entry.finish()-entry.start-60000);
  VivoAtomicNotification.vivo=false;n=notification(c,entry,now,false);check(n.small!=null&&n.big!=null&&n.heads==n.small&&n.style instanceof Notification.DecoratedCustomViewStyle);check(n.big.countdown&&n.big.texts.get(R.id.agenda_description).equals("带上讲义"));
  AgendaEntry system=new AgendaEntry(80,0,entry.start,entry.end,"系统课程",null,"","课表",true,false,true);VivoAtomicNotification.vivo=true;n=notification(c,system,now,false);
  check(n.details.intent.data.value.equals("content://calendar/events/80"));check(((Notification.BigTextStyle)n.style).text.contains("地点未填写"));
  AgendaEntry overnight=new AgendaEntry(81,0,entry.start,entry.end+86400000,"跨天课程","","","课表",true,false,true);
  n=notification(c,overnight,now,false);check(n.text.indexOf("月")!=n.text.lastIndexOf("月"));
  check(notification(c,entry,entry.start-120001,false).extras.capsuleText.equals(notification(c,entry,entry.start-120001,false).extras.clock));
  check(notification(c,entry,entry.start-120000,false).extras.capsuleText.equals("native-timer:120000"));
  check(notification(c,entry,entry.start-1,false).extras.capsuleText.equals("native-timer:1"));
  check(notification(c,entry,entry.start,false).extras.capsuleText.equals(notification(c,entry,entry.start,false).extras.clock));
  check(notification(c,entry,entry.end-120001,false).extras.capsuleText.equals(notification(c,entry,entry.end-120001,false).extras.clock));
  check(notification(c,entry,entry.end-120000,false).extras.capsuleText.equals("native-timer:120000"));
  check(notification(c,entry,entry.end-1,false).extras.capsuleText.equals("native-timer:1"));
  for(String category:new String[]{"todo","reminder"}){
   AgendaEntry notice=new AgendaEntry(90,3,entry.start,entry.end,category,"","","拾时",false,false,true,null,category);
   check(notification(c,notice,notice.start-120000,false).extras.capsuleText.equals("native-timer:120000"));
   Notification started=notification(c,notice,notice.start,false);
   check(started.timeout==180000&&started.text.startsWith("已开始")&&started.extras.remaining.equals("已开始")&&started.extras.capsuleText.equals("已开始"));
   check(notification(c,notice,notice.start+179000,false).timeout==1000);
   Notification ordinaryNotice=notification(c,notice,notice.start+60000,false,NOTIFICATION_ID,now,"",false);
   check(ordinaryNotice.timeout==120000&&ordinaryNotice.text.startsWith("已开始"));
  }
  System.out.println("Reminder notification factory passed: "+checks+" checks; native vivo style, full details, immutable actions, occurrence identity, expiry and existing RemoteViews path");
 }
}
'''.replace('__ENTRY__', entry).replace('__TIMELINE__', timeline).replace('__FACTORY__', method('    private static Notification notification(') + '\n' + method('    private static Notification notification(', 1)).replace('__ACTION__', method('    private static PendingIntent action('))
harness = re.sub(r'notification\(c,([^,\n]+),([^,\n]+),(false|true)\)', r'notification(c,\1,\2,\3,NOTIFICATION_ID)', harness)
out = root / 'build/agenda-notification-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'AgendaNotificationCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'AgendaNotificationCheck'], check=True)
