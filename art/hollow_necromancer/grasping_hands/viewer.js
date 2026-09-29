// Review-only model viewer. Runtime exports remain untouched.
'use strict';
const payload=JSON.parse(document.querySelector('#model-data').textContent);
const canvas=document.querySelector('#view');
const gl=canvas.getContext('webgl2',{antialias:true,alpha:true,preserveDrawingBuffer:true});
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
function sample(channel,time){
 const keys=Object.entries(channel).map(([t,v])=>[+t,v]).sort((a,b)=>a[0]-b[0]);
 if(time<=keys[0][0])return keys[0][1];
 for(let i=1;i<keys.length;i++)if(time<=keys[i][0]){
  const [a,av]=keys[i-1],[b,bv]=keys[i],f=(time-a)/(b-a);
  return av.map((v,j)=>v+(bv[j]-v)*f);
 }
 return keys.at(-1)[1];
}
const state={yaw:rad(-35),pitch:rad(28),distance:6.7,time:1.8,motion:true,single:false,variant:0,escape:false,glow:true};
const vertex=`#version 300 es
in vec3 pos;in vec3 normal;in vec2 uv;uniform mat4 vp;out vec3 n;out vec2 texUv;out float height;
void main(){gl_Position=vp*vec4(pos,1.);n=normal;texUv=uv;height=pos.y;}`;
const fragment=`#version 300 es
precision highp float;in vec3 n;in vec2 texUv;in float height;uniform sampler2D tex;uniform vec4 tint;uniform bool emissive;out vec4 color;
void main(){if(height<-.015)discard;vec4 p=texture(tex,texUv)*tint;if(p.a<.05)discard;
float light=emissive?1.:.45+.4*max(0.,dot(normalize(n),normalize(vec3(.6,1.,-.7))))+.15*max(0.,dot(normalize(n),normalize(vec3(-1.,.3,.5))));color=vec4(p.rgb*light,p.a);}`;
function shader(type,code){const s=gl.createShader(type);gl.shaderSource(s,code);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
function upload(im){const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,im);for(const p of [gl.TEXTURE_MIN_FILTER,gl.TEXTURE_MAG_FILTER])gl.texParameteri(gl.TEXTURE_2D,p,gl.NEAREST);for(const p of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T])gl.texParameteri(gl.TEXTURE_2D,p,gl.CLAMP_TO_EDGE);return t;}
const loadImage=src=>new Promise((resolve,reject)=>{const im=new Image();im.onload=()=>resolve(im);im.onerror=reject;im.src=src;});
function translate(v){const m=M.id();m[12]=v[0];m[13]=v[1];m[14]=v[2];return m;}
function rotate(axis,angle){const m=M.id(),c=Math.cos(angle),s=Math.sin(angle);if(axis===0){m[5]=c;m[6]=s;m[9]=-s;m[10]=c;}if(axis===1){m[0]=c;m[2]=-s;m[8]=s;m[10]=c;}if(axis===2){m[0]=c;m[1]=s;m[4]=-s;m[5]=c;}return m;}
function transform(m,p,normal=false){return [0,1,2].map(r=>m[r]*p[0]+m[4+r]*p[1]+m[8+r]*p[2]+(normal?0:m[12+r]));}
let pipeline,vao,buffer,white,variants,uniforms;
function pose(variant,time){
 const result={};
 for(const bone of variant.bones){
  const channels=variant.animation.bones[bone.name]||{},r=channels.rotation?sample(channels.rotation,time):[0,0,0],p=channels.position?sample(channels.position,time):[0,0,0];
  let local=translate(p);
  local=M.mul(local,translate(bone.pivot));
  // GeckoLib X/Y signs, followed by parent-space transforms.
  for(const axis of [2,1,0])local=M.mul(local,rotate(axis,rad(r[axis])*(axis===2?1:-1)));
  local=M.mul(local,translate(bone.pivot.map(v=>-v)));
  result[bone.name]=bone.parent?M.mul(result[bone.parent],local):local;
 }
 return result;
}
function putFace(data,face,uvs,point,normal){for(const k of [0,1,2,0,2,3])data.push(...point(face.p[k]),...normal(face.n),...uvs[k]);}
function handMesh(variant,time,index){
 const data=[],matrices=pose(variant,time),theta=index*Math.PI/3+.15;
 const radius=state.single?0:1.13+(index%2)*.11;
 const yaw=state.single?0:Math.PI/2-theta,c=Math.cos(yaw),s=Math.sin(yaw),mirror=index%2?-1:1;
 const scale=state.single?1:1+(index%3-1)*.07;
 function place(p,normal=false){let [x,y,z]=p;x*=mirror;if(!normal){x=x/16*scale;y=y/16*scale;z=z/16*scale;}return [c*x+s*z+(normal?0:Math.cos(theta)*radius),y,-s*x+c*z+(normal?0:Math.sin(theta)*radius)];}
 for(const bone of variant.bones)for(const cube of bone.cubes){
  for(const [name,face] of Object.entries(faces(cube))){
   const {uv:[u,v],uv_size:[w,h]}=cube.uv[name];
   const uv=[[u/variant.tw,1-(v+h)/variant.th],[(u+w)/variant.tw,1-(v+h)/variant.th],[(u+w)/variant.tw,1-v/variant.th],[u/variant.tw,1-v/variant.th]];
   putFace(data,face,uv,p=>place(transform(matrices[bone.name],p)),n=>place(transform(matrices[bone.name],n,true),true));
  }
 }
 return data;
}
function box(data,center,size){const cube={origin:center.map((v,i)=>v-size[i]/2),size};for(const f of Object.values(faces(cube)))putFace(data,f,[[0,0],[1,0],[1,1],[0,1]],p=>p,n=>n);}
function submit(data,texture,tint=[1,1,1,1],emissive=false){gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(data),gl.DYNAMIC_DRAW);gl.bindTexture(gl.TEXTURE_2D,texture);gl.uniform4fv(uniforms.tint,tint);gl.uniform1i(uniforms.emissive,emissive);gl.drawArrays(gl.TRIANGLES,0,data.length/8);}
function scene(time){
 const floor=[];box(floor,[0,-.015,0],[8,.03,8]);submit(floor,white,[.075,.10,.135,1]);
 const grid=[];for(let i=-8;i<=8;i++){box(grid,[i*.5,.004,0],[.012,.009,8]);box(grid,[0,.004,i*.5],[8,.009,.012]);}submit(grid,white,[.12,.17,.20,1]);
 if(!state.single){
  const ring=[];for(let i=0;i<80;i++){const a=i*Math.PI/40;box(ring,[Math.cos(a)*1.7,.025,Math.sin(a)*1.7],[.07,.045,.07]);}
  submit(ring,white,time<1.6?[.1,.58,.63,1]:[.2,.82,.83,1],true);
  const figure=[],exit=state.escape?Math.min(1,Math.max(0,(time-.4)/.8))*2.3:0;
  box(figure,[exit,1.6,0],[.46,.46,.46]);box(figure,[exit,1.04,0],[.5,.68,.25]);
  box(figure,[exit-.13,.36,0],[.22,.7,.26]);box(figure,[exit+.13,.36,0],[.22,.7,.26]);
  box(figure,[exit-.38,1.04,0],[.23,.68,.25]);box(figure,[exit+.38,1.04,0],[.23,.68,.25]);
  submit(figure,white,[.26,.34,.39,1]);
 }
 for(let i=0;i<(state.single?1:6);i++){
  const v=variants[state.single?state.variant:i%3];
  // Stagger only the rise; every hand closes at the server's 1.6s impact.
  const delay=(i%3)*.09,t=time<1.2?Math.max(0,time-delay):time;
  const data=handMesh(v,t,i);submit(data,v.texture);
  if(state.glow){gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE);gl.depthMask(false);submit(data,v.glow,[1,1,1,1],true);gl.depthMask(true);gl.disable(gl.BLEND);}
 }
}
function draw(){
 const dpr=Math.min(devicePixelRatio,2),w=Math.round(canvas.clientWidth*dpr),h=Math.round(canvas.clientHeight*dpr);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
 gl.viewport(0,0,w,h);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.disable(gl.CULL_FACE);
 const at=[0,.75,0],d=(state.single?state.distance*.5:state.distance)/Math.min(1,w/h),eye=[Math.sin(state.yaw)*Math.cos(state.pitch)*d,at[1]+Math.sin(state.pitch)*d,-Math.cos(state.yaw)*Math.cos(state.pitch)*d];
 gl.useProgram(pipeline);gl.bindVertexArray(vao);gl.uniformMatrix4fv(uniforms.vp,false,M.mul(M.perspective(rad(38),w/h,.01,50),M.lookAt(eye,at)));gl.activeTexture(gl.TEXTURE0);gl.uniform1i(uniforms.tex,0);scene(state.time);gl.bindVertexArray(null);
 document.querySelector('#timeline').value=state.time;
 document.querySelector('#phase').textContent=state.time<.4?'Warning':state.time<1.6?'Hands emerging':state.time<1.85?'GRAB':state.time<3.1?'Grip held':state.time<3.85?'Sinking away':'Reset';
}
function sync(){const b=document.querySelector('#motion');b.textContent=state.motion?'Pause':'Play';b.classList.toggle('active',state.motion);}
function controls(){
 let drag=null;canvas.onpointerdown=e=>{drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId);canvas.classList.add('dragging');};
 canvas.onpointermove=e=>{if(!drag)return;state.yaw-=(e.clientX-drag[0])*.008;state.pitch=Math.max(.05,Math.min(1.5,state.pitch+(e.clientY-drag[1])*.008));drag=[e.clientX,e.clientY];document.querySelectorAll('[data-view]').forEach(b=>b.classList.remove('active'));};
 const release=()=>{drag=null;canvas.classList.remove('dragging');};canvas.onpointerup=release;canvas.onpointercancel=release;
 canvas.onwheel=e=>{e.preventDefault();state.distance=Math.max(3,Math.min(14,state.distance*Math.exp(e.deltaY*.0012)));};
 const views={hero:[-35,28],front:[0,8],top:[-15,78]};document.querySelectorAll('[data-view]').forEach(b=>b.onclick=()=>{[state.yaw,state.pitch]=views[b.dataset.view].map(rad);document.querySelectorAll('[data-view]').forEach(x=>x.classList.toggle('active',x===b));});
 document.querySelector('#motion').onclick=()=>{state.motion=!state.motion;sync();};document.querySelector('#replay').onclick=()=>{state.time=0;state.motion=true;sync();};
 document.querySelectorAll('[data-time]').forEach(b=>b.onclick=()=>{state.time=+b.dataset.time;state.motion=false;sync();});
 document.querySelector('#timeline').oninput=e=>{state.time=+e.target.value;state.motion=false;sync();};
 document.querySelector('#single').onclick=e=>{state.single=!state.single;e.currentTarget.textContent=state.single?'Six hands':'Single hand';e.currentTarget.classList.toggle('active',state.single);};
 document.querySelector('#variant').onclick=e=>{state.variant=(state.variant+1)%3;state.single=true;document.querySelector('#single').textContent='Six hands';document.querySelector('#single').classList.add('active');e.currentTarget.textContent=['Balanced hand','Broad hand','Long hand'][state.variant];};
 document.querySelector('#escape').onclick=e=>{state.escape=!state.escape;e.currentTarget.textContent=state.escape?'Player escapes':'Player stays';e.currentTarget.classList.toggle('active',state.escape);};
 document.querySelector('#glow').onclick=e=>{state.glow=!state.glow;e.currentTarget.classList.toggle('active',state.glow);};
}
async function main(){
 if(!gl)throw Error('WebGL 2 is unavailable');
 pipeline=gl.createProgram();gl.attachShader(pipeline,shader(gl.VERTEX_SHADER,vertex));gl.attachShader(pipeline,shader(gl.FRAGMENT_SHADER,fragment));gl.linkProgram(pipeline);if(!gl.getProgramParameter(pipeline,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(pipeline));
 uniforms=Object.fromEntries(['vp','tex','tint','emissive'].map(k=>[k,gl.getUniformLocation(pipeline,k)]));
 vao=gl.createVertexArray();buffer=gl.createBuffer();gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,buffer);for(const [name,size,off]of [['pos',3,0],['normal',3,3],['uv',2,6]]){const loc=gl.getAttribLocation(pipeline,name);gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,size,gl.FLOAT,false,32,off*4);}gl.bindVertexArray(null);
 const pixel=document.createElement('canvas');pixel.width=pixel.height=1;pixel.getContext('2d').fillStyle='white';pixel.getContext('2d').fillRect(0,0,1,1);white=upload(pixel);
 variants=await Promise.all(payload.variants.map(async v=>{const g=v.geo['minecraft:geometry'][0],skin=await loadImage(v.images[v.stem+'.png']),glow=await loadImage(v.images[v.stem+'_glowmask.png']);return {bones:g.bones,tw:g.description.texture_width,th:g.description.texture_height,animation:v.animation,texture:upload(skin),glow:upload(glow)};}));
 controls();document.body.dataset.ready='true';let previous=performance.now();function frame(now){const dt=Math.min(.1,(now-previous)/1000);previous=now;if(state.motion)state.time=(state.time+dt)%4.2;draw();requestAnimationFrame(frame);}requestAnimationFrame(frame);
}
main().catch(e=>{document.body.dataset.error=e.message;document.querySelector('aside').insertAdjacentHTML('afterbegin','<p id="error"></p>');document.querySelector('#error').textContent=e.message;console.error(e);});
