/* Compare production web release motion with production Android DockSpring, without a browser. */
const fs=require('fs'),path=require('path'),vm=require('vm'),cp=require('child_process'),assert=require('assert');
const root=path.resolve(__dirname,'..'),out=path.join(root,'build/lan-dock-repair/physics');fs.mkdirSync(out,{recursive:true});
const source=fs.readFileSync(path.join(root,'app/src/main/assets/lan/app.js'),'utf8');
const helpers=source.slice(source.indexOf('const navigationTracks='),source.indexOf('function createNavigationTrack('));
const web=vm.runInNewContext(helpers+';({coastNavigationStep,settleNavigationStep,navigationCoastComplete})');
const java=`package com.donglan.chrona;
public class DockWebGolden {
 public static void main(String[] args){
  for(int scenario=0;scenario<4;scenario++){
   DockSpring s=new DockSpring();s.position=scenario==0?.7f:scenario==1?1.3f:scenario==2?2.5f:.7f;
   s.velocity=scenario==0?12f:scenario==1?-12f:scenario==2?4f:4f;s.target=2f;
   if(scenario<3)s.coast(s.velocity,2f);
   for(int i=0;i<240;i++){
    s.step(1f/120f);System.out.println(scenario+","+i+","+s.position+","+s.velocity+","+s.coasting);
    if(scenario<3&&!s.coasting)break;
   }
  }
 }
}`;
const harness=path.join(out,'DockWebGolden.java');fs.writeFileSync(harness,java);
const bin=path.join(process.env.JAVA_HOME||'D:/Minecraft/java21','bin');
cp.execFileSync(path.join(bin,'javac.exe'),['-encoding','UTF-8','-d',out,path.join(root,'app/src/main/java/com/donglan/chrona/DockSpring.java'),harness]);
const rows=cp.execFileSync(path.join(bin,'java.exe'),['-cp',out,'com.donglan.chrona.DockWebGolden'],{encoding:'utf8'}).trim().split(/\r?\n/);
let compared=0,landings=0;
for(const slot of [48,100,300]){
const states=[{p:.7*slot,v:12*slot},{p:1.3*slot,v:-12*slot},{p:2.5*slot,v:4*slot},{p:.7*slot,v:4*slot,target:2*slot}];
for(const row of rows){const [scenario,frame,p,v,coasting]=row.split(','),s=states[+scenario];
 if(+scenario<3){
  web.coastNavigationStep(s,0,2*slot,1/120,slot);
  const stopped=web.navigationCoastComplete(s,0,2*slot,slot);
  assert.equal(stopped,coasting==='false',`Coast end frame differs at slot ${slot}: ${row}`);
  if(stopped){assert.equal(Math.round(Math.max(0,Math.min(2,s.p/slot))),Math.round(Number(p)));landings++;}
 }else web.settleNavigationStep(s,1/120);
 const nativeSnapped=+scenario===3&&Number(p)===2&&Number(v)===0;
 assert(Math.abs(s.p/slot-Number(p))<.0003&&Math.abs(s.v/slot-Number(v))<(nativeSnapped?.001:.0003),`Android/web diverged: ${row} vs ${JSON.stringify(s)}`);compared++;
}
}
console.log(`PASS ${compared} production Android/web frames; ${landings} matching coast end frames and landings across 48/100/300px slots`);
