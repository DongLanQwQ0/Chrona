"""Exercise the production dual-axis gesture with Android transport stand-ins; check widget routing/resources."""
from pathlib import Path
import os
import subprocess
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/week-calendar-check'
android = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml')
receiver = next(n for n in manifest.iter('receiver') if n.get(android+'name') == '.WeekCalendarWidgetProvider')
assert receiver.find('meta-data').get(android+'resource') == '@xml/widget_week_calendar'
widget = ET.parse(root / 'app/src/main/res/xml/widget_week_calendar.xml').getroot()
assert widget.get(android+'initialLayout') == '@layout/widget_week_calendar'
assert widget.get(android+'resizeMode') == 'horizontal|vertical'
assert widget.get(android+'targetCellWidth') == '1' and widget.get(android+'targetCellHeight') == '1'
layout = ET.parse(root / 'app/src/main/res/layout/widget_week_calendar.xml')
ids = {n.get(android+'id') for n in layout.iter()}
for name in ('root', 'title', 'icon', 'background'):
    assert '@+id/week_widget_' + name in ids
provider = (src / 'WeekCalendarWidgetProvider.java').read_text(encoding='utf-8')
activity = (src / 'CalendarOverviewActivity.java').read_text(encoding='utf-8')
assert '.putExtra("open_week", true)' in provider and 'CalendarOverviewActivity.class' in provider
assert 'getBooleanExtra("open_week", false)' in activity and 'mode = WEEK; date = LocalDate.now()' in activity
assert 'setOnClickPendingIntent(R.id.week_widget_root' in provider
assert 'setColorFilter' not in provider, 'Use remotely supported bitmap binding for theme tint'
assert 'AgendaRepository.load' not in provider and 'TaskStore' not in provider, 'Shortcut must not read calendar data'
assert 'onAppWidgetOptionsChanged' in provider and 'new RemoteViews(variants)' in provider
assert provider.count('new int[]{64, 140}') == 2, 'Four adaptive size variants'
assert 'narrow && !tall' in provider and 'widget_week_calendar_vertical' in provider
shortcuts = ET.parse(root / 'app/src/main/res/xml/launcher_shortcuts.xml').getroot()
assert {n.get(android+'shortcutId') for n in shortcuts} == {'capture','week','schedule','inbox'}
routes = (src / 'LauncherShortcutActivity.java').read_text(encoding='utf-8')
for shortcut in shortcuts:
    intent = shortcut.find('intent')
    assert intent.get(android+'action') in routes
    assert intent.get(android+'targetClass') == 'com.donglan.chrona.LauncherShortcutActivity'
launcher = next(n for n in manifest.iter('activity') if n.get(android+'name') == '.DashboardActivity')
assert any(n.get(android+'resource') == '@xml/launcher_shortcuts' for n in launcher.iter('meta-data'))

files = {
    'android/os/Bundle.java': 'package android.os;public class Bundle {}',
    'android/content/Intent.java': '''package android.content;public class Intent {
public String action;public Class<?> target;public java.util.Map<String,Object>extras=new java.util.HashMap<>();
public Intent(Context c,Class<?> t){target=t;}public String getAction(){return action;}
public Intent putExtra(String k,int v){extras.put(k,v);return this;}public Intent putExtra(String k,boolean v){extras.put(k,v);return this;}}''',
    'android/app/Activity.java': '''package android.app;public class Activity extends android.content.Context {
public android.content.Intent input=new android.content.Intent(this,Activity.class),opened;public boolean finished;
public void onCreate(android.os.Bundle b){}public android.content.Intent getIntent(){return input;}
public void startActivity(android.content.Intent i){opened=i;}public void finish(){finished=true;}}''',
    'com/donglan/chrona/MainActivity.java': 'package com.donglan.chrona;public class MainActivity {}',
    'com/donglan/chrona/CalendarOverviewActivity.java': 'package com.donglan.chrona;public class CalendarOverviewActivity {}',
    'com/donglan/chrona/DashboardActivity.java': 'package com.donglan.chrona;public class DashboardActivity {static final String EXTRA_SECTION="section";static final int SCHEDULE=2,INBOX=1;}',
    'android/content/Context.java': 'package android.content; public class Context {}',
    'android/view/MotionEvent.java': '''package android.view; public class MotionEvent {
public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3;
int action;float x,y;public MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}
public static MotionEvent obtain(MotionEvent e){return new MotionEvent(e.action,e.x,e.y);}
public int getActionMasked(){return action;}public float getX(){return x;}public float getY(){return y;}
public void setAction(int a){action=a;}public void recycle(){} }''',
    'android/view/ViewConfiguration.java': '''package android.view;public class ViewConfiguration {
public static ViewConfiguration get(android.content.Context c){return new ViewConfiguration();}
public int getScaledTouchSlop(){return 8;}public int getScaledMaximumFlingVelocity(){return 10000;}}''',
    'android/view/VelocityTracker.java': '''package android.view; public class VelocityTracker {
float x,y,vx,vy;boolean first=true;public static VelocityTracker obtain(){return new VelocityTracker();}
public void addMovement(MotionEvent e){if(!first){vx=(e.getX()-x)*100;vy=(e.getY()-y)*100;}first=false;x=e.getX();y=e.getY();}
public void computeCurrentVelocity(int units,int max){}public float getXVelocity(){return vx;}public float getYVelocity(){return vy;}public void recycle(){} }''',
    'android/widget/ScrollView.java': '''package android.widget; public class ScrollView {
public int x,y,flingVelocity,stops;public void scrollBy(int x,int y){this.x+=x;this.y+=y;}
public void fling(int v){flingVelocity=v;}public boolean onTouchEvent(android.view.MotionEvent e){if(e.getActionMasked()==0){stops++;flingVelocity=0;}return true;}}''',
    'android/widget/HorizontalScrollView.java': '''package android.widget;public class HorizontalScrollView extends ScrollView {
public int cancels,dispatches;public HorizontalScrollView(android.content.Context c){}
public static class Parent{public boolean disallowed;public void requestDisallowInterceptTouchEvent(boolean b){disallowed=b;}}
final Parent parent=new Parent();public Parent getParent(){return parent;}
public boolean onInterceptTouchEvent(android.view.MotionEvent e){return false;}
public boolean dispatchTouchEvent(android.view.MotionEvent e){dispatches++;if(e.getActionMasked()==3)cancels++;return true;}
protected void onDetachedFromWindow(){} }''',
    'com/donglan/chrona/WeekCalendarCheck.java': '''package com.donglan.chrona;
import android.view.MotionEvent;import android.widget.ScrollView;
public class WeekCalendarCheck {
static void send(FreeCalendarScrollView v,int a,float x,float y){v.dispatchTouchEvent(new MotionEvent(a,x,y));}
static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
public static void main(String[] args){
String[] actions={"CAPTURE","WEEK","SCHEDULE","INBOX"};Class<?>[] targets={MainActivity.class,CalendarOverviewActivity.class,DashboardActivity.class,DashboardActivity.class};
for(int i=0;i<actions.length;i++){LauncherShortcutActivity route=new LauncherShortcutActivity();route.input.action="com.donglan.chrona.shortcut."+actions[i];route.onCreate(null);
check(route.finished&&route.opened.target==targets[i],"shortcut target "+actions[i]);
if(i==1)check(Boolean.TRUE.equals(route.opened.extras.get("open_week")),"week resets current week");
if(i>=2)check(route.opened.extras.get("section").equals(i==2?2:1),"correct dashboard section");}
LauncherShortcutActivity unknown=new LauncherShortcutActivity();unknown.input.action="untrusted";unknown.onCreate(null);check(unknown.finished&&unknown.opened==null,"unknown action cannot launch destination");
FreeCalendarScrollView v=new FreeCalendarScrollView(new android.content.Context());ScrollView vertical=new ScrollView();v.vertical(vertical);
send(v,0,100,100);send(v,2,100,80);check(v.x==0&&vertical.y==20,"first vertical move");
send(v,2,70,60);check(v.x==30&&vertical.y==40,"diagonal move follows both axes");
send(v,2,50,70);check(v.x==50&&vertical.y==30,"direction change within same gesture");
send(v,1,40,80);check(v.flingVelocity==1000&&vertical.flingVelocity==-1000,"independent axis velocities");
check(v.cancels==1&&!v.getParent().disallowed,"child cancelled once and ancestor released");
send(v,0,90,90);check(v.flingVelocity==0&&vertical.flingVelocity==0,"new touch stops both flings");
int before=v.dispatches;send(v,1,90,90);check(v.dispatches==before+1,"tap still reaches child");
send(v,0,100,100);send(v,2,80,100);send(v,3,70,100);check(v.flingVelocity==0,"cancel does not fling");
v.onDetachedFromWindow();System.out.println("Week calendar checks passed: widget resources/routing and live production two-axis gesture with transport stand-ins");
}}'''
}
paths = []
for name, content in files.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
    paths.append(path)
paths.append(src / 'FreeCalendarScrollView.java')
paths.append(src / 'LauncherShortcutActivity.java')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java/'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), *map(str, paths)], check=True)
subprocess.run([str(java/'java.exe'), '-cp', str(out), 'com.donglan.chrona.WeekCalendarCheck'], check=True)
