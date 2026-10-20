"""Execute production modal scale ownership with controllable animation and detach events."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/UiDialogs.java').read_text(encoding='utf-8')

def block(marker):
    start = source.index(marker)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end].replace('android.R.id.content', '0').replace(
        'android.view.animation.DecelerateInterpolator', 'DecelerateInterpolator').replace(
        'android.graphics.Shader.TileMode.CLAMP', '0')

methods = '\n'.join(block(marker) for marker in (
    'private static final class DialogScaleState', 'private static void beginDialogScale(',
    'private static void endDialogScale(', 'private static void finishDialogScale('))
harness = r'''
import java.util.*;
public class DialogScaleCheck {
 static class Activity {final View content=new View();boolean finishing,destroyed;
  boolean isFinishing(){return finishing;}boolean isDestroyed(){return destroyed;}View findViewById(int id){return content;}}
 static class View {
  float x=1,y=1;Object effect;final Animator animator=new Animator(this);final List<OnAttachStateChangeListener> listeners=new ArrayList<>();
  interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
  float getScaleX(){return x;}float getScaleY(){return y;}void setScaleX(float f){x=f;}void setScaleY(float f){y=f;}
  Animator animate(){return animator;}void setRenderEffect(Object e){effect=e;}
  void addOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.add(l);}void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){listeners.remove(l);}
  void detach(){for(var l:new ArrayList<>(listeners))l.onViewDetachedFromWindow(this);}}
 static class Animator {final View view;float x,y;Runnable end;Animator(View v){view=v;}
  Animator scaleX(float f){x=f;return this;}Animator scaleY(float f){y=f;return this;}
  Animator setDuration(long ms){return this;}Animator setInterpolator(Object i){return this;}void start(){}
  Animator withEndAction(Runnable r){end=r;return this;}void cancel(){end=null;}
  void frame(float fraction){view.x+=(x-view.x)*fraction;view.y+=(y-view.y)*fraction;}
  void finish(){view.x=x;view.y=y;Runnable callback=end;end=null;if(callback!=null)callback.run();}}
 static class ValueAnimator {static boolean enabled=true;static boolean areAnimatorsEnabled(){return enabled;}}
 static class Build {static class VERSION {static int SDK_INT=36;}}
 static class ThemeStore {static boolean acrylicEnabled(Activity a){return true;}}
 static class RenderEffect {static Object createBlurEffect(int x,int y,int mode){return new Object();}}
 static class DecelerateInterpolator {}
 static int dp(View v,int value){return value;}
 static final long DIALOG_BACKGROUND_SCALE_IN_DURATION_MS=360;
 static final Map<Activity,DialogScaleState> DIALOG_SCALES=new WeakHashMap<>();
 __METHODS__
 static int checks;static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
 static void restored(Activity a,float x,float y){check(Math.abs(a.content.x-x)<.00001&&Math.abs(a.content.y-y)<.00001,"original scale restored");check(a.content.effect==null&&!DIALOG_SCALES.containsKey(a)&&a.content.listeners.isEmpty(),"state/effect/listener released");}
 public static void main(String[] args){
  Activity a=new Activity();a.content.x=.93f;a.content.y=1.1f;
  beginDialogScale(a);a.content.animator.frame(.7f);endDialogScale(a);a.content.animator.frame(.3f);
  check(DIALOG_SCALES.get(a).count==0,"closing retains original baseline");
  beginDialogScale(a);check(Math.abs(a.content.animator.x-.93f*1.04f)<.00001,"reversal uses original x");check(Math.abs(a.content.animator.y-1.1f*1.04f)<.00001,"reversal uses original y");
  endDialogScale(a);a.content.animator.finish();restored(a,.93f,1.1f);
  beginDialogScale(a);beginDialogScale(a);check(DIALOG_SCALES.get(a).count==2,"nested count");endDialogScale(a);check(DIALOG_SCALES.get(a).count==1&&a.content.effect!=null,"nested dismissal retains background");endDialogScale(a);a.content.animator.finish();restored(a,.93f,1.1f);
  ValueAnimator.enabled=false;beginDialogScale(a);endDialogScale(a);restored(a,.93f,1.1f);ValueAnimator.enabled=true;
  beginDialogScale(a);a.finishing=true;endDialogScale(a);restored(a,.93f,1.1f);a.finishing=false;
  beginDialogScale(a);a.content.animator.frame(.5f);endDialogScale(a);a.content.detach();restored(a,.93f,1.1f);
  beginDialogScale(a);a.content.animator.frame(.5f);a.content.detach();restored(a,.93f,1.1f);
  System.out.println("PASS: "+checks+" production dialog-scale checks; reversal/nesting/reduced motion/finish/detach");
 }
}
'''.replace('__METHODS__', methods)
out = root / 'build/dialog-scale-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'DialogScaleCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java/'javac.exe'), '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java/'java.exe'), '-cp', str(out), 'DialogScaleCheck'], check=True)
