/* Deadline display/editor regression against the real LAN app and a local phone mock. */
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert');
const {chromium}=require('./lan_browser_cdp.cjs');
const root=path.resolve(__dirname,'..'),web=path.join(root,'app/src/main/assets/lan');
const server=http.createServer((req,res)=>{const file={'/':'index.html','/app.js':'app.js','/calendar.js':'calendar.js','/icons.js':'icons.js','/brand.svg':'brand.svg','/style.css':'style.css'}[req.url];if(!file)return res.writeHead(404).end();res.setHeader('Content-Type',file.endsWith('.js')?'text/javascript':file.endsWith('.css')?'text/css':file.endsWith('.svg')?'image/svg+xml':'text/html');res.end(fs.readFileSync(path.join(web,file)));});
const at=hour=>Date.parse(`2026-10-18T${hour}:00:00+08:00`);
const base={taskId:1,category:'task',zone:'Asia/Shanghai',location:'',description:'',allDay:false,reminder:null,needsConfirmation:false,calendarLinked:false,completed:false,record:false};
const items=[{...base,id:1,title:'结束作为截止',start:at('10'),end:at('11'),due:null,effectiveDue:at('11')},{...base,id:2,title:'明确截止优先',category:'homework',start:at('10'),end:at('11'),due:at('15'),effectiveDue:at('15')},{...base,id:3,title:'开始作为截止',start:at('13'),end:null,due:null,effectiveDue:at('13')}];
const requests=[],errors=[];
async function mock(route){const req=route.request(),url=new URL(req.url()),body=req.postDataJSON();requests.push({path:url.pathname,body});let data;
 if(url.pathname==='/api/session')data={csrf:'deadline-test',zone:'Asia/Shanghai',calendarAllowed:true,palette:{background:'#f4f6f8',primary:'#008582'}};
 else if(url.pathname==='/api/home')data={revision:1,items,calendar:[],date:'2026-10-18',review:0,failed:0};
 else if(url.pathname==='/api/schedule')data={revision:1,items,total:items.length,page:0,pageSize:30};
 else if(url.pathname==='/api/task')data={revision:1,task:{id:1,text:'待办',source:'测试',created:at('09'),status:'ready'},candidates:items,images:[],files:[]};
 else if(url.pathname==='/api/candidate/save')data={revision:2};
 else throw new Error('Unhandled '+url.pathname);
 await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});
}
async function idle(page){await page.evaluate(async()=>{while(pending)await new Promise(resolve=>setTimeout(resolve,15));await new Promise(resolve=>requestAnimationFrame(()=>requestAnimationFrame(resolve)));});}
(async()=>{
 const java=relative=>fs.readFileSync(path.join(root,'app/src/main/java/com/donglan/chrona',relative),'utf8');
 const serverSource=java('LanWebServer.java');
 assert(serverSource.includes('.put("due",item.dueAtMillis==null?JSONObject.NULL:item.dueAtMillis)')&&serverSource.includes('.put("effectiveDue",item.effectiveDueAtMillis()==null?JSONObject.NULL:item.effectiveDueAtMillis())'),'LAN preserves raw due and sends derived due separately');
 assert(serverSource.includes('.put("executionStart",item.executionStartAtMillis()'),'LAN exposes display interval separately');
 assert(serverSource.includes('body.has("due")?nullableLong(body,"due"):old.dueAtMillis'),'editor saves only raw due');
 assert(java('AgendaRepository.java').includes('candidate.reminderMinutesBefore,candidate.effectiveDueAtMillis()'),'calendar occurrence carries effective deadline metadata');
 assert(java('AgendaRepository.java').includes('candidate.executionStartAtMillis() : candidate.calendarStart()'),'calendar display uses default execution without changing provider input');
 assert.equal((java('AgendaReminder.java').match(/local\.effectiveDueAtMillis\(\)/g)||[]).length,2,'ordinary and snoozed reminder entries carry effective deadline');
 assert(/deadlineScope \? item.effectiveDueAtMillis\(\)\s*: item.hasDefaultExecutionInterval\(\) \? item.executionStartAtMillis\(\)/.test(java('DashboardActivity.java')),'deadline-scoped grouping uses effective deadline');
 await new Promise(resolve=>server.listen(0,'127.0.0.1',resolve));
 const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});
 try{
  const page=await browser.newPage({viewport:{width:1280,height:900}});page.on('pageerror',error=>errors.push(String(error)));await page.route('**/api/**',mock);await page.goto(`http://127.0.0.1:${server.address().port}/`);await page.getByRole('heading',{name:'首页'}).waitFor();await idle(page);
  let text=await page.evaluate(()=>document.querySelector('#content').textContent);assert(text.includes('11:00')&&text.includes('15:00')&&text.includes('13:00'),'home shows derived end, explicit due and derived start');
  await page.evaluate(()=>{Date.now=()=>Date.parse('2026-10-18T12:00:00+08:00');});
  await page.evaluate(()=>navigate('schedule'));await idle(page);text=await page.evaluate(()=>document.querySelector('#content').textContent);assert(text.includes('截止 ·')&&text.includes('11:00')&&text.includes('15:00'),'schedule shows effective deadlines');
  await page.evaluate(()=>navigate('task/1'));await idle(page);text=await page.evaluate(()=>document.querySelector('#content').textContent);assert(text.includes('11:00')&&text.includes('15:00'),'detail shows effective deadlines');
  const states=await page.evaluate(()=>{const old=Date.now;Date.now=()=>Date.parse('2026-10-18T12:00:00+08:00');try{const start=Date.now()-7200000,end=Date.now()-3600000,due=Date.now()+10800000,base={category:'task',start,end,due,effectiveDue:due};return {ended:candidateStatus(base).key,homework:candidateStatus({...base,category:'homework'}).key,running:candidateStatus({...base,end:Date.now()+3600000}).key,startOnly:candidateStatus({...base,end:null}).key,endOnly:candidateStatus({...base,start:null}).key,dueOnly:candidateStatus({...base,start:null,end:null}).key,edge:candidateStatus({...base,end:Date.now()}).key,done:candidateStatus({...base,completed:true}).key,event:candidateStatus({...base,category:'event'}).key,record:candidateStatus({...base,record:true}).key,none:candidateStatus({...base,start:null,end:null,due:null,effectiveDue:null}).key,deadline:candidateDeadline(base),overdueAt:candidateOverdueAt(base)};}finally{Date.now=old;}});
  assert.equal(states.ended,'overdue');assert.equal(states.homework,'overdue');assert.equal(states.startOnly,'overdue');assert.equal(states.endOnly,'overdue');for(const key of ['running','dueOnly','edge','done','event','record','none'])assert.notEqual(states[key],'overdue',key);assert.equal(states.done,'done');assert.equal(states.deadline,at('15'));assert.equal(states.overdueAt,at('11'));
  await page.evaluate(()=>navigate('task/1?candidate_id=2'));await idle(page);assert.equal(await page.evaluate(()=>document.querySelector('.candidate-state').textContent),'已逾期','real detail renders execution overdue while explicit deadline remains in future');const shot=await page.cdp.send('Page.captureScreenshot',{format:'jpeg',quality:70,captureBeyondViewport:false});const evidence=path.join(root,'build/task-overdue-ui');fs.mkdirSync(evidence,{recursive:true});fs.writeFileSync(path.join(evidence,'web-detail.jpg'),Buffer.from(shot.data,'base64'));
  await page.evaluate(item=>candidateEditor(item),items[0]);assert.equal(await page.evaluate(()=>document.querySelector('#field-due').value),'','derived deadline must not populate editable raw due');
  assert.equal(await page.evaluate(()=>document.querySelector('#field-start').value),'2026-10-18T10:00');assert.equal(await page.evaluate(()=>document.querySelector('#field-end').value),'2026-10-18T11:00');
  await page.getByRole('button',{name:'保存',exact:true}).click();await idle(page);const saved=requests.find(request=>request.path==='/api/candidate/save').body;assert.equal(saved.due,null);assert.equal(saved.start,at('10'));assert.equal(saved.end,at('11'));
  assert.equal(errors.length,0,errors.join('\n'));console.log('Deadline consumers: PASS (home, schedule, detail, raw editor, reminder wiring, execution preservation)');
 }finally{await browser.close();server.close();}
})().catch(error=>{console.error(error);server.close();process.exitCode=1;});
