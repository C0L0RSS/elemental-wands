#!/usr/bin/env python3
"""Authors the Storm Aerie: an abandoned Windcaller summoning gong on an Overworld peak, where the
Storm Roc's fight starts. Striking the gong carries the party up to the Storm Summit.

    python3 art/storm_aerie/build_layout.py             # writes the browser preview
    python3 art/storm_aerie/build_layout.py --serve     # ...and serves it on :8395

The peak's top is levelled into a small snowy summit. On it a weathered dark oak frame of blocks
holds the gong, which is a custom model rather than blocks: a round bronze plate four blocks
across with a raised boss, hung on two cords, with a wrapped mallet leaning against a post. In
game the model is drawn from one anchor block at the gong's foot; invisible part blocks across
its face catch the click. The preview draws the same model on a generated sample peak.

Coordinates are block cells; y=0 is where a player stands on the summit, so its top is y=-1.
North is -z. The gong hangs in the x-y plane through z=0.5 and faces south: players strike it
from the south. The preview lives in the ignored .local-previews/storm-aerie/.
"""
import argparse, base64, importlib.util, io, json, math, random, shutil, zipfile
from pathlib import Path
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
HERE = Path(__file__).resolve().parent
OUT = ROOT / '.local-previews/storm-aerie'


def _load(name, path):
    spec = importlib.util.spec_from_file_location(name, ROOT / path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


# The summit builder's noise, rock and relic models: the gong is the Windcallers' too.
S = _load('storm_summit_layout', 'art/storm_summit/build_layout.py')
noise, fbm, vnoise = S.noise, S.fbm, S.vnoise
JAR = S.JAR

FACING = 'south'                   # the side players strike the gong from
FORWARD, RIGHT = (0, 1), (-1, 0)   # (x, z) of south, and of south turned clockwise (west)
ANCHOR = 'elementalwands:storm_gong[facing=%s]' % FACING
PART = 'elementalwands:storm_gong_part[cell=%d,facing=' + FACING + ']'
SLIVER = 1 / 32                    # a cell the plate covers no more of than this (a 2 x 4 px nub) gets no click target
GONG_Y = 1                         # the anchor block, at the gong's foot; the plate hangs one block up
GONG_R = 2.0                       # the plate is 4 blocks across
CENTRE = (.5, GONG_Y + GONG_R, .5)
THICK = 3 / 16                     # the plate's thickness; the boss stands 2 px proud of each face
POST_X, BEAM_Y = 3, GONG_Y + 5     # posts either side, half a block clear of the plate; the beam
CORDS = (-.9, .9)                  # where the two cords meet the plate's shoulders, from its centre
PAD = (6.5, 5.5, 1.0)              # the levelled summit: half width, half depth, its centre's z
STAND = (4, 0, 0)                  # {forward, right, up} from the anchor's ground cell: facing the gong
CHESTS = ((1, 2, 0),)              # the reward chest, at the gong's foot
REACH = 4.5                        # a survival player's reach, from the eyes
# The template's box: worldgen levels the summit to it. Only the frame and gong are built in it.
BOX = ((-math.ceil(PAD[0]), -1, round(PAD[2] - PAD[1]) - 1), (math.ceil(PAD[0]), BEAM_Y + 1, round(PAD[2] + PAD[1]) + 1))


def at(f, r, u=0):
    """Layout cell of a {forward, right, up} offset from the anchor's ground cell (0, 0, 0)."""
    return FORWARD[0] * f + RIGHT[0] * r, u, FORWARD[1] * f + RIGHT[1] * r


def on_pad(x, z, shrink=0.0):
    return (x / (PAD[0] - shrink)) ** 2 + ((z - PAD[2]) / (PAD[1] - shrink)) ** 2 <= 1


def plate_in_cell(x, y):
    """The plate inside cell (x, y), row by row: for each of its four quarter-block rows (bottom
    up) the span (u0, u1) in pixels from the cell's low x side that the strip there covers, (0, 0)
    for none, and the share of the cell covered. The strips step with the pixel, so spans are whole."""
    rows, area = [], 0.0
    for r in range(4):
        half = strip_halves()[round((y - (CENTRE[1] - GONG_R)) * 4) + r] if 0 <= y - (CENTRE[1] - GONG_R) < 2 * GONG_R else 0
        u0, u1 = max(CENTRE[0] - half, x), min(CENTRE[0] + half, x + 1)
        rows.append((round((u0 - x) * 16), round((u1 - x) * 16)) if u1 > u0 else (0, 0))
        area += max(0.0, u1 - u0) / 4
    return rows, area


def part_cells():
    """The plate's cells, the anchor's (0, GONG_Y) first: every cell its rendered strips cover by
    more than a sliver, so a click anywhere on the visible plate lands on one."""
    cells = [(x, y) for y in range(GONG_Y, GONG_Y + 4) for x in range(-2, 3) if plate_in_cell(x, y)[1] > SLIVER]
    cells.remove((0, GONG_Y))
    return [(0, GONG_Y)] + cells


def cell_rows():
    """Each part cell's click shape: the plate's stepped outline inside it, as (u0, u1) pixel spans
    for its four quarter-block rows, u along the gong's left (+x for this south-facing layout)."""
    return [plate_in_cell(x, y)[0] for x, y in part_cells()]


# ---------------------------------------------------------------- the template

def build():
    """The frame, the gong's anchor and parts, and the remains. It carries no ground: worldgen
    levels the real summit to the box this spans, and the biome paints the surface, so snowy peaks
    get snow and stony peaks bare stone. The template's size, BOX, sets that box."""
    lay = S.Layout()
    # The frame: two dark oak posts with knee braces, a beam whose west end broke off flush with
    # its post, a slab roof with a gap and one lightning rod left standing.
    for side in (-1, 1):
        for y in range(-1, BEAM_Y):
            lay.put(side * POST_X, y, 0, 'dark_oak_log[axis=y]')
        lay.put(side * (POST_X - 1), BEAM_Y - 1, 0, 'stripped_dark_oak_wood[axis=x]')
    for x in range(-POST_X, POST_X + 2):
        lay.put(x, BEAM_Y, 0, 'stripped_dark_oak_log[axis=x]')
    for x in range(-POST_X + 1, POST_X + 1):
        if x != 1:
            lay.put(x, BEAM_Y + 1, 0, 'dark_oak_slab[type=bottom]')
    lay.put(-POST_X, BEAM_Y + 1, 0, 'oxidized_lightning_rod[facing=up]')
    for i, (x, y) in enumerate(part_cells()):
        lay.put(x, y, 0, ANCHOR if i == 0 else PART % i)
    # Who came before: a skull and bones by the left post, and the Roc's feathers.
    for (x, z), state in (((-4, 1), 'skeleton_skull[rotation=0]'), ((-5, 0), 'elementalwands:old_bones[facing=east]'),
                          ((2, 3), 'elementalwands:roc_feather[facing=west,stuck=false]'),
                          ((-1, -2), 'elementalwands:roc_feather[facing=north,stuck=false]'),
                          ((5, -1), 'elementalwands:roc_feather[facing=east,stuck=false]')):
        assert on_pad(x, z), (x, z)
        lay.put(x, 0, z, state)
    return lay


# ---------------------------------------------------------------- the gong model

def strip_halves():
    """Half widths of the plate's 16 strips, bottom up, in blocks, to the pixel."""
    return [round(math.sqrt(GONG_R ** 2 - (k / 4 + 1 / 8 - GONG_R) ** 2) * 16) / 16 for k in range(16)]


def gong_boxes():
    """The model as boxes in layout blocks: (low corner, high corner, textures by face, z rotation
    in degrees about a pivot or None). Faces: n/s are the plate's back and front."""
    cx, cy, cz = CENTRE
    boxes = []
    # The plate: strips a quarter block high, as wide as the circle at their middle, so its edge
    # steps round; its faces carry the round face texture, cell by cell.
    for k, half in enumerate(strip_halves()):
        y0 = cy - GONG_R + k / 4
        boxes.append(((cx - half, y0, cz - THICK / 2), (cx + half, y0 + 1 / 4, cz + THICK / 2), {'side': 'edge'}, None))
    boxes.append(((cx - 6 / 16, cy - 6 / 16, cz - THICK / 2 - 2 / 16), (cx + 6 / 16, cy + 6 / 16, cz + THICK / 2 + 2 / 16),
                  {'n': 'boss', 's': 'boss', 'side': 'edge'}, None))
    for dx in CORDS:
        x = cx + dx
        top = cy + math.sqrt(GONG_R ** 2 - dx * dx) - 1 / 16
        boxes.append(((x - 1 / 32, top, cz - 1 / 32), (x + 1 / 32, BEAM_Y, cz + 1 / 32), {'side': 'cord'}, None))
    # The mallet, leaning on the right-hand post: a wrapped head across the top of its handle. Its
    # foot stands where the head's outer top corner just meets the post's face, so it rests on the
    # post rather than sinking into it.
    lean, head_half, head_top = -24, 3 / 16, 1.78
    reach = head_half * math.cos(math.radians(lean)) - head_top * math.sin(math.radians(lean))
    base = (POST_X - reach - 1 / 64, 0, .9)
    boxes.append(((base[0] - 1 / 16, 0, base[2] - 1 / 16), (base[0] + 1 / 16, 1.5, base[2] + 1 / 16), {'side': 'handle'}, (lean, base)))
    boxes.append(((base[0] - head_half, 1.4, base[2] - 5 / 16), (base[0] + head_half, head_top, base[2] + 5 / 16), {'side': 'head'}, (lean, base)))
    return boxes


def box_quads(lo, hi, rot):
    """The box's six faces as (name, four corners) after rotating about z through the pivot."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    faces = {
        'n': [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
        's': [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
        'e': [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
        'w': [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
        'u': [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
        'd': [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
    }
    if rot:
        a, (px, py, _) = math.radians(rot[0]), rot[1]
        turn = lambda p: (px + (p[0] - px) * math.cos(a) - (p[1] - py) * math.sin(a),
                          py + (p[0] - px) * math.sin(a) + (p[1] - py) * math.cos(a), p[2])
        faces = {k: [turn(p) for p in v] for k, v in faces.items()}
    return faces


SHADE = {'n': .8, 's': .8, 'e': .6, 'w': .6, 'u': 1, 'd': .5}


def gong_tris(tile):
    """Preview triangles (x, y, z, u, v, shade, tile) for the gong model."""
    tris = []

    def quad(corners, uv, shade, t):
        (u0, v0, u1, v1) = uv
        uvs = [(u0, v0), (u1, v0), (u1, v1), (u0, v1)]
        for i in (0, 1, 2, 0, 2, 3):
            tris.append((*corners[i], *uvs[i], shade, t))
    names = {k: 'ew_gong_' + k for k in ('edge', 'boss', 'cord', 'handle', 'head')}
    for lo, hi, tex, rot in gong_boxes():
        for face, corners in box_quads(lo, hi, rot).items():
            quad(corners, (0, 0, 1, 1), SHADE[face], tile(names[tex.get(face, tex['side'])]))
    # The plate's faces: one textured quad per block cell of the 4 x 4 face texture, front and back.
    cx, cy, cz = CENTRE
    for i in range(4):
        for j in range(4):
            x0, x1 = cx - GONG_R + i, cx - GONG_R + i + 1
            y1 = cy + GONG_R - j
            y0 = y1 - 1
            t = tile(f'ew_gong_face_{i}_{j}')
            front = cz + THICK / 2 + .002
            quad([(x0, y1, front), (x1, y1, front), (x1, y0, front), (x0, y0, front)], (0, 0, 1, 1), .8, t)
            back = cz - THICK / 2 - .002
            quad([(x1, y1, back), (x0, y1, back), (x0, y0, back), (x1, y0, back)], (1, 0, 0, 1), .8, t)
    return tris


# ---------------------------------------------------------------- textures

BRONZE, BRIGHT = (156, 108, 48), (204, 156, 78)
VERDIGRIS, PALE = (70, 132, 104), (120, 180, 148)


FACE_ART = HERE / 'gong_face.png'


def gong_face_texture():
    """The plate's face, 64 px across: the painted face in gong_face.png (a worn thunderbird
    crest, lightning marks and patina in the engravings, the struck centre worn bright), clipped
    to the plate's stepped outline. Without it, the procedural face below."""
    if not FACE_ART.exists():
        return procedural_face()
    art = Image.open(FACE_ART).convert('RGBA')
    assert art.size == (64, 64), art.size
    halves = strip_halves()
    for y in range(64):
        for x in range(64):
            inside = abs(x + .5 - 32) <= halves[15 - y // 4] * 16
            r, g, b, a = art.getpixel((x, y))
            assert not inside or a == 255, f'gong_face.png has a hole at {x},{y}'
            art.putpixel((x, y), (r, g, b, 255) if inside else (0, 0, 0, 0))
    return art


def procedural_face():
    """A dark rolled rim, hammered bronze with two turned grooves, verdigris run down from the top
    and the struck centre worn bright. Clear outside the plate's stepped outline, which follows its
    strips exactly so the model shows no gaps."""
    img = Image.new('RGBA', (64, 64), (0, 0, 0, 0))
    halves = strip_halves()
    for y in range(64):
        for x in range(64):
            r = math.hypot(x - 31.5, y - 31.5)
            if abs(x + .5 - 32) > halves[15 - y // 4] * 16:
                continue
            m = vnoise(x / 5, y / 5, 111) * .6 + noise(x, y, 0, 110) * .4
            c = [BRONZE[k] + (BRIGHT[k] - BRONZE[k]) * m for k in range(3)]
            dx, dy = (x + 2 * (y // 4 % 2)) % 4, y % 4
            if (dx, dy) == (1, 1):
                c = [v * 1.12 for v in c]
            elif (dx, dy) in ((1, 2), (2, 2)):
                c = [v * .84 for v in c]
            if r > 27.5:
                c = [v * (.95 if 28.5 < r < 29.5 else .62) for v in c]
            elif abs(r - 19) < .7 or abs(r - 11) < .7:
                c = [v * .7 for v in c]
            elif abs(r - 18) < .6 or abs(r - 10) < .6:
                c = [v * 1.12 for v in c]
            elif r < 9:
                c = [v * 1.15 for v in c]
            # Verdigris: streaks run down from the top, and it gathers in the rim.
            streak = .5 * max(0.0, 1 - y / (12 + 20 * noise(x // 2, 0, 0, 113))) if noise(x // 2, 1, 0, 112) < .35 else 0
            rim = .55 * fbm(x / 5, y / 5, 114) if r > 27.5 and fbm(x / 5, y / 5, 114) > .5 else 0
            g = max(streak, rim)
            tint = PALE if noise(x, y, 0, 115) < .3 else VERDIGRIS
            c = [min(255, c[k] + (tint[k] - c[k]) * g) for k in range(3)]
            img.putpixel((x, y), tuple(round(v) for v in c) + (255,))
    return img


def flat_texture(base, dark, salt, size=16):
    img = Image.new('RGBA', (size, size))
    for y in range(size):
        for x in range(size):
            m = vnoise(x / 3, y / 3, salt) * .6 + noise(x, y, 0, salt + 1) * .4
            img.putpixel((x, y), tuple(round(dark[k] + (base[k] - dark[k]) * m) for k in range(3)) + (255,))
    return img


def gong_textures():
    face = gong_face_texture()
    out = {f'ew_gong_face_{i}_{j}': face.crop((i * 16, j * 16, i * 16 + 16, j * 16 + 16)) for i in range(4) for j in range(4)}
    out['ew_gong_edge'] = flat_texture((110, 76, 36), (70, 48, 24), 120)
    boss = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            r = math.hypot(x - 7.5, y - 7.5)
            c = [BRIGHT[k] * (1.15 - .05 * r) for k in range(3)]
            if r > 6.5:
                c = [v * .7 for v in c]
            boss.putpixel((x, y), tuple(min(255, round(v)) for v in c) + (255,))
    out['ew_gong_boss'] = boss
    out['ew_gong_cord'] = flat_texture((70, 52, 36), (40, 30, 22), 121)
    # The mallet: a weathered dark wood handle, its head wrapped in old brown cloth.
    handle = flat_texture((96, 72, 48), (62, 44, 28), 122)
    head = flat_texture((128, 86, 54), (84, 56, 34), 124)
    for y in range(0, 16, 4):
        for x in range(16):
            head.putpixel((x, y), tuple(round(v * .75) for v in head.getpixel((x, y))[:3]) + (255,))
    out['ew_gong_handle'], out['ew_gong_head'] = handle, head
    return out


# ---------------------------------------------------------------- the sample peak (preview only)

PEAK_R, PEAK_BOTTOM, BLEND = 30, -40, 8.0


BIOMES = ('snowy', 'stony')


def sample_peak(biome):
    """A peak with an east-west ridge whose top has been levelled for the gong: the summit at y=-1,
    the slopes eased into it, like the terrain blending worldgen does. Its surface is the biome's:
    a snowy peak (jagged or frozen) is snow with packed ice and bare stone on steep faces; a stony
    peak is bare stone with calcite, andesite and gravel."""
    raw = {}
    for x in range(-PEAK_R, PEAK_R + 1):
        for z in range(-PEAK_R, PEAK_R + 1):
            r = math.hypot(x * .8, z * 1.2)
            if r > PEAK_R:
                continue
            h = -(.3 * r + .026 * r * r) + 2.2 * (fbm(x / 7, z / 7, 91) - .5)
            crag = noise(x // 2, 0, z // 2, 92)
            if r > 9 and crag > .9:
                h += 6 * (crag - .9) / .1
            raw[x, z] = h
    pad = sorted(raw[c] for c in raw if on_pad(*c))
    lift = -1 - pad[len(pad) // 2]
    edge = [c for c in raw if on_pad(*c)]
    tops = {}
    for (x, z), h in raw.items():
        h += lift
        if on_pad(x, z):
            h = -1
        else:
            # Ease the slope into the summit over BLEND blocks from its edge.
            d = min(math.hypot(x - px, z - pz) for px, pz in edge)
            if d < BLEND:
                h = -1 + (h + 1) * (d / BLEND) ** 1.3
        tops[x, z] = math.floor(h + .5)
    world = S.Layout()
    for (x, z), top in tops.items():
        for y in range(max(PEAK_BOTTOM, top - 40), top + 1):
            world.put(x, y, z, 'stone' if noise(x, y, z, 94) < .8 else 'andesite')
        if biome == 'stony':
            n, g = fbm(x / 5, z / 5, 97), noise(x, top, z, 98)
            world.put(x, top, z, 'calcite' if n > .66 else 'gravel' if g < .06 else 'andesite' if n < .3 else 'stone')
            continue
        drop = max(top - tops.get((x + dx, z + dz), PEAK_BOTTOM) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        if drop >= 3 and top < -3:
            continue
        ice = fbm(x / 6, z / 6, 95) > .66
        world.put(x, top, z, 'packed_ice' if ice else 'snow_block')
        if not ice and fbm(x / 4, z / 4, 96) > .5 and not on_pad(x, z):
            world.put(x, top + 1, z, 'snow[layers=1]')
    return world


# ---------------------------------------------------------------- checks

def standable(world, x, y, z):
    solid = world.get(x, y - 1, z)
    return solid is not None and 'snow[' not in solid and world.get(x, y, z) is None and world.get(x, y + 1, z) is None


def levelled(x, z):
    """Inside the template's box, which worldgen levels to the summit's floor."""
    return BOX[0][0] < x < BOX[1][0] and BOX[0][2] < z < BOX[1][2]


def check(lay):
    """One anchor at the gong's foot with its click targets above and beside it, all facing the
    same way, and a stand point and chest cell clear on the summit within reach of the gong."""
    anchors = [p for p, s in lay.b.items() if s == ANCHOR]
    parts = [p for p, s in lay.b.items() if s.startswith(PART.split('[')[0] + '[')]
    assert anchors == [(0, GONG_Y, 0)], anchors
    assert len(parts) == len(part_cells()) - 1
    assert all(abs(x) <= 2 and GONG_Y <= y < GONG_Y + 4 and z == 0 for x, y, z in parts)
    for i, (x, y) in enumerate(part_cells()[1:], 1):
        assert lay.get(x, y, 0) == PART % i, (x, y, lay.get(x, y, 0))
    # The click shapes are the plate: every pixel of every strip lies in its cell's row span, unless
    # that cell is a sliver, and no span reaches past the strip.
    rows = dict(zip(part_cells(), cell_rows()))
    for k, half in enumerate(strip_halves()):
        y = GONG_Y + k // 4
        lo, hi = round((CENTRE[0] - half) * 16), round((CENTRE[0] + half) * 16)
        for x in range(-2, 3):
            span = rows.get((x, y), [(0, 0)] * 4)[k % 4]
            want = (max(lo - x * 16, 0), min(hi - x * 16, 16))
            want = want if want[1] > want[0] else (0, 0)
            assert span == want or (x, y) not in rows and plate_in_cell(x, y)[1] <= SLIVER, (k, x, span, want)
    stand = at(*STAND)
    clear = lambda x, y, z: levelled(x, z) and lay.get(x, y, z) is None and lay.get(x, y + 1, z) is None
    assert clear(*stand), stand
    for c in CHESTS:
        assert clear(*at(*c)) and at(*c) != stand
    eye = (stand[0] + .5, stand[1] + 1.62, stand[2] + .5)
    near = min(math.dist(eye, [min(max(e, c), c + 1) for e, c in zip(eye, p)]) for p in parts + anchors)
    assert near <= REACH, near
    return near


# ---------------------------------------------------------------- preview output

SHAPE_OF = {'dark_oak_slab': 14}
TEX_ALIAS = S.TEX_ALIAS | {
    'dark_oak_log': ('dark_oak_log_top', 'dark_oak_log', 'dark_oak_log_top'),
    'stripped_dark_oak_log': ('stripped_dark_oak_log',) * 3, 'stripped_dark_oak_wood': ('stripped_dark_oak_log',) * 3,
    'dark_oak_slab': ('dark_oak_planks',) * 3,
}
HIDDEN = ('elementalwands:storm_gong', 'minecraft:structure_void')   # the anchor and parts draw nothing themselves


def model_for(state):
    """As the summit builder's, but a skull's rotation 0 faces south, as the game places it."""
    m = S.model_for(state)
    return (m[0], (m[1] + 180) % 360) if m and m[0] == 'skeleton_skull' else m


def export(world, facts, biome):
    OUT.mkdir(parents=True, exist_ok=True)
    blocks, models = {}, []
    for p, s in world.b.items():
        if s.startswith(HIDDEN):
            continue
        m = model_for(s)
        if m:
            models.append((p, *m))
        else:
            blocks[p] = s
    keys = sorted({S.material_key(s) for s in blocks.values()})
    tiles, tile_index = [], {}
    with zipfile.ZipFile(JAR) as archive:
        painted = S.paint_textures(archive) | gong_textures()

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
            base = key.split('@')[0] if key.startswith('snow') else key
            top, side, bottom = TEX_ALIAS.get(base, (base,) * 3)
            shape = 10 + int(key.split('@')[1]) if key.startswith('snow@') else SHAPE_OF.get(key, 0)
            materials.append({'name': key, 'shape': shape, 'tex': [tile(top), tile(side), tile(bottom)]})
        tris = []
        for (x, y, z), name, yaw in models:
            tris += S.bake_model(name, x, y, z, yaw, tile)
        tris += gong_tris(tile)
    cols = 16
    atlas = Image.new('RGBA', (cols * 32, math.ceil(len(tiles) / cols) * 32))
    for i, img in enumerate(tiles):
        atlas.paste(img, (i % cols * 32, i // cols * 32))
    atlas.save(OUT / f'atlas-{biome}.png')
    index = {k: i for i, k in enumerate(keys)}
    opaque = {i for i, m in enumerate(materials) if m['shape'] == 0}
    mat = {p: index[S.material_key(s)] for p, s in blocks.items()}
    raw = bytearray()
    count = 0
    for (x, y, z), m in mat.items():
        mask = 0
        for bit, (dx, dy, dz) in enumerate(((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1))):
            if mat.get((x + dx, y + dy, z + dz)) not in opaque or m not in opaque:
                mask |= 1 << bit
        if mask:
            for v in (x, y, z, m | mask << 8):
                raw += v.to_bytes(2, 'little', signed=True)
            count += 1
    flat = bytearray()
    for t in tris:
        flat += b''.join(round(v * 4096).to_bytes(4, 'little', signed=True) for v in t[:5])
        flat += round(t[5] * 255).to_bytes(2, 'little') + t[6].to_bytes(2, 'little')
    data = {'materials': materials, 'atlasCols': cols, 'atlasRows': math.ceil(len(tiles) / cols),
            'aerie': {'count': count, 'blocks': len(world.b), 'instances': base64.b64encode(bytes(raw)).decode(),
                      'models': base64.b64encode(bytes(flat)).decode(), 'modelVerts': len(tris)},
            'facts': facts}
    (OUT / f'layouts-{biome}.json').write_text(json.dumps(data, separators=(',', ':')))
    for f in ('viewer.template.html', 'viewer.js'):
        shutil.copy(HERE / f, OUT / ('index.html' if f.endswith('.html') else f))


def preview():
    lay = build()
    reach = check(lay)
    frame = [p for p, s in lay.b.items() if 'dark_oak' in s or 'storm_gong' in s or 'lightning_rod' in s]
    xs, ys, zs = zip(*frame)
    facts = {'stand': at(*STAND), 'chests': [at(*c) for c in CHESTS], 'reach': round(reach, 2),
             'frame': [max(xs) - min(xs) + 1, max(ys) - min(ys) + 1],
             'summit': [round(2 * PAD[0]), round(2 * PAD[1])], 'gong': round(2 * GONG_R)}
    for biome in BIOMES:
        world = sample_peak(biome)
        for p, s in lay.b.items():
            world.put(*p, s)
        export(world, facts | {'biome': biome}, biome)
    print(f'gong {facts["gong"]} across in a {facts["frame"][0]} x {facts["frame"][1]} frame on a {facts["summit"][0]} x '
          f'{facts["summit"][1]} summit; stand {facts["stand"]} (reach {facts["reach"]}), chest {facts["chests"][0]}')


# ---------------------------------------------------------------- mod resources

RES = ROOT / 'src/main/resources'
ASSETS = RES / 'assets/elementalwands'
TEMPLATE = RES / 'data/elementalwands/structure/storm_aerie.nbt'
GEO = ASSETS / 'geckolib/models/storm_gong.geo.json'
ANIMATION = ASSETS / 'geckolib/animations/storm_gong.animation.json'
SHEET = ASSETS / 'textures/block/storm_gong.png'
LAYOUT_JAVA = ROOT / 'src/main/java/com/anton/elementalwands/summit/StormAerieLayout.java'
# Where each texture sits on the model's 128 px sheet.
SHEET_AT = {'face': (0, 0), 'edge': (64, 0), 'boss': (80, 0), 'cord': (96, 0), 'handle': (64, 16), 'head': (80, 16)}


def template_nbt(lay):
    lo, hi = BOX
    blocks = {}
    for (x, y, z), state in lay.b.items():
        assert all(lo[i] <= v <= hi[i] for i, v in enumerate((x, y, z))), (x, y, z)
        blocks[x - lo[0], y - lo[1], z - lo[2]] = state
    return S._crypt().structure_nbt(blocks, tuple(hi[i] - lo[i] + 1 for i in range(3)))


def sheet():
    img = Image.new('RGBA', (128, 128), (0, 0, 0, 0))
    img.paste(gong_face_texture(), SHEET_AT['face'])
    textures = gong_textures()
    for kind in ('edge', 'boss', 'cord', 'handle', 'head'):
        img.paste(textures['ew_gong_' + kind], SHEET_AT[kind])
    return img


def geo():
    """GeckoLib geometry, in pixels from the anchor block's bottom centre. The plate, its boss
    and cords hang from the 'swing' bone, pivoting on the beam; the mallet is its own bone.

    GeckoLib loads Bedrock geometry mirrored in x (origins and pivots) but keeps each cube's z
    rotation, so x is written mirrored here: the baked model is then the layout's own geometry,
    facing south, and the mallet leans toward its post rather than toward the plate. The game's
    renderer turns it from south to the anchor's facing."""
    ox, oy, oz = .5, GONG_Y, .5
    px = lambda v: round(v * 16, 4)

    def region(kind, w, h, du=0, dv=0):
        u, v = SHEET_AT[kind]
        return {'uv': [u + du, v + dv], 'uv_size': [w, h]}
    plate, mallet = [], []
    for i, (lo, hi, tex, rot) in enumerate(gong_boxes()):
        size = [px(hi[k] - lo[k]) for k in range(3)]
        side = tex['side']
        if i < 16:
            # A strip: its faces show its band of the round face texture.
            v = 64 - (i + 1) * 4
            band = {'uv': [32 - size[0] / 2, v], 'uv_size': [size[0], 4]}
            uv = {'north': band, 'south': band, 'east': region('edge', size[2], 4), 'west': region('edge', size[2], 4),
                  'up': region('edge', min(16, size[0]), size[2]), 'down': region('edge', min(16, size[0]), size[2])}
        else:
            w, h, d = size
            front = region(tex.get('s', side), min(16, w), min(16, h), (16 - w) / 2 if w < 16 else 0, (16 - h) / 2 if h < 16 else 0)
            uv = {'north': front, 'south': front, 'east': region(side, d, min(16, h)), 'west': region(side, d, min(16, h)),
                  'up': region(side, w, d), 'down': region(side, w, d)}
        cube = {'origin': [px(ox - hi[0]), px(lo[1] - oy), px(lo[2] - oz)], 'size': size, 'uv': uv}
        if rot:
            cube['pivot'] = [px(ox - rot[1][0]), px(rot[1][1] - oy), px(rot[1][2] - oz)]
            cube['rotation'] = [0, 0, rot[0]]
        (mallet if side in ('handle', 'head') else plate).append(cube)
    bones = [{'name': 'root', 'pivot': [0, 0, 0]},
             {'name': 'swing', 'parent': 'root', 'pivot': [0, px(BEAM_Y - oy), 0], 'cubes': plate},
             {'name': 'mallet', 'parent': 'root', 'pivot': [0, 0, 0], 'cubes': mallet}]
    return {'format_version': '1.12.0', 'minecraft:geometry': [{
        'description': {'identifier': 'geometry.storm_gong', 'texture_width': 128, 'texture_height': 128,
                        'visible_bounds_width': 8, 'visible_bounds_height': 7, 'visible_bounds_offset': [0, 3, 0]},
        'bones': bones}]}


# Two identical swings: GeckoLib does not restart a triggered animation that is still playing, so the
# gong alternates between them and every strike swings it afresh.
STRIKES = ('animation.storm_gong.strike', 'animation.storm_gong.strike_b')


def animations():
    """A faint sway in the mountain wind, and the swing when the gong is struck."""
    keys = lambda frames: {f'{t:.2f}': [a, 0, 0] for t, a in frames}
    return {'format_version': '1.8.0', 'animations': {
        'animation.storm_gong.idle': {'loop': True, 'animation_length': 6.0, 'bones': {'swing': {'rotation': keys(
            ((0, 0), (1.5, 1.2), (3, 0), (4.5, -1.2), (6, 0)))}}},
        **{name: {'animation_length': 2.4, 'bones': {'swing': {'rotation': keys(
            ((0, 0), (.1, -14), (.45, 10), (.8, -7), (1.15, 4.5), (1.5, -2.5), (1.9, 1), (2.4, 0)))}}}
           for name in STRIKES},
    }}


def layout_java(lay):
    lo, hi = BOX
    gong = [-lo[0], GONG_Y - lo[1], -lo[2]]
    chests = ',\n'.join('        {%d, %d, %d}' % c for c in CHESTS)
    cells = ',\n'.join('        {' + ', '.join(str(v) for v in (x, y - GONG_Y) + sum(rows, ())) + '}'
                        for (x, y), rows in zip(part_cells(), cell_rows()))
    return f"""package com.anton.elementalwands.summit;

/**
 * Generated by art/storm_aerie/build_layout.py; do not edit. The aerie template's box, which
 * worldgen levels the summit to, and where players and the reward chest go around the gong.
 * Offsets are {{forward, right, up}} blocks from the gong's foot, the standing cell under its anchor:
 * forward along the gong's facing, right along that facing turned clockwise, up along +y. Being
 * taken from the placed anchor's own facing, they hold in every rotation of the template.
 */
public final class StormAerieLayout {{
    private StormAerieLayout() {{}}

    public static final int SIZE_X = {hi[0] - lo[0] + 1}, SIZE_Y = {hi[1] - lo[1] + 1}, SIZE_Z = {hi[2] - lo[2] + 1};
    /** Template layer of the summit's top; the frame's feet stand in it and players one above. */
    public static final int FLOOR = {-1 - lo[1]};
    /** The gong's anchor block, in template cells, and how far above standing level it sits. */
    public static final int[] GONG = {{{gong[0]}, {gong[1]}, {gong[2]}}};
    public static final int GONG_UP = {GONG_Y};
    /** Where an arriving or returning player's feet go; they face the gong. */
    public static final int[] STAND = {{{STAND[0]}, {STAND[1]}, {STAND[2]}}};
    /** The reward chest cells, at the gong's foot. */
    public static final int[][] CHESTS = {{
{chests}
    }};
    /**
     * The plate's cells, the anchor's first; a part's {{@code cell}} indexes this. Each is {{left, up,
     * then u0, u1 for each quarter-block row bottom up}}: its offset from the anchor (left along the
     * facing turned counterclockwise, up along +y) and the plate's stepped outline inside it, as pixel
     * spans along that left; a row of 0, 0 is empty.
     */
    public static final int[][] GONG_CELLS = {{
{cells}
    }};
}}
"""


def block_assets():
    """The anchor and part blocks draw nothing themselves: the gong is the anchor's model."""
    variants = {f'facing={f}': {'model': 'elementalwands:block/storm_gong_hidden'} for f in S.FACING_YAW}
    return {ASSETS / 'blockstates/storm_gong.json': S.as_json({'variants': variants}),
            ASSETS / 'blockstates/storm_gong_part.json': S.as_json({'variants': variants}),
            ASSETS / 'models/block/storm_gong_hidden.json': S.as_json({'textures': {'particle': 'elementalwands:block/storm_gong_particle'}}),
            ASSETS / 'textures/block/storm_gong_particle.png': S.png(gong_textures()['ew_gong_edge'])}


def install_files():
    lay = build()
    check(lay)
    files = {TEMPLATE: template_nbt(lay), LAYOUT_JAVA: layout_java(lay).encode(), GEO: S.as_json(geo()),
             ANIMATION: S.as_json(animations()), SHEET: S.png(sheet())}
    files.update(block_assets())
    return lay, files


if __name__ == '__main__':
    ap = argparse.ArgumentParser()
    ap.add_argument('--serve', action='store_true')
    ap.add_argument('--install', action='store_true', help='write the template, layout constants and gong assets into the mod')
    ap.add_argument('--check', action='store_true', help='verify the installed files match this builder')
    args = ap.parse_args()
    if args.install or args.check:
        lay, files = install_files()
        if args.check:
            for path, data in files.items():
                assert path.exists() and path.read_bytes() == data, f'Drift: {path.relative_to(ROOT)}'
            print(f'Storm Aerie verified: template ({len(lay.b)} blocks) and {len(files)} files')
        else:
            for path, data in files.items():
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
            print(f'Installed {len(files)} files ({sum(map(len, files.values())) // 1024} KiB)')
        raise SystemExit
    preview()
    print(f'Preview written to {OUT.relative_to(ROOT)}/index.html')
    if args.serve:
        import functools, http.server

        class NoCache(http.server.SimpleHTTPRequestHandler):
            def end_headers(self):
                self.send_header('Cache-Control', 'no-store'); super().end_headers()
        http.server.ThreadingHTTPServer(('127.0.0.1', 8395), functools.partial(NoCache, directory=str(OUT))).serve_forever()
