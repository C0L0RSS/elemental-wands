// Wind's landing in the Storm Roc intro (art/storm_roc/intro/wind_landing.py bakes the scene): chained
// Waylay Dashes zigzagging down through the air, a skim over the snow and a light landing, drawn as
// "block wind" like the Wind spells: see-through cloud cubes, chunky streaks and rings textured from
// the spells' atlas (art/wind/blockwind/textures/blockwind.png). The cube kit is a small port of
// art/wind/blockwind/kit.js at this scene's scale; the look rules are that study's: air fades by
// shrinking and thinning, slow air is puffy, fast air is stretched, a chained dash doubles its ring
// and tints its outer cloud pale blue. Loaded by animatic.js through its LANDING hook.
'use strict';
(() => {
  const BAND = {gale: 0, cloud: 1, mist: 2, streak: 3, swirl: 4, feather: 5, pearl: 6, silver: 7};
  const FPS = 8, AIR = .62, TAU = Math.PI * 2, UP = [0, 1, 0];
  const PALE = [.74, .87, 1];  // a chained dash's outer cloud
  const VS = `#version 300 es
in vec3 pos; in vec2 uv; in vec4 col; uniform mat4 viewProj; out vec2 vUv; out vec4 vCol; out vec3 vPos;
void main(){ gl_Position = viewProj * vec4(pos, 1.); vUv = uv; vCol = col; vPos = pos; }`;
  const FS = `#version 300 es
precision highp float; in vec2 vUv; in vec4 vCol; in vec3 vPos; out vec4 color;
uniform sampler2D tex; uniform vec3 eye; uniform vec3 fog; uniform vec2 fogRange; uniform float light;
void main(){
  vec4 s = texture(tex, vUv);
  float d = distance(vPos, eye), haze = smoothstep(fogRange.x, fogRange.y, d);
  float near = vCol.a < .999 ? smoothstep(.7, 2.8, d) : 1.;  // air never fills the lens
  color = vec4(mix(s.rgb * vCol.rgb * light, fog, haze), vCol.a * near);
  if (color.a < .01) discard;
}`;
  let prog, mesh, atlas, F = null;

  // ---------- vectors (animatic.js has add, sub, scale, norm, cross, lerp, dot) ----------
  const len = a => Math.hypot(a[0], a[1], a[2]);
  const cl = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
  const sstep = k => { k = cl(k); return k * k * (3 - 2 * k); };
  const easeOut = k => 1 - Math.pow(1 - cl(k), 2);
  const rnd = n => { const a = Math.sin(n * 127.1 + 43.7) * 43758.5453; return a - Math.floor(a); };
  const sphereDir = (i, n) => { const y = 1 - 2 * (i + .5) / n, r = Math.sqrt(Math.max(0, 1 - y * y)), a = i * 2.399963; return [Math.cos(a) * r, y, Math.sin(a) * r]; };
  const yawAxes = yaw => { const f = [Math.cos(yaw), 0, Math.sin(yaw)]; return [f, UP, cross(f, UP)]; };
  function flowAxes(dir, up = UP, roll = 0) {
    const x = norm(dir); let ref = sub(up, scale(x, dot(up, x))); if (len(ref) < 1e-3) ref = Math.abs(x[0]) < .9 ? [1, 0, 0] : [0, 0, 1];
    let y = norm(sub(ref, scale(x, dot(ref, x)))), z = cross(x, y);
    if (roll) { const c = Math.cos(roll), s = Math.sin(roll); [y, z] = [add(scale(y, c), scale(z, s)), sub(scale(z, c), scale(y, s))]; }
    return [x, y, z];
  }

  // ---------- the cube collector: solid cubes first, then see-through air sorted back to front ----------
  const tone = n => .80 + .20 * n[1] + .07 * n[2] + .03 * n[0];
  class Cubes {
    constructor(ft, eye) { this.time = ft / 20; this.eye = eye; this.solid = []; this.air = []; this.count = 0; }
    box(c, half, axes, cell, col, alpha) {
      if (!(half[0] > 1e-4 && half[1] > 1e-4 && half[2] > 1e-4) || alpha <= .02) return;
      const [a, b, z] = axes.map((v, i) => scale(v, half[i])), P = (x, y, w) => add(c, add(scale(a, x), add(scale(b, y), scale(z, w))));
      const faces = [[P(1,-1,1),P(1,-1,-1),P(1,1,-1),P(1,1,1)], [P(-1,-1,-1),P(-1,-1,1),P(-1,1,1),P(-1,1,-1)],
        [P(-1,-1,1),P(1,-1,1),P(1,1,1),P(-1,1,1)], [P(1,-1,-1),P(-1,-1,-1),P(-1,1,-1),P(1,1,-1)],
        [P(-1,1,1),P(1,1,1),P(1,1,-1),P(-1,1,-1)], [P(-1,-1,-1),P(1,-1,-1),P(1,-1,1),P(-1,-1,1)]];
      const u0 = cell[0] / 4, v0 = cell[1] / 8, du = .25, dv = .125, uv = [[u0, v0 + dv], [u0 + du, v0 + dv], [u0 + du, v0], [u0, v0]];
      const out = [], air = alpha < 1;
      for (const f of faces) {
        const n = norm(cross(sub(f[1], f[0]), sub(f[2], f[0]))), t = tone(n), sh = air ? 1 - (1 - t) * .4 : t;
        for (const i of [0, 1, 2, 0, 2, 3]) out.push(f[i][0], f[i][1], f[i][2], uv[i][0], uv[i][1], col[0] * sh, col[1] * sh, col[2] * sh, Math.min(1, alpha));
      }
      if (air) this.air.push([len(sub(c, this.eye)), out]); else this.solid.push(out);
      this.count++;
    }
  }
  /** One wind cube: size is an edge or [x, y, z] along axes; alpha under 1 makes it see-through air. */
  function cube(m, p, size, {band = 'cloud', seed = 0, axes = [[1, 0, 0], [0, 1, 0], [0, 0, 1]], alpha = AIR, color = [1, 1, 1]} = {}) {
    const s = typeof size === 'number' ? [size, size, size] : size;
    const frame = Math.floor(m.time * FPS + rnd(seed + .77) * 4) % 4;
    m.box(p, scale(s, .5), axes, [frame, BAND[band]], color, alpha);
  }
  function streak(m, a, b, {width = .12, band = 'streak', alpha = .7, seed = 0, up = UP, color} = {}) {
    const d = sub(b, a), l = len(d); if (l < 1e-3 || width <= 1e-3) return;
    cube(m, scale(add(a, b), .5), [l, width, width], {band, alpha, seed, axes: flowAxes(d, up), color});
  }
  /** A puffy cloud that blooms, drifts and thins away; lower lobes take the shadowed mist band. */
  function puff(m, center, age, {size = .5, seed = 0, duration = .8, drift = [0, 0, 0], spread = .6, lobes = 4, alpha = AIR, band = 'cloud', color} = {}) {
    const k = age / duration; if (k < 0 || k >= 1) return;
    const grow = .45 + .55 * easeOut(k / .3), fade = 1 - sstep((k - .5) / .5), s = size * grow * (.35 + .65 * fade);
    const c = add(center, scale(drift, easeOut(k))), axes = yawAxes(rnd(seed) * TAU + k * .8);
    const a = alpha * (.55 + .45 * fade) * (1 - sstep((k - .7) / .3));
    cube(m, c, s, {band, seed, axes, alpha: a, color});
    for (let i = 0; i < lobes; i++) {
      const d = sphereDir(i + Math.floor(rnd(seed + i) * 3), lobes + 3), off = scale(d, s * (.42 + spread * k));
      cube(m, add(c, off), s * (.5 + .25 * rnd(seed + i * 1.7)), {band: d[1] < -.15 ? 'mist' : band, seed: seed + i + 1, axes, alpha: a, color});
    }
  }
  function ring(m, center, normal, radius, {count = 16, size = .12, stretch = 1.4, band = 'cloud', alpha = AIR, phase = 0, seed = 0, color} = {}) {
    if (radius <= 1e-3 || size <= 1e-3) return;
    const n = norm(normal), a0 = norm(cross(n, Math.abs(n[1]) > .9 ? [1, 0, 0] : UP)), b0 = cross(n, a0), arc = TAU * radius / count;
    for (let i = 0; i < count; i++) {
      const a = phase + TAU * i / count, radial = add(scale(a0, Math.cos(a)), scale(b0, Math.sin(a))), tangent = cross(n, radial);
      cube(m, add(center, scale(radial, radius)), [Math.min(arc * .85, size * stretch * 2.2), size, size], {band, seed: seed + i, axes: [tangent, n, radial], alpha, color});
    }
  }
  function wisp(m, p, dir, age, {size = .12, seed = 0, duration = .5, curl = 2.2, length = .6, alpha = .7, up = UP} = {}) {
    const k = age / duration; if (k < 0 || k >= 1) return;
    const x = norm(dir), y0 = norm(sub(up, scale(x, dot(up, x)))), y = scale(y0, rnd(seed) < .5 ? -1 : 1);
    const fade = 1 - sstep((k - .35) / .65), head = add(p, scale(x, length * easeOut(k)));
    for (let i = 0; i < 4; i++) {
      const a = i / 3 * curl, r = length * .35 * (1 - i * .18), q = add(head, add(scale(x, -Math.sin(a) * r), scale(y, (1 - Math.cos(a)) * r)));
      const s = size * (1 - i * .2) * fade; if (s <= .01) continue;
      cube(m, q, [s * 1.6, s, s], {band: i === 0 ? 'gale' : 'cloud', seed: seed + i, axes: flowAxes(add(scale(x, Math.cos(a)), scale(y, Math.sin(a))), up), alpha: alpha * (1 - i * .15)});
    }
  }
  function motes(m, origin, age, {count = 8, spread = .8, speed = 1.2, duration = .7, size = .05, seed = 0, dir = null} = {}) {
    const k = age / duration; if (k < 0 || k >= 1) return;
    for (let i = 0; i < count; i++) {
      const d = dir ? norm(add(dir, scale(sphereDir(i, count), .6))) : sphereDir(i, count);
      const p = add(origin, add(scale(sphereDir(i + 3, count + 5), spread * .3), scale(d, speed * duration * easeOut(k) * (.6 + .4 * rnd(seed + i)))));
      cube(m, p, size * (1 - k), {band: 'gale', seed: seed + i, alpha: 1});
    }
  }

  // ---------- the flight: hips and wand tip baked every tenth of a story tick ----------
  /** Hips and tip at story clock c (game ticks), interpolated. */
  function at(c) {
    const fl = F.flight, n = fl.length - 1;
    if (c <= fl[0][0]) return {hip: fl[0].slice(1, 4), tip: fl[0].slice(4, 7)};
    if (c >= fl[n][0]) return {hip: fl[n].slice(1, 4), tip: fl[n].slice(4, 7)};
    let lo = 0, hi = n; while (hi - lo > 1) { const mid = (lo + hi) >> 1; if (fl[mid][0] <= c) lo = mid; else hi = mid; }
    const a = fl[lo], b = fl[hi], f = (c - a[0]) / (b[0] - a[0] || 1);
    return {hip: lerp(a.slice(1, 4), b.slice(1, 4), f), tip: lerp(a.slice(4, 7), b.slice(4, 7), f)};
  }
  /** Wake samples every `step` blocks along the flight from clock c0 to c1: where, when, which way. */
  function wakeSamples(c0, c1, step) {
    const out = []; let last = null, prev = at(c0).hip;
    for (let c = c0; c <= c1; c += .05) {
      const p = at(c).hip, d = sub(p, prev);
      if (!last || len(sub(p, last.p)) >= step) {
        const f = len(d) > 1e-4 ? norm(d) : (last ? last.f : [0, 0, 1]);
        const side = norm(cross(f, Math.abs(f[1]) > .95 ? [1, 0, 0] : UP));
        last = {c, p, f, side}; out.push(last);
      }
      prev = p;
    }
    return out;
  }
  function prepare() {
    const L = track.landing, D = L.dashes;
    F = {flight: L.flight, dashes: D, floor: L.floor, touch: L.touch, stop: L.stop, low: L.low, touchAt: L.touch_at, arrival: L.arrival};
    D.forEach((d, i) => {
      const end = i < D.length - 1 ? D[i + 1].clock - .4 : L.touch + 3;
      d.wake = wakeSamples(d.clock, end, .62);
    });
    F.skid = wakeSamples(L.touch, L.stop, .3);
  }

  // ---------- the dashes ----------
  const sec = (ft, c) => (ft - c) / 20;  // seconds of story time since clock c
  function drawDash(m, ft, d, i) {
    const age = sec(ft, d.clock); if (age < -.02 || age > 6) return;
    const dir = d.dir, start = d.at, blue = d.chained ? PALE : [1, 1, 1];
    // The pressure ring snapping open behind you; a chained dash doubles it, pale blue.
    const open = sstep(age / .3), thin = 1 - sstep(age / .5);
    ring(m, add(start, scale(dir, -.8)), dir, .7 + 2.1 * open, {count: 20, size: .3 * thin, phase: age * 1.2, seed: i * 9, alpha: .78});
    if (d.chained) ring(m, add(start, scale(dir, -2.0)), dir, .5 + 1.6 * open, {count: 16, size: .24 * thin, band: 'streak', alpha: .72, phase: -age * 1.6, seed: i * 9 + 5, color: PALE});
    // The cloud burst that bleeds off your speed: puffs blown out round the turn and left behind.
    for (let k = 0; k < 9; k++) {
      const o = sphereDir(k, 9), out = norm(sub(o, scale(dir, dot(o, dir) * .8)));
      puff(m, add(start, scale(out, .5)), age - k * .012, {size: .95, seed: i * 31 + k, duration: 1.1, drift: add(scale(out, 2.2), scale(dir, -1.4)), lobes: 4, alpha: .6,
        band: d.chained && k % 2 ? 'streak' : 'cloud', color: d.chained && k % 2 ? PALE : undefined});
    }
    // The wake: a soft cloud trail born where you passed, widening into a V and drifting back,
    // left hanging in the sky; thin curled swooshes peel off the shoulders.
    const skim = i === F.dashes.length - 1;
    d.wake.forEach((q, j) => {
      const a = sec(ft, q.c); if (a < 0) return;
      const near = skim && q.c > F.low - 3;  // low over the snow the wake is smaller, rolls on a little past you and clears
      if (near && j % 2) return;
      for (const lane of [-1, 0, 1]) {
        const spread = lane ? (near ? .25 + .9 : .3 + 1.5) * (1 - Math.exp(-a / .7)) : 0, life = near ? (lane ? 1.3 : .9) : (lane ? 3.2 : 1.8);
        let p = add(add(q.p, scale(q.side, lane * spread)), add(scale(q.f, (near ? .7 : -1.2) * easeOut(a / 1.5)), [0, (lane ? .05 : .2) - .25 * a, 0]));
        if (near) p[1] = Math.max(F.floor + .25, p[1] - .4);
        puff(m, p, a, {size: (near ? .4 : lane ? .62 : .5) * (d.chained ? 1.12 : 1), seed: q.c * 97 + lane * 13 + i, duration: life, drift: scale(q.side, lane * .4), lobes: 3,
          alpha: near ? .42 : lane ? .52 : .44, band: d.chained && lane ? 'streak' : 'cloud', color: d.chained && lane ? PALE : undefined});
      }
      if (j % 5 === 2) for (const side of [-1, 1])
        wisp(m, add(add(q.p, scale(q.side, side * .45)), [0, .35, 0]), add(scale(q.f, -1), scale(q.side, side * .8)), a, {seed: q.c * 7 + side, duration: .6, size: .2, length: 1.3, curl: 2.6, up: scale(q.side, side)});
    });
    motes(m, start, age, {count: d.chained ? 12 : 8, dir: scale(dir, -1), seed: i * 3, duration: .6, speed: 3.2, size: .1});
  }
  /** The wand cutting the air: a chunky streak trailing its tip through each dash. */
  function drawTip(m, ft, tip, s) {
    const k = s.w_lead === undefined ? 1 : s.w_lead; if (k < .05) return;
    // Only through each dash's first burst: a short, chunky streak, never a long wire.
    const since = Math.min(...F.dashes.map(d => ft >= d.clock ? ft - d.clock : 99)), burst = 1 - sstep((since - 2) / 3);
    if (burst < .05) return;
    let prev = tip;
    for (let j = 1; j <= 2; j++) {
      const p = at(ft - j * .25).tip, w = .22 * (1 - j / 3) * k * burst;

      streak(m, prev, p, {width: w, band: j < 2 ? 'gale' : 'streak', alpha: .85 * (1 - j / 4) * k * burst, seed: j * 3.1});
      prev = p;
    }
  }

  /** The touchdown: a soft ring of cloud rolling out over the snow, and the skid's low spray of air. */
  function drawLanding(m, ft) {
    const g = F.floor, age = sec(ft, F.touch); if (age < 0) return;
    const c = F.touchAt;
    for (let k = 0; k < 14; k++) {
      const a = k / 14 * TAU + rnd(k) * .3, dir = [Math.cos(a), 0, Math.sin(a)];
      puff(m, [c[0] + dir[0] * .5, g + .22, c[2] + dir[2] * .5], age - rnd(k + 4) * .05, {size: .38, seed: 700 + k, duration: 1.1, drift: add(scale(dir, 2.2), [0, .15, 0]), lobes: 3, alpha: .5});
    }
    ring(m, [c[0], g + .12, c[2]], UP, .5 + 2.6 * sstep(age / .45), {count: 22, size: .16 * (1 - sstep(age / .6)), seed: 77, alpha: .75});
    F.skid.forEach((q, j) => {
      const a = sec(ft, q.c); if (a < 0) return;
      if (j % 2) return;
      for (const sgn of [-1, 1]) puff(m, [q.p[0] + q.side[0] * sgn * .5, g + .2, q.p[2] + q.side[2] * sgn * .5], a, {size: .3, seed: 900 + j * 7 + sgn, duration: 1,
        drift: add(scale(q.side, sgn * 1.2), add(scale(q.f, .8), [0, .3, 0])), lobes: 3, alpha: .5});
    });
  }

  // ---------- snow kicked up by the skim and the skid (solid, lit) ----------
  function snowSolids(ft) {
    const data = [], g = F.floor;
    const spray = (c0, c1, n, power, seed) => {
      for (let k = 0; k < n; k++) {
        const tk = c0 + (c1 - c0) * k / n, age = ft - tk; if (age < 0 || age > 24) continue;
        const o = at(tk).hip, h = at(tk - .3).hip, f = norm(sub([o[0], 0, o[2]], [h[0], 0, h[2]])), side = cross(f, UP), t = age / 20;
        for (let j = 0; j < 3; j++) {
          const r = q => rnd(seed + k * 13.1 + j * 3.7 + q), sgn = j % 2 ? 1 : -1, pw = power(k / n);
          const vel = add(add(scale(side, sgn * (1.5 + 2.5 * r(1)) * pw), scale(f, (1 + 3 * r(2)) * pw)), [0, (1.5 + 3.5 * r(3)) * pw, 0]);
          const p = add([o[0], g, o[2]], scale(vel, t)); p[1] = Math.max(g + .04, g + .08 + vel[1] * t - 6 * t * t);
          const z = (.05 + .06 * r(4)) * (1 - age / 24); if (z < .006) continue;
          box(data, [-z, -z * .8, -z], [z, z * .8, z], j % 3 ? [.93, .95, 1] : [.84, .88, .95], M.mul(M.t(...p), M.ry(r(5) * 6 + age * .2)));
        }
      }
    };
    spray(F.low - 1, F.touch, 22, v => .55, 11);             // the skim's wind ruffling the snow under you
    spray(F.touch, F.stop, 26, v => 1.3 * Math.pow(1 - v, 1.4), 41);  // the skid
    return data;
  }

  // ---------- hooks ----------
  LANDING.load = async (json, image) => {
    const img = await image(track.landing.atlas);
    atlas = texture(img);
    prog = program(VS, FS);
    mesh = vao(prog, [['pos', 3], ['uv', 2], ['col', 4]], 9);
    prepare();
  };
  LANDING.solids = (s, t) => (F ? snowSolids(s.n_clock) : []);
  LANDING.translucent = (s, t, view) => {
    if (!F) return;
    const ft = s.n_clock, m = new Cubes(ft, view.eye);
    F.dashes.forEach((d, i) => drawDash(m, ft, d, i));
    drawTip(m, ft, view.tip, s);
    drawLanding(m, ft);
    if (!m.count) return;
    gl.useProgram(prog.p); gl.uniformMatrix4fv(prog.u.viewProj, false, view.vp); gl.uniform3fv(prog.u.eye, view.eye);
    gl.uniform3fv(prog.u.fog, view.fog); gl.uniform2fv(prog.u.fogRange, view.fogRange); gl.uniform1f(prog.u.light, 1.12 + 1.2 * s.flash);
    gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, atlas); gl.uniform1i(prog.u.tex, 0);
    gl.bindVertexArray(mesh.va);
    const drawData = d => { if (!d.length) return; upload(mesh, d, gl.DYNAMIC_DRAW); gl.drawArrays(gl.TRIANGLES, 0, d.length / 9); };
    gl.disable(gl.BLEND); gl.depthMask(true);
    drawData(m.solid.flat());
    m.air.sort((a, b) => b[0] - a[0]);
    // Keep the frame opaque: blending alpha too would let the page's black show through the air.
    gl.enable(gl.BLEND); gl.blendFuncSeparate(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA, gl.ZERO, gl.ONE); gl.depthMask(false);
    const air = []; for (const [, v] of m.air) for (const x of v) air.push(x);
    drawData(air);
  };
  LANDING.hud = (t, s, w, h) => {
    if (s.w_slow > .01) {  // the slow motion: the frame's edges cool a little
      const g = ctx.createRadialGradient(w / 2, h / 2, h * .32, w / 2, h / 2, Math.hypot(w, h) * .56);
      g.addColorStop(0, 'rgba(14,30,52,0)'); g.addColorStop(1, `rgba(14,30,52,${.5 * s.w_slow})`);
      ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
    }
    if (s.w_speed > .01) {  // speed lines streaming past
      ctx.strokeStyle = `rgba(255,255,255,${.36 * s.w_speed})`; ctx.lineWidth = Math.max(1, h * .0022);
      ctx.beginPath();
      const R = Math.hypot(w, h) / 2;
      for (let i = 0; i < 110; i++) {
        const a = hash(i * 1.3) * Math.PI * 2, ph = (hash(i * 2.7) + t * .08 * (.6 + hash(i * 3.1))) % 1;
        const r0 = R * (.4 + .6 * ph), l = R * (.08 + .18 * hash(i * 4.3)) * s.w_speed;
        ctx.moveTo(w / 2 + Math.cos(a) * r0, h / 2 + Math.sin(a) * r0); ctx.lineTo(w / 2 + Math.cos(a) * (r0 + l), h / 2 + Math.sin(a) * (r0 + l));
      }
      ctx.stroke();
    }
  };
})();
