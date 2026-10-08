'use strict';
const payload=JSON.parse(document.querySelector('#model-data').textContent);
const canvas=document.querySelector('#view');
const gl=canvas.getContext('webgl2',{antialias:true,alpha:true,preserveDrawingBuffer:true});
if(!gl)throw Error('WebGL 2 is required');
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
function translate(v){const m=M.id();m[12]=v[0];m[13]=v[1];m[14]=v[2];return m;}
function rotate(axis,angle){const m=M.id(),c=Math.cos(angle),s=Math.sin(angle);if(axis===0){m[5]=c;m[6]=s;m[9]=-s;m[10]=c;}if(axis===1){m[0]=c;m[2]=-s;m[8]=s;m[10]=c;}if(axis===2){m[0]=c;m[1]=s;m[4]=-s;m[5]=c;}return m;}
function transform(m,p,normal=false){return [0,1,2].map(r=>m[r]*p[0]+m[4+r]*p[1]+m[8+r]*p[2]+(normal?0:m[12+r]));}
const state={yaw:rad(-32),pitch:rad(15),distance:25,time:0,motion:true,clip:'perched',crackle:true,charged:false,sky:'day',focus:false,ortho:false,overlay:false,overlayKind:'body'};
const geo=payload.geo['minecraft:geometry'][0], bones=geo.bones;
const vertex=`#version 300 es
in vec3 pos;in vec3 normal;in vec2 uv;uniform mat4 vp;out vec3 n;out vec2 texUv;
void main(){gl_Position=vp*vec4(pos,1.);n=normal;texUv=uv;}`;
const fragment=`#version 300 es
precision highp float;in vec3 n;in vec2 texUv;uniform sampler2D tex;uniform sampler2D glow;uniform vec4 tint;uniform float charge;uniform float ambient;uniform bool emissiveSprite;out vec4 color;
void main(){vec4 p=texture(tex,texUv)*tint;if(p.a<(emissiveSprite?.01:.5))discard;if(emissiveSprite){color=vec4(p.rgb*charge,p.a);return;}float shade=ambient+.38*max(0.,dot(normalize(n),normalize(vec3(-.5,1.,-.8))))+.12*max(0.,dot(normalize(n),normalize(vec3(1.,.2,.5))));vec4 g=texture(glow,texUv);vec3 lit=p.rgb*min(1.,shade);color=vec4(mix(lit,g.rgb*charge,g.a),p.a);}`;
function shader(type,source){let s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
function upload(im){let t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL,true);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,im);for(let p of [gl.TEXTURE_MIN_FILTER,gl.TEXTURE_MAG_FILTER])gl.texParameteri(gl.TEXTURE_2D,p,gl.NEAREST);for(let p of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T])gl.texParameteri(gl.TEXTURE_2D,p,gl.CLAMP_TO_EDGE);return t;}
const loadImage=src=>new Promise((resolve,reject)=>{let im=new Image();im.onload=()=>resolve(im);im.onerror=()=>reject(Error('Image failed: '+src.slice(0,80)));im.src=src;});
function rotationMatrix(pivot,r=[0,0,0],p=[0,0,0],s=[1,1,1]){
 let m=M.mul(translate(p),translate(pivot));for(let axis of [2,1,0])m=M.mul(m,rotate(axis,rad(r[axis])*(axis===2?1:-1)));
 const sm=M.id();sm[0]=s[0];sm[5]=s[1];sm[10]=s[2];m=M.mul(m,sm);return M.mul(m,translate(pivot.map(v=>-v)));
}
function channel(ch,t,def){
 if(!ch)return def;if(Array.isArray(ch))return ch;
 let entries=Object.entries(ch),prev=entries[0];
 const val=v=>Array.isArray(v)?v:v.post||v.pre;
 for(let i=1;i<entries.length;i++){
  let next=entries[i];if(t<+next[0]){let v=val(prev[1]);if(prev[1].lerp_mode==='step')return v;let f=Math.max(0,(t-+prev[0])/(+next[0]-+prev[0]));return v.map((n,j)=>n+((next[1].pre||val(next[1]))[j]-n)*f);}prev=next;
 }return val(prev[1]);
}
function pose(){
 const matrices={},clip=payload.clips[state.clip];
 for(let b of bones){let c=clip.bones[b.name]||{};let scale=channel(c.scale,state.time,[1,1,1]);
 let m=rotationMatrix(b.pivot,channel(c.rotation,state.time,[0,0,0]),channel(c.position,state.time,[0,0,0]),scale);matrices[b.name]=b.parent?M.mul(matrices[b.parent],m):m;}return matrices;
}
const triangleOrder=[0,1,2,0,2,3];
const geometry=bones.map(b=>{
 let data=[];
 for(let cube of b.cubes){let cm=rotationMatrix(cube.pivot||[0,0,0],cube.rotation);for(let [name,f] of Object.entries(faces(cube))){const {uv:[u,v],uv_size:[w,h]}=cube.uv[name],tw=geo.description.texture_width,th=geo.description.texture_height;
 const uv=[[u/tw,1-(v+h)/th],[(u+w)/tw,1-(v+h)/th],[(u+w)/tw,1-v/th],[u/tw,1-v/th]];
 for(let k of triangleOrder)data.push(...transform(cm,f.p[k]),...transform(cm,f.n,true),...uv[k]);}}
 return {name:b.name,data:new Float32Array(data)};
});
const mesh=new Float32Array(geometry.reduce((n,g)=>n+g.data.length,0));
function modelMesh(){let matrices=pose(),at=0;for(let g of geometry){let m=matrices[g.name],d=g.data;const a=[m[0],m[1],m[2]],b=[m[4],m[5],m[6]],c=[m[8],m[9],m[10]],na=cross(b,c),nb=cross(c,a),nc=cross(a,b);for(let i=0;i<d.length;i+=8){for(let j=0;j<3;j++){mesh[at+j]=(m[j]*d[i]+m[4+j]*d[i+1]+m[8+j]*d[i+2]+m[12+j])/16;mesh[at+3+j]=na[j]*d[i+3]+nb[j]*d[i+4]+nc[j]*d[i+5];}mesh[at+6]=d[i+6];mesh[at+7]=d[i+7];at+=8;}}return mesh;}
function box(data,center,size){for(let f of Object.values(faces({origin:center.map((v,i)=>v-size[i]/2),size})))for(let k of triangleOrder)data.push(...f.p[k],...f.n,0,0);}
let program,buffer,vao,uniforms,skin,glow,white,clear,sparkTexture;
function submit(data,tex=white,mask=clear,tint=[1,1,1,1],charge=1,sprite=false){
 gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,data instanceof Float32Array?data:new Float32Array(data),gl.DYNAMIC_DRAW);
 gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,tex);gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,mask);gl.uniform4fv(uniforms.tint,tint);gl.uniform1f(uniforms.charge,charge);gl.uniform1i(uniforms.emissiveSprite,sprite?1:0);gl.drawArrays(gl.TRIANGLES,0,data.length/8);
}
const floor=[],grid=[],stone=[],stoneLight=[],stoneDark=[],clouds=[],player={skin:[],shirt:[],pants:[],boots:[],hair:[]};
box(floor,[0,-.18,0],[24,.25,30]);
for(let i=-12;i<=12;i++)box(grid,[i,-.043,0],[.012,.008,30]);for(let i=-15;i<=15;i++)box(grid,[0,-.043,i],[24,.008,.012]);
// Fractured vertical rock columns: broad roots taper to an uneven gripping crown.
// Adjacent columns have different ledge heights, avoiding continuous slab seams.
for(let ix=-5;ix<=5;ix++)for(let iz=-4;iz<=4;iz++){
 const x=ix*.34,z=.28+iz*.34,r=Math.hypot(x/1.9,(z-.28)/1.55);
 if(r>1.1)continue;
 const noise=Math.sin(ix*19.3+iz*7.1),crown=r<.59;
 const top=crown?3.98+noise*.11:Math.max(.22,4.02-(r-.59)*6.2+noise*.22);
 let bottom=-.035,course=0;
 while(bottom<top){
  const height=Math.min(top-bottom,.48+((ix*ix+iz*iz+course*3)%5)*.14);
  const taper=1-.1*bottom,offset=Math.sin(ix*3+iz*5+course)*.035;
  const dest=(ix+iz*2+course*3)%7===0?stoneLight:(ix*2-iz+course)%5===0?stoneDark:stone;
  box(dest,[x*taper+offset,bottom+height/2,z*taper],[.39,height+.025,.39]);
  bottom+=height;course++;
 }
}
// Two buried gripping shelves support the pads, with chipped edges under the claws.
for(const side of [-1,1]){
 box(stone,[side*.72,3.94,.26],[.74,.42,.96]);
 box(stoneLight,[side*.75,4.075,.22],[.57,.16,.72]);
 box(stone,[side*.75,3.84,-.32],[.66,.32,.32]);
}
// Slanted splinters and unequal buttresses interrupt the square column silhouettes.
function rockShard(center,size,angles,dest){
 const vertices=[];box(vertices,center,size);const matrix=rotationMatrix(center,angles);
 for(let i=0;i<vertices.length;i+=8){
  const p=transform(matrix,vertices.slice(i,i+3)),n=transform(matrix,vertices.slice(i+3,i+6),true);
  dest.push(...p,...n,0,0);
 }
}
for(let i=0;i<11;i++){
 const a=i*2.399,r=1.05+(i%3)*.2,high=.9+(i%4)*.37;
 rockShard([Math.cos(a)*r,high*.5,.25+Math.sin(a)*r*.8],
 [.32+(i%2)*.16,high,.48],[Math.sin(a)*12,0,-Math.cos(a)*14],i%3===0?stoneLight:stoneDark);
}
rockShard([-.72,3.28,.64],[.35,1.1,.38],[8,0,-13],stoneLight);
rockShard([.92,2.77,-.1],[.38,1.3,.35],[-9,0,11],stone);
for(let i=0;i<23;i++){let a=i*2.4,r=18+i%5*3;box(clouds,[Math.sin(a)*r,-2.5-i%3*.4,Math.cos(a)*r],[7+i%4*2,.7+i%3*.35,4+i%5]);}
// 1.8-block player figure, beside the wing silhouette rather than beneath it.
const px=-4.8,pz=-6.4;
box(player.skin,[px,1.55,pz],[.5,.5,.5]);box(player.hair,[px,1.77,pz+.015],[.52,.10,.52]);
box(player.shirt,[px,.985,pz],[.5,.63,.25]);for(let s of [-1,1]){box(player.skin,[px+s*.375,.96,pz],[.25,.6,.25]);box(player.shirt,[px+s*.375,1.18,pz],[.25,.2,.26]);box(player.pants,[px+s*.125,.37,pz],[.24,.6,.25]);box(player.boots,[px+s*.125,.045,pz-.03],[.25,.09,.31]);}
function shimmer(eye,at){
 if(!state.crackle)return;
 const data=payload.spark_metadata,mats=pose(),normal=norm(sub(eye,at)),right=norm(cross([0,1,0],normal)),up=cross(normal,right);
 const mode=data[state.charged?'charged':'normal'];
 gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE);gl.depthMask(false);
 data.anchors.forEach((anchor,index)=>{
  for(let copy=0;copy<(anchor.sequence==='spark'?1:mode.copies_per_anchor);copy++){
   const t=state.time+anchor.phase+copy*.119,sequence=data.sequences[anchor.sequence];
   const frame=sequence.frames[Math.floor(t*sequence.fps)%sequence.frames.length];
   const center=transform(mats[anchor.bone],anchor.pivot_model_pixels).map(v=>v/16);
   const jitter=data.jitter_blocks*(state.charged?1.5:1),jx=Math.sin(t*31+copy*7)*jitter,jy=Math.cos(t*27+index)*jitter;
   for(let k=0;k<3;k++)center[k]+=right[k]*jx+up[k]*jy+normal[k]*.025;
   const size=anchor.size_blocks*mode.size_multiplier*(copy? .78:1),angle=index*.67+copy*1.8+Math.sin(t*6)*.16,c=Math.cos(angle),s=Math.sin(angle);
   const r=right.map((v,k)=>(v*c+up[k]*s)*size*.5),u=up.map((v,k)=>(v*c-right[k]*s)*size*.5);
   const [fx,fy]=frame.uv,[fw,fh]=frame.size,[tw,th]=data.sheet_size;
   const coords=[[-1,-1],[1,-1],[1,1],[-1,1]],uv=[[fx/tw,1-(fy+fh)/th],[(fx+fw)/tw,1-(fy+fh)/th],[(fx+fw)/tw,1-fy/th],[fx/tw,1-fy/th]],verts=[];
   for(let k of triangleOrder)verts.push(...center.map((v,j)=>v+r[j]*coords[k][0]+u[j]*coords[k][1]),...normal,...uv[k]);
   const alpha=data.alpha_range[0]+(data.alpha_range[1]-data.alpha_range[0])*(.5+.5*Math.sin(t*43+index*2+copy));
   submit(verts,sparkTexture,clear,[1,1,1,Math.min(1,alpha*mode.alpha_multiplier)],mode.brightness,true);
  }
 });
 gl.depthMask(true);gl.disable(gl.BLEND);
}
function scene(eye,at){
 submit(clouds,white,clear,state.sky==='day'?[.88,.93,.95,1]:[.22,.29,.38,1]);
 submit(floor,white,clear,state.sky==='day'?[.45,.52,.55,1]:[.14,.19,.24,1]);submit(grid,white,clear,state.sky==='day'?[.65,.72,.70,1]:[.30,.36,.41,1]);
 if(state.clip==='perched'){submit(stone,white,clear,[.40,.45,.49,1]);submit(stoneLight,white,clear,[.53,.57,.58,1]);submit(stoneDark,white,clear,[.29,.34,.39,1]);}
 for(let [key,tint] of Object.entries({skin:[.70,.48,.34,1],shirt:[.19,.49,.49,1],pants:[.23,.26,.38,1],boots:[.16,.19,.24,1],hair:[.22,.15,.12,1]}))submit(player[key],white,clear,tint);
 submit(modelMesh(),skin,glow,[1,1,1,1],state.charged?1.55:.70);shimmer(eye,at);
}
function orthographic(width,height,near,far){let m=M.id();m[0]=2/width;m[5]=2/height;m[10]=-2/(far-near);m[14]=-(far+near)/(far-near);return m;}
const overlayImage=new Image();overlayImage.src=payload.concept;const faceOverlayImage=new Image();faceOverlayImage.src=payload.face_concept;
function overlayDraw(){
 const layer=document.querySelector('#concept-overlay');layer.hidden=!state.overlay;
 if(!state.overlay||!overlayImage.complete)return;
 layer.width=canvas.width;layer.height=canvas.height;layer.style.height=canvas.clientHeight+'px';
 const c=layer.getContext('2d');c.clearRect(0,0,layer.width,layer.height);c.globalAlpha=.38;
 const faceMode=state.overlayKind==='face';const crop=faceMode?[0,0,930,1024]:state.clip==='perched'?[0,0,705,1024]:[710,0,826,510];
 const h=layer.height*.94,w=h*crop[2]/crop[3];
 c.drawImage(faceMode?faceOverlayImage:overlayImage,...crop,(layer.width-w)/2,layer.height*.03,w,h);
}
function draw(){
 const dpr=Math.min(devicePixelRatio,1.6),w=Math.round(canvas.clientWidth*dpr),h=Math.round(canvas.clientHeight*dpr);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h;}
 gl.viewport(0,0,w,h);gl.clearColor(0,0,0,0);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.disable(gl.CULL_FACE);
 let perch=state.clip==='perched',attack=state.clip==='hunched'||state.clip==='peck';let at=[0,perch?7.1:state.clip==='screech'?5.4:state.clip==='flight'||state.clip==='grab'?5.0:3.3,attack?.5:-.9];if(state.focus){const head=bones.find(b=>b.name==='head');at=transform(pose().head,head.pivot).map(v=>v/16);at[2]-=.3;}if(state.ortho&&state.pitch>1.5)at[2]=state.clip==='flight'||state.clip==='grab'?.35:-.9;let d=(state.focus?7.3:state.distance)/Math.min(1,w/h);
 let eye=[at[0]+Math.sin(state.yaw)*Math.cos(state.pitch)*d,at[1]+Math.sin(state.pitch)*d,at[2]-Math.cos(state.yaw)*Math.cos(state.pitch)*d];
 const oh=state.focus?4.5:state.pitch>1.5?(state.clip==='flight'||state.clip==='grab'?Math.max(17,30/(w/h)):Math.max(20,30/(w/h))):perch?15.5:state.clip==='screech'?13.5:state.clip==='flight'||state.clip==='grab'?Math.max(13,(Math.abs(state.yaw)>1?23:29)/(w/h)):attack?Math.max(11.4,23/(w/h)):10.2;const projection=state.ortho?orthographic(oh*w/h,oh,.03,150):M.perspective(rad(39),w/h,.03,150);
 gl.useProgram(program);gl.bindVertexArray(vao);gl.uniformMatrix4fv(uniforms.vp,false,M.mul(projection,M.lookAt(eye,at)));gl.uniform1i(uniforms.tex,0);gl.uniform1i(uniforms.glow,1);gl.uniform1f(uniforms.ambient,state.sky==='day'?.70:.35);scene(eye,at);gl.bindVertexArray(null);overlayDraw();
 document.querySelector('#timeline').value=state.time;document.querySelector('#time').textContent=state.time.toFixed(2)+' / '+payload.clips[state.clip].animation_length.toFixed(1)+' s';
}
function sync(){
 for(let b of document.querySelectorAll('[data-clip]')){b.classList.toggle('active',b.dataset.clip===state.clip);b.setAttribute('aria-pressed',b.dataset.clip===state.clip);}
 for(let [id,on] of Object.entries({motion:state.motion,crackle:state.crackle,charged:state.charged,focus:state.focus,ortho:state.ortho,overlay:state.overlay})){let b=document.querySelector('#'+id);b.classList.toggle('active',on);b.setAttribute('aria-pressed',on);}
 document.querySelector('#motion').textContent=state.motion?'Pause':'Play';document.querySelector('#charged').textContent=state.charged?'Charged':'Normal';document.querySelector('#crackle').textContent='Crackle '+(state.crackle?'on':'off');document.querySelector('#timeline').max=payload.clips[state.clip].animation_length;
 document.querySelector('#pose-label').textContent={perched:'PERCHED / SPIRE',hunched:'HUNCHED / GROUND FIGHT',peck:'LOW PECK / PLAYER HEIGHT',grab:'TALONS / GRAB',spread:'WINGS SPREAD',flight:'FLIGHT / FLAP LOOP',screech:'SCREECH / OPEN BEAK'}[state.clip];
}
function controls(){
 document.querySelector('#ortho').onclick=()=>{state.ortho=!state.ortho;sync();};
 document.querySelector('#overlay').onclick=()=>{
  state.overlay=!state.overlay;
  if(state.overlay){state.overlayKind=state.focus?'face':'body';state.ortho=true;state.motion=false;state.time=0;if(!state.focus&&state.clip!=='perched')state.clip='spread';state.yaw=rad(state.focus||state.clip==='perched'?90:180);state.pitch=rad(state.focus||state.clip==='perched'?0:89.99);}
  sync();
 };
 document.querySelector('#capture').onclick=()=>{state.motion=false;sync();draw();const shot=document.createElement('canvas');shot.width=canvas.width;shot.height=canvas.height;const ctx=shot.getContext('2d');ctx.fillStyle=state.sky==='day'?'#85acc0':'#192636';ctx.fillRect(0,0,shot.width,shot.height);ctx.drawImage(canvas,0,0);if(state.overlay)ctx.drawImage(document.querySelector('#concept-overlay'),0,0);document.querySelector('#capture-image').src=shot.toDataURL('image/png');document.querySelector('#capture-dialog').showModal();};
 document.querySelector('#close-capture').onclick=()=>document.querySelector('#capture-dialog').close();
 let drag=null;canvas.onpointerdown=e=>{state.overlay=false;sync();drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId);};canvas.onpointermove=e=>{if(!drag)return;state.yaw-=(e.clientX-drag[0])*.007;state.pitch=Math.max(-.6,Math.min(1.54,state.pitch+(e.clientY-drag[1])*.007));drag=[e.clientX,e.clientY];};canvas.onpointerup=canvas.onpointercancel=()=>drag=null;
 canvas.addEventListener('wheel',e=>{e.preventDefault();state.focus=false;state.distance=Math.max(6,Math.min(48,state.distance*Math.exp(e.deltaY*.001)));sync();},{passive:false});
 const views={front:[0,9],side:[90,0],hero:[-32,15],below:[-25,-17],top:[180,89.99],flightSide:[60,16],flightHero:[-35,30]};
 document.querySelectorAll('[data-view]').forEach(b=>b.onclick=()=>{state.overlay=false;sync();[state.yaw,state.pitch]=views[b.dataset.view].map(rad);document.querySelectorAll('[data-view]').forEach(x=>x.classList.toggle('active',x===b));});
 document.querySelectorAll('[data-clip]').forEach(b=>b.onclick=()=>{state.clip=b.dataset.clip;state.time=0;state.distance=state.clip==='perched'?25:state.clip==='flight'||state.clip==='spread'?29:24;state.focus=false;state.overlay=false;state.ortho=true;[state.yaw,state.pitch]=({perched:[90,0],hunched:[90,0],peck:[90,0],flight:[60,16],spread:[180,89.99],screech:[-32,8],grab:[-32,8]}[state.clip]).map(rad);sync();});
 for(let id of ['motion','crackle','charged','focus'])document.querySelector('#'+id).onclick=()=>{state[id]=!state[id];sync();};
 document.querySelector('#timeline').oninput=e=>{state.time=+e.target.value;state.motion=false;sync();};
 document.querySelectorAll('[data-sky]').forEach(b=>b.onclick=()=>{state.sky=b.dataset.sky;document.querySelector('.stage').dataset.sky=state.sky;document.querySelectorAll('[data-sky]').forEach(x=>x.classList.toggle('active',x===b));});
 document.querySelector('#replay').onclick=()=>{state.time=0;state.motion=true;sync();};
}
async function main(){
 program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,vertex));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,fragment));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));
 uniforms=Object.fromEntries(['vp','tex','glow','tint','charge','ambient','emissiveSprite'].map(k=>[k,gl.getUniformLocation(program,k)]));vao=gl.createVertexArray();gl.bindVertexArray(vao);buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);
 for(let [name,n,offset] of [['pos',3,0],['normal',3,12],['uv',2,24]]){let location=gl.getAttribLocation(program,name);gl.enableVertexAttribArray(location);gl.vertexAttribPointer(location,n,gl.FLOAT,false,32,offset);}
 [skin,glow,sparkTexture]=await Promise.all([payload.texture,payload.glow,payload.sparks].map(async src=>upload(await loadImage(src))));
 for(let blank of [false,true]){let c=document.createElement('canvas');c.width=c.height=1;if(!blank){let ctx=c.getContext('2d');ctx.fillStyle='#fff';ctx.fillRect(0,0,1,1);}if(blank)clear=upload(c);else white=upload(c);}
 document.querySelector('#stats').textContent=payload.stats.bones+' bones · '+payload.stats.cubes+' cubes';controls();sync();document.body.dataset.ready='true';
 // Expose a review hook for reproducible screenshots and geometry checks.
 window.rocPreview={state,draw,pose,sync,stats:payload.stats};
 let previous=performance.now();function frame(now){const dt=Math.min(.1,(now-previous)/1000);previous=now;if(state.motion)state.time=(state.time+dt)%payload.clips[state.clip].animation_length;draw();requestAnimationFrame(frame);}requestAnimationFrame(frame);
}
main().catch(e=>{document.body.dataset.error=e.message;document.querySelector('#status').textContent=e.message;console.error(e);});
