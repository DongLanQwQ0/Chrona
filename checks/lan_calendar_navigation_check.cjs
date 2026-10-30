/* Functional browser check, without visual inspection.
   Production assets + mock phone data; no screenshots and no Android acceptance claims. */
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert');
const {chromium}=require('./lan_browser_cdp.cjs');
const root=path.resolve(__dirname,'../app/src/main/assets/lan'),requests=[],errors=[];
const files={'/':'index.html','/app.js':'app.js','/calendar.js':'calendar.js','/icons.js':'icons.js','/brand.svg':'brand.svg','/style.css':'style.css'};
const server=http.createServer((req,res)=>{const name=files[new URL(req.url,'http://localhost').pathname];if(!name)return res.writeHead(404).end();res.setHeader('Content-Type',name.endsWith('.js')?'text/javascript':name.endsWith('.css')?'text/css':name.endsWith('.svg')?'image/svg+xml':'text/html');res.end(fs.readFileSync(path.join(root,name)));});
const items=[{id:'1',key:'system:1',title:'闰日清晨',start:Date.parse('2024-02-29T08:00:00+08:00'),end:Date.parse('2024-02-29T09:00:00+08:00'),system:true,allDay:false,source:'日历',location:'教室'}, {id:'2',key:'system:2',title:'跨日讨论',start:Date.parse('2024-02-29T23:30:00+08:00'),end:Date.parse('2024-03-01T00:30:00+08:00'),system:true,allDay:false,source:'日历'}];
let included=false,failPreference=false,holdFrom=null,releaseHeld=null;
for(const item of items)item.finish=item.end;
async function mock(route){const request=route.request(),url=new URL(request.url());requests.push({path:url.pathname,query:Object.fromEntries(url.searchParams),body:request.postDataJSON()});let data;
 if(url.pathname==='/api/session')data={csrf:'calendar-test',revision:1,zone:'Asia/Shanghai',calendarAllowed:true,calendarReadable:true,calendarSystemIncluded:included,systemCalendarIncluded:true,palette:{background:'#f4f6f8',primary:'#008582'}};
 else if(url.pathname==='/api/home')data={revision:1,items:[],calendar:[],date:'2024-02-29',review:0,failed:0,todos:{total:0,todayDue:0,overdue:0,unscheduled:0,items:[]}};
 else if(url.pathname==='/api/preferences'){assert.equal(request.postDataJSON().scope,'calendar');if(failPreference){await route.fulfill({status:403,contentType:'application/json',body:JSON.stringify({error:'请在手机授权读取日历'})});return;}included=request.postDataJSON().systemCalendarIncluded;data={ok:true};}
 else if(url.pathname==='/api/agenda'){
  const q=Object.fromEntries(url.searchParams),span=(Date.parse(q.until)-Date.parse(q.from))/86400000;assert(span>0&&span<=366);
  if(q.from===holdFrom){holdFrom=null;await new Promise(resolve=>releaseHeld=resolve);}
  const filtered=items.filter(item=>!q.search||item.title.includes(q.search));data={revision:1,items:filtered,truncated:false,systemUnavailable:false};
  if(q.view==='day'||q.view==='week'){const days=[];for(let n=0;n<(q.view==='day'?1:7);n++){const start=Date.parse(q.from+'T00:00:00+08:00')+n*86400000,blocks=[];filtered.forEach((item,index)=>{if(item.start>=start+86400000||item.end<=start)return;blocks.push({index,startMinute:Math.max(0,(item.start-start)/60000),endMinute:Math.min(1440,(item.end-start)/60000),lane:0,lanes:1});});days.push({date:new Date(start+8*3600000).toISOString().slice(0,10),allDay:[],blocks});}data.timeline={days,compressMorning:false,hourHeight:72,minimumBandHeight:32};}
 }else if(url.pathname==='/api/task')data={revision:1,task:{id:7,text:'普通记录',source:'隔离数据',created:items[0].start,status:'ready'},candidates:[{id:3,taskId:7,title:'本地讨论',category:'event',start:items[0].start,end:items[0].end,zone:'Asia/Shanghai',needsConfirmation:false,location:'教室',description:'讨论内容'}],images:[],files:[],mergedTargets:[]};
 else if(url.pathname==='/api/agenda/week-image'){await route.fulfill({status:503,contentType:'application/json',body:JSON.stringify({error:'日程读取不完整，请稍后导出'})});return;}
 else throw new Error('Unhandled '+url.pathname);
 await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
}
async function idle(page){await page.evaluate(async()=>{while(pending)await new Promise(r=>setTimeout(r,15));await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));});}
async function view(page,type){await page.evaluate(async type=>{calendarDate='2024-02-29';calendarView=type;await navigate('calendar');},type);await idle(page);}
(async()=>{await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;try{
 browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});const page=await browser.newPage({viewport:{width:1280,height:900}});page.on('pageerror',error=>errors.push(String(error)));await page.route('**/api/**',mock);await page.cdp.send('Emulation.setTimezoneOverride',{timezoneId:'America/Los_Angeles'});await page.goto('http://127.0.0.1:'+server.address().port+'/');await page.getByRole('heading',{name:'首页',exact:true}).waitFor();await idle(page);
 await view(page,'year');assert.equal(await page.evaluate(()=>document.querySelectorAll('.mini-date').length),366);await page.getByRole('button',{name:'2月',exact:true}).click();await idle(page);assert.equal(await page.evaluate(()=>calendarView),'month');assert.equal(await page.evaluate(()=>document.querySelectorAll('.calendar-month-grid .calendar-date').length),42);
 await page.getByRole('button',{name:'2024-02-29，2项日程',exact:true}).click();await idle(page);assert.equal(await page.evaluate(()=>calendarView),'month');assert.equal(await page.evaluate(()=>document.querySelectorAll('.calendar-day-list .calendar-entry').length),2);
 await view(page,'week');assert.equal(await page.evaluate(()=>document.querySelectorAll('.calendar-time-event').length),3);
 await page.setViewportSize({width:414,height:896});
 assert(await page.evaluate(async()=>{const scroll=document.querySelector('.calendar-timeline-scroll');scroll.scrollLeft=140;const before=scroll.scrollLeft;await refreshSession();return before>0&&document.querySelector('.calendar-timeline-scroll').scrollLeft===before;}),'poll preserves horizontal timeline reading position');
 const zoomAnchor=await page.evaluate(()=>{const scroll=document.querySelector('.calendar-timeline-scroll'),track=scroll.firstElementChild,x=170,point=()=>{const grid=scroll.querySelector('.calendar-time-grid');return[(scroll.scrollLeft+x-40)/(track.offsetWidth-40),calendarMinuteAt(200-grid.getBoundingClientRect().top,{hourHeight:72,minimumBandHeight:32,compressMorning:false},calendarZoom)];};window.scrollTo(0,scroll.querySelector('.calendar-time-grid').getBoundingClientRect().top+scrollY+300);scroll.scrollLeft=100;const before=point();scroll.dispatchEvent(new WheelEvent('wheel',{ctrlKey:true,deltaY:-100,clientX:scroll.getBoundingClientRect().left+x,clientY:200,cancelable:true}));const after=point();return{before,after};});
 assert(Math.abs(zoomAnchor.before[0]-zoomAnchor.after[0])<.002,'zoom keeps the same day-column focal point: '+JSON.stringify(zoomAnchor));
 assert(Math.abs(zoomAnchor.before[1]-zoomAnchor.after[1])<1,'zoom keeps the same minute under the pointer');
 await page.setViewportSize({width:1280,height:900});
 await view(page,'day');assert.equal(await page.evaluate(()=>document.querySelectorAll('.calendar-time-event').length),2);
 await view(page,'agenda');assert.equal(await page.evaluate(()=>document.querySelectorAll('.calendar-agenda .calendar-selected-day').length),1);
 const identity=await page.evaluate(async()=>{const input=document.querySelector('.calendar-search input'),tabs=document.querySelector('.calendar-tools');input.value='未提交';input.focus();await refreshSession();return input===document.querySelector('.calendar-search input')&&tabs===document.querySelector('.calendar-tools')&&input.value==='未提交';});assert(identity,'poll preserves real controls/draft');
 await page.locator('.calendar-search input').fill('尚未提交的检索');
 await page.evaluate(()=>openCalendarItem({taskId:7,id:3,occurrenceAt:123}));await idle(page);
 assert.equal(requests.filter(r=>r.path==='/api/task').at(-1).query.occurrenceAt,'123','original recurrence identity reaches existing detail');
 await page.getByRole('button',{name:'返回列表',exact:true}).click();await idle(page);
 assert.equal(await page.evaluate(()=>route),'calendar');
 assert.equal(await page.evaluate(()=>calendarView),'agenda');
 assert.equal(await page.locator('.calendar-search input').evaluate(input=>input.value),'尚未提交的检索','detail return preserves unsubmitted search draft');
 await page.locator('.calendar-search input').fill('闰日');await page.evaluate(()=>document.querySelector('.calendar-search').requestSubmit());await idle(page);assert.equal(requests.filter(r=>r.path==='/api/agenda').at(-1).query.search,'闰日');assert.equal(await page.evaluate(()=>document.querySelectorAll('.calendar-entry').length),1);
 failPreference=true;await page.getByRole('button',{name:'包含系统日程',exact:true}).click();await idle(page);assert(!included&&await page.evaluate(()=>!session.calendarSystemIncluded),'permission denial does not enable calendar sources');assert((await page.locator('#toast').textContent()).includes('请在手机授权'));
 failPreference=false;await page.getByRole('button',{name:'包含系统日程',exact:true}).click();await idle(page);assert(included);assert(await page.evaluate(()=>session.systemCalendarIncluded),'calendar source toggle does not change home preference');
 assert.equal(requests.filter(r=>r.path==='/api/agenda').at(-1).query.search,'闰日','detail draft was submitted only explicitly');
 await view(page,'month');holdFrom='2024-02-26';await page.evaluate(()=>{calendarShift(1);});for(let n=0;n<100&&!releaseHeld;n++)await new Promise(resolve=>setTimeout(resolve,10));assert(releaseHeld,'old date request is in flight');await page.evaluate(()=>calendarShift(1));releaseHeld();releaseHeld=null;await idle(page);assert.equal(await page.evaluate(()=>calendarDate),'2024-04-29');assert.equal(await page.evaluate(()=>JSON.parse(document.querySelector('.calendar-body').dataset.window)[1]),'2024-04-01','late older month does not replace current results');
 await view(page,'week');await page.getByRole('button',{name:'导出周日程为图片',exact:true}).click();await page.getByRole('button',{name:'导出',exact:true}).click();await idle(page);assert(await page.evaluate(()=>!calendarExportBusy&&!document.querySelector('.calendar-export').disabled),'failed export releases busy state');assert((await page.locator('#toast').textContent()).includes('日程读取不完整'));
 await view(page,'day');assert(await page.evaluate(()=>document.querySelector('.calendar-export').inert),'week export is unavailable in other views');
 const fill=await page.evaluate(()=>{const r=document.querySelector('.calendar-tools .selection-highlight').getBoundingClientRect();return r.width>0&&r.height>0;});assert(fill,'shared live-state highlighter remains positioned');
 assert.equal(errors.length,0,errors.join('\n'));console.log('PASS functional five-view calendar, native windows, repeatable controls and independent source preference (mock API; no screenshots)');
 }finally{releaseHeld?.();if(browser)await browser.close();await new Promise(r=>server.close(r));}})().catch(error=>{console.error(error);server.close();process.exitCode=1;});
