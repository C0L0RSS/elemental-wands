(()=>{
// Adapted from hollow_necromancer/workshop/app.js. GeckoLib 5 baking and pivots.
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
    const hide = parent !== undefined && hidden[parent];
    out[i] = m; hidden[i] = hide; byName[bone.name] = i;
  });
  return {matrices: out, hidden};
}


globalThis.PyreCore = {M, rad, norm, bakeModel, poseBones};

})();
