"""Exercise shared press installation with View/animator transport doubles."""
import os
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/PressFeedback.java').read_text(encoding='utf-8')
source = re.sub(r'^(package |import ).*;\s*', '', source, flags=re.M)
source = source.replace('android.view.ViewParent', 'ViewParent').replace('android.R.attr.', 'Attr.')
harness = r'''
import java.util.*;
interface ViewParent { ViewParent getParent(); }
class Attr { static int state_enabled=1,state_pressed=2; }
class ViewTreeObserver {
 interface OnGlobalLayoutListener { void onGlobalLayout(); }
 List<OnGlobalLayoutListener> listeners=new ArrayList<>();
 boolean isAlive(){return true;}void addOnGlobalLayoutListener(OnGlobalLayoutListener l){listeners.add(l);}
 void removeOnGlobalLayoutListener(OnGlobalLayoutListener l){listeners.remove(l);}
 void layout(){for(var l:new ArrayList<>(listeners))l.onGlobalLayout();}
}
class View implements ViewParent {
 static int SCALE_X=1,SCALE_Y=2;ViewParent parent;boolean clickable,longClickable;
 StateListAnimator states;int installs;ViewTreeObserver observer=new ViewTreeObserver();List<OnAttachStateChangeListener> attach=new ArrayList<>();
 interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 public ViewParent getParent(){return parent;}boolean isClickable(){return clickable;}boolean isLongClickable(){return longClickable;}
 void setStateListAnimator(StateListAnimator a){states=a;installs++;}ViewTreeObserver getViewTreeObserver(){return observer;}
 void addOnAttachStateChangeListener(OnAttachStateChangeListener l){attach.add(l);}void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){attach.remove(l);}
 void detach(){for(var l:new ArrayList<>(attach))l.onViewDetachedFromWindow(this);}
}
class ViewGroup extends View {List<View> children=new ArrayList<>();int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}void add(View v){children.add(v);v.parent=this;}}
class DockNavigationLayout extends ViewGroup {}class ScrollView extends ViewGroup {}class HorizontalScrollView extends ViewGroup {}
class ObjectAnimator {float value;int property;static ObjectAnimator ofFloat(View v,int p,float value){var a=new ObjectAnimator();a.value=value;a.property=p;return a;}}
class AnimatorSet {ObjectAnimator[] pair;long duration;void playTogether(ObjectAnimator... a){pair=a;}void setDuration(long d){duration=d;}void setInterpolator(Object o){}}
class StateListAnimator {List<int[]> keys=new ArrayList<>();List<AnimatorSet> animations=new ArrayList<>();void addState(int[] k,AnimatorSet a){keys.add(k);animations.add(a);}}
class UiMotion {static boolean enabled=true;static long PRESS=90,RELEASE=160;static Object SETTLE=new Object();static boolean isEnabled(){return enabled;}}
__SOURCE__
public class PressFeedbackCheck {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("press check "+checks);}
 public static void main(String[] args){
  ViewGroup root=new ViewGroup();View button=new View();button.clickable=true;root.add(button);
  View longPress=new View();longPress.longClickable=true;root.add(longPress);View label=new View();root.add(label);
  DockNavigationLayout dock=new DockNavigationLayout();dock.clickable=true;View tab=new View();tab.clickable=true;dock.add(tab);root.add(dock);
  ScrollView scroll=new ScrollView();scroll.clickable=true;root.add(scroll);View nested=new View();nested.clickable=true;scroll.add(nested);
  PressFeedback.install(root);check(button.installs==1&&longPress.installs==1&&nested.installs==1);
  check(label.installs==0&&dock.installs==0&&tab.installs==0&&scroll.installs==0);
  check(Arrays.equals(button.states.keys.get(0),new int[]{1,2}));check(button.states.keys.get(1).length==0);
  check(button.states.animations.get(0).pair.length==2);check(button.states.animations.get(0).pair[0].value==1.05f);
  check(button.states.animations.get(1).pair[0].value==1f);check(button.states.animations.get(0).duration==90&&button.states.animations.get(1).duration==160);
  PressFeedback.install(root);root.observer.layout();check(root.observer.listeners.size()==1&&button.installs==1);
  View dynamic=new View();dynamic.clickable=true;root.add(dynamic);root.observer.layout();check(dynamic.installs==1);
  PressFeedback.apply(tab);check(tab.installs==0);root.detach();check(root.observer.listeners.isEmpty()&&root.attach.isEmpty());
  PressFeedback.install(root);check(root.observer.listeners.size()==1&&button.installs==1);
  UiMotion.enabled=false;View reduced=new View();PressFeedback.apply(reduced);check(reduced.states.animations.get(0).duration==0&&reduced.states.animations.get(1).duration==0);
  System.out.println("Press feedback passed: "+checks+" (production controller with View/animator doubles)");
 }
}
'''.replace('__SOURCE__', source)
out = root / 'build/press-feedback-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'PressFeedbackCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'PressFeedbackCheck'], check=True)
