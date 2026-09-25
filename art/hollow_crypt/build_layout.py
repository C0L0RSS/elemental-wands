#!/usr/bin/env python3
"""Authors the Hollow Crypt block layouts: the realm clearing with its dead forest,
and the overworld graveyard where the ritual starts.

    python3 art/hollow_crypt/build_layout.py            # writes the browser preview
    python3 art/hollow_crypt/build_layout.py --serve    # ...and serves it on :8360

Coordinates are relative to each layout's centre; y=0 is the ground surface, so
players stand at y=1. The preview lives in the ignored .local-previews/hollow-crypt/.
"""
import argparse, base64, gzip, io, json, math, random, shutil, struct, zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
OUT = ROOT / '.local-previews/hollow-crypt'
JAR = Path.home() / '.gradle/caches/fabric-loom/1.21.10/minecraft-client.jar'

# Realm dimensions, shared with the Java arena rules once installed.
PLAY_RADIUS = 44      # players and the boss stay inside this horizontal radius
PLAY_CEILING = 30     # ...and below this height above the ground
BARRIER_RADIUS = 45   # invisible wall shell just outside the play radius
FOREST_INNER = 49
FOREST_OUTER = 124
GROUND_RADIUS = 132
ARRIVAL = (0, 1, 34)  # south edge of the clearing, facing the centre


class Layout:
    def __init__(self):
        self.b = {}

    def put(self, x, y, z, state, replace=True):
        state = state if ':' in state else 'minecraft:' + state
        if replace or (x, y, z) not in self.b:
            self.b[x, y, z] = state

    def get(self, x, y, z):
        return self.b.get((x, y, z))

    def clear(self, x, y, z):
        self.b.pop((x, y, z), None)


def value_noise(seed):
    """Smooth 2D noise in [0,1] for ground patches."""
    rng = random.Random(seed)
    grid = {}

    def corner(i, j):
        if (i, j) not in grid:
            grid[i, j] = rng.random()
        return grid[i, j]

    def sample(x, z, scale):
        fx, fz = x / scale, z / scale
        i, j = math.floor(fx), math.floor(fz)
        u, v = fx - i, fz - j
        u, v = u * u * (3 - 2 * u), v * v * (3 - 2 * v)
        a, b = corner(i, j), corner(i + 1, j)
        c, d = corner(i, j + 1), corner(i + 1, j + 1)
        return (a * (1 - u) + b * u) * (1 - v) + (c * (1 - u) + d * u) * v
    return sample


# ---------------------------------------------------------------- dead trees

def line(a, b):
    """Face-connected voxel path from a to b (every step shares a face)."""
    x, y, z = a
    pts = [(x, y, z)]
    fx, fy, fz = map(float, a)
    steps = max(1, int(max(abs(b[0] - a[0]), abs(b[1] - a[1]), abs(b[2] - a[2])) * 2))
    for s in range(1, steps + 1):
        t = s / steps
        nx, ny, nz = (round(a[i] + (b[i] - a[i]) * t) for i in range(3))
        # Move one axis at a time so diagonal hops never leave a gap.
        for axis in (1, 0, 2):
            target = (nx, ny, nz)[axis]
            while (x, y, z)[axis] != target:
                step = 1 if target > (x, y, z)[axis] else -1
                if axis == 0: x += step
                elif axis == 1: y += step
                else: z += step
                pts.append((x, y, z))
    return pts


class TreeBuilder:
    def __init__(self, lay, rng):
        self.lay, self.rng = lay, rng

    def wood(self, kind):
        return {'dark': 'dark_oak_wood', 'pale': 'pale_oak_wood', 'bleak': 'stripped_dark_oak_wood'}[kind]

    def branch(self, start, yaw, pitch, length, kind, depth, inward, avoid):
        """Draws a tapering branch, forking into twigs. Returns its tip."""
        rng = self.rng
        pts, pos = [], list(map(float, start))
        segs = max(2, int(length / 3))
        for s in range(segs):
            # Dead branches kink and droop a little as they reach out.
            yaw += rng.uniform(-.35, .35)
            pitch += rng.uniform(-.18, .12) - .04 * s
            seg = length / segs
            nxt = [pos[0] + math.cos(yaw) * math.cos(pitch) * seg,
                   pos[1] + math.sin(pitch) * seg,
                   pos[2] + math.sin(yaw) * math.cos(pitch) * seg]
            pts += line(tuple(map(round, pos)), tuple(map(round, nxt)))
            pos = nxt
        placed = []
        for p in pts:
            if avoid(p):
                break
            placed.append(p)
            self.lay.put(*p, self.wood(kind), replace=False)
        if not placed:
            return None
        tip = placed[-1]
        # Twigs: short thin sticks off the far half, ending in fence posts.
        if depth < 2:
            for p in placed[len(placed) // 2::max(2, len(placed) // 3)]:
                if rng.random() < .7:
                    self.branch(p, yaw + rng.choice((-1, 1)) * rng.uniform(.5, 1.3),
                                pitch + rng.uniform(-.1, .5), rng.uniform(2, 5) * (1 - depth * .4),
                                kind, depth + 1, inward, avoid)
        tx, ty, tz = tip
        if not avoid((tx, ty + 1, tz)) and self.lay.get(tx, ty + 1, tz) is None and rng.random() < .6:
            self.lay.put(tx, ty + 1, tz, 'dark_oak_fence' if kind != 'pale' else 'pale_oak_fence', replace=False)
        # Hanging moss drips from the undersides of the older limbs.
        for p in placed:
            if rng.random() < (.16 if kind == 'pale' else .09) and depth < 2:
                self.moss(p, rng.randint(1, 5), avoid)
        return tip

    def moss(self, p, length, avoid):
        x, y, z = p
        run = []
        for d in range(1, length + 1):
            q = (x, y - d, z)
            if self.lay.get(*q) is not None or avoid(q) or q[1] < 3:
                break
            run.append(q)
        for i, q in enumerate(run):
            self.lay.put(*q, 'pale_hanging_moss[tip=%s]' % ('true' if i == len(run) - 1 else 'false'))

    def tree(self, bx, bz, height, kind, lean_to, giant, avoid):
        rng, lay = self.rng, self.lay
        # A slow curve: the trunk leans, then bends back a little near the top.
        ang = math.atan2(lean_to[1] - bz, lean_to[0] - bx) if lean_to else rng.uniform(0, math.tau)
        lean = rng.uniform(.08, .22) if lean_to else rng.uniform(0, .12)
        wobble = rng.uniform(0, math.tau)
        trunk = []
        x, z = float(bx), float(bz)
        for y in range(1, height + 1):
            t = y / height
            dx = math.cos(ang) * lean * (1 - .5 * t) + .12 * math.sin(wobble + y * .23)
            dz = math.sin(ang) * lean * (1 - .5 * t) + .12 * math.cos(wobble + y * .19)
            prev = (round(x), round(z))
            x, z = x + dx, z + dz
            cur = (round(x), round(z))
            if cur != prev:
                lay.put(cur[0], y - 1, prev[1], self.wood(kind))  # keep the trunk face-connected
            thick = giant and t < .55 or t < .12
            for ox, oz in ([(0, 0), (1, 0), (0, 1), (1, 1)] if thick else [(0, 0)]):
                lay.put(cur[0] + ox, y, cur[1] + oz, self.wood(kind))
            trunk.append((cur[0], y, cur[1]))
        # Root flare: buttresses that sink into the forest floor.
        for i in range(rng.randint(3, 5) + (3 if giant else 0)):
            a = rng.uniform(0, math.tau)
            reach = rng.randint(2, 4 if not giant else 6)
            for d in range(1, reach + 1):
                rx, rz = round(bx + .5 + math.cos(a) * d), round(bz + .5 + math.sin(a) * d)
                ry = max(0, round((reach - d) * (1.2 if giant else .7)) - (1 if d == reach else 0))
                for yy in range(0, ry + 1):
                    lay.put(rx, yy, rz, self.wood(kind) if d < reach or rng.random() < .5 else 'mangrove_roots')
        # Main limbs from mid-height up. Inner trees reach over the clearing.
        y0 = int(height * rng.uniform(.3, .45))
        y = y0
        while y < height - 2:
            for _ in range(rng.choice((1, 1, 2))):
                px, py, pz = trunk[y - 1]
                t = (y - y0) / max(1, height - y0)
                if lean_to and rng.random() < .55:
                    yaw = math.atan2(lean_to[1] - pz, lean_to[0] - px) + rng.uniform(-.6, .6)
                else:
                    yaw = rng.uniform(0, math.tau)
                length = (1 - .55 * t) * rng.uniform(.25, .45) * height * (1.25 if giant else 1)
                self.branch((px, py, pz), yaw, rng.uniform(.25, .7) - .15 * t, length, kind, 0, lean_to, avoid)
            y += rng.randint(3, 7)
        # A broken or forked crown.
        px, py, pz = trunk[-1]
        for _ in range(rng.randint(1, 3)):
            self.branch((px, py, pz), rng.uniform(0, math.tau), rng.uniform(.7, 1.2),
                        rng.uniform(3, 7), kind, 1, lean_to, avoid)
        return trunk


def poisson_annulus(rng, inner, outer, spacing, tries=30):
    pts = []
    cell = spacing(outer) / math.sqrt(2)
    grid = {}

    def ok(p):
        s = spacing(math.hypot(*p))
        gx, gz = int(p[0] // cell), int(p[1] // cell)
        for i in range(gx - 3, gx + 4):
            for j in range(gz - 3, gz + 4):
                for q in grid.get((i, j), ()):
                    if math.dist(p, q) < s:
                        return False
        return True
    active = []
    for _ in range(8):
        a, r = rng.uniform(0, math.tau), rng.uniform(inner, outer)
        p = (math.cos(a) * r, math.sin(a) * r)
        if ok(p):
            pts.append(p); active.append(p); grid.setdefault((int(p[0] // cell), int(p[1] // cell)), []).append(p)
    while active:
        base = active.pop(rng.randrange(len(active)))
        for _ in range(tries):
            s = spacing(math.hypot(*base))
            a, d = rng.uniform(0, math.tau), rng.uniform(s, 2 * s)
            p = (base[0] + math.cos(a) * d, base[1] + math.sin(a) * d)
            if inner <= math.hypot(*p) <= outer and ok(p):
                pts.append(p); active.append(p); grid.setdefault((int(p[0] // cell), int(p[1] // cell)), []).append(p)
    return pts


# ---------------------------------------------------------------- the realm

def build_realm(seed=0x4011_0C27):
    rng = random.Random(seed)
    lay = Layout()
    noise = value_noise(seed + 1)
    noise2 = value_noise(seed + 2)

    def in_play(p):
        x, y, z = p
        return y <= PLAY_CEILING + 1 and math.hypot(x + .5, z + .5) <= BARRIER_RADIUS + 1.5

    # Ground: bare clearing, darker forest floor beyond the rim.
    for x in range(-GROUND_RADIUS, GROUND_RADIUS + 1):
        for z in range(-GROUND_RADIUS, GROUND_RADIUS + 1):
            r = math.hypot(x, z)
            if r > GROUND_RADIUS:
                continue
            n, m = noise(x, z, 9), noise2(x, z, 4)
            if r <= BARRIER_RADIUS + 2:
                block = ('coarse_dirt' if n < .42 else 'podzol' if n < .7 else 'rooted_dirt')
                if m > .83: block = 'gravel'
                if r < 20 and noise2(x, z, 6) > .62: block = 'soul_soil'
            else:
                block = ('podzol' if n < .45 else 'coarse_dirt' if n < .7 else 'pale_moss_block')
                if m > .86: block = 'rooted_dirt'
            lay.put(x, 0, z, block)
            # Sparse litter and dead scrub; the forest floor is busier.
            roll = rng.random()
            if r <= PLAY_RADIUS:
                if roll < .035: lay.put(x, 1, z, 'leaf_litter')
                elif roll < .043 and r > 9: lay.put(x, 1, z, 'dead_bush')
            elif r <= GROUND_RADIUS - 2:
                if roll < .09: lay.put(x, 1, z, 'leaf_litter')
                elif roll < .12: lay.put(x, 1, z, 'dead_bush')
                elif roll < .135 and block == 'pale_moss_block': lay.put(x, 1, z, 'pale_moss_carpet')
                elif roll < .1375 and r > FOREST_INNER + 6: lay.put(x, 1, z, 'firefly_bush')

    # The summoning circle at the centre stays flush so the colossus can crawl over it.
    for x in range(-8, 9):
        for z in range(-8, 9):
            r = math.hypot(x, z)
            lay.clear(x, 1, z)
            if 6.4 <= r <= 7.6:
                lay.put(x, 0, z, 'polished_blackstone' if (x + z) % 3 else 'chiseled_polished_blackstone')
            elif r < 6.4 and (abs(x) == abs(z) or x == 0 or z == 0) and r > 1.5:
                lay.put(x, 0, z, 'cracked_polished_blackstone_bricks')
            elif r <= 1.5:
                lay.put(x, 0, z, 'soul_soil')
            elif r < 6.4:
                lay.put(x, 0, z, 'soul_soil' if noise2(x, z, 2) > .55 else 'coarse_dirt')

    # Cover: fallen trunks, stumps and old grave markers, low enough to crawl past.
    def fallen(cx, cz, yaw, length, kind):
        dx, dz = math.cos(yaw), math.sin(yaw)
        axis = 'x' if abs(dx) > abs(dz) else 'z'
        log = ('dark_oak_log' if kind == 'dark' else 'pale_oak_log') + '[axis=%s]' % axis
        for d in range(-length // 2, length // 2 + 1):
            x, z = round(cx + dx * d), round(cz + dz * d)
            lay.put(x, 1, z, log)
            if abs(d) < length // 3 and rng.random() < .35:
                lay.put(x, 2, z, log)
        # Snapped limbs poke upward from the log.
        for d in rng.sample(range(-length // 2, length // 2), 2):
            x, z = round(cx + dx * d), round(cz + dz * d)
            lay.put(x + round(-dz), 2, z + round(dx), 'dark_oak_wood' if kind == 'dark' else 'pale_oak_wood')

    def stump(cx, cz, h):
        for ox, oz in ((0, 0), (1, 0), (0, 1), (1, 1)):
            for y in range(1, h + (1 if (ox + oz) % 2 else 0) + 1):
                lay.put(cx + ox, y, cz + oz, 'dark_oak_wood')
        for ox, oz in ((-1, 0), (2, 1), (1, -1), (0, 2)):
            lay.put(cx + ox, 1, cz + oz, 'mangrove_roots')

    def marker(cx, cz, yaw):
        lay.put(cx, 1, cz, 'cobbled_deepslate')
        lay.put(cx, 2, cz, rng.choice(['cobbled_deepslate', 'mossy_cobblestone', 'polished_deepslate']))
        if rng.random() < .5:
            lay.put(cx, 3, cz, 'deepslate_tile_slab[type=bottom]')

    cover = [('fallen', 20, .35), ('stump', 27, 1.2), ('marker', 17, 2.0), ('fallen', 31, 2.7),
             ('marker', 22, 3.4), ('stump', 16, 4.0), ('fallen', 26, 4.6), ('marker', 30, 5.3),
             ('marker', 14, 5.8), ('stump', 33, 6.1)]
    for what, r, a in cover:
        cx, cz = round(math.cos(a) * r), round(math.sin(a) * r)
        if math.hypot(cx - ARRIVAL[0], cz - ARRIVAL[2]) < 7:
            continue
        if what == 'fallen':
            fallen(cx, cz, a + math.pi / 2 + rng.uniform(-.4, .4), rng.randint(6, 10), rng.choice(['dark', 'dark', 'pale']))
        elif what == 'stump':
            stump(cx, cz, rng.randint(2, 3))
        else:
            for i in range(rng.randint(1, 3)):
                marker(cx + i * 2 * round(math.cos(a + 1.57)), cz + i * 2 * round(math.sin(a + 1.57)), a)

    # Soul-fire braziers around the rim give the only warm-ish light.
    for i in range(10):
        a = i / 10 * math.tau + .12
        cx, cz = round(math.cos(a) * 41), round(math.sin(a) * 41)
        lay.put(cx, 1, cz, 'polished_blackstone_bricks')
        lay.put(cx, 2, cz, 'chiseled_polished_blackstone')
        lay.put(cx, 3, cz, 'soul_campfire[lit=true]')

    # The dead forest. Nearer trees lean in; the farthest ones are the tallest.
    trees = TreeBuilder(lay, rng)
    spacing = lambda r: 6.0 + (r - FOREST_INNER) / (FOREST_OUTER - FOREST_INNER) * 3.5
    bases = poisson_annulus(rng, FOREST_INNER, FOREST_OUTER, spacing)
    bases.sort(key=lambda p: math.hypot(*p))
    for bx, bz in bases:
        r = math.hypot(bx, bz)
        t = (r - FOREST_INNER) / (FOREST_OUTER - FOREST_INNER)
        giant = rng.random() < .06 + .12 * t
        height = int(24 + 34 * t + rng.uniform(-5, 9) + (18 if giant else 0))
        kind = rng.choices(['dark', 'pale', 'bleak'], [.74, .18, .08])[0]
        inner = r < FOREST_INNER + 20
        trees.tree(round(bx), round(bz), height, kind, (0, 0) if inner else None, giant, in_play)

    # A handful of sentinel giants whose high boughs arch out over the rim.
    for i in range(7):
        a = i / 7 * math.tau + rng.uniform(-.2, .2)
        r = FOREST_INNER + rng.uniform(2, 6)
        bx, bz = round(math.cos(a) * r), round(math.sin(a) * r)
        trunk = trees.tree(bx, bz, rng.randint(62, 78), 'dark', (0, 0), True, in_play)
        for y in (PLAY_CEILING + 6, PLAY_CEILING + 14):
            px, py, pz = trunk[min(y, len(trunk)) - 1]
            yaw = math.atan2(-pz, -px) + rng.uniform(-.4, .4)
            trees.branch((px, py, pz), yaw, rng.uniform(-.05, .15), rng.uniform(16, 24), 'dark', 0, (0, 0),
                         lambda p: p[1] <= PLAY_CEILING + 3 and math.hypot(p[0], p[2]) < BARRIER_RADIUS + 1)

    # Nothing solid may sit inside the play volume except the authored clearing.
    for (x, y, z), s in list(lay.b.items()):
        if 1 <= y <= PLAY_CEILING + 1 and math.hypot(x + .5, z + .5) <= BARRIER_RADIUS + .5 \
                and ('wood' in s or 'fence' in s or 'moss[' in s or 'mangrove' in s) and math.hypot(x, z) > PLAY_RADIUS - 2:
            lay.clear(x, y, z)

    # Invisible wall: a cylinder shell and a lid over the play volume.
    for x in range(-BARRIER_RADIUS - 1, BARRIER_RADIUS + 2):
        for z in range(-BARRIER_RADIUS - 1, BARRIER_RADIUS + 2):
            r = math.hypot(x + .5, z + .5)
            if BARRIER_RADIUS - .5 <= r <= BARRIER_RADIUS + .9:
                for y in range(1, PLAY_CEILING + 2):
                    lay.put(x, y, z, 'barrier')
            if r < BARRIER_RADIUS + .9:
                lay.put(x, PLAY_CEILING + 1, z, 'barrier')
    return lay


# ---------------------------------------------------------------- overworld graveyard

def build_graveyard(seed=0x6EA7):
    rng = random.Random(seed)
    lay = Layout()
    noise = value_noise(seed)
    W, N, S = 13, -17, 13   # half-width, north and south edges (z)
    for x in range(-W - 3, W + 4):
        for z in range(N - 3, S + 5):
            for y in (-2, -1):
                lay.put(x, y, z, 'dirt')
            n = noise(x, z, 5)
            inside = abs(x) <= W and N <= z <= S
            lay.put(x, 0, z, ('coarse_dirt' if n < .35 else 'podzol' if n < .55 else 'grass_block') if inside else 'grass_block')
    # Path from the lychgate to the open grave.
    for z in range(N + 4, S + 4):
        for x in (-1, 0, 1):
            lay.put(x, 0, z, rng.choice(['gravel', 'coarse_dirt', 'cobblestone', 'gravel']))
    # Ruined boundary wall with iron-dark fence runs and gaps.
    for x in range(-W, W + 1):
        for z in range(N, S + 1):
            if abs(x) != W and z not in (N, S):
                continue
            if z == S and abs(x) <= 2:
                continue  # gate
            if noise(x * 3, z * 3, 3) < .18:
                continue  # collapsed section
            corner = abs(x) == W and z in (N, S)
            h = 3 if corner else (1 if noise(x, z, 2) < .5 else 2)
            for y in range(1, h + 1):
                lay.put(x, y, z, rng.choice(['mossy_cobblestone', 'cobblestone', 'mossy_stone_bricks', 'cobbled_deepslate']))
            if not corner and h == 1 and rng.random() < .7:
                lay.put(x, 2, z, 'dark_oak_fence')
            if corner:
                lay.put(x, 4, z, 'soul_lantern')
    # Lychgate: a small roofed gate with soul lanterns.
    for x in (-3, 3):
        for y in range(1, 5):
            lay.put(x, y, S, 'dark_oak_log[axis=y]')
    for x in range(-4, 5):
        lay.put(x, 5, S, 'dark_oak_planks')
        lay.put(x, 5, S - 1, 'dark_oak_slab[type=bottom]')
        lay.put(x, 5, S + 1, 'dark_oak_slab[type=bottom]')
    for x in range(-3, 4):
        lay.put(x, 6, S, 'dark_oak_slab[type=bottom]')
    for x in (-2, 2):
        lay.put(x, 4, S, 'soul_lantern[hanging=true]')
    # Offering chest just inside the gate.
    lay.put(3, 1, S - 2, 'chest[facing=west]')
    # Rows of graves either side of the path.
    for z in range(N + 7, S - 3, 4):
        for side in (-1, 1):
            for x0 in (4, 8):
                if rng.random() < .15:
                    continue
                x = side * x0 + rng.choice((-1, 0, 0, 1))
                style = rng.randint(0, 3)
                if style == 0:
                    lay.put(x, 1, z, 'mossy_cobblestone_wall')
                elif style == 1:
                    lay.put(x, 1, z, 'cobbled_deepslate'); lay.put(x, 2, z, 'deepslate_tile_slab[type=bottom]')
                elif style == 2:
                    lay.put(x, 1, z, 'chiseled_stone_bricks')
                else:
                    lay.put(x, 1, z, 'polished_andesite'); lay.put(x, 2, z, 'polished_andesite_slab[type=bottom]')
                # A sunken mound in front of each marker.
                for dz in (1, 2):
                    lay.put(x, 0, z + dz, rng.choice(['coarse_dirt', 'rooted_dirt', 'podzol']))
                if rng.random() < .3:
                    lay.put(x, 1, z + 1, rng.choice(['dead_bush', 'candle[lit=false,candles=2]', 'leaf_litter']))
    # A dead tree in the north-east corner, hung with pale moss.
    trees = TreeBuilder(lay, rng)
    trees.tree(9, N + 4, 16, 'dark', None, False, lambda p: False)
    # The open grave and its altar at the north end.
    for x in range(-4, 5):
        for z in range(N + 1, N + 7):
            lay.put(x, 0, z, 'deepslate_tiles' if (x + z) % 4 else 'cracked_deepslate_tiles')
    for x in (-1, 0, 1):
        for z in range(N + 3, N + 7):
            if x != 0 and z in (N + 3, N + 6):
                continue
            lay.clear(x, 0, z); lay.clear(x, -1, z)
            lay.put(x, -2, z, 'soul_soil')
    for x in range(-2, 3):
        for z in (N + 2, N + 7):
            lay.put(x, 0, z, 'polished_deepslate')
    # Headstone altar: the offering goes here. Custom block later; carved stone for now.
    for x in (-2, -1, 0, 1, 2):
        lay.put(x, 1, N + 2, 'polished_deepslate')
    for x in (-1, 0, 1):
        lay.put(x, 2, N + 2, 'chiseled_deepslate')
    lay.put(0, 3, N + 2, 'chiseled_deepslate')
    lay.put(0, 4, N + 2, 'skeleton_skull[rotation=8]')
    lay.put(0, 2, N + 3, 'polished_deepslate')  # offering ledge
    for x in (-2, 2):
        lay.put(x, 2, N + 2, 'candle[lit=true,candles=3]')
    for x in (-4, 4):
        for y in (1, 2, 3):
            lay.put(x, y, N + 2, 'polished_blackstone_bricks')
        lay.put(x, 4, N + 2, 'soul_campfire[lit=true]')
    return lay


# ---------------------------------------------------------------- preview output

SHAPES = {'cube': 0, 'cross': 1, 'post': 2, 'carpet': 3, 'slab': 4, 'barrier': 5, 'hidden': 6, 'lantern': 7, 'skull': 8, 'candle': 9}
TEX_ALIAS = {
    'dark_oak_wood': ('dark_oak_log',) * 3, 'pale_oak_wood': ('pale_oak_log',) * 3,
    'stripped_dark_oak_wood': ('stripped_dark_oak_log',) * 3,
    'dark_oak_log': ('dark_oak_log_top', 'dark_oak_log', 'dark_oak_log_top'),
    'pale_oak_log': ('pale_oak_log_top', 'pale_oak_log', 'pale_oak_log_top'),
    'podzol': ('podzol_top', 'podzol_side', 'dirt'), 'grass_block': ('grass_block_top', 'grass_block_side', 'dirt'),
    'mangrove_roots': ('mangrove_roots_top', 'mangrove_roots_side', 'mangrove_roots_top'),
    'polished_blackstone': ('polished_blackstone',) * 3, 'chiseled_polished_blackstone': ('chiseled_polished_blackstone',) * 3,
    'soul_campfire': ('soul_campfire_log_lit', 'soul_campfire_fire', 'soul_campfire_log_lit'),
    'dark_oak_fence': ('dark_oak_planks',) * 3, 'pale_oak_fence': ('pale_oak_planks',) * 3,
    'deepslate_tile_slab': ('deepslate_tiles',) * 3, 'polished_andesite_slab': ('polished_andesite',) * 3,
    'dark_oak_slab': ('dark_oak_planks',) * 3, 'mossy_cobblestone_wall': ('mossy_cobblestone',) * 3,
    'deepslate': ('deepslate_top', 'deepslate', 'deepslate_top'), 'chest': ('dark_oak_planks',) * 3,
    'pale_moss_carpet': ('pale_moss_carpet',) * 3, 'pale_hanging_moss': ('pale_hanging_moss',) * 3,
    'skeleton_skull': ('bone_block_top', 'bone_block_side', 'bone_block_top'), 'candle': ('bone_block_side',) * 3,
    'barrier': ('glass',) * 3, 'polished_deepslate': ('polished_deepslate',) * 3,
    'soul_lantern': ('soul_lantern',) * 3, 'leaf_litter': ('leaf_litter',) * 3,
}
SHAPE_OF = {'dead_bush': 'cross', 'firefly_bush': 'cross', 'pale_hanging_moss': 'cross',
            'dark_oak_fence': 'post', 'pale_oak_fence': 'post', 'mossy_cobblestone_wall': 'post',
            'leaf_litter': 'carpet', 'pale_moss_carpet': 'carpet', 'soul_campfire': 'slab',
            'deepslate_tile_slab': 'slab', 'polished_andesite_slab': 'slab', 'dark_oak_slab': 'slab',
            'barrier': 'barrier', 'soul_lantern': 'lantern', 'skeleton_skull': 'skull', 'candle': 'candle'}
EMISSIVE = {'soul_campfire': (.35, .85, 1.0, 14), 'soul_lantern': (.35, .85, 1.0, 10),
            'firefly_bush': (.9, .95, .45, 4), 'candle': (1.0, .75, .4, 5)}


def material_key(state):
    name = state.split(':', 1)[1]
    base = name.split('[')[0]
    if base == 'pale_hanging_moss' and 'tip=true' in name:
        return 'pale_hanging_moss_tip'
    return base


def texture(archive, name):
    try:
        img = Image.open(io.BytesIO(archive.read(f'assets/minecraft/textures/block/{name}.png'))).convert('RGBA')
    except KeyError:
        img = Image.new('RGBA', (16, 16), (255, 0, 255, 255))
    return img.crop((0, 0, 16, 16))


def export(layouts):
    OUT.mkdir(parents=True, exist_ok=True)
    keys = sorted({material_key(s) for lay in layouts.values() for s in lay.b.values()})
    tiles, tile_index, materials = [], {}, []

    def tile(archive, name):
        if name not in tile_index:
            img = texture(archive, name)
            if name == 'grass_block_top':
                tint = Image.new('RGBA', (16, 16), (110, 140, 70, 255))
                img = Image.composite(Image.blend(img, tint, .0), img, img)
                px = img.load()
                for i in range(16):
                    for j in range(16):
                        r, g, b, a = px[i, j]
                        px[i, j] = (r * 110 // 255, g * 150 // 255, b * 80 // 255, a)
            tile_index[name] = len(tiles)
            tiles.append(img)
        return tile_index[name]
    with zipfile.ZipFile(JAR) as archive:
        for key in keys:
            base = 'pale_hanging_moss' if key == 'pale_hanging_moss_tip' else key
            top, side, bottom = TEX_ALIAS.get(base, (base,) * 3)
            if key == 'pale_hanging_moss_tip':
                top = side = bottom = 'pale_hanging_moss_tip'
            shape = SHAPE_OF.get(base, 'cube')
            materials.append({'name': key, 'shape': SHAPES[shape],
                              'tex': [tile(archive, top), tile(archive, side), tile(archive, bottom)],
                              'light': EMISSIVE.get(base)})
    cols = 16
    atlas = Image.new('RGBA', (cols * 16, math.ceil(len(tiles) / cols) * 16))
    for i, img in enumerate(tiles):
        atlas.paste(img, (i % cols * 16, i // cols * 16))
    atlas.save(OUT / 'atlas.png')
    index = {k: i for i, k in enumerate(keys)}
    opaque = {i for i, m in enumerate(materials) if m['shape'] == 0}
    data = {'materials': materials, 'atlasCols': cols, 'atlasRows': math.ceil(len(tiles) / cols), 'layouts': {}}
    for name, lay in layouts.items():
        mat = {p: index[material_key(s)] for p, s in lay.b.items()}
        rows, lights = [], []
        for (x, y, z), m in mat.items():
            mask = 0
            for bit, (dx, dy, dz) in enumerate(((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
                if mat.get((x + dx, y + dy, z + dz)) not in opaque or m not in opaque:
                    mask |= 1 << bit
            if y == 0 and m in opaque and name == 'realm':
                mask &= ~(1 << 3)
            if mask:
                rows.append((x, y, z, m | mask << 8))
            if materials[m]['light']:
                lights.append((x, y, z, m))
        flat = [v for row in rows for v in row]
        raw = b''.join(v.to_bytes(2, 'little', signed=True) for v in flat)
        data['layouts'][name] = {'count': len(rows), 'blocks': len(lay.b), 'instances': base64.b64encode(raw).decode(),
                                 'lights': lights}
    data['realm'] = {'playRadius': PLAY_RADIUS, 'ceiling': PLAY_CEILING, 'barrierRadius': BARRIER_RADIUS,
                     'forestInner': FOREST_INNER, 'forestOuter': FOREST_OUTER, 'arrival': ARRIVAL}
    (OUT / 'layouts.json').write_text(json.dumps(data, separators=(',', ':')))
    for f in ('viewer.template.html', 'viewer.js'):
        shutil.copy(HERE / f, OUT / ('index.html' if f.endswith('.html') else f))
    for name, lay in layouts.items():
        counts = {}
        for s in lay.b.values():
            k = material_key(s); counts[k] = counts.get(k, 0) + 1
        print(f'{name}: {len(lay.b)} blocks; ' + ', '.join(f'{k} {v}' for k, v in sorted(counts.items(), key=lambda kv: -kv[1])[:8]))


# ---------------------------------------------------------------- mod resources

TILE = 48                # realm tiles are placed one per server tick
TILE_ORIGIN = -192       # tile (i, j) covers x/z from TILE_ORIGIN + TILE * index
TILE_COUNT = 8
STRUCTURES = ROOT / 'src/main/resources/data/elementalwands/structure/hollow_crypt'
DATA_VERSION = 4556


def _string(s):
    b = s.encode()
    return struct.pack('>H', len(b)) + b


def _payload(t, v):
    if t == 3: return struct.pack('>i', v)
    if t == 8: return _string(v)
    if t == 9:
        kind, vs = v
        return bytes([kind]) + struct.pack('>i', len(vs)) + b''.join(_payload(kind, a) for a in vs)
    if t == 10: return b''.join(bytes([k]) + _string(n) + _payload(k, vv) for n, (k, vv) in v.items()) + b'\0'
    raise ValueError(t)


def _palette_entry(state):
    name, _, props = state.partition('[')
    entry = {'Name': (8, name)}
    if props:
        entry['Properties'] = (10, {k: (8, v) for k, v in (p.split('=') for p in props.rstrip(']').split(','))})
    return entry


def structure_nbt(blocks, size):
    """Vanilla structure template. Absent positions are left untouched when placed."""
    palette, index, entries = [], {}, []
    for (x, y, z), state in sorted(blocks.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
        if state not in index:
            index[state] = len(palette)
            palette.append(_palette_entry(state))
        entries.append({'pos': (9, (3, [x, y, z])), 'state': (3, index[state])})
    root = {'DataVersion': (3, DATA_VERSION), 'size': (9, (3, list(size))), 'palette': (9, (10, palette)),
            'blocks': (9, (10, entries)), 'entities': (9, (10, []))}
    return gzip.compress(b'\x0a\0\0' + _payload(10, root), mtime=0)


def realm_tiles(lay):
    tiles = {}
    for (x, y, z), state in lay.b.items():
        i, j = (x - TILE_ORIGIN) // TILE, (z - TILE_ORIGIN) // TILE
        assert 0 <= i < TILE_COUNT and 0 <= j < TILE_COUNT and y >= 0, (x, y, z)
        tiles.setdefault((i, j), {})[x - TILE_ORIGIN - i * TILE, y, z - TILE_ORIGIN - j * TILE] = state
    out = {}
    for (i, j), blocks in sorted(tiles.items()):
        height = max(p[1] for p in blocks) + 1
        out[STRUCTURES / f'realm_{i}_{j}.nbt'] = structure_nbt(blocks, (TILE, height, TILE))
    return out


GRAVEYARD = ROOT / 'src/main/resources/data/elementalwands/structure/hollow_graveyard.nbt'


def graveyard_nbt(lay):
    """Worldgen template. Air above the ground clears grass and bumps inside the footprint;
    jigsaw placement does not treat absent positions specially, so nothing below is omitted."""
    xs, ys, zs = zip(*lay.b)
    lo = (min(xs), min(ys), min(zs))
    size = (max(xs) - lo[0] + 1, max(ys) - lo[1] + 1, max(zs) - lo[2] + 1)
    blocks = {}
    for x in range(size[0]):
        for z in range(size[2]):
            for y in range(size[1]):
                state = lay.b.get((x + lo[0], y + lo[1], z + lo[2]))
                if state is None and y + lo[1] >= 1:
                    state = 'minecraft:air'
                if state is not None:
                    blocks[x, y, z] = state
    return structure_nbt(blocks, size)


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--serve', action='store_true')
    ap.add_argument('--install', action='store_true', help='write the realm tiles and graveyard structure into the mod')
    ap.add_argument('--check', action='store_true', help='verify the installed tiles match this builder')
    args = ap.parse_args()
    realm = build_realm()
    if args.install or args.check:
        files = realm_tiles(realm)
        stale = set(STRUCTURES.glob('realm_*.nbt')) - set(files)
        files[GRAVEYARD] = graveyard_nbt(build_graveyard())
        if args.check:
            for path, data in files.items():
                assert path.exists() and path.read_bytes() == data, f'Drift: {path.relative_to(ROOT)}'
            assert not stale, f'Unexpected tiles: {sorted(p.name for p in stale)}'
            print(f'Hollow Crypt verified: {len(files) - 1} realm tiles ({len(realm.b)} blocks) and the graveyard')
        else:
            STRUCTURES.mkdir(parents=True, exist_ok=True)
            for path in stale:
                path.unlink()
            for path, data in files.items():
                path.write_bytes(data)
            print(f'Installed {len(files) - 1} realm tiles and the graveyard ({sum(map(len, files.values())) // 1024} KiB)')
        raise SystemExit
    export({'realm': realm, 'graveyard': build_graveyard()})
    print(f'Preview written to {OUT.relative_to(ROOT)}/index.html')
    if args.serve:
        import functools, http.server

        class NoCache(http.server.SimpleHTTPRequestHandler):
            def end_headers(self):
                self.send_header('Cache-Control', 'no-store'); super().end_headers()
        http.server.ThreadingHTTPServer(('127.0.0.1', 8360), functools.partial(NoCache, directory=str(OUT))).serve_forever()
