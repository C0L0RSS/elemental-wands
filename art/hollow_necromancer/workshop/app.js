// Hollow Necromancer art workshop: renders the real GeckoLib files the way the mod does.
// Geometry, box UVs, pivots and rotation signs follow GeckoLib 5 (BakedModelFactory,
// BakedAnimationsAdapter, RenderUtil.prepMatrixForBone), so what is reviewed here is
// what the game draws. Lighting approximates Minecraft's two-light entity shading.
'use strict';

const FILES = {geo: 'hollow_necromancer.geo.json', animation: 'hollow_necromancer.animation.json',
  texture: 'hollow_necromancer.png', glowmask: 'hollow_necromancer_glowmask.png'};
const FACE_NAMES = {north: 'front', south: 'back', east: 'right side', west: 'left side', up: 'top', down: 'bottom'};
const LIGHTS = {
  day: {level: 1, tint: [1, 1, 1], sky: [.62, .74, .86], floor: [.47, .47, .45]},
  night: {level: .42, tint: [.82, .88, 1.08], sky: [.05, .065, .11], floor: [.3, .3, .32]},
  crypt: {level: .2, tint: [.78, .88, 1.12], sky: [.025, .025, .04], floor: [.26, .25, .28]},
};
const $ = s => document.querySelector(s);
const rad = d => d * Math.PI / 180;

// ---------- Small matrix helpers (column-major, like WebGL) ----------
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
  lookAt(eye, at) {
    const z = norm(sub(eye, at)), x = norm(cross([0, 1, 0], z)), y = cross(z, x);
    return new Float32Array([x[0],y[0],z[0],0, x[1],y[1],z[1],0, x[2],y[2],z[2],0, -dot(x,eye),-dot(y,eye),-dot(z,eye),1]);
  },
};
const sub = (a, b) => a.map((v, i) => v - b[i]);
const dot = (a, b) => a[0]*b[0] + a[1]*b[1] + a[2]*b[2];
const cross = (a, b) => [a[1]*b[2]-a[2]*b[1], a[2]*b[0]-a[0]*b[2], a[0]*b[1]-a[1]*b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };

// ---------- GeckoLib model baking ----------
function bakeModel(geo) {
  const g = geo['minecraft:geometry'][0], d = g.description, W = d.texture_width, H = d.texture_height;
  const byName = new Map();
  for (const b of g.bones) byName.set(b.name, b);
  const order = [], seen = new Set();
  const visit = b => { if (seen.has(b.name)) return; if (b.parent && byName.has(b.parent)) visit(byName.get(b.parent)); seen.add(b.name); order.push(b); };
  g.bones.forEach(visit);
  const bones = order.map(b => {
    const p = b.pivot || [0, 0, 0], r = b.rotation || [0, 0, 0];
    return {name: b.name, parent: b.parent || null, pivot: [-p[0] / 16, p[1] / 16, p[2] / 16],
      rest: [rad(-r[0]), rad(-r[1]), rad(r[2])], cubes: b.cubes || []};
  });
  const faces = [], cubes = [];
  bones.forEach((bone, boneIndex) => bone.cubes.forEach(c => {
    const cubeIndex = cubes.length, s = c.size, o = c.origin;
    cubes.push({bone: bone.name, boneIndex, size: s});
    const ox = -(o[0] + s[0]) / 16, oy = o[1] / 16, oz = o[2] / 16, sx = s[0] / 16, sy = s[1] / 16, sz = s[2] / 16, inf = (c.inflate || 0) / 16;
    const V = {
      blb: [ox - inf, oy - inf, oz - inf], brb: [ox - inf, oy - inf, oz + sz + inf],
      tlb: [ox - inf, oy + sy + inf, oz - inf], trb: [ox - inf, oy + sy + inf, oz + sz + inf],
      tlf: [ox + sx + inf, oy + sy + inf, oz - inf], trf: [ox + sx + inf, oy + sy + inf, oz + sz + inf],
      blf: [ox + sx + inf, oy - inf, oz - inf], brf: [ox + sx + inf, oy - inf, oz + sz + inf]};
    const quads = {west: ['trb','tlb','blb','brb'], east: ['tlf','trf','brf','blf'], north: ['tlb','tlf','blf','blb'],
      south: ['trf','trb','brb','brf'], up: ['trb','trf','tlf','tlb'], down: ['blb','blf','brf','brb']};
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
        const [bu, bv] = c.uv;
        [u, v, us, vs] = {west: [bu+fd+fw, bv+fd, fd, fh], east: [bu, bv+fd, fd, fh], north: [bu+fd, bv+fd, fw, fh],
          south: [bu+fd+fw+fd, bv+fd, fw, fh], up: [bu+fd, bv, fw, fd], down: [bu+fd+fw, bv+fd, fw, -fd]}[dir];
      } else {
        const f = c.uv && c.uv[dir]; if (!f) continue;
        [u, v] = f.uv; [us, vs] = f.uv_size;
      }
      // GeckoLib swaps u for unmirrored faces, then assigns corners clockwise from the first vertex.
      const u0 = (u + us) / W, u1 = u / W, v0 = v / H, v1 = (v + vs) / H;
      let pts = quads[dir].map(k => V[k]), n = normals[dir];
      if (cubeMatrix) { pts = pts.map(p => M.apply(cubeMatrix, p)); n = M.dir(cubeMatrix, n); }
      const area = Math.hypot(...cross(sub(pts[1], pts[0]), sub(pts[2], pts[0])));
      faces.push({cube: cubeIndex, bone: boneIndex, dir, pts, n, uvs: [[u0,v0],[u1,v0],[u1,v1],[u0,v1]],
        rect: [u, Math.min(v, v + vs), us, Math.abs(vs)], empty: area < 1e-9 || us === 0 || vs === 0});
    }
  }));
  return {W, H, bones, cubes, faces, id: d.identifier};
}

// ---------- GeckoLib animation sampling ----------
const keyCache = new WeakMap();
function sampleChannel(channel, t, fallback) {
  if (channel === undefined) return fallback;
  if (Array.isArray(channel) || typeof channel === 'number') return vec(channel, fallback);
  let keys = keyCache.get(channel);
  if (!keys) {
    keys = Object.entries(channel).map(([k, v]) => ({t: +k, pre: vec(v.pre ?? v.post ?? v, fallback), post: vec(v.post ?? v.pre ?? v, fallback)})).sort((a, b) => a.t - b.t);
    keyCache.set(channel, keys);
  }
  if (t <= keys[0].t) return keys[0].post;
  for (let i = 1; i < keys.length; i++) if (t <= keys[i].t) {
    const a = keys[i - 1], b = keys[i], f = (t - a.t) / (b.t - a.t || 1);
    return a.post.map((v, j) => v + (b.pre[j] - v) * f);
  }
  return keys[keys.length - 1].post;
}
function vec(v, fallback) {
  if (typeof v === 'number') return [v, v, v];
  if (Array.isArray(v)) return v.map((x, i) => typeof x === 'number' ? x : fallback[i]); // Molang strings are not evaluated.
  return fallback;
}

function formOf(clipName) { return clipName.includes('colossus') ? 'colossus' : clipName.includes('transform') ? 'both' : 'robed'; }

function poseBones(model, clip, t, form) {
  const out = [], hidden = [], byName = {};
  model.bones.forEach((bone, i) => {
    const ch = (clip && clip.bones && clip.bones[bone.name]) || {};
    const r = sampleChannel(ch.rotation, t, [0, 0, 0]), p = sampleChannel(ch.position, t, [0, 0, 0]), s = sampleChannel(ch.scale, t, [1, 1, 1]);
    const rot = [bone.rest[0] + rad(-r[0]), bone.rest[1] + rad(-r[1]), bone.rest[2] + rad(r[2])];
    const pv = bone.pivot;
    let m = [M.t(-p[0] / 16, p[1] / 16, p[2] / 16), M.t(...pv), M.rz(rot[2]), M.ry(rot[1]), M.rx(rot[0]), M.s(...s), M.t(-pv[0], -pv[1], -pv[2])].reduce(M.mul);
    const parent = bone.parent != null ? byName[bone.parent] : undefined;
    if (parent !== undefined) m = M.mul(out[parent], m);
    // The entity model hides the inactive body (NecromancerModel#setCustomAnimations).
    let hide = parent !== undefined && hidden[parent];
    if (bone.name === 'robe' && form === 'colossus') hide = true;
    if (bone.name === 'colossus' && form === 'robed') hide = true;
    out[i] = m; hidden[i] = hide; byName[bone.name] = i;
  });
  return {matrices: out, hidden};
}

// ---------- WebGL ----------
const canvas = $('#view');
const gl = canvas.getContext('webgl2', {antialias: true, preserveDrawingBuffer: true});
if (!gl) fail('WebGL 2 is not available in this browser.');

const MODEL_VS = `#version 300 es
in vec3 pos; in vec3 normal; in vec2 uv; in float face;
uniform mat4 viewProj; out vec3 vNormal; out vec2 vUv; flat out float vFace;
void main(){ gl_Position = viewProj * vec4(pos, 1.); vNormal = normal; vUv = uv; vFace = face; }`;
const MODEL_FS = `#version 300 es
precision highp float;
in vec3 vNormal; in vec2 vUv; flat in float vFace; out vec4 color;
uniform sampler2D tex; uniform int mode; uniform float level; uniform vec3 tint; uniform float clay;
uniform float hoverCube; uniform float selectCube; uniform float pulse; uniform sampler2D faceCube;
void main(){
  vec4 c = texture(tex, vUv);
  if (mode == 2) { // picking: encode face id
    if (c.a < .1) discard;
    float id = vFace + 1.; color = vec4(mod(id, 256.) / 255., mod(floor(id / 256.), 256.) / 255., floor(id / 65536.) / 255., 1.); return;
  }
  if (c.a < .1) discard; // RenderLayer.getEntityCutoutNoCull
  if (mode == 1) { // glowmask: additive, full bright (AutoGlowingGeoLayer)
    color = vec4(c.rgb, 1.); return;
  }
  vec3 n = normalize(gl_FrontFacing ? vNormal : -vNormal);
  vec3 l0 = normalize(vec3(.2, 1., -.7)), l1 = normalize(vec3(-.2, 1., .7));
  float shade = min(1., .4 + .6 * (max(0., dot(n, l0)) + max(0., dot(n, l1))));
  vec3 base = clay > .5 ? vec3(.72, .7, .67) : c.rgb;
  vec3 lit = base * shade * level * tint;
  float cube = texelFetch(faceCube, ivec2(int(mod(vFace, 1024.)), int(vFace / 1024.)), 0).r * 65535.;
  if (abs(cube - selectCube) < .5) lit = mix(lit, vec3(.5, .95, 1.), .25 + .15 * pulse);
  else if (abs(cube - hoverCube) < .5) lit = mix(lit, vec3(1.), .12);
  color = vec4(lit, 1.);
}`;
const SOLID_VS = `#version 300 es
in vec3 pos; in vec3 normal; in vec3 col; uniform mat4 viewProj; out vec3 vNormal; out vec3 vCol; out vec3 vPos;
void main(){ gl_Position = viewProj * vec4(pos, 1.); vNormal = normal; vCol = col; vPos = pos; }`;
const SOLID_FS = `#version 300 es
precision highp float; in vec3 vNormal; in vec3 vCol; in vec3 vPos; out vec4 color;
uniform float level; uniform vec3 tint; uniform vec3 floorCol; uniform vec3 sky;
void main(){
  vec3 n = normalize(vNormal);
  if (vCol.r < 0.) { // floor: one-block tiles that fade into the sky colour
    vec2 cell = floor(vPos.xz); float checker = mod(cell.x + cell.y, 2.);
    vec2 f = fract(vPos.xz); float edge = step(.965, max(f.x, f.y));
    vec3 c = floorCol * (checker > .5 ? 1. : .9) * (1. - .25 * edge) * level * tint;
    float fade = smoothstep(9., 22., length(vPos.xz));
    color = vec4(mix(c, sky, fade), 1.); return;
  }
  vec3 l0 = normalize(vec3(.2, 1., -.7)), l1 = normalize(vec3(-.2, 1., .7));
  float shade = min(1., .4 + .6 * (max(0., dot(n, l0)) + max(0., dot(n, l1))));
  color = vec4(vCol * shade * level * tint, 1.);
}`;

function program(vs, fs) {
  const p = gl.createProgram();
  for (const [type, src] of [[gl.VERTEX_SHADER, vs], [gl.FRAGMENT_SHADER, fs]]) {
    const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw Error(gl.getShaderInfoLog(s));
    gl.attachShader(p, s);
  }
  gl.linkProgram(p);
  if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw Error(gl.getProgramInfoLog(p));
  const u = {}; const n = gl.getProgramParameter(p, gl.ACTIVE_UNIFORMS);
  for (let i = 0; i < n; i++) { const name = gl.getActiveUniform(p, i).name; u[name] = gl.getUniformLocation(p, name); }
  return {p, u, a: name => gl.getAttribLocation(p, name)};
}
const modelProg = program(MODEL_VS, MODEL_FS), solidProg = program(SOLID_VS, SOLID_FS);

function vao(prog, layout, stride) {
  const va = gl.createVertexArray(), buf = gl.createBuffer();
  gl.bindVertexArray(va); gl.bindBuffer(gl.ARRAY_BUFFER, buf);
  let offset = 0;
  for (const [name, size] of layout) { const loc = prog.a(name); if (loc >= 0) { gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, size, gl.FLOAT, false, stride * 4, offset * 4); } offset += size; }
  gl.bindVertexArray(null);
  return {va, buf, count: 0};
}
const modelMesh = vao(modelProg, [['pos', 3], ['normal', 3], ['uv', 2], ['face', 1]], 9);
const solidMesh = vao(solidProg, [['pos', 3], ['normal', 3], ['col', 3]], 9);

function makeTexture(image) {
  const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.pixelStorei(gl.UNPACK_PREMULTIPLY_ALPHA_WEBGL, false);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
  return t;
}
function faceCubeTexture(model) { // face index -> cube index lookup for highlight tinting
  const w = 1024, h = Math.max(1, Math.ceil(model.faces.length / w)), data = new Uint16Array(w * h);
  model.faces.forEach((f, i) => data[i] = f.cube);
  const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  const f = new Float32Array(w * h); for (let i = 0; i < data.length; i++) f[i] = data[i] / 65535;
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.R32F, w, h, 0, gl.RED, gl.FLOAT, f);
  return t;
}

// ---------- State ----------
const state = {version: 'candidate', sets: {}, clip: null, time: 0, playing: true, speed: 1, hold: 0,
  light: 'night', glow: true, clay: false, player: true, layer: 'texture', zoom: 3, uvLines: true,
  cam: {yaw: rad(-35), pitch: rad(14), dist: 4.2}, target: [0, .8, 0], hoverFace: -1, selectFace: -1};

async function loadSet(version) {
  if (state.sets[version]) return state.sets[version];
  const dir = version + '/', bust = '?v=' + Date.now();
  const json = async f => { const r = await fetch(dir + f + bust); if (!r.ok) throw Error(`${dir}${f}: ${r.status}`); return r.json(); };
  const image = f => new Promise((ok, no) => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => no(Error(dir + f + ' did not load')); i.src = dir + f + bust; });
  const [geo, anim, texture, glowmask] = await Promise.all([json(FILES.geo), json(FILES.animation), image(FILES.texture), image(FILES.glowmask)]);
  let manifest = null; try { const r = await fetch(dir + 'manifest.json' + bust); if (r.ok) manifest = await r.json(); } catch {}
  const model = bakeModel(geo);
  const set = {model, anim, texture, glowmask, manifest, gl: {tex: makeTexture(texture), glow: makeTexture(glowmask), faceCube: faceCubeTexture(model)}};
  state.sets[version] = set;
  return set;
}
const current = () => state.sets[state.version];
const clips = () => current().anim.animations;
const shortName = n => n.replace(/^animation\.[^.]+\./, '');

function fillClips() {
  const select = $('#clip'), keep = state.clip ? shortName(state.clip) : 'idle';
  select.innerHTML = '';
  const groups = {'Robed form': [], 'Transformation': [], 'Colossus': []};
  for (const name of Object.keys(clips())) groups[{robed: 'Robed form', both: 'Transformation', colossus: 'Colossus'}[formOf(name)]].push(name);
  groups['Robed form'].unshift('rest:robed'); groups['Colossus'].unshift('rest:colossus');
  for (const [label, names] of Object.entries(groups)) {
    const og = document.createElement('optgroup'); og.label = label;
    for (const n of names) { const o = document.createElement('option'); o.value = n; o.textContent = n.startsWith('rest:') ? 'rest pose' : shortName(n).replace('colossus_', ''); og.append(o); }
    select.append(og);
  }
  const match = [...select.options].find(o => shortName(o.value) === keep) || [...select.options].find(o => o.value.endsWith('.idle'));
  select.value = match.value; setClip(match.value, true);
}
function clipData() { return state.clip.startsWith('rest:') ? null : clips()[state.clip]; }
function clipForm() { return state.clip.startsWith('rest:') ? state.clip.slice(5) : formOf(state.clip); }
function clipLength() { const c = clipData(); return c ? (c.animation_length || 1) : 1; }
function setClip(name, keepTime) {
  state.clip = name; if (!keepTime) state.time = 0; state.time = Math.min(state.time, clipLength()); state.hold = 0;
  $('#timeline').max = clipLength();
  const colossal = clipForm() !== 'robed';
  state.target = colossal ? [0, 2.1, 0] : [0, 1.1, 0];
  if (!keepTime) state.cam.dist = colossal ? 10.5 : 5.2;
}

// ---------- Geometry upload ----------
function buildModelBuffer(pose) {
  const {model} = current(), data = [];
  model.faces.forEach((f, i) => {
    if (f.empty || pose.hidden[f.bone]) return;
    const m = pose.matrices[f.bone], p = f.pts.map(v => M.apply(m, v)), n = norm(M.dir(m, f.n));
    for (const k of [0, 1, 2, 0, 2, 3]) data.push(...p[k], ...n, ...f.uvs[k], i);
  });
  gl.bindBuffer(gl.ARRAY_BUFFER, modelMesh.buf); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(data), gl.DYNAMIC_DRAW);
  modelMesh.count = data.length / 9;
}
function box(data, min, max, col) {
  const [x0, y0, z0] = min, [x1, y1, z1] = max;
  const quads = [[[x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0],[0,0,-1]], [[x1,y0,z1],[x0,y0,z1],[x0,y1,z1],[x1,y1,z1],[0,0,1]],
    [[x0,y0,z1],[x0,y0,z0],[x0,y1,z0],[x0,y1,z1],[-1,0,0]], [[x1,y0,z0],[x1,y0,z1],[x1,y1,z1],[x1,y1,z0],[1,0,0]],
    [[x0,y1,z0],[x1,y1,z0],[x1,y1,z1],[x0,y1,z1],[0,1,0]], [[x0,y0,z1],[x1,y0,z1],[x1,y0,z0],[x0,y0,z0],[0,-1,0]]];
  for (const q of quads) for (const k of [0, 1, 2, 0, 2, 3]) data.push(...q[k], ...q[4], ...col);
}
function buildSolidBuffer() {
  const data = [], R = 40;
  for (const k of [0, 1, 2, 0, 2, 3]) data.push(...[[-R,0,-R],[R,0,-R],[R,0,R],[-R,0,R]][k], 0, 1, 0, -1, 0, 0);
  if (state.player) { // Steve-sized reference, facing the model, beside it.
    const x = clipForm() === 'robed' ? 1.6 : 4.6, z = 0, skin = [.78, .6, .47], shirt = [.2, .62, .66], pants = [.24, .25, .52];
    box(data, [x - .25, 1.5, z - .25], [x + .25, 2, z + .25], skin);
    box(data, [x - .25, .75, z - .125], [x + .25, 1.5, z + .125], shirt);
    box(data, [x - .5, .75, z - .125], [x - .25, 1.5, z + .125], skin); box(data, [x + .25, .75, z - .125], [x + .5, 1.5, z + .125], skin);
    box(data, [x - .25, 0, z - .125], [x, .75, z + .125], pants); box(data, [x, 0, z - .125], [x + .25, .75, z + .125], pants);
  }
  gl.bindBuffer(gl.ARRAY_BUFFER, solidMesh.buf); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(data), gl.DYNAMIC_DRAW);
  solidMesh.count = data.length / 9;
}

// ---------- Camera & drawing ----------
function viewProj() {
  const {yaw, pitch} = state.cam, t = state.target, aspect = canvas.width / canvas.height;
  const dist = state.cam.dist / Math.min(1, aspect / 1.2);
  const eye = [t[0] - Math.sin(yaw) * Math.cos(pitch) * dist, t[1] + Math.sin(pitch) * dist, t[2] - Math.cos(yaw) * Math.cos(pitch) * dist];
  return M.mul(M.perspective(rad(40), canvas.width / canvas.height, .05, 200), M.lookAt(eye, t));
}
let pickFbo = null, pickSize = [0, 0];
function draw() {
  const set = current(); if (!set) return;
  const dpr = Math.min(devicePixelRatio, 2), w = Math.round(canvas.clientWidth * dpr), h = Math.round(canvas.clientHeight * dpr);
  if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
  const pose = poseBones(set.model, clipData(), state.time, clipForm());
  buildModelBuffer(pose); buildSolidBuffer();
  const L = LIGHTS[state.light], vp = viewProj();
  gl.viewport(0, 0, w, h); gl.clearColor(...L.sky, 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
  gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL); gl.disable(gl.CULL_FACE); gl.disable(gl.BLEND); gl.depthMask(true);
  gl.useProgram(solidProg.p); gl.bindVertexArray(solidMesh.va);
  gl.uniformMatrix4fv(solidProg.u.viewProj, false, vp); gl.uniform1f(solidProg.u.level, L.level); gl.uniform3fv(solidProg.u.tint, L.tint);
  gl.uniform3fv(solidProg.u.floorCol, L.floor); gl.uniform3fv(solidProg.u.sky, L.sky);
  gl.drawArrays(gl.TRIANGLES, 0, solidMesh.count);
  const selectCube = state.selectFace >= 0 ? set.model.faces[state.selectFace].cube : -9, hoverCube = state.hoverFace >= 0 ? set.model.faces[state.hoverFace].cube : -9;
  gl.useProgram(modelProg.p); gl.bindVertexArray(modelMesh.va);
  gl.uniformMatrix4fv(modelProg.u.viewProj, false, vp); gl.uniform1f(modelProg.u.level, L.level); gl.uniform3fv(modelProg.u.tint, L.tint);
  gl.uniform1f(modelProg.u.clay, state.clay ? 1 : 0); gl.uniform1f(modelProg.u.selectCube, selectCube); gl.uniform1f(modelProg.u.hoverCube, hoverCube);
  gl.uniform1f(modelProg.u.pulse, .5 + .5 * Math.sin(performance.now() / 180));
  gl.uniform1i(modelProg.u.tex, 0); gl.uniform1i(modelProg.u.faceCube, 1);
  gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, set.gl.faceCube);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, set.gl.tex);
  gl.uniform1i(modelProg.u.mode, 0); gl.drawArrays(gl.TRIANGLES, 0, modelMesh.count);
  if (state.glow && !state.clay) {
    gl.bindTexture(gl.TEXTURE_2D, set.gl.glow); gl.uniform1i(modelProg.u.mode, 1);
    gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE); gl.depthMask(false);
    gl.drawArrays(gl.TRIANGLES, 0, modelMesh.count);
    gl.depthMask(true); gl.disable(gl.BLEND);
  }
}
function pick(clientX, clientY) {
  const set = current(); if (!set) return -1;
  const w = canvas.width, h = canvas.height;
  if (!pickFbo || pickSize[0] !== w || pickSize[1] !== h) {
    pickFbo = gl.createFramebuffer(); gl.bindFramebuffer(gl.FRAMEBUFFER, pickFbo);
    const c = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, c); gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA8, w, h, 0, gl.RGBA, gl.UNSIGNED_BYTE, null);
    gl.framebufferTexture2D(gl.FRAMEBUFFER, gl.COLOR_ATTACHMENT0, gl.TEXTURE_2D, c, 0);
    const d = gl.createRenderbuffer(); gl.bindRenderbuffer(gl.RENDERBUFFER, d); gl.renderbufferStorage(gl.RENDERBUFFER, gl.DEPTH_COMPONENT24, w, h);
    gl.framebufferRenderbuffer(gl.FRAMEBUFFER, gl.DEPTH_ATTACHMENT, gl.RENDERBUFFER, d);
    pickSize = [w, h];
  }
  gl.bindFramebuffer(gl.FRAMEBUFFER, pickFbo); gl.viewport(0, 0, w, h);
  gl.clearColor(0, 0, 0, 0); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT); gl.enable(gl.DEPTH_TEST); gl.disable(gl.BLEND);
  gl.useProgram(modelProg.p); gl.bindVertexArray(modelMesh.va);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, set.gl.tex); gl.uniform1i(modelProg.u.mode, 2);
  gl.drawArrays(gl.TRIANGLES, 0, modelMesh.count);
  const r = canvas.getBoundingClientRect(), px = new Uint8Array(4);
  gl.readPixels(Math.floor((clientX - r.left) * w / r.width), Math.floor(h - (clientY - r.top) * h / r.height), 1, 1, gl.RGBA, gl.UNSIGNED_BYTE, px);
  gl.bindFramebuffer(gl.FRAMEBUFFER, null);
  return px[0] + px[1] * 256 + px[2] * 65536 - 1;
}

// ---------- Texture atlas panel ----------
const atlas = $('#atlas'), actx = atlas.getContext('2d');
function faceLabel(i) {
  const set = current(), f = set.model.faces[i], bone = set.model.bones[f.bone].name;
  const sz = set.model.cubes[f.cube].size.join('×');
  return `<b>${bone}</b> · ${FACE_NAMES[f.dir]} · cube ${sz} px`;
}
function drawAtlas() {
  const set = current(); if (!set) return;
  const {W, H} = set.model, z = state.zoom, img = state.layer === 'texture' ? set.texture : set.glowmask;
  if (atlas.width !== W * z || atlas.height !== H * z) { atlas.width = W * z; atlas.height = H * z; }
  actx.imageSmoothingEnabled = false; actx.clearRect(0, 0, atlas.width, atlas.height);
  if (state.layer === 'glowmask') { actx.fillStyle = '#050508'; actx.fillRect(0, 0, atlas.width, atlas.height); }
  actx.drawImage(img, 0, 0, W * z, H * z);
  const pose = poseBones(set.model, clipData(), state.time, clipForm());
  const rectOf = f => f.rect.map(v => v * z);
  if (state.uvLines) {
    actx.lineWidth = 1; actx.strokeStyle = 'rgba(127,227,234,.28)';
    set.model.faces.forEach(f => { if (f.empty || pose.hidden[f.bone]) return; const [x, y, w, h] = rectOf(f); actx.strokeRect(x + .5, y + .5, w - 1, h - 1); });
  }
  const mark = (i, fill, stroke) => {
    if (i < 0) return; const cube = set.model.faces[i].cube;
    set.model.faces.forEach((f, j) => { if (f.cube !== cube) return; const [x, y, w, h] = rectOf(f); actx.fillStyle = j === i ? fill : fill.replace(/[\d.]+\)$/, '.12)'); actx.fillRect(x, y, w, h); actx.strokeStyle = stroke; actx.lineWidth = j === i ? 2 : 1; actx.strokeRect(x + 1, y + 1, w - 2, h - 2); });
  };
  mark(state.hoverFace, 'rgba(255,255,255,.18)', 'rgba(255,255,255,.7)');
  mark(state.selectFace, 'rgba(127,227,234,.3)', '#7fe3ea');
}
function faceAtPixel(x, y) {
  const set = current(); let hit = -1;
  const pose = poseBones(set.model, clipData(), state.time, clipForm());
  set.model.faces.forEach((f, i) => { if (f.empty) return; const [u, v, w, h] = f.rect; if (x >= u && x < u + w && y >= v && y < v + h && (hit < 0 || !pose.hidden[f.bone])) hit = i; });
  return hit;
}
let pixelCache = null;
function pixelAt(x, y) {
  const set = current(), img = state.layer === 'texture' ? set.texture : set.glowmask;
  if (!pixelCache || pixelCache.img !== img) { const c = document.createElement('canvas'); c.width = img.width; c.height = img.height; const x2 = c.getContext('2d'); x2.drawImage(img, 0, 0); pixelCache = {img, data: x2.getImageData(0, 0, img.width, img.height).data, w: img.width}; }
  const o = (y * pixelCache.w + x) * 4; return [...pixelCache.data.slice(o, o + 4)];
}
atlas.addEventListener('pointermove', e => {
  const set = current(); if (!set) return;
  const r = atlas.getBoundingClientRect(), x = Math.floor((e.clientX - r.left) / state.zoom), y = Math.floor((e.clientY - r.top) / state.zoom);
  if (x < 0 || y < 0 || x >= set.model.W || y >= set.model.H) return;
  const i = faceAtPixel(x, y), [pr, pg, pb, pa] = pixelAt(x, y);
  state.hoverFace = i;
  const hex = '#' + [pr, pg, pb].map(v => v.toString(16).padStart(2, '0')).join('');
  $('#atlas-info').innerHTML = `Pixel ${x}, ${y} · <i style="background:rgba(${pr},${pg},${pb},${pa / 255})"></i>${pa ? hex : 'transparent'}${pa && pa < 255 ? ' α' + pa : ''}<br>${i >= 0 ? faceLabel(i) : 'unused space'}`;
  dirty = true;
});
atlas.addEventListener('pointerleave', () => { state.hoverFace = -1; dirty = true; });
atlas.addEventListener('click', () => { if (state.hoverFace >= 0) select(state.hoverFace, false); });

function select(i, scroll) {
  state.selectFace = i; $('#add-part').disabled = i < 0;
  $('#picked').innerHTML = i >= 0 ? 'Selected: ' + faceLabel(i) : 'Hover the model to identify a part · click to select it in the texture';
  if (i >= 0 && scroll) {
    const [u, v, w, h] = current().model.faces[i].rect.map(n => n * state.zoom), wrap = $('.atlas-wrap');
    wrap.scrollTo({left: u + w / 2 - wrap.clientWidth / 2, top: v + h / 2 - wrap.clientHeight / 2, behavior: 'smooth'});
  }
  dirty = true;
}

// ---------- Interaction ----------
let drag = null, moved = 0, dirty = true;
canvas.addEventListener('pointerdown', e => { drag = [e.clientX, e.clientY]; moved = 0; canvas.setPointerCapture(e.pointerId); canvas.classList.add('dragging'); });
canvas.addEventListener('pointermove', e => {
  if (drag) {
    const dx = e.clientX - drag[0], dy = e.clientY - drag[1]; moved += Math.abs(dx) + Math.abs(dy);
    state.cam.yaw -= dx * .008; state.cam.pitch = Math.max(-1.2, Math.min(1.35, state.cam.pitch + dy * .008));
    drag = [e.clientX, e.clientY]; activeView(''); dirty = true; return;
  }
  hover3d = [e.clientX, e.clientY];
});
let hover3d = null;
canvas.addEventListener('pointerup', e => {
  canvas.classList.remove('dragging');
  if (drag && moved < 4) { const i = pick(e.clientX, e.clientY); select(i, true); }
  drag = null;
});
canvas.addEventListener('pointerleave', () => { hover3d = null; if (!drag) { state.hoverFace = -1; dirty = true; } });
canvas.addEventListener('wheel', e => { e.preventDefault(); state.cam.dist = Math.max(1.2, Math.min(40, state.cam.dist * Math.exp(e.deltaY * .0012))); dirty = true; }, {passive: false});

function activeView(v) { document.querySelectorAll('[data-view]').forEach(b => b.classList.toggle('active', b.dataset.view === v)); }
document.querySelectorAll('[data-view]').forEach(b => b.onclick = () => {
  const v = b.dataset.view, colossal = clipForm() !== 'robed';
  const base = colossal ? 11.5 : 4.2;
  Object.assign(state.cam, {hero: {yaw: rad(-35), pitch: rad(14), dist: base}, front: {yaw: 0, pitch: rad(4), dist: base},
    side: {yaw: rad(90), pitch: rad(4), dist: base}, back: {yaw: rad(180), pitch: rad(4), dist: base}}[v] || {});
  if (v === 'eye') { // Standing player's eye (1.62 blocks) a few blocks in front.
    const d = colossal ? 9 : 5, dy = 1.62 - state.target[1];
    Object.assign(state.cam, {yaw: rad(-12), dist: Math.hypot(d, dy), pitch: Math.atan2(dy, d)});
  }
  activeView(v); dirty = true;
});
const toggle = (id, key) => $(id).onclick = () => { state[key] = !state[key]; $(id).classList.toggle('active', state[key]); dirty = true; };
toggle('#toggle-player', 'player'); toggle('#toggle-glow', 'glow'); toggle('#toggle-clay', 'clay');
document.querySelectorAll('[data-light]').forEach(b => b.onclick = () => { state.light = b.dataset.light; document.querySelectorAll('[data-light]').forEach(x => x.classList.toggle('active', x === b)); dirty = true; });
document.querySelectorAll('[data-layer]').forEach(b => b.onclick = () => { state.layer = b.dataset.layer; document.querySelectorAll('[data-layer]').forEach(x => x.classList.toggle('active', x === b)); dirty = true; });
document.querySelectorAll('[data-zoom]').forEach(b => b.onclick = () => { state.zoom = +b.dataset.zoom; document.querySelectorAll('[data-zoom]').forEach(x => x.classList.toggle('active', x === b)); dirty = true; });
$('#uv-lines').onchange = e => { state.uvLines = e.target.checked; dirty = true; };
$('#clip').onchange = e => { setClip(e.target.value); state.playing = true; syncPlay(); dirty = true; };
$('#speed').onchange = e => state.speed = +e.target.value;
function syncPlay() { $('#play').textContent = state.playing ? '❚❚' : '▶'; $('#play').setAttribute('aria-label', state.playing ? 'Pause' : 'Play'); }
$('#play').onclick = () => { state.playing = !state.playing; if (state.playing && state.time >= clipLength()) state.time = 0; syncPlay(); };
$('#replay').onclick = () => { state.time = 0; state.hold = 0; state.playing = true; syncPlay(); dirty = true; };
$('#timeline').oninput = e => { state.time = +e.target.value; state.playing = false; syncPlay(); dirty = true; };
addEventListener('keydown', e => {
  if (e.target.tagName === 'TEXTAREA' || e.target.tagName === 'SELECT') return;
  if (e.key === ' ') { e.preventDefault(); $('#play').click(); }
  if (e.key === 'c') switchVersion(state.version === 'candidate' ? 'installed' : 'candidate');
  if (e.key.startsWith('Arrow')) { e.preventDefault(); if (e.key === 'ArrowLeft') state.cam.yaw -= .1; if (e.key === 'ArrowRight') state.cam.yaw += .1;
    if (e.key === 'ArrowUp') state.cam.pitch = Math.min(1.35, state.cam.pitch + .1); if (e.key === 'ArrowDown') state.cam.pitch = Math.max(-1.2, state.cam.pitch - .1); activeView(''); dirty = true; }
});
document.querySelectorAll('[data-version]').forEach(b => b.onclick = () => switchVersion(b.dataset.version));
async function switchVersion(v) {
  try {
    await loadSet(v); state.version = v; state.hoverFace = state.selectFace = -1; pixelCache = null; select(-1);
    document.querySelectorAll('[data-version]').forEach(x => x.classList.toggle('active', x.dataset.version === v));
    fillClips(); stats(); dirty = true;
  } catch (err) { fail(err.message); }
}

// ---------- Notes ----------
const NOTES_KEY = 'hollow-necromancer-workshop-notes';
try { $('#notes').value = localStorage.getItem(NOTES_KEY) || ''; } catch {}
$('#notes').oninput = () => { try { localStorage.setItem(NOTES_KEY, $('#notes').value); $('#notes-state').textContent = 'Saved in this browser'; } catch { $('#notes-state').textContent = 'Not saved (storage blocked)'; } };
$('#copy-notes').onclick = async () => { try { await navigator.clipboard.writeText($('#notes').value); $('#notes-state').textContent = 'Copied'; } catch { $('#notes').select(); $('#notes-state').textContent = 'Select-all done; press ⌘C'; } };
$('#add-part').onclick = () => {
  if (state.selectFace < 0) return;
  const set = current(), f = set.model.faces[state.selectFace];
  const ref = `[${state.version} · ${set.model.bones[f.bone].name} · ${FACE_NAMES[f.dir]} · ${shortName(state.clip)} @ ${state.time.toFixed(2)}s] `;
  const t = $('#notes'); t.value += (t.value && !t.value.endsWith('\n') ? '\n' : '') + ref; t.focus(); t.oninput();
};

function stats() {
  const s = current(), m = s.model, info = s.manifest;
  const label = state.sets.candidate?.manifest?.revision; if (label) $('#candidate-label').textContent = label;
  $('#stats').innerHTML = `${info ? `<b>${info.revision}</b> · ${info.date}<br>${info.summary || ''}<br>` : ''}` +
    `${m.cubes.length} cubes · ${m.bones.length} bones · ${m.W}×${m.H} texture · ${Object.keys(s.anim.animations).length} animations<br>` +
    `Keys: space play/pause · C swap candidate/in-game · arrows orbit`;
}
function fail(message) {
  let el = $('.error'); if (!el) { el = document.createElement('div'); el.className = 'error'; $('.stage').append(el); }
  el.textContent = message + (location.protocol === 'file:' ? ' Open this page through the local server described in README.md.' : '');
}

// ---------- Loop ----------
let last = 0, atlasTimer = 0;
function frame(now) {
  const dt = last ? Math.min(.1, (now - last) / 1000) : 0; last = now;
  if (current()) {
    if (state.playing) {
      const len = clipLength(), c = clipData();
      if (state.time >= len && !(c && c.loop)) { state.hold += dt; if (state.hold > .8) { state.time = 0; state.hold = 0; } }
      else { state.time += dt * state.speed; if (state.time >= len) state.time = c && c.loop ? state.time % len : len; }
      $('#timeline').value = state.time; $('#time').textContent = state.time.toFixed(2) + ' s';
      dirty = true;
    }
    if (hover3d && !drag) { const i = pick(...hover3d); if (i !== state.hoverFace) { state.hoverFace = i; $('#picked').innerHTML = i >= 0 ? faceLabel(i) : (state.selectFace >= 0 ? 'Selected: ' + faceLabel(state.selectFace) : 'Hover the model to identify a part · click to select it in the texture'); dirty = true; } }
    if (dirty || state.selectFace >= 0) { draw(); if (now - atlasTimer > 90 || !state.playing) { drawAtlas(); atlasTimer = now; } dirty = false; }
  }
  requestAnimationFrame(frame);
}
addEventListener('resize', () => dirty = true);
// Link settings: ?version=installed&clip=colossus_idle&view=front&light=day&zoom=2&t=1.2&paused&clean
const params = new URLSearchParams(location.search);
if (params.has('clean')) document.body.classList.add('clean');
switchVersion(params.get('version') || 'candidate').then(() => {
  const clip = params.get('clip');
  if (clip) { const o = [...$('#clip').options].find(x => x.value === clip || shortName(x.value) === clip || x.value === 'rest:' + clip); if (o) { $('#clip').value = o.value; setClip(o.value); } }
  if (params.get('light')) document.querySelector(`[data-light="${params.get('light')}"]`)?.click();
  if (params.get('view')) document.querySelector(`[data-view="${params.get('view')}"]`)?.click();
  if (params.get('yaw')) { state.cam.yaw = rad(+params.get('yaw')); activeView(''); }
  if (params.get('pitch')) state.cam.pitch = rad(+params.get('pitch'));
  if (params.get('zoom')) state.cam.dist /= +params.get('zoom');
  if (params.get('ty')) state.target[1] = +params.get('ty');
  if (params.has('t')) { state.time = Math.min(+params.get('t'), clipLength()); }
  if (params.has('paused') || params.has('t')) { state.playing = false; syncPlay(); }
  if (params.has('noref')) { state.player = false; $('#toggle-player').classList.remove('active'); }
  dirty = true;
}).catch(err => fail(err.message));
requestAnimationFrame(frame);
