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
    "android/view/Gravity.java": "package android.view;public class Gravity {public static final int CENTER=1,TOP=2,END=4;}",
    "android/content/Context.java": """package android.content;
public class Context { public android.content.res.Resources getResources(){return new android.content.res.Resources();} }""",
    "android/content/res/Resources.java": """package android.content.res;
public class Resources { public android.util.DisplayMetrics getDisplayMetrics(){return new android.util.DisplayMetrics();} }""",
    "android/util/DisplayMetrics.java": "package android.util; public class DisplayMetrics { public float density=1,scaledDensity=1; }",
    "android/util/TypedValue.java": "package android.util;public class TypedValue {public static final int COMPLEX_UNIT_SP=2;public static float deriveDimension(int unit,float value,DisplayMetrics metrics){return value/metrics.scaledDensity;}}",
    "android/os/Build.java": "package android.os;public class Build {public static class VERSION {public static int SDK_INT=36;}}",
    "android/content/res/ColorStateList.java": "package android.content.res; public class ColorStateList { public static ColorStateList valueOf(int x){return new ColorStateList();} }",
    "android/graphics/Canvas.java": "package android.graphics; public class Canvas {}",
    "android/graphics/drawable/GradientDrawable.java": """package android.graphics.drawable;
public class GradientDrawable { public static float lastCenter,lastWidth,lastHeight,lastLeft,lastRight;public static int lastColor; int l,r,t,b;
public static float[] radii;public void setCornerRadius(float x){}public void setCornerRadii(float[] x){radii=x.clone();} public void setColor(int c){lastColor=c;}
public void setBounds(int l,int t,int r,int b){this.l=l;this.r=r;this.t=t;this.b=b;}
public void draw(android.graphics.Canvas c){lastCenter=(l+r)/2f;lastWidth=r-l;lastHeight=b-t;lastLeft=l;lastRight=r;} }""",
    "android/os/SystemClock.java": "package android.os; public class SystemClock { public static long uptimeMillis(){return android.view.View.now;} }",
    "android/view/HapticFeedbackConstants.java": "package android.view; public class HapticFeedbackConstants { public static final int LONG_PRESS=0; }",
    "android/view/ViewConfiguration.java": """package android.view; public class ViewConfiguration {
public static ViewConfiguration get(android.content.Context c){return new ViewConfiguration();}
public int getScaledTouchSlop(){return 8;} public static int getLongPressTimeout(){return 500;} }""",
    "android/view/MotionEvent.java": """package android.view; public class MotionEvent {
public static final int ACTION_DOWN=0,ACTION_UP=1,ACTION_MOVE=2,ACTION_CANCEL=3,ACTION_POINTER_DOWN=5;
 int a;float x,y,rawX,rawY; public MotionEvent(int a,float x,float y){this.a=a;this.x=x;this.y=y;rawX=x;rawY=y;}
public static MotionEvent obtain(long d,long t,int a,float x,float y,int m){return new MotionEvent(a,x,y);}
 public static MotionEvent obtain(MotionEvent e){MotionEvent copy=new MotionEvent(e.a,e.x,e.y);copy.rawX=e.rawX;copy.rawY=e.rawY;return copy;}public void offsetLocation(float dx,float dy){x+=dx;y+=dy;}public void setAction(int action){a=action;}
 public int getActionMasked(){return a;} public float getX(){return x;} public float getY(){return y;} public float getRawX(){return rawX;}public float getRawY(){return rawY;}public long getDownTime(){return 0;}public long getEventTime(){return View.now;}public void recycle(){} }""",
    "com/donglan/chrona/CandidatePagerScrollView.java": (ROOT / "app/src/main/java/com/donglan/chrona/CandidatePagerScrollView.java").read_text(encoding="utf-8"),
    "android/view/animation/Interpolator.java": "package android.view.animation;public interface Interpolator {}",
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
 public static final int VISIBLE=0,INVISIBLE=4,GONE=8;private Object tag;private int visibility=VISIBLE;public void setTag(Object value){tag=value;}public Object getTag(){return tag;}public void setVisibility(int value){visibility=value;}public int getVisibility(){return visibility;}
  private boolean pressed,enabled=true; private final Runnable perform=()->{if(pressed){pressed=false;performClick();}};
  public boolean isEnabled(){return enabled;}public void setEnabled(boolean value){enabled=value;}
 protected Context context; protected View parent; public int l,t,r,b;
 public View(Context c){context=c;} public Context getContext(){return context;}
 public android.content.res.Resources getResources(){return context.getResources();}
 public View getParent(){return parent;} public void requestDisallowInterceptTouchEvent(boolean x){if(parent!=null)parent.requestDisallowInterceptTouchEvent(x);}
  public int getScrollX(){return 0;}public int getScrollY(){return 0;}
  public boolean isShown(){return true;}public boolean isAttachedToWindow(){return parent!=null;}public void getLocationOnScreen(int[] xy){xy[0]=l;xy[1]=t;for(View p=parent;p!=null;p=p.parent){xy[0]+=p.l-p.getScrollX();xy[1]+=p.t-p.getScrollY();}}
 public int getLeft(){return l;} public int getRight(){return r;} public int getTop(){return t;} public int getBottom(){return b;}
 public int getWidth(){return r-l;} public int getHeight(){return b-t;}
 public static class MeasureSpec {public static final int EXACTLY=1073741824,AT_MOST=-2147483648,UNSPECIFIED=0;public static int getSize(int spec){return spec&0x3fffffff;}public static int getMode(int spec){return spec&0xc0000000;}public static int makeMeasureSpec(int size,int mode){return size|mode;}}
 private int measuredWidth,measuredHeight;public final void measure(int w,int h){onMeasure(w,h);}protected void onMeasure(int w,int h){setMeasuredDimension(MeasureSpec.getSize(w),MeasureSpec.getSize(h));}protected final void setMeasuredDimension(int w,int h){measuredWidth=w;measuredHeight=h;}public int getMeasuredWidth(){return measuredWidth;}public int getMeasuredHeight(){return measuredHeight;}
 public int layouts,invalidations;public void layout(int l,int t,int r,int b){this.l=l;this.t=t;this.r=r;this.b=b;layouts++;if(this instanceof ViewGroup group)group.onLayout(true,l,t,r,b);}
 public static final int IMPORTANT_FOR_ACCESSIBILITY_NO=2;public void setImportantForAccessibility(int value){}
 public ViewGroup.LayoutParams layoutParams=new ViewGroup.LayoutParams(1,1);public ViewGroup.LayoutParams getLayoutParams(){return layoutParams;}public void setLayoutParams(ViewGroup.LayoutParams value){layoutParams=value;}
 public void setBackground(Object background){}public void setPivotX(float x){}public void setPivotY(float y){}
 public int pl,pt,pr,pb;public void setPadding(int a,int b,int c,int d){pl=a;pt=b;pr=c;pb=d;}public int getPaddingLeft(){return pl;}public int getPaddingTop(){return pt;}public int getPaddingRight(){return pr;}public int getPaddingBottom(){return pb;}
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
 public boolean disallowed,clipChildren=true,clipPadding=true;
 public ViewGroup(android.content.Context c){super(c);}
 public static class LayoutParams {public int width,height,leftMargin,rightMargin;public LayoutParams(int w,int h){width=w;height=h;}public LayoutParams(int w,int h,int gravity){this(w,h);}public void setMargins(int a,int b,int c,int d){leftMargin=a;rightMargin=c;}}
 public void addView(View view,LayoutParams params){view.setLayoutParams(params);addView(view);}
 protected void onLayout(boolean changed,int l,int t,int r,int b){}
 public void setClipChildren(boolean value){clipChildren=value;}public void setClipToPadding(boolean value){clipPadding=value;}
 @Override public void requestDisallowInterceptTouchEvent(boolean value){disallowed=value;super.requestDisallowInterceptTouchEvent(value);}
 public boolean onInterceptTouchEvent(MotionEvent e){return false;}
 public void addView(View v){children.add(v);v.parent=this;} public int getChildCount(){return children.size();}
 public View getChildAt(int i){return i<0||i>=children.size()?null:children.get(i);}
 public void removeAllViews(){for(View v:new ArrayList<>(children))v.detach();children.clear();target=null;}
 @Override public void detach(){removeAllViews();super.detach();}
  private MotionEvent local(MotionEvent e,View v){MotionEvent child=MotionEvent.obtain(e);child.offsetLocation(-v.l,-v.t);return child;}
 @Override public boolean dispatchTouchEvent(MotionEvent e){
  if(e.a==MotionEvent.ACTION_DOWN)disallowed=false;
  boolean intercepted=!disallowed&&onInterceptTouchEvent(e);
   if(intercepted){if(target!=null){View child=target;target=null;MotionEvent cancel=MotionEvent.obtain(e);cancel.setAction(MotionEvent.ACTION_CANCEL);child.dispatchTouchEvent(local(cancel,child));}return onTouchEvent(e);}
  if(e.a==MotionEvent.ACTION_DOWN){target=null;for(int i=children.size()-1;i>=0;i--){View v=children.get(i);
   if(e.x>=v.l&&e.x<v.r&&e.y>=v.t&&e.y<v.b&&v.dispatchTouchEvent(local(e,v))){target=v;return true;}}}
  else if(target!=null){View v=target;boolean handled=v.dispatchTouchEvent(local(e,v));if(e.a==1||e.a==3)target=null;return handled;}
  return onTouchEvent(e);
 }
} """,
    "android/widget/LinearLayout.java": "package android.widget; public class LinearLayout extends android.view.ViewGroup {public static class LayoutParams extends android.view.ViewGroup.LayoutParams {public float weight;public LayoutParams(int w,int h,float value){super(w,h);weight=value;}}public static final int HORIZONTAL=0,VERTICAL=1; public void setOrientation(int value){} public void setBaselineAligned(boolean value){} public LinearLayout(android.content.Context c){super(c);} }",
    "android/widget/FrameLayout.java": "package android.widget; public class FrameLayout extends android.view.ViewGroup { public FrameLayout(android.content.Context c){super(c);} protected void onLayout(boolean changed,int l,int t,int r,int b){for(android.view.View child:children){var p=child.getLayoutParams();int w=p.width<0?getWidth()-pl-pr:p.width,h=p.height<0?getHeight()-pt-pb:p.height;child.layout(pl,pt,pl+w,pt+h);}} }",
    "android/widget/HorizontalScrollView.java": """package android.widget; public class HorizontalScrollView extends FrameLayout {
 public static final int OVER_SCROLL_NEVER=2;private int scrollX;
 public boolean canScrollHorizontally(int direction){return getChildCount()>0&&(direction<0?scrollX>0:scrollX<getChildAt(0).getWidth()-getWidth());}
 public HorizontalScrollView(android.content.Context c){super(c);}public void setHorizontalScrollBarEnabled(boolean value){}public void setFillViewport(boolean value){}public void setOverScrollMode(int value){}
 @Override public int getScrollX(){return scrollX;}public void scrollTo(int x,int y){scrollX=x;}
 @Override public boolean onTouchEvent(android.view.MotionEvent event){return true;}
 }""",
    "android/widget/TextView.java": """package android.widget; public class TextView extends android.view.View {
public TextView(android.content.Context c){super(c);}private int color; public void setTextColor(int c){color=c;}public int getCurrentTextColor(){return color;}
public float textSize=14;public boolean singleLine,fontPadding=true,horizontalScrolling;public int autoMin,autoMax,autoStep,autoUnit;
public void setTextSize(float value){textSize=value;}public float getTextSize(){return textSize;}
public void setSingleLine(boolean value){singleLine=value;horizontalScrolling=value;}public void setHorizontallyScrolling(boolean value){horizontalScrolling=value;}
public void setGravity(int g){}public void setIncludeFontPadding(boolean b){fontPadding=b;}
public void setAutoSizeTextTypeUniformWithConfiguration(int min,int max,int step,int unit){autoMin=min;autoMax=max;autoStep=step;autoUnit=unit;}
public void setCompoundDrawableTintList(android.content.res.ColorStateList c){} }""",
    "android/view/animation/DecelerateInterpolator.java": "package android.view.animation; public class DecelerateInterpolator implements Interpolator {public DecelerateInterpolator(float x){} }",
    "android/animation/Animator.java": "package android.animation;public class Animator {}",
    "android/animation/AnimatorListenerAdapter.java": "package android.animation;public class AnimatorListenerAdapter {public void onAnimationEnd(Animator animator){}}",
    "android/animation/ValueAnimator.java": """package android.animation;
 public class ValueAnimator extends Animator { public interface AnimatorUpdateListener {void onAnimationUpdate(ValueAnimator a);}
  float from,to;boolean integer; AnimatorUpdateListener listener;AnimatorListenerAdapter end; public static boolean enabled=true;
 public static boolean areAnimatorsEnabled(){return enabled;}
 public static ValueAnimator ofFloat(float a,float b){ValueAnimator v=new ValueAnimator();v.from=a;v.to=b;return v;}
  public static ValueAnimator ofInt(int a,int b){ValueAnimator v=ofFloat(a,b);v.integer=true;return v;}
 public void setDuration(long d){} public void setInterpolator(Object i){} public void cancel(){}
  public void addUpdateListener(AnimatorUpdateListener l){listener=l;} public Object getAnimatedValue(){if(integer)return Integer.valueOf((int)to);return Float.valueOf(to);}
  public void addListener(AnimatorListenerAdapter listener){end=listener;}
  public void start(){if(listener!=null)listener.onAnimationUpdate(this);if(end!=null)end.onAnimationEnd(this);} }""",
    "com/donglan/chrona/UiStyle.java": """package com.donglan.chrona; public class UiStyle {
public static final int RADIUS_DOCK=28;
public static void dockGlass(android.view.View view){}public static void minimumAcrylicMix(android.view.View view,int percent){}
public static class Palette {public int onPrimaryContainer,muted=0xff668080,primaryContainer,primary=0xff00b8a6;}
public static Palette colors(android.content.Context c){return new Palette();} }""",
}

RUNNER = """package com.donglan.chrona;
import android.content.Context; import android.view.*; import android.widget.*;
import android.graphics.Canvas; import android.graphics.drawable.GradientDrawable;
import java.util.*;
public class DockNavigationCheck extends Context {
 final List<View> strips=new ArrayList<>();
 int dp(int value){return value;}TextView text(String title,int size,boolean bold){return new TextView(this);}TextView countBadge(int count){return new TextView(this);}
 __FILTER_DOCK__
 __OVERFLOW__
 static int checks; static void require(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static Context c=new Context(); static DockNavigationLayout dock; static List<Integer> selected; static int rebuilds;
 static void populate(){for(int i=0;i<3;i++){final int n=i;TextView button=new TextView(c);button.setOnClickListener(v->selected.add(n));
  if(i==2){FrameLayout wrapper=new FrameLayout(c);wrapper.layout(200,0,300,60);button.layout(0,0,100,60);wrapper.addView(button);
   TextView badge=new TextView(c);badge.layout(50,0,85,20);badge.setOnClickListener(v->selected.add(2));wrapper.addView(badge);dock.addItem(wrapper);}
  else{button.layout(i*100,0,(i+1)*100,60);dock.addItem(button);}}
 }
 static void init(){View.reset();selected=new ArrayList<>();rebuilds=0;
  dock=new DockNavigationLayout(c,n->{selected.add(n);dock.beginPageSettle(n);},()->{rebuilds++;dock.clearItems();populate();dock.setSelectedIndex(0);});
  dock.setHighlightMovementSpeed(1f);
  dock.layout(0,0,300,60);new FrameLayout(c).addView(dock);populate();dock.setSelectedIndex(0);}
 static void touch(int a,float x,float y){dock.dispatchTouchEvent(new MotionEvent(a,x,y));}
 static View highlight(){try{var field=DockNavigationLayout.class.getDeclaredField("highlightView");field.setAccessible(true);return (View)field.get(dock);}catch(Exception e){throw new RuntimeException(e);}}
 static float center(){View h=highlight();GradientDrawable.lastLeft=h.getLeft()+h.getTranslationX();GradientDrawable.lastWidth=h.getWidth()*h.getScaleX();GradientDrawable.lastHeight=h.getHeight()*h.getScaleY();GradientDrawable.lastRight=GradientDrawable.lastLeft+GradientDrawable.lastWidth;GradientDrawable.lastCenter=(GradientDrawable.lastLeft+GradientDrawable.lastRight)/2;return GradientDrawable.lastCenter;}
 static void close(float actual,float expected,String label){require(Math.abs(actual-expected)<=1f,label+" got "+actual);}
  static final class NestedPager {
   final int[] starts={0};final SwipePagerLayout outer;final CandidatePagerScrollView candidates;
   NestedPager(boolean register){
    init();outer=new SwipePagerLayout(c,new SwipePagerLayout.Listener(){
     public void onStart(){starts[0]++;}public void onDrag(float x){}public void onRelease(float x,float v){}public void onCancel(){}
    });
    outer.layout(0,0,420,600);candidates=new CandidatePagerScrollView(c);candidates.layout(30,100,390,360);candidates.setPages(360,3);
    FrameLayout pages=new FrameLayout(c);pages.layout(0,0,1080,260);candidates.addView(pages);outer.addView(candidates);
    dock.layout(50,20,350,80);pages.addView(dock);outer.setGesturePriorityChildren(List.of(candidates,dock));
    if(register)candidates.addGesturePriorityChild(dock);
   }
   void touch(int action,float x,float y){outer.dispatchTouchEvent(new MotionEvent(action,x,y));}
  }
 public static void main(String[] args)throws Exception{
  View.reset();DockNavigationLayout labels=new DockNavigationLayout(c,n->{},()->{});TextView direct=new TextView(c);direct.setTextSize(18);
  labels.addItem(direct);require(direct.singleLine&&!direct.fontPadding&&!direct.horizontalScrolling,"Direct Dock labels fit one visible line without scrolling");
  require(direct.autoMin==8&&direct.autoMax==18&&direct.autoStep==1&&direct.autoUnit==android.util.TypedValue.COMPLEX_UNIT_SP,"Autosize retains caller's sp ceiling with 8sp minimum and 1sp steps");
  TextView weighted=new TextView(c);weighted.setTextSize(14);labels.addItem(weighted,new LinearLayout.LayoutParams(0,42,1));
  require(weighted.autoMin==8&&weighted.autoMax==14&&weighted.singleLine,"Weighted addItem follows the same label fitting path");
  FrameLayout wrapped=new FrameLayout(c);TextView nestedLabel=new TextView(c);wrapped.addView(nestedLabel);TextView countLabel=new TextView(c);wrapped.addView(countLabel);labels.addItem(wrapped);
  require(nestedLabel.autoMin==8&&nestedLabel.autoMax==14&&nestedLabel.singleLine&&!nestedLabel.fontPadding&&!nestedLabel.horizontalScrolling&&countLabel.autoMax==0,
    "Badge wrapper fits its first label while retaining the count badge typography");
  View.reset();DockNavigationCheck builder=new DockNavigationCheck();List<Integer> filters=new ArrayList<>();DockNavigationLayout five=builder.filterDock(new String[]{"全部","待确认","处理中","失败","已写入"},0,filters::add,new int[]{0,3,0,2,0});
  require(builder.strips.contains(five),"Filter dock is registered in page gesture priorities");
  LinearLayout content=new LinearLayout(c);FrameLayout host=new FrameLayout(c),viewport=new FrameLayout(c);
  builder.allowSectionDockOverflow(content,host);
  require(!content.clipChildren&&!content.clipPadding&&!host.clipChildren&&!host.clipPadding,"Intermediate content parents permit Dock overflow");
  require(viewport.clipChildren&&viewport.clipPadding,"Actual page viewport retains clipping");
  init();int[] pageStarts={0};SwipePagerLayout pager=new SwipePagerLayout(c,new SwipePagerLayout.Listener(){
   public void onStart(){pageStarts[0]++;}public void onDrag(float x){}public void onRelease(float x,float v){}public void onCancel(){}
  });
  pager.layout(0,0,400,600);FrameLayout scroll=new FrameLayout(c){@Override public boolean onTouchEvent(MotionEvent e){return true;}};
  scroll.layout(0,0,400,600);pager.addView(scroll);FrameLayout row=new FrameLayout(c);row.layout(30,100,350,160);scroll.addView(row);
  dock.layout(0,0,300,60);row.addView(dock);pager.setGesturePriorityChildren(List.of(dock));
  pager.dispatchTouchEvent(new MotionEvent(0,90,125));pager.dispatchTouchEvent(new MotionEvent(2,270,125));
  require(pageStarts[0]==0&&pager.disallowed,"Registered Dock retains ancestor gesture ownership");
  View.advance(600);pager.dispatchTouchEvent(new MotionEvent(1,270,190));View.advance(1800);
  require(selected.size()==1,"Nested Dock release outside row commits once without switching page");
  pager.dispatchTouchEvent(new MotionEvent(0,90,230));pager.dispatchTouchEvent(new MotionEvent(2,250,230));pager.dispatchTouchEvent(new MotionEvent(1,250,230));
  require(pageStarts[0]==1,"Horizontal gesture outside Dock still switches page");
  int before=selected.size();pager.setGesturePriorityChildren(List.of());
  pager.dispatchTouchEvent(new MotionEvent(0,90,125));pager.dispatchTouchEvent(new MotionEvent(2,270,125));pager.dispatchTouchEvent(new MotionEvent(1,270,125));View.advance(1800);
  require(pageStarts[0]==2&&selected.size()==before,"Missing registration reproduces ancestor stealing and cancels Dock selection");
  NestedPager nested=new NestedPager(true);
  require(nested.candidates.ownsPriorityGestureAt(110,145),"Candidate pager recognizes its explicitly registered inner selection bar");
  nested.touch(0,110,145);nested.touch(2,320,145);View.advance(600);nested.touch(1,320,145);View.advance(1800);
  require(selected.equals(List.of(2))&&nested.starts[0]==0&&nested.candidates.getScrollX()==0,
    "Selection-bar drag at the candidate boundary selects once without candidate paging or task handoff");
  nested.touch(0,280,250);nested.touch(2,100,250);nested.touch(2,50,250);nested.touch(1,50,250);View.advance(1800);
  require(nested.starts[0]==0&&nested.candidates.getScrollX()==360,
    "A drag outside the selection bar still advances the candidate pager");
  nested.candidates.scrollTo(0,0);nested.touch(0,100,250);nested.touch(2,250,250);nested.touch(1,250,250);
  require(nested.starts[0]==1&&nested.candidates.getScrollX()==0,
    "Outside-bar outward drag retains the original candidate-boundary task handoff");
  NestedPager missingNested=new NestedPager(false);
  missingNested.touch(0,110,145);missingNested.touch(2,320,145);missingNested.touch(1,320,145);View.advance(1800);
  require(missingNested.starts[0]==1&&selected.isEmpty(),
    "Missing inner registration reproduces candidate-boundary theft and cancels the selection bar");
  init();dock.setEnabled(false);
  require(!dock.dispatchTouchEvent(new MotionEvent(0,30,35))&&!dock.dispatchTouchEvent(new MotionEvent(2,245,35))
    &&!dock.dispatchTouchEvent(new MotionEvent(1,245,35)),"Disabled selection bar rejects down, move and release");
  View.advance(1000);require(!dock.isTrackingTouch()&&selected.isEmpty()&&rebuilds==0&&Math.abs(dock.scaleX-1f)<.002f,
    "Disabled selection bar cannot track, select, finish, or enlarge");
  init();touch(0,30,35);touch(2,245,35);View.advance(32);dock.setEnabled(false);View.advance(1000);
  require(!dock.isTrackingTouch()&&selected.isEmpty()&&rebuilds==0,"Disabling during a drag cancels its pending selection and finish");
  close(center(),50,"Disabling during a drag returns the highlight to the original selection");
  require(!dock.dispatchTouchEvent(new MotionEvent(1,245,35)),"Disabled drag release stays inert");
  dock.setEnabled(true);touch(0,130,35);touch(1,130,35);View.advance(0);
  require(selected.equals(List.of(1)),"Re-enabled selection bar accepts a fresh tap exactly once");
  require(five.itemCount()==5,"Inbox selection bar uses the shared five-item dock");
  require(five.itemAt(1) instanceof FrameLayout&&((FrameLayout)five.itemAt(1)).getChildCount()==2,"Review badge stays attached to its dock item");
  ((FrameLayout)five.itemAt(1)).getChildAt(0).performClick();require(filters.equals(List.of(1)),"Counted filter invokes original selection once");
  FrameLayout finalFilter=(FrameLayout)five.itemAt(4);finalFilter.getChildAt(0).performClick();
  require(filters.equals(List.of(1,4))&&finalFilter.getChildAt(1).getVisibility()==View.GONE&&"filter_count_badge".equals(finalFilter.getChildAt(1).getTag()),
    "Zero-count final inbox filter retains its selectable label and hidden tagged badge");
  DockNavigationLayout four=new DockNavigationCheck().filterDock(new String[]{"未来","未定","以前","全部"},0,filters::add,null);
  require(four.itemCount()==4,"Schedule presets use the same dock framework");four.itemAt(2).performClick();require(filters.get(filters.size()-1)==2,"Schedule preset selection is retained");
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
  init();touch(0,225,35);touch(2,245,35);close(center(),50,"Drag retarget preserves the input frame's visual position");
  View.advance(16);float firstFollow=center();require(firstFollow>50&&firstFollow<245,"First follow frame advances continuously toward finger");
  View.advance(32);require(center()>firstFollow&&center()<245,"Held position follows through intermediate frames");
  View.advance(600);close(center(),245,"Stationary held position converges to finger");
  float beforeReverse=center();touch(2,150,35);close(center(),beforeReverse,"Direction reversal retargets without jumping");
  View.advance(32);require(center()<beforeReverse&&center()>150,"Reversal follows through intermediate frames");
  View.advance(600);close(center(),150,"Reversed stationary position converges to finger");
  float beforeUp=center();touch(1,245,35);close(center(),beforeUp,"Release preserves visual position");
  require(selected.equals(List.of(1)),"Release starts predicted page immediately while highlight keeps moving");
  View.advance(1800);close(center(),150,"Stationary release lands at actual visual position rather than remote finger position");
  init();touch(0,30,35);View.advance(800);center();close(GradientDrawable.lastWidth,150,"Held highlight scales to 1.5");close(GradientDrawable.lastHeight,90,"Held highlight height scales to 1.5");require(Math.abs(dock.scaleX-1.1f)<.002f,"Held dock scales to 1.1");
  touch(1,30,100);View.advance(1000);center();close(GradientDrawable.lastWidth,100,"Released highlight returns to base width");require(Math.abs(dock.scaleX-1f)<.002f,"Released dock returns to base size");
  init();FrameLayout embeddedHost=dock.motionHost();dock.finishPageSelection(1);View.advance(1000);touch(0,150,35);View.advance(800);center();
  close(GradientDrawable.lastWidth,150,"Embedded held highlight retains full 1.5 width");close(GradientDrawable.lastHeight,90,"Embedded held highlight retains full 1.5 height");
  require(!embeddedHost.clipChildren&&!embeddedHost.clipPadding,"Embedded motion host preserves highlight overflow");
  init();dock.fullWidthMotionHost();touch(0,50,30);View.advance(900);center();
  close(GradientDrawable.lastWidth,150,"Full-width first highlight retains held growth");
  require(GradientDrawable.lastLeft<0f,"First held highlight may naturally overflow the full-width rail");
  close(dock.scaleX,1f,"Full-width rail retains full content width without side envelope");
  touch(3,50,30);View.advance(1000);dock.finishPageSelection(2);View.advance(1000);touch(0,250,30);View.advance(900);center();
  close(GradientDrawable.lastWidth,150,"Full-width last highlight retains held growth");
  require(GradientDrawable.lastRight>300f,"Last held highlight may naturally overflow without inward relocation");
  touch(3,250,30);View.advance(1000);
  DockSpring physics=new DockSpring();physics.target=2;float maxLag=0,maxPosition=0;
  for(int i=0;i<240;i++){physics.step(1f/120);maxLag=Math.max(maxLag,physics.position-physics.tailPosition);maxPosition=Math.max(maxPosition,physics.position);}
  require(maxLag>.1f,"Leading end moves before trailing end, creating directional stretch");require(maxPosition<=2.002f,"Click converges without oscillating across its target");require(Math.abs(physics.position-2)<.002f&&Math.abs(physics.tailPosition-2)<.002f,"Both ends settle rather than leaving a long ribbon");
  DockSpring fast=new DockSpring();fast.press(true);for(int i=0;i<12;i++)fast.step(1f/120);
  DockSpring tap=new DockSpring();tap.press(true);tap.step(.008f);tap.releaseWithPulse();float peak=1;
  for(int i=0;i<180;i++){tap.step(1f/120);peak=Math.max(peak,tap.scale);}
  require(peak>1.1f&&peak<1.3f,"Quick tap has a small visible pulse below held scale");
  require(Math.abs(tap.scale-1)<.001f,"Tap pulse shrinks back without a held finger");
  require(fast.scale>1.4f&&fast.scale<1.53f&&fast.bar>1.08f,"Reference press visibly swells within about 100ms without excessive overshoot");for(int i=0;i<24;i++)fast.step(1f/120);
  require(fast.scale>1.49f&&fast.bar>1.09f,"Held press settles at full size");fast.press(false);float releasedScale=fast.scale;for(int i=0;i<8;i++)fast.step(1f/120);
  require(fast.scale>1.1f&&fast.scale<releasedScale,"Release contracts through intermediate frames");for(int i=0;i<180;i++)fast.step(1f/120);
  require(fast.scale<1.002f&&fast.bar<1.001f,"Slower release ultimately returns to rest");center();require((GradientDrawable.lastColor>>>24)==0x52,"Native highlight has 32 percent alpha for background blending");
  DockSpring pressTiming=new DockSpring(),releaseTiming=new DockSpring();pressTiming.press(true);releaseTiming.scale=1.5f;releaseTiming.bar=1.1f;releaseTiming.press(false);
  for(int i=0;i<300;i++){pressTiming.step(.001f);releaseTiming.step(.0015f);}
  require(Math.abs((pressTiming.scale-1f)-(1.5f-releaseTiming.scale))<.005f,
    "Release matches press response at 1.5 times elapsed time");
  DockSpring tracking=new DockSpring();tracking.follow(2f,0f);require(tracking.position==0f,"Follow retarget never teleports position");
  for(int i=0;i<12;i++)tracking.step(1f/120);
  require(tracking.trackingHeight<.99f&&tracking.trackingHeight>.95f,"Moving follow smoothly approaches 0.95 height");
  for(int i=0;i<180;i++)tracking.step(1f/120);
  require(Math.abs(tracking.position-2f)<.002f&&Math.abs(tracking.trackingHeight-1f)<.002f,"Stopped follow restores full height");
  init();android.view.VelocityTracker.velocity=2500;touch(0,25,35);touch(2,150,35);touch(1,150,100);
  var springField=DockNavigationLayout.class.getDeclaredField("spring");springField.setAccessible(true);DockSpring transferred=(DockSpring)springField.get(dock);
  require(Math.abs(transferred.velocity-25f)<.001f,"Fast release inherits the measured finger speed without a click-speed cap");require(selected.size()==1&&transferred.coasting,"Predicted page begins during free coast");
  View.advance(2500);require(selected.size()==1,"Coast completion never switches the page a second time");
  init();android.view.VelocityTracker.velocity=-1800;touch(0,240,35);touch(2,140,35);touch(1,140,100);
  require(Math.abs(((DockSpring)springField.get(dock)).velocity+18f)<.001f,"Reverse release inherits measured speed and direction");android.view.VelocityTracker.velocity=0;
  DockSpring forward=new DockSpring();forward.position=1;forward.coast(4,4);
  DockSpring reverse=new DockSpring();reverse.position=1;reverse.coast(-4,4);
  for(int i=0;i<400;i++){forward.step(1f/120);reverse.step(1f/120);}
  require(forward.target==2&&reverse.target==0,"Identical release position selects different final items from velocity");
  DockSpring nearEdge=new DockSpring(),farEdge=new DockSpring();nearEdge.position=1.8f;farEdge.position=1f;nearEdge.coast(1f,2f);farEdge.coast(1f,2f);
  nearEdge.step(1f/120);farEdge.step(1f/120);
  require(nearEdge.velocity<farEdge.velocity&&nearEdge.position<2f,"Coast resistance increases before reaching boundary");
  DockSpring thrown=new DockSpring();thrown.position=2;thrown.coast(12,2);float farthest=2;
  for(int i=0;i<500;i++){thrown.step(1f/120);farthest=Math.max(farthest,thrown.position);}
  require(farthest>2.12f&&farthest<2.3f&&!thrown.coasting&&thrown.position<=2.01f,"Faster release and rebound retain a restrained visible recoil");
  for(float direction:new float[]{-1f,1f})for(float speed:new float[]{1f,.5f}){
   DockSpring hardThrow=new DockSpring();hardThrow.position=direction>0?2f:0f;hardThrow.movementSpeed=speed;
   hardThrow.coast(direction*100f,2f);float overflow=0f;
   for(int i=0;i<1500;i++){hardThrow.step(1f/120f);overflow=Math.max(overflow,direction>0?hardThrow.position-2f:-hardThrow.position);}
   require(overflow>.01f&&Float.isFinite(overflow)&&!hardThrow.coasting
     &&DockSpring.outwardExtension(overflow*100f,75f)<=75f,"Uncapped throw converges while visual overflow remains partial: edge="+direction+" speed="+speed+" peak="+overflow+" coasting="+hardThrow.coasting);
  }
  System.out.println("Moderate edge throw peak excess: "+(farthest-2f)+" slots");
  for(float pace:new float[]{.5f,1f})for(float distance:new float[]{1f,4f}){
   DockSpring distant=new DockSpring();distant.movementSpeed=pace;distant.follow(distance,0f);distant.press(true);
   for(int i=0;i<10;i++)distant.step(1f/120f);
   distant.press(false);distant.releaseWithPulse();float peakPosition=distant.position;
   for(int i=0;i<240;i++){distant.step(1f/120f);peakPosition=Math.max(peakPosition,distant.position);}
   require(peakPosition<=distance+.002f,"Distant tap must not oscillate past its target: pace="+pace+" distance="+distance+" peak="+peakPosition);
  }
  for(float speed:new float[]{-100f,25f,100f}){
   DockSpring uncapped=new DockSpring();uncapped.coast(speed,2f);
   require(uncapped.velocity==speed,"Release speed is unchanged, including fast throws: "+speed);
  }
  DockSpring naturalRelease=new DockSpring();naturalRelease.coast(4f,2f);
  require(naturalRelease.velocity==4f,"Moderate release is no longer unconditionally halved");
  DockSpring releasedNormal=new DockSpring(),releasedHalf=new DockSpring();releasedHalf.movementSpeed=.5f;
  releasedNormal.position=releasedHalf.position=1f;releasedNormal.coast(4f,10f);releasedHalf.coast(4f,10f);
  for(int i=0;i<240;i++){releasedNormal.step(1f/120f);releasedHalf.step(1f/120f);}
  require(Math.abs(releasedNormal.position-releasedHalf.position)<.001f&&Math.abs(releasedNormal.tailPosition-releasedHalf.tailPosition)<.001f,
    "Release coast, trailing end and final settling all use normal time on every selection bar");
  releasedHalf.coast(100f,2f);
  require(releasedHalf.velocity==100f,"Even formerly half-speed rails keep the full measured release speed");
  DockSpring tapNormal=new DockSpring(),tapHalf=new DockSpring();tapHalf.movementSpeed=.5f;
  tapNormal.target=tapHalf.target=3f;tapNormal.releaseWithPulse();tapHalf.releaseWithPulse();
  for(int i=0;i<30;i++){tapNormal.step(1f/120f);tapHalf.step(1f/120f);}
  require(Math.abs(tapNormal.position-tapHalf.position)<.001f,"Ordinary tap release also settles at normal speed on all rails");
  DockSpring quickRebound=new DockSpring();quickRebound.position=2.2f;quickRebound.coast(0f,2f);
  float oldOffset=.2f,oldVelocity=0f;int oldCross=0,newCross=0;
  for(int i=1;i<180;i++){
   oldVelocity+=(-180f*oldOffset-18f*oldVelocity)/120f;oldOffset+=oldVelocity/120f;
   quickRebound.step(1f/120f);
   if(oldCross==0&&oldOffset<=0f)oldCross=i;
   if(newCross==0&&quickRebound.position<=2f)newCross=i;
  }
  require(newCross>0&&oldCross>0&&newCross<oldCross*.8f,"Rebound returns at least 20 percent sooner without increasing its damping ratio");
  init();DockSpring edges=(DockSpring)springField.get(dock);var apply=DockNavigationLayout.class.getDeclaredMethod("applySpring");apply.setAccessible(true);
  edges.position=edges.tailPosition=3.5f;apply.invoke(dock);center();close(GradientDrawable.lastLeft,250,"Right throw pins inner end half a standard width inside");
  require(GradientDrawable.lastRight>350,"Right outer end can stretch beyond half width");
  edges.position=edges.tailPosition=-1.5f;apply.invoke(dock);center();close(GradientDrawable.lastRight,50,"Left throw pins inner end half a standard width inside");
  require(GradientDrawable.lastLeft<-50,"Left outer end can stretch beyond half width");
  require(GradientDrawable.lastLeft>=-75,"Outer end cannot exceed configured standard-width limit");
  init();DockSpring shape=(DockSpring)springField.get(dock);
  shape.position=shape.tailPosition=.5f;shape.scale=1.5f;apply.invoke(dock);center();
  int muted=UiStyle.colors(c).muted;
  require(((TextView)dock.itemAt(0)).getCurrentTextColor()!=muted
    &&((TextView)dock.itemAt(1)).getCurrentTextColor()!=muted,
    "Highlight coverage lights both labels while crossing instead of switching at midpoint");
  require(((TextView)((FrameLayout)dock.itemAt(2)).getChildAt(0)).getCurrentTextColor()==muted,"Uncovered label keeps its normal tint");
  float capX=GradientDrawable.radii[0]*highlight().getScaleX(),capY=GradientDrawable.radii[1]*highlight().getScaleY();
  require(Math.abs(capX-capY)<.01f&&Math.abs(capY-GradientDrawable.lastHeight/2f)<.01f,
    "Physical end caps remain circular after render-node stretching");
  shape.position=1.2f;shape.tailPosition=.4f;apply.invoke(dock);center();float stretchedWidth=GradientDrawable.lastWidth;
  require(stretchedWidth>150f&&stretchedWidth<225f,"Tail separation adds bounded length instead of multiplying held scale");
  float beforeTail=shape.tailPosition,beforeLeading=shape.position;shape.follow(0f,0f);
  require(shape.tailPosition==beforeTail&&shape.position==beforeLeading,"Reversal preserves both end positions in the input frame");
  for(int i=0;i<240;i++)shape.step(1f/120f);
  require(Math.abs(shape.position)<.002f&&Math.abs(shape.tailPosition)<.002f,"Reversed tail converges without a stuck trail");
  init();touch(0,250,45);View.advance(160);require(dock.getTranslationX()>0f&&dock.getTranslationX()<6f
    &&dock.getTranslationY()>0f&&dock.getTranslationY()<4f,"Floating plate leans toward the contact within bounded dp travel");
  touch(3,250,45);View.advance(1600);require(Math.abs(dock.getTranslationX())<.01f&&Math.abs(dock.getTranslationY())<.01f,
    "Cancelled contact restores plate translation and actual selection");
  init();dock.fullWidthMotionHost();touch(0,250,45);View.advance(160);
  require(dock.getTranslationX()==0f,"Full-width form track never drifts into the viewport's horizontal clipping edge");
  require(Math.abs(GradientDrawable.radii[0]*highlight().getScaleX()*dock.getScaleX()
    -GradientDrawable.radii[1]*highlight().getScaleY()*dock.getScaleY())<.01f,
    "Full-width plate's nonuniform scale also preserves physical circular caps");
  touch(3,250,45);View.advance(1600);
  float cover1=DockSpring.coverage(-5f,0f,100f,20f),cover2=DockSpring.coverage(0f,0f,100f,20f),cover3=DockSpring.coverage(5f,0f,100f,20f);
  require(cover1>0f&&cover1<cover2&&cover2<cover3&&cover3<1f,"Coverage tint interpolates continuously across a highlight edge");
  require(DockSpring.coverage(50f,0f,100f,20f)==1f&&DockSpring.coverage(130f,0f,100f,20f)==0f,
    "Coverage resolves cleanly to active and inactive endpoints");
  DockSpring normalSpeed=new DockSpring(),halfSpeed=new DockSpring();normalSpeed.target=2f;halfSpeed.target=2f;halfSpeed.movementSpeed=.5f;
  for(int i=0;i<120;i++){normalSpeed.step(1f/240f);halfSpeed.step(1f/120f);}
  require(Math.abs(normalSpeed.position-halfSpeed.position)<.001f&&Math.abs(normalSpeed.tailPosition-halfSpeed.tailPosition)<.001f,
    "Half-speed movement reaches the same leading and trailing state in twice the wall time");
  normalSpeed.press(true);halfSpeed.press(true);normalSpeed.step(.032f);halfSpeed.step(.032f);
  require(Math.abs(normalSpeed.scale-halfSpeed.scale)<.001f,"Movement speed does not slow down press expansion");
  DockSpring fingerFast=new DockSpring(),fingerHalf=new DockSpring();fingerHalf.movementSpeed=.5f;
  fingerFast.follow(2f,0f);fingerHalf.follow(2f,0f);
  for(int i=0;i<12;i++){fingerFast.step(1f/120f);fingerHalf.step(1f/120f);}
  require(Math.abs(fingerFast.position-fingerHalf.position)<.001f&&Math.abs(fingerFast.tailPosition-fingerHalf.tailPosition)<.001f,
    "Physical finger tracking does not inherit the slower automatic selection timing");
  init();touch(0,250,35);View.advance(16);
  require(((DockSpring)springField.get(dock)).scaleTarget==1.5f&&((DockSpring)springField.get(dock)).scale>1f,
    "All selection bars begin full expansion on the first pressed frame without waiting for long press");
  touch(3,250,35);View.advance(1600);
  var defaultDock=new DockNavigationLayout(c,n->{},()->{});
  require(((DockSpring)springField.get(defaultDock)).movementSpeed==.5f,"Non-home selection bars default to half-speed highlight movement");
  float a=DockSpring.outwardExtension(20,75),b=DockSpring.outwardExtension(40,75),c=DockSpring.outwardExtension(60,75);
  require(a<b&&b<c&&(c-b)<(b-a),"Resistance progressively increases toward the outward limit");
  require(DockSpring.outwardExtension(10000,75)<=75,"Extreme fling remains bounded");
  float slow1=DockSpring.softUpperBoundary(230,250,50),slow2=DockSpring.softUpperBoundary(240,250,50),slow3=DockSpring.softUpperBoundary(250,250,50);
  require(slow1<230&&slow2<240&&slow3<250,"Inner end slows before reaching the anchor");
  require(slow1<slow2&&slow2<slow3&&slow3-slow2<slow2-slow1,"Inner end decelerates smoothly instead of stopping abruptly");
  require(Math.abs(DockSpring.softUpperBoundary(200.01f,250,50)-200.01f)<.001f,"Soft approach joins regular motion continuously");
  DockSpring prediction=new DockSpring();prediction.position=1;prediction.velocity=3;
  require(prediction.predictLanding(4,4)==2&&prediction.predictLanding(-4,4)==0,"Prediction includes measured-momentum landing");
  require(prediction.position==1&&prediction.velocity==3,"Prediction does not mutate displayed state or velocity");
  DockSpring predictedThrow=new DockSpring();predictedThrow.position=1.9f;int predictedLanding=predictedThrow.predictLanding(12f,2f);predictedThrow.coast(12f,2f);
  for(int i=0;i<1000;i++)predictedThrow.step(1f/120);
  require(!predictedThrow.coasting&&Math.abs(predictedThrow.position-predictedLanding)<.002f,"Predicted landing agrees with actual edge throw and rebound");
  DockSpring resisted=new DockSpring();resisted.coast(1,10);for(int i=0;i<120;i++)resisted.step(1f/120);
  require(resisted.position<.14f,"Increased ordinary resistance shortens coast distance");
  init();dock.finishPageSelection(2);int redraws=dock.invalidations,layouts=highlight().layouts;View.advance(400);
  require(dock.invalidations==redraws,"Motion does not invalidate entire glass dock each frame");
  require(highlight().layouts==layouts,"Motion uses render properties without repeated layout");
  init();dock.setPadding(8,6,8,6);dock.layout(0,0,300,72);
  for(int i=0;i<3;i++)dock.itemAt(i).layout(i*94,0,(i+1)*94,60);
  android.animation.ValueAnimator.enabled=false;dock.finishPageSelection(1);dock.layout(0,0,300,72);
  close(center(),149,"Padded horizontal dock centers highlight on button after parent relayout");
  close(highlight().getTop()+highlight().getTranslationY()+highlight().getHeight()*highlight().getScaleY()/2,36,"Padded dock does not double its top inset");
  close(highlight().getTop()+highlight().getTranslationY(),6,"Top spacing is preserved");
  close(72-highlight().getTop()-highlight().getTranslationY()-highlight().getHeight()*highlight().getScaleY(),6,"Bottom spacing matches top spacing");
  dock.setPadding(13,9,13,9);dock.layout(0,0,300,78);
  close(center(),154,"Changed padding is applied exactly once");
  close(highlight().getTop()+highlight().getTranslationY(),9,"Changed vertical inset is applied exactly once");
  for(int count:new int[]{4,5}){
   dock=new DockNavigationLayout(DockNavigationCheck.c,n->{},()->{});new FrameLayout(DockNavigationCheck.c).addView(dock);
   dock.setPadding(4,2,4,2);for(int i=0;i<count;i++)dock.addItem(new TextView(DockNavigationCheck.c));dock.layout(0,0,320,48);
   int cellWidth=312/count;for(int i=0;i<count;i++)dock.itemAt(i).layout(i*cellWidth,0,(i+1)*cellWidth,44);
   dock.finishPageSelection(1);dock.layout(0,0,320,48);
   close(center(),4+1.5f*cellWidth,"Filter dock stays centered with "+count+" items and padding");
   close(highlight().getTop()+highlight().getTranslationY(),2,"Compact filter top inset is preserved");
  }
  dock=new DockNavigationLayout(DockNavigationCheck.c,n->{},()->{});new FrameLayout(DockNavigationCheck.c).addView(dock);
  dock.setOrientation(LinearLayout.VERTICAL);dock.setPadding(8,12,8,12);for(int i=0;i<3;i++)dock.addItem(new TextView(DockNavigationCheck.c));dock.layout(0,0,76,324);
  for(int i=0;i<3;i++)dock.itemAt(i).layout(0,i*100,60,(i+1)*100);dock.finishPageSelection(1);dock.layout(0,0,76,324);
  close(center(),38,"Vertical padded dock keeps highlight centered horizontally");
  close(highlight().getTop()+highlight().getTranslationY()+highlight().getHeight()*highlight().getScaleY()/2,162,"Vertical padded dock applies top origin exactly once");
  android.animation.ValueAnimator.enabled=true;
  init();dock=new DockNavigationLayout(DockNavigationCheck.c,n->selected.add(n),()->{});dock.setHighlightMovementSpeed(1f);new FrameLayout(DockNavigationCheck.c).addView(dock);populate();dock.setOrientation(LinearLayout.VERTICAL);dock.layout(0,0,60,300);
  for(int i=0;i<3;i++)dock.itemAt(i).layout(0,i*100,60,(i+1)*100);dock.setSelectedIndex(0);
  touch(0,35,25);touch(2,35,150);View.advance(600);touch(1,35,150);View.advance(1800);
  require(selected.equals(List.of(1)),"Shared vertical dock commits a vertical drag exactly once");
  close(highlight().getTranslationY()+highlight().getHeight()*highlight().getScaleY()/2,150,"Vertical highlight settles on active row");
  init();dock=new DockNavigationLayout(DockNavigationCheck.c,n->selected.add(n),()->{});dock.setHighlightMovementSpeed(1f);new FrameLayout(DockNavigationCheck.c).addView(dock);populate();dock.setOrientation(LinearLayout.VERTICAL);dock.layout(0,0,60,300);
  for(int i=0;i<3;i++)dock.itemAt(i).layout(0,i*100,60,(i+1)*100);dock.setSelectedIndex(0);
  android.view.VelocityTracker.velocity=900;touch(0,35,25);touch(2,35,140);touch(1,100,140);
  require(Math.abs(((DockSpring)springField.get(dock)).velocity-9f)<.001f,"Vertical release keeps measured finger momentum");
  require(selected.size()==1,"Vertical outside release starts predicted page immediately");android.view.VelocityTracker.velocity=0;
  init();touch(0,245,35);close(center(),50,"Press preview starts at current visual position");View.advance(32);
  require(center()>50&&center()<245&&selected.isEmpty(),"Press preview moves before long press without navigating");
  View.advance(500);View.advance(600);close(center(),245,"Long press converges to held finger position");
  touch(3,245,35);View.advance(1000);close(center(),50,"Cancel stops finger loop and returns");
  init();touch(0,225,35);touch(2,245,35);View.advance(32);touch(5,245,35);View.advance(1000);close(center(),50,"Multiple pointers stop follow");
  init();touch(0,225,35);touch(2,245,35);View.advance(32);dock.onDetachedFromWindow();float detached=center();View.advance(400);close(center(),detached,"Detach cancels scheduled follow");
  init();touch(0,225,35);touch(2,245,35);View.advance(32);float bodyStart=center();dock.beginBodyDrag();close(center(),bodyStart,"Body takeover preserves the current visual frame");View.advance(1000);
  require(!dock.isAnimating(),"Body takeover stops the old finger loop after the trailing end settles");
  init();android.animation.ValueAnimator.enabled=false;touch(0,225,35);touch(2,245,35);close(center(),245,"Disabled animations follow immediately");android.animation.ValueAnimator.enabled=true;
  init();touch(0,225,35);touch(2,245,35);View.advance(16);android.animation.ValueAnimator.enabled=false;
  View.advance(16);close(center(),245,"Disabling during follow settles on next frame");android.animation.ValueAnimator.enabled=true;
  System.out.println("Dock navigation checks passed: "+checks+" (queue test double, not device runtime)");
 }
}
"""

dashboard = (ROOT / "app/src/main/java/com/donglan/chrona/DashboardActivity.java").read_text(encoding="utf-8")
start = dashboard.index("    private DockNavigationLayout filterDock(")
end = dashboard.index("    /** The one count marker:", start)
RUNNER = RUNNER.replace("__FILTER_DOCK__", dashboard[start:end])
start = dashboard.index("    private void allowSectionDockOverflow(")
end = dashboard.index("    private DockNavigationLayout filterDock(", start)
RUNNER = RUNNER.replace("__OVERFLOW__", dashboard[start:end])

for name, source in STUBS.items():
    file = OUT / "src" / name
    file.parent.mkdir(parents=True, exist_ok=True)
    file.write_text(source, encoding="utf-8")
runner = OUT / "src/com/donglan/chrona/DockNavigationCheck.java"
runner.write_text(RUNNER, encoding="utf-8")
java_home = Path(os.environ["JAVA_HOME"])
files = sorted(str(file) for file in (OUT / "src").rglob("*.java"))
files += [str(ROOT / "app/src/main/java/com/donglan/chrona" / name) for name in ("DockNavigationLayout.java", "DockSpring.java", "SwipePagerLayout.java")]
subprocess.run([str(java_home / "bin/javac.exe"), "-encoding", "UTF-8", "-d", str(OUT / "classes"), *files], check=True)
subprocess.run([str(java_home / "bin/java.exe"), "-cp", str(OUT / "classes"), "com.donglan.chrona.DockNavigationCheck"], check=True)
