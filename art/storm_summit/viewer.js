// Storm Summit layout preview: instanced WebGL2 voxels and small models from layouts.json +
// atlas.png, the planned sea of clouds, and the Storm Roc posed for scale. It shows the
// authored layout; Minecraft owns the real lighting, sky and fog.
'use strict';

const $ = s => document.querySelector(s);
const canvas = $('#view');
const gl = canvas.getContext('webgl2', {antialias: true});
if (!gl) document.body.innerHTML = '<p style="padding:24px">This preview needs WebGL2.</p>';

// ---------- math ----------
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
const rad = d => d * Math.PI / 180;
const M = {
  id: () => new Float32Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]),
  perspective(fov, aspect, near, far) { const f = 1 / Math.tan(fov / 2), m = new Float32Array(16); m[0] = f / aspect; m[5] = f; m[10] = (far + near) / (near - far); m[11] = -1; m[14] = 2 * far * near / (near - far); return m; },
  lookAt(e, f) {
    const s = norm(cross(f, [0, 1, 0])), u = cross(s, f);
    return new Float32Array([s[0], u[0], -f[0], 0, s[1], u[1], -f[1], 0, s[2], u[2], -f[2], 0, -dot(s, e), -dot(u, e), dot(f, e), 1]);
  },
  mul(a, b) { const o = new Float32Array(16); for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) { let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k]; o[c * 4 + r] = s; } return o; },
  translate(v) { const m = M.id(); m[12] = v[0]; m[13] = v[1]; m[14] = v[2]; return m; },
  rotate(axis, angle) { const m = M.id(), c = Math.cos(angle), s = Math.sin(angle); if (axis === 0) { m[5] = c; m[6] = s; m[9] = -s; m[10] = c; } if (axis === 1) { m[0] = c; m[2] = -s; m[8] = s; m[10] = c; } if (axis === 2) { m[0] = c; m[1] = s; m[4] = -s; m[5] = c; } return m; },
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

// A bright, clear day high above the clouds.
const SUN = norm([-.35, .82, .45]);
const HORIZON = [.78, .87, .96], ZENITH = [.2, .45, .86];

// ---------- shaders ----------
const FOG_GLSL = `
uniform vec3 eye; uniform float fogEnd; uniform vec3 horizon;
vec3 fogged(vec3 c, vec3 p) { float f = smoothstep(fogEnd * .55, fogEnd, length(p.xz - eye.xz)); return mix(c, horizon, f); }`;
const VOXEL_VS = `#version 300 es
precision highp float; precision highp int;
layout(location=0) in ivec4 inst;
uniform mat4 viewProj;
uniform ivec4 mats[96];
out vec2 vUv; out vec3 vWorld; out float vShade; flat out int vTex;
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
  if (m.x >= 10) p.y *= float(m.x - 10) / 8.;   // snow layers
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
in vec2 vUv; in vec3 vWorld; in float vShade; flat in int vTex;
uniform sampler2D atlas; uniform vec2 atlasSize;
${FOG_GLSL}
out vec4 color;
void main() {
  vec2 tile = vec2(vTex % int(atlasSize.x), vTex / int(atlasSize.x));
  vec4 t = texture(atlas, (tile + clamp(vUv, .002, .998)) / atlasSize);
  if (t.a < .5) discard;
  color = vec4(fogged(t.rgb * vShade, vWorld), 1);
}`;
const MODEL_VS = `#version 300 es
precision highp float;
layout(location=0) in vec3 pos; layout(location=1) in vec2 uv; layout(location=2) in vec2 shadeTile;
uniform mat4 viewProj;
out vec2 vUv; out vec3 vWorld; out float vShade; flat out int vTex;
void main() { vWorld = pos / 4096.; vUv = uv / 4096.; vShade = shadeTile.x / 255.; vTex = int(shadeTile.y); gl_Position = viewProj * vec4(vWorld, 1); }`;
const ROC_VS = `#version 300 es
precision highp float;
layout(location=0) in vec3 pos; layout(location=1) in vec3 normal; layout(location=2) in vec2 uv;
uniform mat4 viewProj; uniform mat4 model;
out vec3 vN; out vec2 vUv; out vec3 vWorld;
void main() { vec4 w = model * vec4(pos, 1); vWorld = w.xyz; vN = mat3(model) * normal; vUv = uv; gl_Position = viewProj * w; }`;
const ROC_FS = `#version 300 es
precision highp float;
in vec3 vN; in vec2 vUv; in vec3 vWorld; uniform sampler2D tex; uniform vec3 sun;
${FOG_GLSL}
out vec4 color;
void main() {
  vec4 p = texture(tex, vUv); if (p.a < .5) discard;
  float shade = .58 + .42 * max(0., dot(normalize(vN), sun));
  color = vec4(fogged(p.rgb * shade, vWorld), 1);
}`;
const SKY_VS = `#version 300 es
out vec2 ndc; void main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2) * 2. - 1.; ndc = p; gl_Position = vec4(p, .9999, 1); }`;
const SKY_FS = `#version 300 es
precision highp float;
in vec2 ndc; uniform mat4 invViewProj; uniform vec3 horizon; uniform vec3 zenith; uniform vec3 sun; out vec4 color;
void main() {
  vec4 a = invViewProj * vec4(ndc, -1, 1), b = invViewProj * vec4(ndc, 1, 1);
  vec3 dir = normalize(b.xyz / b.w - a.xyz / a.w);
  vec3 c = mix(horizon, zenith, pow(smoothstep(-.02, .75, dir.y), .7));
  float s = dot(dir, sun);
  c += vec3(1., .95, .8) * (smoothstep(.9985, .9992, s) * 1.2 + pow(max(s, 0.), 64.) * .25);
  color = vec4(c, 1);
}`;
// The sea of clouds: one plane at the cloud top, shaded from a drifting height field.
const CLOUD_VS = `#version 300 es
precision highp float;
uniform mat4 viewProj; uniform float cloudY; uniform vec3 eye; out vec3 vWorld;
void main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2) * 2. - 1.;
  vec2 q = vec2(gl_VertexID == 1 || gl_VertexID == 2 || gl_VertexID == 4 ? 1. : -1., gl_VertexID >= 2 && gl_VertexID != 3 ? 1. : -1.);
  vWorld = vec3(eye.x + q.x * 6000., cloudY, eye.z + q.y * 6000.); gl_Position = viewProj * vec4(vWorld, 1); }`;
const CLOUD_FS = `#version 300 es
precision highp float;
in vec3 vWorld; uniform float time; uniform vec3 sun; uniform float layer;
${FOG_GLSL}
out vec4 color;
float h(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3. - 2. * f);
  return mix(mix(h(i), h(i + vec2(1, 0)), f.x), mix(h(i + vec2(0, 1)), h(i + vec2(1, 1)), f.x), f.y); }
float fbm(vec2 p) { float s = 0., a = .5; for (int i = 0; i < 5; i++) { s += vn(p) * a; p = p * 2.03 + 17.1; a *= .5; } return s; }
float field(vec2 p) { vec2 d = vec2(time * .9, time * .35); return fbm((p + d) / 46.) * .75 + fbm((p - d * .6) / 13.) * .25; }
void main() {
  vec2 p = vWorld.xz;
  float c = field(p), e = 1.5;
  vec3 n = normalize(vec3(field(p - vec2(e, 0)) - field(p + vec2(e, 0)), .045, field(p - vec2(0, e)) - field(p + vec2(0, e))));
  float lit = clamp(dot(n, sun) * .55 + .55, 0., 1.);
  vec3 col = mix(vec3(.66, .73, .84), vec3(1., .995, .98), smoothstep(.25, .85, lit) * .75 + smoothstep(.38, .7, c) * .25);
  float alpha = 1.;
  if (layer > .5) { alpha = smoothstep(.52, .7, fbm((p + vec2(time * 2.2, time)) / 22.)) * .55; col = vec3(1.); }
  float dist = length(p - eye.xz);
  col = mix(col, horizon * 1.04, smoothstep(fogEnd * .6, fogEnd * 3.5, dist));
  color = vec4(col, alpha);
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

// ---------- the Storm Roc (GeckoLib geometry, posed like the art preview) ----------
function faces(cube) {
  const [x, y, z] = cube.origin, [w, h, d] = cube.size, X = x + w, Y = y + h, Z = z + d;
  return {
    north: {p: [[x, y, z], [X, y, z], [X, Y, z], [x, Y, z]], n: [0, 0, -1]},
    south: {p: [[X, y, Z], [x, y, Z], [x, Y, Z], [X, Y, Z]], n: [0, 0, 1]},
    east: {p: [[X, y, z], [X, y, Z], [X, Y, Z], [X, Y, z]], n: [1, 0, 0]},
    west: {p: [[x, y, Z], [x, y, z], [x, Y, z], [x, Y, Z]], n: [-1, 0, 0]},
    up: {p: [[x, Y, z], [X, Y, z], [X, Y, Z], [x, Y, Z]], n: [0, 1, 0]},
    down: {p: [[x, y, Z], [X, y, Z], [X, y, z], [x, y, z]], n: [0, -1, 0]},
  };
}
function transform(m, p, normal = false) { return [0, 1, 2].map(r => m[r] * p[0] + m[4 + r] * p[1] + m[8 + r] * p[2] + (normal ? 0 : m[12 + r])); }
function rotationMatrix(pivot, r = [0, 0, 0], p = [0, 0, 0], s = [1, 1, 1]) {
  let m = M.mul(M.translate(p), M.translate(pivot));
  for (const axis of [2, 1, 0]) m = M.mul(m, M.rotate(axis, rad(r[axis]) * (axis === 2 ? 1 : -1)));
  const sm = M.id(); sm[0] = s[0]; sm[5] = s[1]; sm[10] = s[2]; m = M.mul(m, sm);
  return M.mul(m, M.translate(pivot.map(v => -v)));
}
function channel(ch, t, def) {
  if (!ch) return def; if (Array.isArray(ch)) return ch;
  const entries = Object.entries(ch), val = v => Array.isArray(v) ? v : v.post || v.pre;
  let prev = entries[0];
  for (let i = 1; i < entries.length; i++) {
    const next = entries[i];
    if (t < +next[0]) { const v = val(prev[1]); if (prev[1].lerp_mode === 'step') return v; const f = Math.max(0, (t - +prev[0]) / (+next[0] - +prev[0])); return v.map((n, j) => n + ((next[1].pre || val(next[1]))[j] - n) * f); }
    prev = next;
  }
  return val(prev[1]);
}
function posedRoc(roc, clipName, time) {
  const geo = roc.geo['minecraft:geometry'][0], clip = roc.clips[clipName], tw = geo.description.texture_width, th = geo.description.texture_height;
  const matrices = {};
  for (const b of geo.bones) {
    const c = clip.bones[b.name] || {};
    const m = rotationMatrix(b.pivot, channel(c.rotation, time, [0, 0, 0]), channel(c.position, time, [0, 0, 0]), channel(c.scale, time, [1, 1, 1]));
    matrices[b.name] = b.parent ? M.mul(matrices[b.parent], m) : m;
  }
  const out = [];
  for (const b of geo.bones) {
    const bm = matrices[b.name];
    for (const cube of b.cubes || []) {
      const m = M.mul(bm, rotationMatrix(cube.pivot || [0, 0, 0], cube.rotation));
      for (const [name, f] of Object.entries(faces(cube))) {
        const {uv: [u, v], uv_size: [w, h]} = cube.uv[name];
        const uv = [[u / tw, 1 - (v + h) / th], [(u + w) / tw, 1 - (v + h) / th], [(u + w) / tw, 1 - v / th], [u / tw, 1 - v / th]];
        const n = norm(transform(m, f.n, true));
        for (const k of [0, 1, 2, 0, 2, 3]) out.push(...transform(m, f.p[k]).map(c => c / 16), ...n, ...uv[k]);
      }
    }
  }
  return new Float32Array(out);
}

// ---------- state ----------
const cam = {mode: 'walk', target: [0, 0, 0], yaw: 0, pitch: .35, dist: 260, eye: [.5, 2.62, 32.5], lookYaw: Math.PI, lookPitch: .05};
let data, facts, voxel, model, rocProg, sky, cloud, box, atlasTex, rocTex, summit, rocs = {};
const rocShown = new Set(['perched', 'flying']);
let rocScale = 2.5;   // render scale on the art study's model; the layout's chosen size replaces it
const rocBounds = {}, rocFeet = {};   // per pose at 1×: [minX, maxX, minY, maxY, minZ, maxZ] in blocks; lowest toe
const keys = new Set();
let time0 = performance.now();

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

let VIEWS;
function views() {
  const home = facts.perches[0].perch, shrine = facts.shrine, east = facts.perches[2].perch;
  const ra = rad(52), rimEast = [Math.cos(rad(22)) * (facts.rim[0] - 2), 2.62, Math.sin(rad(22)) * (facts.rim[0] - 2)];
  return [
    ['Arrival', () => lookFrom([.5, 2.62, 32.5], [home[0] + .5, home[1] + 4, home[2] + .5])],
    ['Centre', () => lookFrom([.5, 2.62, .5], [home[0] + .5, home[1] + 2, home[2] + .5])],
    ['At the rim', () => lookFrom(rimEast, [east[0] + .5, east[1] - 10, east[2] + .5])],
    ['Over the edge', () => walk([rimEast[0] + 1, 6, rimEast[2]], Math.atan2(Math.cos(rad(22)), Math.sin(rad(22))), -.75)],
    ['Shrine', () => lookFrom([shrine[0] + 15.5, 3.4, shrine[1] + 7.5], [shrine[0] - 2, 1, shrine[1]])],
    ['Promontory', () => lookFrom([Math.cos(ra) * (facts.rim[1] + 3), 2.62, Math.sin(ra) * (facts.rim[1] + 3)], [0, 1, 0])],
    ['From the perch', () => { const d = norm([home[0], 0, home[2]]);
      lookFrom([home[0] + .5 + d[0] * 10, home[1] + 11, home[2] + .5 + d[2] * 10], [0, 0, 0]); }],
    ['Face the Roc', () => { if (!rocShown.has('landed')) { rocShown.add('landed'); document.querySelectorAll('#rocs button')[2]?.classList.add('on'); }
      const P = playerBeforeRoc(), L = rocPlacements().landed.at, d = norm([P[0] - L[0], 0, P[2] - L[2]]);
      lookFrom([P[0] + d[0] * 6 + .8, 2.62, P[2] + d[2] * 6], [L[0], (rocBounds.landed[3] - rocBounds.landed[2]) * rocScale * .5, L[2]]); }],
    ['Orbit', () => orbit([0, -6, 0], .6, .32, 280)],
    ['Overhead', () => orbit([0, 0, 0], 0, 1.52, 420)],
  ];
}
function walk(eye, yaw, pitch) { Object.assign(cam, {mode: 'walk', eye: [...eye], lookYaw: yaw, lookPitch: pitch}); hint(); draw(); }
function orbit(target, yaw, pitch, dist) { Object.assign(cam, {mode: 'orbit', target: [...target], yaw, pitch, dist}); hint(); draw(); }
function hint() {
  $('#hint').textContent = cam.mode === 'walk'
    ? 'Walk: drag to look · WASD move · Space/Shift up/down · hold Ctrl or R to go fast · O for orbit'
    : 'Orbit: drag to rotate · right-drag or Shift-drag to pan · scroll to zoom · V to walk from here';
}

// Where each Roc stands for scale: perched on the home spire, flying over, landed for the brawl.
// Positions follow the size slider so a bigger bird still clears the summit.
const toCentre = p => Math.atan2(p[0], p[2]);
function rocPlacements(s = rocScale) {
  const home = facts.perches[0];
  const landed = [6.5, 1, -14.5 - 7 * (s - 1)];
  return {
    perched: {clip: 'perched', t: 0, at: [home.perch[0] + .5, home.perch[1], home.perch[2] + .5], yaw: toCentre(home.perch), bank: 0},
    flying: {clip: 'flight', t: .7, at: [-24 - 6 * (s - 1), 26 + 9 * (s - 1), -6], yaw: Math.atan2(1, -.4), bank: rad(-18)},
    landed: {clip: 'hunched', t: 0, at: landed, yaw: toCentre(landed), bank: 0},
  };
}
// A player standing in front of the landed Roc's beak.
function playerBeforeRoc() {
  const L = rocPlacements().landed.at, d = norm([-L[0], 0, -L[2]]), k = -rocBounds.landed[4] * rocScale + 4;
  return [L[0] + d[0] * k, 1, L[2] + d[2] * k];
}
function rocModel(key) {
  const p = rocPlacements()[key];
  // Stand the feet on the ground: the perched clip holds them above the origin, with the
  // tail hanging below them over the back of the perch.
  const lift = key === 'flying' ? 0 : -rocFeet[key] * rocScale;
  let m = M.mul(M.translate([p.at[0], p.at[1] + lift, p.at[2]]), M.rotate(1, p.yaw));
  m = M.mul(m, M.rotate(2, p.bank));
  const sm = M.id(); sm[0] = sm[5] = sm[10] = rocScale;
  return M.mul(m, sm);
}

// ---------- setup ----------
async function load() {
  data = await (await fetch('layouts.json?v=' + Date.now())).json();
  facts = data.facts;
  rocScale = facts.rocScale;
  const roc = await (await fetch('roc.json?v=' + Date.now())).json();
  const [img, rocImg] = await Promise.all(['atlas.png', 'roc.png'].map(src => new Promise((ok, fail) => { const i = new Image(); i.onload = () => ok(i); i.onerror = () => fail(Error('Image failed: ' + src)); i.src = src + '?v=' + Date.now(); })));
  voxel = program(VOXEL_VS, VOXEL_FS); model = program(MODEL_VS, VOXEL_FS); rocProg = program(ROC_VS, ROC_FS);
  sky = program(SKY_VS, SKY_FS); cloud = program(CLOUD_VS, CLOUD_FS); box = program(BOX_VS, BOX_FS);
  atlasTex = texture(img, false); rocTex = texture(rocImg, true);
  const lay = data.summit;
  const bytes = Uint8Array.from(atob(lay.instances), c => c.charCodeAt(0));
  const vao = gl.createVertexArray(); gl.bindVertexArray(vao);
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer()); gl.bufferData(gl.ARRAY_BUFFER, bytes, gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribIPointer(0, 4, gl.SHORT, 8, 0); gl.vertexAttribDivisor(0, 1);
  const mvao = gl.createVertexArray(); gl.bindVertexArray(mvao);
  gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer()); gl.bufferData(gl.ARRAY_BUFFER, Uint8Array.from(atob(lay.models), c => c.charCodeAt(0)), gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribPointer(0, 3, gl.INT, false, 24, 0);
  gl.enableVertexAttribArray(1); gl.vertexAttribPointer(1, 2, gl.INT, false, 24, 12);
  gl.enableVertexAttribArray(2); gl.vertexAttribPointer(2, 2, gl.UNSIGNED_SHORT, false, 24, 20);
  summit = {vao, count: lay.count, mvao, mcount: lay.modelVerts};
  for (const [key, p] of Object.entries(rocPlacements())) {
    const mesh = posedRoc(roc, p.clip, p.t), rvao = gl.createVertexArray(); gl.bindVertexArray(rvao);
    gl.bindBuffer(gl.ARRAY_BUFFER, gl.createBuffer()); gl.bufferData(gl.ARRAY_BUFFER, mesh, gl.STATIC_DRAW);
    for (const [loc, n, off] of [[0, 3, 0], [1, 3, 12], [2, 2, 24]]) { gl.enableVertexAttribArray(loc); gl.vertexAttribPointer(loc, n, gl.FLOAT, false, 32, off); }
    rocs[key] = {vao: rvao, count: mesh.length / 8};
    const b = [1e9, -1e9, 1e9, -1e9, 1e9, -1e9];
    for (let i = 0; i < mesh.length; i += 8) for (let a = 0; a < 3; a++) { b[a * 2] = Math.min(b[a * 2], mesh[i + a]); b[a * 2 + 1] = Math.max(b[a * 2 + 1], mesh[i + a]); }
    rocBounds[key] = b;
    const g = roc.geo['minecraft:geometry'][0];
    const feet = posedRoc({...roc, geo: {'minecraft:geometry': [{...g, bones: g.bones.map(bn => /foot|toe/.test(bn.name) ? bn : {...bn, cubes: []})}]}}, p.clip, p.t);
    rocFeet[key] = Infinity;
    for (let i = 1; i < feet.length; i += 8) rocFeet[key] = Math.min(rocFeet[key], feet[i]);
  }
  gl.bindVertexArray(null);
  const mats = new Int32Array(96 * 4);
  data.materials.forEach((m, i) => mats.set([m.shape, ...m.tex], i * 4));
  gl.useProgram(voxel.p); gl.uniform4iv(voxel.u.mats, mats);
  for (const prog of [voxel, model]) { gl.useProgram(prog.p); gl.uniform2f(prog.u.atlasSize, data.atlasCols, data.atlasRows); }
  VIEWS = views();
  buildUi(); VIEWS[0][1]();
}
function texture(img, flip) {
  const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, flip);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
  gl.pixelStorei(gl.UNPACK_FLIP_Y_WEBGL, false);
  for (const p of [gl.TEXTURE_MIN_FILTER, gl.TEXTURE_MAG_FILTER]) gl.texParameteri(gl.TEXTURE_2D, p, gl.NEAREST);
  for (const p of [gl.TEXTURE_WRAP_S, gl.TEXTURE_WRAP_T]) gl.texParameteri(gl.TEXTURE_2D, p, gl.CLAMP_TO_EDGE);
  return t;
}

function buildUi() {
  const views = $('#views');
  for (const [label, go] of VIEWS) { const b = document.createElement('button'); b.textContent = label; b.onclick = go; views.append(b); }
  const rocRow = $('#rocs');
  for (const [key, label] of [['perched', 'On the home spire'], ['flying', 'Flying over'], ['landed', 'Landed to brawl']]) {
    const b = document.createElement('button'); b.textContent = label; b.classList.toggle('on', rocShown.has(key));
    b.onclick = () => { rocShown.has(key) ? rocShown.delete(key) : rocShown.add(key); b.classList.toggle('on', rocShown.has(key)); draw(); };
    rocRow.append(b);
  }
  $('#fog').oninput = () => { labels(); draw(); };
  $('#rocSize').value = rocScale;
  $('#rocSize').oninput = () => { rocScale = +$('#rocSize').value; labels(); draw(); };
  $('#player').onchange = $('#clouds').onchange = $('#drift').onchange = draw;
  $('#toggle').onclick = () => { $('#panel').classList.toggle('collapsed'); $('#toggle').textContent = $('#panel').classList.contains('collapsed') ? 'Show' : 'Hide'; };
  labels();
  const rows = facts.perches.map(p => `<tr><td>${p.name}</td><td><b>${p.height}</b> up</td><td><b>${p.gap}</b> off the rim</td><td>top <b>${p.cap}</b> across</td></tr>`).join('');
  $('#facts').innerHTML = `<b>${data.summit.blocks.toLocaleString()}</b> blocks. The summit is <b>${Math.round(facts.rim[0] * 2)}–${Math.round(facts.rim[1] * 2)}</b> blocks across `
    + `(plus a promontory), flat rock with the middle ${facts.open * 2} blocks left bare. The sea of clouds lies <b>${-facts.cloud}</b> blocks below the summit; falling into it kills.<br>`
    + `Spires (perch height above the summit, gap from the rim at summit height, width of the capstone):<table>${rows}</table>`
    + `Gold boxes: a player beside the arrival point, and one in front of the landed Roc. The Roc is the art study's model in its perched, flight and hunched poses, scaled by the size slider.`;
}
function labels() {
  $('#fogv').textContent = $('#fog').value + ' (' + ($('#fog').value / 16) + ' chunks)';
  $('#rocSizeV').textContent = rocScale + '×';
  if (!rocBounds.perched) return;
  const up = rocBounds.perched[3] - rocBounds.perched[2], low = rocBounds.landed[3] - rocBounds.landed[2], long = rocBounds.landed[5] - rocBounds.landed[4];
  $('#rocStats').innerHTML = `Wingspan <b>${Math.round(27.2 * rocScale)}</b> blocks. Standing tall it is <b>${(up * rocScale).toFixed(0)}</b> blocks high; `
    + `hunched to brawl, <b>${(low * rocScale).toFixed(0)}</b> high and <b>${(long * rocScale).toFixed(0)}</b> long, beak to tail. A player is 1.8. 1× is the art study's size; the map is built for ${facts.rocScale}×.`;
}

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
    if (keys.has('Space')) cam.eye[1] += speed; if (keys.has('ShiftLeft')) cam.eye[1] -= speed;
  } else if (!$('#drift').checked) lastT = 0;
  const dpr = Math.min(devicePixelRatio, 2), w = canvas.clientWidth * dpr | 0, h = canvas.clientHeight * dpr | 0;
  if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
  gl.viewport(0, 0, w, h);
  const fogEnd = +$('#fog').value;
  const [eye, fwd] = eyeAndForward();
  const viewProj = M.mul(M.perspective(1.2, w / h, .1, 8000), M.lookAt(eye, fwd));
  gl.disable(gl.DEPTH_TEST); gl.disable(gl.BLEND);
  gl.useProgram(sky.p);
  gl.uniformMatrix4fv(sky.u.invViewProj, false, M.invert(viewProj));
  gl.uniform3fv(sky.u.horizon, HORIZON); gl.uniform3fv(sky.u.zenith, ZENITH); gl.uniform3fv(sky.u.sun, SUN);
  gl.drawArrays(gl.TRIANGLES, 0, 3);
  gl.enable(gl.DEPTH_TEST); gl.clear(gl.DEPTH_BUFFER_BIT);
  // Orbit views fog around the orbit target, as a player standing there would see it.
  const fogAt = cam.mode === 'orbit' ? cam.target : eye;
  const fog = prog => { gl.uniform3fv(prog.u.eye, fogAt); gl.uniform1f(prog.u.fogEnd, fogEnd); gl.uniform3fv(prog.u.horizon, HORIZON); };
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, atlasTex);
  gl.useProgram(voxel.p); gl.uniformMatrix4fv(voxel.u.viewProj, false, viewProj); fog(voxel); gl.uniform1i(voxel.u.atlas, 0);
  gl.bindVertexArray(summit.vao); gl.drawArraysInstanced(gl.TRIANGLES, 0, 36, summit.count);
  gl.useProgram(model.p); gl.uniformMatrix4fv(model.u.viewProj, false, viewProj); fog(model); gl.uniform1i(model.u.atlas, 0);
  gl.disable(gl.CULL_FACE);
  gl.bindVertexArray(summit.mvao); gl.drawArrays(gl.TRIANGLES, 0, summit.mcount);
  gl.bindTexture(gl.TEXTURE_2D, rocTex);
  gl.useProgram(rocProg.p); gl.uniformMatrix4fv(rocProg.u.viewProj, false, viewProj); fog(rocProg); gl.uniform1i(rocProg.u.tex, 0); gl.uniform3fv(rocProg.u.sun, SUN);
  for (const key of rocShown) { const r = rocs[key]; gl.uniformMatrix4fv(rocProg.u.model, false, rocModel(key)); gl.bindVertexArray(r.vao); gl.drawArrays(gl.TRIANGLES, 0, r.count); }
  gl.bindVertexArray(null);
  if ($('#clouds').checked) {
    const time = $('#drift').checked ? (performance.now() - time0) / 1000 : 0;
    gl.useProgram(cloud.p); gl.uniformMatrix4fv(cloud.u.viewProj, false, viewProj); fog(cloud);
    gl.uniform1f(cloud.u.time, time); gl.uniform3fv(cloud.u.sun, SUN);
    gl.uniform1f(cloud.u.layer, 0); gl.uniform1f(cloud.u.cloudY, facts.cloud);
    gl.drawArrays(gl.TRIANGLES, 0, 6);
    gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); gl.depthMask(false);
    gl.uniform1f(cloud.u.layer, 1); gl.uniform1f(cloud.u.cloudY, facts.cloud + 3.5);
    gl.drawArrays(gl.TRIANGLES, 0, 6);
    gl.depthMask(true); gl.disable(gl.BLEND);
  }
  if ($('#player').checked) {
    gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    gl.useProgram(box.p); gl.uniformMatrix4fv(box.u.viewProj, false, viewProj);
    const [ax, ay, az] = facts.arrival;
    gl.uniform3fv(box.u.lo, [ax + 2.7, ay, az - 2.8]); gl.uniform3fv(box.u.hi, [ax + 3.3, ay + 1.8, az - 2.2]); gl.uniform4fv(box.u.tint, [.95, .8, .4, .95]);
    gl.drawArrays(gl.TRIANGLES, 0, 36);
    if (rocShown.has('landed')) {
      const [px, py, pz] = playerBeforeRoc();
      gl.uniform3fv(box.u.lo, [px - .3, py, pz - .3]); gl.uniform3fv(box.u.hi, [px + .3, py + 1.8, pz + .3]);
      gl.drawArrays(gl.TRIANGLES, 0, 36);
    }
    gl.disable(gl.BLEND);
  }
  if ((cam.mode === 'walk' && keys.size) || ($('#clouds').checked && $('#drift').checked)) draw();
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
    cam.yaw -= dx * .005; cam.pitch = Math.max(-.4, Math.min(1.55, cam.pitch + dy * .005));
  }
  draw();
});
canvas.addEventListener('wheel', e => {
  e.preventDefault();
  if (cam.mode === 'orbit') cam.dist = Math.max(4, Math.min(1200, cam.dist * Math.exp(e.deltaY * .001)));
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
window.summitPreview = {cam, draw, walk, orbit, rocShown, setScale(v) { rocScale = v; $('#rocSize').value = v; labels(); draw(); }, get views() { return VIEWS; }};
load().catch(err => { document.body.insertAdjacentHTML('beforeend', `<pre style="position:fixed;bottom:40px;left:12px;color:#f88;background:#000a">${err.stack || err}</pre>`); });
