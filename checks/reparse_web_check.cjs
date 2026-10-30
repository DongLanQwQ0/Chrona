/* Actual LAN UI with mocked phone transport; no Android/provider or AI claims. */
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert');
const {chromium}=require('./lan_browser_cdp.cjs');
const root=path.resolve(__dirname,'../app/src/main/assets/lan');
const out=path.resolve(__dirname,'../build/reparse-web');fs.mkdirSync(out,{recursive:true});
let revision=12,posts=0,status='ready',failure=false;
const task=()=>({id:1,text:'测试原文保留',status,error:'',created:1792713600000,source:'模拟手机数据'});
const palette={background:'#f4f6f8',surface:'#ffffff',text:'#202c36',muted:'#70808e',primary:'#486a85',outline:'#d7e0e7'};
const server=http.createServer(async(req,res)=>{
 const url=new URL(req.url,'http://localhost'),file={'/':'index.html','/app.js':'app.js','/calendar.js':'calendar.js','/icons.js':'icons.js','/brand.svg':'brand.svg','/style.css':'style.css'}[url.pathname];
 if(file){res.setHeader('Content-Type',file.endsWith('js')?'text/javascript':file.endsWith('css')?'text/css':'text/html');res.end(fs.readFileSync(path.join(root,file)));return;}
 let data={},code=200;
 if(url.pathname==='/api/session')data={csrf:'test-csrf',zone:'Asia/Shanghai',palette,calendarAllowed:true,calendarReadable:true};
 else if(url.pathname==='/api/merge/preview')data={revision,groups:[],removed:0};
 else if(url.pathname==='/api/home')data={revision,items:[],recent:[],calendar:[],review:0,failed:0,date:'2026-10-23'};
 else if(url.pathname==='/api/task')data={revision,task:task(),candidates:[{id:11,taskId:1,title:'已写入测试活动',category:'event',start:1893456000000,end:1893459600000,zone:'Asia/Shanghai',location:'',description:'',calendarLinked:true,needsConfirmation:false}],mergedTargets:[],images:[],files:[],managedHomeworkCapture:false};
 else if(url.pathname==='/api/task/process'){
  let raw='';for await(const part of req)raw+=part;
  const body=JSON.parse(raw);assert.equal(body.id,'1');assert.equal(body.revision,revision);assert.equal(req.headers['x-chrona-csrf'],'test-csrf');posts++;
  revision++;if(failure){code=409;status='failed';data={error:'旧日程已清理，解析未能排队，请重试'};}else{status='queued';data={ok:true,revision};}
 }else{code=404;data={error:'mock route missing '+url.pathname};}
 res.writeHead(code,{'Content-Type':'application/json'});res.end(JSON.stringify(data));
});
(async()=>{
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const browser=await chromium.launch({headless:true,executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
 try{
  const page=await browser.newPage({viewport:{width:1280,height:900}}),errors=[];
  page.on('pageerror',error=>errors.push(String(error)));
  await page.goto(`http://127.0.0.1:${server.address().port}/`);
  await page.getByRole('heading',{name:'首页',exact:true}).waitFor();
  await page.evaluate(async()=>navigate('task/1'));
  async function open(){await page.getByRole('button',{name:'原始内容',exact:true}).click();await page.getByRole('button',{name:'AI 重新处理',exact:true}).click();}
  for(const dark of [false,true])for(const large of [false,true]){
   await page.setViewportSize({width:large?320:1280,height:900});
   await page.evaluate(({dark,large})=>{let style=document.querySelector('#test-large-font');if(!style){style=document.createElement('style');style.id='test-large-font';document.head.append(style);}style.textContent=large?'#dialog{font-size:22.5px}#dialog .subtle{font-size:19.5px}#dialog h2{font-size:31.5px}':'';applyPalette(dark?{background:'#161c22',surface:'#252d36',text:'#edf2f7',muted:'#b7c3cd',primary:'#a1c8ee',outline:'#526171'}:{background:'#f4f6f8',surface:'#ffffff',text:'#202c36',muted:'#70808e',primary:'#486a85',outline:'#d7e0e7'});},{dark,large});
   await open();
   assert.equal(posts,0,'opening never deletes');
   const geometry=await page.evaluate(()=>{
    const dialog=document.querySelector('#dialog'),r=dialog.getBoundingClientRect(),buttons=[...dialog.querySelectorAll('button')].filter(b=>['确认','取消'].includes(b.textContent));
    return {text:dialog.textContent,within:r.left>=-1&&r.right<=innerWidth+1&&r.top>=0&&r.bottom<=innerHeight+1,reachable:buttons.every(b=>{const x=b.getBoundingClientRect();return x.width>=48&&x.height>=40&&x.bottom<=innerHeight+1;})};
   });
   assert(geometry.within&&geometry.reachable,'dialog and actions fit');
   assert(await page.locator('#dialog-body > p').evaluate(element=>element.textContent==='这将替换已有日程'),'concise exact confirmation copy');
   await page.screenshot({path:path.join(out,`confirm-${dark?'dark':'light'}-${large?'320-large':'1280'}.png`)});
   await page.getByRole('button',{name:'取消',exact:true}).click();assert.equal(posts,0,'cancel never mutates');
  }
  await open();await page.getByRole('button',{name:'确认',exact:true}).click();
  assert.equal(posts,1);assert.equal(status,'queued');
  await page.getByRole('button',{name:'原始内容',exact:true}).click();
  assert(await page.getByRole('button',{name:'AI 重新处理',exact:true}).evaluate(e=>e.disabled),'queued disables reparse');
  await page.evaluate(()=>closeDialog());
  status='ready';failure=true;await page.evaluate(async()=>render());
  await open();await page.getByRole('button',{name:'确认',exact:true}).click();
  assert.equal(posts,2);assert.equal(await page.evaluate(()=>currentTask.task.status),'failed','failed scheduling refreshes real returned state');
  assert.equal(await page.evaluate(()=>revision),14,'failure refreshes revision for retry');
  assert((await page.locator('#dialog').textContent()).includes('解析未能排队'),'failure remains actionable');
  assert.deepEqual(errors,[]);console.log('PASS reparse LAN UI: linked enabled, four renders, cancel, confirmed POST, busy guard, scheduling failure refresh; mocked phone transport.');
 }finally{await browser.close();await new Promise(resolve=>server.close(resolve));}
})().catch(error=>{console.error(error);process.exitCode=1;});
