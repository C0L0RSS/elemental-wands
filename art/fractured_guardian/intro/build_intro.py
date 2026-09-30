#!/usr/bin/env python3
"""The effigy wakes: the Guardian's intro performance, from kneeling statue to fist slam.

It kneels as still as stone, fists planted and head bowed, until the heart strikes its core. The
blow jolts its chest open; it shudders under lightning, lifts its head, pushes up off its fists,
stands, spreads its arms and slams both fists together in front of its chest to open the fight.
The beats are in ticks and match GuardianIntro.java, whose contract test reads intro-points.json.

Run from the repository root:
    python3 art/fractured_guardian/intro/build_intro.py           # bake the clip and review sheet
    python3 tools/prepare_guardian_assets.py                      # install it with the other clips
"""
import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from rig_tools import rig, fingers, lowest, point, reach, leg, pos, rot, sheet, transforms  # noqa: E402

# Beats, in ticks from the scene's first frame.
LENGTH, IMPACT, WAKE, HEAD, RISE, STAND, WIND, CLAP, RELEASE = 322, 108, 124, 140, 152, 186, 196, 216, 236
RECOVERY_END = 260  # Preserve the performance; the extra scene time holds the recovered pose.
RATE = 40  # samples per second before key reduction


def smooth(s):
    s = min(1, max(0, s))
    return s*s*(3 - 2*s)


def ramp(t, a, b): return smooth((t - a)/(b - a))


def mix(a, b, s): return np.array(a, dtype=float)*(1 - s) + np.array(b, dtype=float)*s


# The dormant statue: front (left) foot planted, right knee on the floor, fists on the ground.
KNEEL_PELVIS, KNEEL_FRONT, KNEEL_REAR, KNEEL_TOE = [0, -8, 2], (-14, 5), (17, 13), 30
KNEEL_TORSO, KNEEL_HEAD = 22, 32
FIST_FLOOR = (30, -24)  # x (mirrored per side) and z of each planted fist
STANCE = (4, 5)  # both ankles when standing, as the idle clip has them
SPREAD, CLAP_AT = (50, 54, -4), (6.5, 47, -33)
# Rising, in ticks: pelvis offset, front (left) ankle, rear (right) ankle as (z, y), rear toe tuck.
LEG_KEYS = [(RISE, KNEEL_PELVIS, KNEEL_FRONT, KNEEL_REAR, KNEEL_TOE),
            (RISE + 16, [0, -4, -6], KNEEL_FRONT, (10, 5), 0),
            (RISE + 26, [0, -4, -4], KNEEL_FRONT, STANCE, 0),
            (STAND, [0, 0, 0], STANCE, STANCE, 0)]


def keys(t, table):
    """Eases between rows of (tick, values...)."""
    if t <= table[0][0]: return [np.array(v, dtype=float) for v in table[0][1:]]
    for a, b in zip(table, table[1:]):
        if t <= b[0]:
            s = smooth((t - a[0])/(b[0] - a[0]))
            return [mix(x, y, s) for x, y in zip(a[1:], b[1:])]
    return [np.array(v, dtype=float) for v in table[-1][1:]]


def planted(p, side, x, z, lift=0.0):
    """Closes the hand and rests its knuckles on the floor at (x, z), raised by lift."""
    y = 8.0
    for _ in range(5):
        reach(p, side, [x, y, z], [(-1 if side == 'right' else 1), .3, .6])
        low = min(lowest(p, side + '_finger'), lowest(p, side + '_hand'), lowest(p, side + '_thumb'))
        y += .4 - low
    if lift:
        reach(p, side, [x, y + lift, z], [(-1 if side == 'right' else 1), .3, .6])


def hanging(p, side, s):
    """Arms from the planted fists (s=0) to hanging loose at the sides (s=1)."""
    sign = -1 if side == 'right' else 1
    if s >= 1:
        rot(p, side + '_upper_arm', [0, 0, 0]); rot(p, side + '_forearm', [0, 0, 0]); return
    planted(p, side, sign*FIST_FLOOR[0], FIST_FLOOR[1], lift=0)
    bent = {b: p[side + b]['rotation'].copy() for b in ['_upper_arm', '_forearm']}
    for b in bent: rot(p, side + b, mix(bent[b], [0, 0, 0], s))


def slam_fingers(p, side, grip, closure):
    """Intro-only fist: curl toward the palm and lay the thumb across the knuckles.

    The shared v4 helper uses negative X, which swings these downward-pointing
    finger bones away from the palm. Blend from that existing standing hand into
    a positive-X curl before the windup, without changing the dormant statue.
    """
    fingers(p, side, grip, 0, 0)
    sign = -1 if side == 'right' else 1
    for i in range(1, 4):
        for bone, target in ((f'{side}_finger_{i}', [96*(.76 + .06*i), 0, 0]),
                             (f'{side}_finger_{i}_tip', [72, 0, 0])):
            rot(p, bone, mix(p[bone]['rotation'], target, closure))
    for bone, target in ((side + '_thumb', [0, 0, -sign*90]),
                         (side + '_thumb_tip', [0, 0, -sign*50])):
        rot(p, bone, mix(p[bone]['rotation'], target, closure))


def pose(t):
    """The whole performance at tick t (fractional)."""
    t = min(t, RECOVERY_END)
    p = {}
    impact = ramp(t, IMPACT, IMPACT + 3)*(1 - ramp(t, IMPACT + 3, IMPACT + 16))
    tremble = ramp(t, IMPACT, WAKE)*(1 - ramp(t, RISE + 10, STAND))
    head_up = ramp(t, HEAD, RISE + 4)
    shake = lambda f, phase=0: math.sin(t*f + phase)*(.5 + .5*math.sin(t*.37 + phase))

    # Legs: kneel, lunge up and forward off the rear knee, bring the rear foot through, then step
    # the front foot back into the stance.
    wind = ramp(t, WIND, CLAP - 4)
    clap = ramp(t, CLAP - 3, CLAP)
    after = ramp(t, CLAP, CLAP + 6)*(1 - ramp(t, RELEASE, RECOVERY_END - 4))
    crouch = 1.5*wind + 3*after
    pelvis, front, rear, toe = keys(t, LEG_KEYS)
    rear_step, front_step = ramp(t, RISE + 16, RISE + 26), ramp(t, RISE + 26, STAND)
    rear = rear + [0, 5*math.sin(math.pi*rear_step)]
    front = front + [0, 4*math.sin(math.pi*front_step)]
    pos(p, 'pelvis', pelvis + np.array([0, -crouch, .5*crouch]))
    leg(p, 'left', front, 0)
    leg(p, 'right', rear, float(toe))

    # Torso and head: bowed statue, jolted open by the heart, lifting its head, standing, arching back
    # to wind up and driving forward into the slam.
    lean = -6*ramp(t, STAND - 4, WIND) - 6*wind  # standing tall, then arching back to wind up
    lean = lean*(1 - clap) + 10*clap  # driving forward into the slam
    lean *= 1 - ramp(t, RELEASE, RECOVERY_END - 6)
    torso = KNEEL_TORSO*(1 - ramp(t, HEAD, STAND)) - 9*impact + lean
    head = KNEEL_HEAD*(1 - head_up) - 14*impact - 6*head_up*(1 - ramp(t, STAND, WIND)) - 4*wind*(1 - clap)
    head -= 8*clap*(1 - ramp(t, RELEASE, RECOVERY_END - 6))  # eyes stay on the players over the fists
    rot(p, 'torso', [torso + 1.4*tremble*shake(2.9), 1.2*tremble*shake(3.7, 1), .9*tremble*shake(4.3, 2)])
    rot(p, 'head', [head + 1.2*tremble*shake(3.3, 3), 3*tremble*shake(1.9, 4), 0])
    rot(p, 'jaw', [10*impact + 14*head_up*(1 - ramp(t, RISE, RISE + 10)) + 18*after, 0, 0])

    # The heart seats: the chest plates flare, the core swells and settles, then breathes.
    for sign in (-1, 1):
        flare = impact + .35*tremble*(.5 + .5*math.sin(t*.9))
        pos(p, f'chest_plate_{sign}', [sign*2.6*flare, .4*flare, -1.4*flare])
        rot(p, f'chest_plate_{sign}', [0, -sign*16*flare, 0])
    swell = 1 + .5*impact + .12*tremble*(.5 + .5*math.sin(t*1.3)) + .25*after
    p['core'] = {'position': np.array([0, 0, -1.2*impact]), 'scale': np.array([swell, swell, swell])}

    # Arms: planted fists, pushed off and hung loose, spread wide, slammed together, released.
    for side, sign in (('right', -1), ('left', 1)):
        grip = mix(72, 38, impact)
        if t < RISE + 4:
            fingers(p, side, grip + 12*tremble*shake(3.1, sign), 2*tremble, 0)
            rot(p, side + '_hand', [0, 0, 0])
            planted(p, side, sign*FIST_FLOOR[0], FIST_FLOOR[1], lift=4*impact)
            if tremble: rot(p, side + '_upper_arm', p[side + '_upper_arm']['rotation'] + [1.5*tremble*shake(2.3, sign), 0, 0])
        elif t < STAND:
            fingers(p, side, 72 - 30*ramp(t, RISE + 4, STAND), 2, 0)
            hanging(p, side, ramp(t, RISE + 4, STAND - 4))
        else:
            closure = ramp(t, STAND, WIND)
            slam_fingers(p, side, mix(42, 96, closure), closure)
            spread = ramp(t, STAND, WIND + 10)
            hand = mix([0, 0, 0], [0, sign*-30, 0], clap)
            rot(p, side + '_hand', hand)
            if t < RELEASE:
                start = point_at_rest(side)
                target = mix(start, [sign*SPREAD[0], SPREAD[1], SPREAD[2]], spread)
                target = mix(target, [sign*CLAP_AT[0], CLAP_AT[1], CLAP_AT[2]], clap)
                # The fists press together and shiver with the impact.
                if after: target = np.array(target) + [sign*-.4*after*math.sin(t*7), .5*after*math.sin(t*9), 0]
                pole = mix([sign*.3, -1, 1], [sign*1, -.6, .3], clap)
                reach(p, side, target, pole)
            else:
                release = ramp(t, RELEASE, RECOVERY_END - 4)
                reach(p, side, [sign*CLAP_AT[0], CLAP_AT[1], CLAP_AT[2]], [sign*1, -.6, .3])
                bent = {b: p[side + b]['rotation'].copy() for b in ['_upper_arm', '_forearm']}
                for b in bent: rot(p, side + b, mix(bent[b], [0, 0, 0], release))
                rot(p, side + '_hand', mix(hand, [0, 0, 0], release))
                slam_fingers(p, side, mix(96, 5, release), 1 - release)
        rot(p, side + '_shoulder', [0, 0, -sign*3*impact])
    # Long knuckles never drag through the floor while the body is low: shrug the shoulder instead,
    # as the other clips do. Planted fists already rest on it.
    for side in ('right', 'left'):
        if t < RISE + 4: continue
        mats = transforms(p)
        low = min(float((np.array(c['vertices'])@mats[c['bone']][:3, :3].T + mats[c['bone']][:3, 3])[:, 1].min())
                  for c in rig['mesh'] if c['bone'].startswith(side + '_') and not any(k in c['bone'] for k in ('thigh', 'shin', 'foot')))
        if low < .3:
            shoulder = p.setdefault(side + '_shoulder', {})
            shoulder['position'] = shoulder.get('position', np.zeros(3)) + [0, (.3 - low)/mats['torso'][1, 1], 0]
    return p


_rest = {}
def point_at_rest(side):
    """Where the closed fist hangs when the arms are loose at the sides."""
    if side not in _rest:
        q = {}; fingers(q, side, 42, 0, 0); pos(q, 'pelvis', [0, 0, 0]); rig['legs'](q, 0, False)
        _rest[side] = point(q, side + '_hand', [(-1 if side == 'right' else 1)*34, 8, -14])
    return _rest[side]


def build():
    clip = {'loop': False, 'animation_length': LENGTH/20, 'bones': {}}
    worst = 99
    for i in range(round(LENGTH/20*RATE) + 1):
        seconds = round(i/RATE, 4); p = pose(seconds*20)
        worst = min(worst, lowest(p))
        for bone, channels in p.items():
            for kind, value in channels.items():
                clip['bones'].setdefault(bone, {}).setdefault(kind, {})[str(seconds)] = [round(float(v), 5) for v in value]
    for bone, channels in clip['bones'].items():
        for kind, keys in list(channels.items()):
            precise = kind != 'rotation' or bone in ('head', 'torso') or any(s in bone for s in ('thigh', 'shin', 'foot'))
            channels[kind] = rig['reduce_keys'](keys, .0015 if precise else .012)
    return clip, worst


def measure():
    """Points the scene aims at, in blocks in the Guardian's frame (+Z in front of it, +Y up)."""
    def blocks(v): return [round(float(-v[0]/16), 4), round(float(v[1]/16), 4), round(float(-v[2]/16), 4)]
    kneel = pose(0)
    core = point(kneel, 'core', [0, 59, -6])
    fists = (point(pose(CLAP + 1), 'left_hand', [34, 8, -14]) + point(pose(CLAP + 1), 'right_hand', [-34, 8, -14]))/2
    head_kneel = point(kneel, 'head', [0, 70, -12])
    head_stand = point(pose(WIND), 'head', [0, 70, -12])
    return {'beats': {'length': LENGTH, 'impact': IMPACT, 'wake': WAKE, 'head': HEAD, 'rise': RISE, 'stand': STAND,
                      'wind': WIND, 'clap': CLAP, 'release': RELEASE},
            'core_kneeling': blocks(core), 'fists_at_clap': blocks(fists),
            'head_kneeling': blocks(head_kneel), 'head_standing': blocks(head_stand)}


if __name__ == '__main__':
    clip, worst = build()
    assert worst > -1.0, f'the performance sinks {worst:.2f} px into the floor'
    out = {'format_version': '1.8.0', 'animations': {'animation.fractured_guardian.intro': clip}}
    (HERE/'intro.animation.json').write_text(json.dumps(out, indent=2) + '\n')
    points = measure()
    (HERE/'intro-points.json').write_text(json.dumps(points, indent=2) + '\n')
    frames = [(f'{label} / tick {t}', pose(t)) for label, t in
              [('dormant', 0), ('heart strikes', IMPACT + 3), ('lightning', WAKE + 10), ('head lifts', RISE),
               ('pushing up', RISE + 16), ('standing', STAND), ('wound up', CLAP - 4), ('fists slam', CLAP + 1), ('released', LENGTH)]]
    sheet(frames, HERE/'intro-review.png', views=((-35, 8), (0, 2), (90, 0)), size=300, centre=36, span=125)
    print(json.dumps(points, indent=2))
    print(f'Intro clip baked: {LENGTH/20:.1f} s, lowest vertex {worst:.2f} px.')
