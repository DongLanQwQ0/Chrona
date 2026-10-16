"""Execute the production circular toggle with manually advanced animator transport."""
from pathlib import Path
import os
import re
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/CalendarVisibilityToggle.java').read_text(encoding='utf-8')
source = re.sub(r'^(package |import ).*;\s*', '', source, flags=re.M)
source = source.replace('android.animation.ArgbEvaluator', 'ArgbEvaluator')
harness = r'''
class Context {Drawable icon=new Drawable();Drawable getDrawable(int id){return icon;}}
class R {static class drawable {static int ic_week_calendar=1;}}
class Color {static int TRANSPARENT=0,WHITE=-1;}
class ColorStateList {int color;static ColorStateList valueOf(int color){var v=new ColorStateList();v.color=color;return v;}}
class Resources {static class Metrics {float density=1;}Metrics getDisplayMetrics(){return new Metrics();}}
class Drawable {int tint,left,top,right,bottom;Drawable mutate(){return this;}void setTint(int color){tint=color;}
 void setBounds(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}void draw(Canvas canvas){}}
class GradientDrawable extends Drawable {static final int OVAL=1;int shape,color;void setShape(int value){shape=value;}void setColor(int value){color=value;}}
class RippleDrawable extends Drawable {Drawable mask;RippleDrawable(ColorStateList tint,Drawable content,Drawable mask){this.mask=mask;}}
class Paint {static final int ANTI_ALIAS_FLAG=1;int color;Paint(int flags){}void setColor(int value){color=value;}}
class Canvas {float x,y,radius;int color;void drawCircle(float x,float y,float radius,Paint paint){this.x=x;this.y=y;this.radius=radius;color=paint.color;}}
class CheckBox {Context context;boolean checked,attached=true;Drawable foreground;int invalidations,changes;
 CheckBox(Context context){this.context=context;}Context getContext(){return context;}Resources getResources(){return new Resources();}
 int getWidth(){return 44;}int getHeight(){return 44;}void setButtonDrawable(Drawable drawable){}void setBackgroundColor(int color){}
 void setForeground(Drawable drawable){foreground=drawable;}boolean isChecked(){return checked;}
 void setChecked(boolean value){if(value!=checked){checked=value;changes++;}}boolean isAttachedToWindow(){return attached;}
 void invalidate(){invalidations++;}protected void onDraw(Canvas canvas){}protected void onDetachedFromWindow(){attached=false;}}
class UiStyle {static class Palette {int primary=0xff44aaee,muted=0xff667788;}static Palette colors(Context context){return new Palette();}static void pressable(CheckBox view){}}
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
  check(view.foreground instanceof RippleDrawable,"Circular surface retains native ripple");
  check(((RippleDrawable)view.foreground).mask instanceof GradientDrawable&&((GradientDrawable)((RippleDrawable)view.foreground).mask).shape==GradientDrawable.OVAL,"Ripple mask is circular");
  view.onDraw(canvas);check(canvas.color>>>24==0&&context.icon.tint==UiStyle.colors(context).muted,"Off appearance has muted icon and transparent fill");
  near(canvas.x,22,"Surface centered horizontally");near(canvas.y,22,"Surface centered vertically");near(canvas.radius,20,"Surface circle radius");
  view.setChecked(true);ValueAnimator on=ValueAnimator.last;check(view.isChecked()&&view.changes==1,"Native checked state updates immediately");
  near(fraction(view),0,"On transition starts from current fraction");check(on.duration==UiMotion.ENTER,"On transition uses enter duration");
  on.advance(.5f);near(fraction(view),.5f,"On transition supplies intermediate frame");view.onDraw(canvas);
  check(canvas.color>>>24==16&&context.icon.tint!=UiStyle.colors(context).muted&&context.icon.tint!=UiStyle.colors(context).primary,"Intermediate fill and tint animate together");
  check(context.icon.right-context.icon.left>24&&(context.icon.left+context.icon.right)/2==22,"Icon pulse expands around its center");
  on.advance(1);near(fraction(view),1,"On transition reaches checked appearance");view.onDraw(canvas);
  check(canvas.color>>>24==32&&context.icon.tint==UiStyle.colors(context).primary&&context.icon.right-context.icon.left==24,"Checked appearance restores base icon size");
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
