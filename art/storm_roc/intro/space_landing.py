#!/usr/bin/env python3
"""The Storm Roc's intro with Space's landing: build_intro.py's scene from the throw on.

Everything up to the throw is build_intro's. Then a Space wizard does not try to slow down at all.
Out of the tumble they fall head first, fast and flailing, the snow rushing up. A few blocks above
it time slows for a breath, a Blink Rift opens round them and they fold into it. Their momentum
still slams into the snow where they would have hit, a burst of it; and a few blocks to the side a
second rift opens and they step out of it already walking, perfectly calm, tugging a cuff straight
and straightening their collar as they stroll to the arrival. Head down; then it comes up to glare
at the spire, and the scene cuts to the Roc landing on it as before.

The scene is authored in story time: speed() stretches the moment of the blink into slow motion.
The camera runs in real time. The Roc's flight and wingbeats run on the story clock too.

Run from the repository root, this writes a browser animatic to .local-previews/storm-roc-intro-space/:
    py -3 art/storm_roc/intro/space_landing.py
then serve that folder and open it. Space's effects are in landing_space.js beside this file, and in
the game in client/roclanding/SpaceLanding.java; install_landings.py space bakes this into the mod.
"""
import json
import math
import shutil
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import build_intro as B  # noqa: E402
from build_intro import EASE, Rx, curve, heading, kick, look, mix, pulse, ramp, shake, slerp, smooth, wobble  # noqa: E402
from gecko import Rx as Rx4, T as T4  # noqa: E402

PREVIEW = B.REPO/'.local-previews/storm-roc-intro-space'

# ------------------------------------------------------------------ story beats

# In story ticks (one is 0.1 s at normal speed). Until the slow motion, story and real ticks agree.
THROW, GROUND = B.THROW, B.GROUND
RIGHT = 234      # out of the tumble: head first, flailing, the snow rushing up
VANISH = 250.6   # a few blocks above the snow: the rift opens round them
FOLDED = VANISH + 1.6  # folded into it and gone; it pinches shut
HIT = FOLDED + .15     # where they would have hit: their momentum blows the snow out
APPEAR = HIT + 1.5     # a few blocks to the side: out of a second rift, already walking
STOP = APPEAR + 30     # strolled to the arrival, collar straightened

ARRIVAL = B.ARRIVAL.copy()                   # where you stop, and the fight expects you
FLOOR = ARRIVAL[1]
IMPACT = np.array([-3.0, FLOOR, 37.0])       # where you would have hit the snow, head first
SIDE = np.array([1.5, FLOOR, 37.0])          # where you step out of the rift: a few blocks to the side
WAND_TIP = 0.72                              # the animatic's wand: tip 0.72 out along its axis
SPIRE = B.HOME_PERCH + [0, 6, 0]
STRIDE = .8                                  # blocks a step


def speed(s):
    """How fast story time runs at story tick s: 1 normal, under 1 slow motion."""
    return 1.0 - .72*pulse(s, VANISH - 2.2, VANISH - .3, FOLDED - .2, FOLDED + .4)


def slowmo(s): return pulse(s, VANISH - 2.2, VANISH - .3, FOLDED - .2, FOLDED + .4)


_S = np.arange(THROW, STOP + 80, .005)
_V = np.array([speed(x) for x in _S])
_R = THROW + np.concatenate([[0.0], np.cumsum((1/_V[1:] + 1/_V[:-1])/2*.005)])


def real(s):
    """The real tick at story tick s."""
    if s <= THROW: return float(s)
    if s >= _S[-1]: return float(_R[-1] + s - _S[-1])
    return float(np.interp(s, _S, _R))


def clock(t):
    """The story tick at real tick t."""
    if t <= THROW: return float(t)
    if t >= _R[-1]: return float(_S[-1] + t - _R[-1])
    return float(np.interp(t, _R, _S))


def half(x): return round(x*2)/2


# ------------------------------------------------------------------ real beats: cuts and the camera

RT = {k: half(real(v)) for k, v in dict(right=RIGHT, vanish=VANISH, folded=FOLDED, hit=HIT,
                                         appear=APPEAR, stop=STOP).items()}
RT['dive_cut'] = 244.5                                 # riding the dive, looking down past you at the snow
RT['blink_cut'] = RT['folded']                         # low on the snow, in front: the burst, the stroll
RT['look_cut'] = half(real(STOP - 7))                  # low behind your wand shoulder as you arrive
RT['look'] = half(real(STOP + 2))                      # the head comes up
LANDING = math.ceil(RT['look']) + 12                              # cut: the Roc coming down onto its spire, as before
TOUCHDOWN = LANDING + 2
LAND = TOUCHDOWN - 16
SETTLED = LAND + 50
BACK = LANDING + 32
UP = BACK + 6
RETURN = UP + 1
LENGTH = RETURN + 20

# ------------------------------------------------------------------ build_intro's timeline, moved on

ORIGINAL_RATE = B.rate
ORIGINAL_OVERLAYS = B.overlays


def rate(t):
    return ORIGINAL_RATE(t)*speed(clock(t))  # its wingbeats slow with the story clock


def flash(t):
    """build_intro's lightning light, aged on the story clock."""
    f = 0.0
    for e in B.LIGHTNING:
        if t >= e[0]:
            size = {'crackle': .6, 'sheet': .8, 'bolt': .9}[e[2]]*(e[5] if len(e) > 5 else 1)
            age = clock(t) - clock(e[0])
            f += size*math.exp(-age/2.2)*(.6 + .4*math.cos(age*2.1))
    return min(1.0, f)


def overlays(scene, t):
    o = ORIGINAL_OVERLAYS(scene, t)
    o['bars'] = o['bars']*(1 + .3*slowmo(clock(t)))  # the letterbox closes in a little on the slow motion
    return o


def retime():
    g = vars(B)
    g.update(LANDING=LANDING, TOUCHDOWN=TOUCHDOWN, LAND=LAND, SETTLED=SETTLED, BACK=BACK, UP=UP, RETURN=RETURN,
             LENGTH=LENGTH, CRASH=RT['hit'], ROLLED=RT['stop'], TITLE=LANDING + 3, TITLE_END=BACK)
    g['rate'] = rate
    g['flash'] = flash
    g['overlays'] = overlays
    g['PHASES'] = B._phases()
    g['DOWNSTROKES'] = [t for t in range(1, LENGTH + 1) if (B.PHASES[t - 1]/B.BEAT) % 1 < .45 <= (B.PHASES[t]/B.BEAT) % 1]
    g['LIGHTNING'] = [e for e in B.LIGHTNING if e[0] < THROW + 10] + [(TOUCHDOWN + 1, 1, 'bolt', 26, -70)]
    beats = dict(B.BEATS, length=LENGTH, crash=RT['hit'], landing=LANDING, touchdown=TOUCHDOWN, land=LAND, rolled=RT['stop'],
                 back=BACK, up=UP, title=LANDING + 3, title_end=BACK, ret=RETURN, **RT)
    g['BEATS'] = beats
    g['CUTS'] = sorted({B.SUMMIT, THROW, GROUND, RT['dive_cut'], RT['blink_cut'], RT['look_cut'], LANDING, BACK})


class Warped:
    """A path keyed in story ticks, read in real ticks."""

    def __init__(self, inner): self.inner = inner
    def __call__(self, t): return self.inner(clock(t))
    def velocity(self, t, dt=.25): return self.inner.velocity(clock(t), dt)


def summit_body_path():
    """As build_intro's to the throw; then, its hands empty, it wheels out over the void east of
    its spire in one wide climbing circle, and comes back round from the north to land on it."""
    perched = B.HOME_PERCH + heading(B.HOME_YAW)@B.roc_local(LENGTH)['body']
    S = B.SUMMIT
    start, land = THROW + 14, clock(LAND)
    circle = [(13/62, [18, 52, -96]), (26/62, [31, 56, -106]), (39/62, [22, 56, -121]), (51/62, [8, 51, -116])]
    keys = [(S - 10, [-74, 8, -160]), (S, [-62, 18, -168]), (S + 16, [-44, 31, -178]), (S + 34, [-20, 41, -176]),
            (S + 52, [2, 47, -162]), (THROW - 10, [10, 49, -142]), (THROW, [10, 49, -128]), (start, [7, 47, -111])]
    keys += [(start + f*(land - start), p) for f, p in circle]
    keys += [(land, [4, 45, -99]), (clock(TOUCHDOWN), perched + [0, .6, -1.2]), (clock(SETTLED), perched)]
    return Warped(B.Path3(keys, end_velocity=np.zeros(3)))


# ------------------------------------------------------------------ the player's body

ORIGINAL_CHANNELS = B.puppet_channels


def K(s, *rows):
    """A value keyed through rows of (story tick, ease into it, value)."""
    return curve(s, list(rows))[0]


def wand_points(m):
    """The hand and the wand's tip, as the animatic draws the wand."""
    w = m['wand']@T4(*B.PUP.pivot['wand'])@Rx4(math.radians(-70))
    return (w@np.array([0, 0, 0, 1.0]))[:3], (w@np.array([0, 0, WAND_TIP, 1.0]))[:3]


def fold(s):
    """How folded into the rift you are: 1 whole, 0 gone (a thin streak into the slit, then nothing)."""
    if s < VANISH + .3: return 1.0
    if s < FOLDED: return 1 - EASE['in'](min(1, (s - VANISH - .3)/(FOLDED - .2 - VANISH - .3)))
    if s < APPEAR: return 0.0
    return EASE['out'](min(1, (s - APPEAR)/1.4))


def walked(s):
    """Blocks walked from the rift toward the arrival by story tick s: a steady stroll, easing into the stop."""
    total = float(np.linalg.norm(ARRIVAL - SIDE))
    u = min(1, max(0, (s - APPEAR)/(STOP - APPEAR)))
    # Mostly steady, slowing over the last stretch.
    return total*(u*1.25 - .25*u**4)/1.0 if u < 1 else total


def stepping(s):
    """The walk's leg swing (0 standing still, 1 striding) at story tick s."""
    return ramp(s, APPEAR - .5, APPEAR + .5)*(1 - ramp(s, STOP - 4, STOP))


def posture(s):
    """Every bone at story tick s (degrees; arms -X raise forward, +Z out on the right arm and -Z on
    the left; legs -X forward; waist +X bends forward; head +X looks down)."""
    c = {}
    def rot(b, r): c[b] = {'rotation': np.array(r, dtype=float)}
    if s < RIGHT:
        # Flung: flailing as you tumble.
        fl = 1 - ramp(s, RIGHT - 5, RIGHT)
        dive = dive_pose(s)
        rot('right_arm', mix([-120 + 50*math.sin(s*.6), 0, 60*fl + 20], dive['right_arm'], 1 - fl))
        rot('left_arm', mix([-120 + 50*math.sin(s*.6 + 2), 0, -60*fl - 20], dive['left_arm'], 1 - fl))
        rot('right_leg', mix([-30 + 40*math.sin(s*.5), 0, 14], dive['right_leg'], 1 - fl))
        rot('left_leg', mix([-30 - 40*math.sin(s*.5), 0, -14], dive['left_leg'], 1 - fl))
        rot('waist', mix([-20, 0, 0], dive['waist'], 1 - fl)); rot('head', mix([30, 0, 0], dive['head'], 1 - fl))
        return c
    if s < APPEAR:
        d = dive_pose(s)
        for b, r in d.items(): rot(b, r)
        return c
    return stroll(s)


def dive_pose(s):
    """Head first and flailing: the arms windmilling past your head toward the snow, the legs kicking,
    the head craned back to see the ground coming."""
    panic = 1 + .5*ramp(s, VANISH - 4, VANISH)
    return {
        'right_arm': np.array([-150 + 40*panic*math.sin(s*2.1), 0, 34 + 26*math.sin(s*1.7)]),
        'left_arm': np.array([-150 + 40*panic*math.sin(s*2.1 + 2.6), 0, -34 - 26*math.sin(s*1.9 + 1)]),
        'right_leg': np.array([18*panic*math.sin(s*2.6), 0, 12 + 6*math.sin(s*1.3)]),
        'left_leg': np.array([-18*panic*math.sin(s*2.6), 0, -12 - 6*math.sin(s*1.5)]),
        'waist': np.array([-14 + 6*math.sin(s*1.3), 0, 0]),
        'head': np.array([-38 + 6*math.sin(s*1.1), 6*math.sin(s*.9), 0]),
    }


# The stroll's gestures (story ticks after APPEAR): the left hand tugs the right cuff straight, then
# goes up to straighten the collar, chin lifted; a last smoothing-down of the front; walking on.
CUFF_IN, CUFF_OUT = 3.0, 12.0
COLLAR_IN, COLLAR_OUT = 13.5, 22.0


def stroll(s):
    c = {}
    def rot(b, r): c[b] = {'rotation': np.array(r, dtype=float)}
    a = s - APPEAR
    phase = walked(s)/STRIDE*math.pi
    swing = math.sin(phase)*stepping(s)
    lift = EASE['io'](min(1, max(0, (s - clock(RT['look']))/5)))
    rise = ramp(s, clock(BACK + 1), clock(UP))
    cuff = pulse(a, CUFF_IN - 1.5, CUFF_IN, CUFF_OUT - 1, CUFF_OUT + .5)
    collar = pulse(a, COLLAR_IN - 1.5, COLLAR_IN, COLLAR_OUT - 1, COLLAR_OUT + .8)
    tug = kick(a, CUFF_IN + 2, 1, 4, 2.5) + kick(a, CUFF_IN + 5, 1, 4, 2.5)
    pull = kick(a, COLLAR_IN + 1.5, 1, 3.5, 2.2) + kick(a, COLLAR_IN + 4.5, 1, 3.5, 2.2)
    rot('right_leg', [-24*swing, 0, 3]); rot('left_leg', [24*swing, 0, -3])
    rot('waist', [2 + 2*abs(swing), 4*swing, 0])
    # The wand arm: swinging easy, the wand held low; brought up across the front for the cuff.
    walk_r = np.array([14*swing - 6, 0, 6])
    rot('right_arm', mix(mix(walk_r, [-50, -24, -4], cuff), [-22, 0, 12], rise))
    # The free arm: swinging, then at the cuff, then at the collar.
    walk_l = np.array([-14*swing - 2, 0, -5])
    left = mix(walk_l, [-58 + 8*tug, 30, 22], cuff)
    left = mix(left, [-96 + 9*pull, 38, 4], collar)
    rot('left_arm', mix(left, [0, 0, -6], rise))
    # Looking down at the cuff, chin up for the collar, a small settling roll of the neck, then down
    # again as you arrive; up to glare at the spire.
    head_x = 8 + 26*cuff - 30*collar + 10*ramp(a, COLLAR_OUT, COLLAR_OUT + 3)*(1 - lift) - 34*lift
    head_y = -18*cuff + 4*math.sin(a*.7)*(1 - lift)
    head_z = 8*pulse(a, COLLAR_OUT - 1, COLLAR_OUT + .5, COLLAR_OUT + 1.5, COLLAR_OUT + 3)
    rot('head', [head_x - 14*rise*0, head_y, head_z])
    return c


def puppet_channels(t):
    if t < THROW: return ORIGINAL_CHANNELS(t)
    s = clock(t)
    c = posture(s)
    k = fold(s)
    if k < 1:
        # Folding into the slit: thinned to a sliver, drawn in toward the middle; and unfolding out of it.
        if s < APPEAR: c['root'] = {'scale': np.array([k**1.5, .35 + .65*k, k**1.5])}
        else: c['root'] = {'scale': np.array([max(1e-3, k), 1.0, max(1e-3, k)])}
        if k <= 0: c['root'] = {'scale': np.zeros(3)}
    return c


class SpaceScene(B.Scene):

    def ballistic(self):
        """The throw: from the talons at THROW to the snow at HIT, head first, on a lob."""
        (_, _), w = self.roc_world(THROW)
        p0 = w['grip']
        land = IMPACT + [0, 1.0, 0]
        tau = (HIT - THROW)/20
        v0 = (land - p0 - .5*np.array([0, -B.GRAVITY, 0])*tau*tau)/tau
        return p0, v0

    def velocity(self, s):
        p0, v0 = self.throw
        return v0 + np.array([0, -B.GRAVITY, 0])*(s - THROW)/20

    def hips(self, s):
        """Where your hips are at story tick s, from the throw on."""
        if s < VANISH: return self.thrown(s)
        if s < APPEAR:
            # Swallowed: carried on a little into the rift as it takes you, then held at its middle.
            v = self.velocity(VANISH)
            return self.thrown(VANISH) + v/20*.35*EASE['out'](min(1, (s - VANISH)/(FOLDED - VANISH)))
        d = (ARRIVAL - SIDE)/np.linalg.norm(ARRIVAL - SIDE)
        return SIDE + d*walked(s) + [0, B.HIPS[1], 0]

    def rotation(self, s):
        if s < RIGHT:
            # One fast forward tumble out of its grip, and on over into a head-first dive.
            u = (s - THROW)/(RIGHT - THROW)
            spin = heading(6*wobble(s, 31, 1.3), 88 + (360 + dive_pitch(self, RIGHT) - 88)*EASE['out'](u), 10*wobble(s, 32, 1.1)*(1 - u))
            return slerp(self.held(THROW), spin, ramp(s, THROW, THROW + 4))
        if s < APPEAR:
            # Head first along the fall, buffeted.
            ss = min(s, VANISH + .3)
            b = 1 + .6*ramp(ss, VANISH - 4, VANISH)
            return heading(4*b*math.sin(ss*1.3), dive_pitch(self, ss) + 4*b*math.sin(ss*1.7), 8*b*math.sin(ss*.9))
        d = ARRIVAL - SIDE
        yaw = math.degrees(math.atan2(-d[0], d[2]))
        settle = ramp(s, STOP - 6, STOP)
        return heading(float(mix(yaw, 180, settle)) + 2*math.sin(walked(s)/STRIDE*math.pi)*stepping(s), 0, 0)

    def body_at(self, s):
        r = self.rotation(s)
        return self.hips(s) - r@B.HIPS, r

    def player(self, t):
        if t < THROW: return super().player(t)
        s = clock(t)
        root, r = self.body_at(s)
        k = fold(s)
        if s < APPEAR and k < 1:
            # Folding toward the hips (the scale's pivot is at the feet).
            root = root + r@(B.HIPS*(1 - (.35 + .65*k)))
            return root, r
        if FOLDED <= s < APPEAR:
            return SIDE + [0, -.01, 0], heading(180)
        low = self.lowest(root, r, t)
        if s >= APPEAR:
            root = root + [0, FLOOR - low, 0]  # walking on the snow
        elif low < FLOOR:
            root = root + [0, FLOOR - low, 0]
        return root, r

    def tip(self, t):
        return wand_points(self.puppet_pose(t))[1]


def dive_pitch(scene, s):
    """The pitch at story tick s: head first, steeper than the fall itself so it reads as a plunge
    (90 is face down, head forward; 180 straight down)."""
    v = scene.velocity(s)
    along = 90 + math.degrees(math.atan2(-v[1], math.hypot(v[0], v[2])))
    return along + (165 - along)*.8


# ------------------------------------------------------------------ the camera

ORIGINAL_THIRD = B.third_person


def third_person(scene, t):
    if t < GROUND or t >= LANDING: return ORIGINAL_THIRD(scene, t)
    s = clock(t)
    root, r = scene.player(t)
    if t < RT['dive_cut']:
        # Falling beside you, a little ahead and below: out of the tumble, over into the dive.
        chest = scene.thrown(s)
        v = scene.velocity(s)
        d = v*np.array([1, 0, 1]); d = d/np.linalg.norm(d)
        side = np.cross([0, 1, 0], d)
        eye = chest + side*(5.0 - .6*ramp(t, GROUND, RT['dive_cut'])) + d*1.6 + [0, -.6 + 1.6*ramp(t, RIGHT - 2, RT['dive_cut']), 0]
        target = mix(chest, chest + v/np.linalg.norm(v)*6, .08 + .12*ramp(t, RIGHT, RT['dive_cut']))
        yaw, pitch = look(eye, target)
        sh = shake(t, 40, 1.0 + 1.0*ramp(t, RIGHT, RT['dive_cut']))
        fov = 58 + 6*ramp(t, RIGHT, RT['dive_cut'])
        return eye, yaw + sh[0], pitch + sh[1], sh[2]*2 - 6*math.sin(t*.1), float(fov)
    if t < RT['blink_cut']:
        # Riding the dive just behind and above you, looking down past your head at the snow rushing
        # up; slowing as the rift opens round you, and holding as you fold into it.
        ss = min(s, VANISH)
        hips = scene.thrown(ss)
        v = scene.velocity(ss); d = v/np.linalg.norm(v)
        flat = v*np.array([1, 0, 1]); flat = flat/np.linalg.norm(flat)
        side = np.cross([0, 1, 0], flat)
        brake = EASE['out'](min(1, max(0, (s - VANISH)/(FOLDED - VANISH))))
        back = 1.8 - .6*ramp(t, RT['dive_cut'], RT['vanish'])
        eye = hips - flat*back + [0, 2.2, 0] + side*3.4 + v/20*.35*brake
        target = mix(hips, hips + d*12, .3 - .2*ramp(t, RT['vanish'] - 3, RT['folded']))
        yaw, pitch = look(eye, target)
        bt = slowmo(s)
        sh = shake(t, 42, (1.6 - 1.2*bt)*(1 - brake))
        fov = 70 + 8*ramp(t, RT['dive_cut'], RT['vanish'] - 2) - 16*bt
        return eye, yaw + sh[0], pitch + sh[1], sh[2]*2 + 6*math.sin(t*.13)*(1 - bt), float(fov)
    if t < RT['look_cut']:
        # Low on the snow ahead of you, a little to the side: the snow bursts where you would have hit;
        # beside it you step out of the rift and stroll toward us, fixing your cuff and your collar.
        u = ramp(t, RT['blink_cut'], RT['look_cut'])
        eye = np.array([3.6, 1.45, 31.6]) + [-.9*u, .15*u, -4.0*u]
        chest = root + r@B.CHEST
        if s < APPEAR: chest = SIDE + [0, 1.1, 0]
        target = mix(mix(IMPACT, SIDE, .55) + [0, 1.2, 0], chest + [0, .1, 0], ramp(t, RT['appear'], RT['appear'] + 10))
        yaw, pitch = look(eye, target)
        sh = shake(t, 45, .3 + 1.3*math.exp(-max(0, t - RT['hit'])/4)*(t >= RT['hit']))
        fov = 54 - 12*ramp(t, RT['appear'] + 4, RT['look_cut'])
        return eye, yaw + sh[0], pitch + sh[1], sh[2]*1.4, float(fov)
    # Low behind you, over your wand shoulder: you arrive and stop, head down; then as it comes up,
    # we tilt up with it past your shoulder to the Roc coming back round to its spire.
    up = ramp(t, RT['look'] - 1, RT['look'] + 7)
    eye = ARRIVAL + [1.5, 1.25, 4.6] + [-.2*up, -.2*up, .3*up]
    chest = root + r@B.CHEST
    (_, _), w = scene.roc_world(t)
    head = scene.eye(t)
    aim = head + (w['body'] - head)/np.linalg.norm(w['body'] - head)*14
    target = mix(chest + [-.4, .25, -1.5], aim, .05 + .9*up**1.3)
    yaw, pitch = look(eye, target)
    sh = shake(t, 47, .2)
    fov = 50 - 12*up
    return eye, yaw + sh[0], pitch + sh[1], sh[2] - 2*up, float(fov)


# ------------------------------------------------------------------ effects

def events(scene):
    """One-shot effects: where, and when on both clocks (game ticks)."""
    def e(s, kind, at, **more):
        return {'tick': round(real(s)*B.PACE, 2), 'clock': round(s*B.PACE, 2), 'kind': kind, 'at': B.r3(at), **more}
    root, r = scene.body_at(VANISH)
    centre = scene.hips(VANISH + 1) + r@np.array([0, .25, 0])
    out = [e(VANISH, 'fold', centre, axis=B.r3(r@np.array([0, 1.0, 0]))),
           e(HIT, 'burst', IMPACT),
           e(APPEAR - .3, 'unfold', SIDE)]
    return out


SHELL_BONES = ['head', 'body', 'right_arm', 'left_arm', 'right_leg', 'left_leg']
SHELL_INFLATE = .025  # the animatic's shellOver: each face pushed out this far


def shell_frames(scene, evs):
    """The player's posed body as boxes, for the game's port of the glass sheen over them as they
    fold into the rift and unfold out of the other (the animatic draws it from the posed model,
    which the game's effects can't see). Per game tick through those moments: for each of
    SHELL_BONES' skin cube, its centre and its three half-edges (x, y, z), grown by SHELL_INFLATE;
    12 numbers a bone."""
    fold = next(e for e in evs if e['kind'] == 'fold')
    unfold = next(e for e in evs if e['kind'] == 'unfold')
    ticks = list(range(math.floor(fold['tick']), math.ceil(real(FOLDED)*B.PACE) + 1))
    ticks += list(range(math.floor(unfold['tick']), math.ceil(unfold['tick'] + 10) + 1))
    cubes = {b['name']: b['cubes'][0] for b in B.PUPPET_GEO['minecraft:geometry'][0]['bones'] if b['name'] in SHELL_BONES}
    frames = {}
    for g in ticks:
        m = scene.puppet_pose(g/B.PACE)
        out = []
        for bone in SHELL_BONES:
            o, size = np.array(cubes[bone]['origin'], float), np.array(cubes[bone]['size'], float)
            at = lambda f: B.PUP.point(m, bone, o + size*np.array(f))
            centre = at([.5, .5, .5])
            out.append(centre)
            for axis in np.eye(3):
                half = at(.5 + axis*.5) - centre
                n = np.linalg.norm(half)
                out.append(half/n*(n + SHELL_INFLATE) if n > 1e-6 else half)
        frames[str(g)] = [round(float(x), 3) for v in out for x in v]
    return frames


def space_columns(scene):
    keys = ('n_clock', 'sp_speed', 'sp_bt', 'sp_fold', 'sp_rush')
    cols = {k: [] for k in keys}
    for g in range(LENGTH*B.PACE + 1):
        t = g/B.PACE
        s = clock(t)
        put = lambda k, v: cols[k].append(round(float(v), 3))
        put('n_clock', s*B.PACE)
        put('sp_speed', .4*pulse(t, GROUND + 1, GROUND + 4, RT['dive_cut'] - .5, RT['dive_cut']) +
            .55*pulse(t, RT['dive_cut'], RT['dive_cut'] + .5, RT['vanish'] - 2, RT['vanish'] + 1))
        put('sp_bt', slowmo(s))
        put('sp_fold', fold(s) if t >= THROW else 1)
        put('sp_rush', ramp(t, RT['dive_cut'], RT['vanish'])*(t < RT['blink_cut']))
    return cols


ORIGINAL_CUES = B.cues


def cues(scene):
    out = [c for c in ORIGINAL_CUES(scene) if c['cue'] not in ('crash',)]
    more = [(RT['right'], 'dive_wind'), (RT['vanish'] - 3, 'slowmo_in'), (RT['vanish'], 'rift_open'), (RT['folded'] - 1, 'rift_fold'),
            (RT['hit'], 'snow_burst'), (RT['appear'], 'rift_unfold'), (RT['appear'] + 1, 'footsteps'),
            (half(real(APPEAR + CUFF_IN + 2)), 'cuff_tug'), (half(real(APPEAR + COLLAR_IN + 1.5)), 'collar_snap')]
    out += [{'tick': int(round(t)), 'cue': c, 'from': 'player'} for t, c in more]
    return sorted(out, key=lambda c: c['tick'])


# ------------------------------------------------------------------ baking

SHOTS = [['1 · quiet', 0], ['2 · caw', 'caw'], ['3 · snatch', 'snatch'], ['4 · carried', 'grip', 8], ['5 · reveal', 'look_up'],
         ['6 · clouds', 'cloud'], ['7 · struggle', 'summit'], ['8 · throw', 'throw'], ['9 · the fall', 'ground'],
         ['10 · the dive', 'dive_cut'], ['11 · the blink', 'blink_cut'], ['12 · the glare', 'look_cut'],
         ['13 · the Roc lands', 'landing'], ['14 · hand back', 'back']]
SHOT_NAMES = ['quiet', 'caw', 'snatch', 'carried off', 'the reveal', 'storm clouds', 'the struggle', 'the throw', 'the fall',
              'the dive', 'the blink', 'the glare', 'the Roc lands', 'hand back']


def build():
    retime()
    B.summit_body_path = summit_body_path
    B.puppet_channels = puppet_channels
    B.third_person = third_person
    B.cues = cues
    scene = SpaceScene()
    roc_clip = B.bake(lambda seconds: B.roc_channels(seconds*20/B.PACE), LENGTH*B.PACE/20, B.RATE, .02)
    player_clip = B.bake(lambda seconds: puppet_channels(seconds*20/B.PACE), LENGTH*B.PACE/20, B.RATE*2, .05)
    track = B.bake_track(scene)
    track['ticks'].update(space_columns(scene))
    landing = {'element': 'space', 'script': 'landing_space.js',
               'shots': [[label, beat, plus[0]*B.PACE] if plus else [label, beat] for label, beat, *plus in SHOTS],
               'shot_names': SHOT_NAMES, 'events': events(scene), 'impact': B.r3(IMPACT), 'side': B.r3(SIDE),
               'floor': FLOOR, 'hit_story': HIT}
    landing['shell'] = shell_frames(scene, landing['events'])
    track['landing'] = landing
    return scene, roc_clip, player_clip, track


def write_preview():
    scene, roc_clip, player_clip, track = build()
    PREVIEW.mkdir(parents=True, exist_ok=True)
    compact = lambda d: json.dumps(d, separators=(',', ':')) + '\n'
    (PREVIEW/'storm_roc_intro.animation.json').write_text(compact({'format_version': '1.8.0', 'animations': {
        'animation.storm_roc.intro': roc_clip}}))
    (PREVIEW/'player.animation.json').write_text(compact({'format_version': '1.8.0', 'animations': {
        'animation.storm_roc_intro_player.intro': player_clip}}))
    (PREVIEW/'intro-track.json').write_text(compact(track))
    (PREVIEW/'storm_roc_intro.geo.json').write_text(compact(B.ROC_GEO))
    (PREVIEW/'player.geo.json').write_text(compact(B.PUPPET_GEO))
    (PREVIEW/'player_slim.geo.json').write_text(compact(B.PUPPET_SLIM))
    for source, name in {B.ASSETS/'textures/entity/storm_roc.png': 'storm_roc.png',
                         B.ASSETS/'textures/entity/storm_roc_glowmask.png': 'storm_roc_glowmask.png',
                         B.ASSETS/'geckolib/models/storm_gong.geo.json': 'storm_gong.geo.json',
                         B.ASSETS/'textures/block/storm_gong.png': 'storm_gong.png',
                         HERE/'animatic.js': 'animatic.js', HERE/'landing_space.js': 'landing_space.js'}.items():
        shutil.copyfile(source, PREVIEW/name)
    page = (HERE/'viewer.template.html').read_text(encoding='utf-8')
    page = page.replace('<title>Storm Roc Intro Animatic</title>', '<title>Roc Intro Space</title>')
    page = page.replace('<h1>Storm Roc intro — animatic</h1>',
                        '<h1>Storm Roc intro — Space landing</h1>')
    (PREVIEW/'index.html').write_text(page, encoding='utf-8')
    if B.MINECRAFT.exists():
        import zipfile
        with zipfile.ZipFile(B.MINECRAFT) as jar:
            for kind, name in (('wide', 'steve'), ('slim', 'alex')):
                (PREVIEW/f'skin_{name}.png').write_bytes(jar.read(f'assets/minecraft/textures/entity/player/{kind}/{name}.png'))
    else:
        for name in ('steve', 'alex'):
            for old in (B.PREVIEW/f'skin_{name}.png', B.REPO/f'.local-previews/storm-roc-intro-nature/skin_{name}.png'):
                if old.exists(): shutil.copyfile(old, PREVIEW/f'skin_{name}.png'); break
            else: print(f'no default skin for {name}: the animatic needs skin_{name}.png')
    print(f'Space landing baked: {LENGTH*B.PACE/20:.1f} s, into {PREVIEW.relative_to(B.REPO)}')
    print('real beats:', {k: v for k, v in RT.items()}, 'landing', LANDING, 'length', LENGTH)


if __name__ == '__main__':
    write_preview()
