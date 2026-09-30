// Guardian intro animatic: the baked Guardian and zombie clips on a stand-in nave floor, seen
// through the scene's per-tick camera track (intro-track.json), as GuardianIntroClient plays it.
// Model baking, box UVs and bone maths follow GeckoLib 5 as in the Necromancer workshop
// (art/hollow_necromancer/workshop/app.js). Both actors stand at the seat facing +Z of the
// scene's frame; GeckoLib's mirrored model space turns into that frame with a half turn about Y.
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

function poseBones(model, clip, seconds, base) {
  const out = [];
  const byName = {};
  model.bones.forEach((bone, i) => {
    const ch = clip.bones[bone.name] || {};
    const r = sampleChannel(ch.rotation, seconds, [0, 0, 0]), p = sampleChannel(ch.position, seconds, [0, 0, 0]), s = sampleChannel(ch.scale, seconds, [1, 1, 1]);
    const rot = [bone.rest[0] + rad(-r[0]), bone.rest[1] + rad(-r[1]), bone.rest[2] + rad(r[2])], pv = bone.pivot;
    let m = [M.t(-p[0] / 16, p[1] / 16, p[2] / 16), M.t(...pv), M.rz(rot[2]), M.ry(rot[1]), M.rx(rot[0]), M.s(...s), M.t(-pv[0], -pv[1], -pv[2])].reduce(M.mul);
    m = M.mul(bone.parent != null && byName[bone.parent] !== undefined ? out[byName[bone.parent]] : base, m);
    out[i] = m; byName[bone.name] = i;
  });
  return out;
}

function meshData(model, matrices) {
  const data = [];
  for (const f of model.faces) {
    const m = matrices[f.bone], p = f.pts.map(v => M.apply(m, v)), n = norm(M.dir(m, f.n));
    if (!n.every(Number.isFinite) || !p.every(q => q.every(Number.isFinite))) continue;
    for (const k of [0, 1, 2, 0, 2, 3]) data.push(...p[k], ...n, ...f.uvs[k]);
  }
  return new Float32Array(data);
}

// ---------- WebGL ----------
const canvas = $('#view'), hud = $('#hud'), ctx = hud.getContext('2d');
const gl = canvas.getContext('webgl2', {antialias: true, preserveDrawingBuffer: true});
const FOG = [.035, .04, .052];
const MODEL_VS = `#version 300 es
in vec3 pos; in vec3 normal; in vec2 uv; uniform mat4 viewProj; out vec3 vNormal; out vec2 vUv; out vec3 vPos;
void main(){ gl_Position = viewProj * vec4(pos, 1.); vNormal = normal; vUv = uv; vPos = pos; }`;
const MODEL_FS = `#version 300 es
precision highp float; in vec3 vNormal; in vec2 vUv; in vec3 vPos; out vec4 color;
uniform sampler2D tex; uniform int glow; uniform vec3 eye; uniform vec3 fog;
void main(){
  vec4 c = texture(tex, vUv); if (c.a < .1) discard;
  float haze = smoothstep(40., 140., distance(vPos, eye));
  if (glow == 1) { color = vec4(c.rgb * (1. - haze), 1.); return; }
  vec3 n = normalize(gl_FrontFacing ? vNormal : -vNormal);
  vec3 l0 = normalize(vec3(.2, 1., -.7)), l1 = normalize(vec3(-.2, 1., .7));
  float shade = min(1., .4 + .6 * (max(0., dot(n, l0)) + max(0., dot(n, l1))));
  color = vec4(mix(c.rgb * shade * .78, fog, haze), 1.);
}`;
const SOLID_VS = `#version 300 es
in vec3 pos; in vec3 normal; in vec3 col; uniform mat4 viewProj; out vec3 vNormal; out vec3 vCol; out vec3 vPos;
void main(){ gl_Position = viewProj * vec4(pos, 1.); vNormal = normal; vCol = col; vPos = pos; }`;
const SOLID_FS = `#version 300 es
precision highp float; in vec3 vNormal; in vec3 vCol; in vec3 vPos; out vec4 color; uniform vec3 eye; uniform vec3 fog;
void main(){
  float haze = smoothstep(30., 140., distance(vPos, eye));
  if (vCol.r < 0.) { // floor: one-block stone tiles, a glowing seat inlay at the origin
    vec2 cell = floor(vPos.xz); float checker = mod(cell.x + cell.y, 2.);
    vec2 f = fract(vPos.xz); float edge = step(.94, max(f.x, f.y));
    vec3 c = vec3(.25, .25, .27) * (checker > .5 ? 1. : .86) * (1. - .35 * edge);
    float seat = step(max(abs(vPos.x), abs(vPos.z)), 1.5) * step(1.2, max(abs(vPos.x), abs(vPos.z)));
    c = mix(c, vec3(.45, .85, .9), seat);
    color = vec4(mix(c, fog, haze), 1.); return;
  }
  if (vCol.g < 0.) { color = vec4(0.42, .95, 1., 1.); return; } // camera gizmo
  vec3 n = normalize(vNormal); vec3 l0 = normalize(vec3(.2, 1., -.7)), l1 = normalize(vec3(-.2, 1., .7));
  float shade = min(1., .4 + .6 * (max(0., dot(n, l0)) + max(0., dot(n, l1))));
  color = vec4(mix(vCol * shade, fog, haze), 1.);
}`;
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
function box(data, min, max, col) {
  const [x0, y0, z0] = min, [x1, y1, z1] = max;
  const quads = [[[x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0],[0,0,-1]], [[x1,y0,z1],[x0,y0,z1],[x0,y1,z1],[x1,y1,z1],[0,0,1]],
    [[x0,y0,z1],[x0,y0,z0],[x0,y1,z0],[x0,y1,z1],[-1,0,0]], [[x1,y0,z0],[x1,y0,z1],[x1,y1,z1],[x1,y1,z0],[1,0,0]],
    [[x0,y1,z0],[x1,y1,z0],[x1,y1,z1],[x0,y1,z1],[0,1,0]], [[x0,y0,z1],[x1,y0,z1],[x1,y0,z0],[x0,y0,z0],[0,-1,0]]];
  for (const q of quads) for (const k of [0, 1, 2, 0, 2, 3]) data.push(...q[k], ...q[4], ...col);
}

// ---------- The scene ----------
const state = {tick: 0, playing: true, speed: 1, mode: 'scene', bars: true, orbit: {yaw: rad(35), pitch: rad(18), dist: 16, target: [0, 2.2, 6]}};
let actors, track, modelProg, solidProg, modelMesh, solidMesh;
const ENTITY = M.ry(Math.PI); // GeckoLib's mirrored model space into the scene's frame, facing +Z

async function load() {
  const bust = '?v=' + Date.now();
  const json = async f => { const r = await fetch(f + bust); if (!r.ok) throw Error(`${f}: ${r.status}`); return r.json(); };
  const image = f => new Promise((ok, no) => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => no(Error(f + ' did not load')); i.src = f + bust; });
  const [gGeo, gAnim, gTex, gGlow, zGeo, zAnim, zTex, trackData] = await Promise.all([
    json('fractured_guardian.geo.json'), json('fractured_guardian.animation.json'), image('fractured_guardian.png'), image('fractured_guardian_glowmask.png'),
    json('intro_zombie.geo.json'), json('intro_zombie.animation.json'), image('intro_zombie.png'), json('intro-track.json')]);
  modelProg = program(MODEL_VS, MODEL_FS); solidProg = program(SOLID_VS, SOLID_FS);
  modelMesh = vao(modelProg, [['pos', 3], ['normal', 3], ['uv', 2]], 8);
  solidMesh = vao(solidProg, [['pos', 3], ['normal', 3], ['col', 3]], 9);
  actors = [
    {model: bakeModel(gGeo), clip: gAnim.animations['animation.fractured_guardian.intro'], tex: texture(gTex), glow: texture(gGlow)},
    {model: bakeModel(zGeo), clip: zAnim.animations['animation.guardian_intro_zombie.intro'], tex: texture(zTex)}];
  track = trackData;
  $('#timeline').max = track.beats.length;
  const shots = [['1 · wanderer', 0], ['2 · top-down', track.beats.stop], ['3 · its eyes', track.beats.pov], ['4 · smash', track.beats.impact],
    ['5 · up close', track.beats.show], ['6 · players', track.beats.players], ['title', track.beats.title], ['return', track.beats.ret]];
  for (const [label, tick] of shots) {
    const b = document.createElement('button'); b.textContent = label;
    b.onclick = () => { state.tick = tick; state.playing = false; syncPlay(); };
    $('#shots').append(b);
  }
}

/** The scene camera at a fractional tick, as GuardianIntroClient samples the track. */
function sceneCamera(t) {
  const T = track.ticks, n = T.eye.length - 1;
  const a = Math.max(0, Math.min(n, Math.floor(t))), b = Math.min(n, a + 1);
  const f = track.cuts.includes(b) ? 0 : t - a; // hold the shot until the cut rather than smear across it
  const pick = k => typeof T[k][a] === 'number' ? T[k][a] + (T[k][b] - T[k][a]) * f : lerp(T[k][a], T[k][b], f);
  let yawA = T.yaw[a], yawB = T.yaw[b]; while (yawB - yawA > 180) yawB -= 360; while (yawB - yawA < -180) yawB += 360;
  const eye = pick('eye'), yaw = yawA + (yawB - yawA) * f, pitch = pick('pitch'), fov = pick('fov');
  return {eye, yaw, pitch, fov, flash: pick('flash'), black: pick('black')};
}

/** Minecraft's camera axes for a yaw and pitch (yaw 0 looks along +Z; +pitch looks down). */
function axes(yaw, pitch) {
  const y = rad(yaw), p = rad(pitch);
  const forward = [-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)];
  const right = [-Math.cos(y), 0, -Math.sin(y)];
  return {forward, right, up: cross(right, forward)};
}

function draw() {
  const dpr = Math.min(devicePixelRatio, 2), w = Math.round(canvas.clientWidth * dpr), h = Math.round(canvas.clientHeight * dpr);
  if (canvas.width !== w || canvas.height !== h) { canvas.width = hud.width = w; canvas.height = hud.height = h; }
  const t = state.tick, cam = sceneCamera(t);
  let eye, vp;
  if (state.mode === 'scene') {
    const {forward, right, up} = axes(cam.yaw, cam.pitch);
    eye = cam.eye;
    vp = M.mul(M.perspective(rad(cam.fov), w / h, .05, 400), M.view(eye, right, up, scale(forward, -1)));
  } else {
    const o = state.orbit;
    eye = add(o.target, [Math.sin(o.yaw) * Math.cos(o.pitch) * o.dist, Math.sin(o.pitch) * o.dist, Math.cos(o.yaw) * Math.cos(o.pitch) * o.dist]);
    const z = norm(sub(eye, o.target)), x = norm(cross([0, 1, 0], z)), y = cross(z, x);
    vp = M.mul(M.perspective(rad(50), w / h, .05, 400), M.view(eye, x, y, z));
  }
  gl.viewport(0, 0, w, h); gl.clearColor(...FOG, 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
  gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL); gl.disable(gl.CULL_FACE);

  // Floor, the players at the arrival line and, in orbit, the scene camera.
  const solid = [], R = 160;
  for (const k of [0, 1, 2, 0, 2, 3]) solid.push(...[[-R,0,-R],[R,0,-R],[R,0,R],[-R,0,R]][k], 0, 1, 0, -1, 0, 0);
  for (const x of [-2, 0, 2]) {
    const z = 28, skin = [.78, .6, .47], shirt = [.2, .62, .66], pants = [.24, .25, .52];
    box(solid, [x - .25, 1.5, z - .25], [x + .25, 2, z + .25], skin); box(solid, [x - .25, .75, z - .125], [x + .25, 1.5, z + .125], shirt);
    box(solid, [x - .5, .75, z - .125], [x - .25, 1.5, z + .125], skin); box(solid, [x + .25, .75, z - .125], [x + .5, 1.5, z + .125], skin);
    box(solid, [x - .25, 0, z - .125], [x, .75, z + .125], pants); box(solid, [x, 0, z - .125], [x + .25, .75, z + .125], pants);
  }
  if (state.mode === 'orbit') {
    const {forward, right, up} = axes(cam.yaw, cam.pitch), e = cam.eye, g = [1, -1, 1];
    box(solid, sub(e, [.12, .12, .12]), add(e, [.12, .12, .12]), g);
    for (let i = 1; i <= 12; i++) { const c = add(e, scale(forward, i * .18)); box(solid, sub(c, [.03, .03, .03]), add(c, [.03, .03, .03]), g); }
    for (const [sx, sy] of [[1, 1], [-1, 1], [1, -1], [-1, -1]]) {
      const spread = Math.tan(rad(cam.fov / 2)), corner = add(add(forward, scale(up, sy * spread)), scale(right, sx * spread * 16 / 9));
      for (let i = 1; i <= 5; i++) { const c = add(e, scale(corner, i * .25)); box(solid, sub(c, [.025, .025, .025]), add(c, [.025, .025, .025]), g); }
    }
  }
  gl.useProgram(solidProg.p); gl.bindVertexArray(solidMesh.va);
  gl.uniformMatrix4fv(solidProg.u.viewProj, false, vp); gl.uniform3fv(solidProg.u.eye, eye); gl.uniform3fv(solidProg.u.fog, FOG);
  gl.bindBuffer(gl.ARRAY_BUFFER, solidMesh.buf); gl.bufferData(gl.ARRAY_BUFFER, new Float32Array(solid), gl.DYNAMIC_DRAW);
  gl.drawArrays(gl.TRIANGLES, 0, solid.length / 9);

  // The actors, then the Guardian's glow on top.
  gl.useProgram(modelProg.p); gl.bindVertexArray(modelMesh.va);
  gl.uniformMatrix4fv(modelProg.u.viewProj, false, vp); gl.uniform3fv(modelProg.u.eye, eye); gl.uniform3fv(modelProg.u.fog, FOG);
  gl.uniform1i(modelProg.u.tex, 0); gl.activeTexture(gl.TEXTURE0);
  for (const actor of actors) {
    const data = meshData(actor.model, poseBones(actor.model, actor.clip, t / 20, ENTITY));
    gl.bindBuffer(gl.ARRAY_BUFFER, modelMesh.buf); gl.bufferData(gl.ARRAY_BUFFER, data, gl.DYNAMIC_DRAW);
    gl.bindTexture(gl.TEXTURE_2D, actor.tex); gl.uniform1i(modelProg.u.glow, 0);
    gl.drawArrays(gl.TRIANGLES, 0, data.length / 8);
    if (actor.glow) {
      gl.bindTexture(gl.TEXTURE_2D, actor.glow); gl.uniform1i(modelProg.u.glow, 1);
      gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE); gl.depthMask(false);
      gl.drawArrays(gl.TRIANGLES, 0, data.length / 8);
      gl.depthMask(true); gl.disable(gl.BLEND);
    }
  }
  drawHud(t, cam, w, h);
}

/** Letterbox, the fade from black, the landing's white-out and the title card, as the client draws them. */
function drawHud(t, cam, w, h) {
  const b = track.beats;
  ctx.clearRect(0, 0, w, h);
  const back = smooth((t - b.ret) / (b.length - b.ret));
  if (state.mode === 'scene') {
    if (cam.black > 0) { ctx.fillStyle = `rgba(0,0,0,${cam.black})`; ctx.fillRect(0, 0, w, h); }
    if (cam.flash > 0) { ctx.fillStyle = `rgba(255,255,255,${Math.min(1, cam.flash)})`; ctx.fillRect(0, 0, w, h); }
    if (state.bars) { const bar = Math.round(h * .11 * (1 - back)); ctx.fillStyle = '#000'; ctx.fillRect(0, 0, w, bar); ctx.fillRect(0, h - bar, w, bar); }
    const fade = 10, enter = smooth((t - b.title) / fade), title = enter * (1 - smooth((t - (b.title_end - fade)) / fade)) * (1 - back);
    if (title > 0) {
      const size = Math.round(h * .062), y = h * .7 + h * .03 * (1 - enter);
      ctx.font = `600 ${size}px system-ui, sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      const text = 'FRACTURED GUARDIAN', tw = ctx.measureText(text).width;
      ctx.fillStyle = `rgba(0,0,0,${.9 * title})`; ctx.fillRect(w / 2 - tw / 2 - size * .6, y - size * .85, tw + size * 1.2, size * 1.9);
      ctx.fillStyle = `rgba(228,236,236,${title})`; ctx.fillText(text, w / 2, y);
      const line = tw / 2 * smooth((t - b.title) / 14); ctx.fillStyle = `rgba(106,242,255,${.8 * title})`; ctx.fillRect(w / 2 - line, y + size * .72, line * 2, Math.max(1, h * .002));
    }
    if (t > 10 && back <= 0) {
      ctx.font = `${Math.round(h * .022)}px system-ui, sans-serif`; ctx.textAlign = 'right'; ctx.textBaseline = 'alphabetic';
      ctx.fillStyle = 'rgba(255,255,255,.45)'; ctx.fillText('Hold Shift to skip', w - h * .03, h - h * .11 - h * .02);
    }
  }
  const shot = ['wanderer', 'top-down', "zombie's eyes", 'smash', 'up close', "players' line"][[0, ...track.cuts].filter(c => c <= t).length - 1];
  ctx.font = `${Math.round(h * .02)}px system-ui, sans-serif`; ctx.textAlign = 'left'; ctx.textBaseline = 'top'; ctx.fillStyle = 'rgba(106,242,255,.85)';
  ctx.fillText(`${state.mode === 'scene' ? 'scene camera' : 'orbit'} · shot: ${shot} · fov ${cam.fov.toFixed(0)}`, h * .02, h * .015);
}

// ---------- Controls ----------
function syncPlay() { $('#play').textContent = state.playing ? '❚❚' : '▶'; $('#play').setAttribute('aria-label', state.playing ? 'Pause' : 'Play'); }
function setMode(mode) { state.mode = mode; $('#cam-scene').classList.toggle('active', mode === 'scene'); $('#cam-orbit').classList.toggle('active', mode === 'orbit'); }
$('#play').onclick = () => { if (state.tick >= track.beats.length) state.tick = 0; state.playing = !state.playing; syncPlay(); };
$('#timeline').oninput = e => { state.tick = +e.target.value; state.playing = false; syncPlay(); };
$('#speed').onchange = e => state.speed = +e.target.value;
$('#cam-scene').onclick = () => setMode('scene');
$('#cam-orbit').onclick = () => setMode('orbit');
$('#bars').onclick = () => { state.bars = !state.bars; $('#bars').classList.toggle('active', state.bars); };
addEventListener('keydown', e => {
  if (e.target.tagName === 'SELECT') return;
  if (e.code === 'Space') { e.preventDefault(); $('#play').click(); }
  if (e.code === 'ArrowRight' || e.code === 'ArrowLeft') { state.playing = false; syncPlay(); state.tick = Math.max(0, Math.min(track.beats.length, Math.round(state.tick) + (e.code === 'ArrowRight' ? 1 : -1))); }
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
canvas.parentElement.addEventListener('wheel', e => { if (state.mode !== 'orbit') return; e.preventDefault(); state.orbit.dist = Math.max(3, Math.min(60, state.orbit.dist * Math.exp(e.deltaY * .001))); }, {passive: false});

let last = 0;
function frame(now) {
  if (track) {
    if (state.playing && last) { state.tick += (now - last) / 50 * state.speed; if (state.tick >= track.beats.length) { state.tick = track.beats.length; state.playing = false; syncPlay(); } }
    $('#timeline').value = state.tick;
    $('#clock').textContent = `tick ${state.tick.toFixed(1)} · ${(state.tick / 20).toFixed(2)} s`;
    draw();
  }
  last = now; requestAnimationFrame(frame);
}
const params = new URLSearchParams(location.search);
load().then(() => {
  if (params.has('t')) { state.tick = +params.get('t'); state.playing = false; syncPlay(); }
  if (params.get('cam') === 'orbit') setMode('orbit');
  requestAnimationFrame(frame);
}).catch(e => { $('#error').textContent = e.stack || String(e); });
