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
const state={yaw:rad(-35),pitch:rad(28),distance:8.4,time:0,motion:true,variant:1,clip:"idle",compare:false,hands:false,glow:true};
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
function pose(variant,time,clip){
 const result={};
 for(const bone of variant.bones){
  const channels=variant.clips[clip].bones[bone.name]||{},r=channels.rotation?sample(channels.rotation,time):[0,0,0],p=channels.position?sample(channels.position,time):[0,0,0];
  let local=translate(p);
  local=M.mul(local,translate(bone.pivot));
  // GeckoLib X/Y signs, followed by parent-space transforms.
  for(const axis of [2,1,0])local=M.mul(local,rotate(axis,rad(r[axis])*(axis===2?1:-1)));
  const scale=channels.scale?sample(channels.scale,time):[1,1,1],sm=M.id();sm[0]=scale[0];sm[5]=scale[1];sm[10]=scale[2];local=M.mul(local,sm);
  local=M.mul(local,translate(bone.pivot.map(v=>-v)));
  result[bone.name]=bone.parent?M.mul(result[bone.parent],local):local;
 }
 return result;
}
function putFace(data,face,uvs,point,normal){for(const k of [0,1,2,0,2,3])data.push(...point(face.p[k]),...normal(face.n),...uvs[k]);}
function modelMesh(variant,time,clip,offset=0){
 const data=[],matrices=pose(variant,time,clip);
 function place(p,normal=false){return normal?p:[p[0]/16+offset,p[1]/16,p[2]/16];}
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
 for(const index of state.compare?[0,1,2]:[state.variant]){
 const v=variants[index],clip=state.compare?'idle':state.clip;
 const data=modelMesh(v,state.compare?time%4:time,clip,state.compare?[1.85,0,-1.85][index]:0);submit(data,v.texture);
 if(state.glow){gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE);gl.depthMask(false);submit(data,v.glow,[1,1,1,1],true);gl.depthMask(true);gl.disable(gl.BLEND);}
 }

}
function draw(){
 const dpr=Math.min(devicePixelRatio,2),w=Math.round(canvas.clientWidth*dpr),h=Math.round(canvas.clientHeight*dpr);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
 gl.viewport(0,0,w,h);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.disable(gl.CULL_FACE);
 const crawler=state.variant===0&&!state.compare;
 const rootMotion=variants[state.variant].clips[state.clip].bones.root?.position;
 const follow=!state.compare&&rootMotion?sample(rootMotion,state.time)[2]/16:0;
 let at=[0,crawler?.5:state.compare?1.0:state.variant===2?1.1:1.0,(crawler?-.15:0)+follow];
 if(state.hands&&!state.compare){const mats=pose(variants[state.variant],state.time,state.clip);const a=transform(mats.left_hand,[0,0,0]),b=transform(mats.right_hand,[0,0,0]);at=a.map((v,i)=>(v+b[i])/32);}
 const d=(state.hands&&!state.compare?(state.variant===0?1.65:2.05):state.compare?7.2:state.distance*.5)/Math.min(1,w/h),eye=[at[0]+Math.sin(state.yaw)*Math.cos(state.pitch)*d,at[1]+Math.sin(state.pitch)*d,at[2]-Math.cos(state.yaw)*Math.cos(state.pitch)*d];

 gl.useProgram(pipeline);gl.bindVertexArray(vao);gl.uniformMatrix4fv(uniforms.vp,false,M.mul(M.perspective(rad(38),w/h,.01,50),M.lookAt(eye,at)));gl.activeTexture(gl.TEXTURE0);gl.uniform1i(uniforms.tex,0);scene(state.time);gl.bindVertexArray(null);
 document.querySelector('#timeline').value=state.time;
 document.querySelector('#phase').textContent=state.compare?'Same scale · Crawler / Archer / Brute':state.clip==='death'?(state.variant===0?(state.time<.2?'Jolt':state.time<.9?'Collapse':'Still'):(state.time<.15?'Recoil':state.time<.6?'Knees buckle':state.time<1.2?'Topple':'Still')):state.variant===0?(state.clip==='attack'?(state.time<.44?'Rear up':state.time<.62?'Claw rake':'Recover'):state.clip==='walk'?'Dragging':state.clip==='rise'?(state.time<.75?'Claws break through':'Hauling free'):'Idle'):state.clip==='attack'?(state.variant===1?(state.time<.65?'Load arrow':state.time<2?'Extend & draw':state.time<2.6?'Aim':state.time<2.95?'Release':'Lower bow'):(state.time<1.7?'Heavy windup':state.time<2.6?'Downward strike':'Recover')):state.clip==='walk'?'Walking':state.clip==='rise'?(state.time<.9?'Emerging':state.time<2.6?'Hauling upright':'Standing'):'Idle';
}
function sync(){const b=document.querySelector('#motion');b.textContent=state.motion?'Pause':'Play';b.classList.toggle('active',state.motion);}
function controls(){
 let drag=null;canvas.onpointerdown=e=>{drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId);canvas.classList.add('dragging');};
 canvas.onpointermove=e=>{if(!drag)return;state.yaw-=(e.clientX-drag[0])*.008;state.pitch=Math.max(.05,Math.min(1.5,state.pitch+(e.clientY-drag[1])*.008));drag=[e.clientX,e.clientY];document.querySelectorAll('[data-view]').forEach(b=>b.classList.remove('active'));};
 const release=()=>{drag=null;canvas.classList.remove('dragging');};canvas.onpointerup=release;canvas.onpointercancel=release;
 canvas.onwheel=e=>{e.preventDefault();state.distance=Math.max(3,Math.min(14,state.distance*Math.exp(e.deltaY*.0012)));};
 const views={hero:[-35,28],front:[0,8],top:[-15,78],side:[90,12]};document.querySelectorAll('[data-view]').forEach(b=>b.onclick=()=>{[state.yaw,state.pitch]=views[b.dataset.view].map(rad);document.querySelectorAll('[data-view]').forEach(x=>x.classList.toggle('active',x===b));});
 document.querySelector('#motion').onclick=()=>{state.motion=!state.motion;sync();};document.querySelector('#replay').onclick=()=>{state.time=0;state.motion=true;sync();};
 document.querySelectorAll('[data-creature]').forEach(b=>b.onclick=()=>selectCreature(+b.dataset.creature));
 document.querySelectorAll('[data-clip]').forEach(b=>b.onclick=()=>{state.clip=b.dataset.clip;state.time=0;state.motion=true;sync();updatePanel();});
 document.querySelector('#compare').onclick=()=>{state.compare=!state.compare;if(state.compare){state.yaw=0;state.pitch=rad(14);}updatePanel();};
 document.querySelector('#timeline').oninput=e=>{state.time=+e.target.value;state.motion=false;sync();};
 document.querySelector('#hands').onclick=e=>{state.hands=!state.hands;e.currentTarget.classList.toggle('active',state.hands);};
 document.querySelector('#glow').onclick=e=>{state.glow=!state.glow;e.currentTarget.classList.toggle('active',state.glow);};
}
const descriptions=[
 {name:'Hollow Crawler',text:'The approved legless skeleton. Slender arms pull the rib cage forward while the pelvis trails along the ground.',silhouette:'Human skull, open ribs, and outward-bending elbows.',motion:'Idle breathing and a restless claw; the crawl pulls with staggered finger grips; rise bursts both claws out of the ground first; the claw lunge rears up and rakes down; death jolts, collapses and rolls.'},
 {name:'Risen Archer',text:'A lean skeleton in the remnants of a burial hood. A worn wooden bow and a small quiver keep its ranged role readable.',silhouette:'Upright anatomy, exposed ribs, narrow limbs, and frayed olive-gray cloth.',motion:'Inspect the measured walk, then Draw & fire: the arrow is loaded, the bow arm extends, and three fingers draw to the cheek before releasing. On death it drops the bow, buckles and topples face down.'},
 {name:'Hunched Brute',text:'A broad, stooped skeleton with an uneven spine, one heavier arm, a worn club, and a torn burial mantle.',silhouette:'Wider ribs and pelvis, a forward-set skull, old iron bindings, and a worn wooden club.',motion:'An uneven walk favors one leg. The strike has a long windup, a forceful downward swing, and a slow recovery. On death it buckles and topples, the club rolling out beside its arm.'}
];
function selectCreature(index){state.variant=index;state.clip='idle';state.time=0;state.compare=false;state.hands=false;document.querySelector('#hands').classList.remove('active');state.distance=[5.8,8.4,9.5][index];state.motion=true;state.pitch=rad(index===0?28:14);sync();updatePanel();}
function updatePanel(){
 const d=descriptions[state.variant];document.querySelector('h1').textContent=state.compare?'The Hollow Army':d.name;
 document.querySelector('#creature-title').textContent=state.compare?'The Hollow Army':d.name;document.querySelector('#description').textContent=state.compare?'Crawler, archer, and brute at the same scale. Choose a creature to inspect its model and animations.':d.text;
 document.querySelector('#silhouette').textContent=state.compare?'A low crawler, a lean upright archer, and a broader stooped brute.':d.silhouette;document.querySelector('#movement').textContent=state.compare?'Select a creature for Idle, Walk, Rise, its attack, and Death.':d.motion;
 document.querySelector('#timeline').max=variants[state.variant].clips[state.clip].animation_length;
 document.querySelectorAll('[data-creature]').forEach(b=>b.classList.toggle('active',+b.dataset.creature===state.variant&&!state.compare));
 document.querySelectorAll('[data-clip]').forEach(b=>{b.hidden=state.compare;b.classList.toggle('active',b.dataset.clip===state.clip);if(b.dataset.clip==='attack')b.textContent=['Claw lunge','Draw & fire','Club strike'][state.variant];if(b.dataset.clip==='walk')b.textContent=state.variant===0?'Crawl':'Walk';});
 document.querySelector('#compare').classList.toggle('active',state.compare);
 canvas.setAttribute('aria-label',state.compare?'Three skeletal creatures at the same scale':'Animated '+d.name);
}
async function main(){
 if(!gl)throw Error('WebGL 2 is unavailable');
 pipeline=gl.createProgram();gl.attachShader(pipeline,shader(gl.VERTEX_SHADER,vertex));gl.attachShader(pipeline,shader(gl.FRAGMENT_SHADER,fragment));gl.linkProgram(pipeline);if(!gl.getProgramParameter(pipeline,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(pipeline));
 uniforms=Object.fromEntries(['vp','tex','tint','emissive'].map(k=>[k,gl.getUniformLocation(pipeline,k)]));
 vao=gl.createVertexArray();buffer=gl.createBuffer();gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,buffer);for(const [name,size,off]of [['pos',3,0],['normal',3,3],['uv',2,6]]){const loc=gl.getAttribLocation(pipeline,name);gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,size,gl.FLOAT,false,32,off*4);}gl.bindVertexArray(null);
 const pixel=document.createElement('canvas');pixel.width=pixel.height=1;pixel.getContext('2d').fillStyle='white';pixel.getContext('2d').fillRect(0,0,1,1);white=upload(pixel);
 variants=await Promise.all(payload.variants.map(async v=>{const g=v.geo['minecraft:geometry'][0],skin=await loadImage(v.images[v.stem+'.png']),glow=await loadImage(v.images[v.stem+'_glowmask.png']);return {bones:g.bones,tw:g.description.texture_width,th:g.description.texture_height,clips:v.clips,texture:upload(skin),glow:upload(glow)};}));
 controls();updatePanel();document.body.dataset.ready='true';let previous=performance.now();function frame(now){const dt=Math.min(.1,(now-previous)/1000);previous=now;if(state.motion){state.time+=dt;const duration=state.compare?4:variants[state.variant].clips[state.clip].animation_length;if(state.time>duration){if(!state.compare&&!variants[state.variant].clips[state.clip].loop){state.time=duration;state.motion=false;sync();}else state.time=state.time-duration;}}draw();requestAnimationFrame(frame);}requestAnimationFrame(frame);
}
main().catch(e=>{document.body.dataset.error=e.message;document.querySelector('aside').insertAdjacentHTML('afterbegin','<p id="error"></p>');document.querySelector('#error').textContent=e.message;console.error(e);});
