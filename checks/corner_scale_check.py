"""Execute production radius anchors, default and fixed geometry."""
from pathlib import Path
import os
import subprocess
root = Path(__file__).resolve().parents[1]
out = root / 'build/corner-scale-check'
out.mkdir(parents=True, exist_ok=True)
source = out / 'CornerScaleCheck.java'
source.write_text('''package com.donglan.chrona;
public final class CornerScaleCheck {
 public static void main(String[] args) {
  int[][] expected={{4,5,6,4,3,4},{8,10,12,8,6,8},{13,16,20,16,9,18},{18,22,28,24,12,999},{24,30,36,32,16,999}};
  if(CornerScale.DEFAULT_LEVEL!=4)throw new AssertionError("default");
  for(int level=1;level<=5;level++)for(int i=0;i<6;i++)
   if(CornerScale.radius(-i-1,level)!=expected[level-1][i])throw new AssertionError("level "+level);
  for(int fixed:new int[]{0,6,8,28,999})for(int level=1;level<=5;level++)
   if(CornerScale.radius(fixed,level)!=fixed)throw new AssertionError("fixed geometry");
  if(CornerScale.clamp(0)!=1||CornerScale.clamp(6)!=5)throw new AssertionError("bounds");
  System.out.println("CornerScale PASS: all five levels, default, fixed Dock/circles, bounds");
 }
}''', encoding='utf-8')
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
subprocess.run([str(java/'javac.exe'), '--release', '17', '-d', str(out), str(source),
               str(root/'app/src/main/java/com/donglan/chrona/CornerScale.java')], check=True)
subprocess.run([str(java/'java.exe'), '-cp', str(out), 'com.donglan.chrona.CornerScaleCheck'], check=True)
