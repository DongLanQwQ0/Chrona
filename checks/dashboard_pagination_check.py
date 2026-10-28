"""Execute the production pagination controller with host transport/layout doubles."""
from pathlib import Path
import os
import shutil
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / "app/src/main/java/com/donglan/chrona/DashboardActivity.java").read_text(encoding="utf-8")
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
    "private void inboxPageControls(", "private void schedulePageControls(",
    "private void bindListPagination(", "private void chooseListPage(",
    "private boolean changeInboxPage(", "private boolean changeSchedulePage("))
assert "pageArrow(" not in source, "The old separately surfaced pager arrows are gone"
assert "UiStyle.addSpaced(content, bar, 12, 0)" in methods, "Footer stays outside rebuilt rows"
assert "if (!previewRender && pager != null) pager.addGesturePriorityChild(bar)" in methods
java = r'''
import java.util.*;
import java.util.function.*;
class View {
 static final int VISIBLE=0,GONE=8;ViewGroup parent;String description;Object tag;boolean enabled=true;int visibility;
 Object getParent(){return parent;}void setContentDescription(String s){description=s;}void setTag(Object t){tag=t;}
 void setEnabled(boolean b){enabled=b;}void setVisibility(int v){visibility=v;}
}
class ViewGroup extends View {
 List<View> children=new ArrayList<>();int detached;
 void addView(View v){if(v.parent!=null)throw new AssertionError("already parented");children.add(v);v.parent=this;}
 void removeView(View v){children.remove(v);v.parent=null;detached++;}
 void removeAllViews(){for(View v:new ArrayList<>(children))removeView(v);}
 int getChildCount(){return children.size();}View getChildAt(int i){return children.get(i);}
}
class LinearLayout extends ViewGroup {int top=30;int getTop(){return top;}}
class ScrollView {int y;void scrollTo(int x,int y){this.y=y;}}
class DateIntervalBar extends ViewGroup {
 String text;Runnable pick;IntPredicate shift;boolean moving;
 DateIntervalBar(Object c,Runnable p,IntPredicate s){pick=p;shift=s;addView(new View());addView(new View());addView(new View());}
 void setText(String s){text=s;}
 boolean arrow(int d){if(!getChildAt(d<0?1:2).enabled)return false;moving=true;return shift.test(d);}
}
class SwipePagerLayout {List<View> priority=new ArrayList<>();void addGesturePriorityChild(View v){if(!priority.contains(v))priority.add(v);}}
class UiStyle {static void addSpaced(LinearLayout p,View v,int top,int bottom){if(top!=12||bottom!=0)throw new AssertionError("changed spacing");p.addView(v);}}
class UiDialogs {
 static String[] choices;static IntConsumer choice;static int selected;
 static void choiceDialog(Object c,String title,String[] options,int current,IntConsumer picked){choices=options;selected=current;choice=picked;}
}
class InboxQuery {static final int PAGE_SIZE=12;}class ScheduleQuery {static final int PAGE_SIZE=24;}
public class DashboardPaginationCheck {
 DateIntervalBar inboxPagination,schedulePagination;int inboxPageCount=1,schedulePageCount=1,inboxPage,schedulePage,section=1,updates;
 LinearLayout content=new LinearLayout(),results=new LinearLayout(),scheduleControls=new LinearLayout();
 ScrollView scroll=new ScrollView();List<View> strips=new ArrayList<>();SwipePagerLayout pager=new SwipePagerLayout();boolean previewRender;
 Set<Long> selected=new HashSet<>();String query="preserved";Object search=new Object();
 int dp(int n){return n;}void updateResultsWithEntrance(){updates++;}
 @@METHODS@@
 static int checks;static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
 public static void main(String[]args){
  for(boolean schedule:new boolean[]{false,true}){
   DashboardPaginationCheck d=new DashboardPaginationCheck();d.content.addView(d.results);d.selected.add(42L);Object search=d.search;
   if(schedule)d.schedulePageControls(51);else d.inboxPageControls(51);
   DateIntervalBar bar=schedule?d.schedulePagination:d.inboxPagination;int pages=schedule?3:5;
   check(bar!=null&&bar.parent==d.content&&d.content.getChildAt(1)==bar,"same scrolling footer immediately follows results");
   check(bar.text.equals("1 / "+pages)&&!bar.getChildAt(1).enabled&&bar.getChildAt(2).enabled,"initial count and boundaries "+schedule);
   check(d.pager.priority.contains(bar)&&d.strips.contains(bar),"rail registered before parent interception");
   check(!bar.shift.test(-1)&&d.updates==0,"first boundary produces no content animation");
   check(bar.arrow(1),"next arrow accepted");int page=schedule?d.schedulePage:d.inboxPage;
   check(page==1&&d.updates==1,"one accepted page step");
   d.results.removeAllViews();if(schedule)d.schedulePageControls(51);else d.inboxPageControls(51);
   check(bar==(schedule?d.schedulePagination:d.inboxPagination)&&bar.parent==d.content&&d.content.detached==0&&bar.moving,"row rebuild retains attached rail and in-progress rail motion");
   check(d.search==search&&d.query.equals("preserved")&&d.selected.contains(42L),"page change retains query, input and selection");
   bar.pick.run();check(UiDialogs.choices.length==pages&&UiDialogs.selected==1,"click uses common full page menu");
   UiDialogs.choice.accept(pages-1);if(schedule)d.schedulePageControls(51);else d.inboxPageControls(51);
   check((schedule?d.schedulePage:d.inboxPage)==pages-1&&!bar.getChildAt(2).enabled,"pick last page with disabled next");
   check(!bar.shift.test(1),"last boundary clamps gesture");
   // Search/filter renderer returns to page zero and a single-page result with no footer gap.
   if(schedule){d.schedulePage=0;d.schedulePageControls(1);}else{d.inboxPage=0;d.inboxPageControls(1);}
   check(bar.visibility==View.GONE&&bar.text.equals("1 / 1"),"search reset hides footer without reserving layout");
   UiDialogs.choice.accept(pages-1);check((schedule?d.schedulePage:d.inboxPage)==0,"stale picker clamps against latest count");
   if(schedule)d.schedulePageControls(51);else d.inboxPageControls(51);
   check(bar.visibility==View.VISIBLE&&bar==(schedule?d.schedulePagination:d.inboxPagination),"clearing search restores the same rail");
   check(d.strips.size()==1&&d.pager.priority.size()==1,"refresh does not duplicate gesture registrations");
  }
  System.out.println("Dashboard pagination host checks passed: "+checks+"; actual touch/rendering requires Android checks");
 }
}
'''.replace("@@METHODS@@", methods)
path = out / "DashboardPaginationCheck.java"
path.write_text(java, encoding="utf-8")
suffix = ".exe" if os.name == "nt" else ""
home = os.environ.get("JAVA_HOME")
tool = lambda name: str(Path(home) / "bin" / (name + suffix)) if home else shutil.which(name)
subprocess.run([tool("javac"), "--release", "17", "-encoding", "UTF-8", "-d", str(out), str(path)], check=True)
result = subprocess.check_output([tool("java"), "-cp", str(out), "DashboardPaginationCheck"], text=True)
(out / "host-result.txt").write_text(result, encoding="utf-8")
print(result, end="")
