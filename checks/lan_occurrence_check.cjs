/* Actual web render/physical pointer paths against an isolated API transport, no phone or AI. */
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert');
const assets=path.resolve(__dirname,'../app/src/main/assets/lan'),out=path.resolve(__dirname,'../build/occurrence-web-evidence');
const original=Date.parse('2026-10-30T19:00:00+08:00'),requests=[];let revision=1;
const common={category:'event',zone:'Asia/Shanghai',allDay:false,location:'北四-111',description:'主题：机械设计与3D打印。请假请于17点前联系负责人。',reminder:15,needsConfirmation:true,calendarLinked:false,mergedSources:[],baseline:'fixture-baseline',due:null,effectiveDue:null};
const proposal={state:'pending',original,original_date:'2026-10-30',targetTaskId:2,targetCandidateId:2};
let source={...common,id:1,taskId:1,title:'无协内训 · 机械设计与3D打印',start:original,end:original+5400000,occurrenceUpdate:{...proposal}},series={...common,id:2,taskId:2,title:'无协每周内训',start:original,end:original+3600000,location:'北四-212',calendarLinked:true,needsConfirmation:false,recurrence:{frequency:'weekly',interval:1,weekdays:[5],until:null,count:null},repeatLabel:'每周五'};
const server=http.createServer(async(req,res)=>{try{
 const url=new URL(req.url,'http://localhost'),file={'/':'index.html','/app.js':'app.js','/icons.js':'icons.js','/brand.svg':'brand.svg','/style.css':'style.css'}[url.pathname];res.setHeader('Cache-Control','no-store');
 if(file){res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':file.endsWith('.svg')?'image/svg+xml':'text/html');res.end(fs.readFileSync(path.join(assets,file)));return;}
 let raw='';for await(const chunk of req)raw+=chunk;const body=raw?JSON.parse(raw):null;requests.push({path:url.pathname,body});let data;
 if(url.pathname==='/api/session')data={csrf:'fixture',zone:'Asia/Shanghai',calendarAllowed:true,palette:{background:'#f4f6f8',surface:'#fff',text:'#202c36',muted:'#70808e',primary:'#008582',outline:'#d7e0e7'}};
 else if(url.pathname==='/api/task'){const id=Number(url.searchParams.get('id'));data={revision,task:{id,text:'无协第二次内训公告',source:'隔离验收',created:original,status:'needs_review'},candidates:[id===1?source:series],images:[],files:[],mergedTargets:[]};}
 else if(url.pathname==='/api/candidate/occurrences')data={revision,series,items:[{...series,occurrenceAt:original,location:'北四-111'}]};
 else if(url.pathname==='/api/candidate/repeat'){Object.assign(series,body,{id:2,taskId:2});data={revision:++revision,candidate:series};}
 else if(url.pathname==='/api/candidate/save'){Object.assign(source,body,{id:1,taskId:1});data={revision:++revision};}
 else if(url.pathname==='/api/candidate/publish'){Object.assign(source,body,{id:1,taskId:1,record:true,occurrenceUpdate:{...proposal,state:'applied'}});data={revision:++revision};}
 else if(url.pathname==='/api/candidate/choose-new'){source.occurrenceUpdate.state='new';data={revision:++revision};}
 else{res.statusCode=404;data={error:url.pathname};}res.setHeader('Content-Type','application/json');res.end(JSON.stringify(data));
 }catch(error){res.writeHead(500).end(JSON.stringify({error:error.message}));}});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function idle(page){await page.evaluate(async()=>{while(pending)await new Promise(r=>setTimeout(r,10));});await sleep(300);}
async function tap(page,text,scope='document'){
 const point=await page.evaluate(`(()=>{const el=[...(${scope}).querySelectorAll('button')].find(e=>e.textContent.trim()===${JSON.stringify(text)}&&!e.disabled);if(!el)throw new Error('Missing enabled button: '+${JSON.stringify(text)});el.scrollIntoView({block:'center'});const r=el.getBoundingClientRect();return {x:r.x+r.width/2,y:r.y+r.height/2};})()`);
 for(const type of ['mousePressed','mouseReleased'])await page.cdp.send('Input.dispatchMouseEvent',{type,...point,button:'left',clickCount:1});await idle(page);
}
async function run(){fs.mkdirSync(out,{recursive:true});await new Promise(r=>server.listen(0,'127.0.0.1',r));const browser=await require('./lan_browser_cdp.cjs').chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});let checks=0;const errors=[],check=(v,m)=>{assert(v,m);checks++;};
 try{const page=await browser.newPage({viewport:{width:1280,height:900}});page.on('pageerror',e=>errors.push(String(e)));const url='http://127.0.0.1:'+server.address().port+'/#task/1?candidate_id=1';await page.goto(url);await idle(page);
 check(await page.evaluate(()=>document.body.textContent.includes('更新本次 · 2026-10-30')),'matched preview visible');
 await tap(page,'修改日程');await page.locator('#field-location').fill('北四-113');await tap(page,'保存草稿');check(requests.some(r=>r.path==='/api/candidate/save'&&r.body.location==='北四-113'&&r.body.baseline==='fixture-baseline'),'draft endpoint and conflict baseline');
 await tap(page,'修改日程');check(await page.locator('#field-location').evaluate(el=>el.value)==='北四-113','reopened saved draft');await tap(page,'更新本次');check(requests.at(-2)?.path==='/api/candidate/publish'||requests.some(r=>r.path==='/api/candidate/publish'),'explicit update endpoint');
 check(await page.evaluate(()=>document.body.textContent.includes('已更新本次')&&!document.querySelector('.detail-actions')?.textContent.includes('修改日程')),'applied outcome read only');
 await tap(page,'查看本次日程');await page.locator('#field-location').waitFor();check(await page.evaluate(()=>document.querySelector('#dialog .candidate-form')!==null),'existing full editor opens target occurrence');
 await page.locator('#field-location').fill('北四-114');await tap(page,'更新本次','document.querySelector("#dialog")');check(requests.some(r=>r.path==='/api/candidate/repeat'&&r.body.action==='edit'&&r.body.original===original&&r.body.location==='北四-114'),'full form updates original identity via shared repeat action');
 const before=requests.filter(r=>r.path==='/api/candidate/repeat').length;await page.evaluate(()=>openOccurrence(2,2,Date.parse('2026-10-30T19:00:00+08:00')));await idle(page);await tap(page,'取消','document.querySelector("#dialog")');check(requests.filter(r=>r.path==='/api/candidate/repeat').length===before,'cancel does not mutate');
 for(const width of [1280,390])for(const theme of ['light','dark']){
  source={...source,record:false,needsConfirmation:true,occurrenceUpdate:{...proposal}};await page.setViewportSize({width,height:900});await page.evaluate(()=>navigate('task/1?candidate_id=1'));await idle(page);
  await page.evaluate((theme,width)=>{document.documentElement.style.fontSize=width<500?'150%':'100%';themeOverride=false;session.palette=theme==='dark'?{...session.palette,background:'#102422',surface:'#1d3431',text:'#dce5e1',muted:'#b0ccc5',primary:'#80d5cd',outline:'#536966'}:{...session.palette,background:'#f4f6f8',surface:'#fff',text:'#202c36',muted:'#70808e',primary:'#008582',outline:'#d7e0e7'};applyPalette(session.palette);},theme,width);await tap(page,'修改日程');await sleep(400);
  const contrast=await page.evaluate(()=>{const button=[...document.querySelectorAll('button')].find(e=>e.textContent.trim()==='保存草稿');const c=getComputedStyle(button);const parse=v=>{const canvas=document.createElement('canvas'),ctx=canvas.getContext('2d');ctx.fillStyle=v;ctx.fillRect(0,0,1,1);return [...ctx.getImageData(0,0,1,1).data].slice(0,3).map(n=>{n/=255;return n<=.04045?n/12.92:((n+.055)/1.055)**2.4;});};const l=v=>{const a=parse(v);return a[0]*.2126+a[1]*.7152+a[2]*.0722;};const a=l(c.color),b=l(c.backgroundColor);return {foreground:c.color,background:c.backgroundColor,ratio:(Math.max(a,b)+.05)/(Math.min(a,b)+.05)};});check(contrast.ratio>=4.5,'stable themed primary button contrast: '+JSON.stringify(contrast));console.log(JSON.stringify({width,theme,contrast}));
  check(await page.evaluate(()=>{const form=document.querySelector('.candidate-form');return form.getBoundingClientRect().left>=0&&form.getBoundingClientRect().right<=innerWidth+1&&document.documentElement.scrollWidth<=innerWidth+1;}),'form within actual viewport');
  await page.screenshot({path:path.join(out,`form-${width}-${theme}.png`),fullPage:false});await tap(page,'取消');
 }
 check(!requests.some(r=>r.path==='/api/candidate/create'),'matched flow never calls create');check(errors.length===0,'no runtime errors: '+errors.join('\n'));console.log(JSON.stringify({checks,renderings:4,physicalPointer:true,transport:'isolated API fixture'}));
 }finally{await browser.close();await new Promise(r=>server.close(r));}}
run().catch(error=>{console.error(error);process.exitCode=1;server.close();});
