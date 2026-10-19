#!/usr/bin/env python3
"""Build the Storm Roc's 16-tile tornado atlas and local Pillow previews.

Requires Pillow. Run from any directory; --check is read-only and compares the
encoded PNG bytes, not just decoded pixels. No external art or fonts are needed.
Tiles 0-3: cloud wall; 4-7: dust; 8: snow; 9: stone; 10: ice;
11: rubble; 12-15: transparent wind. All artwork is authored at 16 pixels.
"""

import argparse
from io import BytesIO
from pathlib import Path
import random

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[2]
OUTPUT = ROOT / 'src/main/resources/assets/elementalwands/textures/misc/roc_tornado.png'
PREVIEWS = ROOT / '.local-previews/storm-roc/tornado-texture'
SEED = 0x524F43
NEAREST = Image.Resampling.NEAREST

WALL = [(178, 184, 188), (196, 202, 206), (215, 220, 223),
        (232, 236, 238), (246, 248, 249), (255, 255, 255)]
DUST = [(137, 135, 130), (157, 155, 149), (177, 175, 168),
        (195, 193, 185), (211, 212, 206), (220, 220, 218)]


def opaque(rgb):
    return (*rgb, 255)


def flowing_tile(variant, dusty=False):
    """Periodic diagonal strata, with broad stepped puffs inside each tile.

    The shared edge profile closes an eight-pixel rise at x=15. Every variant
    has identical left/right columns, so arbitrary variants can be neighbors.
    Interior clusters never overwrite those edges. No independent pixel noise.
    """
    palette = DUST if dusty else WALL
    tile = Image.new('RGBA', (16, 16))
    rise = [0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5, 6, 6, 7, 8]
    strata = [2, 3, 4, 3, 2, 1, 0, 1]
    for y in range(16):
        for x in range(16):
            tile.putpixel((x, y), opaque(palette[strata[(y + rise[x]) % 8]]))
    draw = ImageDraw.Draw(tile)
    rng = random.Random(SEED + variant + (100 if dusty else 0))
    # Chunky compressed cloud lobes: dark undersides and short lit wind crests.
    for band in range(3):
        x = rng.randint(2, 6)
        y = (band * 5 + variant * 2) % 13 + 1
        points = [(x, y + 1), (x + 2, y + 1), (x + 2, y),
                  (x + 5, y), (x + 5, y - 1), (min(13, x + 8), y - 1)]
        draw.line(points, fill=opaque(palette[4]), width=1)
        draw.line([(px, py + 1) for px, py in points],
                  fill=opaque(palette[3]), width=1)
        if band != 1:
            draw.line([(x + 1, y + 3), (x + 3, y + 3), (x + 3, y + 2),
                       (min(13, x + 6), y + 2)], fill=opaque(palette[1]))
    if dusty:
        for x, y in [(3, 3), (10, 10), (6, 13)]:
            y = (y + variant * 3) % 15
            draw.rectangle((x, y, x + 1, min(15, y + 1)), fill=opaque(palette[5]))
            draw.point((x + 2, y), fill=opaque(palette[4]))
        draw.line([(9, 5), (10, 5), (10, 4), (12, 4)], fill=opaque(palette[0]))
    else:
        draw.line([(3, 11 - variant), (5, 11 - variant),
                   (5, 10 - variant), (8, 10 - variant)], fill=opaque(palette[5]))
    return tile


def debris_tiles():
    snow = Image.new('RGBA', (16, 16), (235, 241, 244, 255))
    d = ImageDraw.Draw(snow)
    for box in [(1, 2, 5, 3), (9, 1, 13, 2), (6, 7, 11, 8),
                (0, 12, 3, 13), (11, 12, 15, 14)]:
        d.rectangle(box, fill=(250, 253, 255, 255))
    for points in [[(0, 6), (3, 6), (3, 5), (6, 5)],
                   [(9, 10), (12, 10), (12, 9), (15, 9)],
                   [(3, 15), (3, 13), (6, 13)]]:
        d.line(points, fill=(202, 216, 226, 255))
    d.line([(1, 7), (4, 7), (4, 6), (6, 6)], fill=(220, 231, 238, 255))

    stone = Image.new('RGBA', (16, 16), (166, 170, 172, 255))
    d = ImageDraw.Draw(stone)
    for box, color in [((1, 1, 5, 3), (185, 188, 188, 255)),
                       ((8, 0, 12, 2), (153, 158, 160, 255)),
                       ((3, 6, 7, 8), (179, 183, 183, 255)),
                       ((10, 7, 14, 10), (192, 194, 193, 255)),
                       ((0, 12, 4, 14), (153, 158, 160, 255)),
                       ((8, 13, 12, 15), (179, 183, 183, 255))]:
        d.rectangle(box, fill=color)
    for points in [[(6, 0), (6, 3), (5, 3), (5, 5), (8, 5), (8, 9),
                    (7, 9), (7, 12), (9, 12), (9, 15)],
                   [(8, 6), (11, 6), (11, 5), (15, 5)]]:
        d.line(points, fill=(112, 121, 128, 255))
    d.line([(7, 1), (7, 3), (6, 4)], fill=(204, 207, 206, 255))
    d.rectangle((12, 12, 13, 13), fill=(153, 158, 160, 255))

    ice = Image.new('RGBA', (16, 16), (197, 220, 232, 255))
    d = ImageDraw.Draw(ice)
    d.polygon([(0, 0), (7, 0), (7, 2), (5, 2), (5, 5), (3, 5),
               (3, 8), (0, 8)], fill=(225, 240, 247, 255))
    d.polygon([(15, 3), (13, 3), (13, 6), (11, 6), (11, 9),
               (9, 9), (9, 12), (7, 12), (7, 15), (15, 15)],
              fill=(173, 201, 219, 255))
    for points in [[(0, 12), (2, 12), (2, 9), (4, 9), (4, 6),
                    (6, 6), (6, 3), (8, 3), (8, 0)],
                   [(6, 15), (6, 13), (8, 13), (8, 10), (10, 10),
                    (10, 7), (12, 7), (12, 4), (14, 4), (14, 1)]]:
        d.line(points, fill=(242, 251, 255, 255))
    d.line([(0, 14), (2, 14), (2, 13), (4, 13)], fill=(215, 234, 244, 255))

    rubble = Image.new('RGBA', (16, 16), (127, 132, 135, 255))
    d = ImageDraw.Draw(rubble)
    rng = random.Random(SEED + 211)
    for row in range(4):
        for col in range(4):
            x = col * 4 + (row % 2)
            y = row * 4
            tone = rng.choice([157, 174, 188])
            d.polygon([(x, y + 1), (x + 1, y), (x + 3, y),
                       (x + 3, y + 2), (x + 2, y + 3), (x, y + 3)],
                      fill=(tone, tone + 3, tone + 4, 255))
            d.line([(x + 1, y), (x + 2, y)], fill=(210, 213, 213, 255))
            d.point((x + 2, y + 3), fill=(146, 151, 154, 255))
    return [snow, stone, ice, rubble]


def wind_tile(variant):
    tile = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(tile)
    # A common curling ribbon crosses both edges at the same height/alpha.
    heights = [9, 9, 8, 8, 7, 7, 6, 6, 6, 6, 7, 7, 8, 8, 9, 9]
    for x, y in enumerate(heights):
        d.point((x, y), fill=(246, 250, 252, 220))
        d.point((x, y + 1), fill=(226, 237, 243, 150))
    curls = [
        [(2, 5), (5, 5), (5, 4), (9, 4), (9, 2), (7, 2), (7, 3)],
        [(3, 3), (6, 3), (6, 2), (11, 2), (11, 4), (9, 4)],
        [(2, 4), (4, 4), (4, 2), (8, 2), (8, 3), (7, 3)],
        [(4, 4), (7, 4), (7, 3), (12, 3), (12, 1), (10, 1)],
    ]
    d.line(curls[variant], fill=(255, 255, 255, 230))
    d.line([(3, 14), (6, 14), (6, 13), (10 + variant, 13)],
           fill=(240, 247, 251, 180))
    return tile


def build_atlas():
    tiles = ([flowing_tile(i) for i in range(4)]
             + [flowing_tile(i, dusty=True) for i in range(4)]
             + debris_tiles() + [wind_tile(i) for i in range(4)])
    atlas = Image.new('RGBA', (64, 64))
    for index, tile in enumerate(tiles):
        atlas.paste(tile, (index % 4 * 16, index // 4 * 16))
    validate(atlas, tiles)
    return atlas, tiles


def pixels_of(image):
    return [image.getpixel((x, y)) for y in range(image.height)
            for x in range(image.width)]


def validate(atlas, tiles):
    assert atlas.size == (64, 64) and atlas.mode == 'RGBA'
    assert all(p[3] == 255 for t in tiles[:12] for p in pixels_of(t))
    for tile in tiles[12:]:
        pixels = list(pixels_of(tile))
        assert all(p[3] == 0 or 140 <= p[3] <= 230 for p in pixels)
        assert sum(p[3] == 0 for p in pixels) > 128
    for row in (0, 1, 3):
        reference = list(pixels_of(tiles[row * 4].crop((0, 0, 1, 16))))
        for tile in tiles[row * 4:row * 4 + 4]:
            assert list(pixels_of(tile.crop((0, 0, 1, 16)))) == reference
            assert list(pixels_of(tile.crop((15, 0, 16, 16)))) == reference
    assert all(len(set(pixels_of(t))) <= 8 for t in tiles)


def png_bytes(image):
    encoded = BytesIO()
    image.save(encoded, format='PNG', optimize=False, compress_level=9)
    return encoded.getvalue()


def cube(canvas, tile, cx, cy, tint):
    """Rasterize each source pixel as an isometric parallelogram; no filtering."""
    draw = ImageDraw.Draw(canvas)
    # Each face uses a complete tile; face light is independent of color tint.
    faces = [((cx, cy), (3, 1.5), (-3, 1.5), 1.0),
             ((cx - 48, cy + 24), (3, 1.5), (0, 3), 0.9),
             ((cx, cy + 48), (3, -1.5), (0, 3), 0.8)]
    for (ox, oy), (ux, uy), (vx, vy), shade in faces:
        for y in range(16):
            for x in range(16):
                rgb = tile.getpixel((x, y))[:3]
                color = tuple(round(c * t * shade) for c, t in zip(rgb, tint))
                corners = [(round(ox + (x + a) * ux + (y + b) * vx),
                            round(oy + (x + a) * uy + (y + b) * vy))
                           for a, b in [(0, 0), (1, 0), (1, 1), (0, 1)]]
                draw.polygon(corners, fill=color)


def write_previews(atlas, tiles):
    PREVIEWS.mkdir(parents=True, exist_ok=True)
    atlas.resize((512, 512), NEAREST).save(PREVIEWS / 'atlas-8x.png')
    mock = Image.new('RGB', (960, 390), (42, 49, 59))
    d = ImageDraw.Draw(mock)
    d.text((24, 16), 'WALL / GREY TINT (0.88, 0.90, 0.92)', fill=(234, 239, 244))
    d.text((24, 156), 'WALL / STORM TINT (0.48, 0.52, 0.60)', fill=(234, 239, 244))
    d.text((24, 296), 'Faces: top 1.0 / left 0.9 / right 0.8. Pillow mock, not in-game.',
           fill=(202, 215, 226))
    for i in range(4):
        for y, tint in [(42, (0.88, 0.90, 0.92)), (182, (0.48, 0.52, 0.60))]:
            cube(mock, tiles[i], 100 + i * 130, y, tint)
            d.text((82 + i * 130, y + 104), f'Tile {i}', fill=(218, 227, 235))
    d.text((595, 16), 'DEBRIS / NEUTRAL TINT', fill=(234, 239, 244))
    for i in range(4):
        x, y = 655 + (i % 2) * 150, 42 + (i // 2) * 140
        cube(mock, tiles[8 + i], x, y, (1, 1, 1))
        d.text((x - 18, y + 104), f'Tile {8 + i}', fill=(218, 227, 235))
    mock.save(PREVIEWS / 'textured-cubes.png')
    # Repetition view makes seams and transparent ribbon shapes easy to inspect.
    repeat = Image.new('RGBA', (128, 56), (65, 76, 89, 255))
    for row, atlas_row in enumerate((0, 1, 3)):
        for col in range(8):
            tile = tiles[atlas_row * 4 + col % 4]
            repeat.alpha_composite(tile, (col * 16, row * 20))
    repeat.resize((1024, 448), NEAREST).save(PREVIEWS / 'scrolling-rows-8x.png')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='verify PNG bytes; write nothing')
    args = parser.parse_args()
    atlas, tiles = build_atlas()
    expected = png_bytes(atlas)
    if args.check:
        assert OUTPUT.is_file(), f'Missing atlas: {OUTPUT}'
        assert OUTPUT.read_bytes() == expected, f'Atlas differs: {OUTPUT}'
        print('PASS --check: roc_tornado.png is byte-identical (64x64 RGBA, 16 tiles).')
        print('PASS: opaque materials, wind alpha/palette limits, all scrolling tile edges.')
    else:
        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_bytes(expected)
        write_previews(atlas, tiles)
        print(f'Wrote {OUTPUT.relative_to(ROOT)} ({len(expected)} bytes)')
        print(f'Previews: {PREVIEWS.relative_to(ROOT)}')


if __name__ == '__main__':
    main()
