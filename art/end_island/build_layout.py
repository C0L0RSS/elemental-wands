#!/usr/bin/env python3
"""Authors the End's main island as the First Wizard's arena: a flat round floor of end stone
at the End's origin, a skirt that runs it into the natural island, a broken kerb, and a ring of
broken obsidian pillars standing outside the floor as scenery. Also the way home, a small
basin of vanilla end portal blocks that replaces the floor's centre after the boss is beaten.

    py -3 art/end_island/build_layout.py            # writes a rough preview
    py -3 art/end_island/build_layout.py --install  # writes the structure tiles into the mod
    py -3 art/end_island/build_layout.py --check    # verifies the installed tiles

Coordinates are integer block cells relative to the arena's centre block, the End's origin
(0, 63, 0): a cell's distance from the centre is hypot(x, z). y=0 is the floor block (world
y=63), so players stand at y=1. +Z is south, +X east; azimuth a points at (cos a, sin a), so
90 is south, where players arrive. The tiles hold only built blocks. Clearing what vanilla
put on the island (the spikes, the fountain, the terrain above the floor) is done by the game
code, because a tall volume of air would be hundreds of thousands of structure entries.
The preview lives in the ignored .local-previews/end-island/.
"""
import argparse, math
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.local-previews/end-island'
STRUCTURES = ROOT / 'src/main/resources/data/elementalwands/structure/end_island'

# ---- contract with the game code (end/EndArena.java; do not change) ----
FLOOR_R = 44.5         # fight floor: every cell with hypot(x, z) <= FLOOR_R, flat at y=0
FLOOR_R2 = FLOOR_R * FLOOR_R   # 1980.25, exact
ARRIVAL = (0, 1, 42)   # players arrive here (feet) facing north, on the south rim
BOTTOM = -24           # lowest authored y
TILE = 48
TILE_ORIGIN = -72
TILE_COUNT = 3
LO, HI = TILE_ORIGIN, TILE_ORIGIN + TILE * TILE_COUNT - 1
CLEAR_R = 64           # the game clears everything above the floor within this radius first
WAY_HOME_ORIGIN = (-4, -2, -4)   # the way_home template's corner; it is 9 x 3 x 9
# ----

KERB_R = 46.0          # the flush ring of brick just outside the floor
PILLAR_RING = 52
PILLAR_AZ = [36 * k for k in range(10)]           # none due south, where players arrive
PILLAR_TOPS = [15, 6, 20, 9, 13, 4, 18, 7, 11, 10]   # y of the highest block, per pillar
SPOKES = [22.5 + 45 * k for k in range(8)]

SIDES = ((1, 0), (-1, 0), (0, 1), (0, -1))
ALLOWED = {'end_stone', 'end_stone_bricks', 'obsidian', 'crying_obsidian', 'purpur_block', 'purpur_pillar'}
WAY_HOME_ALLOWED = {'end_portal', 'bedrock', 'purpur_block', 'air'}


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


def az_of(x, z):
    # Rounded so that the last digit of atan2, which can differ between machines, never decides a block.
    return round(math.degrees(math.atan2(z, x)) % 360, 6)


def anoise(a, period, salt=0):
    """Smooth noise in [0, 1] around the circle, one knot every `period` degrees."""
    n = round(360 / period)
    f = (a % 360) / (360 / n)
    i = math.floor(f)
    t = smooth(f - i)
    return noise(i % n, 0, 0, salt) * (1 - t) + noise((i + 1) % n, 0, 0, salt) * t


def adiff(a, b):
    return (a - b + 180) % 360 - 180


def on_floor(x, z):
    return x * x + z * z <= FLOOR_R2


# ---------------------------------------------------------------- the fight floor

def spoke_distance(x, z):
    """Distance from the nearest of the eight spokes, measured across it (outward half only)."""
    best = 99.0
    for a in SPOKES:
        r = math.radians(a)
        along = x * math.cos(r) + z * math.sin(r)
        if along > 0:
            best = min(best, abs(-x * math.sin(r) + z * math.cos(r)))
    return round(best, 6)


def floor_block(x, z):
    d2 = x * x + z * z
    if d2 <= 12:
        return 'obsidian'                      # the centre, where the way home opens after the win
    if d2 <= 25:
        return 'purpur_block'
    if d2 <= 81:
        return 'end_stone_bricks'
    if 1722 < d2 <= 1980.25:                   # the outer band, r 41.5 to the edge
        return 'obsidian' if 1806 < d2 <= 1892 else 'end_stone_bricks'   # an obsidian line round it
    if 420 < d2 <= 506:                        # the middle ring, r 20.5 to 22.5
        return 'purpur_block' if 441 < d2 <= 484 and spoke_distance(x, z) < .8 else 'end_stone_bricks'
    if spoke_distance(x, z) < .6:
        return 'end_stone_bricks'
    # Worn: a few single bricks left in the plain stone.
    return 'end_stone_bricks' if noise(x, 0, z, 3) < .045 else 'end_stone'


def build_floor(lay):
    R = math.ceil(FLOOR_R)
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            if on_floor(x, z):
                lay.put(x, 0, z, floor_block(x, z))
                for y in range(-8, 0):
                    lay.put(x, y, z, 'end_stone')


# ---------------------------------------------------------------- the skirt and kerb

def skirt_edge(a):
    return 56 + 6 * anoise(a, 30, 11)


def build_skirt(lay):
    """Outside the floor the ground falls away into the natural island, under a kerb of brick."""
    for x in range(LO, HI + 1):
        for z in range(LO, HI + 1):
            d2 = x * x + z * z
            if d2 <= FLOOR_R2:
                continue
            r = math.sqrt(d2)
            a = az_of(x, z)
            edge = skirt_edge(a)
            if r > edge:
                continue
            if r <= KERB_R:
                top = 0
                state = 'end_stone_bricks'
            else:
                t = (r - KERB_R) / (edge - KERB_R)
                top = round(-1 - 7 * t ** 1.3 + 2 * (vnoise(x, z, 5, 12) - .5))
                top = max(-10, min(-1, top))
                state = 'end_stone'
                if r < KERB_R + 3 and noise(x, top, z, 13) < .25:
                    state = 'end_stone_bricks'          # fallen kerb stones
            bottom = -14 - round(4 * vnoise(x, z, 7, 14))
            for y in range(bottom, top + 1):
                lay.put(x, y, z, state if y == top else 'end_stone')
            # The kerb's broken low wall: a block high in places, open toward the arrival.
            if r <= KERB_R and r > FLOOR_R + 1 and abs(adiff(a, 90)) > 14 and anoise(a, 8, 15) > .58:
                lay.put(x, 1, z, 'end_stone_bricks')


# ---------------------------------------------------------------- the broken pillars

def build_pillars(lay):
    """Ten broken obsidian pillars outside the floor where vanilla's spikes stood a little further in."""
    for k, (az, top) in enumerate(zip(PILLAR_AZ, PILLAR_TOPS)):
        r = math.radians(az)
        cx, cz = PILLAR_RING * math.cos(r), PILLAR_RING * math.sin(r)
        rad = 2.6 + .4 * (k % 3)
        slant_dir = math.radians((az * 7 + 40) % 360)
        cells = [(x, z) for x in range(math.floor(cx - rad) - 1, math.ceil(cx + rad) + 2)
                 for z in range(math.floor(cz - rad) - 1, math.ceil(cz + rad) + 2)]
        for x, z in cells:
            d = math.hypot(x - cx, z - cz)
            if d > rad:
                continue
            # A slanted, jagged break, its highest block at `top`.
            s = ((x - cx) * math.cos(slant_dir) + (z - cz) * math.sin(slant_dir) + rad) / (2 * rad)
            h = top - round(3 * (1 - s) + 1.6 * noise(x, 0, z, 20 + k))
            for y in range(-14, h + 1):
                state = 'crying_obsidian' if noise(x, y, z, 21) < .04 else 'obsidian'
                lay.put(x, y, z, state)
        # Its fallen pieces lie on the skirt round its foot, never on the floor.
        for j in range(3 + k % 3):
            fa = az + 70 * (noise(k, j, 0, 22) - .5)
            fr = PILLAR_RING + 4 + 4 * noise(k, j, 1, 23)
            fx = round(fr * math.cos(math.radians(fa)))
            fz = round(fr * math.sin(math.radians(fa)))
            size = 1 + (j % 2)
            for dx in range(size):
                for dz in range(size):
                    x, z = fx + dx, fz + dz
                    ground = column_top(lay, x, z)
                    if ground is None or x * x + z * z <= (KERB_R + 1) ** 2:
                        continue
                    lay.put(x, ground + 1, z, 'obsidian' if noise(x, 0, z, 24) < .7 else 'crying_obsidian')
                    if size == 2 and dx == dz == 0:
                        lay.put(x, ground + 2, z, 'obsidian')


def column_top(lay, x, z):
    for y in range(40, BOTTOM - 1, -1):
        if lay.get(x, y, z):
            return y
    return None


# ---------------------------------------------------------------- the way home

def way_home():
    """The basin opened at the floor's centre after the win: vanilla end portal blocks a block below
    the floor, a bedrock wall and bed under them, a flush rim of purpur. It is placed over the floor,
    so it also holds the air that opens the basin."""
    out = {}
    ox, oy, oz = WAY_HOME_ORIGIN
    for x in range(-4, 5):
        for z in range(-4, 5):
            d2 = x * x + z * z
            local = lambda y: (x - ox, y - oy, z - oz)
            if d2 <= 6:
                out[local(-2)] = 'minecraft:bedrock'
                out[local(-1)] = 'minecraft:end_portal'
                out[local(0)] = 'minecraft:air'
            elif d2 <= 13:
                out[local(-2)] = 'minecraft:bedrock'
                out[local(-1)] = 'minecraft:bedrock'
                out[local(0)] = 'minecraft:purpur_block'
    return out


# ---------------------------------------------------------------- assembly and checks

def build_island():
    lay = Layout()
    build_floor(lay)
    build_skirt(lay)
    build_pillars(lay)
    verify(lay)
    return lay


def verify(lay):
    names = {}
    for (x, y, z), state in lay.b.items():
        assert LO <= x <= HI and LO <= z <= HI and BOTTOM <= y, f'outside the tiles: {(x, y, z)} {state}'
        assert x * x + z * z <= CLEAR_R * CLEAR_R or y < 1, f'built above the floor outside the cleared radius: {(x, y, z)}'
        base = state.split(':', 1)[1].split('[')[0]
        names[base] = names.get(base, 0) + 1
    bad = set(names) - ALLOWED
    assert not bad, f'blocks outside the palette (fluids, gravity blocks or anything that hurts?): {bad}'
    R = math.ceil(FLOOR_R)
    for x in range(-R, R + 1):
        for z in range(-R, R + 1):
            if on_floor(x, z):
                assert lay.get(x, 0, z), f'floor hole {(x, z)}'
                for y in range(1, 64):
                    assert lay.get(x, y, z) is None, f'something on the fight floor at {(x, y, z)}: {lay.get(x, y, z)}'
    x, y, z = ARRIVAL
    assert on_floor(x, z) and z * z > (FLOOR_R - 4) ** 2, 'the arrival must be on the floor, just inside its south edge'
    assert lay.get(x, y - 1, z) and lay.get(x, y, z) is None and lay.get(x, y + 1, z) is None, 'the arrival is blocked'
    ox, oy, oz = WAY_HOME_ORIGIN
    for (lx, ly, lz), state in way_home().items():
        wx, wy, wz = lx + ox, ly + oy, lz + oz
        assert state.split(':')[1] in WAY_HOME_ALLOWED, state
        # A reset lays the floor's tiles again, which must cover every cell the basin changed.
        assert on_floor(wx, wz) and lay.get(wx, wy, wz), f'the way home reaches past the floor at {(wx, wy, wz)}'


def tiles(lay):
    structure_nbt = _nbt_writer()
    grouped = {}
    for (x, y, z), state in lay.b.items():
        i, j = (x - TILE_ORIGIN) // TILE, (z - TILE_ORIGIN) // TILE
        grouped.setdefault((i, j), {})[x - TILE_ORIGIN - i * TILE, y - BOTTOM, z - TILE_ORIGIN - j * TILE] = state
    out = {STRUCTURES / f'arena_{i}_{j}.nbt': structure_nbt(blocks, (TILE, max(p[1] for p in blocks) + 1, TILE))
           for (i, j), blocks in sorted(grouped.items())}
    out[STRUCTURES / 'way_home.nbt'] = structure_nbt(way_home(), (9, 3, 9))
    return out


def _nbt_writer():
    """The crypt builder's vanilla structure writer; the realms share the format and its stable gzip."""
    import importlib.util
    spec = importlib.util.spec_from_file_location('hollow_crypt_layout', ROOT / 'art/hollow_crypt/build_layout.py')
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module.structure_nbt


# ---------------------------------------------------------------- rough preview

COLOURS = {
    'end_stone': (219, 222, 158), 'end_stone_bricks': (196, 199, 138), 'obsidian': (24, 18, 38),
    'crying_obsidian': (70, 20, 120), 'purpur_block': (169, 125, 169), 'purpur_pillar': (171, 129, 171),
    'end_portal': (10, 40, 30), 'bedrock': (80, 80, 80),
}


def export(lay):
    from PIL import Image
    OUT.mkdir(parents=True, exist_ok=True)
    size, scale = HI - LO + 1, 4
    top = Image.new('RGB', (size, size), (12, 10, 20))
    tops = {}
    for (x, y, z), state in lay.b.items():
        if y > tops.get((x, z), (-999,))[0]:
            tops[x, z] = (y, state)
    for (x, z), (y, state) in tops.items():
        c = COLOURS.get(state.split(':')[1].split('[')[0], (255, 0, 255))
        shade = max(.45, min(1.15, 1 + y * .03))
        top.putpixel((x - LO, z - LO), tuple(min(255, int(v * shade)) for v in c))
    for x in range(-1, 2):
        top.putpixel((ARRIVAL[0] + x - LO, ARRIVAL[2] - LO), (255, 60, 60))
    top.resize((size * scale, size * scale), Image.NEAREST).save(OUT / 'top.png')
    # Looking north from the south: the nearest block in each column of the elevation.
    side = Image.new('RGB', (size, 70), (12, 10, 20))
    near = {}
    for (x, y, z), state in lay.b.items():
        if z > near.get((x, y), (-999,))[0]:
            near[x, y] = (z, state)
    for (x, y), (z, state) in near.items():
        py = 45 - y
        if 0 <= py < 70:
            c = COLOURS.get(state.split(':')[1].split('[')[0], (255, 0, 255))
            shade = max(.5, min(1.1, .75 + (z - LO) / size * .4))
            side.putpixel((x - LO, py), tuple(int(v * shade) for v in c))
    side.resize((size * scale, 70 * scale), Image.NEAREST).save(OUT / 'side.png')
    (OUT / 'index.html').write_text(
        '<!doctype html><meta charset="utf-8"><meta name="viewport" content="width=device-width">'
        '<title>End island arena</title><body style="background:#111;color:#ddd;font:14px sans-serif;margin:16px">'
        '<p>Top-down (north up; red mark: the arrival). Rough preview, not the game.</p>'
        '<img src="top.png" style="max-width:100%;image-rendering:pixelated">'
        '<p>From the south.</p><img src="side.png" style="max-width:100%;image-rendering:pixelated"></body>\n')


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--install', action='store_true', help='write the structure tiles into the mod')
    ap.add_argument('--check', action='store_true', help='verify the installed tiles match this builder')
    args = ap.parse_args()
    island = build_island()
    if args.install or args.check:
        files = tiles(island)
        stale = set(STRUCTURES.glob('*.nbt')) - set(files)
        if args.check:
            for path, data in files.items():
                assert path.exists() and path.read_bytes() == data, f'Drift: {path.relative_to(ROOT)}'
            assert not stale, f'Unexpected tiles: {sorted(p.name for p in stale)}'
            print(f'End island verified: {len(files)} structure files ({len(island.b)} blocks)')
        else:
            STRUCTURES.mkdir(parents=True, exist_ok=True)
            for path in stale:
                path.unlink()
            for path, data in files.items():
                path.write_bytes(data)
            print(f'Installed {len(files)} structure files ({len(island.b)} blocks, {sum(map(len, files.values())) // 1024} KiB)')
        raise SystemExit
    export(island)
    counts = {}
    for s in island.b.values():
        k = s.split(':', 1)[1]
        counts[k] = counts.get(k, 0) + 1
    print(f'island: {len(island.b)} blocks; ' + ', '.join(f'{k} {v}' for k, v in sorted(counts.items(), key=lambda kv: -kv[1])))
    print(f'Preview written to {OUT.relative_to(ROOT)}/index.html')
