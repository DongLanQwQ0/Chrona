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
 static class Activity {int density;Activity(int d){density=d;}}
 static class View {final Activity context;Runnable click;View(Activity a){context=a;}
  void setOnClickListener(java.util.function.Consumer<View> listener){click=()->listener.accept(this);}}
 static class Button extends View {String text;boolean styled,primary;Button(Activity a){super(a);}void setText(String s){text=s;}}
 static class LinearLayout extends View {List<Button> children=new ArrayList<>();List<LayoutParams> params=new ArrayList<>();
  LinearLayout(Activity a){super(a);}void addView(Button v,LayoutParams p){children.add(v);params.add(p);}
  static class LayoutParams {int width,height,leftMargin;float weight;LayoutParams(int w,int h,float f){width=w;height=h;weight=f;}}}
 static class Dialog {int dismissed;void dismiss(){dismissed++;}}
 static class UiStyle {static void button(Button b,boolean primary){b.styled=true;b.primary=primary;}}
 static int dp(View view,int value){return value*view.context.density;}
 __METHOD__
 static int checks;static void check(boolean b,String message){checks++;if(!b)throw new AssertionError(message);}
 public static void main(String[] args){
  for(int density:new int[]{1,2,3})for(String label:new String[]{"确定","保存","删除"}){
   Activity a=new Activity(density);Dialog d=new Dialog();int[] calls={0};
   LinearLayout row=actionRow(a,d,label,()->{d.dismiss();calls[0]++;});
   check(row.children.size()==2,"two actions");Button cancel=row.children.get(0),submit=row.children.get(1);
   check(cancel.text.equals("取消")&&submit.text.equals(label),"action labels");
   check(cancel.styled&&!cancel.primary&&submit.styled&&submit.primary,"semantic styles");
   check(row.params.get(0).width==0&&row.params.get(1).width==0&&row.params.get(0).weight==1&&row.params.get(1).weight==1,"equal flexible widths");
   check(row.params.get(0).height==52*density&&row.params.get(1).height==52*density&&row.params.get(1).leftMargin==8*density,"original geometry");
   cancel.click.run();check(d.dismissed==1&&calls[0]==0,"cancel never submits");
   submit.click.run();check(d.dismissed==2&&calls[0]==1,"submit dispatches callback once");
  }
  System.out.println("PASS: "+checks+" production modal-action checks; UI ownership and caller migration checked");
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
