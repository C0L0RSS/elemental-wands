
'use strict';
const mesh=__MESH_DATA__,bones=__BONES_DATA__,clips=__ANIMATION_DATA__;
let clipName='idle',motionTime=0,playing=true,motionLast=0,completedLoops=0;

const review=__REWORKED_DATA__;
let playerEye=false,distance=10;
function floorVertices(){for(let x=-192;x<192;x+=16)for(let z=-192;z<192;z+=16){const drift=clipName==='walk'?((motionTime+completedLoops*2)*10/1.2)%32:0;const q=[[x,-.08,z+drift],[x+15.8,-.08,z+drift],[x+15.8,-.08,z+15.8+drift],[x,-.08,z+15.8+drift]];for(const i of [0,1,2,0,2,3])vertices.push(...q[i],0,1,0,0,0,((x+z)/16)%2===0?-1:-2)}}
function previewChannels(name,ch){
 if(!document.querySelector('#shell').checked)return ch;
 const smooth=u=>{u=Math.max(0,Math.min(1,u));return u*u*(3-2*u)};
 const tick=motionTime*20,phase=clipName==='phase_change',pulse=clipName==='core_pulse';
 const charge=pulse?(tick<10?.25*tick/10:tick<50?.25+.75*(tick-10)/40:Math.max(0,1-(tick-50)/12)):0;
 const open=(phase?smooth((tick-32)/24):1)*(1+.3*charge);
 const r=sample(ch.rotation,motionTime).slice(),pos=sample(ch.position,motionTime).slice(),scale=ch.scale?sample(ch.scale,motionTime).slice():[1,1,1];
 if(name.startsWith('chest_plate_')){const sign=name.endsWith('_-1')?-1:1;r[1]-=68*sign*open;pos[0]+=3*sign*open;pos[2]-=2*open;}
 if(name==='core'){const jitter=open*(phase?1:.35+.65*charge);pos[0]+=Math.sin(motionTime*31)*.5*jitter;pos[1]+=Math.sin(motionTime*21)*.4*jitter;pos[2]-=12*open;r[1]+=Math.sin(motionTime*17)*8*jitter;r[2]+=Math.sin(motionTime*23)*7*jitter;const size=phase?1:(1.04+.035*Math.sin(tick*1.7))*(1+.35*charge);[.65,.3,1.4].forEach((v,i)=>scale[i]*=size*(1+v*open));}
 return {rotation:{0:r},position:{0:pos},scale:{0:scale}};
}

function selectedClip(){return clips['animation.fractured_guardian.'+clipName]}
const keyCache=new WeakMap();
function sample(keys,t){if(!keys)return [0,0,0];let es=keyCache.get(keys);if(!es){es=Object.entries(keys).map(([k,v])=>[+k,v]).sort((a,b)=>a[0]-b[0]);keyCache.set(keys,es)}if(t<=es[0][0])return es[0][1];let lo=0,hi=es.length-1;while(lo+1<hi){const mid=(lo+hi)>>1;if(es[mid][0]<t)lo=mid;else hi=mid}const [a,x]=es[lo],[b,y]=es[hi];if(t>=b)return y;return x.map((v,j)=>v+(y[j]-v)*(t-a)/(b-a))}
let posedBones={};
function mul(r,v){return [r[0]*v[0]+r[1]*v[1]+r[2]*v[2],r[3]*v[0]+r[4]*v[1]+r[5]*v[2],r[6]*v[0]+r[7]*v[1]+r[8]*v[2]]}
function buildPoses(){posedBones={};for(const [name,b] of Object.entries(bones)){const ch=previewChannels(name,selectedClip().bones[name]||{}),r=sample(ch.rotation,motionTime).map((x,i)=>x*Math.PI/180*(i===1?1:-1)),p=b.pivot,pos=sample(ch.position,motionTime);const [cx,sx,cy,sy,cz,sz]=[Math.cos(r[0]),Math.sin(r[0]),Math.cos(r[1]),Math.sin(r[1]),Math.cos(r[2]),Math.sin(r[2])];let rot=[cz*cy,cz*sy*sx-sz*cx,cz*sy*cx+sz*sx,sz*cy,sz*sy*sx+cz*cx,sz*sy*cx-cz*sx,-sy,cy*sx,cy*cx];const scale=ch.scale?sample(ch.scale,motionTime):[1,1,1];rot=rot.map((v,i)=>v*scale[i%3]);let rp=mul(rot,p),offset=p.map((v,i)=>v-rp[i]+pos[i]);if(b.parent){const par=posedBones[b.parent],result=[];for(let row=0;row<3;row++)for(let col=0;col<3;col++)result.push(par.r[row*3]*rot[col]+par.r[row*3+1]*rot[3+col]+par.r[row*3+2]*rot[6+col]);offset=mul(par.r,offset).map((v,i)=>v+par.p[i]);rot=result}posedBones[name]={r:rot,p:offset}}}
function poseVertex(v,name){const m=posedBones[name];return mul(m.r,v).map((x,i)=>x+m.p[i])}


const canvas=document.querySelector('canvas'),gl=canvas.getContext('webgl',{antialias:true});
if(!gl){document.querySelector('.hint').textContent='WebGL unavailable. Enable browser hardware acceleration.';throw Error('WebGL unavailable')}
const vert=`attribute vec3 pos;attribute vec3 normal;attribute vec2 uv;attribute float magic;uniform mat3 camera;uniform vec2 extent;uniform vec3 eye;uniform float player;uniform float aspect;varying vec3 n;varying vec2 texcoord;varying float lightFace;void main(){vec3 p=camera*(pos-eye);if(player>.5){float d=-p.z;gl_Position=vec4(p.x/(.70*aspect),p.y/.70,1.001334*d-2.001334,d);}else{gl_Position=vec4(p.x/extent.x,p.y/extent.y,-p.z/500.,1.);}n=camera*normal;texcoord=uv;lightFace=magic;}`;
const frag=`precision mediump float;varying vec3 n;varying vec2 texcoord;varying float lightFace;uniform sampler2D atlas;uniform float clay;void main(){vec3 light=normalize(vec3(-.55,.8,.65));float shade=.70+.45*max(0.,dot(normalize(n),light))+.04*max(0.,n.y);vec3 color=texture2D(atlas,texcoord).rgb;if(clay>.5){color=vec3(.68,.67,.64);}if(lightFace<-.5){color=mix(vec3(.61,.65,.62),vec3(.72,.75,.72),step(-1.5,lightFace));}float lit=0.;if(clay<.5 && lightFace>.5 && color.g>color.r+.032 && color.b>color.r-.02 && color.g>.61){lit=1.;}gl_FragColor=vec4(color*mix(shade,1.12,lit),1.);}`;
function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s}
const program=gl.createProgram();gl.attachShader(program,shader(gl.VERTEX_SHADER,vert));gl.attachShader(program,shader(gl.FRAGMENT_SHADER,frag));gl.linkProgram(program);if(!gl.getProgramParameter(program,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(program));gl.useProgram(program);
const vertices=[];
function sub(a,b){return a.map((x,i)=>x-b[i])}function cross(a,b){return[a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]]}
function updateVertices(){buildPoses();vertices.length=0;for(const cube of mesh){const posed=cube.vertices.map(v=>poseVertex(v,cube.bone));for(let f=0;f<cube.faces.length;f++){const face=cube.faces[f],q=face.map(i=>posed[i]),n=cross(sub(q[1],q[0]),sub(q[2],q[0])),length=Math.hypot(...n)||1,normal=n.map(x=>x/length);for(const i of [0,1,2,0,2,3])vertices.push(...q[i],...normal,...cube.uvs[f][i],cube.magic[f]?1:0)}}floorVertices();}updateVertices();
const triangleCount=vertices.length/9;const buffer=gl.createBuffer();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(vertices),gl.DYNAMIC_DRAW);
for(const [name,count,offset] of [['pos',3,0],['normal',3,12],['uv',2,24],['magic',1,32]]){const a=gl.getAttribLocation(program,name);gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,count,gl.FLOAT,false,36,offset)}
const texture=gl.createTexture();gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,texture);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);gl.uniform1i(gl.getUniformLocation(program,'atlas'),0);
const clayLocation=gl.getUniformLocation(program,'clay');let clay=false;document.querySelector('#clay').onclick=()=>{clay=!clay;document.querySelector('#clay').classList.toggle('active',clay);draw()};
const atlasImage=new Image();atlasImage.onload=()=>{gl.bindTexture(gl.TEXTURE_2D,texture);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGB,gl.RGB,gl.UNSIGNED_BYTE,atlasImage);draw()};atlasImage.src='data:image/png;base64,__ATLAS_DATA__';
const playerLocation=gl.getUniformLocation(program,'player'),eyeLocation=gl.getUniformLocation(program,'eye'),aspectLocation=gl.getUniformLocation(program,'aspect');
const cameraLocation=gl.getUniformLocation(program,'camera'),extentLocation=gl.getUniformLocation(program,'extent');
gl.enable(gl.DEPTH_TEST);gl.clearColor(239/255,237/255,232/255,1);
let yaw=-32*Math.PI/180,pitch=14*Math.PI/180,zoom=1,spinning=false,last=0;
function draw(){updateVertices();gl.bindBuffer(gl.ARRAY_BUFFER,buffer);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(vertices),gl.DYNAMIC_DRAW);const dpr=Math.min(devicePixelRatio,2),w=Math.round(canvas.clientWidth*dpr),h=Math.round(canvas.clientHeight*dpr);if(canvas.width!==w||canvas.height!==h){canvas.width=w;canvas.height=h}gl.viewport(0,0,w,h);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);const a=yaw,b=playerEye?-Math.atan2(35.2,distance*16):pitch,ca=Math.cos(a),sa=Math.sin(a),cb=Math.cos(b),sb=Math.sin(b);gl.uniformMatrix3fv(cameraLocation,false,new Float32Array([ca,-sa*sb,sa*cb,0,cb,sb,sa,ca*sb,-ca*cb]));const extent=Math.max(90,90*h/w)/zoom;gl.uniform2f(extentLocation,extent*w/h,extent);gl.uniform1f(clayLocation,clay?1:0);gl.uniform1f(playerLocation,playerEye?1:0);gl.uniform1f(aspectLocation,w/h);gl.uniform3fv(eyeLocation,playerEye?[Math.sin(yaw)*distance*16,28.8,-Math.cos(yaw)*distance*16]:[0,60,0]);gl.drawArrays(gl.TRIANGLES,0,triangleCount)}
function active(name){document.querySelectorAll('[data-view]').forEach(b=>b.classList.toggle('active',b.dataset.view===name))}
function stop(){spinning=false;document.querySelector('#spin').classList.remove('active')}
document.querySelectorAll('[data-view]').forEach(button=>button.onclick=()=>{stop();const v=button.dataset.view;playerEye=v==='player';[yaw,pitch]=({player:[0,0],hero:[-32,14],front:[0,0],side:[90,0],back:[180,0]})[v].map(x=>x*Math.PI/180);zoom=1;active(v);draw()});
document.querySelector('#spin').onclick=()=>{spinning=!spinning;document.querySelector('#spin').classList.toggle('active',spinning);active('');last=0;if(spinning)requestAnimationFrame(tick)};
function tick(t){if(!spinning)return;if(last)yaw+=(t-last)*.00022;last=t;draw();requestAnimationFrame(tick)}
let drag=null;canvas.onpointerdown=e=>{stop();active('');drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId)};canvas.onpointermove=e=>{if(!drag)return;yaw-=(e.clientX-drag[0])*.008;pitch=Math.max(-1.1,Math.min(1.1,pitch+(e.clientY-drag[1])*.008));drag=[e.clientX,e.clientY];draw()};canvas.onpointerup=canvas.onpointercancel=()=>drag=null;canvas.onwheel=e=>{e.preventDefault();zoom=Math.max(.55,Math.min(2.4,zoom*Math.exp(-e.deltaY*.001)));draw()};addEventListener('keydown',e=>{if(e.target.matches('input,select,button')||!e.key.startsWith('Arrow'))return;e.preventDefault();stop();active('');if(e.key==='ArrowLeft')yaw-=.1;if(e.key==='ArrowRight')yaw+=.1;if(e.key==='ArrowUp')pitch=Math.min(1.1,pitch+.1);if(e.key==='ArrowDown')pitch=Math.max(-1.1,pitch-.1);draw()});addEventListener('resize',draw);draw();
const timeline=document.querySelector('#timeline'),clockText=document.querySelector('#time');
function updateClock(){timeline.value=motionTime;clockText.textContent=motionTime.toFixed(2)+' / '+selectedClip().animation_length.toFixed(2)+' s'}
function resetMotion(){completedLoops=0;motionTime=0;motionLast=0;playing=true;timeline.max=selectedClip().animation_length;document.querySelector('#pause').textContent='Pause';updateClock();draw()}
document.querySelector('#clip').onchange=e=>selectClip(e.target.value);document.querySelector('#replay').onclick=resetMotion;
document.querySelector('#pause').onclick=()=>{playing=!playing;motionLast=0;document.querySelector('#pause').textContent=playing?'Pause':'Play'};
timeline.oninput=()=>{playing=false;document.querySelector('#pause').textContent='Play';motionTime=+timeline.value;updateClock();draw()};
function animate(now){if(playing){if(motionLast){motionTime+=(now-motionLast)/1000*Number(document.querySelector('#speed').value);const c=selectedClip();if(motionTime>=c.animation_length){if(c.loop||document.querySelector('#loop').checked){completedLoops+=Math.floor(motionTime/c.animation_length);motionTime%=c.animation_length;}else{motionTime=c.animation_length;playing=false;document.querySelector('#pause').textContent='Play'}}}updateClock();draw()}motionLast=now;requestAnimationFrame(animate)}requestAnimationFrame(animate);

function selectClip(name){clipName=name;document.querySelector('#clip').value=name;document.querySelectorAll('[data-clip]').forEach(b=>b.classList.toggle('selected',b.dataset.clip===name));document.querySelector('#selected-name').textContent=name;document.querySelector('#description').textContent=review[name]?.description||'Unchanged in this pass. '+selectedClip().animation_length.toFixed(2)+' seconds. '+(name==='intro'?'Approved cinematic rig only; camera, other actors and world effects are not simulated.':name.startsWith('leap_')?'Local rig pose; entity flight and landing effects run in Minecraft.':'');document.querySelector('#selected-badge').hidden=!review[name];document.querySelector('#shell').checked=['phase_change','core_pulse','slam_fast','throw_fast'].includes(name);document.querySelector('#events').replaceChildren();for(const [t,label] of review[name]?.events||[]){const b=document.createElement('button');b.textContent=label+' · '+t.toFixed(3).replace(/0+$/,'').replace(/\.$/,'')+' s';b.onclick=()=>{playing=false;document.querySelector('#pause').textContent='Play';motionTime=t;updateClock();draw()};document.querySelector('#events').append(b)}resetMotion();}
for(const key of Object.keys(clips)){const name=key.split('.').pop(),option=document.createElement('option');option.value=name;option.textContent=name+(review[name]?' ★ Reworked tonight':'');document.querySelector('#clip').append(option);const button=document.createElement('button');button.dataset.clip=name;button.innerHTML='<strong>'+name+'</strong><span>'+clips[key].animation_length.toFixed(2)+' s</span>'+(review[name]?'<em class="badge">Reworked tonight</em>':'<em class="unchanged">Unchanged</em>');button.onclick=()=>selectClip(name);document.querySelector('#clip-list').append(button);}
for(const name of Object.keys(review)){const b=document.createElement('button');b.textContent=name;b.dataset.clip=name;b.onclick=()=>selectClip(name);document.querySelector('#reworked-links').append(b)}
document.querySelector('#shell').onchange=draw;
document.querySelector('#distance').oninput=e=>{distance=Number(e.target.value);document.querySelector('#distance-value').textContent=distance+' blocks';draw()};
selectClip('idle');
