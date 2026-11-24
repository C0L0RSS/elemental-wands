#!/usr/bin/env python3
"""Build the First Wizard's stone: its Stone Wall's block and the stone atlas its Stone spells are drawn with.

    py -3 art/first_wizard/build_stone.py             write the art copies here (stone.png, wall.png)
    py -3 art/first_wizard/build_stone.py --install   also write the game's copies under src/main/resources
    py -3 art/first_wizard/build_stone.py --check     write nothing; fail if any art or game copy differs

The raised wall and Faultline spikes use the fractured-slate panel of materials/source.png.
The flat fault cutout follows the player's fault mask. Ink settles into the painted fracture.
Atlas (64 x 128, the block-fire kit's layout: eight 16-pixel rows, four identical frames each, since stone does not
move; read by client/blockink):
  0 wall       painted slate masonry
  1 veined     the same, its mortar run with ink and violet: the boss's wall
  2 spike      split slate
  3 spike ink  the spike stone veined with ink and violet
  4 stained    the spike stone with ink soaking up from below, ragged
  5 dust       pale stone dust
  6 crack      the player's fault crack, its core violet in black ink (transparent round it, drawn as a cutout)
  7 cooling    the stone's own pattern in violet greys: where stone starts to cool into purple

Wall (16 x 16): row 1, the boss's Stone Wall block (textures/block/first_wizard_wall.png).

The stone's painted values and ink accents are sampled deterministically.
Requires Pillow and NumPy.
"""

import argparse
import io
from pathlib import Path

import numpy as np
from PIL import Image

from build_materials import levels, paint

ROOT = Path(__file__).resolve().parent
REPO = ROOT.parents[1]
GAME = REPO / "src/main/resources/assets/elementalwands/textures"
FAULT = GAME / "block/stone_fault.png"
OUTPUTS = {
    "stone.png": GAME / "misc/first_wizard_stone.png",
    "wall.png": GAME / "block/first_wizard_wall.png",
}

INK = (0x13, 0x0f, 0x1b)
WET = (0x2a, 0x1d, 0x3d)
VIOLET = (0x7a, 0x43, 0xd6)
LILAC = (0xa2, 0x73, 0xf2)


def rnd(*values):
    """A fixed integer hash in 0..65535 (the same as build_ink.py's)."""
    h = 2166136261
    for v in values:
        h = ((h ^ (v & 0xFFFFFFFF)) * 16777619) & 0xFFFFFFFF
        h ^= h >> 13
        h = (h * 1274126177) & 0xFFFFFFFF
    return (h ^ (h >> 16)) & 0xFFFF


def luminance(tile):
    return (tile[:, :, 0].astype(int) + tile[:, :, 1].astype(int) + tile[:, :, 2].astype(int)) // 3


def veined(tile, indices):
    """Soak the fracture in charcoal. Violet only catches on a short lip of the cut."""
    out = tile.copy()
    out[indices == 0, :3] = WET
    lip = (indices == 0) & (np.roll(indices, 1, axis=1) >= 2)
    out[lip, :3] = (88, 65, 111)
    return out


def stained(tile, seed):
    """Ink soaking up from the bottom edge, ragged, with a violet line where it stops."""
    out = tile.copy()
    for x in range(16):
        length = 4 + rnd(seed, x, 41) % 7
        out[16 - length:, x, :3] = INK
        out[16 - length, x, :3] = VIOLET
        if rnd(seed, x, 42) % 3 == 0:
            out[17 - length, x, :3] = WET
    return out


def dust(tile):
    """The stone's pattern, lifted to a pale dust."""
    out = tile.copy()
    lum = luminance(tile)
    for c, base in enumerate((0xc4, 0xc8, 0xc2)):
        out[:, :, c] = np.clip(base + (lum - 140) // 4, 0, 255).astype(np.uint8)
    out[:, :, 3] = 255
    return out


def crack(tile):
    """The fault crack: its dark walls in ink, its light core violet; clear round it."""
    out = np.zeros_like(tile)
    lum = luminance(tile)
    for y in range(16):
        for x in range(16):
            if tile[y, x, 3] == 0:
                continue
            out[y, x, :3] = LILAC if lum[y, x] >= 180 else VIOLET if lum[y, x] >= 100 else INK
            out[y, x, 3] = 255
    return out


def cooling(tile):
    """The stone's pattern in violet greys."""
    out = tile.copy()
    lum = luminance(tile)
    for c, (scale, add) in enumerate(((0x60, 0x30), (0x48, 0x1c), (0x88, 0x40))):
        out[:, :, c] = np.clip(add + lum * scale // 200, 0, 255).astype(np.uint8)
    out[:, :, 3] = 255
    return out


def build():
    slate = levels("stone")
    # Two offset courses let a field of connected wall blocks read as masonry.
    courses = slate.copy()
    courses[8:] = np.roll(courses[8:], 6, axis=1)
    courses[7] = np.minimum(courses[7], 1)
    wall = paint(courses, "353641 555761 797d86 9197a0 b6bcc1")
    spike = paint(slate, "3a3744 525260 777986 9498a0 babcc2")
    boss_wall = veined(wall, courses)
    with Image.open(FAULT) as source:
        fault = np.array(source.convert("RGBA"), dtype=np.uint8)
    rows = [wall, boss_wall, spike, veined(spike, slate), stained(spike, 4), dust(spike), crack(fault), cooling(spike)]
    atlas = np.zeros((128, 64, 4), dtype=np.uint8)
    for row, tile in enumerate(rows):
        for phase in range(4):
            atlas[row * 16:(row + 1) * 16, phase * 16:(phase + 1) * 16] = tile
    return Image.fromarray(atlas, "RGBA"), Image.fromarray(boss_wall, "RGBA")


def png_bytes(image):
    stream = io.BytesIO()
    image.save(stream, format="PNG", optimize=False, compress_level=9)
    return stream.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    group = parser.add_mutually_exclusive_group()
    group.add_argument("--install", action="store_true", help="also write the game's copies")
    group.add_argument("--check", action="store_true", help="write nothing; fail if any copy differs")
    args = parser.parse_args()
    atlas, wall = build()
    built = {"stone.png": png_bytes(atlas), "wall.png": png_bytes(wall)}
    failures = []
    for name, data in built.items():
        for path in (ROOT / name, OUTPUTS[name]):
            if args.check:
                if not path.is_file() or path.read_bytes() != data:
                    failures.append(str(path.relative_to(REPO)))
            elif path.parent == ROOT or args.install:
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
    if failures:
        raise SystemExit("MISMATCH: " + ", ".join(failures) + "; run build_stone.py --install")
    print(("CHECK PASSED" if args.check else "INSTALLED" if args.install else "BUILT") + ": " + ", ".join(built))


if __name__ == "__main__":
    main()
