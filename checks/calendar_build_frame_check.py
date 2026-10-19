"""Production calendar construction scheduler with frame/clock transport doubles."""
import os, re
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/java/com/donglan/chrona/CalendarOverviewPage.java').read_text(encoding='utf-8')
def method(name):
    start = source.index('    private void ' + name + '(')
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end].replace('System.nanoTime()', 'Clock.now')

methods = method('enqueueCalendarBuild') + method('buildCalendarFrame') + method('cancelCalendarBuild')
runner = r'''
import java.util.*;
public class CalendarBuildFrameCheck {
 static class Clock { static long now; }
 static class Body { ArrayDeque<Runnable> frames=new ArrayDeque<>();void postOnAnimation(Runnable job){frames.add(job);}void tick(){frames.removeFirst().run();}void removeCallbacks(Runnable job){frames.removeIf(item->item==job);} }
 __CONSTANTS__
 private ArrayDeque<Runnable> calendarBuildSteps;
 private Runnable calendarBuildDone;
 private final Runnable calendarBuildFrame=this::buildCalendarFrame;
 private final Body body=new Body();
 private String renderedKey="current";
 __METHODS__
 static int checks;
 static void check(boolean value){checks++;if(!value)throw new AssertionError();}
 public static void main(String[] args){
  CalendarBuildFrameCheck c=new CalendarBuildFrameCheck();List<Integer> built=new ArrayList<>();int[] done={0};
  c.enqueueCalendarBuild(()->built.add(0));check(built.equals(List.of(0)));
  c.calendarBuildSteps=new ArrayDeque<>();c.calendarBuildDone=()->done[0]++;
  for(int i=1;i<=6;i++){int row=i;c.enqueueCalendarBuild(()->{built.add(row);Clock.now+=5_000_000L;});}
  check(built.size()==1);c.buildCalendarFrame();check(built.equals(List.of(0,1)));check(c.body.frames.size()==1&&done[0]==0);
  for(int i=0;i<5;i++)c.body.tick();check(built.equals(List.of(0,1,2,3,4,5,6)));check(done[0]==1&&c.calendarBuildSteps==null&&c.body.frames.isEmpty());
  c.calendarBuildSteps=new ArrayDeque<>();c.calendarBuildDone=()->done[0]++;
  c.enqueueCalendarBuild(()->{built.add(7);c.enqueueCalendarBuild(()->{built.add(8);Clock.now+=5_000_000L;});});c.buildCalendarFrame();
  check(built.subList(7,9).equals(List.of(7,8)));check(done[0]==2);
  c.calendarBuildSteps=new ArrayDeque<>();c.calendarBuildDone=()->done[0]++;
  c.enqueueCalendarBuild(()->Clock.now+=5_000_000L);c.enqueueCalendarBuild(()->{throw new AssertionError("Cancelled build executed");});
  c.buildCalendarFrame();c.cancelCalendarBuild();c.buildCalendarFrame();check(done[0]==2);check(c.body.frames.isEmpty());
  check(c.renderedKey.isEmpty()&&c.calendarBuildDone==null);c.cancelCalendarBuild();check(c.body.frames.isEmpty());
  c.calendarBuildSteps=new ArrayDeque<>();int[] fast={0};for(int i=0;i<34;i++)c.enqueueCalendarBuild(()->fast[0]++);c.buildCalendarFrame();check(fast[0]==16);c.body.tick();check(fast[0]==32);c.body.tick();check(fast[0]==34&&c.calendarBuildSteps==null);
  c.calendarBuildSteps=new ArrayDeque<>();fast[0]=0;for(int i=0;i<20;i++)c.enqueueCalendarBuild(()->{fast[0]++;Clock.now+=500_000L;});c.buildCalendarFrame();check(fast[0]==4);c.cancelCalendarBuild();
  System.out.println("Calendar build frame checks passed: "+checks+" (production methods, frame and clock doubles)");
 }
}
'''.replace('__METHODS__', methods).replace('__CONSTANTS__', '\n'.join(re.findall(r'private static final (?:long|int) BUILD_FRAME_\w+ = [^;]+;', source)))
out = root / 'build/calendar-build-frame-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'CalendarBuildFrameCheck.java'
file.write_text(runner, encoding='utf-8')
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'), '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'CalendarBuildFrameCheck'], check=True)
