"""Talon variants for the Storm Roc's feet, so the grab in its intro shows real raptor feet.

`current` is the installed foot: flat toes whose front talons are a pixel long. The others
rebuild each toe on its own two bones (base and tip, so the study's grab still curls them):
a thick scaled digit splayed out from the foot, a raised knuckle, and a talon of three
tapering blocks that arcs up, over and down to a sharp tip resting on the ground. The back
toe (the hallux) carries the biggest talon, like an eagle's.

    build_mod.refine(model, head, feet='raptor')
"""
import math
import numpy as np

# digit: the toe's width; talon / hallux: (arc length, base thickness) of the front and back
# talons; hook: the talon's angle at its tip (deg, below horizontal is negative); splay: how far
# the outer front toes turn out; dig: how far the tip bites into the rock (model pixels). The
# talon rises from the top of the toe's end pad just steeply enough to land its tip there.
# reach: how far the toe runs past its tip joint; legs: how much thicker each leg bone's blocks
# get across the leg (the bones keep their lengths, which the leg solve and sockets rely on).
FEET = {
    'current': dict(label='Installed', note='The installed feet, for comparison: flat toes, front talons a pixel long.'),
    'raptor': dict(label='Raptor', note='Thick scaled toes splayed out, long curved black talons, the back one biggest.',
        digit=5, talon=(14, 3.2), hallux=(18, 3.6), hook=-95, splay=14, dig=1.5),
    'sickle': dict(label='Sickle', note='Huge sickle talons over a block long on heavier toes.',
        digit=6, talon=(19, 4), hallux=(24, 4.4), hook=-100, splay=16, dig=2),
    'grapple': dict(label='Grapple', note='Big talons hooked tight, curling back under like an eagle closing on prey.',
        digit=5.5, talon=(17, 3.6), hallux=(21, 4), hook=-150, splay=14, dig=0),
    'sickle_big': dict(label='Big legs', note='Sickle talons a third bigger on much thicker legs: heavier thighs and trousers, a broad lower leg and foot, longer toes.',
        digit=7, talon=(25, 5), hallux=(31, 5.5), hook=-100, splay=16, dig=2, reach=8,
        legs=dict(thigh=1.3, shin=1.35, tarsus=1.7, foot=1.45)),
    'sickle_huge': dict(label='Huge legs', note='Bigger again: massive sickle talons and legs about twice as thick below the knee.',
        digit=8.5, talon=(30, 6), hallux=(37, 6.5), hook=-100, splay=18, dig=2.5, reach=9.5,
        legs=dict(thigh=1.45, shin=1.55, tarsus=2.1, foot=1.75)),
}

def _thicken(m, label, legs):
    """Fatten a leg's blocks across the leg about each bone's own axis, keeping their heights,
    so the foot's sole and the joints stay where the leg solve put them."""
    from fold import rotation
    for bone, k in legs.items():
        name = f'{label}_{bone}'; axis = m.pivot(name)
        for c in m.by[name]['cubes']:
            size = np.array(c['size'], dtype=float)
            centre = np.array(c['pivot'], dtype=float) if 'pivot' in c else np.array(c['origin']) + size/2
            R = rotation(c.get('rotation', [0, 0, 0]))
            s = np.full(3, k); s[np.argmax(np.abs(R[1]))] = 1   # not along the leg
            centre = axis + (centre - axis)*[k, 1, k]
            if bone == 'tarsus':
                # In the brawl crouch the lower leg lies nearly flat on its back face, so it
                # keeps that face and grows toward the front, which then faces up.
                centre[2] = c['origin'][2] + size[2] - size[2]*s[2]/2
            c['size'] = (size*s).tolist(); c['origin'] = (centre - size*s/2).tolist()
            if 'pivot' in c: c['pivot'] = centre.tolist()

def _arc(start, d, up, length, a0, a1, n=48):
    """Points along a circular arc in the (d, up) plane: tangent angle a0 at the start to a1."""
    pts = [np.array(start, dtype=float)]; ds = length/n
    for k in range(n):
        a = math.radians(a0 + (a1 - a0)*(k + .5)/n)
        pts.append(pts[-1] + (d*math.cos(a) + up*math.sin(a))*ds)
    return np.array(pts)

def _replace(m, part, bone, a, b, width, thick, up, **tags):
    """Move an existing block to run a->b, keeping its UV island, and tag it for the painter."""
    c = part['cube']; uv = c['uv']
    new = m.slab(bone, a, b, width, thick, part['mat'], up=up, kind=part['kind'])
    m.by[bone]['cubes'].pop(); m.parts.pop()
    c.clear(); c.update(new); c['uv'] = uv; part.update(tags)

def shape(m, feet, sole=3.5):
    v = FEET[feet]
    if 'digit' not in v: return
    Y = np.array([0., 1., 0.])
    for label in ('left', 'right'):
        if 'legs' in v: _thicken(m, label, v['legs'])
        centre = m.pivot(f'{label}_foot')
        for i in range(4):
            base, tip = f'{label}_toe_{i}_base', f'{label}_toe_{i}_tip'
            P0, T = m.pivot(base), m.pivot(tip)
            d = (T - P0)*[1, 0, 1]; d /= np.linalg.norm(d)
            back = i == 3
            if not back:
                # The outer front toes turn out from the middle one.
                side = np.sign(round(P0[0] - centre[0], 3))
                a = -math.radians(v['splay'])*side
                d = np.array([d[0]*math.cos(a) + d[2]*math.sin(a), 0, -d[0]*math.sin(a) + d[2]*math.cos(a)])
            length, thick = v['hallux'] if back else v['talon']
            w = v['digit']*(1.1 if back else 1)
            side_axis = np.cross(Y, d)
            # A level toe: the base block, then the tip's old block as the end pad, deep enough to
            # carry the talon from its top front edge.
            reach = v.get('reach', 6)
            K = T + d*(reach - 1 if back else reach) + Y*1
            bp = next(p for p in m.parts if p['bone'] == base)
            _replace(m, bp, base, P0 - d*2, T + d*1, w, w*.9, Y, feet='digit')
            tp = next(p for p in m.parts if p['bone'] == tip)
            _replace(m, tp, tip, T - d*1, K + d*.5, w*.92, w*1.05, Y, feet='digit')
            S = K + d*.2 + Y*(w*.3)
            # The talon arcs from rising at a0 over to `hook`; find the a0 that lands its tip
            # `dig` into the rock (a steeper start lifts the tip), between 8 and 80 degrees.
            def tip_y(a0): return (S + _arc([0, 0, 0], d, Y, length, a0, v['hook'])[-1])[1]
            lo, hi = 8., 80.
            for _ in range(30):
                mid = (lo + hi)/2
                if tip_y(mid) < sole - v['dig']: lo = mid
                else: hi = mid
            pts = S + _arc([0, 0, 0], d, Y, length, hi, v['hook'])
            # The talon: three tapering blocks, each a chord of the arc, rooted in the knuckle.
            n = len(pts) - 1
            for k, (f0, f1, scale) in enumerate(((0, .46, 1), (.4, .8, .66), (.74, 1, .36))):
                a, b = pts[int(f0*n)], pts[int(f1*n)]
                if k == 0: a = a - (b - a)/np.linalg.norm(b - a)*1.5
                t = (b - a)/np.linalg.norm(b - a); normal = np.cross(t, side_axis)
                normal *= np.sign(normal@Y) or 1
                m.slab(tip, a, b, thick*scale*.7, thick*scale, 'talon', up=normal, kind='talon',
                    feet='talon', tip_at=pts[-1].tolist(), talon_len=length)

DARK, SHEEN, BONE = np.array([18, 16, 17]), np.array([70, 66, 64]), np.array([206, 196, 176])

def paint_talon(points, face, tip_at, length):
    """Glossy black, a little lighter on top, and pale toward the very tip."""
    dist = np.linalg.norm(points - np.array(tip_at), axis=-1)
    tile = np.zeros(points.shape[:2] + (4,), np.uint8); tile[..., 3] = 255
    base = np.where(face == 'up', 1, 0)
    col = DARK + (SHEEN - DARK)*(.55*base + .15)
    pale = np.clip(1 - dist/max(2.5, length*.2), 0, 1)[..., None]
    tile[..., :3] = (col*(1 - pale) + BONE*pale).astype(np.uint8)
    return tile
