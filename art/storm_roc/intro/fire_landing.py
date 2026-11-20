#!/usr/bin/env python3
"""The Storm Roc's intro with Fire's landing: build_intro.py's scene from the throw on.

Everything up to the throw is build_intro's. Then a Fire wizard comes out of the tumble belly down
like a skydiver, spots the ground, and thrusts the wand down at it. A Flamethrower jet lights and
brakes the fall like a rocket thruster: it swings them upright and round to face the home spire,
and they sink on it, the jet splashing across the snow and boiling it off as steam. A few blocks
up they cut the flame, drop, and land a Fire Leap at the arrival: the landing blast, a ring of
fire rolling out across the snow, a short skid back, low flames licking round them as they hold
the crouch. Head bowed; then they look up at the spire, and the scene cuts to the Roc landing on
it as before.

The scene is authored in story time: the speed curve below slows the landing a little. The
camera runs in real time. The Roc's flight and wingbeats run on the story clock too.

Fire's effects (art/storm_roc/intro/landing_fire.js) are "block fire": emissive cubes from the
Fire atlas that rise, tumble, cool and shrink away, ported from art/fire/blockfire/.

Run alone, this writes a browser animatic to .local-previews/storm-roc-intro-fire/.
install_landings.py fire installs it into the game, where its effects are
client/roclanding/FireLanding.java. Run from the repository root:
    py -3 art/storm_roc/intro/fire_landing.py
then serve that folder (python -m http.server 8401) and open it.
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
from build_intro import EASE, curve, heading, kick, look, mix, pulse, ramp, shake, slerp, wobble  # noqa: E402
from gecko import Rx as Rx4, T as T4  # noqa: E402

PREVIEW = B.REPO/'.local-previews/storm-roc-intro-fire'
ATLAS = B.REPO/'art/fire/blockfire/textures/blockfire.png'

# ------------------------------------------------------------------ story beats

# In story ticks (one is 0.1 s at normal speed). Until the landing, story and real ticks agree.
THROW, GROUND = B.THROW, B.GROUND
RIGHT = 234      # out of the tumble: belly down, arms and legs spread, head first along the throw
SPOT = 235.5     # the head comes up: the ground ahead
DRAW = 237       # the wand arm thrust down at the snow, its tip heating
IGNITE = 239     # the jet lights: a thruster, braking hard
TURNED = 244     # swung upright on it, and round to face the spire
LEVEL = 248      # the brake done: upright, the jet straight down, sinking toward the snow
CUT = 256        # the flame cut, a few blocks above the snow

FLOOR = B.ARRIVAL[1]
ARRIVAL = B.ARRIVAL.copy()           # where you stop, and the fight expects you
SKID = 1.1                           # the Fire Leap's short skid: back along the way you came
LAND_AT = ARRIVAL + [0, 0, -SKID]    # where your feet meet the snow
DROP_VY, DROP_VZ = 5.0, 3.6          # still sinking and drifting when the flame cuts (blocks/s)
CUT_FEET = 3.0                       # how high your feet are when it cuts
G = B.GRAVITY
DROP = (-DROP_VY + math.sqrt(DROP_VY**2 + 2*G*CUT_FEET))/G   # seconds of the fall to the snow
TOUCH = CUT + DROP*20                # feet on the snow: the Fire Leap lands
STOP = TOUCH + 3.5                   # the skid stopped, crouched at the arrival
HIPS_Y = B.HIPS[1]

P_CUT = LAND_AT + [0, CUT_FEET + HIPS_Y, -DROP_VZ*DROP]       # your hips when the flame cuts
V_CUT = np.array([0, -DROP_VY, DROP_VZ])
P_LEVEL = P_CUT + [0, 2.5, -1.6]                               # and when the brake is done
V_LEVEL = np.array([0, -9.0, 3.4])
REACH = 8.5                          # the jet's length (the Flamethrower's is 6)
WAND_TIP = 0.72                      # the animatic's wand: tip 0.72 out along its axis
SPIRE = B.HOME_PERCH + [0, 6, 0]


def speed(s):
    """How fast story time runs at story tick s: 1 normal, under 1 slow motion."""
    return 1.0 - .55*pulse(s, TOUCH - .8, TOUCH - .1, TOUCH + 2.2, TOUCH + 4.5)  # the landing, a beat slower


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

RT = {k: half(real(v)) for k, v in dict(right=RIGHT, spot=SPOT, draw=DRAW, ignite=IGNITE, turned=TURNED, level=LEVEL,
                                         cut=CUT, touch=TOUCH, stop=STOP).items()}
RT['burn_cut'] = half(real(IGNITE + 2.5))               # under you, off to the side: the swing upright
RT['sink_cut'] = half(real(LEVEL + 1))                  # low on the snow ahead of the arrival
RT['crane_cut'] = math.ceil(RT['stop']) + 8             # high over the ring burning down
RT['look_cut'] = RT['crane_cut'] + 12                   # low behind you
RT['look'] = RT['look_cut'] + 4                         # the head comes up
LANDING = RT['look'] + 12                               # cut: the Roc coming down onto its spire, as before
TOUCHDOWN = LANDING + 2
LAND = TOUCHDOWN - 16
SETTLED = LAND + 50
BACK = LANDING + 32
UP = BACK + 6
RETURN = UP + 1
LENGTH = RETURN + 20
BOLT = half(real(TURNED - 1))                           # a bolt behind you as you swing upright

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


def slow(s): return (1 - speed(s))/.55


def overlays(scene, t):
    o = ORIGINAL_OVERLAYS(scene, t)
    o['bars'] = o['bars']*(1 + .3*slow(clock(t)))  # the letterbox closes in a little on the slow landing
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
    g['CUTS'] = [B.SUMMIT, THROW, GROUND, RT['burn_cut'], RT['sink_cut'], RT['crane_cut'], RT['look_cut'], LANDING, BACK]


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
SCENE = None  # set once built: the arm aims the jet from where the body is
AIMS = {}


def K(s, *rows):
    """A value keyed through rows of (story tick, ease into it, value)."""
    return curve(s, list(rows))[0]


def unit(v): return np.asarray(v, dtype=float)/np.linalg.norm(v)


def herm(p0, v0, p1, v1, s0, s1, s):
    """A cubic from p0 moving at v0 (blocks/s) at story tick s0 to p1 moving at v1 at s1."""
    T, u = (s1 - s0)/20, (s - s0)/(s1 - s0)
    return ((2*u**3 - 3*u**2 + 1)*p0 + (u**3 - 2*u**2 + u)*T*v0 + (-2*u**3 + 3*u**2)*p1 + (u**3 - u**2)*T*v1)


def jet_dir(s):
    """Where you point the jet: down, angled ahead into the throw while it brakes, straight down by LEVEL."""
    lean = float(K(s, (DRAW, 'io', .78), (IGNITE, 'io', .8), (TURNED, 'io', .42), (LEVEL, 'io', .06), (CUT, 'io', .04)))
    return unit([0, -1, lean])


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
    """The wand arm's rotation that points the wand along the jet from the body's place at story tick s."""
    key = round(s, 3)
    if key in AIMS: return AIMS[key]
    root, r = SCENE.body_at(s)
    base = np.eye(4); base[:3, :3] = r; base[:3, 3] = root
    want = jet_dir(s)

    def miss(ax, az):
        c = posture(s); c['right_arm'] = {'rotation': np.array([ax, 0, az])}
        c['wand'] = {'rotation': np.array([ALIGN, 0, 0])}
        hand, tip = wand_points(B.PUP.pose(c, base@B.ENTITY))
        d = (tip - hand)/np.linalg.norm(tip - hand)
        return 1 - float(d@want) + .00002*az*az  # the arm kept in by the side where it can be
    _, ax, az = min((miss(ax, az), ax, az) for ax in range(-232, 81, 8) for az in range(-40, 81, 10))
    for step in (3, 1):
        _, ax, az = min((miss(ax + i*step, az + k*step), ax + i*step, az + k*step) for i in (-2, -1, 0, 1, 2) for k in (-2, -1, 0, 1, 2))
    AIMS[key] = (ax, az)
    return ax, az


SKY_R, SKY_L = np.array([-40.0, 0, 84]), np.array([-40.0, 0, -84])


def posture(s):
    """Every bone but the aimed wand arm at story tick s (degrees; arms -X raise forward, +Z out on
    the right arm and -Z on the left; legs -X forward; waist +X bends forward; head +X looks down)."""
    c = {}
    def rot(b, r): c[b] = {'rotation': np.array(r, dtype=float)}
    if s < RIGHT:
        # Flung: flailing as you tumble, then spreading out flat to stop it, like a skydiver.
        fl = 1 - ramp(s, RIGHT - 5, RIGHT)
        rot('right_arm', mix([-120 + 50*math.sin(s*.6), 0, 60*fl + 20], SKY_R, 1 - fl))
        rot('left_arm', mix([-120 + 50*math.sin(s*.6 + 2), 0, -60*fl - 20], SKY_L, 1 - fl))
        rot('right_leg', [-30*fl + 40*math.sin(s*.5)*fl + 14*(1 - fl), 0, 14])
        rot('left_leg', [-30*fl - 40*math.sin(s*.5)*fl + 20*(1 - fl), 0, -14])
        rot('waist', [-20*fl - 12*(1 - fl), 0, 0]); rot('head', [30*fl - 20*(1 - fl), 0, 0])
        return c
    if s < IGNITE:
        # Spread out flat, buffeted; the head comes up to the ground ahead; the free arm swept
        # back out of the way as the wand arm (aimed below) thrusts down at the snow.
        flut = math.sin(s*1.9)*4, math.sin(s*1.6 + 1)*3
        rot('right_arm', SKY_R + [flut[0], 0, 0])
        rot('left_arm', K(s, (RIGHT, 'io', SKY_L), (DRAW, 'io', SKY_L), (IGNITE, 'io', [-60, 0, -66])) + [-flut[0], 0, 0])
        rot('right_leg', [14 + flut[1], 0, 14]); rot('left_leg', [20 - flut[1], 0, -14])
        rot('waist', K(s, (RIGHT, 'io', [-12, 0, 0]), (SPOT + .7, 'out', [-22, 0, 0]), (IGNITE, 'io', [-14, 0, 0])))
        rot('head', K(s, (RIGHT, 'io', [-20, 0, 0]), (SPOT, 'io', [-18, 0, 0]), (SPOT + .6, 'out', [-52, 0, 0]), (IGNITE, 'io', [-44, 0, 0])))
        return c
    tr = math.sin(s*7.3)*2.5 + math.sin(s*11.1)*1.5  # the thrust shaking through you
    if s < LEVEL:
        # The jet swings you upright: legs dropping under you, the free arm flung out for balance,
        # the head coming round with the body to the spire, then down to the snow.
        rot('right_arm', [-20, 0, 20])
        rot('left_arm', K(s, (IGNITE, 'io', [-60, 0, -66]), (IGNITE + 1.5, 'out', [-112, 0, -40]), (TURNED, 'io', [-40, 0, -80]),
                          (LEVEL, 'io', [-24, 0, -74])))
        rot('right_leg', K(s, (IGNITE, 'io', [14, 0, 14]), (IGNITE + 2, 'out', [-22, 0, 12]), (TURNED, 'io', [-30, 0, 8]),
                           (LEVEL, 'io', [-18, 0, 6])) + [tr, 0, 0])
        rot('left_leg', K(s, (IGNITE, 'io', [20, 0, -14]), (IGNITE + 2, 'out', [4, 0, -10]), (TURNED, 'io', [-4, 0, -6]),
                          (LEVEL, 'io', [-2, 0, -6])) - [tr, 0, 0])
        rot('waist', K(s, (IGNITE, 'io', [-14, 0, 0]), (IGNITE + 2, 'out', [24, 0, 0]), (TURNED, 'io', [12, 0, 0]), (LEVEL, 'io', [8, 0, 0])))
        rot('head', K(s, (IGNITE, 'io', [-44, 0, 0]), (IGNITE + 2, 'out', [-8, 0, 0]), (TURNED, 'io', [22, 0, 0]), (LEVEL, 'io', [30, 0, 0])))
        return c
    if s < CUT:
        # Riding it down: upright, knees soft, the free arm out, eyes on the snow coming up.
        sway = math.sin(s*.9)
        rot('right_arm', [-20, 0, 20])
        rot('left_arm', [-24 + 4*sway, 0, -74 + 3*sway])
        rot('right_leg', [-18 + tr*.6, 0, 6]); rot('left_leg', [-2 - tr*.6, 0, -6])
        rot('waist', [8 + 2*sway, 0, 0]); rot('head', [30 + 6*ramp(s, LEVEL, CUT), 0, 0])
        return c
    if s < TOUCH:
        # The flame cut: dropping, the arms coming up, the knees gathering, the free hand reaching
        # down for the snow.
        rot('right_arm', K(s, (CUT, 'io', [-20, 0, 20]), (CUT + 2, 'out', [-40, 0, 72]), (TOUCH, 'io', [10, 0, 64])))
        rot('left_arm', K(s, (CUT, 'io', [-20, 0, -74]), (CUT + 2, 'out', [-70, 0, -56]), (TOUCH, 'io', [-84, 0, -16])))
        rot('right_leg', K(s, (CUT, 'io', [-18, 0, 6]), (TOUCH - 1.5, 'io', [-42, 0, 10]), (TOUCH, 'io', [-50, 0, 10])))
        rot('left_leg', K(s, (CUT, 'io', [-2, 0, -6]), (TOUCH - 1.5, 'io', [12, 0, -6]), (TOUCH, 'io', [28, 0, -6])))
        rot('waist', K(s, (CUT, 'io', [8, 0, 0]), (TOUCH, 'io', [30, 0, 0])))
        rot('head', K(s, (CUT, 'io', [36, 0, 0]), (TOUCH, 'io', [22, 0, 0])))
        return c
    if s < STOP:
        # The landing: down into a crouch, the free hand planted, the wand arm thrown back and out,
        # skidding back a block.
        T_ = TOUCH
        rot('right_arm', K(s, (T_, 'io', [10, 0, 64]), (T_ + 1, 'out', [78, 0, 50]), (STOP, 'io', [74, 0, 48])))
        rot('left_arm', K(s, (T_, 'io', [-84, 0, -16]), (T_ + .8, 'out', [-66, 0, -8]), (STOP, 'io', [-70, 0, -8])))
        rot('right_leg', K(s, (T_, 'io', [-50, 0, 10]), (T_ + .8, 'out', [-62, 0, 10]), (STOP, 'io', [-56, 0, 10])))
        rot('left_leg', K(s, (T_, 'io', [28, 0, -6]), (T_ + .8, 'out', [66, 0, -6]), (STOP, 'io', [62, 0, -6])))
        rot('waist', K(s, (T_, 'io', [30, 0, 0]), (T_ + .8, 'out', [72, 0, 0]), (STOP, 'io', [64, 0, 0])))
        rot('head', K(s, (T_, 'io', [22, 0, 0]), (T_ + .8, 'out', [34, 0, 0]), (STOP, 'io', [26, 0, 0])))
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
    if SCENE is not None and DRAW - .8 <= s < CUT + 1.6:
        # The wand thrust down along the jet: whipped in, shaking with the thrust, kicked by the
        # ignition, let go when the flame cuts.
        ax, az = aim_arm(s)
        k = ramp(s, DRAW - .8, DRAW + .3)*(1 - ramp(s, CUT + .2, CUT + 1.6))
        on = pulse(s, IGNITE, IGNITE + .3, CUT - .2, CUT)
        tremble = (1.5*math.sin(s*23) + 1.0*math.sin(s*31))*on
        recoil = -14*kick(s, IGNITE, 1, 1.5, 1.0)
        base = c['right_arm']['rotation']
        c['right_arm'] = {'rotation': base*(1 - k) + np.array([ax + recoil + tremble, 0, az])*k}
        c['wand'] = {'rotation': np.array([ALIGN*k, 0, 0])}
    return c


def skid(s):
    """Your feet's place on the snow through the skid: back off the touchdown, easing to a stop."""
    u = min(1, max(0, (s - TOUCH)/(STOP - TOUCH)))
    return mix(LAND_AT, ARRIVAL, EASE['out'](u))


class FireScene(B.Scene):

    def ballistic(self):
        """The throw: a lob from the talons that reaches the ignition point at IGNITE, moving so that
        a steady brake from there takes you to P_LEVEL at V_LEVEL by LEVEL."""
        (_, _), w = self.roc_world(THROW)
        p0 = w['grip']
        tau, T1, g = (IGNITE - THROW)/20, (LEVEL - IGNITE)/20, np.array([0, -G, 0])
        # p_ign = P_LEVEL - T1/2*(v_ign + V_LEVEL), v_ign = (p_ign - p0)/tau + g*tau/2
        p_ign = (P_LEVEL - T1/2*(-p0/tau + g*tau/2 + V_LEVEL))/(1 + T1/(2*tau))
        self.p_ign = p_ign
        self.v_ign = (p_ign - p0)/tau + g*tau/2
        return p0, (p_ign - p0 - .5*g*tau*tau)/tau

    def hips(self, s):
        """Where your hips are at story tick s, from the throw on."""
        if s < IGNITE: return self.thrown(s)
        wob = np.array([.12*math.sin(s*1.7), .05*math.sin(s*2.3), .1*math.sin(s*1.3)])*pulse(s, IGNITE, IGNITE + 1, CUT - 1, CUT)
        if s < LEVEL: return herm(self.p_ign, self.v_ign, P_LEVEL, V_LEVEL, IGNITE, LEVEL, s) + wob
        if s < CUT: return herm(P_LEVEL, V_LEVEL, P_CUT, V_CUT, LEVEL, CUT, s) + wob
        if s < TOUCH:
            tau = (s - CUT)/20
            return P_CUT + V_CUT*tau + .5*np.array([0, -G, 0])*tau*tau
        return skid(s) + [0, HIPS_Y, 0]

    def rotation(self, s):
        if s < RIGHT:
            # One fast forward tumble out of its grip, caught flat, belly down, as you spread out.
            u = (s - THROW)/(RIGHT - THROW)
            spin = heading(6*wobble(s, 31, 1.3)*(1 - u), 88 + 360*EASE['out'](u), 10*wobble(s, 32, 1.1)*(1 - u))
            return slerp(self.held(THROW), spin, ramp(s, THROW, THROW + 4))
        if s < IGNITE:
            calm = ramp(s, RIGHT, RIGHT + 2)
            p = K(s, (RIGHT, 'io', 88.0), (SPOT + .7, 'out', 74.0), (IGNITE, 'io', 80.0))
            return heading(3*math.sin(s*.3)*calm, float(p) + 2*math.sin(s*.9)*calm, 4*math.sin(s*.25)*calm)
        if s < LEVEL:
            # Swung upright by the jet and round to face the spire, overshooting and settling.
            yaw = K(s, (IGNITE, 'io', 0.0), (IGNITE + .8, 'in', 10.0), (TURNED, 'out', 180.0))
            pitch = K(s, (IGNITE, 'io', 80.0), (IGNITE + 2.5, 'out', 26.0), (TURNED, 'io', -9.0), (LEVEL, 'io', 4.0))
            roll = K(s, (IGNITE, 'io', 0.0), (IGNITE + 2.5, 'out', -14.0), (TURNED, 'io', 6.0), (LEVEL, 'io', 0.0))
            return heading(float(yaw), float(pitch), float(roll))
        if s < CUT:
            k = ramp(s, LEVEL, LEVEL + 1)
            return heading(180 + 3*math.sin(s*.4)*k, 4 + 2*math.sin(s*.7)*k, 3*math.sin(s*.55)*k)
        if s < TOUCH:
            return heading(180, float(K(s, (CUT, 'io', 4.0), (TOUCH, 'io', 10.0))))
        if s < STOP:
            # Skidding back: leaning into it, a little kicked round, straightening as it slows.
            u = (s - TOUCH)/(STOP - TOUCH)
            e = 1 - EASE['out'](u)
            return heading(180 + 10*e, 10*e, 3*math.sin(u*9)*(1 - u))
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
        if s >= TOUCH or low < FLOOR:
            root = root + [0, FLOOR - low, 0]  # planted: skidding, then stopped
        return root, r

    def tip(self, t):
        return wand_points(self.puppet_pose(t))[1]

    def wand_dir(self, t):
        hand, tip = wand_points(self.puppet_pose(t))
        return unit(tip - hand)


# ------------------------------------------------------------------ the camera

ORIGINAL_THIRD = B.third_person


def third_person(scene, t):
    if t < GROUND or t >= LANDING: return ORIGINAL_THIRD(scene, t)
    s = clock(t)
    root, r = scene.player(t)
    chest = root + r@B.CHEST
    if t < RT['burn_cut']:
        # Falling beside you, a little ahead and below: you spread out of the tumble, your head comes
        # up to the ground (a punch in), the wand thrusts down and the jet lights; it brakes you
        # hard, and we fall on past you for a moment before we catch up.
        p0, v0 = scene.throw
        v = v0 + np.array([0, -G, 0])*(min(t, IGNITE) - THROW)/20
        d = v*np.array([1, 0, 1]); d = d/np.linalg.norm(d)
        side = np.cross([0, 1, 0], d)
        tau = max(0.0, s - IGNITE)/20
        overshoot = (scene.thrown(s) - scene.hips(s))*.45*(1 - ramp(s, IGNITE + 1, IGNITE + 3.5)) if s > IGNITE else 0
        eye = chest + overshoot + side*(4.4 - .5*ramp(t, GROUND, RT['burn_cut'])) + d*2.2 + [0, -1.1 - .7*ramp(t, DRAW, RT['ignite']), 0]
        target = mix(chest, chest + v/np.linalg.norm(v)*6, .08 + .1*ramp(t, SPOT, DRAW))
        target = mix(target, scene.tip(t) + jet_dir(s)*2.5, .22*ramp(t, DRAW, IGNITE))
        yaw, pitch = look(eye, target)
        sh = shake(t, 40, .9 + .8*kick(t, SPOT + .5, 1, 1.5, 1.5)**2 + 2.6*math.exp(-max(0, t - IGNITE)/3)*(t >= IGNITE))
        fov = 56 - 9*pulse(t, SPOT, SPOT + .7, DRAW + .5, IGNITE - .2) + 12*math.exp(-max(0, t - IGNITE)/2.5)*(t >= IGNITE) + 0*tau
        return eye, yaw + sh[0], pitch + sh[1], sh[2]*2 - 6*math.sin(t*.1), float(fov)
    if t < RT['sink_cut']:
        # Under you and off to the side, looking up past you at the storm: the jet stabbing down and
        # away, swinging you upright and round to face the spire.
        u = ramp(t, RT['burn_cut'], RT['sink_cut'])
        eye = chest + [float(mix(6.2, 5.0, u)), float(mix(-3.4, -2.2, u)), float(mix(1.8, -1.6, u))]
        target = mix(chest + [0, .2, 0], scene.tip(t) + jet_dir(s)*2.0, .25)
        yaw, pitch = look(eye, target)
        sh = shake(t, 43, .9*(1 - .4*u))
        return eye, yaw + sh[0], pitch + sh[1], sh[2] + float(mix(8, 2, u)), float(mix(64, 58, u))
    if t < RT['crane_cut']:
        # Low on the snow ahead of the arrival, off to one side: you sink toward us on the jet, it
        # splashes across the snow and boils it off; the flame cuts, you drop, and the Fire Leap
        # lands, its ring rolling out at us and stopping short; a short skid, and the crouch. Inside
        # the summit's boulder-free ring round the arrival (7 blocks), so the real summit's rocks and
        # rises beyond it never stand in front of you.
        push = ramp(t, RT['stop'], RT['crane_cut'])
        eye = ARRIVAL + [-4.7, 2.7, -4.9] + [.4*push, -.2*push, .4*push]
        target = mix(chest, LAND_AT + [0, .2, 0], .34)  # you and the snow the jet is beating on
        target = mix(target, ARRIVAL + [0, .5, 0], .8*ramp(t, RT['cut'] + 1, RT['touch'] + 2))
        yaw, pitch = look(eye, target)
        jolt = kick(t, RT['touch'], 2.5, 3, 1.6)
        sh = shake(t, 45, .3 + .5*pulse(t, RT['sink_cut'], RT['sink_cut'] + 1, RT['cut'] - 1, RT['cut'] + 1)
                   + 1.2*math.exp(-max(0, t - RT['touch'])/5)*(t >= RT['touch']))
        fov = 60 - 9*ramp(t, RT['cut'], RT['touch'] + 1) - 4*ramp(t, RT['touch'], RT['crane_cut'])
        return eye, yaw + sh[0], pitch + sh[1] + jolt, sh[2]*1.4, float(fov)
    if t < RT['look_cut']:
        # High over you: the ring of fire burning down round you on the scorched snow.
        u = ramp(t, RT['crane_cut'], RT['look_cut'])
        a = math.radians(40 + 18*u)
        c = LAND_AT + [0, 0, .4]
        eye = c + [math.sin(a)*mix(10.5, 9.0, u), float(mix(6.0, 4.8, u)), math.cos(a)*mix(10.5, 9.0, u)]
        yaw, pitch = look(eye, c + [0, .3, 0])
        sh = shake(t, 46, .25)
        return eye, yaw + sh[0], pitch + sh[1], sh[2], 58.0
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


# ------------------------------------------------------------------ the effects' data

def bursts(scene):
    """One-shot effects: where, and when on both clocks (game ticks)."""
    def b(s, kind, at): return {'tick': round(real(s)*B.PACE, 2), 'clock': round(s*B.PACE, 2), 'kind': kind, 'at': B.r3(at)}
    return [b(IGNITE, 'ignite', scene.tip(real(IGNITE))), b(CUT, 'cut', scene.tip(real(CUT))),
            b(TOUCH, 'touch', [LAND_AT[0], FLOOR, LAND_AT[2]])]


def fire_columns(scene):
    keys = ('n_clock', 'f_rate', 'f_tip', 'f_dir', 'f_power', 'f_heat', 'f_melt', 'f_speed', 'f_impact', 'f_bt', 'f_flames', 'f_jetfx')
    cols = {k: [] for k in keys}
    melt = 0.0
    for g in range(LENGTH*B.PACE + 1):
        t = g/B.PACE
        s = clock(t)
        put = lambda k, v: cols[k].append(round(float(v), 3))
        put('n_clock', s*B.PACE)
        put('f_rate', speed(s))
        if t >= THROW:
            d = scene.wand_dir(t)
            cols['f_dir'].append(B.r3(d))
            tip = scene.tip(t)
        else:
            cols['f_dir'].append([0, -1, 0]); tip = np.array([0, 99.0, 0])
        cols['f_tip'].append(B.r3(tip))  # the wand's tip, for the game (the browser takes it from the posed model)
        on = 1.0 if IGNITE <= s < CUT else 0.0
        put('f_power', K(s, (IGNITE, 'io', 1.25), (TURNED, 'io', 1.1), (LEVEL, 'io', .92), (CUT, 'io', .88)))
        put('f_heat', ramp(s, DRAW - .5, IGNITE)*(s < IGNITE) + (math.exp(-(s - CUT)/6) if s >= CUT else 0))
        melt = max(melt, on*min(1.0, max(0.0, (REACH + .5 - (tip[1] - FLOOR))/3.5)))
        put('f_melt', melt)
        put('f_speed', .5*pulse(t, GROUND + 1, GROUND + 4, IGNITE, IGNITE + 3))
        put('f_impact', math.exp(-(t - RT['touch'])/1.4) if t >= RT['touch'] else 0)
        put('f_bt', slow(s))
        put('f_flames', ramp(s, TOUCH + .5, TOUCH + 4)*(1 - .8*ramp(t, LANDING - 6, BACK + 10)))
        put('f_jetfx', pulse(s, IGNITE, IGNITE + .3, CUT, CUT + .5))  # the heat haze over the frame while it burns
    return cols


ORIGINAL_CUES = B.cues


def cues(scene):
    out = [c for c in ORIGINAL_CUES(scene) if c['cue'] not in ('crash',)]
    out += [{'tick': int(round(t)), 'cue': c, 'from': 'player'} for t, c in (
        (RT['right'] - 2, 'catch_yourself'), (RT['draw'], 'wand_heat'), (RT['ignite'], 'thruster_ignite'),
        (RT['ignite'] + 1, 'thruster_roar'), (half(real(LEVEL)), 'snow_hiss'), (RT['cut'], 'thruster_cut'),
        (RT['touch'], 'fire_leap_land'), (RT['touch'], 'fire_wave'), (RT['touch'] + 1, 'skid'),
        (RT['stop'], 'flames_crackle'))]
    return sorted(out, key=lambda c: c['tick'])


# ------------------------------------------------------------------ baking

SHOTS = [['1 · quiet', 0], ['2 · caw', 'caw'], ['3 · snatch', 'snatch'], ['4 · carried', 'grip', 8], ['5 · reveal', 'look_up'],
         ['6 · clouds', 'cloud'], ['7 · struggle', 'summit'], ['8 · throw', 'throw'], ['9 · the fall', 'ground'],
         ['10 · the burn', 'burn_cut'], ['11 · the landing', 'sink_cut'], ['12 · the ring', 'crane_cut'],
         ['13 · the glare', 'look_cut'], ['14 · the Roc lands', 'landing'], ['15 · hand back', 'back']]
SHOT_NAMES = ['quiet', 'caw', 'snatch', 'carried off', 'the reveal', 'storm clouds', 'the struggle', 'the throw', 'the fall',
              'the burn', 'the landing', 'the ring', 'the glare', 'the Roc lands', 'hand back']


def build():
    global SCENE
    retime()
    B.summit_body_path = summit_body_path
    B.puppet_channels = puppet_channels
    B.third_person = third_person
    B.cues = cues
    scene = FireScene()
    SCENE = scene
    roc_clip = B.bake(lambda seconds: B.roc_channels(seconds*20/B.PACE), LENGTH*B.PACE/20, B.RATE, .02)
    player_clip = B.bake(lambda seconds: puppet_channels(seconds*20/B.PACE), LENGTH*B.PACE/20, B.RATE*2, .05)
    track = B.bake_track(scene)
    track['ticks'].update(fire_columns(scene))
    track['landing'] = {
        'element': 'fire', 'script': 'landing_fire.js',
        'shots': [[label, beat, plus[0]*B.PACE] if plus else [label, beat] for label, beat, *plus in SHOTS], 'shot_names': SHOT_NAMES,
        'floor': FLOOR, 'arrival': B.r3(ARRIVAL), 'land_at': B.r3(LAND_AT), 'reach': REACH,
        'jet': {'c0': IGNITE*B.PACE, 'c1': CUT*B.PACE}, 'heat': {'c0': DRAW*B.PACE, 'c1': IGNITE*B.PACE},
        'touch': TOUCH*B.PACE, 'stop': STOP*B.PACE, 'melt_at': B.r3(LAND_AT + [0, 0, -.6]), 'bursts': bursts(scene)}
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
                         ATLAS: 'blockfire.png',
                         HERE/'animatic.js': 'animatic.js', HERE/'landing_fire.js': 'landing_fire.js'}.items():
        shutil.copyfile(source, PREVIEW/name)
    page = (HERE/'viewer.template.html').read_text(encoding='utf-8')
    page = page.replace('<title>Storm Roc Intro Animatic</title>', '<title>Roc Intro Fire</title>')
    page = page.replace('<h1>Storm Roc intro — animatic</h1>', '<h1>Storm Roc intro — Fire landing</h1>')
    (PREVIEW/'index.html').write_text(page, encoding='utf-8')
    if B.MINECRAFT.exists():
        import zipfile
        with zipfile.ZipFile(B.MINECRAFT) as jar:
            for kind, name in (('wide', 'steve'), ('slim', 'alex')):
                (PREVIEW/f'skin_{name}.png').write_bytes(jar.read(f'assets/minecraft/textures/entity/player/{kind}/{name}.png'))
    else:
        for old in (B.PREVIEW, B.REPO/'.local-previews/storm-roc-intro-nature'):
            for name in ('steve', 'alex'):
                if (old/f'skin_{name}.png').exists() and not (PREVIEW/f'skin_{name}.png').exists():
                    shutil.copyfile(old/f'skin_{name}.png', PREVIEW/f'skin_{name}.png')
        for name in ('steve', 'alex'):
            if not (PREVIEW/f'skin_{name}.png').exists(): print(f'no default skin for {name}: the animatic needs skin_{name}.png')
    print(f'Fire landing baked: {LENGTH*B.PACE/20:.1f} s, wand alignment {ALIGN:.1f} deg, into {PREVIEW.relative_to(B.REPO)}')
    print('ignition at', B.r3(scene.p_ign), 'moving', B.r3(scene.v_ign))
    print('real beats:', {k: v for k, v in RT.items()}, 'landing', LANDING, 'length', LENGTH)


if __name__ == '__main__':
    write_preview()
