// Storm Roc intro animatic: the baked Roc and player clips placed by the scene's per-tick track
// (intro-track.json), seen through its camera, over stand-ins for the aerie's peak and the
// summit's sky. GeckoLib baking and posing follow the Guardian intro's animatic
// (art/fractured_guardian/intro/animatic.js). Each actor is placed by a point and a quaternion
// in its half's frame; models face +Z there after GeckoLib's mirror and a half turn.
'use strict';

const $ = s => document.querySelector(s);
const rad = d => d * Math.PI / 180;
const clamp = (v, a, b) => Math.min(b, Math.max(a, v));
const smooth = s => { s = clamp(s, 0, 1); return s * s * (3 - 2 * s); };
const ramp = (t, a, b) => smooth((t - a) / (b - a));

const M = {
  id: () => new Float32Array([1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]),
  mul(a, b) { const o = new Float32Array(16); for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) { let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k]; o[c * 4 + r] = s; } return o; },
  t(x, y, z) { const m = M.id(); m[12] = x; m[13] = y; m[14] = z; return m; },
  s(x, y, z) { const m = M.id(); m[0] = x; m[5] = y; m[10] = z; return m; },
  rx(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([1,0,0,0, 0,c,s,0, 0,-s,c,0, 0,0,0,1]); },
  ry(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([c,0,-s,0, 0,1,0,0, s,0,c,0, 0,0,0,1]); },
  rz(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([c,s,0,0, -s,c,0,0, 0,0,1,0, 0,0,0,1]); },
  quat(p, q) { // translation p then rotation q (w, x, y, z)
    const [w, x, y, z] = q;
    return new Float32Array([1-2*(y*y+z*z), 2*(x*y+z*w), 2*(x*z-y*w), 0, 2*(x*y-z*w), 1-2*(x*x+z*z), 2*(y*z+x*w), 0,
      2*(x*z+y*w), 2*(y*z-x*w), 1-2*(x*x+y*y), 0, p[0], p[1], p[2], 1]);
  },
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
const mix3 = (a, b, s) => [a[0] + (b[0] - a[0]) * s, a[1] + (b[1] - a[1]) * s, a[2] + (b[2] - a[2]) * s];
function hash(n) { const s = Math.sin(n * 127.1 + 311.7) * 43758.5453; return s - Math.floor(s); }

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
  return {bones, faces, index: new Map(bones.map((b, i) => [b.name, i]))};
}

const keyCache = new WeakMap();
function sampleChannel(channel, t, fallback) {
  if (channel === undefined) return fallback;
  if (Array.isArray(channel)) return channel; // a constant
  let keys = keyCache.get(channel);
  if (!keys) { keys = Object.entries(channel).map(([k, v]) => ({t: +k, v})).sort((a, b) => a.t - b.t); keyCache.set(channel, keys); }
  if (t <= keys[0].t) return keys[0].v;
  let lo = 0, hi = keys.length - 1;
  if (t >= keys[hi].t) return keys[hi].v;
  while (hi - lo > 1) { const mid = (lo + hi) >> 1; if (keys[mid].t <= t) lo = mid; else hi = mid; }
  const a = keys[lo], b = keys[hi], f = (t - a.t) / (b.t - a.t || 1);
  return a.v.map((v, j) => v + (b.v[j] - v) * f);
}

function poseBones(model, clip, seconds, base, hidden) {
  const out = [], byName = {};
  model.bones.forEach((bone, i) => {
    const ch = clip.bones[bone.name] || {};
    const r = sampleChannel(ch.rotation, seconds, [0, 0, 0]), p = sampleChannel(ch.position, seconds, [0, 0, 0]);
    let s = sampleChannel(ch.scale, seconds, [1, 1, 1]);
    if (hidden && hidden.has(bone.name)) s = [0, 0, 0];
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
    if (Math.abs(m[0]) + Math.abs(m[5]) + Math.abs(m[10]) + Math.abs(m[1]) + Math.abs(m[4]) < 1e-6) continue;
    for (const k of [0, 1, 2, 0, 2, 3]) data.push(...p[k], ...n, ...f.uvs[k]);
  }
  return new Float32Array(data);
}

// ---------- WebGL ----------
const canvas = $('#view'), hud = $('#hud'), ctx = hud.getContext('2d');
const gl = canvas.getContext('webgl2', {antialias: true, preserveDrawingBuffer: true});
const MODEL_VS = `#version 300 es
in vec3 pos; in vec3 normal; in vec2 uv; uniform mat4 viewProj; out vec3 vNormal; out vec2 vUv; out vec3 vPos;
void main(){ gl_Position = viewProj * vec4(pos, 1.); vNormal = normal; vUv = uv; vPos = pos; }`;
const MODEL_FS = `#version 300 es
precision highp float; in vec3 vNormal; in vec2 vUv; in vec3 vPos; out vec4 color;
uniform sampler2D tex; uniform int glow; uniform vec3 eye; uniform vec3 fog; uniform vec2 fogRange;
uniform float dark, rim, light, hazeK; uniform vec3 sun, glowTint;
void main(){
  vec4 c = texture(tex, vUv); if (c.a < .1) discard;
  float haze = smoothstep(fogRange.x, fogRange.y, distance(vPos, eye)) * hazeK;
  if (glow == 1) { color = vec4(c.rgb * glowTint * 1.6 * (1. - .7 * haze), 1.); return; }
  vec3 n = normalize(gl_FrontFacing ? vNormal : -vNormal);
  float shade = .45 + .55 * max(0., dot(n, normalize(sun))) + .15 * max(0., n.y);
  vec3 lit = c.rgb * shade * light;
  vec3 v = normalize(eye - vPos);
  float edge = pow(1. - abs(dot(n, v)), 2.5);
  vec3 col = mix(lit, vec3(.025, .028, .04) + lit * .08, dark) + rim * edge * vec3(.75, .85, 1.) * 1.6 + rim * .12 * c.rgb;
  color = vec4(mix(col, fog, haze), 1.);
}`;
const SOLID_VS = `#version 300 es
in vec3 pos; in vec3 normal; in vec4 col; uniform mat4 viewProj; out vec3 vNormal; out vec4 vCol; out vec3 vPos;
void main(){ gl_Position = viewProj * vec4(pos, 1.); vNormal = normal; vCol = col; vPos = pos; }`;
const SOLID_FS = `#version 300 es
precision highp float; in vec3 vNormal; in vec4 vCol; in vec3 vPos; out vec4 color;
uniform vec3 eye; uniform vec3 fog; uniform vec2 fogRange; uniform float light; uniform vec3 sun; uniform int flat_;
void main(){
  float haze = smoothstep(fogRange.x, fogRange.y, distance(vPos, eye));
  vec3 c = vCol.rgb;
  if (flat_ == 0) { vec3 n = normalize(gl_FrontFacing ? vNormal : -vNormal); c *= (.5 + .5 * max(0., dot(n, normalize(sun))) + .12 * n.y) * light; }
  color = vec4(mix(c, fog, haze), vCol.a);
}`;
// Cloud puffs: camera-facing sprites with a soft noisy edge, lit from above.
const PUFF_VS = `#version 300 es
in vec3 centre; in vec2 corner; in vec4 col; uniform mat4 viewProj; uniform vec3 right, up; out vec2 vC; out vec4 vCol; out vec3 vPos;
void main(){ vec3 p = centre + (right * corner.x + up * corner.y) * col.a; gl_Position = viewProj * vec4(p, 1.); vC = corner; vCol = col; vPos = p; }`;
const PUFF_FS = `#version 300 es
precision highp float; in vec2 vC; in vec4 vCol; in vec3 vPos; out vec4 color; uniform vec3 eye; uniform vec3 fog; uniform vec2 fogRange; uniform float opacity;
float h(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float n(vec2 p){ vec2 i = floor(p), f = fract(p); f = f*f*(3.-2.*f); return mix(mix(h(i), h(i+vec2(1,0)), f.x), mix(h(i+vec2(0,1)), h(i+vec2(1,1)), f.x), f.y); }
void main(){
  float r = length(vC);
  float k = n(vC * 3. + vCol.a) * .5 + n(vC * 7. - vCol.a) * .25;
  float a = smoothstep(1., .35, r + k * .45 - .2);
  if (a < .01) discard;
  float shade = mix(.72, 1.05, clamp(vC.y * .5 + .5 + k * .3, 0., 1.));
  float haze = smoothstep(fogRange.x, fogRange.y, distance(vPos, eye));
  float nearFade = smoothstep(.5, 6., distance(vPos, eye));
  color = vec4(mix(vCol.rgb * shade, fog, haze * .8), a * opacity * nearFade);
}`;
// Sky dome gradient and the summit's cloud sea, both drawn from the fragment's view ray.
const SKY_VS = `#version 300 es
in vec2 corner; out vec2 vC; void main(){ vC = corner; gl_Position = vec4(corner, .9999, 1.); }`;
const SKY_FS = `#version 300 es
precision highp float; in vec2 vC; out vec4 color; uniform mat4 invViewProj; uniform vec3 eye;
uniform vec3 zenith, horizon, ground; uniform float seaY; uniform int sea; uniform float time; uniform vec3 fog;
float h(vec2 p){ return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float n(vec2 p){ vec2 i = floor(p), f = fract(p); f = f*f*(3.-2.*f); return mix(mix(h(i), h(i+vec2(1,0)), f.x), mix(h(i+vec2(0,1)), h(i+vec2(1,1)), f.x), f.y); }
float fbm(vec2 p){ float s = 0., a = .5; for (int i = 0; i < 5; i++) { s += a * n(p); p *= 2.03; a *= .5; } return s; }
void main(){
  vec4 far = invViewProj * vec4(vC, 1., 1.); vec3 d = normalize(far.xyz / far.w - eye);
  vec3 sky = mix(horizon, zenith, pow(clamp(d.y, 0., 1.), .55));
  if (d.y < 0.) sky = mix(horizon, ground, clamp(-d.y * 3., 0., 1.));
  if (sea == 1 && d.y < 0. && eye.y > seaY) {
    float t = (seaY - eye.y) / d.y; vec2 p = eye.xz + d.xz * t + vec2(time * .9, time * .35);
    float c = fbm(p * .018) * .65 + fbm(p * .07) * .35;
    vec3 top = mix(vec3(.74, .78, .86), vec3(1.), smoothstep(.35, .75, c));
    float dist = t;
    sky = mix(top, horizon, smoothstep(250., 1400., dist));
  }
  color = vec4(sky, 1.);
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
function vao(prog, layout, stride) {
  const va = gl.createVertexArray(), buf = gl.createBuffer();
  gl.bindVertexArray(va); gl.bindBuffer(gl.ARRAY_BUFFER, buf);
  let offset = 0;
  for (const [name, size] of layout) { const loc = prog.a(name); if (loc >= 0) { gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, size, gl.FLOAT, false, stride * 4, offset * 4); } offset += size; }
  gl.bindVertexArray(null);
  return {va, buf, count: 0};
}
function upload(mesh, data, usage) { gl.bindBuffer(gl.ARRAY_BUFFER, mesh.buf); gl.bufferData(gl.ARRAY_BUFFER, data instanceof Float32Array ? data : new Float32Array(data), usage || gl.STATIC_DRAW); mesh.count = data.length; }
function texture(image) {
  const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, image);
  return t;
}
// Solid geometry: pos, normal, rgba.
function quad(data, a, b, c, d, col) {
  const n = norm(cross(sub(b, a), sub(c, a)));
  for (const p of [a, b, c, a, c, d]) data.push(...p, ...n, ...col);
}
function box(data, min, max, col, m) {
  const [x0, y0, z0] = min, [x1, y1, z1] = max, P = p => m ? M.apply(m, p) : p;
  const c = [[x0,y0,z0],[x1,y0,z0],[x1,y1,z0],[x0,y1,z0],[x0,y0,z1],[x1,y0,z1],[x1,y1,z1],[x0,y1,z1]].map(P);
  const col4 = col.length === 4 ? col : [...col, 1];
  for (const [a, b, cc, d] of [[0,3,2,1],[4,5,6,7],[0,4,7,3],[1,2,6,5],[3,7,6,2],[0,1,5,4]]) quad(data, c[a], c[b], c[cc], c[d], col4);
}

// ---------- Scenery ----------
// The aerie: a peak whose top the build levelled, the gong in its frame, ranges falling away
// below into a valley, a low storm cloud a few dozen blocks over the summit.
function peakHeight(x, z) {
  const r = Math.hypot(x, z - 4);
  const noise = (Math.sin(x * .13 + z * .07) + Math.sin(z * .11 - x * .05) * .7 + Math.sin((x + z) * .31) * .25) * (1.2 + r * .02);
  let h = r < 8.5 ? 0 : -(r - 8.5) * .95 - Math.pow(r - 8.5, 1.32) * .04 + noise * smooth((r - 8.5) / 10);
  for (const [px, pz, ph, slope] of [[150, 170, -12, 1.05], [-190, 70, -4, 1.0], [70, -230, 6, 1.1], [-120, -160, -20, .95], [260, -40, -26, 1.0], [-60, 300, -16, 1.0]]) {
    const d = Math.hypot(x - px, z - pz);
    h = Math.max(h, ph - d * slope + noise * .8);
  }
  return Math.max(h, -175 + Math.sin(x * .02) * 4 + Math.cos(z * .025) * 4);
}
function buildAerie() {
  const data = [];
  const rings = [];
  for (let i = 0; i <= 90; i++) rings.push(i < 30 ? i * .5 : 15 + Math.pow(i - 30, 1.65) * 1.1);
  const S = 96;
  const pt = (ri, si) => { const r = rings[ri], a = si / S * Math.PI * 2; const x = Math.cos(a) * r, z = 4 + Math.sin(a) * r; return [x, peakHeight(x, z), z]; };
  const colour = (p, q) => {
    const steep = Math.abs(q[1] - p[1]) / (Math.hypot(q[0] - p[0], q[2] - p[2]) + .01);
    if (p[1] < -150) return [.2, .24, .2, 1];
    const snow = p[1] > -55 + Math.sin(p[0] * .1) * 8 && steep < 1.35;
    return snow ? [.88, .9, .95, 1] : [.38, .38, .4, 1];
  };
  for (let ri = 0; ri < rings.length - 1; ri++) for (let si = 0; si < S; si++) {
    const a = pt(ri, si), b = pt(ri, si + 1), c = pt(ri + 1, si + 1), d = pt(ri + 1, si);
    quad(data, a, d, c, b, colour(a, d));
  }
  // The gong's frame: dark oak posts and beam, a slab roof, the oxidized rod.
  const oak = [.29, .2, .12, 1], slab = [.25, .17, .1, 1], rod = [.32, .56, .5, 1];
  box(data, [-3.5, 0, 3.5], [-2.5, 6, 4.5], oak); box(data, [2.5, 0, 3.5], [3.5, 6, 4.5], oak);
  box(data, [-3.5, 6, 3.5], [4.5, 7, 4.5], oak); box(data, [-4.5, 7, 3], [5.5, 7.5, 5], slab);
  box(data, [-3.1, 7.5, 3.9], [-2.9, 9.5, 4.1], rod);
  box(data, [-2.6, 4.8, 3.6], [-2.2, 5.2, 4.4], oak); box(data, [2.2, 4.8, 3.6], [2.6, 5.2, 4.4], oak);
  return data;
}
function buildSummit(facts) {
  const data = [];
  const snow = [.92, .94, .97, 1], rock = [.42, .43, .47, 1], dark = [.3, .31, .35, 1];
  // The floor: a flat round of snow with a ragged rim, a rock skirt below it.
  const S = 72;
  const rim = a => facts.rim + Math.sin(a * 5) * 2.4 + Math.sin(a * 11 + 1) * 1.3;
  for (let i = 0; i < S; i++) {
    const a0 = i / S * Math.PI * 2, a1 = (i + 1) / S * Math.PI * 2;
    const p0 = [Math.cos(a0) * rim(a0), 0, Math.sin(a0) * rim(a0)], p1 = [Math.cos(a1) * rim(a1), 0, Math.sin(a1) * rim(a1)];
    quad(data, [0, 0, 0], p1, p0, p0, snow);
    const b0 = [p0[0] * .8, -18, p0[2] * .8], b1 = [p1[0] * .8, -18, p1[2] * .8];
    const c0 = [p0[0] * .45, -52, p0[2] * .45], c1 = [p1[0] * .45, -52, p1[2] * .45];
    quad(data, p0, p1, b1, b0, rock); quad(data, b0, b1, c1, c0, dark);
  }
  // A few boulders and drifts so the floor reads at speed.
  for (let i = 0; i < 40; i++) {
    const a = hash(i) * Math.PI * 2, r = 24 + hash(i + 50) * 30, x = Math.cos(a) * r, z = Math.sin(a) * r, s = 1 + hash(i + 9) * 2.2;
    if (Math.hypot(x - facts.arrival[0], z - facts.arrival[2] + 4) < 15) continue; // the landing and the roll stay clear
    box(data, [x - s, 0, z - s * .8], [x + s, s * 1.2, z + s * .8], hash(i + 3) > .5 ? rock : [.8, .82, .88, 1]);
  }
  // The spires: needles out of the cloud sea, a neck below a flared capstone, snow on top.
  facts.perches.forEach((perch, k) => {
    const out = norm([perch[0], 0, perch[2]]), top = perch[1] - 1;
    const cx = perch[0] - out[0] * 1.5, cz = perch[2] - out[2] * 1.5;
    const profile = [[facts.cloud_y - 6, 8.5], [0, 5.8], [top - 6, 3.8], [top - 3, 5.4], [top - .8, 6.9], [top, 6.6]];
    const N = 10;
    for (let j = 0; j < profile.length - 1; j++) for (let i = 0; i < N; i++) {
      const [y0, r0] = profile[j], [y1, r1] = profile[j + 1];
      const a0 = i / N * Math.PI * 2 + k, a1 = (i + 1) / N * Math.PI * 2 + k;
      const w = (a, r, y) => [cx + Math.cos(a) * r * (1 + .08 * Math.sin(a * 3 + y * .1)), y, cz + Math.sin(a) * r * (1 + .08 * Math.sin(a * 3 + y * .1))];
      quad(data, w(a0, r0, y0), w(a1, r0, y0), w(a1, r1, y1), w(a0, r1, y1), j === profile.length - 2 ? snow : (j % 2 ? rock : dark));
    }
    for (let i = 0; i < N; i++) {
      const a0 = i / N * Math.PI * 2 + k, a1 = (i + 1) / N * Math.PI * 2 + k;
      quad(data, [cx, top + .3, cz], [cx + Math.cos(a1) * 6.6, top, cz + Math.sin(a1) * 6.6], [cx + Math.cos(a0) * 6.6, top, cz + Math.sin(a0) * 6.6], [cx, top + .3, cz], snow);
    }
  });
  // Far peaks standing out of the sea on the horizon.
  for (const [x, z, h, r] of [[-200, -300, 30, 60], [180, -340, 10, 45], [320, 140, 0, 50], [-330, 120, 18, 55], [60, 360, -8, 40]]) {
    const N = 12;
    for (let i = 0; i < N; i++) {
      const a0 = i / N * Math.PI * 2, a1 = (i + 1) / N * Math.PI * 2;
      quad(data, [x + Math.cos(a0) * r, facts.cloud_y, z + Math.sin(a0) * r], [x + Math.cos(a1) * r, facts.cloud_y, z + Math.sin(a1) * r], [x, h, z], [x, h, z], i % 3 ? [.7, .72, .78, 1] : [.5, .52, .58, 1]);
    }
  }
  return data;
}
// The summit's storm cap: a dark ceiling over the centre that frays away (SummitCloudEffects).
function buildCap(facts) {
  const data = [], N = 64, R = 14;
  const alpha = r => clamp(1 - (r - facts.cap_solid) / (facts.cap_edge - facts.cap_solid), 0, 1);
  for (let j = 0; j < R; j++) for (let i = 0; i < N; i++) {
    const r0 = j / R * facts.cap_edge * 1.05, r1 = (j + 1) / R * facts.cap_edge * 1.05;
    const a0 = i / N * Math.PI * 2, a1 = (i + 1) / N * Math.PI * 2;
    const f = (a, r) => { const k = alpha(r + 14 * Math.sin(a * 7 + r * .05)); return [[Math.cos(a) * r, facts.cap_y - 3 * Math.sin(a * 5 + r * .1), Math.sin(a) * r], k]; };
    const v = [f(a0, r0), f(a1, r0), f(a1, r1), f(a0, r1)];
    for (const k of [0, 1, 2, 0, 2, 3]) data.push(...v[k][0], 0, -1, 0, .2, .21, .26, v[k][1] * .92);
  }
  return data;
}
function puffs(seed, n, place) {
  const out = [];
  for (let i = 0; i < n; i++) out.push(place(i, k => hash(seed + i * 7.31 + k * 1.7)));
  return out;
}
const PUFFS = {
  0: puffs(1, 520, (i, r) => { // the storm cloud low over the aerie
    const a = r(0) * Math.PI * 2, d = Math.sqrt(r(1)) * 260, y = 38 + Math.pow(r(2), .8) * 40;  // its underside at the base (30, lifted)
    const g = .45 + .25 * (y - 38) / 40;
    return {c: [Math.cos(a) * d - 10, y, Math.sin(a) * d + 10], s: 9 + r(3) * 16, col: [g, g + .02, g + .06]};
  }).concat(puffs(9, 60, (i, r) => ({c: [12 + (r(0) - .5) * 60, 36 + r(1) * 24, 2 + (r(2) - .5) * 60], s: 6 + r(3) * 10, col: [.5, .52, .58]}))),
  1: puffs(2, 140, (i, r) => { // wisps over the sea, and the bank it climbs out of
    const bank = i < 46;
    if (bank) return {c: [-66 + (r(0) - .5) * 34, -30 + r(1) * 50, -168 + (r(2) - .5) * 34], s: 9 + r(3) * 16, col: [.93, .95, 1]};
    const a = r(0) * Math.PI * 2, d = 40 + r(1) * 420;
    return {c: [Math.cos(a) * d, -34 + r(2) * 12 + (r(4) > .8 ? r(5) * 60 : 0), Math.sin(a) * d - 60], s: 14 + r(3) * 30, col: [.95, .97, 1]};
  }),
};

// Lightning: jagged bolts and crackles, drawn as camera-facing ribbons.
function bolt(from, to, seed, wiggle, branches) {
  const pts = [from], n = 14;
  for (let i = 1; i < n; i++) {
    const s = i / n, p = mix3(from, to, s);
    pts.push([p[0] + (hash(seed + i) - .5) * wiggle, p[1] + (hash(seed + i + 40) - .5) * wiggle * .3, p[2] + (hash(seed + i + 80) - .5) * wiggle]);
  }
  pts.push(to);
  const lines = [pts];
  for (let b = 0; b < branches; b++) {
    const k = 3 + Math.floor(hash(seed + b * 13) * (n - 6)), start = pts[k];
    const end = add(start, [(hash(seed + b + 5) - .5) * wiggle * 2.5, -Math.abs(to[1] - from[1]) * (.15 + hash(seed + b) * .2), (hash(seed + b + 7) - .5) * wiggle * 2.5]);
    lines.push(bolt(start, end, seed + 100 + b, wiggle * .6, 0)[0]);
  }
  return lines;
}

// ---------- State ----------
const ELEMENTS = {fire: [224, 132, 44], nature: [127, 211, 107], wind: [170, 225, 255], stone: [188, 199, 195], space: [178, 157, 255]};
const SHOTS = [['1 · quiet', 0], ['2 · caw', 'caw'], ['3 · snatch', 'snatch'], ['4 · carried', 'grip', 8], ['5 · reveal', 'look_up'], ['6 · clouds', 'cloud'],
  ['7 · struggle', 'summit'], ['8 · throw', 'throw'], ['9 · landing', 'ground'], ['10 · hand back', 'back']];
const SHOT_NAMES = ['quiet', 'caw', 'snatch', 'carried off', 'the reveal', 'storm clouds', 'the struggle', 'the throw', 'the landing', 'hand back'];
const state = {tick: 0, playing: true, speed: 1, mode: 'scene', bars: true, cues: true, element: 'wind', skin: 'steve', orbit: {yaw: rad(35), pitch: rad(12), dist: 9}};
let track, roc, player, playerSlim, gong, skins = {}, progs = {}, meshes = {}, scenery = {};

async function load() {
  const bust = '?v=' + Date.now();
  const json = async f => { const r = await fetch(f + bust); if (!r.ok) throw Error(`${f}: ${r.status}`); return r.json(); };
  const image = f => new Promise((ok, no) => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => no(Error(f + ' did not load')); i.src = f + bust; });
  const [rGeo, rAnim, rTex, rGlow, gGeo, gTex, pGeo, pSlim, pAnim, steve, alex, trackData] = await Promise.all([
    json('storm_roc_intro.geo.json'), json('storm_roc_intro.animation.json'), image('storm_roc.png'), image('storm_roc_glowmask.png'),
    json('storm_gong.geo.json'), image('storm_gong.png'), json('player.geo.json'), json('player_slim.geo.json'), json('player.animation.json'),
    image('skin_steve.png'), image('skin_alex.png'), json('intro-track.json')]);
  progs = {model: program(MODEL_VS, MODEL_FS), solid: program(SOLID_VS, SOLID_FS), puff: program(PUFF_VS, PUFF_FS), sky: program(SKY_VS, SKY_FS)};
  meshes.model = vao(progs.model, [['pos', 3], ['normal', 3], ['uv', 2]], 8);
  meshes.solid = vao(progs.solid, [['pos', 3], ['normal', 3], ['col', 4]], 10);
  meshes.puff = vao(progs.puff, [['centre', 3], ['corner', 2], ['col', 4]], 9);
  meshes.sky = vao(progs.sky, [['corner', 2]], 2); upload(meshes.sky, [-1,-1, 1,-1, 1,1, -1,-1, 1,1, -1,1]);
  roc = {model: bakeModel(rGeo), clip: rAnim.animations['animation.storm_roc.intro'], tex: texture(rTex), glow: texture(rGlow)};
  const pClip = pAnim.animations['animation.storm_roc_intro_player.intro'];
  player = {model: bakeModel(pGeo), clip: pClip}; playerSlim = {model: bakeModel(pSlim), clip: pClip};
  skins = {steve: texture(steve), alex: texture(alex)};
  gong = {model: bakeModel(gGeo), tex: texture(gTex)};
  track = trackData;
  for (const [half, build] of [[0, () => buildAerie()], [1, () => buildSummit(track.summit)]]) {
    const m = vao(progs.solid, [['pos', 3], ['normal', 3], ['col', 4]], 10); upload(m, build()); scenery[half] = m;
  }
  scenery.cap = vao(progs.solid, [['pos', 3], ['normal', 3], ['col', 4]], 10); upload(scenery.cap, buildCap(track.summit));
  scenery.bolts = track.lightning.map((e, i) => {
    if (e.kind === 'bolt') return {...e, lines: bolt(e.at, [e.at[0] + 6, track.summit.cloud_y + 1, e.at[2] + 4], i * 31, 9, 3)};
    if (e.kind === 'crackle') return {...e, lines: Array.from({length: 5}, (_, k) => { const a = hash(i * 9 + k) * 6.3, b = hash(i * 9 + k + 3) * 3 - 1.5;
      const from = add(e.at, [Math.cos(a) * 1.2, b, Math.sin(a) * 1.2]); return bolt(from, add(from, [(hash(k + i) - .5) * 4, (hash(k * 3 + i) - .5) * 3, (hash(k * 5 + i) - .5) * 4]), i * 17 + k, .9, 0)[0]; })};
    return {...e, lines: []};
  });
  $('#timeline').max = track.beats.length;
  for (const [label, beat, plus] of SHOTS) {
    const b = document.createElement('button'); b.textContent = label;
    b.onclick = () => { state.tick = (typeof beat === 'number' ? beat : track.beats[beat]) + (plus || 0); state.playing = false; syncPlay(); };
    $('#shots').append(b);
  }
}

function clipTime(t) { const a = Math.floor(t); return track.cuts.includes(a + 1) ? a : t; }

/** The track at a fractional tick, holding each shot until its cut. */
function sample(t) {
  const T = track.ticks, n = T.eye.length - 1;
  const a = clamp(Math.floor(t), 0, n), b = Math.min(n, a + 1);
  const f = track.cuts.includes(b) ? 0 : t - a;
  const pick = k => typeof T[k][a] === 'number' ? T[k][a] + (T[k][b] - T[k][a]) * f : lerp(T[k][a], T[k][b], f);
  const angle = k => { let x = T[k][a], y = T[k][b]; while (y - x > 180) y -= 360; while (y - x < -180) y += 360; return x + (y - x) * f; };
  const place = k => { const A = T[k][a], B = T[k][b]; let qa = A.slice(3), qb = B.slice(3); if (dot4(qa, qb) < 0) qb = qb.map(v => -v);
    return {pos: lerp(A.slice(0, 3), B.slice(0, 3), f), q: norm4(lerp(qa, qb, f))}; };
  const out = {half: T.half[a], eye: pick('eye'), yaw: angle('yaw'), pitch: pick('pitch'), roll: pick('roll'), fov: pick('fov'), roc: place('roc'), player: place('player'),
    pov: T.pov[a], hand: pick('hand'), rocHead: pick('roc_head')};
  for (const k of ['white', 'dim', 'shade', 'beat', 'wand', 'dark', 'bars', 'flash']) out[k] = pick(k);
  return out;
}
const dot4 = (a, b) => a[0]*b[0] + a[1]*b[1] + a[2]*b[2] + a[3]*b[3];
const norm4 = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };

/** Minecraft's camera axes for a yaw, pitch and roll (yaw 0 looks along +Z; +pitch looks down). */
function axes(yaw, pitch, roll) {
  const y = rad(yaw), p = rad(pitch), r = rad(roll || 0);
  const forward = [-Math.sin(y) * Math.cos(p), -Math.sin(p), Math.cos(y) * Math.cos(p)];
  let right = [-Math.cos(y), 0, -Math.sin(y)];
  let up = cross(right, forward);
  const c = Math.cos(r), s = Math.sin(r);
  [right, up] = [add(scale(right, c), scale(up, -s)), add(scale(up, c), scale(right, s))];
  return {forward, right, up};
}

const LOOK = {
  0: {zenith: [.2, .23, .3], horizon: [.5, .54, .6], ground: [.22, .25, .24], fog: [.42, .45, .52], fogRange: [110, 650], sun: [.3, 1, .2], light: .85},
  1: {zenith: [.2, .4, .76], horizon: [.78, .87, .96], ground: [.7, .75, .82], fog: [.76, .84, .94], fogRange: [220, 1300], sun: [.4, .9, .3], light: 1},
};

function draw() {
  const dpr = Math.min(devicePixelRatio, 2), w = Math.round(canvas.clientWidth * dpr), h = Math.round(canvas.clientHeight * dpr);
  if (canvas.width !== w || canvas.height !== h) { canvas.width = hud.width = w; canvas.height = hud.height = h; }
  // Clips hold with the track before a cut, so bodies never move ahead of their placement.
  const t = clipTime(state.tick), s = sample(state.tick), half = s.half, look = LOOK[half];
  let eye, right, up, forward, fov;
  if (state.mode === 'scene') {
    const adj = window.camAdjust || {yaw: 0, pitch: 0, fov: 0}; // for review only
    ({forward, right, up} = axes(s.yaw + adj.yaw, s.pitch + adj.pitch, s.roll)); eye = s.eye; fov = s.fov + adj.fov;
  } else {
    const o = state.orbit, target = add(s.player.pos, [0, 1, 0]);
    eye = add(target, [Math.sin(o.yaw) * Math.cos(o.pitch) * o.dist, Math.sin(o.pitch) * o.dist, Math.cos(o.yaw) * Math.cos(o.pitch) * o.dist]);
    forward = norm(sub(target, eye)); right = norm(cross(forward, [0, 1, 0])); up = cross(right, forward); fov = 55;
  }
  const proj = M.perspective(rad(fov), w / h, .05, 3000), vp = M.mul(proj, M.view(eye, right, up, scale(forward, -1)));
  const inv = invert(vp);
  // In the aerie's cloud the world goes grey and close.
  let fog = look.fog, fogRange = look.fogRange, light = look.light * (1 - .55 * s.dim) * (1 + 1.4 * s.flash);
  if (half === 0) {
    const inCloud = smooth((eye[1] - track.aerie.cloud_base) / 5);  // a dark storm cloud, close round you
    fog = mix3(fog, [.4, .42, .48], inCloud); fogRange = [mix(60, 1, inCloud), mix(420, 30, inCloud)];
  }
  gl.viewport(0, 0, w, h); gl.clearColor(...fog, 1); gl.clear(gl.COLOR_BUFFER_BIT | gl.DEPTH_BUFFER_BIT);
  // Sky (and in the summit's half the cloud sea).
  gl.disable(gl.DEPTH_TEST); gl.useProgram(progs.sky.p); gl.bindVertexArray(meshes.sky.va);
  gl.uniformMatrix4fv(progs.sky.u.invViewProj, false, inv); gl.uniform3fv(progs.sky.u.eye, eye);
  const dimSky = c => scale(c, (1 - .5 * s.dim) * (1 + 1.2 * s.flash));
  gl.uniform3fv(progs.sky.u.zenith, dimSky(look.zenith)); gl.uniform3fv(progs.sky.u.horizon, dimSky(half === 0 ? mix3(look.horizon, fog, smooth((eye[1] - track.aerie.cloud_base) / 5)) : look.horizon));
  gl.uniform3fv(progs.sky.u.ground, look.ground); gl.uniform1f(progs.sky.u.seaY, track.summit.cloud_y); gl.uniform1i(progs.sky.u.sea, half);
  gl.uniform1f(progs.sky.u.time, t / 20); gl.uniform3fv(progs.sky.u.fog, fog);
  gl.drawArrays(gl.TRIANGLES, 0, 6);
  gl.enable(gl.DEPTH_TEST); gl.depthFunc(gl.LEQUAL); gl.disable(gl.CULL_FACE);

  // Scenery.
  const solid = progs.solid;
  gl.useProgram(solid.p); gl.uniformMatrix4fv(solid.u.viewProj, false, vp); gl.uniform3fv(solid.u.eye, eye); gl.uniform3fv(solid.u.fog, fog);
  gl.uniform2fv(solid.u.fogRange, fogRange); gl.uniform1f(solid.u.light, light); gl.uniform3fv(solid.u.sun, look.sun); gl.uniform1i(solid.u.flat_, 0);
  gl.bindVertexArray(scenery[half].va); gl.drawArrays(gl.TRIANGLES, 0, scenery[half].count / 10);

  // Actors.
  const model = progs.model;
  gl.useProgram(model.p); gl.uniformMatrix4fv(model.u.viewProj, false, vp); gl.uniform3fv(model.u.eye, eye); gl.uniform3fv(model.u.fog, fog);
  gl.uniform2fv(model.u.fogRange, fogRange); gl.uniform3fv(model.u.sun, look.sun); gl.uniform1i(model.u.tex, 0); gl.activeTexture(gl.TEXTURE0);
  gl.bindVertexArray(meshes.model.va);
  const drawActor = (actor, tex, base, opts) => {
    const data = meshData(actor.model, poseBones(actor.model, actor.clip, t / 20, base, opts.hidden));
    upload(meshes.model, data, gl.DYNAMIC_DRAW);
    gl.bindTexture(gl.TEXTURE_2D, tex); gl.uniform1i(model.u.glow, 0);
    gl.uniform1f(model.u.dark, opts.dark || 0); gl.uniform1f(model.u.rim, opts.rim || 0); gl.uniform1f(model.u.light, opts.light);
    gl.uniform1f(model.u.hazeK, opts.hazeK === undefined ? 1 : opts.hazeK); gl.uniform3fv(model.u.glowTint, opts.glowTint || [1, 1, 1]);
    gl.drawArrays(gl.TRIANGLES, 0, data.length / 8);
    if (opts.glow) {
      gl.bindTexture(gl.TEXTURE_2D, opts.glow); gl.uniform1i(model.u.glow, 1);
      gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE); gl.depthMask(false);
      gl.drawArrays(gl.TRIANGLES, 0, data.length / 8);
      gl.depthMask(true); gl.disable(gl.BLEND);
    }
    return data;
  };
  const ENTITY = M.ry(Math.PI);
  if (half === 0) drawActor({model: gong.model, clip: {bones: {}}}, gong.tex, M.mul(M.t(0, 1, 4), ENTITY), {light: light * .9});
  const rocBase = M.mul(M.mul(M.quat(s.roc.pos, s.roc.q), M.s(1.25, 1.25, 1.25)), ENTITY);
  drawActor(roc, roc.tex, rocBase, {glow: roc.glow, dark: s.dark, rim: 1.3 * s.flash + (half === 0 ? .12 : .08), light, hazeK: half === 0 ? .3 : 1, glowTint: [1, .5, .4]});
  const slim = state.skin === 'alex', pup = slim ? playerSlim : player;
  const pBase = M.mul(M.quat(s.player.pos, s.player.q), ENTITY);
  const hidden = s.pov && state.mode === 'scene' ? new Set(track.pov_hides) : null;
  drawActor(pup, skins[state.skin], pBase, {light: light * .95, hidden});
  // The wand in the right hand, its tip in the element's colour.
  const pMats = poseBones(pup.model, pup.clip, t / 20, pBase, hidden), wand = pMats[pup.model.index.get('wand')];
  const el = ELEMENTS[state.element].map(v => v / 255), wd = [];
  if (!hidden) {
  const wm = M.mul(wand, M.mul(M.t(...pup.model.bones[pup.model.index.get('wand')].pivot), M.rx(rad(-70))));
  box(wd, [-.035, -.035, -.06], [.035, .035, .62], [.33, .22, .13], wm); box(wd, [-.06, -.06, .58], [.06, .06, .72], el, wm);
  gl.useProgram(solid.p); upload(meshes.solid, wd, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.solid.va); gl.drawArrays(gl.TRIANGLES, 0, wd.length / 10);
  }

  // Translucent: the storm cap, clouds, lightning, the wand's flash.
  gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); gl.depthMask(false);
  gl.useProgram(solid.p);
  if (half === 1) { gl.uniform1f(solid.u.light, 1 + 2 * s.flash); gl.bindVertexArray(scenery.cap.va); gl.drawArrays(gl.TRIANGLES, 0, scenery.cap.count / 10); }
  drawPuffs(half, eye, right, up, vp, fog, fogRange, s, t);
  drawLightning(t, half, eye, vp, fog);
  if (s.wand > .02) { // the shock: a burst of the element's light where the wand hits the talon
    const prog = progs.puff, d = [], size = .6 + 3.2 * s.wand;
    for (const [k, c] of [[1, el.map(v => Math.min(1, v * 1.4))], [.45, [1, 1, 1]]])
      for (const [x, y] of [[-1,-1],[1,-1],[1,1],[-1,-1],[1,1],[-1,1]]) d.push(...s.hand, x, y, ...c, size * k);
    gl.useProgram(prog.p); gl.uniformMatrix4fv(prog.u.viewProj, false, vp); gl.uniform3fv(prog.u.right, right); gl.uniform3fv(prog.u.up, up);
    gl.uniform3fv(prog.u.eye, eye); gl.uniform3fv(prog.u.fog, fog); gl.uniform2fv(prog.u.fogRange, fogRange); gl.uniform1f(prog.u.opacity, Math.min(1, s.wand * 1.6));
    gl.disable(gl.DEPTH_TEST); gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
    upload(meshes.puff, d, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.puff.va); gl.drawArrays(gl.TRIANGLES, 0, d.length / 9);
    gl.enable(gl.DEPTH_TEST); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
  }
  gl.depthMask(true); gl.disable(gl.BLEND);
  if (state.mode === 'orbit') drawGizmo(s, vp);
  drawHud(t, s, w, h);
}

function drawPuffs(half, eye, right, up, vp, fog, fogRange, s, t) {
  const list = PUFFS[half];
  const sorted = list.map(p => ({p, d: Math.hypot(...sub(p.c, eye))})).filter(x => x.d < 900).sort((a, b) => b.d - a.d);
  const data = [];
  const drift = half === 1 ? [t / 20 * 2.2, 0, t / 20 * 1.0] : [t / 20 * 3, -t / 20 * 4, 0];
  const lift = half === 0 ? [0, track.aerie.cloud_base - 30, 0] : [0, 0, 0];
  for (const {p} of sorted) {
    const c = add(add(p.c, drift), lift), col = scale(p.col, 1 + 1.6 * s.flash);
    for (const [x, y] of [[-1,-1],[1,-1],[1,1],[-1,-1],[1,1],[-1,1]]) data.push(...c, x, y, ...col, p.s);
  }
  const prog = progs.puff;
  gl.useProgram(prog.p); gl.uniformMatrix4fv(prog.u.viewProj, false, vp); gl.uniform3fv(prog.u.right, right); gl.uniform3fv(prog.u.up, up);
  gl.uniform3fv(prog.u.eye, eye); gl.uniform3fv(prog.u.fog, fog); gl.uniform2fv(prog.u.fogRange, fogRange); gl.uniform1f(prog.u.opacity, half === 0 ? .9 : .85);
  upload(meshes.puff, data, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.puff.va); gl.drawArrays(gl.TRIANGLES, 0, data.length / 9);
}

function drawLightning(t, half, eye, vp, fog) {
  const data = [];
  for (const e of scenery.bolts) {
    if (e.half !== half || t < e.tick || t > e.tick + 6 || !e.lines.length) continue;
    const age = t - e.tick, a = (1 - age / 6) * (age % 2 < 1 ? 1 : .55);
    const width = e.kind === 'bolt' ? .9 : .07;
    for (const line of e.lines) for (let i = 0; i < line.length - 1; i++) {
      const p = line[i], q = line[i + 1], d = norm(sub(q, p)), side = norm(cross(d, norm(sub(eye, p)))), o = scale(side, width);
      quad(data, sub(p, o), add(p, o), add(q, o), sub(q, o), [.85, .92, 1, a]);
      const g = scale(side, width * 4);
      quad(data, sub(p, g), add(p, g), add(q, g), sub(q, g), [.55, .7, 1, a * .25]);
    }
  }
  if (!data.length) return;
  const prog = progs.solid;
  gl.useProgram(prog.p); gl.uniform1i(prog.u.flat_, 1); gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
  upload(meshes.solid, data, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.solid.va); gl.drawArrays(gl.TRIANGLES, 0, data.length / 10);
  gl.uniform1i(prog.u.flat_, 0); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
}

function drawGizmo(s, vp) {
  const {forward, right, up} = axes(s.yaw, s.pitch, s.roll), e = s.eye, g = [], col = [.42, .95, 1, 1];
  box(g, sub(e, [.08, .08, .08]), add(e, [.08, .08, .08]), col);
  for (let i = 1; i <= 10; i++) { const c = add(e, scale(forward, i * .15)); box(g, sub(c, [.02, .02, .02]), add(c, [.02, .02, .02]), col); }
  const prog = progs.solid; gl.useProgram(prog.p); gl.uniform1i(prog.u.flat_, 1);
  upload(meshes.solid, g, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.solid.va); gl.drawArrays(gl.TRIANGLES, 0, g.length / 10); gl.uniform1i(prog.u.flat_, 0);
}

function invert(m) {
  const inv = new Float32Array(16), a = m;
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
  return inv.map(v => v / det);
}
const mix = (a, b, s) => a + (b - a) * s;

/** Snow, the white-out, the wing shadow and lightning, the wand's flash, letterbox and title, as the client draws them. */
function drawHud(t, s, w, h) {
  const b = track.beats;
  ctx.clearRect(0, 0, w, h);
  if (state.mode === 'scene') {
    // Sideways snow at the aerie until the cloud swallows it.
    if (s.half === 0 && t < b.cloud) {
      const amount = .4 + .6 * ramp(t, 0, b.caw);
      ctx.strokeStyle = 'rgba(240,244,250,.55)'; ctx.lineWidth = Math.max(1, h * .0018);
      ctx.beginPath();
      for (let i = 0; i < 220 * amount; i++) {
        const speed = .8 + hash(i) * 1.4, y = (hash(i + 7) * 1.2 - .1 + t * .004 * speed) % 1.1 * h, x = ((hash(i + 3) + t * .035 * speed) % 1.2 - .1) * w;
        const len = (8 + hash(i + 11) * 20) * speed * h / 900;
        ctx.moveTo(x, y); ctx.lineTo(x + len, y + len * .18);
      }
      ctx.stroke();
    }
    if (s.shade > 0) { // its wing's shadow sweeping across the view, top to bottom
      const y = (s.beat * 1.8 - .5) * h, g = ctx.createLinearGradient(0, y - h * .45, 0, y + h * .45);
      g.addColorStop(0, 'rgba(4,6,10,0)'); g.addColorStop(.5, `rgba(4,6,10,${Math.min(.85, s.shade * 1.5)})`); g.addColorStop(1, 'rgba(4,6,10,0)');
      ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
    }
    if (s.white > 0) { ctx.fillStyle = `rgba(232,236,242,${s.white})`; ctx.fillRect(0, 0, w, h); }
    if (s.flash > 0) { ctx.fillStyle = `rgba(214,228,255,${.35 * s.flash * (s.half === 0 && t > b.cloud ? 1.6 : 1)})`; ctx.fillRect(0, 0, w, h); }
    if (s.wand > 0) { const c = ELEMENTS[state.element]; ctx.fillStyle = `rgba(${c[0]},${c[1]},${c[2]},${.3 * s.wand})`; ctx.fillRect(0, 0, w, h); }
    if (state.bars) { const bar = Math.round(h * .11 * s.bars); ctx.fillStyle = '#000'; ctx.fillRect(0, 0, w, bar); ctx.fillRect(0, h - bar, w, bar); }
    const fade = 8, enter = smooth((t - b.title) / fade), title = enter * (1 - smooth((t - (b.title_end - fade)) / fade));
    if (title > 0) {
      // Under the Roc in the landing insert, just above the letterbox, as the client draws it.
      const size = Math.round(h * .062), bar = state.bars ? Math.round(h * .11 * s.bars) : 0;
      const y = h - bar - h * .012 - size * 1.05 - h * .02 * (1 - enter);
      ctx.font = `600 ${size}px system-ui, sans-serif`; ctx.textAlign = 'center'; ctx.textBaseline = 'middle';
      const text = 'STORM ROC', tw = ctx.measureText(text).width;
      ctx.fillStyle = `rgba(0,0,0,${.85 * title})`; ctx.fillRect(w / 2 - tw / 2 - size * .6, y - size * .85, tw + size * 1.2, size * 1.9);
      ctx.fillStyle = `rgba(232,238,246,${title})`; ctx.fillText(text, w / 2, y);
      const line = tw / 2 * smooth((t - b.title) / 14); ctx.fillStyle = `rgba(255,96,64,${.85 * title})`; ctx.fillRect(w / 2 - line, y + size * .72, line * 2, Math.max(1, h * .002));
    }
    if (t > 10 && t < b.ret && title <= 0) {
      ctx.font = `${Math.round(h * .022)}px system-ui, sans-serif`; ctx.textAlign = 'right'; ctx.textBaseline = 'alphabetic';
      ctx.fillStyle = 'rgba(255,255,255,.45)'; ctx.fillText('Hold Sneak to skip', w - h * .03, h - h * .11 * s.bars - h * .02);
    }
    if (state.cues) {
      const now = track.cues.filter(c => c.cue !== 'wingbeat' && t >= c.tick && t < c.tick + 18);
      ctx.font = `${Math.round(h * .024)}px system-ui, sans-serif`; ctx.textAlign = 'left'; ctx.textBaseline = 'alphabetic';
      now.forEach((c, i) => { ctx.fillStyle = `rgba(255,214,140,${.9 * (1 - (t - c.tick) / 18)})`; ctx.fillText('♪ ' + c.cue.replace('_', ' '), h * .03, h - h * .11 * s.bars - h * .025 - i * h * .032); });
    }
  }
  const shot = SHOT_NAMES[SHOTS.map(([, beat, plus]) => (typeof beat === 'number' ? beat : track.beats[beat])).filter(x => x <= t).length - 1];
  ctx.font = `${Math.round(h * .02)}px system-ui, sans-serif`; ctx.textAlign = 'left'; ctx.textBaseline = 'top'; ctx.fillStyle = 'rgba(255,140,110,.9)';
  ctx.fillText(`${state.mode === 'scene' ? 'scene camera' : 'orbit'} · ${s.half ? 'summit' : 'aerie'} · shot: ${shot} · fov ${s.fov.toFixed(0)}`, h * .02, h * .015);
}

// ---------- Controls ----------
function syncPlay() { $('#play').textContent = state.playing ? '❚❚' : '▶'; $('#play').setAttribute('aria-label', state.playing ? 'Pause' : 'Play'); }
function setMode(mode) { state.mode = mode; $('#cam-scene').classList.toggle('active', mode === 'scene'); $('#cam-orbit').classList.toggle('active', mode === 'orbit'); }
$('#play').onclick = () => { if (state.tick >= track.beats.length) state.tick = 0; state.playing = !state.playing; syncPlay(); };
$('#timeline').oninput = e => { state.tick = +e.target.value; state.playing = false; syncPlay(); };
$('#speed').onchange = e => state.speed = +e.target.value;
$('#element').onchange = e => state.element = e.target.value;
$('#skin').onchange = e => state.skin = e.target.value;
$('#cam-scene').onclick = () => setMode('scene');
$('#cam-orbit').onclick = () => setMode('orbit');
$('#bars').onclick = () => { state.bars = !state.bars; $('#bars').classList.toggle('active', state.bars); };
$('#cues').onclick = () => { state.cues = !state.cues; $('#cues').classList.toggle('active', state.cues); };
addEventListener('keydown', e => {
  if (e.target.tagName === 'SELECT') return;
  if (e.code === 'Space') { e.preventDefault(); $('#play').click(); }
  if (e.code === 'ArrowRight' || e.code === 'ArrowLeft') { state.playing = false; syncPlay(); state.tick = clamp(Math.round(state.tick) + (e.code === 'ArrowRight' ? 1 : -1), 0, track.beats.length); }
  if (e.code === 'KeyC') setMode(state.mode === 'scene' ? 'orbit' : 'scene');
});
let drag = null;
canvas.parentElement.addEventListener('pointerdown', e => { if (state.mode === 'orbit') drag = [e.clientX, e.clientY]; });
addEventListener('pointerup', () => drag = null);
addEventListener('pointermove', e => {
  if (!drag) return;
  state.orbit.yaw -= (e.clientX - drag[0]) * .008; state.orbit.pitch = clamp(state.orbit.pitch + (e.clientY - drag[1]) * .008, -1.4, 1.5);
  drag = [e.clientX, e.clientY];
});
canvas.parentElement.addEventListener('wheel', e => { if (state.mode !== 'orbit') return; e.preventDefault(); state.orbit.dist = clamp(state.orbit.dist * Math.exp(e.deltaY * .001), 2, 120); }, {passive: false});

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
  if (params.has('element')) { state.element = params.get('element'); $('#element').value = state.element; }
  requestAnimationFrame(frame);
}).catch(e => { $('#error').textContent = e.stack || String(e); });
// For headless review: render one tick and report what is on screen.
window.renderAt = (tick, mode) => { state.tick = tick; state.playing = false; syncPlay(); if (mode) setMode(mode); draw(); return sample(tick); };
// For review: a contact sheet of ticks, drawn over the page (call sheet() with no ticks to hide it).
window.sheet = (ticks, cols, mode, before) => {
  let c = document.getElementById('sheet');
  if (!ticks) { if (c) c.remove(); return; }
  if (!c) { c = document.createElement('canvas'); c.id = 'sheet'; c.style.cssText = 'position:fixed;inset:0;width:100vw;height:100vh;z-index:10;background:#000'; document.body.append(c); }
  c.width = innerWidth; c.height = innerHeight;
  const g = c.getContext('2d'), rows = Math.ceil(ticks.length / cols), tw = c.width / cols, th = Math.min(c.height / rows, tw * 9 / 16);
  ticks.forEach((t, i) => {
    if (before) before(i);
    renderAt(t, mode || 'scene');
    const x = (i % cols) * tw, y = Math.floor(i / cols) * th;
    g.drawImage(canvas, x, y, tw - 2, th - 2); g.drawImage(hud, x, y, tw - 2, th - 2);
    g.fillStyle = '#000a'; g.fillRect(x, y + th - 18, 60, 16); g.fillStyle = '#ffd'; g.font = '12px system-ui'; g.fillText('t ' + t, x + 4, y + th - 6);
  });
};
