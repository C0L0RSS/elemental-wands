#!/usr/bin/env python3
"""The Guardian's intro: a zombie wanders the nave, looks up, and the Guardian smashes it.

A zombie shuffles out of the dark toward the players, stops, turns at a rumble overhead and looks
up. The Guardian arcs down out of the vaults with both fists raised and hammers it flat. It hauls
the zombie up by the head to look at it, grabs its feet, rips it in two at the waist, flings the
legs away and hurls the rest hard over the players' heads, then points at them.

One script authors both actors and the camera from the same beats, so the zombie stays in the
Guardian's fists and every shot frames them. It writes:
  intro.animation.json                 the Guardian's clip (tools/prepare_guardian_assets.py merges it)
  intro.bbmodel                        the Guardian and its clip, to open in Blockbench
  zombie/guardian_intro_zombie.geo.json  a vanilla zombie split at the waist (it wears the game's own
                                       zombie texture, so none is kept here)
  zombie/guardian_intro_zombie.animation.json  the zombie's clip, posed in the Guardian's frame
  intro-track.json                     per tick: the camera, the zombie's halves and the fists; the beats
  intro-review.png                     the Guardian's key poses
and the browser animatic in .local-previews/guardian-intro-v2/ (launch entry guardian-intro-preview).

Run from the repository root:
    python3 art/fractured_guardian/intro/build_intro.py             # bake everything and the animatic
    python3 art/fractured_guardian/intro/build_intro.py --install   # also write the runtime zombie and track
    python3 art/fractured_guardian/intro/build_intro.py --check     # fail if any output has drifted
    python3 tools/prepare_guardian_assets.py                        # merge the Guardian's clip

Both actors share one origin, the seat, and the Guardian's facing. Poses are in the models' own
pixel space (Bedrock geometry: +Y up, the front toward -Z). The track is in blocks in the scene's
frame, as GuardianIntro.place reads it: +Z straight ahead of the Guardian, +X on its left.
GeckoLib mirrors model X and the renderer then turns the model to face ahead, so a model point
(x, y, z) sits at (x, y, -z)/16 in that frame.
"""
import argparse
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
from rig_tools import rig, fingers, fist_point, lowest, reach, leg, pos, rot, euler, pivot, sheet, transforms  # noqa: E402

ASSETS = REPO/'src/main/resources/assets/elementalwands'
ZOMBIE = HERE/'zombie'
PREVIEW = REPO/'.local-previews/guardian-intro-v2'
MINECRAFT = Path.home()/'.gradle/caches/fabric-loom/1.21.10/minecraft-client.jar'  # the animatic's zombie skin

# Beats, in ticks from the scene's first frame. GuardianIntro.java shares them (its contract test
# reads intro-track.json).
LENGTH = 476
STOP = 64        # the zombie reaches its mark; cut to straight above it
TURN = 70        # it turns round to face the seat (for 16 ticks)
LOOK = 88        # and tilts its head back to look up
FALL = 100       # the Guardian drops out of the vaults
POV = 116        # cut to the zombie's eyes
IMPACT = 156     # both fists hammer it flat; cut to the front. From here on it moves like the slow brawler it is
RISE = 190       # after a roar and a long breath, it reaches for the zombie's head
GRIP = 208       # and closes its fist on it
SHOW = 232       # cut in closer: it has hauled the zombie up to its face
GRAB = 260       # its left fist has the feet
TEAR = 288       # it rips the zombie in two at the waist
TOSS = 312       # the legs leave the left fist
HURL = 344       # the top half leaves the right, hard at the camera
PLAYERS = 348    # cut to the players' line as it flies over them
POINT = 366      # it slowly raises its arm and points at the players
TITLE, TITLE_END, RETURN = 382, 458, 458
BEATS = dict(length=LENGTH, stop=STOP, turn=TURN, look=LOOK, fall=FALL, pov=POV, impact=IMPACT, rise=RISE, grip=GRIP,
             show=SHOW, grab=GRAB, tear=TEAR, toss=TOSS, hurl=HURL, players=PLAYERS, point=POINT,
             title=TITLE, title_end=TITLE_END, ret=RETURN)
CUTS = [STOP, POV, IMPACT, SHOW, PLAYERS]
RATE = 40  # clip samples per second before key reduction

# Places, in blocks in the scene's frame.
WALK_FROM, MARK = np.array([-1.3, 0, -1.9]), np.array([0, 0, 3.4])
HEIGHT, BEHIND = 72, 34  # the Guardian drops from this high, arcing in from this far behind the seat
PIN = np.array([0, 0, 2.55])  # the flattened zombie's chest, under both fists
SHOT5_EYE, SHOT6_EYE = np.array([-.35, 3.55, 7.6]), np.array([-2.1, 1.35, 26.2])
OVER_PLAYERS, GONE = np.array([-.55, 3.65, 28]), np.array([-.3, 2.6, 52])  # the top half's flight
LEGS_GONE = np.array([17, 9, -9])  # far off the Guardian's left, long out of shot


# ------------------------------------------------------------------ easing

def smooth(s):
    s = min(1, max(0, s))
    return s*s*(3 - 2*s)


EASE = {
    'io': smooth,                                   # settle in and out
    'in': lambda s: s*s*s,                          # gathering speed into a blow
    'out': lambda s: 1 - (1 - s)**3,                # snapping out of it, then settling
    'back': lambda s: 1 + 2.70158*(s - 1)**3 + 1.70158*(s - 1)**2,  # overshooting, then settling back
    'lin': lambda s: s,
}


def ramp(t, a, b): return smooth((t - a)/(b - a))
def mix(a, b, s): return np.array(a, dtype=float)*(1 - s) + np.array(b, dtype=float)*s


def curve(t, rows, fill=None):
    """Values at tick t from rows of (tick, ease, values...). Each row's ease shapes the move into
    it. A string value is a moving point, fill(name, t)."""
    def value(v): return np.array(fill(v, t) if isinstance(v, str) else v, dtype=float)
    if t <= rows[0][0]: return [value(v) for v in rows[0][2:]]
    for a, b in zip(rows, rows[1:]):
        if t <= b[0]:
            s = min(1, max(0, (t - a[0])/(b[0] - a[0])))
            e = EASE[b[1]](s)
            return [value(x)*(1 - e) + value(y)*e for x, y in zip(a[2:], b[2:])]
    return [value(v) for v in rows[-1][2:]]


def frame(v): return np.array([v[0]/16, v[1]/16, -v[2]/16])
def model(f): return np.array([16*f[0], 16*f[1], -16*f[2]])


# ------------------------------------------------------------------ rotations

def Rx(a): c, s = math.cos(a), math.sin(a); return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
def Ry(a): c, s = math.cos(a), math.sin(a); return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
def Rz(a): c, s = math.cos(a), math.sin(a); return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def json_matrix(r):
    """The bone rotation for a JSON rotation, as GeckoLib applies it (the rig's convention)."""
    x, y, z = np.radians(r)
    return Rz(-z)@Ry(y)@Rx(-x)


def axis_angle(axis, degrees):
    a = np.array(axis, dtype=float); a /= np.linalg.norm(a)
    k = np.array([[0, -a[2], a[1]], [a[2], 0, -a[0]], [-a[1], a[0], 0]])
    r = math.radians(degrees)
    return np.eye(3) + math.sin(r)*k + (1 - math.cos(r))*k@k


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


# ------------------------------------------------------------------ the Guardian

STANCE, WIDE_FRONT, WIDE_REAR, STRIDE = (4, 5), (-7, 5), (9, 5), (-13, 5)
SQUAT_REAR = (10, 11)  # the rear heel up and its knee just off the floor
# Pelvis offset, front (left) ankle and rear (right) ankle as (z, y), rear toe.
LEGS = [(FALL, 'io', [0, 0, 0], (-4, 12), (10, 13), 10),
        (IMPACT - 8, 'io', [0, 1, 0], (-6, 13), (12, 14), 12),
        (IMPACT - 2, 'in', [0, 0, 0], (-8, 7), (10, 6), 0),
        (IMPACT + 2, 'out', [0, -12, 2], (-12, 5), SQUAT_REAR, 15),  # the smash drives it into a crouch
        (IMPACT + 14, 'io', [0, -9, 2], (-12, 5), SQUAT_REAR, 15),
        (RISE, 'io', [0, -9.5, 2], (-12, 5), SQUAT_REAR, 15),
        (RISE + 10, 'io', [0, -11.5, 2], (-12, 5), SQUAT_REAR, 15),  # sinks to take the zombie's head
        (GRIP, 'io', [0, -11.5, 2], (-12, 5), SQUAT_REAR, 15),
        (SHOW - 4, 'io', [0, .8, -1], WIDE_FRONT, WIDE_REAR, 0),  # stands, slowly, hauling it up
        (SHOW + 6, 'io', [0, 0, 0], WIDE_FRONT, WIDE_REAR, 0),
        (TEAR - 12, 'io', [0, -3, 1], WIDE_FRONT, WIDE_REAR, 0),  # braces
        (TEAR + 8, 'out', [0, .6, 1.5], WIDE_FRONT, WIDE_REAR, 0),  # rips upward
        (TOSS, 'io', [0, -1.5, 0], WIDE_FRONT, WIDE_REAR, 0),
        (HURL - 14, 'io', [0, -2, 4], WIDE_FRONT, WIDE_REAR, 0),  # rocks back onto the rear foot
        (HURL - 3, 'in', [0, -3.5, -2], STRIDE, WIDE_REAR, 0),  # strides into the throw
        (HURL + 6, 'out', [0, -6, -5], STRIDE, (12, 11), 22),  # lunges through it, rear heel up
        (HURL + 20, 'io', [0, -2, -3], STRIDE, WIDE_REAR, 0),
        (RETURN, 'io', [0, -1.5, -2.5], STRIDE, WIDE_REAR, 0),
        (LENGTH - 2, 'io', [0, 0, 0], STANCE, STANCE, 0)]
# Feet lift clear of the floor while they move: (from, to, which, height).
STEPS = [(GRIP, SHOW - 4, 'both', 4), (HURL - 10, HURL - 3, 'front', 6), (RETURN, LENGTH - 2, 'both', 3)]
# Torso (pitch, twist, roll), head (pitch, yaw, roll) and jaw. A positive pitch leans or looks down;
# a positive twist or head yaw turns toward the Guardian's right. Only the blows are quick.
BODY = [(FALL, 'io', [-10, 0, 0], [26, 0, 0], 8),
        (IMPACT - 8, 'io', [-16, 0, 0], [30, 0, 0], 14),
        (IMPACT - 3, 'io', [-24, 0, 0], [26, 0, 0], 20),  # wound back for the smash
        (IMPACT, 'in', [52, 0, 0], [-8, 0, 0], 10),
        (IMPACT + 3, 'out', [58, 0, 0], [6, 0, 0], 6),
        (IMPACT + 8, 'io', [56, 0, 0], [4, 0, 0], 8),  # stays down over it a moment
        (IMPACT + 22, 'io', [42, 0, 0], [-44, 0, 0], 32),  # slowly lifts its head into a roar
        (IMPACT + 34, 'io', [44, 0, 3], [-38, -4, 6], 26),
        (RISE, 'io', [42, 0, 0], [-30, 4, 0], 6),
        (RISE + 10, 'io', [48, 4, 0], [-20, 8, 0], 4),  # eyes on the zombie
        (SHOW - 4, 'io', [-8, 10, 0], [10, 16, 0], 2),  # hauls it up, leaning back
        (SHOW + 10, 'io', [-3, 12, 0], [6, 20, 16], 10),  # studies it, head cocked
        (GRAB - 14, 'io', [0, 9, 0], [4, 16, 10], 4),
        (GRAB, 'io', [6, 0, 0], [10, 0, 0], 2),  # takes the feet
        (TEAR - 12, 'io', [12, 0, 0], [14, 0, 0], 0),  # hunches and strains
        (TEAR, 'io', [15, 0, 0], [12, 0, 0], 0),
        (TEAR + 8, 'out', [-16, 0, 0], [-20, 0, 0], 30),  # rips: arches back, roaring
        (TEAR + 18, 'io', [-4, 0, 0], [-6, 0, 0], 14),
        (TOSS - 8, 'io', [2, 12, 0], [4, 10, 0], 6),  # the left arm winds across
        (TOSS + 4, 'out', [4, -24, 0], [0, -20, 0], 10),  # and flings the legs away
        (TOSS + 14, 'io', [2, -14, 0], [0, -8, 0], 4),
        (HURL - 4, 'io', [-16, 34, -6], [-4, 12, 0], 10),  # wound up, the right shoulder far back
        (HURL + 1, 'in', [16, -26, 4], [-6, -8, 0], 24),  # and hurls
        (HURL + 10, 'out', [24, -32, 4], [-14, -6, 0], 20),  # following it over the players
        (HURL + 20, 'io', [8, -8, 0], [-4, 0, 0], 6),
        (POINT, 'io', [4, 0, 0], [0, 0, 0], 4),
        (POINT + 20, 'io', [10, 4, 0], [8, -2, -6], 8),  # points, leaning in, head cocked
        (RETURN, 'io', [9, 4, 0], [8, -2, -6], 6),
        (LENGTH - 2, 'io', [0, 0, 0], [0, 0, 0], 0)]
REST_R, REST_L = [-34, 12, -12], [34, 12, -12]
# Fist targets in model space (a string is a point on the zombie), how far each hand is closed (1 is
# a fist) and, for the right, how straight the index finger is.
RIGHT = [(FALL, 'io', [-9, 100, 8], 1, 0),  # both fists raised together
         (IMPACT - 8, 'io', [-9, 100, 12], 1, 0),
         (IMPACT - 3, 'io', [-8, 94, 26], 1, 0),  # wound back behind the head
         (IMPACT, 'in', 'smash_r', 1, 0),  # hammered down on it
         (RISE, 'io', 'smash_r', 1, 0),
         (RISE + 10, 'io', 'head', .45, 0),  # opens over its head
         (GRIP, 'io', 'head', .95, 0),  # and grips it
         (SHOW - 4, 'io', [-16, 74, -34], .95, 0),  # hauled up to its face
         (SHOW + 10, 'io', [-14, 71, -36], .95, 0),
         (GRAB - 12, 'io', [-18, 66, -36], .95, 0),
         (GRAB, 'io', [-28, 60, -34], .95, 0),  # swung across the chest
         (TEAR - 12, 'io', [-23, 57, -32], .95, 0),  # braced, pulling in
         (TEAR, 'io', [-22, 56, -32], .95, 0),
         (TEAR + 8, 'out', [-48, 66, -22], .95, 0),  # ripped wide
         (TEAR + 18, 'io', [-40, 62, -28], .95, 0),
         (TOSS + 4, 'io', [-38, 62, -28], .95, 0),
         (HURL - 24, 'io', [-34, 68, -22], .95, 0),
         (HURL - 4, 'io', [-36, 92, 22], .95, 0),  # wound up overhead
         (HURL, 'in', [-16, 84, -46], .95, 0),  # heaved forward: let go
         (HURL + 1, 'lin', [-12, 76, -48], .15, 0),
         (HURL + 10, 'out', [4, 34, -34], .35, 0),  # following through across the body
         (HURL + 20, 'io', REST_R, .6, 0),
         (POINT, 'io', [-30, 24, -20], .8, 0),
         (POINT + 20, 'io', [-12, 64, -58], 1, 1),
         (RETURN, 'io', [-12, 63, -57], 1, 1),
         (LENGTH - 2, 'io', REST_R, .45, 0)]
LEFT = [(FALL, 'io', [9, 100, 8], 1),
        (IMPACT - 8, 'io', [9, 100, 12], 1),
        (IMPACT - 3, 'io', [8, 94, 26], 1),
        (IMPACT, 'in', 'smash_l', 1),
        (RISE + 10, 'io', 'smash_l', 1),
        (RISE + 22, 'io', [30, 30, -10], .5),
        (SHOW, 'io', [34, 26, -8], .6),
        (GRAB - 14, 'io', [20, 42, -30], .45),  # reaching
        (GRAB - 4, 'io', 'feet', .45),
        (GRAB, 'io', 'feet', .95),  # takes them
        (TEAR, 'io', 'feet', .95),
        (TEAR + 8, 'out', [34, 50, -20], .95),  # ripped wide
        (TEAR + 18, 'io', [28, 54, -26], .95),
        (TOSS - 8, 'io', [6, 62, -34], .95),  # wound across the body
        (TOSS, 'out', [44, 66, -6], .95),  # flung: let go
        (TOSS + 1, 'lin', [46, 64, 0], .2),
        (TOSS + 12, 'out', [40, 44, 16], .4),
        (HURL - 18, 'io', [36, 40, 4], .6),
        (HURL - 4, 'io', [30, 60, -30], .9),  # thrown forward for balance
        (HURL + 4, 'io', [34, 50, 8], .9),  # and back as the right arm throws
        (HURL + 20, 'io', REST_L, .7),
        (LENGTH - 2, 'io', REST_L, .45)]

def fist(p, side, closure, index=0.0):
    """Curls the fingers toward the palm and lays the thumb across them; index straightens the
    first finger to point. Positive X curls these downward-pointing finger bones toward the palm;
    the shared v4 helper curls the other way, so an open hand starts from its relaxed pose."""
    fingers(p, side, 5, 0, 0)
    sign = -1 if side == 'right' else 1
    for i in range(1, 4):
        c = closure*(1 - index) if i == 1 else closure
        for bone, target in ((f'{side}_finger_{i}', [96*(.76 + .06*i), 0, 0]), (f'{side}_finger_{i}_tip', [72, 0, 0])):
            rot(p, bone, mix(p[bone]['rotation'], target, c))
    for bone, target in ((side + '_thumb', [0, 0, -sign*90]), (side + '_thumb_tip', [0, 0, -sign*50])):
        rot(p, bone, mix(p[bone]['rotation'], target, closure))


def grip(p, side):
    """The centre of the closed hand in model space: where the zombie is held."""
    return (transforms(p)[side + '_forearm']@np.append(fist_point(p, side), 1))[:3]


def fall(t):
    """The root's offset in pixels: waiting high behind the seat, then arcing down onto it, faster
    and faster, so the zombie looking up sees its face and raised fists coming."""
    s = min(1, max(0, (t - FALL)/(IMPACT - FALL)))
    return np.array([0, HEIGHT*16*(1 - s*s), BEHIND*16*(1 - s)])


def planted(pelvis, front, rear):
    """Lowers the hips just enough that both legs reach their feet: a wide stance bends the knees."""
    reach_ = math.hypot(14, 3) + math.hypot(13, 3) - .3
    for side, foot in (('left', front), ('right', rear)):
        dz = foot[0] - (pivot(side + '_thigh')[2] + pelvis[2])
        highest = math.sqrt(max(0, reach_**2 - dz*dz)) - (pivot(side + '_thigh')[1] - foot[1])
        pelvis = np.array([pelvis[0], min(pelvis[1], highest), pelvis[2]])
    return pelvis


def airborne(p, target):
    """A target authored around the Guardian's feet, carried along with its fall and lean."""
    return (transforms(p)['root']@np.append(target, 1))[:3]


def lift(t):
    """How far each foot (front, rear) is off the floor while it steps."""
    up = [0.0, 0.0]
    for a, b, feet, height in STEPS:
        if a <= t <= b:
            h = height*math.sin(math.pi*(t - a)/(b - a))
            if feet in ('both', 'front'): up[0] = h
            if feet in ('both', 'rear'): up[1] = h
    return up


def guardian(t, fill):
    """The Guardian's pose at tick t, all but the left arm (which may reach for the zombie, whose
    place depends on the right fist). fill(name, t) gives the points on the zombie."""
    p = {}
    pos(p, 'root', fall(t))
    rot(p, 'root', [22*(1 - ramp(t, IMPACT - 6, IMPACT - 1)) if t < IMPACT else 0, 0, 0])
    pelvis, front, rear, toe = curve(t, LEGS)
    up = lift(t)
    heave = math.sin(t*.2)*ramp(t, IMPACT + 12, IMPACT + 20)*(1 - ramp(t, RISE + 6, RISE + 12))
    breath = heave + .4*math.sin(t*.16)*ramp(t, POINT + 14, POINT + 22)
    front, rear = front + [0, up[0]], rear + [0, up[1]]
    pelvis = planted(pelvis + [0, .7*breath, 0], front, rear)
    pos(p, 'pelvis', pelvis)
    leg(p, 'left', front, 0)
    leg(p, 'right', rear, float(toe))
    torso, head, jaw = curve(t, BODY)
    # Straining to tear it: a shiver building in the arms and back.
    shiver = ramp(t, GRAB + 2, TEAR)*(1 - ramp(t, TEAR, TEAR + 1))*math.sin(t*2.7)
    rot(p, 'torso', torso + [1.6*breath + .8*shiver, .6*shiver, 0])
    rot(p, 'head', head + [-.9*breath, 0, 0])
    rot(p, 'jaw', [float(jaw), 0, 0])
    # The smash slams the chest plates open and swells the core; so does the rip.
    slam = ramp(t, IMPACT, IMPACT + 2)*(1 - ramp(t, IMPACT + 2, IMPACT + 14))
    slam += .7*ramp(t, TEAR + 2, TEAR + 6)*(1 - ramp(t, TEAR + 6, TEAR + 16))
    for sign in (-1, 1):
        pos(p, f'chest_plate_{sign}', [sign*2.4*slam, .4*slam, -1.3*slam])
        rot(p, f'chest_plate_{sign}', [0, -sign*15*slam, 0])
        pos(p, ('right' if sign < 0 else 'left') + '_shoulder', [0, 1.2*breath + .5*shiver, 0])
    swell = 1 + .45*slam
    p['core'] = {'position': np.array([0, 0, -1.1*slam]), 'scale': np.array([swell, swell, swell])}

    target, closure, index = curve(t, RIGHT, fill)
    if t < IMPACT: target = airborne(p, target)  # the fall's targets ride with it
    # It gives the zombie a shake as it looks at it.
    shake_it = ramp(t, SHOW + 4, SHOW + 8)*(1 - ramp(t, GRAB - 16, GRAB - 12))
    target = target + shake_it*np.array([1.5*math.sin(t*.55), 1.2*math.sin(t*.8), 0])
    fist(p, 'right', float(closure), float(index))
    reach(p, 'right', target, [-1, .2, 1])
    return p


def left_arm(p, t, fill):
    target, closure = curve(t, LEFT, fill)
    if t < IMPACT: target = airborne(p, target)
    fist(p, 'left', float(closure))
    reach(p, 'left', target, [1, .2, 1])


# ------------------------------------------------------------------ the zombie

def zombie_geometry():
    """The vanilla zombie's shape and texture layout, split where the legs meet the body."""
    def cube(origin, size, uv, **extra): return dict(origin=origin, size=size, uv=uv, **extra)
    bones = [
        dict(name='root', pivot=[0, 0, 0]),
        dict(name='lower', parent='root', pivot=[0, 12, 0]),
        dict(name='right_leg', parent='lower', pivot=[-1.9, 12, 0], cubes=[cube([-3.9, 0, -2], [4, 12, 4], [0, 16])]),
        dict(name='left_leg', parent='lower', pivot=[1.9, 12, 0], cubes=[cube([-.1, 0, -2], [4, 12, 4], [0, 16], mirror=True)]),
        dict(name='upper', parent='root', pivot=[0, 12, 0], cubes=[cube([-4, 12, -2], [8, 12, 4], [16, 16])]),
        dict(name='head', parent='upper', pivot=[0, 24, 0], cubes=[cube([-4, 24, -4], [8, 8, 8], [0, 0]),
                                                                   cube([-4, 24, -4], [8, 8, 8], [32, 0], inflate=.5)]),
        dict(name='right_arm', parent='upper', pivot=[-5, 22, 0], cubes=[cube([-8, 12, -2], [4, 12, 4], [40, 16])]),
        dict(name='left_arm', parent='upper', pivot=[5, 22, 0], cubes=[cube([4, 12, -2], [4, 12, 4], [40, 16], mirror=True)]),
    ]
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.guardian_intro_zombie', 'texture_width': 64, 'texture_height': 64,
                        'visible_bounds_width': 3, 'visible_bounds_height': 3, 'visible_bounds_offset': [0, 1.5, 0]},
        'bones': bones}]}


PIVOT = np.array([0, 12, 0], dtype=float)  # both halves turn about the waist
HEAD_PIVOT = np.array([0, 24, 0], dtype=float)
HEAD, FEET, UPPER_MID, LOWER_MID = [0, 28, 0], [0, 1, 0], [0, 20, 0], [0, 6, 0]
EYE = [0, 28, -4.7]


def half(r, anchor, at, squash=1.0, shown=True):
    """A half posed so its model-space anchor lands on the point at, turned by r, squashed
    through its thickness: GeckoLib's (rotation, position, scale) for a bone pivoted at the waist.
    A half no longer shown is scaled to nothing."""
    s = np.diag([1, 1, squash])
    position = np.array(at, dtype=float) - PIVOT - r@s@(np.array(anchor, dtype=float) - PIVOT)
    return {'r': r, 'squash': squash, 'position': position, 'scale': np.array([1, 1, squash]) if shown else np.zeros(3)}


def placed(h, local):
    """Where a point of a half's rest geometry ends up."""
    return h['position'] + PIVOT + h['r']@np.diag([1, 1, h['squash']])@(np.array(local, dtype=float) - PIVOT)


def walk(t):
    """Where the zombie stands at tick t: shuffling to its mark, easing to a stop."""
    s = min(1, max(0, t/STOP))
    return mix(WALK_FROM, MARK, s*(2 - s))


def limb_swing(t):
    """Vanilla's LimbAnimator over the walk: (position, speed) at tick t."""
    position = speed = 0.0
    for i in range(1, int(t) + 1):
        d = walk(i) - walk(i - 1)
        speed += (min(1, 4*math.hypot(d[0], d[2])) - speed)*.4
        position += speed
    return position + speed*(t - int(t)), speed


WALK_YAW = math.atan2(-(MARK - WALK_FROM)[0], (MARK - WALK_FROM)[2])  # in model space (x, -z): toward the mark
TURN_BY = math.degrees((math.atan2(MARK[0], -MARK[2]) - WALK_YAW + math.pi) % (2*math.pi) - math.pi)  # round to the seat


def mouse(t, moves, start=0.0):
    """Where a player's mouse has the view at tick t: it flicks to each target fast, overshoots a
    little and settles, then holds with a faint tremor. moves are (tick, ticks to get there, target)."""
    held = start
    tremor = .5*math.sin(t*2.3) + .3*math.sin(t*3.7 + 1)
    for t0, ticks, target in moves:
        if t < t0: return held + tremor
        if t < t0 + ticks: return held + (target - held)*EASE['back']((t - t0)/ticks) + tremor
        held = target
    return held + tremor


# The zombie looks about like a player: a couple of glances as it walks, a stuttering turn back
# toward the rumble behind it, then looking up in fits and starts. Yaw in degrees off its walk,
# turning toward the seat; pitch negative looks up.
LOOK_YAW = [(16, 3, -18), (24, 2, -14), (34, 3, 12), (44, 2, 4), (52, 3, 0),
            (TURN, 2, .22*TURN_BY), (TURN + 3, 1, .26*TURN_BY), (TURN + 8, 3, .6*TURN_BY), (TURN + 13, 2, .96*TURN_BY),
            (TURN + 18, 2, TURN_BY), (LOOK + 11, 1, TURN_BY + 6), (LOOK + 20, 2, TURN_BY + 2)]
LOOK_PITCH = [(16, 3, 4), (34, 3, -3), (52, 3, 0), (TURN + 8, 2, 5), (TURN + 18, 2, 0),
              (LOOK, 2, -14), (LOOK + 5, 1, -17), (LOOK + 8, 2, -38), (LOOK + 13, 1, -35),
              (LOOK + 16, 3, -63), (LOOK + 21, 1, -60), (LOOK + 24, 2, -66)]


def _body_yaws():
    """The body follows the head as vanilla's does: it lags behind, and never lets the head get more
    than 50 degrees ahead of it."""
    body, out = 0.0, []
    for t in range(IMPACT + 1):
        head = mouse(t, LOOK_YAW)
        body += (head - body)*.18
        body = min(max(body, head - 50), head + 50)
        out.append(body)
    return out


BODY_YAWS = _body_yaws()


def body_yaw(t):
    i = min(int(t), len(BODY_YAWS) - 2)
    return BODY_YAWS[i] + (BODY_YAWS[i + 1] - BODY_YAWS[i])*min(1, t - i)


def heading(t):
    return Ry(WALK_YAW + math.radians(body_yaw(min(t, IMPACT))))


# Face down with its head toward the seat: up (+Y) runs to +Z (toward the seat in model space),
# the face (-Z) to -Y; so +Z goes to +Y and +X to -X.
FACE_DOWN = np.column_stack([[-1, 0, 0], [0, 0, 1], [0, 1, 0]]).astype(float)
HANGING = np.eye(3)  # upright, facing ahead like the Guardian
# Held across the Guardian's chest: head to its right (-X), still facing ahead.
ACROSS = np.column_stack([[0, 1, 0], [-1, 0, 0], [0, 0, 1]]).astype(float)


def zombie_limbs(t):
    """Head, arm and leg rotations (JSON) at tick t. Walking follows vanilla's zombie model:
    legs swing cos(limb * .6662) * 1.4 * speed, arms reach forward at -pi/2.25 and bob."""
    limb, speed = limb_swing(min(t, STOP + 10))
    swing = math.degrees(math.cos(limb*.6662)*1.4*speed)
    bob_pitch, bob_roll = math.degrees(math.sin(t*.067)*.05), math.degrees(math.cos(t*.09)*.05 + .05)
    head_yaw = mouse(min(t, IMPACT), LOOK_YAW) - body_yaw(min(t, IMPACT))
    limbs = {'head': [mouse(min(t, IMPACT), LOOK_PITCH), head_yaw, 0],
             'right_arm': [-80 + bob_pitch, 0, bob_roll], 'left_arm': [-80 - bob_pitch, 0, -bob_roll],
             'right_leg': [swing, 0, 0], 'left_leg': [-swing, 0, 0]}
    if t >= IMPACT:
        # Flattened: arms flung past its head, legs splayed, cheek on the floor.
        k = ramp(t, IMPACT, IMPACT + 3)
        limbs = {'head': mix(limbs['head'], [0, 70, 0], k), 'right_arm': mix(limbs['right_arm'], [-165, 0, 28], k),
                 'left_arm': mix(limbs['left_arm'], [-160, 0, -24], k), 'right_leg': mix(limbs['right_leg'], [4, 0, 12], k),
                 'left_leg': mix(limbs['left_leg'], [-2, 0, -14], k)}
    if t >= GRIP:
        # Hauled up by the head, it claws at the fist and kicks.
        k = ramp(t, GRIP, GRIP + 7)
        limbs = {'head': mix(limbs['head'], [-10, 0, 0], k),
                 'right_arm': mix(limbs['right_arm'], [-150 + 25*math.sin(t*.55), 0, 14], k),
                 'left_arm': mix(limbs['left_arm'], [-140 + 25*math.sin(t*.55 + 2), 0, -14], k),
                 'right_leg': mix(limbs['right_leg'], [20*math.sin(t*.5), 0, 4], k),
                 'left_leg': mix(limbs['left_leg'], [-20*math.sin(t*.5), 0, -4], k)}
    if t >= GRAB:
        # Stretched out, then torn: the top half flails, the legs kick.
        k = ramp(t, GRAB, GRAB + 4)
        limbs['right_arm'] = mix(limbs['right_arm'], [-100 + 45*math.sin(t*.9), 0, 30], k)
        limbs['left_arm'] = mix(limbs['left_arm'], [-80 + 45*math.sin(t*.8 + 1), 0, -30], k)
        limbs['right_leg'] = mix(limbs['right_leg'], [25*math.sin(t*.7), 0, 6], k)
        limbs['left_leg'] = mix(limbs['left_leg'], [-25*math.sin(t*.7), 0, -6], k)
    return limbs


class Flight:
    """A thrown half: through three (tick, point)s along a gentle curve, spinning, then gone."""

    def __init__(self, r0, path, axis, turns_per_tick):
        self.r0, self.path, self.axis, self.spin = r0, [(t, np.array(p, float)) for t, p in path], axis, turns_per_tick

    def at(self, t):
        (t0, p0), (t1, p1), (t2, p2) = self.path
        a0 = (t - t1)*(t - t2)/((t0 - t1)*(t0 - t2))
        a1 = (t - t0)*(t - t2)/((t1 - t0)*(t1 - t2))
        a2 = (t - t0)*(t - t1)/((t2 - t0)*(t2 - t1))
        return a0*p0 + a1*p1 + a2*p2, axis_angle(self.axis, 360*self.spin*(t - t0))@self.r0

    @property
    def gone(self): return self.path[-1][0]


class Scene:
    """Bakes the Guardian and the zombie together, tick by tick, so each can depend on the other."""

    def __init__(self):
        self.poses = {}
        self.legs_flight = self.top_flight = None

    def pinned(self, t):
        """Face down on the floor, squashed under both fists."""
        sq = mix(.5, .8, ramp(t, IMPACT, IMPACT + 8))
        return half(FACE_DOWN, UPPER_MID, [model(PIN)[0], 2*sq, model(PIN)[2]], sq)

    def fill(self, name, t, p=None):
        """A moving target on the zombie for the Guardian's fists."""
        if name in ('smash_r', 'smash_l'):
            back = placed(self.pinned(t), [0, 20, 2])  # the middle of its back
            return back + [-6 if name == 'smash_r' else 6, 8, 0]
        if name == 'head':
            return placed(self.pinned(t), [0, 28, 4]) + [0, 7, 0]
        if name == 'feet':
            return placed(self.body(t, p), FEET)
        raise KeyError(name)

    def body(self, t, p=None):
        """The whole zombie while it is in one piece; p is the Guardian's pose so far."""
        if t < IMPACT:
            return half(heading(t), [0, 0, 0], model(walk(t)))
        if t < GRIP:
            return self.pinned(t)
        # Hauled up by the head in the right fist, dangling, then swung across the chest. The fist
        # takes the head where it lay, and it eases into the fist's middle as it lifts.
        g = grip(self.pose(t) if p is None else p, 'right')
        g = g + (placed(self.pinned(GRIP), HEAD) - grip(self.pose(GRIP), 'right'))*(1 - ramp(t, GRIP, GRIP + 8))
        r = slerp(FACE_DOWN, HANGING, EASE['out'](min(1, max(0, (t - GRIP)/(SHOW - 4 - GRIP)))))
        swing = axis_angle([0, 0, 1], 14*math.sin(t*.3)*ramp(t, GRIP + 4, SHOW)*(1 - ramp(t, GRAB - 14, GRAB - 8)))
        r = slerp(swing@r, ACROSS, ramp(t, GRAB - 12, GRAB))
        return half(r, HEAD, g)

    def pose(self, t):
        key = round(t, 4)
        if key not in self.poses:
            p = guardian(t, self.fill)
            left_arm(p, t, lambda name, at: self.fill(name, at, p))
            self.poses[key] = p
        return self.poses[key]

    def zombie(self, t):
        """The zombie's halves at tick t: (upper, lower)."""
        if t < TEAR:
            b = self.body(t)
            return b, b
        # Torn apart: each half rides its fist, the torn end swinging down, until it is thrown.
        tear = EASE['back'](min(1, (t - TEAR)/8))
        settle = min(1, (t - TEAR)/8)
        up_r = axis_angle([0, 0, 1], -40*tear + 7*math.sin(t*.7)*settle)@ACROSS
        low_r = axis_angle([0, 0, 1], 48*tear + 6*math.sin(t*.8)*settle)@ACROSS
        lower = half(low_r, FEET, grip(self.pose(t), 'left')) if t < TOSS else self.flying(self.legs(), LOWER_MID, t)
        upper = half(up_r, HEAD, grip(self.pose(t), 'right')) if t < HURL else self.flying(self.top(), UPPER_MID, t)
        return upper, lower

    def flying(self, flight, anchor, t):
        at, r = flight.at(t)
        return half(r, anchor, at, shown=t < flight.gone)

    def legs(self):
        """Flung off the Guardian's left and out of the shot, gone before anyone could look."""
        if self.legs_flight is None:
            low = self.zombie(TOSS - .001)[1]
            start = placed(low, LOWER_MID)
            flick = start + (grip(self.pose(TOSS), 'left') - grip(self.pose(TOSS - 1), 'left'))*3
            self.legs_flight = Flight(low['r'], [(TOSS, start), (TOSS + 3, flick), (TOSS + 14, model(LEGS_GONE))], [.3, .2, 1], -.13)
        return self.legs_flight

    def top(self):
        """Hurled hard and flat over the players' heads, gone behind them."""
        if self.top_flight is None:
            up = self.zombie(HURL - .001)[0]
            start = placed(up, UPPER_MID)
            self.top_flight = Flight(up['r'], [(HURL, start), (HURL + 11, model(OVER_PLAYERS)), (HURL + 21, model(GONE))], [1, .15, 0], .16)
        return self.top_flight


# ------------------------------------------------------------------ the camera

def look(eye, target):
    d = np.array(target) - np.array(eye)
    return math.degrees(math.atan2(-d[0], d[2])), -math.degrees(math.atan2(d[1], math.hypot(d[0], d[2])))


def pov_ideal(scene, t):
    """The zombie's eye, and the yaw and pitch that would look straight at the falling Guardian."""
    upper = scene.zombie(t)[0]
    head = json_matrix(zombie_limbs(t)['head'])
    eye = frame(placed(upper, HEAD_PIVOT + head@(np.array(EYE) - HEAD_PIVOT)))
    p = scene.pose(t)
    chest = frame((transforms(p)['torso']@np.array([0, 60, -4, 1]))[:3])
    fists = frame((grip(p, 'right') + grip(p, 'left'))/2)
    return eye, look(eye, mix(chest, fists, ramp(t, IMPACT - 10, IMPACT - 2)))


# When the zombie's view snaps after the Guardian, and how far off each snap lands (yaw, pitch).
POV_FLICKS = [(POV + 5, 2, (1.5, -1)), (POV + 9, 1, (-.8, .6)), (POV + 16, 2, (1.2, 1.2)), (POV + 20, 1, (0, -.8)),
              (POV + 27, 2, (-1.4, .9)), (POV + 31, 1, (.6, 0)), (POV + 35, 2, (-.5, -.6))]


def pov_view(scene, t):
    """The view lags the Guardian and snaps after it; from four ticks out it just follows. Yaws are
    kept as turns from the first aim, so looking back past 180 degrees never swings the long way."""
    first = None
    def aim(tick):
        nonlocal first
        _, (yaw, pitch) = pov_ideal(scene, tick)
        if first is None: first = yaw
        return np.array([first + (yaw - first + 180) % 360 - 180, pitch])
    held = aim(POV)
    for t0, ticks, error in POV_FLICKS:
        if t < t0: break
        target = aim(t0 + ticks) + error
        if t < t0 + ticks:
            held = held + (target - held)*EASE['back']((t - t0)/ticks)
            break
        held = target
    held = held + .5*np.array([math.sin(t*2.1), math.sin(t*2.9 + 1)])  # a hand on the mouse
    direct = ramp(t, IMPACT - 5, IMPACT - 2)
    yaw, pitch = held*(1 - direct) + aim(t)*direct
    return (yaw + 180) % 360 - 180, pitch


def camera(scene, t):
    """(eye, yaw, pitch, fov) in the scene's frame at tick t."""
    if t < STOP:
        # Low and ahead of the zombie as it shuffles out of the dark toward us.
        z = walk(t)
        s = t/STOP
        eye = z + [1.35 - .3*s, .7 + .15*s, 3.3 - .3*s]
        yaw, pitch = look(eye, z + [0, 1.25, 0])
        return eye, yaw, pitch, 55
    if t < POV:
        # Straight down on it, sinking slowly and turning a little as it looks up at us.
        s = (t - STOP)/(POV - STOP)
        eye = MARK + [0, 7 - 1.6*smooth(s), 0]
        return eye, 180 - 16*smooth(s), 90, 50
    if t < IMPACT:
        # Its eyes: up into the vaults, where the Guardian comes down on it. The view chases it the
        # way a player's mouse would, lagging and catching up in flicks, until the last moment.
        eye, _ = pov_ideal(scene, t)
        yaw, pitch = pov_view(scene, t)
        return eye, yaw, pitch, float(22 + 54*ramp(t, IMPACT - 8, IMPACT)**2)  # tight on it all the way down, flaring as it hits
    if t < SHOW:
        # Low in front as the dust clears, tilting up as it hauls the zombie up. The smash's
        # shockwave hits the lens: a hard drop, a few bounces dying away, and a punch of the lens.
        s = ramp(t, IMPACT, GRIP)
        eye = mix([2.4, 1.3, 9.2], [1.7, 1.45, 7.9], s)  # above the shockwave's ridge (.75 high) as it rolls under
        target = mix([0, 1.2, 1.9], [-.4, 3.6, 1.5], ramp(t, GRIP, SHOW))
        yaw, pitch = look(eye, target)
        k = t - IMPACT
        jolt = math.exp(-k/6)
        eye = eye + [.1*jolt*math.sin(k*1.1), -.32*jolt*math.cos(k*.95), .08*jolt*math.sin(k*.8)]
        return eye, yaw, pitch, 55 + 11*math.exp(-k/4)
    if t < PLAYERS:
        # In close at its height: the zombie held up to its face, torn apart, and the top half
        # coming straight at us.
        drift = ramp(t, SHOW, HURL)
        eye = SHOT5_EYE + [-.2*drift, -.1*drift, -.35*drift]
        up, low = scene.zombie(min(t, TOSS - 1))
        zombie = frame((placed(up, UPPER_MID) + placed(low, LOWER_MID))/2)
        head = frame((transforms(scene.pose(t))['head']@np.array([0, 70, -12, 1]))[:3])
        target = mix(mix(zombie, head, .15), [-.3, 3.6, 1.3], ramp(t, TOSS, HURL - 4))
        flinch = ramp(t, HURL + 1, HURL + 3)
        eye = eye + flinch*np.array([-.25, -.3, .3])
        yaw, pitch = look(eye, target)
        return eye, yaw - 8*flinch, pitch + 8*flinch, 50
    # From the players' line: the camera tilts up after the top half as it comes over our heads,
    # then settles on the Guardian and closes in as it points at us.
    s = ramp(t, PLAYERS, LENGTH)
    eye = SHOT6_EYE + [.3*s, .15*s, -1.2*s]
    duck = ramp(t, HURL + 7, HURL + 10)*(1 - ramp(t, HURL + 13, HURL + 22))
    eye = eye + duck*np.array([0, -.3, .15])
    target = mix([-.2, 3.2, 0], [0, 3.55, 0], ramp(t, POINT, POINT + 20))
    over = scene.top().path[1][0]
    follow = ramp(t, PLAYERS, PLAYERS + 3)*(1 - ramp(t, over + 2, over + 12))
    if follow > 0:
        half = frame(placed(scene.zombie(min(t, over + 2))[0], UPPER_MID))
        half[2] = min(half[2], eye[2] - 2.5)  # keep looking ahead and up, never round behind
        target = mix(target, half, follow)
    yaw, pitch = look(eye, target)
    fov = mix(40, 17, ramp(t, POINT, POINT + 26))
    return eye, yaw, pitch, float(fov)


def flash(t):
    """The screen whites out as the fists land."""
    return 0 if t < IMPACT else math.exp(-(t - IMPACT)/4)


def black(t):
    return 1 - smooth(t/16)


# ------------------------------------------------------------------ baking

def continuous(prev, cur):
    """Picks the Euler triple for cur that interpolates smoothly from prev."""
    if prev is None: return cur
    best = None
    for c in (np.array(cur), np.array([cur[0] + 180, 180 - cur[1], cur[2] + 180])):
        c = prev + (c - prev + 180) % 360 - 180
        if best is None or np.abs(c - prev).sum() < np.abs(best - prev).sum(): best = c
    return best


def zombie_channels(scene, t):
    """GeckoLib channels for every zombie bone at tick t."""
    up, low = scene.zombie(t)
    out = {}
    for name, h in (('upper', up), ('lower', low)):
        out[name] = {'rotation': np.array(euler(h['r'])), 'position': h['position'], 'scale': h['scale']}
    for bone, value in zombie_limbs(t).items():
        out[bone] = {'rotation': np.array(value, dtype=float)}
    return out


def bake(sample, length, precise):
    clip = {'loop': False, 'animation_length': length/20, 'bones': {}}
    last = {}
    for i in range(round(length/20*RATE) + 1):
        seconds = round(i/RATE, 4)
        for bone, channels in sample(seconds*20).items():
            for kind, value in channels.items():
                if kind == 'rotation':
                    value = continuous(last.get(bone), np.array(value, dtype=float)); last[bone] = value
                clip['bones'].setdefault(bone, {}).setdefault(kind, {})[str(seconds)] = [round(float(v), 5) for v in value]
    for bone, channels in clip['bones'].items():
        for kind, keyframes in list(channels.items()):
            channels[kind] = rig['reduce_keys'](keyframes, .0015 if precise(bone, kind) else .012)
    return clip


def guardian_precise(bone, kind):
    return kind != 'rotation' or bone in ('head', 'torso', 'root') or any(s in bone for s in ('thigh', 'shin', 'foot', 'arm'))


def bake_track(scene):
    ticks = []
    for t in range(LENGTH + 1):
        eye, yaw, pitch, fov = camera(scene, t)
        up, low = scene.zombie(t)
        p = scene.pose(t)
        ticks.append({'eye': [round(float(v), 4) for v in eye], 'yaw': round(float(yaw), 3), 'pitch': round(float(pitch), 3),
                      'fov': round(float(fov), 2), 'flash': round(flash(t), 4),
                      'black': round(black(t), 4),
                      'upper': [round(float(v), 3) for v in frame(placed(up, UPPER_MID))],
                      'lower': [round(float(v), 3) for v in frame(placed(low, LOWER_MID))],
                      'fists': [round(float(v), 3) for v in frame((grip(p, 'right') + grip(p, 'left'))/2)]})
    columns = {k: [tick[k] for tick in ticks] for k in ticks[0]}
    events = {'legs_gone': scene.legs().gone, 'over_players': scene.top().path[1][0], 'half_gone': scene.top().gone}
    return {'beats': BEATS, 'events': events, 'cuts': CUTS, 'ticks': columns}


def build():
    scene = Scene()
    worst = 99
    for i in range(IMPACT*2, LENGTH*2 + 1):  # the whole performance stays above the floor once landed
        worst = min(worst, lowest(scene.pose(i/2)))
    guardian_clip = bake(lambda t: scene.pose(t), LENGTH, guardian_precise)
    zombie_clip = bake(lambda t: zombie_channels(scene, t), LENGTH, lambda b, k: True)
    return scene, guardian_clip, zombie_clip, bake_track(scene), worst


def dump(data): return json.dumps(data, indent=2) + '\n'


def blockbench(clip):
    """The Guardian with its intro clip as an editable Blockbench project. The zombie's geometry
    and clip open in Blockbench directly (File > Open, then Animate > Import Animations)."""
    project = rig['legacy']['blockbench']({'animations': {'animation.fractured_guardian.intro': clip}})
    project['name'] = 'Fractured Guardian — Intro'
    return project


def outputs():
    scene, guardian_clip, zombie_clip, track_data, worst = build()
    assert worst > -1.0, f'the performance sinks {worst:.2f} px into the floor'
    geometry = dump(zombie_geometry())
    clip = dump({'format_version': '1.8.0', 'animations': {'animation.guardian_intro_zombie.intro': zombie_clip}})
    track_text = json.dumps(track_data, separators=(',', ':')) + '\n'
    files = {
        HERE/'intro.animation.json': dump({'format_version': '1.8.0', 'animations': {'animation.fractured_guardian.intro': guardian_clip}}),
        HERE/'intro.bbmodel': dump(blockbench(guardian_clip)),
        ZOMBIE/'guardian_intro_zombie.geo.json': geometry,
        ZOMBIE/'guardian_intro_zombie.animation.json': clip,
        HERE/'intro-track.json': track_text,
    }
    runtime = {
        ASSETS/'geckolib/models/guardian_intro_zombie.geo.json': geometry,
        ASSETS/'geckolib/animations/guardian_intro_zombie.animation.json': clip,
        ASSETS/'cinematics/guardian_intro.json': track_text,
    }
    return scene, files, runtime, worst


def review(scene):
    def still(t):
        p = {k: dict(v) for k, v in scene.pose(t).items()}
        p['root'] = {'position': np.zeros(3), 'rotation': np.zeros(3)}
        return p
    frames = [(f'{label} / tick {t}', still(t)) for label, t in
              [('falling', IMPACT - 8), ('wound up', IMPACT - 3), ('smash', IMPACT + 2), ('roar', IMPACT + 22),
               ('hauling', SHOW - 4), ('studying', SHOW + 10), ('bracing', TEAR - 1), ('ripped', TEAR + 8),
               ('fling', TOSS + 4), ('wound up', HURL - 4), ('hurled', HURL + 2), ('pointing', POINT + 22)]]
    sheet(frames, HERE/'intro-review.png', views=((-35, 8), (0, 2), (90, 0)), size=300, centre=36, span=125)


def animatic():
    """Copies what the browser animatic loads into its local preview folder."""
    PREVIEW.mkdir(parents=True, exist_ok=True)
    copies = {
        ASSETS/'geckolib/models/fractured_guardian.geo.json': 'fractured_guardian.geo.json',
        ASSETS/'textures/entity/fractured_guardian.png': 'fractured_guardian.png',
        ASSETS/'textures/entity/fractured_guardian_glowmask.png': 'fractured_guardian_glowmask.png',
        HERE/'intro.animation.json': 'fractured_guardian.animation.json',
        ZOMBIE/'guardian_intro_zombie.geo.json': 'intro_zombie.geo.json',
        ZOMBIE/'guardian_intro_zombie.animation.json': 'intro_zombie.animation.json',
        HERE/'intro-track.json': 'intro-track.json',
        HERE/'animatic.js': 'animatic.js',
        HERE/'viewer.template.html': 'index.html',
    }
    for source, name in copies.items():
        if source.exists(): shutil.copyfile(source, PREVIEW/name)
        else: print(f'animatic: missing {source.relative_to(REPO)}')
    # The game's own zombie skin, for this local preview only.
    if MINECRAFT.exists():
        with zipfile.ZipFile(MINECRAFT) as jar:
            (PREVIEW/'intro_zombie.png').write_bytes(jar.read('assets/minecraft/textures/entity/zombie/zombie.png'))
    else: print(f'animatic: no {MINECRAFT} for the zombie skin')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    parser.add_argument('--install', action='store_true', help='also write the runtime zombie and track')
    parser.add_argument('--check', action='store_true', help='fail if any output differs from a fresh bake')
    args = parser.parse_args()
    scene, files, runtime, worst = outputs()
    if args.check:
        stale = [p for p, text in {**files, **runtime}.items() if not p.exists() or p.read_text() != text]
        for p in stale: print(f'stale: {p.relative_to(REPO)}')
        sys.exit(1 if stale else 0)
    for path, text in files.items():
        path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text)
    if args.install:
        for path, text in runtime.items():
            path.parent.mkdir(parents=True, exist_ok=True); path.write_text(text)
    review(scene)
    animatic()
    print(f'Intro baked: {LENGTH/20:.1f} s, lowest landed vertex {worst:.2f} px; the legs are gone at tick '
          f'{scene.legs().gone}, the top half passes over the players at {scene.top().path[1][0]}.')
