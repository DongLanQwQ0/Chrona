"""Compile the complete production HeaderActionGroup against small Android stubs.

Exercises slot/separator state and the animation callbacks, not Android rendering,
actual layout measurement, frame timing, touch dispatch or accessibility services.
"""
from pathlib import Path
import os
import shutil
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "build/ui-calendar-header-20261104/check-owner"
SRC = OUT / "src"
CLASSES = OUT / "classes"

STUBS = {
    "android/content/Context.java": """
package android.content;
public class Context {
 public final android.content.res.Resources resources=new android.content.res.Resources();
 public android.content.res.Resources getResources(){return resources;}
}
""",
    "android/content/res/Resources.java": """
package android.content.res;
public class Resources {
 public static class DisplayMetrics {public float density=1;}
 final DisplayMetrics metrics=new DisplayMetrics();
 public DisplayMetrics getDisplayMetrics(){return metrics;}
}
""",
    "android/view/Gravity.java": "package android.view; public class Gravity {public static final int CENTER_VERTICAL=16;}",
    "android/graphics/Color.java": "package android.graphics; public class Color {public static final int TRANSPARENT=0;}",
    "android/graphics/Canvas.java": "package android.graphics; public class Canvas {}",
    "android/view/View.java": """
package android.view;
public class View {
 public static final int VISIBLE=0,GONE=8,IMPORTANT_FOR_ACCESSIBILITY_AUTO=0,IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4;
 final android.content.Context context;
 public boolean attached,laidOut,clickable,focusable;
 public int accessibility,visibility=VISIBLE,background;
 public float alpha=1,scaleX=1,scaleY=1;
 int left,top,right,bottom;
 android.widget.LinearLayout.LayoutParams params;
 public View(android.content.Context c){context=c;}
 public android.content.Context getContext(){return context;}
 public android.content.res.Resources getResources(){return context.getResources();}
 public void setBackgroundColor(int value){background=value;}
 public void setVisibility(int value){visibility=value;}public int getVisibility(){return visibility;}
 public void setAlpha(float value){alpha=value;}public void setScaleX(float value){scaleX=value;}public void setScaleY(float value){scaleY=value;}
 public void setClickable(boolean value){clickable=value;}public void setFocusable(boolean value){focusable=value;}
 public void setImportantForAccessibility(int value){accessibility=value;}
 public boolean isAttachedToWindow(){return attached;}public boolean isLaidOut(){return laidOut;}
 public android.widget.LinearLayout.LayoutParams getLayoutParams(){return params;}
 public void setLayoutParams(android.widget.LinearLayout.LayoutParams value){params=value;}
 public int getWidth(){return params==null?0:params.width;}public int getHeight(){return params==null?48:params.height;}
 public int getPaddingLeft(){return left;}public int getPaddingTop(){return top;}public int getPaddingRight(){return right;}public int getPaddingBottom(){return bottom;}
 public void setPadding(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 protected void onSizeChanged(int w,int h,int ow,int oh){}protected void onDetachedFromWindow(){}
}
""",
    "android/widget/LinearLayout.java": """
package android.widget;
public class LinearLayout extends android.view.View {
 public static class LayoutParams {public int width,height;public LayoutParams(int w,int h){width=w;height=h;}}
 final java.util.List<android.view.View> children=new java.util.ArrayList<>();
 public LinearLayout(android.content.Context c){super(c);}
 public void setGravity(int g){}public void setClipChildren(boolean b){}public void setClipToPadding(boolean b){}
 public void addView(android.view.View v,LayoutParams p){children.add(v);v.setLayoutParams(p);}
 public int getChildCount(){return children.size();}public android.view.View getChildAt(int i){return children.get(i);}
 protected boolean drawChild(android.graphics.Canvas c,android.view.View v,long t){return true;}
}
""",
    "android/animation/ValueAnimator.java": """
package android.animation;
public class ValueAnimator {
 public interface AnimatorUpdateListener {void onAnimationUpdate(ValueAnimator a);}
 public static final java.util.List<ValueAnimator> created=new java.util.ArrayList<>();
 public final float from,to;public float value;public long duration;public Object interpolator;public boolean cancelled,started;
 AnimatorUpdateListener listener;
 ValueAnimator(float f,float t){from=f;to=t;value=f;created.add(this);}
 public static ValueAnimator ofFloat(float... v){return new ValueAnimator(v[0],v[1]);}
 public void setDuration(long d){duration=d;}public void setInterpolator(Object i){interpolator=i;}
 public void addUpdateListener(AnimatorUpdateListener l){listener=l;}public Object getAnimatedValue(){return value;}
 public void start(){started=true;}public void cancel(){cancelled=true;}
 public void fraction(float f){if(cancelled)throw new AssertionError("cancelled animation advanced");value=from+(to-from)*f;listener.onAnimationUpdate(this);}
 public static ValueAnimator latest(){return created.get(created.size()-1);}
}
""",
    "com/donglan/chrona/Dependencies.java": """
package com.donglan.chrona;
class UiStyle {
 static final int RADIUS_PILL=24;static class Colors {int outline=123;}
 static Colors colors(android.content.Context c){return new Colors();}
 static void acrylicChoice(android.view.View v,boolean a,int r,boolean b){}
 static int radius(android.content.Context c,int r){return r;}
}
class UiMotion {static boolean enabled=true;static final long ENTER=220,EXIT=160;static final Object SETTLE=new Object();static boolean isEnabled(){return enabled;}}
class PressFeedback {static android.view.View group;static float scale;static void group(android.view.View v,float s){group=v;scale=s;}}
class UiSurfaces {static int invalidations;static void invalidateControlHighlight(android.view.View v){invalidations++;}}
""",
}

HARNESS = r"""
package com.donglan.chrona;
import android.view.View;
import android.animation.ValueAnimator;
public class HeaderActionGroupCheck {
 static int checks;
 static void check(boolean ok,String name){checks++;if(!ok)throw new AssertionError(name);}
 static void near(float a,float b,String name){check(Math.abs(a-b)<.00001f,name+" expected="+b+" actual="+a);}
 static int dp(float density,int value){return Math.round(density*value);}
 static class Fixture {
  final android.content.Context context=new android.content.Context();final HeaderActionGroup group;final View[] actions;
  Fixture(float density,int count){context.resources.getDisplayMetrics().density=density;group=new HeaderActionGroup(context,48);actions=new View[count];for(int i=0;i<count;i++){actions[i]=new View(context);group.addAction(actions[i],48);}}
  View separator(int i){return group.getChildAt(i*2-1);}
  void states(float density,float... progress){
   float preceding=0;
   for(int i=0;i<progress.length;i++){
    View action=actions[i];float p=progress[i];
    check(action.getLayoutParams().width==Math.round(dp(density,48)*p),"action slot width "+i);
    check(action.getVisibility()==(p==0?View.GONE:View.VISIBLE),"action visibility "+i);near(action.alpha,p,"action alpha");
    if(p!=1){near(action.scaleX,.85f+.15f*p,"action scale X");near(action.scaleY,.85f+.15f*p,"action scale Y");}
    if(i>0){View s=separator(i);float expected=Math.min(preceding,p);near(s.alpha,expected,"separator alpha "+i);check(s.getVisibility()==(expected==0?View.GONE:View.VISIBLE),"separator visibility "+i);check(s.getLayoutParams().width==Math.round(dp(density,1)*expected),"separator width "+i);check(s.getLayoutParams().height==dp(density,16),"separator height unchanged");}
    preceding=Math.max(preceding,p);
   }
  }
 }
 public static void main(String[] args){
  for(float density:new float[]{.75f,1f,1.5f,2f,2.75f,3.5f}){
   for(int mask=0;mask<8;mask++){
    Fixture f=new Fixture(density,3);float[] p=new float[3];
    for(int i=0;i<3;i++){boolean visible=(mask&(1<<i))!=0;p[i]=visible?1:0;f.group.setActionVisible(f.actions[i],visible);check(f.actions[i].clickable==visible&&f.actions[i].focusable==visible,"action focus and click gates");check(f.actions[i].accessibility==(visible?0:4),"accessibility gate");}
    f.states(density,p);
   }
   Fixture f=new Fixture(density,2);f.group.setActionVisible(f.actions[0],false);f.states(density,0,1);
   check(f.actions[1].getLayoutParams().width==dp(density,48),"single remaining more action retains 48dp slot");
   f.actions[1].setPadding(4,5,6,7);f.group.drawChild(new android.graphics.Canvas(),f.actions[1],0);
   check(f.actions[1].getPaddingLeft()==4&&f.actions[1].getPaddingRight()==6,"single action has no optical end shift");
   Fixture early=new Fixture(density,1);early.group.setActionVisible(early.actions[0],false);View added=new View(early.context);early.group.addAction(added,48);
   View separator=early.group.getChildAt(1);check(separator.getVisibility()==View.GONE&&separator.getLayoutParams().width==0&&separator.alpha==0,"append after first hidden adds no leading separator");
   Fixture single=new Fixture(density,1);check(single.group.getChildCount()==1&&single.actions[0].getLayoutParams().width==dp(density,48),"single action 48dp without any separator");
   Fixture animated=new Fixture(density,3);animated.group.attached=animated.group.laidOut=true;
   animated.group.setActionVisible(animated.actions[0],false);ValueAnimator exit=ValueAnimator.latest();check(exit.started&&exit.duration==UiMotion.EXIT&&exit.interpolator==UiMotion.SETTLE,"existing exit motion contract");exit.fraction(.75f);animated.states(density,.25f,1,1);
   animated.group.setActionVisible(animated.actions[1],false);ValueAnimator middle=ValueAnimator.latest();middle.fraction(.5f);animated.states(density,.25f,.5f,1);
   animated.group.setActionVisible(animated.actions[0],true);ValueAnimator reverse=ValueAnimator.latest();check(exit.cancelled,"reverse cancels prior exit");near(reverse.from,.25f,"reverse starts from current progress");check(reverse.duration==UiMotion.ENTER&&reverse.interpolator==UiMotion.SETTLE,"existing enter motion contract");reverse.fraction(.5f);animated.states(density,.625f,.5f,1);
   reverse.fraction(1);middle.fraction(1);animated.states(density,1,0,1);
   animated.group.setActionVisible(animated.actions[2],false);ValueAnimator tail=ValueAnimator.latest();tail.fraction(.4f);animated.group.onDetachedFromWindow();check(tail.cancelled,"detach cancels running tail motion");animated.states(density,1,0,0);
   UiMotion.enabled=false;animated.group.setActionVisible(animated.actions[0],false);animated.states(density,0,0,0);animated.group.setActionVisible(animated.actions[2],true);animated.states(density,0,0,1);UiMotion.enabled=true;
   animated.group.finish();check(PressFeedback.group==animated.group&&PressFeedback.scale==1.10f,"group press feedback unchanged");
   int invalidations=UiSurfaces.invalidations;animated.group.onSizeChanged(48,48,97,48);check(UiSurfaces.invalidations-invalidations==animated.group.getChildCount()+1,"size changes invalidate all highlight masks");
  }
  Fixture f=new Fixture(1,1);boolean rejected=false;try{f.group.setActionVisible(new View(f.context),false);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,"unknown action rejected");
  System.out.println("PASS: "+checks+" assertions; complete production HeaderActionGroup compiled; 6 densities, all 3-action visibility masks, append-after-hidden, single 48dp action, interrupted/reversed motion, detach, reduced motion, interaction and highlight contracts.");
  System.out.println("Scope: deterministic Android stubs; actual Android rendering, animation frame timing, touch dispatch and accessibility service acceptance remain separate.");
 }
}
"""


def main():
    files = dict(STUBS)
    files["com/donglan/chrona/HeaderActionGroup.java"] = (
        ROOT / "app/src/main/java/com/donglan/chrona/HeaderActionGroup.java"
    ).read_text(encoding="utf-8")
    files["com/donglan/chrona/HeaderActionGroupCheck.java"] = HARNESS
    paths = []
    for relative, content in files.items():
        path = SRC / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")
        paths.append(str(path))
    CLASSES.mkdir(parents=True, exist_ok=True)
    suffix = ".exe" if os.name == "nt" else ""
    home = os.environ.get("JAVA_HOME")
    def tool(name):
        candidate = Path(home) / "bin" / (name + suffix) if home else None
        return str(candidate) if candidate and candidate.is_file() else shutil.which(name)
    subprocess.run([tool("javac"), "--release", "17", "-encoding", "UTF-8", "-d", str(CLASSES), *paths], check=True)
    result = subprocess.check_output([tool("java"), "-cp", str(CLASSES), "com.donglan.chrona.HeaderActionGroupCheck"], text=True)
    (OUT / "result.txt").write_text(result, encoding="utf-8")
    print(result, end="")


if __name__ == "__main__":
    main()
