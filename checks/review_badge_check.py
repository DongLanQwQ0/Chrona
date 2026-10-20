"""Execute production inbox refresh/count/badge methods with lightweight View transport doubles."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'app/src/main/java/com/donglan/chrona/DashboardActivity.java').read_text(encoding='utf-8')


def method(signature):
    start = source.index(signature)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


methods = '\n'.join(method(x) for x in ('private void updateResults()',
    'private void updatePendingCounts(', 'private void updateFilterCount(', 'private DockNavigationLayout filterDock('))
java = r'''
import java.util.*;
class View {
 static final int VISIBLE=0,GONE=8;Object tag;int visibility=VISIBLE;String description="";
 void setTag(Object value){tag=value;}void setVisibility(int value){visibility=value;}void setContentDescription(String value){description=value;}
 void setSelected(boolean value){}void setMinimumWidth(int value){}void setPadding(int a,int b,int c,int d){}int getPaddingLeft(){return 4;}int getPaddingRight(){return 4;}
 @SuppressWarnings("unchecked") <T extends View>T findViewWithTag(Object value){return Objects.equals(tag,value)?(T)this:null;}
}
class Paint {float measureText(String value){return value.length()*14f;}}
class TextView extends View {static final int AUTO_SIZE_TEXT_TYPE_NONE=0;String value="";void setText(String s){value=s;}void setGravity(int x){}void setIncludeFontPadding(boolean x){}void setOnClickListener(java.util.function.Consumer<View> c){}Paint getPaint(){return new Paint();}void setAutoSizeTextTypeWithDefaults(int type){}}
class LinearLayout extends View {void removeAllViews(){}static class LayoutParams{LayoutParams(int w,int h,int weight){}}}
class FrameLayout extends View {
 List<View>children=new ArrayList<>();FrameLayout(Object c){}void setClipChildren(boolean x){}void setClipToPadding(boolean x){}
 void addView(View v,LayoutParams p){children.add(v);}static class LayoutParams{LayoutParams(int w,int h){}LayoutParams(int w,int h,int g){}void setMargins(int a,int b,int c,int d){}}
 @Override <T extends View>T findViewWithTag(Object tag){for(View c:children){T v=c.findViewWithTag(tag);if(v!=null)return v;}return super.findViewWithTag(tag);}
}
class DockNavigationLayout extends View {
 List<View>items=new ArrayList<>();int selected;DockNavigationLayout(Object c,java.util.function.IntConsumer a,Runnable r){}
 void setPadding(int a,int b,int c,int d){}void addItem(View v,LinearLayout.LayoutParams p){items.add(v);}View itemAt(int i){return items.get(i);}
 void setSelectedIndex(int i){selected=i;}void finishPageSelection(int i){selected=i;}
}
class Gravity {static final int CENTER=1,TOP=2,END=4;}
class ScrollView {int y=42;int getScrollY(){return y;}void scrollTo(int x,int v){y=v;}}
class UiMotion {static void resetList(Object r){}static void settleScroll(Object s,Object r){}}
class TaskRecord {static final String NEEDS_REVIEW="needs_review",FAILED="failed";}
class TaskStore implements AutoCloseable {
 static int review=1,failed=0,opened,closed;TaskStore(Object c){opened++;}int taskCountByStatus(String status){return status.equals(TaskRecord.NEEDS_REVIEW)?review:failed;}public void close(){closed++;}
}
public class ReviewBadgeCheck {
 static final int INBOX=1,SCHEDULE=2,HOME=0;static int checks;
 static void check(boolean v,String m){checks++;if(!v)throw new AssertionError(m);}
 int section=INBOX,resultMotionGeneration,pendingReview,pendingFailed,navigationCount,navigationUpdates,remembered,rendered;
 LinearLayout results=new LinearLayout();ScrollView scroll=new ScrollView();Object navigation=new Object();
 DockNavigationLayout filterChips;Map<Integer,SectionPage>sectionPages=new HashMap<>();List<View>strips=new ArrayList<>();
 static class SectionPage {DockNavigationLayout filterChips;SectionPage(DockNavigationLayout d){filterChips=d;}}
 int dp(int v){return v;}TextView text(String s,int z,boolean b){TextView t=new TextView();t.setText(s);return t;}
 TextView countBadge(int n){return text(n>99?"99+":Integer.toString(n),11,true);}
 void drawNavigation(){navigationCount=pendingReview+pendingFailed;navigationUpdates++;}
 void updateCompactFilterSelection(DockNavigationLayout dock,int selected){}
 void requestScheduleResults(){throw new AssertionError("wrong path");}void renderScheduleResults(TaskStore s){throw new AssertionError("wrong path");}
 void renderInboxResults(TaskStore s){check(pendingReview==TaskStore.review&&pendingFailed==TaskStore.failed,"counts synchronized before rows");rendered++;}
 void message(Object c,String s){throw new AssertionError(s);}
 void rememberRenderedPage(){check(pendingReview==TaskStore.review&&pendingFailed==TaskStore.failed&&navigationCount==pendingReview+pendingFailed,"counts/navigation updated before revision bookkeeping");remembered++;}
 __METHODS__
 DockNavigationLayout inbox(int review,int failed){return filterDock(new String[]{"全部","待确认","处理中","失败","已写入"},1,i->{},new int[]{0,review,0,failed,0});}
 static TextView badge(DockNavigationLayout d,int i){return d.itemAt(i).findViewWithTag("filter_count_badge");}
 public static void main(String[] args){
  ReviewBadgeCheck d=new ReviewBadgeCheck();d.filterChips=d.inbox(1,0);TextView review=badge(d.filterChips,1),failed=badge(d.filterChips,3);
  check(review.visibility==View.VISIBLE&&failed.visibility==View.GONE,"initial counts visibility");
  TaskStore.review=0;TaskStore.failed=2;d.updateResults();
  check(review.visibility==View.GONE&&review.value.equals("0"),"confirm final record hides review badge");
  check(failed.visibility==View.VISIBLE&&failed.value.equals("2")&&d.navigationCount==2,"failure count and navigation synchronize");
  TaskStore.review=1;TaskStore.failed=0;d.updateResults();
  check(badge(d.filterChips,1)==review&&review.visibility==View.VISIBLE&&review.value.equals("1"),"zero to one restores same badge without dock rebuild");
  check(failed.visibility==View.GONE&&d.navigationCount==1&&d.navigationUpdates==2,"failure zero hides and navigation updates");
  check(d.filterChips.selected==1&&d.scroll.y==42&&d.rendered==2&&d.remembered==2,"refresh preserves selected filter and scroll");
  ReviewBadgeCheck zero=new ReviewBadgeCheck();zero.filterChips=zero.inbox(0,0);TextView zeroBadge=badge(zero.filterChips,1);zero.updatePendingCounts(1,0);
  check(zeroBadge!=null&&zeroBadge.visibility==View.VISIBLE,"initially zero badge remains updateable");
  ReviewBadgeCheck cached=new ReviewBadgeCheck();cached.section=SCHEDULE;DockNavigationLayout hidden=cached.inbox(1,0);cached.sectionPages.put(INBOX,new SectionPage(hidden));cached.filterChips=cached.filterDock(new String[]{"日程"},0,i->{},null);cached.updatePendingCounts(0,4);
  check(badge(hidden,1).visibility==View.GONE&&badge(hidden,3).value.equals("4")&&cached.navigationCount==4,"schedule counts update cached inbox and navigation without touching schedule chips");
  cached.updatePendingCounts(120,0);check(badge(hidden,1).value.equals("99+")&&badge(hidden,1).description.equals("待确认 120 条收件"),"large counts retain exact accessibility value");
  ReviewBadgeCheck empty=new ReviewBadgeCheck();empty.section=HOME;empty.filterChips=null;empty.updatePendingCounts(0,0);check(empty.navigationCount==0,"count update works before inbox page exists");
  check(TaskStore.opened==TaskStore.closed&&TaskStore.opened==2,"refresh closes same store used for counts and list");
  System.out.println("Review badge passed: "+checks+" checks (production refresh/helper/dock methods, View transport doubles)");
 }
}
'''.replace('__METHODS__', methods)
out = ROOT / 'build/review-badge-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'ReviewBadgeCheck.java'
file.write_text(java, encoding='utf-8')
bin_dir = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(bin_dir / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(bin_dir / 'java.exe'), '-cp', str(out), 'ReviewBadgeCheck'], check=True)
