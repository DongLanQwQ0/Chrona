"""Execute production reminder delivery status without creating channels or sending notifications."""
import os
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/NotificationDeliveryStatus.java').read_text(encoding='utf-8')
source = re.sub(r'^package .*;\s*|^import .*;\s*', '', source, flags=re.M).replace('android.Manifest.', 'Manifest.')
harness = r'''
import java.util.*;
class Build {static class VERSION {static int SDK_INT;}}
class PackageManager {static final int PERMISSION_GRANTED=0;}
class Manifest {static class permission {static final String POST_NOTIFICATIONS="android.permission.POST_NOTIFICATIONS";}}
class AgendaReminder {static final String CHANNEL="upcoming_agenda";}
class Settings {
 static final String ACTION_APP_NOTIFICATION_SETTINGS="android.settings.APP_NOTIFICATION_SETTINGS";
 static final String ACTION_CHANNEL_NOTIFICATION_SETTINGS="android.settings.CHANNEL_NOTIFICATION_SETTINGS";
 static final String EXTRA_APP_PACKAGE="android.provider.extra.APP_PACKAGE",EXTRA_CHANNEL_ID="android.provider.extra.CHANNEL_ID";
}
class Intent {
 final String action;final Map<String,String> extras=new HashMap<>();
 Intent(String action){this.action=action;}Intent putExtra(String key,String value){extras.put(key,value);return this;}
}
class NotificationChannel {
 final int importance;NotificationChannel(int importance){this.importance=importance;}int getImportance(){return importance;}
}
class NotificationManager {
 static final int IMPORTANCE_NONE=0;boolean allowed;int channelReads;final Map<String,NotificationChannel> channels=new HashMap<>();
 boolean areNotificationsEnabled(){return allowed;}
 NotificationChannel getNotificationChannel(String id){channelReads++;if(!id.equals(AgendaReminder.CHANNEL))throw new AssertionError("wrong channel: "+id);return channels.get(id);}
}
class Context {
 final NotificationManager manager=new NotificationManager();int permission,permissionReads;
 String getPackageName(){return "com.donglan.chrona";}
 int checkSelfPermission(String name){permissionReads++;if(!name.equals(Manifest.permission.POST_NOTIFICATIONS))throw new AssertionError(name);return permission;}
 <T>T getSystemService(Class<T> type){return type.cast(manager);}
}
__SOURCE__
public class NotificationDeliveryStatusCheck {
 static int checks;static void check(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 public static void main(String[] args){
  for(int sdk:new int[]{26,32,33,36})for(boolean app:new boolean[]{false,true})for(boolean permission:new boolean[]{false,true})for(int importance:new int[]{-1,0,1,2,4}){
   Build.VERSION.SDK_INT=sdk;Context c=new Context();c.manager.allowed=app;c.permission=permission?PackageManager.PERMISSION_GRANTED:-1;
   if(importance>=0)c.manager.channels.put(AgendaReminder.CHANNEL,new NotificationChannel(importance));
   c.manager.channels.put("unrelated",new NotificationChannel(0));
   Map<String,NotificationChannel> previous=new HashMap<>(c.manager.channels);
   NotificationDeliveryStatus state=NotificationDeliveryStatus.read(c);
   boolean appAllowed=app&&(sdk<33||permission),channelAllowed=importance!=0;
   String label="sdk="+sdk+" app="+app+" permission="+permission+" importance="+importance;
   check(state.appAllowed==appAllowed,"app permission "+label);
   check(state.channelCreated==(importance>=0),"channel creation state "+label);
   check(state.channelAllowed==channelAllowed,"specific channel state "+label);
   check(state.canPostReminders()==(appAllowed&&channelAllowed),"snapshot availability "+label);
   check(NotificationDeliveryStatus.canPostReminders(c)==(appAllowed&&channelAllowed),"static availability "+label);
   check(state.channelSummary().equals(importance==0?"提醒频道已关闭":importance<0?"首次提醒时创建":"已开启"),"channel summary "+label);
   check(c.manager.channels.equals(previous)&&c.manager.channelReads==2,"status only reads existing channels "+label);
   check(c.permissionReads==(sdk<33?0:2),"runtime permission only applies to API 33+ "+label);
  }
  Build.VERSION.SDK_INT=36;Context c=new Context();c.permission=PackageManager.PERMISSION_GRANTED;c.manager.allowed=true;
  NotificationDeliveryStatus initial=NotificationDeliveryStatus.read(c);
  check(initial.canPostReminders()&&!initial.channelCreated,"no channel is not a user block");
  c.manager.channels.put(AgendaReminder.CHANNEL,new NotificationChannel(0));
  check(!NotificationDeliveryStatus.canPostReminders(c),"fresh read detects channel disabled while app stays allowed");
  check(initial.canPostReminders()&&!initial.channelCreated,"snapshot stays immutable after system changes");
  c.manager.channels.put(AgendaReminder.CHANNEL,new NotificationChannel(4));
  check(NotificationDeliveryStatus.canPostReminders(c),"fresh read detects reopened channel");
  c.manager.allowed=false;check(!NotificationDeliveryStatus.canPostReminders(c),"enabled channel cannot bypass app block");
  c.manager.allowed=true;c.permission=-1;check(!NotificationDeliveryStatus.canPostReminders(c),"enabled channel cannot bypass runtime permission");
  Intent app=NotificationDeliveryStatus.appSettings(c),channel=NotificationDeliveryStatus.channelSettings(c);
  check(app.action.equals(Settings.ACTION_APP_NOTIFICATION_SETTINGS)&&app.extras.size()==1,"app settings intent");
  check(channel.action.equals(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)&&channel.extras.size()==2,"channel settings intent");
  check(app.extras.get(Settings.EXTRA_APP_PACKAGE).equals(c.getPackageName())&&channel.extras.get(Settings.EXTRA_APP_PACKAGE).equals(c.getPackageName()),"settings target this app");
  check(channel.extras.get(Settings.EXTRA_CHANNEL_ID).equals("upcoming_agenda"),"settings target the actual reminder channel");
  System.out.println("Notification delivery status passed: "+checks+" checks; permission/channel truth table, live refresh, read-only snapshots and precise settings intents");
 }
}
'''.replace('__SOURCE__', source)
out = root / 'build/notification-delivery-status-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'NotificationDeliveryStatusCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'NotificationDeliveryStatusCheck'], check=True)
