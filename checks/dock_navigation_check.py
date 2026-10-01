"""Exercise the real DockNavigationLayout on a small UI-queue test double.

Models the relevant AOSP contract: UP posts a child click; detach cancels it.
No Android runtime/dependencies are installed. This does not replace device tests.
"""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/dock-navigation-check"
STUBS = {
    "android/content/Context.java": """package android.content;
public class Context { public android.content.res.Resources getResources(){return new android.content.res.Resources();} }""",
    "android/content/res/Resources.java": """package android.content.res;
public class Resources { public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();} }""",
    "android/util/DisplayMetrics.java": "package android.util; public class DisplayMetrics { public float density=1; }",
    "android/content/res/ColorStateList.java": "package android.content.res; public class ColorStateList { public static ColorStateList valueOf(int x){return new ColorStateList();} }",
    "android/graphics/Canvas.java": "package android.graphics; public class Canvas {}",
    "android/graphics/drawable/GradientDrawable.java": """package android.graphics.drawable;
public class GradientDrawable { public static float lastCenter; int l,r;
public void setCornerRadius(float x){} public void setColor(int c){}
public void setBounds(int l,int t,int r,int b){this.l=l;this.r=r;}
public void draw(android.graphics.Canvas c){lastCenter=(l+r)/2f;} }""",
    "android/os/SystemClock.java": "package android.os; public class SystemClock { public static long uptimeMillis(){return android.view.View.now;} }",
    "android/view/HapticFeedbackConstants.java": "package android.view; public class HapticFeedbackConstants { public static final int LONG_PRESS=0; }",
    "android/view/ViewConfiguration.java": """package android.view; public class ViewConfiguration {
public static ViewConfiguration get(android.content.Context c){return new ViewConfiguration();}
public int getScaledTouchSlop(){return 8;} public static int getLongPressTimeout(){return 500;} }""",
    "android/view/MotionEvent.java": """package android.view; public class MotionEvent {
public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
int a;float x,y; public MotionEvent(int a,float x,float y){this.a=a;this.x=x;this.y=y;}
public static MotionEvent obtain(long d,long t,int a,float x,float y,int m){return new MotionEvent(a,x,y);}
public int getActionMasked(){return a;} public float getX(){return x;} public float getY(){return y;} public void recycle(){} }""",
    "android/view/View.java": """package android.view;
import java.util.*; import android.content.Context;
public class View {
 public static long now; private static long sequence;
 private record Job(View owner,Runnable action,long time,long order){}
 private static final PriorityQueue<Job> jobs=new PriorityQueue<>(Comparator.comparingLong(Job::time).thenComparingLong(Job::order));
 public static void advance(long ms){long end=now+ms;while(!jobs.isEmpty()&&jobs.peek().time<=end){Job j=jobs.remove();now=j.time;j.action.run();}now=end;}
 public static void reset(){jobs.clear();now=sequence=0;}
 public interface OnClickListener {void onClick(View v);} private OnClickListener click;
 private boolean pressed; private final Runnable perform=()->{if(pressed){pressed=false;performClick();}};
 protected Context context; protected View parent; public int l,t,r,b;
 public View(Context c){context=c;} public Context getContext(){return context;}
 public android.content.res.Resources getResources(){return context.getResources();}
 public View getParent(){return parent;} public void requestDisallowInterceptTouchEvent(boolean x){}
 public int getLeft(){return l;} public int getRight(){return r;} public int getTop(){return t;} public int getBottom(){return b;}
 public int getWidth(){return r-l;} public int getHeight(){return b-t;}
 public void layout(int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;}
 public void setOnClickListener(OnClickListener c){click=c;} public boolean performClick(){if(click!=null)click.onClick(this);return click!=null;}
 public boolean dispatchTouchEvent(MotionEvent e){return onTouchEvent(e);}
 public boolean onTouchEvent(MotionEvent e){if(click==null)return false;
  if(e.a==MotionEvent.ACTION_DOWN)pressed=true;
  if(e.a==MotionEvent.ACTION_CANCEL){pressed=false;removeCallbacks(perform);}
  if(e.a==MotionEvent.ACTION_UP&&pressed)post(perform);
  return true;}
 public boolean post(Runnable action){return postDelayed(action,0);}
 public void postOnAnimation(Runnable action){postDelayed(action,16);}
 public boolean postDelayed(Runnable action,long delay){jobs.add(new Job(this,action,now+delay,sequence++));return true;}
 public boolean removeCallbacks(Runnable action){return jobs.removeIf(j->j.owner==this&&j.action==action);}
 public void invalidate(){} public boolean performHapticFeedback(int x){return true;}
 protected void onDetachedFromWindow(){jobs.removeIf(j->j.owner==this);pressed=false;}
 public void detach(){onDetachedFromWindow();parent=null;}
 protected void dispatchDraw(android.graphics.Canvas c){}
} """,
    "android/view/ViewGroup.java": """package android.view;
import java.util.*; public class ViewGroup extends View {
 protected final List<View> children=new ArrayList<>(); private View target;
 public ViewGroup(android.content.Context c){super(c);}
 public void addView(View v){children.add(v);v.parent=this;} public int getChildCount(){return children.size();}
 public View getChildAt(int i){return i<0||i>=children.size()?null:children.get(i);}
 public void removeAllViews(){for(View v:new ArrayList<>(children))v.detach();children.clear();target=null;}
 @Override public void detach(){removeAllViews();super.detach();}
 private MotionEvent local(MotionEvent e,View v){return new MotionEvent(e.a,e.x-v.l,e.y-v.t);}
 @Override public boolean dispatchTouchEvent(MotionEvent e){
  if(e.a==MotionEvent.ACTION_DOWN){target=null;for(int i=children.size()-1;i>=0;i--){View v=children.get(i);
   if(e.x>=v.l&&e.x<v.r&&e.y>=v.t&&e.y<v.b&&v.dispatchTouchEvent(local(e,v))){target=v;return true;}}}
  else if(target!=null){View v=target;boolean handled=v.dispatchTouchEvent(local(e,v));if(e.a==1||e.a==3)target=null;return handled;}
  return onTouchEvent(e);
 }
} """,
    "android/widget/LinearLayout.java": "package android.widget; public class LinearLayout extends android.view.ViewGroup { public LinearLayout(android.content.Context c){super(c);} }",
    "android/widget/FrameLayout.java": "package android.widget; public class FrameLayout extends android.view.ViewGroup { public FrameLayout(android.content.Context c){super(c);} }",
    "android/widget/TextView.java": """package android.widget; public class TextView extends android.view.View {
public TextView(android.content.Context c){super(c);} public void setTextColor(int c){}
public void setCompoundDrawableTintList(android.content.res.ColorStateList c){} }""",
    "android/view/animation/DecelerateInterpolator.java": "package android.view.animation; public class DecelerateInterpolator {public DecelerateInterpolator(float x){} }",
    "android/animation/ValueAnimator.java": """package android.animation;
public class ValueAnimator { public interface AnimatorUpdateListener {void onAnimationUpdate(ValueAnimator a);}
 float from,to; AnimatorUpdateListener listener; public static boolean enabled=true;
 public static boolean areAnimatorsEnabled(){return enabled;}
 public static ValueAnimator ofFloat(float a,float b){ValueAnimator v=new ValueAnimator();v.from=a;v.to=b;return v;}
 public void setDuration(long d){} public void setInterpolator(Object i){} public void cancel(){}
 public void addUpdateListener(AnimatorUpdateListener l){listener=l;} public Object getAnimatedValue(){return to;}
 public void start(){if(listener!=null)listener.onAnimationUpdate(this);} }""",
    "com/donglan/chrona/UiStyle.java": """package com.donglan.chrona; public class UiStyle {
public static final int RADIUS_PANEL=20;
public static class Palette {public int onPrimaryContainer,muted,primaryContainer;}
public static Palette colors(android.content.Context c){return new Palette();} }""",
}

RUNNER = """package com.donglan.chrona;
import android.content.Context; import android.view.*; import android.widget.*;
import android.graphics.Canvas; import android.graphics.drawable.GradientDrawable;
import java.util.*;
public class DockNavigationCheck {
 static int checks; static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static Context c=new Context(); static DockNavigationLayout dock; static List<Integer> selected; static int rebuilds;
 static void populate(){for(int i=0;i<3;i++){final int n=i;TextView button=new TextView(c);button.setOnClickListener(v->selected.add(n));
  if(i==2){FrameLayout wrapper=new FrameLayout(c);wrapper.layout(200,0,300,60);button.layout(0,0,100,60);wrapper.addView(button);
   TextView badge=new TextView(c);badge.layout(50,0,85,20);badge.setOnClickListener(v->selected.add(2));wrapper.addView(badge);dock.addView(wrapper);}
  else{button.layout(i*100,0,(i+1)*100,60);dock.addView(button);}}
 }
 static void init(){View.reset();selected=new ArrayList<>();rebuilds=0;
  dock=new DockNavigationLayout(c,n->{selected.add(n);dock.beginPageSettle(n);},()->{rebuilds++;dock.removeAllViews();populate();dock.setSelectedIndex(0);});
  dock.layout(0,0,300,60);new FrameLayout(c).addView(dock);populate();dock.setSelectedIndex(0);}
 static void touch(int a,float x,float y){dock.dispatchTouchEvent(new MotionEvent(a,x,y));}
 static float center(){dock.dispatchDraw(new Canvas());return GradientDrawable.lastCenter;}
 static void close(float actual,float expected,String label){require(Math.abs(actual-expected)<=1f,label+" got "+actual);}
 public static void main(String[] args){
  for(int i=0;i<3;i++){init();touch(0,i*100+25,35);View.advance(100);touch(1,i*100+25,35);
   require(rebuilds==0,"Do not rebuild during UP before queued child click");View.advance(0);
   require(selected.equals(List.of(i)),"Each ordinary button tap executes exactly once before detach: "+i);}
  init();touch(0,270,10);View.advance(100);touch(1,270,10);View.advance(0);require(selected.equals(List.of(2)),"Badge tap survives refresh");
  init();touch(0,130,35);View.advance(400);touch(1,130,35);View.advance(0);require(selected.equals(List.of(1)),"Tap held below long-press timeout remains a click");
  init();touch(0,25,35);touch(1,25,35);View.advance(0);touch(0,125,35);touch(1,125,35);View.advance(0);require(selected.equals(List.of(0,1)),"Consecutive ordinary taps each execute once");
  init();touch(0,130,25);touch(2,132,40);touch(1,132,40);View.advance(0);require(selected.equals(List.of(1)),"Vertical drift inside button retains click");
  init();dock.getChildAt(0).layout(10,0,90,60);touch(0,95,35);touch(1,95,35);View.advance(0);require(selected.equals(List.of(0)),"Gap tap selects nearest button");
  init();dock.getChildAt(0).layout(10,0,90,60);touch(0,95,35);touch(1,95,100);View.advance(0);require(selected.isEmpty(),"Gap tap released outside never selects");
  init();dock.getChildAt(0).layout(10,0,90,60);touch(0,95,35);touch(2,95,100);touch(2,95,35);touch(1,95,35);View.advance(0);require(selected.isEmpty(),"Gap tap leaving and reentering does not accidentally click");
  init();touch(0,30,35);touch(2,245,35);touch(1,245,35);View.advance(0);require(selected.equals(List.of(2)),"Horizontal drag commits once without ghost child tap");
  init();touch(0,140,35);View.advance(500);touch(2,245,35);touch(1,245,35);View.advance(0);require(selected.equals(List.of(2)),"Long press then drag commits once");
  init();touch(0,30,35);touch(2,245,35);touch(3,245,35);View.advance(0);require(selected.isEmpty(),"Cancel never selects");close(center(),50,"Cancel returns to actual selection");
  init();touch(0,30,35);touch(2,245,35);touch(5,245,35);touch(1,245,35);View.advance(0);require(selected.isEmpty(),"Multiple pointers cancel");
  init();touch(0,30,35);touch(2,245,35);touch(1,245,100);View.advance(0);require(selected.isEmpty(),"Release outside dock cancels");
  init();dock.showPageProgress(0,1,.25f);close(center(),75,"Body swipe drives partial highlight");dock.showPageProgress(0,1,.5f);close(center(),100,"Half swipe is between buttons");
  dock.beginPageSettle(1);dock.updatePageSettle(0);close(center(),100,"Settle starts without jump");dock.updatePageSettle(.5f);close(center(),125,"Settle follows page fraction");
  dock.removeAllViews();populate();dock.setSelectedIndex(0);close(center(),125,"Rebuilding labels does not reset visual position");
  dock.updatePageSettle(1);dock.finishPageSelection(1);close(center(),150,"Commit arrives at destination");
  dock.showPageProgress(1,0,.4f);dock.beginPageSettle(1);dock.updatePageSettle(.5f);close(center(),130,"Cancelled swipe smoothly returns");
  dock.updatePageSettle(1);dock.finishPageSelection(1);close(center(),150,"Cancelled swipe finishes at source");
  init();dock.beginPageSettle(2);dock.updatePageSettle(.25f);close(center(),100,"Click can animate through intermediate position");dock.updatePageSettle(1);close(center(),250,"Click reaches distant destination");
  init();touch(0,30,35);touch(2,235,35);float released=center();touch(1,235,35);dock.updatePageSettle(0);close(center(),released,"Dock release does not jump to old source");
  dock.updatePageSettle(.5f);require(center()>released&&center()<250,"Dock release settles from finger toward target");
  init();dock.beginPageSettle(1);dock.updatePageSettle(.6f);float interrupted=center();dock.beginBodyDrag();dock.showPageProgress(0,1,0);close(center(),interrupted,"New body gesture preserves interrupted highlight position");
  dock.showPageProgress(0,1,.5f);close(center(),130,"Interrupted gesture moves continuously toward target");
  init();touch(0,225,35);touch(2,245,35);close(center(),50,"Remote drag begins at existing highlight");
  View.advance(16);float first=center();require(first>50&&first<245,"First frame moves partway toward finger");
  View.advance(32);float next=center();require(next>first&&next<245,"Subsequent frames approach finger");
  touch(2,150,35);close(center(),next,"Retargeting does not teleport during MOVE");
  View.advance(16);require(center()<next&&center()>150,"Frame follows changed finger direction");
  float beforeUp=center();touch(1,245,35);close(center(),beforeUp,"Release preserves visual position");
  require(selected.equals(List.of(2)),"Fast release selects finger target despite visual lag");
  dock.updatePageSettle(.5f);float settled=center();View.advance(80);close(center(),settled,"Finger loop stops when page animation takes over");
  init();touch(0,245,35);View.advance(500);close(center(),50,"Long press starts at existing highlight");
  View.advance(32);require(center()>50&&center()<245,"Long press smoothly approaches finger");
  touch(3,245,35);View.advance(400);close(center(),50,"Cancel stops finger loop and returns");
  init();touch(0,225,35);touch(2,245,35);View.advance(32);touch(5,245,35);View.advance(400);close(center(),50,"Multiple pointers stop follow");
  init();touch(0,225,35);touch(2,245,35);View.advance(32);float detached=center();dock.onDetachedFromWindow();View.advance(400);close(center(),detached,"Detach cancels scheduled follow");
  init();touch(0,225,35);touch(2,245,35);View.advance(32);float bodyStart=center();dock.beginBodyDrag();View.advance(400);close(center(),bodyStart,"Body gesture cancels scheduled follow");
  init();android.animation.ValueAnimator.enabled=false;touch(0,225,35);touch(2,245,35);close(center(),245,"Disabled animations follow immediately");android.animation.ValueAnimator.enabled=true;
  System.out.println("Dock navigation checks passed: "+checks+" (queue test double, not device runtime)");
 }
}
"""

for name, source in STUBS.items():
    file = OUT / "src" / name
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(source, encoding="utf-8")
runner = OUT / "src/com/donglan/chrona/DockNavigationCheck.java"
runner.write_text(RUNNER, encoding="utf-8")
java_home = Path(os.environ["JAVA_HOME"])
files = sorted(str(file) for file in (OUT / "src").rglob("*.java"))
files.append(str(ROOT / "app/src/main/java/com/donglan/chrona/DockNavigationLayout.java"))
subprocess.run([str(java_home / "bin/javac.exe"), "-encoding", "UTF-8", "-d", str(OUT / "classes"), *files], check=True)
subprocess.run([str(java_home / "bin/java.exe"), "-cp", str(OUT / "classes"), "com.donglan.chrona.DockNavigationCheck"], check=True)
