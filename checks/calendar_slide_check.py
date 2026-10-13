"""Run production calendar transition methods with layout/animation transport doubles."""
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/CalendarOverviewActivity.java').read_text(encoding='utf-8')
assert 'body.setAlpha(' not in source, 'Calendar page transitions must not use alpha compositing'
def method(name):
    start = source.index('    private void ' + name + '(')
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]
methods = ''.join(method(name) for name in ('finishBodyMotion', 'prepareBodySlide', 'startBodySlide'))
methods = methods.replace('android.animation.ValueAnimator', 'ValueAnimator').replace('android.animation.AnimatorListenerAdapter', 'Adapter').replace('android.animation.Animator', 'Animator')
runner = r'''
package com.donglan.chrona;
import java.util.*;
public class CalendarSlideCheck {
 static final List<String> order=new ArrayList<>();static final ArrayDeque<Runnable> jobs=new ArrayDeque<>();
 interface PreDraw { boolean onPreDraw(); }
 static class Observer {List<PreDraw> listeners=new ArrayList<>();boolean isAlive(){return true;}void addOnPreDrawListener(PreDraw l){listeners.add(l);}void removeOnPreDrawListener(PreDraw l){listeners.remove(l);}void ready(){for(PreDraw l:new ArrayList<>(listeners))l.onPreDraw();}}
 static class View {static final int VISIBLE=0,INVISIBLE=4;float x;boolean focus,layoutRequested,requestOnScroll;int scroll,visibility=INVISIBLE;Observer observer=new Observer();int getWidth(){return 300;}boolean hasFocus(){return focus;}boolean isLayoutRequested(){return layoutRequested;}void setTranslationX(float v){x=v;}void setVisibility(int v){visibility=v;}void post(Runnable r){jobs.add(r);}Observer getViewTreeObserver(){return observer;}void invalidate(){}void scrollTo(int x,int y){scroll=y;order.add("scroll");if(requestOnScroll)activeBody.layoutRequested=true;}void removeView(View view){check(view.visibility==INVISIBLE);}}
 static View activeBody;
 static class Animator {}
 static class Adapter {public void onAnimationEnd(Animator a){}}
 static class ValueAnimator extends Animator {interface Update {void run(ValueAnimator v);}float value;Update update;Adapter end;void setDuration(long d){}void setInterpolator(Object o){}static ValueAnimator ofFloat(float a,float b){return new ValueAnimator();}void addUpdateListener(Update u){update=u;}void addListener(Adapter e){end=e;}Object getAnimatedValue(){return value;}void start(){order.add("start");tick(0);}void tick(float v){value=v;update.run(this);if(v==1&&end!=null)end.onAnimationEnd(this);}void cancel(){if(end!=null)end.onAnimationEnd(this);}}
 static class UiMotion {static boolean enabled=true;static long ENTER=220;static Object SETTLE=new Object();static boolean isEnabled(){return enabled;}}
 final View body=new View(),bodyViewport=new View(),verticalViewport=new View(),horizontalViewport=new View(),handler=new View();
 View outgoingBody=new View();ValueAnimator bodySlide;Observer bodyObserver;PreDraw bodyPreDraw;
 int presentationGeneration=1;boolean bodyTransitionRunning=true,pendingBodyRender,resumed=true;
 String renderedKey="month";Map<String,int[]> positions=new HashMap<>();Runnable renderPendingBody=()->{};int cancelled;
 void cancelCalendarBuild(){cancelled++;}
 __METHODS__
 static int checks;static void check(boolean value){checks++;if(!value)throw new AssertionError();}
 static void drain(){while(!jobs.isEmpty())jobs.removeFirst().run();}
 public static void main(String[] args){
  CalendarSlideCheck c=new CalendarSlideCheck();c.positions.put("month",new int[]{21,90});c.prepareBodySlide(1,1);
  check(c.bodySlide==null);drain();check(c.bodySlide==null&&c.body.observer.listeners.size()==1);
  c.body.observer.ready();check(c.bodySlide!=null&&c.body.x==300&&c.outgoingBody.x==0);
  check(c.verticalViewport.scroll==90&&order.indexOf("scroll")<order.indexOf("start"));
  ValueAnimator moving=c.bodySlide;moving.tick(.35f);check(Math.abs(c.outgoingBody.x+300-c.body.x)<.001f);
  check(c.body.visibility==View.VISIBLE);moving.tick(1);check(c.body.x==0&&c.outgoingBody==null&&!c.bodyTransitionRunning);
  c=new CalendarSlideCheck();activeBody=c.body;c.positions.put("month",new int[]{21,90});c.body.layoutRequested=true;c.verticalViewport.requestOnScroll=true;c.prepareBodySlide(1,1);drain();
  c.body.observer.ready();check(c.bodySlide==null&&c.body.visibility==View.INVISIBLE&&c.verticalViewport.scroll==0);
  c.body.layoutRequested=false;c.body.observer.ready();check(c.bodySlide==null&&c.body.visibility==View.INVISIBLE&&c.verticalViewport.scroll==90);
  c.body.layoutRequested=false;c.body.observer.ready();check(c.bodySlide!=null&&c.body.visibility==View.VISIBLE);c.finishBodyMotion();
  for(int direction:new int[]{-1,1})for(int i=0;i<=100;i++){float f=i/100f,inside=CalendarSlideMotion.incoming(300,direction,f),outside=CalendarSlideMotion.outgoing(300,direction,f);check(Math.abs((direction==1?outside+300-inside:inside+300-outside))<.001f);}
  c=new CalendarSlideCheck();c.prepareBodySlide(1,-1);drain();PreDraw late=c.bodyPreDraw;c.finishBodyMotion();late.onPreDraw();c.body.observer.ready();check(c.bodySlide==null&&c.body.observer.listeners.isEmpty());
  c=new CalendarSlideCheck();c.prepareBodySlide(1,1);c.finishBodyMotion();drain();check(c.body.observer.listeners.isEmpty());
  c=new CalendarSlideCheck();UiMotion.enabled=false;c.prepareBodySlide(1,1);drain();c.body.observer.ready();check(c.bodySlide==null&&c.outgoingBody==null&&c.body.x==0);UiMotion.enabled=true;
  System.out.println("Calendar slide checks passed: "+checks+" (production methods, layout and animator doubles)");
 }
}
'''.replace('__METHODS__', methods)
out = root / 'build/calendar-slide-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'CalendarSlideCheck.java'
file.write_text(runner, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out), str(file), str(root / 'app/src/main/java/com/donglan/chrona/CalendarSlideMotion.java')], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.CalendarSlideCheck'], check=True)
