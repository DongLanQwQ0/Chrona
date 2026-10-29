/* Real browser, explicit mock phone API. Device execution is a separate acceptance step. */
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert');
const {chromium}=require('./lan_browser_cdp.cjs');
const root=path.resolve(__dirname,'../app/src/main/assets/lan'),out=path.resolve(__dirname,'../build/lan-parity-checks');
fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{const file={'/':'index.html','/app.js':'app.js','/icons.js':'icons.js','/brand.svg':'brand.svg','/style.css':'style.css'}[new URL(req.url,'http://localhost').pathname];if(!file){res.writeHead(404).end();return;}res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':file.endsWith('.svg')?'image/svg+xml':'text/html');res.end(fs.readFileSync(path.join(root,file)));});
let revision=5,phoneZone='Asia/Shanghai',agendaItems=null;const errors=[],requests=[];
const tomorrow=Date.parse('2026-10-18T14:00:00+08:00');
const tasks=[{id:1,text:'学习安排',status:'ready',source:'手动记录',created:tomorrow-86400000},{id:2,text:'作业',status:'ready',source:'学在浙大',created:tomorrow-86400000}];
let items=[
 {id:11,taskId:1,title:'课程讨论',category:'event',start:tomorrow,end:tomorrow+3600000,canAcknowledge:true,acknowledged:false},
 {id:12,taskId:1,title:'购买资料',category:'task',start:null,end:null,due:tomorrow,completed:false},
 {id:21,taskId:2,title:'线性代数 · 第三次作业',category:'homework',start:null,end:null,due:tomorrow,completed:false,managed:true,submitted:false,mobileUrl:'https://mcourses.zju.edu.cn/course/123/homework/456',desktopUrl:'https://courses.zju.edu.cn/course/123/learning-activity#/456'}
].map(item=>({zone:'Asia/Shanghai',allDay:false,needsConfirmation:false,calendarLinked:false,location:'紫金港',description:'完成指定练习，整理证明过程。',reminder:null,...item}));
async function mock(route){const request=route.request(),url=new URL(request.url()),body=request.postDataJSON();requests.push({path:url.pathname,query:Object.fromEntries(url.searchParams),body});let data;
 if(url.pathname==='/api/session')data={csrf:'parity-csrf',zone:phoneZone,calendarAllowed:true,calendarReadable:true,systemCalendarIncluded:false,palette:{background:'#f4f6f8',surface:'#ffffff',text:'#202c36',muted:'#70808e',primary:'#008582',outline:'#d7e0e7'},syncStatus:'同步成功'};
 else if(url.pathname==='/api/home')data={revision,items,calendar:[],date:'2026-10-18',review:1,failed:0,todos:{total:2,todayDue:2,overdue:0,unscheduled:0,items:items.filter(item=>item.category!=='event')}};
 else if(url.pathname==='/api/task')data={revision,task:tasks.find(item=>item.id===Number(url.searchParams.get('id'))),candidates:items.filter(item=>item.taskId===Number(url.searchParams.get('id'))),images:[],files:[],mergedTargets:[]};
 else if(url.pathname==='/api/inbox')data={revision,items:tasks,total:tasks.length,page:Number(url.searchParams.get('page')),pageSize:12};
 else if(url.pathname==='/api/schedule'){const filtered=items.filter(item=>!url.searchParams.get('category')||item.category===url.searchParams.get('category'));data={revision,items:filtered,total:filtered.length,page:Number(url.searchParams.get('page')),pageSize:24};}
 else if(url.pathname==='/api/agenda')data={revision,items:agendaItems??items.map(item=>({...item,start:item.start??item.due,end:item.end??item.due,system:false,source:'拾时'})),truncated:false,systemUnavailable:false};
 else if(url.pathname==='/api/calendar/publish-selected'){assert.equal(request.headers()['x-chrona-csrf'],'parity-csrf');assert.equal(body.revision,revision);revision++;data={revision,summary:'已写入 2 条，跳过 1 条，失败 0 条',published:2,skipped:1,failed:0};}
 else if(url.pathname==='/api/candidate/acknowledge'){items.find(item=>item.id===Number(body.candidateId)).acknowledged=body.acknowledged;data={revision};}
 else if(url.pathname==='/api/candidate/complete'){items.find(item=>item.id===Number(body.candidateId)).completed=body.completed;data={revision:++revision};}
 else if(url.pathname==='/api/candidate/save'){Object.assign(items.find(item=>item.id===Number(body.candidateId)),body,{id:Number(body.candidateId),taskId:Number(body.id)});data={revision:++revision};}
 else if(url.pathname==='/api/candidate/create'){items.push({...body,id:99,taskId:Number(body.id)});data={revision:++revision};}
 else throw new Error('Unhandled mock '+url.pathname);
 await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
}
async function idle(page){await page.evaluate(async()=>{while(pending)await new Promise(resolve=>setTimeout(resolve,15));await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));});}
async function clickText(page,text){await page.getByRole('button',{name:text,exact:true}).click();await idle(page);}
async function checkTimePayloads(page){
 const edit=async item=>{await page.evaluate(item=>candidateEditor(item),item);await idle(page);};
 await edit({...items[1],start:null,end:null});await page.locator('#field-start').fill('2026-10-18T13:00');await page.locator('#field-end').evaluate(element=>element.focus());
 const before=requests.filter(item=>item.path==='/api/candidate/save').length;await clickText(page,'保存');
 assert.equal(requests.filter(item=>item.path==='/api/candidate/save').length,before,'picker equality must not submit invalid duration');assert((await page.locator('.candidate-form .form-feedback').textContent()).includes('结束时间须晚于开始时间'));
 await page.locator('#field-end').fill('');await clickText(page,'保存');let saved=requests.filter(item=>item.path==='/api/candidate/save').at(-1).body;assert.equal(saved.start,Date.parse('2026-10-18T05:00:00Z'));assert.equal(saved.end,null,'start-only stays start-only');
 await edit({...items[0],start:Date.parse('2026-10-18T04:00:00Z'),end:Date.parse('2026-10-18T05:00:00Z')});await page.locator('#field-start').fill('2026-10-18T14:00');assert.equal(await page.locator('#field-end').evaluate(element=>element.value),'2026-10-18T14:05');await clickText(page,'保存');saved=requests.filter(item=>item.path==='/api/candidate/save').at(-1).body;assert.equal(saved.end-saved.start,300000,'shifted start sends valid five-minute duration');
 await edit({...items[2],start:tomorrow,end:tomorrow+3600000});await clickText(page,'清空执行时间');await clickText(page,'保存');saved=requests.filter(item=>item.path==='/api/candidate/save').at(-1).body;assert.equal(saved.start,null);assert.equal(saved.end,null);assert.equal(saved.due,tomorrow,'clearing homework execution retains deadline');
}
async function checkCalendarDates(page){
 await page.cdp.send('Emulation.setTimezoneOverride',{timezoneId:'America/Los_Angeles'});
 const event=(title,start,end,allDay=false)=>({title,start:Date.parse(start),end:Date.parse(end),allDay,system:true,location:''});
 agendaItems=[event('上午安排','2026-10-12T08:00:00+08:00','2026-10-12T09:00:00+08:00'),event('午夜跨日','2026-10-12T23:30:00+08:00','2026-10-13T00:30:00+08:00'),event('整日事项','2026-10-12T00:00:00+08:00','2026-10-13T00:00:00+08:00',true)];
 await page.evaluate(async()=>{calendarDate='2026-10-12';calendarView='day';await navigate('calendar');});await idle(page);
 const titles=()=>page.evaluate(()=>[...document.querySelectorAll('.week-day')].map(day=>({date:day.dataset.date,titles:[...day.querySelectorAll('strong')].map(element=>element.textContent)})));
 assert.deepEqual(await titles(),[{date:'2026-10-12',titles:['上午安排','午夜跨日','整日事项']}],'phone day includes morning and midnight, not browser timezone');
 await clickText(page,'下一天');assert.deepEqual(await titles(),[{date:'2026-10-13',titles:['午夜跨日']}],'cross-midnight repeats only on intersecting phone days; all-day excludes its end');
 await page.evaluate(async()=>{calendarDate='2026-10-12';calendarView='week';await render();});await idle(page);
 await page.evaluate(()=>{window.calendarExportText=[];const original=CanvasRenderingContext2D.prototype.fillText;CanvasRenderingContext2D.prototype.fillText=function(text,x,y,...rest){calendarExportText.push({text,x});return original.call(this,text,x,y,...rest);};});await clickText(page,'导出周图片');
 const exported=await page.evaluate(()=>calendarExportText);assert.equal(exported.filter(item=>item.text==='上午安排').length,1);assert.equal(exported.filter(item=>item.text==='午夜跨日').length,2);assert.equal(exported.filter(item=>item.text==='整日事项').length,1,'PNG uses same civil-day membership as calendar');
 fs.writeFileSync(path.join(out,'exported-phone-zone.png'),Buffer.from((await page.evaluate(()=>window.exportedWeek)).split(',')[1],'base64'));
 await page.evaluate(async()=>{calendarDate='2026-10-31';calendarView='month';await render();});await idle(page);await clickText(page,'下一月');assert.equal(requests.at(-1).query.from,'2026-11-01');assert.equal(requests.at(-1).query.until,'2026-12-01');await clickText(page,'上一月');assert.equal((await titles()).length,31);
 phoneZone='America/New_York';await page.evaluate(zone=>session.zone=zone,phoneZone);
 for(const [date,hours]of [['2026-03-08',23],['2026-11-01',25]]){const duration=await page.evaluate(date=>phoneDayStart(addCivilDays(date,1))-phoneDayStart(date),date);assert.equal(duration,hours*3600000,'DST natural day duration');}
 agendaItems=[event('春季凌晨','2026-03-08T00:10:00-05:00','2026-03-08T00:40:00-05:00'),event('春季跳时后','2026-03-08T03:10:00-04:00','2026-03-08T03:40:00-04:00'),event('次日边界','2026-03-09T00:00:00-04:00','2026-03-09T00:30:00-04:00')];
 await page.evaluate(async()=>{calendarDate='2026-03-08';calendarView='day';await render();});await idle(page);assert.deepEqual((await titles())[0].titles,['春季凌晨','春季跳时后']);await clickText(page,'下一天');assert.deepEqual((await titles())[0].titles,['次日边界']);
 agendaItems=[event('秋季第一次一点','2026-11-01T01:15:00-04:00','2026-11-01T01:45:00-04:00'),event('秋季第二次一点','2026-11-01T01:15:00-05:00','2026-11-01T01:45:00-05:00')];await page.evaluate(async()=>{calendarDate='2026-11-01';calendarView='day';await render();});await idle(page);assert.equal((await titles())[0].titles.length,2,'both repeated fall-back hours appear');
 phoneZone='America/Sao_Paulo';await page.evaluate(zone=>session.zone=zone,phoneZone);assert.equal(await page.evaluate(()=>new Date(phoneDayStart('2018-11-04')).toISOString()),'2018-11-04T03:00:00.000Z','midnight DST gap starts at first existing instant');
 phoneZone='Pacific/Kiritimati';await page.cdp.send('Emulation.setTimezoneOverride',{timezoneId:'Etc/GMT+12'});await page.evaluate(zone=>session.zone=zone,phoneZone);await clickText(page,'今天');assert(await page.evaluate(()=>document.querySelector('.week-day.today')?.dataset.date===phoneDate()&&document.querySelector('.calendar-controls input').value===phoneDate()&&phoneDate()!==dateISO(new Date())),'today uses phone date across browser date line');
 phoneZone='Asia/Shanghai';agendaItems=null;await page.evaluate(zone=>session.zone=zone,phoneZone);await page.cdp.send('Emulation.setTimezoneOverride',{timezoneId:'UTC'});
}
(async()=>{await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});try{
 const page=await browser.newPage({viewport:{width:1280,height:900}});page.on('pageerror',error=>errors.push(String(error)));await page.route('**/api/**',mock);await page.goto(`http://127.0.0.1:${server.address().port}/`);await page.getByRole('heading',{name:'首页'}).waitFor();await idle(page);
 assert((await page.locator('#content').textContent()).includes('线性代数 · 第三次作业'),'homework in homepage timeline and todo summary');
 await page.screenshot({path:path.join(out,'desktop-home.png'),fullPage:true});
 const retained=await page.evaluate(async()=>{const item=document.querySelector('.entry');await render();return item===document.querySelector('.entry');});assert(retained,'unchanged timeline DOM retained');
 await clickText(page,'最近作业');assert.equal(requests.at(-1).query.category,'homework');assert.equal(requests.at(-1).query.todo,'3');assert.equal(requests.at(-1).query.tab,'3');
 await clickText(page,'筛选');await clickText(page,'已写入');await clickText(page,'今日截止');await clickText(page,'应用筛选');assert.equal(requests.at(-1).query.publication,'2');assert.equal(requests.at(-1).query.todo,'1');
 await clickText(page,'清除筛选');await clickText(page,'历史');assert.equal(requests.at(-1).query.tab,'2');await clickText(page,'全部');
 await clickText(page,'选择本页');await clickText(page,'写入所选');await clickText(page,'确认');assert.deepEqual(requests.find(item=>item.path==='/api/calendar/publish-selected').body.ids,['11','12','21']);
 await page.evaluate(async()=>{await navigate('task/1');});await clickText(page,'确认活动');assert(items[0].acknowledged);await clickText(page,'取消确认活动');assert(!items[0].acknowledged);
 await page.evaluate(()=>navigate('task/1?candidate_id=12'));await idle(page);await clickText(page,'标记完成');assert(items[1].completed);await clickText(page,'恢复待办');assert(!items[1].completed);
 await clickText(page,'新增事项');await page.locator('#field-title').fill('新增日程');await page.locator('#field-start').fill('2026-10-19T10:00');await page.locator('#field-end').fill('2026-10-19T11:00');await clickText(page,'保存');assert.equal(items.length,4,'append preserves existing candidates');
 await page.evaluate(async()=>{await navigate('task/2');});await clickText(page,'修改作业');assert(await page.locator('#field-title').evaluate(element=>element.readOnly));assert(await page.locator('#field-category').evaluate(element=>element.disabled));assert(await page.locator('#field-due').evaluate(element=>element.readOnly));
 await page.locator('#field-start').fill('2026-10-18T13:00');await page.locator('#field-end').evaluate(element=>{element.blur();element.focus();});assert.equal(await page.locator('#field-end').evaluate(element=>element.value),'2026-10-18T13:00',JSON.stringify(await page.evaluate(()=>({start:document.querySelector('#field-start').value,end:document.querySelector('#field-end').value,active:document.activeElement.id,open:document.querySelector('dialog').open,inert:document.querySelector('dialog').inert}))));await clickText(page,'清空执行时间');assert.equal(await page.locator('#field-start').evaluate(element=>element.value),'');await clickText(page,'取消');await idle(page);
 const links=await page.evaluate(()=>[...document.querySelectorAll('.homework-links a')].map(element=>element.href));assert.equal(links.length,2);assert(links[0].endsWith('/123/homework/456'));assert(links[1].endsWith('#/456'));
 await page.evaluate(async()=>{calendarDate=new Date('2026-10-18T12:00:00');await navigate('calendar');});await page.getByRole('heading',{name:'日历',exact:true}).waitFor();
 await page.evaluate(()=>{const original=HTMLCanvasElement.prototype.toBlob;HTMLCanvasElement.prototype.toBlob=function(...args){window.exportedWeek=this.toDataURL('image/png');return original.apply(this,args);};});await clickText(page,'导出周图片');const png=await page.evaluate(()=>window.exportedWeek);assert(png.startsWith('data:image/png;base64,'));fs.writeFileSync(path.join(out,'exported-week.png'),Buffer.from(png.split(',')[1],'base64'));
 await clickText(page,'月');assert.equal(requests.at(-1).query.from,'2026-10-01');assert.equal(requests.at(-1).query.until,'2026-11-01');await page.screenshot({path:path.join(out,'desktop-calendar.png'),fullPage:true});
 await page.setViewportSize({width:390,height:844});await page.evaluate(async()=>{await navigate('schedule');});await clickText(page,'筛选');await page.screenshot({path:path.join(out,'mobile-filters.png'),fullPage:true});
 const geometry=await page.evaluate(()=>[...document.querySelectorAll('#dialog .selection-bar')].map(element=>{const rect=element.getBoundingClientRect();return {left:rect.left,right:rect.right,width:rect.width,scroll:element.scrollWidth};}));assert(geometry.every(item=>item.left>=0&&item.right<=390&&item.scroll<=item.width+1),'full-width selection bars without narrow-screen clipping');
 await page.locator('#close-dialog').click();await idle(page);await page.evaluate(()=>document.body.classList.add('dark'));await page.screenshot({path:path.join(out,'mobile-dark-schedule.png'),fullPage:true});
 await page.evaluate(async()=>{await navigate('inbox');const select=document.querySelector('.filters select');select.value='1';select.dispatchEvent(new Event('change',{bubbles:true}));});await idle(page);assert.equal(requests.at(-1).query.status,'1');
 await clickText(page,'选择本页');await clickText(page,'写入所选');await clickText(page,'确认');assert(requests.filter(item=>item.path==='/api/calendar/publish-selected').at(-1).body.inbox,'capture IDs used for inbox bulk publication');
 await checkTimePayloads(page);await checkCalendarDates(page);
 assert.equal(errors.length,0,errors.join('\n'));console.log('Web parity: PASS (phone-zone calendar, DST, PNG membership and time payloads included)');
}finally{await browser.close();server.close();}})().catch(error=>{console.error(error);server.close();process.exitCode=1;});
