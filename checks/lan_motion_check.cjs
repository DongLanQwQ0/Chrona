/* Real Edge animation frames with a local mock phone; no Android/device claims. */
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert');
const {chromium}=require('./lan_browser_cdp.cjs');
const root=path.resolve(__dirname,'../app/src/main/assets/lan'),out=path.resolve(__dirname,'../build/lan-motion-checks');
fs.mkdirSync(out,{recursive:true});
let heldHome=null,holdHome=false;
const source=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://localhost'),json=value=>{res.setHeader('Content-Type','application/json');res.end(JSON.stringify(value));};
 if(url.pathname==='/api/session')return json({csrf:'test',zone:'Asia/Shanghai',palette:{background:'#f4f6f8',primary:'#486a85'},calendarAllowed:true});
 if(url.pathname==='/api/home'){const send=()=>json({revision:1,items:[],calendar:[],date:'2026-10-05',review:0,failed:0});if(holdHome){heldHome=send;return;}return send();}
 if(['/api/schedule','/api/inbox'].includes(url.pathname))return json({revision:2,total:60,pageSize:30,items:Array.from({length:30},(_,i)=>({id:i+1,taskId:i+1,title:'日程 '+(i+1),text:'原始记录 '+(i+1),status:'ready',category:'event',start:1791072000000,created:1791072000000,zone:'Asia/Shanghai',location:'图书馆'}))});
 const file={'/':'index.html','/app.js':'app.js','/style.css':'style.css'}[url.pathname];if(!file){res.writeHead(404);return res.end();}res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(path.join(root,file)));
});
const delay=ms=>new Promise(r=>setTimeout(r,ms));
async function until(fn){for(let i=0;i<100;i++){if(await fn())return;await delay(25);}throw new Error('Condition timed out');}
(async()=>{
 await new Promise(r=>source.listen(0,'127.0.0.1',r));let browser;
 try{
  browser=await chromium.launch({headless:true,executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
  const page=await browser.newPage({viewport:{width:390,height:680}}),errors=[],evidence={};page.on('pageerror',e=>errors.push(String(e)));
  await page.goto(`http://127.0.0.1:${source.address().port}/`);await page.getByRole('heading',{name:'今日安排'}).waitFor();await delay(300);
  evidence.route=await page.evaluate(async()=>{await navigate('schedule');const host=document.querySelector('#content'),animation=activeMotion.get(host);const a=getComputedStyle(host).transform;await new Promise(r=>setTimeout(r,65));const b=getComputedStyle(host).transform;return{a,b,keyframes:animation.effect.getKeyframes()};});
  assert.notEqual(evidence.route.a,evidence.route.b,'route moves through actual intermediate frames');
  await page.screenshot({path:path.join(out,'schedule-enter-mobile.png')});await delay(260);
  evidence.dock=await page.evaluate(async()=>{const highlight=document.querySelector('.dock-highlight');document.querySelectorAll('[data-page]').forEach(b=>b.classList.toggle('active',b.dataset.page==='inbox'));updateDock();await new Promise(r=>setTimeout(r,40));const before=getComputedStyle(highlight).transform;document.querySelectorAll('[data-page]').forEach(b=>b.classList.toggle('active',b.dataset.page==='schedule'));updateDock();return{before,after:activeMotion.get(highlight).effect.getKeyframes()[0].transform};});
  assert.equal(evidence.dock.before,evidence.dock.after,'dock reverses from current presentation');
  const reveal=await page.evaluate(async()=>{window.revealTarget=document.querySelector('.reveal-pending');revealTarget.scrollIntoView({block:'center'});await new Promise(r=>setTimeout(r,60));return{revealed:!revealTarget.classList.contains('reveal-pending'),opacity:getComputedStyle(revealTarget).opacity};});
  assert(reveal.revealed&&Number(reveal.opacity)<1,'scroll starts a visible reveal animation');await delay(320);
  await page.evaluate(()=>scrollTo(0,0));await delay(50);await page.evaluate(()=>revealTarget.scrollIntoView({block:'center'}));await delay(50);
  assert.equal(await page.evaluate(()=>revealTarget.getAnimations().length),0,'scrolling back does not replay reveal');
  await page.evaluate(()=>scrollTo(0,0));
  evidence.dialog=await page.evaluate(async()=>{capture();await new Promise(r=>setTimeout(r,40));const dialog=document.querySelector('#dialog'),before=getComputedStyle(dialog).opacity;closeDialog();const after=activeMotion.get(dialog).effect.getKeyframes()[0].opacity;await new Promise(r=>setTimeout(r,35));showDialog('快速重开',body=>body.append(document.createTextNode('保留')));return{before,after};});
  assert(Math.abs(Number(evidence.dialog.before)-Number(evidence.dialog.after))<.03,'dialog close continues current opacity');await delay(300);assert(await page.locator('#dialog').evaluate(e=>e.open&&!e.inert),'old close completion cannot close reopened dialog');
  await page.evaluate(()=>{closeDialog(true);candidateEditor({id:0,taskId:1,title:'动效检查',category:'event',start:null,end:null,zone:'Asia/Shanghai',location:'',description:'',allDay:false,reminder:null,needsConfirmation:true});});await delay(250);
  evidence.select=await page.evaluate(async()=>{const trigger=document.querySelector('#dialog .select-trigger');trigger.click();await new Promise(r=>setTimeout(r,35));const menu=openSelect.menu,before=getComputedStyle(menu).opacity;closeSelect();const closed=activeMotion.get(menu).effect.getKeyframes()[0].opacity;trigger.click();return{before,closed,reused:menu===openSelect.menu,count:document.querySelectorAll('.select-menu').length};});
  assert(evidence.select.reused&&evidence.select.count===1,'quick reopen reuses the single interactive dropdown');assert(Math.abs(Number(evidence.select.before)-Number(evidence.select.closed))<.03);
  await page.evaluate(()=>{closeSelect();closeDialog();});await page.cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});await delay(60);
  assert(await page.evaluate(()=>!document.querySelector('#dialog').open&&!document.querySelector('.select-menu')&&[...runningMotions].every(a=>a.playState!=='running')),'enabling reduced motion finishes close cleanup');
  await page.cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'no-preference'}]});
  evidence.disclosure=await page.evaluate(async()=>{const details=disclosure('内容','文本\n'.repeat(30));document.querySelector('#content').append(details);details.querySelector('summary').click();await new Promise(r=>setTimeout(r,40));const panel=details.lastChild,before=panel.getBoundingClientRect().height;details.querySelector('summary').click();return{before,after:parseFloat(activeMotion.get(panel).effect.getKeyframes()[0].height)};});assert(Math.abs(evidence.disclosure.before-evidence.disclosure.after)<1);
  await page.cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});await until(()=>page.evaluate(()=>!document.querySelector('details').open));assert(await page.evaluate(()=>reducedMotion.matches),'reduced motion settles disclosure closed');
  await page.cdp.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'no-preference'}]});
  await page.evaluate(async()=>{await navigate('schedule');const input=document.querySelector('input[type=search]');input.value='保留焦点';input.focus();await render({local:true,direction:1});});
  assert.equal(await page.evaluate(()=>document.activeElement.getAttribute('aria-label')),'搜索日程');assert.equal(await page.evaluate(()=>document.querySelector('.filters').getAnimations().length),0,'filter form stays still');await delay(300);
  await page.evaluate(async()=>{await navigate('home');});await delay(300);
  await page.evaluate(()=>{window.pollAnimations=0;const original=Element.prototype.animate;Element.prototype.animate=function(...args){pollAnimations++;return original.apply(this,args);};});
  await delay(10500);assert.equal(await page.evaluate(()=>pollAnimations),0,'actual ten-second polling never starts animations');
  holdHome=true;await page.evaluate(()=>{navigate('home');});await until(()=>!!heldHome);await page.evaluate(()=>navigate('schedule'));heldHome();heldHome=null;holdHome=false;await delay(300);
  assert.equal(await page.evaluate(()=>document.querySelector('#content h1').textContent),'日程','late route response cannot replace current route');assert.equal(await page.evaluate(()=>revision),2,'late response cannot overwrite current revision');
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'390px layout has no page overflow');assert.deepEqual(errors,[]);
  await page.screenshot({path:path.join(out,'schedule-settled-mobile.png'),fullPage:true});fs.writeFileSync(path.join(out,'evidence.json'),JSON.stringify({...evidence,pageErrors:errors},null,2));
  console.log('PASS: real Edge intermediate frames, dock/dialog/dropdown/disclosure reversals, reduced-motion live cleanup, one-time observer reveal, actual 10s idle polling, stale route revision, focus and 390px layout.');
 }finally{if(heldHome)heldHome();if(browser)await browser.close();await new Promise(r=>source.close(r));}
})().catch(e=>{console.error(e);process.exitCode=1;});
