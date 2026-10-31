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
  assert(html.includes('[id:99]'),'corrected transcript retains its original source IDs');
  assert.equal(JSON.stringify(summary),snapshot,'structured audit data and source arrays never mutate');
  assert(html.includes('data-task="0"')&&html.includes('aria-label="提取课堂安排为日程"'),'existing action semantics remain intact');
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
  console.log('ClassroomReaderSources: 19 assertions passed (production parser/reader, DOM adapter; no visual acceptance)');
})().catch(error=>{console.error(error);process.exitCode=1;});
