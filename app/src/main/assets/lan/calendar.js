'use strict';
// Calendar data and lanes come from the phone's AgendaRepository/CalendarTimelineLayout.
function validCalendarDate(value){if(typeof value!=='string'||!/^\d{4}-\d{2}-\d{2}$/.test(value)||value<'0001-01-01'||value>'9998-12-31')return false;const date=new Date(value+'T00:00:00Z');return Number.isFinite(date.getTime())&&date.toISOString().slice(0,10)===value;}
function calendarWindow(date,view){
 if(view==='year')return[date.slice(0,4)+'-01-01',String(Number(date.slice(0,4))+1).padStart(4,'0')+'-01-01'];
 if(view==='day')return[date,addCivilDays(date,1)];
 const first=view==='week'?date:date.slice(0,8)+'01',from=addCivilDays(first,-(civilWeekday(first)+6)%7);
 return[from,addCivilDays(from,view==='week'?7:42)];
}
function calendarShiftDate(date,view,direction){
 if(view==='day'||view==='week')return addCivilDays(date,direction*(view==='day'?1:7));
 const first=monthAfter(date,direction*(view==='year'?12:1)),last=addCivilDays(monthAfter(first),-1);
 return first.slice(0,8)+String(Math.min(Number(date.slice(8)),Number(last.slice(8)))).padStart(2,'0');
}
function calendarSetDate(date,view=calendarView,direction=0){
 if(!validCalendarDate(date))throw new Error('请选择有效日期');
 rememberCalendarScroll();calendarDate=date;calendarView=view;return render({local:true,direction});
}
function calendarShift(direction){return calendarSetDate(calendarShiftDate(calendarDate,calendarView,direction),calendarView,direction);}
function calendarPickDate(){showDialog('选择日期',(body,owner)=>{const input=field(body,'日期',calendarDate,'date','calendar-picked-date');input.min='0001-01-01';input.max='9998-12-31';const buttons=node('div','actions');buttons.append(action('取消',()=>closeDialog(false,owner)),action('确认',()=>{if(!input.reportValidity())return;const date=input.value;if(!validCalendarDate(date))throw new Error('请选择有效日期');closeDialog(false,owner);return calendarSetDate(date);},'primary'));body.append(buttons);});}
function calendarInterval(){
 const current=action('',calendarPickDate,'interval-current'),label=node('span','interval-value');current.setAttribute('aria-label','选择日期');current.append(label);
 const previous=iconAction('chevron-left','上一段',()=>calendarShift(-1)),next=iconAction('chevron-right','下一段',()=>calendarShift(1)),bar=actionGroup([previous,current,next]);bar.classList.add('date-interval');
 let gesture=null,suppress=false;
 current.addEventListener('pointerdown',event=>{suppress=false;if(event.button!==0)return;gesture={id:event.pointerId,x:event.clientX,y:event.clientY,drag:false};current.setPointerCapture(event.pointerId);});
 current.addEventListener('pointermove',event=>{if(!gesture||gesture.id!==event.pointerId)return;const dx=event.clientX-gesture.x,dy=event.clientY-gesture.y;if(!gesture.drag&&Math.abs(dx)>8&&Math.abs(dx)>Math.abs(dy)*1.5)gesture.drag=true;if(gesture.drag){suppress=true;label.style.transform=`translateX(${dx*.35}px)`;}});
 const finish=(event,cancel)=>{if(!gesture||gesture.id!==event.pointerId)return;const dx=event.clientX-gesture.x,drag=gesture.drag;gesture=null;label.style.transform='';if(drag&&!cancel&&Math.abs(dx)>40)guard(()=>calendarShift(dx>0?-1:1));};
 current.addEventListener('pointerup',event=>finish(event,false));current.addEventListener('pointercancel',event=>finish(event,true));current.addEventListener('click',event=>{if(suppress&&event.detail!==0){suppress=false;event.preventDefault();event.stopImmediatePropagation();}},true);
 return bar;
}
function calendarHeader(){
 const header=node('div','page-heading calendar-heading'),back=iconAction('arrow-left','返回日程',()=>navigate('schedule')),form=node('form','calendar-search'),input=node('input');
 input.type='search';input.value=calendarSearchDraft;input.maxLength=256;input.placeholder='搜索日历';input.setAttribute('aria-label','搜索日历');input.addEventListener('input',()=>calendarSearchDraft=input.value);form.append(input);form.addEventListener('submit',event=>{event.preventDefault();calendarSearchDraft=input.value;calendarSearch=calendarSearchDraft.trim();guard(()=>render({local:true}));});
 const source=iconAction('calendar-days','包含系统日程',async()=>{const included=!session.calendarSystemIncluded;await api('/api/preferences',{scope:'calendar',systemCalendarIncluded:included});session.calendarSystemIncluded=included;return render({local:true});},!!session.calendarSystemIncluded);source.classList.add('system-toggle');source.dataset.calendarSystem='';
 const download=iconAction('download','导出周日程为图片',calendarExport);download.classList.add('calendar-export');download.classList.toggle('conditional-hidden',calendarView!=='week');download.inert=calendarView!=='week';download.setAttribute('aria-hidden',String(calendarView!=='week'));
 header.append(node('h1','sr-only','日历'),back,form,actionGroup([source,download,iconAction('plus','记录新日程',capture)]));return preserve(header,'calendar-header',0);
}
function calendarTools(){const tools=node('div','calendar-tools'),row=node('div','calendar-navigation');row.append(calendarInterval(),action('今天',()=>calendarSetDate(phoneDate())));tools.append(row,selectionBar('日历视图',calendarViews,calendarView,value=>calendarSetDate(calendarDate,value,Math.sign(calendarViews.findIndex(([key])=>key===value)-calendarViews.findIndex(([key])=>key===calendarView)))));return preserve(tools,'calendar-tools',0);}
function syncCalendarTools(){
 if(route!=='calendar')return;const label=$('.interval-value'),[from,until]=calendarWindow(calendarDate,calendarView),text=calendarView==='week'?from+' — '+addCivilDays(until,-1):calendarView==='year'?calendarDate.slice(0,4)+'年':calendarView==='day'?calendarDate:calendarDate.slice(0,7);
 if(label&&label.textContent!==text){label.textContent=text;motion(label,[{opacity:.4,transform:'translateX(6px)'},{opacity:1,transform:'translateX(0)'}],180);}
 document.querySelectorAll('.calendar-tools [data-choice]').forEach(button=>button.setAttribute('aria-pressed',String(button.dataset.choice===calendarView)));
 const source=$('[data-calendar-system]');source?.setAttribute('aria-pressed',String(!!session.calendarSystemIncluded));
 const download=$('.calendar-export'),shown=calendarView==='week',body=$('.calendar-body');if(download){download.classList.toggle('conditional-hidden',!shown);download.inert=!shown;download.setAttribute('aria-hidden',String(!shown));download.disabled=calendarExportBusy||body?.dataset.incomplete==='true';}
 layoutSelectionBars();body?._relayout?.();history.replaceState(null,'',`#calendar?${new URLSearchParams({view:calendarView,date:calendarDate})}`);
}
function rememberCalendarScroll(){const body=$('.calendar-body');if(!body)return;calendarPositions.set(body.dataset.window,{x:body.querySelector('.calendar-timeline-scroll')?.scrollLeft||0});while(calendarPositions.size>12)calendarPositions.delete(calendarPositions.keys().next().value);}
function restoreCalendarScroll(){const body=$('.calendar-body'),scroll=body?.querySelector('.calendar-timeline-scroll');if(scroll)scroll.scrollLeft=calendarPositions.get(body.dataset.window)?.x||0;}
function calendarWhen(item){
 if(item.allDay){const start=phoneDate(item.start),end=phoneDate(Math.max(item.start,item.end-1));return start+(end!==start?' — '+end:'')+' · 全天';}
 if(item.end===item.start)return(item.due!=null?'截止 · ':taskLike(item.category)?categories[item.category]+' · ':'')+fmt(item.start,false,true);
 return fmt(item.start,false,true)+' — '+fmt(item.end,false,phoneDate(item.start)!==phoneDate(item.end));
}
function openCalendarItem(item){return item.system?showReadOnly(item):navigate(taskRoute(item));}
function setCalendarColor(element,item){element.style.setProperty('--event-color',/^#[0-9a-f]{6}$/i.test(item.calendarColor||'')?`color-mix(in srgb,${item.calendarColor} 65%,var(--primary))`:'var(--primary)');}
function calendarEntry(item,day){
 const button=action('',()=>openCalendarItem(item),'calendar-entry');button.dataset.key=`calendar-${item.key}-${day}`;setCalendarColor(button,item);button.append(node('strong','',item.title),node('span','subtle',calendarWhen(item)));if(item.location)button.append(node('span','subtle',item.location));button.append(node('span','calendar-source',item.source+(item.needsConfirmation?' · 待确认':'')));return button;
}
function calendarDayList(items,day){const list=node('div','calendar-day-list'),entries=agendaDayEntries(items,day);for(const item of entries)list.append(calendarEntry(item,day));if(!entries.length)empty(list,'当天暂无日程');return list;}
function calendarDayCount(items,day){return agendaDayEntries(items,day).length;}
function calendarMonthGrid(items,month,mini=false){
 const grid=node('div',mini?'mini-month':'calendar-month-grid'),first=month.slice(0,8)+'01',start=addCivilDays(first,-(civilWeekday(first)+6)%7),today=phoneDate();
 for(const label of ['一','二','三','四','五','六','日'])grid.append(node('span',mini?'mini-weekday':'calendar-weekday',label));
 if(mini){for(let n=0;n<(civilWeekday(first)+6)%7;n++)grid.append(node('span'));}
 const days=mini?Number(addCivilDays(monthAfter(first),-1).slice(8)):42;
 for(let n=0;n<days;n++){
  const day=addCivilDays(mini?first:start,n),count=calendarDayCount(items,day),own=day.slice(0,7)===month.slice(0,7),button=action('',()=>calendarSetDate(day,mini?'month':'month'),'calendar-date'+(mini?' mini-date':''));
  button.dataset.date=day;button.classList.toggle('adjacent-month',!own);button.classList.toggle('today',day===today);button.classList.toggle('selected-date',!mini&&day===calendarDate);button.classList.toggle('has-events',count>0);button.setAttribute('aria-label',day+'，'+count+'项日程');button.setAttribute('aria-pressed',String(!mini&&day===calendarDate));if(day===today)button.setAttribute('aria-current','date');button.append(node('span','',String(Number(day.slice(8)))));
  if(count){const mark=node('span','calendar-count',mini?'':count>99?'99+':String(count));mark.dataset.density=count<=2?'light':count<=5?'medium':'heavy';button.append(mark);}grid.append(button);
 }return grid;
}
function calendarY(minute,model,zoom=1){const hour=model.hourHeight*zoom,band=Math.max(model.minimumBandHeight,hour/2);return !model.compressMorning?minute/60*hour:minute<=480?minute/480*band:band+(minute-480)/60*hour;}
function calendarMinuteAt(y,model,zoom=1){const hour=model.hourHeight*zoom,band=Math.max(model.minimumBandHeight,hour/2);return !model.compressMorning?y/hour*60:y<band?y/band*480:480+(y-band)/hour*60;}
function calendarClockMinute(now=Date.now()){const parts=new Intl.DateTimeFormat('en-GB',{timeZone:session.zone,hour:'2-digit',minute:'2-digit',second:'2-digit',hourCycle:'h23'}).formatToParts(now),value=type=>Number(parts.find(p=>p.type===type).value);return value('hour')*60+value('minute')+value('second')/60;}
function calendarTimeline(data,body){
 const model=data.timeline,days=model.days.length,viewport=node('div','calendar-timeline-scroll'),track=node('div','calendar-timeline'),head=node('div','calendar-time-row calendar-time-head'),rows=node('div','calendar-time-row calendar-all-day'),grid=node('div','calendar-time-grid'),columns=node('div','calendar-time-days'),bindings=[];
 track.style.setProperty('--calendar-days',days);head.append(node('span'));rows.append(node('span','subtle','全天'));
 for(const day of model.days){const dateButton=action(new Intl.DateTimeFormat('zh-CN',{timeZone:'UTC',weekday:'short'}).format(new Date(day.date+'T00:00:00Z'))+'\n'+Number(day.date.slice(8)),()=>calendarSetDate(day.date,'day'),'calendar-time-date');dateButton.classList.toggle('today',day.date===phoneDate());head.append(dateButton);
  const all=node('div','calendar-all-day-column');for(const index of day.allDay.slice(0,3)){const item=data.items[index],button=action(item.title,()=>openCalendarItem(item),'calendar-all-day-item');setCalendarColor(button,item);all.append(button);}if(day.allDay.length>3)all.append(action('+'+(day.allDay.length-3),()=>showDialog(day.date,content=>day.allDay.forEach(index=>content.append(calendarEntry(data.items[index],day.date))))));rows.append(all);
  const column=node('div','calendar-time-day');for(const block of day.blocks){const item=data.items[block.index],button=action('',()=>openCalendarItem(item),'calendar-time-event');setCalendarColor(button,item);button.style.left=(block.lane/block.lanes*100)+'%';button.style.width=`calc(${100/block.lanes}% - 4px)`;button.title=[item.title,calendarWhen(item),item.location].filter(Boolean).join('\n');button.append(node('strong','',item.title),node('span','calendar-event-time',calendarWhen(item)));if(item.location)button.append(node('span','calendar-event-location',item.location));button.classList.toggle('unconfirmed',item.needsConfirmation);column.append(button);bindings.push(zoom=>{const top=calendarY(block.startMinute,model,zoom),height=Math.max(20,calendarY(block.endMinute,model,zoom)-top-2);button.style.top=top+'px';button.style.height=height+'px';button.classList.toggle('compact-event',height<44);});}
  if(day.date===phoneDate()){const now=node('span','calendar-now');column.append(now);bindings.push(zoom=>now.style.top=calendarY(calendarClockMinute(),model,zoom)+'px');}columns.append(column);
 }
 for(let hour=0;hour<=24;hour++){if(model.compressMorning&&hour>0&&hour<8)continue;const row=node('div','calendar-hour');row.append(node('span','',model.compressMorning&&hour===0?'00–08':String(hour).padStart(2,'0')+':00'));grid.append(row);bindings.push(zoom=>row.style.top=calendarY(hour*60,model,zoom)+'px');}
 grid.append(columns);track.append(head);if(model.days.some(day=>day.allDay.length))track.append(rows);track.append(grid);viewport.append(track);viewport.tabIndex=0;viewport.setAttribute('aria-label',days===7?'周日程时间轴，可双指缩放或按加减键缩放':'日程时间轴');
 const layout=()=>{const available=Math.max(240,viewport.clientWidth),base=Math.max(available,days===7?670:available),minimum=Math.max(.1,(available-40)/(base-40)),zoom=days===7?Math.max(minimum,Math.min(2,calendarZoom)):1;if(days===7)calendarZoom=zoom;track.style.width=(40+(base-40)*zoom)+'px';grid.style.height=calendarY(1440,model,zoom)+'px';for(const bind of bindings)bind(zoom);};body._relayout=layout;
 const zoomAt=(value,x,y)=>{if(days!==7)return;const before=calendarZoom,oldX=viewport.scrollLeft+x,gutter=40,minute=calendarMinuteAt(y,model,before);calendarZoom=value;layout();viewport.scrollLeft=(oldX<=gutter?oldX:gutter+(oldX-gutter)*(calendarZoom/before))-x;window.scrollBy(0,calendarY(minute,model,calendarZoom)-y);};
 viewport.addEventListener('wheel',event=>{if(days===7&&event.ctrlKey){event.preventDefault();zoomAt(calendarZoom*Math.exp(-event.deltaY*.002),event.clientX-viewport.getBoundingClientRect().left,event.clientY-grid.getBoundingClientRect().top);}},{passive:false});
 viewport.addEventListener('keydown',event=>{if(event.target!==viewport)return;if(['+','=','-'].includes(event.key)){event.preventDefault();zoomAt(calendarZoom*(event.key==='-'?.9:1.1),viewport.clientWidth/2,0);}});
 let pinch=null,single=null,suppress=false;viewport.addEventListener('pointerdown',()=>suppress=false);
 viewport.addEventListener('touchstart',event=>{if(event.touches.length===2&&days===7){event.preventDefault();const[a,b]=event.touches;pinch={distance:Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY),zoom:calendarZoom};single=null;suppress=true;}else if(event.touches.length===1){const t=event.touches[0];single={x:t.clientX,y:t.clientY,left:viewport.scrollLeft<=1,right:viewport.scrollLeft+viewport.clientWidth>=viewport.scrollWidth-1};}},{passive:false});
 viewport.addEventListener('touchmove',event=>{if(pinch&&event.touches.length===2){event.preventDefault();const[a,b]=event.touches;zoomAt(pinch.zoom*Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY)/Math.max(1,pinch.distance),(a.clientX+b.clientX)/2-viewport.getBoundingClientRect().left,(a.clientY+b.clientY)/2-grid.getBoundingClientRect().top);}},{passive:false});
 viewport.addEventListener('touchend',event=>{if(event.touches.length<2)pinch=null;if(!event.touches.length&&single&&days===7){const t=event.changedTouches[0],dx=t.clientX-single.x,dy=t.clientY-single.y;if(Math.abs(dx)>80&&Math.abs(dx)>Math.abs(dy)*1.5&&(dx>0&&single.left||dx<0&&single.right)){suppress=true;guard(()=>calendarShift(dx>0?-1:1));}single=null;}});
 viewport.addEventListener('touchcancel',()=>{pinch=null;single=null;suppress=false;});viewport.addEventListener('click',event=>{if(suppress&&event.detail!==0){suppress=false;event.preventDefault();event.stopImmediatePropagation();}},true);return viewport;
}
async function calendarPage(ticket){
 if(!calendarDate)calendarDate=phoneDate();const anchor=calendarDate,view=calendarView,[from,until]=calendarWindow(anchor,view),data=await api('/api/agenda?'+new URLSearchParams({from,until,view,search:calendarSearch}));if(ticket!==sequence)return[];revision=data.revision;
 const body=node('section','calendar-body');body.dataset.window=JSON.stringify([view,from,until,calendarSearch,!!session.calendarSystemIncluded]);body.dataset.incomplete=String(!!(data.truncated||data.systemUnavailable));
 if(data.systemUnavailable)body.append(node('p','calendar-notice','系统日程暂时无法读取'));if(data.truncated)body.append(node('p','calendar-notice','日程数量较多，请缩小日期范围'));
 if(view==='year'){const grid=node('div','year-grid');for(let month=1;month<=12;month++){const first=anchor.slice(0,4)+'-'+String(month).padStart(2,'0')+'-01',tile=node('section','year-month');tile.append(action(month+'月',()=>calendarSetDate(first,'month'),'year-month-title'),calendarMonthGrid(data.items,first,true));grid.append(tile);}body.append(grid);}
 else if(view==='month'){body.append(calendarMonthGrid(data.items,anchor),node('h2','calendar-selected-day',anchor),calendarDayList(data.items,anchor));}
 else if(view==='week'||view==='day')body.append(calendarTimeline(data,body));
 else{const list=node('div','calendar-agenda'),end=monthAfter(anchor);let shown=0;for(let day=anchor.slice(0,8)+'01';day<end;day=addCivilDays(day,1)){if(!calendarDayCount(data.items,day))continue;shown++;list.append(node('h2','calendar-selected-day',day),calendarDayList(data.items,day));}if(!shown)empty(list,'本月暂无日程');body.append(list);}
 return[calendarHeader(),calendarTools(),body];
}
function calendarExport(){if(calendarExportBusy)return;showDialog('导出周日程',(body,owner)=>{const label=node('label','checkbox'),check=node('input');check.type='checkbox';label.append(check,node('span','','附带日程索引'));const actions=node('div','actions');actions.append(action('取消',()=>closeDialog(false,owner)),action('导出',async()=>{const[from]=calendarWindow(calendarDate,'week'),query=new URLSearchParams({from,search:calendarSearch,index:String(check.checked)});calendarExportBusy=true;syncCalendarTools();closeDialog(false,owner);pending++;try{const response=await fetch('/api/agenda/week-image?'+query,{credentials:'same-origin',cache:'no-store'});if(response.status===401){disconnect();throw new Error('连接已失效，请重新连接');}if(!response.ok){const error=await response.json();throw new Error(error.error||'导出失败');}const url=URL.createObjectURL(await response.blob()),link=node('a');link.href=url;link.download='Chrona-周日程-'+from+'.png';document.body.append(link);link.click();link.remove();setTimeout(()=>URL.revokeObjectURL(url),1000);}finally{pending--;calendarExportBusy=false;syncCalendarTools();}},'primary'));body.append(label,actions);});}
window.addEventListener('resize',()=>document.querySelector('.calendar-body')?._relayout?.());
