"""Run production DateIntervalBar text delivery against a deterministic animation clock."""
from pathlib import Path
import os
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/com/donglan/chrona/DateIntervalBar.java").read_text(encoding="utf-8")
out = root / "build/page-audit-next/pagination"
out.mkdir(parents=True, exist_ok=True)


def method(signature):
    start = source.index(signature)
    end = source.index("{", start) + 1
    depth = 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[start:end]


methods = "\n".join(method(signature) for signature in (
    "void setText(", "private void cancelMotion(", "private void settle(", "private void shift("))
java = r'''
import java.util.*;
import java.util.function.IntPredicate;
class Clock {
 static long now;static long order;static PriorityQueue<Job> jobs=new PriorityQueue<>(Comparator.comparingLong((Job j)->j.time).thenComparingLong(j->j.order));
 static class Job {long time,order;Runnable action;boolean cancelled;Job(long t,Runnable r){time=t;order=Clock.order++;action=r;}}
 static Job after(long ms,Runnable r){Job j=new Job(now+ms,r);jobs.add(j);return j;}
 static void until(long time){while(!jobs.isEmpty()&&jobs.peek().time<=time){Job j=jobs.remove();now=j.time;if(!j.cancelled)j.action.run();}now=time;}
 static void reset(){jobs.clear();now=order=0;}
}
class UiMotion {static boolean enabled=true;static boolean isEnabled(){return enabled;}}
class Label {
 String text="";float x,alpha=1;Animator animator=new Animator(this);
 void setText(String s){text=s;}void setTranslationX(float n){x=n;}void setAlpha(float n){alpha=n;}int getWidth(){return 100;}Animator animate(){return animator;}
}
class Animator {
 final Label label;float targetX,targetAlpha=1;long duration;Runnable end;Clock.Job job;
 Animator(Label label){this.label=label;}Animator translationX(float n){targetX=n;return this;}Animator alpha(float n){targetAlpha=n;return this;}
 Animator setDuration(long ms){duration=ms;return this;}Animator withEndAction(Runnable r){end=r;return this;}
 void cancel(){if(job!=null)job.cancelled=true;job=null;}
 void start(){float x=targetX,a=targetAlpha;Runnable done=end;job=Clock.after(duration,()->{label.x=x;label.alpha=a;if(done!=null)done.run();});}
}
public class DateIntervalBarMotionCheck {
 final Label value=new Label();final IntPredicate shift;String desired="";boolean moving;int generation,layouts;
 DateIntervalBarMotionCheck(IntPredicate shift){this.shift=shift;}String displayText(){return desired;}void requestLayout(){layouts++;}
 @@METHODS@@
 static int checks;static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
 public static void main(String[]args){
  for(int delay:new int[]{20,170,350}){
   Clock.reset();UiMotion.enabled=true;DateIntervalBarMotionCheck[] rail={null};
   rail[0]=new DateIntervalBarMotionCheck(direction->{Clock.after(delay,()->rail[0].setText("2 / 5"));return true;});
   rail[0].setText("1 / 5");rail[0].shift(1);Clock.until(110);
   check(rail[0].moving&&rail[0].value.x==100&&rail[0].value.alpha==0,"110ms exit starts incoming with old or latest label");
   if(delay==170){Clock.until(170);check(rail[0].desired.equals("2 / 5")&&rail[0].value.text.equals("1 / 5")&&rail[0].moving,"170ms result reproduces stale visible text during incoming phase");}
   Clock.until(400);
   check(rail[0].value.text.equals("2 / 5")&&rail[0].desired.equals(rail[0].value.text),"latest result is actually displayed after delay "+delay);
   check(!rail[0].moving&&rail[0].value.x==0&&rail[0].value.alpha==1,"animation finishes in visible settled state "+delay);
  }
  Clock.reset();UiMotion.enabled=false;DateIntervalBarMotionCheck[] reduced={null};
  reduced[0]=new DateIntervalBarMotionCheck(direction->{Clock.after(170,()->reduced[0].setText("2 / 5"));return true;});
  reduced[0].setText("1 / 5");reduced[0].shift(1);Clock.until(170);
  check(!reduced[0].moving&&reduced[0].value.text.equals("2 / 5"),"reduced animation also publishes delayed results");
  System.out.println("DateIntervalBar production animation checks passed: "+checks+"; 170ms delayed result exercised");
 }
}
'''.replace("@@METHODS@@", methods)
path = out / "DateIntervalBarMotionCheck.java"
path.write_text(java, encoding="utf-8")
suffix = ".exe" if os.name == "nt" else ""
home = os.environ.get("JAVA_HOME")
tool = lambda name: str(Path(home) / "bin" / (name + suffix)) if home else shutil.which(name)
subprocess.run([tool("javac"), "--release", "17", "-encoding", "UTF-8", "-d", str(out), str(path)], check=True)
result = subprocess.check_output([tool("java"), "-cp", str(out), "DateIntervalBarMotionCheck"], text=True)
(out / "date-interval-motion-result.txt").write_text(result, encoding="utf-8")
print(result, end="")
