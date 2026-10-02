"""Run actual bind actions against an AOSP-shaped removal/animation double.

Models ViewAnimator.showOnly/removeViewAt and ViewGroup disappearing children,
including RemoteViews' reverse-order removal. No drawing occurs between refreshes.
This checks action ordering, not launcher rendering or Android lifecycle behavior.
"""
from pathlib import Path
import os
import subprocess
import re

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/widget-danmaku-refresh-check'
out.mkdir(parents=True, exist_ok=True)
source = (src / 'WidgetDanmaku.java').read_text(encoding='utf-8')
source = re.sub(r'import android\.[^;]+;\s*', '', source)
source = source.replace('android.util.DisplayMetrics', 'DisplayMetrics')
source = source.replace('android.graphics.Paint', 'Paint')
harness = r'''
package com.donglan.chrona;
import java.util.*;
import java.util.function.Consumer;
class R {
 static class id {static final int widget_danmaku=1,widget_bullets_left=2,
 widget_bullets_right=3,widget_bullet_text=4,widget_bullet_frame=5;}
 static class layout {static final int widget_bullet=1;}
 static class array {static final int widget_encouragements=1;}
}
class View {static final int VISIBLE=0,GONE=8;}
class DisplayMetrics {float density=1,scaledDensity=1;}
class Paint {
 static int height=18;
 static class FontMetricsInt {int bottom=height,top=0;}
 void setTextSize(float size){} FontMetricsInt getFontMetricsInt(){return new FontMetricsInt();}
}
class Color {static int argb(int a,int r,int g,int b){return 0;}
 static int red(int c){return 0;} static int green(int c){return 0;} static int blue(int c){return 0;}}
class Context {
 Context getResources(){return this;} DisplayMetrics getDisplayMetrics(){return new DisplayMetrics();}
 String[] getStringArray(int id){return new String[]{"A","B","C"};}
 String getPackageName(){return "com.donglan.chrona";}
}
class Child {String text;boolean animation;Child(String t){text=t;}}
class Flipper {
 List<Child> children=new ArrayList<>(), disappearing=new ArrayList<>();
 int index,interval;boolean first=true;
 void select(int requested){
  index=requested>=children.size()?0:requested<0?children.size()-1:requested;
  boolean animate=!first;
  for(int i=0;i<children.size();i++){
   Child c=children.get(i);
   if(i==index){if(animate)c.animation=true;first=false;}
   else c.animation=false;
  }
 }
 void remove(){
  // API 31+ RemoteViews removes non-stable children from last to first.
  for(int i=children.size()-1;i>=0;i--){
   Child c=children.remove(i);
   if(c.animation)disappearing.add(c);
   if(children.isEmpty()){index=0;first=true;}
   else if(index>=children.size())select(0);
   else if(index==i)select(index);
  }
 }
 long staleText(){return disappearing.stream().filter(c->!c.text.isEmpty()).count();}
}
class Host {
 Flipper left=new Flipper(),right=new Flipper();
 Flipper lane(int id){return id==R.id.widget_bullets_left?left:right;}
}
class RemoteViews {
 String text="";List<Consumer<Host>> actions=new ArrayList<>();
 RemoteViews(String pkg,int layout){}
 void setDisplayedChild(int id,int n){actions.add(h->h.lane(id).select(n));}
 void removeAllViews(int id){actions.add(h->h.lane(id).remove());}
 void addView(int id,RemoteViews child){actions.add(h->h.lane(id).children.add(new Child(child.text)));}
 void setInt(int id,String method,int value){actions.add(h->h.lane(id).interval=value);}
 void setTextViewText(int id,String value){text=value;}
 void setTextColor(int id,int value){}
 void setViewVisibility(int id,int value){}
 void setViewPadding(int id,int l,int t,int r,int b){}
 void apply(Host h){for(Consumer<Host> a:actions)a.accept(h);}
}
public class WidgetDanmakuRefreshCheck {
 static int checks;
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static void bind(Host host,int height,int font,long now){
  Paint.height=font;Context c=new Context();RemoteViews v=new RemoteViews("",0);
  WidgetDanmaku.bind(c,v,new WidgetSize(240,height),0,7,now);v.apply(host);
  check(host.left.staleText()==0&&host.right.staleText()==0,"removed text survives refresh");
 }
 public static void main(String[] args){
  // Negative control: the old deletion order retains an animated phrase.
  Flipper old=new Flipper();old.children.add(new Child(""));old.children.add(new Child("old"));
  old.select(0);old.select(1);old.remove();
  check(old.staleText()==1,"model must expose original defect");
  for(int active=1;active<=3;active++){
   Host h=new Host();bind(h,260,18,0);
   for(int round=0;round<40;round++){
    h.left.select(active);h.right.select(active);
    bind(h,260,18,round*60000L);
    check(h.left.children.size()==4&&h.right.children.size()==4,"bounded phrase list");
    check(h.left.index==0&&h.right.index==0,"restart blank");
    check(h.left.interval>=8000&&h.right.interval>=12000,"animation duration preserved");
   }
   h.left.select(active);h.right.select(active);
   bind(h,160,44,0); // Large text: two lanes -> one.
   check(h.right.children.isEmpty(),"disabled lane cleared");
   h.left.select(1);bind(h,100,18,0); // Entire overlay disabled.
   check(h.left.children.isEmpty()&&h.right.children.isEmpty(),"hidden overlay cleared");
   bind(h,260,18,0);
   check(h.left.children.size()==4&&h.right.children.size()==4,"restore both lanes");
  }
  System.out.println("Widget refresh checks passed: "+checks+"; Android host behavior modeled, not device-tested");
 }
}
'''
(out / 'WidgetDanmaku.java').write_text(source, encoding='utf-8')
(out / 'WidgetDanmakuRefreshCheck.java').write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out),
               str(out / 'WidgetDanmaku.java'), str(out / 'WidgetDanmakuRefreshCheck.java'),
               str(src / 'WidgetSize.java'), str(src / 'WidgetDanmakuLayout.java'),
               str(root / 'checks/WidgetDanmakuLayoutCheck.java')], check=True)
for name in ('WidgetDanmakuRefreshCheck', 'WidgetDanmakuLayoutCheck'):
    subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.' + name], check=True)
