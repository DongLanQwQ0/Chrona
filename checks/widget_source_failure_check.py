"""Run the actual widget loader with independently failing local/course data sources."""
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
public List<Long> linkedCalendarIds() { return List.of(); } public void close() { }
}''',
    "com/donglan/chrona/calendar/CalendarStore.java": '''package com.donglan.chrona.calendar;
public class CalendarStore { public CalendarStore(android.content.Context context) { }
public boolean hasReadPermission() { return true; }
public java.util.List<CalendarOccurrence> listInstances(long a,long b,Object c) { return java.util.List.of(); } }''',
    "com/donglan/chrona/WidgetSourceFailureCheck.java": '''package com.donglan.chrona;
import android.content.Context; import java.time.*; import java.util.*;
class CalendarLinkReconciler { static void reconcileNow(Context context) { } }
class HomeTimelinePreferences { static boolean includesSystemCalendar(Context context) { return false; } }
class WidgetPreferences { static final int DEFAULT_PREVIEW_MINUTES=1320;
static int previewMinutes(Context context) { return DEFAULT_PREVIEW_MINUTES; } }
class CourseAgenda {
static boolean fail=true;
static class Item { long start,end; String title,location,key,termId; boolean allDay; }
static List<Item> load(Context context,long a,long b,ZoneId zone) throws Exception {
if(fail) throw new java.io.IOException("corrupt timetable"); return List.of(); }
}
public class WidgetSourceFailureCheck {
static void require(boolean condition,String message) { if(!condition) throw new AssertionError(message); }
public static void main(String[] args) {
Context context=new Context();
WidgetAgenda agenda=WidgetAgenda.load(context);
require(agenda.courseUnavailable,"course failure must remain visible");
require(!agenda.failed && agenda.next!=null && agenda.next.id==1,"course failure must preserve normal next appointment");
CourseAgenda.fail=false; agenda=WidgetAgenda.load(context);
require(!agenda.courseUnavailable && !agenda.failed && agenda.next!=null,"successful reread clears course warning");
com.donglan.chrona.data.TaskStore.fail=true; agenda=WidgetAgenda.load(context);
require(agenda.failed,"local database failure remains a real loader failure");
System.out.println("Widget source failure checks passed: isolated course error, retained next appointment and recovery");
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
