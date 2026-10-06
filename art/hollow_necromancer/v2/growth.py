"""The Necromancer grows into the colossus: the transformation clip after the convulsions.

On his back after the souls have poured into him:
  claws   his right hand jerks up and three jolts stretch his bony fingers into jointed claws
  drop    the hand slams down; every following convulsion is a growth spurt (four, to 2.1x),
          each one ripping the robe further: the chest seam parts, the hems tear loose
  swap    inside a burst of smoke he is the giant, on its back in the rags of his robe; two
          spasms bow it off the floor while the rags hang, slide and drop, and his hood falls
          off the bare skull
  sit     it sits bolt upright without its hands and the skull snaps round to the party
  eyes    its eyes ignite
  crouch  it lunges forward onto its claws in a spider crouch
  crawl   two hand steps carry it at the party, onto the colossus idle's crouch at ignite
  rear    it gathers low and pushes its front half up on locked arms, claws braced
  roar    it howls, skull thrown back and jaw wide, shaking, then sinks into the idle crouch
          (land), where transform.handback plants the claws exactly as the colossus idle begins

Only the robed Necromancer changes size; colossus parts are only ever shown at full size.
Every tick is posed on its own single-key copy of the clip (so floor contact and the arm
reaches are exact), then baked into the clip, blended in from the approved convulsions. The
arms are solved every tick, so a reaching arm never comes apart at the elbow.
"""
import math
from collections import defaultdict

import numpy as np

import rig
from geometry import HOOD_RAGS, MAGE_CLAWS, SHELL_CLOSED, SHELL_PANELS, SHREDS, cubes
from transform import (B, BY_NAME, IDLE, LENGTH, PELVIS, PIVOT, TOLERANCE, ZERO, Author, aligning, axis_angle, claw,
                       continuous, douglas_peucker, hashf, keyed, ramp, slerp, smooth, translation)

START = B['smoke']
# Paced so players can take each change in: a jolt, then a held beat before the next.
CLAW_JOLTS = tuple(B['claws'] + d for d in (8, 20, 32))  # then a hold on the grown claws before the slam
SPURTS = tuple(B['spurt'] + 18 * k for k in range(4))
SIZES = (1.25, 1.5, 1.8, 2.1)
GROWN = SIZES[-1]  # the robed figure's height then matches the colossus's
HEMS = {'left': SPURTS[2], 'right': SPURTS[3]}
SPASMS = (B['swap'] + 20, B['swap'] + 34)
SNAP = B['sit'] + 18  # the skull snaps round to the party
PLANTS = {'right': B['crouch'] + 10, 'left': B['crouch'] + 18}
STEPS = {'right': (B['crawl'], B['crawl'] + 12), 'left': (B['crawl'] + 12, B['crawl'] + 24)}
SWING = 12  # ticks for a claw to swing onto its next plant
END_WRIST = {'right': [20, 5.1, -43], 'left': [-20, 5.1, -43]}  # the colossus idle's plants
IDLE_ELBOW = {'right': [.83, -.21, .52], 'left': [-.75, -.4, .53]}  # its elbows: out, a little down and back


# ---------------- bones and cubes ----------------
def descendants(root):
    found = {root}
    for _ in range(14):
        found |= {n for n, b in BY_NAME.items() if b.get('parent') in found}
    return found


MAGE = descendants('robe')
GIANT = descendants('colossus')
CLAWS = {name for chain in MAGE_CLAWS.values() for name in chain}
BODY = MAGE - CLAWS - {f'mage_finger_{side}_{k}' for side in ('right', 'left') for k in range(3)}
# Cloth rags only: his bony hands and legs are inside the giant's own bones by then.
RAGS = [(name, source) for name, source in SHREDS
        if any(c['bone'] == name and c['material'] in ('cloth', 'leather') for c in cubes)] + list(HOOD_RAGS)
RAG_NAMES = {name for name, _ in RAGS}
UNUSED_SHREDS = {name for name, _ in SHREDS} - RAG_NAMES
BY_BONE = defaultdict(list)
for _entry in cubes:
    BY_BONE[_entry['bone']].append(_entry['raw'])
# Where each piece of his robe sits on the giant that outgrew it.
ON_GIANT = {'robe': 'pelvis', 'body': 'pelvis', SHELL_CLOSED: 'ribcage', 'robe_tail': 'pelvis',
            'skirt_right': 'leg_right', 'skirt_left': 'leg_left', 'skirt_hem_right': 'shin_right',
            'skirt_hem_left': 'shin_left', 'right_arm': 'arm_right', 'left_arm': 'arm_left',
            'mage_forearm_right': 'forearm_right', 'mage_forearm_left': 'forearm_left',
            'mage_shin_right': 'foot_right', 'mage_shin_left': 'foot_left',
            'hood': 'skull', 'hood_crown': 'skull', 'hood_right': 'skull', 'hood_left': 'skull'}


def cube_local(raw):
    cp = np.asarray(raw.get('pivot', ZERO)) * [-1, 1, 1]
    return translation(cp) @ rig.rotation(raw.get('rotation', ZERO)) @ translation(-cp)


def cube_corners(raw):
    o, s = np.asarray(raw['origin'], float), np.asarray(raw['size'], float)
    return [(o + s * [x, y, z]) * [-1, 1, 1] for x in (0, 1) for y in (0, 1) for z in (0, 1)]


def cube_centres(names):
    return np.array([(np.asarray(r['origin']) + np.asarray(r['size']) / 2) * [-1, 1, 1] for n in names for r in BY_BONE[n]])


# ---------------- a pose on a single-key copy ----------------
def frozen(source, tick):
    clip = {'loop': False, 'animation_length': 1, 'bones': {}}
    for name, channels in source.clip['bones'].items():
        clip['bones'][name] = {ch: {'0': source.sample(name, ch, tick, (1, 1, 1) if ch == 'scale' else ZERO).tolist()}
                               for ch in channels}
    return Author(clip)


def set_rot(a, poses):
    for name, r in poses.items():
        a.rot(name, [(0, tuple(float(v) for v in r))])


def set_scale(a, name, s):
    a.scale(name, [(0, s if isinstance(s, (int, float)) else tuple(float(v) for v in s))])


def pivot(a, name):
    return (a.world(name, 0) @ [*PIVOT[name], 1])[:3]


def turn_about(a, name, rotation, point=None):
    p = pivot(a, name) if point is None else np.asarray(point, float)
    a.place(name, {0: translation(p) @ rotation @ translation(-p) @ a.world(name, 0)})


def move(a, name, delta):
    a.place(name, {0: translation(delta) @ a.world(name, 0)})


def aim(a, name, child, direction):
    """Point a limb (its pivot to its child's pivot) along a world direction."""
    turn_about(a, name, aligning(pivot(a, child) - pivot(a, name), direction))


def rx(degrees):
    return axis_angle([1, 0, 0], degrees)


def world_vertices(a, names):
    out = []
    for name in names:
        m = a.world(name, 0)
        if np.linalg.norm(m[:3, :3]) < 1e-6:
            continue
        for raw in BY_BONE[name]:
            local = m @ cube_local(raw)
            out.extend((local @ [*v, 1])[:3] for v in cube_corners(raw))
    return np.array(out)


def seat(a, root, names, floor=.06):
    """Lift or drop a root bone so the lowest visible cube of names rests on the floor."""
    move(a, root, [0, floor - world_vertices(a, names)[:, 1].min(), 0])


def mix(a, b, f):
    return np.asarray(a, float) + (np.asarray(b, float) - np.asarray(a, float)) * f


def unit(v):
    v = np.asarray(v, float)
    return v / np.linalg.norm(v)


# ---------------- timing ----------------
def step(t, at, length=3):
    """A sudden change at tick at, overshooting a little before it settles."""
    x = t - at
    if x <= 0:
        return 0.
    return smooth(min(1, x / length)) + .08 * math.sin(math.pi * min(1, max(0, (x - length + 1) / 5)))


def size(t):
    """His height: four violent spurts, held between them."""
    g, previous = 1., 1.
    for at, s in zip(SPURTS, SIZES):
        g += (s - previous) * step(t, at, 4)
        previous = s
    return g


def pulse(t, times, rise=1, fall=5):
    """A jolt: a one-tick snap at each time, dying away."""
    out = 0.
    for at in times:
        x = t - at
        if -rise < x <= 0:
            out = max(out, 1 + x / rise)
        elif x > 0:
            out = max(out, math.exp(-x / fall))
    return out


def claw_length(t):
    """World pixels: the claws start as long as his fingers and stretch in three jolts."""
    if t < CLAW_JOLTS[0]:
        return 0.
    length, previous = 2.2, 2.2
    for at, l in zip(CLAW_JOLTS, (3.6, 5.6, 7.5)):
        length += (l - previous) * step(t, at, 4)
        previous = l
    return length * size(t) ** .85


# ---------------- the mage ----------------
def claw_chain(a, side, length, spread=30, curls=(6, 24, 30), width=1.):
    """Long jointed claws grow out of his fingers. The stubby fingers hide; three segments per
    finger chain from each knuckle, fanned across the hand and hooking toward the palm, placed in
    the world on the hand so their stretch never shears. length and width are world pixels."""
    hand = a.world('mage_hand_' + side, 0)
    g = np.linalg.norm(hand[:3, 0])
    turn = np.eye(4)
    turn[:3, :3] = hand[:3, :3] / g
    for k in range(3):
        finger = f'mage_finger_{side}_{k}'
        start = pivot(a, finger)
        set_scale(a, finger, 0)
        bend = 0
        for j, name in enumerate(MAGE_CLAWS[side, k]):
            bend += curls[j]
            r = turn @ rig.rotation((0, 0, (k - 1) * spread)) @ rig.rotation((bend + 4 * (k == 1), 0, 0))
            piece = length * (.4, .33, .27)[j] * (1.08 if k == 1 else 1)
            thick = width * (1, .85, .62)[j]  # tapering to a point
            a.place(name, {0: translation(start) @ r @ np.diag([thick, piece / 2, thick, 1]) @ translation(-PIVOT[name])})
            start = start + r[:3, :3] @ [0, -piece, 0]


def touch(a, side, floor=.15, span=40):
    """Tilt the hand at the wrist until its lowest claw tip just meets the floor."""
    names = {name for k in range(3) for name in MAGE_CLAWS[side, k]} | {'mage_hand_' + side}
    start = a.sample('mage_hand_' + side, 'rotation', 0, ZERO).copy()
    best = None
    for tilt in range(-span, span + 1, 4):
        a.rot('mage_hand_' + side, [(0, tuple(start + [tilt, 0, 0]))])
        gap = abs(world_vertices(a, names)[:, 1].min() - floor)
        if best is None or gap < best[0]:
            best = (gap, tilt)
    a.rot('mage_hand_' + side, [(0, tuple(start + [best[1], 0, 0]))])


def split_seams(a, chest, shoulders):
    """The robe rips as he grows: the chest seam parts over a black hollow."""
    set_scale(a, SHELL_CLOSED, 0)
    for name in (*SHELL_PANELS, 'shell_lining'):
        set_scale(a, name, 1)
    for side, sign in (('right', 1), ('left', -1)):
        set_rot(a, {'shell_chest_' + side: (0, sign * chest, sign * chest / 4),
                    'shell_shoulder_' + side: (-shoulders / 3, sign * shoulders, sign * shoulders)})


def mage_pose(a, t):
    """On his back: the claw close-up, then the four spurts, the robe ripping as he grows."""
    g = size(t)
    k = (g - 1) / (GROWN - 1)
    j = pulse(t, SPURTS)
    c = pulse(t, CLAW_JOLTS, fall=3)
    up = ramp(t, B['claws'], B['claws'] + 5) * (1 - ramp(t, B['drop'] - 2, B['drop'] + 3))
    shiver = lambda phase: math.sin(t * 1.9 + phase) * (.35 + .65 * max(j, c))
    set_rot(a, {'robe': (-90, 0, 0), 'robe_tail': (-2 - 6 * j, 0, 0),
                'body': (-14 * k - 18 * j - 5 * c, 3 * shiver(0), 2 * shiver(1)),
                'hood': (-24 * k - 34 * j - 8 * c, 6 * shiver(2), 3 * shiver(3))})
    for side, sign in (('right', 1), ('left', -1)):
        set_rot(a, {side + '_arm': (2 - 16 * j, sign * 6 * j, sign * (74 + 12 * k + 10 * j)),
                    'mage_forearm_' + side: (-6 - 24 * j, 0, 0), 'mage_hand_' + side: ZERO,
                    'mage_leg_' + side: (-10 - 20 * j, sign * 2, sign * (6 + 8 * k + 6 * j)),
                    'mage_shin_' + side: (18 + 30 * j, 0, 0),
                    'skirt_' + side: (-7 - 10 * j, sign * 2, sign * 8), 'skirt_hem_' + side: (12 + 15 * j, 0, 0)})
    arms = 1 + .3 * k
    set_scale(a, 'robe', g)
    set_scale(a, 'hood', (1 + .02 * k, 1 + .15 * k, 1 + .1 * k))
    for side in ('right', 'left'):
        set_scale(a, side + '_arm', arms)
        set_scale(a, 'mage_leg_' + side, 1 + .2 * k)
        # The knuckles stay near his own size; the claws do the growing.
        set_scale(a, 'mage_hand_' + side, (1.05 + .2 * (g - 1)) / (g * arms))
    if up > 0:
        # His right hand jerks up off the floor, trembling, while the claws grow out of it.
        splayed = unit(pivot(a, 'mage_forearm_right') - pivot(a, 'right_arm'))
        aim(a, 'right_arm', 'mage_forearm_right', unit(splayed * (1 - up) + unit([.25, 1, -.2]) * up))
        set_rot(a, {'mage_forearm_right': (-6 - 18 * up - 10 * c, 0, 0),
                    'mage_hand_right': (-10 * up + 9 * c * math.sin(t * 2.3), 3 * shiver(4), 0)})
    length = claw_length(t)
    if length > 0:
        for side in ('right', 'left'):
            raised = side == 'right' and up > .3
            curls = (8, 26, 32) if raised else (4, 26, 30)
            open_ = 1 - .45 * c  # each jolt splays the claws open before they hook again
            claw_chain(a, side, length * (1 if side == 'right' else .96), 30 + 6 * c,
                       tuple(v * open_ for v in curls), .9 + .2 * g)
    seat(a, 'robe', BODY)
    if length > 0:
        for side in ('right', 'left'):
            if not (side == 'right' and up > .3):
                touch(a, side)
    rip = ramp(g, 1.35, 2.05)
    if rip > 0:
        split_seams(a, 34 * rip + 6 * j, 22 * rip + 4 * j)
    frames = {name: a.world(source, 0).copy() for name, source in RAGS}
    for name in RAG_NAMES | UNUSED_SHREDS:
        set_scale(a, name, 0)
    for side, at in HEMS.items():
        if t >= at:
            # A skirt hem tears loose and swings from a corner.
            set_scale(a, 'skirt_hem_' + side, 0)
            out = unit([1. if side == 'right' else -1., 0, -.3])
            for index, (name, source) in enumerate(RAGS):
                if source == 'skirt_hem_' + side:
                    a.place(name, {0: hanging(frames[name], name, index, out, 40 * min(1, step(t, at, 5)))})
    set_scale(a, 'colossus', 0)


# ---------------- the rags ----------------
def rag_box(name, m):
    """A rag's world centre, its model centre, highest point, thinnest side and world thickness."""
    raw = BY_BONE[name][0]
    size_ = np.asarray(raw['size'], float)
    local = cube_local(raw)
    corners = [(m @ local @ [*v, 1])[:3] for v in cube_corners(raw)]
    model = (local @ [*((np.asarray(raw['origin']) + size_ / 2) * [-1, 1, 1]), 1])[:3]
    thin = m[:3, :3] @ local[:3, :3] @ np.eye(3)[int(np.argmin(size_))]
    return (np.mean(corners, axis=0), model, max(corners, key=lambda v: v[1]), unit(thin),
            np.linalg.norm(m[:3, 0]) * size_.min())


def hanging(m0, name, index, out, droop):
    """Torn loose along its lower edges, swinging down from its highest corner."""
    _, _, top, _, _ = rag_box(name, m0)
    out = np.asarray(out, float)
    axis = np.cross([0, 1, 0], out)
    if np.linalg.norm(axis) < 1e-6:
        axis = np.array([1., 0, 0])
    r = axis_angle(axis, droop) @ axis_angle([0, 1, 0], droop * .4 * (hashf(index, 53) * 2 - 1))
    return translation(top + out * 1.2 * droop / 40) @ r @ translation(-top) @ m0


def fallen(m0, name, index, spot):
    """Flat on the floor at spot, its thinnest side down, turned at random."""
    centre, _, _, thin, depth = rag_box(name, m0)
    r = axis_angle([0, 1, 0], 60 * (hashf(index, 51) * 2 - 1)) @ aligning(thin, [0, 1, 0])
    return translation([spot[0], depth / 2 + .05, spot[2]]) @ r @ translation(-centre) @ m0


def falling(m0, m1, name, f):
    """From m0 to m1 under gravity: the centre drops on a quadratic, the rag turning as it goes."""
    c0, model, _, _, _ = rag_box(name, m0)
    c1 = rag_box(name, m1)[0]
    g = np.linalg.norm(m0[:3, 0])
    r = slerp(m0, m1, smooth(f))
    c = mix(c0, c1, [f, f * f, f])
    return translation(c) @ r @ np.diag([g, g, g, 1]) @ translation(-model)


def giant_rag_frames(a):
    """His robe, grown with him, worn by the giant: each group of rags keeps its own layout at his
    grown size, centred on its giant bone, and follows that bone."""
    out = {}
    for source in {source for _, source in RAGS}:
        names = [name for name, src in RAGS if src == source]
        offset = cube_centres({ON_GIANT[source]}).mean(axis=0) - cube_centres(names).mean(axis=0) * GROWN
        for name in names:
            out[name] = a.world(ON_GIANT[source], 0) @ translation(offset) @ np.diag([GROWN, GROWN, GROWN, 1])
    return out


def rag_plan(index, name, source):
    """When each rag starts to hang loose and when it drops. The hood and anything that can't
    hang on bones go at once; the rest rip away through the arching and the sit-up."""
    swap = B['swap']
    if (name, source) in HOOD_RAGS or source in ('robe_tail', 'mage_shin_right', 'mage_shin_left'):
        return swap, swap + 1 + index % 4
    if source.startswith('skirt_hem_'):
        return swap, B['sit'] + round(10 * hashf(index, 71))
    hang = swap + 8 + round(36 * hashf(index, 72))
    return hang, min(B['crouch'] + 6, hang + 18 + round(40 * hashf(index, 73)))


def rags(a, t, state):
    """The robe ripping on the body that outgrew it: clinging with widening gaps, swinging from a
    torn corner, then dropping to the floor, where it burns away in the roar."""
    worn = giant_rag_frames(a)
    core = pivot(a, 'ribcage')
    for name in UNUSED_SHREDS:
        set_scale(a, name, 0)
    for index, (name, source) in enumerate(RAGS):
        hang, fall = rag_plan(index, name, source)
        if t < fall:
            m0 = worn[name]
            centre = rag_box(name, m0)[0]
            out = (centre - core) * [1, 0, 1]
            out = unit(out + [1e-6, 0, 0])
            if t < hang:
                gap = .6 + 2.4 * ramp(t, B['swap'], hang)
                m = translation(out * gap) @ m0
            else:
                m = hanging(m0, name, index, out, (30 + 40 * hashf(index, 66)) * min(1, step(t, hang, 4)))
            state['last'][name] = (m, out)
        else:
            if name not in state['floor']:
                m, out = state['last'][name]
                centre = rag_box(name, m)[0]
                spot = centre * [1, 0, 1] + out * (2 + 5 * hashf(index, 65))
                state['floor'][name] = (m, fallen(m, name, index, spot))
            m0, m1 = state['floor'][name]
            m = falling(m0, m1, name, min(1, (t - fall) / 9))
            burn = B['roar'] + round(12 * hashf(index, 9))
            if t >= burn:
                f = ramp(t, burn, burn + 12)
                c = rag_box(name, m1)[0]
                m = translation(c + [0, 1.5 * f, 0]) @ np.diag([1 - .95 * f] * 3 + [1]) @ translation(-c) @ m1
                if f >= 1:
                    set_scale(a, name, 0)
                    continue
        a.place(name, {0: m})


# ---------------- the giant ----------------
def place_colossus(a, pelvis, rotation):
    a.place('colossus', {0: translation(pelvis) @ rotation @ translation(-PELVIS)})


def point_matrix(aim_, palm):
    # Local +Z is the palm side of this mirrored Gecko model.
    y = -unit(aim_)
    z = np.asarray(palm, float) - np.dot(palm, y) * y
    z = unit(z)
    m = np.eye(4)
    m[:3, :3] = np.column_stack([np.cross(y, z), y, z])
    return m


def posed_hand(a, side, wrist, aim_, palm, turn=None):
    """The hand at wrist, fingers along aim_ and palm toward palm, or turned exactly by turn."""
    turn = point_matrix(aim_, palm) if turn is None else turn
    a.place('hand_' + side, {0: translation(wrist) @ turn @ translation(-PIVOT['hand_' + side])})


def reach(a, side, goal, pole):
    """A two-bone reach for one held pose: the wrist on goal (or as near as the arm reaches), the
    elbow toward pole."""
    upper, fore = 'arm_' + side, 'forearm_' + side
    ps, pe, pw = PIVOT[upper], PIVOT[fore], PIVOT['hand_' + side]
    l1, l2 = np.linalg.norm(pe - ps), np.linalg.norm(pw - pe)
    u0 = unit(pe - ps)
    f0 = unit(np.array([0, 0, -1.]) - np.dot([0, 0, -1.], u0) * u0)
    h0 = np.cross(u0, f0)
    v0 = unit(pw - pe)
    k0 = unit(h0 - np.dot(h0, v0) * v0)
    upper0, fore0 = np.column_stack([u0, f0, h0]), np.column_stack([v0, np.cross(k0, v0), k0])
    shoulder = pivot(a, upper)
    d = unit(np.asarray(goal, float) - shoulder)
    dist = min(np.linalg.norm(np.asarray(goal, float) - shoulder), (l1 + l2) * .999)
    along = (l1 ** 2 - l2 ** 2 + dist ** 2) / (2 * dist)
    off = math.sqrt(max(0, l1 ** 2 - along ** 2))
    n = unit(np.asarray(pole, float) - np.dot(pole, d) * d)
    elbow = shoulder + d * along + n * off
    u = unit(elbow - shoulder)
    f = -unit(n - np.dot(n, u) * u)
    h = np.cross(u, f)
    ru = np.eye(4)
    ru[:3, :3] = np.column_stack([u, f, h]) @ upper0.T
    v = unit(shoulder + d * dist - elbow)
    k = unit(h - np.dot(h, v) * v)
    rf = np.eye(4)
    rf[:3, :3] = np.column_stack([v, np.cross(k, v), k]) @ fore0.T
    a.place(upper, {0: translation(shoulder) @ ru @ translation(-ps)})
    a.place(fore, {0: translation(elbow) @ rf @ translation(-pe)})


def brace(a, side, wrist, pole, aim_, bend, grounded, palm=(0, -1, 0), turn=None):
    """A claw on its target (palm down unless told otherwise); on the floor its fingertips just
    touch it."""
    wrist = np.array(wrist, float)
    claw(a, side, [(0, bend)])
    posed_hand(a, side, wrist, aim_, palm, turn)
    if grounded:
        wrist[1] += .3 - world_vertices(a, descendants('hand_' + side))[:, 1].min()
    reach(a, side, wrist, pole)
    # On the forearm's actual end: the first placement left the hand offset toward the target,
    # which floats it off the arm wherever the reach falls short.
    end = (a.world('forearm_' + side, 0) @ [*PIVOT['hand_' + side], 1])[:3]
    posed_hand(a, side, end, aim_, palm, turn)


def arc(a_, b_, f, lift):
    """A hand's path between two plants, lifted off the floor in between."""
    return mix(a_, b_, smooth(f)) + [0, lift * math.sin(math.pi * min(1, max(0, f))), 0]


def roar_pose(a, t, state):
    """From the crawl's crouch it gathers, then howls like an animal: claws braced wide on locked
    arms, its front half pushed up, chest out and skull thrown back at the ceiling, jaw wide and the
    whole body shaking; then it sinks back into the colossus idle's crouch. The claws never leave
    the floor, and hold the crawl's grip until it sinks: only then do they turn onto the idle's."""
    ignite, rear, roar, land = B['ignite'], B['rear'], B['roar'], B['land']
    shaking = ramp(t, roar, roar + 2) * (1 - ramp(t, land - 18, land - 16))
    beat = shaking * (1 if (t // 2) % 2 else -1)
    # Like a howling wolf it sits back on its haunches over its braced forelegs: the hips drop and
    # come forward under the chest, which rises nearly upright on the locked arms.
    pelvis = keyed(t, [(ignite, (0, 20.8, 3)), (ignite + 8, (0, 19, 5)), (rear + 10, (0, 16.5, -8)), (roar + 6, (0, 16, -9)),
                       (land - 16, (0, 17, -6)), (land, IDLE['pos'] + PELVIS)])
    place_colossus(a, pelvis, np.eye(4))
    set_rot(a, {
        'spine': keyed(t, [(ignite, IDLE['spine']), (ignite + 8, (54, 0, 0)), (rear + 10, (-2, 0, 0)), (roar + 4, (-6, 0, 0)),
                           (land - 16, (0, 0, 0)), (land, IDLE['spine'])]) + np.array([0, 2, 1]) * beat,
        'skull': keyed(t, [(ignite, IDLE['skull']), (ignite + 8, (-70, 0, 0)), (rear + 8, (-86, 0, 0)), (roar + 3, (-100, 0, 0)),
                           (land - 16, (-94, 0, 0)), (land, IDLE['skull'])]) + np.array([3, 2, 0]) * beat,
        'jaw': keyed(t, [(ignite, IDLE['jaw']), (rear + 6, (10, 0, 0)), (roar + 3, (66, 0, 0)), (land - 16, (50, 0, 0)),
                         (land - 3, (10, 0, 0)), (land, IDLE['jaw'])]) + np.array([4, 0, 0]) * beat})
    for side, sign in (('right', 1), ('left', -1)):
        leg = keyed(t, [(ignite, IDLE['leg']), (ignite + 8, (-40, 0, 0)), (rear + 10, (-64, 0, 0)), (land - 16, (-60, 0, 0)), (land, IDLE['leg'])])
        shin = keyed(t, [(ignite, IDLE['shin']), (ignite + 8, (92, 0, 0)), (rear + 10, (120, 0, 0)), (land - 16, (114, 0, 0)), (land, IDLE['shin'])])
        set_rot(a, {'leg_' + side: leg, 'shin_' + side: shin})
    # Hind feet stay on the floor as it pushes up.
    feet = descendants('foot_right') | descendants('foot_left')
    rising = ramp(t, ignite, ignite + 4) * (1 - ramp(t, land - 4, land))
    move(a, 'colossus', [0, (.1 - world_vertices(a, feet)[:, 1].min()) * rising, 0])
    settle = ramp(t, land - 12, land)
    landing = 'landed' not in state
    for side, sign in (('right', 1), ('left', -1)):
        # Claws braced where the crawl put them, gripping harder through the howl.
        pole = mix([sign * .6, .5, -.8], IDLE_ELBOW[side], settle)
        planted = point_matrix(unit([sign * .35, .2, -1]), [0, -1, 0])
        bend = float(mix(64 + 8 * shaking, 12, settle))
        wrist = np.array(END_WRIST[side], float)
        for _ in range(3):
            brace(a, side, wrist, pole, None, bend, False, turn=planted)
            fore = a.world('forearm_' + side, 0)
            if landing:
                # On the idle crouch itself: the tilt transform.handback plants (rig.plant keeps only
                # the palm's pitch against the forearm, no twist), so nothing turns at the handover.
                pitch = (-65 - a.sample('spine', 'rotation', 0, ZERO)[0] - BY_NAME['spine']['rotation'][0]
                         - a.sample('arm_' + side, 'rotation', 0, ZERO)[0] - BY_NAME['arm_' + side]['rotation'][0]
                         - a.sample('forearm_' + side, 'rotation', 0, ZERO)[0] - BY_NAME['forearm_' + side]['rotation'][0])
                turn = np.eye(4)
                turn[:3, :3] = fore[:3, :3] / np.linalg.norm(fore[:3, 0]) @ rig.rotation((pitch, 0, 0))[:3, :3]
            else:
                # The crawl's grip, held fixed on the floor, then turned onto the idle's as it sinks.
                turn = slerp(planted, state['landed'][side], ramp(t, land - 14, land - 1))
            end = (fore @ [*PIVOT['hand_' + side], 1])[:3]
            posed_hand(a, side, end, None, None, turn)
            # Re-seat on the claws' tilt: only the tips rest on the stone.
            gap = .3 - world_vertices(a, descendants('hand_' + side))[:, 1].min()
            if abs(gap) < .15:
                break
            wrist[1] += gap
        if landing:
            state.setdefault('landing', {})[side] = turn
    if landing:
        state['landed'] = state.pop('landing')


def giant_pose(a, t, state):
    if t >= B['ignite']:
        return roar_pose(a, t, state)
    swap, flat, sit, crouch, crawl = B['swap'], B['flat'], B['sit'], B['crouch'], B['crawl']
    arch = ramp(t, swap + 1, swap + 12) * (1 - ramp(t, flat - 6, flat + 6))
    arch *= 1 + .35 * pulse(t, SPASMS)
    up = min(1.04, ramp(t, sit, sit + 14) + .04 * math.sin(math.pi * min(1, max(0, (t - sit - 12) / 8))))
    lean = ramp(t, crouch, crouch + 20)
    go = ramp(t, crawl, B['ignite'] - 2)
    lie = state['lie']
    seated = lie + [0, 7, 0]
    crouched = lie * [1, 0, 1] + [0, 24, -8]
    pelvis = mix(seated + [0, 18 * arch, 0], crouched, lean)
    pelvis = mix(pelvis, [0, 20.8, 3], go)
    place_colossus(a, pelvis, slerp(rx(90), np.eye(4), lean) if lean > 0 else rx(90))
    spine = mix([-30 * arch + 66 * up, 0, 0], [52, 0, 0], lean)
    skull_lie = np.array([-60 * arch, 0, 8 * arch])
    skull_sit = keyed(t, [(sit, ZERO), (sit + 14, (14, 0, 0)), (SNAP, (14, 0, 0)), (SNAP + 4, (-46, 18, -12)), (SNAP + 8, (-40, 14, -10))])
    skull = mix(mix(skull_lie, skull_sit, ramp(t, flat - 2, sit + 2)), [-74, 0, 8], lean)
    jaw = mix(mix([32 * arch, 0, 0], keyed(t, [(sit, (10, 0, 0)), (SNAP + 4, (28, 0, 0))]), ramp(t, flat - 2, sit + 2)), [34, 0, 0], lean)
    set_rot(a, {'spine': mix(spine, IDLE['spine'], go), 'skull': mix(skull, IDLE['skull'], go), 'jaw': mix(jaw, IDLE['jaw'], go)})
    for side, sign in (('right', 1), ('left', -1)):
        leg = mix(mix([-4 + 40 * arch, 0, sign * 6], [-46, 0, sign * 14], lean), IDLE['leg'], go)
        shin = mix(mix([6 + 10 * arch, 0, 0], [70, 0, 0], lean), IDLE['shin'], go)
        set_rot(a, {'leg_' + side: leg, 'shin_' + side: shin})
    if 'plants' not in state:
        # Claws gripping the floor beside its shoulders as it arches.
        state['plants'] = {side: pivot(a, 'arm_' + side) * [1, 0, 1] + [sign * 26, 4, -4]
                           for side, sign in (('right', 1), ('left', -1))}
    for side, sign in (('right', 1), ('left', -1)):
        lying = state['plants'][side]
        beside = seated * [1, 0, 1] + [sign * 24, 4, 4]
        front = crouched * [1, 0, 1] + [sign * 24, 4, -34 - 4 * sign]
        end = np.array(END_WRIST[side])
        delay = 0 if side == 'right' else 3
        lift = ramp(t, flat + delay, sit + 4 + delay)
        wrist = arc(lying, beside, lift, 6)
        plant = PLANTS[side]
        if t >= plant - SWING:
            wrist = arc(beside, front, (t - plant + SWING) / SWING, 16)
        s0, s1 = STEPS[side]
        if t >= s0:
            wrist = arc(front, end, (t - s0) / (s1 - s0), 9)
        moving = (0 < lift < 1) or (plant - SWING < t < plant) or (s0 < t < s1)
        pole = mix(mix(mix([sign * .5, 1, .2], [sign * .8, .5, .4], lift), [sign * .7, .7, -.1], lean), [sign * .6, .5, -.8], go)
        aim_ = mix(mix(mix([sign, 0, -.2], [.3 * sign, 0, -1], lift), [sign * .35, .2, -1], lean), [sign * .35, .2, -1], go)
        brace(a, side, wrist, pole, unit(aim_), 20 if moving else 64, not moving)
    if t >= swap and 'lie_skull' not in state:
        state['lie_skull'] = pivot(a, 'skull')


# ---------------- baking ----------------
def animate(a):
    """Pose every tick from the end of the convulsions to the end of the clip, then bake it into
    the clip a, blended in from the approved convulsions."""
    source = Author(a.clip)
    probe = frozen(source, B['swap'] - 1)
    mage_pose(probe, B['swap'] - 1)
    state = {'lie': pivot(probe, 'body') * [1, 0, 1], 'last': {}, 'floor': {}}
    landing = frozen(source, B['land'])
    set_scale(landing, 'colossus', 1)
    roar_pose(landing, B['land'], state)  # the claws' tilt on the idle crouch, which the howl sinks onto
    driven = sorted(MAGE | GIANT | RAG_NAMES | UNUSED_SHREDS)
    rows = {name: {ch: {} for ch in ('rotation', 'position', 'scale')} for name in driven}
    for t in range(START, LENGTH + 1):
        p = frozen(source, t)
        if t < B['swap']:
            mage_pose(p, t)
            set_scale(p, 'colossus', 1)
            giant_pose(p, B['swap'], state)  # unseen, already in its first pose: no turn at the swap
            set_scale(p, 'colossus', 0)
        else:
            set_scale(p, 'robe', 0)
            set_scale(p, 'colossus', 1)
            giant_pose(p, t, state)
            rags(p, t, state)
        for name in driven:
            for ch in rows[name]:
                rows[name][ch][t] = p.sample(name, ch, 0, (1, 1, 1) if ch == 'scale' else ZERO).copy()
    for name in driven:
        until = LENGTH
        for ch, values in rows[name].items():
            default = (1, 1, 1) if ch == 'scale' else ZERO
            ticks = [t for t in sorted(values) if t <= until]
            base = {t: source.sample(name, ch, t, default) for t in ticks}
            weight = {t: ramp(t, START, START + 8) if name in MAGE else 1. for t in ticks}
            if ch == 'rotation':
                # One Euler branch, so no seam spins: forward from the approved pose, and for the
                # giant backward from plain angles at the end, where the colossus idle takes over.
                order = ticks[::-1] if name in GIANT else ticks
                anchor = (continuous(np.zeros(3), values[ticks[-1]]) if name in GIANT
                          else source.sample(name, ch, START - 1, default))
                previous = anchor
                for t in order:
                    values[t] = continuous(previous, values[t])
                    previous = values[t]
                near = {t: continuous(values[t], base[t]) for t in ticks}  # the old key on our branch
                blended = {t: near[t] + (values[t] - near[t]) * weight[t] for t in ticks}
                previous = anchor
                for t in order:
                    blended[t] = continuous(previous, blended[t])
                    previous = blended[t]
            else:
                blended = {t: base[t] + (values[t] - base[t]) * weight[t] for t in ticks}
            write(a, name, ch, blended, until, name in GIANT)
    a._changed()


def write(a, name, channel, values, until, hold=False):
    """Replace the channel's keys from START to until with these per-tick values, simplified, and
    with rotation keys never more than 12 degrees apart (the clip checks for snapped joints). hold
    also replaces every earlier key with the first value: the giant waits, hidden, in its first pose."""
    old = a.clip['bones'].get(name, {}).get(channel, {})
    keep = {k: v for k, v in old.items() if (float(k) * 20 < START - 1e-6 and not hold) or float(k) * 20 > until + 1e-6}
    if hold:
        keep['0.0000'] = [round(float(v), 4) for v in values[min(values)]]
    ticks = sorted(values)
    series = [values[t] for t in ticks]
    kept = douglas_peucker(ticks, series, TOLERANCE[channel])
    if channel == 'rotation':
        dense = [kept[0]]
        for i, j in zip(kept, kept[1:]):
            gap = np.abs(np.asarray(series[j]) - series[i]).max()
            pieces = int(math.ceil(gap / 12))
            dense += [i + round((j - i) * n / pieces) for n in range(1, pieces)] + [j]
        kept = sorted(set(dense))
    keys = dict(keep)
    keys.update({f'{ticks[i] / 20:.4f}': [round(float(v), 4) for v in series[i]] for i in kept})
    a.clip['bones'].setdefault(name, {})[channel] = dict(sorted(keys.items(), key=lambda kv: float(kv[0])))
