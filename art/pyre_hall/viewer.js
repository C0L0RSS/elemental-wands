// Burned Throne Hall layout preview: instanced WebGL2 voxels from layouts.json + atlas.png, the
// lava sea as one emissive sheet, and the Pyre King posed in his idle for scale. It shows the
// authored blocks; Minecraft owns the real lighting and fog.
//   ?view=arrival|face|pillar|throne|gallery|up|gate|orbit|overhead  &look=target|ember|daylight
//   &bare=1 hides the controls, &open=1 opens them on a narrow screen, &wall=1 shows the invisible wall,
//   &eye=x,y,z&at=x,y,z places the camera for scripted shots.
'use strict';

const $ = s => document.querySelector(s);
const canvas = $('#view');
const gl = canvas.getContext('webgl2', {antialias: true});
if (!gl) document.body.innerHTML = '<p style="padding:24px">This preview needs WebGL2.</p>';
const params = new URLSearchParams(location.search);

// ---------- math (column-major, like WebGL) ----------
const rad = d => d * Math.PI / 180;
const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const cross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const norm = a => { const l = Math.hypot(...a) || 1; return a.map(v => v / l); };
const sub = (a, b) => a.map((v, i) => v - b[i]);
const M = {
  id: () => new Float32Array([1,0,0,0, 0,1,0,0, 0,0,1,0, 0,0,0,1]),
  t(x, y, z) { const m = M.id(); m[12] = x; m[13] = y; m[14] = z; return m; },
  s(x, y, z) { const m = M.id(); m[0] = x; m[5] = y; m[10] = z; return m; },
  rx(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([1,0,0,0, 0,c,s,0, 0,-s,c,0, 0,0,0,1]); },
  ry(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([c,0,-s,0, 0,1,0,0, s,0,c,0, 0,0,0,1]); },
  rz(a) { const c = Math.cos(a), s = Math.sin(a); return new Float32Array([c,s,0,0, -s,c,0,0, 0,0,1,0, 0,0,0,1]); },
  apply(m, v) { return [m[0]*v[0]+m[4]*v[1]+m[8]*v[2]+m[12], m[1]*v[0]+m[5]*v[1]+m[9]*v[2]+m[13], m[2]*v[0]+m[6]*v[1]+m[10]*v[2]+m[14]]; },
  dir(m, v) { return [m[0]*v[0]+m[4]*v[1]+m[8]*v[2], m[1]*v[0]+m[5]*v[1]+m[9]*v[2], m[2]*v[0]+m[6]*v[1]+m[10]*v[2]]; },
  perspective(fov, aspect, near, far) { const f = 1 / Math.tan(fov / 2), m = new Float32Array(16); m[0] = f / aspect; m[5] = f; m[10] = (far + near) / (near - far); m[11] = -1; m[14] = 2 * far * near / (near - far); return m; },
  lookAt(e, f) { // eye, unit forward
    const s = norm(cross(f, [0, 1, 0])), u = cross(s, f);
    return new Float32Array([s[0], u[0], -f[0], 0, s[1], u[1], -f[1], 0, s[2], u[2], -f[2], 0, -dot(s, e), -dot(u, e), dot(f, e), 1]);
  },
  mul(a, b) { const o = new Float32Array(16); for (let c = 0; c < 4; c++) for (let r = 0; r < 4; r++) { let s = 0; for (let k = 0; k < 4; k++) s += a[k * 4 + r] * b[c * 4 + k]; o[c * 4 + r] = s; } return o; },
  invert(a) {
    const inv = new Float32Array(16);
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

// ---------- the Pyre King: GeckoLib 5 baking and posing, as art/pyre_king/workshop/core.js ----------
// X is mirrored, bones keep their bind rotations, rotations apply Z then Y then X.
function bakeModel(geo) {
  const g = geo['minecraft:geometry'][0], d = g.description, W = d.texture_width, H = d.texture_height;
  const byName = new Map(g.bones.map(b => [b.name, b]));
  const order = [], seen = new Set();
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
      const u0 = (u + us) / W, u1 = u / W, v0 = v / H, v1 = (v + vs) / H;
      let pts = quads[dir].map(k => V[k]), n = normals[dir];
      if (cubeMatrix) { pts = pts.map(p => M.apply(cubeMatrix, p)); n = M.dir(cubeMatrix, n); }
      if (us === 0 || vs === 0) continue;
      faces.push({bone: boneIndex, pts, n, uvs: [[u0, v0], [u1, v0], [u1, v1], [u0, v1]]});
    }
  }));
  return {bones, faces};
}
function sampleChannel(channel, t, fallback) {
  if (channel === undefined) return fallback;
  if (Array.isArray(channel) || typeof channel === 'number') return vec(channel, fallback);
  const keys = Object.entries(channel).map(([k, v]) => ({t: +k, pre: vec(v.pre ?? v.post ?? v, fallback), post: vec(v.post ?? v.pre ?? v, fallback)})).sort((a, b) => a.t - b.t);
  if (t <= keys[0].t) return keys[0].post;
  for (let i = 1; i < keys.length; i++) if (t <= keys[i].t) {
    const a = keys[i - 1], b = keys[i], f = (t - a.t) / (b.t - a.t || 1);
    return a.post.map((v, j) => v + (b.pre[j] - v) * f);
  }
  return keys[keys.length - 1].post;
}
function vec(v, fallback) {
  if (typeof v === 'number') return [v, v, v];
  if (Array.isArray(v)) return v.map((x, i) => typeof x === 'number' ? x : fallback[i]);
  return fallback;
}
function poseBones(model, clip, t) {
  const out = [], byName = {};
  model.bones.forEach((bone, i) => {
    const ch = (clip && clip.bones && clip.bones[bone.name]) || {};
    const r = sampleChannel(ch.rotation, t, [0, 0, 0]), p = sampleChannel(ch.position, t, [0, 0, 0]), s = sampleChannel(ch.scale, t, [1, 1, 1]);
    const rot = [bone.rest[0] + rad(-r[0]), bone.rest[1] + rad(-r[1]), bone.rest[2] + rad(r[2])], pv = bone.pivot;
    let m = [M.t(-p[0] / 16, p[1] / 16, p[2] / 16), M.t(...pv), M.rz(rot[2]), M.ry(rot[1]), M.rx(rot[0]), M.s(...s), M.t(-pv[0], -pv[1], -pv[2])].reduce(M.mul);
    if (bone.parent != null && byName[bone.parent] !== undefined) m = M.mul(out[byName[bone.parent]], m);
    out[i] = m; byName[bone.name] = i;
  });
  return out;
}
// Baked triangles in world space: position, normal, uv. His front is -Z in source space, so
// he is turned half round to face south (+Z), and stands on the block above KING.
function kingMesh(king, at) {
  const model = bakeModel(king.geo), mats = poseBones(model, king.idle, 0);
  const world = M.mul(M.t(at[0] + .5, at[1], at[2] + .5), M.ry(Math.PI));
  const out = [];
  let lo = 1e9, hi = -1e9;
  for (const f of model.faces) {
    const m = M.mul(world, mats[f.bone]), n = norm(M.dir(m, f.n)), pts = f.pts.map(p => M.apply(m, p));
    for (const k of [0, 1, 2, 0, 2, 3]) { out.push(...pts[k], ...n, ...f.uvs[k]); lo = Math.min(lo, pts[k][1]); hi = Math.max(hi, pts[k][1]); }
  }
  const si = model.bones.findIndex(b => b.name === 'sword');
  const sword = si >= 0 ? M.apply(M.mul(world, mats[si]), model.bones[si].pivot) : [at[0] + .5, at[1] + 2, at[2] + 1.5];
  return {verts: new Float32Array(out), count: out.length / 8, sword, height: hi - lo};
}

// ---------- shaders ----------
const LIGHTING = `
uniform vec4 lightPos[64]; uniform vec3 lightCol[64]; uniform int lightCount;
uniform vec3 ambCol; uniform float lavaGlow; uniform float lavaY; uniform float lightGain;
vec3 lighting(vec3 w, vec3 n) {
  vec3 light = ambCol;
  for (int i = 0; i < 64; i++) {
    if (i >= lightCount) break;
    vec3 d = abs(w - lightPos[i].xyz);
    float reach = lightPos[i].w - (d.x + d.y + d.z) * .9;
    if (reach > 0.) light += lightCol[i] * pow(reach / 15., 1.4) * lightGain;
  }
  // Lava-light from the sea under the crag: strongest on faces that look down at it.
  float outside = smoothstep(51., 57., length(w.xz - .5));
  float facing = n.y < -.5 ? 1. : n.y > .5 ? .35 : .7;
  light += lavaGlow * vec3(1., .36, .1) * facing * exp(-max(0., w.y - lavaY) / 18.) * outside;
  float top = max(light.r, max(light.g, light.b));
  return top > 1.5 ? light * (1.5 / top) : light;   // keep the hue where lights pile up
}`;
const HEAD = `#version 300 es
precision highp float; precision highp int;
uniform vec3 eye; uniform vec3 fogColor; uniform float fogEnd; uniform float fogNear;
vec3 fogged(vec3 c, vec3 w, float k) {
  float f = smoothstep(fogNear + fogEnd * .06, fogNear + fogEnd, length(w - eye)) * k;
  return mix(c, fogColor, f);
}
`;
const VOXEL_VS = `#version 300 es
precision highp float; precision highp int;
layout(location=0) in ivec4 inst;
uniform mat4 viewProj;
uniform ivec4 mats[96];
uniform int passKind;
out vec2 vUv; out vec3 vWorld; out float vShade; flat out int vTex; flat out int vMat; flat out int vFace;
const vec3 C[24] = vec3[24](
  vec3(1,0,1),vec3(1,1,1),vec3(1,1,0),vec3(1,0,0),  vec3(0,0,0),vec3(0,1,0),vec3(0,1,1),vec3(0,0,1),
  vec3(0,1,1),vec3(0,1,0),vec3(1,1,0),vec3(1,1,1),  vec3(0,0,0),vec3(0,0,1),vec3(1,0,1),vec3(1,0,0),
  vec3(0,0,1),vec3(0,1,1),vec3(1,1,1),vec3(1,0,1),  vec3(1,0,0),vec3(1,1,0),vec3(0,1,0),vec3(0,0,0));
const int K[6] = int[6](0,1,2,0,2,3);
const float SHADE[6] = float[6](.62,.62,1.,.5,.8,.8);
void collapse() { gl_Position = vec4(2,2,2,1); }
void main() {
  int face = gl_VertexID / 6, corner = K[gl_VertexID % 6];
  int mat = inst.w & 255, mask = (inst.w >> 8) & 63;
  ivec4 m = mats[mat];
  int shape = m.x;
  vMat = mat; vFace = face;
  if ((passKind == 1) != (shape == 5)) { collapse(); return; }
  if ((mask & (1 << face)) == 0) { collapse(); return; }
  if ((shape == 7 || shape == 1) && (face == 2 || face == 3)) { collapse(); return; }
  vec3 c = C[face * 4 + corner];
  vec3 lo = vec3(0), hi = vec3(1);
  if (shape == 1) { lo = vec3(.40625, 0, .40625); hi = vec3(.59375, 1, .59375); }
  else if (shape == 2) { lo = vec3(.375, 0, .375); hi = vec3(.625, 1, .625); }
  else if (shape == 3) { lo = vec3(.4375, .25, 0); hi = vec3(.5625, .9375, 1); }
  else if (shape == 4) { lo = vec3(0, .25, .4375); hi = vec3(1, .9375, .5625); }
  else if (shape == 6) { lo = vec3(.3125, .0625, .3125); hi = vec3(.6875, .5, .6875); }
  else if (shape == 8) { lo = vec3(0, 0, .46875); hi = vec3(1, 1, .53125); }
  else if (shape == 9) { lo = vec3(.46875, 0, 0); hi = vec3(.53125, 1, 1); }
  vec3 p = lo + c * (hi - lo);
  vec2 uv;
  if (face < 2) uv = vec2(face == 0 ? 1. - p.z : p.z, p.y);
  else if (face < 4) uv = vec2(p.x, face == 2 ? 1. - p.z : p.z);
  else uv = vec2(face == 4 ? p.x : 1. - p.x, p.y);
  vUv = vec2(uv.x, 1. - uv.y);
  if (shape == 1) vUv.x = (face < 2 ? c.z : c.x) * .1875;   // the chain's links sit in the texture's left 3 px
  if (shape == 6) {                                         // lantern body and lid from its texture
    if (face < 2 || face > 3) vUv = vec2((face < 2 ? c.z : c.x) * .375, .125 + (1. - c.y) * .4375);
    else vUv = vec2(c.x * .375, .5625 + c.z * .375);
  }
  vTex = face == 2 ? m.y : face == 3 ? m.w : m.z;
  vShade = SHADE[face];
  vWorld = vec3(inst.xyz) + p;
  gl_Position = viewProj * vec4(vWorld, 1);
}`;
const VOXEL_FS = HEAD + `
in vec2 vUv; in vec3 vWorld; in float vShade; flat in int vTex; flat in int vMat; flat in int vFace;
uniform sampler2D atlas; uniform vec2 atlasSize;
uniform int emissive[96];
uniform int passKind;
out vec4 color;
const vec3 N[6] = vec3[6](vec3(1,0,0),vec3(-1,0,0),vec3(0,1,0),vec3(0,-1,0),vec3(0,0,1),vec3(0,0,-1));
` + LIGHTING + `
void main() {
  if (passKind == 1) { color = vec4(1., .3, .25, .12); return; }
  vec2 tile = vec2(vTex % int(atlasSize.x), vTex / int(atlasSize.x));
  vec4 t = texture(atlas, (tile + clamp(vUv, .002, .998)) / atlasSize);
  if (t.a < .5) discard;
  vec3 c;
  float k = 1.;
  if (emissive[vMat] == 1) { c = t.rgb * 1.15; k = .55; }
  else c = t.rgb * vShade * lighting(vWorld, N[vFace]);
  color = vec4(fogged(c, vWorld, k), 1);
}`;
const SKY_VS = `#version 300 es
out vec2 ndc; void main() { vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2) * 2. - 1.; ndc = p; gl_Position = vec4(p, .9999, 1); }`;
const SKY_FS = `#version 300 es
precision highp float;
in vec2 ndc; uniform mat4 invViewProj; uniform vec3 fogColor; uniform vec3 zenith; out vec4 color;
void main() {
  vec4 a = invViewProj * vec4(ndc, -1, 1), b = invViewProj * vec4(ndc, 1, 1);
  vec3 dir = normalize(b.xyz / b.w - a.xyz / a.w);
  color = vec4(mix(fogColor, zenith, smoothstep(.0, .5, dir.y)), 1);
}`;
const LAVA_VS = `#version 300 es
precision highp float;
uniform mat4 viewProj; uniform float level; out vec3 vWorld;
const vec2 Q[6] = vec2[6](vec2(-1,-1),vec2(1,-1),vec2(1,1),vec2(-1,-1),vec2(1,1),vec2(-1,1));
void main() { vWorld = vec3(Q[gl_VertexID].x * 900., level, Q[gl_VertexID].y * 900.); gl_Position = viewProj * vec4(vWorld, 1); }`;
const LAVA_FS = HEAD + `
in vec3 vWorld; uniform sampler2D lavaTex; out vec4 color;
float hash(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vn(vec2 p) { vec2 i = floor(p), f = fract(p); f = f * f * (3. - 2. * f);
  return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x), mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y); }
void main() {
  vec3 t = texture(lavaTex, vWorld.xz).rgb;
  vec2 b = floor(vWorld.xz);   // darker cooling crust drifts in block-sized patches
  float crust = smoothstep(.5, .78, vn(b / 11.) * .7 + vn(b / 3.) * .3);
  vec3 c = t * mix(1.1, .66, crust);
  float f = smoothstep(fogNear + fogEnd * .06, fogNear + fogEnd * 1.4, length(vWorld - eye));
  color = vec4(mix(c, fogColor, mix(.55, 1., f) * f), 1);
}`;
const KING_VS = `#version 300 es
precision highp float;
layout(location=0) in vec3 pos; layout(location=1) in vec3 nrm; layout(location=2) in vec2 uv;
uniform mat4 viewProj; out vec3 vWorld; out vec3 vN; out vec2 vUv;
void main() { vWorld = pos; vN = nrm; vUv = uv; gl_Position = viewProj * vec4(pos, 1); }`;
const KING_FS = HEAD + `
in vec3 vWorld; in vec3 vN; in vec2 vUv; uniform sampler2D skin; uniform sampler2D glow; out vec4 color;
` + LIGHTING + `
void main() {
  vec4 t = texture(skin, vUv);
  if (t.a < .5) discard;
  vec3 n = normalize(vN);
  float shade = .62 + .38 * max(dot(n, normalize(vec3(.3, 1., .6))), 0.);
  vec3 c = t.rgb * shade * lighting(vWorld, n);
  vec4 g = texture(glow, vUv);
  c = mix(c, max(c, g.rgb * 1.25), g.a);
  color = vec4(fogged(c, vWorld, 1.), 1);
}`;
const BOX_VS = `#version 300 es
precision highp float;
uniform mat4 viewProj; uniform vec3 lo; uniform vec3 hi; out vec3 vN;
const vec3 C[8] = vec3[8](vec3(0,0,0),vec3(1,0,0),vec3(1,1,0),vec3(0,1,0),vec3(0,0,1),vec3(1,0,1),vec3(1,1,1),vec3(0,1,1));
const int I[36] = int[36](0,1,2,0,2,3, 5,4,7,5,7,6, 4,0,3,4,3,7, 1,5,6,1,6,2, 3,2,6,3,6,7, 4,5,1,4,1,0);
void main() { vec3 p = C[I[gl_VertexID]]; vN = vec3(gl_VertexID / 6); gl_Position = viewProj * vec4(lo + p * (hi - lo), 1); }`;
const BOX_FS = `#version 300 es
precision highp float; in vec3 vN; uniform vec4 tint; out vec4 color;
void main() { float s = .75 + .05 * vN.x; color = vec4(tint.rgb * s, tint.a); }`;

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
function textureFrom(img) {
  const t = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, t);
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, img);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.NEAREST); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_S, gl.CLAMP_TO_EDGE); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_WRAP_T, gl.CLAMP_TO_EDGE);
  return t;
}
function image(src, tries = 3) {
  return new Promise((ok, fail) => {
    const img = new Image();
    img.onload = () => ok(img);
    img.onerror = () => tries > 1 ? image(src, tries - 1).then(ok, fail) : fail(Error('could not load ' + src));
    img.src = src + '?v=' + Date.now();
  });
}

// ---------- state ----------
const LOOKS = {
  target: {label: 'In-game target', fog: 170, amb: [.6, .57, .56], gain: 1.0, lava: 1.0, fogColor: [.14, .055, .045], zenith: [.12, .045, .035]},
  ember: {label: 'Warm haze', fog: 340, amb: [.5, .38, .32], gain: 1.15, lava: 1.2, fogColor: [.26, .1, .065], zenith: [.12, .045, .035]},
  daylight: {label: 'Daylight', fog: 1600, amb: [1, 1, 1], gain: .35, lava: .25, fogColor: [.6, .6, .63], zenith: [.36, .46, .6]},
};
const cam = {mode: 'walk', target: [0, 8, 0], yaw: .6, pitch: .4, dist: 200, eye: [.5, 2.62, 33.5], lookYaw: Math.PI, lookPitch: 0};
let data, voxel, sky, lava, kingProg, box, atlasTex, lavaTex, skinTex, glowTex, hall, king, look = 'target', showBarrier = false;
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
function walk(eye, yaw, pitch) { Object.assign(cam, {mode: 'walk', eye: [...eye], lookYaw: yaw, lookPitch: pitch}); draw(); }
function lookFrom(eye, at) { walk(eye, yawTo(eye, at), pitchTo(eye, at)); }
function orbit(target, yaw, pitch, dist) { Object.assign(cam, {mode: 'orbit', target: [...target], yaw, pitch, dist}); draw(); }

let VIEWS;
function views() {
  const f = data.facts, [ax, ay, az] = f.arrival, [kx, ky, kz] = f.king, kh = king ? king.height : 5;
  const head = [kx + .5, ky + kh * .82, kz + .5];
  const p = f.pillars.find(q => Math.abs(q[0] + 20) < .1) || f.pillars[0];   // the west pillar, 17 high
  return [
    ['arrival', 'Arrival', () => lookFrom([ax + .5, ay + 1.62, az + .5], [kx + .5, ky + kh * .55, kz + .5])],
    ['face', 'Face the king', () => lookFrom([kx + .5, ky + 1.62, kz + 8.5], head)],
    ['pillar', 'Behind a pillar', () => lookFrom([p[0] - 10.5, 2.62, p[1] + 7.5], [kx + .5, ky + kh * .6, kz + .5])],
    ['throne', 'From the throne', () => lookFrom([-1.5, 8.62, -47.5], [.5, 2, 10])],
    ['gallery', 'On the gallery', () => { const g = f.galleryEye; lookFrom([g[0] + .5, g[1] + 1.62, g[2] + .5], [6, 8, -20]); }],
    ['up', 'Look up', () => walk([.5, 2.62, 16.5], Math.PI, 1.0)],
    ['gate', 'Outside the gate', () => lookFrom([.5, 4.62, 84.5], [.5, 12, 0])],
    ['orbit', 'Orbit', () => orbit([0, 4, 0], .65, .42, 190)],
    ['overhead', 'Overhead', () => orbit([0, 0, 0], 0, 1.53, 175)],
  ];
}

// ---------- setup ----------
async function load() {
  const [layouts, atlasImg, kingData, skinImg, glowImg, lavaImg] = await Promise.all([
    fetch('layouts.json?v=' + Date.now()).then(r => r.json()), image('atlas.png'),
    fetch('king.json?v=' + Date.now()).then(r => r.json()), image('king.png'), image('king_glow.png'), image('lava.png')]);
  data = layouts;
  voxel = program(VOXEL_VS, VOXEL_FS); sky = program(SKY_VS, SKY_FS); lava = program(LAVA_VS, LAVA_FS);
  kingProg = program(KING_VS, KING_FS); box = program(BOX_VS, BOX_FS);
  atlasTex = textureFrom(atlasImg); skinTex = textureFrom(skinImg); glowTex = textureFrom(glowImg);
  lavaTex = gl.createTexture(); gl.bindTexture(gl.TEXTURE_2D, lavaTex);   // repeats and fades to its mean colour far off
  gl.texImage2D(gl.TEXTURE_2D, 0, gl.RGBA, gl.RGBA, gl.UNSIGNED_BYTE, lavaImg); gl.generateMipmap(gl.TEXTURE_2D);
  gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MIN_FILTER, gl.LINEAR_MIPMAP_LINEAR); gl.texParameteri(gl.TEXTURE_2D, gl.TEXTURE_MAG_FILTER, gl.NEAREST);
  const lay = data.hall;
  const bytes = Uint8Array.from(atob(lay.instances), c => c.charCodeAt(0));
  const vao = gl.createVertexArray(); gl.bindVertexArray(vao);
  const buf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, buf); gl.bufferData(gl.ARRAY_BUFFER, bytes, gl.STATIC_DRAW);
  gl.enableVertexAttribArray(0); gl.vertexAttribIPointer(0, 4, gl.SHORT, 8, 0); gl.vertexAttribDivisor(0, 1);
  gl.bindVertexArray(null);
  const lights = lay.lights.map(([x, y, z, m]) => ({p: [x + .5, y + .5, z + .5], c: data.materials[m].light}))
    // Invisible light blocks (the throne's fill): never drawn, only light.
    .concat((lay.fill || []).map(([x, y, z, r, g, b, level]) => ({p: [x + .5, y + .5, z + .5], c: [r, g, b, level], keep: true})));
  hall = {vao, count: lay.count, lights};
  // The king.
  const mesh = kingMesh(kingData, data.facts.king);
  const kvao = gl.createVertexArray(); gl.bindVertexArray(kvao);
  const kbuf = gl.createBuffer(); gl.bindBuffer(gl.ARRAY_BUFFER, kbuf); gl.bufferData(gl.ARRAY_BUFFER, mesh.verts, gl.STATIC_DRAW);
  for (const [i, n, off] of [[0, 3, 0], [1, 3, 12], [2, 2, 24]]) { gl.enableVertexAttribArray(i); gl.vertexAttribPointer(i, n, gl.FLOAT, false, 32, off); }
  gl.bindVertexArray(null);
  king = {vao: kvao, count: mesh.count, height: mesh.height};
  hall.lights.push({p: mesh.sword, c: [1, .5, .18, 13]});   // his blade burns
  const mats = new Int32Array(96 * 4), emissive = new Int32Array(96);
  data.materials.forEach((m, i) => { mats.set([m.shape, ...m.tex], i * 4); emissive[i] = m.light ? 1 : 0; });
  gl.useProgram(voxel.p); gl.uniform4iv(voxel.u.mats, mats); gl.uniform1iv(voxel.u.emissive, emissive);
  gl.uniform2f(voxel.u.atlasSize, data.atlasCols, data.atlasRows);
  VIEWS = views();
  buildUi();
  const want = params.get('view'), v = VIEWS.find(x => x[0] === want) || VIEWS[0];
  setLook(LOOKS[params.get('look')] ? params.get('look') : 'target');
  pickView(v[0]);
  const num = k => params.get(k) && params.get(k).split(',').map(Number);
  if (num('eye') && num('at')) lookFrom(num('eye'), num('at'));   // scripted inspection shots
  window.previewReady = true;
}

function pickView(key) {
  const v = VIEWS.find(x => x[0] === key); if (!v) return;
  v[2]();
  document.querySelectorAll('#views button').forEach(b => b.classList.toggle('on', b.dataset.key === key));
}
function setLook(key) {
  look = key;
  document.querySelectorAll('#looks button[data-key]').forEach(b => b.classList.toggle('on', b.dataset.key === key));
  draw();
}
function buildUi() {
  if (params.get('bare') === '1') document.body.classList.add('bare');
  const bar = $('#bar');
  if ((innerWidth < 700 && params.get('open') !== '1') || params.get('open') === '0') bar.classList.add('collapsed');
  $('#toggle').onclick = () => bar.classList.toggle('collapsed');
  const views = $('#views');
  for (const [key, label] of VIEWS) {
    const b = document.createElement('button'); b.textContent = label; b.dataset.key = key; b.onclick = () => pickView(key); views.append(b);
  }
  const looks = $('#looks'), wall = $('#barrier');
  for (const [key, l] of Object.entries(LOOKS)) {
    const b = document.createElement('button'); b.textContent = l.label; b.dataset.key = key; b.onclick = () => setLook(key);
    looks.insertBefore(b, wall);
  }
  wall.onclick = () => { showBarrier = !showBarrier; wall.classList.toggle('on', showBarrier); draw(); };
  if (params.get('wall') === '1') wall.onclick();
}

// ---------- render ----------
let pending = false;
function draw() { if (!pending) { pending = true; requestAnimationFrame(frame); } }
let lastT = 0;
const dist2 = (a, b) => (a[0] - b[0]) ** 2 + (a[1] - b[1]) ** 2 + (a[2] - b[2]) ** 2;
function setScene(prog, L, eye) {
  // Orbiting, the haze starts around the hall rather than at the far-off camera.
  gl.uniform3fv(prog.u.eye, eye); gl.uniform3fv(prog.u.fogColor, L.fogColor); gl.uniform1f(prog.u.fogEnd, L.fog);
  gl.uniform1f(prog.u.fogNear, cam.mode === 'orbit' ? Math.max(0, cam.dist - 60) : 0);
}
function setLights(prog, L, focus) {
  gl.uniform3fv(prog.u.ambCol, L.amb); gl.uniform1f(prog.u.lightGain, L.gain); gl.uniform1f(prog.u.lavaGlow, L.lava);
  gl.uniform1f(prog.u.lavaY, data.facts.lavaY + .9);
  // The throne's fill always counts, so the throne reads lit from anywhere; the nearest of the rest fill the slots left.
  const keep = hall.lights.filter(l => l.keep);
  const lights = keep.concat(hall.lights.filter(l => !l.keep).sort((a, b) => dist2(a.p, focus) - dist2(b.p, focus))).slice(0, 64);
  const lp = new Float32Array(256), lc = new Float32Array(192);
  lights.forEach((l, i) => { lp.set([...l.p, l.c[3]], i * 4); lc.set(l.c.slice(0, 3), i * 3); });
  gl.uniform4fv(prog.u.lightPos, lp); gl.uniform3fv(prog.u.lightCol, lc); gl.uniform1i(prog.u.lightCount, lights.length);
}
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
    if (keys.has('Space')) cam.eye[1] += speed; if (keys.has('ShiftLeft')) cam.eye[1] = Math.max(-12, cam.eye[1] - speed);
    draw();
  } else lastT = 0;
  const dpr = Math.min(devicePixelRatio, 2), w = canvas.clientWidth * dpr | 0, h = canvas.clientHeight * dpr | 0;
  if (canvas.width !== w || canvas.height !== h) { canvas.width = w; canvas.height = h; }
  gl.viewport(0, 0, w, h);
  const L = LOOKS[look];
  const [eye, fwd] = eyeAndForward();
  const focus = cam.mode === 'walk' ? eye.map((v, i) => v + fwd[i] * 10) : cam.target;
  const viewProj = M.mul(M.perspective(w < h ? 1.35 : 1.15, w / h, .1, 2000), M.lookAt(eye, fwd));
  gl.disable(gl.DEPTH_TEST); gl.disable(gl.BLEND);
  gl.useProgram(sky.p);
  gl.uniformMatrix4fv(sky.u.invViewProj, false, M.invert(viewProj));
  gl.uniform3fv(sky.u.fogColor, L.fogColor); gl.uniform3fv(sky.u.zenith, L.zenith);
  gl.drawArrays(gl.TRIANGLES, 0, 3);
  gl.enable(gl.DEPTH_TEST); gl.clear(gl.DEPTH_BUFFER_BIT);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, atlasTex);
  // Blocks.
  gl.useProgram(voxel.p);
  gl.uniformMatrix4fv(voxel.u.viewProj, false, viewProj);
  setScene(voxel, L, eye); setLights(voxel, L, focus);
  gl.uniform1i(voxel.u.atlas, 0);
  gl.bindVertexArray(hall.vao);
  gl.uniform1i(voxel.u.passKind, 0);
  gl.drawArraysInstanced(gl.TRIANGLES, 0, 36, hall.count);
  // The lava sea.
  gl.bindVertexArray(null);
  gl.useProgram(lava.p);
  gl.uniformMatrix4fv(lava.u.viewProj, false, viewProj); setScene(lava, L, eye);
  gl.uniform1f(lava.u.level, data.facts.lavaY + .9);
  gl.activeTexture(gl.TEXTURE3); gl.bindTexture(gl.TEXTURE_2D, lavaTex); gl.uniform1i(lava.u.lavaTex, 3); gl.activeTexture(gl.TEXTURE0);
  gl.drawArrays(gl.TRIANGLES, 0, 6);
  // The king.
  gl.useProgram(kingProg.p);
  gl.uniformMatrix4fv(kingProg.u.viewProj, false, viewProj); setScene(kingProg, L, eye); setLights(kingProg, L, focus);
  gl.activeTexture(gl.TEXTURE1); gl.bindTexture(gl.TEXTURE_2D, skinTex); gl.uniform1i(kingProg.u.skin, 1);
  gl.activeTexture(gl.TEXTURE2); gl.bindTexture(gl.TEXTURE_2D, glowTex); gl.uniform1i(kingProg.u.glow, 2);
  gl.activeTexture(gl.TEXTURE0);
  gl.bindVertexArray(king.vao); gl.drawArrays(gl.TRIANGLES, 0, king.count); gl.bindVertexArray(null);
  // A player at the arrival point, for scale.
  gl.useProgram(box.p); gl.uniformMatrix4fv(box.u.viewProj, false, viewProj);
  const [ax, ay, az] = data.facts.arrival;
  if (Math.hypot(eye[0] - ax - .5, eye[2] - az - .5) > .8 || Math.abs(eye[1] - ay - 1) > 2) {   // unless we stand in it
    gl.uniform3fv(box.u.lo, [ax + .2, ay, az + .2]); gl.uniform3fv(box.u.hi, [ax + .8, ay + 1.8, az + .8]); gl.uniform4fv(box.u.tint, [1, .78, .3, 1]);
    gl.drawArrays(gl.TRIANGLES, 0, 36);
  }
  if (showBarrier) {
    gl.enable(gl.BLEND); gl.depthMask(false); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    gl.useProgram(voxel.p); gl.bindVertexArray(hall.vao); gl.uniform1i(voxel.u.passKind, 1);
    gl.drawArraysInstanced(gl.TRIANGLES, 0, 36, hall.count);
    gl.bindVertexArray(null); gl.depthMask(true); gl.disable(gl.BLEND);
  }
}

// ---------- input: mouse, touch (drag, two-finger pinch) and keys ----------
const pointers = new Map();
let pinch = null;
canvas.addEventListener('pointerdown', e => {
  pointers.set(e.pointerId, {x: e.clientX, y: e.clientY, pan: e.button === 2 || e.shiftKey});
  canvas.setPointerCapture(e.pointerId);
  if (pointers.size === 2) { const [a, b] = [...pointers.values()]; pinch = Math.hypot(a.x - b.x, a.y - b.y); }
});
const release = e => { pointers.delete(e.pointerId); if (pointers.size < 2) pinch = null; };
canvas.addEventListener('pointerup', release);
canvas.addEventListener('pointercancel', release);
canvas.addEventListener('contextmenu', e => e.preventDefault());
canvas.addEventListener('pointermove', e => {
  const p = pointers.get(e.pointerId);
  if (!p) return;
  const dx = e.clientX - p.x, dy = e.clientY - p.y; p.x = e.clientX; p.y = e.clientY;
  if (pointers.size >= 2) {
    const [a, b] = [...pointers.values()], d = Math.hypot(a.x - b.x, a.y - b.y);
    if (pinch != null) zoom((d - pinch) * -6);
    pinch = d;
    return;
  }
  const touch = e.pointerType === 'touch', k = touch ? 1.4 : 1;
  if (cam.mode === 'walk') {
    cam.lookYaw -= dx * .004 * k; cam.lookPitch = Math.max(-1.5, Math.min(1.5, cam.lookPitch - dy * .004 * k));
  } else if (p.pan) {
    const s = cam.dist * .0016, c = Math.cos(cam.yaw), n = Math.sin(cam.yaw);
    cam.target[0] -= dx * c * s; cam.target[2] += dx * n * s; cam.target[1] += dy * s;
  } else {
    cam.yaw -= dx * .005 * k; cam.pitch = Math.max(-.2, Math.min(1.55, cam.pitch + dy * .005 * k));
  }
  draw();
});
function zoom(amount) {   // positive: back off / step back
  if (cam.mode === 'orbit') cam.dist = Math.max(4, Math.min(900, cam.dist * Math.exp(amount * .001)));
  else { const f = eyeAndForward()[1]; for (let i = 0; i < 3; i++) cam.eye[i] -= f[i] * amount * .02; }
  draw();
}
canvas.addEventListener('wheel', e => { e.preventDefault(); zoom(e.deltaY); }, {passive: false});
addEventListener('keydown', e => {
  if (e.code === 'KeyV' && cam.mode === 'orbit') { const [eye, f] = eyeAndForward(); walk(eye, Math.atan2(f[0], f[2]), Math.asin(f[1])); return; }
  if (e.code === 'KeyO' && cam.mode === 'walk') { const f = eyeAndForward()[1]; orbit(cam.eye.map((v, i) => v + f[i] * 30), Math.atan2(-f[0], -f[2]), -Math.asin(f[1]), 30); return; }
  keys.add(e.code); if (e.code === 'Space') e.preventDefault(); draw();
});
addEventListener('keyup', e => keys.delete(e.code));
addEventListener('blur', () => keys.clear());
addEventListener('resize', draw);
load().catch(err => { document.body.insertAdjacentHTML('beforeend', `<pre style="position:fixed;bottom:40px;left:12px;color:#f88;white-space:pre-wrap">${err.stack || err}</pre>`); });
