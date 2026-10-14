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

files = {
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
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java/'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), *map(str, paths)], check=True)
subprocess.run([str(java/'java.exe'), '-cp', str(out), 'com.donglan.chrona.WeekCalendarCheck'], check=True)
