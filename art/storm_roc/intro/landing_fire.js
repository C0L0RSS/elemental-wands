// Fire's landing in the Storm Roc intro (art/storm_roc/intro/fire_landing.py bakes the scene):
// a Flamethrower jet held straight down as a thruster, braking the fall; the flame cut a few
// blocks up; a Fire Leap landing with its ring of fire rolling out across the snow; low flames
// licking round the crouch. Everything here is "block fire" in the Fire spells' visual language:
// emissive cubes from the Fire atlas (art/fire/blockfire/textures/blockfire.png) that rise,
// tumble, cool through the heat bands and shrink away, with see-through smoke. The primitives
// (cube, flame, burst, smoke, sparkBurst, sparks) are ported from art/fire/blockfire/kit.js,
// the jet from its flamethrower scene and the landing ring from its leap scene.
// It plugs into animatic.js through the global LANDING hooks. Effects age on the story clock
// (s.n_clock, game ticks), so they slow with the slow motion.
(() => {
'use strict';
const TAU = Math.PI * 2, UPV = [0, 1, 0], AXES = [[1, 0, 0], [0, 1, 0], [0, 0, 1]];
const vadd = (a, b) => [a[0] + b[0], a[1] + b[1], a[2] + b[2]];
const vsub = (a, b) => [a[0] - b[0], a[1] - b[1], a[2] - b[2]];
const vmul = (a, k) => [a[0] * k, a[1] * k, a[2] * k];
const vdot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
const vcross = (a, b) => [a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]];
const vlen = a => Math.hypot(a[0], a[1], a[2]);
const vnorm = a => { const l = vlen(a) || 1; return [a[0] / l, a[1] / l, a[2] / l]; };
const cl = (v, a = 0, b = 1) => Math.max(a, Math.min(b, v));
const lerp1 = (a, b, t) => a + (b - a) * t;
const sstep = t => { t = cl(t); return t * t * (3 - 2 * t); };
const rnd = n => { const a = Math.sin(n * 127.1 + 43.7) * 43758.5453; return a - Math.floor(a); };
const noise = x => { const i = Math.floor(x), f = x - i, u = f * f * (3 - 2 * f); return lerp1(rnd(i), rnd(i + 1), u); };

// ---------- The cube mesh: opaque emissive fire and see-through smoke, as blockfire's engine.js ----------
const BAND = {core: 0, yellow: 1, orange: 2, deep: 3, ember: 4, smoke: 5, char: 6, magma: 7};
const HEAT = ['ember', 'deep', 'orange', 'yellow', 'core'];
const heatBand = i => HEAT[cl(Math.round(i), 0, 4)];
const FPS = 8, SMOKE_SCALE = .7, SMOKE_ALPHA = .5;
const STEAM = [2.1, 2.12, 2.2];  // smoke cubes tinted pale: snow boiled off by the jet
let F = null;  // the frame being built: {time (s), eye, fire: [], smoke: []}

/** Minecraft-style directional shade baked into each face (engine.js faceTone). */
const faceTone = n => .80 + .20 * n[1] + .07 * n[2] + .03 * n[0];
const FACES = [[[1,-1,1],[1,-1,-1],[1,1,-1],[1,1,1]], [[-1,-1,-1],[-1,-1,1],[-1,1,1],[-1,1,-1]],
  [[-1,-1,1],[1,-1,1],[1,1,1],[-1,1,1]], [[1,-1,-1],[-1,-1,-1],[-1,1,-1],[1,1,-1]],
  [[-1,1,1],[1,1,1],[1,1,-1],[-1,1,-1]], [[-1,-1,-1],[1,-1,-1],[1,-1,1],[-1,-1,1]]];
function boxq(center, half, axes, cell, color, see) {
  // Cubes near the lens shrink away (SpellViewClearance), so nothing fills the frame.
  const d = vlen(vsub(center, F.eye)), h = Math.max(half[0], half[1], half[2]), hidden = .5 + h * .9, visible = hidden + Math.max(1.2, h);
  const k = sstep((d - hidden) / (visible - hidden)); if (k <= 0) return;
  if (!(half[0] * k > 1e-4 && half[1] * k > 1e-4 && half[2] * k > 1e-4)) return;
  const a = vmul(axes[0], half[0] * k), b = vmul(axes[1], half[1] * k), c = vmul(axes[2], half[2] * k);
  const u0 = cell[0] / 4, v0 = cell[1] / 8, uv = [[u0, v0 + 1 / 8], [u0 + 1 / 4, v0 + 1 / 8], [u0 + 1 / 4, v0], [u0, v0]];
  const out = see ? F.smoke : F.fire;
  for (const face of FACES) {
    const q = face.map(([x, y, z]) => [center[0] + a[0] * x + b[0] * y + c[0] * z, center[1] + a[1] * x + b[1] * y + c[1] * z, center[2] + a[2] * x + b[2] * y + c[2] * z]);
    const s = see ? 1 : faceTone(vnorm(vcross(vsub(q[1], q[0]), vsub(q[2], q[0]))));
    for (const i of [0, 1, 2, 0, 2, 3]) out.push(q[i][0], q[i][1], q[i][2], uv[i][0], uv[i][1], color[0] * s, color[1] * s, color[2] * s, color[3]);
  }
}
/** One cube: a full edge length or [x, y, z] extents along axes; band names an atlas row. */
function cube(p, size, {band = 'orange', seed = 0, axes = AXES, alpha = SMOKE_ALPHA, tint = null} = {}) {
  const s = typeof size === 'number' ? [size, size, size] : size, row = BAND[band];
  const frame = ((Math.floor(F.time * FPS + rnd(seed + .77) * 4) % 4) + 4) % 4, see = row === BAND.smoke;
  boxq(p, [s[0] / 2, s[1] / 2, s[2] / 2], axes, [frame, row], tint ? [...tint, see ? alpha : 1] : [1, 1, 1, see ? alpha : 1], see);
}

// ---------- kit.js, ported ----------
function basis(dir, roll = 0) {
  const y = vnorm(dir), ref = Math.abs(y[1]) > .92 ? [1, 0, 0] : UPV;
  let x = vnorm(vcross(y, ref)), z = vcross(x, y);
  if (roll) { const c = Math.cos(roll), s = Math.sin(roll); [x, z] = [vadd(vmul(x, c), vmul(z, s)), vsub(vmul(z, c), vmul(x, s))]; }
  return [x, y, z];
}
function yawAxes(yaw) { const f = [Math.cos(yaw), 0, Math.sin(yaw)]; return [f, UPV, vcross(f, UPV)]; }
function rotate(v, k, a) { const c = Math.cos(a), s = Math.sin(a); return vadd(vadd(vmul(v, c), vmul(vcross(k, v), s)), vmul(k, vdot(k, v) * (1 - c))); }
function tumble(seed, t, speed = 2) {
  const k = vnorm([rnd(seed + .1) - .5, rnd(seed + .2) - .5, rnd(seed + .3) - .5]), a = rnd(seed + .4) * TAU + t * speed * (rnd(seed + .5) < .5 ? -1 : 1);
  return AXES.map(v => rotate(v, k, a));
}
function sphereDir(i, count) { const y = 1 - 2 * (i + .5) / count, r = Math.sqrt(Math.max(0, 1 - y * y)), a = i * 2.399963; return [Math.cos(a) * r, y, Math.sin(a) * r]; }

/** A flame tongue: a flickering hot base cluster and cubes rising from it along dir, cooling to the tip. */
function flame(root, dir, {width = .5, length = 1.2, seed = 0, heat = 1, sway = .08, count = 0, flicker = 1, speed = 1, t = F.time} = {}) {
  if (length <= .01 || width <= .01) return;
  const d = vnorm(dir), roll = rnd(seed + .9) * TAU, [bx, , bz] = basis(d, roll), top = heat * 3;
  const L = length * (1 - .15 * flicker + .3 * flicker * noise(t * 4.1 + seed * 7.1));
  const n = count || cl(Math.round(L / width * 2.2), 3, 8);
  const leanA = (noise(t * 1.6 + seed * 3.3) - .5) * 2 * sway, leanB = (noise(t * 1.9 + seed * 5.9) - .5) * 2 * sway;
  const bw = width * (.92 + .16 * noise(t * 6.7 + seed * 2.3)), base = vadd(root, vmul(d, bw * .36));
  for (let j = 0; j < 3; j++) {
    const a = roll + j * TAU / 3, s = bw * (.68 + .16 * noise(t * 6.1 + seed * 2.3 + j * 3.1));
    cube(vadd(base, vadd(vmul(bx, Math.cos(a) * bw * .17), vmul(bz, Math.sin(a) * bw * .17))), [s, s * .92, s], {band: heatBand(top - (j ? .7 : 0)), seed: seed + j * 9, axes: basis(d, roll + j * .55)});
  }
  const period = cl(.32 + L * .2, .32, 1.1) / speed;
  for (let i = 0; i < n; i++) {
    const phase = t / period + i / n + rnd(seed + i * 1.31) * .35 / n, loop = Math.floor(phase), u = phase - loop, k = seed + i * 4.1 + loop * 17.3;
    const w = bw * .74 * Math.pow(1 - u, .8) * (.85 + .3 * rnd(k)); if (w < .012) continue;
    const side = vadd(vmul(bx, (rnd(k + 1.7) - .5) * width * .4 * u + leanA * L * u * u), vmul(bz, (rnd(k + 2.9) - .5) * width * .4 * u + leanB * L * u * u));
    cube(vadd(vadd(base, vmul(d, L * Math.pow(u, .85))), side), [w, w * 1.15, w], {band: heatBand(top - u * 2.5), seed: seed + i, axes: basis(d, roll + (rnd(k + 3.3) - .5) * 1.2 + u * .6)});
  }
}
/** A ball of fire that blooms and burns out; age in seconds. */
function burst(center, age, {radius = 2, count = 24, seed = 0, duration = .9, size = .45, up = .35, heat = 1, smoke: withSmoke = true, flash = true} = {}) {
  if (age < 0) return; const life = age / duration; if (life > 2.2) return;
  if (flash && life < .45) {
    const k = life / .45, s = size * 2.3 * Math.pow(Math.sin(k * Math.PI), .6);
    cube(center, s, {band: heatBand(heat * 4 - k * 1.6), seed, axes: tumble(seed, age, 3)});
    cube(center, s * .86, {band: heatBand(heat * 4 - k * 1.6 - 1), seed: seed + 1, axes: tumble(seed + 1, age, -3)});
  }
  for (let i = 0; i < count; i++) {
    const delay = rnd(seed + i * 1.7) * .12, l = cl((life - delay) / (1 - delay)); if (l <= 0 || l >= 1) continue;
    const dir = vnorm(vadd(sphereDir(i, count), [0, up, 0])), r = radius * (1 - Math.pow(1 - l, 2.2)) * (.55 + .45 * rnd(seed + i * 3.3));
    const p = vadd(vadd(center, vmul(dir, r)), [0, .5 * l * l, 0]);
    const s = size * (.6 + .6 * rnd(seed + i * 4.9)) * Math.min(1, l * 8) * Math.pow(1 - l, 1.1);
    cube(p, s, {band: heatBand(heat * 4 - l * 4.2 + rnd(seed + i * 2.3) * .6), seed: seed + i, axes: tumble(seed + i, age, 4)});
  }
  if (withSmoke) for (let i = 0; i < Math.ceil(count / 5); i++) {
    const start = .3 + rnd(seed + i * 8.1) * .2, l = (life - start) / 1.6; if (l <= 0 || l >= 1) continue;
    const dir = vnorm(vadd(sphereDir(i * 3 + 1, count), [0, .8, 0]));
    smoke(vadd(vadd(center, vmul(dir, radius * .65)), [0, .2, 0]), l * 1.6 * duration, {size: size * .9, seed: seed + i + 300, duration: 1.6 * duration, rise: radius * .8});
  }
}
/** A smoke puff (or steam, tinted) that swells, rises and shrinks away: a body and a smaller cap. */
function smoke(p, age, {size = .5, seed = 0, rise = 1.4, duration = 1.2, scale = 1, tint = null, alpha = SMOKE_ALPHA} = {}) {
  const l = age / duration; if (l <= 0 || l >= 1) return;
  const s = SMOKE_SCALE * size * scale * Math.pow(Math.sin(l * Math.PI), .7) * (.8 + .4 * rnd(seed)); if (s <= .01) return;
  const q = vadd(p, [(rnd(seed + 1) - .5) * .6 * l, rise * l, (rnd(seed + 2) - .5) * .6 * l]);
  const tilt = (rnd(seed + 3) - .5) * .3, axes = yawAxes(rnd(seed + 4) * TAU + age * (rnd(seed + 5) < .5 ? -.5 : .5)).map(v => rotate(v, [1, 0, 0], tilt));
  const a = alpha * Math.min(1, (1 - l) * 2.5);
  cube(q, [s * 1.15, s * .78, s * 1.15], {band: 'smoke', seed, axes, alpha: a, tint});
  const ang = rnd(seed + 6) * TAU, c = s * .62;
  cube(vadd(q, vadd(vmul(axes[0], Math.cos(ang) * s * .22), vadd(vmul(axes[2], Math.sin(ang) * s * .22), vmul(axes[1], s * (.42 + .25 * l))))), [c * 1.1, c * .8, c * 1.1], {band: 'smoke', seed: seed + 7, axes, alpha: a, tint});
}
/** One-shot spark cubes thrown up and out from c, falling under g; each winks out as it cools. */
function sparkBurst(c, age, {count = 12, speed = 2.5, up = 3, life = .65, seed = 0, size = .08, g = 8, dir = null, bias = 0, cool = 2.4} = {}) {
  if (age <= 0) return;
  for (let i = 0; i < count; i++) {
    const li = life * (.6 + .7 * rnd(seed + i * 1.3)), l = age / li; if (l <= 0 || l >= 1) continue;
    const a = rnd(seed + i * 2.1) * TAU, h = speed * (.35 + .65 * rnd(seed + i * 3.7)), v = up * (.45 + .7 * rnd(seed + i * 4.9));
    let vel = [Math.cos(a) * h, v, Math.sin(a) * h]; if (dir) vel = vadd(vel, vmul(dir, bias * speed));
    const p = vadd(c, [vel[0] * age, vel[1] * age - g * age * age / 2, vel[2] * age]);
    cube(p, size * (1 - l) * (.7 + .6 * rnd(seed + i * 5.3)), {band: heatBand(3.6 - l * cool), seed: seed + i, axes: tumble(seed + i, age, 6)});
  }
}
/** Tiny rising spark cubes on looping lives around origin. */
function sparks(origin, t, {count = 10, spread = .6, rise = 1.6, seed = 0, size = .08} = {}) {
  for (let i = 0; i < count; i++) {
    const period = .7 + rnd(seed + i * 1.9) * .7, u = ((t / period + rnd(seed + i * 2.7)) % 1 + 1) % 1, a = rnd(seed + i * 3.1) * TAU, r = spread * Math.sqrt(rnd(seed + i * 4.3));
    const p = vadd(origin, [Math.cos(a) * r + Math.sin(u * 5 + i) * .08, rise * u, Math.sin(a) * r + Math.cos(u * 4 + i) * .08]);
    cube(p, size * (1 - u) * (.7 + .6 * rnd(seed + i * 5.7)), {band: heatBand(3.4 - u * 2.6), seed: seed + i, axes: tumble(seed + i, t, 5)});
  }
}

// ---------- The thruster: the Flamethrower held straight down ----------
// As the Flamethrower scene's stream (flamethrower.js), but longer, wider and hotter, and carried
// with the wand like a rocket's exhaust: each puff flies out along the jet from where the tip is
// now, along the way the wand pointed when it left (so the jet bends as the arm swings). Puffs
// that reach the snow splash and spread across it, boiling it off as steam.
const JET = {spread: .26, d0: .15, ease: 2.2, rate: 10, maxAge: 1.3};  // rate: puffs per game tick
function jet(s, t, tip, L) {
  const ft = s.n_clock, {c0, c1} = L.jet, floor = L.floor, REACH = L.reach;
  if (ft < c0 || ft > c1 + JET.maxAge * 20) return;
  // The cut: the jet coughs in widening gaps for a few ticks, then stops.
  const gate = e => { if (e < c1 - 4) return 1; if (e >= c1) return 0; const q = (e - (c1 - 4)) / 4; return sstep((noise(e * .5 + 3.7) - (.15 + .6 * q)) / .12); };
  const k0 = Math.ceil(Math.max(c0, ft - JET.maxAge * 20) * JET.rate), k1 = Math.floor(Math.min(ft, c1) * JET.rate);
  // Once it cuts, what is still in the air flies on from where the tip was, the way it was pointing.
  const cutAt = L.bursts.find(b => b.kind === 'cut'), late = ft > c1, dNow = late ? colAt('f_dir', cutAt.tick) : s.f_dir;
  if (late) tip = cutAt.at;
  for (let k = k0; k <= k1; k++) {
    const e = k / JET.rate, sd = k * 7.31 + 11; if (rnd(sd + 4.4) > gate(e)) continue;
    const age = (ft - e) / 20, power = colAt('f_power', t - (ft - e) / Math.max(.25, s.f_rate)), young = cl((e - c0) / 3);
    const dThen = colAt('f_dir', Math.min(t - (ft - e) / Math.max(.25, s.f_rate), cutAt.tick)), D = vnorm(vadd(vmul(dNow, .45), vmul(dThen, .55)));
    let R = vcross(D, UPV); if (vlen(R) < .1) R = [1, 0, 0]; R = vnorm(R); const VU = vcross(R, D);
    const a = rnd(sd + .1) * TAU, r = Math.sqrt(rnd(sd + .2)) * .95, radial = vadd(vmul(R, Math.cos(a)), vmul(VU, Math.sin(a)));
    const dir = vnorm(vadd(D, vmul(radial, r * JET.spread))), o = vadd(tip, vmul(radial, r * .06));
    const life = .46 + .16 * rnd(sd + .3), reach = REACH * (.9 + .12 * rnd(sd + .4)) * (.75 + .25 * young), j = .8 + .4 * rnd(sd + .5);
    const hitT = dir[1] < -1e-3 ? (o[1] - (floor + .04)) / -dir[1] : Infinity, stop = Math.max(JET.d0 + .05, hitT - .15), blocked = stop < reach;
    const uHit = blocked ? 1 - Math.pow(1 - (stop - JET.d0) / (reach - JET.d0), 1 / JET.ease) : 1;
    const sAt = u => JET.d0 + (reach - JET.d0) * (1 - Math.pow(1 - u, JET.ease));
    const fOf = x => cl((x - JET.d0) / (REACH - JET.d0));
    const widthAt = x => j * power * (.13 + .85 * Math.pow(fOf(x), 1.05)) * sstep((x - JET.d0) / .35);
    const top = 3.7 + .5 * (rnd(sd + .05) - .5), bandAt = x => Math.max(1, top - 2.6 * fOf(x));
    const u = age / life;
    if (u < Math.min(uHit, 1)) {
      const x = sAt(u), p = vadd(o, vmul(dir, x)), sp = Math.pow(1 - u, JET.ease - 1);
      let w = widthAt(x); if (!blocked) w *= 1 - sstep((u - .7) / .3);
      const kx = vnorm([rnd(sd + .8) - .5, rnd(sd + .9) - .5, rnd(sd + 1) - .5]), th = age * 5 * sstep((u - .08) / .45);
      const ax = basis(dir, rnd(sd + .6) * TAU + age * (rnd(sd + .7) < .5 ? -3 : 3)).map(v => rotate(v, kx, th)), hb = bandAt(x);
      cube(p, [w, w * (1 + .8 * sp), w], {band: heatBand(hb), seed: sd, axes: ax});
      if (w > .2) for (let i = 0; i < 2; i++) { const sg = rnd(sd + i * 1.9 + 5) < .5 ? -1 : 1; cube(vadd(p, vmul(ax[i * 2], sg * w * .5)), w * .55, {band: heatBand(hb - .8), seed: sd + i + 1, axes: ax}); }
      if (rnd(sd + 3) < .12) {  // a spark flicks out of the side of the stream
        const u0 = .15 + .35 * rnd(sd + 3.1), sa = (age - u0 * life) / .35;
        if (sa > 0 && sa < 1 && u0 < uHit) {
          const start = vadd(o, vmul(dir, sAt(u0))), drift = vadd(vadd(vmul(radial, 1.1), vmul(dir, .8)), [0, .5, 0]);
          cube(vadd(start, vmul(drift, 1.4 * (1 - Math.pow(1 - sa, 2)))), .08 * (1 - sa), {band: heatBand(3.6 - 2 * sa), seed: sd + 7, axes: ax});
        }
      }
    }
    if (blocked) {
      // Splash: flattened against the snow, spreading out across it, then burning out; steam boils off.
      const ageH = uHit * life, sDur = .34 + .2 * rnd(sd + 1.1), v = (age - ageH) / sDur; if (v < 0) continue;
      const surf = vadd(o, vmul(dir, hitT));
      let tan = [dir[0], 0, dir[2]], jit = [rnd(sd + 1.2) - .5, 0, rnd(sd + 1.4) - .5];
      tan = vnorm(vadd(tan, vmul(jit, 1.6)));
      const spread = (.5 + 1.5 * rnd(sd + 1.5)) * (.6 + .7 * (1 - uHit)) * power;
      const wH = Math.max(widthAt(stop), .14), hbH = bandAt(stop);
      const placeAt = vv => { const ws = wH * (1 + .35 * Math.sin(Math.min(vv * 2.5, 1) * Math.PI / 2)) * Math.pow(1 - vv, .75); return {c: vadd(vadd(surf, [0, .1 + ws * .25, 0]), vmul(tan, spread * (1 - Math.pow(1 - vv, 2)))), ws}; };
      if (v < 1) {
        const {c, ws} = placeAt(v), ax = basis(UPV, rnd(sd + 1.6) * TAU + age * 2.5);
        cube(c, [ws * 1.2, ws * .5, ws * 1.2], {band: heatBand(hbH - .2 - 1.6 * v), seed: sd, axes: ax});
        if (ws > .18) cube(vadd(c, vadd(vmul(tan, ws * .6), [0, ws * .2, 0])), ws * .5, {band: heatBand(hbH - .9 - 1.6 * v), seed: sd + 5, axes: ax});
      }
      if (rnd(sd + 2.1) < .14) { const {c} = placeAt(.8); smoke(vadd(c, [0, .1, 0]), age - ageH - sDur * .5, {size: .5 * j, seed: sd + 300, rise: 1.3, duration: .9, tint: STEAM, alpha: .22}); }
      continue;
    }
    if (rnd(sd + 2) < .16) {  // free puffs that reach the end turn to smoke
      const sa = age - .76 * life, dur = .65 + .15 * rnd(sd + 2.2);
      if (sa > 0 && sa < dur) smoke(vadd(vadd(o, vmul(dir, sAt(.76))), vmul(dir, .35 * sstep(sa / dur))), sa, {size: .4 * j, seed: sd + 200, rise: 1, duration: dur});
    }
  }
}
/** The wand's tip: heating before the jet lights, a hot nozzle flame while it burns, a cough of smoke as it cuts. */
function nozzle(s, t, tip, L) {
  const ft = s.n_clock, sec = ft / 20, {c0, c1} = L.jet, dir = s.f_dir;
  if (s.f_heat > .02 && ft < c0) {  // heating: the tip glowing up through the bands, sparks shed off it
    const hcube = .1 + .14 * s.f_heat;
    cube(tip, hcube, {band: heatBand(1 + 3 * s.f_heat), seed: 3, axes: tumble(3, sec, 3)});
    sparks(vsub(tip, [0, .1, 0]), sec, {count: Math.round(3 + 7 * s.f_heat), spread: .18, rise: .7, seed: 61, size: .07});
  }
  if (ft >= c0 && ft < c1 + 1) {
    const on = sstep((ft - c0) / 1.5) * (ft < c1 ? 1 : 1 - (ft - c1));
    flame(tip, dir, {width: .34 * on, length: .9 * on, seed: 5, heat: 1.35, count: 4, sway: .02, speed: 1.9, t: sec});
  }
  // The ignition: a hot bloom off the tip and sparks.
  const ign = L.bursts.find(b => b.kind === 'ignite'), ia = (ft - ign.clock) / 20;
  if (ia > 0 && ia < 1.2) {
    burst(vadd(tip, vmul(dir, .5)), ia, {radius: 1.3, count: 18, seed: 41, duration: .42, size: .48, up: 0, heat: 1, smoke: false});
    sparkBurst(vadd(tip, vmul(dir, .3)), ia, {count: 16, speed: 4, up: 1, life: .55, seed: 47, size: .09, g: 2, dir, bias: .8});
  }
  // The cut: dark, smoking tip; one last cough of smoke forward and dying sparks.
  const ca = (ft - c1) / 20;
  if (ca > 0 && ca < 2.5) {
    if (ca < 1) cube(tip, .15, {band: ca < .25 ? 'deep' : ca < .6 ? 'ember' : 'char', seed: 21, axes: basis(dir, Math.PI / 4)});
    for (let i = 0; i < 3; i++) {
      const side = (rnd(400 + i) - .5) * .5, fwd = .2 + .7 * rnd(405 + i);
      smoke(vadd(tip, vadd(vmul(dir, fwd), [side, .08 * (i % 4), (rnd(415 + i) - .5) * .4])), ca - i * .03, {size: .22 + .12 * rnd(410 + i), seed: 420 + i, rise: 1.2, duration: 1.0});
    }
    for (let i = 0; i < 6; i++) {
      const sa = (ca - .02 * i) / .45; if (sa <= 0 || sa >= 1) continue;
      const d = vnorm(vadd(vmul(dir, .8), [rnd(430 + i) - .5, .7 + .5 * rnd(440 + i), rnd(435 + i) - .5]));
      cube(vadd(tip, vmul(d, .8 * (1 - Math.pow(1 - sa, 2)))), .07 * (1 - sa), {band: heatBand(3 - 2.5 * sa), seed: 450 + i});
    }
    for (let i = 0; i < 4; i++) smoke(vadd(tip, [0, .1, 0]), ca - .7 - i * .35, {size: .14, seed: 460 + i, rise: .75, duration: .8});
  }
}

// ---------- The Fire Leap's landing, from the leap scene (leap.js) ----------
// The hot blast at the feet, sparks, and the knee-high wall of fire rolling out to 5 blocks across
// the snow, its wake rolling to a stop behind the crest. Here the wall stands a moment longer
// before it burns down, tips first, so the ring reads round you.
const WAVE_RANGE = 5, WAVE_SPEED = .65, COL_GAP = .4, COL_MIN = 14, COL_MAX = 78, WAKE_DT = .07, WAKE_LIFE = .18, TICK = .05;
const crest = a => Math.min(WAVE_RANGE, (a / TICK + 1) * WAVE_SPEED);
const WSTOP = (WAVE_RANGE / WAVE_SPEED - 1) * TICK;
const colAngle = i => i * 2.399963 + (rnd(i * 1.3 + 7) - .5) * .08;
const colCount = r => cl(TAU * r / COL_GAP, COL_MIN, COL_MAX);
const dieAt = i => WSTOP + .3 + .55 * rnd(i * 3.9);
const crestR = a => Math.min(WAVE_RANGE, crest(Math.min(a, WSTOP))) - .21;
function leaning(a, jitter, lean) { const k = [Math.sin(a), 0, -Math.cos(a)]; return yawAxes(a + jitter).map(v => rotate(v, k, lean)); }
function rolled(a, angle) { const k = [Math.sin(a), 0, -Math.cos(a)]; return yawAxes(a).map(v => rotate(v, k, angle)); }
function landing(s, L) {
  const b = L.bursts.find(x => x.kind === 'touch'), a = (s.n_clock - b.clock) / 20, TO = b.at, sec = s.n_clock / 20;
  if (a < 0 || a > 3.4) return;
  burst(vadd(TO, [0, .35, 0]), a, {radius: 1.5, count: 18, seed: 400, duration: .45, size: .44, up: .12, heat: 1, smoke: true});
  sparkBurst(vadd(TO, [0, .2, 0]), a, {count: 22, speed: 3.5, up: 3.2, life: .75, seed: 430});
  const rc = crestR(a), cols = colCount(rc), spawn = sstep(a / .05), moving = a < WSTOP, inner = 1 - sstep((rc - 1.3) / .4);
  for (let i = 0; i < Math.ceil(cols); i++) {
    const ang = colAngle(i), dir = [Math.cos(ang), 0, Math.sin(ang)];
    const g = cl(cols - i) * spawn, burn = 3 * cl((a - dieAt(i)) / .5), die = burn / 3;
    const kt = g * (1 - sstep(burn)), km = g * (1 - sstep(burn - 1)), kb = g * (1 - sstep(burn - 2));
    if (kb > .01) {
      const ri = rc + (rnd(i * 2.1 + 3) - .5) * .16 * Math.min(1, rc / 2), base = [TO[0] + dir[0] * ri, L.floor, TO[2] + dir[2] * ri];
      const hot = lerp1(3.5, 4.2, inner) - die * 2.4, jit = (rnd(i * 4.3) - .5) * .7, lean = (moving ? .12 : 0) + (rnd(i * 4.9) - .5) * .16;
      const ax = j => leaning(ang, jit + j * .5, lean), up = ax(0)[1], at = h => vadd(base, vmul(up, h));
      const tall = .9 + .2 * rnd(i * 6.7), w = (.38 + .1 * rnd(i * 3.7 + 1)) * (.92 + .16 * noise(sec * 6.3 + i * 1.7));
      const bh = w * .92 * tall * (.92 + .16 * noise(sec * 5.1 + i * 2.9)) * kb, mw = w * .72 * km, mh = w * .82 * tall * (.9 + .2 * noise(sec * 6.1 + i * 3.7)) * km;
      const tw = w * .46 * kt, th = w * .62 * tall * (.55 + .9 * noise(sec * 7.7 + i * 4.3)) * kt;
      const bTop = bh - .08, mTop = bTop + mh - .1;
      cube(at(bTop - bh / 2), [w * kb, bh, w * .94 * kb], {band: heatBand(hot - .8), seed: 700 + i, axes: ax(0)});
      if (km > .01) cube(at(mTop - mh / 2), [mw, mh, mw * .94], {band: heatBand(hot - 1.3), seed: 740 + i, axes: ax(1)});
      if (kt > .01) {
        const tipP = at(mTop - .07 + th / 2);
        cube(tipP, [tw, th, tw * .94], {band: heatBand(hot - 2.1), seed: 780 + i, axes: ax(2)});
        const u = ((sec / .28 + rnd(i * 5.3)) % 1 + 1) % 1, rs = w * .4 * Math.pow(1 - u, .9) * kt;
        if (rs > .02) cube(vadd(tipP, vadd(vmul(up, th * .2 + .36 * u), vmul(dir, .06 * u))), rs, {band: heatBand(hot - 1.9 - u * 1.6), seed: 820 + i, axes: leaning(ang, u * 1.4 + rnd(i * 6.1) * 3, u * .8)});
      }
    }
    if (i % 6 === 0) { const r = crestR(WSTOP); smoke([TO[0] + dir[0] * r, L.floor + .35, TO[2] + dir[2] * r], a - dieAt(i) - .12, {size: .32, rise: 1.2, duration: 1.0, seed: 880 + i}); }
    if (i % 2) continue;
    for (let n = 0; n * WAKE_DT <= WSTOP; n++) {
      const te = (n + rnd(i * 8.9 + n * .37)) * WAKE_DT; if (te > WSTOP || te > a || i >= colCount(crestR(te))) continue;
      const age = a - te, l = age / WAKE_LIFE; if (l >= 1) continue;
      const r = crestR(te) - .25, sz = (.24 + .08 * rnd(i * 9.7 + n)) * Math.pow(1 - l, 1.2);
      cube([TO[0] + dir[0] * (r + .4 * l), L.floor + .12 + .28 * l, TO[2] + dir[2] * (r + .4 * l)], sz, {band: heatBand(3.3 - l * 3.2), seed: 900 + i * 7 + n, axes: rolled(ang, l * 2.5 + n)});
    }
  }
}
/** Low flames licking round the crouch, embers in the scorched snow, sparks and wisps going up. */
function lingering(s, L) {
  const k = s.f_flames; if (k < .01) return;
  const sec = s.n_clock / 20, at = L.arrival, floor = L.floor;
  const spots = [[1.05, .2], [.95, 1.0], [1.25, 1.75], [.9, 2.5], [1.35, 3.1], [1.0, 3.75], [1.5, 4.45], [.85, 5.2], [1.2, 5.85], [2.1, .7], [2.3, 2.9], [2.0, 4.9]];
  const cam = Math.atan2(3.6, 1.2);  // the glare's camera sits low behind you this way: keep its side clear
  spots.forEach(([r, a], i) => {
    if (Math.abs(Math.atan2(Math.sin(a - cam), Math.cos(a - cam))) < .75) return;
    const grow = cl(k * 1.4 - i * .03), h = (.32 + .22 * rnd(i * 3.1)) * grow * (r > 1.8 ? .75 : 1); if (h < .02) return;
    const base = [at[0] + Math.cos(a) * r, floor, at[2] + Math.sin(a) * r];
    flame(base, [Math.cos(a) * .1, 1, Math.sin(a) * .1], {width: (.26 + .1 * rnd(i * 2.3)) * grow, length: h * 1.6, seed: 950 + i * 11, heat: .95, sway: .05, t: sec});
  });
  for (let i = 0; i < 22; i++) {  // embers: glowing coals pressed into the snow, dimming
    const a = rnd(i * 4.1 + 2) * TAU, r = .5 + 2.4 * Math.sqrt(rnd(i * 7.3 + 1)), z = (.07 + .06 * rnd(i * 2.2)) * k;
    cube([L.land_at[0] + Math.cos(a) * r, floor + z * .3, L.land_at[2] + Math.sin(a) * r], [z, z * .6, z], {band: heatBand(1.2 + 1.3 * noise(sec * 1.3 + i) * k), seed: 990 + i, axes: yawAxes(a)});
  }
  sparks([at[0], floor + .25, at[2]], sec, {count: Math.round(10 * k), spread: 1.7, rise: 2.2, seed: 1100, size: .08});
  for (let i = 0; i < 5; i++) {
    const per = 1.6, ph = sec / per + rnd(1200 + i), loop = Math.floor(ph), a = rnd(1210 + i + loop * 3.3) * TAU;
    smoke([at[0] + Math.cos(a) * 1.3, floor + .5, at[2] + Math.sin(a) * 1.3], (ph - loop) * per, {size: .22 * k, seed: 1220 + i + loop * 7, rise: 1.1, duration: per});
  }
}

// ---------- The mark on the snow ----------
/** A disc on the snow, its edge ragged. */
function disc(data, c, y, radius, col, n = 48) {
  for (let i = 0; i < n; i++) {
    const a0 = i / n * TAU, a1 = (i + 1) / n * TAU, r0 = radius(a0), r1 = radius(a1);
    for (const p of [[c[0], y, c[2]], [c[0] + Math.cos(a1) * r1, y, c[2] + Math.sin(a1) * r1], [c[0] + Math.cos(a0) * r0, y, c[2] + Math.sin(a0) * r0]]) data.push(...p, 0, 1, 0, ...col, 1);
  }
}
function ring(data, c, y, r0, r1, col, n = 72) {
  for (let i = 0; i < n; i++) {
    const a0 = i / n * TAU, a1 = (i + 1) / n * TAU, j0 = .12 * Math.sin(a0 * 7) + .08 * Math.sin(a0 * 13), j1 = .12 * Math.sin(a1 * 7) + .08 * Math.sin(a1 * 13);
    const P = (a, r) => [c[0] + Math.cos(a) * r, y, c[2] + Math.sin(a) * r];
    for (const p of [P(a0, r0 + j0), P(a1, r0 + j1), P(a1, r1 + j1), P(a0, r0 + j0), P(a1, r1 + j1), P(a0, r1 + j0)]) data.push(...p, 0, 1, 0, ...col, 1);
  }
}
function scorch(s, L) {
  const data = [], floor = L.floor, ft = s.n_clock;
  // Where the jet beat down: the snow melted back to wet grey, darkest in the middle.
  const m = s.f_melt;
  if (m > .02) {
    const R = a => 2.4 * m * (.86 + .1 * Math.sin(a * 5 + 1) + .06 * Math.sin(a * 11));
    disc(data, L.melt_at, floor + .006, R, [.7, .72, .77]);
    disc(data, L.melt_at, floor + .01, a => R(a) * .62, [.5, .5, .53]);
  }
  // The Fire Leap's ring: singed snow inside it, a black char line where the wall stood.
  const b = L.bursts.find(x => x.kind === 'touch'), a = (ft - b.clock) / 20;
  if (a > 0) {
    const rc = crestR(a) + .2, burnt = cl((a - WSTOP - .6) / 1.2);
    if (rc > .6) ring(data, b.at, floor + .014, rc - .32, rc + .02, [lerp1(.78, .5, burnt), lerp1(.78, .47, burnt), lerp1(.82, .47, burnt)]);
    disc(data, b.at, floor + .016, x => Math.min(1.6, rc) * (.8 + .12 * Math.sin(x * 6 + 2)), [.32, .29, .29]);
  }
  return data;
}

// ---------- Light: a little warm glow where the fire is hottest ----------
function glows(s, t, tip, L, view) {
  const d = [], ft = s.n_clock, sec = ft / 20;
  const spr = (p, col, size, k) => { if (k > .003) for (const [x, y] of CORNERS) d.push(...p, x, y, col[0] * k, col[1] * k, col[2] * k, size); };
  const ORANGE = [1, .52, .16], GOLD = [1, .78, .35];
  const {c0, c1} = L.jet;
  if (ft >= c0 && ft < c1 + 2) {
    const on = sstep((ft - c0) / 1.5) * (ft < c1 ? 1 : 1 - (ft - c1) / 2), f = 1 + .15 * Math.sin(sec * 37) + .1 * Math.sin(sec * 53);
    spr(tip, GOLD, 1.1 * f, .55 * on);
    const dir = s.f_dir, hit = dir[1] < -.2 ? (tip[1] - L.floor) / -dir[1] : 99;
    if (hit < L.reach + 1.5) { const g = vadd(tip, vmul(dir, hit)); spr([g[0], L.floor + .4, g[2]], ORANGE, 3.4 * on, .3 * on * cl((L.reach + 1.5 - hit) / 3)); }
  }
  if (s.f_heat > .05 && ft < c0) spr(tip, ORANGE, .5 + .5 * s.f_heat, .5 * s.f_heat);
  const b = L.bursts.find(x => x.kind === 'touch'), a = (ft - b.clock) / 20;
  if (a > 0 && a < .6) spr(vadd(b.at, [0, .6, 0]), GOLD, 2.4 + 3 * a, .6 * (1 - a / .6));
  if (s.f_flames > .02) spr([L.arrival[0], L.floor + .5, L.arrival[2]], ORANGE, 2.6, .2 * s.f_flames * (1 + .2 * Math.sin(sec * 13)));
  if (!d.length) return;
  const prog = progs.glow;
  gl.useProgram(prog.p); gl.uniformMatrix4fv(prog.u.viewProj, false, view.vp); gl.uniform3fv(prog.u.right, view.right); gl.uniform3fv(prog.u.up, view.up);
  gl.uniform1f(prog.u.opacity, 1); gl.uniform3fv(prog.u.eye, view.eye);
  gl.enable(gl.BLEND); gl.blendFunc(gl.ONE, gl.ONE); gl.depthMask(false);
  upload(meshes.glow, d, gl.DYNAMIC_DRAW); gl.bindVertexArray(meshes.glow.va); gl.drawArrays(gl.TRIANGLES, 0, d.length / 9);
}

// ---------- Drawing ----------
const CUBE_VS = `#version 300 es
in vec3 pos; in vec2 uv; in vec4 col; uniform mat4 viewProj; out vec2 vUv; out vec4 vCol; out vec3 vPos;
void main(){ gl_Position = viewProj * vec4(pos, 1.); vUv = uv; vCol = col; vPos = pos; }`;
const CUBE_FS = `#version 300 es
precision highp float; in vec2 vUv; in vec4 vCol; in vec3 vPos; out vec4 color;
uniform sampler2D tex; uniform vec3 eye, fog; uniform vec2 fogRange; uniform float light;
void main(){
  vec4 c = texture(tex, vUv);
  float haze = smoothstep(fogRange.x, fogRange.y, distance(vPos, eye)) * .7;
  color = vec4(mix(c.rgb * vCol.rgb * light, fog, haze), vCol.a);
}`;
let atlas, prog, mesh;
LANDING.load = async (json, image) => {
  atlas = texture(await image('blockfire.png'));
  prog = program(CUBE_VS, CUBE_FS);
  mesh = vao(prog, [['pos', 3], ['uv', 2], ['col', 4]], 9);
};
LANDING.solids = (s, t, view) => scorch(s, track.landing);
LANDING.translucent = (s, t, view) => {
  const L = track.landing;
  F = {time: s.n_clock / 20, eye: view.eye, fire: [], smoke: []};
  jet(s, t, view.tip, L);
  nozzle(s, t, view.tip, L);
  landing(s, L);
  lingering(s, L);
  const draw = (data, light) => {
    if (!data.length) return;
    gl.uniform1f(prog.u.light, light);
    upload(mesh, data, gl.DYNAMIC_DRAW); gl.bindVertexArray(mesh.va); gl.drawArrays(gl.TRIANGLES, 0, data.length / 9);
  };
  gl.useProgram(prog.p); gl.uniformMatrix4fv(prog.u.viewProj, false, view.vp); gl.uniform3fv(prog.u.eye, view.eye);
  gl.uniform3fv(prog.u.fog, view.fog); gl.uniform2fv(prog.u.fogRange, view.fogRange); gl.uniform1i(prog.u.tex, 0);
  gl.activeTexture(gl.TEXTURE0); gl.bindTexture(gl.TEXTURE_2D, atlas);
  // Fire is opaque and lights itself; smoke is see-through and lit like the snow, drawn after.
  gl.disable(gl.BLEND); gl.depthMask(true);
  draw(F.fire, 1);
  gl.enable(gl.BLEND); gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA); gl.depthMask(false);
  draw(F.smoke, .95 * (1 + .8 * s.flash));
  glows(s, t, view.tip, L, view);
  window.fireCubes = {fire: F.fire.length / 324, smoke: F.smoke.length / 324};  // for review: cubes this frame
};
/** Over the frame: speed lines in the fall, a warm edge while the jet burns, the slow landing's vignette. */
LANDING.hud = (t, s, w, h) => {
  if (s.f_speed > .01) {
    ctx.strokeStyle = `rgba(255,255,255,${.38 * s.f_speed})`; ctx.lineWidth = Math.max(1, h * .0022);
    ctx.beginPath();
    const R = Math.hypot(w, h) / 2;
    for (let i = 0; i < 110; i++) {
      const a = hash(i * 1.3) * Math.PI * 2, ph = (hash(i * 2.7) + t * .08 * (.6 + hash(i * 3.1))) % 1;
      const r0 = R * (.4 + .6 * ph), len = R * (.08 + .18 * hash(i * 4.3)) * s.f_speed;
      ctx.moveTo(w / 2 + Math.cos(a) * r0, h / 2 + Math.sin(a) * r0); ctx.lineTo(w / 2 + Math.cos(a) * (r0 + len), h / 2 + Math.sin(a) * (r0 + len));
    }
    ctx.stroke();
  }
  const edge = .16 * s.f_jetfx + .18 * s.f_bt;
  if (edge > .01) {
    const g = ctx.createRadialGradient(w / 2, h / 2, h * .32, w / 2, h / 2, Math.hypot(w, h) * .56);
    g.addColorStop(0, 'rgba(40,10,0,0)'); g.addColorStop(1, `rgba(40,10,0,${edge})`);
    ctx.fillStyle = g; ctx.fillRect(0, 0, w, h);
  }
  if (s.f_impact > .01) { ctx.fillStyle = `rgba(255,190,120,${.1 * s.f_impact * s.f_impact})`; ctx.fillRect(0, 0, w, h); }
};
})();
