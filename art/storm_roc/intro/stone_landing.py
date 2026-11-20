#!/usr/bin/env python3
"""The Storm Roc's intro with Stone's landing: build_intro.py's scene from the throw on.

Everything up to the throw is build_intro's. Then a Stone wizard catches themselves in the fall,
spots the summit below and thrusts the wand down at it: Gathered Mass. Time slows as the snow ahead
breaks open and rocks and earth tear up out of it, trailing snow and grit, flying up to them and
packing round them into one big round boulder, several blocks across, with them inside; the rocks
braking their fall until the ball hangs in the air. As the last of it closes over the top, time slows for a moment. Then the
weight takes it: the boulder rolls over and drops onto the arrival, and the summit cracks open
round the crater, snow and rubble thrown up. A beat; the ball cracks, and breaks apart, the rock
falling away round them as they push themselves up out of it into a kneel. Their head comes up to
glare at the spire, and the scene cuts to the Roc landing on it as before.

The scene is authored in story time: the speed curve below stretches parts of it into slow motion.
The camera runs in real time. The Roc's flight and wingbeats run on the story clock too.

The ball is built of oriented boxes only (chunks on a sphere), so the game can draw it the same way:
the track carries the ball's centre and roll per tick (st_ball, st_roll) and each chunk's slot,
boxes, cracks, source on the summit and timings (track['landing']['chunks']).

Run alone, this writes a browser animatic to .local-previews/storm-roc-intro-stone/.
install_landings.py stone installs it into the game, where its effects are
client/roclanding/StoneLanding.java. Its effects are art/storm_roc/intro/landing_stone.js, which the animatic loads
through track['landing']. Run from the repository root:
    py -3 art/storm_roc/intro/stone_landing.py
then serve that folder (python -m http.server 8413) and open it.
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

PREVIEW = B.REPO/'.local-previews/storm-roc-intro-stone'

# ------------------------------------------------------------------ story beats

# In story ticks (one is 0.1 s at normal speed). Until the slow motion, story and real ticks agree.
THROW, GROUND = B.THROW, B.GROUND
RIGHT = 234      # out of the tumble: arms flung out, falling feet first
SPOT = 236       # the head snaps down: the ground
AIM = 238        # the wand thrust down at the summit: Gathered Mass
RIP = 239.5      # the snow breaks open and the first rocks tear up out of it
FIRST = 242      # the first chunk packs on, under you
SEAL = 250.5     # the last chunk closes the top: one round boulder; a short slow motion
DROP = 252       # the weight takes it: it rolls over and drops
IMPACT = 260     # onto the arrival; the summit cracks open round it
CRACK = 268      # a beat later the ball cracks
BREAK = 270.5    # and breaks apart, falling away as you push yourself up
STOP = BREAK + 8.5  # risen to a kneel; then the shared ending

ARRIVAL = B.ARRIVAL.copy()            # where you end up, and the fight expects you
FLOOR = ARRIVAL[1]
CONTACT = ARRIVAL + [0, 0, -.7]       # where it hits; the skid carries it back onto the arrival
QUARRY = np.array([1.5, FLOOR, 7.0])  # where the rocks tear up out of the summit
HANG = np.array([1.0, 26.2, 5.0])     # your hips as the ball closes, held up by the stream
RADIUS = 1.75                         # the ball's outer radius: 3.5 blocks across
SINK = .35                            # how far it beds into the crater
REST = ARRIVAL + [0, RADIUS - SINK, 0]  # the ball's centre, resting
WAND_TIP = 0.72                       # the animatic's wand: tip 0.72 out along its axis
SPIRE = B.HOME_PERCH + [0, 6, 0]


def bullet(s): return pulse(s, SEAL - .9, SEAL - .3, SEAL + .5, SEAL + 1.0)
def hitstop(s): return pulse(s, IMPACT - .05, IMPACT, IMPACT + .2, IMPACT + .35)
def gather(s): return pulse(s, RIP - .6, RIP, FIRST + 1, FIRST + 3.5)  # the rocks tearing up and flying at you


def speed(s):
    """How fast story time runs at story tick s: 1 normal, under 1 slow motion."""
    return 1.0 - .55*gather(s) - .75*bullet(s) - .85*hitstop(s)


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


# ------------------------------------------------------------------ the ball's chunks

# Colours: Stone's natural cool greys (art/stone/natural-gray), cobble, and earth torn up with it.
GREYS = [[.62, .64, .64], [.55, .57, .58], [.69, .71, .7], [.5, .52, .53], [.58, .61, .6]]
COBBLE = [.44, .46, .47]
DIRT = [[.46, .38, .3], [.4, .33, .26]]
LIGHT = [.8, .82, .81]
SNOWY = [.9, .92, .95]
N_CHUNKS = 64


def rng(seed): return np.random.default_rng(seed)


def chunk_shapes():
    """The ball's chunks on a Fibonacci sphere, each in its slot frame (x, y across the surface,
    z out along the normal): a slab and a lump or two on its outer face, and a crack line across it."""
    out = []
    golden = math.pi*(3 - math.sqrt(5))
    for i in range(N_CHUNKS):
        r = rng(700 + i)
        y = 1 - 2*(i + .5)/N_CHUNKS
        ring = math.sqrt(1 - y*y)
        n = np.array([math.cos(golden*i)*ring, y, math.sin(golden*i)*ring])
        x = np.cross([0, 1, 0], n)
        if np.linalg.norm(x) < .1: x = np.cross([1, 0, 0], n)
        x /= np.linalg.norm(x)
        yv = np.cross(n, x)
        tw = r.uniform(0, math.tau)  # a twist about the normal, so the seams don't line up
        x, yv = x*math.cos(tw) + yv*math.sin(tw), -x*math.sin(tw) + yv*math.cos(tw)
        w, h, d = r.uniform(.4, .5), r.uniform(.38, .48), r.uniform(.2, .28)
        pick = r.random()
        col = DIRT[i % 2] if pick < .22 else COBBLE if pick < .4 else GREYS[i % len(GREYS)]
        boxes = [[[-w, -h, -d], [w, h, d], col]]
        for k in range(int(r.integers(1, 3))):  # lumps on the outer face
            lw, lh = w*r.uniform(.25, .45), h*r.uniform(.25, .45)
            cx, cy = r.uniform(-1, 1)*(w - lw), r.uniform(-1, 1)*(h - lh)
            depth = r.uniform(.05, .1)
            p2 = r.random()
            lump = LIGHT if p2 < .35 else COBBLE if p2 < .6 else DIRT[k % 2] if p2 < .75 else SNOWY if p2 < .85 else GREYS[(i + k + 2) % 5]
            boxes.append([[cx - lw, cy - lh, d - .02], [cx + lw, cy + lh, d + depth], lump])
        cracks, p = [], np.array([r.uniform(-.3, .3)*w, r.uniform(-.3, .3)*h, d + .006])
        ang = r.uniform(0, math.tau)
        for seg in range(3):  # a jagged crack across the face, three segments from near the middle out
            ang += r.uniform(-.8, .8)
            q = p + [math.cos(ang)*w*.55, math.sin(ang)*h*.55, 0]
            q[0], q[1] = np.clip(q[0], -w, w), np.clip(q[1], -h, h)
            cracks.append([np.round(p, 4).tolist(), np.round(q, 4).tolist(), seg])
            p = q
        r3 = lambda v: [round(float(a), 4) for a in v]
        out.append({'n': n, 'basis': [r3(x), r3(yv), r3(n)], 'centre': r3(n*(RADIUS - d)),
                    'boxes': [[r3(a), r3(b), c] for a, b, c in boxes], 'cracks': cracks, 'depth': d})
    return out


SHAPES = chunk_shapes()


def order_chunks():
    """When each chunk packs on: the side facing the quarry first (the rocks come from there), the
    far top last, closing on SEAL."""
    toward = np.array([0, -1.0, .45]); toward /= np.linalg.norm(toward)
    keys = [float(sh['n']@toward) + .25*rng(900 + i).uniform(-1, 1) for i, sh in enumerate(SHAPES)]
    rank = np.argsort(np.argsort(-np.array(keys)))
    for i, sh in enumerate(SHAPES):
        u = rank[i]/(N_CHUNKS - 1)
        sh['at'] = FIRST + (SEAL - FIRST)*(.85*u + .15*u*u)


order_chunks()
ARRIVALS = sorted(sh['at'] for sh in SHAPES)


def jolts(s):
    """The knocks of the chunks packing on, felt in the body (every fourth one)."""
    return sum(kick(s, at, 1.0, 1.2, 2.6) for at in ARRIVALS[::4])


# ------------------------------------------------------------------ real beats: cuts and the camera

RT = {k: half(real(v)) for k, v in dict(right=RIGHT, spot=SPOT, aim=AIM, rip=RIP, first=FIRST, seal=SEAL, drop=DROP,
                                         impact=IMPACT, crack=CRACK, rise=BREAK, stop=STOP).items()}
RT['quarry_cut'] = half(real(RIP + .5))        # low by the quarry: the rocks tearing up out of the summit
RT['shell_cut'] = half(real(FIRST + 3))       # out beside you: the ball packing round you
RT['crash_cut'] = half(real(DROP + 1.5))        # beyond the arrival: the boulder coming in, the crater
RT['shed_cut'] = RT['crack'] - 1                # low in front: the ball cracks and breaks apart
RT['look_cut'] = math.ceil(RT['stop']) + 3      # low behind you
RT['look'] = RT['look_cut'] + 4                 # the head comes up
LANDING = RT['look'] + 12                       # cut: the Roc coming down onto its spire, as before
TOUCHDOWN = LANDING + 2
LAND = TOUCHDOWN - 16
SETTLED = LAND + 50
BACK = LANDING + 32
UP = BACK + 6
RETURN = UP + 1
LENGTH = RETURN + 20
LOOK_S = clock(RT['look'])

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
    o['bars'] = o['bars']*(1 + .3*bullet(clock(t)))  # the letterbox closes in on the slow motion
    return o


def retime():
    g = vars(B)
    g.update(LANDING=LANDING, TOUCHDOWN=TOUCHDOWN, LAND=LAND, SETTLED=SETTLED, BACK=BACK, UP=UP, RETURN=RETURN,
             LENGTH=LENGTH, CRASH=RT['impact'], ROLLED=RT['stop'], TITLE=LANDING + 3, TITLE_END=BACK)
    g['rate'] = rate
    g['flash'] = flash
    g['overlays'] = overlays
    g['PHASES'] = B._phases()
    g['DOWNSTROKES'] = [t for t in range(1, LENGTH + 1) if (B.PHASES[t - 1]/B.BEAT) % 1 < .45 <= (B.PHASES[t]/B.BEAT) % 1]
    g['LIGHTNING'] = [e for e in B.LIGHTNING if e[0] < THROW + 10] + [(TOUCHDOWN + 1, 1, 'bolt', 26, -70)]
    beats = dict(B.BEATS, length=LENGTH, crash=RT['impact'], landing=LANDING, touchdown=TOUCHDOWN, land=LAND, rolled=RT['stop'],
                 back=BACK, up=UP, title=LANDING + 3, title_end=BACK, ret=RETURN, **RT)
    g['BEATS'] = beats
    g['CUTS'] = [B.SUMMIT, THROW, GROUND, RT['quarry_cut'], RT['shell_cut'], RT['crash_cut'], RT['shed_cut'],
                 RT['look_cut'], LANDING, BACK]


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
SCENE = None
AIMS = {}


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


def aim_arm(s):
    """The wand arm's rotation that points the wand at the quarry from the body's place at story tick s."""
    key = round(s, 3)
    if key in AIMS: return AIMS[key]
    root, r = SCENE.body_at(s)
    base = np.eye(4); base[:3, :3] = r; base[:3, 3] = root

    def miss(ax, az):
        c = posture(s); c['right_arm'] = {'rotation': np.array([ax, 0, az])}
        c['wand'] = {'rotation': np.array([ALIGN, 0, 0])}
        hand, tip = wand_points(B.PUP.pose(c, base@B.ENTITY))
        d = (tip - hand)/np.linalg.norm(tip - hand)
        want = (QUARRY - tip)/np.linalg.norm(QUARRY - tip)
        return 1 - float(d@want)
    _, ax, az = min((miss(ax, az), ax, az) for ax in range(-232, 41, 8) for az in range(-30, 71, 10))
    for step in (3, 1):
        _, ax, az = min((miss(ax + i*step, az + k*step), ax + i*step, az + k*step) for i in (-2, -1, 0, 1, 2) for k in (-2, -1, 0, 1, 2))
    AIMS[key] = (ax, az)
    return ax, az


def crouch(s):
    """The crouch inside the ball, from the impact on: (waist, head, right leg, left leg, left arm,
    right arm), with the push up out of it into a kneel as it breaks."""
    hit = kick(s, IMPACT, 1.0, 1.6, 1.4)
    up = EASE['io'](min(1, max(0, (s - BREAK)/6.5)))
    shrug = kick(s, BREAK + .2, 1.0, 1.4, 1.6) + .7*kick(s, BREAK + 2.4, 1.0, 1.4, 1.8)
    waist = 74 + 10*hit - 36*up
    head = 34 + 6*hit + 6*up
    rleg = [-62 - 6*hit, 0, 10]
    lleg = [70 + 4*hit - 2*up, 0, -6]
    larm = mix([-64 + 4*hit, 0, -10], [-34, 0, -12], up) + [0, 0, -14*shrug]
    rarm = mix([70 - 6*hit, 0, 46], [24, 0, 22], up) + [0, 0, 18*shrug]
    return waist, head, rleg, lleg, larm, rarm


def posture(s):
    """Every bone but the aimed wand arm at story tick s (degrees; arms -X raise forward, +Z out on
    the right arm and -Z on the left; legs -X forward; waist +X bends forward; head +X looks down)."""
    c = {}
    def rot(b, r): c[b] = {'rotation': np.array(r, dtype=float)}
    if s < RIGHT:
        # Flung: flailing as you tumble, then flinging your arms out to stop it.
        fl = 1 - ramp(s, RIGHT - 5, RIGHT)
        rot('right_arm', mix([-120 + 50*math.sin(s*.6), 0, 60*fl + 20], [-62, 0, 78], 1 - fl))
        rot('left_arm', mix([-120 + 50*math.sin(s*.6 + 2), 0, -60*fl - 20], [-62, 0, -78], 1 - fl))
        rot('right_leg', [-30 + 40*math.sin(s*.5)*fl - 12*(1 - fl), 0, 14*fl + 8*(1 - fl)])
        rot('left_leg', [-30 - 40*math.sin(s*.5)*fl + 24*(1 - fl), 0, -14*fl - 8*(1 - fl)])
        rot('waist', [-20*fl + 10*(1 - fl), 0, 0]); rot('head', [30*fl + 25*(1 - fl), 0, 0])
        return c
    if s < SEAL - 1.5:
        # Arms out, buffeted; the head snaps down to the ground; the wand arm wound up over the head
        # and thrust down at it (aim_arm takes it from there), the free arm out wide for balance;
        # curled toward the ground, legs braced together, knocked about as the rock packs on.
        flut = math.sin(s*1.9)*4*(1 - ramp(s, AIM, FIRST)), math.sin(s*1.6 + 1)*3
        jb = jolts(s)
        rot('right_arm', K(s, (RIGHT, 'io', [-62, 0, 78]), (SPOT + .5, 'io', [-80, 0, 70]), (AIM - .6, 'io', [-178, 0, 30]),
                           (AIM + .4, 'out', [-40, 0, 20])) + [flut[0], 0, 0])
        rot('left_arm', K(s, (RIGHT, 'io', [-62, 0, -78]), (AIM - .6, 'io', [-110, 0, -40]), (AIM + .6, 'out', [-30, 0, -84]),
                          (FIRST + 3, 'io', [-46, 0, -70])) + [-flut[0] + 4*jb, 0, -3*jb])
        rot('waist', K(s, (RIGHT, 'io', [10, 0, 0]), (SPOT + .8, 'out', [20, 0, 0]), (AIM - .6, 'io', [-8, 0, 0]),
                       (AIM + .5, 'out', [30, 0, 0]), (FIRST + 2, 'io', [22, 0, 0])) + [-5*jb, 0, 0])
        rot('head', K(s, (RIGHT, 'io', [25, 0, 0]), (SPOT, 'io', [22, 0, 0]), (SPOT + .7, 'out', [60, 0, 0]),
                      (AIM - .6, 'io', [40, 0, 0]), (AIM + .5, 'out', [58, 0, 0]), (FIRST + 3, 'io', [48, 0, 0])) + [-4*jb, 0, 0])
        rot('right_leg', K(s, (RIGHT, 'io', [-12, 0, 8]), (AIM, 'io', [-18, 0, 6]), (FIRST, 'io', [-22, 0, 5])) + [flut[1] + 4*jb, 0, 0])
        rot('left_leg', K(s, (RIGHT, 'io', [12, 0, -8]), (AIM, 'io', [6, 0, -6]), (FIRST, 'io', [-4, 0, -5])) - [flut[1] - 4*jb, 0, 0])
        return c
    if s < IMPACT:
        # The ball closing over you: pulled into a tuck inside it; just before the ground, bracing
        # into the landing crouch.
        T0, T1 = SEAL - 1.5, SEAL + .5
        land = ramp(s, IMPACT - 1.6, IMPACT)
        waist = K(s, (T0, 'io', [22, 0, 0]), (T1, 'out', [52, 0, 0]))
        head = K(s, (T0, 'io', [48, 0, 0]), (T1, 'out', [40, 0, 0]))
        rleg = K(s, (T0, 'io', [-22, 0, 5]), (T1, 'out', [-112, 0, 10]))
        lleg = K(s, (T0, 'io', [-4, 0, -5]), (T1, 'out', [-106, 0, -10]))
        larm = K(s, (T0, 'io', [-46, 0, -70]), (T1, 'out', [-100, 0, 30]))
        rarm = K(s, (T0, 'io', [-40, 0, 20]), (T1, 'out', [-96, 0, -34]))
        w, h, rl, ll, la, ra = crouch(IMPACT)
        rot('waist', mix(waist, [w - 10, 0, 0], land)); rot('head', mix(head, [h, 0, 0], land))
        rot('right_leg', mix(rleg, rl, land)); rot('left_leg', mix(lleg, [40, 0, -6], land))
        rot('left_arm', mix(larm, [-90, 0, -20], land)); rot('right_arm', mix(rarm, [40, 0, 50], land))
        return c
    # The crouch: one knee down, the free fist on the snow, the wand arm swept back, under the ball;
    # then pushing up out of it as it breaks apart, to a kneel. The glare: the head comes up slowly
    # to the spire; and from BACK rising to stand, wand ready.
    w, h, rl, ll, la, ra = crouch(s)
    rise = ramp(s, clock(BACK + 1), clock(UP))
    lift = EASE['io'](min(1, max(0, (s - LOOK_S)/6)))
    breath = math.sin((s - STOP)*.55)*(1 - lift*.6)*ramp(s, BREAK, STOP)
    hero = 1 - rise
    rot('waist', [(w - 8*lift + 2.5*breath)*hero, 0, 0])
    rot('right_leg', mix(rl, [0, 0, 0], rise)); rot('left_leg', mix(ll, [0, 0, 0], rise))
    rot('left_arm', mix(la + [10*lift - 1.5*breath, 0, 0], [0, 0, -6], rise))
    rot('right_arm', mix(ra - [4*breath, 0, 0], [-22, 0, 12], rise))
    rot('head', [(h + 4*breath*(1 - lift) - 100*lift)*hero - 14*rise, 0, 0])
    return c


def puppet_channels(t):
    if t < THROW: return ORIGINAL_CHANNELS(t)
    s = clock(t)
    c = posture(s)
    if SCENE is not None and AIM - 1 <= s < SEAL - .5:
        # The wand thrust down at the quarry and held on it, kicked by the cast, trembling as the
        # rocks come; pulled in as the ball closes.
        ax, az = aim_arm(s)
        k = ramp(s, AIM - .5, AIM + .2)*(1 - ramp(s, SEAL - 2, SEAL - .5))
        tremble = 1.5*math.sin(s*23)*ramp(s, RIP, FIRST)
        recoil = 14*kick(s, AIM + .2, 1, 1.2, 1.5) + 3*jolts(s)
        base = c['right_arm']['rotation']
        c['right_arm'] = {'rotation': base*(1 - k) + np.array([ax + recoil + tremble, 0, az])*k}
        c['wand'] = {'rotation': np.array([ALIGN*k, 0, 0])}
    return c


def drop_hips(s):
    """Your hips (the ball's centre) through the drop: off the hang with a little lift, falling ever
    faster and steeper, to the ball bedded in the snow at the contact."""
    u = min(1, max(0, (s - DROP)/(IMPACT - DROP)))
    contact = CONTACT + [0, RADIUS - SINK, 0]
    flat = mix(HANG[[0, 2]], contact[[0, 2]], u**1.5)
    y = HANG[1] + .5*math.sin(math.pi*min(1, u*2.5))*(1 - u) + (contact[1] - HANG[1])*u*u
    return np.array([flat[0], y, flat[1]])


def skid(s):
    """0 at the impact to 1 once the skid back onto the arrival has stopped."""
    return EASE['out'](min(1, max(0, (s - IMPACT)/1.6)))


class StoneScene(B.Scene):

    def ballistic(self):
        """The throw, as build_intro's: a lob from the talons toward the snow."""
        (_, _), w = self.roc_world(THROW)
        p0 = w['grip']
        land = np.array([0.5, .3, 26.0])
        tau = (252 - THROW)/20
        v0 = (land - p0 - .5*np.array([0, -B.GRAVITY, 0])*tau*tau)/tau
        return p0, v0

    def build_path(self):
        """Out of the throw, braked by the rocks flying up into you, to the hang over the quarry."""
        p0, v0 = self.throw
        v = (v0 + np.array([0, -B.GRAVITY, 0])*(RIGHT - THROW)/20)/20  # blocks a tick
        keys = [(RIGHT, self.thrown(RIGHT)), (AIM, self.thrown(AIM)), (FIRST + 1, [3.0, 29.0, -13.0]),
                (FIRST + 4.5, [2.0, 26.9, -3.6]), (SEAL - 2, [1.4, 26.2, 2.4]), (SEAL, HANG + [0, -.1, -.5]), (DROP, HANG)]
        self.hang_path = B.Path3(keys, start_velocity=v, end_velocity=np.array([0, .02, .25]))

    def hips(self, s):
        if s < RIGHT: return self.thrown(s)
        if not hasattr(self, 'hang_path'): self.build_path()
        if s < DROP: return self.hang_path(s)
        if s < IMPACT: return drop_hips(s)
        return mix(CONTACT, ARRIVAL, skid(s)) + [0, .95, 0]  # crouched inside it; planted by player()

    def rotation(self, s):
        if s < RIGHT:
            u = (s - THROW)/(RIGHT - THROW)
            spin = heading(6*wobble(s, 31, 1.3), 88 + 272*EASE['out'](u), 10*wobble(s, 32, 1.1)*(1 - u))
            return slerp(self.held(THROW), spin, ramp(s, THROW, THROW + 4))
        jb = jolts(s)
        if s < SEAL - 1.5:
            p = K(s, (RIGHT, 'io', 0.0), (RIGHT + 2, 'out', 18.0), (AIM - .6, 'io', 6.0), (AIM + .6, 'out', 34.0),
                  (FIRST + 2, 'io', 18.0))
            calm = 1 - ramp(s, FIRST, SEAL)
            return heading(3*math.sin(s*.3)*calm, float(p) - 4*jb + 2*math.sin(s*.9)*calm, 4*math.sin(s*.25)*calm)
        if s < IMPACT:
            # Tucked inside it, turning round to face the spire by the time it lands.
            u = smooth((s - (SEAL - 1.5))/(IMPACT - .8 - (SEAL - 1.5)))
            return heading(180*u, 18*(1 - u) + 10*math.sin(math.pi*u))
        u = (s - IMPACT)/1.6
        if u < 1:
            return heading(180 + 6*(1 - EASE['out'](u)), -8*(1 - EASE['out'](u)))
        return heading(180)

    def body_at(self, s):
        """(root, rotation) at story tick s, before the floor is applied."""
        r = self.rotation(s)
        return self.hips(s) - r@B.HIPS, r

    def player(self, t):
        if t < THROW: return super().player(t)
        s = clock(t)
        root, r = self.body_at(s)
        low = self.lowest(root, r, t)
        if s >= IMPACT or low < FLOOR:
            root = root + [0, FLOOR - low, 0]  # planted from the impact on
        return root, r

    def tip(self, t):
        return wand_points(self.puppet_pose(t))[1]


def ball_centre(s):
    """The ball's centre at story tick s: on your hips while it forms and falls, then bedded in the
    crater, jolted on the impact, trembling as it cracks."""
    if s < IMPACT: return SCENE.hips(s)
    p = mix(CONTACT, ARRIVAL, skid(s)) + [0, RADIUS - SINK, 0]
    p = p + [0, -.22*kick(s, IMPACT, 1, .9, 1.2), 0]
    shiver = .04*pulse(s, CRACK, CRACK + .3, BREAK - .3, BREAK) + .05*kick(s, CRACK, 1, 1, 3)
    return p + [shiver*math.sin(s*31), .5*shiver*math.sin(s*23 + 1), shiver*math.sin(s*27 + 2)]


def ball_turn(s):
    """How far the ball has rolled (radians, about the axis across its travel): still while it
    forms, rolling over as the weight takes it, a last turn in the skid, then still."""
    return 3.0*smooth((s - (SEAL - .3))/(IMPACT - (SEAL - .3))) + .45*skid(s)


ROLL_AXIS = np.array([1.0, 0, 0])  # across its travel (+Z): the top rolls forward


def ball_rotation(s):
    a = ball_turn(s)
    c, sn = math.cos(a/2), math.sin(a/2)
    return [c, *(ROLL_AXIS*sn)]


def rotate(q, v):
    return B.unquat(np.array(q))@np.asarray(v)


# ------------------------------------------------------------------ the camera

ORIGINAL_THIRD = B.third_person


def third_person(scene, t):
    if t < GROUND or t >= LANDING: return ORIGINAL_THIRD(scene, t)
    s = clock(t)
    root, r = scene.player(t)
    chest = root + r@B.CHEST
    ball = ball_centre(s)
    if t < RT['quarry_cut']:
        # Falling beside you, a little ahead and below: you catch yourself out of the tumble, your
        # head snaps down to the ground (a punch in), and you thrust the wand down at it.
        p0, v0 = scene.throw
        v = v0 + np.array([0, -B.GRAVITY, 0])*(t - THROW)/20
        d = v*np.array([1, 0, 1]); d = d/np.linalg.norm(d)
        side = np.cross([0, 1, 0], d)
        eye = chest + side*(4.2 - .6*ramp(t, GROUND, RT['quarry_cut'])) + d*2.6 + [0, -1.2 - .6*ramp(t, AIM - 2, RT['quarry_cut']), 0]
        down = (QUARRY - chest)/np.linalg.norm(QUARRY - chest)
        target = chest + down*(.3 + .9*ramp(t, AIM, RT['quarry_cut']))
        yaw, pitch = look(eye, target)
        sh = shake(t, 40, .9 + .8*kick(t, SPOT + .5, 1, 1.5, 1.5)**2 + 1.4*kick(t, AIM + .2, 1, 2, 1.5)**2)
        fov = 56 - 10*pulse(t, SPOT, SPOT + .7, AIM - 1, AIM + .5) + 4*ramp(t, AIM, RT['quarry_cut'])
        return eye, yaw + sh[0], pitch + sh[1], sh[2]*2 - 6*math.sin(t*.1), float(fov)
    if t < RT['shell_cut']:
        # Low on the snow beside the quarry, in slow motion: the summit breaks open in front of us
        # and the rocks tear up out of it, past us and up against the sky to you, high above with
        # the wand thrust down at them; the camera tilts up after them.
        u = ramp(t, RT['quarry_cut'], RT['shell_cut'])
        tilt = EASE['io'](min(1.0, u*1.15))
        eye = QUARRY + [-5.8 + .6*u, 1.7 + .9*u, 4.6 - .4*u]
        target = mix(QUARRY + [0, .6, 0], mix(QUARRY, chest, .55), tilt)
        yaw, pitch = look(eye, target)
        rumble = .5 + 1.2*math.exp(-max(0, t - RT['rip'])/6)*(t >= RT['rip'])
        sh = shake(t, 41, rumble)
        return eye, yaw + sh[0], pitch + sh[1], sh[2] - 3, float(72 - 4*u)
    if t < RT['crash_cut']:
        # Out beside you, falling with you: the rocks slam in round you, building up into a ball
        # with you inside; the camera eases round as time slows and the top closes; then it rolls
        # over, and we stop as it drops away below us.
        u = smooth((t - RT['shell_cut'])/(RT['seal'] + 1 - RT['shell_cut']))
        phi = math.radians(mix(80, 150, u))
        radius = float(mix(6.6, 6.0, u))
        follow = min(t, RT['drop'] + .5)
        fball = ball_centre(clock(follow))
        eye = fball + [math.sin(phi)*radius, float(mix(-1.6, .6, u)), math.cos(phi)*radius]
        target = mix(fball, ball, ramp(t, RT['drop'], RT['crash_cut']))
        yaw, pitch = look(eye, target)
        hits = sum(kick(t, real(at), 1, 1.2, 2.2) for at in ARRIVALS[::6])
        sh = shake(t, 43, .35 + .35*abs(hits) + 1.6*ramp(t, RT['drop'], RT['drop'] + 1.5))
        fov = 54 - 4*bullet(s) + 8*ramp(t, RT['drop'], RT['crash_cut'])
        return eye, yaw + sh[0], pitch + sh[1] + .6*hits, sh[2] + float(mix(5, -3, u)), float(fov)
    if t < RT['shed_cut']:
        # Up on the snow beyond the arrival, looking back up the summit: a long lens finds the
        # boulder coming down out of the sky and follows it in; it slams in, the summit cracks open,
        # snow and rubble are thrown up, the dust rolls out and settles round the ball in its crater.
        push = ramp(t, RT['impact'], RT['shed_cut'])
        eye = ARRIVAL + [-6.0, 3.4, 8.4] + [.7*push, -.6*push, -1.4*push]
        follow = .3*ramp(t, RT['crash_cut'], RT['impact'] - 3) + .7*ramp(t, RT['impact'] - 1.5, RT['impact'] + .5)
        target = mix(ball, REST + [0, -.4, 0], follow)
        yaw, pitch = look(eye, target)
        after = t >= RT['impact']
        sh = shake(t, 45, .3 + 2.4*math.exp(-max(0, t - RT['impact'])/3)*after)
        jolt = kick(t, RT['impact'], 3, 3, 2.2)
        fov = float(mix(24, 50, ramp(t, RT['crash_cut'] + 2, RT['impact'] - 1.5))) - 6*push
        return eye, yaw + sh[0], pitch + sh[1] - jolt, sh[2]*1.6, float(fov)
    if t < RT['look_cut']:
        # Low in front of it and off to the side: the ball cracks, then breaks apart and falls away
        # round you as you push yourself up out of it into a kneel. Inside the summit's boulder-free
        # ring round the arrival (7 blocks), so no rock on the real summit stands in front of it.
        u = ramp(t, RT['shed_cut'], RT['look_cut'])
        eye = ARRIVAL + [-3.9, 2.5, -4.3] + [.6*u, -.5*u, .9*u]
        target = mix(REST + [0, -.3, 0], chest + [0, .1, 0], ramp(t, RT['rise'], RT['rise'] + 6))
        yaw, pitch = look(eye, target)
        sh = shake(t, 48, .2 + .8*kick(t, RT['crack'], 1, 2, 2)**2 + 1.2*kick(t, RT['rise'], 1, 2, 2)**2)
        return eye, yaw + sh[0], pitch + sh[1], sh[2], float(60 - 6*u)
    # Low behind you, over your wand shoulder: head bowed among the rubble; then as it comes up, we
    # tilt up with it past your shoulder to the Roc coming back round to its spire, closing in on it.
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


# ------------------------------------------------------------------ the chunks' timings, sources, bursts

def chunk_data():
    """Each chunk: its slot on the ball and its boxes; when it tears out of the quarry and where
    from; when it packs on; and when it falls away as the ball breaks, with its throw."""
    out = []
    q_rest = ball_rotation(BREAK)
    for i, sh in enumerate(SHAPES):
        r = rng(300 + i)
        at = sh['at']
        flight = r.uniform(2.4, 3.2)
        a = r.uniform(0, math.tau); d = r.uniform(1.2, 4.6)
        # Long flights, so the rocks are seen tearing out and streaming up; none leaves before the
        # snow breaks open (the first few all tear out together on the rip).
        launch = max(at - flight - 1.4, RIP + .1 + .8*rng(1300 + i).random())
        src = QUARRY + [math.cos(a)*d, 0, math.sin(a)*d]
        n = rotate(q_rest, sh['n'])  # its normal once the ball has come to rest
        stay = bool(n[1] < -.6)      # the very bottom of the ball stays bedded in the crater
        top = (n[1] + 1)/2
        off = BREAK + (1 - top)*1.6 + r.uniform(0, .5) if not stay else BREAK + .5 + r.uniform(0, 1.5)
        horiz = n*np.array([1, 0, 1])
        vel = horiz*r.uniform(2.6, 4.4) + [0, r.uniform(.8, 1.8) + 2.4*max(0, n[1]), 0]
        out.append({'centre': sh['centre'], 'basis': sh['basis'], 'boxes': sh['boxes'], 'cracks': sh['cracks'],
                    'src': B.r3(src), 'launch': round(launch*B.PACE, 2), 'arrive': round(at*B.PACE, 2),
                    'detach': round(off*B.PACE, 2), 'stay': stay, 'vel': B.r3(vel), 'curve': round(float(r.uniform(-1, 1)), 3),
                    'spin': B.r3(r.normal(size=3)), 'crackDelay': round(float(r.uniform(0, 3)), 2)})
    return out


def bursts():
    def b(s, kind, at): return {'tick': round(real(s)*B.PACE, 2), 'clock': round(s*B.PACE, 2), 'kind': kind, 'at': B.r3(at)}
    return [b(RIP, 'rip', QUARRY), b(IMPACT, 'impact', ARRIVAL), b(CRACK, 'crack', REST), b(BREAK, 'break', ARRIVAL)]


def stone_columns(scene):
    keys = ('n_clock', 'st_ball', 'st_roll', 'st_tip', 'st_glow', 'st_speed', 'st_bt', 'st_drop')
    cols = {k: [] for k in keys}
    for g in range(LENGTH*B.PACE + 1):
        t = g/B.PACE
        s = clock(t)
        put = lambda k, v: cols[k].append(round(float(v), 3))
        put('n_clock', s*B.PACE)
        cols['st_ball'].append(B.r3(ball_centre(s)) if t >= THROW else [0, 0, 0])
        put('st_roll', ball_turn(s))
        cols['st_tip'].append(B.r3(scene.tip(t)) if t >= THROW else [0, 0, 0])
        put('st_glow', min(1.0, ramp(s, AIM - .6, AIM + .2)*(1 - ramp(s, SEAL - 2, SEAL)) + .6*kick(s, AIM + .2, 1, 1.5, .1)))
        put('st_speed', .4*pulse(t, GROUND + 1, GROUND + 4, RT['quarry_cut'] - .5, RT['quarry_cut']) +
            .55*pulse(t, RT['drop'] + .5, RT['drop'] + 2, RT['impact'] - .3, RT['impact']))
        put('st_bt', bullet(s))
        put('st_drop', pulse(s, DROP + .3, DROP + 1.5, IMPACT - .2, IMPACT))
    return cols


ORIGINAL_CUES = B.cues


def cues(scene):
    out = [c for c in ORIGINAL_CUES(scene) if c['cue'] not in ('crash',)]
    out += [{'tick': int(round(t)), 'cue': c, 'from': 'player'} for t, c in (
        (RT['right'] - 2, 'catch_yourself'), (RT['aim'], 'gathered_mass_cast'), (RT['rip'], 'ground_tears'),
        (RT['first'], 'rocks_pack_on'), (RT['seal'] - 1, 'slowmo_in'), (RT['seal'], 'ball_closes'), (RT['drop'], 'boulder_drop'),
        (RT['impact'], 'boulder_impact'), (RT['impact'] + 1, 'ground_cracks'), (RT['crack'], 'ball_cracks'),
        (RT['rise'], 'ball_breaks'))]
    return sorted(out, key=lambda c: c['tick'])


# ------------------------------------------------------------------ baking

SHOTS = [['1 · quiet', 0], ['2 · caw', 'caw'], ['3 · snatch', 'snatch'], ['4 · carried', 'grip', 8], ['5 · reveal', 'look_up'],
         ['6 · clouds', 'cloud'], ['7 · struggle', 'summit'], ['8 · throw', 'throw'], ['9 · the fall', 'ground'],
         ['10 · the quarry', 'quarry_cut'], ['11 · the ball', 'shell_cut'], ['12 · the crash', 'crash_cut'],
         ['13 · the break', 'shed_cut'], ['14 · the glare', 'look_cut'],
         ['15 · the Roc lands', 'landing'], ['16 · hand back', 'back']]
SHOT_NAMES = ['quiet', 'caw', 'snatch', 'carried off', 'the reveal', 'storm clouds', 'the struggle', 'the throw', 'the fall',
              'the quarry', 'the ball', 'the crash', 'the break', 'the glare', 'the Roc lands', 'hand back']


def build():
    global SCENE
    retime()
    B.summit_body_path = summit_body_path
    B.puppet_channels = puppet_channels
    B.third_person = third_person
    B.cues = cues
    scene = StoneScene()
    SCENE = scene
    roc_clip = B.bake(lambda seconds: B.roc_channels(seconds*20/B.PACE), LENGTH*B.PACE/20, B.RATE, .02)
    player_clip = B.bake(lambda seconds: puppet_channels(seconds*20/B.PACE), LENGTH*B.PACE/20, B.RATE*2, .05)
    track = B.bake_track(scene)
    track['ticks'].update(stone_columns(scene))
    track['landing'] = {'element': 'stone', 'script': 'landing_stone.js', 'radius': RADIUS, 'chunks': chunk_data(), 'bursts': bursts(),
                        'quarry': B.r3(QUARRY), 'arrival': B.r3(ARRIVAL),
                        'shots': [[label, beat, plus[0]*B.PACE] if plus else [label, beat] for label, beat, *plus in SHOTS],
                        'shot_names': SHOT_NAMES}
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
                         HERE/'animatic.js': 'animatic.js', HERE/'landing_stone.js': 'landing_stone.js'}.items():
        shutil.copyfile(source, PREVIEW/name)
    page = (HERE/'viewer.template.html').read_text(encoding='utf-8')
    page = page.replace('<title>Storm Roc Intro Animatic</title>', '<title>Roc Intro Stone</title>')
    page = page.replace('<h1>Storm Roc intro — animatic</h1>', '<h1>Storm Roc intro — Stone landing</h1>')
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
    print(f'Stone landing baked: {LENGTH*B.PACE/20:.1f} s, wand alignment {ALIGN:.1f} deg, into {PREVIEW.relative_to(B.REPO)}')
    print('real beats:', {k: v for k, v in RT.items()}, 'landing', LANDING, 'length', LENGTH)


if __name__ == '__main__':
    write_preview()
