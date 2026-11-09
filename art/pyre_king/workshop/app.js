'use strict';
// Pyre King review workshop. Phone first: the view fills the screen, a bottom bar holds playback,
// and a sheet (a side panel on landscape and desktop screens) holds everything else.
const {M,rad,norm,bakeModel}=PyreCore;
const $=s=>document.querySelector(s),$$=s=>[...document.querySelectorAll(s)];
const canvas=$('#view'),app=$('#app');
const add=(a,b)=>a.map((v,i)=>v+b[i]),sub=(a,b)=>a.map((v,i)=>v-b[i]),mul=(a,k)=>a.map(v=>v*k);
const dot=(a,b)=>a[0]*b[0]+a[1]*b[1]+a[2]*b[2],lerp=(a,b,k)=>a.map((v,i)=>v+(b[i]-v)*k);
const cross=(a,b)=>[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
const clamp=(v,lo,hi)=>Math.max(lo,Math.min(hi,v)),deg=r=>r*180/Math.PI;
// yaw, pitch, zoom and pan are the camera you choose; target and distance report where the orbit
// camera actually is (the automatic framing with your zoom and pan on top).
const state={clip:'',speed:1,model:'king',view:'hero',preset:'hero',yaw:-32,pitch:12,zoom:1,pan:[0,0,0],distance:13.8,target:[.65,3.1,0],
  player:true,glow:true,fire:true,clay:false,light:'day',motion:false,time:.65,playing:true,sheet:false};
let models,manifest,clips={},ready=false,images=null,dirty=true;
const invalidate=()=>{dirty=true;};
// Test hooks: extra flame faces drawn with the scene's own (see window.workshop.debug).
const debug={flames:[]};

// ---------- WebGL ----------
const VS=`#version 300 es
in vec3 pos;in vec3 normal;in vec2 uv;in vec3 col;in float opacity;uniform mat4 vp;out float alpha;out vec3 n;out vec2 t;out vec3 tint;
void main(){gl_Position=vp*vec4(pos,1.);n=normal;t=uv;tint=col;alpha=opacity;}`;
const FS=`#version 300 es
precision highp float;in float alpha;in vec3 n;in vec2 t;in vec3 tint;out vec4 outColor;
uniform sampler2D skin;uniform sampler2D glowmask;uniform bool effect;uniform bool textured;uniform bool glow;uniform bool clay;uniform float level;
void main(){vec4 c=textured?texture(skin,t):vec4(tint,1.);if(c.a<.02)discard;
vec3 nn=normalize(gl_FrontFacing?n:-n);float shade=min(1.,.48+.40*max(0.,dot(nn,normalize(vec3(-.5,1.,-.8))))+.12*max(0.,dot(nn,normalize(vec3(1.,.2,.5)))));
vec3 base=clay&&textured?vec3(.66,.64,.60):c.rgb;vec3 rgb=base*shade*level;
vec4 g=textured&&glow&&!clay?texture(glowmask,t):vec4(0.);rgb=mix(rgb,max(rgb,g.rgb),g.a);
if(effect)rgb=c.rgb;outColor=vec4(rgb,c.a*alpha);}`;
// The floor is one wide quad. Its checker (0.98-block tiles with 0.02 seams, as lit tile tops)
// is drawn in the shader, so it can scroll under a walk and fade into the backdrop long before
// its edge: no edge ever shows, however far the camera pulls back.
const FLOOR_VS=`#version 300 es
in vec3 pos;uniform mat4 vp;out vec3 w;void main(){w=pos;gl_Position=vp*vec4(pos,1.);}`;
const FLOOR_FS=`#version 300 es
precision highp float;in vec3 w;out vec4 o;uniform float scroll;uniform vec3 sky;uniform float level;
void main(){vec2 g=vec2(w.x,w.z-scroll),fw=max(fwidth(g),vec2(1e-4));float blur=max(fw.x,fw.y);
float c=mix(mix(.35,.32,mod(floor(g.x)+floor(g.y),2.)),.335,smoothstep(.2,.7,blur));
vec3 col=vec3(c,c*.99,c*.95)*.792*level;
vec2 e=abs(fract(g-.49)-.5),s=1.-smoothstep(.01-fw*.5,.01+fw*.5,e);
col=mix(col,sky,max(s.x,s.y)*(1.-smoothstep(.04,.15,blur)));
o=vec4(mix(col,sky,smoothstep(20.,64.,length(w.xz))),1.);}`;
const FLOOR_SIZE=90,FLOOR_Y=-.01;
const ATTRS=[['pos',3],['normal',3],['uv',2],['col',3],['opacity',1]];
let gl=null,prog,floorProg,u,fu,vao,buf,floorVao,tex,mask,fireTex,lost=false;
function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
function program(vs,fs){const p=gl.createProgram();gl.attachShader(p,shader(gl.VERTEX_SHADER,vs));gl.attachShader(p,shader(gl.FRAGMENT_SHADER,fs));
  gl.linkProgram(p);if(!gl.getProgramParameter(p,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(p));return p;}
function texture(img){let t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);for(const k of [gl.TEXTURE_MIN_FILTER,gl.TEXTURE_MAG_FILTER])gl.texParameteri(gl.TEXTURE_2D,k,gl.NEAREST);for(const k of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T])gl.texParameteri(gl.TEXTURE_2D,k,gl.CLAMP_TO_EDGE);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,img);return t;}
function image(src){return new Promise((resolve,reject)=>{const i=new Image();i.onload=()=>resolve(i);i.onerror=()=>reject(Error(src+' did not load'));i.src=src;});}
function vertexLayout(){let offset=0;for(const [name,size] of ATTRS){const loc=gl.getAttribLocation(prog,name);if(loc>=0){gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,size,gl.FLOAT,false,48,offset*4);}offset+=size;}}
// Builds (or, after a lost context, rebuilds) every GPU resource. The page draws only when
// something changed, so it keeps no drawing buffer around between frames.
function setupGL(){
  gl=gl||canvas.getContext('webgl2',{antialias:true});if(!gl)return false;
  prog=program(VS,FS);u={};for(const n of ['effect','vp','skin','glowmask','textured','glow','clay','level'])u[n]=gl.getUniformLocation(prog,n);
  floorProg=program(FLOOR_VS,FLOOR_FS);fu={};for(const n of ['vp','scroll','sky','level'])fu[n]=gl.getUniformLocation(floorProg,n);
  const F=FLOOR_SIZE,y=FLOOR_Y;floorVao=gl.createVertexArray();gl.bindVertexArray(floorVao);gl.bindBuffer(gl.ARRAY_BUFFER,gl.createBuffer());
  gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-F,y,-F,F,y,-F,F,y,F,-F,y,-F,F,y,F,-F,y,F]),gl.STATIC_DRAW);
  const loc=gl.getAttribLocation(floorProg,'pos');gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,3,gl.FLOAT,false,12,0);
  vao=gl.createVertexArray();buf=gl.createBuffer();gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,buf);vertexLayout();
  if(images){tex=texture(images.skin);mask=texture(images.glow);fireTex=texture(images.fire);}
  return true;
}
canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();lost=true;});
canvas.addEventListener('webglcontextrestored',()=>{lost=false;setupGL();invalidate();});

// ---------- Geometry ----------
const QUAD=[0,1,2,0,2,3];
let scratch=new Float32Array(0);
// A face may carry one opacity (alpha) or one per corner (alphas); either fades its texture.
function emitFaces(fs){const n=fs.length*72;if(scratch.length<n)scratch=new Float32Array(Math.ceil(n*1.25));const out=scratch;let o=0;
  for(const f of fs){const al=f.alphas,a=f.alpha??1;for(const k of QUAD){const p=f.pts[k],t=f.uvs[k];
    out[o]=p[0];out[o+1]=p[1];out[o+2]=p[2];out[o+3]=f.n[0];out[o+4]=f.n[1];out[o+5]=f.n[2];
    out[o+6]=t[0];out[o+7]=t[1];out[o+8]=1;out[o+9]=1;out[o+10]=1;out[o+11]=al?al[k]:a;o+=12;}}
  return out.subarray(0,n);}
function box(data,o,s,color){
  const [x,y,z]=o,[X,Y,Z]=o.map((v,i)=>v+s[i]);
  const fs=[[[x,y,z],[X,y,z],[X,Y,z],[x,Y,z],[0,0,-1]],[[X,y,Z],[x,y,Z],[x,Y,Z],[X,Y,Z],[0,0,1]],[[x,y,Z],[x,y,z],[x,Y,z],[x,Y,Z],[-1,0,0]],[[X,y,z],[X,y,Z],[X,Y,Z],[X,Y,z],[1,0,0]],[[x,Y,z],[X,Y,z],[X,Y,Z],[x,Y,Z],[0,1,0]],[[x,y,Z],[X,y,Z],[X,y,z],[x,y,z],[0,-1,0]]];
  for(const f of fs)for(const k of QUAD)data.push(...f[k],...f[4],0,0,...color,1);
}
// The 1.8-block scale player, built facing the camera at a place chosen by the framing.
const PLAYER_HALF=[.45,1.8,.225];
let figureMemo={key:'',data:null};
function playerFigure(fig){
  const key=fig.pos.join()+fig.xr.join();if(figureMemo.key===key)return figureMemo.data;
  const d=[],skin=[.63,.45,.32],shirt=[.12,.48,.51],pants=[.22,.25,.39];
  box(d,[-.225,1.35,-.225],[.45,.45,.45],skin);box(d,[-.225,.675,-.1125],[.45,.675,.225],shirt);
  box(d,[-.45,.675,-.1125],[.225,.675,.225],skin);box(d,[.225,.675,-.1125],[.225,.675,.225],skin);
  box(d,[-.225,0,-.1125],[.225,.675,.225],pants);box(d,[0,0,-.1125],[.225,.675,.225],pants);
  const {pos,xr}=fig,zf=cross(xr,[0,1,0]);
  for(let i=0;i<d.length;i+=12){const [x,y,z]=[d[i],d[i+1],d[i+2]],[a,b,c]=[d[i+3],d[i+4],d[i+5]];
    d[i]=pos[0]+x*xr[0]+z*zf[0];d[i+1]=pos[1]+y;d[i+2]=pos[2]+x*xr[2]+z*zf[2];
    d[i+3]=a*xr[0]+c*zf[0];d[i+4]=b;d[i+5]=a*xr[2]+c*zf[2];}
  figureMemo={key,data:new Float32Array(d)};return figureMemo.data;}
function playerCorners(fig){const {pos,xr}=fig,zf=cross(xr,[0,1,0]),[hx,h,hz]=PLAYER_HALF,out=[];
  for(const sx of [-1,1])for(const y of [0,h])for(const sz of [-1,1])out.push(pos[0]+sx*hx*xr[0]+sz*hz*zf[0],y,pos[2]+sx*hx*xr[2]+sz*hz*zf[2]);
  return new Float32Array(out);}
function submit(data,textured){gl.uniform1i(u.textured,textured);gl.bufferData(gl.ARRAY_BUFFER,data,gl.DYNAMIC_DRAW);gl.drawArrays(gl.TRIANGLES,0,data.length/12);}

// ---------- Clips ----------
function clipTime(){const c=clips[state.clip];if(!c)return state.time;const L=c.animation_length;
  // Looping clips wrap; one-shots hold their last frame for a beat, then replay.
  return c.loop?state.time%L:Math.min(state.time%(L+.8),L);}
function activeClip(){return state.model==='king'&&clips[state.clip]?clips[state.clip]:null;}
// An in-place walk or run moves at its ground speed (blocks per second, from the manifest).
function groundSpeed(name=state.clip){const v=+(manifest&&manifest.clips&&manifest.clips[name]&&manifest.clips[name].ground_speed);return v>0?v:0;}
function sceneOpts(name){const v=groundSpeed(name);return v>0?{travel:[0,0,-v]}:undefined;}
function poseAt(t){const c=activeClip();
  return PyreEffects.scene(models[state.model==='king'?'king':'sword'],manifest,t,c?false:state.motion,state.model==='planted',c,c?sceneOpts(state.clip):undefined);}
function scene(){return poseAt(activeClip()?clipTime():state.time);}
// The floor slides back under an in-place walk or run at the clip's ground speed, on the same
// clock as the pose, so a planted foot stays on its tile. Front is -Z, so the floor moves +Z.
function floorScroll(){const v=activeClip()?groundSpeed():0;return v>0?((v*state.time)%2+2)%2:0;}
// What the scrubber and clock show: the clip, or the 4-second test sweep.
function timeline(){const c=activeClip();if(c)return {t:clipTime(),length:c.animation_length};
  if(state.motion)return {t:((state.time%4)+4)%4,length:4};return null;}

// ---------- Framing ----------
// The camera frames what moves. A still subject (the guard, a looping clip's whole cycle, the
// test sweep) is fitted exactly. A one-shot clip is followed: each frame fits the pose plus what
// the next LOOK_AHEAD seconds of playback will show (while paused, the pose alone) and eases
// toward that framing with a critically damped spring, quick to pull back and slower to close
// in; the pose on screen never leaves the view. The camera pulls back only until the king is
// MIN_KING_SHARE of the view's height (THROWN_KING_SHARE when that shows a whole throw); past
// that he stays whole and the view leans toward the sword as far as it can. Your own orbit,
// zoom and pan sit on top of the automatic framing.
const ORBIT_FOV=40,PLAYER_FOV=50,MAX_PLAYER_FOV=75,MARGIN=.9,MIN_DISTANCE=1,MAX_DISTANCE=90,NEAR_SHARE=.4;
const KING_HEIGHT=5,MIN_KING_SHARE=.28,THROWN_KING_SHARE=.22,LOOK_AHEAD=.3,SAMPLE_RATE=40,OMEGA_OUT=12,OMEGA_IN=5;
const PRESETS={hero:[-32,12],front:[0,0],side:[90,5],back:[180,8],head:[-18,5]};
const PLAYER_CAMS={player8:8,player16:16},PLAYER_EYE=1.6,PLAYER_LOOK=3.3,PLAYER_COST=1.15,PLAYER_GAP=.35;
const isPreset=v=>v in PRESETS||v in PLAYER_CAMS;
const HEAD_BONES=new Set(['head','death_mask','crown','ash_beard']),GEAR=new Set(['sword','pommel_ring','sword_fire','long_chain']);
// Clouds are flat xyz arrays cut down to their extreme points in 128 directions: the corners
// of their hull, which is all a perspective fit needs.
const DIRS=(()=>{const n=128,d=new Float64Array(n*3);for(let i=0;i<n;i++){const y=1-2*(i+.5)/n,r=Math.sqrt(1-y*y),a=i*2.399963229728653;d[3*i]=Math.cos(a)*r;d[3*i+1]=y;d[3*i+2]=Math.sin(a)*r;}return d;})();
function hull(list){const n=DIRS.length/3,top=new Float64Array(n).fill(-Infinity),ba=new Int32Array(n).fill(-1),bi=new Int32Array(n);
  list.forEach((a,ai)=>{if(a)for(let i=0;i<a.length;i+=3){const x=a[i],y=a[i+1],z=a[i+2];
    for(let k=0,j=0;k<n;k++,j+=3){const v=x*DIRS[j]+y*DIRS[j+1]+z*DIRS[j+2];if(v>top[k]){top[k]=v;ba[k]=ai;bi[k]=i;}}}});
  const seen=new Set(),out=[];for(let k=0;k<n;k++){if(ba[k]<0)continue;const key=ba[k]*1e8+bi[k];if(seen.has(key))continue;seen.add(key);const a=list[ba[k]];out.push(a[bi[k]],a[bi[k]+1],a[bi[k]+2]);}
  return out.length?new Float32Array(out):null;}
// One pose's points: everything above the floor, the body without the sword and its chain
// (which must always stay in view), and the head. thrown: the sword is out on its chain (its
// pommel ring sits 0.74 blocks from the fist's centre while held).
function poseRaw(s){const all=[],body=[],head=[],hand=[0,0,0,0],ring=[0,0,0,0];
  for(const f of s.faces){const gear=GEAR.has(f.bone),hd=HEAD_BONES.has(f.bone),c=f.bone==='right_hand'?hand:f.bone==='pommel_ring'?ring:null;
    for(const p of f.pts){if(c){c[0]+=p[0];c[1]+=p[1];c[2]+=p[2];c[3]++;}
      if(p[1]<-.05)continue;all.push(p[0],p[1],p[2]);if(!gear)body.push(p[0],p[1],p[2]);if(hd)head.push(p[0],p[1],p[2]);}}
  for(const f of s.flames)for(const p of f.pts)if(p[1]>=-.05)all.push(p[0],p[1],p[2]);
  const thrown=hand[3]>0&&ring[3]>0&&Math.hypot(hand[0]/hand[3]-ring[0]/ring[3],hand[1]/hand[3]-ring[1]/ring[3],hand[2]/hand[3]-ring[2]/ring[3])>1.2;
  return {all,body,head:head.length?head:null,thrown};}
function poseClouds(s){const r=poseRaw(s);return {all:hull([r.all]),body:hull([r.body]),head:r.head&&hull([r.head]),thrown:r.thrown};}
const stillCache=new Map(),sampleCache=new Map();
function stillKey(){if(state.model!=='king')return '#'+state.model+(state.motion?'-sweep':'');const c=activeClip();return c?state.clip:state.motion?'#sweep':'#guard';}
// A still subject's clouds, measured once: the guard over several flicker frames, a looping
// clip over its whole cycle, the sweep over its four seconds.
function stillCloud(key){let st=stillCache.get(key);if(st)return st;
  const flick=[.65,.8,.95,1.1,1.25,1.4],king=models.king,sword=models.sword,poses=[];
  if(key==='#guard')for(const t of flick)poses.push(PyreEffects.scene(king,manifest,t));
  else if(key==='#sword'||key==='#planted')for(const t of flick)poses.push(PyreEffects.scene(sword,manifest,t,false,key==='#planted'));
  else if(key.endsWith('sweep')){const m=key==='#sweep'?king:sword;for(let i=0;i<=32;i++)poses.push(PyreEffects.scene(m,manifest,4*i/32,true,key.startsWith('#planted')));}
  else{const c=clips[key],n=Math.max(12,Math.ceil(c.animation_length*20));for(let i=0;i<n;i++)poses.push(PyreEffects.scene(king,manifest,c.animation_length*i/n,false,false,c,sceneOpts(key)));}
  const cs=poses.map(poseClouds),heads=cs.filter(c=>c.head).map(c=>c.head);
  st={all:hull(cs.map(c=>c.all)),body:hull(cs.map(c=>c.body)),head:heads.length?hull(heads):null};
  // Where the scale player may stand: every visible point of the guard (or sweep).
  if(key==='#guard'||key==='#sweep'){const raw=[];for(const f of poses[0].faces)for(const p of f.pts)raw.push(p[0],p[1],p[2]);st.raw=[new Float32Array(raw),st.all];}
  stillCache.set(key,st);return st;}
// A one-shot clip's poses, SAMPLE_RATE a second, measured as the clip first reaches them.
function sampleCloud(i){let arr=sampleCache.get(state.clip);if(!arr)sampleCache.set(state.clip,arr=[]);
  if(!arr[i])arr[i]=poseClouds(poseAt(Math.min(i/SAMPLE_RATE,activeClip().animation_length)));return arr[i];}
function viewAspect(){const w=canvas.clientWidth,h=canvas.clientHeight;return w>0&&h>0?w/h:1;}
function orbitAxes(yaw,pitch){const y=rad(yaw),p=rad(pitch),z=[-Math.sin(y)*Math.cos(p),Math.sin(p),-Math.cos(y)*Math.cos(p)];
  const x=norm(cross([0,1,0],z));return {x,y:cross(z,x),z};}
// How far back an orbit camera aimed at T must stand to hold every point inside MARGIN of the
// view, with no point nearer the lens than NEAR_SHARE of that distance.
function needAt(list,T,yaw,pitch,aspect){const {x,y,z}=orbitAxes(yaw,pitch),tv=Math.tan(rad(ORBIT_FOV)/2)*MARGIN,th=tv*aspect;let m=0;
  for(const a of list)if(a)for(let i=0;i<a.length;i+=3){const vx=a[i]-T[0],vy=a[i+1]-T[1],vz=a[i+2]-T[2],Z=z[0]*vx+z[1]*vy+z[2]*vz;
    const q=Math.max(Math.abs(x[0]*vx+x[1]*vy+x[2]*vz)/th,Math.abs(y[0]*vx+y[1]*vy+y[2]*vz)/tv)+Z,r=Z/(1-NEAR_SHARE);if(q>m)m=q;if(r>m)m=r;}
  return m;}
// The closest orbit camera at this yaw and pitch that holds every point, centred on screen.
function fitOrbit(list,yaw,pitch,aspect){
  const {x,y,z}=orbitAxes(yaw,pitch),lo=[Infinity,Infinity,Infinity],hi=[-Infinity,-Infinity,-Infinity];
  for(const a of list)if(a)for(let i=0;i<a.length;i+=3)for(let k=0;k<3;k++){if(a[i+k]<lo[k])lo[k]=a[i+k];if(a[i+k]>hi[k])hi[k]=a[i+k];}
  if(!(lo[0]<=hi[0]))return {target:[.65,3.1,0],distance:13.8};
  let T=mul(add(lo,hi),.5);
  for(let it=0;it<4;it++){const d=needAt(list,T,yaw,pitch,aspect);let lx=Infinity,hx=-Infinity,ly=Infinity,hy=-Infinity;
    for(const a of list)if(a)for(let i=0;i<a.length;i+=3){const vx=a[i]-T[0],vy=a[i+1]-T[1],vz=a[i+2]-T[2],k=1/Math.max(1e-3,d-(z[0]*vx+z[1]*vy+z[2]*vz));
      const X=(x[0]*vx+x[1]*vy+x[2]*vz)*k,Y=(y[0]*vx+y[1]*vy+y[2]*vz)*k;if(X<lx)lx=X;if(X>hx)hx=X;if(Y<ly)ly=Y;if(Y>hy)hy=Y;}
    T=add(T,add(mul(x,(lx+hx)/2*d),mul(y,(ly+hy)/2*d)));}
  return {target:T,distance:clamp(needAt(list,T,yaw,pitch,aspect),MIN_DISTANCE,MAX_DISTANCE)};
}
// The follow fit: everything if that keeps the king at least MIN_KING_SHARE of the view's height
// (THROWN_KING_SHARE while the sword is out on its chain, if that is enough to show the whole
// throw); otherwise the must-see points (the body, plus a sword still in hand) whole, at that
// distance or the least that holds them, and the view slid toward the rest as far as they allow.
const capFor=share=>KING_HEIGHT/(2*Math.tan(rad(ORBIT_FOV)/2)*share);
function followFit(list,prio,yaw,pitch,aspect,thrown=false){
  const f=fitOrbit(list,yaw,pitch,aspect),cap=capFor(MIN_KING_SHARE);
  if(!prio||f.distance<=cap||thrown&&f.distance<=capFor(THROWN_KING_SHARE))return f;
  const p=fitOrbit(prio,yaw,pitch,aspect),d=Math.max(cap,p.distance);let a=0,b=1;
  for(let i=0;i<12;i++){const m=(a+b)/2;if(needAt(prio,lerp(p.target,f.target,m),yaw,pitch,aspect)<=d)a=m;else b=m;}
  return {target:lerp(p.target,f.target,a),distance:d};
}
// The scale player stands beside the king on whichever side of the screen has more room, at his
// depth and facing the camera, so he never stands between the camera and the king.
function placePlayer(raw,yaw){
  const xr=[-Math.cos(rad(yaw)),0,Math.sin(rad(yaw))],b={lo:[Infinity,0,Infinity],hi:[-Infinity,0,-Infinity]};
  for(let i=0;i<raw[0].length;i+=3)for(const k of [0,2]){const v=raw[0][i+k];if(v<b.lo[k])b.lo[k]=v;if(v>b.hi[k])b.hi[k]=v;}
  const C=[(b.lo[0]+b.hi[0])/2,0,(b.lo[2]+b.hi[2])/2];let lo=0,hi=0;
  // Only what is below his head height can touch him.
  for(const a of raw)for(let i=0;i<a.length;i+=3)if(a[i+1]<PLAYER_HALF[1]+.5){const s=(a[i]-C[0])*xr[0]+(a[i+2]-C[2])*xr[2];if(s<lo)lo=s;if(s>hi)hi=s;}
  const side=hi<=-lo?1:-1,off=(side>0?hi:-lo)+PLAYER_GAP+PLAYER_HALF[0];
  return {pos:[C[0]+xr[0]*side*off,0,C[2]+xr[2]*side*off],xr};
}
// Player cameras keep the player's eye (1.6 blocks up, 8 or 16 blocks in front) and the 50° view
// whenever everything fits. Otherwise the gaze turns to centre it and the view widens only as
// much as it needs, up to 75°. If even that is not enough (a blade swung at the player's own
// eye), the body stays whole and the gaze turns from it toward the rest only as far as it may.
function fitPlayer(list,body,d,aspect){
  const eye=[0,PLAYER_EYE,-d],t50=Math.tan(rad(PLAYER_FOV)/2),tmax=Math.tan(rad(MAX_PLAYER_FOV)/2);
  const span=(pts,at)=>{const z=norm(sub(eye,at)),x=norm(cross([0,1,0],z)),y=cross(z,x);let lx=Infinity,hx=-Infinity,ly=Infinity,hy=-Infinity;
    for(const a of pts)if(a)for(let i=0;i<a.length;i+=3){const v=[a[i]-eye[0],a[i+1]-eye[1],a[i+2]-eye[2]],depth=-dot(z,v);if(depth<.5)continue;
      const X=dot(x,v)/depth,Y=dot(y,v)/depth;if(X<lx)lx=X;if(X>hx)hx=X;if(Y<ly)ly=Y;if(Y>hy)hy=Y;}
    return {lx,hx,ly,hy,x,y,z,tan:Math.max((hx-lx)/2/(aspect*MARGIN),(hy-ly)/2/MARGIN)};};
  const inView=(q,tan)=>Math.max(-q.lx,q.hx)<=tan*aspect*MARGIN&&Math.max(-q.ly,q.hy)<=tan*MARGIN;
  const fov=tan=>clamp(deg(2*Math.atan(Math.max(t50,tan))),PLAYER_FOV,MAX_PLAYER_FOV),base=[0,PLAYER_LOOK,0];
  if(inView(span(list,base),t50))return {at:base,fov:PLAYER_FOV};
  const aim=pts=>{let at=base,q=span(pts,at);
    for(let i=0;i<3;i++){const L=Math.hypot(...sub(at,eye));at=add(eye,mul(norm(add(mul(q.z,-1),add(mul(q.x,(q.lx+q.hx)/2),mul(q.y,(q.ly+q.hy)/2)))),L));q=span(pts,at);}
    return {at,tan:q.tan};};
  const all=aim(list);if(all.tan<=tmax||!body||!body.some(Boolean))return {at:all.at,fov:fov(all.tan)};
  const b=aim(body),L=Math.hypot(...sub(b.at,eye)),from=norm(sub(b.at,eye)),to=norm(sub(all.at,eye));
  const gaze=k=>add(eye,mul(norm(lerp(from,to,k)),L));let lo=0,hi=1;
  for(let i=0;i<12;i++){const m=(lo+hi)/2;if(inView(span(body,gaze(m)),tmax))lo=m;else hi=m;}
  return {at:gaze(lo),fov:MAX_PLAYER_FOV};
}
// The framing the camera is heading for, given the pose on screen now.
let stillMemo={key:'',value:null};
function desired(s){
  const aspect=viewAspect(),king=state.model==='king',c=activeClip(),head=king&&state.view==='head',player=state.view in PLAYER_CAMS;
  if(king&&c&&!c.loop){
    // Playing: the measured poses from just before now to LOOK_AHEAD ahead. Paused: the pose itself.
    const now=poseRaw(s),prio=head?null:[now.thrown?now.body:now.all];let list=[head?now.head:now.all];
    // The look-ahead is real time, so at ½× or ¼× the camera keeps closer to the action.
    let thrown=now.thrown;
    if(state.playing){const t=clipTime(),N=Math.ceil(c.animation_length*SAMPLE_RATE),ahead=LOOK_AHEAD*state.speed;list=[];
      for(let i=Math.floor(t*SAMPLE_RATE);i<=Math.min(N,Math.ceil((t+ahead)*SAMPLE_RATE));i++){const q=sampleCloud(i);list.push(head?q.head:q.all);thrown=thrown||q.thrown;}}
    if(player)return {kind:'player',follow:true,...fitPlayer(list,[now.body],PLAYER_CAMS[state.view],aspect)};
    return {kind:'orbit',follow:true,prio:prio||list,...followFit(list,prio,state.yaw,state.pitch,aspect,thrown)};
  }
  const key=[stillKey(),state.view,state.yaw,state.pitch,aspect.toFixed(4),state.player].join('|');
  if(stillMemo.key===key)return stillMemo.value;
  const st=stillCloud(stillKey()),list=[head&&st.head?st.head:st.all];let value;
  if(player)value={kind:'player',follow:false,...fitPlayer(list,[st.body],PLAYER_CAMS[state.view],aspect)};
  else{let f=fitOrbit(list,state.yaw,state.pitch,aspect),figure=null;
    // The scale player joins the still guard (or sweep) whenever that costs little; on a narrow
    // portrait screen the king comes first and the player stays out rather than cut in half.
    if(state.player&&st.raw&&!head){const fig=placePlayer(st.raw,state.yaw),g=fitOrbit(list.concat([playerCorners(fig)]),state.yaw,state.pitch,aspect);
      if(g.distance<=f.distance*PLAYER_COST){f=g;figure=fig;}}
    value={kind:'orbit',follow:false,figure,...f};}
  stillMemo={key,value};return value;
}
// The eased framing: [target xyz, log distance] for orbit views, [gaze xyz, fov] for player eyes.
const cam={kind:'',x:null,v:[0,0,0,0],settled:true,figure:null};
let snapNext=true,lastCamTime=NaN;
const snap=()=>{snapNext=true;invalidate();};
// A critically damped spring, solved exactly over the step, toward a goal that holds still.
function spring(x,v,goal,dt,w){const e=Math.exp(-w*dt);for(let k=0;k<x.length;k++){const d=x[k]-goal[k],b=v[k]+w*d;x[k]=goal[k]+(d+b*dt)*e;v[k]=(v[k]-w*b*dt)*e;}}
function updateCamera(dt,s){
  const d=desired(s),goal=d.kind==='orbit'?[...d.target,Math.log(d.distance)]:[...d.at,d.fov];
  // While paused, moving through time (scrubbing, a link) cuts to the new framing.
  const jumped=!state.playing&&state.time!==lastCamTime;lastCamTime=state.time;
  if(snapNext||jumped||!d.follow||cam.kind!==d.kind||!cam.x){cam.x=goal.slice();cam.v=[0,0,0,0];snapNext=false;}
  else spring(cam.x,cam.v,goal,dt,goal[3]>cam.x[3]?OMEGA_OUT:OMEGA_IN);
  cam.kind=d.kind;cam.figure=d.figure||null;
  // Whatever the easing, the pose on screen now (its must-see part) never leaves the view.
  if(d.kind==='orbit'&&d.follow){const n=needAt(d.prio,cam.x.slice(0,3),state.yaw,state.pitch,viewAspect());if(Math.log(n)>cam.x[3]){cam.x[3]=Math.log(n);cam.v[3]=Math.max(0,cam.v[3]);}}
  cam.settled=cam.x.every((v,k)=>Math.abs(v-goal[k])<2e-3)&&cam.v.every(v=>Math.abs(v)<2e-3);
  if(cam.settled){cam.x=goal.slice();cam.v=[0,0,0,0];}
  if(cam.kind==='orbit'){state.target=add(cam.x.slice(0,3),state.pan);state.distance=clamp(Math.exp(cam.x[3])*state.zoom,MIN_DISTANCE,MAX_DISTANCE);}
}
function camera(){
  if(cam.kind==='player'&&state.view in PLAYER_CAMS)return {eye:[0,PLAYER_EYE,-PLAYER_CAMS[state.view]],at:cam.x.slice(0,3),fov:cam.x[3]};
  const base=cam.kind==='orbit'&&cam.x?cam.x:[.65,3.1,0,Math.log(13.8)];
  const t=add(base.slice(0,3),state.pan),d=clamp(Math.exp(base[3])*state.zoom,MIN_DISTANCE,MAX_DISTANCE),{z}=orbitAxes(state.yaw,state.pitch);
  return {eye:add(t,mul(z,d)),at:t,fov:ORBIT_FOV};
}
function markCamera(){for(const b of $$('[data-view]')){const on=b.dataset.view===state.view;b.classList.toggle('active',on);b.setAttribute('aria-pressed',String(on));}}
// Brings the camera to the framing for the pose on screen now, at once.
function settle(){if(!ready)return;const s=scene();lastAspect=viewAspect();snapNext=true;updateCamera(0,s);invalidate();}
function setView(view){
  if(!isPreset(view))view='hero';
  state.view=state.preset=view;state.zoom=1;state.pan=[0,0,0];
  if(view in PRESETS)[state.yaw,state.pitch]=PRESETS[view];
  markCamera();settle();scheduleHash();
}
// After the subject changes (clip, model, sweep), the camera keeps its angle and frames the new
// subject afresh, whether it was a preset or turned by hand.
function reframe(){state.zoom=1;state.pan=[0,0,0];settle();}
function resetView(){setView(state.preset);}
let lastAspect=0;
function onResize(){const a=viewAspect();if(!ready)return;if(Math.abs(a-lastAspect)>=1e-3){lastAspect=a;snap();}else invalidate();}

// ---------- Drawing ----------
const coarse=matchMedia('(pointer:coarse)').matches;
let infoText='';
function cameraInfo(c){
  if(state.view in PLAYER_CAMS){const d=PLAYER_CAMS[state.view],turn=Math.round(deg(Math.atan2(Math.abs(c.at[0]),d))),wide=c.fov>PLAYER_FOV+.05;
    return `Player eye 1.6 blocks up, ${d} blocks away · ${Math.round(c.fov)}° vertical view`+
      (turn||wide?` · the gaze turns ${turn}°${wide?' and the view widens from 50°':''} to keep him in view`:' · looking at y = 3.3');}
  return coarse?'Drag to orbit · pinch to zoom · two fingers to pan · double-tap resets':'Drag to orbit · wheel to zoom · right-drag to pan · double-click resets';
}
const SKY={day:[.45,.46,.45],night:[.065,.073,.09]};
function render(s){if(!ready||!gl||lost)return;
  const dpr=Math.min(devicePixelRatio||1,2),w=Math.max(1,Math.round(canvas.clientWidth*dpr)),h=Math.max(1,Math.round(canvas.clientHeight*dpr));
  if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
  const c=camera(),vp=M.mul(M.perspective(rad(c.fov),w/h,.05,200),M.lookAt(c.eye,c.at)),night=state.light==='night',sky=SKY[night?'night':'day'],level=night?.32:1;
  gl.viewport(0,0,w,h);gl.clearColor(...sky,1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
  gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.depthMask(true);gl.disable(gl.CULL_FACE);gl.disable(gl.BLEND);
  gl.useProgram(floorProg);gl.uniformMatrix4fv(fu.vp,false,vp);gl.uniform1f(fu.scroll,floorScroll());gl.uniform3f(fu.sky,...sky);gl.uniform1f(fu.level,level);
  gl.bindVertexArray(floorVao);gl.drawArrays(gl.TRIANGLES,0,6);
  gl.useProgram(prog);gl.uniformMatrix4fv(u.vp,false,vp);gl.uniform1f(u.level,level);gl.uniform1i(u.glow,state.glow);gl.uniform1i(u.clay,state.clay);gl.uniform1i(u.effect,false);
  gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,tex);gl.uniform1i(u.skin,0);gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,mask);gl.uniform1i(u.glowmask,1);
  gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,buf);
  if(cam.figure&&state.player&&state.model==='king'&&!activeClip())submit(playerFigure(cam.figure),false);
  submit(emitFaces(s.faces),true);
  if(state.fire){
    // Flames, back to front: ordinary blended faces first, then additive ones ("blend": "add"),
    // both depth-tested without writing depth.
    const e=c.eye,far=f=>{let d=0;for(const p of f.pts)d+=(p[0]-e[0])**2+(p[1]-e[1])**2+(p[2]-e[2])**2;return d;};
    const sorted=fs=>fs.map(f=>[far(f),f]).sort((a,b)=>b[0]-a[0]).map(x=>x[1]),plain=[],glow=[];
    for(const f of debug.flames.length?s.flames.concat(debug.flames):s.flames)(f.blend==='add'?glow:plain).push(f);
    gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,fireTex);gl.uniform1i(u.effect,true);gl.enable(gl.BLEND);gl.depthMask(false);
    if(plain.length){gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);submit(emitFaces(sorted(plain)),true);}
    if(glow.length){gl.blendFunc(gl.SRC_ALPHA,gl.ONE);submit(emitFaces(sorted(glow)),true);}
    gl.depthMask(true);gl.disable(gl.BLEND);
  }
  const text=cameraInfo(c);if(text!==infoText){infoText=text;$('#camera-info').textContent=text;}
}
// One frame: pose the scene, move the camera, draw.
function frame(dt){if(!ready)return;const s=scene();updateCamera(dt,s);render(s);dirty=false;lastVisual=visualKey();}
// What the picture depends on besides the camera and the toggles: the clock, unless nothing on
// screen moves with it (a still guard with its fire off).
function visualKey(){return activeClip()||state.motion||state.fire?state.time:0;}
let lastVisual=null;

// ---------- Touch, mouse and wheel ----------
// One finger (or the left button) orbits, two fingers pinch-zoom and pan, the right button or
// Shift-drag pans, the wheel zooms, and a double tap (or double click) returns to the last preset.
function leavePreset(){
  if(state.view==='orbit')return;
  if(state.view in PLAYER_CAMS){const c=camera(),v=sub(c.eye,c.at),d=Math.hypot(...v);
    // Continue from the player's eye, keeping roughly the same framing at the orbit camera's 40°.
    state.pitch=deg(Math.asin(v[1]/d));state.yaw=deg(Math.atan2(-v[0],-v[2]));state.view='orbit';state.zoom=1;state.pan=[0,0,0];settle();
    const base=Math.exp(cam.x[3]),want=d*Math.tan(rad(c.fov)/2)/Math.tan(rad(ORBIT_FOV)/2);
    state.zoom=clamp(want,MIN_DISTANCE,MAX_DISTANCE)/base;state.pan=sub(c.at,cam.x.slice(0,3));}
  state.view='orbit';markCamera();
}
function orbitBy(dx,dy){leavePreset();state.yaw-=dx*.4;state.pitch=clamp(state.pitch+dy*.4,-65,80);invalidate();}
function panBy(dx,dy){leavePreset();const {x,y}=orbitAxes(state.yaw,state.pitch),k=2*state.distance*Math.tan(rad(ORBIT_FOV)/2)/Math.max(1,canvas.clientHeight);
  state.pan=add(state.pan,add(mul(x,-dx*k),mul(y,dy*k)));invalidate();}
function zoomBy(f){if(!isFinite(f)||f<=0)return;leavePreset();const base=cam.x?Math.exp(cam.x[3]):13.8;
  state.zoom=clamp(state.zoom*f,MIN_DISTANCE/base,MAX_DISTANCE/base);invalidate();}
const pointers=new Map();let pinch=null,tap=null,lastTap=null,panning=false;
function pinchInfo(){const [a,b]=[...pointers.values()];return {span:Math.max(1,Math.hypot(a.x-b.x,a.y-b.y)),mid:[(a.x+b.x)/2,(a.y+b.y)/2]};}
canvas.addEventListener('pointerdown',e=>{
  if(e.pointerType==='mouse'&&e.button>2)return;
  try{canvas.setPointerCapture(e.pointerId);}catch{}
  pointers.set(e.pointerId,{x:e.clientX,y:e.clientY});
  panning=e.pointerType==='mouse'&&(e.button!==0||e.shiftKey);
  if(pointers.size===1)tap={id:e.pointerId,x:e.clientX,y:e.clientY,t:e.timeStamp,moved:false};
  else{tap=null;if(pointers.size===2)pinch=pinchInfo();}
});
canvas.addEventListener('pointermove',e=>{
  const p=pointers.get(e.pointerId);if(!p)return;
  let dx=e.clientX-p.x,dy=e.clientY-p.y;p.x=e.clientX;p.y=e.clientY;
  if(pointers.size===1){
    // A few pixels of jitter stay a tap; past that the whole movement counts.
    if(tap&&tap.id===e.pointerId&&!tap.moved){if(Math.hypot(e.clientX-tap.x,e.clientY-tap.y)<8)return;tap.moved=true;dx=e.clientX-tap.x;dy=e.clientY-tap.y;}
    if(panning)panBy(dx,dy);else orbitBy(dx,dy);
  }else if(pinch){const n=pinchInfo();zoomBy(pinch.span/n.span);panBy(n.mid[0]-pinch.mid[0],n.mid[1]-pinch.mid[1]);pinch=n;}
});
function endPointer(e){
  if(!pointers.delete(e.pointerId))return;
  pinch=pointers.size===2?pinchInfo():null;
  if(e.type==='pointerup'&&tap&&tap.id===e.pointerId&&!tap.moved&&e.timeStamp-tap.t<350){
    if(lastTap&&e.timeStamp-lastTap.t<400&&Math.hypot(e.clientX-lastTap.x,e.clientY-lastTap.y)<40){lastTap=null;resetView();}
    else lastTap={t:e.timeStamp,x:e.clientX,y:e.clientY};
  }
  if(!tap||tap.id===e.pointerId)tap=null;
}
canvas.addEventListener('pointerup',endPointer);canvas.addEventListener('pointercancel',endPointer);
canvas.addEventListener('wheel',e=>{e.preventDefault();zoomBy(Math.exp(e.deltaY*(e.deltaMode===1?.04:.001)));},{passive:false});
canvas.addEventListener('contextmenu',e=>e.preventDefault());
// Belt and braces for browsers that ignore touch-action: the page never scrolls or zooms under the view.
for(const type of ['touchstart','touchmove'])canvas.addEventListener(type,e=>e.preventDefault(),{passive:false});
// Nor anywhere else: a pinch that starts on the bar or the sheet must not zoom the page, which
// would leave the view filling the zoomed screen with nothing left to pinch back out on. iOS
// Safari ignores the viewport's zoom limits, so stop its gesture events and two-finger moves.
for(const type of ['gesturestart','gesturechange','gestureend'])document.addEventListener(type,e=>e.preventDefault(),{passive:false});
document.addEventListener('touchmove',e=>{if(e.touches.length>1)e.preventDefault();},{passive:false});
if('ResizeObserver' in window)new ResizeObserver(onResize).observe(canvas);
addEventListener('resize',onResize);

// ---------- Playback bar ----------
const ICON_PLAY='<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M8 5.5v13l11-6.5z"/></svg>';
const ICON_PAUSE='<svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true"><path d="M7 5h3.6v14H7zM13.4 5H17v14h-3.6z"/></svg>';
function setPlaying(on){state.playing=on;const b=$('#pause'),label=on?'Pause':'Play';b.innerHTML=on?ICON_PAUSE:ICON_PLAY;b.setAttribute('aria-label',label);b.title=label;invalidate();scheduleHash();}
function setClip(name){
  if(name&&!clips[name])name='';
  state.clip=name;$('#clip').value=name;state.time=0;setPlaying(true);
  if(name){state.motion=false;$('#motion').checked=false;if(state.model!=='king'){state.model='king';$('#model').value='king';}}
  updateClock();reframe();scheduleHash();
}
// The sweep, Freeze horizontal and High guard all work on the static guard, so they leave any clip.
function setMotion(on,time=0,playing=true){
  if(state.clip){state.clip='';$('#clip').value='';}
  state.motion=on;$("#motion").checked=on;state.time=time;setPlaying(playing);updateClock();reframe();scheduleHash();
}
const scrub=$('#scrub'),clock=$('#clock');let clockText='',hadTimeline=null;
function updateClock(){
  const tl=timeline();
  // Without a timeline (the still guard) the scrubber row folds away and the view grows.
  if(!!tl!==hadTimeline){hadTimeline=!!tl;app.classList.toggle('no-timeline',!tl);scrub.disabled=!tl;}
  const text=tl?`${tl.t.toFixed(2)} / ${tl.length.toFixed(2)} s`:'–';
  if(text!==clockText){clockText=text;clock.textContent=text;
    if(tl){if(+scrub.max!==tl.length)scrub.max=tl.length;scrub.value=tl.t;}else scrub.value=0;
    scrub.style.setProperty('--p',(tl?100*tl.t/tl.length:0)+'%');}
}
function scrubTo(v){const tl=timeline();if(!tl)return;const c=activeClip(),wraps=!c||c.loop;
  // A looping timeline's end is its start; stop just short so the thumb does not jump back.
  setPlaying(false);state.time=clamp(v,0,tl.length-(wraps?.001:0));updateClock();invalidate();scheduleHash();}
scrub.addEventListener('input',()=>scrubTo(+scrub.value));
// iOS only drags a range input by its thumb; let a touch anywhere on the track scrub.
let scrubbing=null;
scrub.addEventListener('pointerdown',e=>{if(e.pointerType!=='touch'||scrub.disabled)return;e.preventDefault();scrubbing=e.pointerId;try{scrub.setPointerCapture(e.pointerId);}catch{}scrubAt(e);});
scrub.addEventListener('pointermove',e=>{if(e.pointerId===scrubbing)scrubAt(e);});
for(const type of ['pointerup','pointercancel'])scrub.addEventListener(type,e=>{if(e.pointerId===scrubbing)scrubbing=null;});
function scrubAt(e){const r=scrub.getBoundingClientRect(),f=clamp((e.clientX-r.left-11)/Math.max(1,r.width-22),0,1);scrubTo(f*+scrub.max);}
$('#clip').onchange=e=>setClip(e.target.value);
$('#speed').onchange=e=>{state.speed=+e.target.value;scheduleHash();};
$('#pause').onclick=()=>setPlaying(!state.playing);

// ---------- Sheet ----------
function setSheet(open){
  state.sheet=open;app.classList.toggle('sheet-open',open);$('#sheet').hidden=!open;
  const more=$('#more');more.setAttribute('aria-expanded',String(open));more.classList.toggle('active',open);
}
$('#more').onclick=()=>setSheet(!state.sheet);
$('#close').onclick=()=>{setSheet(false);$('#more').focus();};
for(const b of $$('[data-view]'))b.onclick=()=>setView(b.dataset.view);
for(const n of ['player','glow','fire','clay'])$('#'+n).onchange=e=>{state[n]=e.target.checked;if(n==='player')snap();invalidate();scheduleHash();};
$('#light').onchange=e=>{state.light=e.target.value;invalidate();scheduleHash();};
$('#model').onchange=e=>{state.model=e.target.value;if(state.model!=="king"&&state.clip){state.clip="";$("#clip").value="";}updateClock();reframe();scheduleHash();};
$('#motion').onchange=e=>setMotion(e.target.checked);
$('#horizontal').onclick=()=>setMotion(true,1.8,false);
$('#guard').onclick=()=>setMotion(false,.65,true);
for(const b of $$('[data-atlas]'))b.onclick=()=>{$('#atlas').src='candidate/pyre_king'+(b.dataset.atlas==='glow'?'_glowmask':'')+'.png';for(const x of $$('[data-atlas]'))x.classList.toggle('active',x===b);};
// The ../renders links only exist beside a local build; hosted copies would 404.
$('#local-links').hidden=!['localhost','127.0.0.1','[::1]','::1'].includes(location.hostname);

// ---------- Links ----------
// State lives in the address's #fragment, which survives hosting (a hosted page gets no query
// string): index.html#clip=royal_cut&t=1.05&view=side opens the Royal Cut paused at 1.05 s from
// the side. A local ?query still works; the fragment wins where both set a key.
function readParams(){const p=new URLSearchParams(location.search),h=location.hash.replace(/^#/,'');if(h)for(const [k,v] of new URLSearchParams(h))p.set(k,v);return p;}
const on=v=>!['0','false','off','no'].includes(String(v).toLowerCase());
function applyParams(p){
  if(p.has('model')&&['king','sword','planted'].includes(p.get('model'))){state.model=p.get('model');$('#model').value=state.model;}
  if(p.has('speed')){state.speed=[1,.5,.25].includes(+p.get('speed'))?+p.get('speed'):1;$('#speed').value=String(state.speed);}
  for(const n of ['player','glow','fire','clay'])if(p.has(n)){state[n]=on(p.get(n));$('#'+n).checked=state[n];}
  if(p.has('light')&&p.get('light') in SKY){state.light=p.get('light');$('#light').value=state.light;}
  if(p.has('clip'))setClip(p.get('clip'));else if(p.has('sweep'))setMotion(on(p.get('sweep')));
  if(p.has('t')){state.time=Math.max(0,+p.get('t')||0);setPlaying(false);}
  else if(p.has('play'))setPlaying(on(p.get('play')));
  setView(p.has('view')?p.get('view'):state.preset);
}
let hashTimer=0,hashText=null;
// Writes the fragment at most every half second (Safari refuses rapid history updates).
function scheduleHash(){if(!ready||hashTimer)return;hashTimer=setTimeout(writeHash,500);}
function writeHash(){hashTimer=0;const p=new URLSearchParams(),tl=timeline();
  if(state.clip)p.set('clip',state.clip);else if(state.motion)p.set('sweep','1');
  if(!state.playing&&tl)p.set('t',tl.t.toFixed(2));else if(!state.playing)p.set('play','0');
  if(state.preset!=='hero')p.set('view',state.preset);
  if(state.speed!==1)p.set('speed',String(state.speed));
  if(state.model!=='king')p.set('model',state.model);
  for(const n of ['fire','glow','player'])if(!state[n])p.set(n,'0');
  if(state.clay)p.set('clay','1');if(state.light!=='day')p.set('light',state.light);
  const text=p.toString();if(text===hashText)return;hashText=text;
  try{history.replaceState(history.state,'',location.pathname+location.search+(text?'#'+text:''));}catch{}
}
addEventListener('hashchange',()=>{if(ready&&location.hash.replace(/^#/,'')!==hashText)applyParams(new URLSearchParams(location.hash.replace(/^#/,'')));});
const params=readParams();
setSheet(params.has('sheet')?params.get('sheet')!=='closed':matchMedia('(min-width:900px) and (min-height:600px)').matches);
if(params.get('details')==='open')$('#rig-notes').open=true;
setPlaying(true);updateClock();

// ---------- Loading ----------
function showStatus(text){const s=$('#status');s.textContent=text;s.hidden=!text;}
(async()=>{
  if(!setupGL())throw Error('this browser has no WebGL 2');
  const json=async f=>{const r=await fetch('candidate/'+f);if(!r.ok)throw Error(f+': '+r.status);return r.json();};
  const [king,sword,im,gm,info,fi]=await Promise.all([json('pyre_king.geo.json'),json('pyre_king_sword.geo.json'),image('candidate/pyre_king.png'),image('candidate/pyre_king_glowmask.png'),json('manifest.json'),image('candidate/bladefire.png')]);
  models={king:bakeModel(king),sword:bakeModel(sword)};manifest=info;images={skin:im,glow:gm,fire:fi};
  try{const an=await json('pyre_king.animation.json');for(const [k,v] of Object.entries(an.animations)){const n=k.replace('animation.pyre_king.','');if(n==='guard')continue;clips[n]=v;
    const o=document.createElement('option'),label=n.replace(/_/g,' ');o.value=n;o.textContent=label[0].toUpperCase()+label.slice(1);$('#clip').appendChild(o);}}catch(e){console.warn(e);}
  tex=texture(im);mask=texture(gm);fireTex=texture(fi);$('#atlas').src=im.src;
  const held=info.measured_held_silhouette_height_blocks;
  $('#stats').textContent=`${info.cubes} cubes · ${info.bones} bones · ${(info.measured_body_height_blocks??5).toFixed(2)} blocks tall in the guard${held?` (${held.toFixed(2)} with the raised sword)`:''}. Sword: ${info.sword_cubes} cubes, 4.50 blocks overall with a 3.50-block blade.`;
  $('#bones').textContent=models.king.bones.map(b=>b.name).join('\n');
  ready=true;lastAspect=viewAspect();
  applyParams(params);hashText=location.hash.replace(/^#/,'');scheduleHash();
  showStatus('');document.body.dataset.ready='true';requestAnimationFrame(tick);
})().catch(e=>{showStatus('Could not load the model: '+e.message);document.body.dataset.ready='error';console.error(e);});
// Draws only when something changed: while a clip or the sweep plays, while the camera is still
// easing, or after any input. The still guard's flames flicker while playing, at 30 frames a second.
let previousTime=null,lastFrameAt=null;
function tick(now){
  const dt=previousTime===null?0:Math.min(.05,(now-previousTime)/1000);previousTime=now;
  // A test hook: hold the clock and camera where step() left them, drawing only on request.
  if(debug.freeze){if(dirty){render(scene());dirty=false;}updateClock();requestAnimationFrame(tick);return;}
  if(state.playing)state.time+=dt*state.speed;
  const changed=dirty||!cam.settled||visualKey()!==lastVisual,flickerOnly=!dirty&&cam.settled&&!activeClip()&&!state.motion;
  if(changed&&!(flickerOnly&&lastFrameAt!==null&&now-lastFrameAt<32)){frame(lastFrameAt===null?0:Math.min(.1,(now-lastFrameAt)/1000));lastFrameAt=now;}
  updateClock();requestAnimationFrame(tick);
}
// Advances the clock and the camera by dt seconds exactly as playback does, without drawing.
function step(dt){if(state.playing)state.time+=dt*state.speed;const s=scene();updateCamera(dt,s);lastFrameAt=null;invalidate();return s;}
function seek(t){state.time=t;setPlaying(false);settle();updateClock();scheduleHash();}
window.workshop={state,setView,setClip,setSheet,setMotion,resetView,camera,scene,step,seek,settle,floorScroll,groundSpeed,
  draw:()=>frame(0),get ready(){return ready;},get models(){return models;},get manifest(){return manifest;},get clips(){return clips;},
  get following(){return !!(activeClip()&&!activeClip().loop);},get figure(){return cam.figure&&state.player&&!activeClip()?cam.figure:null;},
  // The still subject, or the pose on screen for a followed clip, as [x,y,z] points.
  subject:()=>{if(!ready)return null;const c=activeClip(),head=state.view==='head';let a;
    if(c&&!c.loop){const r=poseRaw(scene());a=head&&r.head?r.head:r.all;}else{const st=stillCloud(stillKey());a=head&&st.head?st.head:st.all;}
    const out=[];for(let i=0;i<a.length;i+=3)out.push([a[i],a[i+1],a[i+2]]);return out;},
  debug:{flames:debug.flames,get gl(){return gl;},render:()=>{const s=scene();render(s);return s;},get freeze(){return !!debug.freeze;},set freeze(v){debug.freeze=!!v;invalidate();}}};
