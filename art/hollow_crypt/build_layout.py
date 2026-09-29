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
        self.carved = set()  # below-ground cells a structure template must dig out as air

    def put(self, x, y, z, state, replace=True):
        state = state if ':' in state else 'minecraft:' + state
        if replace or (x, y, z) not in self.b:
            self.b[x, y, z] = state
            self.carved.discard((x, y, z))

    def get(self, x, y, z):
        return self.b.get((x, y, z))

    def clear(self, x, y, z):
        self.b.pop((x, y, z), None)

    def carve(self, x, y, z):
        self.clear(x, y, z)
        self.carved.add((x, y, z))


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
        lay.put(cx + 1, h + 2, cz, 'soul_lantern')  # On a taller corner; pools light inside the clearing.

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
            lay.put(cx, 3, cz, 'soul_lantern')  # A grave light on the first marker, replacing any cap.

    # Soul-fire braziers ring the rim, dark until the intro's slam lights them; the lanterns on the
    # cover above light the clearing.
    for i in range(10):
        a = i / 10 * math.tau + .12
        cx, cz = round(math.cos(a) * 41), round(math.sin(a) * 41)
        lay.put(cx, 1, cz, 'polished_blackstone_bricks')
        lay.put(cx, 2, cz, 'chiseled_polished_blackstone')
        lay.put(cx, 3, cz, 'soul_campfire[lit=false]')  # Lit by the Necromancer's intro slam.

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


# ---------------------------------------------------------------- mausoleum doorway model

# The mausoleum's doorway frame is one carved stone arch, authored in pixels and sliced into
# block models. Arch space: ax runs left to right as seen from the yard across five cells, ay
# up from the threshold across seven, and ad out of the facade (0 is the wall's face, -16 its
# back). The opening is a pointed arch whose sides rise straight to the springing.
ARCH_COLS, ARCH_ROWS = 7, 9
ARCH_W = ARCH_COLS * 16
ARCH_MID = ARCH_W // 2            # centreline
OPEN_L, SPRING = 32, 48           # the opening spans ax 32..80 and springs at ay 48
BANDS = (48, 52, 57, 60)          # arc radii: opening, inner order, outer order, hood moulding
CORNICE = 112                     # the frame's top row sits in the cornice course
# A pale marble frame against the dark facade, so the way in is plain from the gate.
ARCH_TEX = {'wall': 'minecraft:block/deepslate_bricks', 'cornice': 'minecraft:block/polished_deepslate',
            'outer': 'minecraft:block/calcite', 'inner': 'minecraft:block/polished_diorite',
            'carved': 'minecraft:block/chiseled_stone_bricks', 'bone': 'minecraft:block/bone_block_side',
            'hood': 'minecraft:block/polished_blackstone', 'glow': 'elementalwands:block/mausoleum_glow'}
VEIL_COLS, VEIL_ROWS = 3, 6       # the veil's image spans the opening: 48 x 96 pixels
VEIL_EYE = 46                     # the vortex's eye, in pixels above the threshold


def arch_edge(ay, r):
    """Left edge of the band of radius r at height ay. The left arc is centred on the opening's
    right edge at the springing, as in an equilateral arch; past the apex it is the centreline."""
    centre, dy = OPEN_L + BANDS[0], ay - SPRING
    if dy < 0:
        return centre - r
    if dy >= r:
        return ARCH_MID
    return min(ARCH_MID, centre - math.sqrt(r * r - dy * dy))


def arch_boxes():
    """The whole frame as (ax0, ay0, ad0, ax1, ay1, ad1, texture, light) boxes, left half
    authored and mirrored where the carving is symmetric."""
    boxes, open_rows = [], {}
    # Two-pixel courses across the facade: flush wall; above the springing a raised hood
    # moulding; a proud outer order; an inner order; then the reveal back to the veil. Courses
    # with the same spans merge into one tall box.
    for ay in range(0, ARCH_ROWS * 16, 2):
        hood, o1, o2, op = (round(arch_edge(ay + 1, r)) for r in reversed(BANDS))
        if ay < SPRING:
            hood = o1
        wall = 'cornice' if CORNICE <= ay < CORNICE + 16 else 'wall'
        row = set()
        for tex, x0, x1, front in ((wall, 0, hood, 0), ('carved', hood, o1, 7), ('outer', o1, o2, 4), ('inner', o2, op, 1)):
            if x0 < x1:
                row |= {(tex, x0, x1, front), (tex, ARCH_W - x1, ARCH_W - x0, front)}
        for key in list(open_rows):
            if key not in row:
                tex, x0, x1, front = key
                boxes.append((x0, open_rows.pop(key), -16, x1, ay, front, tex, 0))
        for key in sorted(row):  # sorted, so the output never depends on set order
            open_rows.setdefault(key, ay)
    for (tex, x0, x1, front), ay0 in open_rows.items():
        boxes.append((x0, ay0, -16, x1, ARCH_ROWS * 16, front, tex, 0))
    carving = [
        # Colonnettes where the orders step, with carved bases and capitals.
        (26.5, 5, 0, 29.5, 44, 4.5, 'outer'), (25.5, 0, 0, 30.5, 5, 5.5, 'carved'), (25, 44, 0, 31, 48, 5.5, 'carved'),
        # Label stops where the hood moulding meets the springing.
        (19, 42, 0, 24, 48, 8, 'carved'),
        # Pinnacle shafts either side, rising past the arch to stepped points.
        (9, 0, 0, 17, 6, 6, 'carved'), (10, 6, 0, 16, 92, 5, 'outer'), (9.5, 92, 0, 16.5, 96, 6, 'carved'),
        (10.5, 96, 0, 15.5, 104, 4.5, 'outer'), (11.5, 104, 0, 14.5, 112, 3.5, 'outer'), (12.5, 112, 0, 13.5, 120, 3, 'outer'),
    ]
    # A steep gable over the arch, springing beside the pinnacles, with crockets up its edge.
    slope = (140 - 76) / (ARCH_MID - 17)
    for x in range(17, ARCH_MID, 2):
        top = 76 + (x + 1 - 17) * slope
        carving.append((x, top - 5, 0, x + 2, top, 3, 'outer'))
        if (x - 17) % 8 == 5:
            carving.append((x, top, 0, x + 2, top + 2, 4, 'carved'))
    for ax0, ay0, ad0, ax1, ay1, ad1, tex in carving:
        boxes += [(ax0, ay0, ad0, ax1, ay1, ad1, tex, 0), (ARCH_W - ax1, ay0, ad0, ARCH_W - ax0, ay1, ad1, tex, 0)]
    m = ARCH_MID
    # Finial, the keystone hanging in the apex, and in the gable the Necromancer's hooded
    # skull in a dark roundel. The eyes glow.
    boxes += [(m - 3, 138, 0, m + 3, 142, 4, 'carved', 0), (m - 1.5, 142, 0, m + 1.5, 144, 3.5, 'carved', 0),
              (m - 3, 86, -16, m + 3, 96, 2, 'carved', 0)]
    for dy in range(-9, 9, 2):
        w = math.sqrt(max(0, 81 - (dy + 1) ** 2))
        boxes.append((m - round(w), 116 + dy, -2, m + round(w), 116 + dy + 2, 0.5, 'hood', 0))
    boxes += [
        (m - 7.5, 106, 0, m - 5, 122, 5, 'hood', 0), (m + 5, 106, 0, m + 7.5, 122, 5, 'hood', 0),
        (m - 6, 121, 0, m + 6, 124, 5, 'hood', 0), (m - 4, 124, 0, m + 4, 126, 5, 'hood', 0), (m - 2, 126, 0, m + 2, 128, 5, 'hood', 0),
        (m - 5, 111, 0, m + 5, 121, 6.5, 'bone', 0), (m - 4, 106, 0, m + 4, 111, 5.5, 'bone', 0),
        (m - 4, 114, 6.5, m - 1, 117, 7.1, 'glow', 12), (m + 1, 114, 6.5, m + 4, 117, 7.1, 'glow', 12),
        (m - .7, 111.5, 6.5, m + .7, 113.5, 7.1, 'hood', 0), (m - 3, 108, 5.5, m + 3, 108.8, 6.1, 'hood', 0),
    ]
    return boxes


FACE_UV = {  # (axis, flipped) for u, then v, following Minecraft's automatic face UVs
    'north': ((0, True), (1, True)), 'south': ((0, False), (1, True)),
    'west': ((2, False), (1, True)), 'east': ((2, True), (1, True)),
    'up': ((0, False), (2, False)), 'down': ((0, False), (2, True)),
}
FACE_OF = {'west': (0, 0), 'east': (0, 1), 'down': (1, 0), 'up': (1, 1), 'north': (2, 0), 'south': (2, 1)}


def model_element(lo, hi, texture, light=0):
    """A block-model element whose UVs follow its position, so carvings sliced across cells line
    up with each other and with neighbouring full blocks of the same texture."""
    faces = {}
    for face, spec in FACE_UV.items():
        uv = []
        for axis, flipped in spec:
            a, b = (16 - hi[axis], 16 - lo[axis]) if flipped else (lo[axis], hi[axis])
            span = min(16, b - a)
            a -= math.floor(a / 16) * 16
            a = min(a, 16 - span)
            uv.append((a, a + span))
        entry = {'uv': [round(uv[0][0], 4), round(uv[1][0], 4), round(uv[0][1], 4), round(uv[1][1], 4)], 'texture': '#' + texture}
        axis, side = FACE_OF[face]
        if (hi if side else lo)[axis] == (16 if side else 0):
            entry['cullface'] = face
        faces[face] = entry
    element = {'from': [round(v, 4) for v in lo], 'to': [round(v, 4) for v in hi], 'faces': faces}
    if light:
        element['light_emission'] = light
    return element


_arch_cells = []


def arch_cells():
    """The frame sliced into north-facing block models keyed by (column, row), plus the cells
    that hold only flush wall. Cells with nothing in them are the open doorway."""
    if not _arch_cells:
        _arch_cells.append(slice_arch())
    return _arch_cells[0]


def arch_pieces():
    """The carved cells in block-state order: bottom row first, left to right from the yard."""
    return sorted(arch_cells()[0], key=lambda k: (k[1], k[0]))


def slice_arch():
    cells, flush, carved = {}, {}, set()
    for ax0, ay0, ad0, ax1, ay1, ad1, tex, light in arch_boxes():
        for c in range(int(ax0 // 16), math.ceil(ax1 / 16)):
            for r in range(int(ay0 // 16), math.ceil(ay1 / 16)):
                x0, x1 = max(ax0, c * 16) - c * 16, min(ax1, c * 16 + 16) - c * 16
                y0, y1 = max(ay0, r * 16) - r * 16, min(ay1, r * 16 + 16) - r * 16
                if x0 >= x1 or y0 >= y1:
                    continue
                # The model faces the yard as a north-facing block, so arch-left is model +x.
                cells.setdefault((c, r), []).append(model_element((16 - x1, y0, -ad1), (16 - x0, y1, -ad0), tex, light))
                if tex == 'wall' and ad1 == 0:
                    flush[c, r] = flush.get((c, r), 0) + (x1 - x0) * (y1 - y0)
                else:
                    carved.add((c, r))
    plain = {k for k in cells if k not in carved and flush.get(k) == 256}
    return {k: v for k, v in cells.items() if k not in plain}, plain


def arch_opening(c, r):
    """Whether any of the doorway opening falls in arch cell (c, r)."""
    for ay in range(r * 16, r * 16 + 16, 2):
        edge = arch_edge(ay + 1, BANDS[0])
        if edge < ARCH_MID and edge < (c + 1) * 16 and ARCH_COLS * 16 - edge > c * 16:
            return True
    return False


def veil_frames(count=32):
    """The doorway veil: one image across the whole opening, a dark vortex whose three arms turn
    slowly inward around a black eye, with a faint glow along the arch. Loops seamlessly."""
    palette = [(5, 8, 12), (9, 17, 23), (14, 34, 42), (24, 66, 76), (54, 132, 142), (128, 214, 220), (206, 246, 246)]
    w, h = VEIL_COLS * 16, VEIL_ROWS * 16
    cx, cy = w / 2, h - VEIL_EYE
    frames = []
    for f in range(count):
        t = f / count * math.tau
        img = Image.new('RGBA', (w, h))
        px = img.load()
        for i in range(w):
            for j in range(h):
                dx, dy = i + .5 - cx, (j + .5 - cy) * .85
                r = math.hypot(dx, dy) + .01
                arm = (math.sin(3 * math.atan2(dy, dx) + 4.2 * math.log(r) + 3 * t) * .5 + .5) ** 3
                ring = math.exp(-((r - 10) / 13) ** 2) * min(1, (r / 4.5) ** 2)
                rim = ARCH_MID - arch_edge(h - j - .5, BANDS[0]) - abs(i + .5 - cx)
                k = arm * ring * 1.15 + (math.exp(-max(0, rim) / 2.2) * .5 if rim > -1 else 0) + .1 * math.exp(-r / 30)
                px[i, j] = palette[min(len(palette) - 1, int(k * 6.4))] + (255,)
        frames.append(img)
    return frames


def veil_element(piece):
    """The veil for one doorway cell: a glowing panel set just behind the frame, facing out,
    showing that cell's part of the veil image."""
    c, r = piece % VEIL_COLS, piece // VEIL_COLS
    u, v = 16 / VEIL_COLS, 16 / VEIL_ROWS
    element = model_element((0, 0, 1), (16, 16, 2), 'veil', 15)
    element['faces'] = {'north': {'uv': [round(c * u, 4), round((VEIL_ROWS - 1 - r) * v, 4), round((c + 1) * u, 4),
                                         round((VEIL_ROWS - r) * v, 4)], 'texture': '#veil'}}
    return element


def void_frames(count=32):
    """The crypt's void as seen through the windows: near black, with faint teal wisps drifting
    up. Integer frequencies keep it seamless and the loop closed."""
    palette = [(5, 8, 12), (8, 14, 19), (12, 26, 32), (20, 50, 58), (40, 100, 110)]
    frames = []
    for f in range(count):
        t = f / count * math.tau
        img = Image.new('RGBA', (16, 16))
        px = img.load()
        for i in range(16):
            for j in range(16):
                u, v = i / 16 * math.tau, j / 16 * math.tau
                w = math.sin(u + 1.2 * math.sin(v + t))
                s = math.sin(2 * v + 2 * t + 1.4 * w) * (w * .5 + .5)
                px[i, j] = palette[min(len(palette) - 1, int(max(0, s) ** 2.4 * 4.6))] + (255,)
        frames.append(img)
    return frames


def window_element():
    """A window: the void set three pixels back, so the surrounding stone shows as a reveal."""
    element = model_element((0, 0, 3), (16, 16, 16), 'void', 15)
    element['faces'] = {'north': element['faces']['north']}
    return element


def glow_texture():
    img = Image.new('RGBA', (16, 16))
    px = img.load()
    for i in range(16):
        for j in range(16):
            d = max(abs(i - 7.5), abs(j - 7.5))
            px[i, j] = (150, 240, 250, 255) if d < 4 else (96, 214, 232, 255) if d < 6.5 else (58, 168, 190, 255)
    return img


# ---------------------------------------------------------------- overworld graveyard

MAUSOLEUM_FRONT = -12   # facade plane (z); the doorway faces the lychgate to the south


def build_mausoleum(lay, rng, F):
    """A gothic mausoleum, solid throughout: its pointed doorway holds the crypt's veil, which
    takes whoever walks into it (and everyone near them) to the Necromancer."""
    B, X, TOP = F - 9, 5, 8   # back wall, body half-width, last wall course below the cornice
    brick = lambda: 'cracked_deepslate_bricks' if rng.random() < .07 else 'deepslate_bricks'
    # Foundation, plinth and a porch before the door, with steps down to the yard.
    for x in range(-X - 1, X + 2):
        for z in range(B - 1, F + 2):
            for y in (-2, -1, 0):
                lay.put(x, y, z, 'cobbled_deepslate')
            lay.put(x, 1, z, 'polished_deepslate' if abs(x) == X + 1 or z in (B - 1, F + 1) else 'deepslate_tiles')
    for x in range(-3, 4):
        lay.put(x, 1, F + 2, 'deepslate_brick_stairs[facing=north,half=bottom]')
        lay.put(x, 1, F + 3, 'deepslate_brick_slab[type=bottom]')
    # The body is solid: nothing inside is ever reached.
    for x in range(-X, X + 1):
        for z in range(B, F + 1):
            for y in range(2, TOP + 1):
                edge = abs(x) == X or z in (B, F)
                lay.put(x, y, z, 'polished_deepslate' if edge and y == 2 else 'deepslate_tiles' if edge and y == 5 else brick())
            lay.put(x, TOP + 1, z, 'polished_deepslate')
    # Twin lancet windows between buttresses. Like the doorway, they look into the crypt's void.
    for side in (-1, 1):
        for z0 in (F - 1, F - 4, F - 7):
            for z in (z0, z0 - 1):
                lay.put(side * X, 3, z, 'chiseled_deepslate')
                for y in (4, 5, 6):
                    lay.put(side * X, y, z, f"elementalwands:mausoleum_window[facing={'east' if side > 0 else 'west'}]")
                lay.put(side * X, 7, z, 'deepslate_tiles')
        # Stepped buttresses.
        for z in (F - 3, F - 6):
            face = 'west' if side > 0 else 'east'
            for y in range(2, 7):
                lay.put(side * (X + 1), y, z, 'deepslate_bricks')
            lay.put(side * (X + 1), 7, z, f'deepslate_brick_stairs[facing={face},half=bottom]')
            for y in (2, 3):
                lay.put(side * (X + 2), y, z, 'deepslate_bricks')
            lay.put(side * (X + 2), 4, z, f'deepslate_brick_stairs[facing={face},half=bottom]')
        # Corner piers with pinnacles; soul lanterns crown the two by the door.
        for z in (F, B):
            for y in range(2, TOP + 3):
                lay.put(side * (X + 1), y, z, 'polished_deepslate')
            for y in (TOP + 3, TOP + 4):
                lay.put(side * (X + 1), y, z, 'polished_deepslate_wall')
            if z == F:
                lay.put(side * (X + 1), TOP + 5, z, 'soul_lantern')
        # Braziers on the porch.
        lay.put(side * X, 2, F + 1, 'polished_blackstone_bricks')
        lay.put(side * X, 3, F + 1, 'chiseled_polished_blackstone')
        lay.put(side * X, 4, F + 1, 'soul_campfire[lit=true]')
    # A steep roof of deepslate tiles, overhanging the sides; the gable ends are brick.
    top = lambda dx: TOP + 1 + round((X + 1.5 - dx) * 1.55)
    for x in range(-X - 1, X + 2):
        dx = abs(x)
        for z in range(B, F + 1):
            if dx == X + 1 and z in (B, F):
                continue
            gable = z in (B, F)
            for y in range(TOP + 2, top(dx) + 1):
                if y < top(dx):
                    lay.put(x, y, z, brick() if gable else 'deepslate_tiles')
                elif dx:
                    lay.put(x, y, z, f"deepslate_tile_stairs[facing={'east' if x < 0 else 'west'},half=bottom]")
                else:
                    lay.put(x, y, z, 'deepslate_tiles')
        if dx == X + 1:
            for z in range(B + 1, F):
                if rng.random() < .3:
                    lay.put(x, TOP + 1, z, 'pale_hanging_moss[tip=true]')
    # Rose window in the front gable, and a slender spire over the door with a skull finial.
    for x, y in ((0, 13), (0, 14), (0, 15), (-1, 14), (1, 14)):
        lay.put(x, y, F, 'elementalwands:mausoleum_window[facing=south]')
    for x, y in ((-1, 13), (1, 13), (-1, 15), (1, 15), (0, 12), (0, 16), (-2, 14), (2, 14)):
        lay.put(x, y, F, 'chiseled_deepslate')
    ridge = top(0)
    lay.put(0, ridge + 1, F, 'polished_deepslate')
    for y in range(ridge + 2, ridge + 5):
        lay.put(0, y, F, 'polished_deepslate_wall')
    lay.put(0, ridge + 5, F, 'skeleton_skull[rotation=8]')
    # The carved doorway: model cells, flush wall, and the open doorway with the veil behind it.
    cells, plain = arch_cells()
    for c in range(ARCH_COLS):
        for r in range(ARCH_ROWS):
            x, y = c - ARCH_COLS // 2, 2 + r
            if (c, r) in cells:
                lay.put(x, y, F, f'elementalwands:mausoleum_arch[facing=south,piece={arch_pieces().index((c, r))}]')
            elif (c, r) not in plain:
                lay.clear(x, y, F)
            if arch_opening(c, r):
                piece = r * VEIL_COLS + c - (ARCH_COLS - VEIL_COLS) // 2
                lay.put(x, y, F - 1, f'elementalwands:mausoleum_veil[facing=south,piece={piece}]')


def build_graveyard(seed=0x6EA7):
    rng = random.Random(seed)
    lay = Layout()
    noise = value_noise(seed)
    W, N, S = 13, -24, 13   # half-width, north and south edges (z)
    F = MAUSOLEUM_FRONT
    # The floor is a single layer laid over the natural ground; worldgen levels the land to it.
    # Only the mausoleum's foundation reaches lower, and it sits under the building.
    for x in range(-W - 3, W + 4):
        for z in range(N - 3, S + 5):
            n = noise(x, z, 5)
            inside = abs(x) <= W and N <= z <= S
            lay.put(x, 0, z, ('coarse_dirt' if n < .35 else 'podzol' if n < .55 else 'grass_block') if inside else 'grass_block')
    # Path from the lychgate to the mausoleum steps, ending in a flagged court.
    for z in range(F + 4, S + 4):
        for x in (-1, 0, 1):
            lay.put(x, 0, z, rng.choice(['gravel', 'coarse_dirt', 'cobblestone', 'gravel']))
    for x in range(-4, 5):
        for z in range(F + 4, F + 7):
            lay.put(x, 0, z, 'polished_deepslate' if abs(x) == 4 or z == F + 6 else
                    'deepslate_tiles' if (x + z) % 4 else 'cracked_deepslate_tiles')
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
    # Graves: rows either side of the path, and a few old markers beside the mausoleum.
    def grave(x, z):
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
    for z in range(F + 8, S - 3, 4):
        for side in (-1, 1):
            for x0 in (4, 8):
                if rng.random() >= .15:
                    grave(side * x0 + rng.choice((-1, 0, 0, 1)), z)
    for z in (F - 1, F - 5):
        grave(10, z)
    # A dead tree beside the mausoleum, hung with pale moss; its limbs keep off the roof.
    trees = TreeBuilder(lay, rng)
    trees.tree(-10, F - 6, 16, 'dark', None, False,
               lambda p: abs(p[0]) <= 8 and F - 11 <= p[2] <= F + 4 or abs(p[0]) >= W or not N < p[2] < S)
    build_mausoleum(lay, rng, F)
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


SHAPE_OF.update({'polished_deepslate_wall': 'post', 'deepslate_brick_slab': 'slab'})
TEX_ALIAS.update({'polished_deepslate_wall': ('polished_deepslate',) * 3, 'deepslate_brick_slab': ('deepslate_bricks',) * 3,
                  'chiseled_polished_blackstone': ('chiseled_polished_blackstone',) * 3})
STAIR_TEX = {'deepslate_brick_stairs': 'deepslate_bricks', 'deepslate_tile_stairs': 'deepslate_tiles'}
VEIL_FRAMES = 32


def material_key(state):
    name = state.split(':', 1)[1]
    base = name.split('[')[0]
    if base == 'pale_hanging_moss' and 'tip=true' in name:
        return 'pale_hanging_moss_tip'
    return base


def is_part(state):
    """Blocks the preview draws as model geometry rather than instanced voxels."""
    return state.startswith('elementalwands:') or state.split('[')[0].endswith('_stairs')


def veil_tile(frame, piece):
    return f'elementalwands:mausoleum_veil#{frame}/{piece}'


_custom = {}


def custom_textures():
    """Generated textures, and the veil's frames cut into one 16-pixel tile per doorway cell."""
    if not _custom:
        _custom['elementalwands:mausoleum_glow'] = glow_texture()
        for f, frame in enumerate(void_frames(VEIL_FRAMES)):
            _custom[f'elementalwands:mausoleum_void#{f}'] = frame
        for f, frame in enumerate(veil_frames(VEIL_FRAMES)):
            for piece in range(VEIL_COLS * VEIL_ROWS):
                c, r = piece % VEIL_COLS, piece // VEIL_COLS
                top = (VEIL_ROWS - 1 - r) * 16
                _custom[veil_tile(f, piece)] = frame.crop((c * 16, top, c * 16 + 16, top + 16))
    return _custom


def texture(archive, name):
    if name.startswith('elementalwands:'):
        return custom_textures()[name]
    try:
        img = Image.open(io.BytesIO(archive.read(f'assets/minecraft/textures/block/{name}.png'))).convert('RGBA')
    except KeyError:
        img = Image.new('RGBA', (16, 16), (255, 0, 255, 255))
    return img.crop((0, 0, 16, 16))


def part_model(state):
    """(elements, textures, y rotation) for a block the preview draws as geometry, matching the
    block models the mod ships."""
    name, _, props = state.partition('[')
    props = dict(p.split('=') for p in props.rstrip(']').split(',')) if props else {}
    base = name.split(':')[1]
    if base.endswith('_stairs'):
        lo, hi = (8, 16) if props.get('half') == 'top' else (0, 8)
        slo, shi = (0, 8) if props.get('half') == 'top' else (8, 16)
        (x0, z0), (x1, z1) = {'north': ((0, 0), (16, 8)), 'south': ((0, 8), (16, 16)),
                              'east': ((8, 0), (16, 16)), 'west': ((0, 0), (8, 16))}[props['facing']]
        return ([model_element((0, lo, 0), (16, hi, 16), 'all'), model_element((x0, slo, z0), (x1, shi, z1), 'all')],
                {'all': STAIR_TEX[base]}, 0)
    turn = {'north': 0, 'east': 90, 'south': 180, 'west': 270}[props.get('facing', 'north')]
    if base == 'mausoleum_arch':
        piece = int(props['piece'])
        cells, _ = arch_cells()
        textures = {k: v.replace('minecraft:block/', '').replace('block/', '') for k, v in ARCH_TEX.items()}
        return cells[arch_pieces()[piece]], textures, turn
    if base == 'mausoleum_veil':
        piece = int(props['piece'])
        return [veil_element(piece)], {'veil': veil_tile(0, piece)}, turn
    if base == 'mausoleum_window':
        return [window_element()], {'void': 'elementalwands:mausoleum_void#0'}, turn
    raise ValueError(state)


CORNERS = {  # top-left, top-right, bottom-right, bottom-left, seen from outside the face
    'north': lambda l, h: [(h[0], h[1], l[2]), (l[0], h[1], l[2]), (l[0], l[1], l[2]), (h[0], l[1], l[2])],
    'south': lambda l, h: [(l[0], h[1], h[2]), (h[0], h[1], h[2]), (h[0], l[1], h[2]), (l[0], l[1], h[2])],
    'west': lambda l, h: [(l[0], h[1], l[2]), (l[0], h[1], h[2]), (l[0], l[1], h[2]), (l[0], l[1], l[2])],
    'east': lambda l, h: [(h[0], h[1], h[2]), (h[0], h[1], l[2]), (h[0], l[1], l[2]), (h[0], l[1], h[2])],
    'up': lambda l, h: [(l[0], h[1], l[2]), (h[0], h[1], l[2]), (h[0], h[1], h[2]), (l[0], h[1], h[2])],
    'down': lambda l, h: [(l[0], l[1], h[2]), (h[0], l[1], h[2]), (h[0], l[1], l[2]), (l[0], l[1], l[2])],
}
NORMALS = {'north': (0, -1), 'south': (0, 1), 'west': (-1, 0), 'east': (1, 0)}


def part_triangles(state, pos, tile):
    """Flat vertex rows (x, y, z, u, v, tile, shade, flag) for one modelled block. Flag 1 is
    emissive, 2 the animated veil, 3 the animated void in the windows."""
    elements, textures, turn = part_model(state)
    rows = []
    for e in elements:
        for face, spec in e['faces'].items():
            corners = CORNERS[face](e['from'], e['to'])
            for _ in range(turn // 90):
                corners = [(16 - z, y, x) for x, y, z in corners]
            if face in NORMALS:
                nx, nz = NORMALS[face]
                for _ in range(turn // 90):
                    nx, nz = -nz, nx
                shade = .6 if nx else .8
            else:
                shade = 1.0 if face == 'up' else .5
            name = textures[spec['texture'][1:]]
            flag = (2 if name.startswith('elementalwands:mausoleum_veil') else 3 if name.startswith('elementalwands:mausoleum_void')
                    else 1 if e.get('light_emission') else 0)
            # The preview cuts the veil into one tile per cell, so its face spans that tile.
            u0, v0, u1, v1 = (0, 0, 1, 1) if flag == 2 else (c / 16 for c in spec['uv'])
            uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
            for i in (0, 1, 2, 0, 2, 3):
                x, y, z = corners[i]
                rows.append((pos[0] + x / 16, pos[1] + y / 16, pos[2] + z / 16, *uvs[i], tile(name), shade, flag))
    return rows


def export(layouts):
    OUT.mkdir(parents=True, exist_ok=True)
    keys = sorted({material_key(s) for lay in layouts.values() for s in lay.b.values() if not is_part(s)})
    tiles, tile_index, materials = [], {}, []

    def tile(archive, name):
        if name.startswith('elementalwands:mausoleum_veil#') and name not in tile_index:
            for f in range(VEIL_FRAMES):  # frame-major and contiguous, so the viewer animates by offset
                for piece in range(VEIL_COLS * VEIL_ROWS):
                    tile_index[veil_tile(f, piece)] = len(tiles)
                    tiles.append(texture(archive, veil_tile(f, piece)))
        if name.startswith('elementalwands:mausoleum_void#') and name not in tile_index:
            for f in range(VEIL_FRAMES):
                tile_index[f'elementalwands:mausoleum_void#{f}'] = len(tiles)
                tiles.append(texture(archive, f'elementalwands:mausoleum_void#{f}'))
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
        parts = {}
        for name, lay in layouts.items():
            rows, veil = [], []
            for pos, state in lay.b.items():
                if is_part(state):
                    rows += part_triangles(state, pos, lambda n: tile(archive, n))
                    if 'mausoleum_veil' in state:
                        veil.append(pos)
            parts[name] = rows, veil
    cols = 16
    atlas = Image.new('RGBA', (cols * 16, math.ceil(len(tiles) / cols) * 16))
    for i, img in enumerate(tiles):
        atlas.paste(img, (i % cols * 16, i // cols * 16))
    atlas.save(OUT / 'atlas.png')
    index = {k: i for i, k in enumerate(keys)}
    opaque = {i for i, m in enumerate(materials) if m['shape'] == 0}
    data = {'materials': materials, 'atlasCols': cols, 'atlasRows': math.ceil(len(tiles) / cols), 'layouts': {}}
    for name, lay in layouts.items():
        mat = {p: index[material_key(s)] for p, s in lay.b.items() if not is_part(s)}
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
        prows, veil = parts[name]
        data['layouts'][name] = {'count': len(rows), 'blocks': len(lay.b), 'instances': base64.b64encode(raw).decode(),
                                 'lights': lights, 'partVertices': len(prows),
                                 'parts': base64.b64encode(struct.pack(f'<{len(prows) * 8}f', *(v for r in prows for v in r))).decode(),
                                 # The veil lights the doorway a little; motes drift to its centre.
                                 'partLights': [[x + .5, y + .5, z + 1.2, .3, .75, .85, 6] for x, y, z in veil if y <= 4],
                                 'door': [sum(p[0] for p in veil) / len(veil) + .5, sum(p[1] for p in veil) / len(veil) + .3,
                                          max(p[2] for p in veil) + .9] if veil else None}
    data['veil'] = {'frames': VEIL_FRAMES, 'stride': VEIL_COLS * VEIL_ROWS}
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


PERCHES = ROOT / 'src/main/java/com/anton/elementalwands/crypt/HollowCryptPerches.java'


def perches(lay):
    """Bough tops above the barrier lid, over the clearing, where the Necromancer stands during
    a siege: seen from below, never reached. Low, inward spots win; at least 16 blocks apart."""
    spots = []
    for (x, y, z), state in lay.b.items():
        if 'wood' not in state or not PLAY_CEILING + 3 <= y <= PLAY_CEILING + 14:
            continue
        r = math.hypot(x + .5, z + .5)
        if not 24 <= r <= 38:
            continue
        if any(lay.get(x + dx, y + dy, z + dz) is not None for dx in (-1, 0, 1) for dz in (-1, 0, 1) for dy in (1, 2, 3)):
            continue  # Room to stand: nothing in the 3x3 column above the top face.
        spots.append((y + r * .5, y, x, z))
    chosen = []
    for _, y, x, z in sorted(spots):
        if all(math.hypot(x - cx, z - cz) >= 16 for cx, _, cz in chosen):
            chosen.append((x, y + 1, z))
    assert len(chosen) >= 4, f'Only {len(chosen)} siege perches'
    return sorted(chosen, key=lambda p: math.atan2(p[2], p[0]))


def perches_java(spots):
    rows = ''.join(f'        {{{x}, {y}, {z}}},\n' for x, y, z in spots)
    return ('package com.anton.elementalwands.crypt;\n\n'
            '/**\n'
            ' * Generated by art/hollow_crypt/build_layout.py: bough tops above the barrier lid where the\n'
            ' * Necromancer stands during a siege. Block offsets from the clearing centre; y is the feet\n'
            ' * height above {@link HollowCryptRealm#SURFACE_Y}.\n'
            ' */\n'
            'public final class HollowCryptPerches {\n'
            '    private HollowCryptPerches() {}\n\n'
            '    public static final int[][] SPOTS = {\n'
            f'{rows}'
            '    };\n'
            '}\n').encode()


GRAVEYARD = ROOT / 'src/main/resources/data/elementalwands/structure/hollow_graveyard.nbt'
# Template layer holding the graveyard floor (layout y=0). Shared with "floor_layer" in
# data/elementalwands/worldgen/structure/hollow_graveyard.json, which seats it on the ground.
GRAVEYARD_FLOOR_LAYER = 2


def graveyard_nbt(lay):
    """Worldgen template. Air above the floor clears grass and bumps inside the footprint. Cells
    below the floor are absent apart from the mausoleum's foundation, so the natural ground
    (levelled by the structure's terrain adaptation) stays in place under the floor. The
    mausoleum's stone is swapped for its unbreakable look-alikes."""
    xs, ys, zs = zip(*lay.b)
    lo = (min(xs), min(ys), min(zs))
    assert lo[1] == -GRAVEYARD_FLOOR_LAYER, f'Graveyard floor moved to template layer {-lo[1]}'
    size = (max(xs) - lo[0] + 1, max(ys) - lo[1] + 1, max(zs) - lo[2] + 1)
    blocks = {}
    for x in range(size[0]):
        for z in range(size[2]):
            for y in range(size[1]):
                pos = (x + lo[0], y + lo[1], z + lo[2])
                state = lay.b.get(pos)
                if state is None and (pos[1] >= 1 or pos in lay.carved):
                    state = 'minecraft:air'
                if state is not None:
                    blocks[x, y, z] = sealed(pos, state)
    return structure_nbt(blocks, size)


# ---------------------------------------------------------------- mausoleum resources

ASSETS = ROOT / 'src/main/resources/assets/elementalwands'
MAUSOLEUM_MODELS = ASSETS / 'models/block/mausoleum'
MAUSOLEUM_JAVA = ROOT / 'src/main/java/com/anton/elementalwands/crypt/MausoleumModel.java'
# Unbreakable look-alikes of the vanilla stone the mausoleum is built from. The same list, in the
# same order, is registered by ModBlocks.SEALED.
SEALED = ['cobbled_deepslate', 'polished_deepslate', 'deepslate_tiles', 'deepslate_bricks', 'cracked_deepslate_bricks',
          'chiseled_deepslate', 'polished_blackstone_bricks', 'chiseled_polished_blackstone', 'deepslate_brick_stairs',
          'deepslate_tile_stairs', 'deepslate_brick_slab', 'polished_deepslate_wall']
MAUSOLEUM_BLOCKS = ['mausoleum_arch', 'mausoleum_veil', 'mausoleum_window']


def in_mausoleum(pos):
    x, y, z = pos
    return abs(x) <= 7 and MAUSOLEUM_FRONT - 10 <= z <= MAUSOLEUM_FRONT + 3 and y >= -GRAVEYARD_FLOOR_LAYER


def sealed(pos, state):
    name, _, props = state.partition('[')
    base = name.split(':')[1]
    if name.startswith('minecraft:') and base in SEALED and in_mausoleum(pos):
        return f'elementalwands:sealed_{base}' + (f'[{props}' if props else '')
    return state


def write_png(img):
    out = io.BytesIO()
    img.save(out, 'PNG', optimize=True)
    return out.getvalue()


def strip(frames):
    """Animation frames stacked top to bottom, as Minecraft reads an animated texture."""
    w, h = frames[0].size
    img = Image.new('RGBA', (w, h * len(frames)))
    for i, frame in enumerate(frames):
        img.paste(frame, (0, i * h))
    return write_png(img)


def as_json(data):
    return (json.dumps(data, indent=2) + '\n').encode()


def mausoleum_files():
    """Block models, block states, textures, tags and the generated shape table for the
    mausoleum's doorway, veil, windows and sealed stone."""
    files = {}
    turns = {'north': 0, 'east': 90, 'south': 180, 'west': 270}
    cells, _ = arch_cells()
    pieces = arch_pieces()
    arch_tex = dict(ARCH_TEX, particle=ARCH_TEX['outer'])
    for i, cell in enumerate(pieces):
        files[MAUSOLEUM_MODELS / f'arch_{i}.json'] = as_json({'textures': arch_tex, 'elements': cells[cell]})
    for piece in range(VEIL_COLS * VEIL_ROWS):
        files[MAUSOLEUM_MODELS / f'veil_{piece}.json'] = as_json({
            'ambientocclusion': False,
            'textures': {'veil': 'elementalwands:block/mausoleum_veil', 'particle': 'elementalwands:block/mausoleum_veil'},
            'elements': [veil_element(piece)]})
    files[MAUSOLEUM_MODELS / 'window.json'] = as_json({
        'ambientocclusion': False,
        'textures': {'void': 'elementalwands:block/mausoleum_void', 'particle': 'minecraft:block/deepslate_bricks'},
        'elements': [window_element()]})

    def variants(name, count):
        out = {}
        for facing, y in turns.items():
            for piece in range(count) if count else [None]:
                suffix = '' if piece is None else f'_{piece}'
                key = f'facing={facing}' + ('' if piece is None else f',piece={piece}')
                out[key] = {'model': f'elementalwands:block/mausoleum/{name}{suffix}', **({'y': y} if y else {})}
        return {'variants': out}
    files[ASSETS / 'blockstates/mausoleum_arch.json'] = as_json(variants('arch', len(pieces)))
    files[ASSETS / 'blockstates/mausoleum_veil.json'] = as_json(variants('veil', VEIL_COLS * VEIL_ROWS))
    files[ASSETS / 'blockstates/mausoleum_window.json'] = as_json(variants('window', 0))
    with zipfile.ZipFile(JAR) as archive:
        for base in SEALED:
            files[ASSETS / f'blockstates/sealed_{base}.json'] = archive.read(f'assets/minecraft/blockstates/{base}.json')
    textures = ASSETS / 'textures/block'
    files[textures / 'mausoleum_veil.png'] = strip(veil_frames(VEIL_FRAMES))
    files[textures / 'mausoleum_veil.png.mcmeta'] = as_json({'animation': {'frametime': 2, 'width': VEIL_COLS * 16, 'height': VEIL_ROWS * 16}})
    files[textures / 'mausoleum_void.png'] = strip(void_frames(VEIL_FRAMES))
    files[textures / 'mausoleum_void.png.mcmeta'] = as_json({'animation': {'frametime': 3}})
    files[textures / 'mausoleum_glow.png'] = write_png(glow_texture())
    # Withers and the dragon break anything not tagged immune, whatever its hardness.
    immune = {'replace': False, 'values': [f'elementalwands:{n}' for n in MAUSOLEUM_BLOCKS + [f'sealed_{b}' for b in SEALED]]}
    for tag in ('wither_immune', 'dragon_immune'):
        files[ROOT / f'src/main/resources/data/minecraft/tags/block/{tag}.json'] = as_json(immune)
    files[MAUSOLEUM_JAVA] = mausoleum_java(cells, pieces)
    return files


def mausoleum_java(cells, pieces):
    num = lambda v: f'{round(v, 3):g}'
    rows = []
    for cell in pieces:
        boxes = ', '.join('{' + ', '.join(num(v) for v in e['from'] + e['to']) + '}' for e in cells[cell])
        rows.append(f'        {{{boxes}}},\n')
    cell_rows = ', '.join(f'{{{c}, {r}}}' for c, r in pieces)
    return ('package com.anton.elementalwands.crypt;\n\n'
            '/**\n'
            ' * Generated by art/hollow_crypt/build_layout.py: the mausoleum doorway\'s carved frame, sliced\n'
            ' * into block models, and the veil behind it. Boxes are model pixels of a north-facing piece\n'
            ' * (x0, y0, z0, x1, y1, z1); the frame faces the yard, so arch-left is +x.\n'
            ' */\n'
            'public final class MausoleumModel {\n'
            '    private MausoleumModel() {}\n\n'
            f'    public static final int ARCH_COLS = {ARCH_COLS}, ARCH_ROWS = {ARCH_ROWS}, VEIL_COLS = {VEIL_COLS}, VEIL_ROWS = {VEIL_ROWS};\n'
            '    /** Height of the veil vortex\'s eye above the threshold, in blocks: where the motes drift to. */\n'
            f'    public static final double VEIL_EYE = {num(VEIL_EYE / 16)};\n'
            '    /** Column (left to right from the yard) and row (bottom up) of each arch piece. */\n'
            f'    public static final int[][] ARCH_CELLS = {{{cell_rows}}};\n'
            '    /** Each arch piece\'s outline and collision boxes. */\n'
            '    public static final double[][][] ARCH_BOXES = {\n'
            f'{"".join(rows)}'
            '    };\n'
            '}\n').encode()


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--serve', action='store_true')
    ap.add_argument('--install', action='store_true', help='write the realm tiles, siege perches and graveyard structure into the mod')
    ap.add_argument('--check', action='store_true', help='verify the installed tiles match this builder')
    args = ap.parse_args()
    realm = build_realm()
    if args.install or args.check:
        files = realm_tiles(realm)
        stale = set(STRUCTURES.glob('realm_*.nbt')) - set(files)
        tiles = len(files)
        files[GRAVEYARD] = graveyard_nbt(build_graveyard())
        spots = perches(realm)
        files[PERCHES] = perches_java(spots)
        mausoleum = mausoleum_files()
        files.update(mausoleum)
        stale |= set(MAUSOLEUM_MODELS.glob('*.json')) - set(files)
        if args.check:
            for path, data in files.items():
                assert path.exists() and path.read_bytes() == data, f'Drift: {path.relative_to(ROOT)}'
            assert not stale, f'Unexpected files: {sorted(p.name for p in stale)}'
            print(f'Hollow Crypt verified: {tiles} realm tiles ({len(realm.b)} blocks), '
                  f'{len(spots)} siege perches, the graveyard and {len(mausoleum)} mausoleum resources')
        else:
            STRUCTURES.mkdir(parents=True, exist_ok=True)
            MAUSOLEUM_MODELS.mkdir(parents=True, exist_ok=True)
            for path in stale:
                path.unlink()
            for path, data in files.items():
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
            print(f'Installed {tiles} realm tiles, {len(spots)} siege perches, the graveyard and {len(mausoleum)} '
                  f'mausoleum resources ({sum(map(len, files.values())) // 1024} KiB)')
        raise SystemExit
    export({'realm': realm, 'graveyard': build_graveyard()})
    print(f'Preview written to {OUT.relative_to(ROOT)}/index.html')
    if args.serve:
        import functools, http.server

        class NoCache(http.server.SimpleHTTPRequestHandler):
            def end_headers(self):
                self.send_header('Cache-Control', 'no-store'); super().end_headers()
        http.server.ThreadingHTTPServer(('127.0.0.1', 8360), functools.partial(NoCache, directory=str(OUT))).serve_forever()
