/* Local Markdown + Obsidian math. HTML and remote assets remain disabled. */
(() => {
  'use strict';
  const md=window.markdownit({html:false,linkify:false,breaks:false});
  const esc=md.utils.escapeHtml;
  const safeImage=path => /^(?:images\/)?image-\d+\.jpg$/.test(path);
  let allowed=new Set(), formulas=[], generation=0;
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
    return safeImage(src)&&allowed.has(name)?`<a href="action/image/${name}" aria-label="放大课堂截图"><img src="images/${name}" alt="${esc(t.content||'课堂截图')}"></a>`:esc(t.content||'图片引用无效');
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
  new ResizeObserver(resize).observe(document.querySelector('main'));
  window.renderClassroom=async(data,theme={})=>{
    const turn=++generation;formulas=[];allowed=new Set(data.images);
    for(const key of ['text','muted','link','line'])if(theme[key])document.documentElement.style.setProperty('--'+key,theme[key]);
    document.documentElement.style.fontSize=(16*(theme.fontScale||1))+'px';
    const summary=data.summary;
    let html=summary.complete?'':'<p class="draft">未完成</p>';
    if(String(summary.overview||'').trim())html+='<h2>重点概览</h2>'+md.render(summary.overview);
    let taskIndex=0;
    for(const part of summary.parts||[]){
      for(const note of part.notes||[]){
        html+='<h2>'+md.renderInline(note.title||'')+'</h2>'+md.render(note.text||'');
        const inline=new Set([...String(note.text).matchAll(/!\[[^\]\r\n]*\]\((?:images\/)?(image-\d+\.jpg)\)/g)].map(m=>m[1]));
        for(const image of note.images||[]){const name=String(image).replace(/^images\//,'');if(!inline.has(name))html+=md.render(`![课堂截图](images/${name})`);}
      }
      for(const task of part.assignments||[]){
        html+=`<h2>课堂安排</h2><div class="assignment" role="link" tabindex="0" data-task="${taskIndex++}" aria-label="提取课堂安排为日程">${md.render(task.text||'')}</div>`;
      }
    }
    if(summary.corrected_transcript)html+='<details><summary>更正文稿</summary>'+md.render(summary.corrected_transcript)+'</details>';
    const main=document.querySelector('main');main.innerHTML=html;resize();
    main.querySelectorAll('.assignment').forEach(el=>{
      const activate=event=>{if(event.target.closest('a'))return;location.href='action/task/'+el.dataset.task;};
      el.addEventListener('click',activate);el.addEventListener('keydown',event=>{if(event.key==='Enter'||event.key===' '){event.preventDefault();activate(event);}});
    });
    await MathJax.startup.promise;
    for(const element of main.querySelectorAll('[data-formula]')){
      if(turn!==generation)return;
      const formula=formulas[Number(element.dataset.formula)];
      try{const metrics=MathJax.getMetricsFor(element,formula.block);const output=await MathJax.tex2svgPromise(formula.tex,metrics);if(turn!==generation)return;sizeMath(output,element,formula.block);element.replaceChildren(output);}
      catch(error){element.classList.add('formula-error');element.textContent=(formula.block?'$$':'$')+formula.tex+(formula.block?'$$':'$');}
    }
    window.classroomReady=true;resize();window.ChronaSize?.ready();
  };
})();
