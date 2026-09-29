// Review-only WebGL scene. The character uses current GeckoLib runtime assets;
// the braided soul stream is proposed art and is not installed in Minecraft.
'use strict';
const $=s=>document.querySelector(s), canvas=$('#view'),gl=canvas.getContext('webgl2',{antialias:true,alpha:false,preserveDrawingBuffer:true});
const vignette=$('#vignette'),vctx=vignette.getContext('2d');
vctx.imageSmoothingEnabled=false;
const rad=d=>d*Math.PI/180,clamp=(v,a=0,b=1)=>Math.max(a,Math.min(b,v)),mix=(a,b,t)=>a+(b-a)*t,TAU=Math.PI*2;
const hash=n=>{const x=Math.sin(n*127.1+39.42)*43758.5453;return x-Math.floor(x);};
const add=(a,b)=>a.map((v,i)=>v+b[i]),sub=(a,b)=>a.map((v,i)=>v-b[i]),scale=(a,s)=>a.map(v=>v*s);
const dot=(a,b)=>a[0]*b[0]+a[1]*b[1]+a[2]*b[2],cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const norm=a=>{const n=Math.hypot(...a)||1;return a.map(v=>v/n);};
const M={
 id:()=>new Float32Array([1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]),
 mul(a,b){const o=new Float32Array(16);for(let c=0;c<4;c++)for(let r=0;r<4;r++)for(let k=0;k<4;k++)o[c*4+r]+=a[k*4+r]*b[c*4+k];return o;},
 t(x,y,z){const m=M.id();m[12]=x;m[13]=y;m[14]=z;return m;},
 s(x,y,z){const m=M.id();m[0]=x;m[5]=y;m[10]=z;return m;},
 rx(a){const c=Math.cos(a),s=Math.sin(a);return new Float32Array([1,0,0,0,0,c,s,0,0,-s,c,0,0,0,0,1]);},
 ry(a){const c=Math.cos(a),s=Math.sin(a);return new Float32Array([c,0,-s,0,0,1,0,0,s,0,c,0,0,0,0,1]);},
 rz(a){const c=Math.cos(a),s=Math.sin(a);return new Float32Array([c,s,0,0,-s,c,0,0,0,1,0,0,0,0,0,1]);},
 apply(m,v){return [m[0]*v[0]+m[4]*v[1]+m[8]*v[2]+m[12],m[1]*v[0]+m[5]*v[1]+m[9]*v[2]+m[13],m[2]*v[0]+m[6]*v[1]+m[10]*v[2]+m[14]];},
 dir(m,v){return [m[0]*v[0]+m[4]*v[1]+m[8]*v[2],m[1]*v[0]+m[5]*v[1]+m[9]*v[2],m[2]*v[0]+m[6]*v[1]];},
 perspective(fov,aspect,near,far){const f=1/Math.tan(fov/2),m=new Float32Array(16);m[0]=f/aspect;m[5]=f;m[10]=(far+near)/(near-far);m[11]=-1;m[14]=2*far*near/(near-far);return m;},
 lookAt(eye,at){const z=norm(sub(eye,at)),x=norm(cross([0,1,0],z)),y=cross(z,x);return new Float32Array([x[0],y[0],z[0],0,x[1],y[1],z[1],0,x[2],y[2],z[2],0,-dot(x,eye),-dot(y,eye),-dot(z,eye),1]);}
};

// Keep box UVs, pivots and rotation signs aligned with the Necromancer workshop.
function bakeModel(geo){
 const g=geo['minecraft:geometry'][0],d=g.description,byName=new Map(g.bones.map(b=>[b.name,b]));
 const order=[],seen=new Set(),visit=b=>{if(seen.has(b.name))return;if(b.parent&&byName.has(b.parent))visit(byName.get(b.parent));seen.add(b.name);order.push(b);};
 g.bones.forEach(visit);
 const bones=order.map(b=>{const p=b.pivot||[0,0,0],r=b.rotation||[0,0,0];return {name:b.name,parent:b.parent||null,pivot:[-p[0]/16,p[1]/16,p[2]/16],rest:[rad(-r[0]),rad(-r[1]),rad(r[2])],cubes:b.cubes||[]};});
 const faces=[];
 bones.forEach((bone,boneIndex)=>bone.cubes.forEach(c=>{
  const s=c.size,o=c.origin,inf=(c.inflate||0)/16,ox=-(o[0]+s[0])/16,oy=o[1]/16,oz=o[2]/16,sx=s[0]/16,sy=s[1]/16,sz=s[2]/16;
  const V={blb:[ox-inf,oy-inf,oz-inf],brb:[ox-inf,oy-inf,oz+sz+inf],tlb:[ox-inf,oy+sy+inf,oz-inf],trb:[ox-inf,oy+sy+inf,oz+sz+inf],tlf:[ox+sx+inf,oy+sy+inf,oz-inf],trf:[ox+sx+inf,oy+sy+inf,oz+sz+inf],blf:[ox+sx+inf,oy-inf,oz-inf],brf:[ox+sx+inf,oy-inf,oz+sz+inf]};
  const quads={west:['trb','tlb','blb','brb'],east:['tlf','trf','brf','blf'],north:['tlb','tlf','blf','blb'],south:['trf','trb','brb','brf'],up:['trb','trf','tlf','tlb'],down:['blb','blf','brf','brb']};
  const normals={west:[-1,0,0],east:[1,0,0],north:[0,0,-1],south:[0,0,1],up:[0,1,0],down:[0,-1,0]};
  let cubeMatrix=null;
  if(c.rotation&&c.rotation.some(v=>v)){const cp=c.pivot||[0,0,0],pv=[-cp[0]/16,cp[1]/16,cp[2]/16];cubeMatrix=[M.t(...pv),M.rz(rad(c.rotation[2])),M.ry(rad(-c.rotation[1])),M.rx(rad(-c.rotation[0])),M.t(-pv[0],-pv[1],-pv[2])].reduce(M.mul);}
  const [fw,fh,fd]=s.map(Math.floor);
  for(const dir of ['west','east','north','south','up','down']){
   let u,v,us,vs;
   if(Array.isArray(c.uv)){const [bu,bv]=c.uv;[u,v,us,vs]={west:[bu+fd+fw,bv+fd,fd,fh],east:[bu,bv+fd,fd,fh],north:[bu+fd,bv+fd,fw,fh],south:[bu+fd+fw+fd,bv+fd,fw,fh],up:[bu+fd,bv,fw,fd],down:[bu+fd+fw,bv+fd,fw,-fd]}[dir];}
   else{const f=c.uv&&c.uv[dir];if(!f)continue;[u,v]=f.uv;[us,vs]=f.uv_size;}
   let pts=quads[dir].map(k=>V[k]),n=normals[dir];if(cubeMatrix){pts=pts.map(p=>M.apply(cubeMatrix,p));n=M.dir(cubeMatrix,n);}
   if(Math.hypot(...cross(sub(pts[1],pts[0]),sub(pts[2],pts[0])))<1e-9||us===0||vs===0)continue;
   faces.push({bone:boneIndex,pts,n,uvs:[[(u+us)/d.texture_width,v/d.texture_height],[u/d.texture_width,v/d.texture_height],[u/d.texture_width,(v+vs)/d.texture_height],[(u+us)/d.texture_width,(v+vs)/d.texture_height]]});
  }
 }));return {bones,faces};
}
const keyCache=new WeakMap();
function vec(v,f){if(typeof v==='number')return [v,v,v];if(Array.isArray(v))return v.map((x,i)=>typeof x==='number'?x:f[i]);return f;}
function sample(ch,t,f){
 if(ch===undefined)return f;if(Array.isArray(ch)||typeof ch==='number')return vec(ch,f);
 let keys=keyCache.get(ch);if(!keys){keys=Object.entries(ch).map(([k,v])=>({t:+k,pre:vec(v.pre??v.post??v,f),post:vec(v.post??v.pre??v,f)})).sort((a,b)=>a.t-b.t);keyCache.set(ch,keys);}
 if(t<=keys[0].t)return keys[0].post;
 for(let i=1;i<keys.length;i++)if(t<=keys[i].t){const a=keys[i-1],b=keys[i],q=(t-a.t)/(b.t-a.t||1);return a.post.map((v,j)=>mix(v,b.pre[j],q));}
 return keys.at(-1).post;
}
function poseBones(model,clip,t){
 const matrices=[],hidden=[],byName={};model.bones.forEach((bone,i)=>{
  const ch=clip.bones?.[bone.name]||{},r=sample(ch.rotation,t,[0,0,0]),p=sample(ch.position,t,[0,0,0]),s=sample(ch.scale,t,[1,1,1]),pv=bone.pivot;
  const rot=[bone.rest[0]+rad(-r[0]),bone.rest[1]+rad(-r[1]),bone.rest[2]+rad(r[2])];
  let m=[M.t(-p[0]/16,p[1]/16,p[2]/16),M.t(...pv),M.rz(rot[2]),M.ry(rot[1]),M.rx(rot[0]),M.s(...s),M.t(-pv[0],-pv[1],-pv[2])].reduce(M.mul);
  const parent=bone.parent!=null?byName[bone.parent]:undefined;if(parent!==undefined)m=M.mul(matrices[parent],m);
  matrices[i]=m;hidden[i]=(parent!==undefined&&hidden[parent])||bone.name==='colossus';byName[bone.name]=i;
 });return {matrices,hidden};
}

const MODEL_VS=`#version 300 es
in vec3 pos;in vec3 normal;in vec2 uv;uniform mat4 vp;out vec3 vNormal;out vec2 vUv;
void main(){gl_Position=vp*vec4(pos,1.);vNormal=normal;vUv=uv;}`;
const MODEL_FS=`#version 300 es
precision highp float;in vec3 vNormal;in vec2 vUv;out vec4 color;uniform sampler2D tex;uniform bool emissive;
void main(){vec4 p=texture(tex,vUv);if(p.a<.1)discard;if(emissive){color=vec4(p.rgb,1.);return;}
vec3 n=normalize(gl_FrontFacing?vNormal:-vNormal);
float shade=min(1.,.42+.58*(max(0.,dot(n,normalize(vec3(.25,1.,-.65))))+max(0.,dot(n,normalize(vec3(-.2,1.,.65))))));
color=vec4(p.rgb*shade*vec3(.72,.83,1.),1.);}`;
const SOLID_VS=`#version 300 es
in vec3 pos;in vec3 normal;in vec4 col;uniform mat4 vp;out vec3 vNormal;out vec4 vColor;
void main(){gl_Position=vp*vec4(pos,1.);vNormal=normal;vColor=col;}`;
const SOLID_FS=`#version 300 es
precision highp float;in vec3 vNormal;in vec4 vColor;out vec4 color;uniform bool emissive;
void main(){vec3 n=normalize(gl_FrontFacing?vNormal:-vNormal);float shade=emissive?1.:min(1.,.48+.52*max(0.,dot(n,normalize(vec3(.3,1.,-.5)))));color=vec4(vColor.rgb*shade,vColor.a);}`;
function program(vs,fs){const p=gl.createProgram();for(const [type,src] of [[gl.VERTEX_SHADER,vs],[gl.FRAGMENT_SHADER,fs]]){const s=gl.createShader(type);gl.shaderSource(s,src);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));gl.attachShader(p,s);}gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;}
function mesh(prog,layout,stride){const vao=gl.createVertexArray(),buffer=gl.createBuffer();gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,buffer);let offset=0;for(const [name,size] of layout){const loc=gl.getAttribLocation(prog,name);gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,size,gl.FLOAT,false,stride*4,offset*4);offset+=size;}gl.bindVertexArray(null);return {vao,buffer,count:0,stride};}
function upload(mesh,data){gl.bindBuffer(gl.ARRAY_BUFFER,mesh.buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(data),gl.DYNAMIC_DRAW);mesh.count=data.length/mesh.stride;}
function texture(image){const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);for(const p of [gl.TEXTURE_MIN_FILTER,gl.TEXTURE_MAG_FILTER])gl.texParameteri(gl.TEXTURE_2D,p,gl.NEAREST);for(const p of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T])gl.texParameteri(gl.TEXTURE_2D,p,gl.CLAMP_TO_EDGE);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,image);return t;}
const image=url=>new Promise((ok,no)=>{const im=new Image();im.onload=()=>ok(im);im.onerror=()=>no(Error(url+' failed to load'));im.src=url;});
const json=async url=>{const r=await fetch(url);if(!r.ok)throw Error(url+' returned '+r.status);return r.json();};

let modelProgram,solidProgram,modelMesh,solidMesh,effectMesh,skin,glow,model,drain;
const state={time:1.55,playing:true,spacing:55,cam:{yaw:rad(-25),pitch:rad(17),distance:9.3},view:'hero'};
const bossMatrix=M.mul(M.t(-2.25,0,.6),M.ry(rad(-30)));
const C={floor:[.16,.2,.23,1],stone:[.21,.27,.30,1],grout:[.10,.15,.18,1],shirt:[.24,.46,.51,1],
 shirtLight:[.34,.57,.60,1],pants:[.24,.28,.43,1],skin:[.73,.63,.51,1],hair:[.25,.22,.2,1],
 deep:[.035,.14,.17,.65],teal:[.08,.36,.39,.68],mid:[.16,.55,.59,.72],bright:[.31,.76,.79,.77],pale:[.67,.98,1,.9]};
function box(data,center,size,col){
 const lo=center.map((v,i)=>v-size[i]/2),hi=center.map((v,i)=>v+size[i]/2);
 const [x0,y0,z0]=lo,[x1,y1,z1]=hi;
 const quads=[[[x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0],[0,0,-1]],
  [[x1,y0,z1],[x0,y0,z1],[x0,y1,z1],[x1,y1,z1],[0,0,1]],
  [[x0,y0,z1],[x0,y0,z0],[x0,y1,z0],[x0,y1,z1],[-1,0,0]],
  [[x1,y0,z0],[x1,y0,z1],[x1,y1,z1],[x1,y1,z0],[1,0,0]],
  [[x0,y1,z0],[x1,y1,z0],[x1,y1,z1],[x0,y1,z1],[0,1,0]],
  [[x0,y0,z1],[x1,y0,z1],[x1,y0,z0],[x0,y0,z0],[0,-1,0]]];
 for(const q of quads)for(const k of [0,1,2,0,2,3])data.push(...q[k],...q[4],...col);
}
function worldScene(playerX){
 const data=[];
 for(let x=-7;x<=7;x++)for(let z=-5;z<=5;z++){
  box(data,[x,-.08,z],[.98,.16,.98],(x+z)%2?C.floor:C.stone);
  if(hash(x*17+z*23)>.72)box(data,[x+.15,.008,z-.19],[.18,.014,.025],C.grout);
 }
 // Sparse crypt pillars add depth without obscuring the channel.
 for(const x of [-5.7,5.7]){
  box(data,[x,1.1,2.2],[.5,2.2,.5],[.19,.23,.27,1]);
  box(data,[x,2.25,2.2],[.72,.22,.72],[.27,.31,.34,1]);
 }
 if(state.view!=='player'){
  const x=playerX,z=-.7;
  box(data,[x,1.75,z],[.5,.5,.5],C.skin);box(data,[x,1.98,z-.025],[.52,.12,.54],C.hair);
  box(data,[x-.13,1.76,z-.256],[.07,.055,.025],C.grout);box(data,[x+.13,1.76,z-.256],[.07,.055,.025],C.grout);
  box(data,[x,1.12,z],[.52,.75,.29],C.shirt);box(data,[x,1.46,z-.157],[.52,.09,.02],C.shirtLight);
  box(data,[x-.375,1.13,z],[.24,.72,.27],C.skin);box(data,[x+.375,1.13,z],[.24,.72,.27],C.skin);
  box(data,[x-.13,.38,z],[.24,.77,.27],C.pants);box(data,[x+.13,.38,z],[.24,.77,.27],C.pants);
 }
 upload(solidMesh,data);
}
function modelScene(pose){
 const data=[];
 for(const f of model.faces){if(pose.hidden[f.bone])continue;
  const matrix=M.mul(bossMatrix,pose.matrices[f.bone]),points=f.pts.map(v=>M.apply(matrix,v)),n=norm(M.dir(matrix,f.n));
  for(const k of [0,1,2,0,2,3])data.push(...points[k],...n,...f.uvs[k]);
 }
 upload(modelMesh,data);
}
function handPosition(pose,name){
 const index=model.bones.findIndex(b=>b.name===name);
 const faces=model.faces.filter(f=>f.bone===index),matrix=M.mul(bossMatrix,pose.matrices[index]);
 const points=faces.flatMap(f=>f.pts).map(p=>M.apply(matrix,p));
 return points.reduce((sum,p)=>add(sum,p),[0,0,0]).map(v=>v/points.length);
}
function streamCurve(u,src,dst){
 const p=src.map((v,i)=>mix(v,dst[i],u));p[1]+=.30*Math.sin(Math.PI*u);p[2]+=.14*Math.sin(Math.PI*u);return p;
}
function basis(u,src,dst){
 const tangent=norm(sub(streamCurve(clamp(u+.01),src,dst),streamCurve(clamp(u-.01),src,dst)));
 const side=norm(cross(tangent,[0,1,0])),up=norm(cross(side,tangent));return {side,up};
}
function ring(data,center,radius,axis,amount){
 const right=norm(cross(axis,[0,1,0])),up=norm(cross(right,axis));
 for(let i=0;i<18;i++){
  if(i%7===3)continue;const a=i*TAU/18,p=add(center,add(scale(right,Math.cos(a)*radius),scale(up,Math.sin(a)*radius)));
  box(data,p,[.055,.055,.055],[.35,.83,.84,.7*amount]);
 }
}
const nearFade=u=>state.view==='player'?clamp((u-.35)/.5):1;
function effectScene(t,playerX,hand){
 const data=[],src=[playerX,1.22,-.7],dst=add(hand,[0,.4,0]),windup=clamp(t/.7),breaking=clamp((t-4.2)/.75),active=t>=.7&&t<4.2;
 const pulse=active?Math.pow(1-((t-.7)%.5)/.5,5):0;
 if(state.view!=='player')ring(data,src,.30,norm(sub(dst,src)),t<.7?windup:.72*(1-breaking));
 ring(data,dst,.17,norm(sub(src,dst)),t<.7?windup:.7*(1-breaking));
 if(state.view!=='player')box(data,src,[.12,.12,.12],[.35,.83,.84,.38+.28*windup]);
 box(data,dst,[.12,.12,.12],[.67,.98,1,.38+.32*pulse]);
 for(let i=1;i<=5;i++){
  const point=dst.map((v,k)=>mix(v,hand[k],i/6));
  box(data,point,[.038,.038,.038],[.31,.76,.79,.5*(1-breaking)]);
 }
 if(t<.7){
  for(let i=0;i<20;i++){const u=i/20,p=streamCurve(u,src,dst);
   box(data,add(p,[(hash(i*7)-.5)*.15,(hash(i*11)-.5)*.15,0]),[.035,.035,.035],[.1,.43,.46,.2*windup]);}
  upload(effectMesh,data);return;
 }
 const grow=clamp((t-.7)/.24),count=Math.floor(82*grow);
 for(let i=0;i<=count;i++){
  const u=i/82,p=streamCurve(u,src,dst),{side,up}=basis(u,src,dst),jitter=breaking*.32;
  const viewAlpha=nearFade(u);
  if(viewAlpha<.02)continue;
  const scatter=[(hash(i*17)-.5)*jitter,(hash(i*23+3)-.5)*jitter,(hash(i*31+7)-.5)*jitter];
  if(breaking&&hash(i*19)<breaking*.55)continue;
  box(data,add(p,scatter),[.10,.10,.10],[.035,.14,.17,.42*(1-breaking)*viewAlpha]);
  for(let strand=0;strand<2;strand++){
   const a=u*TAU*3.1-t*3.5+strand*Math.PI;
   const radial=add(scale(side,Math.cos(a)*.14),scale(up,Math.sin(a)*.14));
   const pos=add(add(p,radial),scatter),shade=strand?C.teal:C.mid,fade=1-breaking*.8;
   box(data,pos,[.105,.105,.105],[...shade.slice(0,3),shade[3]*fade*viewAlpha]);
   if(i%4<2)box(data,add(pos,[0,.018,0]),[.052,.055,.052],[strand?.19:.35,strand?.64:.83,strand?.67:.84,.65*fade*viewAlpha]);
   if(i%17===4)box(data,add(pos,scale(side,.045)),[.045,.045,.045],[.67,.98,1,.7*fade*viewAlpha]);
  }
 }
 if(active){
  for(let i=0;i<16;i++){
   const u=((t-.7)*.43+i/16)%1,p=streamCurve(u,src,dst),{side,up}=basis(u,src,dst);
   const col=i%4===0?C.pale:C.bright,visibility=nearFade(u);
   if(visibility>.02)box(data,add(p,add(scale(side,(hash(i*13)-.5)*.3),scale(up,(hash(i*31)-.5)*.3))),[.045,.045,.045],[...col.slice(0,3),col[3]*visibility]);
  }
  const u=((t-.7)%.5)/.5,p=streamCurve(u,src,dst);
  const pulseAlpha=nearFade(u);
  if(pulseAlpha>.02){box(data,p,[.22,.19,.19],[.19,.64,.67,.55*pulseAlpha]);box(data,p,[.13,.13,.13],[...C.pale.slice(0,3),C.pale[3]*pulseAlpha]);}
  for(let i=0;i<6;i++){
   const q=streamCurve(clamp(u-i*.015),src,dst);
   const visibility=nearFade(u-i*.015);
   if(visibility>.02)box(data,add(q,[(hash(i*11)-.5)*.14,(hash(i*7)-.5)*.14,0]),[.06,.06,.06],[...C.bright.slice(0,3),C.bright[3]*visibility]);
  }
 }else{
  for(let i=0;i<28;i++){
   const u=hash(i*8+2),p=streamCurve(u,src,dst),spread=breaking*.55;
   box(data,add(p,[(hash(i*17)-.5)*spread,(hash(i*19+8)-.5)*spread,(hash(i*23)-.5)*spread]),[.05,.05,.05],[.35,.83,.84,.8*(1-breaking)*nearFade(u)]);
  }
 }
 upload(effectMesh,data);
}

function camera(playerX){
 let eye,target;
 if(state.view==='player'){
  eye=[playerX,1.62,-.9];target=[-2.2,1.5,.5];
 }else{
  target=[.1,1.25,0];const {yaw,pitch,distance}=state.cam;
  eye=[target[0]+Math.sin(yaw)*Math.cos(pitch)*distance,target[1]+Math.sin(pitch)*distance,target[2]-Math.cos(yaw)*Math.cos(pitch)*distance];
 }
 return M.mul(M.perspective(rad(state.view==='player'?63:41),canvas.width/canvas.height,.03,80),M.lookAt(eye,target));
}
function drawVignette(t){
 const targetWidth=Math.max(1,Math.round(vignette.clientWidth*Math.min(devicePixelRatio,1.5)));
 const targetHeight=Math.max(1,Math.round(targetWidth*9/16));
 if(vignette.width!==targetWidth||vignette.height!==targetHeight){vignette.width=targetWidth;vignette.height=targetHeight;}
 const w=vignette.width,h=vignette.height;
 vctx.clearRect(0,0,w,h);
 if(state.view!=='player')return;
 const breaking=clamp((t-4.2)/.75);
 const windup=clamp(t/.7);
 const phase=t>=.7&&t<4.2?(t-.7)%.5:.25;
 const pulse=t>=.7&&t<4.2?Math.exp(-Math.pow(Math.min(phase,.5-phase)/.075,2)):0;
 const strength=(t<.7?.22+.50*windup:.72)*(1-breaking);
 if(strength<=0)return;
 // Smooth shadow falloff keeps the caster readable inside a cold, closing edge.
 vctx.save();
 vctx.translate(w/2,h/2);vctx.scale(w/2,h/2);
 const shadow=vctx.createRadialGradient(0,0,.28,0,0,1.28);
 shadow.addColorStop(0,'rgba(1,5,12,0)');
 shadow.addColorStop(.4,`rgba(2,8,16,${.10*strength})`);
 shadow.addColorStop(.72,`rgba(2,7,14,${.60*strength})`);
 shadow.addColorStop(1,`rgba(1,4,10,${.94*strength})`);
 vctx.fillStyle=shadow;vctx.fillRect(-1,-1,2,2);
 vctx.restore();
 // Overlapping elliptical clouds drift unevenly, avoiding a regular glowing ring.
 for(let i=0;i<22;i++){
  const a=i*TAU/22+Math.sin(t*.31+i*2.7)*.07;
  const reach=.96+Math.sin(t*.6+i*1.9)*.07+breaking*.28;
  const x=w/2+Math.cos(a)*w*.51*reach,y=h/2+Math.sin(a)*h*.53*reach;
  const size=.16+hash(i*37)*.12;
  vctx.save();vctx.translate(x,y);vctx.rotate(a+.7*Math.sin(i));
  vctx.scale(w*size,h*size*.85);
  const fog=vctx.createRadialGradient(0,0,0,0,0,1);
  const alpha=strength*(.32+.14*pulse)*(.6+.4*Math.sin(i*3+t*.7)**2);
  fog.addColorStop(0,`rgba(58,126,137,${alpha})`);
  fog.addColorStop(.4,`rgba(29,76,91,${alpha*.7})`);
  fog.addColorStop(1,'rgba(8,22,37,0)');
  vctx.fillStyle=fog;vctx.fillRect(-1,-1,2,2);vctx.restore();
 }
 // Thin, blurred tendrils curl inward from the periphery like passing apparitions.
 vctx.save();vctx.lineCap='round';vctx.filter=`blur(${Math.max(1,w*.0025)}px)`;
 for(let i=0;i<12;i++){
  const a=i*TAU/12+.15*Math.sin(i*7),sway=Math.sin(t*.8+i*2)*.10;
  const point=(r,turn)=>[w/2+Math.cos(a+turn)*w*.56*r,h/2+Math.sin(a+turn)*h*.60*r];
  const start=point(1.15+breaking*.2,0),c1=point(.95,sway+.13),c2=point(.73,sway-.10),end=point(.78,sway-.23);
  const alpha=strength*(.15+.09*pulse)*(.5+.5*Math.sin(t*.9+i)**2);
  vctx.strokeStyle=`rgba(113,164,169,${alpha})`;vctx.lineWidth=w*(.005+hash(i*19)*.005);
  vctx.beginPath();vctx.moveTo(...start);vctx.bezierCurveTo(...c1,...c2,...end);vctx.stroke();
 }
 vctx.restore();
}
function render(){
 if(!model)return;
 const dpr=Math.min(devicePixelRatio,2),w=Math.max(1,Math.round(canvas.clientWidth*dpr)),h=Math.max(1,Math.round(canvas.clientHeight*dpr));
 if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
 const playerX=2.65+(state.spacing-55)*.024,pose=poseBones(model,drain,Math.min(state.time,4.2));
 const leftHand=handPosition(pose,'mage_hand_left');
 worldScene(playerX);modelScene(pose);effectScene(state.time,playerX,leftHand);
 const vp=camera(playerX);
 gl.viewport(0,0,w,h);gl.clearColor(.025,.045,.075,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
 gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.disable(gl.CULL_FACE);
 gl.useProgram(solidProgram);gl.bindVertexArray(solidMesh.vao);
 gl.uniformMatrix4fv(gl.getUniformLocation(solidProgram,'vp'),false,vp);
 gl.uniform1i(gl.getUniformLocation(solidProgram,'emissive'),false);
 gl.drawArrays(gl.TRIANGLES,0,solidMesh.count);
 gl.useProgram(modelProgram);gl.bindVertexArray(modelMesh.vao);
 gl.uniformMatrix4fv(gl.getUniformLocation(modelProgram,'vp'),false,vp);
 gl.activeTexture(gl.TEXTURE0);gl.uniform1i(gl.getUniformLocation(modelProgram,'tex'),0);
 gl.bindTexture(gl.TEXTURE_2D,skin);gl.uniform1i(gl.getUniformLocation(modelProgram,'emissive'),false);
 gl.drawArrays(gl.TRIANGLES,0,modelMesh.count);
 gl.enable(gl.BLEND);gl.blendFunc(gl.ONE,gl.ONE);gl.depthMask(false);
 gl.bindTexture(gl.TEXTURE_2D,glow);gl.uniform1i(gl.getUniformLocation(modelProgram,'emissive'),true);
 gl.drawArrays(gl.TRIANGLES,0,modelMesh.count);
 gl.depthMask(true);gl.disable(gl.BLEND);
 gl.useProgram(solidProgram);gl.bindVertexArray(effectMesh.vao);
 gl.uniformMatrix4fv(gl.getUniformLocation(solidProgram,'vp'),false,vp);
 gl.uniform1i(gl.getUniformLocation(solidProgram,'emissive'),true);
 gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);gl.depthMask(false);
 gl.drawArrays(gl.TRIANGLES,0,effectMesh.count);
 gl.depthMask(true);gl.disable(gl.BLEND);gl.bindVertexArray(null);
 drawVignette(state.time);
}

const timeline=$('#timeline'),timeLabel=$('#time'),motion=$('#motion'),spacing=$('#distance');
const phaseTime={windup:.35,channel:1.2,pulse:1.69,break:4.5};
function sync(){
 motion.textContent=state.playing?'Pause':'Play';motion.classList.toggle('active',state.playing);
 timeline.value=state.time;timeLabel.textContent=state.time.toFixed(2)+' s';
 $('#distance-label').textContent=state.spacing<33?'Close':state.spacing>73?'Far':'Medium';
 const phase=state.time<.7?'windup':state.time>=4.2?'break':!state.playing&&Math.abs(state.time-1.69)<.04?'pulse':'channel';
 document.querySelectorAll('[data-phase]').forEach(b=>b.classList.toggle('active',b.dataset.phase===phase));
}
motion.onclick=()=>{state.playing=!state.playing;sync();};
$('#replay').onclick=()=>{state.time=0;state.playing=true;sync();render();};
timeline.oninput=()=>{state.time=+timeline.value;state.playing=false;sync();render();};
spacing.oninput=()=>{state.spacing=+spacing.value;sync();render();};
document.querySelectorAll('[data-phase]').forEach(b=>b.onclick=()=>{state.time=phaseTime[b.dataset.phase];state.playing=false;sync();render();});
const views={hero:[-25,17,9.3],front:[0,7,9.8],side:[82,9,9.3],above:[-20,65,10]};
document.querySelectorAll('[data-view]').forEach(b=>b.onclick=()=>{
 state.view=b.dataset.view;
 if(views[state.view]){const [yaw,pitch,distance]=views[state.view];Object.assign(state.cam,{yaw:rad(yaw),pitch:rad(pitch),distance});}
 document.querySelectorAll('[data-view]').forEach(x=>x.classList.toggle('active',x===b));render();
});
let drag=null;
canvas.onpointerdown=e=>{drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId);canvas.classList.add('dragging');};
canvas.onpointermove=e=>{if(!drag)return;const dx=e.clientX-drag[0],dy=e.clientY-drag[1];
 state.cam.yaw-=dx*.008;state.cam.pitch=clamp(state.cam.pitch+dy*.008,-1.2,1.4);
 drag=[e.clientX,e.clientY];state.view='custom';document.querySelectorAll('[data-view]').forEach(b=>b.classList.remove('active'));render();};
const release=()=>{drag=null;canvas.classList.remove('dragging');};canvas.onpointerup=release;canvas.onpointercancel=release;
canvas.onwheel=e=>{e.preventDefault();state.cam.distance=clamp(state.cam.distance*Math.exp(e.deltaY*.0012),3.5,20);render();};
new ResizeObserver(render).observe(canvas);

async function main(){
 if(!gl)throw Error('WebGL 2 is unavailable in this browser');
 modelProgram=program(MODEL_VS,MODEL_FS);solidProgram=program(SOLID_VS,SOLID_FS);
 modelMesh=mesh(modelProgram,[['pos',3],['normal',3],['uv',2]],8);
 solidMesh=mesh(solidProgram,[['pos',3],['normal',3],['col',4]],10);
 effectMesh=mesh(solidProgram,[['pos',3],['normal',3],['col',4]],10);
 const root='/src/main/resources/assets/elementalwands/';
 const [geo,animation,skinImage,glowImage]=await Promise.all([
  json(root+'geckolib/models/hollow_necromancer.geo.json'),
  json(root+'geckolib/animations/hollow_necromancer.animation.json'),
  image(root+'textures/entity/hollow_necromancer.png'),
  image(root+'textures/entity/hollow_necromancer_glowmask.png')]);
 model=bakeModel(geo);drain=animation.animations['animation.hollow_necromancer.drain'];
 if(!drain)throw Error('Necromancer drain animation was not found');
 skin=texture(skinImage);glow=texture(glowImage);
 sync();render();document.body.dataset.ready='true';
 let previous=performance.now();
 function frame(now){const dt=Math.min((now-previous)/1000,.1);previous=now;
  if(state.playing){state.time=(state.time+dt)%5;sync();render();}requestAnimationFrame(frame);}
 requestAnimationFrame(frame);
}
main().catch(error=>{document.body.dataset.error=error.message;console.error(error);
 const note=document.createElement('div');note.className='notice';note.textContent=error.message;document.querySelector('aside').prepend(note);});
