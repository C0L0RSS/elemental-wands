// Exercise the shared preview/export math with in-memory candidate data.
const fs=require('fs'),vm=require('vm'),path=require('path'),assert=require('assert');
const ctx={};vm.createContext(ctx);
for(const file of ['core.js','effects.js'])vm.runInContext(fs.readFileSync(path.join(__dirname,'workshop',file),'utf8'),ctx);
const {king,sword,manifest}=JSON.parse(fs.readFileSync(0,'utf8'));
const models=[king,sword].map(ctx.PyreCore.bakeModel),{scene,posed}=ctx.PyreEffects;
const close=(a,b)=>assert(Math.abs(a-b)<1e-5,`${a} != ${b}`);
const sub=(a,b)=>a.map((v,i)=>v-b[i]),dot=(a,b)=>a[0]*b[0]+a[1]*b[1]+a[2]*b[2],len=a=>Math.hypot(...a);
const bf=manifest.blade_fire,sw=bf.swing,span=sw.history_samples*sw.history_step_seconds;
assert.equal(bf.sockets.length,17);
close(bf.sockets[0][1],16);close(bf.sockets.at(-1)[1],72);
const flameCount=(17+bf.peak_sockets.length)*3;
// Synthetic motions, independent of the authored clips (the sword bone alone moves, then holds).
// whip: 120 degrees over the top in 0.15 s (~50 blocks/s at the tip); sway: 40 degrees in 0.4 s
// (~6 blocks/s); rise and rise_slow lift the blade back up (fast and moderate, the moves that
// used to hang the fire below the blade); spike: 90 degrees in 0.05 s (~110 blocks/s), then a
// slow drift, so the fastest moment in the window is far faster than the present; reverse: out
// and straight back, so the path doubles back inside a flame's life; yank: the sword is pulled
// 4 blocks straight up in 0.06 s, leaving its wake below it; jump: the
// sword teleports 8 blocks between two instants; spin: a whole turn in 0.05 s (a parent switch).
const rot=keys=>({animation_length:1,bones:{sword:{rotation:keys}}});
const whip=rot({'0':[0,0,0],'0.15':[-120,0,0]}),sway=rot({'0':[0,0,0],'0.4':[-40,0,0]});
const rise=rot({'0':[120,0,0],'0.15':[0,0,0]}),riseSlow=rot({'0':[60,0,0],'0.4':[0,0,0]});
const spike=rot({'0':[0,0,0],'0.05':[-90,0,0],'0.45':[-110,0,0]}),spin=rot({'0':[0,0,0],'0.2':[0,0,0],'0.25':[360,0,0]});
const jump={animation_length:1,bones:{sword:{position:{'0':[0,0,0],'0.2':{pre:[0,0,0],post:[0,0,-128]}}}}};
const reverse=rot({'0':[0,0,0],'0.08':[-60,0,0],'0.16':[0,0,0]});
const yank={animation_length:1,bones:{sword:{position:{'0':[0,-64,0],'0.06':[0,0,0]}}}};
const swings={whip,sway,rise,riseSlow,spike,reverse,yank};
const times=(a,b,dt)=>{const out=[];for(let t=a;t<=b+1e-9;t+=dt)out.push(+t.toFixed(4));return out;};
// Every flame face is a finite, textured world-fire quad. Opacity is per face ('alpha', in
// (0,1]) or per vertex ('alphas', four values in [0,1] whose largest is 'alpha'); 'blend' is
// absent (normal blending) or 'add' (drawn after the rest, additively).
function wellFormed(sample,label){
 for(const f of sample.flames){
  assert(f.alpha>0&&f.alpha<=1,`${label}: alpha ${f.alpha}`);
  assert(f.fire===true&&f.bone==='world_fire'&&typeof f.trail==='boolean',label);
  assert(f.pts.length===4&&f.uvs.length===4,label);
  assert([...f.pts.flat(),...f.n,...f.uvs.flat()].every(Number.isFinite),`${label}: non-finite value`);
  assert(f.uvs.flat().every(v=>v>=-1e-9&&v<=1+1e-9),`${label}: uv outside the atlas`);
  assert(f.blend===undefined||f.blend==='add',`${label}: unknown blend ${f.blend}`);
  if(f.alphas!==undefined){
   assert(Array.isArray(f.alphas)&&f.alphas.length===4&&f.alphas.every(a=>Number.isFinite(a)&&a>=0&&a<=1),`${label}: bad alphas`);
   assert(Math.abs(Math.max(...f.alphas)-f.alpha)<1e-9,`${label}: alpha is not the largest of alphas`);
  }
  // The wake is the only translucent, per-vertex or additive fire; cores are opaque quads.
  if(!f.trail)assert(f.alpha===1&&f.alphas===undefined&&f.blend===undefined,`${label}: a core flame is translucent`);
  else assert(f.alphas!==undefined,`${label}: a wake face without per-vertex fading`);
 }
 for(const g of sample.fire){
  assert([...g.root,...g.tip,...g.velocity,g.speed,g.now,g.lean,g.stream,g.height,...g.spine.flat()].every(Number.isFinite),label);
  assert(g.height>0&&g.lean>=0&&g.lean<=1&&g.stream>=0&&g.stream<=1,label);
 }
}
// At rest every flame stands upright as one quad per sheet: each edge runs along one world
// axis, nothing streams, and every face is opaque where its texture is.
function atRest(sample,label){
 wellFormed(sample,label);
 assert.equal(sample.flames.length,flameCount,`${label}: a flame at every socket plus the peaks`);
 assert(!sample.flames.some(f=>f.trail),`${label}: no stream at rest`);
 assert(sample.flames.every(f=>f.alpha===1&&f.blend===undefined),`${label}: live cores must not be globally translucent`);
 for(const face of sample.flames)for(let i=0;i<4;i++)
  assert.equal(face.pts[i].filter((v,k)=>Math.abs(v-face.pts[(i+1)%4][k])>1e-8).length,1,`${label}: a flame leans at rest`);
 for(const g of sample.fire){const d=sub(g.tip,g.root);assert(d[1]>0&&Math.hypot(d[0],d[2])<1e-8,`${label}: flame not upright`);close(d[1],g.height);}
}
const turnLimit=Math.PI/2;
// Whatever the motion: a moving flame is never shorter than the same flame standing still, never
// points down or hangs below its root, and never folds back on itself (no joint turns past 90
// degrees); no visible part of the wake hangs more than 1.5 blocks below the blade.
function sound(sample,label){
 wellFormed(sample,label);
 const floorY=Math.min(...sample.sockets.map(p=>p[1]))-1.5;
 for(const f of sample.flames)if(f.trail)f.pts.forEach((p,k)=>assert(p[1]>=floorY||f.alphas[k]===0,`${label}: the wake hangs below the blade`));
 for(const g of sample.fire){
  const d=sub(g.tip,g.root),l=len(d);
  assert(l>=g.height*(1-1e-6),`${label}: flame ${g.socket} is ${(l/g.height).toFixed(2)}x its still length at ${g.now.toFixed(1)} blocks/s`);
  assert(d[1]/l>=-.2,`${label}: flame ${g.socket} points down (up-cosine ${(d[1]/l).toFixed(2)})`);
  for(const p of g.spine)assert(p[1]>=g.root[1]-.1,`${label}: flame ${g.socket} hangs ${(g.root[1]-p[1]).toFixed(2)} blocks below its root`);
  for(let k=1;k+1<g.spine.length;k++){const a=sub(g.spine[k],g.spine[k-1]),b=sub(g.spine[k+1],g.spine[k]);
   if(len(a)>1e-6&&len(b)>1e-6)assert(Math.acos(Math.max(-1,Math.min(1,dot(a,b)/len(a)/len(b))))<=turnLimit,`${label}: flame ${g.socket} folds`);}
 }
}
for(const model of models){
 for(const planted of [false,true])for(const time of [0,.8,1.1,1.5,1.8,2,2.4,3.6,4]){
  const sample=scene(model,manifest,time,true,planted),points=sample.sockets;
  close(Math.hypot(...points.at(-1).map((v,i)=>v-points[0][i])),3.5);
  if(time===1.8)close(points[0][1],points.at(-1)[1]);
  sound(sample,`sweep ${time}`);
 }
 // Still scenes: the static guard, the planted sword, the sweep before it starts and once it
 // has held level for longer than the history window.
 for(const [label,sample] of [['guard .3',scene(model,manifest,.3)],['guard .6',scene(model,manifest,.6)],
  ['planted',scene(model,manifest,.65,false,true)],['sweep start',scene(model,manifest,0,true)],
  ['sweep held',scene(model,manifest,1.8+span+.05,true)],['whip start',scene(model,manifest,0,false,false,whip)],
  ['whip settled',scene(model,manifest,.15+span+.01,false,false,whip)]])atRest(sample,label);
 // No travel is the same as standing still: opts are optional and default to [0,0,0].
 assert.deepEqual(JSON.parse(JSON.stringify(scene(model,manifest,.3,false,false,null,{travel:[0,0,0]}))),JSON.parse(JSON.stringify(scene(model,manifest,.3))));
 // Slow moves stay calm: the slow test sweep and sway lean the fire but never draw a wake.
 for(const time of [1,1.3,1.6])assert(!scene(model,manifest,time,true).flames.some(f=>f.trail),'the slow test sweep draws a wake');
 for(const time of [.1,.2,.3])assert(!scene(model,manifest,time,false,false,sway).flames.some(f=>f.trail),'a slow sway draws a wake');
 assert(!scene(model,manifest,1.5,false).flames.some(f=>f.trail),'a still blade never streams');
 // A fast blade streams a wake: a normally blended tint and an additive glow over the same quad,
 // fading in from nothing at the blade, so the sheet never starts on a hard edge.
 const fast=scene(model,manifest,.09,false,false,whip);wellFormed(fast,'whip');
 const wake=fast.flames.filter(f=>f.trail),glow=wake.filter(f=>f.blend==='add');
 assert(glow.length>0&&glow.length*2===wake.length,'a fast blade streams a tinted, glowing wake');
 const roots=posed(model,manifest,.09,false,false,whip).roots;let atBlade=0;
 for(const f of wake)f.pts.forEach((p,k)=>{if(roots.some(r=>len(sub(p,r))<1e-6)){atBlade++;assert.equal(f.alphas[k],0,'the wake starts on a hard edge at the blade');}});
 assert(atBlade>0,'the wake starts at the blade');
 // Fast flames lean away from their socket's travel.
 const angle=g=>{const d=sub(g.tip,g.root);return Math.acos(Math.max(-1,Math.min(1,d[1]/len(d))));};
 for(const g of fast.fire.filter(g=>g.stream>.5)){
  const d=sub(g.tip,g.root);
  assert(dot(d,g.velocity)<-.3*len(d)*len(g.velocity),`flame ${g.socket} does not trail its travel`);
  // Swept sideways, a fast flame lies well over instead of standing up.
  if(Math.abs(g.velocity[1])<.5*len(g.velocity))assert(angle(g)>.5,`flame ${g.socket} still stands upright at ${g.speed.toFixed(1)} blocks/s`);
 }
 // In a slower sway the hilt barely moves and stays nearly upright while the tip streams.
 const swaying=scene(model,manifest,.3,false,false,sway),licks=swaying.fire.filter(g=>!g.tall);wellFormed(swaying,'sway');
 const behind=g=>-dot(sub(g.tip,g.root),g.velocity)/len(g.velocity);
 assert(licks[16].speed>licks[2].speed*2&&behind(licks[16])>behind(licks[2])+.1&&angle(licks[16])>angle(licks[2])+.2,'the outer blade streams most');
 const rest=scene(model,manifest,0,false,false,whip).fire;
 assert(fast.fire.filter(g=>g.stream>.9).every(g=>len(sub(g.tip,g.root))>len(sub(rest[fast.fire.indexOf(g)].tip,rest[fast.fire.indexOf(g)].root))),'fast flames stretch');
 // Every swing, fast or slow, rising or falling, and after a speed spike: no short, downward,
 // hanging or folded flames.
 for(const [name,clip] of Object.entries(swings))for(const time of times(0,.5,.01))sound(scene(model,manifest,time,false,false,clip),`${name} ${time}`);
 // Just after a spike the window still holds a far faster moment than now; the flames span the
 // motion they just made instead of shrinking (the bug that made fast fire shorter than still).
 for(const time of times(.06,.2,.01))for(const g of scene(model,manifest,time,false,false,spike).fire)
  assert(len(sub(g.tip,g.root))>=g.height*(1-1e-6),`spike ${time}: flame ${g.socket} shrinks`);
 // A jump is not a swing: after a teleport, or a whole turn in a twentieth of a second, the fire
 // stands as if it had always been there; it never traces the jump.
 for(const time of times(.201,.2+span,.01))atRest(scene(model,manifest,time,false,false,jump),`after a teleport ${time}`);
 for(const time of times(.255,.45,.01))atRest(scene(model,manifest,time,false,false,spin),`after a spin ${time}`);
 // The body's own travel (a walk or run played in place) carries the fire back through still
 // air: the flames lean away from the way he walks, calmly, without a wake.
 for(const speed of [3,5]){const walking=scene(model,manifest,.3,false,false,null,{travel:[0,0,-speed]});wellFormed(walking,`travel ${speed}`);
  assert(!walking.flames.some(f=>f.trail),`walking at ${speed} blocks/s draws a wake`);
  for(const g of walking.fire){const d=sub(g.tip,g.root);assert(d[2]>0&&d[1]/len(d)>.5&&g.stream<.15,`travel ${speed}: flame ${g.socket} does not lean calmly back`);}}
 // No popping: flame shapes change continuously as swings start, stop and settle.
 for(const clip of [whip,spike,rise]){
  let previous=null;
  for(let time=0;time<=.32;time+=.002){
   const now=scene(model,manifest,time,false,false,clip);wellFormed(now,`popping ${time}`);
   // A flame reshapes no faster than the motion driving it (its root now, or the fastest it has
   // moved within the window while its trail catches up), plus flicker: no jumps.
   if(previous)now.fire.forEach((g,i)=>{const p=previous.fire[i],change=len(sub(sub(g.tip,g.root),sub(p.tip,p.root)));
    const drive=Math.max(len(sub(g.root,p.root)),Math.max(g.speed,p.speed)*.002);
    assert(change<.03+1.5*drive,`flame ${g.socket} jumps ${change.toFixed(3)} blocks at ${time.toFixed(3)} s`);});
   previous=now;
  }
 }
 // The same moment always builds the same fire.
 assert.deepEqual(JSON.parse(JSON.stringify(scene(model,manifest,.07,false,false,whip))),JSON.parse(JSON.stringify(scene(model,manifest,.07,false,false,whip))));
 const a=scene(model,manifest,.3),b=scene(model,manifest,.6);
 const heights=a.fire.map(g=>g.tip[1]-g.root[1]);
 assert(Math.max(...heights)/Math.min(...heights)>1.6,'uneven flame heights');
 assert.notDeepEqual(a.flames,b.flames);
 assert.deepEqual(a.sockets,b.sockets);
}
// The test sweep keeps both fists on the grip with unstretched, rigid forearms.
{const model=models[0],idx=n=>model.bones.findIndex(b=>b.name===n);
 for(const time of [.8,1.3,1.8,2.6,3.4])for(const side of ['right','left']){
  const {matrices}=posed(model,manifest,time,true),fi=idx(side+'_forearm'),hi=idx(side+'_hand'),h=model.bones[hi].pivot;
  const gap=Math.hypot(...[0,1,2].map(k=>ctx.PyreCore.M.apply(matrices[fi],h)[k]-ctx.PyreCore.M.apply(matrices[hi],h)[k]));
  assert(gap<.01,`${side} fist leaves the grip by ${gap} blocks at ${time} s`);
  for(const c of [0,4,8])close(Math.hypot(matrices[fi][c],matrices[fi][c+1],matrices[fi][c+2]),1);
 }}
// Every flame roots on the blade's uppermost edge, not across its face.
{const R=posed(models[0],manifest,.65).roots;assert.equal(R.length,17);
 const S=posed(models[0],manifest,.65).sockets;assert(R.every((r,i)=>r[1]>=S[i][1]-1e-9),'roots sit on the upper edge');}
// Held and thrown versions have identical local weapon geometry/UVs.
const held=king['minecraft:geometry'][0].bones,thrown=sword['minecraft:geometry'][0].bones;
const pivot=manifest.blade_fire.king_pivot;
for(const b of thrown){const original=held.find(x=>x.name===b.name);assert.equal(original.cubes.length,b.cubes.length);
 b.cubes.forEach((c,i)=>{assert.deepEqual(c.size,original.cubes[i].size);assert.deepEqual(c.uv,original.cubes[i].uv);c.origin.forEach((v,k)=>close(v,original.cubes[i].origin[k]-pivot[k]));});}
process.stdout.write('Upright fire at rest / swings stream, never shrink, droop or fold / jumps are not traced / travel leans calmly / synchronized sword: OK\n');
