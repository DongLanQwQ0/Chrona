'use strict';
// A QR fragment never enters the HTTP request/referrer; consume it before any API call.
function takePairingFragment(){if(!location.hash.startsWith('#pair='))return null;const value=location.hash.slice(6);history.replaceState(null,'',location.pathname+location.search);return /^[0-9]{6}$/.test(value)?value:'';}
let scannedPairing=takePairingFragment();
// Lucide icon paths, ISC license (lucide.dev). Kept in the APK; no CDN or remote assets.
const ICONS={
 'house':'<path d="m3 10 9-7 9 7v10a1 1 0 0 1-1 1h-6v-7H10v7H4a1 1 0 0 1-1-1z"/>',
 'calendar-days':'<rect x="3" y="5" width="18" height="16" rx="2"/><path d="M16 3v4M8 3v4M3 11h18M8 15h.01M12 15h.01M16 15h.01M8 18h.01M12 18h.01M16 18h.01"/>',
 'inbox':'<path d="M4 4h16l2 12v4H2v-4zM2 16h6l2 3h4l2-3h6"/>',
 'grid-2x2':'<rect width="18" height="18" x="3" y="3" rx="2"/><path d="M3 12h18M12 3v18"/>',
 'plus':'<path d="M12 5v14M5 12h14"/>', 'x':'<path d="m18 6-12 12M6 6l12 12"/>',
 'chevron-right':'<path d="m9 18 6-6-6-6"/>','chevron-left':'<path d="m15 18-6-6 6-6"/>',
 'arrow-left':'<path d="m12 19-7-7 7-7M5 12h14"/>',
 'refresh-cw':'<path d="M3 12a9 9 0 0 1 15.4-6.4L21 8M21 3v5h-5M21 12a9 9 0 0 1-15.4 6.4L3 16M8 16H3v5"/>',
 'sun-moon':'<path d="M12 3a6.5 6.5 0 1 0 9 9 9 9 0 1 1-9-9M19 3v4M17 5h4"/>',
 'settings':'<path d="M9.671 4.136a2.34 2.34 0 0 1 4.659 0 2.34 2.34 0 0 0 3.319 1.915 2.34 2.34 0 0 1 2.33 4.033 2.34 2.34 0 0 0 0 3.831 2.34 2.34 0 0 1-2.33 4.033 2.34 2.34 0 0 0-3.319 1.915 2.34 2.34 0 0 1-4.659 0 2.34 2.34 0 0 0-3.32-1.915 2.34 2.34 0 0 1-2.33-4.033 2.34 2.34 0 0 0 0-3.831A2.34 2.34 0 0 1 6.35 6.051a2.34 2.34 0 0 0 3.319-1.915"/><circle cx="12" cy="12" r="3"/>',
 'check':'<path d="m20 6-11 11-5-5"/>',
 'circle':'<circle cx="12" cy="12" r="9"/>',
 'clock-alert':'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2M21 3v3M21 8h.01"/>',
 'file-text':'<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8zM14 2v6h6M8 13h8M8 17h6"/>'
};
const $=selector=>document.querySelector(selector);
function node(tag,cls,text){const element=document.createElement(tag);if(cls)element.className=cls;if(text!==undefined)element.textContent=text;return element;}
function icon(name){const element=node('span');element.innerHTML=`<svg viewBox="0 0 24 24" aria-hidden="true">${ICONS[name]||ICONS['file-text']}</svg>`;return element;}
function icons(){enhanceSelects();document.querySelectorAll('[data-icon]').forEach(element=>element.replaceChildren(icon(element.dataset.icon)));}
let openSelect=null,closingSelect=null,selectId=0;
function closeSelect(restore=false,immediate=false){const opened=openSelect||(immediate?closingSelect:null);if(!opened)return;openSelect=null;closingSelect=opened;opened.menu.inert=true;opened.trigger.setAttribute('aria-expanded','false');if(restore&&opened.trigger.isConnected)opened.trigger.focus({preventScroll:true});const animation=immediate?null:motion(opened.menu,[{opacity:1,transform:'translateY(0)'},{opacity:0,transform:'translateY(-4px)'}],120);const remove=()=>{if(openSelect===opened)return;if(opened.menu.matches(':popover-open'))opened.menu.hidePopover();opened.menu.remove();if(closingSelect===opened)closingSelect=null;};if(animation)animation.finished.then(remove,()=>{});else{activeMotion.get(opened.menu)?.cancel();remove();}}
function enhanceSelects(){
 if(openSelect&&!openSelect.trigger.isConnected)closeSelect(false,true);
 document.querySelectorAll('select:not(.select-native)').forEach(select=>{
  const wrapper=node('div','select-control'),trigger=node('button','select-trigger');trigger.type='button';trigger.setAttribute('role','combobox');trigger.setAttribute('aria-haspopup','listbox');trigger.setAttribute('aria-expanded','false');trigger.setAttribute('aria-label',select.getAttribute('aria-label')||document.querySelector(`label[for="${select.id}"]`)?.textContent||'选择');
  trigger.id=select.id?select.id+'-trigger':'select-trigger-'+(++selectId);if(select.id){const caption=document.querySelector(`label[for="${select.id}"]`);if(caption)caption.htmlFor=trigger.id;}const text=node('span');trigger.append(text,icon('chevron-right'));select.before(wrapper);wrapper.append(select,trigger);select.classList.add('select-native');select.tabIndex=-1;select.setAttribute('aria-hidden','true');
  const sync=()=>{text.textContent=select.selectedOptions[0]?.textContent||'';trigger.disabled=select.disabled;};sync();select.addEventListener('change',sync);
  const show=(last=false)=>{
   if(closingSelect?.trigger===trigger&&closingSelect.menu.isConnected){openSelect=closingSelect;closingSelect=null;const menu=openSelect.menu;menu.inert=false;trigger.setAttribute('aria-expanded','true');motion(menu,[{opacity:0,transform:'translateY(-6px)'},{opacity:1,transform:'translateY(0)'}],180);menu.querySelector('[aria-selected="true"]')?.focus({preventScroll:true});return;}
   closeSelect(false,true);const menu=node('div','select-menu');menu.popover='manual';menu.id='select-menu-'+(++selectId);menu.setAttribute('role','listbox');menu.setAttribute('aria-label',trigger.getAttribute('aria-label'));trigger.setAttribute('aria-controls',menu.id);trigger.setAttribute('aria-expanded','true');
   const options=[...select.options].map((option,index)=>{const button=node('button','',option.textContent);button.type='button';button.setAttribute('role','option');button.setAttribute('aria-selected',String(index===select.selectedIndex));button.disabled=option.disabled;button.tabIndex=-1;button.addEventListener('click',()=>{select.value=option.value;closeSelect(true);sync();select.dispatchEvent(new Event('input',{bubbles:true}));select.dispatchEvent(new Event('change',{bubbles:true}));});menu.append(button);return button;});
   (select.closest('dialog')||document.body).append(menu);menu.showPopover();const bounds=trigger.getBoundingClientRect(),height=Math.min(menu.scrollHeight,280,innerHeight-24),below=innerHeight-bounds.bottom-8;menu.style.width=Math.min(Math.max(bounds.width,180),innerWidth-24)+'px';menu.style.maxHeight=height+'px';menu.style.left=Math.max(12,Math.min(bounds.left,innerWidth-parseFloat(menu.style.width)-12))+'px';menu.style.top=Math.max(12,below>=height?bounds.bottom+6:bounds.top-height-6)+'px';
   openSelect={menu,trigger};motion(menu,[{opacity:0,transform:'translateY(-6px)'},{opacity:1,transform:'translateY(0)'}],180);
   const enabled=options.filter(option=>!option.disabled);(last?enabled.at(-1):options[select.selectedIndex]&&!options[select.selectedIndex].disabled?options[select.selectedIndex]:enabled[0])?.focus({preventScroll:true});
   menu.addEventListener('keydown',event=>{const index=enabled.indexOf(document.activeElement);if(['ArrowDown','ArrowUp','Home','End'].includes(event.key)){event.preventDefault();const next=event.key==='Home'?0:event.key==='End'?enabled.length-1:(index+(event.key==='ArrowDown'?1:-1)+enabled.length)%enabled.length;enabled[next]?.focus({preventScroll:true});const option=enabled[next];if(option){if(option.offsetTop<menu.scrollTop)menu.scrollTop=option.offsetTop;else if(option.offsetTop+option.offsetHeight>menu.scrollTop+menu.clientHeight)menu.scrollTop=option.offsetTop+option.offsetHeight-menu.clientHeight;}}else if(event.key==='Enter'||event.key===' '){event.preventDefault();document.activeElement?.click();}else if(event.key==='Escape'){event.preventDefault();event.stopPropagation();closeSelect(true);}else if(event.key==='Tab')closeSelect(true,true);});
  };
  trigger.addEventListener('click',()=>openSelect?.trigger===trigger?closeSelect(true):show());trigger.addEventListener('keydown',event=>{if(['ArrowDown','ArrowUp'].includes(event.key)){event.preventDefault();show(event.key==='ArrowUp');}});
 });
}
document.addEventListener('pointerdown',event=>{if(openSelect&&!openSelect.menu.contains(event.target)&&!openSelect.trigger.contains(event.target))closeSelect();});
window.addEventListener('resize',()=>closeSelect());document.addEventListener('scroll',event=>{if(openSelect&&!openSelect.menu.contains(event.target))closeSelect();},true);
function action(label,handler,cls=''){const button=node('button',cls,label);button.type='button';button.addEventListener('click',()=>guard(handler));return button;}
function guard(work){const owner=dialogGeneration;return Promise.resolve().then(work).catch(error=>toast(error.message||'操作未完成，请重试',owner));}
let csrf='',session=null,revision=0,termRevision=0,route='home',page=0,search='',filter='',selectedTerm='',week=new Date(),sequence=0,currentTask=null,themeOverride=false;
let pending=0;
async function api(path,body){pending++;try{const response=await fetch(path,{method:body?'POST':'GET',credentials:'same-origin',cache:'no-store',headers:body?{'Content-Type':'application/json','X-Chrona-CSRF':csrf}:{},body:body?JSON.stringify(body):undefined});const value=await response.json();if(response.status===401){disconnect();const error=new Error('连接已失效，请重新连接');error.status=401;throw error;}if(!response.ok){const error=new Error(value.error||'手机暂时无法响应');error.status=response.status;throw error;}return value;}catch(error){if(error instanceof TypeError)throw new Error('无法连接手机，请检查手机局域网访问及网络');throw error;}finally{pending--;}}
const activeMotion=new WeakMap(),runningMotions=new Set(),reducedMotion=matchMedia('(prefers-reduced-motion: reduce)');
// Read the current presentation before cancelling: reversals start at the visible frame.
function motion(element,frames,duration=180){
 const previous=activeMotion.get(element);if(previous?.playState==='running'){const style=getComputedStyle(element),start={...frames[0]};for(const property of Object.keys(start))if(property in style)start[property]=style[property];frames=[start,...frames.slice(1)];}
 previous?.cancel();if(reducedMotion.matches)return null;
 const animation=element.animate(frames,{duration,easing:'cubic-bezier(.2,.75,.25,1)'});activeMotion.set(element,animation);runningMotions.add(animation);animation.finished.then(()=>runningMotions.delete(animation),()=>runningMotions.delete(animation));return animation;
}
let dialogGeneration=0;
let inlineEditor=null;
let dialogEditor=null;
function requestDialogClose(){
 const editor=dialogEditor;
 if(!editor||!editor.form.isConnected){closeDialog();return;}
 if(editor.working()){toast('正在保存，请稍候');return;}
 if(editor.restore){editor.restore();return;}
 if(!editor.dirty()){closeDialog();return;}
 confirmEditorAction(editor,'放弃未保存的修改？','修改尚未保存。','放弃修改',()=>closeDialog(),'继续编辑');
}
function confirmEditorAction(editor,prompt,message,confirmLabel,confirmed,cancelLabel='取消'){
 const body=$('#dialog-body'),title=$('#dialog-title'),label=title.textContent,confirmation=node('div','editor-confirmation'),buttons=node('div','actions');
 const restore=()=>{confirmation.remove();editor.form.hidden=false;title.textContent=label;editor.restore=null;editor.form.querySelector('input:not([readonly]):not(:disabled)')?.focus({preventScroll:true});};
 editor.restore=restore;editor.form.hidden=true;title.textContent=prompt;confirmation.append(node('p','subtle',message));
 buttons.append(action(cancelLabel,restore),action(confirmLabel,confirmed,'danger'));confirmation.append(buttons);body.append(confirmation);buttons.firstElementChild.focus();
}
window.addEventListener('beforeunload',event=>{if([inlineEditor,dialogEditor].some(editor=>editor?.form.isConnected&&(editor.dirty()||editor.working()))){event.preventDefault();event.returnValue='';}});
async function leaveInlineEditor(){
 if(!inlineEditor||!inlineEditor.form.isConnected){inlineEditor=null;return true;}
 const editor=inlineEditor;if(editor.working()){toast('正在保存，请稍候');return false;}
 if(!editor.dirty()){inlineEditor=null;return true;}
 return new Promise(resolve=>{showDialog('放弃未保存的修改？',(body,owner)=>{body.append(node('p','subtle','修改尚未保存。'));const buttons=node('div','actions');buttons.append(action('继续编辑',()=>{closeDialog(false,owner);resolve(false);}),action('放弃修改',()=>{inlineEditor=null;closeDialog(false,owner);resolve(true);},'danger'));body.append(buttons);});const dialog=$('#dialog');dialog.addEventListener('close',()=>resolve(false),{once:true});});
}
function ownsDialog(owner){return owner===dialogGeneration&&$('#dialog').open&&!$('#dialog').inert;}
function closeDialog(immediate=false,owner=dialogGeneration){
 if(owner!==dialogGeneration)return;dialogEditor=null;closeSelect(false,true);const dialog=$('#dialog');clearDialogFeedback();if(!dialog.open)return;dialog.inert=true;
 const finish=()=>{if(owner===dialogGeneration){dialog.close();dialog.inert=false;}};
 if(immediate){activeMotion.get(dialog)?.cancel();finish();return;}
 const animation=motion(dialog,[{opacity:1,transform:'translateY(0) scale(1)'},{opacity:0,transform:'translateY(6px) scale(.99)'}],140);
 if(animation)animation.finished.then(()=>{if(activeMotion.get(dialog)===animation)finish();}).catch(()=>{});else finish();
}
let toastTimer,toastRevision=0;
function clearDialogFeedback(){const feedback=$('#dialog-feedback');feedback.textContent='';feedback.hidden=true;}
function toast(message,owner=dialogGeneration){if(owner!==dialogGeneration)return;if($('#dialog').open&&!$('#dialog').inert){const feedback=$('#dialog-feedback');feedback.hidden=false;feedback.textContent=message;feedback.scrollIntoView({block:'nearest'});return;}const ticket=++toastRevision;$('#toast').textContent=message;$('#toast').hidden=false;motion($('#toast'),[{opacity:0},{opacity:1}],160);clearTimeout(toastTimer);toastTimer=setTimeout(()=>{const animation=motion($('#toast'),[{opacity:1},{opacity:0}],120);if(!animation){$('#toast').hidden=true;return;}animation.finished.then(()=>{if(ticket===toastRevision)$('#toast').hidden=true;}).catch(()=>{});},5000);}
function disconnect(){csrf='';session=null;sequence++;stopApproval();approvalStatus('连接已断开',true);$('#workspace').hidden=true;$('#pair').hidden=false;$('#content').replaceChildren();currentTask=null;closeDialog(true);}
function colorRGB(value){return value.startsWith('#')?[0,2,4].map(offset=>parseInt(value.slice(1+offset,3+offset),16)/255):value.match(/[\d.]+/g).slice(0,3).map(Number).map(channel=>channel/255);}
function colorLuminance(rgb){return rgb.map(channel=>channel<=.04045?channel/12.92:((channel+.055)/1.055)**2.4).reduce((sum,channel,index)=>sum+channel*[.2126,.7152,.0722][index],0);}
function colorContrast(first,second){const a=colorLuminance(first),b=colorLuminance(second);return (Math.max(a,b)+.05)/(Math.min(a,b)+.05);}
function readableAccent(value,surface){
 const original=colorRGB(value),base=colorRGB(surface),max=Math.max(...original),min=Math.min(...original),delta=max-min,light=(max+min)/2,saturation=delta===0?0:delta/(1-Math.abs(2*light-1));
 let hue=delta===0?0:max===original[0]?((original[1]-original[2])/delta)%6:max===original[1]?(original[2]-original[0])/delta+2:(original[0]-original[1])/delta+4;hue=(hue+6)%6;
 const fromLightness=level=>{const c=(1-Math.abs(2*level-1))*saturation,x=c*(1-Math.abs(hue%2-1)),m=level-c/2;return (hue<1?[c,x,0]:hue<2?[x,c,0]:hue<3?[0,c,x]:hue<4?[0,x,c]:hue<5?[x,0,c]:[c,0,x]).map(channel=>Math.round((channel+m)*255)/255);};
 // Primary buttons use a 15% sRGB accent tint on the current reading surface.
 const readable=rgb=>colorContrast(rgb,base)>=4.5&&colorContrast(rgb,rgb.map((channel,index)=>Math.round((.15*channel+.85*base[index])*255)/255))>=4.5;
 if(readable(original))return value;const target=colorLuminance(base)<.5?1:0;let low=0,high=1;
 for(let step=0;step<24;step++){const amount=(low+high)/2;if(readable(fromLightness(light+(target-light)*amount)))high=amount;else low=amount;}
 return '#'+fromLightness(light+(target-light)*high).map(channel=>Math.round(channel*255).toString(16).padStart(2,'0')).join('');
}
let phonePalette=null;
function applyPalette(palette=phonePalette){if(!palette)return;phonePalette=palette;if(!themeOverride)document.body.classList.toggle('dark',parseInt(palette.background.slice(1,3),16)<100);document.documentElement.style.colorScheme=document.body.classList.contains('dark')?'dark':'light';document.documentElement.style.setProperty('--phone-accent',readableAccent(palette.primary,getComputedStyle(document.body).getPropertyValue('--surface').trim()));}

async function connect(){session=await api('/api/session');csrf=session.csrf;applyPalette(session.palette);$('#pair').hidden=true;$('#workspace').hidden=false;await navigate(location.hash.slice(1)||'home',false);}
let approvalId='',approvalTimer=null,approvalSequence=0;
function stopApproval(){const id=approvalId;approvalId='';approvalSequence++;clearTimeout(approvalTimer);approvalTimer=null;return id;}
function approvalStatus(text,retry=false,verification=''){$('#approval-status').textContent=text;$('#approval-code').textContent=verification;$('#approval-verification').hidden=!verification;$('#approval-retry').hidden=!retry;$('#approval-cancel').hidden=!approvalId;}
async function cancelApproval(){const id=stopApproval();approvalStatus('连接请求已取消',true);if(id)await api('/api/approval/cancel',{id});}
async function requestApproval(){
 const ticket=++approvalSequence;$('#approval-retry').hidden=true;approvalStatus('正在请求手机授权');
 try{
  const request=await api('/api/approval/request',{});
  if(ticket!==approvalSequence){await api('/api/approval/cancel',{id:request.id});return;}
  approvalId=request.id;approvalStatus('请在手机确认连接',false,request.verification);
  const poll=async()=>{
   try{
    const result=await api('/api/approval/poll',{id:request.id});if(ticket!==approvalSequence)return;
    if(result.state==='pending'){approvalTimer=setTimeout(poll,1000);return;}
    stopApproval();
    if(result.state==='approved'){approvalStatus('正在连接手机');try{await connect();}catch(error){if(!csrf){disconnect();approvalStatus(error.message,true);}else toast(error.message);}return;}
    approvalStatus(result.state==='denied'?'手机已拒绝连接':result.state==='full'?'连接数量已满，请在手机重新配对':'连接请求已过期',true);
   }catch(error){if(ticket!==approvalSequence)return;stopApproval();approvalStatus(error.message,true);}
  };
  approvalTimer=setTimeout(poll,1000);
 }catch(error){if(ticket!==approvalSequence)return;stopApproval();approvalStatus(error.message,true);}
}
$('#approval-retry').addEventListener('click',()=>guard(requestApproval));
$('#approval-cancel').addEventListener('click',()=>guard(cancelApproval));
$('#manual-pair').addEventListener('toggle',()=>{if($('#manual-pair').open)guard(cancelApproval);});
window.addEventListener('pagehide',()=>{const id=stopApproval();if(id)fetch('/api/approval/cancel',{method:'POST',credentials:'same-origin',keepalive:true,headers:{'Content-Type':'application/json'},body:JSON.stringify({id})}).catch(()=>{});});
$('#pair-form').addEventListener('submit',event=>{event.preventDefault();guard(async()=>{const button=event.target.querySelector('button');button.disabled=true;try{await cancelApproval();await api('/api/pair',{code:$('#pair-code').value.trim()});$('#pair-code').value='';await connect();}finally{button.disabled=false;}});});
function heading(title,subtitle,buttons=[]){const head=node('div','page-heading'),left=node('div');left.append(node('h1','',title));if(subtitle)left.append(node('p','subtle',subtitle));head.append(left);if(buttons.length){const actions=node('div','heading-actions');actions.append(...buttons);head.append(actions);}return head;}
function card(title){const result=node('section','card glass');if(title){const head=node('div','section-title');head.append(node('h2','',title));result.append(head);}return result;}
function empty(parent,label){parent.append(node('p','empty',label));}
function fmt(value,allDay=false,full=false,zone){if(value===null||value===undefined)return '待确认时间';if(allDay)return new Date(value).toISOString().slice(0,10)+' · 全天';return new Intl.DateTimeFormat('zh-CN',{...(full?{month:'numeric',day:'numeric'}:{}),hour:'2-digit',minute:'2-digit',hour12:false,timeZone:zone||session?.zone}).format(new Date(value));}
function statusLabel(status){return {queued:'排队中',processing:'正在处理',needs_review:'待确认',ready:'已就绪',failed:'处理失败'}[status]||status;}
let scheduleTab=0,publication=0,todoScope=0,inboxCategory='',rangeFrom='',rangeUntil='',calendarView='week',calendarDate=new Date(),calendarDensity='standard',selectedRows=new Set();
let rangePreset=0,listSource=0,inboxOldest=false;
const scheduleOrders=new Map();
const selectionObservers=new Set();
const routeState=new Map();
function listState(){return {page,search,filter,scheduleTab,publication,todoScope,inboxCategory,rangeFrom,rangeUntil,rangePreset,listSource,scroll:scrollY};}
function restoreList(value={}){page=value.page??0;search=value.search??'';filter=value.filter??'';scheduleTab=value.scheduleTab??0;publication=value.publication??0;todoScope=value.todoScope??0;inboxCategory=value.inboxCategory??'';rangeFrom=value.rangeFrom??'';rangeUntil=value.rangeUntil??'';rangePreset=value.rangePreset??0;listSource=value.listSource??0;selectedRows.clear();}
const categories={event:'活动',task:'待办',homework:'作业'};
function taskLike(category){return category==='task'||category==='homework';}
const SHIFTED_START_INTERVAL_MILLIS=5*60*1000;
function executionStart(item){return item.executionStart??item.start??item.effectiveDue;}
function executionEnd(item){return item.executionEnd??item.end;}
function executionAllDay(item){return item.executionAllDay??item.allDay;}
function executionWhen(item,full=false){
 const start=executionStart(item),end=executionEnd(item),allDay=executionAllDay(item);
 if(start==null)return '待安排时间';
 const first=fmt(start,allDay,full,item.zone);
 return !allDay&&end!=null&&end>start?first+' — '+fmt(end,false,full&&phoneDate(start)!==phoneDate(end),item.zone):first;
}
function entry(item,click,source='',scope=''){
 const button=action('',click,'entry'),time=node('span','entry-time',fmt(executionStart(item),executionAllDay(item)));
 const main=node('span','entry-main');main.append(node('span','entry-title',item.title),node('span','entry-detail',[item.effectiveDue!=null?`截止 · ${fmt(item.effectiveDue,false,true,item.zone)}`:'',item.location,source||categories[item.category],item.needsConfirmation?'待确认':''].filter(Boolean).join(' · ')));
 button.dataset.key=`entry-${scope}-${item.kind||item.category}-${item.taskId||0}-${item.id||item.start}-${item.start}`;button.dataset.content=JSON.stringify(item);
 button.append(time,main,icon('chevron-right'));const complete=source||item.system?null:completionAction(item);if(!complete)return button;const row=node('div','entry-row');row.dataset.key=button.dataset.key;button.removeAttribute('data-key');row.append(button,complete);return row;
}
function taskRoute(item){return `task/${item.taskId}?candidate_id=${item.id}${item.occurrenceAt!=null?'&occurrenceAt='+item.occurrenceAt:''}`;}
function itemDate(item){const value=executionStart(item)??item.created;if(value==null)return '待安排';return executionAllDay(item)?new Date(value).toISOString().slice(0,10):phoneDate(value);}
function appendGrouped(parent,items,build,minimumDate=''){let previous=null;for(const item of items){const raw=itemDate(item),date=minimumDate&&raw<minimumDate?minimumDate:raw;if(date!==previous){parent.append(node('h3','list-day',date));previous=date;}parent.append(build(item));}}
function timelineTime(item){const time=executionStart(item)??Infinity;return executionAllDay(item)&&item.kind==='event'?phoneDayStart(new Date(time).toISOString().slice(0,10)):time;}
function dateISO(date){const year=date.getFullYear(),month=String(date.getMonth()+1).padStart(2,'0'),day=String(date.getDate()).padStart(2,'0');return `${year}-${month}-${day}`;}
// Calendar dates are civil dates in the phone's zone; UTC is only used for date arithmetic.
const calendarDateFormats=new Map(),calendarDayBounds=new Map();
function phoneDate(value=new Date()){const zone=session.zone;if(!calendarDateFormats.has(zone))calendarDateFormats.set(zone,new Intl.DateTimeFormat('sv-SE',{timeZone:zone,year:'numeric',month:'2-digit',day:'2-digit'}));return calendarDateFormats.get(zone).format(new Date(value));}
function civilDate(value){return typeof value==='string'?value:phoneDate(value);}
function addCivilDays(date,count){const value=new Date(date+'T00:00:00Z');value.setUTCDate(value.getUTCDate()+count);return value.toISOString().slice(0,10);}
function civilWeekday(date){return new Date(date+'T00:00:00Z').getUTCDay();}
function phoneDayStart(date){
 const key=session.zone+'|'+date;if(calendarDayBounds.has(key))return calendarDayBounds.get(key);
 // Locate the first instant of the civil day, including midnight clock changes.
 const noon=Date.parse(date+'T12:00:00Z');let low=noon-36*3600000,high=noon+36*3600000;
 while(high-low>1){const middle=Math.floor((low+high)/2);if(phoneDate(middle)<date)low=middle;else high=middle;}
 calendarDayBounds.set(key,high);return high;
}
// AgendaRepository already projects all-day entries onto phone-zone day boundaries.
function agendaDayEntries(items,date){const start=phoneDayStart(date),end=phoneDayStart(addCivilDays(date,1));return items.filter(item=>item.start<end&&Math.max(item.end??item.start,item.start+1)>start);}
const pageOrder=['home','schedule','inbox','timetable','settings'];
const dockHighlight=node('span','dock-highlight');dockHighlight.hidden=true;dockHighlight.setAttribute('aria-hidden','true');$('.bottom nav').prepend(dockHighlight);
const dockNav=$('.bottom nav'),dockState={p:0,v:0,target:0,w:0,wv:0,wt:0,s:1,sv:0,st:1,bar:1,bv:0,bt:1,stretch:1,xv:0};
let dockFrame=0,dockTime=0,dockTouch=null,dockSuppressClick=false,dockCoasting=false;
const dockCoastDamping=7.5,dockOutwardWidth=.75,dockPressTime=1.5;
let dockCoastTarget=null;
function dockLanding(position=dockState.p){const buttons=dockButtons();return buttons.reduce((best,button)=>Math.abs(position-(button.offsetLeft+button.offsetWidth/2))<best.distance?{button,distance:Math.abs(position-(button.offsetLeft+button.offsetWidth/2))}:best,{button:null,distance:Infinity}).button;}
function coastDockStep(state,first,last,dt){const edge=Math.max(first,Math.min(last,state.p)),overflow=state.p-edge;if(overflow){state.v+=(-180*overflow-18*state.v)*dt;state.p+=state.v*dt;}else{const decay=Math.exp(-dockCoastDamping*dt);state.p+=state.v*(1-decay)/dockCoastDamping;state.v*=decay;}}
function predictDockLanding(speed){const buttons=dockButtons(),first=buttons[0].offsetLeft+buttons[0].offsetWidth/2,last=buttons.at(-1).offsetLeft+buttons.at(-1).offsetWidth/2,state={p:dockState.p,v:speed};for(let frame=0;frame<2400;frame++){coastDockStep(state,first,last,1/120);if(Math.abs(state.v)<8&&state.p>=first-2&&state.p<=last+2)break;}return dockLanding(state.p);}
function startDockCoast(speed){dockCoastTarget=predictDockLanding(speed);dockState.v=speed;dockCoasting=true;if(dockCoastTarget)guard(()=>navigate(dockCoastTarget.dataset.page));if(reducedMotion.matches){commitDockCoast();snapDock();}else animateDock();}
function commitDockCoast(){dockCoasting=false;targetDock(dockCoastTarget);}
function dockButtons(){return [...dockNav.querySelectorAll('[data-page]')];}
function dockNearest(clientX){return dockButtons().reduce((best,button)=>{const rect=button.getBoundingClientRect();return Math.abs(clientX-(rect.left+rect.right)/2)<best.distance?{button,distance:Math.abs(clientX-(rect.left+rect.right)/2)}:best;},{button:null,distance:Infinity}).button;}
function softDockUpper(value,limit,approach){if(approach<=0)return Math.min(value,limit);const start=limit-approach;return value<=start?value:start+approach*Math.tanh((value-start)/approach);}
function drawDock(){const state=dockState,width=state.w*state.s*state.stretch,maximum=state.w*dockOutwardWidth;let left=softDockUpper(state.p-width/2,dockNav.offsetWidth-state.w/2,state.w/2),right=-softDockUpper(-state.p-width/2,-state.w/2,state.w/2);if(maximum>0){if(left<0)left=-maximum*Math.tanh(-left/maximum);if(right>dockNav.offsetWidth)right=dockNav.offsetWidth+maximum*Math.tanh((right-dockNav.offsetWidth)/maximum);}dockHighlight.style.width=(right-left)+'px';dockHighlight.style.transform=`translateX(${left}px) scaleY(${state.s})`;dockNav.style.transform=`scale(${state.bar})`;}
function snapDock(){const state=dockState;state.p=state.target;state.w=state.wt;state.s=state.st;state.bar=state.bt;state.stretch=1;state.v=state.wv=state.sv=state.bv=state.xv=0;drawDock();}
function advanceDock(now){dockFrame=0;const state=dockState;if(reducedMotion.matches){if(dockCoasting)commitDockCoast();snapDock();return;}const elapsed=Math.min(.064,Math.max(0,(now-dockTime)/1000)),steps=Math.max(1,Math.ceil(elapsed*120)),dt=elapsed/steps;dockTime=now;
 const buttons=dockButtons(),first=buttons[0].offsetLeft+buttons[0].offsetWidth/2,last=buttons.at(-1).offsetLeft+buttons.at(-1).offsetWidth/2;
 for(let i=0;i<steps;i++){if(dockCoasting)coastDockStep(state,first,last,dt);else{state.v+=(180*(state.target-state.p)-22*state.v)*dt;state.p+=state.v*dt;}state.wv+=(200*(state.wt-state.w)-22*state.wv)*dt;state.w+=state.wv*dt;
  const pressTime=state.st>1?dockPressTime:1,stiffness=900/(pressTime*pressTime),damping=50/pressTime;
  state.sv+=(stiffness*(state.st-state.s)-damping*state.sv)*dt;state.s+=state.sv*dt;const stretch=1+Math.min(.25,Math.abs(state.v)/Math.max(1,state.w)*.025);
  state.xv+=(200*(stretch-state.stretch)-18*state.xv)*dt;state.stretch+=state.xv*dt;state.bv+=(stiffness*(state.bt-state.bar)-damping*state.bv)*dt;state.bar+=state.bv*dt;}
 if(dockCoasting&&Math.abs(state.v)<8&&state.p>=first-2&&state.p<=last+2)commitDockCoast();
 drawDock();const moving=dockCoasting||Math.abs(state.p-state.target)>.05||Math.abs(state.v)>.1||Math.abs(state.w-state.wt)>.05||Math.abs(state.wv)>.1||Math.abs(state.s-state.st)>.001||Math.abs(state.sv)>.01||Math.abs(state.bar-state.bt)>.001||Math.abs(state.bv)>.01||Math.abs(state.stretch-1)>.001||Math.abs(state.xv)>.01;
 if(moving)dockFrame=requestAnimationFrame(advanceDock);else snapDock();
}
function animateDock(){if(reducedMotion.matches){if(dockFrame)cancelAnimationFrame(dockFrame);dockFrame=0;snapDock();}else if(!dockFrame){dockTime=performance.now();dockFrame=requestAnimationFrame(advanceDock);}}
function pressDock(pressed){dockState.st=pressed?1.5:1;dockState.bt=pressed?1.1:1;animateDock();}
function targetDock(button){if(!button||dockCoasting)return;dockState.target=button.offsetLeft+button.offsetWidth/2;dockState.wt=button.offsetWidth;animateDock();}
function updateDock(){const selected=dockNav.querySelector('button.active');if(!selected){dockHighlight.hidden=true;return;}const first=dockHighlight.hidden;dockHighlight.hidden=false;if(first){dockState.target=selected.offsetLeft+selected.offsetWidth/2;dockState.wt=selected.offsetWidth;snapDock();}else targetDock(selected);}
function dockPointerVelocity(samples){const recent=samples.filter(sample=>sample.time>=samples.at(-1).time-100);if(recent.length<2)return 0;
 const meanTime=recent.reduce((sum,sample)=>sum+sample.time,0)/recent.length,meanX=recent.reduce((sum,sample)=>sum+sample.x,0)/recent.length;
 let numerator=0,denominator=0;for(const sample of recent){numerator+=(sample.time-meanTime)*(sample.x-meanX);denominator+=(sample.time-meanTime)**2;}return denominator>0?numerator/denominator*1000:0;}
function recordDockPointer(touch,event){const samples=event.getCoalescedEvents?.()||[];for(const sample of samples.length?samples:[event])touch.samples.push({x:sample.clientX,time:sample.timeStamp});touch.samples=touch.samples.filter(sample=>sample.time>=event.timeStamp-150);}
new ResizeObserver(()=>{if(dockTouch)dockTouch.cancelled=true;updateDock();}).observe(dockNav);
// Detail controls stay above the real Dock, including enlarged browser text.
function updateDockClearance(){const bottom=$('.bottom');document.documentElement.style.setProperty('--dock-clearance',(bottom.offsetHeight+(parseFloat(getComputedStyle(bottom).bottom)||0)+20)+'px');}
new ResizeObserver(updateDockClearance).observe($('.bottom'));
window.addEventListener('resize',updateDockClearance);
dockNav.addEventListener('pointerdown',event=>{if(event.button!==0)return;if(dockTouch){dockTouch.cancelled=true;dockCoasting=false;pressDock(false);return;}dockCoasting=false;dockTouch={id:event.pointerId,x:event.clientX,y:event.clientY,dragging:false,cancelled:false,left:false,samples:[]};recordDockPointer(dockTouch,event);dockNav.setPointerCapture(event.pointerId);pressDock(true);});
dockNav.addEventListener('pointermove',event=>{const touch=dockTouch;if(!touch||touch.id!==event.pointerId||touch.cancelled)return;const dx=Math.abs(event.clientX-touch.x),dy=Math.abs(event.clientY-touch.y),rect=dockNav.getBoundingClientRect();
 recordDockPointer(touch,event);
 touch.left ||= event.clientX<rect.left||event.clientX>rect.right||event.clientY<rect.top||event.clientY>rect.bottom;if(!touch.dragging&&dx>8&&dx>dy*1.25)touch.dragging=true;
 if(touch.dragging){const buttons=dockButtons(),ratio=dockNav.offsetWidth/rect.width,x=(event.clientX-rect.left)*ratio;dockState.target=Math.max(buttons[0].offsetLeft+buttons[0].offsetWidth/2,Math.min(buttons.at(-1).offsetLeft+buttons.at(-1).offsetWidth/2,x));dockState.wt=dockNearest(event.clientX).offsetWidth;animateDock();}
});
function finishDockPointer(event,cancelled=false){const touch=dockTouch;if(!touch||touch.id!==event.pointerId)return;dockTouch=null;pressDock(false);const button=dockNearest(event.clientX),rect=dockNav.getBoundingClientRect(),inside=event.clientX>=rect.left&&event.clientX<=rect.right&&event.clientY>=rect.top&&event.clientY<=rect.bottom;
 recordDockPointer(touch,event);const releaseVelocity=dockPointerVelocity(touch.samples)*dockNav.offsetWidth/rect.width;
 dockSuppressClick=true;setTimeout(()=>{dockSuppressClick=false;},0);if(dockNav.hasPointerCapture(event.pointerId))dockNav.releasePointerCapture(event.pointerId);
 if(!cancelled&&!touch.cancelled&&button&&(touch.dragging||(!touch.left&&inside))){if(touch.dragging)startDockCoast(releaseVelocity);else guard(()=>navigate(button.dataset.page));}else {dockCoasting=false;updateDock();}
}
dockNav.addEventListener('pointerup',event=>finishDockPointer(event));dockNav.addEventListener('pointercancel',event=>finishDockPointer(event,true));
dockNav.addEventListener('lostpointercapture',()=>{if(dockTouch){dockTouch=null;pressDock(false);updateDock();}});
dockNav.addEventListener('click',event=>{if(dockSuppressClick&&event.detail!==0){event.preventDefault();event.stopImmediatePropagation();return;}dockCoasting=false;},true);
window.addEventListener('pointerdown',event=>{if(dockTouch&&event.pointerId!==dockTouch.id){dockTouch.cancelled=true;pressDock(false);}},true);
window.addEventListener('blur',()=>{dockTouch=null;dockCoasting=false;pressDock(false);updateDock();});
reducedMotion.addEventListener('change',()=>{if(reducedMotion.matches){if(dockFrame)cancelAnimationFrame(dockFrame);dockFrame=0;if(dockCoasting)commitDockCoast();snapDock();}});
let revealObserver=null;
function clearReveals(){revealObserver?.disconnect();revealObserver=null;document.querySelectorAll('.reveal-pending').forEach(element=>element.classList.remove('reveal-pending'));}
function observeCards(host){
 clearReveals();if(reducedMotion.matches)return;
 revealObserver=new IntersectionObserver(entries=>{for(const entry of entries){if(!entry.isIntersecting)continue;const element=entry.target;revealObserver?.unobserve(element);element.classList.remove('reveal-pending');motion(element,[{opacity:0,transform:'translateY(12px)'},{opacity:1,transform:'translateY(0)'}],260);}},{threshold:.05});
 for(const element of host.querySelectorAll('.card,.course')){if(element.getBoundingClientRect().top<innerHeight)continue;element.classList.add('reveal-pending');revealObserver.observe(element);}
}
reducedMotion.addEventListener('change',()=>{if(!reducedMotion.matches)return;clearReveals();for(const animation of runningMotions)if(animation.playState==='running')animation.finish();});
async function navigate(next,updateHash=true,initialState=null){
 if(inlineEditor?.form.isConnected&&!await leaveInlineEditor()){history.replaceState(null,'','#'+route);return;}
 rememberDetailScroll();
 const [path,query]=next.split('?');
 if(path==='schedule'&&query){const params=new URLSearchParams(query);initialState=history.state?.schedule??{filter:params.get('category')||'',scheduleTab:Number(params.get('tab')||0),todoScope:Number(params.get('todo')||0)};next=path;}
 const previous=route,changed=previous!==next;
 if(changed){routeState.delete(previous);routeState.set(previous,listState());while(routeState.size>10)routeState.delete(routeState.keys().next().value);}
 route=next;restoreList(initialState??(changed?routeState.get(next):listState()));currentTask=null;
 if(next.startsWith('task/')&&new URLSearchParams(query||'').has('candidate_id'))detailPosition(Number(path.split('/')[1])).detailOpen=true;
 const destination=next==='schedule'&&initialState?`schedule?${new URLSearchParams({category:filter,tab:scheduleTab,todo:todoScope})}`:next;
 if(updateHash&&location.hash!==`#${destination}`)history.pushState(next==='schedule'?{schedule:listState()}:null,'',`#${destination}`);
 document.querySelectorAll('[data-page]').forEach(button=>{const selected=button.dataset.page===next;button.classList.toggle('active',selected);if(selected)button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');});
 updateDock();const direction=next.startsWith('task/')?1:previous.startsWith('task/')?-1:Math.sign(pageOrder.indexOf(next)-pageOrder.indexOf(previous));
 await render(changed?{direction,detail:next.startsWith('task/')}:false);
 if(changed)window.scrollTo(0,routeState.get(next)?.scroll??0);
}
window.addEventListener('popstate',()=>guard(()=>navigate(location.hash.slice(1)||'home',false)));
async function render(animate=false){if(inlineEditor?.form.isConnected)return;const ticket=++sequence,host=$('#content');if(!host.childElementCount)host.append(node('p','loading','正在读取手机…'));let content;
 if(route.startsWith('task/'))content=await taskPage(Number(route.split('/')[1].split('?')[0]),ticket);
 else if(route==='home')content=await homePage(ticket);
 else if(route==='inbox'||route==='schedule')content=await listPage(ticket);
 else if(route==='timetable')content=await timetablePage(ticket);
 else if(route==='calendar')content=await calendarPage(ticket);
 else if(route==='settings')content=settingsPage();
 else{route='home';content=await homePage(ticket);}
 if(ticket===sequence){const active=document.activeElement,label=host.contains(active)?active.getAttribute('aria-label'):null,selection=label&&'selectionStart' in active?[active.selectionStart,active.selectionEnd]:null,scrollY=window.scrollY;
  clearReveals();for(const observer of selectionObservers)observer.disconnect();selectionObservers.clear();
  const previous=new Map([...host.querySelectorAll('[data-key]')].map(element=>[element.dataset.key,{element,rect:element.getBoundingClientRect()}]));
  const incoming=document.createDocumentFragment();incoming.append(...content);
  const local=!animate||animate.local;
  const reused=new Set();
  if(local)for(const element of incoming.querySelectorAll('[data-key]')){const old=previous.get(element.dataset.key);if(old&&!reused.has(old.element)&&!element.querySelector('input')&&old.element.isEqualNode(element)){element.replaceWith(old.element);reused.add(old.element);}}
  $('#workspace').classList.toggle('task-mode',route.startsWith('task/'));
  host.replaceChildren(incoming);icons();
  if(route==='schedule')history.replaceState({schedule:listState()},'',`#schedule?${new URLSearchParams({category:filter,tab:scheduleTab,todo:todoScope})}`);
  if(local){window.scrollTo(0,scrollY);for(const element of host.querySelectorAll('[data-key]')){const old=previous.get(element.dataset.key);if(!old)continue;const rect=element.getBoundingClientRect(),x=old.rect.x-rect.x,y=old.rect.y-rect.y;if(Math.abs(x)+Math.abs(y)>1)motion(element,[{transform:`translate(${x}px,${y}px)`},{transform:'translate(0,0)'}],280);}}
  if(label&&local){const replacement=[...host.querySelectorAll('[aria-label]')].find(element=>element.getAttribute('aria-hidden')!=='true'&&element.getAttribute('aria-label')===label);replacement?.focus({preventScroll:true});if(selection&&replacement?.setSelectionRange)replacement.setSelectionRange(...selection);}
  if(animate&&!animate.local){motion(host,[{opacity:.55,transform:`translateX(${(animate.direction||1)*(animate.detail?20:12)}px)`},{opacity:1,transform:'translateX(0)'}],240);observeCards(host);}
 }}
async function homePage(ticket){
 const data=await api('/api/home');if(ticket!==sequence)return [];revision=data.revision;
 const columns=node('div','columns'),agenda=card('时间线'),list=node('div','timeline');
 const events=[...data.items.map(item=>({...item,kind:'event'})),...data.calendar.map(item=>({...item,kind:'calendar'}))].sort((a,b)=>timelineTime(a)-timelineTime(b));
 const future=events.slice(0,data.limit??30);
 appendGrouped(list,future,item=>entry(item,()=>item.kind==='event'?navigate(taskRoute(item)):showReadOnly(item),item.kind==='calendar'?item.source||'日历':'','home-timeline'),data.date);
 if(!future.length)empty(list,'暂无未来安排');agenda.append(list);
 const summary=card();summary.classList.add('summary');const date=new Date(data.date+'T12:00:00');
 summary.append(node('div','eyebrow','TODAY'),node('p','date-large',String(date.getDate()).padStart(2,'0')),node('p','date-label',new Intl.DateTimeFormat('zh-CN',{month:'long',weekday:'long'}).format(date)));
 const stats=node('div','stat-row');for(const [value,label,status]of [[data.review,'待确认','1'],[data.failed,'处理失败','3']]){const button=action('',async()=>{await navigate('inbox');filter=status;page=0;await render();},'stat');button.append(node('strong','',String(value)),node('span','',label));stats.append(button);}summary.append(stats);
 const shortcuts=node('div','detail-actions');shortcuts.append(action('查看日历',()=>navigate('calendar')),action('最近作业',()=>openSchedule({filter:'homework',scheduleTab:3,todoScope:3})),action('记录',capture,'primary'));summary.append(shortcuts);columns.append(agenda,summary);
 const result=[heading('首页',data.date),columns];
 const recentItems=[...(data.recent||[]).map(item=>({...item,kind:'event'})),...data.calendar.map(item=>({...item,kind:'calendar'}))].sort((a,b)=>timelineTime(a)-timelineTime(b)).slice(0,5);if(recentItems.length){const recent=card('最近事项');appendGrouped(recent,recentItems,item=>entry(item,()=>item.kind==='event'?navigate(taskRoute(item)):showReadOnly(item),item.kind==='calendar'?item.source||'日历':'','home-recent'),data.date);result.push(recent);}
 if(data.todos){const todo=card('待办 · '+data.todos.total),counts=node('div','stat-row');
  for(const [value,label,scope,tab]of [[data.todos.todayDue,'今日截止',1,3],[data.todos.overdue,'已逾期',2,3],[data.todos.unscheduled,'待安排',3,1]]){const b=action('',()=>openSchedule({scheduleTab:tab,todoScope:scope}),'stat');b.append(node('strong','',String(value)),node('span','',label));counts.append(b);}todo.append(counts);
  for(const item of data.todos.items)todo.append(entry(item,()=>navigate(taskRoute(item)),'','home-todos'));
  todo.append(action('全部待办',()=>openSchedule({scheduleTab:3,todoScope:3}),'quick-action'));result.push(todo);
 }
 return result;
}
async function openSchedule(values){await navigate('schedule',true,values);}
function showReadOnly(item){showDialog(item.title,body=>{
 body.append(node('p','subtle',`${fmt(item.start,item.allDay,true)} — ${fmt(item.end,item.allDay,true)}`));
 if(item.source)body.append(node('span','tag',item.source));if(item.location)body.append(node('p','raw-text',item.location));if(item.description)body.append(node('p','raw-text',item.description));
});}
function searchForm(inbox){
 const form=node('form','filters'),input=node('input');input.type='search';input.placeholder=inbox?'搜索收件':'搜索日程';input.setAttribute('aria-label',input.placeholder);input.value=search;input.maxLength=256;
 const select=node('select');select.setAttribute('aria-label',inbox?'收件状态':'日程类型');
 for(const [value,label]of (inbox?[['','全部状态'],['1','待确认'],['2','处理中'],['3','失败'],['4','已写入日历']]:[['','全部类型'],...Object.entries(categories)])){const option=node('option','',label);option.value=value;select.append(option);}select.value=filter;
 select.addEventListener('change',()=>{filter=select.value;if(!inbox&&!taskLike(filter))todoScope=0;search=input.value.trim();refreshList();});
 const submit=node('button','','搜索');submit.type='submit';form.append(input,select,submit,action('筛选',()=>filterDialog(inbox)),action('清除筛选',()=>{restoreList();return render({local:true});}));
 form.addEventListener('submit',event=>{event.preventDefault();search=input.value.trim();filter=select.value;refreshList();});return form;
}
function refreshList(){page=0;selectedRows.clear();return guard(()=>render({local:true,direction:1}));}
function selectionBar(label,choices,value,change){
 const host=node('div','selection-host'),bar=node('div','selection-bar');bar.setAttribute('role','group');bar.setAttribute('aria-label',label);
 const highlight=node('span','selection-highlight');bar.append(highlight);
 let active;
 for(const [key,text]of choices){const button=action(text,()=>{active=button;bar.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));position();change(key);});button.setAttribute('aria-pressed',String(String(key)===String(value)));if(String(key)===String(value))active=button;bar.append(button);}
 const position=()=>{if(active){highlight.style.width=active.offsetWidth+'px';highlight.style.transform=`translateX(${active.offsetLeft}px)`;}};
 host.append(bar);requestAnimationFrame(()=>{if(!bar.isConnected)return;position();const observer=new ResizeObserver(position);selectionObservers.add(observer);observer.observe(bar);});return host;
}
function monthAfter(date,amount=1){const d=new Date(date.slice(0,8)+'01T00:00:00Z');d.setUTCMonth(d.getUTCMonth()+amount);return d.toISOString().slice(0,10);}
function presetWindow(value){const today=phoneDate();if(value===1)return [today,addCivilDays(today,1)];if(value===2){const from=addCivilDays(today,-(civilWeekday(today)+6)%7);return [from,addCivilDays(from,7)];}if(value===3){const from=today.slice(0,8)+'01';return [from,monthAfter(from)];}return ['',''];}
function filterDialog(inbox){showDialog('筛选'+(inbox?'收件':'日程'),(body,owner)=>{
 let type=inbox?inboxCategory:filter,pub=publication,todo=todoScope,from=rangeFrom,until=rangeUntil,preset=rangePreset,source=listSource,tab=scheduleTab;
 const draw=()=>{body.replaceChildren();const add=(label,choices,value,change,disabled=false)=>{const bar=selectionBar(label,choices,value,value=>{change(value);draw();});if(disabled)bar.querySelectorAll('button').forEach(button=>button.disabled=true);body.append(node('h3','filter-label',label),bar);};
 if(!inbox){add('日期',[[0,'全部'],[1,'今天'],[2,'本周'],[3,'本月'],[5,'自选']],preset,value=>{preset=Number(value);if(preset!==5)[from,until]=presetWindow(preset);if(preset)tab=3;});
 if(from&&until){const row=node('div','range-step');const shift=direction=>{if(preset===3){from=monthAfter(from,direction);until=monthAfter(from);}else{const count=Math.round((Date.parse(until)-Date.parse(from))/86400000);from=addCivilDays(from,direction*count);until=addCivilDays(until,direction*count);preset=5;}tab=3;draw();};row.append(action('上一段',()=>shift(-1)),node('span','',`${from} — ${addCivilDays(until,-1)}`),action('下一段',()=>shift(1)));body.append(row);}
 if(preset===5){const dates=node('div','two-fields'),a=node('div'),b=node('div'),start=field(a,'起始日期',from,'date','range-from'),end=field(b,'结束日期',until?addCivilDays(until,-1):'','date','range-until');dates.append(a,b);body.append(dates);start.addEventListener('change',()=>{from=start.value;tab=3;});end.addEventListener('change',()=>{until=end.value?addCivilDays(end.value,1):'';tab=3;});}}
 add('类型',[['','全部'],...Object.entries(categories)],source&&!inbox?'event':type,value=>{type=value;if(!taskLike(type))todo=0;},!inbox&&source===1);
 if(!inbox){add('待办',[[0,'不限'],[1,'今日截止'],[2,'已逾期'],[3,'未完成']],source?0:todo,value=>{todo=Number(value);if(todo){if(type!=='homework')type='task';tab=3;}},source===1);add('日历状态',[[0,'全部'],[3,'未写入'],[1,'待确认'],[2,'已写入']],source?2:pub,value=>pub=Number(value),source===1);add('来源',[[0,'拾时'],[1,'系统日程']],source,value=>{source=Number(value);if(source){type='';pub=todo=0;}});}
 const buttons=node('div','actions');buttons.append(action('应用筛选',()=>{if(!inbox&&((!from)!==(!until)||from&&until<=from))throw new Error('请选择完整且有效的日期范围');if(inbox)inboxCategory=type;else{filter=type;publication=pub;todoScope=todo;rangeFrom=from;rangeUntil=until;rangePreset=preset;listSource=source;scheduleTab=tab;}closeDialog(false,owner);return refreshList();},'primary'));body.append(buttons);icons();};draw();
});}
async function listPage(ticket){
 const inbox=route==='inbox',params=new URLSearchParams({page,search});
 const remembered=inbox?inboxOldest:scheduleOrders.get(scheduleTab);if(remembered!==undefined)params.set('oldestFirst',remembered);
 if(filter)params.set(inbox?'status':'category',filter);
 if(inbox){if(inboxCategory)params.set('category',inboxCategory);}else{params.set('tab',scheduleTab);params.set('publication',publication);params.set('todo',todoScope);params.set('source',listSource);if(rangeFrom&&rangeUntil){params.set('from',rangeFrom);params.set('until',rangeUntil);}}
 const path=`/api/${route}?${params}`,data=await api(path);if(ticket!==sequence)return [];revision=data.revision;page=data.page;
 if(inbox)inboxOldest=data.oldestFirst;else scheduleOrders.set(scheduleTab,data.oldestFirst);
 const toolbar=node('div','bulk-toolbar'),selection=node('span','subtle'),write=action('写入所选',()=>confirmAction('批量写入日历','只写入信息完整的事项，其余保留。',async()=>{const result=await api('/api/calendar/publish-selected',{ids:[...selectedRows].map(String),inbox,revision});selectedRows.clear();await render({local:true});toast(result.summary);}), 'primary');
 const remove=action('删除所选',()=>confirmAction('删除所选',inbox?'删除所选原文、日程及关联日历事件，公共 Downloads 文件保留。':'删除所选日程及关联日历事件；重复日程会删除整个系列，收件原文保留。',async()=>{await api('/api/selection/delete',{ids:[...selectedRows].map(String),inbox,revision});selectedRows.clear();await render({local:true});}),'danger');
 const update=()=>{selection.textContent=`已选 ${selectedRows.size} 条`;write.disabled=!selectedRows.size||!session.calendarAllowed;remove.disabled=!selectedRows.size;};
 const all=action('选择本页',()=>{const ids=data.items.map(item=>item.id),full=ids.every(id=>selectedRows.has(id));for(const id of ids){if(full)selectedRows.delete(id);else selectedRows.add(id);}for(const box of list.querySelectorAll('input[type=checkbox]'))box.checked=selectedRows.has(Number(box.value));update();});
 const matching=action('全选筛选结果',async()=>{const snapshot=await api(path+'&includeIds=true');if(snapshot.revision!==revision)throw new Error('数据已更新，请刷新后重试');selectedRows=new Set(snapshot.ids);for(const box of list.querySelectorAll('input[type=checkbox]'))box.checked=selectedRows.has(Number(box.value));update();});
 toolbar.append(all,matching,selection,write,remove);update();
 const list=node('div','result-list');
 let day='';for(const item of data.items){const date=itemDate(item);if(date!==day){list.append(node('h3','list-day',date));day=date;}const row=node('article','selectable-result'),box=node('input');row.dataset.key=`${inbox?'task':'candidate'}-${item.id}`;box.type='checkbox';box.value=item.id;box.checked=selectedRows.has(item.id);box.setAttribute('aria-label','选择 '+(inbox?item.text.slice(0,40):item.title));box.addEventListener('change',()=>{if(box.checked)selectedRows.add(item.id);else selectedRows.delete(item.id);update();});
 const button=action('',()=>data.system?showReadOnly(item):navigate(inbox?`task/${item.id}`:taskRoute(item)),'card glass');
 if(inbox){button.append(node('span','status-label',statusLabel(item.status)),node('h3','',item.text.slice(0,120)||'附件记录'),node('p','subtle',item.error||item.source));const meta=node('div','meta');meta.append(node('span','',fmt(item.created,false,true)),icon('chevron-right'));button.append(meta);}
 else{button.append(node('span','status-label',categories[item.category]+(item.completed?' · 已完成':item.calendarLinked?' · 已写入日历':item.needsConfirmation?' · 待确认':'')),node('h3','',item.title),node('p','subtle',executionWhen(item,true)));if(item.effectiveDue!=null)button.append(node('p','subtle',`截止 · ${fmt(item.effectiveDue,false,true,item.zone)}`));if(item.location)button.append(node('p','subtle',item.location));}
 if(item.recurrence)button.append(node('span','tag',item.repeatLabel));row.append(button);if(!data.system){row.append(box);if(!inbox){const state=completionAction(item);if(state)row.append(state);}}list.append(row);}
 if(!data.items.length)empty(list,inbox?'没有符合条件的收件':'没有符合条件的日程');
 const order=action(data.oldestFirst?'最早优先':'最近优先',()=>{if(inbox)inboxOldest=!data.oldestFirst;else scheduleOrders.set(scheduleTab,!data.oldestFirst);return refreshList();});
 const result=[heading(inbox?'收件箱':'日程',`共 ${data.total} 条${inbox?'收件':'日程'}`,[order,...(inbox?[]:[action('查看日历',()=>navigate('calendar'))])]),searchForm(inbox)];
 if(!inbox)result.push(selectionBar('日程范围',[[0,'待处理'],[1,'待安排'],[2,'历史'],[3,'全部']],scheduleTab,value=>{scheduleTab=Number(value);refreshList();}));
 const tags=[inbox?categories[inboxCategory]:categories[filter],!inbox&&listSource?'系统日程':'',!inbox&&['','待确认','已写入','未写入'][publication],!inbox&&['','今日截止','已逾期','未完成'][todoScope],!inbox&&(rangeFrom||data.from)&&`${rangeFrom||data.from} — ${addCivilDays(rangeUntil||data.until,-1)}`].filter(Boolean);
 if(tags.length)result.push(node('p','filter-summary',tags.join(' · ')));if(!data.system)result.push(toolbar);result.push(list,pagination(data));return result;
}
function pagination(data){const total=Math.max(1,Math.ceil(data.total/data.pageSize)),row=node('div','pagination'),turn=async direction=>{page+=direction;await render({local:true,direction});window.scrollTo({top:0,behavior:reducedMotion.matches?'instant':'smooth'});},previous=action('',()=>turn(-1)),next=action('',()=>turn(1));previous.append(icon('chevron-left'));next.append(icon('chevron-right'));previous.setAttribute('aria-label','上一页');next.setAttribute('aria-label','下一页');previous.disabled=page<=0;next.disabled=page+1>=total;row.append(previous,node('span','',`${page+1} / ${total}`),next);return row;}
function showDialog(title,build){const owner=++dialogGeneration;dialogEditor=null;closeSelect(false,true);clearDialogFeedback();const dialog=$('#dialog'),wasOpen=dialog.open;dialog.inert=false;$('#dialog-title').textContent=title;$('#dialog-body').replaceChildren();build($('#dialog-body'),owner);if(!dialog.open)dialog.showModal();else dialog.querySelector('input,textarea,button')?.focus({preventScroll:true});icons();motion(dialog,[{opacity:wasOpen?1:0,transform:wasOpen?'translateY(0) scale(1)':'translateY(10px) scale(.99)'},{opacity:1,transform:'translateY(0) scale(1)'}]);return owner;}
$('#close-dialog').addEventListener('click',requestDialogClose);$('#dialog').addEventListener('cancel',event=>{event.preventDefault();requestDialogClose();});
// Only a gesture that starts and ends on the actual backdrop dismisses the dialog.
let backdropPointer=null;
function outsideDialog(event){const r=$('#dialog').getBoundingClientRect();return event.target===$('#dialog')&&(event.clientX<r.left||event.clientX>r.right||event.clientY<r.top||event.clientY>r.bottom);}
$('#dialog').addEventListener('pointerdown',event=>{backdropPointer=event.button===0&&outsideDialog(event)?event.pointerId:null;});
$('#dialog').addEventListener('pointerup',event=>{if(backdropPointer===event.pointerId&&outsideDialog(event))requestDialogClose();backdropPointer=null;});
$('#dialog').addEventListener('pointercancel',()=>backdropPointer=null);
function confirmAction(title,message,work){showDialog(title,(body,owner)=>{body.append(node('p','subtle',message));const buttons=node('div','actions');buttons.append(action('取消',()=>closeDialog()),action('确认',async()=>{await work(owner);closeDialog(false,owner);},'primary'));body.append(buttons);});}
function field(parent,label,value='',type='text',name=''){const id=`field-${name||Math.random().toString(16).slice(2)}`;const caption=node('label','',label);caption.htmlFor=id;const input=node(type==='textarea'?'textarea':type==='select'?'select':'input');input.id=id;input.name=name;input.value=value??'';if(type!=='textarea'&&type!=='select')input.type=type;parent.append(caption,input);return input;}
const MAX_ATTACHMENT_BYTES=20*1024*1024;
let attachmentWork=0;
async function uploadAttachment(id,version,file){
 pending++;try{const response=await fetch('/api/attachment-upload',{method:'POST',credentials:'same-origin',headers:{'Content-Type':'application/octet-stream','X-Chrona-CSRF':csrf,'X-Chrona-Task-Id':String(id),'X-Chrona-Revision':String(version),'X-Chrona-Filename':encodeURIComponent(file.name),'X-Chrona-Image':String(file.type.startsWith('image/'))},body:file});const result=await response.json();if(response.status===401){disconnect();throw new Error('连接已失效，请重新配对');}if(!response.ok){const error=new Error(result.error||'附件上传失败');error.status=response.status;throw error;}return result;}catch(error){if(error instanceof TypeError)throw new Error('无法连接手机，附件尚未上传');throw error;}finally{pending--;}
}
function attachmentPicker(parent){
 const picker=node('div','attachment-picker'),input=node('input'),list=node('div','upload-list');input.type='file';input.multiple=true;input.setAttribute('aria-label','选择图片或文件');const chosen=[];let latestRevision=null,locked=false,stale=false;
 const draw=()=>{list.replaceChildren();for(const item of chosen){const row=node('div','upload-row'),text=node('span','',item.file.name+' · '+(item.done?'已上传':item.error||`${(item.file.size/1024/1024).toFixed(1)} MB`));row.append(text);if(!item.done)row.append(action('移除',()=>{chosen.splice(chosen.indexOf(item),1);draw();},'upload-remove'));row.querySelectorAll('button').forEach(button=>button.disabled=locked);list.append(row);}};
 input.addEventListener('change',()=>{for(const file of input.files){if(file.size>MAX_ATTACHMENT_BYTES){toast(file.name+' 超过 20 MB');continue;}chosen.push({file,done:false});}input.value='';draw();});picker.append(input,list);parent.append(picker);
 return {chosen,lock(value){locked=value;input.disabled=value;list.querySelectorAll('button').forEach(button=>button.disabled=value);},async upload(id,version){if(stale){const current=await api(`/api/task?id=${id}`);latestRevision=current.revision;stale=false;}version=latestRevision??version;for(const item of chosen){if(item.done)continue;try{const result=await uploadAttachment(id,version,item.file);version=result.revision;latestRevision=version;revision=version;item.done=true;item.error='';draw();}catch(error){if(error.status===409){stale=true;error.message='记录已更新，请重试未完成附件';}item.error=error.message;draw();throw error;}}return version;}};
}
function capture(){showDialog('记录',(body,owner)=>{
 const form=node('form'),input=field(form,'原文','','textarea','text');input.maxLength=100000;const attachments=attachmentPicker(form),label=node('label','checkbox'),checkbox=node('input');checkbox.type='checkbox';checkbox.checked=true;label.append(checkbox,document.createTextNode('交给手机 AI 整理为日程'));form.append(label);
 const buttons=node('div','actions'),submit=node('button','primary','保存记录');submit.type='submit';buttons.append(submit);form.append(buttons);let saved=null,working=false,stale=false;
 form.addEventListener('submit',event=>{event.preventDefault();guard(async()=>{if(working)return;if(!saved&&!input.value.trim()&&!attachments.chosen.length)throw new Error('请输入原文或选择附件');working=true;attachmentWork++;submit.disabled=true;attachments.lock(true);clearDialogFeedback();try{
  if(!saved){saved=await api('/api/task/create',{text:input.value.trim()||(attachments.chosen.every(item=>item.file.type.startsWith('image/'))?'图片记录':'附件记录'),process:false});input.readOnly=true;submit.textContent='继续保存';}
  if(stale){saved.revision=(await api(`/api/task?id=${saved.id}`)).revision;stale=false;}saved.revision=await attachments.upload(saved.id,saved.revision);
  if(checkbox.checked)await api('/api/task/process',{id:String(saved.id),revision:saved.revision});
  if(ownsDialog(owner)){closeDialog(false,owner);await navigate(`task/${saved.id}`);}
 }catch(error){if(error.status===409)stale=true;if(saved&&ownsDialog(owner)&&!buttons.querySelector('.view-saved'))buttons.append(action('查看已保存记录',async()=>{closeDialog(false,owner);await navigate(`task/${saved.id}`);},'view-saved'));throw error;}finally{working=false;attachmentWork--;submit.disabled=false;attachments.lock(false);}});});body.append(form);
});}
function appendAttachments(id,version){showDialog('添加附件',(body,owner)=>{const attachments=attachmentPicker(body),save=action('上传附件',async()=>{if(!attachments.chosen.some(item=>!item.done))throw new Error('请先选择附件');save.disabled=true;attachments.lock(true);attachmentWork++;try{version=await attachments.upload(id,version);if(ownsDialog(owner)){closeDialog(false,owner);await render();}}finally{attachmentWork--;save.disabled=false;attachments.lock(false);}},'primary');const buttons=node('div','actions');buttons.append(save);body.append(buttons);});}
function disclosure(label,text){const details=node('details'),summary=node('summary','subtle',label),panel=node('div','disclosure-body');panel.append(node('p','raw-text',text));details.append(summary,panel);let expanded=false;summary.addEventListener('click',event=>{event.preventDefault();const start=details.open?panel.getBoundingClientRect().height:0;expanded=!expanded;details.open=true;summary.setAttribute('aria-expanded',String(expanded));const end=expanded?panel.scrollHeight:0,animation=motion(panel,[{height:start+'px',opacity:start?1:0,overflow:'hidden'},{height:end+'px',opacity:expanded?1:0,overflow:'hidden'}],160);if(!animation){details.open=expanded;return;}animation.finished.then(()=>{if(activeMotion.get(panel)===animation&&!expanded)details.open=false;}).catch(()=>{});});return details;}
// The selected business identity survives urgency sorting, edit and history restoration.
const detailPositions=new Map();
function detailPosition(id){if(!detailPositions.has(id)){detailPositions.set(id,{candidate:null,scroll:new Map()});if(detailPositions.size>10)detailPositions.delete(detailPositions.keys().next().value);}return detailPositions.get(id);}
function rememberDetailScroll(){const workspace=$('.task-workspace');if(!workspace)return;const saved=detailPosition(Number(workspace.dataset.task));saved.listScroll=workspace.querySelector('.master-list')?.scrollTop||0;saved.scroll.set(saved.candidate,window.scrollY);}
function candidateDeadline(item){return item.effectiveDue??item.due??executionStart(item)??null;}
function urgencyCandidates(items){return [...items].sort((a,b)=>{const complete=Number(!!a.completed)-Number(!!b.completed);if(complete)return complete;const first=candidateDeadline(a)??Infinity,second=candidateDeadline(b)??Infinity;return(first===second?0:first-second)||a.id-b.id;});}
function candidateStatus(item){const due=candidateDeadline(item);if(item.completed)return {key:'done',text:'已完成',icon:'check'};if(taskLike(item.category)&&due!==null&&due<Date.now())return {key:'overdue',text:'已逾期',icon:'clock-alert'};if(taskLike(item.category)&&due!==null&&due-Date.now()<=86400000)return {key:'soon',text:'即将截止',icon:'clock-alert'};return {key:'open',text:item.needsConfirmation?'待确认':taskLike(item.category)?'未完成':due===null?'待安排':'已安排',icon:'circle'};}
function taskOriginal(data,id){
 const task=data.task,original=node('div','record-original');
 original.append(node('span','status-label',statusLabel(task.status)),node('p','raw-text',task.text));
 if(task.error)original.append(node('p','subtle',task.error));
 if(task.linkText)original.append(disclosure('链接正文',task.linkText));
 if(data.images.length){const grid=node('div','attachment-grid');for(const item of data.images){const link=node('a');link.href=`/api/attachment/${id}/image-${item.index}`;link.target='_blank';link.rel='noopener';const image=node('img');image.src=link.href;image.alt=`记录图片 ${item.index+1}`;image.loading='lazy';link.append(image);grid.append(link);}original.append(grid);}
 if(data.files.length){const files=node('div','files');for(const item of data.files){const link=node('a','',item.name+` · ${(item.size/1024).toFixed(1)} KB`);link.href=`/api/attachment/${id}/${item.id}`;files.append(link);}original.append(files);}
 const actions=node('div','detail-actions'),busy=['queued','processing'].includes(task.status),edit=action('修改原文',()=>editRaw(task));edit.disabled=busy;
 const process=action('AI 重新处理',()=>confirmAction('重新处理','现有未写入日历的日程将由新的 AI 结果替换。',async()=>{await api('/api/task/process',{id:String(id),revision});await render();}));
 process.disabled=busy||data.candidates.some(item=>item.calendarLinked);
 if(!busy)actions.append(action('添加附件',()=>appendAttachments(id,data.revision)));
 actions.append(edit,process,action('删除记录',()=>confirmAction('删除记录','将删除原文、日程及已关联的手机系统日历事件。公共 Downloads 文件会保留。',async owner=>{await api('/api/task/delete',{id:String(id),revision});if(ownsDialog(owner))await navigate('inbox');else await render();}),'danger'));original.append(actions);
 for(const target of data.mergedTargets||[]){if(target.taskId===Number(id))continue;if(target.taskId)original.append(action('已合并 · '+target.title,()=>navigate('task/'+target.taskId)));else original.append(node('p','subtle','已合并 · '+target.title+'（保留日程已不存在）'));}
 return original;
}
function detailCandidate(item,id,busy){
 const result=card();result.dataset.key='candidate-'+item.id;
 result.append(node('span','status-label',categories[item.category]+(item.completed?' · 已完成':item.calendarLinked?' · 已写入日历':item.needsConfirmation?' · 待确认':'')),node('h2','',item.title),node('p','subtle',executionWhen(item,true)));
 if(item.effectiveDue!=null)result.append(node('p','subtle',`截止 · ${fmt(item.effectiveDue,false,true,item.zone)}`));
 if(item.location)result.append(node('p','subtle',item.location));if(item.description)result.append(node('p','raw-text',item.description));
 const controls=node('div','detail-actions'),modify=action(item.recurrence?'重复日程':item.category==='homework'?'修改作业':'修改日程',()=>item.recurrence?repeatDialog(item):candidateEditor(item,$('.detail-reader')));modify.disabled=busy;controls.append(modify);
 if(!item.recurrence&&!item.managed){const repeat=action('设置重复',()=>repeatRuleDialog(item));repeat.disabled=busy||item.start==null&&item.effectiveDue==null;controls.append(repeat);}
 if(item.recurrence)result.append(node('span','tag',item.repeatLabel+(item.repeatStoppedAt?' · 已停止':'')));
 const state=completionAction(item);if(state){state.disabled=busy;controls.append(state);}appendHomeworkLinks(result,item);
 for(const source of item.mergedSources||[]){if(source.taskId===Number(id))continue;if(source.taskId)result.append(action('合并来源 · '+source.source,()=>navigate('task/'+source.taskId)));else result.append(node('p','raw-text',source.source+'\n'+source.text));}
 return {result,controls};
}
async function taskPage(id,ticket){
 rememberDetailScroll();
 const data=await api('/api/task?id='+id+(route.includes('?')?'&'+route.split('?')[1]:''));if(ticket!==sequence)return [];
 revision=data.revision;currentTask=data;
 const task=data.task,busy=['queued','processing'].includes(task.status),saved=detailPosition(id),items=urgencyCandidates(data.candidates),params=new URLSearchParams(route.split('?')[1]||'');
 const requested=Number(params.get('candidate_id'))||saved.candidate;
 let active=items.find(item=>item.id===requested)||items[0];
 const workspace=node('section','task-workspace'),master=node('aside','detail-master'),list=node('div','master-list'),reader=node('section','detail-reader'),tools=node('div','master-tools'),rows=new Map();
 workspace.dataset.task=id;master.setAttribute('aria-label','日程列表');reader.setAttribute('aria-label','日程详情');reader.tabIndex=-1;
 const label=node('h2','','日程 · '+items.length);tools.append(label);
 if(!data.managedHomeworkCapture)tools.append(action('原始内容',()=>showDialog('原始内容',body=>body.append(taskOriginal(data,id)))));
 const fresh={id:0,taskId:id,title:'',category:'event',start:null,end:null,due:null,zone:session.zone,location:'',description:'',allDay:false,reminder:null,needsConfirmation:true,calendarLinked:false};
 if(!busy&&!data.managedHomeworkCapture&&!items.some(item=>item.managed))tools.append(action('新增事项',()=>candidateEditor(fresh,reader)));
 master.append(tools,list);
 function drawDetail(){
  reader.replaceChildren();if(!active)return;
  const built=detailCandidate(active,id,busy),status=candidateStatus(active),head=node('div','detail-titlebar'),statusLabel=node('span','candidate-state state-'+status.key,status.text);
  head.append(statusLabel,built.controls);built.result.prepend(head);built.result.classList.add('candidate-detail');built.result.removeAttribute('data-key');
  const back=action('日程列表',()=>{workspace.classList.remove('show-detail');saved.detailOpen=false;requestAnimationFrame(()=>{const row=rows.get(active.id);if(!row?.isConnected)return;window.scrollTo(0,saved.mobileListScroll??0);row.focus({preventScroll:true});const bounds=row.getBoundingClientRect(),dock=$('.bottom').getBoundingClientRect();if(bounds.top<0||bounds.bottom>dock.top)window.scrollTo(0,Math.max(0,window.scrollY+bounds.top-(dock.top-bounds.height)/2));});},'detail-list-back');back.prepend(icon('arrow-left'));
  reader.append(back,built.result);
 }
 function sync(){
  if(!active)return;saved.candidate=active.id;
  for(const [itemId,row]of rows){const selected=itemId===active.id;row.setAttribute('aria-current',String(selected));row.tabIndex=selected?0:-1;}
  route=taskRoute(active);history.replaceState(null,'','#'+route);
 }
 async function choose(item,focus=true){
  if(!workspace.isConnected||ticket!==sequence||!await leaveInlineEditor()||!workspace.isConnected||ticket!==sequence)return;
  if(!workspace.classList.contains('show-detail'))saved.mobileListScroll=window.scrollY;
  rememberDetailScroll();active=item;sync();drawDetail();workspace.classList.add('show-detail');saved.detailOpen=true;icons();
  if(focus){reader.focus({preventScroll:true});window.scrollTo({top:0,behavior:reducedMotion.matches?'instant':'smooth'});}
 }
 for(const item of items){
  const status=candidateStatus(item),row=action('',()=>choose(item),'master-item state-'+status.key),text=node('span','master-item-text'),name=node('strong','',item.title),due=candidateDeadline(item);
  row.dataset.candidate=item.id;row.setAttribute('aria-label',item.title+' · '+status.text);row.append(icon(status.icon));text.append(name);
  if(item.courseName)text.append(node('span','master-course',item.courseName));
  text.append(node('span','master-due',due===null?'待安排时间':(taskLike(item.category)?'截止 · ':'')+fmt(due,item.allDay,true,item.zone)));
  row.append(text,node('span','master-status',status.text));rows.set(item.id,row);list.append(row);
  row.addEventListener('keydown',event=>{if(!['ArrowDown','ArrowUp','Home','End'].includes(event.key))return;event.preventDefault();const index=items.indexOf(item),next=event.key==='Home'?items[0]:event.key==='End'?items.at(-1):items[Math.max(0,Math.min(items.length-1,index+(event.key==='ArrowDown'?1:-1)))];guard(async()=>{await choose(next,false);rows.get(next.id)?.focus({preventScroll:true});rows.get(next.id)?.scrollIntoView({block:'nearest'});});});
 }
 if(!items.length){
  const block=card('日程');empty(block,busy?'手机正在处理，可稍后刷新查看结果':'这条记录还没有日程');
  const create=action('手动建立日程',()=>candidateEditor({...fresh,title:task.text.slice(0,80)},reader),'primary');create.disabled=busy||data.managedHomeworkCapture;block.append(create);reader.append(block);workspace.classList.add('show-detail');empty(list,'暂无日程');
 }else drawDetail();
 if(saved.detailOpen??params.has('candidate_id'))workspace.classList.add('show-detail');
 workspace.append(master,reader);
 requestAnimationFrame(()=>{if(!workspace.isConnected||ticket!==sequence)return;sync();const selected=rows.get(active?.id);if(selected){list.scrollTop=saved.listScroll||0;const top=selected.offsetTop-list.offsetTop;if(top<list.scrollTop||top+selected.offsetHeight>list.scrollTop+list.clientHeight)list.scrollTop=top-(list.clientHeight-selected.offsetHeight)/2;}});
 const back=action('',()=>{const previous=[...routeState.keys()].reverse().find(key=>['home','inbox','schedule','calendar'].includes(key));return navigate(previous||'inbox');},'icon-button');back.append(icon('arrow-left'));back.setAttribute('aria-label','返回列表');
 return[heading('记录详情',task.source+' · '+fmt(task.created,false,true),[back]),workspace];
}
function editRaw(task){showDialog('修改原文',(body,owner)=>{const form=node('form'),input=field(form,'原文',task.text,'textarea','text');input.required=true;input.maxLength=100000;const actions=node('div','actions'),submit=node('button','primary','保存');submit.type='submit';actions.append(submit);form.append(actions);form.addEventListener('submit',event=>{event.preventDefault();guard(async()=>{submit.disabled=true;try{await api('/api/task/edit',{id:String(task.id),revision,text:input.value});closeDialog(false,owner);await render();}finally{submit.disabled=false;}});});body.append(form);});}
function alignEndAfterStart(start,end,allDay,zone){
 if(allDay||!start.value||!end.value||!start.validity.valid||!end.validity.valid)return;
 try{const begin=instant(start.value,false,zone),finish=instant(end.value,false,zone);if(finish<=begin)end.value=localValue(begin+SHIFTED_START_INTERVAL_MILLIS,false,zone);}catch{ /* Keep incomplete or DST-gap values for explicit validation on save. */ }
}
// datetime-local is projected into the event's IANA zone, not the computer's local zone.
function localValue(value,allDay,zone){if(value===null)return '';if(allDay)return new Date(value).toISOString().slice(0,10);const parts=new Intl.DateTimeFormat('sv-SE',{year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',hour12:false,timeZone:zone}).format(new Date(value));return parts.replace(' ','T');}
function instant(value,allDay,zone){if(!value)return null;if(allDay)return Date.parse(value+'T00:00:00Z');const target=Date.parse(value+'Z');let estimate=target;for(let i=0;i<3;i++){const displayed=localValue(estimate,false,zone);const difference=target-Date.parse(displayed+'Z');estimate+=difference;if(!difference)break;}if(localValue(estimate,false,zone)!==value)throw new Error('此时间位于时区夏令时跳变区间，请重新选择');return estimate;}
async function candidateEditor(item,host=null){
 if(host&&inlineEditor?.form.isConnected&&!await leaveInlineEditor())return;
 const label=item.id?(item.category==='homework'?'修改作业':'修改日程'):'新建事项';
 if(host){
  const reader=host,workspace=reader.closest('.task-workspace');if(!workspace)return;
  const previous=[...reader.childNodes],savedScroll=window.scrollY;
  const restore=()=>{inlineEditor=null;reader.replaceChildren(...previous);icons();window.scrollTo(0,savedScroll);reader.querySelector('.detail-actions button')?.focus({preventScroll:true});};
  const editor=candidateForm(item,async()=>{inlineEditor=null;await render();},async()=>{if(await leaveInlineEditor())restore();});
  inlineEditor=editor;reader.replaceChildren(heading(label,'',[]),editor.form);workspace.classList.add('show-detail');icons();reader.querySelector('input:not([readonly]):not(:disabled),textarea:not([readonly])')?.focus({preventScroll:true});
  window.scrollTo({top:0,behavior:reducedMotion.matches?'instant':'smooth'});
 }else showDialog(label,(body,owner)=>{const editor=candidateForm(item,async()=>{closeDialog(false,owner);await render();},requestDialogClose);dialogEditor=editor;body.append(editor.form);});
}
function candidateForm(item,finish,cancel){
 const baseline=revision,form=node('form','candidate-form'),fields=node('div','candidate-fields'),basic=node('fieldset','form-group'),timing=node('fieldset','form-group'),notes=node('fieldset','form-group');
 basic.append(node('legend','','基础信息'));timing.append(node('legend','','时间与地点'));notes.append(node('legend','','备注'));
 const title=field(basic,'标题',item.title,'text','title');title.required=true;title.maxLength=500;
 const identity=node('div','two-fields'),kind=node('div'),timezone=node('div'),category=field(kind,'类型','','select','category');
 for(const [value,label]of Object.entries(categories)){const option=node('option','',label);option.value=value;category.append(option);}category.value=item.category;
 const zone=field(timezone,'时区',item.zone||session.zone,'text','zone');zone.required=true;identity.append(kind,timezone);basic.append(identity);
 const allDayLabel=node('label','checkbox'),allDay=node('input');allDay.type='checkbox';allDay.checked=item.allDay;allDayLabel.append(allDay,document.createTextNode('全天'));timing.append(allDayLabel);
 const due=field(timing,'截止时间（可选）',localValue(item.due??null,false,item.zone||session.zone),'datetime-local','due');
 const syncDue=()=>{due.hidden=!taskLike(category.value);timing.querySelector('label[for="'+due.id+'"]').hidden=due.hidden;};category.addEventListener('change',syncDue);syncDue();
 const dates=node('div','execution-fields'),first=node('div'),second=node('div'),start=field(first,'开始',localValue(item.start??null,item.allDay,item.zone),item.allDay?'date':'datetime-local','start'),end=field(second,'结束',localValue(item.end??null,item.allDay,item.zone),item.allDay?'date':'datetime-local','end'),clear=action('清空',()=>{start.value='';end.value='';},'clear-times');
 clear.setAttribute('aria-label','清空执行时间');clear.title='清空执行时间';dates.append(first,second,clear);timing.append(dates);
 start.addEventListener('change',()=>{if(!end.value&&start.value&&taskLike(category.value))end.value=start.value;else alignEndAfterStart(start,end,allDay.checked,zone.value);});end.addEventListener('focus',()=>{if(!end.value&&start.value)end.value=start.value;});
 allDay.addEventListener('change',()=>{const type=allDay.checked?'date':'datetime-local';start.type=type;end.type=type;start.value='';end.value='';zone.disabled=allDay.checked;});zone.disabled=item.allDay;
 const location=field(timing,'地点',item.location,'text','location');location.maxLength=2000;
 const reminder=field(timing,'提前提醒（分钟，留空关闭）',item.reminder??'','number','reminder');reminder.min='0';reminder.max='525600';
 const reviewLabel=node('label','checkbox'),review=node('input');review.type='checkbox';review.checked=item.needsConfirmation;reviewLabel.append(review,document.createTextNode('仍需确认'));basic.append(reviewLabel);
 const description=field(notes,'备注',item.description,'textarea','description');description.maxLength=100000;
 if(item.managed){title.readOnly=true;category.disabled=true;due.readOnly=true;description.readOnly=true;}
 fields.append(basic,timing,notes);
 const feedback=node('p','form-feedback');feedback.hidden=true;feedback.setAttribute('role','alert');
 const buttons=node('div','actions editor-actions'),cancelButton=action('取消',cancel),save=node('button','primary','保存');save.type='submit';let publishButton=null,submitting=false;
 if(item.id)buttons.append(action('删除',()=>{const message='手机系统日历中关联的事件也将删除，原文保留。';if(dialogEditor?.form===form)confirmEditorAction(dialogEditor,'删除日程',message,'确认',remove);else confirmAction('删除日程',message,remove);},'danger'));
 buttons.append(cancelButton,save);
 if(item.id){publishButton=action(item.calendarLinked?'更新手机日历':'写入手机日历',()=>commit(true));publishButton.disabled=!session.calendarAllowed;buttons.append(publishButton);}
 form.append(fields,feedback,buttons);
 const setWorking=value=>{fields.inert=value;for(const button of buttons.querySelectorAll('button')){if(value){button.dataset.wasDisabled=String(button.disabled);button.disabled=true;}else{button.disabled=button.dataset.wasDisabled==='true';delete button.dataset.wasDisabled;}}};
 const values=()=>JSON.stringify([...form.querySelectorAll('input,textarea,select')].map(input=>[input.id,input.value,input.checked,input.disabled]));
 const original=values();
 async function remove(){
  if(submitting)return;submitting=true;setWorking(true);const confirmation=$('.editor-confirmation');if(confirmation)confirmation.inert=true;
  try{await api('/api/candidate/delete',{id:String(item.taskId),candidateId:String(item.id),revision:baseline});if(form.isConnected)await finish();}
  finally{submitting=false;setWorking(false);if(confirmation)confirmation.inert=false;}
 }
 async function commit(publish){
  if(submitting||!form.reportValidity())return;
  feedback.hidden=true;
  try{
   const result={id:String(item.taskId),candidateId:String(item.id),revision:baseline,title:title.value,category:category.value,start:instant(start.value,allDay.checked,zone.value),end:instant(end.value,allDay.checked,zone.value),zone:zone.value,allDay:allDay.checked,location:location.value,description:description.value,reminder:reminder.value===''?null:Number(reminder.value),needsConfirmation:review.checked,due:due.value?instant(due.value,false,zone.value):null,record:false};
   if(result.end!==null&&(result.start===null||result.end<=result.start))throw new Error('结束时间须晚于开始时间');
   submitting=true;setWorking(true);
   await api(item.id?(publish?'/api/candidate/publish':'/api/candidate/save'):'/api/candidate/create',result);
   if(form.isConnected)await finish();
  }catch(error){if(form.isConnected){feedback.textContent=error.message||'保存未完成，请重试';feedback.hidden=false;feedback.scrollIntoView({block:'nearest'});}}
  finally{if(submitting)setWorking(false);submitting=false;}
 }
 form.addEventListener('submit',event=>{event.preventDefault();guard(()=>commit(false));});
 return {form,dirty:()=>values()!==original,working:()=>submitting};
}
async function timetablePage(ticket){const data=await api(`/api/timetable?date=${dateISO(week)}`);if(ticket!==sequence)return [];termRevision=data.revision;selectedTerm=data.selected;const filters=node('div','filters timetable-filters');filters.append(action('上一周',()=>{week.setDate(week.getDate()-7);return render({local:true,direction:-1});}),action('本周',()=>{week=new Date();return render({local:true,direction:1});}),action('下一周',()=>{week.setDate(week.getDate()+7);return render({local:true,direction:1});}));const wrapper=node('div','week-wrapper'),grid=node('div','week-grid'),monday=new Date(week);monday.setDate(monday.getDate()-(monday.getDay()+6)%7);for(let i=0;i<7;i++){const date=new Date(monday);date.setDate(date.getDate()+i);const day=node('section','week-day');day.append(node('h3','day-label',['周一','周二','周三','周四','周五','周六','周日'][i]+` · ${date.getDate()}`));for(const item of data.items.filter(item=>item.start.slice(0,10)===dateISO(date)).sort((a,b)=>a.start.localeCompare(b.start))){const button=action('',()=>showDialog(item.title,body=>body.append(node('p','subtle',`${item.start.replace('T',' ')} — ${item.end.replace('T',' ')}`),node('p','raw-text',item.location),node('p','raw-text',item.description))),'course');button.append(node('small','',item.allDay?'全天':item.start.slice(11,16)+' — '+item.end.slice(11,16)),node('strong','',item.title),node('small','',item.location));day.append(button);}grid.append(day);}wrapper.append(grid);const actions=[action('导入 ICS',importIcs,'primary')];if(data.terms.length){actions.push(action('下载原始 ICS',()=>{const link=node('a');link.href='/api/ics?term='+encodeURIComponent(selectedTerm);link.download='chrona-timetable.ics';link.click();}),action('删除学期',()=>confirmAction('删除学期','此学期课表将从手机及下一次同步中移除。',async()=>{await api('/api/timetable',{action:'delete',term:selectedTerm,revision:termRevision});selectedTerm='';await render();}),'danger'));}const content=[heading(data.terms.find(term=>term.id===selectedTerm)?.name||'课表','',actions),filters];if(data.terms.length)content.push(wrapper);else content.push(node('p','empty','导入 ICS 课表，安排随手机同步'));return content;}
function importIcs(){
 showDialog('导入课表',(body,owner)=>{
  const form=node('form'),name=field(form,'名称','导入的课表','text','name');name.maxLength=160;name.required=true;
  const input=field(form,'ICS 文件','','file','file');input.accept='.ics,text/calendar';input.required=true;
  const actions=node('div','actions'),submit=node('button','primary','导入');submit.type='submit';actions.append(submit);form.append(actions);
  form.addEventListener('submit',event=>{
   event.preventDefault();guard(async()=>{
    const file=input.files[0];if(!file||file.size>900000)throw new Error('请选择小于 900 KB 的 ICS 文件');submit.disabled=true;
    try{
     const incoming={action:'preview',name:name.value,source:await file.text(),revision:termRevision};
     const preview=await api('/api/timetable',incoming);
     if(!ownsDialog(owner))return;const commit=async(commitOwner=owner)=>{await api('/api/timetable',{...incoming,action:'import'});closeDialog(false,commitOwner);await render();};
     if(preview.replaced.length)confirmAction('替换已有学期',`将替换 ${preview.replaced.join('、')} 的课表；其他学期保留。`,commit);
     else await commit();
    }finally{submit.disabled=false;}
   });
  });body.append(form);
 });
}
let mergeChecking=false;
const shownMergeGroups=new Set(JSON.parse(sessionStorage.getItem('chrona-shown-merges')||'[]'));
const mergeSignature=group=>{const {canAcknowledge,acknowledged,...candidate}=group.candidate;return JSON.stringify({...group,candidate});};
async function detectMerges(){
 if(mergeChecking||!session||pending||attachmentWork||$('#dialog').open||document.hidden)return;
 mergeChecking=true;
 const requestedDialog=dialogGeneration,requestedRoute=route;
 try{
  const preview=await api('/api/merge/preview');
  if(requestedDialog!==dialogGeneration||requestedRoute!==route||$('#dialog').open||document.hidden)return;
  const fresh=preview.groups.filter(group=>!shownMergeGroups.has(mergeSignature(group)));
  if(!fresh.length)return;
  for(const group of fresh)shownMergeGroups.add(mergeSignature(group));
  sessionStorage.setItem('chrona-shown-merges',JSON.stringify([...shownMergeGroups]));
  showMergePreview({...preview,groups:fresh,removed:fresh.reduce((count,group)=>count+group.sources.length-1,0)},true);
 }finally{mergeChecking=false;}
}
async function previewMerges(){
 const requestedDialog=dialogGeneration,requestedRoute=route;
 const preview=await api('/api/merge/preview');
 if(requestedDialog!==dialogGeneration||requestedRoute!==route)return;
 showMergePreview(preview,false);
}
function showMergePreview(preview,automatic){
 let submitting=false;
 const selected=new Set(),pageSize=12,show=(index=0)=>showDialog(automatic?'发现重复日程':'合并重复日程',(body,owner)=>{
  if(!preview.groups.length){body.append(node('p','empty','没有可合并的重复日程'));return;}
  body.append(node('h3','',`${preview.groups.length} 组 · 合并 ${preview.removed} 份重复日程`));
  for(const group of preview.groups.slice(index*pageSize,(index+1)*pageSize)){const candidate=group.candidate,block=card(candidate.title),label=node('label','checkbox'),choice=node('input');choice.type='checkbox';choice.checked=selected.has(String(candidate.id));choice.setAttribute('aria-label','选择合并 '+candidate.title);choice.addEventListener('change',()=>{if(choice.checked)selected.add(String(candidate.id));else selected.delete(String(candidate.id));update();});label.append(choice,document.createTextNode('合并此组'));block.prepend(label);block.append(node('p','subtle',`${fmt(candidate.start,candidate.allDay,true,candidate.zone)} — ${fmt(candidate.end,candidate.allDay,true,candidate.zone)} · ${candidate.zone}`),node('p','raw-text',candidate.location),node('p','raw-text',candidate.description),node('p','subtle',`${categories[candidate.category]}${candidate.allDay?' · 全天':''} · 提醒 ${candidate.reminder??'关闭'}${candidate.needsConfirmation?' · 待确认':' · 已确认'}`));
   for(const source of group.sources)block.append(action(`${String(source.candidateId)===String(candidate.id)?'保留':'合并'} · ${source.title}\n${source.source}${source.location?' · '+source.location:''}`,()=>{closeDialog();return navigate('task/'+source.taskId+'?candidate_id='+encodeURIComponent(source.candidateId));}));body.append(block);
  }
  if(preview.groups.length>pageSize){const pages=node('div','pagination'),previous=action('上一页',()=>show(index-1)),next=action('下一页',()=>show(index+1));previous.disabled=index===0;next.disabled=(index+1)*pageSize>=preview.groups.length;pages.append(previous,node('span','',`${index+1} / ${Math.ceil(preview.groups.length/pageSize)}`),next);body.append(pages);}
  const actions=node('div','actions'),confirm=action('合并所选',async()=>{if(submitting)return;submitting=true;body.querySelectorAll('button,input').forEach(control=>control.disabled=true);try{const result=await api('/api/candidate/merge',{revision:preview.revision,groups:[...selected]});closeDialog(false,owner);toast(`已合并 ${result.removed} 份重复日程`,owner);await render();}catch(error){actions.append(action('重新扫描',previewMerges));throw error;}},'primary');
  const update=()=>{const count=preview.groups.filter(group=>selected.has(String(group.candidate.id))).reduce((sum,group)=>sum+group.sources.length-1,0);confirm.textContent=count?`确认合并 ${count} 份`:'合并所选';confirm.disabled=submitting||!count;};update();actions.append(action('暂不合并',()=>closeDialog(false,owner)),confirm);body.append(actions);
 });show();
}

function completionAction(item,after=null){
 if(item.recurrence&&item.occurrenceAt==null)return null;
 if(item.record||!taskLike(item.category)&&!item.canAcknowledge)return null;
 const task=taskLike(item.category),selected=task?item.completed:item.acknowledged;
 const label=task?(selected?'恢复'+categories[item.category]:'标记完成'):(selected?'取消确认活动':'确认活动');
 const button=action('',async()=>{const result=item.recurrence&&task?await changeRepeat(item,selected?'reopen':'complete',{original:item.occurrenceAt}):await api(task?'/api/candidate/complete':'/api/candidate/acknowledge',{id:String(item.taskId),candidateId:String(item.id),revision,...(item.occurrenceAt!=null?{occurrenceAt:item.occurrenceAt}:{}),...(task?{completed:!selected}:{acknowledged:!selected})});await render({local:true});if(after)await after(result);if(result.warning)toast(result.warning);},'icon-button completion-action');
 button.setAttribute('aria-label',label);button.title=label;button.setAttribute('aria-pressed',String(!!selected));button.append(icon('check'));button.disabled=!!item.submitted&&!!item.completed;return button;
}
async function changeRepeat(item,action,extra={}){const result=await api('/api/candidate/repeat',{id:String(item.taskId),candidateId:String(item.id),baseline:item.baseline,revision,action,...extra});revision=result.revision;return result;}
function repeatRuleDialog(item){showDialog('设置重复',(body,owner)=>{
 const form=node('form'),frequency=field(form,'重复频率','','select','repeat-frequency');for(const [value,label]of [['daily','每天'],['weekly','每周'],['monthly','每月'],['yearly','每年']]){const option=node('option','',label);option.value=value;frequency.append(option);}
 const interval=field(form,'间隔',1,'number','repeat-interval');interval.min='1';interval.max='999';interval.required=true;
 const weekdayHost=node('div','repeat-weekdays'),days=[];for(let day=1;day<=7;day++){const label=node('label','checkbox'),box=node('input');box.type='checkbox';box.value=day;label.append(box,document.createTextNode(['一','二','三','四','五','六','日'][day-1]));days.push(box);weekdayHost.append(label);}form.append(weekdayHost);const sync=()=>weekdayHost.hidden=frequency.value!=='weekly';frequency.addEventListener('change',sync);sync();
 const ending=field(form,'结束方式','','select','repeat-ending');for(const [value,label]of [['never','持续重复'],['until','截止日期'],['count','重复次数']]){const option=node('option','',label);option.value=value;ending.append(option);}const extra=node('div');form.append(extra);let until=null,count=null;const endFields=()=>{extra.replaceChildren();until=count=null;if(ending.value==='until')until=field(extra,'截止日期','','date','repeat-until');if(ending.value==='count'){count=field(extra,'次数',10,'number','repeat-count');count.min='1';count.max='100000';count.required=true;}};ending.addEventListener('change',endFields);
 const save=node('button','primary','保存重复');save.type='submit';form.append(save);form.addEventListener('submit',event=>{event.preventDefault();guard(async()=>{if(!form.reportValidity())return;if(until&&!until.value)throw new Error('请选择截止日期');save.disabled=true;try{await changeRepeat(item,'rule',{rule:{frequency:frequency.value,interval:Number(interval.value),weekdays:frequency.value==='weekly'?days.filter(box=>box.checked).map(box=>Number(box.value)):[],until:until?.value||null,count:count?Number(count.value):null}});closeDialog(false,owner);await render({local:true});}finally{save.disabled=false;}});});body.append(form);
});}
async function repeatDialog(item,month){
 const from=month||(item.occurrenceAt!=null?phoneDate(item.occurrenceAt):phoneDate()).slice(0,8)+'01',until=monthAfter(from);
 const owner=showDialog('重复日程',body=>body.append(node('p','loading','正在读取…'))),data=await dialogData(`/api/candidate/occurrences?id=${item.taskId}&candidateId=${item.id}&from=${from}&until=${until}`,owner,()=>repeatDialog(item,from));if(!data||!ownsDialog(owner))return;
 revision=data.revision;const series=data.series,body=$('#dialog-body');body.replaceChildren();body.append(node('h3','',series.title),node('p','filter-summary',series.repeatLabel+(series.repeatStoppedAt?' · 已停止':'')));const controls=node('div','range-step');controls.append(action('上个月',()=>repeatDialog(series,monthAfter(from,-1))),node('span','',from.slice(0,7)),action('下个月',()=>repeatDialog(series,until)));body.append(controls);
 for(const occurrence of data.items){const row=node('section','repeat-occurrence');row.append(node('h3','',`${itemDate(occurrence)} · ${executionWhen(occurrence)}`));const buttons=node('div','actions'),complete=completionAction(occurrence,result=>repeatDialog(result.candidate||series,from));if(complete)buttons.append(complete);buttons.append(action('仅改期这次',()=>moveOccurrence(occurrence,from)),action('跳过这次',()=>confirmAction('跳过这次',`${itemDate(occurrence)} · ${occurrence.title}`,async()=>{const changed=await changeRepeat(occurrence,'skip',{original:occurrence.occurrenceAt});await render({local:true});await repeatDialog(changed.candidate,from);})));row.append(buttons);body.append(row);}
 if(!data.items.length)empty(body,'本月没有安排');if(session.calendarAllowed&&!series.calendarLinked)body.append(action('写入手机日历',async()=>{const result=await api('/api/calendar/publish-selected',{ids:[String(series.id)],inbox:false,revision});revision=result.revision;await render({local:true});await repeatDialog(series,from);toast(result.summary);},'primary'));if(!series.repeatStoppedAt)body.append(action('停止重复',()=>confirmAction('停止重复','停止之后尚未开始的日程及提醒，保留历史记录',async()=>{await changeRepeat(series,'stop');await render({local:true});}),'danger'));icons();
}
function moveOccurrence(item,month){showDialog('仅改期这次',(body,owner)=>{
 const value=field(body,item.allDay?'日期':'开始时间',localValue(item.start??item.effectiveDue,item.allDay,item.zone),item.allDay?'date':'datetime-local','repeat-move');body.append(action('保存改期',async()=>{if(!value.value)throw new Error('请选择时间');const changed=await changeRepeat(item,'move',{original:item.occurrenceAt,moved:instant(value.value,item.allDay,item.zone)});closeDialog(false,owner);await render({local:true});await repeatDialog(changed.candidate,month);},'primary'));
});}
function appendHomeworkLinks(parent,item){
 const row=node('div','homework-links');for(const [url,label]of [[item.mobileUrl,'雪灾浙大M'],[item.desktopUrl,'雪灾浙大']]){if(!url)continue;const parsed=new URL(url);if(parsed.protocol!=='https:'||!['courses.zju.edu.cn','mcourses.zju.edu.cn'].includes(parsed.hostname))continue;const link=node('a','link-button',label);link.href=parsed.href;link.target='_blank';link.rel='noopener noreferrer';row.append(link);}if(row.childElementCount)parent.append(row);
}
async function exportAgendaImage(items,from){
 const canvas=document.createElement('canvas'),context=canvas.getContext('2d'),column=184,gap=12,margin=28;
 const style=getComputedStyle(document.body),surface=style.getPropertyValue('--surface').trim(),text=style.color,muted=style.getPropertyValue('--muted').trim(),accent=session.palette.primary;
 canvas.width=margin*2+7*column+6*gap;
 const wrap=(value,width)=>{const lines=[];let line='';for(const char of value){if(char==='\n'||context.measureText(line+char).width>width){lines.push(line);line=char==='\n'?'':char;}else line+=char;}if(line)lines.push(line);return lines;};
 context.font='16px "Microsoft YaHei", sans-serif';
 const days=Array.from({length:7},(_,index)=>{const day=addCivilDays(from,index);return {day,entries:agendaDayEntries(items,day).map(item=>{const lines=wrap(item.title,column-24),location=wrap(item.location||'',column-24);return {item,lines,location,height:58+(lines.length+location.length)*23};})};});
 const height=180+Math.max(60,...days.map(day=>day.entries.reduce((sum,entry)=>sum+entry.height+gap,0)));
 if(height>16000)throw new Error('本周内容过多，图片尺寸超出浏览器限制');canvas.height=height;
 context.fillStyle=style.backgroundColor;context.fillRect(0,0,canvas.width,canvas.height);context.fillStyle=text;context.font='600 26px "Microsoft YaHei",sans-serif';context.fillText('拾时 · 本周日历',margin,46);context.fillStyle=muted;context.font='15px "Microsoft YaHei",sans-serif';context.fillText(from,margin,76);
 days.forEach(({day,entries},index)=>{const x=margin+index*(column+gap);context.fillStyle=accent;context.font='600 16px "Microsoft YaHei",sans-serif';context.fillText(['周一','周二','周三','周四','周五','周六','周日'][index]+' · '+Number(day.slice(8)),x,120);let y=140;
  for(const {item,lines,location,height}of entries){context.beginPath();context.roundRect(x,y,column,height,18);context.fillStyle=surface;context.fill();context.strokeStyle=style.getPropertyValue('--outline').trim();context.stroke();context.font='13px "Microsoft YaHei",sans-serif';context.fillStyle=accent;context.fillText(item.allDay?'全天':(item.due&&item.start===item.due?'截止 ':'')+fmt(item.start)+(item.end>item.start?' — '+fmt(item.end):''),x+12,y+24);let lineY=y+49;context.font='16px "Microsoft YaHei",sans-serif';context.fillStyle=text;for(const line of lines){context.fillText(line,x+12,lineY);lineY+=23;}context.fillStyle=muted;for(const line of location){context.fillText(line,x+12,lineY);lineY+=23;}y+=height+gap;}
 });
 const blob=await new Promise(resolve=>canvas.toBlob(resolve,'image/png'));if(!blob)throw new Error('图片生成失败');const url=URL.createObjectURL(blob),link=node('a');link.href=url;link.download=`Chrona-${from}.png`;link.click();setTimeout(()=>URL.revokeObjectURL(url),10000);
}
async function calendarPage(ticket){
 const anchor=civilDate(calendarDate);calendarDate=anchor;let from=anchor,until;
 if(calendarView==='year'){from=anchor.slice(0,4)+'-01-01';until=String(Number(anchor.slice(0,4))+1).padStart(4,'0')+'-01-01';}else if(calendarView==='month'){from=anchor.slice(0,8)+'01';const next=new Date(from+'T00:00:00Z');next.setUTCMonth(next.getUTCMonth()+1);until=next.toISOString().slice(0,10);}else if(calendarView==='day')until=addCivilDays(from,1);else{from=addCivilDays(anchor,-(civilWeekday(anchor)+6)%7);until=addCivilDays(from,7);}
 const data=await api(`/api/agenda?from=${from}&until=${until}`);if(ticket!==sequence)return [];revision=data.revision;
 const openDate=(day,view='day')=>{calendarDate=day;calendarView=view;return render({local:true});};
 const controls=node('div','calendar-controls'),date=node('input');date.type='date';date.value=anchor;date.setAttribute('aria-label','日历日期');date.addEventListener('change',()=>{if(date.value){calendarDate=date.value;guard(()=>render({local:true}));}});
 const shift=direction=>{if(calendarView==='year')calendarDate=String(Number(anchor.slice(0,4))+direction).padStart(4,'0')+'-01-01';else if(calendarView==='month'){const next=new Date(anchor.slice(0,8)+'01T00:00:00Z');next.setUTCMonth(next.getUTCMonth()+direction);calendarDate=next.toISOString().slice(0,10);}else calendarDate=addCivilDays(anchor,direction*(calendarView==='day'?1:7));return render({local:true,direction});};
 const unit={year:'年',month:'月',day:'天',week:'周'}[calendarView];controls.append(action('上一'+unit,()=>shift(-1)),date,action('今天',()=>{calendarDate=phoneDate();return render({local:true});}),action('下一'+unit,()=>shift(1)));
 if(calendarView==='day')controls.append(action('返回月',()=>openDate(anchor,'month')));
 const views=selectionBar('日历视图',[['day','日'],['week','周'],['month','月'],['year','年']],calendarView,value=>{calendarView=value;guard(()=>render({local:true}));});
 const wrapper=node('div',calendarView==='day'?'agenda-day':calendarView==='year'?'agenda-year':'week-wrapper'),grid=node('div',calendarView==='year'?'year-grid':calendarView==='month'?'month-grid':calendarView==='day'?'day-grid':'week-grid');
 if(calendarView==='week'){grid.dataset.density=calendarDensity;const density=node('select');density.setAttribute('aria-label','周视图密度');for(const [value,label]of [['compact','紧凑'],['standard','标准'],['relaxed','宽松']]){const option=node('option','',label);option.value=value;density.append(option);}density.value=calendarDensity;density.addEventListener('change',()=>{calendarDensity=density.value;grid.dataset.density=calendarDensity;});const densityControl=node('div','calendar-density');densityControl.append(density);controls.append(densityControl);}
 if(calendarView==='month'){for(const label of ['周一','周二','周三','周四','周五','周六','周日'])grid.append(node('span','month-weekday',label));for(let i=0;i<(civilWeekday(from)+6)%7;i++)grid.append(node('span','month-blank'));}
 const today=phoneDate();if(calendarView==='year'){for(let month=1;month<=12;month++){const first=from.slice(0,4)+'-'+String(month).padStart(2,'0')+'-01',next=month===12?until:from.slice(0,4)+'-'+String(month+1).padStart(2,'0')+'-01',section=node('section','year-month');section.dataset.month=first.slice(0,7);section.append(action(month+'月',()=>openDate(first,'month'),'year-month-title'));const days=node('div','mini-month');for(const label of ['一','二','三','四','五','六','日'])days.append(node('span','mini-weekday',label));for(let i=0;i<(civilWeekday(first)+6)%7;i++)days.append(node('span'));for(let day=first;day<next;day=addCivilDays(day,1)){const count=agendaDayEntries(data.items,day).length,button=action(String(Number(day.slice(8))),()=>openDate(day),'mini-date');button.dataset.date=day;button.classList.toggle('today',day===today);button.classList.toggle('has-events',count>0);button.setAttribute('aria-label',day+(count?' · '+count+' 条'+(data.truncated?'已加载日程':'日程'):''));if(day===today)button.setAttribute('aria-current','date');days.append(button);}section.append(days);grid.append(section);}}else for(let day=from;day<until;day=addCivilDays(day,1)){const section=node('section','week-day');section.dataset.date=day;section.classList.toggle('today',day===today);const label=new Intl.DateTimeFormat('zh-CN',{month:'numeric',day:'numeric',weekday:'short',timeZone:'UTC'}).format(new Date(day+'T12:00:00Z'));if(calendarView==='month'){const title=action(label,()=>openDate(day),'day-label day-link');title.setAttribute('aria-label',day+' · 日视图');section.append(title);}else section.append(node('h3','day-label',label));
 for(const item of agendaDayEntries(data.items,day)){const button=action('',()=>item.system?showReadOnly(item):navigate(taskRoute(item)),'course');button.append(node('small','',item.allDay?'全天':(item.due&&item.start===item.due?'截止 · ':'')+fmt(item.start)+(item.end>item.start?' — '+fmt(item.end):'')),node('strong','',item.title));if(item.location)button.append(node('small','',item.location));if(item.category==='homework')button.append(node('span','tag','作业'));section.append(button);}grid.append(section);}wrapper.append(grid);
 const actions=[action('导入课表',()=>navigate('timetable'))];if(calendarView==='week'&&!data.truncated)actions.push(action('导出周图片',()=>exportAgendaImage(data.items,from)));
 const result=[heading('日历',`${from} — ${addCivilDays(until,-1)}`,actions),views,controls];
 if(data.truncated)result.push(node('p','calendar-notice',calendarView==='year'?'仅显示部分日程，请进入月份查看':'日程较多，请缩小日期范围'));result.push(wrapper);
 if(!data.items.length&&!data.truncated)result.push(node('p','empty','这段时间没有安排'));if(data.systemUnavailable)result.push(node('p','subtle','系统日历读取权限需在手机授予'));return result;
}

async function dialogData(path,owner,retry){try{return await api(path);}catch(error){if(ownsDialog(owner)){$('#dialog-body').replaceChildren(action('重新读取',retry,'quick-action'));toast(error.message,owner);}return null;}}
async function sourcesDialog(){
 const owner=showDialog('日程来源',body=>body.append(node('p','loading','正在读取来源…'))),data=await dialogData('/api/sources',owner,sourcesDialog);if(!data||!ownsDialog(owner))return;
 const body=$('#dialog-body');body.replaceChildren();const primary=field(body,'主来源','','select','primary-source');for(const source of [{id:'all',name:'不优先'},...data.sources]){const option=node('option','',source.name);option.value=source.id;primary.append(option);}primary.value=data.primary;primary.addEventListener('change',()=>guard(async()=>{primary.disabled=true;primary.parentElement.querySelector('.select-trigger').disabled=true;try{await api('/api/sources',{revision:data.revision,primary:primary.value});if(ownsDialog(owner))await sourcesDialog();}catch(error){primary.value=data.primary;primary.parentElement.querySelector('.select-trigger span').textContent=primary.selectedOptions[0]?.textContent||'';throw error;}finally{primary.disabled=false;primary.parentElement.querySelector('.select-trigger').disabled=false;icons();}}));
 const list=node('div','source-list');for(const source of data.sources){const row=node('div','source-row'),view=action('',()=>sourceContents(source.id,source.name),'source-view');view.append(node('strong','',source.name),node('span','subtle',String(source.count)+' 条'),icon('chevron-right'));const label=node('label','source-switch'),toggle=node('input');toggle.type='checkbox';toggle.checked=source.enabled;toggle.setAttribute('aria-label','启用'+source.name);toggle.addEventListener('change',()=>guard(async()=>{toggle.disabled=true;try{await api('/api/sources',{revision:data.revision,source:source.id,enabled:toggle.checked});if(ownsDialog(owner))await sourcesDialog();}catch(error){toggle.checked=source.enabled;throw error;}finally{toggle.disabled=false;}}));label.append(toggle);row.append(view,label);list.append(row);}body.append(list);if(!data.systemReadable)body.append(node('p','subtle','系统日历读取权限需在手机侧授予'));icons();
}
async function sourceContents(id,name,index=0){
 const owner=showDialog(name,body=>body.append(node('p','loading','正在读取日程…'))),data=await dialogData('/api/sources?source='+encodeURIComponent(id)+'&page='+index,owner,()=>sourceContents(id,name,index));if(!data||!ownsDialog(owner))return;
 const body=$('#dialog-body');body.replaceChildren();const list=node('div','source-items');for(const item of data.items)list.append(entry(item,()=>showReadOnly({...item,source:name})));if(!data.items.length)empty(list,'此来源暂无日程');body.append(list);const buttons=node('div','actions');buttons.append(action('全部来源',sourcesDialog));if(index>0)buttons.append(action('上一页',()=>sourceContents(id,name,index-1)));if(data.hasMore)buttons.append(action('下一页',()=>sourceContents(id,name,index+1)));body.append(buttons);
}
async function syncDialog(index=0,started=false){
 const owner=showDialog('跨设备同步',body=>body.append(node('p','loading','正在读取同步状态…')));let waiting=started;
 async function load(){const data=await api('/api/sync?page='+index);if(!ownsDialog(owner))return;const body=$('#dialog-body');body.replaceChildren();body.append(node('p','sync-status',data.running||waiting?'处理中…':data.status||'尚未同步'));const automaticLabel=node('label','checkbox'),automatic=node('input');automatic.type='checkbox';automatic.checked=!!data.automatic;automatic.disabled=!data.configured&&!data.automatic;automatic.addEventListener('change',()=>guard(async()=>{automatic.disabled=true;try{const result=await api('/api/sync/preferences',{automatic:automatic.checked});data.automatic=!!result.automatic;automatic.checked=data.automatic;}catch(error){automatic.checked=!!data.automatic;throw error;}finally{automatic.disabled=!data.configured&&!data.automatic;}}));automaticLabel.append(automatic,document.createTextNode('自动同步'));body.append(automaticLabel);
  const run=action(data.running||waiting?'处理中…':'立即同步',async()=>{run.disabled=true;try{await api('/api/sync/run',{});waiting=true;body.querySelector('.sync-status').textContent='处理中…';setTimeout(()=>guard(poll),800);}catch(error){run.disabled=false;throw error;}},'primary');run.disabled=!data.configured||data.running||waiting;body.append(run);if(!data.configured)body.append(node('p','subtle','请在手机设置 WebDAV 账号'));
  for(const conflict of data.pending){const row=node('section','sync-conflict');const resolve=action('处理冲突',()=>syncConflict(conflict));resolve.disabled=data.running||waiting;row.append(node('h3','',conflict.title),resolve);body.append(row);}if(!data.pending.length&&!data.running&&!waiting)body.append(node('p','empty','暂无待处理冲突'));
  const pages=node('div','actions');if(index>0)pages.append(action('上一页',()=>syncDialog(index-1)));if(data.hasMore)pages.append(action('下一页',()=>syncDialog(index+1)));body.append(pages);if(data.running||waiting)setTimeout(()=>guard(poll),1000);
 }
 async function poll(){if(!ownsDialog(owner))return;waiting=false;try{await load();}catch(error){if(ownsDialog(owner)){$('#dialog-body .sync-status').textContent='状态读取失败';$('#dialog-body').append(action('重新读取',()=>syncDialog(index),'quick-action'));}throw error;}}
 try{await load();}catch(error){if(ownsDialog(owner)){$('#dialog-body').replaceChildren(action('重新读取',()=>syncDialog(index),'quick-action'));toast(error.message,owner);}}
}
function syncConflict(conflict){showDialog('处理冲突',(body,owner)=>{
 body.append(node('h3','',conflict.title));const choice=field(body,'保留版本','','select','sync-version'),local=node('option','','保留本机');local.value='-1';choice.append(local);for(const version of conflict.versions){const option=node('option','','版本 '+(version.index+1)+(version.deleted?'（已删除）':''));option.value=String(version.index);choice.append(option);}choice.value='-1';
 const preview=node('div','sync-version-preview');preview.tabIndex=0;preview.setAttribute('role','region');preview.setAttribute('aria-label','版本完整内容');body.append(preview);
 const consent=node('label','checkbox'),remove=node('input');remove.type='checkbox';consent.append(remove,document.createTextNode('移除本机旧日历事件，再应用所选版本'));body.append(consent);const update=()=>{const version=conflict.versions.find(item=>String(item.index)===choice.value);preview.textContent=version?version.description||(version.deleted?'此版本已删除记录':'此版本无正文'):'保留当前本机记录';preview.scrollTop=0;consent.hidden=!conflict.calendar||choice.value==='-1';remove.checked=false;clearDialogFeedback();};choice.addEventListener('change',update);update();
 const buttons=node('div','actions'),save=action('应用版本',async()=>{const index=Number(choice.value);if(conflict.calendar&&index!==-1&&!remove.checked)throw new Error('请确认移除本机旧日历事件');save.disabled=true;try{await api('/api/sync/resolve',{id:conflict.id,signature:conflict.signature,index,removeCalendar:index!==-1&&remove.checked});if(ownsDialog(owner)){await syncDialog(0,true);}}finally{save.disabled=false;}},'primary');buttons.append(action('返回',()=>syncDialog()),save);body.append(buttons);
});}
function settingsPage(){const group=card('服务与数据');group.append(action('日程来源',sourcesDialog,'quick-action'),action('跨设备同步',()=>syncDialog(),'quick-action'));for(const [title,text]of [['AI 服务','浏览器使用手机已有 AI 配置，账号与密钥仅保留在手机'],['权限与提醒','系统日历授权、通知、后台运行及小组件设置请在手机操作'],['局域网访问','在手机关闭服务或重新配对，会撤销当前浏览器连接']]){const row=node('div','settings-row');row.append(node('h3','',title),node('p','subtle',text));group.append(row);}const include=node('label','checkbox'),checkbox=node('input');checkbox.type='checkbox';checkbox.checked=!!session.systemCalendarIncluded;checkbox.addEventListener('change',()=>guard(async()=>{checkbox.disabled=true;try{await api('/api/preferences',{systemCalendarIncluded:checkbox.checked});session.systemCalendarIncluded=checkbox.checked;}catch(error){checkbox.checked=!!session.systemCalendarIncluded;throw error;}finally{checkbox.disabled=false;}}));include.append(checkbox,document.createTextNode('包含系统其他日程'));group.append(include,action('合并重复日程',previewMerges,'quick-action'));group.append(action('断开当前浏览器',async()=>{await api('/api/logout',{});disconnect();},'quick-action'));return[heading('设置'),group];}
document.querySelectorAll('[data-page]').forEach(button=>button.addEventListener('click',()=>guard(()=>navigate(button.dataset.page))));$('#capture').addEventListener('click',capture);$('#settings').addEventListener('click',()=>guard(()=>navigate('settings')));$('#refresh').addEventListener('click',()=>guard(async()=>{session=await api('/api/session');applyPalette(session.palette);await render();}));$('#theme').addEventListener('click',()=>{themeOverride=true;document.body.classList.toggle('dark');applyPalette();});
// Refresh the session and actively processing record; never replace an open editor.
setInterval(()=>{if(!session||pending||attachmentWork||$('#dialog').open||document.hidden)return;guard(async()=>{const latest=await api('/api/session');session=latest;csrf=latest.csrf;applyPalette(latest.palette);if(route==='home'||currentTask&&['queued','processing'].includes(currentTask.task.status))await render();});},10000);
setInterval(()=>{detectMerges().catch(()=>{});},2500);
icons();guard(async()=>{const code=scannedPairing;scannedPairing=null;if(code!==null){if(!code){disconnect();throw new Error('配对二维码无效，请重新扫码或手动输入');}try{await api('/api/pair',{code});await connect();}catch(error){disconnect();throw new Error('扫码配对未完成，请重新扫码或手动输入');}return;}try{await connect();}catch(error){if(!csrf){disconnect();if(error.status===401)await requestApproval();else approvalStatus(error.message,true);}else throw error;}});
