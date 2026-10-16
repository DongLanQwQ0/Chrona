"""Exercise production source merge/cache with synthetic data and Android I/O adapters."""
import os
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
out = root / 'build/zju-sources-check'
out.mkdir(parents=True, exist_ok=True)
files = {
'android/content/Context.java': '''package android.content;public class Context {final java.io.File dir;public Context(java.io.File d){dir=d;}public java.io.File getFilesDir(){return dir;}}''',
'android/content/Intent.java': '''package android.content;public class Intent {public Intent(Context c,Class<?> x){}public Intent putExtra(String k,long v){return this;}}''',
'android/util/AtomicFile.java': '''package android.util;import java.io.*;public class AtomicFile {final File file;public AtomicFile(File f){file=f;}public File getBaseFile(){return file;}public byte[] readFully()throws IOException{return java.nio.file.Files.readAllBytes(file.toPath());}public FileOutputStream startWrite()throws IOException{return new FileOutputStream(file);}public void finishWrite(FileOutputStream s)throws IOException{s.close();}public void failWrite(FileOutputStream s)throws IOException{s.close();}public void delete(){file.delete();}}''',
'com/donglan/chrona/CourseAgenda.java': '''package com.donglan.chrona;import java.util.*;class CourseAgenda {static List<Item> rows=new ArrayList<>();static long revision(){return 0;}static List<Item> load(android.content.Context c,long b,long e,java.time.ZoneId z){return rows;}static class Item {String termId="term",key,title,location="";long start,end;boolean allDay;Item(String k,String t,long s,long e,boolean a){key=k;title=t;start=s;end=e;allDay=a;}}}''',
'com/donglan/chrona/ScheduleSources.java': '''package com.donglan.chrona;class ScheduleSources {static boolean imported=true,zju=true;static String preference="all";static long revision(android.content.Context c){return 0;}static boolean importedEnabled(android.content.Context c){return imported;}static boolean zjuEnabled(android.content.Context c){return zju;}static String primary(android.content.Context c){return preference;}static java.util.Map<String,String> courseRules(android.content.Context c){return java.util.Map.of();}}''',
'com/donglan/chrona/ZjuSettingsStore.java': '''package com.donglan.chrona;class ZjuSettingsStore {static boolean on=true,table=true;ZjuSettingsStore(android.content.Context c){}boolean enabled(){return on;}boolean timetable(){return on&&table;}String account(){return "synthetic-account";}}''',
'com/donglan/chrona/ExternalCourseDetailActivity.java': '''package com.donglan.chrona;class ExternalCourseDetailActivity {}''',
'com/donglan/chrona/SourceCheck.java': '''package com.donglan.chrona;
import android.content.Context;import com.donglan.chrona.calendar.CalendarOccurrence;import com.donglan.chrona.zju.ZjuSnapshot;import java.time.*;import java.util.*;
public class SourceCheck {
static int count;static void check(boolean ok,String message){count++;if(!ok)throw new AssertionError(message);}
public static void main(String[]args)throws Exception{
TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));Context c=new Context(java.nio.file.Files.createTempDirectory(java.nio.file.Path.of(args[0]),"fixture-").toFile());
long begin=LocalDate.of(2026,10,16).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli(),end=begin+86400000L;
ZjuTimetableStore store=new ZjuTimetableStore(c);
ZjuSnapshot.Lesson a=new ZjuSnapshot.Lesson("a","Course","Room","autumn",begin+3600000,end-3600000);
ZjuSnapshot.Lesson b=new ZjuSnapshot.Lesson("b","Winter","Room","winter",end+3600000,end+7200000);
store.save("synthetic-account",List.of(a,b),List.of("autumn","winter"));check(store.load("synthetic-account").size()==2,"initial cache");
check(store.load("other-account").isEmpty(),"account isolation");
store.save("synthetic-account",List.of(),List.of());check(store.load("synthetic-account").size()==2,"failed response preserves cache");
store.save("synthetic-account",List.of(),List.of("winter"));check(store.load("synthetic-account").size()==1,"successful empty term removes old lessons");
CourseAgenda.rows.add(new CourseAgenda.Item("imported","Course",a.start,a.end,false));
List<CalendarOccurrence> rows=ExternalCourseAgenda.merge(c,List.of(),begin,end);check(rows.size()==2,"separate sources retained without primary");
check(rows.stream().allMatch(x->x.eventId<0),"read only identity namespace");long stable=rows.get(0).eventId;
check(ExternalCourseAgenda.merge(c,List.of(),begin,end).get(0).eventId==stable,"stable identities");
ScheduleSources.preference="zju";rows=ExternalCourseAgenda.merge(c,List.of(),begin,end);check(rows.size()==1&&rows.get(0).calendarName.equals("折姜星辰"),"explicit primary suppresses exact duplicate");
CalendarOccurrence system=new CalendarOccurrence(99,a.start,a.end,"Course",false,"Room","system",null,42L,"calendar-account");
ScheduleSources.preference="system:42";rows=ExternalCourseAgenda.merge(c,List.of(system),begin,end);check(rows.size()==1&&rows.get(0).eventId==99,"system primary");
CalendarOccurrence unrelated=new CalendarOccurrence(100,a.start,a.end,"Different",false,"Room","system");
rows=ExternalCourseAgenda.merge(c,List.of(system,unrelated),begin,end);check(rows.size()==2,"unrelated system item remains");
ScheduleSources.imported=false;ScheduleSources.zju=false;rows=ExternalCourseAgenda.merge(c,List.of(unrelated),begin,end);check(rows.size()==1,"source switches intersect independently");
ScheduleSources.imported=true;CourseAgenda.rows.clear();CourseAgenda.rows.add(new CourseAgenda.Item("all-day","All day",begin,end,true));
rows=ExternalCourseAgenda.merge(c,List.of(),begin,end);check(rows.size()==1&&rows.get(0).displayStart(ZoneId.systemDefault())==begin&&rows.get(0).displayEnd(ZoneId.systemDefault())==end,"all day local to UTC dates roundtrip");
ScheduleSources.imported=false;ScheduleSources.zju=true;ZjuSettingsStore.on=false;check(ExternalCourseAgenda.merge(c,List.of(),begin,end).isEmpty(),"master off hides cached API course");
store.clear();check(store.load("synthetic-account").isEmpty(),"logout clears cache");
System.out.println("ZJU source/cache checks: "+count+" passed (Android storage adapters; no device rendering)");
}}
'''
}
paths=[]
for name,text in files.items():
    path=out/name;path.parent.mkdir(parents=True,exist_ok=True);path.write_text(text,encoding='utf-8');paths.append(path)
src=root/'app/src/main/java/com/donglan/chrona'
paths += [src/'ExternalCourseAgenda.java',src/'CourseSourceMatching.java',src/'ZjuTimetableStore.java',src/'zju/ZjuSnapshot.java',src/'calendar/CalendarOccurrence.java',src/'calendar/AllDayDates.java']
java=Path(os.environ.get('JAVA_HOME','D:/Minecraft/java21'))/'bin'
cp=str(root/'build/ai-checks/json.jar')
subprocess.run([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-cp',cp,'-d',str(out),*map(str,paths)],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out)+os.pathsep+cp,'com.donglan.chrona.SourceCheck',str(out)],check=True)
