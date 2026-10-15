"""Run production schedule refresh and renderer with worker/frame transport doubles."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
source = (ROOT / 'app/src/main/java/com/donglan/chrona/DashboardActivity.java').read_text(encoding='utf-8')

def method(signature):
    start = source.index(signature)
    brace = source.index('{', start)
    end, depth = brace + 1, 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]

methods = '\n'.join(method(x) for x in (
    'private void cancelScheduleResults(', 'private void requestScheduleResults()', 'private void requestScheduleResults(boolean',
    'private boolean scheduleResultCurrent(', 'private void enqueueScheduleResult(',
    'private void buildScheduleResultFrame(', 'private void renderScheduleResults(', 'private void updateResultsWithEntrance(', 'private String inboxCategory(',
    'private ScheduleQuery buildScheduleQuery('
))
java = r'''
import java.util.*;
import java.time.*;
import java.util.function.*;
class LinearLayout {
 int rows, headings, footer; boolean removed;
 Queue<Runnable> frames=new ArrayDeque<>();
 void removeAllViews(){rows=headings=footer=0;removed=true;}
 void removeCallbacks(Runnable r){frames.removeIf(x->x==r);}
 void postOnAnimation(Runnable r){frames.add(r);}
 void frame(){Runnable r=frames.poll();if(r!=null)r.run();}
}
class ScrollView {int y; Queue<Runnable> posts=new ArrayDeque<>();int getScrollY(){return y;}void scrollTo(int x,int value){y=value;}void post(Runnable r){posts.add(r);}void drain(){while(!posts.isEmpty())posts.remove().run();}}
class QueueExecutor {boolean worker;Queue<Runnable> tasks=new ArrayDeque<>();void execute(Runnable r){tasks.add(r);}void run(){worker=true;try{tasks.remove().run();}finally{worker=false;}}}
class Handler {Queue<Runnable> tasks=new ArrayDeque<>();void post(Runnable r){tasks.add(r);}void drain(){while(!tasks.isEmpty())tasks.remove().run();}}
class ScheduleQuery {Object[] args;ScheduleQuery(Object...a){args=a;}static int PAGE_SIZE=50,TODO_ANY=0,TODO_TODAY_DUE=1,TODO_OVERDUE=2,TODO_OPEN=3;}
class InboxQuery {InboxQuery(Object...a){}}
class EventCategory {static String[] VALUES={"event"};}
class EventCandidate {Long anchor(){return startAtMillis;}Long startAtMillis=1L,dueAtMillis=2L; boolean allDay;}
class TaskStore {List<Long> queryScheduleIds(ScheduleQuery q){throw new AssertionError("SQL on UI");}List<EventCandidate> querySchedulePage(ScheduleQuery q,boolean o,int p){throw new AssertionError("SQL on UI");}}
class AllDayDates {static String displayStart(long t){return "2026-10-13";}static String localDate(long t,ZoneId z){return "2026-10-13";}}
class HomeTimelinePreferences {static boolean includesSystemCalendar(Object c){return true;}}
class UiMotion {static int settled,enters,exits;static Runnable replace;static void resetList(Object l){replace=null;}static void exitList(Object l,Runnable r){exits++;replace=r;}static void enterList(Object l){enters++;}static void settleScroll(ScrollView s,LinearLayout l){settled++;}}
class Feedback {static int failures;static void show(Object c,String s){failures++;}}
class DashboardSectionData {
 List<Long> ids=new ArrayList<>();List<EventCandidate> candidates=new ArrayList<>();int page;long revision=9;
 static int reads, count=55;static boolean fail;static ScheduleQuery last;
 static DashboardSectionData load(DashboardRefreshCheck c,int destination,InboxQuery i,ScheduleQuery q,boolean o,int p,boolean system){
  if(!c.startupReader.worker)throw new AssertionError("read outside worker");reads++;last=q;
  if(fail)throw new RuntimeException("read failed");
  DashboardSectionData d=new DashboardSectionData();d.page=p;
  for(int n=0;n<count;n++){d.ids.add((long)n);d.candidates.add(new EventCandidate());}return d;
 }
}
public class DashboardRefreshCheck {
 static int checks,throwAt=-1;static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
 static final int SCHEDULE=2,INBOX=1;
 int section=SCHEDULE,scheduleResultGeneration,resultMotionGeneration,scheduleSource,scheduleTab,scheduleCategory,schedulePublication,schedulePage,statusIndex,categoryIndex,scheduleTodoScope;
 boolean scheduleResultLoading,scheduleOldestFirst,destroyed,finishing;int updates;
 String scheduleQuery="original",inboxQuery="";
 LinearLayout results=new LinearLayout(),scheduleBuildTarget;
 ScrollView scroll=new ScrollView();
 ArrayDeque<Runnable> scheduleBuildSteps;Runnable scheduleBuildDone;
 final Runnable scheduleBuildFrame=this::buildScheduleResultFrame;
 Set<Integer> dirtySections=new HashSet<>();Set<Long> selectedScheduleIds=new HashSet<>();
 Map<Long,Object> inboxRows=new HashMap<>();List<Long> matchingInboxIds=new ArrayList<>();
 Map<Integer,Object> sectionPages=new HashMap<>(),sectionVersions=new HashMap<>();
 DashboardSectionData preparedSection;
 QueueExecutor startupReader=new QueueExecutor();Handler refresh=new Handler();
 static class SectionPage {}
 DashboardRefreshCheck getApplicationContext(){return this;}
 boolean isDestroyed(){return destroyed;}boolean isFinishing(){return finishing;}
 LocalDate[] scheduleWindow(boolean system){return new LocalDate[]{LocalDate.of(2026,10,1),LocalDate.of(2026,11,1)};}
 String snapshotForRevision(long revision){return "revision:"+revision;}
 void addScheduleFilterSummary(){}void updateSelectionUi(){}void addInboxResultsHeading(int total){}
 void empty(LinearLayout target,String message){}
 void renderSystemSchedule(TaskStore store) {enqueueScheduleResult(()->results.rows++);}
 void candidateRow(LinearLayout target,EventCandidate item,int i){if(i==throwAt)throw new RuntimeException("view creation failed");target.rows++;}
 void scheduleDayHeading(String day){results.headings++;}
 void schedulePageControls(int total){results.footer++;}
 void updateResults(){updates++;resultMotionGeneration++;results.rows=2;}
 __METHODS__
 void workerCommit(){startupReader.run();refresh.drain();}
 void complete(){int frames=0;while(!results.frames.isEmpty()){int before=results.rows;results.frame();check(results.rows-before<=1,"one row maximum per frame");if(++frames>1000)throw new AssertionError("endless build");}scroll.drain();}
 public static void main(String[] args){
  DashboardRefreshCheck d=new DashboardRefreshCheck();d.results.rows=7;d.scroll.y=123;d.scheduleTodoScope=ScheduleQuery.TODO_TODAY_DUE;
  d.requestScheduleResults();check(d.results.rows==7&&!d.results.removed,"old content retained before load");check(DashboardSectionData.reads==0,"no synchronous query");
  d.scheduleQuery="changed without second request";d.workerCommit();check(DashboardSectionData.last.args[3].equals("original"),"capture filter state");
  check(DashboardSectionData.last.args[8].equals(ScheduleQuery.TODO_TODAY_DUE),"worker query captures deadline scope alongside ordinary filters");
  check(d.results.rows==1&&d.results.footer==0&&d.scheduleResultLoading,"first callback bounded to one row");
  d.complete();check(d.results.rows==55&&d.results.headings==1&&d.results.footer==1,"complete ordered rows and footer");
  check(!d.scheduleResultLoading&&!d.dirtySections.contains(SCHEDULE)&&d.scroll.y==123,"completion restores scroll and marks cache coherent");
  check(d.sectionVersions.get(SCHEDULE).equals("revision:9"),"cached revision from worker without extra UI query");
  DashboardRefreshCheck rapid=new DashboardRefreshCheck();rapid.results.rows=7;rapid.requestScheduleResults();rapid.scheduleQuery="latest";rapid.requestScheduleResults();
  rapid.workerCommit();check(rapid.results.rows==7&&!rapid.results.removed,"older worker result discarded");rapid.workerCommit();check(rapid.results.rows==1,"latest starts");
  rapid.requestScheduleResults();check(rapid.results.frames.isEmpty(),"new request removes pending old build callback");rapid.workerCommit();rapid.complete();check(rapid.results.rows==55,"latest builds exactly once");
  DashboardRefreshCheck pause=new DashboardRefreshCheck();pause.requestScheduleResults();pause.workerCommit();pause.cancelScheduleResults();check(pause.results.frames.isEmpty()&&pause.dirtySections.contains(SCHEDULE),"partial cancellation marks dirty and removes frame callback");pause.refresh.drain();check(pause.results.rows==1,"cancelled frame cannot append");
  DashboardRefreshCheck leave=new DashboardRefreshCheck();leave.results.rows=8;leave.requestScheduleResults();leave.section=INBOX;leave.workerCommit();check(leave.results.rows==8,"leaving page rejects stale load");
  DashboardRefreshCheck replace=new DashboardRefreshCheck();replace.requestScheduleResults();replace.results=new LinearLayout();replace.workerCommit();check(replace.results.rows==0,"new results identity rejects old data");
  for(boolean finished:new boolean[]{false,true}){DashboardRefreshCheck dead=new DashboardRefreshCheck();dead.requestScheduleResults();dead.destroyed=!finished;dead.finishing=finished;dead.workerCommit();check(dead.results.rows==0,"destroyed or finishing activity rejects callback");}
  DashboardRefreshCheck failure=new DashboardRefreshCheck();failure.results.rows=4;DashboardSectionData.fail=true;failure.requestScheduleResults();failure.workerCommit();check(failure.results.rows==4&&!failure.scheduleResultLoading&&Feedback.failures==1,"error retains rows and releases busy state");DashboardSectionData.fail=false;failure.requestScheduleResults();failure.workerCommit();failure.complete();check(failure.results.rows==55,"retry succeeds");
  DashboardRefreshCheck rowFailure=new DashboardRefreshCheck();throwAt=3;rowFailure.requestScheduleResults();rowFailure.workerCommit();rowFailure.complete();check(!rowFailure.scheduleResultLoading&&rowFailure.dirtySections.contains(SCHEDULE)&&rowFailure.results.rows==3,"frame creation failure cancels safely and leaves retryable cache");throwAt=-1;rowFailure.requestScheduleResults();rowFailure.workerCommit();rowFailure.complete();check(rowFailure.results.rows==55,"retry after frame creation failure succeeds");
  DashboardRefreshCheck animated=new DashboardRefreshCheck();animated.results.rows=7;animated.requestScheduleResults(true);animated.workerCommit();check(animated.results.rows==7&&UiMotion.exits==1,"exit only after worker snapshot and old rows retained until exit completes");Runnable swap=UiMotion.replace;swap.run();check(animated.results.rows==1&&UiMotion.enters==1,"shared enter begins with bounded first row");animated.complete();
  DashboardRefreshCheck obsolete=new DashboardRefreshCheck();obsolete.requestScheduleResults(true);obsolete.workerCommit();Runnable stale=UiMotion.replace;obsolete.cancelScheduleResults();stale.run();check(obsolete.results.rows==0,"superseded exit cannot rebuild results");
  DashboardRefreshCheck inbox=new DashboardRefreshCheck();inbox.section=INBOX;inbox.results.rows=7;inbox.updateResultsWithEntrance();check(inbox.updates==0&&inbox.results.rows==7,"inbox uses shared exit before replacing");Runnable inboxSwap=UiMotion.replace;int entered=UiMotion.enters;inboxSwap.run();check(inbox.updates==1&&inbox.results.rows==2&&UiMotion.enters==entered+1,"inbox shares enter with schedule");inbox.updateResultsWithEntrance();Runnable ignored=UiMotion.replace;inbox.results=new LinearLayout();ignored.run();check(inbox.updates==1,"inbox stale exit does not rebuild replaced page");
  DashboardRefreshCheck sync=new DashboardRefreshCheck();DashboardSectionData.count=0;sync.requestScheduleResults();sync.workerCommit();check(sync.results.footer==1&&!sync.scheduleResultLoading,"empty list completes without an extra frame");
  System.out.println("Schedule refresh passed: "+checks+" checks (production controller/renderer, worker and frame doubles)");
 }
}
'''.replace('__METHODS__', methods)
out = ROOT / 'build/schedule-refresh-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'DashboardRefreshCheck.java'
file.write_text(java, encoding='utf-8')
bin_dir = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(bin_dir / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file)], check=True)
subprocess.run([str(bin_dir / 'java.exe'), '-cp', str(out), 'DashboardRefreshCheck'], check=True)
