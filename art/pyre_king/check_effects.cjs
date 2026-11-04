// Exercise the shared preview/export math with in-memory candidate data.
const fs=require('fs'),vm=require('vm'),path=require('path'),assert=require('assert');
const ctx={};vm.createContext(ctx);
for(const file of ['core.js','effects.js'])vm.runInContext(fs.readFileSync(path.join(__dirname,'workshop',file),'utf8'),ctx);
const {king,sword,manifest}=JSON.parse(fs.readFileSync(0,'utf8'));
const models=[king,sword].map(ctx.PyreCore.bakeModel),{scene,posed}=ctx.PyreEffects;
const close=(a,b)=>assert(Math.abs(a-b)<1e-5,`${a} != ${b}`);
assert.equal(manifest.blade_fire.sockets.length,17);
close(manifest.blade_fire.sockets[0][1],16);close(manifest.blade_fire.sockets.at(-1)[1],72);
for(const model of models){
 assert.equal(model.bones.find(b=>b.name==='sword_fire').cubes.length,0);
 for(const planted of [false,true])for(const time of [0,.8,1.1,1.5,1.8,2,2.4,3.6,4]){
  const sample=scene(model,manifest,time,true,planted),points=sample.sockets;
  close(Math.hypot(...points.at(-1).map((v,i)=>v-points[0][i])),3.5);
  if(time===1.8)close(points[0][1],points.at(-1)[1]);
  for(const face of sample.flames){
   assert(face.alpha>0&&face.alpha<=1);
   assert(face.pts.flat().every(Number.isFinite));
   // All edges belong to one world axis; no rotated fire may enter the pass.
   for(let i=0;i<4;i++)assert.equal(face.pts[i].filter((v,k)=>Math.abs(v-face.pts[(i+1)%4][k])>1e-8).length,1);
  }
 }
 assert(scene(model,manifest,1.5,true).flames.some(f=>f.trail));
 assert(!scene(model,manifest,0,true).flames.some(f=>f.trail));
 assert(!scene(model,manifest,2.19,true).flames.some(f=>f.trail));
 assert(!scene(model,manifest,1.5,false).flames.some(f=>f.trail));
 const a=scene(model,manifest,.3),b=scene(model,manifest,.6);
 assert.equal(a.flames.length,(17+manifest.blade_fire.peak_sockets.length)*3,'a flame at every socket plus the peaks');
 assert(a.flames.every(f=>f.alpha===1),'live cores must not be globally translucent');
 const heights=a.flames.filter((_,i)=>i%3===0).map(f=>Math.max(...f.pts.map(p=>p[1]))-Math.min(...f.pts.map(p=>p[1])));
 assert(Math.max(...heights)/Math.min(...heights)>1.6,'uneven flame heights');
 assert.notDeepEqual(a.flames,b.flames);
 assert.deepEqual(a.sockets,b.sockets);
}
// Held and thrown versions have identical local weapon geometry/UVs.
const held=king['minecraft:geometry'][0].bones,thrown=sword['minecraft:geometry'][0].bones;
const pivot=manifest.blade_fire.king_pivot;
for(const b of thrown){const original=held.find(x=>x.name===b.name);assert.equal(original.cubes.length,b.cubes.length);
 b.cubes.forEach((c,i)=>{assert.deepEqual(c.size,original.cubes[i].size);assert.deepEqual(c.uv,original.cubes[i].uv);c.origin.forEach((v,k)=>close(v,original.cubes[i].origin[k]-pivot[k]));});}
process.stdout.write('World-up fire / swept sockets / trail decay / synchronized sword: OK\n');
