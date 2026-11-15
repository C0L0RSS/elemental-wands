#!/usr/bin/env python3
"""The Pyre King's intro: the scene's script, baked for the game.

The developer's idea (Nov 11): he sits slumped on his throne like a statue, bound by a web of
chains strung through the hall, with his greatsword hanging point down on a chain high above
and just in front of him. The chains burn through one after another, the sword falls and
drives into the dais at his feet, his hand reaches and closes on the hilt, the blade and then
the whole hall take fire, and he rises. Then he slings the sword down the hall, takes its
chain in both fists and heaves himself after it to the hall's centre, and the fight begins. It
is long on purpose (61 s): every beat is given room.

His own acting is three clips in ../clips.py: `throned` (the statue, a loop), `awaken`, and
`intro_haul`, which he plays from RUNWAY, where the scene stands him behind a cut. This
script owns everything else, on the same clock (20 ticks a second):
  - the camera, one shot after another, with hard cuts;
  - the web: every chain's two ends, when heat starts along it, when it parts and when the
    last of it has burned away;
  - the hall's fires and the light blocks that carry them, which start out and catch in a
    wave from the throne;
  - how dark the hall is, how much of his own glow shows, and how much of the blade burns.

Everything is in hall cells as art/pyre_hall/build_layout.py lays them out: +X east, +Z south,
y in cells with the fight floor's top at y = 1 (so a point (x, y, z) here is the world's
centre + (x, SURFACE_Y + y, z)); a cell's middle is at .5.

  py -3 art/pyre_king/intro/build_intro.py            writes intro-track.json beside this file
  ... --install                                       also writes the game's copy
  ... --check                                         fails if either copy is stale
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
sys.path.insert(0, str(HERE.parent))
sys.path.insert(0, str(ROOT/'art/pyre_hall'))
import build_layout as hall       # noqa: E402
import clips                      # noqa: E402

TRACK = HERE/'intro-track.json'
RUNTIME = ROOT/'src/main/resources/assets/elementalwands/cinematics/pyre_king_intro.json'
RATE = 20

# ---------------------------------------------------------------- the clock (ticks)
AWAKEN = 600                                        # `throned` loops three times, then `awaken` plays
FALL, STRUCK = AWAKEN+round(clips.FALL*RATE), AWAKEN+round(clips.STRUCK*RATE)
GRASP = AWAKEN+round(clips.GRASP*RATE)
IGNITE = AWAKEN+200                                 # awaken's 10 s: the blade takes fire from his fist
BRAZIERS, LIT = IGNITE+30, IGNITE+100               # the hall's fires catch, throne to gate
RISE = AWAKEN+310
TITLE, TITLE_END = AWAKEN+400, AWAKEN+460
HAUL = AWAKEN+round(clips.AWAKEN*RATE)              # awaken ends in his guard; behind a cut, `intro_haul` begins
def hauled(seconds): return HAUL+round(seconds*RATE)
THROW, STUCK = hauled(clips.IH_THROW), hauled(clips.IH_STICK)       # the sword leaves his hand; it drives in by his mark
GRAB, STRAIN = hauled(clips.IH_GRAB), hauled(clips.IH_TAUT)         # both fists have the chain; it holds his weight
HEAVE, LAUNCH = hauled(clips.IH_HEAVE), hauled(clips.IH_OFF)        # he heaves; his feet leave the floor
LANDED, STOPPED = hauled(clips.IH_TOUCH), hauled(clips.IH_STOP)     # they strike; he has ploughed to a stop on his mark
TAKE, TEAR = hauled(clips.IH_TAKE), hauled(clips.IH_TEAR)           # his fist closes on the hilt; the blade comes out
RETURN = hauled(clips.IH_LEN)-10                    # the camera starts home as he comes up into his guard
LENGTH = RETURN+25
PULSE = 390                                         # the shard in his chest stirs once
FIRST_LINK, BURN = 416, 440                         # one link by the gate begins to glow; then the web goes
BEATS = {'awaken': AWAKEN, 'fall': FALL, 'struck': STRUCK, 'grasp': GRASP, 'ignite': IGNITE, 'braziers': BRAZIERS, 'lit': LIT,
         'rise': RISE, 'title': TITLE, 'title_end': TITLE_END, 'haul': HAUL, 'throw': THROW, 'stuck': STUCK, 'grab': GRAB,
         'strain': STRAIN, 'heave': HEAVE, 'launch': LAUNCH, 'landed': LANDED, 'stopped': STOPPED, 'take': TAKE, 'tear': TEAR,
         'return': RETURN, 'length': LENGTH,
         'pulse': PULSE, 'burn': BURN}
assert AWAKEN % round(clips.TWIST_PERIOD*RATE) == 0, 'the statue loop must end as awaken begins'

# ---------------------------------------------------------------- where things are
SEAT = np.array([.5, 7., -50.5])                    # where he stands for both clips: half a block in front of the seat
KING_MARK = np.array([hall.KING[0]+.5, hall.KING[1], hall.KING[2]+.5])
ARRIVAL = np.array([hall.ARRIVAL[0]+.5, hall.ARRIVAL[1], hall.ARRIVAL[2]+.5])
RUNWAY = KING_MARK-np.array([0, 0, clips.IH_RUN])  # where `intro_haul` begins: its travel ends on his mark
STUCK_AT = RUNWAY+np.array([-clips.IH_FLOOR[0], 0, -clips.IH_FLOOR[2]])     # where the slung sword enters the floor
assert hall.THRONE_SEAT_FRONT == -52 and abs(SEAT[2]-(hall.THRONE_SEAT_FRONT+1+clips.SEAT_FRONT)) < 1e-9, 'the seat moved'


def king(p):
    """A point of his clips (his right, up, his back) in hall cells: he faces south from the seat."""
    return SEAT+np.array([-p[0], p[1], -p[2]])


SWORD_FLOOR = king(clips.SWORD_DROP)                # where the point enters the dais
SWORD_TOP = SWORD_FLOOR+np.array([0, clips.SWORD_HUNG, 0])      # the hanging sword's pommel
GRIP = king(clips.STUCK_GRIP)
HAND = king(clips.ARMREST['right'])
HEAD = SEAT+np.array([0, 3.25, -1.75])              # the statue's bowed head
SHARD = SEAT+np.array([0, 2.25, -1.7])
RING_Y = 62                                         # the roof's broken ring, which the sword's chain hangs from


def sword(t):
    """The pommel of his sword at scene tick t, as his clips have it, in hall cells."""
    s = t/RATE
    spec = clips.throned(s % clips.TWIST_PERIOD) if t < AWAKEN else clips._awaken(min(s-AWAKEN/RATE, clips.AWAKEN))
    return king(spec['sword_p'])


# ---------------------------------------------------------------- the web of chains
def pillar(az, toward, height=None):
    """A point on a pillar's shaft, on the side facing `toward`, at a height on it."""
    a = math.radians(az); c = np.array([hall.PILLAR_R*math.cos(a)+.5, 0, hall.PILLAR_R*math.sin(a)+.5])
    top = hall.PILLARS[az]
    d = np.array([toward[0]-c[0], 0, toward[2]-c[2]]); d /= max(np.linalg.norm(d), 1e-9)
    return c+3.4*d+np.array([0, min(top+.5, 14.) if height is None else min(height, top+.5), 0])


def chain(a, b, sag, heat, run, part=.5):
    """One chain: its ends, how far it sags, the tick heat starts at a, the ticks it takes to run to b. It parts
    `part` of the way along just after the heat has reached there, and the last of it is ash 3 s later."""
    a, b = np.asarray(a, float), np.asarray(b, float)
    parts = heat+round(run*max(part, 1-part))+6
    return {'a': [round(float(v), 3) for v in a], 'b': [round(float(v), 3) for v in b], 'sag': sag, 'heat': int(heat),
            'run': int(run), 'part': part, 'parts': int(parts), 'gone': int(parts+60)}


def web():
    out = []
    az = sorted(hall.PILLARS)                                       # 0 east, 60 south-east, ... 300 north-east
    centre = np.array([.5, 0, .5])
    throne = np.array([.5, 22., -53.])

    def when(point):
        """Heat walks the hall from the gate to the throne: 6 s over its length."""
        return BURN+round(120*(48-point[2])/100)
    # From the wall above the gate to the two southern pillars: where the fire starts.
    for sx, a in ((1, 60), (-1, 120)):
        g = np.array([.5+sx*6.5, 20., 48.2]); p = pillar(a, g)
        out.append(chain(g, p, 3.0, FIRST_LINK if sx > 0 else BURN+8, 44 if sx > 0 else 26))
    # Pillar to pillar round the hall, and three through its middle.
    for i, a in enumerate(az):
        b = az[(i+1) % 6]
        p, q = pillar(a, pillar(b, centre)), pillar(b, pillar(a, centre))
        south, north = (p, q) if p[2] >= q[2] else (q, p)
        out.append(chain(south, north, 2.4, when(south), 22))
    for a in az[:3]:
        p, q = pillar(a, centre, 10), pillar(a+180, centre, 10)
        south, north = (p, q) if p[2] >= q[2] else (q, p)
        out.append(chain(south, north, 3.2, when(south)+10, 34))
    # Every pillar to the throne's back.
    for i, a in enumerate(az):
        t = throne+np.array([(-3.5, -2, 2, 3.5, -1, 1)[i], (-2, 1, 1, -2, 3, 3)[i], 0])
        p = pillar(a, t)
        out.append(chain(p, t, 4.0, when(p)+16, 40, part=.55))
    # What holds him: ankles, lap, wrists, and his collar to the throne's back. They go last, from his feet up.
    hold = BURN+124
    right_wrist, left_hand = HAND+np.array([0, .1, 0]), king(clips.LAP)+np.array([0, .12, 0])
    out.append(chain(king(clips.SEAT_FEET['right'])+np.array([0, .25, 0]), SEAT+np.array([-4., .08, 1.5]), .08, hold, 6))
    out.append(chain(king(clips.SEAT_FEET['left'])+np.array([0, .25, 0]), SEAT+np.array([4.2, .08, 1.6]), .08, hold+4, 6))
    out.append(chain(SEAT+np.array([-1.75, 2.06, -1.0]), SEAT+np.array([1.75, 2.06, -1.0]), .38, hold+10, 6))
    out.append(chain(right_wrist, SEAT+np.array([-6., .08, 2.0]), .35, hold+16, 8, part=.3))
    out.append(chain(left_hand, SEAT+np.array([5.5, .08, 2.0]), .45, hold+20, 8, part=.3))
    out.append(chain(SEAT+np.array([-1.0, 3.0, -2.25]), np.array([-3.7, 19., -52.9]), 1.1, hold+26, 10, part=.25))
    out.append(chain(SEAT+np.array([1.0, 3.0, -2.25]), np.array([4.7, 19., -52.9]), 1.1, hold+30, 10, part=.25))
    # And the one the sword hangs on. Heat runs down it from the ring; it parts at the pommel as the sword falls.
    hung = chain([SWORD_TOP[0], RING_Y, SWORD_TOP[2]], SWORD_TOP, 0., FALL-36, 30, part=1.)
    hung['parts'] = FALL; hung['gone'] = FALL+70; hung['sword'] = True
    out.append(hung)
    assert max(c['parts'] for c in out) <= FALL and max(c['gone'] for c in out) < IGNITE, 'every chain is ash before the blade lights'
    return out


def along(c, u):
    """The point a share u of the way along a chain, hanging: the game draws it by the same rule."""
    a, b = np.array(c['a']), np.array(c['b'])
    return a+(b-a)*u-np.array([0, c['sag']*4*u*(1-u), 0])


WEB = web()

# ---------------------------------------------------------------- the camera
EASE = {'io': lambda f: f*f*(3-2*f), 'lin': lambda f: f, 'out': lambda f: 1-(1-f)**2, 'in': lambda f: f*f,
        'slow': lambda f: f*f*f*(f*(6*f-15)+10)}


def key(t, rows):
    """rows: (tick, value[, ease]); the ease names how the move arrives at that row."""
    if t <= rows[0][0]: return np.asarray(rows[0][1], float)
    for a, b in zip(rows, rows[1:]):
        if t <= b[0]:
            f = (t-a[0])/(b[0]-a[0])
            return np.asarray(a[1], float)+(np.asarray(b[1], float)-np.asarray(a[1], float))*EASE[b[2] if len(b) > 2 else 'io'](f)
    return np.asarray(rows[-1][1], float)


def jolt(t, at, size, ticks=14):
    """A camera knock at `at`, dying away."""
    if not at <= t < at+ticks: return np.zeros(3)
    k = size*(1-(t-at)/ticks)**2
    return k*np.array([math.sin(t*2.9), math.cos(t*3.7), math.sin(t*1.9+1)])


# Each shot: (first tick, name, eye rows, look rows, fov rows). A cut starts each one.
def shots():
    blade_mid = SWORD_FLOOR+np.array([0, 2.0, -.45])
    return [
        (0, 'arrival', [(0, (.5, 5.4, 47.5)), (100, (.5, 4.5, 39.), 'out')], [(0, (.5, 9., -50.))], [(0, 55)]),
        (100, 'the web', [(100, (-13., 8.5, 13.)), (220, (-7.5, 10., 2.), 'lin')], [(100, (6., 12., -28.)), (220, (3., 13., -40.), 'lin')], [(100, 60)]),
        (220, 'the king', [(220, (2.8, 8.3, -42.8)), (300, (1.9, 10., -46.4), 'slow')], [(220, HEAD)], [(220, 50), (300, 46)]),
        # From down the hall at its own height, so that it hangs black in the apse's broken round window.
        (300, 'the hanging sword', [(300, SWORD_TOP+np.array([.6, -2.6, 15.])), (360, SWORD_TOP+np.array([.4, -2.4, 11.5]), 'lin')],
         [(300, SWORD_TOP-np.array([0, 2.2, 0]))], [(300, 30)]),
        (360, 'the shard', [(360, (2.1, 9.5, -49.3)), (410, (1.8, 9.45, -49.6), 'lin')], [(360, SHARD)], [(360, 34)]),
        (410, 'the first link', [(410, along(WEB[0], .16)+np.array([3.2, -1.2, -3.6])), (440, along(WEB[0], .16)+np.array([2.9, -1.1, -3.9]), 'lin')],
         [(410, along(WEB[0], .12))], [(410, 42)]),
        (440, 'the web burns', [(440, (.5, 27., 45.5)), (520, (.5, 23., 42.), 'lin')], [(440, (.5, 6., -8.)), (520, (.5, 7., -14.), 'lin')], [(440, 62)]),
        (520, 'toward the throne', [(520, (-5.5, 13., -12.)), (585, (-3.4, 12., -39.5), 'lin')], [(520, (.5, 10., -52.))], [(520, 56)]),
        (585, 'its chain', [(585, SWORD_TOP+np.array([1.8, 1.6, 9.5])), (FALL+2, SWORD_TOP+np.array([1.7, 1.5, 9.2]), 'lin')],
         [(585, SWORD_TOP+np.array([0, .2, 0]))], [(585, 34)]),
        (FALL+2, 'the fall', [(FALL+2, (3.6, 7.7, -45.2)), (670, (3.6, 7.7, -45.2))], [(FALL+2, blade_mid)], [(FALL+2, 50), (STRUCK, 50), (STRUCK+26, 44)]),
        (670, 'the hand', [(670, HAND+np.array([-1.9, .5, 1.8])), (712, HAND+np.array([-1.8, .45, 1.7]), 'lin')], [(670, HAND+np.array([0, .05, 0]))], [(670, 36)]),
        (712, 'his head', [(712, (-1.2, 9.9, -48.7)), (745, (-1.1, 9.95, -48.8), 'lin')], [(712, HEAD)], [(712, 36)]),
        (745, 'the reach', [(745, GRIP+np.array([4.3, -.2, 1.2])), (IGNITE, GRIP+np.array([4.3, -.2, 1.2])), (IGNITE+30, GRIP+np.array([4.9, .5, 2.0]), 'out')],
         [(745, GRIP), (IGNITE, GRIP), (IGNITE+30, blade_mid, 'out')], [(745, 40), (IGNITE, 40), (IGNITE+30, 48)]),
        (BRAZIERS, 'the hall takes fire', [(BRAZIERS, (.5, 15.5, 46.5)), (RISE, (.5, 14., 43.), 'lin')], [(BRAZIERS, (.5, 8., -20.))], [(BRAZIERS, 64)]),
        (RISE, 'he rises', [(RISE, (4.1, 7.9, -45.4)), (TITLE, (4.4, 8.0, -44.8), 'lin'), (HAUL, (4.5, 8.0, -44.6), 'lin')],
         [(RISE, SEAT+np.array([0, 2.6, .5])), (RISE+90, SEAT+np.array([0, 3.5, .2]), 'slow'), (HAUL, SEAT+np.array([0, 3.7, .2]))], [(RISE, 50)]),
        # Close on his sword side, low, so the wind-up, the fists on the chain and his weight against it all read.
        (HAUL, 'the throw', [(HAUL, RUNWAY+np.array([-5.1, 1.3, 8.])), (LAUNCH+2, RUNWAY+np.array([-4.7, 1.2, 7.2]), 'lin')],
         [(HAUL, RUNWAY+np.array([-.2, 2.6, .9])), (LAUNCH+2, RUNWAY+np.array([-.2, 2.4, 1.1]), 'lin')], [(HAUL, 46)]),
        # From beside his mark, on his left, so the stuck sword stands clear beside where he will stop: he comes at it.
        (LAUNCH+2, 'down the hall', [(LAUNCH+2, (3.4, 1.7, 6.)), (LENGTH, (3.4, 1.7, 6.))],
         [(LAUNCH+2, (.5, 3.8, -12.)), (LANDED, KING_MARK+np.array([-.3, 2.6, -2.]), 'lin'), (STOPPED+4, KING_MARK+np.array([-.8, 1.7, 0]), 'out'),
          (TEAR, KING_MARK+np.array([-.8, 1.8, 0])), (RETURN, KING_MARK+np.array([-.2, 2.9, 0]), 'slow')], [(LAUNCH+2, 58)]),
    ]


def camera(t):
    """Eye, yaw, pitch (Minecraft's: yaw 0 looks south, positive pitch looks down) and field of view."""
    shot = [s for s in shots() if s[0] <= t][-1]
    eye = key(t, shot[2])+jolt(t, STRUCK, .35)+jolt(t, STUCK, .1, 8)+jolt(t, LANDED, .45, 18)
    look = key(t, shot[3])
    d = look-eye
    return eye, math.degrees(math.atan2(-d[0], d[2])), -math.degrees(math.atan2(d[1], math.hypot(d[0], d[2]))), float(key(t, shot[4]))


# ---------------------------------------------------------------- light
def columns(t):
    black = float(key(t, [(0, 1), (44, 0, 'out')]))
    dark = float(key(t, [(0, 1), (IGNITE, 1), (IGNITE+26, .8, 'out'), (BRAZIERS, .8), (LIT, 0, 'lin')]))
    ember = float(key(t, [(0, 0), (PULSE-10, 0), (PULSE, 1, 'out'), (PULSE+44, .22), (BURN, .22), (FALL, .5, 'lin'), (GRASP-6, .5),
                          (GRASP+4, .75, 'out'), (IGNITE, .75), (IGNITE+12, 1, 'out')]))
    blade = float(key(t, [(0, 0), (IGNITE, 0), (IGNITE+26, 1, 'lin')]))
    return black, dark, ember, blade


def fires():
    """Every fire in the hall and every light block that carries one, with the tick it catches: throne first."""
    lay, facts = hall.build_hall()
    throne = np.array([.5, -50.])
    out_fire, out_light = [], []
    for (x, y, z), state in lay.b.items():
        name = state.split(':', 1)[1]
        if name != 'fire' and not name.startswith('light['): continue
        far = math.hypot(x+.5-throne[0], z+.5-throne[1])
        tick = BRAZIERS+round((LIT-BRAZIERS-10)*min(1, far/100))
        if name == 'fire': out_fire.append([x, y, z, tick])
        else: out_light.append([x, y, z, int(name[len('light[level='):-1]), tick])
    return sorted(out_fire, key=lambda f: f[3]), sorted(out_light, key=lambda f: f[4])


def bake():
    cuts = [s[0] for s in shots()][1:]
    ticks = {k: [] for k in ('eye', 'yaw', 'pitch', 'fov', 'black', 'dark', 'ember', 'blade', 'sword')}
    for t in range(LENGTH+1):
        eye, yaw, pitch, fov = camera(t)
        black, dark, ember, blade = columns(t)
        ticks['eye'].append([round(float(v), 3) for v in eye]); ticks['yaw'].append(round(yaw, 2)); ticks['pitch'].append(round(pitch, 2))
        ticks['fov'].append(round(fov, 2)); ticks['black'].append(round(black, 3)); ticks['dark'].append(round(dark, 3))
        ticks['ember'].append(round(ember, 3)); ticks['blade'].append(round(blade, 3))
        ticks['sword'].append([round(float(v), 3) for v in sword(min(t, HAUL))])
    fire, light = fires()
    return {'beats': BEATS, 'cuts': cuts, 'shots': {s[1]: s[0] for s in shots()},
            'frame': 'hall cells: +X east, +Z south, the fight floor\'s top at y = 1; world = centre + (x, SURFACE_Y + y, z)',
            'seat': [float(v) for v in SEAT], 'mark': [float(v) for v in KING_MARK], 'runway': [float(v) for v in RUNWAY],
            'stuck': [float(v) for v in STUCK_AT], 'chains': WEB, 'fires': fire, 'lights': light, 'ticks': ticks}


def packed(track):
    rows = ',\n'.join('  %s: %s' % (json.dumps(k), json.dumps(v, separators=(',', ':'))) for k, v in track.items())
    return ('{\n'+rows+'\n}\n').encode()


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
    print(('CHECK OK' if args.check else 'INSTALLED' if args.install else 'BUILT')
          + f': {LENGTH} ticks ({LENGTH/RATE:.1f} s), {len(track["cuts"])+1} shots, {len(track["chains"])} chains, {len(track["fires"])} fires, {len(track["lights"])} lights')


if __name__ == '__main__':
    main()
