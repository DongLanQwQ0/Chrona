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
title = next(n for n in layout.iter() if n.get(android+'id') == '@+id/week_widget_title')
assert title.get(android+'layout_width') == 'wrap_content' and title.get(android+'layout_weight') is None
assert title.get(android+'includeFontPadding') == 'false'
home = (src / 'DashboardActivity.java').read_text(encoding='utf-8')
toggle = home[home.index('private CheckBox homeSystemCalendarToggle()'):home.index('private void home(HomeData')]
toggle_view = (src / 'CalendarVisibilityToggle.java').read_text(encoding='utf-8')
assert 'new CalendarVisibilityToggle(this)' in toggle and 'extends CheckBox' in toggle_view
assert '(getWidth() - size) / 2' in toggle_view and '(getHeight() - size) / 2' in toggle_view
assert 'UiStyle.pressable(this)' in toggle_view and 'setForeground(' not in toggle_view
assert 'drawCircle' not in toggle_view, 'State uses icon tint; press fill uses the shared capsule mask'
assert 'tint.evaluate(checkedFraction, palette.muted, palette.primary)' in toggle_view
assert 'setStateDescription(state)' in toggle and 'include.setChecked(false)' in toggle
assert 'refresh.removeCallbacks(homeCalendarToggleRefresh)' in toggle
assert 'refresh.postDelayed(homeCalendarToggleRefresh, UiMotion.isEnabled() ? UiMotion.ENTER : 0L)' in toggle
provider = (src / 'WeekCalendarWidgetProvider.java').read_text(encoding='utf-8')
activity = (src / 'CalendarOverviewPage.java').read_text(encoding='utf-8')
assert '.putExtra("open_week", true)' in provider and 'CalendarOverviewActivity.class' in provider
assert 'getBooleanExtra("open_week", false)' in activity and 'mode = WEEK; date = LocalDate.now()' in activity
assert 'setOnClickPendingIntent(R.id.week_widget_root' in provider
assert 'setColorFilter' not in provider, 'Use remotely supported bitmap binding for theme tint'
assert 'AgendaRepository.load' not in provider and 'TaskStore' not in provider, 'Shortcut must not read calendar data'
assert 'onAppWidgetOptionsChanged' in provider and 'new RemoteViews(variants)' in provider
assert provider.count('new int[]{64, 140}') == 2, 'Four adaptive size variants'
assert 'narrow && !tall' in provider and 'widget_week_calendar_vertical' in provider
shortcuts = (src / 'LauncherShortcuts.java').read_text(encoding='utf-8')
assert '"quick_capture", "quick_week", "quick_schedule", "quick_inbox"' in shortcuts
assert 'UiStyle.colors(context).primary' in shortcuts and 'drawable.setTint(color)' in shortcuts
assert 'setDynamicShortcuts(shortcuts)' in shortcuts
routes = (src / 'LauncherShortcutActivity.java').read_text(encoding='utf-8')
for action in ('CAPTURE','WEEK','SCHEDULE','INBOX'):
    assert 'com.donglan.chrona.shortcut.' + action in routes
assert 'LauncherShortcutActivity.class' in shortcuts

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
    'android/content/res/Resources.java': 'package android.content.res;public class Resources {public static class Metrics{public float density=1;}public Metrics getDisplayMetrics(){return new Metrics();}}',
    'android/view/ScaleGestureDetector.java': '''package android.view;public class ScaleGestureDetector {
public static class SimpleOnScaleGestureListener {public boolean onScaleBegin(ScaleGestureDetector d){return true;}public boolean onScale(ScaleGestureDetector d){return true;}}
SimpleOnScaleGestureListener listener;public ScaleGestureDetector(android.content.Context c,SimpleOnScaleGestureListener l){listener=l;}
public void onTouchEvent(MotionEvent e){if(e.getActionMasked()==5)listener.onScaleBegin(this);if(e.multi&&e.getActionMasked()==2)listener.onScale(this);}
public float getScaleFactor(){return .5f;}public float getFocusX(){return 100;}public float getFocusY(){return 80;}}''',
    'android/view/MotionEvent.java': '''package android.view; public class MotionEvent {
public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
public boolean multi;int action;float x,y;public MotionEvent(int a,float x,float y){action=a;this.x=x;this.y=y;}
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
public static final int OVER_SCROLL_NEVER=2;public int overscroll;public void setOverScrollMode(int mode){overscroll=mode;}
public int x,y,flingVelocity,stops;public void scrollBy(int x,int y){this.x+=x;this.y+=y;}
public void fling(int v){flingVelocity=v;}public boolean onTouchEvent(android.view.MotionEvent e){if(e.getActionMasked()==0){stops++;flingVelocity=0;}return true;}}''',
    'android/widget/HorizontalScrollView.java': '''package android.widget;public class HorizontalScrollView extends ScrollView {
public int cancels,dispatches;public HorizontalScrollView(android.content.Context c){}
public int getWidth(){return 300;}public boolean canScrollHorizontally(int d){return d<0?x>0:x<500;}
public int getScrollX(){return x;}@Override public void scrollBy(int dx,int dy){x=Math.max(0,Math.min(500,x+dx));}
public android.content.res.Resources getResources(){return new android.content.res.Resources();}
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
check(v.overscroll==ScrollView.OVER_SCROLL_NEVER&&vertical.overscroll==ScrollView.OVER_SCROLL_NEVER,"both week axes suppress platform edge stretch");
send(v,0,100,100);send(v,2,100,80);check(v.x==0&&vertical.y==20,"first vertical move");
send(v,2,70,60);check(v.x==30&&vertical.y==40,"diagonal move follows both axes");
send(v,2,50,70);check(v.x==50&&vertical.y==30,"direction change within same gesture");
send(v,1,40,80);check(v.flingVelocity==1000&&vertical.flingVelocity==-1000,"independent axis velocities");
check(v.cancels==1&&!v.getParent().disallowed,"child cancelled once and ancestor released");
send(v,0,90,90);check(v.flingVelocity==0&&vertical.flingVelocity==0,"new touch stops both flings");
int before=v.dispatches;send(v,1,90,90);check(v.dispatches==before+1,"tap still reaches child");
send(v,0,100,100);send(v,2,80,100);send(v,3,70,100);check(v.flingVelocity==0,"cancel does not fling");
int[] direction={0};float[] scale={1};v.gestures(d->direction[0]=d,(f,x,y)->scale[0]*=f);
v.x=0;send(v,0,100,100);send(v,2,170,100);send(v,1,170,100);check(direction[0]==-1,"outward swipe from left edge changes previous week");
direction[0]=0;v.x=500;send(v,0,170,100);send(v,2,100,100);send(v,1,100,100);check(direction[0]==1,"outward swipe from right edge changes next week");
direction[0]=0;v.x=450;send(v,0,170,100);send(v,2,120,100);send(v,1,120,100);check(direction[0]==0,"reaching edge itself cannot switch week");
v.x=450;send(v,0,170,100);send(v,2,100,100);send(v,2,30,100);send(v,1,30,100);check(direction[0]==1,"same drag consumes content then sufficient outward distance switches week");
direction[0]=0;v.x=50;send(v,0,100,100);send(v,2,170,100);send(v,2,240,100);send(v,1,240,100);check(direction[0]==-1,"same drag reaches left edge then continues outward");
direction[0]=0;v.x=450;send(v,0,170,100);send(v,2,50,100);send(v,2,90,100);send(v,1,90,100);check(direction[0]==0&&v.x<500,"reversal back into content cancels outward drag");
v.x=450;send(v,0,170,100);send(v,2,100,100);send(v,1,100,100);check(direction[0]==0,"only unconsumed edge distance counts, not prior scrolling");
v.x=0;send(v,0,100,100);send(v,2,120,180);send(v,1,190,190);check(direction[0]==0,"vertical intent cannot switch week");
v.x=0;send(v,0,100,100);send(v,2,140,100);send(v,1,140,100);check(direction[0]==0,"below threshold cannot switch week");
v.x=0;send(v,0,100,100);send(v,5,100,100);MotionEvent pinch=new MotionEvent(2,170,100);pinch.multi=true;v.dispatchTouchEvent(pinch);send(v,1,190,100);
check(scale[0]==.5f&&direction[0]==0&&v.flingVelocity==0&&vertical.flingVelocity==0,"pinch zoom cancels click, fling and edge navigation");
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
paths.append(src / 'CalendarSwipe.java')
paths.append(src / 'LauncherShortcutActivity.java')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java/'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), *map(str, paths)], check=True)
subprocess.run([str(java/'java.exe'), '-cp', str(out), 'com.donglan.chrona.WeekCalendarCheck'], check=True)
