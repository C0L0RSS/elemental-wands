'use strict';
const {M,rad,norm,bakeModel,poseBones}=PyreCore;
const $=s=>document.querySelector(s),canvas=$('#view');
const gl=canvas.getContext('webgl2',{antialias:true,preserveDrawingBuffer:true});
const state={model:'king',view:'hero',yaw:-32,pitch:12,distance:10.8,target:[.65,3.1,0],player:true,glow:true,fire:true,clay:false,light:'day',motion:false,time:.65,playing:true};
let models,tex,mask,fireTex,manifest,ready=false;
function shader(type,source){const s=gl.createShader(type);gl.shaderSource(s,source);gl.compileShader(s);if(!gl.getShaderParameter(s,gl.COMPILE_STATUS))throw Error(gl.getShaderInfoLog(s));return s;}
const prog=gl.createProgram();
gl.attachShader(prog,shader(gl.VERTEX_SHADER,`#version 300 es
in vec3 pos;in vec3 normal;in vec2 uv;in vec3 col;in float opacity;uniform mat4 vp;out float alpha;out vec3 n;out vec2 t;out vec3 tint;
void main(){gl_Position=vp*vec4(pos,1.);n=normal;t=uv;tint=col;alpha=opacity;}`));
gl.attachShader(prog,shader(gl.FRAGMENT_SHADER,`#version 300 es
precision highp float;in float alpha;in vec3 n;in vec2 t;in vec3 tint;out vec4 outColor;
uniform sampler2D skin;uniform sampler2D glowmask;uniform bool effect;uniform bool textured;uniform bool glow;uniform bool clay;uniform float level;
void main(){vec4 c=textured?texture(skin,t):vec4(tint,1.);if(c.a<.02)discard;
vec3 nn=normalize(gl_FrontFacing?n:-n);float shade=min(1.,.48+.40*max(0.,dot(nn,normalize(vec3(-.5,1.,-.8))))+.12*max(0.,dot(nn,normalize(vec3(1.,.2,.5)))));
vec3 base=clay&&textured?vec3(.66,.64,.60):c.rgb;vec3 rgb=base*shade*level;
vec4 g=textured&&glow&&!clay?texture(glowmask,t):vec4(0.);rgb=mix(rgb,max(rgb,g.rgb),g.a);
if(effect)rgb=c.rgb;outColor=vec4(rgb,c.a*alpha);}`));
gl.linkProgram(prog);if(!gl.getProgramParameter(prog,gl.LINK_STATUS))throw Error(gl.getProgramInfoLog(prog));
const u={};for(const n of ['effect','vp','skin','glowmask','textured','glow','clay','level'])u[n]=gl.getUniformLocation(prog,n);
const vao=gl.createVertexArray(),buf=gl.createBuffer();gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,buf);
let offset=0;for(const [name,size]of [['pos',3],['normal',3],['uv',2],['col',3],['opacity',1]]){let loc=gl.getAttribLocation(prog,name);gl.enableVertexAttribArray(loc);gl.vertexAttribPointer(loc,size,gl.FLOAT,false,48,offset*4);offset+=size;}
function texture(img){let t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);for(const k of [gl.TEXTURE_MIN_FILTER,gl.TEXTURE_MAG_FILTER])gl.texParameteri(gl.TEXTURE_2D,k,gl.NEAREST);for(const k of [gl.TEXTURE_WRAP_S,gl.TEXTURE_WRAP_T])gl.texParameteri(gl.TEXTURE_2D,k,gl.CLAMP_TO_EDGE);gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,gl.RGBA,gl.UNSIGNED_BYTE,img);return t;}
function image(src){return new Promise((resolve,reject)=>{const i=new Image();i.onload=()=>resolve(i);i.onerror=reject;i.src=src;});}
function camera(){const aspect=canvas.width/canvas.height;
    if(state.view.startsWith('player')){const d=state.view==='player8'?8:16;return {eye:[0,1.6,-d],at:[0,3.3,0],fov:50};}
    const d=state.distance/Math.min(1,aspect/1.25),y=rad(state.yaw),p=rad(state.pitch),t=state.target;
    return {eye:[t[0]-Math.sin(y)*Math.cos(p)*d,t[1]+Math.sin(p)*d,t[2]-Math.cos(y)*Math.cos(p)*d],at:t,fov:40};
}
function setView(view){state.view=view;state.target=state.model==='king'?[.65,3.1,0]:[0,2.5,0];state.distance=state.model==='king'?13.8:9.5;
    const angles={hero:[-32,12],front:[0,0],side:[90,5],back:[180,8],head:[-18,5]};
    [state.yaw,state.pitch]=angles[view]||[0,0];if(view==='head'){state.target=[0,4.1,-.18];state.distance=3.3;}
    document.querySelectorAll('[data-view]').forEach(b=>b.classList.toggle('active',b.dataset.view===view));draw();
}
function scene(){return PyreEffects.scene(models[state.model==='king'?'king':'sword'],manifest,state.time,state.motion,state.model==='planted');}
function emitFaces(fs){const data=[];for(const f of fs)for(const k of [0,1,2,0,2,3])data.push(...f.pts[k],...f.n,...f.uvs[k],1,1,1,f.alpha??1);return data;}
function box(data,o,s,color){
 const [x,y,z]=o,[X,Y,Z]=o.map((v,i)=>v+s[i]);
 const fs=[[[x,y,z],[X,y,z],[X,Y,z],[x,Y,z],[0,0,-1]],[[X,y,Z],[x,y,Z],[x,Y,Z],[X,Y,Z],[0,0,1]],[[x,y,Z],[x,y,z],[x,Y,z],[x,Y,Z],[-1,0,0]],[[X,y,z],[X,y,Z],[X,Y,Z],[X,Y,z],[1,0,0]],[[x,Y,z],[X,Y,z],[X,Y,Z],[x,Y,Z],[0,1,0]],[[x,y,Z],[X,y,Z],[X,y,z],[x,y,z],[0,-1,0]]];
 for(const f of fs)for(const k of [0,1,2,0,2,3])data.push(...f[k],...f[4],0,0,...color,1);
}
function solids(){const d=[];
    for(let x=-12;x<12;x++)for(let z=-12;z<12;z++){const c=(x+z)%2===0?.35:.32;box(d,[x,-.045,z],[.98,.035,.98],[c,c*.99,c*.95]);}
    if(state.player&&state.model==='king'){const x=-2.35,skin=[.63,.45,.32],shirt=[.12,.48,.51],pants=[.22,.25,.39];
        box(d,[x-.225,1.35,-.225],[.45,.45,.45],skin);box(d,[x-.225,.675,-.1125],[.45,.675,.225],shirt);
        box(d,[x-.45,.675,-.1125],[.225,.675,.225],skin);box(d,[x+.225,.675,-.1125],[.225,.675,.225],skin);
        box(d,[x-.225,0,-.1125],[.225,.675,.225],pants);box(d,[x,0,-.1125],[.225,.675,.225],pants);}
    return d;
}
function submit(data,textured){gl.uniform1i(u.textured,textured);gl.bufferData(gl.ARRAY_BUFFER,new Float32Array(data),gl.DYNAMIC_DRAW);gl.drawArrays(gl.TRIANGLES,0,data.length/12);}
function draw(){if(!ready)return;const dpr=Math.min(devicePixelRatio,2);canvas.width=Math.round(canvas.clientWidth*dpr);canvas.height=Math.round(canvas.clientHeight*dpr);
    const c=camera(),vp=M.mul(M.perspective(rad(c.fov),canvas.width/canvas.height,.05,120),M.lookAt(c.eye,c.at));
    gl.viewport(0,0,canvas.width,canvas.height);const night=state.light==='night';gl.clearColor(...(night?[.065,.073,.09]:[.45,.46,.45]),1);gl.clear(gl.COLOR_BUFFER_BIT|gl.DEPTH_BUFFER_BIT);
    gl.enable(gl.DEPTH_TEST);gl.depthFunc(gl.LEQUAL);gl.depthMask(true);gl.disable(gl.CULL_FACE);gl.disable(gl.BLEND);gl.useProgram(prog);gl.bindVertexArray(vao);gl.bindBuffer(gl.ARRAY_BUFFER,buf);
    gl.uniformMatrix4fv(u.vp,false,vp);gl.uniform1f(u.level,night?.32:1);gl.uniform1i(u.glow,state.glow);gl.uniform1i(u.clay,state.clay);gl.uniform1i(u.effect,false);
    gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,tex);gl.uniform1i(u.skin,0);gl.activeTexture(gl.TEXTURE1);gl.bindTexture(gl.TEXTURE_2D,mask);gl.uniform1i(u.glowmask,1);
    submit(solids(),false);const frame=scene();submit(emitFaces(frame.faces),true);
    if(state.fire){const dist=f=>f.pts.reduce((s,p)=>s+p.reduce((s,v,i)=>s+(v-c.eye[i])**2,0),0);const flames=frame.flames.sort((a,b)=>dist(b)-dist(a));gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,fireTex);gl.uniform1i(u.effect,true);gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);gl.depthMask(false);submit(emitFaces(flames),true);gl.depthMask(true);gl.disable(gl.BLEND);}
    $('#camera-info').textContent=state.view.startsWith('player')?`Player camera · eye 1.6 blocks · distance ${state.view==='player8'?8:16} blocks · 50° vertical FOV · looking at y=3.3`:'Drag to orbit · wheel to zoom · 16 model pixels = 1 block';
}
let drag=null;
canvas.onpointerdown=e=>{drag=[e.clientX,e.clientY];canvas.setPointerCapture(e.pointerId);};canvas.onpointerup=()=>drag=null;
canvas.onpointermove=e=>{if(!drag)return;if(state.view.startsWith('player')){state.distance=state.view==='player8'?8:16;state.pitch=-10;state.yaw=0;}state.view='orbit';state.yaw-=(e.clientX-drag[0])*.4;state.pitch=Math.max(-65,Math.min(80,state.pitch+(e.clientY-drag[1])*.4));drag=[e.clientX,e.clientY];draw();};
canvas.onwheel=e=>{e.preventDefault();state.view='orbit';state.distance=Math.max(1.5,Math.min(30,state.distance*Math.exp(e.deltaY*.001)));draw();};
document.querySelectorAll('[data-view]').forEach(b=>b.onclick=()=>setView(b.dataset.view));
for(const n of ['player','glow','fire','clay'])$('#'+n).onchange=e=>{state[n]=e.target.checked;draw();};
$('#light').onchange=e=>{state.light=e.target.value;draw();};$('#model').onchange=e=>{state.model=e.target.value;setView('hero');};
document.querySelectorAll('[data-atlas]').forEach(b=>b.onclick=()=>{$('#atlas').src='candidate/pyre_king'+(b.dataset.atlas==='glow'?'_glowmask':'')+'.png';document.querySelectorAll('[data-atlas]').forEach(x=>x.classList.toggle('active',x===b));});
try{$('#notes').value=localStorage.getItem('pyre-king-review-notes')||'';}catch{}
$('#notes').oninput=()=>{try{localStorage.setItem('pyre-king-review-notes',$('#notes').value);$('#notes-status').textContent='Saved in this browser.';}catch{$('#notes-status').textContent='Browser storage unavailable.';}};
addEventListener('resize',draw);
(async()=>{const json=async f=>{const r=await fetch('candidate/'+f);if(!r.ok)throw Error(f+': '+r.status);return r.json();};
    const [king,sword,im,gm,info,fi]=await Promise.all([json('pyre_king.geo.json'),json('pyre_king_sword.geo.json'),image('candidate/pyre_king.png'),image('candidate/pyre_king_glowmask.png'),json('manifest.json'),image('candidate/bladefire.png')]);
    models={king:bakeModel(king),sword:bakeModel(sword)};manifest=info;tex=texture(im);mask=texture(gm);fireTex=texture(fi);$('#atlas').src=im.src;
    $('#stats').textContent=`${info.cubes} cubes · ${info.bones} bones · 5.00 blocks to crown (${info.measured_held_silhouette_height_blocks.toFixed(2)} with raised sword). Sword: ${info.sword_cubes} cubes, 4.50 blocks overall / 3.50-block blade.`;
    $('#bones').textContent=models.king.bones.map(b=>b.name).join('\n');ready=true;const params=new URLSearchParams(location.search);if(params.has('model')){state.model=params.get('model');$('#model').value=state.model;}setView(params.get('view')||'hero');requestAnimationFrame(tick);
})().catch(e=>{$('#stats').textContent='Could not load model: '+e.message;console.error(e);});
let previousTime=null;
function tick(now){const dt=previousTime===null?0:Math.min(.05,(now-previousTime)/1000);previousTime=now;if(state.playing)state.time+=dt;draw();requestAnimationFrame(tick);}
$('#motion').onchange=e=>{state.motion=e.target.checked;state.time=0;draw();};
$('#pause').onclick=()=>{state.playing=!state.playing;$('#pause').textContent=state.playing?'Pause':'Play';};
$('#horizontal').onclick=()=>{state.motion=true;state.playing=false;state.time=1.8;$('#motion').checked=true;$('#pause').textContent='Play';draw();};
$('#guard').onclick=()=>{state.motion=false;state.playing=true;state.time=.65;$('#motion').checked=false;$('#pause').textContent='Pause';draw();};
window.workshop={state,draw,setView,camera,scene,get ready(){return ready;},get models(){return models;}};
