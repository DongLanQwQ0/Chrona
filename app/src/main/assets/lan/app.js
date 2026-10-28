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
 'clock':'<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 2"/>',
 'list-checks':'<path d="m3 6 2 2 3-3M11 6h10M3 13h18M3 19h18"/>',
 'search':'<circle cx="10.5" cy="10.5" r="7.5"/><path d="m16 16 5 5"/>',
 'filter':'<path d="M4 7h16M7 12h10M10 17h4"/>',
 'log-out':'<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4M16 17l5-5-5-5M21 12H9"/>',
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
   const options=[...select.options].map((option,index)=>{const button=node('button','',option.textContent);button.type='button';button.setAttribute('role','option');button.setAttribute('aria-selected',String(index===select.selectedIndex));if(index===select.selectedIndex)button.append(icon('check'));button.disabled=option.disabled;button.tabIndex=-1;button.addEventListener('click',()=>{select.value=option.value;closeSelect(true);sync();select.dispatchEvent(new Event('input',{bubbles:true}));select.dispatchEvent(new Event('change',{bubbles:true}));});menu.append(button);return button;});
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
let csrf='',session=null,revision=0,route='home',page=0,search='',filter='',sequence=0,currentTask=null;
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
let choiceReturn=null;
function requestDialogClose(){
 if(choiceReturn){const restore=choiceReturn;choiceReturn=null;restore();return;}
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
 if(owner!==dialogGeneration)return;dialogEditor=null;choiceReturn=null;closeSelect(false,true);const dialog=$('#dialog');clearDialogFeedback();if(!dialog.open)return;dialog.inert=true;
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
function applyPalette(palette=phonePalette){if(!palette)return;phonePalette=palette;document.body.classList.toggle('dark',parseInt(palette.background.slice(1,3),16)<100);document.documentElement.style.colorScheme=document.body.classList.contains('dark')?'dark':'light';document.documentElement.style.setProperty('--phone-accent',readableAccent(palette.primary,getComputedStyle(document.body).getPropertyValue('--surface').trim()));}

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
let scheduleTab=0,publication=0,todoScope=0,inboxCategory='',rangeFrom='',rangeUntil='',selectedRows=new Set(),selectionMode=false,homeworkLocate=null;
let rangePreset=0,listSource=0,inboxOldest=false;
const scheduleOrders=new Map();
const selectionObservers=new Set();
const routeState=new Map();
function listState(){return {page,search,filter,scheduleTab,publication,todoScope,inboxCategory,rangeFrom,rangeUntil,rangePreset,listSource,selected:[...selectedRows],selectionMode,scroll:scrollY};}
function restoreList(value={}){page=value.page??0;search=value.search??'';filter=value.filter??'';scheduleTab=value.scheduleTab??0;publication=value.publication??0;todoScope=value.todoScope??0;inboxCategory=value.inboxCategory??'';rangeFrom=value.rangeFrom??'';rangeUntil=value.rangeUntil??'';rangePreset=value.rangePreset??0;listSource=value.listSource??0;selectedRows=new Set(value.selected||[]);selectionMode=!!value.selectionMode;}
const categories={event:'活动',task:'待办',homework:'作业'};
const scheduleCategories={...categories,exam:'考试'};
function taskLike(category){return category==='task'||category==='homework';}
const SHIFTED_START_INTERVAL_MILLIS=5*60*1000;
function executionStart(item){return item.executionStart??item.start??item.effectiveDue;}
function executionEnd(item){return item.executionEnd??item.end;}
function executionAllDay(item){return item.executionAllDay??item.allDay;}
// System instances already carry phone-zone display boundaries; candidate all-day values remain UTC civil dates.
function displayTime(item,value,full=false,exclusiveEnd=false){if(item.system&&executionAllDay(item)&&value!=null)return phoneDate(value-(exclusiveEnd&&value>item.start?1:0))+' · 全天';return fmt(value,executionAllDay(item),full,item.zone);}
function executionWhen(item,full=false){
 const start=executionStart(item),end=executionEnd(item),allDay=executionAllDay(item);
 if(start==null)return '待安排时间';
 const first=displayTime(item,start,full);
 if(item.system&&allDay&&end!=null&&end>start&&phoneDate(start)!==phoneDate(end-1))return first+' — '+displayTime(item,end,full,true);
 return !allDay&&end!=null&&end>start?first+' — '+fmt(end,false,full&&phoneDate(start)!==phoneDate(end),item.zone):first;
}
function entry(item,click,source='',scope=''){
 const button=action('',click,'entry'),time=node('span','entry-time',displayTime(item,executionStart(item)));
 const main=node('span','entry-main');main.append(node('span','entry-title',item.title),node('span','entry-detail',[item.effectiveDue!=null?`截止 · ${fmt(item.effectiveDue,false,true,item.zone)}`:'',item.location,source||categories[item.category],item.needsConfirmation?'待确认':''].filter(Boolean).join(' · ')));
 button.dataset.key=`entry-${scope}-${item.kind||item.category}-${item.taskId||0}-${item.id||item.start}-${item.start}`;button.dataset.content=JSON.stringify(item);
 button.append(time,main,icon('chevron-right'));const complete=completionAction(item,null,true);if(!complete)return button;const row=node('div','entry-row');row.dataset.key=button.dataset.key;button.removeAttribute('data-key');row.append(button,complete);return row;
}
function taskRoute(item){return `task/${item.taskId}?candidate_id=${item.id}${item.occurrenceAt!=null?'&occurrenceAt='+item.occurrenceAt:''}`;}
function itemDate(item){const value=executionStart(item)??item.created;if(value==null)return '待安排';return executionAllDay(item)&&!item.system?new Date(value).toISOString().slice(0,10):phoneDate(value);}
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
const pageOrder=['home','schedule','inbox'];
let mainSection='home';
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
 if(!['home','schedule','inbox'].includes(next)&&!next.startsWith('task/'))next='home';
 const previous=route,changed=previous!==next;
 const nextSection=pageOrder.includes(next)?next:mainSection,sectionChanged=nextSection!==mainSection;
 if(sectionChanged){selectedRows.clear();selectionMode=false;for(const saved of routeState.values()){saved.selected=[];saved.selectionMode=false;}mainSection=nextSection;}
 if(changed){routeState.delete(previous);routeState.set(previous,listState());while(routeState.size>10)routeState.delete(routeState.keys().next().value);}
 route=next;restoreList(initialState??(changed?routeState.get(next):listState()));if(sectionChanged){selectedRows.clear();selectionMode=false;}currentTask=null;homeworkLocate=initialState?.locate??null;
 if(next.startsWith('task/')&&new URLSearchParams(query||'').has('candidate_id'))detailPosition(Number(path.split('/')[1])).detailOpen=true;
 const destination=next==='schedule'&&initialState?`schedule?${new URLSearchParams({category:filter,tab:scheduleTab,todo:todoScope})}`:next;
 if(updateHash&&location.hash!==`#${destination}`)history.pushState(next==='schedule'?{schedule:listState()}:null,'',`#${destination}`);
 document.querySelectorAll('[data-page]').forEach(button=>{const selected=button.dataset.page===mainSection;button.classList.toggle('active',selected);if(selected)button.setAttribute('aria-current','page');else button.removeAttribute('aria-current');});
 updateCapture();updateDock();const direction=next.startsWith('task/')?1:previous.startsWith('task/')?-1:Math.sign(pageOrder.indexOf(next)-pageOrder.indexOf(previous));
 await render(changed?{direction,detail:next.startsWith('task/')}:false);
 if(changed)window.scrollTo(0,routeState.get(next)?.scroll??0);
}
window.addEventListener('popstate',()=>guard(()=>navigate(location.hash.slice(1)||'home',false)));
async function render(animate=false){if(inlineEditor?.form.isConnected)return;const ticket=++sequence,host=$('#content');if(!host.childElementCount)host.append(node('p','loading','正在读取手机…'));let content;
 if(route.startsWith('task/'))content=await taskPage(Number(route.split('/')[1].split('?')[0]),ticket);
 else if(route==='home')content=await homePage(ticket);
 else if(route==='inbox'||route==='schedule')content=await listPage(ticket);
 else{route='home';content=await homePage(ticket);}
 if(ticket===sequence){
  const switching=animate?.local&&animate.direction&&host.querySelector('.result-list');
  if(switching){const exit=motion(switching,[{opacity:1,transform:'translateX(0)'},{opacity:0,transform:`translateX(${-Math.sign(animate.direction)*12}px)`}],120);if(exit)await exit.finished.catch(()=>{});if(ticket!==sequence)return;}
  const active=document.activeElement,label=host.contains(active)?active.getAttribute('aria-label'):null,selection=label&&'selectionStart' in active?[active.selectionStart,active.selectionEnd]:null,scrollY=window.scrollY;
  clearReveals();
  const previous=new Map([...host.querySelectorAll('[data-key]')].map(element=>[element.dataset.key,{element,rect:element.getBoundingClientRect()}]));
  const incoming=document.createDocumentFragment();incoming.append(...content);
  const local=!animate||animate.local;
  const reused=new Set();
  if(local)for(const element of incoming.querySelectorAll('[data-preserve]')){const old=host.querySelector(`[data-preserve="${element.dataset.preserve}"]`);if(old&&old.dataset.state===element.dataset.state)element.replaceWith(old);}
  if(local)for(const element of incoming.querySelectorAll('[data-key]')){const old=previous.get(element.dataset.key);if(old&&!reused.has(old.element)&&!element.matches('.selectable-result')&&!element.querySelector('input')&&old.element.isEqualNode(element)){element.replaceWith(old.element);reused.add(old.element);}}
  $('#workspace').classList.toggle('task-mode',route.startsWith('task/'));host.classList.toggle('list-mode',route==='schedule'||route==='inbox');
  host.replaceChildren(incoming);icons();for(const observer of selectionObservers)if(!observer.host?.isConnected){observer.disconnect();selectionObservers.delete(observer);}if(route==='schedule'&&homeworkLocate)locateHomework(homeworkLocate,ticket);
  if(route==='schedule')history.replaceState({schedule:listState()},'',`#schedule?${new URLSearchParams({category:filter,tab:scheduleTab,todo:todoScope})}`);
  if(local){window.scrollTo(0,scrollY);for(const element of host.querySelectorAll('[data-key]')){const old=previous.get(element.dataset.key);if(!old)continue;const rect=element.getBoundingClientRect(),x=old.rect.x-rect.x,y=old.rect.y-rect.y;if(Math.abs(x)+Math.abs(y)>1)motion(element,[{transform:`translate(${x}px,${y}px)`},{transform:'translate(0,0)'}],280);}}
  if(label&&local){const replacement=[...host.querySelectorAll('[aria-label]')].find(element=>element.getAttribute('aria-hidden')!=='true'&&element.getAttribute('aria-label')===label);replacement?.focus({preventScroll:true});if(selection&&replacement?.setSelectionRange)replacement.setSelectionRange(...selection);}
  if(animate&&!animate.local){motion(host,[{opacity:.55,transform:`translateX(${(animate.direction||1)*(animate.detail?20:12)}px)`},{opacity:1,transform:'translateX(0)'}],240);observeCards(host);}
  else if(switching){const list=host.querySelector('.result-list');if(list)motion(list,[{opacity:0,transform:`translateX(${Math.sign(animate.direction)*12}px)`},{opacity:1,transform:'translateX(0)'}],180);}
 }}
function iconAction(name,label,work,pressed){const button=action('',work,'icon-button');button.append(icon(name));button.setAttribute('aria-label',label);button.title=label;if(pressed!==undefined)button.setAttribute('aria-pressed',String(pressed));return button;}
function actionGroup(buttons){const group=node('div','header-action-group');group.append(...buttons);return group;}
function preserve(element,key,state){element.dataset.preserve=key;element.dataset.state=JSON.stringify(state);return element;}
function updateCapture(){const shown=route==='home',button=$('#capture');button.inert=!shown;button.setAttribute('aria-hidden',String(!shown));$('.bottom').classList.toggle('without-capture',!shown);}
async function refreshHomework(){
 if(!session.homeworkRefreshAvailable){toast(session.homeworkStatus||'请先在手机启用折姜星辰并登录');return;}
 if(session.homeworkRefreshing)return;
 const response=await api('/api/homework/refresh',{});toast(response.message);
 if(response.accepted){homeworkRefreshPending=true;session.homeworkRefreshing=true;updateHomeworkRefresh();}
}
function updateHomeworkRefresh(){for(const button of document.querySelectorAll('[data-homework-refresh]')){button.disabled=!!session?.homeworkRefreshing;button.classList.toggle('refreshing',!!session?.homeworkRefreshing);button.setAttribute('aria-label',session?.homeworkRefreshing?'正在刷新作业':'刷新作业');}}
async function openHomework(){scheduleOrders.set(3,true);await navigate('schedule',true,{filter:'homework',scheduleTab:3,todoScope:3,locate:unreadHomework});}
let unreadHomework=null,homeworkRefreshPending=false;
async function homePage(ticket){
 const data=await api('/api/home');if(ticket!==sequence)return [];revision=data.revision;unreadHomework=data.unreadHomework??null;
 const homework=iconAction('list-checks','作业',openHomework);if(unreadHomework)homework.classList.add('has-unread');
 const system=iconAction('calendar-days','包含系统其他日程',async()=>{await api('/api/preferences',{systemCalendarIncluded:!session.systemCalendarIncluded});session.systemCalendarIncluded=!session.systemCalendarIncluded;await render();},!!session.systemCalendarIncluded);
 system.classList.add('system-toggle');
 const head=heading('首页',data.date,[actionGroup([homework,system])]),columns=node('div','columns home-columns'),primary=node('div','home-primary'),secondary=node('div','home-secondary');
 const events=[...data.items.map(item=>({...item,kind:'event'})),...data.calendar.map(item=>({...item,kind:'calendar'}))].sort((a,b)=>timelineTime(a)-timelineTime(b));
 const future=events.slice(0,data.limit??30),next=future[0],nextCard=card('下一件事');
 if(next)nextCard.append(entry(next,()=>next.kind==='event'?navigate(taskRoute(next)):showReadOnly(next),next.kind==='calendar'?next.source||'日历':'','home-next'));else empty(nextCard,'暂无未来安排');primary.append(nextCard);
 if(data.todos){const todo=card('待办 · '+data.todos.total),counts=node('div','stat-row');
  for(const [value,label,scope,tab]of [[data.todos.todayDue,'今日截止',1,3],[data.todos.overdue,'已逾期',2,3],[data.todos.unscheduled,'待安排',3,1]]){const button=action('',()=>openSchedule({scheduleTab:tab,todoScope:scope}),'stat');button.append(node('strong','',String(value)),node('span','',label));counts.append(button);}todo.append(counts);
  for(const item of data.todos.items)todo.append(entry(item,()=>navigate(taskRoute(item)),'','home-todos'));
  todo.append(action('全部待办',()=>openSchedule({scheduleTab:3,todoScope:3}),'quick-action'));primary.append(todo);
 }
 const agenda=card('时间线'),list=node('div','timeline');appendGrouped(list,future,item=>entry(item,()=>item.kind==='event'?navigate(taskRoute(item)):showReadOnly(item),item.kind==='calendar'?item.source||'日历':'','home-timeline'),data.date);if(!future.length)empty(list,'暂无未来安排');agenda.append(list);secondary.append(agenda);
 const stats=node('div','stat-row');for(const [value,label,status]of [[data.review,'待确认','1'],[data.failed,'处理失败','3']]){const button=action('',()=>navigate('inbox',true,{filter:status}),'stat');button.append(node('strong','',String(value)),node('span','',label));stats.append(button);}secondary.append(stats);columns.append(primary,secondary);return [head,columns];
}
async function openSchedule(values){await navigate('schedule',true,values);}
function showReadOnly(item){showDialog(item.title,body=>{
 body.append(node('p','subtle',item.officialTime||`${displayTime(item,item.start,true)} — ${displayTime(item,item.end,true,true)}`));
 if(item.source)body.append(node('span','tag',item.source));if(item.location)body.append(node('p','raw-text',item.location));if(item.description)body.append(node('p','raw-text',item.description));
 const state=completionAction(item,()=>{closeDialog();});if(state){const controls=node('div','detail-actions');controls.append(state);body.append(controls);}
});}
function groupingDate(item,inbox=false){
 let value,allDay=false;
 if(inbox)value=item.created;
 else if(todoScope===2)value=item.overdueAt??candidateOverdueAt(item);
 else if(todoScope===1||filter==='homework')value=item.effectiveDue??item.due;
 else{value=item.executionStart??item.start??item.due;allDay=executionAllDay(item);}
 if(value==null)return '待安排';return allDay&&!item.system?new Date(value).toISOString().slice(0,10):phoneDate(value);
}
function locateHomework(id,ticket){
 const row=[...document.querySelectorAll('.selectable-result')].find(row=>row.dataset.id===String(id));if(!row)return;
 const locate=()=>{if(ticket!==sequence||!row.isConnected||document.hidden)return;const dock=$('.bottom').getBoundingClientRect(),height=Math.max(1,dock.top),bounds=row.getBoundingClientRect(),top=window.scrollY+bounds.top-(height-bounds.height)/2;
 const needed=Math.max(0,top+innerHeight-document.documentElement.scrollHeight);if(needed){const spacer=node('div','locate-space');spacer.style.height=needed+'px';$('#content').append(spacer);}window.scrollTo(0,Math.max(0,top));row.classList.add('homework-located');
 requestAnimationFrame(()=>{if(ticket!==sequence||document.hidden||!row.isConnected)return;const visible=row.getBoundingClientRect();if(visible.top>=0&&visible.bottom<=dock.top+1){homeworkLocate=null;const candidate=String(id);api('/api/homework/seen',{candidateId:candidate,revision}).then(()=>{if(unreadHomework===candidate)unreadHomework=null;}).catch(error=>toast(error.message));}setTimeout(()=>row.classList.remove('homework-located'),2240);});
 };
 if(document.hidden){document.addEventListener('visibilitychange',locate,{once:true});return;}requestAnimationFrame(locate);
}

function searchForm(inbox){
 const form=node('form','filters list-search'),input=node('input');input.type='search';input.placeholder=inbox?'搜索收件':'搜索日程';input.setAttribute('aria-label',input.placeholder);input.value=search;input.maxLength=256;
 const select=node('select');select.setAttribute('aria-label',inbox?'收件状态':'日程类型');
 for(const [value,label]of (inbox?[['','全部状态'],['1','待确认'],['2','处理中'],['3','失败'],['4','已写入日历']]:[['','全部类型'],...Object.entries(scheduleCategories)])){const option=node('option','',label);option.value=value;select.append(option);}select.value=filter;
 select.addEventListener('change',()=>{filter=select.value;if(!inbox&&!taskLike(filter))todoScope=0;search=input.value.trim();refreshList();});
 const submit=node('button','icon-button');submit.type='submit';submit.setAttribute('aria-label','搜索');submit.append(icon('search'));
 form.append(input,select,actionGroup([submit,iconAction('filter','筛选',()=>filterDialog(inbox)),iconAction('x','清除筛选',()=>{restoreList();return render({local:true});})]));
 form.addEventListener('submit',event=>{event.preventDefault();search=input.value.trim();filter=select.value;refreshList();});return preserve(form,'list-search',{inbox,search,filter});
}
function refreshList(){page=0;selectedRows.clear();selectionMode=false;homeworkLocate=null;return guard(()=>render({local:true,direction:1}));}
function choicePanel(label,choices,value,change){
 const dialog=$('#dialog'),body=$('#dialog-body'),title=$('#dialog-title'),parent=dialog.open&&!dialog.inert?{nodes:[...body.childNodes],title:title.textContent,scroll:dialog.scrollTop,editor:dialogEditor}:null;
 const restore=()=>{if(!parent)return;choiceReturn=null;body.replaceChildren(...parent.nodes);title.textContent=parent.title;dialogEditor=parent.editor;dialog.scrollTop=parent.scroll;};
 const build=(body,owner)=>{const list=node('div','choice-list');for(const [key,text]of choices){const button=action('',()=>{if(parent)restore();else closeDialog(false,owner);return change(key);});button.append(node('span','',text));button.setAttribute('aria-pressed',String(String(key)===String(value)));if(String(key)===String(value))button.append(icon('check'));list.append(button);}body.replaceChildren(list);};
 if(parent){choiceReturn=restore;dialogEditor=null;title.textContent=label;build(body,dialogGeneration);dialog.scrollTop=0;body.querySelector('button')?.focus({preventScroll:true});}else showDialog(label,build);
}
function selectionBar(label,choices,value,change){
 const host=node('div','selection-host'),bar=node('div','selection-bar');bar.setAttribute('role','group');bar.setAttribute('aria-label',label);
 const highlight=node('span','selection-highlight');bar.append(highlight);let active;
 for(const [key,text]of choices){const button=action(text,()=>{active=button;bar.querySelectorAll('button').forEach(b=>b.setAttribute('aria-pressed',String(b===button)));position();change(key);});button.setAttribute('aria-pressed',String(String(key)===String(value)));if(String(key)===String(value))active=button;bar.append(button);}
 const compact=action('',()=>choicePanel(label,choices,value,change),'selection-current');compact.append(node('span','',choices.find(([key])=>String(key)===String(value))?.[1]||''),icon('chevron-right'));compact.setAttribute('aria-label',label);compact.hidden=true;
 const position=()=>{if(active){highlight.style.width=active.offsetWidth+'px';highlight.style.transform=`translateX(${active.offsetLeft}px)`;}const buttons=[...bar.querySelectorAll('button')],required=buttons.reduce((sum,button)=>{const style=getComputedStyle(button),canvas=selectionBar.canvas||(selectionBar.canvas=document.createElement('canvas')),context=canvas.getContext('2d');context.font=style.font;return sum+context.measureText(button.textContent).width+parseFloat(style.paddingLeft)+parseFloat(style.paddingRight);},10);const small=required>host.clientWidth;bar.classList.toggle('selection-measure',small);compact.hidden=!small;};
 host.append(bar,compact);requestAnimationFrame(()=>{if(!bar.isConnected)return;position();const observer=new ResizeObserver(position);observer.host=host;selectionObservers.add(observer);observer.observe(host);});return host;
}

function monthAfter(date,amount=1){const d=new Date(date.slice(0,8)+'01T00:00:00Z');d.setUTCMonth(d.getUTCMonth()+amount);return d.toISOString().slice(0,10);}
function presetWindow(value){const today=phoneDate();if(value===1)return [today,addCivilDays(today,1)];if(value===2){const from=addCivilDays(today,-(civilWeekday(today)+6)%7);return [from,addCivilDays(from,7)];}if(value===3){const from=today.slice(0,8)+'01';return [from,monthAfter(from)];}return ['',''];}
function filterDialog(inbox){showDialog('筛选'+(inbox?'收件':'日程'),(body,owner)=>{
 let type=inbox?inboxCategory:filter,status=filter,pub=publication,todo=todoScope,from=rangeFrom,until=rangeUntil,preset=rangePreset,source=listSource,tab=scheduleTab;
 const draw=()=>{body.replaceChildren();const add=(label,choices,value,change,disabled=false)=>{const bar=selectionBar(label,choices,value,value=>{change(value);draw();});if(disabled)bar.querySelectorAll('button').forEach(button=>button.disabled=true);body.append(node('h3','filter-label',label),bar);};
 if(!inbox){add('日期',[[0,'全部'],[1,'今天'],[2,'本周'],[3,'本月'],[5,'自选']],preset,value=>{preset=Number(value);if(preset!==5)[from,until]=presetWindow(preset);if(preset)tab=3;});
 if(from&&until){const row=node('div','range-step');const shift=direction=>{if(preset===3){from=monthAfter(from,direction);until=monthAfter(from);}else{const count=Math.round((Date.parse(until)-Date.parse(from))/86400000);from=addCivilDays(from,direction*count);until=addCivilDays(until,direction*count);preset=5;}tab=3;draw();};row.append(action('上一段',()=>shift(-1)),node('span','',`${from} — ${addCivilDays(until,-1)}`),action('下一段',()=>shift(1)));body.append(row);}
 if(preset===5){const dates=node('div','two-fields'),a=node('div'),b=node('div'),start=field(a,'起始日期',from,'date','range-from'),end=field(b,'结束日期',until?addCivilDays(until,-1):'','date','range-until');dates.append(a,b);body.append(dates);start.addEventListener('change',()=>{from=start.value;tab=3;});end.addEventListener('change',()=>{until=end.value?addCivilDays(end.value,1):'';tab=3;});}}
 if(inbox)add('状态',[['','全部状态'],['1','待确认'],['2','处理中'],['3','失败'],['4','已写入日历']],status,value=>status=value);
 add('类型',[['','全部'],...Object.entries(inbox?categories:scheduleCategories)],source&&!inbox?'event':type,value=>{type=value;if(!taskLike(type))todo=0;},!inbox&&source===1);
 if(!inbox){add('待办',[[0,'不限'],[1,'今日截止'],[2,'已逾期'],[3,'未完成']],source?0:todo,value=>{todo=Number(value);if(todo){if(type!=='homework')type='task';tab=3;}},source===1);add('日历状态',[[0,'全部'],[3,'未写入'],[1,'待确认'],[2,'已写入']],source?2:pub,value=>pub=Number(value),source===1);add('来源',[[0,'拾时'],[1,'系统日程']],source,value=>{source=Number(value);if(source){type='';pub=todo=0;}});}
 const buttons=node('div','actions');buttons.append(action('应用筛选',()=>{if(!inbox&&((!from)!==(!until)||from&&until<=from))throw new Error('请选择完整且有效的日期范围');if(inbox){inboxCategory=type;filter=status;}else{filter=type;publication=pub;todoScope=todo;rangeFrom=from;rangeUntil=until;rangePreset=preset;listSource=source;scheduleTab=tab;}closeDialog(false,owner);return refreshList();},'primary'));body.append(buttons);icons();};draw();
});}
function listParams(inbox,includeIds=false){const params=new URLSearchParams({page,search});const remembered=inbox?inboxOldest:scheduleOrders.get(scheduleTab);if(remembered!==undefined)params.set('oldestFirst',remembered);if(filter)params.set(inbox?'status':'category',filter);if(inbox){if(inboxCategory)params.set('category',inboxCategory);}else{params.set('tab',scheduleTab);params.set('publication',publication);params.set('todo',todoScope);params.set('source',listSource);if(rangeFrom&&rangeUntil){params.set('from',rangeFrom);params.set('until',rangeUntil);}if(homeworkLocate)params.set('locate',homeworkLocate);}if(includeIds)params.set('includeIds','true');return params;}
async function listPage(ticket){
 const inbox=route==='inbox',path=`/api/${route}?${listParams(inbox)}`,data=await api(path);if(ticket!==sequence)return [];revision=data.revision;page=data.page;if(homeworkLocate&&data.located!==homeworkLocate)homeworkLocate=null;
 if(inbox)inboxOldest=data.oldestFirst;else scheduleOrders.set(scheduleTab,data.oldestFirst);
 const list=node('div','result-list'),toolbar=node('div','bulk-toolbar'),count=node('span','list-count'),controls=node('div','selection-actions');
 const clear=()=>{selectedRows.clear();selectionMode=false;return render({local:true});};
 const update=()=>{count.textContent=selectionMode?`已选 ${selectedRows.size} 条`:`共 ${data.total} 条${inbox?'收件':'日程'}`;toolbar.classList.toggle('selecting',selectionMode);for(const row of list.querySelectorAll('.selectable-result')){const selected=selectedRows.has(Number(row.dataset.id));row.classList.toggle('selected',selected);const circle=row.querySelector('.selection-circle');if(circle){circle.hidden=!selectionMode;circle.replaceChildren(icon(selected?'check':'circle'));circle.setAttribute('aria-pressed',String(selected));}row.querySelector('.completion-action')?.toggleAttribute('hidden',selectionMode);}drawActions();};
 const selectPage=()=>{const ids=data.items.filter(item=>!item.readOnly).map(item=>item.id),full=ids.length&&ids.every(id=>selectedRows.has(id));for(const id of ids){if(full)selectedRows.delete(id);else selectedRows.add(id);}update();};
 const selectMatching=async()=>{const snapshot=await api(path+'&includeIds=true');if(ticket!==sequence)return;if(snapshot.revision!==revision)throw new Error('数据已更新，请刷新后重试');if(snapshot.ids.length>500)throw new Error('每次最多选择 500 条，请缩小筛选范围');selectedRows=new Set(snapshot.ids);update();};
 const publish=()=>{if(!selectedRows.size)throw new Error('请先选择记录');confirmAction('批量写入日历','只写入信息完整的事项，其余保留。',async()=>{const result=await api('/api/calendar/publish-selected',{ids:[...selectedRows].map(String),inbox,revision});selectedRows.clear();selectionMode=false;await render({local:true});toast(result.summary);});};
 const remove=()=>{if(!selectedRows.size)throw new Error('请先选择记录');confirmAction('删除所选',inbox?'删除所选原文、日程及关联日历事件，公共 Downloads 文件保留。':'删除所选日程及关联日历事件；重复日程会删除整个系列，收件原文保留。',async()=>{await api('/api/selection/delete',{ids:[...selectedRows].map(String),inbox,revision});selectedRows.clear();selectionMode=false;await render({local:true});});};
 function drawActions(){controls.replaceChildren();if(data.system||data.selectableTotal===0)return;if(!selectionMode){controls.append(action('选择',()=>{selectionMode=true;update();}));return;}const choices=[['page','本页'],['all','全选'],['write','写入'],['delete','删除']],work={page:selectPage,all:selectMatching,write:publish,delete:remove};const actions=action('操作',()=>choicePanel('操作',choices,'',key=>work[key]()),'batch-menu');controls.append(actions);const direct=node('div','batch-direct');for(const [key,label]of choices){const button=action(label,work[key],key==='delete'?'danger':'');if(key==='write')button.disabled=!selectedRows.size||!session.calendarAllowed;if(key==='delete')button.disabled=!selectedRows.size;direct.append(button);}controls.append(direct,action('取消',clear));}
 toolbar.append(count,controls);if(data.examWarning)list.append(node('p','feedback',data.examWarning));
 let day='';for(const item of data.items){const date=groupingDate(item,inbox);if(date!==day){list.append(node('h3','list-day',date));day=date;}const row=node('article','selectable-result');row.dataset.key=`${inbox?'task':'candidate'}-${item.id}`;row.dataset.id=String(item.id);
 const toggle=()=>{selectionMode=true;if(selectedRows.has(item.id))selectedRows.delete(item.id);else{if(selectedRows.size>=500)throw new Error('每次最多选择 500 条');selectedRows.add(item.id);}update();};
 const readOnly=data.system||item.readOnly;
 const button=action('',()=>{if(suppress)return;if(selectionMode&&!readOnly){toggle();return;}return readOnly?showReadOnly(item):navigate(inbox?`task/${item.id}`:taskRoute(item));},'card glass');
 let press=null,suppress=false;const cancelPress=()=>{clearTimeout(press?.timer);press=null;};if(!readOnly){button.addEventListener('pointerdown',event=>{if(event.button!==0||selectionMode)return;cancelPress();press={x:event.clientX,y:event.clientY,timer:setTimeout(()=>{suppress=true;guard(toggle);press=null;},500)};});button.addEventListener('pointermove',event=>{if(press&&Math.hypot(event.clientX-press.x,event.clientY-press.y)>8)cancelPress();});button.addEventListener('pointerup',()=>{cancelPress();if(suppress)setTimeout(()=>suppress=false,0);});button.addEventListener('pointercancel',()=>{cancelPress();suppress=false;});button.addEventListener('contextmenu',event=>event.preventDefault());}
 if(inbox){button.append(node('span','status-label',statusLabel(item.status)),node('h3','',item.text.slice(0,120)||'附件记录'),node('p','subtle',item.error||item.source));const meta=node('div','meta');meta.append(node('span','',fmt(item.created,false,true)),icon('chevron-right'));button.append(meta);}
 else{button.append(node('span','status-label',(item.readOnly?'考试 · '+item.source:data.system?item.source||'系统日程':categories[item.category])+(item.completed?' · 已完成':item.calendarLinked?' · 已写入日历':item.needsConfirmation?' · 待确认':'')),node('h3','',item.title),node('p','subtle',item.officialTime||executionWhen(item,true)));if(item.effectiveDue!=null)button.append(node('p','subtle',`截止 · ${fmt(item.effectiveDue,false,true,item.zone)}`));if(item.location)button.append(node('p','subtle',item.location));}
 if(item.recurrence)button.append(node('span','tag',item.repeatLabel));row.append(button);if(!readOnly){const circle=iconAction('circle','选择 '+(inbox?item.text.slice(0,40):item.title),toggle);circle.classList.add('selection-circle');row.append(circle);}if(!inbox){const state=completionAction(item,null,true);if(state)row.append(state);}list.append(row);}
 update();if(!data.items.length)empty(list,inbox?'没有符合条件的收件':filter==='exam'&&!data.examsLoaded?'尚未获取考试安排，请在手机中刷新':'没有符合条件的日程');
 const order=action(data.oldestFirst?'最早优先':'最近优先',()=>{if(inbox)inboxOldest=!data.oldestFirst;else scheduleOrders.set(scheduleTab,!data.oldestFirst);return refreshList();});
 const refresh=iconAction('refresh-cw','刷新作业',refreshHomework);refresh.dataset.homeworkRefresh='';refresh.disabled=!!session.homeworkRefreshing;refresh.classList.toggle('refreshing',!!session.homeworkRefreshing);
 const result=[heading(inbox?'收件箱':'日程','',[order,...(!inbox&&filter==='homework'?[refresh]:[])]),searchForm(inbox)];
 if(!inbox)result.push(preserve(selectionBar('日程范围',[[0,'待处理'],[1,'待安排'],[2,'历史'],[3,'全部']],scheduleTab,value=>{scheduleTab=Number(value);refreshList();}),'schedule-tabs',scheduleTab));
 const tags=[inbox?categories[inboxCategory]:scheduleCategories[filter],!inbox&&listSource?'系统日程':'',!inbox&&['','待确认','已写入','未写入'][publication],!inbox&&['','今日截止','已逾期','未完成'][todoScope],!inbox&&(rangeFrom||data.from)&&`${rangeFrom||data.from} — ${addCivilDays(rangeUntil||data.until,-1)}`].filter(Boolean);
 if(tags.length)result.push(node('p','filter-summary',tags.join(' · ')));result.push(toolbar,list,pagination(data));return result;
}

function pagination(data){const total=Math.max(1,Math.ceil(data.total/data.pageSize)),row=node('div','pagination'),turn=async direction=>{page+=direction;await render({local:true,direction});window.scrollTo({top:0,behavior:reducedMotion.matches?'instant':'smooth'});},previous=action('',()=>turn(-1)),next=action('',()=>turn(1));previous.append(icon('chevron-left'));next.append(icon('chevron-right'));previous.setAttribute('aria-label','上一页');next.setAttribute('aria-label','下一页');previous.disabled=page<=0;next.disabled=page+1>=total;row.append(previous,node('span','',`${page+1} / ${total}`),next);return row;}
function showDialog(title,build){const owner=++dialogGeneration;dialogEditor=null;choiceReturn=null;closeSelect(false,true);clearDialogFeedback();const dialog=$('#dialog'),wasOpen=dialog.open;dialog.inert=false;$('#dialog-title').textContent=title;$('#dialog-body').replaceChildren();build($('#dialog-body'),owner);if(!dialog.open)dialog.showModal();else dialog.querySelector('input,textarea,button')?.focus({preventScroll:true});icons();motion(dialog,[{opacity:wasOpen?1:0,transform:wasOpen?'translateY(0) scale(1)':'translateY(10px) scale(.99)'},{opacity:1,transform:'translateY(0) scale(1)'}]);return owner;}
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
 }catch(error){if(error.status===409)stale=true;if(saved&&ownsDialog(owner)&&!buttons.querySelector('.view-saved'))buttons.append(action('查看已保存记录',async()=>{closeDialog(false,owner);await navigate(`task/${saved.id}`);},'view-saved'));throw error;}finally{working=false;attachmentWork--;submit.disabled=false;attachments.lock(false);}});});dialogEditor={form,dirty:()=>!saved&&!!input.value.trim()||attachments.chosen.some(item=>!item.done),working:()=>working};body.append(form);
});}
function appendAttachments(id,version){showDialog('添加附件',(body,owner)=>{const attachments=attachmentPicker(body),save=action('上传附件',async()=>{if(!attachments.chosen.some(item=>!item.done))throw new Error('请先选择附件');save.disabled=true;attachments.lock(true);attachmentWork++;try{version=await attachments.upload(id,version);if(ownsDialog(owner)){closeDialog(false,owner);await render();}}finally{attachmentWork--;save.disabled=false;attachments.lock(false);}},'primary');const buttons=node('div','actions');buttons.append(save);body.append(buttons);});}
function disclosure(label,text){const details=node('details'),summary=node('summary','subtle',label),panel=node('div','disclosure-body');panel.append(node('p','raw-text',text));details.append(summary,panel);let expanded=false;summary.addEventListener('click',event=>{event.preventDefault();const start=details.open?panel.getBoundingClientRect().height:0;expanded=!expanded;details.open=true;summary.setAttribute('aria-expanded',String(expanded));const end=expanded?panel.scrollHeight:0,animation=motion(panel,[{height:start+'px',opacity:start?1:0,overflow:'hidden'},{height:end+'px',opacity:expanded?1:0,overflow:'hidden'}],160);if(!animation){details.open=expanded;return;}animation.finished.then(()=>{if(activeMotion.get(panel)===animation&&!expanded)details.open=false;}).catch(()=>{});});return details;}
// The selected business identity survives urgency sorting, edit and history restoration.
const detailPositions=new Map();
function detailPosition(id){if(!detailPositions.has(id)){detailPositions.set(id,{candidate:null,scroll:new Map()});if(detailPositions.size>10)detailPositions.delete(detailPositions.keys().next().value);}return detailPositions.get(id);}
function rememberDetailScroll(){const workspace=$('.task-workspace');if(!workspace)return;const saved=detailPosition(Number(workspace.dataset.task));saved.listScroll=workspace.querySelector('.master-list')?.scrollTop||0;saved.scroll.set(saved.candidate,window.scrollY);}
function candidateDeadline(item){return item.effectiveDue??item.due??executionStart(item)??null;}
function candidateOverdueAt(item){if(item.overdueAt!=null)return item.overdueAt;if(!taskLike(item.category)||item.record)return null;const execution=item.end??item.start??null,due=candidateDeadline(item);return execution===null?due:due===null?execution:Math.min(execution,due);}
function urgencyCandidates(items){return [...items].sort((a,b)=>{const complete=Number(!!a.completed)-Number(!!b.completed);if(complete)return complete;const first=candidateDeadline(a)??Infinity,second=candidateDeadline(b)??Infinity;return(first===second?0:first-second)||a.id-b.id;});}
function candidateStatus(item){const due=candidateDeadline(item),overdue=candidateOverdueAt(item);if(item.completed)return {key:'done',text:'已完成',icon:'check'};if(overdue!==null&&overdue<Date.now())return {key:'overdue',text:'已逾期',icon:'clock-alert'};if(taskLike(item.category)&&due!==null&&due-Date.now()<=86400000)return {key:'soon',text:'即将截止',icon:'clock-alert'};return {key:'open',text:item.needsConfirmation?'待确认':taskLike(item.category)?'未完成':due===null?'待安排':'已安排',icon:'circle'};}
function taskOriginal(data,id){
 const task=data.task,original=node('div','record-original');
 original.append(node('span','status-label',statusLabel(task.status)),node('p','raw-text',task.text));
 if(task.error)original.append(node('p','subtle',task.error));
 if(task.linkText)original.append(disclosure('链接正文',task.linkText));
 if(data.images.length){const grid=node('div','attachment-grid');for(const item of data.images){const link=node('a');link.href=`/api/attachment/${id}/image-${item.index}`;link.target='_blank';link.rel='noopener';const image=node('img');image.src=link.href;image.alt=`记录图片 ${item.index+1}`;image.loading='lazy';link.append(image);grid.append(link);}original.append(grid);}
 if(data.files.length){const files=node('div','files');for(const item of data.files){const link=node('a','',item.name+` · ${(item.size/1024).toFixed(1)} KB`);link.href=`/api/attachment/${id}/${item.id}`;files.append(link);}original.append(files);}
 const actions=node('div','detail-actions'),busy=['queued','processing'].includes(task.status),edit=action('修改原文',()=>editRaw(task));edit.disabled=busy;
 const process=action('AI 重新处理',()=>confirmAction('重新处理','这将替换已有日程',async()=>{try{await api('/api/task/process',{id:String(id),revision});}finally{await render();}}));
 process.disabled=busy||data.managedHomeworkCapture;
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
 const proposal=item.occurrenceUpdate,held=proposal&&proposal.state!=='new',controls=node('div','detail-actions');
 if(held)result.append(node('span','tag',proposal.state==='applied'?'已更新本次':proposal.state==='pending'?'更新本次 · '+proposal.original_date:'占位匹配待重新确认'));
 if(!item.record){const modify=action(item.recurrence?'重复日程':item.category==='homework'?'修改作业':'修改日程',()=>item.recurrence?repeatDialog(item):candidateEditor(item,$('.detail-reader')));modify.disabled=busy;controls.append(modify);}
 if(held&&proposal.state!=='applied'){const independent=action('另建日程',()=>confirmAction('另建日程','保留原占位，另建一条日程',async()=>{await api('/api/candidate/choose-new',{id:String(id),candidateId:String(item.id),baseline:item.baseline,revision});await render();}));independent.disabled=busy;controls.append(independent);}
 if(proposal?.state==='applied'&&proposal.targetTaskId)controls.append(action('查看本次日程',()=>openOccurrence(proposal.targetTaskId,proposal.targetCandidateId,proposal.original)));
 if(!item.record&&!held&&!item.recurrence&&!item.managed){const repeat=action('设置重复',()=>repeatRuleDialog(item));repeat.disabled=busy||item.start==null&&item.effectiveDue==null;controls.append(repeat);}
 if(item.recurrence)result.append(node('span','tag',item.repeatLabel+(item.repeatStoppedAt?' · 已停止':'')));
 const state=held?null:completionAction(item);if(state){state.disabled=busy;controls.append(state);}appendHomeworkLinks(result,item);
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
 const back=action('',()=>{const previous=[...routeState.keys()].reverse().find(key=>['home','inbox','schedule'].includes(key));return navigate(previous||'inbox');},'icon-button');back.append(icon('arrow-left'));back.setAttribute('aria-label','返回列表');
 return[heading('记录详情',task.source+' · '+fmt(task.created,false,true),[back]),workspace];
}
function editRaw(task){showDialog('修改原文',(body,owner)=>{const form=node('form'),input=field(form,'原文',task.text,'textarea','text');input.required=true;input.maxLength=100000;const actions=node('div','actions'),submit=node('button','primary','保存');submit.type='submit';actions.append(submit);form.append(actions);form.addEventListener('submit',event=>{event.preventDefault();guard(async()=>{submit.disabled=true;try{await api('/api/task/edit',{id:String(task.id),revision,text:input.value});closeDialog(false,owner);await render();}finally{submit.disabled=false;}});});dialogEditor={form,dirty:()=>input.value!==task.text,working:()=>submit.disabled};body.append(form);});}
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
 const occurrence=item.occurrenceEdit,held=item.occurrenceUpdate&&item.occurrenceUpdate.state!=='new';
 const buttons=node('div','actions editor-actions'),cancelButton=action('取消',cancel),save=node('button','primary',occurrence?'更新本次':held?'保存草稿':'保存');save.type='submit';let publishButton=null,submitting=false;
 if(item.id)buttons.append(action(occurrence?'跳过这次':'删除',()=>{const message=occurrence?item.title:'手机系统日历中关联的事件也将删除，原文保留。';if(dialogEditor?.form===form)confirmEditorAction(dialogEditor,occurrence?'跳过这次':'删除日程',message,'确认',remove);else confirmAction(occurrence?'跳过这次':'删除日程',message,remove);},'danger'));
 buttons.append(cancelButton,save);
 if(item.id&&!occurrence){publishButton=action(held?'更新本次':item.calendarLinked?'更新手机日历':'写入手机日历',()=>commit(true));publishButton.disabled=!session.calendarAllowed||held&&item.occurrenceUpdate.state!=='pending';buttons.append(publishButton);}
 form.append(fields,feedback,buttons);
 const setWorking=value=>{fields.inert=value;for(const button of buttons.querySelectorAll('button')){if(value){button.dataset.wasDisabled=String(button.disabled);button.disabled=true;}else{button.disabled=button.dataset.wasDisabled==='true';delete button.dataset.wasDisabled;}}};
 const values=()=>JSON.stringify([...form.querySelectorAll('input,textarea,select')].map(input=>[input.id,input.value,input.checked,input.disabled]));
 const original=values();
 async function remove(){
  if(submitting)return;submitting=true;setWorking(true);const confirmation=$('.editor-confirmation');if(confirmation)confirmation.inert=true;
  try{if(occurrence)await changeRepeat(item,'skip',{original:item.occurrenceAt});else await api('/api/candidate/delete',{id:String(item.taskId),candidateId:String(item.id),revision:baseline});if(form.isConnected)await finish();}
  finally{submitting=false;setWorking(false);if(confirmation)confirmation.inert=false;}
 }
 async function commit(publish){
  if(submitting||!form.reportValidity())return;
  feedback.hidden=true;
  try{
   const result={id:String(item.taskId),candidateId:String(item.id),baseline:item.baseline,revision:baseline,title:title.value,category:category.value,start:instant(start.value,allDay.checked,zone.value),end:instant(end.value,allDay.checked,zone.value),zone:zone.value,allDay:allDay.checked,location:location.value,description:description.value,reminder:reminder.value===''?null:Number(reminder.value),needsConfirmation:review.checked,due:taskLike(category.value)&&due.value?instant(due.value,false,zone.value):null,record:false};
   if(result.end!==null&&(result.start===null||result.end<=result.start))throw new Error('结束时间须晚于开始时间');
   submitting=true;setWorking(true);
   if(occurrence){result.action='edit';result.original=item.occurrenceAt;await api('/api/candidate/repeat',result);}
   else await api(item.id?(publish?'/api/candidate/publish':'/api/candidate/save'):'/api/candidate/create',result);
   if(form.isConnected)await finish();
  }catch(error){if(form.isConnected){feedback.textContent=error.message||'保存未完成，请重试';feedback.hidden=false;feedback.scrollIntoView({block:'nearest'});}}
  finally{if(submitting)setWorking(false);submitting=false;}
 }
 form.addEventListener('submit',event=>{event.preventDefault();guard(()=>commit(false));});
 return {form,dirty:()=>values()!==original,working:()=>submitting};
}

function completionAction(item,after=null,cardOnly=false){
 if(item.recurrence&&item.occurrenceAt==null)return null;
 if(item.record||!taskLike(item.category)&&(!item.canAcknowledge||cardOnly&&item.needsConfirmation))return null;
 const task=taskLike(item.category),selected=task?item.completed:item.acknowledged;
 const label=task?(selected?'恢复'+categories[item.category]:'标记完成'):(selected?'取消确认活动':'确认活动');
 const button=action('',async()=>{button.disabled=true;try{const result=item.system?await api('/api/agenda/acknowledge',{id:item.externalId,start:item.start,acknowledged:!selected}):item.recurrence&&task?await changeRepeat(item,selected?'reopen':'complete',{original:item.occurrenceAt}):await api(task?'/api/candidate/complete':'/api/candidate/acknowledge',{id:String(item.taskId),candidateId:String(item.id),revision,...(item.occurrenceAt!=null?{occurrenceAt:item.occurrenceAt}:{}),...(task?{completed:!selected}:{acknowledged:!selected})});await render({local:true});if(after)await after(result);if(result.warning)toast(result.warning);}finally{button.disabled=false;}},'icon-button completion-action');
 button.setAttribute('aria-label',label);button.title=label;button.setAttribute('aria-pressed',String(!!selected));button.append(icon(selected?'check':task?((item.overdueAt??candidateOverdueAt(item))<Date.now()&&(item.overdueAt??candidateOverdueAt(item))!=null?'clock-alert':'clock'):'check'));button.disabled=!!item.submitted&&!!item.completed;return button;
}
async function changeRepeat(item,action,extra={}){const result=await api('/api/candidate/repeat',{id:String(item.taskId),candidateId:String(item.id),baseline:item.baseline,revision,action,...extra});revision=result.revision;return result;}
function repeatRuleDialog(item){showDialog('设置重复',(body,owner)=>{
 const form=node('form'),frequency=field(form,'重复频率','','select','repeat-frequency');for(const [value,label]of [['daily','每天'],['weekly','每周'],['monthly','每月'],['yearly','每年']]){const option=node('option','',label);option.value=value;frequency.append(option);}
 const interval=field(form,'间隔',1,'number','repeat-interval');interval.min='1';interval.max='999';interval.required=true;
 const weekdayHost=node('div','repeat-weekdays'),days=[];for(let day=1;day<=7;day++){const label=node('label','checkbox'),box=node('input');box.type='checkbox';box.value=day;label.append(box,document.createTextNode(['一','二','三','四','五','六','日'][day-1]));days.push(box);weekdayHost.append(label);}form.append(weekdayHost);const sync=()=>weekdayHost.hidden=frequency.value!=='weekly';frequency.addEventListener('change',sync);sync();
 const ending=field(form,'结束方式','','select','repeat-ending');for(const [value,label]of [['never','持续重复'],['until','截止日期'],['count','重复次数']]){const option=node('option','',label);option.value=value;ending.append(option);}const extra=node('div');form.append(extra);let until=null,count=null;const endFields=()=>{extra.replaceChildren();until=count=null;if(ending.value==='until')until=field(extra,'截止日期','','date','repeat-until');if(ending.value==='count'){count=field(extra,'次数',10,'number','repeat-count');count.min='1';count.max='100000';count.required=true;}};ending.addEventListener('change',endFields);
 const save=node('button','primary','保存重复');save.type='submit';form.append(save);form.addEventListener('submit',event=>{event.preventDefault();guard(async()=>{if(!form.reportValidity())return;if(until&&!until.value)throw new Error('请选择截止日期');save.disabled=true;try{await changeRepeat(item,'rule',{rule:{frequency:frequency.value,interval:Number(interval.value),weekdays:frequency.value==='weekly'?days.filter(box=>box.checked).map(box=>Number(box.value)):[],until:until?.value||null,count:count?Number(count.value):null}});closeDialog(false,owner);await render({local:true});}finally{save.disabled=false;}});});body.append(form);
});}
async function dialogData(path,owner,retry){try{return await api(path);}catch(error){if(ownsDialog(owner)){$('#dialog-body').replaceChildren(action('重新读取',retry,'quick-action'));toast(error.message,owner);}return null;}}
async function repeatDialog(item,month){
 const from=month||(item.occurrenceAt!=null?phoneDate(item.occurrenceAt):phoneDate()).slice(0,8)+'01',until=monthAfter(from);
 const owner=showDialog('重复日程',body=>body.append(node('p','loading','正在读取…'))),data=await dialogData(`/api/candidate/occurrences?id=${item.taskId}&candidateId=${item.id}&from=${from}&until=${until}`,owner,()=>repeatDialog(item,from));if(!data||!ownsDialog(owner))return;
 revision=data.revision;const series=data.series,body=$('#dialog-body');body.replaceChildren();body.append(node('h3','',series.title),node('p','filter-summary',series.repeatLabel+(series.repeatStoppedAt?' · 已停止':'')));const controls=node('div','range-step');controls.append(action('上个月',()=>repeatDialog(series,monthAfter(from,-1))),node('span','',from.slice(0,7)),action('下个月',()=>repeatDialog(series,until)));body.append(controls);
 for(const occurrence of data.items){const row=node('section','repeat-occurrence');row.append(node('h3','',`${itemDate(occurrence)} · ${executionWhen(occurrence)}`));const buttons=node('div','actions'),complete=completionAction(occurrence,result=>repeatDialog(result.candidate||series,from));if(complete)buttons.append(complete);buttons.append(action('编辑本次日程',()=>candidateEditor({...occurrence,recurrence:null,occurrenceEdit:true})),action('跳过这次',()=>confirmAction('跳过这次',`${itemDate(occurrence)} · ${occurrence.title}`,async()=>{const changed=await changeRepeat(occurrence,'skip',{original:occurrence.occurrenceAt});await render({local:true});await repeatDialog(changed.candidate,from);})));row.append(buttons);body.append(row);}
 if(!data.items.length)empty(body,'本月没有安排');if(session.calendarAllowed&&!series.calendarLinked)body.append(action('写入手机日历',async()=>{const result=await api('/api/calendar/publish-selected',{ids:[String(series.id)],inbox:false,revision});revision=result.revision;await render({local:true});await repeatDialog(series,from);toast(result.summary);},'primary'));if(!series.repeatStoppedAt)body.append(action('停止重复',()=>confirmAction('停止重复','停止之后尚未开始的日程及提醒，保留历史记录',async()=>{await changeRepeat(series,'stop');await render({local:true});}),'danger'));icons();
}
async function openOccurrence(taskId,candidateId,original){
 const from=new Date(original-2*86400000).toISOString().slice(0,10),until=new Date(original+3*86400000).toISOString().slice(0,10);
 const data=await api(`/api/candidate/occurrences?id=${taskId}&candidateId=${candidateId}&from=${from}&until=${until}`);revision=data.revision;
 const item=data.items.find(value=>value.occurrenceAt===original);if(!item)throw new Error('这一次日程已跳过或停止');
 await candidateEditor({...item,recurrence:null,occurrenceEdit:true});
}
function appendHomeworkLinks(parent,item){
 const row=node('div','homework-links');for(const [url,label]of [[item.mobileUrl,'雪灾浙大M'],[item.desktopUrl,'雪灾浙大']]){if(!url)continue;const parsed=new URL(url);if(parsed.protocol!=='https:'||!['courses.zju.edu.cn','mcourses.zju.edu.cn'].includes(parsed.hostname))continue;const link=node('a','link-button',label);link.href=parsed.href;link.target='_blank';link.rel='noopener noreferrer';row.append(link);}if(row.childElementCount)parent.append(row);
}

document.querySelectorAll('[data-page]').forEach(button=>button.addEventListener('click',()=>guard(()=>navigate(button.dataset.page))));$('#capture').addEventListener('click',capture);$('#logout').addEventListener('click',()=>confirmAction('断开连接','断开当前浏览器与手机的连接？',async()=>{await api('/api/logout',{});disconnect();}));$('#refresh').addEventListener('click',()=>guard(()=>refreshSession(true)));
async function refreshSession(manual=false){
 if(inlineEditor?.form.isConnected||dialogEditor?.form.isConnected)return;
 const ticket=sequence,latest=await api('/api/session');if(!session||ticket!==sequence)return;
 const changed=latest.revision!==session.revision||latest.acknowledgementRevision!==session.acknowledgementRevision,finished=homeworkRefreshPending&&!latest.homeworkRefreshing;session=latest;csrf=latest.csrf;applyPalette(latest.palette);updateHomeworkRefresh();
 if(finished){homeworkRefreshPending=false;toast(latest.homeworkStatus||'作业刷新已结束');}
 if(manual||finished||changed||pageOrder.includes(route)||currentTask&&['queued','processing'].includes(currentTask.task.status))await render();
}
let pagePointerCount=0;
document.addEventListener('pointerdown',()=>pagePointerCount++,true);
const releasePagePointer=()=>{pagePointerCount=Math.max(0,pagePointerCount-1);};document.addEventListener('pointerup',releasePagePointer,true);document.addEventListener('pointercancel',releasePagePointer,true);window.addEventListener('blur',()=>pagePointerCount=0);
setInterval(()=>{if(!session||pending||attachmentWork||pagePointerCount||inlineEditor?.form.isConnected||$('#dialog').open||openSelect||document.hidden)return;guard(()=>refreshSession());},10000);

icons();guard(async()=>{const code=scannedPairing;scannedPairing=null;if(code!==null){if(!code){disconnect();throw new Error('配对二维码无效，请重新扫码或手动输入');}try{await api('/api/pair',{code});await connect();}catch(error){disconnect();throw new Error('扫码配对未完成，请重新扫码或手动输入');}return;}try{await connect();}catch(error){if(!csrf){disconnect();if(error.status===401)await requestApproval();else approvalStatus(error.message,true);}else throw error;}});
