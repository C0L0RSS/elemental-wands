#!/usr/bin/env python3
"""The Storm Roc's intro: snatched from the gong, carried into the storm, thrown onto the summit.

About 16 seconds in ten shots (docs/ROADMAP.md, "Wind: the Storm Roc"). Shots 1-6 are the
player's own eyes at the aerie: the last toll fades, a caw from behind, black talons snap in
from the edges of the screen and the peak falls away; looking down between the talons as it
climbs; looking up to see it for the first time as it screams down; then cloud and lightning.
The white-out hides the move into the sky realm. Shots 7-10 are close third person over the
summit's cloud sea: the player struggles in its talons, strikes one with their wand, is flung
the length of the summit, crashes, rolls and comes up facing the home spire as the Roc lands
on it; the camera glides into their eyes and the fight begins.

One script authors both actors and the camera from the same beats, so the player stays in the
Roc's talons and every shot frames them. It writes:
  storm_roc_intro.geo.json           the Roc's geometry with the jointed talon it holds you in
  storm_roc_intro.animation.json     the Roc's clip, composed from its installed flight, pounce,
                                     scream and land clips (its texture is the game's)
  player/storm_roc_intro_player*.geo.json  the player, wide and slim arms, wearing their own skin
  player/storm_roc_intro_player.animation.json  the player's clip
  intro-track.json                   per tick: which half, the camera, both actors' places, the
                                     overlays; the beats, cuts, lightning and sound cues
and the browser animatic in .local-previews/storm-roc-intro/ (launch entry storm-roc-intro-preview).

Run from the repository root:
    python3 art/storm_roc/intro/build_intro.py             # bake everything and the animatic
    python3 art/storm_roc/intro/build_intro.py --install   # also write the game's copies (geckolib/, cinematics/)
    python3 art/storm_roc/intro/build_intro.py --check     # fail if any output or installed copy has drifted

Frames. Each half has its own frame, in blocks: +Y up, yaw 0 looks along +Z, +X on the left of
someone facing +Z (Minecraft's). The aerie half's origin is the player's feet where they struck
the gong, facing it (the gong is 4 ahead). The summit half's frame is the summit layout's
(art/storm_summit): the slot centre's corner at SURFACE_Y, home spire to the north (-Z), the
arrival at (0, 1, 32). Actors are placed by a point and a rotation (w, x, y, z) in the frame;
the model faces +Z after GeckoLib's mirror and half turn, the Roc at 1.25 scale.
"""
import argparse
import copy
import json
import math
import shutil
import sys
import zipfile
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
sys.path.insert(0, str(HERE))
from gecko import ENTITY, Clip, Rig, S, T, bake  # noqa: E402
import puppet  # noqa: E402

ASSETS = REPO/'src/main/resources/assets/elementalwands'
PLAYER = HERE/'player'
PREVIEW = REPO/'.local-previews/storm-roc-intro'
MINECRAFT = Path.home()/'.gradle/caches/fabric-loom/1.21.10/minecraft-client.jar'  # default skins for the animatic
ROC_SCALE = 1.25
GRAVITY = 32.0  # blocks/s/s, near the game's

# Beats, in ticks from the confirming strike.
CAW = 40         # a caw, loud, from above and behind
SNATCH = 47      # its talons reach past your shoulders
GRIP = 53        # and close on you: a jolt, and the ground falls away
LOOK_UP = 96     # you look up at it: it is already glaring down at you, and it sinks in
SCREAM = 110     # it screams down at you
AWAY = 124       # you wrench your eyes away as the cloud closes over you
CLOUD = 126      # into the storm cloud: dark, lit from inside, streaming past
HOLD = 130       # the cloud shot can wait on this tick while the summit is raised and loads
WHITE = 132      # deepest in the cloud; the move into the sky realm happens while it stays a cloud
SUMMIT = 136     # cut, straight from your eyes: the summit's sky; it flies on looking ahead, you struggling in its talon
FREE = 182       # your wand arm wrenches free
STRIKE = 195     # and strikes the talon: a shock in your element's colour
SHRIEK = 197     # its head whips down to you and it shrieks
THROW = 211      # and flings you at the summit
GROUND = 228     # cut: low on the summit, you coming in out of the sky
CRASH = 252      # into the snow
LANDING = 258    # cut: the Roc coming down onto its spire, its name beneath it
TOUCHDOWN = 260  # its feet on the capstone
LAND = TOUCHDOWN - 16  # its landing clip begins on the approach
SETTLED = LAND + 50    # the landing clip ends in the perch; it holds there under its name
ROLLED = 263     # out of the backward roll, crouched, facing the spire
BACK = 290       # cut: behind you as you stand (the insert holds two seconds on the perched Roc)
UP = 293         # standing
RETURN = 294     # the camera glides into your eyes; the fight starts at LENGTH
LENGTH = RETURN + 20
TITLE, TITLE_END = LANDING + 3, BACK  # the name shows in the insert only, below the Roc
BEATS = dict(length=LENGTH, caw=CAW, snatch=SNATCH, grip=GRIP, look_up=LOOK_UP, scream=SCREAM, away=AWAY, cloud=CLOUD,
             white=WHITE, hold=HOLD, summit=SUMMIT, free=FREE, strike=STRIKE,
             shriek=SHRIEK, throw=THROW, ground=GROUND, crash=CRASH, landing=LANDING, touchdown=TOUCHDOWN, land=LAND,
             rolled=ROLLED, back=BACK, up=UP, title=TITLE, title_end=TITLE_END, ret=RETURN)
CUTS = [SUMMIT, THROW, GROUND, LANDING, BACK]
RATE = 40  # clip samples a second before key reduction
PACE = 2   # it plays at half the speed it is authored at: game ticks per authored tick

# The summit's layout (art/storm_summit/build_layout.py, StormSummitLayout).
HOME_PERCH = np.array([2.5, 36.0, -87.5])   # where the Roc stands on the home spire
HOME_YAW = 1.3                              # facing the summit's centre
ARRIVAL = np.array([0.5, 1.0, 32.5])        # where the fight expects you
SUMMIT_FACTS = {'perches': [[2, 36, -88], [73, 29, -54], [80, 32, 34], [7, 26, 92], [-75, 31, 52], [-83, 28, -41]],
                'rim': 58, 'cloud_y': -36, 'wisp_y': -32.5, 'cap_y': 58, 'cap_solid': 60, 'cap_edge': 125,
                'arrival': ARRIVAL.tolist(), 'home_perch': HOME_PERCH.tolist()}
EYE = 1.62

# ------------------------------------------------------------------ easing and noise


def smooth(s):
    s = min(1.0, max(0.0, s))
    return s*s*(3 - 2*s)


def ramp(t, a, b): return smooth((t - a)/(b - a)) if b != a else float(t >= a)
def mix(a, b, s): return np.array(a, dtype=float)*(1 - s) + np.array(b, dtype=float)*s
def pulse(t, a, b, c, d): return ramp(t, a, b)*(1 - ramp(t, c, d))


EASE = {'io': smooth, 'in': lambda s: s*s*s, 'out': lambda s: 1 - (1 - s)**3, 'lin': lambda s: s,
        'back': lambda s: 1 + 2.70158*(s - 1)**3 + 1.70158*(s - 1)**2}


def curve(t, rows):
    """Values at tick t from rows of (tick, ease, values...); each row's ease shapes the move into it."""
    if t <= rows[0][0]: return [np.array(v, dtype=float) for v in rows[0][2:]]
    for a, b in zip(rows, rows[1:]):
        if t <= b[0]:
            e = EASE[b[1]](min(1, max(0, (t - a[0])/(b[0] - a[0]))))
            return [np.array(x, dtype=float)*(1 - e) + np.array(y, dtype=float)*e for x, y in zip(a[2:], b[2:])]
    return [np.array(v, dtype=float) for v in rows[-1][2:]]


def wobble(t, seed, hz=1.0):
    """Smooth irregular drift in about -1..1."""
    rng = np.random.default_rng(seed)
    f = hz*np.array([1, 1.73, 2.91, 4.37])
    ph = rng.uniform(0, math.tau, 4)
    return float(np.sum(np.sin(math.tau*f*t/20 + ph)/[1, 1.6, 2.4, 3.4])/2.0)


def kick(t, at, size, decay=5.0, hz=1.6):
    """A jolt at tick at: an oscillation of this size that dies away."""
    if t < at: return 0.0
    u = t - at
    return size*math.exp(-u/decay)*math.cos(math.tau*hz*u/20)


def shake(t, seed, size):
    return np.array([wobble(t, seed, 9), wobble(t, seed + 1, 11), wobble(t, seed + 2, 7)])*size


# ------------------------------------------------------------------ rotations

def Rx(a): c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def Ry(a): c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def Rz(a): c, s = math.cos(a), math.sin(a); return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def heading(yaw, pitch=0.0, roll=0.0):
    """A rotation that turns a model facing +Z to Minecraft's yaw (degrees, 0 = +Z, 90 = -X),
    pitch (positive nose down) and roll (positive banks right: the left side up)."""
    return Ry(-math.radians(yaw))@Rx(math.radians(pitch))@Rz(math.radians(roll))


def quat(r):
    w = math.sqrt(max(0, 1 + r[0, 0] + r[1, 1] + r[2, 2]))/2
    x = math.sqrt(max(0, 1 + r[0, 0] - r[1, 1] - r[2, 2]))/2
    y = math.sqrt(max(0, 1 - r[0, 0] + r[1, 1] - r[2, 2]))/2
    z = math.sqrt(max(0, 1 - r[0, 0] - r[1, 1] + r[2, 2]))/2
    x = math.copysign(x, r[2, 1] - r[1, 2]); y = math.copysign(y, r[0, 2] - r[2, 0]); z = math.copysign(z, r[1, 0] - r[0, 1])
    q = np.array([w, x, y, z]); return q/np.linalg.norm(q)


def unquat(q):
    w, x, y, z = q
    return np.array([[1 - 2*(y*y + z*z), 2*(x*y - z*w), 2*(x*z + y*w)],
                     [2*(x*y + z*w), 1 - 2*(x*x + z*z), 2*(y*z - x*w)],
                     [2*(x*z - y*w), 2*(y*z + x*w), 1 - 2*(x*x + y*y)]])


def slerp(a, b, s):
    if s <= 0: return a
    if s >= 1: return b
    qa, qb = quat(a), quat(b)
    d = float(np.dot(qa, qb))
    if d < 0: qb, d = -qb, -d
    if d > .9995: q = qa + (qb - qa)*s
    else:
        th = math.acos(d)
        q = (math.sin((1 - s)*th)*qa + math.sin(s*th)*qb)/math.sin(th)
    return unquat(q/np.linalg.norm(q))


def look(eye, target):
    """Minecraft's yaw and pitch from eye toward target."""
    d = np.array(target) - np.array(eye)
    return math.degrees(math.atan2(-d[0], d[2])), -math.degrees(math.atan2(d[1], math.hypot(d[0], d[2])))


def forward(yaw, pitch):
    y, p = math.radians(yaw), math.radians(pitch)
    return np.array([-math.sin(y)*math.cos(p), -math.sin(p), math.cos(y)*math.cos(p)])


def wrap(a): return (a + 180) % 360 - 180


# ------------------------------------------------------------------ paths

class Path3:
    """A smooth path through (tick, point) keys: Catmull-Rom by time, with optional end speeds."""

    def __init__(self, keys, start_velocity=None, end_velocity=None):
        self.t = np.array([k[0] for k in keys], dtype=float)
        self.p = np.array([k[1] for k in keys], dtype=float)
        n = len(keys)
        self.m = np.zeros_like(self.p)
        for i in range(n):
            if 0 < i < n - 1: self.m[i] = (self.p[i + 1] - self.p[i - 1])/(self.t[i + 1] - self.t[i - 1])
            elif i == 0: self.m[i] = start_velocity if start_velocity is not None else (self.p[1] - self.p[0])/(self.t[1] - self.t[0])
            else: self.m[i] = end_velocity if end_velocity is not None else (self.p[-1] - self.p[-2])/(self.t[-1] - self.t[-2])

    def __call__(self, t):
        if t <= self.t[0]: return self.p[0] + self.m[0]*(t - self.t[0])
        if t >= self.t[-1]: return self.p[-1] + self.m[-1]*(t - self.t[-1])
        i = int(np.searchsorted(self.t, t)) - 1
        h = self.t[i + 1] - self.t[i]
        s = (t - self.t[i])/h
        h00, h10, h01, h11 = 2*s**3 - 3*s**2 + 1, s**3 - 2*s**2 + s, -2*s**3 + 3*s**2, s**3 - s**2
        return h00*self.p[i] + h10*h*self.m[i] + h01*self.p[i + 1] + h11*h*self.m[i + 1]

    def velocity(self, t, dt=.25):
        return (self(t + dt) - self(t - dt))/(2*dt)  # blocks a tick


def flight_frame(path, t, bank_gain=1.0, climb_gain=.7):
    """A bird's rotation along a path: facing its way, nose up as it climbs, banked into turns.
    Measured over a few ticks either side, so a near-vertical climb doesn't swing its heading."""
    v = path.velocity(t, 3)
    yaw = math.degrees(math.atan2(-v[0], v[2]))
    climb = math.degrees(math.atan2(v[1], math.hypot(v[0], v[2])))
    bank = 0.0
    for dt in (-3, -1.5, 0, 1.5, 3):
        a, b = path.velocity(t + dt - 2, 2), path.velocity(t + dt + 2, 2)
        turn = wrap(math.degrees(math.atan2(-b[0], b[2])) - math.degrees(math.atan2(-a[0], a[2])))/4  # degrees a tick
        bank += 7.5*turn*min(math.hypot(a[0], a[2]), math.hypot(b[0], b[2]))/5
    return yaw, -climb*climb_gain, max(-45, min(45, bank*bank_gain))


# ------------------------------------------------------------------ the Roc

# The talon it holds you with: four toes of short jointed segments on its left foot, which curl
# smoothly round a player where its own long rigid toes can't. Only the intro's Roc has them;
# its own left toes hide while they show. Segments are (length, width, height) in model pixels,
# root to claw; each toe wears the texture of the Roc's own claws.
GRIP_SEGMENTS = [(8.0, 6.0, 6.5), (8.0, 5.5, 6.0), (7.5, 4.5, 5.0), (8.0, 2.5, 3.5)]
GRIP_ROOTS = [(9.45, -16.1, -1), (13.5, -16.1, -1), (17.55, -16.1, -1), (13.5, -9.35, 1)]  # x, z, which way it points


def intro_geometry(geo):
    """The Roc's geometry with the gripping talon added to its left foot."""
    geo = copy.deepcopy(geo)
    g = geo['minecraft:geometry'][0]
    g['description']['identifier'] = 'geometry.storm_roc_intro'
    claw = {b['name']: b for b in g['bones']}['left_toe_1_tip']['cubes']
    skins = [claw[1]['uv'], claw[1]['uv'], claw[2]['uv'], claw[3]['uv']]
    for i, (x, z, way) in enumerate(GRIP_ROOTS):
        parent, y = 'left_foot', 5.5
        for k, (length, width, height) in enumerate(GRIP_SEGMENTS):
            near = z if way > 0 else z - length
            g['bones'].append({'name': f'grip_toe_{i}_{k}', 'parent': parent, 'pivot': [x, y, z],
                               'cubes': [{'origin': [x - width/2, y - height/2, near], 'size': [width, height, length],
                                          'uv': copy.deepcopy(skins[k])}]})
            parent, z = f'grip_toe_{i}_{k}', z + way*length
    return geo


def load_roc():
    geo = intro_geometry(json.loads((ASSETS/'geckolib/models/storm_roc.geo.json').read_text()))
    raw = json.loads((ASSETS/'geckolib/animations/storm_roc.animation.json').read_text())['animations']
    clips = {name.split('.')[-1]: Clip(raw[name]) for name in raw}
    return geo, Rig(geo), clips


ROC_GEO, ROC, CLIPS = load_roc()
GRIP_TOES = [[f'grip_toe_{i}_{k}' for k in range(len(GRIP_SEGMENTS))] for i in range(len(GRIP_ROOTS))]
LEFT_LEG, RIGHT_LEG = set(ROC.subtree('left_thigh')), set(ROC.subtree('right_thigh'))
LEGS = LEFT_LEG | RIGHT_LEG
CAGE = [13.2, -4.0, -12.5]  # in its left foot's model pixels: your hips, inside the loop of its curled talons
NECK = ['neck_base', 'neck_lower', 'neck_mid', 'neck_upper', 'neck_tip']
CREST = set(ROC.subtree('crest'))
TOES = [(side, i) for side in ('left', 'right') for i in range(4)]
BEAT_FROM, BEAT = 1.0, 2.0  # one wingbeat of the flight loop, top of the upstroke to the next
OPEN_LEGS, CARRY_LEGS = 2.3, 2.55  # the pounce's legs: reaching with talons open, and down and forward


def copy_channels(p):
    return {b: {k: np.array(v, dtype=float) for k, v in ch.items()} for b, ch in p.items()}


def blend(p, q, w, bones=None):
    """p moved toward q by w, bone by bone (channels missing from one side are kept)."""
    if w <= 0: return p
    for b in (bones if bones is not None else q.keys()):
        if b not in q: continue
        for k, v in q[b].items():
            base = p.setdefault(b, {}).get(k)
            if base is None: base = np.ones(3) if k == 'scale' else np.zeros(3)
            p[b][k] = base*(1 - w) + v*w
    return p


def add_rot(p, bone, r):
    ch = p.setdefault(bone, {})
    ch['rotation'] = ch.get('rotation', np.zeros(3)) + np.array(r, dtype=float)


def rate(t):
    """Wingbeats a second as the flight loop's beat (1 is the loop's own heavy two-second beat)."""
    return float(curve(t, [(0, 'io', 1.0), (SNATCH - 6, 'io', 1.0), (GRIP, 'out', 1.8), (GRIP + 14, 'io', 1.45),
                           (CLOUD, 'io', 1.3), (SUMMIT, 'io', 1.3), (THROW - 8, 'io', 1.35), (THROW, 'io', 1.7),
                           (THROW + 12, 'io', 1.15), (LAND, 'io', 1.0)])[0])


def _phases():
    """The beat's phase (seconds into the loop's beat) at every tick, integrating the rate."""
    out, phase = [], 0.15
    for t in range(LENGTH + 2):
        out.append(phase)
        phase += rate(t + .5)/20
    return out


PHASES = _phases()
# Where each downstroke starts: the wingbeat's top, every BEAT of phase.
DOWNSTROKES = [t for t in range(1, LENGTH + 1) if (PHASES[t - 1]/BEAT) % 1 < .45 <= (PHASES[t]/BEAT) % 1]


def phase(t):
    i = min(int(t), len(PHASES) - 2)
    return PHASES[i] + (PHASES[i + 1] - PHASES[i])*(t - i)


def wings(t):
    """The flight loop's beat, looped over one wingbeat with a short crossfade at its seam."""
    u = phase(t) % BEAT
    p = CLIPS['flight'].sample(BEAT_FROM + u)
    seam = ramp(u, BEAT - .25, BEAT)
    if seam > 0: blend(p, CLIPS['flight'].sample(BEAT_FROM + u - BEAT), seam)
    return p


GRIP_CURL = [35, 55, 60, 45]  # degrees each joint of the talon bends, root to claw, when clenched


def toes_closed(p, closure, turn):
    """Curls the talons shut by closure (0 open as it reaches, 1 clenched round you). The left foot
    holds you with its jointed talon, turned sideways by turn, so as you lie flat under it its three
    front toes, fanned out from your chest to your thighs, curl round under your right side and its
    hind toe round your left. The right foot's own toes just close as it folds away."""
    add_rot(p, 'left_foot', [15*turn, 15*turn, 90*turn])
    for i, toe in enumerate(GRIP_TOES):
        hind = -1 if i == 3 else 1
        for k, bone in enumerate(toe):
            bend = GRIP_CURL[k]*closure - (15*(1 - closure) if k == 0 else 0)
            spread = (i - 1)*24*(.4 + .6*closure) if k == 0 and i < 3 else 0
            add_rot(p, bone, [hind*bend, spread, 0])
    for i in range(4):
        hind = -1 if i == 3 else 1
        add_rot(p, f'right_toe_{i}_base', [hind*10*closure, 0, 0])
        add_rot(p, f'right_toe_{i}_tip', [hind*30*closure, 0, 0])


def talon(p, t):
    """Shows the gripping talon on the left foot, its own toes hidden, until it has let you go;
    then swaps them back before it lands."""
    shown = t < THROW + 10
    for i in range(4):
        p.setdefault(f'left_toe_{i}_base', {})['scale'] = np.zeros(3) if shown else np.ones(3)
        p.setdefault(GRIP_TOES[i][0], {})['scale'] = np.ones(3) if shown else np.zeros(3)


REACH_LEGS, CARRYING_LEGS = CLIPS['pounce'].sample(OPEN_LEGS), CLIPS['pounce'].sample(CARRY_LEGS)


def legs_pose(t):
    """(left weight, right weight, channels) for the legs: tucked in flight (0) to reaching or
    carrying (1). Both reach for you; the left closes round your chest and carries you, and the
    right folds back up out of the way."""
    reach, carry = REACH_LEGS, CARRYING_LEGS
    if t < SUMMIT:
        w = ramp(t, SNATCH - 8, SNATCH)
        q = blend(copy_channels(reach), carry, ramp(t, SNATCH - 2, GRIP), LEGS)
        closure = ramp(t, GRIP - 2.5, GRIP + .5)
        turn = ramp(t, SNATCH - 1, GRIP - .5)
        # The jolt of the grab: legs yanked back and up as the weight comes on, then settling.
        jolt = kick(t, GRIP, 1.0, 6, 1.2)
        q = copy_channels(q)
        for side in ('left', 'right'):
            add_rot(q, f'{side}_thigh', [-14*jolt, 0, 0])
            add_rot(q, f'{side}_shin', [10*jolt, 0, 0])
    else:
        q = copy_channels(carry)
        # The fling: legs swing forward and down, talons open at the throw, then fold away.
        fling = pulse(t, THROW - 6, THROW, THROW + 2, THROW + 12)
        for side in ('left', 'right'):
            add_rot(q, f'{side}_thigh', [-28*fling, 0, 0])
            add_rot(q, f'{side}_shin', [-18*fling, 0, 0])
        closure = 1 - ramp(t, THROW - 1, THROW + 2)
        turn = 1 - ramp(t, THROW, THROW + 6)
        w = 1 - ramp(t, THROW + 4, THROW + 22)
    right = w*(1 - ramp(t, GRIP + 3, GRIP + 16)) if t < SUMMIT else 0.0
    # The player kicking in them, and the clench while struggling.
    struggle = pulse(t, SUMMIT, SUMMIT + 10, THROW - 4, THROW)
    if struggle:
        for k, side in enumerate(('left', 'right')):
            add_rot(q, f'{side}_thigh', [5*struggle*wobble(t, 40 + k, 3), 3*struggle*wobble(t, 50 + k, 2), 0])
            add_rot(q, f'{side}_tarsus', [6*struggle*wobble(t, 60 + k, 4), 0, 0])
    # Pull the feet in toward each other so they close around one body, not two.
    for side, sign in (('left', 1), ('right', -1)):
        add_rot(q, f'{side}_thigh', [0, 0, sign*7*ramp(t, SNATCH - 4, GRIP + 4)])
    toes_closed(q, closure + .15*struggle*max(0, wobble(t, 70, 5)), turn)
    return w, right, q


def peer(t):
    """How far it bends its head down to glare at you in its talons (1): its neck arched a little,
    its face about five blocks out in front of you and above, its bill pointing down at you. It
    does so as you look up at it, and again when your wand shocks it; otherwise it looks ahead."""
    rows = [(0, 'io', 0), (LOOK_UP - 10, 'io', 0), (LOOK_UP + 2, 'out', 1.0), (AWAY, 'io', .95), (WHITE, 'io', .2),
            (SUMMIT, 'io', 0), (STRIKE, 'io', 0), (SHRIEK, 'out', 1.05), (THROW + 2, 'io', 1.0), (THROW + 14, 'io', 0)]
    return float(curve(t, rows)[0])


def thrust(t):
    """Its head stabbing at you as it screams and shrieks."""
    rows = [(0, 'io', 0), (SCREAM - 4, 'io', 0), (SCREAM, 'out', .6), (SCREAM + 10, 'io', 0),
            (SHRIEK - 1, 'io', 0), (SHRIEK + 2, 'out', .7), (SHRIEK + 10, 'io', 0)]
    return float(curve(t, rows)[0])


def shriek(t):
    return 0.0


def jaw(t):
    rows = [(0, 'io', 0), (SCREAM - 3, 'io', 0), (SCREAM, 'out', 1), (AWAY - 2, 'io', .85), (AWAY + 2, 'io', 0),
            (SHRIEK - 1, 'io', 0), (SHRIEK + 2, 'out', 1), (THROW - 2, 'io', .8), (THROW + 6, 'io', 0)]
    return float(curve(t, rows)[0])


def crest_up(t):
    return max(pulse(t, SCREAM - 6, SCREAM, AWAY, CLOUD + 4), pulse(t, STRIKE, SHRIEK, THROW + 4, THROW + 16))


# Head glances as it flies on over the summit's sky, looking ahead: (tick, yaw, roll), snapped.
GLANCES = [(SUMMIT + 6, 14, 8), (SUMMIT + 20, -10, -10), (SUMMIT + 30, 0, 0), (SUMMIT + 38, 18, 12), (FREE - 2, -6, -4),
           (FREE + 8, 0, 0)]


def glance(t):
    yaw = roll = 0.0
    before = (0.0, 0.0)
    for at, y, r in GLANCES:
        if t < at: break
        s = EASE['out'](min(1, (t - at)/2.5))
        yaw, roll = before[0] + (y - before[0])*s, before[1] + (r - before[1])*s
        shiver = math.exp(-(t - at)/3)*math.sin((t - at)*2.6)
        yaw += 3*shiver; roll += 2.5*shiver
        before = (y, r)
    return yaw*pulse(t, SUMMIT, SUMMIT + 2, STRIKE, SHRIEK), roll*pulse(t, SUMMIT, SUMMIT + 2, STRIKE, SHRIEK)


SCREAM_CREST = CLIPS['scream'].sample(1.6)


def roc_channels(t):
    """The Roc's bones at tick t."""
    p = wings(t)
    left, right, legs = legs_pose(t)
    blend(p, legs, left, LEFT_LEG)
    blend(p, legs, right, RIGHT_LEG)
    k, th, up = peer(t), thrust(t), shriek(t)
    for n in NECK:
        add_rot(p, n, [2*k + 7*th - 12*up, -4*k, 0])
    yaw, roll = glance(t)
    add_rot(p, 'head', [90*k + 8*th - 60*up + 4*wobble(t, 9, 3)*k, yaw, roll])
    open_ = jaw(t)
    add_rot(p, 'lower_beak', [48*open_ + 3*open_*math.sin(t*2.9), 0, 0])
    blend(p, SCREAM_CREST, crest_up(t), CREST)
    if t >= LAND:
        p = blend(p, CLIPS['land'].sample((t - LAND)/20), ramp(t, LAND, LAND + 5))
    if t > SETTLED:
        # Holding on its perch under its name: its idle eased out and back, so the scene ends on the
        # perch's first frame, where the fight's own Roc begins.
        p = blend(p, CLIPS['perched'].sample(.6*math.sin(math.pi*min(1, (t - SETTLED)/(LENGTH - SETTLED)))), 1)
    talon(p, t)
    return p


def roc_local(t):
    """The Roc's own landmarks at tick t, in blocks in its frame (facing +Z, 1.25 scale): body,
    head, eyes, the grip between its feet, the wrists."""
    m = ROC.pose(roc_channels(t), S(ROC_SCALE, ROC_SCALE, ROC_SCALE)@ENTITY)
    at = lambda n: (m[n]@np.append(ROC.pivot[n], 1))[:3]
    return {'body': at('body'), 'head': at('head'), 'beak': at('lower_beak'),
            'eyes': (ROC.point(m, 'head', [11, 176.5, -37.7]) + ROC.point(m, 'head', [-11, 176.5, -37.7]))/2,
            'grip': ROC.point(m, 'left_foot', CAGE), 'wrists': (at('left_wing_wrist') + at('right_wing_wrist'))/2,
            'feet': (at('left_foot'), at('right_foot'))}


# ------------------------------------------------------------------ the player

PUPPET_GEO, PUPPET_SLIM = puppet.geometry(False), puppet.geometry(True)
PUP = Rig(PUPPET_GEO)
CHEST = np.array([0, 1.125, 0])
HIPS = np.array([0, .85, 0])  # where its talons close round you, in your model's blocks
PUPPET_CORNERS = [(b['name'], [c['origin'][0] + i*c['size'][0], c['origin'][1] + j*c['size'][1], c['origin'][2] + k*c['size'][2]])
                  for b in PUPPET_GEO['minecraft:geometry'][0]['bones'] for c in b.get('cubes', [])[:1]
                  for i in (0, 1) for j in (0, 1) for k in (0, 1)]


def puppet_channels(t):
    """The player's limbs at tick t (degrees; arms -X raise forward, +Z on the right arm lifts it out)."""
    c = {}
    def rot(b, r): c.setdefault(b, {})['rotation'] = np.array(r, dtype=float)
    breath = math.sin(t*.25)
    if t < GRIP:
        flinch = pulse(t, CAW, CAW + 2, CAW + 8, GRIP)
        rot('right_arm', [-4*breath - 25*flinch, 0, 3 + 14*flinch])
        rot('left_arm', [4*breath - 25*flinch, 0, -3 - 14*flinch])
        rot('right_leg', [0, 0, 0]); rot('left_leg', [0, 0, 0])
        rot('waist', [8*flinch, 0, 0])
        rot('head', [0, 0, 0])
        return c
    if t < THROW:
        # Carried flat, face down: head lifted to look ahead, legs kicking behind, arms reaching
        # ahead, clawing at the air and its toes. Then the wand arm wrenched free, swung up over your back and
        # brought down on the toe at your side. (Lying face down, +X swings a limb up behind you.)
        jolt = kick(t, GRIP, 1.0, 5, 1.5)
        fight = .55 + .45*ramp(t, SUMMIT - 4, SUMMIT + 6) + .3*pulse(t, FREE - 10, FREE - 4, FREE, FREE + 4)
        claw = 25*math.sin(t*.55)*fight
        legs = 28*fight*math.sin(t*.85)
        rot('left_arm', [-135 + claw + 40*jolt, 0, -14 + 6*math.sin(t*.4)])
        free = ramp(t, FREE, FREE + 4)
        raise_ = ramp(t, FREE + 4, STRIKE - 3)
        strike = EASE['in'](min(1, max(0, (t - (STRIKE - 3))/3)))
        right = mix([-135 - claw + 40*jolt, 0, 14 - 6*math.sin(t*.4 + 1)], [40, 0, 85], free)
        right = mix(right, [115, 0, 45], raise_)
        right = mix(right, [15, 0, 82], strike)
        right = mix(right, [-40, 0, 60], ramp(t, STRIKE + 3, THROW))
        rot('right_arm', right)
        rot('right_leg', [legs + 18*jolt, 0, 6])
        rot('left_leg', [-legs + 18*jolt, 0, -6])
        rot('waist', [-10 + 5*math.sin(t*.4) - 12*raise_*(1 - strike), 12*fight*wobble(t, 81, 1.5), 0])
        rot('head', [-48 + 10*math.sin(t*.3)*fight, 20*fight*wobble(t, 82, 1.2), 0])
        return c
    if t < CRASH:
        # Flung: limbs flailing, then braced as the snow comes up.
        fl = 1 - ramp(t, CRASH - 8, CRASH - 2)
        rot('right_arm', [-120 + 50*math.sin(t*.6), 0, 60*fl + 20])
        rot('left_arm', [-120 + 50*math.sin(t*.6 + 2), 0, -60*fl - 20])
        rot('right_leg', [-30 + 40*math.sin(t*.5)*fl, 0, 14*fl])
        rot('left_leg', [-30 - 40*math.sin(t*.5)*fl, 0, -14*fl])
        rot('waist', [-20*fl + 30*(1 - fl), 0, 0])
        rot('head', [30, 0, 0])
        return c
    # The crash, the backward roll (tucked), the crouch and standing up, wand ready.
    tuck = pulse(t, CRASH, CRASH + 3, ROLLED - 3, ROLLED + 2)
    crouch = pulse(t, ROLLED - 2, ROLLED + 1, UP - 6, UP)
    rot('waist', [60*tuck + 28*crouch, 0, 0])
    rot('right_leg', [-95*tuck - 80*crouch, 0, 4])
    rot('left_leg', [-95*tuck + 15*crouch, 0, -4])
    rot('right_arm', [-60*tuck - 55*crouch - 22*ramp(t, UP - 4, UP), 0, 10*tuck + 12])
    rot('left_arm', [-60*tuck - 20*crouch, 0, -10*tuck - 22*crouch - 6])
    rot('head', [-28*tuck - 25*crouch - 14*ramp(t, UP - 4, UP), 0, 0])
    return c


# ------------------------------------------------------------------ the aerie half

# The Roc's body centre over the aerie: diving in from behind and to the right, unseen, low over
# your shoulders as it grabs, then climbing away over the gong in a spiral up into the cloud.
SPIRAL = dict(centre=np.array([12.0, 0, 2.5]), start=GRIP + 14, end=WHITE + 10, turns=1.15, y0=15.5, y1=48.0, r0=12, r1=18)


def spiral_point(t):
    s = (t - SPIRAL['start'])/(SPIRAL['end'] - SPIRAL['start'])
    a = math.pi - math.tau*SPIRAL['turns']*s  # turning left, so its right side (where its head comes round) rides high
    r = SPIRAL['r0'] + (SPIRAL['r1'] - SPIRAL['r0'])*s
    y = SPIRAL['y0'] + (SPIRAL['y1'] - SPIRAL['y0'])*(1 - (1 - s)**2)  # fastest early, so the peak drops away
    return SPIRAL['centre'] + np.array([r*math.cos(a), y, r*math.sin(a)])


def aerie_body_path(grab):
    """Through grab, its body's place as its talons close on you, level and heading on past the
    gong; the dive leading into it from behind you, the climb out of it."""
    grab = np.array(grab, dtype=float)
    keys = [(-20, grab + [-30, 46, -104]), (0, grab + [-24, 36, -84]), (20, grab + [-14, 22, -54]),
            (CAW, grab + [-5.5, 8.5, -21]), (SNATCH, grab + [-1.2, 2.0, -8.0]), (GRIP, grab),
            (GRIP + 6, grab + [0, 5.6, .2]), (SPIRAL['start'], spiral_point(SPIRAL['start']))]
    keys += [(t, spiral_point(t)) for t in range(SPIRAL['start'] + 12, SPIRAL['end'] + 1, 12)]
    return Path3(keys)


# ------------------------------------------------------------------ the summit half

def summit_body_path():
    """Up out of a cloud bank north-west of the home spire, round in a climbing turn to come at
    the spire from the north, the throw, then gliding in to land on it facing the summit."""
    perched = HOME_PERCH + heading(HOME_YAW)@roc_local(LENGTH)['body']
    keys = [(SUMMIT - 10, [-74, 8, -160]), (SUMMIT, [-62, 18, -168]), (SUMMIT + 16, [-44, 31, -178]),
            (SUMMIT + 34, [-20, 41, -176]), (SUMMIT + 52, [2, 47, -162]), (THROW - 10, [10, 49, -142]),
            (THROW, [10, 49, -128]), (THROW + 14, [7, 47, -111]), (LAND, [4, 45, -99]),
            (TOUCHDOWN, perched + [0, .6, -1.2]), (SETTLED, perched)]  # and still on it after
    return Path3(keys, end_velocity=np.zeros(3))


class Scene:
    """Bakes the Roc, the player and the camera together, tick by tick."""

    def __init__(self):
        self.local = {}
        # Aim the dive so its grip closes exactly on your chest.
        local = self.roc_l(GRIP)
        grab = HIPS - heading(0, -10)@(local['grip'] - local['body'])
        for _ in range(4):
            self.aerie = aerie_body_path(grab)
            grab = grab + .8*(HIPS - self.roc_world(GRIP)[1]['grip'])
        self.aerie = aerie_body_path(grab)
        self.summit = summit_body_path()
        self.throw = self.ballistic()

    def roc_l(self, t):
        key = round(t, 3)
        if key not in self.local: self.local[key] = roc_local(t)
        return self.local[key]

    def half(self, t): return 0 if t < SUMMIT else 1

    def roc_rotation(self, t):
        if t < SUMMIT:
            # Hauling you straight up off the peak it climbs steeply, but keeps its body near level.
            # A shallow bank, so its low wing stays above you rather than hanging beside you.
            yaw, pitch, roll = flight_frame(self.aerie, t, .4, .7 - .45*pulse(t, GRIP - 6, GRIP, GRIP + 10, GRIP + 24))
            # Flaring as it grabs you, nose up and wings back; then labouring up.
            pitch += -12*pulse(t, SNATCH - 4, GRIP - 1, GRIP + 2, GRIP + 12)
            # Beats lurch it: the body rises and pitches with each downstroke.
            return heading(yaw, pitch + 3*math.sin(math.tau*phase(t)/BEAT), roll)
        yaw, pitch, roll = flight_frame(self.summit, t, .9)
        pitch += -18*pulse(t, THROW - 6, THROW, THROW + 2, THROW + 14) - 10*pulse(t, LAND - 6, LAND + 4, TOUCHDOWN, TOUCHDOWN + 6)
        r = heading(yaw, pitch + 2.5*math.sin(math.tau*phase(t)/BEAT)*(1 - ramp(t, LAND, TOUCHDOWN)), roll)
        return slerp(r, heading(HOME_YAW), ramp(t, LAND + 4, TOUCHDOWN + 2))

    def roc_world(self, t):
        """(root, rotation, landmarks in the frame) for the Roc at tick t."""
        r = self.roc_rotation(t)
        local = self.roc_l(t)
        path = self.aerie if t < SUMMIT else self.summit
        root = path(t) - r@local['body']
        if t >= SUMMIT:
            root = mix(root, HOME_PERCH, ramp(t, TOUCHDOWN - 4, TOUCHDOWN + 2))
        world = {k: (root + r@v if not isinstance(v, tuple) else tuple(root + r@x for x in v)) for k, v in local.items()}
        return (root, r), world

    # --- the player

    def ballistic(self):
        """The throw: from the talons at THROW to the snow at CRASH, chest first, on a lob."""
        (_, _), w = self.roc_world(THROW)
        p0 = w['grip']
        land = np.array([0.5, .3, 26.0])
        tau = (CRASH - THROW)/20
        v0 = (land - p0 - .5*np.array([0, -GRAVITY, 0])*tau*tau)/tau
        return p0, v0

    def thrown(self, t):
        """Your hips on the throw: a lob from the talons to the snow."""
        p0, v0 = self.throw
        tau = (t - THROW)/20
        return p0 + v0*tau + .5*np.array([0, -GRAVITY, 0])*tau*tau

    def held(self, t):
        """Your rotation in its talons: lying flat under it, head first and face down like a fish
        in an eagle's foot, rocking with its beats and twisting as you fight it."""
        (_, r), _ = self.roc_world(t)
        # Clamped tight, your body barely moves in its grip: the fight is in your limbs.
        swing = 2*math.sin(t*.31) + 6*kick(t, GRIP, 1, 8, .9)
        return r@heading(2.5*wobble(t, 91, 1), 88 + swing, 3*wobble(t, 92, 1.4))

    def player(self, t):
        """(root, rotation) for the player at tick t."""
        if t < GRIP:
            return np.zeros(3), np.eye(3)  # standing at the gong
        if t < THROW:
            # Its talons close round your hips and you are swept off your feet, flat under it.
            (_, _), w = self.roc_world(t)
            r = slerp(np.eye(3), self.held(t), EASE['out'](min(1, (t - GRIP)/8)))
            return w['grip'] - r@HIPS, r
        if t < CRASH:
            chest = self.thrown(t)
            s = (t - THROW)/(CRASH - THROW)
            start = self.held(THROW)
            # A half twist round to face back up the summit, and a somersault and a quarter
            # backward, to slam into the snow on your back, head first down the summit.
            spin = heading(180*smooth(min(1, s*1.3)), 0, 0)@Rx(math.radians(-450*EASE['out'](s)))
            r = slerp(start, spin, ramp(t, THROW, THROW + 4)) if t < THROW + 4 else spin
            root = chest - r@HIPS
            return root + [0, max(0.0, ARRIVAL[1] - self.lowest(root, r, t)), 0], r
        # The backward roll your momentum carries you through, up into a crouch, then standing.
        roll_s = EASE['out'](min(1, (t - CRASH)/(ROLLED - CRASH)))
        r = heading(180)@Rx(math.radians(-90 - 270*roll_s))
        bounce = .4*math.sin(math.pi*min(1, (t - CRASH)/7))
        z = 26.0 + (ARRIVAL[2] - 26.0)*EASE['out'](min(1, (t - CRASH)/(ROLLED - CRASH + 2)))
        waist = np.array([ARRIVAL[0], ARRIVAL[1] + .75, z])
        root = waist - r@np.array([0, .75, 0])
        return root + [0, ARRIVAL[1] - self.lowest(root, r, t) + bounce, 0], r

    def lowest(self, root, r, t):
        """The lowest corner of the player's body posed at root, r."""
        base = np.eye(4); base[:3, :3] = r; base[:3, 3] = root
        m = PUP.pose(puppet_channels(t), base@ENTITY)
        return min(PUP.point(m, bone, corner)[1] for bone, corner in PUPPET_CORNERS)

    def puppet_pose(self, t):
        root, r = self.player(t)
        base = np.eye(4); base[:3, :3] = r; base[:3, 3] = root
        return PUP.pose(puppet_channels(t), base@ENTITY)

    def eye(self, t):
        m = self.puppet_pose(t)
        return PUP.point(m, 'head', [0, 25.9, 0])

    def hand(self, t):
        m = self.puppet_pose(t)
        return (m['wand']@np.append(PUP.pivot['wand'], 1))[:3]


# ------------------------------------------------------------------ the camera

def mouse(t, moves, start):
    """A view flicked to each target fast, overshooting a little and settling. moves are
    (tick, ticks to get there, (yaw, pitch))."""
    held = np.array(start, dtype=float)
    for t0, ticks, target in moves:
        if t < t0: return held
        target = np.array(target, dtype=float)
        if t < t0 + ticks: return held + (target - held)*EASE['back']((t - t0)/ticks)
        held = target
    return held


def first_person(scene, t):
    """Your own eyes at the aerie: looking about at the gong, the caw, the snatch, looking down
    between the talons as the peak falls away, then up at it."""
    eye = scene.eye(t)
    if t < GRIP:
        yaw, pitch = mouse(t, [(8, 9, (-24, -34)), (19, 10, (-70, 25)), (30, 9, (4, -14)),
                               (CAW + 1, 4, (40, -26)), (SNATCH - 2, 6, (12, 26))], (0, -18))
        drift = shake(t, 1, .35)
        fov = 70 + 8*pulse(t, CAW, CAW + 1, CAW + 3, CAW + 10)
        return eye, yaw + drift[0], pitch + drift[1], 0.0, fov
    (_, r), w = scene.roc_world(t)
    roc_yaw = math.degrees(math.atan2(-r[0, 2], r[2, 2]))
    roc_roll = math.degrees(math.atan2(r[1, 0], r[1, 1]))
    # Down between the talons, a little behind, swinging with its turns.
    # Forward and down out of the open front of its grip, its claws curled under you, the peak
    # wheeling below as it spirals up.
    peak = look(eye, [0, 0, 4])
    behind = wrap(peak[0] - roc_yaw)
    down_yaw = roc_yaw + behind*.45*max(0.0, 1 - abs(behind)/140) + 8*math.sin(t*.07)  # fades out as the peak swings behind you
    down_pitch = .5*48 + .5*peak[1] + 6*math.sin(t*.11)
    head = w['eyes']
    up_yaw, up_pitch = look(eye, head)
    moves = [(GRIP, 7, (roc_yaw + 30, 44)), (GRIP + 7, 7, (roc_yaw + 20, 58)), (GRIP + 14, 14, (down_yaw, down_pitch))]
    if t < LOOK_UP:
        yaw, pitch = mouse(t, moves, (12, 26))
        if t >= GRIP + 28: yaw, pitch = down_yaw, down_pitch
    else:
        # Looking up at it: snapped up in two jerks, overshooting, then held on it.
        aim = np.array(look(eye, head))
        down = np.array([aim[0] + wrap(down_yaw - aim[0]), down_pitch])
        mid = mix(down, aim, .55) + [-14, 6]
        flick = mouse(t, [(LOOK_UP, 5, mid), (LOOK_UP + 5, 4, aim + [4, -3])], down)
        yaw, pitch = mix(flick, aim, ramp(t, LOOK_UP + 8, LOOK_UP + 11))
        # Then away from it, ahead and down into the cloud as it closes round you.
        away = EASE['out'](min(1, max(0, (t - AWAY)/6)))
        if away > 0:
            ahead = np.array([aim[0] + wrap(roc_yaw - aim[0]), 8.0])
            yaw, pitch = mix([yaw, pitch], ahead, away)
    # The ride: every downstroke lurches the view; the bank tips it; shocks on the grab and scream.
    lurch = 1.2*math.sin(math.tau*phase(t)/BEAT + 1.2) + sum(kick(t, d, 5, 4, 1.4) for d in DOWNSTROKES if GRIP < d < CLOUD)
    jolt = kick(t, GRIP, 9, 5, 1.7) + kick(t, SCREAM, 4, 6, 2.6)
    s = shake(t, 3, 1.1 + 2.5*pulse(t, GRIP, GRIP + 1, GRIP + 6, GRIP + 22) + 2.2*pulse(t, SCREAM, SCREAM + 2, SCREAM + 16, SCREAM + 30)
              + 1.4*ramp(t, CLOUD, WHITE))
    roll = .55*roc_roll + s[2]*1.5 + 6*kick(t, GRIP, 1, 7, 1.1)
    # Wide and reeling while it carries you off; tight on its face once you look up at it.
    tight = ramp(t, LOOK_UP + 3, LOOK_UP + 8)*(1 - ramp(t, AWAY, AWAY + 6))
    fov = (72 + 16*math.exp(-max(0, t - GRIP)/9))*(1 - tight) + 62*tight + 12*pulse(t, SCREAM, SCREAM + 2, SCREAM + 8, SCREAM + 24) \
        + 14*ramp(t, CLOUD, WHITE)
    return eye, yaw + s[0], pitch + lurch + jolt + s[1], roll, fov


def third_person(scene, t):
    """Close beside the talon in the summit's sky, the throw from behind, the landing from low
    beside you, then gliding into your eyes."""
    if t < THROW:
        root, r = scene.player(t)
        chest = root + r@CHEST
        (_, rr), w = scene.roc_world(t)
        # Low, out to its right and ahead: you flat in its talon under it, its head out ahead of you
        # looking where it flies; drifting round to the front, and in closer for the wand strike.
        # Offsets are in its frame (+X its left, +Z ahead).
        offset = curve(t, [(SUMMIT, 'io', [-9.0, -2.0, 6.0]), (SUMMIT + 22, 'io', [-8.0, -2.4, 9.0]), (FREE - 6, 'io', [-4.0, -2.6, 11.0]),
                           (STRIKE - 2, 'io', [-3.8, -1.2, 7.0]), (THROW, 'io', [-5.0, -1.8, 8.5])])[0]
        offset = offset + [.25*math.sin(t*.21), .2*math.sin(t*.17 + 1), .25*math.sin(t*.13 + 2)]
        eye = chest + rr@offset
        target = mix(chest + rr@np.array([0, .3, 0]), w['eyes'], .38)
        target = mix(target, scene.hand(t), .45*pulse(t, FREE + 2, STRIKE - 3, STRIKE - 1, STRIKE + 2))
        target = mix(target, w['eyes'], .3*ramp(t, STRIKE + 1, SHRIEK + 2))  # toward it as it whips round and shrieks
        yaw, pitch = look(eye, target)
        hit = 1.6*kick(t, STRIKE, 1, 4, 2.4) + .8*kick(t, SHRIEK, 1, 4, 2.0)
        sh = shake(t, 7, 1.4 + 1.6*abs(hit))
        fov = 62 - 6*pulse(t, FREE, STRIKE - 3, STRIKE, STRIKE + 6) + 10*math.exp(-max(0, t - STRIKE)/5)*(t >= STRIKE)
        return eye, yaw + sh[0], pitch + sh[1] + 3*hit, sh[2]*2.2 + 8*math.sin(t*.05), float(fov)
    if t < GROUND:
        # Behind you as you tumble away from it down the length of the summit; for the first few
        # ticks beside you, so it letting go is in shot.
        chest = scene.thrown(t)
        p0, v0 = scene.throw
        v = v0 + np.array([0, -GRAVITY, 0])*(t - THROW)/20
        d = v/np.linalg.norm(v)
        side = np.cross(d, [0, 1, 0]); side = side/np.linalg.norm(side)
        behind = ramp(t, THROW + 3, THROW + 12)
        eye = chest + mix(side*6.5 + [0, -4.5, 0] - d*1.0, -d*4.0 + [0, 1.8, 0], behind)  # clear of its legs as it lets go
        (_, _), w = scene.roc_world(t)
        target = mix(chest + d*2, w['body'], .5*(1 - ramp(t, THROW + 1, THROW + 7)))
        yaw, pitch = look(eye, target)
        sh = shake(t, 11, 1.4)
        return eye, yaw + sh[0], pitch + sh[1], sh[2]*2 - 10*math.sin((t - THROW)*.12), 80.0
    if t < LANDING:
        # Low on the summit, looking up its length: you come in out of the sky, grow from a speck,
        # slam into the snow in front of us and roll toward us. Inside the ring round the arrival the
        # summit keeps clear of boulders (art/storm_summit/build_layout.py: 7 blocks).
        eye = np.array([3.8, 1.6, 37.6])
        chest = scene.thrown(t) if t < CRASH else scene.player(t)[0] + [0, .6, 0]
        yaw, pitch = look(eye, chest + [0, .3, 0])
        sh = shake(t, 12, .5 + 2.8*math.exp(-max(0, t - CRASH)/5)*(t >= CRASH))
        jolt = kick(t, CRASH, 5, 4, 2.0)
        fov = 50 + 10*ramp(t, CRASH - 8, CRASH) + 8*math.exp(-max(0, t - CRASH)/5)*(t >= CRASH)
        return eye, yaw + sh[0], pitch + sh[1] + jolt, sh[2]*1.4, float(fov)
    if t < BACK:
        # Its spire, from low beside it: it comes down onto the capstone, wings flared, and screams.
        (_, _), w = scene.roc_world(t)
        eye = np.array([20.0, 25.0, -64.0]) + [-1.2*ramp(t, LANDING, BACK), .6*ramp(t, LANDING, BACK), -1.5*ramp(t, LANDING, BACK)]
        yaw, pitch = look(eye, mix(w['body'], w['eyes'], .4))
        sh = shake(t, 15, .4 + 1.6*math.exp(-max(0, t - TOUCHDOWN)/4)*(t >= TOUCHDOWN))
        return eye, yaw + sh[0], pitch + sh[1], sh[2], 46 - 4*ramp(t, LANDING, BACK)
    # Behind you as you stand up facing it, you low in the frame and it on its spire beyond; then
    # gliding into your eyes, looking at it.
    spire = HOME_PERCH + [0, 8.5, 0]
    eye_final = ARRIVAL + [0, EYE, 0]
    final = look(eye_final, spire)
    behind = np.array([1.9, 3.1, 36.6])
    root, r = scene.player(t)
    waist = root + r@np.array([0, .75, 0])
    target = mix(waist + [0, 1.0, 0], spire, .04 + .06*ramp(t, BACK, RETURN))
    yaw, pitch = look(behind, target)
    g = ramp(t, RETURN, LENGTH)
    eye = mix(behind, eye_final, g)
    yaw = yaw + wrap(final[0] - yaw)*g
    pitch = pitch + (final[1] - pitch)*g
    sh = shake(t, 14, .25)
    return eye, yaw + sh[0], pitch + sh[1], sh[2]*(1 - g), float(60 + 10*g)


def camera(scene, t):
    return first_person(scene, t) if t < SUMMIT else third_person(scene, t)


# ------------------------------------------------------------------ light, weather, sound

# Lightning: (tick, half, kind, x, y, z). 'sheet' lights a cloud from inside, 'bolt' strikes from
# the cap down, 'crackle' sparks round the Roc's head.
LIGHTNING = [(SCREAM - 1, 0, 'crackle'), (SCREAM + 3, 0, 'crackle'), (SCREAM + 8, 0, 'crackle'), (SCREAM + 13, 0, 'crackle'),
             (CLOUD + 1, 0, 'sheet'), (CLOUD + 5, 0, 'sheet'), (WHITE + 1, 0, 'sheet'),
             (SUMMIT + 6, 1, 'bolt', -96, -128), (SUMMIT + 21, 1, 'bolt', 34, -200), (SUMMIT + 37, 1, 'bolt', -58, -112),
             (STRIKE + 1, 1, 'crackle'), (SHRIEK + 4, 1, 'bolt', 30, -150),
             (SHRIEK + 1, 1, 'crackle'), (TOUCHDOWN + 1, 1, 'bolt', 26, -70)]


def lightning(scene):
    out = []
    for e in LIGHTNING:
        t, half, kind = e[:3]
        if kind == 'crackle':
            (_, _), w = scene.roc_world(t)
            at = w['head']
        elif kind == 'sheet':
            (_, _), w = scene.roc_world(t)
            at = w['body'] + [8, 6, 4]
        else:
            at = np.array([e[3], SUMMIT_FACTS['cap_y'], e[4]])
        out.append({'tick': t, 'half': half, 'kind': kind, 'at': [round(float(v), 2) for v in at]})
    return out


def flash(t):
    """How bright the lightning is lighting things at tick t."""
    f = 0.0
    for e in LIGHTNING:
        if t >= e[0]:
            size = {'crackle': .6, 'sheet': .8, 'bolt': .9}[e[2]]
            f += size*math.exp(-(t - e[0])/2.2)*(.6 + .4*math.cos((t - e[0])*2.1))
    return min(1.0, f)


def overlays(scene, t):
    """white: the cloud white-out; dim: the storm darkening the aerie; shade and beat: its wing's
    shadow crossing the view on each downstroke (beat is how far across, 0 top to 1 bottom);
    wand: the strike's flash; dark: how near black the Roc is drawn; bars: the letterbox."""
    white = .2*ramp(t, CLOUD - 2, CLOUD + 4)*(t < SUMMIT)  # a dark storm cloud, never blank: the cut to the summit is instant
    dim = .45*ramp(t, 4, CAW)*(1 - ramp(t, CLOUD, WHITE))
    beat = shade = 0.0
    for d in DOWNSTROKES:
        if GRIP < d < CLOUD and d - 2 <= t <= d + 8:
            beat, shade = (t - d + 2)/10, .5*math.sin(math.pi*(t - d + 2)/10)
    wand = math.exp(-(t - STRIKE - 1)/3.5) if t >= STRIKE + 1 else 0.0
    if t < SUMMIT:
        dark = .85 - .4*pulse(t, LOOK_UP + 4, SCREAM - 4, SCREAM + 20, CLOUD)
    else:
        dark = .6*(1 - ramp(t, TOUCHDOWN - 10, SETTLED - 2))
    bars = ramp(t, 0, 10)*(1 - ramp(t, RETURN, LENGTH))
    return {'white': white, 'dim': dim, 'shade': shade, 'beat': beat, 'wand': wand, 'dark': dark, 'bars': bars, 'flash': flash(t)}


def cues(scene):
    """Sound cues for the game to play: (tick, cue, where from: 'roc_head', 'roc', 'player',
    'camera' or a point in the frame). None fall inside the white hold but the wind, which loops."""
    out = [(0, 'toll_fade', 'gong'), (6, 'wind_rise', 'camera'), (CAW, 'caw', 'roc_head'), (SNATCH, 'wings_rush', 'roc'),
           (GRIP, 'snatch', 'player'), (GRIP + 2, 'wind_rush', 'camera'), (SCREAM, 'scream', 'roc_head'),
           (CLOUD, 'cloud_roar', 'camera'), (SUMMIT, 'sky_open', 'camera'), (FREE, 'struggle', 'player'), (STRIKE, 'wand_strike', 'hand'),
           (SHRIEK, 'shriek', 'roc_head'), (THROW, 'fling', 'player'), (THROW + 6, 'fall_wind', 'player'),
           (CRASH, 'crash', 'player'), (TOUCHDOWN, 'roc_lands', 'roc')]
    for e in LIGHTNING:
        if e[2] == 'crackle': out.append((e[0], 'crackle', 'roc_head'))
        else: out.append((e[0] + (6 if e[2] == 'bolt' else 3), 'thunder', 'camera' if e[2] == 'sheet' else [e[3], SUMMIT_FACTS['cap_y'], e[4]]))
    out += [(d, 'wingbeat', 'roc') for d in DOWNSTROKES if d < LAND]
    out = [c for c in out if not WHITE <= c[0] < SUMMIT]
    return [{'tick': int(t), 'cue': c, 'from': f} for t, c, f in sorted(out, key=lambda c: c[0])]


# ------------------------------------------------------------------ baking

def r3(v): return [round(float(x), 3) for x in v]


POV_HIDES = ['head', 'body', 'right_arm', 'left_arm']  # the player's bones not drawn while the camera is in their eyes
CONVENTIONS = {
    'frames': 'aerie half (half 0): blocks from the player\'s feet where they struck, +Z toward the gong (the frame '
              'turns with their yaw), +X on their left; summit half (half 1): the summit layout, from the slot '
              'centre\'s corner at SURFACE_Y, unturned',
    'camera': 'eye in the frame; yaw/pitch as Minecraft (yaw 0 looks +Z, 90 looks -X; +pitch looks down) and '
              'relative to the frame; roll in degrees, positive lowers the view\'s right side (in game: '
              'rotation.rotateZ(-roll))',
    'actors': '[x, y, z, w, qx, qy, qz]: the model origin, and the rotation that turns a model facing +Z (after '
              'GeckoLib\'s mirror and half turn); the Roc is drawn at 1.25 scale; consecutive quaternions keep a '
              'positive dot product',
    'pov': 'while 1, the player model hides ' + ', '.join(POV_HIDES),
    'hold': 'the scene may wait on the hold tick, inside the cloud, until the summit is ready; ticks after it run late by the wait',
}


def bake_track(scene):
    ticks, last = [], {}
    for g in range(LENGTH*PACE + 1):
        t = g/PACE
        eye, yaw, pitch, roll, fov = camera(scene, t)
        (root, rr), w = scene.roc_world(t)
        proot, pr = scene.player(t)
        o = overlays(scene, t)
        places = {}
        for name, (p, r) in (('roc', (root, rr)), ('player', (proot, pr))):
            q = quat(r)
            if name in last and np.dot(q, last[name]) < 0: q = -q
            last[name] = q
            places[name] = r3(p) + [round(float(x), 5) for x in q]
        in_eyes = t < SUMMIT or np.linalg.norm(np.array(eye) - scene.eye(t)) < 1.5
        ticks.append({'half': scene.half(t), 'eye': r3(eye), 'yaw': round(float(yaw), 2), 'pitch': round(float(pitch), 2),
                      'roll': round(float(roll), 2), 'fov': round(float(fov), 2), **places,
                      'pov': 1 if in_eyes else 0, 'roc_head': r3(w['head']), 'hand': r3(scene.hand(t)),
                      **{k: round(float(v), 3) for k, v in o.items()}})
    columns = {k: [tick[k] for tick in ticks] for k in ticks[0]}
    # Everything the game reads is in its own ticks, at PACE.
    flashes = [{**e, 'tick': e['tick']*PACE} for e in sorted(lightning(scene), key=lambda e: e['tick'])]
    cloud_base = round(float(scene.eye(CLOUD)[1]) - 1.5, 1)
    return {'beats': {k: v*PACE for k, v in BEATS.items()}, 'cuts': [c*PACE for c in CUTS], 'conventions': CONVENTIONS,
            'pov_hides': POV_HIDES, 'summit': SUMMIT_FACTS, 'aerie': {'gong': [0, 0, 4], 'stand': [0, 0, 0], 'cloud_base': cloud_base},
            'lightning': flashes, 'cues': [{**c, 'tick': c['tick']*PACE} for c in cues(scene)], 'ticks': columns}


def build():
    scene = Scene()
    roc_clip = bake(lambda seconds: roc_channels(seconds*20/PACE), LENGTH*PACE/20, RATE, .02)
    player_clip = bake(lambda seconds: puppet_channels(seconds*20/PACE), LENGTH*PACE/20, RATE, .05)
    return scene, roc_clip, player_clip, bake_track(scene)


def dump(data): return json.dumps(data, indent=2) + '\n'


def outputs():
    scene, roc_clip, player_clip, track = build()
    files = {
        HERE/'storm_roc_intro.geo.json': json.dumps(ROC_GEO, separators=(',', ':')) + '\n',
        HERE/'storm_roc_intro.animation.json': json.dumps({'format_version': '1.8.0', 'animations': {
            'animation.storm_roc.intro': roc_clip}}, separators=(',', ':')) + '\n',
        PLAYER/'storm_roc_intro_player.geo.json': dump(PUPPET_GEO),
        PLAYER/'storm_roc_intro_player_slim.geo.json': dump(PUPPET_SLIM),
        PLAYER/'storm_roc_intro_player.animation.json': dump({'format_version': '1.8.0', 'animations': {
            'animation.storm_roc_intro_player.intro': player_clip}}),
        HERE/'intro-track.json': json.dumps(track, separators=(',', ':')) + '\n',
    }
    runtime = {
        ASSETS/'geckolib/models/storm_roc_intro.geo.json': files[HERE/'storm_roc_intro.geo.json'],
        ASSETS/'geckolib/animations/storm_roc_intro.animation.json': files[HERE/'storm_roc_intro.animation.json'],
        ASSETS/'geckolib/models/storm_roc_intro_player.geo.json': files[PLAYER/'storm_roc_intro_player.geo.json'],
        ASSETS/'geckolib/models/storm_roc_intro_player_slim.geo.json': files[PLAYER/'storm_roc_intro_player_slim.geo.json'],
        ASSETS/'geckolib/animations/storm_roc_intro_player.animation.json': files[PLAYER/'storm_roc_intro_player.animation.json'],
        ASSETS/'cinematics/storm_roc_intro.json': files[HERE/'intro-track.json'],
    }
    return scene, files, runtime


def cloud_texture():
    """The inside of the storm cloud the game draws over the screen while the player is carried into
    the sky realm: tileable soft grey billows with alpha, as PNG bytes."""
    from io import BytesIO
    from PIL import Image
    n, rng = 256, np.random.default_rng(7)

    def tile_noise(freq):
        g = rng.random((freq, freq))
        x = np.arange(n)/n*freq
        i0 = np.floor(x).astype(int) % freq; i1 = (i0 + 1) % freq
        f = x - np.floor(x); f = f*f*(3 - 2*f)
        a, b, c, d = g[i0][:, i0], g[i0][:, i1], g[i1][:, i0], g[i1][:, i1]
        fx, fy = f[None, :], f[:, None]
        return (a*(1 - fx) + b*fx)*(1 - fy) + (c*(1 - fx) + d*fx)*fy
    fbm = sum(tile_noise(2**k)*.5**(k - 1) for k in range(2, 8))
    fbm = (fbm - fbm.min())/(fbm.max() - fbm.min())
    shade = np.clip(.55 + .6*(fbm - .5), 0, 1)
    alpha = np.clip((fbm - .25)/.5, 0, 1)**1.2
    img = np.zeros((n, n, 4), np.uint8)
    img[..., 0] = (shade*205).astype(np.uint8); img[..., 1] = (shade*210).astype(np.uint8)
    img[..., 2] = (shade*222).astype(np.uint8); img[..., 3] = (alpha*255).astype(np.uint8)
    out = BytesIO()
    Image.fromarray(img, 'RGBA').save(out, 'PNG')
    return out.getvalue()


def animatic():
    """Copies what the browser animatic loads into its local preview folder."""
    PREVIEW.mkdir(parents=True, exist_ok=True)
    copies = {
        HERE/'storm_roc_intro.geo.json': 'storm_roc_intro.geo.json',
        ASSETS/'textures/entity/storm_roc.png': 'storm_roc.png',
        ASSETS/'textures/entity/storm_roc_glowmask.png': 'storm_roc_glowmask.png',
        ASSETS/'geckolib/models/storm_gong.geo.json': 'storm_gong.geo.json',
        ASSETS/'textures/block/storm_gong.png': 'storm_gong.png',
        HERE/'storm_roc_intro.animation.json': 'storm_roc_intro.animation.json',
        PLAYER/'storm_roc_intro_player.geo.json': 'player.geo.json',
        PLAYER/'storm_roc_intro_player_slim.geo.json': 'player_slim.geo.json',
        PLAYER/'storm_roc_intro_player.animation.json': 'player.animation.json',
        HERE/'intro-track.json': 'intro-track.json',
        HERE/'animatic.js': 'animatic.js',
        HERE/'viewer.template.html': 'index.html',
    }
    for source, name in copies.items():
        if source.exists(): shutil.copyfile(source, PREVIEW/name)
        else: print(f'animatic: missing {source.relative_to(REPO)}')
    # The game's default skins stand in for the player's own, for this local preview only.
    if MINECRAFT.exists():
        with zipfile.ZipFile(MINECRAFT) as jar:
            for kind, name in (('wide', 'steve'), ('slim', 'alex')):
                (PREVIEW/f'skin_{name}.png').write_bytes(jar.read(f'assets/minecraft/textures/entity/player/{kind}/{name}.png'))
    else: print(f'animatic: no {MINECRAFT} for the default skins')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--install', action='store_true', help='also write the copies the game loads: models, clips and track')
    parser.add_argument('--check', action='store_true', help='fail if any output (or installed copy) differs from a fresh bake')
    args = parser.parse_args()
    scene, files, runtime = outputs()
    images = {ASSETS/'textures/misc/storm_roc_intro_cloud.png': cloud_texture()}
    if args.check:
        stale = [p for p, text in {**files, **runtime}.items() if not p.exists() or p.read_text() != text]
        stale += [p for p, data in images.items() if not p.exists() or p.read_bytes() != data]
        for p in stale: print(f'stale: {p.relative_to(REPO)}')
        sys.exit(1 if stale else 0)
    for path, text in (files | runtime if args.install else files).items():
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text)
    if args.install:
        for path, data in images.items():
            path.parent.mkdir(parents=True, exist_ok=True); path.write_bytes(data)
    animatic()
    print(f'Intro baked: {LENGTH*PACE/20:.1f} s. Roc clip {len(files[HERE/"storm_roc_intro.animation.json"])//1024} KB.')
