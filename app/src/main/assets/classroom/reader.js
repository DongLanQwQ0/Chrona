/* Local Markdown + Obsidian math. HTML and remote assets remain disabled. */
(() => {
  'use strict';
  const md=window.markdownit({html:false,linkify:false,breaks:false});
  const esc=md.utils.escapeHtml;
  const safeImage=path => /^(?:images\/)?image-\d+\.jpg$/.test(path);
  let allowed=new Set(), formulas=[], generation=0, noteImages=null,lessonKey=null;
  // Source IDs belong to structured audit metadata, not the reading copy.
  // Inline parsing leaves fenced/inline code and math tokens untouched.
  md.inline.ruler.before('link','source_id',(state)=>{
    if(!state.env?.hideSourceIds)return false;
    const match=/^\[id:\s*\d+\](?:\s*[—–-]\s*\[id:\s*\d+\])?/.exec(state.src.slice(state.pos));
    if(!match)return false;
    state.pos+=match[0].length;return true;
  });
  const reading=text=>md.render(text||'',{hideSourceIds:true});
  md.inline.ruler.before('escape','math',(state,silent)=>{
    const start=state.pos;
    if(state.src[start]!=='$')return false;
    const block=state.src[start+1]==='$',marker=block?'$$':'$';
    if(!block && /\s/.test(state.src[start+1]||' '))return false;
    let end=start+marker.length;
    while((end=state.src.indexOf(marker,end))>=0){
      let escapes=0;for(let p=end-1;p>=0&&state.src[p]==='\\';p--)escapes++;
      if(escapes%2===0)break;end+=marker.length;
    }
    if(end<0 || end===start+marker.length || !block&&(/\s/.test(state.src[end-1])||/\d/.test(state.src[end+1]||'')))return false;
    if(!silent){const token=state.push('math','',0);token.content=state.src.slice(start+marker.length,end);token.meta={block};}
    state.pos=end+marker.length;return true;
  });
  md.block.ruler.before('fence','math_block',(state,startLine,endLine,silent)=>{
    const start=state.bMarks[startLine]+state.tShift[startLine];
    if(state.src.slice(start,start+2)!=='$$')return false;
    const end=state.src.indexOf('$$',start+2);if(end<0)return false;
    let next=startLine;while(next<endLine&&state.eMarks[next]<end+2)next++;
    if(next>=endLine||state.src.slice(end+2,state.eMarks[next]).trim())return false;
    if(silent)return true;
    const token=state.push('math','',0);token.content=state.src.slice(start+2,end);token.meta={block:true};token.map=[startLine,next+1];state.line=next+1;return true;
  },{alt:['paragraph','reference','blockquote','list']});
  md.renderer.rules.math=(tokens,index)=>{
    const token=tokens[index],id=formulas.push({tex:token.content,block:token.meta.block})-1;
    return `<${token.meta.block?'div':'span'} class="${token.meta.block?'math-block':'math-inline'}" data-formula="${id}">${esc(token.content)}</${token.meta.block?'div':'span'}>`;
  };
  md.renderer.rules.image=(tokens,index)=>{
    const t=tokens[index],src=t.attrGet('src')||'',name=src.replace(/^images\//,'');
    if(safeImage(src)&&allowed.has(name))noteImages?.add(name);
    const imagePath=lessonKey?`lessons/${lessonKey}/images/${name}`:`images/${name}`,actionPath=lessonKey?`${lessonKey}/${name}`:name;
    return safeImage(src)&&allowed.has(name)?`<a href="action/image/${actionPath}" aria-label="放大课堂截图"><img src="${imagePath}" alt="${esc(t.content||'课堂截图')}"></a>`:esc(t.content||'图片引用无效');
  };
  md.renderer.rules.table_open=()=>'<div class="table-scroll"><table>';
  md.renderer.rules.table_close=()=>'</table></div>';
  // SVG uses TeX x-height units. Resolve them against its own font, not an OEM
  // system font's CSS `ex`, which can be much larger than the surrounding text.
  function sizeMath(output,element,block){
    const xHeight=MathJax.startup.output.font.params.x_height;
    const em=value=>value.replace(/(-?\d*\.?\d+)ex\b/g,(_,number)=>(Number(number)*xHeight)+'em');
    for(const svg of output.querySelectorAll(':scope > svg'))
      for(const name of ['width','height'])if(svg.hasAttribute(name))svg.setAttribute(name,em(svg.getAttribute(name)));
    for(const node of [output,...output.querySelectorAll('[style]')])
      if(node.hasAttribute('style'))node.setAttribute('style',em(node.getAttribute('style')));
    if(!block){
      const svg=output.querySelector(':scope > svg');
      // Overflow makes the wrapper's baseline its bottom edge. Transfer TeX's
      // depth to that wrapper; CSS `middle` would use the OEM font's x-height.
      element.style.verticalAlign=svg.style.verticalAlign||'baseline';
      svg.style.verticalAlign='0';
    }
  }
  function resize(){if(window.ChronaSize)window.ChronaSize.height(Math.ceil(document.querySelector('main').getBoundingClientRect().height));}
  const yieldToReader=()=>new Promise(resolve=>setTimeout(resolve,0));
  new ResizeObserver(resize).observe(document.querySelector('main'));
  window.renderClassroom=async(data,theme={})=>{
    window.classroomRenderStage='markdown';
    const turn=++generation;formulas=[];allowed=new Set(data.images||[]);lessonKey=null;
    for(const key of ['text','muted','link','line'])if(theme[key])document.documentElement.style.setProperty('--'+key,theme[key]);
    document.documentElement.style.fontSize=(16*(theme.fontScale||1))+'px';
    let html='';
    const hasContent=note=>String(note.text||'').trim()||(note.images||[]).length;
    const renderNote=(note,heading)=>{
      const inline=new Set();noteImages=inline;
      let output='<'+heading+'>'+md.renderInline(note.title||'',{hideSourceIds:true})+'</'+heading+'>'+reading(note.text);
      for(const image of note.images||[]){const name=String(image).replace(/^images\//,'');if(!inline.has(name))output+=md.render(`![课堂截图](images/${name})`);}
      noteImages=null;return output;
    };
    if(typeof data.transcript==='string'){
      html=md.render(data.transcript);
    }else if(Array.isArray(data.lessons)){
      for(const lesson of data.lessons){
        if(!/^\d+-\d+$/.test(lesson.key))continue;
        lessonKey=lesson.key;allowed=new Set(lesson.images||[]);
        const label=(lesson.label||'')+(lesson.summary?.complete===false?' · 未完成':'');
        html+=`<h2><a href="action/lesson/${lessonKey}" aria-label="${esc('查看原课堂：'+label)}">${esc(label)}</a></h2>`;
        for(const part of lesson.summary?.parts||[]){
          for(const affair of part.affairs||[])if(hasContent(affair))html+=renderNote(affair,'h3');
          for(const task of part.assignments||[])if(String(task.text||'').trim())html+=reading(task.text);
        }
      }
      lessonKey=null;allowed=new Set();
    }else{
    const summary=data.summary;
    html=summary.complete?'':'<p class="draft">未完成</p>';
    if(String(summary.overview||'').trim())html+='<h2>重点概览</h2>'+reading(summary.overview);
    let taskIndex=0;
    const parts=summary.parts||[],affairsSchema=summary.affairs_schema===1||parts.some(part=>Object.hasOwn(part,'affairs'));
    let learning=false;
    for(const part of parts){
      for(const note of part.notes||[]){
        if(affairsSchema&&!hasContent(note))continue;
        if(affairsSchema&&!learning){html+='<h2>学习笔记</h2>';learning=true;}
        html+=renderNote(note,affairsSchema?'h3':'h2');
      }
      if(affairsSchema)continue;
      for(const task of part.assignments||[]){
        html+=`<h2>课堂安排</h2><div class="assignment" role="link" tabindex="0" data-task="${taskIndex++}" aria-label="提取课堂安排为日程">${reading(task.text)}</div>`;
      }
    }
    if(affairsSchema){
      let affairs=false;
      const heading=()=>{if(!affairs){html+='<h2>课程事务</h2>';affairs=true;}};
      for(const part of parts){
        for(const affair of part.affairs||[]){if(hasContent(affair)){heading();html+=renderNote(affair,'h3');}}
        for(const task of part.assignments||[]){
          const index=taskIndex++;if(!String(task.text||'').trim())continue;
          heading();html+=`<div class="assignment" role="link" tabindex="0" data-task="${index}" aria-label="提取课堂安排为日程">${reading(task.text)}</div>`;
        }
      }
    }
    }
    const main=document.querySelector('main');main.innerHTML=html;resize();
    main.querySelectorAll('.assignment').forEach(el=>{
      const activate=event=>{if(event.target.closest('a'))return;location.href='action/task/'+el.dataset.task;};
      el.addEventListener('click',activate);el.addEventListener('keydown',event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();activate(event);}});
    });
    window.classroomRenderStage='math-startup';
    await MathJax.startup.promise;
    window.classroomRenderStage='formulas';
    const entries=[...main.querySelectorAll('[data-formula]')];
    const renderMath=async elements=>{
      let count=0;
      for(const element of elements){
        if(turn!==generation)return;
        if(element.dataset.typeset)continue;
        element.dataset.typeset='pending';
        const formula=formulas[Number(element.dataset.formula)];
        try{const metrics=MathJax.getMetricsFor(element,formula.block);const output=await MathJax.tex2svgPromise(formula.tex,metrics);if(turn!==generation)return;sizeMath(output,element,formula.block);element.replaceChildren(output);}
        catch(error){element.classList.add('formula-error');element.textContent=(formula.block?'$$':'$')+formula.tex+(formula.block?'$$':'$');}
        element.dataset.typeset='done';
        if(++count%16===0){resize();await yieldToReader();}
      }
      resize();
    };
    // The native disclosure lazily creates a renderer for the transcript.
    // Both modes present their first screen before typesetting offscreen math.
    const firstScreen=entries.filter(element=>element.getBoundingClientRect().top<(theme.viewportHeight||window.screen.height));
    await renderMath(firstScreen);
    if(turn!==generation)return;
    window.classroomRenderStage='ready';window.classroomReady=true;resize();window.ChronaSize?.ready();
    await renderMath(entries);
  };
})();
