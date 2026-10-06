"""GeckoLib pose evaluation and offline hand planting (NumPy, no runtime IK).

Coordinates here use GeckoLib's mirrored X, pixels. The numeric output is baked
into the animation, so workshop, client and server sockets use the same pose.
"""
import math
import numpy as np
from geometry import bones

BY_NAME = {b['name']: b for b in bones}


def translation(p):
    out = np.eye(4)
    out[:3, 3] = p
    return out


def rotation(r):
    x, y, z = np.radians(r) * [-1, -1, 1]
    cx, sx, cy, sy, cz, sz = math.cos(x), math.sin(x), math.cos(y), math.sin(y), math.cos(z), math.sin(z)
    return (np.array([[cz, -sz, 0, 0], [sz, cz, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])
            @ np.array([[cy, 0, sy, 0], [0, 1, 0, 0], [-sy, 0, cy, 0], [0, 0, 0, 1]])
            @ np.array([[1, 0, 0, 0], [0, cx, -sx, 0], [0, sx, cx, 0], [0, 0, 0, 1]]))


def sample(channel, t, default):
    if channel is None:
        return np.array(default, dtype=float)
    seq = sorted((float(k), np.array(v, dtype=float)) for k, v in channel.items())
    for (a, x), (b, y) in zip(seq, seq[1:]):
        if a <= t <= b:
            return x + (y - x) * ((t - a) / (b - a))
    return seq[0][1] if t < seq[0][0] else seq[-1][1]


def local(name, clip, t, override=None):
    b = BY_NAME[name]
    channels = clip['bones'].get(name, {})
    pivot = np.array(b['pivot']) * [-1, 1, 1]
    r = np.array(b.get('rotation', [0, 0, 0])) + (override if override is not None else sample(channels.get('rotation'), t, [0, 0, 0]))
    p = sample(channels.get('position'), t, [0, 0, 0]) * [-1, 1, 1]
    s = sample(channels.get('scale'), t, [1, 1, 1])
    return translation(p) @ translation(pivot) @ rotation(r) @ np.diag([*s, 1]) @ translation(-pivot)


def matrix(name, clip, t):
    parent = BY_NAME[name].get('parent')
    return (matrix(parent, clip, t) if parent else np.eye(4)) @ local(name, clip, t)


def hand(clip, t, side='right'):
    point = np.array(BY_NAME['hand_' + side]['pivot']) * [-1, 1, 1]
    return (matrix('hand_' + side, clip, t) @ [*point, 1])[:3]


def plant(clip, side, targets):
    """Solve wrist placement for authored contact targets, preserving anatomical lengths."""
    arm, fore, palm = 'arm_' + side, 'forearm_' + side, 'hand_' + side
    parent = BY_NAME[arm]['parent']
    wrist = np.array(BY_NAME[palm]['pivot']) * [-1, 1, 1]
    rows, fore_rows, palm_rows = {}, {}, {}
    previous = None
    for t, target, weight, palm_pitch in targets:
        ar = sample(clip['bones'].get(arm, {}).get('rotation'), t, [0, 0, 0])
        fr = sample(clip['bones'].get(fore, {}).get('rotation'), t, [0, 0, 0])
        base = matrix(parent, clip, t)
        authored = np.array([ar[0], ar[1], ar[2], fr[0]])
        q = authored.copy() if previous is None or weight < .5 else previous.copy()
        if side == 'left' and weight > 0:
            # The mirrored arm must flare toward its own side. An unconstrained
            # solve can find the same wrist position by folding this elbow
            # across the chest, then flip branches during an ability.
            q[2] = min(q[2], 0)

        def point(v):
            return (base @ local(arm, clip, t, v[:3]) @ local(fore, clip, t, [v[3], 0, 0]) @ [*wrist, 1])[:3]

        target = np.array(target) * weight + point(authored) * (1 - weight)
        if weight <= 1e-6:
            # No contact influence: keep the artist's swing/reach exactly.
            # IK otherwise chooses another elbow branch for the same wrist.
            q = authored
        else:
            for _ in range(50):
                error = target - point(q)
                if np.linalg.norm(error) < .04:
                    break
                jac = np.column_stack([(point(q + np.eye(4)[i] * .1) - point(q)) / .1 for i in range(4)])
                delta = np.linalg.solve(jac.T @ jac + np.eye(4) * .005, jac.T @ error)
                q += np.clip(delta, -10, 10)
                q = np.clip(q, [-170, -75, -70, -140], [80, 75, 0 if side == 'left' else 70, 5])
            # Fade joint angles toward the authored pose as contact ends.
            # A wrist can have multiple valid elbow solutions; fading only its
            # target lets the solver snap to a different one at weight zero.
            fade = weight * weight * (3 - 2 * weight)
            q = authored * (1 - fade) + q * fade
        previous = q
        key = f'{t:.4f}'
        rows[key] = [round(float(v), 4) for v in q[:3]]
        fore_rows[key] = [round(float(q[3]), 4), 0, 0]
        old = sample(clip['bones'].get(palm, {}).get('rotation'), t, [0, 0, 0])
        # Desired palm direction in the ground plane, counter-rotating the supporting arm.
        spine = sample(clip['bones'].get('spine', {}).get('rotation'), t, [0, 0, 0])[0] + BY_NAME['spine'].get('rotation', [0, 0, 0])[0]
        pitch = palm_pitch - spine - q[0] - q[3] - BY_NAME[arm].get('rotation', [0, 0, 0])[0] - BY_NAME[fore].get('rotation', [0, 0, 0])[0]
        fade = weight * weight * (3 - 2 * weight)
        palm_rows[key] = [round(float(old[0] * (1 - fade) + pitch * fade), 4), 0, 0]
    for name, rows_ in [(arm, rows), (fore, fore_rows), (palm, palm_rows)]:
        clip['bones'].setdefault(name, {})['rotation'] = rows_


def bake_contacts(clips):
    for side, sign in [('right', 1), ('left', -1)]:
        for name in ['colossus_idle', 'colossus_walk', 'colossus_roar', 'colossus_swipe', 'colossus_lunge']:
            clip = clips[name]
            targets = []
            for tick in range(round(clip['animation_length'] * 20) + 1):
                t = tick / 20
                if name == 'colossus_idle':
                    weight = 1
                    target = [sign * 20, 5.5, -43 - .6 * math.sin(t * math.pi / 2)]
                    pitch = -65
                elif name == 'colossus_walk':
                    # During the loaded half of each step, the wrist slides
                    # backward relative to the advancing body. The other half
                    # lifts and reaches forward; left and right alternate.
                    phase = (t / 1.8 + (0 if sign > 0 else .5)) % 1
                    if phase < .55:
                        target = [sign * 20, 5, -49 + 17 * phase / .55]
                    else:
                        swing = (phase - .55) / .45
                        target = [sign * 20, 5 + 10 * math.sin(math.pi * swing), -32 - 17 * swing]
                    weight = 1
                    pitch = -65 if phase < .55 else -65 + 25 * math.sin(math.pi * swing)
                elif name in ('colossus_roar', 'colossus_swipe'):
                    # Reared attacks begin and recover on the same four points
                    # of contact as the crawl, avoiding a standing-pose pop.
                    duration = clip['animation_length']
                    weight = max(0, 1 - t / .55, (t - duration + .55) / .55)
                    target = [sign * 20, 5, -43]
                    pitch = -65
                else:
                    weight = 1
                    if t <= .8:
                        stride = math.sin(t * math.pi * 2 + (0 if sign > 0 else math.pi))
                        target = [sign * 20, 5 + max(0, stride) * 5, -43 + stride * 7]
                    elif t < 1:
                        stride = math.sin(.8 * math.pi * 2 + (0 if sign > 0 else math.pi))
                        start = [5 + max(0, stride) * 5, -43 + stride * 7]
                        target = [sign * 20, *sample({'0.8': start, '1': [13, -52]}, t, start)]
                    elif t < 1.7:
                        # Flight is a true reaching jump: both wrists project
                        # ahead of the ribcage, palms open toward the victim.
                        reach = sample({'1': [13, -52], '1.3': [23, -59], '1.5': [20, -61], '1.7': [5, -50]}, t, [5, -50])
                        target = [sign * 20, reach[0], reach[1]]
                    else:
                        settle = min(1, (t - 1.7) / .8)
                        target = [sign * 20, 5, -50 + 7 * settle]
                    pitch = sample({'0': [-65], '.8': [-65], '1.2': [-25], '1.5': [-25], '1.8': [-65], '2.5': [-65]}, t, [-65])[0]
                targets.append((t, target, weight, pitch))
            plant(clip, side, targets)
    # Grab is authored against a feasible wrist path; the server grip is sampled from this rig.
    clip = clips['colossus_grab']
    target_keys = {'0': [20, 5, -43], '.25': [12, 12, -45], '.8': [8, 23, -43], '1.8': [8, 72, -30], '2.2': [8, 11, -48], '2.75': [15, 8, -45], '3.2': [20, 5, -43]}
    targets = []
    for tick in range(65):
        t = tick / 20
        pitch = sample({'0': [-65], '.3': [-50], '.8': [-10], '2.2': [-10], '2.8': [-50], '3.2': [-65]}, t, [-65])[0]
        targets.append((t, sample(target_keys, t, [8, 23, -43]), 1, pitch))
    plant(clip, 'right', targets)
    # The free hand braces the low body before and after the lift.
    targets = []
    for tick in range(65):
        t = tick / 20
        weight = max(0, 1 - t / .55, (t - 2.65) / .55)
        targets.append((t, [-20, 5, -43], weight, -65))
    plant(clip, 'left', targets)
