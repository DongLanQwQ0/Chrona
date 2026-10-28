"""Execute production school/source lifecycle and page-delivery callbacks with JVM doubles."""
from pathlib import Path
import os
import subprocess

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / 'app/src/main/java/com/donglan/chrona'


def block(source, marker):
    start = source.index(marker)
    end = source.index('{', start) + 1
    depth = 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


source = (JAVA / 'ScheduleSourceContentsActivity.java').read_text(encoding='utf-8')
history = (JAVA / 'ZjuHomeworkHistoryActivity.java').read_text(encoding='utf-8')
for code in (source, history):
    assert 'setOnScrollChangeListener((view,x,y,oldX,oldY)->{if(y!=oldY)restoreScroll=-1;})' in code
source_methods = '\n'.join(block(source, marker) for marker in (
    '@Override protected void onResume()', '@Override protected void onSaveInstanceState(Bundle state)',
    'private boolean hiddenZju()', 'private boolean systemSource()', 'private void load()',
    'private void load(int direction)', 'private void beginLoad(int direction)',
    'private void restoreReadingPosition()'))
source_delivery = block(source, 'runOnUiThread(() -> {')[len('runOnUiThread(() -> {'):-1]
history_delivery = block(history, 'runOnUiThread(() -> {\n                if (isDestroyed()||isFinishing()) return;\n                root.removeView(more);')
history_delivery = history_delivery[len('runOnUiThread(() -> {'):-1]
history_methods = '\n'.join(block(history, marker) for marker in (
    '@Override protected void onSaveInstanceState(Bundle state)', 'private void restoreReadingPosition()'))

# Call-site contract: no school page consumes its first marker before the shared confirmation.
for name in ('ZjuExamsActivity.java', 'ZjuHomeworkHistoryActivity.java',
             'ExternalCourseDetailActivity.java', 'CourseSourceMatchingActivity.java',
             'ScheduleSourceContentsActivity.java'):
    code = (JAVA / name).read_text(encoding='utf-8')
    assert 'firstDataPresentation=false;UiMotion.enterPage' not in code, name
    assert '()->firstDataPresentation=false' in code, name

harness = r'''
import java.util.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
class Bundle {
 final Map<String,Object> values=new HashMap<>();
 void putInt(String k,int v){values.put(k,v);}int getInt(String k){return (int)values.get(k);}
 void putLong(String k,long v){values.put(k,v);}void putBoolean(String k,boolean v){values.put(k,v);}
}
class Activity {
 boolean finishing,destroyed;protected void onResume(){}protected void onSaveInstanceState(Bundle state){}
 boolean isFinishing(){return finishing;}boolean isDestroyed(){return destroyed;}void finish(){finishing=true;}
}
class View {
 static final int VISIBLE=0,GONE=8;int visibility;String text="";boolean enabled;
 void setVisibility(int v){visibility=v;}void setText(String v){text=v;}void setEnabled(boolean v){enabled=v;}
}
class LinearLayout extends View {
 final List<Object> children=new ArrayList<>();
 void removeAllViews(){children.clear();}void addView(Object v){children.add(v);}void removeView(Object v){children.remove(v);}
 int getChildCount(){return children.size();}
}
class Button extends View {}
class ViewTreeObserver {
 interface OnPreDrawListener{boolean onPreDraw();}
 final List<OnPreDrawListener> listeners=new ArrayList<>();
 void addOnPreDrawListener(OnPreDrawListener l){listeners.add(l);}void removeOnPreDrawListener(OnPreDrawListener l){listeners.remove(l);}
 void draw(){for(var l:new ArrayList<>(listeners))l.onPreDraw();}
}
class ScrollView {
 final ViewTreeObserver tree=new ViewTreeObserver();int y,max;
 ViewTreeObserver getViewTreeObserver(){return tree;}int getScrollY(){return y;}
 void scrollTo(int x,int requested){y=Math.max(0,Math.min(max,requested));}
}
class ZjuSettingsStore {
 static boolean enabled=true;static String account="accountA";
 ZjuSettingsStore(Object context){}boolean enabled(){return enabled;}String account(){return account;}
}
class ScheduleSourceRepository {static boolean readable=true;static boolean systemReadable(Object c){return readable;}}
class UiMotion {
 static int entrances,swaps;static void enterPage(Object v,Runnable confirmed){entrances++;confirmed.run();}
 static void swap(Object a,Object b,Runnable bind,int direction){swaps++;bind.run();}
}
class ZjuHomeworkStore {static class History{long id;History(long id){this.id=id;}}}
public class PageRecoveryCheck {
 static int checks;
 static void check(boolean ok,String label){checks++;if(!ok)throw new AssertionError(label);}
 static class Entry {long start,providerId;Entry(long id){start=id;providerId=id;}}
 static class Source extends Activity {
  static final int PAGE_SIZE=50;
  String source="imported",schoolAccount="accountA";LocalDate week=LocalDate.of(2026,10,26);
  LinearLayout rows=new LinearLayout(),navigation=new LinearLayout();View range=new View();Button mode=new Button(),more=new Button();
  boolean firstDataPresentation,initialized,calendarReadable=true,dataReady,loading,all=true;
  int restoreCount,restoreScroll=-1,offset,generation,loadDirection,requests;
  long afterStart=Long.MIN_VALUE,afterId=-1;List<Entry> localSnapshot;ScrollView viewport=new ScrollView();
  @@SOURCE_METHODS@@
  void loadPage(){requests++;}
  void addEntry(Entry entry){rows.addView(entry);viewport.max=Math.max(0,rows.getChildCount()*100-1000);}
  void text(LinearLayout host,String value,int size){host.addView(value);}
  void deliver(int pageOffset,List<Entry> result){
   int request=generation,direction=loadDirection;boolean entireSource=all;List<Entry> snapshot=new ArrayList<>();
   @@SOURCE_DELIVERY@@
  }
 }
 static class History extends Activity {
  static final int PAGE_SIZE=50;LinearLayout root=new LinearLayout(),content=new LinearLayout();Button more=new Button();
  boolean firstDataPresentation,loading;int loadedCount,restoreCount,restoreScroll=-1,requests;
  long beforeId=Long.MAX_VALUE;ScrollView viewport=new ScrollView();
  @@HISTORY_METHODS@@
  void loadMore(){requests++;}Object text(String value,int size,boolean primary){return value;}
  void addRecord(ZjuHomeworkStore.History item){content.addView(item);viewport.max=Math.max(0,content.getChildCount()*100-1000);}
  void deliver(List<ZjuHomeworkStore.History> rows){long cursor=beforeId;@@HISTORY_DELIVERY@@}
 }
 static List<Entry> entries(int count,long first){List<Entry> result=new ArrayList<>();for(int i=0;i<count;i++)result.add(new Entry(first+i));return result;}
 static List<ZjuHomeworkStore.History> history(int count,long first){List<ZjuHomeworkStore.History> result=new ArrayList<>();for(int i=0;i<count;i++)result.add(new ZjuHomeworkStore.History(first-i));return result;}
 public static void main(String[] args){
  Source returning=new Source();returning.initialized=true;returning.offset=100;
  for(Entry e:entries(100,1))returning.addEntry(e);returning.viewport.y=7000;
  Object sameRow=returning.rows.children.get(60);returning.onResume();
  check(returning.requests==0&&returning.offset==100&&returning.rows.children.get(60)==sameRow&&returning.viewport.y==7000,
   "detail return retains all loaded rows, identity, cursor and scroll");
  Bundle saved=new Bundle();returning.onSaveInstanceState(saved);
  check(saved.getInt("loaded_count")==100&&saved.getInt("scroll_y")==7000,"source saves complete loaded extent and reading position");
  Source restored=new Source();restored.restoreCount=saved.getInt("loaded_count");restored.restoreScroll=saved.getInt("scroll_y");restored.onResume();
  restored.deliver(0,entries(51,1));
  check(restored.offset==50&&restored.requests==2&&restored.restoreScroll==7000,"source restoration requests the second page before restoring scroll");
  Bundle mid=new Bundle();restored.onSaveInstanceState(mid);check(mid.getInt("loaded_count")==100,"recreate during replay retains the full target extent");
  restored.deliver(50,entries(51,51));check(restored.offset==100&&restored.requests==2,"source restoration stops at the saved extent");
  check(restored.viewport.y==0,"source restoration waits for final real layout");restored.viewport.tree.draw();
  check(restored.viewport.y==7000&&restored.restoreScroll==-1,"source restores scroll after all saved pages are laid out");
  Source shortened=new Source();shortened.restoreCount=100;shortened.restoreScroll=7000;shortened.onResume();shortened.deliver(0,entries(25,1));shortened.viewport.tree.draw();
  check(shortened.offset==25&&shortened.requests==1&&shortened.viewport.y==1500,"shorter source stops at actual end and clamps to reachable scroll");
  Source emptyPending=new Source();emptyPending.initialized=true;emptyPending.dataReady=true;emptyPending.firstDataPresentation=true;
  int entered=UiMotion.entrances;emptyPending.onResume();
  check(UiMotion.entrances==entered+1&&emptyPending.requests==0,"return retries an unpresented empty result without re-reading");
  restored.source="system:8";ScheduleSourceRepository.readable=false;restored.onResume();
  check(restored.offset==0&&restored.rows.getChildCount()==0&&restored.requests==3,"revoked calendar permission never retains old rows");ScheduleSourceRepository.readable=true;
  Source revoked=new Source();revoked.source="zju";revoked.initialized=true;revoked.addEntry(new Entry(1));ZjuSettingsStore.account="accountB";revoked.onResume();
  check(revoked.finishing&&revoked.rows.getChildCount()==0&&revoked.requests==0,"changed school identity closes and clears the old source");ZjuSettingsStore.account="accountA";
  History original=new History();original.loadedCount=100;original.viewport.y=6500;Bundle historyState=new Bundle();original.onSaveInstanceState(historyState);
  History replay=new History();replay.restoreCount=historyState.getInt("loaded_count");replay.restoreScroll=historyState.getInt("scroll_y");replay.deliver(history(50,200));
  check(replay.loadedCount==50&&replay.requests==1&&replay.restoreScroll==6500,"history restoration follows the saved row extent through its original cursor");
  check(replay.beforeId==151,"history replay advances the real history cursor");replay.deliver(history(50,150));replay.viewport.tree.draw();
  check(replay.loadedCount==100&&replay.requests==1&&replay.beforeId==101&&replay.viewport.y==6500,"history restores appended pages and final position once");
  History fewer=new History();fewer.restoreCount=100;fewer.restoreScroll=6500;fewer.deliver(history(12,200));fewer.viewport.tree.draw();
  check(fewer.requests==0&&fewer.loadedCount==12&&fewer.viewport.y==200,"history ending early clamps instead of inventing rows");
  History touched=new History();touched.restoreCount=50;touched.restoreScroll=6500;touched.deliver(history(50,200));touched.restoreScroll=-1;touched.viewport.y=900;touched.viewport.tree.draw();
  check(touched.viewport.y==900,"a new user scroll cancels delayed restoration");
  System.out.println("Page recovery checks passed: "+checks+" (production lifecycle and delivery callbacks, JVM doubles)");
 }
}
'''.replace('@@SOURCE_METHODS@@', source_methods).replace('@@SOURCE_DELIVERY@@', source_delivery)
harness = harness.replace('@@HISTORY_METHODS@@', history_methods).replace('@@HISTORY_DELIVERY@@', history_delivery)
harness = harness.replace('android.view.ViewTreeObserver', 'ViewTreeObserver').replace('android.view.View.', 'View.')
directory = ROOT / 'build/page-audit-next/school/recovery-host'
directory.mkdir(parents=True, exist_ok=True)
path = directory / 'PageRecoveryCheck.java'
path.write_text(harness, encoding='utf-8')
home = Path(os.environ['JAVA_HOME'])
subprocess.run([str(home / 'bin/javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(directory), str(path)], check=True)
subprocess.run([str(home / 'bin/java.exe'), '-cp', str(directory), 'PageRecoveryCheck'], check=True)
