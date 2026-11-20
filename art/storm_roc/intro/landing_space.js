// Space's landing in the Storm Roc intro (art/storm_roc/intro/space_landing.py): the Blink Rift the
// player folds into a few blocks above the snow, the burst of snow their momentum still blows out
// where they would have hit, the rift they step out of beside it. Drawn in block space's void glass
// (art/space/blockspace/kit.js): dark see-through glass, opaque void lenses framed in bright rim,
// starlight motes; space falls inward, only bursts move out. Loaded by animatic.js through LANDING;
// the game's port is client/roclanding/SpaceLanding.java.
'use strict';
(() => {
  const L = () => track.landing;
  const ev = kind => L().events.find(e => e.kind === kind);
  /** Seconds since an event, on the story clock (so it slows with the slow motion). */
  const age = (s, e) => e ? (s.n_clock - e.clock) / 20 : -1;
  const rnd = n => hash(n * 1.618 + .37);
  const TAU = Math.PI * 2;
  const sstep = x => smooth(x);

  // ---- void glass, as flat colour bands (blockspace.png's rows) ----
  const BAND = {
    void: [.06, .035, .11, 1], rim: [.84, .44, 1, 1], starlight: [.97, .95, 1, 1],
    blue: [.62, .82, 1, 1], glass: [.26, .15, .46, .55], nebula: [.5, .22, .52, .5], frame: [.55, .34, .9, .8],
  };
  const OPAQUE = new Set(['void', 'rim', 'starlight', 'blue']);
  let OPQ = [], GLS = [];
  /** An oriented box: centre p, extents along axes [x, y, z]. */
  function piece(p, size, band, axes) {
    const s = typeof size === 'number' ? [size, size, size] : size, a = axes || [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
    const m = new Float32Array([...a[0], 0, ...a[1], 0, ...a[2], 0, ...p, 1]);
    const col = BAND[band] || band;
    box(OPAQUE.has(band) ? OPQ : GLS, [-s[0] / 2, -s[1] / 2, -s[2] / 2], [s[0] / 2, s[1] / 2, s[2] / 2], col, m);
    if (band === 'glass' || band === 'nebula') {  // the glass's bright frame: its edges, a little proud
      const f = .035, e = BAND.frame;
      for (const [i, j, k] of [[0, 1, 2], [1, 2, 0], [2, 0, 1]]) for (const u of [-1, 1]) for (const v of [-1, 1]) {
        const c = [0, 0, 0]; c[j] = u * s[j] / 2; c[k] = v * s[k] / 2;
        const lo = [0, 0, 0], hi = [0, 0, 0]; lo[i] = -s[i] / 2; hi[i] = s[i] / 2; lo[j] = c[j] - f / 2; hi[j] = c[j] + f / 2; lo[k] = c[k] - f / 2; hi[k] = c[k] + f / 2;
        box(GLS, lo, hi, e, m);
      }
    }
  }
  const sphereDir = (i, n) => { const y = 1 - 2 * (i + .5) / n, r = Math.sqrt(Math.max(0, 1 - y * y)), a = i * 2.399963; return [Math.cos(a) * r, y, Math.sin(a) * r]; };
  /** A basis whose second axis is dir. */
  function basis(dir, roll) {
    const y = norm(dir), ref = Math.abs(y[1]) > .92 ? [1, 0, 0] : [0, 1, 0];
    let x = norm(cross(y, ref)), z = cross(x, y);
    if (roll) { const c = Math.cos(roll), s = Math.sin(roll); [x, z] = [add(scale(x, c), scale(z, s)), sub(scale(z, c), scale(x, s))]; }
    return [x, y, z];
  }
  /** A basis whose first axis runs along dir. */
  function flow(dir, roll) { const b = basis(dir, roll || 0); return [b[1], b[2], b[0]]; }
  const tumble = (seed, t, rate) => basis(sphereDir(Math.floor(rnd(seed) * 29), 29), rnd(seed + .3) * TAU + t * rate);

  /** How open a rift slit is age seconds after it appears: snaps open, holds, then pinches shut. */
  function openness(a, {open = .07, hold = .12, close = .32} = {}) {
    if (a < 0) return 0;
    if (a < open) return sstep(a / open);
    return 1 - sstep((a - open - hold) / close);
  }
  /** The eclipse slit: a lens of void rows framed by rim edges, long along up, turned to face the eye. */
  function slit(base, up, eye, {height = 2.2, width = .9, open = 1, seed = 0, rows = 11} = {}) {
    if (open <= .01) return;
    const u = norm(up), e = sub(eye, base), toEye = norm(sub(e, scale(u, dot(e, u)))), side = norm(cross(u, toEye)), axes = [side, u, toEye];
    const rowH = height / rows;
    for (let i = 0; i < rows; i++) {
      const v = (i + .5) / rows, shape = Math.pow(Math.sin(Math.PI * v), .75), w = Math.max(.04, width * open * shape);
      const half = Math.round(w * 8) / 16 + .03, p = add(base, scale(u, v * height));
      piece(p, [half * 2, rowH * 1.02, .08], 'void', axes);
      for (const sg of [-1, 1]) {
        piece(add(p, scale(side, sg * (half + .035))), [.07, rowH * 1.02, .1], 'rim', axes);
        piece(add(p, scale(side, sg * (half + .17))), [.16, rowH * .9, .06], 'glass', axes);
      }
    }
  }
  /** Shards of glass falling into c from round it, stretching along the fall, gone as they arrive. */
  function inflow(c, a, {count = 12, radius = 2, period = .4, seed = 0, size = .22} = {}) {
    for (let i = 0; i < count; i++) {
      const u = (a - rnd(seed + i) * period * .5) / period; if (u < 0 || u >= 1) continue;
      const d = sphereDir(i, count), p = add(c, scale(d, radius * (1 - u * u) * (.75 + .25 * rnd(seed + i * 3))));
      const z = size * sstep(u / .15) * (1 - u); if (z < .01) continue;
      piece(p, [z * (1 + 1.5 * u), z * .7, z * .22 + .015], i % 4 === 1 ? 'nebula' : i % 5 === 3 ? 'rim' : 'glass', flow(d, rnd(seed + i) * TAU));
    }
  }
  /** Shards spilling out of c, fast then slowing, shrinking away. */
  function spill(c, a, {count = 10, speed = 2.2, duration = .42, seed = 0, size = .22} = {}) {
    for (let i = 0; i < count; i++) {
      const u = a / duration; if (u < 0 || u >= 1) continue;
      const d = sphereDir(i, count), p = add(c, scale(d, speed * duration * (1 - (1 - u) * (1 - u)) * (.6 + .4 * rnd(seed + i))));
      const z = size * (1 - u); if (z < .01) continue;
      piece(p, [z, z, z * .25 + .015], i % 4 === 3 ? 'rim' : 'glass', tumble(seed + i, a, 6));
    }
  }
  /** Tiny twinkling starlight motes round c, falling in (or drifting out). */
  function stars(c, a, {count = 8, spread = 1, duration = 1, seed = 0, size = .06, inward = true} = {}) {
    for (let i = 0; i < count; i++) {
      const u = (a / duration + rnd(seed + i * .37)) % 1, d = sphereDir(i + Math.floor(rnd(seed + i) * 5), count + 5);
      const r = spread * (inward ? Math.pow(1 - u, 1.3) : u) * (.5 + .5 * rnd(seed + i * 3.1));
      const tw = .55 + .45 * Math.sin(a * 9 + i * 2.1), z = size * tw * sstep(u / .15) * (1 - sstep((u - .8) / .2)); if (z <= .008) continue;
      piece(add(c, scale(d, r)), z, i % 3 ? 'starlight' : 'blue', basis(d, Math.PI / 4));
    }
  }
  /** A ring of rim pieces round c facing n, radius r. */
  function ring(c, n, r, {count = 12, size = .07, phase = 0} = {}) {
    const b = basis(n), x = b[0], z = b[2];
    for (let i = 0; i < count; i++) {
      const a = i / count * TAU + phase, d = add(scale(x, Math.cos(a)), scale(z, Math.sin(a)));
      piece(add(c, scale(d, r)), [size * 1.6, size, size], 'rim', [norm(cross(n, d)), norm(n), d]);
    }
  }
  /** The departure: the lens open round the falling body, the space round it pulled in; a ring snapping shut. */
  function riftIn(e, a, eye) {
    if (a < 0 || a > .9) return;
    const c = e.at, up = e.axis, base = sub(c, scale(norm(up), 1.6));
    slit(base, up, eye, {height: 3.2, width: 1.15, open: openness(a, {open: .05, hold: .1, close: .28}), seed: 3, rows: 13});
    inflow(c, a, {count: 16, radius: 2.2, period: .34, seed: 5, size: .3});
    const close = (a - .05 - .1) / .28;
    if (close > 0 && close < 1) ring(c, norm(sub(eye, c)), mix(1.5, .05, sstep(close)), {count: 14, size: .09, phase: a * 4});
    stars(c, a, {count: 10, spread: 1.8, duration: .5, seed: 7, size: .08});
  }
  /** The arrival: a standing slit at the feet, shards and stars spilling back out. */
  function riftOut(e, a, eye) {
    if (a < 0 || a > .9) return;
    const feet = e.at, c = add(feet, [0, 1.1, 0]);
    slit(feet, [0, 1, 0], eye, {height: 2.4, width: 1.0, open: openness(a, {hold: .16}), seed: 13});
    spill(c, a, {count: 12, speed: 2.4, duration: .45, seed: 15, size: .24});
    stars(c, a, {count: 10, spread: 1.4, duration: .6, seed: 17, inward: false, size: .07});
  }
  // ---- the burst of snow where they would have hit ----
  const SNOW = [.93, .95, 1], PACK = [.8, .84, .92];
  function burstSolids(data, s) {
    const e = ev('burst'), a = age(s, e); if (a < 0) return;
    const g = L().floor, c = e.at;
    // A shallow crater of packed snow, its lip broken into blocks.
    const grow = 1 - Math.pow(1 - clamp(a / .12, 0, 1), 3);
    fan(data, c, g + .015, ang => 1.5 * grow * (.85 + .15 * Math.sin(ang * 5 + 1)), [.74, .78, .88], 28);
    for (let i = 0; i < 12; i++) {
      const ang = i / 12 * TAU + rnd(i) * .4, r = 1.6 * grow, z = (.18 + .14 * rnd(i + 4)) * grow;
      const p = [c[0] + Math.cos(ang) * r, g + z * .5, c[2] + Math.sin(ang) * r];
      box(data, [-z, -z * .5, -z], [z, z * .5, z], i % 3 ? SNOW : PACK, [M.t(...p), M.ry(ang + rnd(i))].reduce(M.mul));
    }
    // Chunks thrown out, up and away from the fall, tumbling down again.
    if (a < 1.6) for (let i = 0; i < 46; i++) {
      const r = k => rnd(i * 7.1 + k * 3.3), ang = r(0) * TAU, out = 2 + 6 * r(1), up = 4 + 9 * r(2), sec = a;
      const fwd = .5 + .8 * r(5);  // carried on along the fall a little
      const p = [c[0] + Math.cos(ang) * out * sec, Math.max(g + .05, g + .2 + up * sec - 16 * sec * sec), c[2] + Math.sin(ang) * out * sec + fwd * 3 * sec];
      const z = (.06 + .1 * r(4)) * (1 - sstep((a - 1) / .6)); if (z < .01) continue;
      box(data, [-z, -z * .8, -z], [z, z * .8, z], i % 3 ? SNOW : PACK, [M.t(...p), M.ry(ang + a * 4 * (r(6) - .5)), M.rx(a * 5 * (r(7) - .5))].reduce(M.mul));
    }
  }
  function burstPuffs(s, d) {
    const e = ev('burst'), a = age(s, e); if (a < 0 || a > 2.6) return 0;
    const g = L().floor, c = e.at, spread = 1 - Math.exp(-a / .35);
    for (let i = 0; i < 26; i++) {  // a ring rolling out low over the snow
      const ang = i / 26 * TAU + rnd(i) * .4, r = .5 + 3.2 * spread * (.75 + .5 * rnd(i * 3)), lift = .35 + .8 * spread * rnd(i * 5);
      for (const [x, y] of CORNERS) d.push(c[0] + Math.cos(ang) * r, g + lift, c[2] + Math.sin(ang) * r + .8 * spread, x, y, .96, .97, 1, .8 + 1.1 * spread);
    }
    for (let i = 0; i < 16; i++) {  // and a plume thrown up out of the middle
      const up = (1.2 + 4.2 * rnd(i + 40)) * (1 - Math.exp(-a / .25)), r = .4 + .9 * rnd(i + 60) * spread, ang = rnd(i + 80) * TAU;
      for (const [x, y] of CORNERS) d.push(c[0] + Math.cos(ang) * r, g + up, c[2] + Math.sin(ang) * r + .6 * spread, x, y, .97, .98, 1, .6 + 1.0 * spread);
    }
    return .8 * (1 - sstep((a - .3) / 1.1));
  }

  /** Snow flecks in the air round you as you fall, streaked by your speed: the snow rushing past. */
  function flecks(s, t) {
    const k = Math.max(s.sp_speed, s.sp_rush * .8); if (k < .02 || s.n_clock / 2 > L().hit_story) return;
    const here = s.player.pos, before = colAt('player', t - 1).slice(0, 3), v = sub(here, before), sp = Math.hypot(...v); if (sp < 1e-3) return;
    const dir = scale(v, 1 / sp), ax = flow(dir), cell = 14;
    for (let i = 0; i < 90; i++) {
      const seed = [rnd(i) * cell, rnd(i + 50) * cell, rnd(i + 90) * cell];
      const p = seed.map((x, j) => here[j] + (((x - here[j]) % cell) + cell * 1.5) % cell - cell / 2);
      const len = Math.min(2.4, sp * .45 * (.6 + .4 * rnd(i + 7)));
      box(GLS, [-len / 2, -.03, -.03], [len / 2, .03, .03], [1, 1, 1, .55 * k], new Float32Array([...ax[0], 0, ...ax[1], 0, ...ax[2], 0, ...p, 1]));
    }
  }

  /** The posed model's faces again, a little proud, as see-through violet glass. */
  let SHELL = [];
  function shellOver(model, mats, k, inflate) {
    if (k <= .01) return;
    for (const f of model.faces) {
      const m = mats[f.bone], n = norm(M.dir(m, f.n));
      if (!n.every(Number.isFinite)) continue;
      const p = f.pts.map(v => add(M.apply(m, v), scale(n, inflate)));
      if (Math.hypot(...cross(sub(p[1], p[0]), sub(p[2], p[0]))) < 1e-7) continue;
      for (const i of [0, 1, 2, 0, 2, 3]) SHELL.push(...p[i], ...n, .3, .16, .55, .62 * k);
    }
  }

  // ---- hooks ----
  /** Wind-carved drifts on the summit's snow round the landing, so the ground reads as it rushes up. */
  function drifts(data) {
    const g = L().floor, c = L().impact;
    for (let i = 0; i < 70; i++) {
      const a = rnd(i + 200) * TAU, r = 3 + 30 * Math.sqrt(rnd(i + 300)), x = c[0] + Math.cos(a) * r, z = c[2] - 6 + Math.sin(a) * r * 1.4;
      const len = 1.2 + 3 * rnd(i + 400), wid = .25 + .4 * rnd(i + 500), ang = .4 + .3 * rnd(i + 600), sh = .86 + .05 * rnd(i + 700);
      fan(data, [x, 0, z], g + .008, q => { const e = Math.abs(Math.cos(q - ang)); return wid + (len - wid) * e * e; }, [sh, sh + .02, sh + .07], 12);
    }
  }
  LANDING.solids = (s, t, view) => {
    const data = [];
    drifts(data);
    burstSolids(data, s);
    return data;
  };

  LANDING.translucent = (s, t, view) => {
    const {eye, right, up, vp, fog, fogRange} = view;
    OPQ = []; GLS = []; SHELL = [];
    flecks(s, t);
    // The player folding into the rift and unfolding out of the other: their own glass sheen.
    const fe = ev('fold'), fa = age(s, fe), ue = ev('unfold'), ua = age(s, ue);
    let sheen = 0;
    if (fa >= 0 && ua < 0) sheen = sstep(fa / .05);
    if (ua >= 0) sheen = clamp(1 - (ua - .03) / .4, 0, 1);
    if (sheen > .01 && s.sp_fold > .001) shellOver(view.model, view.mats, sheen, .025);
    riftIn(fe, fa, eye);
    riftOut(ue, ua, eye);
    const prog = progs.solid;
    gl.useProgram(prog.p); gl.uniform1i(prog.u.flat_, 1);
    if (OPQ.length) {
      gl.disable(gl.BLEND); gl.depthMask(true);
      upload(meshes.solid, OPQ, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.solid.va); gl.drawArrays(gl.TRIANGLES, 0, OPQ.length / 10);
    }
    gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); gl.depthMask(false);
    for (const list of [SHELL, GLS]) if (list.length) {
      upload(meshes.solid, list, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.solid.va); gl.drawArrays(gl.TRIANGLES, 0, list.length / 10);
    }
    gl.uniform1i(prog.u.flat_, 0);
    // Snow: the burst's ring and plume.
    const d = [], op = burstPuffs(s, d);
    if (d.length) {
      const pp = progs.puff;
      gl.useProgram(pp.p); gl.uniformMatrix4fv(pp.u.viewProj, false, vp); gl.uniform3fv(pp.u.right, right); gl.uniform3fv(pp.u.up, up);
      gl.uniform3fv(pp.u.eye, eye); gl.uniform3fv(pp.u.fog, fog); gl.uniform2fv(pp.u.fogRange, fogRange); gl.uniform1f(pp.u.opacity, op);
      upload(meshes.puff, d, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.puff.va); gl.drawArrays(gl.TRIANGLES, 0, d.length / 9);
    }
  };

  LANDING.hud = (t, s, w, h) => {
    if (s.sp_bt > .01) {  // the slow motion: the frame's edges darken toward the void
      const g = ctx.createRadialGradient(w / 2, h / 2, h * .3, w / 2, h / 2, Math.hypot(w, h) * .55);
      g.addColorStop(0, 'rgba(14,6,28,0)'); g.addColorStop(1, `rgba(14,6,28,${.6 * s.sp_bt})`);
      ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
    }
    if (s.sp_speed > .01) {  // speed lines streaming out of the middle
      ctx.strokeStyle = `rgba(255,255,255,${.38 * s.sp_speed})`; ctx.lineWidth = Math.max(1, h * .0022);
      ctx.beginPath();
      const R = Math.hypot(w, h) / 2;
      for (let i = 0; i < 120; i++) {
        const a = hash(i * 1.3) * Math.PI * 2, ph = (hash(i * 2.7) + t * .09 * (.6 + hash(i * 3.1))) % 1;
        const r0 = R * (.4 + .6 * ph), len = R * (.08 + .2 * hash(i * 4.3)) * s.sp_speed;
        ctx.moveTo(w / 2 + Math.cos(a) * r0, h / 2 + Math.sin(a) * r0); ctx.lineTo(w / 2 + Math.cos(a) * (r0 + len), h / 2 + Math.sin(a) * (r0 + len));
      }
      ctx.stroke();
    }
  };
})();
