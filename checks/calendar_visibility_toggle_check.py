"""Execute the production icon toggle with manually advanced animator transport."""
from pathlib import Path
import os
import re
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/CalendarVisibilityToggle.java').read_text(encoding='utf-8')
assert 'UiStyle.pressable(this)' in source and 'setForeground(' not in source
assert 'drawCircle' not in source and 'checkedBackground' not in source
source = re.sub(r'^(package |import ).*;\s*', '', source, flags=re.M)
source = source.replace('android.animation.ArgbEvaluator', 'ArgbEvaluator')
harness = r'''
class Context {Drawable icon=new Drawable();Drawable getDrawable(int id){return icon;}}
class R {static class drawable {static int ic_week_calendar=1;}}
class Color {static int TRANSPARENT=0;}
class Resources {static float density=1;static class Metrics {float density=Resources.density;}Metrics getDisplayMetrics(){return new Metrics();}}
class Drawable {int tint,left,top,right,bottom;Drawable mutate(){return this;}void setTint(int color){tint=color;}
 void setBounds(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}void draw(Canvas canvas){}}
class RippleDrawable extends Drawable {}
class Canvas {}
class CheckBox {Context context;boolean checked,attached=true,broadcasting;java.util.function.Consumer<Boolean> listener;Drawable foreground;int invalidations,changes,width=48,height=48;
 CheckBox(Context context){this.context=context;}Context getContext(){return context;}Resources getResources(){return new Resources();}
 int getWidth(){return width;}int getHeight(){return height;}void setButtonDrawable(Drawable drawable){}void setBackgroundColor(int color){}
 void setForeground(Drawable drawable){foreground=drawable;}boolean isChecked(){return checked;}
 void setChecked(boolean value){if(value!=checked){checked=value;changes++;if(!broadcasting&&listener!=null){broadcasting=true;try{listener.accept(value);}finally{broadcasting=false;}}}}boolean isAttachedToWindow(){return attached;}
 void invalidate(){invalidations++;}protected void onDraw(Canvas canvas){}protected void onDetachedFromWindow(){attached=false;}}
class UiStyle {static class Palette {int primary=0xff44aaee,muted=0xff667788;}static Palette colors(Context context){return new Palette();}static void pressable(CheckBox view){view.setForeground(new RippleDrawable());}}
class UiMotion {static long ENTER=240;static boolean enabled=true;static Object SETTLE=new Object();static boolean isEnabled(){return enabled;}}
class ArgbEvaluator {Object evaluate(float fraction,Object start,Object end){int a=(Integer)start,b=(Integer)end,result=0;for(int shift=0;shift<32;shift+=8){int x=(a>>>shift)&255,y=(b>>>shift)&255;result|=Math.round(x+(y-x)*fraction)<<shift;}return result;}}
class ValueAnimator {
 interface Update {void update(ValueAnimator value);}static ValueAnimator last;static int starts;float from,to,value;long duration;boolean cancelled;Update listener;
 static ValueAnimator ofFloat(float from,float to){var a=new ValueAnimator();a.from=a.value=from;a.to=to;last=a;return a;}
 void setDuration(long value){duration=value;}void setInterpolator(Object value){}void addUpdateListener(Update value){listener=value;}
 void start(){starts++;}void cancel(){cancelled=true;}Object getAnimatedValue(){return value;}
 void advance(float fraction){if(!cancelled){value=from+(to-from)*fraction;listener.update(this);}}
}
__SOURCE__
public class CalendarVisibilityToggleCheck {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void near(float actual,float expected,String why){check(Math.abs(actual-expected)<.0001f,why+" got "+actual);}
 static float fraction(CalendarVisibilityToggle view)throws Exception{var field=CalendarVisibilityToggle.class.getDeclaredField("checkedFraction");field.setAccessible(true);return field.getFloat(view);}
 public static void main(String[] args)throws Exception {
  Context context=new Context();CalendarVisibilityToggle view=new CalendarVisibilityToggle(context);Canvas canvas=new Canvas();
  check(view.foreground instanceof RippleDrawable,"Press feedback is installed by UiStyle");
  view.onDraw(canvas);check(context.icon.tint==UiStyle.colors(context).muted,"Off appearance has muted icon");
  near((context.icon.left+context.icon.right)/2f,24,"Icon centered horizontally");near((context.icon.top+context.icon.bottom)/2f,24,"Icon centered vertically");
  view.width=view.height=56;view.onDraw(canvas);near((context.icon.left+context.icon.right)/2f,28,"Larger target keeps icon centered");view.width=view.height=48;
  Resources.density=2.625f;CalendarVisibilityToggle scaled=new CalendarVisibilityToggle(new Context());scaled.width=scaled.height=126;scaled.onDraw(canvas);
  check(Math.abs((scaled.getContext().icon.left+scaled.getContext().icon.right)/2f-63)<=.5f,"Fractional density centers icon within half a pixel");Resources.density=1;
  view.setChecked(true);ValueAnimator on=ValueAnimator.last;check(view.isChecked()&&view.changes==1,"Native checked state updates immediately");
  near(fraction(view),0,"On transition starts from current fraction");check(on.duration==UiMotion.ENTER,"On transition uses enter duration");
  on.advance(.5f);near(fraction(view),.5f,"On transition supplies intermediate frame");view.onDraw(canvas);
  check(context.icon.tint!=UiStyle.colors(context).muted&&context.icon.tint!=UiStyle.colors(context).primary,"Intermediate tint animates continuously");
  check(context.icon.right-context.icon.left>24&&(context.icon.left+context.icon.right)/2==24,"Icon pulse expands around its center");
  on.advance(1);near(fraction(view),1,"On transition reaches checked appearance");view.onDraw(canvas);
  check(context.icon.tint==UiStyle.colors(context).primary&&context.icon.right-context.icon.left==24,"Checked appearance restores base icon size");
  int starts=ValueAnimator.starts;view.setChecked(true);check(ValueAnimator.starts==starts,"Same state does not restart animation");
  view.setChecked(false);ValueAnimator off=ValueAnimator.last;check(on.cancelled&&!view.isChecked(),"Off cancels old animation and updates native state");
  off.advance(.5f);near(fraction(view),.5f,"Off transition is continuous");off.advance(1);near(fraction(view),0,"Off transition completes");
  view.setChecked(true);ValueAnimator interrupted=ValueAnimator.last;interrupted.advance(.4f);view.setChecked(false);ValueAnimator reverse=ValueAnimator.last;
  check(interrupted.cancelled,"Rapid reversal cancels previous animator");near(reverse.from,.4f,"Reversal begins at displayed fraction");near(fraction(view),.4f,"Reversal does not jump");
  interrupted.advance(1);near(fraction(view),.4f,"Cancelled animator cannot overwrite reversal");reverse.advance(.5f);near(fraction(view),.2f,"Reversal proceeds toward off");
  view.setChecked(true);near(ValueAnimator.last.from,.2f,"Second reversal also preserves current fraction");ValueAnimator.last.advance(1);near(fraction(view),1,"Rapid reversal final state settles");
  UiMotion.enabled=false;starts=ValueAnimator.starts;view.setChecked(false);near(fraction(view),0,"Disabled animation switches off immediately");view.setChecked(true);near(fraction(view),1,"Disabled animation switches on immediately");check(ValueAnimator.starts==starts,"Disabled animation creates no running transition");
  UiMotion.enabled=true;view.setChecked(false);ValueAnimator detach=ValueAnimator.last;detach.advance(.25f);view.onDetachedFromWindow();check(detach.cancelled,"Detach cancels transition");near(fraction(view),0,"Detach settles actual native off state");
  starts=ValueAnimator.starts;view.setChecked(true);near(fraction(view),1,"Detached toggle initializes checked appearance immediately");check(ValueAnimator.starts==starts,"Detached initialization does not animate");
  CalendarVisibilityToggle rejected=new CalendarVisibilityToggle(new Context());
  rejected.listener=enabled->{if(enabled)rejected.setChecked(false);};
  rejected.setChecked(true);ValueAnimator.last.advance(1);
  check(!rejected.isChecked(),"Permission rejection restores native off state");
  near(fraction(rejected),0,"Reentrant rejection animates toward final native state");
  rejected.attached=false;rejected.setChecked(true);
  near(fraction(rejected),0,"Detached reentrant rejection also retains off appearance");
  System.out.println("Calendar visibility toggle checks passed: "+checks+" (production view with animator/drawing transport; not Android rendering)");
 }
}
'''.replace('__SOURCE__', source)
out = root / 'build/calendar-visibility-toggle-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'CalendarVisibilityToggleCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'CalendarVisibilityToggleCheck'], check=True)
