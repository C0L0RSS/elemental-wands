const BIOME = new URLSearchParams(location.search).get('biome') === 'stony' ? 'stony' : 'snowy';
// Storm Aerie layout preview: instanced WebGL2 voxels and small models from layouts.json +
// atlas.png, the Storm Summit preview's renderer without the Roc and the sea of clouds. Gold
// marks the stand point, green the reward chest cells. It shows the authored template only.
'use strict';

const $ = s => document.querySelector(s);
const canvas = $('#view');
const gl = canvas.getContext('webgl2', {antialias: true});
if (!gl) document.body.innerHTML = '<p style="padding:24px">This preview needs WebGL2.</p>';

// ---------- math ----------
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
const M = {
  id: () => new Float32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]),
  perspective(fov, aspect, near, far) { const f = 1 / Math.tan(fov / 2), m = new Float32Array(16); m[0] = f / aspect; m[5] = f; m[10] = (far + near) / (near - far); m[11] = -1; m[14] = 2 * far * near / (near - far); return m; },
  lookAt(e, f) {
    const s = norm(cross(f, [0, 1, 0])), u = cross(s, f);
    return new Float32Array([s[0], u[0], -f[0], 0, s[1], u[1], -f[1], 0, s[2], u[2], -f[2], 0, -dot(s, e), -dot(u, e), dot(f, e), 1]);
  },
  mul(a, b) { const o = new Float32Array(16); for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) { let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k]; o[c * 4 + r] = s; } return o; },
  // Inverse of a perspective-times-view matrix, enough for the sky's view rays.
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

// A clear, cold mountain sky.
const SUN = norm([-.35, .82, .45]);
const HORIZON = [.78, .87, .96], ZENITH = [.2, .45, .86];

// ---------- shaders ----------
const VOXEL_VS = `#version 300 es
precision highp float; precision highp int;
layout(location=0) in ivec4 inst;
uniform mat4 viewProj;
uniform ivec4 mats[96];
out vec2 vUv; out float vShade; flat out int vTex;
const vec3 C[24] = vec3[24](
  vec3(1,0,1),vec3(1,1,1),vec3(1,1,0),vec3(1,0,0),  vec3(0,0,0),vec3(0,1,0),vec3(0,1,1),vec3(0,0,1),
  vec3(0,1,1),vec3(0,1,0),vec3(1,1,0),vec3(1,1,1),  vec3(0,0,0),vec3(0,0,1),vec3(1,0,1),vec3(1,0,0),
  vec3(0,0,1),vec3(0,1,1),vec3(1,1,1),vec3(1,0,1),  vec3(1,0,0),vec3(1,1,0),vec3(0,1,0),vec3(0,0,0));
const int K[6] = int[6](0,1,2,0,2,3);
const float SHADE[6] = float[6](.6,.6,1.,.5,.8,.8);
void main() {
  int face = gl_VertexID / 6, corner = K[gl_VertexID % 6];
  int mat = inst.w & 255, mask = inst.w >> 8;
  ivec4 m = mats[mat];
  if ((mask & (1 << face)) == 0) { gl_Position = vec4(2, 2, 2, 1); return; }
  vec3 p = C[face * 4 + corner];
  if (m.x >= 10) p.y *= float(m.x - 10) / 8.;          // snow layers and bottom slabs
  vec2 uv;
  if (face < 2) uv = vec2(face == 0 ? 1. - p.z : p.z, p.y);
  else if (face < 4) uv = vec2(p.x, face == 2 ? 1. - p.z : p.z);
  else uv = vec2(face == 4 ? p.x : 1. - p.x, p.y);
  if (m.x == 2) p.xz = .5 + (p.xz - .5) * .25;          // chains and the clapper
  vTex = face == 2 ? m.y : face == 3 ? m.w : m.z;
  vUv = vec2(uv.x, 1. - uv.y);
  vShade = SHADE[face];
  gl_Position = viewProj * vec4(vec3(inst.xyz) + p, 1);
}`;
const VOXEL_FS = `#version 300 es
precision highp float; precision highp int;
in vec2 vUv; in float vShade; flat in int vTex;
uniform sampler2D atlas; uniform vec2 atlasSize;
out vec4 color;
void main() {
  vec2 tile = vec2(vTex % int(atlasSize.x), vTex / int(atlasSize.x));
  vec4 t = texture(atlas, (tile + clamp(vUv, .002, .998)) / atlasSize);
  if (t.a < .5) discard;
  color = vec4(t.rgb * vShade, 1);
}`;
const MODEL_VS = `#version 300 es
precision highp float;
layout(location=0) in vec3 pos; layout(location=1) in vec2 uv; layout(location=2) in vec2 shadeTile;
uniform mat4 viewProj;
out vec2 vUv; out float vShade; flat out int vTex;
void main() { vUv = uv / 4096.; vShade = shadeTile.x / 255.; vTex = int(shadeTile.y); gl_Position = viewProj * vec4(pos / 4096., 1); }`;
const SKY_VS = `#version 300 es
out vec2 ndc; void main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2) * 2. - 1.; ndc = p; gl_Position = vec4(p, .9999, 1); }`;
const SKY_FS = `#version 300 es
precision highp float;
in vec2 ndc; uniform mat4 invViewProj; uniform vec3 horizon; uniform vec3 zenith; uniform vec3 sun; out vec4 color;
void main() {
  vec4 a = invViewProj * vec4(ndc, -1, 1), b = invViewProj * vec4(ndc, 1, 1);
  vec3 dir = normalize(b.xyz / b.w - a.xyz / a.w);
  vec3 c = mix(horizon, zenith, pow(smoothstep(-.02, .75, dir.y), .7));
  if (dir.y < 0.) c = mix(horizon, vec3(.42, .45, .5), smoothstep(0., -.35, dir.y));
  float s = dot(dir, sun);
  c += vec3(1., .95, .8) * (smoothstep(.9985, .9992, s) * 1.2 + pow(max(s, 0.), 64.) * .25);
  color = vec4(c, 1);
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
const cam = {mode: 'orbit', target: [.5, 3, .5], yaw: .5, pitch: .4, dist: 48, eye: [.5, 2.62, 5.5], lookYaw: Math.PI, lookPitch: .1};
let data, facts, voxel, model, sky, box, atlasTex, aerie, VIEWS;
const keys = new Set();

function eyeAndForward() {
  if (cam.mode === 'orbit') {
    const f = [-Math.cos(cam.pitch) * Math.sin(cam.yaw), -Math.sin(cam.pitch), -Math.cos(cam.pitch) * Math.cos(cam.yaw)];
    return [[cam.target[0] - f[0] * cam.dist, cam.target[1] - f[1] * cam.dist, cam.target[2] - f[2] * cam.dist], f];
  }
  return [cam.eye, [Math.cos(cam.lookPitch) * Math.sin(cam.lookYaw), Math.sin(cam.lookPitch), Math.cos(cam.lookPitch) * Math.cos(cam.lookYaw)]];
}
const yawTo = (from, to) => Math.atan2(to[0] - from[0], to[2] - from[2]);
const pitchTo = (from, to) => Math.atan2(to[1] - from[1], Math.hypot(to[0] - from[0], to[2] - from[2]));
function lookFrom(eye, at) { walk(eye, yawTo(eye, at), pitchTo(eye, at)); }
function walk(eye, yaw, pitch) { Object.assign(cam, {mode: 'walk', eye: [...eye], lookYaw: yaw, lookPitch: pitch}); hint(); draw(); }
function orbit(target, yaw, pitch, dist) { Object.assign(cam, {mode: 'orbit', target: [...target], yaw, pitch, dist}); hint(); draw(); }
function hint() {
  $('#hint').textContent = cam.mode === 'walk'
    ? 'Walk: drag to look · WASD move · Space/Shift up/down · hold Ctrl or R to go fast · O for orbit'
    : 'Orbit: drag to rotate · right-drag or Shift-drag to pan · scroll to zoom · V to walk from here';
}

function views() {
  const [sx, sy, sz] = facts.stand, gong = [.5, 3, .5], [cx, cy, cz] = facts.chests[0];
  return [
    ['Orbit', () => orbit([.5, 2, .5], .5, .3, 20)],
    ['Arrival', () => lookFrom([sx + .5, sy + 1.62, sz + .5], gong)],
    ['From a nearby ridge', () => lookFrom([.5, 1.5, 24.5], [.5, 2, .5])],
    ['Reward chest', () => lookFrom([sx + 1.5, sy + 1.62, sz + .5], [cx + .5, cy + .4, cz + .5])],
    ['Three quarters', () => orbit([.5, 2.5, .5], .75, .15, 13)],
    ['From far below', () => orbit([.5, -12, .5], .9, .05, 70)],
  ];
}

// ---------- setup ----------
async function load() {
  data = await (await fetch(`layouts-${BIOME}.json?v=` + Date.now())).json();
  facts = data.facts;
  const img = await new Promise((ok, fail) => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => fail(Error('Image failed: atlas.png')); i.src = `atlas-${BIOME}.png?v=` + Date.now(); });
  voxel = program(VOXEL_VS, VOXEL_FS); model = program(MODEL_VS, VOXEL_FS); sky = program(SKY_VS, SKY_FS); box = program(BOX_VS, BOX_FS);
  atlasTex = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, atlasTex);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
  for (const p of [gl.TEXTURE_MIN_FILTER, gl.TEXTURE_MAG_FILTER]) gl.texParameteri(gl.TEXTURE_2D, p, gl.NEAREST);
  for (const p of [gl.TEXTURE_WRAP_S, gl.TEXTURE_WRAP_T]) gl.texParameteri(gl.TEXTURE_2D, p, gl.CLAMP_TO_EDGE);
  const lay = data.aerie;
  const vao = gl.createVertexArray(); gl.bindVertexArray(vao);
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer()); gl.bufferData(gl.ARRAY_BUFFER, Uint8Array.from(atob(lay.instances), c => c.charCodeAt(0)), gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribIPointer(0, 4, gl.SHORT, 8, 0); gl.vertexAttribDivisor(0, 1);
  const mvao = gl.createVertexArray(); gl.bindVertexArray(mvao);
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer()); gl.bufferData(gl.ARRAY_BUFFER, Uint8Array.from(atob(lay.models), c => c.charCodeAt(0)), gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 3, gl.INT, false, 24, 0);
  gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 2, gl.INT, false, 24, 12);
  gl.enableVertexAttribArray(2); gl.vertexAttribPointer(2, 2, gl.UNSIGNED_SHORT, false, 24, 20);
  gl.bindVertexArray(null);
  aerie = {vao, count: lay.count, mvao, mcount: lay.modelVerts};
  const mats = new Int32Array(96 * 4);
  data.materials.forEach((m, i) => mats.set([m.shape, ...m.tex], i * 4));
  gl.useProgram(voxel.p); gl.uniform4iv(voxel.u.mats, mats);
  for (const prog of [voxel, model]) { gl.useProgram(prog.p); gl.uniform2f(prog.u.atlasSize, data.atlasCols, data.atlasRows); }
  VIEWS = views();
  buildUi(); VIEWS[0][1]();
}

function buildUi() {
  for (const [label, go] of VIEWS) { const b = document.createElement('button'); b.textContent = label; b.onclick = go; $('#views').append(b); }
  $('#markers').onchange = draw;
  $('#toggle').onclick = () => { $('#panel').classList.toggle('collapsed'); $('#toggle').textContent = $('#panel').classList.contains('collapsed') ? 'Show' : 'Hide'; };
  const f = facts, list = a => '{' + a.join(', ') + '}';
  $('#facts').innerHTML = `The gong is a custom model <b>${f.gong}</b> blocks across, in a frame <b>${f.frame[0]}</b> wide and <b>${f.frame[1]}</b> tall, `
    + `on a levelled summit about <b>${f.summit[0]} × ${f.summit[1]}</b>.<br>`
    + `It faces south. The stand point (gold) and the reward chest (green) are on the summit; `
    + `the gong is <b>${f.reach}</b> blocks from a standing player's eyes.`;
}

// ---------- render ----------
let pending = false, lastT = 0;
function draw() { if (!pending) { pending = true; requestAnimationFrame(frame); } }
function frame(t) {
  pending = false;
  if (!data) return;
  const dt = Math.min(.1, (t - (lastT || t)) / 1000); lastT = t;
  if (cam.mode === 'walk' && keys.size) {
    const fast = keys.has('ControlLeft') || keys.has('KeyR') ? 5 : 1, speed = 6 * fast * dt;
    const f = [Math.sin(cam.lookYaw), 0, Math.cos(cam.lookYaw)], s = [-f[2], 0, f[0]];
    const mv = (v, k) => { for (let i = 0; i < 3; i++) cam.eye[i] += v[i] * k * speed; };
    if (keys.has('KeyW')) mv(f, 1); if (keys.has('KeyS')) mv(f, -1);
    if (keys.has('KeyD')) mv(s, -1); if (keys.has('KeyA')) mv(s, 1);
    if (keys.has('Space')) cam.eye[1] += speed; if (keys.has('ShiftLeft')) cam.eye[1] -= speed;
  } else lastT = 0;
  const dpr = Math.min(devicePixelRatio, 2), w = canvas.clientWidth * dpr | 0, h = canvas.clientHeight * dpr | 0;
  if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
  gl.viewport(0, 0, w, h);
  const [eye, fwd] = eyeAndForward();
  const viewProj = M.mul(M.perspective(1.2, w / h, .05, 2000), M.lookAt(eye, fwd));
  gl.disable(gl.DEPTH_TEST); gl.disable(gl.BLEND);
  gl.useProgram(sky.p);
  gl.uniformMatrix4fv(sky.u.invViewProj, false, M.invert(viewProj));
  gl.uniform3fv(sky.u.horizon, HORIZON); gl.uniform3fv(sky.u.zenith, ZENITH); gl.uniform3fv(sky.u.sun, SUN);
  gl.drawArrays(gl.TRIANGLES, 0, 3);
  gl.enable(gl.DEPTH_TEST); gl.clear(gl.DEPTH_BUFFER_BIT);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, atlasTex);
  gl.useProgram(voxel.p); gl.uniformMatrix4fv(voxel.u.viewProj, false, viewProj); gl.uniform1i(voxel.u.atlas, 0);
  gl.bindVertexArray(aerie.vao); gl.drawArraysInstanced(gl.TRIANGLES, 0, 36, aerie.count);
  gl.useProgram(model.p); gl.uniformMatrix4fv(model.u.viewProj, false, viewProj); gl.uniform1i(model.u.atlas, 0);
  gl.disable(gl.CULL_FACE);
  gl.bindVertexArray(aerie.mvao); gl.drawArrays(gl.TRIANGLES, 0, aerie.mcount);
  gl.bindVertexArray(null);
  if ($('#markers').checked) {
    gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    gl.useProgram(box.p); gl.uniformMatrix4fv(box.u.viewProj, false, viewProj);
    const [sx, sy, sz] = facts.stand;
    // The stand marker hides while the camera stands in it (the Arrival view).
    if (Math.hypot(eye[0] - sx - .5, eye[2] - sz - .5) > .6 || eye[1] > sy + 2) {
      gl.uniform3fv(box.u.lo, [sx + .2, sy, sz + .2]); gl.uniform3fv(box.u.hi, [sx + .8, sy + 1.8, sz + .8]); gl.uniform4fv(box.u.tint, [.95, .8, .4, .95]);
      gl.drawArrays(gl.TRIANGLES, 0, 36);
    }
    gl.uniform4fv(box.u.tint, [.35, .9, .45, .6]);
    for (const [cx, cy, cz] of facts.chests) {
      gl.uniform3fv(box.u.lo, [cx + .06, cy, cz + .06]); gl.uniform3fv(box.u.hi, [cx + .94, cy + .88, cz + .94]);
      gl.drawArrays(gl.TRIANGLES, 0, 36);
    }
    gl.disable(gl.BLEND);
  }
  if (cam.mode === 'walk' && keys.size) draw();
}

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
    cam.yaw -= dx * .005; cam.pitch = Math.max(-1.2, Math.min(1.55, cam.pitch + dy * .005));
  }
  draw();
});
canvas.addEventListener('wheel', e => {
  e.preventDefault();
  if (cam.mode === 'orbit') cam.dist = Math.max(3, Math.min(400, cam.dist * Math.exp(e.deltaY * .001)));
  else { const f = eyeAndForward()[1]; for (let i = 0; i < 3; i++) cam.eye[i] -= f[i] * e.deltaY * .01; }
  draw();
}, {passive: false});
addEventListener('keydown', e => {
  if (e.target.tagName === 'INPUT') return;
  if (e.code === 'KeyV' && cam.mode === 'orbit') { const [eye, f] = eyeAndForward(); walk(eye, Math.atan2(f[0], f[2]), Math.asin(f[1])); return; }
  if (e.code === 'KeyO' && cam.mode === 'walk') { const f = eyeAndForward()[1]; orbit(cam.eye.map((v, i) => v + f[i] * 20), Math.atan2(-f[0], -f[2]), -Math.asin(f[1]), 20); return; }
  keys.add(e.code); if (e.code === 'Space') e.preventDefault(); draw();
});
addEventListener('keyup', e => keys.delete(e.code));
addEventListener('blur', () => keys.clear());
addEventListener('resize', draw);
window.aeriePreview = {cam, draw, walk, orbit, get views() { return VIEWS; }};
load().catch(err => { document.body.insertAdjacentHTML('beforeend', `<pre style="position:fixed;bottom:40px;left:12px;color:#f88;background:#000a">${err.stack || err}</pre>`); });
