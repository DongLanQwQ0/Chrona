"""Execute calendar day-list and time-grid card factories to verify deferred creation."""
import os,re,subprocess
from pathlib import Path
root=Path(__file__).resolve().parents[1]
s=(root/'app/src/main/java/com/donglan/chrona/CalendarOverviewPage.java').read_text(encoding='utf-8')
def method(signature):
 start=s.index(signature); brace=s.index('{',start); depth=1; end=brace+1
 while depth: depth+=(s[end]=='{')-(s[end]=='}');end+=1
 return s[start:end]
day=method('    private void dayList(')
start=s.index('        android.graphics.Paint.FontMetrics smallMetrics =');end=s.index('        View nowLine =',start)
timeline='void events(){int maxCardHeight=0; List<List<CalendarTimelineLayout.Block>> dayBlocks=new ArrayList<>(); for(int d=0;d<days;d++)dayBlocks.add(CalendarTimelineLayout.day(entries,first.plusDays(d),zone));\n'+s[start:end]+'\nmeasuredMax=maxCardHeight;}'
prod=(day+'\n'+timeline).replace('android.graphics.Paint','Paint').replace('android.graphics.drawable.GradientDrawable','GradientDrawable').replace('android.text.TextUtils','TextUtils')
java=r'''
import java.util.*;import java.time.*;
class View {Object params;void setLayoutParams(Object p){params=p;}View(){}View(Object c){}void setBackgroundColor(int c){}void setContentDescription(String s){}static int IMPORTANT_FOR_ACCESSIBILITY_NO=1;Object tag;void setBackground(Object o){}void setImportantForAccessibility(int i){}void setTag(Object t){tag=t;}void setFocusable(boolean b){}void setOnClickListener(java.util.function.Consumer<View> c){}}
class LinearLayout extends View {List<View> children=new ArrayList<>();void addView(View v){children.add(v);}void addView(View v,Object p){addView(v);}void setPadding(int a,int b,int c,int d){}static class LayoutParams {int rightMargin;LayoutParams(int a,int b){}LayoutParams(int a,int b,int c){}}}
class FrameLayout extends LinearLayout {static class LayoutParams {int leftMargin,topMargin,width,height;LayoutParams(int a,int b){width=a;height=b;}}}
class TextView extends View {int textChanges;void setText(String s){textChanges++;}void setTextSize(int n){}void setMaxLines(int n){}void setEllipsize(Object o){}void setTextColor(int c){}void setPadding(int a,int b,int c,int d){}void setGravity(int g){}Paint getPaint(){return new Paint();}}
class Paint {static int fontHeight=15;static class FontMetrics {float descent=3,ascent=3-fontHeight;}FontMetrics getFontMetrics(){return new FontMetrics();}}
class GradientDrawable {static int OVAL=1;void setShape(int s){}void setColor(int c){}}
class TextUtils {static class TruncateAt {static Object END=new Object();}}
class Gravity {static int TOP=1;}
class AgendaEntry {String category="event";long start,end;String title="课",location="教室",source="拾时";boolean system,confirmed=true;Integer calendarColor;String key(){return title;}boolean isTask(){return "task".equals(category)||"homework".equals(category);}}
class EntryStatus {EntryStatus(AgendaEntry e,TextView t,TextView time){}}
class CalendarSourceColors {static int mix(Integer c,int p){return p;}}
class PressFeedback {static final float CARD_PRESSED_SCALE=1.08f;static void group(LinearLayout l,float f){}}
class UiStyle {static int RADIUS_PANEL=24;static class Palette {int primary,text,outline;}static Palette colors(Object o){return new Palette();}static void muted(TextView t){}static void pressable(View v){}static void addSpaced(LinearLayout p,View v,int a,int b){p.addView(v);}}
class CalendarTimelineLayout {static class Block {AgendaEntry entry=new AgendaEntry();double startMinute=600,endMinute=650;int lane,lanes=1;}static List<Block> day(List<AgendaEntry> e,LocalDate d,ZoneId z){List<Block> r=new ArrayList<>();for(AgendaEntry a:e){Block b=new Block();b.entry=a;r.add(b);}return r;}}
public class CalendarEventBuildCheck {
 Queue<Runnable> pending=new ArrayDeque<>();List<EntryStatus> statuses=new ArrayList<>();List<AgendaEntry> entries=new ArrayList<>();int days=7,hourHeight=72,dayWidth=80,gutter=40,height=1728,created;
 int measuredMax;static class Geometry {int hourHeight=72,dayWidth=80,height=1728;float y(double minute){return (float)(minute/60*hourHeight);}}Geometry geometry=new Geometry();List<Runnable> geometryBindings=new ArrayList<>();
 LocalDate first=LocalDate.of(2026,10,13);ZoneId zone=ZoneId.systemDefault();FrameLayout grid=new FrameLayout();
 LinearLayout column(){return new LinearLayout();}LinearLayout row(){return new LinearLayout();}TextView text(String t,int n,boolean bold){created++;return new TextView();}int dp(int n){return n;}
 void calendarSurface(View v,boolean b,int r,boolean o){}void eventSurface(View v,AgendaEntry e){}void open(AgendaEntry e){}void updateStatus(EntryStatus s){}String time(AgendaEntry e){return "10:00";}
 List<AgendaEntry> onDay(List<AgendaEntry> e,LocalDate d){return e;}void notice(LinearLayout p,String s){}void enqueueCalendarBuild(Runnable r){pending.add(r);}
 __PROD__
 static int checks;static void check(boolean b){checks++;if(!b)throw new AssertionError("calendar factory check "+checks);}
 public static void main(String[] a){
 for(int count:new int[]{1,20,55}){
 CalendarEventBuildCheck c=new CalendarEventBuildCheck();for(int i=0;i<count;i++)c.entries.add(new AgendaEntry());LinearLayout content=c.column();c.dayList(content,c.entries,c.first);
 check(c.created==0&&c.statuses.isEmpty()&&content.children.size()==1);check(c.pending.size()==count);
 while(!c.pending.isEmpty()){int before=c.statuses.size();c.pending.remove().run();check(c.statuses.size()==before+1);}check(c.statuses.size()==count);
 CalendarEventBuildCheck t=new CalendarEventBuildCheck();t.entries=c.entries;t.events();check(t.created==2&&t.grid.children.isEmpty());check(t.pending.size()==count*7);
 while(!t.pending.isEmpty()){int before=t.grid.children.size();t.pending.remove().run();check(t.grid.children.size()==before+1);}check(t.grid.children.size()==count*7);
 Set<Object> tags=new HashSet<>();for(View v:t.grid.children)tags.add(v.tag);check(tags.size()==7);
 int built=t.created;FrameLayout.LayoutParams before=(FrameLayout.LayoutParams)t.grid.children.get(0).params;
 t.geometry.hourHeight=36;t.geometry.dayWidth=40;t.geometry.height=864;for(Runnable binding:t.geometryBindings)binding.run();
 FrameLayout.LayoutParams after=(FrameLayout.LayoutParams)t.grid.children.get(0).params;
 check(t.created==built&&t.pending.isEmpty());check(after.width<before.width&&after.height<before.height&&after.topMargin<before.topMargin);
 TextView firstCard=(TextView)t.grid.children.get(0);int changed=firstCard.textChanges;
 t.geometry.hourHeight=37;for(Runnable binding:t.geometryBindings)binding.run();check(firstCard.textChanges==changed);
 }
 CalendarEventBuildCheck broken=new CalendarEventBuildCheck(){@Override void enqueueCalendarBuild(Runnable r){r.run();}};broken.entries.add(new AgendaEntry());broken.dayList(broken.column(),broken.entries,broken.first);check(broken.created>0&&broken.statuses.size()==1);broken.events();check(broken.grid.children.size()==7);
 Paint.fontHeight=100;CalendarEventBuildCheck large=new CalendarEventBuildCheck();large.entries.add(new AgendaEntry());large.events();check(large.measuredMax>=108);
 while(!large.pending.isEmpty())large.pending.remove().run();large.geometry.hourHeight=30;for(Runnable binding:large.geometryBindings)binding.run();
 check(((FrameLayout.LayoutParams)large.grid.children.get(0).params).height>=104);Paint.fontHeight=15;
 System.out.println("Calendar event factories passed: "+checks+" checks; immediate-construction negative control reproduces burst");
 }
}
'''.replace('__PROD__',prod)
out=root/'build/calendar-event-build-check';out.mkdir(parents=True,exist_ok=True);f=out/'CalendarEventBuildCheck.java';f.write_text(java,encoding='utf-8');j=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(j/'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out),str(f)],check=True);subprocess.run([str(j/'java.exe'),'-cp',str(out),'CalendarEventBuildCheck'],check=True)
