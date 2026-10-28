"""GeckoLib clips and bones in Python, the way the game and the browser animatic pose them.

A clip is GeckoLib's JSON: per bone, rotation/position/scale channels that are either a bare
[x, y, z] (constant) or {seconds: [x, y, z]} keys, linearly interpolated. Posing follows
GeckoLib 5 and the animatic's poseBones: model X is mirrored, each bone turns about its pivot
by Z, then Y, then X, with X and Y negated, and is offset by its position channel (X negated).
Points come out in blocks in the model's own frame (front toward +Z after ENTITY's half turn).
"""
import math

import numpy as np


def T(x, y, z):
    m = np.eye(4); m[:3, 3] = (x, y, z); return m


def S(x, y, z):
    return np.diag([x, y, z, 1.0])


def Rx(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]])


def Ry(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]])


def Rz(a):
    c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])


ENTITY = Ry(math.pi)  # GeckoLib's mirrored model space into a frame facing +Z


class Channel:
    """One channel's keys, sampled linearly; a bare list is constant."""

    def __init__(self, raw):
        if isinstance(raw, list):
            self.times, self.values = np.array([0.0]), np.array([raw], dtype=float)
        else:
            items = sorted((float(k), v) for k, v in raw.items())
            self.times = np.array([k for k, _ in items])
            self.values = np.array([v for _, v in items], dtype=float)

    def __call__(self, seconds):
        t, v = self.times, self.values
        if len(t) == 1 or seconds <= t[0]: return v[0].copy()
        if seconds >= t[-1]: return v[-1].copy()
        i = int(np.searchsorted(t, seconds)) - 1
        f = (seconds - t[i])/(t[i + 1] - t[i])
        return v[i] + (v[i + 1] - v[i])*f


class Clip:
    def __init__(self, raw):
        self.length = raw.get('animation_length', 0)
        self.loop = raw.get('loop', False)
        self.bones = {b: {k: Channel(v) for k, v in ch.items()} for b, ch in raw['bones'].items()}

    def sample(self, seconds):
        """{bone: {channel: value}} at seconds (wrapped if the clip loops)."""
        if self.loop and self.length: seconds %= self.length
        return {b: {k: c(seconds) for k, c in ch.items()} for b, ch in self.bones.items()}


class Rig:
    """A geo's bones in parent order, posed into 4x4 matrices in blocks."""

    def __init__(self, geo):
        g = geo['minecraft:geometry'][0]
        by_name = {b['name']: b for b in g['bones']}
        self.order, seen = [], set()

        def visit(b):
            if b['name'] in seen: return
            if b.get('parent') in by_name: visit(by_name[b['parent']])
            seen.add(b['name']); self.order.append(b)
        for b in g['bones']: visit(b)
        self.names = [b['name'] for b in self.order]
        self.parent = {b['name']: b.get('parent') for b in self.order}
        self.pivot = {b['name']: np.array([-b.get('pivot', [0, 0, 0])[0], b.get('pivot', [0, 0, 0])[1], b.get('pivot', [0, 0, 0])[2]])/16
                      for b in self.order}
        self.rest = {b['name']: np.radians([-(b.get('rotation') or [0, 0, 0])[0], -(b.get('rotation') or [0, 0, 0])[1],
                                            (b.get('rotation') or [0, 0, 0])[2]]) for b in self.order}
        self.children = {}
        for n, p in self.parent.items(): self.children.setdefault(p, []).append(n)

    def pose(self, channels, base=ENTITY):
        """{bone: 4x4} for {bone: {rotation, position, scale}} (degrees, pixels)."""
        out = {}
        for n in self.names:
            ch = channels.get(n, {})
            r = ch.get('rotation', (0, 0, 0)); p = ch.get('position', (0, 0, 0)); s = ch.get('scale', (1, 1, 1))
            rot = self.rest[n] + np.radians([-r[0], -r[1], r[2]])
            pv = self.pivot[n]
            m = T(-p[0]/16, p[1]/16, p[2]/16)@T(*pv)@Rz(rot[2])@Ry(rot[1])@Rx(rot[0])@S(*s)@T(*-pv)
            parent = self.parent[n]
            out[n] = (out[parent] if parent in out else base)@m
        return out

    def point(self, matrices, bone, model_px):
        """Where a model-space pixel point riding bone ends up (blocks)."""
        v = np.array([-model_px[0], model_px[1], model_px[2]])/16
        return (matrices[bone]@np.append(v, 1))[:3]

    def subtree(self, name):
        out = [name]
        for c in self.children.get(name, []): out += self.subtree(c)
        return out


def continuous(prev, cur):
    """cur's Euler triple nearest prev (wrapped by 360s), so keys interpolate the short way."""
    if prev is None: return np.array(cur, dtype=float)
    return prev + (np.array(cur, dtype=float) - prev + 180) % 360 - 180


def reduce_keys(times, values, tolerance):
    """The keys a linear interpolation needs to stay within tolerance of every sample."""
    t = np.asarray(times, dtype=float); v = np.asarray(values, dtype=float)
    def fits(a, b):
        f = (t[a + 1:b] - t[a])/(t[b] - t[a])
        return np.all(np.abs(v[a] + (v[b] - v[a])*f[:, None] - v[a + 1:b]) <= tolerance)
    kept, a = [0], 0
    while a < len(t) - 1:
        b = a + 1
        while b + 1 < len(t) and fits(a, b + 1): b += 1
        kept.append(b); a = b
    return kept


def bake(sample, seconds, rate, tolerance=.01, loop=False):
    """A GeckoLib clip from sample(seconds) -> {bone: {channel: value}} at rate samples a second.
    Constant channels become bare lists; the rest keep only the keys they need."""
    n = round(seconds*rate)
    times = [round(i/rate, 4) for i in range(n + 1)]
    frames = [sample(t) for t in times]
    # A channel a frame leaves out is at rest there (GeckoLib's default).
    names = sorted({(bone, kind) for f in frames for bone, channels in f.items() for kind in channels})
    series, last = {}, {}
    for f in frames:
        for bone, kind in names:
            value = f.get(bone, {}).get(kind)
            value = np.array(value, dtype=float) if value is not None else (np.ones(3) if kind == 'scale' else np.zeros(3))
            if kind == 'rotation':
                value = continuous(last.get(bone), value); last[bone] = value
            series.setdefault((bone, kind), []).append(value)
    bones = {}
    for (bone, kind), values in series.items():
        values = np.array(values)
        if np.all(np.abs(values - values[0]) < 1e-4):
            bones.setdefault(bone, {})[kind] = [round(float(x), 4) for x in values[0]]
            continue
        keep = reduce_keys(times, values, tolerance)
        bones.setdefault(bone, {})[kind] = {f'{times[i]:.4f}': [round(float(x), 4) for x in values[i]] for i in keep}
    return {'loop': loop, 'animation_length': seconds, 'bones': bones}
