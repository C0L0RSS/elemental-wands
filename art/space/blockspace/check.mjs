// Headless geometry check: node check.mjs [sceneId ...]
// Samples every scene across its loop from each camera; fails on non-finite geometry, unknown texture keys,
// missing scene fields or fire-cube counts over the scene's budget. Prints peak cube counts.
import{cameraEye}from'./engine.js';
import{TEXTURES,CAMERAS,cameraFor,buildMesh,spaceCubes}from'./stage.js';
import{scenes}from'./scenes/index.js';

const only=process.argv.slice(2),failures=[];
const fail=(s,msg)=>failures.push(`${s.id}: ${msg}`);
for(const s of scenes){
 if(only.length&&!only.includes(s.id))continue;
 for(const k of['id','name','slot','duration','summary','notes','phases','cameras','draw'])if(s[k]===undefined)fail(s,`missing field ${k}`);
 if(!s.cameras?.orbit)fail(s,'needs an orbit camera');
 const budget=s.budget||600;let peak=0,peakAt=0,quads=0;
 for(const cam of CAMERAS){
  if(!s.cameras?.[cam])continue;
  for(let i=0;i<=120;i++){
   const t=s.duration*i/120,camera=cameraFor(s,cam,t);
   if(!cameraEye(camera).every(Number.isFinite))fail(s,`${cam} camera not finite at t=${t.toFixed(2)}`);
   let m;
   try{m=buildMesh(s,t,camera,{cam,embers:i%2===0,dusk:i%3===0})}catch(e){fail(s,`threw at t=${t.toFixed(2)} (${cam}): ${e.message}`);continue}
   for(const[key,data]of m.batches){
    if(!TEXTURES[key])fail(s,`unknown texture ${key}`);
    if(!data.every(Number.isFinite)){fail(s,`non-finite vertex in ${key} at t=${t.toFixed(2)} (${cam})`);break}
   }
   const n=spaceCubes(m);if(n>peak){peak=n;peakAt=t}quads=Math.max(quads,m.quads);
  }
 }
 if(peak>budget)fail(s,`peak ${peak} space pieces exceeds budget ${budget}`);
 console.log(`${s.id.padEnd(13)} peak ${String(peak).padStart(5)} space pieces at t=${peakAt.toFixed(2)}s (budget ${budget}), max ${quads} quads`);
}
if(failures.length){console.error('\nFAILED\n'+failures.join('\n'));process.exit(1)}
console.log('\nCHECK PASSED');
