"""Exercise the production local-course export with provider/source adapters."""
from pathlib import Path
import os
import subprocess
import textwrap

root = Path(__file__).resolve().parents[1]
out = root / 'build/course-export-checks'
src = out / 'src'
src.mkdir(parents=True, exist_ok=True)

def write(path, value):
    target = src / path
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(textwrap.dedent(value), encoding='utf-8')

write('android/content/Context.java', 'package android.content; public class Context {}')
write('com/donglan/chrona/AndroidSync.java', 'package com.donglan.chrona; class AndroidSync {static final Object LOCK=new Object();}')
write('com/donglan/chrona/ZjuSettingsStore.java', '''package com.donglan.chrona;
class ZjuSettingsStore {static boolean enabled=true; ZjuSettingsStore(android.content.Context c){} boolean enabled(){return enabled;} String account(){return "fixture";}}''')
write('com/donglan/chrona/ZjuTimetableStore.java', '''package com.donglan.chrona;
class ZjuTimetableStore {static java.util.List<com.donglan.chrona.zju.ZjuSnapshot.Lesson> rows=new java.util.ArrayList<>();
ZjuTimetableStore(android.content.Context c){} java.util.List<com.donglan.chrona.zju.ZjuSnapshot.Lesson> load(String account){return rows;}}''')
write('com/donglan/chrona/CourseAgenda.java', '''package com.donglan.chrona;
class CourseAgenda {static java.util.List<Item> rows=new java.util.ArrayList<>(); static java.util.List<Item> all(android.content.Context c,java.time.ZoneId z){return rows;}
static class Item {String title,location,description;long start,end;boolean allDay;
Item(String title,long start,long end,String location,boolean allDay){this.title=title;this.start=start;this.end=end;this.location=location;this.allDay=allDay;description="imported notes";}}}''')
write('com/donglan/chrona/CourseSourceMatching.java', 'package com.donglan.chrona; class CourseSourceMatching {static final long IMPORTED=-1,ZJU=-2;}')
write('com/donglan/chrona/calendar/CalendarStore.java', '''package com.donglan.chrona.calendar;
import java.util.*;
public class CalendarStore {public static List<CalendarOccurrence> rows=new ArrayList<>();public static List<EventInput> inserted=new ArrayList<>();
public static boolean permission=true;public static int failAt=-1,queries;
public CalendarStore(android.content.Context c){}public boolean hasReadPermission(){return permission;}public boolean hasWritePermission(){return permission;}
public List<CalendarOccurrence> listAllInstances(long begin,long end){queries++;return rows.stream().filter(x->x.startMillis<end&&x.endMillis>begin).toList();}
public long insertEvent(EventInput input){if(inserted.size()==failAt)throw new IllegalStateException("provider failure");inserted.add(input);
rows.add(new CalendarOccurrence(rows.size()+1,input.start,input.end,input.title,input.allDay,input.location,"owned"));return rows.size();}
public static class EventInput {public String title,description,location;public long start,end;public boolean allDay;public List<Integer> reminders;
public EventInput(String title,long start,long end,String zone,boolean allDay,String description,String location,List<Integer> reminders){this.title=title;this.start=start;this.end=end;this.allDay=allDay;this.description=description;this.location=location;this.reminders=reminders;}}}''')
write('com/donglan/chrona/CourseCalendarExportCheck.java', '''package com.donglan.chrona;
import com.donglan.chrona.calendar.*;import com.donglan.chrona.zju.ZjuSnapshot;import java.util.*;
class CourseCalendarExportCheck {
static int checks;static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
public static void main(String[] ignored)throws Exception {
var context=new android.content.Context();long at=java.time.Instant.parse("2026-10-19T02:00:00Z").toEpochMilli();
CourseAgenda.rows.add(new CourseAgenda.Item("数学",at,at+3600000,"教室1",false));
CourseAgenda.rows.add(new CourseAgenda.Item("物理",at+86400000,at+90000000,"教室2",false));
ZjuTimetableStore.rows.add(new ZjuSnapshot.Lesson("a","数学","教室1","秋",at,at+3600000,"teacher notes"));
ZjuTimetableStore.rows.add(new ZjuSnapshot.Lesson("b","化学","教室3","秋",at+172800000,at+176400000,"school notes"));
var all=CourseCalendarExport.load(context,true,true);
check(all.size()==3,"imported plus school exact duplicates collapse");
check(all.get(0).description.equals("teacher notes"),"school metadata wins duplicate local course");
check(CourseCalendarExport.load(context,true,false).size()==2,"imported-only selection");
check(CourseCalendarExport.load(context,false,true).size()==2,"school-only selection");
ZjuSettingsStore.enabled=false;check(CourseCalendarExport.load(context,true,true).size()==2,"master-off excludes school cache");
boolean failed=false;try{CourseCalendarExport.write(context,all);}catch(IllegalStateException expected){failed=true;}
check(failed&&CalendarStore.inserted.isEmpty(),"queued school selection cannot bypass master-off");ZjuSettingsStore.enabled=true;
CalendarStore.rows.add(new CalendarOccurrence(7,at,at+3600000," 数学 ",false,"教室1","other app",null,19,"other"));
var first=CourseCalendarExport.write(context,all);check(first.inserted==2&&first.skipped==1,"existing other-app course skipped without modification");
check(CalendarStore.rows.get(0).eventId==7,"other calendar original retained");
check(CalendarStore.queries==2,"one occupied-month query per write, including rejected queued write");
var second=CourseCalendarExport.write(context,all);check(second.inserted==0&&second.skipped==3,"repeat export idempotent");
check(CalendarStore.inserted.stream().allMatch(x->x.reminders.isEmpty()),"export does not add duplicate reminders");
var different=new CalendarOccurrence(0,at,at+3600000,"数学",false,"教室9","import");
check(CourseCalendarExport.write(context,List.of(different)).inserted==1,"same title/time but different room remains separate");
CalendarStore.permission=false;failed=false;try{CourseCalendarExport.write(context,all);}catch(IllegalStateException expected){failed=true;}
check(failed,"permission denial performs no writes");CalendarStore.permission=true;
var x=new CalendarOccurrence(0,at+600000,at+1200000,"x",false,"","import");
var y=new CalendarOccurrence(0,at+1800000,at+2400000,"y",false,"","import");
CalendarStore.failAt=CalendarStore.inserted.size()+1;failed=false;
try{CourseCalendarExport.write(context,List.of(x,y));}catch(IllegalStateException expected){failed=expected.getMessage().contains("已写入 1 节");}
check(failed,"partial provider failure reports exact progress");CalendarStore.failAt=-1;
var retry=CourseCalendarExport.write(context,List.of(x,y));check(retry.inserted==1&&retry.skipped==1,"retry preserves successful writes without duplicate");
var wholeDay=new CourseAgenda.Item("全天课",at,at+86400000,"",true);CourseAgenda.rows.add(wholeDay);
check(CourseCalendarExport.load(context,true,false).get(2).allDay,"all-day semantics preserved");
System.out.println("PASS: "+checks+" production course export checks");
}}
''')
base = root / 'app/src/main/java/com/donglan/chrona'
sources = list(src.rglob('*.java')) + [base / name for name in
    ('CourseCalendarExport.java', 'calendar/CalendarOccurrence.java', 'calendar/AllDayDates.java', 'zju/ZjuSnapshot.java')]
java = Path(os.environ.get('JAVA_HOME', 'D:/Minecraft/java21')) / 'bin'
subprocess.run([str(java / 'javac.exe'), '--release', '17', '-encoding', 'UTF-8', '-d', str(out), *map(str, sources)], check=True)
subprocess.run([str(java / 'java.exe'), '-cp', str(out), 'com.donglan.chrona.CourseCalendarExportCheck'], check=True)
