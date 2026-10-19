"""Run the actual widget loader/merge, checking source switches and visible read failures."""
from recurrence_dependencies import recurrence_java

from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/com/donglan/chrona"
out = root / "build/widget-source-failure-checks"
out.mkdir(parents=True, exist_ok=True)
fixtures = {
    "android/content/Context.java": "package android.content; public class Context {}",
    "android/content/Intent.java": "package android.content;public class Intent {public Intent(Context c,Class<?> target){}public Intent putExtra(String key,long value){return this;}}",
    "com/donglan/chrona/data/TaskStore.java": '''package com.donglan.chrona.data;
import java.util.*; public class TaskStore implements AutoCloseable {
public static final int WIDGET_ITEM_LIMIT=100; public static boolean fail;
public TaskStore(android.content.Context context) { }
public List<EventCandidate> widgetCandidates(long a,long b,long c,long d) {
if(fail) throw new IllegalStateException("local database unavailable");
long now=System.currentTimeMillis();
return List.of(new EventCandidate(1,2,"normal appointment",now+3600000,now+7200000,"UTC","","",null,false,null)); }
public List<Long> linkedCalendarIds() { return List.of(9L); } public void close() { }
}''',
    "com/donglan/chrona/calendar/CalendarStore.java": '''package com.donglan.chrona.calendar;
public class CalendarStore { public static int reads;public CalendarStore(android.content.Context context) { }
public boolean hasReadPermission() { return true; }
public java.util.List<CalendarOccurrence> listInstances(long a,long b,Object c) {
reads++;long now=java.time.LocalDate.now().atTime(12,0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();return java.util.List.of(
new CalendarOccurrence(3,now+3600000,now+7200000,"same external content",false,"room","system"),
new CalendarOccurrence(4,now+3600000,now+7200000,"same external content",false,"room","system"),
new CalendarOccurrence(9,now+3600000,now+7200000,"owned linked copy",false,"room","system")); } }''',
    "com/donglan/chrona/WidgetSourceFailureCheck.java": '''package com.donglan.chrona;
import android.content.Context; import java.time.*; import java.util.*;
class CalendarLinkReconciler { static void reconcileNow(Context context) { } }
class HomeTimelinePreferences { static boolean included;static boolean includesSystemCalendar(Context context) { return included; } }
class WidgetPreferences { static final int DEFAULT_PREVIEW_MINUTES=1320;
static boolean included;static boolean includesSystemCalendar(Context context) { return included; }
static int previewMinutes(Context context) { return DEFAULT_PREVIEW_MINUTES; } }
class ScheduleSources {
static boolean imported,zju;
static boolean importedEnabled(Context c){return imported;}static boolean zjuEnabled(Context c){return zju;}
static String primary(Context c){return "all";}static long revision(Context c){return 0;}
static Map<String,String> courseRules(Context c){return Map.of();}
}
class ZjuSettingsStore {ZjuSettingsStore(Context c){}boolean enabled(){return false;}boolean timetable(){return false;}String account(){return "fixture";}}
class ZjuTimetableStore {ZjuTimetableStore(Context c){}static long revision(){return 0;}List<com.donglan.chrona.zju.ZjuSnapshot.Lesson>load(String account){throw new AssertionError("disabled ZJU source must not load");}}
class ExternalCourseDetailActivity {}
class CourseAgenda {
static int calls;static boolean fail;
static long revision(){return 0;}
static class Item { long start,end; String title="imported course",location="room",key="fixture",termId="term",description="notes"; boolean allDay; }
static List<Item> load(Context context,long a,long b,ZoneId zone) throws Exception {
calls++;if(fail)throw new IllegalStateException("course store unavailable");Item item=new Item();item.start=LocalDate.now(zone).atTime(12,0).atZone(zone).toInstant().toEpochMilli();item.end=item.start+3600000;return List.of(item); }
}
public class WidgetSourceFailureCheck {
static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
public static void main(String[] args) {
Context context=new Context();
WidgetAgenda agenda=WidgetAgenda.load(context);
require(CourseAgenda.calls==0,"disabled imported timetable is not loaded into widget");
require(!agenda.failed && agenda.next!=null && agenda.next.id==1,"ordinary appointment retained");
require(com.donglan.chrona.calendar.CalendarStore.reads==0,"disabled switch performs no system listing");
HomeTimelinePreferences.included=true;agenda=WidgetAgenda.load(context);
require(com.donglan.chrona.calendar.CalendarStore.reads==0,"home switch cannot change widget sources");
WidgetPreferences.included=true;agenda=WidgetAgenda.load(context);
require(CourseAgenda.calls==0&&!agenda.failed&&agenda.next!=null,"system calendar switch cannot enable imported courses");
require(com.donglan.chrona.calendar.CalendarStore.reads==1,"enabled switch reads system instances");
require(agenda.today.stream().filter(item->item.system&&item.title.equals("same external content")).count()==1,"identical system entries merge according to shared source contract");
require(agenda.today.stream().noneMatch(item->item.system&&item.id==9),"only own associated provider copy excluded");
ScheduleSources.imported=true;agenda=WidgetAgenda.load(context);
require(CourseAgenda.calls==1&&!agenda.failed&&agenda.today.stream().anyMatch(item->item.id<0&&item.title.equals("imported course")),"enabled imported source reaches widget with external identity");
CourseAgenda.fail=true;agenda=WidgetAgenda.load(context);
require(agenda.failed&&agenda.today.isEmpty()&&agenda.next==null,"enabled course read failure is visible and cannot leave a partial snapshot");
ScheduleSources.imported=false;agenda=WidgetAgenda.load(context);
require(!agenda.failed&&agenda.next!=null&&CourseAgenda.calls==2,"disabling failed course source restores ordinary appointment without reading it");
com.donglan.chrona.data.TaskStore.fail=true; agenda=WidgetAgenda.load(context);
require(agenda.failed,"local database failure remains a real loader failure");
System.out.println("Widget source checks passed: disabled-source isolation, enabled courses, shared duplicate merge and visible source failures");
} }
'''
}
stubs = []
for name, text in fixtures.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    stubs.append(path)
sources = [src / relative for relative in ("WidgetAgenda.java", "ExternalCourseAgenda.java", "CourseSourceMatching.java", "zju/ZjuSnapshot.java", "calendar/CalendarOccurrence.java",
           "calendar/AllDayDates.java", "data/EventCandidate.java", "data/EventCategory.java")]
java = Path(os.environ["JAVA_HOME"]) / "bin"
subprocess.run(recurrence_java([str(java / "javac.exe"), "--release", "17", "-encoding", "UTF-8", "-d", str(out),
                *map(str, sources + stubs)]), check=True)
subprocess.run(recurrence_java([str(java / "java.exe"), "-cp", str(out), "com.donglan.chrona.WidgetSourceFailureCheck"]), check=True)
