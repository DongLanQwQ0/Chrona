"""Execute production borderless menu rows; native scrolling/rendering has device checks."""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'app/src/main/java/com/donglan/chrona/UiStyle.java').read_text(encoding='utf-8')


def method(signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


menu = method('    static TextView addMenuItem(')
choice = method('    public static void choiceDialog(')
assert 'addMenuItem(choices,options[i],i==selected,' in choice
assert 'choice(item,' not in choice and 'options.length * dp' not in choice
assert 'View.MeasureSpec.AT_MOST' in choice and 'new LinearLayout.LayoutParams(-1,-2)' in choice
assert 'scroll.setVerticalScrollBarEnabled(false)' in choice
assert 'scroll.setHorizontalScrollBarEnabled(false)' in choice

java_source = r'''
import java.util.*;
public class ChoiceMotionCheck {
 static int checks,negative;
 static void require(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 static class Activity {final Resources resources=new Resources();}
 static class Resources {final Metrics metrics=new Metrics();Metrics getDisplayMetrics(){return metrics;}}
 static class Metrics {float density;}
 static class View {
  static final int IMPORTANT_FOR_ACCESSIBILITY_NO=2;
  final Activity context;Object tag,background;boolean selected,ripple;String state;float scale=1;int minHeight,left,top,right,bottom;
  interface Click {void click(View view);}Click click;
  View(Activity activity){context=activity;}Activity getContext(){return context;}Resources getResources(){return context.resources;}
  Object getTag(){return tag;}void setTag(Object value){tag=value;}void setBackgroundColor(int color){background=color;}
  void setImportantForAccessibility(int value){}void setPadding(int l,int t,int r,int b){left=l;top=t;right=r;bottom=b;}
  void setSelected(boolean value){selected=value;}void setStateDescription(String value){if(Build.VERSION.SDK_INT<30)throw new AssertionError("API 30 method on older Android");state=value;}
  void setOnClickListener(Click value){click=value;}void performClick(){click.click(this);}
 }
 static class TextView extends View {
  String text;int size,gravity,color;boolean check;
  TextView(Activity context){super(context);}void setText(String value){text=value;}void setTextSize(int value){size=value;}
  void setGravity(int value){gravity=value;}void setMinHeight(int value){minHeight=value;}void setTextColor(int value){color=value;}
 }
 static class LinearLayout extends View {
  final List<View> children=new ArrayList<>();final List<LayoutParams> params=new ArrayList<>();
  static class LayoutParams {int width,height,l,r;LayoutParams(int w,int h){width=w;height=h;}void setMargins(int a,int b,int c,int d){l=a;r=c;}}
  LinearLayout(Activity context){super(context);}int getChildCount(){return children.size();}View getChildAt(int index){return children.get(index);}
  void addView(View child,LayoutParams p){children.add(child);params.add(p);}
 }
 static class Gravity {static final int START=3,CENTER_VERTICAL=16;}
 static class Build {static class VERSION {static int SDK_INT=36;}}
 static class Palette {int primary=1,text=2,outline=3;}
 static Palette colors(Activity context){return new Palette();}
 static void marked(TextView item,boolean selected){item.check=selected;}
 static void pressable(View item){item.ripple=true;}
 static class PressFeedback {static void apply(View item,float scale){item.scale=scale;}}
 __METHODS__
 static boolean valid(LinearLayout list,int count,int prefix,int selected,int density) {
  if(list.getChildCount()!=prefix+count*2-1)return false;
  for(int i=0;i<count;i++) {
   View view=list.getChildAt(prefix+i*2);
   if(!(view instanceof TextView row)||!"menu_item".equals(row.tag)||row.background!=null||row.scale!=1||!row.ripple)return false;
   if(row.size!=16||row.gravity!=(Gravity.START|Gravity.CENTER_VERTICAL)||row.minHeight!=52*density)return false;
   if(row.selected!=(i==selected)||row.check!=(i==selected)||row.color!=(i==selected?1:2))return false;
   if(i<count-1) {
    View line=list.getChildAt(prefix+i*2+1);var params=list.params.get(prefix+i*2+1);
    if(!"menu_separator".equals(line.tag)||!Integer.valueOf(3).equals(line.background)||params.height!=density||params.l!=14*density||params.r!=14*density)return false;
   }
  }
  return true;
 }
 public static void main(String[] args) {
  for(int sdk:new int[]{26,36})for(int density:new int[]{1,3})for(int count:new int[]{1,4,12})for(int prefix:new int[]{0,1}) {
   Build.VERSION.SDK_INT=sdk;
   Activity context=new Activity();context.resources.metrics.density=density;LinearLayout list=new LinearLayout(context);
   if(prefix==1)list.addView(new TextView(context),new LinearLayout.LayoutParams(-1,-2));
   int[] calls={0},choice={-1};
   for(int i=0;i<count;i++){final int index=i;addMenuItem(list,"Choice "+i,i==count-1,()->{calls[0]++;choice[0]=index;});}
   require(valid(list,count,prefix,count-1,density),"borderless rows, separators, selection and ripple");
   for(int i=0;i<count;i++)list.getChildAt(prefix+i*2).performClick();
   require(calls[0]==count&&choice[0]==count-1,"each full menu row invokes its own action once");
   ((TextView)list.getChildAt(prefix)).scale=1.05f;
   require(!valid(list,count,prefix,count-1,density),"button-like scaling regression rejected");negative++;
   ((TextView)list.getChildAt(prefix)).scale=1;
   if(count>1){list.children.remove(prefix+1);require(!valid(list,count,prefix,count-1,density),"missing separator rejected");negative++;}
  }
  System.out.println("Choice menu checks passed: "+checks+"; regressions rejected: "+negative+" (View doubles, not device rendering)");
 }
}
'''.replace('__METHODS__', menu + '\n' + method('    private static int dp('))
out = ROOT / 'build/choice-motion-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'ChoiceMotionCheck.java'
file.write_text(java_source, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'ChoiceMotionCheck'], check=True)
