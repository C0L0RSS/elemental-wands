#!/usr/bin/env python3
"""Build the Astral Double's void-glass shell textures from the Space atlas's glass band.

The shell is the player model drawn a little larger over the owner's skin (AstralDoubleRenderer). These 64 x 64
textures follow the vanilla skin layout: every face of the head, body, arms and legs gets the glass band's dark
see-through interior and a one-pixel bright frame, so each limb reads as a void-glass box; the outer skin-layer
regions stay fully transparent so only one shell draws. Wide and slim arms need separate layouts.

python3 build_astral_glass.py [--check]   # --check writes nothing and fails if either PNG differs
"""
import argparse
import io
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent
ATLAS = ROOT / 'blockspace.png'
OUT = ROOT.parents[3] / 'src/main/resources/assets/elementalwands/textures/entity'
GLASS_ROW = 1
FRAME = (0x89, 0x50, 0xd5)   # the glass band's frame shade
CORNER = (0xcb, 0xb0, 0xff)  # its brightest shade, for face corners


def cuboids(slim):
    """(u, v, width, height, depth) of each base body part in the 64 x 64 skin layout."""
    arm = 3 if slim else 4
    return [(0, 0, 8, 8, 8), (16, 16, 8, 12, 4), (40, 16, arm, 12, 4), (32, 48, arm, 12, 4),
            (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)]


def faces(u, v, w, h, d):
    return [(u + d, v, w, d), (u + d + w, v, w, d), (u, v + d, d, h), (u + d, v + d, w, h),
            (u + d + w, v + d, d, h), (u + 2 * d + w, v + d, w, h)]


def build(slim):
    with Image.open(ATLAS) as image:
        tile = np.asarray(image.convert('RGBA'))[GLASS_ROW * 16:(GLASS_ROW + 1) * 16, 0:16]
    interior = tile[1:15, 1:15]
    out = np.zeros((64, 64, 4), np.uint8)
    for part, cuboid in enumerate(cuboids(slim)):
        for face, (x, y, w, h) in enumerate(faces(*cuboid)):
            if w <= 0 or h <= 0:
                continue
            # Each face samples a different window of the interior, so neighbouring faces don't repeat.
            oy, ox = (part * 5 + face * 3) % 14, (part * 3 + face * 7) % 14
            rows = (np.arange(h) + oy) % 14
            cols = (np.arange(w) + ox) % 14
            region = interior[rows][:, cols].copy()
            region[0, :, :3] = region[-1, :, :3] = FRAME
            region[:, 0, :3] = region[:, -1, :3] = FRAME
            region[0, :, 3] = region[-1, :, 3] = 255
            region[:, 0, 3] = region[:, -1, 3] = 255
            for cy, cx in ((0, 0), (0, -1), (-1, 0), (-1, -1)):
                region[cy, cx, :3] = CORNER
            out[y:y + h, x:x + w] = region
    return Image.fromarray(out, 'RGBA')


def png_bytes(image):
    stream = io.BytesIO()
    image.save(stream, format='PNG', optimize=False, compress_level=9)
    return stream.getvalue()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='Read-only byte comparison')
    args = parser.parse_args()
    failures = []
    for name, slim in (('astral_glass_wide.png', False), ('astral_glass_slim.png', True)):
        path, data = OUT / name, png_bytes(build(slim))
        if args.check:
            if not path.is_file() or path.read_bytes() != data:
                failures.append(name)
        else:
            path.write_bytes(data)
    if failures:
        raise SystemExit('MISMATCH: ' + ', '.join(failures) + '; run build_astral_glass.py')
    print(('CHECK PASSED' if args.check else 'BUILT') + ': astral_glass_wide.png, astral_glass_slim.png')


if __name__ == '__main__':
    main()
