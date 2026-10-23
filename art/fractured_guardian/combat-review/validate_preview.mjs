// Deterministic DOM/WebGL API smoke, not a browser screenshot or GPU test.
// Exercises initialization, every clip, runtime scale tracks and review controls
// even when this workspace cannot launch Chrome or bind a local server.
import fs from 'node:fs';
import vm from 'node:vm';
import assert from 'node:assert/strict';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'../../..');
const html=fs.readFileSync(path.join(root,'.local-previews/guardian-combat-review/index.html'),'utf8');
const script=html.match(/<script>([\s\S]*)<\/script>/)[1];
const nodes=[];const uniforms={};let draws=0;
class Element {
  constructor(){this.children=[];this.dataset={};this.checked=false;this.value='';this.clientWidth=1120;this.clientHeight=650;this.classList={toggle(){},remove(){}};nodes.push(this)}
  append(child){this.children.push(child)}
  replaceChildren(){this.children=[]}
  setPointerCapture(){}
}
const ids={};
for(const [,id] of html.matchAll(/id="([^"]+)"/g))ids[id]=new Element();
ids.loop.checked=true;ids.speed.value='1';
const views=['hero','front','side','back','player'].map(view=>{const e=new Element();e.dataset.view=view;return e});
const gl=new Proxy({
  getShaderParameter:()=>true,getProgramParameter:()=>true,
  getUniformLocation:(_,name)=>name,getAttribLocation:()=>0,
  bufferData:(_,values)=>assert.ok([...values].every(Number.isFinite),'Nonfinite posed vertices'),
  uniform3fv:(name,values)=>{uniforms[name]=[...values]},
  drawArrays:()=>draws++,
},{get:(target,key)=>key in target?target[key]:()=>({})});
ids.view.getContext=()=>gl;
const document={
  querySelector:selector=>selector==='canvas'?ids.view:selector.startsWith('#')?ids[selector.slice(1)]:new Element(),
  querySelectorAll:selector=>selector==='[data-view]'?views:selector==='[data-clip]'?nodes.filter(n=>n.dataset.clip):[],
  createElement:()=>new Element(),
};
const context=vm.createContext({document,devicePixelRatio:1,requestAnimationFrame(){},addEventListener(){},Image:class {},console});
vm.runInContext(script,context);
assert.equal(ids['clip-list'].children.length,18);
assert.equal(ids['reworked-links'].children.length,15);
assert.equal(ids['clip-list'].children.filter(e=>e.innerHTML.includes('Reworked tonight')).length,15);
for(const button of ids['reworked-links'].children){
  button.onclick();assert.equal(ids['selected-name'].textContent,button.dataset.clip);
  assert.ok(ids.description.textContent.includes('s'));assert.equal(ids['selected-badge'].hidden,false);
  for(const landmark of ids.events.children)landmark.onclick();
}
vm.runInContext(`for(const key of Object.keys(clips)){
  selectClip(key.split('.').pop());
  for(const shell of [false,true]){
    document.querySelector('#shell').checked=shell;
    for(let i=0;i<=24;i++){motionTime=selectedClip().animation_length*i/24;draw();}
  }
}`,context);
views.find(e=>e.dataset.view==='player').onclick();
assert.equal(uniforms.eye[1],28.8,'Eye must be 1.8 blocks above the floor');
ids['reworked-links'].children[0].onclick();
ids.pause.onclick();ids.replay.onclick();ids.timeline.value='0.5';ids.timeline.oninput();
assert.ok(ids.time.textContent.startsWith('0.50'));
// Full sequence uses the retained V5 travel/arc function and shipped clips.
assert.ok(html.includes('href="full-leap.html"'));
const full=fs.readFileSync(path.join(root,'.local-previews/guardian-combat-review/full-leap.html'),'utf8');
const fullScript=full.match(/<script>([\s\S]*)<\/script>/)[1];
new vm.Script(fullScript);
const offsetFunction=fullScript.match(/function leapOffset\(\)\{[^\n]+\}/)[0];
for(const [time,want] of [[.7,[0,0,0]],[1.6,[0,176,-128]],[2.5,[0,0,-256]]]){
  const result=vm.runInNewContext(offsetFunction+';leapOffset()',{clipName:'full_leap',motionTime:time});
  result.forEach((v,i)=>assert.ok(Math.abs(v-want[i])<1e-8));
}
// Following the model, the ground must travel at the stance-foot speed.
vm.runInContext(`selectClip('walk');motionTime=.3;floorVertices();`,context);
const floorA=vm.runInContext('vertices.slice(-24*24*6*9)',context);
vm.runInContext('motionTime=.6;vertices.length=0;floorVertices();',context);
const floorB=vm.runInContext('vertices',context);
assert.ok(Math.abs(floorB[2]-floorA[2]-2.5)<1e-8);
console.log(`PASS: 18 clips, 15 library badges and clickable banner entries, landmarks, playback controls, ${draws} finite geometry draws, 1.8-block camera, full leap arc and walking ground speed. Mock APIs; real browser/GPU review still required.`);
