#!/usr/bin/env python3
"""Authors the Shattered Nave, the Fractured Guardian's realm: a flat fight floor in the
crossing of an endless cathedral whose pillar grid fades into haze in every direction.

    python3 art/guardian_nave/build_layout.py            # writes the browser preview
    python3 art/guardian_nave/build_layout.py --serve    # ...and serves it on :8370

Coordinates are block cells relative to the crossing's centre; y=0 is the floor, so
players stand at y=1. The preview lives in the ignored .local-previews/guardian-nave/.
"""
import argparse, base64, io, json, math, random, shutil, zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
OUT = ROOT / '.local-previews/guardian-nave'
JAR = Path.home() / '.gradle/caches/fabric-loom/1.21.10/minecraft-client.jar'

# The fight keeps the sky arena's 128-block square floor, cells [-64, 63] on both axes.
HALF = 64
PLAY_CEILING = 40      # invisible lid; the Guardian's leap peaks about 16 blocks up
# The endless hall: a square pillar grid with the crossing's inner pillars left out.
GRID = 44
RING = 2               # grid index of the pillars that frame the crossing (±88)
REACH = 5              # the preview draws pillars out to ±220; the realm repeats them
SPRING = 84            # capital height; the vault ribs spring from here
DOME_RISE = 46         # the crossing's ribs meet at a broken ring this far above the capitals
DOME_RING = 14
MEDALLION_Y = 30
FLOOR_REACH = GRID * REACH + 24
ARRIVAL = (0, 1, 28)   # south of the effigy, facing it
EFFIGY = (0, 1, 0)
SHAFT_DIR = (.42, -1.0, .28)  # light falls from high unseen windows to the north-west
POOL_LIGHT = 12        # invisible light block where each shaft lands
SEED = 0x6A7E

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


def noise(x, y, z, salt=0):
    """Deterministic per-cell value in [0, 1] for weathering."""
    n = (x * 73856093 ^ y * 19349663 ^ z * 83492791 ^ salt * 2654435761) & 0xffffffff
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffffff) / 0xffffff


def weathered(x, y, z, salt=0):
    r = noise(x, y, z, salt)
    if r < .10 + max(0, 18 - y) * .008:
        return 'mossy_stone_bricks'
    return 'cracked_stone_bricks' if r < .30 else 'stone_bricks'


# ---------------------------------------------------------------- cross-sections

def disc(cx, cz, r):
    R = int(math.ceil(r)) + 1
    return {(x, z) for x in range(int(cx) - R, int(cx) + R + 1) for z in range(int(cz) - R, int(cz) + R + 1)
            if (x - cx) ** 2 + (z - cz) ** 2 <= r * r}


def octagon(h):
    R = int(h)
    return frozenset((x, z) for x in range(-R, R + 1) for z in range(-R, R + 1) if abs(x) + abs(z) <= h * 1.414)


def grow(cells, n=1):
    for _ in range(n):
        cells = cells | {(x + dx, z + dz) for x, z in cells for dx, dz in SIDES}
    return frozenset(cells)


_rims = {}


def rim(cells):
    key = id(cells)
    if key not in _rims:
        _rims[key] = (cells, {(x, z) for x, z in cells if any((x + dx, z + dz) not in cells for dx, dz in SIDES)})
    return _rims[key][1]


def pier(scale):
    """Compound gothic pier: a round core, four half-round shafts on the axes and four
    slim colonettes on the diagonals. Returns cell -> role."""
    roles = {}
    for ax, az in SIDES:
        for c in disc(ax * 7 * scale, az * 7 * scale, 3.2 * scale):
            roles.setdefault(c, 'shaft')
    for sx in (-1, 1):
        for sz in (-1, 1):
            for c in disc(sx * 5 * scale, sz * 5 * scale, 1.8 * scale):
                roles.setdefault(c, 'colonette')
    for c in disc(0, 0, 6.2 * scale):
        roles.setdefault(c, 'core')
    return roles


# ---------------------------------------------------------------- the hall

def grid_points():
    for i in range(-REACH, REACH + 1):
        for j in range(-REACH, REACH + 1):
            if max(abs(i), abs(j)) >= RING:
                yield i, j


def rubble(x, y, z):
    r = noise(x, y, z, 17)
    return 'andesite' if r < .4 else 'cobblestone' if r < .62 else 'cracked_stone_bricks' if r < .88 else 'cobbled_deepslate'


def plan_damage(rng, ring, corner, faces, scale, can_fall):
    """How a pillar is broken. Damage stays clear of the lit medallions at MEDALLION_Y,
    except on a fallen pillar, whose whole middle is gone."""
    breaks, gouges, strips = [], [], []
    fallen = can_fall and rng.random() < .13
    if fallen:
        # Collapsed: a stump, then nothing until the capital hanging from the vault.
        b0 = rng.randint(14, 24)
        breaks.append((b0, rng.randint(58, 66)))
    elif not corner and rng.random() < (.6 if ring else .72):
        b0 = rng.randint(40, 60) if ring else rng.randint(37, 62)
        breaks.append((b0, b0 + (rng.randint(6, 10) if ring else rng.randint(8, 15))))
        if not ring and rng.random() < .45:
            b0 = rng.randint(11, 14)
            breaks.append((b0, b0 + rng.randint(6, 9)))
    for _ in range(rng.randint(3, 7)):
        a = rng.uniform(0, 2 * math.pi)
        d = rng.uniform(7.5, 10) * scale
        cy = rng.choice([rng.randint(9, 19), rng.randint(42, SPRING - 9)])
        gouges.append((d * math.cos(a), cy, d * math.sin(a), rng.uniform(3, 5.5) * scale))
    if rng.random() < .6:
        for _ in range(rng.randint(1, 2)):
            axis = rng.choice(SIDES)
            y0 = rng.randint(8, 60)
            if axis in faces:
                y0 = max(y0, 39)
            strips.append((axis, y0, min(SPRING - 7, y0 + rng.randint(10, 28))))
    return {'breaks': breaks, 'gouges': gouges, 'strips': strips, 'fallen': fallen}


def fallen_drums(lay, rng, gx, gz, axis, side):
    """The collapsed middle of a pillar lies across the neighbouring bay as giant drums,
    partly sunk into the floor, their broken ends showing the rough core."""
    ax, az = axis
    px, pz = -az * side, ax * side
    roles = pier(.9)
    section = frozenset(roles)
    edge = rim(section)
    along = rng.randint(12, 15)
    while True:
        length = rng.randint(6, 10)
        if along + length > 42:
            break
        lat = round(22 + rng.uniform(-1.5, 1.5))
        lift = 10 - rng.randint(0, 2)
        for t in range(along, along + length):
            cap = t in (along, along + length - 1)
            for (w, v), role in roles.items():
                y = v + lift
                if y < 1 or not (cap or (w, v) in edge):
                    continue
                x, z = gx + ax * t + px * (lat + w), gz + az * t + pz * (lat + w)
                if in_fight(x, y, z):
                    continue
                if cap:
                    state = rubble(x, y, z)
                elif role == 'shaft':
                    state = 'polished_diorite' if noise(x, y, z, 3) > .1 else 'cracked_stone_bricks'
                elif role == 'colonette':
                    state = 'polished_andesite'
                else:
                    state = weathered(x, y, z, 5)
                lay.put(x, y, z, state)
        along += length + rng.randint(1, 3)


def build_pillar(lay, gx, gz, scale, damage, salt):
    roles = pier(scale)
    shaft = frozenset(roles)
    base, step, plinth_top = octagon(12.5 * scale), octagon(11 * scale), octagon(10 * scale)
    annulet = grow(shaft)
    layers = {1: (base, 'polished_andesite'), 2: (base, 'chiseled_stone_bricks'),
              3: (step, None), 4: (step, None), 5: (step, None), 6: (plinth_top, 'chiseled_stone_bricks'),
              7: (annulet, 'polished_andesite')}
    for y in range(8, SPRING - 4):
        layers[y] = (annulet, 'chiseled_stone_bricks') if (y - 8) % 16 == 15 else (shaft, None)
    capital = [(grow(shaft, 1), 'chiseled_stone_bricks'), (grow(shaft, 1), 'polished_andesite'),
               (grow(shaft, 2), 'polished_andesite'), (grow(shaft, 3), 'chiseled_stone_bricks'),
               (octagon(12 * scale), 'polished_deepslate')]
    for i, layer in enumerate(capital):
        layers[SPRING - 4 + i] = layer
    breaks, gouges, strips = damage['breaks'], damage['gouges'], damage['strips']

    def intact(c, y):
        # Snapped: the shaft stops with a jagged top, hangs open for a stretch and carries on above.
        for b0, b1 in breaks:
            if b0 - 3 <= y <= b1 + 3 and not (y < b0 + int(noise(c[0], b0, c[1], salt) * 4)
                                              or y >= b1 - int(noise(c[0], b1, c[1], salt) * 4)):
                return False
        # Bitten: a rough chunk knocked out of the surface.
        for cx, cy, cz, r in gouges:
            if (c[0] - cx) ** 2 + ((y - cy) * 1.3) ** 2 + (c[1] - cz) ** 2 <= r * r:
                return False
        # Stripped: an outer half-column has fallen away and bared the core.
        for (ax, az), y0, y1 in strips:
            if y0 <= y <= y1 and c[0] * ax + c[1] * az > 6.2 * scale and abs(c[0] * az - c[1] * ax) <= 4.3 * scale \
                    and roles.get(c) != 'colonette':
                return False
        return True
    hit = set()
    for b0, b1 in breaks:
        hit.update(range(b0 - 3, b1 + 4))
    for _, cy, _, r in gouges:
        hit.update(range(int(cy - r) - 1, int(cy + r) + 2))
    for _, y0, y1 in strips:
        hit.update(range(y0, y1 + 1))
    whole = {}
    for y in hit & layers.keys():
        cells, mat = layers[y]
        whole[y] = cells
        layers[y] = (frozenset(c for c in cells if intact(c, y)), mat)
    for y, (cells, mat) in layers.items():
        above = layers.get(y + 1, (frozenset(),))[0]
        below = layers[y - 1][0] if y > 1 else None
        edge = rim(cells)
        outside = rim(whole[y]) if y in whole else None
        for c in cells:
            if c in edge or c not in above or (below is not None and c not in below):
                x, z = gx + c[0], gz + c[1]
                if outside is not None and c not in outside:
                    state = rubble(x, y, z)  # the pillar's rough core, bared by the damage
                elif mat:
                    state = mat
                else:
                    role = roles.get(c)
                    if role == 'shaft':
                        state = 'polished_diorite' if noise(x, y, z, 3) > .07 else 'polished_andesite'
                    elif role == 'colonette':
                        state = 'polished_andesite'
                    else:
                        state = weathered(x, y, z, 5)
                lay.put(x, y, z, state)
    return int(10.2 * scale)


def medallion(lay, gx, gz, ax, az, reach, scale, chipped=0.0):
    """Broken-ring emblem on a shaft face, lit at its heart like the church facade.
    A chipped one has lost some of its copper."""
    ux, uz = -az, ax
    for u in range(-4, 5):
        for v in range(-4, 5):
            d = u * u + v * v
            if d > 13:
                continue
            for depth in range(int(7 * scale), reach + 1):
                lay.put(gx + ax * depth + ux * u, MEDALLION_Y + v, gz + az * depth + uz * u, 'chiseled_stone_bricks')
            if 6 <= d <= 13 and not (u > 0 and v > 0) and noise(gx + u, MEDALLION_Y + v, gz + ax + az, 19) >= chipped:
                lay.put(gx + ax * (reach + 1) + ux * u, MEDALLION_Y + v, gz + az * (reach + 1) + uz * u, 'oxidized_copper')
    lay.put(gx + ax * (reach + 1), MEDALLION_Y, gz + az * (reach + 1), 'sea_lantern')


def sweep(lay, samples, material):
    """Drags a 3x3 cross-section (lateral x vertical) along a path of (point, lateral)."""
    for (px, py, pz), (lx, lz) in samples:
        for dl in (-1, -.5, 0, .5, 1):
            for dv in (-1, 0, 1):
                x, y, z = round(px + lx * dl), round(py + dv), round(pz + lz * dl)
                lay.put(x, y, z, material(x, y, z, dv), replace=False)


def arch(a, b, ratio):
    """Pointed arch from pillar centre a to b, springing at the capitals."""
    (ax, az), (bx, bz) = a, b
    span = math.hypot(bx - ax, bz - az)
    ux, uz = (bx - ax) / span, (bz - az) / span
    r = ratio * span
    apex = math.acos((span / 2 - r) / r)
    n = int(r * (math.pi - apex) / .3) + 1
    half = [(r + r * math.cos(math.pi - (math.pi - apex) * i / n), SPRING + r * math.sin(math.pi - (math.pi - apex) * i / n))
            for i in range(n + 1)]
    path = half + [(span - s, y) for s, y in reversed(half)]
    return [((ax + ux * s, y, az + uz * s), (-uz, ux)) for s, y in path], SPRING + r * math.sin(apex)


def rib_stone(x, y, z, dv):
    if dv < 0:
        return 'polished_deepslate'
    return 'chiseled_stone_bricks' if noise(x, y, z, 9) < .06 else 'polished_andesite'


MASONRY = ['stone_bricks', 'cracked_stone_bricks', 'polished_andesite', 'chiseled_stone_bricks', 'mossy_stone_bricks']
SHAFT_PIECES = ['polished_diorite', 'polished_diorite', 'polished_andesite', 'cracked_stone_bricks']


def in_fight(x, y, z):
    """Inside the walled play space, where loose stone would be an obstacle."""
    return -HALF - 1 <= x <= HALF and -HALF - 1 <= z <= HALF and 0 < y <= PLAY_CEILING + 1


def fragment(lay, rng, cx, cy, cz, size, palette=MASONRY):
    """An irregular chunk of masonry hanging in the air."""
    R = int(math.ceil(size))
    for dx in range(-R, R + 1):
        for dy in range(-R, R + 1):
            for dz in range(-R, R + 1):
                if dx * dx + dy * dy * 1.6 + dz * dz <= size * size * (.55 + .6 * noise(cx + dx, cy + dy, cz + dz, 11)) \
                        and not in_fight(cx + dx, cy + dy, cz + dz):
                    lay.put(cx + dx, cy + dy, cz + dz, rng.choice(palette))


def floor_block(x, z):
    r = math.hypot(x, z)
    if r < 18.6:
        # The effigy's seat: concentric inlays with the broken ring, gap to the north-east.
        if r < 2.6:
            return 'chiseled_stone_bricks'
        if r < 8.5:
            return 'deepslate_tiles'
        if r < 10:
            return 'oxidized_cut_copper'
        if r < 15:
            spoke = abs(((math.atan2(z, x) + math.pi / 12) % (math.pi / 6)) - math.pi / 12) * r
            return 'polished_deepslate' if spoke < .7 else 'polished_andesite'
        if 16 <= r < 17.6 and not (x > 0 and z < 0):
            return 'oxidized_copper'
        return 'polished_deepslate'
    inside = -HALF <= x < HALF and -HALF <= z < HALF
    edge = min(x + HALF, HALF - 1 - x, z + HALF, HALF - 1 - z)
    if -1 <= edge <= 1:
        # A dark band marks where the fight floor ends; the invisible wall stands on its outer row.
        if edge == 1 and inside:
            return 'chiseled_stone_bricks'
        return 'polished_deepslate'
    qx, qz = x % GRID, z % GRID
    if qx <= 1 or qx >= GRID - 1 or qz <= 1 or qz >= GRID - 1:
        return 'polished_deepslate'  # bands run along every pillar line, through the crossing too
    if (qx - 2) % 8 == 0 or (qz - 2) % 8 == 0:
        return 'stone_bricks'
    checker = ((qx - 2) // 8 + (qz - 2) // 8) % 2
    tile = 'polished_tuff' if checker else 'polished_andesite'
    if not inside:
        wear = noise(x, 0, z, 13)
        if wear < .05:
            return 'cracked_stone_bricks'
        if wear < .08:
            return 'mossy_stone_bricks'
    return tile


def build_nave(seed=SEED):
    rng = random.Random(seed)
    lay = Layout()
    for x in range(-FLOOR_REACH, FLOOR_REACH + 1):
        for z in range(-FLOOR_REACH, FLOOR_REACH + 1):
            lay.put(x, 0, z, floor_block(x, z))
    # Light inlays: the seat's ring and every 16 blocks along the fight floor's border.
    for k in range(8):
        a = (k + .5) * math.pi / 4
        lay.put(round(12.5 * math.cos(a)), 0, round(12.5 * math.sin(a)), 'sea_lantern')
    for t in range(-HALF + 8, HALF, 16):
        for x, z in ((t, -HALF), (t, HALF - 1), (-HALF, t), (HALF - 1, t)):
            lay.put(x, 0, z, 'sea_lantern')
    # Fractures run out from the seat under the whole floor, glowing where they are deepest.
    for k in range(7):
        a = k * 2 * math.pi / 7 + rng.uniform(-.3, .3)
        px, pz = 19 * math.cos(a), 19 * math.sin(a)
        for s in range(rng.randint(60, 140)):
            a += rng.uniform(-.28, .28)
            px, pz = px + math.cos(a), pz + math.sin(a)
            cx, cz = round(px), round(pz)
            lay.put(cx, 0, cz, 'sea_lantern' if s % 11 == 5 else 'cracked_deepslate_tiles')
            if rng.random() < .35:
                side = rng.choice(SIDES)
                if lay.get(cx + side[0], 0, cz + side[1]) != 'minecraft:sea_lantern':
                    lay.put(cx + side[0], 0, cz + side[1], 'cracked_stone_bricks')

    pillars = {}
    for i, j in grid_points():
        gx, gz = i * GRID, j * GRID
        corner = abs(i) == RING and abs(j) == RING
        scale = 1.35 if corner else 1.0
        faces = [(ax, az) for ax, az in SIDES if ax * -gx + az * -gz > 0]
        damage = plan_damage(rng, max(abs(i), abs(j)) == RING, corner, faces, scale, RING < max(abs(i), abs(j)) < REACH)
        reach = build_pillar(lay, gx, gz, scale, damage, i * 31 + j)
        pillars[i, j] = (gx, gz)
        if damage['fallen']:
            outward = [(ax, az) for ax, az in SIDES if abs(gx + ax * 42) <= GRID * REACH and abs(gz + az * 42) <= GRID * REACH]
            fallen_drums(lay, rng, gx, gz, rng.choice(outward), rng.choice((-1, 1)))
        else:
            for ax, az in faces:
                medallion(lay, gx, gz, ax, az, reach, scale, rng.choice((0, 0, .15, .3)))
        # What broke off still hangs where it fell from.
        for b0, b1 in damage['breaks']:
            for _ in range(rng.randint(6, 11)):
                ang = rng.uniform(0, 2 * math.pi)
                d = rng.uniform(4, 13) * scale
                fragment(lay, rng, round(gx + d * math.cos(ang)), rng.randint(b0, b1),
                         round(gz + d * math.sin(ang)), rng.uniform(.8, 2.2))
        for cx, cy, cz, r in damage['gouges']:
            if rng.random() < .5:
                d = math.hypot(cx, cz)
                out = (d + r + rng.uniform(1.5, 4)) / d
                fragment(lay, rng, round(gx + cx * out), round(cy + rng.uniform(-2, 2)), round(gz + cz * out), rng.uniform(.6, 1.3))
        for (ax, az), y0, y1 in damage['strips']:
            for _ in range(rng.randint(2, 4)):
                d = rng.uniform(12, 16) * scale
                side = rng.uniform(-4, 4)
                fragment(lay, rng, round(gx + ax * d - az * side), rng.randint(y0, y1), round(gz + az * d + ax * side),
                         rng.uniform(.8, 1.6), SHAFT_PIECES)
        # Fallen masonry at the foot of pillars outside the fight floor, more under broken ones.
        for _ in range(rng.randint(0, 3) + 2 * len(damage['breaks'])):
            ang = rng.uniform(0, 2 * math.pi)
            d = rng.uniform(13, 17) * scale
            x0, z0 = round(gx + d * math.cos(ang)), round(gz + d * math.sin(ang))
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    if dx * dx + dz * dz <= rng.uniform(2, 5) and not in_fight(x0 + dx, 1, z0 + dz):
                        lay.put(x0 + dx, 1, z0 + dz, rng.choice(['cracked_stone_bricks', 'stone_bricks', 'polished_andesite']))
                        if dx * dx + dz * dz <= 1 and rng.random() < .5:
                            lay.put(x0 + dx, 2, z0 + dz, 'cracked_stone_bricks')

    # Rib vaults over the aisles: pointed arches along the grid and across each bay.
    keystones = []
    for (i, j), a in pillars.items():
        for di, dj in ((1, 0), (0, 1)):
            if (i + di, j + dj) in pillars:
                path, _ = arch(a, pillars[i + di, j + dj], .72)
                sweep(lay, path, rib_stone)
        if all(p in pillars for p in ((i + 1, j), (i, j + 1), (i + 1, j + 1))):
            path, top = arch(a, pillars[i + 1, j + 1], .72)
            sweep(lay, path, rib_stone)
            path, _ = arch(pillars[i + 1, j], pillars[i, j + 1], .72)
            sweep(lay, path, rib_stone)
            keystones.append((a[0] + GRID // 2, round(top), a[1] + GRID // 2))
    for x, y, z in keystones:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for dy in (-1, 0, 1):
                    lay.put(x + dx, y + dy, z + dz, 'chiseled_stone_bricks')
        lay.put(x, y - 2, z, 'sea_lantern')

    # The crossing: ribs from every ring pillar arch inward to a broken ring above the effigy.
    ring_y = SPRING + DOME_RISE
    gap = lambda ang: -math.radians(78) < ang < -math.radians(12)
    for (i, j), (gx, gz) in pillars.items():
        if max(abs(i), abs(j)) != RING:
            continue
        r0 = math.hypot(gx, gz)
        ux, uz = gx / r0, gz / r0
        end = DOME_RING + (3 if gap(math.atan2(gz, gx)) else 0)
        n = int((r0 + DOME_RISE) * 1.6 / .3)
        samples = []
        for k in range(n + 1):
            t = k / n * math.pi / 2
            h = end + (r0 - end) * math.cos(t)
            samples.append(((ux * h, SPRING + DOME_RISE * math.sin(t), uz * h), (-uz, ux)))
        if gap(math.atan2(gz, gx)):
            samples = samples[:int(len(samples) * .86)]  # these ribs end in the air at the ring's break
        sweep(lay, samples, rib_stone)
    n = int(2 * math.pi * DOME_RING / .3)
    ring = []
    for k in range(n):
        ang = -math.pi + 2 * math.pi * k / n
        if not gap(ang):
            ring.append(((DOME_RING * math.cos(ang), ring_y, DOME_RING * math.sin(ang)), (math.cos(ang), math.sin(ang))))
    sweep(lay, ring, lambda x, y, z, dv: 'oxidized_copper' if dv >= 0 else 'polished_deepslate')
    for k in range(12):
        ang = -math.pi + (k + .5) * math.pi / 6
        if not gap(ang):
            lay.put(round(DOME_RING * math.cos(ang)), ring_y - 2, round(DOME_RING * math.sin(ang)), 'sea_lantern')
    for _ in range(9):
        ang = rng.uniform(-math.radians(78), -math.radians(12))
        d = rng.uniform(DOME_RING - 3, DOME_RING + 10)
        fragment(lay, rng, round(d * math.cos(ang)), ring_y + rng.randint(-8, 3), round(d * math.sin(ang)), rng.uniform(.9, 1.8))

    # Masonry hanging in the air: over the crossing only above the invisible lid.
    for _ in range(14):
        ang, d = rng.uniform(0, 2 * math.pi), rng.uniform(10, 70)
        fragment(lay, rng, round(d * math.cos(ang)), rng.randint(PLAY_CEILING + 8, 78), round(d * math.sin(ang)), rng.uniform(1, 2.6))
    for (i, j), (gx, gz) in pillars.items():
        if (i + 1, j + 1) in pillars and rng.random() < .55:
            for _ in range(rng.randint(1, 3)):
                fragment(lay, rng, gx + rng.randint(12, 32), rng.randint(8, 70), gz + rng.randint(12, 32), rng.uniform(.8, 2.4))

    # Invisible wall and lid around the fight floor.
    lo, hi = -HALF - 1, HALF
    for t in range(lo, hi + 1):
        for y in range(1, PLAY_CEILING + 2):
            for x, z in ((t, lo), (t, hi), (lo, t), (hi, t)):
                lay.put(x, y, z, 'barrier')
    for x in range(lo, hi + 1):
        for z in range(lo, hi + 1):
            lay.put(x, PLAY_CEILING + 1, z, 'barrier')

    shafts = light_shafts(rng, pillars)
    for shaft in shafts:
        x, _, z = (math.floor(v) for v in shaft['bottom'])
        if lay.get(x, 1, z) is None:
            lay.put(x, 1, z, f'light[level={POOL_LIGHT}]')
    return lay, shafts


def light_shafts(rng, pillars):
    """Coloured light falling from unseen high windows. A mod render effect in game, not
    blocks; the floor pools become invisible light blocks (Minecraft light has no colour)."""
    colours = [((.35, .85, .95), 5), ((.5, .7, 1.0), 3), ((1.0, .84, .52), 2), ((.82, .52, .92), 1)]
    pick = lambda: rng.choices([c for c, _ in colours], [w for _, w in colours])[0]
    dx, dy, dz = SHAFT_DIR
    spots = [(-30, 14), (26, -34), (38, 30), (-14, -46), (-44, 40)]
    while len(spots) < 42:
        x, z = rng.uniform(-200, 200), rng.uniform(-200, 200)
        if max(abs(x), abs(z)) < 70:
            continue
        if any(math.hypot(x - gx, z - gz) < 16 for gx, gz in pillars.values()):
            continue
        spots.append((round(x), round(z)))
    out = []
    for x, z in spots:
        t = 150 / -dy
        out.append({'bottom': [x + .5, 0.5, z + .5], 'top': [round(x + .5 - dx * t, 2), 150.5, round(z + .5 - dz * t, 2)],
                    'width': round(rng.uniform(5, 9), 2), 'colour': pick()})
    return out


# ---------------------------------------------------------------- preview output

SHAPES = {'cube': 0, 'barrier': 5}
TEX_ALIAS = {'barrier': ('glass',) * 3}
EMISSIVE = {'sea_lantern': (.62, .9, 1.0, 15)}


def material_key(state):
    return state.split(':', 1)[1].split('[')[0]


def texture(archive, name):
    try:
        img = Image.open(io.BytesIO(archive.read(f'assets/minecraft/textures/block/{name}.png'))).convert('RGBA')
    except KeyError:
        img = Image.new('RGBA', (16, 16), (255, 0, 255, 255))
    return img.crop((0, 0, 16, 16))


def export(lay, shafts):
    OUT.mkdir(parents=True, exist_ok=True)
    blocks = {p: s for p, s in lay.b.items() if material_key(s) != 'light'}
    keys = sorted({material_key(s) for s in blocks.values()})
    tiles, tile_index, materials = [], {}, []

    def tile(archive, name):
        if name not in tile_index:
            tile_index[name] = len(tiles)
            tiles.append(texture(archive, name))
        return tile_index[name]
    with zipfile.ZipFile(JAR) as archive:
        for key in keys:
            top, side, bottom = TEX_ALIAS.get(key, (key,) * 3)
            materials.append({'name': key, 'shape': SHAPES['barrier' if key == 'barrier' else 'cube'],
                              'tex': [tile(archive, top), tile(archive, side), tile(archive, bottom)],
                              'light': EMISSIVE.get(key)})
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
        mask = 0
        for bit, (dx, dy, dz) in enumerate(((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            if mat.get((x + dx, y + dy, z + dz)) not in opaque or m not in opaque:
                mask |= 1 << bit
        if y == 0 and m in opaque:
            mask &= ~(1 << 3)
        if mask:
            for v in (x, y, z, m | mask << 8):
                raw += v.to_bytes(2, 'little', signed=True)
            count += 1
        if materials[m]['light']:
            lights.append((x, y, z, m))
    data = {'materials': materials, 'atlasCols': cols, 'atlasRows': math.ceil(len(tiles) / cols),
            'layouts': {'nave': {'count': count, 'blocks': sum(1 for s in blocks.values() if 'barrier' not in s),
                                 'instances': base64.b64encode(bytes(raw)).decode(), 'lights': lights}},
            'shafts': shafts,
            'nave': {'half': HALF, 'ceiling': PLAY_CEILING, 'grid': GRID, 'ring': RING * GRID, 'reach': REACH * GRID,
                     'spring': SPRING, 'domeTop': SPRING + DOME_RISE, 'arrival': ARRIVAL, 'effigy': EFFIGY}}
    (OUT / 'layouts.json').write_text(json.dumps(data, separators=(',', ':')))
    for f in ('viewer.template.html', 'viewer.js'):
        shutil.copy(HERE / f, OUT / ('index.html' if f.endswith('.html') else f))
    counts = {}
    for s in lay.b.values():
        k = material_key(s)
        counts[k] = counts.get(k, 0) + 1
    print(f'nave: {len(lay.b)} blocks, {count} drawn; ' + ', '.join(f'{k} {v}' for k, v in sorted(counts.items(), key=lambda kv: -kv[1])[:10]))


# ---------------------------------------------------------------- mod resources

TILE = 48                # realm tiles are placed one per server tick
TILE_ORIGIN = -264       # tile (i, j) covers x/z from TILE_ORIGIN + TILE * index
TILE_COUNT = 11
STRUCTURES = ROOT / 'src/main/resources/data/elementalwands/structure/shattered_nave'
SHAFTS_JAVA = ROOT / 'src/main/java/com/anton/elementalwands/arena/ShatteredNaveShafts.java'


def _nbt_writer():
    """The crypt builder's vanilla structure writer; both realms share the format."""
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
        assert 0 <= i < TILE_COUNT and 0 <= j < TILE_COUNT and y >= 0, (x, y, z)
        tiles.setdefault((i, j), {})[x - TILE_ORIGIN - i * TILE, y, z - TILE_ORIGIN - j * TILE] = state
    return {STRUCTURES / f'nave_{i}_{j}.nbt': structure_nbt(blocks, (TILE, max(p[1] for p in blocks) + 1, TILE))
            for (i, j), blocks in sorted(tiles.items())}


def shafts_java(shafts):
    rows = ''.join('        {%s, %s, %s, %s, %s, %s, 0x%02x%02x%02x},\n' % (
        s['bottom'][0], s['bottom'][2], s['top'][0], s['top'][1], s['top'][2], s['width'],
        *(round(c * 255) for c in s['colour'])) for s in shafts)
    return ('package com.anton.elementalwands.arena;\n\n'
            '/**\n'
            ' * Generated by art/guardian_nave/build_layout.py: the light shafts that fall into the nave\n'
            ' * from unseen windows. Offsets from the crossing centre, with y above the floor surface:\n'
            ' * bottom x, bottom z, top x, top y, top z, width, colour. The floor pools under them are\n'
            ' * invisible light blocks in the realm tiles.\n'
            ' */\n'
            'public final class ShatteredNaveShafts {\n'
            '    private ShatteredNaveShafts() {}\n\n'
            '    public static final double[][] SHAFTS = {\n'
            f'{rows}'
            '    };\n'
            '}\n')


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--serve', action='store_true')
    ap.add_argument('--install', action='store_true', help='write the realm tiles and light shafts into the mod')
    ap.add_argument('--check', action='store_true', help='verify the installed tiles and shafts match this builder')
    args = ap.parse_args()
    nave, shafts = build_nave()
    if args.install or args.check:
        files = realm_tiles(nave)
        stale = set(STRUCTURES.glob('nave_*.nbt')) - set(files)
        files[SHAFTS_JAVA] = shafts_java(shafts).encode()
        if args.check:
            for path, data in files.items():
                assert path.exists() and path.read_bytes() == data, f'Drift: {path.relative_to(ROOT)}'
            assert not stale, f'Unexpected tiles: {sorted(p.name for p in stale)}'
            print(f'Shattered Nave verified: {len(files) - 1} realm tiles ({len(nave.b)} blocks) and {len(shafts)} light shafts')
        else:
            STRUCTURES.mkdir(parents=True, exist_ok=True)
            for path in stale:
                path.unlink()
            for path, data in files.items():
                path.write_bytes(data)
            print(f'Installed {len(files) - 1} realm tiles and {len(shafts)} light shafts '
                  f'({sum(map(len, files.values())) // 1024} KiB)')
        raise SystemExit
    export(nave, shafts)
    print(f'Preview written to {OUT.relative_to(ROOT)}/index.html')
    if args.serve:
        import functools, http.server

        class NoCache(http.server.SimpleHTTPRequestHandler):
            def end_headers(self):
                self.send_header('Cache-Control', 'no-store'); super().end_headers()
        http.server.ThreadingHTTPServer(('127.0.0.1', 8370), functools.partial(NoCache, directory=str(OUT))).serve_forever()
