"""Exercise shared press installation with View/animator transport doubles."""
import os
import re
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/PressFeedback.java').read_text(encoding='utf-8')
source = re.sub(r'^(package |import ).*;\s*', '', source, flags=re.M)
source = source.replace('android.view.ViewParent', 'ViewParent').replace('android.R.attr.', 'Attr.').replace('android.graphics.Rect', 'Rect')
style = (root / 'app/src/main/java/com/donglan/chrona/UiStyle.java').read_text(encoding='utf-8')
start = style.index('    static void pressable(View view) {')
end = style.index('\n    }', start) + len('\n    }')
pressable = style[start:end]
surface = (root / 'app/src/main/java/com/donglan/chrona/UiSurfaces.java').read_text(encoding='utf-8')
start = surface.index('    static void installRipple(View view) {')
end = surface.index('\n    }', start) + len('\n    }')
ripple = surface[start:end]
harness = r'''
import java.util.*;
interface ViewParent { ViewParent getParent(); }
class Attr { static int state_enabled=1,state_pressed=2; }
class Rect {int left,top,right,bottom;Rect(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}}
class ViewTreeObserver {
 interface OnGlobalLayoutListener { void onGlobalLayout(); }
 interface OnGlobalFocusChangeListener {void onGlobalFocusChanged(View oldFocus,View newFocus);}
 List<OnGlobalLayoutListener> listeners=new ArrayList<>();
 List<OnGlobalFocusChangeListener> focusListeners=new ArrayList<>();
 boolean isAlive(){return true;}void addOnGlobalLayoutListener(OnGlobalLayoutListener l){listeners.add(l);}
 void removeOnGlobalLayoutListener(OnGlobalLayoutListener l){listeners.remove(l);}
 void addOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l){focusListeners.add(l);}
 void removeOnGlobalFocusChangeListener(OnGlobalFocusChangeListener l){focusListeners.remove(l);}
 void layout(){for(var l:new ArrayList<>(listeners))l.onGlobalLayout();}
}
class View implements ViewParent {
 static int SCALE_X=1,SCALE_Y=2;ViewParent parent;boolean clickable,longClickable,clipOutline,pressed;float sx=1,sy=1;
 StateListAnimator states;Object foreground;int installs;ViewTreeObserver observer=new ViewTreeObserver();List<OnAttachStateChangeListener> attach=new ArrayList<>();
 int width=320,height=600,clipUpdates;Rect clipBounds;List<OnLayoutChangeListener> layouts=new ArrayList<>();
 interface OnLayoutChangeListener {void onLayoutChange(View v,int l,int t,int r,int b,int ol,int ot,int or,int ob);}
 int getWidth(){return width;}int getHeight(){return height;}void setClipBounds(Rect r){clipBounds=r;clipUpdates++;}void addOnLayoutChangeListener(OnLayoutChangeListener l){layouts.add(l);}void layout(int w,int h){width=w;height=h;for(var l:new ArrayList<>(layouts))l.onLayoutChange(this,0,0,w,h,0,0,0,0);}
 interface OnAttachStateChangeListener {void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 public ViewParent getParent(){return parent;}boolean isClickable(){return clickable;}boolean isLongClickable(){return longClickable;}
 void setStateListAnimator(StateListAnimator a){states=a;installs++;}ViewTreeObserver getViewTreeObserver(){return observer;}
 boolean getClipToOutline(){return clipOutline;}void setScaleX(float f){sx=f;}void setScaleY(float f){sy=f;}
 Object getContext(){return this;}Object getForeground(){return foreground;}void setForeground(Object value){foreground=value;}
 View focused;View findFocus(){return focused;}
 void setPressed(boolean p){pressed=p;if(parent instanceof ViewGroup g&&g.addStates)g.pressed=p;}
 void addOnAttachStateChangeListener(OnAttachStateChangeListener l){attach.add(l);}void removeOnAttachStateChangeListener(OnAttachStateChangeListener l){attach.remove(l);}
 void detach(){for(var l:new ArrayList<>(attach))l.onViewDetachedFromWindow(this);}
}
class ViewGroup extends View {boolean clipChildren=true,clipPadding=true,addStates;void setAddStatesFromChildren(boolean b){addStates=b;}void setClipChildren(boolean b){clipChildren=b;}void setClipToPadding(boolean b){clipPadding=b;}List<View> children=new ArrayList<>();int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}void add(View v){children.add(v);v.parent=this;}}
class DockNavigationLayout extends ViewGroup {}class ScrollView extends ViewGroup {}class HorizontalScrollView extends ViewGroup {}class SwipePagerLayout extends ViewGroup {}
class ObjectAnimator {float value;int property;static ObjectAnimator ofFloat(View v,int p,float value){var a=new ObjectAnimator();a.value=value;a.property=p;return a;}}
class AnimatorSet {ObjectAnimator[] pair;long duration;void playTogether(ObjectAnimator... a){pair=a;}void setDuration(long d){duration=d;}void setInterpolator(Object o){}}
class StateListAnimator {List<int[]> keys=new ArrayList<>();List<AnimatorSet> animations=new ArrayList<>();void addState(int[] k,AnimatorSet a){keys.add(k);animations.add(a);}}
class UiMotion {static boolean enabled=true;static long PRESS=90,RELEASE=160;static Object SETTLE=new Object();static boolean isEnabled(){return enabled;}}
class InteractionLayers {static int PRESSED=3,refreshes;static View focus;static void refresh(){refreshes++;}static void focus(View oldFocus,View newFocus){focus=newFocus;}}
class RippleDrawable {Object mask;RippleDrawable(Object tint,Object content,Object mask){this.mask=mask;}}
class ControlHighlightMask {final View host;ControlHighlightMask(View view){host=view;}}
class ColorStateList {static Object valueOf(int value){return value;}}
class Color {static int WHITE=1,TRANSPARENT=0;}
class UiStyle {static boolean isCardSurface(View view){return view instanceof Card;}static int RADIUS_CARD=22;static class Palette {int primary=1;}static Palette colors(Object context){return new Palette();}static int alpha(int color,int value){return value;}static Object shape(View v,int color,int radius,int stroke){return radius;}
__PRESSABLE__
}
__SOURCE__
class UiSurfaces {static boolean isCardSurface(View view){return view instanceof Card;}static UiStyle.Palette colors(Object c){return UiStyle.colors(c);}static int alpha(int c,int v){return UiStyle.alpha(c,v);}
__RIPPLE__
}
class Card extends ViewGroup {}
public class PressFeedbackCheck {
 static int checks;static void check(boolean ok){checks++;if(!ok)throw new AssertionError("press check "+checks);}
 public static void main(String[] args){
  ViewGroup root=new ViewGroup();View button=new View();button.clickable=true;root.add(button);
  Card card=new Card();card.clickable=true;root.add(card);PressFeedback.apply(card);
  check(card.states.animations.get(0).pair[0].value==1.08f);
  View longPress=new View();longPress.longClickable=true;root.add(longPress);View label=new View();root.add(label);
  DockNavigationLayout dock=new DockNavigationLayout();dock.clickable=true;View tab=new View();tab.clickable=true;dock.add(tab);root.add(dock);
  ScrollView scroll=new ScrollView();scroll.clickable=true;root.add(scroll);View nested=new View();nested.clickable=true;scroll.add(nested);
  PressFeedback.install(root);check(button.installs==1&&longPress.installs==1&&nested.installs==1);
  check(label.installs==0&&dock.installs==0&&tab.installs==0&&scroll.installs==0);
  check(Arrays.equals(button.states.keys.get(0),new int[]{1,2}));check(button.states.keys.get(1).length==0);
  check(button.states.animations.get(0).pair.length==3);check(button.states.animations.get(0).pair[0].value==1.05f);
  check(button.states.animations.get(0).pair[0].property==View.SCALE_X&&button.states.animations.get(0).pair[1].property==View.SCALE_Y&&button.states.animations.get(0).pair[1].value==1.05f);
  check(button.states.animations.get(0).pair[2].property==InteractionLayers.PRESSED&&button.states.animations.get(0).pair[2].value==1f&&button.states.animations.get(1).pair[2].value==0f);
  check(button.states.animations.get(1).pair[0].value==1f);check(button.states.animations.get(0).duration==90&&button.states.animations.get(1).duration==160);
  PressFeedback.install(root);root.observer.layout();check(root.observer.listeners.size()==1&&button.installs==1);
  check(root.observer.focusListeners.size()==1&&InteractionLayers.refreshes==1);root.focused=button;root.observer.focusListeners.get(0).onGlobalFocusChanged(null,button);check(InteractionLayers.focus==button);
  View dynamic=new View();dynamic.clickable=true;root.add(dynamic);root.observer.layout();check(dynamic.installs==1);
  PressFeedback.apply(tab);check(tab.installs==0);root.detach();check(root.observer.listeners.isEmpty()&&root.attach.isEmpty());
  check(root.observer.focusListeners.isEmpty()&&InteractionLayers.focus==null);
  PressFeedback.install(root);check(root.observer.listeners.size()==1&&button.installs==1);
  ScrollView viewport=new ScrollView();ViewGroup host=new ViewGroup(),rows=new ViewGroup();View wideCard=new View();wideCard.clickable=true;PressFeedback.apply(wideCard);rows.add(wideCard);host.add(rows);viewport.add(host);PressFeedback.install(viewport);
  check(!rows.clipChildren&&!rows.clipPadding&&!host.clipChildren&&!host.clipPadding);check(viewport.clipChildren&&viewport.clipPadding);
  ViewGroup rounded=new ViewGroup();rounded.clipOutline=true;View inner=new View();inner.clickable=true;rounded.add(inner);PressFeedback.install(rounded);check(rounded.clipChildren&&rounded.clipPadding);
  SwipePagerLayout pager=new SwipePagerLayout();ViewGroup page=new ViewGroup();View control=new View();control.clickable=true;page.add(control);pager.add(page);PressFeedback.install(pager);check(!page.clipChildren&&pager.clipChildren&&pager.clipPadding);
  ViewGroup plate=new ViewGroup();View gear=new View();gear.clickable=true;plate.add(gear);PressFeedback.apply(gear);PressFeedback.group(plate,1.10f);root.add(plate);root.observer.layout();
  check(plate.addStates&&plate.states.animations.get(0).pair[0].value==1.10f);check(gear.states==null&&gear.sx==1&&gear.sy==1);
  gear.setPressed(true);check(plate.pressed);gear.setPressed(false);check(!plate.pressed);root.observer.layout();check(gear.states==null&&plate.states.animations.get(0).pair[0].value==1.10f);
  View add=new View();add.clickable=true;PressFeedback.apply(add);PressFeedback.apply(add,1.20f);root.add(add);root.observer.layout();check(add.states.animations.get(0).pair[0].value==1.20f);
  View course=new View();RippleDrawable courseRipple=new RippleDrawable(null,null,6);course.foreground=courseRipple;UiStyle.pressable(course);check(course.foreground==courseRipple&&course.states!=null);
  View plain=new View();UiStyle.pressable(plain);check(plain.foreground instanceof RippleDrawable);check(((RippleDrawable)plain.foreground).mask instanceof ControlHighlightMask&&((ControlHighlightMask)((RippleDrawable)plain.foreground).mask).host==plain);Object ripple=plain.foreground;UiStyle.pressable(plain);check(plain.foreground==ripple);
  for(int extent:new int[]{128,320,640,1080,1440,2400,4000}){int edge=PressFeedback.edgeSpace(extent);double growth=(extent-2*edge)*.08/2;check(edge>=growth&&extent-edge+growth<=extent);}
  check(viewport.clipBounds.right==320&&viewport.clipBounds.bottom==600);viewport.layout(640,360);check(viewport.clipBounds.right==640&&viewport.clipBounds.bottom==360);int updates=viewport.clipUpdates;PressFeedback.protectViewport(viewport);check(viewport.clipUpdates==updates&&viewport.layouts.size()==1);
  ScrollView empty=new ScrollView();root.add(empty);root.observer.layout();check(empty.clipBounds!=null);ViewGroup pane=new ViewGroup();pane.width=pane.height=0;PressFeedback.protectViewport(pane);check(pane.clipBounds==null);pane.layout(250,400);check(pane.clipBounds.right==250&&pane.clipBounds.bottom==400);
  UiMotion.enabled=false;View reduced=new View();PressFeedback.apply(reduced);
  check(reduced.states.animations.get(0).duration==90&&reduced.states.animations.get(1).duration==160);
  UiMotion.enabled=true;PressFeedback.apply(reduced);
  check(reduced.installs==1&&reduced.states.animations.get(0).duration==90&&reduced.states.animations.get(1).duration==160);
  System.out.println("Press feedback passed: "+checks+" (production controller with View/animator doubles)");
 }
}
'''.replace('__SOURCE__', source).replace('__PRESSABLE__', pressable).replace('__RIPPLE__', ripple)
out = root / 'build/press-feedback-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'PressFeedbackCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'PressFeedbackCheck'], check=True)
