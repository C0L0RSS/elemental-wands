import{loadTextures,createRenderer}from'./engine.js';
import{clamp}from'./engine.js';
import{TEXTURES,cameraFor,buildMesh,spaceCubes}from'./stage.js';
import{scenes}from'./scenes/index.js';

const $=id=>document.getElementById(id);
const params=new URLSearchParams(location.search);
const byId=Object.fromEntries(scenes.map(s=>[s.id,s]));
const state={scene:scenes[0].id,time:0,playing:!params.has('still'),speed:1,cam:'orbit',dusk:false,embers:true};
let orbit=null,renderer,last=performance.now(),dirty=true,drag=null,stopped=false;

const scene=()=>byId[state.scene];
function phaseAt(s,t){let label='';for(const[at,name]of s.phases)if(t>=at)label=name;return label}
function resetOrbit(){orbit=structuredClone(scene().cameras.orbit);dirty=true}
function camera(){return state.cam==='orbit'?orbit:cameraFor(scene(),state.cam,state.time)}

function setScene(id,keepTime=false){
 if(!byId[id])return;state.scene=id;const s=scene();if(!keepTime)state.time=0;
 if(!s.cameras[state.cam])state.cam='orbit';
 $('slot').textContent=s.slot;$('title').textContent=s.name;$('summary').textContent=s.summary;$('notes').textContent=s.notes;
 $('timeline').max=s.duration;
 for(const b of document.querySelectorAll('[data-scene]'))b.setAttribute('aria-pressed',String(b.dataset.scene===id));
 for(const b of document.querySelectorAll('[data-cam]'))b.hidden=!s.cameras[b.dataset.cam];
 resetOrbit();update();document.title=`Block space · ${s.name}`;
}
function setCam(name){if(!scene().cameras[name])return;state.cam=name;if(name==='orbit')resetOrbit();dirty=true;update()}
function update(){
 const s=scene();$('play').textContent=state.playing?'Pause':'Play';$('timeline').value=state.time;
 $('time').textContent=`${state.time.toFixed(2)} / ${s.duration.toFixed(1)} s`;$('phase').textContent=phaseAt(s,state.time);
 for(const b of document.querySelectorAll('[data-cam]'))b.setAttribute('aria-pressed',String(b.dataset.cam===state.cam));
}

const nav=$('scenes');
scenes.forEach((s,i)=>{const b=document.createElement('button');b.dataset.scene=s.id;b.innerHTML=`<span>${String(i+1).padStart(2,'0')} · ${s.slot}</span>${s.name}`;b.onclick=()=>setScene(s.id);nav.append(b)});
for(const b of document.querySelectorAll('[data-cam]'))b.onclick=()=>setCam(b.dataset.cam);
$('play').onclick=()=>{state.playing=!state.playing;update()};
$('replay').onclick=()=>{state.time=0;state.playing=true;dirty=true;update()};
$('timeline').oninput=()=>{state.time=Number($('timeline').value);state.playing=false;dirty=true;update()};
$('speed').onchange=()=>state.speed=Number($('speed').value);
$('dusk').onclick=()=>{state.dusk=!state.dusk;$('dusk').setAttribute('aria-pressed',String(state.dusk));dirty=true};
$('embers').onchange=()=>{state.embers=$('embers').checked;dirty=true};
// Full view hides the panels and asks the browser for real fullscreen where it allows it; leaving fullscreen (Esc)
// brings the panels back.
function setFocus(on){
 document.body.classList.toggle('focus',on);$('focus').setAttribute('aria-pressed',String(on));$('focus').textContent=on?'Exit full view':'Full view';dirty=true;
 if(on&&!document.fullscreenElement)document.documentElement.requestFullscreen?.().catch(()=>{});
 if(!on&&document.fullscreenElement)document.exitFullscreen?.().catch(()=>{});
}
$('focus').onclick=()=>setFocus(!document.body.classList.contains('focus'));
document.addEventListener('fullscreenchange',()=>{if(!document.fullscreenElement&&document.body.classList.contains('focus'))setFocus(false);dirty=true});
const canvas=$('view');
canvas.onpointerdown=e=>{if(state.cam!=='orbit')setCam('orbit');drag={id:e.pointerId,x:e.clientX,y:e.clientY};canvas.setPointerCapture(e.pointerId)};
canvas.onpointermove=e=>{if(!drag||drag.id!==e.pointerId)return;orbit.yaw-=(e.clientX-drag.x)*.008;orbit.pitch=clamp(orbit.pitch+(e.clientY-drag.y)*.006,-.2,1.45);drag.x=e.clientX;drag.y=e.clientY;dirty=true};
canvas.onpointerup=canvas.onpointercancel=()=>drag=null;
canvas.addEventListener('wheel',e=>{e.preventDefault();if(state.cam!=='orbit')setCam('orbit');orbit.distance=clamp(orbit.distance*Math.exp(e.deltaY*.001),2,90);dirty=true},{passive:false});
window.addEventListener('resize',()=>dirty=true);
document.addEventListener('visibilitychange',()=>last=performance.now());
document.addEventListener('keydown',e=>{if(e.target.tagName==='INPUT'||e.target.tagName==='SELECT')return;
 if(e.key===' '){e.preventDefault();$('play').click()}
 const i=scenes.findIndex(s=>s.id===state.scene);
 if(e.key==='ArrowDown'||e.key==='j'){e.preventDefault();setScene(scenes[(i+1)%scenes.length].id)}
 if(e.key==='ArrowUp'||e.key==='k'){e.preventDefault();setScene(scenes[(i-1+scenes.length)%scenes.length].id)}
 if(e.key==='f'||e.key==='F')$('focus').click();
 if(e.key==='Escape'&&document.body.classList.contains('focus'))setFocus(false);
 if(e.key==='1')setCam('orbit');if(e.key==='2')setCam('caster');if(e.key==='3')setCam('target');});

function draw(){
 const cam=camera(),mesh=buildMesh(scene(),state.time,cam,{cam:state.cam,embers:state.embers,dusk:state.dusk});
 const info=renderer.draw(mesh,cam,state.dusk);info.spaceCubes=spaceCubes(mesh);
 $('stats').textContent=`${info.spaceCubes} space pieces`;window.__blockspace.last=info;dirty=false;
}
function frame(now){
 if(stopped)return;const dt=Math.min(.08,(now-last)/1000);last=now;
 if(!document.hidden){if(state.playing){state.time=(state.time+dt*state.speed)%scene().duration;dirty=true;update()}if(dirty){try{draw()}catch(e){fail(e);return}}}
 requestAnimationFrame(frame);
}
function fail(e){stopped=true;console.error(e);$('error').hidden=false;$('error').textContent=e.message;document.body.dataset.ready='error'}

window.__blockspace={state,scenes,setScene,setCam,redraw:()=>dirty=true,last:null};
try{
 renderer=createRenderer(canvas,await loadTextures(TEXTURES));
 // URL: ?scene=inferno&t=1.2&cam=caster&dusk=1&still=1 (still freezes the frame for screenshots).
 setScene(byId[params.get('scene')]?params.get('scene'):scenes[0].id);
 if(params.has('t'))state.time=Number(params.get('t'));
 if(params.get('cam'))setCam(params.get('cam'));
 if(params.get('dusk')==='1'){state.dusk=true;$('dusk').setAttribute('aria-pressed','true')}
 if(params.has('yaw'))orbit.yaw=Number(params.get('yaw'));if(params.has('pitch'))orbit.pitch=Number(params.get('pitch'));if(params.has('dist'))orbit.distance=Number(params.get('dist'));
 if(params.has('still')){document.body.classList.add('still');setTimeout(()=>dirty=true,150)}
 update();draw();document.body.dataset.ready='true';requestAnimationFrame(frame);
}catch(e){fail(e)}
