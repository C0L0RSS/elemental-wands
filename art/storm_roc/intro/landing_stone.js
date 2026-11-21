// Stone's landing in the Storm Roc intro (art/storm_roc/intro/stone_landing.py bakes the track):
// Gathered Mass tears rocks and earth out of the summit and packs them round the falling player into
// one big round boulder; it crashes down onto the arrival, the summit cracks open round the crater,
// and after a beat the ball cracks and breaks apart, the rock falling away and crumbling on the snow.
// Everything is boxes (oriented cubes), soft puffs and additive glow sprites, so the game can draw it
// the same way. Loaded by animatic.js through track.landing; registers its hooks on LANDING.
//
// What it reads. Track columns, per game tick (s.<name>):
//   n_clock   the story clock in game ticks: every effect's age is measured on it (slow motion included)
//   st_ball   [x, y, z] the ball's centre in the summit frame
//   st_roll   the ball's roll in radians about +X (chunk slots are in the ball's frame)
//   st_tip    [x, y, z] the wand's tip
//   st_glow   0..1 the wand tip's pale light while it gathers
//   st_speed  0..1 speed lines over the frame
//   st_bt     0..1 the slow motion's dark edge
//   st_drop   0..1 dust shed off the boulder as it drops
// track.landing: radius, quarry, arrival, bursts [{kind: rip|impact|crack|break, clock, tick, at}],
// and chunks: see chunkLocal and chunks() below.
(() => {
  'use strict';
  const L = () => track.landing;
  const GRAV = 32;                       // blocks/s/s, as the throw
  const SEC = a => a / 20;               // story-clock game ticks to seconds
  const GREYS = [[.62, .64, .64], [.55, .57, .58], [.69, .71, .7], [.5, .52, .53], [.58, .61, .6]];
  const COBBLE = [.44, .46, .47], DARK = [.33, .34, .37], DIRT = [[.46, .38, .3], [.4, .33, .26]], SNOW = [.93, .95, 1];
  const LIGHT = [.8, .82, .81], CRACK = [.16, .17, .19], PIT = [.38, .39, .42], DUST = [.86, .86, .84];
  const ground = () => track.summit.arrival[1];
  const burst = kind => L().bursts.find(b => b.kind === kind);
  const easeOut = u => 1 - Math.pow(1 - clamp(u, 0, 1), 3);

  const rotOnly = m => { const r = new Float32Array(m); r[12] = r[13] = r[14] = 0; return r; };
  const origin = m => [m[12], m[13], m[14]];
  function axisRot(axis, a) {
    const [x, y, z] = norm(axis), c = Math.cos(a), s = Math.sin(a), k = 1 - c;
    return new Float32Array([c + x*x*k, y*x*k + z*s, z*x*k - y*s, 0, x*y*k - z*s, c + y*y*k, z*y*k + x*s, 0,
      x*z*k + y*s, y*z*k - x*s, c + z*z*k, 0, 0, 0, 0, 1]);
  }
  /** A chunk's slot frame in the ball's frame: columns basis[0], basis[1], basis[2] (its normal),
   * origin at centre (the normal times the radius less its half depth). */
  function chunkLocal(c) {
    if (!c._m) { const [x, y, z] = c.basis; c._m = new Float32Array([...x, 0, ...y, 0, ...z, 0, ...c.centre, 1]); }
    return c._m;
  }
  /** A chunk's boxes into data at m (scaled by k); its crack line grown by crack (0..1). */
  function drawChunk(data, c, m, crack, k) {
    if (k !== undefined && k !== 1) m = M.mul(m, M.s(k, k, k));
    for (const [a, b, col] of c.boxes) box(data, a, b, col, m);
    if (crack > 0) for (const [a, b, i] of c.cracks) {
      const g = clamp(crack * 3 - i, 0, 1); if (g <= 0) continue;
      beam(data, M.apply(m, a), M.apply(m, lerp(a, b, g)), .035, CRACK);
    }
  }
  const halfOf = c => c.boxes[0][1];
  /** The ball: at st_ball, rolled st_roll radians about +X. */
  const ballMatrix = s => M.quat(s.st_ball, [Math.cos(s.st_roll / 2), Math.sin(s.st_roll / 2), 0, 0]);

  // ---------- small physics: a piece thrown from p with velocity v (blocks/s), resting on the snow
  function thrown(p, v, sec, rest) {
    const y0 = p[1] - rest, g = ground();
    const tl = (v[1] + Math.sqrt(Math.max(0, v[1] * v[1] + 2 * GRAV * Math.max(0, y0 - g)))) / GRAV;  // when it lands
    const t = Math.min(sec, tl);
    return {pos: [p[0] + v[0] * t, Math.max(g + rest, p[1] + v[1] * t - GRAV / 2 * t * t), p[2] + v[2] * t], landed: sec >= tl, since: sec - tl, tl};
  }

  // ---------- the ball
  function chunks(data, s, ft) {
    const C = L().chunks, ball = ballMatrix(s), crackAt = burst('crack').clock;
    for (let i = 0; i < C.length; i++) {
      const c = C[i];
      if (ft < c.launch) continue;
      const W = M.mul(ball, chunkLocal(c));
      if (ft < c.arrive) {
        // Torn out of the snow and flying up to the ball, tumbling, homing on its slot.
        const u = (ft - c.launch) / (c.arrive - c.launch), e = Math.pow(u, 1.7), target = origin(W);
        const src = [c.src[0], ground() - .3, c.src[2]];
        const side = norm(cross(sub(target, src), [0, 1, 0]));
        const pos = add(lerp(src, target, e), add(scale(side, c.curve * 2.4 * Math.sin(Math.PI * e)), [0, .5 * smooth(u / .15) * (1 - e), 0]));
        drawChunk(data, c, [M.t(...pos), rotOnly(W), axisRot(c.spin, (1 - u) * 9)].reduce(M.mul), 0, 1.5 - .5 * smooth(u));
        for (let k = 1; k <= 2; k++) {  // gravel dragged along behind it
          const uk = u - .06 * k; if (uk <= 0) continue;
          const ek = Math.pow(uk, 1.7), q = add(lerp(src, target, ek), add(scale(side, c.curve * 2.4 * Math.sin(Math.PI * ek)), [(hash(i * 9 + k) - .5) * .6, (hash(i * 5 + k) - .5) * .6, (hash(i * 3 + k) - .5) * .6]));
          const z = .05 + .04 * hash(i + k * 7);
          box(data, [-z, -z, -z], [z, z, z], k === 2 ? DIRT[i % 2] : COBBLE, [M.t(...q), axisRot(c.spin, ft * .4 + k)].reduce(M.mul));
        }
        continue;
      }
      if (ft < c.detach) {
        // Packed on: slammed into place, cracked once the beat is over.
        const age = ft - c.arrive, pop = 1 + .15 * Math.exp(-age / 1.2) * (age < 6);
        drawChunk(data, c, W, clamp((ft - crackAt - c.crackDelay) / 4, 0, 1), pop);
        if (age < 12) for (let k = 0; k < 4; k++) {  // chips knocked off as it lands
          const r = q => hash(i * 17.3 + k * 4.1 + q), a = r(0) * Math.PI * 2, out = norm(M.dir(W, [0, 0, 1]));
          const p = add(origin(W), scale(add(out, [Math.cos(a) * .6, 0, Math.sin(a) * .6]), (2 + 3 * r(1)) * SEC(age)));
          const z = .045 * (1 - age / 12);
          box(data, [-z, -z, -z], [z, z, z], k % 2 ? LIGHT : GREYS[k % 5], [M.t(...p), M.ry(a + age), M.rx(age * .7)].reduce(M.mul));
        }
        continue;
      }
      const age = ft - c.detach, sec = SEC(age), r = q => hash(i * 13.7 + q);
      if (c.stay) {
        // The bottom of the ball: left bedded in the crater, slumping and crumbling.
        const k = 1 - .5 * smooth(age / 30), sink = .4 * smooth(age / 24);
        drawChunk(data, c, M.mul(M.t(0, -sink, 0), W), 1, k);
        continue;
      }
      // Falling away: thrown off the ball, tumbling, breaking on the snow into pieces that crumble.
      const fall = thrown(origin(W), c.vel, sec, Math.min(...halfOf(c)));
      if (!fall.landed) {
        drawChunk(data, c, [M.t(...fall.pos), axisRot(c.spin, sec * 4 * (r(5) + .4)), rotOnly(W)].reduce(M.mul), 1);
        continue;
      }
      const big = Math.max(...halfOf(c)), hit = fall.pos, since = fall.since;
      for (let k = 0; k < 3; k++) {
        const rr = q => hash(i * 31.1 + k * 7.7 + q), a = rr(0) * Math.PI * 2, z = big * (.3 + .1 * rr(1));
        const piece = thrown(add(hit, [Math.cos(a) * z, 0, Math.sin(a) * z]), [Math.cos(a) * (.8 + 1.4 * rr(2)) + c.vel[0] * .35, 1 + 1.6 * rr(3), Math.sin(a) * (.8 + 1.4 * rr(2)) + c.vel[2] * .35], since, z * .7);
        const rest = piece.landed ? piece.since * 20 : 0, crumble = 1 - .6 * smooth(rest / 30);
        const zz = z * crumble, col = k === 2 ? COBBLE : c.boxes[0][2], spin = piece.landed ? piece.tl : since;
        box(data, [-zz, -zz * .7, -zz * .9], [zz, zz * .7, zz * .9], col,
          [M.t(...sub(piece.pos, [0, z * .7 - zz * .7, 0])), M.ry(a + spin * 3), M.rx(spin * 4 * (rr(4) - .5))].reduce(M.mul));
        for (let j = 0; j < 3; j++) {  // gravel crumbling off it
          const g = smooth((rest - 4 - j * 8) / 12); if (g <= 0) continue;
          const b = add(piece.pos, [(hash(i * 3 + k * 5 + j) - .5) * z * 3.2, 0, (hash(i * 7 + k * 3 + j) - .5) * z * 3.2]);
          const gz = .03 * g; b[1] = ground() + gz * .6;
          box(data, [-gz, -gz * .6, -gz], [gz, gz * .6, gz], j % 2 ? COBBLE : LIGHT, M.mul(M.t(...b), M.ry(j + k)));
        }
      }
    }
  }

  // ---------- cracks in the snow, the quarry's pits
  function crackLines(data, centre, ft, at, n, len0, len1, seed, grow, r0) {
    const g0 = ground();
    for (let k = 0; k < n; k++) {
      const ang = k / n * Math.PI * 2 + hash(seed + k) * .5, len = len0 + (len1 - len0) * hash(seed + k * 3.3);
      const g = easeOut((ft - at - hash(seed + k * 2.1) * 1.5) / grow); if (g <= 0) continue;
      let p = [centre[0] + Math.cos(ang) * r0, g0 + .03, centre[2] + Math.sin(ang) * r0], dir = ang;
      for (let j = 0; j < 6; j++) {
        dir += (hash(seed + k * 6 + j) - .5) * .9;
        const step = len / 6 * clamp(g * 6 - j, 0, 1); if (step <= 0) break;
        const q = [p[0] + Math.cos(dir) * step, p[1], p[2] + Math.sin(dir) * step];
        leaf(data, p, q, .2 * (1 - j / 7), CRACK);
        if (j % 2 === 1) {  // a broken edge kicked up along it
          const z = .08 * (1 - j / 8), m = [M.t(q[0], g0 + z * .5, q[2]), M.ry(dir + j), M.rz(.5)].reduce(M.mul);
          box(data, [-z, -z * .5, -z * 1.6], [z, z * .5, z * 1.6], j % 3 ? GREYS[(k + j) % 5] : COBBLE, m);
        }
        p = q;
      }
    }
  }
  function quarry(data, ft) {
    const rip = burst('rip'), g0 = ground();
    if (ft < rip.clock) return;
    crackLines(data, rip.at, ft, rip.clock, 8, 2.4, 4.8, 11, 5, .5);
    const C = L().chunks;
    for (let i = 0; i < C.length; i++) {
      const c = C[i], age = ft - c.launch; if (age < 0) continue;
      const open = easeOut(age / 3), r0 = (.35 + .25 * hash(i * 1.7)) * open;
      fan(data, c.src, g0 + .025, a => r0 * (.8 + .25 * Math.sin(a * 3 + i) + .12 * Math.sin(a * 7 + i * 2)), PIT, 9);
      fan(data, c.src, g0 + .03, a => r0 * .55 * (.8 + .2 * Math.sin(a * 5 + i)), DARK, 7);
      for (let k = 0; k < 5; k++) {  // snow, earth and gravel flicked up as it tears out
        const r = q => hash(i * 23.3 + k * 3.9 + q), a = r(0) * Math.PI * 2, z = .04 + .05 * r(1);
        const piece = thrown([c.src[0], g0 + .1, c.src[2]], [Math.cos(a) * (1 + 2.2 * r(2)), 2.5 + 4 * r(3), Math.sin(a) * (1 + 2.2 * r(2))], SEC(age), z);
        box(data, [-z, -z * .8, -z], [z, z * .8, z], k < 2 ? SNOW : k < 4 ? GREYS[k] : DIRT[k % 2], [M.t(...piece.pos), M.ry(a + (piece.landed ? piece.tl : SEC(age)) * 6)].reduce(M.mul));
      }
    }
  }

  // ---------- the crater: the summit cracked open, slabs kicked up round it, rubble and snow thrown out
  const SLABS = Array.from({length: 16}, (_, i) => ({a: i / 16 * Math.PI * 2 + hash(i + 40) * .3, r: 2.15 + .5 * hash(i + 41), w: .22 + .16 * hash(i + 42),
    h: .18 + .22 * hash(i + 43), d: .09 + .05 * hash(i + 44), tilt: .55 + .5 * hash(i + 45), col: i % 4 === 1 ? COBBLE : GREYS[i % 5], delay: hash(i + 46) * 1.2}));
  function crater(data, ft) {
    const b = burst('impact'), age = ft - b.clock, g0 = ground(), c = b.at;
    if (age < 0) return;
    const open = easeOut(age / 2.2);
    fan(data, c, g0 + .018, a => 2.9 * open * (.85 + .12 * Math.sin(a * 5 + 1) + .06 * Math.sin(a * 11)), [.56, .57, .6], 48);
    fan(data, c, g0 + .024, a => 2.1 * open * (.86 + .1 * Math.sin(a * 6 + 2) + .05 * Math.sin(a * 13)), PIT, 48);
    fan(data, c, g0 + .03, a => 1.3 * open * (.82 + .18 * Math.sin(a * 4)), DARK, 32);
    crackLines(data, c, ft, b.clock, 12, 3, 7.5, 61, 6, 2.2);
    for (const s of SLABS) {  // kicked up round the rim, tilted out, snow still on top
      const g = clamp((age - s.delay) / 3, 0, 1); if (g <= 0) continue;
      const up = backEase(g), at = [c[0] + Math.cos(s.a) * s.r, g0, c[2] + Math.sin(s.a) * s.r];
      const m = [M.t(...at), M.ry(-s.a + Math.PI / 2), M.rx(-s.tilt * up)].reduce(M.mul);
      box(data, [-s.w, -.12, -s.d], [s.w, s.h * up, s.d], s.col, m);
      box(data, [-s.w * .9, s.h * up, -s.d * 1.05], [s.w * .9, s.h * up + .05, s.d * 1.05], SNOW, m);
    }
    for (let i = 0; i < 70; i++) {  // rubble and snow thrown out, landing; the snow melts away
      const r = q => hash(i * 7.31 + q + 500), a = r(0) * Math.PI * 2, out = 3 + 8 * r(1), z = .05 + .12 * r(2) * r(2);
      const piece = thrown([c[0] + Math.cos(a) * 1.6, g0 + .3, c[2] + Math.sin(a) * 1.6], [Math.cos(a) * out, 3.5 + 9 * r(3), Math.sin(a) * out], SEC(age), z * .7);
      const pick = r(4), col = pick < .3 ? SNOW : pick < .45 ? DIRT[i % 2] : pick < .6 ? COBBLE : GREYS[i % 5];
      const melt = pick < .3 ? 1 - smooth((age - 24) / 36) : 1; if (melt <= .02) continue;
      const spin = piece.landed ? piece.tl : SEC(age);
      box(data, [-z * melt, -z * .7 * melt, -z * .9 * melt], [z * melt, z * .7 * melt, z * .9 * melt], col, [M.t(...piece.pos), M.ry(a + spin * 7), M.rx(spin * 9 * (r(5) - .5))].reduce(M.mul));
    }
  }

  LANDING.solids = (s, t, view) => {
    const data = [], ft = s.n_clock;
    if (!L()) return data;
    quarry(data, ft);
    crater(data, ft);
    chunks(data, s, ft);
    return data;
  };

  // ---------- dust and light. Each puff is [centre, size, colour]; one opacity per draw.
  function puffs(list, opacity, view) {
    if (!list.length || opacity <= .005) return;
    const prog = progs.puff, d = [];
    for (const [c, size, col] of list) for (const [x, y] of CORNERS) d.push(...c, x, y, ...col, size);
    gl.useProgram(prog.p); gl.uniformMatrix4fv(prog.u.viewProj, false, view.vp); gl.uniform3fv(prog.u.right, view.right); gl.uniform3fv(prog.u.up, view.up);
    gl.uniform3fv(prog.u.eye, view.eye); gl.uniform3fv(prog.u.fog, view.fog); gl.uniform2fv(prog.u.fogRange, view.fogRange); gl.uniform1f(prog.u.opacity, opacity);
    gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); gl.depthMask(false);
    upload(meshes.puff, d, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.puff.va); gl.drawArrays(gl.TRIANGLES, 0, d.length / 9);
  }
  LANDING.translucent = (s, t, view) => {
    if (!L()) return;
    const ft = s.n_clock, g0 = ground(), C = L().chunks, ball = ballMatrix(s);
    // The quarry's ring of snow dust as it breaks open.
    const rip = burst('rip'), ra = ft - rip.clock;
    if (ra >= 0 && ra < 34) {
      const list = [], spread = 1 - Math.exp(-ra / 5);
      for (let i = 0; i < 18; i++) { const a = i / 18 * Math.PI * 2 + hash(i) * .4, r = .5 + 4 * spread * (.75 + .5 * hash(i * 3));
        list.push([[rip.at[0] + Math.cos(a) * r, g0 + .3 + .4 * spread * hash(i * 5), rip.at[2] + Math.sin(a) * r], .6 + .8 * spread, [.93, .94, .96]]); }
      puffs(list, .55 * (1 - smooth(ra / 34)), view);
    }
    // Snow puffing where each rock tears out; grit where each packs on (shrinking as they fade).
    const torn = [], packed = [], broke = [];
    for (let i = 0; i < C.length; i++) {
      const c = C[i], la = ft - c.launch, aa = ft - c.arrive, da = ft - c.detach;
      if (la >= 0 && la < 18) for (let k = 0; k < 2; k++) torn.push([[c.src[0] + (hash(i + k) - .5) * .6, g0 + .25 + la * .03 + k * .15, c.src[2] + (hash(i * 2 + k) - .5) * .6], (.3 + .5 * (1 - Math.exp(-la / 4))) * (1 - la / 18), [.92, .93, .94]]);
      if (aa >= 0 && aa < 10) { const W = M.mul(ball, chunkLocal(c)); packed.push([add(origin(W), M.dir(W, [0, 0, .25])), (.2 + .4 * (1 - Math.exp(-aa / 3))) * (1 - aa / 10), [.8, .8, .79]]); }
      if (da > 0 && da < 40 && !c.stay) {  // where it breaks on the snow
        const W = M.mul(ball, chunkLocal(c)), fall = thrown(origin(W), c.vel, SEC(da), Math.min(...halfOf(c)));
        if (fall.landed && fall.since < .8) { const k = fall.since / .8; broke.push([add(fall.pos, [0, .15 + .25 * k, 0]), (.25 + .45 * k) * (1 - k * .5), DUST]); }
      }
    }
    puffs(torn, .5, view); puffs(packed, .45, view); puffs(broke, .3, view);
    // Dust shed off the boulder as it drops.
    if (s.st_drop > .02) {
      const list = [];
      for (let k = 1; k <= 12; k++) { const p = colAt('st_ball', t - k * .45); list.push([[p[0] + (hash(k) - .5) * .8, p[1] + (hash(k * 3) - .5) * .8, p[2] + (hash(k * 5) - .5) * .8], .5 + .1 * k, [.8, .8, .79]]); }
      puffs(list, .4 * s.st_drop, view);
    }
    // The impact: a fast ring along the snow, then the dust rolling out and settling.
    const b = burst('impact'), ia = ft - b.clock;
    if (ia >= 0 && ia < 12) {
      const u = ia / 12, r = 1.8 + 7 * easeOut(u), list = [];
      for (let i = 0; i < 64; i++) { const a = i / 64 * Math.PI * 2 + hash(i) * .1; if (hash(i + 7) < .2) continue; list.push([[b.at[0] + Math.cos(a) * r, g0 + .12, b.at[2] + Math.sin(a) * r], .35 + .3 * u, [.95, .95, .95]]); }
      puffs(list, .38 * (1 - u), view);
    }
    if (ia >= 0 && ia < 46) {
      const spread = 1 - Math.exp(-ia / 6), list = [];
      for (let i = 0; i < 20; i++) { const a = i / 20 * Math.PI * 2 + hash(i + 70) * .3, r = 2 + 3.4 * spread * (.75 + .5 * hash(i * 3 + 70));
        list.push([[b.at[0] + Math.cos(a) * r, g0 + .25 + .4 * spread * hash(i * 5 + 70), b.at[2] + Math.sin(a) * r], .35 + .6 * spread, i % 3 ? [.93, .93, .92] : DUST]); }
      puffs(list, .48 * (1 - smooth(ia / 46)), view);
    }
    // Grit puffing out of the cracks; a ring of dust as it breaks apart.
    const ca = ft - burst('crack').clock;
    if (ca >= 0 && ca < 16) {
      const list = [];
      for (let i = 0; i < C.length; i += 4) { const W = M.mul(ball, chunkLocal(C[i])); list.push([add(origin(W), M.dir(W, [0, 0, .3 + ca * .02])), (.15 + .3 * (1 - Math.exp(-ca / 4))), DUST]); }
      puffs(list, .4 * (1 - ca / 16), view);
    }
    const br = burst('break'), ba = ft - br.clock;
    if (ba >= 0 && ba < 40) {
      const spread = 1 - Math.exp(-ba / 6), list = [];
      for (let i = 0; i < 16; i++) { const a = i / 16 * Math.PI * 2 + hash(i + 30) * .4, r = 1.6 + 2 * spread * (.8 + .4 * hash(i + 31));
        list.push([[br.at[0] + Math.cos(a) * r, g0 + .3 + .5 * spread * hash(i + 32), br.at[2] + Math.sin(a) * r], .35 + .5 * spread, i % 2 ? DUST : [.92, .92, .91]]); }
      puffs(list, .3 * (1 - smooth(ba / 40)), view);
    }
    // The wand's pull: a small pale light at its tip while it gathers.
    if (s.st_glow > .02) {
      const prog = progs.glow, d = [], f = 1 + .1 * Math.sin(ft * 1.7);
      const spr = (p, col, size, k) => { for (const [x, y] of CORNERS) d.push(...p, x, y, col[0] * k, col[1] * k, col[2] * k, size); };
      spr(s.st_tip, [.72, .8, .74], (.18 + .3 * s.st_glow) * f, .35 * s.st_glow); spr(s.st_tip, [1, 1, .96], (.06 + .1 * s.st_glow) * f, .5 * s.st_glow);
      gl.useProgram(prog.p); gl.uniformMatrix4fv(prog.u.viewProj, false, view.vp); gl.uniform3fv(prog.u.right, view.right); gl.uniform3fv(prog.u.up, view.up);
      gl.uniform1f(prog.u.opacity, 1); gl.uniform3fv(prog.u.eye, view.eye); gl.blendFunc(gl.ONE, gl.ONE);
      upload(meshes.glow, d, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.glow.va); gl.drawArrays(gl.TRIANGLES, 0, d.length / 9);
      gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
    }
  };

  // ---------- over the frame: speed lines in the fall and the drop, a dark edge on the slow motion
  LANDING.hud = (t, s, w, h) => {
    if (s.st_bt > .01) {
      const g = ctx.createRadialGradient(w / 2, h / 2, h * .3, w / 2, h / 2, Math.hypot(w, h) * .55);
      g.addColorStop(0, 'rgba(14,15,17,0)'); g.addColorStop(1, `rgba(14,15,17,${.55 * s.st_bt})`);
      ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
    }
    if (s.st_speed > .01) {
      ctx.strokeStyle = `rgba(255,255,255,${.38 * s.st_speed})`; ctx.lineWidth = Math.max(1, h * .0022);
      ctx.beginPath();
      const R = Math.hypot(w, h) / 2;
      for (let i = 0; i < 110; i++) {
        const a = hash(i * 1.3) * Math.PI * 2, ph = (hash(i * 2.7) + t * .08 * (.6 + hash(i * 3.1))) % 1;
        const r0 = R * (.4 + .6 * ph), len = R * (.08 + .18 * hash(i * 4.3)) * s.st_speed;
        ctx.moveTo(w / 2 + Math.cos(a) * r0, h / 2 + Math.sin(a) * r0); ctx.lineTo(w / 2 + Math.cos(a) * (r0 + len), h / 2 + Math.sin(a) * (r0 + len));
      }
      ctx.stroke();
    }
  };
})();
