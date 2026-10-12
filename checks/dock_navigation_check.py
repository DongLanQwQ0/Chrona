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
public class GradientDrawable { public static float lastCenter,lastWidth,lastHeight,lastLeft,lastRight;public static int lastColor; int l,r,t,b;
public void setCornerRadius(float x){} public void setColor(int c){lastColor=c;}
public void setBounds(int l,int t,int r,int b){this.l=l;this.r=r;this.t=t;this.b=b;}
public void draw(android.graphics.Canvas c){lastCenter=(l+r)/2f;lastWidth=r-l;lastHeight=b-t;lastLeft=l;lastRight=r;} }""",
    "android/os/SystemClock.java": "package android.os; public class SystemClock { public static long uptimeMillis(){return android.view.View.now;} }",
    "android/view/HapticFeedbackConstants.java": "package android.view; public class HapticFeedbackConstants { public static final int LONG_PRESS=0; }",
    "android/view/ViewConfiguration.java": """package android.view; public class ViewConfiguration {
public static ViewConfiguration get(android.content.Context c){return new ViewConfiguration();}
public int getScaledTouchSlop(){return 8;} public static int getLongPressTimeout(){return 500;} }""",
    "android/view/MotionEvent.java": """package android.view; public class MotionEvent {
public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
int a;float x,y; public MotionEvent(int a,float x,float y){this.a=a;this.x=x;this.y=y;}
public static MotionEvent obtain(long d,long t,int a,float x,float y,int m){return new MotionEvent(a,x,y);}
public static MotionEvent obtain(MotionEvent e){return new MotionEvent(e.a,e.x,e.y);}public void offsetLocation(float dx,float dy){x+=dx;y+=dy;}
public int getActionMasked(){return a;} public float getX(){return x;} public float getY(){return y;} public float getRawX(){return x;}public float getRawY(){return y;}public long getDownTime(){return 0;}public long getEventTime(){return View.now;}public void recycle(){} }""",
    "android/view/VelocityTracker.java": """package android.view;public class VelocityTracker {
 public static float velocity;public static int recycled;public static VelocityTracker obtain(){return new VelocityTracker();}
 public void addMovement(MotionEvent event){}public void computeCurrentVelocity(int units){}public float getXVelocity(){return velocity;}public float getYVelocity(){return velocity;}public void recycle(){recycled++;}}
""",
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
 public int layouts,invalidations;public void layout(int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;layouts++;if(this instanceof ViewGroup group)group.onLayout(true,l,t,r,b);}
 public static final int IMPORTANT_FOR_ACCESSIBILITY_NO=2;public void setImportantForAccessibility(int value){}
 public void setBackground(Object background){}public void setPivotX(float x){}public void setPivotY(float y){}
 public float translationX,translationY;public void setTranslationX(float x){translationX=x;}public void setTranslationY(float y){translationY=y;}public float getTranslationX(){return translationX;}public float getTranslationY(){return translationY;}
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
 public void invalidate(){invalidations++;} public boolean performHapticFeedback(int x){return true;}
 public float scaleX=1,scaleY=1;public void setScaleX(float value){scaleX=value;}public void setScaleY(float value){scaleY=value;}public float getScaleX(){return scaleX;}public float getScaleY(){return scaleY;}
 protected void onDetachedFromWindow(){jobs.removeIf(j->j.owner==this);pressed=false;}
 public void detach(){onDetachedFromWindow();parent=null;}
 protected void dispatchDraw(android.graphics.Canvas c){}
} """,
    "android/view/ViewGroup.java": """package android.view;
import java.util.*; public class ViewGroup extends View {
 protected final List<View> children=new ArrayList<>(); private View target;
 public ViewGroup(android.content.Context c){super(c);}
 public static class LayoutParams {public int width,height;public LayoutParams(int w,int h){width=w;height=h;}}
 public void addView(View view,LayoutParams params){addView(view);}
 protected void onLayout(boolean changed,int l,int t,int r,int b){}
 public void setClipChildren(boolean value){}public void setClipToPadding(boolean value){}
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
    "android/widget/LinearLayout.java": "package android.widget; public class LinearLayout extends android.view.ViewGroup {public static final int HORIZONTAL=0,VERTICAL=1; public void setOrientation(int value){} public LinearLayout(android.content.Context c){super(c);} }",
    "android/widget/FrameLayout.java": "package android.widget; public class FrameLayout extends android.view.ViewGroup { public FrameLayout(android.content.Context c){super(c);} protected void onLayout(boolean changed,int l,int t,int r,int b){for(android.view.View child:children)if(child instanceof android.widget.LinearLayout)child.layout(0,0,getWidth(),getHeight());} }",
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
public static void glass(android.view.View view){}public static void minimumAcrylicMix(android.view.View view,int percent){}
public static class Palette {public int onPrimaryContainer,muted,primaryContainer,primary;}
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
   TextView badge=new TextView(c);badge.layout(50,0,85,20);badge.setOnClickListener(v->selected.add(2));wrapper.addView(badge);dock.addItem(wrapper);}
  else{button.layout(i*100,0,(i+1)*100,60);dock.addItem(button);}}
 }
 static void init(){View.reset();selected=new ArrayList<>();rebuilds=0;
  dock=new DockNavigationLayout(c,n->{selected.add(n);dock.beginPageSettle(n);},()->{rebuilds++;dock.clearItems();populate();dock.setSelectedIndex(0);});
  dock.layout(0,0,300,60);new FrameLayout(c).addView(dock);populate();dock.setSelectedIndex(0);}
 static void touch(int a,float x,float y){dock.dispatchTouchEvent(new MotionEvent(a,x,y));}
 static View highlight(){try{var field=DockNavigationLayout.class.getDeclaredField("highlightView");field.setAccessible(true);return (View)field.get(dock);}catch(Exception e){throw new RuntimeException(e);}}
 static float center(){View h=highlight();GradientDrawable.lastLeft=h.getTranslationX();GradientDrawable.lastWidth=h.getWidth()*h.getScaleX();GradientDrawable.lastHeight=h.getHeight()*h.getScaleY();GradientDrawable.lastRight=GradientDrawable.lastLeft+GradientDrawable.lastWidth;GradientDrawable.lastCenter=(GradientDrawable.lastLeft+GradientDrawable.lastRight)/2;return GradientDrawable.lastCenter;}
 static void close(float actual,float expected,String label){require(Math.abs(actual-expected)<=1f,label+" got "+actual);}
 public static void main(String[] args)throws Exception{
  for(int i=0;i<3;i++){init();touch(0,i*100+25,35);View.advance(100);touch(1,i*100+25,35);
   require(rebuilds==0,"Do not rebuild during UP before queued child click");View.advance(0);
   require(selected.equals(List.of(i)),"Each ordinary button tap executes exactly once before detach: "+i);}
  init();touch(0,270,10);View.advance(100);touch(1,270,10);View.advance(0);require(selected.equals(List.of(2)),"Badge tap survives refresh");
  init();touch(0,130,35);View.advance(400);touch(1,130,35);View.advance(0);require(selected.equals(List.of(1)),"Tap held below long-press timeout remains a click");
  init();touch(0,25,35);touch(1,25,35);View.advance(0);touch(0,125,35);touch(1,125,35);View.advance(0);require(selected.equals(List.of(0,1)),"Consecutive ordinary taps each execute once");
  init();touch(0,130,25);touch(2,132,40);touch(1,132,40);View.advance(0);require(selected.equals(List.of(1)),"Vertical drift inside button retains click");
  init();dock.itemAt(0).layout(10,0,90,60);touch(0,95,35);touch(1,95,35);View.advance(0);require(selected.equals(List.of(0)),"Gap tap selects nearest button");
  init();dock.itemAt(0).layout(10,0,90,60);touch(0,95,35);touch(1,95,100);View.advance(0);require(selected.isEmpty(),"Gap tap released outside never selects");
  init();dock.itemAt(0).layout(10,0,90,60);touch(0,95,35);touch(2,95,100);touch(2,95,35);touch(1,95,35);View.advance(0);require(selected.isEmpty(),"Gap tap leaving and reentering does not accidentally click");
  init();touch(0,30,35);touch(2,245,35);View.advance(600);touch(1,245,35);View.advance(1800);require(selected.equals(List.of(2)),"Slow horizontal drag commits once at physical landing");
  init();touch(0,140,35);View.advance(500);touch(2,245,35);View.advance(600);touch(1,245,35);View.advance(1800);require(selected.equals(List.of(2)),"Long press then drag commits once");
  init();touch(0,30,35);touch(2,245,35);touch(3,245,35);View.advance(1000);require(selected.isEmpty(),"Cancel never selects");close(center(),50,"Cancel returns to actual selection");
  init();touch(0,30,35);touch(2,245,35);touch(5,245,35);touch(1,245,35);View.advance(0);require(selected.isEmpty(),"Multiple pointers cancel");
  init();touch(0,30,35);touch(2,245,35);View.advance(600);touch(1,245,100);View.advance(1800);require(selected.equals(List.of(2)),"Release outside dock commits physical landing");
  init();touch(0,30,35);touch(2,345,100);View.advance(600);touch(1,345,150);View.advance(1800);require(selected.equals(List.of(2)),"Release beyond horizontal edge commits endpoint");
  init();dock.showPageProgress(0,1,.5f);close(center(),50,"Body retarget preserves current visual position");View.advance(80);require(center()>50&&center()<110,"Body highlight moves with inertia");
  float old=center();dock.beginPageSettle(1);dock.updatePageSettle(0);close(center(),old,"Settle starts without jump");dock.updatePageSettle(1);dock.finishPageSelection(1);View.advance(1000);close(center(),150,"Commit eventually arrives at destination");
  dock.clearItems();populate();dock.setSelectedIndex(1);close(center(),150,"Rebuilding labels does not reset visual position");
  dock.showPageProgress(1,0,.4f);View.advance(60);dock.beginPageSettle(1);dock.finishPageSelection(1);View.advance(1000);close(center(),150,"Cancelled body swipe springs back to source");
  init();dock.beginPageSettle(2);View.advance(50);require(center()>50&&center()<250,"Click moves through intermediate position");dock.finishPageSelection(2);View.advance(1000);close(center(),250,"Click reaches distant destination");
  init();touch(0,30,35);touch(2,235,35);float released=center();touch(1,235,35);dock.updatePageSettle(0);close(center(),released,"Dock release does not jump to old source");
  dock.updatePageSettle(1);View.advance(50);require(selected.size()==1,"Stationary release commits actual position after coast");
  init();dock.beginPageSettle(1);dock.updatePageSettle(.6f);float interrupted=center();dock.beginBodyDrag();dock.showPageProgress(0,1,0);close(center(),interrupted,"New body gesture preserves interrupted highlight position");
  dock.showPageProgress(0,1,.5f);close(center(),interrupted,"Interrupted gesture retarget keeps current frame");View.advance(50);require(center()>interrupted,"Interrupted gesture moves toward target");
  init();touch(0,225,35);touch(2,245,35);close(center(),50,"Remote drag begins at existing highlight");
  View.advance(16);float first=center();require(first>50&&first<245,"First frame moves partway toward finger");
  View.advance(32);float next=center();require(next>first&&next<245,"Subsequent frames approach finger");
  touch(2,150,35);close(center(),next,"Retargeting does not teleport during MOVE");
  View.advance(80);require(center()>first&&center()<200,"Direction reversal retains bounded inertia");
  float beforeUp=center();touch(1,245,35);close(center(),beforeUp,"Release preserves visual position");
  require(selected.equals(List.of(1)),"Release starts predicted page immediately while highlight keeps moving");
  View.advance(1800);close(center(),150,"Stationary release lands at actual visual position rather than remote finger position");
  init();touch(0,30,35);View.advance(800);center();close(GradientDrawable.lastWidth,150,"Held highlight scales to 1.5");close(GradientDrawable.lastHeight,90,"Held highlight height scales to 1.5");require(Math.abs(dock.scaleX-1.1f)<.002f,"Held dock scales to 1.1");
  touch(1,30,100);View.advance(1000);center();close(GradientDrawable.lastWidth,100,"Released highlight returns to base width");require(Math.abs(dock.scaleX-1f)<.002f,"Released dock returns to base size");
  DockSpring physics=new DockSpring();physics.target=2;float maxWidth=1,maxPosition=0;
  for(int i=0;i<180;i++){physics.step(1f/120);maxWidth=Math.max(maxWidth,physics.width);maxPosition=Math.max(maxPosition,physics.position);}
  require(maxWidth>1.02f,"Moving highlight width stretches elastically");require(maxPosition>2f,"Position spring has bounded elastic overshoot");require(Math.abs(physics.position-2)<.002f&&Math.abs(physics.width-1)<.002f,"Spring settles rather than oscillating indefinitely");
  DockSpring fast=new DockSpring();fast.press(true);for(int i=0;i<12;i++)fast.step(1f/120);
  require(fast.scale<1.4f&&fast.bar<1.08f,"Press expansion is slower than previous 100ms response");for(int i=0;i<6;i++)fast.step(1f/120);
  require(fast.scale>1.4f&&fast.bar>1.08f,"Press reaches previous response at 1.5 times elapsed time");fast.press(false);for(int i=0;i<18;i++)fast.step(1f/120);
  require(fast.scale<1.04f&&fast.bar<1.01f,"Release contracts rapidly");center();require((GradientDrawable.lastColor>>>24)==0x52,"Native highlight has 32 percent alpha for background blending");
  init();android.view.VelocityTracker.velocity=2500;touch(0,25,35);touch(2,150,35);touch(1,150,100);
  var springField=DockNavigationLayout.class.getDeclaredField("spring");springField.setAccessible(true);DockSpring transferred=(DockSpring)springField.get(dock);
  require(Math.abs(transferred.velocity-25f)<.001f,"Release transfers measured physical finger velocity at 1:1 scale");require(selected.size()==1&&transferred.coasting,"Predicted page begins during free coast");
  View.advance(2500);require(selected.size()==1,"Coast completion never switches the page a second time");
  init();android.view.VelocityTracker.velocity=-1800;touch(0,240,35);touch(2,140,35);touch(1,140,100);
  require(Math.abs(((DockSpring)springField.get(dock)).velocity+18f)<.001f,"Reverse release keeps measured velocity sign");android.view.VelocityTracker.velocity=0;
  DockSpring forward=new DockSpring();forward.position=1;forward.coast(4,4);
  DockSpring reverse=new DockSpring();reverse.position=1;reverse.coast(-4,4);
  for(int i=0;i<400;i++){forward.step(1f/120);reverse.step(1f/120);}
  require(forward.target==2&&reverse.target==0,"Identical release position selects different final items from velocity");
  DockSpring thrown=new DockSpring();thrown.position=2;thrown.coast(12,2);float farthest=2;
  for(int i=0;i<500;i++){thrown.step(1f/120);farthest=Math.max(farthest,thrown.position);}
  require(farthest>2.1f&&!thrown.coasting&&thrown.position<=2.01f,"Edge allows throw outside then damping pulls it back");
  init();DockSpring edges=(DockSpring)springField.get(dock);var apply=DockNavigationLayout.class.getDeclaredMethod("applySpring");apply.setAccessible(true);
  edges.position=3.5f;apply.invoke(dock);center();close(GradientDrawable.lastLeft,250,"Right throw pins inner end half a standard width inside");
  require(GradientDrawable.lastRight>350,"Right outer end can stretch beyond half width");
  edges.position=-1.5f;apply.invoke(dock);center();close(GradientDrawable.lastRight,50,"Left throw pins inner end half a standard width inside");
  require(GradientDrawable.lastLeft<-50,"Left outer end can stretch beyond half width");
  require(GradientDrawable.lastLeft>=-75,"Outer end cannot exceed configured standard-width limit");
  float a=DockSpring.outwardExtension(20,75),b=DockSpring.outwardExtension(40,75),c=DockSpring.outwardExtension(60,75);
  require(a<b&&b<c&&(c-b)<(b-a),"Resistance progressively increases toward the outward limit");
  require(DockSpring.outwardExtension(10000,75)<=75,"Extreme fling remains bounded");
  DockSpring prediction=new DockSpring();prediction.position=1;prediction.velocity=3;
  require(prediction.predictLanding(4,4)==2&&prediction.predictLanding(-4,4)==0,"Prediction includes velocity-dependent landing");
  require(prediction.position==1&&prediction.velocity==3,"Prediction does not mutate displayed state or velocity");
  DockSpring resisted=new DockSpring();resisted.coast(1,10);for(int i=0;i<120;i++)resisted.step(1f/120);
  require(resisted.position<.14f,"Increased ordinary resistance shortens coast distance");
  init();dock.finishPageSelection(2);int redraws=dock.invalidations,layouts=highlight().layouts;View.advance(400);
  require(dock.invalidations==redraws,"Motion does not invalidate entire glass dock each frame");
  require(highlight().layouts==layouts,"Motion uses render properties without repeated layout");
  init();dock=new DockNavigationLayout(DockNavigationCheck.c,n->selected.add(n),()->{});new FrameLayout(DockNavigationCheck.c).addView(dock);populate();dock.setOrientation(LinearLayout.VERTICAL);dock.layout(0,0,60,300);
  for(int i=0;i<3;i++)dock.itemAt(i).layout(0,i*100,60,(i+1)*100);dock.setSelectedIndex(0);
  touch(0,35,25);touch(2,35,150);View.advance(600);touch(1,35,150);View.advance(1800);
  require(selected.equals(List.of(1)),"Shared vertical dock commits a vertical drag exactly once");
  close(highlight().getTranslationY()+highlight().getHeight()*highlight().getScaleY()/2,150,"Vertical highlight settles on active row");
  init();dock=new DockNavigationLayout(DockNavigationCheck.c,n->selected.add(n),()->{});new FrameLayout(DockNavigationCheck.c).addView(dock);populate();dock.setOrientation(LinearLayout.VERTICAL);dock.layout(0,0,60,300);
  for(int i=0;i<3;i++)dock.itemAt(i).layout(0,i*100,60,(i+1)*100);dock.setSelectedIndex(0);
  android.view.VelocityTracker.velocity=900;touch(0,35,25);touch(2,35,140);touch(1,100,140);
  require(Math.abs(((DockSpring)springField.get(dock)).velocity-9)<.001f,"Vertical release transfers physical Y velocity");
  require(selected.size()==1,"Vertical outside release starts predicted page immediately");android.view.VelocityTracker.velocity=0;
  init();touch(0,245,35);View.advance(500);close(center(),50,"Long press starts at existing highlight");
  View.advance(32);require(center()>50&&center()<245,"Long press smoothly approaches finger");
  touch(3,245,35);View.advance(400);close(center(),50,"Cancel stops finger loop and returns");
  init();touch(0,225,35);touch(2,245,35);View.advance(32);touch(5,245,35);View.advance(400);close(center(),50,"Multiple pointers stop follow");
  init();touch(0,225,35);touch(2,245,35);View.advance(32);float detached=center();dock.onDetachedFromWindow();View.advance(400);close(center(),detached,"Detach cancels scheduled follow");
  init();touch(0,225,35);touch(2,245,35);View.advance(32);float bodyStart=center();dock.beginBodyDrag();View.advance(400);close(center(),bodyStart,"Body gesture cancels scheduled follow");
  init();android.animation.ValueAnimator.enabled=false;touch(0,225,35);touch(2,245,35);close(center(),245,"Disabled animations follow immediately");android.animation.ValueAnimator.enabled=true;
  init();touch(0,225,35);touch(2,245,35);View.advance(16);android.animation.ValueAnimator.enabled=false;
  View.advance(16);close(center(),245,"Disabling during follow settles on next frame");android.animation.ValueAnimator.enabled=true;
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
files += [str(ROOT / "app/src/main/java/com/donglan/chrona" / name) for name in ("DockNavigationLayout.java", "DockSpring.java")]
subprocess.run([str(java_home / "bin/javac.exe"), "-encoding", "UTF-8", "-d", str(OUT / "classes"), *files], check=True)
subprocess.run([str(java_home / "bin/java.exe"), "-cp", str(OUT / "classes"), "com.donglan.chrona.DockNavigationCheck"], check=True)
