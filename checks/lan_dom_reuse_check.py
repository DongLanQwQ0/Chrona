"""Production date/state checks; real DOM identity and interaction are checked in Edge."""
from pathlib import Path
import subprocess
root = Path(__file__).resolve().parents[1]
script = r'''
const fs=require('fs'),vm=require('vm'),assert=require('assert');
const source=fs.readFileSync('app/src/main/assets/lan/app.js','utf8');
function section(start,end){const a=source.indexOf(start);return source.slice(a,source.indexOf(end,a));}
const context={Intl,Date,Map,Set,session:{zone:'Asia/Shanghai'},filter:'',todoScope:0,taskLike:category=>category==='task'||category==='homework',executionAllDay:item=>item.executionAllDay??item.allDay,candidateOverdueAt:item=>item.overdueAt};
vm.createContext(context);vm.runInContext(section('function groupingDate(', 'function locateHomework(')+section('const calendarDateFormats=', '// AgendaRepository'),context);
let count=0;function equal(value,want,label){assert.equal(value,want,label);count++;}
equal(context.groupingDate({start:Date.parse('2026-10-27T20:00:00Z'),category:'event'}),'2026-10-28','phone zone grouping');
equal(context.groupingDate({start:Date.parse('2026-10-27T00:00:00Z'),category:'event',allDay:true}),'2026-10-27','all-day civil date uses UTC');
equal(context.groupingDate({created:Date.parse('2026-10-27T20:00:00Z'),start:0},true),'2026-10-28','inbox groups created');
const homework={start:Date.parse('2026-10-27T00:00:00Z'),executionStart:Date.parse('2026-10-27T00:00:00Z'),due:Date.parse('2026-11-06T00:00:00Z'),effectiveDue:Date.parse('2026-11-06T00:00:00Z'),category:'homework'};
equal(context.groupingDate(homework),'2026-10-27','all-types query groups scheduled homework by execution date');
context.filter='homework';equal(context.groupingDate(homework),'2026-11-06','homework query groups effective due');context.filter='';
context.session.zone='America/Los_Angeles';equal(context.groupingDate({start:Date.parse('2026-10-27T00:00:00Z'),due:Date.parse('2026-11-06T00:00:00Z'),category:'task',allDay:true,executionAllDay:true}),'2026-10-27','default all-day task with due preserves UTC civil date');context.session.zone='Asia/Shanghai';
equal(context.groupingDate({start:Date.parse('2026-10-27T00:00:00+08:00'),system:true,allDay:true}),'2026-10-27','external all-day display date remains phone-zone civil date');
context.todoScope=2;equal(context.groupingDate({overdueAt:Date.parse('2026-10-26T00:00:00Z'),start:Date.parse('2026-10-27T00:00:00Z'),category:'task'}),'2026-10-26','overdue groups authoritative overdueAt');
context.todoScope=1;equal(context.groupingDate({effectiveDue:Date.parse('2026-10-29T00:00:00Z'),start:Date.parse('2026-10-27T00:00:00Z'),category:'task'}),'2026-10-29','today-due groups deadline');
context.todoScope=0;equal(context.groupingDate({category:'event'}),'待安排','unknown date stays explicit');
assert(source.includes('old.dataset.state===element.dataset.state'),'unchanged interactive controls have stable identity');count++;
assert(source.includes('!reused.has(old.element)'),'one old node cannot be consumed twice');count++;
assert(source.includes('if(ticket!==sequence)return [];'),'stale async results rejected');count++;
assert(source.includes('selected:[...selectedRows],selectionMode'),'history carries selected identities');count++;
assert(source.includes('selectedRows.clear();selectionMode=false;for(const saved of routeState.values())'),'section switch invalidates selection');count++;
for(const removed of ['async function calendarPage(', 'async function timetablePage(', 'function settingsPage(', 'async function sourcesDialog(', 'async function syncDialog(', 'async function detectMerges(']){assert(!source.includes(removed),removed+' removed');count++;}
console.log('PASS '+count+' daily-list production date/state checks; rendering is checks/lan_daily_workspace_check.cjs');
'''
subprocess.run(['node', '-e', script], cwd=root, check=True)
