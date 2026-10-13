"""Check the production pure palette cache against its uncached computation."""
import os
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/UiStyle.java').read_text(encoding='utf-8')

def block(marker):
    start = source.index(marker)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

method = block('private static synchronized Palette accentPalette(')
uncached = method.replace('synchronized Palette accentPalette', 'Palette referencePalette')
uncached = uncached.replace('if (lastAccentPalette != null && lastAccentDark == dark && lastAccentSeed == seed)\n            return lastAccentPalette;', '')
uncached = uncached.replace('lastAccentDark = dark; lastAccentSeed = seed;', '')
uncached = uncached.replace('lastAccentPalette = new Palette', 'return new Palette').replace('return lastAccentPalette;', '')
harness = r'''
import java.util.*;
public class AccentPaletteCacheCheck {
 static Palette lastAccentPalette; static boolean lastAccentDark; static int lastAccentSeed;
 static class Color {
  static final int BLACK=0xff000000, WHITE=0xffffffff; static int conversions;
  static void colorToHSV(int color,float[] hsv){conversions++;float[] hsb=java.awt.Color.RGBtoHSB((color>>16)&255,(color>>8)&255,color&255,null);hsv[0]=hsb[0]*360;hsv[1]=hsb[1];hsv[2]=hsb[2];}
  static int HSVToColor(float[] hsv){conversions++;return java.awt.Color.HSBtoRGB(hsv[0]/360,hsv[1],hsv[2]);}
  static float luminance(int color){return (((color>>16)&255)+((color>>8)&255)+(color&255))/765f;}
 }
 __PALETTE__
 __METHOD__
 __REFERENCE__
 static void check(boolean ok){if(!ok)throw new AssertionError();}
 static void equal(Palette a,Palette b) throws Exception {
  for(var field:Palette.class.getDeclaredFields())check(field.getInt(a)==field.getInt(b));
 }
 public static void main(String[] args) throws Exception {
  Palette first=accentPalette(false,0xff7353ba);int conversions=Color.conversions;
  for(int i=0;i<10000;i++)check(accentPalette(false,0xff7353ba)==first);
  check(Color.conversions==conversions);
  for(int seed:new int[]{0xff7353ba,0xff000000,0xffeeeeee,0xff123456})for(boolean dark:new boolean[]{false,true,false})equal(accentPalette(dark,seed),referencePalette(dark,seed));
  List<Throwable> failures=Collections.synchronizedList(new ArrayList<>());List<Thread> workers=new ArrayList<>();
  for(int i=0;i<4;i++){final int thread=i;Thread worker=new Thread(()->{try{for(int n=0;n<300;n++){int seed=0xff001234+thread*1000+n;boolean dark=(n&1)==0;equal(accentPalette(dark,seed),referencePalette(dark,seed));}}catch(Throwable error){failures.add(error);}});workers.add(worker);worker.start();}
  for(Thread worker:workers)worker.join();check(failures.isEmpty());
  System.out.println("Accent palette cache passed: unchanged inputs skip conversions; mode/seed changes and 1200 concurrent results match uncached production computation");
 }
}
'''.replace('__PALETTE__', block('static final class Palette {')).replace('__METHOD__', method).replace('__REFERENCE__', uncached)
out = root / 'build/accent-palette-cache-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'AccentPaletteCacheCheck.java'
file.write_text(harness, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'AccentPaletteCacheCheck'], check=True)
