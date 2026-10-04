"""Run the actual widget loader, verifying isolated timetable data and calendar switch."""
from pathlib import Path
import os
import subprocess

root = Path(__file__).resolve().parents[1]
src = root / "app/src/main/java/com/donglan/chrona"
out = root / "build/widget-source-failure-checks"
out.mkdir(parents=True, exist_ok=True)
fixtures = {
    "android/content/Context.java": "package android.content; public class Context {}",
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
reads++;long now=System.currentTimeMillis();return java.util.List.of(
new CalendarOccurrence(3,now+3600000,now+7200000,"same external content",false,"room","system"),
new CalendarOccurrence(4,now+3600000,now+7200000,"same external content",false,"room","system"),
new CalendarOccurrence(9,now+3600000,now+7200000,"owned linked copy",false,"room","system")); } }''',
    "com/donglan/chrona/WidgetSourceFailureCheck.java": '''package com.donglan.chrona;
import android.content.Context; import java.time.*; import java.util.*;
class CalendarLinkReconciler { static void reconcileNow(Context context) { } }
class HomeTimelinePreferences { static boolean included;static boolean includesSystemCalendar(Context context) { return included; } }
class WidgetPreferences { static final int DEFAULT_PREVIEW_MINUTES=1320;
static int previewMinutes(Context context) { return DEFAULT_PREVIEW_MINUTES; } }
class CourseAgenda {
static int calls;
static class Item { long start,end; String title,location,key,termId; boolean allDay; }
static List<Item> load(Context context,long a,long b,ZoneId zone) throws Exception {
calls++;throw new AssertionError("widget must not load imported courses"); }
}
public class WidgetSourceFailureCheck {
static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
public static void main(String[] args) {
Context context=new Context();
WidgetAgenda agenda=WidgetAgenda.load(context);
require(CourseAgenda.calls==0,"imported timetable never loaded into widget");
require(!agenda.failed && agenda.next!=null && agenda.next.id==1,"ordinary appointment retained");
require(com.donglan.chrona.calendar.CalendarStore.reads==0,"disabled switch performs no system listing");
HomeTimelinePreferences.included=true;agenda=WidgetAgenda.load(context);
require(CourseAgenda.calls==0&&!agenda.failed&&agenda.next!=null,"enabling system calendar still never imports courses");
require(com.donglan.chrona.calendar.CalendarStore.reads==1,"enabled switch reads system instances");
require(agenda.today.stream().filter(item->item.system&&item.title.equals("same external content")).count()==2,"system same content remains separate");
require(agenda.today.stream().noneMatch(item->item.system&&item.id==9),"only own associated provider copy excluded");
com.donglan.chrona.data.TaskStore.fail=true; agenda=WidgetAgenda.load(context);
require(agenda.failed,"local database failure remains a real loader failure");
System.out.println("Widget source checks passed: timetable isolation and retained appointments");
} }
'''
}
stubs = []
for name, text in fixtures.items():
    path = out / name
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    stubs.append(path)
sources = [src / relative for relative in ("WidgetAgenda.java", "calendar/CalendarOccurrence.java",
           "calendar/AllDayDates.java", "data/EventCandidate.java", "data/EventCategory.java")]
java = Path(os.environ["JAVA_HOME"]) / "bin"
subprocess.run([str(java / "javac.exe"), "--release", "17", "-encoding", "UTF-8", "-d", str(out),
                *map(str, sources + stubs)], check=True)
subprocess.run([str(java / "java.exe"), "-cp", str(out), "com.donglan.chrona.WidgetSourceFailureCheck"], check=True)
