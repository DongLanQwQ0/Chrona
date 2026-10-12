"""Execute the production wallpaper sampling method with graphics transport doubles."""
import os
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'app/src/main/java/com/donglan/chrona/GlassBackdropView.java').read_text(encoding='utf-8')
start = source.index('    boolean drawBlurredWallpaper(')
opening = source.index('{', start)
depth = 1
end = opening + 1
while depth:
    depth += (source[end] == '{') - (source[end] == '}')
    end += 1
method = source[start:end].replace('android.graphics.BitmapShader', 'BitmapShader')
runner = r'''
public class AcrylicTranslationCheck {
 static class Bitmap { int width=540,height=1080; int getWidth(){return width;} int getHeight(){return height;} boolean isRecycled(){return false;} }
 static class Rect { float left,top,right,bottom; Rect(float l,float t,float r,float b){left=l;top=t;right=r;bottom=b;} }
 static class RectF extends Rect { RectF(){super(0,0,0,0);} void set(Rect r){left=r.left;top=r.top;right=r.right;bottom=r.bottom;} float width(){return right-left;} float height(){return bottom-top;} }
 static class Matrix { float sx,sy,tx,ty; void setScale(float x,float y){sx=x;sy=y;tx=ty=0;} void postTranslate(float x,float y){tx+=x;ty+=y;} }
 static class Shader { enum TileMode { CLAMP } }
 static class BitmapShader extends Shader { Matrix m; BitmapShader(Bitmap b,TileMode x,TileMode y){if(x!=TileMode.CLAMP||y!=TileMode.CLAMP)throw new AssertionError();} void setLocalMatrix(Matrix value){m=value;} }
 static class Paint { Shader shader; void setShader(Shader s){shader=s;} void setColor(int c){} void setAlpha(int a){} }
 static class Canvas { float tx,ty,sx,sy; int draws; void drawRoundRect(RectF r,int x,int y,Paint p){draws++;if(p.shader instanceof BitmapShader s){tx=s.m.tx;ty=s.m.ty;sx=s.m.sx;sy=s.m.sy;}} }
 static class View { int x,y; void getLocationInWindow(int[] a){a[0]=x;a[1]=y;} }
 static class UiStyle { static class Palette { int background=0xff111111; } }
 static class ThemeStore { static int blurStrength(Object c){return 2;} static boolean gaussianBlur(Object c){return true;} static boolean dark(Object c){return true;} }
 static class Color { static int WHITE=-1; static int argb(int a,int r,int g,int b){return a<<24|r<<16|g<<8|b;} }
 int requestedBlurStrength=2; boolean requestedGaussian=true;
 Bitmap blurredWallpaper=new Bitmap(),shaderBitmap; BitmapShader surfaceShader;
 Matrix surfaceMatrix=new Matrix(); Paint surfacePaint=new Paint(); RectF surfaceBounds=new RectF(),blurDestination=new RectF();
 int[] backdropLocation=new int[2],targetLocation=new int[2]; float wallpaperOpacity=1;
 Object getContext(){return this;} void rebuildBlurredWallpaper(){throw new AssertionError("Unexpected blur rebuild on UI draw");}
 void getLocationInWindow(int[] a){a[0]=10;a[1]=20;} int tint(int color,int alpha){return color;}
 __METHOD__
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 public static void main(String[] args){
  AcrylicTranslationCheck host=new AcrylicTranslationCheck();host.blurDestination.set(new Rect(0,0,1080,2160));
  View target=new View();Canvas canvas=new Canvas();UiStyle.Palette palette=new UiStyle.Palette();Rect bounds=new Rect(0,0,300,200);
  target.x=100;target.y=300;check(host.drawBlurredWallpaper(canvas,target,bounds,20,palette));
  check(canvas.tx==-90&&canvas.ty==-280&&canvas.sx==2&&canvas.sy==2);
  BitmapShader shader=host.surfaceShader;
  target.x=1300;check(host.drawBlurredWallpaper(canvas,target,bounds,20,palette));
  check(canvas.tx==-1290&&host.surfaceShader==shader); // offscreen samples extend edge instead of exposing a solid rectangle
  target.x=200;check(host.drawBlurredWallpaper(canvas,target,bounds,20,palette));check(canvas.tx==-190);
  check(canvas.draws==12); // four rounded draws per frame, no temporary bitmap or screenshot
  host.blurredWallpaper=new Bitmap();host.drawBlurredWallpaper(canvas,target,bounds,20,palette);check(host.surfaceShader!=shader);
  host.blurredWallpaper=null;check(!host.drawBlurredWallpaper(canvas,target,bounds,20,palette));
  System.out.println("Acrylic translation checks passed: 10 (production draw method, graphics transport doubles)");
 }
}
'''.replace('__METHOD__', method)
out = ROOT / 'build/acrylic-translation-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'AcrylicTranslationCheck.java'
file.write_text(runner, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'AcrylicTranslationCheck'], check=True)
