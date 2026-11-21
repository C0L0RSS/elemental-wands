#!/usr/bin/env python3
"""The Storm Roc's intro with Nature's landing: build_intro.py's scene from the throw on.

Everything up to the throw is build_intro's. Then a Nature wizard catches themselves in the fall,
spots the ground, winds up and pitches a charged Springbloom pod down ahead of them. It bursts open
in the snow; they hit it and it fires them straight back up like a trampoline, high into the air,
corkscrewing round to face the home spire. At the top time slows for a moment: the camera circles
them hanging there as the wand charges, a lightning bolt held in the sky behind. Time snaps back, a
braided vine fires from the wand, its flytrap head bites into the snow ahead of the arrival, and it
swings them down feet first into a slide across the snow, grass and flowers springing up along the
track behind them and spreading out where they stop, crouched. Head bowed; then they look up at the
spire, and the scene cuts to the Roc landing on it as before.

The scene is authored in story time: the speed curve below stretches parts of it into slow motion.
The camera runs in real time, so it can circle a moment that hardly moves. The Roc's flight and
wingbeats run on the story clock too, so it hangs in the bullet time with everything else.

Run alone, this writes a browser animatic to .local-previews/storm-roc-intro-nature/.
install_landings.py nature installs it into the game, where its effects are
client/roclanding/NatureLanding.java. Run from the repository root:
    py -3 art/storm_roc/intro/nature_landing.py
then serve that folder (python -m http.server 8397) and open it.
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
from build_intro import EASE, Rx, Ry, curve, heading, kick, look, mix, pulse, ramp, shake, slerp, smooth, wobble  # noqa: E402
from gecko import Rx as Rx4, T as T4  # noqa: E402

PREVIEW = B.REPO/'.local-previews/storm-roc-intro-nature'
NATURE_MESHES = B.ASSETS/'nature/springbloom.json'

# ------------------------------------------------------------------ story beats

# In story ticks (one is 0.1 s at normal speed). Until the bounce, story and real ticks agree.
THROW, GROUND = B.THROW, B.GROUND
RIGHT = 234      # out of the tumble: arms flung out, falling feet first
SPOT = 236       # the head snaps down: the ground
WIND = 238       # the wand arm cocked back over the head, the pod charging on its tip
TOSS = 242.5     # the pitch: the pod fired down ahead of you
OPEN = 247       # it hits the snow and bursts open
BOUNCE = 252     # feet on the cushion: a quick dip, like a trampoline
LAUNCH = 252.7   # and it fires you straight back up
APEX = 262       # the top: corkscrewed round to face the spire; a short bullet time
SHOOT = 264      # time snaps back: the vine fires
LATCH = 266      # its flytrap head bites into the snow ahead of the arrival
REEL = 266.5     # it swings you down, feet first
TOUCH = 270      # feet on the snow: a slide, the vine still pulling
RELEASE = 273    # let go as the slide slows
STOP = 274.5     # stopped, crouched at the arrival; grass and flowers along the track behind you

PAD = np.array([0.5, 1.0, 20.0])     # the Springbloom's centre, on the snow
CUSHION = 0.6875                     # its landing centre's height
PAD_RADIUS = 1.0
ARRIVAL = B.ARRIVAL.copy()           # where you land, and the fight expects you
ANCHOR = ARRIVAL + [0, 0, -2.2]      # where the vine bites: ahead of where the slide stops
TOUCHDOWN_AT = ARRIVAL + [0, 0, 4.0] # where your feet meet the snow; you slide from here to the arrival
TOP = np.array([0.5, 13.0, 41.0])    # your hips at the top of the jump
FLOOR = B.ARRIVAL[1]
WAND_TIP = 0.72                      # the animatic's wand: tip 0.72 out along its axis
SPIRE = B.HOME_PERCH + [0, 6, 0]


def speed(s):
    """How fast story time runs at story tick s: 1 normal, under 1 slow motion."""
    v = 1.0
    v -= .75*pulse(s, APEX - 1.6, APEX - .3, SHOOT - .2, SHOOT)            # bullet time, snapped off by the shot
    return v


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

RT = {k: half(real(v)) for k, v in dict(right=RIGHT, spot=SPOT, wind=WIND, toss=TOSS, open=OPEN, bounce=BOUNCE,
                                         launch=LAUNCH, apex=APEX, shoot=SHOOT, latch=LATCH, reel=REEL,
                                         touch=TOUCH, release=RELEASE, stop=STOP).items()}
RT['drop_cut'] = 245.5                                  # low on the snow by the pad
RT['apex_cut'] = half(real(LAUNCH + 5.5))               # circling you at the top of the jump
RT['reel_cut'] = half(real(REEL + .8))                  # on the snow under you, looking up the vine
RT['crane_cut'] = math.ceil(RT['stop']) + 6             # high over the green track the slide left
RT['look_cut'] = RT['crane_cut'] + 12                   # low in front of you
RT['look'] = RT['look_cut'] + 4                         # the head comes up
LANDING = RT['look'] + 12                               # cut: the Roc coming down onto its spire, as before
TOUCHDOWN = LANDING + 2
LAND = TOUCHDOWN - 16
SETTLED = LAND + 50
BACK = LANDING + 32
UP = BACK + 6
RETURN = UP + 1
LENGTH = RETURN + 20
BOLT = half(real(APEX - .6))                            # a bolt behind the spire, frozen in the bullet time

# ------------------------------------------------------------------ build_intro's timeline, moved on

ORIGINAL_RATE = B.rate
ORIGINAL_OVERLAYS = B.overlays


def rate(t):
    return ORIGINAL_RATE(t)*speed(clock(t))  # its wingbeats slow with the story clock


def flash(t):
    """build_intro's lightning light, aged on the story clock (so the frozen bolt stays lit)."""
    f = 0.0
    for e in B.LIGHTNING:
        if t >= e[0]:
            size = {'crackle': .6, 'sheet': .8, 'bolt': .9}[e[2]]*(e[5] if len(e) > 5 else 1)
            age = clock(t) - clock(e[0])
            f += size*math.exp(-age/2.2)*(.6 + .4*math.cos(age*2.1))
    return min(1.0, f)


def bullet(s): return pulse(s, APEX - 1.6, APEX - .3, SHOOT - .2, SHOOT)


def overlays(scene, t):
    o = ORIGINAL_OVERLAYS(scene, t)
    o['bars'] = o['bars']*(1 + .45*bullet(clock(t)))  # the letterbox closes in on the bullet time
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
    g['LIGHTNING'] = [e for e in B.LIGHTNING if e[0] < THROW + 10] + [(BOLT, 1, 'bolt', -30, -124, .4), (TOUCHDOWN + 1, 1, 'bolt', 26, -70)]
    beats = dict(B.BEATS, length=LENGTH, crash=RT['stop'], landing=LANDING, touchdown=TOUCHDOWN, land=LAND, rolled=RT['stop'],
                 back=BACK, up=UP, title=LANDING + 3, title_end=BACK, ret=RETURN, **RT)
    g['BEATS'] = beats
    g['CUTS'] = [B.SUMMIT, THROW, GROUND, RT['drop_cut'], RT['apex_cut'], RT['reel_cut'], RT['crane_cut'], RT['look_cut'], LANDING, BACK]


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
SCENE = None  # set once built: the arm aims at the anchor from where the body is
AIMS = {}


def K(s, *rows):
    """A value keyed through rows of (story tick, ease into it, value)."""
    return curve(s, list(rows))[0]


def squash(s):
    """The Springbloom's height under you: dipping as you land on it, then springing back past full."""
    if s < BOUNCE: return 1.0
    if s < BOUNCE + .35: return 1 - .45*EASE['out']((s - BOUNCE)/.35)
    u = s - (BOUNCE + .35)
    return 1 - .45*math.exp(-u/1.4)*math.cos(math.tau*u/2.2)


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
    """The wand arm's rotation that points the wand at the vine's anchor from the body's place at story tick s."""
    key = round(s, 3)
    if key in AIMS: return AIMS[key]
    root, r = SCENE.body_at(s)
    base = np.eye(4); base[:3, :3] = r; base[:3, 3] = root

    def miss(ax, az):
        c = posture(s); c['right_arm'] = {'rotation': np.array([ax, 0, az])}
        c['wand'] = {'rotation': np.array([ALIGN, 0, 0])}
        hand, tip = wand_points(B.PUP.pose(c, base@B.ENTITY))
        d = (tip - hand)/np.linalg.norm(tip - hand)
        want = (ANCHOR - tip)/np.linalg.norm(ANCHOR - tip)
        return 1 - float(d@want)
    _, ax, az = min((miss(ax, az), ax, az) for ax in range(-232, 41, 8) for az in range(-30, 71, 10))
    for step in (3, 1):
        _, ax, az = min((miss(ax + i*step, az + k*step), ax + i*step, az + k*step) for i in (-2, -1, 0, 1, 2) for k in (-2, -1, 0, 1, 2))
    AIMS[key] = (ax, az)
    return ax, az


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
    if s < BOUNCE:
        # Arms out, buffeted; the head snaps down to the ground; the wand arm cocked back over the
        # head, the free arm pointing at the spot; the pitch, the whole body thrown into it, the leg
        # kicked back; out of it, arms out again, and the legs reaching for the flower.
        flut = math.sin(s*1.9)*4, math.sin(s*1.6 + 1)*3
        T = TOSS
        rot('right_arm', K(s, (RIGHT, 'io', [-62, 0, 78]), (WIND, 'io', [-74, 0, 74]), (T - .4, 'io', [-200, 0, 24]),
                           (T + .5, 'out', [-14, 0, 10]), (T + 1.6, 'out', [26, 0, -10]), (T + 4.5, 'io', [-60, 0, 74]),
                           (BOUNCE - 2, 'io', [-112, 0, 46]), (BOUNCE, 'io', [-128, 0, 36])) + [flut[0], 0, 0])
        rot('left_arm', K(s, (RIGHT, 'io', [-62, 0, -78]), (WIND, 'io', [-78, 0, -70]), (T - .4, 'io', [-122, 0, -12]),
                          (T + .5, 'out', [-40, 0, -34]), (T + 1.6, 'out', [26, 0, -42]), (T + 4.5, 'io', [-60, 0, -74]),
                          (BOUNCE - 2, 'io', [-112, 0, -46]), (BOUNCE, 'io', [-128, 0, -36])) + [-flut[0], 0, 0])
        rot('waist', K(s, (RIGHT, 'io', [10, 0, 0]), (SPOT + .8, 'out', [20, 0, 0]), (WIND, 'io', [12, 0, 0]),
                       (T - .4, 'io', [-16, 0, 0]), (T + .5, 'out', [36, 0, 0]), (T + 1.6, 'out', [42, 0, 0]),
                       (T + 4.5, 'io', [12, 0, 0]), (BOUNCE - 1, 'io', [4, 0, 0]), (BOUNCE, 'io', [10, 0, 0])))
        rot('head', K(s, (RIGHT, 'io', [25, 0, 0]), (SPOT, 'io', [22, 0, 0]), (SPOT + .7, 'out', [60, 0, 0]),
                      (WIND + 1, 'io', [46, 0, 0]), (T - .4, 'io', [38, 0, 0]), (T + 1, 'out', [56, 0, 0]),
                      (OPEN, 'io', [48, 0, 0]), (BOUNCE - 1, 'io', [30, 0, 0]), (BOUNCE, 'io', [22, 0, 0])))
        rot('right_leg', K(s, (RIGHT, 'io', [-12, 0, 8]), (WIND, 'io', [-14, 0, 8]), (T - .4, 'io', [-34, 0, 8]),
                           (T + .5, 'out', [28, 0, 8]), (T + 2, 'io', [10, 0, 8]), (T + 4.5, 'io', [-8, 0, 8]),
                           (BOUNCE - 2, 'io', [-30, 0, 8]), (BOUNCE, 'io', [-22, 0, 8])) + [flut[1], 0, 0])
        rot('left_leg', K(s, (RIGHT, 'io', [12, 0, -8]), (WIND, 'io', [0, 0, -8]), (T - .4, 'io', [16, 0, -8]),
                          (T + .5, 'out', [-26, 0, -8]), (T + 2, 'io', [-10, 0, -8]), (T + 4.5, 'io', [-4, 0, -8]),
                          (BOUNCE - 2, 'io', [-24, 0, -8]), (BOUNCE, 'io', [-18, 0, -8])) - [flut[1], 0, 0])
        return c
    if s < LAUNCH + 2:
        # A quick dip into the flower, like a trampoline, and straight back up off it, arms flung overhead.
        L, D = LAUNCH, BOUNCE + .35
        legs = lambda z: K(s, (BOUNCE, 'io', [-20, 0, z]), (D, 'out', [-48, 0, z*1.4]), (L + .5, 'out', [10, 0, z*.5]), (L + 2, 'io', [4, 0, z*.5]))
        rot('right_leg', legs(8)); rot('left_leg', legs(-8))
        rot('waist', K(s, (BOUNCE, 'io', [10, 0, 0]), (D, 'out', [30, 0, 0]), (L + .5, 'out', [-18, 0, 0]), (L + 2, 'io', [-8, 0, 0])))
        arms = lambda z: K(s, (BOUNCE, 'io', [-128, 0, 36*z]), (D, 'out', [-40, 0, 30*z]), (L + .5, 'out', [-180, 0, 16*z]), (L + 2, 'io', [-170, 0, 14*z]))
        rot('right_arm', arms(1)); rot('left_arm', arms(-1))
        rot('head', K(s, (BOUNCE, 'io', [22, 0, 0]), (D, 'out', [24, 0, 0]), (L + .5, 'out', [-30, 0, 0]), (L + 2, 'io', [-20, 0, 0])))
        return c
    if s < SHOOT:
        # Tucked tight for the corkscrew; opening out of it into the hang: the free arm flung back,
        # one leg forward, one back, looking down at the snow. The wand arm aims (set by aim_arm).
        A, L = APEX, LAUNCH + 2
        drift = math.sin(s*.8)
        rot('right_arm', K(s, (L, 'io', [-170, 0, 14]), (L + 1.5, 'io', [-80, 0, -28]), (A - 3, 'io', [-80, 0, -28]), (A - 1.5, 'out', [-95, 0, 20])))
        rot('left_arm', K(s, (L, 'io', [-170, 0, -14]), (L + 1.5, 'io', [-80, 0, 28]), (A - 3, 'io', [-80, 0, 28]), (A - 1, 'out', [50, 0, -74])))
        rot('right_leg', K(s, (L, 'io', [4, 0, 4]), (L + 1.5, 'io', [-118, 0, 6]), (A - 3, 'io', [-118, 0, 6]), (A - 1, 'out', [-50, 0, 8])) + [3*drift, 0, 0])
        rot('left_leg', K(s, (L, 'io', [4, 0, -4]), (L + 1.5, 'io', [-112, 0, -6]), (A - 3, 'io', [-112, 0, -6]), (A - 1, 'out', [40, 0, -8])) - [3*drift, 0, 0])
        rot('waist', K(s, (L, 'io', [-8, 0, 0]), (L + 1.5, 'io', [40, 0, 0]), (A - 3, 'io', [40, 0, 0]), (A - 1, 'out', [14, 0, 0])))
        rot('head', K(s, (L, 'io', [-20, 0, 0]), (L + 1.5, 'io', [26, 0, 0]), (A - 3, 'io', [26, 0, 0]), (A - 1, 'out', [34, 0, 0])) + [2*drift, 0, 0])
        return c
    if s < TOUCH:
        # Shot, bitten, and swung down the vine: the legs coming forward under you to meet the snow,
        # the free arm out for balance.
        R_ = REEL
        rot('right_arm', [-95, 0, 20])
        rot('left_arm', K(s, (SHOOT, 'io', [50, 0, -74]), (R_, 'io', [50, 0, -74]), (TOUCH - .5, 'io', [-30, 0, -70])))
        rot('right_leg', K(s, (SHOOT, 'io', [-50, 0, 8]), (R_, 'io', [-50, 0, 8]), (TOUCH, 'io', [-58, 0, 10])))
        rot('left_leg', K(s, (SHOOT, 'io', [40, 0, -8]), (R_, 'io', [40, 0, -8]), (TOUCH, 'io', [30, 0, -6])))
        rot('waist', K(s, (SHOOT, 'io', [14, 0, 0]), (R_, 'io', [14, 0, 0]), (TOUCH, 'io', [30, 0, 0])))
        rot('head', K(s, (SHOOT, 'io', [34, 0, 0]), (R_, 'io', [34, 0, 0]), (TOUCH, 'io', [10, 0, 0])))
        return c
    if s < STOP:
        # The slide: low in a skid, one leg out ahead, the trailing knee dropping, the free hand
        # dragging in the snow; the wand arm lets go of the vine and sweeps back as it slows.
        T_ = TOUCH
        rot('right_arm', K(s, (RELEASE, 'io', [-95, 0, 20]), (STOP, 'out', [74, 0, 48])))
        rot('left_arm', K(s, (T_, 'io', [-30, 0, -70]), (T_ + 1, 'out', [-58, 0, -26]), (STOP, 'io', [-70, 0, -8])))
        rot('right_leg', K(s, (T_, 'io', [-58, 0, 10]), (T_ + 1, 'out', [-62, 0, 12]), (STOP, 'io', [-56, 0, 10])))
        rot('left_leg', K(s, (T_, 'io', [30, 0, -6]), (T_ + 1.2, 'out', [56, 0, -6]), (STOP, 'io', [62, 0, -6])))
        rot('waist', K(s, (T_, 'io', [30, 0, 0]), (T_ + 1, 'out', [46, 0, 0]), (STOP, 'io', [64, 0, 0])))
        rot('head', K(s, (T_, 'io', [10, 0, 0]), (T_ + 1, 'io', [16, 0, 0]), (STOP, 'io', [26, 0, 0])))
        return c
    # Stopped: one knee down, the free hand on the snow, the wand arm swept back and out; the head
    # bowed, breathing hard; then coming up slowly to glare at the spire; and from BACK rising to
    # stand, wand ready.
    settle = kick(s, STOP, .6, 3, 1.0)
    look_s = clock(RT['look'])
    rise = ramp(s, clock(BACK + 1), clock(UP))
    lift = EASE['io'](min(1, max(0, (s - look_s)/6)))
    breath = math.sin((s - STOP)*.55)*(1 - lift*.6)
    hero = 1 - rise
    rot('waist', [(64 + 6*settle - 12*lift + 2.5*breath)*hero, 0, 0])
    rot('right_leg', [-56*hero, 0, 10*hero]); rot('left_leg', [62*hero, 0, -6*hero])
    rot('left_arm', mix([-70 + 3*settle + 14*lift - 1.5*breath, 0, -8], [0, 0, -6], rise))
    rot('right_arm', mix([74 - 4*breath, 0, 48 - 4*settle], [-22, 0, 12], rise))
    rot('head', [(26 + 4*breath*(1 - lift) - 100*lift)*hero - 14*rise, 0, 0])
    return c


def puppet_channels(t):
    if t < THROW: return ORIGINAL_CHANNELS(t)
    s = clock(t)
    c = posture(s)
    if SCENE is not None and APEX - 2 <= s < RELEASE + .7:
        # The wand points at the anchor: eased in out of the tuck, trembling as it charges, kicked
        # back by the shot, held through the swing, let go as the slide slows.
        ax, az = aim_arm(s)
        k = ramp(s, APEX - 2, APEX - .5)*(1 - ramp(s, RELEASE - .3, RELEASE + .7))
        tremble = 1.5*math.sin(s*23)*pulse(s, APEX - 1, APEX, SHOOT - .3, SHOOT)
        recoil = 18*kick(s, SHOOT, 1, 1.5, 1.0)
        base = c['right_arm']['rotation']
        c['right_arm'] = {'rotation': base*(1 - k) + np.array([ax + recoil + tremble, 0, az])*k}
        c['wand'] = {'rotation': np.array([ALIGN*k, 0, 0])}
    return c


def slide(s):
    """Your feet's place on the snow through the slide: fast off the touchdown, easing to a stop."""
    u = min(1, max(0, (s - TOUCH)/(STOP - TOUCH)))
    return mix(TOUCHDOWN_AT, ARRIVAL, EASE['out'](u))


class NatureScene(B.Scene):

    def ballistic(self):
        """The throw: from the talons at THROW to your feet on the cushion at BOUNCE, on a lob."""
        (_, _), w = self.roc_world(THROW)
        p0 = w['grip']
        land = PAD + [0, CUSHION + B.HIPS[1], 0]
        tau = (BOUNCE - THROW)/20
        v0 = (land - p0 - .5*np.array([0, -B.GRAVITY, 0])*tau*tau)/tau
        return p0, v0

    def hips(self, s):
        """Where your hips are at story tick s, from the throw on."""
        if s < BOUNCE: return self.thrown(s)
        on_pad = PAD + [0, CUSHION + B.HIPS[1], 0]
        if s < LAUNCH:
            p0, v0 = self.throw
            v = v0 + np.array([0, -B.GRAVITY, 0])*(BOUNCE - THROW)/20
            drift = v*np.array([1, 0, 1])/20*.15
            return on_pad + drift*EASE['out'](min(1, (s - BOUNCE)/(LAUNCH - BOUNCE)))
        start = self.hips(LAUNCH - 1e-6)
        if s < APEX:
            u = (s - LAUNCH)/(APEX - LAUNCH)
            return mix(start, TOP, 1 - (1 - u)**2)  # decelerating to the top, as a throw does
        hang = TOP + [0, -.35, .35]
        if s < REEL:
            u = (s - APEX)/(REEL - APEX)
            return TOP + [0, -.35*u*u, .35*u]
        touch = TOUCHDOWN_AT + [0, B.HIPS[1], 0]
        if s < TOUCH:
            # Swung down the vine on a curve that flattens out into the snow.
            u = ((s - REEL)/(TOUCH - REEL))**1.6
            bend = TOUCHDOWN_AT + [0, B.HIPS[1] + .4, 2.2]
            return (1 - u)**2*hang + 2*u*(1 - u)*bend + u*u*touch
        return slide(s) + [0, B.HIPS[1], 0]

    def rotation(self, s):
        if s < RIGHT:
            # One fast forward tumble out of its grip, caught upright as you fling your arms out.
            u = (s - THROW)/(RIGHT - THROW)
            spin = heading(6*wobble(s, 31, 1.3), 88 + 272*EASE['out'](u), 10*wobble(s, 32, 1.1)*(1 - u))
            return slerp(self.held(THROW), spin, ramp(s, THROW, THROW + 4))
        if s < BOUNCE:
            p = K(s, (RIGHT, 'io', 0.0), (RIGHT + 2, 'out', 20.0), (SPOT + 1, 'io', 30.0), (TOSS - .4, 'io', 10.0),
                  (TOSS + .8, 'out', 44.0), (TOSS + 3, 'io', 22.0), (BOUNCE - 2.5, 'io', -2.0), (BOUNCE, 'io', -4.0))
            calm = 1 - ramp(s, BOUNCE - 3, BOUNCE)
            return heading(3*math.sin(s*.3)*calm, float(p) + 2*math.sin(s*.9)*calm, 4*math.sin(s*.25)*calm)
        a, b = LAUNCH + .3, APEX - 1.2
        if s < a:
            return heading(0, -4 + 10*pulse(s, BOUNCE, BOUNCE + 1, LAUNCH - .3, LAUNCH + .2))
        hang = heading(180, 14 + 2*math.sin(s*.6) - 10*kick(s, SHOOT, 1, 1.5, 1.0), 3*math.sin(s*.4))
        if s < APEX:
            # The corkscrew: a back flip with a half twist out of it, to come out facing the spire.
            u = min(1, (s - a)/(b - a))
            flip = Rx(math.radians(-360*smooth(u)))@Ry(-math.radians(180*smooth((u - .15)/.75)))
            return slerp(flip, hang, ramp(s, APEX - 2, APEX))
        if s < REEL: return hang
        if s < TOUCH:
            # Swung down feet first, leaning back into the pull.
            return heading(180, float(K(s, (REEL, 'io', 14.0), (TOUCH, 'io', -16.0))))
        if s < STOP:
            # Skidding: kicked a little sideways, straightening as it slows.
            u = (s - TOUCH)/(STOP - TOUCH)
            return heading(180 - 16*(1 - EASE['out'](u)), -16*(1 - EASE['out'](u)), 4*math.sin(u*9)*(1 - u))
        return heading(180)

    def body_at(self, s):
        """(root, rotation) at story tick s, before the floor is applied."""
        r = self.rotation(s)
        return self.hips(s) - r@B.HIPS, r

    def floor(self, root, s):
        on_pad = OPEN <= s < LAUNCH + 2 and math.hypot(root[0] - PAD[0], root[2] - PAD[2]) < PAD_RADIUS + .6
        return PAD[1] + CUSHION*squash(s) if on_pad else FLOOR

    def player(self, t):
        if t < THROW: return super().player(t)
        s = clock(t)
        root, r = self.body_at(s)
        low = self.lowest(root, r, t)
        floor = self.floor(root, s)
        if s >= TOUCH:
            root = root + [0, floor - low, 0]  # planted: sliding, then stopped
        elif BOUNCE <= s < LAUNCH + 1.2:
            root = root + [0, (floor - low)*(1 - ramp(s, LAUNCH, LAUNCH + 1.2)), 0]  # planted in the cushion
            low = self.lowest(root, r, t)
            if low < floor: root = root + [0, floor - low, 0]
        elif low < floor:
            root = root + [0, floor - low, 0]
        return root, r

    def tip(self, t):
        return wand_points(self.puppet_pose(t))[1]


# ------------------------------------------------------------------ the camera

ORIGINAL_THIRD = B.third_person


def third_person(scene, t):
    if t < GROUND or t >= LANDING: return ORIGINAL_THIRD(scene, t)
    s = clock(t)
    root, r = scene.player(t)
    chest = root + r@B.CHEST
    if t < RT['drop_cut']:
        # Falling beside you, a little ahead and below: you catch yourself out of the tumble, your
        # head snaps down to the ground (a punch in), you wind up and pitch the pod; we glance after it.
        p0, v0 = scene.throw
        v = v0 + np.array([0, -B.GRAVITY, 0])*(t - THROW)/20
        d = v*np.array([1, 0, 1]); d = d/np.linalg.norm(d)
        side = np.cross([0, 1, 0], d)
        eye = chest + side*(4.2 - .6*ramp(t, GROUND, RT['drop_cut'])) + d*2.6 + [0, -1.2 - .8*ramp(t, TOSS - 2, RT['drop_cut']), 0]
        target = mix(chest, chest + v/np.linalg.norm(v)*6, .08 + .12*ramp(t, TOSS, RT['drop_cut']))
        pod = pod_place(scene, t)
        if pod is not None: target = mix(target, pod, .35*ramp(t, TOSS, TOSS + 1.5))
        yaw, pitch = look(eye, target)
        sh = shake(t, 40, .9 + 1.2*kick(t, TOSS + .5, 1, 2, 1.5)**2 + .8*kick(t, SPOT + .5, 1, 1.5, 1.5)**2)
        fov = 56 + 6*ramp(t, TOSS, RT['drop_cut']) - 10*pulse(t, SPOT, SPOT + .7, WIND + 1.5, TOSS - .2)
        return eye, yaw + sh[0], pitch + sh[1], sh[2]*2 - 6*math.sin(t*.1), float(fov)
    if t < RT['apex_cut']:
        # Low on the snow beside the run-in: the pod streaks down and bursts open, you drop onto it,
        # it dips under you and fires you straight back up past us.
        eye = np.array([5.4, 1.3, 24.4]) + [-.5*ramp(t, RT['drop_cut'], RT['apex_cut']), .5*ramp(t, RT['launch'], RT['apex_cut']), .8*ramp(t, RT['drop_cut'], RT['apex_cut'])]
        target = mix(chest, PAD + [0, .5, 0], .62*(1 - ramp(t, RT['bounce'] - 1, RT['launch'] + 2)))
        pod = pod_place(scene, t)
        if pod is not None: target = mix(target, pod, .5)
        yaw, pitch = look(eye, target)
        hit = kick(t, RT['bounce'], 3.5, 4, 1.4) + kick(t, RT['open'], 1.5, 2, 2.0)
        sh = shake(t, 41, .45 + 1.8*math.exp(-max(0, t - RT['bounce'])/5)*(t >= RT['bounce']) + 1.2*math.exp(-max(0, t - RT['launch'])/3)*(t >= RT['launch']))
        punch = 5*math.exp(-max(0, t - RT['bounce'])/3)*(t >= RT['bounce'])
        fov = 60 - punch + 18*ramp(t, RT['launch'], RT['launch'] + 7)
        return eye, yaw + sh[0], pitch + sh[1] + hit, sh[2], float(fov)
    if t < RT['reel_cut']:
        # Bullet time: circling you from below and in front, out round your wand side, to over your
        # shoulder looking down at the snow and up at the spire, as the wand charges. Then the shot
        # snaps us after the vine, and you are torn away down it.
        u = smooth((t - RT['apex_cut'])/(RT['shoot'] - RT['apex_cut']))
        phi = math.radians(mix(130, 40, u))
        radius = float(mix(5.8, 4.6, u))
        eye = chest + [math.sin(phi)*radius, float(mix(-1.9, 1.0, u)), math.cos(phi)*radius]
        target = mix(chest + [0, .15, 0], SPIRE, .08*u)
        target = mix(target, ANCHOR, .25*u)
        head, shown, _ = vine_tip(scene, t)
        if head is not None: target = mix(target, head, .4*ramp(t, RT['shoot'], RT['latch']))
        target = mix(target, chest, .6*ramp(t, RT['reel'], RT['reel_cut']))
        yaw, pitch = look(eye, target)
        sh = shake(t, 43, .3 + 2.0*kick(t, RT['shoot'], 1, 2, 1.5)**2 + 1.4*ramp(t, RT['latch'], RT['latch'] + 1))
        fov = float(mix(64, 54, u)) + 14*math.exp(-max(0, t - RT['shoot'])/2.5)*(t >= RT['shoot'])
        return eye, yaw + sh[0], pitch + sh[1], sh[2] + float(mix(9, -5, u)), fov
    if t < RT['crane_cut']:
        # Low on the snow ahead of where you stop, off to one side: you swing down the vine, hit
        # the snow and slide toward us, spraying snow, the green coming up behind you.
        push = ramp(t, RT['stop'], RT['crane_cut'])
        eye = ARRIVAL + [-5.2, .6, -.6] + [.6*push, .05*push, .2*push]
        target = mix(chest, ARRIVAL + [0, .8, 1.0], .5*ramp(t, RT['touch'] - 1, RT['stop']))
        yaw, pitch = look(eye, target)
        sh = shake(t, 45, .35 + 1.0*math.exp(-max(0, t - RT['touch'])/4)*(t >= RT['touch'])*(1 - ramp(t, RT['touch'], RT['stop'] + 2)))
        fov = 62 - 10*ramp(t, RT['reel_cut'], RT['stop'])
        return eye, yaw + sh[0], pitch + sh[1], sh[2]*1.4, float(fov)
    if t < RT['look_cut']:
        # High over you: the green track the slide left, grass and flowers spreading out round you.
        u = ramp(t, RT['crane_cut'], RT['look_cut'])
        a = math.radians(55 + 18*u)
        c = ARRIVAL + [0, 0, 1.6]
        eye = c + [math.sin(a)*mix(8.5, 7.0, u), float(mix(8.5, 6.5, u)), math.cos(a)*mix(8.5, 7.0, u)]
        yaw, pitch = look(eye, c + [0, .3, 0])
        sh = shake(t, 46, .25)
        return eye, yaw + sh[0], pitch + sh[1], sh[2], 56.0
    # Low behind you, over your wand shoulder: head bowed; then as it comes up, we tilt up with
    # it past your shoulder to the Roc coming back round to its spire, closing in on it.
    up = ramp(t, RT['look'] - 1, RT['look'] + 7)
    eye = ARRIVAL + [1.2, .75, 3.6] + [-.15*up, -.15*up, .3*up]
    (_, _), w = scene.roc_world(t)
    head = scene.eye(t)
    aim = head + (w['body'] - head)/np.linalg.norm(w['body'] - head)*14  # along your gaze, out toward it
    target = mix(chest + [0, .2, -1.5], aim, .05 + .9*up**1.3)
    yaw, pitch = look(eye, target)
    sh = shake(t, 47, .2)
    fov = 50 - 12*up
    return eye, yaw + sh[0], pitch + sh[1], sh[2] - 2*up, float(fov)


# ------------------------------------------------------------------ the Springbloom, the vine, the bursts

def vine_tip(scene, t):
    """(head, shown, taut): the vine's head at real tick t. Out of the wand, flying to the anchor;
    biting; reeling you in taut; then let go and snapping back into the wand."""
    s = clock(t)
    if s < SHOOT or s >= RELEASE + 2.5: return None, 0.0, 0.0
    if s < LATCH:
        u = EASE['out']((s - SHOOT)/(LATCH - SHOOT))
        return mix(scene.tip(real(SHOOT)), ANCHOR, u), 1.0, 0.0
    if s < RELEASE: return ANCHOR.copy(), 1.0, ramp(s, LATCH, REEL)
    u = EASE['in']((s - RELEASE)/2.5)
    return mix(ANCHOR, scene.tip(t), u), 1 - u*u, 0.0


def pod_place(scene, t):
    """The pod, from the wand's tip at the pitch to the snow where it opens."""
    s = clock(t)
    if not TOSS <= s < OPEN: return None
    u = (s - TOSS)/(OPEN - TOSS)
    return mix(scene.tip(TOSS), PAD + [0, .25, 0], u**1.15) + [0, .8*math.sin(math.pi*u), 0]


def bursts():
    """One-shot effects: where, and when on both clocks (game ticks)."""
    def b(s, kind, at): return {'tick': round(real(s)*B.PACE, 2), 'clock': round(s*B.PACE, 2), 'kind': kind, 'at': B.r3(at)}
    return [b(OPEN, 'pollen', PAD + [0, .6, 0]), b(OPEN, 'grass', PAD), b(BOUNCE, 'bounce', PAD + [0, .8, 0]),
            b(LAUNCH, 'launch', PAD + [0, 1.2, 0]), b(LATCH, 'bite', ANCHOR)]


def nature_columns(scene):
    keys = ('n_clock', 'n_pod', 'n_pod_on', 'n_tip', 'n_vine', 'n_taut', 'n_leaf', 'n_open', 'n_squash', 'n_wilt',
            'n_glow', 'n_trail', 'n_wtip', 'n_speed', 'n_impact', 'n_bt')
    cols = {k: [] for k in keys}
    hits = [(RT['latch'], .2), (RT['shoot'], .15)]
    for g in range(LENGTH*B.PACE + 1):
        t = g/B.PACE
        s = clock(t)
        put = lambda k, v: cols[k].append(round(float(v), 3))
        put('n_clock', s*B.PACE)
        pod = pod_place(scene, t)
        cols['n_pod'].append(B.r3(pod if pod is not None else PAD)); put('n_pod_on', pod is not None)
        head, shown, taut = vine_tip(scene, t)
        cols['n_tip'].append(B.r3(head if head is not None else ANCHOR)); put('n_vine', shown); put('n_taut', taut)
        put('n_leaf', ramp(s, LATCH, LATCH + 3)*(s < RELEASE + 2.5))
        put('n_open', EASE['back'](min(1, max(0, (s - OPEN)/2.0))) if s >= OPEN else 0)
        put('n_squash', squash(s))
        put('n_wilt', ramp(t, LANDING + 4, LANDING + 20))
        glow = (.75*ramp(s, WIND, TOSS - .4)*(s < TOSS) + (math.exp(-(s - TOSS)/1.5) if s >= TOSS else 0)
                + ramp(s, APEX - 2, SHOOT - .2)*(s < SHOOT) + (math.exp(-(s - SHOOT)/2) if s >= SHOOT else 0)
)
        put('n_glow', min(1.0, glow))
        put('n_trail', min(1.0, pulse(s, WIND + 2, TOSS - .6, TOSS + 1.2, TOSS + 2.8) + pulse(s, SHOOT - .3, SHOOT, SHOOT + 1, SHOOT + 2.5)))
        cols['n_wtip'].append(B.r3(scene.tip(t)) if t >= THROW else [0, 0, 0])
        put('n_speed', .45*pulse(t, GROUND + 1, GROUND + 4, RT['drop_cut'] - .5, RT['drop_cut']) +
            .4*pulse(t, RT['launch'], RT['launch'] + .5, RT['apex_cut'] - 1, RT['apex_cut']) +
            .5*pulse(t, RT['reel_cut'], RT['reel_cut'] + .5, RT['touch'] - .5, RT['touch'] + 2))
        put('n_impact', min(1.0, sum(k*math.exp(-(t - at)/1.0) for at, k in hits if t >= at)))
        put('n_bt', bullet(s))
    return cols


ORIGINAL_CUES = B.cues


def cues(scene):
    out = [c for c in ORIGINAL_CUES(scene) if c['cue'] not in ('crash',)]
    out += [{'tick': int(round(t)), 'cue': c, 'from': 'player'} for t, c in (
        (RT['right'] - 2, 'catch_yourself'), (RT['wind'], 'pod_charge'), (RT['toss'], 'pod_pitch'), (RT['open'], 'springbloom_burst'),
        (RT['bounce'], 'springbloom_bounce'), (RT['apex'] - 2, 'slowmo_in'), (RT['apex'] - 2, 'wand_charge'),
        (RT['shoot'], 'vine_shoot'), (RT['latch'], 'vine_bite'), (RT['reel'], 'vine_swing'), (RT['touch'], 'slide'),
        (RT['touch'], 'grass_trail'), (RT['release'], 'vine_release'), (RT['stop'], 'skid_stop'))]
    return sorted(out, key=lambda c: c['tick'])


# ------------------------------------------------------------------ baking

SHOTS = [['1 · quiet', 0], ['2 · caw', 'caw'], ['3 · snatch', 'snatch'], ['4 · carried', 'grip', 8], ['5 · reveal', 'look_up'],
         ['6 · clouds', 'cloud'], ['7 · struggle', 'summit'], ['8 · throw', 'throw'], ['9 · the fall', 'ground'],
         ['10 · springbloom', 'drop_cut'], ['11 · bullet time', 'apex_cut'], ['12 · the slide', 'reel_cut'],
         ['13 · the trail', 'crane_cut'], ['14 · the glare', 'look_cut'], ['15 · the Roc lands', 'landing'], ['16 · hand back', 'back']]
SHOT_NAMES = ['quiet', 'caw', 'snatch', 'carried off', 'the reveal', 'storm clouds', 'the struggle', 'the throw', 'the fall',
              'springbloom', 'bullet time', 'the slide', 'the trail', 'the glare', 'the Roc lands', 'hand back']


def build():
    global SCENE
    retime()
    B.summit_body_path = summit_body_path
    B.puppet_channels = puppet_channels
    B.third_person = third_person
    B.cues = cues
    scene = NatureScene()
    SCENE = scene
    roc_clip = B.bake(lambda seconds: B.roc_channels(seconds*20/B.PACE), LENGTH*B.PACE/20, B.RATE, .02)
    player_clip = B.bake(lambda seconds: puppet_channels(seconds*20/B.PACE), LENGTH*B.PACE/20, B.RATE*2, .05)
    track = B.bake_track(scene)
    track['ticks'].update(nature_columns(scene))
    track['nature'] = {'pad': PAD.tolist(), 'arrival': ARRIVAL.tolist(), 'anchor': ANCHOR.tolist(), 'bursts': bursts(),
                       'slide': {'from': B.r3(TOUCHDOWN_AT), 'to': B.r3(ARRIVAL), 'c0': TOUCH*B.PACE, 'c1': STOP*B.PACE},
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
    meshes = json.loads(NATURE_MESHES.read_text())
    (PREVIEW/'springbloom.json').write_text(compact({k: meshes[k] for k in ('springbloom_pad', 'springbloom_wilt', 'springbloom_pod')}))
    for source, name in {B.ASSETS/'textures/entity/storm_roc.png': 'storm_roc.png',
                         B.ASSETS/'textures/entity/storm_roc_glowmask.png': 'storm_roc_glowmask.png',
                         B.ASSETS/'geckolib/models/storm_gong.geo.json': 'storm_gong.geo.json',
                         B.ASSETS/'textures/block/storm_gong.png': 'storm_gong.png',
                         HERE/'animatic.js': 'animatic.js'}.items():
        shutil.copyfile(source, PREVIEW/name)
    page = (HERE/'viewer.template.html').read_text(encoding='utf-8')
    page = page.replace('<title>Storm Roc Intro Animatic</title>', '<title>Roc Intro Nature</title>')
    page = page.replace('<h1>Storm Roc intro — animatic</h1>', '<h1>Storm Roc intro — Nature landing</h1>')
    (PREVIEW/'index.html').write_text(page, encoding='utf-8')
    if B.MINECRAFT.exists():
        import zipfile
        with zipfile.ZipFile(B.MINECRAFT) as jar:
            for kind, name in (('wide', 'steve'), ('slim', 'alex')):
                (PREVIEW/f'skin_{name}.png').write_bytes(jar.read(f'assets/minecraft/textures/entity/player/{kind}/{name}.png'))
    else:
        for name in ('steve', 'alex'):
            old = B.PREVIEW/f'skin_{name}.png'
            if old.exists(): shutil.copyfile(old, PREVIEW/f'skin_{name}.png')
            else: print(f'no default skin for {name}: the animatic needs skin_{name}.png')
    print(f'Nature landing baked: {LENGTH*B.PACE/20:.1f} s, wand alignment {ALIGN:.1f} deg, into {PREVIEW.relative_to(B.REPO)}')
    print('real beats:', {k: v for k, v in RT.items()}, 'landing', LANDING, 'length', LENGTH)


if __name__ == '__main__':
    write_preview()
