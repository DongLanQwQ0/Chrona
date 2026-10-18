"""Execute calendar counts/repository and actual Activity read/scroll controller methods.

Android provider/UI/queue transport uses stand-ins. A real single-thread executor exercises
superseded running and queued reads; this is not Android rendering or touch validation.
"""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
src = root / 'app/src/main/java/com/donglan/chrona'
out = root / 'build/calendar-review-check'
out.mkdir(parents=True, exist_ok=True)
activity = (src / 'CalendarOverviewActivity.java').read_text(encoding='utf-8')

def method(marker):
    start = activity.index(marker)
    end = activity.index('{', start) + 1
    depth = 1
    while depth:
        depth += (activity[end] == '{') - (activity[end] == '}')
        end += 1
    return activity[start:end]

controller = '\n'.join(method(marker) for marker in ('private LocalDate windowStart(', 'private LocalDate windowEnd(',
    'private String windowKey(', 'private void rememberPosition(', 'private void restorePosition(',
    'private void cancelRead(', 'private void applyDeferred(', 'private void applyLoaded(', 'private void load(', 'private void updateHeader(',
    'private boolean selectMonthDay(', 'private void navigateDate('))

files = {
 'android/os/OperationCanceledException.java': 'package android.os; public class OperationCanceledException extends RuntimeException {}',
 'android/os/CancellationSignal.java': '''package android.os; public class CancellationSignal {
 volatile boolean cancelled;public void cancel(){cancelled=true;}public boolean isCanceled(){return cancelled;}
 public void throwIfCanceled(){if(cancelled)throw new OperationCanceledException();}}''',
 'android/os/SystemClock.java': 'package android.os;public class SystemClock {public static long uptimeMillis(){return 1000;}}',
 'android/content/Context.java': '''package android.content;
import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
import com.donglan.chrona.data.EventCandidate;import com.donglan.chrona.calendar.CalendarOccurrence;
public class Context {
 public static final int MODE_PRIVATE=0;
 public List<EventCandidate>candidates=new ArrayList<>();public List<CalendarOccurrence>system=new ArrayList<>();
 public Set<Long>linked=new HashSet<>();public boolean permitted=true,failLocal,failCalendar,cancelCalendar;
 public final AtomicInteger reads=new AtomicInteger(),calendarReads=new AtomicInteger();
 public final AtomicBoolean blockNextRead=new AtomicBoolean();public CountDownLatch started=new CountDownLatch(1),release=new CountDownLatch(1);
 public long revision;public String feedback;public Context getApplicationContext(){return this;}
}''',
 'android/net/Uri.java': 'package android.net;public class Uri {public String value;public Uri(String s){value=s;}}',
 'android/content/ContentUris.java': 'package android.content;public class ContentUris {public static android.net.Uri withAppendedId(android.net.Uri uri,long id){return new android.net.Uri(uri.value+id);}}',
 'android/content/Intent.java': '''package android.content;public class Intent {
 public static final String ACTION_VIEW="view";public Intent(Context c,Class<?> target){}public Intent(String action,android.net.Uri uri){}
 public Intent putExtra(String key,long value){return this;}public Intent putExtra(String key,String value){return this;}}''',
 'android/provider/CalendarContract.java': 'package android.provider;public class CalendarContract {public static class Events {public static final android.net.Uri CONTENT_URI=new android.net.Uri("events/");}}',
 'com/donglan/chrona/TaskDetailActivity.java': 'package com.donglan.chrona;public class TaskDetailActivity {}',
 'com/donglan/chrona/ExternalCourseAgenda.java': '''package com.donglan.chrona;
import java.util.*;import android.content.Context;import com.donglan.chrona.calendar.CalendarOccurrence;
final class ExternalCourseAgenda {
 static String revision(Context c){return "fixture-courses:0";}
 static List<CalendarOccurrence>merge(Context c,List<CalendarOccurrence>system,long begin,long end,Set<Long>linked){return CalendarOccurrence.unlinked(system,linked);}
}''',
 'com/donglan/chrona/SystemAgendaDetailActivity.java': 'package com.donglan.chrona;public class SystemAgendaDetailActivity {static android.content.Intent intent(android.content.Context c,long id,long start,long end,boolean allDay,String title,String location,String calendar){return new android.content.Intent(c,SystemAgendaDetailActivity.class);}}',
 'com/donglan/chrona/data/TaskStore.java': '''package com.donglan.chrona.data;
import java.util.*;import android.content.Context;
public class TaskStore implements AutoCloseable {
 final Context c;public TaskStore(Context c){this.c=c;}public void close(){}public long dataRevision(){return c.revision;}
 public List<EventCandidate>calendarCandidates(long a,long b,long d,long e){
  c.reads.incrementAndGet();if(c.failLocal)throw new IllegalStateException("storage failure");
  if(c.blockNextRead.getAndSet(false)){c.started.countDown();boolean done=false;while(!done)try{done=c.release.await(2,java.util.concurrent.TimeUnit.SECONDS);}catch(InterruptedException ignored){}}
  return c.candidates;
 }
 public List<Long>linkedCalendarIds(){return new ArrayList<>(c.linked);}
}''',
 'com/donglan/chrona/calendar/CalendarStore.java': '''package com.donglan.chrona.calendar;
import java.util.*;import android.content.Context;import android.os.CancellationSignal;
public class CalendarStore {
 final Context c;public CalendarStore(Context c){this.c=c;}public boolean hasReadPermission(){return c.permitted;}
 public List<CalendarOccurrence>listInstances(long begin,long end,CancellationSignal signal,int limit){
  c.calendarReads.incrementAndGet();if(c.cancelCalendar){signal.cancel();signal.throwIfCanceled();}
  if(c.failCalendar)throw new IllegalStateException("provider failure");return c.system;
 }
}''',
 'com/donglan/chrona/CalendarReviewCheck.java': r'''package com.donglan.chrona;
import java.time.*;import java.time.format.DateTimeFormatter;import java.time.temporal.TemporalAdjusters;import java.util.*;import java.util.concurrent.*;
import android.content.Context;import android.os.CancellationSignal;
import com.donglan.chrona.data.*;import com.donglan.chrona.calendar.*;
class Queue {
 final List<Runnable>tasks=new ArrayList<>();synchronized void post(Runnable r){tasks.add(r);}void postDelayed(Runnable r,long delay){post(r);}
 synchronized void removeCallbacks(Runnable r){tasks.removeIf(x->x==r);}
 void drain(){for(int i=0;i<100;i++){Runnable r;synchronized(this){if(tasks.isEmpty())return;r=tasks.remove(0);}r.run();}throw new AssertionError("queue did not settle");}
}
class View {
 static final int IMPORTANT_FOR_ACCESSIBILITY_AUTO=0,IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS=4;
 static final int VISIBLE=0,GONE=8;void setVisibility(int v){}
 int importance;void setImportantForAccessibility(int value){importance=value;}
}
class LinearLayout extends View {}
class FrameLayout extends View {
 final Queue queue;View child=new View();FrameLayout(Queue q){queue=q;}int getChildCount(){return child==null?0:1;}
 void removeAllViews(){child=null;}void addView(View v){child=v;}void post(Runnable r){queue.post(r);}
}
class ScrollView extends View {int y;int getScrollY(){return y;}void scrollTo(int x,int y){this.y=y;}}
class HorizontalScrollView extends View {int x;int getScrollX(){return x;}void scrollTo(int x,int y){this.x=x;}}
class TextView {String text,description;void setText(String s){text=s;}void setContentDescription(String s){description=s;}}
class Switch {boolean checked;void setChecked(boolean value){checked=value;}}
class Dialog {boolean isShowing(){return false;}}
class Tabs {void finishPageSelection(int mode){}}
class Preferences {int mode;boolean system;Preferences edit(){return this;}Preferences putInt(String k,int value){mode=value;return this;}
 Preferences putBoolean(String k,boolean value){system=value;return this;}void apply(){}}
class Feedback {static void show(Context c,String message){c.feedback=message;}}
public class CalendarReviewCheck extends Context {
 static final int YEAR=0,MONTH=1,WEEK=2,DAY=3,AGENDA=4;
 int mode=MONTH,generation,renderedMode=MONTH,renderCalls,renderedHighlights;boolean includeSystem,loading,loadingStale,bodyTouchActive,resumed=true,destroyed,animateNext,renderedSystem;
 LocalDate date=LocalDate.of(2026,10,12),renderedDate,renderedToday;
 final ThreadPoolExecutor reader=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new LinkedBlockingQueue<>());
 java.util.concurrent.Future<?> readFuture;CancellationSignal readCancellation;
 final Queue handler=new Queue();final FrameLayout body=new FrameLayout(handler);ScrollView verticalViewport;HorizontalScrollView horizontalViewport;
 String renderedKey="",renderedQuery="",query="",deferredKey;int deferredToken;long lastScrollAt;
 View monthDates;LinearLayout monthDetails;int cellUpdates,detailsUpdates,navigationReads;LocalDate detailsDate;final Runnable minuteTick=()->{};
 final LinkedHashMap<String,int[]>positions=new LinkedHashMap<>(16,.75f,true);
 volatile int providerRevision;final LinkedHashMap<String,AgendaRepository.Snapshot>snapshotCache=new LinkedHashMap<>(4,.75f,true);
 AgendaRepository.Snapshot snapshot=new AgendaRepository.Snapshot(),deferredSnapshot;final Runnable deferredApply=this::applyDeferred;
 final TextView dateTitle=new TextView();final Switch sourceSwitch=new Switch();final Tabs tabs=new Tabs();Object tabMotion;Dialog allDayDialog;final Preferences preferences=new Preferences();
 View weekExportButton=new View();
 boolean isDestroyed(){return destroyed;}Preferences getPreferences(int mode){return preferences;}
 void finishTabMotion(){}void finishBodyMotion(){}void moveTabHighlight(){renderedHighlights++;}
 View action(String label,Runnable action){return new View();}
 View text(String label,int size,boolean bold){return new View();}
 void updateMonthCell(LocalDate day){cellUpdates++;}void updateMonthDetails(List<AgendaEntry>entries){detailsUpdates++;detailsDate=date;}
 List<AgendaEntry>matching(){return snapshot.entries;}long nextTickDelay(){return 60000;}void animatedLoad(int direction){navigationReads++;}
 void renderBody(){renderCalls++;renderedKey=windowKey(snapshot.zone);renderedDate=date;renderedToday=LocalDate.now();renderedQuery=query;renderedMode=mode;renderedSystem=includeSystem;}
 @@CONTROLLER@@
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static AgendaEntry entry(long id,long start,long end){return new AgendaEntry(id,1,start,end,"title","room","note","local",false,false,true);}
 static EventCandidate candidate(long id,long start,long end,boolean allDay){return new EventCandidate(id,1,"title",start,end,"UTC","room","note",null,false,null,"event",allDay);}
 static AgendaRepository.Snapshot load(Context c,long first,long last,ZoneId zone,boolean system,CancellationSignal signal){return AgendaRepository.load(c,first,last,system,zone,signal);}
 public static void main(String[]args)throws Exception {
  ZoneId zone=ZoneId.of("Asia/Shanghai");LocalDate day=LocalDate.of(2026,10,12);long midnight=day.atStartOfDay(zone).toInstant().toEpochMilli();
  CalendarSwipe swipe=new CalendarSwipe();
  swipe.begin(100,100,true);check(!swipe.move(96,102,8,false),"tap jitter does not take over");check(swipe.finish(96,102,48)==0,"tap does not navigate");
  swipe.begin(200,100,true);check(swipe.move(180,102,8,false),"horizontal drag claimed");check(swipe.finish(100,105,48)==1,"left swipe goes forward");
  swipe.begin(100,100,true);swipe.move(120,101,8,false);check(swipe.finish(200,105,48)==-1,"right swipe goes back");
  swipe.begin(100,100,true);check(!swipe.move(103,130,8,false)&&!swipe.move(220,131,8,false),"vertical scroll stays with child");
  swipe.begin(100,100,true);check(!swipe.move(130,102,8,true)&&!swipe.move(240,103,8,false),"nested horizontal scroll keeps whole gesture");
  swipe.begin(100,100,true);swipe.move(130,102,8,false);check(swipe.finish(135,102,48)==0,"short swipe cancelled");
  swipe.begin(100,100,true);swipe.move(130,102,8,false);swipe.cancel();check(swipe.claimed()&&swipe.finish(230,102,48)==0,"multitouch cancels navigation but keeps input ownership");
  swipe.begin(100,100,false);check(!swipe.move(250,100,8,false)&&swipe.finish(250,100,48)==0,"loading/transition blocks gesture");
  swipe.begin(100,100,true);swipe.move(130,102,8,false);check(swipe.finish(150,200,48)==0,"vertical ending cancels navigation");
  List<AgendaEntry>e=List.of(entry(1,midnight-3600000,midnight),entry(2,midnight-3600000,midnight+3600000),entry(3,midnight+86390000,midnight+86410000));
  CalendarDayCounts counts=new CalendarDayCounts(e,day,day.plusDays(3),zone);
  check(counts.on(day)==2&&counts.on(day.plusDays(1))==1&&counts.on(day.minusDays(1))==0,"exclusive ends, cross-midnight and bounds");
  Random random=new Random(917);List<AgendaEntry>many=new ArrayList<>();
  for(int i=0;i<5000;i++){long begin=midnight+random.nextInt(40*86400)*1000L-5*86400000L;many.add(entry(i,begin,begin+random.nextInt(8*86400)*1000L));}
  for(ZoneId z:List.of(zone,ZoneId.of("America/New_York"))){
   LocalDate first=Instant.ofEpochMilli(midnight).atZone(z).toLocalDate();CalendarDayCounts index=new CalendarDayCounts(many,first,first.plusDays(42),z);
   for(int i=0;i<42;i++){LocalDate d=first.plusDays(i);long begin=d.atStartOfDay(z).toInstant().toEpochMilli(),end=d.plusDays(1).atStartOfDay(z).toInstant().toEpochMilli();
    int reference=0;for(AgendaEntry item:many)if(item.overlaps(begin,end))reference++;check(index.on(d)==reference,"5000 event count against overlap oracle");}
  }
  ZoneId ny=ZoneId.of("America/New_York");LocalDate dst=LocalDate.of(2026,11,1);
  long dstBegin=dst.atStartOfDay(ny).toInstant().toEpochMilli(),dstEnd=dst.plusDays(1).atStartOfDay(ny).toInstant().toEpochMilli();
  check(new CalendarDayCounts(List.of(entry(1,dstBegin,dstEnd)),dst,dst.plusDays(2),ny).on(dst.plusDays(1))==0,"DST exclusive end");
  check(CalendarViewport.hour(day.minusDays(1),1,day,LocalTime.of(23,0))==8,"exclusive past-window focus boundary");
  check(CalendarViewport.hour(day,1,day,LocalTime.of(23,0))==22,"current day focus");
  check(CalendarViewport.column(day,7,day.plusDays(4))==4&&CalendarViewport.column(day,7,day.plusDays(9))==6,"selected column and bounds");
  Context c=new Context();c.candidates.add(candidate(1,midnight,midnight+3600000,false));
  CancellationSignal cancelled=new CancellationSignal();cancelled.cancel();
  try{load(c,midnight,midnight+86400000,zone,false,cancelled);throw new AssertionError("cancelled read accepted");}catch(android.os.OperationCanceledException expected){}
  check(c.reads.get()==0,"cancel before opening source");
  TimeZone prior=TimeZone.getDefault();TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"));
  AgendaRepository.Snapshot a=load(c,midnight,midnight+86400000,zone,false,new CancellationSignal());
  check(a.zone.equals(zone)&&a.entries.size()==1&&c.calendarReads.get()==0,"captured zone and isolated source");TimeZone.setDefault(prior);
  check(a.sameContents(load(c,midnight,midnight+86400000,zone,false,new CancellationSignal())),"identical snapshots keep native content");
  c.system.add(new CalendarOccurrence(8,midnight,midnight+3600000,"copy",false,"","system"));c.linked.add(8L);
  c.system.add(new CalendarOccurrence(9,midnight,midnight+3600000,"external",false,"","system",0xFFFF0000));
  check(load(c,midnight,midnight+86400000,zone,true,new CancellationSignal()).entries.size()==2,"linked provider copy excluded");
  AgendaRepository.Snapshot colored=load(c,midnight,midnight+86400000,zone,true,new CancellationSignal());
  check(colored.entries.get(1).calendarColor==0xFFFF0000,"calendar color reaches display snapshot");
  c.system.set(1,new CalendarOccurrence(9,midnight,midnight+3600000,"external",false,"","system",0xFF00FF00));
  check(!colored.sameContents(load(c,midnight,midnight+86400000,zone,true,new CancellationSignal())),"source-only color changes trigger refresh");
  check(CalendarSourceColors.mix(null,0xFF336699)==0xFF336699,"missing source uses theme");
  check(CalendarSourceColors.mix(0xFFFF0000,0xFF0000FF)==0xFFA60059,"source 65/theme 35 RGB mix");
  check(CalendarSourceColors.mix(0,0xFFFFFFFF)==0xFF595959,"black source is valid, output opaque");
  check(CalendarSourceColors.mix(0x00FF0000,0xFF0000FF)==0xFFA60059,"RGB source without alpha supported");
  check(CalendarSourceColors.mix(0xFFFF0000,0xFF00FF00)==0xFFA65900,"active theme changes mixed color");
  c.failCalendar=true;check(load(c,midnight,midnight+86400000,zone,true,new CancellationSignal()).systemUnavailable,"provider failure is visible");c.failCalendar=false;
  c.cancelCalendar=true;try{load(c,midnight,midnight+86400000,zone,true,new CancellationSignal());throw new AssertionError("provider cancellation swallowed");}catch(android.os.OperationCanceledException expected){}
  CalendarReviewCheck m=new CalendarReviewCheck();
  try {
   m.blockNextRead.set(true);m.mode=YEAR;m.load();check(m.started.await(2,TimeUnit.SECONDS),"running read started");CancellationSignal old=m.readCancellation;
   m.mode=MONTH;for(int month=1;month<=4;month++){m.date=LocalDate.of(2026,month,12);m.load();check(m.reader.getQueue().size()<=1,"only latest queued read retained");}
   check(old.isCanceled(),"running obsolete provider signal cancelled");m.release.countDown();m.readFuture.get(3,TimeUnit.SECONDS);m.handler.drain();
   check(m.reads.get()==2&&m.renderCalls==1&&m.date.getMonthValue()==4,"obsolete reads cannot block or render over latest");
   int rendered=m.renderCalls,priorReads=m.reads.get();m.load();m.readFuture.get(3,TimeUnit.SECONDS);m.handler.drain();check(m.renderCalls==rendered,"unchanged refresh preserves native content/focus");
   check(m.reads.get()==priorReads,"same window uses cached snapshot");
   m.revision++;m.load();m.readFuture.get(3,TimeUnit.SECONDS);m.handler.drain();check(m.reads.get()==priorReads+1,"local data revision invalidates cache");
   m.providerRevision++;m.load();m.readFuture.get(3,TimeUnit.SECONDS);m.handler.drain();check(m.reads.get()==priorReads+2,"provider revision invalidates cache");
   m.includeSystem=true;m.failCalendar=true;m.load();m.readFuture.get(3,TimeUnit.SECONDS);m.handler.drain();
   check(m.snapshot.systemUnavailable,"provider error is visible");int failedReads=m.reads.get();
   m.failCalendar=false;m.load();m.readFuture.get(3,TimeUnit.SECONDS);m.handler.drain();
   check(!m.snapshot.systemUnavailable&&m.reads.get()==failedReads+1,"failed source is retried, not cached");m.includeSystem=false;
   m.verticalViewport=new ScrollView();m.horizontalViewport=new HorizontalScrollView();m.verticalViewport.y=731;m.horizontalViewport.x=127;m.rememberPosition();
   m.verticalViewport=new ScrollView();m.horizontalViewport=new HorizontalScrollView();m.restorePosition();m.handler.drain();
   check(m.verticalViewport.y==731&&m.horizontalViewport.x==127,"both scroll axes restored");
   m.restorePosition();ScrollView newest=new ScrollView();m.verticalViewport=newest;m.handler.drain();check(newest.y==0,"obsolete restore ignored");
   for(int i=0;i<20;i++){m.renderedKey="key"+i;m.rememberPosition();}check(m.positions.size()==12,"viewport cache bounded");
   m.renderedKey=m.windowKey(ZoneId.systemDefault());m.renderedDate=m.date;m.renderedToday=LocalDate.now();m.renderedQuery=m.query;
   AgendaRepository.Snapshot changed=new AgendaRepository.Snapshot();changed.entries.add(entry(17,midnight,midnight+1000));
   m.bodyTouchActive=true;m.loading=true;m.loadingStale=false;m.applyLoaded(changed,m.renderedKey,m.generation);
   check(m.deferredSnapshot==changed&&m.loading,"data update waits for gesture");m.bodyTouchActive=false;m.applyDeferred();check(!m.loading&&m.deferredSnapshot==null,"gesture completion applies latest update");
   m.renderedMode=MONTH;m.renderedDate=LocalDate.of(2026,4,12);m.renderedSystem=false;m.mode=DAY;m.date=day;m.failLocal=true;View oldBody=m.body.child;
   m.load();check(m.loadingStale&&m.body.importance==4,"different window hides stale accessibility content");m.readFuture.get(3,TimeUnit.SECONDS);m.handler.drain();
   check(m.mode==MONTH&&m.date.equals(m.renderedDate)&&!m.loadingStale&&m.body.child==oldBody,"failed navigation restores successful context");
   int token=m.generation;m.cancelRead();m.applyLoaded(changed,"bad",token);check(m.mode==MONTH,"cancelled lifecycle result ignored");
   m.monthDates=new View();m.monthDetails=new LinearLayout();m.renderedMode=MONTH;m.mode=MONTH;m.renderedDate=m.date;
   m.renderedKey=m.windowKey(m.snapshot.zone);View grid=m.monthDates,details=m.monthDetails;ScrollView scroll=m.verticalViewport;
   int bodyRenders=m.renderCalls,reads=m.reads.get();LocalDate target=m.date.plusDays(1);
   m.navigateDate(target);m.handler.drain();
   check(m.monthDates==grid&&m.monthDetails==details&&m.verticalViewport==scroll&&m.renderCalls==bodyRenders,"same-month selection preserves actual hosts and skips body rebuild");
   check(m.date.equals(target)&&m.renderedDate.equals(target)&&m.detailsDate.equals(target)&&m.cellUpdates==2&&m.detailsUpdates==1,"selection updates old/new cells and only day details");
   check(m.reads.get()==reads&&m.navigationReads==0,"same month does not schedule repository read");
   m.navigateDate(target);check(m.detailsUpdates==1&&m.cellUpdates==2,"reselecting same day is a no-op");
   m.loading=true;m.navigateDate(target.plusDays(1));check(m.navigationReads==0&&m.detailsUpdates==2,"same-month selection stays local during background refresh");
   m.loading=false;m.navigateDate(target.plusMonths(1));check(m.navigationReads==1,"different month follows normal navigation");
  } finally {m.release.countDown();m.cancelRead();m.reader.shutdownNow();}
  System.out.println("Calendar review checks passed: "+checks+"; real counts/repository/read and scroll methods, Android transport stand-ins");
 }
}'''.replace('@@CONTROLLER@@', controller)
}
paths = []
for name, content in files.items():
    p = out / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content, encoding='utf-8')
    paths.append(p)
paths += [src / name for name in ['AgendaEntry.java','AgendaRepository.java','CalendarDayCounts.java','CalendarViewport.java','CalendarSwipe.java','CalendarSourceColors.java',
    'calendar/CalendarOccurrence.java','calendar/AllDayDates.java','data/EventCandidate.java','data/EventCategory.java']]
java = Path(os.environ['JAVA_HOME']) / 'bin'
subprocess.run([str(java / 'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out),*map(str,paths)],check=True)
subprocess.run([str(java / 'java.exe'),'-cp',str(out),'com.donglan.chrona.CalendarReviewCheck'],check=True)
