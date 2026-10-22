/* Production web assets, isolated HTTP fixture. No phone data, AI or calendar writes. */
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert');
const root=path.resolve(__dirname,'../app/src/main/assets/lan'),out=path.resolve(__dirname,'../build/lan-detail-navigation');
const start=Date.parse('2026-10-22T08:00:00+08:00');
function fixtures(){
 const common={taskId:1,category:'event',zone:'Asia/Shanghai',allDay:false,location:'图书馆 · 二楼讨论室',description:'核对资料与课程安排。',needsConfirmation:false,calendarLinked:false,mergedSources:[]};
 const items=Array.from({length:43},(_,i)=>({...common,id:i+101,title:`日程 ${String(i+1).padStart(2,'0')}`,start:start+i*3600000,end:start+(i+1)*3600000}));
 items[17].description='长内容验收：完整保留每一段。\n'.repeat(90);
 items.push({...common,id:190,title:'未定时间',start:null,end:null});
 items.push({...common,id:191,title:'同时间的另一项',start:items[10].start,end:items[10].end});
 const homework={...common,id:201,taskId:2,title:'课程作业 · 第三次练习',category:'homework',start:null,end:null,due:start+86400000,effectiveDue:start+86400000,managed:true};
 return {items:items.reverse(),homework};
}
function createServer(){
 const {items,homework}=fixtures(),requests=[];let revision=1;
 const task=id=>({id,text:'一条记录包含多项安排，原文与附件仍可查看。',source:'浏览器隔离测试',created:start-86400000,status:'ready'});
 const server=http.createServer(async(req,res)=>{
  try{
   const url=new URL(req.url,'http://localhost'),file={'/':'index.html','/app.js':'app.js','/style.css':'style.css'}[url.pathname];
   res.setHeader('Cache-Control','no-store');
   if(file){res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(path.join(root,file)));return;}
   let raw='';for await(const chunk of req)raw+=chunk;const body=raw?JSON.parse(raw):null;
   requests.push({path:url.pathname,query:Object.fromEntries(url.searchParams),body});let data;
   if(url.pathname==='/api/session')data={csrf:'isolated-fixture',zone:'Asia/Shanghai',calendarAllowed:true,palette:{background:'#f4f6f8',surface:'#ffffff',text:'#202c36',muted:'#70808e',primary:'#008582',outline:'#d7e0e7'}};
   else if(url.pathname==='/api/home')data={revision,date:'2026-10-22',review:0,failed:0,items:[items.find(i=>i.id===118),homework],recent:[],calendar:[],todos:{total:1,todayDue:0,overdue:0,unscheduled:1,items:[homework]}};
   else if(url.pathname==='/api/task'){const id=Number(url.searchParams.get('id'));data={revision,task:task(id),candidates:id===1?items:id===2?[homework]:[],managedHomeworkCapture:id===2,images:[],files:[],mergedTargets:[]};}
   else if(url.pathname==='/api/schedule'){let filtered=[...items,homework];if(url.searchParams.get('category'))filtered=filtered.filter(i=>i.category===url.searchParams.get('category'));const page=Number(url.searchParams.get('page')||0);data={revision,total:filtered.length,items:filtered.slice(page*24,(page+1)*24),page,pageSize:24,oldestFirst:true};}
   else if(url.pathname==='/api/inbox')data={revision,items:[task(1),task(2),task(3)],total:3,page:0,pageSize:12};
   else if(url.pathname==='/api/candidate/acknowledge'){items.find(i=>i.id===Number(body.candidateId)).acknowledged=body.acknowledged;data={revision:++revision};}
   else if(url.pathname==='/api/candidate/save'){const item=items.find(i=>i.id===Number(body.candidateId));Object.assign(item,body,{id:item.id,taskId:item.taskId});data={revision:++revision};}
   else{res.statusCode=404;data={error:'Fixture endpoint missing: '+url.pathname};}
   res.setHeader('Content-Type','application/json');res.end(JSON.stringify(data));
  }catch(error){res.writeHead(500).end(JSON.stringify({error:error.message}));}
 });return {server,requests};
}
async function idle(page){await page.evaluate(async()=>{while(pending)await new Promise(r=>setTimeout(r,10));await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));});}
async function run(){
 fs.mkdirSync(out,{recursive:true});const {server,requests}=createServer();await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const {chromium}=require('./lan_browser_cdp.cjs'),browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}),errors=[];
 let count=0;const check=(value,message)=>{assert(value,message);count++;};
 try{
  const page=await browser.newPage({viewport:{width:1280,height:900}});page.on('pageerror',e=>errors.push(String(e)));const url=`http://127.0.0.1:${server.address().port}/`;
  await page.goto(url);await page.getByRole('heading',{name:'首页',exact:true}).waitFor();await idle(page);
  await page.getByRole('button',{name:'最近作业',exact:true}).click();await idle(page);
  check(requests.at(-1).query.category==='homework'&&requests.at(-1).query.todo==='3'&&requests.at(-1).query.tab==='3','home shortcut uses homework/open/all filters');
  check(await page.evaluate(()=>document.querySelector('.filters select').value==='homework'),'homework type shown selected');
  const shortcut=await page.evaluate(()=>location.href);await page.goto(shortcut);await idle(page);check(await page.evaluate(()=>route==='schedule'&&filter==='homework'&&scheduleTab===3&&todoScope===3),'shortcut survives full reload');
  await page.evaluate(()=>navigate('home'));await idle(page);await page.getByRole('button',{name:'日程 18'}).click();await idle(page);
  check(await page.evaluate(()=>document.querySelector('.candidate-slide[aria-current=true]').dataset.candidate==='118'),'external item goes directly to requested candidate');
  check(await page.evaluate(()=>{const target=document.querySelector('.candidate-slide[aria-current=true]').getBoundingClientRect(),view=document.querySelector('.candidate-viewport').getBoundingClientRect();return Math.abs(target.left+target.width/2-view.left-view.width/2)<2;}),'requested card is visibly centered');
  check(await page.evaluate(()=>{const view=document.querySelector('.candidate-viewport').getBoundingClientRect();return [...document.querySelectorAll('.candidate-slide')].filter(el=>{const r=el.getBoundingClientRect();return r.left>=view.left&&r.right<=view.right;}).length>=3;}),'desktop displays at least three complete cards');
  check(await page.evaluate(()=>[...document.querySelectorAll('.candidate-slide')].map(el=>Number(el.dataset.candidate)).join(',')===[...Array.from({length:11},(_,i)=>101+i),191,...Array.from({length:32},(_,i)=>112+i),190].join(',')),'chronological order, stable equal-time tie and undated last');
  check(await page.evaluate(()=>document.documentElement.scrollHeight<=innerHeight+1),'45 candidates do not increase document height');
  await new Promise(r=>setTimeout(r,350));await page.screenshot({path:path.join(out,'desktop-target.png'),fullPage:false});
  await page.evaluate(()=>{document.querySelector('.candidate-slide[aria-current=true]').scrollTop=300;});await page.evaluate(()=>render());await idle(page);
  check(await page.evaluate(()=>document.querySelector('.candidate-slide[aria-current=true]').scrollTop===300),'refresh retains selected candidate and internal scroll');
  await page.getByRole('button',{name:'下一项日程',exact:true}).click();await idle(page);await new Promise(r=>setTimeout(r,500));
  check(await page.evaluate(()=>document.querySelector('.candidate-slide[aria-current=true]').dataset.candidate==='119'),'footer goes to next schedule');
  await page.evaluate(()=>{const el=document.querySelector('.candidate-viewport');el.dispatchEvent(new KeyboardEvent('keydown',{key:'End',bubbles:true}));});await new Promise(r=>setTimeout(r,700));
  check(await page.evaluate(()=>document.querySelector('.candidate-slide[aria-current=true]').dataset.candidate==='190'),'keyboard End goes to last schedule');
  await page.evaluate(()=>{const el=document.querySelector('.candidate-viewport'),target=el.children[2];el.dispatchEvent(new WheelEvent('wheel'));el.scrollLeft=el.scrollLeft+target.getBoundingClientRect().left-el.getBoundingClientRect().left-(el.clientWidth-target.offsetWidth)/2;});await new Promise(r=>setTimeout(r,300));
  check(await page.evaluate(()=>document.querySelector('.candidate-slide[aria-current=true]').dataset.candidate==='103'),'native horizontal scroll updates active card and controls');
  check(await page.evaluate(()=>location.hash.includes('candidate_id=103')),'swipe updates reloadable candidate identity');
  await page.getByRole('button',{name:'原始内容',exact:true}).click();await idle(page);check((await page.locator('#dialog-body').textContent()).includes('一条记录包含多项安排'),'original remains accessible without occupying page');await page.locator('#close-dialog').click();await idle(page);
  for(const width of [390,320])for(const dark of [false,true]){
   await page.setViewportSize({width,height:844});await page.evaluate(dark=>document.body.classList.toggle('dark',dark),dark);await idle(page);
   check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth&&document.documentElement.scrollHeight<=innerHeight+1),'narrow theme has no document overflow');
   check(await page.evaluate(()=>{const r=document.querySelector('.candidate-footer').getBoundingClientRect(),dock=document.querySelector('.bottom').getBoundingClientRect();return r.bottom<=dock.top&&r.top>=0;}),'footer is visible above fixed Dock');
   await page.screenshot({path:path.join(out,`${width}-${dark?'dark':'light'}.png`),fullPage:false});
  }
  await page.cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});await page.getByRole('button',{name:'上一项日程',exact:true}).click();await idle(page);
  check(await page.evaluate(()=>document.querySelector('.candidate-slide[aria-current=true]').dataset.candidate==='102'),'reduced motion still selects and scrolls');
  await page.locator('.candidate-navigation .select-trigger').click();await page.getByRole('button',{name:'45 · 未定时间',exact:true}).click();await idle(page);
  check(await page.evaluate(()=>document.querySelector('.candidate-slide[aria-current=true]').dataset.candidate==='190'&&document.querySelector('[aria-label="下一项日程"]').disabled),'dropdown jumps directly to final candidate');
  await page.evaluate(async()=>{const leaving=navigate('home');document.querySelector('.candidate-viewport').dispatchEvent(new Event('scrollend'));await leaving;});await idle(page);
  check(await page.evaluate(()=>route==='home'&&location.hash==='#home'),'old scroll callback cannot hijack a newer route');
  await page.evaluate(()=>navigate('task/1'));await idle(page);check(await page.evaluate(()=>document.querySelector('.candidate-slide[aria-current=true]').dataset.candidate==='190'),'plain record entry remembers last candidate');
  await page.evaluate(()=>navigate('task/1?candidate_id=118'));await idle(page);await page.getByRole('button',{name:'修改日程',exact:true}).click();await idle(page);
  await page.locator('#field-title').fill('修改后仍定位同一事项');await page.getByRole('button',{name:'保存',exact:true}).click();await idle(page);
  check(await page.evaluate(()=>document.querySelector('.candidate-slide[aria-current=true] h2').textContent==='修改后仍定位同一事项'),'bottom edit saves selected candidate and restores its card');
  await page.setViewportSize({width:320,height:650});await idle(page);check(await page.evaluate(()=>{const footer=document.querySelector('.candidate-footer').getBoundingClientRect(),dock=document.querySelector('.bottom').getBoundingClientRect();return footer.bottom<=dock.top&&document.querySelector('.candidate-viewport').clientHeight>=120;}),'small zoom-equivalent viewport retains useful card area and visible controls');
  await page.screenshot({path:path.join(out,'320-short-dark.png'),fullPage:false});
  for(const width of [390,320]){
   await page.evaluate(()=>document.querySelector('.candidate-slide[aria-current=true]').scrollTop=0);
   await page.setViewportSize({width,height:844});await page.evaluate(()=>{const sizes=[...document.querySelectorAll('#workspace *')].map(el=>[el,parseFloat(getComputedStyle(el).fontSize)]);for(const [el,size]of sizes){el.dataset.originalFont=el.style.fontSize;el.style.fontSize=size*1.5+'px';}});await idle(page);
   await page.screenshot({path:path.join(out,`${width}-font150-dark.png`),fullPage:false});
   const geometry=await page.evaluate(()=>{const r=document.querySelector('.candidate-footer').getBoundingClientRect(),dock=document.querySelector('.bottom').getBoundingClientRect();return {footer:r.toJSON(),dock:dock.toJSON(),card:document.querySelector('.candidate-viewport').clientHeight,scroll:document.documentElement.scrollWidth,width:innerWidth};});
   check(geometry.footer.bottom<=geometry.dock.top&&geometry.card>100&&geometry.scroll<=geometry.width,'150% actual text retains card area and footer reachability: '+JSON.stringify(geometry));
   await page.evaluate(()=>{for(const el of document.querySelectorAll('[data-original-font]')){el.style.fontSize=el.dataset.originalFont;delete el.dataset.originalFont;}});
  }
  await page.evaluate(()=>navigate('task/2?candidate_id=201'));await idle(page);check(await page.getByRole('button',{name:'原始内容',exact:true}).count()===0,'managed homework hides original capture actions');
  await page.evaluate(()=>navigate('task/3'));await idle(page);check(await page.getByRole('button',{name:'手动建立日程',exact:true}).count()===1,'empty capture retains create path');
  check(!errors.length,errors.join('\n'));fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({assertions:count,errors},null,2));console.log(`Web detail navigation: PASS (${count} assertions)`);
 }finally{await browser.close();server.close();}
}
if(require.main===module){if(process.argv.includes('--serve')){const {server}=createServer();server.listen(18789,'127.0.0.1',()=>console.log('Isolated fixture http://127.0.0.1:18789'));}else run().catch(e=>{console.error(e);process.exitCode=1;});}
module.exports={createServer};
