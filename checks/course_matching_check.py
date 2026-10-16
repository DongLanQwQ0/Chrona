"""Run production course matching against synthetic data; no phone, account or network."""
import os
import subprocess
from pathlib import Path

root = Path(__file__).resolve().parents[1]
out = root / 'build/course-matching-check'
out.mkdir(parents=True, exist_ok=True)
test = out / 'com/donglan/chrona/CourseMatchingCheck.java'
test.parent.mkdir(parents=True, exist_ok=True)
test.write_text(r'''package com.donglan.chrona;
import com.donglan.chrona.calendar.CalendarOccurrence;import java.util.*;
public class CourseMatchingCheck {
static int count;static void check(boolean x,String why){count++;if(!x)throw new AssertionError(why);}
static CalendarOccurrence c(long source,String title,String room,long start){return new CalendarOccurrence(source*100,start,start+3600000,title,false,room,"display name",null,source,"");}
static List<CalendarOccurrence> filter(CalendarOccurrence a,CalendarOccurrence b,String primary,Map<String,String> rules){return CourseSourceMatching.filter(List.of(a,b),primary,rules);}
public static void main(String[] args){
CalendarOccurrence a=c(-1,"高等数学Ⅰ（教师：张三）","东1",0), b=c(12,"高等数学I","东1",0);
check(CourseSourceMatching.strong(a,b),"NFKC Roman numerals and labelled teacher suffix");
check(filter(a,b,"all",Map.of()).size()==2,"default retains sources");
check(filter(a,b,"imported",Map.of()).size()==1,"strong match respects primary");
check(filter(a,b,"imported",Map.of(CourseSourceMatching.pairKey(a,b),"reject")).size()==2,"explicit rejection overrides strong heuristic");
check(!CourseSourceMatching.strong(a,c(12,"高等数学II","东1",0)),"Roman level remains distinct");
check(!CourseSourceMatching.strong(c(-1,"数学甲","",0),c(12,"数学乙","",0)),"Chinese course stages remain distinct");
check(!CourseSourceMatching.strong(c(-1,"物理实验","",0),c(12,"物理理论","",0)),"lab and theory remain distinct");
CalendarOccurrence code1=c(-1,"课程A 12345678","",0),code2=c(12,"导出名称B(课程编号:12345678)","",0);
check(CourseSourceMatching.strong(code1,code2),"same explicit course code and exact time");
check(!CourseSourceMatching.strong(c(-1,"数学I 12345678","",0),c(12,"数学II 12345678","",0)),"code cannot erase stage metadata");
check(!CourseSourceMatching.strong(c(-1,"数学I(课程编号:12345678)","",0),c(12,"数学II(课程编号:12345678)","",0)),"labelled code cannot erase Roman level");
CalendarOccurrence otherRoom=c(12,"高等数学I","西2",0);
check(!CourseSourceMatching.strong(a,otherRoom)&&CourseSourceMatching.suggested(a,otherRoom),"location conflict is review-only");
check(filter(a,otherRoom,"imported",Map.of()).size()==2,"location conflict retains both");
check(filter(a,otherRoom,"all",Map.of(CourseSourceMatching.pairKey(a,otherRoom),"imported")).size()==1,"explicit reviewed conflict can choose primary");
check(filter(a,c(-1,a.title,"东1",0),"imported",Map.of()).size()==2,"same source is never merged");
CalendarOccurrence alias=c(12,"高数导出名","东1",0);
Map<String,String> rules=Map.of(CourseSourceMatching.pairKey(a,alias),"imported");
check(filter(a,alias,"all",rules).size()==1,"manual alias link works");
check(filter(c(-1,a.title,"东1",604800000),c(12,alias.title,"东1",604800000),"all",rules).size()==1,"alias persists across weeks");
check(filter(a,c(12,alias.title,"东1",3600000),"all",rules).size()==2,"link does not suppress a different time");
check(!CourseSourceMatching.strong(c(-1,"数学1班","",0),c(12,"数学2班","",0)),"different groups stay separate");
check(CourseSourceMatching.source(a).equals("imported")&&CourseSourceMatching.source(b).equals("system:12"),"source keys ignore display names");
check(filter(a,c(12,"另一个课程","",0),"imported",Map.of()).size()==2,"unrelated same-slot course retained");
System.out.println("Course matching: "+count+" passed");
}}
''', encoding='utf-8')
src=root/'app/src/main/java/com/donglan/chrona'
java=Path(os.environ.get('JAVA_HOME','D:/Minecraft/java21'))/'bin'
subprocess.run([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out),
    str(test),str(src/'CourseSourceMatching.java'),str(src/'calendar/CalendarOccurrence.java'),str(src/'calendar/AllDayDates.java')],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out),'com.donglan.chrona.CourseMatchingCheck'],check=True)
