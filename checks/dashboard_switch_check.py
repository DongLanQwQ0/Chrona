"""Execute production section loading and switch/defer controllers with platform transport doubles."""
from recurrence_dependencies import recurrence_java

from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/dashboard-switch-check'
activity = (src / 'DashboardActivity.java').read_text(encoding='utf-8')
def method(marker):
    first = activity.index(marker)
    end = activity.index('{', first) + 1
    depth = 1
    while depth:
        depth += (activity[end] == '{') - (activity[end] == '}')
        end += 1
    return activity[first:end]
controller = '\n'.join(method(x) for x in ('private void switchTo(int destination)',
    'private void switchTo(int destination, boolean fromHistory)', 'private void prepareSectionSwitch(',
    'private void switchToPendingSection(', 'private void refreshSettledSection(', 'private String inboxCategory(',
    'private ScheduleQuery buildScheduleQuery(', 'private void openHomeTodos(', 'private void openHomeHomework('))
files = {
'android/content/Context.java': '''package android.content;public class Context {
 public volatile boolean fail;public volatile int reads,closed;public volatile String keyword;public final android.database.sqlite.SQLiteDatabase db=new android.database.sqlite.SQLiteDatabase();
 public com.donglan.chrona.data.ScheduleQuery lastSchedule;
 public Context getApplicationContext(){return this;}
}''',
'android/database/sqlite/SQLiteDatabase.java': '''package android.database.sqlite;public class SQLiteDatabase {
 public int started,ended,successful;public void beginTransactionNonExclusive(){started++;}public void setTransactionSuccessful(){successful++;}public void endTransaction(){ended++;}}
''',

'com/donglan/chrona/data/TaskStore.java': '''package com.donglan.chrona.data;
import java.util.*;import android.content.Context;
public class TaskStore implements AutoCloseable {
 final Context c;public TaskStore(Context c){this.c=c;check();}void check(){if(Thread.currentThread().getName().equals("main"))throw new AssertionError("database access on UI thread");c.reads++;}
 public void close(){c.closed++;}public android.database.sqlite.SQLiteDatabase getReadableDatabase(){return c.db;}
 public long dataRevision(){check();return 7;}public int taskCountByStatus(String status){check();return 2;}
 public List<Long>queryInboxIds(InboxQuery query,boolean oldest){check();c.keyword=query.keyword;if(c.fail)throw new IllegalStateException("read failure");return java.util.stream.LongStream.rangeClosed(1,30).boxed().toList();}
 public List<TaskRecord>queryInboxPage(InboxQuery query,boolean oldest,int page,List<Long>ids){check();return List.of(new TaskRecord(ids.get(page*12),"text","source",0,"ready",null));}
 public List<Long>queryScheduleIds(ScheduleQuery query){check();c.lastSchedule=query;return List.of(1L,2L);}
 public int schedulePageForCandidate(ScheduleQuery query,boolean oldest,long id){check();return queryScheduleIds(query).contains(id)?0:-1;}
 public List<EventCandidate>querySchedulePage(ScheduleQuery query,boolean oldest,int page){check();return List.of(new EventCandidate(1,1,"fixture",null,null,"UTC","","",null,false));}
 public List<Long>linkedCalendarIds(){check();return List.of(55L);}
}''',
'com/donglan/chrona/HomeData.java': '''package com.donglan.chrona;class HomeData {
 final int review=2,failed=2;HomeData(android.content.Context c,com.donglan.chrona.data.TaskStore store){store.dataRevision();}}
''',
'com/donglan/chrona/ScheduleOrderPreferences.java': '''package com.donglan.chrona;final class ScheduleOrderPreferences {
 static boolean oldestFirst(Object context,int tab){return true;}}
''',
'com/donglan/chrona/ZjuSettingsStore.java': '''package com.donglan.chrona;final class ZjuSettingsStore {
 static boolean active=true;ZjuSettingsStore(Object context){}boolean enabled(){return active;}}
''',
'com/donglan/chrona/data/ScheduleFilterState.java': '''package com.donglan.chrona.data;public final class ScheduleFilterState {
 public static final int HOMEWORK_CATEGORY=3;}
''',
'com/donglan/chrona/DashboardActivity.java': '''package com.donglan.chrona;
import java.time.*;import java.util.*;import java.util.concurrent.*;import com.donglan.chrona.data.*;
class Queue {final List<Runnable>tasks=new ArrayList<>();synchronized void post(Runnable r){tasks.add(r);}void postDelayed(Runnable r,long delay){post(r);}synchronized void clear(){tasks.clear();}
 void drain(){for(int i=0;i<30;i++){Runnable r;synchronized(this){if(tasks.isEmpty())return;r=tasks.remove(0);}r.run();}throw new AssertionError("queue stuck");}}
class Dock {boolean animating,tracking;int target;void beginPageSettle(int n){target=n;}void finishPageSelection(int n){target=n;}boolean isAnimating(){return animating;}boolean isTrackingTouch(){return tracking;}}
class Scroll {void setTranslationX(float x){}}
class Page {final int section;final Scroll scroll=new Scroll();Page(int section){this.section=section;}}
class Pager {void addView(Scroll s,int index,FrameLayout.LayoutParams p){}}
class FrameLayout {static class LayoutParams {LayoutParams(int w,int h){}}}
class Listener {void onStart(){}}
class Feedback {static void show(DashboardActivity owner,String message){owner.failure=message;}}
public class DashboardActivity extends android.content.Context {
 static final int HOME=0,INBOX=1,SCHEDULE=2;int section=HOME,pendingSection=-1,loadingSection,sectionLoadGeneration,dragDirection;
 boolean initialDataReady=true,pageTransitionRunning,dragTargetLoaded,sectionLoadRunning,foreground=true,homeRefreshPending,scheduleRefreshPending,destroyed,dockSectionSwitch;
 boolean pendingSectionFromHistory,sectionSwitchFromHistory;
 boolean completionRefreshRunning,completionRefreshPending;final Set<Long>completingCandidates=new HashSet<>();
 void refreshCompletionRows(){completionRefreshPending=false;}
 int statusIndex,categoryIndex,scheduleTab,scheduleCategory,schedulePublication,scheduleSource,inboxPage=999,schedulePage,scheduleTodoScope,scheduleRange;
 long pendingHomeworkFocus,unreadHomeworkId;int homeworkFocusTop,homeworkFocusBottom;String homeworkFocusFilter="";
 LocalDate scheduleDate=LocalDate.now(),scheduleUntil=scheduleDate;
 String inboxQuery="original",scheduleQuery="",snapshot="",failure;boolean inboxOldestFirst,scheduleOldestFirst;
 final Map<Integer,Page>sectionPages=new HashMap<>();Page adjacentPage;DashboardSectionData preparedSection;
 final Map<Integer,String>sectionVersions=new HashMap<>();final Set<Integer>dirtySections=new HashSet<>();final Set<Long>selectedScheduleIds=new HashSet<>();
 final Queue refresh=new Queue();final Dock navigation=new Dock();final Pager pager=new Pager();final Listener sectionPagerListener=new Listener();
 final ExecutorService startupReader=Executors.newSingleThreadExecutor();final Runnable deferredSectionRefresh=this::refreshSettledSection;
 int builds,commits,refreshes,snapshotReads;DashboardSectionData used;
 DashboardActivity(){sectionPages.put(HOME,new Page(HOME));}
 boolean isDestroyed(){return destroyed;}boolean isFinishing(){return false;}int sectionPosition(int destination){return destination;}
 LocalDate[]scheduleWindow(boolean system){return scheduleRange==0?new LocalDate[]{null,null}:new LocalDate[]{LocalDate.now(),LocalDate.now().plusDays(7)};}
 float sectionPageWidth(){return 300;}void collectSectionSurfaces(){}
 Page buildAdjacentSection(int destination){builds++;used=preparedSection;return new Page(destination);}
 void finishSectionDrag(boolean commit,float width){section=adjacentPage.section;sectionPages.put(section,adjacentPage);dragTargetLoaded=false;commits++;}
 void refreshHomeContent(){refreshes++;homeRefreshPending=false;}void updateResults(){refreshes++;}void refreshActivePageIfNeeded(){refreshes++;}String dataSnapshot(){snapshotReads++;return "snapshot";}
 void await()throws Exception{startupReader.submit(()->{}).get(3,TimeUnit.SECONDS);refresh.drain();}
 @@CONTROLLER@@
 static int checks;static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
 public static void main(String[]args)throws Exception {
  DashboardActivity d=new DashboardActivity();
  try {
   d.switchTo(INBOX);check(d.builds==0&&d.section==HOME&&d.sectionLoadRunning,"release schedules read without building on caller");d.inboxQuery="edited after request";d.await();
   check(d.section==INBOX&&d.commits==1&&d.used.tasks.size()==1&&d.used.page==2,"worker snapshot renders bounded clamped page");
   check(d.keyword.equals("original"),"request captures filter inputs");check(d.db.started==d.db.ended&&d.db.successful==1&&d.closed==1,"snapshot closes coherent read transaction");
   int reads=d.reads;d.switchTo(HOME);check(d.section==HOME&&d.reads==reads,"cached navigation does no database read");
   d.switchTo(SCHEDULE);d.switchTo(HOME);d.await();check(d.section==HOME&&d.navigation.target==HOME,"newer pending request wins and restores highlight");
   d.switchTo(SCHEDULE,true);d.switchTo(SCHEDULE);d.await();check(!d.sectionSwitchFromHistory&&!d.pendingSectionFromHistory,"normal request replaces history reason for same loading destination");
   d.switchTo(HOME);d.sectionPages.remove(INBOX);d.switchTo(INBOX,true);d.switchTo(HOME);d.await();check(d.section==HOME&&!d.pendingSectionFromHistory&&!d.sectionSwitchFromHistory,"new normal destination discards loading history request reason");
   d.navigation.animating=true;d.refreshSettledSection();check(d.refreshes==0&&d.snapshotReads==0&&!d.refresh.tasks.isEmpty(),"revision checks wait for dock animation");
   d.refresh.clear();d.navigation.animating=false;d.refreshSettledSection();check(d.refreshes==1&&d.snapshotReads==1,"idle refresh eventually runs");
  }finally{d.startupReader.shutdownNow();}
  DashboardActivity returning=new DashboardActivity();try{
   returning.switchTo(INBOX,true);returning.await();check(returning.section==INBOX&&returning.sectionSwitchFromHistory,"history reason follows asynchronous loaded destination");
   returning.switchTo(HOME);check(!returning.sectionSwitchFromHistory,"next normal cached switch clears history reason");
   returning.initialDataReady=false;returning.switchTo(INBOX,true);check(returning.pendingSectionFromHistory,"initial load defers destination and history reason together");
   returning.initialDataReady=true;returning.switchToPendingSection();check(returning.section==INBOX&&returning.sectionSwitchFromHistory&&!returning.pendingSectionFromHistory,"deferred history request retains reason and clears pending pair");
  }finally{returning.startupReader.shutdownNow();}
  DashboardActivity failure=new DashboardActivity();try{failure.fail=true;failure.switchTo(INBOX);failure.await();check(failure.section==HOME&&failure.builds==0&&failure.failure!=null,"failed read preserves page");check(failure.db.ended==1&&failure.closed==1,"failure releases read transaction");}finally{failure.startupReader.shutdownNow();}
  DashboardActivity paused=new DashboardActivity();try{paused.switchTo(INBOX);paused.startupReader.submit(()->{}).get(3,TimeUnit.SECONDS);paused.sectionLoadGeneration++;paused.sectionLoadRunning=false;paused.refresh.drain();check(paused.builds==0&&paused.section==HOME,"stale lifecycle callback cannot create page");}finally{paused.startupReader.shutdownNow();}
  android.content.Context c=new android.content.Context();ExecutorService worker=Executors.newSingleThreadExecutor();try{DashboardSectionData system=worker.submit(()->DashboardSectionData.load(c,SCHEDULE,null,null,false,0,true,0)).get();check(system.linked.equals(List.of(55L))&&system.ids.isEmpty()&&system.candidates.isEmpty(),"system source uses linked snapshot without local schedule query");}finally{worker.shutdownNow();}
  for(int scope:new int[]{ScheduleQuery.TODO_TODAY_DUE,ScheduleQuery.TODO_OVERDUE,ScheduleQuery.TODO_OPEN,ScheduleQuery.TODO_ANY}){
   DashboardActivity route=new DashboardActivity();try{
    route.sectionPages.put(SCHEDULE,new Page(SCHEDULE));route.sectionVersions.put(SCHEDULE,"stale");
    route.scheduleQuery="stale search";route.scheduleRange=5;route.scheduleSource=1;route.schedulePublication=2;route.schedulePage=99;route.selectedScheduleIds.add(44L);
    route.openHomeTodos(scope,scope==ScheduleQuery.TODO_ANY);
    check(route.sectionLoadRunning&&route.section==HOME,"home summary invalidates stale schedule page and uses worker switch");
    int expectedScope=scope==ScheduleQuery.TODO_ANY?ScheduleQuery.TODO_OPEN:scope;
    check(route.scheduleTodoScope==expectedScope&&route.scheduleTab==(scope==ScheduleQuery.TODO_ANY?1:3),"home summary selects matching tab and task scope");
    check(route.scheduleCategory==0&&route.scheduleRange==0&&route.schedulePublication==0&&route.scheduleSource==0&&route.schedulePage==0&&route.scheduleQuery.isEmpty()&&route.selectedScheduleIds.isEmpty(),"home summary includes both task categories and clears unrelated filters and stale selection");
    route.await();check(route.section==SCHEDULE&&route.lastSchedule.todoScope==expectedScope,"scope reaches production ScheduleQuery in worker snapshot");
    check(route.lastSchedule.selection.contains("category IN ('task','homework') AND completed_at_millis IS NULL")&&!route.lastSchedule.selection.contains("category=?"),"home summary includes uncompleted todos and homework without a single-category restriction");
    check(scope!=ScheduleQuery.TODO_ANY||route.lastSchedule.selection.contains("NOT"),"undated summary routes to unscheduled tab predicate");
   }finally{route.startupReader.shutdownNow();}
  }
  DashboardActivity homework=new DashboardActivity();try{
   homework.sectionPages.put(SCHEDULE,new Page(SCHEDULE));homework.scheduleQuery="stale";homework.scheduleSource=1;
   homework.openHomeHomework();check(homework.sectionLoadRunning&&homework.scheduleCategory==ScheduleFilterState.HOMEWORK_CATEGORY,"homework shortcut uses fresh worker and homework category");
   homework.await();check(homework.lastSchedule.selection.contains("category='homework'")&&homework.lastSchedule.todoScope==ScheduleQuery.TODO_OPEN&&homework.scheduleOldestFirst,"homework shortcut selects open homework and ascending deadlines");
   check(homework.lastSchedule.orderBy(true).startsWith("COALESCE(due_at_millis,end_at_millis,start_at_millis) IS NULL ASC, COALESCE(due_at_millis,end_at_millis,start_at_millis) ASC"),"homework shortcut prioritizes nearest effective deadline in due/end/start order");
   ZjuSettingsStore.active=false;ScheduleQuery retainedHomework=homework.buildScheduleQuery(System.currentTimeMillis(),ZoneId.systemDefault());
   check(homework.scheduleCategory==ScheduleFilterState.HOMEWORK_CATEGORY&&retainedHomework.selection.contains("category='homework'")&&retainedHomework.todoScope==ScheduleQuery.TODO_OPEN,"disabling school source retains the independent homework category and open-task scope");
  }finally{ZjuSettingsStore.active=true;homework.startupReader.shutdownNow();}
  System.out.println("Dashboard switch checks passed: "+checks+"; production worker/controller methods, Android transport stand-ins");
 }
}'''.replace('@@CONTROLLER@@', controller)
}
paths=[]
for name, content in files.items():
    p=out/name;p.parent.mkdir(parents=True,exist_ok=True);p.write_text(content,encoding='utf-8');paths.append(p)
paths += [src/'DashboardSectionData.java']+[src/'data'/name for name in ('TaskRecord.java','InboxQuery.java','ScheduleQuery.java','EventCategory.java')]
java=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run(recurrence_java([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out),*map(str,paths)]),check=True)
subprocess.run(recurrence_java([str(java/'java.exe'),'-cp',str(out),'com.donglan.chrona.DashboardActivity']),check=True)
