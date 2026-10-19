#!/usr/bin/env python3
"""Authors the Storm Summit, the Storm Roc's realm: a round mountaintop above a sea of
clouds, ringed by thin rock spires the Roc perches on.

    python3 art/storm_summit/build_layout.py            # writes the browser preview
    python3 art/storm_summit/build_layout.py --serve    # ...and serves it on :8390

Coordinates are block cells relative to the summit's centre; y=0 is the summit surface,
so players stand at y=1. North is -z. The sea of clouds is a render effect, not blocks;
everything below its top is hidden. The preview lives in the ignored
.local-previews/storm-summit/.
"""
import argparse, base64, io, json, math, random, shutil, zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
OUT = ROOT / '.local-previews/storm-summit'
JAR = Path.home() / '.gradle/caches/fabric-loom/1.21.10/minecraft-client.jar'
ROC = ROOT / 'art/storm_roc/candidate'

SUMMIT_R = 58          # mean rim radius: about 116 blocks across
CLOUD_Y = -36          # top of the sea of clouds; a fall past it is death
BOTTOM = -50           # lowest authored block, well inside the cloud
OPEN_R = 22            # the middle stays bare rock: no boulders or ruins
ARRIVAL = (0, 1, 32)   # the party lands south of the centre, facing the home spire
SEED = 0x5707
ROC_SCALE = 1.25      # the Roc is drawn at 1.25x the art study: a 34-block wingspan

# The six perches: azimuth (degrees, 0 = east, 90 = south), gap from the rim to the
# spire's face at summit height, and the height of the perch above the summit.
SPIRES = [
    ('home', 270, 24, 35),
    ('north-east', 326, 27, 28),
    ('east', 22, 22, 31),
    ('south', 86, 29, 25),
    ('south-west', 146, 23, 30),
    ('west', 206, 26, 27),
]
PROMONTORY_AZ = 52     # a narrow tongue of rock between the east and south spires
SHRINE_AZ = 180        # the Windcaller shrine, half fallen, on the west rim
LONE_STONES = [(52, 'promontory'), (300, 'pair'), (122, 'snapped')]
# Distant tops through the clouds: azimuth, distance, height, footprint scale.
DISTANT_PEAKS = [(12, 150, -6, 1), (60, 190, 6, 1.1), (104, 158, -18, .9), (140, 198, 14, 1.15),
                 (172, 142, -12, 1), (224, 182, 2, 1), (304, 160, -8, .95), (342, 196, 9, 1.1)]
LOW_NEEDLES = [(40, 112, -8), (120, 104, -14), (236, 118, -5), (296, 126, -11)]

SIDES = ((1, 0), (-1, 0), (0, 1), (0, -1))


class Layout:
    def __init__(self):
        self.b = {}

    def put(self, x, y, z, state, replace=True):
        state = state if ':' in state else 'minecraft:' + state
        if replace or (x, y, z) not in self.b:
            self.b[x, y, z] = state

    def get(self, x, y, z):
        return self.b.get((x, y, z))


# ---------------------------------------------------------------- noise

def noise(x, y, z, salt=0):
    """Deterministic per-cell value in [0, 1]."""
    n = (x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ salt * 2654435761) & 0xffffffff
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffffff) / 0xffffff


def vnoise(x, z, salt):
    ix, iz = math.floor(x), math.floor(z)
    fx, fz = x - ix, z - iz
    sx, sz = fx * fx * (3 - 2 * fx), fz * fz * (3 - 2 * fz)
    a, b = noise(ix, 0, iz, salt), noise(ix + 1, 0, iz, salt)
    c, d = noise(ix, 0, iz + 1, salt), noise(ix + 1, 0, iz + 1, salt)
    return a + (b - a) * sx + (c - a) * sz + (a - b - c + d) * sx * sz


def fbm(x, z, salt, octaves=3):
    total = norm = 0.0
    amp = 1.0
    for o in range(octaves):
        total += vnoise(x * 2 ** o, z * 2 ** o, salt + o * 31) * amp
        norm += amp
        amp *= .5
    return total / norm


def angle_diff(a, b):
    return (a - b + math.pi) % (2 * math.pi) - math.pi


def rad(deg):
    return math.radians(deg)


# ---------------------------------------------------------------- the rim

class Rim:
    """The summit's outline: a wobbling circle with one narrow promontory."""

    def __init__(self, rng):
        self.waves = [(k, rng.uniform(0, 2 * math.pi), a) for k, a in ((2, 2.0), (3, 1.6), (4, 1.3), (6, 1.1), (9, .7), (13, .45))]

    def radius(self, az):
        r = SUMMIT_R + sum(a * math.sin(k * az + p) for k, p, a in self.waves)
        off = abs(angle_diff(az, rad(PROMONTORY_AZ)))
        if off < .09:
            r += 8 * (1 - off / .09) ** .7
        return r

    def depth(self, x, z):
        """Signed distance outside the rim (negative inside)."""
        return math.hypot(x, z) - self.radius(math.atan2(z, x))


def strata(x, y, z):
    """Banded rock for the cliffs and the ground under the summit."""
    band = math.floor((y + 3 * vnoise(x / 13, z / 13, 7)) / 3)
    rock = ('stone', 'stone', 'andesite', 'stone', 'tuff', 'stone', 'andesite', 'stone', 'stone', 'diorite')[band % 10]
    if rock == 'diorite' and noise(x, y, z, 5) < .85:
        rock = 'stone'
    r = noise(x, y, z, 3)
    if r < .06:
        return 'cobblestone'
    if r < .1 and rock == 'stone':
        return 'andesite'
    return rock


def summit_rock(x, z):
    n = fbm(x / 11, z / 11, 1)
    r = noise(x, 0, z, 2)
    if r < .045:
        return 'cobblestone'
    if r < .075:
        return 'gravel'
    if n < .36:
        return 'andesite'
    if n > .68:
        return 'tuff'
    return 'stone'


# ---------------------------------------------------------------- summit features

def plan_cracks(rng, rim, avoid):
    """Fissures that wander outward from the open middle and break the rim."""
    cells = {}
    rim_ends = []

    def walk(x, z, heading, steps, to_rim):
        for _ in range(steps):
            heading += rng.uniform(-.32, .32)
            if to_rim:
                heading += .18 * angle_diff(math.atan2(z, x), heading)
            x += math.cos(heading)
            z += math.sin(heading)
            c = (round(x), round(z))
            if any(math.hypot(c[0] - ax, c[1] - az) < ar for ax, az, ar in avoid):
                return
            d = rim.depth(*c)
            if d > 1.5:
                rim_ends.append((c, heading))
                return
            cells[c] = 'core'
            for dx, dz in SIDES:
                n = (c[0] + dx, c[1] + dz)
                if n not in cells and noise(n[0], 1, n[1], 9) < .45:
                    cells[n] = 'side'
            if to_rim and rng.random() < .05:
                walk(x, z, heading + rng.choice((-1, 1)) * rng.uniform(.6, 1.0), rng.randint(4, 9), False)
    for i in range(8):
        az = i * math.pi / 4 + rng.uniform(.1, .6)
        r = rng.uniform(OPEN_R - 4, 34)
        walk(r * math.cos(az), r * math.sin(az), az + rng.uniform(-.4, .4), 80, True)
    return cells, rim_ends


def lichtenberg(rng, cx, cz, reach):
    """A lightning scar: fern-like branches burned into the rock from a strike point."""
    cells = {}

    def walk(x, z, heading, length, depth):
        for _ in range(length):
            x += math.cos(heading)
            z += math.sin(heading)
            heading += rng.uniform(-.55, .55)
            c = (round(x), round(z))
            cells[c] = max(cells.get(c, 0), 1 - math.hypot(x - cx, z - cz) / reach)
            if depth < 3 and rng.random() < .22:
                walk(x, z, heading + rng.choice((-1, 1)) * rng.uniform(.5, .9), max(2, int(length * .5)), depth + 1)
    arms = rng.randint(5, 7)
    for k in range(arms):
        walk(cx, cz, k * 2 * math.pi / arms + rng.uniform(-.3, .3), int(reach * rng.uniform(.7, 1.05)), 0)
    for x in range(round(cx) - 2, round(cx) + 3):
        for z in range(round(cz) - 2, round(cz) + 3):
            if math.hypot(x - cx, z - cz) < 1.7:
                cells[x, z] = 1.0
    return cells


def scar_block(intensity):
    if intensity > .86:
        return 'obsidian'
    if intensity > .6:
        return 'blackstone'
    if intensity > .3:
        return 'smooth_basalt'
    return 'cobbled_deepslate'


def scatter(rng, count, rmin, rmax_fn, spacing, avoid, tries=4000):
    """Poisson-style points on the summit, kept apart from each other and from `avoid`."""
    pts = []
    for _ in range(tries):
        if len(pts) == count:
            break
        az = rng.uniform(0, 2 * math.pi)
        r = rng.uniform(rmin, rmax_fn(az))
        x, z = r * math.cos(az), r * math.sin(az)
        if any(math.hypot(x - px, z - pz) < spacing for px, pz in pts):
            continue
        if any(math.hypot(x - ax, z - az_) < ar for ax, az_, ar in avoid):
            continue
        pts.append((x, z))
    return pts


# ---------------------------------------------------------------- the summit

def build_summit(seed=SEED):
    rng = random.Random(seed)
    lay = Layout()
    rim = Rim(rng)
    shrine_r = rim.radius(rad(SHRINE_AZ)) - 2.5
    shrine = (round(shrine_r * math.cos(rad(SHRINE_AZ))), round(shrine_r * math.sin(rad(SHRINE_AZ))))
    keep_clear = [(ARRIVAL[0], ARRIVAL[2], 7), (shrine[0], shrine[1], 11), (0, 0, OPEN_R - 6)]
    cracks, rim_ends = plan_cracks(rng, rim, [(shrine[0], shrine[1], 9), (ARRIVAL[0], ARRIVAL[2], 4)])

    # Heightfield: a flat summit, a craggy lip in places, cliffs that fall to the clouds.
    lip_salt = rng.randint(0, 999)
    tops = {}
    reach = SUMMIT_R + 40
    for x in range(-reach, reach + 1):
        for z in range(-reach, reach + 1):
            d = rim.depth(x, z)
            az = math.atan2(z, x)
            if d <= 0:
                t = 0
                if d > -1.6 and fbm(az * 9, 0, lip_salt) > .62 and noise(x, 1, z, 4) < .55 and abs(angle_diff(az, rad(SHRINE_AZ))) > .3:
                    t = 1 + (noise(x, 1, z, 4) < .12)
            else:
                # A sheer drop under the rim, then a steep flank that flares out into
                # buttresses and reaches the clouds 12-18 blocks out, short of the spires.
                spur = max(0.0, math.cos(7 * az + 1.3)) ** 3 * 5 * fbm(az * 3, 1, 5) + 2.5 * max(0.0, math.cos(13 * az + .4)) ** 6
                dd = max(0.0, d - spur * min(1.0, d / 3))
                g = 5 * dd if dd < 1.4 else 7 + 2.1 * (dd - 1.4)
                t = -1 - round(g + 2.6 * (fbm(x / 5, z / 5, 6) - .5) * 2)
                if t < BOTTOM:
                    continue
            tops[x, z] = t
    # Fissures break the rim into narrow gullies.
    for (cx, cz), heading in rim_ends:
        for k in range(-4, 4):
            gx, gz = round(cx - math.cos(heading) * k), round(cz - math.sin(heading) * k)
            for w in (0, 1):
                c = (gx + round(-math.sin(heading) * w), gz + round(math.cos(heading) * w))
                d = rim.depth(*c)
                if c in tops and -3.5 < d < 3:
                    tops[c] = min(tops[c], -1 - round((3.5 + d) * 1.7))
    # Fill each column from a little below its lowest neighbour, so faces are closed.
    for (x, z), t in tops.items():
        low = min(tops.get((x + dx, z + dz), BOTTOM) for dx, dz in SIDES)
        for y in range(max(BOTTOM, min(low, t) - 2), t + 1):
            lay.put(x, y, z, strata(x, y, z))
        if t == 0 or (t > 0 and rim.depth(x, z) <= 0):
            lay.put(x, t, z, summit_rock(x, z) if t == 0 else ('andesite' if noise(x, t, z, 8) < .5 else 'stone'))
            if t > 0:
                for y in range(0, t):
                    lay.put(x, y, z, 'stone')
    for c, role in cracks.items():
        if tops.get(c) == 0:
            r = noise(c[0], 0, c[1], 10)
            lay.put(c[0], 0, c[1], ('cobbled_deepslate' if r < .6 else 'deepslate') if role == 'core'
                    else ('gravel' if r < .4 else 'tuff' if r < .75 else 'cobblestone'))

    # Boulders: a few low rocks around the open middle.
    obstacles = {}
    for bx, bz in scatter(rng, 11, OPEN_R, lambda az: rim.radius(az) - 6, 15, keep_clear):
        rx, rz = rng.uniform(1.3, 2.8), rng.uniform(1.2, 2.4)
        height = rng.choices((1, 2, 3), (5, 4, 2))[0]
        turn = rng.uniform(0, math.pi)
        for x in range(round(bx) - 4, round(bx) + 5):
            for z in range(round(bz) - 4, round(bz) + 5):
                u = ((x - bx) * math.cos(turn) + (z - bz) * math.sin(turn)) / rx
                v = (-(x - bx) * math.sin(turn) + (z - bz) * math.cos(turn)) / rz
                e = u * u + v * v + .35 * (noise(x, 2, z, 11) - .5)
                if e > 1 or tops.get((x, z)) != 0:
                    continue
                h = max(1, round(height * math.sqrt(max(0, 1 - e)) + .3))
                for y in range(1, h + 1):
                    r = noise(x, y, z, 12)
                    lay.put(x, y, z, 'andesite' if r < .4 else 'stone' if r < .75 else 'cobblestone' if r < .9 else 'tuff')
                obstacles[x, z] = h

    # Lightning scars: on the open rock, where the Roc's strikes have landed before.
    scars = scatter(rng, 5, 6, lambda az: rim.radius(az) - 10, 19, [(shrine[0], shrine[1], 13), (ARRIVAL[0], ARRIVAL[2], 5)])
    scarred = set()
    for sx, sz in scars:
        for c, inten in lichtenberg(rng, sx, sz, rng.uniform(6, 10)).items():
            if tops.get(c) == 0 and c not in obstacles:
                lay.put(c[0], 0, c[1], scar_block(inten))
                scarred.add(c)

    windcaller_shrine(lay, rng, rim, shrine, tops, obstacles)
    for az, kind in LONE_STONES:
        lone_stone(lay, rng, rim, az, kind, tops, obstacles)

    relics = remains(lay, rng, rim, cracks, tops, obstacles, keep_clear + [(sx, sz, 5) for sx, sz in scars])

    # Snow: drifts in the lee of rocks and ruins (the wind blows from the west), in
    # hollows of the rim, and a dusting on the ledges of the cliffs.
    lee = {}
    for (ox, oz), h in obstacles.items():
        for k in range(1, 3 + h):
            c = (ox + k, oz + (noise(ox, k, oz, 13) < .3) - (noise(ox, k, oz, 14) < .3))
            lee[c] = lee.get(c, 0) + .3 * (1 - k / (4 + h))
    bare = {(x + dx, z + dz) for x, z in relics for dx in (-1, 0, 1) for dz in (-1, 0, 1)}
    for (x, z), t in tops.items():
        if (x, z) in scarred or (x, z) in bare:
            continue
        top = lay.get(x, t, z)
        if t == 0 and (x, z) not in obstacles:
            if top and ('copper' in top or 'diorite' in top or 'calcite' in top):
                continue
            s = fbm(x / 9, z / 9, 15) + lee.get((x, z), 0) + .1 * max(0, rim.depth(x, z) + 6) / 6
            if s > .74:
                lay.put(x, 0, z, 'snow_block')
                if noise(x, 1, z, 16) < .5:
                    lay.put(x, 1, z, 'snow[layers=1]')
            elif s > .64:
                lay.put(x, 1, z, 'snow[layers=1]')
        elif t < 0 and t > CLOUD_Y - 2:
            if sum(tops.get((x + dx, z + dz), BOTTOM) <= t for dx, dz in SIDES) >= 3 and noise(x, t, z, 17) < .7:
                lay.put(x, t + 1, z, 'snow[layers=%d]' % (1 + (noise(x, t, z, 18) < .4) + (noise(x, t, z, 19) < .2)))
        elif (x, z) in obstacles and noise(x, 9, z, 20) < .45:
            h = max(y for y in range(1, 8) if lay.get(x, y, z))
            if 'snow' not in lay.get(x, h, z) and 'rod' not in lay.get(x, h, z) and lay.get(x, h + 1, z) is None:
                lay.put(x, h + 1, z, 'snow[layers=1]')

    perches = []
    for name, az, gap, height in SPIRES:
        perches.append(build_spire(lay, rng, rim, name, az, gap, height))
    for az, dist, top in LOW_NEEDLES:
        build_needle(lay, rng, az, dist, top)
    for peak in DISTANT_PEAKS:
        build_peak(lay, *peak)
    facts = {'rim': [round(min(rim.radius(rad(a)) for a in range(360)), 1), round(max(rim.radius(rad(a)) for a in range(360) if abs(angle_diff(rad(a), rad(PROMONTORY_AZ))) > .1), 1)],
             'shrine': shrine, 'scars': [[round(x), round(z)] for x, z in scars], 'perches': perches}
    return lay, facts


# ---------------------------------------------------------------- Windcaller ruins

def standing_stone(lay, x, z, height, axis, snapped=False, rod=True):
    """A wind-worn pale stone with a lower shoulder beside it, capped with green copper."""
    for y in range(1, height + 1):
        r = noise(x, y, z, 21)
        lay.put(x, y, z, 'calcite' if r < .62 else 'polished_diorite' if r < .86 else 'diorite')
    sx, sz = x + axis[0], z + axis[1]
    for y in range(1, max(1, height - 2) + 1):
        lay.put(sx, y, sz, 'calcite' if noise(sx, y, sz, 21) < .7 else 'diorite')
    if not snapped:
        lay.put(x, height, z, 'oxidized_chiseled_copper')
        if rod:
            lay.put(x, height + 1, z, 'oxidized_lightning_rod[facing=up]')


def fallen_stone(lay, x, z, length, direction):
    for k in range(length):
        cx, cz = x + direction[0] * k, z + direction[1] * k
        lay.put(cx, 1, cz, 'calcite' if noise(cx, 1, cz, 22) < .7 else 'polished_diorite')
        if k == length - 1:
            lay.put(cx, 1, cz, 'oxidized_chiseled_copper')


def windcaller_shrine(lay, rng, rim, centre, tops, obstacles):
    """A paved wind rose and a ring of copper-capped stones; its outer half fell with the cliff."""
    sx, sz = centre
    for x in range(sx - 7, sx + 8):
        for z in range(sz - 7, sz + 8):
            if tops.get((x, z)) != 0:
                continue
            d = math.hypot(x - sx, z - sz)
            if d > 6.6 or noise(x, 0, z, 23) < .12 * d / 6.6:
                continue
            a = math.atan2(z - sz, x - sx)
            # Eight-pointed wind rose: long cardinal points, short diagonals.
            point = (1 - abs(angle_diff(a * 4, 0)) / math.pi * 1.0) * (5.6 if abs(angle_diff(a * 2, 0)) < .5 else 3.6)
            if d < 1.2 or d < point - .6:
                block = 'oxidized_cut_copper' if d < 4.6 or noise(x, 0, z, 24) < .5 else 'oxidized_copper'
            elif d > 5.6:
                block = 'polished_diorite'
            else:
                block = 'calcite' if noise(x, 0, z, 25) < .7 else 'diorite'
            if noise(x, 0, z, 26) < .07:
                block = 'cobblestone'
            lay.put(x, 0, z, block)
    lay.put(sx, 1, sz, 'polished_diorite')
    lay.put(sx, 2, sz, 'bell[attachment=floor,facing=east]')
    obstacles[sx, sz] = 2
    slots = 10
    for i in range(slots):
        a = i * 2 * math.pi / slots + .2
        x, z = round(sx + 7.6 * math.cos(a)), round(sz + 7.6 * math.sin(a))
        if rim.depth(x, z) > -1.5 or tops.get((x, z)) != 0:
            continue  # fell with the cliff
        tangent = (round(-math.sin(a)), round(math.cos(a))) or (1, 0)
        tangent = tangent if tangent != (0, 0) else (1, 0)
        kind = rng.choice(('stand', 'stand', 'snapped', 'fallen'))
        if kind == 'fallen':
            out = (round(math.cos(a + rng.uniform(-.4, .4))), round(math.sin(a + rng.uniform(-.4, .4))))
            fallen_stone(lay, x, z, 4, out if out != (0, 0) else (1, 0))
            for k in range(4):
                obstacles[x + out[0] * k, z + out[1] * k] = 1
        else:
            h = rng.randint(4, 5) if kind == 'stand' else rng.randint(1, 2)
            standing_stone(lay, x, z, h, tangent, snapped=kind == 'snapped')
            obstacles[x, z] = obstacles[x + tangent[0], z + tangent[1]] = h
            if kind == 'snapped':
                fallen_stone(lay, x + 2 * tangent[0] - tangent[1], z + 2 * tangent[1] + tangent[0], 2, tangent)


def lone_stone(lay, rng, rim, az, kind, tops, obstacles):
    a = rad(az)
    r = rim.radius(a) - (5 if kind == 'promontory' else 3.5)
    x, z = round(r * math.cos(a)), round(r * math.sin(a))
    tangent = (round(-math.sin(a)), round(math.cos(a)))
    tangent = tangent if tangent != (0, 0) else (1, 0)
    if tops.get((x, z)) != 0:
        return
    if kind == 'snapped':
        standing_stone(lay, x, z, 2, tangent, snapped=True)
        fallen_stone(lay, x - tangent[1] * 2, z + tangent[0] * 2, 3, tangent)
    else:
        standing_stone(lay, x, z, 4 if kind == 'promontory' else 5, tangent)
        if kind == 'pair':
            ox, oz = x - round(math.cos(a) * 4) + tangent[0] * 3, z - round(math.sin(a) * 4) + tangent[1] * 3
            fallen_stone(lay, ox, oz, 4, tangent)
    obstacles[x, z] = 3


# ---------------------------------------------------------------- remains

def remains(lay, rng, rim, cracks, tops, obstacles, avoid):
    """Bones, a rusted helm, a broken sword and the Roc's feathers left on the rock."""
    placed = set()

    def free(x, z):
        return tops.get((x, z)) == 0 and (x, z) not in obstacles and (x, z) not in placed and lay.get(x, 1, z) is None

    def put(x, z, state):
        if free(x, z):
            lay.put(x, 1, z, state)
            placed.add((x, z))
            return True
        return False
    facing = lambda: rng.choice(('north', 'east', 'south', 'west'))
    sites = scatter(rng, 5, OPEN_R + 2, lambda az: rim.radius(az) - 5, 16, avoid)
    kinds = ['skeleton', 'helm', 'sword', 'skeleton', 'helm']
    for (sx, sz), kind in zip(sites, kinds):
        x, z = round(sx), round(sz)
        if kind == 'skeleton':
            put(x, z, f'elementalwands:old_ribcage[facing={facing()}]')
            put(x + 1, z + rng.choice((-1, 1)), 'skeleton_skull[rotation=%d]' % rng.randrange(16))
            put(x - 1, z + rng.choice((-1, 0, 1)), f'elementalwands:old_bones[facing={facing()}]')
        elif kind == 'helm':
            put(x, z, f'elementalwands:rusted_helm[facing={facing()}]')
            put(x + rng.choice((-2, 2)), z + rng.choice((-1, 1)), f'elementalwands:old_bones[facing={facing()}]')
        else:
            put(x, z, f'elementalwands:broken_sword[facing={facing()}]')
            put(x + rng.choice((-2, 2)), z + rng.choice((-2, 2)), f'elementalwands:sword_tip[facing={facing()}]')
            put(x + rng.choice((-1, 1)), z - 2, 'skeleton_skull[rotation=%d]' % rng.randrange(16))
    # A skull fallen into a fissure.
    crack_cells = sorted(c for c, role in cracks.items() if role == 'core' and math.hypot(*c) > OPEN_R + 4)
    for c in rng.sample(crack_cells, min(2, len(crack_cells))):
        put(c[0], c[1], 'skeleton_skull[rotation=%d]' % rng.randrange(16))
    # Feathers: some caught upright in the cracks, some lying loose on the rock.
    for c in rng.sample(crack_cells, min(4, len(crack_cells))):
        put(c[0], c[1], f'elementalwands:roc_feather[facing={facing()},stuck=true]')
    for fx, fz in scatter(rng, 4, OPEN_R - 4, lambda az: rim.radius(az) - 3, 14, avoid):
        put(round(fx), round(fz), f'elementalwands:roc_feather[facing={facing()},stuck=false]')
    return placed


# ---------------------------------------------------------------- spires and peaks

def spire_column(rng, x0, z0, base_y, top_y, r_base, r_top, lean, lean_dir):
    """Centre and radius of a leaning spire at each height."""
    span = top_y - base_y
    wob = rng.uniform(0, 6.3)

    def at(y):
        f = (y - base_y) / span
        cx = x0 + math.cos(lean_dir) * lean * f ** 1.4 + .6 * math.sin(f * 7 + wob)
        cz = z0 + math.sin(lean_dir) * lean * f ** 1.4 + .6 * math.cos(f * 6 + wob)
        return cx, cz, r_top + (r_base - r_top) * (1 - f) ** 1.25
    return at


def spire_rock(x, y, z, salt, phi=0.0):
    """Dark tuff streaked top to bottom by weathering, unlike the pale summit. The streaks
    run down the rock, never around it, so a spire never reads as stacked courses."""
    streak = vnoise(phi * 5 + salt % 13, y / 14, salt) * .7 + vnoise(x / 3, z / 3, salt + 2) * .3
    rock = 'deepslate' if streak < .3 else 'andesite' if streak > .72 else 'tuff'
    r = noise(x, y, z, salt + 1)
    if r < .08:
        return 'cobbled_deepslate'
    if r < .13:
        return 'andesite' if rock == 'tuff' else 'tuff'
    return rock


def build_spire(lay, rng, rim, name, az, gap, height):
    """A thin, leaning needle of dark rock rising out of the clouds, with an off-centre
    capstone to perch on. Lumpy cross-sections, fissures and ledges keep it from reading
    as a built tower."""
    a = rad(az)
    top = height
    neck_y = top - 6
    salt = rng.randint(100, 9999)
    lean_dir = a + rng.uniform(-1.4, 1.4)
    lean = rng.uniform(2.5, 4.5)
    bend = rng.uniform(0, 6.3)
    span = top - BOTTOM

    def axis(y, x0, z0):
        f = (y - BOTTOM) / span
        side = 1.3 * math.sin(f * 3.6 + bend) * f
        return (x0 + math.cos(lean_dir) * lean * f ** 1.3 - math.sin(lean_dir) * side,
                z0 + math.sin(lean_dir) * lean * f ** 1.3 + math.cos(lean_dir) * side)

    def radius(y):
        f = min(1.0, (y - BOTTOM) / (neck_y - BOTTOM))
        return 3.8 + (10.5 - 3.8) * (1 - f) ** 1.2
    # Place the base so the face at summit height stands `gap` blocks off the rim.
    centre_r = rim.radius(a) + gap + radius(0)
    x0, z0 = centre_r * math.cos(a), centre_r * math.sin(a)
    for _ in range(3):
        cx, cz = axis(0, x0, z0)
        miss = gap - (math.hypot(cx, cz) - radius(0) - rim.radius(math.atan2(cz, cx)))
        x0 += math.cos(a) * miss
        z0 += math.sin(a) * miss
    fissures = [rng.uniform(0, 2 * math.pi) for _ in range(rng.randint(2, 3))]
    ledges = [(rng.randint(-24, neck_y - 8), rng.uniform(0, 2 * math.pi)) for _ in range(3)]
    ncx, ncz = axis(neck_y, x0, z0)
    cap_dir = rng.uniform(0, 2 * math.pi)
    capx, capz = ncx + math.cos(cap_dir) * 1.2, ncz + math.sin(cap_dir) * 1.2
    # The capstone is sized for the Roc's grip at ROC_SCALE: its feet cover about 6 x 8.
    cap_profile = {top - 5: 4.6, top - 4: 5.6, top - 3: 6.3, top - 2: 6.8, top - 1: 6.9, top: 6.6}
    scar_dir = rng.uniform(0, 2 * math.pi)
    for y in range(BOTTOM, top + 1):
        if y in cap_profile:
            cx, cz, r = capx, capz, cap_profile[y]
        else:
            cx, cz = axis(min(y, neck_y), x0, z0)
            r = radius(y)
        twist = y * .045 + bend
        R = int(r * 1.5 + 3)
        for x in range(math.floor(cx) - R, math.floor(cx) + R + 2):
            for z in range(math.floor(cz) - R, math.floor(cz) + R + 2):
                dx, dz = x + .5 - cx, z + .5 - cz
                d = math.hypot(dx, dz)
                phi = math.atan2(dz, dx)
                # An oval, lumpy cross-section that turns slowly with height.
                lump = fbm(math.cos(phi) * 1.7 + y / 6, math.sin(phi) * 1.7 + salt % 97, salt, 2)
                rr = r * (1 + .2 * math.cos(2 * (phi - twist))) * (.72 + .56 * lump)
                if y in cap_profile:
                    rr = r * (.9 + .2 * fbm(math.cos(phi) * 2 + salt % 31, math.sin(phi) * 2, salt + 5, 2))
                else:
                    for fa in fissures:
                        off = abs(angle_diff(phi, fa + y * .02))
                        if off < .16 and r > 3:
                            rr -= 1.3 * (1 - off / .16)
                    for ly, la in ledges:
                        off = abs(angle_diff(phi, la))
                        if ly <= y <= ly + 1 and off < .9:
                            rr += 1.6 * (1 - off / .9)
                if d > rr or (d < rr - 2.4 and y not in (top, BOTTOM)):
                    continue
                block = spire_rock(x, y, z, salt, phi)
                if y in cap_profile:
                    block = 'cobbled_deepslate' if noise(x, y, z, 33) < .5 else 'deepslate'
                # A scorch streak runs down the side the old strikes ran.
                if y > top - 18 and abs(angle_diff(phi, scar_dir)) < .35 + .25 * noise(x, y, z, 34) and d > rr - 1.2:
                    block = 'blackstone' if noise(x, y, z, 35) < .6 else 'smooth_basalt'
                lay.put(x, y, z, block)
    # The cap: blackened rock and fused glass where the strikes land, snow elsewhere.
    # The Roc grips its far edge, facing the summit, with its tail hanging off the back.
    out = math.atan2(capz, capx)
    px, pz = math.floor(capx + math.cos(out) * 1.5), math.floor(capz + math.sin(out) * 1.5)
    cx0, cz0 = math.floor(capx), math.floor(capz)
    for (x, y, z) in [q for q in lay.b if q[1] == top]:
        if abs(x - cx0) > 9 or abs(z - cz0) > 9:
            continue
        d = math.hypot(x - cx0, z - cz0)
        if d < 1.2:
            lay.put(x, y, z, 'obsidian')
        elif d < 3 and noise(x, y, z, 36) < .6:
            lay.put(x, y, z, 'blackstone')
        elif noise(x, y, z, 37) < .75:
            lay.put(x, y + 1, z, 'snow[layers=%d]' % (1 + (noise(x, y, z, 38) < .5)))
    sx, sz = axis(0, x0, z0)
    surface_gap = math.hypot(sx, sz) - radius(0) - rim.radius(math.atan2(sz, sx))
    cap = 2 * max(math.hypot(x + .5 - capx, z + .5 - capz) for (x, y, z) in lay.b if y == top and math.hypot(x - cx0, z - cz0) < 10)
    return {'name': name, 'perch': [px, top + 1, pz], 'height': top, 'gap': round(surface_gap, 1), 'cap': round(cap)}


def build_needle(lay, rng, az, dist, top):
    a = rad(az)
    x0, z0 = dist * math.cos(a), dist * math.sin(a)
    col = spire_column(rng, x0, z0, BOTTOM, top, 5.5, 1.2, rng.uniform(1, 2.5), rng.uniform(0, 6.3))
    for y in range(CLOUD_Y - 6, top + 1):
        cx, cz, r = col(y)
        R = int(r + 2)
        for x in range(math.floor(cx) - R, math.floor(cx) + R + 2):
            for z in range(math.floor(cz) - R, math.floor(cz) + R + 2):
                d = math.hypot(x + .5 - cx, z + .5 - cz)
                if d <= r + .4 * (noise(x, y, z, 40) - .5) and (d > r - 2 or y == top):
                    lay.put(x, y, z, spire_rock(x, y, z, 77, math.atan2(z + .5 - cz, x + .5 - cx)))


def build_peak(lay, az, dist, top, spread):
    """A distant mountain top breaking through the clouds: sharp ridges, snow above a line."""
    a = rad(az)
    px, pz = dist * math.cos(a), dist * math.sin(a)
    low = CLOUD_Y - 6
    base_r = ((top - low) * .62 + 5) * spread
    ridges = 3 + int(noise(round(px), 0, round(pz), 44) * 3)
    turn = noise(round(px), 1, round(pz), 45) * 6.3
    tops = {}
    R = round(base_r * 1.5) + 2
    for x in range(round(px) - R, round(px) + R + 1):
        for z in range(round(pz) - R, round(pz) + R + 1):
            dx, dz = x - px, z - pz
            phi = math.atan2(dz, dx)
            # Arêtes run out along a few ridges; the faces between them fall away faster.
            ridge = .55 * max(0.0, math.cos(ridges * (phi - turn) / 2) ** 2) ** 3
            f = math.hypot(dx, dz) / (base_r * (1 + ridge))
            t = round(top - (top - low) * f ** .9 + 2.5 * (fbm(x / 5, z / 5, 41) - .5) * 2)
            if t >= low:
                tops[x, z] = t
    for (x, z), t in tops.items():
        lowest = min(tops.get((x + dx, z + dz), low) for dx, dz in SIDES)
        for y in range(max(low, min(lowest, t) - 2), t + 1):
            lay.put(x, y, z, strata(x, y, z))
        snowline = top - 10 + 3 * (fbm(x / 6, z / 6, 42) - .5) * 2
        if t > snowline and lowest >= t - 2:
            lay.put(x, t, z, 'snow_block')
        elif t > snowline - 5 and lowest >= t - 1 and noise(x, t, z, 43) < .5:
            lay.put(x, t + 1, z, 'snow[layers=2]')


# ---------------------------------------------------------------- small models

RUST = 'ew_rusted_iron'
STEEL = 'ew_old_steel'
GRIP = 'ew_old_grip'
FEATHER = 'ew_roc_feather'
BONE = 'bone_block_side'


def el(frm, to, tex, rot=None, uv=None, faces=None):
    return {'from': frm, 'to': to, 'tex': tex, 'rot': rot, 'uv': uv or {}, 'faces': faces}


def _ribs():
    out = [el([7.25, 0, 1], [8.75, 1.5, 15], BONE)]
    for z in (3, 6, 9, 12):
        out.append(el([2.5, 0.2, z], [7.25, 1.4, z + 1.2], BONE, ('z', -22.5, [7.25, 1, z])))
        out.append(el([8.75, 0.2, z], [13.5, 1.4, z + 1.2], BONE, ('z', 22.5, [8.75, 1, z])))
    return out


FEATHER_UV = [4, 1, 12, 15]   # the vane in the 32 px feather texture
MODELS = {
    'roc_feather/flat': [
        el([3, .3, -6], [13, .3, 22], FEATHER, ('y', 22.5, [8, 0, 8]), {'up': FEATHER_UV, 'down': FEATHER_UV}, ('up', 'down')),
        el([7.6, .2, -8], [8.4, .9, 21], BONE, ('y', 22.5, [8, 0, 8])),
    ],
    'roc_feather/stuck': [
        el([3, -4, 8], [13, 24, 8], FEATHER, ('x', -22.5, [8, 0, 8]), {'north': FEATHER_UV, 'south': FEATHER_UV}, ('north', 'south')),
        el([7.6, -6, 7.6], [8.4, 23, 8.4], BONE, ('x', -22.5, [8, 0, 8])),
    ],
    'rusted_helm': [
        el([4.5, 0, 4.5], [11.5, 7, 11.5], RUST, ('z', 22.5, [8, 0, 8]), {'north': [0, 0, 7, 7]}),
        el([5.5, 7, 5.5], [10.5, 8, 10.5], RUST, ('z', 22.5, [8, 0, 8])),
    ],
    'broken_sword': [
        el([7.25, -4, 7.5], [8.75, 10, 8.5], STEEL, ('x', 22.5, [8, 0, 8])),
        el([4.5, 10, 7], [11.5, 11.5, 9], RUST, ('x', 22.5, [8, 0, 8])),
        el([7.5, 11.5, 7.5], [8.5, 15.5, 8.5], GRIP, ('x', 22.5, [8, 0, 8])),
        el([7, 15.5, 7], [9, 17, 9], RUST, ('x', 22.5, [8, 0, 8])),
    ],
    'sword_tip': [
        el([7.25, 0, 3], [8.75, 1, 12], STEEL, ('y', 22.5, [8, 0, 8])),
        el([7.6, 0, 1], [8.4, 1, 3], STEEL, ('y', 22.5, [8, 0, 8])),
    ],
    'old_bones': [
        el([3, 0, 4], [4.5, 1.5, 13], BONE, ('y', 22.5, [4, 0, 8])),
        el([2.5, 0, 3], [5, 2, 5], BONE, ('y', 22.5, [4, 0, 8])),
        el([9, 0, 2], [10.5, 1.2, 10], BONE, ('y', -45, [10, 0, 6])),
        el([6, 0, 12], [13, 1.2, 13.2], BONE),
    ],
    'old_ribcage': _ribs(),
    # Vanilla blocks drawn by entity or special models in game; shapes for the preview.
    'skeleton_skull': [el([4, 0, 4], [12, 8, 12], 'skull', None, {}, None)],
    'bell': [
        el([2, 0, 6.5], [4, 13, 9.5], 'stone'), el([12, 0, 6.5], [14, 13, 9.5], 'stone'),
        el([2, 13, 7], [14, 15, 9], 'dark_oak_planks'),
        el([5, 6, 5], [11, 12, 11], 'gold_block'), el([4, 4, 4], [12, 6, 12], 'gold_block'),
    ],
    'oxidized_lightning_rod': [
        el([6, 12, 6], [10, 16, 10], 'oxidized_lightning_rod', None, {f: [0, 0, 4, 4] for f in ('north', 'south', 'east', 'west', 'up', 'down')}),
        el([7, 0, 7], [9, 12, 9], 'oxidized_lightning_rod', None, {f: [0, 4, 2, 16] for f in ('north', 'south', 'east', 'west')} | {'up': [0, 4, 2, 6], 'down': [0, 4, 2, 6]}),
    ],
}
FACING_YAW = {'north': 0, 'east': 90, 'south': 180, 'west': 270}


def model_for(state):
    """(model name, yaw degrees) for a block drawn as a small model, or None."""
    name = state.split(':', 1)[1].split('[')[0]
    props = dict(p.split('=') for p in state[state.index('[') + 1:-1].split(',')) if '[' in state else {}
    if name == 'roc_feather':
        return 'roc_feather/' + ('stuck' if props.get('stuck') == 'true' else 'flat'), FACING_YAW[props['facing']]
    if name == 'skeleton_skull':
        return name, int(props.get('rotation', 0)) * 22.5
    if name in MODELS:
        return name, FACING_YAW.get(props.get('facing'), 0)
    return None


def paint_textures(archive):
    """The generated textures the small models use (32 px, like the atlas tiles)."""
    def vanilla(name):
        return Image.open(io.BytesIO(archive.read(f'assets/minecraft/textures/block/{name}.png'))).convert('RGBA').crop((0, 0, 16, 16))
    out = {}
    iron = vanilla('iron_block')
    rust = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            v = sum(iron.getpixel((x, y))[:3]) / 765
            n = noise(x, y, 0, 50)
            c = (142, 74, 40) if n < .35 else (110, 58, 34) if n < .7 else (160, 110, 72)
            rust.putpixel((x, y), tuple(int(ch * (.6 + .5 * v)) for ch in c) + (255,))
    helm = rust.copy()
    for x in range(1, 6):
        helm.putpixel((x, 3), (24, 16, 12, 255))
    for y in range(3, 6):
        helm.putpixel((3, y), (24, 16, 12, 255))
    out[RUST] = rust
    out[RUST + '_face'] = helm
    steel = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            v = sum(iron.getpixel((x, y))[:3]) / 765
            n = noise(x, y, 1, 51)
            steel.putpixel((x, y), (int(150 * v + 40 + 30 * n), int(150 * v + 40 + 22 * n), int(155 * v + 44 + 10 * n), 255))
    out[STEEL] = steel
    grip = Image.new('RGBA', (16, 16))
    for x in range(16):
        for y in range(16):
            band = (y % 3 == 0)
            grip.putpixel((x, y), (58, 36, 24, 255) if band else (86, 54, 34, 255))
    out[GRIP] = grip
    # A long Roc feather: red-pink at the base fading to white at the tip, barred.
    f = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
    for y in range(2, 30):
        t = (29 - y) / 27          # 0 at the base (bottom), 1 at the tip
        half = 7.5 * math.sin(min(1, (t + .08) * 1.05) * math.pi) ** .55 if t < .97 else 1
        base = (214, 66, 92) if t < .35 else (236, 140, 158) if t < .62 else (246, 236, 234)
        for x in range(32):
            dx = x + .5 - 16
            if abs(dx) > half:
                continue
            c = list(base)
            if (y + (2 if dx < 0 else 0)) % 6 == 0 and t < .8:
                c = [int(v * .82) for v in c]
            if abs(dx) > half - 1.2:
                c = [int(v * .86) for v in c]
            if abs(dx) < .8:
                c = [244, 232, 214]
            f.putpixel((x, y), tuple(c) + (255,))
    out[FEATHER] = f
    skel = Image.open(io.BytesIO(archive.read('assets/minecraft/textures/entity/skeleton/skeleton.png'))).convert('RGBA')
    for face, box in {'front': (8, 8, 16, 16), 'side': (0, 8, 8, 16), 'top': (8, 0, 16, 8), 'back': (24, 8, 32, 16)}.items():
        out['skull_' + face] = skel.crop(box).resize((16, 16), Image.NEAREST)
    return out


def bake_model(name, x, y, z, yaw, tile):
    """Triangles (x, y, z, u, v, shade, tile) for one small model at a block cell."""
    tris = []
    for e in MODELS[name]:
        (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
        corners = {
            'north': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            'south': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
            'east': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
            'west': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
            'up': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            'down': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
        }
        auto = {'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0], 'south': [x0, 16 - y1, x1, 16 - y0],
                'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0], 'west': [z0, 16 - y1, z1, 16 - y0],
                'up': [x0, z0, x1, z1], 'down': [x0, 16 - z1, x1, 16 - z0]}
        shade = {'north': .8, 'south': .8, 'east': .6, 'west': .6, 'up': 1, 'down': .5}
        for face in e['faces'] or corners:
            tex = e['tex']
            if tex == 'skull':
                tex = {'north': 'skull_front', 'south': 'skull_back', 'up': 'skull_top', 'down': 'skull_top'}.get(face, 'skull_side')
            if tex == RUST and face == 'north' and name == 'rusted_helm' and e['from'][1] == 0:
                tex = RUST + '_face'
            u0, v0, u1, v1 = e['uv'].get(face) or [min(16, max(0, c)) for c in auto[face]]
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            pts = []
            for p in corners[face]:
                p = list(p)
                if e['rot']:
                    axis, ang, o = e['rot']
                    c, s = math.cos(rad(ang)), math.sin(rad(ang))
                    q = [p[i] - o[i] for i in range(3)]
                    if axis == 'x':
                        q = [q[0], q[1] * c - q[2] * s, q[1] * s + q[2] * c]
                    elif axis == 'y':
                        q = [q[0] * c + q[2] * s, q[1], -q[0] * s + q[2] * c]
                    else:
                        q = [q[0] * c - q[1] * s, q[0] * s + q[1] * c, q[2]]
                    p = [q[i] + o[i] for i in range(3)]
                # Block-state yaw turns the model about the cell's centre (clockwise from above).
                cy, sy = math.cos(rad(-yaw)), math.sin(rad(-yaw))
                qx, qz = p[0] - 8, p[2] - 8
                p = [qx * cy + qz * sy + 8, p[1], -qx * sy + qz * cy + 8]
                pts.append((x + p[0] / 16, y + p[1] / 16, z + p[2] / 16))
            t = tile(tex)
            for k in (0, 1, 2, 0, 2, 3):
                tris.append((*pts[k], uvs[k][0] / 16, uvs[k][1] / 16, shade[face], t))
    return tris


# ---------------------------------------------------------------- preview output

TEX_ALIAS = {'snow': ('snow',) * 3, 'snow_block': ('snow',) * 3, 'tuff': ('tuff',) * 3, 'bone_block': ('bone_block_top', 'bone_block_side', 'bone_block_top'),
             'smooth_basalt': ('smooth_basalt',) * 3, 'deepslate': ('deepslate_top', 'deepslate', 'deepslate_top'),
             'oxidized_chiseled_copper': ('oxidized_chiseled_copper',) * 3}


def material_key(state):
    base = state.split(':', 1)[1].split('[')[0]
    if base == 'snow':
        return 'snow@%s' % state.split('layers=')[1].rstrip(']')
    return base


def export(lay, facts):
    OUT.mkdir(parents=True, exist_ok=True)
    blocks, models = {}, []
    for p, s in lay.b.items():
        m = model_for(s)
        if m:
            models.append((p, *m))
        else:
            blocks[p] = s
    keys = sorted({material_key(s) for s in blocks.values()})
    tiles, tile_index = [], {}
    with zipfile.ZipFile(JAR) as archive:
        painted = paint_textures(archive)

        def tile(name):
            if name not in tile_index:
                tile_index[name] = len(tiles)
                if name in painted:
                    img = painted[name]
                else:
                    try:
                        img = Image.open(io.BytesIO(archive.read(f'assets/minecraft/textures/block/{name}.png'))).convert('RGBA').crop((0, 0, 16, 16))
                    except KeyError:
                        img = Image.new('RGBA', (16, 16), (255, 0, 255, 255))
                tiles.append(img.resize((32, 32), Image.NEAREST) if img.size[0] == 16 else img)
            return tile_index[name]
        materials = []
        for key in keys:
            base = key.split('@')[0]
            top, side, bottom = TEX_ALIAS.get(base, (base,) * 3)
            shape = 10 + int(key.split('@')[1]) if '@' in key else 0
            materials.append({'name': key, 'shape': shape, 'tex': [tile(top), tile(side), tile(bottom)]})
        tris = []
        for (x, y, z), name, yaw in models:
            tris += bake_model(name, x, y, z, yaw, tile)
    cols = 16
    atlas = Image.new('RGBA', (cols * 32, math.ceil(len(tiles) / cols) * 32))
    for i, img in enumerate(tiles):
        atlas.paste(img, (i % cols * 32, i // cols * 32))
    atlas.save(OUT / 'atlas.png')
    index = {k: i for i, k in enumerate(keys)}
    opaque = {i for i, m in enumerate(materials) if m['shape'] == 0}
    mat = {p: index[material_key(s)] for p, s in blocks.items()}
    raw = bytearray()
    count = 0
    for (x, y, z), m in mat.items():
        mask = 0
        for bit, (dx, dy, dz) in enumerate(((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            if mat.get((x + dx, y + dy, z + dz)) not in opaque or m not in opaque:
                mask |= 1 << bit
        if y == BOTTOM:
            mask &= ~(1 << 3)
        if mask:
            for v in (x, y, z, m | mask << 8):
                raw += v.to_bytes(2, 'little', signed=True)
            count += 1
    flat = bytearray()
    for t in tris:
        flat += b''.join(round(v * 4096).to_bytes(4, 'little', signed=True) for v in t[:5])
        flat += round(t[5] * 255).to_bytes(2, 'little') + t[6].to_bytes(2, 'little')
    data = {'materials': materials, 'atlasCols': cols, 'atlasRows': math.ceil(len(tiles) / cols),
            'summit': {'count': count, 'blocks': len(lay.b), 'instances': base64.b64encode(bytes(raw)).decode(),
                       'models': base64.b64encode(bytes(flat)).decode(), 'modelVerts': len(tris)},
            'facts': facts | {'rocScale': ROC_SCALE, 'radius': SUMMIT_R, 'cloud': CLOUD_Y, 'bottom': BOTTOM, 'arrival': ARRIVAL, 'open': OPEN_R}}
    (OUT / 'layouts.json').write_text(json.dumps(data, separators=(',', ':')))
    # The Roc, posed by the viewer for scale.
    anim = json.loads((ROC / 'storm_roc.animation.json').read_text())['animations']
    roc = {'geo': json.loads((ROC / 'storm_roc.geo.json').read_text()),
           'clips': {k.rsplit('.', 1)[1]: v for k, v in anim.items()}}
    (OUT / 'roc.json').write_text(json.dumps(roc, separators=(',', ':')))
    shutil.copy(ROC / 'storm_roc.png', OUT / 'roc.png')
    for f in ('viewer.template.html', 'viewer.js'):
        shutil.copy(HERE / f, OUT / ('index.html' if f.endswith('.html') else f))
    counts = {}
    for s in lay.b.values():
        k = material_key(s)
        counts[k] = counts.get(k, 0) + 1
    print(f'summit: {len(lay.b)} blocks, {count} drawn, {len(models)} small models; '
          + ', '.join(f'{k} {v}' for k, v in sorted(counts.items(), key=lambda kv: -kv[1])[:12]))
    for p in facts['perches']:
        print(f"  spire {p['name']}: perch {p['perch']}, {p['height']} above the summit, {p['gap']} from the rim, top {p['cap']} across")


# ---------------------------------------------------------------- mod resources

TILE = 48                # realm tiles are placed one per server tick
TILE_ORIGIN = -264       # tile (i, j) covers x/z from TILE_ORIGIN + TILE * index
TILE_COUNT = 11
RES = ROOT / 'src/main/resources'
STRUCTURES = RES / 'data/elementalwands/structure/storm_summit'
ASSETS = RES / 'assets/elementalwands'
LAYOUT_JAVA = ROOT / 'src/main/java/com/anton/elementalwands/summit/StormSummitLayout.java'
RELICS = ('roc_feather', 'rusted_helm', 'broken_sword', 'sword_tip', 'old_bones', 'old_ribcage')
TEXTURE_FILES = {FEATHER: 'roc_feather', RUST: 'rusted_iron', RUST + '_face': 'rusted_helm_face', STEEL: 'old_steel', GRIP: 'old_grip'}


def _crypt():
    """The crypt builder's vanilla structure writer; the realms share the format."""
    import importlib.util
    spec = importlib.util.spec_from_file_location('hollow_crypt_layout', ROOT / 'art/hollow_crypt/build_layout.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def realm_tiles(lay):
    structure_nbt = _crypt().structure_nbt
    tiles = {}
    for (x, y, z), state in lay.b.items():
        i, j = (x - TILE_ORIGIN) // TILE, (z - TILE_ORIGIN) // TILE
        assert 0 <= i < TILE_COUNT and 0 <= j < TILE_COUNT, (x, y, z)
        tiles.setdefault((i, j), {})[x - TILE_ORIGIN - i * TILE, y - BOTTOM, z - TILE_ORIGIN - j * TILE] = state
    return {STRUCTURES / f'summit_{i}_{j}.nbt': structure_nbt(blocks, (TILE, max(q[1] for q in blocks) + 1, TILE))
            for (i, j), blocks in sorted(tiles.items())}


def png(img):
    out = io.BytesIO()
    img.save(out, 'PNG', optimize=True)
    return out.getvalue()


def as_json(value):
    return (json.dumps(value, indent=2) + '\n').encode()


def model_json(name):
    textures, faces_tex = {}, {}
    def ref(tex):
        if tex not in faces_tex:
            key = 't%d' % len(faces_tex)
            faces_tex[tex] = key
            textures[key] = f'elementalwands:block/summit/{TEXTURE_FILES[tex]}' if tex in TEXTURE_FILES else f'minecraft:block/{tex}'
        return '#' + faces_tex[tex]
    elements = []
    for e in MODELS[name]:
        (x0, y0, z0), (x1, y1, z1) = e['from'], e['to']
        auto = {'north': [16 - x1, 16 - y1, 16 - x0, 16 - y0], 'south': [x0, 16 - y1, x1, 16 - y0],
                'east': [16 - z1, 16 - y1, 16 - z0, 16 - y0], 'west': [z0, 16 - y1, z1, 16 - y0],
                'up': [x0, z0, x1, z1], 'down': [x0, 16 - z1, x1, 16 - z0]}
        faces = {}
        for face in e['faces'] or ('north', 'south', 'east', 'west', 'up', 'down'):
            tex = e['tex']
            if tex == RUST and face == 'north' and name == 'rusted_helm' and y0 == 0:
                tex = RUST + '_face'
            uv = e['uv'].get(face) or [min(16, max(0, c)) for c in auto[face]]
            faces[face] = {'uv': [round(v, 3) for v in uv], 'texture': ref(tex)}
        el_json = {'from': e['from'], 'to': e['to'], 'faces': faces}
        if e['rot']:
            axis, angle, origin = e['rot']
            el_json['rotation'] = {'angle': angle, 'axis': axis, 'origin': origin}
        elements.append(el_json)
    textures['particle'] = textures['t0']
    return {'ambientocclusion': False, 'textures': textures, 'elements': elements}


def relic_assets():
    files = {}
    with zipfile.ZipFile(JAR) as archive:
        painted = paint_textures(archive)
    for tex, filename in TEXTURE_FILES.items():
        files[ASSETS / f'textures/block/summit/{filename}.png'] = png(painted[tex])
    for name in ('roc_feather/flat', 'roc_feather/stuck') + RELICS[1:]:
        files[ASSETS / f"models/block/summit/{name.replace('/', '_')}.json"] = as_json(model_json(name))
    for name in RELICS:
        variants = {}
        for facing, yaw in FACING_YAW.items():
            if name == 'roc_feather':
                for stuck in ('false', 'true'):
                    model = 'roc_feather_' + ('stuck' if stuck == 'true' else 'flat')
                    variants[f'facing={facing},stuck={stuck}'] = {'model': f'elementalwands:block/summit/{model}', **({'y': yaw} if yaw else {})}
            else:
                variants[f'facing={facing}'] = {'model': f'elementalwands:block/summit/{name}', **({'y': yaw} if yaw else {})}
        files[ASSETS / f'blockstates/{name}.json'] = as_json({'variants': variants})
    files[ASSETS / 'textures/environment/summit_clouds.png'] = png(cloud_texture(False))
    files[ASSETS / 'textures/environment/summit_wisps.png'] = png(cloud_texture(True))
    files[ASSETS / 'textures/environment/summit_storm.png'] = png(storm_texture(False))
    files[ASSETS / 'textures/environment/summit_scud.png'] = png(storm_texture(True))
    files[ASSETS / 'textures/environment/summit_cap.png'] = png(cap_texture())
    return files


def noise_sheet(size, period, salt):
    """Fractal value noise that wraps every `size` pixels, one value per pixel."""
    def tile_noise(x, y, salt, cells):
        fx, fy = x * cells / size, y * cells / size
        ix, iy = int(fx), int(fy)
        tx, ty = fx - ix, fy - iy
        tx, ty = tx * tx * (3 - 2 * tx), ty * ty * (3 - 2 * ty)
        n = lambda a, b: noise(a % cells, 0, b % cells, salt)
        return (n(ix, iy) * (1 - tx) + n(ix + 1, iy) * tx) * (1 - ty) + (n(ix, iy + 1) * (1 - tx) + n(ix + 1, iy + 1) * tx) * ty

    def field(x, y):
        total = norm = 0.0
        for o, cells in enumerate((period, period * 2, period * 4, period * 8)):
            amp = .5 ** o
            total += tile_noise(x, y, salt + o, cells) * amp
            norm += amp
        return total / norm
    return [[field(x, y) for x in range(size)] for y in range(size)]


def storm_texture(scud, size=256):
    """Phase two's storm, one pixel per block like the sea below: the heavy, billowed underside
    of the cloud ceiling, or the torn scud that races beneath it."""
    img = Image.new('RGBA', (size, size))
    h = noise_sheet(size, 16 if scud else 8, 95 if scud else 80)
    for y in range(size):
        for x in range(size):
            v = h[y][x]
            if scud:
                a = max(0.0, min(1.0, (v - .5) / .16)) * .8
                img.putpixel((x, y), (66, 72, 88, round(a * 255)))
                continue
            # Seen from below: billows catch a little light, the folds between them sink to slate.
            slope = h[(y - 1) % size][(x - 1) % size] - h[(y + 1) % size][(x + 1) % size]
            lit = max(0.0, min(1.0, .4 + slope * 6 + (v - .5) * 1.2))
            dark, light = (34, 38, 50), (98, 106, 126)
            img.putpixel((x, y), tuple(round(dark[k] + (light[k] - dark[k]) * lit) for k in range(3)) + (255,))
    return img


def cap_texture(size=256):
    """Phase one's cap cloud: a thin, broken grey deck over the summit that the lightning falls
    from. Seen from below, its billows are lit at the edges and grey in the folds, with gaps."""
    img = Image.new('RGBA', (size, size))
    h = noise_sheet(size, 8, 120)
    for y in range(size):
        for x in range(size):
            v = h[y][x]
            a = max(0.0, min(1.0, (v - .4) / .14)) * .88
            slope = h[(y - 1) % size][(x - 1) % size] - h[(y + 1) % size][(x + 1) % size]
            lit = max(0.0, min(1.0, .45 + slope * 6 + (v - .5) * 1.1))
            dark, light = (128, 136, 152), (222, 228, 238)
            img.putpixel((x, y), tuple(round(dark[k] + (light[k] - dark[k]) * lit) for k in range(3)) + (round(a * 255),))
    return img


def cloud_texture(wisps, size=256):
    """A tiling sheet for the sea of clouds, one pixel per block: lit, puffy tops for the
    base layer, or thin drifting wisps for the layer above it."""
    img = Image.new('RGBA', (size, size))
    h = noise_sheet(size, 8 if not wisps else 16, 60 + 9 * wisps)
    for y in range(size):
        for x in range(size):
            v = h[y][x]
            if wisps:
                a = max(0.0, min(1.0, (v - .55) / .14)) * .6
                img.putpixel((x, y), (255, 255, 255, round(a * 255)))
                continue
            # Light from the north-west: a slope facing it is bright, one facing away shaded.
            slope = h[(y - 1) % size][(x - 1) % size] - h[(y + 1) % size][(x + 1) % size]
            lit = max(0.0, min(1.0, .62 + slope * 7 + (v - .5) * .8))
            shade = (168, 186, 214)
            top = (255, 254, 250)
            c = tuple(round(shade[k] + (top[k] - shade[k]) * lit) for k in range(3))
            img.putpixel((x, y), c + (255,))
    return img


def rim_table(rim):
    return [round(rim.radius(rad(a)), 2) for a in range(0, 360, 5)]


def layout_java(lay, facts, rim):
    reach = max(max(abs(x), abs(z)) for x, y, z in lay.b)
    top = max(y for x, y, z in lay.b)
    perches = ',\n'.join('        {%d, %d, %d}' % tuple(p['perch']) for p in facts['perches'])
    rims = ', '.join(str(r) for r in rim_table(rim))
    return f"""package com.anton.elementalwands.summit;

/**
 * Generated by art/storm_summit/build_layout.py; do not edit. Offsets are blocks from the summit's
 * centre, with y above the summit surface (players stand at y=1).
 */
public final class StormSummitLayout {{
    private StormSummitLayout() {{}}

    /** Lowest authored block, and the top of the sea of clouds. */
    public static final int BOTTOM = {BOTTOM}, CLOUD_TOP = {CLOUD_Y};
    /** Highest authored block and the farthest one from the centre on either axis. */
    public static final int TOP = {top}, REACH = {reach};
    public static final int TILE = {TILE}, TILE_ORIGIN = {TILE_ORIGIN}, TILE_COUNT = {TILE_COUNT};
    /** The Roc is drawn at this multiple of its art study; the capstones are sized for its grip. */
    public static final double ROC_SCALE = {ROC_SCALE};
    public static final int[] ARRIVAL = {{{ARRIVAL[0]}, {ARRIVAL[1]}, {ARRIVAL[2]}}};
    /** Where the Roc stands on each spire, home spire first; it faces the summit's centre. */
    public static final int[][] PERCHES = {{
{perches}
    }};
    /** The rim's radius every 5 degrees, starting east (+x) and turning toward south (+z). */
    public static final double[] RIM = {{{rims}}};
}}
"""


def install_files():
    summit, facts = build_summit()
    files = realm_tiles(summit)
    files.update(relic_assets())
    files[LAYOUT_JAVA] = layout_java(summit, facts, Rim(random.Random(SEED))).encode()
    return summit, files


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--serve', action='store_true')
    ap.add_argument('--install', action='store_true', help='write the realm tiles, layout constants and block assets into the mod')
    ap.add_argument('--check', action='store_true', help='verify the installed files match this builder')
    args = ap.parse_args()
    if args.install or args.check:
        summit, files = install_files()
        stale = set(STRUCTURES.glob('summit_*.nbt')) - set(files)
        if args.check:
            for path, data in files.items():
                assert path.exists() and path.read_bytes() == data, f'Drift: {path.relative_to(ROOT)}'
            assert not stale, f'Unexpected tiles: {sorted(p.name for p in stale)}'
            print(f'Storm Summit verified: {sum(1 for f in files if f.suffix == ".nbt")} realm tiles ({len(summit.b)} blocks) and {len(files)} files')
        else:
            for path in stale:
                path.unlink()
            for path, data in files.items():
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
            print(f'Installed {len(files)} files ({sum(map(len, files.values())) // 1024} KiB)')
        raise SystemExit
    summit, facts = build_summit()
    export(summit, facts)
    print(f'Preview written to {OUT.relative_to(ROOT)}/index.html')
    if args.serve:
        import functools, http.server

        class NoCache(http.server.SimpleHTTPRequestHandler):
            def end_headers(self):
                self.send_header('Cache-Control', 'no-store'); super().end_headers()
        http.server.ThreadingHTTPServer(('127.0.0.1', 8390), functools.partial(NoCache, directory=str(OUT))).serve_forever()
