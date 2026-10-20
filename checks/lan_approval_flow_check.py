"""Exercise actual LAN approval functions with controlled requests and timers; no browser."""
from pathlib import Path
import subprocess

root = Path(__file__).resolve().parents[1]
script = r'''
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync('app/src/main/assets/lan/app.js','utf8');
const functions=source.slice(source.indexOf("let approvalId="),source.indexOf("$('#approval-retry').addEventListener"));
let count=0;
async function fixture(){
 const elements=new Map(),timers=new Map(),calls=[],requests=[];let next=0,connections=0;
 const context={console,csrf:'',setTimeout:fn=>{timers.set(++next,fn);return next;},clearTimeout:id=>timers.delete(id),
 $:id=>{if(!elements.has(id))elements.set(id,{hidden:false,textContent:''});return elements.get(id);},
 api:async(url,body)=>{calls.push({url,body});if(url.endsWith('/request'))return await new Promise(resolve=>requests.push(resolve));if(url.endsWith('/poll'))return {state:context.state};return {ok:true};},
 connect:async()=>{connections++;if(context.failConnect)throw new Error('连接中断');context.csrf='connected';},
 disconnect:()=>{context.csrf='';},toast:()=>{}};
 vm.createContext(context);vm.runInContext(functions+';globalThis.flow={requestApproval,cancelApproval,stopApproval};',context);
 const check=(value,message)=>{assert(value,message);count++;};
 const tick=async()=>{const [id,fn]=timers.entries().next().value;timers.delete(id);await fn();};
 return {context,elements,calls,requests,timers,check,tick,get connections(){return connections;}};
}
(async()=>{
 let f=await fixture(),p=f.context.flow.requestApproval();f.requests.shift()({id:'first',verification:'0123'});await p;
 f.check(f.elements.get('#approval-code').textContent==='0123','leading zero verification');
 f.check(f.connections===0,'pending grants no access');f.context.state='pending';await f.tick();f.check(f.timers.size===1,'pending schedules once');
 f.context.state='approved';await f.tick();f.check(f.connections===1&&f.timers.size===0,'approval connects once');
 for(const state of ['denied','expired','full']){f=await fixture();p=f.context.flow.requestApproval();f.requests.shift()({id:'x',verification:'1234'});await p;f.context.state=state;await f.tick();f.check(f.connections===0&&f.timers.size===0,'terminal stops '+state);f.check(!f.elements.get('#approval-retry').hidden,'retry visible '+state);}
 f=await fixture();p=f.context.flow.requestApproval();await f.context.flow.cancelApproval();f.requests.shift()({id:'late',verification:'1234'});await p;
 f.check(f.calls.some(x=>x.url.endsWith('/cancel')&&x.body.id==='late'),'late request cancelled');f.check(f.timers.size===0&&f.connections===0,'cancel race cannot connect');
 f=await fixture();p=f.context.flow.requestApproval();f.requests.shift()({id:'x',verification:'1234'});await p;await f.context.flow.cancelApproval();
 f.check(f.calls.some(x=>x.url.endsWith('/cancel'))&&f.timers.size===0,'cancel clears timer');
 f=await fixture();p=f.context.flow.requestApproval();f.requests.shift()({id:'x',verification:'1234'});await p;f.context.state='approved';f.context.failConnect=true;await f.tick();f.check(f.elements.get('#approval-status').textContent==='连接中断'&&!f.elements.get('#approval-retry').hidden,'connect failure remains actionable');
 console.log('LAN approval flow: '+count+' checks PASS (actual functions, controlled API/timers; no browser)');
})().catch(error=>{console.error(error);process.exitCode=1;});
'''
subprocess.run(["node", "-e", script], cwd=root, check=True)
