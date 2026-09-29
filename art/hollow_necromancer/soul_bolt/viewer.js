// Review-only WebGL viewer for the authored GeckoLib Soul Bolt candidate.
// Its drag direction matches the Hollow Necromancer and Guardian workshops.
'use strict';

const base = '/art/hollow_necromancer/soul_bolt/candidate/';
const embedded = document.querySelector('#model-data') ? JSON.parse(document.querySelector('#model-data').textContent) : null;
const canvas = document.querySelector('#view');
const gl = canvas.getContext('webgl2', {antialias: true, alpha: true, preserveDrawingBuffer: true});
const atlas = document.querySelector('#atlas');
const rad = degrees => degrees * Math.PI / 180;
const M = {
  id: () => new Float32Array([1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]),
  mul(a,b) { const out = new Float32Array(16); for(let c=0;c<4;c++) for(let r=0;r<4;r++) for(let k=0;k<4;k++) out[c*4+r]+=a[k*4+r]*b[c*4+k]; return out; },
  perspective(fov,aspect,near,far) { const out=new Float32Array(16), f=1/Math.tan(fov/2); out[0]=f/aspect; out[5]=f; out[10]=(far+near)/(near-far); out[11]=-1; out[14]=2*far*near/(near-far); return out; },
  lookAt(eye,at) { const z=norm(sub(eye,at)), x=norm(cross([0,1,0],z)), y=cross(z,x); return new Float32Array([x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,eye),-dot(y,eye),-dot(z,eye),1]); },
};
const sub=(a,b)=>a.map((v,i)=>v-b[i]);
const dot=(a,b)=>a[0]*b[0]+a[1]*b[1]+a[2]*b[2];
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const norm=a=>{const l=Math.hypot(...a)||1;return a.map(v=>v/l);};

const state={yaw:rad(-35),pitch:rad(8),distance:1.95,motion:true,time:.65,glow:true,night:true};
const vs=`#version 300 es
in vec3 pos; in vec3 normal; in vec2 uv; uniform mat4 vp;
out vec3 vNormal; out vec2 vUv;
void main(){gl_Position=vp*vec4(pos,1.);vNormal=normal;vUv=uv;}`;
const fs=`#version 300 es
precision highp float;
in vec3 vNormal; in vec2 vUv; out vec4 color;
uniform sampler2D tex; uniform bool emissive; uniform bool night;
void main(){
 vec4 p=texture(tex,vUv); if(p.a<.1) discard;
 if(emissive){color=vec4(p.rgb*.75,1.);return;}
 vec3 n=normalize(vNormal);
 float shade=.42+.35*max(0.,dot(n,normalize(vec3(.6,.9,-.7))))+.23*max(0.,dot(n,normalize(vec3(-.7,.3,.8))));
 color=vec4(p.rgb*shade*(night?vec3(.7,.81,.95):vec3(1.,.98,.93)),1.);
}`;

function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
function program(){const p=gl.createProgram();gl.attachShader(p,shader(gl.VERTEX_SHADER,vs));gl.attachShader(p,shader(gl.FRAGMENT_SHADER,fs));gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;}
let pipeline,vao,buffer,texture,glowTexture,bones,bite,texWidth,texHeight,ready=false;

function upload(image){const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,image);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);return t;}
function loadImage(name){return new Promise((resolve,reject)=>{const im=new Image();im.onload=()=>resolve(im);im.onerror=()=>reject(Error(name+' failed to load'));im.src=embedded?embedded.images[name]:base+name;});}

function faces(cube){
 const [x,y,z]=cube.origin,[w,h,d]=cube.size,X=x+w,Y=y+h,Z=z+d;
 return {
  north:{p:[[x,y,z],[X,y,z],[X,Y,z],[x,Y,z]],n:[0,0,-1]},
  south:{p:[[X,y,Z],[x,y,Z],[x,Y,Z],[X,Y,Z]],n:[0,0,1]},
  east:{p:[[X,y,z],[X,y,Z],[X,Y,Z],[X,Y,z]],n:[1,0,0]},
  west:{p:[[x,y,Z],[x,y,z],[x,Y,z],[x,Y,Z]],n:[-1,0,0]},
  up:{p:[[x,Y,z],[X,Y,z],[X,Y,Z],[x,Y,Z]],n:[0,1,0]},
  down:{p:[[x,y,Z],[X,y,Z],[X,y,z],[x,y,z]],n:[0,-1,0]},
 };
}
function turnX(point,angle,pivot=[0,0,0]){const dy=point[1]-pivot[1],dz=point[2]-pivot[2],c=Math.cos(angle),s=Math.sin(angle);return [point[0],pivot[1]+dy*c-dz*s,pivot[2]+dy*s+dz*c];}
function sample(channel,time){
 const keys=Object.entries(channel).map(([t,v])=>[+t,v]).sort((a,b)=>a[0]-b[0]);
 if(time<=keys[0][0])return keys[0][1];
 for(let i=1;i<keys.length;i++)if(time<=keys[i][0]){
  const [a,av]=keys[i-1],[b,bv]=keys[i],f=(time-a)/(b-a);
  return av.map((v,j)=>v+(bv[j]-v)*f);
 }
 return keys.at(-1)[1];
}
function mesh(time){
 const data=[],angle=-rad(sample(bite.bones.jaw.rotation,time)[0]);
 const shift=sample(bite.bones.skull.position,time),tilt=-rad(sample(bite.bones.skull.rotation,time)[0]);
 const jawPivot=bones.find(b=>b.name==='jaw').pivot;
 for(const bone of bones)for(const cube of bone.cubes){
  const regions=faces(cube);
  for(const [name,face] of Object.entries(regions)){
   const {uv:[u,v],uv_size:[w,h]}=cube.uv[name];
   // Read the exported face rectangles directly; geometry can be fractional.
   const u0=u/texWidth,u1=(u+w)/texWidth,v0=1-v/texHeight,v1=1-(v+h)/texHeight;
   const uvs=[[u0,v1],[u1,v1],[u1,v0],[u0,v0]];
   for(const k of [0,1,2,0,2,3]){
    let p=face.p[k],n=face.n;
    if(bone.name==='jaw'){
     p=turnX(p,angle,jawPivot);n=turnX(n,angle);
    }
    p=turnX(p,tilt);n=turnX(n,tilt);
    data.push((p[0]+shift[0])/16,(p[1]+shift[1])/16,(p[2]+shift[2])/16,...n,...uvs[k]);
   }
  }
 }
 gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(data),gl.DYNAMIC_DRAW);
 return data.length/8;
}
function draw(time){
 if(!ready)return;
 const dpr=Math.min(devicePixelRatio,2),w=Math.max(1,Math.round(canvas.clientWidth*dpr)),h=Math.max(1,Math.round(canvas.clientHeight*dpr));
 if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
 gl.viewport(0,0,w,h);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
 gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.disable(gl.CULL_FACE);
 const count=mesh(time);
 const d=state.distance/Math.min(1,w/h),target=[0,-.16,-.04],eye=[target[0]+Math.sin(state.yaw)*Math.cos(state.pitch)*d,target[1]+Math.sin(state.pitch)*d,target[2]-Math.cos(state.yaw)*Math.cos(state.pitch)*d];
 const vp=M.mul(M.perspective(rad(34),w/h,.01,20),M.lookAt(eye,target));
 gl.useProgram(pipeline);gl.bindVertexArray(vao);
 gl.uniformMatrix4fv(gl.getUniformLocation(pipeline,'vp'),false,vp);
 gl.uniform1i(gl.getUniformLocation(pipeline,'night'),state.night);
 gl.activeTexture(gl.TEXTURE0);gl.uniform1i(gl.getUniformLocation(pipeline,'tex'),0);
 gl.bindTexture(gl.TEXTURE_2D,texture);gl.uniform1i(gl.getUniformLocation(pipeline,'emissive'),false);
 gl.drawArrays(gl.TRIANGLES,0,count);
 if(state.glow){gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE);gl.depthMask(false);gl.bindTexture(gl.TEXTURE_2D,glowTexture);gl.uniform1i(gl.getUniformLocation(pipeline,'emissive'),true);gl.drawArrays(gl.TRIANGLES,0,count);gl.depthMask(true);gl.disable(gl.BLEND);}
 gl.bindVertexArray(null);
}

function setupControls(){
 let drag=null;
 canvas.addEventListener('pointerdown',e=>{drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId);canvas.classList.add('dragging');});
 canvas.addEventListener('pointermove',e=>{if(!drag)return;const dx=e.clientX-drag[0],dy=e.clientY-drag[1];state.yaw-=dx*.008;state.pitch=Math.max(-1.25,Math.min(1.25,state.pitch+dy*.008));drag=[e.clientX,e.clientY];document.querySelectorAll('[data-view]').forEach(b=>b.classList.remove('active'));});
 canvas.addEventListener('pointerup',()=>{drag=null;canvas.classList.remove('dragging');});
 canvas.addEventListener('wheel',e=>{e.preventDefault();state.distance=Math.max(.7,Math.min(4,state.distance*Math.exp(e.deltaY*.0012)));},{passive:false});
 const views={hero:[-35,8],front:[0,0],side:[90,0],back:[180,0],under:[-35,-52]};
 document.querySelectorAll('[data-view]').forEach(b=>b.onclick=()=>{[state.yaw,state.pitch]=views[b.dataset.view].map(rad);document.querySelectorAll('[data-view]').forEach(x=>x.classList.toggle('active',x===b));});
 document.querySelector('#motion').onclick=()=>{state.motion=!state.motion;syncMotion();};
 document.querySelector('#replay').onclick=()=>{state.time=0;state.motion=true;syncMotion();};
 document.querySelector('#open-jaw').onclick=()=>{state.time=1.5;state.motion=false;syncMotion();};
 document.querySelector('#closed-jaw').onclick=()=>{state.time=1.96;state.motion=false;syncMotion();};
 document.querySelector('#timeline').oninput=e=>{state.time=+e.target.value;state.motion=false;syncMotion();};
 document.querySelector('#glow').onclick=e=>{state.glow=!state.glow;e.currentTarget.classList.toggle('active',state.glow);};
 document.querySelector('#light').onclick=e=>{state.night=!state.night;e.currentTarget.classList.toggle('active',state.night);e.currentTarget.textContent=state.night?'Night light':'Day light';};
}
function syncMotion(){const b=document.querySelector('#motion');b.textContent=state.motion?'Pause bite':'Play bite';b.classList.toggle('active',state.motion);}
function updateTimeline(){
 document.querySelector('#timeline').value=state.time;
 const phase=state.time<.55?'Opening jaws':state.time<1.7?'Flying with jaws wide':state.time<1.86?'BITE':state.time<2.12?'Bite held':'Resetting preview';
 document.querySelector('#phase').textContent=phase;
}

async function main(){
 if(!gl)throw Error('WebGL 2 is unavailable in this browser');
 const [model,skin,glow,animation]=await Promise.all([embedded?embedded.geo:fetch(base+'soul_bolt.geo.json').then(r=>{if(!r.ok)throw Error('Model failed to load');return r.json();}),loadImage('soul_bolt.png'),loadImage('soul_bolt_glowmask.png'),embedded?embedded.animation:fetch(base+'soul_bolt.animation.json').then(r=>{if(!r.ok)throw Error('Bite animation failed to load');return r.json();})]);
 bite=animation.animations['animation.soul_bolt.bite'];
 const geo=model['minecraft:geometry'][0];bones=geo.bones;texWidth=geo.description.texture_width;texHeight=geo.description.texture_height;
 atlas.width=skin.width;atlas.height=skin.height;atlas.getContext('2d').drawImage(skin,0,0);
 texture=upload(skin);glowTexture=upload(glow);pipeline=program();vao=gl.createVertexArray();buffer=gl.createBuffer();
 gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,buffer);
 for(const [name,size,offset] of [['pos',3,0],['normal',3,3],['uv',2,6]]){const loc=gl.getAttribLocation(pipeline,name);gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,size,gl.FLOAT,false,32,offset*4);}
 gl.bindVertexArray(null);ready=true;setupControls();
 document.querySelector('#timeline').max=bite.animation_length;
 let previous=performance.now();function frame(now){const dt=Math.min(.1,(now-previous)/1000);previous=now;if(state.motion)state.time=(state.time+dt)%bite.animation_length;draw(state.time);updateTimeline();requestAnimationFrame(frame);}requestAnimationFrame(frame);
}
main().catch(e=>{const note=document.createElement('div');note.className='notice';note.textContent=e.message;document.querySelector('aside').prepend(note);});
