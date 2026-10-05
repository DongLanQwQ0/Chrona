"""Run production scroll/reveal and snapshot sizing code with deterministic View doubles."""
from pathlib import Path
import os
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/donglan/chrona'


def method(source, marker):
    start = source.index(marker)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


def production(name):
    source = (JAVA / name).read_text(encoding='utf-8')
    source = re.sub(r'^(package |import ).*;\s*', '', source, flags=re.M)
    return source.replace('android.graphics.Rect', 'Rect')


style = (JAVA / 'UiStyle.java').read_text(encoding='utf-8')
snapshot = method(style, 'private static Snapshot snapshot(View container)')
snapshot = snapshot.replace('android.graphics.Rect', 'Rect')
snapshot_type = method(style, 'private static final class Snapshot')
snapshot_type = snapshot_type.replace('android.graphics.Rect', 'Rect')
limits = '\n'.join(re.findall(r'    private static final int SNAPSHOT_MAX_.*;', style))
swap = '\n'.join(method(style, marker) for marker in (
    'public static void swap(View snapshotOf, View container, Runnable rebuild)',
    'private static boolean hostsSeveralChildren(ViewGroup host)',
    'private static void dropSwapGhosts(ViewGroup host)')).replace('android.widget.HorizontalScrollView', 'HorizontalScrollView')

harness = r'''
import java.util.*;
import java.util.function.Consumer;
class Interpolator {}
class PathInterpolator extends Interpolator { PathInterpolator(float a,float b,float c,float d){} }
class ValueAnimator { static boolean enabled=true; static boolean areAnimatorsEnabled(){return enabled;} }
class Rect {
 int left,top,right,bottom;
 void set(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 int width(){return right-left;} int height(){return bottom-top;}
 void offset(int x,int y){left+=x;right+=x;top+=y;bottom+=y;}
 boolean intersect(int l,int t,int r,int b){left=Math.max(l,left);top=Math.max(t,top);right=Math.min(r,right);bottom=Math.min(b,bottom);return right>left&&bottom>top;}
}
class Resources { Display getDisplayMetrics(){return new Display();} }
class Display { float density=1; }
class ViewTreeObserver {
 interface OnScrollChangedListener{void onScrollChanged();}
 interface OnGlobalLayoutListener{void onGlobalLayout();}
 List<OnScrollChangedListener> scroll=new ArrayList<>(); List<OnGlobalLayoutListener> layout=new ArrayList<>();
 boolean isAlive(){return true;}
 void addOnScrollChangedListener(OnScrollChangedListener x){scroll.add(x);}
 void removeOnScrollChangedListener(OnScrollChangedListener x){scroll.remove(x);}
 void addOnGlobalLayoutListener(OnGlobalLayoutListener x){layout.add(x);}
 void removeOnGlobalLayoutListener(OnGlobalLayoutListener x){layout.remove(x);}
}
class Animator {
 View view; int starts,cancels; float alpha,y; Runnable end; Consumer<ValueAnimator> update;
 Animator(View v){view=v;}
 Animator alpha(float x){alpha=x;return this;} Animator translationY(float x){y=x;return this;}
 Animator setStartDelay(long x){return this;} Animator setDuration(long x){return this;}
 Animator setInterpolator(Interpolator x){return this;} Animator withEndAction(Runnable x){end=x;return this;}
 Animator setUpdateListener(Consumer<ValueAnimator> x){update=x;return this;}
 void start(){starts++;} void cancel(){cancels++;end=null;}
 void finish(){view.alpha=alpha;view.y=y;Runnable done=end;end=null;if(done!=null)done.run();}
}
class View {
 interface OnAttachStateChangeListener{void onViewAttachedToWindow(View v);void onViewDetachedFromWindow(View v);}
 boolean attached=true,shown=true,visible=true,focused=false,accessibility=false;
 float alpha=1,y; int width=100,height=100,scrollX,scrollY,left,top; Object tag;
 Rect clip; ViewGroup parent; Animator animator=new Animator(this); Resources resources=new Resources();
 List<OnAttachStateChangeListener> listeners=new ArrayList<>(); List<Runnable> frames=new ArrayList<>();
 ViewTreeObserver tree=new ViewTreeObserver();
 boolean isAttachedToWindow(){return attached;} boolean isShown(){return shown;}
 boolean getGlobalVisibleRect(Rect r){r.set(0,0,width,height);return visible;}
 boolean getLocalVisibleRect(Rect r){if(!visible)return false;if(clip!=null)r.set(clip.left,clip.top,clip.right,clip.bottom);else r.set(scrollX,scrollY,scrollX+width,scrollY+height);return true;}
 boolean isFocused(){return focused;} boolean isAccessibilityFocused(){return accessibility;}
 boolean hasFocus(){return focused;} Animator animate(){return animator;}
 void setAlpha(float x){alpha=x;} void setTranslationY(float x){y=x;}
 Resources getResources(){return resources;} ViewTreeObserver getViewTreeObserver(){return tree;}
 void addOnAttachStateChangeListener(OnAttachStateChangeListener x){listeners.add(x);}
 void removeOnAttachStateChangeListener(OnAttachStateChangeListener x){listeners.remove(x);}
 void postOnAnimation(Runnable x){frames.add(x);} void removeCallbacks(Runnable x){frames.remove(x);}
 void frame(){List<Runnable> q=new ArrayList<>(frames);frames.clear();q.forEach(Runnable::run);}
 void detach(){attached=false;for(var l:new ArrayList<>(listeners))l.onViewDetachedFromWindow(this);}
 int getWidth(){return width;} int getHeight(){return height;} int getScrollX(){return scrollX;} int getScrollY(){return scrollY;}
 int getLeft(){return left;} int getTop(){return top;} ViewGroup getParent(){return parent;}
 void invalidate(){} void draw(Canvas c){}
 Object getContext(){return null;} void setTag(Object x){tag=x;} Object getTag(){return tag;}
}
class ViewGroup extends View {
 List<View> children=new ArrayList<>(); int getChildCount(){return children.size();}
 View getChildAt(int i){return children.get(i);} void add(View v){children.add(v);v.parent=this;}
 void removeView(View v){children.remove(v);v.detach();v.parent=null;}
 @Override void draw(Canvas c){Canvas.drawnChildren=children.size();}
}
class FrameLayout extends ViewGroup {
 static class LayoutParams { int leftMargin,topMargin;LayoutParams(int w,int h){} }
 void addView(View v,LayoutParams params){add(v);}
}
class ImageView extends View {
 enum ScaleType { FIT_XY } Bitmap bitmap; ImageView(Object c){}
 void setImageBitmap(Bitmap b){bitmap=b;}void setScaleType(ScaleType s){}void setImageDrawable(Object d){bitmap=null;}
}
class ScrollView extends ViewGroup {}
class HorizontalScrollView extends ViewGroup {}
class EditText extends View {}
class UiStyle { static void collectAcrylicSurfaces(View v,List<View> surfaces){surfaces.add(v);} }
class Bitmap {
 enum Config{ARGB_8888} int w,h;boolean recycled; static boolean oom;
 static Bitmap createBitmap(int w,int h,Config c){if(oom)throw new OutOfMemoryError();Bitmap b=new Bitmap();b.w=w;b.h=h;return b;}
 int getWidth(){return w;} int getHeight(){return h;} void recycle(){recycled=true;}
}
class Canvas { static float dx,dy;static int drawnChildren;Canvas(Bitmap b){}void scale(float x,float y){}void translate(float x,float y){dx=x;dy=y;} }
public class NativeMotionCoreCheck {
 static final Object SWAP_GHOST=new Object();static final long SWAP_MILLIS=220;
 @@LIMITS@@
 @@TYPE@@
 @@SNAPSHOT@@
 @@SWAP@@
 static int count;
 static void check(boolean ok,String label){count++;if(!ok)throw new AssertionError(label);}
 public static void main(String[] args){
  ScrollView viewport=new ScrollView();ViewGroup content=new ViewGroup();View a=new View(),b=new View();b.visible=false;content.add(a);content.add(b);
  UiMotion.observeScroll(viewport,content);UiMotion.observeScroll(viewport,content);
  check(viewport.frames.size()==1&&viewport.tree.scroll.size()==1,"binding and frame coalescing");
  viewport.frame();check(a.animator.starts==1&&b.animator.starts==0,"visible initial cards only");a.animator.finish();
  for(var l:viewport.tree.scroll) {l.onScrollChanged();l.onScrollChanged();}
  check(viewport.frames.size()==1,"many scroll events one frame");b.visible=true;viewport.frame();
  check(a.animator.starts==1&&b.animator.starts==1,"only newly visible card enters");b.animator.finish();
  View replacement=new View();content.children.clear();content.add(replacement);
  for(var l:viewport.tree.layout)l.onGlobalLayout();viewport.frame();
  check(replacement.animator.starts==0,"background layout rebuild is silent");
  UiMotion.reveal(replacement,0);check(replacement.animator.starts==1,"explicit navigation reveal");
  UiMotion.settleScroll(viewport,content);check(replacement.alpha==1&&replacement.y==0,"silent rebuild settles running reveal");
  viewport.frame();check(replacement.animator.starts==1,"settle cannot be replayed by layout");
  ViewGroup form=new ViewGroup();form.add(new EditText());UiMotion.reveal(form,0);check(form.animator.starts==0,"input subtree protected");
  ViewGroup pager=new ViewGroup();pager.add(new HorizontalScrollView());UiMotion.reveal(pager,0);check(pager.animator.starts==0,"horizontal subtree protected");
  View focus=new View();focus.accessibility=true;UiMotion.reveal(focus,0);check(focus.animator.starts==0,"accessibility focus protected");
  ViewGroup focusedCard=new ViewGroup();View focusChild=new View();focusedCard.add(focusChild);UiMotion.reveal(focusedCard,0);focusChild.accessibility=true;focusedCard.animator.update.accept(new ValueAnimator());check(focusedCard.alpha==1&&focusedCard.y==0&&focusedCard.listeners.isEmpty(),"accessibility focus acquired during reveal settles the card");
  ValueAnimator.enabled=false;View disabled=new View();UiMotion.reveal(disabled,0);check(disabled.alpha==1&&disabled.animator.starts==0,"disabled immediate final state");
  ValueAnimator.enabled=true;View active=new View();UiMotion.reveal(active,0);ValueAnimator.enabled=false;active.animator.update.accept(new ValueAnimator());check(active.alpha==1&&active.y==0,"disable during reveal settles");
  ValueAnimator.enabled=true;View detached=new View();UiMotion.reveal(detached,0);detached.detach();check(detached.alpha==1&&detached.y==0&&detached.listeners.isEmpty(),"detach cancels and restores");
  for(var l:viewport.tree.scroll)l.onScrollChanged();viewport.detach();check(viewport.frames.isEmpty()&&viewport.tree.scroll.isEmpty()&&viewport.tree.layout.isEmpty(),"observer detach cleans callbacks");
  ViewGroup host=new ViewGroup();View longPage=new View();host.add(longPage);longPage.width=4000;longPage.height=200000;longPage.left=7;longPage.top=11;longPage.clip=new Rect();longPage.clip.set(0,9000,4000,11000);
  Snapshot shot=snapshot(longPage);check(shot!=null&&shot.bounds.height()==2000&&shot.bounds.top==9000,"snapshot crops tall content");
  check((long)shot.bitmap.w*shot.bitmap.h<=SNAPSHOT_MAX_PIXELS&&Math.max(shot.bitmap.w,shot.bitmap.h)<=SNAPSHOT_MAX_EDGE,"snapshot allocation bound");
  check(Canvas.dx==-7&&Canvas.dy==-9011,"cropped parent canvas coordinates");
  longPage.scrollY=9000;longPage.height=2000;shot=snapshot(longPage);check(shot.bounds.top==0&&shot.bounds.height()==2000,"scroll viewport local offset");
  longPage.visible=false;check(snapshot(longPage)==null,"invisible snapshot skipped");longPage.visible=true;Bitmap.oom=true;check(snapshot(longPage)==null,"allocation failure retains synchronous path");
  Bitmap.oom=false;FrameLayout stage=new FrameLayout();View body=new View();stage.add(body);
  swap(body,body,()->{});check(stage.getChildCount()==2&&body.alpha==0,"cross-fade creates one ghost");
  ImageView old=(ImageView)stage.getChildAt(1);Bitmap oldBitmap=old.bitmap;
  body.alpha=.6f;old.alpha=.4f;swap(body,body,()->{});
  check(Canvas.drawnChildren==2&&oldBitmap.recycled&&old.bitmap==null&&stage.getChildCount()==2,"rapid reversal captures composite and releases old ghost");
  ImageView current=(ImageView)stage.getChildAt(1);Bitmap currentBitmap=current.bitmap;current.animator.finish();
  check(stage.getChildCount()==1&&currentBitmap.recycled&&body.alpha==1,"completion releases bitmap and settles content");
  swap(body,body,()->{});ValueAnimator.enabled=false;swap(body,body,()->{});
  check(stage.getChildCount()==1&&body.alpha==1,"disable during swap clears ghost");ValueAnimator.enabled=true;
  try {swap(body,body,()->{throw new IllegalStateException();});}catch(IllegalStateException expected){}
  check(stage.getChildCount()==1&&body.alpha==1,"rebuild failure releases ghost");
  ScrollView single=new ScrollView();View nested=new View();single.add(nested);swap(nested,nested,()->{});
  check(single.getChildCount()==1&&nested.alpha==1,"single-child scroll host never receives ghost");
  System.out.println("Native motion core checks passed: "+count+" (production methods, Android doubles)");
 }
}
'''.replace('@@LIMITS@@', limits).replace('@@TYPE@@', snapshot_type).replace('@@SNAPSHOT@@', snapshot).replace('@@SWAP@@', swap)
# Append the unmodified observer and motion implementations, only replacing Android imports.
harness += '\n' + production('UiMotion.java') + '\n' + production('ScrollRevealObserver.java')
directory = ROOT / 'build/native-motion-core-check'
directory.mkdir(parents=True, exist_ok=True)
java_file = directory / 'NativeMotionCoreCheck.java'
java_file.write_text(harness, encoding='utf-8')
java_home = Path(os.environ['JAVA_HOME'])
subprocess.run([str(java_home / 'bin/javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(directory), str(java_file)], check=True)
subprocess.run([str(java_home / 'bin/java.exe'), '-cp', str(directory), 'NativeMotionCoreCheck'], check=True)
