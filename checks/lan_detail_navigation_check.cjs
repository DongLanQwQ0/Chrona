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
 const {items,homework}=fixtures(),requests=[],control={failSave:false,delaySave:0};let revision=1;
 const task=id=>({id,text:'一条记录包含多项安排，原文与附件仍可查看。',source:'浏览器隔离测试',created:start-86400000,status:'ready'});
 const server=http.createServer(async(req,res)=>{
  try{
   const url=new URL(req.url,'http://localhost'),file={'/':'index.html','/app.js':'app.js','/calendar.js':'calendar.js','/icons.js':'icons.js','/brand.svg':'brand.svg','/style.css':'style.css'}[url.pathname];
   res.setHeader('Cache-Control','no-store');
   if(file){res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':file.endsWith('.svg')?'image/svg+xml':'text/html');res.end(fs.readFileSync(path.join(root,file)));return;}
   let raw='';for await(const chunk of req)raw+=chunk;const body=raw?JSON.parse(raw):null;
   requests.push({path:url.pathname,query:Object.fromEntries(url.searchParams),body});let data;
   if(url.pathname==='/api/session')data={csrf:'isolated-fixture',zone:'Asia/Shanghai',calendarAllowed:true,palette:{background:'#f4f6f8',surface:'#ffffff',text:'#202c36',muted:'#70808e',primary:'#008582',outline:'#d7e0e7'}};
   else if(url.pathname==='/api/home')data={revision,date:'2026-10-22',review:0,failed:0,items:[items.find(i=>i.id===118),homework],recent:[],calendar:[],todos:{total:1,todayDue:0,overdue:0,unscheduled:1,items:[homework]}};
   else if(url.pathname==='/api/task'){const id=Number(url.searchParams.get('id'));data={revision,task:task(id),candidates:id===1?items:id===2?[homework]:[],managedHomeworkCapture:id===2,images:[],files:[],mergedTargets:[]};}
   else if(url.pathname==='/api/schedule'){let filtered=[...items,homework];if(url.searchParams.get('category'))filtered=filtered.filter(i=>i.category===url.searchParams.get('category'));const page=Number(url.searchParams.get('page')||0);data={revision,total:filtered.length,items:filtered.slice(page*24,(page+1)*24),page,pageSize:24,oldestFirst:true};}
   else if(url.pathname==='/api/inbox')data={revision,items:[task(1),task(2),task(3)],total:3,page:0,pageSize:12};
   else if(url.pathname==='/api/candidate/acknowledge'){items.find(i=>i.id===Number(body.candidateId)).acknowledged=body.acknowledged;data={revision:++revision};}
   else if(url.pathname==='/api/candidate/save'||url.pathname==='/api/candidate/publish'){if(control.delaySave)await new Promise(r=>setTimeout(r,control.delaySave));if(control.failSave){control.failSave=false;res.statusCode=409;data={error:'记录已更新，请保留修改并重新核对'};}else{const item=[...items,homework].find(i=>i.id===Number(body.candidateId));Object.assign(item,body,{id:item.id,taskId:item.taskId});data={revision:++revision};}}
   else if(url.pathname==='/api/candidate/create'){const item={...body,id:300,taskId:Number(body.id)};items.push(item);data={revision:++revision};}
   else{res.statusCode=404;data={error:'Fixture endpoint missing: '+url.pathname};}
   res.setHeader('Content-Type','application/json');res.end(JSON.stringify(data));
  }catch(error){res.writeHead(500).end(JSON.stringify({error:error.message}));}
 });return {server,requests,control,items};
}
async function idle(page){await page.evaluate(async()=>{while(pending)await new Promise(r=>setTimeout(r,10));await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));});}
async function run(){
 fs.mkdirSync(out,{recursive:true});const {server,requests,control,items}=createServer();await new Promise(r=>server.listen(0,'127.0.0.1',r));
 const {chromium}=require('./lan_browser_cdp.cjs'),browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'}),errors=[];
 let count=0;const check=(value,message)=>{assert(value,message);count++;},sleep=ms=>new Promise(r=>setTimeout(r,ms));
 try{
  const page=await browser.newPage({viewport:{width:1280,height:900}});page.on('pageerror',e=>errors.push(String(e)));const url='http://127.0.0.1:'+server.address().port+'/';
  await page.goto(url);await page.getByRole('heading',{name:'首页',exact:true}).waitFor();await idle(page);
  await page.getByRole('button',{name:'最近作业',exact:true}).click();await idle(page);
  check(requests.at(-1).query.category==='homework'&&requests.at(-1).query.todo==='3'&&requests.at(-1).query.tab==='3','home shortcut uses homework/open/all filters');
  const shortcut=await page.evaluate(()=>location.href);await page.goto(shortcut);await idle(page);check(await page.evaluate(()=>route==='schedule'&&filter==='homework'&&scheduleTab===3&&todoScope===3),'shortcut survives reload');
  await page.evaluate(()=>navigate('home'));await idle(page);await page.getByRole('button',{name:'日程 18'}).click();await idle(page);
  check(await page.evaluate(()=>document.querySelector('.master-item[aria-current=true]').dataset.candidate==='118'),'external target is selected');
  check(await page.evaluate(()=>document.querySelectorAll('.candidate-detail').length===1&&!document.querySelector('.candidate-viewport,.candidate-navigation,.candidate-footer')),'one complete detail, no carousel or bottom selector');
  check(await page.evaluate(()=>{const a=document.querySelector('.detail-master').getBoundingClientRect(),b=document.querySelector('.detail-reader').getBoundingClientRect();return a.right<b.left&&a.width/(a.width+b.width)>.27&&a.width/(a.width+b.width)<.34;}),'desktop uses 30/70 master detail');
  check(await page.evaluate(()=>[...document.querySelectorAll('.master-item')].map(el=>Number(el.dataset.candidate)).join(',')===[...Array.from({length:11},(_,i)=>101+i),191,...Array.from({length:32},(_,i)=>112+i),190].join(',')),'nearest time, stable ties and undated last');
  check(await page.evaluate(()=>{const el=document.querySelector('.candidate-detail .raw-text');return el.textContent.split('长内容验收').length===91&&getComputedStyle(el).overflowY==='visible'&&el.scrollHeight<=el.clientHeight+1&&document.documentElement.scrollHeight>innerHeight;}),'long text is complete and uses page scrolling');
  check(await page.evaluate(()=>{const el=document.querySelector('.master-item[aria-current=true]'),r=el.getBoundingClientRect(),v=el.parentElement.getBoundingClientRect();return r.top>=v.top&&r.bottom<=v.bottom;}),'selected row visible in compact index');
  await page.screenshot({path:path.join(out,'desktop-target.png'),fullPage:false});
  await page.evaluate(()=>{document.querySelector('.master-list').scrollTop=300;window.scrollTo(0,450);});await page.evaluate(()=>render());await idle(page);
  check(await page.evaluate(()=>document.querySelector('.master-item[aria-current=true]').dataset.candidate==='118'),'refresh retains target');
  await page.evaluate(()=>document.querySelector('.master-item[data-candidate="119"]').click());await idle(page);await sleep(300);
  check(await page.evaluate(()=>location.hash.includes('candidate_id=119')&&document.querySelector('.candidate-detail h2').textContent==='日程 19'),'list selection updates detail and reloadable identity');
  await page.evaluate(()=>document.querySelector('.master-item[aria-current=true]').dispatchEvent(new KeyboardEvent('keydown',{key:'End',bubbles:true})));await idle(page);
  check(await page.evaluate(()=>document.querySelector('.master-item[aria-current=true]').dataset.candidate==='190'),'keyboard End selects last entry');
  await page.evaluate(()=>navigate('task/1?candidate_id=118'));await idle(page);
  await page.getByRole('button',{name:'修改日程',exact:true}).click();await idle(page);
  check(await page.evaluate(()=>!document.querySelector('#dialog').open&&!!document.querySelector('.detail-reader>.candidate-form')),'edit is inline in right pane');
  check(await page.evaluate(()=>[...document.querySelectorAll('.form-group legend')].map(el=>el.textContent).join(',')==='基础信息,时间与地点,备注'&&parseFloat(getComputedStyle(document.querySelector('#field-description')).minHeight)>=120),'grouped form and resizable notes');
  check(await page.evaluate(()=>{const r=document.querySelector('.editor-actions').getBoundingClientRect(),d=document.querySelector('.bottom').getBoundingClientRect();return r.top>=0&&r.bottom<=d.top;}),'save toolbar visible above Dock before scrolling');
  await page.screenshot({path:path.join(out,'desktop-inline.png'),fullPage:false});
  await page.locator('#field-title').fill('保留中的修改');
  await page.evaluate(()=>history.back());await idle(page);check(await page.evaluate(()=>document.querySelector('#dialog-title').textContent==='放弃未保存的修改？'),'browser back protects inline draft');
  await page.getByRole('button',{name:'继续编辑',exact:true}).click();await idle(page);check(await page.evaluate(()=>location.hash.includes('candidate_id=118')&&document.querySelector('#field-title').value==='保留中的修改'),'canceled browser back restores target URL and draft');
  let unloadPrompts=0;page.cdp.on('Page.javascriptDialogOpening',params=>{if(params.type==='beforeunload'){unloadPrompts++;void page.cdp.send('Page.handleJavaScriptDialog',{accept:false});}});
  const titleBounds=await page.evaluate(()=>document.querySelector('#field-title').getBoundingClientRect().toJSON());await page.cdp.send('Input.dispatchMouseEvent',{type:'mousePressed',x:titleBounds.left+20,y:titleBounds.top+20,button:'left',clickCount:1});await page.cdp.send('Input.dispatchMouseEvent',{type:'mouseReleased',x:titleBounds.left+20,y:titleBounds.top+20,button:'left',clickCount:1});
  await page.cdp.send('Page.reload');await sleep(300);check(unloadPrompts===1&&await page.evaluate(()=>document.querySelector('#field-title').value==='保留中的修改'),'real browser reload prompts and cancel retains draft');
  await page.getByRole('button',{name:'取消',exact:true}).click();await idle(page);
  check(await page.evaluate(()=>document.querySelector('#dialog-title').textContent==='放弃未保存的修改？'),'cancel protects dirty draft');
  await page.getByRole('button',{name:'继续编辑',exact:true}).click();await idle(page);
  check(await page.evaluate(()=>document.querySelector('#field-title').value==='保留中的修改'),'continue edit keeps draft');
  await page.evaluate(()=>render());await idle(page);check(await page.evaluate(()=>document.querySelector('#field-title').value==='保留中的修改'),'refresh cannot destroy inline draft');
  control.failSave=true;await page.getByRole('button',{name:'保存',exact:true}).click();await idle(page);
  check(await page.evaluate(()=>document.querySelector('#field-title').value==='保留中的修改'&&!document.querySelector('.form-feedback').hidden&&!document.querySelector('.candidate-fields').inert),'failed save preserves draft and unlocks controls');
  control.delaySave=700;
  await page.evaluate(()=>{document.querySelector('.candidate-form').requestSubmit();document.querySelector('.candidate-form').requestSubmit();});await sleep(100);
  check(await page.evaluate(()=>document.querySelector('.candidate-fields').inert&&[...document.querySelectorAll('.editor-actions button')].every(b=>b.disabled)),'pending save locks fields and every mutation action');
  const saves=requests.filter(r=>r.path==='/api/candidate/save').length;await idle(page);control.delaySave=0;
  check(requests.filter(r=>r.path==='/api/candidate/save').length===saves,'duplicate submit sends only one request');
  check(await page.evaluate(()=>!document.querySelector('.candidate-form')&&document.querySelector('.candidate-detail h2').textContent==='保留中的修改'&&location.hash.includes('candidate_id=118')),'save restores selected detail');
  await page.getByRole('button',{name:'修改日程',exact:true}).click();await idle(page);await page.locator('#field-title').fill('放弃这次修改');
  await page.evaluate(()=>{void navigate('home');});await idle(page);await page.getByRole('button',{name:'放弃修改',exact:true}).click();await idle(page);
  check(await page.evaluate(()=>route==='home'),'explicit discard permits navigation');
  await page.evaluate(()=>navigate('task/1?candidate_id=119'));await idle(page);await page.getByRole('button',{name:'原始内容',exact:true}).click();await idle(page);
  check((await page.locator('#dialog-body').textContent()).includes('一条记录包含多项安排'),'original content preserved');
  const click=async(x,y)=>{await page.cdp.send('Input.dispatchMouseEvent',{type:'mousePressed',x,y,button:'left',clickCount:1});await page.cdp.send('Input.dispatchMouseEvent',{type:'mouseReleased',x,y,button:'left',clickCount:1});await sleep(250);};
  const rect=await page.evaluate(()=>document.querySelector('#dialog').getBoundingClientRect().toJSON());
  await click(rect.left+3,rect.top+70);check(await page.evaluate(()=>document.querySelector('#dialog').open),'inside dialog padding does not close');
  await page.cdp.send('Input.dispatchMouseEvent',{type:'mousePressed',x:rect.left+4,y:rect.top+70,button:'left',clickCount:1});await page.cdp.send('Input.dispatchMouseEvent',{type:'mouseReleased',x:5,y:5,button:'left',clickCount:1});await sleep(250);
  check(await page.evaluate(()=>document.querySelector('#dialog').open),'inside-to-backdrop drag does not close');
  await click(5,5);check(await page.evaluate(()=>!document.querySelector('#dialog').open),'actual backdrop click closes modal');
  await page.evaluate(()=>candidateEditor(currentTask.candidates.find(i=>i.id===119)));await idle(page);
  check(await page.evaluate(()=>{const f=document.querySelector('#dialog .editor-actions').getBoundingClientRect(),d=document.querySelector('#dialog').getBoundingClientRect();return f.top>=d.top&&f.bottom<=d.bottom&&document.querySelector('#close-dialog').offsetWidth>=48;}),'modal save footer and enlarged close target fit');
  await page.screenshot({path:path.join(out,'desktop-modal.png'),fullPage:false});
  await page.setViewportSize({width:320,height:844});await page.evaluate(()=>{const sizes=[...document.querySelectorAll('#dialog *')].map(el=>[el,parseFloat(getComputedStyle(el).fontSize)]);for(const [el,size]of sizes){el.dataset.originalFont=el.style.fontSize;el.style.fontSize=size*1.5+'px';}});await idle(page);
  check(await page.evaluate(()=>{const a=document.querySelector('#dialog .editor-actions').getBoundingClientRect(),b=document.querySelector('#dialog').getBoundingClientRect();return a.top>=b.top&&a.bottom<=b.bottom&&document.documentElement.scrollWidth<=innerWidth&&[...document.querySelectorAll('#dialog .editor-actions button')].every(el=>el.offsetHeight<100&&el.scrollWidth<=el.clientWidth);}),'modal 150% text footer is reachable and labels stay horizontal');
  await page.screenshot({path:path.join(out,'320-font150-modal.png'),fullPage:false});await page.evaluate(()=>{for(const el of document.querySelectorAll('[data-original-font]')){el.style.fontSize=el.dataset.originalFont;delete el.dataset.originalFont;}});await page.setViewportSize({width:1280,height:900});await idle(page);
  await page.locator('#field-title').fill('模态未保存的草稿');
  for(const cancel of ['cancel','x','escape','backdrop']){
   await page.getByRole('button',{name:'删除',exact:true}).click();await idle(page);
   if(cancel==='cancel')await page.getByRole('button',{name:'取消',exact:true}).click();
   if(cancel==='x')await page.locator('#close-dialog').click();
   if(cancel==='escape')await page.cdp.send('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});
   if(cancel==='backdrop')await click(5,5);await idle(page);
   check(await page.evaluate(()=>document.querySelector('#dialog').open&&!document.querySelector('.candidate-form').hidden&&document.querySelector('#field-title').value==='模态未保存的草稿'),'cancel modal delete by '+cancel+' restores existing draft');
  }
  for(const close of ['cancel','x','escape','backdrop']){
   if(close==='cancel')await page.getByRole('button',{name:'取消',exact:true}).click();
   if(close==='x')await page.locator('#close-dialog').click();
   if(close==='escape')await page.cdp.send('Input.dispatchKeyEvent',{type:'keyDown',key:'Escape',code:'Escape',windowsVirtualKeyCode:27});
   if(close==='backdrop')await click(5,5);
   await idle(page);check(await page.evaluate(()=>document.querySelector('#dialog').open&&document.querySelector('#dialog-title').textContent==='放弃未保存的修改？'),'modal '+close+' protects draft');
   await page.getByRole('button',{name:'继续编辑',exact:true}).click();await idle(page);
   check(await page.evaluate(()=>document.querySelector('#field-title').value==='模态未保存的草稿'),'modal draft node retained after '+close);
  }
  control.delaySave=600;await page.evaluate(()=>document.querySelector('.candidate-form').requestSubmit());await sleep(80);await page.locator('#close-dialog').click();
  check(await page.evaluate(()=>document.querySelector('#dialog').open&&!document.querySelector('.candidate-form').hidden),'pending modal save cannot be dismissed');await idle(page);control.delaySave=0;
  items.find(i=>i.id===101).completed=true;items.find(i=>i.id===102).category='homework';items.find(i=>i.id===102).due=Date.now()-3600000;
  await page.evaluate(()=>render());await idle(page);
  check(await page.evaluate(()=>document.querySelector('.master-item:last-child').dataset.candidate==='101'&&document.querySelector('.master-item[data-candidate="102"]').classList.contains('state-overdue')),'completed items last and overdue has explicit state');
  for(const width of [390,320])for(const dark of [false,true]){
   await page.setViewportSize({width,height:844});await page.evaluate(dark=>{document.body.classList.toggle('dark',dark);window.scrollTo(0,0);},dark);await idle(page);
   check(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth&&getComputedStyle(document.querySelector('.detail-master')).display==='none'),'narrow target opens detail without horizontal overflow');
   await page.screenshot({path:path.join(out,width+'-'+(dark?'dark':'light')+'.png'),fullPage:false});
   await page.getByRole('button',{name:'日程列表',exact:true}).click();await idle(page);
   check(await page.evaluate(()=>getComputedStyle(document.querySelector('.detail-reader')).display==='none'&&getComputedStyle(document.querySelector('.master-list')).overflowY==='visible'),'narrow list has natural page scroll');
   check(await page.evaluate(()=>{const r=document.querySelector('.master-item[aria-current=true]').getBoundingClientRect(),d=document.querySelector('.bottom').getBoundingClientRect();return r.top>=0&&r.bottom<=d.top;}),'return to narrow list keeps selected row visible');
   await page.evaluate(()=>render());await idle(page);check(await page.evaluate(()=>getComputedStyle(document.querySelector('.detail-reader')).display==='none'),'refresh retains narrow list mode');
   await page.evaluate(()=>document.querySelector('.master-item[data-candidate="119"]').click());await idle(page);
  }
  await page.cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
  await page.getByRole('button',{name:'修改日程',exact:true}).click();await idle(page);
  for(const width of [390,320]){
   await page.setViewportSize({width,height:844});
   await page.evaluate(()=>{const sizes=[...document.querySelectorAll('#workspace *')].map(el=>[el,parseFloat(getComputedStyle(el).fontSize)]);for(const [el,size]of sizes){el.dataset.originalFont=el.style.fontSize;el.style.fontSize=size*1.5+'px';}});
   await idle(page);await page.screenshot({path:path.join(out,width+'-font150-inline.png'),fullPage:false});
   check(await page.evaluate(()=>{const r=document.querySelector('.editor-actions').getBoundingClientRect(),d=document.querySelector('.bottom').getBoundingClientRect();return document.documentElement.scrollWidth<=innerWidth&&r.top>=0&&r.bottom<=d.top&&[...document.querySelectorAll('.editor-actions button')].every(b=>b.offsetHeight<100&&b.scrollWidth<=b.clientWidth);}),'150% real text retains readable sticky actions above Dock');
   await page.evaluate(()=>{for(const el of document.querySelectorAll('[data-original-font]')){el.style.fontSize=el.dataset.originalFont;delete el.dataset.originalFont;}});
  }
  await page.getByRole('button',{name:'取消',exact:true}).click();await idle(page);
  await page.evaluate(()=>navigate('task/2?candidate_id=201'));await idle(page);
  check(await page.getByRole('button',{name:'原始内容',exact:true}).count()===0,'managed source hides original capture');
  await page.getByRole('button',{name:'修改作业',exact:true}).click();await idle(page);
  check(await page.evaluate(()=>document.querySelector('#field-title').readOnly&&document.querySelector('#field-category').disabled&&document.querySelector('#field-due').readOnly&&document.querySelector('#field-description').readOnly&&!document.querySelector('#field-start').readOnly),'managed source fields readonly, execution editable');
  await page.getByRole('button',{name:'取消',exact:true}).click();await idle(page);
  await page.evaluate(()=>navigate('task/3'));await idle(page);check(await page.getByRole('button',{name:'手动建立日程',exact:true}).count()===1,'empty capture retains create');
  check(!errors.length,errors.join('\n'));fs.writeFileSync(path.join(out,'result.json'),JSON.stringify({assertions:count,errors},null,2));console.log('Web master detail: PASS ('+count+' assertions)');
 }finally{await browser.close();server.close();}
}
if(require.main===module){if(process.argv.includes('--serve')){const {server}=createServer();server.listen(18789,'127.0.0.1',()=>console.log('Isolated fixture http://127.0.0.1:18789'));}else run().catch(e=>{console.error(e);process.exitCode=1;});}
module.exports={createServer};
