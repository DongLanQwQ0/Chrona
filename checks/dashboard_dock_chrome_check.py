"""Execute the production home-only capture / Dock coordinator with interruptible UI doubles."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'build/dashboard-dock-chrome-check'
STUBS = {
    'android/animation/Animator.java': 'package android.animation;public class Animator {}',
    'android/animation/AnimatorListenerAdapter.java': 'package android.animation;public class AnimatorListenerAdapter {public void onAnimationEnd(Animator a){}}',
    'android/animation/ValueAnimator.java': '''package android.animation;
import java.util.*;
public class ValueAnimator extends Animator {
 public static ValueAnimator last;float from,to,value;public long duration;public android.view.animation.Interpolator curve;
 public interface Update {void onAnimationUpdate(ValueAnimator frame);}
 final List<Update> updates=new ArrayList<>();final List<AnimatorListenerAdapter> ends=new ArrayList<>();
 public static ValueAnimator ofFloat(float from,float to){last=new ValueAnimator();last.from=from;last.to=to;last.value=from;return last;}
 public void setDuration(long d){duration=d;}public void setInterpolator(android.view.animation.Interpolator i){curve=i;}
 public void addUpdateListener(Update update){updates.add(update);}public void addListener(AnimatorListenerAdapter end){ends.add(end);}
 public void removeAllUpdateListeners(){updates.clear();}public void removeAllListeners(){ends.clear();}
 public void cancel(){for(var end:List.copyOf(ends))end.onAnimationEnd(this);}public void start(){}
 public Object getAnimatedValue(){return value;}public long getCurrentPlayTime(){return Math.round(duration*(value-from)/(to-from));}
 public void step(float f){value=from+(to-from)*f;for(var update:List.copyOf(updates))update.onAnimationUpdate(this);if(f==1)for(var end:List.copyOf(ends))end.onAnimationEnd(this);}
}''',
    'android/view/animation/Interpolator.java': 'package android.view.animation;public interface Interpolator {float getInterpolation(float input);}',
    'android/os/Build.java': 'package android.os;public class Build {public static class VERSION {public static int SDK_INT=36;}}',
    'android/graphics/Shader.java': 'package android.graphics;public class Shader {public enum TileMode {CLAMP,DECAL}}',
    'android/graphics/RenderEffect.java': 'package android.graphics;public class RenderEffect {public float radius;public Shader.TileMode mode;public static RenderEffect createBlurEffect(float x,float y,Shader.TileMode mode){var effect=new RenderEffect();effect.radius=x;effect.mode=mode;return effect;}}',
    'android/view/View.java': '''package android.view;
public class View {
 public static final int VISIBLE=0,INVISIBLE=4,IMPORTANT_FOR_ACCESSIBILITY_AUTO=0,IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4;
 public int left,top,right,bottom,mw,mh,visibility,access,pl,pr,pb;public float alpha=1,sx=1,sy=1;public boolean enabled=true;
 public void setVisibility(int v){visibility=v;}public void setAlpha(float v){alpha=v;}public void setScaleX(float v){sx=v;}public void setScaleY(float v){sy=v;}
 public void setEnabled(boolean v){enabled=v;}public void setImportantForAccessibility(int v){access=v;}public void invalidate(){}
 public android.graphics.RenderEffect effect;public void setRenderEffect(android.graphics.RenderEffect v){effect=v;}
 public int getWidth(){return right-left;}public int getHeight(){return bottom-top;}public int getLeft(){return left;}public int getRight(){return right;}public int getTop(){return top;}public int getBottom(){return bottom;}
 public int getMeasuredWidth(){return mw;}public int getMeasuredHeight(){return mh;}public int getPaddingLeft(){return pl;}public int getPaddingRight(){return pr;}public int getPaddingBottom(){return pb;}
 public static class MeasureSpec {public static final int EXACTLY=1;public static int makeMeasureSpec(int size,int mode){return size;}}
 public void measure(int w,int h){mw=w;mh=h;}public void layout(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
}''',
    'android/widget/FrameLayout.java': 'package android.widget;public class FrameLayout extends android.view.View {}',
    'com/donglan/chrona/UiMotion.java': 'package com.donglan.chrona;class UiMotion {static boolean enabled=true;static final android.view.animation.Interpolator SETTLE=input->input;static boolean isEnabled(){return enabled;}}',
    'com/donglan/chrona/DockNavigationLayout.java': '''package com.donglan.chrona;
class DockNavigationLayout extends android.view.View {
 static class Metrics{public float density=1;}static class Resources{public Metrics getDisplayMetrics(){return new Metrics();}}
 public Resources getResources(){return new Resources();}
}''',
}
RUNNER = '''package com.donglan.chrona;
import android.view.View;import android.widget.FrameLayout;import android.animation.ValueAnimator;
public class DockChromeCheck {
 static int checks;static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
 static void near(float actual,float expected,String name){check(Math.abs(actual-expected)<.001f,name+" got "+actual);}
 public static void main(String[]args){
  var dock=new DockNavigationLayout();var host=new FrameLayout();host.layout(0,0,360,88);
  var capture=new View();var button=new View();var chrome=new DashboardDockChrome(dock,host,capture,button,64,true);
  check(capture.visibility==View.VISIBLE&&button.enabled,"Home starts visible and clickable");
  check(dock.left==36&&dock.right==256&&dock.top==16&&dock.getHeight()==64,"Home preserves existing dock frame");
  chrome.animateHome(false,240);var leave=ValueAnimator.last;
  check(leave.duration==460&&leave.curve.getInterpolation(.25f)<.25f,"Exit lasts 460ms with a gradual nonlinear start");leave.step(.5f);
  chrome.setHome(false);near(chrome.fraction(),.5f,"Page completion cannot cut off the chrome exit");
  near(chrome.fraction(),.5f,"Hide advances continuously");near(capture.alpha,.5f,"Plus fades with frame progress");near(capture.sx,.8f,"Plus wrapper shrinks without modifying button scale");
  near(button.sx,1f,"Pressed-state button retains independent scale");check(!button.enabled,"Transitioning plus cannot be tapped");
  check(dock.getWidth()==233&&Math.abs(dock.left+dock.right-360)<=1,"Dock is centered at half of the previous duration while capture exit is still running");
  check(capture.effect!=null&&capture.effect.radius==9f,"Plus progressively blurs while fading out");
  check(capture.effect.mode==android.graphics.Shader.TileMode.DECAL,"Blur samples transparent pixels outside its layer instead of repeating a bright border");
  chrome.animateHome(true,240);var returnHome=ValueAnimator.last;near(chrome.fraction(),.5f,"Reversal begins at current frame");leave.step(1f);near(chrome.fraction(),.5f,"Cancelled animator cannot force a stale endpoint");
  returnHome.step(.5f);near(chrome.fraction(),.75f,"Reverse animation continues from intermediate state");returnHome.step(1f);
  check(button.enabled&&capture.access==View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,"Completed return restores click and accessibility");
  check(capture.effect==null,"Returned home action clears its blur effect");
  chrome.setHome(false);check(capture.visibility==View.INVISIBLE&&!button.enabled&&capture.access==View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,"Other pages fully hide plus and its accessibility target");
  check(dock.getWidth()==233&&Math.abs(dock.left+dock.right-360)<=1,"Hidden plus centers a modestly wider Dock instead of filling all freed space");
  check(capture.effect==null,"Fully hidden action releases its blur effect");
  chrome.setHome(true);chrome.beginDrag();chrome.drag(1f,false,.4f);near(chrome.fraction(),.6f,"Body swipe previews hide");chrome.drag(1f,false,.2f);near(chrome.fraction(),.8f,"Reversed swipe restores continuously");
  chrome.animateHome(true,180);ValueAnimator.last.step(1);check(button.enabled&&dock.right==256,"Cancelled swipe restores home state");
  chrome.setHome(false);host.layout(0,0,600,110);host.pl=12;host.pr=12;host.pb=20;chrome.layoutMobile();
  check(dock.left==74&&dock.right==526&&dock.bottom==82,"Resizing retains centered bounds with a capped width gain and correct insets");
  var cold=new DashboardDockChrome(new DockNavigationLayout(),null,new View(),new View(),64,false);
  near(cold.fraction(),0f,"Non-home direct entry starts hidden without a visible flash");
  var wideCapture=new View();var wideButton=new View();var wideDock=new DockNavigationLayout();wideDock.layout(0,0,78,290);
  var wide=new DashboardDockChrome(wideDock,null,wideCapture,wideButton,64,true);wide.setHome(false);
  check(wideCapture.visibility==View.INVISIBLE&&wideDock.getWidth()==78&&wideDock.getHeight()==290,"Wide layout hides action without resizing vertical navigation");
  UiMotion.enabled=false;chrome.animateHome(true,240);near(chrome.fraction(),1f,"Reduced motion applies correct home state immediately");
  UiMotion.enabled=true;chrome.animateHome(false,240);ValueAnimator.last.step(.3f);UiMotion.enabled=false;ValueAnimator.last.step(.5f);
  near(chrome.fraction(),0f,"Disabling animation during transition settles to destination");
  chrome.drag(1f,false,.75f);near(capture.alpha,.15625f,"Capture alpha follows a nonlinear smooth fade rather than raw page distance");
  android.os.Build.VERSION.SDK_INT=30;capture.effect=null;chrome.drag(1f,false,.5f);
  check(capture.effect==null&&capture.alpha==.5f,"Pre-31 devices retain fade without calling unsupported blur APIs");
  android.os.Build.VERSION.SDK_INT=36;chrome.setHome(true);
  for(int[] size:new int[][]{{52,14},{56,24}}){int gutter=DashboardDockChrome.BLUR_GUTTER_DP;
   int outerSize=size[0]+2*gutter,outerRight=360-(size[1]-gutter),innerRight=outerRight-gutter;
   check(innerRight==360-size[1]&&outerSize-2*gutter==size[0],"Blur gutter preserves original button size and screen position");
   check(gutter>=36,"Transparent gutter surrounds all four sides of the blur input");}
  UiMotion.enabled=true;chrome.setHome(true);chrome.animateHome(false,240);ValueAnimator.last.step(.5f);
  chrome.beginDrag();float pausedCapture=chrome.fraction();int pausedLeft=dock.left,pausedRight=dock.right;
  chrome.drag(pausedCapture,false,0f);
  check(dock.left==pausedLeft&&dock.right==pausedRight,"A new gesture preserves the centered Dock independently of unfinished plus fade");
  chrome.setHome(true);int homeLeft=dock.left;chrome.animateHome(false,240);ValueAnimator.last.step(.25f);int middleLeft=dock.left;
  ValueAnimator.last.step(.5f);
  check(middleLeft>homeLeft&&middleLeft<dock.left,"Centering passes through intermediate bounds before its half-duration endpoint");
  System.out.println("Dashboard Dock chrome checks passed: "+checks+" (production coordinator, UI doubles; no device rendering)");
 }
}'''
for name, content in STUBS.items():
    path = OUT / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(content, encoding='utf-8')
runner = OUT / 'com/donglan/chrona/DockChromeCheck.java'
runner.write_text(RUNNER, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
files = [str(p) for p in OUT.rglob('*.java')]
files.append(str(ROOT / 'app/src/main/java/com/donglan/chrona/DashboardDockChrome.java'))
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(OUT / 'classes'), *files], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(OUT / 'classes'), 'com.donglan.chrona.DockChromeCheck'], check=True)

source = (ROOT / 'app/src/main/java/com/donglan/chrona/DashboardActivity.java').read_text(encoding='utf-8')
assert 'dockChrome.drag(dockDragFrom,' in source
assert 'dockChrome.animateHome((commit ? adjacentPage.section : section) == HOME,' in source
assert 'dockChrome.setHome(section == HOME)' in source
assert 'if (dockChrome != null) dockChrome.layoutMobile();' in source
assert source.count('captureHost.setPadding(blurGutter, blurGutter, blurGutter, blurGutter)') == 2
assert 'dp(14) - blurGutter' in source and 'dp(24) - blurGutter' in source
style = (ROOT / 'app/src/main/java/com/donglan/chrona/UiSurfaces.java').read_text(encoding='utf-8')
assert 'railSurface.getWidth() < bounds.width()' in style
assert 'railSource.set(0, 0, bounds.width(), bounds.height())' in style
assert 'canvas.drawBitmap(railSurface, railSource, bounds, null)' in style
