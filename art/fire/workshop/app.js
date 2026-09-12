import{loadReferences,createRenderer,frameRect,clamp}from'./engine.js';
import{scenes,makeScene}from'./effects.js?v=5b';
const $=id=>document.getElementById(id);
const state={scene:'meteor',time:.8,playing:!matchMedia('(prefers-reduced-motion: reduce)').matches,speed:1,compare:false,dusk:false,particles:true};
const camera={yaw:.42,pitch:.28,distance:6.1,target:[0,.62,0]};
let refs,renderers,last=performance.now(),lastDraw=0,dirty=true,drag=null,stopped=false;

function cameraReset(){const s=scenes[state.scene];Object.assign(camera,{yaw:state.scene==='pyre'?-.65:state.scene==='primary'?.72:.42,pitch:s.pitch,distance:s.distance,target:[...s.target]});dirty=true}
function updateControls(){
 $('play').textContent=state.playing?'Pause':'Play';$('play').setAttribute('aria-label',state.playing?'Pause animation':'Play animation');
 $('timeline').value=state.time;$('time').textContent=`${state.time.toFixed(1)} / ${scenes[state.scene].duration.toFixed(1)} s`;$('phase').textContent=scenes[state.scene].phase(state.time);
}
function setScene(name){
 if(!scenes[name])return;state.scene=name;state.time=name==='ground'?.8:name==='primary'?.95:name==='pyre'?2.8:name==='meteor'?2.7:.4;
 const s=scenes[name];$('scene-title').textContent=s.name;$('note-title').textContent=s.title;$('description').textContent=s.description;$('detail').textContent=s.detail;$('timeline-label').textContent=s.timeline;$('timeline').max=s.duration;$('meteor-note').hidden=name!=='meteor';
 $('proposed-label').textContent=name==='ground'?'Regular ground fire':'Original proposed texture';$('particles').disabled=name==='particles';$('eye').hidden=name!=='pyre';$('scale').textContent=name==='pyre'?'Wall: 2.8 blocks · Floor fire: ~3 inches':name==='meteor'?'Fireball body: 8 blocks across':'Ground squares = 1 block';$('custom-family').textContent={ground:'Regular Minecraft fire',pyre:'Moving fire wall · four frames',primary:'Flame stream · four frames',meteor:'Fireball surface · four frames',particles:'Ember flecks · four frames'}[name];
 for(const b of document.querySelectorAll('[data-scene]'))b.setAttribute('aria-pressed',String(b.dataset.scene===name));
 document.body.dataset.scene=name;cameraReset();updateControls();document.title=`Fire · ${s.name}`;
}

for(const b of document.querySelectorAll('[data-scene]'))b.onclick=()=>setScene(b.dataset.scene);
$('play').onclick=()=>{state.playing=!state.playing;dirty=true;updateControls()};
$('replay').onclick=()=>{state.time=0;state.playing=true;dirty=true;updateControls()};
$('timeline').oninput=()=>{state.time=Number($('timeline').value);state.playing=false;dirty=true;updateControls()};
$('speed').onchange=()=>state.speed=Number($('speed').value);
$('compare').onclick=()=>{state.compare=!state.compare;$('compare').setAttribute('aria-pressed',String(state.compare));$('stages').classList.toggle('compare',state.compare);dirty=true};
$('lighting').onclick=()=>{state.dusk=!state.dusk;$('lighting').textContent=state.dusk?'Dusk':'Daylight';$('lighting').setAttribute('aria-pressed',String(state.dusk));dirty=true};
$('reset').onclick=cameraReset;
$('eye').onclick=()=>{Object.assign(camera,{yaw:-Math.PI/2,pitch:Math.atan2(1.50,6.8),distance:Math.hypot(1.50,6.8),target:[0,.12,0]});dirty=true};
$('particles').onchange=()=>{state.particles=$('particles').checked;dirty=true};
for(const c of[$('old-view'),$('new-view')]){
 c.onpointerdown=e=>{drag={id:e.pointerId,x:e.clientX,y:e.clientY};c.setPointerCapture(e.pointerId)};
 c.onpointermove=e=>{if(!drag||drag.id!==e.pointerId)return;camera.yaw-=(e.clientX-drag.x)*.008;camera.pitch=clamp(camera.pitch+(e.clientY-drag.y)*.006,.05,1.35);drag.x=e.clientX;drag.y=e.clientY;dirty=true};
 c.onpointerup=c.onpointercancel=()=>drag=null;
 c.addEventListener('wheel',e=>{e.preventDefault();camera.distance=clamp(camera.distance*Math.exp(e.deltaY*.001),2,45);dirty=true},{passive:false});
 c.onkeydown=e=>{if(['ArrowLeft','ArrowRight','ArrowUp','ArrowDown','+','-'].includes(e.key)){e.preventDefault();if(e.key==='ArrowLeft')camera.yaw-=.12;if(e.key==='ArrowRight')camera.yaw+=.12;if(e.key==='ArrowUp')camera.pitch=clamp(camera.pitch+.08,.05,1.35);if(e.key==='ArrowDown')camera.pitch=clamp(camera.pitch-.08,.05,1.35);if(e.key==='+')camera.distance=clamp(camera.distance*.9,2,45);if(e.key==='-')camera.distance=clamp(camera.distance*1.1,2,45);dirty=true}};
}
window.addEventListener('resize',()=>dirty=true);
document.addEventListener('visibilitychange',()=>last=performance.now());

function textureStudy(canvas,key,time){
 const ctx=canvas.getContext('2d'),w=canvas.width,h=canvas.height;
 ctx.fillStyle='#222528';ctx.fillRect(0,0,w,h);
 ctx.fillStyle='#2c2f31';for(let x=0;x<w;x+=16)for(let y=0;y<h;y+=16)if((x/16+y/16)%2===0)ctx.fillRect(x,y,16,16);
 let img=refs.images[key],rect=frameRect(refs,key,time),size=128;ctx.imageSmoothingEnabled=false;
 ctx.drawImage(img,rect[0]*img.width,rect[1]*img.height,rect[2]*img.width,rect[3]*img.height,(w-size)/2,h-size-5,size,size);
}

function draw(){
 const info={};if(state.compare)info.current=renderers.old.draw(makeScene(refs,state,camera,true),camera,state.dusk);
 info.proposed=renderers.new.draw(makeScene(refs,state,camera,false),camera,state.dusk);
 textureStudy($('reference'),'fire0',state.time);textureStudy($('old-texture'),'old_ground0',state.time);textureStudy($('vanilla-texture'),'fire0',state.time);textureStudy($('proposed-texture'),{ground:'fire0',pyre:'custom_pyre_wall',primary:'custom_stream',meteor:'custom_meteor_surface',particles:'custom_ember'}[state.scene],state.time);
 window.__firePreview.lastRender=info;updateControls();dirty=false;
}
function frame(now){
 if(stopped)return;let dt=Math.min(.08,(now-last)/1000);last=now;
 if(!document.hidden){if(state.playing){state.time=(state.time+dt*state.speed)%scenes[state.scene].duration;dirty=true}if(dirty&&now-lastDraw>=1000/30){lastDraw=now;try{draw()}catch(e){fail(e);return}}}
 requestAnimationFrame(frame);
}
function fail(e){stopped=true;console.error(e);$('error').hidden=false;$('error').textContent=e.message;document.body.dataset.ready='error'}
window.__firePreview={state,camera,scenes,setScene,redraw:()=>{dirty=true},lastRender:null};
try{
 refs=await loadReferences();renderers={old:createRenderer($('old-view'),refs),new:createRenderer($('new-view'),refs)};
 Object.assign(window.__firePreview,{refs,renderers});setScene('meteor');$('stages').classList.remove('compare');$('compare').setAttribute('aria-pressed','false');document.body.dataset.ready='true';draw();requestAnimationFrame(frame);
}catch(e){fail(e)}
