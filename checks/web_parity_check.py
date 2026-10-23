"""LAN parity contracts plus actual Edge interaction against an explicit mock phone API."""
from pathlib import Path
import subprocess

ROOT = Path(__file__).resolve().parents[1]
SERVER = ROOT / 'app/src/main/java/com/donglan/chrona/LanWebServer.java'
source = SERVER.read_text(encoding='utf-8')
for required in ['queryInboxIds(query,oldestFirst)', 'querySchedulePage(query,oldestFirst,page)',
                 'ScheduleOrderPreferences.oldestFirst(context,tab)', 'includeIds',
                 'CandidateEditBaseline.of(series)', 'RepeatActions.change(',
                 'RepeatOccurrences.at(saved,', 'HomeData home=new HomeData(context,store)',
                 'provider.listInstances(begin-86_400_000L,end+86_400_000L,null)', '.withRepeat(item)']:
    assert required in source, required
bulk = source[source.index('if(path.equals("/api/selection/delete"))'):source.index('if(path.equals("/api/candidate/merge"))')]
assert bulk.index('calendarWrites.delete') < bulk.index('store.deleteTask') < bulk.index('new ImageStore')
assert bulk.index('calendarWrites.delete') < bulk.index('store.deleteCandidate')
out = ROOT / 'build/web-parity-checks'
out.mkdir(parents=True, exist_ok=True)
script = r'''
const fs=require('fs'),path=require('path'),http=require('http'),assert=require('assert');
const root=__ROOT__,{chromium}=require(path.join(root,'checks/lan_browser_cdp.cjs'));
const assets=path.join(root,'app/src/main/assets/lan'),out=path.join(root,'build/web-parity-checks');
const server=http.createServer((req,res)=>{const name={'/':'index.html','/app.js':'app.js','/style.css':'style.css'}[new URL(req.url,'http://localhost').pathname];if(!name){res.writeHead(404).end();return;}res.setHeader('Content-Type',name.endsWith('js')?'text/javascript':name.endsWith('css')?'text/css':'text/html');res.end(fs.readFileSync(path.join(assets,name)));});
const original=Date.now()+86400000,base={id:11,taskId:1,title:'重复学习',category:'task',start:original,end:original+3600000,effectiveDue:original+3600000,due:original+3600000,zone:'Asia/Shanghai',allDay:false,location:'图书馆',description:'',reminder:10,recurrence:{frequency:'daily',interval:1,weekdays:[],until:null,count:5},repeatLabel:'每天 · 共5次',baseline:'saved-series',occurrenceAt:original,completed:false};
const plain={...base,id:12,title:'普通活动',category:'event',recurrence:null,repeatLabel:'',occurrenceAt:null,canAcknowledge:true},task={id:1,text:'学习计划',created:Date.now(),source:'模拟手机',status:'ready'};
let revision=1,requests=[],errors=[];
async function mock(route){const req=route.request(),url=new URL(req.url()),body=req.postDataJSON();requests.push({path:url.pathname,q:Object.fromEntries(url.searchParams),body});let data;
switch(url.pathname){
case '/api/session':data={csrf:'test',zone:'Asia/Shanghai',calendarAllowed:true,calendarReadable:true,systemCalendarIncluded:true,palette:{background:'#f4f6f8',primary:'#008582'}};break;
case '/api/home':data={revision,items:[base,plain],recent:[base],calendar:[],date:'2026-10-19',review:1,failed:0,limit:30};break;
case '/api/schedule':data={revision,items:[base,plain],ids:[11,12,99],total:36,page:Math.min(1,Number(url.searchParams.get('page'))||0),pageSize:24,oldestFirst:url.searchParams.has('oldestFirst')?url.searchParams.get('oldestFirst')==='true':url.searchParams.get('tab')==='0'};break;
case '/api/inbox':data={revision,items:[task],ids:[1,2,3],total:3,page:0,pageSize:12,oldestFirst:url.searchParams.get('oldestFirst')==='true'};break;
case '/api/task':data={revision,task,candidates:[base,plain],images:[],files:[]};break;
case '/api/candidate/occurrences':data={revision,series:{...base,occurrenceAt:null},items:[base]};break;
case '/api/candidate/repeat':assert.equal(body.baseline,'saved-series');revision++;data={revision,candidate:{...base,occurrenceAt:null}};break;
case '/api/candidate/acknowledge':data={revision};break;
case '/api/selection/delete':revision++;data={revision,removed:body.ids.length};break;
default:throw Error('Unhandled mock '+url.pathname);
}await route.fulfill({status:200,contentType:'application/json',body:JSON.stringify(data)});}
async function idle(p){await p.evaluate(async()=>{while(pending)await new Promise(r=>setTimeout(r,10));await new Promise(r=>requestAnimationFrame(()=>requestAnimationFrame(r)));});}
async function click(p,name){await p.getByRole('button',{name,exact:true}).click();await idle(p);}
const latest=path=>requests.filter(item=>item.path===path).at(-1);
(async()=>{await new Promise(r=>server.listen(0,'127.0.0.1',r));const browser=await chromium.launch({executablePath:'C:/Program Files (x86)/Microsoft/Edge/Application/msedge.exe'});try{
const p=await browser.newPage({viewport:{width:1280,height:900}});p.on('pageerror',e=>errors.push(String(e)));await p.route('**/api/**',mock);await p.goto(`http://127.0.0.1:${server.address().port}/`);await p.getByRole('heading',{name:'时间线',exact:true}).waitFor();await idle(p);
assert(await p.evaluate(()=>document.querySelectorAll('.list-day').length>=2));assert.equal(await p.evaluate(()=>document.querySelectorAll('button button').length),0);
await p.evaluate(()=>navigate('schedule'));await idle(p);await click(p,'最早优先');assert.equal(latest('/api/schedule').q.oldestFirst,'false');await click(p,'历史');assert.equal(latest('/api/schedule').q.tab,'2');await click(p,'最近优先');assert.equal(latest('/api/schedule').q.oldestFirst,'true');await click(p,'待处理');assert.equal(latest('/api/schedule').q.oldestFirst,'false');
await click(p,'筛选');await click(p,'今日截止');await click(p,'应用筛选');assert.equal(latest('/api/schedule').q.category,'task');assert.equal(latest('/api/schedule').q.todo,'1');assert.equal(latest('/api/schedule').q.tab,'3');
await click(p,'筛选');await click(p,'活动');await click(p,'本周');await click(p,'下一段');await click(p,'应用筛选');assert.equal(latest('/api/schedule').q.todo,'0');assert(latest('/api/schedule').q.until>latest('/api/schedule').q.from);assert((await p.locator('.filter-summary').textContent()).includes(' — '));
await click(p,'全选筛选结果');await click(p,'删除所选');await click(p,'确认');assert.deepEqual(latest('/api/selection/delete').body.ids,['11','12','99']);assert.equal(latest('/api/selection/delete').body.inbox,false);
await p.evaluate(async()=>{window.scrollTo(0,1000);document.querySelector('[aria-label="下一页"]').click();});await idle(p);await new Promise(r=>setTimeout(r,500));assert.equal(await p.evaluate(()=>page),1);assert.equal(await p.evaluate(()=>scrollY),0);
await p.evaluate(item=>navigate(taskRoute(item)),base);await idle(p);assert.equal(latest('/api/task').q.candidate_id,'11');assert.equal(Number(latest('/api/task').q.occurrenceAt),original);
await click(p,'标记完成');assert.equal(latest('/api/candidate/repeat').body.action,'complete');assert.equal(latest('/api/candidate/repeat').body.original,original);
await click(p,'重复日程');await click(p,'仅改期这次');await p.locator('#field-repeat-move').fill('2026-11-20T14:00');await click(p,'保存改期');assert.equal(latest('/api/candidate/repeat').body.action,'move');assert.equal(latest('/api/candidate/repeat').body.original,original);
await click(p,'跳过这次');await click(p,'确认');assert.equal(latest('/api/candidate/repeat').body.action,'skip');await click(p,'停止重复');assert((await p.locator('#dialog-body').textContent()).includes('停止之后尚未开始的日程及提醒，保留历史记录'));await click(p,'确认');assert.equal(latest('/api/candidate/repeat').body.action,'stop');
await p.evaluate(item=>repeatRuleDialog(item),plain);await click(p,'保存重复');assert.equal(latest('/api/candidate/repeat').body.rule.frequency,'daily');await p.locator('#close-dialog').click();
await p.setViewportSize({width:390,height:844});await p.evaluate(()=>navigate('schedule'));await idle(p);await click(p,'筛选');await new Promise(r=>setTimeout(r,300));assert(await p.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));assert(await p.evaluate(()=>[...document.querySelectorAll('#dialog .selection-bar')].every(el=>el.scrollWidth<=el.clientWidth+1)));await p.screenshot({path:path.join(out,'mobile-filter.png'),fullPage:true});
assert.deepEqual(errors,[]);console.log('LAN web parity: PASS (SQL order contract, filters, cross-page selection/delete, occurrence identity and actions, mobile geometry)');
}finally{await browser.close();server.close();}})().catch(e=>{console.error(e);server.close();process.exitCode=1;});
'''
import json
target = out / 'check.cjs'
target.write_text(script.replace('__ROOT__', json.dumps(str(ROOT))), encoding='utf-8')
subprocess.run(['node', '--check', str(ROOT / 'app/src/main/assets/lan/app.js')], check=True)
subprocess.run(['node', str(target)], check=True)
