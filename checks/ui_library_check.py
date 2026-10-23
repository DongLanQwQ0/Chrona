"""Guard UI ownership and execute the production modal action row; rendering is checked on Android."""
from pathlib import Path
import os
import re
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/donglan/chrona'
owners = {
    'UiDialogs': ('dateDialog', 'timeDialog', 'choiceDialog', 'confirmDialog', 'textEditorDialog',
                  'dialogPanel', 'dialogBody', 'showFloatingDialog', 'addMenuItem'),
    'UiSurfaces': ('isCardSurface', 'transformToRoot', 'invalidateAcrylicSurfaces',
                   'collectAcrylicSurfaces', 'minimumAcrylicMix', 'acrylicSourceColor'),
    'UiMotion': ('enterChildren', 'swap', 'finishSwap', 'pop', 'pulse'),
}
style = (JAVA / 'UiStyle.java').read_text(encoding='utf-8')
callers = {file: file.read_text(encoding='utf-8') for file in (ROOT / 'app/src').rglob('*.java')}
for owner, methods in owners.items():
    source = (JAVA / (owner + '.java')).read_text(encoding='utf-8')
    for name in methods:
        declaration = r'\bstatic\s+[\w<>]+\s+' + name + r'\('
        assert re.search(declaration, source), f'{owner}.{name} has its own implementation'
        assert not re.search(declaration, style), f'UiStyle retains an obsolete {name} alias'
        for file, caller in callers.items():
            assert not re.search(r'\bUiStyle\.' + name + r'\b', caller), file
assert 'private static GradientDrawable shape(' not in style
assert 'private static int childSurface(' not in style
surfaces = (JAVA / 'UiSurfaces.java').read_text(encoding='utf-8')
assert 'UiDialogs' not in surfaces, 'Rendering must not depend on modal lifecycle'
assert 'setDialogBlurRoot(content, true)' in (JAVA / 'UiDialogs.java').read_text(encoding='utf-8')

dialogs = (JAVA / 'UiDialogs.java').read_text(encoding='utf-8')
assert dialogs.count('actionRow(activity, dialog,') == 4, 'Date/time/confirm/editor share paired actions'
assert 'originalRenderEffect' not in dialogs, 'Always-null state should not survive as an abstraction'
start = dialogs.index('    private static LinearLayout actionRow(')
end = dialogs.index('{', start) + 1
depth = 1
while depth:
    depth += (dialogs[end] == '{') - (dialogs[end] == '}')
    end += 1
method = dialogs[start:end]
harness = r'''
import java.util.*;
public class UiLibraryCheck {
 static class Activity {final int density;final float font;Activity(int d,float f){density=d;font=f;}}
 static class View {final Activity context;Runnable click;int padLeft,padRight;LinearLayout.LayoutParams params;
  View(Activity a){context=a;}int getPaddingLeft(){return padLeft;}int getPaddingRight(){return padRight;}
  void setLayoutParams(LinearLayout.LayoutParams p){params=p;}
  void setOnClickListener(java.util.function.Consumer<View> listener){click=()->listener.accept(this);}
  static class MeasureSpec {static final int UNSPECIFIED=0,EXACTLY=0x40000000,AT_MOST=0x80000000;
   static int getSize(int spec){return spec&0x3fffffff;}static int getMode(int spec){return spec&0xc0000000;}
   static int makeMeasureSpec(int size,int mode){return size|mode;}}}
 static class Paint {final Activity context;Paint(Activity a){context=a;}
  float measureText(String text){return text.length()*15*context.density*context.font;}}
 static class Button extends View {String text;boolean styled,primary,singleLine=true;int minimumHeight;
  Button(Activity a){super(a);}void setText(String s){text=s;}String getText(){return text;}
  Paint getPaint(){return new Paint(context);}void setSingleLine(boolean value){singleLine=value;}}
 static class LinearLayout extends View {static final int HORIZONTAL=0,VERTICAL=1;int orientation;boolean baselineAligned=true;
  List<Button> children=new ArrayList<>();LinearLayout(Activity a){super(a);}
  void addView(Button v,LayoutParams p){children.add(v);v.setLayoutParams(p);}
  int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
  int getOrientation(){return orientation;}void setOrientation(int value){orientation=value;}
  void setBaselineAligned(boolean value){baselineAligned=value;}
  protected void onMeasure(int widthSpec,int heightSpec){}void measure(int width,int mode){onMeasure(MeasureSpec.makeMeasureSpec(width,mode),0);}
  static class LayoutParams {int width,height,leftMargin,topMargin;float weight;LayoutParams(int w,int h,float f){width=w;height=h;weight=f;}}}
 static class Dialog {int dismissed;void dismiss(){dismissed++;}}
 static class UiStyle {static void button(Button b,boolean primary){b.styled=true;b.primary=primary;
  b.padLeft=b.padRight=18*b.context.density;b.minimumHeight=52*b.context.density;}}
 static int dp(View view,int value){return value*view.context.density;}
 __METHOD__
 static int checks;static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 public static void main(String[] args){
  for(int density:new int[]{1,2,3})for(float font:new float[]{1f,1.5f,2f})for(String label:new String[]{"确定","保存","删除","重新解析","全部重新解析"}){
   Activity a=new Activity(density,font);Dialog d=new Dialog();int[] calls={0};
   LinearLayout row=actionRow(a,d,label,()->{d.dismiss();calls[0]++;});
   check(row.children.size()==2,"two actions");Button cancel=row.children.get(0),submit=row.children.get(1);
   check(cancel.text.equals("取消")&&submit.text.equals(label),"action labels");
   check(cancel.styled&&!cancel.primary&&submit.styled&&submit.primary,"semantic styles");
   check(!cancel.singleLine&&!submit.singleLine&&!row.baselineAligned,"natural text height without forced baseline");
   check(cancel.minimumHeight==52*density&&submit.minimumHeight==52*density,"shared style retains 52dp minimum height");
   row.padLeft=7*density;row.padRight=9*density;
   int needed=Math.max((int)Math.ceil(cancel.getPaint().measureText(cancel.text))+cancel.padLeft+cancel.padRight,
                      (int)Math.ceil(submit.getPaint().measureText(submit.text))+submit.padLeft+submit.padRight);
   int threshold=needed*2+8*density+row.padLeft+row.padRight;
   row.measure(threshold,View.MeasureSpec.EXACTLY);horizontal(row,density);
   row.measure(threshold-1,View.MeasureSpec.EXACTLY);vertical(row,density);
   Object stable=submit.params;row.measure(threshold-2,View.MeasureSpec.AT_MOST);
   check(submit.params==stable,"same orientation avoids redundant parameter replacement");vertical(row,density);
   cancel.click.run();check(d.dismissed==1&&calls[0]==0,"stacked cancel never submits");
   submit.click.run();check(d.dismissed==2&&calls[0]==1,"stacked submit callback retained");
   row.measure(threshold+100*density,View.MeasureSpec.EXACTLY);horizontal(row,density);
   row.measure(threshold-1,View.MeasureSpec.EXACTLY);vertical(row,density);
   row.measure(0,View.MeasureSpec.UNSPECIFIED);horizontal(row,density);
   cancel.click.run();check(d.dismissed==3&&calls[0]==1,"restored cancel never submits");
   submit.click.run();check(d.dismissed==4&&calls[0]==2,"restored submit callback retained");
  }
  System.out.println("PASS: "+checks+" production modal-action checks; UI ownership and caller migration checked");
 }
 static void horizontal(LinearLayout row,int density){
  check(row.getOrientation()==LinearLayout.HORIZONTAL,"fitting labels use horizontal row");
  for(Button button:row.children)check(button.params.width==0&&button.params.weight==1&&button.params.height==-2,"equal flexible widths and natural height");
  check(row.children.get(0).params.leftMargin==0&&row.children.get(0).params.topMargin==0&&
   row.children.get(1).params.leftMargin==8*density&&row.children.get(1).params.topMargin==0,"original horizontal gap");
 }
 static void vertical(LinearLayout row,int density){
  check(row.getOrientation()==LinearLayout.VERTICAL,"long labels stack");
  for(Button button:row.children)check(button.params.width==-1&&button.params.weight==0&&button.params.height==-2,"stacked full widths and natural height");
  check(row.children.get(0).params.leftMargin==0&&row.children.get(0).params.topMargin==0&&
   row.children.get(1).params.leftMargin==0&&row.children.get(1).params.topMargin==8*density,"vertical gap replaces horizontal margin");
 }
}
'''.replace('__METHOD__', method)
out = ROOT / 'build/ui-library-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'UiLibraryCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'UiLibraryCheck'], check=True)
