/* Real bundled Markdown parser + production reader, DOM adapter only; no device/AI. */
'use strict';
const fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'..'),assets=path.join(root,'app/src/main/assets/classroom');
const main={innerHTML:'',querySelectorAll:()=>[],getBoundingClientRect:()=>({height:300})};
const document={querySelector:()=>main,documentElement:{style:{setProperty(){}}}};
const window={markdownit:require(path.join(assets,'markdown-it-14.1.0.min.js')),screen:{height:900}};
const context={window,document,ResizeObserver:class{observe(){}},MathJax:{startup:{promise:Promise.resolve()}},setTimeout};
vm.runInNewContext(fs.readFileSync(path.join(assets,'reader.js'),'utf8'),context,{filename:'reader.js'});
(async()=>{
  const summary={complete:true,overview:'[id:1] 概览',parts:[{notes:[{title:'[id:1]—[id:2] 条件',text:'[id:1]—[id:2] 正文。\n\n`[id:3]`\n\n```c\nputs("[id:4]");\n```\n\n$[id:5]$\n\n\\[id:6] 原样示例',sources:[1,2],images:[]}],assignments:[{text:'[id:7] 交报告',sources:[7]}]}],corrected_transcript:'[id:99] 原文保留'};
  const snapshot=JSON.stringify(summary);
  await window.renderClassroom({summary,images:[]});
  const html=main.innerHTML;
  assert(!html.includes('[id:1]')&&!html.includes('[id:2]'),'reader hides source identifiers and their range separator');
  assert(!html.includes('[id:7]'),'assignment uses the same reading rule');
  assert(html.includes('<code>[id:3]</code>')&&html.includes('puts(&quot;[id:4]&quot;)'),'inline and fenced source-code literals stay exact');
  assert(html.includes('[id:5]')&&html.includes('[id:6]'),'math and escaped literal identifiers remain intact');
  assert(!html.includes('[id:99]')&&!html.includes('原文保留'),'main notes do not render the lazily opened corrected transcript');
  assert(!/<(?:details|summary)(?:\s|>)/i.test(html),'main reader never inserts a system disclosure');
  assert.equal(JSON.stringify(summary),snapshot,'structured audit data and source arrays never mutate');
  assert(html.includes('data-task="0"')&&html.includes('aria-label="提取课堂安排为日程"'),'existing action semantics remain intact');
  const transcript={transcript:summary.corrected_transcript+'\n\n`[id:3]`\n\n```c\nputs("[id:4]");\n```\n\n$[id:5]$\n\n\\[id:6] 原样示例\n\n![允许](images/image-61.jpg)\n\n![越界](images/image-62.jpg)',images:['image-61.jpg']};
  const transcriptBefore=JSON.stringify(transcript);
  await window.renderClassroom(transcript);
  const transcriptHtml=main.innerHTML;
  assert(transcriptHtml.includes('[id:99]')&&transcriptHtml.includes('原文保留'),'standalone transcript preserves raw source IDs without summary data');
  assert(transcriptHtml.includes('<code>[id:3]</code>')&&transcriptHtml.includes('puts(&quot;[id:4]&quot;)'),'transcript inline and fenced code literals stay exact');
  assert(transcriptHtml.includes('[id:5]')&&transcriptHtml.includes('[id:6]'),'transcript math and escaped identifiers remain intact');
  assert(!/<(?:details|summary)(?:\s|>)/i.test(transcriptHtml)&&!transcriptHtml.includes('data-task='),'native disclosure transcript has neither system details nor extraction actions');
  assert.equal((transcriptHtml.match(/<img /g)||[]).length,1,'standalone transcript uses the original image allowlist');
  assert.equal(JSON.stringify(transcript),transcriptBefore,'transcript mode leaves exact input unchanged');
  await window.renderClassroom({transcript:'',images:[]});
  assert.equal(main.innerHTML,'','empty transcript selects its explicit mode without creating summary headings');
  const cases=[
    ['![caption](images/image-61.jpg)',1],
    ['![caption][ref]\n\n[ref]: images/image-61.jpg',1],
    ['`![literal](images/image-61.jpg)`',1],
    ['```text\n![literal](images/image-61.jpg)\n```',1],
    ['> ```text\n> ![literal](images/image-61.jpg)',1],
    ['Paragraph\n    ![caption](images/image-61.jpg)',1]
  ];
  for(const [text,count] of cases){
    const data={complete:true,overview:'',parts:[{notes:[{title:'Proof',text,images:['image-61.jpg','images/image-61.jpg']}],assignments:[]}]};
    const before=JSON.stringify(data);await window.renderClassroom({summary:data,images:['image-61.jpg']});
    assert.equal((main.innerHTML.match(/<img /g)||[]).length,count,'actual image tokens control fallback without duplicate or missing picture: '+text);
    assert.equal(JSON.stringify(data),before,'reader never mutates saved source for fallback');
  }
  const note=(title,text,images=[])=>({title,text,images,sources:[0]});
  const grouped={complete:true,affairs_schema:1,overview:'统一概览',parts:[
    {notes:[note('知识一','概念一')],affairs:[note('[id:0] 评分规则','[id:0] 平时分30%。\n\n![图][slide]\n\n[slide]: images/image-61.jpg',['image-61.jpg','images/image-61.jpg'])],assignments:[{text:'第一项行动'}]},
    {notes:[note('知识二','概念二')],affairs:[note('考核范围','第三章不考。')],assignments:[{text:'第二项行动'}]}
  ]};
  const groupedBefore=JSON.stringify(grouped);
  await window.renderClassroom({summary:grouped,images:['image-61.jpg']});
  const groupedHtml=main.innerHTML;
  assert(groupedHtml.indexOf('<h2>重点概览</h2>')<groupedHtml.indexOf('<h2>学习笔记</h2>')&&groupedHtml.indexOf('概念二')<groupedHtml.indexOf('<h2>课程事务</h2>'),'all knowledge precedes consolidated affairs');
  assert.equal((groupedHtml.match(/<h2>课程事务<\/h2>/g)||[]).length,1,'one affairs heading');
  assert(!groupedHtml.includes('课堂安排</h2>')&&/<h3>\s*评分规则<\/h3>/.test(groupedHtml),'new affairs keep existing subsection styles and hide source markers');
  assert.deepEqual([...groupedHtml.matchAll(/data-task="(\d+)"/g)].map(m=>m[1]),['0','1'],'existing assignment indices remain across parts');
  assert.equal((groupedHtml.match(/class="assignment"/g)||[]).length,2,'non-action affairs never become extraction links');
  assert.equal((groupedHtml.match(/<img /g)||[]).length,1,'affairs use actual CommonMark token fallback dedupe');
  assert.equal(JSON.stringify(grouped),groupedBefore,'grouped reader leaves source data unchanged');
  const only=structuredClone(grouped);only.overview='';for(const part of only.parts)part.notes=[];
  await window.renderClassroom({summary:only,images:['image-61.jpg']});
  assert(!main.innerHTML.includes('学习笔记')&&!main.innerHTML.includes('重点概览')&&main.innerHTML.includes('课程事务'),'affairs-only content has no empty learning shells');
  const noMarker=structuredClone(only);delete noMarker.affairs_schema;
  await window.renderClassroom({summary:noMarker,images:['image-61.jpg']});
  assert(main.innerHTML.includes('课程事务'),'explicit optional affairs field also selects grouped schema');
  const skipped=structuredClone(only);skipped.parts[0].assignments.unshift({text:'  '});
  await window.renderClassroom({summary:skipped,images:['image-61.jpg']});
  assert.deepEqual([...main.innerHTML.matchAll(/data-task="(\d+)"/g)].map(m=>m[1]),['1','2'],'skipping blank content never shifts original extraction indices');
  const empty={complete:true,affairs_schema:1,overview:'',parts:[{notes:[],affairs:[],assignments:[]}]};
  await window.renderClassroom({summary:empty,images:[]});
  assert.equal(main.innerHTML,'','empty grouped document has no headings');
  const aggregate={lessons:[
    {key:'41-51',label:'2026-09-01 · 第1节',summary:grouped,images:['image-61.jpg']},
    {key:'41-52',label:'2026-09-02 · <第2节>',summary:{complete:false,parts:[{notes:[note('不可出现','知识隐藏')],affairs:[note('第二课规则','![同编号](image-61.jpg)\n\n![越界](image-62.jpg)\n\n![远程](https://example.com/image-61.jpg)',['image-61.jpg'])],assignments:[{text:'第三项行动'}]}]},images:['image-61.jpg']}
  ]};
  const aggregateBefore=JSON.stringify(aggregate);
  await window.renderClassroom(aggregate);
  const aggregated=main.innerHTML;
  assert(aggregated.includes('href="action/lesson/41-51" aria-label="查看原课堂：2026-09-01 · 第1节"')&&aggregated.includes('2026-09-02 · &lt;第2节&gt;'),'each lesson has escaped original-lesson navigation');
  assert(aggregated.includes('aria-label="查看原课堂：2026-09-02 · &lt;第2节&gt; · 未完成"')&&!aggregated.includes('aria-label="查看原课堂"'),'lesson source accessible names distinguish dates/periods and preserve escaped draft status');
  assert(aggregated.includes('2026-09-02 · &lt;第2节&gt; · 未完成</a>')&&aggregated.includes('2026-09-01 · 第1节</a>')&&(aggregated.match(/ · 未完成<\/a>/g)||[]).length===1,'only draft lesson source labels carry unfinished status');
  assert(!aggregated.includes('重点概览')&&!aggregated.includes('学习笔记')&&!aggregated.includes('课程事务')&&!aggregated.includes('不可出现')&&!aggregated.includes('统一概览'),'aggregate contains only original affairs and actions, no extra section labels');
  assert(!aggregated.includes('data-task=')&&!aggregated.includes('class="assignment"'),'aggregate action text is read-only');
  assert(aggregated.includes('src="lessons/41-51/images/image-61.jpg"')&&aggregated.includes('src="lessons/41-52/images/image-61.jpg"'),'same image names scoped per lesson');
  assert(aggregated.includes('href="action/image/41-51/image-61.jpg"')&&aggregated.includes('href="action/image/41-52/image-61.jpg"'),'image actions preserve original lesson identity');
  assert.equal((aggregated.match(/<img /g)||[]).length,2,'scoped CommonMark tokens dedupe and reject unallowed or remote images');
  assert(aggregated.indexOf('第一项行动')<aggregated.indexOf('第二项行动')&&aggregated.indexOf('第二项行动')<aggregated.indexOf('第三项行动'),'aggregate preserves original action order without merging');
  assert.equal(JSON.stringify(aggregate),aggregateBefore,'aggregate never mutates input');
  await window.renderClassroom({summary:grouped,images:['image-61.jpg']});
  assert(main.innerHTML.includes('src="images/image-61.jpg"')&&!main.innerHTML.includes('src="lessons/'),'ordinary rendering restores unscoped image paths after aggregation');
  console.log('ClassroomReaderSources: 49 assertions passed (production parser/reader, DOM adapter; no visual acceptance)');
})().catch(error=>{console.error(error);process.exitCode=1;});
