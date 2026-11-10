#!/usr/bin/env python3
"""Authors the Burned Throne Hall, the Pyre King's realm: the round throne hall he set alight
rather than surrender, roofless now, on a crag in a Nether lava sea.

    py -3 art/pyre_hall/build_layout.py            # writes the browser preview
    py -3 art/pyre_hall/build_layout.py --serve    # ...and serves it on 127.0.0.1:8420
    py -3 art/pyre_hall/build_layout.py --install  # writes the structure tiles into the mod
    py -3 art/pyre_hall/build_layout.py --check    # verifies the installed tiles

Coordinates are integer block cells relative to the hall centre, which is centred ON block
(0, 0): a cell's distance from the centre is hypot(x, z). y=0 is the fight floor block, so
players stand at y=1. +Z is south, -Z north, +X east. Azimuth a points at (cos a, sin a), so
90 is south (the gate) and 270 north (the throne). The game's dimension supplies the lava
sea; the layout holds no lava. The preview lives in the ignored .local-previews/pyre-hall/.
"""
import argparse, base64, io, json, math, random, shutil, zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
OUT = ROOT / '.local-previews/pyre-hall'
JAR = Path.home() / '.gradle/caches/fabric-loom/1.21.10/minecraft-client.jar'
KING_ART = ROOT / 'art/pyre_king/candidate'

# ---- contract with the game code (do not change) ----
FLOOR_R = 40           # fight floor: every cell with r <= FLOOR_R + .5, flat at y=0
PLAY_CEILING = 32      # barrier lid at PLAY_CEILING + 1 over r <= FLOOR_R + 1.5
ARRIVAL = (0, 1, 33)   # players arrive here facing north
KING = (0, 1, 0)       # he stands at the centre facing south
BOTTOM = -16           # lowest authored y
LAVA_Y = -14           # top lava block of the dimension's sea

# ---- the hall ----
STEP_R = FLOOR_R + 1.5         # the 1-high step ring that carries the invisible wall
WALL_IN, WALL_OUT = 48.5, 52.5
WALL_H = 30
WALL_FOOT = -3
GALLERY_TOP = 2
PILLAR_R = 20
PILLARS = {0: 12, 60: 5, 120: 8, 180: 17, 240: 34, 300: 24}   # azimuth -> y of the highest block
WHOLE_PILLAR = 240
SHAFT_R, BASE_R = 3.3, 4.4
BAY = 22.5
GATE_AZ, THRONE_AZ = 90.0, 270.0
BREACHES = {22.5: (4, 11.0), 157.5: (7, 8.5), 315.0: (16, 6.0)}   # azimuth -> (lowest y, half-width at WALL_H)
APSE = (0, -50)
APSE_IN, APSE_OUT = 8.5, 12.5
ROSE_Y, ROSE_R = 38, 4.0       # high enough that its gold ring clears the throne's peak
RING_R, RING_Y = 9, 62
WHOLE_RIBS = (165, 306)        # piers in this azimuth range keep their whole rib and the ring
HANG_FLOOR = 36                # nothing hangs lower than this over r <= STEP_R
SEED = 0x9A1E

# ---- realm tiles (do not change) ----
TILE = 48
TILE_ORIGIN = -120
TILE_COUNT = 5
LO, HI = TILE_ORIGIN, TILE_ORIGIN + TILE * TILE_COUNT - 1

SIDES = ((1, 0), (-1, 0), (0, 1), (0, -1))
ALLOWED = {
    'polished_blackstone_bricks', 'cracked_polished_blackstone_bricks', 'polished_blackstone',
    'chiseled_polished_blackstone', 'blackstone', 'gilded_blackstone', 'polished_basalt', 'smooth_basalt',
    'basalt', 'coal_block', 'nether_bricks', 'red_nether_bricks', 'chiseled_nether_bricks',
    'cracked_nether_bricks', 'gold_block', 'raw_gold_block', 'magma_block', 'netherrack', 'fire',
    'iron_chain', 'lantern', 'iron_bars', 'nether_brick_fence', 'barrier', 'light'}


class Layout:
    def __init__(self):
        self.b = {}

    def put(self, x, y, z, state, replace=True):
        state = state if ':' in state else 'minecraft:' + state
        if replace or (x, y, z) not in self.b:
            self.b[x, y, z] = state

    def get(self, x, y, z):
        return self.b.get((x, y, z))

    def name(self, x, y, z):
        s = self.b.get((x, y, z))
        return s.split(':', 1)[1].split('[')[0] if s else None

    def remove(self, x, y, z):
        self.b.pop((x, y, z), None)


def noise(x, y, z, salt=0):
    """Deterministic per-cell value in [0, 1]."""
    n = (x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ salt * 2654435761) & 0xffffffff
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffffff) / 0xffffff


def smooth(t):
    return t * t * (3 - 2 * t)


def vnoise(x, z, cell, salt=0):
    """Smooth value noise in [0, 1] on a grid of the given cell size."""
    fx, fz = x / cell, z / cell
    ix, iz = math.floor(fx), math.floor(fz)
    tx, tz = smooth(fx - ix), smooth(fz - iz)
    n = lambda a, b: noise(a, 0, b, salt)
    return (n(ix, iz) * (1 - tx) + n(ix + 1, iz) * tx) * (1 - tz) + (n(ix, iz + 1) * (1 - tx) + n(ix + 1, iz + 1) * tx) * tz


def anoise(a, period, salt=0):
    """Smooth noise in [0, 1] around the circle, one knot every `period` degrees."""
    n = round(360 / period)
    f = (a % 360) / (360 / n)
    i = math.floor(f)
    t = smooth(f - i)
    return noise(i % n, 0, 0, salt) * (1 - t) + noise((i + 1) % n, 0, 0, salt) * t


def az_of(x, z):
    return math.degrees(math.atan2(z, x)) % 360


def adiff(a, b):
    return (a - b + 180) % 360 - 180


def tang(x, z, a):
    """Signed distance along the tangent at azimuth a (toward increasing azimuth)."""
    r = math.radians(a)
    return -x * math.sin(r) + z * math.cos(r)


def depth(x, z, a):
    r = math.radians(a)
    return x * math.cos(r) + z * math.sin(r)


def polar(r, a):
    a = math.radians(a)
    return r * math.cos(a), r * math.sin(a)


def cracked(state):
    if 'nether_bricks' in state and 'red' not in state and 'chiseled' not in state:
        return 'cracked_nether_bricks'
    if state.endswith('polished_blackstone_bricks'):
        return 'cracked_polished_blackstone_bricks'
    return state


def char(x, y, z, salt=0):
    r = noise(x, y, z, salt)
    return 'coal_block' if r < .38 else 'blackstone' if r < .75 else 'smooth_basalt'


def masonry(x, y, z, salt=0):
    """Burnt royal ashlar for the walls."""
    r = noise(x, y, z, salt)
    if r < .17:
        return 'cracked_polished_blackstone_bricks'
    if r < .19:
        return 'blackstone'
    if r < .2:
        return 'gilded_blackstone'
    if r < .23:
        return 'polished_blackstone'
    return 'polished_blackstone_bricks'


def rubble(x, y, z, salt=0):
    r = noise(x, y, z, salt)
    return ('blackstone' if r < .3 else 'cracked_polished_blackstone_bricks' if r < .55 else 'polished_blackstone_bricks' if r < .68
            else 'basalt[axis=y]' if r < .8 else 'coal_block' if r < .88 else 'polished_basalt[axis=y]' if r < .95 else 'gilded_blackstone')


# ---------------------------------------------------------------- the fight floor

# The floor's inlaid lines: pale enough against the brick to read from above in the game's light.
LINE = 'smooth_basalt'


def floor_pattern(x, z):
    r = math.hypot(x, z)
    a = az_of(x, z)
    soot = vnoise(x, z, 6, 1) * .7 + vnoise(x, z, 2.5, 2) * .3
    if r <= 6.5:
        n = noise(x, 0, z, 3) * .6 + vnoise(x, z, 2, 4) * .4
        return 'coal_block' if n < .42 else 'blackstone' if n < .72 else 'smooth_basalt'
    if r <= 8.5:
        seg = int(a // 15)
        return 'gilded_blackstone' if seg % 2 == 0 else 'chiseled_polished_blackstone'
    if r > FLOOR_R - 1.5:
        return 'chiseled_polished_blackstone'
    if r > FLOOR_R - 2.5:
        return LINE
    if 18.5 < r <= 21.5:
        state = 'red_nether_bricks' if r <= 19.5 or r > 20.5 else LINE
    else:
        spoke = abs(((a + 15) % 30) - 15)                      # degrees from the nearest spoke
        if spoke * math.pi / 180 * r < .55:
            state = LINE
        elif r <= 18.5:
            state = 'nether_bricks' if int(a // 30) % 2 == 0 and 10.5 < r <= 16.5 else 'polished_blackstone_bricks'
            if 9.5 < r <= 10.5 or 16.5 < r <= 17.5:
                state = LINE
        elif 29.5 < r <= 30.5:
            state = 'chiseled_polished_blackstone'
        else:
            # A sunburst: every other whole sector between the spokes dark red, out of step with
            # the inner ring's, from the pillar band to the border.
            state = 'nether_bricks' if int(a // 30) % 2 == 1 and 22.5 < r <= 36.5 else 'polished_blackstone_bricks'
    # Away from the pyre the pattern stays whole: only single cracked bricks here and there.
    if noise(x, 0, z, 5) < (.13 if r <= 14 else .06):
        state = cracked(state)
    # Soot from the fire, heavy round the scar and gone by r=14.
    if r <= 14 and soot > .47 + .32 * (r / 14) ** 1.5:
        return char(x, 0, z, 6)
    return state


def walk_path(rng, start, heading, steps, wander, pull=0.0):
    """A wandering line of cells from start; pull bends it back toward radial."""
    px, pz = start
    hx, hz = math.cos(heading), math.sin(heading)
    out = []
    for _ in range(steps):
        ang = math.atan2(hz, hx) + rng.uniform(-wander, wander)
        hx, hz = math.cos(ang), math.sin(ang)
        if pull:
            rr = math.hypot(px, pz) or 1
            hx, hz = hx * (1 - pull) + px / rr * pull, hz * (1 - pull) + pz / rr * pull
            l = math.hypot(hx, hz)
            hx, hz = hx / l, hz / l
        px, pz = px + hx, pz + hz
        out.append((round(px), round(pz), hx, hz))
    return out


def build_floor(lay, rng):
    R = FLOOR_R
    for x in range(-R - 1, R + 2):
        for z in range(-R - 1, R + 2):
            if math.hypot(x, z) <= R + .5:
                lay.put(x, 0, z, floor_pattern(x, z))
                lay.put(x, -1, z, 'blackstone')
    # Melted gold that ran out of the pyre along the floor and froze.
    for k in range(6):
        a = (k + rng.uniform(.15, .85)) * 2 * math.pi / 6
        length = rng.randint(7, 18)
        path = walk_path(rng, (6 * math.cos(a), 6 * math.sin(a)), a + rng.uniform(-.3, .3), length, .35, .12)
        for i, (x, z, hx, hz) in enumerate(path):
            if math.hypot(x, z) > R - 2.5:
                break
            gold = lambda xx, zz: 'gold_block' if noise(xx, 0, zz, 8) < .07 else 'raw_gold_block'
            lay.put(x, 0, z, gold(x, z))
            if i < length * .3 or rng.random() < .15:
                sx, sz = round(-hz), round(hx)
                lay.put(x + sx, 0, z + sz, gold(x + sx, z + sz))
            if rng.random() < .25:
                dx, dz = rng.choice(SIDES)
                if 'gold' not in lay.get(x + dx, 0, z + dz):
                    lay.put(x + dx, 0, z + dz, 'gilded_blackstone')
    # Long fissures the fire split out toward the rim, cold now: open runs of shattered brick
    # between stretches where the floor's own stone merely cracked. Nothing on the floor burns.
    def fissure(x, z):
        n = noise(x, 0, z, 7)
        return 'blackstone' if n < .4 else 'smooth_basalt' if n < .75 else 'cracked_polished_blackstone_bricks'
    n = 7
    for k in range(n):
        a = (k + rng.uniform(.1, .9)) * 2 * math.pi / n
        path = walk_path(rng, (9 * math.cos(a), 9 * math.sin(a)), a + rng.uniform(-.4, .4), 60, .45, .1)
        run, open_run = rng.randint(2, 4), True
        for i, (x, z, hx, hz) in enumerate(path):
            if math.hypot(x, z) > R - 2:
                break
            here = lay.name(x, 0, z)
            # Where a crack crosses a gold run the gold is split open too.
            lay.put(x, 0, z, fissure(x, z) if open_run or 'gold' in here else cracked(lay.get(x, 0, z).split(':')[1]))
            run -= 1
            if run <= 0:
                open_run = not open_run
                run = rng.randint(2, 5) if open_run else rng.randint(1, 3)
            if rng.random() < .25:
                dx, dz = rng.choice(SIDES)
                s = lay.get(x + dx, 0, z + dz)
                if s:
                    lay.put(x + dx, 0, z + dz, cracked(s.split(':')[1]))
            if rng.random() < .05:
                # A short fork.
                for fx, fz, _, _ in walk_path(rng, (x, z), math.atan2(hz, hx) + rng.choice((-1, 1)) * .9, rng.randint(3, 6), .3):
                    if math.hypot(fx, fz) < R - 2:
                        lay.put(fx, 0, fz, fissure(fx, fz) if rng.random() < .5 else cracked(lay.get(fx, 0, fz).split(':')[1]))
    # The step ring that carries the invisible wall, and the gallery behind it.
    for x in range(-60, 61):
        for z in range(-60, 61):
            r = math.hypot(x, z)
            if R + .5 < r <= STEP_R:
                lay.put(x, -1, z, 'blackstone')
                lay.put(x, 0, z, 'polished_blackstone')
                lay.put(x, 1, z, 'polished_blackstone' if noise(x, 1, z, 9) > .15 else 'cracked_polished_blackstone_bricks')
            elif STEP_R < r <= WALL_OUT:
                lay.put(x, -1, z, 'blackstone')
                lay.put(x, 0, z, 'blackstone')
                lay.put(x, 1, z, 'polished_blackstone_bricks')
                if r <= STEP_R + 1:
                    top = 'chiseled_polished_blackstone'
                else:
                    top = 'polished_blackstone_bricks' if noise(x, 2, z, 10) > .2 else 'cracked_polished_blackstone_bricks'
                    if vnoise(x, z, 4, 11) > .7:
                        top = char(x, 2, z, 12)
                lay.put(x, 2, z, top)


# ---------------------------------------------------------------- pillars

def build_pillar(lay, rng, az, top, foot):
    cx, cz = polar(PILLAR_R, az)
    whole = top >= PLAY_CEILING
    slant_dir = math.radians(rng.uniform(0, 360))
    slant = 0 if whole else rng.uniform(2.5, 5) if top > 8 else rng.uniform(.8, 1.6)
    cells = [(x, z) for x in range(int(cx) - 7, int(cx) + 8) for z in range(int(cz) - 7, int(cz) + 8)]
    dist = {c: math.hypot(c[0] - cx, c[1] - cz) for c in cells}
    shaft = [c for c in cells if dist[c] <= SHAFT_R]
    # The broken top is a slanted, jagged plane whose highest block sits at `top`.
    heights = {}
    for x, z in shaft:
        s = ((x - cx) * math.cos(slant_dir) + (z - cz) * math.sin(slant_dir) + SHAFT_R) / (2 * SHAFT_R)
        heights[x, z] = -slant * (1 - s) - 1.4 * noise(x, 0, z, 41 + az) * (0 if whole else 1)
    hmax = max(heights.values())
    heights = {c: top + round(h - hmax) for c, h in heights.items()}
    shaft_top = (top - 5) if whole else top
    collar = []
    for c in cells:
        x, z = c
        d = dist[c]
        if d <= BASE_R:
            lay.put(x, 1, z, 'polished_blackstone_bricks' if d > BASE_R - 1 else 'polished_blackstone')
            foot.add(c)
        if d <= BASE_R - .5:
            lay.put(x, 2, z, 'chiseled_polished_blackstone' if d > SHAFT_R + .2 else 'polished_blackstone')
            foot.add(c)
        if SHAFT_R < d <= SHAFT_R + .7:
            # The collar: a lumpy, broken ring of gilded and carved stone, only a hint of gold.
            ang = math.degrees(math.atan2(z - cz, x - cx))
            lump = anoise(ang, 40, 40 + az)
            if lump < .22:
                continue
            n = noise(x, 3, z, 42)
            lay.put(x, 3, z, 'gilded_blackstone' if n < .55 else 'chiseled_polished_blackstone')
            collar.append((noise(x, 3, z, 45 + az), x, z))
            if lump > .78 and noise(x, 4, z, 39) < .6:
                lay.put(x, 4, z, 'gilded_blackstone')
            foot.add(c)
            if noise(x, 2, z, 43) < .3:
                lay.put(x, 2, z, 'gilded_blackstone')
    # Two or three drops of gold that ran down onto the collar, no more.
    for _, x, z in sorted(collar)[:2 + (az // 60) % 2]:
        lay.put(x, 3, z, 'raw_gold_block')
    for c in shaft:
        x, z = c
        d = dist[c]
        h = min(heights[c], shaft_top)
        rim = d > SHAFT_R - 1.05
        for y in range(3, h + 1):
            if (y - 2) % 7 == 0 and y < h - 1 and rim:
                state = 'chiseled_polished_blackstone'
            elif y <= 9 and rim and anoise(math.degrees(math.atan2(z - cz, x - cx)), 45, 46 + az) < .45 - (y - 3) * .06:
                state = 'blackstone' if noise(x, y, z, 46) < .7 else 'smooth_basalt'   # soot streaks at the foot
            elif not whole and y == h and noise(x, y, z, 47) < .4:
                state = 'blackstone'   # the broken end is burnt
            else:
                state = 'polished_basalt[axis=y]' if noise(x, y, z, 49) > .05 else 'basalt[axis=y]'
            lay.put(x, y, z, state)
        foot.add(c)
    # Bands stand a little proud of the shaft.
    for c in cells:
        x, z = c
        if SHAFT_R < dist[c] <= SHAFT_R + .45:
            for y in range(9, shaft_top - 1, 7):
                if any(min(heights.get((x + dx, z + dz), -99), shaft_top) > y + 1 for dx, dz in SIDES):
                    lay.put(x, y, z, 'chiseled_polished_blackstone')
                    foot.add(c)
    if whole:
        # The one whole pillar ends in a flared capital that rises through the lid.
        flare = [(shaft_top + 1, SHAFT_R + .5, 'polished_blackstone_bricks'), (shaft_top + 2, SHAFT_R + 1.1, 'gilded_blackstone'),
                 (shaft_top + 3, SHAFT_R + 1.6, 'polished_blackstone_bricks'), (shaft_top + 4, SHAFT_R + 2.1, 'chiseled_polished_blackstone'),
                 (shaft_top + 5, SHAFT_R + 2.1, 'polished_blackstone')]
        for y, rad, mat in flare:
            for c in cells:
                if dist[c] <= rad:
                    m = mat
                    if mat == 'gilded_blackstone' and dist[c] <= rad - 1:
                        m = 'polished_blackstone'
                    lay.put(c[0], y, c[1], cracked(m) if noise(c[0], y, c[1], 50) < .12 else m)
                    foot.add(c)
        assert shaft_top + 5 == top


# ---------------------------------------------------------------- the wall

def wall_top(a):
    h = WALL_H - 2 + round(7 * anoise(a, 9, 21))
    if abs(adiff(a, GATE_AZ)) <= BAY / 2:
        h += 4
    return h


def breach_cut(x, y, z):
    """The wall keeps a cell only below this height inside a breach (None elsewhere)."""
    a = az_of(x, z)
    best = None
    for b, (bottom, hw) in BREACHES.items():
        if abs(adiff(a, b)) > 35:
            continue
        t = abs(tang(x, z, b))
        cut = bottom + (t / hw) ** 1.15 * (WALL_H - bottom) + 3.2 * (noise(x, y, z, 31) - .5) + 2 * (vnoise(t, y, 3, 32) - .5)
        best = cut if best is None else min(best, cut)
    return best


def window_open(t, y):
    """Tall pointed window, 6 wide, sill at 11, apex at 26."""
    if y < 11:
        return False
    if y <= 21:
        return abs(t) <= 3
    return (abs(t) + 3) ** 2 + (y - 21) ** 2 <= 36


def gate_open(t, y):
    """The doorway, 9 wide, from the gallery floor up to a point at 18."""
    if y < 3:
        return False
    if y <= 12:
        return abs(t) <= 4.45
    return (abs(t) + 2.5) ** 2 + (y - 12) ** 2 <= 49


def near(fn, t, y, reach=1.25):
    return any(fn(t + dt, y + dy) for dt in (-reach, 0, reach) for dy in (-1, 0, 1))


def near2(fn, t, y):
    return any(fn(t + dt, y + dy) for dt in (-2.3, -1.2, 0, 1.2, 2.3) for dy in (-2, -1, 0, 1, 2))


def apse_d(x, z):
    return math.hypot(x - APSE[0], z - APSE[1])


def window_bays():
    skip = {GATE_AZ, THRONE_AZ} | set(BREACHES)
    return [k * BAY for k in range(16) if k * BAY not in skip]


def pier_azimuths():
    return [BAY / 2 + k * BAY for k in range(16)]


def build_wall(lay, rng):
    bays = window_bays()
    rng.sample(bays, 4)   # the windows' bars are gone; the draw keeps the rest of the stream unchanged
    for x in range(-64, 65):
        for z in range(-64, 65):
            r = math.hypot(x, z)
            d = apse_d(x, z)
            in_main = WALL_IN < r <= WALL_OUT
            in_apse = APSE_IN < d <= APSE_OUT and r > WALL_IN - .01
            if not (in_main or in_apse) or d <= APSE_IN:
                continue
            a = az_of(x, z)
            if in_apse:
                top = 46 + round(3 * anoise(math.degrees(math.atan2(z - APSE[1], x)) % 360, 30, 22))
            else:
                top = wall_top(a)
            top += 1 if noise(x, 0, z, 23) > .78 else -1 if noise(x, 0, z, 23) < .18 else 0
            bay = round(a / BAY) % 16 * BAY
            t = tang(x, z, bay)
            dep = depth(x, z, bay)
            inner = r <= WALL_IN + 1.05 and not in_apse
            is_window = bay in bays and not in_apse
            is_gate = bay == GATE_AZ
            for y in range(WALL_FOOT, top + 1):
                cut = breach_cut(x, y, z)
                if cut is not None and y > cut:
                    continue
                state = masonry(x, y, z, 24)
                if y >= top - 2 and noise(x, y, z, 25) < .35:
                    state = char(x, y, z, 26)
                elif y <= 4 and noise(x, y, z, 27) < .25:
                    state = char(x, y, z, 28)
                if is_window:
                    if window_open(t, y):
                        continue
                    if near(window_open, t, y):
                        state = 'nether_bricks' if noise(x, y, z, 30) > .12 else 'cracked_nether_bricks'
                        if y == 27 and abs(t) < 1:
                            state = 'chiseled_nether_bricks'
                    elif inner and 26 <= y <= 33 and abs(t) <= 4.2 - (y - 26) * .5 and noise(x, y, z, 33) < .8:
                        state = char(x, y, z, 34)   # soot plume where the fire poured out
                if is_gate:
                    if gate_open(t, y):
                        # The portcullis, jammed half raised: one grid across the arch in the
                        # wall's middle plane, its bent lower edge at 13 to 15.
                        if round(dep) == 50 and y >= 13 + (noise(round(t), 0, 0, 35) < .3) + (noise(round(t), 1, 0, 35) < .15):
                            lay.put(x, y, z, 'iron_bars')
                        continue
                    if near(gate_open, t, y):
                        state = 'nether_bricks' if noise(x, y, z, 36) > .1 else 'cracked_nether_bricks'
                        if abs(t) < 1 and y == 19:
                            state = 'chiseled_nether_bricks'
                    elif near2(gate_open, t, y):
                        state = 'red_nether_bricks'
                    if abs(t) <= 4.45 and y == GALLERY_TOP:
                        state = 'polished_blackstone_bricks'
                if y == 10 and inner and not in_apse:
                    state = 'nether_bricks'
                lay.put(x, y, z, state)
            # The string course stands proud of the inner face.
            if inner and WALL_IN < r <= WALL_IN + 1.05:
                ox, oz = (round(x - x / r), round(z - z / r))
                if math.hypot(ox, oz) <= WALL_IN and lay.get(x, 10, z) and not (is_gate and abs(t) <= 6):
                    lay.put(ox, 10, oz, 'nether_bricks' if noise(ox, 10, oz, 37) > .1 else 'chiseled_nether_bricks', replace=False)
    # A gold crown over the gate, outside and in.
    for t in range(-3, 4):
        for y, cond in ((21, True), (22, t in (-3, 0, 3)), (23, t == 0)):
            if cond:
                for z in (52, 49):
                    lay.put(-t, y, z, 'gold_block' if noise(t, y, z, 38) > .25 else 'raw_gold_block')
    build_rose(lay)
    return build_piers(lay)


def build_rose(lay):
    """The broken round window high in the apse over the throne, ringed in gold."""
    for x in range(-7, 8):
        for z in range(APSE[1] - 13, APSE[1] - 7):
            d = apse_d(x, z)
            if not (APSE_IN < d <= APSE_OUT):
                continue
            for y in range(ROSE_Y - 7, ROSE_Y + 8):
                q = math.hypot(x, y - ROSE_Y)
                ang = math.degrees(math.atan2(y - ROSE_Y, x))
                broken = 35 < ang < 120 and noise(x, y, z, 51) < .75   # the upper ring is smashed through
                if q <= ROSE_R + .3 or (broken and q <= ROSE_R + 1.7):
                    lay.remove(x, y, z)
                    # Stubs of the tracery.
                    if q <= ROSE_R + .3 and (x == 0 or y == ROSE_Y) and q > 2.2 and not broken and noise(x, y, z, 52) < .7:
                        lay.put(x, y, z, 'chiseled_polished_blackstone')
                elif q <= ROSE_R + 1.6:
                    lay.put(x, y, z, 'gold_block' if noise(x, y, z, 53) > .3 else 'raw_gold_block')
                elif q <= ROSE_R + 2.4 and lay.get(x, y, z):
                    lay.put(x, y, z, 'gilded_blackstone' if noise(x, y, z, 54) < .3 else 'polished_blackstone')


def build_piers(lay):
    """Basalt piers at each bay boundary, a block proud inside and two out, above the wall.
    Returns the piers that still carry a roof rib: azimuth -> top y."""
    carriers = {}
    for b in pier_azimuths():
        top = wall_top(b) + 6
        px, pz = polar(50.5, b)
        intact = True
        for x in range(round(px) - 5, round(px) + 6):
            for z in range(round(pz) - 5, round(pz) + 6):
                if abs(tang(x, z, b)) > 1.5 or not (WALL_IN - 1 < depth(x, z, b) <= WALL_OUT + 2) or apse_d(x, z) <= APSE_IN:
                    continue
                for y in range(WALL_FOOT, top + 1):
                    cut = breach_cut(x, y, z)
                    if cut is not None and y > cut:
                        intact = False
                        continue
                    if y >= top:
                        state = 'chiseled_polished_blackstone'
                    elif y <= GALLERY_TOP:
                        state = 'polished_blackstone_bricks'
                    elif y > top - 3 and noise(x, y, z, 56) < .3:
                        state = char(x, y, z, 57)
                    else:
                        state = 'polished_basalt[axis=y]'
                    if y == 10 and depth(x, z, b) < WALL_IN + .3:
                        state = 'nether_bricks'
                    lay.put(x, y, z, state)
        if intact:
            carriers[b] = top
    return carriers


# ---------------------------------------------------------------- the throne end

def build_throne(lay, rng):
    # Dais: 19 wide, steps up from the gallery, platform at 6 running back into the apse.
    for x in range(-9, 10):
        for z, top in ((-43, 3), (-44, 4), (-45, 5)):
            for y in range(1, top + 1):
                lay.put(x, y, z, 'polished_blackstone_bricks')
            lay.put(x, top, z, 'red_nether_bricks' if abs(x) <= 1 else 'polished_blackstone' if abs(x) < 9 else 'chiseled_polished_blackstone')
    for x in range(-12, 13):
        for z in range(-64, -45):
            r = math.hypot(x, z)
            d = apse_d(x, z)
            if not ((abs(x) <= 9 and r <= WALL_IN + .01) or d <= APSE_IN):
                continue
            for y in range(-1, 6):
                lay.put(x, y, z, 'polished_blackstone_bricks' if y > 0 else 'blackstone')
            top = 'red_nether_bricks' if abs(x) <= 1 and z >= -50 else 'polished_blackstone_bricks'
            if z == -46 or (abs(x) == 9 and z > -49):
                top = 'chiseled_polished_blackstone'
            elif top == 'polished_blackstone_bricks' and noise(x, 6, z, 60) < .14:
                top = 'cracked_polished_blackstone_bricks'
            lay.put(x, 6, z, top)
    # The throne, sized for a five-block king: a tall pointed back edged all round in gold,
    # a crown on it, and a broad seat between solid arms.
    put = lambda x, y, z, s: (lay.put(x, y, z, s), lay.put(-x, y, z, s))
    half = {y: 4 for y in range(7, 22)} | {22: 3, 23: 3, 24: 2, 25: 1, 26: 0}   # y -> half-width of the back

    def inside(x, y):
        return y >= 7 and y in half and abs(x) <= half[y]
    crown = {(0, 16), (1, 16), (2, 16), (0, 17), (1, 17), (2, 17), (0, 18), (2, 18), (0, 19)}
    for y in half:
        for x in range(0, half[y] + 1):
            edge = any(not inside(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, 1)) if y + dy >= 7)
            for z in (-55, -54):
                if edge:
                    state = 'gold_block'
                elif z == -54 and (x, y) in crown:
                    state = 'gold_block'
                elif z == -54 and 9 <= y <= 13 and (x + y) % 3 == 0:
                    state = 'chiseled_polished_blackstone'
                else:
                    state = 'polished_blackstone'
                put(x, y, z, state)
    # The seat, five wide and two high, its front edge gold.
    for x in range(0, 3):
        for z in range(-53, -49):
            put(x, 7, z, 'polished_blackstone')
            put(x, 8, z, 'polished_blackstone')
        put(x, 7, -50, 'chiseled_polished_blackstone')
        put(x, 8, -50, 'gold_block')
    # Solid arms, gold capped at the front.
    for z in range(-53, -49):
        for y in range(7, 11):
            put(3, y, z, 'polished_blackstone_bricks' if y < 10 else 'polished_blackstone')
    for y in range(7, 10):
        put(3, y, -50, 'chiseled_polished_blackstone')
    put(3, 10, -50, 'gold_block')
    # Gold that melted off it ran from the seat's foot down the dais in two clean runs; one
    # reached the floor and froze in a small pool.
    for _ in range(23):
        rng.choice((-1, 0, 0, 1))   # the old runs' draws, so the rest of the stream is unchanged
    steps = {-45: 5, -44: 4, -43: 3, -42: 2, -41: 1, -40: 0}
    for x, last in ((-2, -40), (3, -43)):
        for z in range(-49, last + 1):
            y = steps.get(z, 6)
            if x == 3 and z <= -46:
                x_here = 2   # it slid off the arm's foot and ran out beside it
            else:
                x_here = x
            lay.put(x_here, y, z, 'gold_block' if noise(x_here, y, z, 62) < .3 else 'raw_gold_block')
    for dx, dz in ((-1, 0), (1, 0), (0, 1)):
        lay.put(-2 + dx, 0, -40 + dz, 'raw_gold_block')
    # Braziers flank the throne on the platform and light it: fire on netherrack in blackstone bowls.
    for sx in (-6, 6):
        z0 = -52
        for y in (7, 8):
            lay.put(sx, y, z0, 'polished_blackstone' if y == 7 else 'chiseled_polished_blackstone')
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                lay.put(sx + dx, 9, z0 + dz, 'blackstone')
                lay.put(sx + dx, 10, z0 + dz, 'netherrack' if dx == dz == 0 else 'polished_blackstone' if dx and dz else 'blackstone')
        lay.put(sx, 11, z0, 'fire')   # burns above the rim, so the hall sees it


# Light on the throne's back. The braziers only reach its lowest outer edges, so two lanterns
# hang on chains either side of its upper half from short beams built out of the apse's side
# walls, and invisible light blocks just in front of the back fill the rest so its gold reads
# top to bottom. Each (x, y) is a cell in the layer in front of the back (z = THRONE_FRONT).
THRONE_FRONT = -53
THRONE_LANTERNS = {'x': 6, 'beam_y': 27, 'lantern_y': 19, 'wall_x': 8}
THRONE_FILL = ([(sx, y) for sx in (-3, 3) for y in (11, 15, 19)] + [(0, y) for y in (9, 13, 17, 21, 25)]
               + [(-2, 23), (2, 23), (-4, 8), (4, 8)])
THRONE_FILL_LEVEL = 15


def light_throne(lay):
    z = THRONE_FRONT
    p = THRONE_LANTERNS
    for sx in (-1, 1):
        assert lay.get(sx * p['wall_x'], p['beam_y'], z), 'the lantern beams need the apse side wall to build out of'
        for x in range(p['x'], p['wall_x']):
            assert lay.get(sx * x, p['beam_y'], z) is None
            lay.put(sx * x, p['beam_y'], z, 'polished_blackstone_bricks' if x < p['wall_x'] - 1 else 'chiseled_polished_blackstone')
        for y in range(p['lantern_y'], p['beam_y']):
            assert lay.get(sx * p['x'], y, z) is None
            lay.put(sx * p['x'], y, z, 'lantern[hanging=true]' if y == p['lantern_y'] else 'iron_chain[axis=y]')
    for x, y in THRONE_FILL:
        assert lay.get(x, y, z) is None and lay.get(x, y, z - 1), f'fill light at {(x, y, z)} must sit against the throne back'
        lay.put(x, y, z, f'light[level={THRONE_FILL_LEVEL}]')


# ---------------------------------------------------------------- the crag and the sea

def crag_heights():
    """Surface y per column outside the wall, down to the lava at a ragged shore."""
    out = {}
    for x in range(LO, HI + 1):
        for z in range(LO, HI + 1):
            r = math.hypot(x, z)
            if r <= WALL_OUT:
                continue
            a = az_of(x, z)
            ledge = 56 + 2.5 * anoise(a, 24, 70)
            shore = 70 + 15 * anoise(a, 36, 71) + 3 * (vnoise(x, z, 7, 72) - .5)
            if r > shore + 6:
                continue
            if r <= ledge:
                y = 0 if noise(x, 0, z, 73) > .2 else -1
            elif r >= shore:
                y = LAVA_Y - 1 - int((r - shore) / 3)
            else:
                t = (r - ledge) / (shore - ledge)
                base = -1 + (LAVA_Y + 1 - -1) * (t ** .75)
                y = round(base + 4 * (vnoise(x, z, 6, 74) - .5) + 2 * (vnoise(x, z, 2.5, 75) - .5))
                y = max(LAVA_Y + 1 if r < shore - 1 else LAVA_Y, min(0, y))
            out[x, z] = y
    return out


def build_crag(lay, rng, heights):
    for (x, z), s in heights.items():
        lows = [heights.get((x + dx, z + dz), BOTTOM) for dx, dz in SIDES]
        lows += [0 for dx, dz in SIDES if math.hypot(x + dx, z + dz) <= WALL_OUT]
        floor_y = max(BOTTOM, min(min(lows), s) - 1)
        if s <= LAVA_Y + 1:
            floor_y = BOTTOM
        steep = s - min(lows) >= 2
        for y in range(floor_y, s + 1):
            n = noise(x, y, z, 80)
            if s <= LAVA_Y + 1 and y >= LAVA_Y - 1 and n < .55:
                state = 'magma_block'
            elif steep and y < s and vnoise(x, z, 5, 81) > .45:
                state = 'basalt[axis=y]'
            elif vnoise(x + y * .3, z, 9, 82) > .62:
                state = 'blackstone'
            elif n < .05:
                state = 'magma_block' if y < -6 else 'smooth_basalt'
            else:
                state = 'netherrack'
            if y == s and s > LAVA_Y + 1 and vnoise(x, z, 5, 83) > .74:
                state = 'basalt[axis=y]' if noise(x, y, z, 84) < .5 else 'blackstone'
            lay.put(x, y, z, state)
    # A few basalt stacks along the slope.
    for _ in range(12):
        a = rng.uniform(0, 360)
        if any(abs(adiff(a, c)) < 10 for c in (GATE_AZ, 180, 315)):
            continue
        rr = rng.uniform(58, 74)
        x, z = (round(v) for v in polar(rr, a))
        if (x, z) not in heights:
            continue
        h = rng.randint(3, 7)
        rad = rng.uniform(1.5, 2.6)
        for dx in range(-2, 3):
            for dz in range(-2, 3):
                if dx * dx + dz * dz <= rad * rad and (x + dx, z + dz) in heights:
                    s = heights[x + dx, z + dz]
                    for y in range(s + 1, s + 1 + h - int(math.hypot(dx, dz) * 1.5) - int(noise(x + dx, 0, z + dz, 85) * 2)):
                        lay.put(x + dx, y, z + dz, 'basalt[axis=y]')


def causeway(lay, heights, az, deck, half, s0, s1, gap=None, spacing=12, first_pier=None):
    """A Nether-fortress bridge: deck, fence rails, round arches between piers that stand
    in the lava. Ragged ends where it breaks."""
    ux, uz = math.cos(math.radians(az)), math.sin(math.radians(az))
    piers = []
    p = first_pier if first_pier is not None else s0 + 5
    while p < s1:
        if not gap or not (gap[0] - 1 <= p <= gap[1] + 1):
            piers.append(p)
        p += spacing
    span = spacing / 2 - 1.5
    for x in range(LO, HI + 1):
        for z in range(LO, HI + 1):
            s = x * ux + z * uz
            l = -x * uz + z * ux
            if abs(l) > half + .5 or s < s0 - 1 or s > s1:
                continue
            if math.hypot(x, z) <= WALL_OUT + .3:
                continue
            j = noise(x, 0, z, 90) * 3
            if s > s1 - j:
                continue
            if gap and gap[0] + j * .7 <= s <= gap[1] - noise(x, 1, z, 91) * 3:
                continue
            ground = heights.get((x, z), BOTTOM - 1)
            if ground >= deck - 1:
                continue   # still on the rock
            edge = abs(l) > half - .5
            pd = min((abs(s - p) for p in piers), default=99)
            if pd <= 1.5:
                under = BOTTOM
            else:
                f = min(1.0, (pd - 1.5) / span)
                under = deck - 1 - round(5 * (1 - math.sqrt(max(0., 1 - (1 - f) ** 2))))
            under = max(BOTTOM, ground) if pd <= 1.5 else max(under, ground + 1)
            for y in range(under, deck + 1):
                if y == deck:
                    state = 'nether_bricks' if noise(x, y, z, 92) > .14 else 'cracked_nether_bricks'
                else:
                    state = 'nether_bricks' if noise(x, y, z, 93) > .1 else 'cracked_nether_bricks'
                    if pd <= 1.5 and y <= LAVA_Y + 1 and noise(x, y, z, 94) < .3:
                        state = 'red_nether_bricks'
                lay.put(x, y, z, state)
            if edge and noise(x, deck + 1, z, 95) > .12:
                lay.put(x, deck + 1, z, 'nether_brick_fence')


def build_causeways(lay, rng, heights):
    # The gate's bridge runs south to the edge of the realm, broken across a gap.
    causeway(lay, heights, GATE_AZ, GALLERY_TOP, 4, 50, HI + 12, gap=(95, 102), first_pier=58)
    for x in range(-4, 5):
        for z in range(49, 58):
            if math.hypot(x, z) > WALL_OUT + .3 and not lay.get(x, GALLERY_TOP, z):
                for y in range(heights.get((x, z), -1) + 1, GALLERY_TOP + 1):
                    lay.put(x, y, z, 'nether_bricks')
    # Two lower roads leave the crag and end in the air.
    for az, length in ((180, rng.randint(32, 40)), (315, rng.randint(30, 38))):
        ux, uz = math.cos(math.radians(az)), math.sin(math.radians(az))
        s = 54
        while heights.get((round(s * ux), round(s * uz)), BOTTOM) > -5:
            s += 1
        causeway(lay, heights, az, -4, 2, s - 2, s + length, first_pier=s + 4)


def spire_sites(rng):
    sites = []
    tries = 0
    while len(sites) < 12 and tries < 6000:
        tries += 1
        a = rng.uniform(0, 360)
        if any(abs(adiff(a, c)) < 16 for c in (GATE_AZ, 180, 315)):
            continue
        r = rng.uniform(88, 116)
        x, z = polar(r, a)
        rb = rng.uniform(2.8, 5.2)
        if not (LO + rb + 6 <= x <= HI - rb - 6 and LO + rb + 6 <= z <= HI - rb - 6):
            continue
        if any(abs(adiff(a, math.degrees(math.atan2(sz, sx)))) < 19 for sx, sz, *_ in sites):
            continue   # spread round the sea rather than bunched
        sites.append((x, z, rb, rng.randint(20, 70)))
    return sites


def spire_column(lay, cx, cz, rb, h, lean, salt):
    """One basalt column out of the sea: a flared foot, a long thick body, a snapped crown."""
    top = LAVA_Y + h
    for y in range(BOTTOM, top + 1):
        f = max(0., (y - LAVA_Y) / h)
        rad = rb * (1 - f ** 2.2) ** .9 + .7 + (1.4 * (1 - (y - LAVA_Y) / 4) if y < LAVA_Y + 4 else 0)
        ox, oz = cx + lean[0] * max(0, y - LAVA_Y), cz + lean[1] * max(0, y - LAVA_Y)
        R = int(rad) + 2
        for x in range(round(ox) - R, round(ox) + R + 1):
            for z in range(round(oz) - R, round(oz) + R + 1):
                d = math.hypot(x - ox, z - oz)
                ridge = 1.1 * (anoise(math.degrees(math.atan2(z - oz, x - ox)), 30, salt + (y // 9)) - .5)
                if d > rad + ridge:
                    continue
                if y > top - 5 and noise(x, y, z, salt) < (y - top + 5) * .22:
                    continue   # a jagged broken crown
                n = noise(x, y, z, 102)
                if y <= LAVA_Y + 1 and n < .45:
                    state = 'magma_block'
                elif (y + salt) % 11 == 0 and d > rad - 1.2:
                    state = 'smooth_basalt'   # bands where the column cooled in layers
                elif n < .62:
                    state = 'basalt[axis=y]'
                elif n < .82:
                    state = 'polished_basalt[axis=y]'
                else:
                    state = 'blackstone'
                lay.put(x, y, z, state)


def build_spires(lay, rng):
    """Basalt stacks rising from the sea, one to three columns each."""
    sites = spire_sites(rng)
    for i, (cx, cz, rb, h) in enumerate(sites):
        lean = (rng.uniform(-.05, .05), rng.uniform(-.05, .05))
        spire_column(lay, cx, cz, rb, h, lean, 100 + i * 7)
        for k in range(rng.choice((0, 1, 1, 2))):
            a = rng.uniform(0, 2 * math.pi)
            d = rb * rng.uniform(.8, 1.3)
            spire_column(lay, cx + d * math.cos(a), cz + d * math.sin(a), rb * rng.uniform(.45, .7), max(8, round(h * rng.uniform(.3, .7))),
                         (lean[0] + rng.uniform(-.03, .03), lean[1] + rng.uniform(-.03, .03)), 150 + i * 7 + k)
    return sites


# ---------------------------------------------------------------- dressing

def heap(lay, cx, cz, base_y, rad, height, pick, ok):
    """A mound of loose stone; base_y is a height or a function of the column."""
    for x in range(round(cx) - int(rad) - 1, round(cx) + int(rad) + 2):
        for z in range(round(cz) - int(rad) - 1, round(cz) + int(rad) + 2):
            d = math.hypot(x - cx, z - cz)
            if d > rad or not ok(x, z):
                continue
            h = round(height * (1 - (d / (rad + .5)) ** 1.6) + noise(x, 0, z, 110) * .7 - .2)
            y0 = base_y(x, z) if callable(base_y) else base_y
            for y in range(y0, y0 + h):
                if lay.get(x, y, z) is None:
                    lay.put(x, y, z, pick(x, y, z))


def on_gallery(x, z):
    r = math.hypot(x, z)
    if not (STEP_R + 1 < r <= WALL_IN - .2):
        return False
    if abs(x) <= 13 and z < -40:
        return False   # the dais and braziers
    if abs(x) <= 6 and z > 40:
        return False   # the gate path
    return True


def build_dressing(lay, rng, heights):
    # Rubble: heavy under the breaches, scattered elsewhere.
    for b, (bottom, hw) in BREACHES.items():
        n = 9 if bottom < 10 else 4
        for _ in range(n):
            a = b + rng.uniform(-1, 1) * math.degrees(hw / 45) * 1.3
            rr = rng.uniform(43.5, 47.5)
            x, z = polar(rr, a)
            heap(lay, x, z, GALLERY_TOP + 1, rng.uniform(1.5, 3.2), rng.uniform(2, 5 if bottom < 10 else 3), rubble, on_gallery)
            # ...and outside on the ledge and slope.
            x, z = polar(rng.uniform(54, 64), b + rng.uniform(-1, 1) * math.degrees(hw / 55) * 1.5)
            s = heights.get((round(x), round(z)))
            if s is not None:
                heap(lay, x, z, lambda xx, zz: heights[xx, zz] + 1, rng.uniform(1.8, 3.6), rng.uniform(2, 4.5), rubble,
                     lambda xx, zz: (xx, zz) in heights)
    for _ in range(16):
        a = rng.uniform(0, 360)
        x, z = polar(rng.uniform(44, 47.5), a)
        heap(lay, x, z, GALLERY_TOP + 1, rng.uniform(1, 2.2), rng.uniform(1, 2.6), rubble, on_gallery)
    # Fallen pillar drums lying along the gallery.
    for a in (133, 205, 52, 337):
        L = rng.randint(3, 5)
        cy = GALLERY_TOP + 2.6
        axis = 'x' if abs(math.sin(math.radians(a))) > .7 else 'z'
        for x in range(-60, 61):
            for z in range(-60, 61):
                if abs(adiff(az_of(x, z), a)) > 12:
                    continue
                t = tang(x, z, a)
                q = depth(x, z, a) - 45.5
                if abs(t) > L / 2 or not on_gallery(x, z):
                    continue
                for y in range(GALLERY_TOP + 1, GALLERY_TOP + 6):
                    if q * q + (y - cy) ** 2 <= 2.3 ** 2:
                        end = abs(t) > L / 2 - 1
                        lay.put(x, y, z, 'chiseled_polished_blackstone' if end and noise(x, y, z, 111) < .4
                                else f'polished_basalt[axis={axis}]' if noise(x, y, z, 112) > .1
                                else 'blackstone' if noise(x, y, z, 113) < .6 else 'smooth_basalt')
    # Heaps of his treasure, melted and hardened, either side of the throne.
    gold = lambda x, y, z: ('raw_gold_block' if noise(x, y, z, 114) < .62 else 'gold_block' if noise(x, y, z, 114) < .85
                            else 'gilded_blackstone')
    for a, rad, h in ((236, 2.8, 3.5), (244, 2.0, 2.4), (304, 2.9, 3.6), (296, 1.8, 2.2), (226, 1.6, 1.8), (314, 1.5, 1.6)):
        x, z = polar(45.6, a)
        heap(lay, x, z, GALLERY_TOP + 1, rad, h, gold, on_gallery)
    # Small fires still burning on the gallery.
    fires = 0
    while fires < 7:
        a = rng.uniform(0, 360)
        x, z = (round(v) for v in polar(rng.uniform(43.5, 47.5), a))
        if not on_gallery(x, z) or lay.get(x, GALLERY_TOP + 1, z):
            continue
        lay.put(x, GALLERY_TOP, z, 'netherrack')
        lay.put(x, GALLERY_TOP + 1, z, 'fire')
        fires += 1
    # ...and a few out on the crag.
    fires = 0
    while fires < 12:
        a = rng.uniform(0, 360)
        x, z = (round(v) for v in polar(rng.uniform(55, 72), a))
        s = heights.get((x, z))
        if s is None or s < LAVA_Y + 3 or lay.get(x, s + 1, z):
            continue
        lay.put(x, s, z, 'netherrack')
        lay.put(x, s + 1, z, 'fire')
        fires += 1


# ---------------------------------------------------------------- the great braziers

# Twelve great fire-pillars ring the hall on the gallery, a pair either side of the gate and of the
# throne. Each stands on a stepped plinth, a banded basalt shaft carries a flared bowl whose rim is
# BRAZIER_RIM above the fight floor, and in the bowl a stepped netherrack mound burns at four heights.
# Block light reaches only 15, so invisible light blocks low on the gallery just behind the kerb
# carry each fire's glow onto the rim of the floor. One, beside the great east breach, snapped when
# the wall came down: its stump is cold and its bowl lies on the gallery.
BRAZIER_R = 45.5
BRAZIER_AZ = [15 + 30 * k for k in range(12)]
BRAZIER_BROKEN = 15
BRAZIER_RIM = 15                  # y of the rim's top block
BRAZIER_POOL = [(-4, 3, 15), (0, 3, 15), (4, 3, 15), (-2, 6, 14), (2, 6, 14)]   # (tangent, y, level), at r ~43
BRAZIER_GLOW = 13                 # light blocks under the bowl's lip, so the shaft reads lit by its own fire
# Each brazier: (y, radius, material) from the plinth up. Radii are measured between whole cells
# round a whole-cell centre: 1.5 is 3x3, 2.0 adds the arms, 2.3 a rounded 5, 2.9 a full 5x5.
BRAZIER_PROFILE = [(3, 2.9, 'plinth'), (4, 2.3, 'polished_blackstone'), (5, 2.0, 'band'),
                   (6, 1.5, 'shaft'), (7, 1.5, 'shaft'), (8, 1.5, 'shaft'), (9, 2.0, 'band'), (10, 1.5, 'shaft'),
                   (11, 1.5, 'shaft'), (12, 2.0, 'flare'), (13, 2.9, 'flare'), (14, 3.3, 'bowl'), (15, 3.65, 'rim')]


def brazier_site(lay, az):
    """The whole cell nearest (BRAZIER_R, az), nudged round the gallery until it clears the wall's
    piers, the fallen drums, the dais and the gate path. Returns (azimuth, cx, cz)."""
    # Ties go toward the gate and the throne's axis mirrored, so the pairs stay mirror images.
    for nudge in sorted(range(-8, 9), key=lambda d: (abs(d), d * (1 if math.cos(math.radians(az)) < 0 else -1))):
        a = az + nudge
        x, z = polar(BRAZIER_R, a)
        cx, cz = round(x), round(z)
        if math.hypot(cx, cz) < BRAZIER_R - .35:
            cx, cz = (round(v) for v in polar(BRAZIER_R + .5, a))
        ok = True
        for y, rad, _ in BRAZIER_PROFILE:
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    x, z = cx + dx, cz + dz
                    r = math.hypot(x, z)
                    if math.hypot(dx, dz) > rad or r > WALL_IN:
                        continue
                    assert r > STEP_R + .2, f'brazier at azimuth {az} reaches the barrier'
                    if (abs(x) <= 10.5 and z < -40) or (abs(x) <= 6 and z > 40):
                        ok = False   # the dais, the throne's braziers, the gate path
                    s = lay.get(x, y, z)
                    if s and (r > WALL_IN - 1.2 or 'axis=x' in s or 'axis=z' in s):
                        ok = False   # a pier or the string course, or a fallen drum
        if ok:
            return a, cx, cz
    raise AssertionError(f'no room for the brazier at azimuth {az}')


def brazier_stone(role, x, y, z, d):
    if role == 'plinth':
        return 'chiseled_polished_blackstone' if d > 2.7 else 'polished_blackstone_bricks'
    if role == 'band':
        return 'gilded_blackstone' if d > 1.9 and noise(x, y, z, 141) < .35 else 'chiseled_polished_blackstone'
    if role == 'shaft':
        return 'polished_basalt[axis=y]' if noise(x, y, z, 142) > .06 else 'basalt[axis=y]'
    if role == 'flare':
        return 'polished_blackstone_bricks' if noise(x, y, z, 143) > .15 else 'cracked_polished_blackstone_bricks'
    if role == 'bowl':
        return 'polished_blackstone_bricks'
    # The rim: carved stone, gilt at every other cell of the lip.
    return 'gilded_blackstone' if (x + z) % 2 == 0 and noise(x, y, z, 144) < .7 else 'chiseled_polished_blackstone'


def build_braziers(lay):
    """Returns, per brazier: azimuth, centre cell, whether it burns, its fires and its light blocks."""
    out = []
    for az in BRAZIER_AZ:
        a, cx, cz = brazier_site(lay, az)
        burning = az != BRAZIER_BROKEN
        # Clear the rubble, treasure and small fires it stands in.
        for dx in range(-4, 5):
            for dz in range(-4, 5):
                if math.hypot(dx, dz) <= 3.0 and math.hypot(cx + dx, cz + dz) <= WALL_IN - 1.2:
                    for y in range(GALLERY_TOP + 1, BRAZIER_RIM + 5):
                        lay.remove(cx + dx, y, cz + dz)
                    lay.put(cx + dx, GALLERY_TOP, cz + dz, 'polished_blackstone_bricks')
        snap = 8 + round(2 * noise(cx, 0, cz, 145))   # where the broken one's shaft gave way
        fires, lights = [], []
        for y, rad, role in BRAZIER_PROFILE:
            for dx in range(-4, 5):
                for dz in range(-4, 5):
                    d = math.hypot(dx, dz)
                    x, z = cx + dx, cz + dz
                    if d > rad or math.hypot(x, z) > WALL_IN:
                        continue
                    if not burning and (y > snap or (y == snap and noise(x, y, z, 146) < .5)):
                        continue
                    if (role == 'bowl' and d <= 2.3) or (role == 'rim' and d <= 2.9):
                        continue   # the bowl's hollow, filled below
                    state = brazier_stone(role, x, y, z, d)
                    if not burning and y >= snap - 1 and noise(x, y, z, 147) < .5:
                        state = 'blackstone'   # the broken end is burnt
                    lay.put(x, y, z, state)
        if burning:
            # The mound heaped to the rim across the whole 5x5, then a 3x3, a cross and a peak, fire
            # on every top: flames at four heights from the rim up, one blaze.
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    d = math.hypot(dx, dz)
                    top = BRAZIER_RIM + (d <= 1.5) + (d <= 1.0) + (d == 0)
                    for y in range(BRAZIER_RIM - (d <= 2.3), top + 1):
                        lay.put(cx + dx, y, cz + dz, 'netherrack')
                    lay.put(cx + dx, top + 1, cz + dz, 'fire')
                    fires.append((cx + dx, top + 1, cz + dz))
            # Its glow carried down onto the floor's rim, and under the bowl onto its own shaft.
            ux, uz = math.cos(math.radians(a)), math.sin(math.radians(a))
            for t, y, level in BRAZIER_POOL:
                # The nearest free cell to the kerb that the builder counts outside the fight.
                spot = next(((x, yy, z) for yy in range(y, y + 6) for dt in (0, -1, 1) for rr in (42.6, 43, 43.5, 44)
                            for x, z in [(round(rr * ux - (t + dt) * uz), round(rr * uz + (t + dt) * ux))]
                            if math.hypot(x, z) > STEP_R + 1 and lay.get(x, yy, z) is None), None)
                assert spot, f'no room for a pool light by the brazier at {a}, tangent {t}'
                lights.append((*spot, level))
            for t in (-2, 2):
                x, z = next((x, z) for q in (2.5, 2.2, 1.8) for x, z in [(round(cx - q * ux - t * uz), round(cz - q * uz + t * ux))]
                            if math.hypot(x, z) > STEP_R + 1 and math.hypot(x - cx, z - cz) > 1.5)
                lights.append((x, 11, z, BRAZIER_GLOW))
        else:
            # The bowl broke off and lies on the gallery beside its stump, mouth toward the breach,
            # its burnt-out mound spilled from it.
            ba = a - 8
            bx, bz = polar(BRAZIER_R, ba)
            for x in range(round(bx) - 5, round(bx) + 6):
                for z in range(round(bz) - 5, round(bz) + 6):
                    if not on_gallery(x, z) or math.hypot(x, z) > WALL_IN - 1.2:
                        continue
                    t = tang(x - bx, z - bz, ba)   # along the gallery: the bowl's axis
                    q = depth(x, z, ba) - BRAZIER_R
                    for y in range(GALLERY_TOP + 1, GALLERY_TOP + 8):
                        rr = math.hypot(q, y - (GALLERY_TOP + 3.2))
                        if rr > 3.3 or not (-1.5 <= t <= 1.2):
                            continue
                        if t > .2 and rr < 2.2:
                            continue   # the mouth, open toward the breach
                        lay.put(x, y, z, brazier_stone('rim' if rr > 2.6 else 'bowl', x, y, z, rr))
            heap(lay, *polar(BRAZIER_R - .5, ba + 4.5), GALLERY_TOP + 1, 1.8, 1.8,
                 lambda x, y, z: 'coal_block' if noise(x, y, z, 148) < .45 else 'blackstone' if noise(x, y, z, 148) < .8 else 'smooth_basalt',
                 on_gallery)
        out.append({'azimuth': a, 'centre': (cx, cz), 'burning': burning, 'fires': fires, 'lights': lights})
    return out


def light_braziers(lay, braziers):
    """Placed after the prune, which would take a light hanging in the air for a loose block."""
    for b in braziers:
        for x, y, z, level in b['lights']:
            assert lay.get(x, y, z) is None, f'brazier light at {(x, y, z)} lands in {lay.get(x, y, z)}'
            lay.put(x, y, z, f'light[level={level}]')


# ---------------------------------------------------------------- the roof skeleton

def rib_path(b, top, r0=50.5):
    """Samples (x, y, z, r) of a rib from a pier top to the ring."""
    ux, uz = math.cos(math.radians(b)), math.sin(math.radians(b))
    n = 260
    out = []
    for k in range(n + 1):
        t = k / n * math.pi / 2
        h = RING_R + (r0 - RING_R) * math.cos(t)
        y = top + 1 + (RING_Y - top - 1) * math.sin(t)
        out.append((ux * h, y, uz * h, h))
    return out


def rib_stone(x, y, z, low):
    n = noise(x, y, z, 120)
    if n < .07:
        return char(x, y, z, 121)
    if low and n < .35:
        return 'polished_blackstone'
    return 'polished_basalt[axis=y]'


def sweep(lay, samples, b, material, jag_from=None):
    ux, uz = -math.sin(math.radians(b)), math.cos(math.radians(b))
    cells = []
    for i, (px, py, pz, _) in enumerate(samples):
        for dl in (-.5, .5):
            for dv in (-.5, .5):
                c = (math.floor(px + ux * dl + .5), math.floor(py + dv + .5), math.floor(pz + uz * dl + .5))
                if jag_from is not None and i > jag_from and noise(*c, 122) < (i - jag_from) / (len(samples) - jag_from + 1):
                    continue
                cells.append((c, dv < 0))
    for (x, y, z), low in cells:
        lay.put(x, y, z, material(x, y, z, low), replace=False)
    return [c for c, _ in cells]


def build_roof(lay, rng, carriers):
    hangs = []
    lo, hi = WHOLE_RIBS
    for b, top in sorted(carriers.items()):
        path = rib_path(b, top)
        if lo <= b <= hi:
            cells = sweep(lay, path, b, rib_stone)
        else:
            keep = int(len(path) * rng.uniform(.25, .6))
            cells = sweep(lay, path[:keep], b, rib_stone, jag_from=keep - 14)
            hangs.append(path[keep - 1][:3])
        for k in range(rng.randint(2, 4)):
            idx = rng.randint(int(len(path) * .1), len(path) - 1)
            hangs.append(path[min(idx, len(path) - 1)][:3])
    # The ring over the centre: whole over the north-west, broken stubs elsewhere.
    span = (lo - 9, hi + 9)
    n = 340
    ring = []
    for k in range(n):
        a = span[0] + (span[1] - span[0]) * k / (n - 1)
        x, z = polar(RING_R, a)
        ring.append((x, RING_Y, z, RING_R))
    def ring_stone(x, y, z, low):
        n = noise(x, y, z, 123)
        return 'gilded_blackstone' if n < .25 else 'chiseled_polished_blackstone' if low else 'polished_blackstone_bricks'
    for i, (x, y, z, _) in enumerate(ring):
        a = span[0] + (span[1] - span[0]) * i / (n - 1)
        end = min(i, n - 1 - i)
        ux, uz = math.cos(math.radians(a)), math.sin(math.radians(a))
        for dr in (-.5, .5):
            for dv in (-.5, .5):
                c = (math.floor(x + ux * dr + .5), math.floor(y + dv + .5), math.floor(z + uz * dr + .5))
                if end < 20 and noise(*c, 124) < (20 - end) / 22:
                    continue
                lay.put(*c, ring_stone(*c, dv < 0), replace=False)
    for k in range(9):
        hangs.append(ring[rng.randint(15, n - 16)][:3])
    # Chains hang from the ribs and the ring, some still carrying a lantern.
    chains = 0
    for (px, py, pz) in hangs:
        x, z = math.floor(px + .5), math.floor(pz + .5)
        y = math.floor(py + .5) - 1
        while lay.get(x, y, z) is not None and y > 0:
            y -= 1
        if lay.get(x, y + 1, z) is None:
            continue
        r = math.hypot(x, z)
        floor_y = HANG_FLOOR if r <= STEP_R + 2 else max(GALLERY_TOP + 6, HANG_FLOOR - 14)
        room = y - floor_y
        if room < 2:
            continue
        length = rng.randint(2, min(room, 22))
        lantern = rng.random() < .55
        for k in range(length):
            if lay.get(x, y - k, z) is not None:
                break
            last = k == length - 1
            lay.put(x, y - k, z, 'lantern[hanging=true]' if last and lantern else 'iron_chain[axis=y]')
        chains += 1
    return chains


# ---------------------------------------------------------------- invisible wall, connections

def build_barrier(lay):
    for x in range(-42, 43):
        for z in range(-42, 43):
            r = math.hypot(x, z)
            if r <= STEP_R:
                lay.put(x, PLAY_CEILING + 1, z, 'barrier', replace=False)
            if FLOOR_R + .5 < r <= STEP_R:
                for y in range(2, PLAY_CEILING + 2):
                    lay.put(x, y, z, 'barrier')


SOLID_SKIP = ('fire', 'iron_chain', 'lantern', 'barrier', 'light')


def connect(lay):
    """Fences and iron bars join their own kind and full blocks beside them."""
    dirs = (('north', 0, -1), ('south', 0, 1), ('east', 1, 0), ('west', -1, 0))
    for (x, y, z), state in list(lay.b.items()):
        base = state.split(':', 1)[1].split('[')[0]
        if base not in ('nether_brick_fence', 'iron_bars'):
            continue
        props = []
        for name, dx, dz in dirs:
            n = lay.name(x + dx, y, z + dz)
            ok = n is not None and (n == base or (n not in SOLID_SKIP and n not in ('nether_brick_fence', 'iron_bars')))
            if base == 'nether_brick_fence' and n == 'iron_bars':
                ok = False
            props.append(f'{name}={"true" if ok else "false"}')
        lay.b[x, y, z] = f'minecraft:{base}[{",".join(props)},waterlogged=false]'


# ---------------------------------------------------------------- assembly and checks

def build_hall(seed=SEED):
    rng = random.Random(seed)
    lay = Layout()
    heights = crag_heights()
    build_crag(lay, rng, heights)
    build_floor(lay, rng)
    foot = set()
    for az, top in PILLARS.items():
        build_pillar(lay, rng, az, top, foot)
    carriers = build_wall(lay, rng)
    build_throne(lay, rng)
    build_causeways(lay, rng, heights)
    sites = build_spires(lay, random.Random(seed + 1))   # their own stream, so the rest can change freely
    build_dressing(lay, rng, heights)
    braziers = build_braziers(lay)
    chains = build_roof(lay, rng, carriers)
    light_throne(lay)
    pruned = prune(lay)
    light_braziers(lay, braziers)
    build_barrier(lay)
    connect(lay)
    verify(lay, foot, braziers)
    facts = {'braziers': braziers, 'pillarCells': len(foot), 'spires': len(sites), 'chains': chains, 'ribs': len(carriers), 'pruned': pruned,
             'galleryEye': gallery_eye(lay)}
    return lay, facts


def gallery_eye(lay):
    """A clear standing spot on the south-west gallery for the preview's gallery view."""
    for da in range(0, 60):
        for a in (130 + da, 130 - da):
            x, z = (round(v) for v in polar(45, a))
            if all(lay.get(x + dx, y, z + dz) is None for dx in (-2, -1, 0, 1, 2) for dz in (-2, -1, 0, 1, 2) for y in range(3, 7)):
                return [x, GALLERY_TOP + 1, z]
    raise AssertionError('no clear spot on the gallery')


def prune(lay):
    """Drops anything left hanging in the air: broken-off rib ends, spire chips, loose grille."""
    from collections import deque
    seen = {p for p in lay.b if p[1] <= LAVA_Y}
    q = deque(seen)
    while q:
        x, y, z = q.popleft()
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if n in lay.b and n not in seen:
                seen.add(n)
                q.append(n)
    loose = [p for p in lay.b if p not in seen]
    for p in loose:
        del lay.b[p]
    return len(loose)


def verify(lay, foot, braziers):
    names = {}
    for (x, y, z), state in lay.b.items():
        assert LO <= x <= HI and LO <= z <= HI and BOTTOM <= y, f'outside the tiles: {(x, y, z)} {state}'
        base = state.split(':', 1)[1].split('[')[0]
        names[base] = names.get(base, 0) + 1
    bad = set(names) - ALLOWED
    assert not bad, f'blocks outside the palette (lava, gravity or flammable?): {bad}'
    R = FLOOR_R
    for x in range(-R - 1, R + 2):
        for z in range(-R - 1, R + 2):
            r = math.hypot(x, z)
            if r <= R + .5:
                s = lay.name(x, 0, z)
                assert s and s not in ('barrier', 'fire', 'iron_chain', 'lantern', 'nether_brick_fence', 'iron_bars', 'light'), f'floor hole {(x, z)}'
                for y in range(1, PLAY_CEILING + 1):
                    if lay.get(x, y, z) is not None:
                        assert (x, z) in foot, f'something on the fight floor at {(x, y, z)}: {lay.get(x, y, z)}'
            if r <= STEP_R:
                assert lay.get(x, PLAY_CEILING + 1, z) is not None, f'lid hole {(x, z)}'
                for y in range(PLAY_CEILING + 2, HANG_FLOOR):
                    if lay.get(x, y, z) is not None:
                        assert (x, z) in foot, f'hangs too low over the fight at {(x, y, z)}: {lay.get(x, y, z)}'
    for x, y, z in (ARRIVAL, KING):
        assert lay.get(x, y, z) is None and lay.get(x, y + 1, z) is None and lay.get(x, y - 1, z), (x, y, z)
    for (x, y, z), state in lay.b.items():
        if state.startswith('minecraft:light['):
            assert math.hypot(x, z) > STEP_R + 1 or y > HANG_FLOOR, f'invisible light inside the fight volume at {(x, y, z)}'
        if state == 'minecraft:magma_block':
            assert math.hypot(x, z) > WALL_IN, f'magma on the floor, kerb or gallery at {(x, y, z)}: nothing there may burn'
    # The great braziers: whole, lit pairs either side of the gate and the throne, at least ten
    # burning, every flame on netherrack so the dimension's infiniburn keeps it alight.
    lit = [b for b in braziers if b['burning']]
    assert len(braziers) == 12 and len(lit) >= 10, len(lit)
    for axis in (GATE_AZ, THRONE_AZ):
        flank = [b for b in braziers if abs(adiff(b['azimuth'], axis)) < 30]
        assert len(flank) == 2 and all(b['burning'] for b in flank), f'the pair beside {axis} must be whole and lit'
    for b in lit:
        assert len(b['fires']) == 25 and len(b['lights']) == len(BRAZIER_POOL) + 2
        for x, y, z in b['fires']:
            assert lay.name(x, y, z) == 'fire' and lay.name(x, y - 1, z) == 'netherrack', (x, y, z)
        for x, y, z, _ in b['lights']:
            assert lay.name(x, y, z) == 'light'


# ---------------------------------------------------------------- preview output

TEX_ALIAS = {
    'barrier': ('glass',) * 3,
    'polished_basalt': ('polished_basalt_top', 'polished_basalt_side', 'polished_basalt_top'),
    'basalt': ('basalt_top', 'basalt_side', 'basalt_top'),
    'blackstone': ('blackstone_top', 'blackstone', 'blackstone_top'),
    'magma_block': ('magma',) * 3,
    'fire': ('fire_0',) * 3,
    'nether_brick_fence': ('nether_bricks',) * 3,
}
SHAPES = {'iron_chain': 1, 'nether_brick_fence#post': 2, 'nether_brick_fence#ns': 3, 'nether_brick_fence#ew': 4,
          'barrier': 5, 'lantern': 6, 'fire': 7, 'iron_bars#x': 8, 'iron_bars#z': 9}
EMISSIVE = {'fire': (1.0, .55, .22, 15), 'lantern': (1.0, .72, .4, 15), 'magma_block': (1.0, .36, .1, 10)}
BRAZIER_TINT = (1.0, .55, .22)   # a brazier's blaze, as one light in the preview
FILL_TINT = (1.0, .8, .55)   # invisible light blocks: Minecraft light has no colour; a soft warm white, as the gold reads in game


def material_key(state):
    name = state.split(':', 1)[1]
    base, _, props = name.partition('[')
    p = dict(kv.split('=') for kv in props.rstrip(']').split(',')) if props else {}
    ns = p.get('north') == 'true' or p.get('south') == 'true'
    ew = p.get('east') == 'true' or p.get('west') == 'true'
    if base == 'nether_brick_fence':
        return base + ('#ns' if ns and not ew else '#ew' if ew and not ns else '#post')
    if base == 'iron_bars':
        return base + ('#z' if ns and not ew else '#x')
    return base


def texture(archive, name):
    try:
        img = Image.open(io.BytesIO(archive.read(f'assets/minecraft/textures/block/{name}.png'))).convert('RGBA')
    except KeyError:
        print(f'  missing texture: {name}')
        img = Image.new('RGBA', (16, 16), (255, 0, 255, 255))
    return img.crop((0, 0, 16, 16))


def export(lay, facts):
    OUT.mkdir(parents=True, exist_ok=True)
    # Invisible light blocks are not drawn; the preview lights the scene with them instead. The
    # throne's fill counts one by one; each brazier counts as two lights, its blaze and the pool
    # its light blocks throw on the floor, so the preview's light budget covers all twelve.
    braziers = facts['braziers']
    pooled = {(x, y, z) for b in braziers for x, y, z, _ in b['lights']}
    blazing = {f for b in braziers for f in b['fires']}
    blocks = {p: s for p, s in lay.b.items() if material_key(s) != 'light'}
    fill = [(x, y, z, *FILL_TINT, int(s.split('level=')[1].split(']')[0].split(',')[0]))
            for (x, y, z), s in lay.b.items() if material_key(s) == 'light' and (x, y, z) not in pooled]
    for b in braziers:
        if not b['burning']:
            continue
        cx, cz = b['centre']
        fill.append((cx, BRAZIER_RIM + 1.5, cz, *BRAZIER_TINT, 16))
        pool = [l for l in b['lights'] if l[1] < 9]
        fill.append((sum(l[0] for l in pool) / len(pool), sum(l[1] for l in pool) / len(pool), sum(l[2] for l in pool) / len(pool),
                     *FILL_TINT, max(l[3] for l in pool) + .5))
    facts = {k: v for k, v in facts.items() if k != 'braziers'} | {
        'braziers': [{'azimuth': b['azimuth'], 'centre': b['centre'], 'burning': b['burning']} for b in braziers]}
    keys = sorted({material_key(s) for s in blocks.values()})
    tiles, tile_index, materials = [], {}, []

    def tile(archive, name):
        if name not in tile_index:
            tile_index[name] = len(tiles)
            tiles.append(texture(archive, name))
        return tile_index[name]
    with zipfile.ZipFile(JAR) as archive:
        for key in keys:
            base = key.split('#')[0]
            top, side, bottom = TEX_ALIAS.get(base, (base,) * 3)
            materials.append({'name': key, 'shape': SHAPES.get(key, 0),
                              'tex': [tile(archive, top), tile(archive, side), tile(archive, bottom)],
                              'light': EMISSIVE.get(base)})
        lava_tile = tile(archive, 'lava_still')
    assert len(materials) <= 96, len(materials)
    cols = 16
    atlas = Image.new('RGBA', (cols * 16, math.ceil(len(tiles) / cols) * 16))
    for i, img in enumerate(tiles):
        atlas.paste(img, (i % cols * 16, i // cols * 16))
    atlas.save(OUT / 'atlas.png')
    index = {k: i for i, k in enumerate(keys)}
    opaque = {i for i, m in enumerate(materials) if m['shape'] == 0}
    mat = {p: index[material_key(s)] for p, s in blocks.items()}
    raw, lights = bytearray(), []
    count = 0
    for (x, y, z), m in mat.items():
        if y < LAVA_Y - 1:
            continue   # under the lava sheet
        mask = 0
        for bit, (dx, dy, dz) in enumerate(((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            if mat.get((x + dx, y + dy, z + dz)) not in opaque or m not in opaque:
                mask |= 1 << bit
        if mask:
            for v in (x, y, z, m | mask << 8):
                raw += v.to_bytes(2, 'little', signed=True)
            count += 1
        if materials[m]['light'] and (x, y, z) not in blazing:
            name = materials[m]['name']
            if name != 'magma_block' or (mask & 0b100 and noise(x, y, z, 130) < .3):
                lights.append((x, y, z, m))
    data = {'materials': materials, 'atlasCols': cols, 'atlasRows': math.ceil(len(tiles) / cols), 'lavaTile': lava_tile,
            'hall': {'count': count, 'blocks': sum(1 for s in blocks.values() if 'barrier' not in s),
                     'instances': base64.b64encode(bytes(raw)).decode(), 'lights': lights, 'fill': fill},
            'facts': facts | {'floorR': FLOOR_R, 'ceiling': PLAY_CEILING, 'arrival': ARRIVAL, 'king': KING, 'lavaY': LAVA_Y,
                              'bottom': BOTTOM, 'ringY': RING_Y, 'wallIn': WALL_IN, 'galleryTop': GALLERY_TOP,
                              'pillars': [[*polar(PILLAR_R, a), t] for a, t in PILLARS.items()]}}
    (OUT / 'layouts.json').write_text(json.dumps(data, separators=(',', ':')))
    # The Pyre King, posed in his idle by the viewer: the geometry and only that one clip.
    anim = json.loads((KING_ART / 'pyre_king.animation.json').read_text())['animations']
    king = {'geo': json.loads((KING_ART / 'pyre_king.geo.json').read_text()), 'idle': anim['animation.pyre_king.idle']}
    (OUT / 'king.json').write_text(json.dumps(king, separators=(',', ':')))
    shutil.copy(KING_ART / 'pyre_king.png', OUT / 'king.png')
    shutil.copy(KING_ART / 'pyre_king_glowmask.png', OUT / 'king_glow.png')
    with zipfile.ZipFile(JAR) as archive:
        texture(archive, 'lava_still').save(OUT / 'lava.png')
    for f in ('viewer.template.html', 'viewer.js'):
        shutil.copy(HERE / f, OUT / ('index.html' if f.endswith('.html') else f))
    counts = {}
    for s in lay.b.values():
        k = s.split(':', 1)[1].split('[')[0]
        counts[k] = counts.get(k, 0) + 1
    print(f'hall: {len(lay.b)} blocks, {count} drawn, {len(lights)} lights, {len(fill)} invisible fill lights; ' + ', '.join(
        f'{k} {v}' for k, v in sorted(counts.items(), key=lambda kv: -kv[1])[:14]))
    print(f"  {facts['ribs']} piers carry ribs, {facts['chains']} chains, {facts['spires']} spires, {facts['pruned']} loose blocks pruned")


# ---------------------------------------------------------------- mod resources

STRUCTURES = ROOT / 'src/main/resources/data/elementalwands/structure/pyre_hall'


def _nbt_writer():
    """The crypt builder's vanilla structure writer; the realms share the format."""
    import importlib.util
    spec = importlib.util.spec_from_file_location('hollow_crypt_layout', ROOT / 'art/hollow_crypt/build_layout.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.structure_nbt


def realm_tiles(lay):
    structure_nbt = _nbt_writer()
    tiles = {}
    for (x, y, z), state in lay.b.items():
        i, j = (x - TILE_ORIGIN) // TILE, (z - TILE_ORIGIN) // TILE
        assert 0 <= i < TILE_COUNT and 0 <= j < TILE_COUNT and y >= BOTTOM, (x, y, z)
        tiles.setdefault((i, j), {})[x - TILE_ORIGIN - i * TILE, y - BOTTOM, z - TILE_ORIGIN - j * TILE] = state
    return {STRUCTURES / f'hall_{i}_{j}.nbt': structure_nbt(blocks, (TILE, max(p[1] for p in blocks) + 1, TILE))
            for (i, j), blocks in sorted(tiles.items())}


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--serve', action='store_true')
    ap.add_argument('--install', action='store_true', help='write the realm tiles into the mod')
    ap.add_argument('--check', action='store_true', help='verify the installed tiles match this builder')
    args = ap.parse_args()
    hall, facts = build_hall()
    if args.install or args.check:
        files = realm_tiles(hall)
        stale = set(STRUCTURES.glob('hall_*.nbt')) - set(files)
        if args.check:
            for path, data in files.items():
                assert path.exists() and path.read_bytes() == data, f'Drift: {path.relative_to(ROOT)}'
            assert not stale, f'Unexpected tiles: {sorted(p.name for p in stale)}'
            print(f'Burned Throne Hall verified: {len(files)} realm tiles ({len(hall.b)} blocks)')
        else:
            STRUCTURES.mkdir(parents=True, exist_ok=True)
            for path in stale:
                path.unlink()
            for path, data in files.items():
                path.write_bytes(data)
            print(f'Installed {len(files)} realm tiles ({len(hall.b)} blocks, {sum(map(len, files.values())) // 1024} KiB)')
        raise SystemExit
    export(hall, facts)
    print(f'Preview written to {OUT.relative_to(ROOT)}/index.html')
    if args.serve:
        import functools, http.server

        class NoCache(http.server.SimpleHTTPRequestHandler):
            def end_headers(self):
                self.send_header('Cache-Control', 'no-store'); super().end_headers()
        print('Serving on http://127.0.0.1:8420/')
        http.server.ThreadingHTTPServer(('127.0.0.1', 8420), functools.partial(NoCache, directory=str(OUT))).serve_forever()
