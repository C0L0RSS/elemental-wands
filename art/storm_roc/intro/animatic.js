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
let SHOTS = [['1 · quiet', 0], ['2 · caw', 'caw'], ['3 · snatch', 'snatch'], ['4 · carried', 'grip', 8], ['5 · reveal', 'look_up'], ['6 · clouds', 'cloud'],
  ['7 · struggle', 'summit'], ['8 · throw', 'throw'], ['9 · landing', 'ground'], ['10 · hand back', 'back']];
let SHOT_NAMES = ['quiet', 'caw', 'snatch', 'carried off', 'the reveal', 'storm clouds', 'the struggle', 'the throw', 'the landing', 'hand back'];
const state = {tick: 0, playing: true, speed: 1, mode: 'scene', bars: true, cues: true, element: 'wind', skin: 'steve', orbit: {yaw: rad(35), pitch: rad(12), dist: 9}};
let track, roc, player, playerSlim, gong, nature, skins = {}, progs = {}, meshes = {}, scenery = {};
/** An element's landing plugs in here from its own script (track.landing.script, written by
 * art/storm_roc/intro/<element>_landing.py), which sets any of: load(json, image) to fetch its
 * props; solids(s, t, view) returning solid-layout geometry to draw lit and opaque; translucent(s,
 * t, view) to draw its own clouds and light; hud(t, s, w, h) to draw over the frame. view holds
 * tip (the wand's tip), mats and model (the posed player's bones), and in translucent also eye,
 * right, up, vp, fog and fogRange. Its track columns all reach s by name. Nature's landing is
 * built into this file instead (track.nature). */
const LANDING = {};

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
  if (track.nature) { // an element's own landing (art/storm_roc/intro/nature_landing.py): its props and shots
    const m = await json('springbloom.json');
    nature = {pad: m.springbloom_pad, wilt: m.springbloom_wilt, pod: m.springbloom_pod};
    progs.glow = program(PUFF_VS, GLOW_FS); meshes.glow = vao(progs.glow, [['centre', 3], ['corner', 2], ['col', 4]], 9);
    SHOTS = track.nature.shots; SHOT_NAMES = track.nature.shot_names;
    state.element = 'nature'; $('#element').value = 'nature';
  }
  if (track.landing) {
    await new Promise((ok, no) => { const el = document.createElement('script'); el.src = track.landing.script + bust; el.onload = ok;
      el.onerror = () => no(Error(track.landing.script + ' did not load')); document.head.append(el); });
    progs.glow = program(PUFF_VS, GLOW_FS); meshes.glow = vao(progs.glow, [['centre', 3], ['corner', 2], ['col', 4]], 9);
    if (track.landing.shots) { SHOTS = track.landing.shots; SHOT_NAMES = track.landing.shot_names; }
    state.element = track.landing.element; $('#element').value = state.element;
    if (LANDING.load) await LANDING.load(json, image);
  }
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
  for (const k of Object.keys(T)) if (!(k in out) && k !== 'roc_head') out[k] = pick(k);  // a landing's own columns
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
  gl.uniform1f(progs.sky.u.time, fxTime(t) / 20); gl.uniform3fv(progs.sky.u.fog, fog);
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
  const natTip = M.apply(M.mul(wand, M.mul(M.t(...pup.model.bones[pup.model.index.get('wand')].pivot), M.rx(rad(-70)))), [0, 0, .72]);
  const landView = {tip: natTip, mats: pMats, model: pup.model};
  if (nature && half === 1) {
    const nd = natureSolids(s, t, natTip);
    gl.useProgram(solid.p); upload(meshes.solid, nd, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.solid.va); gl.drawArrays(gl.TRIANGLES, 0, nd.length / 10);
  }
  if (LANDING.solids && half === 1) {
    const ld = LANDING.solids(s, t, landView);
    if (ld && ld.length) { gl.useProgram(solid.p); upload(meshes.solid, ld, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.solid.va); gl.drawArrays(gl.TRIANGLES, 0, ld.length / 10); }
  }

  // Translucent: the storm cap, clouds, lightning, the wand's flash.
  gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); gl.depthMask(false);
  gl.useProgram(solid.p);
  if (half === 1) { gl.uniform1f(solid.u.light, 1 + 2 * s.flash); gl.bindVertexArray(scenery.cap.va); gl.drawArrays(gl.TRIANGLES, 0, scenery.cap.count / 10); }
  drawPuffs(half, eye, right, up, vp, fog, fogRange, s, t);
  drawLightning(t, half, eye, vp, fog);
  if (nature && half === 1) { natureSnow(s, eye, right, up, vp, fog, fogRange); natureTrail(s, t, eye); natureGlow(s, t, natTip, eye, right, up, vp); }
  if (LANDING.translucent && half === 1) {
    LANDING.translucent(s, t, {...landView, eye, right, up, vp, fog, fogRange});
    gl.useProgram(solid.p); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); gl.depthMask(false); gl.enable(gl.BLEND);
  }
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
  const ft = fxTime(t), drift = half === 1 ? [ft / 20 * 2.2, 0, ft / 20 * 1.0] : [ft / 20 * 3, -ft / 20 * 4, 0];
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
    const age = fxAge(t, e.tick);
    if (e.half !== half || age < 0 || age > 6 || !e.lines.length) continue;
    const a = (1 - age / 6) * (age % 2 < 1 ? 1 : .55);
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

// ---------- An element's landing: the Springbloom, the vine, the slide (nature_landing.py) ----------
/** A track column at a fractional tick, ignoring cuts. */
function colAt(k, t) {
  const C = track.ticks[k], n = C.length - 1, a = clamp(Math.floor(t), 0, n), b = Math.min(n, a + 1), f = clamp(t - a, 0, 1);
  return typeof C[a] === 'number' ? C[a] + (C[b] - C[a]) * f : lerp(C[a], C[b], f);
}
/** Effects age on the story clock, so they slow down with the slow motion. */
const fxTime = t => track && track.ticks.n_clock ? colAt('n_clock', t) : t;
const fxAge = (t, tick) => fxTime(t) - fxTime(tick);
const backEase = s => 1 + 2.70158 * Math.pow(s - 1, 3) + 1.70158 * Math.pow(s - 1, 2);
const CORNERS = [[-1,-1],[1,-1],[1,1],[-1,-1],[1,1],[-1,1]];
const GLOW_FS = `#version 300 es
precision highp float; in vec2 vC; in vec4 vCol; in vec3 vPos; out vec4 color; uniform float opacity; uniform vec3 eye;
void main(){ float r = length(vC); if (r > 1.) discard; float a = pow(1. - r, 2.2) * opacity * smoothstep(.6, 3.5, distance(vPos, eye)); color = vec4(vCol.rgb * a, a); }`;
/** A mesh in the solid layout (pos, normal, rgba) moved by m (normals by n) into data. */
function placeMesh(data, src, m, n) {
  for (let i = 0; i < src.length; i += 10) {
    data.push(...M.apply(m, [src[i], src[i + 1], src[i + 2]]), ...norm(M.dir(n || m, [src[i + 3], src[i + 4], src[i + 5]])), src[i + 6], src[i + 7], src[i + 8], src[i + 9]);
  }
}
/** A frame at a whose +Z runs toward b. */
function along(a, b) {
  const d = norm(sub(b, a)); let x = cross([0, 1, 0], d); if (Math.hypot(...x) < .05) x = cross([1, 0, 0], d); x = norm(x); const y = cross(d, x);
  return new Float32Array([...x, 0, ...y, 0, ...d, 0, ...a, 1]);
}
function beam(data, a, b, w, col) {
  const len = Math.hypot(...sub(b, a)); if (len < 1e-4) return;
  box(data, [-w / 2, -w / 2, -.01], [w / 2, w / 2, len + .01], col, along(a, b));
}
function leaf(data, a, b, w, col) {
  const len = Math.hypot(...sub(b, a)); if (len < .01) return;
  box(data, [-w / 2, -.012, 0], [w / 2, .012, len], col, along(a, b));
}
/** A flat disc on the snow, its radius by angle. */
function fan(data, c, y, radius, col, n) {
  for (let i = 0; i < n; i++) {
    const a0 = i / n * Math.PI * 2, a1 = (i + 1) / n * Math.PI * 2, r0 = radius(a0), r1 = radius(a1);
    for (const p of [[c[0], y, c[2]], [c[0] + Math.cos(a1) * r1, y, c[2] + Math.sin(a1) * r1], [c[0] + Math.cos(a0) * r0, y, c[2] + Math.sin(a0) * r0]]) data.push(...p, 0, 1, 0, ...col, 1);
  }
}
const VINE = [[.47, .89, .19], [.18, .52, .1], [.19, .72, .13]];  // Thornbite's three strands
/** Thornbite's braid at a grapple's scale: three strands from the wand's tip to the head, slack
 * and whipping as it flies, pulled straight once it bites; thorns along it, and leaves that sprout
 * up it from the bite toward the wand. */
function vine(data, root, end, ft, taut, size, leafing) {
  const L = Math.hypot(...sub(end, root)); if (L < .05) return;
  const axis = norm(sub(end, root)); let side = cross(axis, [0, 1, 0]); if (Math.hypot(...side) < .1) side = [1, 0, 0]; side = norm(side); const up = norm(cross(side, axis));
  const n = Math.max(28, Math.round(L * 7));
  const P = (u, k) => {
    const ph = u * Math.PI * (4 + L * .9) + k * Math.PI * 2 / 3, r = .043 * size * (.5 + .5 * Math.sin(Math.PI * Math.min(1, u * 3)));
    const sag = (1 - taut) * Math.sin(Math.PI * u) * (.18 * size + .35 * Math.sin(u * 9 - ft * .8));
    const sway = (1 - taut) * .2 * Math.sin(u * 7 + ft * .6) * Math.sin(Math.PI * u);
    const thrum = taut * .025 * Math.sin(ft * 3.1 + u * 20) * Math.sin(Math.PI * u);  // humming under the strain
    return add(add(lerp(root, end, u), scale(up, sag + thrum + Math.sin(ph) * r)), scale(side, Math.cos(ph) * r + sway));
  };
  for (let k = 0; k < 3; k++) for (let i = 0; i < n; i++) {
    const u = i / n, a = P(u, k), b = P((i + 1) / n, k);
    beam(data, a, b, (.043 - u * .012) * size, VINE[k]);
    if (k === 1 && i % 5 === 2) beam(data, a, add(a, add(scale(side, (i % 2 ? 1 : -1) * .13 * size), scale(up, .06 * size))), .028 * size, [.13, .32, .07]);
  }
  for (let i = 2; i < n - 1; i += 3) {
    const u = i / n, grow = clamp((leafing * 1.5 - (1 - u)) * 3, 0, 1); if (grow <= 0) continue;
    const a = P(u, 0), sgn = i % 2 ? 1 : -1, g = backEase(grow);
    leaf(data, a, add(a, add(add(scale(side, sgn * .3 * size * g), scale(up, .12 * size * g)), scale(axis, -.08 * size * g))), .13 * size * g, i % 4 < 2 ? VINE[0] : VINE[2]);
  }
}
/** The flytrap on the vine's end: jaws open in flight, shut and half buried once it bites. */
function trap(data, at, dir, open, size) {
  const m = M.mul(along(at, add(at, dir)), M.s(size, size, size)), jaw = [.18, .52, .1], inner = [.78, .2, .24], tooth = [.95, .93, .8];
  box(data, [-.1, -.1, -.22], [.1, .1, .02], [.15, .42, .08], m);
  for (const sgn of [1, -1]) {
    const j = M.mul(m, M.rx(-sgn * rad(open)));
    box(data, [-.16, sgn > 0 ? 0 : -.06, 0], [.16, sgn > 0 ? .06 : 0, .34], jaw, j);
    box(data, [-.13, sgn > 0 ? -.01 : 0, .02], [.13, sgn > 0 ? 0 : .01, .3], inner, j);
    for (let i = 0; i < 4; i++) box(data, [-.15 + i * .1, sgn > 0 ? -.05 : 0, .3], [-.12 + i * .1, sgn > 0 ? 0 : .05, .38], tooth, j);
  }
}
const PETALS = [[1, .55, .72], [1, .97, .92], [1, .84, .22], [.74, .56, 1], [1, .45, .36], [1, .66, .82]];
/** Grass blades in a tuft at base, grown by e. */
function tuft(data, base, a, e, i, ft) {
  for (let j = 0; j < 3; j++) {
    const h = (.26 + hash(i * 7 + j) * .34) * e, lean = (hash(i * 11 + j) - .5) * .6 + .12 * Math.sin(ft * .07 + i);
    beam(data, add(base, [(j - 1) * .05, 0, (hash(i + j) - .5) * .08]), add(base, [Math.cos(a + j - 1) * lean * h, h, Math.sin(a + j - 1) * lean * h]), .045,
      [[.3, .62, .16], [.42, .78, .2], [.24, .5, .12]][j]);
  }
}
function flower(data, base, e, i, ft) {
  const h = (.3 + hash(i * 2.3) * .38) * e, top = add(base, [.06 * Math.sin(ft * .08 + i), h, .06 * Math.cos(ft * .07 + i * 1.3)]);
  beam(data, base, top, .035, [.25, .55, .14]);
  leaf(data, add(base, [0, h * .35, 0]), add(base, [.14 * e * Math.cos(i), h * .5, .14 * e * Math.sin(i)]), .06 * e, VINE[0]);
  const col = PETALS[i % PETALS.length], z = .08 * e, m = [M.t(...top), M.ry(hash(i) * 6.3 + ft * .01), M.rz(.3 * Math.sin(i))].reduce(M.mul);
  for (const [dx, dz] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) box(data, [dx * z * 1.15 - z * .7, -.014, dz * z * 1.15 - z * .7], [dx * z * 1.15 + z * .7, .014, dz * z * 1.15 + z * .7], col, m);
  box(data, [-z * .5, -.01, -z * .5], [z * .5, .035, z * .5], [1, .8, .18], m);
}
/** Where the slide has your feet at story clock ft, and when it passed fraction v of its length. */
function slidePoint(sl, ft) { const u = clamp((ft - sl.c0) / (sl.c1 - sl.c0), 0, 1); return lerp(sl.from, sl.to, 1 - Math.pow(1 - u, 3)); }
function slidePassed(sl, v) { return sl.c0 + (1 - Math.cbrt(1 - clamp(v, 0, .9999))) * (sl.c1 - sl.c0); }
/** The slide's mark: a green track through the snow behind you, grass and flowers springing up
 * along it and spreading out round where you stop, snow sprayed off your feet, a few petals. */
function slideField(data, sl, ft, ground) {
  if (ft < sl.c0) return;
  const dir = norm(sub(sl.to, sl.from)), side = norm(cross(dir, [0, 1, 0]));
  const e = 1 - Math.pow(1 - clamp((ft - sl.c0) / (sl.c1 - sl.c0), 0, 1), 3), at = v => lerp(sl.from, sl.to, v);
  const width = v => { const age = ft - slidePassed(sl, Math.max(0, v)); return (.3 + 1.2 * (1 - Math.exp(-age / 10))) * (.85 + .15 * Math.sin(v * 17)) * clamp((v + .12) / .1, 0, 1); };
  for (let i = 0; i < 32; i++) {
    const v0 = -.12 + (e + .12) * i / 32, v1 = -.12 + (e + .12) * (i + 1) / 32, a = at(v0), b = at(v1), wa = width(v0), wb = width(v1);
    const q = [add(a, scale(side, -wa)), add(a, scale(side, wa)), add(b, scale(side, wb)), add(b, scale(side, -wb))].map(x => [x[0], ground + .02, x[2]]);
    for (const k of [0, 2, 1, 0, 3, 2]) data.push(...q[k], 0, 1, 0, .36, .6, .21, 1);
  }
  const spread = 3.2 * (1 - Math.pow(1 - clamp((ft - sl.c1 + 3) / 34, 0, 1), 3));
  if (spread > .1) fan(data, sl.to, ground + .021, a => spread * (.82 + .12 * Math.sin(a * 6 + 1) + .06 * Math.sin(a * 11)), [.36, .6, .21], 56);
  for (let i = 0; i < 120; i++) {
    const v = -.1 + 1.1 * hash(i * 2.37), off = (hash(i * 5.1) - .5) * 3; if (v > e) continue;
    const g = clamp((ft - slidePassed(sl, Math.max(0, v)) - Math.abs(off) * 3) / 5, 0, 1); if (g <= 0) continue;
    const base = add(at(v), scale(side, off)); base[1] = ground;
    if (i % 4) tuft(data, base, hash(i) * 6.3, backEase(g), i, ft); else flower(data, base, backEase(g), i, ft);
  }
  for (let i = 0; i < 80; i++) {
    const a = hash(i * 3.71 + 9) * Math.PI * 2, r = .9 + 2.4 * Math.sqrt(hash(i * 6.13 + 4)), g = clamp((spread - r) / .8, 0, 1); if (g <= 0) continue;
    const base = [sl.to[0] + Math.cos(a) * r, ground, sl.to[2] + Math.sin(a) * r];
    if (i % 3) tuft(data, base, a, backEase(g), i + 300, ft); else flower(data, base, backEase(g), i + 300, ft);
  }
  for (let k = 0; k < 36; k++) {  // snow sprayed out from your feet, less as you slow
    const tk = sl.c0 + k * (sl.c1 - sl.c0) / 36, age = ft - tk; if (age < 0 || age > 22) continue;
    const power = Math.pow(1 - k / 36, 1.5), origin = slidePoint(sl, tk), sec = age / 20;
    for (let j = 0; j < 4; j++) {
      const r = q => hash(k * 13.1 + j * 3.7 + q), sgn = j % 2 ? 1 : -1;
      const vel = add(add(scale(side, sgn * (2 + 3 * r(1)) * power), scale(dir, (2 + 3 * r(2)) * power)), [0, (2 + 4 * r(3)) * power, 0]);
      const p = add(origin, scale(vel, sec)); p[1] = Math.max(ground + .04, ground + .1 + vel[1] * sec - 5 * sec * sec);
      const z = (.05 + .06 * r(4)) * (1 - age / 22); if (z < .005) continue;
      box(data, [-z, -z * .8, -z], [z, z * .8, z], j % 3 ? [.93, .95, 1] : [.85, .88, .94], M.mul(M.t(...p), M.ry(r(5) * 6 + age * .2)));
    }
  }
  for (let i = 0; i < 24; i++) {  // a few petals lifted off the new flowers, drifting
    const age = ft - sl.c1 - hash(i * 4.4) * 16; if (age < 0) continue;
    const z = .045 * clamp(age / 4, 0, 1) * (1 - smooth((age - 80) / 30)); if (z < .004) continue;
    const p = [sl.to[0] + (hash(i * 1.9) - .5) * 5 + age * .012, ground + .2 + age * (.006 + .01 * hash(i * 2.6)) + .15 * Math.sin(age * .1 + i), sl.to[2] + (hash(i * 3.1) - .2) * 5];
    box(data, [-z, -.01, -z * .6], [z, .01, z * .6], PETALS[i % PETALS.length], [M.t(...p), M.ry(age * .05 + i), M.rx(age * .06 + i)].reduce(M.mul));
  }
}
function natureSolids(s, t, tip) {
  const data = [], N = track.nature, pad = N.pad, ground = track.summit.arrival[1], ft = s.n_clock;
  const burst = kind => N.bursts.find(b => b.kind === kind);
  // The Springbloom: popping open, dipping under you and springing back past full; wilting later.
  if (s.n_open > 0 && s.n_wilt < 1) {
    const o = s.n_open, w = s.n_wilt, k = .35 + .65 * o, sq = s.n_squash, wide = 1 / Math.sqrt(Math.max(.3, sq));
    const m = M.mul(M.t(pad[0], pad[1] - .25 * w, pad[2]), M.s(k * wide, Math.max(.02, o) * sq * (1 - .85 * w), k * wide));
    placeMesh(data, w > 0 ? nature.wilt : nature.pad, m, M.id());
  }
  // Grass bursting up through the snow round it as it opens.
  const grass = burst('grass'), ga = ft - grass.clock;
  if (ga >= 0) for (let i = 0; i < 26; i++) {
    const a = i / 26 * Math.PI * 2 + hash(i) * .3, r = 1.2 + 1.4 * hash(i * 2.1), g = clamp((ga - r * 1.2) / 3, 0, 1); if (g <= 0) continue;
    const base = [pad[0] + Math.cos(a) * r, ground, pad[2] + Math.sin(a) * r];
    if (i % 3) tuft(data, base, a, backEase(g) * (1 - .6 * s.n_wilt), i + 500, ft); else flower(data, base, backEase(g) * (1 - .6 * s.n_wilt), i + 500, ft);
  }
  // The pod, spinning down off the wand.
  if (s.n_pod_on > .5) {
    const spin = M.mul(M.ry(t * .55), M.rx(t * .8));
    placeMesh(data, nature.pod, [M.t(...s.n_pod), spin, M.s(1.5, 1.5, 1.5)].reduce(M.mul), spin);
  }
  // Leaves spiralling in to the wand's tip as it charges.
  if (s.n_glow > .12 && s.n_vine < .5) for (let k = 0; k < 10; k++) {
    const ph = (ft * .05 + k / 10) % 1, r = .1 + 1.1 * (1 - ph), a = k * 2.4 + ft * .3 + ph * 5;
    const c = add(tip, [Math.cos(a) * r, Math.sin(a * 1.3) * r * .45, Math.sin(a) * r]), z = .07 * s.n_glow * Math.sin(Math.PI * ph);
    if (z > .004) box(data, [-z, -z * .2, -z * 1.7], [z, z * .2, z * 1.7], k % 2 ? VINE[0] : VINE[2], [M.t(...c), M.ry(a), M.rx(a * .7)].reduce(M.mul));
  }
  // The vine and its flytrap.
  if (s.n_vine > .02) {
    const size = 1.7 * Math.min(1, s.n_vine * 1.3), dir = norm(sub(s.n_tip, tip));
    const bitten = s.n_taut > 0 || (t > track.beats.latch && t < track.beats.release + 1), head = bitten ? add(s.n_tip, [0, .12, 0]) : s.n_tip;
    const neck = sub(head, scale(bitten ? [0, -1, 0] : dir, .2 * size));
    vine(data, tip, neck, ft, s.n_taut, size, s.n_leaf);
    trap(data, neck, bitten ? [dir[0] * .3, -1, dir[2] * .3] : dir, bitten ? 4 : 38, size * .9);
  }
  // The bite: roots splayed out into the snow, the snow cracked round it.
  const bite = burst('bite'), ba = ft - bite.clock;
  if (ba >= 0) {
    const grow = 1 - Math.pow(1 - clamp(ba / 10, 0, 1), 3), a0 = bite.at;
    for (let k = 0; k < 8; k++) {
      const ang = k / 8 * Math.PI * 2 + hash(k) * .6, len = (1 + hash(k + 5) * 1.2) * grow;
      let p = [a0[0], ground + .03, a0[2]];
      for (let j = 0; j < 5; j++) {
        const q = add(p, [Math.cos(ang + (hash(k * 4 + j) - .5) * .9) * len / 5, 0, Math.sin(ang + (hash(k * 4 + j) - .5) * .9) * len / 5]);
        beam(data, p, q, .1 * (1 - j * .16), j % 2 ? [.24, .4, .11] : [.18, .52, .1]); p = q;
      }
    }
  }
  // The slide: the green track, the grass and the flowers, the spray.
  slideField(data, N.slide, ft, ground);
  // Kernels, leaves and snow chunks thrown out by each burst.
  for (const b of N.bursts) {
    const kind = BURSTS[b.kind]; if (!kind) continue;
    const age = ft - b.clock; if (age < 0 || age > kind.life) continue;
    const sec = age / 20;
    for (let i = 0; i < kind.n; i++) {
      const r = k => hash(b.clock * 1.37 + i * 7.1 + k * 3.3), a = r(0) * Math.PI * 2, out = kind.out[0] + r(1) * (kind.out[1] - kind.out[0]), upv = kind.up[0] + r(2) * (kind.up[1] - kind.up[0]);
      const p = [b.at[0] + Math.cos(a) * (.2 + out * sec), Math.max(ground + .04, b.at[1] + upv * sec - kind.g * sec * sec / 2), b.at[2] + Math.sin(a) * (.2 + out * sec)];
      const piece = kind.pieces[Math.floor(r(4) * kind.pieces.length)], fade = 1 - smooth((age - kind.life * .6) / (kind.life * .4)), z = piece.size * fade;
      if (z < .005) continue;
      const m = [M.t(...p), M.ry(a + age * .3 * (r(5) - .5)), M.rx(age * .25 * (r(6) - .5))].reduce(M.mul);
      box(data, [-z, -z * piece.flat, -z * .7], [z, z * piece.flat, z * .7], piece.col, m);
    }
  }
  return data;
}
const KERNEL = {col: [1, .8, .15], size: .05, flat: 1}, GOLD = {col: [.93, .61, .08], size: .045, flat: 1}, LEAF = {col: [.47, .89, .19], size: .11, flat: .2},
  DARK_LEAF = {col: [.19, .72, .13], size: .1, flat: .2}, SNOW = {col: [.93, .95, 1], size: .07, flat: .8}, CHUNK = {col: [.85, .88, .94], size: .13, flat: .8},
  PINK = {col: [1, .55, .72], size: .08, flat: .15}, CREAM = {col: [1, .97, .92], size: .08, flat: .15};
const BURSTS = {
  pollen: {n: 40, life: 36, out: [1.2, 3.6], up: [3, 6.5], g: 9, pieces: [KERNEL, KERNEL, GOLD, LEAF, DARK_LEAF, PINK]},
  bounce: {n: 30, life: 32, out: [2, 5], up: [2, 5], g: 9, pieces: [KERNEL, GOLD, LEAF, LEAF, DARK_LEAF, PINK, CREAM]},
  launch: {n: 40, life: 34, out: [.5, 2], up: [8, 14], g: 14, pieces: [KERNEL, GOLD, LEAF, PINK]},
  bite: {n: 34, life: 30, out: [1, 3.4], up: [2, 6], g: 10, pieces: [SNOW, SNOW, CHUNK, LEAF]},
};
/** Soft clouds: the snow ring the landing blows out, the puff where the vine bites, the bounce's pollen. */
function natureSnow(s, eye, right, up, vp, fog, fogRange) {
  const prog = progs.puff, ground = track.summit.arrival[1], ft = s.n_clock;
  gl.useProgram(prog.p); gl.uniformMatrix4fv(prog.u.viewProj, false, vp); gl.uniform3fv(prog.u.right, right); gl.uniform3fv(prog.u.up, up);
  gl.uniform3fv(prog.u.eye, eye); gl.uniform3fv(prog.u.fog, fog); gl.uniform2fv(prog.u.fogRange, fogRange);
  for (const b of track.nature.bursts) {
    const ring = {bite: [12, 34, 2.4, .8], bounce: [16, 30, 3.2, .7], launch: [8, 20, 1.4, .55]}[b.kind]; if (!ring) continue;
    const age = ft - b.clock; if (age < 0 || age > ring[1]) continue;
    const d = [], spread = 1 - Math.exp(-age / 6);
    for (let i = 0; i < ring[0]; i++) {
      const a = i / ring[0] * Math.PI * 2 + hash(i + b.clock) * .4, r = .4 + ring[2] * spread * (.75 + .5 * hash(i * 3 + b.clock));
      const c = [b.at[0] + Math.cos(a) * r, ground + .3 + ring[3] * .4 * spread * hash(i * 5), b.at[2] + Math.sin(a) * r];
      const col = b.kind === 'bounce' || b.kind === 'launch' ? [1, .92, .55] : [.96, .97, 1];
      for (const [x, y] of CORNERS) d.push(...c, x, y, ...col, ring[3] * (.6 + .9 * spread));
    }
    gl.uniform1f(prog.u.opacity, (b.kind === 'bounce' || b.kind === 'launch' ? .4 : .7) * (1 - smooth(age / ring[1])));
    upload(meshes.puff, d, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.puff.va); gl.drawArrays(gl.TRIANGLES, 0, d.length / 9);
  }
  const sl = track.nature.slide, d = [];
  for (let k = 0; k < 10; k++) {
    const tk = sl.c0 + k * (sl.c1 - sl.c0) / 10, age = ft - tk; if (age < 0 || age > 30) continue;
    const p = slidePoint(sl, tk), power = 1 - k / 16, size = (.3 + .7 * (1 - Math.exp(-age / 6))) * power * (1 - age / 30);
    for (const sgn of [-1, 1]) for (const [x, y] of CORNERS) d.push(p[0] + sgn * (.4 + age * .03), ground + .25 + age * .012, p[2] + hash(k) * .3, x, y, 1, 1, 1, size);
  }
  if (d.length) { gl.uniform1f(prog.u.opacity, .35); upload(meshes.puff, d, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.puff.va); gl.drawArrays(gl.TRIANGLES, 0, d.length / 9); }
}
/** The wand's light trail through the pitch, the shot and the flip. */
function natureTrail(s, t, eye) {
  if (s.n_trail < .02) return;
  // Carried along with you, so it trails the wand's swing, not your fall.
  const here = s.player.pos, pts = [];
  for (let k = 0; k <= 16; k++) pts.push(add(sub(colAt('n_wtip', t - k * .5), colAt('player', t - k * .5).slice(0, 3)), here));
  const data = [];
  for (let k = 0; k < 16; k++) {
    const p = pts[k], q = pts[k + 1], d = sub(q, p); if (Math.hypot(...d) < 1e-3) continue;
    const side = norm(cross(norm(d), norm(sub(eye, p)))), w0 = .16 * (1 - k / 16), w1 = .16 * (1 - (k + 1) / 16), a = s.n_trail * (1 - k / 16);
    quad(data, sub(p, scale(side, w0)), add(p, scale(side, w0)), add(q, scale(side, w1)), sub(q, scale(side, w1)), [.55, 1, .4, a * .85]);
    quad(data, sub(p, scale(side, w0 * .35)), add(p, scale(side, w0 * .35)), add(q, scale(side, w1 * .35)), sub(q, scale(side, w1 * .35)), [1, 1, .85, a * .7]);
  }
  if (!data.length) return;
  const prog = progs.solid;
  gl.useProgram(prog.p); gl.uniform1i(prog.u.flat_, 1); gl.blendFunc(gl.SRC_ALPHA, gl.ONE);
  upload(meshes.solid, data, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.solid.va); gl.drawArrays(gl.TRIANGLES, 0, data.length / 10);
  gl.uniform1i(prog.u.flat_, 0); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
}
/** Light: the wand's charge, the pod's pollen trail, pulses up the taut vine, shock rings. */
function natureGlow(s, t, tip, eye, right, up, vp) {
  const d = [], N = track.nature, ft = s.n_clock, ground = track.summit.arrival[1];
  const spr = (p, col, size, k) => { if (k > .003) for (const [x, y] of CORNERS) d.push(...p, x, y, col[0] * k, col[1] * k, col[2] * k, size); };
  const GREEN = [.5, 1, .35], WHITE = [1, 1, .88], GOLDEN = [1, .82, .3];
  if (s.n_glow > .02) {
    const f = 1 + .12 * Math.sin(ft * 1.9) + .08 * Math.sin(ft * 3.3);
    spr(tip, GREEN, (.25 + 1.1 * s.n_glow) * f, .9 * s.n_glow); spr(tip, WHITE, (.1 + .35 * s.n_glow) * f, s.n_glow);
  }
  for (let k = 1; k <= 14; k++) { const tt = t - k * .5; if (colAt('n_pod_on', tt) < .5) continue; spr(colAt('n_pod', tt), GOLDEN, .45 * (1 - k / 15), .7 * (1 - k / 15)); }
  if (s.n_pod_on > .5) { spr(s.n_pod, GREEN, .9, .8); spr(s.n_pod, WHITE, .3, .7); }
  if (s.n_vine > .02 && s.n_taut > .2) for (let k = 0; k < 4; k++) { const u = 1 - ((ft * .11 + k / 4) % 1); spr(lerp(s.n_tip, tip, 1 - u), GREEN, .45, s.n_taut * Math.sin(Math.PI * u)); }
  for (const b of N.bursts) {
    const ring = {bounce: [6, 3.5, 32, .6], bite: [6, 3, 26, .7], launch: [6, 3.2, 30, .7]}[b.kind]; if (!ring) continue;
    const age = ft - b.clock; if (age < 0 || age > ring[0]) continue;
    const u = age / ring[0], r = ring[1] * (1 - Math.pow(1 - u, 2)), k = (1 - u) * (1 - u);
    for (let i = 0; i < ring[2]; i++) { const a = i / ring[2] * Math.PI * 2; spr([b.at[0] + Math.cos(a) * r, ground + .2, b.at[2] + Math.sin(a) * r], [.65, 1, .5], ring[3], .6 * k); }
  }
  if (s.n_bt > .02) for (let i = 0; i < 60; i++) {  // pollen hanging in the air through the bullet time
    const c = add(s.player.pos, [(hash(i) - .5) * 9, (hash(i + 40) - .35) * 6 + Math.sin(ft * .05 + i) * .2, (hash(i + 120) - .5) * 9]);
    spr(c, i % 3 ? GOLDEN : GREEN, .07 + .05 * hash(i + 7), .7 * s.n_bt);
  }
  if (!d.length) return;
  const prog = progs.glow;
  gl.useProgram(prog.p); gl.uniformMatrix4fv(prog.u.viewProj, false, vp); gl.uniform3fv(prog.u.right, right); gl.uniform3fv(prog.u.up, up); gl.uniform1f(prog.u.opacity, 1); gl.uniform3fv(prog.u.eye, eye);
  gl.blendFunc(gl.ONE, gl.ONE);
  upload(meshes.glow, d, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.glow.va); gl.drawArrays(gl.TRIANGLES, 0, d.length / 9);
  gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
}
/** Over the frame: the bullet time's dark edges, speed lines, the bite's flash. */
function natureHud(t, s, w, h) {
  if (s.n_bt > .01) {
    const g = ctx.createRadialGradient(w / 2, h / 2, h * .3, w / 2, h / 2, Math.hypot(w, h) * .55);
    g.addColorStop(0, 'rgba(6,22,10,0)'); g.addColorStop(1, `rgba(6,22,10,${.65 * s.n_bt})`);
    ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
    ctx.fillStyle = `rgba(150,235,140,${.05 * s.n_bt})`; ctx.fillRect(0, 0, w, h);
  }
  if (s.n_speed > .01) {
    ctx.strokeStyle = `rgba(255,255,255,${.38 * s.n_speed})`; ctx.lineWidth = Math.max(1, h * .0022);
    ctx.beginPath();
    const R = Math.hypot(w, h) / 2;
    for (let i = 0; i < 110; i++) {
      const a = hash(i * 1.3) * Math.PI * 2, ph = (hash(i * 2.7) + t * .08 * (.6 + hash(i * 3.1))) % 1;
      const r0 = R * (.4 + .6 * ph), len = R * (.08 + .18 * hash(i * 4.3)) * s.n_speed;
      ctx.moveTo(w / 2 + Math.cos(a) * r0, h / 2 + Math.sin(a) * r0); ctx.lineTo(w / 2 + Math.cos(a) * (r0 + len), h / 2 + Math.sin(a) * (r0 + len));
    }
    ctx.stroke();
  }
  if (s.n_impact > .01) { ctx.fillStyle = `rgba(240,255,232,${.8 * s.n_impact * s.n_impact})`; ctx.fillRect(0, 0, w, h); }
}
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
    if (nature && s.half === 1) natureHud(t, s, w, h);
    if (LANDING.hud && s.half === 1) LANDING.hud(t, s, w, h);
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
