#!/usr/bin/env python3
"""The Necromancer's transformation cinematic: the camera track and the browser animatic.

The boss's clip is authored in ../v2/transform.py (built into the model by ../v2/build_art.py);
this script flies the camera through the same beats, sampling the clip's bones so every shot
frames them. It writes:
  transform-track.json   per tick: the camera, the flash, the fade from black, the crypt's light,
                         the braziers, whether the hood is hidden (his POV) and the souls' light
                         (where and how bright); the beats, cuts and sound cues
and the browser animatic in .local-previews/necromancer-transform/ (launch entry
necromancer-transform-preview), which plays the workshop candidate through that track.

    python3 art/hollow_necromancer/v2/build_art.py                  # the model and clip (workshop candidate)
    python3 art/hollow_necromancer/transform/build_scene.py         # the track and the animatic
    python3 art/hollow_necromancer/transform/build_scene.py --install  # also the runtime track
    python3 art/hollow_necromancer/transform/build_scene.py --check    # fail if an output drifted

The track is in blocks in the scene's frame, as NecromancerTransformScene.place reads it: the
landing spot at the origin, +Z straight ahead of the Necromancer (toward the party), +X on his
left. The rig's frame (GeckoLib's mirrored X, pixels, front -Z) turns into it as
(x, y, z) -> (-x, y, -z) / 16.
"""
import json
import hashlib
import math
import shutil
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
sys.path.insert(0, str(HERE.parent / 'v2'))
from transform import BEATS, CUTS, Author, soul_plan, transform_clip  # noqa: E402
from geometry import MAGE_CLAWS, STAFF_BREAK, TRANSFORM_ONLY  # noqa: E402
from transform import BY_NAME  # noqa: E402
import growth  # noqa: E402

B = BEATS
LENGTH = B['length']
TRACK = HERE / 'transform-track.json'
RUNTIME = REPO / 'src/main/resources/assets/elementalwands/cinematics/necromancer_transform.json'
PREVIEW = REPO / '.local-previews/necromancer-transform'
CANDIDATE = HERE.parent / 'workshop/candidate'


def smooth(s):
    s = min(1, max(0, s))
    return s * s * (3 - 2 * s)


def ramp(t, a, b):
    return smooth((t - a) / (b - a))


def mix(a, b, s):
    return np.array(a, dtype=float) * (1 - s) + np.array(b, dtype=float) * s


def frame(p):
    """A rig-frame point (pixels) in the scene's frame (blocks)."""
    return np.array([-p[0], p[1], -p[2]]) / 16


def look(eye, target):
    """Minecraft yaw and pitch from eye toward target (yaw 0 looks along +Z; +pitch looks down)."""
    d = np.array(target, dtype=float) - eye
    yaw = math.degrees(math.atan2(-d[0], d[2]))
    pitch = math.degrees(-math.atan2(d[1], math.hypot(d[0], d[2])))
    return yaw, pitch


def jolt(t, at, size, decay=5):
    """A camera knock at tick `at`, dying away."""
    if t < at:
        return np.zeros(3)
    k = t - at
    e = math.exp(-k / decay) * size
    return np.array([.35 * e * math.sin(k * 1.9), -e * math.cos(k * 1.3), .25 * e * math.sin(k * 1.1)])


class Scene:
    def __init__(self):
        clips = {}
        transform_clip(clips)
        self.clip = clips['transform']
        self.a = Author(self.clip)
        self._smooth = {}

    def p(self, bone, t, point):
        return frame(self.a.point(bone, min(LENGTH, max(0, round(t))), point))

    def face(self, t):
        return self.p('mage_face', t, [0, 31, -1.2])

    def cracks(self, t):
        return (self.p('staff_lower', t, [-5, STAFF_BREAK, 1]) + self.p('staff_upper', t, [-5, STAFF_BREAK, 1])) / 2

    def skull(self, t):
        return self.p('skull', t, [0, 67, -15])

    def at(self, bone, t):
        """A bone's pivot in the scene's frame."""
        return self.p(bone, t, BY_NAME[bone]['pivot'])

    def claws(self, t):
        """The middle of his right hand's claws."""
        return np.mean([self.at(name, t) for k in range(3) for name in MAGE_CLAWS['right', k]], axis=0)

    def facing(self, t):
        """Where his hood looks, as a direction in the scene's frame."""
        m = self.a.world('hood', min(LENGTH, max(0, round(t))))
        d = m[:3, :3] @ [0, 0, -1]
        d = np.array([-d[0], d[1], -d[2]])
        return d / np.linalg.norm(d)

    def gaze(self, t):
        """His eyes in the POV: on the fallen head, on the halves rising to his face, on the
        cracks as they shake, then darting from one whirling soul to the next."""
        head = self.p('staff_head', t, [-5, 24, 1])
        if t < B['grasp'] + 6:
            return mix(self.p('staff_lower', t, [-5, 14, 1]), head, ramp(t, B['pov'], B['pov'] + 8))
        held = mix(head, self.cracks(t), ramp(t, B['grasp'] + 6, B['lifted']))
        if t < B['burst'] + 6:
            return held
        # Follow the small souls shooting up, rather than chasing individual nearby ghosts.
        launch = self.cracks(B['burst'])
        rise = ramp(t, B['burst'] + 18, B['scared'] - 2)
        return mix(self.cracks(t) + [0, .3, 0], launch + [0, 4.8, 1.1], rise)

    def follow(self, key, t, fn, lag=.25):
        """fn(t) eased so a whipping target does not jerk the camera (per-shot memory)."""
        value = fn(t)
        prev = self._smooth.get(key)
        if prev is not None and prev[0] == t - 1:
            value = prev[1] + (value - prev[1]) * lag
        self._smooth[key] = (t, value)
        return value


def camera(scene, t):
    """(eye, yaw, pitch, fov) in the scene's frame at tick t."""
    imp, snap, pov, shake, burst, scared = B['impact'], B['snap'], B['pov'], B['shake'], B['burst'], B['scared']
    strike, strike2, floor, pour, writhe, smoke = B['strike'], B['strike2'], B['floor'], B['pour'], B['writhe'], B['smoke']
    ignite, roar = B['ignite'], B['roar']
    if t < pov:
        # Low on the floor before him, looking up into the dark as he drops straight at the lens;
        # the camera rides down with him onto the knee.
        body = scene.p('body', t, [0, 24, 1])
        eye = np.array([-1.7, .3, 2.6]) + [0, 0, -.2 * ramp(t, imp, pov)]
        target = np.array([0, max(1.15, body[1] - .3), .15])
        eye = eye + jolt(t, imp, .16, 5) + jolt(t, snap, .05, 3)
        yaw, pitch = look(eye, target)
        # The lens starts tight on him high in the dark and opens as he falls at it, so he grows
        # only a little until he lands.
        dist = np.linalg.norm(body - eye)
        fov = min(56, math.degrees(2 * math.atan(1.32 / dist ** .85))) if t < imp else 56 + 8 * math.exp(-(t - imp) / 4)
        return eye, yaw, pitch, fov
    if t < scared:
        # His eyes: down at the two halves, on the cracks as they rattle, then after the souls.
        eye = scene.face(t) + [0, .05, .05]
        target = scene.follow('pov', t, lambda t: scene.gaze(t), .35)
        breath = .012 * math.sin(t * .35) + .006 * math.sin(t * 1.3)
        k = ramp(t, shake, burst) * .012 + ramp(t, burst, burst + 4) * .01
        eye = eye + [k * math.sin(t * 2.7), breath + k * math.sin(t * 3.3), 0]
        yaw, pitch = look(eye, target)
        return eye, yaw + .6 * math.sin(t * .23), pitch, 66 + 8 * ramp(t, burst - 2, burst + 6)
    if t < pour:
        # Close overhead: two ghosts flank the mask, lunge into it, and floor him.
        target = scene.follow('overhead', t, lambda t: mix(scene.face(t), scene.p('body', t, [0, 22, 1]), .65), .2)
        fall = ramp(t, strike2, floor + 4)
        eye = target + mix([.8, 5.6, 1.35], [.55, 5.0, .9], fall)
        eye = eye + jolt(t, strike, .035, 3) + jolt(t, strike2, .055, 4) + jolt(t, floor, .04, 3)
        yaw, pitch = look(eye, target)
        return eye, yaw, pitch, 50
    if t < writhe:
        # Keep the floor-level side silhouette visible throughout the faster mask intake.
        anchor = mix(scene.face(floor + 6), scene.p('body', floor + 6, [0, 18, 1]), .65)
        push = ramp(t, pour, pour + 22)
        eye = anchor + [3.6 - .35 * push, .28, .15]
        target = anchor + [0, .25, -.12]
        yaw, pitch = look(eye, target)
        return eye, yaw, pitch, 48
    claws, drop, swap, sit, crouch = B['claws'], B['drop'], B['swap'], B['sit'] - 2, B['crouch']
    if t < claws:
        # Cut overhead only after the last soul has entered; hold through the residual
        # convulsions and the first smoke, without chasing the individual body jolts.
        anchor = scene.p('robe', floor + 6, [0, 14, 1])
        smoke_rise = ramp(t, smoke, claws + 20)
        eye = anchor + [.25, 4.1 + .6 * smoke_rise, .35]
        target = anchor + [0, .12 + .35 * smoke_rise, 0]
        yaw, pitch = look(eye, target)
        return eye, yaw, pitch, 48
    if t < drop:
        # Close beside the hand that jerks up into the frame, the claws growing out of it.
        raised = growth.CLAW_JOLTS[1]  # the hand is up, mid-growth
        focus = scene.claws(raised) * .7 + scene.at('mage_hand_right', raised) * .3
        head = scene.at('hood', raised)
        along = (head - scene.at('robe', raised)) * [1, 0, 1]
        along /= np.linalg.norm(along)
        side = np.cross([0, 1, 0], along)
        eye = focus + side * (1.05 - .12 * ramp(t, claws, drop)) + along * .55 + [0, -.3, 0]
        target = scene.follow('claws', t, lambda t: scene.claws(t) * .7 + scene.at('mage_hand_right', t) * .3, .3)
        for k, at in enumerate(growth.CLAW_JOLTS):
            eye = eye + jolt(t, at, .012 + .002 * k, 3)
        yaw, pitch = look(eye, target)
        return eye, yaw, pitch, 50
    if t < swap:
        # Overhead: every spurt knocks the camera back, but less than he grows, so he fills more
        # of the frame each time.
        anchor = scene.follow('growth', t, lambda t: scene.at('body', t) * .6 + scene.at('robe', t) * .4, .2)
        g = scene.follow('height', t, lambda t: growth.size(t), .35)
        eye = anchor + [.4, 4.6 * g ** .45, .6]
        for at in growth.SPURTS:
            eye = eye + jolt(t, at, .07, 4)
        yaw, pitch = look(eye, anchor)
        return eye, yaw, pitch, 50
    if t < sit:
        # Low at its side: the giant, arched off the floor as the robe rips apart on its bones.
        anchor = scene.at('ribcage', swap + 10) * [1, 0, 1] + [0, .9, 0]
        eye = anchor + [-5.6, .35, 1.6]
        target = scene.follow('rip', t, lambda t: scene.at('ribcage', t) * [1, .6, 1] + [0, .5, 0], .15)
        for at in growth.SPASMS:
            eye = eye + jolt(t, at, .05, 4)
        eye = eye + jolt(t, swap, .09, 5)
        yaw, pitch = look(eye, target)
        return eye, yaw, pitch, 52
    if t < crouch:
        # Low before its feet: it sits bolt upright into the lens and its skull snaps round.
        base = scene.at('pelvis', sit) * [1, 0, 1]
        eye = base + [-.8, .4, 4.3] + [0, .25 * ramp(t, sit, sit + 12), 0]
        target = scene.follow('sit', t, lambda t: scene.skull(t) * .7 + scene.at('pelvis', t) * .3, .22)
        eye = eye + jolt(t, growth.SNAP, .05, 3) + jolt(t, B['eyes'], .04, 4)
        yaw, pitch = look(eye, target)
        return eye, yaw, pitch, 56
    # Low before it as it lunges onto its claws and crawls at the lens; it rears up and roars.
    s = ramp(t, crouch, roar)
    # It howls at the ceiling: the lens stays low and tilts up after the skull.
    eye = mix([-1.0, .75, 4.8], [-.9, .55, 6.4], s) + [0, -.2 * ramp(t, roar - 10, roar + 2), .5 * ramp(t, roar - 10, roar + 4)]
    # As it pushes up, swing round to a low three-quarter view: the howl reads in profile.
    swing = ramp(t, B['rear'] - 4, roar + 2)
    eye = eye + [-3.4 * swing, .1 * swing, -1.6 * swing]
    target = scene.follow('crawl', t, lambda t: scene.skull(t) + [0, -.5, 0], .25)
    for at in (*growth.PLANTS.values(), *(steps[1] for steps in growth.STEPS.values())):
        eye = eye + jolt(t, at, .03, 3)
    eye = eye + jolt(t, roar, .2, 7)
    yaw, pitch = look(eye, target)
    return eye, yaw, pitch, 50 + 10 * math.exp(-max(0, t - roar) / 5) * (t >= roar)


def flash(t):
    """Soul-white flashes: the landing, the souls tearing free, the strikes, the ignition and the roar."""
    out = 0
    for at, size, decay in ((B['impact'], .3, 3), (B['burst'], .5, 4), (B['strike'], .3, 3), (B['strike2'], .45, 4),
                            (B['swap'], .55, 5), (B['eyes'], .2, 3), (B['roar'], .25, 4)):
        if t >= at:
            out = max(out, size * math.exp(-(t - at) / decay))
    return out


def black(t):
    return 1 - smooth(t / 8)


def dark(t):
    """The crypt's light: it drains as the staff snaps and the braziers gutter, and floods back
    when the roar relights them."""
    return 1 - ramp(t, B['snap'], B['pov']) + ramp(t, B['roar'] + 2, B['roar'] + 18)


def braziers(t):
    return 1 if t < B['snap'] + 2 or t >= B['roar'] + 2 else 0


def pov(t):
    return 1 if B['pov'] <= t < B['scared'] else 0


def glow(scene, t):
    """The souls' light (scene position and level 0..1): at the cracks, in the storm about him,
    in him, guttering under the smoke, then in the colossus until the braziers return."""
    if t < B['shake']:
        return np.zeros(3), 0
    if t < B['burst']:
        return scene.cracks(t), .7 * ramp(t, B['shake'], B['burst'])
    flicker = .9 + .1 * math.sin(t * 1.7) * math.sin(t * .63)
    if t < B['writhe']:
        storm = mix([0, 1.5, 0], scene.p('body', t, [0, 22, 1]) + [0, 1.0, 0], ramp(t, B['strike2'], B['pour'] + 8))
        return storm, .85 * flicker
    if t < B['smoke']:
        return scene.p('body', t, [0, 22, -3]) + [0, .4, 0], .6 * flicker
    if t < B['swap']:
        # Inside him, leaking through the ripping seams; each spurt flares it.
        surge = max(math.exp(-max(0, t - at) / 5) if t >= at else 0 for at in growth.SPURTS)
        return scene.p('body', t, [0, 22, -3]) + [0, .3, 0], (.45 + .35 * surge) * flicker
    level = .35 + .3 * ramp(t, B['eyes'], B['eyes'] + 4) + .3 * math.exp(-max(0, t - B['swap']) / 6)
    return scene.p('ribcage', t, [0, 47, -2]) + [0, .3, 0], level * flicker * (1 - ramp(t, B['roar'] + 2, B['roar'] + 18))


EVENTS = [(0, 'wind rush'), (B['impact'], 'crash'), (B['snap'], 'staff snaps'), (B['snap'] + 2, 'braziers gutter out'),
          (B['pov'] + 4, 'heartbeat'), (B['grasp'] + 2, 'heartbeat'), (B['shake'], 'the halves rattle'), (B['shake'] + 10, 'whispers'),
          (B['burst'], 'souls shriek free'), (B['burst'] + 16, 'wailing storm'), (B['scared'] + 2, 'heartbeat'),
          (B['strike'], 'a soul strikes'), (B['strike2'], 'a soul strikes'), (B['floor'], 'thud'), (B['pour'] + 6, 'souls rush in'),
          (B['strike2'] + 2, 'the eye light gutters'), (B['floor'] - 4, 'eyes go dark'), (B['writhe'], 'choking'), (B['writhe'] + 12, 'bones crack'),
          (B['smoke'], 'hiss of smoke'), (B['claws'] + 2, 'a hand jerks up'), *((at, 'finger bones crack') for at in growth.CLAW_JOLTS),
          (B['drop'], 'the hand slams down'), *((at, 'bones grind and stretch') for at in growth.SPURTS),
          (growth.SPURTS[1], 'a seam rips'), *((at + 2, 'cloth tears') for at in growth.HEMS.values()),
          (B['swap'], 'the robe tears apart'), *((at, 'a spasm') for at in growth.SPASMS), (B['swap'] + 14, 'rags slap the stone'),
          (B['sit'], 'it sits up'), (growth.SNAP, 'bone creaks'), (B['eyes'], 'soul fire ignites'),
          *((at, 'a claw strikes stone') for at in growth.PLANTS.values()), (B['crawl'], 'bone scraping stone'),
          (B['rear'], 'claws grind into the stone'), (B['roar'], 'a howl'), (B['roar'] + 2, 'braziers flare')]


def bursts(scene):
    """The preview's smoke (translucent pixel puffs) and flecks, each born at a bone it rises
    from. In game these are particles."""
    smoke, effects = [], []
    puff = lambda tick, at, kind, count, spread, rise, radius, opacity, life, seed: smoke.append(dict(
        tick=tick, at=r(at, 3), kind=kind, count=count, spread=spread, rise=rise, radius=radius, opacity=opacity, life=life, seed=seed))
    for k, t in enumerate(range(B['smoke'], B['swap'], 6)):
        if B['claws'] - 24 <= t < B['drop']:
            continue  # the claws' close-up sits by his face: nothing drifts across the lens
        puff(t, scene.face(t) + [0, .08, 0], 'wisp', 1, .1, .45, .16, .16, 30, 7 + k)
    for k, at in enumerate(growth.SPURTS):
        g = growth.size(at + 3)
        puff(at, scene.p('body', at + 2, [0, 22, -3]), 'puff', 6, .45 * g, .5, .32 * g ** .5, .34, 30, 61 + k)
    swap = B['swap']
    chest = scene.at('ribcage', swap)
    puff(swap - 1, chest, 'burst', 24, 1.7, .9, .8, .6, 46, 101)
    puff(swap, chest * [1, 0, 1] + [0, .3, 0], 'ring', 16, 2.3, .2, .62, .45, 52, 131)
    for k, t in enumerate(range(swap + 4, B['flat'] + 6, 5)):
        puff(t, scene.at('ribcage', t) * [1, 0, 1] + [0, .3, 0], 'cloak', 3, 1.8, .25, .6, .3, 40, 151 + k)
    for k, t in enumerate(range(B['sit'], B['crouch'], 8)):
        puff(t, scene.at('pelvis', t) * [1, 0, 1] + [0, .25, 0], 'cloak', 2, 2.0, .2, .55, .22, 36, 191 + k)
    landings = [(PLANT, side) for side, PLANT in growth.PLANTS.items()] + [(s1, side) for side, (_, s1) in growth.STEPS.items()]
    for k, (t, side) in enumerate(landings):
        hand = scene.at('hand_' + side, t) * [1, 0, 1] + [0, .12, 0]
        puff(t, hand, 'dust', 4, .35, .25, .26, .3, 18, 221 + k)
        effects.append(dict(tick=t, kind='chip', at=r(hand, 3), count=9, seed=t))
    effects.append(dict(tick=swap, kind='ember', at=r(chest, 3), count=14, seed=swap))
    effects.append(dict(tick=B['eyes'], kind='ember', at=r(scene.skull(B['eyes']), 3), count=8, seed=B['eyes']))
    return smoke, effects


def r(v, n=4):
    return [round(float(x), n) for x in v]


def quaternion(m):
    """The (x, y, z, w) quaternion of a rotation matrix (JOML's convention)."""
    t = np.trace(m)
    if t > 0:
        s = math.sqrt(t + 1) * 2
        q = [(m[2, 1] - m[1, 2]) / s, (m[0, 2] - m[2, 0]) / s, (m[1, 0] - m[0, 1]) / s, s / 4]
    else:
        i = int(np.argmax(np.diag(m)))
        j, k = (i + 1) % 3, (i + 2) % 3
        s = math.sqrt(1 + m[i, i] - m[j, j] - m[k, k]) * 2
        q = [0, 0, 0, (m[k, j] - m[j, k]) / s]
        q[i] = s / 4
        q[j] = (m[j, i] + m[i, j]) / s
        q[k] = (m[k, i] + m[i, k]) / s
    return np.array(q) / np.linalg.norm(q)


GHOST = np.eye(4)
GHOST[1:3, 1:3] = [[math.cos(math.radians(-25)), -math.sin(math.radians(-25))], [math.sin(math.radians(-25)), math.cos(math.radians(-25))]]
GHOST = GHOST @ np.array([[1, 0, 0, 0], [0, 1, 0, -11 / 16], [0, 0, 1, 0], [0, 0, 0, 1]])  # tipped back, its head on the socket


def soul_poses(scene, eyes):
    """Each ghost as the animatic draws it, for the game's Soul Harvest stand-ins: from its first
    visible tick, per tick its root in the scene's frame, its turn (a quaternion in GeckoLib's
    mirrored model space turned into the scene's frame, as the boss is) and its size. The camera's
    clearance is baked into the size, so a soul near the lens shrinks away rather than filling it."""
    out = []
    flip = np.diag([-1., 1, -1, 1])  # GeckoLib's mirrored model space into the scene's frame
    for i, plan in soul_plan().items():
        rows = {}
        for t in range(plan['emerge'] - 1, LENGTH + 1):
            socket = scene.a.world(f'soul_{i}', t).copy()
            socket[:3, 3] /= 16
            socket = flip @ socket
            if np.linalg.norm(socket[:3, 0]) < .002:
                continue
            distance = np.linalg.norm(socket[:3, 3] - eyes[t])
            emerging = t - plan['emerge'] < 14
            clearance = smooth((distance - (.18 if emerging else .65)) / (.42 if emerging else .9))
            size = (.8 + .15 * math.sin(i * 2.4)) * clearance
            if size < .005:
                continue
            base = socket @ np.diag([size, size, size, 1]) @ GHOST
            scale = abs(np.linalg.det(base[:3, :3])) ** (1 / 3)
            u, _, vt = np.linalg.svd(base[:3, :3])
            rows[t] = [*r(base[:3, 3]), *r(quaternion(u @ vt), 4), round(float(scale), 4)]
        first, last = min(rows), max(rows)
        poses = [rows.get(t, [*rows[max(k for k in rows if k <= t)][:7], 0]) for t in range(first, last + 1)]
        strike = {4: B['strike'], 9: B['strike2']}.get(i)
        out.append(dict(bone=f'soul_{i}', **plan, first=first, poses=poses, attack=[strike - 15, strike] if strike else None))
    return out


def bake_track(scene):
    ticks = {'eye': [], 'yaw': [], 'pitch': [], 'fov': [], 'flash': [], 'black': [], 'dark': [], 'braziers': [], 'pov': [], 'glow': []}
    last_yaw = None
    for t in range(LENGTH + 1):
        eye, yaw, pitch, fov = camera(scene, t)
        if last_yaw is not None:
            yaw = last_yaw + (yaw - last_yaw + 180) % 360 - 180
        last_yaw = yaw
        ticks['eye'].append(r(eye))
        ticks['yaw'].append(round(float(yaw), 3))
        ticks['pitch'].append(round(float(pitch), 3))
        ticks['fov'].append(round(float(fov), 2))
        ticks['flash'].append(round(flash(t), 4))
        ticks['black'].append(round(black(t), 4))
        ticks['dark'].append(round(dark(t), 4))
        ticks['braziers'].append(braziers(t))
        ticks['pov'].append(pov(t))
        at, level = glow(scene, t)
        ticks['glow'].append(r([*at, level]))
    smoke, effects = bursts(scene)
    return {'beats': B, 'cuts': CUTS, 'staff_break': STAFF_BREAK, 'smoke': smoke, 'effects': effects,
            'souls': soul_poses(scene, [np.array(e) for e in ticks['eye']]), 'transformOnly': sorted(TRANSFORM_ONLY),
            'events': [{'tick': t, 'cue': c} for t, c in EVENTS], 'ticks': ticks}


def encode(data):
    return json.dumps(data, separators=(',', ':')) + '\n'


def animatic(track_text):
    PREVIEW.mkdir(parents=True, exist_ok=True)
    for name in ('hollow_necromancer.geo.json', 'hollow_necromancer.animation.json', 'hollow_necromancer.png', 'hollow_necromancer_glowmask.png'):
        shutil.copyfile(CANDIDATE / name, PREVIEW / name)
    assets = REPO / 'src/main/resources/assets/elementalwands'
    for folder, name in (('geckolib/models', 'harvest_soul.geo.json'),
                         ('geckolib/animations', 'harvest_soul.animation.json'),
                         ('textures/entity', 'harvest_soul.png'),
                         ('textures/entity', 'harvest_soul_glowmask.png')):
        shutil.copyfile(assets / folder / name, PREVIEW / name)
    (PREVIEW / 'transform-track.json').write_text(track_text)
    shutil.copyfile(HERE / 'animatic.js', PREVIEW / 'animatic.js')
    revision = hashlib.sha256((HERE / 'animatic.js').read_bytes()).hexdigest()[:12]
    html = (HERE / 'viewer.template.html').read_text().replace('src="animatic.js"', f'src="animatic.js?v={revision}"')
    (PREVIEW / 'index.html').write_text(html)


def main():
    scene = Scene()
    text = encode(bake_track(scene))
    if '--check' in sys.argv:
        stale = [str(p.relative_to(REPO)) for p in (TRACK, RUNTIME) if not p.exists() or p.read_text() != text]
        if stale:
            sys.exit('Stale transformation track: ' + ', '.join(stale))
        print('Transformation track matches.')
        return
    TRACK.write_text(text)
    animatic(text)
    print(f'Track written ({LENGTH} ticks); animatic in {PREVIEW.relative_to(REPO)}.')
    if '--install' in sys.argv:
        RUNTIME.parent.mkdir(parents=True, exist_ok=True)
        RUNTIME.write_text(text)
        print('Installed the runtime track.')


if __name__ == '__main__':
    main()
