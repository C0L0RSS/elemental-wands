#!/usr/bin/env python3
"""The First Wizard's intro: the scene's script, baked for the game.

The developer's decisions (Nov 17-18, docs/design/first-wizard.md, "The intro"): one scene for the kill
and the transformation, long and unhurried. A pool of living ink hangs over the End's island with the
Ender Dragon circling under it. The dragon turns on the pool and breathes into it; a patch of violet
blooms under the surface and hangs there. The pool sends the same breath back, stretched into a stream,
catches the dragon in flight, and it dies of its own magic. Quiet. Then one edge of the pool comes down
to the floor in front of the players and pours itself into the first player's shape, boots first, while
the mass overhead drains away; the last strand pulls into its wand hand, it raises its head, and the
fight begins. The dragon dies once per world: later attempts play only the condensing ('short').

This script owns the whole scene on one clock (20 ticks a second), for both versions:
  - the camera, one shot after another, with hard cuts, and the fade in;
  - the dragon (the vanilla model, drawn by each client as an actor): where it is, which way it faces,
    its extra nose pitch and roll, its wing beat, when it is struck and how far its death has run;
  - the breath into the pool, the bloom, the stream back and what is left of the dragon;
  - the pool's size and where it drains to, the edge coming down, the pour, how far the copy has formed,
    when it raises its head, and its name.

Points are relative to the End arena's centre: +X east, +Z south, y up from the fight floor's top (so a
point (x, y, z) here is the world's (0.5 + x, 64 + y, 0.5 + z)). The players arrive at (0, 0, 42) facing
north; the copy forms at FORM, between them and the centre, under the pool's south edge.

  py -3 art/first_wizard/intro/build_intro.py        writes intro-track.json beside this file
  ... --install                                      also writes the game's copy
  ... --check                                        fails if either copy is stale
"""
import argparse
import json
import math
from pathlib import Path

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
TRACK = HERE/'intro-track.json'
RUNTIME = ROOT/'src/main/resources/assets/elementalwands/cinematics/first_wizard_intro.json'
RATE = 20

# ---------------------------------------------------------------- where things are
ARRIVAL = (0., 0., 42.)                 # in step with EndArena.ARRIVAL
EYE = 1.62
FORM = (0., 0., 24.)                    # where the copy forms: under the pool's south edge, 18 blocks in front of the players
POOL_LEVEL, POOL_R, POOL_DEPTH = 38., 26., 6.5   # the pool's middle height, radius, and how far its belly hangs below the rim


def underside(r, radius=POOL_R):
    """The pool's underside at r from its middle: a shallow belly, deepest in the middle."""
    k = min(1., (r/radius)**2) if radius > 0 else 1.
    return POOL_LEVEL-1-POOL_DEPTH*(1-k)*(radius/POOL_R)**.5


BLOOM = (0., round(underside(6.)-.2, 3), 6.)    # where the breath strikes the pool, and where the stream back leaves it

# ---------------------------------------------------------------- the clock (ticks), the full scene
B = {
    'island': 0, 'arrive': 130, 'under': 240,
    'turn': 360, 'hover': 440, 'breath': 450, 'breath_end': 540, 'bloom': 460, 'bloom_full': 560,
    'flee': 615, 'back': 630, 'catch': 648, 'death': 680, 'burst': 860, 'quiet': 940,
    'sag': 1030, 'touch': 1110, 'form': 1120, 'drain_end': 1410, 'strand': 1395, 'formed': 1425,
    'raise': 1430, 'raised': 1480, 'title': 1490, 'title_end': 1590, 'return': 1590, 'length': 1650,
}
HITS = [648, 668, 692, 718, 746, 776]
CONDENSING = ('sag', 'touch', 'form', 'drain_end', 'strand', 'formed', 'raise', 'raised', 'title', 'title_end', 'return', 'length')
SHORT_SAG = 150                             # the short scene: the pool waiting overhead, then the same condensing


def short_beats():
    shift = SHORT_SAG-B['sag']
    out = {'waiting': 0}
    for k in CONDENSING: out[k] = B[k]+shift
    return out


EASE = {'io': lambda f: f*f*(3-2*f), 'lin': lambda f: f, 'out': lambda f: 1-(1-f)**2, 'in': lambda f: f*f,
        'slow': lambda f: f*f*f*(f*(6*f-15)+10), 'in3': lambda f: f*f*f}


def key(t, rows):
    """rows: (tick, value[, ease]); the ease names how the move arrives at that row. Values are numbers or tuples."""
    def lerp(a, b, f):
        if isinstance(a, tuple): return tuple(x+(y-x)*f for x, y in zip(a, b))
        return a+(b-a)*f
    if t <= rows[0][0]: return rows[0][1]
    for a, b in zip(rows, rows[1:]):
        if t <= b[0]:
            f = (t-a[0])/(b[0]-a[0])
            return lerp(a[1], b[1], EASE[b[2] if len(b) > 2 else 'io'](f))
    return rows[-1][1]


def clamp(v, lo=0., hi=1.): return max(lo, min(hi, v))
def add(a, b): return tuple(x+y for x, y in zip(a, b))
def sub(a, b): return tuple(x-y for x, y in zip(a, b))
def mul(a, k): return tuple(x*k for x in a)
def length(a): return math.sqrt(sum(x*x for x in a))
def mix(a, b, f): return tuple(x+(y-x)*f for x, y in zip(a, b))


# ---------------------------------------------------------------- the dragon
CIRCLE_C, CIRCLE_R, CIRCLE_Y, OMEGA, PHASE = (0., -2.), 23., 23., -.04, math.pi+.04*360
HOVER = (0., 13., 21.)                      # well under the pool's south side, facing north up into it
CAUGHT = (17., 12., 9.)                     # where the stream catches it, fleeing east


def circling(t):
    th = PHASE+OMEGA*t
    return (CIRCLE_C[0]+CIRCLE_R*math.cos(th), CIRCLE_Y+1.3*math.sin(t*.045), CIRCLE_C[1]+CIRCLE_R*math.sin(th))


def circling_v(t):
    th = PHASE+OMEGA*t
    return (-CIRCLE_R*math.sin(th)*OMEGA, 1.3*.045*math.cos(t*.045), CIRCLE_R*math.cos(th)*OMEGA)


def hermite(p0, v0, p1, v1, s, span):
    h00, h10, h01, h11 = 2*s**3-3*s**2+1, s**3-2*s**2+s, -2*s**3+3*s**2, s**3-s**2
    return tuple(h00*a+h10*span*b+h01*c+h11*span*d for a, b, c, d in zip(p0, v0, p1, v1))


def hover(t): return add(HOVER, (.35*math.sin(t*.11), .55*math.sin(t*.08), 0))


FLEE_V = (.75, -.1, -.35)


def dragon_pos(t):
    """Where the dragon's body is (the vanilla entity's position), until it bursts."""
    if t <= B['turn']: return circling(t)
    if t <= B['hover']:
        s = (t-B['turn'])/(B['hover']-B['turn'])
        return hermite(circling(B['turn']), circling_v(B['turn']), hover(B['hover']), (0, .03, -.06), s, B['hover']-B['turn'])
    if t <= B['flee']: return hover(t)
    if t <= B['catch']:
        s = (t-B['flee'])/(B['catch']-B['flee'])
        return hermite(hover(B['flee']), (0, .02, 0), CAUGHT, FLEE_V, s, B['catch']-B['flee'])
    # Caught: it skids on a little, then the stream lifts it toward the pool and holds it there, shaking.
    tau = t-B['catch']
    drift = mul(FLEE_V, 9*(1-math.exp(-tau/9)))
    lift = EASE['io'](clamp(tau/(B['burst']-B['catch'])))
    pulled = mix((0, 0, 0), sub(BLOOM, CAUGHT), .22*lift)
    shake = (.25*math.sin(tau*1.7), .2*math.sin(tau*2.3), .25*math.sin(tau*1.3))
    return add(add(add(CAUGHT, drift), add(pulled, (0, 5*lift, 0))), mul(shake, clamp(tau/20)))


def dragon_velocity(t):
    a, b = dragon_pos(max(0, t-1)), dragon_pos(t+1)
    return mul(sub(b, a), .5)


def yaw_of(v):
    """The dragon faces (sin yaw, -cos yaw) on the ground plane: vanilla's head offset."""
    return math.degrees(math.atan2(v[0], -v[2]))


def unwrap(prev, cur):
    while cur-prev > 180: cur -= 360
    while cur-prev < -180: cur += 360
    return cur


def dragon_turn(t, prev_yaw):
    """Yaw, extra nose pitch (degrees, up), roll (degrees, right wing down)."""
    v = dragon_velocity(t)
    yaw = yaw_of(v) if math.hypot(v[0], v[2]) > .05 else prev_yaw
    pitch, roll = 0., 0.
    if t <= B['turn']:
        roll = -18.                                     # banked into the turn
    elif t <= B['hover']:
        s = (t-B['turn'])/(B['hover']-B['turn'])
        roll = -18*(1-EASE['io'](s)); pitch = 30*EASE['in'](s)
    elif t <= B['flee']:
        yaw = 0.
        pitch = float(key(t, [(B['hover'], 30.), (B['breath'], 38.), (B['breath_end'], 38.), (B['breath_end']+20, 18.), (B['flee'], 14.)]))
    elif t <= B['catch']:
        s = (t-B['flee'])/(B['catch']-B['flee'])
        yaw = mix((0.,), (yaw_of(FLEE_V),), EASE['io'](s))[0]
        pitch = 14*(1-s); roll = 25*math.sin(math.pi*s)
    else:
        tau = t-B['catch']
        k = clamp(tau/12)
        yaw = yaw_of(FLEE_V)+k*(32*math.sin(tau*.19)+12*math.sin(tau*.47))
        pitch = k*(22*math.sin(tau*.13)+20)
        roll = k*(28*math.sin(tau*.23)+10*math.sin(tau*.61))
    return unwrap(prev_yaw, yaw), pitch, roll


def wing_rate(t):
    """Wing beats a tick (vanilla's wingPosition, one beat per 1)."""
    if t <= B['turn']: return .028
    if t <= B['hover']: return float(key(t, [(B['turn'], .028), (B['hover'], .085)]))
    if t <= B['flee']: return .085
    if t <= B['catch']: return .07
    return float(key(t, [(B['catch'], .16), (B['death']+40, .12), (B['burst'], .045, 'lin')]))


def hurt(t):
    """vanilla hurtTime: 10 when struck, falling a tick at a time (the red flash)."""
    return max([0]+[10-(t-h) for h in HITS if 0 <= t-h < 10])


def death(t):
    """vanilla ticksSinceDeath: the rays and the dissolve, 0 to 200, run over the time it is held."""
    if t < B['death']: return 0.
    return min(DEATH_MAX, DEATH_MAX*(t-B['death'])/(B['burst']-B['death']))


DEATH_MAX = 150.                            # of vanilla's 200: its rays at full length would fill the sky


def mouth(pos, yaw, pitch):
    """Where its breath leaves its jaws."""
    y, p = math.radians(yaw), math.radians(pitch)
    f = (math.sin(y)*math.cos(p), math.sin(p), -math.cos(y)*math.cos(p))
    return add(add(pos, mul(f, 10.2)), (0, .6, 0))


# ---------------------------------------------------------------- the pool and the condensing (any scene, by its own beats)
def condensing(t, b):
    """The pool's radius and where its middle drifts (it drains toward the edge that comes down), the edge's fall,
    the pour, the last strand, how far the copy has formed and how far it has raised its head."""
    drain = EASE['io'](clamp((t-b['form'])/(b['drain_end']-b['form'])))
    r = POOL_R*(1-drain)
    cz = (FORM[2]-1.5)*drain
    drape = EASE['in3'](clamp((t-b['sag'])/(b['touch']-b['sag'])))
    flow = 0. if t < b['touch'] else float(key(t, [(b['touch'], 1.), (b['strand'], 1.), (b['formed'], 0., 'lin')]))
    top = EASE['in'](clamp((t-b['strand'])/(b['formed']-b['strand'])))
    s = b['form']
    formed = float(key(t, [(s, 0.), (s+80, .3, 'io'), (s+150, .55, 'io'), (s+220, .75, 'io'), (s+270, .95, 'io'), (b['formed'], 1., 'lin')]))
    raise_ = EASE['io'](clamp((t-b['raise'])/(b['raised']-b['raise'])))
    return r, cz, drape, flow, top, formed, raise_


# ---------------------------------------------------------------- the camera
def look_at(eye, target):
    d = sub(target, eye)
    return math.degrees(math.atan2(-d[0], d[2])), -math.degrees(math.atan2(d[1], math.hypot(d[0], d[2])))


def ahead(t, lead=0.): return dragon_pos(t+lead)


def full_shots():
    b = B
    head = (FORM[0], 1.62, FORM[2])
    return [
        # The island from far off, the ink hanging over it like a hole in the sky, the dragon a speck circling under it.
        (b['island'], 'the island', [(0, (-50., 14., 74.)), (130, (-43., 13., 64.), 'lin')], [(0, (0., 26., 0.)), (130, (0., 27., 0.), 'lin')], [(0, 62.)]),
        # Behind the players at the arrival, tilting up from the floor to the pool; the dragon crosses overhead.
        (b['arrive'], 'the arrival', [(130, (1.6, 2.6, 48.5)), (240, (1.3, 2.2, 46.5), 'lin')],
         [(130, (0., 8., 14.)), (240, (0., 30., 2.), 'io')], [(130, 70.)]),
        # Under the belly of the pool, the dragon passing low and close.
        (b['under'], 'under the pool', [(240, (-17., 1.6, 13.)), (360, (-14., 1.8, 8.), 'lin')], [(240, (4., 30., -4.)), (360, (2., 31., -6.), 'lin')], [(240, 68.)]),
        # It breaks from its circle and climbs to the pool.
        (b['turn'], 'it turns', [(360, (30., 7., 38.)), (470, (27., 8., 34.), 'lin')], [(360, (-2., 20., 10.)), (470, (0., 22., 13.), 'lin')], [(360, 60.)]),
        # It breathes into the ink; a patch of violet spreads under the surface.
        (470, 'the breath', [(470, (21., 12., 31.)), (560, (19., 13., 29.), 'lin')], [(470, (0., 23., 13.)), (560, (0., 24., 12.), 'lin')], [(470, 54.)]),
        # The violet hangs there; the dragon waits under it.
        (560, 'the bloom', [(560, (13., 3., 40.)), (b['flee'], (12.4, 3.3, 38.5)), (b['back']+2, (12.2, 3.4, 38.), 'lin')],
         [(560, add(BLOOM, (0, -6., 5.)))], [(560, 50.)]),
        # The pool sends the breath back and catches it in flight: wide, from below and behind its flight.
        (b['back']+2, 'the breath comes back', [(b['back']+2, (-22., 4., 40.)), (730, (-20., 5., 37.), 'lin')],
         [(b['back']+2, (6., 20., 10.)), (730, (10., 21., 8.), 'out')], [(b['back']+2, 64.)]),
        # Close: it is held in its own breath and dies of it.
        (730, 'caught', [(730, add(ahead(730), (16., -6., 26.))), (b['burst']-14, add(ahead(b['burst']-14), (13., -5., 22.)), 'lin')],
         [(730, add(ahead(730), (0, 1., 0))), (b['burst']-14, add(ahead(b['burst']-14), (0, 1., 0)), 'lin')], [(730, 48.)]),
        # From the players: it bursts, and what was left of it is drawn up into the ink.
        (b['burst']-14, 'it is gone', [(b['burst']-14, (1.2, 2.0, 40.5)), (b['quiet'], (1., 2., 40.), 'lin')],
         [(b['burst']-14, add(ahead(b['burst']-14), (0, 2., 0))), (b['quiet'], add(BLOOM, (0, -2., 2.)), 'io')], [(b['burst']-14, 64.)]),
    ]+condensing_shots(b, head)


def condensing_shots(b, head):
    f = FORM
    return [
        # Quiet: the sky and the pool, nothing moving but the ink.
        (b['quiet'] if 'quiet' in b else b['waiting'], 'quiet', [(0, (11., 1.3, 47.)), (100000, (11., 1.3, 47.))], [(0, (0., 30., 0.))], [(0, 66.)]),
        # One edge of the pool comes down: the camera follows it from the sky to the floor.
        (b['sag'], 'the edge comes down', [(b['sag'], (12., 2.4, 52.)), (b['touch']+20, (10.5, 2.0, 49.), 'lin')],
         [(b['sag'], (0., 26., 20.)), (b['sag']+40, (0., 24., 21.)), (b['touch'], (0., 6., 24.), 'in'), (b['touch']+20, (0., 4., 24.))], [(b['sag'], 64.)]),
        # Close on the floor: it pours into a pool, and boots rise out of it.
        (b['touch']+20, 'boots', [(b['touch']+20, (2.9, .9, f[2]+5.2)), (b['form']+110, (2.6, 1.1, f[2]+4.6), 'lin')],
         [(b['touch']+20, (0., .45, f[2])), (b['form']+110, (0., .8, f[2]), 'lin')], [(b['touch']+20, 42.)]),
        # Wide from behind the players: the sky drains into the figure.
        (b['form']+110, 'the sky drains', [(b['form']+110, (-.8, 2.4, 50.)), (b['form']+200, (-.6, 2.2, 48.), 'lin')],
         [(b['form']+110, (0., 21., 16.)), (b['form']+200, (0., 19., 17.), 'lin')], [(b['form']+110, 68.)]),
        # Its body, its arms, its head; the last strand into its wand hand.
        (b['form']+200, 'the last strand', [(b['form']+200, (-2.8, 1.8, f[2]+5.6)), (b['raise'], (-2.3, 1.75, f[2]+4.6), 'lin')],
         [(b['form']+200, (0., 1.1, f[2])), (b['raise'], (-.2, 1.25, f[2]), 'lin')], [(b['form']+200, 44.)]),
        # It raises its head: the player's own face.
        (b['raise'], 'its face', [(b['raise'], (.5, 1.62, f[2]+3.)), (b['title']+10, (.7, 1.5, f[2]+4.4), 'lin')],
         [(b['raise'], add(head, (0, -.15, 0))), (b['title']+10, add(head, (0, .05, 0)), 'lin')], [(b['raise'], 38.)]),
        # Below it, looking up: the sky above it is empty. Its name.
        (b['title']+10, 'the empty sky', [(b['title']+10, (1.8, .35, f[2]+5.)), (b['length'], (1.6, .45, f[2]+5.6), 'lin')],
         [(b['title']+10, (0., 3.6, f[2]-4.)), (b['length'], (0., 3.2, f[2]-4.), 'lin')], [(b['title']+10, 62.)]),
    ]


def short_shots():
    b = short_beats()
    head = (FORM[0], 1.62, FORM[2])
    rows = condensing_shots(b, head)
    # The pool waits overhead: behind the players, looking up at it, then under its belly.
    waiting = (0, 'waiting', [(0, (1.6, 2.6, 48.5)), (SHORT_SAG, (1.2, 2.2, 45.5), 'lin')], [(0, (0., 14., 10.)), (SHORT_SAG, (0., 30., 2.), 'io')], [(0, 70.)])
    return [waiting]+rows[1:]


def camera(t, shots):
    shot = [s for s in shots if s[0] <= t][-1]
    eye, look = key(t, shot[2]), key(t, shot[3])
    yaw, pitch = look_at(eye, look)
    return eye, yaw, pitch, float(key(t, shot[4])), shot


# ---------------------------------------------------------------- baking
def r3(v): return round(float(v), 3)
def r2(v): return round(float(v), 2)
def v3(p): return [r3(x) for x in p]


def bake_full():
    shots = full_shots()
    n = B['length']
    ticks = {k: [] for k in ('eye', 'yaw', 'pitch', 'fov', 'black', 'title', 'dragon', 'd_yaw', 'd_pitch', 'd_roll', 'wing', 'hurt',
                             'death', 'shown', 'mouth', 'breath', 'bloom', 'back', 'target', 'remains',
                             'pool_r', 'pool_cz', 'drape', 'flow', 'top', 'formed', 'raise')}
    wing, d_yaw = 0., yaw_of(circling_v(0))
    last_yaw = None
    for t in range(n+1):
        eye, yaw, pitch, fov, _ = camera(t, shots)
        if last_yaw is not None: yaw = unwrap(last_yaw, yaw)
        last_yaw = yaw
        ticks['eye'].append(v3(eye)); ticks['yaw'].append(r2(yaw)); ticks['pitch'].append(r2(pitch)); ticks['fov'].append(r2(fov))
        ticks['black'].append(r3(key(t, [(0, 1.), (50, 0., 'out')])))
        ticks['title'].append(r3(title(t, B)))
        shown = t <= B['burst']+1
        at = dragon_pos(min(t, B['burst']))
        d_yaw, dp, dr = dragon_turn(min(t, B['burst']), d_yaw)
        wing += wing_rate(t)
        ticks['dragon'].append(v3(at)); ticks['d_yaw'].append(r2(d_yaw)); ticks['d_pitch'].append(r2(dp)); ticks['d_roll'].append(r2(dr))
        ticks['wing'].append(r3(wing)); ticks['hurt'].append(hurt(t)); ticks['death'].append(r2(death(t))); ticks['shown'].append(1 if shown else 0)
        ticks['mouth'].append(v3(mouth(at, d_yaw, dp)))
        ticks['breath'].append(r3(key(t, [(B['breath'], 0.), (B['breath']+12, 1., 'out'), (B['breath_end']-14, 1.), (B['breath_end'], 0., 'lin')])))
        ticks['bloom'].append(r3(key(t, [(B['bloom'], 0.), (B['bloom_full'], 1., 'out'), (B['back'], 1.), (B['back']+10, 1.25, 'out'), (B['burst'], 1.15),
                                         (B['burst']+30, 1.4, 'out'), (B['quiet']+40, 0., 'io')])))
        ticks['back'].append(r3(key(t, [(B['back'], 0.), (B['catch'], 1., 'in'), (B['burst'], 1.), (B['burst']+26, 0., 'in')])))
        chest = dragon_pos(min(max(t, B['catch']), B['burst']))
        ticks['target'].append(v3(add(chest, (0, 1.2, 0))))
        ticks['remains'].append(r3(clamp((t-B['burst'])/80) if t >= B['burst'] else 0.))
        r, cz, drape, flow, top, formed, raise_ = condensing(t, B)
        ticks['pool_r'].append(r3(r)); ticks['pool_cz'].append(r3(cz)); ticks['drape'].append(r3(drape)); ticks['flow'].append(r3(flow))
        ticks['top'].append(r3(top)); ticks['formed'].append(r3(formed)); ticks['raise'].append(r3(raise_))
    return scene(B, shots, ticks, HITS)


def bake_short():
    b = short_beats()
    shots = short_shots()
    n = b['length']
    ticks = {k: [] for k in ('eye', 'yaw', 'pitch', 'fov', 'black', 'title', 'pool_r', 'pool_cz', 'drape', 'flow', 'top', 'formed', 'raise')}
    last_yaw = None
    for t in range(n+1):
        eye, yaw, pitch, fov, _ = camera(t, shots)
        if last_yaw is not None: yaw = unwrap(last_yaw, yaw)
        last_yaw = yaw
        ticks['eye'].append(v3(eye)); ticks['yaw'].append(r2(yaw)); ticks['pitch'].append(r2(pitch)); ticks['fov'].append(r2(fov))
        ticks['black'].append(r3(key(t, [(0, 1.), (40, 0., 'out')])))
        ticks['title'].append(r3(title(t, b)))
        r, cz, drape, flow, top, formed, raise_ = condensing(t, b)
        ticks['pool_r'].append(r3(r)); ticks['pool_cz'].append(r3(cz)); ticks['drape'].append(r3(drape)); ticks['flow'].append(r3(flow))
        ticks['top'].append(r3(top)); ticks['formed'].append(r3(formed)); ticks['raise'].append(r3(raise_))
    return scene(b, shots, ticks, [])


def title(t, b):
    return clamp((t-b['title'])/14)*(1-clamp((t-(b['title_end']-14))/14))


def scene(beats, shots, ticks, hits):
    cuts = sorted({s[0] for s in shots if s[0] > 0})
    return {'beats': beats, 'cuts': cuts, 'shots': {s[1]: s[0] for s in shots}, 'hits': hits, 'ticks': ticks}


def bake():
    return {
        'frame': 'relative to the End arena centre: +X east, +Z south, y up from the floor top; world = (0.5 + x, 64 + y, 0.5 + z)',
        'form': list(FORM), 'arrival': list(ARRIVAL), 'bloom': list(BLOOM),
        'pool': {'level': POOL_LEVEL, 'radius': POOL_R, 'depth': POOL_DEPTH},
        'full': bake_full(), 'short': bake_short(),
    }


def packed(track):
    def scene_rows(s):
        head = ',\n'.join('    %s: %s' % (json.dumps(k), json.dumps(v, separators=(',', ':'))) for k, v in s.items() if k != 'ticks')
        ticks = ',\n'.join('      %s: %s' % (json.dumps(k), json.dumps(v, separators=(',', ':'))) for k, v in s['ticks'].items())
        return '{\n'+head+',\n    "ticks": {\n'+ticks+'\n    }\n  }'
    rows = []
    for k, v in track.items():
        rows.append('  %s: %s' % (json.dumps(k), scene_rows(v) if k in ('full', 'short') else json.dumps(v, separators=(',', ':'))))
    return ('{\n'+',\n'.join(rows)+'\n}\n').encode()


def main():
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument('--install', action='store_true'); ap.add_argument('--check', action='store_true')
    args = ap.parse_args()
    track = bake(); data = packed(track)
    targets = [TRACK]+([RUNTIME] if args.install or args.check else [])
    if args.check:
        stale = [str(p.relative_to(ROOT)) for p in targets if not p.exists() or p.read_bytes() != data]
        if stale: raise SystemExit('Stale or missing: '+', '.join(stale))
    else:
        for p in targets: p.parent.mkdir(parents=True, exist_ok=True); p.write_bytes(data)
    full, short = track['full'], track['short']
    print(('CHECK OK' if args.check else 'INSTALLED' if args.install else 'BUILT')
          + f": full {full['beats']['length']} ticks ({full['beats']['length']/RATE:.1f} s, {len(full['cuts'])+1} shots),"
          + f" short {short['beats']['length']} ticks ({short['beats']['length']/RATE:.1f} s, {len(short['cuts'])+1} shots)")


if __name__ == '__main__':
    main()
