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
check(filter(a,c(-1,a.title,"东1",0),"imported",Map.of()).size()==1,"exact same-source duplicates merge");
CalendarOccurrence alias=c(12,"高数导出名","东1",0);
Map<String,String> rules=Map.of(CourseSourceMatching.pairKey(a,alias),"imported");
check(filter(a,alias,"all",rules).size()==1,"manual alias link works");
check(filter(c(-1,a.title,"东1",604800000),c(12,alias.title,"东1",604800000),"all",rules).size()==1,"alias persists across weeks");
check(filter(a,c(12,alias.title,"东1",3600000),"all",rules).size()==2,"link does not suppress a different time");
check(!CourseSourceMatching.strong(c(-1,"数学1班","",0),c(12,"数学2班","",0)),"different groups stay separate");
check(CourseSourceMatching.source(a).equals("imported")&&CourseSourceMatching.source(b).equals("system:12"),"source keys ignore display names");
check(filter(a,c(12,"另一个课程","",0),"imported",Map.of()).size()==2,"unrelated same-slot course retained");
CalendarOccurrence exactImport=c(-1," 数学Ⅰ "," 东１ ",0),exactZju=c(-2,"数学I","东1",0),exactSystem=c(42,"数学i","东1",0);
check(filter(exactImport,exactZju,"all",Map.of()).size()==1,"default merges exact normalized title and location");
check(filter(exactImport,exactZju,"all",Map.of()).get(0)==exactZju,"default stable representative prefers ZJU");
check(filter(exactZju,exactImport,"imported",Map.of()).get(0)==exactImport,"present primary wins exact pair");
check(filter(exactSystem,exactImport,"zju",Map.of()).get(0)==exactImport,"absent primary uses stable fallback");
CalendarOccurrence lowId=new CalendarOccurrence(1,0,3600000,"数学i",false,"东1","system",null,42,"");
check(filter(exactSystem,lowId,"all",Map.of()).get(0)==lowId,"same calendar stable lowest event id");
check(filter(c(10,"数学i","东1",0),exactSystem,"all",Map.of()).get(0).calendarId==10,"stable lowest system calendar id");
check(filter(exactImport,exactZju,"all",Map.of(CourseSourceMatching.pairKey(exactImport,exactZju),"reject")).size()==2,"reject overrides exact default");
for(List<CalendarOccurrence> order:List.of(List.of(exactImport,exactZju,exactSystem),List.of(exactSystem,exactImport,exactZju),List.of(exactZju,exactSystem,exactImport))){
 List<CalendarOccurrence> merged=CourseSourceMatching.filter(order,"all",Map.of());
 check(merged.size()==1&&merged.get(0)==exactZju,"three-source representative independent of order");
 merged=CourseSourceMatching.filter(order,"all",Map.of(CourseSourceMatching.pairKey(exactImport,exactZju),"reject"));
 check(merged.size()==2&&merged.contains(exactImport)&&merged.contains(exactZju),"third source cannot bypass rejected pair");
}
check(!CourseSourceMatching.exact(a,b),"exact title does not strip teacher suffix");
check(filter(a,b,"all",Map.of()).size()==2,"near match stays separate without primary");
check(filter(exactImport,c(42,"数学i","",0),"all",Map.of()).size()==2,"missing and nonempty location stay distinct");
check(filter(c(-1,"数学1班","",0),c(-2,"数学2班","",0),"all",Map.of()).size()==2,"exact retains group differences");
check(filter(c(-1,"数学甲","",0),c(-2,"数学乙","",0),"all",Map.of()).size()==2,"exact retains stage differences");
check(filter(exactImport,c(42,"数学i","东1",1),"all",Map.of()).size()==2,"exact requires same start and end");
CalendarOccurrence allDay=new CalendarOccurrence(71,0,3600000,"数学i",true,"东1","system",null,42,"");
check(filter(exactImport,allDay,"all",Map.of()).size()==2,"all-day and timed stay distinct");
CalendarOccurrence allDayTwin=new CalendarOccurrence(72,0,3600000,"数学i",true,"东1","system",null,43,"");
check(filter(allDay,allDayTwin,"all",Map.of()).size()==1,"exact all-day occurrences merge");
java.time.ZoneId zone=java.time.ZoneId.of("Asia/Shanghai");
long day=java.time.LocalDate.of(2026,10,19).atStartOfDay(zone).toInstant().toEpochMilli();
CalendarOccurrence first=new CalendarOccurrence(-101,day+805*60000L,day+850*60000L,"线性代数（甲）",false,"东2-203","折姜星辰",null,-2,"","第6节");
CalendarOccurrence second=new CalendarOccurrence(-102,day+855*60000L,day+950*60000L,first.title,false,first.location,first.calendarName,null,-2,"","第7-8节");
List<CalendarOccurrence> blocks=CourseSourceMatching.mergeConsecutive(List.of(second,first),List.of(first,second),zone);
check(blocks.size()==1&&blocks.get(0).startMillis==first.startMillis&&blocks.get(0).endMillis==second.endMillis,"13:25 to 15:50 forms one three-period course");
check(blocks.get(0).eventId==first.eventId&&blocks.get(0).description.contains("第6节")&&blocks.get(0).description.contains("第7-8节"),"stable first identity and all notes preserved");
check(first.endMillis==day+850*60000L&&second.startMillis==day+855*60000L,"raw source objects remain unchanged");
CalendarOccurrence distant=new CalendarOccurrence(-103,second.endMillis+55*60000L,second.endMillis+100*60000L,first.title,false,first.location,first.calendarName,null,-2,"");
check(CourseSourceMatching.mergeConsecutive(List.of(first,second,distant),List.of(),zone).size()==2,"missing period does not bridge");
CalendarOccurrence differentRoom=new CalendarOccurrence(-104,second.startMillis,second.endMillis,first.title,false,"东2-204",first.calendarName,null,-2,"");
check(CourseSourceMatching.mergeConsecutive(List.of(first,differentRoom),List.of(),zone).size()==2,"different rooms remain separate");
CalendarOccurrence differentTitle=new CalendarOccurrence(-105,second.startMillis,second.endMillis,"线性代数（乙）",false,first.location,first.calendarName,null,-2,"");
check(CourseSourceMatching.mergeConsecutive(List.of(first,differentTitle),List.of(),zone).size()==2,"different course stages remain separate");
CalendarOccurrence otherSource=new CalendarOccurrence(-106,second.startMillis,second.endMillis,first.title,false,first.location,"导入课表",null,-1,"");
check(CourseSourceMatching.mergeConsecutive(List.of(first,otherSource),List.of(),zone).size()==2,"source choice happens before joining, never splice sources");
CalendarOccurrence meeting1=new CalendarOccurrence(301,first.startMillis,first.endMillis,"会议",false,"会议室","工作",null,10,"");
CalendarOccurrence meeting2=new CalendarOccurrence(302,second.startMillis,second.endMillis,"会议",false,"会议室","工作",null,10,"");
check(CourseSourceMatching.mergeConsecutive(List.of(meeting1,meeting2),List.of(),zone).size()==2,"ordinary appointments do not merge");
CalendarOccurrence system1=new CalendarOccurrence(401,first.startMillis,first.endMillis,first.title,false,first.location,"学校",null,11,"");
CalendarOccurrence system2=new CalendarOccurrence(402,second.startMillis,second.endMillis,first.title,false,first.location,"学校",null,11,"");
check(CourseSourceMatching.mergeConsecutive(List.of(system1,system2),List.of(first,second),zone).size()==1,"recognized system course mirror also joins");
System.out.println("Course matching: "+count+" passed");
}}
''', encoding='utf-8')
src=root/'app/src/main/java/com/donglan/chrona'
java=Path(os.environ.get('JAVA_HOME','D:/Minecraft/java21'))/'bin'
subprocess.run([str(java/'javac.exe'),'--release','17','-encoding','UTF-8','-d',str(out),
    str(test),str(src/'CourseSourceMatching.java'),str(src/'calendar/CalendarOccurrence.java'),str(src/'calendar/AllDayDates.java')],check=True)
subprocess.run([str(java/'java.exe'),'-cp',str(out),'com.donglan.chrona.CourseMatchingCheck'],check=True)
