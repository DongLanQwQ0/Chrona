/* Production controls in the existing Edge profile. Interaction/geometry only, no screenshots. */
const assert=require('assert'),fs=require('fs'),path=require('path');
const {createServer}=require('./lan_detail_navigation_check.cjs');
const {chromium}=require('./lan_browser_cdp.cjs');
const out=path.resolve(__dirname,'../build/lan-dock-repair');fs.mkdirSync(out,{recursive:true});
const delay=ms=>new Promise(resolve=>setTimeout(resolve,ms));
async function idle(page){await page.evaluate(async()=>{while(pending)await new Promise(resolve=>setTimeout(resolve,10));});await delay(550);}
(async()=>{const {server}=createServer();await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));let browser;const evidence={};try{
 browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});const page=await browser.newPage({viewport:{width:1280,height:900}});
 await page.goto('http://127.0.0.1:'+server.address().port+'/');await page.getByRole('heading',{name:'首页',exact:true}).waitFor();await idle(page);
 evidence.initial=await geometry(page);
 await page.evaluate(()=>navigate('schedule'));await idle(page);await page.evaluate(()=>navigate('home'));await idle(page);evidence.returned=await geometry(page);
 await page.evaluate(()=>navigate('schedule'));await idle(page);
 const track=await page.evaluate(()=>{const r=document.querySelector('.selection-bar').getBoundingClientRect();return{x:r.left+r.width*.18,to:r.left+r.width*.62,y:r.top+r.height/2};});
 await page.cdp.send('Input.dispatchMouseEvent',{type:'mousePressed',button:'left',clickCount:1,x:track.x,y:track.y});await page.cdp.send('Input.dispatchMouseEvent',{type:'mouseMoved',button:'left',buttons:1,x:track.to,y:track.y});
 evidence.selectionMove=await page.evaluate(()=>{const r=document.querySelector('.selection-highlight').getBoundingClientRect();return{center:r.left+r.width/2};});
 await page.cdp.send('Input.dispatchMouseEvent',{type:'mouseReleased',button:'left',clickCount:1,x:track.to,y:track.y});
 evidence.selectionPointer=track.to;
 await idle(page);
 for(const size of [{width:1280,height:900},{width:414,height:896},{width:896,height:414}]){
  await page.setViewportSize(size);await page.evaluate(()=>navigate('home'));await idle(page);
  const home=await geometry(page);assert(home.gap>=9,'plus has a separate slot '+JSON.stringify({size,home}));
  const hit=await page.evaluate(()=>{const button=document.querySelector('#capture'),r=button.getBoundingClientRect(),hit=document.elementFromPoint(r.left+r.width/2,r.top+r.height/2);return{correct:hit?.closest('#capture')===button,element:hit?.tagName,id:hit?.id,className:hit?.className,route,hidden:button.inert,railClass:dockNav.className,state:dockTrack.state};});assert(hit.correct,'plus owns its hit region '+JSON.stringify({size,home,hit}));
  await page.evaluate(()=>navigate('schedule'));await idle(page);
  for(const selector of ['.bottom nav','.selection-bar']){
   const points=await page.evaluate(selector=>{const bar=document.querySelector(selector),buttons=[...bar.querySelectorAll(':scope>button')],a=buttons[0].getBoundingClientRect(),b=buttons.at(-1).getBoundingClientRect();bar.scrollIntoView({block:'center'});const r=bar.getBoundingClientRect();return{x:r.left+r.width*.3,y:r.top+r.height/2,first:a.left+a.width/2,last:b.left+b.width/2};},selector);
   const initial=await page.evaluate(()=>({route,tab:scheduleTab}));
   await page.evaluate(()=>{window.pointerTrace=[];if(!window.pointerTraceInstalled){window.pointerTraceInstalled=true;for(const type of ['pointerdown','pointermove','pointerup','pointercancel'])document.addEventListener(type,event=>pointerTrace.push({type,x:event.clientX,y:event.clientY,id:event.pointerId,target:event.target.className}),true);}});
   await page.cdp.send('Emulation.setTouchEmulationEnabled',{enabled:true,maxTouchPoints:2});
   const touch=(type,x,y)=>page.cdp.send('Input.dispatchTouchEvent',{type,touchPoints:type==='touchCancel'||type==='touchEnd'?[]:[{x,y}]});
   await touch('touchStart',points.x,points.y);await delay(350);
   for(const fraction of [.65,.35,.7,.4]){
    const position=await page.evaluate(({selector,fraction})=>{const bar=document.querySelector(selector),r=bar.getBoundingClientRect();return{x:r.left+r.width*fraction,y:r.top+r.height/2};},{selector,fraction});
    await touch('touchMove',position.x,position.y);
    await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
    const measured=await page.evaluate(selector=>{const bar=document.querySelector(selector),r=bar.querySelector('.dock-highlight,.selection-highlight').getBoundingClientRect();return{center:r.left+r.width/2,p:navigationTracks.get(bar).state.p,trace:pointerTrace.slice(-8)};},selector);
    assert(Math.abs(measured.center-position.x)<=2,'real touch move frame follows without spring/CSS lag '+JSON.stringify({selector,size,position,measured}));
   }
   await touch('touchMove',0,points.y);
   await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
   assert(await page.evaluate(selector=>{const bar=document.querySelector(selector),first=bar.querySelector('button');return Math.abs(navigationTracks.get(bar).state.p-(first.offsetLeft+first.offsetWidth/2))<.01;},selector),'held pointer is bounded to first option');
   await touch('touchMove',size.width-1,points.y);
   await page.evaluate(()=>new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve))));
   assert(await page.evaluate(selector=>{const bar=document.querySelector(selector),last=[...bar.querySelectorAll('button')].at(-1);return Math.abs(navigationTracks.get(bar).state.p-(last.offsetLeft+last.offsetWidth/2))<.01;},selector),'held pointer is bounded to last option');
   await touch('touchCancel');await idle(page);
   assert.deepEqual(await page.evaluate(()=>({route,tab:scheduleTab})),initial,'cancel does not navigate or select');
   assert(await page.evaluate(selector=>{const s=navigationTracks.get(document.querySelector(selector)).state;return Math.abs(s.s-1)<.01&&Math.abs(s.bar-1)<.01;},selector),'cancel restores resting sizes');
   await page.cdp.send('Emulation.setTouchEmulationEnabled',{enabled:false});
  }
  await page.evaluate(()=>navigate('home'));await idle(page);evidence['returned-'+size.width]=await geometry(page);
  assert(evidence['returned-'+size.width].gap>=9,'return home retains final plus slot');
 }
 await page.setViewportSize({width:1280,height:900});await page.evaluate(()=>navigate('schedule'));await idle(page);
 const identity=await page.evaluate(async()=>{const bar=document.querySelector('.selection-bar'),track=navigationTracks.get(bar);await refreshSession();return bar===document.querySelector('.selection-bar')&&track===navigationTracks.get(bar);});assert(identity,'poll retains the same live pointer controller');
 await page.evaluate(()=>document.querySelector('.selection-bar button[data-choice="2"]').focus());await page.cdp.send('Input.dispatchKeyEvent',{type:'keyDown',key:'Enter',code:'Enter',windowsVirtualKeyCode:13});await page.cdp.send('Input.dispatchKeyEvent',{type:'keyUp',key:'Enter',code:'Enter',windowsVirtualKeyCode:13});await idle(page);assert.equal(await page.evaluate(()=>scheduleTab),2,'keyboard selection still works after pointer cancellation');
 await page.evaluate(()=>document.querySelector('.selection-bar button[data-choice="1"]').focus());await page.cdp.send('Input.dispatchKeyEvent',{type:'keyDown',key:' ',code:'Space',windowsVirtualKeyCode:32});await page.cdp.send('Input.dispatchKeyEvent',{type:'keyUp',key:' ',code:'Space',windowsVirtualKeyCode:32});await idle(page);assert.equal(await page.evaluate(()=>scheduleTab),1,'Space still selects after pointer cancellation');
 for(const selector of ['.bottom nav','.selection-bar']){
  const initial=await page.evaluate(()=>({route,tab:scheduleTab})),point=await page.evaluate(selector=>{const bar=document.querySelector(selector);bar.scrollIntoView({block:'center'});const r=bar.getBoundingClientRect();return{x:r.left+r.width*.3,y:r.top+r.height/2};},selector);
  await page.cdp.send('Emulation.setTouchEmulationEnabled',{enabled:true,maxTouchPoints:2});
  await page.cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:31,...point}]});
  await page.cdp.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{id:31,...point},{id:32,x:point.x+45,y:point.y}]});
  await page.cdp.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});await idle(page);
  assert.deepEqual(await page.evaluate(()=>({route,tab:scheduleTab})),initial,'second finger cancels selection '+selector);
  await page.cdp.send('Emulation.setTouchEmulationEnabled',{enabled:false});
  await page.cdp.send('Input.dispatchMouseEvent',{type:'mousePressed',button:'left',buttons:1,x:point.x,y:point.y,clickCount:1});
  await page.evaluate(()=>window.dispatchEvent(new Event('blur')));
  await page.cdp.send('Input.dispatchMouseEvent',{type:'mouseReleased',button:'left',buttons:0,x:point.x,y:point.y,clickCount:1});await idle(page);
  assert.deepEqual(await page.evaluate(()=>({route,tab:scheduleTab})),initial,'blur cleanup does not commit '+selector);
 }
 assert(await page.evaluate(async()=>{const bar=document.querySelector('.selection-bar'),track=navigationTracks.get(bar);await navigate('inbox');return !bar.isConnected&&!navigationTracks.has(bar)&&navigationTracks.get(document.querySelector('.selection-bar'))!==track;}),'detached track releases its registered controller');
 await page.evaluate(()=>navigate('schedule'));await idle(page);
 assert(await page.evaluate(()=>!!navigationTracks.get(document.querySelector('.selection-bar'))),'remounted selection has a live controller');
 fs.writeFileSync(path.join(out,'track-evidence.json'),JSON.stringify(evidence,null,2));
 assert(evidence.returned.gap>=12,'independent plus overlaps navigation after returning home: '+JSON.stringify(evidence.returned));
 assert(Math.abs(evidence.selectionMove.center-track.to)<=2,'selection highlight does not follow pointer in the move frame: '+JSON.stringify(evidence.selectionMove));
 console.log('PASS shared navigation positioning and direct pointer tracking (no screenshots)');
}finally{if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));}})().catch(error=>{console.error(error);process.exitCode=1;});
async function geometry(page){return page.evaluate(()=>{const r=element=>{const b=element.getBoundingClientRect();return{x:b.left,y:b.top,right:b.right,bottom:b.bottom,width:b.width,height:b.height};},nav=r(document.querySelector('.bottom nav')),capture=r(document.querySelector('#capture')),bottom=r(document.querySelector('.bottom'));return{nav,capture,bottom,gap:capture.x-nav.right};});}
