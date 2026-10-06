"""The transformation cinematic's clip, authored in ticks on the scene's beats.

He drops out of the dark onto one knee and the staff he lands on snaps. He picks up the broken
head and stares at the two halves (the camera is his eyes). They start to shake, their broken
ends glow, and the souls trapped in the staff tear out of them and whirl around him in a storm
while he looks around in terror. Two dive into his face and throw him onto his back; the rest shoot
into his mask. He writhes and smoke rises. Then he grows into the colossus (growth.py): his
fingers stretch into claws, every convulsion is a growth spurt that rips the robe further, and
inside a burst of smoke he is the giant, arched on its back in the rags of his robe. It sits bolt
upright, its eyes ignite, it drops into a spider crouch and crawls at the party, then howls with
its claws braced and its skull thrown back, and settles into the crouch the colossus idle takes over.

NecromancerTransformScene and the camera track (art/hollow_necromancer/transform/build_scene.py)
share BEATS. Poses are in the rig's frame (rig.py: GeckoLib's mirrored X, pixels, front -Z), so
the mage's right hand, which holds the staff, is on +X. Only the robed Necromancer changes size;
the colossus is only ever shown at full size.
"""
import bisect
import math

import numpy as np

import rig
from geometry import SHELL_CLOSED, SMOKES, SOULS, STAFF_BREAK, TRANSFORM_ONLY, bones, cubes

BEATS = dict(length=692, impact=22, snap=27, pov=40, grasp=52, lifted=76, shake=80, burst=104, scared=148,
             strike=180, strike2=190, floor=204, pour=208, writhe=280, smoke=320, claws=322, drop=370, spurt=378,
             swap=452, flat=504, sit=514, eyes=544, crouch=560, crawl=586, ignite=614, rear=624, roar=636, land=676, ret=672)
B = BEATS
LENGTH = B['length']
CUTS = [B['pov'], B['scared'], B['pour'], B['writhe'], B['claws'], B['drop'], B['swap'], B['sit'] - 2, B['crouch']]
FALL_HEIGHT = 176  # pixels above the landing spot at the first frame
INTAKE = 1.5  # ticks between the souls pouring into his mask once he is down
DIVE = 8  # ticks from a soul's turn to its arrival
STRIKERS = {4: B['strike'], 9: B['strike2']}  # the two souls that dive into his face and floor him
HUSK = B['swap']  # the robed body is gone once the giant has outgrown it

BY_NAME = {b['name']: b for b in bones}
PIVOT = {b['name']: np.array(b['pivot'], dtype=float) * [-1, 1, 1] for b in bones}
REST = {b['name']: np.array(b.get('rotation', [0, 0, 0]), dtype=float) for b in bones}
ZERO = (0, 0, 0)


def smooth(f):
    f = min(1, max(0, f))
    return f * f * (3 - 2 * f)


def ramp(t, a, b):
    return smooth((t - a) / (b - a))


def keyed(t, frames):
    """A vector eased between (tick, value) keys, held before the first and after the last."""
    if t <= frames[0][0]:
        return np.array(frames[0][1], dtype=float)
    for (t0, a), (t1, b) in zip(frames, frames[1:]):
        if t <= t1:
            f = smooth((t - t0) / (t1 - t0)) if t1 > t0 else 1
            return np.array(a, dtype=float) + (np.array(b, dtype=float) - np.array(a, dtype=float)) * f
    return np.array(frames[-1][1], dtype=float)


def hashf(*v):
    """A stable pseudo-random number in [0, 1)."""
    x = math.sin(sum(a * k for a, k in zip(v, (12.9898, 78.233, 37.719, 4.581))) + 1.2345) * 43758.5453
    return x - math.floor(x)


# ---------------- matrices ----------------
def translation(p):
    out = np.eye(4)
    out[:3, 3] = p
    return out


def euler(m):
    """Inverse of rig.rotation: the GeckoLib rotation key for a rotation matrix."""
    r = m[:3, :3]
    y = math.asin(max(-1, min(1, -r[2, 0])))
    x = math.atan2(r[2, 1], r[2, 2])
    z = math.atan2(r[1, 0], r[0, 0])
    return np.degrees([-x, -y, z])


def continuous(prev, cur):
    """The equivalent Euler triple for cur that is nearest prev, so keys interpolate smoothly."""
    if prev is None:
        return cur
    best = None
    for c in (np.array(cur), np.array([cur[0] + 180, 180 - cur[1], cur[2] + 180])):
        c = prev + (c - prev + 180) % 360 - 180
        if best is None or np.abs(c - prev).sum() < np.abs(best - prev).sum():
            best = c
    return best


def quat(m):
    r = m[:3, :3] / np.linalg.norm(m[:3, :3], axis=0)  # the rotation alone, whatever the scale
    w = math.sqrt(max(0, 1 + r[0, 0] + r[1, 1] + r[2, 2])) / 2
    x = math.copysign(math.sqrt(max(0, 1 + r[0, 0] - r[1, 1] - r[2, 2])) / 2, r[2, 1] - r[1, 2])
    y = math.copysign(math.sqrt(max(0, 1 - r[0, 0] + r[1, 1] - r[2, 2])) / 2, r[0, 2] - r[2, 0])
    z = math.copysign(math.sqrt(max(0, 1 - r[0, 0] - r[1, 1] + r[2, 2])) / 2, r[1, 0] - r[0, 1])
    q = np.array([w, x, y, z])
    return q / np.linalg.norm(q)


def from_quat(q):
    w, x, y, z = q
    out = np.eye(4)
    out[:3, :3] = [[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                   [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                   [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]]
    return out


def slerp(a, b, f):
    qa, qb = quat(a), quat(b)
    if np.dot(qa, qb) < 0:
        qb = -qb
    d = min(1, np.dot(qa, qb))
    if d > .9995:
        q = qa + (qb - qa) * f
    else:
        th = math.acos(d)
        q = (math.sin((1 - f) * th) * qa + math.sin(f * th) * qb) / math.sin(th)
    return from_quat(q / np.linalg.norm(q))


def axis_angle(axis, degrees):
    axis = np.array(axis, dtype=float)
    axis /= np.linalg.norm(axis)
    a = math.radians(degrees)
    x, y, z = axis
    c, s, k = math.cos(a), math.sin(a), 1 - math.cos(a)
    out = np.eye(4)
    out[:3, :3] = [[c + x * x * k, x * y * k - z * s, x * z * k + y * s],
                   [y * x * k + z * s, c + y * y * k, y * z * k - x * s],
                   [z * x * k - y * s, z * y * k + x * s, c + z * z * k]]
    return out


def aligning(a, b):
    """The rotation turning direction a onto direction b."""
    a, b = np.array(a, float) / np.linalg.norm(a), np.array(b, float) / np.linalg.norm(b)
    axis = np.cross(a, b)
    if np.linalg.norm(axis) < 1e-6:
        return np.eye(4) if np.dot(a, b) > 0 else axis_angle([1, 0, 0] if abs(a[0]) < .9 else [0, 0, 1], 180)
    return axis_angle(axis, math.degrees(math.acos(max(-1, min(1, np.dot(a, b))))))


# ---------------- authoring on a clip ----------------
class Author:
    def __init__(self, clip):
        self.clip = clip
        self._sorted, self._world = {}, {}

    def _changed(self):
        self._sorted.clear()
        self._world.clear()

    def write(self, part, channel, keys):
        self.clip['bones'].setdefault(part, {})[channel] = keys
        self._changed()

    def put(self, part, channel, frames):
        """Keys in ticks, eased like animations.keys() between authored poses, held without steps."""
        frames = [(t / 20, (v, v, v) if isinstance(v, (int, float)) else tuple(v)) for t, v in frames]
        out = {}
        for (t0, a), (t1, b) in zip(frames, frames[1:]):
            steps = 1 if a == b else max(1, math.ceil((t1 - t0) * 20 - 1e-9))
            for i in range(steps):
                f = smooth(i / steps)
                out[f'{t0 + (t1 - t0) * i / steps:.4f}'] = [round(x + (y - x) * f, 4) for x, y in zip(a, b)]
        out[f'{frames[-1][0]:.4f}'] = [round(v, 4) for v in frames[-1][1]]
        self.write(part, channel, out)

    def rot(self, part, frames):
        self.put(part, 'rotation', frames)

    def pos(self, part, frames):
        self.put(part, 'position', frames)

    def scale(self, part, frames):
        self.put(part, 'scale', frames)

    def bake(self, part, channel, values, simplify=False):
        """Per-tick keys {tick: vector}, dropping keys that only repeat both neighbours; with
        simplify, every key that linear interpolation between its neighbours already reproduces."""
        ticks = sorted(values)
        if simplify and len(ticks) > 2:
            kept = douglas_peucker(ticks, [values[t] for t in ticks], TOLERANCE[channel])
            self.write(part, channel, {f'{ticks[i] / 20:.4f}': [round(float(x), 4) for x in values[ticks[i]]] for i in kept})
            return
        out = {}
        for i, t in enumerate(ticks):
            v = [round(float(x), 4) for x in values[t]]
            prev = values[ticks[i - 1]] if i else None
            nxt = values[ticks[i + 1]] if i + 1 < len(ticks) else None
            if prev is not None and nxt is not None and np.allclose(prev, values[t], atol=1e-4) and np.allclose(nxt, values[t], atol=1e-4):
                continue
            out[f'{t / 20:.4f}'] = v
        self.write(part, channel, out)

    def sample(self, part, channel, tick, default):
        frames = self.clip['bones'].get(part, {}).get(channel)
        if frames is None:
            return np.array(default, dtype=float)
        seq = self._sorted.get(id(frames))
        if seq is None:
            items = sorted((float(k), np.array(v, dtype=float)) for k, v in frames.items())
            seq = self._sorted[id(frames)] = ([a for a, _ in items], [v for _, v in items])
        times, values = seq
        t = tick / 20
        i = bisect.bisect_left(times, t)
        if i == 0:
            return values[0]
        if i >= len(times):
            return values[-1]
        a, b = times[i - 1], times[i]
        return values[i - 1] + (values[i] - values[i - 1]) * ((t - a) / (b - a))

    def local(self, name, tick, rotation=None):
        r = REST[name] + (rotation if rotation is not None else self.sample(name, 'rotation', tick, ZERO))
        p = self.sample(name, 'position', tick, ZERO) * [-1, 1, 1]
        s = self.sample(name, 'scale', tick, (1, 1, 1))
        pivot = PIVOT[name]
        return translation(p) @ translation(pivot) @ rig.rotation(r) @ np.diag([*s, 1]) @ translation(-pivot)

    def world(self, name, tick):
        key = (name, tick)
        if key not in self._world:
            parent = BY_NAME[name].get('parent')
            self._world[key] = (self.world(parent, tick) if parent else np.eye(4)) @ self.local(name, tick)
        return self._world[key]

    def point(self, name, tick, p):
        """A model-space point (geometry coordinates) carried by a bone, in the rig's frame."""
        return (self.world(name, tick) @ [*(np.array(p, float) * [-1, 1, 1]), 1])[:3]

    def place(self, name, frames, visible=lambda t: 1, simplify=False, anchor_end=False):
        """Bake a bone so its world matrix follows frames {tick: matrix} (rotation, translation and
        a uniform or per-axis scale along its own axes); visible(t) scales it away. Each rotation
        key is the Euler triple nearest the one before it, or with anchor_end the one after it, so
        the clip ends on plain angles another clip can blend from."""
        parent = BY_NAME[name].get('parent')
        rows = {'rotation': {}, 'position': {}, 'scale': {}}
        prev = None
        for tick in sorted(frames, reverse=anchor_end):
            base = self.world(parent, tick) if parent else np.eye(4)
            if abs(np.linalg.det(base[:3, :3])) < 1e-9:
                # A parent scaled away hides this bone too: hold the last pose.
                for channel in ('rotation', 'position'):
                    held = rows[channel][(min if anchor_end else max)(rows[channel])] if rows[channel] else np.zeros(3)
                    rows[channel][tick] = held
                rows['scale'][tick] = np.zeros(3)
                continue
            m = np.linalg.inv(base) @ frames[tick]
            s = np.linalg.norm(m[:3, :3], axis=0)
            r = np.eye(4)
            r[:3, :3] = m[:3, :3] / np.where(s > 1e-9, s, 1)
            e = continuous(prev, euler(r) - REST[name])
            prev = e
            pivot = PIVOT[name]
            rows['rotation'][tick] = e
            rows['position'][tick] = ((m @ [*pivot, 1])[:3] - pivot) * [-1, 1, 1]
            rows['scale'][tick] = np.where(s > 1e-9, s, 0) * visible(tick)
        for channel, values in rows.items():
            self.bake(name, channel, values, simplify)

    def solve(self, upper, fore, wrist, tick, target, start):
        """Two-joint reach: the upper bone's three angles and the fore bone's bend that put the
        wrist (a point on the fore chain, rig frame of its child) on target."""
        parent = BY_NAME[upper]['parent']
        base = self.world(parent, tick)
        child = [b['name'] for b in bones if b.get('parent') == fore][0]

        def at(q):
            m = base @ self.local(upper, tick, q[:3]) @ self.local(fore, tick, [q[3], 0, 0]) @ self.local(child, tick)
            return (m @ [*wrist, 1])[:3]

        q = np.array(start, dtype=float)
        target = np.array(target, dtype=float)
        for _ in range(60):
            error = target - at(q)
            if np.linalg.norm(error) < .03:
                break
            jac = np.column_stack([(at(q + np.eye(4)[i] * .1) - at(q)) / .1 for i in range(4)])
            delta = np.linalg.solve(jac.T @ jac + np.eye(4) * .01, jac.T @ error)
            q = np.clip(q + np.clip(delta, -8, 8), [-200, -80, -80, -150], [70, 80, 80, 5])
        return q

    def reach(self, side, frames, weight=None):
        """Mage arm IK: frames {tick: target or None}; a None tick keeps the authored arm."""
        upper, fore = side + '_arm', 'mage_forearm_' + side
        wrist = PIVOT['mage_hand_' + side]
        rows_u, rows_f, prev = {}, {}, None
        for tick in range(LENGTH + 1):
            authored = np.array([*self.sample(upper, 'rotation', tick, ZERO), self.sample(fore, 'rotation', tick, ZERO)[0]])
            target = frames(tick)
            w = 0 if target is None else 1 if weight is None else max(0, min(1, weight(tick)))
            if w <= 0:
                q = authored
            else:
                q = self.solve(upper, fore, wrist, tick, target, prev if prev is not None else authored)
                q = authored + (q - authored) * smooth(w)
            prev = q
            rows_u[tick], rows_f[tick] = q[:3], [q[3], 0, 0]
        self.bake(upper, 'rotation', rows_u)
        self.bake(fore, 'rotation', rows_f)


TOLERANCE = {'rotation': .2, 'position': .03, 'scale': .005}


def douglas_peucker(ticks, values, tol):
    """Indices of the fewest keys whose linear interpolation stays within tol of every value."""
    t, v = np.array(ticks, dtype=float), np.array(values, dtype=float)
    keep, stack = {0, len(t) - 1}, [(0, len(t) - 1)]
    while stack:
        i, j = stack.pop()
        if j - i < 2:
            continue
        line = v[i] + (v[j] - v[i]) * ((t[i + 1:j] - t[i]) / (t[j] - t[i]))[:, None]
        err = np.abs(v[i + 1:j] - line).max(axis=1)
        k = int(err.argmax())
        if err[k] > tol:
            keep.add(i + 1 + k)
            stack += [(i, i + 1 + k), (i + 1 + k, j)]
    return sorted(keep)


def look_rotation(forward, up=(0, 1, 0)):
    """The rotation turning a part's front (-Z) along forward, its top as near up as it can."""
    z = -np.array(forward, dtype=float)
    z /= np.linalg.norm(z)
    x = np.cross(up, z)
    if np.linalg.norm(x) < 1e-6:
        x = np.cross([0, 0, 1], z)
    x /= np.linalg.norm(x)
    out = np.eye(4)
    out[:3, :3] = np.column_stack([x, np.cross(z, x), z])
    return out


def strain(start, stop, a, b, shake, step=3, seed=0):
    """Ease from pose a to b over start-stop while shuddering every `step` ticks."""
    out = [(start, a)]
    for t in range(start + step, stop, step):
        f = smooth((t - start) / (stop - start))
        k = hashf(t, seed) * 2 - 1
        out.append((t, tuple(x + (y - x) * f + k * s for x, y, s in zip(a, b, shake))))
    return out + [(stop, b)]


# ---------------- the clip ----------------
def transform_clip(clips):
    clip = clips['transform'] = {'loop': False, 'animation_length': LENGTH / 20, 'bones': {}}
    a = Author(clip)
    mage(a)
    staff(a)
    souls(a)
    smoke(a)
    colossus(a)
    import growth  # it builds on this module's helpers
    growth.animate(a)
    handback(a)
    for name in TRANSFORM_ONLY:
        if 'scale' not in clip['bones'].get(name, {}):
            a.scale(name, [(0, 0), (LENGTH, 0)])
    return clip


IMPACT_POSE = dict(robe_pos=(0, -6, 0), body=(42, 0, 5), hood=(28, 0, 0), right_arm=(-38, 0, 12), fore_r=(-20, 0, 0),
                   left_arm=(-12, 0, -18), leg_r=(-55, 0, 0), leg_l=(35, 0, 0), staff=(24, 0, 0))
KNEEL = (0, -4.5, 0)
LIE = (0, 4.5, 4)  # on his back: the robe turned -90 about the floor, lifted onto its back


def soul_plan():
    """Each soul's emergence from a broken end, the start of its dive and its arrival in him."""
    plan = {}
    order = sorted((i for i in range(SOULS) if i not in STRIKERS), key=lambda i: hashf(i, 31))
    for i in range(SOULS):
        emerge = B['burst'] + round(i * .7)
        if i in STRIKERS:
            arrive, target = STRIKERS[i], 'face'
            dive = arrive - 9
        else:
            k = order.index(i)
            dive = B['pour'] + 4 + round(k * INTAKE)  # leave a short empty-mask hold before he writhes
            arrive, target = dive + DIVE, 'face'
        plan[i] = dict(emerge=emerge, dive=dive, arrive=arrive, target=target, src='lower' if i % 2 == 0 else 'upper')
    return plan


def hits(target, after=0):
    return sorted(p['arrive'] for p in soul_plan().values() if p['target'] == target and p['arrive'] >= after)


def jolts(times, peak, rest, rise=1, fall=3):
    """A sharp key at each tick, settling back to rest between them."""
    times = sorted(set(times))
    if not times:
        return []
    # Sample the pulse envelope: dropping overlapping keyframes skipped real arrivals.
    out = []
    for t in range(times[0] - rise, times[-1] + fall + 1):
        pulse = max(max(0, 1 - (hit - t) / rise) if t <= hit else max(0, 1 - (t - hit) / fall) for hit in times)
        out.append((t, tuple(float(x) for x in np.array(rest) + (np.array(peak) - rest) * pulse)))
    return out


def mage(a):
    imp, snap, pov, grasp, lifted = B['impact'], B['snap'], B['pov'], B['grasp'], B['lifted']
    shake, burst, scared, strike, strike2, floor = B['shake'], B['burst'], B['scared'], B['strike'], B['strike2'], B['floor']
    pour, writhe, smoke_at = B['pour'], B['writhe'], B['smoke']

    # The fall: dropped from the dark, accelerating, then the crash onto one knee.
    a.bake('root', 'position', {t: (0, FALL_HEIGHT * (1 - (t / imp) ** 2) if t < imp else 0, 0) for t in range(0, imp + 1)})

    # Kneeling until the second soul throws him onto his back.
    def robe_pos(t):
        if t < imp - 1:
            return (0, 2, 0)
        if t < imp + 6:
            return (0, -6 + 1.5 * ramp(t, imp, imp + 6), 0)
        return keyed(t, [(imp + 6, KNEEL), (strike2, KNEEL), (strike2 + 8, (0, -1, 3)), (floor, LIE),
                         (floor + 3, np.add(LIE, (0, 1.5, 0))), (floor + 6, LIE)])
    a.bake('robe', 'position', {t: robe_pos(t) for t in range(LENGTH + 1)})
    a.rot('robe', [(0, ZERO), (strike2, ZERO), (strike2 + 5, (-14, 0, 0)), (strike2 + 10, (-48, 0, 0)), (floor, (-94, 0, 0)),
                   (floor + 3, (-86, 0, 0)), (floor + 6, (-90, 0, 0)), (writhe, (-90, 0, 0))]
          + strain(writhe, smoke_at, (-90, 0, 0), (-90, 0, 0), (0, 0, 8), 4, 12)[1:] + [(smoke_at + 6, (-90, 0, 0))])
    a.scale('robe', [(0, 1), (HUSK - 1, 1), (HUSK, 0), (LENGTH, 0)])

    intake = sorted(p['arrive'] for p in soul_plan().values() if p['arrive'] >= pour)
    chest, face = intake, hits('face', pour)
    a.rot('body', [(0, (-10, 0, 0)), (imp - 2, (-8, 0, 0)), (imp, IMPACT_POSE['body']), (imp + 6, (32, 0, 3)),
                   (snap, (32, 0, 3)), (snap + 3, (46, 0, 6)), (snap + 9, (38, 0, 4)), (pov, (38, 0, 4)),
                   (grasp - 4, (44, -12, -6)), (grasp, (46, -14, -8)), (lifted, (18, 0, 0))]
          + strain(shake, burst, (18, 0, 0), (14, 0, 0), (2.5, 2.5, 2.5), 2, 4)[1:]
          # The souls tear free: he recoils and twists after them.
          + [(burst + 3, (2, 0, 0)), (burst + 12, (0, -12, 2)), (burst + 22, (4, 14, -3)), (burst + 32, (0, -16, 2)),
             (scared - 4, (2, 8, 0)), (scared + 4, (4, 16, -4)), (scared + 13, (4, 16, -4)), (scared + 17, (0, -18, 4)),
             (scared + 25, (0, -18, 4)), (scared + 29, (6, 6, -2)), (strike - 2, (8, 0, 0)), (strike, (-14, 0, 0)),
             (strike + 5, (-4, 0, 0)), (strike2 - 2, (0, 0, 0)), (strike2, (-24, 0, 0)), (floor, (-12, 0, 0)), (floor + 5, (-9, 0, 0))]
          # Every intake impact kicks the torso upward, even when the soul strikes his mask.
          + jolts(chest, (18, 0, 0), (-9, 0, 0), fall=2)
          + [(writhe - 2, (-9, 0, 0))] + strain(writhe, smoke_at, (2, 0, 0), (8, 0, 0), (10, 16, 12), 3, 7)[1:]
          + [(smoke_at + 4, (12, 0, 0))])
    a.rot('hood', [(0, (22, 0, 0)), (imp, IMPACT_POSE['hood']), (imp + 6, (20, -6, -4)), (snap, (20, -6, -4)),
                   (snap + 4, (30, 8, 0)), (pov, (30, 8, 0)), (grasp, (34, -10, 0)), (lifted, (30, 0, 0))]
          + strain(shake, burst, (30, 0, 0), (26, 0, 0), (3, 3, 3), 2, 5)[1:]
          # His eyes after the souls (the POV follows the hood): up, left, right, up.
          + [(burst + 4, (-6, 0, 0)), (burst + 10, (-34, -10, 0)), (burst + 16, (-34, -10, 0)), (burst + 21, (-16, 46, 0)),
             (burst + 27, (-16, 46, 0)), (burst + 32, (-22, -50, 4)), (burst + 38, (-22, -50, 4)), (scared - 4, (-30, 8, 0)),
             # Facing the camera: snapping from one whirling soul to the next.
             (scared + 3, (-62, 22, 0)), (scared + 10, (-62, 22, 0)), (scared + 13, (-66, -24, 4)), (scared + 21, (-66, -24, 4)),
             (scared + 25, (-68, 10, 0)), (scared + 30, (-68, 10, 0)), (strike - 3, (-70, -10, 0)), (strike, (-85, 0, 0)),
             (strike + 5, (-30, 8, 0)), (strike2 - 3, (-18, 0, 0)), (strike2, (-60, 0, 0)), (floor, (-30, 0, 0)), (floor + 5, (0, 0, 0))]
          + jolts(face, (-7, 0, 0), (0, 0, 0), fall=1)
          + [(writhe - 2, (0, 0, 0))] + strain(writhe, smoke_at, (0, 0, 0), (-10, 0, 0), (12, 40, 12), 3, 8)[1:]
          + [(smoke_at + 4, (-35, 0, 0))])
    a.scale('hood', [(0, 1), (HUSK - 1, 1), (HUSK, 0), (LENGTH, 0)])

    kick = lambda seed: strain(writhe, smoke_at, (-12, 0, 0), (-10, 0, 0), (22, 6, 10), 4, seed)
    a.rot('mage_leg_right', [(0, (-30, 0, 0)), (imp - 2, (-30, 0, 0)), (imp, IMPACT_POSE['leg_r']), (strike2, IMPACT_POSE['leg_r']),
                             (strike2 + 6, (-80, 0, 0)), (floor, (-45, 0, 4)), (floor + 6, (-14, 0, 4)), (pour + 8, (-12, 0, 0))]
          + jolts(intake, (-42, 0, 8), (-12, 0, 0), fall=2) + [(writhe, (-12, 0, 0))]
          + kick(13)[1:] + [(smoke_at + 6, (-8, 0, 4))])
    a.rot('mage_leg_left', [(0, (20, 0, 0)), (imp - 2, (20, 0, 0)), (imp, IMPACT_POSE['leg_l']), (strike2, IMPACT_POSE['leg_l']),
                            (strike2 + 6, (-40, 0, 0)), (floor, (-30, 0, -4)), (floor + 6, (-8, 0, -4)), (pour + 8, (-12, 0, 0))]
          + jolts(intake, (-30, 0, -7), (-12, 0, 0), fall=2) + [(writhe, (-12, 0, 0))]
          + kick(14)[1:] + [(smoke_at + 6, (-14, 0, -4))])
    for side, sign in (('right', -1), ('left', 1)):
        a.rot('skirt_' + side, [(0, (-35, 0, sign * 22)), (imp - 2, (-30, 0, sign * 18)), (imp, (-18, 0, sign * 16)),
                                (imp + 6, (-15, 0, sign * 14)), (strike2, (-15, 0, sign * 14)), (floor, (-30, 0, sign * 24)),
                                (floor + 6, (-8, 0, sign * 22)), (LENGTH, (-8, 0, sign * 22))])
        a.rot('hood_' + side, [(0, (0, sign * 20, sign * 10)), (imp, ZERO), (LENGTH, ZERO)])
    a.rot('robe_tail', [(0, (60, 0, 0)), (imp - 2, (50, 0, 0)), (imp, (8, 0, 0)), (imp + 6, (14, 0, 0)), (strike2, (14, 0, 0)),
                        (floor, ZERO), (LENGTH, ZERO)])
    # The second strike snuffs his eyes during the fall, permanently for this cinematic.
    # Both the flare geometry and the painted face texels must be hidden.
    eye_out = floor - 4
    a.scale('mage_eye_void', [(0, 0), (strike2 + 1, 0), (strike2 + 2, 1), (LENGTH, 1)])
    for side in ('right', 'left'):
        eye = 'mage_eye_' + side
        a.pos(eye, [(0, ZERO), (burst - 1, ZERO), (burst + 2, (0, 0, -1)),
                    (strike2, (0, 0, -1.7)), (eye_out, ZERO), (LENGTH, ZERO)])
        a.scale(eye, [(0, 1), (lifted, 1), (burst - 2, 1.2), (burst, 1.8), (burst + 8, 1.4),
                      (strike - 1, 1.4), (strike, 2.4), (strike + 4, 1.6), (strike2, 2.6),
                      (strike2 + 3, .6), (eye_out, 0), (LENGTH, 0)])
    # Arms: the staff hand rides the fall overhead and plants the staff on landing; the empty hand
    # flails and steadies him. Both hold the halves until he is thrown back, fling wide, lie
    # splayed, claw at his face and chest as he writhes and fall limp (held poses solved below).
    for side, sign in (('right', 1), ('left', -1)):
        early = ([(0, (-120, 0, 10)), (imp - 2, (-110, 0, 10)), (imp, IMPACT_POSE['right_arm']), (snap, IMPACT_POSE['right_arm']),
                  (snap + 3, (-18, 0, 14)), (pov, (-22, 0, 12))] if side == 'right' else
                 [(0, (-125, 0, -20)), (imp - 2, (-100, 0, -18)), (imp, IMPACT_POSE['left_arm']), (imp + 6, (-6, 0, -10)),
                  (snap, (-6, 0, -10)), (snap + 3, (-30, 0, -14)), (pov, (-20, 0, -12))])
        twitch = jolts(intake, (-32, sign * 12, sign * 60), (0, 0, sign * 80), fall=2)
        twitch += strain(writhe, smoke_at, (-4, 0, sign * 76), (-4, 0, sign * 76), (24, 12, 14), 3, 20 + sign)
        a.rot(side + '_arm', early + [(strike2, (-40, 0, sign * 14)), (strike2 + 6, (-140, 0, sign * 30)), (floor, (-120, 0, sign * 55)),
                                      (floor + 6, (0, 0, sign * 80)), (pour + 6, (0, 0, sign * 80))] + twitch
              + [(smoke_at + 6, (6, 0, sign * 74)), (LENGTH, (6, 0, sign * 74))])
        a.rot('mage_forearm_' + side, [(0, ZERO), (imp, IMPACT_POSE['fore_r'] if side == 'right' else ZERO),
                                       (snap + 3, (-10 if side == 'right' else -20, 0, 0)), (strike2, (-20, 0, 0)),
                                       (floor, (10, 0, 0)), (floor + 6, (16, 0, 0)), (LENGTH, (16, 0, 0))])
        a.rot('mage_hand_' + side, [(0, ZERO), (writhe, ZERO), (writhe + 4, (-50, 0, 0)), (smoke_at + 2, (-50, 0, 0)),
                                    (smoke_at + 6, ZERO), (LENGTH, ZERO)])
        for k in range(3):
            spread = (k - 1) * 14
            grip = (70, 0, spread / 4)
            claw_ = strain(writhe + 2, smoke_at, (55, 0, spread), (40, 0, spread), (25, 4, 6), 3, 30 + k)
            a.rot(f'mage_finger_{side}_{k}', [(0, (-30, 0, spread)), (imp, grip if side == 'right' else (-20, 0, spread)),
                                               (grasp - 3, grip if side == 'right' else (-35, 0, spread)), (grasp, grip),
                                               (strike2, grip), (strike2 + 3, (-40, 0, spread * 1.5)), (floor + 6, (10, 0, spread)),
                                               (writhe, (10, 0, spread))] + claw_ + [(smoke_at + 6, (15, 0, spread)), (LENGTH, (15, 0, spread))])

    possession(a)

    splayed_convulsions(a)


def splayed_convulsions(a):
    """Outstretched arms held in tension, with uneven shoulder, wrist and finger tremors.

    Each side shakes independently; the elbows stay mostly open so the silhouette remains
    spread across the floor instead of folding inward into a face-clutching pose.
    """
    start, stop = B['writhe'], B['smoke'] + 6
    weight = lambda t: ramp(t, start - 4, start + 2) * (1 - ramp(t, B['smoke'] + 1, stop))
    def tremor(t, phase, lag=0):
        age = t - start - lag
        strength = .45 + .55 * math.sin(age * .19 + phase) ** 2
        return strength * (.7 * math.sin(age * 1.65 + phase) + .3 * math.sin(age * 2.47 + phase * 1.7))
    def replace(name, pose):
        frames = {}
        for t in range(LENGTH + 1):
            old = a.sample(name, 'rotation', t, ZERO)
            w = weight(t)
            frames[t] = old * (1 - w) + np.array(pose(t)) * w
        a.bake(name, 'rotation', frames)
    for side, sign, phase in (('right', 1, .3), ('left', -1, 2.2)):
        replace(side + '_arm', lambda t, sign=sign, phase=phase:
                (-4 + 3 * tremor(t, phase), sign * (3 + 3 * tremor(t, phase, .6)),
                 sign * (82 + 4 * tremor(t, phase, 1))))
        replace('mage_forearm_' + side, lambda t, phase=phase:
                (-11 + 5 * tremor(t, phase, .8), 2 * tremor(t, phase, 1.2), 0))
        replace('mage_hand_' + side, lambda t, sign=sign, phase=phase:
                (-8 + 12 * tremor(t, phase, 1.6), sign * 6 * tremor(t, phase, 2), 5 * tremor(t, phase, 1.2)))
        for k in range(3):
            replace(f'mage_finger_{side}_{k}', lambda t, k=k, phase=phase:
                    (25 + 18 * tremor(t, phase + k * .7, 2 + k * .4), 0, (k - 1) * 22))


def possession(a):
    """A causal, damped reaction travels through the body; cloth trails the underlying limbs.

    Soft arrivals accumulate into a sustained arch, with larger, irregular spasms among them.
    Each part has its own delay and recovery instead of resetting every three ticks.
    """
    begin, end = B['floor'] + 6, B['smoke'] + 6
    # Faster arrivals overlap more, so each counts for less: the same violence, sooner.
    impacts = [(p['arrive'], ((.85 if k % 5 == 0 else .25) + .2 * hashf(k, 80)) * INTAKE / 2.5)
               for k, p in enumerate(sorted((p for p in soul_plan().values() if p['arrive'] >= B['pour']), key=lambda p: p['arrive']))]
    # After the last soul, residual spasms weaken before the smoke takes over.
    impacts += [(B['writhe'] + d, strength) for d, strength in ((1, .9), (10, .65), (21, .8), (31, .4))]

    def wave(t, delay=0, decay=7, period=8, alternate=False):
        result = 0.0
        for k, (at, strength) in enumerate(impacts):
            age = t - at - delay
            if 0 <= age < 40:
                result += strength * math.exp(-age / decay) * math.sin(math.pi * age / period) * (-1 if alternate and k % 2 else 1)
        return result

    envelope = lambda t: ramp(t, begin, begin + 8) * (1 - ramp(t, B['smoke'] - 2, end))
    channels = {}
    def add_channel(name, channel, pose):
        old = {t: a.sample(name, channel, t, ZERO) for t in range(LENGTH + 1)}
        channels[name, channel] = {t: old[t] * (1 - envelope(t)) + np.array(pose(t)) * envelope(t) for t in old}

    add_channel('body', 'rotation', lambda t: (-6 + 12 * wave(t, 1), 3 * wave(t, 2, alternate=True), 4 * wave(t, 3, alternate=True)))
    add_channel('hood', 'rotation', lambda t: (-8 - 14 * wave(t, 0, 4, 5), 5 * wave(t, 1, 6, 8, True), 3 * wave(t, 2, 7, 9, True)))
    for side, sign, lag in (('right', 1, 0), ('left', -1, 2)):
        add_channel('mage_leg_' + side, 'rotation', lambda t, sign=sign, lag=lag: (-12 - 24 * wave(t, 3 + lag, 9, 10), sign * 3, sign * (4 + 6 * wave(t, 4 + lag, 8, 10))))
        add_channel('mage_shin_' + side, 'rotation', lambda t, lag=lag: (16 + 40 * wave(t, 4 + lag, 10, 11), 0, 0))
        add_channel('skirt_hem_' + side, 'rotation', lambda t, sign=sign, lag=lag: (12 + 27 * wave(t, 6 + lag, 12, 13), sign * 2 * wave(t, 7 + lag), 0))
        add_channel('skirt_' + side, 'rotation', lambda t, sign=sign, lag=lag: (-8 - 15 * wave(t, 5 + lag, 11, 12), sign * 3 * wave(t, 6 + lag), sign * (20 + 5 * wave(t, 5 + lag))))
        add_channel(side + '_arm', 'rotation', lambda t, sign=sign, lag=lag: (-5 - 20 * wave(t, 2 + lag, 7, 8), sign * 8 * wave(t, 3 + lag), sign * (70 - 16 * wave(t, 3 + lag))))
        add_channel('mage_forearm_' + side, 'rotation', lambda t, lag=lag: (12 - 28 * wave(t, 4 + lag, 8, 9), 0, 0))
        add_channel('mage_hand_' + side, 'rotation', lambda t, lag=lag: (-8 - 20 * wave(t, 5 + lag, 9, 10), 0, 0))
    add_channel('robe_tail', 'rotation', lambda t: (-5 - 8 * wave(t, 6, 12, 12), 2 * wave(t, 7, alternate=True), 0))
    # The newly articulated knees and hems must not reset to zero when the convulsion
    # envelope fades. Settle the whole lower garment into one compatible resting pose.
    settle_start, settle_end = B['smoke'] - 3, B['smoke'] + 16
    rest = {'robe_tail': (-2, 0, 0)}
    for side, sign in (('right', 1), ('left', -1)):
        rest.update({
            'mage_leg_' + side: (-10, sign * 2, sign * 6),
            'mage_shin_' + side: (18, 0, 0),
            'skirt_' + side: (-7, sign * 2, sign * 8),
            'skirt_hem_' + side: (12, 0, 0),
        })
    for name, target in rest.items():
        rows = channels[name, 'rotation']
        start_pose = rows[settle_start].copy()
        for t in range(settle_start, LENGTH + 1):
            f = ramp(t, settle_start, settle_end)
            rows[t] = start_pose * (1 - f) + np.array(target) * f
    for (name, channel), rows in channels.items():
        a.bake(name, channel, rows)

    # The pelvis carries the lower robe, so the visible waist and trouser panels move together.
    # Rock around the hips rather than around the model origin at its feet.
    hip = a.point('robe', begin, [0, 14, 1])
    frames = {}
    for t in range(LENGTH + 1):
        w = envelope(t)
        lift = max(0, wave(t, 2.5, 9, 10)) * 1.3
        sway = wave(t, 4, 10, 11, True)
        change = (translation([.5 * sway * w, lift * w, 0]) @ translation(hip)
                  @ axis_angle([0, 0, 1], 2 * sway * w)
                  @ axis_angle([1, 0, 0], 2 * wave(t, 3, 10, 12) * w) @ translation(-hip))
        frames[t] = change @ a.world('robe', t)
    a.place('robe', frames)

    # Keep the robe, folded hems and back of the hood just above the floor.
    # A broad correction envelope avoids tiny contact adjustments vibrating the pelvis.
    contact = []
    for entry in cubes:
        name, raw = entry['bone'], entry['raw']
        if name not in ('robe', 'robe_tail', 'body', SHELL_CLOSED, 'hood', 'mage_face') and not name.startswith(('skirt_', 'mage_leg_', 'mage_shin_')):
            continue
        origin, size = np.array(raw['origin']), np.array(raw['size'])
        pivot = np.array(raw.get('pivot', [0, 0, 0])) * [-1, 1, 1]
        local = translation(pivot) @ rig.rotation(raw.get('rotation', ZERO)) @ translation(-pivot)
        vertices = np.array([local @ [*((origin + size * [x, y, z]) * [-1, 1, 1]), 1]
                             for x in (0, 1) for y in (0, 1) for z in (0, 1)]).T
        contact.append((name, vertices))
    correction = {}
    for t in range(begin, HUSK):
        lowest = min((a.world(name, t) @ points)[1].min() for name, points in contact)
        correction[t] = max(0, .06 - lowest)
    rows = {t: a.sample('robe', 'position', t, ZERO).copy() for t in range(LENGTH + 1)}
    for t in correction:
        rows[t][1] += max(correction.get(t + offset, 0) for offset in range(-2, 3))
    a.bake('robe', 'position', rows)


def staff(a):
    """The staff snaps under him: the lower half stays in his right hand, the head falls by his
    knee. He picks the head up and holds both halves before his chest, broken ends meeting in a
    peak, the skull turned back at him. They shake harder and harder, the broken ends flare and
    the souls tear out of them; when the second soul floors him the halves fly out of his hands
    (as free stand-ins) and lie on the floor until the roar burns them."""
    imp, snap, pov, grasp, lifted = B['impact'], B['snap'], B['pov'], B['grasp'], B['lifted']
    shake, burst, scared, strike2 = B['shake'], B['burst'], B['scared'], B['strike2']
    a.scale('staff_shaft', [(0, 1), (snap - 1, 1), (snap, 0), (LENGTH, 0)])
    for half in ('staff_lower', 'staff_upper'):
        a.scale(half, [(0, 0), (snap - 1, 0), (snap, 1), (LENGTH, 1)])
    a.scale('staff_flame', [(0, 1.2), (imp, 1.6), (snap, 1.8), (snap + 2, 2.4), (snap + 5, .6), (pov, .35), (lifted, .3),
                            (shake, .5), (burst - 2, 1.2), (burst, 0), (LENGTH, 0)])
    flare = ([(0, 0), (shake, 0), (shake + 8, .3), (burst - 3, .6), (burst, 1)]
             + [(t, .55 + .35 * hashf(t, 5)) for t in range(burst + 2, burst + 32, 2)] + [(scared, .35), (strike2 - 1, .3), (strike2, 0), (LENGTH, 0)])
    for half in ('lower', 'upper'):
        a.scale(f'staff_glow_{half}', flare)
    # Overhead through the fall, planted on landing; the lift is solved below.
    a.rot('staff', [(0, (110, 0, 0)), (imp - 2, (100, 0, 0)), (imp, IMPACT_POSE['staff']), (snap, IMPACT_POSE['staff']),
                    (snap + 3, (60, 0, 0)), (pov, (70, 0, -10)), (LENGTH, (70, 0, -10))])
    brk = PIVOT['staff_head']  # the break, in the rig's frame
    last = strike2 - 1

    def held(t, lower):
        """In front of his chest, in the body's frame: the halves rise to a peak where the
        broken ends meet, the lower half from his right, the head from his left; rattling
        harder and harder until the souls are out."""
        t = min(t, last)
        body = a.world('body', t)
        rb = np.eye(4)
        rb[:3, :3] = body[:3, :3] / np.linalg.norm(body[:3, :3], axis=0)
        side = 1 if lower else -1
        k = ramp(t, lifted - 6, lifted) * .22 + ramp(t, shake, burst) * .9 + .6 * (ramp(t, burst, burst + 3) - ramp(t, burst + 28, scared + 4))
        wobble = np.array([math.sin(t * 2.3), math.sin(t * 3.1 + 1), math.sin(t * 1.7 + 2)])
        jitter = np.array([hashf(t, 1, side), hashf(t, 2, side), hashf(t, 3, side)]) * 2 - 1
        crack = (body @ [side * 2.2, 27, -11, 1])[:3] + (wobble * .4 + jitter * .6) * k
        rattle = axis_angle([hashf(t, 4, side) - .5, hashf(t, 5, side) - .5, .3], 9 * k * (hashf(t, 7, side) * 2 - 1))
        direction = [-1, .8, -.2] if lower else [-1, -.8, .2]
        roll = 0 if lower else 180  # the staff's skull turned back to stare at him
        return translation(crack) @ rb @ aligning([0, 1, 0], direction) @ axis_angle([0, 1, 0], roll) @ rattle @ translation(-brk)

    def lerp_pose(m0, m1, f):
        m = slerp(m0, m1, f)
        p0, p1 = (m0 @ [*brk, 1])[:3], (m1 @ [*brk, 1])[:3]
        m[:3, 3] = p0 + (p1 - p0) * f - m[:3, :3] @ brk
        return m

    # 1. The lower half: in his hand as authored until he lifts it with the head.
    carried = {t: a.world('staff', t) for t in range(LENGTH + 1)}
    lower = {t: carried[t] if t < grasp else lerp_pose(carried[grasp], held(t, True), smooth((t - grasp) / (lifted - grasp)))
             for t in range(LENGTH + 1)}
    hand_on = lambda m, p: (m @ [*(np.array(p) * [-1, 1, 1]), 1])[:3]
    release = lambda t: 1 - ramp(t, strike2, strike2 + 4)
    a.reach('right', lambda t: hand_on(lower[min(t, last)], [-5, 11.5, 1]) if t >= grasp else None, release)
    a.place('staff', lower, lambda t: 0 if t >= strike2 else 1)

    # 2. The head: rigid with the staff until it snaps, then toppling onto the floor by his left
    # knee, lifted by the left hand and held against the lower half.
    at_snap = carried[snap - 1]
    spot = np.array(a.point('robe', imp + 8, [3, 0, -9]))
    spot[1] = 1.2
    ground = translation(spot) @ aligning([0, 1, 0], [-.35, 0, -1]) @ translation(-brk)

    def head(t):
        if t < snap:
            return carried[t]
        if t < snap + 8:
            f = (t - snap) / 8
            m = lerp_pose(at_snap, ground, f ** 1.6)
            m[1, 3] += 3 * math.sin(math.pi * f) * (1 - f)
            return m
        if t < grasp:
            return ground
        return lerp_pose(ground, held(t, False), smooth((t - grasp) / 12))
    heads = {t: head(t) for t in range(LENGTH + 1)}
    a.place('staff_head', heads)
    a.reach('left', lambda t: hand_on(heads[min(t, last)], [-5, 25, 1]) if t >= grasp - 8 else None,
            lambda t: ramp(t, grasp - 8, grasp) * release(t))

    # Free stand-ins take over as the halves fly out of his hands, tumbling onto the floor.
    for name, source, fling, axis in (('staff_drop_lower', 'staff', (1.6, 2.4, -.6), (0, .3, 1)),
                                      ('staff_drop_upper', 'staff_head', (-1.4, 2.8, .9), (1, .2, 0))):
        start = a.world(source, last)
        p = PIVOT[name]
        c0 = (start @ [*p, 1])[:3]
        frames, landed = {}, None
        for t in range(LENGTH + 1):
            k = t - last
            if k <= 0:
                frames[t] = start
                continue
            if landed is None:
                c = c0 + np.array([fling[0] * k, fling[1] * k - .3 * k * k, fling[2] * k])
                m = translation(c) @ axis_angle(axis, 34 * k) @ translation(-c0) @ start
                if c[1] < 1.2:
                    c[1] = 1.2
                    landed, fallen = t, m
                    flat_axis = fallen[:3, :3] @ [0, 1, 0]
                    flat_axis[1] = 0
                    flat = aligning(fallen[:3, :3] @ [0, 1, 0], flat_axis) @ fallen
                    rest = c + np.array([fling[0], 0, fling[2]]) * 2.5
            if landed is not None:
                f = smooth((t - landed) / 4)
                m = slerp(fallen, flat, f)
                m[:3, 3] = (c * (1 - f) + rest * f) - m[:3, :3] @ p
            frames[t] = m
        a.place(name, frames, lambda t: 0 if t < strike2 else 1 - ramp(t, B['roar'], B['roar'] + 14), simplify=True)


def souls(a):
    """The souls trapped in the staff: torn out of its broken ends, spiralling up into a storm
    about him, two diving into his face to floor him, the rest shooting into his mask
    once he is down. Each faces its flight."""
    mix_path = lambda x, y, k: x * (1 - k) + y * k
    plan = soul_plan()
    lying = a.point('body', B['floor'] + 6, [0, 22, 1]) * [1, 0, 1]
    down = lambda t: ramp(t, B['strike2'], B['pour'] + 8)
    centre_ = lambda t: lying * down(t)
    targets = {'face': lambda t: a.point('mage_face', t, [0, 31, -.4]), 'chest': lambda t: a.point('body', t, [0, 22, -3.5])}
    for i in range(SOULS):
        p = plan[i]
        e, dive, arrive = p['emerge'], p['dive'], p['arrive']
        src = a.point('staff_' + p['src'], e, [-5, STAFF_BREAK, 1])
        h_out = 16 + 144 * (i + .5) / SOULS
        r_out = 30 + h_out * .48 + 8 * hashf(i, 21)
        omega = math.tau / (54 + 20 * hashf(i, 23))
        rel = src - centre_(e)
        theta0, r0 = math.atan2(rel[2], rel[0]), math.hypot(rel[0], rel[2])
        phase = hashf(i, 24) * math.tau
        target = targets[p['target']]

        def orbit(t, e=e, src=src, theta0=theta0, omega=omega, r_out=r_out, h_out=h_out, phase=phase, i=i, source=p['src']):
            # Launch vertically from the broken staff before any sideways orbit can cross his eyes.
            age = max(0, t - e)
            # Stay attached to the moving broken end while the head pulls free.
            # The first eight ticks are slow enough to see the ghost being extruded.
            tip = a.point('staff_' + source, min(round(t), e + 8), [-5, STAFF_BREAK, 1])
            lift = .14 * age * age if age <= 8 else 8.96 + 3.2 * (age - 8) + .3 * (age - 8) ** 2
            rocket = tip + np.array([(-1 if i % 2 else 1) * min(age, 18) * .12,
                                    min(lift, 72.8), -min(age, 18) * .55])
            spread = ramp(age, 18, 36)
            theta = theta0 + omega * max(0, age - 14) + i * 2.399963
            radius = r_out * (1 - .12 * down(t)) * (1 + .06 * math.sin(.13 * t + phase))
            height = h_out * (1 - .12 * down(t)) + 8 * down(t) + 3 * math.sin(.16 * t + phase)
            storm = centre_(t) + [radius * math.cos(theta), height, radius * math.sin(theta)]
            return mix_path(rocket, storm, spread)

        def at(t, orbit=orbit, dive=dive, arrive=arrive, target=target, i=i):
            if i in STRIKERS:
                # Two readable attackers peel away and hang either side of the mask before lunging.
                side = -1 if i == 4 else 1
                ready = a.point('mage_face', dive, [0, 31, -2.4]) + [side * 25, 24, -22]
                here = mix_path(orbit(min(t, dive)), ready, ramp(min(t, dive), dive - 12, dive - 3))
            else:
                here = orbit(min(t, dive))
            if t <= dive:
                return here
            g = min(1, (t - dive) / (arrive - dive))
            # A direct accelerating strike, with a short normal-aligned entry into the surface.
            bone = 'mage_face' if plan[i]['target'] == 'face' else 'body'
            normal = a.world(bone, round(min(t, arrive)))[:3, :3] @ [0, 0, -1]
            normal = normal / np.linalg.norm(normal)
            end = target(min(t, arrive))
            approach = end + normal * 10
            return mix_path(mix_path(here, approach, g ** 1.6), end, ramp(g, .65, 1))

        frames = {}
        for t in range(LENGTH + 1):
            tc = min(max(t, e), arrive)
            here = at(tc)
            forward = at(min(tc + .5, arrive)) - at(max(tc - .5, e))
            if np.linalg.norm(forward) < 1e-3:
                forward = here - centre_(tc) + [0, 0, -1e-3]
            frames[t] = translation(here) @ look_rotation(forward) @ axis_angle([0, 0, 1], 18 * math.sin(.3 * t + i))
        a.place(f'soul_{i}', frames, lambda t, e=e, arrive=arrive: ramp(t, e - 1, e + 3) * (.18 + .82 * ramp(t, e + 12, e + 38)) * (1 - ramp(t, arrive - 2, arrive)),
                simplify=True)


def smoke(a):
    """Preview smoke is a translucent particle pass, never a solid piece of the actor."""
    for j in range(SMOKES):
        a.scale(f'smoke_{j}', [(0,0),(LENGTH,0)])


def claw(a, side, frames):
    """animations.claw in ticks: index and middle lead, pinky lags, thumb opposes."""
    for i, (strength, lag) in enumerate(((.82, 0), (1.08, .9), (.95, 1.7), (.72, 2.6))):
        poses = [(min(LENGTH, t + (lag if 0 < t < LENGTH else 0)), v * strength) for t, v in frames]
        a.rot(f'finger_{side}_{i}', [(t, (v, (i - 1.5) * 2.5, (i - 1.5) * 1.5)) for t, v in poses])
        a.rot(f'tip_{side}_{i}', [(t, (v * .72, 0, 0)) for t, v in poses])
        a.rot(f'end_{side}_{i}', [(t, (v * .42, 0, 0)) for t, v in poses])
    a.rot('thumb_' + side, [(t, (v * .65, (1 if side == 'right' else -1) * v * .65, 0)) for t, v in frames])
    a.rot('thumb_tip_' + side, [(t, (v * .55, 0, 0)) for t, v in frames])


IDLE = dict(pos=(0, -10, 0), spine=(43, -3, 1), skull=(-58, 5, -3), jaw=(4, 0, 0), leg=(-32, 0, 0), shin=(82, 0, 0))
PELVIS = np.array([0, 30, 3.0])  # the colossus is placed by its pelvis


def colossus(a):
    """The giant's parts that only switch on or off; growth.py poses it from the swap to the end."""
    ignite, eyes, end = B['ignite'], B['eyes'], LENGTH
    # His hood stays on the floor with his rags: the skull is bare. The eyes ignite as it sits up.
    a.scale('skull_hood', [(0, 0), (LENGTH, 0)])
    a.scale('colossus_lids', [(0, 1), (eyes - 1, 1), (eyes, 0), (LENGTH, 0)])
    for side in ('right', 'left'):
        a.scale('colossus_eye_' + side, [(0, 0), (eyes - 1, 0), (eyes + 2, 1.6), (eyes + 8, 1), (LENGTH, 1)])
    a.scale('soul_core', [(0, 0), (eyes - 1, 0), (eyes + 2, 1.7), (eyes + 8, 1.1), (ignite - 1, 1.1), (ignite + 2, 1.5),
                          (ignite + 8, 1), (end, 1)])
    a.scale('colossus', [(0, 0), (B['swap'] - 1, 0), (B['swap'], 1), (LENGTH, 1)])  # unseen until he has grown into it


def handback(a):
    """The idle crawl's planted hands, exactly, by the time the camera hands back. rig.plant keeps
    only the palm's pitch, so it solves a copy and only its last beat is kept. Its joint-space blend
    keeps each arm in one piece."""
    land, end = B['land'], LENGTH
    for side, sign in (('right', 1), ('left', -1)):
        scratch = {'bones': {k: dict(v) for k, v in a.clip['bones'].items()}}
        rig.plant(scratch, side, [(t / 20, np.array([sign * 20, 5, -43]), ramp(t, land - 2, end - 4), -65) for t in range(LENGTH + 1)])
        for part in ('arm_', 'forearm_', 'hand_'):
            frames = a.clip['bones'][part + side]['rotation']
            for key in [k for k in frames if float(k) * 20 > land - 2.5]:
                del frames[key]
            frames.update({k: v for k, v in scratch['bones'][part + side]['rotation'].items() if float(k) * 20 > land - 2.5})
            a.clip['bones'][part + side]['rotation'] = dict(sorted(frames.items(), key=lambda kv: float(kv[0])))
    a._changed()
