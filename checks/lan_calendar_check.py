"""Production calendar/data and control state checks. No browser, screenshots or device."""
from pathlib import Path
import os
import subprocess

ROOT=Path(__file__).resolve().parents[1]
SRC=ROOT/'app/src/main/java/com/donglan/chrona'
OUT=ROOT/'build/lan-calendar-code/checks'
OUT.mkdir(parents=True,exist_ok=True)
JAVA=Path(os.environ.get('JAVA_HOME','D:/Minecraft/java21'))/'bin'
JSON=ROOT/'build/ai-checks/json.jar'
if not JSON.is_file():
    raise RuntimeError('Existing build/ai-checks/json.jar required; no downloads')
driver=r'''
package com.donglan.chrona;
import java.time.*;import java.util.*;import org.json.*;
public final class LanCalendarDataCheck {
 static int n;static final ZoneId Z=ZoneId.of("Asia/Shanghai");static final LocalDate F=LocalDate.of(2026,10,26);
 static void ok(boolean v,String m){n++;if(!v)throw new AssertionError(m);}
 static long t(String s){return LocalDateTime.parse(s).atZone(Z).toInstant().toEpochMilli();}
 static AgendaEntry e(long id,String a,String b,boolean all){return new AgendaEntry(id,1,t(a),t(b),"Calculus "+id,"North 111","review detail","source",false,all,true);}
 static JSONObject p(List<AgendaEntry> e,String view,boolean partial,boolean unavailable)throws Exception{return LanCalendarData.project(e,F,F.plusDays(view.equals("day")?1:7),Z,view,"",partial,unavailable);}
 static JSONArray blocks(JSONObject d,int i)throws Exception{return d.getJSONObject("timeline").getJSONArray("days").getJSONObject(i).getJSONArray("blocks");}
 public static void main(String[]args)throws Exception{
  AgendaEntry a=e(1,"2026-10-26T08:00","2026-10-26T09:00",false),b=e(2,"2026-10-26T08:30","2026-10-26T09:30",false),c=e(3,"2026-10-26T10:00","2026-10-26T11:00",false),all=e(4,"2026-10-26T00:00","2026-10-29T00:00",true);
  List<AgendaEntry> input=List.of(a,b,c,all);JSONObject data=p(input,"week",false,false);JSONArray days=data.getJSONObject("timeline").getJSONArray("days");
  ok(data.getJSONArray("items").length()==4,"occurrences retained");ok(data.getJSONObject("timeline").getBoolean("compressMorning"),"all-day does not occupy timed quiet hours");
  ok(blocks(data,0).getJSONObject(0).getInt("lanes")==2,"native overlap lanes");ok(blocks(data,0).getJSONObject(1).getInt("lane")==1,"second lane");ok(blocks(data,0).getJSONObject(2).getInt("lanes")==1,"group resets");ok(blocks(data,0).getJSONObject(0).getInt("index")==0,"identity maps to original item");
  for(int i=0;i<3;i++)ok(days.getJSONObject(i).getJSONArray("allDay").getInt(0)==3,"all-day overlap identity");ok(days.getJSONObject(3).getJSONArray("allDay").length()==0,"exclusive end");
  ok(!p(input,"week",true,false).getJSONObject("timeline").getBoolean("compressMorning"),"partial snapshot");ok(!p(input,"week",false,true).getJSONObject("timeline").getBoolean("compressMorning"),"unavailable source");ok(!p(input,"day",false,false).getJSONObject("timeline").getBoolean("compressMorning"),"native uncompressed day");
  AgendaEntry cross=e(5,"2026-10-25T23:30","2026-10-26T00:30",false);JSONObject early=p(List.of(cross),"week",false,false);ok(!early.getJSONObject("timeline").getBoolean("compressMorning"),"cross-midnight quiet occupancy");ok(blocks(early,0).getJSONObject(0).getDouble("startMinute")==0,"clip day start");ok(blocks(early,0).getJSONObject(0).getDouble("endMinute")==30,"clip day end");
  AgendaEntry midnight=e(6,"2026-10-26T23:45","2026-10-27T00:15",false);JSONObject split=p(List.of(midnight),"week",false,false);ok(blocks(split,0).getJSONObject(0).getDouble("endMinute")==1440,"first-day clipping");ok(blocks(split,1).getJSONObject(0).getDouble("startMinute")==0,"next-day clipping");
  for(String q:List.of("calCULus","NORTH","detail","SOURCE"))ok(LanCalendarData.matching(input,q).size()==4,"native search "+q);ok(LanCalendarData.matching(input,"absent").isEmpty(),"no invented matches");
  JSONObject filtered=LanCalendarData.project(List.of(cross,a),F,F.plusDays(7),Z,"week","Calculus 1",false,false);ok(filtered.getJSONObject("timeline").getBoolean("compressMorning"),"query and compression use same entries");
  AgendaEntry ext=new AgendaEntry(Long.MIN_VALUE,0,a.start,a.end,"official","room","note","calendar",true,false,true,0xff336699);JSONObject id=p(List.of(ext),"day",false,false).getJSONArray("items").getJSONObject(0);ok(id.getString("id").equals(Long.toString(Long.MIN_VALUE)),"no lossy JS system ID");ok(id.getString("calendarColor").equals("#336699"),"source color survives browser theme override");ok(data.getJSONArray("items").getJSONObject(0).isNull("calendarColor"),"local color follows browser theme");
  AgendaEntry once=new AgendaEntry(8,7,a.start,a.start,"todo","","","拾时",false,false,false,null,"homework",10,a.end,123L,0L);JSONObject original=p(List.of(once),"day",false,false).getJSONArray("items").getJSONObject(0);ok(original.getLong("occurrenceAt")==123,"original repeat identity");ok(original.getLong("due")==a.end,"official due");ok(original.getLong("overdueAt")==0,"zero is not null");ok(original.getBoolean("needsConfirmation"),"review state");ok(blocks(p(List.of(once),"day",false,false),0).getJSONObject(0).getDouble("endMinute")==495,"point event native 15-minute visual interval");
  ok(original.getLong("finish")==a.start+1,"point finish uses the exact native membership boundary");
  ok(input.size()==4&&a.start==t("2026-10-26T08:00"),"input not mutated");for(String view:List.of("year","month","agenda"))ok(!p(input,view,false,false).has("timeline"),"only time views have lanes");
  try{p(input,"bad",false,false);throw new AssertionError();}catch(IllegalArgumentException expected){n++;}try{LanCalendarData.matching(input,"x".repeat(257));throw new AssertionError();}catch(IllegalArgumentException expected){n++;}try{LanCalendarData.project(input,F,F.plusDays(2),Z,"week","",false,false);throw new AssertionError();}catch(IllegalArgumentException expected){n++;}
  System.out.println("PASS "+n+" production calendar projection/layout checks (not visual acceptance)");
 }
}
'''
file=OUT/'LanCalendarDataCheck.java'
file.write_text(driver,encoding='utf-8')
files=[SRC/'data/EventCategory.java',*[SRC/name for name in ('AgendaEntry.java','CalendarTimelineLayout.java','WeekTimelineScale.java','LanCalendarData.java')],file]
subprocess.run([str(JAVA/'javac.exe'),'--release','17','-encoding','UTF-8','-cp',str(JSON),'-d',str(OUT),*map(str,files)],check=True)
subprocess.run([str(JAVA/'java.exe'),'-cp',str(OUT)+os.pathsep+str(JSON),'com.donglan.chrona.LanCalendarDataCheck'],check=True)
script=r'''
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync('app/src/main/assets/lan/app.js','utf8'),calendar=fs.readFileSync('app/src/main/assets/lan/calendar.js','utf8'),html=fs.readFileSync('app/src/main/assets/lan/index.html','utf8'),css=fs.readFileSync('app/src/main/assets/lan/style.css','utf8');
function section(start,end){const a=source.indexOf(start),b=source.indexOf(end,a);assert(a>=0&&b>a,start);return source.slice(a,b);}
let n=0;function eq(a,b,m){assert.deepEqual(a,b,m);n++;}
const context={Intl,Date,Map,Set,Number,Math,session:{zone:'Asia/Shanghai'},window:{addEventListener(){}},document:{querySelector(){return null;}}};vm.createContext(context);vm.runInContext(section('const calendarDateFormats=','// AgendaRepository')+section('function monthAfter(','function presetWindow(')+calendar,context);
for(const v of [null,undefined,'','2026-99-90','2025-02-29','2026-02-31','0000-01-01'])eq(context.validCalendarDate(v),false,'reject invalid date without throwing');eq(context.validCalendarDate('2024-02-29'),true);eq(context.phoneDate(Date.parse('2026-10-27T20:00:00Z')),'2026-10-28');
for(const [view,want]of [['year',['2024-01-01','2025-01-01']],['month',['2024-01-29','2024-03-11']],['week',['2024-02-26','2024-03-04']],['day',['2024-02-29','2024-03-01']],['agenda',['2024-01-29','2024-03-11']]])eq(Array.from(context.calendarWindow('2024-02-29',view)),want,'native date window '+view);
eq(context.calendarShiftDate('2024-02-29','year',1),'2025-02-28');eq(context.calendarShiftDate('2026-10-31','month',1),'2026-11-30');eq(context.calendarShiftDate('2026-12-31','month',1),'2027-01-31');eq(context.calendarShiftDate('2026-10-26','week',-1),'2026-10-19');
for(const compressed of [false,true])for(const zoom of [.1,.6,1,2])for(const minute of [0,1,479.5,480,600,1439,1440]){const model={hourHeight:72,minimumBandHeight:32,compressMorning:compressed};assert(Math.abs(context.calendarMinuteAt(context.calendarY(minute,model,zoom),model,zoom)-minute)<1e-8);n++;}
vm.runInContext(section('function agendaDayEntries(','const pageOrder='),context);const begin=context.phoneDayStart('2026-10-27');eq(context.agendaDayEntries([{start:begin-30000,end:begin-30000,finish:begin+30000}],'2026-10-27').length,1,'native point finish crosses midnight');eq(context.agendaDayEntries([{start:begin-30000,end:begin-30000,finish:begin-29999}],'2026-10-27').length,0,'task point has the same native one-ms boundary');
let live=0;const highlight={style:{}},compact={hidden:false,firstElementChild:{textContent:''}},buttons=[0,1,2,3].map(i=>({textContent:['待处理','待安排','历史','全部'][i],offsetWidth:100,offsetLeft:6+i*100})),bar={querySelectorAll:()=>buttons,querySelector:q=>q.includes('aria-pressed')?buttons[live]:highlight,classList:{toggle(){}}},host={isConnected:true,clientWidth:414,querySelector:q=>q==='.selection-bar'?bar:compact};
context.getComputedStyle=()=>({font:'14px sans-serif',paddingLeft:'12',paddingRight:'12'});context.document.createElement=()=>({getContext:()=>({measureText:()=>({width:30})})});vm.runInContext(section('function selectionBar(','function monthAfter('),context);
for(live=0;live<4;live++){context.positionSelectionBar(host);eq(highlight.style.width,'100px');eq(highlight.style.transform,`translateX(${6+live*100}px)`);eq(compact.firstElementChild.textContent,buttons[live].textContent);}
vm.runInContext(section('function softDockUpper(','function snapDock('),context);context.dockOutwardWidth=.75;context.dockInnerInset=6;context.dockNav={offsetWidth:330,clientHeight:60,style:{}};context.dockHighlight={style:{}};
for(const scale of [1,1.16,1.5]){context.dockState={w:100,s:scale,stretch:1,p:165,bar:1.1};context.drawDock();eq(Number.parseFloat(context.dockHighlight.style.height),48*scale);eq(Number.parseFloat(context.dockHighlight.style.top),(60-48*scale)/2);assert(!context.dockHighlight.style.transform.includes('scale'));n++;}
eq(html.includes('</nav></div><button id="capture"'),true,'plus is outside expanding navigation host');assert(!css.match(/#capture\.is-hidden\{[^}]*width/));n++;
const server=fs.readFileSync('app/src/main/java/com/donglan/chrona/LanWebServer.java','utf8');assert(server.indexOf('if (!security.authenticated')<server.indexOf('return weekImage(request)'));n++;assert(server.includes('CalendarSourcePreferences.setIncludesSystem(context,included)')&&server.includes('HomeTimelinePreferences.setIncludesSystemCalendar(context,included)'));n++;assert(server.includes('WeekAgendaImage.write(context.getCacheDir()')&&server.includes('finally{image.delete();}'));n++;
console.log('PASS '+n+' calendar date/scale/control-state/wiring checks (not visual acceptance)');
'''
subprocess.run(['D:/NodeJs/node.exe','-e',script],cwd=ROOT,check=True)
