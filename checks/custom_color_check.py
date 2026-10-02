"""Run actual custom-color routing and input callbacks with small Android doubles."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/AppearanceActivity.java').read_text(encoding='utf-8')

def method(marker):
    start = source.index(marker)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

harness = r'''
class CustomColorCheck {
 static class ThemeStore {
  static final String CUSTOM="custom";
  static String selected="teal";
  static boolean isHexColor(String s){return s.matches("#?[0-9a-fA-F]{6}");}
  static void setColor(Object c,String s){selected=s;}
 }
 static class Color {
  static int parseColor(String s){if(!s.matches("#[0-9a-fA-F]{6}"))throw new IllegalArgumentException(s);return (int)(0xff000000L|Long.parseLong(s.substring(1),16));}
  static void colorToHSV(int c,float[] hsv){}
 }
 static class Button {boolean enabled;void setEnabled(boolean b){enabled=b;}}
 int dialogs,previews;
 int[] chosen={0xff7353ba};float[] hsv=new float[3];boolean[] syncing={false};
 Button apply=new Button();Runnable syncSliders=()->{},paintPreview=()->previews++;
 void showCustomColorDialog(){dialogs++;}
 ROUTING
 INPUT
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  CustomColorCheck c=new CustomColorCheck();
  c.selectColor("custom");check(c.dialogs==1&&ThemeStore.selected.equals("teal"));
  c.selectColor("custom");check(c.dialogs==2);
  c.selectColor("blue");check(ThemeStore.selected.equals("blue")&&c.dialogs==2);
  c.onTextChanged("  #12ab34  ",0,0,11);check(c.chosen[0]==0xff12ab34&&c.apply.enabled);
  c.onTextChanged(" ABCDEF ",0,0,8);check(c.chosen[0]==0xffabcdef&&c.apply.enabled);
  c.onTextChanged("#12",0,0,3);check(c.chosen[0]==0xffabcdef&&!c.apply.enabled);
  c.onTextChanged("#zzzzzz",0,0,7);check(!c.apply.enabled);
  c.onTextChanged("",0,0,0);check(!c.apply.enabled);
  c.syncing[0]=true;c.onTextChanged("#000000",0,0,7);check(c.chosen[0]==0xffabcdef&&c.apply.enabled);
  c.syncing[0]=false;c.onTextChanged("#000000",0,0,7);check(c.chosen[0]==0xff000000&&c.previews==3);
  System.out.println("Custom color checks passed: editor routing, no premature save, whitespace, invalid input and slider sync");
 }
}
'''.replace('ROUTING', method('private void selectColor(')).replace(
    'INPUT', method('public void onTextChanged('))
out = root / 'build/custom-color-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'CustomColorCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'CustomColorCheck'], check=True)
