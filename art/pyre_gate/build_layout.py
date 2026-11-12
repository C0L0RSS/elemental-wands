#!/usr/bin/env python3
"""Authors the Burned Gate, the way in to the Pyre King's fight (the developer's idea, Nov 11):
a bridge that leads off from a lava shore in the Nether to nowhere, over the lake. At its end,
on a last pier, stands a lone gate with nothing behind it. The gate looks like a portal: a
banked fire fills its arch, held shut by chains, and on the chains hangs the king's seal,
which players touch to be taken to him (hall/PyreGate.java).

    py -3 art/pyre_gate/build_layout.py            # writes preview pictures
    py -3 art/pyre_gate/build_layout.py --install  # writes the structure, the seal's and veil's assets and PyreGateLayout.java
    py -3 art/pyre_gate/build_layout.py --check    # verifies what is installed

Coordinates are block cells in the gate's own frame. The origin is the middle of the gate's
arch, y=0 is the road's deck (players stand at y=1), +Z runs back along the bridge to the shore
and -Z is the open lake behind the gate. +X is east when the gate faces south, as it is
authored; the game turns the whole template. The previews go to the ignored
.local-previews/pyre-gate/.
"""
import argparse, importlib.util, io, json, math, zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / '.local-previews/pyre-gate'
JAR = Path.home() / '.gradle/caches/fabric-loom/1.21.10/minecraft-client.jar'


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


hall = _load('pyre_hall_layout', ROOT / 'art/pyre_hall/build_layout.py')
Layout, noise, masonry, char, rubble, connect = hall.Layout, hall.noise, hall.masonry, hall.char, hall.rubble, hall.connect

# ---------------------------------------------------------------- the plan

X0, X1 = -8, 8            # template bounds
Z0, Z1 = -4, 47
Y0, Y1 = -16, 19
GATE_Z = 1                # the gate's stone: |z| <= GATE_Z
PYLON_IN, PYLON_OUT = 4, 6   # its two pylons: PYLON_IN <= |x| <= PYLON_OUT
VEIL_Z = -1               # the fire in the arch, at its back
CHAIN_Z = 0               # the chains that hold it shut, and the seal
SEAL = (0, 2, CHAIN_Z)    # at a player's eye height
STAND = (0, 1, 5)         # where a party comes back to, on the gate's platform, facing it
PLATFORM = (0, 2, 7.5)    # the last pier's deck: centre x, z and radius
BRIDGE = (9, 40)          # the bridge's deck runs between these, 7 wide
PIERS = (16, 28)
SHORE = 40                # the shore's apron starts here
SEAL_BLOCK = 'elementalwands:pyre_seal[facing=south,lit=false]'
# The fire in the arch is one picture across the whole opening, VEIL_COLS x VEIL_ROWS blocks; each of its blocks
# shows its own piece of it (piece = row from the threshold * VEIL_COLS + column from the west).
VEIL_COLS, VEIL_ROWS = 7, 10
VEIL_EYE = 76             # the vortex's eye, in pixels above the threshold
VEIL_FRAMES = 32


def veil_block(x, y):
    return f'elementalwands:pyre_gate_veil[facing=south,lit=false,piece={(y - 1) * VEIL_COLS + x + VEIL_COLS // 2}]'

# Where the structure looks before it settles (world/PyreGateStructure.java), in this frame: these
# must be shore, at about one height with open air over them...
LAND = ((0, 42), (-4, 44), (4, 44), (0, 47))
# ...and these lava with open air over it.
SEA = ((0, 34), (0, 26), (0, 16), (0, 6), (-6, 2), (6, 2), (0, -6), (0, -14))
# Height of open air a land column needs over its floor, and a sea column over the lava.
HEADROOM = 14


def arch_open(x, y):
    """The gateway: 7 wide, pointed, 10 high."""
    if y < 1:
        return False
    if y <= 6:
        return abs(x) <= 3.45
    return (abs(x) + 1.6) ** 2 + (y - 6) ** 2 <= 25.5


def pylon_top(side):
    return 17 if side > 0 else 12       # the west pylon is broken down


def wall_top(x):
    return 13 + round(noise(x, 0, 0, 201) * 1.6 - .5)


def scorched(x, y, z, top):
    """Stone near a broken top is burnt black."""
    return y >= top - 2 and noise(x, y, z, 202) < .6 or noise(x, y, z, 203) < .04


def on_platform(x, z, grow=0.0):
    cx, cz, r = PLATFORM
    return math.hypot(x - cx, z - cz) <= r + grow and z >= -3 - noise(x, 0, 0, 219) * 1.4


def build_platform(lay):
    """The last pier: a round drum out of the lava, corbelled out under a round deck."""
    cx, cz, r = PLATFORM
    for x in range(X0, X1 + 1):
        for z in range(Z0, 11):
            d = math.hypot(x - cx, z - cz)
            if not on_platform(x, z):
                continue
            for y in range(Y0, 1):
                reach = 5.2 if y < -3 else 5.2 + (y + 3) * .8     # the drum, then three courses stepping out
                if d > reach + .01 and y < 0:
                    continue
                if y == 0:
                    ring = d > r - 1.2
                    state = ('chiseled_polished_blackstone' if ring and (x + z) % 2 == 0 else 'polished_blackstone' if ring
                             else 'nether_bricks' if abs(x) <= 2 and noise(x, y, z, 213) > .12 else 'cracked_nether_bricks' if abs(x) <= 2
                             else 'polished_blackstone_bricks' if noise(x, y, z, 224) > .25 else 'cracked_polished_blackstone_bricks')
                elif y >= -3:
                    state = 'polished_blackstone' if noise(x, y, z, 210) > .2 else 'blackstone'
                else:
                    state = 'blackstone' if noise(x, y, z, 205) < .65 else 'basalt[axis=y]' if noise(x, y, z, 206) < .8 else 'polished_blackstone_bricks'
                lay.put(x, y, z, state)
            # A low broken kerb round the deck's rim, open toward the bridge and behind the gate.
            if d > r - 1.2 and abs(x) > 3 and z > GATE_Z + 1 and noise(x, 1, z, 226) < .6:
                lay.put(x, 1, z, 'polished_blackstone_bricks' if noise(x, 1, z, 227) > .4 else 'blackstone')


def build_gate_arch(lay):
    for x in range(-PYLON_OUT - 1, PYLON_OUT + 2):
        side = 1 if x > 0 else -1
        for z in range(-GATE_Z - 1, GATE_Z + 2):
            pylon = PYLON_IN <= abs(x) <= PYLON_OUT and abs(z) <= GATE_Z
            plinth = PYLON_IN - 1 < abs(x) <= PYLON_OUT + 1 and abs(z) <= GATE_Z + 1 and abs(x) >= PYLON_IN
            head = abs(x) < PYLON_IN and abs(z) <= GATE_Z
            if not lay.get(x, 0, z) and (pylon or plinth):
                continue    # off the platform's broken back edge
            if pylon:
                top = pylon_top(side) - round(noise(x, 1, z, 204) * (3.5 if side < 0 else 1.2))
                for y in range(1, top + 1):
                    if scorched(x, y, z, top):
                        state = char(x, y, z, 206)
                    elif y == 11 and side > 0:
                        state = 'chiseled_polished_blackstone'
                    elif y == 12 and side > 0:
                        state = 'gilded_blackstone' if noise(x, y, z, 207) < .5 else 'polished_blackstone'
                    else:
                        state = masonry(x, y, z, 208)
                    lay.put(x, y, z, state)
                if side > 0 and (x + z) % 2 == 0 and noise(x, 2, z, 209) < .75:
                    lay.put(x, top + 1, z, 'polished_blackstone_bricks')     # what is left of its crown
            elif plinth:
                for y in (1, 2):
                    if y == 2 and noise(x, y, z, 211) < .3:
                        continue   # chipped
                    lay.put(x, y, z, 'chiseled_polished_blackstone' if y == 2 and noise(x, y, z, 210) < .5 else 'polished_blackstone')
            if head:
                top = wall_top(x)
                for y in range(1, top + 1):
                    if arch_open(x, y):
                        continue
                    if scorched(x, y, z, top):
                        state = char(x, y, z, 214)
                    elif abs(z) == GATE_Z and y == 11:
                        state = 'gilded_blackstone' if noise(x, y, z, 215) < .6 else 'chiseled_polished_blackstone'
                    elif abs(z) == GATE_Z and any(arch_open(x + dx, y + dy) for dx, dy in ((1, 0), (-1, 0), (0, -1))):
                        state = 'polished_blackstone'       # the arch's dressed ring
                    else:
                        state = masonry(x, y, z, 216)
                    lay.put(x, y, z, state)
    # The pylons' faces toward the arch are dressed too.
    for side in (-1, 1):
        for y in range(1, 7):
            for z in (-GATE_Z, GATE_Z):
                if lay.get(side * PYLON_IN, y, z):
                    lay.put(side * PYLON_IN, y, z, 'polished_blackstone' if y > 1 else 'chiseled_polished_blackstone')


def build_veil(lay):
    """The fire in the arch, the chains across it, and the seal where they cross."""
    for x in range(-4, 5):
        for y in range(1, 12):
            if arch_open(x, y):
                lay.put(x, y, VEIL_Z, veil_block(x, y))
    for y in (2, 5):
        for x in range(-3, 4):
            if arch_open(x, y):
                lay.put(x, y, CHAIN_Z, 'iron_chain[axis=x]')
    for y in range(3, 11):
        if arch_open(0, y):
            lay.put(0, y, CHAIN_Z, 'iron_chain[axis=y]')
    for x, low in ((-2, 7), (2, 8)):     # two that hang loose from the arch
        for y in range(low, 11):
            if arch_open(x, y):
                lay.put(x, y, CHAIN_Z, 'iron_chain[axis=y]')
    lay.put(*SEAL, SEAL_BLOCK)


def build_bridge(lay):
    """A Nether-fortress bridge on piers, from the shore out to the gate's platform. Whole enough to walk."""
    span = (PIERS[1] - PIERS[0]) / 2 - 1.5
    for z in range(BRIDGE[0] - 2, BRIDGE[1] + 1):
        for x in range(-3, 4):
            if lay.get(x, 0, z):
                continue
            pd = min(min(abs(z - p) for p in PIERS), abs(z - (BRIDGE[0] - 3)), abs(z - (BRIDGE[1] + 1)))
            pier = min(abs(z - p) for p in PIERS) <= 1.5
            if pier:
                under = Y0
            else:
                f = min(1.0, max(0, pd - 1.5) / span)
                under = -1 - round(4 * (1 - math.sqrt(max(0., 1 - (1 - f) ** 2))))
            # Broken edges, never the middle three: the road can always be walked.
            if abs(x) >= 2 and not pier and noise(x, 0, z, 234) < (.2 if abs(x) == 3 else .06):
                continue
            for y in range(under, 1):
                state = 'nether_bricks' if noise(x, y, z, 231) > .13 else 'cracked_nether_bricks'
                if pier and y < -9 and noise(x, y, z, 232) < .3:
                    state = 'red_nether_bricks'
                lay.put(x, y, z, state, replace=False)
            if abs(x) == 3 and noise(x, 1, z, 233) > .3:
                lay.put(x, 1, z, 'nether_brick_fence')


def build_shore(lay):
    """The bridge's head on the shore: a small paved apron with two fire bowls."""
    for z in range(SHORE, Z1 + 1):
        half = 3.4 + (z - SHORE) * .75
        for x in range(X0, X1 + 1):
            edge = min(half, 7.6) - abs(x) + (noise(x, 0, z, 220) - .5) * 2
            if edge < 0 or lay.get(x, 0, z):
                continue
            road = abs(x) <= 2
            for y in range(-6, 1):
                if y < 0:
                    state = rubble(x, y, z, 221) if noise(x, y, z, 222) < .5 else 'blackstone'
                elif road:
                    state = 'nether_bricks' if noise(x, y, z, 223) > .15 else 'cracked_nether_bricks'
                else:
                    state = 'polished_blackstone_bricks' if noise(x, y, z, 224) > .3 else 'cracked_polished_blackstone_bricks' if noise(x, y, z, 225) > .3 else 'blackstone'
                lay.put(x, y, z, state)
    for side in (-1, 1):
        cx, cz = side * 5, 44
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                for y in range(-4, 1):
                    lay.put(cx + dx, y, cz + dz, 'blackstone', replace=False)
                lay.put(cx + dx, 1, cz + dz, 'polished_blackstone' if (dx, dz) != (0, 0) else 'polished_blackstone_bricks')
                if (dx, dz) == (0, 0):
                    lay.put(cx, 2, cz, 'chiseled_polished_blackstone')
                    lay.put(cx, 3, cz, 'netherrack')
                    lay.put(cx, 4, cz, 'fire')
                elif dx == 0 or dz == 0:
                    lay.put(cx + dx, 3, cz + dz, 'gilded_blackstone' if noise(cx + dx, 3, cz + dz, 229) < .5 else 'chiseled_polished_blackstone')


def clear_air(lay):
    """Open air the template carries, so that rock in its way is cut back: over the platform, the
    bridge and the apron, and through the arch."""
    for x in range(X0, X1 + 1):
        for z in range(Z0, Z1 + 1):
            if on_platform(x, z, 1) or z < 11 and abs(x) <= 7:
                top = 12
            elif z >= SHORE - 1:
                top = 9
            elif abs(x) <= 4:
                top = 8
            else:
                continue
            for y in range(1, top):
                lay.put(x, y, z, 'air', replace=False)


def build_gate():
    lay = Layout()
    build_platform(lay)
    build_gate_arch(lay)
    build_bridge(lay)
    build_shore(lay)
    build_veil(lay)
    connect(lay)
    clear_air(lay)
    verify(lay)
    return lay


def verify(lay):
    for (x, y, z) in lay.b:
        assert X0 <= x <= X1 and Y0 <= y <= Y1 and Z0 <= z <= Z1, (x, y, z)
    assert lay.get(*SEAL) == SEAL_BLOCK
    assert lay.get(SEAL[0], SEAL[1], VEIL_Z) == veil_block(SEAL[0], SEAL[1])
    cells = {(x, y) for (x, y, z), s in lay.b.items() if z == VEIL_Z and 'pyre_gate_veil' in s}
    assert cells == veil_cells(), 'the fire in the arch does not fill its picture'
    # A clear walk down the middle of the road from the shore to the seal.
    for z in range(SEAL[2] + 1, Z1 + 1):
        assert lay.name(0, 0, z) not in (None, 'air'), f'the road has a hole at z={z}'
        assert all(lay.name(0, k, z) == 'air' for k in (1, 2)), f'the way to the seal is blocked at z={z}'
    sx, sy, sz = STAND
    assert lay.name(sx, sy - 1, sz) not in (None, 'air') and all(lay.name(sx, sy + k, sz) == 'air' for k in (0, 1)), 'the stand point is not open ground'
    # Stone over a party that waits in the arch.
    assert any(lay.name(0, y, GATE_Z) not in (None, 'air') for y in range(10, 16)), 'no arch over the seal'
    # Nothing behind the gate but its broken lip: the road goes nowhere.
    assert not any(z < -4 for (_, _, z) in lay.b)
    for x, z in LAND:
        assert lay.name(x, 0, z) not in (None, 'air'), f'land sample {(x, z)} is not on the apron'


# ---------------------------------------------------------------- previews

COLORS = {'pyre_seal': (232, 186, 70), 'pyre_gate_veil': (230, 96, 20)}


def color(name):
    if name not in COLORS:
        alias = {'basalt': 'basalt_side', 'polished_basalt': 'polished_basalt_side', 'fire': 'fire_0',
                 'nether_brick_fence': 'nether_bricks'}.get(name, name)
        try:
            with zipfile.ZipFile(JAR) as archive:
                img = Image.open(io.BytesIO(archive.read(f'assets/minecraft/textures/block/{alias}.png'))).convert('RGBA').crop((0, 0, 16, 16))
            px = [img.getpixel((i, j)) for i in range(16) for j in range(16)]
            px = [p for p in px if p[3] > 40]
            COLORS[name] = tuple(sum(p[i] for p in px) // max(1, len(px)) for i in range(3))
        except KeyError:
            COLORS[name] = (255, 0, 255)
    return COLORS[name]


def render(lay, path, turn=0, scale=8):
    """An isometric picture; `turn` quarter-turns the model first."""
    from PIL import ImageDraw
    cells = [((x, y, z), s.split(':', 1)[1].split('[')[0]) for (x, y, z), s in lay.b.items() if not s.endswith(':air')]
    for _ in range(turn % 4):
        cells = [((-z, y, x), n) for (x, y, z), n in cells]
    solid = {p for p, n in cells if n not in ('iron_chain', 'nether_brick_fence', 'fire', 'pyre_gate_veil', 'pyre_seal')}
    w, h = scale * 2, scale

    def at(x, y, z):
        return (x - z) * w // 2, (x + z) * h // 2 - y * scale

    pts = [at(x, y, z) for (x, y, z), _ in cells]
    ox, oy = min(p[0] for p in pts) - w, min(p[1] for p in pts) - scale * 2
    img = Image.new('RGB', (max(p[0] for p in pts) - ox + w * 2, max(p[1] for p in pts) - oy + scale * 3), (60, 18, 8))
    draw = ImageDraw.Draw(img)
    for (x, y, z), name in sorted(cells, key=lambda c: (c[0][0] + c[0][2], c[0][1])):
        if (x, y, z) in solid and all(n in solid for n in ((x + 1, y, z), (x, y + 1, z), (x, y, z + 1))):
            continue
        cx, cy = at(x, y, z)
        cx -= ox
        cy -= oy
        r, g, b = color(name)
        if name in ('iron_chain', 'nether_brick_fence', 'fire'):
            draw.line([(cx, cy + h // 2), (cx, cy + h // 2 + scale)], fill=(r, g, b), width=2)
            continue
        top = [(cx, cy), (cx + w // 2, cy + h // 2), (cx, cy + h), (cx - w // 2, cy + h // 2)]
        left = [(cx - w // 2, cy + h // 2), (cx, cy + h), (cx, cy + h + scale), (cx - w // 2, cy + h // 2 + scale)]
        right = [(cx + w // 2, cy + h // 2), (cx, cy + h), (cx, cy + h + scale), (cx + w // 2, cy + h // 2 + scale)]
        draw.polygon(top, fill=(min(255, int(r * 1.25)), min(255, int(g * 1.25)), min(255, int(b * 1.25))))
        draw.polygon(left, fill=(int(r * .62), int(g * .62), int(b * .62)))
        draw.polygon(right, fill=(int(r * .85), int(g * .85), int(b * .85)))
    img.save(path)


# ---------------------------------------------------------------- mod resources

DATA = ROOT / 'src/main/resources/data/elementalwands'
ASSETS = ROOT / 'src/main/resources/assets/elementalwands'
JAVA = ROOT / 'src/main/java/com/anton/elementalwands/hall/PyreGateLayout.java'
def png(img):
    data = io.BytesIO()
    img.save(data, 'PNG')
    return data.getvalue()


def seal_texture(lit):
    """The king's seal: an iron disc rimmed with gold, his crown on it. Lit, the iron glows and the gold burns."""
    img = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    crown = {(7, 4), (8, 4), (7, 5), (8, 5), (4, 6), (7, 6), (8, 6), (11, 6), (4, 7), (5, 7), (7, 7), (8, 7), (10, 7), (11, 7)}
    crown |= {(x, y) for x in range(4, 12) for y in (8, 9)}
    band = {(x, 10) for x in range(4, 12)}
    for x in range(16):
        for y in range(16):
            d = math.hypot(x - 7.5, y - 7.5)
            if d > 6.7:
                continue
            n = noise(x, y, 0, 240)
            if d > 5.4:
                c = (255, 214, 96) if lit else (198, 150, 52) if n > .3 else (150, 108, 36)
            elif (x, y) in crown:
                c = (255, 246, 190) if lit else (232, 186, 70) if n > .25 else (190, 142, 46)
            elif (x, y) in band:
                c = (255, 190, 80) if lit else (150, 104, 34)
            elif lit:
                c = (150 + int(n * 60), 46 + int(n * 30), 16)
            else:
                c = (52 + int(n * 18), 50 + int(n * 16), 54 + int(n * 16))
            img.putpixel((x, y), (*c, 255))
    return png(img)


def seal_model(lit):
    """A round plate hung on the chains: three boxes make its octagon, 3 px thick, in the middle of the block."""
    elements = []
    for (x0, y0, x1, y1) in ((4, 1, 12, 15), (1, 4, 15, 12), (2, 2, 14, 14)):
        element = {'from': [x0, y0, 6.5], 'to': [x1, y1, 9.5], 'shade': not lit,
                   'faces': {face: {'texture': '#seal'} for face in ('north', 'south', 'east', 'west', 'up', 'down')}}
        if lit:
            element['light_emission'] = 15
        elements.append(element)
    name = 'pyre_seal_lit' if lit else 'pyre_seal'
    return json.dumps({'textures': {'seal': f'elementalwands:block/{name}', 'particle': f'elementalwands:block/{name}'},
                       'elements': elements}, indent=2) + '\n'


def seal_blockstate():
    turn = {'south': 0, 'west': 90, 'north': 180, 'east': 270}
    variants = {}
    for facing, y in turn.items():
        for lit in (False, True):
            variant = {'model': 'elementalwands:block/pyre_seal' + ('_lit' if lit else '')}
            if y:
                variant['y'] = y
            variants[f'facing={facing},lit={"true" if lit else "false"}'] = variant
    return json.dumps({'variants': variants}, indent=2) + '\n'


def veil_cells():
    """The arch's opening, as cells (x, y) of the frame: the blocks the fire fills."""
    return {(x, y) for x in range(-VEIL_COLS // 2, VEIL_COLS // 2 + 1) for y in range(1, VEIL_ROWS + 1) if arch_open(x, y)}


W, H = VEIL_COLS * 16, VEIL_ROWS * 16
TAU = math.tau
CELLS = veil_cells()


def inside(i, j):
    """Whether a pixel of the picture (from the top left, seen from the bridge) falls in the opening."""
    return 0 <= i < W and 0 <= j < H and (i // 16 - VEIL_COLS // 2, VEIL_ROWS - j // 16) in CELLS


def frame_distance():
    """Pixels from the stone round the opening (8-neighbour steps); the threshold counts as stone too."""
    from collections import deque
    INF = 10 ** 6
    d = {(i, j): (0 if not inside(i, j) else INF) for i in range(-1, W + 1) for j in range(-1, H + 1)}
    q = deque(p for p, v in d.items() if v == 0)
    while q:
        i, j = q.popleft()
        for di in (-1, 0, 1):
            for dj in (-1, 0, 1):
                n = (i + di, j + dj)
                if n in d and d[n] > d[i, j] + 1:
                    d[n] = d[i, j] + 1
                    q.append(n)
    return d


def hashf(n, salt):
    n = (n * 374761393 + salt * 668265263) & 0xffffffff
    n = (n ^ (n >> 13)) * 1274126177 & 0xffffffff
    return ((n ^ (n >> 16)) & 0xffff) / 0xffff


# Hard pixel shades, few of them, so the fire's edges are sharp (the developer, Nov 11: like fire, "sharp or
# aggressive", not the Necromancer's soft souls). Banked it is embers; lit, a blaze.
LIT = [(22, 3, 2), (70, 10, 4), (158, 34, 6), (228, 88, 12), (252, 160, 36), (255, 222, 110), (255, 250, 220)]
BANKED = [(10, 3, 3), (30, 6, 4), (70, 12, 5), (128, 28, 7), (186, 60, 12), (228, 110, 26)]
EYE = VEIL_EYE


def veil_frames(lit, count=VEIL_FRAMES):
    """The fire in the arch, frame by frame, one picture across the whole opening, so that the gate looks like a
    portal (the developer's idea). A vortex of fire turns in on a dark eye, its arms torn into licking tongues;
    jagged flames reach in from the stone all round and stand up from the threshold, and sparks rise. Every motion
    in it runs a whole number of times through the frames, so it loops."""
    dist = frame_distance()
    pal = LIT if lit else BANKED
    cx, cy = W / 2, H - EYE
    out = []
    spin = 2 if lit else 1
    for f in range(count):
        t = f / count * TAU
        img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        px = img.load()
        for i in range(W):
            for j in range(H):
                if not inside(i, j):
                    continue
                dx, dy = i + .5 - cx, (j + .5 - cy) * .78
                r = math.hypot(dx, dy) + .01
                a = math.atan2(dy, dx)
                # Five arms of fire turning in on a dark eye, the other way round from the crypt's. Their edges are
                # torn into licking tongues by fast ripples along them, and they go from dark to blazing in a few pixels.
                jag = .8 * math.sin(11 * a + .8 * r - 5 * t) + .45 * math.sin(23 * a - 1.7 * r + 7 * t + 1)
                wave = math.sin(5 * a - 3.6 * math.log(r) - spin * t + .75 * jag) * .5 + .5
                ring = math.exp(-((r - 31) / 22) ** 2) * min(1, (r / 9) ** 2)
                arm = min(1., max(0., (wave - .46) / .14)) * ring
                hot = min(1., max(0., (wave - .8) / .06)) * ring
                # The stone round the opening burns: jagged tongues reach in from it, flickering round the arch.
                d = dist[i, j]
                up = H - j
                lick = 2 + 9 * max(0., math.sin(23 * a - 6 * t) * .5 + .5 + .35 * math.sin(41 * a + 9 * t + 2) - .2)
                rim = min(1., max(0., 1.4 * (1 - d / lick)))
                # Tongues of flame stand up from the threshold, each its own height, wavering as they climb.
                sway = i + 1.4 * math.sin(.3 * up - 6 * t)
                g = .5 + .3 * math.sin(.37 * sway + 3 * t) + .25 * math.sin(.83 * sway - 2 * t + 1.3) + .17 * math.sin(1.71 * sway + 5 * t + .4)
                reach = (5 + 32 * max(0., min(1., g)) ** 1.3) * (1 if lit else .6)
                tongue = min(1., max(0., 1.6 * (1 - up / reach)))
                k = max(arm * .62 + hot * .38, rim * .72, tongue * .82) + .04
                if not lit:
                    k *= .78
                px[i, j] = pal[max(0, min(len(pal) - 1, int(k * (len(pal) - .4))))] + (255,)
        # Sparks that rise the height of the arch and loop.
        for s in range(18 if lit else 8):
            speed = 1 + int(hashf(s, 1) * 3)
            x = int(8 + hashf(s, 2) * (W - 16))
            y = int(H - 1 - ((f / count * speed + hashf(s, 3)) % 1) * H)
            for k, c in ((0, pal[-1]), (1, pal[-3])):
                if inside(x, y + k):
                    px[x, y + k] = c + (255,)
        out.append(img)
    return out


def veil_texture(lit):
    frames = veil_frames(lit)
    strip = Image.new('RGBA', (W, H * len(frames)))
    for n, frame in enumerate(frames):
        strip.paste(frame, (0, n * H))
    return png(strip)


def veil_model(lit, piece):
    """One block of the fire: a sheet two pixels thick across the middle of its block, showing its piece of the
    picture on both faces. From behind it is the same sheet, seen through."""
    name = 'pyre_gate_veil_lit' if lit else 'pyre_gate_veil'
    c, r = piece % VEIL_COLS, piece // VEIL_COLS
    u, v = 16 / VEIL_COLS, 16 / VEIL_ROWS
    u0, u1, v0, v1 = round(c * u, 4), round((c + 1) * u, 4), round((VEIL_ROWS - 1 - r) * v, 4), round((VEIL_ROWS - r) * v, 4)
    element = {'from': [0, 0, 7], 'to': [16, 16, 9], 'shade': False, 'light_emission': 15 if lit else 10,
               'faces': {'south': {'uv': [u0, v0, u1, v1], 'texture': '#veil'}, 'north': {'uv': [u1, v0, u0, v1], 'texture': '#veil'}}}
    return json.dumps({'textures': {'veil': f'elementalwands:block/{name}', 'particle': f'elementalwands:block/{name}'},
                       'elements': [element]}, indent=2) + '\n'


def veil_pieces():
    return sorted((y - 1) * VEIL_COLS + x + VEIL_COLS // 2 for x, y in CELLS)


def veil_blockstate():
    turn = {'south': 0, 'west': 90, 'north': 180, 'east': 270}
    variants = {}
    for facing, y in turn.items():
        for lit in (False, True):
            for piece in range(VEIL_COLS * VEIL_ROWS):
                model = 'elementalwands:block/pyre_gate_veil' + ('_lit' if lit else '') + f'/{piece if piece in veil_pieces() else veil_pieces()[0]}'
                variant = {'model': model}
                if y:
                    variant['y'] = y
                variants[f'facing={facing},lit={"true" if lit else "false"},piece={piece}'] = variant
    return json.dumps({'variants': variants}, indent=1) + '\n'


def java(lay):
    def pairs(points):
        return ', '.join('{%d, %d}' % p for p in points)
    return f'''package com.anton.elementalwands.hall;

/**
 * The Burned Gate's template: generated by art/pyre_gate/build_layout.py --install. Do not edit.
 * Cells are in the gate's own frame: the origin is the middle of the gate's arch on the road's deck, +z runs back
 * along the bridge to the shore, -z is the open lake behind the gate, and +x is east while the gate faces south, as
 * it is authored.
 */
public final class PyreGateLayout {{
    private PyreGateLayout() {{}}

    /** The template's size, and where the frame's origin is inside it. */
    public static final int SIZE_X = {X1 - X0 + 1}, SIZE_Y = {Y1 - Y0 + 1}, SIZE_Z = {Z1 - Z0 + 1};
    public static final int ORIGIN_X = {-X0}, ORIGIN_Y = {-Y0}, ORIGIN_Z = {-Z0};
    /** The seal, and the cell a party stands in when it comes back (x, y, z). */
    public static final int[] SEAL = {{{SEAL[0]}, {SEAL[1]}, {SEAL[2]}}}, STAND = {{{STAND[0]}, {STAND[1]}, {STAND[2]}}};
    /** The fire in the arch is one picture over this many blocks across and up; each of its blocks shows its own piece. */
    public static final int VEIL_COLS = {VEIL_COLS}, VEIL_ROWS = {VEIL_ROWS};
    /** The fire in the arch stands this far behind the seal, and reaches this far to each side of it, below it and above it. */
    public static final int VEIL_BEHIND = {CHAIN_Z - VEIL_Z}, VEIL_SIDE = 4, VEIL_DOWN = {SEAL[1] - 1}, VEIL_UP = {11 - SEAL[1]};
    /** Columns (x, z) that must be shore at about one height, and columns that must be lava, each with HEADROOM of open air. */
    public static final int[][] LAND = {{{pairs(LAND)}}}, SEA = {{{pairs(SEA)}}};
    public static final int HEADROOM = {HEADROOM};
    /** The road's deck stands this far above the lava at least, and at most. */
    public static final int DECK_MIN = 3, DECK_MAX = {-Y0 - 3};
}}
'''


def outputs(lay):
    structure_nbt = hall._nbt_writer()
    blocks = {(x - X0, y - Y0, z - Z0): s for (x, y, z), s in lay.b.items()}
    animation = json.dumps({'animation': {'frametime': 2, 'width': W, 'height': H}}, indent=2) + '\n'
    files = {
        DATA / 'structure/pyre_gate.nbt': structure_nbt(blocks, (X1 - X0 + 1, Y1 - Y0 + 1, Z1 - Z0 + 1)),
        ASSETS / 'textures/block/pyre_seal.png': seal_texture(False),
        ASSETS / 'textures/block/pyre_seal_lit.png': seal_texture(True),
        ASSETS / 'models/block/pyre_seal.json': seal_model(False).encode(),
        ASSETS / 'models/block/pyre_seal_lit.json': seal_model(True).encode(),
        ASSETS / 'blockstates/pyre_seal.json': seal_blockstate().encode(),
        ASSETS / 'textures/block/pyre_gate_veil.png': veil_texture(False),
        ASSETS / 'textures/block/pyre_gate_veil.png.mcmeta': animation.encode(),
        ASSETS / 'textures/block/pyre_gate_veil_lit.png': veil_texture(True),
        ASSETS / 'textures/block/pyre_gate_veil_lit.png.mcmeta': animation.encode(),
        ASSETS / 'blockstates/pyre_gate_veil.json': veil_blockstate().encode(),
        JAVA: java(lay).encode(),
    }
    for lit in (False, True):
        for piece in veil_pieces():
            files[ASSETS / f'models/block/pyre_gate_veil{"_lit" if lit else ""}/{piece}.json'] = veil_model(lit, piece).encode()
    return files


def stale(files):
    """Files of the fire in the arch that this builder no longer writes."""
    old = [ASSETS / 'models/block/pyre_gate_veil.json', ASSETS / 'models/block/pyre_gate_veil_lit.json']
    for folder in ('pyre_gate_veil', 'pyre_gate_veil_lit'):
        old += list((ASSETS / 'models/block' / folder).glob('*.json'))
    return [p for p in old if p.exists() and p not in files]


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--install', action='store_true', help='write the structure, the seal, the veil and PyreGateLayout.java into the mod')
    ap.add_argument('--check', action='store_true', help='verify the installed files match this builder')
    args = ap.parse_args()
    gate = build_gate()
    stone = sum(1 for s in gate.b.values() if not s.endswith(':air'))
    if args.install or args.check:
        files = outputs(gate)
        if args.check:
            for path, data in files.items():
                assert path.exists() and path.read_bytes() == data, f'Drift: {path.relative_to(ROOT)}'
            assert not stale(files), f'Unexpected files: {[str(p.relative_to(ROOT)) for p in stale(files)]}'
            print(f'Burned Gate verified: {len(files)} files ({stone} blocks)')
        else:
            for path in stale(files):
                path.unlink()
            for path, data in files.items():
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
            print(f'Installed the Burned Gate: {len(files)} files ({stone} blocks, {len(gate.b) - stone} cells of cleared air)')
        raise SystemExit
    OUT.mkdir(parents=True, exist_ok=True)
    render(gate, OUT / 'gate-and-bridge.png', turn=1)
    render(gate, OUT / 'gate-from-behind.png', turn=3)
    for lit in (False, True):
        frames = [Image.alpha_composite(Image.new('RGBA', (W, H), (34, 30, 34, 255)), f).resize((W * 3, H * 3), Image.NEAREST)
                  for f in veil_frames(lit)]
        frames[0].save(OUT / f'veil-{"lit" if lit else "banked"}.webp', save_all=True, append_images=frames[1:], duration=100, loop=0, lossless=True)
    print(f'Burned Gate: {stone} blocks; previews in {OUT.relative_to(ROOT)}/')
