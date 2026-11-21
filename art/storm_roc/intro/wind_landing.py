#!/usr/bin/env python3
"""The Storm Roc's intro with Wind's landing: build_intro.py's scene from the throw on.

Everything up to the throw is build_intro's, and the fall out of it is Nature's. Then a Wind wizard
catches themselves out of the tumble, snaps their head round to pick a line, and chains Waylay
Dashes down through the air: each one a quick turn of the body into a new direction, the wand
leading, a pressure ring snapping open behind and a cloud burst bleeding off speed, with a widening
cloud wake left hanging in the sky. Left, right, left, each dash slower and lower; the fourth, a
chained dash (its ring doubled, its outer cloud pale blue), comes in slow motion for a moment and
dives for the snow, then flattens into a low skim just over it. Feet down light, a pivot to face
back up the summit, a short skid to a crouch at the arrival as the wake rolls past. Head bowed;
then they look up past the shoulder at the Roc coming down onto its spire, and the scene cuts to
build_intro's Roc-landing insert as before.

The scene is authored in story time: speed() below stretches the last dash into slow motion. The
camera runs in real time. The Roc's flight and wingbeats run on the story clock too.

Run alone, this writes a browser animatic to .local-previews/storm-roc-intro-wind/.
install_landings.py wind installs it into the game, where its effects are
client/roclanding/WindLanding.java. The effects are art/storm_roc/intro/landing_wind.js, drawn with the Wind spells'
block-wind atlas (art/wind/blockwind/textures/blockwind.png). Run from the repository root:
    py -3 art/storm_roc/intro/wind_landing.py
then serve that folder (python -m http.server) and open it.
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

PREVIEW = B.REPO/'.local-previews/storm-roc-intro-wind'
ATLAS = B.REPO/'art/wind/blockwind/textures/blockwind.png'

# ------------------------------------------------------------------ story beats

# In story ticks (one is 0.1 s at normal speed). Until the last dash, story and real ticks agree.
THROW, GROUND = B.THROW, B.GROUND
RIGHT = 234      # out of the tumble: arms flung out, falling feet first
SPOT = 235.2     # the head snaps down: picking the line
DASHES = [237.0, 243.0, 249.0, 255.0]  # Waylay Dashes: left, right, left, and the chained dive
CHAINED = [False, True, False, True]   # the second of each pair is a chained dash: doubled ring, pale blue
D1, D2, D3, D4 = DASHES
LOW = 257.8      # the dive flattens out into a skim just over the snow
TOUCH = 260.8    # feet down, light; a pivot to face back up the summit
STOP = 263.8     # skidded to a crouch at the arrival

ARRIVAL = B.ARRIVAL.copy()           # where you land, and the fight expects you
FLOOR = B.ARRIVAL[1]
NODES = [None, np.array([11.5, 25.5, -13.5]), np.array([-2.5, 15.5, 3.5]), np.array([7.0, 6.5, 17.5])]  # hips at each dash
SKIM_AT = np.array([3.2, 2.0, 25.6])     # hips where the dive levels out
TOUCH_AT = np.array([1.1, 0.0, 29.7])    # where the feet meet the snow (x, z); the skid runs on to the arrival
WAND_TIP = 0.72                          # the animatic's wand: tip 0.72 out along its axis
SPIRE = B.HOME_PERCH + [0, 6, 0]
NATURE_PAD = np.array([0.5, 1.0, 20.0])  # Nature's throw is aimed here: the fall is the same until the dashes
NATURE_BOUNCE = 252


def speed(s):
    """How fast story time runs at story tick s: 1 normal, under 1 slow motion."""
    return 1.0 - .72*slowmo(s)


def slowmo(s): return pulse(s, D4 - 1.1, D4 - .2, D4 + 1.0, D4 + 2.0)


_S = np.arange(THROW, STOP + 14, .005)
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

RT = {k: half(real(v)) for k, v in dict(right=RIGHT, spot=SPOT, d1=D1, d2=D2, d3=D3, d4=D4, low=LOW, touch=TOUCH,
                                         stop=STOP).items()}
RT['chase_cut'] = half(real(D1 - .2))                  # behind and above you, down the zigzag
RT['skim_cut'] = half(real(D4 - 1.6))                  # low on the snow by the arrival, looking up at the last dash
RT['look_cut'] = math.ceil(RT['stop']) + 9             # low behind you, over your wand shoulder
RT['look'] = RT['look_cut'] + 4                        # the head comes up
LANDING = RT['look'] + 12                              # cut: the Roc coming down onto its spire, as before
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
             LENGTH=LENGTH, CRASH=RT['stop'], ROLLED=RT['stop'], TITLE=LANDING + 3, TITLE_END=BACK)
    g['rate'] = rate
    g['flash'] = flash
    g['overlays'] = overlays
    g['PHASES'] = B._phases()
    g['DOWNSTROKES'] = [t for t in range(1, LENGTH + 1) if (B.PHASES[t - 1]/B.BEAT) % 1 < .45 <= (B.PHASES[t]/B.BEAT) % 1]
    g['LIGHTNING'] = [e for e in B.LIGHTNING if e[0] < THROW + 10] + [(TOUCHDOWN + 1, 1, 'bolt', 26, -70)]
    beats = dict(B.BEATS, length=LENGTH, crash=RT['stop'], landing=LANDING, touchdown=TOUCHDOWN, land=LAND, rolled=RT['stop'],
                 back=BACK, up=UP, title=LANDING + 3, title_end=BACK, ret=RETURN, **RT)
    g['BEATS'] = beats
    g['CUTS'] = [B.SUMMIT, THROW, GROUND, RT['chase_cut'], RT['skim_cut'], RT['look_cut'], LANDING, BACK]


class Warped:
    """A path keyed in story ticks, read in real ticks."""

    def __init__(self, inner): self.inner = inner
    def __call__(self, t): return self.inner(clock(t))
    def velocity(self, t, dt=.25): return self.inner.velocity(clock(t), dt)


def summit_body_path():
    """As Nature's: after the throw it wheels out east of its spire in one wide climbing circle and
    comes back round from the north to land on it."""
    perched = B.HOME_PERCH + heading(B.HOME_YAW)@B.roc_local(LENGTH)['body']
    S = B.SUMMIT
    start, land = THROW + 14, clock(LAND)
    circle = [(13/62, [18, 52, -96]), (26/62, [31, 56, -106]), (39/62, [22, 56, -121]), (51/62, [8, 51, -116])]
    keys = [(S - 10, [-74, 8, -160]), (S, [-62, 18, -168]), (S + 16, [-44, 31, -178]), (S + 34, [-20, 41, -176]),
            (S + 52, [2, 47, -162]), (THROW - 10, [10, 49, -142]), (THROW, [10, 49, -128]), (start, [7, 47, -111])]
    keys += [(start + f*(land - start), p) for f, p in circle]
    keys += [(land, [4, 45, -99]), (clock(TOUCHDOWN), perched + [0, .6, -1.2]), (clock(SETTLED), perched)]
    return Warped(B.Path3(keys, end_velocity=np.zeros(3)))


# ------------------------------------------------------------------ the flight line

def hermite(p0, m0, p1, m1, h, u):
    h00, h10, h01, h11 = 2*u**3 - 3*u**2 + 1, u**3 - 2*u**2 + u, -2*u**3 + 3*u**2, u**3 - u**2
    return h00*p0 + h10*h*m0 + h01*p1 + h11*h*m1


def unit(v): return v/np.linalg.norm(v)


def yaw_of(v): return math.degrees(math.atan2(-v[0], v[2]))


def segments(start):
    """The dashes as Hermite pieces (s0, p0, v0, s1, p1, v1), velocities in blocks a story tick:
    each starts fast in its new direction, a little above the chord, and bleeds off speed, sagging
    under it by the next. The last dives to the skim, which runs flat on to the touchdown."""
    nodes = [start] + NODES[1:] + [SKIM_AT]
    times = DASHES + [LOW]
    skim_v = (np.array([TOUCH_AT[0], SKIM_AT[1], TOUCH_AT[2]]) - SKIM_AT)/(TOUCH - LOW)
    out = []
    for i in range(4):
        p0, p1, s0, s1 = nodes[i], nodes[i + 1], times[i], times[i + 1]
        chord = p1 - p0
        avg = np.linalg.norm(chord)/(s1 - s0)
        side = unit(np.cross([0, 1, 0], chord))*np.sign(np.cross([0, 1, 0], chord)@side_hint(i))
        v0 = unit(unit(chord) + [0, .2, 0] + .18*side)*avg*1.75
        v1 = unit(unit(chord) + [0, -.32, 0])*avg*.5 if i < 3 else skim_v*1.3
        out.append((s0, p0, v0, s1, p1, v1))
    return out


def side_hint(i):
    """Which way each dash cuts out sideways: left, right, left, right."""
    return np.array([1.0, 0, 0]) if i % 2 == 0 else np.array([-1.0, 0, 0])


# ------------------------------------------------------------------ the player's body

ORIGINAL_CHANNELS = B.puppet_channels
SCENE = None


def K(s, *rows):
    """A value keyed through rows of (story tick, ease into it, value)."""
    return curve(s, list(rows))[0]


def wand_points(m):
    """The hand and the wand's tip, as the animatic draws the wand."""
    w = m['wand']@T4(*B.PUP.pivot['wand'])@Rx4(math.radians(-70))
    return (w@np.array([0, 0, 0, 1.0]))[:3], (w@np.array([0, 0, WAND_TIP, 1.0]))[:3]


def find_alignment():
    """The wand locator's X rotation that lays the wand along the forearm, pointing out of the fist."""
    best = None
    for w in np.arange(-180, 180, .5):
        m = B.PUP.pose({'wand': {'rotation': np.array([w, 0, 0])}}, B.ENTITY)
        hand, tip = wand_points(m)
        d = (tip - hand)/np.linalg.norm(tip - hand)
        score = float(d@(m['right_arm'][:3, :3]@np.array([0, -1.0, 0])))
        if best is None or score > best[0]: best = (score, float(w))
    return best[1]


ALIGN = find_alignment()


def lead(s):
    """How fully the wand arm leads, pointed along your flight: thrust out with each dash, pulled
    in for the coil before the next, and let go as the feet come down."""
    k = 0.0
    for i, d in enumerate(DASHES):
        end = DASHES[i + 1] - 1.0 if i < 3 else TOUCH + .2
        k = max(k, ramp(s, d - .1, d + .3)*(1 - ramp(s, end, end + .75 if i < 3 else end + 1.0)))
    return k


# Key poses (degrees): arms -X raise forward, +Z out on the right arm and -Z on the left; legs -X
# forward; waist +X bends forward, Y twists; head +X looks down, Y turns.
COIL = dict(right_arm=[-62, 0, -34], left_arm=[-58, 0, 30], right_leg=[-78, 0, 6], left_leg=[-46, 0, -6], waist=[30, 0, 0], head=[8, 0, 0])
SNAP = dict(right_arm=[-150, 0, 6], left_arm=[48, 0, -42], right_leg=[34, 0, 8], left_leg=[6, 0, -10], waist=[-14, 0, 0], head=[-48, 0, 0])
GLIDE = dict(right_arm=[-140, 0, 12], left_arm=[22, 0, -62], right_leg=[12, 0, 10], left_leg=[-30, 0, -10], waist=[6, 0, 0], head=[-34, 0, 0])
SURF = dict(right_arm=[-90, 0, 12], left_arm=[-8, 0, -70], right_leg=[-52, 0, 9], left_leg=[34, 0, -8], waist=[34, 0, 0], head=[-14, 0, 0])
SKID = dict(right_arm=[40, 0, 46], left_arm=[-58, 0, -26], right_leg=[-62, 0, 12], left_leg=[56, 0, -6], waist=[46, 0, 0], head=[14, 0, 0])
HERO = dict(right_arm=[74, 0, 48], left_arm=[-70, 0, -8], right_leg=[-56, 0, 10], left_leg=[62, 0, -6], waist=[64, 0, 0], head=[26, 0, 0])
BONES = ('right_arm', 'left_arm', 'right_leg', 'left_leg', 'waist', 'head')


def turn(i):
    """How far (degrees) the body turns into dash i."""
    return wrap_to(YAWS[i] - (YAWS[i - 1] if i else 0.0))


def wrap_to(a): return (a + 180) % 360 - 180


def posture(s):
    """Every bone at story tick s; the wand arm is pointed along the flight by puppet_channels."""
    c = {}
    def rot(b, r): c[b] = {'rotation': np.array(r, dtype=float)}
    if s < RIGHT:
        # Flung: flailing as you tumble, then flinging your arms out to stop it (Nature's).
        fl = 1 - ramp(s, RIGHT - 5, RIGHT)
        rot('right_arm', mix([-120 + 50*math.sin(s*.6), 0, 60*fl + 20], [-62, 0, 78], 1 - fl))
        rot('left_arm', mix([-120 + 50*math.sin(s*.6 + 2), 0, -60*fl - 20], [-62, 0, -78], 1 - fl))
        rot('right_leg', [-30 + 40*math.sin(s*.5)*fl - 12*(1 - fl), 0, 14*fl + 8*(1 - fl)])
        rot('left_leg', [-30 - 40*math.sin(s*.5)*fl + 24*(1 - fl), 0, -14*fl - 8*(1 - fl)])
        rot('waist', [-20*fl + 10*(1 - fl), 0, 0]); rot('head', [30*fl + 25*(1 - fl), 0, 0])
        return c
    if s >= STOP:
        # Stopped: one knee down, the free hand on the snow, the wand arm swept back and out; the head
        # bowed; then coming up slowly to glare at the spire; and from BACK rising to stand, wand ready.
        settle = kick(s, STOP, .6, 3, 1.0)
        look_s = clock(RT['look'])
        rise = ramp(s, clock(BACK + 1), clock(UP))
        lift = EASE['io'](min(1, max(0, (s - look_s)/6)))
        breath = math.sin((s - STOP)*.55)*(1 - lift*.6)*.6
        hero = 1 - rise
        rot('waist', [(64 + 6*settle - 12*lift + 2.5*breath)*hero, 0, 0])
        rot('right_leg', [-56*hero, 0, 10*hero]); rot('left_leg', [62*hero, 0, -6*hero])
        rot('left_arm', mix([-70 + 3*settle + 14*lift - 1.5*breath, 0, -8], [0, 0, -6], rise))
        rot('right_arm', mix([74 - 4*breath, 0, 48 - 4*settle], [-22, 0, 12], rise))
        rot('head', [(26 + 4*breath*(1 - lift) - 100*lift)*hero - 14*rise, 0, 0])
        return c
    # Out of the tumble: arms out, buffeted; the head snaps down to pick the line; then the dashes.
    rows = {b: [] for b in BONES}
    out = dict(right_arm=[-62, 0, 78], left_arm=[-62, 0, -78], right_leg=[-12, 0, 8], left_leg=[12, 0, -8], waist=[10, 0, 0], head=[25, 0, 0])
    spot = dict(out, head=[62, 0, 0], waist=[20, 0, 0], right_arm=[-70, 0, 72], left_arm=[-74, 0, -70])
    for b in BONES:
        rows[b] += [(RIGHT, 'io', out[b]), (SPOT + .6, 'out', spot[b]), (D1 - 1.0, 'io', spot[b])]
    for i, d in enumerate(DASHES):
        tw = -.35*turn(i)  # wound up against the turn, the head already looking into it; then whipped round
        coil = dict(COIL, waist=[30, tw, 0], head=[COIL['head'][0] if i else 30, .45*turn(i), 0])
        snap = dict(SNAP, waist=[-14, -.3*tw, 0])
        nxt = DASHES[i + 1] if i < 3 else None
        for b in BONES:
            rows[b] += [(d - .15, 'io', coil[b]), (d + .45, 'out', snap[b])]
            if nxt is not None: rows[b] += [(d + 2.4, 'io', GLIDE[b]), (nxt - 1.0, 'io', GLIDE[b])]
    for b in BONES:
        rows[b] += [(LOW - .4, 'io', SURF[b]), (TOUCH, 'io', dict(SURF, waist=[40, 0, 0], right_leg=[-60, 0, 10], left_leg=[40, 0, -8])[b]),
                    (TOUCH + 1.2, 'out', SKID[b]), (STOP, 'io', HERO[b])]
    flut = math.sin(s*1.9)*5, math.sin(s*1.6 + 1)*4
    air = 1 - ramp(s, LOW - 1, LOW)
    for b in BONES:
        v = K(s, *rows[b])
        if b.endswith('leg'): v = v + [flut[1]*air*(1 if b[0] == 'r' else -1), 0, 0]
        if b.endswith('arm') and b[0] == 'l': v = v + [flut[0]*air, 0, 0]
        rot(b, v)
    return c


def puppet_channels(t):
    if t < THROW: return ORIGINAL_CHANNELS(t)
    s = clock(t)
    c = posture(s)
    k = lead(s)
    if SCENE is not None and k > 0:
        # The wand leads: the arm points along the flight. The arm's direction in the body's frame is
        # (0, -cos A, sin A) for a rotation of -A, so A follows from the flight seen from the body.
        r = SCENE.rotation(s)
        v = SCENE.hips(s + .08) - SCENE.hips(s - .08)
        if s >= LOW: v = v + [0, -.25*np.linalg.norm(v), 0]  # skimming: pointed a touch down at the snow ahead
        wb = r.T@unit(v)
        a = math.degrees(math.atan2(wb[2], -wb[1]))
        base = c['right_arm']['rotation']
        c['right_arm'] = {'rotation': base*(1 - k) + np.array([-a, 0, 8])*k}
        c['wand'] = {'rotation': np.array([ALIGN*k, 0, 0])}
    return c


class WindScene(B.Scene):

    def ballistic(self):
        """Nature's throw: from the talons at THROW on a lob toward its pad, so the fall is the same."""
        (_, _), w = self.roc_world(THROW)
        p0 = w['grip']
        land = NATURE_PAD + [0, .6875 + B.HIPS[1], 0]
        tau = (NATURE_BOUNCE - THROW)/20
        v0 = (land - p0 - .5*np.array([0, -B.GRAVITY, 0])*tau*tau)/tau
        return p0, v0

    def __init__(self):
        super().__init__()
        self.segs = segments(self.thrown(D1))

    def hips(self, s):
        """Where your hips are at story tick s, from the throw on."""
        if s < D1: return self.thrown(s)
        for s0, p0, v0, s1, p1, v1 in self.segs:
            if s < s1: return hermite(p0, v0, p1, v1, s1 - s0, (s - s0)/(s1 - s0))
        skim_end = np.array([TOUCH_AT[0], SKIM_AT[1], TOUCH_AT[2]])
        if s < TOUCH:
            s0, p0, v0, s1, p1, v1 = self.segs[-1]
            return hermite(SKIM_AT, v1, skim_end, v1*.7, TOUCH - LOW, (s - LOW)/(TOUCH - LOW))
        u = min(1, (s - TOUCH)/(STOP - TOUCH))
        end = np.array([ARRIVAL[0], SKIM_AT[1], ARRIVAL[2]])
        v = self.segs[-1][5]*.7*(STOP - TOUCH)
        # Skidding on to the arrival, easing to a stop.
        return hermite(skim_end, v/(STOP - TOUCH), end, np.zeros(3), STOP - TOUCH, u)

    def rotation(self, s):
        if s < RIGHT:
            # One fast forward tumble out of its grip, caught upright as you fling your arms out.
            u = (s - THROW)/(RIGHT - THROW)
            spin = heading(6*wobble(s, 31, 1.3), 88 + 272*EASE['out'](u), 10*wobble(s, 32, 1.1)*(1 - u))
            return slerp(self.held(THROW), spin, ramp(s, THROW, THROW + 4))
        return heading(YAW(s), PITCH(s), ROLL(s))

    def body_at(self, s):
        r = self.rotation(s)
        return self.hips(s) - r@B.HIPS, r

    def clearance(self, s):
        """How high your feet skim over the snow: a hand's width, then down onto it."""
        return .32*(1 - ramp(s, TOUCH - .9, TOUCH))

    def player(self, t):
        if t < THROW: return super().player(t)
        s = clock(t)
        root, r = self.body_at(s)
        if s < LOW - 1.6: return root, r
        low = self.lowest(root, r, t)
        planted = root + [0, FLOOR + self.clearance(s) - low, 0]
        k = ramp(s, LOW - 1.6, LOW - .2)
        root = mix(root, planted, k)
        if root[1] < planted[1] and s < LOW: root = planted
        return root, r

    def tip(self, t):
        return wand_points(self.puppet_pose(t))[1]


SCENE_SEGS = None
YAWS = None


def build_body_curves():
    """The body's yaw, pitch and roll through the dashes: a head-first turn into each, whipped
    round with an overshoot, leaning hard into it; banked into the turn; easing upright as it
    slows; then the skim, the touchdown and the pivot round to face the spire."""
    global YAWS, YAW, PITCH, ROLL
    YAWS = [yaw_of(seg[2]) for seg in SCENE_SEGS]
    for i in range(1, 4):  # keep yaws continuous
        while YAWS[i] - YAWS[i - 1] > 180: YAWS[i] -= 360
        while YAWS[i] - YAWS[i - 1] < -180: YAWS[i] += 360
    yaw = [(RIGHT, 'io', 0.0), (D1 - 1.0, 'io', 0.0)]
    pitch = [(RIGHT, 'io', 0.0), (RIGHT + 2, 'out', 22.0), (D1 - 1.0, 'io', 24.0)]
    roll = [(RIGHT, 'io', 0.0), (D1 - 1.0, 'io', 0.0)]
    prev = 0.0
    for i, d in enumerate(DASHES):
        y = YAWS[i]
        bank = max(-1, min(1, (y - prev)/45))*24
        nxt = DASHES[i + 1] if i < 3 else LOW
        yaw += [(d - .15, 'io', prev + .18*(y - prev)), (d + .55, 'back', y), (nxt - 1.0, 'io', y)]
        pitch += [(d - .15, 'io', 8.0), (d + .4, 'out', 66.0 if i < 3 else 72.0)]
        roll += [(d - .15, 'io', -.15*bank), (d + .4, 'out', bank), (d + 2.2, 'io', .3*bank)]
        if i < 3:
            pitch += [(d + 2.6, 'io', 52.0), (nxt - 1.0, 'io', 40.0)]
            roll += [(nxt - 1.0, 'io', .2*bank)]
        prev = y
    skim_yaw = yaw_of(np.array([TOUCH_AT[0], 0, TOUCH_AT[2]]) - [SKIM_AT[0], 0, SKIM_AT[2]])
    yaw += [(LOW, 'io', skim_yaw), (TOUCH - .2, 'io', skim_yaw), (TOUCH + 1.5, 'out', 180.0)]
    pitch += [(D4 + 2.0, 'io', 58.0), (LOW, 'io', 24.0), (TOUCH, 'io', 16.0), (TOUCH + 1.2, 'out', -12.0), (STOP, 'io', 0.0)]
    roll += [(LOW, 'io', -6.0), (TOUCH, 'io', 0.0), (TOUCH + .8, 'out', -14.0), (STOP, 'io', 0.0)]
    YAW = lambda s: float(K(s, *yaw)) + 2*math.sin(s*.9)*(1 - ramp(s, TOUCH, STOP))
    PITCH = lambda s: float(K(s, *pitch))
    ROLL = lambda s: float(K(s, *roll)) + 3*math.sin(s*.7)*(1 - ramp(s, LOW, TOUCH))


YAW = PITCH = ROLL = None

# ------------------------------------------------------------------ the camera

ORIGINAL_THIRD = B.third_person


def chase_centre(scene, s):
    """Your hips as a lagging camera follows them: each dash snaps you away from it."""
    w, acc = 0.0, np.zeros(3)
    for k in range(8):
        q = math.exp(-k/2.2)
        acc += q*scene.hips(max(D1 - 1, s - k*.13)); w += q

    return acc/w


DOWN_LINE = None  # the zigzag's overall heading, flattened


def third_person(scene, t):
    if t < GROUND or t >= LANDING: return ORIGINAL_THIRD(scene, t)
    s = clock(t)
    root, r = scene.player(t)
    chest = root + r@B.CHEST
    if t < RT['chase_cut']:
        # Falling beside you, a little ahead and below: you catch yourself out of the tumble, your
        # head snaps down to pick the line (a punch in), you coil.
        p0, v0 = scene.throw
        v = v0 + np.array([0, -B.GRAVITY, 0])*(t - THROW)/20
        d = v*np.array([1, 0, 1]); d = d/np.linalg.norm(d)
        side = np.cross([0, 1, 0], d)
        eye = chest + side*(4.2 - .9*ramp(t, GROUND, RT['chase_cut'])) + d*2.6 + [0, -1.2 - .5*ramp(t, RIGHT, RT['chase_cut']), 0]
        target = mix(chest, chest + v/np.linalg.norm(v)*6, .08 + .06*ramp(t, RIGHT, RT['chase_cut']))
        yaw, pitch = look(eye, target)
        sh = shake(t, 40, .9 + .8*kick(t, SPOT + .5, 1, 1.5, 1.5)**2)
        fov = 56 - 9*pulse(t, SPOT, SPOT + .7, D1 - 1.2, D1)
        return eye, yaw + sh[0], pitch + sh[1], sh[2]*2 - 6*math.sin(t*.1), float(fov)
    if t < RT['skim_cut']:
        # Behind and above you as you zigzag down, lagging, so each dash snaps you off across the
        # frame: the rings open toward us, the wakes hang in the sky between us and you.
        c = chase_centre(scene, s)
        sway = np.cross([0, 1, 0], DOWN_LINE)*(1.2*math.sin((t - RT['chase_cut'])*.12))
        eye = c - DOWN_LINE*4.2 + [0, -.9, 0] + sway
        target = mix(chest, c, .35) + DOWN_LINE*1.5 + [0, -.6, 0]
        yaw, pitch = look(eye, target)
        jolt = sum(kick(t, real(d), 1, 2.5, 1.4) for d in DASHES[1:3] if t >= real(d))
        sh = shake(t, 43, .5 + .5*abs(jolt))
        fov = 56 + 7*sum(
math.exp(-(t - real(d))/3)*(t >= real(d)) for d in DASHES[:3])
        return eye, yaw + sh[0], pitch + sh[1] + 1.5*jolt, sh[2]*1.5 + 4*math.sin(t*.13), float(fov)
    if t < RT['look_cut']:
        # Low on the snow beside the arrival, on your wand side: the last dash in slow motion up in
        # the sky, the dive, the skim in toward us, feet down, the pivot, the skid, the wake rolling past.
        push = ramp(t, RT['touch'], RT['look_cut'])
        eye = ARRIVAL + [5.6, .55, -2.2] + [-1.4*push, .1*push, .9*push]

        target = mix(chest, ARRIVAL + [0, .7, 0], .45*ramp(t, RT['touch'] - 1, RT['stop']))
        yaw, pitch = look(eye, target)
        sh = shake(t, 45, .3 + .8*math.exp(-max(0, t - RT['touch'])/4)*(t >= RT['touch']))
        fov = 38 + 22*ramp(t, RT['d4'] + 2, RT['touch']) - 4*ramp(t, RT['stop'], RT['look_cut'])
        return eye, yaw + sh[0], pitch + sh[1], sh[2]*1.2, float(fov)
    # Low behind you, over your wand shoulder: head bowed; then as it comes up, we tilt up with it
    # past your shoulder to the Roc coming back round to its spire, closing in on it (Nature's).
    up = ramp(t, RT['look'] - 1, RT['look'] + 7)
    eye = ARRIVAL + [1.2, .75, 3.6] + [-.15*up, -.15*up, .3*up]
    (_, _), w = scene.roc_world(t)
    head = scene.eye(t)
    aim = head + (w['body'] - head)/np.linalg.norm(w['body'] - head)*14
    target = mix(chest + [0, .2, -1.5], aim, .05 + .9*up**1.3)
    yaw, pitch = look(eye, target)
    sh = shake(t, 47, .2)
    fov = 50 - 12*up
    return eye, yaw + sh[0], pitch + sh[1], sh[2] - 2*up, float(fov)


# ------------------------------------------------------------------ effects data

def dash_data(scene):
    """Each dash for the animatic: when (story clock, game ticks), where it starts, which way."""
    out = []
    for i, (s0, p0, v0, s1, p1, v1) in enumerate(scene.segs):
        out.append({'clock': round(s0*B.PACE, 2), 'tick': round(real(s0)*B.PACE, 2), 'chained': CHAINED[i],
                    'at': B.r3(p0), 'dir': B.r3(unit(v0)), 'end': round(s1*B.PACE, 2), 'speed': round(float(np.linalg.norm(v0)), 3)})
    return out


def flight_samples(scene):
    """Your hips and the wand's tip every tenth of a story tick from the first dash to the stop,
    as [story clock (game ticks), hips xyz, tip xyz]."""
    out = []
    s = D1 - .5
    while s <= STOP + .05:
        t = real(s)
        root, r = scene.player(t)
        hip = root + r@B.HIPS
        out.append([round(s*B.PACE, 2)] + B.r3(hip) + B.r3(scene.tip(t)))
        s += .1
    return out


def wind_columns(scene):
    keys = ('n_clock', 'w_speed', 'w_slow', 'w_lead')
    cols = {k: [] for k in keys}
    for g in range(LENGTH*B.PACE + 1):
        t = g/B.PACE
        s = clock(t)
        put = lambda k, v: cols[k].append(round(float(v), 3))
        put('n_clock', s*B.PACE)
        put('w_speed', .45*pulse(t, GROUND + 1, GROUND + 4, RT['chase_cut'] - .5, RT['chase_cut']) +
            .35*pulse(t, RT['chase_cut'], RT['chase_cut'] + .5, RT['skim_cut'] - .5, RT['skim_cut']))
        put('w_slow', slowmo(s))
        put('w_lead', lead(s)*(s < TOUCH))
    return cols


ORIGINAL_CUES = B.cues


def cues(scene):
    out = [c for c in ORIGINAL_CUES(scene) if c['cue'] not in ('crash',)]
    beats = [(RT['right'] - 2, 'catch_yourself')]
    for i, d in enumerate(DASHES):
        beats.append((real(d), 'waylay_dash_chained' if CHAINED[i] else 'waylay_dash'))
    beats += [(real(D4) - 1, 'slowmo_in'), (RT['low'], 'skim'), (RT['touch'], 'light_landing'), (RT['touch'] + 1, 'skid'), (RT['stop'], 'skid_stop')]
    out += [{'tick': int(round(t)), 'cue': c, 'from': 'player'} for t, c in beats]
    return sorted(out, key=lambda c: c['tick'])


# ------------------------------------------------------------------ baking

SHOTS = [['1 · quiet', 0], ['2 · caw', 'caw'], ['3 · snatch', 'snatch'], ['4 · carried', 'grip', 8], ['5 · reveal', 'look_up'],
         ['6 · clouds', 'cloud'], ['7 · struggle', 'summit'], ['8 · throw', 'throw'], ['9 · the fall', 'ground'],
         ['10 · the zigzag', 'chase_cut'], ['11 · the skim', 'skim_cut'], ['12 · the glare', 'look_cut'],
         ['13 · the Roc lands', 'landing'], ['14 · hand back', 'back']]
SHOT_NAMES = ['quiet', 'caw', 'snatch', 'carried off', 'the reveal', 'storm clouds', 'the struggle', 'the throw', 'the fall',
              'the zigzag', 'the skim', 'the glare', 'the Roc lands', 'hand back']


def build():
    global SCENE, SCENE_SEGS, DOWN_LINE
    retime()
    B.summit_body_path = summit_body_path
    B.puppet_channels = puppet_channels
    B.third_person = third_person
    B.cues = cues
    scene = WindScene()
    SCENE_SEGS = scene.segs
    line = NODES[3] - scene.segs[0][1]
    DOWN_LINE = unit(line*np.array([1, 0, 1]))
    build_body_curves()
    SCENE = scene
    roc_clip = B.bake(lambda seconds: B.roc_channels(seconds*20/B.PACE), LENGTH*B.PACE/20, B.RATE, .02)
    player_clip = B.bake(lambda seconds: puppet_channels(seconds*20/B.PACE), LENGTH*B.PACE/20, B.RATE*2, .05)
    track = B.bake_track(scene)
    track['ticks'].update(wind_columns(scene))
    track['landing'] = {'element': 'wind', 'script': 'landing_wind.js', 'atlas': 'blockwind.png',
                        'shots': [[label, beat, plus[0]*B.PACE] if plus else [label, beat] for label, beat, *plus in SHOTS],
                        'shot_names': SHOT_NAMES, 'arrival': ARRIVAL.tolist(), 'floor': FLOOR,
                        'dashes': dash_data(scene), 'flight': flight_samples(scene),
                        'low': round(LOW*B.PACE, 2), 'touch': round(TOUCH*B.PACE, 2), 'stop': round(STOP*B.PACE, 2),
                        'touch_at': B.r3([TOUCH_AT[0], FLOOR, TOUCH_AT[2]])}
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
                         ATLAS: 'blockwind.png',
                         HERE/'animatic.js': 'animatic.js', HERE/'landing_wind.js': 'landing_wind.js'}.items():
        shutil.copyfile(source, PREVIEW/name)
    page = (HERE/'viewer.template.html').read_text(encoding='utf-8')
    page = page.replace('<title>Storm Roc Intro Animatic</title>', '<title>Roc Intro Wind</title>')
    page = page.replace('<h1>Storm Roc intro — animatic</h1>', '<h1>Storm Roc intro — Wind landing</h1>')
    (PREVIEW/'index.html').write_text(page, encoding='utf-8')
    if B.MINECRAFT.exists():
        import zipfile
        with zipfile.ZipFile(B.MINECRAFT) as jar:
            for kind, name in (('wide', 'steve'), ('slim', 'alex')):
                (PREVIEW/f'skin_{name}.png').write_bytes(jar.read(f'assets/minecraft/textures/entity/player/{kind}/{name}.png'))
    else:
        for name in ('steve', 'alex'):
            old = B.PREVIEW/f'skin_{name}.png'
            nature = B.REPO/'.local-previews/storm-roc-intro-nature'/f'skin_{name}.png'
            if old.exists(): shutil.copyfile(old, PREVIEW/f'skin_{name}.png')
            elif nature.exists(): shutil.copyfile(nature, PREVIEW/f'skin_{name}.png')
            else: print(f'no default skin for {name}: the animatic needs skin_{name}.png')
    print(f'Wind landing baked: {LENGTH*B.PACE/20:.1f} s, wand alignment {ALIGN:.1f} deg, into {PREVIEW.relative_to(B.REPO)}')
    print('real beats:', {k: v for k, v in RT.items()}, 'landing', LANDING, 'length', LENGTH)
    print('dash yaws:', [round(y, 1) for y in YAWS])


if __name__ == '__main__':
    write_preview()
