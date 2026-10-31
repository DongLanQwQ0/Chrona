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
    'private ScheduleQuery buildScheduleQuery(', 'private String completionFilterKey(', 'private void queueHomeworkFocus('
))
# ScheduleListing composes school exams with local rows; the harness only needs its read shape.
SCHEDULE_LISTING_STUB = r'''
class ScheduleListing {
 static final class Exam { String time="",room="",seat=""; }
 static final class Row { EventCandidate candidate; Exam exam; Row(EventCandidate c,Exam e){candidate=c;exam=e;} }
 static final class Page { List<Long> ids=new ArrayList<>();List<Row> rows=new ArrayList<>();int page,total;String warning="",account="",scope="";boolean examsLoaded; }
 static Page load(Object context,Object store,ScheduleQuery query,boolean oldestFirst,int requestedPage,long focusCandidate){
  Page page=new Page();
  for(int i=0;i<DashboardSectionData.count;i++){page.ids.add((long)i);page.rows.add(new Row(new EventCandidate(),null));}
  page.total=DashboardSectionData.count;page.page=requestedPage;return page;
 }
 static String scope(Object context){return "";}
 static boolean schoolCurrent(Object context,String account){return false;}
}
'''
java = r'''
import java.util.*;
import java.time.*;
import java.util.function.*;
class LinearLayout {
 int rows, headings, footer; boolean removed;
 int paddingTop,paddingBottom;void setPadding(int left,int top,int right,int bottom){paddingTop=top;paddingBottom=bottom;}
 Queue<Runnable> frames=new ArrayDeque<>();
 void removeAllViews(){rows=headings=footer=0;removed=true;}
 void removeCallbacks(Runnable r){frames.removeIf(x->x==r);}
 void postOnAnimation(Runnable r){frames.add(r);}
 void setTag(Object tag){}
 void setOnClickListener(OnClick listener){}
 void frame(){Runnable r=frames.poll();if(r!=null)r.run();}
}
class ScrollView {int y; Queue<Runnable> posts=new ArrayDeque<>();int getScrollY(){return y;}void scrollTo(int x,int value){y=value;}void post(Runnable r){posts.add(r);}void drain(){while(!posts.isEmpty())posts.remove().run();}}
class QueueExecutor {boolean worker;Queue<Runnable> tasks=new ArrayDeque<>();void execute(Runnable r){tasks.add(r);}void run(){worker=true;try{tasks.remove().run();}finally{worker=false;}}}
class Handler {Queue<Runnable> tasks=new ArrayDeque<>();void post(Runnable r){tasks.add(r);}void postDelayed(Runnable r,long delay){post(r);}void removeCallbacks(Runnable r){tasks.removeIf(x->x==r);}void drain(){while(!tasks.isEmpty())tasks.remove().run();}}
class ScheduleQuery {Object[] args;ScheduleQuery(Object...a){args=a;}static int PAGE_SIZE=50,TODO_ANY=0,TODO_TODAY_DUE=1,TODO_OVERDUE=2,TODO_OPEN=3;static String CATEGORY_HOMEWORK="homework";boolean officialExamOnly(){return false;}}
class ZjuSettingsStore {ZjuSettingsStore(Object context){}boolean enabled(){return true;}boolean configured(){return true;}}
class InboxQuery {InboxQuery(Object...a){}}
class EventCategory {static String[] VALUES={"event"};}
class ExternalCourseDetailActivity { }
// All rows in this frame-budget fixture are explicitly timed events, not deadline-only tasks.
class EventCandidate {long id;Long overdueAtMillis(){return null;}Long anchor(){return startAtMillis;}Long effectiveDueAtMillis(){return dueAtMillis!=null?dueAtMillis:endAtMillis!=null?endAtMillis:startAtMillis;}boolean hasDefaultExecutionInterval(){return false;}Long executionStartAtMillis(){return startAtMillis;}boolean executionAllDay(){return allDay;}Long startAtMillis=1L,endAtMillis,dueAtMillis=2L; boolean allDay;}
class TaskStore {List<Long> queryScheduleIds(ScheduleQuery q){throw new AssertionError("SQL on UI");}List<EventCandidate> querySchedulePage(ScheduleQuery q,boolean o,int p){throw new AssertionError("SQL on UI");}}
class AllDayDates {static String displayStart(long t){return "2026-10-13";}static String localDate(long t,ZoneId z){return "2026-10-13";}}
class HomeTimelinePreferences {static boolean includesSystemCalendar(Object c){return true;}}
class UiMotion {static int settled,enters,exits;static Runnable replace;static void resetList(Object l){replace=null;}static void exitList(Object l,Runnable r){exits++;replace=r;}static void enterList(Object l){enters++;}static void settleScroll(ScrollView s,LinearLayout l){settled++;}}
class Feedback {static int failures;static void show(Object c,String s){failures++;}}
class R {static class drawable {static final int ic_schedule=1,ic_inbox_outline=2,ic_homework=3;}}
class Button { }
interface OnClick { void onClick(Object view); }
class CompletionRows { void row(Object view,String key,Object state){} }
class Intent { Intent(Object context,Object target){} Intent putExtra(String key,String value){return this;} Intent putExtra(String key,long value){return this;} }
class ZjuExamsActivity { }
class ZjuSettingsActivity { }
class UiStyle {static void emptyState(LinearLayout parent,int icon,String label,String action,Runnable run){}
 static void addSpaced(LinearLayout parent,Object view,int top,int bottom){}
 static void pressable(Object view){}}
class DashboardSectionData {
 List<Long> ids=new ArrayList<>();List<EventCandidate> candidates=new ArrayList<>();int page,review,failed;long revision=9;ScheduleListing.Page schedule;
 static int reads, count=55,nextReview=2,nextFailed=1;static boolean fail;static ScheduleQuery last;
 static DashboardSectionData load(DashboardRefreshCheck c,int destination,InboxQuery i,ScheduleQuery q,boolean o,int p,boolean system,long focusCandidate){
  if(!c.startupReader.worker)throw new AssertionError("read outside worker");reads++;last=q;
  if(fail)throw new RuntimeException("read failed");
  DashboardSectionData d=new DashboardSectionData();d.page=p;d.review=nextReview;d.failed=nextFailed;
  d.schedule=ScheduleListing.load(c,null,q,o,p,focusCandidate);
  for(int n=0;n<count;n++){d.ids.add((long)n);d.candidates.add(new EventCandidate());}return d;
 }
}
public class DashboardRefreshCheck {
 static int checks,throwAt=-1;static void check(boolean v,String message){checks++;if(!v)throw new AssertionError(message);}
 static final int SCHEDULE=2,INBOX=1;
 void message(LinearLayout parent,String text){ }
 void showScheduleFilters(){ }
 void startActivity(Intent intent){ }
 final CompletionRows completionRows=new CompletionRows();
 LinearLayout card(){return new LinearLayout();}
 void addScheduleCardContent(LinearLayout card,EventCandidate item,Object event,boolean a,boolean b,Object c,ScheduleListing.Exam exam){}
 Button scheduleAction(String label,Runnable run){return new Button();}
 void scheduleDayHeading(Object date){}
 int section=SCHEDULE,scheduleResultGeneration,resultMotionGeneration,scheduleSource,scheduleTab,scheduleCategory,schedulePublication,schedulePage,statusIndex,categoryIndex,scheduleTodoScope;
 boolean scheduleResultLoading,scheduleOldestFirst,destroyed,finishing;int updates,pendingReview=-1,pendingFailed=-1,countUpdates;
 String scheduleQuery="original",inboxQuery="";
 long pendingHomeworkFocus;int homeworkFocusTop,homeworkFocusBottom,scheduleRange;String homeworkFocusFilter="";
 LocalDate scheduleDate=LocalDate.of(2026,10,1),scheduleUntil=LocalDate.of(2026,11,1);
 final Runnable homeworkFocus=()->{};
 LinearLayout results=new LinearLayout(),scheduleBuildTarget;
 ScrollView scroll=new ScrollView();
 ArrayDeque<Runnable> scheduleBuildSteps;Runnable scheduleBuildDone;
 final Runnable scheduleBuildFrame=this::buildScheduleResultFrame;
 Set<Integer> dirtySections=new HashSet<>();Set<Long> selectedScheduleIds=new HashSet<>();
 Set<Long> awaitingCompletionRows=new HashSet<>();
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
 void updatePendingCounts(int review,int failed){check(!sectionVersions.containsKey(SCHEDULE),"counts applied before revision bookkeeping");pendingReview=review;pendingFailed=failed;countUpdates++;}
 __METHODS__
 void workerCommit(){startupReader.run();refresh.drain();}
 void complete(){int frames=0;while(!results.frames.isEmpty()){int before=results.rows;results.frame();check(results.rows-before<=1,"one row maximum per frame");if(++frames>1000)throw new AssertionError("endless build");}scroll.drain();}
 public static void main(String[] args){
  DashboardRefreshCheck d=new DashboardRefreshCheck();d.results.rows=7;d.scroll.y=123;d.scheduleTodoScope=ScheduleQuery.TODO_TODAY_DUE;
  d.requestScheduleResults();check(d.results.rows==7&&!d.results.removed,"old content retained before load");check(DashboardSectionData.reads==0,"no synchronous query");
  d.scheduleQuery="changed without second request";d.workerCommit();check(DashboardSectionData.last.args[3].equals("original"),"capture filter state");
  check(DashboardSectionData.last.args[8].equals(ScheduleQuery.TODO_TODAY_DUE),"worker query captures deadline scope alongside ordinary filters");
  check(d.results.rows==1&&d.results.footer==0&&d.scheduleResultLoading,"first callback bounded to one row");
  check(d.pendingReview==2&&d.pendingFailed==1&&d.countUpdates==1,"schedule snapshot applies review and failed counts");
  d.complete();check(d.results.rows==55&&d.results.headings==1&&d.results.footer==1,"complete ordered rows and footer");
  check(!d.scheduleResultLoading&&!d.dirtySections.contains(SCHEDULE)&&d.scroll.y==123,"completion restores scroll and marks cache coherent");
  check(d.sectionVersions.get(SCHEDULE).equals("revision:9"),"cached revision from worker without extra UI query");
  DashboardRefreshCheck rapid=new DashboardRefreshCheck();rapid.results.rows=7;rapid.requestScheduleResults();rapid.scheduleQuery="latest";rapid.requestScheduleResults();
  rapid.workerCommit();check(rapid.results.rows==7&&!rapid.results.removed&&rapid.countUpdates==0,"older worker result discarded without changing counts");DashboardSectionData.nextReview=0;DashboardSectionData.nextFailed=3;rapid.workerCommit();check(rapid.results.rows==1&&rapid.pendingReview==0&&rapid.pendingFailed==3&&rapid.countUpdates==1,"latest snapshot applies counts");
  rapid.requestScheduleResults();check(rapid.results.frames.isEmpty(),"new request removes pending old build callback");rapid.workerCommit();rapid.complete();check(rapid.results.rows==55,"latest builds exactly once");
  DashboardRefreshCheck pause=new DashboardRefreshCheck();pause.requestScheduleResults();pause.workerCommit();pause.cancelScheduleResults();check(pause.results.frames.isEmpty()&&pause.dirtySections.contains(SCHEDULE),"partial cancellation marks dirty and removes frame callback");pause.refresh.drain();check(pause.results.rows==1,"cancelled frame cannot append");
  DashboardRefreshCheck leave=new DashboardRefreshCheck();leave.results.rows=8;leave.requestScheduleResults();leave.section=INBOX;leave.workerCommit();check(leave.results.rows==8,"leaving page rejects stale load");
  DashboardRefreshCheck replace=new DashboardRefreshCheck();replace.requestScheduleResults();replace.results=new LinearLayout();replace.workerCommit();check(replace.results.rows==0,"new results identity rejects old data");
  for(boolean finished:new boolean[]{false,true}){DashboardRefreshCheck dead=new DashboardRefreshCheck();dead.requestScheduleResults();dead.destroyed=!finished;dead.finishing=finished;dead.workerCommit();check(dead.results.rows==0,"destroyed or finishing activity rejects callback");}
  DashboardRefreshCheck failure=new DashboardRefreshCheck();failure.results.rows=4;DashboardSectionData.fail=true;failure.requestScheduleResults();failure.workerCommit();check(failure.results.rows==4&&!failure.scheduleResultLoading&&Feedback.failures==1,"error retains rows and releases busy state");DashboardSectionData.fail=false;failure.requestScheduleResults();failure.workerCommit();failure.complete();check(failure.results.rows==55,"retry succeeds");
  DashboardRefreshCheck rowFailure=new DashboardRefreshCheck();throwAt=3;rowFailure.requestScheduleResults();rowFailure.workerCommit();rowFailure.complete();check(!rowFailure.scheduleResultLoading&&rowFailure.dirtySections.contains(SCHEDULE)&&rowFailure.results.rows==3,"frame creation failure cancels safely and leaves retryable cache");throwAt=-1;rowFailure.requestScheduleResults();rowFailure.workerCommit();rowFailure.complete();check(rowFailure.results.rows==55,"retry after frame creation failure succeeds");
  DashboardRefreshCheck animated=new DashboardRefreshCheck();animated.results.rows=7;animated.requestScheduleResults(true);animated.workerCommit();check(animated.results.rows==7&&UiMotion.exits==1,"exit only after worker snapshot and old rows retained until exit completes");Runnable swap=UiMotion.replace;swap.run();check(animated.results.rows==1&&UiMotion.enters==1,"shared enter begins with bounded first row");animated.complete();
  DashboardRefreshCheck obsolete=new DashboardRefreshCheck();obsolete.requestScheduleResults(true);obsolete.workerCommit();Runnable stale=UiMotion.replace;obsolete.cancelScheduleResults();stale.run();check(obsolete.results.rows==0&&obsolete.countUpdates==0,"superseded exit cannot rebuild results or overwrite counts");
  obsolete.requestScheduleResults();DashboardSectionData.nextReview=4;DashboardSectionData.nextFailed=0;obsolete.workerCommit();obsolete.complete();int latestUpdates=obsolete.countUpdates;stale.run();check(obsolete.pendingReview==4&&obsolete.pendingFailed==0&&obsolete.countUpdates==latestUpdates,"older animated callback cannot overwrite already applied latest counts");
  DashboardRefreshCheck inbox=new DashboardRefreshCheck();inbox.section=INBOX;inbox.results.rows=7;inbox.updateResultsWithEntrance();check(inbox.updates==0&&inbox.results.rows==7,"inbox uses shared exit before replacing");Runnable inboxSwap=UiMotion.replace;int entered=UiMotion.enters;inboxSwap.run();check(inbox.updates==1&&inbox.results.rows==2&&UiMotion.enters==entered+1,"inbox shares enter with schedule");inbox.updateResultsWithEntrance();Runnable ignored=UiMotion.replace;inbox.results=new LinearLayout();ignored.run();check(inbox.updates==1,"inbox stale exit does not rebuild replaced page");
  DashboardRefreshCheck sync=new DashboardRefreshCheck();DashboardSectionData.count=0;sync.requestScheduleResults();sync.workerCommit();check(sync.results.footer==1&&!sync.scheduleResultLoading,"empty list completes without an extra frame");
  System.out.println("Schedule refresh passed: "+checks+" checks (production controller/renderer, worker and frame doubles)");
 }
}
'''.replace('__METHODS__', methods) + SCHEDULE_LISTING_STUB
out = ROOT / 'build/schedule-refresh-check'
out.mkdir(parents=True, exist_ok=True)
file = out / 'DashboardRefreshCheck.java'
file.write_text(java, encoding='utf-8')
filter_state = out / 'com/donglan/chrona/data/ScheduleFilterState.java'
filter_state.parent.mkdir(parents=True, exist_ok=True)
filter_state.write_text('package com.donglan.chrona.data; public final class ScheduleFilterState { public static final int HOMEWORK_CATEGORY=3; public static String categoryValue(int index){return "";} }', encoding='utf-8')
bin_dir = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(bin_dir / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), str(file), str(filter_state)], check=True)
subprocess.run([str(bin_dir / 'java.exe'), '-cp', str(out), 'DashboardRefreshCheck'], check=True)
