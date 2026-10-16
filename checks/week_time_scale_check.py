"""Run actual week time coordinates; no Android or calendar writes."""
import os
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
out = root / 'build/week-time-scale-check'
out.mkdir(parents=True, exist_ok=True)
src = root / 'app/src/main/java/com/donglan/chrona'
check = out / 'WeekTimeScaleCheck.java'
check.write_text('''package com.donglan.chrona;
public class WeekTimeScaleCheck {
 static int checks;
 static void check(boolean b){checks++;if(!b)throw new AssertionError("time scale "+checks);}
 public static void main(String[] args){
  check(!WeekTimelineScale.occupiesQuietHours(0,60));
  check(!WeekTimelineScale.occupiesQuietHours(480,500));
  for(double[] range:new double[][]{{59,61},{60,75},{479,480},{0,1440},{420,540}})
   check(WeekTimelineScale.occupiesQuietHours(range[0],range[1]));
  for(double hour:new double[]{28,72,144})for(boolean folded:new boolean[]{false,true}){
   WeekTimelineScale scale=new WeekTimelineScale(folded,hour,32);
   double previous=-1;
   for(int minute=0;minute<=1440;minute++){
    double y=scale.y(minute);check(y>previous);previous=y;
    check(Math.abs(scale.minuteAt(y)-minute)<.00001);
   }
   check(scale.y(60)==hour);
   check(Math.abs((scale.y(600)-scale.y(540))-hour)<.00001);
   check(folded ? scale.y(480)-scale.y(60)<=72 : scale.y(1440)==24*hour);
   if(folded)check(scale.y(1440)<24*hour);
  }
  System.out.println("Week time scale: "+checks+" checks passed");
 }
}''', encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out),
                str(src / 'WeekTimelineScale.java'), str(check)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.WeekTimeScaleCheck'], check=True)

activity = (src / 'CalendarOverviewActivity.java').read_text(encoding='utf-8')
assert '!snapshot.systemUnavailable && !snapshot.truncated' in activity
assert 'geometry.timeScale.minuteAt' in activity and 'geometry.y(minuteAtFocus)' in activity
home = (src / 'DashboardActivity.java').read_text(encoding='utf-8')
assert 'PressFeedback.group(summary, 1.05f)' in home
assert 'details.setAddStatesFromChildren(true)' in home
assert 'PressFeedback.edgeSpace(b - t)' in home
