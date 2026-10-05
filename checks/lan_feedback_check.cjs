/* Targeted real Edge checks with mock phone responses; no Android/device claims. */
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert');
const {chromium}=require('./lan_browser_cdp.cjs');
const root=path.resolve(__dirname,'../app/src/main/assets/lan');
const out=path.resolve(__dirname,'../build/lan-feedback-checks');
fs.mkdirSync(out,{recursive:true});
let requests=0,release=null;
const item={id:11,taskId:1,title:'反馈检查',category:'event',start:1791072000000,end:1791075600000,zone:'Asia/Shanghai',location:'',description:'',allDay:false,reminder:10,needsConfirmation:false,calendarLinked:false};
const source=http.createServer((req,res)=>{
 const url=new URL(req.url,'http://localhost');
 const json=(status,value)=>{res.writeHead(status,{'Content-Type':'application/json'});res.end(JSON.stringify(value));};
 if(url.pathname.startsWith('/api/candidate/')){requests++;release=(success=false)=>json(success?200:503,success?{ok:true}:{error:'模拟手机暂时不可用，请重试'});req.resume();return;}
 if(url.pathname==='/api/session')return json(200,{csrf:'test',zone:'Asia/Shanghai',palette:{background:'#f4f6f8',primary:'#486a85'},calendarAllowed:true});
 if(url.pathname==='/api/home')return json(200,{revision:1,items:[],calendar:[],date:'2026-10-05',review:0,failed:0});
 const file={'/':'index.html','/app.js':'app.js','/style.css':'style.css'}[url.pathname];
 if(!file){res.writeHead(404);return res.end();}
 res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':'text/html');res.end(fs.readFileSync(path.join(root,file)));
});
async function until(check){for(let i=0;i<100;i++){if(await check())return;await new Promise(r=>setTimeout(r,20));}throw new Error('Condition timed out');}
(async()=>{
 await new Promise(resolve=>source.listen(0,'127.0.0.1',resolve));
 let browser;
 try{
  browser=await chromium.launch({headless:true,executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
  const page=await browser.newPage({viewport:{width:390,height:600}}),errors=[];
  page.on('pageerror',error=>errors.push(String(error)));
  await page.goto(`http://127.0.0.1:${source.address().port}/`);
  await page.getByRole('heading',{name:'今日安排'}).waitFor();
  const open=()=>page.evaluate(`candidateEditor(${JSON.stringify(item)})`);
  const clickPublish=()=>page.evaluate(()=>[...document.querySelectorAll('#dialog button')].find(b=>b.textContent==='写入手机日历').click());
  await open();
  await page.locator('#field-title').fill('');await clickPublish();
  await page.evaluate(()=>new Promise(r=>setTimeout(r,100)));assert.equal(requests,0,'required title blocks publication');
  await page.locator('#field-title').fill('反馈检查');await page.locator('#field-reminder').fill('525601');await clickPublish();
  await page.evaluate(()=>new Promise(r=>setTimeout(r,100)));assert.equal(requests,0,'reminder maximum blocks publication');
  await page.locator('#field-reminder').fill('10');
  await page.evaluate(()=>{const b=[...document.querySelectorAll('#dialog button')].find(b=>b.textContent==='写入手机日历');b.click();b.click();document.querySelector('#dialog form').dispatchEvent(new Event('submit',{cancelable:true,bubbles:true}));});
  await until(()=>requests===1);
  assert(await page.evaluate(()=>[...document.querySelectorAll('#dialog button')].filter(b=>['保存','写入手机日历'].includes(b.textContent)).every(b=>b.disabled)),'both mutation buttons disabled');
  release();await page.locator('#dialog-feedback').waitFor();
  assert.equal(requests,1,'double click and concurrent submit send only one request');
  assert(await page.evaluate(()=>{const f=document.querySelector('#dialog-feedback'),r=f.getBoundingClientRect();return f.closest('dialog').open&&r.top>=0&&r.bottom<=innerHeight&&document.elementFromPoint(r.x+r.width/2,r.y+r.height/2)===f;}),'modal error visible and on top at mobile size');
  const ax=await page.cdp.send('Accessibility.getFullAXTree');
  assert(ax.nodes.some(n=>!n.ignored&&n.name?.value==='模拟手机暂时不可用，请重试'),'error is present in accessibility tree');
  assert(await page.evaluate(()=>[...document.querySelectorAll('#dialog button')].filter(b=>['保存','写入手机日历'].includes(b.textContent)).every(b=>!b.disabled)),'failure restores both buttons');
  await page.screenshot({path:path.join(out,'modal-error-mobile.png'),fullPage:true});
  await clickPublish();await until(()=>requests===2);release(true);await page.locator('#dialog').waitFor({state:'hidden'});
  assert(await page.locator('#dialog-feedback').isHidden(),'successful retry clears modal message');
  await open();assert.equal(await page.locator('#dialog-feedback').textContent(),'','reopened editor has no stale error');
  await page.evaluate(()=>{toast('关闭前提示');closeDialog(true);toast('页面提示');});
  assert(await page.locator('#dialog-feedback').isHidden());assert.equal(await page.locator('#toast').textContent(),'页面提示');
  await page.evaluate(()=>{session.calendarAllowed=false;});await open();
  await page.locator('#dialog').getByRole('button',{name:'保存',exact:true}).click();await until(()=>requests===3);release();await page.locator('#dialog-feedback').waitFor();
  assert(await page.evaluate(()=>[...document.querySelectorAll('#dialog button')].find(b=>b.textContent==='写入手机日历').disabled),'failed save preserves unavailable calendar permission');
  assert(await page.evaluate(()=>![...document.querySelectorAll('#dialog button')].find(b=>b.textContent==='保存').disabled),'failed save is retryable');
  await page.evaluate(()=>{showDialog('新弹窗',body=>body.append(document.createTextNode('内容')));});
  assert.equal(await page.locator('#dialog-feedback').textContent(),'','dialog replacement clears stale feedback');
  await page.evaluate(()=>{session.calendarAllowed=true;});await open();await clickPublish();await until(()=>requests===4);
  await page.evaluate(()=>{closeDialog(true);showDialog('下一次编辑',body=>{const input=document.createElement('input');input.id='new-draft';input.value='保留未保存内容';body.append(input);input.focus();});});
  release(true);await until(()=>page.evaluate(()=>pending===0));
  assert(await page.locator('#dialog').evaluate(element=>element.open),'old successful request cannot close new dialog');
  assert.equal(await page.locator('#new-draft').evaluate(element=>element.value),'保留未保存内容');
  await page.evaluate(()=>{session.calendarAllowed=true;});await open();await clickPublish();await until(()=>requests===5);
  await page.evaluate(()=>showDialog('新的编辑会话',body=>body.append(document.createTextNode('内容'))));
  release();await until(()=>page.evaluate(()=>pending===0));
  assert.equal(await page.locator('#dialog-feedback').textContent(),'','old failed request cannot write into new dialog');
  assert.deepEqual(errors,[]);
  console.log('PASS: required/range validation, slow duplicate submissions, visible accessible modal errors, successful retry, close/reopen cleanup, permission restoration; real Edge with mock phone responses.');
 }finally{if(browser)await browser.close();await new Promise(resolve=>source.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;});
