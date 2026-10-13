"""Execute Dashboard's carousel factory with narrow Android layout doubles.

Checks held-scale geometry, not Android rendering or device touch handling.
The negative control executes the same factory with motion padding moved back
from the direct content child to HorizontalScrollView.
"""
import os
import re
import subprocess
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'app/src/main/java/com/donglan/chrona/DashboardActivity.java').read_text(encoding='utf-8')
start = source.index('    private void showUpcomingCarousel(')
end = source.index('\n    private ', start + 1)
factory = source[start:end].replace('android.text.TextUtils', 'TextUtils')
press = (ROOT / 'app/src/main/java/com/donglan/chrona/PressFeedback.java').read_text(encoding='utf-8')
scale = re.search(r'PRESSED_SCALE\s*=\s*([\d.]+)f', press).group(1)
# Mutate the extracted executable factory, rather than comparing source strings
# as the regression assertion. Fail fast if its construction syntax changes.
old_padding = 'cards.setPadding(dp(8), dp(6), dp(8), dp(6));'
if factory.count(old_padding) != 1:
    raise RuntimeError('Update the negative-control mutation for the current factory')
broken = factory.replace('showUpcomingCarousel(', 'brokenCarousel(', 1).replace(
    old_padding, 'cards.setPadding(0, 0, 0, 0); strip.setPadding(dp(8), dp(6), dp(12), dp(6));')
harness = r'''
import java.util.*;
public class CarouselMotionCheck {
 static final float HELD = __SCALE__f;
 static int checks, rejected;
 final int density;
 final LinearLayout content;
 final List<HorizontalScrollView> strips = new ArrayList<>();
 CarouselMotionCheck(int density) { this.density=density; content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); }
 int dp(int n){return n*density;}
 static class Params {
  int width,height,left,top,right,bottom;
  Params(int w,int h){width=w;height=h;}
  void setMargins(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
 }
 interface Click { void run(View v); }
 static class View {
  final CarouselMotionCheck context;
  int pl,pt,pr,pb,x,y,w,h; Params params=new Params(-2,-2); boolean clickable;
  float held=1;
  View(CarouselMotionCheck c){context=c;}
  void setPadding(int l,int t,int r,int b){pl=l;pt=t;pr=r;pb=b;}
  void setOnClickListener(Click c){clickable=true;}
  void measure(int available){w=params.width>=0?params.width:Math.max(0,available);h=params.height>=0?params.height:context.dp(20);}
 }
 static class LinearLayout extends View {
  static final int HORIZONTAL=0,VERTICAL=1; int orientation=VERTICAL;
  boolean clipChildren=true,clipPadding=true; final List<View> children=new ArrayList<>();
  static class LayoutParams extends Params {LayoutParams(int w,int h){super(w,h);}}
  LinearLayout(CarouselMotionCheck c){super(c);}
  void setOrientation(int value){orientation=value;}
  void setClipChildren(boolean value){clipChildren=value;}
  void setClipToPadding(boolean value){clipPadding=value;}
  void addView(View v){children.add(v);}
  void addView(View v,Params p){v.params=p;children.add(v);}
  @Override void measure(int available){
   int inner=params.width>=0?params.width-pl-pr:available-pl-pr;
   int cursor=0,cross=0;
   for(View child:children){
    child.measure(Math.max(0,inner-child.params.left-child.params.right));
    Params p=child.params;
    if(orientation==HORIZONTAL){child.x=pl+cursor+p.left;child.y=pt+p.top;cursor+=p.left+child.w+p.right;cross=Math.max(cross,p.top+child.h+p.bottom);}
    else{child.x=pl+p.left;child.y=pt+cursor+p.top;cursor+=p.top+child.h+p.bottom;cross=Math.max(cross,p.left+child.w+p.right);}
   }
   int desiredW=pl+pr+(orientation==HORIZONTAL?cursor:cross);
   w=params.width>=0?params.width:params.width==-1?available:desiredW;
   h=params.height>=0?params.height:pt+pb+(orientation==HORIZONTAL?cross:cursor);
  }
 }
 static class HorizontalScrollView extends LinearLayout {
  boolean fillViewport,scrollbar=true;
  HorizontalScrollView(CarouselMotionCheck c){super(c);}
  void setHorizontalScrollBarEnabled(boolean b){scrollbar=b;}
  void setFillViewport(boolean b){fillViewport=b;}
  @Override void measure(int available){
   View child=children.get(0); child.measure(available-pl-pr);
   w=params.width>=0?params.width:available;
   child.x=pl;child.y=pt;h=pt+child.h+pb;
  }
 }
 static class TextView extends View {
  int minLines=1,maxLines=Integer.MAX_VALUE,size;
  TextView(CarouselMotionCheck c,int size){super(c);this.size=size;}
  void setIncludeFontPadding(boolean b){}
  void setMinLines(int n){minLines=n;}
  void setMaxLines(int n){maxLines=n;}
  void setEllipsize(Object o){}
  @Override void measure(int available){super.measure(available);h=context.dp((size+4)*Math.min(minLines,maxLines));}
 }
 static class TextUtils {static class TruncateAt {static final Object END=new Object();}}
 static class R {static class drawable {static final int ic_schedule=1;}}
 static class HomeTimelineEntry {
  long timestampMillis; Object candidate=new Object(),systemEvent; String name;
  HomeTimelineEntry(String n,long t){name=n;timestampMillis=t;} String title(){return name;}
 }
 static class UiStyle {
  static void addSpaced(LinearLayout p,View v,int top,int bottom){Params lp=new Params(-1,-2);lp.setMargins(0,p.context.dp(top),0,p.context.dp(bottom));p.addView(v,lp);}
  static void pressable(View v){v.held=HELD;}
 }
 TextView text(String label,int size,boolean bold){return new TextView(this,size);}
 LinearLayout card(){return new LinearLayout(this);}
 TextView scheduleAttribute(int resource,Object boundary,String title,int size,int lines,boolean bold){return text(title,size,bold);}
 Object homeCardBoundary(Object candidate,Object event,boolean a,boolean b){return new Object();}
 void openHomeEntry(HomeTimelineEntry entry){}
 __FACTORY__
 __BROKEN__
 static void check(boolean ok,String message){checks++;if(!ok)throw new AssertionError(message);}
 static void contained(double left,double top,double right,double bottom,int width,int height,String label){
  check(left>=-0.001&&top>=-0.001&&right<=width+0.001&&bottom<=height+0.001,label+" clipped: ["+left+","+top+","+right+","+bottom+"] in "+width+"x"+height);
 }
 void verify(boolean broken,int viewportDp){
  List<HomeTimelineEntry> entries=new ArrayList<>(List.of(new HomeTimelineEntry("later",2),new HomeTimelineEntry("first",1)));
  if(broken)brokenCarousel(entries);else showUpcomingCarousel(entries);
  content.params=new Params(dp(viewportDp),-2);content.measure(dp(viewportDp));
  HorizontalScrollView strip=strips.get(0);LinearLayout cards=(LinearLayout)strip.children.get(0);
  check(cards.children.size()==2&&entries.get(0).timestampMillis==1,"nonempty factory and ordering");
  View first=cards.children.get(0),last=cards.children.get(1);
  check(first.clickable&&first.held>1,"factory installs held feedback and click action");
  check(strip.x+cards.x+first.x==content.pl,"first card starts at body alignment");
  for(View item:List.of(first,last)){
   double dx=item.w*(item.held-1)/2,dy=item.h*(item.held-1)/2;
   double l=item.x-dx,t=item.y-dy,r=item.x+item.w+dx,b=item.y+item.h+dy;
   // HScroll's clipChildren retains clipping to its direct content child,
   // even when the child's own clipChildren/clipToPadding flags are false.
   if(strip.clipChildren)contained(l,t,r,b,cards.w,cards.h,"direct cards boundary");
   int scroll=item==first?0:Math.max(0,cards.w+strip.pl+strip.pr-strip.w);
   double offsetX=cards.x-scroll,offsetY=cards.y;
   contained(l+offsetX,t+offsetY,r+offsetX,b+offsetY,strip.w,strip.h,"HScroll viewport at "+(item==first?"start":"end"));
  }
 }
 public static void main(String[] args){
  for(int density:new int[]{1,2,3})for(int width:new int[]{320,360,480}){
   new CarouselMotionCheck(density).verify(false,width);
   try{new CarouselMotionCheck(density).verify(true,width);throw new IllegalStateException("negative control unexpectedly passed");}
   catch(AssertionError expected){check(expected.getMessage().startsWith("direct cards boundary clipped"),"negative control must fail at actual direct-child clipping: "+expected.getMessage());rejected++;}
  }
  System.out.println("Carousel motion passed: "+checks+" checks, first/last held geometry, alignment, densities 1/2/3, widths 320/360/480dp");
  System.out.println("Rejected strip-padding negative control: "+rejected+"/9 executed factories (direct cards boundary clipping)");
  System.out.println("Scope: production factory with Android layout doubles; no device rendering assertion");
 }
}
'''.replace('__FACTORY__', factory).replace('__BROKEN__', broken).replace('__SCALE__', scale)
out = ROOT / 'build/carousel-motion-check'
out.mkdir(parents=True, exist_ok=True)
java_file = out / 'CarouselMotionCheck.java'
java_file.write_text(harness, encoding='utf-8')
java_home = Path(os.environ.get('JAVA_HOME', r'D:\Minecraft\java21'))
subprocess.run([str(java_home / 'bin/javac'), '-encoding', 'UTF-8', '-d', str(out), str(java_file)], check=True)
subprocess.run([str(java_home / 'bin/java'), '-cp', str(out), 'CarouselMotionCheck'], check=True)
