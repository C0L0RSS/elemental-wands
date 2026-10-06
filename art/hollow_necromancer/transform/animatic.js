// Necromancer transformation animatic: the workshop candidate's transformation clip on a stand-in
// crypt clearing, seen through the scene's per-tick camera track (transform-track.json), as
// NecromancerTransformClient plays it. Forked from the Guardian intro animatic
// (art/fractured_guardian/intro/animatic.js); model baking and bone maths follow GeckoLib 5 as in
// the Necromancer workshop. He lands at the origin facing +Z of the scene's frame; GeckoLib's
// mirrored model space turns into that frame with a half turn about Y.
'use strict';

const $ = s => document.querySelector(s);
const rad = d => d * Math.PI / 180;
const smooth = s => { s = Math.min(1, Math.max(0, s)); return s * s * (3 - 2 * s); };

const M = {
  id: () => new Float32Array([1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]),
  mul(a, b) { const o = new Float32Array(16); for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) { let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k]; o[c * 4 + r] = s; } return o; },
  t(x, y, z) { const m = M.id(); m[12] = x; m[13] = y; m[14] = z; return m; },
  s(x, y, z) { const m = M.id(); m[0] = x; m[5] = y; m[10] = z; return m; },
  rx(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([1,0,0,0, 0,c,s,0, 0,-s,c,0, 0,0,0,1]); },
  ry(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([c,0,-s,0, 0,1,0,0, s,0,c,0, 0,0,0,1]); },
  rz(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([c,s,0,0, -s,c,0,0, 0,0,1,0, 0,0,0,1]); },
  apply(m, v) { return [m[0]*v[0]+m[4]*v[1]+m[8]*v[2]+m[12], m[1]*v[0]+m[5]*v[1]+m[9]*v[2]+m[13], m[2]*v[0]+m[6]*v[1]+m[10]*v[2]+m[14]]; },
  dir(m, v) { return [m[0]*v[0]+m[4]*v[1]+m[8]*v[2], m[1]*v[0]+m[5]*v[1]+m[9]*v[2], m[2]*v[0]+m[6]*v[1]+m[10]*v[2]]; },
  perspective(fov, aspect, near, far) { const f = 1 / Math.tan(fov / 2), m = new Float32Array(16); m[0] = f / aspect; m[5] = f; m[10] = (far + near) / (near - far); m[11] = -1; m[14] = 2 * far * near / (near - far); return m; },
  view(eye, x, y, z) { return new Float32Array([x[0],y[0],z[0],0, x[1],y[1],z[1],0, x[2],y[2],z[2],0, -dot(x,eye),-dot(y,eye),-dot(z,eye),1]); },
};
const sub = (a, b) => a.map((v, i) => v - b[i]);
const add = (a, b) => a.map((v, i) => v + b[i]);
const scale = (a, k) => a.map(v => v * k);
const dot = (a, b) => a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
const cross = (a, b) => [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };
const lerp = (a, b, s) => a.map((v, i) => v + (b[i] - v) * s);

// ---------- GeckoLib model baking and posing ----------
function bakeModel(geo) {
  const g = geo['minecraft:geometry'][0], d = g.description, W = d.texture_width, H = d.texture_height;
  const byName = new Map(g.bones.map(b => [b.name, b])), order = [], seen = new Set();
  const visit = b => { if (seen.has(b.name)) return; if (b.parent && byName.has(b.parent)) visit(byName.get(b.parent)); seen.add(b.name); order.push(b); };
  g.bones.forEach(visit);
  const bones = order.map(b => {
    const p = b.pivot || [0, 0, 0], r = b.rotation || [0, 0, 0];
    return {name: b.name, parent: b.parent || null, pivot: [-p[0] / 16, p[1] / 16, p[2] / 16], rest: [rad(-r[0]), rad(-r[1]), rad(r[2])], cubes: b.cubes || []};
  });
  const faces = [];
  bones.forEach((bone, boneIndex) => bone.cubes.forEach(c => {
    const s = c.size, o = c.origin;
    const ox = -(o[0] + s[0]) / 16, oy = o[1] / 16, oz = o[2] / 16, sx = s[0] / 16, sy = s[1] / 16, sz = s[2] / 16, inf = (c.inflate || 0) / 16;
    const V = {
      blb: [ox - inf, oy - inf, oz - inf], brb: [ox - inf, oy - inf, oz + sz + inf], tlb: [ox - inf, oy + sy + inf, oz - inf], trb: [ox - inf, oy + sy + inf, oz + sz + inf],
      tlf: [ox + sx + inf, oy + sy + inf, oz - inf], trf: [ox + sx + inf, oy + sy + inf, oz + sz + inf], blf: [ox + sx + inf, oy - inf, oz - inf], brf: [ox + sx + inf, oy - inf, oz + sz + inf]};
    const quads = {west: ['trb','tlb','blb','brb'], east: ['tlf','trf','brf','blf'], north: ['tlb','tlf','blf','blb'], south: ['trf','trb','brb','brf'], up: ['trb','trf','tlf','tlb'], down: ['blb','blf','brf','brb']};
    const normals = {west: [-1,0,0], east: [1,0,0], north: [0,0,-1], south: [0,0,1], up: [0,1,0], down: [0,-1,0]};
    let cubeMatrix = null;
    if (c.rotation && c.rotation.some(v => v)) {
      const cp = c.pivot || [0, 0, 0], pv = [-cp[0] / 16, cp[1] / 16, cp[2] / 16];
      cubeMatrix = [M.t(...pv), M.rz(rad(c.rotation[2])), M.ry(rad(-c.rotation[1])), M.rx(rad(-c.rotation[0])), M.t(-pv[0], -pv[1], -pv[2])].reduce(M.mul);
    }
    const [fw, fh, fd] = s.map(Math.floor);
    for (const dir of ['west','east','north','south','up','down']) {
      let u, v, us, vs;
      if (Array.isArray(c.uv)) {
        // A mirrored cube (vanilla's left limbs) swaps its side faces and flips every face.
        const [bu, bv] = c.uv, side = c.mirror ? {west: 'east', east: 'west'}[dir] || dir : dir;
        [u, v, us, vs] = {west: [bu+fd+fw, bv+fd, fd, fh], east: [bu, bv+fd, fd, fh], north: [bu+fd, bv+fd, fw, fh], south: [bu+fd+fw+fd, bv+fd, fw, fh], up: [bu+fd, bv, fw, fd], down: [bu+fd+fw, bv+fd, fw, -fd]}[side];
        if (c.mirror) { u += us; us = -us; }
      } else {
        const f = c.uv && c.uv[dir]; if (!f) continue;
        [u, v] = f.uv; [us, vs] = f.uv_size;
      }
      const u0 = (u + us) / W, u1 = u / W, v0 = v / H, v1 = (v + vs) / H;
      let pts = quads[dir].map(k => V[k]), n = normals[dir];
      if (cubeMatrix) { pts = pts.map(p => M.apply(cubeMatrix, p)); n = M.dir(cubeMatrix, n); }
      const area = Math.hypot(...cross(sub(pts[1], pts[0]), sub(pts[2], pts[0])));
      if (area > 1e-9 && us !== 0 && vs !== 0) faces.push({bone: boneIndex, pts, n, uvs: [[u0,v0],[u1,v0],[u1,v1],[u0,v1]]});
    }
  }));
  return {bones, faces};
}

const keyCache = new WeakMap();
function sampleChannel(channel, t, fallback) {
  if (channel === undefined) return fallback;
  let keys = keyCache.get(channel);
  if (!keys) { keys = Object.entries(channel).map(([k, v]) => ({t: +k, v})).sort((a, b) => a.t - b.t); keyCache.set(channel, keys); }
  if (t <= keys[0].t) return keys[0].v;
  for (let i = 1; i < keys.length; i++) if (t <= keys[i].t) {
    const a = keys[i - 1], b = keys[i], f = (t - a.t) / (b.t - a.t || 1);
    return a.v.map((v, j) => v + (b.v[j] - v) * f);
  }
  return keys[keys.length - 1].v;
}

const ZERO = M.s(0, 0, 0);
function poseBones(model, clip, seconds, base, hidden = new Set()) {
  const out = [];
  const byName = {};
  model.bones.forEach((bone, i) => {
    const ch = clip.bones[bone.name] || {};
    const r = sampleChannel(ch.rotation, seconds, [0, 0, 0]), p = sampleChannel(ch.position, seconds, [0, 0, 0]), s = sampleChannel(ch.scale, seconds, [1, 1, 1]);
    const rot = [bone.rest[0] + rad(-r[0]), bone.rest[1] + rad(-r[1]), bone.rest[2] + rad(r[2])], pv = bone.pivot;
    let m = [M.t(-p[0] / 16, p[1] / 16, p[2] / 16), M.t(...pv), M.rz(rot[2]), M.ry(rot[1]), M.rx(rot[0]), M.s(...s), M.t(-pv[0], -pv[1], -pv[2])].reduce(M.mul);
    m = M.mul(bone.parent != null && byName[bone.parent] !== undefined ? out[byName[bone.parent]] : base, m);
    if (hidden.has(bone.name)) m = M.mul(m, ZERO);
    out[i] = m; byName[bone.name] = i;
  });
  return out;
}

// Reveal only the anatomy above the hood cavity; the creature always stays full size.
function clipAtPortal(vertices, portal) {
  const out=[],axis=portal.plane_y!==undefined?1:2,plane=portal.plane_y??portal.plane_z;
  for (let i=0;i<vertices.length;i++) {
    const a=vertices[i],b=vertices[(i+1)%vertices.length],da=a.p[axis]-plane,db=b.p[axis]-plane;
    if(da>=0)out.push(a);
    if((da>=0)!==(db>=0)){
      const f=da/(da-db);
      out.push({p:lerp(a.p,b.p,f),uv:lerp(a.uv,b.uv,f)});
    }
  }
  return out;
}
function meshData(model, matrices, portal=null) {
  const data=[];
  for(const f of model.faces){
    if(f.giant && state.study>=0 && !track.poseStudies[state.study].visibleGiant.includes(model.bones[f.bone].name))continue;
    if(f.giant && state.study<0){const reveal=track.giantVisibility?.find(r=>state.tick<r.until);if(reveal&&!reveal.names.includes(model.bones[f.bone].name))continue;}
    if(f.giant && portal && state.tick<portal.start)continue;
    const m=matrices[f.bone],n=norm(M.dir(m,f.n));
    let vertices=f.pts.map((p,i)=>({p:M.apply(m,p),uv:f.uvs[i]}));
    if(f.giant && portal && state.tick<portal.end)vertices=clipAtPortal(vertices,portal);
    if(!n.every(Number.isFinite)||!vertices.every(v=>v.p.every(Number.isFinite)))continue;
    for(let k=1;k<vertices.length-1;k++)for(const i of [0,k,k+1])data.push(...vertices[i].p,...n,...vertices[i].uv);
  }
  return new Float32Array(data);
}
function portalMesh(t){
  const p=track.hoodPortal;
  if(!p||t<p.start||t>=p.end)return new Float32Array();
  const tick=Math.floor(t),a=p.frames[tick],b=p.frames[Math.min(tick+1,p.frames.length-1)],f=t-tick;
  if(!a?.length)return new Float32Array();
  return new Float32Array(a.map((v,i)=>b.length===a.length?v+(b[i]-v)*f:v));
}

// Translucent smoke belongs to the preview, not the opaque GeckoLib cube material.
// Every puff is sampled from its birth tick, so backward scrubbing is identical to playback.
// Rig [0,6,26] becomes scene [0,.375,-1.625] after ENTITY's half turn.
const SMOKE_SHAPE = [
  '................', '......11........', '....11221.......', '...12233211.....',
  '..1233433221....', '..23344443321...', '.1234454443321..', '.23445554433221.',
  '..3445554433221.', '.1234444433221..', '..23344332211...', '...123332221....',
  '...1122211......', '.....111........', '................', '................',
];
const SMOKE_PALETTE = [[0,0], [.17,.28], [.20,.43], [.235,.58], [.27,.72], [.31,.82]];
const effectRandom=seed=>{const n=Math.sin(seed*127.1+311.7)*43758.5453;return n-Math.floor(n);};
function smokeSpriteAt(x,y,variant=0){
  if(x<0||x>=16||y<0||y>=16)return [0,0,0,0];
  let sx=variant===1?15-x:x,sy=variant===2?15-y:y;
  let key=+(SMOKE_SHAPE[sy][sx]||0);
  // Small missing clusters and unequal lobes prevent the repeated circle/solid-block look.
  if((variant===1&&sx<5&&sy>9)||(variant===2&&sx>10&&sy<6))key=0;
  const [value,alpha]=SMOKE_PALETTE[key]||SMOKE_PALETTE[0];
  return [value,value,value,alpha];
}
function smokeParticlesAt(t){
  // Each birth in the track rises from the bone it was placed at (see build_scene.bursts).
  const particles=[];
  for(const b of track.smoke||[])for(let i=0;i<b.count;i++){
    const age=t-b.tick;if(age<=0||age>=b.life)continue;
    const seed=b.seed*13+i*7,u=age/b.life,a=effectRandom(seed+1),c=effectRandom(seed+2),d=effectRandom(seed+3),angle=a*Math.PI*2;
    const low=b.kind==='cloak'||b.kind==='ring'||b.kind==='dust';
    const drift=b.spread*(.35+.65*c)*smooth(age/(b.kind==='ring'?6:10));
    const center=[b.at[0]+Math.cos(angle)*drift,b.at[1]+u*b.rise*(.6+.8*d)+Math.sin(u*Math.PI)*.05,b.at[2]+Math.sin(angle)*drift];
    const opacity=b.opacity*smooth(age/3)*(1-smooth((u-.38)/.62));
    if(opacity<.0005)continue;
    particles.push({id:b.tick+'-'+i,kind:b.kind,birth:b.tick,center,radius:b.radius*(.85+c*.3)*(1+u*.45),
      aspect:low?.72+d*.14:.9+d*.25,opacity,variant:Math.floor(a*3)});
  }
  return particles;
}
function emergenceEffectsAt(t){
  const out=[];
  for(const e of track.effects||[])for(let i=0;i<e.count;i++){
    const age=t-e.tick,seed=e.seed+i*19,a=effectRandom(seed),b=effectRandom(seed+1),c=effectRandom(seed+2);
    if(e.kind==='ember'){
      // Souls escaping as sparse sharp flecks.
      const life=25+b*13;if(age<=0||age>=life)continue;
      const source=[e.at[0]+(a-.5)*.7,e.at[1],e.at[2]+(c-.5)*.5],velocity=[(a-.5)*.038,.025+b*.026,(c-.5)*.02];
      const position=dt=>[source[0]+velocity[0]*dt+.10*Math.sin(dt*.09)*(a-.5),source[1]+velocity[1]*dt-.00024*dt*dt,source[2]+velocity[2]*dt];
      const fade=smooth(age/2)*(1-smooth((age/life-.48)/.52));
      out.push({kind:'ember',birth:e.tick,center:position(age),tail:position(Math.max(0,age-1.4)),
        radius:.013+c*.012,opacity:.68*fade,color:[.16,.68+b*.16,.79+c*.17]});
    }else{
      // One small ballistic shower of stone chips per claw landing.
      const life=15+b*7;if(age<=0||age>=life)continue;
      const angle=a*Math.PI*2,velocity=.012+b*.022,y=.035+(.035+c*.022)*age-.0032*age*age;
      if(y<.018)continue;
      const value=.14+b*.075;
      out.push({kind:'chip',birth:e.tick,center:[e.at[0]+Math.cos(angle)*velocity*age,y,e.at[2]+Math.sin(angle)*velocity*age],
        radius:.025+c*.022,opacity:.84*(1-smooth((age/life-.55)/.45)),color:[value,value*.96,value*.98]});
    }
  }
  return out;
}
function emergenceEffectMeshData(effects,eye,right,up,forward){
  const data=[],push=(point,color,opacity)=>data.push(...point,...color,opacity);
  const triangle=(points,color,opacity)=>points.forEach(p=>push(p,color,opacity));
  for(const e of [...effects].sort((a,b)=>dot(sub(b.center,eye),forward)-dot(sub(a.center,eye),forward))){
    if(e.tail){
      const side=scale(right,e.radius*.42),quad=[add(e.center,side),sub(e.center,side),sub(e.tail,side),add(e.tail,side)];
      for(const k of [0,1,2,0,2,3])push(quad[k],e.color,e.opacity*.35);
    }
    const shape=e.kind==='ember'?[[0,-1.6],[.65,0],[0,1.6],[-.65,0]]:[[-1,-.45],[-.35,-1],[.75,-.65],[1,.3],[0,1],[-.8,.55]];
    const points=shape.map(([x,y])=>add(e.center,add(scale(right,x*e.radius),scale(up,y*e.radius))));
    for(let k=1;k<points.length-1;k++)triangle([points[0],points[k],points[k+1]],e.color,e.opacity);
  }
  return new Float32Array(data);
}
function smokeMeshData(particles,eye,right,up,forward){
  const data=[];
  // View depth is the correct ordering even when inspecting the emergence from orbit.
  const sorted=[...particles].sort((a,b)=>dot(sub(b.center,eye),forward)-dot(sub(a.center,eye),forward));
  for(const p of sorted){
    const corners=[[-1,-1],[1,-1],[1,1],[-1,1]];
    for(const k of [0,1,2,0,2,3]){
      const [x,y]=corners[k],point=add(p.center,add(scale(right,x*p.radius),scale(up,y*p.radius*p.aspect)));
      // One three-variant atlas with nearest-neighbour sampling; no blur or rotating cubes.
      const u=(p.variant+(x+1)/2*.998+.001)/3,v=(1-y)/2*.998+.001;
      data.push(...point,u,v,p.opacity);
    }
  }
  return new Float32Array(data);
}
// Offline inspectors can use exactly the same particles, mask and billboard geometry.
globalThis.NecromancerSmoke=Object.freeze({particlesAt:smokeParticlesAt,spriteAt:smokeSpriteAt,
  meshData:smokeMeshData,births:()=>track.smoke,spriteSize:16,variants:3,
  effectsAt:emergenceEffectsAt,effectMeshData:emergenceEffectMeshData});

// ---------- WebGL ----------
const canvas = $('#view'), hud = $('#hud'), ctx = hud.getContext('2d');
const gl = canvas.getContext('webgl2', {antialias: true, preserveDrawingBuffer: true});
const FOG = [.035, .04, .052];
const MODEL_VS = `#version 300 es
in vec3 pos; in vec3 normal; in vec2 uv; uniform mat4 viewProj; out vec3 vNormal; out vec2 vUv; out vec3 vPos;
void main(){ gl_Position = viewProj * vec4(pos, 1.); vNormal = normal; vUv = uv; vPos = pos; }`;
const MODEL_FS = `#version 300 es
precision highp float; in vec3 vNormal; in vec2 vUv; in vec3 vPos; out vec4 color;
uniform sampler2D tex; uniform float emissiveGain; uniform int glow; uniform vec3 eye; uniform vec3 fog; uniform float light; uniform vec4 soul; uniform int silhouette;
void main(){
  vec4 c = texture(tex, vUv); if (c.a < .1) discard;
  if (silhouette == 1) { color = vec4(.025, .028, .035, 1.); return; } // review aid: shape only, against a lit backdrop
  float haze = smoothstep(40., 140., distance(vPos, eye));
  if (glow == 1) { color = vec4(c.rgb * emissiveGain * (1. - haze), 1.); return; }
  vec3 d = vPos - soul.xyz; float near = soul.w / (1. + dot(d, d) * .3); // the souls' light
  c.rgb *= mix(.5, 1., light) + vec3(.35, .85, .95) * near * 1.3; // the boss keeps a minimum body light in the dark crypt
  vec3 n = normalize(gl_FrontFacing ? vNormal : -vNormal);
  vec3 l0 = normalize(vec3(.2, 1., -.7)), l1 = normalize(vec3(-.2, 1., .7));
  float shade = min(1., .4 + .6 * (max(0., dot(n, l0)) + max(0., dot(n, l1))));
  color = vec4(mix(c.rgb * shade * .78, fog, haze), 1.);
}`;
const SOLID_VS = `#version 300 es
in vec3 pos; in vec3 normal; in vec3 col; uniform mat4 viewProj; out vec3 vNormal; out vec3 vCol; out vec3 vPos;
void main(){ gl_Position = viewProj * vec4(pos, 1.); vNormal = normal; vCol = col; vPos = pos; }`;
const SOLID_FS = `#version 300 es
precision highp float; in vec3 vNormal; in vec3 vCol; in vec3 vPos; out vec4 color; uniform vec3 eye; uniform vec3 fog; uniform float light; uniform vec4 soul; uniform int silhouette;
void main(){
  if (silhouette == 1) { color = vec4(vCol.r < 0. ? vec3(.30, .34, .36) : vec3(.025, .028, .035), 1.); return; }
  float haze = smoothstep(24., 110., distance(vPos, eye));
  vec3 d = vPos - soul.xyz;
  vec3 lit = vec3(mix(.18, 1., light)) + vec3(.35, .85, .95) * soul.w / (1. + dot(d, d) * .3) * 1.6;
  if (vCol.r < 0.) { // floor: dirt and soul soil, the blackstone ring of the summoning circle
    vec2 cell = floor(vPos.xz); float n = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);
    float r = length(vPos.xz);
    vec3 c = mix(vec3(.2, .16, .13), vec3(.16, .13, .12), step(.55, n));
    if (r > 6.4 && r < 7.6) c = vec3(.13, .12, .14) * (n > .66 ? 1.3 : 1.);
    vec2 f = fract(vPos.xz); c *= 1. - .25 * step(.94, max(f.x, f.y));
    color = vec4(mix(c * lit, fog, haze), 1.); return;
  }
  if (vCol.g < 0.) { color = vec4(0.42, .95, 1., 1.); return; } // camera gizmo
  if (vCol.b > 1.5) { color = vec4(mix(vCol - vec3(0., 0., 1.), fog, haze * .5), 1.); return; } // brazier fire, full bright
  vec3 n = normalize(vNormal); vec3 l0 = normalize(vec3(.2, 1., -.7)), l1 = normalize(vec3(-.2, 1., .7));
  float shade = min(1., .4 + .6 * (max(0., dot(n, l0)) + max(0., dot(n, l1))));
  color = vec4(mix(vCol * shade * lit, fog, haze), 1.);
}`;
const SMOKE_VS=`#version 300 es
in vec3 pos; in vec2 uv; in float alpha; uniform mat4 viewProj; out vec2 vUv; out float vAlpha;
void main(){gl_Position=viewProj*vec4(pos,1.);vUv=uv;vAlpha=alpha;}`;
const SMOKE_FS=`#version 300 es
precision highp float; in vec2 vUv; in float vAlpha; uniform sampler2D tex; uniform float light; out vec4 color;
void main(){vec4 c=texture(tex,vUv);float a=c.a*vAlpha;if(a<.001)discard;
  color=vec4(c.rgb*mix(.82,1.,light),a);}`;
const EFFECT_VS=`#version 300 es
in vec3 pos; in vec3 col; in float alpha; uniform mat4 viewProj; out vec3 vCol; out float vAlpha;
void main(){gl_Position=viewProj*vec4(pos,1.);vCol=col;vAlpha=alpha;}`;
const EFFECT_FS=`#version 300 es
precision highp float; in vec3 vCol; in float vAlpha; out vec4 color;
void main(){if(vAlpha<.001)discard;color=vec4(vCol,vAlpha);}`;
function program(vs, fs) {
  const p = gl.createProgram();
  for (const [type, src] of [[gl.VERTEX_SHADER, vs], [gl.FRAGMENT_SHADER, fs]]) {
    const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw Error(gl.getShaderInfoLog(s));
    gl.attachShader(p, s);
  }
  gl.linkProgram(p);
  const u = {}; const n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
  for (let i = 0; i < n; i++) { const name = gl.getActiveUniform(p, i).name; u[name] = gl.getUniformLocation(p, name); }
  return {p, u, a: name => gl.getAttribLocation(p, name)};
}
function vao(prog, layout, stride) {
  const va = gl.createVertexArray(), buf = gl.createBuffer();
  gl.bindVertexArray(va); gl.bindBuffer(gl.ARRAY_BUFFER, buf);
  let offset = 0;
  for (const [name, size] of layout) { const loc = prog.a(name); if (loc >= 0) { gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, size, gl.FLOAT, false, stride * 4, offset * 4); } offset += size; }
  gl.bindVertexArray(null);
  return {va, buf, count: 0};
}
function texture(image) {
  const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
  return t;
}
function smokeTexture(){
  const pixels=new Uint8Array(48*16*4);
  for(let variant=0;variant<3;variant++)for(let y=0;y<16;y++)for(let x=0;x<16;x++){
    const rgba=smokeSpriteAt(x,y,variant),offset=(y*48+variant*16+x)*4;
    rgba.forEach((value,k)=>pixels[offset+k]=Math.round(value*255));
  }
  const t=gl.createTexture();gl.bindTexture(gl.TEXTURE_2D,t);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MIN_FILTER,gl.NEAREST);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_MAG_FILTER,gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_S,gl.CLAMP_TO_EDGE);gl.texParameteri(gl.TEXTURE_2D,gl.TEXTURE_WRAP_T,gl.CLAMP_TO_EDGE);
  gl.texImage2D(gl.TEXTURE_2D,0,gl.RGBA,48,16,0,gl.RGBA,gl.UNSIGNED_BYTE,pixels);
  return t;
}
function box(data, min, max, col) {
  const [x0, y0, z0] = min, [x1, y1, z1] = max;
  const quads = [[[x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0],[0,0,-1]], [[x1,y0,z1],[x0,y0,z1],[x0,y1,z1],[x1,y1,z1],[0,0,1]],
    [[x0,y0,z1],[x0,y0,z0],[x0,y1,z0],[x0,y1,z1],[-1,0,0]], [[x1,y0,z0],[x1,y0,z1],[x1,y1,z1],[x1,y1,z0],[1,0,0]],
    [[x0,y1,z0],[x1,y1,z0],[x1,y1,z1],[x0,y1,z1],[0,1,0]], [[x0,y0,z1],[x1,y0,z1],[x1,y0,z0],[x0,y0,z0],[0,-1,0]]];
  for (const q of quads) for (const k of [0, 1, 2, 0, 2, 3]) data.push(...q[k], ...q[4], ...col);
}

// ---------- The scene ----------
const state = {study: -1, studySmoke: true, silhouette: false, tick: 0, playing: true, speed: 1, mode: 'scene', bars: true, captions: true,
  orbit: {yaw: rad(35), pitch: rad(16), dist: 12, target: [0, 1.6, 0]}};
let actor, ghost, track, modelProg, solidProg, smokeProg, effectProg, modelMesh, solidMesh, smokeMesh, smokeTex, effectMesh;
const ENTITY = M.ry(Math.PI); // GeckoLib's mirrored model space into the scene's frame, facing +Z
const POV_HIDDEN = new Set(['hood']);
const BRIGHT = new URLSearchParams(location.search).has('bright'); // inspection: ignore the crypt's darkness
const SHOTS = ['the fall', 'his eyes', 'overhead strikes', 'side · soul impacts', 'overhead · convulsions', 'the claws', 'overhead · growth',
  'side · ripping', 'it sits up', 'the crawl'];

// The clearing around the landing spot (the crypt's circle centre), with the party to the south.
const BRAZIERS = [...Array(10)].map((_, i) => { const a = i / 10 * Math.PI * 2 + .12; return [Math.cos(a) * 41, Math.sin(a) * 41]; });
const MARKERS = [[17, 2.0], [22, 3.4], [30, 5.3], [14, 5.8]].map(([r, a]) => [Math.round(Math.cos(a) * r), Math.round(Math.sin(a) * r), a]);
const STUMPS = [[27, 1.2], [16, 4.0], [33, 6.1]].map(([r, a]) => [Math.round(Math.cos(a) * r), Math.round(Math.sin(a) * r)]);
const TREES = [...Array(26)].map((_, i) => { const a = i / 26 * Math.PI * 2 + Math.sin(i * 7.1) * .1, r = 50 + 16 * Math.abs(Math.sin(i * 3.7)); return [Math.cos(a) * r, Math.sin(a) * r, 24 + 22 * Math.abs(Math.sin(i * 1.9))]; });
const PLAYERS = [[-1.4, 11.5], [1.6, 12.8]];

async function load() {
  const bust = '?v=' + Date.now();
  const json = async f => { const r = await fetch(f + bust); if (!r.ok) throw Error(`${f}: ${r.status}`); return r.json(); };
  const image = f => new Promise((ok, no) => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => no(Error(f + ' did not load')); i.src = f + bust; });
  const [geo, anim, tex, glow, trackData, soulGeo, soulAnim, soulTex, soulGlow] = await Promise.all([
    json('hollow_necromancer.geo.json'), json('hollow_necromancer.animation.json'), image('hollow_necromancer.png'),
    image('hollow_necromancer_glowmask.png'), json('transform-track.json'), json('harvest_soul.geo.json'),
    json('harvest_soul.animation.json'), image('harvest_soul.png'), image('harvest_soul_glowmask.png')]);
  modelProg = program(MODEL_VS, MODEL_FS); solidProg = program(SOLID_VS, SOLID_FS);
  modelMesh = vao(modelProg, [['pos', 3], ['normal', 3], ['uv', 2]], 8);
  solidMesh = vao(solidProg, [['pos', 3], ['normal', 3], ['col', 3]], 9);
  smokeProg=program(SMOKE_VS,SMOKE_FS);
  smokeMesh=vao(smokeProg,[['pos',3],['uv',2],['alpha',1]],6);smokeTex=smokeTexture();
  effectProg=program(EFFECT_VS,EFFECT_FS);effectMesh=vao(effectProg,[['pos',3],['col',3],['alpha',1]],7);
  actor = {model: bakeModel(geo), clip: anim.animations['animation.hollow_necromancer.transform'], tex: texture(tex), glow: texture(glow)};
  // Use the shipped Soul Harvest ghost, with its articulated mouth, wisps and tail.
  // Keep the authored soul roots as flight sockets, but discard their placeholder cubes.
  actor.boneIndex = new Map(actor.model.bones.map((b, i) => [b.name, i]));
  const giantBones=new Set(['colossus']);
  for(const bone of actor.model.bones)if(giantBones.has(bone.parent))giantBones.add(bone.name);
  for(const face of actor.model.faces)face.giant=giantBones.has(actor.model.bones[face.bone].name);
  actor.soulRoots = actor.model.bones.flatMap((b, i) => /^soul_\d+$/.test(b.name) ? [i] : []);
  const roots = new Set(actor.soulRoots);
  actor.model.faces = actor.model.faces.filter(f => !roots.has(f.bone));
  ghost = {model: bakeModel(soulGeo), clip: soulAnim.animations['animation.harvest_soul.drift'],
    tex: texture(soulTex), glow: texture(soulGlow)};
  ghost.attack = {...ghost.clip, bones: {...ghost.clip.bones,
    head: {...ghost.clip.bones.head, rotation: {'0': [18, 0, 0]}},
    mouth: {scale: {'0': [1.15, 2.0, 1]}},
    wisp_left: {rotation: {'0': [-25, 0, -15]}}, wisp_right: {rotation: {'0': [-25, 0, 15]}}}};
  track = trackData;
  $('#timeline').max = track.beats.length;
  // Pose buttons, grouped by variant; each keeps its study index for selectStudy.
  let group=null;
  track.poseStudies?.forEach((study,i)=>{
    if(study.group&&study.group!==group){group=study.group;const label=document.createElement('span');label.className='group';label.textContent=group;$('#studies').append(label);}
    const button=document.createElement('button');button.textContent=study.title;button.dataset.study=i;
    button.onclick=()=>selectStudy(i);$('#studies').append(button);
  });
  const previous=document.createElement('button');previous.textContent='Watch the transformation';previous.onclick=()=>{selectStudy(-1);setMode('scene');state.tick=356;state.playing=true;syncPlay();};$('#studies').append(previous);
  const smokeButton=document.createElement('button');smokeButton.id='study-smoke';smokeButton.textContent='Smoke on';smokeButton.classList.add('active');smokeButton.hidden=true;
  smokeButton.onclick=()=>{state.studySmoke=!state.studySmoke;smokeButton.textContent=state.studySmoke?'Smoke on':'Smoke off';smokeButton.classList.toggle('active',state.studySmoke);};$('#studies').append(smokeButton);
  const silhouetteButton=document.createElement('button');silhouetteButton.id='silhouette';silhouetteButton.textContent='Silhouette';
  silhouetteButton.onclick=()=>{state.silhouette=!state.silhouette;silhouetteButton.classList.toggle('active',state.silhouette);};$('#studies').append(silhouetteButton);
  const b = track.beats;
  const shots = [['1 · fall', 0], ['2 · his eyes', b.pov], ['shaking', b.shake], ['staff emergence', b.burst + 10], ['souls launch', b.burst + 24], ['3 · overhead', b.scared],
    ['ghosts attack', b.strike - 5], ['struck', b.strike], ['4 · side view', b.pour],
    ['soul impacts', b.pour + 40], ['eyes dark', b.floor], ['5 · overhead convulsions', b.writhe], ['6 · claws', b.claws], ['7 · growth', b.drop],
    ['robe rips', b.spurt + 24], ['8 · ripping', b.swap], ['9 · sits up', b.sit - 2], ['eyes ignite', b.eyes], ['10 · crouch', b.crouch],
    ['crawl', b.crawl], ['roar', b.roar], ['return', b.ret]];
  for (const [label, tick] of shots) {
    const btn = document.createElement('button'); btn.textContent = label;
    btn.onclick = () => { selectStudy(-1); state.tick = tick; state.playing = false; syncPlay(); };
    $('#shots').append(btn);
  }
}

/** The scene camera at a fractional tick, as the client samples the track. */
function sceneCamera(t) {
  if(state.study>=0)return {...track.poseStudies[state.study].camera,light:BRIGHT?1:track.poseStudies[state.study].camera.light};
  const T = track.ticks, n = T.eye.length - 1;
  const a = Math.max(0, Math.min(n, Math.floor(t))), b = Math.min(n, a + 1);
  const f = track.cuts.includes(b) ? 0 : t - a; // hold the shot until the cut rather than smear across it
  const pick = k => typeof T[k][a] === 'number' ? T[k][a] + (T[k][b] - T[k][a]) * f : lerp(T[k][a], T[k][b], f);
  let yawA = T.yaw[a], yawB = T.yaw[b]; while (yawB - yawA > 180) yawB -= 360; while (yawB - yawA < -180) yawB += 360;
  return {eye: pick('eye'), yaw: yawA + (yawB - yawA) * f, pitch: pick('pitch'), fov: pick('fov'), flash: pick('flash'),
    black: pick('black'), light: BRIGHT ? 1 : pick('dark'), braziers: T.braziers[a], pov: T.pov[a], soul: pick('glow')};
}

/** Minecraft's camera axes for a yaw and pitch (yaw 0 looks along +Z; +pitch looks down). */
function axes(yaw, pitch) {
  const y = rad(yaw), p = rad(pitch);
  const forward = [-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)];
  const right = [-Math.cos(y), 0, -Math.sin(y)];
  return {forward, right, up: cross(right, forward)};
}

function steve(solid, x, z) {
  const skin = [.78, .6, .47], shirt = [.2, .62, .66], pants = [.24, .25, .52];
  box(solid, [x - .25, 1.5, z - .25], [x + .25, 2, z + .25], skin); box(solid, [x - .25, .75, z - .125], [x + .25, 1.5, z + .125], shirt);
  box(solid, [x - .5, .75, z - .125], [x - .25, 1.5, z + .125], skin); box(solid, [x + .25, .75, z - .125], [x + .5, 1.5, z + .125], skin);
  box(solid, [x - .25, 0, z - .125], [x, .75, z + .125], pants); box(solid, [x, 0, z - .125], [x + .25, .75, z + .125], pants);
}

function draw() {
  const dpr = Math.min(devicePixelRatio, 2), w = Math.round(canvas.clientWidth * dpr), h = Math.round(canvas.clientHeight * dpr);
  if (canvas.width !== w || canvas.height !== h) { canvas.width = hud.width = w; canvas.height = hud.height = h; }
  const t = state.tick, cam = sceneCamera(t);
  let eye, vp, smokeAxes;
  if (state.mode === 'scene') {
    const {forward, right, up} = axes(cam.yaw, cam.pitch);
    eye = cam.eye;
    smokeAxes={forward,right,up};
    vp = M.mul(M.perspective(rad(cam.fov), w / h, .03, 400), M.view(eye, right, up, scale(forward, -1)));
  } else {
    const o = state.orbit;
    eye = add(o.target, [Math.sin(o.yaw) * Math.cos(o.pitch) * o.dist, Math.sin(o.pitch) * o.dist, Math.cos(o.yaw) * Math.cos(o.pitch) * o.dist]);
    const z = norm(sub(eye, o.target)), x = norm(cross([0, 1, 0], z)), y = cross(z, x);
    smokeAxes={forward:scale(z,-1),right:x,up:y};
    vp = M.mul(M.perspective(rad(50), w / h, .05, 400), M.view(eye, x, y, z));
  }
  const fog = FOG.map(v => v * (.22 + .78 * cam.light));
  const sil = state.silhouette ? 1 : 0;
  gl.viewport(0, 0, w, h); gl.clearColor(...(sil ? [.62, .8, .84] : fog), 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
  gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL); gl.disable(gl.CULL_FACE);

  const hidden = state.mode === 'scene' && cam.pov ? POV_HIDDEN : new Set();
  const matrices = poseBones(actor.model, state.study>=0?track.poseStudies[state.study].clip:actor.clip, state.study>=0?0:t / 20, ENTITY, hidden);

  // Floor, braziers, graves, the dead forest, the party and, in orbit, the scene camera.
  const solid = [], R = 160, stone = [.2, .19, .22], wood = [.12, .09, .08];
  for (const k of [0, 1, 2, 0, 2, 3]) solid.push(...[[-R,0,-R],[R,0,-R],[R,0,R],[-R,0,R]][k], 0, 1, 0, -1, 0, 0);
  for (const [x, z] of BRAZIERS) {
    box(solid, [x - .5, 0, z - .5], [x + .5, 2, z + .5], stone); box(solid, [x - .5, 2, z - .5], [x + .5, 2.45, z + .5], wood);
    if (cam.braziers) box(solid, [x - .3, 2.45, z - .3], [x + .3, 3.3, z + .3], [.42, .95, 2.0]);
  }
  for (const [x, z] of MARKERS) { box(solid, [x, 0, z], [x + 1, 2, z + 1], [.3, .3, .33]); box(solid, [x, 2, z], [x + 1, 2.5, z + 1], [.24, .24, .27]); }
  for (const [x, z] of STUMPS) box(solid, [x, 0, z], [x + 2, 3, z + 2], wood);
  for (const [x, z, hgt] of TREES) box(solid, [x - .8, 0, z - .8], [x + .8, hgt, z + .8], [.07, .06, .06]);
  for (const [x, z] of PLAYERS) steve(solid, x, z);
  if (state.mode === 'orbit') {
    const {forward, right, up} = axes(cam.yaw, cam.pitch), e = cam.eye, g = [1, -1, 1];
    box(solid, sub(e, [.06, .06, .06]), add(e, [.06, .06, .06]), g);
    for (let i = 1; i <= 12; i++) { const c = add(e, scale(forward, i * .12)); box(solid, sub(c, [.02, .02, .02]), add(c, [.02, .02, .02]), g); }
    for (const [sx, sy] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) {
      const spread = Math.tan(rad(cam.fov / 2)), corner = add(add(forward, scale(up, sy * spread)), scale(right, sx * spread * 16 / 9));
      for (let i = 1; i <= 5; i++) { const c = add(e, scale(corner, i * .15)); box(solid, sub(c, [.015, .015, .015]), add(c, [.015, .015, .015]), g); }
    }
  }
  // Brief tapered soul threads join each emerging head to its actual moving staff crack.
  // They thin away after release, so the upward launch reads without filling the POV.
  for (const emission of track.souls) {
    const age = t - emission.emerge;
    if (age < 0 || age > 14) continue;
    const tipMatrix = matrices[actor.boneIndex.get('staff_' + emission.src)];
    const tip = M.apply(tipMatrix, [5 / 16, track.staff_break / 16, 1 / 16]);
    const socket = matrices[actor.boneIndex.get(emission.bone)];
    const head = [socket[12], socket[13], socket[14]];
    const axis = sub(head, tip), width = .016 * smooth(age / 2) * (1 - smooth((age - 9) / 5));
    if (Math.hypot(...axis) < .008 || width <= 0) continue;
    const side = norm(cross(axis, sub(eye, tip)));
    for (let j = 0; j < 5; j++) {
      const u = j / 5, v = (j + 1) / 5;
      const a = lerp(tip, head, u), b = lerp(tip, head, v);
      const wa = width * (1 - .6 * u), wb = width * (1 - .6 * v);
      const quad = [add(a, scale(side, wa)), sub(a, scale(side, wa)), sub(b, scale(side, wb)), add(b, scale(side, wb))];
      for (const k of [0, 1, 2, 0, 2, 3]) solid.push(...quad[k], 0, 1, 0, .12, .7, 1.85);
    }
  }
  gl.useProgram(solidProg.p); gl.bindVertexArray(solidMesh.va);
  gl.uniformMatrix4fv(solidProg.u.viewProj, false, vp); gl.uniform3fv(solidProg.u.eye, eye); gl.uniform3fv(solidProg.u.fog, fog);
  gl.uniform1f(solidProg.u.light, cam.light); gl.uniform4fv(solidProg.u.soul, cam.soul); gl.uniform1i(solidProg.u.silhouette, sil);
  gl.bindBuffer(gl.ARRAY_BUFFER, solidMesh.buf); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(solid), gl.DYNAMIC_DRAW);
  gl.drawArrays(gl.TRIANGLES, 0, solid.length / 9);

  // The Necromancer, then his glow on top. His POV hides the hood the camera sits in.
  gl.useProgram(modelProg.p); gl.bindVertexArray(modelMesh.va);
  gl.uniformMatrix4fv(modelProg.u.viewProj, false, vp); gl.uniform3fv(modelProg.u.eye, eye); gl.uniform3fv(modelProg.u.fog, fog);
  gl.uniform1f(modelProg.u.emissiveGain, 1); gl.uniform1f(modelProg.u.light, cam.light); gl.uniform4fv(modelProg.u.soul, cam.soul);
  gl.uniform1i(modelProg.u.tex, 0); gl.activeTexture(gl.TEXTURE0); gl.uniform1i(modelProg.u.silhouette, sil);
  const bodyData=meshData(actor.model,matrices,state.study>=0?{plane_y:.00375,start:0,end:9999}:track.hoodPortal),opening=state.study>=0?new Float32Array():portalMesh(t);
  const data=new Float32Array(bodyData.length+opening.length);data.set(bodyData);data.set(opening,bodyData.length);
  gl.bindBuffer(gl.ARRAY_BUFFER, modelMesh.buf); gl.bufferData(gl.ARRAY_BUFFER, data, gl.DYNAMIC_DRAW);
  gl.bindTexture(gl.TEXTURE_2D, actor.tex); gl.uniform1i(modelProg.u.glow, 0);
  gl.drawArrays(gl.TRIANGLES, 0, data.length / 8);
  if (!sil) {
    gl.bindTexture(gl.TEXTURE_2D, actor.glow); gl.uniform1i(modelProg.u.glow, 1);
    gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE); gl.depthMask(false);
    gl.drawArrays(gl.TRIANGLES, 0, data.length / 8);
    gl.depthMask(true); gl.disable(gl.BLEND);
  }
  // Flight sockets are centred on each ghost's head, so its face actually crosses the mask.
  const soulMeshes = [];
  for (let i = 0; i < actor.soulRoots.length; i++) {
    const socket = matrices[actor.soulRoots[i]];
    if (Math.hypot(socket[0], socket[1], socket[2]) < .002) continue;
    const seconds = (t / 20 + i * .137) % ghost.clip.animation_length;
    // A camera clearance envelope prevents a nearby soul from covering the lens.
    const emerging = t - track.souls[i].emerge < 14;
    const clearance = state.mode === 'scene' ? smooth((Math.hypot(socket[12] - eye[0], socket[13] - eye[1], socket[14] - eye[2]) - (emerging ? .18 : .65)) / (emerging ? .42 : .9)) : 1;
    const size = (.8 + .15 * Math.sin(i * 2.4)) * clearance;
    if (size < .005) continue;
    const attacking = (i === 4 && t >= track.beats.strike - 15 && t <= track.beats.strike)
      || (i === 9 && t >= track.beats.strike2 - 15 && t <= track.beats.strike2);
    const base = [socket, M.s(size, size, size), M.rx(rad(-25)), M.t(0, -11 / 16, 0)].reduce(M.mul);
    soulMeshes.push(meshData(ghost.model, poseBones(ghost.model, attacking ? ghost.attack : ghost.clip, seconds, base)));
  }
  const souls = new Float32Array(soulMeshes.reduce((n, m) => n + m.length, 0));
  let offset = 0;
  for (const mesh of soulMeshes) { souls.set(mesh, offset); offset += mesh.length; }
  gl.bufferData(gl.ARRAY_BUFFER, souls, gl.DYNAMIC_DRAW);
  gl.bindTexture(gl.TEXTURE_2D, ghost.tex); gl.uniform1i(modelProg.u.glow, 0);
  gl.uniform1f(modelProg.u.light, 1); gl.uniform1f(modelProg.u.emissiveGain, .3);
  gl.drawArrays(gl.TRIANGLES, 0, souls.length / 8);
  gl.bindTexture(gl.TEXTURE_2D, ghost.glow); gl.uniform1i(modelProg.u.glow, 1);
  gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE); gl.depthMask(false);
  gl.drawArrays(gl.TRIANGLES, 0, souls.length / 8);
  gl.depthMask(true); gl.disable(gl.BLEND);
  // Draw after all opaque anatomy and ghosts. Smoke respects their depth and never writes it.
  const smoke=smokeMeshData(state.study>=0?(state.studySmoke?track.poseStudies[state.study].smoke:[]):smokeParticlesAt(t),eye,smokeAxes.right,smokeAxes.up,smokeAxes.forward);
  if(smoke.length){
    gl.useProgram(smokeProg.p);gl.bindVertexArray(smokeMesh.va);
    gl.uniformMatrix4fv(smokeProg.u.viewProj,false,vp);gl.uniform1i(smokeProg.u.tex,0);gl.uniform1f(smokeProg.u.light,cam.light);
    gl.activeTexture(gl.TEXTURE0);gl.bindTexture(gl.TEXTURE_2D,smokeTex);
    gl.bindBuffer(gl.ARRAY_BUFFER,smokeMesh.buf);gl.bufferData(gl.ARRAY_BUFFER,smoke,gl.DYNAMIC_DRAW);
    gl.enable(gl.DEPTH_TEST);gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);gl.depthMask(false);
    gl.drawArrays(gl.TRIANGLES,0,smoke.length/6);
    gl.depthMask(true);gl.disable(gl.BLEND);
  }
  const effects=state.study>=0?[]:emergenceEffectsAt(t);
  const effectData=emergenceEffectMeshData(effects,eye,smokeAxes.right,smokeAxes.up,smokeAxes.forward);
  if(effectData.length){
    gl.useProgram(effectProg.p);gl.bindVertexArray(effectMesh.va);
    gl.uniformMatrix4fv(effectProg.u.viewProj,false,vp);
    gl.bindBuffer(gl.ARRAY_BUFFER,effectMesh.buf);gl.bufferData(gl.ARRAY_BUFFER,effectData,gl.DYNAMIC_DRAW);
    gl.enable(gl.DEPTH_TEST);gl.enable(gl.BLEND);gl.blendFunc(gl.SRC_ALPHA,gl.ONE_MINUS_SRC_ALPHA);gl.depthMask(false);
    gl.drawArrays(gl.TRIANGLES,0,effectData.length/7);
    gl.depthMask(true);gl.disable(gl.BLEND);
  }
  drawHud(t, cam, w, h);
}

/** Letterbox, the fade from black, the soul-white flashes and the sound captions. */
function drawHud(t, cam, w, h) {
  if(state.study>=0){
    ctx.clearRect(0,0,w,h);ctx.font=`600 ${Math.round(h*.028)}px system-ui`;ctx.textAlign='left';ctx.textBaseline='top';
    ctx.fillStyle='rgba(0,0,0,.65)';ctx.fillRect(0,0,w,h*.08);ctx.fillStyle='#bce5e8';ctx.fillText(track.poseStudies[state.study].title,h*.025,h*.022);return;
  }
  const b = track.beats;
  ctx.clearRect(0, 0, w, h);
  const back = smooth((t - b.ret) / (b.length - b.ret));
  if (state.mode === 'scene') {
    if (cam.black > 0) { ctx.fillStyle = `rgba(0,0,0,${cam.black})`; ctx.fillRect(0, 0, w, h); }
    if (cam.flash > 0) { ctx.fillStyle = `rgba(200,250,255,${Math.min(1, cam.flash)})`; ctx.fillRect(0, 0, w, h); }
    if (state.bars) { const bar = Math.round(h * .11 * (1 - back)); ctx.fillStyle = '#000'; ctx.fillRect(0, 0, w, bar); ctx.fillRect(0, h - bar, w, bar); }
    if (t > 10 && back <= 0) {
      ctx.font = `${Math.round(h * .022)}px system-ui, sans-serif`; ctx.textAlign = 'right'; ctx.textBaseline = 'alphabetic';
      ctx.fillStyle = 'rgba(255,255,255,.45)'; ctx.fillText('Hold Shift to skip', w - h * .03, h - h * .11 - h * .02);
    }
    if (back > 0) {
      ctx.font = `${Math.round(h * .024)}px system-ui, sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      ctx.fillStyle = `rgba(106,242,255,${.8 * back})`; ctx.fillText('(in game the camera glides back to the player here)', w / 2, h * .5);
    }
  }
  if (state.captions) {
    const live = track.events.filter(e => t >= e.tick && t < e.tick + 26);
    ctx.font = `italic ${Math.round(h * .026)}px system-ui, sans-serif`; ctx.textAlign = 'left'; ctx.textBaseline = 'alphabetic';
    live.forEach((e, i) => {
      const a = Math.min(1, (e.tick + 26 - t) / 8);
      ctx.fillStyle = `rgba(230,240,240,${.85 * a})`;
      ctx.fillText(`[ ${e.cue} ]`, h * .03, h - h * .11 - h * .02 - (live.length - 1 - i) * h * .035);
    });
  }
  const shot = SHOTS[[0, ...track.cuts].filter(c => c <= t).length - 1];
  ctx.font = `${Math.round(h * .02)}px system-ui, sans-serif`; ctx.textAlign = 'left'; ctx.textBaseline = 'top'; ctx.fillStyle = 'rgba(106,242,255,.85)';
  ctx.fillText(`${state.mode === 'scene' ? 'scene camera' : 'orbit'} · shot: ${shot} · fov ${cam.fov.toFixed(0)} · light ${(cam.light * 100).toFixed(0)}%`, h * .02, h * .015);
}
// ---------- Controls ----------
function selectStudy(index){
  state.study=index;state.playing=false;state.tick=index>=0?356:state.tick;syncPlay();
  for(const id of ['play','timeline','speed'])$('#'+id).disabled=index>=0;
  $('#shots').hidden=index>=0;$('#shots').style.display=index>=0?'none':'';
  $('#study-note').textContent=index>=0?track.poseStudies[index].description:'The transformation: press Space to play, or pick a shot below.';
  $('#studies').querySelectorAll('[data-study]').forEach(b=>b.classList.toggle('active',+b.dataset.study===index));
  $('#study-smoke').hidden=index<0;
  if(index>=0){setMode('scene');Object.assign(state.orbit,{yaw:rad(35),pitch:rad(20),dist:5,target:track.poseStudies[index].target});}
}
function syncPlay() { $('#play').textContent = state.playing ? '❚❚' : '▶'; $('#play').setAttribute('aria-label', state.playing ? 'Pause' : 'Play'); }
function setMode(mode) { state.mode = mode; $('#cam-scene').classList.toggle('active', mode === 'scene'); $('#cam-orbit').classList.toggle('active', mode === 'orbit'); }
$('#play').onclick = () => { if (state.tick >= track.beats.length) state.tick = 0; state.playing = !state.playing; syncPlay(); };
$('#timeline').oninput = e => { state.tick = +e.target.value; state.playing = false; syncPlay(); };
$('#speed').onchange = e => state.speed = +e.target.value;
$('#cam-scene').onclick = () => setMode('scene');
$('#cam-orbit').onclick = () => setMode('orbit');
$('#bars').onclick = () => { state.bars = !state.bars; $('#bars').classList.toggle('active', state.bars); };
$('#captions').onclick = () => { state.captions = !state.captions; $('#captions').classList.toggle('active', state.captions); };
addEventListener('keydown', e => {
  if (e.target.tagName === 'SELECT') return;
  if (e.code === 'Space') { e.preventDefault(); $('#play').click(); }
  if (state.study<0 && (e.code === 'ArrowRight' || e.code === 'ArrowLeft')) { state.playing = false; syncPlay(); state.tick = Math.max(0, Math.min(track.beats.length, Math.round(state.tick) + (e.code === 'ArrowRight' ? 1 : -1))); }
  if (e.code === 'KeyC') setMode(state.mode === 'scene' ? 'orbit' : 'scene');
});
let drag = null;
canvas.parentElement.addEventListener('pointerdown', e => { if (state.mode === 'orbit') drag = [e.clientX, e.clientY]; });
addEventListener('pointerup', () => drag = null);
addEventListener('pointermove', e => {
  if (!drag) return;
  state.orbit.yaw -= (e.clientX - drag[0]) * .008; state.orbit.pitch = Math.max(-.2, Math.min(1.5, state.orbit.pitch + (e.clientY - drag[1]) * .008));
  drag = [e.clientX, e.clientY];
});
canvas.parentElement.addEventListener('wheel', e => { if (state.mode !== 'orbit') return; e.preventDefault(); state.orbit.dist = Math.max(1.5, Math.min(60, state.orbit.dist * Math.exp(e.deltaY * .001))); }, {passive: false});

let last = 0;
function frame(now) {
  if (track) {
    if (state.playing && last) { state.tick += (now - last) / 50 * state.speed; if (state.tick >= track.beats.length) { state.tick = track.beats.length; state.playing = false; syncPlay(); } }
    $('#timeline').value = state.tick;
    $('#clock').textContent = state.study>=0?'Pose study · orbit to inspect':`tick ${state.tick.toFixed(1)} · ${(state.tick / 20).toFixed(2)} s`;
    draw();
  }
  last = now; requestAnimationFrame(frame);
}
const params = new URLSearchParams(location.search);
/** ?sheet=t1,t2,... renders those ticks into a labelled grid (cols=N) for quick review. */
function contactSheet(ticks, cols) {
  const cw = 480, ch = 270, rows = Math.ceil(ticks.length / cols);
  canvas.parentElement.style.width = cw + 'px'; canvas.parentElement.style.aspectRatio = '16 / 9';
  const sheet = document.createElement('canvas'); sheet.width = cw * cols; sheet.height = ch * rows;
  sheet.style.cssText = 'position:fixed;left:0;top:0;width:100vw;z-index:5;background:#000';
  const sc = sheet.getContext('2d');
  ticks.forEach((tick, i) => {
    state.tick = tick; draw();
    const x = (i % cols) * cw, y = Math.floor(i / cols) * ch;
    sc.drawImage(canvas, x, y, cw, ch); sc.drawImage(hud, x, y, cw, ch);
    sc.fillStyle = '#ff6'; sc.font = '600 18px system-ui'; sc.fillText(`t${tick}`, x + 8, y + ch - 10);
  });
  document.querySelector('main').prepend(sheet);
  canvas.parentElement.style.display = 'none';
}
/** ?poses=0,2,... (or all) renders those pose studies into a labelled grid (cols=N). */
function poseSheet(list, cols) {
  const cw = 480, ch = 270, rows = Math.ceil(list.length / cols);
  canvas.parentElement.style.width = cw + 'px'; canvas.parentElement.style.aspectRatio = '16 / 9';
  const sheet = document.createElement('canvas'); sheet.width = cw * cols; sheet.height = ch * rows;
  sheet.style.cssText = 'position:fixed;left:0;top:0;width:100vw;z-index:5;background:#000';
  const sc = sheet.getContext('2d');
  const view = params.get('view')?.split(',').map(Number); // yaw,pitch (degrees),distance about each study's target
  list.forEach((index, i) => {
    selectStudy(index);
    if (view) { setMode('orbit'); Object.assign(state.orbit, {yaw: rad(view[0]), pitch: rad(view[1]), dist: view[2], target: track.poseStudies[index].target}); }
    draw();
    const x = (i % cols) * cw, y = Math.floor(i / cols) * ch;
    sc.drawImage(canvas, x, y, cw, ch);
    sc.fillStyle = '#ff6'; sc.font = '600 16px system-ui';
    sc.fillText(`${track.poseStudies[index].group} ${track.poseStudies[index].title}`, x + 8, y + ch - 10);
  });
  document.querySelector('main').prepend(sheet);
  canvas.parentElement.style.display = 'none';
}
load().then(() => {
  if (params.has('orbit')) { // yaw,pitch (degrees),distance,target height[,target z]
    const [y, p, d, ty, tz] = params.get('orbit').split(',').map(Number);
    Object.assign(state.orbit, {yaw: rad(y), pitch: rad(p), dist: d, target: [0, ty, Number.isFinite(tz) ? tz : .8]}); setMode('orbit');
  }
  if (params.has('sheet')) { state.playing = false; syncPlay(); contactSheet(params.get('sheet').split(',').map(Number), +(params.get('cols') || 4)); return; }
  if (params.has('poses')) { const all = track.poseStudies.map((_, i) => i), list = params.get('poses') === 'all' ? all : params.get('poses').split(',').map(Number);
    poseSheet(list, +(params.get('cols') || 2)); return; }
  if(params.has('pose'))selectStudy(Math.max(0,Math.min(track.poseStudies.length-1,Number(params.get('pose'))||0)));
  if (params.has('silhouette')) $('#silhouette').click();
  if (params.has('t')) { state.tick = +params.get('t'); state.playing = false; syncPlay(); }
  if(params.get('play')==='1'&&state.study<0){state.playing=true;syncPlay();}
  if (params.get('cam') === 'orbit') setMode('orbit');
  requestAnimationFrame(frame);
}).catch(e => { $('#error').textContent = e.stack || String(e); });
