"""Execute production dialog surface/layer methods with rendering-state doubles."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/UiStyle.java').read_text(encoding='utf-8')
methods = []
for marker in ('private static void applyDialogSurface(', 'private static void registerDialogLayer(',
               'private static void removeDialogLayer('):
    start = source.index(marker)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    methods.append(source[start:end].replace('android.graphics.Shader.TileMode.CLAMP', '0'))

harness = r'''
import java.util.*;
class DialogLayersCheck {
 static class Activity {}
 static class Build {static class VERSION {static int SDK_INT=36;}}
 static class ThemeStore {static boolean enabled=true,dark=false;
  static boolean acrylicEnabled(Object c){return enabled;}static boolean dark(Object c){return dark;}}
 static class RenderEffect {static Object createBlurEffect(float x,float y,int mode){return new Object();}}
 static class View {float alpha=1;Object effect,background;
  Object getContext(){return this;}void setBackground(Object o){background=o;}void setElevation(int i){}
  View animate(){return this;}void cancel(){}void setScaleX(float f){}void setScaleY(float f){}
  void setAlpha(float f){alpha=f;}void setRenderEffect(Object o){effect=o;}}
 static class Palette {}
 static Palette colors(Object o){return new Palette();}
 static int dp(View v,int n){return n;}static final int RADIUS_PANEL=20;
 static Object surfaceDrawable(View v,Palette p,int r,int a,int b){return a+":"+b;}
 static void glass(View v){v.setBackground("glass");}
 static class DialogScaleState {List<View> dialogRoots=new ArrayList<>();}
 static Map<Activity,DialogScaleState> DIALOG_SCALES=new HashMap<>();
 METHODS
 static void check(boolean b){if(!b)throw new AssertionError();}
 public static void main(String[] args){
  Activity a=new Activity();DIALOG_SCALES.put(a,new DialogScaleState());
  View parent=new View(),child=new View(),third=new View();
  registerDialogLayer(a,parent);applyDialogSurface(parent,a);check(parent.effect==null);
  registerDialogLayer(a,child);applyDialogSurface(child,a);
  check(parent.effect!=null&&parent.alpha<1&&child.effect==null);
  check(parent.background.equals(child.background)&&child.background.equals("124:112"));
  registerDialogLayer(a,third);check(child.effect!=null);
  removeDialogLayer(a,third);check(child.effect==null&&child.alpha==1&&parent.effect!=null);
  removeDialogLayer(a,child);check(parent.effect==null&&parent.alpha==1);
  ThemeStore.dark=true;applyDialogSurface(child,a);check(child.background.equals("112:100"));
  ThemeStore.enabled=false;registerDialogLayer(a,child);applyDialogSurface(child,a);
  check(parent.effect==null&&child.background.equals("glass"));
  removeDialogLayer(a,child);removeDialogLayer(a,parent);check(DIALOG_SCALES.get(a).dialogRoots.isEmpty());
  System.out.println("Dialog layer checks passed: shared translucent surface, nested blur, restoration, dark mode and blur setting");
 }
}
'''.replace('METHODS', '\n'.join(methods))
out = root / 'build/dialog-layers-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'DialogLayersCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'DialogLayersCheck'], check=True)
