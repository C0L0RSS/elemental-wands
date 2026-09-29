// Shattered Nave layout preview: instanced WebGL2 voxels from layouts.json + atlas.png, plus the
// planned light shafts. It shows the authored block layout; Minecraft owns the real lighting and fog.
'use strict';

const $ = s => document.querySelector(s);
const canvas = $('#view');
const gl = canvas.getContext('webgl2', {antialias: true});
if (!gl) document.body.innerHTML = '<p style="padding:24px">This preview needs WebGL2.</p>';

// ---------- math ----------
const M = {
  perspective(fov, aspect, near, far) { const f = 1 / Math.tan(fov / 2), m = new Float32Array(16); m[0] = f / aspect; m[5] = f; m[10] = (far + near) / (near - far); m[11] = -1; m[14] = 2 * far * near / (near - far); return m; },
  lookAt(e, f) { // eye, unit forward
    const s = norm(cross(f, [0, 1, 0])), u = cross(s, f);
    return new Float32Array([s[0], u[0], -f[0], 0, s[1], u[1], -f[1], 0, s[2], u[2], -f[2], 0,
      -dot(s, e), -dot(u, e), dot(f, e), 1]);
  },
  mul(a, b) { const o = new Float32Array(16); for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) { let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k]; o[c * 4 + r] = s; } return o; },
  invert(m) {
    const a = m, inv = new Float32Array(16);
    inv[0] = a[5]*a[10]*a[15]-a[5]*a[11]*a[14]-a[9]*a[6]*a[15]+a[9]*a[7]*a[14]+a[13]*a[6]*a[11]-a[13]*a[7]*a[10];
    inv[4] = -a[4]*a[10]*a[15]+a[4]*a[11]*a[14]+a[8]*a[6]*a[15]-a[8]*a[7]*a[14]-a[12]*a[6]*a[11]+a[12]*a[7]*a[10];
    inv[8] = a[4]*a[9]*a[15]-a[4]*a[11]*a[13]-a[8]*a[5]*a[15]+a[8]*a[7]*a[13]+a[12]*a[5]*a[11]-a[12]*a[7]*a[9];
    inv[12] = -a[4]*a[9]*a[14]+a[4]*a[10]*a[13]+a[8]*a[5]*a[14]-a[8]*a[6]*a[13]-a[12]*a[5]*a[10]+a[12]*a[6]*a[9];
    inv[1] = -a[1]*a[10]*a[15]+a[1]*a[11]*a[14]+a[9]*a[2]*a[15]-a[9]*a[3]*a[14]-a[13]*a[2]*a[11]+a[13]*a[3]*a[10];
    inv[5] = a[0]*a[10]*a[15]-a[0]*a[11]*a[14]-a[8]*a[2]*a[15]+a[8]*a[3]*a[14]+a[12]*a[2]*a[11]-a[12]*a[3]*a[10];
    inv[9] = -a[0]*a[9]*a[15]+a[0]*a[11]*a[13]+a[8]*a[1]*a[15]-a[8]*a[3]*a[13]-a[12]*a[1]*a[11]+a[12]*a[3]*a[9];
    inv[13] = a[0]*a[9]*a[14]-a[0]*a[10]*a[13]-a[8]*a[1]*a[14]+a[8]*a[2]*a[13]+a[12]*a[1]*a[10]-a[12]*a[2]*a[9];
    inv[2] = a[1]*a[6]*a[15]-a[1]*a[7]*a[14]-a[5]*a[2]*a[15]+a[5]*a[3]*a[14]+a[13]*a[2]*a[7]-a[13]*a[3]*a[6];
    inv[6] = -a[0]*a[6]*a[15]+a[0]*a[7]*a[14]+a[4]*a[2]*a[15]-a[4]*a[3]*a[14]-a[12]*a[2]*a[7]+a[12]*a[3]*a[6];
    inv[10] = a[0]*a[5]*a[15]-a[0]*a[7]*a[13]-a[4]*a[1]*a[15]+a[4]*a[3]*a[13]+a[12]*a[1]*a[7]-a[12]*a[3]*a[5];
    inv[14] = -a[0]*a[5]*a[14]+a[0]*a[6]*a[13]+a[4]*a[1]*a[14]-a[4]*a[2]*a[13]-a[12]*a[1]*a[6]+a[12]*a[2]*a[5];
    inv[3] = -a[1]*a[6]*a[11]+a[1]*a[7]*a[10]+a[5]*a[2]*a[11]-a[5]*a[3]*a[10]-a[9]*a[2]*a[7]+a[9]*a[3]*a[6];
    inv[7] = a[0]*a[6]*a[11]-a[0]*a[7]*a[10]-a[4]*a[2]*a[11]+a[4]*a[3]*a[10]+a[8]*a[2]*a[7]-a[8]*a[3]*a[6];
    inv[11] = -a[0]*a[5]*a[11]+a[0]*a[7]*a[9]+a[4]*a[1]*a[11]-a[4]*a[3]*a[9]-a[8]*a[1]*a[7]+a[8]*a[3]*a[5];
    inv[15] = a[0]*a[5]*a[10]-a[0]*a[6]*a[9]-a[4]*a[1]*a[10]+a[4]*a[2]*a[9]+a[8]*a[1]*a[6]-a[8]*a[2]*a[5];
    const det = a[0]*inv[0]+a[1]*inv[4]+a[2]*inv[8]+a[3]*inv[12];
    for (let i = 0; i < 16; i++) inv[i] /= det;
    return inv;
  },
};
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };

// ---------- shaders ----------
const VOXEL_VS = `#version 300 es
precision highp float; precision highp int;
layout(location=0) in ivec4 inst;
uniform mat4 viewProj;
uniform ivec4 mats[96];
uniform int passKind; uniform float cutY;
out vec2 vUv; out vec3 vWorld; out float vShade; flat out int vTex; flat out int vMat;
const vec3 C[24] = vec3[24](
  vec3(1,0,1),vec3(1,1,1),vec3(1,1,0),vec3(1,0,0),  vec3(0,0,0),vec3(0,1,0),vec3(0,1,1),vec3(0,0,1),
  vec3(0,1,1),vec3(0,1,0),vec3(1,1,0),vec3(1,1,1),  vec3(0,0,0),vec3(0,0,1),vec3(1,0,1),vec3(1,0,0),
  vec3(0,0,1),vec3(0,1,1),vec3(1,1,1),vec3(1,0,1),  vec3(1,0,0),vec3(1,1,0),vec3(0,1,0),vec3(0,0,0));
const int K[6] = int[6](0,1,2,0,2,3);
const float SHADE[6] = float[6](.6,.6,1.,.5,.8,.8);
void collapse() { gl_Position = vec4(2,2,2,1); }
void main() {
  int face = gl_VertexID / 6, corner = K[gl_VertexID % 6];
  int mat = inst.w & 255, mask = inst.w >> 8;
  ivec4 m = mats[mat];
  vMat = mat;
  if ((passKind == 1) != (m.x == 5)) { collapse(); return; }
  if ((mask & (1 << face)) == 0 || float(inst.y) > cutY) { collapse(); return; }
  vec3 p = C[face * 4 + corner];
  vec2 uv;
  if (face < 2) uv = vec2(face == 0 ? 1. - p.z : p.z, p.y);
  else if (face < 4) uv = vec2(p.x, face == 2 ? 1. - p.z : p.z);
  else uv = vec2(face == 4 ? p.x : 1. - p.x, p.y);
  vTex = face == 2 ? m.y : face == 3 ? m.w : m.z;
  vUv = vec2(uv.x, 1. - uv.y);
  vShade = SHADE[face];
  vWorld = vec3(inst.xyz) + p;
  gl_Position = viewProj * vec4(vWorld, 1);
}`;
const VOXEL_FS = `#version 300 es
precision highp float; precision highp int;
in vec2 vUv; in vec3 vWorld; in float vShade; flat in int vTex; flat in int vMat;
uniform sampler2D atlas; uniform vec2 atlasSize;
uniform vec3 eye; uniform vec3 fogColor; uniform float fogEnd; uniform float ambient;
uniform vec4 lightPos[64]; uniform vec3 lightCol[64]; uniform int lightCount;
uniform int emissive[96];
uniform int passKind;
out vec4 color;
void main() {
  if (passKind == 1) { color = vec4(1., .25, .3, .10); return; }
  vec2 tile = vec2(vTex % int(atlasSize.x), vTex / int(atlasSize.x));
  vec4 t = texture(atlas, (tile + clamp(vUv, .002, .998)) / atlasSize);
  if (t.a < .5) discard;
  vec3 light = vec3(ambient);
  for (int i = 0; i < 64; i++) {
    if (i >= lightCount) break;
    vec3 d = abs(vWorld - lightPos[i].xyz);
    float reach = lightPos[i].w - (d.x + d.y + d.z) * .9;
    if (reach > 0.) light += lightCol[i] * pow(reach / 15., 1.4) * 1.3;
  }
  vec3 c = t.rgb * vShade * (emissive[vMat] == 1 ? vec3(1.2) : min(light, vec3(1.25)));
  float dist = length(vWorld - eye);
  float f = smoothstep(fogEnd * .1, fogEnd, dist);
  color = vec4(mix(c, fogColor, f), 1);
}`;
const SKY_VS = `#version 300 es
out vec2 ndc; void main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2) * 2. - 1.; ndc = p; gl_Position = vec4(p, .9999, 1); }`;
const SKY_FS = `#version 300 es
precision highp float;
in vec2 ndc; uniform mat4 invViewProj; uniform vec3 fogColor; uniform vec3 zenith; out vec4 color;
void main() {
  vec4 a = invViewProj * vec4(ndc, -1, 1), b = invViewProj * vec4(ndc, 1, 1);
  vec3 dir = normalize(b.xyz / b.w - a.xyz / a.w);
  color = vec4(mix(fogColor, zenith, smoothstep(.0, .6, dir.y)), 1);
}`;
// A light shaft is a soft ribbon turned to face the camera around its own axis.
const SHAFT_VS = `#version 300 es
precision highp float;
uniform mat4 viewProj; uniform vec3 eye; uniform vec3 top; uniform vec3 bottom; uniform float width;
out vec2 vUv; out float vDist;
const int K[6] = int[6](0,1,2,0,2,3);
void main() {
  int k = K[gl_VertexID];
  float along = (k == 2 || k == 3) ? 1. : 0.;
  float side = (k == 0 || k == 3) ? -1. : 1.;
  vec3 p = mix(top, bottom, along);
  vec3 s = normalize(cross(bottom - top, eye - p));
  vec3 w = p + s * side * width * .5;
  vUv = vec2(side, along); vDist = length(w - eye);
  gl_Position = viewProj * vec4(w, 1);
}`;
const SHAFT_FS = `#version 300 es
precision highp float;
in vec2 vUv; in float vDist; uniform vec3 tint; uniform float strength; uniform float fogEnd; out vec4 color;
void main() {
  float edge = 1. - abs(vUv.x); edge *= edge;
  float fade = smoothstep(0., .55, vUv.y) * (1. - .35 * smoothstep(.9, 1., vUv.y));
  float fog = 1. - smoothstep(fogEnd * .35, fogEnd * 1.05, vDist);
  float a = edge * fade * fog * strength;
  color = vec4(tint * a, a);
}`;
const BOX_VS = `#version 300 es
precision highp float;
uniform mat4 viewProj; uniform vec3 lo; uniform vec3 hi; out vec3 vN;
const vec3 C[8] = vec3[8](vec3(0,0,0),vec3(1,0,0),vec3(1,1,0),vec3(0,1,0),vec3(0,0,1),vec3(1,0,1),vec3(1,1,1),vec3(0,1,1));
const int I[36] = int[36](0,1,2,0,2,3, 5,4,7,5,7,6, 4,0,3,4,3,7, 1,5,6,1,6,2, 3,2,6,3,6,7, 4,5,1,4,1,0);
void main() { vec3 p = C[I[gl_VertexID]]; vN = vec3(gl_VertexID / 6); gl_Position = viewProj * vec4(lo + p * (hi - lo), 1); }`;
const BOX_FS = `#version 300 es
precision highp float; in vec3 vN; uniform vec4 tint; out vec4 color;
void main() { float s = .7 + .06 * vN.x; color = vec4(tint.rgb * s, tint.a); }`;

function program(vs, fs) {
  const p = gl.createProgram();
  for (const [type, src] of [[gl.VERTEX_SHADER, vs], [gl.FRAGMENT_SHADER, fs]]) {
    const s = gl.createShader(type); gl.shaderSource(s, src); gl.compileShader(s);
    if (!gl.getShaderParameter(s, gl.COMPILE_STATUS)) throw Error(gl.getShaderInfoLog(s));
    gl.attachShader(p, s);
  }
  gl.linkProgram(p);
  if (!gl.getProgramParameter(p, gl.LINK_STATUS)) throw Error(gl.getProgramInfoLog(p));
  const u = new Proxy({}, {get: (c, k) => c[k] ?? (c[k] = gl.getUniformLocation(p, k))});
  return {p, u};
}

// ---------- state ----------
const MOODS = {
  target: {label: 'In-game target', fog: 160, bright: 34, fogColor: [.118, .135, .158], zenith: [.028, .032, .042]},
  lighter: {label: 'Lighter haze', fog: 260, bright: 50, fogColor: [.17, .19, .22], zenith: [.05, .056, .07]},
  inspect: {label: 'Daylight', fog: 1400, bright: 100, fogColor: [.62, .66, .72], zenith: [.36, .46, .6]},
};
const cam = {mode: 'orbit', target: [0, 20, 0], yaw: 0.6, pitch: .35, dist: 260, eye: [0, 1.62, 30], lookYaw: Math.PI, lookPitch: 0};
let data, voxel, sky, shaft, box, atlasTex, nave, mood = 'target';
const keys = new Set();

function eyeAndForward() {
  if (cam.mode === 'orbit') {
    const f = [-Math.cos(cam.pitch) * Math.sin(cam.yaw), -Math.sin(cam.pitch), -Math.cos(cam.pitch) * Math.cos(cam.yaw)];
    return [[cam.target[0] - f[0] * cam.dist, cam.target[1] - f[1] * cam.dist, cam.target[2] - f[2] * cam.dist], f];
  }
  return [cam.eye, [Math.cos(cam.lookPitch) * Math.sin(cam.lookYaw), Math.sin(cam.lookPitch), Math.cos(cam.lookPitch) * Math.cos(cam.lookYaw)]];
}

const VIEWS = [
  ['Arrival', () => walk([.5, 1.62, 28.5], Math.PI, .05)],
  ['Beside the effigy', () => walk([7.5, 1.62, 8.5], Math.atan2(.6, -.8), .1)],
  ['Floor edge', () => walk([.5, 1.62, -60.5], Math.PI, .12)],
  ['Corner', () => walk([-58.5, 1.62, 58.5], Math.atan2(-1, 1), .1)],
  ['Look up', () => walk([12.5, 1.62, 24.5], Math.PI * .95, .95)],
  ['Among the pillars', () => walk([110.5, 1.62, 66.5], Math.atan2(.35, -1), .06)],
  ['Orbit', () => orbit([0, 0, 0], .6, .55, 300)],
  ['Overhead', () => orbit([0, 0, 0], 0, 1.52, 480)],
];
function walk(eye, yaw, pitch) { Object.assign(cam, {mode: 'walk', eye: [...eye], lookYaw: yaw, lookPitch: pitch}); $('#cut').checked = false; if (mood === 'inspect') pickMood('target'); hint(); draw(); }
function orbit(target, yaw, pitch, dist, cut = true) { Object.assign(cam, {mode: 'orbit', target: [...target], yaw, pitch, dist}); $('#cut').checked = cut; pickMood('inspect'); hint(); draw(); }
function hint() {
  $('#hint').textContent = cam.mode === 'walk'
    ? 'Walk: drag to look · WASD move · Space/Shift up/down · hold Ctrl or R to go fast · O for orbit'
    : 'Orbit: drag to rotate · right-drag or Shift-drag to pan · scroll to zoom · V to walk from here';
}

// ---------- setup ----------
async function load() {
  data = await (await fetch('layouts.json?v=' + Date.now())).json();
  const img = new Image(); img.src = 'atlas.png?v=' + Date.now(); await img.decode();
  voxel = program(VOXEL_VS, VOXEL_FS); sky = program(SKY_VS, SKY_FS); shaft = program(SHAFT_VS, SHAFT_FS); box = program(BOX_VS, BOX_FS);
  atlasTex = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, atlasTex);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  const lay = data.layouts.nave;
  const bytes = Uint8Array.from(atob(lay.instances), c => c.charCodeAt(0));
  const vao = gl.createVertexArray(); gl.bindVertexArray(vao);
  const buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buf); gl.bufferData(gl.ARRAY_BUFFER, bytes, gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribIPointer(0, 4, gl.SHORT, 8, 0); gl.vertexAttribDivisor(0, 1);
  gl.bindVertexArray(null);
  const blockLights = lay.lights.map(([x, y, z, m]) => ({p: [x + .5, y + .5, z + .5], c: data.materials[m].light}));
  // Where a shaft lands, an invisible light block lifts the floor; the tint is the shaft's.
  const pools = data.shafts.map(s => ({p: [s.bottom[0], 1.5, s.bottom[2]], c: [...s.colour.map(v => v * .8), 11], shaft: true}));
  nave = {vao, count: lay.count, blocks: lay.blocks, blockLights, pools};
  const mats = new Int32Array(96 * 4), emissive = new Int32Array(96);
  data.materials.forEach((m, i) => { mats.set([m.shape, ...m.tex], i * 4); emissive[i] = m.light ? 1 : 0; });
  gl.useProgram(voxel.p); gl.uniform4iv(voxel.u.mats, mats); gl.uniform1iv(voxel.u.emissive, emissive);
  gl.uniform2f(voxel.u.atlasSize, data.atlasCols, data.atlasRows);
  buildUi(); VIEWS[0][1]();
}

function buildUi() {
  const views = $('#views');
  for (const [label, go] of VIEWS) { const b = document.createElement('button'); b.textContent = label; b.onclick = go; views.append(b); }
  const moods = $('#moods');
  for (const [key, m] of Object.entries(MOODS)) {
    const b = document.createElement('button'); b.textContent = m.label; b.dataset.key = key;
    b.onclick = () => { setMood(key); moods.querySelectorAll('button').forEach(x => x.classList.toggle('on', x === b)); };
    moods.append(b);
  }
  for (const id of ['fog', 'bright']) $('#' + id).oninput = () => { labels(); draw(); };
  $('#barrier').onchange = $('#player').onchange = $('#shafts').onchange = $('#cut').onchange = draw;
  $('#toggle').onclick = () => { $('#panel').classList.toggle('collapsed'); $('#toggle').textContent = $('#panel').classList.contains('collapsed') ? 'Show' : 'Hide'; };
  setMood(mood);
  moods.querySelector(`[data-key=${mood}]`).classList.add('on');
  const n = data.nave;
  $('#facts').innerHTML = `<b>${nave.blocks.toLocaleString()}</b> blocks. Fight floor <b>${n.half * 2} × ${n.half * 2}</b>, flat, `
    + `invisible wall and lid <b>${n.ceiling}</b> up. Pillars every <b>${n.grid}</b> blocks, about 20 thick, capitals <b>${n.spring}</b> up; `
    + `the crossing is framed at ±${n.ring} and its ribs meet in a broken ring <b>${n.domeTop}</b> up.<br>`
    + `In game the pillar grid and floor pattern would repeat without end; this preview stops at ±${n.reach}.<br>`
    + `Cyan box: Guardian hitbox (3.2 × 5.2) kneeling on the effigy seat. Gold box: a player beside the arrival point.`;
}
function labels() { $('#fogv').textContent = $('#fog').value; $('#brightv').textContent = $('#bright').value + '%'; }
function pickMood(k) { setMood(k); document.querySelectorAll('#moods button').forEach(b => b.classList.toggle('on', b.dataset.key === k)); }
function setMood(k) { mood = k; $('#fog').value = MOODS[k].fog; $('#bright').value = MOODS[k].bright; labels(); draw(); }

// ---------- render ----------
let pending = false;
function draw() { if (!pending) { pending = true; requestAnimationFrame(frame); } }
let lastT = 0;
function frame(t) {
  pending = false;
  if (!data) return;
  const dt = Math.min(.1, (t - (lastT || t)) / 1000); lastT = t;
  if (cam.mode === 'walk' && keys.size) {
    const fast = keys.has('ControlLeft') || keys.has('KeyR') ? 5 : 1, speed = 9 * fast * dt;
    const f = [Math.sin(cam.lookYaw), 0, Math.cos(cam.lookYaw)], s = [-f[2], 0, f[0]];
    const mv = (v, k) => { for (let i = 0; i < 3; i++) cam.eye[i] += v[i] * k * speed; };
    if (keys.has('KeyW')) mv(f, 1); if (keys.has('KeyS')) mv(f, -1);
    if (keys.has('KeyD')) mv(s, -1); if (keys.has('KeyA')) mv(s, 1);
    if (keys.has('Space')) cam.eye[1] += speed; if (keys.has('ShiftLeft')) cam.eye[1] = Math.max(.3, cam.eye[1] - speed);
    draw();
  } else lastT = 0;
  const dpr = Math.min(devicePixelRatio, 2), w = canvas.clientWidth * dpr | 0, h = canvas.clientHeight * dpr | 0;
  if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
  gl.viewport(0, 0, w, h);
  const m = MOODS[mood], fogEnd = +$('#fog').value, ambient = +$('#bright').value / 100;
  const [eye, fwd] = eyeAndForward();
  const viewProj = M.mul(M.perspective(1.2, w / h, .1, 1600), M.lookAt(eye, fwd));
  gl.disable(gl.DEPTH_TEST); gl.disable(gl.BLEND);
  gl.useProgram(sky.p);
  gl.uniformMatrix4fv(sky.u.invViewProj, false, M.invert(viewProj));
  gl.uniform3fv(sky.u.fogColor, m.fogColor); gl.uniform3fv(sky.u.zenith, m.zenith);
  gl.drawArrays(gl.TRIANGLES, 0, 3);
  gl.enable(gl.DEPTH_TEST); gl.clear(gl.DEPTH_BUFFER_BIT);
  const showShafts = $('#shafts').checked;
  gl.useProgram(voxel.p);
  gl.uniformMatrix4fv(voxel.u.viewProj, false, viewProj);
  gl.uniform3fv(voxel.u.eye, eye); gl.uniform3fv(voxel.u.fogColor, m.fogColor);
  gl.uniform1f(voxel.u.fogEnd, fogEnd); gl.uniform1f(voxel.u.ambient, ambient);
  gl.uniform1f(voxel.u.cutY, $('#cut').checked ? data.nave.spring - 30 : 1e9);
  // Nearest emitters only; the shader has a fixed budget.
  const all = showShafts ? nave.blockLights.concat(nave.pools) : nave.blockLights;
  const lights = all.slice().sort((a, b) => dist2(a.p, eye) - dist2(b.p, eye)).slice(0, 64);
  const lp = new Float32Array(256), lc = new Float32Array(192);
  lights.forEach((l, i) => { lp.set([...l.p, l.c[3]], i * 4); lc.set(l.c.slice(0, 3), i * 3); });
  gl.uniform4fv(voxel.u.lightPos, lp); gl.uniform3fv(voxel.u.lightCol, lc); gl.uniform1i(voxel.u.lightCount, lights.length);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, atlasTex); gl.uniform1i(voxel.u.atlas, 0);
  gl.bindVertexArray(nave.vao);
  gl.uniform1i(voxel.u.passKind, 0);
  gl.drawArraysInstanced(gl.TRIANGLES, 0, 36, nave.count);
  gl.enable(gl.BLEND); gl.depthMask(false);
  if (showShafts) {
    gl.bindVertexArray(null);
    gl.blendFunc(gl.ONE, gl.ONE);
    gl.useProgram(shaft.p);
    gl.uniformMatrix4fv(shaft.u.viewProj, false, viewProj); gl.uniform3fv(shaft.u.eye, eye); gl.uniform1f(shaft.u.fogEnd, fogEnd);
    for (const s of data.shafts) {
      gl.uniform3fv(shaft.u.top, s.top); gl.uniform3fv(shaft.u.bottom, s.bottom); gl.uniform3fv(shaft.u.tint, s.colour);
      // A wide faint veil around a brighter core.
      for (const [wide, strength] of [[s.width * 2.6, .045], [s.width * 1.4, .07], [s.width * .6, .08]]) {
        gl.uniform1f(shaft.u.width, wide); gl.uniform1f(shaft.u.strength, strength);
        gl.drawArrays(gl.TRIANGLES, 0, 6);
      }
    }
    gl.useProgram(voxel.p); gl.bindVertexArray(nave.vao);
  }
  gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
  if ($('#barrier').checked) { gl.uniform1i(voxel.u.passKind, 1); gl.drawArraysInstanced(gl.TRIANGLES, 0, 36, nave.count); }
  gl.bindVertexArray(null);
  if ($('#player').checked) {
    gl.useProgram(box.p); gl.uniformMatrix4fv(box.u.viewProj, false, viewProj);
    const [ax, ay, az] = data.nave.arrival, [ex, ey, ez] = data.nave.effigy;
    const boxes = [[[ex + .5 - 1.6, ey, ez + .5 - 1.6], [ex + .5 + 1.6, ey + 5.2, ez + .5 + 1.6], [.45, .9, 1, .35]],
                   [[ax + 2.7, ay, az - 2.8], [ax + 3.3, ay + 1.8, az - 2.2], [.95, .8, .4, .9]]];
    for (const [lo, hi, tint] of boxes) {
      gl.uniform3fv(box.u.lo, lo); gl.uniform3fv(box.u.hi, hi); gl.uniform4fv(box.u.tint, tint);
      gl.drawArrays(gl.TRIANGLES, 0, 36);
    }
  }
  gl.depthMask(true); gl.disable(gl.BLEND);
}
const dist2 = (a, b) => (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2;

// ---------- input ----------
let drag = null;
canvas.addEventListener('pointerdown', e => { drag = {x: e.clientX, y: e.clientY, pan: e.button === 2 || e.shiftKey}; canvas.setPointerCapture(e.pointerId); });
canvas.addEventListener('pointerup', () => drag = null);
canvas.addEventListener('pointercancel', () => drag = null);
canvas.addEventListener('contextmenu', e => e.preventDefault());
canvas.addEventListener('pointermove', e => {
  if (!drag) return;
  const dx = e.clientX - drag.x, dy = e.clientY - drag.y; drag.x = e.clientX; drag.y = e.clientY;
  if (cam.mode === 'walk') {
    cam.lookYaw -= dx * .004; cam.lookPitch = Math.max(-1.5, Math.min(1.5, cam.lookPitch - dy * .004));
  } else if (drag.pan) {
    const k = cam.dist * .0016, c = Math.cos(cam.yaw), s = Math.sin(cam.yaw);
    cam.target[0] -= dx * c * k; cam.target[2] += dx * s * k;
    cam.target[1] += dy * k;
  } else {
    cam.yaw -= dx * .005; cam.pitch = Math.max(-.2, Math.min(1.55, cam.pitch + dy * .005));
  }
  draw();
});
canvas.addEventListener('wheel', e => {
  e.preventDefault();
  if (cam.mode === 'orbit') cam.dist = Math.max(4, Math.min(900, cam.dist * Math.exp(e.deltaY * .001)));
  else { const f = eyeAndForward()[1]; for (let i = 0; i < 3; i++) cam.eye[i] -= f[i] * e.deltaY * .02; }
  draw();
}, {passive: false});
addEventListener('keydown', e => {
  if (e.target.tagName === 'INPUT') return;
  if (e.code === 'KeyV' && cam.mode === 'orbit') { const [eye, f] = eyeAndForward(); walk(eye, Math.atan2(f[0], f[2]), Math.asin(f[1])); return; }
  if (e.code === 'KeyO' && cam.mode === 'walk') { const f = eyeAndForward()[1]; orbit(cam.eye.map((v, i) => v + f[i] * 30), Math.atan2(-f[0], -f[2]), -Math.asin(f[1]), 30); return; }
  keys.add(e.code); if (e.code === 'Space') e.preventDefault(); draw();
});
addEventListener('keyup', e => keys.delete(e.code));
addEventListener('blur', () => keys.clear());
addEventListener('resize', draw);
load().catch(err => { document.body.insertAdjacentHTML('beforeend', `<pre style="position:fixed;bottom:40px;left:12px;color:#f88">${err.stack || err}</pre>`); });
