"""Run production home construction/reuse with DOM-move transport; browser QA is separate."""
from pathlib import Path
import json
import subprocess

root = Path(__file__).resolve().parents[1]
source = (root / 'app/src/main/assets/lan/app.js').read_text(encoding='utf-8')

def section(start, end):
    return source[source.index(start):source.index(end, source.index(start))]

production = '\n'.join((
    section('function entry(', 'function taskRoute('),
    section('function appendGrouped(', 'function timelineTime('),
    section('function timelineTime(', 'function dateISO('),
    section('async function homePage(', 'async function openSchedule('),
))
reuse = '{\n' + section('  const reused=new Set();', '  host.replaceChildren(incoming);') + '\n}'
script = r'''
const assert=require('node:assert/strict'),vm=require('node:vm');
class Element {
 constructor(tag,cls='',text=''){this.tag=tag;this.className=cls;this.text=text;this.dataset={};this.children=[];this.parent=null;this.classList={add:value=>this.className+=' '+value};}
 append(...nodes){for(const node of nodes){node.detach();this.children.push(node);node.parent=this;}}
 detach(){if(this.parent){const p=this.parent;p.children.splice(p.children.indexOf(this),1);this.parent=null;}}
 removeAttribute(name){if(name==='data-key')delete this.dataset.key;}
 querySelector(selector){return this.children.find(node=>node.tag===selector)||this.children.map(node=>node.querySelector(selector)).find(Boolean)||null;}
 querySelectorAll(){return this.children.flatMap(node=>[...(node.dataset.key?[node]:[]),...node.querySelectorAll()]);}
 isEqualNode(node){return this.tag===node.tag&&this.className===node.className&&this.text===node.text&&JSON.stringify(this.dataset)===JSON.stringify(node.dataset)&&this.children.length===node.children.length&&this.children.every((child,i)=>child.isEqualNode(node.children[i]));}
 replaceWith(other){other.detach();const p=this.parent,index=p.children.indexOf(this);p.children.splice(index,1,other);other.parent=p;this.parent=null;}
}
const node=(tag,cls='',text='')=>new Element(tag,cls,text),action=(label,click,cls='')=>Object.assign(node('button',cls,label),{click});
function card(title){const result=Object.assign(node('section','card'),{caption:title});if(title)result.append(node('div','section-title',title));return result;}
const icon=name=>node('span','icon',name),heading=(title,subtitle)=>node('header','',title+' '+subtitle);
const context={node,action,card,icon,heading,Intl,Date,revision:0,sequence:1,categories:{event:'活动',task:'待办',homework:'作业'},
 fmt:value=>String(value),itemDate:item=>new Date(item.start).toISOString().slice(0,10),
 phoneDayStart:date=>Date.parse(date+'T00:00:00+08:00'),
 completionAction:item=>item.category==='task'?action('complete',()=>{}):null,
 taskRoute:item=>'task/'+item.taskId+'?candidate_id='+item.id,navigate:value=>context.clicked=value,
 showReadOnly:item=>context.clicked='readonly/'+item.id,openSchedule:()=>{},capture:()=>{},
 empty:(parent,label)=>parent.append(node('p','empty',label))};
vm.createContext(context);vm.runInContext(__PRODUCTION__,context);
let assertions=0;function check(value,label){assert(value,label);assertions++;}
const start=Date.parse('2026-10-21T08:00:00+08:00');
const items=[1,2,3].map(id=>({id,taskId:10+id,title:'Tomorrow '+id,category:id===2?'task':'event',start:start+id*3600000,end:start+(id+1)*3600000,zone:'Asia/Shanghai'}));
const calendar=[{id:90,title:'Readonly course',source:'School',system:true,category:'event',start,end:start+3600000}];
function payload(){return {revision:1,date:'2026-10-20',items,recent:items,calendar,limit:30,review:0,failed:0,todos:{total:1,todayDue:0,overdue:0,unscheduled:0,items:[items[1]]}};}
async function build(data){context.api=async()=>data;const root=node('fragment');root.append(...await context.homePage(1));return root;}
function find(root,title){if(root.caption===title)return root;for(const child of root.children){const found=find(child,title);if(found)return found;}return null;}
function refresh(old,incoming){const previous=new Map(old.querySelectorAll().map(element=>[element.dataset.key,{element}]));context.local=true;context.previous=previous;context.incoming=incoming;vm.runInContext(__REUSE__,context);return incoming;}
function validate(root){const all=root.querySelectorAll(),timeline=find(root,'时间线'),recent=find(root,'最近事项'),todos=find(root,'待办 · 1');
 check(timeline.querySelectorAll().length===4,'timeline keeps all rows');check(recent.querySelectorAll().length===4,'recent keeps all rows');check(todos.querySelectorAll().length===1,'todo keeps its row');
 check(new Set(all.map(element=>element.dataset.key)).size===all.length,'section-scoped keys are unique');
 check(timeline.children[1].children[0].text==='2026-10-21','date group stays with rows');
}
(async()=>{
 let old=await build(payload());validate(old);
 for(let turn=0;turn<5;turn++){const before=new Map(old.querySelectorAll().map(element=>[element.dataset.key,element]));old=refresh(old,await build(payload()));validate(old);for(const element of old.querySelectorAll())check(before.get(element.dataset.key)===element,'unchanged rows keep identity in their own section');}
 const changed=payload();changed.items=items.map(item=>item.id===1?{...item,title:'Updated tomorrow'}:item);changed.recent=changed.items;
 old=refresh(old,await build(changed));validate(old);const row=find(old,'时间线').querySelectorAll().find(element=>element.dataset.content?.includes('Updated tomorrow'));check(!!row,'changed content replaces row');row.click();check(context.clicked==='task/11?candidate_id=1','event click retains candidate identity');
 const readonly=find(old,'时间线').querySelectorAll().find(element=>element.dataset.content?.includes('Readonly course'));readonly.click();check(context.clicked==='readonly/90','readonly source click retained');
 const duplicate=node('fragment'),prior=node('fragment');prior.append(node('button'));prior.children[0].dataset.key='duplicate';for(let i=0;i<2;i++){const item=node('button');item.dataset.key='duplicate';duplicate.append(item);}refresh(prior,duplicate);check(duplicate.children.length===2,'one old node cannot erase a second occurrence');check(duplicate.children[0]!==duplicate.children[1],'duplicates remain distinct DOM instances');
 const empty={...payload(),items:[],recent:[],calendar:[],todos:null};old=refresh(old,await build(empty));check(find(old,'时间线').querySelectorAll().length===0,'real removal removes rows');check(find(old,'时间线').children[1].children.every(child=>child.tag!=='h3'),'empty result has no orphan day headers');
 console.log('PASS: '+assertions+' production home/reuse checks; DOM transport, not browser rendering');
})().catch(error=>{console.error(error);process.exitCode=1;});
'''.replace('__PRODUCTION__', json.dumps(production)).replace('__REUSE__', json.dumps(reuse))
out = root / 'build/lan-dom-reuse-check'
out.mkdir(parents=True, exist_ok=True)
target = out / 'check.cjs'
target.write_text(script, encoding='utf-8')
subprocess.run(['node', str(target)], check=True, cwd=root)
