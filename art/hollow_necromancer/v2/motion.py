"""Motion tools for the reworked clips: timing with character, overlap, cloth follow-through,
loop-safe irregularity and contact solving, all baked to the same 20 Hz numeric keys GeckoLib
interpolates linearly.

A channel is a function of clip time, so a clip is authored as poses on a timeline, layered
with springs and noise, then sampled once. Every value stays a plain Euler triple that moves
continuously from key to key: GeckoLib blends Euler channels linearly, so no key may jump to
the other form of the same turn.
"""
import math
import numpy as np
from geometry import bones

BY_NAME = {b['name']: b for b in bones}
RATE = 20


def _unit(f):
    return min(1., max(0., f))


def _settle(f):
    # A damped spring's step response: arrives early, overshoots by about a sixth and settles.
    r = lambda x: 1 - math.exp(-5 * x) * (math.cos(9 * x) + 5 / 9 * math.sin(9 * x))
    return r(f) - (r(1) - 1) * f * f


# Progress 0..1 to value 0..1. Each names how a move arrives at its key.
SHAPES = {
    'ease': lambda f: f * f * (3 - 2 * f),            # soft start and stop: slow, heavy shifts
    'slow': lambda f: f * f * f * (f * (6 * f - 15) + 10),  # softer still: a giant's weight
    'linear': lambda f: f,
    'out': lambda f: 1 - (1 - f) ** 2,                # leaves fast, brakes into the key
    'snap': lambda f: 1 - (1 - f) ** 3,               # starts at full speed and brakes hard
    'whip': lambda f: f ** 3,                         # starts slowly, lands at full speed: a blow
    'in': lambda f: f * f,                            # gathers speed: a fall, a foot set down
    'hit': lambda f: 2 * f * f - f ** 3,              # builds and lands still moving: a heavy blow
    'settle': _settle,                                # arrives, overshoots and settles back
    'hold': lambda f: 0.,                             # stays on the previous key until this one
}


def vec(v):
    return np.array((v, v, v) if isinstance(v, (int, float)) else v, dtype=float)


def curve(points, period=None):
    """A function of time through keys (time, value[, shape]). The shape names how the move
    arrives at that key ('ease' by default). With a period the keys wrap around the loop."""
    pts = [(float(p[0]), vec(p[1]), p[2] if len(p) > 2 else 'ease') for p in points]
    assert all(a[0] <= b[0] for a, b in zip(pts, pts[1:])), f'keys out of time order: {[p[0] for p in pts]}'

    def at(t):
        if period:
            t %= period
            if t < pts[0][0]:
                (a, av, _), (b, bv, shape) = pts[-1], pts[0]
                a -= period
                return av + (bv - av) * SHAPES[shape](_unit((t - a) / (b - a)))
        if t <= pts[0][0]:
            return pts[0][1].copy()
        for (a, av, _), (b, bv, shape) in zip(pts, pts[1:]):
            if t <= b:
                return av + (bv - av) * SHAPES[shape](_unit((t - a) / (b - a))) if b > a else bv.copy()
        return pts[-1][1].copy()
    at.times = [p[0] for p in pts]
    return at


def shaped(t, a, b, shape='ease'):
    """0 before a, 1 after b, shaped in between."""
    return SHAPES[shape](_unit((t - a) / (b - a))) if b > a else float(t >= a)


def envelope(t, start, rise, hold, fall, up='out', down='ease'):
    """0 → 1 over rise, held, then back to 0 over fall."""
    if t < start or t > start + rise + hold + fall:
        return 0.
    if t < start + rise:
        return SHAPES[up](_unit((t - start) / rise))
    if t <= start + rise + hold:
        return 1.
    return 1 - SHAPES[down](_unit((t - start - rise - hold) / fall))


def noise(t, period, seed, harmonics=(1, 2, 3, 5)):
    """Smooth irregular drift in -1..1 that repeats exactly every period."""
    rng = np.random.default_rng(seed)
    phases = rng.uniform(0, math.tau, len(harmonics))
    weights = [1 / h for h in harmonics]
    return sum(w * math.sin(math.tau * h * t / period + p) for h, w, p in zip(harmonics, weights, phases)) / sum(weights)


def follow(times, drive, hz, damping=.45, period=None):
    """A heavy, loose part trailing a driving signal (degrees, pixels): returns the lag, the part's
    value minus the drive, integrated as a damped spring. Low hz is heavy and slow to catch up;
    damping below 1 swings past and settles. With a period the response wraps seamlessly."""
    times = np.asarray(times, dtype=float)
    drive = np.asarray(drive, dtype=float).reshape(len(times), -1)
    w0, zeta = math.tau * hz, damping
    laps = 3 if period else 1
    x, v = drive[0].copy(), np.zeros(drive.shape[1])
    out = np.zeros_like(drive)
    for lap in range(laps):
        for k in range(len(times)):
            if k:
                dt = times[k] - times[k - 1]
                for s in range(8):
                    f = (s + .5) / 8
                    target = drive[k - 1] * (1 - f) + drive[k] * f
                    h = dt / 8
                    v += (w0 * w0 * (target - x) - 2 * zeta * w0 * v) * h
                    x += v * h
            out[k] = x - drive[k]
    return out


# ---------------- Pose evaluation (the rig, sampled from authored functions) ----------------

def _rotation(r):
    x, y, z = np.radians(r) * [-1, -1, 1]
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return (np.array([[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])
            @ np.array([[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]])
            @ np.array([[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]))


def _translation(p):
    out = np.eye(4)
    out[:3, 3] = p
    return out


def local_matrix(name, rot=(0, 0, 0), pos=(0, 0, 0), scl=(1, 1, 1)):
    """GeckoLib's bone transform in rig space (X mirrored), as rig.local computes it."""
    b = BY_NAME[name]
    pivot = np.array(b['pivot']) * [-1, 1, 1]
    r = np.array(b.get('rotation', [0, 0, 0])) + np.asarray(rot, dtype=float)
    p = np.asarray(pos, dtype=float) * [-1, 1, 1]
    return _translation(p) @ _translation(pivot) @ _rotation(r) @ np.diag([*scl, 1]) @ _translation(-pivot)


def rig_point(name, point):
    """A geo-space point (model pixels) in rig space."""
    return np.array(point, dtype=float) * [-1, 1, 1]


class Clip:
    """A clip authored as functions of time, baked to keys on the tick grid (plus every authored
    key time, so fast blows keep their extremes)."""

    def __init__(self, clips, name, length, loop=False):
        self.clips, self.name, self.length, self.loop = clips, name, length, loop
        self.fns = {}       # (bone, channel) -> list of functions summed
        self.extra = set()  # authored key times
        self.solved = {}    # (bone, channel) -> {tick time: value} from contact solving
        self.trial = {}     # joint values a solve is trying, seen by channels that read others

    def add(self, bone, channel, fn, lag=0.):
        assert bone in BY_NAME, bone
        for k in getattr(fn, 'times', []):
            k = (k + lag) % self.length if self.loop else k + lag
            if 0 <= k <= self.length:
                self.extra.add(round(k, 4))
        if lag:
            src = fn
            if self.loop:
                fn = lambda t, f=src, d=lag: f((t - d) % self.length)
            else:
                # The lag closes over the last quarter second, so the clip still ends on its pose.
                L = self.length
                fn = lambda t, f=src, d=lag: f(max(0., t - d * (1 - shaped(t, L - .25, L))))
        self.fns.setdefault((bone, channel), []).append(fn)
        return self

    def rot(self, bone, points, lag=0.):
        return self.add(bone, 'rotation', curve(points, self.length if self.loop else None), lag)

    def pos(self, bone, points, lag=0.):
        return self.add(bone, 'position', curve(points, self.length if self.loop else None), lag)

    def scale(self, bone, points, lag=0.):
        return self.add(bone, 'scale', curve(points, self.length if self.loop else None), lag)

    def value(self, bone, channel, t):
        if (bone, channel) in self.trial:
            return self.trial[(bone, channel)].copy()
        fns = self.fns.get((bone, channel))
        if (bone, channel) in self.solved:
            table = self.solved[(bone, channel)]
            return np.array(table[min(table, key=lambda k: abs(k - t))], dtype=float)
        if not fns:
            return np.array((1., 1., 1.) if channel == 'scale' else (0., 0., 0.))
        if channel == 'scale':
            out = np.ones(3)
            for f in fns:
                out = out * f(t)
            return out
        return sum((f(t) for f in fns), np.zeros(3))

    def ticks(self):
        n = round(self.length * RATE)
        return [i / RATE for i in range(n + 1)]

    def matrix(self, name, t, override=None):
        override = override or {}
        parent = BY_NAME[name].get('parent')
        rot = override.get((name, 'rotation'))
        m = local_matrix(name, self.value(name, 'rotation', t) if rot is None else rot,
                         self.value(name, 'position', t), self.value(name, 'scale', t))
        return (self.matrix(parent, t, override) if parent else np.eye(4)) @ m

    def point(self, name, geo_point, t, override=None):
        return (self.matrix(name, t, override) @ [*rig_point(name, geo_point), 1])[:3]

    def solve(self, goals, dofs, times=None, limits=None, iterations=60, stiffness=.002):
        """Per tick, turn the free joints so each goal point reaches its target (rig space).
        goals: [(bone, geo point, target(t) -> rig point or None, weight or weight(t))]; a partial
        weight aims between the authored point and the target. dofs: [(bone, axis)].
        Starts from the authored pose, then from the previous tick's answer, so a joint keeps
        its branch. The result replaces those channels' authored values on the tick grid."""
        times = times or self.ticks()
        limits = limits or {}
        tables = {}
        previous = None
        for t in times:
            base = {}
            for bone_, _ in dofs:
                base[bone_] = self.value(bone_, 'rotation', t)
            authored = np.array([base[b][a] for b, a in dofs])
            active, strength = [], 0.
            for b, p, f, w in goals:
                w = w(t) if callable(w) else w
                strength = max(strength, w if f(t) is not None else 0.)
                target = f(t)
                if target is None or w <= 0:
                    continue
                if w < 1:
                    # Part way into a contact: aim between where the authored pose puts the point
                    # and the contact, so joints ease onto it instead of popping.
                    target = np.asarray(target) * w + self.point(b, p, t) * (1 - w)
                active.append((b, p, np.asarray(target, dtype=float), 1.))
            q = authored.copy() if previous is None else previous.copy()
            if active:
                prior = q.copy()

                def evaluate(qv):
                    over = {}
                    for (b, a), v in zip(dofs, qv):
                        r = over.setdefault((b, 'rotation'), base[b].copy())
                        r[a] = v
                    # Channels driven by these joints (a staff kept at a world tilt) see the trial.
                    self.trial = over
                    try:
                        err = [(self.point(b, p, t, over) - target) * w for b, p, target, w in active]
                    finally:
                        self.trial = {}
                    # A faint pull toward the last answer keeps redundant joints from hopping.
                    return np.concatenate(err + [(qv - prior) * stiffness])
                lam = 1e-3
                err = evaluate(q)
                for _ in range(iterations):
                    if np.linalg.norm(err[:-len(q)]) < 1e-3:
                        break
                    jac = np.column_stack([(evaluate(q + np.eye(len(q))[i] * .02) - err) / .02 for i in range(len(q))])
                    # Levenberg-Marquardt: damping grows when a step fails and shrinks when it helps.
                    jtj = jac.T @ jac
                    scale = np.diag(np.diag(jtj)) + np.eye(len(q)) * 1e-6
                    step = np.clip(np.linalg.solve(jtj + lam * scale, -jac.T @ err), -10, 10)
                    trial = q + step
                    for i, (b, a) in enumerate(dofs):
                        lo, hi = limits.get((b, a), (-179, 179))
                        trial[i] = min(hi, max(lo, trial[i]))
                    trial_err = evaluate(trial)
                    if np.linalg.norm(trial_err) < np.linalg.norm(err):
                        q, err, lam = trial, trial_err, max(1e-7, lam * .3)
                    else:
                        lam *= 10
                        if lam > 1e6:
                            break
                if strength < 1:
                    # Easing off a contact: the joints, not just the point, fade back to the
                    # authored pose, so no redundant joint is left to jump when the contact ends.
                    fade = strength * strength * (3 - 2 * strength)
                    q = authored + (q - authored) * fade
            else:
                q = authored.copy()
            previous = q
            for (b, a), v in zip(dofs, q):
                row = tables.setdefault(b, {}).setdefault(round(t, 4), base[b].copy())
                row[a] = v
        for b, rows in tables.items():
            # A solve over some ticks keeps an earlier solve's answers for the others.
            self.solved[(b, 'rotation')] = {**self.solved.get((b, 'rotation'), {}), **rows}
        return self

    def bake(self, thin=.02):
        """Sample every channel on the tick grid and authored key times, drop keys a straight line
        already covers, and write the clip."""
        grid = sorted(set(self.ticks()) | {t for t in self.extra if 0 <= t <= self.length})
        out = {'loop': self.loop, 'animation_length': self.length, 'bones': {}}
        channels = set(self.fns) | set(self.solved)
        for bone_, channel in sorted(channels):
            if (bone_, channel) in self.solved:
                table = self.solved[(bone_, channel)]
                times = sorted(table)
                values = [np.array(table[k], dtype=float) for k in times]
            else:
                times = grid
                values = [self.value(bone_, channel, t) for t in times]
            if self.loop:
                values[-1] = values[0].copy()
            keep = _thin(times, values, thin)
            out['bones'].setdefault(bone_, {})[channel] = {
                f'{times[i]:.4f}': [round(float(x), 4) for x in values[i]] for i in keep}
        self.clips[self.name] = out
        return out


def _thin(times, values, tolerance):
    t = np.asarray(times, dtype=float)
    v = np.asarray(values, dtype=float)
    kept, a = [0], 0
    while a < len(t) - 1:
        b = a + 1
        while b + 1 < len(t):
            c = b + 1
            f = (t[a + 1:c] - t[a]) / (t[c] - t[a])
            if np.all(np.abs(v[a] + (v[c] - v[a]) * f[:, None] - v[a + 1:c]) <= tolerance):
                b = c
            else:
                break
        kept.append(b)
        a = b
    return kept
